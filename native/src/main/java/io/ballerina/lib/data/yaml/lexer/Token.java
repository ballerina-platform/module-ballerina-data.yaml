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

/**
 * Tokens used in YAML parser.
 *
 * @since 0.1.0
 */
public class Token {
    private final TokenType type;
    private String value;
    private IndentUtils.Indentation indentation = null;

    public Token(TokenType type) {
        this.type = type;
    }
    public Token(TokenType type, String value) {
        this(type);
        this.value = value;
    }

    public Token(TokenType type, String value, IndentUtils.Indentation indentation) {
        this(type, value);
        this.indentation = indentation;
    }

    public TokenType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public IndentUtils.Indentation getIndentation() {
        return indentation;
    }

    public enum TokenType {
        SEQUENCE_ENTRY("-"),
        MAPPING_KEY("?"),
        MAPPING_VALUE(":"),
        SEPARATOR(","),
        SEQUENCE_START("["),
        SEQUENCE_END("]"),
        MAPPING_START("{"),
        MAPPING_END("}"),
        DIRECTIVE("%"),
        ALIAS("*"),
        ANCHOR("&"),
        TAG_HANDLE("<tag-handle>"),
        TAG_PREFIX("<tag-prefix>"),
        TAG("<tag>"),
        DOT("."),
        LITERAL("|"),
        FOLDED(">"),
        DECIMAL("<integer>"),
        SEPARATION_IN_LINE("<separation-in-line>"),
        DIRECTIVE_MARKER("---"),
        DOCUMENT_MARKER("..."),
        DOUBLE_QUOTE_DELIMITER("\""),
        DOUBLE_QUOTE_CHAR("<double-quoted-scalar>"),
        SINGLE_QUOTE_DELIMITER("'"),
        SINGLE_QUOTE_CHAR("<single-quoted-scalar>"),
        PLANAR_CHAR("<plain-scalar>"),
        PRINTABLE_CHAR("<printable-char>"),
        CHOMPING_INDICATOR("<chomping-indicator>"),
        EMPTY_LINE("<empty-line>"),
        EOL("<end-of-line>"),
        COMMENT("<comment>"),
        TRAILING_COMMENT("<trailing-comment>"),
        DUMMY("<dummy>");
        TokenType(String s) {
        }
    }
}
