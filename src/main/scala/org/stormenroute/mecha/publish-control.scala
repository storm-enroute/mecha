package org.stormenroute.mecha



import better.files._



object Publish {
  def push(
    log: sbt.Logger, projectName: String, version: String,
    scalaVersion: String, repoGitUrl: String, branch: String, contentSubDir: String,
    contentSourcePath: String, removeDirsBeforeDate: String
  ): Unit = {
    val workingDir = File.newTemporaryDirectory()
    val workingPath = workingDir.pathAsString
    try {
      log.info(s"Pushing content for $projectName")
      require(Git.clone(repoGitUrl, workingPath))
      require(Git.fetchAll(workingPath))
      require(Git.checkout(workingPath, branch))
      if ((workingDir / contentSubDir).exists) {
        log.info(s"Deleting contents of $contentSubDir")
        (workingDir / contentSubDir).delete()
      }
      log.info(s"Creating $contentSubDir")
      (workingDir / contentSubDir).createDirectories()
      if (File(contentSourcePath).exists) {
        log.info(s"Copying from $contentSourcePath to $contentSubDir")
        File(contentSourcePath).copyTo(workingDir / contentSubDir)
      } else {
        log.info(s"No content found in $contentSourcePath")
      }
      log.info(s"Adding and pushing updated content to remote.")
      require(Git.addAll(workingPath))
      require(Git.amendCommit(workingPath, "Update content."))
      require(Git.forcePush(workingPath, "origin", branch))
    } finally {
      workingDir.delete()
    }
  }

  def pull(
    log: sbt.Logger, projectName: String, repoGitUrl: String, branch: String,
    contentSubDir: String, contentSourcePath: String
  ): Unit = {
    val workingDir = File.newTemporaryDirectory()
    val workingPath = workingDir.pathAsString
    val contentSourceDir = File(contentSourcePath)
    try {
      log.info(s"Pulling content for $projectName")
      require(Git.clone(repoGitUrl, workingPath))
      require(Git.fetchAll(workingPath))
      require(Git.checkout(workingPath, branch))
      if (contentSourceDir.exists) {
        log.info(s"Deleting contents of $contentSourceDir")
        contentSourceDir.delete()
      }
      log.info(s"Creating $contentSourceDir")
      contentSourceDir.createDirectories()
      if ((workingDir / contentSubDir).exists) {
        log.info(s"Copying from $contentSubDir to $contentSourceDir")
        (workingDir / contentSubDir).copyTo(contentSourceDir)
      }
    } finally {
      workingDir.delete()
    }
  }
}
