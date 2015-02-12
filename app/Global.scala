

import com.typesafe.config.ConfigFactory
import controllers.{SshPortManager, sshd}
import jssc.SerialPortList
import play.api._

import scala.collection.JavaConversions._

object Global extends GlobalSettings {
  val sshd = new sshd

  override def onStart(app: Application) {
    Logger.info("**Application has started")
    Logger.info("Serial Port List:::::::::: port count : " + SerialPortList.getPortNames.length)
    for (port <- SerialPortList.getPortNames) {
      Logger.info(port)
    }
    Logger.info("::::::::::::::::::::::::::")

    Logger.info("sshd start.")

    app.mode.toString match {
      case "Prod" => Logger.info("Prod mode.")
      case "Dev" => Logger.info("Dev mode.")
      case "Test" => Logger.info("test mode.")
      case _ => Logger.info("unknown mode.")
    }

    sshd.start
    Logger.info("sshd server Listen: " + sshd.getPort)

    //    Akka.system.scheduler.schedule(5 seconds, 5 seconds){
    //      val ci = Application.ci       //ciを取得したらいつでもwebsocket送れるっぽい
    //      val jsValue = Json.toJson(Map("status" -> 1))
    //      ci.send(new CommandResponse("test", jsValue))
    //    }

    //コンフィグからserial port listを取得

  }

  override def onStop(app: Application) {
    Logger.info("**Application shutdown...")
    sshd.stop
  }
}