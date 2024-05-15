import ballerina/data.yaml;

type UnionType table<record {|int a;|}>|record {|string b;|};

type IntersectionType UnionType & readonly;

public function main() returns error? {
    string str = string `{"a": 1, "b": "str"}`;
    IntersectionType _ = check yaml:parseString(str);
}
