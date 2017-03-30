package no.nrk.samnorsk.synonymmapper

import no.nrk.samnorsk.synonymmapper.SynonymMapper.Mapping
import org.scalatest.{FlatSpec, Matchers}


class TranslationCounterTest extends FlatSpec with Matchers {
  "a translation counter" should "keep translation pair statistics" in {
    val c = new TranslationCounter[Int, Int]()
    c.update(Vector(Mapping(1, 1), Mapping(2, 1), Mapping(2, 2)))
    c.update(Vector(Mapping(1, 1), Mapping(1, 2), Mapping(2, 2)))
    c.get(1) shouldBe Seq(1)
    c.get(2) shouldBe Seq(2)
    c.docCount shouldBe 2
  }
}
