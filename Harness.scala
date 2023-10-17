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

  val SKIP_CASES: Map[String, String] = Map(
    "escaped pointer ref" -> NOT_IMPLEMENTED,
    "empty tokens in $ref json-pointer" -> NOT_IMPLEMENTED,
    "schema that uses custom metaschema with with no validation vocabulary" -> NOT_IMPLEMENTED,
    "small multiple of large integer" -> NOT_IMPLEMENTED,
    "$ref to $ref finds detached $anchor" -> NOT_IMPLEMENTED,
    "$ref to $dynamicRef finds detached $dynamicAnchor" -> NOT_IMPLEMENTED
  )

  val SKIP_TESTS: Map[String, SpecificSkip] = Map(
    "minLength validation" -> SpecificSkip("one supplementary Unicode code point is not long enough", NOT_IMPLEMENTED),
    "maxLength validation" -> SpecificSkip("two supplementary Unicode code points is long enough", NOT_IMPLEMENTED)
  )

  implicit val customConfig: Configuration = Configuration.default.withDefaults

  def operate(line: String) = {
    try {
      val node: io.circe.Json = parse(line) match {
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
        println(Error(e.getMessage(), e.getStackTrace().mkString("\n")).asJson.noSpaces)
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
    // TODO: Handle properly (if this is not correct)
    DialectResponse(false).asJson.noSpaces
  }

  def run(node: Json): String = {
    if (!Harness.started) {
      throw new RuntimeException("Bowtie hasn't started!")
    }

    val runRequest: RunRequest = decodeTo[RunRequest](node)

    val caseDescription = runRequest.testCase.description
    if (SKIP_CASES.contains(caseDescription)) {
      return SkippedRunResponse(runRequest.seq, true, Some(NOT_IMPLEMENTED)).asJson.noSpaces // TODO: return here or add to array?
      // if we add to array, we only need SkippedTest
    }

    val registryMap: Map[String, String] = runRequest.testCase.registry
      .flatMap { node =>
        node.as[Map[String, Json]].toOption.map { jsonMap => jsonMap.mapValues(_.noSpaces).toMap }
        node.as[Map[String, Json]].toOption.map { jsonMap => jsonMap.mapValues(_.noSpaces).toMap }
      }
      .getOrElse(null)

    var resultArray = Vector.empty[Json]
    try {
      runRequest.testCase.tests.foreach { test =>
        val testDescription = test.description
        val instance = test.instance.noSpaces

        if (SKIP_TESTS.contains(caseDescription) && SKIP_TESTS(caseDescription).description == testDescription) {
          resultArray :+= SkippedTest(message = Some(SKIP_TESTS(caseDescription).message)).asJson
        } else {
          val schema: String = runRequest.testCase.schema.noSpaces
          val result = Json.obj("valid" -> MainClass.validateInstance(schema, instance, registryMap).asJson)
          resultArray :+= result
        }
      }
      RunResponse(runRequest.seq, resultArray).asJson.noSpaces

    } catch {
      // We currently abort instead of creating errored responses?
      case e: Exception =>
        val error: Error = Error(e.getMessage(), e.getStackTrace().mkString("\n"))
        ErrorRunResponse(runRequest.seq, context = error).asJson.noSpaces
    }
  }

  def decodeTo[Request: Decoder](json: Json): Request = {
    json.as[Request] match {
      case Right(value)          => value
      case Left(decodingFailure) => throw decodingFailure
    }
  }
}

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

case class ErrorRunResponse(seq: Json, errored: Boolean = true, context: Error)

case class Error(message: String, traceback: String) // TODO: needed?

case class SkippedRunResponse(seq: Json, skipped: Boolean = true, message: Option[String] = None)

case class SkippedTest(skipped: Boolean = true, message: Option[String] = None)

case class SpecificSkip(description: String, message: String)

object Harness {
  var started: Boolean = false

  def main(args: Array[String]): Unit = {
    val input = new Scanner(System.in)
    while (true) {
      new Harness().operate(input.nextLine())
    }
  }
}
