package upmc.akka.leader

import java.util
import java.util.Date

import collection.mutable.Map

import akka.actor._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

abstract class Tick
case class CheckerTick () extends Tick

//case class RingNeigbor(id: Int)

class CheckerActor (val id:Int, val terminaux:List[Terminal], electionActor:ActorRef) extends Actor {

     var time : Int = 200
     val father = context.parent

    //  var nodesAlive:List[Int] = List()
    //  var datesForChecking:List[Date] = List()

     var mapAlivesDates:scala.collection.mutable.Map[Int, Date] = scala.collection.mutable.Map()

     var leader : Int = -1
     var ringSuccessor : Int = -1
    def receive = {

         // Initialisation²
        case Start => {
             self ! CheckerTick
        }

        // A chaque fois qu'on recoit un Beat : on met a jour la liste des nodes
        case IsAlive (nodeId) => {
            mapAlivesDates = mapAlivesDates.updated(nodeId, new Date())
            println("checker is alive : ")
        }

        case IsAliveLeader (nodeId) => {
            leader = nodeId
            mapAlivesDates = mapAlivesDates.updated(leader, new Date())
            println("checker is alive leader : ")
        }

        // A chaque fois qu'on recoit un CheckerTick : on verifie qui est mort ou pas
        // Objectif : lancer l'election si le leader est mort
        case CheckerTick => {
            val now = new Date()
            val deadIds = mapAlivesDates.keySet.filter(k => now.getTime() - mapAlivesDates(k).getTime() <= time);
            
            mapAlivesDates = mapAlivesDates -- deadIds

            if(!mapAlivesDates.isEmpty && !mapAlivesDates.isDefinedAt(ringSuccessor)){
                println("successor not defined")

                val maxId = (mapAlivesDates.keySet).fold(-1) {
                    (z, i) => 
                        math.max(i, z)
                }
                var i = id
                ringSuccessor = - 1
                while (ringSuccessor == -1){
                    println ("looping ring max id : " + i + " max:" + maxId)
                    if (mapAlivesDates.isDefinedAt( (i + 1) % maxId)){
                        ringSuccessor = i + 1
                        println("new ring successor " + ringSuccessor)
                    }
                    i = (i + 1) % maxId   
                }
                electionActor ! RingNeighbor(ringSuccessor)
            }

            if (!mapAlivesDates.isDefinedAt(leader)) {
                // TODO start election
                electionActor ! Start
            }
            context.system.scheduler.scheduleOnce(time milliseconds, self, CheckerTick)
        }
    }
}
