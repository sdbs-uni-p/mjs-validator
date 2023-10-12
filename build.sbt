// Basic settings
name := "validator"

ThisBuild / version := "1.0"
ThisBuild / scalaVersion := "2.13.10"

// Java compatibility
javacOptions ++= Seq("-source", "17", "-target", "17")

// Resolvers
resolvers += Resolver.mavenCentral

// Dependencies
libraryDependencies ++= Seq(
  "com.googlecode.json-simple" % "json-simple" % "1.1.1",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.15.2",
  "org.scala-lang" % "scala-library" % "2.13.10",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.15.2"
)

val circeVersion = "0.14.1"
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-generic-extras",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

// Unmanaged dependency
unmanagedBase := baseDirectory.value / "lib"

// Jar settings
assemblyJarName in assembly := "validator.jar"

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x                             => MergeStrategy.first
}

// Add manifest information
packageOptions in (Compile, packageBin) += Package.ManifestAttributes(
  "Main-Class" -> "Harness",
  "Implementation-Group" -> "org.up.js",
  "Implementation-Name" -> "mjs",
  "Implementation-Version" -> "1.0",
  "Provider-Group" -> "com.googlecode.json-simple",
  "Provider-Name" -> "json-simple",
  "Provider-Version" -> "1.1.1"
)
