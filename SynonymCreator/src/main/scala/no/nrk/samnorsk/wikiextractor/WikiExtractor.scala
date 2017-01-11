package no.nrk.samnorsk.wikiextractor
import java.io.{File, FileInputStream, PrintWriter}
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import resource._

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

object ApertiumHelper {

  val Counter = new AtomicInteger()
  def translate(input: String, fromLanguage: Language, toLanguage: Language): String = {
    val tempInputFile = File.createTempFile("apertium-input", fromLanguage.Name)
    try {
      Files.write(tempInputFile.toPath, input.getBytes(StandardCharsets.UTF_8))
      val chunkNumber = Counter.incrementAndGet()
      println(s"Started translating chunk $chunkNumber from ${fromLanguage.Name} to ${toLanguage.Name}")
      val trans = s"apertium ${fromLanguage.Aperitum}-${toLanguage.Aperitum} ${tempInputFile.getAbsolutePath}".!!.trim
      println(s"Done translating chunk $chunkNumber")
      trans
    } finally {
      Files.delete(tempInputFile.toPath)
    }
  }
}

object WikiExtractor {

  @JsonIgnoreProperties(ignoreUnknown = true)
  case class Article(text: String)

  val JsonMapper = new ObjectMapper().registerModule(DefaultScalaModule)
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
    println(s"Downloading $url")
    new URL(url) #> downloadedFile !!

    println(s"Done downloading $url")
    downloadedFile
  }

  def resolveDump(fileMaybe: Option[String], language: Language) = {
    fileMaybe match {
      case Some(dumpFile) =>
        val file = new File(dumpFile)
        require(file.exists(), s"$dumpFile does not exist.")
        file
      case None => {
        downloadLatest(language)
      }
    }
  }

  def writeOutput(lines: Seq[String], outputFile: File) = {
    managed(new PrintWriter(outputFile))
      .acquireAndGet(writer => {
        for (line <- lines) {
          writer.write(line + "\n")
        }
        writer.write("\n")
      })
  }

  def translateDump(dump: File, fromLanguage: Language, toLanguage: Language): List[String] = {
    val articles = managed(Source.fromInputStream(new GZIPInputStream(new FileInputStream(dump)))(Codec.UTF8))
      .acquireAndGet(source => {
        val lines = source.getLines()
          .map(article => JsonMapper.readValue(article, classOf[Article]))
          .filter(x => x.text != null)
          .filter(_.text.length > 100)
          .map(article => article.text)
        lines.toList
      })

    // Send reasonably sized chunks to Apertium. We don't want to send too small chunks due to initialization time.
    val translations = articles.grouped(articles.size / Math.min(200, articles.size))
      .map(listOfArticles => listOfArticles.mkString("☃☃¤"))
      .toSeq.par.map(text => ApertiumHelper.translate(text, fromLanguage, toLanguage))
      .flatMap(x => x.split("☃☃¤"))

    translations.toList
  }

  def main(args: Array[String]): Unit = {
    type OptionMap = Map[String, String]
    def parseOptions(map : OptionMap, list: List[String]) : OptionMap = {
      list match {
        case Nil => map
        case "--nynorsk" :: value :: tail =>
          parseOptions(map ++ Map(Nynorsk.Name -> value), tail)
        case "--bokmaal" :: value :: tail =>
          parseOptions(map ++ Map(Bokmaal.Name -> value), tail)
        case "--nynorsk-translation" :: value :: tail =>
          parseOptions(map ++ Map("nynorsktobokmaal" -> value), tail)
        case "--bokmaal-translation" :: value :: tail =>
          parseOptions(map ++ Map("bokmaaltonynorsk" -> value), tail)
        case option :: tail => throw new IllegalArgumentException("Unknown option " + option)
      }
    }

    val options: Map[String, String] = parseOptions(Map(), args.toList)
    val outputNnNb = new File(options.getOrElse("nynorsktobokmaal", throw new IllegalArgumentException("Nynorsk to Bokmaal dictionary is not defined")))
    val outputNbNn = new File(options.getOrElse("bokmaaltonynorsk", throw new IllegalArgumentException("Bokmaal to Nynorsk dictionary is not defined")))

    val nynorskDump = resolveDump(options.get(Nynorsk.Name), Nynorsk)
    val bokmaalDump = resolveDump(options.get(Bokmaal.Name), Bokmaal)

    println("Dumps resolved, starting translation")

    val transNnNB = translateDump(nynorskDump, Nynorsk, Bokmaal)
    writeOutput(transNnNB, outputNnNb)

    val transNbNn = translateDump(bokmaalDump, Bokmaal, Nynorsk)
    writeOutput(transNbNn, outputNbNn)
  }
}
