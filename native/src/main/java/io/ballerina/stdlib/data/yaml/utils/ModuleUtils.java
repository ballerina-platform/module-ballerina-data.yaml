package io.ballerina.stdlib.data.yaml.utils;

import io.ballerina.runtime.api.Environment;
import io.ballerina.runtime.api.Module;

public class ModuleUtils {

    /**
     * Time standard library package ID.
     */
    private static Module module = new Module("ballerina", "data.yaml");

    private ModuleUtils() {
    }

    public static void setModule(Environment env) {
        module = env.getCurrentModule();
    }

    public static Module getModule() {
        return module;
    }
}
