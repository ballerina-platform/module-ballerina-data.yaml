import ballerina/io;
import ballerina/test;

const ANCHORS_TEST_PATH = FILE_PATH + "anchors/";

@test:Config
isolated function testAnchorWithAnydataExpectedType() returns error? {
    string content = check io:fileReadString(ANCHORS_TEST_PATH + "anchor_test_1.yaml");
    anydata result = check parseString(content);
    final anydata & readonly expectedResult = {
        "user_info": {
            "name": "John Doe",
            "email": "john.doe@example.com",
            "roles": ["admin", "editor"]
        },
        "server_config": {
            "owner": {
                "name": "John Doe",
                "email": "john.doe@example.com",
                "roles": ["admin", "editor"]
            },
            "ports": [80, 443],
            "host": "localhost",
            "database": "my_database"
        },
        "web_server": {
            "ports": [8080],
            "database": {"host": "localhost", "database": "my_database"},
            "roles": ["admin", "editor"]
        }
    };
    test:assertEquals(result, expectedResult);
}

@test:Config
isolated function testAnchorWithRecordAsExpectedType() returns error? {
    string content = check io:fileReadString(ANCHORS_TEST_PATH + "anchor_test_2.yaml");
    ProductDetails result = check parseString(content);
    ProductDetails expectedResult = {
        product: {name: "T-Shirt", brand: "Acme Clothing"},
        variation_1: {size: "S", color: "red"},
        variation_2: {size: "M", color: "blue"},
        shopping_cart: {
            items: [{size: "S", color: "red"}, {size: "M", color: "blue"}],
            total_price: 29.99,
            currency: "USD"
        }
    };
    test:assertEquals(result, expectedResult);
}

type ProductDetails record {
    record {
        string name;
        string brand;
    } product;
    record {
        string size;
        string color;
    } variation_1;
    record {
        string size;
        string color;
    } variation_2;
    record {
        record {
            string size;
            string color;
        }[] items;
        decimal total_price;
        string currency;
    } shopping_cart;
};
