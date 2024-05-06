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
