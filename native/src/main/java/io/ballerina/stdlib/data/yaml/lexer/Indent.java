package io.ballerina.stdlib.data.yaml.lexer;

import java.util.List;

public class Indent {
    private int column;
    private Indentation.Collection collection;

    public Indent(int column, Indentation.Collection collection) {
        this.column = column;
        this.collection = collection;
    }

    public int getColumn() {
        return column;
    }

    public void setColumn(int column) {
        this.column = column;
    }

    public Indentation.Collection getCollection() {
        return collection;
    }

    public void setCollection(Indentation.Collection collection) {
        this.collection = collection;
    }
}
