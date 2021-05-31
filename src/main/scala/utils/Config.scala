package utils

import chisel3.SInt
import utils.Fixed.{FIXED_WIDTH, FRAC_WIDTH, INT_WIDTH}

/**
 * A general placeholder class, used for defining all constants used throughout the workflow
 */
object Config {
  //Configuration values
  /** What type of divisor to use if nothing is explicitly stated */
  val DIVTYPE = DivTypes.NEWTONRAPHSON
  /** What type of multiplier to use if nothing is explicitly stated when instantiating */
  val MULTYPE = MulTypes.SINGLECYCLE
  /** How many stages to use in Newton-Raphson divisors stage 3 */
  var NRDIV_STAGE3_REPS = 3

  //Compile-time constant definitions
  /** Number of element in the x-direction */
  var NELX = 6
  /** Number of elements in the y-direction */
  var NELY = 6
  /** Number of element in the z-direction */
  var NELZ = 6
  /** The greatest of the three element dimensions */
  var GDIM = math.max(NELX, math.max(NELY, NELZ))

  /** Number of nodes in the x-direction */
  var NX = NELX + 1
  /** Number of nodes in the y-direction */
  var NY = NELY + 1
  /** Number of nodes in the z-direction */
  var NZ = NELZ + 1

  /** Total number of elements in the design domain */
  var NELEM = NELX * NELY * NELZ
  /** Total number of element DOF in the design domain */
  var NDOF = 3 * NX * NY * NZ

  /** The number of elements in the vector register file */
  var NUM_VREG = 32
  /** The number of vector register slots that are adressible from instructions */
  var NUM_VREG_SLOTS = 4
  /** The number of values stored in each entry in the vector register file */
  var VREG_DEPTH = 24
  /** The number of X-value registers in the x register file. */
  var NUM_XREG = 8
  /** The number of values stored in each entry in the x-register file */
  var XREG_DEPTH = NUM_VREG/NUM_VREG_SLOTS
  /** The number of elements in the scalar register file */
  var NUM_SREG = 16
  /** The number of processing elements used in the design. Determines the width of vectors carrying values between modules. */
  var NUM_PROCELEM = 8
  /** The number of subvectors in each vector register */
  var SUBVECTORS_PER_VREG = VREG_DEPTH/NUM_PROCELEM
  /** The width (number of registers) of each vector register slot */
  var VREG_SLOT_WIDTH = NUM_VREG/NUM_VREG_SLOTS
  /** The width and height of the KE matrix */
  var KE_SIZE = 24
  /** The bitwidth of an instruction */
  var INSTRUCTION_WIDTH = 32
  /** The total number of elements stored in a vector register slot
   * This value is the increment amount used when processing vectors elementwise */
  var ELEMS_PER_VSLOT = VREG_SLOT_WIDTH*VREG_DEPTH
  /** Number of elements parsed when performing an operation over NELEM-long vectors */
  var NELEMLENGTH = if(NELEM % ELEMS_PER_VSLOT == 0) NELEM else (NELEM/ELEMS_PER_VSLOT+1)*ELEMS_PER_VSLOT
  /** Number of elements parsed when performing an operation over NDOF-long vectors */
  var NDOFLENGTH = if(NDOF % ELEMS_PER_VSLOT == 0) NDOF else (NDOF/ELEMS_PER_VSLOT+1)*ELEMS_PER_VSLOT

  /** The number of memory banks used / the number of values that can be loaded from memory at once */
  var NUM_MEMORY_BANKS = 8
  /** Width of memory addresses */
  var MEM_ADDR_WIDTH = 32

  /** Simulation flag. Assert inside of a tester to use simulation-specific functionality */
  var SIMULATION = false

  checkRequirements()

  /** Checks if all configurations requirements are held. This *must* be called in a tester if config values are changed */
  def checkRequirements(): Unit = {
    require(NUM_VREG >= NUM_VREG_SLOTS, "Must have more vector registers than register slots")
    require(NUM_VREG % NUM_VREG_SLOTS == 0, "Number of vector registers must me a multiple of vector register slots")
    require(KE_SIZE == VREG_DEPTH, s"KE_SIZE must equal VREG_DEPTH for proper matrix-vector products. They are $KE_SIZE, $VREG_DEPTH")
    require(NRDIV_STAGE3_REPS > 0, "Newton-Raphson division requires at least one iteration in stage 3")
    require(XREG_DEPTH == VREG_SLOT_WIDTH, s"XREG_DEPTH($XREG_DEPTH) and VREG_SLOT_WIDTH($VREG_SLOT_WIDTH) must be equal")
    require(VREG_SLOT_WIDTH == NUM_VREG/NUM_VREG_SLOTS, s"VREG_SLOT_WIDTH($VREG_SLOT_WIDTH) must equal NUM_VREG($NUM_VREG)/NUM_VREG_SLOTS($NUM_VREG_SLOTS)")
    require(SUBVECTORS_PER_VREG == VREG_DEPTH/NUM_PROCELEM, s"SUBVECTORS_PER_VREG must equal VREG_DPETH/NUM_PROCELEM. Got $SUBVECTORS_PER_VREG, should be ${VREG_DEPTH/NUM_PROCELEM}")
    require(NUM_PROCELEM == VREG_SLOT_WIDTH, "NUM_PROCELEM and VREG_SLOT_WIDTH must be the same. VREG_SLOT_WIDTH is a calculated property")
    require(ELEMS_PER_VSLOT == VREG_SLOT_WIDTH*VREG_DEPTH, "ELEMS_PER_VSLOT must equal VREG_SLOT_WIDTH*VREG_DEPTH. ELEMS_PER_SLOT is a calculated property")
    require(NELEM > ELEMS_PER_VSLOT, s"NELEM(${NELEM}) must be greater than ELEMS_PER_VSLOT(${ELEMS_PER_VSLOT}) for proper computation")
    require(NDOF > NELEM, "NDOF must be greater than NELEM")
    require(NUM_SREG <= 16, "NUM_SREG must be <= 16 as only 4 bits are used for register fields")
    require(XREG_DEPTH >= 6, s"XREG_DEPTH must be greater than or equal to 6. Got $XREG_DEPTH")
    require(VREG_DEPTH % NUM_MEMORY_BANKS == 0, s"VREG_DEPTH($VREG_DEPTH) must be an integer multiple of NUM_MEMORY_BANKS($NUM_MEMORY_BANKS)")
    require(VREG_DEPTH == 24, s"VREG_DEPTH($VREG_DEPTH) must be exactly 24 to correctly perform DOF loads")

    require(NELX % 2 == 0, s"NELX($NELX) must be even")
    require(NELY % 2 == 0, s"NELY($NELY) must be even")
    require(NELZ % 2 == 0, s"NELZ($NELZ) must be even")

    NELEMLENGTH = if(NELEM % ELEMS_PER_VSLOT == 0) NELEM else (NELEM/ELEMS_PER_VSLOT+1)*ELEMS_PER_VSLOT
    NDOFLENGTH = if(NDOF % ELEMS_PER_VSLOT == 0) NDOF else (NDOF/ELEMS_PER_VSLOT+1)*ELEMS_PER_VSLOT
  }

  /**
   * Applies a configuration which requires less resources to elaborate, speeding up execution time
   *
   * @param largeNumbers whether the simulation should support large numbers by increasing INT_WIDTH (default false)
   */
  def simulationConfig(largeNumbers: Boolean = false): Unit = {
    SIMULATION = true
    NUM_VREG = 24
    VREG_DEPTH = 24
    VREG_SLOT_WIDTH = 6
    XREG_DEPTH = 6
    ELEMS_PER_VSLOT = VREG_DEPTH*VREG_SLOT_WIDTH
    KE_SIZE = 24
    NUM_PROCELEM = 6
    SUBVECTORS_PER_VREG  = VREG_DEPTH/NUM_PROCELEM
    FIXED_WIDTH = if(largeNumbers) 32 else 26
    INT_WIDTH = if(largeNumbers) 16 else 10
    FRAC_WIDTH = 15
    Config.checkRequirements()
  }
}

object DivTypes extends Enumeration {
  type DivisorType = Value
  val NEWTONRAPHSON, SRT = Value
}

object MulTypes extends Enumeration {
  type MulType = Value
  val SINGLECYCLE, MULTICYCLE = Value
}
