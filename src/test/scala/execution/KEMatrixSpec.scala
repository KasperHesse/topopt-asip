package execution

import chisel3._
import chiseltest._
import utils.Config.NUM_PROCELEM
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class KEMatrixSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "KE Matrix"

  it should "output values" in {
    test(new KEMatrix()).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      dut.io.keIter.poke(0.U)
      dut.io.keX.poke(0.U)
      dut.io.keY.poke(0.U)
      dut.io.keCol.poke(0.U)
      dut.clock.step()

      for(i <- 0 until NUM_PROCELEM) {
        dut.io.keCol.poke(i.U)
        dut.clock.step()
      }
      dut.clock.step(2)
    }
  }
}