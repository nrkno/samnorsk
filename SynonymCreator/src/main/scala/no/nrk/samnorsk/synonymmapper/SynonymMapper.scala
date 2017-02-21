package no.nrk.samnorsk.synonymmapper

import java.io._
import java.lang.Thread.UncaughtExceptionHandler

import com.typesafe.scalalogging.slf4j.LazyLogging
import info.debatty.java.stringsimilarity.JaroWinkler
import no.nrk.samnorsk.util.{IOUtils, JsonWrapper}
import no.nrk.samnorsk.wikiextractor.WikiExtractor.ArticleAndTranslation
import no.nrk.samnorsk.wikiextractor.{Bokmaal, Nynorsk}
import scopt.RenderingMode.TwoColumns

import scala.annotation.tailrec
import scala.collection.mutable
import scala.io.{Codec, Source}

object SynonymMapper extends LazyLogging {

  case class Mapping(source: String, target: String)
  case class WordAndFrequency(word: String, mappingFrequency: Int)

  case class SynonymLine(synonyms: Seq[String], canonicalForm: String) {

    def getReductionSynonyms = {
      synonyms.mkString("", ",", " => ") + canonicalForm
    }

    def getExpansionSynonyms = {
      (synonyms :+ canonicalForm).mkString(",")
    }
  }

  val TokenizerRegex = """([A-Za-zØÆÅøæåéÉàÀôÔòÒ*]+)""".r
  val JaroWinler = new JaroWinkler()

  def getArticleMappings(nynorskArticle: String, bokmaalArticle: String): Seq[Mapping] = {

    @tailrec
    def getSentenceMappingRec(mappingsQueue: Seq[Mapping], consecutiveDissimilarities: Int, mappingFinished: Seq[Mapping]): Seq[Mapping] = {
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

    val nynorskSentences = nynorskArticle.split("\\.")
    val bokmaalSentences = bokmaalArticle.split("\\.")

    val mappingsInArticle = nynorskSentences.zip(bokmaalSentences).flatMap { case (nynorskSentence, bokmaalSentence) =>
      val nynorskTokens = TokenizerRegex.findAllIn(nynorskSentence).toSeq
      val bokmaalTokens = TokenizerRegex.findAllIn(bokmaalSentence).toSeq

      val sentenceMapping = if (nynorskTokens.size == bokmaalTokens.size) {
        val mappings: Seq[Mapping] = nynorskTokens.zip(bokmaalTokens)

          // Words out of voc have been annotated with * prefix.
          .filter { case (nynorskWord, bokmaalWord) => !nynorskWord.contains('*') && !bokmaalWord.contains('*') }
          .map { case (nynorskWord, bokmaalWord) => Mapping(nynorskWord.toLowerCase, bokmaalWord.toLowerCase) }

        getSentenceMappingRec(mappings, 0, Seq())
      } else {
        Seq()
      }
      sentenceMapping
    }
    mappingsInArticle
  }

  def getCorpusMapping(translations: File) = {

    val mappings = Source.fromFile(translations)(codec = Codec.UTF8).getLines()
      .map(line => JsonWrapper.convert(line, classOf[ArticleAndTranslation]))
      .grouped(1000).flatMap(group => {
      group.par.flatMap(article => {
        val sentenceMappings = article match {
          case wikiArticle if wikiArticle.fromLanguage == Nynorsk.Name => getArticleMappings(wikiArticle.original, wikiArticle.translation)
          case wikiArticle if wikiArticle.fromLanguage == Bokmaal.Name => getArticleMappings(wikiArticle.translation, wikiArticle.original)
          case _ => throw new IllegalArgumentException("Invalid input")
        }
        sentenceMappings
      })
    })
    mappings
  }

  def createSynonymsFromFrequencies(frequencyMap: Map[Mapping, Int]): Seq[SynonymLine] = {

    def aggregateFrequencies(frequencyMap: Map[Mapping, Int], cutoff: Int = 5): Map[String, Seq[WordAndFrequency]] = {

      val createInsert = (map: mutable.Map[String, Seq[WordAndFrequency]], mapping: Mapping, frequency: Int) =>
        map += (mapping.source -> (map.getOrElse(mapping.source, Seq()) :+ WordAndFrequency(mapping.target, frequency)))

      frequencyMap
        .filter(x => x._2 > cutoff)
        .foldLeft(mutable.Map.empty[String, Seq[WordAndFrequency]]) { (agg, mappingAndFreq) => createInsert(agg, mappingAndFreq._1, mappingAndFreq._2) }
        .toMap
    }

    val reverseFreqMap = frequencyMap.toSeq
      .map { case (mapping, frequency) => (Mapping(mapping.target, mapping.source), frequency) }
      .toMap

    val reverseAggregatedMap = aggregateFrequencies(reverseFreqMap)

    def isMostFrequentTranslation(candidate: String, source: String) = {
      val wordsAndFreqForCandidate = reverseAggregatedMap.getOrElse(candidate, Seq()).sortBy(_.mappingFrequency).reverse
      wordsAndFreqForCandidate.head.word == source
    }

    /**
      * Only keep the synonyms which most frequently map to the source word.
      */
    val filteredAggregationMap: Map[String, Seq[WordAndFrequency]] = aggregateFrequencies(frequencyMap)
      .map { case (sourceword, wordsAndFrequencies) =>
        val filtered = wordsAndFrequencies.filter(x => isMostFrequentTranslation(x.word, sourceword))
        (sourceword, filtered)
      }

    /**
      * Keep the synonym candidates which are frequent enough, compared to the most frequent mapping. Don't allow candidate words to
      * appear both on the lhs and rhs.
      */
    def getSynonymsAboveRelativeFreqThreshold(candidates: Seq[WordAndFrequency], highestTranslationFrequency: Int, threshold: Double = 0.3) = {
      candidates
        .filter(x => x.mappingFrequency > highestTranslationFrequency * threshold)
        .filter(x => filteredAggregationMap.getOrElse(x.word, Seq()).isEmpty)
    }

    def getReverseMappedSynonyms(sourceWord: String) = {
      val synonymCandidates = reverseAggregatedMap.getOrElse(sourceWord, Seq())
      if (synonymCandidates.nonEmpty) {
        val highestFrequency = synonymCandidates
          .sortBy(_.mappingFrequency)
          .last
          .mappingFrequency

        getSynonymsAboveRelativeFreqThreshold(synonymCandidates, highestFrequency)
      } else {
        Seq()
      }
    }

    val synonyms: Map[String, Seq[WordAndFrequency]] = filteredAggregationMap
      .filter(x => x._2.nonEmpty)
      .map { case (sourceWord, wordsAndFreqs) =>
        val sorted = wordsAndFreqs.sortBy(_.mappingFrequency).reverse
        val highestFrequency = sorted
          .head
          .mappingFrequency

        val mappingsWithHighFrequencies = getSynonymsAboveRelativeFreqThreshold(sorted, highestFrequency)

        (sourceWord, (mappingsWithHighFrequencies ++ getReverseMappedSynonyms(sourceWord)).distinct)
      }

    synonyms.map(x => SynonymLine(x._2.map(_.word), x._1))
      .map(synonymLine => SynonymLine(synonymLine.synonyms.filter(x => x != synonymLine.canonicalForm).distinct, synonymLine.canonicalForm))
      .filter(synonymLine => synonymLine.synonyms.nonEmpty)
      .toSeq
  }

  def writeSynonyms(synonymLines: Seq[SynonymLine], output: File, expansion: Boolean) = {
    IOUtils.wipeAndCreateNewFile(output)
    synonymLines.map(x => if (expansion) x.getExpansionSynonyms else x.getReductionSynonyms)
      .grouped(1000)
      .foreach(lines => IOUtils.writeOutput(lines, output))
  }

  def main(args: Array[String]): Unit = {
    case class Config(trans: String = "", output: String = "", reduction: Option[String] = Some(Nynorsk.Name))

    Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler {
      override def uncaughtException(t: Thread, e: Throwable): Unit = {
        logger.error(s"Uncaught exception, exiting. ${e.getMessage}")
        sys.exit(1)
      }
    })

    val parser = new scopt.OptionParser[Config]("SynonymMapper") {
      head("SynonymMapper", "0.1.0")

      opt[String]('t', "trans")
        .action((x, c) => c.copy(trans = x))
        .text("Translation input file.")
        .required()
      opt[String]('o', "output")
        .action((x, c) => c.copy(output = x))
        .text("Synonym output file.")
        .required()
      opt[String]('r', "reduction")
        .action((x, c) => c.copy(reduction = Some(x)))
        .text("Synonym reduction language.")
    }

    parser.parse(args, Config()) match {
      case Some(config) =>
        val input = config.trans
        val output = config.output
        val reductionLanguage = config.reduction

        val mappings = getCorpusMapping(new File(input))
          .map(mapping => {
            reductionLanguage match {
              case Some(language) if language == Bokmaal.Name => Mapping(mapping.target, mapping.source)
              case _ => mapping
            }
          })

        val frequencyMap = mappings
          .foldLeft(mutable.Map.empty[Mapping, Int]) { (acc, mapping) => acc += mapping -> (acc.getOrElse(mapping, 0) + 1) }
          .toMap

        val synonyms = createSynonymsFromFrequencies(frequencyMap)
        writeSynonyms(synonyms, new File(output), expansion = reductionLanguage.isEmpty)
      case None =>
        parser.renderUsage(TwoColumns)
    }
  }
}
