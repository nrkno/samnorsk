package no.nrk.samnorsk.wikiextractor

import scala.io.{Codec, Source}
import scala.util.parsing.json.JSON


class WikiIterator(source: Source, codec: Codec = Codec.UTF8) extends Iterator[String] {
  val iterator = source.getLines()
  var nextItem = nextObject()

  override def hasNext: Boolean = nextItem.isDefined

  override def next(): String = {
    val text = nextItem.get

    nextItem = nextObject()

    text
  }

  def nextObject(): Option[String] = {
    var next = Option.empty[String]

    while (iterator.hasNext && next.isEmpty) {
      val obj = iterator.next()
      val parse = JSON.parseFull(obj)

      if (parse.isDefined) {
        parse.get match {
          case parse: Map[String, Any] =>
            val text = parse.get("text")

            text match {
              case text: Option[String] => next = text
              case _ => ;
            }
          case _ => ;
        }
      }

    }

    next
  }
}
