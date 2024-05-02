package io.ballerina.stdlib.data.yaml.utils;

import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.stdlib.data.yaml.common.Types;

public class OptionsUtils {

    public record WriteConfig(int indentationPolicy, int blockLevel, boolean canonical, boolean useSingleQuotes,
                              boolean forceQuotes, Types.YAMLSchema schema, boolean isStream, boolean flowStyle) {
    }

    public static WriteConfig resolveWriteOptions(BMap<BString, Object> options) {
        Object indentationPolicy = options.get(Constants.INDENTATION_POLICY);
        Object blockLevel = options.get(Constants.BLOCK_LEVEL);
        Object canonical = options.get(Constants.CANONICAL);
        Object useSingleQuotes = options.get(Constants.USE_SINGLE_QUOTES);
        Object forceQuotes = options.get(Constants.FORCE_QUOTES);
        Object schema = options.get(Constants.SCHEMA);
        Object isStream = options.get(Constants.IS_STREAM);
        Object flowStyle = options.get(Constants.FLOW_STYLE);

        return new WriteConfig(Math.toIntExact(((Long) indentationPolicy)), Math.toIntExact(((Long) blockLevel)),
                ((Boolean) canonical), ((Boolean) useSingleQuotes), ((Boolean) forceQuotes),
                Types.YAMLSchema.valueOf(((BString) schema).getValue()), ((Boolean) isStream), ((Boolean) flowStyle));
    }
}
