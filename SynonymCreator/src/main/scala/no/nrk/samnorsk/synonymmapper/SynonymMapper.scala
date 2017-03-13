package no.nrk.samnorsk.synonymmapper

import java.io._
import java.lang.Thread.UncaughtExceptionHandler
import java.util.zip.GZIPInputStream

import com.typesafe.scalalogging.slf4j.LazyLogging
import no.nrk.samnorsk.util.IOUtils
import no.nrk.samnorsk.wikiextractor._
import scopt.RenderingMode.TwoColumns

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.io.{Codec, Source}

// TODO expansion/reduction, filtering, output format

case class DumpDescription(dump: File, fromLanguage: Language, toLanguage: Language)

case class Processor(iter: WikiIterator, runner: ApertiumRunner, counter: TranslationCounter[String, String],
                     fromLanguage: Language, toLanguage: Language)

object SynonymMapper extends LazyLogging {

  case class Mapping[A, B](source: A, target: B)

  type StringMapping = Mapping[String, String]

  case class WordAndFrequency(word: String, mappingFrequency: Int)

  case class SynonymLine(synonyms: Seq[String], canonicalForm: String) {

    def getReductionSynonyms: String = {
      synonyms.mkString("", ",", " => ") + canonicalForm
    }

    def getExpansionSynonyms: String = {
      (synonyms :+ canonicalForm).mkString(",")
    }
  }

  def processDumps(dumps: Seq[DumpDescription], limit: Option[Int] = None): Seq[TranslationCounter[String, String]] = {
    val mapper = new EditDistanceMapper

    val procs = dumps.map(d => Processor(iter = new WikiIterator(Source.fromInputStream(new GZIPInputStream(new FileInputStream(d.dump)))(Codec.UTF8), limit = limit),
      runner = new LocalApertiumRunner(fromLanguage = d.fromLanguage, toLanguage = d.toLanguage),
      counter = new TranslationCounter[String, String](),
      fromLanguage = d.fromLanguage,
      toLanguage = d.toLanguage))

    procs.foreach(p => {
      p.iter.grouped(1000).toStream.par.foreach(articles => {
        p.runner.translate(articles)
          .toSeq.zip(articles)
          .map(at => mapper.map(at._2, at._1).filter(m => m.source != m.target))
          .foreach(m => p.counter.update(m))
      })
    })

    procs.foreach(_.iter.source.close())

    procs.map(_.counter)
  }

  def createSynonymsFromFrequencies(frequencyMap: Map[StringMapping, Int]): Seq[SynonymLine] = {

    def aggregateFrequencies(frequencyMap: Map[StringMapping, Int], cutoff: Int = 5): Map[String, Seq[WordAndFrequency]] = {

      val createInsert = (map: mutable.Map[String, Seq[WordAndFrequency]], mapping: StringMapping, frequency: Int) =>
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

  def writeSynonyms(synonymLines: Seq[SynonymLine], output: File, expansion: Boolean): Unit = {
    IOUtils.wipeAndCreateNewFile(output)
    synonymLines.map(x => if (expansion) x.getExpansionSynonyms else x.getReductionSynonyms)
      .grouped(1000)
      .foreach(lines => IOUtils.writeOutput(lines, output))
  }

  def main(args: Array[String]): Unit = {
    case class Config(nnDump: Option[DumpDescription] = None, nbDump: Option[DumpDescription] = None,
                      output: String = "", reduction: Option[String] = Some(Nynorsk.Name), limit: Option[Int] = None)

    Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler {
      override def uncaughtException(t: Thread, e: Throwable): Unit = {
        logger.error(s"Uncaught exception, exiting. ${e.getMessage}")
        sys.exit(1)
      }
    })

    val parser = new scopt.OptionParser[Config]("SynonymMapper") {
      head("SynonymMapper", "0.1.0")

      opt[String]('n', "nn-dump")
        .action((x, c) =>
          c.copy(nnDump = Some(DumpDescription(dump = new File(x), fromLanguage = Nynorsk, toLanguage = Bokmaal))))
        .text("Nynorsk Wikipedia Cirrus dump file.")

      opt[String]('b', "nb-dump")
        .action((x, c) =>
          c.copy(nbDump = Some(DumpDescription(dump = new File(x), fromLanguage = Bokmaal, toLanguage = Nynorsk))))
        .text("Nynorsk Wikipedia Cirrus dump file.")

      opt[Int]('l', "limit")
        .action((x, c) => c.copy(limit = Some(x)))
        .text("Process this number of articles.")

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
        val output = config.output
        val reductionLanguage = config.reduction

        var dumpDescr = ListBuffer.empty[DumpDescription]

        config.nnDump.foreach(d => dumpDescr += d)
        config.nbDump.foreach(d => dumpDescr += d)

        if (dumpDescr.isEmpty) {
          logger.error("Need at least one dump parameter")
          System.exit(1)
        }

        val counters = processDumps(dumpDescr, limit = config.limit).toList

        val counter = counters.head
        counters.tail.foreach(counter.crossMerge)

        counter.write(new File(output))
      case None =>
        parser.renderUsage(TwoColumns)
    }
  }
}
