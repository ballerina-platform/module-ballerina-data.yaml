package io.ballerina.stdlib.data.yaml.common;

public class Types {

    public static final String DEFAULT_LOCAL_TAG_HANDLE = "!";
    public static final String DEFAULT_GLOBAL_TAG_HANDLE = "tag:yaml.org,2002:";
    public static final String DEFAULT_GLOBAL_SEQ_TAG_HANDLE = DEFAULT_GLOBAL_TAG_HANDLE + "seq";
    public static final String DEFAULT_GLOBAL_MAP_TAG_HANDLE = DEFAULT_GLOBAL_TAG_HANDLE + "map";
    public static final String DEFAULT_GLOBAL_STR_TAG_HANDLE = DEFAULT_GLOBAL_TAG_HANDLE + "str";

    public enum Collection {
        STREAM,
        SEQUENCE,
        MAPPING
    }

    public enum FailSafeSchema {
        MAPPING,
        SEQUENCE,
        STRING
    }

    public enum YAMLSchema {
        FAILSAFE_SCHEMA,
        JSON_SCHEMA,
        CORE_SCHEMA
    }
}
