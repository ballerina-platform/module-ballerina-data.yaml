package io.ballerina.stdlib.data.yaml.utils;

import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BString;

public class Constants {

    public static final BString INDENTATION_POLICY = StringUtils.fromString("indentationPolicy");
    public static final BString BLOCK_LEVEL = StringUtils.fromString("blockLevel");
    public static final BString CANONICAL = StringUtils.fromString("canonical");
    public static final BString USE_SINGLE_QUOTES = StringUtils.fromString("useSingleQuotes");
    public static final BString FORCE_QUOTES = StringUtils.fromString("forceQuotes");
    public static final BString SCHEMA = StringUtils.fromString("schema");
    public static final BString IS_STREAM = StringUtils.fromString("isStream");
    public static final BString FLOW_STYLE = StringUtils.fromString("flowStyle");
}
