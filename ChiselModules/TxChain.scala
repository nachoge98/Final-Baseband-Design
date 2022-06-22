package chipyard.baseband//TxChain//note


import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint
import chisel3.util.{log2Ceil, Decoupled}
import dsptools.numbers._

//import PreambleDetector._

// INPUTS

class TxInputBundle extends Bundle {

  val data = Flipped(Decoupled(UInt(8.W)))
  val start = Input(Bool())
  val ownAccessAddress = Flipped(Decoupled(UInt(32.W)))
  val soft_reset = Input(Bool())

  override def cloneType: this.type = TxInputBundle().asInstanceOf[this.type]
}

object TxInputBundle {
  def apply(): TxInputBundle = new TxInputBundle
}


//OUTPUTS 

class TxOutputBundle extends Bundle {
  
  //data
  val data = Decoupled(UInt(1.W)) 
  val done = Output(Bool())
  
  override def cloneType: this.type = TxOutputBundle().asInstanceOf[this.type]
}

object TxOutputBundle {
  def apply(): TxOutputBundle = new TxOutputBundle
} 

//CONEXION WITH WITHENING BLOCK
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

//CONEXION WITH CRC BLOCK
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

//FULL IO bundle 

class TxIO extends Bundle {
  val in = TxInputBundle()
  val wh = TxWhBundle() //whitening conexions
  val crc = TxCRCBundle() //CRC conexions
  val out = TxOutputBundle()

  override def cloneType: this.type = TxIO().asInstanceOf[this.type]
}

object TxIO{
	def apply(): TxIO = new TxIO
}

class TxChain extends Module {

  //functions to update the state of the finite state machine
  def IncreaseAndUpdate(
      currentState: UInt,
      nextState: UInt,
      maxCounter: UInt,
      currentBit: UInt,
      currentByte: UInt
  ): (UInt, UInt, UInt) = {

    val maxByte = maxCounter / 8.U
    val maxBit = maxCounter % 8.U
    val outputState = Wire(UInt(8.W))
    val counterBitOut = Wire(UInt(3.W)) //max value will be 7, if it gets to 8, it will be 0 and counterByteOut will increase by 1
    val counterByteOut = Wire(UInt(8.W)) 

    outputState := currentState
    counterBitOut := currentBit
    counterByteOut := currentByte

    //when(inCondition){
      when(currentByte < maxByte){
        when(currentBit < 7.U){
          counterBitOut := currentBit + 1.U
        }.otherwise{
          counterBitOut := 0.U
          counterByteOut := currentByte + 1.U
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
    //}
    (outputState, counterBitOut, counterByteOut)
  }//end of the def

  def Update(
      currentState: UInt,
      nextState: UInt,
      maxCounter: UInt,
      currentBit: UInt,
      currentByte: UInt,
      previousBit: UInt,
      inCondition1: Bool,  //used for the input valid
      inCondition2: Bool  //used for the output ready
  ): (UInt, UInt, UInt, UInt, Bool) = {

    val maxByte = maxCounter / 8.U
    val maxBit = maxCounter % 8.U
    val outputState = Wire(UInt(8.W))
    val counterBitOut = Wire(UInt(3.W)) //max value will be 7, if it gets to 8, it will be 0 and counterByteOut will increase by 1
    val counterByteOut = Wire(UInt(8.W))
    val previousBitOut = Wire(UInt(3.W))
    val outputValid = Wire(Bool()) //to turn true or false the output valid flag.
    
    //in case it is not possible to increase the counter
    outputState := currentState
    counterBitOut := currentBit
    counterByteOut := currentByte
    outputValid := false.B

    when(currentBit === 7.U){//0.U
      when(inCondition1 && inCondition2){
        //increase
        val (outState, outBitCounter, outByteCounter) =
              IncreaseAndUpdate(
                currentState,
                nextState,
                maxCounter,
                currentBit,
                currentByte
              )
        outputState := outState
        counterBitOut := outBitCounter
        counterByteOut := outByteCounter
        //valid flag
        when(previousBit =/= currentBit){
          outputValid := true.B
        }.otherwise{
          outputValid := false.B
        }
      }.otherwise{
        when(previousBit =/= currentBit){
          outputValid := true.B
        }.otherwise{
          outputValid := false.B
        }
      }
    }.elsewhen(inCondition2){
      //increase
      val (outState, outBitCounter, outByteCounter) =
            IncreaseAndUpdate(
              currentState,
              nextState,
              maxCounter,
              currentBit,
              currentByte
            )
      outputState := outState
      counterBitOut := outBitCounter
      counterByteOut := outByteCounter
      //valid flag
      outputValid := true.B
    }
    previousBitOut := currentBit
    (outputState, counterBitOut, counterByteOut, previousBitOut, outputValid)
  }//end of the def

  
  val io = IO(new TxIO)

  val soft_reset = Wire(Bool())
  soft_reset := io.in.soft_reset

  //BLOCK && IO WIRES

  val in_valid = Wire(Bool())
  in_valid := io.in.data.valid
  val in_ready = Wire(Bool())
  val in_bits = RegInit(0.U(8.W))//Wire(UInt(8.W))
  //in_bits := io.in.bits.data
  val in_start = Wire(Bool())
  in_start := io.in.start


  //probablemente tendré que cambiar las salidas a registros en lugar de wires.
  //val out_valid = RegInit(false.B)
  val out_valid = Wire(Bool())
  out_valid := false.B
  val bit_valid = Wire(Bool())
  bit_valid := false.B
  val out_ready = Wire(Bool())
  out_ready := io.out.data.ready
  //val out_bit = RegInit(0.U(1.W))
  val out_bit = Wire(UInt(1.W))
  //out_bit := 0.U
  val out_done = Wire(Bool())
  out_done := false.B
  //val out_state = Wire(UInt(4.W))
  //out_state := 0.U 
  

  //REGISTERS

  val idle :: preamble :: accessAddress :: headerPDU :: payloadPDU :: crc ::  Nil = Enum(6)
  val state = RegInit(idle)
  
  val bitCounter = RegInit(1.U(3.W))
  val byteCounter = RegInit(0.U(8.W))
  val counter = Wire(UInt(11.W)) //the max value is 251 bytes * 8 -1. (2007)
  counter := byteCounter * 8.U + bitCounter
  val bitPos = Wire(UInt(4.W))
  bitPos := Mux(counter === 0.U, 0.U, (counter)%8.U)// (counter-1.U)%8.U
  val prevBit = RegInit(1.U(3.W))

  val ownAccAdd = Wire(UInt(32.W))//RegInit("01101011011111011001000101110001".U(32.W))
  ownAccAdd := io.in.ownAccessAddress.bits //the received AA is aready reversed to match the bit order
  //val ownAccAdd = "b01101011011111011001000101110001".U
  val aa0 = ownAccAdd(31)
  val LE1Mpreamble0 = "b01010101".U
  val LE1Mpreamble1 = "b10101010".U
  val preamble_LE1M = Mux(aa0 === 0.U, LE1Mpreamble0, LE1Mpreamble1)
  
  /*
  val LE2Mpreamble0 = "b0101010101010101".U
  val LE2Mpreamble1 = "b1010101010101010".U
  val preamble_LE2M = Mux(aa0 === 0.U, LE2Mpreamble0, LE2Mpreamble1)
  */
  
  val payloadLength = RegInit(6.U(8.W))
  
  val aaa = RegInit(0.U(1.W))//this is used to select the input bit depending on the counter.
  aaa := in_bits(7.U-bitPos)
  
  //Whitening block wires
  ////  
  val wh_in_data = Wire(UInt(1.W))
  wh_in_data := 0.U
  val wh_in_valid = Wire(Bool())
  
  val wh_in_reset = Wire(Bool())
  
  val wh_out_data = Wire(UInt(1.W))
  val wh_out_ready = Wire(Bool())
  wh_out_ready := true.B
  val wh_out_valid = Wire(Bool())
  ////
  
  //CRC block wires
  ////
  val crc_in_data = Wire(UInt(1.W))
  crc_in_data := 0.U
  val crc_in_valid = Wire(Bool())
  
  val crc_in_reset = Wire(Bool())
  
  val crc_out_state = Wire(UInt(24.W))
  val crc_out_valid = Wire(Bool())
  val crc_out_ready = Wire(Bool())
  crc_out_ready := true.B
  ////

  //STATE MACHINE UPDATE

  when(state === idle){
    
    when(in_valid && in_start && !soft_reset){
      state := preamble
    }.otherwise{
      state := idle
    }

  }.elsewhen(state === preamble){
    
    when(!soft_reset){
      val (outputState, outputBitCounter, outputByteCounter) =
            IncreaseAndUpdate(
              preamble,
              accessAddress,
              8.U,
              bitCounter,
              byteCounter
            )
      state := outputState
      bitCounter := outputBitCounter
      byteCounter := outputByteCounter
    }.otherwise{
      state := idle
      bitCounter := 1.U
      byteCounter := 0.U      
    }

  }.elsewhen(state === accessAddress){
    
    when(!soft_reset){
      val (outputState, outputBitCounter, outputByteCounter) =
            IncreaseAndUpdate(
              accessAddress,
              headerPDU,
              32.U,
              bitCounter,
              byteCounter
            )
      state := outputState
      bitCounter := outputBitCounter
      byteCounter := outputByteCounter
    }.otherwise{
      state := idle
      bitCounter := 1.U
      byteCounter := 0.U
    }

  }.elsewhen(state === headerPDU){
    
    when(!soft_reset){
      val (outputState, outputBitCounter, outputByteCounter, previousBitOut, outputValid) =
            Update(
              headerPDU,
              payloadPDU,//payloadPDU,
              16.U,
              bitCounter,
              byteCounter,
              prevBit,
              in_valid,
              out_ready
            )
      state := outputState
      bitCounter := outputBitCounter
      byteCounter := outputByteCounter
      prevBit := previousBitOut
      bit_valid := outputValid
      //out_valid := outputValid
    }.otherwise{
      state := idle
      bitCounter := 1.U
      byteCounter := 0.U
    }
    
  }.elsewhen(state === payloadPDU){
   
    when(!soft_reset){
      val (outputState, outputBitCounter, outputByteCounter, previousBitOut, outputValid) =
            Update(
              payloadPDU,
              crc,
              payloadLength*8.U,
              bitCounter,
              byteCounter,
              prevBit,
              in_valid,
              out_ready
            )      
      when(byteCounter =/= payloadLength){
        when((byteCounter === (payloadLength -1.U)) && bitCounter === 7.U){
          state := outputState
          bitCounter := 0.U
          byteCounter := payloadLength
          prevBit := previousBitOut
          bit_valid := true.B
        }.otherwise{
          state := outputState
          bitCounter := outputBitCounter
          byteCounter := outputByteCounter
          prevBit := previousBitOut
          bit_valid := outputValid
        }
        //out_valid := outputValid
      }.elsewhen(byteCounter === payloadLength){
        state := crc
        bitCounter := 1.U
        byteCounter := 0.U
        prevBit := previousBitOut
        bit_valid := true.B
        //out_valid := true.B
      }
    }.otherwise{
      state := idle
      bitCounter := 1.U
      byteCounter := 0.U
    }
    
  }.elsewhen(state === crc){
  
    when(!soft_reset){
      val (outputState, outputBitCounter, outputByteCounter) =
            IncreaseAndUpdate(
              crc,
              idle,
              24.U,
              bitCounter,
              byteCounter
            )
      state := outputState
      when(state =/= outputState){
        bitCounter := 1.U
      }.otherwise{
        bitCounter := outputBitCounter
      }
    
      byteCounter := outputByteCounter
      prevBit := bitCounter
    }.otherwise{
      state := idle
      bitCounter := 1.U
      byteCounter := 0.U
    }
    
  }
  

  //STATE MACHINE OUTPUTS

  when(state === idle){
    out_bit := 0.U
    out_valid := false.B
    //out_state := 0.U

    
    //wh
    wh_in_data := 0.U
    wh_in_valid := false.B
    wh_in_reset := true.B
    
    //crc
    crc_in_data := 0.U
    crc_in_valid := false.B
    crc_in_reset := true.B
    
  }.elsewhen(state === preamble){
    out_bit := preamble_LE1M(8.U-bitCounter-byteCounter*8.U)
    out_valid := true.B
    //out_state := 1.U

    
    //wh
    wh_in_data := 0.U
    wh_in_valid := false.B
    wh_in_reset := true.B
    
    //crc
    crc_in_data := 0.U
    crc_in_valid := false.B
    crc_in_reset := true.B
    
  }.elsewhen(state === accessAddress){
    out_bit := ownAccAdd(32.U-bitCounter-byteCounter*8.U)
    out_valid := true.B
    //out_state := 2.U

    //wh
    wh_in_data := 0.U
    wh_in_valid := false.B
    wh_in_reset := true.B
    
    //crc
    crc_in_data := 0.U
    crc_in_valid := false.B
    crc_in_reset := true.B

    
  }.elsewhen(state === headerPDU){
  
    out_bit := wh_out_data//aaa//wh_out_data
    out_valid := wh_out_valid//bit_valid//wh_out_valid
    
    //wh
    wh_in_data := aaa
    wh_in_valid := bit_valid//false.B//bit_valid //bit valido al wh.
    wh_in_reset := false.B
    
    //crc
    crc_in_data := aaa
    crc_in_valid := bit_valid
    crc_in_reset := false.B
    
    //out_state := 3.U
    when(byteCounter === 1.U && bitCounter === 1.U){
      payloadLength := Reverse(in_bits)
    }
    
  }.elsewhen(state === payloadPDU){
  
    out_bit := wh_out_data//aaa//wh_out_data
    out_valid := wh_out_valid//bit_valid//wh_out_valid
  
    //wh
    wh_in_data := aaa
    wh_in_valid := bit_valid//false.B//bit_valid //bit valido al wh.
    wh_in_reset := false.B
    
    //crc
    crc_in_data := aaa
    crc_in_valid := bit_valid
    crc_in_reset := false.B

    //out_state := 4.U
    
  }.elsewhen(state === crc){
  
    out_bit := wh_out_data//0.U
    out_valid := wh_out_valid//true.B
    
    //wh
    wh_in_data := crc_out_state(24.U-bitCounter-byteCounter*8.U)
    wh_in_valid := true.B
    wh_in_reset := false.B
    
    //crc
    crc_in_data := 0.U
    crc_in_valid := false.B
    crc_in_reset := false.B

    when(byteCounter === 3.U){
      out_done := true.B
    }
  
    //out_state := 5.U
    
  }.otherwise{
    //wh
    wh_in_data := 0.U
    wh_in_valid := false.B
    wh_in_reset := true.B
    
    //crc
    crc_in_data := 0.U
    crc_in_valid := false.B
    crc_in_reset := true.B
  
    out_bit := 0.U
    //out_state := 10.U
    out_valid := false.B
  }

  //storing the input byte

  when(in_valid){
    in_bits := io.in.data.bits
  }.otherwise{
    in_bits := in_bits
  }

  //Control of the INPUT READY.

  when(state === idle){
    in_ready := true.B
  }.elsewhen(state === preamble){
    in_ready := false.B
  }.elsewhen(state === accessAddress){
    in_ready := false.B
  }.elsewhen(state === headerPDU){
    when(bitCounter === 7.U){//0.U//7.U  //version con wire/version con registro
      in_ready := true.B
    }.otherwise{
      in_ready := false.B
    }
  }.elsewhen(state === payloadPDU){//state payloadPDU = 4
    when(bitCounter === 7.U && byteCounter < (payloadLength - 1.U)){//bitCounter === 0.U && byteCounter =/= payloadLength//(bitCounter === 7.U && byteCounter < payloadLength)
      in_ready := true.B
    }.otherwise{
      in_ready := false.B
    }
  }.elsewhen(state === crc){
    in_ready := false.B
  }.otherwise{
    in_ready := false.B
  }

  //USED BLOCKS:  

  // ___CRC___
  ////
  io.crc.out_data.bits := crc_in_data
  io.crc.out_data.valid := crc_in_valid
  
  io.crc.reset := crc_in_reset
  
  crc_out_state := io.crc.in_data.bits
  crc_out_valid := io.crc.in_data.valid
  io.crc.in_data.ready := crc_out_ready
  ////
  
  // ___Whitening___
  ////
  io.wh.out_data.bits := wh_in_data
  io.wh.out_data.valid := wh_in_valid
  
  io.wh.in_data.ready := wh_out_ready//vamos a conectarlo a lo mismo que el valid de la salida
  
  io.wh.reset := wh_in_reset
  
  wh_out_data := io.wh.in_data.bits
  wh_out_valid := io.wh.in_data.valid
  ////

  //OUTPUTS:
  io.in.data.ready := in_ready
  
  io.in.ownAccessAddress.ready := true.B

  io.out.data.valid := out_valid
  io.out.data.bits := out_bit
  io.out.done := out_done //i have to implement the flag done, not implemented yet.//added it in the machine outputs.
  //io.out.bits.state := out_state
  
}//end of the class

