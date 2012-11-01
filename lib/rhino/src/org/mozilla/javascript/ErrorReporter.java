/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// API class

package org.mozilla.javascript;

/**
 * This is interface defines a protocol for the reporting of
 * errors during JavaScript translation or execution.
 *
 */

public interface ErrorReporter {

    /**
     * Report a warning.
     *
     * The implementing class may choose to ignore the warning
     * if it desires.
     *
     * @param message a String describing the warning
     * @param sourceName a String describing the JavaScript source
     * where the warning occured; typically a filename or URL
     * @param line the line number associated with the warning
     * @param lineSource the text of the line (may be null)
     * @param lineOffset the offset into lineSource where problem was detected
     */
    void warning(String message, String sourceName, int line,
                 String lineSource, int lineOffset);

    /**
     * Report an error.
     *
     * The implementing class is free to throw an exception if
     * it desires.
     *
     * If execution has not yet begun, the JavaScript engine is
     * free to find additional errors rather than terminating
     * the translation. It will not execute a script that had
     * errors, however.
     *
     * @param message a String describing the error
     * @param sourceName a String describing the JavaScript source
     * where the error occured; typically a filename or URL
     * @param line the line number associated with the error
     * @param lineSource the text of the line (may be null)
     * @param lineOffset the offset into lineSource where problem was detected
     */
    void error(String message, String sourceName, int line,
               String lineSource, int lineOffset);

    /**
     * Creates an EvaluatorException that may be thrown.
     *
     * runtimeErrors, unlike errors, will always terminate the
     * current script.
     *
     * @param message a String describing the error
     * @param sourceName a String describing the JavaScript source
     * where the error occured; typically a filename or URL
     * @param line the line number associated with the error
     * @param lineSource the text of the line (may be null)
     * @param lineOffset the offset into lineSource where problem was detected
     * @return an EvaluatorException that will be thrown.
     */
    EvaluatorException runtimeError(String message, String sourceName,
                                    int line, String lineSource,
                                    int lineOffset);
}
