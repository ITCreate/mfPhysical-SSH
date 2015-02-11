

import controllers.{Application, sshd}
import jssc.SerialPortList
import org.apache.sshd.SshServer
import play.api._
import play.api.libs.json.{Json, JsValue}
import play.libs.Akka
import play.mvc.Controller
import roomframework.command.{CommandResponse, CommandInvoker}
import play.api.libs.concurrent.Execution.Implicits._

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationLong

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

//    Akka.system.scheduler.schedule(5 seconds, 5 seconds){
//      val ci = Application.ci
//      val jsValue = Json.toJson(Map("status" -> 1))
//      ci.send(new CommandResponse("test", jsValue))
//    }

  }

  override def onStop(app: Application) {
    Logger.info("**Application shutdown...")
    sshd.stop
  }
}