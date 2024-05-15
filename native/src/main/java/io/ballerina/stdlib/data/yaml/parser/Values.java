package io.ballerina.stdlib.data.yaml.parser;

import io.ballerina.runtime.api.PredefinedTypes;
import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.Field;
import io.ballerina.runtime.api.types.FiniteType;
import io.ballerina.runtime.api.types.IntersectionType;
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
import org.ballerinalang.langlib.value.CloneReadOnly;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Stack;

import static io.ballerina.stdlib.data.yaml.parser.ParserUtils.getAllFieldsInRecord;

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
    public static final Integer BBYTE_MIN_VALUE = 0;
    public static final Integer BBYTE_MAX_VALUE = 255;
    public static final Integer SIGNED32_MAX_VALUE = 2147483647;
    public static final Integer SIGNED32_MIN_VALUE = -2147483648;
    public static final Integer SIGNED16_MAX_VALUE = 32767;
    public static final Integer SIGNED16_MIN_VALUE = -32768;
    public static final Integer SIGNED8_MAX_VALUE = 127;
    public static final Integer SIGNED8_MIN_VALUE = -128;
    public static final Long UNSIGNED32_MAX_VALUE = 4294967295L;
    public static final Integer UNSIGNED16_MAX_VALUE = 65535;
    public static final Integer UNSIGNED8_MAX_VALUE = 255;

    static BMap<BString, Object> initRootMapValue(YamlParser.ComposerState state) {
        Type expectedType = state.expectedTypes.peek();
        state.parserContexts.push(YamlParser.ParserContext.MAP);
        switch (expectedType.getTag()) {
            case TypeTags.RECORD_TYPE_TAG -> {
                return ValueCreator.createRecordValue(expectedType.getPackage(), expectedType.getName());
            }
            case TypeTags.MAP_TAG -> {
                return ValueCreator.createMapValue((MapType) expectedType);
            }
            case TypeTags.JSON_TAG -> {
                return ValueCreator.createMapValue(JSON_MAP_TYPE);
            }
            case TypeTags.ANYDATA_TAG -> {
                return ValueCreator.createMapValue(ANYDATA_MAP_TYPE);
            }
            case TypeTags.UNION_TAG -> {
                state.parserContexts.push(YamlParser.ParserContext.MAP);
                state.unionDepth++;
                state.fieldNameHierarchy.push(new Stack<>());
                return ValueCreator.createMapValue(JSON_MAP_TYPE);
            }
            default -> throw DiagnosticLog.error(DiagnosticErrorCode.INVALID_TYPE, expectedType, "map type");
        }
    }

    static Object initRootArrayValue(YamlParser.ComposerState state) {
        state.parserContexts.push(YamlParser.ParserContext.ARRAY);
        Type expType = state.expectedTypes.peek();
        // In this point we know rhs is json[] or anydata[] hence init index counter.
        if (expType.getTag() == TypeTags.JSON_TAG || expType.getTag() == TypeTags.ANYDATA_TAG
                || expType.getTag() == TypeTags.UNION_TAG) {
            state.arrayIndexes.push(0);
        }
        return initArrayValue(state, expType);
    }

    static BArray initArrayValue(YamlParser.ComposerState state, Type expectedType) {
        switch (expectedType.getTag()) {
            case TypeTags.TUPLE_TAG -> {
                return ValueCreator.createTupleValue((TupleType) expectedType);
            }
            case TypeTags.ARRAY_TAG -> {
                return ValueCreator.createArrayValue((ArrayType) expectedType);
            }
            case TypeTags.JSON_TAG -> {
                return ValueCreator.createArrayValue(PredefinedTypes.TYPE_JSON_ARRAY);
            }
            case TypeTags.ANYDATA_TAG -> {
                return ValueCreator.createArrayValue(PredefinedTypes.TYPE_ANYDATA_ARRAY);
            }
            case TypeTags.UNION_TAG -> {
                state.unionDepth++;
                return ValueCreator.createArrayValue(PredefinedTypes.TYPE_JSON_ARRAY);
            }
            default -> throw DiagnosticLog.error(DiagnosticErrorCode.INVALID_TYPE, expectedType, "list type");
        }
    }

    static void handleFieldName(String jsonFieldName, YamlParser.ComposerState state) {
        if (state.jsonFieldDepth == 0 && state.unionDepth == 0) {
            Field currentField = state.visitedFieldHierarchy.peek().get(jsonFieldName);
            if (currentField == null) {
                currentField = state.fieldHierarchy.peek().remove(jsonFieldName);
            }
            state.currentField = currentField;

            Type fieldType;
            if (currentField == null) {
                fieldType = state.restType.peek();
            } else {
                // Replace modified field name with actual field name.
                jsonFieldName = currentField.getFieldName();
                fieldType = currentField.getFieldType();
                state.visitedFieldHierarchy.peek().put(jsonFieldName, currentField);
            }
            state.expectedTypes.push(fieldType);

            if (!state.allowDataProjection && fieldType == null)  {
                throw DiagnosticLog.error(DiagnosticErrorCode.UNDEFINED_FIELD, jsonFieldName);
            }
        } else if (state.expectedTypes.peek() == null) {
            state.currentField = null;
            state.expectedTypes.push(null);
        }
        state.fieldNameHierarchy.peek().push(jsonFieldName);
    }

    static Object convertAndUpdateCurrentValueNode(YamlParser.ComposerState sm, String value, Type type) {
        Object currentYaml = sm.currentYamlNode;
        Object convertedValue = convertToExpectedType(StringUtils.fromString(value), type);
        if (convertedValue instanceof BError) {
            if (sm.currentField != null) {
                throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_VALUE_FOR_FIELD, value, type,
                        getCurrentFieldPath(sm));
            }
            throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE, type, value);
        }

        return updateCurrentValueNode(sm, currentYaml, convertedValue);
    }

    static Object updateCurrentValueNode(YamlParser.ComposerState sm, Object currentYaml, Object convertedValue) {
        Type currentJsonNodeType = TypeUtils.getType(currentYaml);
        switch (currentJsonNodeType.getTag()) {
            case TypeTags.MAP_TAG, TypeTags.RECORD_TYPE_TAG -> {
                ((BMap<BString, Object>) currentYaml).put(StringUtils.fromString(sm.fieldNameHierarchy.peek().pop()),
                        convertedValue);
                return currentYaml;
            }
            case TypeTags.ARRAY_TAG -> {
                // Handle projection in array.
                ArrayType arrayType = (ArrayType) currentJsonNodeType;
                if (arrayType.getState() == ArrayType.ArrayState.CLOSED &&
                        arrayType.getSize() <= sm.arrayIndexes.peek()) {
                    return currentYaml;
                }
                ((BArray) currentYaml).add(sm.arrayIndexes.peek(), convertedValue);
                return currentYaml;
            }
            case TypeTags.TUPLE_TAG -> {
                ((BArray) currentYaml).add(sm.arrayIndexes.peek(), convertedValue);
                return currentYaml;
            }
            default -> {
                return convertedValue;
            }
        }
    }

    private static String getCurrentFieldPath(YamlParser.ComposerState sm) {
        Iterator<Stack<String>> itr = sm.fieldNameHierarchy.iterator();
        StringBuilder result = new StringBuilder(itr.hasNext() ? itr.next().peek() : "");
        while (itr.hasNext()) {
            result.append(".").append(itr.next().peek());
        }
        return result.toString();
    }

    private static Object convertToExpectedType(BString value, Type type) {
        switch (type.getTag()) {
            case TypeTags.CHAR_STRING_TAG -> {
                if (value.length() != 1) {
                    return DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE, type, value);
                }
                return value;
            }
            case TypeTags.FINITE_TYPE_TAG -> {
                return ((FiniteType) type).getValueSpace().stream()
                        .filter(finiteValue -> !(convertToSingletonValue(value.getValue(), finiteValue)
                                instanceof BError))
                        .findFirst()
                        .orElseGet(() -> DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE, type, value));
            }
            case TypeTags.TYPE_REFERENCED_TYPE_TAG -> {
                return convertToExpectedType(value, TypeUtils.getReferredType(type));
            }
            default -> {
                return fromStringWithType(value, type);
            }
        }
    }

    private static Optional<BMap<BString, Object>> initNewMapValue(YamlParser.ComposerState state, Type expType) {
        YamlParser.ParserContext parentContext = state.parserContexts.peek();
        state.parserContexts.push(YamlParser.ParserContext.MAP);
        if (expType == null) {
            state.fieldNameHierarchy.push(new Stack<>());
            return Optional.empty();
        }
        if (state.currentYamlNode != null) {
            state.nodesStack.push(state.currentYamlNode);
        }
        BMap<BString, Object> nextMapValue = checkTypeAndCreateMappingValue(state, expType, parentContext);
        return Optional.of(nextMapValue);
    }

    static BMap<BString, Object> checkTypeAndCreateMappingValue(YamlParser.ComposerState state, Type expType,
                                                                YamlParser.ParserContext parentContext) {
        Type currentType = TypeUtils.getReferredType(expType);
        BMap<BString, Object> nextMapValue;
        switch (currentType.getTag()) {
            case TypeTags.RECORD_TYPE_TAG -> {
                RecordType recordType = (RecordType) currentType;
                nextMapValue = ValueCreator.createRecordValue(expType.getPackage(), expType.getName());
                state.updateFieldHierarchiesAndRestType(getAllFieldsInRecord(recordType),
                        recordType.getRestFieldType());
            }
            case TypeTags.MAP_TAG -> {
                nextMapValue = ValueCreator.createMapValue((MapType) currentType);
                state.updateFieldHierarchiesAndRestType(new HashMap<>(), ((MapType) currentType).getConstrainedType());
            }
            case TypeTags.JSON_TAG -> {
                nextMapValue = ValueCreator.createMapValue(JSON_MAP_TYPE);
                state.updateFieldHierarchiesAndRestType(new HashMap<>(), currentType);
            }
            case TypeTags.ANYDATA_TAG -> {
                nextMapValue = ValueCreator.createMapValue(ANYDATA_MAP_TYPE);
                state.updateFieldHierarchiesAndRestType(new HashMap<>(), currentType);
            }
            case TypeTags.INTERSECTION_TAG -> {
                Optional<Type> mutableType = getMutableType((IntersectionType) currentType);
                if (mutableType.isEmpty()) {
                    throw DiagnosticLog.error(DiagnosticErrorCode.INVALID_TYPE, currentType, "map type");
                }
                return checkTypeAndCreateMappingValue(state, mutableType.get(), parentContext);
            }
            case TypeTags.UNION_TAG -> {
                nextMapValue = ValueCreator.createMapValue(JSON_MAP_TYPE);
                state.parserContexts.push(YamlParser.ParserContext.MAP);
                state.unionDepth++;
                state.fieldNameHierarchy.push(new Stack<>());
            }
            default -> {
                if (parentContext == YamlParser.ParserContext.ARRAY) {
                    throw DiagnosticLog.error(DiagnosticErrorCode.INVALID_TYPE, currentType, "map type");
                }
                throw DiagnosticLog.error(DiagnosticErrorCode.INVALID_TYPE_FOR_FIELD, getCurrentFieldPath(state));
            }
        }
        return nextMapValue;
    }

    static Optional<Type> getMutableType(IntersectionType intersectionType) {
        for (Type constituentType : intersectionType.getConstituentTypes()) {
            if (constituentType.getTag() == TypeTags.READONLY_TAG) {
                continue;
            }
            return Optional.of(constituentType);
        }
        return Optional.empty();
    }

    static Type getMemberType(Type expectedType, int index, boolean allowDataProjection) {
        if (expectedType == null) {
            return null;
        }

        if (expectedType.getTag() == TypeTags.ARRAY_TAG) {
            ArrayType arrayType = (ArrayType) expectedType;
            if (arrayType.getState() == ArrayType.ArrayState.OPEN
                    || arrayType.getState() == ArrayType.ArrayState.CLOSED &&  index < arrayType.getSize()) {
                return arrayType.getElementType();
            }

            if (!allowDataProjection) {
                throw DiagnosticLog.error(DiagnosticErrorCode.ARRAY_SIZE_MISMATCH);
            }
            return null;
        } else if (expectedType.getTag() == TypeTags.TUPLE_TAG) {
            TupleType tupleType = (TupleType) expectedType;
            List<Type> tupleTypes = tupleType.getTupleTypes();
            if (tupleTypes.size() < index + 1) {
                Type restType = tupleType.getRestType();
                if (restType == null && !allowDataProjection) {
                    throw DiagnosticLog.error(DiagnosticErrorCode.ARRAY_SIZE_MISMATCH);
                }
                return restType;
            }
            return tupleTypes.get(index);
        }
        return expectedType;
    }

    static Type getMemberTypeForStreamsWithUnion(Type expectedType, int index) {
        if (expectedType == null) {
            return null;
        }

        if (expectedType.getTag() == TypeTags.ARRAY_TAG) {
            ArrayType arrayType = (ArrayType) expectedType;
            return arrayType.getElementType();
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
            case TypeTags.BYTE_TAG -> stringToByte(value);
            case TypeTags.SIGNED8_INT_TAG -> stringToSigned8Int(value);
            case TypeTags.SIGNED16_INT_TAG-> stringToSigned16Int(value);
            case TypeTags.SIGNED32_INT_TAG -> stringToSigned32Int(value);
            case TypeTags.UNSIGNED8_INT_TAG -> stringToUnsigned8Int(value);
            case TypeTags.UNSIGNED16_INT_TAG -> stringToUnsigned16Int(value);
            case TypeTags.UNSIGNED32_INT_TAG -> stringToUnsigned32Int(value);
            case TypeTags.FLOAT_TAG -> stringToFloat(value);
            case TypeTags.DECIMAL_TAG -> stringToDecimal(value);
            case TypeTags.CHAR_STRING_TAG -> stringToChar(value);
            case TypeTags.STRING_TAG -> string;
            case TypeTags.BOOLEAN_TAG -> stringToBoolean(value);
            case TypeTags.NULL_TAG -> stringToNull(value);
            case TypeTags.FINITE_TYPE_TAG -> stringToFiniteType(value, (FiniteType) expType);
            case TypeTags.UNION_TAG -> stringToUnion(string, (UnionType) expType);
            case TypeTags.JSON_TAG, TypeTags.ANYDATA_TAG -> stringToUnion(string, JSON_TYPE_WITH_BASIC_TYPES);
            case TypeTags.TYPE_REFERENCED_TYPE_TAG -> fromStringWithType(string,
                    ((ReferenceType) expType).getReferredType());
            case TypeTags.INTERSECTION_TAG -> fromStringWithType(string,
                    ((IntersectionType) expType).getEffectiveType());
            default -> returnError(value, expType.toString());
        };
    }

    private static Object stringToFiniteType(String value, FiniteType finiteType) {
        return finiteType.getValueSpace().stream()
                .filter(finiteValue -> !(convertToSingletonValue(value, finiteValue) instanceof BError))
                .findFirst()
                .orElseGet(() -> returnError(value, finiteType.toString()));
    }

    private static Object convertToSingletonValue(String str, Object singletonValue) {
        String singletonStr = String.valueOf(singletonValue);
        if (str.equals(singletonStr)) {
            BString value = StringUtils.fromString(str);
            Type expType = TypeUtils.getType(singletonValue);
            return fromStringWithType(value, expType);
        } else {
            return returnError(str, singletonStr);
        }
    }

    public static BString convertValueToBString(Object value) {
        if (value instanceof BString) {
            return (BString) value;
        } else if (value instanceof Long || value instanceof String || value instanceof Integer
                || value instanceof BDecimal || value instanceof Double) {
            return StringUtils.fromString(String.valueOf(value));
        }
        throw new RuntimeException("cannot convert to BString");
    }

    private static int stringToByte(String value) throws NumberFormatException {
        int intValue = Integer.parseInt(value);
        if (!isByteLiteral(intValue)) {
            throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE, PredefinedTypes.TYPE_BYTE, value);
        }
        return intValue;
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

    private static long stringToSigned8Int(String value) throws NumberFormatException {
        long intValue = Long.parseLong(value);
        if (!isSigned8LiteralValue(intValue)) {
            throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE, PredefinedTypes.TYPE_INT_SIGNED_8, value);
        }
        return intValue;
    }

    private static long stringToSigned16Int(String value) throws NumberFormatException {
        long intValue = Long.parseLong(value);
        if (!isSigned16LiteralValue(intValue)) {
            throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE, PredefinedTypes.TYPE_INT_SIGNED_16, value);
        }
        return intValue;
    }

    private static long stringToSigned32Int(String value) throws NumberFormatException {
        long intValue = Long.parseLong(value);
        if (!isSigned32LiteralValue(intValue)) {
            throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE, PredefinedTypes.TYPE_INT_SIGNED_32, value);
        }
        return intValue;
    }

    private static long stringToUnsigned8Int(String value) throws NumberFormatException {
        long intValue = Long.parseLong(value);
        if (!isUnsigned8LiteralValue(intValue)) {
            throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE,
                    PredefinedTypes.TYPE_INT_UNSIGNED_8, value);
        }
        return intValue;
    }

    private static long stringToUnsigned16Int(String value) throws NumberFormatException {
        long intValue = Long.parseLong(value);
        if (!isUnsigned16LiteralValue(intValue)) {
            throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE,
                    PredefinedTypes.TYPE_INT_UNSIGNED_16, value);
        }
        return intValue;
    }

    private static long stringToUnsigned32Int(String value) throws NumberFormatException {
        long intValue = Long.parseLong(value);
        if (!isUnsigned32LiteralValue(intValue)) {
            throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE,
                    PredefinedTypes.TYPE_INT_UNSIGNED_32, value);
        }
        return intValue;
    }

    private static Object stringToBoolean(String value) throws NumberFormatException {
        if (value.equals("true")) {
            return true;
        }

        if (value.equals("false")) {
            return false;
        }
        return returnError(value, "boolean");
    }

    private static Object stringToNull(String value) throws NumberFormatException {
        if (value.equals("null")) {
            return null;
        }
        return returnError(value, "()");
    }

    private static BString stringToChar(String value) throws NumberFormatException {
        if (!isCharLiteralValue(value)) {
            throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE,
                    PredefinedTypes.TYPE_STRING_CHAR, value);
        }
        return StringUtils.fromString(value);
    }

    private static BError returnError(String string, String expType) {
        return DiagnosticLog.error(DiagnosticErrorCode.CANNOT_CONVERT_TO_EXPECTED_TYPE,
                PredefinedTypes.TYPE_STRING.getName(), string, expType);
    }

    static void updateNextMapValue(YamlParser.ComposerState state) {
        Object currentYamlNode = state.currentYamlNode;
        Type expType = state.expectedTypes.peek();

        Optional<BMap<BString, Object>> nextMap = initNewMapValue(state, expType);
        if (nextMap.isPresent()) {
            state.currentYamlNode = nextMap.get();
        } else {
            // This will restrict from checking the fieldHierarchy.
            state.jsonFieldDepth++;
        }
    }

    static void updateExpectedType(YamlParser.ComposerState state) {
        if (state.unionDepth > 0) {
            return;
        }
        state.expectedTypes.push(getMemberType(state.expectedTypes.peek(),
                state.arrayIndexes.peek(), state.allowDataProjection));
    }

    static void updateExpectedTypeForStreamDocument(YamlParser.ComposerState state) {
        if (state.unionDepth > 0) {
            return;
        }
        state.expectedTypes.push(getMemberTypeForStreamsWithUnion(state.expectedTypes.peek(),
                state.arrayIndexes.peek()));
    }

    static void updateNextMapValueBasedOnExpType(YamlParser.ComposerState state) {
        updateNextMapValue(state);
    }

    static void updateNextArrayValueBasedOnExpType(YamlParser.ComposerState state) {
        updateNextArrayValue(state);
    }

    static void updateNextArrayValue(YamlParser.ComposerState state) {
        state.arrayIndexes.push(0);
        Optional<BArray> nextArray = initNewArrayValue(state);
        nextArray.ifPresent(array -> state.currentYamlNode = array);
    }

    static Optional<BArray> initNewArrayValue(YamlParser.ComposerState state) {
        state.parserContexts.push(YamlParser.ParserContext.ARRAY);
        if (state.expectedTypes.peek() == null) {
            return Optional.empty();
        }

        Object currentYamlNode = state.currentYamlNode;
        Type expType = TypeUtils.getReferredType(state.expectedTypes.peek());
        if (expType.getTag() == TypeTags.INTERSECTION_TAG) {
            Optional<Type> type = getMutableType((IntersectionType) expType);
            if (type.isEmpty()) {
                throw DiagnosticLog.error(DiagnosticErrorCode.INVALID_TYPE, expType, "array type");
            }
            expType = type.get();
        }
        BArray nextArrValue = initArrayValue(state, expType);
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

    public static Object constructReadOnlyValue(Object value) {
        return CloneReadOnly.cloneReadOnly(value);
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

    private static boolean isByteLiteral(long longValue) {
        return (longValue >= BBYTE_MIN_VALUE && longValue <= BBYTE_MAX_VALUE);
    }

    private static boolean isSigned32LiteralValue(Long longObject) {
        return (longObject >= SIGNED32_MIN_VALUE && longObject <= SIGNED32_MAX_VALUE);
    }

    private static boolean isSigned16LiteralValue(Long longObject) {
        return (longObject.intValue() >= SIGNED16_MIN_VALUE && longObject.intValue() <= SIGNED16_MAX_VALUE);
    }

    private static boolean isSigned8LiteralValue(Long longObject) {
        return (longObject.intValue() >= SIGNED8_MIN_VALUE && longObject.intValue() <= SIGNED8_MAX_VALUE);
    }

    private static boolean isUnsigned32LiteralValue(Long longObject) {
        return (longObject >= 0 && longObject <= UNSIGNED32_MAX_VALUE);
    }

    private static boolean isUnsigned16LiteralValue(Long longObject) {
        return (longObject.intValue() >= 0 && longObject.intValue() <= UNSIGNED16_MAX_VALUE);
    }

    private static boolean isUnsigned8LiteralValue(Long longObject) {
        return (longObject.intValue() >= 0 && longObject.intValue() <= UNSIGNED8_MAX_VALUE);
    }

    private static boolean isCharLiteralValue(String value) {
        return value.codePoints().count() == 1;
    }
}
