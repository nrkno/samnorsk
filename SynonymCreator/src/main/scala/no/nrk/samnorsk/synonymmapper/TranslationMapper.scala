package no.nrk.samnorsk.synonymmapper

import info.debatty.java.stringsimilarity.JaroWinkler
import no.nrk.samnorsk.synonymmapper.SynonymMapper.{Mapping, StringMapping}

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks.{break, breakable}
import scala.util.matching.Regex

trait TranslationMapper[A, B] {
  def map(source: A, trans: B): Seq[Mapping[A, B]]
}

class LinearMapper extends TranslationMapper[String, String] {
  private val re = """([A-Za-zØÆÅøæåéÉàÀôÔòÒ]+)""".r

  override def map(source: String, trans: String): Seq[StringMapping] = {
    val fromTokens = re.findAllIn(source).map(_.toLowerCase).toSeq
    val toTokens = re.findAllIn(trans).map(_.toLowerCase).toSeq

    if (fromTokens.size == toTokens.size) {
      fromTokens.zip(toTokens)
        .filter(p => !p._1.equalsIgnoreCase(p._2))
        .map(p => Mapping(p._1, p._2))
    }
    else {
      val substitutions = ArrayBuffer[StringMapping]()

      breakable {
        var prev = false

        for ((fromTerm, i) <- fromTokens.zipWithIndex) {
          if (i < toTokens.size) {
            val toTerm = toTokens(i)

            if (fromTerm.equalsIgnoreCase(toTerm)) {
              prev = false
            }
            else {
              if (prev) {
                break
              }

              substitutions += Mapping(fromTerm.toLowerCase, toTerm.toLowerCase)

              prev = true
            }
          }
        }
      }

      substitutions
    }
  }
}

class EditDistanceMapper extends TranslationMapper[String, String] {
  val TokenizerRegex: Regex = """([A-Za-zØÆÅøæåéÉàÀôÔòÒ*]+)""".r
  val JaroWinler = new JaroWinkler()

  override def map(source: String, trans: String): Seq[StringMapping] = {
    @tailrec
    def getSentenceMappingRec(mappingsQueue: Seq[StringMapping], consecutiveDissimilarities: Int, mappingFinished: Seq[StringMapping]): Seq[StringMapping] = {
      if (consecutiveDissimilarities == 3 ) {
        Seq()
      } else if (mappingsQueue.isEmpty) {
        mappingFinished
      } else {
        val mapping = mappingsQueue.head
        val dissimilarities = {
          if (JaroWinler.similarity(mapping.source, mapping.target) < 0.8) {
            consecutiveDissimilarities + 1
          } else {
            0
          }
        }
        getSentenceMappingRec(mappingsQueue.tail, dissimilarities, mappingFinished :+ mapping)
      }
    }

    val sourceSentences = source.split("\\.")
    val transSentences = trans.split("\\.")

    val mappings = sourceSentences.zip(transSentences).flatMap { case (sourceSentence, transSentence) =>
      val sourceTokens = TokenizerRegex.findAllIn(sourceSentence).toSeq
      val transTokens = TokenizerRegex.findAllIn(transSentence).toSeq

      val sentenceMapping = if (sourceTokens.size == transTokens.size) {
        val mappings: Seq[StringMapping] = sourceTokens.zip(transTokens)

          // Words out of voc have been annotated with * prefix.
          .filter { case (sourceWord, transWord) => !sourceWord.contains('*') && !transWord.contains('*') }
          .map { case (sourceWord, transWord) => Mapping(sourceWord.toLowerCase, transWord.toLowerCase) }

        getSentenceMappingRec(mappings, 0, Seq())
      } else {
        Seq()
      }

      sentenceMapping
    }

    mappings
  }
}