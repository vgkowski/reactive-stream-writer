name := "mongo-reactive-stream-app"

version := "1.0"

scalaVersion := "2.12.4"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

scalacOptions in Test ++= Seq("-Yrangepos")

libraryDependencies ++= {
  Seq(
    "com.typesafe.akka" % "akka-stream_2.12" % "2.5.6",
    "com.typesafe.akka" % "akka-actor_2.12" % "2.5.6",
    "com.typesafe" % "config" % "1.3.2",
    "com.typesafe.scala-logging" % "scala-logging_2.12" % "3.7.2",
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "io.spray" % "spray-json_2.12" % "1.3.4",
    "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.6" % "test",
    "org.scalatest" %% "scalatest" % "3.0.4" % "test",
    "org.scalactic" %% "scalactic" % "3.0.4",
    "org.reactivemongo" %% "reactivemongo-akkastream" % "0.12.7",
    "org.reactivemongo" %% "reactivemongo" % "0.12.7"
  )
}
