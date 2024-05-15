import ballerina/data.yaml as yml;

type Union int|table<record {|string a;|}>|record {| int b;|};

public function main() returns error? {
    Union val = check yml:parseString("1");
}
