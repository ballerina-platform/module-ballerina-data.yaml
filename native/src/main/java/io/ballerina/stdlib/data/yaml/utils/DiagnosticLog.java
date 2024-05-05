package io.ballerina.stdlib.data.yaml.utils;

import io.ballerina.runtime.api.creators.ErrorCreator;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BError;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

public class DiagnosticLog {
    private static final String ERROR_PREFIX = "error";
    private static final String ERROR = "Error";
    private static final ResourceBundle MESSAGES = ResourceBundle.getBundle("error", Locale.getDefault());

    public static BError error(DiagnosticErrorCode code, Object... args) {
        String msg = formatMessage(code, args);
        return getYamlError(msg);
    }

    private static String formatMessage(DiagnosticErrorCode code, Object[] args) {
        String msgKey = MESSAGES.getString(ERROR_PREFIX + "." + code.messageKey());
        return MessageFormat.format(msgKey, args);
    }

    public static BError getYamlError(String message) {
        return ErrorCreator.createError(ModuleUtils.getModule(), ERROR, StringUtils.fromString(message),
                null, null);
    }
}
