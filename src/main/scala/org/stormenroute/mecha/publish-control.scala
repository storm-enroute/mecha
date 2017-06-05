package org.stormenroute.mecha



import better.files._



object Publish {
  def update(
    log: sbt.Logger, projectName: String, version: String,
    scalaVersion: String, repoGitUrl: String, branch: String, contentSubDir: String,
    contentSourcePath: String, removeDirsBeforeDate: String
  ): Unit = {
    val workingDir = File.newTemporaryDirectory()
    val workingDirString = workingDir.pathAsString
    try {
      require(Git.clone(repoGitUrl, workingDirString))
      require(Git.fetchAll(workingDirString))
      require(Git.checkout(workingDirString, branch))
      if ((workingDir / contentSubDir).exists) {
        log.info(s"Deleting contents of $contentSubDir")
        (workingDir / contentSubDir).delete()
      }
      log.info(s"Creating $contentSubDir")
      (workingDir / contentSubDir).createDirectories()
      log.info(s"Copying from $contentSourcePath to $contentSubDir")
      File(contentSourcePath).copyTo(workingDir / contentSubDir)
      log.info(s"Adding and pushing updated content to remote.")
      require(Git.addAll(workingDirString))
      require(Git.amendCommit(workingDirString, "Update content."))
      require(Git.forcePush(workingDirString, "origin", branch))
    } finally {
      workingDir.delete()
    }
  }
}
