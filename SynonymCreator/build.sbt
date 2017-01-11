name := "SynonymCreator"
version := "1.0"
scalaVersion := "2.11.8"
libraryDependencies ++= Seq("info.debatty" % "java-string-similarity" % "0.19",
  "com.fasterxml.jackson.module" % "jackson-module-scala_2.11" % "2.8.4",
  "com.jsuereth" %% "scala-arm" % "2.0",
  "org.scalatest" % "scalatest_2.11" % "3.0.1"
 )
