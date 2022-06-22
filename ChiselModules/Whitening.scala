package chipyard.baseband//Whitening //note


import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint
import chisel3.util.{log2Ceil, Decoupled}
import dsptools.numbers._

 
// INPUTS
class WhInputBundle extends Bundle {
  val data = Flipped(Decoupled(UInt(1.W)))
  val init_state = Input(UInt(7.W)) //initial data for the LFSR
  val reset = Input(Bool())

  override def cloneType: this.type = WhInputBundle().asInstanceOf[this.type]
}

object WhInputBundle {
  def apply(): WhInputBundle = new WhInputBundle
}

//OUTPUTS 
class WhOutputBundle extends Bundle {
  val data = Decoupled(UInt(1.W)) //
  //val debug = Output(UInt(7.W)) //Debug purposes outputs.
  //val debugLSB = Output(UInt(1.W))
  //val debugIN = Output(UInt(1.W))

  override def cloneType: this.type = WhOutputBundle().asInstanceOf[this.type]
}

object WhOutputBundle {
  def apply(): WhOutputBundle = new WhOutputBundle
} 


//FULL IO bundle 
class WhIO extends Bundle {
  val in = new WhInputBundle
  //val param = Input(ParameterBundle())
  val out = new WhOutputBundle

  override def cloneType: this.type =
    WhIO().asInstanceOf[this.type]
}

object WhIO {
  def apply(): WhIO = new WhIO
}

//Class Whitening
class Whitening extends Module {

  val io = IO(new WhIO)
  
  val sRegister = RegInit(0.U(7.W))
  val out = Wire(UInt(1.W))
  val valid = Wire(Bool())
  val LSB = sRegister(0)
  
  //Documentation about Whitening: BLE v.5, vol 6:
  //
  //Before whitening or de-whitening, the shift register is initialized with a sequence that is 
  //derived from the channel indez (data channel index or advertising channel indez)
  //
  //Position 0 (6 in the register) is set to one.
  //
  //Positions 1 to 6 are set to the channel index of the channel used when transmitting or
  // receiving, from the most significant bit in position 1(1 in the register) to the least
  // significant bit in possition 6 (0 in the register).
  
  out := 0.U
  valid := false.B
  
  when(io.in.reset === true.B){ //reset and initialization to the received initial value
    
    sRegister := io.in.init_state
    //out := 0.U
    valid := false.B
    
  }.elsewhen(io.in.data.valid === true.B){ //normal operation mode when there is not a reset

    sRegister := Cat(
      LSB,
      sRegister(6),
      sRegister(5), 
      sRegister(4),
      sRegister(3) ^ LSB,
      sRegister(2),
      sRegister(1)
    )
    
    out := LSB ^ io.in.data.bits
    valid := true.B
    
  }.otherwise{
  
    sRegister := sRegister
    valid := false.B
    //out := 0.U
  
  }
  
  //Outputs
  io.in.data.ready := true.B

  io.out.data.bits := out
  io.out.data.valid := valid

  //io.out.debugIN := io.in.data.bits
  //io.out.debugLSB := LSB
  //io.out.debug := sRegister
  

}
