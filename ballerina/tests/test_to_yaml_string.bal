import ballerina/io;
import ballerina/test;

const TO_YAML_STRING_DATA = FILE_PATH + "to-yaml-string/";

@test:Config {
    dataProvider: dataToConvertAnydataValuesToYamlString
}
isolated function testToYamlString1(anydata inputValue, string expectedFile, WriteConfig conf) returns error? {
    string expectedResultPath = TO_YAML_STRING_DATA + expectedFile;
    string value = check toYamlString(inputValue, conf);
    string expectedValue = check io:fileReadString(expectedResultPath);
    test:assertEquals(value, expectedValue);
}

function dataToConvertAnydataValuesToYamlString() returns [anydata, string, WriteConfig][] => [
    [j1, "test_1.yaml", {}],
    [j1, "test_2.yaml", {flowStyle: true}],
    [j1, "test_3.yaml", {forceQuotes: true, useSingleQuotes: false}],
    [j1, "test_4.yaml", {forceQuotes: true, useSingleQuotes: true}],
    [j1, "test_5.yaml", {canonical: true}],
    [j1, "test_6.yaml", {canonical: true, forceQuotes: true}],
    [j2, "test_7.yaml", {flowStyle: true}],
    [j2, "test_8.yaml", {}],
    [j2, "test_9.yaml", {isStream: true}]
];

final json & readonly j1 = {
    "library": {
        "name": "Central Library",
        "location": {
            "address": "123 Library St",
            "city": "Booktown",
            "state": "Knowledge"
        },
        "books": [
            {
                "title": "The Great Gatsby",
                "author": "F. Scott Fitzgerald",
                "genres": ["Classic", "Fiction"],
                "copiesAvailable": 3
            },
            {
                "title": "1984",
                "author": "George Orwell",
                "genres": ["Dystopian", "Science Fiction"],
                "copiesAvailable": 5
            }
        ],
        "staff": [
            {
                "name": "Jane Doe",
                "position": "Librarian",
                "contact": {
                    "email": "jane.doe@library.com",
                    "phone": "555-1234"
                }
            },
            {
                "name": "John Smith",
                "position": "Assistant Librarian",
                "contact": {
                    "email": "john.smith@library.com",
                    "phone": "555-5678"
                }
            }
        ]
    }
};

final json & readonly j2 = [
    {
        "title": "The Great Gatsby",
        "author": "F. Scott Fitzgerald",
        "genres": ["Classic", "Fiction"],
        "copiesAvailable": 3
    },
    {
        "title": "1984",
        "author": "George Orwell",
        "genres": ["Dystopian", "Science Fiction"],
        "copiesAvailable": 5
    },
    {
        "title": "Dune",
        "author": "Frank Herbert",
        "genres": ["Science Fiction", "Adventure"],
        "yearPublished": 1965,
        "isAvailableInEbook": true
    },
    {
        "title": "And Then There Were None",
        "author": "Agatha Christie",
        "genres": ["Mystery", "Thriller"],
        "firstPublished": 1939,
        "isPartOfSeries": true
    }
];
