package org.stormenroute






package mecha {
  
  /** Dependency descriptor, describes how to resolve dependencies. */
  case class Dep(local: String, artifact: Option[String] = None) {
    override def toString = s"$local($artifact)"
  }

  /** Original repository within this multirepository, in the `dir` directory. */
  case class Repo(dir: String, origin: String, mirrors: Seq[String], dependencies: Seq[Dep], tests: Seq[String])
}


package object mecha {

}
