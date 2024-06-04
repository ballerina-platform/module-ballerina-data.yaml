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

import io.ballerina.lib.data.yaml.utils.DiagnosticErrorCode;
import io.ballerina.lib.data.yaml.utils.DiagnosticLog;
import io.ballerina.lib.data.yaml.utils.Error;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;

/**
 * Read and Consume input stream.
 *
 * @since 0.1.0
 */
public class CharacterReader {
    private final Reader reader;
    private final char[] buff; // data chucks are read into this buffer
    private int[] dataBuffer; // store the read characters as code points
    private int dataBufferSize = 0; // length of the data buffer
    private int remainingBufferedSize = 0;
    private int pointer = 0; // current position in the data buffer
    private boolean eof = false; // flag saying end of the stream reached
    private int line = 1; // current line number
    private int column = 0; // current column number

    public CharacterReader(Reader reader) {
        this.reader = reader;
        this.dataBuffer = new int[0];
        this.buff = new char[1024];
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
        if (k >= 0 && checkAndReadData(k) && pointer + k >= 0) {
            return dataBuffer[pointer + k];
        }
        return -1;
    }

    /**
     * Moves the internal pointer forward by the specified amount (`k`).
     *
     * @param k The number of positions to move forward.
     */
    public boolean forward(int k) {
        int i;
        for (i = 0; i < k && checkAndReadData(k); i++) {
            int codePoint = dataBuffer[pointer++];
            if (hasNewLine(codePoint)) {
                this.remainingBufferedSize -= column + 1;
                this.column = 0;
                this.line++;
            } else if (codePoint != 0xFEFF) {
                this.column++;
            }
        }
        return i == 0;
    }

    private boolean hasNewLine(int codePoint) {
        return codePoint == '\n';
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
                    NewLineIndexData newLineIndexData = findLastNewLineIndexAndNewLineCount();
                    line += newLineIndexData.newLineCount;
                    column += newLineIndexData.lastNewLineIndex == -1 ? cpIndex
                            : cpIndex - newLineIndexData.lastNewLineIndex;
                    throw new Error.YamlParserException("non printable character found", line, column);
                }
            }
            dataBufferSize = cpIndex;
            remainingBufferedSize = dataBufferSize;
            pointer = 0;
        } catch (Error.YamlParserException e) {
            throw DiagnosticLog.error(DiagnosticErrorCode.YAML_PARSER_EXCEPTION, e.getMessage(), line, column);
        } catch (IOException e) {
            throw DiagnosticLog.error(DiagnosticErrorCode.YAML_READER_FAILURE, e.getMessage());
        }
    }

    private record NewLineIndexData(int lastNewLineIndex, int newLineCount) {
    };

    private NewLineIndexData findLastNewLineIndexAndNewLineCount() {
        int idx = -1;
        int count = 0;
        for (int i = 0; i < this.dataBuffer.length; i++) {
            if (this.dataBuffer[i] == 10) {
                idx = i;
                count++;
            }
        }
        return new NewLineIndexData(idx, count);
    }

    private static boolean isPrintable(int codePoint) {
        return (codePoint >= 32 && codePoint <= 126) || (codePoint >= 160 && codePoint <= 55295)
                || (codePoint >= 57344 && codePoint <= 65533) || (codePoint >= 65536 && codePoint <= 1114111)
                || codePoint == 9 || codePoint == 10 || codePoint == 13 || codePoint == 133;
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

    public int getRemainingBufferedSize() {
        return remainingBufferedSize;
    }
}
