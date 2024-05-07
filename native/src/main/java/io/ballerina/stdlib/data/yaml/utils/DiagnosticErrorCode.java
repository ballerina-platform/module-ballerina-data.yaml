package io.ballerina.stdlib.data.yaml.utils;

public enum DiagnosticErrorCode {

    UNSUPPORTED_TYPE("YAML_ERROR_001", "unsupported.type"),
    YAML_READER_FAILURE("YAML_ERROR_002", "yaml.reader.failure"),
    YAML_PARSER_EXCEPTION("YAML_ERROR_003", "yaml.parser.exception"),
    DUPLICATE_FIELD("YAML_ERROR_004", "duplicate.field"),
    INCOMPATIBLE_TYPE("YAML_ERROR_005", "incompatible.type"),
    UNDEFINED_FIELD("YAML_ERROR_006", "undefined.field"),
    ARRAY_SIZE_MISMATCH("YAML_ERROR_007", "array.size.mismatch"),
    INVALID_TYPE("YAML_ERROR_008", "invalid.type"),
    INCOMPATIBLE_VALUE_FOR_FIELD("YAML_ERROR_009", "incompatible.value.for.field"),
    REQUIRED_FIELD_NOT_PRESENT("YAML_ERROR_010", "required.field.not.present"),
    INVALID_TYPE_FOR_FIELD("YAML_ERROR_011", "invalid.type.for.field"),
    CANNOT_CONVERT_TO_EXPECTED_TYPE("YAML_ERROR_012", "cannot.convert.to.expected.type");

    final String diagnosticId;
    final String messageKey;

    DiagnosticErrorCode(String diagnosticId, String messageKey) {
        this.diagnosticId = diagnosticId;
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }
}
