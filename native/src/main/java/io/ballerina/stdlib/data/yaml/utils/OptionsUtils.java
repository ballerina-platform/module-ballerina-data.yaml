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
