A Dockerfile for running mjs Validator.<br><br>
## Pre-requisites

Bowtie application has to be already installed on system.

## Recommendation

This has been tested with bowtie version 2023.8.11 .

## How to run ?

The project launch is in two parts: build and launch.<br>

### Build

```
$ docker build -f Dockerfile -t [image_name] . 
```

### Test JSchema Validator with bowtie

```
$ bowtie run -i [image_name] -V <<EOF
{"description": "test case 1", "schema": {}, "tests": [{"description": "a test", "instance": {}}] }
{"description": "test case 2", "schema": {"const": 37}, "tests": [{"description": "not 37", "instance": {}}, {"description": "is 37", "instance": 37}] }
EOF
```

### Validate Instance data with Schema data
```
$ bowtie validate <optional parameters> -i [image_name] <schema_file_path> <instance_file_path>
```

Optional parameter<br>
For dialect -D <dialect_name>

### Run Json official schema
```
$ bowtie suite -i [image_name] [path_to_test_suite] | bowtie report
```

[path_to_test_suite] can be used as below<br>
https://github.com/json-schema-org/JSON-Schema-Test-Suite/blob/main/tests/draft2020-12/ to run a version directly from a branch which exists in GitHub<br>
https://github.com/json-schema-org/JSON-Schema-Test-Suite/blob/main/tests/draft2020-12/type.json to run a single file directly from a branch which exists in GitHub

### To generate report from bowtie
This command is executed with run, validate, suite
```
[other_command] | bowtie report
```