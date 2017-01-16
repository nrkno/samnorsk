package no.nrk.samnorsk.wikiextractor

import scala.util.matching.Regex

trait ApertiumRunner {
  def translate(text: String): String
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
