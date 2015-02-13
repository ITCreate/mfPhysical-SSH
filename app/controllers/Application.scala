package controllers

import java.io._

import jssc._
import org.apache.sshd.SshServer
import org.apache.sshd.common._
import org.apache.sshd.server._
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.session.ServerSession
import play.api.Logger
import play.api.mvc._
import roomframework.command.CommandInvoker
import utils.{SshUser, WebSocketCommand}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{blocking, Future}

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
//
//class AddCommand extends CommandHandler{
//  def handle(command: roomframework.command.Command): CommandResponse = {
//    val a:Integer = (command.data \ "a").as[Int]
//    val b:Integer = (command.data \ "b").as[Int]
//    command.json(JsNumber(a + b))
//  }
//}

class sshd(){
  val server = SshServer.setUpDefaultServer()
  server.setPort(22220)
  server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider("key.ser"))
  server.setPasswordAuthenticator(new PasswordAuthenticator {
    override def authenticate(p1: String, p2: String, p3: ServerSession): Boolean = {
      Logger.info("auth start [user:" + p1 + "]")
      true
    }
  })
  /*server.setPublickeyAuthenticator(new PublickeyAuthenticator {
    override def authenticate(p1: Nothing, p2: Nothing, p3: ServerSession): Boolean = ???
  });*/
  server.setShellFactory(new CommandFactory)

  def start = {
    server.start
  }
  def stop = {
    server.stop
  }
  def getPort : Integer = {
    server.getPort
  }
}

/**
 * serialPort全体を管理するobject
 */
object SshPortManager{
  var devList = new File("/dev/").listFiles.filter(_.toString.indexOf("USB") > 0).map(_.toString).reverse

  def update:Unit = {
    devList = new File("/dev/").listFiles.filter(_.toString.indexOf("USB") > 0).map(_.toString).reverse
  }
}

class CommandFactory extends Factory[Command] {
  implicit def StringToBytes(s: String): Array[Byte] = {
    s.map(_.toByte).toArray[Byte]
  }
  override def create():Command = {
    new Command() {

      var in: InputStream = null
      var out: OutputStream = null
      var error: OutputStream = null
      var exitCallback: ExitCallback = null
      var endFlag = false
      var select = false
      var lastKeyInput = 0
      var selectSerialDevice = 0

      def setInputStream(inputStream: InputStream) { in = inputStream }
      def setErrorStream(errorStream: OutputStream) { error = errorStream }
      def setOutputStream(outputStream: OutputStream) { out = outputStream }
      def setExitCallback(callback: ExitCallback) { exitCallback = callback }

      def start(env: Environment): Unit = {
        println("Start!! " + env.toString)
        var serial:SerialPort = null
        Future {
          blocking {
            try {
              out.write("Welcome To Physical - SSH\n\r")
              out.flush()
              // TODO シリアルポート選択ここでやりたい
              SshPortManager.devList.foreach(println(_))

              out.write("please select serial ports.\n\r")
              for ((serial, i) <- SshPortManager.devList.zipWithIndex) {
                ("[" + i + "]" + serial + "\n\r").map(_.toByte.toInt).foreach(out.write)
              }
              out.write(":")
              out.flush
              while (!select) {
                if (in.available() > 0) {
                  val read = in.read
                  if (read == 13) {
                    selectSerialDevice = lastKeyInput - '0'
                    if (SshPortManager.devList.length > selectSerialDevice && selectSerialDevice >= 0) {
                      select = true
                    } else {
                      "\n\r:".map(_.toByte.toInt).foreach(out.write)
                    }
                  } else {
                    lastKeyInput = read
                    out.write(read)
                  }
                  out.flush
                }
              }
              serial = new SerialPort(SshPortManager.devList(selectSerialDevice))
              WebSocketCommand.portUpdate(serial, new SshUser("user1", "199.999.999.999"))
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
                  println(read)
                  if (lastKeyInput == 1 && read == 4) // Ctrl+A Ctrl+D
                    endFlag = true;
                  lastKeyInput = read
                  serial.writeByte(read.toByte)
                }
                Thread.sleep(10);
              }
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
          }
        }
      }
      def destroy(): Unit = {
        endFlag = true;
        println("Command End!!")
      }
    }
  }
}