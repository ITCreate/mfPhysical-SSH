name := """mfPhysical-SSH"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  ws,
  "org.apache.sshd" % "sshd-core" % "0.13.0",
  "org.scream3r" % "jssc" % "2.8.0",
  "net.debasishg" %% "redisclient" % "2.13"
)
