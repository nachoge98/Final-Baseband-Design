package chipyard.baseband
//package FIFO //note


import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint
import chisel3.util.{log2Ceil, Decoupled}
import dsptools.numbers._

 
// INPUTS
class FIFOInputBundle extends Bundle {

  val data = Output(UInt(1.W))

  override def cloneType: this.type = FIFOInputBundle().asInstanceOf[this.type]
}

object FIFOInputBundle {
  def apply(): FIFOInputBundle = new FIFOInputBundle
}

//OUTPUTS 
class FIFOOutputBundle extends Bundle {
  
  val data = Output(UInt(1.W))

  override def cloneType: this.type = FIFOOutputBundle().asInstanceOf[this.type]
}

object FIFOOutputBundle {
  def apply(): FIFOOutputBundle = new FIFOOutputBundle
} 


//FULL IO bundle 
class FIFOIO extends Bundle {

  val in = Flipped(Decoupled(FIFOInputBundle()))
  val out = Decoupled(FIFOOutputBundle())

  override def cloneType: this.type =
    FIFOIO().asInstanceOf[this.type]
}

object FIFOIO {
  def apply(): FIFOIO = new FIFOIO
}

//Class FIFO
class FIFO extends Module {

  val io = IO(new FIFOIO)
  
  val queue = Queue(io.in, 2048)
  
  io.out<>queue
}

//Class FIFO
class FIFO_errors extends Module {

  val io = IO(new FIFOIO)
  
  val fifo = Module(new FIFO)
  
  fifo.io.in.bits.data := io.in.bits.data
  fifo.io.in.valid := io.in.valid
  io.in.ready := fifo.io.in.ready
  
  fifo.io.out.ready := io.out.ready
  
  val counter = RegInit(0.U(8.W)) //max 255.U
  val xored = Wire(UInt(1.W))
  val error_pos = 66.U //to make sure it is in the payload.
  
  when(fifo.io.out.valid && io.out.ready){
    when(counter === 255.U){
      counter := 0.U
    }.otherwise{
      counter := counter + 1.U
    }
  }
  xored := Mux(counter === error_pos, 1.U, 0.U)
  
  io.out.bits.data := xored ^ fifo.io.out.bits.data
  io.out.valid := fifo.io.out.valid
  
}
