package io.ballerina.lib.data.yaml.utils;

import io.ballerina.runtime.api.PredefinedTypes;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BValue;

public class TypeUtils {

    public static Type getType(Object value) {
        if (value == null) {
            return PredefinedTypes.TYPE_NULL;
        } else {
            if (value instanceof Number) {
                if (value instanceof Long) {
                    return PredefinedTypes.TYPE_INT;
                }

                if (value instanceof Double) {
                    return PredefinedTypes.TYPE_FLOAT;
                }

                if (value instanceof Integer || value instanceof Byte) {
                    return PredefinedTypes.TYPE_BYTE;
                }
            } else {
                if (value instanceof BString) {
                    return PredefinedTypes.TYPE_STRING;
                }

                if (value instanceof Boolean) {
                    return PredefinedTypes.TYPE_BOOLEAN;
                }

                if (value instanceof BObject) {
                    return ((BObject) value).getOriginalType();
                }
            }

            return ((BValue) value).getType();
        }
    }
}
