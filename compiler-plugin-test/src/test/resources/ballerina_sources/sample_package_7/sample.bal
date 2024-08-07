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

import ballerina/data.yaml;

type T1 (map<anydata>|int|xml)[];
type T2 record {|
    string p1;
    table<record {|string a;|}>|int p2;
|};

public function main() returns error? {
    string str1 = string `[
            {
                "p1":"v1",
                "p2":1
            },
            {
                "p1":"v2",
                "p2":true
            }
        ]`;
    T1 _ = check yaml:parseString(str1);

    string str2 = string `
        {
            "p1":"v1",
            "p2": {
                "a": 1,
                "b": 2
            }
        }`;
    T2 _ = check yaml:parseString(str2);
}
