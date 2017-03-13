package no.nrk.samnorsk.wikiextractor

import java.io.{File, FileWriter}

import com.typesafe.scalalogging.slf4j.LazyLogging
import no.nrk.samnorsk.synonymmapper.SynonymMapper.Mapping

import scala.collection.mutable

class TranslationCounter[A, B](sourceTfFilter: Int = 1, sourceDfFilter: Double = 1.0,
                               transTfFilter: Int = 1, transDfFilter: Double = 1.0,
                               topN: Option[Int] = Option.empty) extends LazyLogging {
  private val translations = mutable.HashMap[A, mutable.Map[B, Int]]()
  private val sourceTf = mutable.HashMap[A, Int]()
  private val sourceDf = mutable.HashMap[A, Int]()
  private val transTf = mutable.HashMap[B, Int]()
  private val transDf = mutable.HashMap[B, Int]()
  private var _docCount = 0

  def docCount:Int = _docCount

  def update(pairs: Seq[Mapping[A, B]]): TranslationCounter[A, B] = {
    _docCount += 1

    pairs.foreach { m =>
      translations(m.source) = translations.getOrElse(m.source, mutable.Map())
      translations(m.source)(m.target) = translations(m.source).getOrElse(m.target, 0) + 1
    }

    pairs.map(_.source)
      .groupBy(identity)
      .mapValues(_.size)
      .foreach((e: (A, Int)) => e match {
        case (k, v) => sourceTf(k) = sourceTf.getOrElse(k, 0) + v
      })

    pairs.map(_.target)
      .groupBy(identity)
      .mapValues(_.size)
      .foreach((e: (B, Int)) => e match {
        case (k, v) => transTf(k) = transTf.getOrElse(k, 0) + v
      })

    pairs.map(_.source).toSet.foreach((k: A) => sourceDf(k) = sourceDf.getOrElse(k, 0) + 1)
    pairs.map(_.target).toSet.foreach((k: B) => transDf(k) = transDf.getOrElse(k, 0) + 1)

    this
  }

  def get(source: A): Seq[B] = {
    if (translations.contains(source)
      && sourceTf.getOrElse(source, 0) >= sourceTfFilter
      && sourceDf.getOrElse(source, _docCount).toDouble / _docCount <= sourceDfFilter) {

      val candidates = translations.getOrElse(source, mutable.Map[B, Int]()).keys.filter(e => {
        transTf.getOrElse(e, 0) >= transTfFilter && transDf.getOrElse(e, _docCount).toDouble / _docCount <= transDfFilter
      })

      candidates.toSeq
        .map(trans => (trans, translations(source).getOrElse(trans, 0)))
        .sortWith(_._2 > _._2)
        .map(_._1)
        .take(topN.getOrElse(1))
    }
    else {
      Seq.empty
    }
  }

  def write(output: File): Unit = {
    logger.info(s"Writing output to ${output.getAbsolutePath}")

    val writer = new FileWriter(output)

    for (source <- translations.keys) {
      val trans = get(source)

      if (trans.nonEmpty) {
        writer.write(s"$source\t${trans.mkString(" ")}\n")
      }
    }

    writer.close()
  }

  def crossMerge(counter: TranslationCounter[B, A]): TranslationCounter[A, B] = {
    _docCount += counter.docCount

    counter.sourceTf.foreach { case (k, v) => transTf(k) = transTf.getOrElse(k, 0) + v }
    counter.transTf.foreach { case (k, v) => sourceTf(k) = sourceTf.getOrElse(k, 0) + v }
    counter.sourceDf.foreach { case (k, v) => transDf(k) = transDf.getOrElse(k, 0) + v }
    counter.transDf.foreach { case (k, v) => sourceDf(k) = sourceDf.getOrElse(k, 0) + v }

    counter.translations.foreach {
      case (s, trans) =>
        trans.foreach {
          case (t, v) =>
            translations(t) = translations.getOrElse(t, mutable.Map())
            translations(t)(s) = translations(t).getOrElse(s, 0) + v
        }
    }

    this
  }
}
