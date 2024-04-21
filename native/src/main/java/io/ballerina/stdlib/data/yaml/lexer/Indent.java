package io.ballerina.stdlib.data.yaml.lexer;

import io.ballerina.stdlib.data.yaml.common.Types.Collection;


public class Indent {
    private int column;
    private Collection collection;

    public Indent(int column, Collection collection) {
        this.column = column;
        this.collection = collection;
    }

    public int getColumn() {
        return column;
    }

    public void setColumn(int column) {
        this.column = column;
    }

    public Collection getCollection() {
        return collection;
    }

    public void setCollection(Collection collection) {
        this.collection = collection;
    }
}
