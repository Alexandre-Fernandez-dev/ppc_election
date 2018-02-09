package upmc.akka.leader

import akka.actor._



abstract class NodeStatus
case class Passive () extends NodeStatus
case class Candidate () extends NodeStatus
case class Dummy () extends NodeStatus
case class Waiting () extends NodeStatus
case class Leader () extends NodeStatus

abstract class LeaderAlgoMessage
case class Initiate () extends LeaderAlgoMessage
case class ALG (list:List[Int], nodeId:Int) extends LeaderAlgoMessage
case class AVS (list:List[Int], nodeId:Int) extends LeaderAlgoMessage
case class AVSRSP (list:List[Int], nodeId:Int) extends LeaderAlgoMessage




case class StartWithNodeList (list:List[Int])

class ElectionActor (val id:Int, val terminaux:List[Terminal]) extends Actor {

     val father = context.parent
     var nodesAlive:List[Int] = List(id)


     var candSucc:Int = -1
     var candPred:Int = -1
     var neighborId: Int = -1
     var status:NodeStatus = new Passive ()

     def getActor(id: Int) : ActorSelection = {
        val n = terminaux(neighborId)
        context.actorSelection("akka.tcp://LeaderSystem" + n.id + "@" + n.ip + ":" + n.port + "/user/Node")
     }

     def receive = {
        
         case RingNeighbor(nId) => {
            println ("RingNeighbor" + id + "->" + nId)
            neighborId = nId
         }

          // Initialisation
          case Start => {
               self ! Initiate
          }

          case StartWithNodeList (list) => {
               if (list.isEmpty) {
                    this.nodesAlive = this.nodesAlive:::List(id)
               }
               else {
                    this.nodesAlive = list
               }

               // Debut de l'algorithme d'election
               self ! Initiate
          }

          case Initiate => {
              status match {
                  case Dummy() => ()
                  case _ => {
                      status = new Candidate ()
                      candSucc = -1
                      candPred = -1
                      getActor(neighborId) ! ALG (List(), id)
                  }
              }
          }

          case ALG (list, init) => {
              status match {
                  case Passive () => {
                      status = new Dummy()
                      getActor(neighborId) ! ALG (list, init)
                  }
                  case Candidate () => {
                      candPred = init
                      if (id > init) {
                          status = new Waiting()
                          getActor(candPred) ! AVS (list, id)
                      } else if (candSucc != -1) {
                          getActor(candSucc) ! AVSRSP(list, candPred)
                          status = new Dummy()
                      }

                  }
                  case _ => ()
              }
          }

          case AVS (list, j) => {
              status match {
                  case Candidate () => {
                      if ( candPred == -1 ) {
                          candSucc = j
                      } else {
                          getActor(j) ! AVSRSP ( list, candPred )
                          status = new Dummy ()
                      }
                  }

                  case Waiting () => {
                      candSucc = j
                  }
                  case _ => ()
              }
          }

          case AVSRSP (list, k) => {
              status match {
                  case Waiting () => {
                      if(id == k) {
                          status = new Leader()
                          father ! LeaderChanged(id)
                      } else {
                          candPred = k
                          if ( candSucc == -1 ) {
                              if (k < id) {
                                  status = new Waiting()
                                  getActor(k) ! AVS(list, id)
                              }
                          } else {
                              status = new Dummy()
                              getActor(candSucc) ! AVSRSP (list, k)
                          }
                      }
                  }
                  case _ => ()
              }
          }
     }
}
