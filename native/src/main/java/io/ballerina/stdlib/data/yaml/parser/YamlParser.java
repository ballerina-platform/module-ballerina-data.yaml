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

package io.ballerina.stdlib.data.yaml.parser;

import io.ballerina.runtime.api.PredefinedTypes;
import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.flags.SymbolFlags;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.Field;
import io.ballerina.runtime.api.types.IntersectionType;
import io.ballerina.runtime.api.types.MapType;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.stdlib.data.yaml.common.Types;
import io.ballerina.stdlib.data.yaml.common.Types.Collection;
import io.ballerina.stdlib.data.yaml.common.YamlEvent;
import io.ballerina.stdlib.data.yaml.lexer.IndentUtils;
import io.ballerina.stdlib.data.yaml.lexer.LexerState;
import io.ballerina.stdlib.data.yaml.lexer.Token;
import io.ballerina.stdlib.data.yaml.lexer.YamlLexer;
import io.ballerina.stdlib.data.yaml.utils.Constants;
import io.ballerina.stdlib.data.yaml.utils.DiagnosticErrorCode;
import io.ballerina.stdlib.data.yaml.utils.DiagnosticLog;
import io.ballerina.stdlib.data.yaml.utils.Error;
import io.ballerina.stdlib.data.yaml.utils.JsonTraverse;
import io.ballerina.stdlib.data.yaml.utils.OptionsUtils;
import io.ballerina.stdlib.data.yaml.utils.TagResolutionUtils;
import org.ballerinalang.langlib.value.CloneReadOnly;

import java.io.Reader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import static io.ballerina.stdlib.data.yaml.common.Types.Collection.SEQUENCE;
import static io.ballerina.stdlib.data.yaml.common.Types.Collection.STREAM;
import static io.ballerina.stdlib.data.yaml.common.Types.DocumentType.ANY_DOCUMENT;
import static io.ballerina.stdlib.data.yaml.common.Types.DocumentType.BARE_DOCUMENT;
import static io.ballerina.stdlib.data.yaml.common.Types.DocumentType.DIRECTIVE_DOCUMENT;
import static io.ballerina.stdlib.data.yaml.lexer.Token.TokenType.ANCHOR;
import static io.ballerina.stdlib.data.yaml.lexer.Token.TokenType.COMMENT;
import static io.ballerina.stdlib.data.yaml.lexer.Token.TokenType.DIRECTIVE;
import static io.ballerina.stdlib.data.yaml.lexer.Token.TokenType.DIRECTIVE_MARKER;
import static io.ballerina.stdlib.data.yaml.lexer.Token.TokenType.DOUBLE_QUOTE_DELIMITER;
import static io.ballerina.stdlib.data.yaml.lexer.Token.TokenType.EMPTY_LINE;
import static io.ballerina.stdlib.data.yaml.lexer.Token.TokenType.EOL;
import static io.ballerina.stdlib.data.yaml.lexer.Token.TokenType.FOLDED;
import static io.ballerina.stdlib.data.yaml.lexer.Token.TokenType.MAPPING_END;
import static io.ballerina.stdlib.data.yaml.lexer.Token.TokenType.MAPPING_KEY;
import static io.ballerina.stdlib.data.yaml.lexer.Token.TokenType.MAPPING_VALUE;
import static io.ballerina.stdlib.data.yaml.lexer.Token.TokenType.PLANAR_CHAR;
import static io.ballerina.stdlib.data.yaml.lexer.Token.TokenType.SEPARATION_IN_LINE;
import static io.ballerina.stdlib.data.yaml.lexer.Token.TokenType.SEPARATOR;
import static io.ballerina.stdlib.data.yaml.lexer.Token.TokenType.SEQUENCE_END;
import static io.ballerina.stdlib.data.yaml.lexer.Token.TokenType.SEQUENCE_ENTRY;
import static io.ballerina.stdlib.data.yaml.lexer.Token.TokenType.SINGLE_QUOTE_DELIMITER;
import static io.ballerina.stdlib.data.yaml.lexer.Token.TokenType.TAG;
import static io.ballerina.stdlib.data.yaml.parser.Directive.reservedDirective;
import static io.ballerina.stdlib.data.yaml.parser.Directive.tagDirective;
import static io.ballerina.stdlib.data.yaml.parser.Directive.yamlDirective;
import static io.ballerina.stdlib.data.yaml.parser.ParserUtils.ParserOption.EXPECT_MAP_KEY;
import static io.ballerina.stdlib.data.yaml.parser.ParserUtils.ParserOption.EXPECT_MAP_VALUE;
import static io.ballerina.stdlib.data.yaml.parser.ParserUtils.ParserOption.EXPECT_SEQUENCE_ENTRY;
import static io.ballerina.stdlib.data.yaml.parser.ParserUtils.ParserOption.EXPECT_SEQUENCE_VALUE;
import static io.ballerina.stdlib.data.yaml.parser.ParserUtils.getAllFieldsInRecord;
import static io.ballerina.stdlib.data.yaml.utils.Constants.DEFAULT_GLOBAL_TAG_HANDLE;
import static io.ballerina.stdlib.data.yaml.utils.Constants.DEFAULT_LOCAL_TAG_HANDLE;

/**
 * Core parsing of YAML strings.
 *
 * @since 0.1.0
 */
public class YamlParser {

    public static final Map<String, String> DEFAULT_TAG_HANDLES = Map.of("!", DEFAULT_LOCAL_TAG_HANDLE,
            "!!", DEFAULT_GLOBAL_TAG_HANDLE);

    public static class ComposerState {
        private final ParserState parserState;
        private final Map<String, Object> anchorBuffer = new HashMap<>();
        Object currentYamlNode;
        Field currentField;
        Deque<Object> nodesStack = new ArrayDeque<>();
        Stack<Map<String, Field>> fieldHierarchy = new Stack<>();
        Stack<Map<String, Field>> visitedFieldHierarchy = new Stack<>();
        Stack<Type> restType = new Stack<>();
        Stack<Type> expectedTypes = new Stack<>();
        Stack<Stack<String>> fieldNameHierarchy = new Stack<>();
        int jsonFieldDepth = 0;
        Stack<Integer> arrayIndexes = new Stack<>();
        Stack<ParserContext> parserContexts = new Stack<>();
        YamlEvent terminatedDocEvent = null;
        int unionDepth = 0;
        boolean rootValueInitialized = false;
        final Types.YAMLSchema schema;
        final boolean allowAnchorRedefinition;
        final boolean allowMapEntryRedefinition;
        final boolean allowDataProjection;
        final boolean nilAsOptionalField;
        final boolean absentAsNilableType;
        boolean expectedTypeIsReadonly = false;
        boolean isStream = false;

        public ComposerState(ParserState parserState, OptionsUtils.ReadConfig readConfig) {
            this.parserState = parserState;
            this.schema = readConfig.schema();
            this.allowAnchorRedefinition = readConfig.allowAnchorRedefinition();
            this.allowMapEntryRedefinition = readConfig.allowMapEntryRedefinition();
            this.allowDataProjection = readConfig.allowDataProjection();
            this.nilAsOptionalField = readConfig.nilAsOptionalField();
            this.absentAsNilableType = readConfig.absentAsNilableType();
            this.isStream = readConfig.isStream();
        }

        public int getLine() {
            return parserState.getLine();
        }

        public int getColumn() {
            return parserState.getColumn();
        }

        private void updateIndexOfArrayElement() {
            int arrayIndex = arrayIndexes.pop();
            arrayIndexes.push(arrayIndex + 1);
        }

        public void updateFieldHierarchiesAndRestType(Map<String, Field> fields, Type restType) {
            this.fieldHierarchy.push(new HashMap<>(fields));
            this.visitedFieldHierarchy.push(new HashMap<>());
            this.restType.push(restType);
            this.fieldNameHierarchy.push(new Stack<>());
        }

        private void checkUnionAndFinalizeArrayObject() {
            arrayIndexes.pop();
            if (unionDepth > 0) {
                finalizeObject();
                return;
            }
            finalizeArrayObjectAndRemoveExpectedType();
        }

        public void checkUnionAndFinalizeNonArrayObject() {
            if (unionDepth > 0) {
                fieldNameHierarchy.pop();
                finalizeObject();
                return;
            }
            finalizeNonArrayObjectAndRemoveExpectedType();
        }

        private void finalizeArrayObjectAndRemoveExpectedType() {
            finalizeObject();
            expectedTypes.pop();
        }

        public void finalizeNonArrayObjectAndRemoveExpectedType() {
            finalizeNonArrayObject();
            expectedTypes.pop();
        }

        private void finalizeNonArrayObject() {
            if (jsonFieldDepth > 0) {
                jsonFieldDepth--;
            }

            if (!expectedTypes.isEmpty() && expectedTypes.peek() == null) {
                parserContexts.pop();
                fieldNameHierarchy.pop();
                return;
            }

            Map<String, Field> remainingFields = fieldHierarchy.pop();
            visitedFieldHierarchy.pop();
            fieldNameHierarchy.pop();
            restType.pop();
            for (Field field : remainingFields.values()) {
                if (SymbolFlags.isFlagOn(field.getFlags(), SymbolFlags.REQUIRED)) {
                    throw DiagnosticLog.error(DiagnosticErrorCode.REQUIRED_FIELD_NOT_PRESENT, field.getFieldName());
                }
            }
            finalizeObject();
        }

        public Object verifyAndConvertToUnion(Object json) {
            if (unionDepth > 0) {
                return json;
            }
            BMap<BString, Object> options = ValueCreator.createMapValue();
            BMap<BString, Object> allowDataProjectionMap = ValueCreator.createMapValue();
            if (!allowDataProjection) {
                options.put(Constants.ALLOW_DATA_PROJECTION, false);
            } else {
                allowDataProjectionMap.put(Constants.NIL_AS_OPTIONAL_FIELD, nilAsOptionalField);
                allowDataProjectionMap.put(Constants.ABSENT_AS_NILABLE_TYPE, absentAsNilableType);
                options.put(Constants.ALLOW_DATA_PROJECTION, allowDataProjectionMap);
            }

            return JsonTraverse.traverse(json, options, expectedTypes.peek());
        }

        private void finalizeObject() {
            parserContexts.pop();

            if (unionDepth > 0) {
                unionDepth--;
                currentYamlNode = verifyAndConvertToUnion(currentYamlNode);
            }

            // Skip the value and continue to next state.
            if (!expectedTypes.isEmpty() && expectedTypes.peek() == null) {
                return;
            }

            if (nodesStack.isEmpty()) {
                return;
            }

            Object parentNode = nodesStack.pop();
            Type parentNodeType = TypeUtils.getType(parentNode);
            int parentNodeTypeTag = TypeUtils.getReferredType(parentNodeType).getTag();
            if (parentNodeTypeTag == TypeTags.RECORD_TYPE_TAG || parentNodeTypeTag == TypeTags.MAP_TAG) {
                Type expType = TypeUtils.getReferredType(expectedTypes.peek());
                if (expType.getTag() == TypeTags.INTERSECTION_TAG) {
                    currentYamlNode = CloneReadOnly.cloneReadOnly(currentYamlNode);
                }
                ((BMap<BString, Object>) parentNode).put(StringUtils.fromString(fieldNameHierarchy.peek().pop()),
                            currentYamlNode);
                currentYamlNode = parentNode;
                return;
            }

            switch (TypeUtils.getType(parentNode).getTag()) {
                case TypeTags.ARRAY_TAG -> {
                    // Handle projection in array.
                    ArrayType arrayType = (ArrayType) parentNodeType;
                    if (arrayType.getState() == ArrayType.ArrayState.CLOSED &&
                            arrayType.getSize() <= arrayIndexes.peek()) {
                        break;
                    }
                    Type expType = TypeUtils.getReferredType(expectedTypes.peek());
                    if (expType.getTag() == TypeTags.INTERSECTION_TAG) {
                        currentYamlNode = CloneReadOnly.cloneReadOnly(currentYamlNode);
                    }
                    ((BArray) parentNode).add(arrayIndexes.peek(), currentYamlNode);
                }
                case TypeTags.TUPLE_TAG -> {
                    Type expType = TypeUtils.getReferredType(expectedTypes.peek());
                    if (expType.getTag() == TypeTags.INTERSECTION_TAG) {
                        currentYamlNode = CloneReadOnly.cloneReadOnly(currentYamlNode);
                    }
                    ((BArray) parentNode).add(arrayIndexes.peek(), currentYamlNode);
                }
                default -> {
                }
            }

            currentYamlNode = parentNode;
        }

        public void finalizeAnchorValueObject() {
            // Skip the value and continue to next state.
            if (!expectedTypes.isEmpty() && expectedTypes.peek() == null) {
                return;
            }

            if (nodesStack.isEmpty()) {
                return;
            }

            Object parentNode = nodesStack.pop();
            Type parentNodeType = TypeUtils.getType(parentNode);
            int parentNodeTypeTag = TypeUtils.getReferredType(parentNodeType).getTag();
            if (parentNodeTypeTag == TypeTags.RECORD_TYPE_TAG || parentNodeTypeTag == TypeTags.MAP_TAG) {
                ((BMap<BString, Object>) parentNode).put(StringUtils.fromString(fieldNameHierarchy.peek().pop()),
                        currentYamlNode);
                currentYamlNode = parentNode;
                return;
            }

            switch (TypeUtils.getType(parentNode).getTag()) {
                case TypeTags.ARRAY_TAG -> {
                    // Handle projection in array.
                    ArrayType arrayType = (ArrayType) parentNodeType;
                    if (arrayType.getState() == ArrayType.ArrayState.CLOSED &&
                            arrayType.getSize() <= arrayIndexes.peek()) {
                        break;
                    }
                    ((BArray) parentNode).add(arrayIndexes.peek(), currentYamlNode);
                }
                case TypeTags.TUPLE_TAG -> {
                    ((BArray) parentNode).add(arrayIndexes.peek(), currentYamlNode);
                }
                default -> {
                }
            }

            currentYamlNode = parentNode;
        }

        public void handleExpectedType(Type type) {
            switch (type.getTag()) {
                case TypeTags.RECORD_TYPE_TAG -> {
                    RecordType recordType = (RecordType) type;
                    expectedTypes.add(recordType);
                    updateFieldHierarchiesAndRestType(getAllFieldsInRecord(recordType), recordType.getRestFieldType());
                }
                case TypeTags.ARRAY_TAG -> {
                    expectedTypes.add(type);
                    arrayIndexes.push(0);
                    if (isStream) {
                        Type elementType = TypeUtils.getReferredType(((ArrayType) type).getElementType());
                        if (elementType.getTag() == TypeTags.UNION_TAG) {
                            expectedTypes.add(elementType);
                        }
                    }
                }
                case TypeTags.TUPLE_TAG -> {
                    expectedTypes.add(type);
                    arrayIndexes.push(0);
                }
                case TypeTags.NULL_TAG, TypeTags.BOOLEAN_TAG, TypeTags.INT_TAG, TypeTags.BYTE_TAG,
                        TypeTags.SIGNED8_INT_TAG, TypeTags.SIGNED16_INT_TAG, TypeTags.SIGNED32_INT_TAG,
                        TypeTags.UNSIGNED8_INT_TAG, TypeTags.UNSIGNED16_INT_TAG, TypeTags.UNSIGNED32_INT_TAG,
                        TypeTags.FLOAT_TAG, TypeTags.DECIMAL_TAG, TypeTags.CHAR_STRING_TAG, TypeTags.STRING_TAG,
                        TypeTags.FINITE_TYPE_TAG, TypeTags.UNION_TAG ->
                        expectedTypes.push(type);
                case TypeTags.JSON_TAG, TypeTags.ANYDATA_TAG -> {
                    expectedTypes.push(type);
                    updateFieldHierarchiesAndRestType(new HashMap<>(), type);
                }
                case TypeTags.MAP_TAG -> {
                    expectedTypes.push(type);
                    updateFieldHierarchiesAndRestType(new HashMap<>(), ((MapType) type).getConstrainedType());
                }
                case TypeTags.INTERSECTION_TAG -> {
                    Type effectiveType = ((IntersectionType) type).getEffectiveType();
                    if (!SymbolFlags.isFlagOn(SymbolFlags.READONLY, effectiveType.getFlags())) {
                        throw DiagnosticLog.error(DiagnosticErrorCode.UNSUPPORTED_TYPE, type);
                    }

                    for (Type constituentType : ((IntersectionType) type).getConstituentTypes()) {
                        if (constituentType.getTag() == TypeTags.READONLY_TAG) {
                            continue;
                        }
                        handleExpectedType(TypeUtils.getReferredType(constituentType));
                        expectedTypeIsReadonly = true;
                        break;
                    }
                }
                case TypeTags.TYPE_REFERENCED_TYPE_TAG -> handleExpectedType(TypeUtils.getReferredType(type));
                default -> throw DiagnosticLog.error(DiagnosticErrorCode.UNSUPPORTED_TYPE, type);
            }
        }
    }

    public enum ParserContext {
        MAP,
        ARRAY
    }

    /**
     * Parses the contents in the given {@link Reader} and returns subtype of anydata value.
     *
     * @param reader reader which contains the YAML content
     * @param options represent the options that can be used to modify the behaviour of conversion
     * @param expectedType Shape of the YAML content required
     * @return subtype of anydata value
     * @throws BError for any parsing error
     */
    public static Object compose(Reader reader, BMap<BString, Object> options, Type expectedType) throws BError {
        OptionsUtils.ReadConfig readConfig = OptionsUtils.resolveReadConfig(options);
        ComposerState composerState = new ComposerState(new ParserState(reader), readConfig);
        composerState.handleExpectedType(expectedType);
        try {
            return readConfig.isStream() ? composeStream(composerState) : composeDocument(composerState);
        } catch (Error.YamlParserException e) {
             return DiagnosticLog.error(DiagnosticErrorCode.YAML_PARSER_EXCEPTION,
                     e.getMessage(), e.getLine(), e.getColumn());
        }
    }

    private static Object composeDocument(ComposerState state) throws Error.YamlParserException {
        return composeDocument(state, null);
    }

    private static Object composeDocument(ComposerState state, YamlEvent eventParam) throws Error.YamlParserException {
        YamlEvent event = eventParam == null ? handleEvent(state, ANY_DOCUMENT) : eventParam;

        // Ignore the start document marker for explicit documents
        if (event.getKind() == YamlEvent.EventKind.DOCUMENT_MARKER_EVENT &&
                ((YamlEvent.DocumentMarkerEvent) event).isExplicit()) {
            event = handleEvent(state, ANY_DOCUMENT);
        }

        Object output = composeNode(state, event, false);

        // Return an error if there is another root event
        event = handleEvent(state);
        if (event.getKind() == YamlEvent.EventKind.END_EVENT && ((YamlEvent.EndEvent) event).getEndType() == STREAM) {
            return handleOutput(state, output);
        }
        if (event.getKind() == YamlEvent.EventKind.DOCUMENT_MARKER_EVENT) {
            state.terminatedDocEvent = event;
            return handleOutput(state, output);
        }
        throw new Error.YamlParserException("there can only be one root event to a document", state.getLine(),
                state.getColumn());
    }

    private static Object composeStream(ComposerState state) throws Error.YamlParserException {
        YamlEvent event = handleEvent(state, ANY_DOCUMENT);

        state.currentYamlNode = Values.initRootArrayValue(state);
        state.rootValueInitialized = true;

        int prevUnionDepth = state.unionDepth;
        boolean hasUnionElementMember = false;
        if (state.expectedTypes.size() > 1) {
            hasUnionElementMember = true;
        } else {
            state.unionDepth = 0;
        }

        // Iterate all the documents
        while (!(event.getKind() == YamlEvent.EventKind.END_EVENT
                && ((YamlEvent.EndEvent) event).getEndType() == STREAM)) {
            if (hasUnionElementMember) {
                Values.updateExpectedTypeForStreamDocument(state);
            } else {
                Values.updateExpectedType(state);
            }
            composeDocument(state, event);
            state.updateIndexOfArrayElement();

            if (state.terminatedDocEvent != null &&
                    state.terminatedDocEvent.getKind() == YamlEvent.EventKind.DOCUMENT_MARKER_EVENT) {
                // Explicit document markers should be passed to the composeDocument
                if (((YamlEvent.DocumentMarkerEvent) state.terminatedDocEvent).isExplicit()) {
                    event = state.terminatedDocEvent;
                    state.terminatedDocEvent = null;
                } else { // All the trailing document end markers should be ignored
                    state.terminatedDocEvent = null;
                    event = handleEvent(state, ANY_DOCUMENT);

                    while (event.getKind() == YamlEvent.EventKind.DOCUMENT_MARKER_EVENT
                            && !(((YamlEvent.DocumentMarkerEvent) event).isExplicit())) {
                        event = handleEvent(state, ANY_DOCUMENT);
                    }
                }
            } else { // Obtain the stream end event
                event = handleEvent(state, ANY_DOCUMENT);
            }
        }

        state.unionDepth = prevUnionDepth;
        if (state.unionDepth == 1) {
            state.unionDepth--;
            if (hasUnionElementMember) {
                state.expectedTypes.pop();
            }
            return handleOutput(state, state.verifyAndConvertToUnion(state.currentYamlNode));
        }
        return handleOutput(state, state.currentYamlNode);
    }

    private static Object composeNode(ComposerState state, YamlEvent event, boolean mapOrSequenceScalar)
            throws Error.YamlParserException {

        // Check for aliases
        YamlEvent.EventKind eventKind = event.getKind();

        if (eventKind == YamlEvent.EventKind.ALIAS_EVENT) {
            YamlEvent.AliasEvent aliasEvent = (YamlEvent.AliasEvent) event;
            Object alias = state.anchorBuffer.get(aliasEvent.getAlias());
            if (alias == null) {
                throw new Error.YamlParserException("anchor does not exist", state.getLine(), state.getColumn());
            }
            return alias;
        }

        // Ignore end events
        if (eventKind == YamlEvent.EventKind.END_EVENT) {
            return null;
        }

        // Ignore document markers
        if (eventKind == YamlEvent.EventKind.DOCUMENT_MARKER_EVENT) {
            state.terminatedDocEvent = event;
            return null;
        }

        Object output;
        // Check for collections
        if (eventKind == YamlEvent.EventKind.START_EVENT) {
            YamlEvent.StartEvent startEvent = (YamlEvent.StartEvent) event;

            switch (startEvent.getStartType()) {
                case SEQUENCE -> {
                    output = castData(state, composeSequence(state, startEvent.isFlowStyle()),
                            Types.FailSafeSchema.SEQUENCE, event.getTag());
                }
                case MAPPING -> {
                    output = castData(state, composeMapping(state, startEvent.isFlowStyle(), startEvent.isImplicit()),
                            Types.FailSafeSchema.MAPPING, event.getTag());
                }
                default -> {
                    throw new Error.YamlParserException("only sequence and mapping are allowed as node start events",
                            state.getLine(), state.getColumn());
                }
            }
            checkAnchor(state, event, output);
            return state.currentYamlNode;
        }

        YamlEvent.ScalarEvent scalarEvent = (YamlEvent.ScalarEvent) event;

        // Check for scalar
        output =  castData(state, scalarEvent.getValue(), Types.FailSafeSchema.STRING, event.getTag());
        checkAnchor(state, event, output);
        if (mapOrSequenceScalar) {
            return output;
        }
        processValue(state, scalarEvent.getValue());
        return state.currentYamlNode;
    }

    private static Object handleOutput(ComposerState state, Object output) {
        return state.expectedTypeIsReadonly ? Values.constructReadOnlyValue(output) : output;
    }

    private static void processValue(ComposerState state, String value) {
        Type expType;
        if (state.unionDepth > 0) {
            expType = PredefinedTypes.TYPE_JSON;
        } else {
            expType = state.expectedTypes.pop();
            if (expType == null) {
                return;
            }
        }
        state.currentYamlNode = Values.convertAndUpdateCurrentValueNode(state, value, expType);
    }

    public static Object composeSequence(YamlParser.ComposerState state, boolean flowStyle)
            throws Error.YamlParserException {
        boolean firstElement = true;
        if (!state.rootValueInitialized) {
            state.currentYamlNode = Values.initRootArrayValue(state);
            state.rootValueInitialized = true;
        } else {
            Values.updateNextArrayValueBasedOnExpType(state);
        }

        YamlEvent event = handleEvent(state, EXPECT_SEQUENCE_VALUE);

        // Iterate until the end sequence event is detected
        boolean terminated = false;
        while (!terminated) {
            if (event.getKind() == YamlEvent.EventKind.DOCUMENT_MARKER_EVENT) {
                state.terminatedDocEvent = event;
                if (!flowStyle) {
                    break;
                }
                throw new Error.YamlParserException("unexpected event", state.getLine(), state.getColumn());
            }

            if (event.getKind() == YamlEvent.EventKind.END_EVENT) {
                YamlEvent.EndEvent endEvent = (YamlEvent.EndEvent) event;

                switch (endEvent.getEndType()) {
                    case MAPPING -> throw new Error.YamlParserException("unexpected event",
                            state.getLine(), state.getColumn());
                    case STREAM -> {
                        if (!flowStyle) {
                            terminated = true;
                            break;
                        }
                        throw new Error.YamlParserException("unexpected event", state.getLine(), state.getColumn());
                    }
                    case SEQUENCE -> {
                        terminated = true;
                    }
                }
            }
            if (!terminated) {
                if (!firstElement) {
                    state.updateIndexOfArrayElement();
                }
                firstElement = false;
                Values.updateExpectedType(state);
                Object value = composeNode(state, event, true);
                if (value instanceof String scalarValue) {
                    processValue(state, scalarValue);
                } else if (event.getKind() == YamlEvent.EventKind.ALIAS_EVENT) {
                    state.nodesStack.push(state.currentYamlNode);
                    state.currentYamlNode = state.verifyAndConvertToUnion(value);
                    state.finalizeAnchorValueObject();
                    state.expectedTypes.pop();
                } else if (value == null || value instanceof Double
                        || value instanceof Long || value instanceof Boolean) {
                    state.currentYamlNode = Values.updateCurrentValueNode(state, state.currentYamlNode, value);
                }
                event = handleEvent(state, EXPECT_SEQUENCE_ENTRY);
            }
        }

        Object tmpCurrentYaml = state.currentYamlNode;
        state.checkUnionAndFinalizeArrayObject();
        return tmpCurrentYaml;
    }

    public static Object composeMapping(ComposerState state, boolean flowStyle, boolean implicitMapping)
            throws Error.YamlParserException {
        if (!state.rootValueInitialized) {
            state.currentYamlNode = Values.initRootMapValue(state);
            state.rootValueInitialized = true;
        } else {
            Values.updateNextMapValueBasedOnExpType(state);
        }
        Set<String> keys = new HashSet<>();
        YamlEvent event = handleEvent(state, EXPECT_MAP_KEY);

        // Iterate until an end event is detected
        boolean terminated = false;
        while (!terminated) {
            if (event.getKind() == YamlEvent.EventKind.DOCUMENT_MARKER_EVENT) {
                state.terminatedDocEvent = event;
                if (!flowStyle) {
                    break;
                }
                throw new Error.YamlParserException("unexpected event", state.getLine(), state.getColumn());
            }

            if (event.getKind() == YamlEvent.EventKind.END_EVENT) {
                YamlEvent.EndEvent endEvent = (YamlEvent.EndEvent) event;
                switch (endEvent.getEndType()) {
                    case MAPPING -> terminated = true;
                    case SEQUENCE -> throw new Error.YamlParserException("unexpected event",
                            state.getLine(), state.getColumn());
                    default -> {
                        if (!flowStyle) {
                            terminated = true;
                            break;
                        }
                        throw new Error.YamlParserException("unexpected event", state.getLine(), state.getColumn());
                    }
                }
                if (terminated) {
                    break;
                }
            }

            // Cannot have a nested block mapping if a value is assigned
            if (event.getKind() == YamlEvent.EventKind.START_EVENT
                    && !((YamlEvent.StartEvent) event).isFlowStyle()) {
                throw new RuntimeException("Cannot have nested mapping under a key-pair that is already assigned");
            }

            // Compose the key
            String key = (String) composeNode(state, event, true);

            if (!state.allowMapEntryRedefinition && !keys.add(key)) {
                throw new Error.YamlParserException("cannot have duplicate map entries for '${key.toString()}",
                        state.getLine(), state.getColumn());
            }
            Values.handleFieldName(key, state);

            // Compose the value
            event = handleEvent(state, EXPECT_MAP_VALUE);

            // Check for mapping end events
            if (event.getKind() == YamlEvent.EventKind.END_EVENT) {
                YamlEvent.EndEvent endEvent = (YamlEvent.EndEvent) event;
                switch (endEvent.getEndType()) {
                    case MAPPING -> {
                        terminated = true;
                    }
                    case SEQUENCE -> throw new Error.YamlParserException("unexpected event error",
                            state.getLine(), state.getColumn());
                    default -> {
                        if (!flowStyle) {
                            terminated = true;
                            break;
                        }
                        throw new Error.YamlParserException("unexpected event error",
                                state.getLine(), state.getColumn());
                    }
                }
                if (terminated) {
                    break;
                }
            } else {
                Object value = composeNode(state, event, true);
                if (value instanceof String scalarValue) {
                    Type expType;
                    if (state.unionDepth > 0) {
                        expType = PredefinedTypes.TYPE_JSON;
                        state.currentYamlNode = Values.convertAndUpdateCurrentValueNode(state,
                                scalarValue, expType);
                    } else {
                        expType = state.expectedTypes.pop();
                        if (expType == null && state.currentField == null) {
                            state.fieldNameHierarchy.peek().pop();
                        } else if (expType == null) {
                            break;
                        } else if (state.jsonFieldDepth > 0 || state.currentField != null) {
                            state.currentYamlNode = Values.convertAndUpdateCurrentValueNode(state,
                                    scalarValue, expType);
                        } else if (state.restType.peek() != null) {
                            try {
                                state.currentYamlNode = Values.convertAndUpdateCurrentValueNode(state,
                                        scalarValue, expType);
                                // this element will be ignored in projection
                            } catch (BError ignored) { }
                        }
                    }
                } else if (event.getKind() == YamlEvent.EventKind.ALIAS_EVENT) {
                    state.nodesStack.push(state.currentYamlNode);
                    state.currentYamlNode = state.verifyAndConvertToUnion(value);
                    state.finalizeAnchorValueObject();
                    state.expectedTypes.pop();
                } else if (value == null || value instanceof Double
                        || value instanceof Long || value instanceof Boolean) {
                    state.currentYamlNode = Values.updateCurrentValueNode(state, state.currentYamlNode, value);
                }
            }

            // Terminate after single key-value pair if implicit mapping flag is set.
            if (implicitMapping) {
                break;
            }

            event = handleEvent(state, EXPECT_MAP_KEY);
        }

        Object tmpCurrentYaml = state.currentYamlNode;
        state.checkUnionAndFinalizeNonArrayObject();
        return tmpCurrentYaml;
    }


    /**
     * Update the alias dictionary for the given alias.
     *
     * @param state - Current composer state
     * @param event - The event representing the alias name
     * @param assignedValue - Anchored value to the alias
     */
    public static void checkAnchor(ComposerState state, YamlEvent event, Object assignedValue)
            throws Error.YamlParserException {

        if (event.getAnchor() != null) {
            if (state.anchorBuffer.containsKey(event.getAnchor()) && !state.allowAnchorRedefinition) {
                throw new Error.YamlParserException("duplicate anchor definition", state.getLine(), state.getColumn());
            }
            state.anchorBuffer.put(event.getAnchor(), assignedValue);
        }
    }

    public static Object castData(ComposerState state, Object data, Types.FailSafeSchema kind, String tag)
            throws Error.YamlParserException {
        // Check for explicit keys
        if (tag != null) {
            // Check for the tags in the YAML failsafe schema
            if (tag.equals(DEFAULT_GLOBAL_TAG_HANDLE + "str")) {
                if (kind == Types.FailSafeSchema.STRING) {
                    return data.toString();
                }
            }

            if (tag.equals(DEFAULT_GLOBAL_TAG_HANDLE + "seq")) {
                if (kind == Types.FailSafeSchema.SEQUENCE) {
                    return data;
                }
            }

            if (tag.equals(DEFAULT_GLOBAL_TAG_HANDLE + "map")) {
                if (kind == Types.FailSafeSchema.MAPPING) {
                    return data;
                }
            }

            if (tag.equals(DEFAULT_GLOBAL_TAG_HANDLE + "int")) {
                if (state.schema == Types.YAMLSchema.JSON_SCHEMA) {
                    return TagResolutionUtils.constructSimpleInt(data.toString(), state);
                } else if (state.schema == Types.YAMLSchema.CORE_SCHEMA) {
                    return TagResolutionUtils.constructInt(data.toString(), state);
                }
            }

            if (tag.equals(DEFAULT_GLOBAL_TAG_HANDLE + "float")) {
                if (state.schema == Types.YAMLSchema.JSON_SCHEMA) {
                    return TagResolutionUtils.constructSimpleFloat(data.toString(), state);
                } else if (state.schema == Types.YAMLSchema.CORE_SCHEMA) {
                    return TagResolutionUtils.constructFloat(data.toString(), state);
                }
            }

            if (tag.equals(DEFAULT_GLOBAL_TAG_HANDLE + "bool")) {
                if (state.schema == Types.YAMLSchema.JSON_SCHEMA) {
                    return TagResolutionUtils.constructSimpleBool(data.toString(), state);
                } else if (state.schema == Types.YAMLSchema.CORE_SCHEMA) {
                    return TagResolutionUtils.constructBool(data.toString(), state);
                }
            }

            if (tag.equals(DEFAULT_GLOBAL_TAG_HANDLE + "null")) {
                if (state.schema == Types.YAMLSchema.JSON_SCHEMA) {
                    return TagResolutionUtils.constructSimpleNull(data.toString(), state);
                } else if (state.schema == Types.YAMLSchema.CORE_SCHEMA) {
                    return TagResolutionUtils.constructNull(data.toString(), state);
                }
            }

            throw new Error.YamlParserException("tag schema not supported", state.getLine(), state.getColumn());
        }
        return data;
    }


    /**
     * Obtain an event for the for a set of tokens.
     *
     * @param state - Current parser state
     * @return - Parsed event
     */
    private static YamlEvent handleEvent(ComposerState state) throws Error.YamlParserException {
        if (state.terminatedDocEvent != null &&
                state.terminatedDocEvent.getKind() == YamlEvent.EventKind.DOCUMENT_MARKER_EVENT) {
            return state.terminatedDocEvent;
        }
        return parse(state.parserState, ParserUtils.ParserOption.DEFAULT, BARE_DOCUMENT);
    }

    /**
     * Obtain an event for the for a set of tokens.
     *
     * @param state - Current parser state
     * @param docType - Document type to be parsed
     * @return - Parsed event
     */
    private static YamlEvent handleEvent(ComposerState state, Types.DocumentType docType)
            throws Error.YamlParserException {
        if (state.terminatedDocEvent != null &&
                state.terminatedDocEvent.getKind() == YamlEvent.EventKind.DOCUMENT_MARKER_EVENT) {
            return state.terminatedDocEvent;
        }
        return parse(state.parserState, ParserUtils.ParserOption.DEFAULT, docType);
    }

    /**
     * Obtain an event for the for a set of tokens.
     *
     * @param state - Current parser state
     * @param option - Expected values inside a mapping collection
     * @return - Parsed event
     */
    private static YamlEvent handleEvent(ComposerState state, ParserUtils.ParserOption option)
            throws Error.YamlParserException {
        if (state.terminatedDocEvent != null &&
                state.terminatedDocEvent.getKind() == YamlEvent.EventKind.DOCUMENT_MARKER_EVENT) {
            return state.terminatedDocEvent;
        }
        return parse(state.parserState, option, BARE_DOCUMENT);
    }

    /**
     * Obtain an event for the for a set of tokens.
     *
     * @param state - Current parser state
     * @param option - Expected values inside a mapping collection
     * @param docType - Document type to be parsed
     * @return - Parsed event
     */
    private static YamlEvent parse(ParserState state, ParserUtils.ParserOption option,
                                     Types.DocumentType docType) throws Error.YamlParserException {
        // Empty the event buffer before getting new tokens
        final List<YamlEvent> eventBuffer = state.getEventBuffer();

        if (!eventBuffer.isEmpty()) {
            return eventBuffer.remove(0);
        }
        state.updateLexerState(LexerState.LEXER_START_STATE);
        if (!state.isExplicitDoc()) {
            getNextToken(state);
        }

        // Ignore the whitespace at the head
        if (state.getCurrentToken().getType() == SEPARATION_IN_LINE) {
            getNextToken(state);
        }

        Token.TokenType currentTokenType = state.getCurrentToken().getType();
        // Set the next line if the end of line is detected
        if (currentTokenType == EOL || currentTokenType == EMPTY_LINE || currentTokenType == COMMENT) {

            LexerState lexerState = state.getLexerState();
            if (lexerState.isEndOfStream())  {
                if (docType == DIRECTIVE_DOCUMENT) {
                    throw new Error.YamlParserException("invalid document", state.getLine(), state.getColumn());
                }
                return new YamlEvent.EndEvent(Collection.STREAM);
            }
            state.initLexer();
            return parse(state, option, docType);
        }

        // Only directive tokens are allowed in directive document
        if (currentTokenType != DIRECTIVE && currentTokenType != DIRECTIVE_MARKER) {
            if (docType == DIRECTIVE_DOCUMENT) {
                throw new Error.YamlParserException("'${state.currentToken.token}' " +
                        "is not allowed in a directive document", state.getLine(), state.getColumn());
            }
        }

        switch (currentTokenType) {
            case DIRECTIVE -> {
                // Directives are not allowed in bare documents
                if (docType == BARE_DOCUMENT) {
                    throw new Error.YamlParserException("directives are not allowed in a bare document",
                            state.getLine(), state.getColumn());
                }

                switch (state.getCurrentToken().getValue()) {
                    case "YAML" -> yamlDirective(state);
                    case "TAG" -> tagDirective(state);
                    default -> reservedDirective(state);
                }
                getNextToken(state, List.of(SEPARATION_IN_LINE, EOL));
                return parse(state, ParserUtils.ParserOption.DEFAULT, DIRECTIVE_DOCUMENT);
            }
            case DOCUMENT_MARKER, DIRECTIVE_MARKER -> {
                boolean explicit = state.getCurrentToken().getType() == DIRECTIVE_MARKER;
                state.setExplicitDoc(explicit);

                getNextToken(state, List.of(SEPARATION_IN_LINE, EOL, COMMENT));
                state.getLexerState().resetState();

                if (!explicit) {
                    state.setYamlVersion(null);
                    state.setCustomTagHandles(new HashMap<>());
                }

                if (state.getCurrentToken().getType() == SEPARATION_IN_LINE) {
                    getNextToken(state, true);

                    Token bufferedToken = state.getBufferedToken();
                    Token.TokenType bufferedTokenType = bufferedToken.getType();

                    // There cannot be nodes next to the document marker.
                    if (bufferedTokenType != EOL && bufferedTokenType != COMMENT && !explicit) {
                        throw new Error.YamlParserException("'${state.tokenBuffer.token}' token cannot " +
                                "start in the same line as the document marker", state.getLine(), state.getColumn());
                    }

                    // Block collection nodes cannot be next to the directive marker.
                    if (explicit && (bufferedTokenType == PLANAR_CHAR && bufferedToken.getIndentation() != null
                            || bufferedTokenType == SEQUENCE_ENTRY)) {
                        throw new Error.YamlParserException("'${state.tokenBuffer.token}' token cannot start " +
                                "in the same line as the directive marker", state.getLine(), state.getColumn());
                    }
                }
                return new YamlEvent.DocumentMarkerEvent(explicit);
            }
            case DOUBLE_QUOTE_DELIMITER, SINGLE_QUOTE_DELIMITER, PLANAR_CHAR, ALIAS -> {
                return appendData(state, option, true);
            }
            case TAG, TAG_HANDLE, ANCHOR -> {
                return nodeComplete(state, option);
            }
            case MAPPING_VALUE -> { // Empty node as the key
                if (state.getLexerState().isFlowCollection()) {
                    if (option == EXPECT_SEQUENCE_ENTRY || option == EXPECT_SEQUENCE_VALUE) {
                        state.getEventBuffer().add(new YamlEvent.ScalarEvent());
                        return new YamlEvent.StartEvent(Collection.MAPPING, false, true);
                    }
                    return new YamlEvent.ScalarEvent();
                } else {
                    IndentUtils.Indentation indentation = state.getCurrentToken().getIndentation();
                    separate(state);
                    switch (indentation.change()) {
                        case INDENT_INCREASE -> { // Increase in indent
                            state.getEventBuffer().add(new YamlEvent.ScalarEvent());
                            return new YamlEvent.StartEvent(Collection.MAPPING);
                        }
                        case INDENT_NO_CHANGE -> { // Same indent
                            return new YamlEvent.ScalarEvent();
                        }
                        case INDENT_DECREASE -> { // Decrease in indent

                            for (Collection collection: indentation.collection()) {
                                state.getEventBuffer().add(new YamlEvent.EndEvent(collection));
                            }
                            if (option == EXPECT_MAP_VALUE) {
                                state.getEventBuffer().add(new YamlEvent.ScalarEvent());
                            }
                            return state.getEventBuffer().remove(0);
                        }
                    }
                }
            }
            case SEPARATOR -> { // Empty node as the value in flow mappings
                if (option == EXPECT_MAP_VALUE) { // Check for empty values in flow mappings
                    return new YamlEvent.ScalarEvent();
                }
            }
            case MAPPING_KEY -> { // Explicit key
                state.setExplicitKey(true);
                state.setLastExplicitKeyLine(state.getLineIndex());
                return appendData(state, option);
            }
            case SEQUENCE_ENTRY -> {
                if (state.getLexerState().isFlowCollection()) {
                    throw new Error.YamlParserException("cannot have block sequence under flow collection",
                            state.getLine(), state.getColumn());
                }
                if (state.isExpectBlockSequenceValue()) {
                    throw new Error.YamlParserException("cannot have nested sequence for a defined value",
                            state.getLine(), state.getColumn());
                }

                switch (state.getCurrentToken().getIndentation().change()) {
                    case INDENT_INCREASE -> { // Increase in indent
                       return new YamlEvent.StartEvent(SEQUENCE);
                    }
                    case INDENT_NO_CHANGE -> { // Same indent
                        YamlEvent event = parse(state, EXPECT_SEQUENCE_VALUE, docType);
                        if (option == EXPECT_SEQUENCE_VALUE) {
                            state.getEventBuffer().add(event);
                            return new YamlEvent.ScalarEvent();
                        }
                        return event;
                    }
                    case INDENT_DECREASE -> { // Decrease in indent
                        for (Collection collection: state.getCurrentToken().getIndentation().collection()) {
                            state.getEventBuffer().add(new YamlEvent.EndEvent(collection));
                        }
                        return state.getEventBuffer().remove(0);
                    }
                }
            }
            case MAPPING_START -> {
                return new YamlEvent.StartEvent(Collection.MAPPING, true, false);
            }
            case SEQUENCE_START -> {
                return new YamlEvent.StartEvent(SEQUENCE, true, false);
            }
            case SEQUENCE_END -> {
                if (state.getLexerState().isFlowCollection()) {
                    separate(state);
                    Token.TokenType bufferedTokenType = state.getBufferedToken().getType();
                    if (bufferedTokenType == SEPARATOR) {
                        getNextToken(state);
                    } else if (bufferedTokenType != MAPPING_END && bufferedTokenType != SEQUENCE_END) {
                        throw new Error.YamlParserException("unexpected token error",
                                state.getLine(), state.getColumn());
                    }
                }
                return new YamlEvent.EndEvent(SEQUENCE);
            }
            case MAPPING_END -> {
                if (option == EXPECT_MAP_VALUE) {
                    state.getEventBuffer().add(new YamlEvent.EndEvent(Collection.MAPPING));
                    return new YamlEvent.ScalarEvent();
                }
                if (state.getLexerState().isFlowCollection()) {
                    separate(state);
                    Token.TokenType bufferedTokenType = state.getBufferedToken().getType();
                    if (bufferedTokenType == SEPARATOR) {
                        getNextToken(state);
                    } else if (bufferedTokenType != MAPPING_END && bufferedTokenType != SEQUENCE_END) {
                        throw new Error.YamlParserException("unexpected token error",
                                state.getLine(), state.getColumn());
                    }
                }
                return new YamlEvent.EndEvent(Collection.MAPPING);
            }
            case LITERAL, FOLDED -> {
                state.updateLexerState(LexerState.LEXER_LITERAL);
                return appendData(state, option, true);
            }
        }

        throw new Error.YamlParserException("`Invalid token '${state.currentToken.token}' " +
                "as the first for generating an event", state.getLine(), state.getColumn());
    }

    /** Verifies the grammar production for separation between nodes.
     *
     * @param state - Current parser state
     */
    private static void separate(ParserState state) throws Error.YamlParserException {
        state.updateLexerState(LexerState.LEXER_START_STATE);
        getNextToken(state, true);

        Token.TokenType bufferedTokenType = state.getBufferedToken().getType();
        // Skip the check when either end-of-line or separate-in-line is not detected.
        if (!(bufferedTokenType == EOL || bufferedTokenType == SEPARATION_IN_LINE || bufferedTokenType == COMMENT)) {
            return;
        }

        // Consider the separate for the latter contexts
        getNextToken(state);

        if (state.getCurrentToken().getType() == SEPARATION_IN_LINE) {
            // Check for s-b comment
            getNextToken(state, true);
            bufferedTokenType = state.getBufferedToken().getType();
            if (bufferedTokenType != EOL && bufferedTokenType != COMMENT) {
                return;
            }
            getNextToken(state);
        }

        // For the rest of the contexts, check either separation in line or comment lines
        Token.TokenType currentTokenType = state.getCurrentToken().getType();
        while (currentTokenType == EOL || currentTokenType == EMPTY_LINE || currentTokenType == COMMENT) {
            try {
                state.initLexer();
            } catch (Exception ex) {
                return;
            }

            getNextToken(state, true);

            switch (state.getBufferedToken().getType()) {
                case EOL, EMPTY_LINE, COMMENT -> { // Check for multi-lines
                    getNextToken(state);
                }
                case SEPARATION_IN_LINE -> { // Check for l-comment
                    getNextToken(state);
                    getNextToken(state, true);
                    bufferedTokenType = state.getBufferedToken().getType();
                    if (bufferedTokenType != EOL && bufferedTokenType != COMMENT) {
                        return;
                    }
                    getNextToken(state);
                }
                default -> {
                    return;
                }
            }
            currentTokenType = state.getCurrentToken().getType();
        }
    }

    private static YamlEvent nodeComplete(ParserState state, ParserUtils.ParserOption option)
            throws Error.YamlParserException {
        return nodeComplete(state, option, new TagStructure());
    }

    private static YamlEvent nodeComplete(ParserState state, ParserUtils.ParserOption option,
                                          TagStructure definedProperties) throws Error.YamlParserException {
        TagStructure tagStructure = new TagStructure();
        state.setTagPropertiesInLine(true);

        switch (state.getCurrentToken().getType()) {
            case TAG_HANDLE -> {
                String tagHandle = state.getCurrentToken().getValue();

                // Obtain the tagPrefix associated with the tag handle
                state.getLexerState().updateLexerState(LexerState.LEXER_NODE_PROPERTY);
                getNextToken(state, List.of(TAG));
                String tagPrefix = state.getCurrentToken().getValue();
                tagStructure.tag = generateCompleteTagName(state, tagHandle, tagPrefix);

                // Check if there is a separate
                separate(state);

                // Obtain the anchor if there exists
                tagStructure.anchor = nodeAnchor(state);
            }
            case TAG -> {
                // Obtain the tagPrefix name
                tagStructure.tag = state.getCurrentToken().getValue();

                // There must be a separate after the tagPrefix
                separate(state);

                // Obtain the anchor if there exists
                tagStructure.anchor = nodeAnchor(state);
            }
            case ANCHOR -> {
                // Obtain the anchor name
                tagStructure.anchor = state.getCurrentToken().getValue();

                // Check if there is a separate
                separate(state);

                // Obtain the tag if there exists
                TagDetails tagDetails = nodeTag(state);

                // Construct the complete tag
                if (tagDetails.tagPrefix != null) {
                    tagStructure.tag = tagDetails.tagHandle == null ? tagDetails.tagPrefix :
                            generateCompleteTagName(state, tagDetails.tagHandle, tagDetails.tagPrefix);
                }
            }
        }
        return appendData(state, option, false, tagStructure, definedProperties);
    }

    private static TagDetails nodeTag(ParserState state) throws Error.YamlParserException {
        String tagPrefix = null;
        String tagHandle = null;
        switch (state.getBufferedToken().getType()) {
            case TAG -> {
                getNextToken(state);
                tagPrefix = state.getCurrentToken().getValue();
                separate(state);
            }
            case TAG_HANDLE -> {
                getNextToken(state);
                tagHandle = state.getCurrentToken().getValue();

                state.getLexerState().updateLexerState(LexerState.LEXER_NODE_PROPERTY);
                getNextToken(state, List.of(TAG));
                tagPrefix = state.getCurrentToken().getValue();
                separate(state);
            }
        }
        return new TagDetails(tagPrefix, tagHandle);
    }

    record TagDetails(String tagPrefix, String tagHandle) {
    }


    private static String nodeAnchor(ParserState state) throws Error.YamlParserException {
        String anchor = null;
        if (state.getBufferedToken().getType() == ANCHOR) {
            getNextToken(state);
            anchor = state.getCurrentToken().getValue();
            separate(state);
        }
        return anchor;
    }

    /**
     * Convert the shorthand tag to the complete tag by mapping the tag handle.
     * @param state - Current parser state
     * @param tagHandle - Tag handle of the event
     * @param tagPrefix - Tag prefix of the event
     * @return - The complete tag name of the event
     */
    private static String generateCompleteTagName(ParserState state, String tagHandle, String tagPrefix)
            throws Error.YamlParserException {
        String tagHandleName;
        // Check if the tag handle is defined in the custom tags.
        if (state.getCustomTagHandles().containsKey(tagHandle)) {
            tagHandleName = state.getCustomTagHandles().get(tagHandle);
        } else { // Else, check if the tag handle is in the default tags.
            if (DEFAULT_TAG_HANDLES.containsKey(tagHandle)) {
                tagHandleName = DEFAULT_TAG_HANDLES.get(tagHandle);
            } else {
                throw new Error.YamlParserException("tag handle is not defined", state.getLine(), state.getColumn());
            }
        }
        return tagHandleName + tagPrefix;
    }

    /**
     * Merge tag structure with the respective data.
     *
     * @param state - Current parser state
     * @param option - Selected parser option
     * @return - The constructed scalar or start event
     */
    private static YamlEvent appendData(ParserState state, ParserUtils.ParserOption option)
            throws Error.YamlParserException {
        return appendData(state, option, false, new TagStructure(), null);
    }

    private static YamlEvent appendData(ParserState state, ParserUtils.ParserOption option, boolean peeked)
            throws Error.YamlParserException {
        return appendData(state, option, peeked, new TagStructure(), null);
    }

    /**
     * Merge tag structure with the respective data.
     *
     * @param state - Current parser state
     * @param option - Selected parser option
     * @param peeked If the expected token is already in the state
     * @param tagStructure - Constructed tag structure if exists
     * @param definedProperties - Tag properties defined by the previous node
     * @return - The constructed scalar or start event
     */
    private static YamlEvent appendData(ParserState state, ParserUtils.ParserOption option, boolean peeked,
                                          TagStructure tagStructure, TagStructure definedProperties)
            throws Error.YamlParserException {

        state.setExpectBlockSequenceValue(true);
        YamlEvent buffer = null;

        // Check for nested explicit keys
        if (!peeked) {
            getNextToken(state, true);
            if (state.getBufferedToken().getType() == MAPPING_KEY) {
                state.setExplicitKey(true);
                getNextToken(state);
            }
        }
        boolean explicitKey = state.isExplicitKey();

        if (option == EXPECT_MAP_VALUE && state.getCurrentToken().getType() == MAPPING_KEY) {
            buffer = new YamlEvent.ScalarEvent();
        }

        IndentUtils.Indentation indentation = null;
        if (state.isExplicitKey()) {
            indentation = state.getCurrentToken().getIndentation();
            separate(state);
        }

        state.updateLexerState(LexerState.LEXER_START_STATE);

        if (state.getLastExplicitKeyLine() == state.getLineIndex() && !state.getLexerState().isFlowCollection()
                && option == EXPECT_MAP_KEY) {
            throw new Error.YamlParserException("cannot have a scalar next to a block key-value pair",
                    state.getLine(), state.getColumn());
        }

        YamlEvent event = content(state, peeked, state.isExplicitKey(), tagStructure);
        boolean isAlias = event.getKind() == YamlEvent.EventKind.ALIAS_EVENT;

        state.setExplicitKey(false);
        if (!explicitKey) {
            indentation = state.getCurrentToken().getIndentation();
        }

        // The tokens described in the indentation.tokens belong to the second node.
        TagStructure newNodeTagStructure = new TagStructure();
        TagStructure currentNodeTagStructure = new TagStructure();
        if (indentation != null) {
            switch (indentation.tokens().size()) {
                case 0 -> {
                    newNodeTagStructure = tagStructure;
                }
                case 1 -> {
                    switch (indentation.tokens().get(0)) {
                        case ANCHOR -> {
                            if (isAlias && tagStructure.anchor != null) {
                                throw new Error.YamlParserException("an alias node cannot have an anchor",
                                        state.getLine(), state.getColumn());
                            }
                            newNodeTagStructure.tag = tagStructure.tag;
                            currentNodeTagStructure.anchor = tagStructure.anchor;
                        }
                        case TAG -> {
                            if (isAlias && tagStructure.tag != null) {
                                throw new Error.YamlParserException("an alias node cannot have a tag",
                                        state.getLine(), state.getColumn());
                            }
                            newNodeTagStructure.anchor = tagStructure.anchor;
                            currentNodeTagStructure.tag = tagStructure.tag;
                        }
                    }
                }
                case 2 -> {
                    if (isAlias && (tagStructure.anchor != null || tagStructure.tag != null)) {
                        throw new Error.YamlParserException("an alias node cannot have tag properties",
                                state.getLine(), state.getColumn());
                    }
                    currentNodeTagStructure = tagStructure;
                }
            }
        } else {
            if (isAlias && (tagStructure.anchor != null || tagStructure.tag != null)) {
                throw new Error.YamlParserException("an alias node cannot have tag properties",
                        state.getLine(), state.getColumn());
            }
            currentNodeTagStructure = tagStructure;
        }

        // Check if the current node is a key
        boolean isJsonKey = state.getLexerState().isJsonKey();

        // Ignore the whitespace and lines if there is any
        Token.TokenType currentTokenType = state.getCurrentToken().getType();
        if (currentTokenType != MAPPING_VALUE && currentTokenType != SEPARATOR) {
            separate(state);
        }
        getNextToken(state, true);

        // Check if the next token is a mapping value or
        Token.TokenType bufferedTokenType = state.getBufferedToken().getType();
        if (bufferedTokenType == MAPPING_VALUE || bufferedTokenType == SEPARATOR) {
            getNextToken(state);
        }

        state.getLexerState().setJsonKey(false);

        // If there are no whitespace, and the current token is ","
        if (state.getCurrentToken().getType() == SEPARATOR) {
            if (!state.getLexerState().isFlowCollection()) {
                throw new Error.YamlParserException("',' are only allowed in flow collections",
                        state.getLine(), state.getColumn());
            }
            separate(state);
            if (option == EXPECT_MAP_KEY) {
                state.getEventBuffer().add(new YamlEvent.ScalarEvent());
            }
        } else if (state.getCurrentToken().getType() == MAPPING_VALUE) {
            // If there are no whitespace, and the current token is ':'
            if (state.getLastKeyLine() == state.getLineIndex() && !state.getLexerState().isFlowCollection()) {
                throw new Error.YamlParserException("two block mapping keys cannot be defined in the same line",
                        state.getLine(), state.getColumn());
            }

            // In a block scalar, if there is a mapping key as in the same line as a mapping value,
            // then that mapping value does not correspond to the mapping key. the mapping value forms a
            // new mapping pair which represents the explicit key.
            if (state.getLastExplicitKeyLine() == state.getLineIndex() && !state.getLexerState().isFlowCollection()) {
                throw new Error.YamlParserException("mappings are not allowed as keys for explicit keys",
                        state.getLine(), state.getColumn());
            }
            state.setLastKeyLine(state.getLineIndex());

            if (state.isExplicitDoc()) {
                throw new Error.YamlParserException("'${lexer:PLANAR_CHAR}' token cannot " +
                        "start in the same line as the directive marker", state.getLine(), state.getColumn());
            }

            separate(state);
            if (state.isEmptyKey() && (option == EXPECT_MAP_VALUE || option == EXPECT_SEQUENCE_VALUE)) {
                state.setEmptyKey(false);
                state.getEventBuffer().add(new YamlEvent.ScalarEvent());
            } else if (option == EXPECT_MAP_VALUE) {
                    buffer = constructEvent(new YamlEvent.ScalarEvent(), newNodeTagStructure);
            } else if (option == EXPECT_SEQUENCE_ENTRY || option == EXPECT_SEQUENCE_VALUE
                        && state.getLexerState().isFlowCollection()) {
                    buffer = new YamlEvent.StartEvent(Collection.MAPPING, false, true);
            }
        } else {
            // There is already tag properties defined and the value is not a key
            if (definedProperties != null) {
                if (definedProperties.anchor != null && tagStructure.anchor != null) {
                    throw new Error.YamlParserException("only one anchor is allowed for a node",
                            state.getLine(), state.getColumn());
                }
                if (definedProperties.tag != null && tagStructure.tag != null) {
                    throw new Error.YamlParserException("only one tag is allowed for a node",
                            state.getLine(), state.getColumn());
                }
            }

            if (option == EXPECT_MAP_KEY && !explicitKey) {
                throw new Error.YamlParserException("expected a key for the block mapping",
                        state.getLine(), state.getColumn());
            }

            if (explicitKey) {
                IndentUtils.Indentation peekedIndentation = state.getBufferedToken().getIndentation();
                if (peekedIndentation != null
                        && peekedIndentation.change() == IndentUtils.Indentation.IndentationChange.INDENT_INCREASE
                        && state.getBufferedToken().getType() != MAPPING_KEY) {
                    throw new Error.YamlParserException("invalid explicit key", state.getLine(), state.getColumn());
                }
            }
        }

        if (indentation != null && !state.isIndentationProcessed()) {
            int collectionSize = indentation.collection().size();
            switch (indentation.change()) {
                case INDENT_INCREASE -> { // Increased
                    // Block sequence
                    if (event.getKind() == YamlEvent.EventKind.START_EVENT
                            && ((YamlEvent.StartEvent) event).getStartType() == SEQUENCE) {
                        return constructEvent(
                                new YamlEvent.StartEvent(indentation.collection().remove(collectionSize - 1)),
                                tagStructure);
                    }
                    // Block mapping
                    buffer = constructEvent(
                            new YamlEvent.StartEvent(indentation.collection().remove(collectionSize - 1)),
                            newNodeTagStructure);
                }
                case INDENT_DECREASE -> { // Decreased
                    buffer = new YamlEvent.EndEvent(indentation.collection().remove(0));
                    for (Collection collection: indentation.collection()) {
                        state.getEventBuffer().add(new YamlEvent.EndEvent(collection));
                    }
                }
            }
        }
        state.setIndentationProcessed(false);

        if (isJsonKey && currentNodeTagStructure.tag == null) {
            currentNodeTagStructure.tag = DEFAULT_GLOBAL_TAG_HANDLE + "str";
        }
        event = constructEvent(event, isAlias ? null : currentNodeTagStructure);

        if (buffer == null) {
            return event;
        }
        if (explicitKey) {
            state.getEventBuffer().add(0, event);
        } else {
            state.getEventBuffer().add(event);
        }

        return buffer;
    }

    private static YamlEvent constructEvent(YamlEvent yamlEvent, TagStructure newNodeTagStructure) {
        YamlEvent event = yamlEvent.clone();
        if (newNodeTagStructure != null) {
            event.setAnchor(newNodeTagStructure.anchor);
            event.setTag(newNodeTagStructure.tag);
        }
        return event;
    }

    /** Extract the data for the given node.
     *
     * @param state - Current parser state
     * @param peeked - If the expected token is already in the state
     * @param explicitKey - Whether the current node is an explicit key
     * @param tagStructure - Tag structure of the current node
     * @return Parser Event
     */
    private static YamlEvent content(ParserState state, boolean peeked, boolean explicitKey,
                                       TagStructure tagStructure) throws Error.YamlParserException {

        if (!peeked) {
            separate(state);
            getNextToken(state);
        }

        // Check for flow and block nodes
        switch (state.getCurrentToken().getType()) {
            case SINGLE_QUOTE_DELIMITER -> {
                state.getLexerState().setJsonKey(true);
                String value = singleQuoteScalar(state);
                checkEmptyKey(state);
                return new YamlEvent.ScalarEvent(value);
            }
            case DOUBLE_QUOTE_DELIMITER -> {
                state.getLexerState().setJsonKey(true);
                String value = doubleQuoteScalar(state);
                checkEmptyKey(state);
                return new YamlEvent.ScalarEvent(value);
            }
            case PLANAR_CHAR -> {
                String value = planarScalar(state);
                checkEmptyKey(state);
                return new YamlEvent.ScalarEvent(value);
            }
            case SEQUENCE_START -> {
                return new YamlEvent.StartEvent(SEQUENCE);
            }
            case SEQUENCE_ENTRY -> {
                if (state.isTagPropertiesInLine()) {
                    throw new Error.YamlParserException("'-' cannot be defined after tag properties",
                            state.getLine(), state.getColumn());
                }

                switch (state.getCurrentToken().getIndentation().change()) {
                    case INDENT_INCREASE -> {
                        return new YamlEvent.StartEvent(SEQUENCE);
                    }
                    case INDENT_NO_CHANGE -> {
                        return new YamlEvent.ScalarEvent();
                    }
                    case INDENT_DECREASE -> {
                        state.setIndentationProcessed(true);
                        for (Collection collection: state.getCurrentToken().getIndentation().collection()) {
                            state.getEventBuffer().add(new YamlEvent.EndEvent(collection));
                        }
                        return constructEvent(new YamlEvent.ScalarEvent(), tagStructure);
                    }
                }
            }
            case MAPPING_START -> {
                return new YamlEvent.StartEvent(Collection.MAPPING);
            }
            case LITERAL, FOLDED -> {
                if (state.getLexerState().isFlowCollection()) {
                    throw new Error.YamlParserException("cannot have a block node inside a flow node",
                            state.getLine(), state.getColumn());
                }
                String value = blockScalar(state, state.getCurrentToken().getType() == FOLDED);
                checkEmptyKey(state);
                return new YamlEvent.ScalarEvent(value);
            }
            case ALIAS -> {
                return new YamlEvent.AliasEvent(state.getCurrentToken().getValue());
            }
            case ANCHOR, TAG, TAG_HANDLE -> {
                YamlEvent event = nodeComplete(state, EXPECT_MAP_KEY, tagStructure);
                if (explicitKey) {
                    return event;
                }
                if (event.getKind() == YamlEvent.EventKind.START_EVENT &&
                        ((YamlEvent.StartEvent) event).getStartType() == Collection.MAPPING) {
                    return new YamlEvent.StartEvent(Collection.MAPPING);
                }
                if (event.getKind() == YamlEvent.EventKind.END_EVENT) {
                    state.getEventBuffer().add(0, event);
                    return new YamlEvent.ScalarEvent();
                }
            }
            case MAPPING_END -> {
                if (explicitKey) {
                    state.getEventBuffer().add(new YamlEvent.ScalarEvent());
                }
                state.getEventBuffer().add(new YamlEvent.EndEvent(Collection.MAPPING));
                return new YamlEvent.ScalarEvent();
            }
        }

        return new YamlEvent.ScalarEvent();
    }

    /**
     * Parse the string of a block scalar.
     *
     * @param state - Current parser state
     * @param isFolded - If set, then the parses folded block scalar. Else, parses literal block scalar.
     * @return - Parsed block scalar value
     */
    private static String blockScalar(ParserState state, boolean isFolded) throws Error.YamlParserException {
        String chompingIndicator = "";
        state.getLexerState().updateLexerState(LexerState.LEXER_BLOCK_HEADER);
        getNextToken(state);

        // Scan for block-header
        switch (state.getCurrentToken().getType()) {
            case CHOMPING_INDICATOR -> { // Strip and keep chomping indicators
                chompingIndicator = state.getCurrentToken().getValue();
                getNextToken(state, List.of(EOL));

                if (state.getLexerState().isEndOfStream()) {
                    state.initLexer();
                }
            }
            case EOL -> { // Clip chomping indicator
                state.initLexer();
                chompingIndicator = "=";
            }
        }

        state.getLexerState().updateLexerState(LexerState.LEXER_LITERAL);
        StringBuilder lexemeBuffer = new StringBuilder();
        StringBuilder newLineBuffer = new StringBuilder();
        boolean isFirstLine = true;
        boolean onlyEmptyLine = false;
        boolean prevTokenIndented = false;

        getNextToken(state, true);

        boolean terminated = false;
        while (!terminated) {
            switch (state.getBufferedToken().getType()) {
                case PRINTABLE_CHAR -> {
                    String bufferedTokenValue = state.getBufferedToken().getValue();
                    char bufferedTokenValueFirstChar = bufferedTokenValue.charAt(0);
                    if (!isFirstLine) {
                        String suffixChar = "\n";
                        if (isFolded && prevTokenIndented &&
                                (bufferedTokenValueFirstChar != ' ' && bufferedTokenValueFirstChar != '\t')) {
                            suffixChar = newLineBuffer.length() == 0 ? " " : "";
                        }
                        lexemeBuffer.append(newLineBuffer).append(suffixChar);
                        newLineBuffer = new StringBuilder();
                    }

                    lexemeBuffer.append(bufferedTokenValue);
                    prevTokenIndented = bufferedTokenValueFirstChar != ' ' && bufferedTokenValueFirstChar != '\t';
                    isFirstLine = false;
                }
                case EOL -> {
                    // Terminate at the end of the line
                    if (state.getLexerState().isEndOfStream()) {
                        terminated = true;
                        break;
                    }
                    state.initLexer();
                }
                case EMPTY_LINE -> {
                    if (!isFirstLine) {
                        newLineBuffer.append("\n");
                    }
                    if (state.getLexerState().isEndOfStream()) {
                        terminated = true;
                        break;
                    }
                    state.initLexer();
                    onlyEmptyLine = isFirstLine;
                    isFirstLine = false;
                }
                case TRAILING_COMMENT -> {
                    state.getLexerState().setTrailingComment(true);

                    // Terminate at the end of the line
                    if (state.getLexerState().isEndOfStream()) {
                        getNextToken(state);
                        terminated = true;
                        break;
                    }
                    state.initLexer();
                    getNextToken(state);
                    getNextToken(state, true);

                    // Ignore the tokens inside trailing comments
                    Token.TokenType bufferedTokenType = state.getBufferedToken().getType();
                    while (bufferedTokenType == EOL || bufferedTokenType == EMPTY_LINE) {
                        // Terminate at the end of the line
                        if (state.getLexerState().isEndOfStream()) {
                            break;
                        }
                        state.initLexer();
                        getNextToken(state);
                        getNextToken(state, true);
                        bufferedTokenType = state.getBufferedToken().getType();
                    }

                    state.getLexerState().setTrailingComment(false);
                    terminated = true;
                }
                default -> {
                    // Break the character when the token does not belong to planar scalar
                    terminated = true;
                }
            }
            if (!terminated) {
                getNextToken(state);
                getNextToken(state, true);
            }
        }

        // Adjust the tail based on the chomping values
        switch (chompingIndicator) {
            case "+" -> {
                lexemeBuffer.append("\n");
                lexemeBuffer.append(newLineBuffer);
            }
            case "=" -> {
                if (!onlyEmptyLine) {
                    lexemeBuffer.append("\n");
                }
            }
        }

        return lexemeBuffer.toString();
    }

    /**
     * Parse the string of a planar scalar.
     *
     * @param state - Current parser state.
     * @return - Parsed planar scalar value
     */
    private static String planarScalar(ParserState state) throws Error.YamlParserException {
        return planarScalar(state, true);
    }

    /**
     * Parse the string of a planar scalar.
     *
     * @param state - Current parser state.
     * @param allowTokensAsPlanar - If set, then the restricted tokens are allowed as a planar scalar
     * @return - Parsed planar scalar value
     */
    private static String planarScalar(ParserState state, boolean allowTokensAsPlanar)
            throws Error.YamlParserException {
        // Process the first planar char
        StringBuilder lexemeBuffer = new StringBuilder(state.getCurrentToken().getValue());
        boolean isFirstLine = true;
        StringBuilder newLineBuffer = new StringBuilder();
        state.getLexerState().setAllowTokensAsPlanar(allowTokensAsPlanar);

        getNextToken(state, true);

        boolean terminate = false;
        // Iterate the content until an invalid token is found
        while (!terminate) {
            switch (state.getBufferedToken().getType()) {
                case PLANAR_CHAR -> {
                    if (state.getBufferedToken().getIndentation() != null) {
                        terminate = true;
                        break;
                    }
                    getNextToken(state);
                    if (newLineBuffer.length() > 0) {
                        lexemeBuffer.append(newLineBuffer);
                        newLineBuffer = new StringBuilder();
                    } else { // Add a whitespace if there are no preceding empty lines
                        lexemeBuffer.append(" ");
                    }
                    lexemeBuffer.append(state.getCurrentToken().getValue());
                }
                case EOL -> {
                    getNextToken(state);

                    // Terminate at the end of the line
                    LexerState lexerState = state.getLexerState();
                    if (lexerState.isEndOfStream()) {
                        terminate = true;
                        break;
                    }
                    state.initLexer();

                    isFirstLine = false;
                }
                case COMMENT -> {
                    getNextToken(state);
                    terminate = true;
                }
                case EMPTY_LINE -> {
                    newLineBuffer.append("\n");
                    getNextToken(state);
                    // Terminate at the end of the line
                    if (state.getLexerState().isEndOfStream()) {
                        terminate = true;
                        break;
                    }
                    state.initLexer();
                }
                case SEPARATION_IN_LINE -> {
                    getNextToken(state);
                    // Continue to scan planar char if the white space at the end-of-line
                    getNextToken(state, true);
                    if (state.getBufferedToken().getType() == MAPPING_VALUE) {
                        terminate = true;
                    }
                }
                default -> { // Break the character when the token does not belong to planar scalar
                    terminate = true;
                }
            }
            if (!terminate) {
                getNextToken(state, true);
            }
        }

        if (state.getBufferedToken().getIndentation() == null) {
            verifyKey(state, isFirstLine);
        }
        state.getLexerState().setAllowTokensAsPlanar(false);
        return trimTailWhitespace(lexemeBuffer.toString());
    }

    /**
     * Parse the string of a double-quoted scalar.
     *
     * @param state - Current parser state
     * @return - Parsed double-quoted scalar value
     */
    private static String doubleQuoteScalar(ParserState state) throws Error.YamlParserException {
        state.getLexerState().updateLexerState(LexerState.LEXER_DOUBLE_QUOTE);
        String lexemeBuffer = "";
        state.getLexerState().setFirstLine(true);
        boolean emptyLine = false;
        boolean escaped = false;

        getNextToken(state);

        // Iterate the content until the delimiter is found
        while (state.getCurrentToken().getType() != DOUBLE_QUOTE_DELIMITER) {
            switch (state.getCurrentToken().getType()) {
                case DOUBLE_QUOTE_CHAR -> { // Regular double quoted string char
                    String lexeme = state.getCurrentToken().getValue();

                    // Check for double escaped character
                    if (lexeme.length() > 0 && lexeme.charAt(lexeme.length() - 1) == '\\') {
                        escaped = true;
                        lexemeBuffer += lexeme.substring(0, lexeme.length() - 1);
                    } else if (!state.getLexerState().isFirstLine()) {
                        if (escaped) {
                            escaped = false;
                        } else { // Trim the white space if not escaped
                            if (!emptyLine) { // Add a white space if there are not preceding empty lines
                                lexemeBuffer += " ";
                            }
                        }
                        lexemeBuffer += lexeme;
                    } else {
                        lexemeBuffer += lexeme;
                    }

                    if (emptyLine) {
                        emptyLine = false;
                    }
                }
                case EOL -> { // Processing new lines
                    if (!escaped) { // If not escaped, trim the trailing white spaces
                        lexemeBuffer = trimTailWhitespace(lexemeBuffer, state.getLexerState().getLastEscapedChar());
                    }

                    state.getLexerState().setFirstLine(false);
                    state.initLexer();

                    // Add a whitespace if the delimiter is on a new line
                    getNextToken(state, true);
                    if (state.getBufferedToken().getType() == DOUBLE_QUOTE_DELIMITER && !emptyLine) {
                        lexemeBuffer += " ";
                    }
                }
                case EMPTY_LINE -> {
                    if (escaped && !state.getLexerState().isFirstLine()) { // Whitespace is preserved when escaped
                        lexemeBuffer += state.getCurrentToken().getValue() + "\n";
                    } else if (!state.getLexerState().isFirstLine()) { // Whitespace is ignored when line folding
                        lexemeBuffer = trimTailWhitespace(lexemeBuffer);
                        lexemeBuffer += "\n";
                    }
                    emptyLine = true;
                    state.initLexer();

                    boolean firstLineBuffer = state.getLexerState().isFirstLine();
                    state.getLexerState().setFirstLine(false);

                    getNextToken(state, true);
                    if (state.getBufferedToken().getType() == DOUBLE_QUOTE_DELIMITER && firstLineBuffer) {
                        lexemeBuffer += " ";
                    }
                    state.getLexerState().setFirstLine(false);
                }
                default -> {
                    throw new Error.YamlParserException("invalid double quote scalar",
                            state.getLine(), state.getColumn());
                }
            }
            getNextToken(state);
        }

        verifyKey(state, state.getLexerState().isFirstLine());
        state.getLexerState().setFirstLine(true);
        return lexemeBuffer;
    }

    private static void checkEmptyKey(ParserState state) throws Error.YamlParserException {
        separate(state);
        getNextToken(state, true);

        Token bufferedToken = state.getBufferedToken();

        if (bufferedToken.getType() != MAPPING_VALUE || bufferedToken.getIndentation() == null) {
            return;
        }

        state.setEmptyKey(true);
        IndentUtils.Indentation indentation = bufferedToken.getIndentation();
        switch (indentation.change()) {
            case INDENT_INCREASE -> {
                int collectionSize = indentation.collection().size();
                state.getEventBuffer().add(
                        new YamlEvent.StartEvent(indentation.collection().remove(collectionSize - 1)));
            }
            case INDENT_DECREASE -> {
                for (Collection collection: indentation.collection()) {
                    state.getEventBuffer().add(new YamlEvent.EndEvent(collection));
                }
            }
        }
    }

    /** Parse the string of a single-quoted scalar.
     *
     * @param state - Current parser state
     * @return - Parsed single-quoted scalar value
     */
    private static String singleQuoteScalar(ParserState state) throws Error.YamlParserException {
        state.getLexerState().updateLexerState(LexerState.LEXER_SINGLE_QUOTE);
        String lexemeBuffer = "";
        state.getLexerState().setFirstLine(true);
        boolean emptyLine = false;

        getNextToken(state);

        // Iterate the content until the delimiter is found
        while (state.getCurrentToken().getType() != SINGLE_QUOTE_DELIMITER) {
            switch (state.getCurrentToken().getType()) {
                case SINGLE_QUOTE_CHAR -> {
                    String lexeme = state.getCurrentToken().getValue();

                    if (!state.getLexerState().isFirstLine()) {
                        if (emptyLine) {
                            emptyLine = false;
                        } else { // Add a white space if there are not preceding empty lines
                            lexemeBuffer += " ";
                        }
                    }
                    lexemeBuffer += lexeme;
                }
                case EOL -> {
                    // Trim trailing white spaces
                    lexemeBuffer = trimTailWhitespace(lexemeBuffer);
                    state.getLexerState().setFirstLine(false);
                    state.initLexer();


                    // Add a whitespace if the delimiter is on a new line
                    getNextToken(state, true);
                    if (state.getBufferedToken().getType() == SINGLE_QUOTE_DELIMITER && !emptyLine) {
                        lexemeBuffer += " ";
                    }
                }
                case EMPTY_LINE -> {
                    if (!state.getLexerState().isFirstLine()) { // Whitespace is ignored when line folding
                        lexemeBuffer = trimTailWhitespace(lexemeBuffer);
                        lexemeBuffer += "\n";
                    }
                    emptyLine = true;
                    state.initLexer();


                    boolean firstLineBuffer = state.getLexerState().isFirstLine();
                    state.getLexerState().setFirstLine(false);

                    getNextToken(state, true);
                    if (state.getBufferedToken().getType() == SINGLE_QUOTE_DELIMITER && firstLineBuffer) {
                        lexemeBuffer += " ";
                    }
                    state.getLexerState().setFirstLine(false);
                }
                default -> {
                    throw new Error.YamlParserException("invalid single quote character",
                            state.getLine(), state.getColumn());
                }
            }
            getNextToken(state);
        }

        verifyKey(state, state.getLexerState().isFirstLine());
        state.getLexerState().setFirstLine(true);
        return lexemeBuffer;
    }

    /**
     * Trims the trailing whitespace of a string.
     */
    private static String trimTailWhitespace(String value) {
        return trimTailWhitespace(value, -1);
    }

    /**
     * Trims the trailing whitespace of a string.
     */
    private static String trimTailWhitespace(String value, int lastEscapedChar) {
        int i = value.length() - 1;

        if (i < 0) {
            return "";
        }

        char charAtI = value.charAt(i);
        while (charAtI == ' ' || charAtI == '\t') {
            if (i < 1 || (lastEscapedChar != -1 && i == lastEscapedChar)) {
                break;
            }
            i -= 1;
            charAtI = value.charAt(i);
        }

        return value.substring(0, i + 1);
    }

    /**
     * Check if the given key adheres to either a explicit or a implicit key.
     *
     * @param state - Current parser state
     * @param isSingleLine - If the scalar only spanned for one line
     */
    private static void verifyKey(ParserState state, boolean isSingleLine) throws Error.YamlParserException {
        // Explicit keys can span multiple lines.
        if (state.isExplicitKey()) {
            return;
        }

        // Regular keys can only exist within one line
        state.getLexerState().updateLexerState(LexerState.LEXER_START_STATE);
        getNextToken(state, true);
        if (state.getBufferedToken().getType() == MAPPING_VALUE && !isSingleLine) {
            throw new Error.YamlParserException("mapping keys cannot span multiple lines",
                    state.getLine(), state.getColumn());
        }
    }

    /**
     * Assert the next lexer token with the predicted token.
     *
     * @param state - Current parser state
     */
    public static void getNextToken(ParserState state) throws Error.YamlParserException {
        getNextToken(state, List.of(Token.TokenType.DUMMY));
    }

    /**
     * Assert the next lexer token with the predicted token.
     *
     * @param state - Current parser state
     * @param peek Store the token in the buffer
     */
    public static void getNextToken(ParserState state, boolean peek) throws Error.YamlParserException {
        getNextToken(state, List.of(Token.TokenType.DUMMY), peek);
    }

    /**
     * Assert the next lexer token with the predicted token.
     *
     * @param state - Current parser state
     * @param expectedTokens Predicted tokens
     */
    public static void getNextToken(ParserState state, List<Token.TokenType> expectedTokens)
            throws Error.YamlParserException {
        getNextToken(state, expectedTokens, false);
    }

    /**
     * Assert the next lexer token with the predicted token.
     *
     * @param state - Current parser state
     * @param expectedTokens Predicted tokens
     * @param peek Store the token in the buffer
     */
    public static void getNextToken(ParserState state, List<Token.TokenType> expectedTokens, boolean peek)
            throws Error.YamlParserException {
        Token token;

        // Obtain a token form the lexer if there is none in the buffer.
        if (state.getBufferedToken().getType() == Token.TokenType.DUMMY) {
            state.updateLexerState(YamlLexer.scanTokens(state.getLexerState()));
            token = state.getLexerState().getToken();
        } else {
            token = state.getBufferedToken();
            state.setBufferedToken(ParserState.DUMMY_TOKEN);
        }

        // Add the token to the tokenBuffer if the peek flag is set.
        if (peek) {
            state.setBufferedToken(token);
        } else {
            state.setCurrentToken(token);
        }

        // Bypass error handling.
        if (expectedTokens.get(0) == Token.TokenType.DUMMY) {
            return;
        }

        if (!expectedTokens.contains(token.getType())) {
            throw new Error.YamlParserException("expected token differ from the actual token",
                    state.getLine(), state.getColumn());
        }
    }

    public static class TagStructure {
        String anchor = null;
        String tag = null;
    }
}
