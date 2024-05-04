package io.ballerina.stdlib.data.yaml.utils;

public enum DiagnosticErrorCode {

    UNSUPPORTED_TYPE("YAML_ERROR_001", "unsupported.type"),
    DUPLICATE_FIELD("YAML_ERROR_002", "duplicate.field"),
//    JSON_READER_FAILURE("JSON_ERROR_002", "json.reader.failure"),
    YAML_PARSER_EXCEPTION("JSON_ERROR_003", "json.parser.exception"),
    INCOMPATIBLE_TYPE("JSON_ERROR_004", "incompatible.type"),
    ARRAY_SIZE_MISMATCH("JSON_ERROR_005", "array.size.mismatch"),
    INVALID_TYPE("JSON_ERROR_006", "invalid.type"),
    INCOMPATIBLE_VALUE_FOR_FIELD("JSON_ERROR_007", "incompatible.value.for.field"),
    REQUIRED_FIELD_NOT_PRESENT("JSON_ERROR_008", "required.field.not.present"),
    INVALID_TYPE_FOR_FIELD("JSON_ERROR_009", "invalid.type.for.field"),
    CANNOT_CONVERT_TO_EXPECTED_TYPE("JSON_ERROR_011", "cannot.convert.to.expected.type"),
    UNDEFINED_FIELD("YAML_ERROR_005", "undefined.field");
    String diagnosticId;
    String messageKey;

    DiagnosticErrorCode(String diagnosticId, String messageKey) {
        this.diagnosticId = diagnosticId;
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }
}
