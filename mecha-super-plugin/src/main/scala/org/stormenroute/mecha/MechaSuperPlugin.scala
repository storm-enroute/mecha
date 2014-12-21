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
  val repositories: Map[String, Repo]
  override def projects: Seq[Project] = {
    val otherprojects = super.projects
    val subprojects = for {
      (name, repo) <- repositories
      if superdirectory != file(repo.dir)
    } yield {
      RootProject(file(repo.dir))
    }
    val superproject = subprojects.foldLeft(Project(
      supername,
      superdirectory,
      settings = supersettings
    ))(_ dependsOn _)
    otherprojects ++ Seq(superproject)
  }
  override def settings = super.settings ++ Seq(
    MechaSuperPlugin.reposKey := repositories
  )
}


/** Added to the root build of the superrepository.
 */
object MechaSuperPlugin extends Plugin {

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

  def ifBranchInAll(repos: Map[String, Repo], log: Logger, name: String)(action: =>Unit): Unit = {
    val nonExisting = repos.filterNot(p => Git.branchExists(p._2.dir, name))
    if (nonExisting.nonEmpty) {
      for ((_, repo) <- nonExisting) {
        log.error(s"Branch '$name' does not exist in the '${repo.dir}' repo.")
      }
    } else {
      action
    }
  }

  def ifBranchInNone(repos: Map[String, Repo], log: Logger, name: String)(action: =>Unit): Unit = {
    val existing = repos.filter(p => Git.branchExists(p._2.dir, name))
    if (existing.nonEmpty) {
      for ((_, repo) <- existing) {
        log.error(s"Branch '$name' exists in the '${repo.dir}' repo.")
      }
    } else {
      action
    }
  }

  def checkSingleBranch(repos: Map[String, Repo], log: Logger) {
    val branches = repos.map(p => Git.branchName(p._2.dir)).toSet
    if (branches.size != 1) {
      log.warn(s"Repos have different branches: ${branches.mkString(", ")}")
    } else {
      log.info(s"All repositories are at branch: ${branches.head}")
    }
  }

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

  val statusKey = TaskKey[Unit](
    "mecha-status",
    "Shows the status of the working tree in all repositories."
  )

  val statusTask = statusKey := {
    val log = streams.value.log
    val repos = trackedReposKey.value
    for ((name, repo) <- repos) {
      val status = Git.status(repo.dir)
      log.info(s"Status for '${name}', directory '${repo.dir}':")
      log.info(status)
    }
  }

  val pullKey = TaskKey[Unit](
    "mecha-pull",
    "For every project, pulls the corresponding branch from the origin repository."
  )

  val pullTask = pullKey := {
    // check if repos are clean
    val log = streams.value.log
    val repos = trackedReposKey.value
    ifClean(repos, log) {
      // pull from remote branches
      for ((name, repo) <- repos) {
        log.info(s"Pull repo '${repo.dir}' from origin...")
        if (!Git.pull(repo.dir, "origin"))
          log.error(s"Pull failed: ${repo.dir}")
      }
    }
  }

  val pushKey = InputKey[Unit](
    "mecha-push",
    "For every project, pushes the corresponding branch to the origin repository."
  )

  val pushTask = pushKey := {
    val flags = spaceDelimited("<push flags>").parsed
    val log = streams.value.log
    val repos = trackedReposKey.value
    ifClean(repos, log) {
      // push to remote branches
      for ((name, repo) <- repos) {
        log.info(s"Push '${repo.dir}' to origin...")
        val branch = Git.branchName(repo.dir)
        if (!Git.push(repo.dir, "origin", branch, flags.mkString(" ")))
          log.error(s"Push failed: ${repo.dir}")
      }
    }
  }

  val pullMirrorKey = TaskKey[Unit](
    "mecha-pull-mirror",
    "Pulls the current branch from the mirror repositories."
  )

  val pullMirrorTask = pullMirrorKey := {
    val log = streams.value.log
    val repos = trackedReposKey.value
    ifClean(repos, log) {
      // push to remote branches
      log.info("Pulling from mirrors...")
      for ((name, repo) <- repos; mirror <- repo.mirrors) {
        log.info(s"Pull '${repo.dir}' from '$mirror'...")
        if (!Git.pull(repo.dir, mirror))
          log.error(s"Pull failed: ${repo.dir}")
      }
    }
  }

  val pushMirrorKey = TaskKey[Unit](
    "mecha-push-mirror",
    "Pushes the master branches to the mirror repositories."
  )

  val pushMirrorTask = pushMirrorKey := {
    val log = streams.value.log
    val repos = trackedReposKey.value
    ifClean(repos, log) {
      // push to remote branches
      log.info("Pushing to mirrors...")
      for ((name, repo) <- repos; mirror <- repo.mirrors) {
        log.info(s"Push '${repo.dir}' to '$mirror'...")
        if (!Git.push(repo.dir, mirror))
          log.error(s"Push failed: ${repo.dir}")
      }
    }
  }

  val commitKey = TaskKey[Unit](
    "mecha-commit",
    "Stages changes in all the repositories, collects commit messages and commits."
  )

  val commitTask = commitKey := {
    val log = streams.value.log
    val repos = trackedReposKey.value
    for ((name, repo) <- repos; if Git.isDirty(repo.dir)) {
      if (!Git.addAll(repo.dir)) {
        log.error(s"Could not stage in '${repo.dir}'.")
      } else {
        log.info(s"Diff for '${repo.dir}':")
        log.info(Git.diff(repo.dir))
        SimpleReader.readLine("Commit message (empty aborts): ") match {
          case Some(msg) =>
            if (!Git.commit(repo.dir, msg)) log.error("Could not commit.")
          case None =>
        }
      }
    }
  }

  val checkoutKey = TaskKey[Unit](
    "mecha-checkout",
    "Checks out the specified, existing branch on all the repositories."
  )

  val checkoutTask = checkoutKey := {
    val log = streams.value.log
    val repos = trackedReposKey.value
    ifClean(repos, log) {
      SimpleReader.readLine("Existing branch name: ") match {
        case None => log.error("Need to specify a branch name.")
        case Some(name) =>
          ifBranchInAll(repos, log, name) {
            for ((name, repo) <- repos) {
              if (!Git.checkout(repo.dir, name))
                log.error("Cannot checkout branch '$name' in repo '$repo.dir'.")
            }
          }
      }
    }
  }

  val newBranchKey = InputKey[Unit](
    "mecha-new-branch",
    "Creates and checks out new non-existing branch on all repositories."
  )

  val newBranchTask = newBranchKey := {
    val log = streams.value.log
    val repos = trackedReposKey.value
    val names = spaceDelimited("<branch name>").parsed
    if (names.length > 1) log.error("Please specify a single new branch name.")
    else ifClean(repos, log) {
      checkSingleBranch(repos, log)
      SimpleReader.readLine("Branch in all repos? [y/n] ") match {
        case Some("y") =>
          val name = {
            if (names.length == 0)
              SimpleReader.readLine("New branch name (empty aborts): ")
            else Some(names.head)
          }
          name.map(_.trim) match {
            case Some(name) if name != "" =>
              ifBranchInNone(repos, log, name) {
                for ((_, repo) <- repos) {
                  if (!Git.newBranch(repo.dir, name))
                    log.error(s"Cannot create branch '$name' in '${repo.dir}'.")
                }
              }
            case _ =>
              log.error("Aborted, empty branch name.")
          }
        case _ =>
          log.error("Aborted.")
      }
    }
  }

  val branchKey = TaskKey[Unit](
    "mecha-branch",
    "Displays the current working branch for each repository."
  )

  val branchTask = branchKey := {
    // TODO
  }

  val mergeKey = InputKey[Unit](
    "mecha-merge",
    "Merges the target branch into the current branch."
  )

  val mergeTask = mergeKey := {
    val log = streams.value.log
    val repos = trackedReposKey.value
    val names = spaceDelimited("<branch name>").parsed
    if (names.length > 1) log.error("Please specify a single existing branch.")
    else ifClean(repos, log) {
      checkSingleBranch(repos, log)
      SimpleReader.readLine("Merge in all repos? [y/n]") match {
        case Some("y") =>
          val name = {
            if (names.length == 0)
              SimpleReader.readLine("Existing branch name (empty aborts): ")
            else Some(name.head)
          }
          name.map(_.trim) match {
            case Some(name) if name != "" =>
              ifBranchInAll(repos, log, name) {
                for ((_, repo) <- repos) {
                  if (!Git.merge(repo.dir, name))
                    log.error(s"Cannot merge from '$name' in '${repo.dir}'.")
                }
              }
            case _ =>
              log.error("Aborted, empty branch name.")
          }
        case _ =>
          log.error("Aborted")
      }
    }
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
    statusTask,
    newBranchTask,
    branchTask,
    checkoutTask,
    pullTask,
    pushTask,
    pullMirrorTask,
    pushMirrorTask,
    commitTask,
    publishTask,
    trackTask,
    testTask
  )

}
