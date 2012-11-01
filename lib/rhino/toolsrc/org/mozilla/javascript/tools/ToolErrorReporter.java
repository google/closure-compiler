/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tools;

import org.mozilla.javascript.*;

import java.text.MessageFormat;
import java.io.*;
import java.util.*;

/**
 * Error reporter for tools.
 *
 * Currently used by both the shell and the compiler.
 */
public class ToolErrorReporter implements ErrorReporter {

    public ToolErrorReporter(boolean reportWarnings) {
        this(reportWarnings, System.err);
    }

    public ToolErrorReporter(boolean reportWarnings, PrintStream err) {
        this.reportWarnings = reportWarnings;
        this.err = err;
    }

    /**
     * Look up the message corresponding to messageId in the
     * org.mozilla.javascript.tools.shell.resources.Messages property file.
     * For internationalization support.
     */
    public static String getMessage(String messageId) {
        return getMessage(messageId, (Object []) null);
    }

    public static String getMessage(String messageId, String argument) {
        Object[] args = { argument };
        return getMessage(messageId, args);
    }

    public static String getMessage(String messageId, Object arg1, Object arg2)
    {
        Object[] args = { arg1, arg2 };
        return getMessage(messageId, args);
    }

    public static String getMessage(String messageId, Object[] args) {
        Context cx = Context.getCurrentContext();
        Locale locale = cx == null ? Locale.getDefault() : cx.getLocale();

        // ResourceBundle does caching.
        ResourceBundle rb = ResourceBundle.getBundle
            ("org.mozilla.javascript.tools.resources.Messages", locale);

        String formatString;
        try {
            formatString = rb.getString(messageId);
        } catch (java.util.MissingResourceException mre) {
            throw new RuntimeException("no message resource found for message property "
                                       + messageId);
        }

        if (args == null) {
            return formatString;
        } else {
            MessageFormat formatter = new MessageFormat(formatString);
            return formatter.format(args);
        }
    }

    private static String getExceptionMessage(RhinoException ex)
    {
        String msg;
        if (ex instanceof JavaScriptException) {
            msg = getMessage("msg.uncaughtJSException", ex.details());
        } else if (ex instanceof EcmaError) {
            msg = getMessage("msg.uncaughtEcmaError", ex.details());
        } else if (ex instanceof EvaluatorException) {
            msg = ex.details();
        } else {
            msg = ex.toString();
        }
        return msg;
    }

    public void warning(String message, String sourceName, int line,
                        String lineSource, int lineOffset)
    {
        if (!reportWarnings)
            return;
        reportErrorMessage(message, sourceName, line, lineSource, lineOffset,
                           true);
    }

    public void error(String message, String sourceName, int line,
                      String lineSource, int lineOffset)
    {
        hasReportedErrorFlag = true;
        reportErrorMessage(message, sourceName, line, lineSource, lineOffset,
                           false);
    }

    public EvaluatorException runtimeError(String message, String sourceName,
                                           int line, String lineSource,
                                           int lineOffset)
    {
        return new EvaluatorException(message, sourceName, line,
                                      lineSource, lineOffset);
    }

    public boolean hasReportedError() {
        return hasReportedErrorFlag;
    }

    public boolean isReportingWarnings() {
        return this.reportWarnings;
    }

    public void setIsReportingWarnings(boolean reportWarnings) {
        this.reportWarnings = reportWarnings;
    }

    public static void reportException(ErrorReporter er, RhinoException ex)
    {
        if (er instanceof ToolErrorReporter) {
            ((ToolErrorReporter)er).reportException(ex);
        } else {
            String msg = getExceptionMessage(ex);
            er.error(msg, ex.sourceName(), ex.lineNumber(),
                     ex.lineSource(), ex.columnNumber());
        }
    }

    public void reportException(RhinoException ex)
    {
        if (ex instanceof WrappedException) {
            WrappedException we = (WrappedException)ex;
            we.printStackTrace(err);
        } else {
            String lineSeparator =
                SecurityUtilities.getSystemProperty("line.separator");
            String msg = getExceptionMessage(ex) + lineSeparator +
                ex.getScriptStackTrace();
            reportErrorMessage(msg, ex.sourceName(), ex.lineNumber(),
                               ex.lineSource(), ex.columnNumber(), false);
        }
    }

    private void reportErrorMessage(String message, String sourceName, int line,
                                    String lineSource, int lineOffset,
                                    boolean justWarning)
    {
        if (line > 0) {
            String lineStr = String.valueOf(line);
            if (sourceName != null) {
                Object[] args = { sourceName, lineStr, message };
                message = getMessage("msg.format3", args);
            } else {
                Object[] args = { lineStr, message };
                message = getMessage("msg.format2", args);
            }
        } else {
            Object[] args = { message };
            message = getMessage("msg.format1", args);
        }
        if (justWarning) {
            message = getMessage("msg.warning", message);
        }
        err.println(messagePrefix + message);
        if (null != lineSource) {
            err.println(messagePrefix + lineSource);
            err.println(messagePrefix + buildIndicator(lineOffset));
        }
    }

    private String buildIndicator(int offset){
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < offset-1; i++)
            sb.append(".");
        sb.append("^");
        return sb.toString();
    }

    private final static String messagePrefix = "js: ";
    private boolean hasReportedErrorFlag;
    private boolean reportWarnings;
    private PrintStream err;
}
