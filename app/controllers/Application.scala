package controllers

import java.io._

import org.apache.sshd.SshServer
import org.apache.sshd.common._
import org.apache.sshd.server.{Command, PasswordAuthenticator}
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.session.ServerSession
import org.apache.sshd.server._
import play.api.mvc._

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global // TODO 検証
import scala.concurrent.Future
import jssc._

object Application extends Controller {

  def index = Action {
    val test = new ssh()
    Ok(views.html.index("Your new application is ready."))
  }

}

object sshCollection{
  val list = ListBuffer.empty[SshServer]
  def allStop() {
    list.foreach(x => {
      x.stop()
    })
    list.clear()
  }
}


class ssh(){
  val server = SshServer.setUpDefaultServer()
  server.setPort(2222)
  println(s"Listen: ${server.getPort}")
  server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider("key.ser"))
  server.setPasswordAuthenticator(new PasswordAuthenticator {
    override def authenticate(p1: String, p2: String, p3: ServerSession): Boolean = {
      true
    }
  })
  /*server.setPublickeyAuthenticator(new PublickeyAuthenticator {
    override def authenticate(p1: Nothing, p2: Nothing, p3: ServerSession): Boolean = ???
  });*/

  server.setShellFactory(new CommandFactory)
  sshCollection.list += server
  server.start();
}

class CommandFactory extends Factory[Command] {

  override def create():Command = {
    new Command() {

      var in: InputStream = null
      var out: OutputStream = null
      var error: OutputStream = null
      var exitCallback: ExitCallback = null
      var endFlag = false
      var lastKeyInput = 0

      def setInputStream(inputStream: InputStream) { in = inputStream }
      def setErrorStream(errorStream: OutputStream) { error = errorStream }
      def setOutputStream(outputStream: OutputStream) { out = outputStream }
      def setExitCallback(callback: ExitCallback) { exitCallback = callback }

      def start(env: Environment): Unit = {
        println("Start!! " + env.toString)
        Future {
          val serial = new SerialPort("/dev/ttyUSB0")
          try {
            serial.openPort()
            serial.setParams(SerialPort.BAUDRATE_9600,
              SerialPort.DATABITS_8,
              SerialPort.STOPBITS_1,
              SerialPort.PARITY_NONE)

            "Welcome To Swans\n\rExit: Ctrl+A Ctrl+D Enter\n\r\n\r".getBytes().map(_.toInt).foreach(out.write)
            out.flush()

            // TODO シリアルポート選択ここでやりたい

            // main loop
            while (!endFlag) {
              if(serial.getInputBufferBytesCount>0){
                out.write(serial.readBytes())
                out.flush()
              }
              if (in.available() > 0) {
                var read = in.read()
                println(read)
                if(lastKeyInput == 1 && read == 4) // Ctrl+A Ctrl+D
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
              ("Error:" + x.getMessage).getBytes().map(_.toInt).foreach(out.write)
              out.flush()
              Thread.sleep(4000)
            }
          }
          in.close()
          out.close()
        }
      }
      def destroy(): Unit = {
        endFlag = true;
        println("Command End!!")
      }
    }
  }
}