package io.ballerina.stdlib.data.yaml.parser;

import io.ballerina.stdlib.data.yaml.common.Types.Collection;

public abstract class ParserEvent {

    private EventKind kind;
    private String anchor = null;
    private String tag = null;

    public ParserEvent(EventKind kind) {
        this.kind = kind;
    }

    public EventKind getKind() {
        return kind;
    }

    public abstract ParserEvent clone();

    public void setKind(EventKind kind) {
        this.kind = kind;
    }

    public String getAnchor() {
        return anchor;
    }

    public void setAnchor(String anchor) {
        this.anchor = anchor;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public static class AliasEvent extends ParserEvent {

        private final String alias;

        public AliasEvent(String alias) {
            super(EventKind.ALIAS_EVENT);
            this.alias = alias;
        }

        public String getAlias() {
            return alias;
        }

        @Override
        public ParserEvent clone() {
            AliasEvent aliasEvent = new AliasEvent(alias);
            aliasEvent.setAnchor(getAnchor());
            aliasEvent.setTag(getTag());
            return aliasEvent;
        }
    }

    public static class ScalarEvent extends ParserEvent {

        private final String value;

        public ScalarEvent() {
            super(EventKind.SCALAR_EVENT);
            this.value = null;
        }

        public ScalarEvent(String value) {
            super(EventKind.SCALAR_EVENT);
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public ParserEvent clone() {
            ScalarEvent scalarEvent = new ScalarEvent(value);
            scalarEvent.setAnchor(getAnchor());
            scalarEvent.setTag(getTag());
            return scalarEvent;
        }
    }

    public static class StartEvent extends ParserEvent {

        private final Collection startType;
        private boolean flowStyle = false;
        private boolean implicit = false;

        public StartEvent(Collection startType) {
            super(EventKind.START_EVENT);
            this.startType = startType;
        }

        public StartEvent(Collection startType, boolean flowStyle, boolean implicit) {
            super(EventKind.START_EVENT);
            this.startType = startType;
            this.flowStyle = flowStyle;
            this.implicit = implicit;
        }

        public Collection getStartType() {
            return startType;
        }

        public boolean isFlowStyle() {
            return flowStyle;
        }

        public boolean isImplicit() {
            return implicit;
        }

        @Override
        public ParserEvent clone() {
            StartEvent startEvent = new StartEvent(startType, flowStyle, implicit);
            startEvent.setAnchor(getAnchor());
            startEvent.setTag(getTag());
            return startEvent;
        }
    }

    public static class EndEvent extends ParserEvent {

        private final Collection endType;

        public EndEvent(Collection endType) {
            super(EventKind.END_EVENT);
            this.endType = endType;
        }

        public Collection getEndType() {
            return endType;
        }

        @Override
        public ParserEvent clone() {
            EndEvent endEvent = new EndEvent(endType);
            endEvent.setAnchor(getAnchor());
            endEvent.setTag(getTag());
            return endEvent;
        }
    }

    public static class DocumentMarkerEvent extends ParserEvent {

        private final boolean explicit;

        public DocumentMarkerEvent(boolean explicit) {
            super(EventKind.DOCUMENT_MARKER_EVENT);
            this.explicit = explicit;
        }

        public boolean isExplicit() {
            return explicit;
        }

        @Override
        public ParserEvent clone() {
            DocumentMarkerEvent documentMarkerEvent = new DocumentMarkerEvent(explicit);
            documentMarkerEvent.setAnchor(getAnchor());
            documentMarkerEvent.setTag(getTag());
            return documentMarkerEvent;
        }
    }

    public enum EventKind {
        ALIAS_EVENT,
        SCALAR_EVENT,
        START_EVENT,
        END_EVENT,
        DOCUMENT_MARKER_EVENT
    }
}
