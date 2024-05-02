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
                serializeSequence(state, ((BArray) value), Types.DEFAULT_GLOBAL_SEQ_TAG_HANDLE, depthLevel);
                return;
            } else if (typeTag == TypeTags.MAP_TAG || typeTag == TypeTags.RECORD_TYPE_TAG) {
                serializeMapping(state, ((BMap<BString, Object>) value),
                        Types.DEFAULT_GLOBAL_MAP_TAG_HANDLE, depthLevel);
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
        scalarEvent.setTag(Types.DEFAULT_GLOBAL_STR_TAG_HANDLE);
        state.events.add(scalarEvent);
    }

    private static void serializeSequence(SerializerState state, BArray data, String tag, int depthLevel) {
        if (state.isStream) {
            state.isStream = false;
            for (int i = 0; i < data.size(); i++) {
                serialize(state, data.get(i), depthLevel + 1, tag);
            }
        } else {
            YamlEvent startEvent = new YamlEvent.StartEvent(Types.Collection.SEQUENCE,
                    state.flowStyle, false);
            startEvent.setTag(tag);
            state.events.add(startEvent);

            for (int i = 0; i < data.size(); i++) {
                serialize(state, data.get(i), depthLevel + 1, tag);
            }

            state.events.add(new YamlEvent.EndEvent(Types.Collection.SEQUENCE));
        }
    }

    private static void serializeMapping(SerializerState state, BMap<BString, Object> bMap, String tag,
                                         int depthLevel) {
        state.events.add(new YamlEvent.StartEvent(Types.Collection.MAPPING, state.flowStyle, false));

        BString[] keys = bMap.getKeys();
        for (BString key: keys) {
            serialize(state, key, depthLevel, tag);
            serialize(state, bMap.get(key), depthLevel + 1, tag);
        }

        state.events.add(new YamlEvent.EndEvent(Types.Collection.MAPPING));
    }
}
