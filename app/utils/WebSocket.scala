package utils

import controllers.Application
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
  def portUpdate(port:SerialPort ,user: SshUser): Unit = {
    val jsonPort:JsValue = Json.toJson(Map("id" -> "0", "name" -> port.getPortName))
    val jsonUser:JsValue = Json.toJson(Map("id" -> "0", "name" -> user.name, "ip" -> user.ip))
    val jsonRoot:JsValue = Json.toJson(Map("port" -> jsonPort, "user" -> jsonUser))
    ci.send(new CommandResponse("portUpdate", jsonRoot))
  }
  def serialConnected: CommandResponse = {
    null
  }
  def serialDisConnected: CommandResponse = {
    null
  }
}

class SshUser(val name:String, val ip:String){

}
