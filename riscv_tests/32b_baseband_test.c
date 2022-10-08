#define BASEBAND_FIFO_WRITE 0x2000
#define BASEBAND_FIFO_WRITE_COUNT 0x2008
#define BASEBAND_FIFO_READ 0x2100
#define BASEBAND_FIFO_READ_COUNT 0x2108


#include <stdio.h>
#include <inttypes.h>
#include <stdint.h>

#include "mmio.h"

/*/////
................
Author: Nacho
................
Date: 09/05/2022
................
*//////

/*/////
................
Comments: This program will be the same the 
................
*//////

#define BYTE_TO_BINARY_PATTERN "%c%c%c%c%c%c%c%c"
#define BYTE_TO_BINARY(byte)  \
  (byte & 0x80 ? '1' : '0'), \
  (byte & 0x40 ? '1' : '0'), \
  (byte & 0x20 ? '1' : '0'), \
  (byte & 0x10 ? '1' : '0'), \
  (byte & 0x08 ? '1' : '0'), \
  (byte & 0x04 ? '1' : '0'), \
  (byte & 0x02 ? '1' : '0'), \
  (byte & 0x01 ? '1' : '0') 

int main(void)
{
/*
  val command00 = "b00100001" //clearAA      //-- //--  //Clear AA List
  val command01 = "b00100010" //rxOne        //-- //++  //Rx until message received
  val command02 = "b00100100" //rxContinuous //-- //++  //Rx until stop
  val command03 = "b00101000" //rxStop       //-- //--  //Rx stop
  
  val command04 = "b01000001" //sendStatus   //-- //++  //Status
  val command05 = "b01000010" //txSavedBroad //-- //--  //Send saved data to broadcast AA
  val command06 = "b01000100" //txSavedList  //-- //--  //send saved data to following AA in the list
  
  val command07 = "b10100001" //modifyAA     //++ //--  //change own AA 
  val command08 = "b10100010" //setTrPower   //++ //--  //Set transmission power 
  val command09 = "b10100100" //savePacket   //++ //--  //save packet in the registers  
  val command10 = "b10101000" //saveAA       //++ //--  //save AA in the list 

  val command11 = "b11000001" //txSavedAA    //++ //--  //Send saved data to the received AA 
  val command12 = "b11000010" //tx           //++ //--  //Normal transmission state 
  val command13 = "b11000100" //saveListAA   //++ //--  //Save a List of AAs 
  
  val command14 = "b00010001" //actEC        //-- //--  //Activate error correction
  val command15 = "b00010010" //deactEC      //-- //--  //Deactivate error correction
  val command16 = "b00010100" //soft_reset   //-- //--  //trigger soft_reset
*/
  //commands:
  uint8_t command00 = 0x21U; //clearAA      //Clear AA List
  uint8_t command01 = 0x22U; //rxOne        //Rx until message received
  uint8_t command02 = 0x24U; //rxContinuous //Rx until stop
  uint8_t command03 = 0x28U; //rxStop       //Rx stop
  
  uint8_t command04 = 0x41U; //sendStatus   //Status
  uint8_t command05 = 0x42U; //txSavedBroad //Send saved data to broadcast AA
  uint8_t command06 = 0x44U; //txSavedList  //send saved data to following AA in the list
  
  uint8_t command07 = 0xA1U; //modifyAA     //change own AA 
  uint8_t command08 = 0xA2U; //setTrPower   //Set transmission power 
  uint8_t command09 = 0xA4U; //savePacket   //save packet in the registers  
  uint8_t command10 = 0xA8U; //saveAA       //save AA in the list 
  
  uint8_t command11 = 0xC1U; //txSavedAA    //Send saved data to the received AA 
  uint8_t command12 = 0xC2U; //tx           //Normal transmission state 
  uint8_t command13 = 0xC4U; //saveListAA   //Save a List of AAs
  
  uint8_t command14 = 0x11U; //actEC        //Activate error correction
  uint8_t command15 = 0x12U; //deactEC      //Deactivate error correction
  uint8_t command16 = 0x14U; //soft_reset   //trigger soft_reset
          
  //data:
  uint32_t trPower = 0x000064U; //100 in decimal
  uint32_t txLengths = 0x8283U;//val txLengths =  "000000001000001010000011"
  
  //Broadcast AA: 0x8E89BED6
  uint32_t accessAddress1 = 0x8E89BEU;//0x6B7D91U;//val accessAddress1 = "011010110111110110010001"
  uint32_t accessAddress2 = 0xD6U;//0x71U;//val accessAddress2 = "000000000000000001110001"
  uint32_t aaLengths = 0x2U;//val aaLengths =  "000000000000000000000010"
  
  uint32_t inPacket1 = 0x0FA0CCU;//var inPacket1 = "000011111010000011001100"
  uint32_t inPacket2 = 0xCE32AAU;//var inPacket2 = "110011100011001010101010"
  uint32_t inPacket3 = 0xCC0000U;//var inPacket3 = "110011000000000000000000"
  
  //uint32_t bbb = 1;
  //uint32_t done = 0;
  //uint32_t data;
  uint32_t FIFO_OUT;
  
  
  
  //printf("Hardware result %d does not match reference value %d\n", x, y);
  //printf("Command clearAA: %d\n", command00);
  //printf("command: %u data: %u combination: %u\n", command08, trial, (command08 << 24)|trial);
  
  printf("Start of the programm\n");
  //______________
  //STATE: setTrPower & sendStatus
  //--------------  
  reg_write32(BASEBAND_FIFO_WRITE, (command08 << 24)|trPower);
  reg_write32(BASEBAND_FIFO_WRITE, (command04 << 24));
  
  FIFO_OUT = reg_read32(BASEBAND_FIFO_READ);
  //printf("read transmission power: %u\n", FIFO_OUT & 0xFFU);
  //printf("read RSSI: %i\n", (FIFO_OUT & 0xFF00U)>>8);
  //printf("read channel: %u\n", (FIFO_OUT & 0x00FF0000U)>>16);
  //printf("status : "BYTE_TO_BINARY_PATTERN, BYTE_TO_BINARY((FIFO_OUT & 0xFF000000U)>>24));
  //printf("; \n");
  
  //______________
  //STATE: modifyAA
  //--------------  
  reg_write32(BASEBAND_FIFO_WRITE, (command07 << 24)|accessAddress1);
  reg_write32(BASEBAND_FIFO_WRITE, (command07 << 24)|accessAddress2);

  //______________
  //STATE: actEC & deactEC & actEC
  //--------------  
  reg_write32(BASEBAND_FIFO_WRITE, (command14 << 24));
  reg_write32(BASEBAND_FIFO_WRITE, (command15 << 24));
  reg_write32(BASEBAND_FIFO_WRITE, (command14 << 24));

  //______________
  //STATE: tx
  //-------------- 
  reg_write32(BASEBAND_FIFO_WRITE, (command12 << 24)|txLengths);
  reg_write32(BASEBAND_FIFO_WRITE, (command12 << 24)|accessAddress1);
  reg_write32(BASEBAND_FIFO_WRITE, (command12 << 24)|accessAddress2);
  reg_write32(BASEBAND_FIFO_WRITE, (command12 << 24)|inPacket1);
  reg_write32(BASEBAND_FIFO_WRITE, (command12 << 24)|inPacket2);
  reg_write32(BASEBAND_FIFO_WRITE, (command12 << 24)|inPacket3);
  
  
  //______________
  //STATE: rxOne
  //--------------  
  reg_write32(BASEBAND_FIFO_WRITE, (command01 << 24));
  FIFO_OUT = reg_read32(BASEBAND_FIFO_READ);
  //printf("received data: %x\n", FIFO_OUT & 0x00FFFFFFU);
  FIFO_OUT = reg_read32(BASEBAND_FIFO_READ);
  //printf("received data: %x\n", FIFO_OUT & 0x00FFFFFFU);
  FIFO_OUT = reg_read32(BASEBAND_FIFO_READ);
  //printf("received data: %x\n", FIFO_OUT & 0x00FFFFFFU);
  
  //______________
  //STATE: rxContinuous & rxStop
  //--------------  
  reg_write32(BASEBAND_FIFO_WRITE, (command02 << 24));
  reg_write32(BASEBAND_FIFO_WRITE, (command03 << 24));
  
  //______________
  //STATE: saveListAA
  //--------------  
  reg_write32(BASEBAND_FIFO_WRITE, (command13 << 24)|aaLengths);
  reg_write32(BASEBAND_FIFO_WRITE, (command13 << 24)|accessAddress1);
  reg_write32(BASEBAND_FIFO_WRITE, (command13 << 24)|accessAddress2);
  reg_write32(BASEBAND_FIFO_WRITE, (command13 << 24)|accessAddress1);
  reg_write32(BASEBAND_FIFO_WRITE, (command13 << 24)|accessAddress2);
  
  //______________
  //STATE: savePacket
  //--------------  
  reg_write32(BASEBAND_FIFO_WRITE, (command09 << 24)|txLengths);
  reg_write32(BASEBAND_FIFO_WRITE, (command09 << 24)|inPacket1);
  reg_write32(BASEBAND_FIFO_WRITE, (command09 << 24)|inPacket2);
  reg_write32(BASEBAND_FIFO_WRITE, (command09 << 24)|inPacket3);
  
  /*
  //______________
  //STATE: txSavedList
  //--------------  
  reg_write32(BASEBAND_FIFO_WRITE, (command06 << 24));
  reg_write32(BASEBAND_FIFO_WRITE, (command06 << 24));//so we see how it resets
  */
  
  /*
  //______________
  //STATE: txSavedAA
  //--------------  
  reg_write32(BASEBAND_FIFO_WRITE, (command11 << 24));
  reg_write32(BASEBAND_FIFO_WRITE, (command11 << 24)|accessAddress1);
  reg_write32(BASEBAND_FIFO_WRITE, (command11 << 24)|accessAddress2);
  */
  
  //______________
  //STATE: txSavedBroad
  //--------------  
  reg_write32(BASEBAND_FIFO_WRITE, (command05 << 24));  
  /*
  //______________
  //STATE: clearAA
  //--------------
  reg_write32(BASEBAND_FIFO_WRITE, (command00 << 24)); 
  */
  return 0;
   
}
