package controllers

import java.io._

import jssc._
import org.apache.sshd.SshServer
import org.apache.sshd.common.SessionListener.Event
import org.apache.sshd.common._
import org.apache.sshd.server._
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.session.ServerSession
import play.api.Logger
import play.api.mvc._
import roomframework.command.CommandInvoker
import utils.WebSocketCommand

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, blocking}

object Application extends Controller {
  val ci:CommandInvoker = new CommandInvoker()
  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def webSocket = WebSocket.using[String] { _ =>
    val ci = this.ci
    ci.addHandler("hello"){ command =>
      val msg = command.data.as[String]
      command.text("Hello"+msg)
    }
//    ci.addHandler("add", new AddCommand())
    (ci.in, ci.out)
  }
}

class sshd(){
  val server = SshServer.setUpDefaultServer()
  server.setPort(22220)
  server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider("key.ser"))
  server.setPasswordAuthenticator(new PasswordAuthenticator {
    override def authenticate(username: String, p2: String, session: ServerSession): Boolean = {
      Logger.info("auth start [user:" + username + "]")
      Logger.info(session.getId.toString)
      SshUserManger.addUser(username)
    }
  })
  server.setShellFactory(new CommandFactory)

  def start = {
    server.start
    server.getSessionFactory.addListener(new SessionListener {
      override def sessionEvent(session: org.apache.sshd.common.Session, event: Event): Unit = {}

      override def sessionCreated(session: org.apache.sshd.common.Session): Unit = {}
      override def sessionClosed(session: org.apache.sshd.common.Session): Unit = {
        val username = session.getUsername
        Logger.info("logout " + ":"+  username)
        if(username != null){
          WebSocketCommand.portDisconnected(username, SshUserManger.usePort(username))
          SshUserManger.removeUser(username)
        }
      }
    })
  }
  def stop = {
    server.stop
  }
  def getPort : Integer = {
    server.getPort
  }
}

object SshUserManger{
  //[username, useportId]
  val userMap:mutable.Map[String, Integer] = mutable.Map.empty

  def addUser(user: String):Boolean = {
    if(userMap.get(user).isEmpty){
      userMap.put(user, -1)
      true
    }else{
      false
    }
  }

  def removeUser(user: String):Unit = {
    userMap.remove(user)
  }

  def usePort(user: String, port: Integer): Unit = {
    userMap(user) = port
  }

  def usePort(user: String): Integer = {
    userMap(user)
  }
}

/**
 * serialPort全体を管理するobject
 */
object SerialPortManager{
  var devList:Array[String] = null
  var useList:mutable.Map[Int, Boolean] = mutable.Map.empty

  def init: Unit ={
    update
    for((e, i) <- devList.zipWithIndex){
      useList.put(i, false)
    }
  }

  def update:Unit = {
    devList = new File("/dev/").listFiles.filter(_.toString.indexOf("USB") > 0).map(_.toString).sorted
  }
  def isUse(i: Integer): Boolean = {
    val option = useList.get(i)
    option match{
      case Some(x) => x
      case None => false
    }
  }
  def use(i:Integer): Unit = {
    useList.put(i, true)
  }

  def deuse(i: Integer): Unit = {
    useList.put(i, false)
  }
  init
}

class CommandFactory extends Factory[Command] {
  implicit def StringToBytes(s: String): Array[Byte] = {
    s.map(_.toByte).toArray[Byte]
  }
  override def create():Command = {
    Logger.info("start user-------------------------")
    Logger.debug("userList")
    SshUserManger.userMap.foreach{ case(user, port) =>
      Logger.debug(user + ":" + port)
    }
    new Command() {

      var in: InputStream = null
      var out: OutputStream = null
      var error: OutputStream = null
      var exitCallback: ExitCallback = null
      var endFlag = false
      var select = false
      var lastKeyInput = 0
      var useDeviceIndex = -1

      def setInputStream(inputStream: InputStream) { in = inputStream }
      def setErrorStream(errorStream: OutputStream) { error = errorStream }
      def setOutputStream(outputStream: OutputStream) { out = outputStream }
      def setExitCallback(callback: ExitCallback) { exitCallback = callback }

      def start(env: Environment): Unit = {
        var serial:SerialPort = null
        Future {
          blocking {
            try {
              out.write("Welcome To Physical - SSH\n\r")
              env.getEnv.toMap.foreach{case (k, v) => Logger.debug(k + ":" + v)}
              out.flush()
              // TODO シリアルポート選択ここでやりたい
              SerialPortManager.devList.foreach(Logger.info(_))

              out.write("please select serial ports.\n\r")
              for ((serial, i) <- SerialPortManager.devList.zipWithIndex) {
                val useText = if(SerialPortManager.isUse(i)){"used"}else{"free"}
                out.write("[" + i + "]" + serial + "\t\t" + useText +  "\n\r")
              }
              out.write(":")
              out.flush
              while (!select) {
                if (in.available() > 0) {
                  val read = in.read

                  read match {
                    case 3 |4 => //Ctrl + C, Ctrl + D 接続終了
                      out.write("bye. bye.")
                      exitCallback.onExit(0)
                    case 13 => //選択したdevice処理
                      useDeviceIndex = lastKeyInput - '0'
                      if (SerialPortManager.devList.length > useDeviceIndex && useDeviceIndex >= 0) {
                        if(SerialPortManager.isUse(useDeviceIndex)){
                          out.write("\r\nThis port already being used.\r\n:")
                          lastKeyInput = -1
                        }else{
                          select = true
                        }
                      } else {
                        out.write("\n\r:")
                      }
                    case 127 => //BackSpace
                      out.write("\n\r:")
                    case _ =>
                      lastKeyInput = read
                      out.write(read)
                  }
                  out.flush
                }
              }

              //シリアル接続
              val serialDeviceName = SerialPortManager.devList(useDeviceIndex)
              serial = new SerialPort(serialDeviceName)
              SerialPortManager.use(useDeviceIndex)
              SshUserManger.usePort(env.getEnv()("USER"), useDeviceIndex)
              WebSocketCommand.portConnected(useDeviceIndex, serialDeviceName, env.getEnv()("USER"))


              serial.openPort()
              serial.setParams(SerialPort.BAUDRATE_9600,
                SerialPort.DATABITS_8,
                SerialPort.STOPBITS_1,
                SerialPort.PARITY_NONE)

              serial.writeByte("13".toByte) //選んだ時にmessageを流す
              out.write("\n\r\n\rExit: Ctrl+A Ctrl+D Enter\n\r")
              // main loop
              while (!endFlag) {
                if (serial.getInputBufferBytesCount > 0) {
                  out.write(serial.readBytes())
                  out.flush()
                }
                if (in.available() > 0) {
                  var read = in.read()
//                  println(read)
                  if (lastKeyInput == 1 && read == 4) // Ctrl+A Ctrl+D
                    endFlag = true;
                  lastKeyInput = read
                  serial.writeByte(read.toByte)
                }
                Thread.sleep(10);
              }
              out.write("\n\rbye bye.\n\r")
              serial.closePort()
            } catch {

              // error
              case x: Exception => {
                out.flush()
                ("Error:" + x.getMessage).getBytes().map(_.toInt).foreach(out.write)
                Thread.sleep(4000)
              }
                serial.closePort()
            }
            in.close()
            out.close()
            exitCallback.onExit(0)
          }
        }
      }
      def destroy(): Unit = {
        endFlag = true;
        SerialPortManager.deuse(useDeviceIndex)
        Logger.info("Command End!!")
      }
    }
  }
}