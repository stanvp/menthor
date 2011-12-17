package menthor.processing

import scala.collection.mutable.HashMap

import scala.actors.{Actor, TIMEOUT}
import Actor._

/*
 * @param graph      the graph for which the worker manages a partition of vertices
 * @param partition  the list of vertices that this worker manages
 */
class Worker[Data](parent: Actor, partition: List[Vertex[Data]], global: Graph[Data]) extends Actor {
  var numSubsteps = 0
  var step = 0

  var id = Graph.nextId

  var incoming = new HashMap[Vertex[Data], List[Message[Data]]]() {
    override def default(v: Vertex[Data]) = List()
  }

  def superstep() {
    // remove all application-level messages from mailbox
    var done = false
    while (!done) {
      receiveWithin(0) {
        case msg: Message[Data] if (msg.step == step) =>
          incoming(msg.dest) = msg :: incoming(msg.dest)
        case TIMEOUT =>
          done = true
      }
    }

    step += 1 // beginning of next superstep
    var allOutgoing: List[Message[Data]] = List()
    var crunch: Option[Crunch[Data]] = None

    def executeNextStep() {
      val substep = partition(0).currentStep
      //println("#substeps = " + substep.size)

      // check whether the next substep of vertex 0 is a crunch step
      // assume that all vertices have crunch step at this point
      if (substep.isInstanceOf[CrunchStep[Data]]) {
        val fun = substep.asInstanceOf[CrunchStep[Data]].cruncher
        // compute aggregated value
        val vertexValues = partition.map(v => v.value)
        val crunchResult = vertexValues.reduceLeft(fun)
        crunch = Some(Crunch(fun, crunchResult))
      } else {
        crunch = None

        // check if substep is enabled
        // step is enabled only if condition is _false_
        if (!substep.cond.isEmpty && substep.cond.get()) {
          // not enabled, move all vertices to next substep
          for (vertex <- partition)
            vertex.moveToNextStep()
          executeNextStep()
        } else {
          // iterate over all vertices that this worker manages
          for (vertex <- partition) {
            // compute outgoing messages using application-level `update` method
            // and forward to parent
            vertex.superstep = step - 1
            // TODO: can we get rid of this assignment?
            vertex.incoming = incoming(vertex)

            val outgoing = vertex.currentStep.stepfun()
            // set step field of outgoing messages to current step
            for (out <- outgoing) out.step = step
            allOutgoing = allOutgoing ::: outgoing
          }

          // evaluate the termination condition
          //if (vertex == parent.vertices(0) && parent.cond())
          if (global.cond()) { // TODO: check!!
            //println(this + ": sending Stop to " + parent)
            parent ! "Stop"
            exit()
          }
        }
      } // not a crunch step

      // move to next substep
      for (vertex <- partition) vertex.moveToNextStep()
    }
    executeNextStep()

    incoming = new HashMap[Vertex[Data], List[Message[Data]]]() {
      override def default(v: Vertex[Data]) = List()
    }

    if (crunch.isEmpty) {
/*
      parent ! "Done" // parent checks for "Stop" message first
      react {
        case "Outgoing" => // message from parent: deliver outgoing messages!
*/
          for (out <- allOutgoing) {
            if (out.dest.worker == this) { // mention in paper
              incoming(out.dest) = out :: incoming(out.dest)
            } else
              out.dest.worker ! out
          }
          parent ! "Done" // parent checks for "Stop" message first
          //parent ! "DoneOutgoing"
//      }
    } else
      parent ! crunch.get
  }

  def act() {
/*    react {
      case "Init" =>
        for (v <- partition) { v.initialize() }
*/
        loop {
          react {
            case "Next" => // TODO: make it a class
              //println(this + ": received Next")
              superstep()

            case CrunchResult(res: Data) =>
              //println(this + ": received CrunchResult")
              // deliver as incoming message to all vertices
              for (vertex <- partition) {
                val msg = Message[Data](null, vertex, res)
                msg.step = step
                this ! msg
              }
            // immediately start new superstep (explain in paper)
            superstep()

            case "Stop" =>
              exit()
          }
        }
    //}
  } // def act()

}
