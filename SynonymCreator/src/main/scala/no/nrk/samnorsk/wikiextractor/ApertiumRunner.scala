package no.nrk.samnorsk.wikiextractor

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import scala.sys.process._
import scala.util.matching.Regex

trait ApertiumRunner {
  def translate(text: String): String
}

// not tested, may not work
class LocalApertiumRunner(fromLanguage: String, toLanguage: String) extends ApertiumRunner {
  override def translate(text: String): String = {
    val tempFile = File.createTempFile("apertium-input", fromLanguage)

    try {
      Files.write(tempFile.toPath, text.getBytes(StandardCharsets.UTF_8))
      s"apertium $fromLanguage-$toLanguage ${tempFile.getAbsolutePath}".!!.trim
    } finally {
      Files.delete(tempFile.toPath)
    }
  }
}

// not tested, may not work
class RemoteApertiumRunner(fromLanguage: String, toLanguage: String, user: String, server: String, keyFile: Path)
  extends ApertiumRunner {
  override def translate(text: String): String = {
    s"""ssh $user@$server "echo \\"$text\\" | apertium $fromLanguage-$toLanguage"""".!!.trim
  }
}

class StubApertiumRunner(substitutions: Map[String, String]) extends ApertiumRunner {
  override def translate(text: String): String = {
    val re: Regex = """\w+""".r
    val trans: StringBuilder = new StringBuilder()
    var last = 0

    for (m <- re.findAllIn(text).matchData) {
      if (substitutions.contains(m.group(0))) {
        if (m.start > last) {
          trans.append(text.substring(last, m.start))
          trans.append(substitutions(m.group(0)))
          last = m.end
        }
      }
    }

    trans.append(text.substring(last))

    trans.mkString
  }
}
