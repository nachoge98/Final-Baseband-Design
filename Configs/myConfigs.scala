package chipyard

import freechips.rocketchip.config.{Config}
import freechips.rocketchip.diplomacy.{AsynchronousCrossing}

// --------------
// made by Ignacio Garcia Ezquerro
// nachoge98@gmail.com
// edited copy of RocketConfigs.scala
//in the path: /home/ignacio/Desktop/thesis/chipyard/generators/rocket-chip/src/main/scala/subsystem/Configs.scala ; I added the modified scratchpad
// version to contain the designs with the final baseband design itself.

//the config files and crap for the arty are in the directory:
// ~/chipyard/fpga/src/main/scala/arty/
// --------------
/*
class myBasebandTinyRocketConfig extends Config(
  new chipyard.config.WithTLSerialLocation(
    freechips.rocketchip.subsystem.FBUS,
    freechips.rocketchip.subsystem.PBUS) ++                       // attach TL serial adapter to f/p busses
  new chipyard.WithMulticlockIncoherentBusTopology ++             // use incoherent bus topology
  new freechips.rocketchip.subsystem.WithNBanks(0) ++             // remove L2$
  new freechips.rocketchip.subsystem.WithNoMemPort ++             // remove backing memory
  new freechips.rocketchip.subsystem.With1TinyCore ++             // single tiny rocket-core
  new chipyard.config.AbstractConfig)
*/
class myBasebandRocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++         // single rocket-core
  new chipyard.config.AbstractConfig)
  
class myBasebandErrorsRocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++         // single rocket-core, compiled including the errors FIFO instead of the normal one.
  new chipyard.config.AbstractConfig)
  
/*
class myModifiedTinyRocketConfig extends Config(
  new chipyard.config.WithTLSerialLocation(
    freechips.rocketchip.subsystem.FBUS,
    freechips.rocketchip.subsystem.PBUS) ++                       // attach TL serial adapter to f/p busses
  new chipyard.WithMulticlockIncoherentBusTopology ++             // use incoherent bus topology
  new freechips.rocketchip.subsystem.WithNBanks(0) ++             // remove L2$
  new freechips.rocketchip.subsystem.WithNoMemPort ++             // remove backing memory
  //new freechips.rocketchip.subsystem.WithModifiedScratchPad ++    // changing scratchpad to 1 Mb
  new freechips.rocketchip.subsystem.WithMyTinyCore ++             // single modified tiny rocket-core (1Mb DCache)
  new chipyard.config.AbstractConfig)
  
class myRocketConfig extends Config(
  //new chipyard.config.WithTLSerialLocation(
  //  freechips.rocketchip.subsystem.FBUS,
  //  freechips.rocketchip.subsystem.PBUS) ++                       // attach TL serial adapter to f/p busses
  //new chipyard.WithMulticlockIncoherentBusTopology ++             // use incoherent bus topology
  //new freechips.rocketchip.subsystem.WithNBanks(0) ++             // remove L2$
  //new freechips.rocketchip.subsystem.WithNoMemPort ++             // remove backing memory
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++         // single rocket-core
  new chipyard.config.AbstractConfig)
  
class myBigRocketConfig extends Config(
  //new chipyard.config.WithTLSerialLocation(
  //  freechips.rocketchip.subsystem.FBUS,
  //  freechips.rocketchip.subsystem.PBUS) ++                       // attach TL serial adapter to f/p busses
  //new chipyard.WithMulticlockIncoherentBusTopology ++             // use incoherent bus topology
  //new freechips.rocketchip.subsystem.WithNBanks(0) ++             // remove L2$
  //new freechips.rocketchip.subsystem.WithNoMemPort ++  
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++         // single rocket-core
  new chipyard.config.AbstractConfig)

class myMMIORocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithDefaultMMIOPort ++  // add default external master port
  new freechips.rocketchip.subsystem.WithDefaultSlavePort ++ // add default external slave port
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++         // single rocket-core
  new chipyard.config.AbstractConfig)
  
class myRV32RocketConfig extends Config( //error: requirement failed: Xbar (out_xbar with parent Some(LazyScope named subsystem_pbus)) data widths don't match: List(writeQueue) has 8B vs List(subsystem_pbus) has 4B //im going to try to change my conexions to 4Bytes instead of 8Bytes
  new freechips.rocketchip.subsystem.WithRV32 ++            // set RocketTiles to be 32-bit
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)
  
class myTinyRocketConfig2 extends Config(
  //new chipyard.config.WithTLSerialLocation(
  //  freechips.rocketchip.subsystem.FBUS,
  //  freechips.rocketchip.subsystem.PBUS) ++                       // attach TL serial adapter to f/p busses
  //new chipyard.WithMulticlockIncoherentBusTopology ++             // use incoherent bus topology
  //new freechips.rocketchip.subsystem.WithNBanks(0) ++             // remove L2$
  //new freechips.rocketchip.subsystem.WithNoMemPort ++             // remove backing memory
  //new testchipip.WithRingSystemBus ++ // Ring-topology system bus
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=1, nWays=4, capacityKB=128) ++
  new freechips.rocketchip.subsystem.With1TinyCore ++             // single tiny rocket-core
  new chipyard.config.AbstractConfig)
  
class myOriginalTinyRocketConfig extends Config(
  new chipyard.config.WithTLSerialLocation(
    freechips.rocketchip.subsystem.FBUS,
    freechips.rocketchip.subsystem.PBUS) ++                       // attach TL serial adapter to f/p busses
  new chipyard.WithMulticlockIncoherentBusTopology ++             // use incoherent bus topology
  new freechips.rocketchip.subsystem.WithNBanks(0) ++             // remove L2$
  new freechips.rocketchip.subsystem.WithNoMemPort ++             // remove backing memory
  new freechips.rocketchip.subsystem.With1TinyCore ++             // single tiny rocket-core
  new chipyard.config.AbstractConfig)
  
class mySmallRocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithNmySmallCores(1) ++             // single small rocket-core
  new chipyard.config.AbstractConfig)
*/
