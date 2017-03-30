package no.nrk.samnorsk.synonymmapper

trait Language {
  val Apertium: String
  val Wiki: String
  val Name: String
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

object Language {
  def apply(lang_code: String): Option[Language] = {
    lang_code match {
      case Nynorsk.Name => Some(Nynorsk)
      case Bokmaal.Name => Some(Bokmaal)
      case _ => None
    }
  }
}