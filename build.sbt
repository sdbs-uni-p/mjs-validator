name := "mjs-validator"

ThisBuild / version := "1.0"
ThisBuild / scalaVersion := "2.13.10"

javacOptions ++= Seq("-source", "17", "-target", "17")

resolvers += Resolver.mavenCentral

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-library" % "2.13.10"
)

val circeVersion = "0.14.1"
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-generic-extras",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

unmanagedBase := baseDirectory.value / "lib"

assembly / assemblyJarName := "validator.jar"

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x                             => MergeStrategy.first
}

// Add manifest information
Compile / packageBin / packageOptions += Package.ManifestAttributes(
  "Main-Class" -> "Harness",
  "Implementation-Group" -> "org.up.mjs",
  "Implementation-Name" -> "mjs",
  "Implementation-Version" -> "Harness 1.0 | Valdiator b09409db"
)

scalacOptions += "-Ymacro-annotations"
