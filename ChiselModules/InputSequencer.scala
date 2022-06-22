package chipyard.baseband//inSequencer//Error Correction


import chisel3._
import chisel3.util._
//import chisel3.experimental.FixedPoint
import chisel3.util.{log2Ceil, Decoupled}
//import dsptools.numbers._

//Inputs
class ISInputBundle extends Bundle {//correction block inputs

  //val start = Input(Bool())
  val data = Flipped(Decoupled(UInt(32.W)))
  val reminder = Flipped(Decoupled(UInt(3.W)))
  val soft_reset = Input(Bool())
  //val discard = Input(Bool())
  //val start = Input(Bool())
  //val done = Input(Bool())
  //val corrCRC = Flipped(Decoupled(Bool()))

  override def cloneType: this.type = ISInputBundle().asInstanceOf[this.type]
}
object ISInputBundle {
  def apply(): ISInputBundle = new ISInputBundle
}


//Outputs
class ISOutputBundle extends Bundle {//error correction base output to the bit sequencer.
  
  val data  = Decoupled(UInt(8.W))
  //val start  = Output(Bool()) //Deleted after, everything necessary for it its commented but there.
  
  override def cloneType: this.type = ISOutputBundle().asInstanceOf[this.type]
}
object ISOutputBundle {
  def apply(): ISOutputBundle = new ISOutputBundle
}

class ISIO extends Bundle {
  val in = ISInputBundle() //inputs from the Rx chain
  val out = ISOutputBundle() //inputs from the controller

  override def cloneType: this.type = ISIO().asInstanceOf[this.type]
}

object ISIO{
  def apply(): ISIO = new ISIO
}

class InputSequencer extends Module {
  
  val io = IO(new ISIO) // IO BUNDLE
  
  //WIRES IO

  val in_data = Wire(UInt(32.W))//RegNext(io.in.data.bits, 0.U)//Wire(UInt(8.W))
  in_data := io.in.data.bits
  val in_data_valid = Wire(Bool())//RegNext(io.in.data.valid, false.B)//Wire(Bool())
  in_data_valid := io.in.data.valid
  val in_data_ready = RegInit(false.B)//Wire(Bool())
  
  val in_reminder = Wire(UInt(3.W))//RegNext(io.in.data.bits, 0.U)//Wire(UInt(8.W))
  in_reminder := io.in.reminder.bits
  val in_reminder_valid = Wire(Bool())//RegNext(io.in.data.bits, 0.U)//Wire(UInt(8.W))
  in_reminder_valid := io.in.reminder.valid
  val in_reminder_ready = true.B

  val soft_reset = Wire(Bool())
  soft_reset := io.in.soft_reset

  
  //REGISTERS:
  val data_bits1 = RegInit(0.U(8.W))
  val data_bits2 = RegInit(0.U(8.W))
  val data_bits3 = RegInit(0.U(8.W))
  val data_bits4 = RegInit(0.U(8.W))
  
  //val out_valid = RegInit(false.B)
  val length = RegInit(0.U(8.W))
  val reminder = RegInit(4.U(3.W))
  //val rec_reminder = RegInit(false.B)
  //val start = RegInit(false.B)
  
  val out_data = Wire(UInt(8.W))//RegInit(0.U(8.W))
  val out_data_valid = Wire(Bool())//RegInit(false.B)
  val out_data_ready = Wire(Bool())
  
  //val out_start = RegInit(false.B)
  
  //val counter = RegInit(0.U(2.W))
    
  val idle :: s1 :: s2 :: s3 :: s4 :: waitData :: Nil = Enum(6)
  val state = RegInit(idle)
  
  //STATES MACHINE
  
  when(state === idle){//STATE: IDLE
    
    reminder := 4.U
    //rec_reminder := false.B
    when(out_data_ready){
      in_data_ready := true.B
    }
    when(in_data_valid){
      in_data_ready := false.B
      //out_start := true.B
      state := s1
      
      //reminder := 4.U
      //rec_reminder := false.B
      
      data_bits1 := in_data(31,24)
      data_bits2 := in_data(23,16)
      data_bits3 := in_data(15,8)
      data_bits4 := in_data(7,0)
    }
    
  }.elsewhen(state === s1){//STATE: s1
    
    when(soft_reset){
      state := idle
    }.elsewhen(out_data_ready){
      
      when(reminder === 1.U){
        state := idle
      }.otherwise{
        state := s2
      }
      
      //out_data_valid := true.B
    }

    
  }.elsewhen(state === s2){//STATE: s2
    
    //out_start := false.B
    when(soft_reset){
      state := idle
    }.elsewhen(out_data_ready){
      
      when(reminder === 2.U){
        state := idle
      }.otherwise{
        state := s3
      }

      //out_data_valid := true.B
    }
  
  }.elsewhen(state === s3){//STATE: s3

    when(soft_reset){
      state := idle
    }.elsewhen(out_data_ready){
 
      when(reminder === 3.U){
        state := idle
      }.otherwise{
        state := s4
      }
    
      //out_data_valid := true.B
    }
    
  }.elsewhen(state === s4){//STATE: s4
  
    when(soft_reset){
      state := idle
    }.elsewhen(out_data_ready){
    
      when(reminder === 0.U){
        state := idle
      }.otherwise{
        in_data_ready := true.B
        state := waitData
      }
      
      //out_data_valid := true.B   
    } 
    
  }.otherwise{//STATE: waitData
    
    when(soft_reset){
      state := idle
    }.elsewhen(in_data_valid){
      in_data_ready := false.B
      state := s1
      
      data_bits1 := in_data(31,24)
      data_bits2 := in_data(23,16)
      data_bits3 := in_data(15,8)
      data_bits4 := in_data(7,0)
    }
    
  }
  
  out_data := Mux(state===s1, data_bits1, Mux(state===s2, data_bits2, Mux(state===s3, data_bits3, data_bits4)))
  
  when(in_reminder_valid){
    reminder := in_reminder
    //rec_reminder := true.B
  }
  
  //when(out_data_valid === true.B){
  //  out_data_valid := false.B
  //}
  out_data_valid := Mux(out_data_ready && (state === s1 || state === s2 || state === s3 || state === s4) && !soft_reset, true.B, false.B)
  
  //when(in_data_valid){
  //  in_data_ready := false.B
  //}
  
  //flag_counter := counter// + 1.U
  
  //OUTPUTS
  io.in.data.ready := in_data_ready
  io.in.reminder.ready := in_reminder_ready
  
  
  io.out.data.bits := out_data
  io.out.data.valid := out_data_valid
  out_data_ready := io.out.data.ready
  
  //io.out.start := out_start
   
}//end of the class

