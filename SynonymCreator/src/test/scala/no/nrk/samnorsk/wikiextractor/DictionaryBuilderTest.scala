package no.nrk.samnorsk.wikiextractor

import java.io.{File, FileInputStream}
import java.util.zip.GZIPInputStream

import org.scalatest.{FlatSpec, Matchers}

import scala.io.Source

class DictionaryBuilderTest extends FlatSpec with Matchers {
  "a dictionary builder" should "collect translation counts" in {
    val source = Source.fromInputStream(new GZIPInputStream(new FileInputStream(new File(getClass.getResource("/test.txt.gz").getFile))))
    val it = WikiIterator(source)
    val translator = new StubApertiumRunner(Map("likar" -> "liker", "kjende" -> "kjente", "kring" -> "omkring"))

    val c = DictionaryBuilder.wikiToCounts(it, translator)

    c.docCount shouldBe 50

    c.get("kjende") shouldBe Seq("kjente")
    c.get("kring") shouldBe Seq("omkring")
  }
}
