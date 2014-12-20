package org.stormenroute



import scala.sys.process._



package mecha {
  
  /** Dependency descriptor, describes how to resolve dependencies. */
  case class Dep(local: String, artifact: Option[String] = None) {
    override def toString = s"$local($artifact)"
  }

  /** Original repository within this multirepository, in the `dir` directory. */
  case class Repo(dir: String, origin: String, mirrors: Seq[String], dependencies: Seq[Dep], tests: Seq[String])
}


package object mecha {

  implicit class ProcessBuilderOps(val pb: ProcessBuilder) {
    def exec(fout: String => Unit, ferr: String => Unit) = {
      val logger = ProcessLogger(fout, ferr)
      val exitcode = pb.!(logger)
      if (exitcode != 0) throw new RuntimeException("Failed with $exitcode.")
    }
  }

}
