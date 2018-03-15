name := "reactive-stream-writer"

version := "1.0"

scalaVersion := "2.12.4"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

scalacOptions in Test ++= Seq("-Yrangepos")

import NativePackagerHelper._

enablePlugins(JavaAppPackaging, DockerPlugin)

/*javaOptions in Universal ++= Seq(
  "-Dconfig.file=/usr/local/etc/container.conf",
  "-Dlog4j.configuration=file:/usr/local/etc/log4j.properties"
)*/

packageName in Docker := packageName.value

version in Docker := version.value

dockerLabels := Map("maintainer" -> "vincent.gromakowski@gmail.com")

dockerBaseImage := "openjdk:9-jre"

dockerRepository := Some("vgkowski")

defaultLinuxInstallLocation in Docker := "/usr/local"

//daemonUser in Docker := "reactive"

mappings in Universal ++= directory( baseDirectory.value / "src" / "main" / "resources" )

libraryDependencies ++= {
  Seq(
    "com.typesafe.akka" % "akka-stream_2.12" % "2.5.11",
    "com.typesafe.akka" % "akka-actor_2.12" % "2.5.11",
    "com.typesafe" % "config" % "1.3.3",
    "com.typesafe.scala-logging" % "scala-logging_2.12" % "3.8.0",
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "io.spray" % "spray-json_2.12" % "1.3.4",
    "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.11" % "test",
    "org.scalatest" %% "scalatest" % "3.0.5" % "test",
    "org.scalactic" %% "scalactic" % "3.0.5",
    "com.lightbend.akka" %% "akka-stream-alpakka-elasticsearch" % "0.17",
    "org.elasticsearch.client" % "elasticsearch-rest-high-level-client" % "6.2.2",
    "com.lightbend.akka" %% "akka-stream-alpakka-mongodb" % "0.17",
    "com.typesafe.akka" %% "akka-stream-kafka" % "0.19",
    "org.apache.kafka" % "kafka-clients" % "1.0.1"
  )
}
