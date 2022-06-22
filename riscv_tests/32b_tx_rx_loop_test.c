#define PACKET_TX_WRITE 0x2000
#define PACKET_TX_WRITE_COUNT 0x2008
#define PACKET_TX_READ 0x2100
#define PACKET_TX_READ_COUNT 0x2108
#define PACKET_RX_WRITE 0x2200
#define PACKET_RX_WRITE_COUNT 0x2208
#define PACKET_RX_READ 0x2300
#define PACKET_RX_READ_COUNT 0x2308

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
Comments: This program will be the same as the tx_rx_loop_test.c, it is the basic test for my baseband design including Tx and Rx.
Although this version is changed to work on 32-bit cores, as we can not use or define 64bit integers or the reg_write64 and reg_read64.
................
*//////

int main(void)
{
//val bitString = "b00001111_10100000 11001100_11001110_00110010_10101010_11001100".U//
  uint32_t data_pduHead = 0x0FA0U;  //the reverse of the A indicates the length of the payload (5bytes), length 16 bits
  uint32_t data_pduPay1 = 0xCCCE32AAU;  // CCCE32AA   Payload part 1 ,partial length 32 bits, total length 40 bits
  uint32_t data_pduPay2 = 0xCCU;  // CC   Payload part 2 ,partial length 8 bits, total length 40 bits
 
  uint32_t done = 0;
  uint32_t data;
  uint32_t TX_out;
  uint32_t RX_out;
  
  //__write the package to the TX part__
  
  for (int i = 0; i < 2; i++){
    data = (data_pduHead >> (8-8*i)) & 255;
    printf("input data: Header %#001x \n", data);
    if(i==0){
      reg_write32(PACKET_TX_WRITE, (data<<1)|1 );
      //printf("check pduH1 %#001x \n", (data));
      //printf("check %#001x \n", (data<<1)|0);
    }else{
      reg_write32(PACKET_TX_WRITE, (data<<1)|0 );
      //printf("check pduH2 %#001x \n", (data));
    }
    //printf("unpacked data: Header %#001x \n", data|1);
  }
  for (int i = 0; i < 4; i++){//part 1 of the payload
    data = (data_pduPay1 >> (24-8*i)) & 255;
    printf("input data: Payload %#001x \n", data);
    reg_write32(PACKET_TX_WRITE, (data<<1)|0 );
  }
  for (int i = 0; i < 1; i++){//part 2 of the payload
    data = (data_pduPay2 >> (0-8*i)) & 255;
    printf("input data: Payload %#001x \n", data);
    reg_write32(PACKET_TX_WRITE, (data<<1)|0 );
  }
  
  
  
  //__read the output of the TX part and write it to the receiver part (Connect Tx output with Rx input)__
  
  for (int i = 0; i < 8; i++){ //some empty bits to initialize the rx
    data = 0x0 & 0;
    reg_write32(PACKET_RX_WRITE, data);
  }
  
  while (1) {
    TX_out = reg_read32(PACKET_TX_READ); //read output of Tx
    data = (TX_out >> 1) & 1;
    //printf("*out data: %#002x \n", (PA_out >> 1) & 1 );
    reg_write32(PACKET_RX_WRITE, data); //write the input of the Rx
    if ((TX_out & 1) == 1) {
      printf("Succesfully Dissasembled \n");
      break;
    }
  }
  
  int i = 0;
  while (1) {    //5 for AA y Head, 6 for Pre, AA y Head //i<=9
    
    RX_out = reg_read32(PACKET_RX_READ);
    if((RX_out & 7) == 4){
      printf("-section: Header \n");
    }else if((RX_out & 7) == 2){
      printf("-section: Payload \n");
    }else if((RX_out & 7) == 1){
      printf("-section: CRC \n");
    }
    printf("output data: %#002x \n", RX_out >> 13 );   //>> 13

    //printf("-section: %#002x \n", PDA_out & 7 );    //4=header, 2=payload, 1=CRC
    //printf("+correct: %#002x \n", (PDA_out >> 3) & 3 );
    //printf("Finished disassembling \n");
    i = i + 1;
    if((RX_out >> 3) % 2 == 1){
      if((RX_out >> 4) % 2 == 1){
        printf("AA Valid\n");
      }
      printf("CRC Valid\n");
      printf("Succesfully Assembled \n");
      break;
    }  
  }
  
  return 0;
   
}
