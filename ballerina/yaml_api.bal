// Copyright (c) 2024, WSO2 LLC. (https://www.wso2.com).
//
// WSO2 LLC. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.
import ballerina/jballerina.java;

# Converts YAML string to subtype of anydata.
#
# + s - Source string value
# + options - Options to be used for filtering in the projection
# + t - Target type
# + return - On success, returns the given target type value, else returns an `yaml:Error`
public isolated function parseString(string s,
        Options options = {}, typedesc<anydata> t = <>)
        returns t|Error = @java:Method {'class: "io.ballerina.stdlib.data.yaml.Native"} external;

# Converts YAML byte[] to subtype of anydata.
#
# + s - Source byte[] value
# + options - Options to be used for filtering in the projection
# + t - Target type
# + return - On success, returns the given target type value, else returns an `yaml:Error`
public isolated function parseBytes(byte[] s,
        Options options = {}, typedesc<anydata> t = <>)
        returns t|Error = @java:Method {'class: "io.ballerina.stdlib.data.yaml.Native"} external;

# Converts YAML byte-block-stream to subtype of anydata.
#
# + s - Source byte-block-stream value
# + options - Options to be used for filtering in the projection
# + t - Target type
# + return - On success, returns the given target type value, else returns an `yaml:Error`
public isolated function parseStream(stream<byte[], error?> s,
        Options options = {}, typedesc<anydata> t = <>)
        returns t|Error = @java:Method {'class: "io.ballerina.stdlib.data.yaml.Native"} external;

# Converts anydata YAML value to a string.
#
# + yamlValue - Input yaml value
# + config - Options used to get desired toString representation
# + return - On success, returns to string value, else returns an `yaml:Error`
public isolated function toYamlString(anydata yamlValue, WriteConfig config) returns string|Error {
    string[] lines = check toYamlStringArray(yamlValue, config);
    return "\n".'join(...lines);
}

isolated function toYamlStringArray(anydata yamlValue, WriteConfig config)
    returns string[]|Error = @java:Method {'class: "io.ballerina.stdlib.data.yaml.Native"} external;

# Represents the YAML schema available for the parser.
#
# + FAILSAFE_SCHEMA - Generic schema that works for any YAML document
# + JSON_SCHEMA - Schema supports all the basic JSON types
# + CORE_SCHEMA - An extension of JSON schema that allows more human-readable presentation
public enum YAMLSchema {
    FAILSAFE_SCHEMA,
    JSON_SCHEMA,
    CORE_SCHEMA
}

# Represent the options that can be used for filtering in the projection.
#
# + schema - field description  
# + allowAnchorRedefinition - field description  
# + allowMapEntryRedefinition - field description
# + allowDataProjection - Enable or disable projection
public type Options record {
    YAMLSchema schema = CORE_SCHEMA;
    boolean allowAnchorRedefinition = true;
    boolean allowMapEntryRedefinition = false;
    record {
        # If `true`, nil values will be considered as optional fields in the projection.
        boolean nilAsOptionalField = false;
        # If `true`, absent fields will be considered as nilable types in the projection.
        boolean absentAsNilableType = false;
    }|false allowDataProjection = {};
};

# Configurations for writing a YAML document.
#
# + indentationPolicy - Number of whitespace for an indentation
# + blockLevel - The maximum depth level for a block collection
# + canonical - If set, the tags are written along with the nodes
# + useSingleQuotes - If set, single quotes are used to surround scalars
# + forceQuotes - If set, all the scalars are surrounded by quotes
# + schema - YAML schema used for writing
# + isStream - If set, the parser will write a stream of YAML documents
public type WriteConfig record {|
    int indentationPolicy = 2;
    int blockLevel = 1;
    boolean canonical = false;
    boolean useSingleQuotes = false;
    boolean forceQuotes = false;
    YAMLSchema schema = CORE_SCHEMA;
    boolean isStream = false;
    boolean flowStyle = false;
|};

# Represents the error type of the ballerina/data.yaml module. This error type represents any error that can occur
# during the execution of data.yaml APIs.
public type Error distinct error;
