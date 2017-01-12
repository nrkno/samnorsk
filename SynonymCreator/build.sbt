name := "SynonymCreator"
version := "1.0"
scalaVersion := "2.11.8"
libraryDependencies ++= Seq(
  "info.debatty" % "java-string-similarity" % "0.19",
  "com.fasterxml.jackson.module" % "jackson-module-scala_2.11" % "2.8.4",
  "com.fasterxml.jackson.core" % "jackson-annotations" % "2.8.4",
  "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.5",
  "com.jsuereth" %% "scala-arm" % "2.0",
  "org.scalatest" % "scalatest_2.11" % "3.0.1",
  "org.scalanlp" %% "epic" % "0.3"
 )
