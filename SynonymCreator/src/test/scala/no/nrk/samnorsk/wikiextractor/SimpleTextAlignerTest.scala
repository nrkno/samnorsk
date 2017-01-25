package no.nrk.samnorsk.wikiextractor

import org.scalatest.{FlatSpec, Matchers}

class SimpleTextAlignerTest extends FlatSpec with Matchers {

  "a SimpleTextAligner" should "return discrepancies" in {
    SimpleTextAligner.tokenDiscrepancy("ba foo fnark", "ba feh fnark") shouldBe Seq(("foo", "feh"))
    SimpleTextAligner.tokenDiscrepancy("Ba foo fnark", "ba Feh fnark") shouldBe Seq(("foo", "feh"))
    SimpleTextAligner.tokenDiscrepancy("ba foo fnark fneh", "ba feh fnork fnah") shouldBe Seq(("foo", "feh"), ("fnark", "fnork"), ("fneh", "fnah"))
    SimpleTextAligner.tokenDiscrepancy("ba foo ba fnark fneh", "ba feh ba fnork fnah ba") shouldBe Seq(("foo", "feh"), ("fnark", "fnork"))
    SimpleTextAligner.tokenDiscrepancy("ba foo ba fnark fneh", "ba feh ba fnork") shouldBe Seq(("foo", "feh"), ("fnark", "fnork"))
  }

}
