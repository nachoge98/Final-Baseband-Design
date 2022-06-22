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
  * The Input FIFO for DATA
  */
abstract class InFIFOWriteQueue
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
    val width = 32//before 64

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


class TLInFIFOWriteQueue
(
  depth: Int = 8,//It has to be 8 for 64b, and 4 for 32b
  csrAddress: AddressSet = AddressSet(0x2000, 0xff),
  beatBytes: Int = 8,//It has to be 8 for 64b, and 4 for 32b
)(implicit p: Parameters) extends InFIFOWriteQueue(depth) with TLHasCSR {
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
  * The Input Data for the Controller (also a FIFO)
  */
abstract class InConWriteQueue
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
    val width = 8//I only need 8 bits for the controller input

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


class TLInConWriteQueue
(
  depth: Int = 8,//It has to be 8 for 64b, and 4 for 32b
  csrAddress: AddressSet = AddressSet(0x2100, 0xff),
  beatBytes: Int = 8,//It has to be 8 for 64b, and 4 for 32b
)(implicit p: Parameters) extends InConWriteQueue(depth) with TLHasCSR {
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
  * The Output FIFO for Data
  */
abstract class OutFIFOReadQueue
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
    val width = 32//before 64 :21/02/2022
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


class TLOutFIFOReadQueue
(
  depth: Int = 8,//It has to be 8 for 64b, and 4 for 32b
  csrAddress: AddressSet = AddressSet(0x2200, 0xff),
  beatBytes: Int = 8//It has to be 8 for 64b, and 4 for 32b
)(implicit p: Parameters) extends OutFIFOReadQueue(depth) with TLHasCSR {
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

/**
  * The Output to the Antenna (also a FIFO for simulation)
  */
abstract class OutAntennaReadQueue
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
    val width = 1//before 64
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


class TLOutAntennaReadQueue
(
  depth: Int = 8,//It has to be 8 for 64b, and 4 for 32b
  csrAddress: AddressSet = AddressSet(0x2300, 0xff),
  beatBytes: Int = 8//It has to be 8 for 64b, and 4 for 32b
)(implicit p: Parameters) extends OutAntennaReadQueue(depth) with TLHasCSR {
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

/**
  * The Input from the antenna (also a FIFO)
  */
abstract class InAntennaWriteQueue
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
    val width = 1//I only need 8 bits for the controller input

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


class TLInAntennaWriteQueue
(
  depth: Int = 8,//It has to be 8 for 64b, and 4 for 32b
  csrAddress: AddressSet = AddressSet(0x2400, 0xff),
  beatBytes: Int = 8,//It has to be 8 for 64b, and 4 for 32b
)(implicit p: Parameters) extends InAntennaWriteQueue(depth) with TLHasCSR {
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

abstract class BasebandBlock[D, U, EO, EI, B <: Data] (implicit p: Parameters) extends DspBlock[D, U, EO, EI, B] {
  val streamNode = AXI4StreamIdentityNode()
  val mem = None

  lazy val module = new LazyModuleImp(this) {
    require(streamNode.in.length == 3)//
    require(streamNode.out.length == 2)//

    val in = streamNode.in.head._1
    val con = streamNode.in.head._2
    val inFIFO = streamNode.in.head._3
    val out = streamNode.out.head._1
    val outFIFO = streamNode.out.head._2

    //unpack and pack
    val baseband = Module(new Baseband())
    /*
    Baseband I/O Bundles:
    val in = Flipped(Decoupled(BasebandInputBundle())) //new BasebandInputBundle
    val out = Decoupled(BasebandOutputBundle()) //new BasebandOutputBundle
    val con = Flipped(Decoupled(BasebandControlBundle()))
    val inFIFO = Flipped(Decoupled(BasebandInputFIFOBundle()))
    val outFIFO = Decoupled(BasebandOutputFIFOBundle())
    */
    
    
    baseband.io.in.bits := in.bits.data.asTypeOf(new BasebandInputBundle())
    baseband.io.in.valid := in.valid
    in.ready := baseband.io.in.ready
    
    baseband.io.con.bits := con.bits.data.asTypeOf(new BasebandControlBundle())
    baseband.io.con.valid := con.valid
    con.ready := baseband.io.con.ready
    
    baseband.io.inFIFO.bits := inFIFO.bits.data.asTypeOf(new BasebandInputFIFOBundle())
    baseband.io.inFIFO.valid := inFIFO.valid
    inFIFO.ready := baseband.io.inFIFO.ready

    out.bits.data := baseband.io.out.bits.asUInt()
    out.valid := baseband.io.out.valid
    baseband.io.out.ready := out.ready
    
    outFIFO.bits.data := baseband.io.outFIFO.bits.asUInt()
    outFIFO.valid := baseband.io.outFIFO.valid
    baseband.io.outFIFO.ready := outFIFO.ready
  }
}

class TLBasebandBlock(implicit p: Parameters)extends
  BasebandBlock[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle] with TLDspBlock


class BasebandThing
(
val depthWriteDataFIFO: Int = 70,//32, //the maximum length of the input is 2B Header + 257B Payload.// The data is written 32 bits at a time.
val depthReadDataFIFO: Int = 90,//32, //The output data goes in a 24+8 bits, so
val depthWriteControl : Int = 15//Usually we wont accumulate control signals in the FIFO, but just in case I gave it some length
val depthAntenna : Int = 2136//2048//256//the maximum length of a baseband in Uncoded PHY is 1+4+259+3=267 bytes //edited
)(implicit p: Parameters) extends LazyModule {
  // instantiate lazy modules
  val writeQueueDataFIFO = LazyModule(new TLInFIFOWriteQueue(depthWriteDataFIFO))
  val writeQueueControlFIFO = LazyModule(new TLInConWriteQueue(depthWriteControl))
  val writeQueueAntennaFIFO = LazyModule(new TLInAntennaWriteQueue(depthAntenna))
  val baseband = LazyModule(new TLBasebandBlock())
  val readQueueDataFIFO = LazyModule(new TLOutFIFOReadQueue(depthReadDataFIFO))
  val readQueueAntennaFIFO = LazyModule(new TLOutAntennaReadQueue(depthAntenna))

  // connect streamNodes of queues and cordic
  //writeQueueDataFIFO.streamNode := baseband.streamNode := readQueueDataFIFO.streamNode
  writeQueueDataFIFO.streamNode := baseband.streamNode.in.head._1
  writeQueueControlFIFO.streamNode := baseband.streamNode.in.head._2
  writeQueueAntennaFIFO.streamNode := baseband.streamNode.in.head._3
  
  baseband.streamNode.out.head._1 := readQueueDataFIFO.streamNode
  baseband.streamNode.out.head._2 := readQueueAntennaFIFO.streamNode

  lazy val module = new LazyModuleImp(this)
}
