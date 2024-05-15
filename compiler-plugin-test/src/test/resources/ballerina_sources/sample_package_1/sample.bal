import ballerina/data.yaml;

public function main() returns error? {
    int|table<record {|string a;|}>|record {| int b;|} val = check yaml:parseString("1");
}
