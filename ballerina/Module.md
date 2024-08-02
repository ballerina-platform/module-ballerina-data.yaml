# Ballerina YAML Data Library

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
    string yamlContent = check io:fileReadString("path/to/<file-name>.yaml");
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
