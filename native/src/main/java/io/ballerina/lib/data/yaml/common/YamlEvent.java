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

package io.ballerina.lib.data.yaml.common;

import io.ballerina.lib.data.yaml.common.Types.Collection;

/**
 * Parser Events when parsing yaml data.
 *
 * @since 0.1.0
 */
public abstract class YamlEvent {

    private EventKind kind;
    private String anchor = null;
    private String tag = null;

    public YamlEvent(EventKind kind) {
        this.kind = kind;
    }

    public EventKind getKind() {
        return kind;
    }

    public abstract YamlEvent clone();

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

    public static class AliasEvent extends YamlEvent {

        private final String alias;

        public AliasEvent(String alias) {
            super(EventKind.ALIAS_EVENT);
            this.alias = alias;
        }

        public String getAlias() {
            return alias;
        }

        @Override
        public YamlEvent clone() {
            AliasEvent aliasEvent = new AliasEvent(alias);
            aliasEvent.setAnchor(getAnchor());
            aliasEvent.setTag(getTag());
            return aliasEvent;
        }
    }

    public static class ScalarEvent extends YamlEvent {

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
        public YamlEvent clone() {
            ScalarEvent scalarEvent = new ScalarEvent(value);
            scalarEvent.setAnchor(getAnchor());
            scalarEvent.setTag(getTag());
            return scalarEvent;
        }
    }

    public static class StartEvent extends YamlEvent {

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
        public YamlEvent clone() {
            StartEvent startEvent = new StartEvent(startType, flowStyle, implicit);
            startEvent.setAnchor(getAnchor());
            startEvent.setTag(getTag());
            return startEvent;
        }
    }

    public static class EndEvent extends YamlEvent {

        private final Collection endType;

        public EndEvent(Collection endType) {
            super(EventKind.END_EVENT);
            this.endType = endType;
        }

        public Collection getEndType() {
            return endType;
        }

        @Override
        public YamlEvent clone() {
            EndEvent endEvent = new EndEvent(endType);
            endEvent.setAnchor(getAnchor());
            endEvent.setTag(getTag());
            return endEvent;
        }
    }

    public static class DocumentMarkerEvent extends YamlEvent {

        private final boolean explicit;

        public DocumentMarkerEvent(boolean explicit) {
            super(EventKind.DOCUMENT_MARKER_EVENT);
            this.explicit = explicit;
        }

        public boolean isExplicit() {
            return explicit;
        }

        @Override
        public YamlEvent clone() {
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
