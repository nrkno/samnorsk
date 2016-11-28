package no.nrk.samnorsk.synonymmapper

import java.io.{File, PrintWriter}

import info.debatty.java.stringsimilarity.JaroWinkler

import scala.io.{Codec, Source}

object SynonymMapper {

  case class Mapping(source: String, target: String)
  case class WordAndFrequency(word: String, mappingFrequency: Int)
  case class MappingWithSimilarity(source: String, target: String, similarity: Double)

  val TokenizerRegex = """([\wØÆÅøæåéÉàÀôÔòÒ]+)""".r
  val JaroWinler = new JaroWinkler()

  def parseLine(frequencyDict: scala.collection.mutable.Map[Mapping, Int], sourceLine: String, targetLine: String) = {
    val lineFrequencies = scala.collection.mutable.Map[Mapping, Int]()

    val sourceTokens = TokenizerRegex.findAllIn(sourceLine).toSeq
    val targetTokens = TokenizerRegex.findAllIn(targetLine).toSeq

    if (sourceTokens.size == targetTokens.size) {
      var consecutiveErrorsCounter = 0
      val wordsWithSimilarity: Seq[MappingWithSimilarity] =
        sourceTokens.zip(targetTokens)

          // Words out of voc has been annotated with * prefix.
          .filter { case (sourceWord, targetWord) => !sourceWord.contains('*') && !targetWord.contains('*') }
          .map { case (sourceWord, targetWord) => MappingWithSimilarity(sourceWord, targetWord, JaroWinler.similarity(sourceWord, targetWord)) }

      for (wordspair <- wordsWithSimilarity) {
        if (wordspair.similarity < 0.8) {
          consecutiveErrorsCounter += 1
        } else {
          consecutiveErrorsCounter = 0
        }
        if (consecutiveErrorsCounter < 3) {
          val currentPair = Mapping(wordspair.source.toLowerCase(), wordspair.target.toLowerCase())
          lineFrequencies(currentPair) = lineFrequencies.getOrElse(currentPair, 0) + 1
        } else {
          lineFrequencies.clear()
        }
      }
    }

    lineFrequencies
      .toSeq
      .foreach { case(mapping, frequency) => frequencyDict(mapping) = frequencyDict.getOrElse(mapping, 0) + 1 }
  }

  def parseCorpus(frequencyDict: scala.collection.mutable.Map[Mapping, Int], source: File, target: File) = {
    val sourceLines = Source.fromFile(source)(codec = Codec.UTF8).getLines()
    val targetLines = Source.fromFile(target)(codec = Codec.UTF8).getLines()
    var counter = 0
    while(sourceLines.hasNext && targetLines.hasNext) {
      counter += 1
      if (counter % 10000 == 0) {
        println(counter)
      }
      val sourceArticleLines = sourceLines.next().split('.').toIterator
      val targetArticleLines = targetLines.next().split('.').toIterator
      while (sourceArticleLines.hasNext && targetArticleLines.hasNext) {
        parseLine(frequencyDict, sourceArticleLines.next(), targetArticleLines.next())
      }
    }
  }


  def createSynonymsFromFrequencies(frequencyMap: Map[Mapping, Int]): Map[String, Seq[WordAndFrequency]] = {

    def aggregateFrequencies(frequencyMap: Map[Mapping, Int], cutoff: Int = 5): Map[String, Seq[WordAndFrequency]] = {
      val aggMap = scala.collection.mutable.Map[String, Seq[WordAndFrequency]]()
      frequencyMap
        .filter(x => x._2 > cutoff)
        .foreach { case(mapping, frequency) =>
          aggMap(mapping.source) = aggMap.getOrElse(mapping.source, Seq()) :+ WordAndFrequency(mapping.target, frequency)
        }
      aggMap.toMap
    }

    val reverseFreqMap = frequencyMap.toSeq
      .map { case (mapping, frequency) => (Mapping(mapping.target, mapping.source), frequency) }
      .toMap

    val reverseAggregatedMap = aggregateFrequencies(reverseFreqMap)

    def isMostFrequentTranslation(candidate: String, source: String) = {
      val wordsAndFreqForCandidate = reverseAggregatedMap.getOrElse(candidate, Seq()).sortBy(_.mappingFrequency).reverse
      wordsAndFreqForCandidate(0).word == source
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
          .reverse
          .head
          .mappingFrequency

        getSynonymsAboveRelativeFreqThreshold(synonymCandidates, highestFrequency)
      } else {
        Seq()
      }
    }

    val synonyms = filteredAggregationMap
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
    val source1 = args(0)
    val target1 = args(1)
    val source2 = args(2)
    val target2 = args(3)

    val frequencyMap = scala.collection.mutable.Map[Mapping, Int]()
    parseCorpus(frequencyMap, new File(source1), new File(target1))
    parseCorpus(frequencyMap, new File(source2), new File(target2))

    val collapsedMap = createSynonymsFromFrequencies(frequencyMap.toMap)
    val useExpansion = args.size > 5 && args(5) == "-e"
    writeSynonyms(collapsedMap, new File(args(4)), expansion = useExpansion)
  }
}
