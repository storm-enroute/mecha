


import sbt._
import Keys._
import java.io._



object MechaCoreBuild extends Build {

  /* mecha */

  def versionFromFile(filename: String): String = {
    val fis = new FileInputStream(filename)
    val props = new java.util.Properties()
    try props.load(fis)
    finally fis.close()

    val major = props.getProperty("mecha_major")
    val minor = props.getProperty("mecha_minor")
    s"$major.$minor"
  }

  val frameworkVersion = baseDirectory { dir =>
    versionFromFile(dir.getParent + File.separator + "version.conf")
  }

  val mechaScalaVersion = "2.10.4"

  val mechaCoreSettings = Defaults.defaultSettings ++ Seq(
    name := "mecha-core",
    scalaVersion := mechaScalaVersion,
    version <<= frameworkVersion,
    organization := "com.storm-enroute"
  )

  lazy val mechaCorePlugin = Project(
    "mecha-core",
    file("."),
    settings = mechaCoreSettings
  )

}
