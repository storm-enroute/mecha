package org.stormenroute



import java.io._
import scala.annotation._
import scala.collection._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.sys.process._
import org.apache.commons.io._
import spray.json._
import spray.json.DefaultJsonProtocol._



package mecha {

  trait MechaLog {
    def info(s: String): Unit
    def warn(s: String): Unit
    def error(s: String): Unit
  }

  /** Original repository within this multirepository,
    * in the `dir` directory.
    */
  case class Repo(dir: String, origin: String, mirrors: Seq[String])

  /** Higher-level utility methods for working with repositories. */
  object Repo {
    def push(log: MechaLog, flags: Seq[String], name: String, repo: Repo):
      Future[(String, BufferedLogger)] = {
      log.info(s"Push '${repo.dir}' to origin...")
      val branch = Git.branchName(repo.dir)
      val logger = BufferedLogger()
      Future {
        if (!Git.push(repo.dir, "origin", branch, flags.mkString(" "),
          logger))
          log.error(s"Push failed: ${repo.dir}")
        (name, logger)
      }
    }
  }

  /** Utility methods for working with Git. */
  object Git {
    def clone(url: String, path: String): Boolean = {
      val dir = new File(path)
      Process(s"git clone --progress $url .", dir).! == 0
    }
    def isDirty(path: String): Boolean = {
      isUncommitted(path) || isUnstaged(path)
    }
    def isUncommitted(path: String): Boolean = {
      val dir = new File(path)
      Process(s"git diff-index --quiet --cached HEAD", dir).! != 0
    }
    def isUnstaged(path: String): Boolean = {
      val dir = new File(path)
      Process(s"git diff-files --quiet", dir).! != 0
    }
    def pull(path: String, location: String): Boolean = {
      val dir = new File(path)
      Process(s"git pull $location", dir).! == 0
    }
    def push(path: String, location: String, branch: String = "",
        flags: String = "", logger: ProcessLogger = PrintlnLogger): Boolean = {
      val dir = new File(path)
      Process(s"git push $flags $location $branch", dir).!<(logger) == 0
    }
    def addAll(path: String): Boolean = {
      val dir = new File(path)
      Process(s"git add -A", dir).! == 0
    }
    def diff(path: String): String = {
      val dir = new File(path)
      Process(s"git diff --color HEAD", dir).!!
    }
    def commit(path: String, msg: String): Boolean = {
      val dir = new File(path)
      Process(s"git commit -m '${msg}'", dir).! == 0
    }
    def branchExists(path: String, name: String): Boolean = {
      val dir = new File(path)
      Process(s"git show-ref --verify --quiet refs/heads/$name", dir).! == 0
    }
    def branchName(path: String): String = {
      val dir = new File(path)
      Process(s"git rev-parse --abbrev-ref HEAD", dir).!!
    }
    def checkout(path: String, name: String): Boolean = {
      val dir = new File(path)
      Process(s"git checkout $name", dir).! == 0
    }
    def newBranch(path: String, name: String): Boolean = {
      val dir = new File(path)
      Process(s"git checkout -b $name", dir).! == 0
    }
    def status(path: String): String = {
      val dir = new File(path)
      Process(s"git -c color.status=always status", dir).!!
    }
    def merge(path: String, branch: String): Boolean = {
      val dir = new File(path)
      Process(s"git merge $branch", dir).! == 0
    }
  }

}


package object mecha {

  val PrintlnLogger = ProcessLogger(println, println)

  case class BufferedLogger() extends ProcessLogger with (() => String) {
    val buf = mutable.ArrayBuffer[String]()
    def err(s: =>String) = buf += s
    def out(s: =>String) = buf += s
    def buffer[T](f: =>T) = f
    def apply() = {
      val s = buf.mkString("\n")
      buf.clear()
      s
    }
  }

  object ConfigParsers {

    /** Parse repository configuration from Json. */
    def reposFromJson(file: File): Map[String, Repo] = {
      import scala.annotation.unchecked
      val repomap = mutable.Map[String, Repo]()
      val content = FileUtils.readFileToString(file, null: String)
      val tree = content.parseJson
      (tree: @unchecked) match {
        case JsObject(projects) =>
          for ((name, JsObject(conf)) <- projects) {
            def str(v: JsValue) = (v: @unchecked) match {
              case JsString(s) => s
            }
            def strings(v: JsValue) = (v: @unchecked) match {
              case JsArray(ss) => for (JsString(s) <- ss) yield s
            }
            repomap(name) = Repo(
              dir = str(conf("dir")),
              origin = str(conf("origin")),
              mirrors = strings(conf("mirrors"))
            )
          }
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
