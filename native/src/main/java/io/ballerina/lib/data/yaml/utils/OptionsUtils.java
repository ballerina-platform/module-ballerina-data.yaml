/*
 * Copyright (c) 2024, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.lib.data.yaml.utils;

import io.ballerina.lib.data.yaml.common.Types;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

/**
 * This class will parse the user configs to Java records.
 *
 * @since 0.1.0
 */
public class OptionsUtils {

    private OptionsUtils() {
    }

    public record WriteConfig(int indentationPolicy, int blockLevel, boolean canonical, boolean useSingleQuotes,
                              boolean forceQuotes, Types.YAMLSchema schema, boolean isStream, boolean flowStyle) {
    }

    public static WriteConfig resolveWriteOptions(BMap<BString, Object> options) {
        Long indentationPolicy = (Long) options.get(Constants.INDENTATION_POLICY);
        Long blockLevel = (Long) options.get(Constants.BLOCK_LEVEL);
        Boolean canonical = (Boolean) options.get(Constants.CANONICAL);
        Boolean useSingleQuotes = (Boolean) options.get(Constants.USE_SINGLE_QUOTES);
        Boolean forceQuotes = (Boolean) options.get(Constants.FORCE_QUOTES);
        BString schema = (BString) options.get(Constants.SCHEMA);
        Boolean isStream = (Boolean) options.get(Constants.IS_STREAM);
        Boolean flowStyle = (Boolean) options.get(Constants.FLOW_STYLE);

        return new WriteConfig(Math.toIntExact(indentationPolicy), Math.toIntExact(blockLevel), canonical,
                useSingleQuotes, forceQuotes, Types.YAMLSchema.valueOf(schema.getValue()), isStream, flowStyle);
    }

    public record ReadConfig(Types.YAMLSchema schema, boolean allowAnchorRedefinition,
                             boolean allowMapEntryRedefinition, boolean allowDataProjection,
                             boolean nilAsOptionalField, boolean absentAsNilableType, boolean enableYamlStreamReorder) {
    }

    public static ReadConfig resolveReadConfig(BMap<BString, Object> options) {
        BString schema = (BString) options.get(Constants.SCHEMA);
        Boolean allowAnchorRedefinition = (Boolean) options.get(Constants.ALLOW_ANCHOR_REDEFINITION);
        Boolean allowMapEntryRedefinition = (Boolean) options.get(Constants.ALLOW_MAP_ENTRY_REDEFINITION);
        Object allowDataProjection = options.get(Constants.ALLOW_DATA_PROJECTION);
        if (allowDataProjection instanceof Boolean) {
            return new ReadConfig(Types.YAMLSchema.valueOf(schema.getValue()), allowAnchorRedefinition,
                    allowMapEntryRedefinition, false, false, false, false);
        }
        Boolean nilAsOptionalField = (Boolean) ((BMap<?, ?>) allowDataProjection).get(Constants.NIL_AS_OPTIONAL_FIELD);
        Boolean absentAsNilableType = (Boolean) ((BMap<?, ?>) allowDataProjection).
                get(Constants.ABSENT_AS_NILABLE_TYPE);
        Boolean enableYamlStreamReorder = (Boolean) ((BMap<?, ?>) allowDataProjection)
                .get(Constants.ENABLE_YAML_STREAM_REORDER);

        return new ReadConfig(Types.YAMLSchema.valueOf(schema.getValue()), allowAnchorRedefinition,
                allowMapEntryRedefinition, true, nilAsOptionalField, absentAsNilableType,
                enableYamlStreamReorder);
    }
}
