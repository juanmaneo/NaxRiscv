package naxriscv

import naxriscv.backend.{BranchContextPlugin, CommitPlugin, RegFilePlugin, RobPlugin}
import naxriscv.compatibility.{MultiPortReadSymplifier, MultiPortWritesSymplifier}
import spinal.core._
import naxriscv.frontend._
import naxriscv.fetch._
import naxriscv.misc.{StaticAddressTranslationParameter, StaticAddressTranslationPlugin}
import naxriscv.execute._
import naxriscv.fetch.FetchCachePlugin
import naxriscv.lsu.{DataCachePlugin, LsuPlugin}
import naxriscv.utilities._
import spinal.lib.eda.bench.Rtl

import scala.collection.mutable.ArrayBuffer

object Config{
  def properties() = {
    NaxDataBase.create()

//    Fetch.RVC.set(true)
//    Fetch.FETCH_DATA_WIDTH.set(64)
//    Fetch.INSTRUCTION_WIDTH.set(32)
//    Frontend.DECODE_COUNT.set(2)
//    Global.COMMIT_COUNT.set(2)
//    ROB.SIZE.set(64)
//    Global.XLEN.set(32)

    Fetch.RVC.set(true)
    Fetch.FETCH_DATA_WIDTH.set(32)
    Fetch.INSTRUCTION_WIDTH.set(32)
    Frontend.DECODE_COUNT.set(1)
    Global.COMMIT_COUNT.set(1)
    ROB.SIZE.set(64)
    Global.XLEN.set(32)
  }

  def plugins(): Seq[Plugin] ={
    val plugins = ArrayBuffer[Plugin]()
    plugins += new DocPlugin()
    plugins += new StaticAddressTranslationPlugin(
      ioRange = _(31 downto 28) === 0x1
    )
    plugins += new FetchPlugin()
    plugins += new FetchAddressTranslationPlugin()
    plugins += new PcPlugin()
    plugins += new FetchCachePlugin(
      cacheSize = 4096*4,
      wayCount = 4,
      injectionAt = 2,
      memDataWidth = Fetch.FETCH_DATA_WIDTH,
      reducedBankWidth = false
    )
    plugins += new AlignerPlugin()
    plugins += new FrontendPlugin()
    plugins += new DecompressorPlugin()
    plugins += new DecoderPlugin()
    plugins += new RfTranslationPlugin()
    plugins += new RfDependencyPlugin()
    plugins += new RfAllocationPlugin(riscv.IntRegFile)
    plugins += new DispatchPlugin(
      slotCount = 32
    )

    plugins += new BranchContextPlugin(
      branchCount = 16
    )
    plugins += new PredictorPlugin()

    plugins += new LsuPlugin(
      lqSize = 16,
      sqSize = 16,
      loadTranslationParameter  = StaticAddressTranslationParameter(rspAt = 1),
      storeTranslationParameter = StaticAddressTranslationParameter(rspAt = 1)
    )
    plugins += new DataCachePlugin(
      memDataWidth = Global.XLEN,
      cacheSize    = 4096*4,
      wayCount     = 4,
      refillCount = 2,
      writebackCount = 2,
      reducedBankWidth = false
    )

    plugins += new ExecutionUnitBase("EU0")
    plugins += new SrcPlugin("EU0")
    plugins += new IntAluPlugin("EU0")
    plugins += new ShiftPlugin("EU0")
    plugins += new BranchPlugin("EU0")
    plugins += new LoadPlugin("EU0")
    plugins += new StorePlugin("EU0")

//    plugins += new ExecutionUnitBase("EU1")
//    plugins += new SrcPlugin("EU1")
//    plugins += new IntAluPlugin("EU1")
//    plugins += new ShiftPlugin("EU1")
//    plugins += new BranchPlugin("EU1")
//    plugins += new LoadPlugin("EU1")
//    plugins += new StorePlugin("EU1")


//    plugins += new ExecutionUnitBase("EU2")
//    plugins += new SrcPlugin("EU2")
//    plugins += new IntAluPlugin("EU2")
//    plugins += new ShiftPlugin("EU2")
//    plugins += new BranchPlugin("EU2")

    plugins += new RobPlugin()
    plugins += new CommitPlugin()
    plugins += new RegFilePlugin(
      spec = riscv.IntRegFile,
      physicalDepth = 64,
      bankCount = 1
    )
    plugins
  }
}

// ramstyle = "MLAB, no_rw_check"
object Gen extends App{
  LutInputs.set(6)
  val spinalConfig = SpinalConfig(inlineRom = true)
  spinalConfig.addTransformationPhase(new MultiPortWritesSymplifier)
//  spinalConfig.addTransformationPhase(new MultiPortReadSymplifier)

//  def wrapper[T <: Component](c : T) = c
  def wrapper[T <: Component](c : T) = { c.afterElaboration(Rtl.ffIo(c)); c }

  val report = spinalConfig.generateVerilog(new Component {
    setDefinitionName("NaxRiscv")
    Config.properties()
    val framework = new Framework(Config.plugins())
  })
  val doc = report.toplevel.framework.getService[DocPlugin]
  doc.genC()

  spinalConfig.generateVerilog(wrapper(new Component {
    setDefinitionName("NaxRiscvSynt")
    Config.properties()
    val framework = new Framework(Config.plugins())
  }))
}

//object GenSim extends App{
//  import spinal.core.sim._
//  SimConfig.withFstWave.compile(new Component {
//    Config.properties()
//    val frontend = new Framework(Config.plugins())
//  }).doSim(seed = 42){}
//}


//ROADMAP
/*
- https://github.com/riscv-non-isa/riscv-arch-test
 */

//TODO Optimisations
/*
- RAS implement healing
- gshare should update the fetch branchHistory himself, instead of the decode stage update workaround
- optimize aligner for non-rvc config
- PredoctorPlugin to Branchplugin context storage optimisation
- LSU getting PC for reschedule
- When a reschedule is pending, stop feeding the pipeline with things which will get trashed anyway
- LSU aliasing prediction, and in general reducing the pessiming nature of checkSq/checkLq
- RobPlugin completion vector could be stored as a distributed ram with single bit flip. This shouild save 200 lut
- DataCache banks write sharing per way, instead of allocating them all between refill and store
- Lsu loadFeedAt is currently set to 1 to relax timings on the d$ banks, maybe those banks can use falling edge clock of stage loadFeedAt+1 stage instead ?
 */

//TODO fix bellow list
/*
- lsu peripheral do not handle rsp.error yet
- lsu load to load with same address ordering in a far future
- Manage the verilator seeds
- having genTests.py generating different seed for each test
- Data cache handle store which had tag hit but the line is currently being written back to the main memory
- Data cache / LSU  need cares about read during writes on tags and data, also, care about refill happening from previous cycle hazarding pipeline
- data cache reduce ram blocks usage clashes by using banks sel
- aligner flush connector from fetches.last stage (workarounded via a extra stage)
- Likely pc management in the aligner need rework, specialy what's about btb impact when buffer pc + 4 != input pc ?
- load to load ordering
- Check lsu memory depedency cross check (store and load with aliasing checking others at the same time)
- data cache flush (init, and others) do not halt incoming requests (should at least schedule a redo)
 */

//ASSUMPTIONS
/*
X0 init =>
- RfTranslationPlugin entries are initialized to all point to physical 0
- RfAllocationPlugin will reject physical 0 freedoom
- RfAllocationPlugin will not init physical 0
- RegFilePlugin will write physical 0 with 0

 */


//stats

/*
checkLq => 6.8slack 550 LUT (16lq/16sq)
 */

/*
obj_dir/VNaxRiscv --name dhrystone --output_dir output/nax/dhrystone --load_elf ../../../../ext/NaxSoftware/baremetal/dhrystone/build/dhrystone.elf --start_symbol _start --pass_symbol pass --fail_symbol fail --stats_print --stats_toggle_symbol sim_time

SUCCESS dhrystone
STATS :
  commits           73612 => 368
  reschedules       8803  => 44
  trap              0
  missprediction    8803
  storeToLoadHazard 0

SUCCESS dhrystone
STATS :
  commits           73612
  reschedules       2641
  trap              0
  missprediction    2641
  storeToLoadHazard 0
  missprediction from :
    800002b4 1
    800002e4 1
    80000318 1
    80000330 1
    8000033c 400
    80000370 2
    8000091c 1
    80000cb8 600
    80000d28 1
    80000d34 1
    80000d3c 400
    80000d70 1
    80000d84 1
    80000da8 1
    80000db4 1
    80000dd8 1
    80000de8 1
    80000dec 1
    80000df4 1
    80000e50 1
    80000e68 1
    80000e84 1
    80000e8c 1
    80000f44 1
    80001028 1
    80001038 1
    800010dc 1
    80001100 1
    80001104 1
    80001120 1
    80001124 1
    80001140 1
    80001144 1
    80001160 1
    80001164 1
    80001180 1
    8000118c 1
    80001198 1
    800011ac 1
    800011b0 1
    80001258 1
    80001268 400
    8000126c 1
    8000131c 1
    80001334 399
    80001348 400
    8000134c 1

 */