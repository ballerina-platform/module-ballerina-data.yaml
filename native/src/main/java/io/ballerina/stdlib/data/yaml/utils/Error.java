package io.ballerina.stdlib.data.yaml.utils;

public class Error {

    public static class YamlParserException extends Exception {
        private final int line;
        private final int column;

        public YamlParserException(String msg, int line, int column) {
            super(msg);
            this.line = line;
            this.column = column;
        }

        public int getLine() {
            return line;
        }

        public int getColumn() {
            return column;
        }
    }
}
