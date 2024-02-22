package io.ballerina.stdlib.data.yaml.lexer;

import java.util.ArrayList;
import java.util.List;

public class Indentation {

    private IndentationChange change;
    private List<Collection> collection;
    private List<Token.TokenType> tokens = new ArrayList<>();

    public Indentation(IndentationChange change, List<Collection> collection, List<Token.TokenType> tokens) {
        this.change = change;
        this.collection = collection;
        this.tokens = tokens;
    }

    public static enum IndentationChange {
        INDENT_INCREASE(+1),
        INDENT_NO_CHANGE(0),
        INDENT_DECREASE(-1);
        IndentationChange(int i) {
        }
    }

    public static enum Collection {
        STREAM,
        SEQUENCE,
        MAPPING
    }
}
