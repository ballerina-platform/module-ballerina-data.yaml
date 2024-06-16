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

const PARSER_TESTS_PATH = FILE_PATH + "parser/";

@test:Config {
    dataProvider: tagHandleData
}
isolated function testTagHandles(string inputPath, TestCase expectedValue) returns error? {
    string filePath = PARSER_TESTS_PATH + inputPath;
    string content = check io:fileReadString(filePath);
    TestCase actual = check parseString(content);
    test:assertEquals(actual, expectedValue);
}

function tagHandleData() returns [string, TestCase][] => [
    ["tag_handle_1.yaml", {case: "uri_scanner"}],
    ["tag_handle_2.yaml", {case: "yaml_version"}],
    ["tag_handle_3.yaml", {case: "verbitam"}],
    ["tag_handle_4.yaml", {case: "yaml_version"}],
    ["tag_handle_5.yaml", {case: "yaml_version"}],
    ["tag_handle_6.yaml", {case: "reserved directive"}],
    ["tag_handle_7.yaml", {case: "secondary tag handle"}],
    ["tag_handle_8.yaml", {case: "value"}],
    ["tag_handle_9.yaml", {case: "uri_scanner"}],
    ["tag_handle_10.yaml", {case: "value"}],
    ["tag_handle_11.yaml", {case: "value"}]
];

type TestCase record {|
    string case;
|};
