# Ballerina YAML Data Library

[![Build](https://github.com/ballerina-platform/module-ballerina-data.yaml/actions/workflows/build-timestamped-master.yml/badge.svg)](https://github.com/ballerina-platform/module-ballerina-data.yaml/actions/workflows/build-timestamped-master.yml)
[![codecov](https://codecov.io/gh/ballerina-platform/module-ballerina-data.yaml/branch/main/graph/badge.svg)](https://codecov.io/gh/ballerina-platform/module-ballerina-data.yaml)
[![Trivy](https://github.com/ballerina-platform/module-ballerina-data.yaml/actions/workflows/trivy-scan.yml/badge.svg)](https://github.com/ballerina-platform/module-ballerina-data.yaml/actions/workflows/trivy-scan.yml)
[![GraalVM Check](https://github.com/ballerina-platform/module-ballerina-data.yaml/actions/workflows/build-with-bal-test-graalvm.yml/badge.svg)](https://github.com/ballerina-platform/module-ballerina-data.yaml/actions/workflows/build-with-bal-test-graalvm.yml)
[![GitHub Last Commit](https://img.shields.io/github/last-commit/ballerina-platform/module-ballerina-data.yaml.svg)](https://github.com/ballerina-platform/module-ballerina-data.yaml/commits/master)
[![Github issues](https://img.shields.io/github/issues/ballerina-platform/ballerina-standard-library/module/data.yaml.svg?label=Open%20Issues)](https://github.com/ballerina-platform/ballerina-standard-library/labels/module%2Fdata.yaml)

The Ballerina data.yaml library provides robust and flexible functionalities for working with YAML data within
Ballerina applications.
This library enables developers to seamlessly integrate YAML processing capabilities,
ensuring smooth data interchange and configuration management.

## Key Features

- **Versatile Input Handling**: Convert YAML input provided as strings, byte arrays, or streams of byte arrays into
  Ballerina's anydata sub-types, facilitating flexible data processing.
- **Data Projection**: Efficiently project data from YAML documents and YAML streams,
  allowing for precise data extraction and manipulation.
- **Ordered Data Representation**: Employ tuples to preserve the order of elements when dealing with
  YAML document streams of unknown order, ensuring the integrity of data sequences.
- **Serialization**: Serialize Ballerina values into YAML-formatted strings, enabling easy generation of YAML content
  from Ballerina applications for configuration files, data storage, or data exchange purposes.

## Usage

### Converting external YAML document to a record value

For transforming YAML content from an external source into a record value,
the `parseString`, `parseBytes`, `parseStream` functions can be used.
This external source can be in the form of a string or a byte array/byte-block-stream that houses the YAML data.
This is commonly extracted from files or network sockets. The example below demonstrates the conversion of an
YAML value from an external source into a record value.

```ballerina
import ballerina/data.yaml;
import ballerina/io;

type Book record {|
    string name;
    string author;
    int year;
|};

public function main() returns error? {
    string yamlContent = check io:fileReadString("path/to/file.yaml");
    Book book = check yaml:parseString(yamlContent);
    io:println(book);
}
```

Make sure to handle possible errors that may arise during the file reading or YAML to anydata conversion process.
The `check` keyword is utilized to handle these errors,
but more sophisticated error handling can be implemented as per your requirements.

## Serialize anydata value to YAML

The serialization of anydata value into YAML-formatted strings can be done in the below way.

```ballerina
import ballerina/data.yaml;
import ballerina/io;

public function main() returns error? {
    json content = {
        name: "Clean Code",
        author: "Robert C. Martin",
        year: 2008
    };
    string yamlString = check yaml:toYamlString(content);
    io:println(yamlString);
}
```


## Issues and projects

Issues and Project are disabled for this repository as this is part of the Ballerina Standard Library. 
To report bugs, request new features, start new discussions, view project boards, etc. 
please visit Ballerina Library 
[parent repository](https://github.com/ballerina-platform/ballerina-library).

This repository only contains the source code for the package.

## Build from the source

### Set up the prerequisites

1. Download and install Java SE Development Kit (JDK) version 21 (from one of the following locations).
    * [Oracle](https://www.oracle.com/java/technologies/downloads/)

    * [OpenJDK](https://adoptium.net/)

      > **Note:** Set the JAVA_HOME environment variable to the path name of the directory into which you installed JDK.

### Build the source

Execute the commands below to build from source.

1. To build the library:

        ./gradlew clean build

2. To run the tests:

        ./gradlew clean test

3. To build the package without tests:

        ./gradlew clean build -x test

4. To run a group of tests:

        ./gradlew clean test -Pgroups=<test_group_names>

5. To debug package implementation:

        ./gradlew clean build -Pdebug=<port>

6. To debug the package with Ballerina language:

        ./gradlew clean build -PbalJavaDebug=<port>

7. Publish ZIP artifact to the local `.m2` repository:

        ./gradlew clean build publishToMavenLocal

8. Publish the generated artifacts to the local Ballerina central repository:

        ./gradlew clean build -PpublishToLocalCentral=true

9. Publish the generated artifacts to the Ballerina central repository:

        ./gradlew clean build -PpublishToCentral=true

## Contribute to Ballerina

As an open source project, Ballerina welcomes contributions from the community.

For more information, go to the [contribution guidelines](https://github.com/ballerina-platform/ballerina-lang/blob/master/CONTRIBUTING.md).

## Code of conduct

All contributors are encouraged to read the [Ballerina Code of Conduct](https://ballerina.io/code-of-conduct).

## Useful links

* Chat live with us via our [Discord server](https://discord.gg/ballerinalang).
* Post all technical questions on Stack Overflow with the [#ballerina](https://stackoverflow.com/questions/tagged/ballerina) tag.
* For more information go to the [`data.yaml` library](https://lib.ballerina.io/ballerina/data.yaml/latest).
* For example demonstrations of the usage, go to [Ballerina By Examples](https://ballerina.io/swan-lake/learn/by-example/).