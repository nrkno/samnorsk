name := "SynonymCreator"
version := "1.0"
scalaVersion := "2.11.8"
scalacOptions ++= Seq("-unchecked", "-deprecation")
libraryDependencies ++= Seq(
  "info.debatty" % "java-string-similarity" % "0.19",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.8.4",
  "com.fasterxml.jackson.core" % "jackson-annotations" % "2.8.4",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.5",
  "com.jsuereth" %% "scala-arm" % "2.0",
  "org.scalatest" %% "scalatest" % "3.0.1",
  "org.scalanlp" %% "epic" % "0.3",
  "com.github.scopt" %% "scopt" % "3.5.0"
)
