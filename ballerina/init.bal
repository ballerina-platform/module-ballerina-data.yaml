import ballerina/jballerina.java;

isolated function init() {
    setModule();
}

isolated function setModule() = @java:Method {
    'class: "io.ballerina.stdlib.data.yaml.utils.ModuleUtils"
} external;
