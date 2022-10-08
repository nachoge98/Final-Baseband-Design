package chipyard.baseband//Baseband//note

import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint
import chisel3.util.{log2Ceil, Decoupled}
import dsptools.numbers._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem._


//import BitProcessing._
//import ErrCorr._
//import outSequencer._
//import inSequencer._

//__________________
//______INPUTS______
//__________________
class BasebandInputBundle extends Bundle { //cambiado el input bundle para anadir mi enable, y la AA.
  
  val data = Flipped(Decoupled(UInt(1.W))) 
  
  override def cloneType: this.type = BasebandInputBundle().asInstanceOf[this.type]
}

object BasebandInputBundle {
  def apply(): BasebandInputBundle = new BasebandInputBundle
}

class BasebandInputFIFOBundle extends Bundle { //cambiado el input bundle para anadir mi enable, y la AA.
  
  val data = Output(UInt(32.W))
  
  override def cloneType: this.type = BasebandInputFIFOBundle().asInstanceOf[this.type]
}

object BasebandInputFIFOBundle {
  def apply(): BasebandInputFIFOBundle = new BasebandInputFIFOBundle
}

//Input FIFO Controller
class BasebandControlBundle extends Bundle {

  val data = Output(UInt(8.W))  
  
  override def cloneType: this.type = BasebandControlBundle().asInstanceOf[this.type]
}

object BasebandControlBundle {
  def apply(): BasebandControlBundle = new BasebandControlBundle
}

//_________________
//_____OUTPUTS_____
//_________________

//Output Bundle
class BasebandOutputBundle extends Bundle {

  val data = Decoupled(UInt(1.W))
  
  override def cloneType: this.type = BasebandOutputBundle().asInstanceOf[this.type]
}

object BasebandOutputBundle {
  def apply(): BasebandOutputBundle = new BasebandOutputBundle
} 

//Output FIFO Bundle
class BasebandOutputFIFOBundle extends Bundle { //cambiado el input bundle para anadir mi enable, y la AA.
  
  val data = Output(UInt(32.W))
  
  override def cloneType: this.type = BasebandOutputFIFOBundle().asInstanceOf[this.type]
}

object BasebandOutputFIFOBundle {
  def apply(): BasebandOutputFIFOBundle = new BasebandOutputFIFOBundle
}

//__________________
//__FULL IO bundle__ 
//__________________

class BasebandIO extends Bundle {
  val in = new BasebandInputBundle
  val out = new BasebandOutputBundle
  //val con = Flipped(Decoupled(BasebandControlBundle()))
  val inFIFO = Flipped(Decoupled(BasebandInputFIFOBundle()))
  val outFIFO = Decoupled(BasebandOutputFIFOBundle())

  override def cloneType: this.type = BasebandIO().asInstanceOf[this.type]
}

object BasebandIO{
  def apply(): BasebandIO = new BasebandIO
}

//__________________
//_____BASEBAND_____
//__________________

class Baseband extends Module {
   
  val io = IO(new BasebandIO)  
    
  //_________________________
  //CREATED WIRES FOR BLOCKS:
  //_________________________
  
  //___Input Sequencer___
  
  //inputs
  val is_in_data_ready = Wire(Bool())
  
  val is_in_reminder_ready = Wire(Bool())
  
  //Outputs
  val is_out_data = Wire(UInt(8.W))//8b
  val is_out_data_valid = Wire(Bool())
  val is_out_data_ready = Wire(Bool())
  
  //val is_out_start = Wire(Bool())
  
  
  //___BIT PROCESSING___\
  
  //inputs
  val bp_in_rxData = Wire(UInt(1.W))
  bp_in_rxData := io.in.data.bits//0.U
  val bp_in_rxData_valid = Wire(Bool())
  bp_in_rxData_valid := io.in.data.valid//false.B
  val bp_in_rxData_ready = Wire(Bool())
  
  
  val bp_in_txData = Wire(UInt(8.W))
  bp_in_txData := 0.U
  val bp_in_txData_valid = Wire(Bool())
  bp_in_txData_valid := false.B
  
  val bp_in_ownAA_ready = Wire(Bool()) 
  
  
  //outputs
  val bp_out_rxData = Wire(UInt(8.W))//RegInit(0.U(8.W))//Wire(UInt(8.W))
  val bp_out_rxData_valid = Wire(Bool())//RegInit(false.B)//Wire(Bool())
  val bp_out_rxData_ready = Wire(Bool())
  bp_out_rxData_ready := true.B
  
  val bp_out_rxLength = Wire(UInt(8.W))//RegInit(0.U(8.W))//Wire(UInt(8.W))
  val bp_out_rxLength_valid = Wire(Bool())//RegInit(false.B)//Wire(Bool())
  val bp_out_rxLength_ready = Wire(Bool())
  bp_out_rxLength_ready := true.B
  
  val bp_out_rxReminder = Wire(UInt(24.W))//RegInit(0.U(24.W))//Wire(UInt(24.W))
  val bp_out_rxReminder_valid = Wire(Bool())//RegInit(false.B)//Wire(Bool())
  val bp_out_rxReminder_ready = Wire(Bool())
  bp_out_rxReminder_ready := true.B
  
  val bp_out_rxStart = RegInit(false.B)//RegInit(false.B)//Wire(Bool())
  val bp_out_rxDone = RegInit(false.B)//RegInit(false.B)//Wire(Bool())
  
  val bp_out_rxCorrAA = Wire(Bool())//RegInit(false.B)//Wire(Bool())
  val bp_out_rxCorrAA_valid = Wire(Bool())//RegInit(false.B)//Wire(Bool())
  val bp_out_rxCorrAA_ready = Wire(Bool())
  bp_out_rxCorrAA_ready := true.B
  
  val bp_out_rxCorrCRC = Wire(Bool())//RegInit(false.B)//Wire(Bool())
  val bp_out_rxCorrCRC_valid = Wire(Bool())//RegInit(false.B)//Wire(Bool())
  val bp_out_rxCorrCRC_ready = Wire(Bool())
  bp_out_rxCorrCRC_ready := true.B
  
  val bp_out_txData = Wire(UInt(1.W))
  val bp_out_txData_valid = Wire(Bool())
  val bp_out_txData_ready = Wire(Bool())
  //bp_out_txData_ready := true.B
  
  val bp_out_txDone = Wire(Bool())
  
  //___ERROR CORRECTION___
  //inputs
  //Directly wired to the outputs of the Bit Processing Block
  
  //outputs
  val ec_out_data = Wire(UInt(8.W))
  val ec_out_data_valid = Wire(Bool())
  val ec_out_data_ready = Wire(Bool())
  ec_out_data_ready := true.B
  
  val ec_out_start = Wire(Bool())
  val ec_out_done = Wire(Bool())
  
  val ec_out_corrCRC = Wire(UInt(1.W)) //I have to modify this to be a boolean output instead of an UInt.
  val ec_out_corrCRC_valid = Wire(Bool())
  val ec_out_corrCRC_ready = Wire(Bool())
  //ec_out_corrCRC_ready := true.B
  
  //input controller
  val ec_con_enable = Wire(Bool())
  ec_con_enable := true.B
  
  
  //___OUTPUT SEQUENCER___
  //Outputs
  val os_out_data = Wire(UInt(32.W))
  val os_out_data_valid = Wire(Bool())
  val os_out_data_ready = Wire(Bool())  
  
  //___________________
  //_____REGISTERS_____
  //___________________
  
  //val idle :: continuousRx :: sendStatus ::   Nil = Enum(3)
  //val state = RegInit(idle)
  val idle :: clearAA :: rxOne :: rxContinuous :: sendStatus :: txSavedBroad :: txSavedList :: modifyAA :: setTrPower :: savePacket :: saveAA :: txSavedAA :: tx :: saveListAA :: actEC :: deactEC :: clearPayload :: Nil = Enum(17)
  val state = RegInit(idle)
  
  //val basebandAA = RegInit(0.U(32.W))//implemented but not connected
  val basebandAA1 = RegInit(0.U(24.W))
  val basebandAA2 = RegInit(0.U(8.W))
  val basebandAA = Wire(UInt(32.W))
  basebandAA := Cat(basebandAA1, basebandAA2)
  val basebandAA_valid = RegInit(false.B)//implemented but not connected
  
  val enable_errorCorrection = RegInit(false.B)
  
  val soft_reset = RegInit(false.B)  
  
  //REGISTERS AAs:
  val listAA_inFIFO_ready = Wire(Bool())
  
  val idle_listAA :: length_listAAs :: receiving_AAs :: writing_AAs ::  Nil = Enum(4)
  val state_listAA = RegInit(idle_listAA)
  
  val list_AAs = RegInit(VecInit(Seq.fill(10)(0.U(32.W))))
  val temporal_AA_reg = RegInit(0.U(24.W))
  val num_AA_list = RegInit(0.U(4.W))
  val write_idx_AA_list = RegInit(0.U(4.W))
  val read_idx_AA_list = RegInit(0.U(4.W))
  
  //REGISTERS PACKET:
  val regPacket_inFIFO_ready = Wire(Bool())
  
  val idle_regPacket :: length_regPacket :: receiving_regPacket ::  Nil = Enum(3)
  val state_regPacket = RegInit(idle_regPacket)
  
  val reg_packet = RegInit(VecInit(Seq.fill(63)(0.U(24.W)))) //register data packet, usar vector en grupos de 32 en 32 //251 bytes max /4=62.7 -> 63
  val reg_packet_valid = RegInit(false.B)//Wire(Bool())//RegInit(false.B) //register used as valid signal for the input sequencer when sending a saved packet.
  //reg_packet_valid := Mux(state_txMode === transmitting_savedMode, is_in_data_ready, false.B)
  val reg_num32_packet = RegInit(0.U(7.W)) //indicates the number of 32 bit sections that contain data. // 251/3 = 83.6 -> 84.
  val reg_payLength_packet = RegInit(0.U(8.W)) //necessary max 251 bytes //2^8 = 256
  val reg_reminder_packet = RegInit(0.U(3.W)) //values can be 0,1,2. But im going to keep it on 3 bits.
  
  val write_idx_packet = RegInit(0.U(7.W))
  val read_idx_packet = RegInit(0.U(7.W))
  
  //REGISTERS TX MODE:
  val tx_inFIFO_ready = Wire(Bool())
  
  val enable_tx = Wire(Bool())//RegInit(false.B)//not implemented
  val inReminder = Wire(UInt(32.W))
  val inReminder_valid = Wire(Bool())
  
  val idle_txMode :: rec_AA1_txMode :: rec_AA2_txMode :: saved_AA_txMode :: delayState_txMode :: transmitting_txMode :: transmitting_savedMode ::  Nil = Enum(7)
  val state_txMode = RegInit(idle_txMode) //deleted the state: rec_length_txMode
  
  val reg_num32_normalTx = RegInit(0.U(7.W)) //indicates the number of 32 bit sections that contain data. // 251/4 = 62.7 -> 63.
  val reg_payLength_normalTx = RegInit(0.U(8.W)) //necessary max 251 bytes //2^8 = 256
  val reg_reminder_normalTx = RegInit(0.U(3.W)) //values can be 0,1,2,3
  val reg_reminderValid_normalTx = RegInit(false.B) //
  val reg_rec32_normalTx = RegInit(0.U(7.W)) //indicates the number of 32 bit sections of data received.
  
  val txMode_access_address1 = RegInit(0.U(24.W))
  val txMode_access_address2 = RegInit(0.U(8.W))
  val txMode_access_address = Wire(UInt(32.W))//RegInit(0.U(32.W))
  txMode_access_address := Cat(txMode_access_address1, txMode_access_address2)
  val txMode_access_address_valid = RegInit(false.B)
  
  //reg_packet_valid := Mux(state_txMode === transmitting_savedMode, is_in_data_ready, false.B)
  //reg_num32_normalTx,reg_payLength_normalTx,reg_reminder_normalTx
  
  //REGISTERS RX MODE:
  val rx_inAntenna_ready = Wire(Bool())
  val rx_inCon_ready = Wire(Bool())
  
  val enable_rx = Wire(Bool())//RegInit(false.B)//not implemented
  val idle_rxMode :: listening_rxMode :: receiving_rxMode :: transmitting_rxMode ::  Nil = Enum(4)
  val state_rxMode = RegInit(idle_rxMode)
  
  
  //Registers INPUT CONTROLLER:  
  val control_data = Wire(UInt(8.W))//RegInit(0.U(8.W))//Wire(UInt(8.W))//RegInit(0.U(8.W))//TIENEN QUE SER WIRES
  control_data := io.inFIFO.bits.data(31,24)//io.con.bits.data
  //val control_valid = RegInit(false.B)//Wire(Bool())//RegInit(false.B)
  //control_valid := io.con.valid
  //val control_ready = RegInit(false.B)//Wire(Bool())//RegInit(false.B)
  //control_ready := true.B
  
  val inFIFO_data = Wire(UInt(24.W))
  inFIFO_data := io.inFIFO.bits.data(23,0)
  val inFIFO_valid = Wire(Bool())
  inFIFO_valid := io.inFIFO.valid
  //val inFIFO_ready = Wire(Bool())
  //inFIFO_ready := false.B
  
  
  //REGISTERS STATUS:
  val reg_channel = RegInit(27.U(8.W)) //probably doesnt need 8 bits, creo que eran 5 //COMPROBAR //he puesto default 27 por ejemplo hulio
  val reg_RSSI = RegInit(0.S(8.W)) //(-127<=N<=20)
  val reg_transPower = RegInit(0.U(8.W)) //probably doesnt need 8 bits //COMPROBAR
  val out_status = Wire(UInt(32.W))
  out_status := Cat("b11111111".U,reg_channel,reg_RSSI,reg_transPower)//STILL TO BE DETERMINED, first idea is this (8bit flag to ONE when reporting status).
  
  //Multiplexor con varias inputs.
  //MuxCase(default,
  //      Array((idx === 0.U) -> a,
  //            (idx === 1.U) -> b, ...))
  //MuxCase(false.B, Array((state === rxContinuous) -> true.B, (state === rxOne) -> true.B))
  
  //___________________
  //_______LOGIC_______
  //___________________
  //: actEC :: deactEC
  
  when(inFIFO_valid && control_data(4) && control_data(2)){ //soft reset input
    soft_reset := true.B  //to be checked
    //state := idle
    
    when(state === saveListAA){
      state := clearAA
    }.elsewhen(state === savePacket){
      state := clearPayload
    }.otherwise{
      state := idle
    }
  }
  
  switch (state) {
    is (idle) {
    
      when(inFIFO_valid  && !control_data(7) && control_data(5) && control_data(0)){
      
        state := clearAA
        
      }.elsewhen(inFIFO_valid && !control_data(7) && control_data(5) && control_data(1) ){
      
        state := rxOne
        state_rxMode := listening_rxMode
        
      }.elsewhen(inFIFO_valid && !control_data(7) && control_data(5) && control_data(2) ){
      
        state := rxContinuous
        state_rxMode := listening_rxMode
        
      }.elsewhen(inFIFO_valid && !control_data(7) && control_data(6) && control_data(0) ){
      
        state := sendStatus
        
      }.elsewhen(inFIFO_valid && !control_data(7) && control_data(6) && control_data(1) ){
        
        when(reg_num32_packet =/= 0.U){
          state := txSavedBroad
          state_txMode := saved_AA_txMode
        }.otherwise{
          state := idle
          state_txMode := idle_txMode
        }
        
      }.elsewhen(inFIFO_valid && !control_data(7) && control_data(6) && control_data(2) ){
        
        when(num_AA_list =/= 0.U && reg_num32_packet =/= 0.U){
          state := txSavedList
          state_txMode := saved_AA_txMode
        }.otherwise{
          state := idle
          state_txMode := idle_txMode
        }
        
      }.elsewhen(inFIFO_valid && control_data(7) && control_data(5) && control_data(0) ){
      
        state := modifyAA
        basebandAA1 := inFIFO_data
        
      }.elsewhen(inFIFO_valid && control_data(7) && control_data(5) && control_data(1) ){
      
        //state := setTrPower
        reg_transPower := inFIFO_data(7,0)
        
      }.elsewhen(inFIFO_valid && control_data(7) && control_data(5) && control_data(2) ){
        
        when(inFIFO_data(6,0) >= 1.U){
          state := savePacket
          state_regPacket := receiving_regPacket//length_regPacket
        
          reg_num32_packet := inFIFO_data(6,0) //7bits //before the 32 to 24 bits it was 6.
          reg_payLength_packet := inFIFO_data(14,7) //8bits
          reg_reminder_packet := inFIFO_data(17,15) //3bits
          write_idx_packet := 0.U //7bits
          read_idx_packet := 0.U  //7bits
        }.otherwise{
          state := idle
          state_regPacket := idle_regPacket
        }

      }.elsewhen(inFIFO_valid && control_data(7) && control_data(5) && control_data(3) ){
      
        state := saveAA
        state_listAA := receiving_AAs
        num_AA_list := num_AA_list + 1.U
        
      }.elsewhen(inFIFO_valid && control_data(7) && control_data(6) && control_data(0) ){
        
        when(reg_num32_packet =/= 0.U){
          state := txSavedAA
          state_txMode := rec_AA1_txMode//rec_length_txMode
        }.otherwise{
          state := idle
          state_txMode := idle_txMode
        }
        
      }.elsewhen(inFIFO_valid && control_data(7) && control_data(6) && control_data(1) ){
        
        state := tx
        state_txMode := rec_AA1_txMode//rec_length_txMode
        reg_num32_normalTx := inFIFO_data(6,0) //6bits
        reg_payLength_normalTx := inFIFO_data(14,7) //8bits
        reg_reminder_normalTx := inFIFO_data(17,15)//(inFIFO_data(13,6).asUInt + 2.U) % 3.U //3bits
        
      }.elsewhen(inFIFO_valid && control_data(7) && control_data(6) && control_data(2) ){
        
        when(inFIFO_data(3,0) >= 1.U){
          state := saveListAA
          state_listAA := receiving_AAs
          num_AA_list := inFIFO_data(3,0)
          write_idx_AA_list := 0.U
          read_idx_AA_list := 0.U
        }.otherwise{
          state := idle
          state_listAA := idle_listAA
        }
        
      }.elsewhen(inFIFO_valid && control_data(4) && control_data(0) ){
      
        state := actEC
        
      }.elsewhen(inFIFO_valid && control_data(4) && control_data(1) ){
      
        state := deactEC
        
      }
      basebandAA_valid := false.B //flag valid for when the own AA is changed
      
    }
    
    is (clearAA) {//STATE: clearAA
    
      num_AA_list := 0.U
      write_idx_AA_list := 0.U
      read_idx_AA_list := 0.U
      state := idle
      
    }
    is (rxOne) {//STATE: rxOne
    
      //enable_rx := true.B
      //enable_tx := false.B
      //state_rxMode := listening_rxMode
      //state := idle
      
    }
    is (rxContinuous) {//STATE: rxContinuous
    
      //state := idle
      
    }
    is (sendStatus) { //STATE: sendStatus
    
      state := idle
      
    }
    is (txSavedBroad) { //STATE: txSaveBroad
    
      //state := idle
    
    }
    is (txSavedList) {
    
      //state := idle
      
    }
    is (modifyAA) {//STATE: modifyAA
    
      when(inFIFO_valid && control_data(7) && control_data(5) && control_data(0)){
        basebandAA2 := inFIFO_data(7,0)
        basebandAA_valid := true.B
        state := idle
      }.elsewhen(inFIFO_valid){
        state := idle
      }
        
    }
    is (setTrPower) {
    
      //when(inFIFO_valid){
      //  state := idle
      //  reg_transPower := inFIFO_data(7,0)
      //}
      
    }
    is (savePacket) {//STATE: savePacket
    
      //state := idle
      
    }
    is (saveAA) {//STATE: saveAA
    
      //state := idle
      
    }
    is (txSavedAA) {//STATE: txSavedAA
    
      //state := idle
      
    }
    is (tx) {//STATE: tx
    
      //state := idle
      
    }
    is (saveListAA) {//STATE: saveListAA
    
      //state := idle
      
    }
    is (actEC) {//STATE: actEC
    
      state := idle
      enable_errorCorrection := true.B
      
    }
    is (deactEC) {//STATE: deactEC
    
      state := idle
      enable_errorCorrection := false.B
      
    }
    is (clearPayload) {//STATE: clearPayload

      reg_num32_packet := 0.U
      write_idx_packet := 0.U
      read_idx_packet := 0.U
      state := idle
      
    }
  }
  
  enable_rx := MuxCase(false.B, Array((state === rxOne) -> true.B, (state === rxContinuous) -> true.B))
  enable_tx := MuxCase(false.B, Array((state === tx) -> true.B, (state === txSavedAA) -> true.B, (state === txSavedBroad) -> true.B, (state === txSavedList) -> true.B))
  
  //************************************************
  //State Machine to write the List Of Access Address
  switch (state_listAA) {
    is (idle_listAA) {
    
    }
    is (length_listAAs) {
    
      when(inFIFO_valid){
        num_AA_list := inFIFO_data(3,0)
        state_listAA := receiving_AAs
      }
      write_idx_AA_list := 0.U
      read_idx_AA_list := 0.U
    
    }
    is (receiving_AAs) {
    
      when(inFIFO_valid && !soft_reset){//has to be minor than the max number of AAs I can store right now, I can change it.
        temporal_AA_reg := inFIFO_data
        state_listAA := writing_AAs
      }
    
    }
    is (writing_AAs) {
    
      when(inFIFO_valid && !soft_reset){//has to be minor than the max number of AAs I can store right now, I can change it.
        list_AAs(write_idx_AA_list) := Cat(temporal_AA_reg, inFIFO_data(7,0))
        write_idx_AA_list := write_idx_AA_list + 1.U
        
        when(write_idx_AA_list === (num_AA_list - 1.U)){
          state_listAA := idle_listAA
          state := idle
        }.otherwise{
          state_listAA := receiving_AAs
        }  
      }
      
    }
  }
  listAA_inFIFO_ready := MuxCase(false.B, Array((state_listAA === length_listAAs) -> true.B, (state_listAA === receiving_AAs) -> true.B, (state_listAA === writing_AAs) -> true.B))
  
  
  //******************************************
  //State Machine to write and save the Packet
  switch (state_regPacket) {
    is (idle_regPacket) {
    
    }
    is (length_regPacket) {
    
      when(inFIFO_valid){
        reg_num32_packet := inFIFO_data(6,0) //7bits //before the 32 to 24 bits it was 6.
        reg_payLength_packet := inFIFO_data(14,7) //8bits
        reg_reminder_packet := inFIFO_data(17,15) //3bits
        //now the reminder number will be received as data, I do not want to do a by 3 module. A by 4 module was easy hardware, not anymore.
        //reg_reminder_packet := (inFIFO_data(14,7).asUInt + 2.U) % 4.U //3bits
        state_regPacket := receiving_regPacket 
      }
      write_idx_packet := 0.U //7bits
      read_idx_packet := 0.U  //7bits
    
    }
    is (receiving_regPacket) {
      
      when(inFIFO_valid && !soft_reset){
        reg_packet(write_idx_packet) := inFIFO_data
        write_idx_packet := write_idx_packet + 1.U
        when(write_idx_packet === (reg_num32_packet - 1.U)){
          state_regPacket := idle_regPacket
          state := idle
        }
      }

      
    }
  }  
  regPacket_inFIFO_ready := MuxCase(false.B, Array((state_regPacket === length_regPacket) -> true.B, (state_regPacket === receiving_regPacket) -> true.B))

  //************************************************
  //State Machine Rx Modes
  switch (state_rxMode) {
    is (idle_rxMode) {
    }
    
    is (listening_rxMode) {
      when(inFIFO_valid && !control_data(7) && control_data(5) && control_data(3)){
        state := idle
        state_rxMode := idle_rxMode
      }.elsewhen(bp_out_rxStart){
        state_rxMode := receiving_rxMode
      }
    }
    
    is (receiving_rxMode) {
      when(bp_out_rxReminder_valid){ //&& state === rxOne
        state_rxMode := transmitting_rxMode
      } 
    }
    
    is (transmitting_rxMode) {
      when(os_out_data(30) === 1.U && os_out_data_valid){
        state_rxMode := Mux(state === rxOne, idle_rxMode, listening_rxMode)
        state := Mux(state === rxOne, idle, rxContinuous)
      }
    }
  }
  rx_inAntenna_ready := MuxCase(false.B, Array((state_rxMode === listening_rxMode) -> true.B,(state_rxMode === receiving_rxMode) -> true.B))
  rx_inCon_ready := Mux( (state_rxMode===listening_rxMode) && (state===rxContinuous), true.B, false.B)

  //************************************************
  //State Machine Tx Modes
    switch (state_txMode) {
      is (idle_rxMode) {
      }
      /*
      is (rec_length_txMode) {
      
        when(inFIFO_valid){
          state_txMode := rec_AA_txMode
          reg_num32_normalTx := inFIFO_data(5,0) //6bits
          reg_payLength_normalTx := inFIFO_data(13,6) //8bits
          reg_reminder_normalTx := (inFIFO_data(13,6).asUInt + 2.U) % 4.U //3bits
        }
        
      }
      */
      is (rec_AA1_txMode) {

        when(inFIFO_valid){
          txMode_access_address1 := inFIFO_data
          state_txMode := rec_AA2_txMode
        }

      }
      is (rec_AA2_txMode) {

        when(inFIFO_valid){
          txMode_access_address2 := inFIFO_data(7,0)
          txMode_access_address_valid := true.B
          state_txMode := delayState_txMode
        }

      }
      is (saved_AA_txMode) {
      
        txMode_access_address_valid := true.B
        state_txMode := delayState_txMode
        
      }
      is (delayState_txMode) {
      
        txMode_access_address_valid := false.B
        when(state === tx){
          state_txMode := transmitting_txMode
        }.elsewhen(state === txSavedAA || state === txSavedBroad || state === txSavedList){
          state_txMode := transmitting_savedMode
        }  
        
      }
      is (transmitting_txMode) {
      
        when(inFIFO_valid && is_in_data_ready){
          when(reg_rec32_normalTx === (reg_num32_normalTx - 1.U)){
            reg_reminderValid_normalTx := true.B //aun hay que hacerlo false.
          }.otherwise{
            reg_rec32_normalTx := reg_rec32_normalTx + 1.U
          }          
        }
        when(bp_out_txDone){
          reg_reminderValid_normalTx := false.B
          state := idle
          state_txMode := idle_rxMode
        }
        
      }
      is (transmitting_savedMode) { //transmitting_savedMode is_in_data_ready
        
        when(is_in_data_ready && !reg_packet_valid){
          reg_packet_valid := true.B
        }
        when(reg_packet_valid){
          reg_packet_valid := false.B
          when(read_idx_packet === (reg_num32_packet - 1.U)){
            reg_reminderValid_normalTx := true.B //aun hay que hacerlo false.
          }.otherwise{
            read_idx_packet := read_idx_packet + 1.U
          }
        }
        when(bp_out_txDone){
          reg_reminderValid_normalTx := false.B
          read_idx_packet := 0.U
          state := idle
          state_txMode := idle_rxMode
          when(state === txSavedList){ //in case we are using the list of AA, update the read idx.
            when(read_idx_AA_list === num_AA_list - 1.U){
              read_idx_AA_list := 0.U
            }.otherwise{
              read_idx_AA_list := read_idx_AA_list + 1.U
            }  
          }
        }
        
      }
    }  
  
  tx_inFIFO_ready := MuxCase(false.B,
                           Array((state_txMode === rec_AA1_txMode) -> true.B,
                                 (state_txMode === rec_AA2_txMode) -> true.B,
                                 (state_txMode === saved_AA_txMode) -> false.B,
                                 (state_txMode === transmitting_txMode) -> is_in_data_ready,
                                 (state_txMode === transmitting_savedMode) -> false.B))
                                 
  inReminder := MuxCase(0.U,
                           Array((state === tx) -> reg_reminder_normalTx,
                                 (state === txSavedAA) -> reg_reminder_packet,
                                 (state === txSavedBroad) -> reg_reminder_packet,
                                 (state === txSavedList) -> reg_reminder_packet))
  inReminder_valid := MuxCase(false.B,
                           Array((state === tx) -> reg_reminderValid_normalTx,
                                 (state === txSavedAA) -> reg_reminderValid_normalTx,
                                 (state === txSavedBroad) -> reg_reminderValid_normalTx,
                                 (state === txSavedList) -> reg_reminderValid_normalTx))
  
  //tx_access_address := MuxCase(, Array((state === tx) -> ))
  //tx_access_address_valid := 
  
  //when(reg_packet_valid){
  //  reg_packet_valid := false.B
  //}
  
  when(soft_reset){
    soft_reset := false.B
  }
  
  //__________________________
  //USED BLOCKS & CONNECTIONS:
  //__________________________
  val inSeq = Module(new InputSequencer)
  val bitProcessor = Module(new BitProcessing)
  val errCorr = Module(new ErrCorrBase)
  val outSeq = Module(new OutputSequencer)
  

  //___Input Sequencer___
  
  inSeq.io.in.data.bits := MuxCase(0.U,
                                 Array((state_txMode === transmitting_txMode) -> inFIFO_data,
                                       (state_txMode === transmitting_savedMode) -> reg_packet(read_idx_packet)))//is_in_data //32b
  inSeq.io.in.data.valid := MuxCase(0.U,
                                  Array((state_txMode === transmitting_txMode) -> inFIFO_valid,
                                        (state_txMode === transmitting_savedMode) -> reg_packet_valid))//is_in_data_valid
  is_in_data_ready := inSeq.io.in.data.ready
  
  inSeq.io.in.reminder.bits := inReminder//io.in.debug_reminder.bits//is_in_reminder //3b
  inSeq.io.in.reminder.valid := inReminder_valid//io.in.debug_reminder.valid//is_in_reminder_valid
  is_in_reminder_ready := inSeq.io.in.reminder.ready
  
  inSeq.io.in.soft_reset := soft_reset
  
  is_out_data := inSeq.io.out.data.bits //8b
  is_out_data_valid := inSeq.io.out.data.valid
  inSeq.io.out.data.ready := is_out_data_ready
  
  //is_out_start := inSeq.io.out.start

  //___BIT PROCESSING___
  
  bp_out_rxStart := bitProcessor.io.out.rx_start//Mux(bitProcessor.io.out.rx_start, true.B, false.B)
  bp_out_rxDone := bitProcessor.io.out.rx_done//Mux(bitProcessor.io.out.rx_done, true.B, false.B)
  
  //inputs
  bitProcessor.io.in.rx_data.bits := bp_in_rxData//io.in.data.bits//bp_in_rxData
  bitProcessor.io.in.rx_data.valid := bp_in_rxData_valid//io.in.data.valid//bp_in_rxData_valid
  bp_in_rxData_ready := bitProcessor.io.in.rx_data.ready //bp_in_rxData_ready//io.in.data.ready 
  
  bitProcessor.io.in.tx_data.bits := is_out_data//bp_in_txData
  bitProcessor.io.in.tx_data.valid := is_out_data_valid//bp_in_txData_valid
  is_out_data_ready := bitProcessor.io.in.tx_data.ready
  //bp_in_txData_ready := bitProcessor.io.in.tx_data.ready
  //bp_in_txData_ready := bitProcessor.io.in.tx_data.ready
  
  bitProcessor.io.in.en_Rx := enable_rx//bp_in_enRx
  bitProcessor.io.in.en_Tx := enable_tx//bp_in_enTx
  
  bitProcessor.io.in.soft_reset := soft_reset
  
  bitProcessor.io.in.ownAccessAddress.bits := basebandAA //bp_in_ownAA//io.in.ownAccessAddress.bits//bp_in_ownAA
  bitProcessor.io.in.ownAccessAddress.valid := basebandAA_valid //bp_in_ownAA_valid//io.in.ownAccessAddress.valid//bp_in_ownAA_valid
  bp_in_ownAA_ready := bitProcessor.io.in.ownAccessAddress.ready //bp_in_ownAA_ready //io.in.ownAccessAddress.ready
  
  bitProcessor.io.in.tx_AccessAddress.bits := MuxCase(txMode_access_address,
                                                    Array((state === tx) -> txMode_access_address,
                                                          (state === txSavedAA) -> txMode_access_address,
                                                          (state === txSavedBroad) -> "b01101011011111011001000101110001".U,
                                                          (state === txSavedList) -> list_AAs(read_idx_AA_list))) //bp_in_txAA
  bitProcessor.io.in.tx_AccessAddress.valid := MuxCase(false.B,
                                                     Array((state === tx) -> txMode_access_address_valid,
                                                           (state === txSavedAA) -> txMode_access_address_valid,
                                                           (state === txSavedBroad) -> txMode_access_address_valid,
                                                           (state === txSavedList) -> txMode_access_address_valid)) //bp_in_txAA_valid
  //bp_in_txAA_ready := bitProcessor.io.in.tx_AccessAddress.ready 
  
  //outputs
  bp_out_rxData := bitProcessor.io.out.rx_data.bits
  bp_out_rxData_valid := bitProcessor.io.out.rx_data.valid
  bitProcessor.io.out.rx_data.ready := bp_out_rxData_ready
  
  bp_out_rxLength := bitProcessor.io.out.rx_lengthPayload.bits
  bp_out_rxLength_valid := bitProcessor.io.out.rx_lengthPayload.valid
  bitProcessor.io.out.rx_lengthPayload.ready := bp_out_rxLength_ready
  
  bp_out_rxReminder := bitProcessor.io.out.rx_reminder.bits
  bp_out_rxReminder_valid := bitProcessor.io.out.rx_reminder.valid
  bitProcessor.io.out.rx_reminder.ready := bp_out_rxReminder_ready
  
  //bp_out_rxStart := bitProcessor.io.out.rx_start
  //bp_out_rxDone := bitProcessor.io.out.rx_done
  
  bp_out_rxCorrAA := bitProcessor.io.out.corrAA.bits
  bp_out_rxCorrAA_valid := bitProcessor.io.out.corrAA.valid
  bitProcessor.io.out.corrAA.ready := bp_out_rxCorrAA_ready
  
  bp_out_rxCorrCRC := bitProcessor.io.out.corrCRC.bits
  bp_out_rxCorrCRC_valid := bitProcessor.io.out.corrCRC.valid
  bitProcessor.io.out.corrCRC.ready := bp_out_rxCorrCRC_ready
  
  bp_out_txData := bitProcessor.io.out.tx_data.bits
  bp_out_txData_valid := bitProcessor.io.out.tx_data.valid
  bitProcessor.io.out.tx_data.ready := bp_out_txData_ready
  
  bp_out_txDone := bitProcessor.io.out.done
  

  
  //___ERROR CORRECTION___
  
  //inputs
  errCorr.io.inRx.data.bits := bp_out_rxData//newReg//bp_out_rxData_bits//0.U//bp_out_rxData
  errCorr.io.inRx.data.valid := bp_out_rxData_valid//false.B//bp_out_rxData_valid
  //bp_out_rxData_ready := errCorr.io.inRx.data.ready
  
  errCorr.io.inRx.length.bits := bp_out_rxLength//0.U//bp_out_rxLength
  errCorr.io.inRx.length.valid := bp_out_rxLength_valid//false.B//bp_out_rxLength_valid
  //bp_out_rxLength_ready := errCorr.io.inRx.length.ready
  
  errCorr.io.inRx.reminder.bits := bp_out_rxReminder//0.U//bp_out_rxReminder
  errCorr.io.inRx.reminder.valid := bp_out_rxReminder_valid//false.B//bp_out_rxReminder_valid
  //bp_out_rxReminder_ready := errCorr.io.inRx.reminder.ready
  
  errCorr.io.inRx.start := bp_out_rxStart//false.B  //Mux(bitProcessor.io.out.rx_start, true.B, false.B)//false.B//bp_out_rxStart
  errCorr.io.inRx.done := bp_out_rxDone//false.B//Mux(bitProcessor.io.out.rx_done, true.B, false.B)//false.B//bp_out_rxDone
  
  errCorr.io.inRx.corrAA.bits := bp_out_rxCorrAA//false.B//bp_out_rxCorrAA
  errCorr.io.inRx.corrAA.valid := bp_out_rxCorrAA_valid//false.B//bp_out_rxCorrAA_valid
  //bp_out_rxCorrAA_ready := errCorr.io.inRx.corrAA.ready
  
  errCorr.io.inRx.corrCRC.bits := bp_out_rxCorrCRC//false.B//bp_out_rxCorrCRC
  errCorr.io.inRx.corrCRC.valid := bp_out_rxCorrCRC_valid//false.B//bp_out_rxCorrCRC_valid
  //bp_out_rxCorrCRC_ready := errCorr.io.inRx.corrCRC.ready
  
  //outputs
  ec_out_data := errCorr.io.out.data.bits
  ec_out_data_valid := errCorr.io.out.data.valid
  errCorr.io.out.data.ready := ec_out_data_ready//
  
  ec_out_start := errCorr.io.out.start
  ec_out_done := errCorr.io.out.done
  
  ec_out_corrCRC := errCorr.io.out.corrCRC.bits //still have to implement the use of this flag in the sequencer
  ec_out_corrCRC_valid := errCorr.io.out.corrCRC.valid //still have to implement the use of this flag in the sequencer
  errCorr.io.out.corrCRC.ready := ec_out_corrCRC_ready //still have to implement the use of this flag in the sequencer
  
  //input controller
  errCorr.io.inCon.enableErrCorr := enable_errorCorrection//ec_con_enable
  errCorr.io.inCon.soft_reset := soft_reset
  
  
    
  //___OUTPUT SEQUENCER___
  
  //inputs
  outSeq.io.in.data.bits := ec_out_data
  outSeq.io.in.data.valid := ec_out_data_valid
  ec_out_data_ready := outSeq.io.in.data.ready
  
  outSeq.io.in.discard := soft_reset //false.B//at the moment I use the soft reset, maybe I want to discard more stuff
  outSeq.io.in.start := ec_out_start
  outSeq.io.in.done := ec_out_done
  
  outSeq.io.in.corrCRC.bits := ec_out_corrCRC
  outSeq.io.in.corrCRC.valid := ec_out_corrCRC_valid
  ec_out_corrCRC_ready := outSeq.io.in.corrCRC.ready
  
  //outputs
  os_out_data := outSeq.io.out.data.bits
  os_out_data_valid := outSeq.io.out.data.valid
  outSeq.io.out.data.ready := os_out_data_ready
  
  
  /*
  LIST OF TODOS:
  
  -inputFIFO ready, sin hacer todavia
  +hecho
  
  -Implement the channel thing in the bit processing in addition to the controller:
  +Solved adding the channel in the status reg con un multiplexor en la salida y el estado status.
  
  -implement the corrCRC flag in the output sequencer
  +Done
  
  -ready data input from fifo
  +DONE
  
  -Estados a implementar:
    -(001_0-0001):Clear AA list
    -(001_0-0010):Rx until message received
    -(001_0-0100):Rx until stop
    -(001_0-1000):Rx stop
    
    -(010_0-0001):Status
    -(010_0-0010):Send saved data to broadcast AA
    -(010_0-0100):Send saved data to following AA in the list
    -(010_0-1000):_______________
    
    -(101_0-0001):Change own AA ++
    -(101_0-0010):Set transmission Power ++
    -(101_0-0100):Save packet in the registers ++
    -(101_0-1000):Save AA in the list ++
    
    -(110_0-0001):Send saved data to the received AA ++
    -(110_0-0010):Normal transmission state ++
    -(110_0-0100):________________
    -(110_0-1000):________________
    
    -(000_1-0001):Activate Error Correction
    -(000_1-0010):Deactivate Error Correction
  +DONE, he creado incluso mas comandos y estados
  
  -eliminar la salida start del input_sequencer, no hace falta para la tx_chain, ya lo apane con el bloque bit processing 
  +DONE
  
  -anadir comando CLEAN en general que resetee a idle y todo a 0
  +he anadido el soft_reset, que ya cancela cualquier cosa funcionando.
  
  
  
  -Cuando manda la payload y aun esta haciendo shift a el registro de la payload, se queda la out_valid en true, revisar
  --no me acuerdo de que es esto ni donde mirarlo
  
  -flag discard in the output sequencer
  -+De momento uso el soft reset, tengo que pensar si hay algun caso mas en el que me interese ponerla a true.

  -ready control input from fifo
  --creo que solo voy a ponerla en false en los estados de un solo ciclo (reset, errCorr, y alguno mas)
  
  -en la lectura de AA y packets para guardarlos, anadir algo para tener en cuenta el maximo espacio y la maxima longitud, ahora mismo se ignora.
  
  -comprobar que pasa con el receiver cuando la AA esta mal, creo que no funciona.

  -anadir paquete con doble error al test
  
  -revisar pequenos detalles y alargar los tests
  
  -comprobar bit width del transmission power, y del resto de basuras del status. (channel etc)
  
  -anadir metodo con el que se pueda resolver si se recibe un error en la longitud de la payload, (guardar consecutivamente hasta 3 bytes, y enviar el 4 (siendo el 4o mas viejo que los otros 3),
   y cuando hayan pasado x ciclos sin recibir ningun bit de datos hacer un trigger para resolverlo y enviar los bytes).
   
   -implementar state con el que limpiar el fifo de datos de entrada, en caso de que se quiera esccribir una payload + larga de lo permitido, o una lista de AA mas larga de lo permitido.
   
   -implementar estado borrar packet?
   
   -asegurarse que no se entre a ningun estado de transmitir cosas guardadas si no hay alguna de las cosas guardadas necesarias.
   
   -maxima length packet y AA list.
   
   -implementar el control del estado inicial del whitening con el channel
   
   -implementar el control del estado inicial del crc
   
   -creo que la secuencia de datos que utilizo para recibir (rx) esta mal, uso preamble 01010101, cuando el AA empieza con 1.
    aun asi funciona bien y los datos extraidos de la recepcion estan bien, pero algo ahi no esta bien.
    
    -seria interesante anadir un comando extra para resetear a 0 el read Idx de la lista de AA, sin necesidad de borrarlas.
    
    -anadir input channel (tanto rx como tx y los estados para inicializarlo al whitening etc).
    
    -anadir input rssi
    
    -anadir output white channel list y su bloque   
  */
  
  //_____________
  //___OUTPUTS___
  //_____________
  
  //io.con.ready := control_ready
  io.inFIFO.ready := MuxCase(true.B,
                           Array((state === sendStatus) -> false.B,
                                 (state === deactEC) -> false.B,
                                 (state === actEC) -> false.B,
                                 (state === clearAA) -> false.B,
                                 (state === tx) -> tx_inFIFO_ready,
                                 (state === txSavedAA) -> tx_inFIFO_ready,
                                 (state === txSavedBroad) -> tx_inFIFO_ready,
                                 (state === txSavedList) -> tx_inFIFO_ready,
                                 (state === saveListAA || state === saveAA) -> listAA_inFIFO_ready,
                                 (state === savePacket) -> regPacket_inFIFO_ready))
  
  io.in.data.ready := rx_inAntenna_ready//bp_in_rxData_ready
  
  io.outFIFO.bits.data := Mux(state === sendStatus, out_status, os_out_data) //the data to the FIFO is always going to be either the status, or the output of the receiver
  io.outFIFO.valid := Mux(os_out_data_ready, Mux(state === sendStatus, true.B, os_out_data_valid), false.B) //output can only be valid when output is ready (to the FIFO)
  os_out_data_ready := io.outFIFO.ready
  
  io.out.data.bits := bp_out_txData
  io.out.data.valid := bp_out_txData_valid
  bp_out_txData_ready := io.out.data.ready
  
}//end of the class

trait HasPeripheryBaseband extends BaseSubsystem {
  // instantiate baseband chain
  val baseband = LazyModule(new BasebandThing)
  // connect memory interfaces to pbus
  pbus.toVariableWidthSlave(Some("inDataFIFOWrite")) { baseband.writeQueue.mem.get }
  pbus.toVariableWidthSlave(Some("outDataFIFORead")) { baseband.readQueue.mem.get }
}
