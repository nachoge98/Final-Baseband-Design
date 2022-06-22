package chipyard.baseband//CRC //note


import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint
import chisel3.util.{log2Ceil, Decoupled}
import dsptools.numbers._

 
// INPUTS
class CRCInputBundle extends Bundle {
  val data = Flipped(Decoupled(UInt(1.W)))
  val init_state = Input(UInt(24.W)) //initial data for the LFSR
  val reset = Input(Bool())

  override def cloneType: this.type = CRCInputBundle().asInstanceOf[this.type]
}

object CRCInputBundle {
  def apply(): CRCInputBundle = new CRCInputBundle
}

//OUTPUTS 
class CRCOutputBundle extends Bundle {
  val state = Decoupled(UInt(24.W)) //
  //val bit = Decoupled(UInt(1.W))

  override def cloneType: this.type = CRCOutputBundle().asInstanceOf[this.type]
}

object CRCOutputBundle {
  def apply(): CRCOutputBundle = new CRCOutputBundle
} 


//FULL IO bundle 
class CRCIO extends Bundle {
  val in = new CRCInputBundle
  //val param = Input(ParameterBundle())
  val out = new CRCOutputBundle

  override def cloneType: this.type =
    CRCIO().asInstanceOf[this.type]
}

object CRCIO {
  def apply(): CRCIO = new CRCIO
}

//Class CRC
class CRC extends Module {

  val io = IO(new CRCIO)
  
  val sRegister = RegInit(0.U(24.W))
  val in = sRegister(23) ^ io.in.data.bits
  val valid = Wire(Bool())  

  //crc_seed := "b010101010101010101010101".U //this is the CRC for the rest of the cases, see readme of the CRC.
  
  //Documentation about CRC: BLE v.5, vol 6:
  //
  //Position 0 (0 in the register) shall be set as the least significant bit and position 23
  //(23 in the register) shall be set as the most significant bit of the initialization value.
  //
  //CRC is transmitted most significant bit first, i.e. from position 23 to position 0.
  //both, the initial state, and the output are in MSB to LSB order.
  
  
  when(io.in.reset){ //reset and initialization to the received initial value
    
    //sRegister := Reverse(io.in.init_state) //check the comment at the beginning
    sRegister := io.in.init_state
    valid := false.B //deberia de ser esto así? no estoy seguro, quizás deberia ser false. //lo he puesto a false, si sale algun problema, cambiar.
    
  }.elsewhen(io.in.data.valid){ //normal operation mode (serial) when there is not a reset

    sRegister := Cat(
      sRegister(22),
      sRegister(21),
      sRegister(20),
      sRegister(19),
      sRegister(18),
      sRegister(17),
      sRegister(16),
      sRegister(15),
      sRegister(14),
      sRegister(13),
      sRegister(12),
      sRegister(11),
      sRegister(10),
      sRegister(9) ^ in,
      sRegister(8) ^ in,
      sRegister(7),
      sRegister(6),
      sRegister(5) ^ in,
      sRegister(4),
      sRegister(3) ^ in,
      sRegister(2) ^ in,
      sRegister(1),
      sRegister(0) ^ in,
      in
    )
    valid := true.B
  }.otherwise{
    sRegister := sRegister
    valid := false.B    
  } 
  
  
  //Outputs
  io.in.data.ready := true.B
  io.out.state.bits := sRegister
  io.out.state.valid := valid
  
  //io.out.bit.bits := io.in.data.bits
  

}
