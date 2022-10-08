package chipyard.baseband//RxChain//note


import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint
import chisel3.util.{log2Ceil, Decoupled}
import dsptools.numbers._

//import CRC._
//import Whitening._
//import PreambleDetector._
//import OctetGenerator._


// INPUTS

class RxInputBundle extends Bundle { //cambiado el input bundle para anadir mi enable, y la AA.
  val data = Flipped(Decoupled(UInt(1.W)))
  val enable = Input(Bool())
  val ownAccessAddress = Flipped(Decoupled(UInt(32.W)))  
  
  val soft_reset = Input(Bool())
  
  override def cloneType: this.type = RxInputBundle().asInstanceOf[this.type]
}

object RxInputBundle {
  def apply(): RxInputBundle = new RxInputBundle
}

//OUTPUTS 
class RxOutputBundle extends Bundle {

  //data
  val data = Decoupled(UInt(8.W))
  val lengthPayload = Decoupled(UInt(8.W))

  //check flags
  val corrAA = Decoupled(Bool())//Output(Bool())
  val corrCRC = Decoupled(Bool())//Output(Bool())
  
  //reminder calculation output
  val reminder = Decoupled(UInt(24.W))
  
  //
  val start = Output(Bool())
  val done = Output(Bool())
  
  //section flags
  //val headerPDU = Output(Bool())
  //val payloadPDU = Output(Bool())
  //val CRC = Output(Bool())
  
  override def cloneType: this.type = RxOutputBundle().asInstanceOf[this.type]
}

object RxOutputBundle {
  def apply(): RxOutputBundle = new RxOutputBundle
} 

//CONEXION WITH WITHENING BLOCK
class RxWhBundle extends Bundle { //cambiado el input bundle para anadir mi enable, y la AA.
  val out_data = Decoupled(UInt(1.W))
  val in_data = Flipped(Decoupled(UInt(1.W)))
  val reset = Output(Bool())
  //val ownAccessAddress = Flipped(Decoupled(UInt(32.W)))  
  
  override def cloneType: this.type = RxWhBundle().asInstanceOf[this.type]
}

object RxWhBundle {
  def apply(): RxWhBundle = new RxWhBundle
}

//CONEXION WITH CRC BLOCK
class RxCRCBundle extends Bundle { //cambiado el input bundle para anadir mi enable, y la AA.
  val out_data = Decoupled(UInt(1.W))
  val in_data = Flipped(Decoupled(UInt(24.W)))
  val reset = Output(Bool())
  //val ownAccessAddress = Flipped(Decoupled(UInt(32.W)))  
  
  override def cloneType: this.type = RxCRCBundle().asInstanceOf[this.type]
}

object RxCRCBundle {
  def apply(): RxCRCBundle = new RxCRCBundle
}


//FULL IO bundle 

class RxIO extends Bundle {
  val in = new RxInputBundle
  val wh = new RxWhBundle
  val crc = new RxCRCBundle
  //val param = Input(ParameterBundle())
  val out = new RxOutputBundle

  override def cloneType: this.type = RxIO().asInstanceOf[this.type]
}

object RxIO{
	def apply(): RxIO = new RxIO
}

class RxChain extends Module {
  
  //FUNCTIONS
  
  //function to update the state of the finite state machine
  def Update(
      currentState: UInt,
      nextState: UInt,
      maxCounter: UInt,
      currentBit: UInt,
      currentByte: UInt,
      inCondition: Bool
  ): (UInt, UInt, UInt, Bool) = {

    val maxByte = maxCounter / 8.U
    val maxBit = maxCounter % 8.U
    val outputState = Wire(UInt(8.W))
    val counterBitOut = Wire(UInt(3.W)) //max value will be 7, if it gets to 8, it will be 0 and counterByteOut will increase by 1
    val counterByteOut = Wire(UInt(8.W))
    val byteChange = Wire(Bool()) 

    outputState := currentState
    counterBitOut := currentBit
    counterByteOut := currentByte
    byteChange := false.B

    when(inCondition){
      when(currentByte < maxByte){
        when(currentBit < 7.U){
          counterBitOut := currentBit + 1.U
        }.otherwise{
          counterBitOut := 0.U
          counterByteOut := currentByte + 1.U
          byteChange := true.B
        }
      }.otherwise{
        when(currentBit < maxBit){
          counterBitOut := currentBit + 1.U
        }.otherwise{
          //finished
          when(nextState === idle){
            counterBitOut := 0.U
          }.otherwise{
            counterBitOut := 1.U
          }
          counterByteOut := 0.U
          outputState := nextState
        } 
      }
    }
    (outputState, counterBitOut, counterByteOut, byteChange)
  }//end of the def
  
  def writteRegLE(  //writtes the specified number of bits in the specified register, shifting the register and introducing the bits in the right side.
      register: UInt, //the register is ordered, LSB to MSB
      dataByte: UInt, //receives the byte in LSB to MSB order
      in_cond: Bool
      //length: Int, //length of the register
      //inLength: Int //length of the input data to writte //both lengths not needed anymore.
  ): (UInt) = {
    
    val length = if(register.widthKnown) register.getWidth else -1
    val inLength = if(dataByte.widthKnown) dataByte.getWidth else -1
    val outputRegister = (VecInit(Seq.fill(length)(false.B)))

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
    (outputRegister.asUInt)
  }//end of the def writteRegLE
  
  //END FUNCTIONS

  val io = IO(new RxIO)  
  
  val soft_reset = Wire(Bool())
  soft_reset := io.in.soft_reset
  
  //CREATED WIRES FOR BLOCKS:
  
  val ownAccessAddress = Wire(UInt(32.W))//RegInit("01101011011111011001000101110001".U(32.W))
  ownAccessAddress := io.in.ownAccessAddress.bits //the received AA is aready reversed to match the bit order
  
  val reversedBroadcastAA = "b01101011011111011001000101110001".U
  
  //Preamble Detector block wires:
  
  val pd_in_valid = Wire(Bool())
  pd_in_valid := io.in.data.valid && io.in.enable//work only if the whole Rx is enabled
  val pd_aa0_valid = Wire(Bool())
  pd_aa0_valid := io.in.ownAccessAddress.valid
  
  val pd_out_data = Wire(UInt(1.W))
  val pd_out_valid = Wire(Bool())
  val pd_det = Wire(Bool())
  
  //Whitening block wires:
  
  val wh_in_reset = Wire(Bool())
  
  val wh_out_data = Wire(UInt(1.W))  
  val wh_out_valid = Wire(Bool())
  
  //CRC block wires:
  val crc_state_valid = Wire(Bool())
  val crc_state = Wire(UInt(24.W))
  
  val crc_reset = Wire(Bool())
  
  //USED BLOCKS:

  // ___Preamble Detector___
  val preambleBlock = Module(new PreambleDetector)
  
  preambleBlock.io.in.data.bits := io.in.data.bits
  io.in.data.ready := preambleBlock.io.in.data.ready
  preambleBlock.io.in.data.valid := pd_in_valid

  preambleBlock.io.in.aa_0.bits := ownAccessAddress(31)//0
  preambleBlock.io.in.aa_0.valid := pd_aa0_valid
  // := preambleBlock.io.in.aa_0.ready

  pd_out_data := preambleBlock.io.out.data.bits
  preambleBlock.io.out.data.ready := true.B//pd_out_ready //always ready for new bits, TODO
  pd_out_valid := preambleBlock.io.out.data.valid
  pd_det := preambleBlock.io.out.preamble_det
  
  //REGISTERS AND SHIT:
  
  val idle :: accessAddress :: headerPDU :: payloadPDU :: crc ::  Nil = Enum(5)
  val state = RegInit(idle)
  
  val bitCounter = RegInit(0.U(3.W))
  val byteCounter = RegInit(0.U(8.W))
  val counter = Wire(UInt(11.W)) //the max value is 251 bytes * 8 -1. (2007)
  counter := byteCounter * 8.U + bitCounter
  val byteChange = RegInit(false.B)
  
  
  //aqui deberia de anadir los registros para

  val saveLength = RegInit(false.B)//flag to save the payloadLength
  
  val receivedAccAdd = RegInit(0.U(32.W))
  
  val reminder = RegInit(0.U(24.W))
  
  val calcCRC = RegInit(0.U(24.W))  
  val flagCheckCRC = RegInit(false.B)
  val sectionCRC = RegInit(0.U(2.W))

  //output registers
  val outData = RegInit(0.U(8.W))
  val outDataValid = RegInit(false.B) 
  
  val payloadLength = RegInit(6.U(8.W))
  val payloadLengthValid = RegInit(false.B) 
  
  val corrAA = RegInit(false.B)
  val corrCRC = RegInit(false.B)
  val validAA = RegInit(false.B)
  val validCRC = RegInit(false.B)  
  
  val start = RegInit(false.B) 
  val done = RegInit(false.B) 
  
  when(state === idle){
    
    when(pd_det && pd_out_valid && !soft_reset){
      state := accessAddress
      bitCounter := 1.U
      byteCounter := 0.U
      start := true.B
      
      //lets add the restarts:
      receivedAccAdd := 0.U
      payloadLength := 6.U
      corrAA := true.B
      corrCRC := true.B
      sectionCRC := 0.U
      
    }.otherwise{
      start := false.B
      state := idle
      bitCounter := bitCounter
      byteCounter := byteCounter
      
    }

  }.elsewhen(state === accessAddress){
    when(!soft_reset){
      val (outputState, outputBitCounter, outputByteCounter, outputByteChange) =
            Update(
              accessAddress,
              headerPDU,
              32.U,
              bitCounter,
              byteCounter,
              pd_out_valid
            )

      //start := false.B
      state := outputState
      bitCounter := outputBitCounter
      byteCounter := outputByteCounter
      byteChange := outputByteChange
      
      val (outputAA) =
         writteRegLE(
           receivedAccAdd,
           pd_out_data,
           pd_out_valid
         )
      receivedAccAdd := outputAA  
      
      when(outputState === headerPDU){
        validAA := true.B
      }
      start := false.B
    }.otherwise{
      state := idle
    }
      
  }.elsewhen(state === headerPDU){
    when(!soft_reset){  
      val (outputState, outputBitCounter, outputByteCounter, outputByteChange) =
            Update(
              headerPDU,
              payloadPDU,
              16.U,
              bitCounter,
              byteCounter,
              pd_out_valid//wh_out_valid
            )
      //state := outputState
      bitCounter := outputBitCounter
      byteCounter := outputByteCounter
      byteChange := outputByteChange
      
      //to control the corrAA flag
      corrAA := Mux(receivedAccAdd === ownAccessAddress, true.B, Mux(receivedAccAdd === reversedBroadcastAA, true.B, false.B))

    
      //to control the payload state jump and the saving of the length.
      when(outputState =!= state){
        saveLength := true.B
        when(Reverse(Cat(outData(6,1),pd_out_data)) === 0.U){
          state := crc
          payloadLength := 0.U
          payloadLengthValid := true.B
          done := true.B
        }.otherwise{
          state := outputState
        }
      }.otherwise{
        state := state
      }
    }.otherwise{
      state := idle
    }

  }.elsewhen(state === payloadPDU){

    when(!soft_reset){
      //out_bit := wh_out_data
      //valid_bit := wh_out_valid    

      val (outputState, outputBitCounter, outputByteCounter, outputByteChange) =
            Update(
              payloadPDU,
              crc,
              payloadLength*8,
              bitCounter,
              byteCounter,
              pd_out_valid//wh_out_valid
            )
      state := outputState
      bitCounter := outputBitCounter
      byteCounter := outputByteCounter
      byteChange := outputByteChange
    
      when(outputState === crc){
        done := true.B
      }
    
      when(saveLength === true.B){
        saveLength := false.B
        payloadLength := Reverse(outData)
        payloadLengthValid := true.B      
      }
    }.otherwise{
      state := idle
    }    

  }.elsewhen(state === crc){

    when(!soft_reset){
      //out_bit := wh_out_data
      //valid_bit := wh_out_valid

      val (outputState, outputBitCounter, outputByteCounter, outputByteChange) =
            Update(
              crc,
              idle,
              24.U,
              bitCounter,
              byteCounter,
              pd_out_valid//wh_out_valid
            )
      state := outputState
      bitCounter := outputBitCounter
      byteCounter := outputByteCounter
      byteChange := outputByteChange
    
      when(byteCounter === 0.U && bitCounter === 1.U){
        calcCRC := crc_state
      }
      
      when(bitCounter === 0.U && flagCheckCRC === false.B){
        flagCheckCRC := true.B
      }
    }.otherwise{
      state := idle
    }
      
  }
  
  //CHECK THE CRC
  
  when(flagCheckCRC === true.B){
    flagCheckCRC := false.B
    sectionCRC := sectionCRC + 1.U
    when(sectionCRC === 0.U && calcCRC(23, 16) =!= outData){
      corrCRC := false.B
    }
    when(sectionCRC === 1.U && calcCRC(15, 8) =!= outData){
      corrCRC := false.B
    }
    when(sectionCRC === 2.U && calcCRC(7, 0) =!= outData){
      corrCRC := false.B
    }
    
    //val xored_value = 0.U
    when(sectionCRC === 0.U){
      //xored_value := calcCRC(23, 16) ^ outData
      val new_reminder = writteRegLE(reminder, calcCRC(23, 16) ^ outData, true.B)
      reminder := new_reminder
    }.elsewhen(sectionCRC === 1.U){
      //xored_value := calcCRC(15, 8) ^ outData
      val new_reminder = writteRegLE(reminder, calcCRC(15, 8) ^ outData, true.B)
      reminder := new_reminder
    }.otherwise{
      //xored_value := calcCRC(7, 0) ^ outData
      val new_reminder = writteRegLE(reminder, calcCRC(7, 0) ^ outData, true.B)
      reminder := new_reminder
      validCRC := true.B
    }
    //val new_reminder = writteRegLE(reminder, xored_value, true.B)
    //reminder := new_reminder
    
  }
  
  
  //___CONTROL & OUTPUT OF THE DATA___
  
  //out_bit := wh_out_data
  //valid_bit := wh_out_valid

  val (nOutputData) =
       writteRegLE(
         outData,
         wh_out_data,//out_bit
         wh_out_valid//out_valid
       )
  outData := nOutputData
  
  outDataValid := Mux((state === headerPDU || state === payloadPDU ) && byteChange, true.B, false.B) //ahora no hago valid con el crc (|| state === crc)
  //With this Mux we control the valid signal of the data output
  
  when(payloadLengthValid === true.B){
    payloadLengthValid := false.B
  }
  
  //__Whitening Reset Control Signals__
  
  //wh_in_reset := Mux(state === idle, true.B, false.B)
  when(state === idle){
    wh_in_reset := true.B
  }.otherwise{
    wh_in_reset := false.B
  }

  //CRC Reset Control Signals__  
  crc_reset := Mux(state === accessAddress, true.B, false.B)
  
  //correct AA flag reset
  when(validAA===true.B){
    validAA := false.B
  }
  
  //correctness flag reset
  when(validCRC===true.B){
    validCRC := false.B
  }
  
  //start flag reset
  //when(start === true.B){
  //  start := false.B
  //}
  
  //done flag reset
  when(done === true.B){
    done := false.B
  }
  
  //OUTPUTS:
  
  io.in.data.ready := Mux(io.in.enable, true.B, false.B)//true.B
  io.in.ownAccessAddress.ready := true.B
  
  //wh conexions
  io.wh.out_data.bits := pd_out_data
  io.wh.out_data.valid := Mux(state === idle || state === accessAddress, false.B, pd_out_valid)
  
  io.wh.in_data.ready := true.B//vamos a conectarlo a lo mismo que el valid de la salida
  
  io.wh.reset := wh_in_reset
  
  wh_out_data := io.wh.in_data.bits
  wh_out_valid := io.wh.in_data.valid
  
  //crc conexions
  io.crc.out_data.bits := wh_out_data
  io.crc.out_data.valid := Mux(state === headerPDU || state === payloadPDU, wh_out_valid, false.B)
  
  io.crc.reset := crc_reset
  
  crc_state := io.crc.in_data.bits
  crc_state_valid := io.crc.in_data.valid
  io.crc.in_data.ready := true.B    
  
  //main outputs:
  
  io.out.data.bits := outData
  io.out.data.valid := outDataValid
  //io.out.data.ready
  
  io.out.lengthPayload.bits := payloadLength
  io.out.lengthPayload.valid := payloadLengthValid
  //io.out.lengthPayload.ready
  
  io.out.corrAA.bits := corrAA
  io.out.corrAA.valid := validAA
  //io.out.corrAA.ready
  
  io.out.corrCRC.bits := corrCRC
  io.out.corrCRC.valid := validCRC
  //io.out.corrCRC.ready
  
  io.out.reminder.bits := reminder
  io.out.reminder.valid := validCRC
  //io.out.reminder.ready
  
  io.out.start := start//Mux(start, true.B, false.B)
  io.out.done := done//Mux(done, true.B, false.B)
  

}//end of the class
