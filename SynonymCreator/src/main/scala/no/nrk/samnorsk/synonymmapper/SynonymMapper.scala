package no.nrk.samnorsk.synonymmapper

import java.io._
import java.lang.Thread.UncaughtExceptionHandler
import java.util.zip.GZIPInputStream

import com.typesafe.scalalogging.slf4j.LazyLogging
import scopt.RenderingMode.TwoColumns

import scala.collection.mutable.ListBuffer
import scala.io.{Codec, Source}

case class DumpDescription(dump: File, fromLanguage: Language, toLanguage: Language)

case class Processor(iter: WikiIterator, runner: ApertiumRunner, counter: TranslationCounter[String, String],
                     fromLanguage: Language, toLanguage: Language)

sealed trait Direction
case object FORWARD extends Direction
case object REVERSE extends Direction
case object BOTH extends Direction

object SynonymMapper extends LazyLogging {

  case class Mapping[A, B](source: A, target: B)

  type StringMapping = Mapping[String, String]

  def processDumps(dumps: Seq[DumpDescription], mapper: TranslationMapper[String, String], limit: Option[Int] = None,
                   filterParams: CounterFilterParams = CounterFilterParams()): Seq[TranslationCounter[String, String]] = {
    val procs = dumps.map(d => Processor(iter = new WikiIterator(Source.fromInputStream(new GZIPInputStream(new FileInputStream(d.dump)))(Codec.UTF8), limit = limit),
      runner = new LocalApertiumRunner(fromLanguage = d.fromLanguage, toLanguage = d.toLanguage),
      counter = new TranslationCounter[String, String](filterParams = filterParams),
      fromLanguage = d.fromLanguage,
      toLanguage = d.toLanguage))

    procs.foreach(p => {
      p.iter.grouped(1000).toStream.par.foreach(articles => {
        p.runner.translate(articles)
          .toSeq.zip(articles)
          .map(at => mapper.map(at._2, at._1).filter(m => m.source != m.target))
          .foreach(m => p.counter.update(m))
      })
    })

    procs.foreach(_.iter.source.close())

    procs.map(_.counter)
  }

  def main(args: Array[String]): Unit = {
    case class Config(nnDump: Option[DumpDescription] = None, nbDump: Option[DumpDescription] = None,
                      output: String = "", reduction: Option[String] = Some(Nynorsk.Name), limit: Option[Int] = None,
                      direction: Direction = FORWARD,
                      mapper: TranslationMapper[String, String] = new EditDistanceMapper(),
                      filterParams: CounterFilterParams = CounterFilterParams())

    Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler {
      override def uncaughtException(t: Thread, e: Throwable): Unit = {
        logger.error(s"Uncaught exception, exiting. ${e.getMessage}")
        sys.exit(1)
      }
    })

    val parser = new scopt.OptionParser[Config]("SynonymMapper") {
      head("SynonymMapper", "0.1.0")

      opt[String]('n', "nn-dump")
        .action((x, c) =>
          c.copy(nnDump = Some(DumpDescription(dump = new File(x), fromLanguage = Nynorsk, toLanguage = Bokmaal))))
        .text("Nynorsk Wikipedia Cirrus dump file.")

      opt[String]('b', "nb-dump")
        .action((x, c) =>
          c.copy(nbDump = Some(DumpDescription(dump = new File(x), fromLanguage = Bokmaal, toLanguage = Nynorsk))))
        .text("Nynorsk Wikipedia Cirrus dump file.")

      opt[Int]('l', "limit")
        .action((x, c) => c.copy(limit = Some(x)))
        .text("Process this number of articles.")

      opt[String]('o', "output")
        .action((x, c) => c.copy(output = x))
        .text("Synonym output file.")
        .required()
      opt[String]('r', "reduction")
        .action((x, c) => c.copy(reduction = Some(x)))
        .text("Synonym reduction language.")

      opt[String]('d', "direction")
        .action((x, c) => c.copy(direction = x match {
          case "forward" => FORWARD
          case "reverse" => REVERSE
          case "both" => BOTH}))
        .text("Synonym file direction.")

      opt[String]('m', "mapper").action((x, c) => c.copy(mapper = x match {
        case "editdist" => new EditDistanceMapper()
        case "linear" => new LinearMapper()}))
        .text("Translation mapping algorithm.")

      opt[Int]('S', "source-tf-filter")
        .action((x, c) => c.copy(filterParams = c.filterParams.copy(sourceTF = x)))
        .text("Minimum term frequency for source words")
      opt[Double]('s', "source-df-filter")
        .action((x, c) => c.copy(filterParams = c.filterParams.copy(sourceIDF = x)))
        .text("Maximum doc frequency for source words")
      opt[Int]('T', "trans-tf-filter")
        .action((x, c) => c.copy(filterParams = c.filterParams.copy(transTF = x)))
        .text("Minimum term frequency for translated words")
      opt[Double]('t', "trans-df-filter")
        .action((x, c) => c.copy(filterParams = c.filterParams.copy(transIDF = x)))
        .text("Maximum doc frequency for translated words")
      opt[Int]('N', "top-n")
        .action((x, c) => c.copy(filterParams = c.filterParams.copy(topN = x)))
        .text("Number of translations to keep")
    }

    parser.parse(args, Config()) match {
      case Some(config) =>
        val output = config.output
        val reductionLanguage = config.reduction

        var dumpDescr = ListBuffer.empty[DumpDescription]

        config.nnDump.foreach(d => dumpDescr += d)
        config.nbDump.foreach(d => dumpDescr += d)

        if (dumpDescr.isEmpty) {
          logger.error("Need at least one dump parameter")
          System.exit(1)
        }

        val counters = processDumps(
          dumpDescr, config.mapper, limit = config.limit, filterParams = config.filterParams).toList

        val counter = counters.head
        counters.tail.foreach(counter.crossMerge)

        if (config.direction == BOTH || config.direction == FORWARD) {
          counter.write(new File(output))
        }

        if (config.direction == BOTH || config.direction == REVERSE) {
          counter.write(new File(output + "-rev"), reverse = true)
        }
      case None =>
        parser.renderUsage(TwoColumns)
    }
  }
}
