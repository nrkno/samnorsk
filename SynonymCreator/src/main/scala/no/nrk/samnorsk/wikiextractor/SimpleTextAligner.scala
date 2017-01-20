package no.nrk.samnorsk.wikiextractor

import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks._
import scala.util.matching.Regex

object SimpleTextAligner {
  def tokenDiscrepancy(fromText: String, toText: String): Seq[(String, String)] = {
    val re: Regex = """\w+""".r
    val fromTokens = re.findAllIn(fromText).toSeq
    val toTokens = re.findAllIn(toText).toSeq

    if (fromTokens.size == toTokens.size) {
      fromTokens.zip(toTokens).filter(p => !p._1.equalsIgnoreCase(p._2)).toList
    }
    else {
      val substitutions = ArrayBuffer[(String, String)]()

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

            substitutions += ((fromTerm.toLowerCase, toTerm.toLowerCase))

            prev = true
          }
        }
      }

      substitutions
    }
  }
}
