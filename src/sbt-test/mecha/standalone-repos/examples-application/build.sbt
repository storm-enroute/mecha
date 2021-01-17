
import scala.sys.process.Process

lazy val `examples-application` = ExamplesApplicationBuild.examplesApplication
  .settings(
    assemblyJarName in assembly := "examples-application.jar",
    TaskKey[Unit]("check") := {
      val process = Process("java", Seq(
        "-jar", (crossTarget.value / (assemblyJarName in assembly).value).toString)
      )
      val out = (process.!!).trim
      if (out != "25") {
        sys.error("unexpected output: " + out)
      }
      ()
    }
  )
