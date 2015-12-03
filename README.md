
![ ](/docs/mecha-logo-256-soft.png)

[ScalaDoc API](http://storm-enroute.com/apidocs/mecha/0.3-SNAPSHOT/api/#package)

[![Build Status](https://travis-ci.org/storm-enroute/mecha.png?branch=master)](https://travis-ci.org/storm-enroute/mecha)

[![Maven Artifact](https://img.shields.io/maven-central/v/com.storm-enroute/mecha.svg)](http://mvnrepository.com/artifact/com.storm-enroute/mecha)


### Mecha -- SBT Plugin That Automates Development Workflow

Ask yourself:

- Did you ever notice yourself working on 10 different projects that depend on each
  other?

- Is publishing a snapshots artifact from one project to compile another project killing
  your productivity?

- You feel your projects are bitrotting because they don't live in the same repository?

- Did you ever want to simultaneously work on different Git code repositories as if they
  were one big project, but commit changes to them separately?

- Are automated nightlies and documentation publishing something you crave for?

- Are you looking for an easy way to configure the project when it's first checked out?

- Or you want to quickly deploy your project over SSH to a remote server?

If the answer is yes to any of the above, you're at the right place.
Mecha is an SBT plugin that aggregates all the SBT projects that you specify into one
big project, which you can then compile as if they were one project.
You can branch/merge/commit changes in all the repositories simultaneously,
pull/push to their respective origins with a single SBT command,
or automatically maintain any number of downstream mirrors.

Mecha is an ideal solution when you want to compile your projects against the
latest version of fast moving dependencies, as the source codes of all your
projects get pulled, compiled, committed, and pushed together.
Guaranteed to boost productivity!


### So, how does all this work?

Mecha uses the concept of a *super-repository*.
A super-repository is a repository in which there are multiple source controlled
repositories for different projects.
Repositories with the super-repository can have dependencies on each other, can
depend on each others' source code, or Maven artifacts.
All the repositories can be worked on, built, tested,
and committed simulatenously.

Mecha puts repositories into subdirectories of the super-repository.
You can choose which subdirectories to *track* while you work,
or instead depend on them through pre-published Maven artifacts.


### What are the features, exactly?

In short:

- supports super-repositories that contain multiple child repositories
- bulk pull, branch, merge, push, and mirroring for child repositories
- optional tracking for child repositories
- automatic source/library dependency configuration in child repositories
- automatic configuration file generation
- automated remote deployments over SSH
- automated documentation deployments to remote Git repositories (ideal for GH pages!)
- automated benchmark deployments in case you're using ScalaMeter

In what follows, we describe the main features of Mecha on an example project.


### Super-Repo Configuration

Let's take a look at an example super-repository --
[`storm-enroute/examples`](https://github.com/storm-enroute/examples).
If you look inside `storm-enroute/examples`, you will see a number of different
example projects.
The example project we are interested in is called `mecha-super-repo`.
It is used to aggregate other projects.

We took four steps in creating this super-repo.

1. Add the `mecha` plugin to your build.
Create the `project/plugins.sbt` file and at the following:

        resolvers ++= Seq(
          "Sonatype OSS Snapshots" at
            "https://oss.sonatype.org/content/repositories/snapshots",
          "Sonatype OSS Releases" at
            "https://oss.sonatype.org/content/repositories/releases"
        )

        addSbtPlugin("com.storm-enroute" % "mecha" % "0.2")

    This will add the plugin to the super-repo SBT build.
    If necessary, replace `0.2` with the proper Mecha version.

2. Import the Mecha package contents:

        import org.stormenroute.mecha._

    This will make all Mecha stuff visible.

3. Define a super-repo build in `project/Build.scala` as in the following example:

        object MechaSuperRepoBuild extends MechaSuperBuild {
          lazy val mechaSuperRepoSettings = Defaults.defaultSettings ++
            defaultMechaSuperSettings ++ Seq(
            name := superName,
            scalaVersion := "2.11.4",
            version := "0.1",
            organization := "com.storm-enroute",
            libraryDependencies ++= Seq()
          )
          
          val superName = "super-storm-enroute"
          val superDirectory = file(".")
          val superSettings = mechaSuperRepoSettings
        }

    The values of particular importance here are `superName`, `superDirectory` and
    `superSettings`.
    You don't need to define a project when you define a super-repo.
    The project is automatically defined for you from these three values.

4. Last, create a `repos.conf` file in the super-repo root directory.
This file contains information about subprojects in this super-repo.
Initially, we can just add the super-project to it:

        # The configuration file is empty for now.
        our-super-project-name {
          dir = "."
          origin = "<path-at-github-or-bitbucket>"
          mirrors = []
        }

    The syntax in the configuration file is
    [HOCON](https://github.com/typesafehub/config).
    Alternatively, you can use JSON syntax for the project configuration file.
    Simply name the file `repos.json` and override the `repositoriesFile` method
    in your project definition to return `repos.json`.
    Then, add the empty JSON object inside that file:

        {
          "our-super-project-name": {
            "dir": ".",
            "origin": "<path-at-github-or-bitbucket>",
            "mirrors": []
          }
        }

    The `repos.conf` file is called the *super-repo configuration file*.
    At this point we can start SBT inside the super-repo.


### Git Workflow

One of the main use cases for Mecha is simplified Git source control when using
multiple projects.
The requirement is that you have Git installed, and available in the `PATH` environment
variable.


#### Optional Tracking

Let's start SBT inside `mecha-super-repo`.
The first Mecha command that you need to know about is `mecha-ls`.
This command will print all the subprojects from the super-repo configuration in
`repos.conf`.
If we run it for an empty configuration file, we get:

    > mecha-ls
    [info] Superproject repositories:
    [success] Total time: 0 s, completed May 23, 2015 12:24:44 AM

So, let's add some projects.
Assume that you have a project `examples-core-utils`, which contains core utilities.
You would like to make changes its nightly version,
but you want to use them right away (to verify that your changes work).
The solution is to include `examples-core-utils` to our super-repo config in
`repos.conf` (use your own fork for `origin` below, or create a fresh repo):

    examples-core-utils {
      dir = "examples-core-utils"
      origin = "git@github.com:storm-enroute/examples-core-utils.git"
      mirrors = []
    }

Alternatively, if you're using JSON syntax:

    {
      "examples-core-utils": {
        "dir": "examples-core-utils",
        "origin": "git@github.com:storm-enroute/examples-core-utils.git",
        "mirrors": []
      }
    }

Run `reload` in the SBT shell to load the change, then `mecha-ls` again:

    > mecha-ls
    [info] Superproject repositories:
    [info] [ ] examples-core-utils
    [success] Total time: 0 s, completed May 23, 2015 12:36:07 AM

Now we're talking business.
The super-project is listing one registered *subproject*.
If you inspect the directory structure in the super-repo,
you will see that `examples-core-utils` is not really there.
That is because you did not track it yet -- in general, there could be many projects
registered, and you don't always want to check out all of them.
So, let's track `examples-core-utils` -- for this we use the `mecha-track` command
(auto-completion will list available projects):

    > mecha-track examples-core-utils
    [info] Cloning 'examples-core-utils' into 'C:\cygwin\home\...'.
    Cloning into '.'...
    remote: Counting objects: 3, done.
    remote: Compressing objects: 100% (2/2), done.
    remote: Total 3 (delta 0), reused 0 (delta 0), pack-reused 0
    Receiving objects: 100% (3/3), done.
    Checking connectivity... done.
    [warn] Please reload the sbt shell.
    [success] Total time: 4 s, completed May 23, 2015 12:39:29 AM

Yeehaw!
The `examples-core-utils` project is now checked out.
If we inspect the directory structure, we should be able to find it.
Now `reload` the SBT shell one more time.
Then, `mecha-ls` shows that `examples-core-utils` is checked out (note the `*`):

    > mecha-ls
    [info] Superproject repositories:
    [info] [*] examples-core-utils
    [success] Total time: 0 s, completed May 23, 2015 12:40:52 AM

To avoid accidentally committing `examples-core-utils` to the super-repo,
we will add `examples-core-utils` to the `.gitignore` file.

The `examples-application` project uses `examples-core-utils` for its benchmarks.
Let's add `examples-application` to `repos.conf` too:

    ...
    examples-application {
      dir = "examples-application"
      origin = "git@github.com:storm-enroute/examples-application.git"
      mirrors = []
    }
    ...

And `reload` again -- `mecha-ls` now gives:

    [info] Superproject repositories:
    [info] [ ] examples-application
    [info] [*] examples-core-utils

Do `mecha-track`, add `examples-application` to `.gitignore` and `reload` once more,
and now you're tracking both projects.
Next, we will see how to pull from remote repositories.


#### Pulling

Pulling latest changes for all the projects is super-easy -- just do:

    > mecha-pull
    [info] Pull repo 'examples-core-utils' from origin...
    Already up-to-date.
    [info] Pull repo 'examples-application' from origin...
    Already up-to-date.

And all the subprojects are updated.
If some of the repos had pending changes, then the pull fails.

#### Committing

Let's make some changes.
Add a line `tmp/` to the `.gitignore` files in both `examples-core-utils`
and the `examples-application` projects.
Now run `mecha-status`:

    > mecha-status
    [info] ------ status for repo 'examples-core-utils' ------
    [info] On branch master
    [info] Your branch is up-to-date with 'origin/master'.
    [info] Changes not staged for commit:
    [info]   (use "git add <file>..." to update what will be committed)
    [info]   (use "git checkout -- <file>..." to discard changes in working directory)
    [info]
    [info]  modified:   .gitignore
    [info]
    [info] no changes added to commit (use "git add" and/or "git commit -a")
    [info] ------ end of status for repo 'examples-core-utils' ------
    [info] ------ status for repo 'examples-application' ------
    [info] On branch master
    [info] Your branch is up-to-date with 'origin/master'.
    [info] Changes not staged for commit:
    [info]   (use "git add <file>..." to update what will be committed)
    [info]   (use "git checkout -- <file>..." to discard changes in working directory)
    [info]
    [info]  modified:   .gitignore
    [info]
    [info] no changes added to commit (use "git add" and/or "git commit -a")
    [info] ------ end of status for repo 'examples-application' ------

Looks like we've got some pending changes in both subprojects.
We can also run 'mecha-diff' to precisely see the changes we made.
If we are happy with the changes, we can run `mecha-commit`.
This both stages the changes and commits them:

    > mecha-commit
    [info] --- diff for 'examples-core-utils' in 'examples-core-utils' ---
    [info] diff --git a/.gitignore b/.gitignore
    [info] index c58d83b..373b673 100644
    [info] --- a/.gitignore
    [info] +++ b/.gitignore
    [info] @@ -15,3 +15,5 @@ project/plugins/project/
    [info]  # Scala-IDE specific
    [info]  .scala_dependencies
    [info]  .worksheet
    [info] +
    [info] +tmp/
    [info] --- end of diff for 'examples-core-utils' in 'examples-core-utils' ---
    Commit message (empty aborts):

We can enter a message and the changes will be commited.
If you are not happy with the output of the diff, just enter an empty message.
We can do the same for the other repo -- Mecha will iterate through all the repos with
pending changes.
Once we're done, running `mecha-status` should report no pending changes.


#### Pushing

Once we committed the changes, we need to push them to origin.
For this, we run `mecha-push`:

    > mecha-push
    [info] Push 'examples-core-utils' to 'origin'...
    [info] Push 'examples-application' to 'origin'...
    [info] ------ examples-core-utils ------
    [info] To git@github.com:storm-enroute/examples-core-utils.git
    [info]    cd260d4..fb527d5  master -> master
    [info] ------ examples-application ------
    [info] To git@github.com:storm-enroute/examples-application.git
    [info]    edd27e1..5f6f5c6  master -> master
    [success] Total time: 2 s, completed May 23, 2015 2:04:10 AM

And that's it!


#### Branching and Merging

Let's say that we want to work on feature `gitignore`.
We would like to have a separate branch for that called `topic/gitignore`.
To simultaneously create a new branch in all the repositories,
run `mecha-new-branch`:

    > mecha-new-branch topic/gitignore
    [info] All repositories are at branch: master
    Branch in all repos? [y/n] y
    Switched to a new branch 'topic/gitignore'
    Switched to a new branch 'topic/gitignore'
    [success] Total time: 11 s, completed May 23, 2015 2:09:51 AM

Mecha will check if all the repositories are at the same branch, and ask you to confirm.
If you do, the new branches are created.
Let's check with `mecha-branch`:

    > mecha-branch
    [info] Repo 'examples-core-utils': topic/gitignore
    [info] Repo 'examples-application': topic/gitignore

To switch all projects back to master, just run `mecha-checkout`:

    > mecha-checkout
    Existing branch name: master
    Switched to branch 'master'
    Your branch is up-to-date with 'origin/master'.
    Switched to branch 'master'
    Your branch is up-to-date with 'origin/master'.

To merge the changes from `topic/gitignore` back to `master`, run `mecha-merge`:

    > mecha-merge
    [info] All repositories are at branch: master
    Merge in all repos? [y/n]y
    Existing branch name (empty aborts): topic/gitignore
    Already up-to-date.
    Already up-to-date.
    [success] Total time: 9 s, completed May 23, 2015 2:13:43 AM

You can alternatively specify the branch name directly in the command line.


#### Other Actions

You can additionally specify a list of mirrors in `repos.conf` for specific projects.
If you do, you will be able to pull from and push to all the mirrors with
`mecha-pull-mirror` and `mecha-push-mirror`.

You can also switch between projects with the `project` command.
Since the subprojects are just normal SBT projects, you can work on them directly.
Sometimes you need to do this to more easily invoked project-specific commands.

To switch between projects, you will need to specify their full path,
since the subprojects are defined as sbt `RootProject`s.

    project {file:/C:/cygwin/home/scala-fanboy/workspaces/scala/examples/mecha-super-repo/examples-core-utils}

Luckily, auto-complete's here to the rescue!


### Project Configuration and the Input Query DSL

In some projects, it is useful to have configuration files that the
users need to fill out before being able to build the project.
Examples of values in this configuration file include sensitive information such
as passwords, local paths or other dev-specific info that you do not want in source
control.
The common convention is to provide a template configuration that the devs must fill.
Sadly, the configuration template and the actual configuration can easily get
out-of-sync and the devs must remember to fill them out correctly each time.

Mecha has an `Input.Query` DSL that you can use to define these configuration files in
the subprojects.
This DSL automatically creates a configuration file generator that queries the user to
enter values for the config file (if the config file does not exist).
Let's say that `examples-application` needs to have a different `target` location
and a version `version`.
We first need to convert the `examples-application` build into a Mecha repo build.

1. Add Mecha to `project/plugins.sbt` in `examples-application`:

        resolvers ++= Seq(
          "Sonatype OSS Snapshots" at
            "https://oss.sonatype.org/content/repositories/snapshots",
          "Sonatype OSS Releases" at
            "https://oss.sonatype.org/content/repositories/releases"
        )
        
        addSbtPlugin("com.storm-enroute" % "mecha" % "0.2")

    If necessary, replace `0.2` with the latest Mecha version.

2. Add the following into the `project/Build.scala`:

        import org.stormenroute.mecha._
        import sbt._
        import sbt.Keys._
        
        object ExamplesApplicationBuild extends MechaRepoBuild {
          lazy val examplesApplicationSettings = Defaults.defaultSettings ++
            MechaRepoPlugin.defaultSettings ++ Seq(
            name := "examples-application",
            scalaVersion := "2.11.4",
            version := "0.1",
            organization := "com.storm-enroute",
            libraryDependencies ++=
              superRepoDependencies("examples-application"),
          )
        
          def repoName = "examples-application"

          lazy val examplesApplication: Project = Project(
            "examples-application",
            file("."),
            settings = examplesApplicationSettings
          ) dependsOnSuperRepo
        }

    Here, the crucial part is the `dependsOnSuperRepo` --
    **don't forget to add this or the dependencies won't be picked up!**
    The other crucial part is `libraryDependencies ++= superRepoDependencies`.
    For `repoName`, use the same name as in the `repos.conf` file from the super-repo.


3. Run `reload` in the SBT shell and you've got a Mecha repo build.

The `examples-application` can now do various powerful stuff.
Let's get back to our config files.
We add the following value to `project/Build.scala` in `examples-application`:

    import Input._
    val configQuery = {
      val target =
        const("file(\"/custom-target/\")")
          .map(("target", _))
      val version =
        stringQuery("Enter version: ")
          .map(v => "\"" + v + "\"")
          .map(("version", _))
      traverseFull(List(target, version))
    }

The above is a template for generating a configuration.
The `const` value will default its argument.
For each `stringQuery` value, the user will have to enter the value when SBT boots.
You can add more `val`s if you need more configuration values.

Importantly, add the following setting to `examplesApplicationSettings`:

    ...
    MechaRepoPlugin.configQueryKey := Some(configQuery),
    ...

Now `reload` the project, run `package` and see the magic happen:

    > package
    [warn] Populating configuration file.
    Enter version: 1.7.4
    [error] Created 'C:\cygwin\home\...\config.sbt'. Please reload!

The generated file is called `config.sbt`.
You can change the default name with the `MechaRepoPlugin.configFilePathKey` setting.


#### Whaa? How does this configuration input-query DSL work?

There is no need to understand this DSL to use it.
However, if you really want to know, read this section.

The `Input.Query` DSL relies heavily functional combinators to create queries and
generation values. It is based on the following type:

    type Query[T] = () => Option[T]

This reads "A query of type `T` is code that maybe produces a value of type `T`".
When we call `const(x)`, we produce a function that always returns `Some(x)`:

    def const[T](v: =>T): Query[T] = () => Some(v)

The `stringQuery` asks the user to produce a query -- it might return `None` if the
user enters an empty string.
Both `const` and `stringQuery` are basic combinators.

Complex combinators transform existing `Query` objects into more complex ones.
For example, the `map` that we used above transforms the value in the `Option` object
that `Query` could return if the `Option` is not `None`.
We used it to wrap the user's string into quotes.

Another example is `repeat` -- given a `Query` it returns another `Query` that repeats
the original query until the user enters an empty string.
Other combinators include `default`, `map`, `traverse`, `traverseFull`, `pair`, ...

The `configQueryKey` is a setting for values of the following type:

    Option[Query[Traversable[(String, String)]]]

This reads: an optional query that may return a traversable of string tuples.
These tuples are the key-value pairs that end up in the configuration objects.


### Inter-Project Dependencies

Now the main reason why we have Mecha -- projects can depend on each other,
and sometimes you don't want to wait until the snapshot lands on Maven
to get the updates from one project in another project.

In these cases, you define a dependency file inside your subproject.
The dependency file in project A can, for example, specify that project A
depends on some other project B.
If you run SBT from the superproject root directory,
Mecha will try to create a source code dependency on project B for project A.
If the project B is not tracked, Mecha will create a dependency on the Maven artifact instead.
Similarly, if you checkout project A without the superproject,
Mecha will create a regular Maven dependency.

In our example, `examples-application` project will depend on the `examples-core-utils`
project.
To express this dependency, we need to:

1. First remove the dependency on the other project
from the `libraryDependencies` setting, if there is one.

2. Next, create a `dependencies.conf` file in the root directory
of the `examples-application` project:

        examples-application {
          project = "examples-core-utils"
          artifact = ["com.storm-enroute", "examples-core-utils", "0.1"]
        }

    Alternatively, you can use JSON syntax, and a `dependencies.json` file.
    Simply override both `dependenciesPath` and `repositoriesPath`
    fields in the project definition,
    and add the following to the `dependencies.json` file:

        {
          "examples-application": [
            {
              "project": "examples-core-utils",
              "artifact": ["com.storm-enroute", "examples-core-utils", "0.1"]
            }
          ]
        }

Here, for every SBT project inside the `examples-application` subproject,
we specify a list if dependent projects.
In case the dependent projects are not checked out, we fall back to their published
artifacts.

We can also specify that the dependency is only on a specific project configuration:

    artifact = ["com.storm-enroute", "examples-core-utils", "0.1", "test"]

Now we can e.g. call methods from `examples-core-utils`
directly from `examples-application`.
Assume that we have the following in `examples-core-utils`:

    package com.stormenroute

    object Util {
      def version = println("core-utils v1.0")
    }

We add the file `src/main/scala/com/stormenroute/Main.scala`
to `examples-application`:

    package com.stormenroute

    object Main {
      def main(args: Array[String]) {
        println(Util.version)
      }
    }

And hit `compile`.


### Edit-Refresh Task

Mecha comes with a special `mechaEditRefresh` task that supports fast edit-refresh
cycles across projects.
Essentially, calling this task in the super-repo depends on completing that task in all
the subprojects.
For specific Mecha subprojects, make sure that this task is overridden, for example:

    mechaEditRefreshKey <<= mechaEditRefreshKey.dependsOn(compile in Compile)

Then, in SBT shell do:

    > ~mecha-edit-refresh

And code the night away.


### Automated Docs Publishing

Mecha supports automated publishing of your project ScalaDoc to a target Git repo.
To enable this, you need to specify these settings:

    ...
    mechaDocsRepoKey := "url of the git repo for hosting the docs",
    mechaDocsBranchKey := "branch in the remote git repo",
    mechaDocsPathKey := "the path in the repo where the docs should be put",
    ...

Running the `mecha-publish-docs` command from the subproject will:

1. Build the docs.
2. Check out the specified branch of the Git url into a temporary directory.
3. Copy the docs into the path specified in `mechaDocsPathKey`, suffixed with version.
4. **Amend the last commit and force-push back to the remote Git repo.**

The fun part with this command is that if you use GitHub and specify a `gh-pages`
branch, your docs will be automatically hosted for the whole world to see...
So, you get ScalaDoc hosting for free.


### Automated Benchmark Publishing

Automated benchmark publishing is very similar to docs publishing,
and is triggered with `mecha-publish-benches` in the subproject.
You will need to specify these settings:

    ...
    mechaBenchRepoKey := "url of the git repo",
    mechaBenchBranchKey := "branch in the git repo",
    mechaBenchPathKey := "subdirectory in the git repo",
    mechaBenchSrcPathKey := (baseDirectory.value / "target/benchmarks").toString,
    ...

See descriptions of these settings for more info.


### Automated Build Deployment

Automated build deployment is similar to docs and benchmark publishing,
and is triggered with `mecha-publish-build` in the subproject.
The difference is that each build is deployed to a subdirectory
with a timestamp and SHA-1 has of the commit.
You will need to specify these settings:

    ...
    mechaBuildOutputRepoKey := "url of the git repo where builds are copied",
    mechaBuildOutputBranchKey := "branch in the git repo",
    mechaBuildOutputPathKey := "subdirectory in the git repo",
    mechaBuildOutputSrcPathKey := (baseDirectory.value / "target").toString,
    mechaBuildOutputExpirationDaysKey := 5, // number of days builds before removing old builds
    ...

See the descriptions of these settings for more info.


### Nightly Task

Mecha has another special task called `mechaNightly`.
By default, this task depends on packaging,
testing, publishing the project, publishing the docs and publishing the benchmarks.
If you want to extend this, add more dependencies for this task *inside the subproject*.

Calling `mecha-nightly` in the super-repo depends on all the Mecha subprojects.


### Automated Remote Deployment

In case you're working on a project that needs to be frequently deployed
to a different host during development,
Mecha exposes the `mechaSshDeploy` command to speed this up.
You will need to specify the following settings:

    ...
    remoteSshHost := None,
    remoteSshUser := "admin",
    remoteSshPass := None,
    remoteDeployPathKey := "~",
    remoteDeployCmdKey := None,
    ...

Calling `mecha-ssh-deploy` from within the subproject will:

1. Connect to the remote host, using the specified credentials.
2. Pull the project into the specified path.
3. Optionally run the bash command from `remoteDeployCmdKey` in the project root.

Note: commit your changes before deploying them at the remote host -- the project is
pulled at the remote location, but there is no local automatic commit/push.

Some of these settings, like `remoteSshPass` are ideal candidates for the Input Query
DSL and the automated project configuration.
