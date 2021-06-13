package arithmetic

import chisel3._
import chisel3.util._
import pipeline.Opcode
import utils.Fixed._
import utils.DivTypes._
import utils.{Config, MulTypes}

/**
 * Abstract base class for the fixed-point divider. Implements [[DivIO]]
 */
abstract class FixedPointDiv extends Module {
  val io = IO(new DivIO)
}

/**
 * I/O ports for a fixed-point divider
 */
class DivIO extends Bundle {
  val in = Input(new DivInput)
  val out = Output(new DivOutput)
}

/**
 * Input signals to a fixed-point divider
 */
class DivInput extends Bundle {
  /** Numerator of the fraction */
  val numer = SInt(FIXED_WIDTH.W)
  /** Denominator of the fraction */
  val denom = SInt(FIXED_WIDTH.W)
  /** Should be asserted when numer,denom are valid */
  val valid = Bool()
}

/**
 * Output signals from a fixed-point divider
 */
class DivOutput extends Bundle {
  /** The result of computing numer/denom */
  val res = SInt(FIXED_WIDTH.W)
  /** Asserted for one clock cycle when the result is valid */
  val valid = Bool()
}

/**
 * A fixed-point divider implemented using the Newton-Raphson method
 *
 * @note Should not be instantiated directly. Instead, use the companion object to generate the correct
 *       type of multiplier
 *
 */
class NRDiv(val stage3Reps: Int = Config.NRDIV_STAGE3_REPS) extends FixedPointDiv {
  val stage1 = Module(new NRDivStage1)
  val stage2 = Module(new NRDivStage2)
  val stage3 = for (i <- 0 until stage3Reps) yield {
    val stage = Module(new NRDivStage3)
    stage
  }
  val stage4 = Module(new NRDivStage4)

  stage1.io.in <> io.in
  stage2.io.in <> stage1.io.out

  stage3(0).io.in <> stage2.io.out
  for(i <- 1 until stage3Reps) {
    stage3(i).io.in <> stage3(i-1).io.out
  }

  stage4.io.in.numer := stage3(stage3Reps-1).io.out.numer
  stage4.io.in.X := stage3(stage3Reps-1).io.out.X
  stage4.io.in.neg := stage3(stage3Reps-1).io.out.neg
  stage4.io.in.valid := stage3(stage3Reps-1).io.out.valid

  io.out := stage4.io.out
}

class Stage1IO extends Bundle {
  val in = Input(new DivInput)
  val out = Output(new Stage1Output)
}


class Stage1Output extends Bundle {
  val denom = SInt(FIXED_WIDTH.W)
  val numer = SInt(FIXED_WIDTH.W)
  val neg = Bool()
  val valid = Bool()
}

/**
 * First stage of the Newton-Raphson division circuit. Scales the denominator to be in range [0.5;1] and scales the
 * numerator by the same amount. The output denominator is the absolute value of the input denominator, and out.neg
 * is set true if in.denom was negative
 */
class NRDivStage1 extends Module {
  val io = IO(new Stage1IO)

  //Latch in signals, set up modules and constants
  val in = RegNext(io.in)

  val lzc = Module(new LeadingZerosCounter64Bit)

  val numer = Wire(SInt(FIXED_WIDTH.W))
  val denom = Wire(SInt(FIXED_WIDTH.W))

  //When dividing by zero, we override the result and force it to be zero
  when(in.denom === 0.S) {
    numer := 0.S
    denom := double2fixed(1).S
  } .otherwise {
    numer := in.numer
    denom := in.denom
  }

  val DIFF_WIDTH = log2Ceil(FIXED_WIDTH)+1 //Since it's fixed-point, we need an additional bit to ensure proper handling

  //Negative flag if denom is negative
  val neg = denom(FIXED_WIDTH-1)

  //Depending on test configuration, we must left-shift input by some fixed amount
  if(FIXED_WIDTH != 64) {
    lzc.io.in := Mux(neg, ((~denom).asSInt() + 1.S).asUInt(), denom.asUInt()) << (64 - FIXED_WIDTH)
  } else {
    lzc.io.in := Mux(neg, ((~denom).asSInt() + 1.S).asUInt(), denom.asUInt())
  }

  //Registers for middle of stage
  val denomReg = RegNext(denom)
  val numerReg = RegNext(numer)
  val cntReg = RegNext(lzc.io.cnt)
  val negReg = RegNext(neg)
  val validReg = RegNext(in.valid)


  val diff = Wire(SInt(DIFF_WIDTH.W))
  /** Number of bits that denom must be shifted to be in the range [0.5;1] */
  val diffAbs = Wire(UInt(DIFF_WIDTH.W))
//  diff := ((INT_WIDTH+1).U - lzc.io.cnt).asSInt()
  diff := ((INT_WIDTH+1).U - cntReg).asSInt()
  diffAbs := diff.abs.asUInt

  val shiftDir = diff(DIFF_WIDTH-1) //If true, shift left by diffAbs, else shift right by diff

  //Must preserve the sign of numerator by keeping MSB constant and shifting all other bits
  val numerLeftShiftedTemp: Bits = numerReg << diffAbs
  val numerLeftShifted: SInt = Cat(numerReg(FIXED_WIDTH - 1), numerLeftShiftedTemp(FIXED_WIDTH-2,0)).asSInt
  io.out.numer := Mux(shiftDir, numerLeftShifted, numerReg >> diff.asUInt)

  //In the next steps, denom must be positive. We'll invert it here and re-invert in stage 4 if necessary
  val denomTemp = Mux(shiftDir, denomReg << diffAbs, denomReg >> diff.asUInt)
  io.out.denom := Mux(negReg, (~denomTemp).asSInt() + 1.S, denomTemp)
//  io.out.neg := neg
  io.out.neg := negReg
//  io.out.valid := in.valid
  io.out.valid := validReg
}

class Stage2Output extends Bundle {
  val X = SInt(FIXED_WIDTH.W)
  val numer = SInt(FIXED_WIDTH.W)
  val denom = SInt(FIXED_WIDTH.W)
  val neg = Bool()
  val valid = Bool()
}

class Stage2IO extends Bundle {
  val in = Input(new Stage1Output)
  val out = Output(new Stage2Output)
}
/**
 * Second stage of the Newton-Raphson division circuit.
 * Calculates the initial estimate X=48/17-32/17*dp, where dp is the scaled denominator
 */
class NRDivStage2 extends Module {
  val io = IO(new Stage2IO)

  val in = RegNext(io.in)

  val FORTYEIGHTOVERSEVENTEEN = double2fixed(48.0/17.0).S(FIXED_WIDTH.W)
  val THIRTYTWOOVERSEVENTEEN = double2fixed(32.0/17.0).S(FIXED_WIDTH.W)

  val mul = Module(FixedPointMul(MulTypes.MULTICYCLE))
//  val sub = Module(new FixedPointALU)

  mul.io.in.a := in.denom
  mul.io.in.b := THIRTYTWOOVERSEVENTEEN
  mul.io.in.valid := in.valid

  io.out.X := (FORTYEIGHTOVERSEVENTEEN - mul.io.out.res)
  io.out.numer := RegNext(in.numer)
  io.out.denom := RegNext(in.denom)
  io.out.neg := RegNext(in.neg)
  io.out.valid := mul.io.out.valid

//  sub.io.in.a := FORTYEIGHTOVERSEVENTEEN
//  sub.io.in.b := mul.io.out.res
//  sub.io.in.op := Opcode.SUB
//  sub.io.in.valid := mul.io.out.valid

//  io.out.X := sub.io.out.res
//  io.out.numer := in.numer
//  io.out.denom := in.denom
//  io.out.neg := in.neg
//  io.out.valid := sub.io.out.valid
}

class Stage3IO extends Bundle {
  val in = Input(new Stage2Output)
  val out = Output(new Stage2Output)
}

/**
 * Third stage of the Newton-Raphson division circuit.
 * Calculates one iteration of X = X + X*(1-D'*X) based on the current value of X.
 * The value of D' * X is stored in a register and fed into another multiplier to compute X*(1-D'*X)
 * This value is stored again, and in the final stage, X+(X*(1-D'*X)) is calculated
 * The total execution time for this circuit is 3 clock cycles
 */
class NRDivStage3 extends Module {
  val io = IO(new Stage3IO)
  val in = RegNext(io.in)

  val mul1 = Module(FixedPointMul(MulTypes.MULTICYCLE))
  val mul2 = Module(FixedPointMul(MulTypes.MULTICYCLE))

  val queue1 = Module(new Queue(new Stage3InternalIO, 4, true, true))
  val queue2 = Module(new Queue(new Stage3InternalIO, 4, true, true))

  //Stage 3.1 - Calculate D' * X
  mul1.io.in.a := in.X
  mul1.io.in.b := in.denom
  mul1.io.in.valid := in.valid
  //Create bundle for first queue
  val step1 = Wire(new Stage3InternalIO)
  step1.X := in.X
  step1.numer := in.numer
  step1.denom := in.denom
  step1.neg := in.neg
  step1.valid := in.valid
  step1.res := DontCare //We won't actually use this right now
  queue1.io.enq.bits := step1
  queue1.io.enq.valid := in.valid

  val step1Result = RegNext(mul1.io.out)


  //Stage 3.2 - Calculate X*(1-D'*x)
  mul2.io.in.a := (double2fixed(1).S(FIXED_WIDTH.W) - step1Result.res)
  mul2.io.in.b := queue1.io.deq.bits.X
  mul2.io.in.valid := step1Result.valid

  //Copy data from first to second queue
  val step2 = Wire(new Stage3InternalIO)
  step2.X := queue1.io.deq.bits.X
  step2.denom := queue1.io.deq.bits.denom
  step2.numer := queue1.io.deq.bits.numer
  step2.neg := queue1.io.deq.bits.neg
  step2.valid := step1Result.valid
  step2.res := DontCare //Not being used
  queue1.io.deq.ready := step1Result.valid //Copy data from Q1 to Q2
  queue2.io.enq.valid := step1Result.valid
  queue2.io.enq.bits := step2

  val step2Result = RegNext(mul2.io.out)

  //Stage 3.3 - Calculate X+X*(1-D'*x)
  io.out.X := (queue2.io.deq.bits.X + step2Result.res)
  io.out.denom := queue2.io.deq.bits.denom
  io.out.numer := queue2.io.deq.bits.numer
  io.out.neg := queue2.io.deq.bits.neg
  io.out.valid := step2Result.valid
  //Dequeue data
  queue2.io.deq.ready := step2Result.valid


  //
////  val asu1 = Module(new FixedPointALU)
//  val asu2 = Module(new FixedPointALU)
//
//  //Stage 3.1 - Calculate D'*X
//  mul1.io.in.a := in.X
//  mul1.io.in.b := in.denom
//  mul1.io.in.valid := in.valid
//
//  val step1 = Wire(new Stage3InternalIO)
//  step1.X := in.X
//  step1.numer := in.numer
//  step1.denom := in.denom
//  step1.neg := in.neg
//  step1.res := mul1.io.out.res //D' * X
//  step1.valid := mul1.io.out.valid
//  val step1Reg = RegNext(step1)
//
//  //Stage 3.2 - Calculate X*(1-D'*x)
////  asu1.io.in.op := Opcode.SUB
////  asu1.io.in.a := double2fixed(1).S(FIXED_WIDTH.W)
////  asu1.io.in.b := step1Reg.res
////  asu1.io.in.valid := step1Reg.valid
////
////  mul2.io.in.a := asu1.io.out.res
////  mul2.io.in.b := step1Reg.X
////  mul2.io.in.valid := asu1.io.out.valid
//  mul2.io.in.a := (double2fixed(1).S(FIXED_WIDTH.W) - step1Reg.res)
//  mul2.io.in.b := step1Reg.X
//  mul2.io.in.valid := step1Reg.valid
//
//  val step2 = Wire(new Stage3InternalIO)
//  step2.X := step1Reg.X
//  step2.numer := step1Reg.numer
//  step2.denom := step1Reg.denom
//  step2.neg := step1Reg.neg
//  step2.res := mul2.io.out.res //X*(1-D' * X)
//  step2.valid := mul2.io.out.valid
//  val step2Reg = RegNext(step2)
//
//  //Stage 3.3 - Calculate 1+X*(1-D'*x)
//  asu2.io.in.a := step2Reg.X
//  asu2.io.in.b := step2Reg.res
//  asu2.io.in.op := Opcode.ADD
//  asu2.io.in.valid := step2Reg.valid
//
//  io.out.X := asu2.io.out.res
//  io.out.denom := step2Reg.denom
//  io.out.numer := step2Reg.numer
//  io.out.neg := step2Reg.neg
//  io.out.valid := asu2.io.out.valid
}

class Stage4IO extends Bundle {
  val in = Input(new Stage4Input)
  val out = Output(new DivOutput)

  class Stage4Input extends Bundle {
    val X = SInt(FIXED_WIDTH.W)
    val numer = SInt(FIXED_WIDTH.W)
    val neg = Bool()
    val valid = Bool()
  }
}

/**
 * Fourth stage of the Newton-Raphson division circuit.
 * This stage uses the value X=1/D' to calculate N/D as N'*X. Outputs the result of the division
 */
class NRDivStage4 extends Module {
  val io = IO(new Stage4IO)
  val in = RegNext(io.in)

  val mul = Module(FixedPointMul(MulTypes.MULTICYCLE))
  mul.io.in.a := in.numer
  mul.io.in.b := in.X
  mul.io.in.valid := in.valid

  val negQueue = Module(new Queue(Bool(), 4, true, true))
  negQueue.io.enq.bits := in.neg
  negQueue.io.enq.valid := in.valid
  negQueue.io.deq.ready := mul.io.out.valid

  val res = mul.io.out.res
  val neg = negQueue.io.deq.bits

  //Invert the sign of the result if necessary
  io.out.res := Mux(neg, (~res).asSInt() + 1.S, res)
  io.out.valid := mul.io.out.valid
}

class Stage3InternalIO extends Bundle {
  val X = SInt(FIXED_WIDTH.W)
  val numer = SInt(FIXED_WIDTH.W)
  val denom = SInt(FIXED_WIDTH.W)
  val neg = Bool()
  val valid = Bool()
  val res = SInt(FIXED_WIDTH.W)
}

object FixedPointDiv {
  def apply(v: DivisorType): FixedPointDiv = {
    v match {
      case NEWTONRAPHSON => new NRDiv
      case _ => throw new IllegalArgumentException("Divisor type must be one of the set types")
    }
  }

  def apply(v: DivisorType, NRstage3reps: Int): FixedPointDiv = {
    v match {
      case NEWTONRAPHSON => new NRDiv
      case _ => throw new IllegalArgumentException("Can only create Newton-Raphson divisors with this apply()")
    }
  }
}

