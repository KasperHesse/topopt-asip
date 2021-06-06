
import chisel3._
import memory.{MemWbIO, MemoryStage}
import pipeline._

/**
 * The top level module for the topological optimizer
 * @param IMsize The number of instructions in instruction memory
 * @param IMinitFileLocation Location of the instruction memory initialization file
 * @param wordsPerBank Number of elements in each memory bank
 * @param memInitFileLocation Location of memory initialization files. This is the relative path. Each memory bank initialization file must be named
 *                            'membank_x.txt', where x is in the range [0;NUM_MEMORY_BANKS[. If eg 'resources/meminit' is given as parameter, the first file should be named 'resources/meminit/membank_0.txt'
 */
class TopLevel(IMsize: Int, IMinitFileLocation: String, wordsPerBank: Int, memInitFileLocation: String) extends Module {
  val io = IO(new Bundle {
    val exout = new ExWbIO
  })

  val fetch = Module(new Fetch(IMsize, IMinitFileLocation))
  val decode = Module(new Decode)
  val execute = Module(new Execute)
  val writeback = Module(new Writeback)
  val forward = Module(new Forwarding)
  val mem = Module(new MemoryStage(wordsPerBank, memInitFileLocation))
  val control = Module(new Control)

  fetch.io.id <> decode.io.fe
  decode.io.ex <> execute.io.id
  execute.io.wb <> writeback.io.ex
  writeback.io.id <> decode.io.wb
  writeback.io.fwd <> forward.io.wb
  forward.io.ex <> execute.io.fwd
  decode.io.mem <> mem.io.id
  decode.io.memWb <> mem.io.wb

  control.io.fe <> fetch.io.ctrl
  control.io.id <> decode.io.ctrl
  control.io.ex <> execute.io.ctrl
  control.io.mem <> mem.io.ctrl

  io.exout <> execute.io.wb
}
