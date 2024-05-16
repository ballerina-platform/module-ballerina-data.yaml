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

package io.ballerina.stdlib.data.yaml.common;

public class Types {

    public static final String DEFAULT_LOCAL_TAG_HANDLE = "!";
    public static final String DEFAULT_GLOBAL_TAG_HANDLE = "tag:yaml.org,2002:";
    public static final String DEFAULT_GLOBAL_SEQ_TAG_HANDLE = DEFAULT_GLOBAL_TAG_HANDLE + "seq";
    public static final String DEFAULT_GLOBAL_MAP_TAG_HANDLE = DEFAULT_GLOBAL_TAG_HANDLE + "map";
    public static final String DEFAULT_GLOBAL_STR_TAG_HANDLE = DEFAULT_GLOBAL_TAG_HANDLE + "str";

    public enum Collection {
        STREAM,
        SEQUENCE,
        MAPPING
    }

    public enum FailSafeSchema {
        MAPPING,
        SEQUENCE,
        STRING
    }

    public enum YAMLSchema {
        FAILSAFE_SCHEMA,
        JSON_SCHEMA,
        CORE_SCHEMA
    }
}
