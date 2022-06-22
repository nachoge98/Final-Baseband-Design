package chipyard.baseband//PreambleDetector //note


import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint
import chisel3.util.{log2Ceil, Decoupled}
import dsptools.numbers._

 
// INPUTS
class PDInputBundle extends Bundle {
  //val switch = Input(Bool())
  val data = Flipped(Decoupled(UInt(1.W))) //input data
  val aa_0 = Flipped(Decoupled(UInt(1.W)))  //LSB of the AA of the device, the preamble depends on it in L1M

  override def cloneType: this.type = PDInputBundle().asInstanceOf[this.type]
}

object PDInputBundle {
  def apply(): PDInputBundle = new PDInputBundle
}

//OUTPUTS 
class PDOutputBundle extends Bundle {
  val data = Decoupled(UInt(1.W)) //same bit as in the input delayed one clock cycle.
  val preamble_det = Output(Bool()) //the correct preamble for LE1M
  
  override def cloneType: this.type = PDOutputBundle().asInstanceOf[this.type]
}

object PDOutputBundle {
  def apply(): PDOutputBundle = new PDOutputBundle
} 

//FULL IO bundle 
class PreambleDetectorIO extends Bundle {
  val in = new PDInputBundle
  //val param = Input(ParameterBundle())
  val out = new PDOutputBundle

  override def cloneType: this.type =
    PreambleDetectorIO().asInstanceOf[this.type]
}

object PreambleDetectorIO {
  def apply(): PreambleDetectorIO = new PreambleDetectorIO
}

//Class Preamble Detector
class PreambleDetector extends Module {

  val io = IO(new PreambleDetectorIO)
  
  //variables for the state machine in charge of the physical layer
  //val le1m :: le2m :: lec :: Nil =  Enum(3)
  //val phy = RegInit(le1m)
  
  val idle :: aa0_0 :: aa0_1 :: aa0_2 :: aa0_3 :: aa0_4 :: aa0_5 :: aa0_6 :: aa1_0 :: aa1_1 :: aa1_2 :: aa1_3 :: aa1_4 :: aa1_5 :: aa1_6 :: detected :: Nil =  Enum(16)
  val pattern_matcher_state = RegInit(idle)
  
  val aa0 = RegInit(0.U(1.W))
  val data_reg = RegInit(0.U(1.W))
  val out_valid = RegInit(false.B)
  val found_preamb = RegInit(false.B)
  
  //val preamble0 = "b01010101".U
  //val preamble1 = "b10101010".U
  //val preamble_aa = Mux(aa0 === 0.U, preamble0, preamble1)

  //idle to init1/init0
  
  when(io.in.aa_0.valid){
  
    pattern_matcher_state := idle
    out_valid := false.B
    aa0 := io.in.aa_0.bits
  
  }.elsewhen(io.in.data.valid){
    //state machine
    switch (pattern_matcher_state) {
      is (idle) {
        when(aa0 === 0.U && io.in.data.bits === 0.U){
          pattern_matcher_state := aa0_0
        }.elsewhen(aa0 === 1.U && io.in.data.bits === 1.U){
          pattern_matcher_state := aa1_0
        }
      }
      is (aa0_0) {
        when(io.in.data.bits === 1.U){
          pattern_matcher_state := aa0_1
        }.otherwise{
          pattern_matcher_state := aa0_0
        }
      }
      is (aa1_0) {
        when(io.in.data.bits === 0.U){
          pattern_matcher_state := aa1_1
        }.otherwise{
          pattern_matcher_state := aa1_0
        }
      }
      is (aa0_1) {
        when(io.in.data.bits === 0.U){
          pattern_matcher_state := aa0_2
        }.otherwise{
          pattern_matcher_state := idle//aa0_0
        }
      }
      is (aa1_1) {
        when(io.in.data.bits === 1.U){
          pattern_matcher_state := aa1_2
        }.otherwise{
          pattern_matcher_state := idle//aa1_0
        }
      }
      is (aa0_2) {
        when(io.in.data.bits === 1.U){
          pattern_matcher_state := aa0_3
        }.otherwise{
          pattern_matcher_state := aa0_0
        }
      }
      is (aa1_2) {
        when(io.in.data.bits === 0.U){
          pattern_matcher_state := aa1_3
        }.otherwise{
          pattern_matcher_state := aa1_0
        }
      }
      is (aa0_3) {
        when(io.in.data.bits === 0.U){
          pattern_matcher_state := aa0_4
        }.otherwise{
          pattern_matcher_state := idle//aa0_0
        }
      }
      is (aa1_3) {
        when(io.in.data.bits === 1.U){
          pattern_matcher_state := aa1_4
        }.otherwise{
          pattern_matcher_state := idle//aa1_0
        }
      }
      is (aa0_4) {
        when(io.in.data.bits === 1.U){
          pattern_matcher_state := aa0_5
        }.otherwise{
          pattern_matcher_state := aa0_0
        }
      }
      is (aa1_4) {
        when(io.in.data.bits === 0.U){
          pattern_matcher_state := aa1_5
        }.otherwise{
          pattern_matcher_state := aa1_0
        }
      }
      is (aa0_5) {
        when(io.in.data.bits === 0.U){
          pattern_matcher_state := aa0_6
        }.otherwise{
          pattern_matcher_state := idle//aa0_0
        }
      }
      is (aa1_5) {
        when(io.in.data.bits === 1.U){
          pattern_matcher_state := aa1_6
        }.otherwise{
          pattern_matcher_state := idle//aa1_0
        }
      }
      is (aa0_6) {
        when(io.in.data.bits === 1.U){
          pattern_matcher_state := detected
          found_preamb := true.B
        }.otherwise{
          pattern_matcher_state := aa0_0
        }
      }
      is (aa1_6) {
        when(io.in.data.bits === 0.U){
          pattern_matcher_state := detected
          found_preamb := true.B
        }.otherwise{
          pattern_matcher_state := aa1_0
        }
      }
      //is (detected) {
        //pattern_matcher_state := idle
        //found_preamb := false.B
        /*
        when(aa0 === 0.U && io.in.data.bits === 0.U){
          pattern_matcher_state := aa0_0
        }.elsewhen(aa0 === 1.U && io.in.data.bits === 1.U){
          pattern_matcher_state := aa1_0
        }.otherwise{
          pattern_matcher_state := idle
        }
        */
      //}
    }
    
    //data and valid reg
    data_reg := io.in.data.bits
    out_valid := io.in.data.valid
  }
  
  //lets get out of detected in one just clock cycle and reset the flag.
  when(pattern_matcher_state === detected){
    pattern_matcher_state := idle
    found_preamb := false.B
  }

  //outputs
  io.in.data.ready := true.B
  io.in.aa_0.ready := true.B
  //io.in.phy.ready := true.B

  io.out.data.valid := out_valid
  io.out.preamble_det := found_preamb
  io.out.data.bits := data_reg

}
