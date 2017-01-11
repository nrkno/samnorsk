package no.nrk.samnorsk.wikiextractor

import java.io.File

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import no.nrk.samnorsk.wikiextractor.WikiExtractor.ArticleAndTranslation
import org.scalatest.FlatSpec

import scala.io.Source

class ExtractorTests extends FlatSpec {

  val testFile = new File(getClass.getResource("/test.txt.gz").getFile)
  val outputFile = File.createTempFile("output", "nn-nb")
  WikiExtractor.translateDump(testFile, Nynorsk, Bokmaal, outputFile)

  val outputTranslation = Source.fromFile(outputFile).getLines().toSeq
  assert(outputTranslation.size == 50)
  val articleAndTrans = new ObjectMapper().registerModule(DefaultScalaModule).readValue(outputTranslation.head, classOf[ArticleAndTranslation])
  assert(articleAndTrans.translation.startsWith("*Wasit er en landsby øst i *Jemen."))
  assert(articleAndTrans.original.startsWith("Wasit er ein landsby aust i Jemen."))
}
