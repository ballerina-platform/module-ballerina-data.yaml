// Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
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

import ballerina/data.yaml as _;

type Tree int|Tree[];

type Cloneable (any & readonly)|xml|Cloneable[]|map<Cloneable>|table<map<Cloneable>>;

type ContextEntry Cloneable|isolated object {};

type Node record {|
    int value;
    Node? next;
|};

public function main() {
    Tree tree = [1, [2, 3]];
    ContextEntry entry = 1;
    Node node = {value: 1, next: ()};
    _ = tree;
    _ = entry;
    _ = node;
}
