


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
    versionFromFile(dir + File.separator + "version.conf")
  }

  val mechaScalaVersion = "2.10.4"

  val mechaSettings = Defaults.defaultSettings ++ Seq(
    sbtPlugin := true,
    name := "mecha",
    scalaVersion := mechaScalaVersion,
    version <<= frameworkVersion,
    organization := "com.storm-enroute",
    libraryDependencies ++= Seq(
      "com.typesafe" % "config" % "1.2.1",
      "commons-io" % "commons-io" % "2.4",
      "com.decodified" %% "scala-ssh" % "0.7.0",
      "org.specs2" %% "specs2-core" % "3.7.2" % "test",
      "org.specs2" %% "specs2-junit" % "3.7.2" % "test",
      "junit" % "junit" % "4.12" % "test"
    ),
    publishMavenStyle := true,
    publishTo <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    pomExtra :=
      <url>http://storm-enroute.com/</url>
      <licenses>
        <license>
          <name>BSD-style</name>
          <url>http://opensource.org/licenses/BSD-3-Clause</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:storm-enroute/mecha.git</url>
        <connection>scm:git:git@github.com:storm-enroute/mecha.git</connection>
      </scm>
      <developers>
        <developer>
          <id>axel22</id>
          <name>Aleksandar Prokopec</name>
          <url>http://axel22.github.com/</url>
        </developer>
      </developers>
  )

  lazy val mecha = Project(
    "mecha",
    file("."),
    settings = mechaSettings
  )

}
