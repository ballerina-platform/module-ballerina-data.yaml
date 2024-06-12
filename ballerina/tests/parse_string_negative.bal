// Copyright (c) 2024, WSO2 LLC. (https://www.wso2.com).
//
// WSO2 LLC. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/io;
import ballerina/test;

const NEGATIVE_TEST_PATH = FILE_PATH + "negative/";

@test:Config {
    dataProvider: negativeDataProvider
}
isolated function negtiveTests(string path, string expectedErrMsg) returns io:Error? {
    string content = check io:fileReadString(NEGATIVE_TEST_PATH + path);
    anydata|Error result = parseString(content);
    test:assertTrue(result is Error);
    test:assertEquals((<Error>result).message(), expectedErrMsg);
}

function negativeDataProvider() returns [string, string][] => [
    ["negative_test_1.yaml", "'non printable character found' at line: '2' column: '13'"],
    ["negative_test_2.yaml", "'invalid indentation' at line: '5' column: '5'"],
    ["negative_test_3.yaml", "'invalid block header' at line: '1' column: '2'"],
    ["negative_test_4.yaml", "'insufficient indentation for a scalar' at line: '3' column: '4'"],
    ["negative_test_5.yaml", "'insufficient indentation for a scalar' at line: '4' column: '4'"],
    [
        "negative_test_6.yaml",
        "'block mapping cannot have the same indent as a block sequence' at line: '3' column: '10'"
    ],
    ["negative_test_7.yaml", "'there can only be one root event to a document' at line: '2' column: '10'"],
    ["negative_test_8.yaml", "'expected a key for the block mapping' at line: '2' column: '4'"],
    ["negative_test_9.yaml", "'anchor does not exist' at line: '1' column: '15'"],
    ["negative_test_10.yaml", "'unexpected event' at line: '1' column: '10'"],
    ["negative_test_11.yaml", "'unexpected event' at line: '1' column: '8'"],
    ["negative_test_12.yaml", "'unexpected event error' at line: '1' column: '5'"],
    ["negative_test_13.yaml", "'cannot have block sequence under flow collection' at line: '2' column: '3'"]
];

@test:Config {
    dataProvider: tagHandleNegativeDataProvider
}
isolated function tagHandleNegativeTests(string path, string expectedErrMsg) returns io:Error? {
    string fullPath = NEGATIVE_TEST_PATH + "tag_handle_negative/" + path;
    string content = check io:fileReadString(fullPath);
    anydata|Error result = parseString(content);
    test:assertTrue(result is Error);
    test:assertEquals((<Error>result).message(), expectedErrMsg);
}

function tagHandleNegativeDataProvider() returns [string, string][] => [
    ["tag_handle_negative_1.yaml", "'incompatible yaml version for the 1.2 parser' at line: '1' column: '9'"],
    ["tag_handle_negative_2.yaml", "'incompatible yaml version for the 1.2 parser' at line: '1' column: '9'"],
    ["tag_handle_negative_3.yaml", "'YAML document version is already defined' at line: '2' column: '5'"],
    ["tag_handle_negative_4.yaml", "'duplicate tag handle' at line: '2' column: '12'"],
    ["tag_handle_negative_5.yaml", "'custom tags not supported' at line: '1' column: '28'"],
    ["tag_handle_negative_6.yaml", "'invalid digit character' at line: '1' column: '9'"],
    ["tag_handle_negative_7.yaml", "'invalid directive document' at line: '2' column: '1'"],
    ["tag_handle_negative_8.yaml", "'invalid document' at line: '1' column: '8'"],
    ["tag_handle_negative_9.yaml", "'directives are not allowed in a bare document' at line: '3' column: '5'"]
];

@test:Config {
    dataProvider: simpleNegativeTestDataProvider
}
function testSimpleNegativeCases(string yaml, string expectedErrMsg) returns error? {
    anydata|Error result = parseString(yaml);
    test:assertTrue(result is Error);
    test:assertEquals((<Error>result).message(), expectedErrMsg);
}

function simpleNegativeTestDataProvider() returns map<[string, string]> {
    return {
        "invalid-unicode-1": [
            string `"${"\\U0000004G"}"`,
            "'expected a unicode character after escaped char' at line: '1' column: '10'"
        ],
        "invalid-unicode-2": [
            string `"${"\\u004g"}"`,
            "'expected a unicode character after escaped char' at line: '1' column: '6'"
        ],
        "invalid-unicode-3": [
            string `"${"\\x4g"}"`,
            "'expected a unicode character after escaped char' at line: '1' column: '4'"
        ],
        "invalid-unicode-4": [
            string `"${"\\d4g"}"`,
            "'invalid escape character' at line: '1' column: '2'"
        ]
    };
}

@test:Config {
    dataProvider: tagResolutionNegativeDataProvider
}
isolated function testTagResolutionNegative(string yaml, string expectedErrMsg, Options option) {
    anydata|Error result = parseString(yaml, option);
    test:assertTrue(result is Error);
    test:assertEquals((<Error>result).message(), expectedErrMsg);
}

function tagResolutionNegativeDataProvider() returns [string, string, Options][] => [
    ["!!null ()", "'cannot cast () to null' at line: '1' column: '8'", {schema: JSON_SCHEMA}],
    ["!!bool yes", "'cannot cast yes to boolean' at line: '1' column: '9'", {schema: JSON_SCHEMA}],
    ["!!int abc", "'cannot cast abc to int' at line: '1' column: '8'", {schema: JSON_SCHEMA}],
    ["!!float abc", "'cannot cast abc to float' at line: '1' column: '10'", {schema: JSON_SCHEMA}],
    ["!!null ()", "'cannot cast () to null' at line: '1' column: '8'", {}],
    ["!!bool yes", "'cannot cast yes to boolean' at line: '1' column: '9'", {}],
    ["!!int abc", "'cannot cast abc to int' at line: '1' column: '8'", {}],
    ["!!float abc", "'cannot cast abc to float' at line: '1' column: '10'", {}]
];
