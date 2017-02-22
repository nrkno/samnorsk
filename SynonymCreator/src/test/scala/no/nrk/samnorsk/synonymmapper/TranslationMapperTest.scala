package no.nrk.samnorsk.synonymmapper

import no.nrk.samnorsk.synonymmapper.SynonymMapper.Mapping
import org.scalatest.{FlatSpec, Matchers}

class TranslationMapperTest extends FlatSpec with Matchers {

  "a SimpleTextAligner" should "return discrepancies" in {
    val aligner = new LinearMapper
    aligner.map("ba foo fnark", "ba feh fnark") shouldBe Seq(Mapping("foo", "feh"))
    aligner.map("Ba foo fnark", "ba Feh fnark") shouldBe Seq(Mapping("foo", "feh"))
    aligner.map("ba foo fnark fneh", "ba feh fnork fnah") shouldBe Seq(Mapping("foo", "feh"), Mapping("fnark", "fnork"), Mapping("fneh", "fnah"))
    aligner.map("ba foo ba fnark fneh", "ba feh ba fnork fnah ba") shouldBe Seq(Mapping("foo", "feh"), Mapping("fnark", "fnork"))
    aligner.map("ba foo ba fnark fneh", "ba feh ba fnork") shouldBe Seq(Mapping("foo", "feh"), Mapping("fnark", "fnork"))
  }

}
