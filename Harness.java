import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import main.MainClass$;
import java.io.StringWriter;
import java.io.PrintWriter;
import scala.Tuple2;
import java.io.*;
import io.circe.Json;
import java.util.jar.Manifest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
* The Harness program implements an application that
* simply take input from Bowtie application using standard
* input and output to the standard output.
*
* @author  Sajal Jain
* @version 1.0
* @since   2023-01-10 
*/
class Harness{
	
	public static boolean started = false;
	
	private static final Logger LOGGER = Logger.getLogger( Harness.class.getName() );
	
	private final ObjectMapper objectMapper = new ObjectMapper().configure(
      DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	
	private static final String NOT_IMPLEMENTED = "This case is not yet implemented.";
	private static final Map<String, String> UNSUPPORTED_CASES = Map.ofEntries(
		Map.entry("escaped pointer ref", NOT_IMPLEMENTED), Map.entry("empty tokens in $ref json-pointer", NOT_IMPLEMENTED),
		Map.entry("schema that uses custom metaschema with with no validation vocabulary", NOT_IMPLEMENTED),
		Map.entry("small multiple of large integer", NOT_IMPLEMENTED), Map.entry("$ref to $ref finds detached $anchor", NOT_IMPLEMENTED),
		Map.entry("$ref to $dynamicRef finds detached $dynamicAnchor", NOT_IMPLEMENTED));
    private static final Map<String, SpecificSkip> UNSUPPORTED_CASES_SPECIFIC = Map.ofEntries(
        Map.entry("minLength validation", new SpecificSkip("one supplementary Unicode code point is not long enough", NOT_IMPLEMENTED)), 
        Map.entry("maxLength validation", new SpecificSkip("two supplementary Unicode code points is long enough", NOT_IMPLEMENTED)));
	
	public static void main(String[] args) {
		Scanner input = new Scanner(System.in);
		while (true) {
			String line = input.nextLine();
			String output = new Harness().operate(line);
			System.out.println(output);
		}
	}
	
	public String operate(String line){
		String error = "";
		try{
			JsonNode node = objectMapper.readTree(line);
			String cmd = node.get("cmd").asText();
			switch(cmd) {
			  case "start":
				StartRequest startRequest = objectMapper.treeToValue(node, StartRequest.class);
				long version = startRequest.version();
				if(version == 1){
					InputStream is = getClass().getResourceAsStream("META-INF/MANIFEST.MF");
					var attributes = new Manifest(is).getMainAttributes();
					started = true;
					JSONObject message = new JSONObject();
					message.put("ready", true);
					message.put("version", 1);
					JSONObject implementation = new JSONObject();
					implementation.put("language", "scala");
					implementation.put("name", attributes.getValue("Implementation-Name"));
					implementation.put("version", attributes.getValue("Implementation-Version"));
					implementation.put("homepage", "https://gitlab.lip6.fr/jsonschema/modernjsonschemavalidator");
					implementation.put("issues", "https://gitlab.lip6.fr/jsonschema/modernjsonschemavalidator/issues");
					JSONArray dialects = new JSONArray();
					dialects.add("https://json-schema.org/draft/2020-12/schema");
					implementation.put("dialects", dialects);
					message.put("implementation", implementation);
					return message.toJSONString();
				}
				break;
			  case "dialect":
				if(started != true){
					throw new RuntimeException("Bowtie hasn't started!");
				}
				DialectRequest dialectRequest = objectMapper.treeToValue(node, DialectRequest.class);
				return "{ \"ok\" : false }";
			  case "run":
				if(started != true){
					throw new RuntimeException("Bowtie hasn't started!");
				}
				RunRequest runRequest = objectMapper.treeToValue(node, RunRequest.class);
				
				try{
                    String caseDescription = runRequest.testCase().description();
					if(UNSUPPORTED_CASES.containsKey(caseDescription)){
						return skipMsg(UNSUPPORTED_CASES.get(caseDescription), runRequest.seq().asLong());
					} 
					
					JSONArray resultArray = new JSONArray();
					
					for (Test test : runRequest.testCase().tests()) {
                        if (UNSUPPORTED_CASES_SPECIFIC.containsKey(caseDescription)) {
                            SpecificSkip skip = UNSUPPORTED_CASES_SPECIFIC.get(caseDescription);
                            if (skip.description().equals(test.description())) {
                                resultArray.add(skipMsg(UNSUPPORTED_CASES_SPECIFIC.get(caseDescription).message()));
                                continue;
                            }
                        }
						String instance = test.instance().toString();
						MainClass$ m = MainClass$.MODULE$;
						boolean results = m.validateInstance(runRequest.testCase().schema().toString(), instance);
						
						JSONObject result = new JSONObject();
						result.put("valid", (boolean) results);
						resultArray.add(result);
					}
					
					JSONObject out = new JSONObject();
					out.put("seq", runRequest.seq().asLong());
					out.put("results", resultArray);
					return out.toJSONString();
				}
				catch(Exception e){
					String msg = getDetailedMessage(e, runRequest.testCase().schema().toString());
					error = errorMsg(msg, runRequest.seq().asLong());
					return error;
				}
			  case "stop":
				if(started != true){
					throw new RuntimeException("Bowtie hasn't started!");
				}
				System.exit(0);
			}
		}catch(Exception e){
			error = errorMsg(e.getMessage(), -1);
			return error;
		}
		error = errorMsg("Send correct command", -1);
		return error;
	}
	
	public static String errorMsg(String message, long seq){
		JSONObject traceBack = new JSONObject(); 
		traceBack.put("traceBack", message);
		JSONObject error = new JSONObject(); 
		error.put("errored", true);
		error.put("seq", seq);
		error.put("context",traceBack);
		return error.toJSONString();
	}
	
	public static String skipMsg(String message, long seq){
		JSONObject error = new JSONObject();
		error.put("skipped", true);
		error.put("seq", seq);
		error.put("message",message);
		return error.toJSONString();
	}
	
    public static JSONObject skipMsg(String message){
		JSONObject error = new JSONObject();
		error.put("skipped", true);
		error.put("message",message);
		return error;
	}
	
    
	public static String getDetailedMessage(Exception e, String schema){
		StringWriter sw = new StringWriter();
		e.printStackTrace(new PrintWriter(sw));
		return sw.toString() + " " + schema;
	}
}

record StartRequest(int version) {}

record DialectRequest(String dialect) {}

record RunRequest(JsonNode seq, @JsonProperty("case") TestCase testCase) {}

record TestCase(String description, String comment, JsonNode schema, JsonNode registry, List<Test> tests) {}

record Test(String description, String comment, JsonNode instance, boolean valid) {}

record SpecificSkip(String description, String message) {}
