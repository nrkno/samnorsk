package no.nrk.samnorsk.wikiextractor

object DictionaryBuilder {
  def textToPairs(text: String, translator: ApertiumRunner): Traversable[(String, String)] = {
    for (sent <- SentenceSegmenter.segment(text);
         translation = translator.translate(sent);
         pair <- SimpleTextAligner.tokenDiscrepancy(sent, translation))
      yield pair
  }

  def wikiToCounts(it: WikiIterator, translator: ApertiumRunner): TranslationCounter[String, String] = {
    val counter = new TranslationCounter[String, String]()

    for (article <- it) {
      counter.update(textToPairs(article, translator))
    }

    counter
  }
}
