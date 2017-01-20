package no.nrk.samnorsk.util

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, StandardOpenOption}


object IOUtils {

  def wipeAndCreateNewFile(file: File) = {
    if (file.exists()) {
      Files.delete(file.toPath)
    }
    file.createNewFile()
  }

  def writeOutput[T](lines: Seq[T], outputFile: File) = {
    outputFile.synchronized {
      for (article <- lines) {
        Files.write(outputFile.toPath, (JsonWrapper.convertToString(article) + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND)
      }
    }
  }
}
