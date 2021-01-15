
import org.stormenroute.mecha._
import sbt.Keys._
import sbt._
import scala.sys.process.Process

object MechaSuperRepoBuild extends MechaSuperBuild {

  def definition(): Project = superProject

  lazy val mechaSuperRepoSettings = defaultMechaSuperSettings ++ Seq(
    name := superName,
    scalaVersion := "2.12.1",
    version := "0.1.0-SNAPSHOT",
    organization := "com.storm-enroute",
    libraryDependencies ++= Seq(),

    TaskKey[Unit]("checkApp") := {
      val superRepoDir = {
        val path = file(".").getAbsoluteFile.getParentFile.getAbsolutePath
        if (path.startsWith("/")) path
        else s"/$path"
      }

      val proj = s"""project {file:$superRepoDir/examples-application/}"""

      val pluginVersion = sys.props("plugin.version")
      println(s"pluginVersion: $pluginVersion")

      val process = Process(
        Seq("sbt", s"""-Dplugin.version=$pluginVersion""", proj, "assembly", "check"),
        file(".")
      )
      val exitCode = (process.!<)
      if (exitCode != 0) {
        sys.error(s"Nonzero exit value: $exitCode")
      }
      ()
    },

    TaskKey[Unit]("checkAppDirectly") := {
      val pluginVersion = sys.props("plugin.version")
      println(s"pluginVersion: $pluginVersion")

      val process = Process(
        Seq("sbt", s"""-Dplugin.version=$pluginVersion""", "clean", "assembly", "check"),
        file("examples-application")
      )
      val exitCode = (process.!<)
      if (exitCode != 0) {
        sys.error(s"Nonzero exit value: $exitCode")
      }
      ()
    }
  )
   
  override val superName = "mecha-super-repo"
  override val superSettings = mechaSuperRepoSettings
}
