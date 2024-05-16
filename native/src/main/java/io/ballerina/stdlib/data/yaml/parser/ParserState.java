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

package io.ballerina.stdlib.data.yaml.parser;

import io.ballerina.runtime.api.types.Type;
import io.ballerina.stdlib.data.yaml.common.Types;
import io.ballerina.stdlib.data.yaml.common.YamlEvent;
import io.ballerina.stdlib.data.yaml.lexer.CharacterReader;
import io.ballerina.stdlib.data.yaml.lexer.LexerState;
import io.ballerina.stdlib.data.yaml.lexer.Token;
import io.ballerina.stdlib.data.yaml.utils.Error;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParserState {

    public static final Token DUMMY_TOKEN = new Token(Token.TokenType.DUMMY);
    private final LexerState lexerState;
    private Token.TokenType prevToken = Token.TokenType.DUMMY;
    private Token currentToken = DUMMY_TOKEN;
    private Token bufferedToken = DUMMY_TOKEN;
    private List<YamlEvent> eventBuffer = new ArrayList<>();
    private int lineIndex = -1;
    private boolean directiveDocument = false;
    private boolean explicitDoc = false;
    private Float yamlVersion = null;
    private Map<String, String> customTagHandles = new HashMap();
    private boolean explicitKey = false;
    private int lastExplicitKeyLine = -1;
    private boolean expectBlockSequenceValue = false;
    private boolean tagPropertiesInLine = false;
    private boolean indentationProcessed = false;
    private int lastKeyLine = -1;
    private boolean emptyKey = false;
    private int numLines;
    private List<String> reservedDirectives = new ArrayList<>();

    public ParserState(Reader reader, Type type) {
        this.lexerState = new LexerState(new CharacterReader(reader));
        try {
            initLexer();
        } catch (Exception e) {
            eventBuffer.add(new YamlEvent.EndEvent(Types.Collection.STREAM));
        }

        // handleExpectedType(type);
    }

    public int getLineIndex() {
        return lineIndex;
    }

    public void setLineIndex(int lineIndex) {
        this.lineIndex = lineIndex;
    }

    public void updateLexerState(LexerState.State state) {
        lexerState.updateLexerState(state);
    }

    public void setBufferedToken(Token token) {
        bufferedToken = token;
    }

    public Token getBufferedToken() {
        return bufferedToken;
    }

    public Token.TokenType getPrevToken() {
        return prevToken;
    }

    public void setPrevToken(Token.TokenType prevToken) {
        this.prevToken = prevToken;
    }

    public Token getCurrentToken() {
        return currentToken;
    }

    public void setCurrentToken(Token currentToken) {
        this.currentToken = currentToken;
    }

    public LexerState getLexerState() {
        return lexerState;
    }

    public List<YamlEvent> getEventBuffer() {
        return eventBuffer;
    }

    public boolean isDirectiveDocument() {
        return directiveDocument;
    }

    public void setDirectiveDocument(boolean directiveDocument) {
        this.directiveDocument = directiveDocument;
    }

    public boolean isExplicitDoc() {
        return explicitDoc;
    }

    public void setExplicitDoc(boolean explicitDoc) {
        this.explicitDoc = explicitDoc;
    }

    public Float getYamlVersion() {
        return yamlVersion;
    }

    public void setYamlVersion(Float yamlVersion) {
        this.yamlVersion = yamlVersion;
    }

    public Map<String, String> getCustomTagHandles() {
        return customTagHandles;
    }

    public void setCustomTagHandles(Map<String, String> customTagHandles) {
        this.customTagHandles = customTagHandles;
    }

    public boolean isExplicitKey() {
        return explicitKey;
    }

    public void setExplicitKey(boolean explicitKey) {
        this.explicitKey = explicitKey;
    }

    public int getLastExplicitKeyLine() {
        return lastExplicitKeyLine;
    }

    public void setLastExplicitKeyLine(int lastExplicitKeyLine) {
        this.lastExplicitKeyLine = lastExplicitKeyLine;
    }

    public boolean isExpectBlockSequenceValue() {
        return expectBlockSequenceValue;
    }

    public boolean isTagPropertiesInLine() {
        return tagPropertiesInLine;
    }

    public void setTagPropertiesInLine(boolean tagPropertiesInLine) {
        this.tagPropertiesInLine = tagPropertiesInLine;
    }

    public void setExpectBlockSequenceValue(boolean expectBlockSequenceValue) {
        this.expectBlockSequenceValue = expectBlockSequenceValue;
    }

    public boolean isIndentationProcessed() {
        return indentationProcessed;
    }

    public void setIndentationProcessed(boolean indentationProcessed) {
        this.indentationProcessed = indentationProcessed;
    }

    public int getLastKeyLine() {
        return lastKeyLine;
    }

    public void setLastKeyLine(int lastKeyLine) {
        this.lastKeyLine = lastKeyLine;
    }

    public boolean isEmptyKey() {
        return emptyKey;
    }

    public void setEmptyKey(boolean emptyKey) {
        this.emptyKey = emptyKey;
    }

    public int getNumLines() {
        return numLines;
    }

    public List<String> getReservedDirectives() {
        return reservedDirectives;
    }

    public void setNumLines(int numLines) {
        this.numLines = numLines;
    }

    public int getLine() {
        return lexerState.getLine();
    }

    public int getColumn() {
        return lexerState.getColumn();
    }

    public void initLexer() throws Error.YamlParserException {
        lineIndex += 1;
        explicitDoc = false;
        expectBlockSequenceValue = false;
        tagPropertiesInLine = false;
        lexerState.updateNewLineProps();
        if (getLexerState().isEndOfStream()) {
            throw new Error.YamlParserException("END of stream reached", lexerState.getLine(), lexerState.getColumn());
        }
    }
}
