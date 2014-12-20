package org.stormenroute.mecha



import sbt._
import Keys._
import complete.DefaultParsers._
import java.io.File
import scala.sys.process._
import scala.sys.process.{Process => Proc}
import org.apache.commons.io._



/** Added to the root project of the superrepository.
 */
object MechaSuperRepoPlugin extends Plugin {

  val reposKey = SettingKey[Map[String, Repo]](
    "mecha-repos", "Contains information about all the repos."
  )

  val lsKey = InputKey[Unit](
    "mecha-ls", "Lists all the repositories specified in the configuration."
  )

  val lsTask = lsKey := {
    val args = spaceDelimited("<arg>").parsed
    val log = streams.value.log
    log.info("Superproject repositories:")
    // list all repositories
    for ((name, repo) <- reposKey.value) {
      log.info(s"$name")
      if (args.contains("-v")) {
        log.info(s"  origin: ${repo.origin}")
        for (mirror <- repo.mirrors) { 
          log.info(s"  mirror: $mirror")
        }
        log.info(s"dependencies: ${repo.dependencies.mkString(", ")}")
      }
    }
  }

  val collectKey = TaskKey[Unit](
    "mecha-collect", "Pulls the master branches from the mirror repositories."
  )

  val collectTask = collectKey := {
    ???
  }

  val pullKey = TaskKey[Unit](
    "mecha-pull", "Pulls the master branches from the origin repository."
  )

  val pullTask = pullKey := {
    ???
  }

  val spreadKey = TaskKey[Unit](
    "mecha-spread", "Pushes the master branches to the mirror repositories."
  )

  val spreadTask = spreadKey := {
    ???
  }

  val pushKey = TaskKey[Unit](
    "mecha-push", "Pushes the master branches to the origin repository."
  )

  val pushTask = pushKey := {
    ???
  }

  val publishKey = TaskKey[Unit](
    "mecha-publish", "Publishes the master branches of all repositories."
  )

  val publishTask = publishKey := {
    ???
  }

  val trackKey = InputKey[Unit](
    "mecha-track", "Tracks a repository."
  )

  val trackTask = trackKey := {
    val args = spaceDelimited(s"${reposKey.value.keys.mkString(", ")}").parsed
    val log = streams.value.log
    val dir = baseDirectory.value
    for (arg <- args) reposKey.value.get(arg) match {
      case None => log.error(s"Project $arg does not exist.")
      case Some(repo) =>
        val repodir = new File(s"$dir/$repo")
        if (repodir.exists) log.warn(s"Project $arg already tracked.")
        else try {
          val url = repo.origin
          repodir.mkdir()
          Proc(s"git clone $url .", Some(repodir)).exec()
        } catch {
          case e: Throwable => FileUtils.deleteDirectory(repodir)
        }
    }
  }

  val testKey = TaskKey[Unit](
    "mecha-test", "Runs the main tests for all the repositories."
  )

  val testTask = testKey := {
    ???
  }

  override val projectSettings = Seq(
    lsTask,
    collectTask,
    pullTask,
    spreadTask,
    pushTask,
    publishTask,
    trackTask,
    testTask
  )

}


/** Added to each repository inside the superrepository.
 */
object MechaRepoPlugin extends Plugin {

  override val projectSettings = Seq(
  )

}
