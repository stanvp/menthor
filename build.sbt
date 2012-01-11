organization := "epfl"

name := "libmenthor"

version := "0.1"

scalaVersion := "2.9.1"

resolvers ++= Seq(
  ScalaToolsSnapshots,
  DefaultMavenRepository,
  "Akka Repo" at "http://akka.io/repository",
  "ScalaNLP Maven2" at "http://repo.scalanlp.org/repo",
  "ondex" at "http://ondex.rothamsted.bbsrc.ac.uk/nexus/content/groups/public",
  "OpenNLP Maven Repository" at "http://opennlp.sourceforge.net/maven2",
  "GuiceyFruit Release Repository" at "http://guiceyfruit.googlecode.com/svn/repo/releases/"
)

libraryDependencies ++= Seq(
  "se.scalablesolutions.akka" % "akka" % "1.1"
)
