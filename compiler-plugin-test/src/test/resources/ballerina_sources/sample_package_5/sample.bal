import ballerina/data.yaml;

type Data record {
    @yaml:Name {
        value: "B"
    }
    string A;
    string B;
};
