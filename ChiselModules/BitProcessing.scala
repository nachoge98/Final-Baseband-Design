package chipyard.baseband//BitProcessing//note


import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint
import chisel3.util.{log2Ceil, Decoupled}
import dsptools.numbers._

//import CRC._
//import Whitening._
//import RxChain._
//import TxChain._
//import OctetGenerator._


// INPUTS
class BPInputBundle extends Bundle { //cambiado el input bundle para anadir mi enable, y la AA.
  
  val rx_data = Flipped(Decoupled(UInt(1.W)))
  
  val tx_data = Flipped(Decoupled(UInt(8.W)))
  
  val en_Rx = Input(Bool())
  val en_Tx = Input(Bool())
  val ownAccessAddress = Flipped(Decoupled(UInt(32.W)))
  
  val tx_AccessAddress = Flipped(Decoupled(UInt(32.W)))
  
  val soft_reset = Input(Bool())  
  
  override def cloneType: this.type = BPInputBundle().asInstanceOf[this.type]
}

object BPInputBundle {
  def apply(): BPInputBundle = new BPInputBundle
}

//OUTPUTS 
class BPOutputBundle extends Bundle {

  //rx data
  val rx_data = Decoupled(UInt(8.W))
  val rx_lengthPayload = Decoupled(UInt(8.W))
  val rx_reminder = Decoupled(UInt(24.W))
  
  //rx flags
  val rx_start = Output(Bool())
  val rx_done = Output(Bool())

  //rx check flags
  val corrAA = Decoupled(Bool())//Output(Bool())
  val corrCRC = Decoupled(Bool())//Output(Bool())
  
  //tx data
  val tx_data = Decoupled(UInt(1.W))
  
  //tx flags
  val done = Output(Bool())
  
  //section flags
  //val headerPDU = Output(Bool())
  //val payloadPDU = Output(Bool())
  //val CRC = Output(Bool())
  
  override def cloneType: this.type = BPOutputBundle().asInstanceOf[this.type]
}

object BPOutputBundle {
  def apply(): BPOutputBundle = new BPOutputBundle
} 

//FULL IO bundle 

class BPIO extends Bundle {
  val in = new BPInputBundle
  val out = new BPOutputBundle
  //val param = Input(ParameterBundle())
  //val out = new RxOutputBundle

  override def cloneType: this.type = BPIO().asInstanceOf[this.type]
}

object BPIO{
	def apply(): BPIO = new BPIO
}

class BitProcessing extends Module {
   
  val io = IO(new BPIO)  
    
  //CREATED WIRES FOR BLOCKS:
  
  //__RX CHAIN BLOCK WIRES__
  //inputs
  /*
  data = Flipped(Decoupled(UInt(1.W)))
  val enable = Input(Bool())
  val ownAccessAddress = Flipped(Decoupled(UInt(32.W)))
  */
  
  //outputs
  
  //whitening
  val rx_wh_out_bits = Wire(UInt(1.W))
  val rx_wh_out_valid = Wire(Bool())
  
  val tx_wh_out_bits = Wire(UInt(1.W))
  val tx_wh_out_valid = Wire(Bool())
  
  //crc
  val rx_crc_out_bits = Wire(UInt(1.W))
  val rx_crc_out_valid = Wire(Bool())
  
  val tx_crc_out_bits = Wire(UInt(1.W))
  val tx_crc_out_valid = Wire(Bool())
  
  //__WHITENING BLOCK WIRES__
  val wh_in_data = Wire(UInt(1.W))
  wh_in_data := Mux(io.in.en_Rx, rx_wh_out_bits, Mux(io.in.en_Tx, tx_wh_out_bits, 0.U))
  val wh_in_valid = Wire(Bool())
  wh_in_valid := Mux(io.in.en_Rx, rx_wh_out_valid, Mux(io.in.en_Tx, tx_wh_out_valid, false.B))
  val wh_in_ready = Wire(Bool()) // //at the moment im not using this ready signal
  
  val wh_reset = Wire(Bool()) //added at the end of the block definition section
  //wh_reset := Mux(io.in.en_Rx, rxChain.io.wh.reset, Mux(io.in.en_Tx, false.B, false.B))
  
  val wh_in_state = Wire(UInt(7.W))//
  wh_in_state := "b1111111".U //"b1111111".U //"b0100101".U // so channel index is 111111: 127 in theory, this is not possible, num of channels in BLE is 37.
  //anadir como cambiar el wh state
  
  val wh_out_data = Wire(UInt(1.W))//
  val wh_out_valid = Wire(Bool())//
  val wh_out_ready = Wire(Bool()) //added at the end of the block definition section
  //wh_out_ready := Mux(io.in.en_Rx, rxChain.io.wh.in_data.ready, Mux(io.in.en_Tx, false.B, false.B))
  
  //__CRC BLOCK WIRES__
  
  val crc_init_state = Wire(UInt(24.W))//this default value is the one of broadcasting, T0D0
  crc_init_state := "b010101010101010101010101".U
  //anadir como cambiar el crc state
  
  val crc_in_reset = Wire(Bool())
  //crc_in_reset := Mux(io.in.en_Rx, rxChain.io.crc.reset, Mux(io.in.en_Tx, false.B, false.B)) //probablemente de problemas
  
  val crc_in_data = Wire(UInt(1.W))
  crc_in_data := Mux(io.in.en_Rx, rx_crc_out_bits, Mux(io.in.en_Tx, tx_crc_out_bits, 0.U))
  val crc_in_ready = Wire(Bool())
  val crc_in_valid = Wire(Bool())
  crc_in_valid := Mux(io.in.en_Rx, rx_crc_out_valid, Mux(io.in.en_Tx, tx_crc_out_valid, false.B))
  
  val crc_out_state = Wire(UInt(24.W))
  val crc_out_valid = Wire(Bool())
  val crc_out_ready = Wire(Bool())
  //crc_out_ready := Mux(io.in.en_Rx, rxChain.io.crc.in_data.ready, Mux(io.in.en_Tx, false.B, false.B))
  
  
  //REGISTERS:
  
  val txAccessAddress = RegInit(0.U(32.W))
  val txAccessAddress_valid = RegInit(false.B)
  
  txAccessAddress_valid := io.in.tx_AccessAddress.valid
  when(io.in.tx_AccessAddress.valid){
    txAccessAddress := Reverse(io.in.tx_AccessAddress.bits)
  }
  
  //USED BLOCKS:

  // ___Rx Chain___
  val rxChain = Module(new RxChain)
  
  //inputs
  rxChain.io.in.data.bits := io.in.rx_data.bits
  rxChain.io.in.data.valid := io.in.rx_data.valid
  io.in.rx_data.ready := rxChain.io.in.data.ready
  
  rxChain.io.in.enable := io.in.en_Rx
  
  rxChain.io.in.soft_reset := io.in.soft_reset
  
  rxChain.io.in.ownAccessAddress.bits := Reverse(io.in.ownAccessAddress.bits)
  rxChain.io.in.ownAccessAddress.valid := io.in.ownAccessAddress.valid
  io.in.ownAccessAddress.ready := rxChain.io.in.ownAccessAddress.ready//Mux(io.in.en_Rx, rxChain.io.in.ownAccessAddress.ready, Mux(io.in.en_Tx, txChain.io.in.ownAccessAddress.ready, false.B))
  
  //outputs
  io.out.rx_data.bits := rxChain.io.out.data.bits
  io.out.rx_data.valid := rxChain.io.out.data.valid
  rxChain.io.out.data.ready := io.out.rx_data.ready

  io.out.rx_lengthPayload.bits := rxChain.io.out.lengthPayload.bits
  io.out.rx_lengthPayload.valid := rxChain.io.out.lengthPayload.valid
  rxChain.io.out.lengthPayload.ready := io.out.rx_lengthPayload.ready
  
  io.out.rx_reminder.bits := rxChain.io.out.reminder.bits
  io.out.rx_reminder.valid := rxChain.io.out.reminder.valid
  rxChain.io.out.reminder.ready := io.out.rx_reminder.ready
  
  io.out.rx_start := rxChain.io.out.start
  io.out.rx_done := rxChain.io.out.done
    
  io.out.corrAA.bits := rxChain.io.out.corrAA.bits
  io.out.corrAA.valid := rxChain.io.out.corrAA.valid
  rxChain.io.out.corrAA.ready := io.out.corrAA.ready
  
  io.out.corrCRC.bits := rxChain.io.out.corrCRC.bits
  io.out.corrCRC.valid := rxChain.io.out.corrCRC.valid
  rxChain.io.out.corrCRC.ready := io.out.corrCRC.ready
  
  //wh
  rx_wh_out_bits := rxChain.io.wh.out_data.bits
  rx_wh_out_valid := rxChain.io.wh.out_data.valid
  rxChain.io.wh.out_data.ready := Mux(io.in.en_Rx, wh_in_ready, false.B)
  
  rxChain.io.wh.in_data.bits := Mux(io.in.en_Rx, wh_out_data, 0.U)
  rxChain.io.wh.in_data.valid := Mux(io.in.en_Rx, wh_out_valid, false.B)
  //rxChain.io.wh.in_data.ready
  
  //crc
  rx_crc_out_bits := rxChain.io.crc.out_data.bits
  rx_crc_out_valid := rxChain.io.crc.out_data.valid
  rxChain.io.crc.out_data.ready := Mux(io.in.en_Rx, crc_in_ready, false.B)
  
  //rxChain.io.crc.reset
  
  rxChain.io.crc.in_data.bits := Mux(io.in.en_Rx, crc_out_state, 0.U)//0.U
  rxChain.io.crc.in_data.valid := Mux(io.in.en_Rx, crc_out_valid, false.B)
  //rxChain.io.crc.in_data.ready
  
  // ___Tx Chain___
  val txChain = Module(new TxChain)
  
  //inputs
  txChain.io.in.data.bits := io.in.tx_data.bits
  txChain.io.in.data.valid := io.in.tx_data.valid
  io.in.tx_data.ready := txChain.io.in.data.ready
  
  txChain.io.in.soft_reset := io.in.soft_reset//false.B
  
  txChain.io.in.start := io.in.en_Tx
  
  txChain.io.in.ownAccessAddress.bits := txAccessAddress//Reverse(io.in.ownAccessAddress.bits)
  txChain.io.in.ownAccessAddress.valid := txAccessAddress_valid//io.in.tx_AccessAddress.valid//io.in.ownAccessAddress.valid
  io.in.tx_AccessAddress.ready := txChain.io.in.ownAccessAddress.ready//Mux(io.in.en_Rx, rxChain.io.in.ownAccessAddress.ready, Mux(io.in.en_Tx, txChain.io.in.ownAccessAddress.ready, false.B))
  
  //outputs  
  io.out.tx_data.bits := txChain.io.out.data.bits
  io.out.tx_data.valid := txChain.io.out.data.valid
  txChain.io.out.data.ready := io.out.tx_data.ready
  io.out.done := txChain.io.out.done
  
  //wh
  tx_wh_out_bits := txChain.io.wh.out_data.bits
  tx_wh_out_valid := txChain.io.wh.out_data.valid
  txChain.io.wh.out_data.ready := Mux(io.in.en_Tx, wh_in_ready, false.B)
  
  txChain.io.wh.in_data.bits := Mux(io.in.en_Tx, wh_out_data, 0.U)
  txChain.io.wh.in_data.valid := Mux(io.in.en_Tx, wh_out_valid, false.B)
  
  //crc
  tx_crc_out_bits := txChain.io.crc.out_data.bits
  tx_crc_out_valid := txChain.io.crc.out_data.valid
  txChain.io.crc.out_data.ready := Mux(io.in.en_Tx, crc_in_ready, false.B)
  
  //txChain.io.crc.reset
  
  txChain.io.crc.in_data.bits := Mux(io.in.en_Tx, crc_out_state, 0.U)//0.U
  txChain.io.crc.in_data.valid := Mux(io.in.en_Tx, crc_out_valid, false.B)
  //txChain.io.crc.in_data.ready
  
  // ___Whitening___
  val whBlock = Module(new Whitening)

  wh_reset := Mux(io.in.en_Rx, rxChain.io.wh.reset, Mux(io.in.en_Tx, txChain.io.wh.reset, false.B))
  wh_out_ready := Mux(io.in.en_Rx, rxChain.io.wh.in_data.ready, Mux(io.in.en_Tx, txChain.io.wh.in_data.ready, false.B))

  whBlock.io.in.data.bits := wh_in_data
  wh_in_ready := whBlock.io.in.data.ready
  whBlock.io.in.data.valid := wh_in_valid
  whBlock.io.in.init_state := wh_in_state
  whBlock.io.in.reset := wh_reset
  
  wh_out_data := whBlock.io.out.data.bits
  whBlock.io.out.data.ready := wh_out_ready
  wh_out_valid := whBlock.io.out.data.valid
  

  
  // ___CRC___
  val crcBlock = Module(new CRC)
 
  crc_in_reset := Mux(io.in.en_Rx, rxChain.io.crc.reset, Mux(io.in.en_Tx, txChain.io.crc.reset, false.B))
  crc_out_ready := Mux(io.in.en_Rx, rxChain.io.crc.in_data.ready, Mux(io.in.en_Tx, txChain.io.crc.in_data.ready, false.B))  

  crcBlock.io.in.init_state := crc_init_state
  crcBlock.io.in.reset := crc_in_reset
  crcBlock.io.in.data.bits := crc_in_data
  crc_in_ready := crcBlock.io.in.data.ready
  crcBlock.io.in.data.valid := crc_in_valid

  crc_out_state := crcBlock.io.out.state.bits
  crc_out_valid := crcBlock.io.out.state.valid
  crcBlock.io.out.state.ready := crc_out_ready
  /*
  crc_init_state
  crc_in_reset
  crc_in_data
  crc_in_ready
  crc_in_valid
  
  crc_out_state
  crc_out_valid
  crc_out_ready
  */

}//end of the class

