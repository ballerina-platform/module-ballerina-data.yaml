Ballerina YAML Data Library
===================

The Ballerina YAML Data Library is a comprehensive toolkit designed to facilitate the handling and manipulation of 
YAML data within Ballerina applications.

## Issues and projects

Issues and Project are disabled for this repository as this is part of the Ballerina Standard Library. 
To report bugs, request new features, start new discussions, view project boards, etc. 
please visit Ballerina Library 
[parent repository](https://github.com/ballerina-platform/ballerina-library).

This repository only contains the source code for the package.

## Build from the source

### Set up the prerequisites

1. Download and install Java SE Development Kit (JDK) version 17 (from one of the following locations).
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