package no.nrk.samnorsk.wikiextractor

import java.io.{File, FileInputStream}
import java.lang.Thread.UncaughtExceptionHandler
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, StandardOpenOption}
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.typesafe.scalalogging.slf4j.LazyLogging
import no.nrk.samnorsk.util.JsonWrapper
import resource._
import scopt.RenderingMode.TwoColumns

import scala.collection.parallel.ParSeq
import scala.io.{Codec, Source}
import scala.sys.process._
import scala.util.matching.Regex

trait Language {
  val Apertium: String
  val Wiki: String
  val Name: String
}

object Language {
  def apply(lang_code: String): Option[Language] = {
    lang_code match {
      case Nynorsk.Name => Some(Nynorsk)
      case Bokmaal.Name => Some(Bokmaal)
      case _ => None
    }
  }
}

object Nynorsk extends Language {
  override val Apertium: String = "nno"
  override val Wiki: String = "nn"
  override val Name: String = "nn"
}

object Bokmaal extends Language {
  override val Apertium: String = "nob"
  override val Wiki: String = "no"
  override val Name: String = "nb"
}

class Counter {
  private val counter = new AtomicInteger()

  def next(): Int = counter.incrementAndGet()
}

object ApertiumHelper {

  def translate(input: String, fromLanguage: Language, toLanguage: Language, counter: Counter): String = {
    val tempInputFile = File.createTempFile("apertium-input", fromLanguage.Name)
    try {
      Files.write(tempInputFile.toPath, input.getBytes(StandardCharsets.UTF_8))
      val chunkNumber = counter.next()
      println(s"Started translating chunk $chunkNumber from ${fromLanguage.Name} to ${toLanguage.Name}")
      val trans = s"apertium ${fromLanguage.Apertium}-${toLanguage.Apertium} ${tempInputFile.getAbsolutePath}".!!.trim
      println(s"Done translating chunk $chunkNumber")
      trans
    } finally {
      Files.delete(tempInputFile.toPath)
    }
  }
}

object WikiExtractor extends LazyLogging {

  @JsonIgnoreProperties(ignoreUnknown = true)
  case class Article(text: String)

  case class ArticleAndTranslation(original: String, translation: String, fromLanguage: String, toLanguage: String)

  val DateRegex: Regex = """20[\d]{6}""".r

  def downloadLatest(language: Language): File = {
    val dates: Seq[String] = Source.fromURL("https://dumps.wikimedia.org/other/cirrussearch/current/")
      .getLines()
      .filter(_.contains(s"${language.Wiki}wiki-"))
      .toSeq
      .flatMap(line => DateRegex.findFirstIn(line))
      .distinct

    require(dates.length == 1, "Unable to find latest date for wiki dump")
    val downloadedFile = new File(s"/tmp/${language.Name}.gz")
    val url = s"https://dumps.wikimedia.org/other/cirrussearch/current/${language.Wiki}wiki-${dates.head}-cirrussearch-content.json.gz"
    logger.info(s"Downloading $url")
    new URL(url) #> downloadedFile !!

    logger.info(s"Done downloading $url")
    downloadedFile
  }

  def resolveDump(fileMaybe: Option[String], language: Language): File = {
    fileMaybe match {
      case Some(dumpFile) =>
        val file = new File(dumpFile)
        require(file.exists(), s"$dumpFile does not exist.")
        file
      case None => downloadLatest(language)
    }
  }

  def translateArticles(it: WikiIterator, runner: ApertiumRunner): ParSeq[ArticleAndTranslation] = {
    it.filter(_.length < 5000)
      .grouped(100)
      .toSeq.par
      .flatMap(articles => articles.zip(runner.translate(articles).toSeq))
      .map(article => ArticleAndTranslation(article._1, article._2, runner.fromLanguage.Name, runner.toLanguage.Name))
  }

  def translateDump(dump: File, fromLanguage: Language, toLanguage: Language, translationFile: File,
                    limit: Option[Int] = None): Unit = {
    managed(Source.fromInputStream(new GZIPInputStream(new FileInputStream(dump)))(Codec.UTF8))
      .acquireAndGet(source => {
        val it = new WikiIterator(source, limit = limit)
        val runner = new LocalApertiumRunner(fromLanguage, toLanguage)

        translateArticles(it, runner).foreach(
          translation => {
            val data = (JsonWrapper.convertToString(translation) + "\n").getBytes(StandardCharsets.UTF_8)

            translationFile.synchronized {
              Files.write(translationFile.toPath, data, StandardOpenOption.APPEND)
            }
          }
        )
      })
  }

  def wipeAndCreateNewFile(file: File): Boolean = {
    if (file.exists()) {
      Files.delete(file.toPath)
    }
    file.createNewFile()
  }

  def main(args: Array[String]): Unit = {
    case class Config(nndump: String = "", nbdump: String = "", trans: String = "", limit: Option[Int] = None)

    Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler {
      override def uncaughtException(t: Thread, e: Throwable): Unit = {
        logger.error(s"Uncaught exception, exiting. ${e.getMessage}")
        sys.exit(1)
      }
    })

    val parser = new scopt.OptionParser[Config]("WikiExtractor") {
      head("WikiExtractor 0.1.0")

      opt[String]('n', "nndump")
        .action((x, c) => c.copy(nndump = x))
        .text("NNO Wikipedia CirrusSearch dump file.")
        .required()
      opt[String]('b', "nbdump")
        .action((x, c) => c.copy(nbdump = x))
        .text("NOB Wikipedia CirrusSearch dump file.")
        .required()
      opt[String]('t', "trans")
        .action((x, c) => c.copy(trans = x))
        .text("Translations output file.")
        .required()
      opt[Int]('l', "limit")
        .action((x, c) => c.copy(limit = Some(x)))
        .text("Process this number of articles.")
    }

    parser.parse(args, Config()) match {
      case Some(config) =>
        val output = new File(config.trans)

        wipeAndCreateNewFile(output)

        val nynorskDump = resolveDump(Some(config.nndump), Nynorsk)
        val bokmaalDump = resolveDump(Some(config.nbdump), Bokmaal)

        println("Dumps resolved, starting translation")
        translateDump(bokmaalDump, Bokmaal, Nynorsk, output, config.limit)
        translateDump(nynorskDump, Nynorsk, Bokmaal, output, config.limit)
      case None => parser.renderUsage(TwoColumns)
    }
  }
}
