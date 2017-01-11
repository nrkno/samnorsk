package no.nrk.samnorsk.wikiextractor

import java.io.File

import org.scalatest.FlatSpec

class ExtractorTests extends FlatSpec {

  val testFile = new File(getClass.getResource("/test.txt.gz").getFile)
  val outputFile = File.createTempFile("output", "nn-nb")
  val foo = WikiExtractor.translateDump(testFile, Nynorsk, Bokmaal)
  assert(foo.size == 50)
  assert(foo.head.startsWith("*Wasit er en landsby øst i *Jemen."))
}
