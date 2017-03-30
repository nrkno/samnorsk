package no.nrk.samnorsk.synonymmapper

import org.scalatest.{FlatSpec, Matchers}

class SentenceSegmenterTest extends FlatSpec with Matchers {

  "a SentenceSegmenter" should "segment sentences" in {
    SentenceSegmenter.segment("Hallo i luken. Hallå på balla.") should contain inOrder ("Hallo i luken.", "Hallå på balla.")
  }

}
