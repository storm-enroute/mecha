
import java.io._

val ideExcludedDirectories = SettingKey[Seq[File]]("ide-excluded-directories")

lazy val mecha = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    scriptedLaunchOpts := { scriptedLaunchOpts.value ++
      Seq("-Xmx1024M", "-XX:MaxPermSize=256M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false
  )
  .settings(
    sbtPlugin := true,
    name := "mecha",
    scalaVersion := "2.12.1",
    version := {
      def versionFromFile(filename: String): String = {
        val fis = new FileInputStream(filename)
        val props = new java.util.Properties()
        try props.load(fis)
        finally fis.close()

        val major = props.getProperty("mecha_major")
        val minor = props.getProperty("mecha_minor")
        s"$major.$minor"
      }

      versionFromFile(baseDirectory.value + File.separator + "version.conf")
    },
    organization := "com.storm-enroute",
    libraryDependencies ++= Seq(
      "com.typesafe" % "config" % "1.2.1",
      "commons-io" % "commons-io" % "2.4",
      "com.decodified" %% "scala-ssh" % "0.8.0",
      "com.github.pathikrit" %% "better-files" % "2.17.1",
      //test
      "org.specs2" %% "specs2-core" % "3.8.6" % "test",
      "org.specs2" %% "specs2-junit" % "3.8.6" % "test",
      "junit" % "junit" % "4.12" % "test"
    ),
    ideExcludedDirectories := {
      val base = baseDirectory.value
      List(
        base / ".idea",
        base / "target"
      )
    },
    publishMavenStyle := true,
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    pomExtra := {
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
    }
  )
