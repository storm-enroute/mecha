package org.stormenroute.mecha



import sbt._
import sbt.Keys._
import sbt.complete.DefaultParsers._
import java.io.File
import scala.collection._
import org.apache.commons.io._
import spray.json._
import spray.json.DefaultJsonProtocol._



/** Optionally mixed into repositories within a superrepository. */
trait MechaRepoBuild extends Build {
  import MechaRepoPlugin.Artifact

  /** Location of the repository configuration in the superrepo.
   */
  def repositoriesFile: File = file("../repos.json")
  
  /** Holds repository configuration if repo is checked out within a
   *  superrepo.
   */
  val repositories: Option[Map[String, Repo]] = {
    if (repositoriesFile.exists)
      Some(ConfigParsers.reposFromJson(repositoriesFile))
    else None
  }

  /** File that describes dependencies on the superrepo for the current build.
   *  
   *  Format:
   *
   *      {
   *        "myProjectName": [
   *          {
   *            "project": "otherProjectName",
   *            "artifact": ["org.myorg", "other-project", "1.6"]
   *          },
   *          {
   *            "project": "yetAnotherProjectName",
   *            "artifact": null
   *          }
   *        ],
   *        "myCoreProjectName": []
   *      }
   *
   *  Override this method to specify a different path to this file.
   *  
   *  Dependencies added to this file do not need to be specified manually as
   *  project dependencies or library dependencies.
   *  Instead, a project must be defined with a `dependsOnSuperRepo` statement,
   *  and library dependencies must take the `superRepoDependencies` list:
   *
   *      lazy val myProject = Project(
   *        "myProjectName",
   *        file("myProjectDir")
   *        settings = mySettings
   *      ).dependsOnSuperRepo("myProjectName")
   *
   *  And:
   *
   *      libraryDependencies ++= superRepoDependencies("myProjectName")
   */
  def dependenciesFile: File = file("dependencies.json")

  /** Maps projects in the build to their dependencies.
   */
  val dependencies: Option[Map[String, Seq[MechaRepoPlugin.Dependency]]] = {
    if (dependenciesFile.exists)
      Some(MechaRepoPlugin.dependenciesFromJson(dependenciesFile))
    else None
  }
  
  /** Resolves the artifact dependencies based on the superrepo.
   */
  final def superRepoDependencies(projName: String): Seq[ModuleID] = {
    def from(art: Option[Artifact], msg: String) = {
      val a = art.getOrElse(
          sys.error(s"Missing artifact information for '$projName'. $msg"))
      a.group % a.project % a.version
    }
    dependencies match {
      case None =>
        // no dependency information
        Seq()
      case Some(deps) =>
        deps(projName).flatMap { dep =>
          repositories match {
            case None =>
              // no superrepo
              Seq(from(dep.artifact, "Not in a superrepo."))
            case Some(repos) =>
              // inside superrepo
              val repodir = new File("../" + repos(dep.project).dir)
              if (repodir.exists) Seq()
              else Seq(from(dep.artifact, "Repository not tracked."))
          }
        }
    }
  }

  implicit class ProjectOps(p: Project) {
    /** Returns the version of the project depending directly on repositories
     *  in the superrepository, if a superrepository is present.
     */
    def aggregateSuperRepo: Project = {
      dependencies match {
        case None =>
          // no dependency information
          p
        case Some(deps) =>
          deps(p.id).filter( dep =>
            repositories match {
              case None =>
                // not in a superrepo -- library dependencies raises the error
                false
              case Some(repos) =>
                // in a superrepo
                val repodir = new File("../" + repos(dep.project).dir)
                repodir.exists
            }
          ).map(dep => RootProject(file(dep.project)))
              .foldLeft(p)(_ aggregate _)
      }
    }
  }

}


/** Added to each repository inside the superrepository.
 */
object MechaRepoPlugin extends Plugin {

  case class Artifact(group: String, project: String, version: String)

  /** Describes a project dependency. */
  case class Dependency(project: String, artifact: Option[Artifact])

  def dependenciesFromJson(file: File): Map[String, Seq[Dependency]] = {
    import scala.annotation.unchecked
    val depmap = mutable.Map[String, Seq[Dependency]]()
    val content = FileUtils.readFileToString(file, null: String)
    val tree = content.parseJson
    def str(v: JsValue) = (v: @unchecked) match {
      case JsString(s) => s
    }
    def artifact(v: JsValue) = (v: @unchecked) match {
      case JsNull =>
        None
      case JsArray(Seq(JsString(org), JsString(proj), JsString(vers))) =>
        Some(Artifact(org, proj, vers))
    }
    (tree: @unchecked) match {
      case JsObject(projects) =>
        for ((projectName, JsArray(depelems)) <- projects) {
          val deps = for (JsObject(entries) <- depelems) yield {
            Dependency(str(entries("project")), artifact(entries("artifact")))
          }
          depmap(projectName) = deps
        }
    }
    depmap
  }

  override val projectSettings = Seq(
  )

}
