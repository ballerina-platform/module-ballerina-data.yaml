package io.ballerina.stdlib.data.yaml.lexer;

import java.util.List;

import static io.ballerina.stdlib.data.yaml.lexer.Utils.*;

public class Scanner {

    public static final Scan ANCHOR_NAME_SCANNER = new AnchorNameScanner();
    public static final Scan PLANAR_CHAR_SCANNER = new PlanarCharScanner();
    public static final Scan URI_SCANNER = new UriScanner(false);
    public static final Scan VERBATIM_URI_SCANNER = new UriScanner(true);
    public static final Scan PRINTABLE_CHAR_WITH_WHITESPACE_SCANNER = new PrintableCharScanner(true);
    public static final Scan PRINTABLE_CHAR_SCANNER = new PrintableCharScanner(false);
    public static final Scan DIFF_TAG_HANDLE_SCANNER = new TagHandleScanner(true);
    public static final Scan TAG_HANDLE_SCANNER = new TagHandleScanner(false);
    public static final Scan WHITE_SPACE_SCANNER = new WhiteSpaceScanner();
    public static final Scan TAG_CHARACTER_SCANNER = new TagCharacterScanner();
    public static final Scan DIGIT_SCANNER = new DigitScanner();
    public static final Scan DOUBLE_QUOTE_CHAR_SCANNER = new DoubleQuoteCharScanner();
    public static final Scan SINGLE_QUOTE_CHAR_SCANNER = new SingleQuoteCharScanner();

    public static void iterate(YamlLexer.StateMachine sm, Scan scan, Token.TokenType token) {
        iterate(sm, scan, token, false);
    }
    public static void iterate(YamlLexer.StateMachine sm, Scan scan, Token.TokenType token, boolean include) {
        while (sm.getColumn() < sm.dataBufferSize()) { // TODO: check for usage of column or pointer for this case
            if (scan.scan(sm)) {
                // state.index = include ? state.index : state.index - 1;
                sm.tokenize(token);
                return;
            }
            sm.forward();
        }
        // state.index = state.line.length() - 1;
        sm.tokenize(token);
    }

    interface Scan {
        boolean scan(YamlLexer.StateMachine sm);
    }

    public static class SingleQuoteCharScanner implements Scan {

        /**
         *  Process double quoted scalar values.
         */
        @Override
        public boolean scan(YamlLexer.StateMachine sm) {
            // Process nb-json characters
            if (matchPattern(sm, List.of(JSON_PATTERN), List.of(new CharPattern('\'')))) {
                sm.appendToLexeme(Character.toString(sm.peek()));
                return false;
            }

            // Terminate when the delimiter is found
            if (sm.peek() == '\'') {
                if (sm.peek(1) == '\'') {
                    sm.appendToLexeme("'");
                    sm.forward();
                    return false;
                }
                return true;
            }

            throw new RuntimeException("Invalid single quote char");
        }
    }

    /**
     * Process double quoted scalar values.
     */
    public static class DoubleQuoteCharScanner implements Scan {

        @Override
        public boolean scan(YamlLexer.StateMachine sm) {
            // Process nb-json characters
            if (matchPattern(sm, List.of(JSON_PATTERN),
                    List.of(new CharPattern('\\'), new CharPattern('\"')))) {
                sm.appendToLexeme(Character.toString(sm.peek()));
                return false;
            }

            // Process escaped characters
            if (sm.peek() == '\\') {
                sm.forward();
                escapedCharacterScan(sm);
//                state.lastEscapedChar = state.lexeme.length() - 1; // TODO:
                return false;
            }

            // Terminate when delimiter is found
            if (sm.peek() == '\"') {
                return true;
            }

            throw new RuntimeException("Invalid double quoted character");
        }
    }

    /**
     * Scan lexemes for the escaped characters.
     * Adds the processed escaped character to the lexeme.
     */
    public static void escapedCharacterScan(YamlLexer.StateMachine sm) {
        int currentChar;

        // Process double escape character
        if (sm.peek() == -1) {
            sm.appendToLexeme("\\");
            return;
        } else {
            currentChar = sm.peek();
        }

        // Check for predefined escape characters
        if (escapedCharMap.hasKey(currentChar)) {
            state.lexeme += <string>escapedCharMap[currentChar];
            return;
        }

        // Check for unicode characters
        match currentChar {
            "x" => {
                check scanUnicodeEscapedCharacters(state, "x", 2);
                return;
            }
            "u" => {
                check scanUnicodeEscapedCharacters(state, "u", 4);
                return;
            }
            "U" => {
                check scanUnicodeEscapedCharacters(state, "U", 8);
                return;
            }
        }
        return generateInvalidCharacterError(state, "escaped character");
    }

    public static class TagCharacterScanner implements Scan {
        @Override
        public boolean scan(YamlLexer.StateMachine sm) {
            return false;
        }
    }

    public static class DigitScanner implements Scan {

        @Override
        public boolean scan(YamlLexer.StateMachine sm) {
            return false;
        }
    }

    public static class WhiteSpaceScanner implements Scan {
        /**
         * Scan the white spaces for a line-in-separation.
         *
         * @param sm - Current lexer state
         * @return - False to continue. True to terminate the token.
         */
        @Override
        public boolean scan(YamlLexer.StateMachine sm) {
            int peek = sm.peek();
            if (peek == ' ') {
                return false;
            }
            if (peek == '\t') {
                sm.updateFirstTabIndex();
                return false;
            }
            return true;
        }
    }

    public static class TagHandleScanner implements Scan {
        private final boolean differentiate;

        public TagHandleScanner(boolean differentiate) {
            this.differentiate = differentiate;
        }

        @Override
        public boolean scan(YamlLexer.StateMachine sm) {
            return false;
        }
    }

    public static class PrintableCharScanner implements Scan {
        private final boolean allowWhiteSpace;

        public PrintableCharScanner(boolean allowWhiteSpace) {
            this.allowWhiteSpace = allowWhiteSpace;
        }

        @Override
        public boolean scan(YamlLexer.StateMachine sm) {
            return false;
        }
    }

    public static class UriScanner implements Scan {
        private final boolean isVerbatim;

        public UriScanner(boolean isVerbatim) {
            this.isVerbatim = isVerbatim;
        }

        @Override
        public boolean scan(YamlLexer.StateMachine sm) {
            return false;
        }
    }

    public static class PlanarCharScanner implements Scan {

        /**
         * Process planar scalar values.
         *
         * @param sm - Current lexer state
         * @return False to continue. True to terminate the token. An error on failure.
         */
        @Override
        public boolean scan(YamlLexer.StateMachine sm) {
            String whitespace = "";
            int numWhitespace = 0;
            int peekAtIndex = sm.peek(numWhitespace);
            while (WHITE_SPACE_PATTERN.pattern(peekAtIndex)) {
                whitespace += " ";
                peekAtIndex = sm.peek(++numWhitespace);
            }

            if (peekAtIndex == -1 || peekAtIndex == '\n') {
                return true;
            }

            // Terminate when the flow indicators are detected inside flow style collections
            if (Utils.matchPattern(sm, List.of(FLOW_INDICATOR_PATTERN)) && sm.isFlowCollection()) {
                return true;
            }

            if (Utils.matchPattern(sm, List.of(PRINTABLE_PATTERN), List.of(LINE_BREAK_PATTERN, BOM_PATTERN,
                    WHITE_SPACE_PATTERN, new CharPattern('#'), new CharPattern(':')))) {
                sm.forward(numWhitespace);
                sm.appendToLexeme(whitespace + Character.toString(peekAtIndex));
                return false;
            }

            // Check for comments with a space before it
            if (peekAtIndex == '#') {
                int peekBeforeAtIndex = sm.peek(numWhitespace-1);
                if (peekBeforeAtIndex == ' ' || peekBeforeAtIndex == '\t') {
                    return true;
                }
                sm.appendToLexeme(whitespace + "#");
                return false;
            }

            // Check for mapping value with a space after it
            if (peekAtIndex == ':') {
                if (!Utils.discernPlanarFromIndicator(sm)) {
                    return true;
                }
                sm.appendToLexeme(whitespace + ":");
                return false;
            }

            throw new RuntimeException("Invalid planar character");
        }
    }

    public static class AnchorNameScanner implements Scan {

        /**
         * Scan the lexeme for the anchor name
         */
        @Override
        public boolean scan(YamlLexer.StateMachine sm) {
            if (Utils.matchPattern(sm, List.of(PRINTABLE_PATTERN),
                    List.of(LINE_BREAK_PATTERN, BOM_PATTERN, FLOW_INDICATOR_PATTERN, WHITE_SPACE_PATTERN)
                    )) {
                sm.appendToLexeme(Character.toString(sm.peek()));
                return false;
            }
            return true;
        }
    }


}
