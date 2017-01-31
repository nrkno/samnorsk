package no.nrk.samnorsk.wikiextractor

import com.typesafe.scalalogging.slf4j.LazyLogging

import scala.io.Source
import scala.util.parsing.json.JSON


object WikiIterator {
  def apply(source: Source): WikiIterator = new WikiIterator(source)
}

class WikiIterator(source: Source, limit: Option[Int] = None) extends Iterator[String] with LazyLogging {
  private var count = 0
  private val iterator = source.getLines()
  private var nextItem = nextObject()

  override def hasNext: Boolean = nextItem.isDefined

  override def next(): String = {
    val text = nextItem.get

    nextItem = nextObject()

    text
  }

  def _belowLimit(c: Int): Boolean = {
    limit match {
      case Some(l) => c < l
      case None => true
    }
  }

  def nextObject(): Option[String] = {
    var next: Option[String] = None

    if (!_belowLimit(count)) {
      next
    }
    else {
      while (next.isEmpty && iterator.hasNext) {
        val obj = iterator.next()
        val parse = JSON.parseFull(obj)

        if (parse.isDefined) {
          parse.get match {
            case parse: Map[_, _] =>
              val p = parse.asInstanceOf[Map[String, Any]]
              val text = p.get("text")

              text match {
                case Some(t:String) =>
                  next = Some(t)
                  count += 1
                  if (count % 1000 == 0) {
                    logger.info(s"Read $count articles ...")
                  }
                case _ => ;
              }
            case _ => ;
          }
        }
      }
    }

    next
  }
}
