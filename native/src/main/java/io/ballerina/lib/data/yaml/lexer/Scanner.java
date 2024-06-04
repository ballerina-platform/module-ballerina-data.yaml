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

package io.ballerina.stdlib.data.yaml.lexer;

import io.ballerina.stdlib.data.yaml.utils.Error;

import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static io.ballerina.stdlib.data.yaml.lexer.Utils.PRINTABLE_PATTERN;
import static io.ballerina.stdlib.data.yaml.lexer.Utils.JSON_PATTERN;
import static io.ballerina.stdlib.data.yaml.lexer.Utils.BOM_PATTERN;
import static io.ballerina.stdlib.data.yaml.lexer.Utils.LINE_BREAK_PATTERN;
import static io.ballerina.stdlib.data.yaml.lexer.Utils.WHITE_SPACE_PATTERN;
import static io.ballerina.stdlib.data.yaml.lexer.Utils.DECIMAL_PATTERN;
import static io.ballerina.stdlib.data.yaml.lexer.Utils.HEXA_DECIMAL_PATTERN;
import static io.ballerina.stdlib.data.yaml.lexer.Utils.WORD_PATTERN;
import static io.ballerina.stdlib.data.yaml.lexer.Utils.FLOW_INDICATOR_PATTERN;
import static io.ballerina.stdlib.data.yaml.lexer.Utils.URI_PATTERN;
import static io.ballerina.stdlib.data.yaml.lexer.Utils.matchPattern;

/**
 * This class will hold utility functions to scan and consume different character patterns.
 *
 * @since 0.1.0
 */
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
    public static final Scan COMMENT_SCANNER = new CommentScanner();
    public static final Map<Character, String> ESCAPED_CHAR_MAP;

    static {
        ESCAPED_CHAR_MAP = new HashMap<>();
        ESCAPED_CHAR_MAP.put('0', "\u0000");
        ESCAPED_CHAR_MAP.put('a', "\u0007");
        ESCAPED_CHAR_MAP.put('b', "\u0008");
        ESCAPED_CHAR_MAP.put('t', "\t");
        ESCAPED_CHAR_MAP.put('n', "\n");
        ESCAPED_CHAR_MAP.put('v', "\u000b");
        ESCAPED_CHAR_MAP.put('f', "\u000c");
        ESCAPED_CHAR_MAP.put('r', "\r");
        ESCAPED_CHAR_MAP.put('e', "\u001b");
        ESCAPED_CHAR_MAP.put('\"', "\"");
        ESCAPED_CHAR_MAP.put('/', "/");
        ESCAPED_CHAR_MAP.put('\\', "\\\\");
        ESCAPED_CHAR_MAP.put('N', "\u0085");
        ESCAPED_CHAR_MAP.put('_', "\u00a0");
        ESCAPED_CHAR_MAP.put('L', "\u2028");
        ESCAPED_CHAR_MAP.put('P', "\u2029");
        ESCAPED_CHAR_MAP.put(' ', "\u0020");
        ESCAPED_CHAR_MAP.put('\t', "\t");
    }

    /**
     * Lookahead the remaining characters in the buffer and capture lexemes for the tagged token.
     *
     * @param sm    Current lexer state
     * @param scan  Scan instance that is going to scan
     * @param token Targeting token upon successful scan
     */
    public static void iterate(LexerState sm, Scan scan, Token.TokenType token) throws Error.YamlParserException {
        iterate(sm, scan, token, false);
    }

    /**
     * Lookahead the remaining characters in the buffer and capture lexemes for the tagged token.
     *
     * @param sm      Current lexer state
     * @param scan    Scan instance that is going to scan
     * @param token   Targeting token upon successful scan
     * @param include True when the last character belongs to the token
     */
    public static void iterate(LexerState sm, Scan scan, Token.TokenType token, boolean include)
            throws Error.YamlParserException {
        while (true) {
            if (scan.scan(sm)) {
                if (include && sm.peek() != '\n') {
                    sm.forward();
                }
                if (include && sm.peek() != '\r' && sm.peek(1) != '\n') {
                    sm.forward(2);
                }
                sm.tokenize(token);
                return;
            }
            if (sm.peek(1) == -1) {
                sm.setEofStream(true);
                break;
            }
            sm.forward();
        }
        sm.forward();
        sm.tokenize(token);
    }

    interface Scan {
        boolean scan(LexerState sm) throws Error.YamlParserException;
    }

    public static class SingleQuoteCharScanner implements Scan {

        /**
         * Process double quoted scalar values.
         */
        @Override
        public boolean scan(LexerState sm) throws Error.YamlParserException {
            // Process nb-json characters
            if (matchPattern(sm, List.of(JSON_PATTERN), List.of(new Utils.CharPattern('\'')))) {
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

            throw new Error.YamlParserException("invalid single quote char", sm.getLine(), sm.getColumn());
        }
    }

    public static class DoubleQuoteCharScanner implements Scan {

        /**
         * Process double quoted scalar values.
         *
         * @param sm - Current lexer state
         */
        @Override
        public boolean scan(LexerState sm) throws Error.YamlParserException {
            // Process nb-json characters
            if (matchPattern(sm, List.of(JSON_PATTERN),
                    List.of(new Utils.CharPattern('\\'), new Utils.CharPattern('\"')))) {
                sm.appendToLexeme(Character.toString(sm.peek()));
                return false;
            }

            // Process escaped characters
            if (sm.peek() == '\\') {
                sm.forward();
                escapedCharacterScan(sm);
                sm.setLastEscapedChar(sm.getLexeme().length() - 1);
                return false;
            }

            // Terminate when delimiter is found
            if (sm.peek() == '\"') {
                return true;
            }

            throw new Error.YamlParserException("invalid double quoted character", sm.getLine(), sm.getColumn());
        }
    }

    public static class TagCharacterScanner implements Scan {

        /**
         * Scan the lexeme for tag characters.
         *
         * @param sm Current lexer state
         * @return false to continue and true to terminate the token.
         */
        @Override
        public boolean scan(LexerState sm) throws Error.YamlParserException {
            // Check for URI character
            if (matchPattern(sm, List.of(URI_PATTERN, WORD_PATTERN),
                    List.of(FLOW_INDICATOR_PATTERN, new Utils.CharPattern('!')))) {
                sm.appendToLexeme(Character.toString(sm.peek()));
                return false;
            }

            // Terminate if a whitespace or a flow indicator is detected
            if (matchPattern(sm, List.of(WHITE_SPACE_PATTERN, FLOW_INDICATOR_PATTERN, LINE_BREAK_PATTERN))) {
                return true;
            }

            // Process the hexadecimal values after '%'
            if (sm.peek() == '%') {
                scanUnicodeEscapedCharacters(sm, '%', 2);
                return false;
            }

            throw new Error.YamlParserException("invalid tag character", sm.getLine(), sm.getColumn());
        }
    }

    public static class DigitScanner implements Scan {

        /**
         * Check for the lexemes to crete an DECIMAL token.
         *
         * @param sm - Current lexer state
         */
        @Override
        public boolean scan(LexerState sm) throws Error.YamlParserException {
            if (matchPattern(sm, List.of(DECIMAL_PATTERN))) {
                sm.appendToLexeme(Character.toString(sm.peek()));
                return false;
            }
            int currentChar = sm.peek();
            if (WHITE_SPACE_PATTERN.pattern(currentChar) || currentChar == '.') {
                return true;
            }
            throw new Error.YamlParserException("invalid digit character", sm.getLine(), sm.getColumn());
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
        public boolean scan(LexerState sm) {
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

    public static class CommentScanner implements Scan {
        /**
         * Scan the comment.
         *
         * @param sm - Current lexer state
         * @return - False to continue. True to terminate the token.
         */
        @Override
        public boolean scan(LexerState sm) {
            return !matchPattern(sm, List.of(PRINTABLE_PATTERN), List.of(LINE_BREAK_PATTERN));
        }
    }

    public static class TagHandleScanner implements Scan {
        private final boolean differentiate;

        public TagHandleScanner(boolean differentiate) {
            this.differentiate = differentiate;
        }

        /**
         * Scan the lexeme for named tag handle.
         *
         * @param sm - Current lexer state
         */
        @Override
        public boolean scan(LexerState sm) throws Error.YamlParserException {

            // Scan the word of the name tag.
            if (matchPattern(sm, List.of(WORD_PATTERN, URI_PATTERN),
                    List.of(new Utils.CharPattern('!'), FLOW_INDICATOR_PATTERN), 1)) {
                sm.forward();
                sm.appendToLexeme(Character.toString(sm.peek()));
                // Store the complete primary tag if another '!' cannot be detected.
                if (differentiate && sm.peek(1) == -1) {
                    sm.setLexemeBuffer(sm.getLexeme().substring(1));
                    sm.setLexeme("!");
                    return true;
                }
                return false;
            }

            // Scan the end delimiter of the tag.
            if (sm.peek(1) == '!') {
                sm.forward();
                sm.appendToLexeme("!");
                return true;
            }

            // Store the complete primary tag if a white space or a flow indicator is detected.
            if (differentiate && matchPattern(sm, List.of(FLOW_INDICATOR_PATTERN, WHITE_SPACE_PATTERN), 1)) {
                sm.setLexemeBuffer(sm.getLexeme().substring(1));
                sm.setLexeme("!");
                return true;
            }

            // Store the complete primary tag if a hexadecimal escape is detected.
            if (differentiate && sm.peek(1) == '%') {
                sm.forward();
                scanUnicodeEscapedCharacters(sm, '%', 2);
                sm.setLexemeBuffer(sm.getLexeme().substring(1));
                sm.setLexeme("!");
                return true;
            }

            throw new Error.YamlParserException("invalid tag handle runtime exception", sm.getLine(), sm.getColumn());
        }
    }

    public static class PrintableCharScanner implements Scan {
        private final boolean allowWhiteSpace;

        public PrintableCharScanner(boolean allowWhiteSpace) {
            this.allowWhiteSpace = allowWhiteSpace;
        }

        /**
         * Scan the lexeme for printable char.
         *
         * @param sm - Current lexer state
         */
        @Override
        public boolean scan(LexerState sm) throws Error.YamlParserException {

            if (allowWhiteSpace) {
                if (matchPattern(sm, List.of(LINE_BREAK_PATTERN))) {
                    return true;
                }
            } else {
                if (matchPattern(sm, List.of(WHITE_SPACE_PATTERN, LINE_BREAK_PATTERN))) {
                    return true;
                }
            }

            if (matchPattern(sm, List.of(PRINTABLE_PATTERN), List.of(BOM_PATTERN, LINE_BREAK_PATTERN))) {
                sm.appendToLexeme(Character.toString(sm.peek()));
                return false;
            }

            throw new Error.YamlParserException("invalid printable character", sm.getLine(), sm.getColumn());
        }
    }

    public static class UriScanner implements Scan {
        private final boolean isVerbatim;

        public UriScanner(boolean isVerbatim) {
            this.isVerbatim = isVerbatim;
        }

        /**
         * Scan the lexeme for URI characters.
         *
         * @param sm - Current lexer state
         */
        @Override
        public boolean scan(LexerState sm) throws Error.YamlParserException {
            int currentChar = sm.peek();

            // Check for URI characters
            if (matchPattern(sm, List.of(URI_PATTERN, WORD_PATTERN))) {
                sm.appendToLexeme(Character.toString(currentChar));
                return false;
            }

            // Process the hexadecimal values after '%'
            if (currentChar == '%') {
                scanUnicodeEscapedCharacters(sm, '%', 2);
                return false;
            }

            // Ignore the comments
            if (matchPattern(sm, List.of(LINE_BREAK_PATTERN, WHITE_SPACE_PATTERN))) {
                return true;
            }

            // Terminate when '>' is detected for a verbatim tag
            if (isVerbatim && currentChar == '>') {
                return true;
            }

            throw new Error.YamlParserException("invalid URI character", sm.getLine(), sm.getColumn());
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
        public boolean scan(LexerState sm) throws Error.YamlParserException {
            StringBuilder whitespace = new StringBuilder();
            int numWhitespace = 0;
            int peekAtIndex = sm.peek();
            while (WHITE_SPACE_PATTERN.pattern(peekAtIndex)) {
                whitespace.append(" ");
                peekAtIndex = sm.peek(++numWhitespace);
            }

            if (peekAtIndex == -1 || LINE_BREAK_PATTERN.pattern(peekAtIndex)) {
                return true;
            }

            // Terminate when the flow indicators are detected inside flow style collections
            if (matchPattern(sm, List.of(FLOW_INDICATOR_PATTERN), numWhitespace) && sm.isFlowCollection()) {
                sm.forward(numWhitespace);
                return true;
            }

            if (matchPattern(sm, List.of(PRINTABLE_PATTERN), List.of(LINE_BREAK_PATTERN, BOM_PATTERN,
                    WHITE_SPACE_PATTERN, new Utils.CharPattern('#'), new Utils.CharPattern(':')), numWhitespace)) {
                sm.forward(numWhitespace);
                sm.appendToLexeme(whitespace + Character.toString(peekAtIndex));
                return false;
            }

            // Check for comments with a space before it
            if (peekAtIndex == '#') {
                if (numWhitespace > 0) {
                    return true;
                }
                sm.appendToLexeme("#");
                return false;
            }

            // Check for mapping value with a space after it
            if (peekAtIndex == ':') {
                if (!Utils.discernPlanarFromIndicator(sm, numWhitespace + 1)) {
                    return true;
                }
                sm.forward(numWhitespace);
                sm.appendToLexeme(whitespace + ":");
                return false;
            }

            throw new Error.YamlParserException("invalid planar character", sm.getLine(), sm.getColumn());
        }
    }

    public static class AnchorNameScanner implements Scan {

        /**
         * Scan the lexeme for the anchor name.
         *
         * @param sm - Current lexer state
         * @return False to continue, true to terminate the token
         */
        @Override
        public boolean scan(LexerState sm) {
            if (matchPattern(sm, List.of(PRINTABLE_PATTERN),
                    List.of(LINE_BREAK_PATTERN, BOM_PATTERN, FLOW_INDICATOR_PATTERN, WHITE_SPACE_PATTERN)
            )) {
                sm.appendToLexeme(Character.toString(sm.peek()));
                return false;
            }
            return true;
        }
    }

    private static void scanUnicodeEscapedCharacters(LexerState sm, char escapedChar, int length)
            throws Error.YamlParserException {

        StringBuilder unicodeDigits = new StringBuilder();
        // Check if the digits adhere to the hexadecimal code pattern.
        for (int i = 0; i < length; i++) {
            sm.forward();
            int peek = sm.peek();
            if (HEXA_DECIMAL_PATTERN.pattern(peek)) {
                unicodeDigits.append(Character.toString(peek));
                continue;
            }
            throw new Error.YamlParserException("expected a unicode character after escaped char",
                    sm.getLine(), sm.getColumn());
        }

        // Check if the lexeme can be converted to hexadecimal
        int hexResult = HexFormat.fromHexDigits(unicodeDigits.toString());
        sm.appendToLexeme(Character.toString(hexResult));
    }

    private static void escapedCharacterScan(LexerState sm) throws Error.YamlParserException {
        int currentChar = sm.peek();

        // Process double escape character
        if (LINE_BREAK_PATTERN.pattern(currentChar)) {
            sm.forward();
            processEscapedWhiteSpaces(sm);
            return;
        }

        if (currentChar == -1) {
            sm.appendToLexeme("\\");
            return;
        }

        // Check for predefined escape characters
        if (ESCAPED_CHAR_MAP.containsKey((char) currentChar)) {
            sm.appendToLexeme(ESCAPED_CHAR_MAP.get((char) currentChar));
            return;
        }

        // Check for unicode characters
        switch (currentChar) {
            case 'x' -> {
                scanUnicodeEscapedCharacters(sm, 'x', 2);
                return;
            }
            case 'u' -> {
                scanUnicodeEscapedCharacters(sm, 'u', 4);
                return;
            }
            case 'U' -> {
                scanUnicodeEscapedCharacters(sm, 'U', 8);
                return;
            }
        }
        throw new Error.YamlParserException("invalid escape character", sm.getLine(), sm.getColumn());
    }

    private static void processEscapedWhiteSpaces(LexerState sm) {
        while (WHITE_SPACE_PATTERN.pattern(sm.peek(1))) {
            sm.forward();
        }
    }
}
