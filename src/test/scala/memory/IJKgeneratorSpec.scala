package memory

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.{FlatSpec, Matchers}
import utils.Fixed._
import chiseltest.experimental.TestOptionBuilder._
import chiseltest.internal.WriteVcdAnnotation

class IJKgeneratorSpec extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "IJK generator"

  def expectIJK(dut: IJKgenerator, i: Int, j: Int, k: Int): Unit = {
    dut.io.out.i.expect(i.U)
    dut.io.out.j.expect(j.U)
    dut.io.out.k.expect(k.U)
  }

  def pokeIJK(dut: IJKgenerator, i: Int, j: Int, k: Int): Unit = {
    dut.io.in.i.poke(i.U)
    dut.io.in.j.poke(j.U)
    dut.io.in.k.poke(k.U)
  }

  it should "output a vcd file" in {
    test(new IJKgenerator).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      dut.clock.step(400)
    }
  }

  it should "saturate once invalid" in {
    test(new IJKgenerator).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      dut.io.ready.poke(true.B)
      while(dut.io.valid.peek.litToBoolean) {
        dut.clock.step()
      }
      expectIJK(dut, 0,0,0)
      dut.io.iterationOut.expect(8.U)

      pokeIJK(dut, 0,0,0)
      dut.io.iterationIn.poke(0.U)
      dut.io.load.poke(true.B)
      dut.clock.step()
      expectIJK(dut, 0,0,0)
      dut.io.iterationOut.expect(0.U)
    }
  }

  it should "load new values" in {
    test(new IJKgenerator) {dut =>
      expectIJK(dut, 0,0,0)
      dut.io.valid.expect(true.B)
      dut.io.ready.poke(true.B)
      dut.clock.step()

      expectIJK(dut, 0,2,0)
      dut.io.valid.expect(true.B)
      dut.io.load.poke(true.B)
      pokeIJK(dut, 1,1,1)

      dut.clock.step()
      expectIJK(dut, 1,1,1)
      dut.io.load.poke(false.B)
      dut.io.ready.poke(false.B)

      dut.clock.step()
      expectIJK(dut, 1,1,1)
    }
  }

  it should "reassert valid when restarted" in {
    test(new IJKgenerator) { dut =>
      pokeIJK(dut, 5,1,5)
      dut.io.iterationIn.poke(7.U)
      dut.io.load.poke(true.B)
      dut.clock.step()

      expectIJK(dut, 5,1,5)
      dut.io.iterationOut.expect(7.U)
      dut.io.load.poke(false.B)
      dut.io.ready.poke(true.B)
      while(dut.io.valid.peek.litToBoolean) {
        dut.clock.step()
      }
      dut.io.ready.poke(false.B)
      dut.io.restart.poke(true.B)
      dut.clock.step()

      expectIJK(dut, 5,1,5)
      dut.io.iterationOut.expect(7.U)
      dut.io.valid.expect(true.B)

    }
  }

  it should "restart to loaded values" in {
    test(new IJKgenerator) {dut =>
      pokeIJK(dut, 1,1,1)
      dut.io.load.poke(true.B)
      dut.clock.step()

      dut.io.load.poke(false.B)
      dut.io.ready.poke(true.B)
      dut.clock.step(2)

      expectIJK(dut, 1,5,1)
      dut.io.restart.poke(true.B)
      dut.clock.step()
      expectIJK(dut, 1,1,1)
    }
  }
  it should "match genIJKmultiple" in {
    test(new IJKgenerator) {dut =>
      val ijkVals = genIJKmultiple(start = Some(Array(0,0,0,0)), elems=30)
      pokeIJK(dut, ijkVals(0)(0), ijkVals(0)(1), ijkVals(0)(2))
      dut.io.iterationIn.poke(ijkVals(0)(3).U)
      dut.io.load.poke(true.B)
      dut.clock.step()
      dut.io.load.poke(false.B)
      dut.io.ready.poke(true.B)
      for(i <- ijkVals.indices) {
        expectIJK(dut, ijkVals(i)(0), ijkVals(i)(1), ijkVals(i)(2))
        val out = dut.io.out
        dut.clock.step()
      }
    }
  }
}
