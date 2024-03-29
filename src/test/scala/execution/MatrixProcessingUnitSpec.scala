package execution

import chisel3._
import chisel3.util._
import chiseltest._
import utils.Fixed._
import execution.{MatrixProcessingUnit, Opcode}
import execution.Opcode._
import utils.Config.simulationConfig
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MatrixProcessingUnitSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Matrix processing unit"

  /**
   * Tests the functionality of a matrix processing unit by applying different stimuli to each of the
   * processing elements instantiated inside.
   *
   * @param dut The DUT
   * @param as 2D array of input values. First dimension denotes the PE that the input is bound for, second dimension
   *           denotes the iteration
   * @param bs      2D array of input values. First dimension denotes the PE that the input is bound for, second dimension
   *                *           denotes the iteration
   * @param results 2D array of expected results. First dimension denotes the PE that the output should coem from, second dimension
   *                *           denotes the iteration
   * @param ops 1D array of operations. Since all PE's perform the same operation on each iteration, there's no need
   *            to make this 2D
   */
  def testBehaviour(dut: MatrixProcessingUnit,
                    as: Array[Array[Long]],
                    bs: Array[Array[Long]],
                    results: Array[Array[Long]],
                    ops: Array[Opcode.Type]): Unit = {
    var i = 0
    var resultCnt = 0
    val itermax = results(0).length
    val nelem = results.length
    while(resultCnt < itermax && i < 500) {
      if(i < itermax) {
        for(j <- 0 until nelem) {
          dut.io.in.a(j).poke(as(j)(i).S)
          dut.io.in.b(j).poke(bs(j)(i).S)
          dut.io.in.op.poke(ops(i))
          dut.io.in.valid.poke(true.B)
        }
      } else {
        dut.io.in.valid.poke(false.B)
      }
      dut.clock.step()
      if(dut.io.out.valid.peek().litToBoolean) {
        //Using assert instead of expect due to rounding errors when dividing.
        for(j <- 0 until nelem) {
          assert(math.abs(fixed2double(results(j)(resultCnt)) - fixed2double(dut.io.out.res(j).peek())) < 1E-5)
        }
        resultCnt += 1
      }
      i += 1
    }
  }


  def generateStimuli(dut: MatrixProcessingUnit, op: Opcode.Type, iters: Int, nelem: Int): Unit = {
    val as = Array.ofDim[Long](nelem, iters)
    val bs = Array.ofDim[Long](nelem, iters)
    val results = Array.ofDim[Long](nelem, iters)
    val ops = Array.fill(iters)(op)

    for (i <- 0 until nelem ) {
      for (j <- 0 until iters) {
        val x = genDouble()
        val y = genDouble()
        val a = double2fixed(x)
        val b = double2fixed(y)
        val res = op match {
          case ADD => fixedAdd(a, b)
          case SUB => fixedSub(a, b)
          case MUL => fixedMul(a, b)
          case DIV => double2fixed(x / y)
          case _ => throw new IllegalArgumentException("Unsupported PE Operation")
        }
        as(i)(j) = a
        bs(i)(j) = b
        results(i)(j) = res
        ops(j) = op
      }
    }
    testBehaviour(dut, as, bs, results, ops)
  }

  private val iters = 50
  private val sizes = List(4,11,16)

  it should "add values in parallel at different widths" in {
    for(nelem <- sizes)
      test(new MatrixProcessingUnit(nelem)) { c =>
        generateStimuli(c, ADD, iters, nelem)
      }
  }

  it should "subtract values in parallel at different widths" in {
    simulationConfig()
    for(nelem <- sizes)
      test(new MatrixProcessingUnit(nelem)) { c =>
        generateStimuli(c, SUB, iters, nelem)
      }
  }

  it should "multiply values in parallel at different widths" in {
    for(nelem <- sizes)
      test(new MatrixProcessingUnit(nelem)) { c =>
        generateStimuli(c, MUL, iters, nelem)
      }
  }

  it should "divide values in parallel at different widths" in {
    for(nelem <- sizes)
      test(new MatrixProcessingUnit(nelem)) { c =>
        generateStimuli(c, DIV, iters, nelem)
      }
  }
}
