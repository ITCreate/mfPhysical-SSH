

import controllers.sshd
import play.api._
import play.libs.Akka
import utils.WebSocketCommand

import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global


object Global extends GlobalSettings {
  val sshd = new sshd

  override def onStart(app: Application) {
    Logger.info("**Application has started")
    Logger.info("sshd start.")

    app.mode.toString match {
      case "Prod" => Logger.info("Prod mode.")
      case "Dev" => Logger.info("Dev mode.")
      case "Test" => Logger.info("test mode.")
      case _ => Logger.info("unknown mode.")
    }

    sshd.start
    Logger.info("sshd server Listen: " + sshd.getPort)

        Akka.system.scheduler.schedule(5 seconds, 10 seconds){
          WebSocketCommand.portUpdate
        }


  }

  override def onStop(app: Application) {
    Logger.info("**Application shutdown...")
    sshd.stop
  }
}