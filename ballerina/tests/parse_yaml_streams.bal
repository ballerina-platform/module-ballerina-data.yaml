import ballerina/io;
import ballerina/test;

const YAML_STREAM_TEST_PATH = FILE_PATH + "streams/";

@test:Config
isolated function testYamlStringParsing() returns error? {
    stream<io:Block, io:Error?> streamResult = check io:fileReadBlocksAsStream(YAML_STREAM_TEST_PATH + "stream_1.yaml");
    anydata result = check parseStream(streamResult, {isStream: true});
    test:assertTrue(result is anydata[]);
    test:assertEquals((<anydata[]>result).length(), 4);
}

@test:Config
isolated function testYamlStringParsing2() returns error? {
    string filePaht = YAML_STREAM_TEST_PATH + "stream_1.yaml";
    stream<io:Block, io:Error?> streamResult = check io:fileReadBlocksAsStream(filePaht);
    [ServiceType, ConfigType, ConfigType, DeploymentType] result = check parseStream(streamResult, {isStream: true});

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
