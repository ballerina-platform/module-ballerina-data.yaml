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

package io.ballerina.lib.data.yaml.emitter;

import io.ballerina.lib.data.yaml.common.Types;
import io.ballerina.lib.data.yaml.common.YamlEvent;

/**
 * Holds utilities use to emit YAML strings.
 *
 * @since 0.1.0
 */
public class Utils {

    public static YamlEvent getEvent(Emitter.EmitterState state) {
        if (state.events.size() < 1) {
            return new YamlEvent.EndEvent(Types.Collection.STREAM);
        }
        return state.events.remove(0);
    }

    public static String appendTagToValue(boolean tagAsSuffix, String tag, String value) {
        return tagAsSuffix ? value + " " + tag : tag + " " + value;
    }
}
