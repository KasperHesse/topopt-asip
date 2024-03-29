package execution

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util.{Decoupled, log2Ceil}
import memory.{AddressGenProducerIO, IJKgeneratorConsumerIO, ReadQueueBundle, WriteQueueBundle}
import utils.Config._
import utils.Fixed._

/**
 * Interface between the instruction fetch and instruction decode stages.
 * Instantiate as-is in the fetch stage, use Flipped() in the decode stage
 */
class IfIdIO extends Bundle {
  /** Instruction fetched from IM */
  val instr = Output(UInt(INSTRUCTION_WIDTH.W))
  /** Current value of the program counter */
  val pc = Output(UInt(32.W))
  /** Address to jump to when branching */
  val branchTarget = Input(UInt(32.W))
  /** Whether to go to branch target (true) or not (false) */
  val branch = Input(Bool())
}

/**
 * Interface between the instruction decode stage and the vector execution stage.
 * Instantiate as-is in the decode stage, and use Flipped() in the Ex stage
 */
class IdExIO extends Bundle {
  /** Vector of the first operands */
  val a = Output(Vec(NUM_PROCELEM, SInt(FIXED_WIDTH.W)))
  /** Vector of second operands */
  val b = Output(Vec(NUM_PROCELEM, SInt(FIXED_WIDTH.W)))
  /** Destination register and subvector of the result */
  val dest = Output(new RegisterBundle())
  /** Register indicating where the first operand came from */
  val rs1 = Output(new RegisterBundle())
  /** Register indicating where the second operand came form */
  val rs2 = Output(new RegisterBundle())
  /** Operation to execute. See [[Opcode]] */
  val op = Output(Opcode())
  /** UInt version of opcode. Debug purposes only */
  val opUInt = Output(UInt(6.W))
  /** Number of multiply-accumulates to perform before releasing the result. Only used when MAC operations are performed. */
  val macLimit = Output(UInt(log2Ceil(NDOFLENGTH/NUM_PROCELEM+1).W))
  /** Signals to the execution unit that the incoming operation is valid */
  val valid = Output(Bool())
  /** Immediate value */
  val imm = Output(SInt(FIXED_WIDTH.W))
  /** Asserted when the immediate should be used instead of a-values */
  val useImm = Output(Bool())
}

/**
 * I/O ports for the KE module
 */
class KEIO extends Bundle {
  /** Input: X-coordinate of the submatrix to be processed */
  val keX = Input(UInt(log2Ceil(KE_SIZE/NUM_PROCELEM).W))
  /** Input: Y-coordinate of the submatrix to be processed */
  val keY = Input(UInt(log2Ceil(KE_SIZE/NUM_PROCELEM).W))
  /** Input: The column of the submatrix to be extracted */
  val keCol = Input(UInt(log2Ceil(NUM_PROCELEM).W))
  /** Which KE iteration value to use for the submatrix being extracted */
  val keIter = Input(UInt(2.W))
  /** Output: one column of a submatrix, as specified by the input variables */
  val keVals = Output(Vec(NUM_PROCELEM, SInt(FIXED_WIDTH.W)))
}

/**
 * Interface between the instruction decode stage and memory stage.
 * Instantiate as-is in decode stage and use FLipped() in memory module
 */
class IdMemIO extends Bundle {
  /** Values used when performing .vec operations that go directly to the address generator */
  val vec = Decoupled(new AddressGenProducerIO)
  /** Values used when performing .dof operations that go through the EDOF generator */
  val edof = Decoupled(new IJKgeneratorConsumerIO)
  /** Values used when performing .elem, .fcn, .edn1, .edn2 and .sel operations that go to neighbour generator */
  val neighbour = Decoupled(new IJKgeneratorConsumerIO)
  /** Data to be written when performing store operations */
  val writeQueue = Decoupled(new WriteQueueBundle)
  /** Destination register and auxilliary information, to be used when storing data into register files */
  val readQueue = Decoupled(new ReadQueueBundle)
  /** Load/store flag used to toggle we on memory */
  val ls = Output(StypeLoadStore())
}

/**
 * Interface between the vector execution stage and the writeback stage.
 * Instantiate as-is in the execute stage and use Flipped() in the writeback stage
 */
class ExWbIO extends Bundle {
  /** The result produced in the execute stage */
  val res = Output(Vec(NUM_PROCELEM, SInt(FIXED_WIDTH.W)))
  /** Destination register and subvector of result */
  val dest = Output(new RegisterBundle())
  /** Indicates that the result is valid and should be stored in the register file */
  val valid = Output(Bool())
  /** Asserted if the result should be and-reduced into a single value and stored into an s-register */
  val reduce = Output(Bool())
}

/**
 * Interface between execution stage and forwarding module.
 * Instantiase as-is in execute stage, use Flipped() in the forwarding module
 */
class ExFwdIO extends Bundle {
  /** The register source of the first operand */
  val rs1 = Output(new RegisterBundle())
  /** The register source of the second operand */
  val rs2 = Output(new RegisterBundle())
  /** Whether to use the forwarded value (true) or current value (false) */
  val rs1swap = Input(Bool())
  /** Whether to use the forwarded value (true) or current value (false) */
  val rs2swap = Input(Bool())
  /** The data to use instead of 'a' if rs1swap is asserted */
  val rs1newData = Input(Vec(NUM_PROCELEM, SInt(FIXED_WIDTH.W)))
  /** The data to use instead of 'b' if rs2swap is asserted */
  val rs2newData = Input(Vec(NUM_PROCELEM, SInt(FIXED_WIDTH.W)))
}

/**
 * Interface between writeback stage and forwarding module
 * Instantiate as-is in the writeback stage, use Flipped() in forwarding module
 */
class WbFwdIO extends Bundle {
  /** The destinations for all values currently stored in writeback buffer */
  val rd = Output(Vec(VREG_DEPTH/NUM_PROCELEM, new RegisterBundle()))
  /** Whether the given writeback destination is actually valid (true) or not (false) */
  val rdValids = Output(Vec(VREG_DEPTH/NUM_PROCELEM, Bool()))
  /** The data stored in writeback buffer, used for forwarding purposes */
  val wbData = Output(Vec(VREG_DEPTH/NUM_PROCELEM, Vec(NUM_PROCELEM, SInt(FIXED_WIDTH.W))))
}

/** Interface between writeback stage and register files in instruction decode stage.
 * Instantiate as-is in the writeback stage, use Flipped() in decode stage
 * */
class WbIdIO extends Bundle {
  /** The data to be written into register file */
  val wrData = Output(Vec(VREG_DEPTH, SInt(FIXED_WIDTH.W)))
  /** Which register to write into */
  val rd = Output(new RegisterBundle)
  /** Write enable / valid bit */
  val we = Output(Bool())
}

/**
 * Interface between the vector execution stage and the control module.
 * Use as-is in the execute stage, use Flipped() in control stage
 */
class ExControlIO extends Bundle {
  /** Whether the ordinary destination queue is empty */
  val empty = Output(Bool())
  /** Whether the MAC destination queue is empty */
  val macEmpty = Output(Bool())
  /** Whether the pipeline should be stalled. If true, deasserts valid for all operations going into MPU */
  val stall = Input(Bool())
  /** Opcode of the currently executing instruction */
  val op = Output(Opcode())
  /** Values in the ExecuteHazardAvoider, used to prevent execute hazards */
  val queueHead = Output(Vec(4, new ValidRegisterBundle))
}

/**
 * Inteface between the instruction decode stage and the control module.
 * Use as-is in the decode stage, use Flipped() in the control module
 */
class IdControlIO extends Bundle {
  /** Asserted whenever the decode stage can load new instructions into its instruction buffer */
  val iload = Input(Bool())
  /** The instruction currently present at the pipeline register */
  val instr = Output(UInt(INSTRUCTION_WIDTH.W))
  /** The current state of the decoder*/
  val state = Output(DecodeState())
  /** The decoder state as UInt. For debug purposes only */
  val stateUint = Output(UInt(4.W))
  /** Value indicating whether thread 0 or thread 1 is the currently executing thread */
  val execThread = Output(UInt(1.W))
  /** Whether a branch was taken */
  val branch = Output(Bool())
  /** Asserted when the decode unit should stall. This can either be because the Ex unit is not finished processing,
   * or because data has yet to be transferred from memory into the register file. */
  val stall = Input(Bool())
  /** Control signals originating from threads inside of decode stage */
  val threadCtrl = Vec(2, new ThreadControlIO)
}

/**
 * Interface between instruction fetch stage and control module
 * Use as-is in fetch stage, use Flipped() in control module
 */
class IfControlIO extends Bundle {
  /** Instruction load signal. If toggled, will increment PC */
  val iload = Input(Bool())
}

/**
 * Interface between memory module and control module.
 * Use as-is in memory module, use Flipped() in control module
 */
class MemControlIO extends Bundle {
  /** Number of elements currently in the read queue */
  val rqCount = Output(UInt(4.W))
  /** Number of elements currently in the write queue */
  val wqCount = Output(UInt(4.W))
}

/**
 * Interface between threads and the control module.
 * Use as-is in the decode stage, use Flipped() in control module
 */
class ThreadControlIO extends Bundle {
  /** Current state for the thread */
  val state = Output(ThreadState())
  /** Current state as UInt, decoded for debug purposes */
  val stateUint = Output(UInt(8.W))
  /** Opcode of the currently executing Rtype instruction */
  val op = Output(Opcode())
  /** R-type mod field of the currently executing instruction */
  val rtypemod = Output(RtypeMod())
  /** Instruction format of the current instruction */
  val fmt = Output(InstructionFMT())
  /** Asserted when the thread should stall.*/
  val stall = Input(Bool())
  /** Register bundle for source 1 of the incoming instruction */
  val rs1 = Output(new RegisterBundle)
  /** Register bundle for source 2 of the incoming instruction */
  val rs2 = Output(new RegisterBundle)
  /** Asserted when the ordinary destination queue in the execute stage is empty */
  val empty = Input(Bool())
  /** Asserted when the MAC destination queue is empty */
  val macEmpty = Input(Bool())

  val finalCycle = Output(Bool()) //TODO remove this
  val firstCycle = Output(Bool()) //TODO remove this
}

/**
 * A bundle encoding the information necessary to specify a register register
 */
class RegisterBundle extends Bundle {
  /** Register number */
  val reg = UInt(log2Ceil(NUM_VREG).W)
  /** Subvector of that register */
  val subvec = UInt(log2Ceil(VREG_DEPTH/NUM_PROCELEM).W)
  /** The register file that this bundle relates to.
   * When stored in the x-reg file, the subvector is ignored.
   * When stored in the scalar reg file, only the element at [0] is stored */
  val rf = RegisterFileType()
  /** UInt value of register file type. Used for debugging purposes. TODO remove it */
  val rfUint = UInt(4.W)
}

/** Bundle storing the information used in the [[DestinationQueue]] module.
 *  Consists of a [[RegisterBundle]] affixed with a valid flag
 */
class ValidRegisterBundle extends Bundle {
  /** Destination */
  val dest = new RegisterBundle
  /** Whether this entry is valid */
  val valid = Bool()
}


object RegisterFileType extends ChiselEnum {
  val SREG, VREG, XREG, KREG = Value
}

/**
 * Output ports for the timing module to board and HSMC pins
 * @param clkFreq Clock frequency being used in the design
 */
class TimingOutput(val clkFreq: Int) extends Bundle {
  /** Milli-second value */
  val ms = Output(UInt(log2Ceil(1000).W))
  /** Seconds value */
  val s = Output(UInt(log2Ceil(60).W))
  /** Minutes value */
  val m = Output(UInt(8.W))
  /** Blinking 'alive' led */
  val blink = Output(Bool())
  /** Ground signal for the millisecond values */
  val msGround = Output(UInt(log2Ceil(1000).W))
  /** Ground signal for the seconds values */
  val sGround = Output(UInt(log2Ceil(60).W))
}

/**
 * I/O bundle between [[Decode]] and [[utils.TimingModule]].
 * Instantiate as-is in Decode stage, use Flipped() in timing module
 */
class IdTimingIO extends Bundle {
  /** Enable signal. When pulled high, the timer is activated */
  val en = Output(Bool())
  /** Clear signal. When pulled high, all registers are reset. Must be pulled low to restart timing */
  val clr = Output(Bool())
}

/**
 * I/O ports for [[utils.TimingWrapper]]
 * @param clkFreq Clock frequency being used in the design
 */
class TimingWrapperIO(val clkFreq: Int) extends Bundle{
  val id = Input(new IdTimingIO)
  val out = new TimingOutput(clkFreq)
}
