package no.nrk.recommendations.common.languagedetector

import java.io.File

import com.google.common.base.Charsets
import com.optimaize.langdetect.i18n.LdLocale
import com.optimaize.langdetect.ngram.NgramExtractors
import com.optimaize.langdetect.profiles.{LanguageProfileBuilder, LanguageProfileWriter}
import com.optimaize.langdetect.text.CommonTextObjectFactories

import scala.io.Source

object ModelBuilder {

  def buildModel(inputFile: File, language: String) = {
    val textObjectFactory = CommonTextObjectFactories.forIndexing()
    val textObject = textObjectFactory.create()

    Source.fromFile(inputFile, Charsets.UTF_8.name())
      .getLines()
      .slice(0, 100000)
      .foreach(textObject.append(_))

    val languageProfile = new LanguageProfileBuilder(LdLocale.fromString(language))
      .ngramExtractor(NgramExtractors.standard())
      .minimalFrequency(40)
      .addText(textObject)
      .build()
    new LanguageProfileWriter().writeToDirectory(languageProfile, new File("/tmp/profile"))
  }

  def main(args: Array[String]): Unit = {
    buildModel(new File("/tmp/nn.txt"), "nn")
    buildModel(new File("/tmp/nb.txt"), "nb")
  }
}
