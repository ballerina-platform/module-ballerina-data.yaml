package io.ballerina.stdlib.data.yaml.emitter;

import io.ballerina.stdlib.data.yaml.common.Types;
import io.ballerina.stdlib.data.yaml.common.YamlEvent;

public class Utils {

    /**
     * Obtain the topmost event from the event tree.
     *
     * @param state Current emitter state
     * @return The topmost event from the current tree.
     */
    public static YamlEvent getEvent(Emitter.EmitterState state) {
        if (state.events.size() < 1) {
            return new YamlEvent.EndEvent(Types.Collection.STREAM);
        }
        return state.events.remove(0);
    }

    public static String appendTagToValue(boolean tagAsSuffix, String tag, String value) {
        return tagAsSuffix ? value + " " + tag : tag + " " + value;
    }
}
