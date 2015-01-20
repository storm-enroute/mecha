package org.stormenroute.mecha



import sbt._
import sbt.Keys._
import sbt.complete.DefaultParsers._
import java.io.File
import scala.collection._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import org.apache.commons.io._
import spray.json._
import spray.json.DefaultJsonProtocol._
import com.decodified.scalassh._



/** Optionally mixed into repositories within a superrepository. */
trait MechaRepoBuild extends Build {
  import MechaRepoPlugin.Artifact

  /* Basic superrepo configuration */

  /** Dirty hack because sbt does not expose BuildUnit.localBase easily. */
  def buildBase: File = {
    if (file(dependenciesPath).exists) file(".")
    else file(repoName)
  }

  def repoName: String

  /** Location of the repository configuration in the superrepo.
   */
  def repositoriesPath: String = "../repos.json"

  final def repositoriesFile: File = new File(buildBase, repositoriesPath)

  /** Holds repository configuration if repo is checked out within a
   *  superrepo.
   */
  val repositories: Option[Map[String, Repo]] = {
    if (repositoriesFile.exists)
      Some(ConfigParsers.reposFromJson(repositoriesFile))
    else None
  }

  /* Dependency configuration */

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
  def dependenciesPath: String = "dependencies.json"

  final def dependenciesFile: File = new File(buildBase, dependenciesPath)

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
    val requiredArtifacts = dependencies match {
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
              val repodir = new File(buildBase, "../" + repos(dep.project).dir)
              if (repodir.exists) Seq()
              else Seq(from(dep.artifact, "Repository not tracked."))
          }
        }
    }
    //println(s"Additional artifacts for '$projName': ${requiredArtifacts}")
    requiredArtifacts
  }

  implicit class ProjectOps(p: Project) {
    /** Returns the version of the project depending directly on repositories
     *  in the superrepository, if a superrepository is present.
     */
    def dependsOnSuperRepo: Project = {
      dependencies match {
        case None =>
          // no dependency information
          p
        case Some(deps) =>
          deps(p.id).filter(dep => repositories match {
            case None =>
              // not in a superrepo -- library dependencies raise the error
              false
            case Some(repos) =>
              // in a superrepo
              val repodir = new File(buildBase, "../" + repos(dep.project).dir)
              repodir.exists
          }).map(dep => RootProject(uri("../" + dep.project)))
            .foldLeft(p)(_ dependsOn _)
      }
    }
  }

  /* Configuration file */

  /** Basic query combinator -- asks user for input and retrieves a string. */
  def stringQuery(question: String): Input.Query[String] = {
    () => SimpleReader.readLine(question).filter(_ != "")
  }

}


/** Added to each repository inside the superrepository.
 */
object MechaRepoPlugin extends Plugin {

  implicit def logger2MechaLog(log: Logger) = new MechaLog {
    def info(s: String) = log.info(s)
    def warn(s: String) = log.warn(s)
    def error(s: String) = log.error(s)
  }

  implicit val reader = new MechaReader {
    def readLine(prompt: String) = SimpleReader.readLine(prompt)
  }

  implicit class ClientOps(client: SshClient) {
    def apply(log: Logger, command: String): Int = {
      val result = client.exec(command).right.map { result =>
        def print(s: String, f: String => Unit) = if (s != "") f(s)
        print(result.stdOutAsString(), s => log.info(s))
        print(result.stdErrAsString(), s => log.warn(s))
        result.exitCode match {
          case Some(code) =>
            code
          case None =>
            sys.error(s"No exit code from '$command'.")
        }
      }
      result.fold(sys.error(_), x => x)
    }
  }

  /* tasks and settings */

  val configFilePathKey = SettingKey[String](
    "mecha-config-file-path",
    "A path to the configuration file."
  )

  val configQueryKey =
    SettingKey[Option[Input.Query[Traversable[(String, String)]]]](
    "mecha-config-query",
    "An optional configuration query that gives a list of string pairs."
  )

  val beforeGenerateConfigKey = SettingKey[(Logger, File) => Unit](
    "mecha-before-generate-config",
    "Function to execute before generating the configuration."
  )

  val afterGenerateConfigKey = SettingKey[(Logger, File) => Unit](
    "mecha-after-generate-config",
    "Function to execute after generating the configuration."
  )

  val generateConfigFileKey = TaskKey[Unit](
    "mecha-generate-config",
    "Generates the configuration file from user input, if it does not exist."
  )

  val generateConfigFileTask = generateConfigFileKey := {
    val log = streams.value.log
    val base = baseDirectory.value
    val configFile = base / configFilePathKey.value
    for (query <- configQueryKey.value) {
      if (!configFile.exists)
        generateConfigFile(log, base, configFile,
          beforeGenerateConfigKey.value, afterGenerateConfigKey.value,
          query)
    }
  }

  val remoteSshHost = SettingKey[Option[String]](
    "mecha-remote-ssh-host",
    "The url of the remote host."
  )

  val remoteSshUser = SettingKey[String](
    "mecha-remote-ssh-user",
    "The user for the remote host."
  )

  val remoteSshPass = SettingKey[Option[String]](
    "mecha-remote-ssh-pass",
    "The pass for the remote host, or `None` to ask each time."
  )

  val remoteDeployPathKey = SettingKey[String](
    "mecha-remote-deploy-path",
    "The path to the remote directory for deployment."
  )

  val remoteDeployCmdKey = SettingKey[String](
    "mecha-remote-deploy-cmd",
    "The command for remote deployment."
  )

  val sshDeployTask = TaskKey[Unit](
    "mecha-ssh-deploy",
    "Pushes the repository contents, checks them out via ssh in a remote " +
    "repository, and runs a custom command."
  ) := {
    val log = streams.value.log
    val name = thisProject.value.id
    val repo = Repo(baseDirectory.value.getPath, "origin", Nil)
    if (Git.isDirty(repo.dir)) {
      // commit
      Repo.commit(log, name, repo)
      
      // push
      val pushing = Repo.push(log, Nil, name, repo, "origin")
      Repo.awaitPushes(log, List(pushing))
    }
    val repoUrl = Git.remoteUrl(baseDirectory.value.getPath, "origin")
    val deployPath = remoteDeployPathKey.value
    val repoDir = thisProject.value.id
    val repoPath = deployPath + "/" + repoDir
    val deployCmd = remoteDeployCmdKey.value

    // ssh pull and run custom command
    remoteSshHost.value match {
      case Some(host) =>
        log.info(s"Pulling at remote host '$host'...")
        log.info(s"repoUrl: ${repoUrl}")
        val user = remoteSshUser.value
        val pass = remoteSshPass.value.getOrElse {
          SimpleReader.readLine("Password for '$user': ").getOrElse("")
        }
        val passProducer = SimplePasswordProducer(pass)
        val hostConfig = HostConfig(
          login = PasswordLogin(user, passProducer),
          hostKeyVerifier = HostKeyVerifiers.DontVerify)
        SSH(host, hostConfig) { client =>
          // change to deploy dir
          if (client(log, s"cd $deployPath") != 0) {
            log.error("Deploy dir does not exist.")
          } else {
            if (client(log, s"""[ -d "$repoPath" ]""") != 0) {
              client(log, s"mkdir $repoPath")
            }
            client(log, s"cd $repoPath; pwd")

            // check if fresh checkout required
            if (client(log, s"cd $repoPath; git rev-parse") != 0) {
              log.warn("Checking out repository for the first time.")
              client(log, s"cd $repoPath; git clone $repoUrl .")
            } else {
              log.info("Pulling from upstream.")
              client(log, s"cd $repoPath; git pull")
            }

            client(log, s"cd $repoPath; $deployCmd")
          }

          ()
        } match {
          case Right(ok) => log.info(s"SSH session completed.")
          case Left(err) => log.error(s"$err")
        }
      case None =>
        log.error(s"Remote SSH host not set up -- see '$remoteSshHost'.")
    }
  }

  val defaultSettings = Seq(
    configFilePathKey := "config.sbt",
    configQueryKey := None,
    beforeGenerateConfigKey := {
      (log, base) => {}
    },
    afterGenerateConfigKey := {
      (log, base) => {}
    },
    generateConfigFileTask,
    (compile in Compile) <<=
      (compile in Compile) dependsOn generateConfigFileKey,
    remoteSshHost := None,
    remoteSshUser := "admin",
    remoteSshPass := None,
    remoteDeployPathKey := "~",
    remoteDeployCmdKey := "echo 'No deploy command specified.'",
    sshDeployTask
  )

  /* various utilities */

  /** Queries the user to enter values for the config file.
   */
  def generateConfigFile(
    log: Logger,
    base: File,
    configFile: File,
    beforeGenerateConfig: (Logger, File) => Unit,
    afterGenerateConfig: (Logger, File) => Unit,
    query: Input.Query[Traversable[(String, String)]]
  ) {
    log.warn(s"Populating configuration file.")
    beforeGenerateConfig(log, base)
    val userInput = Input.Queue.submit(query)
    val settings = for (kvs <- userInput.toList; (k, v) <- kvs) yield (k, v)
    val content = settings.map({case (k, v) => s"$k := $v"}).mkString("\n\n")
    IO.write(configFile, content)
    afterGenerateConfig(log, base)
    log.error(s"Created '${configFile}'. Please reload!")
  }

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

}
