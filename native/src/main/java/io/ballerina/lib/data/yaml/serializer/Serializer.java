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

package io.ballerina.stdlib.data.yaml.serializer;

import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BValue;
import io.ballerina.stdlib.data.yaml.common.Types;
import io.ballerina.stdlib.data.yaml.common.YamlEvent;

import java.util.ArrayList;
import java.util.List;

import static io.ballerina.stdlib.data.yaml.utils.Constants.DEFAULT_GLOBAL_MAP_TAG_HANDLE;
import static io.ballerina.stdlib.data.yaml.utils.Constants.DEFAULT_GLOBAL_SEQ_TAG_HANDLE;
import static io.ballerina.stdlib.data.yaml.utils.Constants.DEFAULT_GLOBAL_STR_TAG_HANDLE;

/**
 * Converts a Ballerina value to stream of YAML events.
 *
 * @since 0.1.0
 */
public class Serializer {

    public static class SerializerState {
        List<YamlEvent> events;
        final char delimiter;
        final boolean forceQuotes;
        final boolean flowStyle;
        final int blockLevel;
        boolean isStream;

        public SerializerState(char delimiter, boolean forceQuotes, int blockLevel,
                               boolean flowStyle, boolean isStream) {
            this.events = new ArrayList<>();
            this.delimiter = delimiter;
            this.forceQuotes = forceQuotes;
            this.blockLevel = blockLevel;
            this.flowStyle = flowStyle;
            this.isStream = isStream;
        }

        public List<YamlEvent> getEvents() {
            return events;
        }
    }

    public static void serialize(SerializerState state, Object data) {
        serialize(state, data, 0, null);
    }

    public static void serialize(SerializerState state, Object value, int depthLevel, String excludeTag) {
        if (value instanceof BValue) {
            int typeTag = ((BValue) value).getType().getTag();
            if (typeTag == TypeTags.ARRAY_TAG || typeTag == TypeTags.TUPLE_TAG) {
                serializeSequence(state, ((BArray) value), depthLevel);
                return;
            } else if (typeTag == TypeTags.MAP_TAG || typeTag == TypeTags.RECORD_TYPE_TAG) {
                serializeMapping(state, ((BMap<BString, Object>) value),
                        depthLevel);
                return;
            }
        }
        serializeString(state, value);
    }

    private static void serializeString(SerializerState state, Object data) {
        String value = data.toString();
        if (value.contains("\n")) {
            value = state.delimiter + value.replaceAll("\n", "\\n") + state.delimiter;
        } else {
            value = state.forceQuotes ? state.delimiter + value + state.delimiter : value;
        }

        YamlEvent scalarEvent = new YamlEvent.ScalarEvent(value);
        scalarEvent.setTag(DEFAULT_GLOBAL_STR_TAG_HANDLE);
        state.events.add(scalarEvent);
    }

    private static void serializeSequence(SerializerState state, BArray data, int depthLevel) {
        if (state.isStream) {
            state.isStream = false;
            for (int i = 0; i < data.size(); i++) {
                serialize(state, data.get(i), depthLevel + 1, DEFAULT_GLOBAL_SEQ_TAG_HANDLE);
            }
        } else {
            YamlEvent startEvent = new YamlEvent.StartEvent(Types.Collection.SEQUENCE,
                    state.flowStyle, false);
            startEvent.setTag(DEFAULT_GLOBAL_SEQ_TAG_HANDLE);
            state.events.add(startEvent);

            for (int i = 0; i < data.size(); i++) {
                serialize(state, data.get(i), depthLevel + 1, DEFAULT_GLOBAL_SEQ_TAG_HANDLE);
            }

            state.events.add(new YamlEvent.EndEvent(Types.Collection.SEQUENCE));
        }
    }

    private static void serializeMapping(SerializerState state, BMap<BString, Object> bMap,
                                         int depthLevel) {
        state.events.add(new YamlEvent.StartEvent(Types.Collection.MAPPING, state.flowStyle, false));

        BString[] keys = bMap.getKeys();
        for (BString key: keys) {
            serialize(state, key, depthLevel, DEFAULT_GLOBAL_MAP_TAG_HANDLE);
            serialize(state, bMap.get(key), depthLevel + 1, DEFAULT_GLOBAL_MAP_TAG_HANDLE);
        }

        state.events.add(new YamlEvent.EndEvent(Types.Collection.MAPPING));
    }
}
