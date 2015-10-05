package org.stormenroute



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



package mecha {

  trait MechaLog {
    def info(s: String): Unit
    def warn(s: String): Unit
    def error(s: String): Unit
  }

  object MechaLog {
    object Println extends MechaLog {
      def info(s: String) = println(s)
      def warn(s: String) = println(s)
      def error(s: String) = scala.Console.err.println(s)
    }
  }

  trait MechaReader {
    def readLine(prompt: String): Option[String]
  }

  /** Original repository within this multirepository,
    * in the `dir` directory.
    */
  case class Repo(dir: String, origin: String, mirrors: Seq[String])

  /** Higher-level utility methods for working with repositories. */
  object Repo {
    def commit(log: MechaLog, name: String, repo: Repo)(
      implicit reader: MechaReader): Unit = {
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

    def push(log: MechaLog, flags: Seq[String], name: String, repo: Repo,
      remoteName: String): Future[(String, BufferedLogger)] = {
      log.info(s"Push '${repo.dir}' to '$remoteName'...")
      val branch = Git.branchName(repo.dir)
      val logger = BufferedLogger()
      Future {
        if (!Git.push(repo.dir, remoteName, branch, flags.mkString(" "), logger))
          log.error(s"Push failed: ${repo.dir}")
        (name, logger)
      }
    }

    def awaitPushes(log: MechaLog,
      pushes: Traversable[Future[(String, BufferedLogger)]]): Unit = {
      for (push <- pushes; (name, output) <- push) {
        log.info(s"------ $name ------")
        log.info(output())
      }
      Await.ready(Future.sequence(pushes), Duration.Inf)
    }
  }

  /** Utility methods for working with Git. */
  object Git {
    def clone(url: String, path: String): Boolean = {
      val dir = new File(path)
      Process(Seq("git", "clone", "--progress", url, "."), dir).! == 0
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
    def pull(path: String, location: String, branch: String = ""): Boolean = {
      val dir = new File(path)
      val cmd = Seq("git", "pull", location, branch).filter(_ != "")
      Process(cmd, dir).! == 0
    }
    def push(path: String, location: String, branch: String = "",
        flags: String = "", logger: ProcessLogger = PrintlnLogger): Boolean = {
      val dir = new File(path)
      val cmd = Seq("git", "push", flags, location, branch).filter(_ != "")
      Process(cmd, dir).!(logger) == 0
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

}


package object mecha {

  /* tasks */

  val mechaEditRefreshKey = TaskKey[Unit](
    "mecha-edit-refresh",
    "By default does nothing, but may be overridden to perform actions " +
    "needed for the edit-refresh cycle (such as compilation). " +
    "Running this task on the super repo invokes the same task on all repos."
  )

  val mechaPublishKey = TaskKey[Unit](
    "mecha-publish",
    "By default does nothing, but may be overridden to publish the project. " +
    "It is invoked by the nightly task."
  )

  val mechaPublishDocsKey = TaskKey[Unit](
    "mecha-publish-docs",
    "Pushes the docs to the Git repo, if defined in `mechaDocsRepo`. " +
    "It is invoked by the nightly task."
  )

  val mechaPublishBenchesKey = TaskKey[Unit](
    "mecha-publish-benches",
    "Pushes the benchmarks to the Git repo, if defined in `mechaBenchRepo`. " +
    "It is invoked by the nightly task."
  )

  val mechaPublishBuildOutputKey = TaskKey[Unit](
    "mecha-publish-build-output",
    "Pushes build output to the Git repo, if defined in `mechaBuildOutputRepo`. " +
    "It is invoked by the nightly task."
  )

  val mechaBuildOutputRepoKey = SettingKey[String](
    "mecha-build-output-repo",
    "The URL of the Git repo where build outputs are published."
  )

  val mechaBuildOutputBranchKey = SettingKey[String](
    "mecha-build-output-branch",
    "The branch where build outputs are published."
  )

  val mechaBuildOutputPathKey = SettingKey[String](
    "mecha-build-output-path",
    "The repo-relative path where build outputs are published."
  )

  val mechaBuildOutputSrcPathKey = SettingKey[String](
    "mecha-build-output-src-path",
    "The local path where build outputs are."
  )

  val mechaBuildOutputExpirationDaysKey = SettingKey[Int](
    "mecha-build-output-expiration-days",
    "Number of days after which build results expire, and can be deleted."
  )

  val mechaBenchRepoKey = SettingKey[String](
    "mecha-bench-repo",
    "The URL of the Git repo where benchmarks are published."
  )

  val mechaBenchBranchKey = SettingKey[String](
    "mecha-bench-branch",
    "The branch where benchmarks are published."
  )

  val mechaBenchPathKey = SettingKey[String](
    "mecha-bench-path",
    "The repo-relative path where benchmarks are published."
  )

  val mechaBenchSrcPathKey = SettingKey[String](
    "mecha-bench-src-path",
    "The local path where benchmarks are (<root>/target/benchmarks by default)."
  )

  val mechaDocsRepoKey = SettingKey[String](
    "mecha-docs-repo",
    "The URL of the Git repo where docs are published."
  )

  val mechaDocsBranchKey = SettingKey[String](
    "mecha-docs-branch",
    "The branch where docs are published."
  )

  val mechaDocsPathKey = SettingKey[String](
    "mecha-docs-path",
    "The repo-relative path where docs are published."
  )

  val mechaNightlyKey = TaskKey[Unit](
    "mecha-nightly",
    "Runs the nightly build: tests all projects, publishes snapshots, docs and benches."
  )

  /* utils */

  val PrintlnLogger = ProcessLogger(println, println)

  case class BufferedLogger() extends ProcessLogger with (() => String) {
    private val buf = mutable.ArrayBuffer[String]()
    def err(s: =>String) = buf.synchronized {
      buf += s
    }
    def out(s: =>String) = buf.synchronized {
      buf += s
    }
    def buffer[T](f: =>T) = f
    def apply() = {
      val s = buf.mkString("\n")
      buf.clear()
      s
    }
  }

  object ConfigParsers {

    /** Parse repository configuration from Hocon. */
    def reposFromHocon(file: File): Map[String, Repo] = {
      import scala.collection.convert.decorateAsScala._
      val repomap = mutable.Map[String, Repo]()
      val config = ConfigFactory.parseFile(file)
      for ((name, r: ConfigObject) <- config.root.asScala) {
        val repo = r.toConfig
        repomap(name) = Repo(
          dir = repo.getString("dir"),
          origin = repo.getString("origin"),
          mirrors = repo.getStringList("mirrors").asScala
        )
      }
      repomap
    }

    def versionFromFile(file: File, labels: List[String]): String = {
      val fis = new FileInputStream(file)
      val props = new java.util.Properties()
      try props.load(fis)
      finally fis.close()
      labels.map(label => Option(props.getProperty(label)).get).mkString(".")
    }

  }

  /** Combinators for querying user input.
   */
  object Input {
    type Query[T] = () => Option[T]

    object Queue {
      private val monitor = new AnyRef
      /** Enqueues queries and executes them serially. */
      def submit[T](query: Query[T]): Option[T] = {
        monitor.synchronized {
          query()
        }
      }
    }

    /* Combinators. */

    /** Basic query combinator -- asks user for input and retrieves a string. */
    def stringQuery(question: String): Input.Query[String] = {
      () => SimpleReader.readLine(question).filter(_ != "")
    }

    def const[T](v: =>T): Query[T] = {
      () => Some(v)
    }

    def pair[P, Q](keyq: Query[P], valq: Query[Q]): Query[(P, Q)] = {
      () => for {
        p <- keyq()
        q <- valq()
      } yield (p, q)
    }

    def repeat[T](query: Query[T]): Query[List[T]] = {
      @tailrec def repeat(acc: List[T]): List[T] = {
        query() match {
          case Some(t) => repeat(t :: acc)
          case None => acc
        }
      }
      () => Some(repeat(Nil).reverse)
    }

    def default[T](query: Query[T])(v: =>T): Query[T] = {
      () => query().orElse(Some(v))
    }

    def map[T, S](query: Query[T])(f: T => S): Query[S] = {
      () => query().map(f)
    }

    def traverse[T](queries: Traversable[Query[T]]):
      Query[Traversable[Option[T]]] = {
      () => Some(for (q <- queries) yield q())
    }

    def traverseFull[T](queries: Traversable[Query[T]]):
      Query[Traversable[T]] = {
      map(traverse(queries))(_.filter(_.nonEmpty).map(_.get))
    }

  }

  implicit class queryOps[T](val query: Input.Query[T]) {
    def map[S](f: T => S): Input.Query[S] = Input.map(query)(f)
    def default(v: =>T): Input.Query[T] = Input.default(query)(v)
  }

}
