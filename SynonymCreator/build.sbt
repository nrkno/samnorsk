name := "SynonymCreator"

version := "1.0"

scalaVersion := "2.12.0"

libraryDependencies ++= Seq("info.debatty" % "java-string-similarity" % "0.19")

mainClass in (Compile, run) := Some("no.nrk.samnorsk.synonymmapper.SynonymMapper")
