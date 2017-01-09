package no.nrk.samnorsk.synonymmapper

import java.io.{File, PrintWriter}
import java.util.concurrent.atomic.AtomicInteger

import info.debatty.java.stringsimilarity.JaroWinkler

import scala.annotation.tailrec
import scala.collection.mutable
import scala.io.{Codec, Source}

object SynonymMapper {

  val Counter = new AtomicInteger()
  case class Mapping(source: String, target: String)
  case class WordAndFrequency(word: String, mappingFrequency: Int)

  val TokenizerRegex = """([A-Za-zØÆÅøæåéÉàÀôÔòÒ*]+)""".r
  val JaroWinler = new JaroWinkler()

  def getSentenceMapping(sourceSentence: String, targetSentence: String): Seq[Mapping] = {

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

    val sourceTokens = TokenizerRegex.findAllIn(sourceSentence).toSeq
    val targetTokens = TokenizerRegex.findAllIn(targetSentence).toSeq

    val sentenceMapping = if (sourceTokens.size == targetTokens.size) {
      val mappings: Seq[Mapping] = sourceTokens.zip(targetTokens)

          // Words out of voc have been annotated with * prefix.
          .filter { case (sourceWord, targetWord) => !sourceWord.contains('*') && !targetWord.contains('*') }
          .map { case (sourceWord, targetWord) => Mapping(sourceWord.toLowerCase, targetWord.toLowerCase) }

      getSentenceMappingRec(mappings, 0, Seq())
    } else {
      Seq()
    }
    sentenceMapping
  }

  def getCorpusMapping(source: File, target: File) = {
    require(Source.fromFile(source)(codec = Codec.UTF8).getLines().size == Source.fromFile(target)(codec = Codec.UTF8).getLines().size,
      "The corpora have different number of lines.")
    val sourceLines = Source.fromFile(source)(codec = Codec.UTF8).getLines()
    val targetLines = Source.fromFile(target)(codec = Codec.UTF8).getLines()

    // Process articles in parallel in batches of 1000.
    val mappingsInCorpus = sourceLines.zip(targetLines).grouped(1000).flatMap(group => {
      group.par.flatMap { case (sourceArticle, targetArticle) =>
        val articlesProcessed = Counter.incrementAndGet()
        if (articlesProcessed % 10000 == 0) {
          println(s"Processed $articlesProcessed articles")
        }
        sourceArticle.split('.').zip(targetArticle.split('.'))
          .flatMap { case (sourceSentence, targetSentence) => getSentenceMapping(sourceSentence, targetSentence)}
      }
    })
    mappingsInCorpus
  }

  def createSynonymsFromFrequencies(frequencyMap: Map[Mapping, Int]): Map[String, Seq[WordAndFrequency]] = {

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
    synonyms
  }

  def writeSynonyms(collapsedMap: Map[String, Seq[WordAndFrequency]], output: File, expansion: Boolean) = {
    val pw = new PrintWriter(output)

    val removedDuplicates = collapsedMap
      .map { case (sourceWord, wordsAndFreqs) =>
        (sourceWord, wordsAndFreqs.filter(x => x.word != sourceWord))
      }
      .filter { case (_, wordsAndFreqs) => wordsAndFreqs.nonEmpty }

    for (translation <- removedDuplicates) {
      val targetWords = translation._2
      for (i <- targetWords.indices) {
        pw.write(targetWords(i).word)
        if (i == targetWords.size - 1 && !expansion) {
          pw.write(" => ")
        } else {
          pw.write(",")
        }
      }
      pw.write(translation._1)
      pw.write('\n')
    }
    pw.close()
  }

  def main(args: Array[String]): Unit = {

    type OptionMap = Map[Symbol, String]
    def parseOptions(map : OptionMap, list: List[String]) : OptionMap = {
      list match {
        case Nil => map
        case "--source1" :: value :: tail =>
          parseOptions(map ++ Map('source1 -> value), tail)
        case "--source2" :: value :: tail =>
          parseOptions(map ++ Map('source2 -> value), tail)
        case "--target1" :: value :: tail =>
          parseOptions(map ++ Map('target1 -> value), tail)
        case "--target2" :: value :: tail =>
          parseOptions(map ++ Map('target2 -> value), tail)
        case "--output" :: value :: tail =>
          parseOptions(map ++ Map('output -> value), tail)
        case "--expansion" :: value :: tail =>
          parseOptions(map ++ Map('expansion -> value), tail)
        case option :: tail => throw new IllegalArgumentException("Unknown option " + option)
      }
    }
    val options: Map[Symbol, String] = parseOptions(Map(), args.toList)
    require(options.contains('output), "Output dictionary is not defined.")

    def getMappingsForInput(options: OptionMap, source: Symbol, target: Symbol) = {
      val mappings: Option[Iterator[Mapping]] = for {
        sourceFile <- options.get(source)
        targetFile <- options.get(target)
      } yield getCorpusMapping(new File(sourceFile), new File(targetFile))
      mappings.getOrElse(Iterator.empty)
    }

    val mappingsCorpus1 = getMappingsForInput(options, 'source1, 'target1)
    val mappingsCorpus2 = getMappingsForInput(options, 'source2, 'target2)
    val outputFile = new File(options('output))
    val isExpansion = options.getOrElse('expansion, "false").toBoolean

    val frequencyMap = (mappingsCorpus1 ++ mappingsCorpus2)
      .foldLeft(mutable.Map.empty[Mapping, Int]) { (acc, mapping) => acc += mapping -> (acc.getOrElse(mapping, 0) + 1) }
      .toMap

    val collapsedMap = createSynonymsFromFrequencies(frequencyMap)
    writeSynonyms(collapsedMap, outputFile, expansion = isExpansion)
  }
}
