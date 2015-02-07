

import org.apache.sshd.SshServer
import play.api._

import scala.collection.mutable.ListBuffer

object Global extends GlobalSettings {
  override def onStart(app: Application) {
    println("**Application has started")
  }

  override def onStop(app: Application) {
    println("**Application shutdown...")
    controllers.sshCollection.allStop()
  }
}