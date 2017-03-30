package no.nrk.samnorsk.synonymmapper

import org.scalatest.{FlatSpec, Matchers}

import scala.io.Source

class WikiIteratorTest extends FlatSpec with Matchers {
  "A WikiIterator" should "return the text of all articles" in {
    val source = Source.fromInputStream(getClass.getResourceAsStream("/dump-a.json"))
    val it = new WikiIterator(source)

    it.toList should contain inOrder ("ba", "foo")
  }

  "A WikiIterator" should "take a limit" in {
    val source = Source.fromInputStream(getClass.getResourceAsStream("/dump-a.json"))
    val it = new WikiIterator(source, limit = Some(1))

    it.toList shouldBe Seq("ba")
  }
}
