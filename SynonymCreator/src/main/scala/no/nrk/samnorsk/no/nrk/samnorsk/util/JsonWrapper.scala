package no.nrk.samnorsk.no.nrk.samnorsk.util

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule

object JsonWrapper {

  private val mapper = new ObjectMapper().registerModule(DefaultScalaModule)

  private def fromString[V](t: String, valueType: Class[V]) = {
    mapper.readValue(t, valueType)
  }

  /**
    * Convert object to valid string json representation.
    */
  def convertToString[T](input: T): String = {
    mapper.writeValueAsString(input)
  }

  /**
    * Convert object to JsonNode
    */
  def convertToJson[T](input: T): JsonNode = {
    input match {
      case s: String => fromString(s, classOf[JsonNode])
      case _ => mapper.valueToTree(input)
    }
  }

  /**
    * Convert from one object to another. NB: Use dedicated methods for
    * converting to String and JsonNode.
    */
  def convert[T,V](input: T, valueType: Class[V]): V = {
    input match {
      case s: String => fromString(s, valueType)
      case node: JsonNode => mapper.treeToValue(node, valueType)
      case _ => mapper.convertValue(input, valueType)
    }
  }
}
