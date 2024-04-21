import ballerina/test;

anydata a = 1;

@test:Config {
    dataProvider: basicTypeDataForParseString
}
isolated function testBasicTypes(string sourceData, typedesc<anydata> expectedType, anydata expectedResult)
returns error? {
    anydata actualResult = check parseString(sourceData, {}, expectedType);
    test:assertEquals(actualResult, expectedResult);
}

function basicTypeDataForParseString() returns [string, typedesc<anydata>, anydata][] => [
    ["This is a basic string", string, "This is a basic string"],
    ["\"This is a double quoted string\"", string, "This is a double quoted string"],
    ["'This is a single quoted string'", string, "This is a single quoted string"],
    ["|\nThis is a literal\nblock scalar string", string, "This is a literal block scalar string"],
    [">\nThis is a folded\nblock scalar string", string, "This is a folded block scalar string"]
];
