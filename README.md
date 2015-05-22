
![Mecha](/docs/mecha-logo-256-soft.png)

### Mecha -- SBT plugin that automates development workflow.

Ask yourself:

- Did you ever notice yourself working on 10 different projects that depend on each
  other?

- Is publishing a snapshots artifact from one project to compile another project killing
  your productivity?

- Did you ever want to simultaneously work on different Git code repositories as if they
  were one big project, but commit changes to them separately?

- Is automated nightly documentation publishing something you crave for?

If so, you're at the right place.
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

We took three steps in creating this super-repo:

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

3. Define a super-repo build as in the following example:

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

4. Last, creates a `repos.json` file in the super-repo root directory.
This file contains information about subprojects in this super-repo.
Initially, we can just keep add the super-project to it:

        {}

    The `repos.json` file is called the *super-repo configuration file*.
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
`repos.json`.
If we run it for an empty configuration file, we get:

    > mecha-ls
    [info] Superproject repositories:
    [success] Total time: 0 s, completed May 23, 2015 12:24:44 AM

So, let's add some projects.
Assume that you have a project `examples-core-utils`, which contains core utilities.
You would like to make changes its nightly version,
but you want to use them right away (to verify that your changes work).
The solution is to include `examples-core-utils` to our super-repo config in
`repos.json` (use your own fork for `origin` below):

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
The super-project is listing one registered subproject.
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
Let's add `examples-application` to `repos.json` too:

    ...
    "examples-application": {
      "dir": "examples-application",
      "origin": "git@github.com:storm-enroute/examples-application.git",
      "mirrors": []
    },
    ...

And `reload` again -- `mecha-ls` now gives:

    [info] Superproject repositories:
    [info] [ ] examples-application
    [info] [*] examples-core-utils

Do `mecha-track`, add `examples-application` to `.gitignore` and `reload` once more,
and now you're tracking both projects.
Next, we will see how to pull from remote repositories.


#### Pulling


#### Branching

#### Merging

#### Committing

#### Other Actions


### Project Configuration API


### Edit-Refresh Task


### Nightly Task


### Automated Docs Publishing


### Automated Benchmark Publishing


### Automated Remote Deployment

