
lazy val `examples-application` = ExamplesApplicationBuild.examplesApplication
  .settings(
    assemblyJarName in assembly := "examples-application.jar",
    TaskKey[Unit]("check") := {
      val process = sbt.Process("java", Seq(
        "-jar", (crossTarget.value / (assemblyJarName in assembly).value).toString)
      )
      val out = (process!!).trim
      if (out != "125") {
        sys.error("unexpected output: " + out)
      }
      ()
    }
  )
