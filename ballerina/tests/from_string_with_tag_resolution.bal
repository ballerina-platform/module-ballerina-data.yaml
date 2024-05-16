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

const TAG_RESOLUTION_TEST_PATH = FILE_PATH + "tags/";

@test:Config
isolated function testTagResolutionWithAnydataExpectedType() returns error? {
    string content = check io:fileReadString(TAG_RESOLUTION_TEST_PATH + "tag_resolution_test_1.yaml");
    anydata result = check parseString(content, {schema: JSON_SCHEMA});
    final anydata expectedResult = {
        "server": {
            "host": "192.168.1.100",
            "port": 8080,
            "environment": "development",
            "restart": true,
            "start": 0.12,
            "retry": false
        },
        "logging": {"level": "info", "file": ()},
        "users": [{"name": "John Doe", "roles": ["admin", "editor"]}, {"name": "Jane Smith", "roles": ["user"]}]
    };
    test:assertEquals(result, expectedResult);
}

@test:Config
isolated function testTagResolutionWithAnydataExpectedType2() returns error? {
    string content = check io:fileReadString(TAG_RESOLUTION_TEST_PATH + "tag_resolution_test_2.yaml");
    anydata result = check parseString(content);
    final anydata expectedResult = {
        "server": {
            "host": "192.168.1.100",
            "ports": [8000, 9000, 9001],
            "environment": "development",
            "restart": true,
            "start": 0.12,
            "end": float:Infinity,
            "retry": false
        },
        "logging": {"level": "info", "file": null},
        "users": [{"name": "John Doe", "roles": ["admin", "editor"]}, {"name": "Jane Smith", "roles": ["user"]}]
    };
    test:assertEquals(result, expectedResult);
}

type ServerData record {
    record {
        string host;
        [int, int, int] ports;
        string environment;
        boolean restart;
        decimal 'start;
        string|float end;
        boolean 'retry;
    } server;
    record {
        string level;
        string? file;
    } logging;
    record {
        string name;
        string[] roles;
    }[] users;
};

@test:Config
isolated function testTagResolutionWithRecordExpectedType() returns error? {
    string content = check io:fileReadString(TAG_RESOLUTION_TEST_PATH + "tag_resolution_test_2.yaml");
    ServerData result = check parseString(content);
    final ServerData expectedResult = {
        server: {
            host: "192.168.1.100",
            ports: [8000, 9000, 9001],
            environment: "development",
            restart: true,
            'start: 0.12,
            end: float:Infinity,
            'retry: false
        },
        logging: {level: "info", file: ()},
        users: [{name: "John Doe", roles: ["admin", "editor"]}, {name: "Jane Smith", roles: ["user"]}]
    };
    test:assertEquals(result, expectedResult);
}
