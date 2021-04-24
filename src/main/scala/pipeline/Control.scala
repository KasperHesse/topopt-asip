package pipeline

import chisel3._

class ControlIO extends Bundle {
  val id = Flipped(new IdControlIO)
  val ex = Flipped(new ExControlIO)
  val fe = Flipped(new IfControlIO)
}

class Control extends Module {
  val io = IO(new ControlIO)

  // --- WIRES AND SHORTHANDS ---
  /** Instruction load signal to decode stage. This register is used to keep the value high while loading */
  val iload = RegInit(false.B)
  /** Execution stall register. Used to hold the estall value for multiple cycles */
  val estall = RegInit(false.B)
  /** Shortcut to executing thread's control signal */
  val execThread = io.id.threadCtrl(io.id.execThread)
  /** Shortcut to memory access thread's control signals */
  val memThread = io.id.threadCtrl(io.id.execThread + 1.U(1.W)) //Adding one should lap back to 0/inc to 1
  /** Stall signal going into execute stage and executing thread */
  val execStall = WireDefault(false.B)
  /** Stall signal going into memory stage and memory access thread */
  val memStall = WireDefault(false.B)


  // --- CONNECTIONS ---
  //We need these assignments or the firrtl checker will be angry
  io.id.threadCtrl(0).stall := false.B
  io.id.threadCtrl(1).stall := false.B
  io.id.stall := false.B

  io.fe.stall := false.B
  io.id.iload := false.B

  execThread.stall := execStall
  io.ex.stall := execStall
  memThread.stall := memStall

  // --- LOGIC ---
  //Logic signals for easier decode of O-type instructions
  val Oinst = io.fe.instr.asTypeOf(new OtypeInstruction)
  val isInstr: Bool = Oinst.iev === OtypeIEV.INSTR
  val isEnd = Oinst.se === OtypeSE.END
  private val isOtype: Bool = Oinst.fmt === InstructionFMT.OTYPE
  private val isStart: Bool = Oinst.se === OtypeSE.START

  // --- INSTRUCTION FETCH/DECODE CONTROL SIGNALS ---
  //When idling and instructions are available, load them in
  when((isStart && isInstr && isOtype && io.id.state === DecodeState.sIdle) || iload) {
    io.id.iload := true.B
    iload := true.B
  }
  //When we read the final instruction, stop loading after this one
  when(isEnd && isInstr && isOtype && io.id.state === DecodeState.sLoad) {
    iload := false.B
  }

  // --- EXECUTE STAGE STALLS ---
  //These stall signals are active if the upcoming instruction relies on data that is currently being computed but not yet finished (data hazards)
  val dataHazardStall = WireDefault(false.B)
  val dataHazardVec =  for(eha <- io.ex.queueHead) yield {
    eha.valid && eha.dest.subvec === execThread.rs1.subvec && ((eha.dest.rf === execThread.rs1.rf && eha.dest.reg === execThread.rs1.reg) || (eha.dest.rf === execThread.rs2.rf && eha.dest.reg === execThread.rs2.reg))
  }
  when(dataHazardVec.reduce((a, b) => a|b) && execThread.state === ThreadState.sExec) {
    dataHazardStall := true.B
  }

  //When decoded instruction is not the same opcode as instruction in execute stage, stall execution decode until destination queue is empty
  val destQueueStall = WireDefault(false.B)
  val validHead = {for(head <- io.ex.queueHead) yield { //We don't need to stall if queue is non-empty but no entries in dQueue are invalid (this happens when the final value in dQueue is being output)
    head.valid
  }}.reduce((a,b) => a|b)
  when(execThread.state === ThreadState.sExec && execThread.firstCycle && execThread.op =/= io.ex.op && !io.ex.empty && validHead) {
    destQueueStall := true.B
  }

  //When decoding XX, SX and SS instructions, stall for one clock cycle if the executing instruction
  //does not exactly match the decoding instruction. This ensures proper arrival into destination queue
  //Stalls on *second* cycle after we've ensured delivery
  val isSingleCycleOp: Bool = (execThread.rtypemod === RtypeMod.XX) || (execThread.rtypemod === RtypeMod.SX) || (execThread.rtypemod === RtypeMod.SS)
  val newOp: Bool = execThread.op =/= RegNext(execThread.op)
  val singleCycleAwaitStall = WireDefault(false.B)
  when(RegNext(isSingleCycleOp) && newOp && execThread.state === ThreadState.sExec) {
    singleCycleAwaitStall := true.B
  }



  execStall := dataHazardStall | destQueueStall | singleCycleAwaitStall



}
