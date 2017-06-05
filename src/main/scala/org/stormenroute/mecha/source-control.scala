package org.stormenroute.mecha



import com.typesafe.config._
import java.io._
import org.apache.commons.io._
import sbt.{Future => _, Process => _, ProcessLogger => _, _}
import sbt.Keys._
import scala.annotation._
import scala.collection._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.sys.process._



/** Utility methods for working with Git. */
object Git {
  def clone(url: String, path: String): Boolean = {
    val dir = new File(path)
    Process(Seq("git", "clone", "--progress", url, "."), dir).! == 0
  }
  def fetchAll(path: String): Boolean = {
    val dir = new File(path)
    Process(Seq("git", "fetch", "--all"), dir).! == 0
  }
  def isDirty(path: String): Boolean = {
    isUncommitted(path) || isUnstaged(path)
  }
  def isUncommitted(path: String): Boolean = {
    val dir = new File(path)
    Process(Seq("git", "diff-index", "--quiet", "--cached", "HEAD"), dir).! != 0
  }
  def isUnstaged(path: String): Boolean = {
    val dir = new File(path)
    Process(Seq("git", "diff-files", "--quiet"), dir).! != 0
  }
  def remoteUrl(path: String, remoteName: String): String = {
    val dir = new File(path)
    Process(Seq("git", "config", "--get", s"remote.$remoteName.url"), dir).!!.trim
  }
  def pull(
    path: String, location: String, branch: String = "",
    logger: ProcessLogger = PrintlnLogger
  ): Boolean = {
    val dir = new File(path)
    val cmd = Seq("git", "pull", location, branch).filter(_ != "")
    Process(cmd, dir).!(logger) == 0
  }
  def push(
    path: String, location: String, branch: String = "",
    flags: Seq[String] = Seq(), logger: ProcessLogger = PrintlnLogger
  ): Boolean = {
    val dir = new File(path)
    val cmd = Seq("git", "push") ++ flags ++ Seq(location, branch).filter(_ != "")
    Process(cmd, dir).!(logger) == 0
  }
  def forcePush(
    path: String, location: String, branch: String = "",
    logger: ProcessLogger = PrintlnLogger
  ): Boolean = {
    push(path, location, branch, Seq("--force"), logger)
  }
  def addAll(path: String): Boolean = {
    val dir = new File(path)
    Process(Seq("git", "add", "-A"), dir).! == 0
  }
  def diff(path: String): String = {
    val dir = new File(path)
    Process(Seq("git", "diff", "--color", "HEAD"), dir).!!
  }
  def commit(path: String, msg: String): Boolean = {
    val dir = new File(path)
    Process(Seq("git", "commit", "-m", msg), dir).! == 0
  }
  def amendCommit(path: String, msg: String): Boolean = {
    val dir = new File(path)
    Process(Seq("git", "commit", "--amend", "--quiet", "-m", msg), dir).! == 0
  }
  def branchExists(path: String, name: String): Boolean = {
    val dir = new File(path)
    Process(
      Seq("git", "show-ref", "--verify", "--quiet", s"refs/heads/$name"),
      dir
    ).! == 0
  }
  def branchName(path: String): String = {
    val dir = new File(path)
    Process(Seq("git", "rev-parse", "--abbrev-ref", "HEAD"), dir).!!.trim
  }
  def checkout(path: String, name: String): Boolean = {
    val dir = new File(path)
    Process(Seq("git", "checkout", name), dir).! == 0
  }
  def newBranch(path: String, name: String): Boolean = {
    val dir = new File(path)
    Process(Seq("git", "checkout", "-b", name), dir).! == 0
  }
  def status(path: String, flags: String = ""): String = {
    val dir = new File(path)
    val cmd = Seq("git", "-c", "color.status=always", "status", flags).filter(_ != "")
    Process(cmd, dir).!!
  }
  def merge(path: String, branch: String): Boolean = {
    val dir = new File(path)
    Process(Seq("git", "merge", branch), dir).! == 0
  }
  def sha(path: String): String = {
    val dir = new File(path)
    val cmd = Seq("git", "rev-parse", "HEAD")
    Process(cmd, dir).!!
  }
}


/** Original repository within this multirepository,
 *  in the `dir` directory.
 */
case class Repo(
  dir: String, origin: String, mirrors: Seq[String], ref: Option[String] = None
)


/** Higher-level utility methods for working with repositories. */
object Repo {
  private val logLock = new AnyRef

  def commit(log: MechaLog, name: String, repo: Repo)(
    implicit reader: MechaReader
  ): Unit = {
    if (!Git.addAll(repo.dir)) {
      log.error(s"Could not stage changes in '${repo.dir}'.")
    } else if (Git.isDirty(repo.dir)) {
      log.info(s"--- diff for '$name' in '${repo.dir}' ---")
      log.info(Git.diff(repo.dir))
      log.info(s"--- end of diff for '$name' in '${repo.dir}' ---")
      reader.readLine("Commit message (empty aborts): ") match {
        case Some(msg) if msg.size > 0 =>
          if (!Git.commit(repo.dir, msg)) log.error("Could not commit.")
        case _ => log.info("Empty message -- commit aborted.")
      }
    }
  }

  def pull(
    log: MechaLog, name: String, repo: Repo, remoteName: String
  ): Future[(String, BufferedLogger)] = {
    log.info(s"Pull '${repo.dir}' from '$remoteName'...")
    val branch = Git.branchName(repo.dir)
    val logger = BufferedLogger()
    Future {
      if (!Git.pull(repo.dir, remoteName, branch, logger))
        log.error(s"Pull failed: ${repo.dir}")
      (name, logger)
    }
  }

  def push(
    log: MechaLog, flags: Seq[String], name: String, repo: Repo,
    remoteName: String
  ): Future[(String, BufferedLogger)] = {
    log.info(s"Push '${repo.dir}' to '$remoteName'...")
    val branch = Git.branchName(repo.dir)
    val logger = BufferedLogger()
    Future {
      if (!Git.push(repo.dir, remoteName, branch, flags, logger))
        log.error(s"Push failed: ${repo.dir}")
      (name, logger)
    }
  }

  def awaitPushes(
    log: MechaLog,
    pushes: Traversable[Future[(String, BufferedLogger)]]
  ): Unit = {
    val allPushes = for (push <- pushes) yield {
      for ((name, output) <- push) yield logLock.synchronized {
        log.info(s"------ $name ------")
        log.info(output())
      }
    }
    Await.ready(Future.sequence(allPushes), Duration.Inf)
  }

  def awaitPulls(log: MechaLog,
    pulls: Traversable[Future[(String, BufferedLogger)]]): Unit = {
    val allPulls = for (pull <- pulls) yield {
      for ((name, output) <- pull) yield logLock.synchronized {
        log.info(s"------ $name ------")
        log.info(output())
      }
    }
    Await.ready(Future.sequence(allPulls), Duration.Inf)
  }
}
