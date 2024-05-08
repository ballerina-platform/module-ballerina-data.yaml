import ballerina/io;
import ballerina/test;

const FILE_PATH = "tests/resources/";

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
    ["|\nThis is a literal\nblock scalar string", string, "This is a literal\nblock scalar string\n"],
    [">\nThis is a folded\nblock scalar string", string, "This is a folded block scalar string\n"],
    ["|+\nThis is a literal\nblock scalar string", string, "This is a literal\nblock scalar string\n"],
    ["|-\nThis is a literal\nblock scalar string", string, "This is a literal\nblock scalar string"],
    [">+\nThis is a folded\nblock scalar string", string, "This is a folded block scalar string\n"],
    [">-\nThis is a folded\nblock scalar string", string, "This is a folded block scalar string"],
    ["123", int, 123],
    ["12.23", float, 12.23],
    ["12.23", decimal, 12.23d]
];

@test:Config {
    dataProvider: simpleYampMappingToRecordData
}
isolated function testSimpleYamlMappingStringToRecord(string inputPath) returns error? {
    string content = check io:fileReadString(FILE_PATH + inputPath);
    SimpleYaml result = check parseString(content);
    test:assertEquals(result.name, "Jhon");
    test:assertEquals(result.age, 30);
    test:assertEquals(result.description, "This is a multiline\nstring in YAML.\nIt preserves line breaks.\n");
}

@test:Config {
    dataProvider: simpleYampMappingToRecordData
}
isolated function testSimpleYamlMappingStringToRecordWithProjection(string inputPath) returns error? {
    string content = check io:fileReadString(FILE_PATH + inputPath);
    record {|int age;|} result = check parseString(content);
    test:assertEquals(result.length(), 1);
    test:assertEquals(result.age, 30);
}

function simpleYampMappingToRecordData() returns string[][] => [
    ["simple_yaml_1a.yaml"],
    ["simple_yaml_1b.yaml"]
];

@test:Config {
    dataProvider: simpleYamlSequenceToArrayData
}
isolated function testSimpleYamlSequenceStringToArray(string inputPath) returns error? {
    string content = check io:fileReadString(FILE_PATH + inputPath);

    string[] result1 = check parseString(content);
    test:assertEquals(result1.length(), 4);
    test:assertEquals(result1, ["YAML", "TOML", "JSON", "XML"]);

    string[2] result2 = check parseString(content);
    test:assertEquals(result2.length(), 2);
    test:assertEquals(result2, ["YAML", "TOML"]);
}

function simpleYamlSequenceToArrayData() returns string[][] => [
    ["simple_yaml_2a.yaml"],
    ["simple_yaml_2b.yaml"]
];

@test:Config
isolated function testNestedYamlStringToRecord() returns error? {
    string content = check io:fileReadString(FILE_PATH + "nested_1.yaml");

    record {|string name; Employee[] employees;|} result = check parseString(content);
    Employee[] employees = result.employees;
    test:assertEquals(result.name, "YAML Test");
    test:assertEquals(employees.length(), 2);
    test:assertEquals(employees[0].name, "Alice");
    test:assertEquals(employees[0].age, 30);
    test:assertEquals(employees[1].department, "Engineering");
    test:assertEquals(employees[1].projects.length(), 2);
    test:assertEquals(employees[0].projects[0].status, "In Progress");
    test:assertEquals(employees[1].projects[1].name, "Project D");

    OpenRecord result2 = check parseString(content);
    test:assertEquals(result2.get("name"), "YAML Test");
    Employee[] employees2 = check result2.get("employees").cloneWithType();
    test:assertEquals(employees2.length(), 2);
    test:assertEquals(employees2[0].name, "Alice");
    test:assertEquals(employees2[0].age, 30);
    test:assertEquals(employees2[1].department, "Engineering");
    test:assertEquals(employees2[1].projects.length(), 2);
    test:assertEquals(employees2[0].projects[0].status, "In Progress");
    test:assertEquals(employees2[1].projects[1].name, "Project D");
}

@test:Config
isolated function testNestedYamlStringToRecordWithProjection() returns error? {
    string content = check io:fileReadString(FILE_PATH + "nested_1.yaml");

    record {|record {|string name; Project[1] projects;|}[] employees;|} result = check parseString(content);
    test:assertEquals(result.employees.length(), 2);
    test:assertEquals(result.employees[0].name, "Alice");
    test:assertEquals(result.employees[0].projects.length(), 1);
    test:assertEquals(result.employees[0].projects[0].name, "Project A");
    test:assertEquals(result.employees[0].projects[0].status, "In Progress");
    test:assertEquals(result.employees[1].projects[0].name, "Project C");
    test:assertEquals(result.employees[1].projects[0].status, "Pending");
}

@test:Config
isolated function testYamlStringToRecordWithOptionalField() returns error? {
    string content = "{name: Alice}";

    record {|string name; string optinalField?;|} result = check parseString(content);
    test:assertEquals(result.length(), 1);
    test:assertEquals(result.name, "Alice");
    test:assertEquals(result.optinalField, ());
}

@test:Config
isolated function testYamlStringToRecordWithOptionalFieldWithProjection() returns error? {
    string content = check io:fileReadString(FILE_PATH + "nested_2.yaml");

    record {|
        string name;
        string optinalField?;
        record {|string name; string optinalField?;|}[1] projects;
    |} result = check parseString(content);

    test:assertEquals(result.length(), 2);
    test:assertEquals(result.name, "Alice");
    test:assertEquals(result.optinalField, ());
    test:assertEquals(result.projects.length(), 1);
    test:assertEquals(result.projects[0].length(), 1);
    test:assertEquals(result.projects[0].name, "Project A");
    test:assertEquals(result.projects[0].optinalField, ());
}

@test:Config
isolated function testYamlStringToNestedArray() returns error? {
    string content = check io:fileReadString(FILE_PATH + "nested_3.yaml");

    BookCover[] books = check parseString(content);
    test:assertEquals(books.length(), 3);
    test:assertEquals(books[0].title, "Book 1");
    test:assertEquals(books[1].pages, 300);
    test:assertEquals(books[1].authors.length(), 2);
    test:assertEquals(books[1].authors, ["Author X", "Author Y"]);
    test:assertEquals(books[2].price, 12.50);
}

@test:Config
isolated function testYamlStringToNestedArrayWithProjection() returns error? {
    string content = check io:fileReadString(FILE_PATH + "nested_3.yaml");

    record {|string title; string[1] authors;|}[2] books = check parseString(content);
    test:assertEquals(books.length(), 2);
    test:assertEquals(books[0].title, "Book 1");
    test:assertEquals(books[1].authors.length(), 1);
    test:assertEquals(books[1].authors, ["Author X"]);
}

@test:Config
isolated function testYamlStringToRecordWithMapTypeString() returns error? {
    string content = check io:fileReadString(FILE_PATH + "nested_4.yaml");

    record {|RecordWithMapType[] records;|} records = check parseString(content);
    test:assertEquals(records.records.length(), 2);
    test:assertEquals(records.records[0].name, "Record 1");
    test:assertEquals(records.records[0].data.length(), 3);
    test:assertEquals(records.records[1].data, {keyA: "valueA", keyB: "valueB"});
}

@test:Config
isolated function testNestedYamlStringToRecord2() returns error? {
    string content = check io:fileReadString(FILE_PATH + "nested_5.yaml");

    Book book = check parseString(content);
    test:assertEquals(book.title, "To Kill a Mockingbird");
    test:assertEquals(book.author.name, "Harper Lee");
    test:assertEquals(book.author.birthdate, "1926-04-28");
    test:assertEquals(book.author.hometown, "Monroeville, Alabama");
    test:assertEquals(book.publisher.name, "J. B. Lippincott & Co.");
    test:assertEquals(book.publisher.year, 1960);
    test:assertEquals(book.publisher["location"], "Philadelphia");
    test:assertEquals(book["price"], 10.5);
    test:assertEquals(book.author["local"], false);
}

@test:Config
isolated function testYamlStringToRecordWithRestFields() returns error? {
    string content = check io:fileReadString(FILE_PATH + "nested_6.yaml");

    record {|TicketBooking[] bookings;|} bookings = check parseString(content);
    test:assertEquals(bookings.bookings.length(), 2);
    test:assertEquals(bookings.bookings[0].length(), 3);
    test:assertEquals(bookings.bookings[0].event, "RockFest 2024");
    test:assertEquals(bookings.bookings[0].price, 75.00);
    test:assertEquals(bookings.bookings[0].attendee.length(), 3);
    test:assertEquals(bookings.bookings[0].attendee.get("name"), "John Doe");
}

@test:Config
isolated function testNestedYamlStringToMap() returns error? {
    string content = check io:fileReadString(FILE_PATH + "nested_7.yaml");

    map<int[][]> result = check parseString(content);
    test:assertEquals(result.length(), 3);
    test:assertEquals(result.get("matrix1"), [[1, 2, 3], [4, 5, 6], [7, 8, 9]]);
    test:assertEquals(result.get("matrix2"), [[9, 8, 7], [5, 5, 4], [3, 2, 1]]);
    test:assertEquals(result.get("matrix3"), [[5, 2, 7], [9, 4, 1], [3, 6, 8]]);

    map<int[2][2]> result2 = check parseString(content);
    test:assertEquals(result2.length(), 3);
    test:assertEquals(result2.get("matrix1"), [[1, 2], [4, 5]]);
    test:assertEquals(result2.get("matrix2"), [[9, 8], [5, 5]]);
    test:assertEquals(result2.get("matrix3"), [[5, 2], [9, 4]]);
}

@test:Config
isolated function testNestedYamlStringToRecordWithTupleField() returns error? {
    string content = check io:fileReadString(FILE_PATH + "nested_8.yaml");

    LibraryB libraryB = check parseString(content);
    test:assertEquals(libraryB.books.length(), 2);
    test:assertEquals(libraryB.books[0].title, "The Great Gatsby");
    test:assertEquals(libraryB.books[0].author, "F. Scott Fitzgerald");
    test:assertEquals(libraryB.books[1].title, "The Grapes of Wrath");
    test:assertEquals(libraryB.books[1].author, "John Steinbeck");

    LibraryC libraryC = check parseString(content);
    test:assertEquals(libraryC.books.length(), 3);
    test:assertEquals(libraryC.books[0].title, "The Great Gatsby");
    test:assertEquals(libraryC.books[0].author, "F. Scott Fitzgerald");
    test:assertEquals(libraryC.books[1].title, "The Grapes of Wrath");
    test:assertEquals(libraryC.books[1].author, "John Steinbeck");
    test:assertEquals(libraryC.books[2].title, "Binary Echoes: Unraveling the Digital Web");
    test:assertEquals(libraryC.books[2].author, "Alexandra Quinn");
}

@test:Config
isolated function testNestedYamlStringToRecordWithRestFields() returns error? {
    string content = check io:fileReadString(FILE_PATH + "nested_9.yaml");

    record {|
        record {|
            string c;
            string d;
        |}...;
    |} result = check parseString(content);
    test:assertEquals(result.length(), 2);
    test:assertEquals(result["a"]["c"], "world");
    test:assertEquals(result["a"]["d"], "2");
    test:assertEquals(result["b"]["c"], "world");
    test:assertEquals(result["b"]["d"], "2");

    record {|
        map<string>...;
    |} result2 = check parseString(content);
    test:assertEquals(result2.length(), 2);
    test:assertEquals(result2["a"]["c"], "world");
    test:assertEquals(result2["a"]["d"], "2");
    test:assertEquals(result2["b"]["c"], "world");
    test:assertEquals(result2["b"]["d"], "2");
}

@test:Config
isolated function testNestedYamlStringToRecordWithRestFields2() returns error? {
    string content = check io:fileReadString(FILE_PATH + "nested_10.yaml");

    record {|
        record {|
            string c;
            string d;
        |}[]...;
    |} result = check parseString(content);
    test:assertEquals(result.length(), 2);
    test:assertEquals(result["a"], [
        {
            "c": "world",
            "d": "2"
        }
    ]);
    test:assertEquals(result["b"], [
        {
            "c": "world",
            "d": "2"
        }
    ]);
}

@test:Config
isolated function testUnionTypeAsExpTypeForParseString() returns error? {
    decimal|float val1 = check parseString("1.0");
    test:assertEquals(val1, 1.0);

    string content = check io:fileReadString(FILE_PATH + "nested_11.yaml");

    record {|
        record {|decimal|int b; record {|string|boolean e;|} d;|} a;
        decimal|float c;
    |} result = check parseString(content);
    test:assertEquals(result.length(), 2);
    test:assertEquals(result.a.length(), 2);
    test:assertEquals(result.a.b, 1);
    test:assertEquals(result.a.d.e, false);
    test:assertEquals(result.c, 2.0);
}

@test:Config
isolated function testAnydataAsExpTypeForParseString1() returns error? {
    string content = check io:fileReadString(FILE_PATH + "nested_10.yaml");

    anydata result = check parseString(content);
    test:assertEquals(result, {"a":[{"c":"world","d":2}],"b":[{"c":"world","d":2}]});
}

@test:Config
isolated function testAnydataAsExpTypeForParseString2() returns error? {
    string content = check io:fileReadString(FILE_PATH + "nested_11.yaml");

    anydata result = check parseString(content);
    test:assertEquals(result, {"a":{"b":1,"d":{"e":false}},"c":2});
}

@test:Config
isolated function testAnydataArrayAsExpTypeForParseString() returns error? {
    string jsonStr1 = string `[["1"], 2.0]`;
    anydata[] val1 = check parseString(jsonStr1);
    test:assertEquals(val1, [[1], 2.0]);

    string jsonStr2 = string `[["1", 2], 2.0]`;
    anydata[] val2 = check parseString(jsonStr2);
    test:assertEquals(val2, [[1, 2], 2.0]);

    string jsonStr3 = string `[["1", 2], [2, "3"]]`;
    anydata[] val3 = check parseString(jsonStr3);
    test:assertEquals(val3, [[1, 2], [2, 3]]);

    string jsonStr4 = string `{"val" : [[1, 2], "2.0", 3.0, [5, 6]]}`;
    record {|
        anydata[] val;
    |} val4 = check parseString(jsonStr4);
    test:assertEquals(val4, {val: [[1, 2], 2.0, 3.0, [5, 6]]});

    string jsonStr41 = string `{"val1" : [[1, 2], "2.0", 3.0, [5, 6]], "val2" : [[1, 2], "2.0", 3.0, [5, 6]]}`;
    record {|
        anydata[] val1;
        anydata[] val2;
    |} val41 = check parseString(jsonStr41);
    test:assertEquals(val41, {val1: [[1, 2], 2.0, 3.0, [5, 6]], val2: [[1, 2], 2.0, 3.0, [5, 6]]});

    string jsonStr5 = string `{"val" : [["1", 2], [2, "3"]]}`;
    record {|
        anydata[] val;
    |} val5 = check parseString(jsonStr5);
    test:assertEquals(val5, {val: [[1, 2], [2, 3]]});

    string jsonStr6 = string `[{"val" : [["1", 2], [2, "3"]]}]`;
    [record {|anydata[][] val;|}] val6 = check parseString(jsonStr6);
    test:assertEquals(val6, [{val: [[1, 2], [2, 3]]}]);
}

@test:Config
isolated function testJsonAsExpTypeForParseString() returns error? {
    string jsonStr1 = string `1`;
    json val1 = check parseString(jsonStr1);
    test:assertEquals(val1, 1);

    string jsonStr2 = string `{
        "a": "hello",
        "b": 1
    }`;

    json val2 = check parseString(jsonStr2);
    test:assertEquals(val2, {"a": "hello", "b": 1});

    string jsonStr3 = string `{
        "a": {
            "b": 1,
            "d": {
                "e": "hello"
            }
        },
        "c": 2
    }`;

    json val3 = check parseString(jsonStr3);
    test:assertEquals(val3, {"a": {"b": 1, "d": {"e": "hello"}}, "c": 2});

    string jsonStr4 = string `{
        "a": [{
            "b": 1,
            "d": {
                "e": "hello"
            }
        }],
        "c": 2
    }`;

    json val4 = check parseString(jsonStr4);
    test:assertEquals(val4, {"a": [{"b": 1, "d": {"e": "hello"}}], "c": 2});

    string str5 = string `[[1], 2]`;
    json val5 = check parseString(str5);
    test:assertEquals(val5, [[1], 2]);
}

@test:Config
isolated function testJsonArrayAsExpTypeForParseString() returns error? {

}

@test:Config
isolated function testMapAsExpTypeForParseString() returns error? {
    string jsonStr1 = string `{
        "a": "hello",
        "b": "1"
    }`;

    map<string> val1 = check parseString(jsonStr1);
    test:assertEquals(val1, {"a": "hello", "b": "1"});

    string jsonStr2 = string `{
        "a": "hello",
        "b": 1,
        "c": {
            "d": "world",
            "e": "2"
        }
    }`;
    record {|
        string a;
        int b;
        map<string> c;
    |} val2 = check parseString(jsonStr2);
    test:assertEquals(val2.a, "hello");
    test:assertEquals(val2.b, 1);
    test:assertEquals(val2.c, {"d": "world", "e": "2"});

    string jsonStr3 = string `{
        "a": {
            "c": "world",
            "d": "2"
        },
        "b": {
            "c": "world",
            "d": "2"
        }
    }`;

    map<map<string>> val3 = check parseString(jsonStr3);
    test:assertEquals(val3, {"a": {"c": "world", "d": "2"}, "b": {"c": "world", "d": "2"}});

    record {|
        map<string> a;
    |} val4 = check parseString(jsonStr3);
    test:assertEquals(val4.a, {"c": "world", "d": "2"});

    map<record {|
        string c;
        string d;
    |}> val5 = check parseString(jsonStr3);
    test:assertEquals(val5, {"a": {"c": "world", "d": "2"}, "b": {"c": "world", "d": "2"}});

    string jsonStr6 = string `{
        "a": "Kanth",
        "b": {
            "g": {
                "c": "hello",
                "d": "1"
            },
            "h": {
                "c": "world",
                "d": "2"
            }
        }
    }`;
    record {|
        string a;
        map<map<string>> b;
    |} val6 = check parseString(jsonStr6);
    test:assertEquals(val6.a, "Kanth");
    test:assertEquals(val6.b, {"g": {"c": "hello", "d": "1"}, "h": {"c": "world", "d": "2"}});
}

@test:Config
isolated function testProjectionInTupleForParseString() returns Error? {
    string str1 = string `["1", 2, "3", 4, 5, 8]`;
    [string, float] val1 = check parseString(str1);
    test:assertEquals(val1, ["1", 2.0]);

    string str2 = string `{
        "a": ["1", "2", 3, "4", 5, 8]
    }`;
    record {|[string, string] a;|} val2 = check parseString(str2);
    test:assertEquals(val2.a, ["1", "2"]);

    string str3 = string `[1, "4"]`;
    [float] val3 = check parseString(str3);
    test:assertEquals(val3, [1.0]);

    string str4 = string `["1", {}]`;
    [string] val4 = check parseString(str4);
    test:assertEquals(val4, ["1"]);

    string str5 = string `[1, [], {"name": 1}]`;
    [float] val5 = check parseString(str5);
    test:assertEquals(val5, [1.0]);
}

@test:Config
isolated function testProjectionInArrayForParseString() returns Error? {
    string strVal = string `[1, 2, 3, 4, 5]`;
    int[] val = check parseString(strVal);
    test:assertEquals(val, [1, 2, 3, 4, 5]);

    string strVal2 = string `[1, 2, 3, 4, 5]`;
    int[2] val2 = check parseString(strVal2);
    test:assertEquals(val2, [1, 2]);

    string strVal3 = string `{
        "a": [1, 2, 3, 4, 5]
    }`;
    record {|int[2] a;|} val3 = check parseString(strVal3);
    test:assertEquals(val3, {a: [1, 2]});

    string strVal4 = string `{
        "a": [1, 2, 3, 4, 5],
        "b": [1, 2, 3, 4, 5]
    }`;
    record {|int[2] a; int[3] b;|} val4 = check parseString(strVal4);
    test:assertEquals(val4, {a: [1, 2], b: [1, 2, 3]});

    string strVal5 = string `{
        "employees": [
            { "name": "Prakanth",
              "age": 26
            },
            { "name": "Kevin",
              "age": 25
            }
        ]
    }`;
    record {|record {|string name; int age;|}[1] employees;|} val5 = check parseString(strVal5);
    test:assertEquals(val5, {employees: [{name: "Prakanth", age: 26}]});

    string strVal6 = string `[1, 2, 3, { "a" : val_a }]`;
    int[3] val6 = check parseString(strVal6);
    test:assertEquals(val6, [1, 2, 3]);
}

@test:Config
isolated function testProjectionInRecordForParseString() returns error? {
    string jsonStr1 = string `{"name": "John", "age": 30, "city": "New York"}`;
    record {|string name; string city;|} val1 = check parseString(jsonStr1);
    test:assertEquals(val1, {name: "John", city: "New York"});

    string jsonStr2 = string `{"name": "John", "age": "30", "city": "New York"}`;
    record {|string name; string city;|} val2 = check parseString(jsonStr2);
    test:assertEquals(val2, {name: "John", city: "New York"});

    string jsonStr3 = string `{ "name": "John", 
                                "company": {
                                    "name": "wso2", 
                                    "year": 2024,
                                    "addrees": {
                                        "street": "123",
                                        "city": "Berkeley"
                                        }
                                    },
                                "city": "New York" }`;
    record {|string name; string city;|} val3 = check parseString(jsonStr3);
    test:assertEquals(val3, {name: "John", city: "New York"});

    string jsonStr4 = string `{ "name": "John", 
                                "company": [{
                                    "name": "wso2", 
                                    "year": 2024,
                                    "addrees": {
                                        "street": "123",
                                        "city": "Berkeley"
                                        }
                                    }],
                                "city": "New York" }`;
    record {|string name; string city;|} val4 = check parseString(jsonStr4);
    test:assertEquals(val4, {name: "John", city: "New York"});

    string jsonStr5 = string `{ "name": "John", 
                                "company1": [{
                                    "name": "wso2", 
                                    "year": 2024,
                                    "addrees": {
                                        "street": "123",
                                        "city": "Berkeley"
                                        }
                                    }],
                                "city": "New York",
                                "company2": [{
                                    "name": "amzn", 
                                    "year": 2024,
                                    "addrees": {
                                        "street": "123",
                                        "city": "Miami"
                                        }
                                    }]
                                }`;
    record {|string name; string city;|} val5 = check parseString(jsonStr5);
    test:assertEquals(val5, {name: "John", city: "New York"});
}

@test:Config
isolated function testArrayOrTupleCaseForParseString() returns error? {
    string jsonStr1 = string `[["1"], 2.0]`;
    [[string], float] val1 = check parseString(jsonStr1);
    test:assertEquals(val1, [["1"], 2.0]);

    string jsonStr2 = string `[["1", 2], "2.0"]`;
    [[string, float], string] val2 = check parseString(jsonStr2);
    test:assertEquals(val2, [["1", 2.0], "2.0"]);

    string jsonStr3 = string `[[1, 2], [2, 3]]`;
    int[][] val3 = check parseString(jsonStr3);
    test:assertEquals(val3, [[1, 2], [2, 3]]);

    string jsonStr4 = string `{"val" : [[1, 2], "2.0", 3.0, ["5", 6]]}`;
    record {|
        [[int, float], string, float, [string, int]] val;
    |} val4 = check parseString(jsonStr4);
    test:assertEquals(val4, {val: [[1, 2.0], "2.0", 3.0, ["5", 6]]});

    string jsonStr41 = string `{"val1" : [[1, 2], "2.0", 3.0, ["5", 6]], "val2" : [[1, 2], "2.0", 3.0, ["5", 6]]}`;
    record {|
        [[int, float], string, float, [string, int]] val1;
        [[float, float], string, float, [string, float]] val2;
    |} val41 = check parseString(jsonStr41);
    test:assertEquals(val41, {val1: [[1, 2.0], "2.0", 3.0, ["5", 6]], val2: [[1.0, 2.0], "2.0", 3.0, ["5", 6.0]]});

    string jsonStr5 = string `{"val" : [[1, 2], [2, 3]]}`;
    record {|
        int[][] val;
    |} val5 = check parseString(jsonStr5);
    test:assertEquals(val5, {val: [[1, 2], [2, 3]]});

    string jsonStr6 = string `[{"val" : [[1, 2], [2, 3]]}]`;
    [record {|int[][] val;|}] val6 = check parseString(jsonStr6);
    test:assertEquals(val6, [{val: [[1, 2], [2, 3]]}]);
}

@test:Config
isolated function testListFillerValuesWithParseString() returns error? {
    int[2] jsonVal1 = check parseString("[1]");
    test:assertEquals(jsonVal1, [1, 0]);

    [int, float, string, boolean] jsonVal2 = check parseString("[1]");
    test:assertEquals(jsonVal2, [1, 0.0, "", false]);

    record {|
        float[3] A;
        [int, decimal, float, boolean] B;
    |} jsonVal3 = check parseString(string `{"A": [1], "B": [1]}`);
    test:assertEquals(jsonVal3, {A: [1.0, 0.0, 0.0], B: [1, 0d, 0.0, false]});
}

@test:Config
isolated function testSingletonAsExpectedTypeForParseString() returns error? {
    "1" val1 = check parseString("\"1\"");
    test:assertEquals(val1, "1");

    Singleton1 val2 = check parseString("1");
    test:assertEquals(val2, 1);

    SingletonUnion val3 = check parseString("1");
    test:assertEquals(val3, 1);

    () val4 = check parseString("null");
    test:assertEquals(val4, ());

    // string str5 = string `{
    //     "value": 1,
    //     "id": "3"
    // }`;
    // SingletonInRecord val5 = check parseString(str5);
    // test:assertEquals(val5.id, "3");
    // test:assertEquals(val5.value, 1);
}

@test:Config
function testDuplicateKeyInTheStringSource() returns error? {

}

@test:Config
function testNameAnnotationWithParseString() returns error? {

}

@test:Config
isolated function testByteAsExpectedTypeForParseString() returns error? {
    byte result = check parseString("1");
    test:assertEquals(result, 1);

    [byte, int] result2 = check parseString("[255, 2000]");
    test:assertEquals(result2, [255, 2000]);

    string content = check io:fileReadString(FILE_PATH + "nested_12.yaml");

    record {
        byte id;
        string name;
        record {
            string street;
            string city;
            byte id;
        } address;
    } result3 = check parseString(content);
    test:assertEquals(result3.length(), 3);
    test:assertEquals(result3.id, 1);
    test:assertEquals(result3.name, "Anne");
    test:assertEquals(result3.address.length(), 3);
    test:assertEquals(result3.address.street, "Main");
    test:assertEquals(result3.address.city, "94");
    test:assertEquals(result3.address.id, 2);
}

@test:Config
isolated function testSignedInt8AsExpectedTypeForParseString() returns error? {
    int:Signed8 val1 = check parseString("-128");
    test:assertEquals(val1, -128);

    int:Signed8 val2 = check parseString("127");
    test:assertEquals(val2, 127);

    [int:Signed8, int] val3 = check parseString("[127, 2000]");
    test:assertEquals(val3, [127, 2000]);

    string content = check io:fileReadString(FILE_PATH + "nested_13.yaml");

    record {
        int:Signed8 id;
        string name;
        record {
            string street;
            string city;
            int:Signed8 id;
        } address;
    } val4 = check parseString(content);
    test:assertEquals(val4.length(), 3);
    test:assertEquals(val4.id, 100);
    test:assertEquals(val4.name, "Anne");
    test:assertEquals(val4.address.length(), 3);
    test:assertEquals(val4.address.street, "Main");
    test:assertEquals(val4.address.city, "94");
    test:assertEquals(val4.address.id, -2);
}

@test:Config
isolated function testSignedInt16AsExpectedTypeForParseString() returns error? {
    int:Signed16 val1 = check parseString("-32768");
    test:assertEquals(val1, -32768);

    int:Signed16 val2 = check parseString("32767");
    test:assertEquals(val2, 32767);

    [int:Signed16, int] val3 = check parseString("[32767, -324234]");
    test:assertEquals(val3, [32767, -324234]);

    string content = check io:fileReadString(FILE_PATH + "nested_13.yaml");

    record {
        int:Signed16 id;
        string name;
        record {
            string street;
            string city;
            int:Signed16 id;
        } address;
    } val4 = check parseString(content);
    test:assertEquals(val4.length(), 3);
    test:assertEquals(val4.id, 100);
    test:assertEquals(val4.name, "Anne");
    test:assertEquals(val4.address.length(), 3);
    test:assertEquals(val4.address.street, "Main");
    test:assertEquals(val4.address.city, "94");
    test:assertEquals(val4.address.id, -2);
}

@test:Config
isolated function testSignedInt32AsExpectedTypeForParseString() returns error? {
    int:Signed32 val1 = check parseString("-2147483648");
    test:assertEquals(val1, -2147483648);

    int:Signed32 val2 = check parseString("2147483647");
    test:assertEquals(val2, 2147483647);

    int:Signed32[] val3 = check parseString("[2147483647, -2147483648]");
    test:assertEquals(val3, [2147483647, -2147483648]);

    string content = check io:fileReadString(FILE_PATH + "nested_14.yaml");

    record {
        int:Signed32 id;
        string name;
        record {
            string street;
            string city;
            int:Signed32 id;
        } address;
    } val4 = check parseString(content);
    test:assertEquals(val4.length(), 3);
    test:assertEquals(val4.id, 2147483647);
    test:assertEquals(val4.name, "Anne");
    test:assertEquals(val4.address.length(), 3);
    test:assertEquals(val4.address.street, "Main");
    test:assertEquals(val4.address.city, "94");
    test:assertEquals(val4.address.id, -2147483648);
}

@test:Config
isolated function testUnSignedInt8AsExpectedTypeForParseString() returns error? {
    int:Unsigned8 val1 = check parseString("255");
    test:assertEquals(val1, 255);

    int:Unsigned8 val2 = check parseString("0");
    test:assertEquals(val2, 0);

    int:Unsigned8[] val3 = check parseString("[0, 255]");
    test:assertEquals(val3, [0, 255]);

    string content = check io:fileReadString(FILE_PATH + "nested_15.yaml");

    record {
        int:Unsigned8 id;
        string name;
        record {
            string street;
            string city;
            int:Unsigned8 id;
        } address;
    } val4 = check parseString(content);
    test:assertEquals(val4.length(), 3);
    test:assertEquals(val4.id, 0);
    test:assertEquals(val4.name, "Anne");
    test:assertEquals(val4.address.length(), 3);
    test:assertEquals(val4.address.street, "Main");
    test:assertEquals(val4.address.city, "94");
    test:assertEquals(val4.address.id, 255);
}

@test:Config
isolated function testUnSignedInt16AsExpectedTypeForParseString() returns error? {
    int:Unsigned16 val1 = check parseString("65535");
    test:assertEquals(val1, 65535);

    int:Unsigned16 val2 = check parseString("0");
    test:assertEquals(val2, 0);

    int:Unsigned16[] val3 = check parseString("[0, 65535]");
    test:assertEquals(val3, [0, 65535]);

    string content = check io:fileReadString(FILE_PATH + "nested_16.yaml");

    record {
        int:Unsigned16 id;
        string name;
        record {
            string street;
            string city;
            int:Unsigned16 id;
        } address;
    } val4 = check parseString(content);
    test:assertEquals(val4.length(), 3);
    test:assertEquals(val4.id, 0);
    test:assertEquals(val4.name, "Anne");
    test:assertEquals(val4.address.length(), 3);
    test:assertEquals(val4.address.street, "Main");
    test:assertEquals(val4.address.city, "94");
    test:assertEquals(val4.address.id, 65535);
}

@test:Config
isolated function testUnSignedInt32AsExpectedTypeForParseString() returns error? {
    int:Unsigned32 val1 = check parseString("4294967295");
    test:assertEquals(val1, 4294967295);

    int:Unsigned32 val2 = check parseString("0");
    test:assertEquals(val2, 0);

    int:Unsigned32[] val3 = check parseString("[0, 4294967295]");
    test:assertEquals(val3, [0, 4294967295]);

    string content = check io:fileReadString(FILE_PATH + "nested_17.yaml");

    record {
        int:Unsigned32 id;
        string name;
        record {
            string street;
            string city;
            int:Unsigned32 id;
        } address;
    } val4 = check parseString(content);
    test:assertEquals(val4.length(), 3);
    test:assertEquals(val4.id, 0);
    test:assertEquals(val4.name, "Anne");
    test:assertEquals(val4.address.length(), 3);
    test:assertEquals(val4.address.street, "Main");
    test:assertEquals(val4.address.city, "94");
    test:assertEquals(val4.address.id, 4294967295);
}

@test:Config
isolated function testNilableTypeAsFieldTypeForParseString() returns error? {

}

@test:Config
isolated function testNilableTypeAsFieldTypeForParseAsType() returns error? {

}

@test:Config
isolated function testEscapeCharacterCaseForParseString() returns error? {
    string jsonStr1 = string `
    {
        "A": "\\A_Field",
        "B": "\/B_Field",
        "C": "\"C_Field\"",
        "D": "\uD83D\uDE01",
        "E": "FIELD\nE",
        "F": "FIELD\rF",
        "G": "FIELD\tG",
        "H": ["\\A_Field", "\/B_Field", "\"C_Field\"", "\uD83D\uDE01", "FIELD\nE", "FIELD\rF", "FIELD\tG"]
    }
    `;
    OpenRecord val1 = check parseString(jsonStr1);

    OpenRecord expectedResult = {
        "A": "\\\\A_Field",
        "B": "/B_Field",
        "C": "\"C_Field\"",
        "D": "😁",
        "E": "FIELD\nE",
        "F": "FIELD\rF",
        "G": "FIELD\tG",
        "H": ["\\\\A_Field", "/B_Field", "\"C_Field\"", "😁", "FIELD\nE", "FIELD\rF", "FIELD\tG"]
    };

    test:assertEquals(val1, expectedResult);
}
