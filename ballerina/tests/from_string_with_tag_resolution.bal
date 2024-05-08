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
