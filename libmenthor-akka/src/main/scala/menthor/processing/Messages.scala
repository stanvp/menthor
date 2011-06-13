package menthor.processing

import menthor.io.DataIO

import akka.actor.Uuid

sealed abstract class InternalMessage extends Serializable

sealed abstract class ControlMessage extends InternalMessage

object Stop {
  def apply(postInfo: Map[Uuid,Int]) = new Stop(postInfo.toArray)
  def unapply(msg: Stop): Option[Map[Uuid,Int]] =
    if (msg eq null) None
    else Some(Map(msg.postInfo: _*))
}

class Stop(val postInfo: Array[(Uuid,Int)]) extends ControlMessage

object Next {
  def apply(postInfo: Map[Uuid,Int]) = new Next(postInfo.toArray)
  def unapply(msg: Next): Option[Map[Uuid,Int]] =
    if (msg eq null) None
    else Some(Map(msg.postInfo: _*))
}

class Next(val postInfo: Array[(Uuid,Int)]) extends ControlMessage

object WorkerStatusMessage {
  def aggregatePostInfo(i1: Array[(Uuid,Int)], i2: Array[(Uuid,Int)]) = {
    (List(i1: _*) ::: List(i2: _*)).groupBy(_._1).mapValues(_.foldLeft(0)(_ + _._2)).toArray
  }

  def reduceStatusMessage(ctrl1: WorkerStatusMessage, ctrl2: WorkerStatusMessage) = (ctrl1, ctrl2) match {
    case (d1: Done, d2: Done) => new Done(aggregatePostInfo(d1.postInfo, d2.postInfo))
    case (d: Done, h: Halt) => new Done(aggregatePostInfo(d.postInfo,h.postInfo))
    case (h: Halt, d: Done) => new Done(aggregatePostInfo(h.postInfo,d.postInfo))
    case (h1: Halt, h2: Halt) => new Halt(aggregatePostInfo(h1.postInfo,h2.postInfo))
  }
}

sealed abstract class WorkerStatusMessage extends InternalMessage

object Done {
  def apply(postInfo: Map[Uuid,Int]) = new Done(postInfo.toArray)
  def unapply(msg: Done): Option[Map[Uuid,Int]] =
    if (msg eq null) None
    else Some(Map(msg.postInfo: _*))
}

class Done(val postInfo: Array[(Uuid,Int)]) extends WorkerStatusMessage

object Halt {
  def apply(postInfo: Map[Uuid,Int]) = new Halt(postInfo.toArray)
  def unapply(msg: Halt): Option[Map[Uuid,Int]] =
    if (msg eq null) None
    else Some(Map(msg.postInfo: _*))
}

class Halt(val postInfo: Array[(Uuid,Int)]) extends WorkerStatusMessage

sealed abstract class CrunchMessage extends InternalMessage

object Crunch {
  def reduceCrunch[Data](crunch1: Crunch[Data], crunch2: Crunch[Data]) = {
    if (crunch1.cruncher(crunch1.result, crunch2.result) != crunch2.cruncher(crunch1.result, crunch2.result))
      throw new InvalidStepException("Different crunchers are used in the same step")
    try {
      Crunch(crunch1.cruncher, crunch1.cruncher(crunch1.result, crunch2.result))
    } catch {
      case e => throw new ProcessingException("Cruncher application error", e)
    }
  }
}

case class Crunch[Data](cruncher: (Data, Data) => Data, result: Data) extends CrunchMessage

case class CrunchResult[Data](result: Data) extends CrunchMessage

sealed abstract class DataMessage

case class Message[Data](dest: VertexRef, value: Data)(
  implicit val source: VertexRef,
  implicit val step: Superstep
) extends DataMessage

case class TransmitMessage[Data](dest: Uuid, value: Data, source: (Uuid, Uuid), step: Superstep) extends DataMessage

sealed abstract class SetupMessage extends Serializable

object SetupDone {
  def apply(workers: Set[Uuid]) = new SetupDone(workers.toArray)
  def unapply(msg: SetupDone): Option[Set[Uuid]] =
    if (msg eq null) None
    else Some(Set(msg.workers: _*))
}

class SetupDone(val workers: Array[Uuid]) extends SetupMessage

class CreateVertices[Data](val source: DataIO[Data])(
  implicit val manifest: Manifest[Data]
) extends SetupMessage

object CreateVertices {
  def apply[Data: Manifest](source: DataIO[Data]) =
    new CreateVertices(source)

  def unapply[Data: Manifest](msg: CreateVertices[_]): Option[DataIO[Data]] = {
    if ((msg eq null) || (msg.manifest != manifest[Data])) None
    else Some(msg.asInstanceOf[CreateVertices[Data]].source)
  }
}

case object VerticesCreated extends SetupMessage

case object ShareVertices extends SetupMessage

case object VerticesShared extends SetupMessage

case class RequestVertexRef[VertexID](vid: VertexID)(
  implicit val manifest: Manifest[VertexID]
) extends SetupMessage

case class VertexRefForID[VertexID](vid: VertexID, vertexUuid: Uuid, workerUuid: Uuid)(
  implicit val manifest: Manifest[VertexID]
) extends SetupMessage
