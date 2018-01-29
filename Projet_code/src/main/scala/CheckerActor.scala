package upmc.akka.leader

import java.util
import java.util.Date

import collection.mutable._

import akka.actor._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

abstract class Tick
case class CheckerTick () extends Tick

class CheckerActor (val id:Int, val terminaux:List[Terminal], electionActor:ActorRef) extends Actor {

     var time : Int = 200
     val father = context.parent

     var nodesAlive:List[Int] = List()
     var datesForChecking:List[Date] = List()

     var mapAlivesDates:Map[Int,Date] = Map()

     var leader : Int = -1

    def receive = {

         // Initialisation²
        case Start => {
             self ! CheckerTick
        }

        // A chaque fois qu'on recoit un Beat : on met a jour la liste des nodes
        case IsAlive (nodeId) => {
            mapAlivesDates.updated(nodeId, Date())
        }

        case IsAliveLeader (nodeId) => {
            leader = nodeId
            mapAlivesDates.updated(leader, Date())
        }

        // A chaque fois qu'on recoit un CheckerTick : on verifie qui est mort ou pas
        // Objectif : lancer l'election si le leader est mort
        case CheckerTick => {
            val now = Date()
            datesForChecking = datesForChecking
              .filterKeys(k => now - datesForChecking(k) > time);

            if (!datesForChecking.isDefinedAt(leader)) {
                // TODO start election
            }
            system.scheduler.scheduleOnce(time, self, CheckerTick)
        }

    }


}
