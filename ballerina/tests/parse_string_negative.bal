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
    ]
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
    ["tag_handle_negative_5.yaml", "'custom tags not supported' at line: '1' column: '28'"]
];
