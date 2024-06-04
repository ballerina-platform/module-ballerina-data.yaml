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

package io.ballerina.stdlib.data.yaml.emitter;

import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.stdlib.data.yaml.common.Types;
import io.ballerina.stdlib.data.yaml.common.YamlEvent;
import io.ballerina.stdlib.data.yaml.utils.DiagnosticErrorCode;
import io.ballerina.stdlib.data.yaml.utils.DiagnosticLog;

import java.util.ArrayList;
import java.util.List;

import static io.ballerina.stdlib.data.yaml.utils.Constants.DEFAULT_GLOBAL_TAG_HANDLE;
import static io.ballerina.stdlib.data.yaml.utils.Constants.DEFAULT_LOCAL_TAG_HANDLE;
import static io.ballerina.stdlib.data.yaml.utils.Constants.END_OF_YAML_DOCUMENT;
import static io.ballerina.stdlib.data.yaml.utils.Constants.START_OF_YAML_DOCUMENT;

/**
 * Convert Yaml Event stream into list of YAML strings.
 *
 * @since 0.1.0
 */
public class Emitter {

    /**
     * Represents the variables of the Emitter state.
     *
     * @since 0.1.0
     */
    public static class EmitterState {
        List<BString> document;
        List<String> documentTags;
        // total white spaces for a single indent
        final String indent;
        // If set, the tag is written explicitly along with the value
        final boolean canonical;
        boolean lastBareDoc = false;
        final List<YamlEvent> events;

        public EmitterState(List<YamlEvent> events, int indentationPolicy, boolean canonical) {
            this.events = events;
            this.canonical = canonical;
            this.document = new ArrayList<>();
            this.documentTags = new ArrayList<>();
            this.indent = " ".repeat(indentationPolicy);
        }

        public void addLine(String line) {
            document.add(StringUtils.fromString(line));
        }

        public List<BString> getDocument() {
            return getDocument(false);
        }

        public List<BString> getDocument(boolean isStream) {
            List<BString> output = new ArrayList<>(document.stream().toList());
            if (!documentTags.isEmpty()) {
                output.add(0, START_OF_YAML_DOCUMENT);
                if (lastBareDoc) {
                    output.add(0, END_OF_YAML_DOCUMENT);
                    lastBareDoc = false;
                }
                output.add(END_OF_YAML_DOCUMENT);
            } else if (isStream && document.size() > 0) {
                output.add(0, START_OF_YAML_DOCUMENT);
                lastBareDoc = true;
            }

            document = new ArrayList<>();
            documentTags = new ArrayList<>();
            return output;
        }
    }

    public static List<BString> emit(EmitterState state, boolean isStream) {
        if (isStream) {
            List<BString> output = new ArrayList<>();
            boolean isFirstEvent = true;
            while (!state.events.isEmpty()) {
                write(state);
                output.addAll(state.getDocument(!isFirstEvent));
                isFirstEvent = false;
            }
            return output;
        }
        write(state);
        if (!state.events.isEmpty()) {
            throw DiagnosticLog.error(DiagnosticErrorCode.ARRAY_SIZE_MISMATCH);
        }
        return state.getDocument();
    }

    private static void write(EmitterState state) {
        YamlEvent event = Utils.getEvent(state);

        if (event.getKind() == YamlEvent.EventKind.START_EVENT) {
            YamlEvent.StartEvent startEvent = ((YamlEvent.StartEvent) event);
            Types.Collection startType = startEvent.getStartType();
            if (startType == Types.Collection.SEQUENCE) {
                if (startEvent.isFlowStyle()) {
                    state.addLine(writeFlowSequence(state, event.getTag()));
                } else {
                    writeBlockSequence(state, "", event.getTag());
                }
                return;
            }
            if (startType == Types.Collection.MAPPING) {
                if (startEvent.isFlowStyle()) {
                    state.addLine(writeFlowMapping(state, event.getTag()));
                } else {
                    writeBlockMapping(state, "");
                }
                return;
            }
            if (startType == Types.Collection.STREAM) {
                state.addLine("---");
            }
        }

        if (event.getKind() == YamlEvent.EventKind.SCALAR_EVENT) {
            YamlEvent.ScalarEvent scalarEvent = (YamlEvent.ScalarEvent) event;
            state.addLine(writeNode(state, scalarEvent.getValue(), event.getTag()));
        }
    }

    private static String writeNode(EmitterState state, String value, String tag) {
        return writeNode(state, value, tag, false);
    }

    private static String writeNode(EmitterState state, String value, String tag, boolean tagAsSuffix) {
        if (tag == null) {
            return value;
        }

        if (tag.startsWith(DEFAULT_GLOBAL_TAG_HANDLE)) {
            return state.canonical ? Utils.appendTagToValue(tagAsSuffix, "!!" +
            tag.substring(DEFAULT_GLOBAL_TAG_HANDLE.length()), value) : value;
        }

        if (tag.startsWith(DEFAULT_LOCAL_TAG_HANDLE)) {
            return Utils.appendTagToValue(tagAsSuffix, tag, value);
        }
        return "";
    }

    private static void writeBlockMapping(EmitterState state, String whitespace) {
        YamlEvent event = Utils.getEvent(state);
        String line;

        while (true) {
            line = "";
            if (event.getKind() == YamlEvent.EventKind.END_EVENT) {
                YamlEvent.EndEvent endEvent = (YamlEvent.EndEvent) event;
                if (endEvent.getEndType() == Types.Collection.MAPPING ||
                        endEvent.getEndType() == Types.Collection.STREAM) {
                    break;
                }
                throw DiagnosticLog.error(DiagnosticErrorCode.ARRAY_SIZE_MISMATCH);
            }

            if (event.getKind() == YamlEvent.EventKind.SCALAR_EVENT) {
                YamlEvent.ScalarEvent scalarEvent = (YamlEvent.ScalarEvent) event;
                line += whitespace + writeNode(state, scalarEvent.getValue(), event.getTag()) + ": ";
            }

            event = Utils.getEvent(state);

            if (event.getKind() == YamlEvent.EventKind.SCALAR_EVENT) {
                YamlEvent.ScalarEvent scalarEvent = (YamlEvent.ScalarEvent) event;
                line += writeNode(state, scalarEvent.getValue(), event.getTag());
                state.addLine(line);
            }

            if (event.getKind() == YamlEvent.EventKind.START_EVENT) {
                YamlEvent.StartEvent startEvent = (YamlEvent.StartEvent) event;
                if (startEvent.getStartType() == Types.Collection.SEQUENCE) {
                    if (startEvent.isFlowStyle()) {
                        state.addLine(line + writeFlowSequence(state, event.getTag()));
                    } else {
                        state.addLine(writeNode(state, line.substring(0, line.length() - 1), event.getTag(), true));
                        writeBlockSequence(state, whitespace, event.getTag());
                    }
                } else if (startEvent.getStartType() == Types.Collection.MAPPING) {
                    if (startEvent.isFlowStyle()) {
                        state.addLine(line + writeFlowMapping(state, event.getTag()));
                    } else {
                        state.addLine(writeNode(state, line.substring(0, line.length() - 1), event.getTag(), true));
                        writeBlockMapping(state, whitespace + state.indent);
                    }
                }
            }
            event = Utils.getEvent(state);
        }
    }

    private static String writeFlowMapping(EmitterState state, String tag) {
        StringBuilder line = new StringBuilder(writeNode(state, "{", tag));
        YamlEvent event = Utils.getEvent(state);
        boolean isFirstValue = true;

        while (true) {
            if (event.getKind() == YamlEvent.EventKind.END_EVENT) {
                YamlEvent.EndEvent endEvent = (YamlEvent.EndEvent) event;
                if (endEvent.getEndType() == Types.Collection.MAPPING) {
                    break;
                }
                DiagnosticLog.error(DiagnosticErrorCode.ARRAY_SIZE_MISMATCH);
            }

            if (!isFirstValue) {
                line.append(", ");
            }

            if (event.getKind() == YamlEvent.EventKind.SCALAR_EVENT) {
                YamlEvent.ScalarEvent scalarEvent = (YamlEvent.ScalarEvent) event;
                line.append(writeNode(state, scalarEvent.getValue(), event.getTag())).append(": ");
            }

            event = Utils.getEvent(state);

            if (event.getKind() == YamlEvent.EventKind.SCALAR_EVENT) {
                YamlEvent.ScalarEvent scalarEvent = (YamlEvent.ScalarEvent) event;
                line.append(writeNode(state, scalarEvent.getValue(), event.getTag()));
            }

            if (event.getKind() == YamlEvent.EventKind.START_EVENT) {
                YamlEvent.StartEvent startEvent = (YamlEvent.StartEvent) event;
                if (startEvent.getStartType() == Types.Collection.SEQUENCE) {
                    line.append(writeFlowSequence(state, event.getTag()));
                } else if (startEvent.getStartType() == Types.Collection.MAPPING) {
                    line.append(writeFlowMapping(state, event.getTag()));
                }
            }

            event = Utils.getEvent(state);
            isFirstValue = false;
        }

        line.append("}");
        return line.toString();
    }

    private static void writeBlockSequence(EmitterState state, String whitespace, String tag) {
        YamlEvent event = Utils.getEvent(state);
        boolean emptySequence = true;

        while (true) {
            if (event.getKind() == YamlEvent.EventKind.END_EVENT) {
                YamlEvent.EndEvent endEvent = (YamlEvent.EndEvent) event;
                if (endEvent.getEndType() == Types.Collection.MAPPING) {
                    throw DiagnosticLog.error(DiagnosticErrorCode.ARRAY_SIZE_MISMATCH);
                }
                if (emptySequence) {
                    state.addLine(whitespace + writeNode(state, "-", tag, true));
                }
                break;
            }

            if (event.getKind() == YamlEvent.EventKind.SCALAR_EVENT) {
                YamlEvent.ScalarEvent scalarEvent = (YamlEvent.ScalarEvent) event;
                state.addLine(whitespace + "- " + writeNode(state, scalarEvent.getValue(), event.getTag()));
            }

            if (event.getKind() == YamlEvent.EventKind.START_EVENT) {
                YamlEvent.StartEvent startEvent = (YamlEvent.StartEvent) event;
                if (startEvent.getStartType() == Types.Collection.SEQUENCE) {
                    if (startEvent.isFlowStyle()) {
                        state.addLine(whitespace + "- " + writeFlowSequence(state, event.getTag()));
                    } else {
                        state.addLine(whitespace + writeNode(state, "-", event.getTag(), true));
                        writeBlockSequence(state, whitespace + state.indent, event.getTag());
                    }
                } else if (startEvent.getStartType() == Types.Collection.MAPPING) {
                    if (startEvent.isFlowStyle()) {
                        state.addLine(whitespace + "- " + writeFlowMapping(state, event.getTag()));
                    } else {
                        state.addLine(whitespace + "-");
                        writeBlockMapping(state, whitespace + state.indent);
                    }
                }
            }

            event = Utils.getEvent(state);
            emptySequence = false;
        }
    }

    private static String writeFlowSequence(EmitterState state, String tag) {
        StringBuilder line = new StringBuilder(writeNode(state, "[", tag));
        YamlEvent event = Utils.getEvent(state);
        boolean firstValue = true;

        while (true) {
            if (event.getKind() == YamlEvent.EventKind.END_EVENT) {
                YamlEvent.EndEvent endEvent = (YamlEvent.EndEvent) event;
                if (endEvent.getEndType() == Types.Collection.SEQUENCE) {
                    break;
                }
                throw DiagnosticLog.error(DiagnosticErrorCode.ARRAY_SIZE_MISMATCH);
            }

            if (!firstValue) {
                line.append(", ");
            }

            if (event.getKind() == YamlEvent.EventKind.SCALAR_EVENT) {
                YamlEvent.ScalarEvent scalarEvent = (YamlEvent.ScalarEvent) event;
                line.append(writeNode(state, scalarEvent.getValue(), event.getTag()));
            }

            if (event.getKind() == YamlEvent.EventKind.START_EVENT) {
                YamlEvent.StartEvent startEvent = (YamlEvent.StartEvent) event;
                if (startEvent.getStartType() == Types.Collection.SEQUENCE) {
                    line.append(writeFlowSequence(state, event.getTag()));
                } else if (startEvent.getStartType() == Types.Collection.MAPPING) {
                    line.append(writeFlowMapping(state, event.getTag()));
                }
            }

            event = Utils.getEvent(state);
            firstValue = false;
        }

        line.append("]");
        return line.toString();
    }
}
