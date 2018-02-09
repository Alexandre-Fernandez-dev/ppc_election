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
case class Reset()

class ElectionActor (val id:Int, val terminaux:List[Terminal]) extends Actor {
     val father = context.parent
     var nodesAlive:List[Int] = List(id)

     var candSucc:Int = -1
     var candPred:Int = -1
     var neighborId: Int = -1
     var status:NodeStatus = new Passive ()

     def getActor(id: Int) : ActorSelection = {
        val n = terminaux(id)
        context.actorSelection("akka.tcp://LeaderSystem" + n.id + "@" + n.ip + ":" + n.port + "/user/Node/electionActor")
     }

     def receive = {
         case Reset() => {
             println ("RESET")
             status = new Passive()
         }
        
         case RingNeighbor(nId) => {
            //println ("RingNeighbor" + id + "->" + nId)
            neighborId = nId
         }

          // Initialisation
          case Start => {
               println(self.path)
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
                  case Dummy() => {
                      println("Election started dummy")
                  }
                    
                  case _ => {
                      println("Election started candidate")
                      status = new Candidate ()
                      candSucc = -1
                      candPred = -1
                      println (status + " " + id + "-> " + neighborId + ": ALG " + id)
                      getActor(neighborId) ! ALG (nodesAlive, id)
                  }
              }
          }

          case ALG (list, init) => {
              println (status + " " + id + "<- ALG " + init)
              status match {
                  case Passive () => {
                      this.nodesAlive = list
                      neighborId = nodesAlive((nodesAlive.indexOf(id) + 1)%nodesAlive.length)
                      status = new Dummy()
                      println (status + " " + id + "-> " + neighborId + ": ALG " + init)
                      getActor(neighborId) ! ALG (list, init)
                  }
                  case Candidate () => {
                      candPred = init
                    
                      if (id > init) {
                          if(candSucc == -1) {
                            status = new Waiting()
                            println (status + " " + id + "-> " + init + ": AVS " + id)
                            getActor(init) ! AVS (list, id)
                          } else {
                            println (status + " " + id + "-> " + candSucc + ": AVSRP " + candPred)
                            getActor(candSucc) ! AVSRSP(list, candPred)
                            status = new Dummy()
                          }
                      } else if (init == id) {
                          status = new Leader()
                          father ! LeaderChanged(id)
                      }
                  }
                  case _ => ()
              }
          }

          case AVS (list, j) => {
              println (status + " " + id + "<- AVS " + j)
              status match {
                  case Candidate () => {
                      if ( candPred == -1 ) {
                          candSucc = j
                      } else {
                          println (status + " " + id + "-> " + j + ": AVSRP " + candPred)
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
              println (status + " " + id + "<- AVSRP " + k)
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
                                  println (status + " " + id + "-> " + k + ": AVS " + id)
                                  getActor(k) ! AVS(list, id)
                              }
                          } else {
                              status = new Dummy()
                              println (status + " " + id + "-> " + candSucc + ": AVSRP " + k)
                              getActor(candSucc) ! AVSRSP (list, k)
                          }
                      }
                  }
                  case _ => ()
              }
          }
     }
}
