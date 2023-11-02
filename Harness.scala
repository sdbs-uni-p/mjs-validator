// TODO: Prints
import java.io._
import io.circe.generic.auto._
import io.circe.generic.semiauto._
import io.circe.parser.parse
import io.circe.syntax.EncoderOps
import io.circe.{Json, Decoder, Encoder, HCursor, ParsingFailure}
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec, JsonKey}
import java.util.jar.Manifest
import main.MainClass
import java.util.Scanner

class Harness {
  val NOT_IMPLEMENTED: String = "This case is not yet implemented."

  val SKIPPED_CASES: Map[String, String] = Map(
    "escaped pointer ref" -> NOT_IMPLEMENTED,
    "empty tokens in $ref json-pointer" -> NOT_IMPLEMENTED,
    "schema that uses custom metaschema with with no validation vocabulary" -> NOT_IMPLEMENTED,
    "small multiple of large integer" -> NOT_IMPLEMENTED,
    "$ref to $ref finds detached $anchor" -> NOT_IMPLEMENTED,
    "$ref to $dynamicRef finds detached $dynamicAnchor" -> NOT_IMPLEMENTED
  )

  val SKIPPED_TESTS: Map[String, TestSkip] = Map(
    "minLength validation" -> TestSkip("one supplementary Unicode code point is not long enough", NOT_IMPLEMENTED),
    "maxLength validation" -> TestSkip("two supplementary Unicode code points is long enough", NOT_IMPLEMENTED)
  )

  implicit val customConfig: Configuration = Configuration.default.withDefaults

  def operate(line: String) = {
    try {
      val node: Json = parse(line) match {
        case Right(json)          => json
        case Left(parsingFailure) => throw parsingFailure
      }

      val cmd: String = (node \\ "cmd").headOption match {
        case Some(value) => value.asString.getOrElse("")
        case None        => throw new RuntimeException("Failed to get cmd")
      }
      cmd match {
        case "start"   => println(start(node))
        case "dialect" => println(dialect(node))
        case "run"     => println(run(node))
        case "stop"    => System.exit(0)
        case default   => throw new IllegalArgumentException(s"Unknown command: $default")
      }
    } catch {
      case e: Exception =>
        // TODO: Check if this is the correct way to handle errors
        println(Errored(e.getMessage(), e.getStackTrace().mkString("\n")).asJson.noSpaces)
    }
  }

  def start(node: Json): String = {
    val startRequest: StartRequest = decodeTo[StartRequest](node)
    val version = startRequest.version
    if (version == 1) {
      Harness.started = true
      val is: InputStream = getClass.getResourceAsStream("META-INF/MANIFEST.MF")
      val attributes = new Manifest(is).getMainAttributes

      val dialects: Json = Json.arr(Json.fromString("https://json-schema.org/draft/2020-12/schema"))

      val implementation: Json = Json.obj(
        "language" -> Json.fromString("scala"),
        "name" -> Json.fromString(attributes.getValue("Implementation-Name")),
        "version" -> Json.fromString(attributes.getValue("Implementation-Version")),
        "homepage" -> Json.fromString("https://gitlab.lip6.fr/jsonschema/modernjsonschemavalidator"),
        "issues" -> Json.fromString("https://gitlab.lip6.fr/jsonschema/modernjsonschemavalidator/issues"),
        "dialects" -> dialects,
        "os" -> Json.fromString(System.getProperty("os.name")),
        "os_version" -> Json.fromString(System.getProperty("os.version")),
        "language_version" -> Json.fromString(Runtime.version().toString)
      )

      StartResponse(version, true, implementation).asJson.noSpaces
    } else {
      // TODO: should return an errored response?
      throw new IllegalArgumentException(s"Unsupported version: ${startRequest.version}")
    }
  }

  def dialect(node: Json): String = {
    if (!Harness.started) {
      throw new RuntimeException("Bowtie hasn't started!")
    }
    val dialectRequest: DialectRequest = decodeTo[DialectRequest](node)
    // TODO: Handle properly (if this is not correct); We should probably return true
    DialectResponse(false).asJson.noSpaces
  }

  def run(node: Json): String = {
    if (!Harness.started) {
      throw new RuntimeException("Bowtie hasn't started!")
    }

    val runRequest: RunRequest = decodeTo[RunRequest](node)
    try {
      val caseDescription = runRequest.testCase.description
      if (SKIPPED_CASES.contains(caseDescription)) {
        return SkippedRunResponse(runRequest.seq, true, Some(NOT_IMPLEMENTED)).asJson.noSpaces
      }

      val registryMap: Map[String, String] = runRequest.testCase.registry
        .flatMap { node =>
          node.as[Map[String, Json]].toOption.map { jsonMap => jsonMap.mapValues(_.noSpaces).toMap }
          node.as[Map[String, Json]].toOption.map { jsonMap => jsonMap.mapValues(_.noSpaces).toMap }
        }
        .getOrElse(null)

      var resultArray = Vector.empty[Json]
      runRequest.testCase.tests.foreach { test =>
        try {
          val testDescription: String = test.description
          val instance: String = test.instance.noSpaces

          if (SKIPPED_TESTS.contains(caseDescription) && SKIPPED_TESTS(caseDescription).description == testDescription) {
            resultArray :+= SkippedTest(message = Some(SKIPPED_TESTS(caseDescription).message)).asJson
          } else {
            val schema: String = runRequest.testCase.schema.noSpaces

            val result: Json = Json.obj("valid" -> MainClass.validateInstance(schema, instance, registryMap).asJson)
            resultArray :+= result
          }
        } catch {
          // TODO: Testing
          case e: Exception => {
            val error: Errored = Errored(e.getMessage(), e.getStackTrace().mkString("\n"))
            resultArray :+= ErroredTest(true, error).asJson
          }
        }
      }
      RunResponse(runRequest.seq, resultArray).asJson.noSpaces

    } catch {
      // TODO: We currently abort instead of creating errored responses?
      case e: Exception =>
        val error: Errored = Errored(e.getMessage(), e.getStackTrace().mkString("\n"))
        ErroredRunResponse(runRequest.seq, context = error).asJson.noSpaces
    }
  }

  def decodeTo[Request: Decoder](json: Json): Request = {
    json.as[Request] match {
      case Right(value)          => value
      case Left(decodingFailure) => throw decodingFailure
    }
  }
}

// TODO: Check if we need any more case classes

case class Test(description: String, comment: Option[String], instance: Json, valid: Option[Boolean])

case class TestCase(description: String, comment: Option[String], schema: Json, registry: Option[Json], tests: List[Test])

case class StartRequest(version: Int)

case class StartResponse(version: Int, ready: Boolean, implementation: Json)

case class DialectRequest(dialect: String)

case class DialectResponse(ok: Boolean)

@ConfiguredJsonCodec
case class RunRequest(seq: Json, @JsonKey("case") testCase: TestCase)

object RunRequest {
  implicit val config: Configuration = Configuration.default.withSnakeCaseMemberNames.withDefaults
}

case class RunResponse(seq: Json, results: Vector[Json])

case class ErroredRunResponse(seq: Json, errored: Boolean = true, context: Errored)

case class ErroredTest(errored: Boolean = true, context: Errored) // TODO: restructure?

case class Errored(message: String, traceback: String) // TODO: needed?

case class SkippedRunResponse(seq: Json, skipped: Boolean = true, message: Option[String] = None)

case class SkippedTest(skipped: Boolean = true, message: Option[String] = None)

case class TestSkip(description: String, message: String)

object Harness {
  var started: Boolean = false

  def main(args: Array[String]): Unit = {
    val input = new Scanner(System.in)
    while (true) {
      new Harness().operate(input.nextLine())
    }
  }
}
