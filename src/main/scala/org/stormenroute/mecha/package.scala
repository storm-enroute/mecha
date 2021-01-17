package org.stormenroute



import com.typesafe.config._
import java.io._
import org.apache.commons.io._
import scala.collection.JavaConverters._
import sbt._
import sbt.Keys._
import scala.annotation._
import scala.collection._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.sys.process._



package mecha {

  /** Logging interface. */
  trait MechaLog {
    def info(s: String): Unit
    def warn(s: String): Unit
    def error(s: String): Unit
  }

  /** Standard logging interface implementations. */
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
}


package object mecha {

  implicit def logger2MechaLog(log: Logger) = new MechaLog {
    def info(s: String) = log.info(s)
    def warn(s: String) = log.warn(s)
    def error(s: String) = log.error(s)
  }

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

  val mechaPullBenchesKey = TaskKey[Unit](
    "mecha-pull-benches",
    "Pulls the benchmark results from the remote Git repo, " +
    "if defined in `mechaBenchRepo`. This task deletes all local benchmark results."
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

  val mechaNightlyTestKey = TaskKey[Unit](
    "mecha-nightly-test",
    "Runs the nightly tests: tests all projects, but does not publish anything."
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
      val repomap = mutable.Map[String, Repo]()
      val config = ConfigFactory.parseFile(file)
      for ((name, r: ConfigObject) <- config.root.asScala) {
        val repo = r.toConfig
        repomap(name) = Repo(
          dir = repo.getString("dir"),
          origin = repo.getString("origin"),
          mirrors = repo.getStringList("mirrors").asScala,
          ref = if (repo.hasPath("ref")) Some(repo.getString("ref")) else None
        )
      }
      repomap.toMap
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
    def stringQuery(question: String): Query[String] = {
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
      () => for (x <- query()) yield f(x)
    }

    def filter[T](query: Query[T])(p: T => Boolean): Query[T] = {
      () => query() match {
        case Some(v) => if (p(v)) Some(v) else None
        case None => None
      }
    }

    def flatMap[T, S](query: Query[T])(f: T => Query[S]): Query[S] = {
      () => for (x <- query(); y <- f(x)()) yield y
    }

    def foreach[T, U](query: Query[T])(f: T => U): Unit = {
      query() match {
        case Some(v) => f(v)
        case None => // do nothing
      }
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
    def filter(p: T => Boolean): Input.Query[T] = Input.filter(query)(p)
    def flatMap[S](f: T => Input.Query[S]): Input.Query[S] = Input.flatMap(query)(f)
    def foreach[U](f: T => U): Unit = Input.foreach(query)(f)
    def default(v: =>T): Input.Query[T] = Input.default(query)(v)
  }

  private[mecha] object GitIgnore {
    import java.nio.charset.StandardCharsets._
    import java.nio.file.{FileSystems, Paths, Files, Path}
    import java.nio.file.StandardOpenOption._

    val log = MechaLog.Println

    def readFile(file: Path): Seq[String] = {
      Files.readAllLines(file, UTF_8).asScala
    }

    def writeFile(file: Path, content: Seq[String]) = {
      Files.write(file, content.asJava, UTF_8, WRITE, CREATE)
    }

    def ignore(toIgnore: String, gitIgnoreFilePath: Path, gitExcludeFilePath: Path) = {
      val gitIgnore =
        if (Files.exists(gitIgnoreFilePath)) readFile(gitIgnoreFilePath)
        else Seq()
      val gitExclude =
        if (Files.exists(gitExcludeFilePath)) readFile(gitExcludeFilePath)
        else Seq()

      addIgnore(toIgnore, gitExclude, gitIgnore ++ gitExclude).fold({
          case Whitelists(patterns) =>
            log.warn(s"The new repo can not be ignored by Git. " +
              "It is whitelisted by the following patterns: $patterns")
          case Blacklists(patterns) =>
            log.info(s"The new repo is already ignored via the following " +
              s"pattern(s): $patterns")
      }, b => writeFile(gitExcludeFilePath, b))
    }

    sealed trait Patterns
    case class Whitelists(patterns: Seq[WhitelistPattern]) extends Patterns
    case class Blacklists(patterns: Seq[BlacklistPattern]) extends Patterns

    def addIgnore(
      linesToAdd: String, excludeLines: Seq[String], allLines: Seq[String]
    ): Either[Patterns, Seq[String]] = {
      val patterns = allLines.map(Line.apply).filter(_.isInstanceOf[Pattern])
      val wls = whitelists(linesToAdd, patterns)
      if (wls.nonEmpty) Left(Whitelists(wls))
      else {
        val bls = blacklists(linesToAdd, patterns)
        if (bls.nonEmpty) Left(Blacklists(bls))
        else Right(excludeLines :+ linesToAdd)
      }
    }

    def whitelists(toCheck: String, against: Seq[Line]): Seq[WhitelistPattern] =
      against collect { case p: WhitelistPattern if p.matches(toCheck) => p }

    def blacklists(toCheck: String, against: Seq[Line]): Seq[BlacklistPattern] =
      against collect { case p: BlacklistPattern if p.matches(toCheck) => p }

    object Line {
      def apply(l: String): Line = l.trim match {
        case "" => Empty
        case s if s.startsWith("#") => Comment(s)
        case s if s.startsWith("!") => WhitelistPattern(s.substring(1))
        case s => BlacklistPattern(s)
      }

      def unapply(l: Line): Option[String] = l match {
        case Empty => Some("")
        case Comment(c) => Some(c)
        case WhitelistPattern(p) => Some(s"!$p")
        case BlacklistPattern(p) => Some(p)
        case _ => None
      }
    }

    sealed trait Line {
      def matches(path: String): Boolean = false
    }

    object Empty extends Line
    case class Comment(c: String) extends Line

    abstract class Pattern extends Line {
      val p: String
      val trimmed = p.trim
      val matcher = FileSystems.getDefault.getPathMatcher(s"glob:./${cleanUp(trimmed)}")

      override def matches(dirName: String): Boolean = {
        val path = if (!dirName.startsWith("./")) s"./$dirName" else dirName
        matcher.matches(Paths.get(path))
      }

      @tailrec
      private def cleanUp(s: String): String = {
        if (s.endsWith("/")) cleanUp(s.substring(0, s.length - 1))
        else if (s.startsWith("/")) cleanUp(s.substring(1))
        else if (s.startsWith("**/")) cleanUp(s.substring(2))
        else if (s.endsWith("/**")) cleanUp(s.substring(0, s.length - 2))
        else s
      }
    }

    /** E.g. "some-*-path/"
     *
     *  @param p the pattern
     */
    case class BlacklistPattern(override val p: String) extends Pattern

    /** E.g. "!some-*-path/"
     *
     *  @param p the pattern
     */
    case class WhitelistPattern(override val p: String) extends Pattern
  }
}
