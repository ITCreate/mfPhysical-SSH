package utils

import controllers.{SerialPortManager, SshUserManger, Application}
import jssc.SerialPort
import play.api.libs.json.{Json, JsValue}
import roomframework.command.{CommandResponseType, CommandResponse}

/**
 * Created by Takahiro Morimoto on 15/02/11.
 */


class WebSocket {

}

object WebSocketCommand {
  val ci = Application.ci
  def portConnected(portId: Integer, portName: String ,user: String): Unit = {
    val jsonPort:JsValue = Json.toJson(Map("id" -> portId.toString, "name" -> portName))
    val jsonUser:JsValue = Json.toJson(Map("name" -> user))
    val jsonRoot:JsValue = Json.toJson(Map("port" -> jsonPort, "user" -> jsonUser))
    ci.send(new CommandResponse("portConnected", jsonRoot))
  }
  def portDisconnected(user:String, portId:Integer): Unit = {
    val jsonPort:JsValue = Json.toJson(Map("id" -> portId.toString))
    val jsonUser:JsValue = Json.toJson(Map("name" -> user))
    val jsonRoot:JsValue = Json.toJson(Map("port" -> jsonPort, "user" -> jsonUser))
    ci.send(new CommandResponse("portDisconnect", jsonRoot))
  }

  def portUpdate:Unit = {
    val jsonPort:JsValue = Json.toJson(for((devName, id) <- SerialPortManager.devList.zipWithIndex) yield (
        (Map("id" -> id.toString, "name" -> devName, "use" -> SerialPortManager.isUse(id).toString))
      ))
    val jsonUser:JsValue = Json.toJson(for((user, usePort) <- SshUserManger.userMap) yield (
        Map("user" -> user, "usePort" -> usePort.toString)
      ))
    val jsonRoot:JsValue = Json.toJson(Map("port" -> jsonPort, "user" -> jsonUser))
    ci.send(new CommandResponse("portUpdate", jsonRoot))
  }
}
