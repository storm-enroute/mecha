package org.stormenroute



import java.io.File
import scala.collection._
import scala.sys.process._
import org.apache.commons.io._
import spray.json._
import DefaultJsonProtocol._



package mecha {
  
  /** Dependency descriptor, describes how to resolve dependencies. */
  case class Dep(local: String, artifact: Option[String] = None) {
    override def toString = s"$local($artifact)"
  }

  /** Original repository within this multirepository, in the `dir` directory. */
  case class Repo(dir: String, origin: String, mirrors: Seq[String], dependencies: Seq[Dep], tests: Seq[String])

  /** Utility methods for working with Git. */
  object Git {
    def clone(url: String, dir: String): Boolean = {
      Process(s"git clone $url $dir").! == 0
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
    def push(path: String, location: String): Boolean = {
      val dir = new File(path)
      Process(s"git push -u $location", dir).! == 0
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
      Process(s"git show-ref --verify --quiet refs/heads/$path", dir).! == 0
    }
    def checkout(path: String, name: String): Boolean = {
      val dir = new File(path)
      Process(s"git checkout $name", dir).! == 0
    }
    def status(path: String): String = {
      val dir = new File(path)
      Process(s"git -c color.status=always status", dir).!!
    }
  }

}


package object mecha {

  /** Parse repos from Json. */
  def reposFromJson(file: File): Map[String, Repo] = {
    import scala.annotation.unchecked
    val repomap = mutable.Map[String, Repo]()
    val content = FileUtils.readFileToString(file, null: String)
    val tree = content.parseJson
    (tree: @unchecked) match {
      case JsArray(repos) =>
        for (JsObject(fields) <- repos) {
          val (name, JsObject(conf)) = fields.head
          def str(v: JsValue) = (v: @unchecked) match {
            case JsString(s) => s
          }
          def strings(v: JsValue) = (v: @unchecked) match {
            case JsArray(ss) => for (JsString(s) <- ss) yield s
          }
          def deps(v: JsValue) = (v: @unchecked) match {
            case JsArray(deps) =>
              for (JsArray(ss) <- deps) yield ss match {
                case Seq(local, artifact) => Dep(str(local), Some(str(artifact)))
                case Seq(local) => Dep(str(local))
              }
          }
          repomap(name) = Repo(
            dir = str(conf("dir")),
            origin = str(conf("origin")),
            mirrors = strings(conf("mirrors")),
            dependencies = deps(conf("dependencies")),
            tests = strings(conf("tests"))
          )
        }
    }
    repomap
  }

}
