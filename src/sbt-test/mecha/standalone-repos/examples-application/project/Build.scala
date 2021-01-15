
import org.stormenroute.mecha._
import sbt.Keys._
import sbt._

object ExamplesApplicationBuild extends MechaProjectBuild {

  lazy val examplesApplicationSettings = Defaults.coreDefaultSettings ++
    MechaRepoPlugin.defaultSettings ++ Seq(
    name := "examples-application",
    scalaVersion := "2.12.1",
    version := "0.1.0-SNAPSHOT",
    organization := "com.storm-enroute",

    // include dependencies as usual
    libraryDependencies ++= runtimeDependencies.value,

    // AT THE END of settings
    // exclude dependencies based on super-repo, if any
    libraryDependencies --= excludeSuperRepoDependencies.value
  )

  // put super-repo projects dependencies here
  val superRepoProjectsDependencies: Seq[(String, String, Option[String])] = Seq(
    // (repo, project, configuration)
    ("examples-core-utils", "examples-core-utils", None)
  )

  // you can put ALL usual project dependencies here
  val runtimeDependencies: Def.Initialize[Seq[ModuleID]] = Def.setting(Seq(
    Def.setting("com.storm-enroute" %% "examples-core-utils" % "0.1.0-SNAPSHOT").value,
    Def.setting("org.scalatest" %% "scalatest" % "3.0.1" % "test").value
  ))

  val repoName = "examples-application"

  lazy val examplesApplication: Project = Project(
    "examples-application",
    file(".")
  ).settings(
    examplesApplicationSettings
  ).dependsOnSuperRepo
}
