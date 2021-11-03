package spinal.lib.pipeline

import naxriscv.utilities.Misc
import spinal.core._
import spinal.idslplugin.Location

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class Stage extends Nameable {
  val internals = new {
    val input = new {
      val valid = Bool()
      var ready : Bool = null
    }

    val output = new Area {
      val valid = Bool()
      var ready : Bool = null
    }

    //    val will = new {
    //      var haltAsk, removeAsk, flushAsk = false
    //      var haltBe, removeBe, flushBe = false
    //    }

    val arbitration = new {
      var isRemoved : Bool = null
      var isFlushed : Bool = null
      var isFlushingNext : Bool = null
      var isHalted : Bool = null
      var isHaltedByOthers : Bool = null
    }

    val request = new {
      val halts = ArrayBuffer[Bool]()
      val flush = ArrayBuffer[Bool]()
      val flushRoot = ArrayBuffer[Bool]()
      val flushNext = ArrayBuffer[Bool]()
    }



    val stageableToData = mutable.LinkedHashMap[StageableKey, Data]()
    val stageableOverloadedToData = mutable.LinkedHashMap[StageableKey, Data]()

    def outputOf(key : StageableKey) = stageableOverloadedToData.get(key) match {
      case Some(x) => x
      case None => stageableToData(key)
    }
  }

  def nameFromLocation[T <: Data](that : T, prefix : String)(implicit loc: Location) : T ={
    that.setCompositeName(this, prefix + "_" + loc.file + "_l" + loc.line, Nameable.REMOVABLE)
  }

  implicit def stageablePiped[T <: Data](stageable: Stageable[T])(implicit key : StageableOffset = StageableOffsetNone) = Stage.this(stageable, key.value)
  implicit def stageablePiped2[T <: Data](stageable: Stageable[T]) = new {
    def of(key : Any) = Stage.this.apply(stageable, key)
  }
  implicit def stageablePiped3[T <: Data](key: Tuple2[Stageable[T], Any]) = Stage.this(key._1, key._2)
  //  implicit def stageablePiped2[T <: Data](stageable: Stageable[T]) = new DataPimper(Stage.this(stageable))
  def haltIt()(implicit loc: Location) : Unit = haltIt(ConditionalContext.isTrue)
  def flushIt() : Unit = flushIt(ConditionalContext.isTrue)
  def flushNext() : Unit = flushNext(ConditionalContext.isTrue)
  def haltIt(cond : Bool)(implicit loc: Location) : Unit = internals.request.halts += nameFromLocation(CombInit(cond), "haltRequest")
  def flushIt(cond : Bool, root : Boolean = true) : Unit = {
    internals.request.flush += cond
    if(root) internals.request.flushRoot += cond
  }
  def flushNext(cond : Bool) : Unit =  internals.request.flushNext += cond
  def removeIt(): Unit = ???
  def isValid: Bool = internals.input.valid
  def isFireing: Bool = signalCache(this -> "isFireing")(isValid && isReady)
  def isStuck: Bool = isValid && !isReady
  def isRemoved : Bool = {
    if(internals.arbitration.isRemoved == null) internals.arbitration.isRemoved = Misc.outsideCondScope(Bool())
    internals.arbitration.isRemoved
  }
  def isFlushed : Bool = {
    if(internals.arbitration.isFlushed == null) internals.arbitration.isFlushed = Misc.outsideCondScope(Bool())
    internals.arbitration.isFlushed
  }
  def isFlushingNext : Bool = {
    if(internals.arbitration.isFlushingNext == null) internals.arbitration.isFlushingNext = Misc.outsideCondScope(Bool())
    internals.arbitration.isFlushingNext
  }
  def isReady : Bool = {
    if(internals.input.ready == null) internals.input.ready = Misc.outsideCondScope(Bool())
    internals.input.ready
  }

  def valid = internals.input.valid



  def apply(key : StageableKey) : Data = {
    internals.stageableToData.getOrElseUpdate(key, Misc.outsideCondScope{
      key.stageable()
    })
  }
  def overloaded(key : StageableKey) : Data = {
    internals.stageableOverloadedToData.getOrElseUpdate(key, Misc.outsideCondScope{
      key.stageable()
    })
  }

  def apply[T <: Data](key : Stageable[T]) : T = {
    apply(StageableKey(key.asInstanceOf[Stageable[Data]], null)).asInstanceOf[T]
  }
  def apply[T <: Data](key : Stageable[T], key2 : Any) : T = {
    apply(StageableKey(key.asInstanceOf[Stageable[Data]], key2)).asInstanceOf[T]
  }
  def overloaded[T <: Data](key : Stageable[T]) : T = {
    apply(StageableKey(key.asInstanceOf[Stageable[Data]], null)).asInstanceOf[T]
  }


  //  def <<(that : Stage) = {
  //
  //    that
  //  }
  //
  //  def <-<(that : Stage) = {
  //    that
  //  }
  //
  ////  def >>(that : Stage) = {
  ////    that
  ////  }
  //  def >>(that : Stage) = new {
  //    def apply(x : Int) = {
  //
  //    }
  //  }
  //
  //  def >->(that : Stage) = {
  //    that
  //  }
}