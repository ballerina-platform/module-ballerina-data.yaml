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
