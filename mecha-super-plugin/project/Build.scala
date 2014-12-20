


import sbt._
import Keys._
import java.io._



object MechaSuperRepoPluginBuild extends Build {

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

  lazy val mechaCore = RootProject(uri("../mecha-core"))

  lazy val mechaSuperRepoPlugin = Project(
    "mecha-super-plugin",
    file("."),
    settings = mechaSuperRepoPluginSettings
  ) dependsOn (
    mechaCore
  )

}
