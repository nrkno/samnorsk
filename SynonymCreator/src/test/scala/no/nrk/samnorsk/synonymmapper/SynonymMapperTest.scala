package no.nrk.samnorsk.synonymmapper

import java.io.File

import no.nrk.samnorsk.synonymmapper.SynonymMapper.{Mapping, SynonymLine}
import org.scalatest.FunSuite

import scala.io.Source

class SynonymMapperTest extends FunSuite {

  test("Write output in solr/elatic format") {
    val testFile = File.createTempFile("synfile", "test")
    testFile.deleteOnExit()

    val inputSynonyms = Seq(SynonymLine(Seq("anledning", "mulighet"), "høve"), SynonymLine(Seq("kanskje", "muligens"), "truleg"))
    SynonymMapper.writeSynonyms(inputSynonyms, testFile, false)

    val reductionLines = Source.fromFile(testFile).getLines().toSeq
    assert(reductionLines.head === "anledning,mulighet => høve")
    assert(reductionLines.last === "kanskje,muligens => truleg")

    SynonymMapper.writeSynonyms(inputSynonyms, testFile, true)
    val expansionLines = Source.fromFile(testFile).getLines().toSeq
    assert(expansionLines.head === "anledning,mulighet,høve")
    assert(expansionLines.last === "kanskje,muligens,truleg")
  }

  test("Create synonyms from frequency map") {
    val frequencyMap = Map(Mapping("høve", "mulighet") -> 20,
      Mapping("høve", "anledning") -> 16,
      Mapping("høve", "min") -> 2,
      Mapping("snakke", "preike") -> 100,
      Mapping("snakke", "preke") -> 105)

    val synonyms = SynonymMapper.createSynonymsFromFrequencies(frequencyMap)
    assert(synonyms.head.canonicalForm == "høve")
    assert(synonyms.head.synonyms.toSet === Set("mulighet", "anledning"))

    assert(synonyms.last.canonicalForm == "snakke")
    assert(synonyms.last.synonyms.toSet === Set("preike", "preke"))
  }
}
