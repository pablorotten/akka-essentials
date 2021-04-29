name := "akka-essentials"

version := "0.1"

scalaVersion := "2.13.5"

val AkkaVersion = "2.6.14"
val scalaTestVersion = "3.2.7"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % AkkaVersion,
  "org.scalatest" %% "scalatest" % scalaTestVersion,
  "org.scalactic" %% "scalactic" % scalaTestVersion,
  "org.scalatest" %% "scalatest" % scalaTestVersion
)