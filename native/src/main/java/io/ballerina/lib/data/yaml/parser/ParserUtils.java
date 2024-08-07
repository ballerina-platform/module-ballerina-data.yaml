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

package io.ballerina.lib.data.yaml.parser;

import io.ballerina.lib.data.yaml.common.YamlEvent;
import io.ballerina.runtime.api.types.Field;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

import java.util.HashMap;
import java.util.Map;

import static io.ballerina.lib.data.yaml.common.Types.Collection.STREAM;

/**
 * This class will hold utility functions used in parser.
 *
 * @since 0.1.0
 */
public class ParserUtils {

    public static final String FIELD = "$field$.";
    public static final String FIELD_REGEX = "\\$field\\$\\.";
    public static final String NAME = "Name";
    public static final BString VALUE = StringUtils.fromString("value");

    private ParserUtils() {
    }
    public static Map<String, Field> getAllFieldsInRecord(RecordType recordType) {
        BMap<BString, Object> annotations = recordType.getAnnotations();
        Map<String, String> modifiedNames = new HashMap<>();
        for (BString annotationKey : annotations.getKeys()) {
            String keyStr = annotationKey.getValue();
            if (!keyStr.contains(FIELD)) {
                continue;
            }
            String fieldName = keyStr.split(FIELD_REGEX)[1];
            Map<BString, Object> fieldAnnotation = (Map<BString, Object>) annotations.get(annotationKey);
            modifiedNames.put(fieldName, getModifiedName(fieldAnnotation, fieldName));
        }

        Map<String, Field> fields = new HashMap<>();
        Map<String, Field> recordFields = recordType.getFields();
        for (String key : recordFields.keySet()) {
            String fieldName = modifiedNames.getOrDefault(key, key);
            fields.put(fieldName, recordFields.get(key));
        }
        return fields;
    }

    public static String getModifiedName(Map<BString, Object> fieldAnnotation, String fieldName) {
        for (BString key : fieldAnnotation.keySet()) {
            if (key.getValue().endsWith(NAME)) {
                return ((Map<?, ?>) fieldAnnotation.get(key)).get(VALUE).toString();
            }
        }
        return fieldName;
    }

    public static boolean isStreamEndEvent(YamlEvent event) {
        return event.getKind() == YamlEvent.EventKind.END_EVENT && ((YamlEvent.EndEvent) event).getEndType() == STREAM;
    }

    public enum ParserOption {
        DEFAULT,
        EXPECT_MAP_KEY,
        EXPECT_MAP_VALUE,
        EXPECT_SEQUENCE_ENTRY,
        EXPECT_SEQUENCE_VALUE
    }
}
