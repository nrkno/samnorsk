package no.nrk.samnorsk.wikiextractor

import org.scalatest.{FlatSpec, Matchers}

class ApertiumRunnerTest extends FlatSpec with Matchers {
  "a stub" should "translate" in {
    val stub = new StubApertiumRunner(Map("ba" -> "foo", "knekk" -> "knark"))

    stub.translate("Jupp (ba) knekker, knekk.") shouldBe "Jupp (foo) knekker, knark."
  }
}
