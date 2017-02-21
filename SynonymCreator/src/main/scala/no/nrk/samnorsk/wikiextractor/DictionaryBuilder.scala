package no.nrk.samnorsk.wikiextractor

import java.io.File

import com.typesafe.scalalogging.slf4j.StrictLogging
import scopt.RenderingMode.TwoColumns

import scala.collection.parallel.ForkJoinTaskSupport
import scala.concurrent.forkjoin.ForkJoinPool
import scala.io.Source

object DictionaryBuilder extends StrictLogging {
  def textToPairs(text: String, translator: ApertiumRunner): Traversable[(String, String)] = {
    for (sent <- SentenceSegmenter.segment(text);
         translation = translator.translate(sent);
         pair <- SimpleTextAligner.tokenDiscrepancy(sent, translation))
      yield pair
  }

  def textToPairs(texts: Traversable[String], translator: ApertiumRunner): Traversable[(String, String)] = {
    val sents = texts.flatMap(SentenceSegmenter.segment)
    val translations = translator.translate(sents)

    for (combined <- sents.toSeq zip translations.toSeq;
         pair <- SimpleTextAligner.tokenDiscrepancy(combined._1, combined._2))
      yield pair
  }

  def wikiToCounts(it: WikiIterator, translator: ApertiumRunner,
                   counter: TranslationCounter[String, String], procs: Option[Int] = None): TranslationCounter[String, String] = {
    it.filter(_.length < 5000)
      .grouped(10000)
      .foreach(articles => {
        val par = articles.grouped(100).toSeq.par

        procs match {
          case Some(p) => par.tasksupport = new ForkJoinTaskSupport(new ForkJoinPool(p))
          case _ => ;
        }

        par.map(textToPairs(_, translator)).seq.foreach(counter.update)
      })

    counter
  }

  def main(args: Array[String]): Unit = {
    case class Config(from: Language = Nynorsk, to: Language = Bokmaal, limit: Option[Int] = None, topN: Int = 1,
                      procs: Option[Int] = None,
                      input: Option[File] = None, output: Option[File] = None,
                      sourceTF: Int = 5, sourceIDF: Double = .5,
                      transTF: Int = 5, transIDF: Double = .5)

    val parser = new scopt.OptionParser[Config]("DictionaryBuilder") {
      head("DictionaryBuilder", "0.1.0")

      opt[String]('d', "direction")
        .action((x, c) => x.split("-") match {
          case Array(from, to) =>
            // TODO should exit on illegal language codes
            c.copy(from = Language(from).getOrElse(Bokmaal), to = Language(to).getOrElse(Bokmaal)) })
        .text("Translation direction (ex. nno-nob).")
      opt[Int]('l', "limit")
        .action((x, c) => c.copy(limit = Some(x)))
        .text("Maximum number of articles to process.")
      opt[String]('i', "input-file")
        .action((x, c) => c.copy(input = Some(new File(x))))
        .text("Input wikipedia dump")
        .required()
      opt[String]('o', "output-file")
        .action((x, c) => c.copy(output = Some(new File(x))))
        .text("Output dictionary file")
        .required()
      opt[Int]('S', "source-tf-filter")
        .action((x, c) => c.copy(sourceTF = x))
        .text("Minimum term frequency for source words")
      opt[Double]('s', "source-df-filter")
        .action((x, c) => c.copy(sourceIDF = x))
        .text("Maximum doc frequency for source words")
      opt[Int]('T', "trans-tf-filter")
        .action((x, c) => c.copy(transTF = x))
        .text("Minimum term frequency for translated words")
      opt[Double]('t', "trans-df-filter")
        .action((x, c) => c.copy(transIDF = x))
        .text("Maximum doc frequency for translated words")
      opt[Int]('n', "top-n")
        .action((x, c) => c.copy(topN = x))
        .text("Number of translations to keep")
      opt[Int]('p', "procs").action((x, c) => c.copy(procs = Some(x))).text("Number of processors to use")
    }

    parser.parse(args, Config()) match {
      case Some(config) =>
        config.limit match {
          case Some(l) => logger.info(s"Reading $l articles from ${config.input.get.getAbsolutePath}")
          case _ => logger.info(s"Reading all articles from ${config.input.get.getAbsolutePath}")
        }

        val translator = new LocalApertiumRunner(fromLanguage = config.from, toLanguage = config.to)
        val source = Source.fromFile(config.input.get)
        val it = new WikiIterator(source, limit = config.limit)

        val counter = wikiToCounts(it, translator,
          new TranslationCounter[String, String](
            sourceTfFilter = config.sourceTF, sourceDfFilter = config.sourceIDF,
            transTfFilter = config.transTF, transDfFilter = config.transIDF, topN = Some(config.topN)),
          procs = config.procs)

        counter.write(config.output.get)

        source.close()
      case None =>
        parser.renderUsage(TwoColumns)
    }
  }
}
