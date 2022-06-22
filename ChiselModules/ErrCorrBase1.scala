package chipyard.baseband//ErrCorr//note


import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint
import chisel3.util.{log2Ceil, Decoupled}
import dsptools.numbers._

// Inputs from the Rx Chain

class ECBRxInputBundle extends Bundle {//error correction base input from the Rx chain

  val data = Flipped(Decoupled(UInt(8.W)))
  val length = Flipped(Decoupled(UInt(8.W)))
  val reminder = Flipped(Decoupled(UInt(24.W)))
  val start = Input(Bool())
  val done = Input(Bool())
  val corrAA = Flipped(Decoupled(Bool()))
  val corrCRC = Flipped(Decoupled(Bool()))
  //val ownAccessAddress = Flipped(Decoupled(UInt(32.W)))  

  override def cloneType: this.type = ECBRxInputBundle().asInstanceOf[this.type]
}

object ECBRxInputBundle {
  def apply(): ECBRxInputBundle = new ECBRxInputBundle
}


//Inputs from the controller 
class ECBControllerInputBundle extends Bundle {//error correction base output to the bit sequencer.
  
  //data
  val enableErrCorr = Input(Bool())
  val soft_reset = Input(Bool())
  
  override def cloneType: this.type = ECBControllerInputBundle().asInstanceOf[this.type]
}

object ECBControllerInputBundle {
  def apply(): ECBControllerInputBundle = new ECBControllerInputBundle
} 


//Data Outputs

class ECBOutputBundle extends Bundle {//error correction base output to the bit sequencer.
  
  //data
  val data = Decoupled(UInt(8.W))
  val start = Output(Bool())
  val done = Output(Bool())
  val corrCRC = Decoupled(Bool())
  
  override def cloneType: this.type = ECBOutputBundle().asInstanceOf[this.type]
}

object ECBOutputBundle {
  def apply(): ECBOutputBundle = new ECBOutputBundle
} 

/*
//CONEXION WITH THE ERROR CORRECTION BLOCK:
class TxWhBundle extends Bundle { //cambiado el input bundle para anadir mi enable, y la AA.
  val out_data = Decoupled(UInt(1.W))
  val in_data = Flipped(Decoupled(UInt(1.W)))
  val reset = Output(Bool())
  //val ownAccessAddress = Flipped(Decoupled(UInt(32.W)))  
  
  override def cloneType: this.type = TxWhBundle().asInstanceOf[this.type]
}

object TxWhBundle {
  def apply(): TxWhBundle = new TxWhBundle
}

class TxCRCBundle extends Bundle { //cambiado el input bundle para anadir mi enable, y la AA.
  val out_data = Decoupled(UInt(1.W))
  val in_data = Flipped(Decoupled(UInt(24.W)))
  val reset = Output(Bool())
  //val ownAccessAddress = Flipped(Decoupled(UInt(32.W)))  
  
  override def cloneType: this.type = TxCRCBundle().asInstanceOf[this.type]
}

object TxCRCBundle {
  def apply(): TxCRCBundle = new TxCRCBundle
}
*/
//FULL IO bundle 

class ECBIO extends Bundle {
  val inRx = ECBRxInputBundle() //inputs from the Rx chain
  val inCon = ECBControllerInputBundle() //inputs from the controller
  val out = ECBOutputBundle() //data output conextions
  //val out = TxOutputBundle() //in crc error correction block
  //val xxxx //out error correction block

  override def cloneType: this.type = ECBIO().asInstanceOf[this.type]
}

object ECBIO{
  def apply(): ECBIO = new ECBIO
}

class ErrCorrBase extends Module {

  def writteRegLE(  //writtes the specified number of bits in the specified register, shifting the register and introducing the bits in the right side.
      register: UInt, //the register is ordered, LSB to MSB
      dataByte: UInt, //receives the byte in LSB to MSB order
      in_cond: Bool,
      prevCounter: UInt      
      //length: Int, //length of the register
      //inLength: Int //length of the input data to writte //both lengths not needed anymore.
  ): (UInt, UInt) = {
    
    val length = if(register.widthKnown) register.getWidth else -1
    val inLength = 8//if(dataByte.widthKnown) dataByte.getWidth else -1
    val outputRegister = (VecInit(Seq.fill(length)(false.B)))
    val outputCounter = Mux(in_cond, prevCounter + 1.U, prevCounter)// + 1.U
    
    //outputRegister := register
    
    when(in_cond){
      for(i <- 1 to (length - inLength)){ //shift all but the LSB bits for the input
        when(register(length-inLength-i) === 1.U){
          outputRegister(length-i) := true.B
        }.otherwise{
          outputRegister(length-i) := false.B
        }
      }

      for(j <- 1 to inLength){ //introduce the new LSB bits
        when(dataByte(inLength-j) === 1.U){
          outputRegister(inLength-j) := true.B //(7-j)
        }.otherwise{
          outputRegister(inLength-j) := false.B //(7-j)
        }
      }
      //outputRegister(7, 0) := Reverse(dataByte).asBools
      //outputRegister(7) := dataByte(0)
      //outputRegister := 0.U
    }.otherwise{
      
      
      for(i <- 1 to (length)){ // if no new byte is ready, output is the same as before
        when(register(length-i) === 1.U){
          outputRegister(length-i) := true.B
        }.otherwise{
          outputRegister(length-i) := false.B
        }
      }

    }
    (outputRegister.asUInt, outputCounter)
  }//end of the def writteRegLE
  
  def leftShift1Byte(  //shifts the register 8 positions to the left introducing 0s
      register: UInt, //the register is ordered, LSB to MSB
      in_cond: Bool  //bool condition to execute the shifts
      //prev_counter: UInt //number of shifts remaining 
  ): (UInt, Bool) = {
    
    val length = if(register.widthKnown) register.getWidth else -1
    val inLength = 8
    val outputRegister = (VecInit(Seq.fill(length)(false.B)))
    //val outputCounter = prev_counter
    val outputBool = Mux(in_cond, true.B, false.B)//(prev_counter > 0.U) && 
    
    //outputRegister := register
    
    when(in_cond){//(prev_counter > 0.U) &&

      for(i <- 1 to (length - inLength)){ //shift all but the LSB bits for the input
        when(register(length-inLength-i) === 1.U){
          outputRegister(length-i) := true.B
        }.otherwise{
          outputRegister(length-i) := false.B
        }
      }

      for(j <- 1 to inLength){ //introduce the new LSB bits
        outputRegister(inLength-j) := false.B
      }
      
      //outputCounter := prev_counter - 1.U
      //outputBool := true.B
    }
    (outputRegister.asUInt, outputBool)//(outputRegister.asUInt, outputCounter, outputBool)
  }//end of the def leftShift1Byte
  
   //CORRECTION MASK METHOD, le paso un int de 0 a 7 y sale un 8bits con 1 solo a 1 para hacer xor.
  def maskSolution(  //just 1 bit to one in order to correct a byte of the solution
      position: UInt //position of the byte to flip
  ): (UInt) = {
    
    val outputRegister = (VecInit(Seq.fill(8)(false.B)))

    for(i <- 0 to 7){ //shift all but the LSB bits for the input
      when(i.asUInt === position){
        outputRegister(i) := true.B
      }.otherwise{
        outputRegister(i) := false.B
      }
    }
    
    (outputRegister.asUInt)
  }//end of the def maskSolution
  

  val io = IO(new ECBIO)
  
  //WIRES RxInput: io.inRx
  val inRx_data = RegNext(io.inRx.data.bits, 255.U)//Wire(UInt(8.W))
  //inRx_data := io.inRx.data.bits
  val inRx_data_valid = RegNext(io.inRx.data.valid, false.B)//Wire(Bool())
  //inRx_data_valid := io.inRx.data.valid
  
  val inRx_length = RegNext(io.inRx.length.bits, 255.U)//Wire(UInt(8.W))
  //inRx_length := io.inRx.length.bits
  val inRx_length_valid = RegNext(io.inRx.length.valid, false.B)//Wire(Bool())
  //inRx_length_valid := io.inRx.length.valid
  
  val inRx_reminder = RegNext(io.inRx.reminder.bits, 16777216.U)//Wire(UInt(24.W))
  //inRx_reminder := io.inRx.reminder.bits
  val inRx_reminder_valid = RegNext(io.inRx.reminder.valid, false.B)//Wire(Bool())
  //inRx_reminder_valid := io.inRx.reminder.valid
  
  val start = RegNext(io.inRx.start, false.B)//Wire(Bool())
  //start := io.inRx.start
  val done = RegNext(io.inRx.done, false.B)//Wire(Bool())
  //done := io.inRx.done

  val inRx_corrAA = RegNext(io.inRx.corrAA.bits, false.B)//Wire(Bool())
  //inRx_corrAA := io.inRx.corrAA.bits
  val inRx_corrAA_valid = RegNext(io.inRx.corrAA.valid, false.B)//Wire(Bool())
  //inRx_corrAA_valid := io.inRx.corrAA.valid
  
  val inRx_corrCRC = RegNext(io.inRx.corrCRC.bits, false.B)//Wire(Bool())
  //inRx_corrCRC := io.inRx.corrCRC.bits
  val inRx_corrCRC_valid = RegNext(io.inRx.corrCRC.valid, false.B)//Wire(Bool())
  //inRx_corrCRC_valid := io.inRx.corrCRC.valid

  //WIRES Controller: io.inCon
  val en_error_correction = RegNext(io.inCon.enableErrCorr, false.B)//Wire(Bool())
  val soft_reset = RegNext(io.inCon.soft_reset, false.B)
  //en_error_correction := io.inCon.enableErrCorr
  
  //WIRES Output (registers): io.out
  val out_data =  RegInit(0.U(8.W))
  val out_data_valid = RegInit(false.B)
  val out_data_ready = Wire(Bool())
  out_data_ready := io.out.data.ready
  
  //WIRES CorrectionBlock
  val corrBlock_syndrome = RegInit(0.U(24.W))
  //corrBlock_syndrome := 0.U
  val corrBlock_syndrome_valid = RegInit(false.B)
  //corrBlock_syndrome_valid := false.B
  val corrBlock_syndrome_ready = Wire(Bool())
  
  //val corrBlock_length = Wire(UInt(8.W))
  //corrBlock_length := 0.U
  //val corrBlock_length_valid = Wire(Bool())
  //corrBlock_length_valid := false.B
  val corrBlock_length_ready = Wire(Bool())
  
  val corrBlock_val1 = Wire(UInt(9.W))
  val corrBlock_val2 = Wire(UInt(9.W))
  val corrBlock_sol = Wire(UInt(2.W))
  val corrBlock_sol_valid = Wire(Bool())


  //REGISTERS

  val yes :: no :: Nil = Enum(2)
  val correction = RegInit(no)
  
  val idle :: header1 :: header2 :: payload :: waiting :: waiting_corr :: calculate_idx :: send_header1_corr :: send_header2_corr :: send_payload_corr :: send_header1 :: send_header2 :: passthrough :: send_corrCRC :: Nil = Enum(14)
  val state = RegInit(idle)
  
  val header_reg1 = RegInit(0.U(8.W))
  val header_reg2 = RegInit(0.U(8.W))
  val payload_reg = RegInit(0.U(248.W))
  val payloadCounter = RegInit(0.U(5.W))
  val payloadShifts = RegInit(0.U(5.W))
  val outputShifts = RegInit(0.U(5.W))
  val headerFlags = RegInit(0.U(2.W))
  
  //val start_reg = RegInit(false.B)
  //val done_reg = RegInit(false.B)
  
  val corrCRC = RegInit(false.B)
  val corrCRC_valid = RegInit(false.B)
  val out_done = RegInit(false.B)
  val out_start = RegInit(false.B)
  //val corrAA = RegInit(false.B) //no hace falta, el bloque no manda nada con una !corrAA
  
  val val1_reg = RegInit(0.U(9.W))
  val val2_reg = RegInit(0.U(9.W))
  val sol_reg = RegInit(0.U(2.W))
  
  val shift_sol1 = RegInit(0.U(5.W))
  val shift_sol2 = RegInit(0.U(5.W))
  val section_sol1 = RegInit(0.U(3.W))//faltan los registros para el shift y el %8 para calcular las mascaras exactas.
  val section_sol2 = RegInit(0.U(3.W))
  val mask_sol1 = RegInit(0.U(8.W))
  val mask_sol2 = RegInit(0.U(8.W))
  //val sendPayload = RegInit(false.B)   
  
  //val payloadRegister = RegInit(0.U(248.W))
  //val headerRegister = RegInit(0.U(16.W))
  
  //val byteCounter = RegInit(0.U(8.W))

  //Error Correction State
  when(correction === yes){
    when(state === idle && !en_error_correction){
      correction := no
    }
  }.otherwise{
    when(state === idle && en_error_correction){
      correction := yes
    }
  }

  //STATE MACHINE UPDATE
  when(soft_reset){
  
    state := idle
  
  }.elsewhen(state === idle){/////////////////////////////////
  
    when(start){//start
      state := Mux(correction === no, passthrough, header1)
    }
    //
    out_data_valid := false.B
    payloadCounter := 0.U
    headerFlags := 0.U
    corrCRC_valid := false.B
    corrCRC := false.B
    out_start := false.B
    out_done := false.B
    //sendPayload := false.B
    
  }.elsewhen(state === header1){////////////////////////

    when(inRx_corrAA_valid && !inRx_corrAA){
      state := idle
    }
    when(inRx_data_valid){
      state := header2
      //
      header_reg1 := inRx_data
      out_start := true.B
    }
    //
    out_data_valid := false.B
    
    
  }.elsewhen(state === header2){////////////////////////
  
    when(inRx_length_valid){
      state := Mux(inRx_length > 31.U, send_header1, Mux(inRx_length === 0.U, waiting, payload))//Mux(inRx_length <= 31.U, payload, send_header1)
      
    }
    //
    when(inRx_data_valid){
      header_reg2 := inRx_data
    }
    out_data_valid := false.B
    out_start := false.B
    
  }.elsewhen(state === payload){////////////////////////
    
    when(done && inRx_data_valid){// && inRx_data_valid
      state := waiting
      payloadShifts := 31.U - (payloadCounter + 1.U)
      outputShifts := payloadCounter// + 1.U
    }.elsewhen(done && !inRx_data_valid){
      state := waiting
      payloadShifts := 31.U - (payloadCounter)
      outputShifts := payloadCounter - 1.U// + 1.U    
    }
    //
    out_data_valid := false.B
    val (outputPayload, outputCounter) =
         writteRegLE(
           payload_reg,
           inRx_data,
           inRx_data_valid,
           payloadCounter
         )
    payload_reg := outputPayload
    payloadCounter := outputCounter
    
  }.elsewhen(state === waiting){/////////////////////// modificarlo para que pase de estado al asegurarse que ha recibido ambas cosas (corrCRC y syndrome) usar registros y dos clock cycles.

    when(inRx_corrCRC_valid){
      state := Mux(inRx_corrCRC, send_header1_corr, waiting_corr)
      corrCRC := Mux(inRx_corrCRC, true.B, false.B)
      corrBlock_syndrome := inRx_reminder
      corrBlock_syndrome_valid := Mux(inRx_corrCRC, false.B, true.B)
    }
    //
    out_data_valid := false.B

  }.elsewhen(state === waiting_corr){/////////////////
    
    when(corrBlock_sol_valid){
      when(corrBlock_sol =/= 0.U){
        state := calculate_idx //still needs to be changed
        corrCRC := true.B
      }.otherwise{
        state := send_header1_corr
        corrCRC := false.B
      }
      val1_reg := Mux(corrBlock_val1>23.U,corrBlock_val1-24.U,0.U)//pensar y revisar
      val2_reg := Mux(corrBlock_val2>23.U,corrBlock_val2-24.U,0.U)//pensar y revisar
      sol_reg  := corrBlock_sol
    }
    //
    out_data_valid := false.B
    corrBlock_syndrome_valid := false.B

  }.elsewhen(state === calculate_idx){/////////////////not done//i think its done now

    state := send_header1_corr
    //
    val div1 = val1_reg/8.U
    val div2 = val2_reg/8.U
    
    when(sol_reg(1)===1.U){
      shift_sol2 := div2
      when(div2>outputShifts+1.U){
        section_sol2 := "b100".U
      }.elsewhen(div2>outputShifts){
        section_sol2 := "b010".U
      }.otherwise{
        section_sol2 := "b001".U
      }
      val outSol2 = maskSolution(val2_reg%8.U)
      mask_sol2 := outSol2
    }
    when(sol_reg(0)===1.U){
      shift_sol1 := div1
      when(div1>outputShifts+1.U){
        section_sol1 := "b100".U
      }.elsewhen(div1>outputShifts){
        section_sol1 := "b010".U
      }.otherwise{
        section_sol1 := "b001".U
      }
      val outSol1 = maskSolution(val1_reg%8.U)
      mask_sol1 := outSol1
    }
    out_data_valid := false.B
    corrBlock_syndrome_valid := false.B

  }.elsewhen(state === send_header1_corr){//////////////////not done
    
    state := send_header2_corr
    //
    out_data := header_reg1 ^ (section_sol2(2)*mask_sol2) ^ (section_sol1(2)*mask_sol1)
    out_data_valid := true.B 
     
  }.elsewhen(state === send_header2_corr){//////////////////not done
    
    state := send_payload_corr
    //
    out_data := header_reg2 ^ (section_sol2(1)*mask_sol2) ^ (section_sol1(1)*mask_sol1)
    out_data_valid := true.B
     
  }.elsewhen(state === send_payload_corr){//////////////////not done
    
    when(outputShifts === 0.U){
      state := send_corrCRC//idle //take into account the ready signal of the output? + counter for the end
      out_done := true.B
    }
    //
    val condSol2 = Mux(shift_sol2 === outputShifts, 1.U, 0.U)
    val condSol1 = Mux(shift_sol1 === outputShifts, 1.U, 0.U)
    when(payloadShifts === 0.U){
      //sendPayload := true.B      
      val bool_condition2 = (outputShifts > 0.U)
      val (output_reg2, output_bool2) = leftShift1Byte(payload_reg, bool_condition2)
      when(output_bool2){
        payload_reg := output_reg2
        outputShifts := outputShifts - 1.U
      }
      out_data := payload_reg(247, 240) ^ (section_sol2(0)*condSol2*mask_sol2) ^ (section_sol1(0)*condSol1*mask_sol1)// ^ mask_sol1 ^ mask_sol2
      out_data_valid := true.B
    }.otherwise{
      out_data_valid := false.B
    }
     
  }.elsewhen(state === send_header1){////////////////
    
    state := send_header2 //take into account the ready signal of the output?
    //
    out_data := header_reg1
    out_data_valid := true.B

  }.elsewhen(state === send_header2){////////////////

    state := passthrough  //take into account the ready signal of the output?
    //
    out_data := header_reg2
    out_data_valid := true.B

  }.elsewhen(state === passthrough){////////////////
    
    when(inRx_corrCRC_valid){
      state := send_corrCRC
      corrCRC := inRx_corrCRC
      out_done := true.B
    }
    when(inRx_corrAA_valid && !inRx_corrAA){
      state := idle
    }
    when(inRx_corrAA_valid && inRx_corrAA){
      out_start := true.B
    }.otherwise{
      out_start := false.B
    }
      
    
    //
    out_data := inRx_data
    out_data_valid := inRx_data_valid
    
  }.elsewhen(state === send_corrCRC){////////////////
    
    state := idle
    corrCRC_valid := true.B
    out_data_valid := false.B
    out_done := false.B
    
  }
  


  //STATE MACHINE OUTPUTS

  //preparing the output:
  val bool_condition = (state === waiting || state === waiting_corr || state === send_header1_corr || state === send_header2_corr || state === send_payload_corr) && (payloadShifts > 0.U)
  val (output_reg, output_bool) = leftShift1Byte(payload_reg, bool_condition)
  when(output_bool){
    payload_reg := output_reg
    payloadShifts := payloadShifts - 1.U
  }
  
  //shifting for the output
  
  
  //Control of the INPUT READY.


  //reseting output data valid
  //when(out_data_valid === true.B){
  //  out_data_valid := false.B
  //}
  //out_data_valid := Mux(state === send_header1 || state === send_header2,true.B,false.B)

  //USED BLOCKS:  
  val corrBlock = Module(new CorrectionBlock)
 
  corrBlock.io.in.syndrome.bits := corrBlock_syndrome
  corrBlock.io.in.syndrome.valid := corrBlock_syndrome_valid
  corrBlock_syndrome_ready := corrBlock.io.in.syndrome.ready
  
  corrBlock.io.in.length.bits := inRx_length//inRx_length//corrblock_length
  corrBlock.io.in.length.valid := inRx_length_valid//inRx_length_valid//corrBlock_length_valid
  corrBlock_length_ready := corrBlock.io.in.length.ready
  
  corrBlock.io.in.soft_reset := soft_reset
  
  corrBlock_val1 := corrBlock.io.out.val1
  corrBlock_val2 := corrBlock.io.out.val2
  corrBlock_sol := corrBlock.io.out.sol.bits
  corrBlock_sol_valid := corrBlock.io.out.sol.valid
  corrBlock.io.out.sol.ready := true.B

  /*
  Inputs:
  val syndrome = Flipped(Decoupled(UInt(24.W)))
  val length = Flipped(Decoupled(UInt(8.W)))
  
  Outputs:
  val val1 = Output(UInt(9.W))
  val val2 = Output(UInt(9.W))
  val sol  = Decoupled(UInt(2.W))
  
  */
  
  
  //OUTPUTS:
  
  //RxInputBundle : io.inRx.
  io.inRx.data.ready := true.B
  io.inRx.length.ready := true.B
  io.inRx.reminder.ready := true.B
  io.inRx.corrAA.ready := true.B
  io.inRx.corrCRC.ready := true.B
  
  //ECBOutputBundle : io.out
  io.out.data.bits := out_data
  io.out.data.valid := out_data_valid
  
  io.out.corrCRC.bits := corrCRC
  io.out.corrCRC.valid := corrCRC_valid
  
  io.out.start := out_start
  io.out.done := out_done
   
}//end of the class

