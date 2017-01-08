package no.nrk.samnorsk.synonymmapper

import java.io.{File, PrintWriter}

import info.debatty.java.stringsimilarity.JaroWinkler

import scala.collection.mutable
import scala.io.{Codec, Source}

object SynonymMapper {

  case class Mapping(source: String, target: String)
  case class WordAndFrequency(word: String, mappingFrequency: Int)
  case class MappingWithSimilarity(source: String, target: String, similarity: Double)

  val TokenizerRegex = """([A-Za-zØÆÅøæåéÉàÀôÔòÒ*]+)""".r
  val JaroWinler = new JaroWinkler()

  def getLineMappings(wordSimilarities: Seq[MappingWithSimilarity]): Seq[Mapping] = {
    var consecutiveErrorsCounter = 0
    var tooManyErrors = false
    val mappings = wordSimilarities.map(wordpair => {
      if (wordpair.similarity < 0.8) {
        consecutiveErrorsCounter += 1
      } else if (consecutiveErrorsCounter >= 3) {
        tooManyErrors = true
      } else {
        consecutiveErrorsCounter = 0
      }
      Mapping(wordpair.source.toLowerCase, wordpair.target.toLowerCase)
    })
    if (!tooManyErrors) mappings else Seq.empty[Mapping]
  }

  def getSentenceMapping(sourceSentence: String, targetSentence: String): Seq[Mapping] = {

    val sourceTokens = TokenizerRegex.findAllIn(sourceSentence).toSeq
    val targetTokens = TokenizerRegex.findAllIn(targetSentence).toSeq

    val sentenceMapping = if (sourceTokens.size == targetTokens.size) {
      val wordsWithSimilarity: Seq[MappingWithSimilarity] =
        sourceTokens.zip(targetTokens)

          // Words out of voc have been annotated with * prefix.
          .filter { case (sourceWord, targetWord) => !sourceWord.contains('*') && !targetWord.contains('*') }
          .map { case (sourceWord, targetWord) => MappingWithSimilarity(sourceWord, targetWord, JaroWinler.similarity(sourceWord, targetWord)) }

      getLineMappings(wordsWithSimilarity)
    } else {
      Seq.empty[Mapping]
    }
    sentenceMapping
  }

  def getCorpusMapping(source: File, target: File): Iterator[Mapping] = {
    require(Source.fromFile(source)(codec = Codec.UTF8).getLines().size == Source.fromFile(target)(codec = Codec.UTF8).getLines().size,
      "The corpora have different number of lines.")
    val sourceLines = Source.fromFile(source)(codec = Codec.UTF8).getLines()
    val targetLines = Source.fromFile(target)(codec = Codec.UTF8).getLines()

    var counter = 0
    val mappingsInCorpus: Iterator[Mapping] = sourceLines.zip(targetLines)
      .flatMap { case (sourceArticle, targetArticle) => {
        counter += 1
        if (counter % 10000 == 0) {
          println(counter)
        }
        sourceArticle.split('.').zip(targetArticle.split('.'))
          .flatMap { case (sourceSentence, targetSentence) => getSentenceMapping(sourceSentence, targetSentence)}
      }}
    mappingsInCorpus
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

    val mappingsCorpus1 = getCorpusMapping(new File(source1), new File(target1))
    val mappingsCorpus2 = getCorpusMapping(new File(source2), new File(target2))
    val frequencyMap = (mappingsCorpus1 ++ mappingsCorpus2)
      .foldLeft(mutable.Map.empty[Mapping, Int]) { (acc, mapping) => acc += mapping -> (acc.getOrElse(mapping, 0) + 1) }
      .toMap

    val collapsedMap = createSynonymsFromFrequencies(frequencyMap)
    val useExpansion = args.size > 5 && args(5) == "-e"
    writeSynonyms(collapsedMap, new File(args(4)), expansion = useExpansion)
  }
}
