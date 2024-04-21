package io.ballerina.stdlib.data.yaml.lexer;

import java.util.List;

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
        return checkCharacters(sm, expectedChars, -1);
    }

    public static boolean checkCharacters(LexerState sm, List<Character> expectedChars, int column) {
        if (column == -1) {
            column = sm.getColumn();
        }
        return expectedChars.contains((char) sm.peek(column));
    }

    public static boolean isComment(LexerState sm) {
        return sm.peek() == '#';
    }

    public static boolean isMarker(LexerState sm, boolean directive) {
        int directiveCodePoint = directive ? '-' : '.';
        if (sm.peek() == directiveCodePoint && sm.peek(1) == directiveCodePoint && sm.peek(2) == directiveCodePoint
                && (WHITE_SPACE_PATTERN.pattern(sm.peek(3)) || sm.peek(3) == -1)) {
            sm.forward(2);
            return true;
        }
        return false;
    }

    public static boolean discernPlanarFromIndicator(LexerState sm) {
        if (sm.isFlowCollection()) {
            return matchPattern(sm, List.of(PRINTABLE_PATTERN),
                    List.of(LINE_BREAK_PATTERN, BOM_PATTERN, WHITE_SPACE_PATTERN, FLOW_INDICATOR_PATTERN), 1);
        }
        return matchPattern(sm, List.of(PRINTABLE_PATTERN),
                List.of(LINE_BREAK_PATTERN, BOM_PATTERN, WHITE_SPACE_PATTERN), 1);
    }

    public static String getWhitespace(LexerState sm) {
        StringBuilder whitespace = new StringBuilder();
        while (sm.getColumn() < sm.getRemainingBufferedSize()) {
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
}
