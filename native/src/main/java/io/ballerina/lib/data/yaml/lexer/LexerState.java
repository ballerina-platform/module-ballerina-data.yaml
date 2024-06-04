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

package io.ballerina.lib.data.yaml.lexer;

import io.ballerina.lib.data.yaml.utils.Error;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import static io.ballerina.lib.data.yaml.lexer.Scanner.COMMENT_SCANNER;
import static io.ballerina.lib.data.yaml.lexer.Scanner.VERBATIM_URI_SCANNER;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.ALIAS;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.ANCHOR;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.CHOMPING_INDICATOR;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.COMMENT;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.DECIMAL;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.DIRECTIVE;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.DIRECTIVE_MARKER;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.DOCUMENT_MARKER;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.DOT;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.DOUBLE_QUOTE_CHAR;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.DOUBLE_QUOTE_DELIMITER;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.EMPTY_LINE;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.EOL;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.FOLDED;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.LITERAL;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.MAPPING_END;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.MAPPING_KEY;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.MAPPING_START;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.MAPPING_VALUE;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.PLANAR_CHAR;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.PRINTABLE_CHAR;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.SEPARATION_IN_LINE;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.SEPARATOR;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.SEQUENCE_END;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.SEQUENCE_ENTRY;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.SEQUENCE_START;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.SINGLE_QUOTE_CHAR;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.SINGLE_QUOTE_DELIMITER;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.TAG;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.TAG_HANDLE;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.TAG_PREFIX;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.TRAILING_COMMENT;
import static io.ballerina.lib.data.yaml.lexer.Utils.DECIMAL_PATTERN;
import static io.ballerina.lib.data.yaml.lexer.Utils.FLOW_INDICATOR_PATTERN;
import static io.ballerina.lib.data.yaml.lexer.Utils.URI_PATTERN;
import static io.ballerina.lib.data.yaml.lexer.Utils.WHITE_SPACE_PATTERN;
import static io.ballerina.lib.data.yaml.lexer.Utils.WORD_PATTERN;
import static io.ballerina.lib.data.yaml.lexer.Utils.checkCharacters;
import static io.ballerina.lib.data.yaml.lexer.Utils.discernPlanarFromIndicator;
import static io.ballerina.lib.data.yaml.lexer.Utils.getWhitespace;

/**
 * State of the YAML Lexer.
 *
 * @since 0.1.0
 */
public class LexerState {

    public static final State LEXER_START_STATE = new StartState();
    public static final State LEXER_TAG_HANDLE_STATE = new TagHandleState();
    public static final State LEXER_TAG_PREFIX_STATE = new TagPrefixState();
    public static final State LEXER_NODE_PROPERTY = new NodePropertyState();
    public static final State LEXER_DIRECTIVE = new DirectiveState();
    public static final State LEXER_DOUBLE_QUOTE = new DoubleQuoteState();
    public static final State LEXER_SINGLE_QUOTE = new SingleQuoteState();
    public static final State LEXER_BLOCK_HEADER = new BlockHeaderState();
    public static final State LEXER_LITERAL = new LiteralState();
    public static final State LEXER_RESERVED_DIRECTIVE = new ReservedDirectiveState();
    private State state = LEXER_START_STATE;
    private final CharacterReader characterReader;
    private Token.TokenType token = null;
    private String lexeme = "";
    private String lexemeBuffer = "";
    private IndentUtils.Indentation indentation = null;
    private int tabInWhitespace  = -1;
    private int numOpenedFlowCollections = 0;
    private int indent = -1;
    private int indentStartIndex = -1;
    private boolean indentationBreak = false;
    private boolean enforceMapping = false;
    private boolean keyDefinedForLine = false;
    private Stack<IndentUtils.Indent> indents = new Stack<>();
    private List<Token.TokenType> tokensForMappingValue = new ArrayList<>();
    private boolean isJsonKey = false;
    private int mappingKeyColumn = -1;
    private int addIndent = 1;
    private boolean captureIndent;
    private boolean firstLine = true;
    private boolean trailingComment = false;
    private boolean isNewLine = false;
    private boolean allowTokensAsPlanar = false;
    private int lastEscapedChar = -1;
    private boolean eofStream = false;

    public LexerState(CharacterReader characterReader) {
        this.characterReader = characterReader;
    }

    public int peek() {
        return peek(0);
    }

    public int peek(int k) {
        return characterReader.peek(k);
    }

    public void forward() {
        eofStream = characterReader.forward(1);
    }

    public void forward(int k) {
        eofStream = characterReader.forward(k);
    }

    public void updateStartIndex() {
        updateStartIndex(null);
    }

    public void updateStartIndex(Token.TokenType token) {
        if (token != null) {
            tokensForMappingValue.add(token);
        }
        int column = getColumn();
        if (column < indentStartIndex || indentStartIndex < 0) {
            indentStartIndex = column;
        }
    }

    public void resetState() {
        addIndent = 1;
        captureIndent = false;
        enforceMapping = false;
        indentStartIndex = -1;
        indent = -1;
        indents = new Stack<>();
        lexeme = "";
        state = LEXER_START_STATE;
    }

    public Token getToken() {
        Token.TokenType tokenBuffer = token;
        token = Token.TokenType.DUMMY;
        String lexemeBuffer = lexeme;
        lexeme = "";
        IndentUtils.Indentation indentationBuffer = indentation;
        indentation = null;
        return new Token(tokenBuffer, lexemeBuffer, indentationBuffer);
    }

    public State getState() {
        return state;
    }

    public void updateLexerState(State state) {
        this.state = state;
    }

    public void updateFirstTabIndex() {
        int column = getColumn();
        if (column < tabInWhitespace || tabInWhitespace < 0) {
            tabInWhitespace = column;
        }
    }

    public void tokenize(Token.TokenType token) {
        this.token = token;
    }

    public int getColumn() {
        return characterReader.getColumn();
    }

    public int getRemainingBufferedSize() {
        return characterReader.getRemainingBufferedSize();
    }

    public boolean isFlowCollection() {
        return numOpenedFlowCollections > 0;
    }

    public int getIndent() {
        return indent;
    }

    public void setIndent(int indent) {
        this.indent = indent;
    }

    public void setIndentationBreak(boolean indentationBreak) {
        this.indentationBreak = indentationBreak;
    }

    public int getTabInWhitespace() {
        return tabInWhitespace;
    }

    public boolean getEnforceMapping() {
        return enforceMapping;
    }

    public void setEnforceMapping(boolean enforceMapping) {
        this.enforceMapping = enforceMapping;
    }

    public int getIndentStartIndex() {
        return indentStartIndex;
    }

    public void setLexeme(String value) {
        lexeme = value;
    }

    public void appendToLexeme(String value) {
        lexeme += value;
    }

    public void setKeyDefinedForLine(boolean keyDefinedForLine) {
        this.keyDefinedForLine = keyDefinedForLine;
    }

    public Stack<IndentUtils.Indent> getIndents() {
        return indents;
    }

    public List<Token.TokenType> getClonedTokensForMappingValue() {
        return List.copyOf(tokensForMappingValue);
    }

    public void setIndentation(IndentUtils.Indentation indentation) {
        this.indentation = indentation;
    }

    public String getLexeme() {
        return lexeme;
    }

    public String getLexemeBuffer() {
        return lexemeBuffer;
    }

    public void setLexemeBuffer(String lexemeBuffer) {
        this.lexemeBuffer = lexemeBuffer;
    }

    public boolean isIndentationBreak() {
        return indentationBreak;
    }

    public boolean isNewLine() {
        return isNewLine;
    }

    public void setNewLine(boolean newLine) {
        isNewLine = newLine;
    }

    public boolean isJsonKey() {
        return isJsonKey;
    }

    public void setJsonKey(boolean jsonKey) {
        isJsonKey = jsonKey;
    }

    public boolean isTrailingComment() {
        return trailingComment;
    }

    public void setTrailingComment(boolean trailingComment) {
        this.trailingComment = trailingComment;
    }

    public boolean isFirstLine() {
        return firstLine;
    }

    public void setFirstLine(boolean firstLine) {
        this.firstLine = firstLine;
    }

    public boolean isAllowTokensAsPlanar() {
        return allowTokensAsPlanar;
    }

    public void setAllowTokensAsPlanar(boolean allowTokensAsPlanar) {
        this.allowTokensAsPlanar = allowTokensAsPlanar;
    }

    public boolean isEndOfStream() {
        return eofStream && characterReader.isEof();
    }

    public int getLastEscapedChar() {
        return lastEscapedChar;
    }

    public void setLastEscapedChar(int lastEscapedChar) {
        this.lastEscapedChar = lastEscapedChar;
    }

    public void setEofStream(boolean eofStream) {
        this.eofStream = eofStream;
    }

    public int getLine() {
        return characterReader.getLine();
    }

    public void updateNewLineProps() {
        lastEscapedChar = -1;
        indentStartIndex = -1;
        tokensForMappingValue = new ArrayList<>();
        tabInWhitespace = -1;
        isNewLine = false;
        keyDefinedForLine = false;
    }

    public interface State {
        State transition(LexerState lexerState) throws Error.YamlParserException;
    }

    private static class StartState implements State {

        /**
         * Scan the lexemes that are without any explicit context.
         *
         * @param lexerState Current lexer state
         * @return Updated state context
         */
        @Override
        public State transition(LexerState lexerState) throws Error.YamlParserException {
            boolean isFirstChar = lexerState.getColumn() == 0;
            boolean startsWithWhiteSpace = false;

            if (WHITE_SPACE_PATTERN.pattern(lexerState.peek())) {
                Scanner.iterate(lexerState, Scanner.WHITE_SPACE_SCANNER, SEPARATION_IN_LINE);
                startsWithWhiteSpace = true;
            }

            if (lexerState.isFlowCollection() && isFirstChar && lexerState.peek() != -1) {
                IndentUtils.assertIndent(lexerState);
                if (IndentUtils.isTabInIndent(lexerState, lexerState.getIndent())) {
                    throw new Error.YamlParserException("cannot have tabs as an indentation",
                            lexerState.getLine(), lexerState.getColumn());
                }
            }

            if (startsWithWhiteSpace) {
                if (lexerState.peek() == -1 && isFirstChar) {
                    lexerState.tokenize(EMPTY_LINE);
                }
                return this;
            }

            if (Utils.isComment(lexerState)) {
                Scanner.iterate(lexerState, COMMENT_SCANNER, COMMENT);
                lexerState.tokenize(COMMENT);
                return this;
            }

            if (Utils.isMarker(lexerState, true)) {
                lexerState.forward();
                lexerState.tokenize(DIRECTIVE_MARKER);
                return this;
            }

            if (Utils.isMarker(lexerState, false)) {
                lexerState.forward();
                lexerState.tokenize(DOCUMENT_MARKER);
                return this;
            }

            switch (lexerState.peek()) {
                case '-' -> {
                    // Scan for planar characters
                    if (discernPlanarFromIndicator(lexerState)) {
                        lexerState.updateStartIndex();
                        lexerState.forward();
                        lexerState.lexeme += "-";
                        IndentUtils.handleMappingValueIndent(lexerState, PLANAR_CHAR, Scanner.PLANAR_CHAR_SCANNER);
                        return this;
                    }

                    if (lexerState.indent < lexerState.getColumn() && lexerState.allowTokensAsPlanar) {
                        lexerState.lexeme += "-";
                        lexerState.forward();
                        Scanner.iterate(lexerState, Scanner.PLANAR_CHAR_SCANNER, PLANAR_CHAR);
                        return this;
                    }

                    // Return block sequence entry
                    lexerState.forward();
                    lexerState.tokenize(SEQUENCE_ENTRY);
                    lexerState.indentation = IndentUtils.handleIndent(lexerState);
                    return this;
                }
                case '*' -> {
                    lexerState.updateStartIndex();
                    lexerState.forward();
                    IndentUtils.handleMappingValueIndent(lexerState, ALIAS, Scanner.ANCHOR_NAME_SCANNER);
                    return this;
                }
                case '%' -> { // Directive line
                    if (lexerState.allowTokensAsPlanar) {
                        IndentUtils.assertIndent(lexerState, 1);
                        lexerState.lexeme += "%";
                        Scanner.iterate(lexerState, Scanner.PLANAR_CHAR_SCANNER, PLANAR_CHAR);
                        return this;
                    }
                    lexerState.forward();
                    Scanner.iterate(lexerState, Scanner.PRINTABLE_CHAR_SCANNER, DIRECTIVE);
                    return this;
                }
                case '!' -> { // Node tags
                    // Check if the tag can be considered as a planar
                    if (lexerState.allowTokensAsPlanar) {
                        IndentUtils.assertIndent(lexerState, 1);
                        lexerState.forward();
                        lexerState.lexeme += "!";
                        Scanner.iterate(lexerState, Scanner.PLANAR_CHAR_SCANNER, PLANAR_CHAR);
                        return this;
                    }

                    // Process the tag token
                    IndentUtils.assertIndent(lexerState, 1, true);
                    lexerState.updateStartIndex(TAG);
                    switch (lexerState.peek(1)) {
                        case '<' -> { // Verbatim tag
                            lexerState.forward(2);

                            if (lexerState.peek() == '!' && lexerState.peek(1) == '>') {
                                throw new Error.YamlParserException("'verbatim tag' is not resolved. " +
                                        "Hence, '!' is invalid", lexerState.getLine(), lexerState.getColumn());
                            }

                            int peek = lexerState.peek();
                            if (peek != -1 && (URI_PATTERN.pattern(peek) || WORD_PATTERN.pattern(peek))) {
                                Scanner.iterate(lexerState, VERBATIM_URI_SCANNER, TAG, true);
                                return this;
                            } else {
                                throw new Error.YamlParserException("expected a 'uri-char' " +
                                        "after '<' in a 'verbatim tag'", lexerState.getLine(), lexerState.getColumn());
                            }
                        }
                        case ' ', -1, '\t' -> { // Non-specific tag
                            lexerState.lexeme = "!";
                            lexerState.forward();
                            lexerState.tokenize(TAG);
                            return this;
                        }
                        case '!' -> { // Secondary tag handle
                            lexerState.lexeme = "!!";
                            lexerState.forward(2);
                            lexerState.tokenize(TAG_HANDLE);
                            return this;
                        }
                        default ->  { // Check for primary and name tag handles
                            lexerState.lexeme = "!";
                            Scanner.iterate(lexerState, Scanner.DIFF_TAG_HANDLE_SCANNER, TAG_HANDLE, true);
                            return this;
                        }
                    }
                }
                case '&' -> {
                    IndentUtils.assertIndent(lexerState, 1);
                    if (lexerState.allowTokensAsPlanar) {
                        lexerState.forward();
                        lexerState.lexeme += "&";
                        Scanner.iterate(lexerState, Scanner.PLANAR_CHAR_SCANNER, PLANAR_CHAR);
                        return this;
                    }
                    IndentUtils.assertIndent(lexerState, 1, true);
                    lexerState.updateStartIndex(ANCHOR);
                    lexerState.forward();
                    Scanner.iterate(lexerState, Scanner.ANCHOR_NAME_SCANNER, ANCHOR);
                    return this;
                }
                case ':' -> {
                    if (!lexerState.isJsonKey && discernPlanarFromIndicator(lexerState)) {
                        lexerState.lexeme += ":";
                        lexerState.updateStartIndex();
                        lexerState.forward();
                        IndentUtils.handleMappingValueIndent(lexerState, PLANAR_CHAR, Scanner.PLANAR_CHAR_SCANNER);
                        return this;
                    }

                    // Capture the for empty key mapping values
                    if (!lexerState.keyDefinedForLine && !lexerState.isFlowCollection()) {
                        if (lexerState.mappingKeyColumn != lexerState.getColumn()
                                && !lexerState.isFlowCollection() && lexerState.mappingKeyColumn > -1) {
                            throw new Error.YamlParserException("'?' and ':' should have the same indentation",
                                    lexerState.getLine(), lexerState.getColumn());
                        }
                        if (lexerState.mappingKeyColumn == -1) {
                            lexerState.updateStartIndex();
                            lexerState.keyDefinedForLine = true;
                            lexerState.indentation = IndentUtils.handleIndent(lexerState, lexerState.indentStartIndex);
                        }
                        lexerState.mappingKeyColumn = -1;
                    }
                    lexerState.forward();
                    lexerState.tokenize(MAPPING_VALUE);
                    return this;
                }
                case '?' -> {
                    if (discernPlanarFromIndicator(lexerState)) {
                        lexerState.lexeme += "?";
                        lexerState.updateStartIndex();
                        lexerState.forward();
                        IndentUtils.handleMappingValueIndent(lexerState, PLANAR_CHAR, Scanner.PLANAR_CHAR_SCANNER);
                        return this;
                    }
                    lexerState.mappingKeyColumn = lexerState.getColumn();
                    lexerState.forward();
                    lexerState.tokenize(MAPPING_KEY);

                    // Capture the for empty key mapping values
                    if (!lexerState.isFlowCollection()) {
                        lexerState.indentation = IndentUtils.handleIndent(lexerState,
                                lexerState.getColumn() - 1);
                    }
                    return this;
                }
                case '\"' -> { // Process double quote flow style value
                    lexerState.updateStartIndex();
                    lexerState.forward();
                    lexerState.tokenize(DOUBLE_QUOTE_DELIMITER);
                    return this;
                }
                case '\'' -> {
                    lexerState.updateStartIndex();
                    lexerState.forward();
                    lexerState.tokenize(SINGLE_QUOTE_DELIMITER);
                    return this;
                }
                case ',' -> {
                    lexerState.forward();
                    lexerState.tokenize(SEPARATOR);
                    return this;
                }
                case '[' -> {
                    IndentUtils.assertIndent(lexerState, 1);
                    lexerState.numOpenedFlowCollections += 1;
                    lexerState.forward();
                    lexerState.tokenize(SEQUENCE_START);
                    return this;
                }
                case ']' -> {
                    lexerState.numOpenedFlowCollections -= 1;
                    lexerState.forward();
                    lexerState.tokenize(SEQUENCE_END);
                    return this;
                }
                case '{' -> {
                    IndentUtils.assertIndent(lexerState, 1);
                    lexerState.numOpenedFlowCollections += 1;
                    lexerState.forward();
                    lexerState.tokenize(MAPPING_START);
                    return this;
                }
                case '}' -> {
                    lexerState.numOpenedFlowCollections -= 1;
                    lexerState.forward();
                    lexerState.tokenize(MAPPING_END);
                    return this;
                }
                case '|' -> {
                    lexerState.addIndent = 1;
                    lexerState.captureIndent = true;
                    lexerState.forward();
                    lexerState.tokenize(LITERAL);
                    return this;
                }
                case '>' -> { // Block scalars
                    lexerState.addIndent = 1;
                    lexerState.captureIndent = true;
                    lexerState.forward();
                    lexerState.tokenize(FOLDED);
                    return this;
                }
            }

            if (Utils.isPlainSafe(lexerState)) {
                IndentUtils.handleMappingValueIndent(lexerState, PLANAR_CHAR, Scanner.PLANAR_CHAR_SCANNER);
                return this;
            }

            throw new Error.YamlParserException("invalid yaml document", lexerState.getLine(), lexerState.getColumn());
        }
    }

    private static class TagHandleState implements State {

        /**
         * Scan the lexemes for YAML tag handle directive.
         *
         * @param lexerState - Current lexer state
         * @return Updated lexer state
         */
        @Override
        public State transition(LexerState lexerState) throws Error.YamlParserException {

            // Check fo primary, secondary, and named tag handles
            if (lexerState.peek() == '!') {
                switch (lexerState.peek(1)) {
                    case ' ', '\t' -> { // Primary tag handle
                        lexerState.lexeme = "!";
                        lexerState.forward();
                        lexerState.tokenize(TAG_HANDLE);
                        return this;
                    }
                    case '!' -> { // Secondary tag handle
                        lexerState.lexeme = "!!";
                        lexerState.forward(2);
                        lexerState.tokenize(TAG_HANDLE);
                        return this;
                    }
                    case -1 -> {
                        throw new Error.YamlParserException("expected a separation in line after primary tag handle",
                                lexerState.getLine(), lexerState.getColumn());
                    }
                    default -> { // Check for named tag handles
                        lexerState.lexeme = "!";
                        lexerState.forward();
                        Scanner.iterate(lexerState, Scanner.TAG_HANDLE_SCANNER, TAG_HANDLE, true);
                        return this;
                    }
                }
            }

            // Check for separation-in-space before the tag prefix
            if (WHITE_SPACE_PATTERN.pattern(lexerState.peek())) {
                Scanner.iterate(lexerState, Scanner.WHITE_SPACE_SCANNER, SEPARATION_IN_LINE);
                return this;
            }

            throw new Error.YamlParserException("expected '!' to start the tag handle",
                    lexerState.getLine(), lexerState.getColumn());
        }
    }

    private static class TagPrefixState implements State {

        /**
         * Scan the lexemes for YAML tag prefix directive.
         *
         * @param lexerState - Current lexer state.
         */
        @Override
        public State transition(LexerState lexerState) throws Error.YamlParserException {
            if (Utils.matchPattern(lexerState, List.of(URI_PATTERN, WORD_PATTERN, new Utils.CharPattern('%')),
                    List.of(FLOW_INDICATOR_PATTERN))) {
                Scanner.iterate(lexerState, Scanner.URI_SCANNER, TAG_PREFIX);
                return this;
            }

            // Check for tail separation-in-line
            if (WHITE_SPACE_PATTERN.pattern(lexerState.peek())) {
                Scanner.iterate(lexerState, Scanner.WHITE_SPACE_SCANNER, SEPARATION_IN_LINE);
                return this;
            }

            throw new Error.YamlParserException("invalid tag prefix character",
                    lexerState.getLine(), lexerState.getColumn());
        }
    }

    private static class NodePropertyState implements State {

        /**
         * Scan the lexemes for tag node properties.
         *
         * @param lexerState - Current lexer state.
         */
        @Override
        public State transition(LexerState lexerState) throws Error.YamlParserException {
            // Scan the anchor node
            if (lexerState.peek() == '&') {
                lexerState.forward();
                Scanner.iterate(lexerState, Scanner.ANCHOR_NAME_SCANNER, ANCHOR);
                return this;
            }

            // Match the tag with the tag character pattern
            if (Utils.isTagChar(lexerState)) {
                Scanner.iterate(lexerState, Scanner.TAG_CHARACTER_SCANNER, TAG);
                return this;
            }

            // Check for tail separation-in-line
            if (WHITE_SPACE_PATTERN.pattern(lexerState.peek())) {
                Scanner.iterate(lexerState, Scanner.WHITE_SPACE_SCANNER, SEPARATION_IN_LINE);
                return this;
            }

            throw new Error.YamlParserException("invalid character tag", lexerState.getLine(), lexerState.getColumn());
        }
    }

    private static class DirectiveState implements State {

        /**
         * Scan the lexemes for YAML version directive.
         *
         * @param lexerState - Current lexer state.
         */
        @Override
        public State transition(LexerState lexerState) throws Error.YamlParserException {
            // Check for decimal digits
            if (Utils.matchPattern(lexerState, List.of(DECIMAL_PATTERN))) {
                Scanner.iterate(lexerState, Scanner.DIGIT_SCANNER, DECIMAL);
                return this;
            }

            // Check for decimal point
            if (lexerState.peek() == '.') {
                lexerState.forward();
                lexerState.tokenize(DOT);
                return this;
            }

            // Check for tail separation-in-line
            if (WHITE_SPACE_PATTERN.pattern(lexerState.peek())) {
                Scanner.iterate(lexerState, Scanner.WHITE_SPACE_SCANNER, SEPARATION_IN_LINE);
                return this;
            }

            throw new Error.YamlParserException("invalid version number character",
                    lexerState.getLine(), lexerState.getColumn());
        }
    }

    private static class DoubleQuoteState implements State {

        /**
         * Scan the lexemes for double-quoted scalars.
         *
         * @param lexerState - Current lexer state.
         */
        @Override
        public State transition(LexerState lexerState) throws Error.YamlParserException {
            if (Utils.isMarker(lexerState, true)) {
                lexerState.forward();
                lexerState.tokenize(DIRECTIVE_MARKER);
                return this;
            }
            if (Utils.isMarker(lexerState, false)) {
                lexerState.forward();
                lexerState.tokenize(DOCUMENT_MARKER);
                return this;
            }

            // Check for empty lines
            if (WHITE_SPACE_PATTERN.pattern(lexerState.peek())) {
                String whitespace = getWhitespace(lexerState);
                if (lexerState.peek() == -1) {
                    lexerState.forward();
                    lexerState.tokenize(EMPTY_LINE);
                    return this;
                }
                if (lexerState.firstLine) {
                    lexerState.lexeme += whitespace;
                }
            }

            // Terminating delimiter
            if (lexerState.peek() == '\"') {
                IndentUtils.handleMappingValueIndent(lexerState, DOUBLE_QUOTE_DELIMITER);
                return this;
            }

            // Regular double quoted characters
            Scanner.iterate(lexerState, Scanner.DOUBLE_QUOTE_CHAR_SCANNER, DOUBLE_QUOTE_CHAR);
            return this;
        }
    }

    private static class SingleQuoteState implements State {

        /**
         * Scan the lexemes for single-quoted scalars.
         *
         * @param lexerState - Current lexer state.
         */
        @Override
        public State transition(LexerState lexerState) throws Error.YamlParserException {
            if (Utils.isMarker(lexerState, true)) {
                lexerState.forward();
                lexerState.tokenize(DIRECTIVE_MARKER);
                return this;
            }
            if (Utils.isMarker(lexerState, false)) {
                lexerState.forward();
                lexerState.tokenize(DOCUMENT_MARKER);
                return this;
            }

            // Check for empty lines
            if (WHITE_SPACE_PATTERN.pattern(lexerState.peek())) {
                String whitespace = getWhitespace(lexerState);
                if (lexerState.peek() == -1) {
                    lexerState.forward();
                    lexerState.tokenize(EMPTY_LINE);
                    return this;
                }
                if (lexerState.firstLine) {
                    lexerState.lexeme += whitespace;
                }
            }

            // Escaped single quote
            if (lexerState.peek() == '\'' && lexerState.peek(1) == '\'') {
                lexerState.lexeme += "'";
                lexerState.forward(2);
            }

            // Terminating delimiter
            if (lexerState.peek() == '\'') {
                if (lexerState.lexeme.length() > 0) {
                    lexerState.tokenize(SINGLE_QUOTE_CHAR);
                    return this;
                }
                IndentUtils.handleMappingValueIndent(lexerState, SINGLE_QUOTE_DELIMITER);
                return this;
            }

            // Regular single quoted characters
            Scanner.iterate(lexerState, Scanner.SINGLE_QUOTE_CHAR_SCANNER, SINGLE_QUOTE_CHAR);
            return this;
        }
    }

    private static class BlockHeaderState implements State {

        /**
         * Scan the lexemes for block header of a block scalar.
         *
         * @param lexerState - Current lexer state.
         */
        @Override
        public State transition(LexerState lexerState) throws Error.YamlParserException {
            if (lexerState.peek() == ' ') {
                Scanner.iterate(lexerState, Scanner.WHITE_SPACE_SCANNER, SEPARATION_IN_LINE);
            }

            // Ignore any comments
            if (lexerState.peek() == '#' && WHITE_SPACE_PATTERN.pattern(lexerState.peek())) {
                lexerState.tokenize(EOL);
                return this;
            }

            // Check for indentation indicators and adjust the current indent
            if (Utils.matchPattern(lexerState, List.of(DECIMAL_PATTERN), List.of(new Utils.CharPattern('0')))) {
                lexerState.captureIndent = false;
                int numericValue = Character.getNumericValue(lexerState.peek());
                if (numericValue == -1 || numericValue == -2) {
                    throw new Error.YamlParserException("invalid numeric value",
                            lexerState.getLine(), lexerState.getColumn());
                }
                lexerState.addIndent += numericValue;
                lexerState.forward();
                return this.transition(lexerState);
            }

            // If the indentation indicator is at the tail
            if (lexerState.getColumn() >= lexerState.getRemainingBufferedSize()) {
                lexerState.forward();
                lexerState.tokenize(EOL);
                return this;
            }

            // Check for chomping indicators
            if (checkCharacters(lexerState, List.of('+', '-'))) {
                lexerState.lexeme += Character.toString(lexerState.peek());
                lexerState.forward();
                lexerState.tokenize(CHOMPING_INDICATOR);
                return this;
            }

            throw new Error.YamlParserException("invalid block header", lexerState.getLine(), lexerState.getColumn());
        }
    }

    private static class LiteralState implements State {

        /**
         * Scan the lexemes for block scalar.
         *
         * @param lexerState - Current lexer state
         */
        @Override
        public State transition(LexerState lexerState) throws Error.YamlParserException {
            boolean hasSufficientIndent = true;

            int limit = lexerState.indent + lexerState.addIndent;
            // Check if the line has sufficient indent to be process as a block scalar.
            for (int i = 0; i < limit - 1; i++) {
                if (lexerState.peek() != ' ') {
                    hasSufficientIndent = false;
                    break;
                }
                lexerState.forward();
            }

            // There is no sufficient indent to consider printable characters
            if (!hasSufficientIndent) {
                if (Utils.isPlainSafe(lexerState)) {
                    lexerState.enforceMapping = true;
                    return LexerState.LEXER_START_STATE.transition(lexerState);
                }

                switch (lexerState.peek()) {
                    case '#' -> { // Generate beginning of the trailing comment
                        if (!lexerState.trailingComment && lexerState.captureIndent) {
                            throw new Error.YamlParserException("Block scalars with more-indented leading empty lines"
                                    + "must use an explicit indentation indicator",
                                    lexerState.getLine(), lexerState.getColumn());
                        }

                        if (lexerState.trailingComment) {
                            lexerState.tokenize(EOL);
                        } else {
                            lexerState.tokenize(TRAILING_COMMENT);
                        }
                        return this;
                    }
                    case '\'', '\"', '.' -> { // Possible flow scalar
                        lexerState.enforceMapping = true;
                        return LexerState.LEXER_START_STATE.transition(lexerState);
                    }
                    case ':', '-' -> {
                        return LexerState.LEXER_START_STATE.transition(lexerState);
                    }
                    case -1 -> { // Empty lines are allowed in trailing comments
                        lexerState.forward();
                        lexerState.tokenize(EMPTY_LINE);
                        return this;
                    }
                    default -> { // Other characters are not allowed when the indentation is less
                        throw new Error.YamlParserException("insufficient indent to process literal characters",
                                lexerState.getLine(), lexerState.getColumn());
                    }
                }
            }

            if (lexerState.trailingComment) {
                while (true) {
                    switch (lexerState.peek()) {
                        case ' ' -> { // Ignore whitespace
                            lexerState.forward();
                        }
                        case '#' -> { // Generate beginning of the trailing comment
                            lexerState.tokenize(EOL);
                            return this;
                        }
                        case -1 -> { // Empty lines are allowed in trailing comments
                            lexerState.forward();
                            lexerState.tokenize(EMPTY_LINE);
                            return this;
                        }
                        default -> {
                            throw new Error.YamlParserException("invalid trailing comment",
                                    lexerState.getLine(), lexerState.getColumn());
                        }
                    }
                }
            }

            // Generate an empty lines that have less index.
            if (lexerState.getColumn() >= lexerState.getRemainingBufferedSize()) {
                lexerState.forward();
                lexerState.tokenize(EMPTY_LINE);
                return this;
            }

            // Update the indent to the first line
            if (lexerState.captureIndent) {
                int additionalIndent = 0;

                while (lexerState.peek() == ' ') {
                    additionalIndent += 1;
                    lexerState.forward();
                }

                lexerState.addIndent += additionalIndent;
                if (lexerState.getColumn() < lexerState.getRemainingBufferedSize()) {
                    lexerState.captureIndent = false;
                }
            }

            if (lexerState.getColumn() >= lexerState.getRemainingBufferedSize()) {
                lexerState.forward();
                lexerState.tokenize(EMPTY_LINE);
                return this;
            }

            // Check for document end markers
            if ((lexerState.peek() == '.' && lexerState.peek(1) == '.' && lexerState.peek(2) == '.')
                    || (lexerState.peek() == '-' && lexerState.peek(1) == '-' && lexerState.peek(2) == '-')) {
                return LexerState.LEXER_START_STATE.transition(lexerState);
            }

            // Scan printable character
            Scanner.iterate(lexerState, Scanner.PRINTABLE_CHAR_WITH_WHITESPACE_SCANNER, PRINTABLE_CHAR);
            return this;
        }
    }

    private static class ReservedDirectiveState implements State {

        /**
         * Scan the lexemes for reserved directive.
         *
         * @param lexerState - Current lexer state
         */
        @Override
        public State transition(LexerState lexerState) throws Error.YamlParserException {
            // Ignore comments
            if (Utils.isComment(lexerState)) {
                lexerState.tokenize(EOL);
                return this;
            }

            // Check for separation-in-line
            if (WHITE_SPACE_PATTERN.pattern(lexerState.peek())) {
                Scanner.iterate(lexerState, Scanner.WHITE_SPACE_SCANNER, SEPARATION_IN_LINE);
                return this;
            }

            // Scan printable character
            Scanner.iterate(lexerState, Scanner.PRINTABLE_CHAR_SCANNER, PRINTABLE_CHAR);
            return this;
        }
    }
}
