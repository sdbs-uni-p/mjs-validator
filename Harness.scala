import java.util.logging.{Level, Logger}
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.{DeserializationFeature, JsonNode, ObjectMapper}
import java.io._
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import io.circe.{Json, Decoder, Encoder}
import io.circe.generic.semiauto._
import java.util.jar.Manifest
import main.MainClass
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import java.util.Scanner
import shapeless.ops.fin

class Harness {
  val NOT_IMPLEMENTED: String = "This case is not yet implemented."

  val UNSUPPORTED_CASES: Map[String, String] = Map(
    "escaped pointer ref" -> NOT_IMPLEMENTED,
    "empty tokens in $ref json-pointer" -> NOT_IMPLEMENTED,
    "schema that uses custom metaschema with with no validation vocabulary" -> NOT_IMPLEMENTED,
    "small multiple of large integer" -> NOT_IMPLEMENTED,
    "$ref to $ref finds detached $anchor" -> NOT_IMPLEMENTED,
    "$ref to $dynamicRef finds detached $dynamicAnchor" -> NOT_IMPLEMENTED
  )

  val UNSUPPORTED_CASES_SPECIFIC: Map[String, SpecificSkip] = Map(
    "minLength validation" -> SpecificSkip(
      "one supplementary Unicode code point is not long enough",
      NOT_IMPLEMENTED
    ),
    "maxLength validation" -> SpecificSkip(
      "two supplementary Unicode code points is long enough",
      NOT_IMPLEMENTED
    )
  )

  def operate(line: String): String = {
    // var error: String = ""
    try {
      // val node: JsonNode = objectMapper.readTree(line)
      val node: io.circe.Json = parse(line).getOrElse {
        throw new RuntimeException(
          s"Failed to convert node to StartRequest 1: ${line}"
        )
      }
      val cmd: String = node \\ "cmd" match {
        case head :: _ => head.as[String].getOrElse("Not a string")
        case Nil       => "Key1 not found"
      }
      cmd match {
        case "start" =>
          val startRequest = node.as[StartRequest].getOrElse {
            throw new RuntimeException(
              s"Failed to convert node to StartRequest 2: ${node}"
            )
          }
          val version = startRequest.version
          if (version == 1) {
            val is = getClass.getResourceAsStream("META-INF/MANIFEST.MF")
            val attributes = new Manifest(is).getMainAttributes
            Harness.started = true

            val dialects: Json = Json.arr(
              Json.fromString(
                "https://json-schema.org/draft/2020-12/schema"
              )
            )

            val implementation: Json = Json.obj(
              "language" -> Json.fromString("scala"),
              "name" -> Json.fromString(
                attributes.getValue("Implementation-Name")
              ),
              "version" -> Json.fromString(
                attributes.getValue("Implementation-Version")
              ),
              "homepage" -> Json.fromString(
                "https://gitlab.lip6.fr/jsonschema/modernjsonschemavalidator"
              ),
              "issues" -> Json.fromString(
                "https://gitlab.lip6.fr/jsonschema/modernjsonschemavalidator/issues"
              ),
              "dialects" -> dialects
            )

            val message: Json = Json.obj(
              "ready" -> Json.fromBoolean(true),
              "version" -> Json.fromInt(1),
              "implementation" -> implementation
            )
            message.asJson.noSpaces
          } else {
            throw new RuntimeException(
              s"Unsupported version: ${startRequest.version}"
            )
          }
        case "dialect" =>
          if (!Harness.started) {
            throw new RuntimeException("Bowtie hasn't started!")
          }
          val dialectRequest = node.as[DialectRequest].getOrElse {
            throw new RuntimeException(
              s"Failed to convert node to DialectRequest: ${node}"
            )
          }
          "{ \"ok\" : false }"
        case "run" =>
          if (!Harness.started) {
            throw new RuntimeException("Bowtie hasn't started!")
          }
          val runReq: Either[io.circe.DecodingFailure, RunRequest] =
            node.as[RunRequest]

          val runRequest: RunRequest = runReq match {
            case Right(value) => value
            case Left(decodingFailure) =>
              val errorMsg = decodingFailure.message
              throw new Exception(s"Decoding Failure: $errorMsg")
          }

          try {
            val caseDescription = runRequest.testCase.description
            if (UNSUPPORTED_CASES.contains(caseDescription)) {
              return skipMsg(
                UNSUPPORTED_CASES(caseDescription),
                runRequest.seq
                  .as[Long]
                  .getOrElse(throw new Exception("Failed to convert to Long"))
              )
            }

            val registryMap: Map[String, String] = runRequest.testCase.registry
              .flatMap { node =>
                node.as[Map[String, Json]].toOption.map { jsonMap =>
                  jsonMap
                    .mapValues(
                      _.noSpaces
                    )
                    .toMap
                }
              }
              .getOrElse(null)

            // Initialize an empty Vector to hold Json objects
            var resultArray = Vector.empty[Json]

            // Convert Java list to Scala sequence
            val tests: List[Test] = runRequest.testCase.tests

            tests.foreach { test =>
              val testDescription = test.description
              val instance = test.instance.noSpaces

              val skip = UNSUPPORTED_CASES_SPECIFIC.contains(
                caseDescription
              ) && UNSUPPORTED_CASES_SPECIFIC(caseDescription).description == testDescription
              if (skip) {
                val foundSkip =
                  UNSUPPORTED_CASES_SPECIFIC(caseDescription)
                // TODO: Check skipMSG - why are there two?
                resultArray :+= skipMsg(foundSkip.message)

              } else {
                val sch: String = runRequest.testCase.schema.noSpaces

                val results =
                  MainClass.validateInstance(sch, instance, registryMap)

                // Create Json object and add to the resultArray
                val result = Json.obj(
                  "valid" -> results.asJson
                )
                resultArray :+= result
              }
            }
            val finalResultArray = Json.arr(resultArray: _*)
            val out: Json = Json.obj(
              "seq" -> Json.fromLong(
                runRequest.seq
                  .as[Long]
                  .getOrElse(throw new Exception("Failed to convert to Long"))
              ),
              "results" -> finalResultArray
            )
            out.asJson.noSpaces

          } catch {
            case e: Exception =>
              // val msg =
              //   getDetailedMessage(
              //     e,
              //     runRequest.testCase \\ "schema" match {
              //       case head :: _ => head.noSpaces
              //       case Nil       => throw new Exception("Key5 not found")
              //     }
              //   )
              // val error = errorMsg(
              //   e.getMessage(),
              //   runRequest.seq
              //     .as[Long]
              //     .getOrElse(throw new Exception("Failed to convert to Long"))
              // )
              s"123 ${e}"
            // error
          }
        case "stop" =>
          if (!Harness.started) {
            throw new RuntimeException("Bowtie hasn't started!")
          }
          System.exit(0)
          errorMsg("Stopped", -1);
      }

    } catch {
      case e: Exception =>
        val trace = e.getStackTrace.mkString("  ")
        // print(s"Exception: ${e.getMessage} ${trace}")
        s"{\"error123\": \"${e.getMessage}  ${trace}\"}"
      // errorMsg(e.getMessage, -1)
    }
  }

  def errorMsg(message: String, seq: Long): String = {
    val traceBack: Json = Json.obj(
      "traceBack" -> Json.fromString(message)
    )
    val error: Json = Json.obj(
      "errored" -> Json.fromBoolean(true),
      "seq" -> Json.fromLong(seq),
      "context" -> Json.fromString(traceBack.asJson.noSpaces)
    )
    error.asJson.noSpaces
  }

  def skipMsg(message: String, seq: Long): String = {
    val error: Json = Json.obj(
      "skipped" -> Json.fromBoolean(true),
      "seq" -> Json.fromLong(seq),
      "message" -> Json.fromString(message)
    )
    error.asJson.noSpaces
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
    // e.printStackTrace(new PrintWriter(sw))
    sw.toString + " " + schema
  }
}

case class StartRequest(version: Int)
case class DialectRequest(dialect: String)

// Define the Test case class and its Circe encoders and decoders
case class Test(
    description: String,
    comment: Option[String],
    instance: Json,
    valid: Option[Boolean]
)

// Define the TestCase case class and its Circe encoders and decoders
case class TestCase(
    description: String,
    comment: Option[String],
    schema: Json,
    registry: Option[Json],
    tests: List[Test]
)

case class SpecificSkip(description: String, message: String)

case class RunRequest(seq: Json, testCase: TestCase)

object Harness {
  var started: Boolean = false
  val LOGGER: Logger = Logger.getLogger(classOf[Harness].getName)

  def main(args: Array[String]): Unit = {
    val input = new Scanner(System.in)
    while (true) {
      val line = input.nextLine()
      val output = new Harness().operate(line)
      println(output)
    }
  }
}

object TestCase {
  implicit val testCaseDecoder: Decoder[TestCase] = deriveDecoder[TestCase]
  implicit val testCaseEncoder: Encoder[TestCase] = deriveEncoder[TestCase]
}

object RunRequest {
  implicit val runRequestEncoder: Encoder[RunRequest] =
    new Encoder[RunRequest] {
      final def apply(a: RunRequest): Json = Json.obj(
        ("seq", a.seq),
        ("case", TestCase.testCaseEncoder(a.testCase))
      )
    }

  implicit val runRequestDecoder: Decoder[RunRequest] =
    new Decoder[RunRequest] {
      final def apply(c: HCursor): Decoder.Result[RunRequest] =
        for {
          seq <- c.downField("seq").as[Json]
          testCase <- c.downField("case").as[TestCase]
        } yield {
          RunRequest(seq, testCase)
        }
    }
}
