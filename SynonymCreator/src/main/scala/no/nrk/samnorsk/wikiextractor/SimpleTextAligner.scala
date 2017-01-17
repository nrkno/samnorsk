package no.nrk.samnorsk.wikiextractor

import scala.collection.mutable
import scala.util.control.Breaks._
import scala.util.matching.Regex

object SimpleTextAligner {
  def tokenDiscrepancy(fromText: String, toText: String): Map[String, String] = {
    val re: Regex = """\w+""".r
    val fromTokens = re.findAllIn(fromText).toSeq
    val toTokens = re.findAllIn(toText).toSeq
    val substitutions = mutable.HashMap.empty[String, String]

    if (fromTokens.size == toTokens.size) {
      fromTokens.zip(toTokens)
        .filter(p => !p._1.equalsIgnoreCase(p._2))
        .map(p => substitutions.put(p._1.toLowerCase, p._2.toLowerCase)).toList
    }
    else {
      breakable {
        var prev = false

        for ((fromTerm, i) <- fromTokens.zipWithIndex) {
          val toTerm = toTokens(i)

          if (fromTerm.equalsIgnoreCase(toTerm)) {
            prev = false
          }
          else {
            if (prev) {
              break
            }

            substitutions.put(fromTerm.toLowerCase, toTerm.toLowerCase)

            prev = true
          }
        }
      }
    }

    Map(substitutions.toSeq:_*)
  }
}
