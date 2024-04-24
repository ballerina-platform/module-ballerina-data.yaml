package io.ballerina.stdlib.data.yaml.parser;

import io.ballerina.runtime.api.PredefinedTypes;
import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.MapType;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.types.ReferenceType;
import io.ballerina.runtime.api.types.TupleType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.types.UnionType;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BDecimal;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.stdlib.data.yaml.utils.DiagnosticErrorCode;
import io.ballerina.stdlib.data.yaml.utils.DiagnosticLog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class Values {
    private static final List<Integer> TYPE_PRIORITY_ORDER = List.of(
            TypeTags.INT_TAG,
            TypeTags.FLOAT_TAG,
            TypeTags.DECIMAL_TAG,
            TypeTags.NULL_TAG,
            TypeTags.BOOLEAN_TAG,
            TypeTags.JSON_TAG,
            TypeTags.STRING_TAG
    );

    private static final List<Type> BASIC_JSON_MEMBER_TYPES = List.of(
            PredefinedTypes.TYPE_NULL,
            PredefinedTypes.TYPE_BOOLEAN,
            PredefinedTypes.TYPE_INT,
            PredefinedTypes.TYPE_FLOAT,
            PredefinedTypes.TYPE_DECIMAL,
            PredefinedTypes.TYPE_STRING
    );
    private static final UnionType JSON_TYPE_WITH_BASIC_TYPES = TypeCreator.createUnionType(BASIC_JSON_MEMBER_TYPES);

    public static final MapType JSON_MAP_TYPE = TypeCreator.createMapType(PredefinedTypes.TYPE_JSON);
    public static final MapType ANYDATA_MAP_TYPE = TypeCreator.createMapType(PredefinedTypes.TYPE_ANYDATA);
    public static final BString VALUE = StringUtils.fromString("value");
    public static final String FIELD = "$field$.";
    public static final String FIELD_REGEX = "\\$field\\$\\.";
    public static final String NAME = "Name";

    static BMap<BString, Object> initRootMapValue(Type expectedType) {
        return switch (expectedType.getTag()) {
            case TypeTags.RECORD_TYPE_TAG ->
                    ValueCreator.createRecordValue(expectedType.getPackage(), expectedType.getName());
            case TypeTags.MAP_TAG -> ValueCreator.createMapValue((MapType) expectedType);
            case TypeTags.JSON_TAG -> ValueCreator.createMapValue(JSON_MAP_TYPE);
            case TypeTags.ANYDATA_TAG -> ValueCreator.createMapValue(ANYDATA_MAP_TYPE);
            default -> throw DiagnosticLog.error(DiagnosticErrorCode.INVALID_TYPE, expectedType, "map type");
        };
    }

    static BArray initArrayValue(Type expectedType) {
        return switch (expectedType.getTag()) {
            case TypeTags.TUPLE_TAG -> ValueCreator.createTupleValue((TupleType) expectedType);
            case TypeTags.ARRAY_TAG -> ValueCreator.createArrayValue((ArrayType) expectedType);
            case TypeTags.JSON_TAG -> ValueCreator.createArrayValue(PredefinedTypes.TYPE_JSON_ARRAY);
            case TypeTags.ANYDATA_TAG -> ValueCreator.createArrayValue(PredefinedTypes.TYPE_ANYDATA_ARRAY);
            default -> throw DiagnosticLog.error(DiagnosticErrorCode.INVALID_TYPE, expectedType, "list type");
        };
    }

    static Object convertAndUpdateCurrentValueNode(YamlParser.ComposerState sm, BString value, Type type) {
        Object currentJson = sm.currentYamlNode;
        Object convertedValue = convertToExpectedType(value, type);
        if (convertedValue instanceof BError) {
            if (sm.currentField != null) {
                throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_VALUE_FOR_FIELD, value, type,
                        getCurrentFieldPath(sm));
            }
            throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE, type, value);
        }

        Type currentJsonNodeType = TypeUtils.getType(currentJson);
        switch (currentJsonNodeType.getTag()) {
            case TypeTags.MAP_TAG, TypeTags.RECORD_TYPE_TAG -> {
                ((BMap<BString, Object>) currentJson).put(StringUtils.fromString(sm.fieldNames.pop()),
                        convertedValue);
                return currentJson;
            }
            case TypeTags.ARRAY_TAG -> {
                // Handle projection in array.
                ArrayType arrayType = (ArrayType) currentJsonNodeType;
                if (arrayType.getState() == ArrayType.ArrayState.CLOSED &&
                        arrayType.getSize() <= sm.arrayIndexes.peek()) {
                    return currentJson;
                }
                ((BArray) currentJson).add(sm.arrayIndexes.peek(), convertedValue);
                return currentJson;
            }
            case TypeTags.TUPLE_TAG -> {
                ((BArray) currentJson).add(sm.arrayIndexes.peek(), convertedValue);
                return currentJson;
            }
            default -> {
                return convertedValue;
            }
        }
    }

    private static String getCurrentFieldPath(YamlParser.ComposerState sm) {
        Iterator<String> itr = sm.fieldNames.descendingIterator();

        StringBuilder result = new StringBuilder(itr.hasNext() ? itr.next() : "");
        while (itr.hasNext()) {
            result.append(".").append(itr.next());
        }
        return result.toString();
    }

    private static Object convertToExpectedType(BString value, Type type) {
        if (type.getTag() == TypeTags.ANYDATA_TAG) {
            return fromStringWithType(value, PredefinedTypes.TYPE_JSON);
        }
        return fromStringWithType(value, type);
    }

    private static Optional<BMap<BString, Object>> initNewMapValue(YamlParser.ComposerState state, Type expType) {
        if (expType == null) {
            return Optional.empty();
        }
        Type currentType = TypeUtils.getReferredType(expType);

        if (state.currentYamlNode != null) {
            state.nodesStack.push(state.currentYamlNode);
        }

        if (currentType.getTag() == TypeTags.UNION_TAG) {
            currentType = findMappingTypeFromUnionType((UnionType) currentType);
        }

        BMap<BString, Object> nextMapValue;
        switch (currentType.getTag()) {
            case TypeTags.RECORD_TYPE_TAG -> {
                RecordType recordType = (RecordType) currentType;
                nextMapValue = ValueCreator.createRecordValue(expType.getPackage(), expType.getName());
                state.updateExpectedType(recordType.getFields(), recordType.getRestFieldType());
            }
            case TypeTags.MAP_TAG -> {
                nextMapValue = ValueCreator.createMapValue((MapType) currentType);
                state.updateExpectedType(new HashMap<>(), ((MapType) currentType).getConstrainedType());
            }
            case TypeTags.JSON_TAG -> {
                nextMapValue = ValueCreator.createMapValue(JSON_MAP_TYPE);
                state.updateExpectedType(new HashMap<>(), PredefinedTypes.TYPE_JSON);
            }
            case TypeTags.ANYDATA_TAG -> {
                nextMapValue = ValueCreator.createMapValue(ANYDATA_MAP_TYPE);
                state.updateExpectedType(new HashMap<>(), PredefinedTypes.TYPE_JSON);
            }
            default -> throw DiagnosticLog.error(DiagnosticErrorCode.INVALID_TYPE_FOR_FIELD,
                    getCurrentFieldPath(state));
        }

        Object currentJson = state.currentYamlNode;
        int valueTypeTag = TypeUtils.getType(currentJson).getTag();
        if (valueTypeTag == TypeTags.MAP_TAG || valueTypeTag == TypeTags.RECORD_TYPE_TAG) {
            ((BMap<BString, Object>) currentJson).put(StringUtils.fromString(state.fieldNames.pop()), nextMapValue);
        }
        return Optional.of(nextMapValue);
    }

    static Type getMemberType(Type expectedType, int index) {
        if (expectedType == null) {
            return null;
        }

        if (expectedType.getTag() == TypeTags.ARRAY_TAG) {
            return ((ArrayType) expectedType).getElementType();
        } else if (expectedType.getTag() == TypeTags.TUPLE_TAG) {
            TupleType tupleType = (TupleType) expectedType;
            List<Type> tupleTypes = tupleType.getTupleTypes();
            if (tupleTypes.size() < index + 1) {
                return tupleType.getRestType();
            }
            return tupleTypes.get(index);
        }
        return expectedType;
    }

    public static Object fromStringWithType(BString string, Type expType) {
        String value = string.getValue();
        return switch (expType.getTag()) {
            case TypeTags.INT_TAG -> stringToInt(value);
            case TypeTags.FLOAT_TAG -> stringToFloat(value);
            case TypeTags.DECIMAL_TAG -> stringToDecimal(value);
            case TypeTags.STRING_TAG -> string;
            case TypeTags.BOOLEAN_TAG -> stringToBoolean(value);
            case TypeTags.NULL_TAG -> stringToNull(value);
            case TypeTags.UNION_TAG -> stringToUnion(string, (UnionType) expType);
            case TypeTags.JSON_TAG -> stringToUnion(string, JSON_TYPE_WITH_BASIC_TYPES);
            case TypeTags.TYPE_REFERENCED_TYPE_TAG ->
                    fromStringWithType(string, ((ReferenceType) expType).getReferredType());
            default -> returnError(value, expType.toString());
        };
    }

    private static Long stringToInt(String value) throws NumberFormatException {
        return Long.parseLong(value);
    }

    private static Double stringToFloat(String value) throws NumberFormatException {
        if (hasFloatOrDecimalLiteralSuffix(value)) {
            throw new NumberFormatException();
        }
        return Double.parseDouble(value);
    }

    private static BDecimal stringToDecimal(String value) throws NumberFormatException {
        return ValueCreator.createDecimalValue(value);
    }

    private static Object stringToBoolean(String value) throws NumberFormatException {
        if ("true".equalsIgnoreCase(value) || "1".equalsIgnoreCase(value)) {
            return true;
        }

        if ("false".equalsIgnoreCase(value) || "0".equalsIgnoreCase(value)) {
            return false;
        }
        return returnError(value, "boolean");
    }

    private static Object stringToNull(String value) throws NumberFormatException {
        if ("null".equalsIgnoreCase(value) || "()".equalsIgnoreCase(value)) {
            return null;
        }
        return returnError(value, "()");
    }

    private static BError returnError(String string, String expType) {
        return DiagnosticLog.error(DiagnosticErrorCode.CANNOT_CONVERT_TO_EXPECTED_TYPE,
                PredefinedTypes.TYPE_STRING.getName(), string, expType);
    }

    static void updateNextMapValue(YamlParser.ComposerState state) {
        Object currentYamlNode = state.currentYamlNode;
        Type currentYamlNodeType = TypeUtils.getType(currentYamlNode);
        Type expType = state.expectedTypes.peek();
        if (currentYamlNodeType.getTag() == TypeTags.ARRAY_TAG) {
            expType = ((ArrayType) currentYamlNodeType).getElementType();
        }

        Optional<BMap<BString, Object>> nextMap = initNewMapValue(state, expType);
        if (nextMap.isPresent()) {
            state.currentYamlNode = nextMap.get();
        } else {
            // This will restrict from checking the fieldHierarchy.
            state.jsonFieldDepth++;
        }
    }

    static Type findMappingTypeFromUnionType(UnionType type) {
        List<Type> memberTypes = new ArrayList<>(type.getMemberTypes());
        for (Type memType: memberTypes) {
            switch (memType.getTag()) {
                case TypeTags.ANYDATA_TAG, TypeTags.MAP_TAG, TypeTags.RECORD_TYPE_TAG, TypeTags.JSON_TAG -> {
                    return memType;
                }
                default -> {
                }
            }
        }
        return null;
    }

    static void updateNextArrayValue(YamlParser.ComposerState state) {
        state.arrayIndexes.push(0);
        Optional<BArray> nextArray = initNewArrayValue(state);
        nextArray.ifPresent(array -> state.currentYamlNode = array);
    }

    static Optional<BArray> initNewArrayValue(YamlParser.ComposerState state) {
        if (state.expectedTypes.peek() == null) {
            return Optional.empty();
        }

        Object currentYamlNode = state.currentYamlNode;
        BArray nextArrValue = initArrayValue(state.expectedTypes.peek());
        if (currentYamlNode == null) {
            return Optional.ofNullable(nextArrValue);
        }

        state.nodesStack.push(currentYamlNode);
        return Optional.ofNullable(nextArrValue);
    }

    private static Object stringToUnion(BString string, UnionType expType) throws NumberFormatException {
        List<Type> memberTypes = new ArrayList<>(expType.getMemberTypes());
        memberTypes.sort(Comparator.comparingInt(t -> {
            int index = TYPE_PRIORITY_ORDER.indexOf(TypeUtils.getReferredType(t).getTag());
            return index == -1 ? Integer.MAX_VALUE : index;
        }));
        for (Type memberType : memberTypes) {
            try {
                Object result = fromStringWithType(string, memberType);
                if (result instanceof BError) {
                    continue;
                }
                return result;
            } catch (Exception e) {
                // Skip
            }
        }
        return returnError(string.getValue(), expType.toString());
    }

    static void validateListSize(int currentIndex, Type expType) {
        int expLength = 0;
        if (expType == null) {
            return;
        }

        if (expType.getTag() == TypeTags.ARRAY_TAG) {
            expLength = ((ArrayType) expType).getSize();
        } else if (expType.getTag() == TypeTags.TUPLE_TAG) {
            TupleType tupleType = (TupleType) expType;
            expLength = tupleType.getTupleTypes().size();
        }

        if (expLength >= 0 && expLength > currentIndex + 1) {
            throw DiagnosticLog.error(DiagnosticErrorCode.ARRAY_SIZE_MISMATCH);
        }
    }

    private static boolean hasFloatOrDecimalLiteralSuffix(String value) {
        int length = value.length();
        if (length == 0) {
            return false;
        }

        switch (value.charAt(length - 1)) {
            case 'F':
            case 'f':
            case 'D':
            case 'd':
                return true;
            default:
                return false;
        }
    }
}
