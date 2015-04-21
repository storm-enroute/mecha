
![Mecha](/docs/mecha-logo-256-soft.png)

## Mecha

SBT plugin that automates development workflow.

Mecha uses the concept of a *super-repository*.
A super-repository is a repository in which there are multiple source controlled
repositories for different projects.
Repositories with the super-repository can have dependencies on each other, can
depend on each others' source code, or Maven artifacts.
All the repositories can be worked on, built, tested,
and committed simulatenously.

Mecha is an ideal solution when you want to compile your projects against the
latest version of fast moving dependencies, as the source codes of all your
projects get pulled, compiled, committed, and pushed together.
Guaranteed to boost productivity!

- supports super-repositories that contain multiple child repositories
- bulk pull, branch, merge, push, and mirroring for child repositories
- optional tracking for child repositories
- automatic source/library dependency configuration in child repositories
- automatic configuration file generation
- automated remote deploys over SSH

In the following, we describe the main features of Mecha.


### Super-Repo Configuration

WIP


### Git Workflow

#### Optional Tracking

#### Pulling

#### Branching

#### Merging

#### Committing

#### Other Actions


### Project Configuration API


### Automated Remote Deployment

