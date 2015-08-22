package org.stormenroute.mecha



import java.io.File
import org.apache.commons.io._
import sbt.{Future => _, _}
import sbt.Keys._
import sbt.complete.DefaultParsers._
import scala.collection._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._



/** Mixed in with the superrepository root project. */
trait MechaSuperBuild extends Build {
  val superName: String
  
  val superDirectory: File
  
  val superSettings: Seq[Setting[_]]
  
  /** File that describes all the repositories in this superrepository.
   *
   *  Format if extension is `json`:
   *  
   *      {
   *        "super-project": {
   *          "dir": ".",
   *          "origin": "<repo-url-at-github>",
   *          "mirrors": ["<repo-url-at-bitbucket>"]
   *        },
   *        "sub-project": {
   *          "dir": "mecha",
   *          "origin": "git@github.com:storm-enroute/mecha.git",
   *          "mirrors": []
   *        }
   *      }
   *
   *  Format if extension is `conf`:
   *
   *      super-project {
   *        dir = "."
   *        origin = "<repo-url-at-github"
   *        mirrors = ["<repo-url-at-bitbucket"]
   *      }
   *      sub-project {
   *        dir = "mecha"
   *        origin = "git@github.com:storm-enroute/mecha.git"
   *        mirrors = []
   *      }
   *  
   *  Override this method to specify a different path to this file.
   */
  def repositoriesFile: File = file("repos.conf")
  
  /** Holds the configuration of repositories in this superrepo.
   */
  val repositories: Map[String, Repo] = {
    ConfigParsers.reposFromJson(repositoriesFile)
  }
  
  override def projects: Seq[Project] = {
    val nothing = Def.task[Unit] {}
    val otherprojects = super.projects
    val subprojects = for {
      (name, repo) <- repositories.toList
      dir = file(repo.dir)
      if dir.exists
      if superDirectory != dir
    } yield {
      RootProject(uri(repo.dir))
    }
    val cleans = for (p <- subprojects) yield (clean in p)
    val nightlies = for (p <- subprojects) yield {
      (mechaNightlyKey in p).or(nothing)
    }
    val refreshes = for (p <- subprojects) yield {
      (mechaEditRefreshKey in p).or(nothing)
    }
    val superproject = subprojects.foldLeft(Project(
      superName,
      superDirectory,
      settings = superSettings ++ Seq(
        mechaEditRefreshKey <<= mechaEditRefreshKey.dependsOn(refreshes: _*),
        mechaNightlyKey <<= mechaNightlyKey.dependsOn(nightlies: _*)
      )
    ))(_ aggregate _)
    otherprojects ++ Seq(superproject)
  }
  
  override def settings = super.settings ++ Seq(
    MechaSuperPlugin.reposKey := repositories
  )

  lazy val defaultMechaSuperSettings = {
    import MechaSuperPlugin._
    Seq(
      trackedReposTask,
      lsTask,
      statusTask,
      diffTask,
      newBranchTask,
      branchTask,
      checkoutTask,
      pullTask,
      pushTask,
      pullMirrorTask,
      pushMirrorTask,
      commitTask,
      mergeTask,
      trackTask,
      mechaEditRefreshKey := {},
      mechaPublishKey := {},
      mechaNightlyKey := {},
      mechaNightlyKey <<= mechaNightlyKey.dependsOn(mechaPublishKey),
      mechaNightlyKey <<= mechaNightlyKey.dependsOn(test in Test),
      mechaNightlyKey <<= mechaNightlyKey.dependsOn(packageBin in Compile)
    )
  }

  // check if initial pull necessary
  if (sys.env.get("MECHA_PULL_ON_INIT") == Some("true")) {
    println("Performing initial pull.")
    val trackedRepos = repositories.filter(p => file(p._2.dir).exists)
    MechaSuperPlugin.ifClean(trackedRepos, MechaLog.Println) {
      // pull from remote branches
      for ((name, repo) <- trackedRepos) {
        println(s"Pull repo '${repo.dir}' from origin...")
        if (!Git.pull(repo.dir, "origin", "master"))
          scala.Console.err.println(s"Pull failed: ${repo.dir}")
      }
    } {
      sys.exit(1)
    }
  } else {
    println("Environment variable MECHA_PULL_ON_INIT not set to true, no initial pull.")
  }

}


/** Added to the root build of the superrepository.
 */
object MechaSuperPlugin extends Plugin {

  class LoggerMechaLog(log: Logger) extends MechaLog {
    def info(s: String) = log.info(s)
    def warn(s: String) = log.warn(s)
    def error(s: String) = log.error(s)
  }

  implicit def logger2MechaLog(log: Logger) = new LoggerMechaLog(log)

  implicit val reader = new MechaReader {
    def readLine(prompt: String) = SimpleReader.readLine(prompt)
  }

  def ifClean(repos: Map[String, Repo], log: MechaLog)(action: =>Unit)
    (errorAction: =>Unit): Unit = {
    val dirtyRepos = repos.filter(p => Git.isDirty(p._2.dir))
    if (dirtyRepos.nonEmpty) {
      for ((name, repo) <- dirtyRepos) {
        log.error(s"Uncommitted changes: ${repo.dir}")
      }
      errorAction
    } else {
      action
    }
  }

  def dirtyRepos(repos: Map[String, Repo]): Map[String,Repo] = {
    repos.filter(p => Git.isDirty(p._2.dir))
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
    "mecha-repos",
    "Information about all the repos."
  )

  val trackedReposKey = SettingKey[Map[String, Repo]](
    "mecha-tracked-repos",
    "Information about the tracked repos."
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
    for ((name, repo) <- reposKey.value.toList.sortBy(_._1)) {
      val tracked = file(repo.dir).exists
      log.info(s"[${if (tracked) "*" else " "}] $name")
      if (args.contains("-v")) {
        log.info(s"  origin: ${repo.origin}")
        for (mirror <- repo.mirrors) { 
          log.info(s"  mirror: $mirror")
        }
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
      val status = Git.status(repo.dir, "--porcelain")
      if (status.trim != "") {
        log.info(s"------ status for repo '${name}' in '${repo.dir}' ------")
        log.info(Git.status(repo.dir))
        log.info(s"------ end of status for repo '${name}' ------")
      }
    }
  }

  val diffKey = TaskKey[Unit](
    "mecha-diff",
    "Shows the diff of the current working tree and HEAD in all repositories."
  )

  val diffTask = diffKey := {
    val log = streams.value.log
    val repos = trackedReposKey.value
    for ((name, repo) <- repos) {
      val d = Git.diff(repo.dir)
      if (d.trim != "") {
        log.info(s"------ diff for repo '$name' in '${repo.dir}' ------")
        log.info(d)
        log.info(s"------ end of diff for repo '$name' in '${repo.dir}' ------")
      }
    }
  }

  val pullKey = TaskKey[Unit](
    "mecha-pull",
    "For every project, pulls the corresponding branch from the origin repository."
  )

  val pullTask = pullKey := {
    val log = streams.value.log
    val repos = trackedReposKey.value

    ifClean(repos, log) {
      // pull from remote branches
      for ((name, repo) <- repos) {
        log.info(s"Pull repo '${repo.dir}' from origin...")
        if (!Git.pull(repo.dir, "origin"))
          log.error(s"Pull failed: ${repo.dir}")
      }
    } {}
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
      val pushes = for ((name, repo) <- repos) yield {
        Repo.push(log, flags, name, repo, "origin")
      }
      Repo.awaitPushes(log, pushes)
    } {}
  }

  val pullMirrorKey = TaskKey[Unit](
    "mecha-pull-mirror",
    "Pulls the current branch from the mirror repositories."
  )

  val pullMirrorTask = pullMirrorKey := {
    val log = streams.value.log
    val repos = trackedReposKey.value
    val dirty = dirtyRepos(repos)
    if (dirty.isEmpty) {
      log.info("Pulling from mirrors...")
      for ((name, repo) <- repos; mirror <- repo.mirrors) {
        log.info(s"Pull '${repo.dir}' from '$mirror'...")
        if (!Git.pull(repo.dir, mirror))
          log.error(s"Pull failed: ${repo.dir}")
      }
    } else {
      log.error(s"Dirty repos: ${dirty.mkString(", ")}")
    }
  }

  val pushMirrorKey = InputKey[Unit](
    "mecha-push-mirror",
    "Pushes the master branches to the mirror repositories."
  )

  val pushMirrorTask = pushMirrorKey := {
    val flags = spaceDelimited("<push flags>").parsed
    val log = streams.value.log
    val repos = trackedReposKey.value
    val dirty = dirtyRepos(repos)
    if (dirty.isEmpty) {
      log.info("Pushing to mirrors...")
      for ((name, repo) <- repos; mirror <- repo.mirrors) {
        log.info(s"Push '${repo.dir}' to '$mirror'...")
        val branch = Git.branchName(repo.dir)
        if (!Git.push(repo.dir, mirror, branch, flags.mkString(" ")))
          log.error(s"Push failed: ${repo.dir}")
      }
    } else {
      log.error(s"Dirty repos: ${dirty.mkString(", ")}")
    }
  }

  val commitKey = TaskKey[Unit](
    "mecha-commit",
    "Stages changes in all the repositories, collects commit messages and commits."
  )

  val commitTask = commitKey := {
    val log = streams.value.log
    val repos = trackedReposKey.value
    for ((name, repo) <- repos) {
      Repo.commit(log, name, repo)
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
        case None => log.error("Please specify a branch name.")
        case Some(name) =>
          ifBranchInAll(repos, log, name) {
            for ((_, repo) <- repos) {
              if (!Git.checkout(repo.dir, name))
                log.error(s"Cannot checkout branch '$name' in '$repo.dir'.")
            }
          }
      }
    } {}
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
              log.error("Aborted due to empty branch name.")
          }
        case _ =>
          log.error("Aborted.")
      }
    } {}
  }

  val branchKey = TaskKey[Unit](
    "mecha-branch",
    "Displays the current working branch for each repository."
  )

  val branchTask = branchKey := {
    val log = streams.value.log
    val repos = trackedReposKey.value
    for ((name, repo) <- repos) {
      val branch = Git.branchName(repo.dir)
      log.info(s"Repo '$name' at directory '${repo.dir}': $branch")
    }
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
            else Some(names.head)
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
          log.error("Aborted.")
      }
    } {}
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
        log.error(s"Project '$arg' does not exist.")
      case Some(repo) =>
        val repodir = new File(s"$dir/${repo.dir}")
        if (repodir.exists) log.warn(s"Project '$arg' already tracked.")
        else try {
          val url = repo.origin
          repodir.mkdir()
          log.info(s"Cloning '$arg' into '$repodir'.")
          if (!Git.clone(url, repo.dir)) sys.error(s"Clone failed.")
          else {
            val gitignoreSample = new File(repodir, ".gitignore-SAMPLE")
            val gitignore = new File(repodir, ".gitignore")
            if (gitignoreSample.exists)
              FileUtils.copyFile(gitignoreSample, gitignore)
            log.warn(s"Please reload the sbt shell.")
          }
        } catch {
          case t: Throwable =>
            log.error(t.getMessage)
            FileUtils.deleteDirectory(repodir)
        }
    }
  }

}
