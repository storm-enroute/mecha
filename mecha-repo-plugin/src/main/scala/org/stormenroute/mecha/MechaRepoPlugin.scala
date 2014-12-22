package org.stormenroute.mecha



import sbt._
import Keys._
import complete.DefaultParsers._
import java.io.File
import org.apache.commons.io._



trait MechaRepoBuild extends Build {
  def repositoriesFile: File
  val repositories: Map[String, Repo] = reposFromJson(repositoriesFile)
}


/** Added to each repository inside the superrepository.
 */
object MechaRepoPlugin extends Plugin {

  override val projectSettings = Seq(
  )

}
