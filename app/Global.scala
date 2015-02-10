

import controllers.sshd
import jssc.SerialPortList
import org.apache.sshd.SshServer
import play.api._

import scala.collection.mutable.ListBuffer

object Global extends GlobalSettings {
  val sshd = new sshd
  override def onStart(app: Application) {
    Logger.info("**Application has started")
    Logger.info("Serial Port List:::::::::: port count : " +  SerialPortList.getPortNames.length)
    for(port <- SerialPortList.getPortNames){
      Logger.info(port)
    }
    Logger.info("::::::::::::::::::::::::::")

    Logger.info("sshd start.")

    app.mode.toString match{
      case "Prod" => Logger.info("Prod mode.")
      case "Dev" => Logger.info("Dev mode.")
      case "Test" => Logger.info("test mode.")
      case _ => Logger.info("unknown mode.")
    }

    sshd.start
    Logger.info("sshd server Listen: " + sshd.getPort)
  }

  override def onStop(app: Application) {
    Logger.info("**Application shutdown...")
    sshd.stop
  }
}