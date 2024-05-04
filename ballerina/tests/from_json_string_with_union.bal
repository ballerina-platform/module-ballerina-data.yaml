import ballerina/test;

type RecA record {
    string a;
    int|float|string b;
};

type Union1 json|RecA|int;

type Union2 RecA|json|int;

@test:Config {
    groups: ["Union"]
}
isolated function testUnionTypeAsExpectedTypeForParseString1() returns error? {
    string jsonStr = string `{
        "a": "1",
        "b": 2
    }`;
    Union1 val = check parseString(jsonStr);
    test:assertTrue(val is json);
    test:assertEquals(val, {a: 1, b: 2});

    Union2 val2 = check parseString(jsonStr);
    test:assertTrue(val2 is RecA);
    test:assertEquals(val2, {a: "1", b: 2});
}

type RecB record {|
    Union1 field1;
    Union2 field2;
|};

@test:Config {
    groups: ["Union"]
}
isolated function testUnionTypeAsExpectedTypeForParseString2() returns error? {
    string jsonStr = string `{
        "field1": {
            "a": "1",
            "b": 2
        },
        "field2": {
            "a": "3",
            "b": 4
        }
    }`;
    RecB val = check parseString(jsonStr);
    test:assertTrue(val.field1 is json);
    test:assertEquals(val.field1, {a: 1, b: 2});
    test:assertTrue(val.field2 is RecA);
    test:assertEquals(val.field2, {a: "3", b: 4});
}

type RecC record {
    Union2[] field1;
    int|float[] field2;
    string field3;
};

@test:Config {
    groups: ["Union"]
}
isolated function testUnionTypeAsExpectedTypeForParseString3() returns error? {
    string jsonStr = string `{
        "field1": [
            {
                "a": "1",
                "b": 2
            },
            {
                "a": "3",
                "b": 4
            }
        ],
        "field2": [1.0, 2.0],
        "field3": "test"
    }`;
    RecC val = check parseString(jsonStr);
    test:assertTrue(val.field1 is Union2[]);
    test:assertTrue(val.field1[0] is RecA);
    test:assertEquals(val.field1, [{a: "1", b: 2}, {a: "3", b: 4}]);
    test:assertTrue(val.field2 is float[]);
    test:assertEquals(val.field2, [1.0, 2.0]);
    test:assertEquals(val.field3, "test");
}

type RecD record {
    RecB|RecC l;
    record {
        string|RecA m;
        int|float n;
    } p;
    string q;
};

@test:Config {
    groups: ["Union"]
}
isolated function testUnionTypeAsExpectedTypeForParseString4() returns error? {
    string jsonStr = string `{
        "l": {
            "field1": {
                "a": "1",
                "b": 2
            },
            "field2": {
                "a": "3",
                "b": 4
            }
        },
        "p": {
            "m": "5",
            "n": 6
        },
        "q": "test"
    }`;
    RecD val = check parseString(jsonStr);
    test:assertTrue(val.l is RecB);
    test:assertEquals(val.l.field1, {a: 1, b: 2});
    test:assertEquals(val.l.field2, {a: "3", b: 4});
    test:assertTrue(val.p.m is string);
    test:assertEquals(val.p.m, "5");
    test:assertTrue(val.p.n is int);
    test:assertEquals(val.p.n, 6);
    test:assertEquals(val.q, "test");
}

type UnionList1 [int, int, int]|int[]|float[];

@test:Config {
    groups: ["Union"]
}
isolated function testUnionTypeAsExpectedTypeForParseString5() returns error? {
    string jsonStr = string `[1, 2, 3]`;
    UnionList1 val = check parseString(jsonStr);
    test:assertTrue(val is [int, int, int]);
    test:assertEquals(val, [1, 2, 3]);

    string jsonStr2 = string `[1, 2, 3, 4]`;
    UnionList1 val2 = check parseString(jsonStr2);
    test:assertTrue(val is [int, int, int]);
    test:assertEquals(val2, [1, 2, 3]);
}

type RecE record {|
    UnionList1 l;
    RecB|RecC m;
    float[] n;
|};

@test:Config {
    groups: ["Union"]
}
isolated function testUnionTypeAsExpectedTypeForParseString6() returns error? {
    string jsonStr = string `{
        "l": [1, 2, 3],
        "m": {
            "field1": {
                "a": "1",
                "b": 2
            },
            "field2": {
                "a": "3",
                "b": 4
            }
        },
        "n": [1.0, 2.0]
    }`;
    RecE val = check parseString(jsonStr);
    test:assertTrue(val.l is [int, int, int]);
    test:assertEquals(val.l, [1, 2, 3]);
    test:assertTrue(val.m is RecB);
    test:assertEquals(val.m.field1, {a: 1, b: 2});
    test:assertEquals(val.m.field2, {a: "3", b: 4});
    test:assertEquals(val.n, [1.0, 2.0]);
}
