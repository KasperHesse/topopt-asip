package execution

import chisel3._
import chiseltest._
import utils.Fixed._
import execution.Opcode._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ProcessingElementSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Processing elements"

  /**
   * Tests the behaviour of a processing elements by applying stimuli and observing the results.
   * This method should only be used to test a stream of inputs without breaks
   * @param dut The DUT
   * @param as First operands in the instruction
   * @param bs Second operands in the instruction
   * @param results Expected results
   * @param ops Operations performed
   */
  def testStreamBehaviour(dut: ProcessingElement,
                          as: Array[Long],
                          bs: Array[Long],
                          results: Array[Long],
                          ops: Array[Opcode.Type]): Unit = {
    var i = 0
    var resultCnt = 0
    val itermax = results.length
    while(resultCnt < itermax && i < 500) {
      if(i < itermax) {
        dut.io.in.a.poke(as(i).S)
        dut.io.in.b.poke(bs(i).S)
        dut.io.in.op.poke(ops(i))
        dut.io.in.valid.poke(true.B)
      } else {
        dut.io.in.valid.poke(false.B)
      }
      dut.clock.step()
      if(dut.io.out.valid.peek().litToBoolean) {
        //Using assert instead of expect due to rounding errors when dividing.
        assert(math.abs(fixed2double(results(resultCnt)) - fixed2double(dut.io.out.res.peek())) < 1E-4)
        resultCnt += 1
      }
      i += 1
    }
    assert(resultCnt == itermax)
  }

  def generateStimuliSingleOperation(dut: ProcessingElement, op: Opcode.Type, iters: Int): Unit = {
    val as = new Array[Long](iters)
    val bs = new Array[Long](iters)
    val results = new Array[Long](iters)
    val ops = new Array[Opcode.Type](iters)

    for (i <- 0 until iters ) {

      val x = genDouble()
      val y = genDouble()
      val a = double2fixed(x)
      val b = double2fixed(y)
      val res = op match {
        case ADD => fixedAdd(a, b)
        case SUB => fixedSub(a, b)
        case MUL => fixedMul(a, b)
        case ABS => fixedAbs(a)
        case DIV => fixedDiv(a, b)
        case NEZ => fixedNez(a)
        case _ => throw new IllegalArgumentException("Unsupported PE Operation")
      }
      as(i) = a
      bs(i) = b
      results(i) = res
      ops(i) = op
    }
    testStreamBehaviour(dut, as, bs, results, ops)
  }

  def generateStimuliAddSub(dut: ProcessingElement, iters: Int): Unit = {
    val as = new Array[Long](iters)
    val bs = new Array[Long](iters)
    val results = new Array[Long](iters)
    val ops = new Array[Opcode.Type](iters)

    for (i <- 0 until iters ) {
      val x = genDouble()
      val y = genDouble()
      val a = double2fixed(x)
      val b = double2fixed(y)
      val myint = scala.util.Random.nextInt(4)
      val op = myint match {
        case 0 => ADD
        case 1 => SUB
        case 2 => MAX
        case 3 => MIN
      }
      val res = myint match {
        case 0 => fixedAdd(a, b)
        case 1 => fixedSub(a, b)
        case 2 => fixedMax(a, b)
        case 3 => fixedMin(a, b)
        case _ => throw new IllegalArgumentException("Unsupported PE Operation")
      }
      as(i) = a
      bs(i) = b
      results(i) = res
      ops(i) = op
    }
    testStreamBehaviour(dut, as, bs, results, ops)
  }

  def testMacRandom(dut: ProcessingElement, iters: Int): Unit = {
    //Generate stimuli
    val as = new Array[Long](iters)
    val bs = new Array[Long](iters)
    var result = 0L
    val op = if(scala.util.Random.nextBoolean()) MAC else RED

    //We need to scale down the values so they don't explode out of our range
    for (i <- 0 until iters ) {
      val x = genDouble()*math.pow(2,-(INT_WIDTH-2))
      val y = genDouble()*math.pow(2,-(INT_WIDTH-2))
      val a = double2fixed(x)
      val b = double2fixed(y)
      result += fixedMul(a,b)
      as(i) = a
      bs(i) = b
    }

    //Pokey pokey
    var i = 0
    while(!dut.io.out.valid.peek().litToBoolean && i < 50) {
      if(i < iters) {
        dut.io.in.a.poke(as(i).S)
        dut.io.in.b.poke(bs(i).S)
        dut.io.in.valid.poke(true.B)
        dut.io.in.op.poke(op)
        dut.io.in.macLimit.poke(iters.U)
      } else {
        dut.io.in.valid.poke(false.B)
      }
      dut.clock.step()
      i += 1
    }
    dut.io.out.res.expect(result.S)
  }

  def testMacMultiple(dut: ProcessingElement, iters: Int, macLimit: Int): Unit = {
    //Generate stimuli
    val as = Array.ofDim[Long](iters, macLimit)
    val bs = Array.ofDim[Long](iters, macLimit)
    var results = new Array[Long](iters)
    var result = 0L
    val op = if(scala.util.Random.nextBoolean()) MAC else RED

    //We need to scale down the values so they don't explode out of our range
    for(i <- 0 until iters) {
      for (j <- 0 until macLimit ) {
        val x = genDouble()*math.pow(2,-(INT_WIDTH-2))
        val y = genDouble()*math.pow(2,-(INT_WIDTH-2))
        val a = double2fixed(x)
        val b = double2fixed(y)
        result += fixedMul(a,b)
        as(i)(j) = a
        bs(i)(j) = b
      }
      results(i) = result
      result = 0
    }
    //Pokey pokey
    var i = 0
    var resultCnt = 0
    while(resultCnt < iters && i < 100) {
      val iter = i/macLimit //Which MAC iteration are we on?
      val inp = i % macLimit //Which input vector are we on
      if(i < iters*macLimit) {

        dut.io.in.a.poke(as(iter)(inp).S)
        dut.io.in.b.poke(bs(iter)(inp).S)
        dut.io.in.valid.poke(true.B)
        dut.io.in.op.poke(op)
        dut.io.in.macLimit.poke(macLimit.U)
      }
      dut.clock.step()
      i += 1
      if(dut.io.out.valid.peek().litToBoolean) {
        dut.io.out.res.expect(results(resultCnt).S)
        resultCnt += 1
      }
    }
  }


  private val iters = 50

  it should "add a stream of numbers" in {
    test(new ProcessingElement) {c =>
      generateStimuliSingleOperation(c, ADD, iters)
    }
  }

  it should "subtract a stream of numbers" in {
    test(new ProcessingElement) {c =>
      generateStimuliSingleOperation(c, SUB, iters)
    }
  }

  it should "multiply a stream of numbers" in {
    test(new ProcessingElement) {c =>
      generateStimuliSingleOperation(c, MUL, iters)
    }
  }

  it should "divide a stream of numbers" in {
    test(new ProcessingElement).withAnnotations(Seq(WriteVcdAnnotation)) {c =>
      generateStimuliSingleOperation(c, DIV, iters)
    }
  }

  it should "handle a mixture of adds and subs" in {
    test(new ProcessingElement) {c =>
      generateStimuliAddSub(c, iters)
    }
  }

  it should "take the abs of a stream of numbers" in {
    test(new ProcessingElement) {c =>
      generateStimuliSingleOperation(c, ABS, iters)
    }
  }

  it should "perform nez on a stream of numbers" in {
    test(new ProcessingElement) {dut =>
      generateStimuliSingleOperation(dut, NEZ, iters)
    }
  }

  it should "handle a randomly generated mac instruction" in {
    test(new ProcessingElement) {c =>
      testMacRandom(c, 20)
    }
  }

  it should "handle sequential mac instructions" in {
    test(new ProcessingElement) {c =>
      testMacMultiple(c, 10, 10)
    }
  }

  it should "divide by zero" in {
    test(new ProcessingElement) {dut =>
      val a = 1.0
      val b = 0.0
      val n = double2fixed(a)
      val d = double2fixed(b)

      val c = fixedDiv(n,d)

      testStreamBehaviour(dut, Array(n), Array(d), Array(c), Array(Opcode.DIV))

    }
  }

  it should "multiply two large numbers" in {
    test(new ProcessingElement) {dut =>
      val a = double2fixed(10000)
      val b = double2fixed(5)
      val c = double2fixed(50000)
      val d = fixedMul(a,b)
      testStreamBehaviour(dut, Array(a), Array(b), Array(c), Array(Opcode.MUL))
    }
  }
}
