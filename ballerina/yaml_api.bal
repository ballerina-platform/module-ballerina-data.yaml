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
public type Options record {
    YAMLSchema schema = CORE_SCHEMA;
    boolean allowAnchorRedefinition = true;
    boolean allowMapEntryRedefinition = false;
};

# Represents the error type of the ballerina/data.yaml module. This error type represents any error that can occur
# during the execution of data.yaml APIs.
public type Error distinct error;
