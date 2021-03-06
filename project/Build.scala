import sbt._
import sbt.Keys._

object FinatraBuild extends Build {

  lazy val dependsOnScalaVersion = (scalaVersion) {
    v => {
      val scalaTest = v match {
        case "2.9.2" => "org.scalatest" %% "scalatest" % "1.7.2"
        case "2.10.1" => "org.scalatest" %% "scalatest" % "1.9.1"
      }
      Seq(
        scalaTest
      )
    }
  }

  def publish = publishTo <<= (version) {
    version: String =>
      if (version.trim.endsWith("SNAPSHOT")) {
        Some(Resolver.file("snaphost", new File("./repos/snapshot")))
      } else {
        Some(Resolver.file("release", new File("./repos/release")))
      }
  }

  lazy val root = Project(
    id = "finatra",
    base = file("."),
    settings = Project.defaultSettings ++ Seq(
      name := "finatra",
      organization := "com.twitter",
      version := "1.3.2-SNAPSHOT",
      scalaVersion := "2.9.2",
      crossScalaVersions := Seq("2.9.2", "2.10.1"),
      resolvers ++= Seq(
        "twttr-repo" at "http://maven.twttr.com/"
      ),
      libraryDependencies ++= Seq(
        "com.twitter" %% "finagle-core" % "6.1.0",
        "com.twitter" %% "finagle-http" % "6.1.0",
        "com.twitter" %% "util-logging" % "6.1.0",
        "com.twitter" %% "ostrich" % "9.1.0",
        "commons-io" % "commons-io" % "1.3.2",
        "io.backchat.jerkson" % "jerkson_2.9.2" % "0.7.0",
        "com.github.spullara.mustache.java" % "compiler" % "0.8.8"
      ),
      libraryDependencies <++= dependsOnScalaVersion,
      publish
    )
  )
}
