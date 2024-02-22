package io.ballerina.stdlib.data.yaml.lexer;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;

public class CharacterReader {

    private final Reader reader;
    private final char[] buff = new char[1024];
    private int[] dataBuffer = new int[0];
    private int dataBufferSize = 0;
    private int pointer = 0;
    private boolean eof = false;
    private int line = 0;
    private int column = 0;

    public CharacterReader(Reader reader) {
        this.reader = reader;
    }

    /**
     * Peeks next code point.
     *
     * @return next code point
     */
    public int peek() {
        return peek(0);
    }

    /**
     * Peeks the k-th indexed code point.
     *
     * @param k number of characters to peek
     * @return code point at the peek
     */
    public int peek(int k) {
        if (checkAndReadData(k) && pointer + k >= 0) {
            return dataBuffer[pointer + k];
        }
        return -1;
    }

    /**
     * Moves the internal pointer forward by one.
     *
     */
    public void forward() {
        forward(1);
    }

    /**
     * Moves the internal pointer forward by the specified amount (`k`).
     *
     * @param k The number of positions to move forward.
     */
    public void forward(int k) {
        for (int i = 0; i < k && checkAndReadData(k); i++) {
            int codePoint = dataBuffer[pointer++];
            if (hasNewLine(codePoint)) {
                this.column = 0;
                this.line++;
            } else if (codePoint != 0xFEFF) {
                this.column++;
            }
        }
    }

    private boolean hasNewLine(int codePoint) {
        return codePoint == '\n' || codePoint == '\r' && peek() == '\n';
    }

    private boolean checkAndReadData(int k) {
        if (!eof && pointer + k >= dataBufferSize) {
            readData();
        }
        return (pointer + k) < dataBufferSize;
    }

    private void readData() {
        try {
            int size = reader.read(buff);
            if (size <= 0) {
                this.eof = true;
                return;
            }

            int cpIndex = dataBufferSize - pointer;
            this.dataBuffer = Arrays.copyOfRange(dataBuffer, pointer, dataBufferSize + size);

            for (int i = 0; i < size; cpIndex++) {
                int codePoint = Character.codePointAt(buff, i);
                dataBuffer[cpIndex] = codePoint;
                if (isPrintable(codePoint)) {
                    i += Character.charCount(codePoint);
                } else {
                    throw new Exception("non printable patterns found");
                }
            }

            dataBufferSize = cpIndex;
        } catch (Exception ignored) {
        }
    }

    private static boolean isPrintable(int codePoint) {
        return (codePoint >= 32 && codePoint <= 126) || (codePoint >= 160 && codePoint <= 55295)
                || (codePoint >= 57344 && codePoint <= 65533) || (codePoint >= 65536 && codePoint <= 1114111)
                || codePoint == 9|| codePoint == 10 || codePoint == 13 || codePoint == 133;
    }

    public boolean isEof() {
        return eof;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public int getDataBufferSize() {
        return dataBufferSize;
    }
}
