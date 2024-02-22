package io.ballerina.stdlib.data.yaml.lexer;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import static io.ballerina.stdlib.data.yaml.lexer.Scanner.VERBATIM_URI_SCANNER;
import static io.ballerina.stdlib.data.yaml.lexer.Scanner.WHITE_SPACE_SCANNER;
import static io.ballerina.stdlib.data.yaml.lexer.Token.TokenType.*;
import static io.ballerina.stdlib.data.yaml.lexer.Utils.*;

public class YamlLexer {
    private final CharacterReader charReader;
    private final StateMachine stateMachine;

    public YamlLexer(Reader reader) {
        this.charReader = new CharacterReader(reader);
        this.stateMachine = new StateMachine(charReader);
    }

    public Token nextToken() {
        stateMachine.state.transition(stateMachine); // TODO: move this to state machine get Token logic
        return stateMachine.getToken();
    }


    static class StateMachine {

        private static final State LEXER_START_STATE = new StartState();
        private static final State LEXER_TAG_HANDLE_STATE = new TagHandleState();
        private static final State LEXER_TAG_PREFIX_STATE = new TagPrefixState();
        private static final State LEXER_NODE_PROPERTY = new NodePropertyState();
        private static final State LEXER_DIRECTIVE = new DirectiveState();
        private static final State LEXER_DOUBLE_QUOTE = new DoubleQuoteState();
        private static final State LEXER_SINGLE_QUOTE = new SingleQuoteState();
        private static final State LEXER_BLOCK_HEADER = new BlockHeaderState();
        private static final State LEXER_LITERAL = new LiteralState();
        private static final State LEXER_RESERVED_DIRECTIVE = new ReservedDirectiveState();

        private State state = LEXER_START_STATE;
        private CharacterReader characterReader;
        private Token.TokenType token = null;
        private String lexeme = "";
        private Indentation indentation = null;
        private int tabInWhitespace  = -1;
        private int numOpenedFlowCollections = 0;
        // Minimum indentation required to the current line
        private int indent = -1;
        private int indentStartIndex = -1;
        private boolean indentationBreak = false;
        private boolean enforceMapping = false;
        private boolean keyDefinedForLine = false;
        private List<Indent> indents = new ArrayList<>();
        private List<Token.TokenType> tokensForMappingValue = new ArrayList<>();
        private boolean allowTokensAsPlanar = false;
        private boolean isJsonKey = false;
        private int mappingKeyColumn = -1;
        private int addIndent = 1;
        private boolean captureIndent;
        private boolean firstLine = true;
        private boolean trailingComment = false;


        public StateMachine(CharacterReader characterReader) {
            this.characterReader = characterReader;
        }

        public int peek() {
            return peek(0);
        }

        public int peek(int k) {
            return characterReader.peek(k);
        }

        public void forward() {
            forward(1);
        }

        public void forward(int k) {
            characterReader.forward(k);
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
                tabInWhitespace = column;
            }
        }

        public Token getToken() {
            Token.TokenType tokenBuffer = token;
            token = Token.TokenType.DUMMY;
            String lexemeBuffer = lexeme;
            lexeme = "";
            Indentation indentationBuffer = indentation;
            indentation = null;
            return new Token(tokenBuffer, lexemeBuffer, indentationBuffer);
        }

        public void updateFirstTabIndex() {
            int column = getColumn();
            if (column < tabInWhitespace || tabInWhitespace < 0) {
                tabInWhitespace = column;
            }
        }

        public void tokenize(Token.TokenType token) {
            forward();
            this.token = token;
        }

        public int getColumn() {
            return characterReader.getColumn();
        }

        public int dataBufferSize() {
            return characterReader.getDataBufferSize();
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

        public void appendToLexeme(String value) {
            lexeme += value;
        }

        public void setKeyDefinedForLine(boolean keyDefinedForLine) {
            this.keyDefinedForLine = keyDefinedForLine;
        }

        public List<Indent> getIndents() {
            return indents;
        }

        public List<Token.TokenType> getTokensForMappingValue() {
            return tokensForMappingValue;
        }

        public void setIndentation(Indentation indentation) {
            this.indentation = indentation;
        }
    }


    interface State {
        State transition(StateMachine stateMachine);
    }

    private static class StartState implements State {

        @Override
        public State transition(StateMachine stateMachine) {
            boolean isFirstChar = stateMachine.getColumn() == 0;
            boolean startsWithWhiteSpace = false;

            if (Utils.WHITE_SPACE_PATTERN.pattern(stateMachine.peek())) {
                Scanner.iterate(stateMachine, Scanner.WHITE_SPACE_SCANNER, SEPARATION_IN_LINE);
                startsWithWhiteSpace = true;
            }

            if (stateMachine.isFlowCollection() && isFirstChar && stateMachine.peek() != -1) {
                IndentUtils.assertIndent(stateMachine); // TODO: catch the error
                if (IndentUtils.isTabInIndent(stateMachine, stateMachine.getIndent())) {
                    // throw new Exception("Cannot have tabs as an indentation"); // TODO
                }
            }

            if (startsWithWhiteSpace) {
                if (stateMachine.peek() == -1 && isFirstChar) {
                    stateMachine.tokenize(EMPTY_LINE);
                }
                return this;
            }

            if (Utils.isComment(stateMachine)) {
                stateMachine.forward(-1);
                return this;
            }

            if (Utils.isMarker(stateMachine, true)) {
                stateMachine.tokenize(DIRECTIVE_MARKER);
                return this;
            }

            if (Utils.isMarker(stateMachine, false)) {
                stateMachine.tokenize(DOCUMENT_MARKER);
                return this;
            }

            switch (stateMachine.peek()) {
                case '-' -> {
                    // Scan for planar characters
                    if (discernPlanarFromIndicator(stateMachine)) {
                        stateMachine.updateStartIndex();
                        stateMachine.forward();
                        stateMachine.lexeme += "-";
                        IndentUtils.handleMappingValueIndent(stateMachine, PLANAR_CHAR, Scanner.PLANAR_CHAR_SCANNER);
                        return this;
                    }

                    if (stateMachine.indent < stateMachine.getColumn() && stateMachine.allowTokensAsPlanar) {
                        stateMachine.lexeme += "-";
                        stateMachine.forward();
                        Scanner.iterate(stateMachine, Scanner.PLANAR_CHAR_SCANNER, PLANAR_CHAR);
                        return this;
                    }

                    // Return block sequence entry
                    stateMachine.tokenize(SEQUENCE_ENTRY);
                    stateMachine.indentation = IndentUtils.handleIndent(stateMachine, -1);
                    return this;
                }
                case '*' -> {
                    stateMachine.updateStartIndex();
                    stateMachine.forward();
                    IndentUtils.handleMappingValueIndent(stateMachine, ALIAS, Scanner.ANCHOR_NAME_SCANNER);
                    return this;
                }
                case '%' -> { // Directive line
                    if (stateMachine.allowTokensAsPlanar) {
                        IndentUtils.assertIndent(stateMachine, 1);
                        stateMachine.forward();
                        stateMachine.lexeme += "%";
                        Scanner.iterate(stateMachine, Scanner.PLANAR_CHAR_SCANNER, PLANAR_CHAR);
                        return this;
                    }
                    stateMachine.forward();
                    Scanner.iterate(stateMachine, Scanner.PRINTABLE_CHAR_SCANNER, DIRECTIVE);
                    return this;
                }
                case '!' -> { // Node tags
                    // Check if the tag can be considered as a planar
                    if (stateMachine.allowTokensAsPlanar) {
                        IndentUtils.assertIndent(stateMachine, 1);
                        stateMachine.forward();
                        stateMachine.lexeme += "!";
                        Scanner.iterate(stateMachine, Scanner.PLANAR_CHAR_SCANNER, PLANAR_CHAR);
                        return this;
                    }

                    // Process the tag token
                    IndentUtils.assertIndent(stateMachine, 1, true);
                    stateMachine.updateStartIndex(TAG);
                    switch (stateMachine.peek(1)) {
                        case '<' -> { // Verbatim tag
                            stateMachine.forward(2);

                            if (stateMachine.peek() == '!' && stateMachine.peek(1) == '>') {
                                // TODO: throw an error "'verbatim tag' is not resolved. Hence, '!' is invalid"
                            }

                            int peek = stateMachine.peek();
                            if (peek != -1 && (URI_PATTERN.pattern(peek) || WORD_PATTERN.pattern(peek))) {
                                Scanner.iterate(stateMachine, VERBATIM_URI_SCANNER, TAG, true);
                                return this;
                            } else {
                                // TODO: throw an error "Expected a 'uri-char' after '<' in a 'verbatim tag'"
                            }
                        }
                        case ' ', -1, '\t' -> { // Non-specific tag
                            stateMachine.lexeme = "!";
                            stateMachine.tokenize(TAG);
                            return this;
                        }
                        case '!' -> { // Secondary tag handle
                            stateMachine.lexeme = "!!";
                            stateMachine.forward();
                            stateMachine.tokenize(TAG_HANDLE);
                            return this;
                        }
                        default ->  { // Check for primary and name tag handles
                            stateMachine.lexeme = "!";
                            stateMachine.forward();
                            Scanner.iterate(stateMachine, Scanner.DIFF_TAG_HANDLE_SCANNER, TAG_HANDLE, true);
                            return this;
                        }
                    }
                }
                case '&' -> {
//                    LexicalError? err = assertIndent(state, 1);
                    IndentUtils.assertIndent(stateMachine, 1);
                    if (stateMachine.allowTokensAsPlanar) {
                        stateMachine.forward();
                        stateMachine.lexeme += "&";
                        Scanner.iterate(stateMachine, Scanner.PLANAR_CHAR_SCANNER, PLANAR_CHAR);
                        return this;
                    }
                    IndentUtils.assertIndent(stateMachine, 1, true);
                    stateMachine.updateStartIndex(ANCHOR);
                    stateMachine.forward();
                    Scanner.iterate(stateMachine, Scanner.ANCHOR_NAME_SCANNER, ANCHOR);
                    return this;
                }
                case ':' -> {
                    if (!stateMachine.isJsonKey && Utils.discernPlanarFromIndicator(stateMachine)) {
                        stateMachine.lexeme += ":";
                        stateMachine.updateStartIndex();
                        stateMachine.forward();
                        IndentUtils.handleMappingValueIndent(stateMachine, PLANAR_CHAR, Scanner.PLANAR_CHAR_SCANNER);
                        return this;
                    }

                    // Capture the for empty key mapping values
                    if (!stateMachine.keyDefinedForLine && !stateMachine.isFlowCollection()) {
                        if (stateMachine.mappingKeyColumn != stateMachine.getColumn()
                                && !stateMachine.isFlowCollection() && stateMachine.mappingKeyColumn > -1) {
                            // TODO: throw error "'?' and ':' should have the same indentation"
                        }
                        if (stateMachine.mappingKeyColumn == -1) {
                            stateMachine.updateStartIndex();
                            stateMachine.keyDefinedForLine = true;
                            stateMachine.indentation = IndentUtils.handleIndent(stateMachine,
                                    stateMachine.indentStartIndex);
                        }
                        stateMachine.mappingKeyColumn = -1;
                    }

                    stateMachine.tokenize(MAPPING_VALUE);
                    return this;
                }
                case '?' -> {
                    if (Utils.discernPlanarFromIndicator(stateMachine)) {
                        stateMachine.lexeme += "?";
                        stateMachine.updateStartIndex();
                        stateMachine.forward();
                        IndentUtils.handleMappingValueIndent(stateMachine, PLANAR_CHAR, Scanner.PLANAR_CHAR_SCANNER);
                        return this;
                    }
                    stateMachine.mappingKeyColumn = stateMachine.getColumn();
                    stateMachine.tokenize(MAPPING_KEY);

                    // Capture the for empty key mapping values
                    if (!stateMachine.isFlowCollection()) {
                        stateMachine.indentation = IndentUtils.handleIndent(stateMachine,
                                stateMachine.getColumn() -1);
                    }
                    return this;
                }
                case '\"' -> { // Process double quote flow style value
                    stateMachine.updateStartIndex();
                    stateMachine.tokenize(DOUBLE_QUOTE_DELIMITER);
                    return this;
                }
                case '\'' -> {
                    stateMachine.updateStartIndex();
                    stateMachine.tokenize(SINGLE_QUOTE_DELIMITER);
                    return this;
                }
                case ',' -> {
                    stateMachine.tokenize(SEPARATOR);
                    return this;
                }
                case '[' -> {
                    IndentUtils.assertIndent(stateMachine, 1);
                    stateMachine.numOpenedFlowCollections += 1;
                    stateMachine.tokenize(SEQUENCE_START);
                    return this;
                }
                case ']' -> {
                    stateMachine.numOpenedFlowCollections -= 1;
                    stateMachine.tokenize(SEQUENCE_END);
                    return this;
                }
                case '{' -> {
                    IndentUtils.assertIndent(stateMachine, 1);
                    stateMachine.numOpenedFlowCollections += 1;
                    stateMachine.tokenize(MAPPING_START);
                    return this;
                }
                case '}' -> {
                    stateMachine.numOpenedFlowCollections -= 1;
                    stateMachine.tokenize(MAPPING_END);
                    return this;
                }
                case '|' -> {
                    stateMachine.addIndent = 1;
                    stateMachine.captureIndent = true;
                    stateMachine.tokenize(LITERAL);
                    return this;
                }
                case '>' -> { // Block scalars
                    stateMachine.addIndent = 1;
                    stateMachine.captureIndent = true;
                    stateMachine.tokenize(FOLDED);
                    return this;
                }
            }

            if (Utils.isPlainSafe(stateMachine)) { // TODO: update this
                IndentUtils.handleMappingValueIndent(stateMachine, PLANAR_CHAR, Scanner.PLANAR_CHAR_SCANNER);
            }

            throw new RuntimeException("Invalid yaml document");
        }
    }

    private static class TagHandleState implements State {

        @Override
        public State transition(StateMachine stateMachine) {

            // Check fo primary, secondary, and named tag handles
            if (stateMachine.peek() == '!') {
                switch (stateMachine.peek(1)) {
                    case ' ', '\t' -> { // Primary tag handle
                        stateMachine.lexeme = "!";
                        stateMachine.tokenize(TAG_HANDLE);
                        return this;
                    }
                    case '!' -> { // Secondary tag handle
                        stateMachine.lexeme = "!!";
                        stateMachine.forward();
                        stateMachine.tokenize(TAG_HANDLE);
                        return this;
                    }
                    case -1 -> {
                        throw new RuntimeException("Expected a separation in line after primary tag handle");
                    }
                    default -> { // Check for named tag handles
                        stateMachine.lexeme = "!";
                        stateMachine.forward();
                        Scanner.iterate(stateMachine, Scanner.TAG_HANDLE_SCANNER, TAG_HANDLE, true);
                        return this;
                    }
                }
            }

            // Check for separation-in-space before the tag prefix
            if (WHITE_SPACE_PATTERN.pattern(stateMachine.peek())) {
                Scanner.iterate(stateMachine, Scanner.WHITE_SPACE_SCANNER, SEPARATION_IN_LINE);
                return this;
            }

            throw new RuntimeException("Expected '!' to start the tag handle");
        }
    }

    private static class TagPrefixState implements State {

        /**
         * Scan the lexemes for YAML tag prefix directive.
         *
         * @param stateMachine - Current lexer state.
         */
        @Override
        public State transition(StateMachine stateMachine) {
            if (Utils.matchPattern(stateMachine, List.of(URI_PATTERN, WORD_PATTERN, new CharPattern('%')),
                    List.of(FLOW_INDICATOR_PATTERN))) {
                Scanner.iterate(stateMachine, Scanner.URI_SCANNER, TAG_PREFIX);
                return this;
            }

            // Check for tail separation-in-line
            if (WHITE_SPACE_PATTERN.pattern(stateMachine.peek())) {
                Scanner.iterate(stateMachine, Scanner.WHITE_SPACE_SCANNER, SEPARATION_IN_LINE);
                return this;
            }

            throw new RuntimeException("Invalid tag prefix character");
        }
    }

    private static class NodePropertyState implements State {

        /**
         * Scan the lexemes for tag node properties.
         *
         * @param stateMachine - Current lexer state.
         */
        @Override
        public State transition(StateMachine stateMachine) {
            // Scan the anchor node
            if (stateMachine.peek() == '&') {
                stateMachine.forward();
                Scanner.iterate(stateMachine, Scanner.ANCHOR_NAME_SCANNER, ANCHOR);
                return this;
            }

            // Match the tag with the tag character pattern
            if (Utils.isTagChar(stateMachine)) {
                Scanner.iterate(stateMachine, Scanner.TAG_CHARACTER_SCANNER, TAG);
                return this;
            }

            // Check for tail separation-in-line
            if (WHITE_SPACE_PATTERN.pattern(stateMachine.peek())) {
                Scanner.iterate(stateMachine, Scanner.WHITE_SPACE_SCANNER, SEPARATION_IN_LINE);
                return this;
            }

            throw new RuntimeException("Invalid character tag");
        }
    }

    private static class DirectiveState implements State {

        /**
         * Scan the lexemes for YAML version directive.
         *
         * @param stateMachine - Current lexer state.
         */
        @Override
        public State transition(StateMachine stateMachine) {
            // Check for decimal digits
            if (Utils.matchPattern(stateMachine, List.of(DECIMAL_PATTERN))) {
                Scanner.iterate(stateMachine, Scanner.DIGIT_SCANNER, DECIMAL);
                return this;
            }

            // Check for decimal point
            if (stateMachine.peek() == '.') {
                stateMachine.tokenize(DOT);
                return this;
            }

            // Check for tail separation-in-line
            if (WHITE_SPACE_PATTERN.pattern(stateMachine.peek())) {
                Scanner.iterate(stateMachine, Scanner.WHITE_SPACE_SCANNER, SEPARATION_IN_LINE);
                return this;
            }

            throw new RuntimeException("Invalid version number character");
        }
    }

    private static class DoubleQuoteState implements State {

        /**
         * Scan the lexemes for double-quoted scalars.
         *
         * @param stateMachine - Current lexer state.
         */
        @Override
        public State transition(StateMachine stateMachine) {
            if (Utils.isMarker(stateMachine, true)) {
                stateMachine.tokenize(DIRECTIVE_MARKER);
                return this;
            }
            if (Utils.isMarker(stateMachine, false)) {
                stateMachine.tokenize(DOCUMENT_MARKER);
                return this;
            }

            // Check for empty lines
            if (WHITE_SPACE_PATTERN.pattern(stateMachine.peek())) {
                String whitespace = getWhitespace(stateMachine);
                if (stateMachine.peek() == -1) {
                    stateMachine.tokenize(EMPTY_LINE);
                    return this;
                }
                if (stateMachine.firstLine) {
                    stateMachine.lexeme += whitespace;
                }
            }

            // Terminating delimiter
            if (stateMachine.peek() == '\"') {
                IndentUtils.handleMappingValueIndent(stateMachine, DOUBLE_QUOTE_DELIMITER);
                return this;
            }

            // Regular double quoted characters
            Scanner.iterate(stateMachine, Scanner.DOUBLE_QUOTE_CHAR_SCANNER, DOUBLE_QUOTE_CHAR);
            return this;
        }
    }

    private static class SingleQuoteState implements State {

        /**
         * Scan the lexemes for single-quoted scalars.
         *
         * @param stateMachine - Current lexer state.
         */
        @Override
        public State transition(StateMachine stateMachine) {
            if (Utils.isMarker(stateMachine, true)) {
                stateMachine.tokenize(DIRECTIVE_MARKER);
                return this;
            }
            if (Utils.isMarker(stateMachine, false)) {
                stateMachine.tokenize(DOCUMENT_MARKER);
                return this;
            }

            // Check for empty lines
            if (WHITE_SPACE_PATTERN.pattern(stateMachine.peek())) {
                String whitespace = getWhitespace(stateMachine);
                if (stateMachine.peek() == -1) {
                    stateMachine.tokenize(EMPTY_LINE);
                    return this;
                }
                if (stateMachine.firstLine) {
                    stateMachine.lexeme += whitespace;
                }
            }

            // Escaped single quote
            if (stateMachine.peek() == '\'' && stateMachine.peek(1) == '\'') {
                stateMachine.lexeme += "'";
                stateMachine.forward(2);
            }

            // Terminating delimiter
            if (stateMachine.peek() == '\'') {
                if (stateMachine.lexeme.length() > 0) {
//                    state.index -= 1; TODO: handle this case properly
                    stateMachine.tokenize(SINGLE_QUOTE_CHAR);
                    return this;
                }
                IndentUtils.handleMappingValueIndent(stateMachine, SINGLE_QUOTE_DELIMITER);
                return this;
            }

            // Regular single quoted characters
            Scanner.iterate(stateMachine, Scanner.SINGLE_QUOTE_CHAR_SCANNER, SINGLE_QUOTE_CHAR);
            return this;
        }
    }

    private static class BlockHeaderState implements State {

        /**
         * Scan the lexemes for block header of a block scalar.
         *
         * @param stateMachine - Current lexer state.
         */
        @Override
        public State transition(StateMachine stateMachine) {
            if (stateMachine.peek() == ' ') {
                Scanner.iterate(stateMachine, Scanner.WHITE_SPACE_SCANNER, SEPARATION_IN_LINE);
            }

            // Ignore any comments
            if (stateMachine.peek() == '#' && WHITE_SPACE_PATTERN.pattern(stateMachine.peek())) {
                // TODO: this if block is incorrect need to improve this
                stateMachine.forward(-1);
                stateMachine.tokenize(EOL);
                return this;
            }

            // Check for indentation indicators and adjust the current indent
            if (Utils.matchPattern(stateMachine, List.of(DECIMAL_PATTERN), List.of(new CharPattern('0')))) {
                stateMachine.captureIndent = false;
//                state.addIndent += <int>(check common:processTypeCastingError(int:fromString(<string>state.peek()))) - 1; TODO
                stateMachine.forward();
                return this.transition(stateMachine);
            }

            // If the indentation indicator is at the tail
            if (stateMachine.getColumn() >= stateMachine.dataBufferSize()) { // TODO: check this
                stateMachine.tokenize(EOL);
                return this;
            }

            // Check for chomping indicators
            if (checkCharacters(stateMachine, List.of('+', '-'))) {
                stateMachine.lexeme += Character.toString(stateMachine.peek());
                stateMachine.tokenize(CHOMPING_INDICATOR);
                return this;
            }

            throw new RuntimeException("Invalid block header");
        }
    }

    private static class LiteralState implements State {

        @Override
        public State transition(StateMachine stateMachine) {
            boolean hasSufficientIndent = true;

            int limit = stateMachine.indent + stateMachine.addIndent;
            for (int i = 0; i < limit -1; i++) {
                if (stateMachine.peek() != ' ') {
                    hasSufficientIndent = false;
                    break;
                }
                stateMachine.forward();
            }

            // There is no sufficient indent to consider printable characters
            if (!hasSufficientIndent) {
                if (Utils.isPlainSafe(stateMachine)) {
                    stateMachine.enforceMapping = true;
                    return StateMachine.LEXER_START_STATE.transition(stateMachine);
                }

                switch (stateMachine.peek()) {
                    case '#' -> { // Generate beginning of the trailing comment
                        if (!stateMachine.trailingComment && stateMachine.captureIndent) {
                            throw new RuntimeException("Block scalars with more-indented leading empty lines"
                                    + "must use an explicit indentation indicator");
                        }

                        stateMachine.forward(-1);
                        if (stateMachine.trailingComment) {
                            stateMachine.tokenize(EOL);
                        } else {
                            stateMachine.tokenize(TRAILING_COMMENT);
                        }
                        return this;
                    }
                    case '\'', '\"', '.' -> { // Possible flow scalar
                        stateMachine.enforceMapping = true;
                        return StateMachine.LEXER_START_STATE.transition(stateMachine);
                    }
                    case ':', '-' -> {
                        return StateMachine.LEXER_START_STATE.transition(stateMachine);
                    }
                    case -1 -> { // Empty lines are allowed in trailing comments
                        stateMachine.tokenize(EMPTY_LINE);
                        return this;
                    }
                    default -> { // Other characters are not allowed when the indentation is less
                        throw new RuntimeException("Insufficient indent to process literal characters");
                    }
                }
            }

            if (stateMachine.trailingComment) {
                while (true) {
                    switch (stateMachine.peek()) {
                        case ' ' -> { // Ignore whitespace
                            stateMachine.forward();
                        }
                        case '#' -> { // Generate beginning of the trailing comment
                            stateMachine.forward(-1);
                            stateMachine.tokenize(EOL);
                            return this;
                        }
                        case -1 -> { // Empty lines are allowed in trailing comments
                            stateMachine.tokenize(EMPTY_LINE);
                            return this;
                        }
                        default -> {
                            throw new RuntimeException("Invalid trailing comment");
                        }
                    }
                }
            }

            // Generate an empty lines that have less index.
            if (stateMachine.getColumn() >= stateMachine.dataBufferSize()) {
                stateMachine.tokenize(EMPTY_LINE);
                return this;
            }

            // Update the indent to the first line
            if (stateMachine.captureIndent) {
                int additionalIndent = 0;

                while (stateMachine.peek() == ' ') {
                    additionalIndent += 1;
                    stateMachine.forward();
                }

                stateMachine.addIndent += additionalIndent;
                if (stateMachine.getColumn() < stateMachine.dataBufferSize()) {
                    stateMachine.captureIndent = false;
                }
            }

            if (stateMachine.getColumn() >= stateMachine.dataBufferSize()) {
                stateMachine.tokenize(EMPTY_LINE);
                return this;
            }

            // Check for document end markers
            if ((stateMachine.peek() == '.' && stateMachine.peek(1) == '.' && stateMachine.peek(2) == '.')
                    || (stateMachine.peek() == '-' && stateMachine.peek(1) == '-' && stateMachine.peek(2) == '-')){
                return StateMachine.LEXER_START_STATE.transition(stateMachine);
            }

            // Scan printable character
            Scanner.iterate(stateMachine, Scanner.PRINTABLE_CHAR_WITH_WHITESPACE_SCANNER, PRINTABLE_CHAR);
            return this;
        }
    }

    private static class ReservedDirectiveState implements State {

        @Override
        public State transition(StateMachine stateMachine) {
            // Ignore comments
            if (Utils.isComment(stateMachine)) {
                stateMachine.forward(-1);
                stateMachine.tokenize(EOL);
                return this;
            }

            // Check for separation-in-line
            if (WHITE_SPACE_PATTERN.pattern(stateMachine.peek())) {
                Scanner.iterate(stateMachine, Scanner.WHITE_SPACE_SCANNER, SEPARATION_IN_LINE);
                return this;
            }

            // Scan printable character
            Scanner.iterate(stateMachine, Scanner.PRINTABLE_CHAR_SCANNER, PRINTABLE_CHAR);
            return this;
        }
    }
}
