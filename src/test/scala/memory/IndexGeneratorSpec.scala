package memory

import chisel3._
import chisel3.util._
import chisel3.experimental.BundleLiterals._
import chiseltest._
import org.scalatest.{FlatSpec, Matchers}
import utils.Fixed._
import chiseltest.experimental.TestOptionBuilder._
import chiseltest.internal.WriteVcdAnnotation
import pipeline.StypeMod
import utils.Config.{NUM_MEMORY_BANKS, NELX, NELY, NELZ}

class IndexGeneratorSpec extends FlatSpec with ChiselScalatestTester with Matchers {

  def pokeIJK(dut: IndexGenerator, index: Int, i: Int, j: Int, k: Int): Unit = {
    dut.io.in.bits.ijk(index).poke((new IJKBundle).Lit(_.i -> i.U, _.j -> j.U, _.k -> k.U))
    dut.io.in.bits.validIjk(index).poke(true.B)
  }

  def expectIJK(dut: IndexGenerator, index: Int, i: Int, j: Int, k: Int, valid: Boolean = true): Unit = {
    val e = i * NELY * NELZ + k * NELY + j
    dut.io.addrGen.bits.indices(index).expect(e.U)
    dut.io.addrGen.bits.validIndices(index).expect(valid.B)
  }

  "Index generator" should "correctly generate indices" in {
    def testFun(dut: IndexGenerator): Unit = {
      dut.io.addrGen.ready.poke(true.B)
      dut.io.in.ready.expect(true.B)
      dut.io.in.valid.poke(true.B)

      pokeIJK(dut, 0, 0, 0, 0)
      pokeIJK(dut, 1, 1, 0, 0)
      pokeIJK(dut, 2, 2, 0, 0)
      for(i <- 3 until NUM_MEMORY_BANKS) {
        dut.io.in.bits.validIjk(i).poke(false.B)
      }
      dut.clock.step()
      expectIJK(dut, 0, 0, 0, 0)
      expectIJK(dut, 1, 1, 0, 0)
      expectIJK(dut, 2, 2, 0, 0)
      dut.io.addrGen.valid.expect(true.B)
    }
    test(new IndexGenerator(pipe=true)).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      testFun(dut)
    }
    test(new IndexGenerator(pipe=false)) { dut =>
      testFun(dut)
    }
  }

  "Index  generator" should "use ready/valid signalling when pipelined" in {
    test(new IndexGenerator(pipe=true)) { dut =>
      //Poke values, but make the consumer non-ready
      dut.io.in.valid.poke(true.B)
      dut.io.addrGen.ready.poke(false.B)
      dut.io.in.ready.expect(true.B)
      dut.io.addrGen.valid.expect(false.B)
      pokeIJK(dut, 0, 0, 0, 0)
      pokeIJK(dut, 1, 1, 0, 0)
      pokeIJK(dut, 2, 2, 0, 0)
      for(i <- 3 until NUM_MEMORY_BANKS) {
        dut.io.in.bits.validIjk(i).poke(false.B)
      }
      dut.clock.step()
      dut.io.addrGen.valid.expect(true.B)
      dut.io.in.ready.expect(false.B)

      //Poke new values, expect the old values to stay
      pokeIJK(dut, 0, 0, 1, 1)
      pokeIJK(dut, 1, 1, 1, 1)
      pokeIJK(dut, 2, 2, 1, 1)
      dut.clock.step()
      dut.io.addrGen.valid.expect(true.B)
      dut.io.in.ready.expect(false.B)
      expectIJK(dut, 0, 0, 0, 0)
      expectIJK(dut, 1, 1, 0, 0)
      expectIJK(dut, 2, 2, 0, 0)

      //Make the consumer ready
      dut.io.addrGen.ready.poke(true.B)
      dut.io.in.ready.expect(true.B)
      dut.clock.step()

      //new values should now be output
      expectIJK(dut, 0, 0, 1, 1)
      expectIJK(dut, 1, 1, 1, 1)
      expectIJK(dut, 2, 2, 1, 1)
      dut.io.addrGen.valid.expect(true.B)
      dut.io.in.valid.poke(false.B)
      pokeIJK(dut, 1, 2, 3, 4)
      dut.clock.step()

      //Should no longer be valid, should keep old values
      dut.io.addrGen.valid.expect(false.B)
      expectIJK(dut, 0, 0, 1, 1)
      expectIJK(dut, 1, 1, 1, 1)
      expectIJK(dut, 2, 2, 1, 1)
    }
  }

  "Index generator" should "override validIndices when outside the legal range" in {
    test(new IndexGenerator(pipe=false)) {dut =>
      dut.io.addrGen.ready.poke(true.B)
      dut.io.in.valid.poke(true.B)

      //Whenever i>=NELX, j>=NELY or k>=NELZ, the output should be set invalid
      pokeIJK(dut, 0, 0, 0, NELZ)
      pokeIJK(dut, 1, 0, NELY, 0)
      pokeIJK(dut, 2, 0, NELY, NELZ)
      pokeIJK(dut, 3, NELX, 0, 0)
      pokeIJK(dut, 4, NELX, 0, NELZ)
      pokeIJK(dut ,5, NELX, NELY, 0)
      pokeIJK(dut, 6, NELX, NELY, NELZ)
      pokeIJK(dut, 7, NELX-1, NELY-1, NELZ-1)
      dut.clock.step()
      for(i <- 0 until 7) {
        dut.io.addrGen.bits.validIndices(i).expect(false.B)
      }
      dut.io.addrGen.bits.validIndices(7).expect(true.B)
    }
  }
}
