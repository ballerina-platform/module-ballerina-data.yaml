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

const YAML_STREAM_TEST_PATH = FILE_PATH + "streams/";

final Options & readonly enableYamlStreamReorderOptions = {
    allowDataProjection: {
        nilAsOptionalField: false,
        absentAsNilableType: false,
        enableYamlStreamReorder: true
    }
};

@test:Config
isolated function testYamlStringParsing() returns error? {
    stream<io:Block, io:Error?> streamResult = check io:fileReadBlocksAsStream(YAML_STREAM_TEST_PATH + "stream_1.yaml");
    anydata result = check parseStream(streamResult);
    test:assertTrue(result is anydata[]);
    test:assertEquals((<anydata[]>result).length(), 4);
}

@test:Config
isolated function testYamlStringParsing2() returns error? {
    string filePath = YAML_STREAM_TEST_PATH + "stream_1.yaml";
    stream<io:Block, io:Error?> streamResult = check io:fileReadBlocksAsStream(filePath);
    ExpectedType result = check parseStream(streamResult);

    final ConfigType configMapValue = {
        "apiVersion": "v1",
        "kind": "ConfigMap",
        "metadata": {"name": "ballerina-mongodb-configmap"},
        "data": {
            "Config.toml": "[ballerina.ballerina_mongodb_kubernetes]" +
                            "\nmongodbPort = 27017\nmongodbHost = \"mongodb-service\"" +
                            "\ndbName = \"students\"\n\n[ballerina.log]\nlevel = \"DEBUG\"\n"
        }
    };

    test:assertEquals(result.length(), 2);
    test:assertEquals(result[1].apiVersion, configMapValue.apiVersion);
    test:assertEquals(result[1].kind, configMapValue.kind);
    test:assertEquals(result[1].metadata, configMapValue.metadata);
    test:assertEquals((<ConfigType>result[1]).data, configMapValue.data);
}

@test:Config
isolated function testYamlStringParsing3() returns error? {
    string filePath = YAML_STREAM_TEST_PATH + "stream_1.yaml";
    stream<io:Block, io:Error?> streamResult = check io:fileReadBlocksAsStream(filePath);
    UnionType[] result = check parseStream(streamResult);

    final ConfigType configMapValue = {
        "apiVersion": "v1",
        "kind": "ConfigMap",
        "metadata": {"name": "ballerina-mongodb-configmap"},
        "data": {
            "Config.toml": "[ballerina.ballerina_mongodb_kubernetes]" +
                            "\nmongodbPort = 27017\nmongodbHost = \"mongodb-service\"" +
                            "\ndbName = \"students\"\n\n[ballerina.log]\nlevel = \"DEBUG\"\n"
        }
    };

    test:assertEquals(result.length(), 4);
    test:assertEquals(result[1].apiVersion, configMapValue.apiVersion);
    test:assertEquals(result[1].kind, configMapValue.kind);
    test:assertEquals(result[1].metadata, configMapValue.metadata);
    test:assertEquals((<ConfigType>result[1]).data, configMapValue.data);
}

@test:Config
isolated function testYamlStreamPastingWithTupleExpected() returns error? {
    string filePath = YAML_STREAM_TEST_PATH + "stream_1.yaml";
    stream<io:Block, io:Error?> streamResult = check io:fileReadBlocksAsStream(filePath);
    [ServiceType, ConfigType, ConfigType, DeploymentType] result = check parseStream(streamResult);

    final ConfigType configMapValue = {
        "apiVersion": "v1",
        "kind": "ConfigMap",
        "metadata": {"name": "ballerina-mongodb-configmap"},
        "data": {
            "Config.toml": "[ballerina.ballerina_mongodb_kubernetes]" +
                            "\nmongodbPort = 27017\nmongodbHost = \"mongodb-service\"" +
                            "\ndbName = \"students\"\n\n[ballerina.log]\nlevel = \"DEBUG\"\n"
        }
    };

    test:assertEquals(result.length(), 4);
    test:assertEquals(result[1].apiVersion, configMapValue.apiVersion);
    test:assertEquals(result[1].kind, configMapValue.kind);
    test:assertEquals(result[1].metadata, configMapValue.metadata);
    test:assertEquals(result[1].data, configMapValue.data);
}

@test:Config
isolated function testYamlStreamPastingWithTupleExpected2() returns error? {
    string filePath = YAML_STREAM_TEST_PATH + "stream_1.yaml";
    stream<io:Block, io:Error?> streamResult = check io:fileReadBlocksAsStream(filePath);
    [DeploymentType, ServiceType, ConfigType] result = check parseStream(streamResult, enableYamlStreamReorderOptions);

    final ConfigType configMapValue = {
        "apiVersion": "v1",
        "kind": "ConfigMap",
        "metadata": {"name": "ballerina-mongodb-configmap"},
        "data": {
            "Config.toml": "[ballerina.ballerina_mongodb_kubernetes]" +
                            "\nmongodbPort = 27017\nmongodbHost = \"mongodb-service\"" +
                            "\ndbName = \"students\"\n\n[ballerina.log]\nlevel = \"DEBUG\"\n"
        }
    };

    test:assertEquals(result.length(), 3);
    test:assertEquals(result[2].apiVersion, configMapValue.apiVersion);
    test:assertEquals(result[2].kind, configMapValue.kind);
    test:assertEquals(result[2].metadata, configMapValue.metadata);
    test:assertEquals(result[2].data, configMapValue.data);
}

@test:Config
isolated function testYamlStreamPastingWithTupleExpected3() returns error? {
    string filePath = YAML_STREAM_TEST_PATH + "stream_1.yaml";
    stream<io:Block, io:Error?> streamResult = check io:fileReadBlocksAsStream(filePath);
    [DeploymentType, ServiceType, ConfigType, ConfigType...] result = check parseStream(streamResult,
        enableYamlStreamReorderOptions);

    final ConfigType configMapValue = {
        "apiVersion": "v1",
        "kind": "ConfigMap",
        "metadata": {"name": "ballerina-mongodb-configmap"},
        "data": {
            "Config.toml": "[ballerina.ballerina_mongodb_kubernetes]" +
                            "\nmongodbPort = 27017\nmongodbHost = \"mongodb-service\"" +
                            "\ndbName = \"students\"\n\n[ballerina.log]\nlevel = \"DEBUG\"\n"
        }
    };

    test:assertEquals(result.length(), 4);
    test:assertEquals(result[2].apiVersion, configMapValue.apiVersion);
    test:assertEquals(result[2].kind, configMapValue.kind);
    test:assertEquals(result[2].metadata, configMapValue.metadata);
    test:assertEquals(result[2].data, configMapValue.data);
}

@test:Config
isolated function testYamlStreamPastingWithTupleExpected4() returns error? {
    string filePath = YAML_STREAM_TEST_PATH + "stream_1.yaml";
    stream<io:Block, io:Error?> streamResult = check io:fileReadBlocksAsStream(filePath);
    [ConfigType, ServiceType, ConfigType...] result = check parseStream(streamResult, enableYamlStreamReorderOptions);

    final ConfigType configMapValue = {
        "apiVersion": "v1",
        "kind": "ConfigMap",
        "metadata": {"name": "ballerina-mongodb-configmap"},
        "data": {
            "Config.toml": "[ballerina.ballerina_mongodb_kubernetes]" +
                            "\nmongodbPort = 27017\nmongodbHost = \"mongodb-service\"" +
                            "\ndbName = \"students\"\n\n[ballerina.log]\nlevel = \"DEBUG\"\n"
        }
    };

    test:assertEquals(result.length(), 3);
    test:assertEquals(result[0].apiVersion, configMapValue.apiVersion);
    test:assertEquals(result[0].kind, configMapValue.kind);
    test:assertEquals(result[0].metadata, configMapValue.metadata);
    test:assertEquals(result[0].data, configMapValue.data);
}

@test:Config
isolated function testYamlStreamPastingWithTupleExpected5() returns error? {
    string filePath = YAML_STREAM_TEST_PATH + "stream_2.yaml";
    stream<io:Block, io:Error?> streamResult = check io:fileReadBlocksAsStream(filePath);
    [ServiceType, int...] result = check parseStream(streamResult, enableYamlStreamReorderOptions);

    test:assertEquals(result.length(), 4);
    test:assertEquals(result[1], 0);
    test:assertEquals(result[2], 1);
    test:assertEquals(result[3], 2);
}

@test:Config
isolated function testYamlStreamPastingWithTupleExpected6() returns error? {
    string filePath = YAML_STREAM_TEST_PATH + "stream_3.yaml";
    stream<io:Block, io:Error?> streamResult = check io:fileReadBlocksAsStream(filePath);
    [T1, T1|T2, T2, T3, T3|T2, T2|T3, T1...] result = check parseStream(streamResult, enableYamlStreamReorderOptions);
    [T1, T1|T2, T2, T3, T3|T2, T2|T3, T1...] expectedResult = [
        {"p1": "T1_0"},
        {"p1": "T1_1"},
        {"p2": "123", "p3": "string", "p1": "T2_0"},
        {"p2": 123, "p3": true, "p1": "T3_0"},
        {"p2": 123, "p3": true, "p1": "T3_1"},
        {"p2": 123, "p3": false, "p1": "T3_2"},
        {"p1": "T1_6"},
        {"p1": "T1_7"},
        {"p1": "T1_8"},
        {"p1": "T1_9"}
    ];
    test:assertEquals(result, expectedResult);
}

@test:Config
isolated function testParsingStreamOfBasicValues() returns error? {
    string filePath = YAML_STREAM_TEST_PATH + "stream_4.yaml";
    stream<io:Block, io:Error?> streamResult = check io:fileReadBlocksAsStream(filePath);
    [int, float, string, (), boolean, float] result = check parseStream(streamResult, enableYamlStreamReorderOptions);
    [int, float, string, (), boolean, float] expectedResult = [
        234, 12.34, "string", (), true, 12.11
    ];
    test:assertEquals(result, expectedResult);
}

type T1 record {|
    string p1;
|};

type T2 record {|
    *T1;
    string p2;
    string p3;
|};

type T3 record {|
    *T1;
    int p2;
    boolean p3;
|};

type ExpectedType UnionType[2];

type UnionType ServiceType|ConfigType|DeploymentType;

type ServiceType record {|
    string apiVersion;
    string kind;
    record {
        record {
            string app;
        } labels;
        string name;
    } metadata;
    record {
        record {
            string name;
            int port;
            string protocol;
            int targetPort;
            int nodePort;
        }[] ports;
        record {
            string app;
        } selector;
        string 'type;
    } spec;
|};

type ConfigType record {|
    string apiVersion;
    string kind;
    record {|
        string name;
    |} metadata;
    map<string> data;
|};

type DeploymentType record {
    string apiVersion;
    string kind;
    record {
        record {
            string app;
        } labels;
        string name;
    } metadata;
    record {
        int replicas;
        record {
            record {
                string app;
            } matchLabels;
        } selector;
        record {
        } template;
    } spec;
};
