import java.io._
import io.circe.generic.auto._
import io.circe.generic.semiauto._
import io.circe.parser.{parse}
import io.circe.syntax.EncoderOps
import io.circe.{Json, Decoder, Encoder, HCursor, ParsingFailure}
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec, JsonKey}
import java.util.jar.Manifest
import main.MainClass
import java.util.Scanner
import cats.instances.seq

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

  // TODO: Find nicer solution
  val SKIP_TESTS: Map[String, SpecificSkip] = Map(
    "minLength validation" -> SpecificSkip("one supplementary Unicode code point is not long enough", NOT_IMPLEMENTED),
    "maxLength validation" -> SpecificSkip("two supplementary Unicode code points is long enough", NOT_IMPLEMENTED)
  )

  implicit val customConfig: Configuration = Configuration.default.withDefaults

  def operate(line: String): String = {
    try {
      val node: io.circe.Json = parse(line) match {
        case Right(json) => json
        case Left(error) => throw error
      }
      val cmd: String = (node \\ "cmd").headOption match {
        case Some(value) => value.asString.getOrElse("")
        case None        => throw new Exception("Failed to get cmd")
      }
      cmd match {
        case "start"   => start(node)
        case "dialect" => dialect(node)
        case "run"     => run(node)
        case "stop"    => stop(node)
      }
    } catch {
      case e: Exception =>
        // TODO: Check all error messages
        errorMsg(e.getMessage, -1)
    }
  }

  def start(node: Json): String = {
    val startRequest: StartRequest = decodeTo[StartRequest](node)
    val version = startRequest.version
    if (version == 1) {
      val is = getClass.getResourceAsStream("META-INF/MANIFEST.MF")
      val attributes = new Manifest(is).getMainAttributes
      Harness.started = true

      val dialects: Json = Json.arr(Json.fromString("https://json-schema.org/draft/2020-12/schema"))

      val implementation: Json = Json.obj(
        "language" -> Json.fromString("scala"),
        "name" -> Json.fromString(attributes.getValue("Implementation-Name")),
        "version" -> Json.fromString(attributes.getValue("Implementation-Version")),
        "homepage" -> Json.fromString("https://gitlab.lip6.fr/jsonschema/modernjsonschemavalidator"),
        "issues" -> Json.fromString("https://gitlab.lip6.fr/jsonschema/modernjsonschemavalidator/issues"),
        "dialects" -> dialects
      )

      val message: Json = Json.obj(
        "ready" -> Json.fromBoolean(true),
        "version" -> Json.fromInt(1), // TODO: Version
        "implementation" -> implementation
      )
      message.noSpaces
    } else {
      throw new RuntimeException(s"Unsupported version: ${startRequest.version}")
    }
  }

  def dialect(node: Json): String = {
    if (!Harness.started) {
      throw new RuntimeException("Bowtie hasn't started!")
    }
    val dialectRequest: DialectRequest = decodeTo[DialectRequest](node)
    // TODO: Handle properly (if this is not correct)
    "{ \"ok\" : false }"
  }

  def run(node: Json): String = {
    if (!Harness.started) {
      throw new RuntimeException("Bowtie hasn't started!")
    }

    val runRequest: RunRequest = decodeTo[RunRequest](node)
    try {
      val caseDescription = runRequest.testCase.description
      if (SKIP_CASES.contains(caseDescription)) {
        return skipMsg(SKIP_CASES(caseDescription), seqAsLong(runRequest.seq))
      }

      val registryMap: Map[String, String] = runRequest.testCase.registry.getOrElse(null)
      var resultArray = Vector.empty[Json]
      val tests: List[Test] = runRequest.testCase.tests

      tests.foreach { test =>
        val testDescription = test.description
        val instance = test.instance.noSpaces

        if (SKIP_TESTS.contains(caseDescription) && SKIP_TESTS(caseDescription).description == testDescription) {
          resultArray :+= skipMsg(SKIP_TESTS(caseDescription).message) // TODO: Check skipMSG - why are there two?
        } else {
          val schema: String = runRequest.testCase.schema.noSpaces
          val result = Json.obj("valid" -> MainClass.validateInstance(schema, instance, registryMap).asJson)
          resultArray :+= result
        }
      }
      val finalResultArray = Json.arr(resultArray: _*)
      val out: Json = Json.obj(
        "seq" -> runRequest.seq,
        "results" -> finalResultArray
      )
      out.noSpaces

    } catch {
      case e: Exception =>
        val msg = getDetailedMessage(e, runRequest.testCase.schema.noSpaces)
        val error = errorMsg(e.getMessage(), seqAsLong(runRequest.seq))
        error
    }
  }

  def stop(node: Json): String = {
    if (!Harness.started) {
      throw new RuntimeException("Bowtie hasn't started!")
    }
    System.exit(0)
    errorMsg("Stopped", -1); // TODO: why -1?
  }

  def errorMsg(message: String, seq: Long): String = {
    val traceBack: Json = Json.obj(
      "traceBack" -> Json.fromString(message)
    )
    val error: Json = Json.obj(
      "errored" -> Json.fromBoolean(true),
      "seq" -> Json.fromLong(seq),
      "context" -> Json.fromString(traceBack.noSpaces)
    )
    error.noSpaces
  }

  def skipMsg(message: String, seq: Long): String = {
    val error: Json = Json.obj(
      "skipped" -> Json.fromBoolean(true),
      "seq" -> Json.fromLong(seq),
      "message" -> Json.fromString(message)
    )
    error.noSpaces
  }

  def skipMsg(message: String): Json = {
    val error: Json = Json.obj(
      "skipped" -> Json.fromBoolean(true),
      "message" -> Json.fromString(message)
    )
    error
  }

  def getDetailedMessage(e: Exception, schema: String): String = {
    val sw = new StringWriter()
    sw.toString + " " + schema
  }

  def seqAsLong(seq: Json): Long = {
    seq.as[Long].getOrElse(throw new Exception("Failed to convert to Long"))
  }

  def decodeTo[Request: Decoder](json: Json): Request = {
    json.as[Request] match {
      case Right(value) => value
      case Left(error)  => throw error
    }
  }

}

case class StartRequest(version: Int)

case class DialectRequest(dialect: String)

case class Test(
    description: String,
    comment: Option[String],
    instance: Json,
    valid: Option[Boolean]
)

case class TestCase(
    description: String,
    comment: Option[String],
    schema: Json,
    registry: Option[Map[String, String]],
    tests: List[Test]
)

object TestCase {
  implicit val decodeRegistry: Decoder[Option[Map[String, String]]] = Decoder[Json].map { json =>
    json.asObject.map(_.toMap.view.mapValues(_.noSpaces).toMap)
  }
  implicit val testCaseDecoder: Decoder[TestCase] = deriveDecoder[TestCase]
  implicit val testCaseEncoder: Encoder[TestCase] = deriveEncoder[TestCase]
}

@ConfiguredJsonCodec
case class RunRequest(seq: Json, @JsonKey("case") testCase: TestCase)

object RunRequest {
  implicit val config: Configuration = Configuration.default.withSnakeCaseMemberNames.withDefaults
}

case class SpecificSkip(description: String, message: String)

object Harness {
  var started: Boolean = false

  def main(args: Array[String]): Unit = {
    val input = new Scanner(System.in)
    while (true) {
      val line = input.nextLine()
      val output = new Harness().operate(line)
      println(output)
    }
  }
}
