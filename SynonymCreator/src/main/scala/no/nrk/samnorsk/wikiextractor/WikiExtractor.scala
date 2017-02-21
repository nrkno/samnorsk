package no.nrk.samnorsk.wikiextractor
import java.io.{File, FileInputStream}
import java.lang.Thread.UncaughtExceptionHandler
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.typesafe.scalalogging.slf4j.LazyLogging
import no.nrk.samnorsk.util.{IOUtils, JsonWrapper}
import resource._
import scopt.RenderingMode.TwoColumns

import scala.io.{Codec, Source}
import scala.sys.process._

trait Language {
  val Aperitum: String
  val Wiki: String
  val Name: String
}

object Nynorsk extends Language {
  override val Aperitum: String = "nno"
  override val Wiki: String = "nn"
  override val Name: String = "nn"
}

object Bokmaal extends Language {
  override val Aperitum: String = "nob"
  override val Wiki: String = "no"
  override val Name: String = "nb"
}

class Counter {
  private val counter = new AtomicInteger()
  def next() = counter.incrementAndGet()
}

object ApertiumHelper {

  def translate(input: String, fromLanguage: Language, toLanguage: Language, counter: Counter): String = {
    val tempInputFile = File.createTempFile("apertium-input", fromLanguage.Name)
    try {
      Files.write(tempInputFile.toPath, input.getBytes(StandardCharsets.UTF_8))
      val chunkNumber = counter.next()
      println(s"Started translating chunk $chunkNumber from ${fromLanguage.Name} to ${toLanguage.Name}")
      val trans = s"apertium ${fromLanguage.Aperitum}-${toLanguage.Aperitum} ${tempInputFile.getAbsolutePath}".!!.trim
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

  val DateRegex = """20[\d]{6}""".r

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

  def resolveDump(fileMaybe: Option[String], language: Language) = {
    fileMaybe match {
      case Some(dumpFile) =>
        val file = new File(dumpFile)
        require(file.exists(), s"$dumpFile does not exist.")
        file
      case None => downloadLatest(language)
    }
  }

  def translateDump(dump: File, fromLanguage: Language, toLanguage: Language, translationFile: File) = {

    val counter = new Counter

    def translateChunk(listOfArticles: Seq[String]) = {
      listOfArticles
        // Group articles to something reasonable to avoid initializing Apertium for each article.
        .grouped(100)
        // Apertium will remove \n, we use ☃☃¤ as a magic article separator.
        .map(listOfArticles => listOfArticles.mkString("☃☃¤"))
        .toSeq.par
        .foreach(originalText => {
          val translation = ApertiumHelper.translate(originalText, fromLanguage, toLanguage, counter)
          val articles = originalText.split("☃☃¤").zip(translation.split("☃☃¤"))
            .map(x => ArticleAndTranslation(x._1, x._2, fromLanguage.Name, toLanguage.Name))
           IOUtils.writeOutput(articles, translationFile)
        })
    }

    managed(Source.fromInputStream(new GZIPInputStream(new FileInputStream(dump)))(Codec.UTF8))
      .acquireAndGet(source => {
        source.getLines()
          .map(article => JsonWrapper.convert(article, classOf[Article]))
          .filter(x => x.text != null)
          .filter(_.text.length > 100)
          .map(article => article.text)
          .grouped(10000)
          .foreach(chunk => translateChunk(chunk))
      })
  }

  def wipeAndCreateNewFile(file: File) = {
    if (file.exists()) {
      Files.delete(file.toPath)
    }
    file.createNewFile()
  }

  def main(args: Array[String]): Unit = {
    case class Config(nndump: String = "", nbdump: String = "", trans: String = "")

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
    }

    parser.parse(args, Config()) match {
      case Some(config) =>
        val output = new File(config.trans)

        wipeAndCreateNewFile(output)

        val nynorskDump = resolveDump(Some(config.nndump), Nynorsk)
        val bokmaalDump = resolveDump(Some(config.nbdump), Bokmaal)

        println("Dumps resolved, starting translation")
        translateDump(bokmaalDump, Bokmaal, Nynorsk, output)
        translateDump(nynorskDump, Nynorsk, Bokmaal, output)
      case None => parser.renderUsage(TwoColumns)
    }
  }
}
