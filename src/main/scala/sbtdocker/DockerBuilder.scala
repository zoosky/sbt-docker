package sbtdocker

import sbt._
import scala.sys.process.{Process, ProcessLogger}
import scala.sys.error
import sbtdocker.Dockerfile.{StageDir, CopyPath}

object DockerBuilder {
  /**
   * Build a Dockerfile using a provided docker binary.
   *
   * @param dockerPath path to the docker binary
   * @param dockerFile Dockerfile to build
   * @param imageName name of the resulting image
   * @param stageDir stage dir
   * @param log logger
   */
  def apply(dockerPath: String, dockerFile: Dockerfile, imageName: String, stageDir: StageDir, log: Logger) = {
    log.info(s"Creating docker image with name: '$imageName'")

    prepareFiles(dockerFile, stageDir, log)

    buildImage(dockerPath, imageName, stageDir, log)
  }

  def prepareFiles(dockerFile: Dockerfile, stageDir: StageDir, log: Logger) = {
    log.debug(s"Preparing stage directory '${stageDir.file.getPath}'")

    IO.delete(stageDir.file)

    IO.write(stageDir.file / "Dockerfile", dockerFile.toInstructionsString)
    copyFiles(dockerFile.pathsToCopy, stageDir, log)
  }

  def copyFiles(pathsToCopy: Seq[CopyPath], stageDir: StageDir, log: Logger) = {
    for (CopyPath(source, targetRelative) <- pathsToCopy) {
      val target = stageDir.file / targetRelative.getPath
      log.debug(s"Copying '${source.getPath}' to '${target.getPath}'")

      if (target.exists()) {
        error(s"""Path "${target.getPath}" already exists in stage directory""")
      }

      if (source.isFile) {
        IO.copyFile(source, target, preserveLastModified = true)
      } else if (source.isDirectory) {
        IO.copyDirectory(source, target, overwrite = false, preserveLastModified = true)
      }
    }
  }

  def buildImage(dockerPath: String, imageName: String, stageDir: StageDir, log: Logger) = {
    val processLog = ProcessLogger({ line =>
      log.info(line)
    })

    val command = Seq(dockerPath, "build", "-t", imageName, ".")
    log.debug(s"Running command: '${command.mkString(" ")}' in '${stageDir.file.absString}'")
    Process(command, stageDir.file) ! processLog match {
      case 0 => log.info(s"Successfully built docker image: $imageName")
      case n => error(s"Error when building Dockerfile, exit code: $n")
    }
  }
}
