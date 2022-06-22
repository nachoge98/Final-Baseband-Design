package chipyard.baseband//ErrCorr//Error Correction


import chisel3._
import chisel3.util._
//import chisel3.experimental.FixedPoint
import chisel3.util.{log2Ceil, Decoupled}
//import dsptools.numbers._

// Inputs from the Rx Chain
class CBInputBundle extends Bundle {//correction block inputs

  val syndrome = Flipped(Decoupled(UInt(24.W)))
  val length = Flipped(Decoupled(UInt(8.W)))
  val soft_reset = Input(Bool())

  override def cloneType: this.type = CBInputBundle().asInstanceOf[this.type]
}
object CBInputBundle {
  def apply(): CBInputBundle = new CBInputBundle
}


//Data Outputs
class CBOutputBundle extends Bundle {//error correction base output to the bit sequencer.
  
  val val1 = Output(UInt(9.W))
  val val2 = Output(UInt(9.W))
  val sol  = Decoupled(UInt(2.W)) //flags to indicate if no solution (00), one error (10,01) or two errors (11)
  
  override def cloneType: this.type = CBOutputBundle().asInstanceOf[this.type]
}
object CBOutputBundle {
  def apply(): CBOutputBundle = new CBOutputBundle
}

class CBIO extends Bundle {
  val in = CBInputBundle() //inputs from the Rx chain
  val out = CBOutputBundle() //inputs from the controller

  override def cloneType: this.type = CBIO().asInstanceOf[this.type]
}

object CBIO{
  def apply(): CBIO = new CBIO
}

class CorrectionBlock extends Module {
  
  def selectBytes(  //extracts 25 bits(taken from register), the selected bits are the first 25 LSBs after the shift
      register: UInt, //the register from which to select
      shift: UInt //shift from the right side
  ): (UInt) = {
    
    val outputRegister = (VecInit(Seq.fill(25)(false.B)))

    for(i <- 0 to (24)){
      when(register(i.asUInt+shift) === 1.U){
         outputRegister(i) := true.B
       }.otherwise{
         outputRegister(i) := false.B
       }
    }
        
    (outputRegister.asUInt)
  }//end of the def selectBytes


  def extract_2LSBOne(  //
      register: UInt //register length 25.
  ): (UInt) = {
    
    val pos = PriorityEncoder(register)
    val pos1 = Wire(UInt(5.W))
    pos1 := 0.U
    
    
    for(i <- 0 to (24)){
      when(register(24-i) === 1.U && (24.U-i.asUInt) =/= pos){
        pos1 := 24.U-i.asUInt
      }
    }

    (pos1)
  }//end of the def extract_LSBOne

  
  val io = IO(new CBIO) // IO BUNDLE
  
  //WIRES IO
  val in_syndrome = Wire(UInt(24.W))
  in_syndrome := io.in.syndrome.bits
  val in_syndrome_valid = Wire(Bool())
  in_syndrome_valid := io.in.syndrome.valid
  //val in_syndrome_ready = Wire(Bool())
  
  val in_length = Wire(UInt(8.W))
  in_length := io.in.length.bits
  val in_length_valid = Wire(Bool())
  in_length_valid := io.in.length.valid
  //val in_length_ready = Wire(Bool())
  
  //val out_sol = RegInit(0.U(2.W))
  val out_sol_valid = RegInit(false.B)
  val out_sol_ready = Wire(Bool())
  out_sol_ready := io.out.sol.ready
  
  
  
  //REGISTERS:
  //val error_vector = RegInit(5517840.U(288.W))//5517840 in hex: 543210
  //val error_vector_copy = RegInit(0.U(288.W))
  
  val sel_bits = RegInit(0.U(25.W)) //0 concatenado con el reminder (24), para su estado inicial.
  val max_shift = RegInit(15.U(9.W))//min length = 40, max length = 288 sin tener en cuenta los 25 del polinomio crc, 15=(40-25), 263=(288-25)
  val shift = RegInit(0.U(9.W))//current shift from right side.
  val crc_polynomy = "b1000000000000011001011011".U //[1000000000000011001011011]
  val fixed_position = RegInit(5.U(9.W))
    
  val idle :: reminder_ones :: fix_position :: xor_2 :: ones_2 :: restore :: xor_restore :: ones_restore :: xor_1 :: ones_1 :: finished ::  Nil = Enum(11)
  val state = RegInit(idle)
  
  val val1 = RegInit(0.U(9.W))
  val val2 = RegInit(0.U(9.W))
  val sol = RegInit(0.U(2.W))
  val solution_found = RegInit(false.B)
  val multiple_solutions_found = RegInit(false.B)

  val restore_bits = RegInit(1608.U(25.W)) //0000000000000011001001000 --> 1608
  val single_errCorr_bits = RegInit(1608.U(25.W)) 
  val restore_shift = RegInit(0.U(9.W))
  
  val soft_reset = RegInit(false.B)
  
  when(io.in.soft_reset && state =/= idle){
    soft_reset := true.B
  }
  
  //val lsbOne = RegInit(0.U(5.W))//to save a shift of max 25 i need 5 bits.
  //val lsbOne2 = RegInit(0.U(5.W))
  //val ones = RegInit(0.U(5.W))
  
  //STATES MACHINE
  
  val num_ones = PopCount(sel_bits)
  
  when(state === idle){//STATE: IDLE
    
    out_sol_valid := false.B
    shift := 0.U
    soft_reset := false.B
    //
    when(in_length_valid){
      max_shift := in_length*8.U+15.U
    }
    when(in_syndrome_valid){
      state := reminder_ones
      //
      sol := 0.U
      sel_bits := in_syndrome
      single_errCorr_bits := in_syndrome
    }
    
  }.elsewhen(state === reminder_ones){//STATE: REMINDER_ONES
  
    val lsb_One = PriorityEncoder(sel_bits)
    val lsb_One2 = extract_2LSBOne(sel_bits)
    
    when(num_ones === 2.U){
      val1 := lsb_One2
      val2 := lsb_One
      sol := 3.U
      solution_found := true.B
    }.elsewhen(num_ones === 1.U){
      val2 := lsb_One
      sol := 2.U
      solution_found := true.B
    }
    state := fix_position
    
  }.elsewhen(state === fix_position){//STATE: FIX POSITION
  
    val lsb_One = PriorityEncoder(sel_bits)
    val lsb_One2 = extract_2LSBOne(sel_bits)
    val fix_pos = shift + lsb_One
    val new_shift = shift + lsb_One2
  
    when(soft_reset){
      state := idle
    }.elsewhen(new_shift > max_shift){
      state := restore
    }.otherwise{
      when(num_ones === 1.U){
        sel_bits := sel_bits >> lsb_One
        shift := fix_pos
        state := xor_restore
      }.otherwise{
        restore_bits := sel_bits >> lsb_One //saving the previous state
        restore_shift := fix_pos

        sel_bits := sel_bits >> lsb_One2
        shift := new_shift
        fixed_position := fix_pos
        state := xor_2
      }
    }
  
  }.elsewhen(state === xor_2){//STATE: XOR_2
  
    sel_bits := sel_bits ^ crc_polynomy
    state := ones_2
    
  }.elsewhen(state === ones_2){//STATE: ONES_2
  
    val lsb_One = PriorityEncoder(sel_bits)
    val new_shift = shift + lsb_One
    val condition = new_shift > max_shift
    
    when(num_ones === 1.U && !solution_found){
      val1 := new_shift
      val2 := fixed_position
      sol := 3.U
      solution_found := true.B
    }.elsewhen(num_ones === 0.U && !solution_found){
      val2 := fixed_position
      sol := 2.U
      solution_found := true.B
    }.elsewhen(num_ones <= 1.U){
      multiple_solutions_found := true.B
      sol := 0.U
    }
    
    when(condition){
      state := restore
    }.elsewhen(!condition){
      shift := new_shift
      sel_bits := sel_bits >> lsb_One
      state := xor_2
    }
    /*
    when(condition && !multiple_solutions_found){
      state := restore
    }.elsewhen(!condition && !multiple_solutions_found){
      shift := new_shift
      debug := debug >> lsb_One
      //new_state := xor
    }.otherwise{ //in case multiple_solutions have been found
      //new_state := finished
    }
    */
 
    //state := idle
    
  }.elsewhen(state === restore){//STATE: RESTORE
    
    when(multiple_solutions_found){
      sol := 0.U //to indicate that a valid solution was not found
      state := finished
    }.otherwise{
      when(shift === restore_shift){
        sol := 0.U
        state := finished
      }.otherwise{
        sel_bits := restore_bits
        shift := restore_shift
        state := xor_restore
      }
    }

  }.elsewhen(state === xor_restore){//STATE: XOR RESTORE
    
    sel_bits := sel_bits ^ crc_polynomy
    state := ones_restore//fix_position
  
  }.elsewhen(state === ones_restore){//STATE: ONES RESTORE
    
    val lsb_One = PriorityEncoder(sel_bits)
    val lsb_One2 = extract_2LSBOne(sel_bits)
    when(num_ones === 2.U && !solution_found){
      val1 := shift + lsb_One2
      val2 := shift + lsb_One
      sol := 2.U
      solution_found := true.B
      state := finished
    }.elsewhen(num_ones === 1.U && !solution_found){
      val2 := shift + lsb_One
      sol := 2.U
      solution_found := true.B
      state := finished
    }.elsewhen(num_ones === 1.U && solution_found){
      multiple_solutions_found := true.B
      sol := 0.U
      state := finished
    }.elsewhen(num_ones === 0.U){  
      state := finished
    }.otherwise{
      state := fix_position
    }
    
  }.elsewhen(state === xor_1){//STATE: XOR_1
    //state := idle
  }.elsewhen(state === ones_1){//STATE: ONES_1
    //state := idle
  }.otherwise{//STATE: FINISHED
    out_sol_valid := true.B
    state := idle
  }  
  
  //OUTPUTS
  io.in.syndrome.ready := true.B
  io.in.length.ready := true.B
  
  io.out.val1 := val1
  io.out.val2 := val2
  io.out.sol.bits := sol
  io.out.sol.valid := out_sol_valid
   
}//end of the class

