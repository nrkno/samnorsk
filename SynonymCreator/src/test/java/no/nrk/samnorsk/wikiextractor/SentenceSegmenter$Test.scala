package no.nrk.samnorsk.wikiextractor

import org.scalatest.{FlatSpec, Matchers}

class SentenceSegmenter$Test extends FlatSpec with Matchers {

  "a SentenceSegmenter" should "segment sentences" in {
    SentenceSegmenter.segment("Hallo i luken. Hallå på balla.") should contain inOrder ("Hallo i luken.", "Hallå på balla.")
  }

}
