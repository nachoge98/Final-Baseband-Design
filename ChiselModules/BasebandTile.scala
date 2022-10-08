package chipyard.baseband

import chisel3._
import chisel3.util._
import dspblocks._
import dsptools.numbers._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem._

//import chipyard.baseband._
//import chipyard.baseband

/**
  * The memory interface writes entries into the queue.
  * They stream out the streaming interface.
  */
abstract class WriteQueue
(
  val depth: Int = 8,
  val streamParameters: AXI4StreamMasterParameters = AXI4StreamMasterParameters()
)(implicit p: Parameters) extends LazyModule with HasCSR {
  // stream node, output only
  val streamNode = AXI4StreamMasterNode(streamParameters)

  lazy val module = new LazyModuleImp(this) {
    require(streamNode.out.length == 1)

    // get the output bundle associated with the AXI4Stream node
    val out = streamNode.out(0)._1

    // width (in bits) of the output interface
    val width = 32 //64

    // instantiate a queue
    val queue = Module(new Queue(UInt(width.W), depth))

    // connect queue output to streaming output
    out.valid := queue.io.deq.valid
    out.bits.data := queue.io.deq.bits

    // don't use last
    out.bits.last := false.B
    queue.io.deq.ready := out.ready

    regmap(
      // each write adds an entry to the queue
      0x0 -> Seq(RegField.w(width, queue.io.enq)),
      // read the number of entries in the queue
      (width+7)/8 -> Seq(RegField.r(width, queue.io.count)),
    )
  }
}


class TLWriteQueue
(
  depth: Int = 4,//for 32bit 4, for 64 bit 8.
  csrAddress: AddressSet = AddressSet(0x2000, 0xff),
  beatBytes: Int = 4//for 32bit 4, for 64 bit 8.
)(implicit p: Parameters) extends WriteQueue(depth) with TLHasCSR {
  val devname = "tlQueueIn"
  val devcompat = Seq("ucb-art", "dsptools")
  val device = new SimpleDevice(devname, devcompat) {
    override def describe(resources: ResourceBindings): Description = {
      val Description(name, mapping) = super.describe(resources)
      Description(name, mapping)
    }
  }
  // make diplomatic TL node for regmap
  override val mem = Some(TLRegisterNode(address = Seq(csrAddress), device = device, beatBytes = beatBytes))
}


/**
  * The streaming interface adds elements into the queue.
  * The memory interface can read elements out of the queue.
  */
abstract class ReadQueue
(
  val depth: Int = 8,
  val streamParameters: AXI4StreamSlaveParameters = AXI4StreamSlaveParameters()
)(implicit p: Parameters)extends LazyModule with HasCSR {
  val streamNode = AXI4StreamSlaveNode(streamParameters)

  lazy val module = new LazyModuleImp(this) {
    require(streamNode.in.length == 1)


    // get the input bundle associated with the AXI4Stream node
    val in = streamNode.in(0)._1
    // width (in bits) of the input interface
    val width = 32
    // instantiate a queue
    val queue = Module(new Queue(UInt(width.W), depth))
    // connect queue output to streaming output


    queue.io.enq.valid := in.valid
    queue.io.enq.bits := in.bits.data
    in.ready := queue.io.enq.ready
    // don't use last
    //in.bits.last := false.B


    regmap(
      // each read cuts an entry from the queue
      0x0 -> Seq(RegField.r(width, queue.io.deq)),
      // read the number of entries in the queue
      (width+7)/8 -> Seq(RegField.r(width, queue.io.count)),
    )
  }
}


class TLReadQueue
(
  depth: Int = 4,//for 32bit 4, for 64 bit 8.
  csrAddress: AddressSet = AddressSet(0x2100, 0xff),
  beatBytes: Int = 4//for 32bit 4, for 64 bit 8.
)(implicit p: Parameters) extends ReadQueue(depth) with TLHasCSR {
  val devname = "tlQueueOut"
  val devcompat = Seq("ucb-art", "dsptools")
  val device = new SimpleDevice(devname, devcompat) {
    override def describe(resources: ResourceBindings): Description = {
      val Description(name, mapping) = super.describe(resources)
      Description(name, mapping)
    }
  }
  // make diplomatic TL node for regmap
  override val mem = Some(TLRegisterNode(address = Seq(csrAddress), device = device, beatBytes = beatBytes))

}


abstract class BasebandBlock[D, U, EO, EI, B <: Data] (implicit p: Parameters) extends DspBlock[D, U, EO, EI, B] {
  val streamNode = AXI4StreamIdentityNode()
  val mem = None

  lazy val module = new LazyModuleImp(this) {
    require(streamNode.in.length == 1)
    require(streamNode.out.length == 1)

    val in = streamNode.in.head._1
    val out = streamNode.out.head._1

    //unpack and pack
    val designedBaseband = Module(new Baseband())
    val fifo = Module(new FIFO_errors()) //fifo hardware chisel block.
    
    designedBaseband.io.inFIFO.bits := in.bits.data.asTypeOf(new BasebandInputFIFOBundle())
    designedBaseband.io.inFIFO.valid := in.valid
    in.ready := designedBaseband.io.inFIFO.ready

    out.valid := designedBaseband.io.outFIFO.valid
    designedBaseband.io.outFIFO.ready := out.ready

    out.bits.data := designedBaseband.io.outFIFO.bits.asUInt()
    
    //fifo connection between the out and the in of the antenna.
    fifo.io.in.bits.data := designedBaseband.io.out.data.bits
    fifo.io.in.valid := designedBaseband.io.out.data.valid
    designedBaseband.io.out.data.ready := fifo.io.in.ready
    
    designedBaseband.io.in.data.bits := fifo.io.out.bits.data
    designedBaseband.io.in.data.valid := fifo.io.out.valid
    fifo.io.out.ready := designedBaseband.io.in.data.ready
    
  }
}

class TLBasebandBlock(implicit p: Parameters)extends
  BasebandBlock[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle] with TLDspBlock


class BasebandThing
(
val depthWriteDataFIFO: Int = 100,//32, //the maximum length of the input is 2B Header + 257B Payload.// The data is written 24 bits at a time, +8b of control
val depthReadDataFIFO: Int = 90//32, //The output data goes in a 24+8 bits, so
)(implicit p: Parameters) extends LazyModule {
  // instantiate lazy modules
  val writeQueue = LazyModule(new TLWriteQueue(depthWriteDataFIFO))
  val bb = LazyModule(new TLBasebandBlock())
  val readQueue = LazyModule(new TLReadQueue(depthReadDataFIFO))

  // connect streamNodes of queues and cordic
  readQueue.streamNode := bb.streamNode := writeQueue.streamNode

  lazy val module = new LazyModuleImp(this)
}
