package no.nrk.samnorsk.synonymmapper

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import scala.sys.process._
import scala.util.matching.Regex

abstract class ApertiumRunner(val fromLanguage: Language, val toLanguage:Language) {
  private val separator = "☃☃¤"

  def translate(text: String): String

  def translate(texts: Traversable[String]): Traversable[String] = {
    translate(texts.mkString(separator)).split(separator)
  }
}

class LocalApertiumRunner(fromLanguage: Language, toLanguage: Language) extends ApertiumRunner(fromLanguage, toLanguage) {
  override def translate(text: String): String = {
    val tempFile = File.createTempFile("apertium-input", fromLanguage.Name)

    try {
      Files.write(tempFile.toPath, text.getBytes(StandardCharsets.UTF_8))
      s"apertium ${fromLanguage.Apertium}-${toLanguage.Apertium} ${tempFile.getAbsolutePath}".!!.trim
    } finally {
      Files.delete(tempFile.toPath)
    }
  }
}

// not tested, may not work
class RemoteApertiumRunner(fromLanguage: Language, toLanguage: Language, user: String, server: String, keyFile: Path)
  extends ApertiumRunner(fromLanguage, toLanguage) {
  override def translate(text: String): String = {
    s"""ssh $user@$server "echo \\"$text\\" | apertium ${fromLanguage.Apertium}-${toLanguage.Apertium}"""".!!.trim
  }
}

class StubApertiumRunner(substitutions: Map[String, String])
  extends ApertiumRunner(fromLanguage = Nynorsk, toLanguage = Bokmaal) {
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
