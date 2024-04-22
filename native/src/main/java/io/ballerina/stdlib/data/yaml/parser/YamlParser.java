package io.ballerina.stdlib.data.yaml.parser;

import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.flags.SymbolFlags;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.Field;
import io.ballerina.runtime.api.types.MapType;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.types.UnionType;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.stdlib.data.yaml.common.Types;
import io.ballerina.stdlib.data.yaml.common.Types.Collection;
import io.ballerina.stdlib.data.yaml.lexer.Indentation;
import io.ballerina.stdlib.data.yaml.lexer.LexerState;
import io.ballerina.stdlib.data.yaml.lexer.Token;
import io.ballerina.stdlib.data.yaml.lexer.YamlLexer;
import io.ballerina.stdlib.data.yaml.utils.DiagnosticErrorCode;
import io.ballerina.stdlib.data.yaml.utils.DiagnosticLog;

import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import static io.ballerina.stdlib.data.yaml.common.Types.Collection.SEQUENCE;
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
import static io.ballerina.stdlib.data.yaml.parser.ParserUtils.DocumentType.ANY_DOCUMENT;
import static io.ballerina.stdlib.data.yaml.parser.ParserUtils.DocumentType.BARE_DOCUMENT;
import static io.ballerina.stdlib.data.yaml.parser.ParserUtils.DocumentType.DIRECTIVE_DOCUMENT;
import static io.ballerina.stdlib.data.yaml.parser.ParserUtils.ParserOption.EXPECT_MAP_KEY;
import static io.ballerina.stdlib.data.yaml.parser.ParserUtils.ParserOption.EXPECT_MAP_VALUE;
import static io.ballerina.stdlib.data.yaml.parser.ParserUtils.ParserOption.EXPECT_SEQUENCE_ENTRY;
import static io.ballerina.stdlib.data.yaml.parser.ParserUtils.ParserOption.EXPECT_SEQUENCE_VALUE;
import static io.ballerina.stdlib.data.yaml.parser.ParserUtils.getAllFieldsInRecord;
import static io.ballerina.stdlib.data.yaml.parser.ParserUtils.isSupportedUnionType;
import static io.ballerina.stdlib.data.yaml.parser.ParserUtils.possibleYamlStream;

public class YamlParser {

    public static final String DEFAULT_LOCAL_TAG_HANDLE = "!";
    public static final String DEFAULT_GLOBAL_TAG_HANDLE = "tag:yaml.org,2002:";
    public static final Map<String, String> DEFAULT_TAG_HANDLES = Map.of("!", DEFAULT_LOCAL_TAG_HANDLE,
            "!!", DEFAULT_GLOBAL_TAG_HANDLE);

    public static class ComposerState {
        private final ParserState parserState;
        private final Map<String, String> anchorBuffer = new HashMap<>();
        private boolean documentTerminated = false;
        private boolean allowMapEntryRedefinition = false;
        private boolean allowAnchorRedefinition = false;

        Object currentYamlNode;

        Field currentField;
        Deque<String> fieldNames = new ArrayDeque<>();
        Deque<Object> nodesStack = new ArrayDeque<>();
        Stack<Map<String, Field>> fieldHierarchy = new Stack<>();
        Stack<Map<String, Field>> visitedFieldHierarchy = new Stack<>();
        Stack<Type> restType = new Stack<>();
        Stack<Type> expectedTypes = new Stack<>();
        int jsonFieldDepth = 0;
        Stack<Integer> arrayIndexes = new Stack<>();
        boolean possibleYamlStream = false;
        boolean rootValueInitialized = false;

        public ComposerState(ParserState parserState) {
            this.parserState = parserState;
        }

        public void updateExpectedType(Map<String, Field> fields, Type restType) {
            this.fieldHierarchy.push(new HashMap<>(fields));
            this.visitedFieldHierarchy.push(new HashMap<>());
            this.restType.push(restType);
        }

        public void finalizeNonArrayObjectAndRemoveExpectedType() {
            finalizeNonArrayObject();
        }

        private void updateIndexOfArrayElement() {
            int arrayIndex = arrayIndexes.pop();
            arrayIndexes.push(arrayIndex + 1);
        }

        private void finalizeArrayObject() {
            int currentIndex = arrayIndexes.pop();
            finalizeObject();
            Values.validateListSize(currentIndex, expectedTypes.pop());
        }

        private void finalizeNonArrayObject() {
            if (jsonFieldDepth > 0) {
                jsonFieldDepth--;
            }

            if (!expectedTypes.isEmpty() && expectedTypes.peek() == null) {
                return;
            }

            Map<String, Field> remainingFields = fieldHierarchy.pop();
            visitedFieldHierarchy.pop();
            restType.pop();
            for (Field field : remainingFields.values()) {
                if (SymbolFlags.isFlagOn(field.getFlags(), SymbolFlags.REQUIRED)) {
                    throw DiagnosticLog.error(DiagnosticErrorCode.REQUIRED_FIELD_NOT_PRESENT, field.getFieldName());
                }
            }
            finalizeObject();
        }

        private void finalizeObject() {
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
                int currentYamlNodeTypeTag = TypeUtils.getReferredType(TypeUtils.getType(currentYamlNode)).getTag();
                if (currentYamlNodeTypeTag == TypeTags.ARRAY_TAG || currentYamlNodeTypeTag == TypeTags.TUPLE_TAG) {
                    expectedTypes.pop();
                    ((BMap<BString, Object>) parentNode).put(StringUtils.fromString(this.fieldNames.pop()),
                            currentYamlNode);
                }
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
                case TypeTags.TUPLE_TAG -> ((BArray) parentNode).add(arrayIndexes.peek(), currentYamlNode);
                default -> {
                }
            }

            currentYamlNode = parentNode;
            return;
        }

        public void handleExpectedType(Type type) {
            switch (type.getTag()) {
                // TODO: Handle readonly and singleton type as expType.
                case TypeTags.RECORD_TYPE_TAG -> {
                    RecordType recordType = (RecordType) type;
                    expectedTypes.add(recordType);
                    fieldHierarchy.push(getAllFieldsInRecord(recordType));
                    visitedFieldHierarchy.push(new HashMap<>());
                    restType.push(recordType.getRestFieldType());
                }
                case TypeTags.ARRAY_TAG, TypeTags.TUPLE_TAG -> {
                    expectedTypes.add(type);
                    arrayIndexes.push(0);
                    possibleYamlStream = true;
                }
                case TypeTags.NULL_TAG, TypeTags.BOOLEAN_TAG, TypeTags.INT_TAG, TypeTags.FLOAT_TAG,
                        TypeTags.DECIMAL_TAG, TypeTags.STRING_TAG ->
                        expectedTypes.push(type);
                case TypeTags.JSON_TAG, TypeTags.ANYDATA_TAG -> {
                    expectedTypes.push(type);
                    fieldHierarchy.push(new HashMap<>());
                    visitedFieldHierarchy.push(new HashMap<>());
                    restType.push(type);
                    possibleYamlStream = true;
                }
                case TypeTags.MAP_TAG -> {
                    expectedTypes.push(type);
                    fieldHierarchy.push(new HashMap<>());
                    visitedFieldHierarchy.push(new HashMap<>());
                    restType.push(((MapType) type).getConstrainedType());
                }
                case TypeTags.UNION_TAG -> {
                    if (isSupportedUnionType((UnionType) type)) {
                        expectedTypes.push(type);
                        if (possibleYamlStream((UnionType) type)) {
                            possibleYamlStream = true;
                        }
                        break;
                    }
                    throw DiagnosticLog.error(DiagnosticErrorCode.UNSUPPORTED_TYPE, type);
                }
                default -> throw DiagnosticLog.error(DiagnosticErrorCode.UNSUPPORTED_TYPE, type);
            }
        }
    }

    /**
     * Parses the contents in the given {@link Reader} and returns subtype of anydata value.
     *
     * @param reader reader which contains the YAML content
     * @param expectedType Shape of the YAML content required
     * @return subtype of anydata value
     * @throws BError for any parsing error
     */
    public static Object parse(Reader reader, Type expectedType) throws BError {
        ComposerState composerState = new ComposerState(new ParserState(reader, expectedType));
        composerState.handleExpectedType(expectedType);
        return parseDocument(composerState);
    }

    private static Object parseDocument(ComposerState state) {

        ParserEvent event = parse(state.parserState);

        // Ignore the start document marker for explicit documents
        if (event.getKind() == ParserEvent.EventKind.DOCUMENT_MARKER_EVENT &&
                ((ParserEvent.DocumentMarkerEvent) event).isExplicit()) {
            event = parse(state.parserState, ANY_DOCUMENT);
        }

        Object output = handleEvent(state, event, false);

//        // Return an error if there is another root event
//        event = check checkEvent(state);
//        if event is common:EndEvent && event.endType == common:STREAM {
//            return output;
//        }
//        if event is common:DocumentMarkerEvent {
//            state.terminatedDocEvent = event;
//            return output;
//        }
//        return generateComposeError(state, "There can only be one root event to a document", event);
        return output;
    }

    private static Object handleEvent(ComposerState state, ParserEvent event, boolean mapOrSequenceScalar) {
        // Check for aliases
        ParserEvent.EventKind eventKind = event.getKind();

        if (eventKind == ParserEvent.EventKind.ALIAS_EVENT) {
            ParserEvent.AliasEvent aliasEvent = (ParserEvent.AliasEvent) event;
            String alias = state.anchorBuffer.get(aliasEvent.getAnchor());
            if (alias == null) {
                throw new RuntimeException("The anchor does not exist");
            }
            return alias;
        }

        // Ignore end events
        if (eventKind == ParserEvent.EventKind.END_EVENT) {
            return null;
        }

        // Ignore document markers
        if (eventKind == ParserEvent.EventKind.DOCUMENT_MARKER_EVENT) {
            state.documentTerminated = true;
            return null;
        }

        Object output = null;
        // Check for collections
        if (eventKind == ParserEvent.EventKind.START_EVENT) {
            ParserEvent.StartEvent startEvent = (ParserEvent.StartEvent) event;

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
                    throw new RuntimeException("Only sequence and mapping are allowed as node start events");
                }
            }
            checkAnchor(state, event, output.toString());
            return state.currentYamlNode;
        }

        ParserEvent.ScalarEvent scalarEvent = (ParserEvent.ScalarEvent) event;

        // Check for scalar
        output =  castData(state, scalarEvent.getValue(), Types.FailSafeSchema.STRING, event.getTag());
        checkAnchor(state, event, output.toString());
        if (mapOrSequenceScalar) {
            return output.toString();
        }
        processValue(state, scalarEvent.getValue());
        return state.currentYamlNode;
    }

    private static void processValue(ComposerState state, String value) {
        Type expType = state.expectedTypes.pop();
        BString bString = StringUtils.fromString(value);
        if (expType == null) {
            return;
        }
        state.currentYamlNode = Values.convertAndUpdateCurrentValueNode(state, bString, expType);
    }

    public static Object composeSequence(YamlParser.ComposerState state, boolean flowStyle) {
        boolean firstElement = true;
        if (!state.rootValueInitialized) {
            Type expType = state.expectedTypes.peek();
            // In this point we know rhs is json[] or anydata[] hence init index counter.
            if (expType.getTag() == TypeTags.JSON_TAG || expType.getTag() == TypeTags.ANYDATA_TAG) {
                state.arrayIndexes.push(0);
            }
            state.currentYamlNode = Values.initArrayValue(state.expectedTypes.peek());
            state.rootValueInitialized = true;
        } else {
            Values.updateNextArrayValue(state);
        }

        List<Object> sequence = new ArrayList<>();
        ParserEvent event = parse(state.parserState, EXPECT_SEQUENCE_VALUE);

        // Iterate until the end sequence event is detected
        boolean terminated = false;
        while (!terminated) {
            if (event.getKind() == ParserEvent.EventKind.DOCUMENT_MARKER_EVENT) {
                state.documentTerminated = true;
                if (!flowStyle) {
                    break;
                }
                throw new RuntimeException("Unexpected event");
            }

            if (event.getKind() == ParserEvent.EventKind.END_EVENT) {
                ParserEvent.EndEvent endEvent = (ParserEvent.EndEvent) event;

                switch (endEvent.getEndType()) {
                    case MAPPING -> throw new RuntimeException("Unexpected event");
                    case STREAM -> {
                        if (!flowStyle) {
                            terminated = true;
                            break;
                        }
                        throw new RuntimeException("Unexpected event");
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
                Object value = handleEvent(state, event, true);
                state.expectedTypes.push(Values.getMemberType(state.expectedTypes.peek(),
                        state.arrayIndexes.peek()));
                if (value instanceof String scalarValue) {
                    processValue(state, scalarValue);
                }
                event = parse(state.parserState, EXPECT_SEQUENCE_ENTRY);
            }
        }

        state.finalizeArrayObject();

        return (sequence.size() == 0 && !flowStyle) ? Collections.singletonList(null) : sequence;
    }

    public static Object composeMapping(ComposerState state, boolean flowStyle, boolean implicitMapping) {
        boolean isParentSequence = false;
        if (!state.rootValueInitialized) {
            state.currentYamlNode = Values.initRootMapValue(state.expectedTypes.peek());
            state.rootValueInitialized = true;
        } else {
            Object currentYamlNode = state.currentYamlNode;
            isParentSequence = TypeUtils.getType(currentYamlNode).getTag() == TypeTags.ARRAY_TAG;
            Values.updateNextMapValue(state);
        }
        Map<String, Object> structure = new HashMap<>();
        ParserEvent event = parse(state.parserState, EXPECT_MAP_KEY);

        // Iterate until an end event is detected
        boolean terminated = false;
        while (!terminated) {
            if (event.getKind() == ParserEvent.EventKind.DOCUMENT_MARKER_EVENT) {
                state.documentTerminated = true;
                if (!flowStyle) {
                    break;
                }
                throw new RuntimeException("Unexpected event");
            }

            if (event.getKind() == ParserEvent.EventKind.END_EVENT) {
                ParserEvent.EndEvent endEvent = (ParserEvent.EndEvent) event;
                switch (endEvent.getEndType()) {
                    case MAPPING -> terminated = true;
                    case SEQUENCE -> throw new RuntimeException("Unexpected event");
                    default -> {
                        if (!flowStyle) {
                            terminated = true;
                            break;
                        }
                        throw new RuntimeException("Unexpected event");
                    }
                }
                if (terminated) {
                    break;
                }
            }

            // Cannot have a nested block mapping if a value is assigned
            if (event.getKind() == ParserEvent.EventKind.START_EVENT
                    && !((ParserEvent.StartEvent) event).isFlowStyle()) {
                throw new RuntimeException("Cannot have nested mapping under a key-pair that is already assigned");
            }

            // Compose the key
            String key = (String) handleEvent(state, event, true);

            if (!state.allowMapEntryRedefinition && structure.containsKey(key.toString())) {
                throw new RuntimeException("Cannot have duplicate map entries for '${key.toString()}");
            }

            if (state.jsonFieldDepth == 0) {
                Field currentField;
                if (state.visitedFieldHierarchy.peek().containsKey(key)) {
                    currentField = state.visitedFieldHierarchy.peek().get(key);
                } else {
                    currentField = state.fieldHierarchy.peek().remove(key);
                }

                state.currentField = currentField;
                Type fieldType;
                if (currentField == null) {
                    fieldType = state.restType.peek();
                } else {
                    // Replace modified field name with actual field name.
                    key = currentField.getFieldName();
                    fieldType = currentField.getFieldType();
                    state.visitedFieldHierarchy.peek().put(key, currentField);
                }
                state.expectedTypes.push(fieldType);
            } else if (state.expectedTypes.peek() == null) {
                state.expectedTypes.push(null);
            }
            state.fieldNames.push(key);

            // Compose the value
            event = parse(state.parserState, EXPECT_MAP_VALUE);

            // Check for mapping end events
            if (event.getKind() == ParserEvent.EventKind.END_EVENT) {
                ParserEvent.EndEvent endEvent = (ParserEvent.EndEvent) event;
                switch (endEvent.getEndType()) {
                    case MAPPING -> {
                        structure.put(key, null);
                        terminated = true;
                    }
                    case SEQUENCE -> throw new RuntimeException("Unexpected event error");
                    default -> {
                        if (!flowStyle) {
                            structure.put(key, null);
                            terminated = true;
                            break;
                        }
                        throw new RuntimeException("Unexpected event error");
                    }
                }
                if (terminated) {
                    break;
                }
            } else {
                Object value = handleEvent(state, event, true);
                if (value instanceof String scalarValue) {
                    Type expType = state.expectedTypes.pop();
                    if (expType == null) {
                        break;
                    }
                    if (state.jsonFieldDepth > 0 || state.currentField != null) {
                        state.currentYamlNode = Values.convertAndUpdateCurrentValueNode(state,
                                StringUtils.fromString(scalarValue), expType);
                    } else if (state.restType.peek() != null) {
                        try {
                            state.currentYamlNode = Values.convertAndUpdateCurrentValueNode(state,
                                    StringUtils.fromString(scalarValue), expType);
                            // this element will be ignored in projection
                        } catch (BError ignored) { }
                    }
                }
            }

            // Terminate after single key-value pair if implicit mapping flag is set.
            if (implicitMapping) {
                break;
            }

            event = parse(state.parserState, EXPECT_MAP_KEY);
        }

        state.finalizeNonArrayObjectAndRemoveExpectedType();
        if (!isParentSequence) {
            state.expectedTypes.pop();
        }
        return structure;
    }


    /**
     * Update the alias dictionary for the given alias.
     *
     * @param state - Current composer state
     * @param event - The event representing the alias name
     * @param assignedValue - Anchored value to the alias
     */
    public static void checkAnchor(ComposerState state, ParserEvent event, String assignedValue) {
        if (event.getAnchor() != null) {
            if (state.anchorBuffer.containsKey(event.getAnchor()) && !state.allowAnchorRedefinition) {
                throw new RuntimeException("Duplicate anchor definition");
            }
            state.anchorBuffer.put(event.getAnchor(), assignedValue);
        }
    }

    public static Object castData(ComposerState state, Object data, Types.FailSafeSchema kind, String tag)  {
        // Check for explicit keys
        if (tag != null) {
            // Check for the tags in the YAML failsafe schema
            if (tag.equals(DEFAULT_GLOBAL_TAG_HANDLE + "str")) {
                if (kind == Types.FailSafeSchema.STRING) {
                    return data.toString();
                }
                throw new RuntimeException("Unexpected kind error");
            }

            if (tag.equals(DEFAULT_GLOBAL_TAG_HANDLE + "seq")) {
                if (kind == Types.FailSafeSchema.SEQUENCE) {
                    return data;
                }
                throw new RuntimeException("Unexpected kind error");
            }

            if (tag.equals(DEFAULT_GLOBAL_TAG_HANDLE + "map")) {
                if (kind == Types.FailSafeSchema.MAPPING) {
                    return data;
                }
                throw new RuntimeException("Unexpected kind error");
            }

            throw new RuntimeException("tag schema not supported");
        }

        // Return as a type of the YAML failsafe schema.
        return data;
    }


    /**
     * Obtain an event for the for a set of tokens.
     *
     * @param state - Current parser state
     * @return - Parsed event
     */
    private static ParserEvent parse(ParserState state) {
        return parse(state, ParserUtils.ParserOption.DEFAULT, BARE_DOCUMENT);
    }

    /**
     * Obtain an event for the for a set of tokens.
     *
     * @param state - Current parser state
     * @param docType - Document type to be parsed
     * @return - Parsed event
     */
    private static ParserEvent parse(ParserState state, ParserUtils.DocumentType docType) {
        return parse(state, ParserUtils.ParserOption.DEFAULT, docType);
    }

    /**
     * Obtain an event for the for a set of tokens.
     *
     * @param state - Current parser state
     * @param option - Expected values inside a mapping collection
     * @return - Parsed event
     */
    private static ParserEvent parse(ParserState state, ParserUtils.ParserOption option) {
        return parse(state, option, BARE_DOCUMENT);
    }

    /**
     * Obtain an event for the for a set of tokens.
     *
     * @param state - Current parser state
     * @param option - Expected values inside a mapping collection
     * @param docType - Document type to be parsed
     * @return - Parsed event
     */
    private static ParserEvent parse(ParserState state, ParserUtils.ParserOption option,
                                     ParserUtils.DocumentType docType) {
        // Empty the event buffer before getting new tokens
        final List<ParserEvent> eventBuffer = state.getEventBuffer();

        if (!eventBuffer.isEmpty()) {
            return eventBuffer.remove(0);
        }
        state.updateLexerState(LexerState.LEXER_START_STATE);
        getNextToken(state);

        // Ignore the whitespace at the head
        if (state.getCurrentToken().getType() == SEPARATION_IN_LINE) {
            getNextToken(state);
        }

        Token.TokenType currentTokenType = state.getCurrentToken().getType();
        // Set the next line if the end of line is detected
        if (currentTokenType == EOL || currentTokenType == EMPTY_LINE || currentTokenType == COMMENT) {

            LexerState lexerState = state.getLexerState();
            if ((!lexerState.isNewLine() && state.getLineIndex() >= state.getNumLines() - 1)
                || (lexerState.isNewLine() && lexerState.isEndOfStream()))  {
                if (docType == DIRECTIVE_DOCUMENT) {
                    throw new RuntimeException("Invalid document");
                }
                return new ParserEvent.EndEvent(Collection.STREAM);
            }
            try {
                state.initLexer();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }

            return parse(state, option, docType);
        }

        // Only directive tokens are allowed in directive document
        if (currentTokenType != DIRECTIVE && currentTokenType != DIRECTIVE_MARKER) {
            if (docType == DIRECTIVE_DOCUMENT) {
                throw new RuntimeException("'${state.currentToken.token}' is not allowed in a directive document");
            }
            state.setDirectiveDocument(false);
        }

        switch (currentTokenType) {
            case DIRECTIVE -> {
                // Directives are not allowed in bare documents
                if (docType == BARE_DOCUMENT) {
                    throw new RuntimeException("Directives are not allowed in a bare document");
                }

                switch (state.getCurrentToken().getValue()) {
                    case "YAML" -> yamlDirective(state);
                    case "TAG" -> tagDirective(state);
                    default -> reservedDirective(state);
                }
                getNextToken(state, List.of(SEPARATION_IN_LINE, EOL));

                state.setDirectiveDocument(true);
                return parse(state, DIRECTIVE_DOCUMENT);
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
                        throw new RuntimeException("'${state.tokenBuffer.token}' token cannot " +
                                "start in the same line as the document marker");
                    }

                    // Block collection nodes cannot be next to the directive marker.
                    if (explicit && (bufferedTokenType == PLANAR_CHAR && bufferedToken.getIndentation() != null
                            || bufferedTokenType == SEQUENCE_ENTRY)) {
                        throw new RuntimeException("'${state.tokenBuffer.token}' token cannot start " +
                                "in the same line as the directive marker");
                    }
                }
                return new ParserEvent.DocumentMarkerEvent(explicit);
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
                        state.getEventBuffer().add(new ParserEvent.ScalarEvent());
                        return new ParserEvent.StartEvent(Collection.MAPPING, false, true);
                    }
                    return new ParserEvent.ScalarEvent();
                } else {
                    Indentation indentation = state.getCurrentToken().getIndentation();
                    separate(state);
                    switch (indentation.getChange()) {
                        case INDENT_INCREASE -> { // Increase in indent
                            state.getEventBuffer().add(new ParserEvent.ScalarEvent());
                            return new ParserEvent.StartEvent(Collection.MAPPING);
                        }
                        case INDENT_NO_CHANGE -> { // Same indent
                            return new ParserEvent.ScalarEvent();
                        }
                        case INDENT_DECREASE -> { // Decrease in indent

                            for (Collection collection: indentation.getCollection()) {
                                state.getEventBuffer().add(new ParserEvent.EndEvent(collection));
                            }
                            if (option == EXPECT_MAP_VALUE) {
                                state.getEventBuffer().add(new ParserEvent.ScalarEvent());
                            }
                            return state.getEventBuffer().remove(0);
                        }
                    }
                }
            }
            case SEPARATOR -> { // Empty node as the value in flow mappings
                if (option == EXPECT_MAP_VALUE) { // Check for empty values in flow mappings
                    return new ParserEvent.ScalarEvent();
                }
            }
            case MAPPING_KEY -> { // Explicit key
                state.setExplicitKey(true);
                state.setLastExplicitKeyLine(state.getLineIndex());
                return appendData(state, option);
            }
            case SEQUENCE_ENTRY -> {
                if (state.getLexerState().isFlowCollection()) {
                    throw new RuntimeException("Cannot have block sequence under flow collection");
                }
                if (state.isExpectBlockSequenceValue()) {
                    throw new RuntimeException("Cannot have nested sequence for a defined value");
                }

                switch (state.getCurrentToken().getIndentation().getChange()) {
                    case INDENT_INCREASE -> { // Increase in indent
                       return new ParserEvent.StartEvent(SEQUENCE);
                    }
                    case INDENT_NO_CHANGE -> { // Same indent
                        ParserEvent event = parse(state, EXPECT_SEQUENCE_VALUE, docType);
                        if (option == EXPECT_SEQUENCE_VALUE) {
                            state.getEventBuffer().add(event);
                            return new ParserEvent.ScalarEvent();
                        }
                        return event;
                    }
                    case INDENT_DECREASE -> { // Decrease in indent
                        for (Collection collection: state.getCurrentToken().getIndentation().getCollection()) {
                            state.getEventBuffer().add(new ParserEvent.EndEvent(collection));
                        }
                        return state.getEventBuffer().remove(0);
                    }
                }
            }
            case MAPPING_START -> {
                return new ParserEvent.StartEvent(Collection.MAPPING, true, false);
            }
            case SEQUENCE_START -> {
                return new ParserEvent.StartEvent(SEQUENCE, true, false);
            }
            case SEQUENCE_END -> {
                if (state.getLexerState().isFlowCollection()) {
                    separate(state);
                    Token.TokenType bufferedTokenType = state.getBufferedToken().getType();
                    if (bufferedTokenType == SEPARATOR) {
                        getNextToken(state);
                    } else if (bufferedTokenType != MAPPING_END && bufferedTokenType != SEQUENCE_END) {
                        throw new RuntimeException("Unexpected token error");
                    }
                }
                return new ParserEvent.EndEvent(SEQUENCE);
            }
            case MAPPING_END -> {
                if (option == EXPECT_MAP_VALUE) {
                    state.getEventBuffer().add(new ParserEvent.EndEvent(Collection.MAPPING));
                    return new ParserEvent.ScalarEvent();
                }
                if (state.getLexerState().isFlowCollection()) {
                    separate(state);
                    Token.TokenType bufferedTokenType = state.getBufferedToken().getType();
                    if (bufferedTokenType == SEPARATOR) {
                        getNextToken(state);
                    } else if (bufferedTokenType != MAPPING_END && bufferedTokenType != SEQUENCE_END) {
                        throw new RuntimeException("Unexpected token error");
                    }
                }
                return new ParserEvent.EndEvent(Collection.MAPPING);
            }
            case LITERAL, FOLDED -> {
                state.updateLexerState(LexerState.LEXER_LITERAL);
                return appendData(state, option, true);
            }
        }

        throw new RuntimeException("`Invalid token '${state.currentToken.token}' as the first for generating an event");
    }

    /** Verifies the grammar production for separation between nodes.
     *
     * @param state - Current parser state
     */
    private static void separate(ParserState state) {
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

    private static ParserEvent nodeComplete(ParserState state, ParserUtils.ParserOption option) {
        return null;
    }

    private static ParserEvent nodeComplete(ParserState state, ParserUtils.ParserOption option,
                                            TagStructure definedProperties) {
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
                List<String> tuple = nodeTag(state);
                String tagHandle = tuple.get(0);
                String tagPrefix = tuple.get(1);

                // Construct the complete tag
                if (tagPrefix != null) {
                    tagStructure.tag = tagHandle == null ? tagPrefix :
                            generateCompleteTagName(state, tagHandle, tagPrefix);
                }
            }
        }
        return appendData(state, option, false, tagStructure, definedProperties);
    }

    private static List<String> nodeTag(ParserState state) {
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
        return List.of(tagHandle, tagPrefix);
    }

    private static String nodeAnchor(ParserState state) {
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
    private static String generateCompleteTagName(ParserState state, String tagHandle, String tagPrefix) {
        String tagHandleName;
        // Check if the tag handle is defined in the custom tags.
        if (state.getCustomTagHandles().containsKey(tagHandle)) {
            tagHandleName = state.getCustomTagHandles().get(tagHandle);
        } else { // Else, check if the tag handle is in the default tags.
            if (DEFAULT_TAG_HANDLES.containsKey(tagHandle)) {
                tagHandleName = DEFAULT_TAG_HANDLES.get(tagHandle);
            } else {
                throw new RuntimeException("tag handle is not defined");
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
    private static ParserEvent appendData(ParserState state, ParserUtils.ParserOption option) {
        return appendData(state, option, false, new TagStructure(), null);
    }

    private static ParserEvent appendData(ParserState state, ParserUtils.ParserOption option, boolean peeked) {
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
    private static ParserEvent appendData(ParserState state, ParserUtils.ParserOption option, boolean peeked,
                                          TagStructure tagStructure, TagStructure definedProperties) {

        state.setExpectBlockSequenceValue(true);
        ParserEvent buffer = null;

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
            buffer = new ParserEvent.ScalarEvent();
        }

        Indentation indentation = null;
        if (state.isExplicitKey()) {
            indentation = state.getCurrentToken().getIndentation();
            separate(state);
        }

        state.updateLexerState(LexerState.LEXER_START_STATE);

        if (state.getLastExplicitKeyLine() == state.getLineIndex() && !state.getLexerState().isFlowCollection()
                && option == EXPECT_MAP_KEY) {
            throw new RuntimeException("Cannot have a scalar next to a block key-value pair");
        }

        ParserEvent event = content(state, peeked, state.isExplicitKey(), tagStructure);
        boolean isAlias = event.getKind() == ParserEvent.EventKind.ALIAS_EVENT;

        state.setExplicitKey(false);
        if (!explicitKey) {
            indentation = state.getCurrentToken().getIndentation();
        }

        // The tokens described in the indentation.tokens belong to the second node.
        TagStructure newNodeTagStructure = new TagStructure();
        TagStructure currentNodeTagStructure = new TagStructure();
        if (indentation != null) {
            switch (indentation.getTokens().size()) {
                case 0 -> {
                    newNodeTagStructure = tagStructure;
                }
                case 1 -> {
                    switch (indentation.getTokens().get(0)) {
                        case ANCHOR -> {
                            if (isAlias && tagStructure.anchor != null) {
                                throw new RuntimeException("An alias node cannot have an anchor");
                            }
                            newNodeTagStructure.tag = tagStructure.tag;
                            currentNodeTagStructure.anchor = tagStructure.anchor;
                        }
                        case TAG -> {
                            if (isAlias && tagStructure.tag != null) {
                                throw new RuntimeException("An alias node cannot have a tag");
                            }
                            newNodeTagStructure.anchor = tagStructure.anchor;
                            currentNodeTagStructure.tag = tagStructure.tag;
                        }
                    }
                }
                case 2 -> {
                    if (isAlias && (tagStructure.anchor != null || tagStructure.tag != null)) {
                        throw new RuntimeException("An alias node cannot have tag properties");
                    }
                    currentNodeTagStructure = tagStructure;
                }
            }
        } else {
            if (isAlias && (tagStructure.anchor != null || tagStructure.tag != null)) {
                throw new RuntimeException("An alias node cannot have tag properties");
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
                throw new RuntimeException("',' are only allowed in flow collections");
            }
            separate(state);
            if (option == EXPECT_MAP_KEY) {
                state.getEventBuffer().add(new ParserEvent.ScalarEvent());
            }
        } else if (state.getCurrentToken().getType() == MAPPING_VALUE) {
            // If there are no whitespace, and the current token is ':'
            if (state.getLastKeyLine() == state.getLineIndex() && !state.getLexerState().isFlowCollection()) {
                throw new RuntimeException("Two block mapping keys cannot be defined in the same line");
            }

            // In a block scalar, if there is a mapping key as in the same line as a mapping value,
            // then that mapping value does not correspond to the mapping key. the mapping value forms a
            // new mapping pair which represents the explicit key.
            if (state.getLastExplicitKeyLine() == state.getLineIndex() && !state.getLexerState().isFlowCollection()) {
                throw new RuntimeException("Mappings are not allowed as keys for explicit keys");
            }
            state.setLastKeyLine(state.getLineIndex());

            if (state.isExplicitDoc()) {
                throw new RuntimeException("'${lexer:PLANAR_CHAR}' token cannot " +
                        "start in the same line as the directive marker");
            }

            separate(state);
            if (state.isEmptyKey() && (option == EXPECT_MAP_VALUE || option == EXPECT_SEQUENCE_VALUE)) {
                state.setEmptyKey(false);
                state.getEventBuffer().add(new ParserEvent.ScalarEvent());
            } else if (option == EXPECT_MAP_VALUE) {
                    buffer = constructEvent(new ParserEvent.ScalarEvent(), newNodeTagStructure);
            } else if (option == EXPECT_SEQUENCE_ENTRY || option == EXPECT_SEQUENCE_VALUE
                        && state.getLexerState().isFlowCollection()) {
                    buffer = new ParserEvent.StartEvent(Collection.MAPPING, false, true);
            }
        } else {
            // There is already tag properties defined and the value is not a key
            if (definedProperties != null) {
                if (definedProperties.anchor != null && tagStructure.anchor != null) {
                    throw new RuntimeException("Only one anchor is allowed for a node");
                }
                if (definedProperties.tag != null && tagStructure.tag != null) {
                    throw new RuntimeException("Only one tag is allowed for a node");
                }
            }

            if (option == EXPECT_MAP_KEY && !explicitKey) {
                throw new RuntimeException("Expected a key for the block mapping");
            }

            if (explicitKey) {
                Indentation peekedIndentation = state.getBufferedToken().getIndentation();
                if (peekedIndentation != null
                        && peekedIndentation.getChange() == Indentation.IndentationChange.INDENT_INCREASE
                        && state.getBufferedToken().getType() != MAPPING_KEY) {
                    throw new RuntimeException("Invalid explicit key");
                }
            }
        }

        if (indentation != null && !state.isIndentationProcessed()) {
            int collectionSize = indentation.getCollection().size();
            switch (indentation.getChange()) {
                case INDENT_INCREASE -> { // Increased
                    // Block sequence
                    if (event.getKind() == ParserEvent.EventKind.START_EVENT
                            && ((ParserEvent.StartEvent) event).getStartType() == SEQUENCE) {
                        return constructEvent(
                                new ParserEvent.StartEvent(indentation.getCollection().remove(collectionSize - 1)),
                                tagStructure);
                    }
                    // Block mapping
                    buffer = constructEvent(
                            new ParserEvent.StartEvent(indentation.getCollection().remove(collectionSize - 1)),
                            newNodeTagStructure);
                }
                case INDENT_DECREASE -> { // Decreased
                    buffer = new ParserEvent.EndEvent(indentation.getCollection().remove(collectionSize - 1));
                    for (Collection collection: indentation.getCollection()) {
                        state.getEventBuffer().add(new ParserEvent.EndEvent(collection));
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

    private static ParserEvent constructEvent(ParserEvent parserEvent, TagStructure newNodeTagStructure) {
        ParserEvent event = parserEvent.clone();
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
    private static ParserEvent content(ParserState state, boolean peeked, boolean explicitKey,
                                       TagStructure tagStructure) {
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
                return new ParserEvent.ScalarEvent(value);
            }
            case DOUBLE_QUOTE_DELIMITER -> {
                state.getLexerState().setJsonKey(true);
                String value = doubleQuoteScalar(state);
                checkEmptyKey(state);
                return new ParserEvent.ScalarEvent(value);
            }
            case PLANAR_CHAR -> {
                String value = planarScalar(state);
                checkEmptyKey(state);
                return new ParserEvent.ScalarEvent(value);
            }
            case SEQUENCE_START -> {
                return new ParserEvent.StartEvent(SEQUENCE);
            }
            case SEQUENCE_ENTRY -> {
                if (state.isTagPropertiesInLine()) {
                   throw new RuntimeException("'-' cannot be defined after tag properties");
                }

                switch (state.getCurrentToken().getIndentation().getChange()) {
                    case INDENT_INCREASE -> {
                        return new ParserEvent.StartEvent(SEQUENCE);
                    }
                    case INDENT_NO_CHANGE -> {
                        return new ParserEvent.ScalarEvent();
                    }
                    case INDENT_DECREASE -> {
                        state.setIndentationProcessed(true);
                        for (Collection collection: state.getCurrentToken().getIndentation().getCollection()) {
                            state.getEventBuffer().add(new ParserEvent.EndEvent(collection));
                        }
                        return constructEvent(new ParserEvent.ScalarEvent(), tagStructure);
                    }
                }
            }
            case MAPPING_START -> {
                return new ParserEvent.StartEvent(Collection.MAPPING);
            }
            case LITERAL, FOLDED -> {
                if (state.getLexerState().isFlowCollection()) {
                    throw new RuntimeException("Cannot have a block node inside a flow node");
                }
                String value = blockScalar(state, state.getCurrentToken().getType() == FOLDED);
                checkEmptyKey(state);
                return new ParserEvent.ScalarEvent(value);
            }
            case ALIAS -> {
                return new ParserEvent.AliasEvent(state.getCurrentToken().getValue());
            }
            case ANCHOR, TAG, TAG_HANDLE -> {
                ParserEvent event = nodeComplete(state, EXPECT_MAP_KEY, tagStructure);
                if (explicitKey) {
                    return event;
                }
                if (event.getKind() == ParserEvent.EventKind.START_EVENT &&
                        ((ParserEvent.StartEvent) event).getStartType() == Collection.MAPPING) {
                    return new ParserEvent.StartEvent(Collection.MAPPING);
                }
                if (event.getKind() == ParserEvent.EventKind.END_EVENT) {
                    state.getEventBuffer().add(0, event);
                    return new ParserEvent.ScalarEvent();
                }
            }
            case MAPPING_END -> {
                if (explicitKey) {
                    state.getEventBuffer().add(new ParserEvent.ScalarEvent());
                }
                state.getEventBuffer().add(new ParserEvent.EndEvent(Collection.MAPPING));
                return new ParserEvent.ScalarEvent();
            }
        }

        return new ParserEvent.ScalarEvent();
    }

    /**
     * Parse the string of a block scalar.
     *
     * @param state - Current parser state
     * @param isFolded - If set, then the parses folded block scalar. Else, parses literal block scalar.
     * @return - Parsed block scalar value
     */
    private static String blockScalar(ParserState state, boolean isFolded) {
        String chompingIndicator = "";
        state.getLexerState().updateLexerState(LexerState.LEXER_BLOCK_HEADER);
        getNextToken(state);

        // Scan for block-header
        switch (state.getCurrentToken().getType()) {
            case CHOMPING_INDICATOR -> { // Strip and keep chomping indicators
                chompingIndicator = state.getCurrentToken().getValue();
                getNextToken(state, List.of(EOL));

                if (state.getLexerState().isEndOfStream()) {
                    try {
                        state.initLexer();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }

                }
            }
            case EOL -> { // Clip chomping indicator
                try {
                    state.initLexer();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
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
                    try {
                        state.initLexer();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }
                case EMPTY_LINE -> {
                    if (!isFirstLine) {
                        newLineBuffer.append("\n");
                    }
                    if (state.getLexerState().isEndOfStream()) {
                        terminated = true;
                        break;
                    }
                    try {
                        state.initLexer();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
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
                    try {
                        state.initLexer();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                    getNextToken(state);
                    getNextToken(state, true);

                    // Ignore the tokens inside trailing comments
                    Token.TokenType bufferedTokenType = state.getBufferedToken().getType();
                    while (bufferedTokenType == EOL || bufferedTokenType == EMPTY_LINE) {
                        // Terminate at the end of the line
                        if (state.getLineIndex() == state.getNumLines() - 1) {
                            break;
                        }
                        try {
                            state.initLexer();
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
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
    private static String planarScalar(ParserState state) {
        return planarScalar(state, true);
    }

    /**
     * Parse the string of a planar scalar.
     *
     * @param state - Current parser state.
     * @param allowTokensAsPlanar - If set, then the restricted tokens are allowed as a planar scalar
     * @return - Parsed planar scalar value
     */
    private static String planarScalar(ParserState state, boolean allowTokensAsPlanar) {
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
                    if ((!lexerState.isNewLine() && state.getLineIndex() >= state.getNumLines() - 1) ||
                            (lexerState.isNewLine() && lexerState.isEndOfStream())) {
                        terminate = true;
                        break;
                    }
                    try {
                        state.initLexer();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }

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
                    if (state.getLineIndex() == state.getNumLines() - 1) {
                        terminate = true;
                        break;
                    }
                    try {
                        state.initLexer();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
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
    private static String doubleQuoteScalar(ParserState state) {
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
                    try {
                        state.initLexer();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }

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
                    try {
                        state.initLexer();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }

                    boolean firstLineBuffer = state.getLexerState().isFirstLine();
                    state.getLexerState().setFirstLine(false);

                    getNextToken(state, true);
                    if (state.getBufferedToken().getType() == DOUBLE_QUOTE_DELIMITER && firstLineBuffer) {
                        lexemeBuffer += " ";
                    }
                    state.getLexerState().setFirstLine(false);
                }
                default -> {
                    throw new RuntimeException("Invalid double quote scalar");
                }
            }
            getNextToken(state);
        }

        verifyKey(state, state.getLexerState().isFirstLine());
        state.getLexerState().setFirstLine(true);
        return lexemeBuffer;
    }

    private static void checkEmptyKey(ParserState state) {
        separate(state);
        getNextToken(state, true);

        Token bufferedToken = state.getBufferedToken();

        if (bufferedToken.getType() != MAPPING_VALUE || bufferedToken.getIndentation() == null) {
            return;
        }

        state.setEmptyKey(true);
        Indentation indentation = bufferedToken.getIndentation();
        switch (indentation.getChange()) {
            case INDENT_INCREASE -> {
                int collectionSize = indentation.getCollection().size();
                state.getEventBuffer().add(
                        new ParserEvent.StartEvent(indentation.getCollection().remove(collectionSize - 1)));
            }
            case INDENT_DECREASE -> {
                for (Collection collection: indentation.getCollection()) {
                    state.getEventBuffer().add(new ParserEvent.EndEvent(collection));
                }
            }
        }
    }

    /** Parse the string of a single-quoted scalar.
     *
     * @param state - Current parser state
     * @return - Parsed single-quoted scalar value
     */
    private static String singleQuoteScalar(ParserState state) {
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
                    try {
                        state.initLexer();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }

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
                    try {
                        state.initLexer();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }

                    boolean firstLineBuffer = state.getLexerState().isFirstLine();
                    state.getLexerState().setFirstLine(false);

                    getNextToken(state, true);
                    if (state.getBufferedToken().getType() == SINGLE_QUOTE_DELIMITER && firstLineBuffer) {
                        lexemeBuffer += " ";
                    }
                    state.getLexerState().setFirstLine(false);
                }
                default -> {
                    throw new RuntimeException("Invalid single quote character");
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
    private static void verifyKey(ParserState state, boolean isSingleLine) {
        // Explicit keys can span multiple lines.
        if (state.isExplicitKey()) {
            return;
        }

        // Regular keys can only exist within one line
        state.getLexerState().updateLexerState(LexerState.LEXER_START_STATE);
        getNextToken(state, true);
        if (state.getBufferedToken().getType() == MAPPING_VALUE && !isSingleLine) {
            throw new RuntimeException("Mapping keys cannot span multiple lines");
        }
    }

    /**
     * Assert the next lexer token with the predicted token.
     *
     * @param state - Current parser state
     */
    public static void getNextToken(ParserState state) {
        getNextToken(state, List.of(Token.TokenType.DUMMY));
    }

    /**
     * Assert the next lexer token with the predicted token.
     *
     * @param state - Current parser state
     * @param peek Store the token in the buffer
     */
    public static void getNextToken(ParserState state, boolean peek) {
        getNextToken(state, List.of(Token.TokenType.DUMMY), peek);
    }

    /**
     * Assert the next lexer token with the predicted token.
     *
     * @param state - Current parser state
     * @param expectedTokens Predicted tokens
     */
    public static void getNextToken(ParserState state, List<Token.TokenType> expectedTokens) {
        getNextToken(state, expectedTokens, false);
    }

    /**
     * Assert the next lexer token with the predicted token.
     *
     * @param state - Current parser state
     * @param expectedTokens Predicted tokens
     * @param peek Store the token in the buffer
     */
    public static void getNextToken(ParserState state, List<Token.TokenType> expectedTokens, boolean peek) {
        Token token;

        // Obtain a token form the lexer if there is none in the buffer.
        if (state.getBufferedToken().getType() == Token.TokenType.DUMMY) {
            state.setPrevToken(state.getCurrentToken().getType());
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
            throw new RuntimeException("Expected token differ from the actual token");
        }
    }

    public static class TagStructure {
        String anchor = null;
        String tag = null;
    }
}
