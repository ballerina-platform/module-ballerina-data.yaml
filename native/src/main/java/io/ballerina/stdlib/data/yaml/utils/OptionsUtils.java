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

    public record ReadConfig(Types.YAMLSchema schema, boolean allowAnchorRedefinition,
                             boolean allowMapEntryRedefinition, boolean allowDataProjection,
                             boolean nilAsOptionalField, boolean absentAsNilableType, boolean isStream) {
    }

    public static ReadConfig resolveReadConfig(BMap<BString, Object> options) {
        Object schema = options.get(Constants.SCHEMA);
        Object allowAnchorRedefinition = options.get(Constants.ALLOW_ANCHOR_REDEFINITION);
        Object allowMapEntryRedefinition = options.get(Constants.ALLOW_MAP_ENTRY_REDEFINITION);
        Object allowDataProjection = options.get(Constants.ALLOW_DATA_PROJECTION);
        Object isStream = options.get(Constants.IS_STREAM);
        if (allowDataProjection instanceof Boolean) {
            return new ReadConfig(Types.YAMLSchema.valueOf(((BString) schema).getValue()),
                    ((Boolean) allowAnchorRedefinition), ((Boolean) allowMapEntryRedefinition),
                    false, false, false, ((Boolean) isStream));
        }
        Object nilAsOptionalField = ((BMap<?, ?>) allowDataProjection).get(Constants.NIL_AS_OPTIONAL_FIELD);
        Object absentAsNilableType = ((BMap<?, ?>) allowDataProjection).get(Constants.ABSENT_AS_NILABLE_TYPE);

        return new ReadConfig(Types.YAMLSchema.valueOf(((BString) schema).getValue()),
                ((Boolean) allowAnchorRedefinition), ((Boolean) allowMapEntryRedefinition), true,
                ((Boolean) nilAsOptionalField), ((Boolean) absentAsNilableType), ((Boolean) isStream));
    }
}
