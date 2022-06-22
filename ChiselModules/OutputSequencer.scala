package chipyard.baseband//outSequencer//Error Correction


import chisel3._
import chisel3.util._
//import chisel3.experimental.FixedPoint
import chisel3.util.{log2Ceil, Decoupled}
//import dsptools.numbers._

//Inputs
class OSInputBundle extends Bundle {//correction block inputs

  val data = Flipped(Decoupled(UInt(8.W)))
  val discard = Input(Bool())
  val start = Input(Bool())
  val done = Input(Bool())
  val corrCRC = Flipped(Decoupled(Bool()))

  override def cloneType: this.type = OSInputBundle().asInstanceOf[this.type]
}
object OSInputBundle {
  def apply(): OSInputBundle = new OSInputBundle
}


//Outputs
class OSOutputBundle extends Bundle {//error correction base output to the bit sequencer.
  
  val data  = Decoupled(UInt(32.W))// 8b (flags) + 24b (3 octets data)
                                   // flags: start(1b), done(1b), num_octets(2b), errorless_packet(1b), unused(3b)
                                   // octets: octet0(8b), octet1(8b), octet2(8b)
  
  override def cloneType: this.type = OSOutputBundle().asInstanceOf[this.type]
}
object OSOutputBundle {
  def apply(): OSOutputBundle = new OSOutputBundle
}

class OSIO extends Bundle {
  val in = OSInputBundle() //inputs from the Rx chain
  val out = OSOutputBundle() //inputs from the controller

  override def cloneType: this.type = OSIO().asInstanceOf[this.type]
}

object OSIO{
  def apply(): OSIO = new OSIO
}

class OutputSequencer extends Module {
  
  val io = IO(new OSIO) // IO BUNDLE
  
  //WIRES IO
  val in_start = RegNext(io.in.start, false.B)//Wire(Bool())
  //in_start := io.in.start
  val in_done = RegNext(io.in.done, false.B)//Wire(Bool())
  //in_done := io.in.done
  val in_discard = RegNext(io.in.discard, false.B)//Wire(Bool())
  //in_discard := io.in.discard
  
  val in_data = RegNext(io.in.data.bits, 0.U)//Wire(UInt(8.W))
  //in_data := io.in.data.bits
  val in_data_valid = RegNext(io.in.data.valid, false.B)//Wire(Bool())
  //in_data_valid := io.in.data.valid
  
  val in_corrCRC = RegNext(io.in.corrCRC.bits, false.B)
  val in_corrCRC_valid = RegNext(io.in.corrCRC.valid, false.B)
  
  /*
  val in_syndrome = Wire(UInt(24.W))
  in_syndrome := io.in.syndrome.bits
  val in_syndrome_valid = Wire(Bool())
  in_syndrome_valid := io.in.syndrome.valid
  //val in_syndrome_ready = Wire(Bool())
  */
  
  //REGISTERS:
  val data_bits0 = RegInit(0.U(8.W))
  val data_bits1 = RegInit(0.U(8.W))
  val data_bits2 = RegInit(0.U(8.W))
  val out_valid = RegInit(false.B)
  
  val counter = RegInit(0.U(2.W))
  
  //val flag_bits = Wire(UInt(8.W))
  val flag_start = RegInit(0.U(1.W))
  val flag_done = RegInit(0.U(1.W))
  //val flag_counter = RegInit(0.U(2.W))
  val flag_crc = RegInit(0.U(1.W))
  val flag_empty = Wire(UInt(3.W))
  flag_empty := 0.U
  //flag_bits := Cat(flag_start, flag_done, flag_counter, flag_crc, flag_empty)
  
  /*  
  val sel_bits = RegInit(0.U(25.W)) //0 concatenado con el reminder (24), para su estado inicial.
  val max_shift = RegInit(15.U(9.W))//min length = 40, max length = 288 sin tener en cuenta los 25 del polinomio crc, 15=(40-25), 263=(288-25)
  val shift = RegInit(0.U(9.W))//current shift from right side.
  val crc_polynomy = "b1000000000000011001011011".U //[1000000000000011001011011]
  val fixed_position = RegInit(5.U(9.W))
  */
    
  val idle :: notIdle :: waitingCRC :: Nil = Enum(3)
  val state = RegInit(idle)
  
  //STATES MACHINE
  
  when(state === idle){//STATE: IDLE
  
    flag_crc := 0.U
    flag_done := 0.U
    counter := 0.U
    when(in_start){
      state := notIdle
      flag_start := 1.U
    }
    
  }.elsewhen(state === notIdle){//notIdle state
    
    when(in_discard){
      data_bits2 := 0.U
      data_bits1 := 0.U
      data_bits0 := 0.U
      //change to
      state := idle
    }.elsewhen(in_data_valid){
      //data shifting
      when(counter === 3.U){
        data_bits2 := 0.U
        data_bits1 := 0.U
        data_bits0 := in_data
      }.otherwise{
        data_bits2 := data_bits1
        data_bits1 := data_bits0
        data_bits0 := in_data
      }
      //counter
      when(counter < 3.U){
        counter := counter + 1.U
      }.otherwise{
        counter := 1.U
      }  
    }.otherwise{//!in_discard && !in_data_valid
      when(counter === 3.U){
        counter := 0.U
      }
    }
    
    when(in_done){
      state := waitingCRC
    }
        
  }.otherwise{ //waiting CRC state
  
    when(in_discard){
      data_bits2 := 0.U
      data_bits1 := 0.U
      data_bits0 := 0.U
      
      //change to
      state := idle
    }.elsewhen(in_corrCRC_valid){
      state := idle
      flag_crc := in_corrCRC.asUInt
      flag_done := 1.U
    }
  }
  
  when((in_data_valid && counter === 2.U && !in_discard && state === notIdle) || (in_corrCRC_valid && state === waitingCRC && !in_discard)){// || (in_done && !in_discard && state === notIdle)
    out_valid := true.B
  }.otherwise{
    out_valid := false.B
  }
    
  when(flag_start === 1.U && out_valid === true.B){
    flag_start := 0.U
  }
  
  //flag_counter := counter// + 1.U
  
  //OUTPUTS
  io.in.data.ready := true.B
  io.in.corrCRC.ready := true.B
  
  
  
  io.out.data.bits := Cat(flag_start, flag_done, counter, flag_crc, flag_empty, data_bits2, data_bits1, data_bits0)
  io.out.data.valid := out_valid
  /*
  io.in.syndrome.ready := true.B
  io.in.length.ready := true.B
  
  io.out.val1 := val1
  io.out.val2 := val2
  io.out.sol.bits := sol
  io.out.sol.valid := out_sol_valid
  */
   
}//end of the class

