package stages

import chisel3._
import execution._

/**
 * A module encompassing the decode and execute stages. Mainly used for testing the two stages together.
 */
class DecodeExecute(v3: Boolean = false) extends Module {
  val io = IO(new Bundle{
    val in = Flipped(new IfIdIO)
    val idctrl = new IdControlIO
    val exctrl = new ExControlIO
    val out = new ExWbIO
    val execStall = Output(Bool())
  })

  val decode = Module(new Decode)
  val execute = Module(new Execute)
  val control = Module(new Control)

  //Connect outputs out to top
  io.idctrl <> decode.io.ctrl
  io.exctrl <> execute.io.ctrl

  io.in <> decode.io.fe
  decode.io.ex <> execute.io.id
  execute.io.wb <> io.out

  control.io.id <> decode.io.ctrl
  control.io.ex <> execute.io.ctrl

  execute.io.fwd.rs1swap := false.B
  execute.io.fwd.rs2swap := false.B
  io.execStall := control.io.ex.stall


  /** DONTCARES */
  decode.io.mem := DontCare
  decode.io.memWb := DontCare
  decode.io.wb := DontCare

  execute.io.fwd := DontCare
  control.io.mem.wqCount := 0.U
  control.io.mem.rqCount := 0.U
}
