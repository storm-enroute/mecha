


import sbt._
import Keys._
import java.io._



object MechaBuild extends Build {

  /* mecha-core */

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
    organization := "com.storm-enroute",
    libraryDependencies ++= Seq(
      "io.spray" %%  "spray-json" % "1.3.1",
      "commons-io" % "commons-io" % "2.4"
    )
  )

  lazy val mechaCore = Project(
    "mecha-core",
    file("mecha-core"),
    settings = mechaCoreSettings
  )

  /* mecha-super-plugin */

  val mechaSuperRepoPluginSettings = Defaults.defaultSettings ++ Seq(
    sbtPlugin := true,
    name := "mecha-super-plugin",
    scalaVersion := mechaScalaVersion,
    version <<= frameworkVersion,
    organization := "com.storm-enroute",
    libraryDependencies ++= Seq(
      "commons-io" % "commons-io" % "2.4"
    )
  )

  lazy val mechaSuperRepoPlugin = Project(
    "mecha-super-plugin",
    file("mecha-super-plugin"),
    settings = mechaSuperRepoPluginSettings
  ) dependsOn (
    mechaCore
  )

}
