package org.stormenroute.mecha



import sbt._
import Keys._
import complete.DefaultParsers._
import java.io.File
import scala.collection._
import org.apache.commons.io._



/** Mixed in with the superrepository root project. */
trait MechaSuperBuild extends Build {
  val supername: String
  val superdirectory: File
  val supersettings: Seq[Setting[_]]
  val subrepositories: Map[String, Repo]
  override def projects: Seq[Project] = {
    val otherprojects = super.projects
    val subprojects = for ((name, _) <- subrepositories) yield {
      RootProject(file(name))
    }
    val superproject = subprojects.foldLeft(Project(
      supername,
      superdirectory,
      settings = supersettings
    ))(_ dependsOn _)
    otherprojects ++ Seq(superproject)
  }
  override def settings = super.settings ++ Seq(
    MechaSuperPlugin.reposKey := subrepositories
  )
}


/** Added to the root build of the superrepository.
 */
object MechaSuperPlugin extends Plugin {

  val reposKey = SettingKey[Map[String, Repo]](
    "mecha-repos", "Information about all the repos."
  )

  val trackedReposKey = SettingKey[Map[String, Repo]](
    "mecha-tracked-repos", "Information about the tracked repos."
  )

  val trackedReposTask = trackedReposKey := {
    reposKey.value.filter(p => file(p._2.dir).exists)
  }

  val lsKey = InputKey[Unit](
    "mecha-ls",
    "Lists all the repositories specified in the configuration."
  )

  val lsTask = lsKey := {
    val args = spaceDelimited("").parsed
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
        log.info(s"  dependencies: ${repo.dependencies.mkString(", ")}")
      }
    }
  }

  val pullKey = TaskKey[Unit](
    "mecha-pull",
    "For every project, pulls the corresponding branch from the origin repository."
  )

  def ifClean(repos: Map[String, Repo], log: Logger)(action: =>Unit): Unit = {
    val dirtyRepos = repos.filter(p => Git.isDirty(p._2.dir))
    if (dirtyRepos.nonEmpty) {
      for ((name, repo) <- dirtyRepos) {
        log.error(s"Uncommitted changes: ${repo.dir}")
      }
    } else {
      action
    }
  }

  val pullTask = pullKey := {
    // check if repos are clean
    val log = streams.value.log
    val repos = trackedReposKey.value
    ifClean(repos, log) {
      // pull from remote branches
      for ((name, repo) <- repos) {
        log.info(s"Pull repo: ${repo.dir}")
        if (!Git.pull(repo.dir)) log.error(s"Pull failed: ${repo.dir}")
      }
    }
  }

  val pushKey = TaskKey[Unit](
    "mecha-push",
    "For every project, pushes the corresponding branch to the origin repository."
  )

  val pushTask = pushKey := {
    val log = streams.value.log
    val repos = trackedReposKey.value
    ifClean(repos, log) {
      // push to remote branches
      for ((name, repo) <- repos) {
        log.info(s"Push repo: ${repo.dir}")
        if (!Git.push(repo.dir)) log.error(s"Push failed: ${repo.dir}")
      }
    }
  }

  val pullMirrorKey = TaskKey[Unit](
    "mecha-pull-mirror",
    "Pulls the current branch from the mirror repositories."
  )

  val pullMirrorTask = pullMirrorKey := {
    ???
  }

  val pushMirrorKey = TaskKey[Unit](
    "mecha-push-mirror",
    "Pushes the master branches to the mirror repositories."
  )

  val pushMirrorTask = pushMirrorKey := {
    ???
  }

  val publishKey = TaskKey[Unit](
    "mecha-publish",
    "Publishes the master branches of all repositories."
  )

  val publishTask = publishKey := {
    ???
  }

  val trackKey = InputKey[Unit](
    "mecha-track",
    "Tracks a repository."
  )

  val trackTask = trackKey := {
    val args = spaceDelimited(s"${reposKey.value.keys.mkString(", ")}").parsed
    val log = streams.value.log
    val dir = baseDirectory.value
    for (arg <- args) reposKey.value.get(arg) match {
      case None =>
        log.error(s"Project $arg does not exist.")
      case Some(repo) =>
        val repodir = new File(s"$dir/$arg")
        if (repodir.exists) log.warn(s"Project $arg already tracked.")
        else try {
          val url = repo.origin
          repodir.mkdir()
          if (Git.clone(url, arg)) sys.error(s"Clone failed.")
          else {
            val gitignoreSample = new File(repodir, ".gitignore-SAMPLE")
            val gitignore = new File(repodir, ".gitignore")
            FileUtils.copyFile(gitignoreSample, gitignore)
            log.info(s"Please reload.")
          }
        } catch {
          case t: Throwable =>
            log.error(t.getMessage)
            FileUtils.deleteDirectory(repodir)
        }
    }
  }

  val testKey = TaskKey[Unit](
    "mecha-test",
    "Runs the main tests for all the repositories."
  )

  val testTask = testKey := {
    ???
  }

  override val projectSettings = Seq(
    trackedReposTask,
    lsTask,
    pullMirrorTask,
    pullTask,
    pushMirrorTask,
    pushTask,
    publishTask,
    trackTask,
    testTask
  )

}
