import ballerina/data.yaml as yml;

public function main() returns error? {
    record {
        @yml:Name {
            value: "B"
        }
        string A;
        string B;
    } _ = check yml:parseString(string `{
        "A": "Hello",
        "B": "World"
    }`);

    record {
        @yml:Name {
            value: "B"
        }
        string A;
        string B;
    } _ = {A: "Hello", B: "World"};
}
