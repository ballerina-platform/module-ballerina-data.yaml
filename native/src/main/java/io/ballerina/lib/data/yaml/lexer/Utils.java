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

import java.util.List;

/**
 * This class will hold utility functions used in YAML Lexer.
 *
 * @since 0.1.0
 */
public class Utils {

    public static final Pattern PRINTABLE_PATTERN = new PrintablePattern();
    public static final Pattern JSON_PATTERN = new JsonPattern();
    public static final Pattern BOM_PATTERN = new BomPattern();
    public static final Pattern LINE_BREAK_PATTERN = new LineBreakPattern();
    public static final Pattern WHITE_SPACE_PATTERN = new WhiteSpacePattern();
    public static final Pattern DECIMAL_PATTERN = new DecimalPattern();
    public static final Pattern HEXA_DECIMAL_PATTERN = new HexaDecimalPattern();
    public static final Pattern WORD_PATTERN = new WordPattern();
    public static final Pattern FLOW_INDICATOR_PATTERN = new FlowIndicatorPattern();
    public static final Pattern INDICATOR_PATTERN = new IndicatorPattern();
    public static final Pattern URI_PATTERN = new UriPattern();

    private Utils() {
    }

    public static boolean matchPattern(LexerState sm, List<Pattern> inclusionPatterns) {
        return matchPattern(sm, inclusionPatterns, List.of(), 0);
    }

    public static boolean matchPattern(LexerState sm, List<Pattern> inclusionPatterns, int offset) {
        return matchPattern(sm, inclusionPatterns, List.of(), offset);
    }

    public static boolean matchPattern(LexerState sm, List<Pattern> inclusionPatterns,
                                       List<Pattern> exclusionPatterns) {
        return matchPattern(sm, inclusionPatterns, exclusionPatterns, 0);
    }

    public static boolean matchPattern(LexerState sm, List<Pattern> inclusionPatterns,
                                       List<Pattern> exclusionPatterns, int offset) {
        int peek = sm.peek(offset);
        // If there is no character to check the pattern, then return false.
        if (peek == -1) {
            return false;
        }

        for (Pattern pattern: exclusionPatterns) {
            if (pattern.pattern(peek)) {
                return false;
            }
        }

        for (Pattern pattern: inclusionPatterns) {
            if (pattern.pattern(peek)) {
                return true;
            }
        }

        return false;
    }

    interface Pattern {
        boolean pattern(int codePoint);
    }

    public static class CharPattern implements Pattern {
        private final char value;

        public CharPattern(char value) {
            this.value = value;
        }

        @Override
        public boolean pattern(int codePoint) {
            return value == codePoint;
        }
    }

    public static class PrintablePattern implements Pattern {
        @Override
        public boolean pattern(int codePoint) {
            return (codePoint >= 32 && codePoint <= 126)
                    || (codePoint >= 160 && codePoint <= 55295)
                    || (codePoint >= 57344 && codePoint <= 65533)
                    || (codePoint >= 65536 && codePoint <= 1114111)
                    || codePoint == 9 || codePoint == 10 || codePoint == 13 || codePoint == 133;
        }
    }

    public static class JsonPattern implements Pattern {

        @Override
        public boolean pattern(int codePoint) {
            return codePoint >= 32 || codePoint == 9;
        }
    }

    public static class BomPattern implements Pattern {

        @Override
        public boolean pattern(int codePoint) {
            return codePoint == 65279;
        }
    }

    public static class LineBreakPattern implements Pattern {

        @Override
        public boolean pattern(int codePoint) {
            return codePoint == 10 || codePoint == 13;
        }
    }

    public static class WhiteSpacePattern implements Pattern {

        @Override
        public boolean pattern(int codePoint) {
            return codePoint == 32 || codePoint == 9;
        }
    }

    public static class DecimalPattern implements Pattern {

        @Override
        public boolean pattern(int codePoint) {
            return codePoint >= 48 && codePoint <= 57; // ASCII codes for 0-9
        }
    }

    public static class HexaDecimalPattern implements Pattern {

        @Override
        public boolean pattern(int codePoint) {
            return (codePoint >= 48 && codePoint <= 57) // 0-9
                    || (codePoint >= 65 && codePoint <= 70) // A-F
                    || (codePoint >= 97 && codePoint <= 102); // a-f
        }
    }

    public static class WordPattern implements Pattern {

        @Override
        public boolean pattern(int codePoint) {
            return (codePoint >= 97 && codePoint <= 122)
                    || (codePoint >= 65 && codePoint <= 90)
                    || DECIMAL_PATTERN.pattern(codePoint) || codePoint == '-';
        }
    }

    public static class FlowIndicatorPattern implements Pattern {

        @Override
        public boolean pattern(int codePoint) {
            return codePoint == ',' || codePoint == '[' || codePoint == ']' || codePoint == '{' || codePoint == '}';
        }
    }

    public static class IndicatorPattern implements Pattern {

        @Override
        public boolean pattern(int codePoint) {
            return "-?:,[]{}#&*!|>'\"%@`".contains(Character.toString(codePoint));
        }
    }

    public static class UriPattern implements Pattern {

        @Override
        public boolean pattern(int codePoint) {
            return "#;/?:@&=+$,_.!~*'()[]".contains(Character.toString(codePoint));
        }
    }

    public static boolean isPlainSafe(LexerState sm) {
        return matchPattern(sm, List.of(PRINTABLE_PATTERN),
                List.of(LINE_BREAK_PATTERN, BOM_PATTERN, WHITE_SPACE_PATTERN, INDICATOR_PATTERN));
    }

    public static boolean isTagChar(LexerState sm) {
        return matchPattern(sm, List.of(URI_PATTERN, WORD_PATTERN, new CharPattern('%')),
                List.of(new CharPattern('!'), FLOW_INDICATOR_PATTERN));
    }

    public static boolean checkCharacters(LexerState sm, List<Character> expectedChars) {
        return expectedChars.contains((char) sm.peek());
    }

    public static boolean isComment(LexerState sm) {
        return sm.peek() == '#';
    }

    public static boolean isMarker(LexerState sm, boolean directive) {
        int directiveCodePoint = directive ? '-' : '.';
        if (sm.peek() == directiveCodePoint && sm.peek(1) == directiveCodePoint
                && sm.peek(2) == directiveCodePoint) {
            if (WHITE_SPACE_PATTERN.pattern(sm.peek(3)) || LINE_BREAK_PATTERN.pattern(sm.peek(3))) {
                sm.forward(2);
                return true;
            }
            if (sm.peek(3) == -1) {
                sm.forward(2);
                sm.forward();
                return true;
            }
        }
        return false;
    }

    public static boolean discernPlanarFromIndicator(LexerState sm) {
        return discernPlanarFromIndicator(sm, 1);
    }

    public static boolean discernPlanarFromIndicator(LexerState sm, int offset) {
        if (sm.isFlowCollection()) {
            return matchPattern(sm, List.of(PRINTABLE_PATTERN),
                    List.of(LINE_BREAK_PATTERN, BOM_PATTERN, WHITE_SPACE_PATTERN, FLOW_INDICATOR_PATTERN), offset);
        }
        return matchPattern(sm, List.of(PRINTABLE_PATTERN),
                List.of(LINE_BREAK_PATTERN, BOM_PATTERN, WHITE_SPACE_PATTERN), offset);
    }

    public static String getWhitespace(LexerState sm) {
        StringBuilder whitespace = new StringBuilder();
        while (true) {
            if (sm.peek() == ' ') {
                whitespace.append(" ");
            } else if (sm.peek() == '\t') {
                sm.updateFirstTabIndex();
                whitespace.append("\t");
            } else {
                break;
            }
            sm.forward();
        }
        return whitespace.toString();
    }

    public static boolean isNewLine(LexerState sm) {
        return sm.peek() == '\n' || sm.peek() == '\r' && sm.peek(1) == '\n';
    }
}
