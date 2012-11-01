/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// API class

package org.mozilla.javascript.ast;

import org.mozilla.javascript.ErrorReporter;

/**
 * This is interface defines a protocol for the reporting of
 * errors during JavaScript translation in IDE-mode.
 * If the {@link org.mozilla.javascript.Parser}'s error reporter is
 * set to an instance of this interface, then this interface's
 * {@link #warning} and {@link #error} methods are called instead
 * of the {@link org.mozilla.javascript.ErrorReporter} versions. <p>
 *
 * These methods take a source char offset and a length.  The
 * rationale is that in interactive IDE-type environments, the source
 * is available and the IDE will want to indicate where the error
 * occurred and how much code participates in it.  The start and length
 * are generally chosen to fit within a single line, for readability,
 * but the client is free to use the AST to determine the affected
 * node(s) from the start position and change the error or warning's
 * display bounds.<p>
 *
 */
public interface IdeErrorReporter extends ErrorReporter {

    /**
     * Report a warning.<p>
     *
     * The implementing class may choose to ignore the warning
     * if it desires.
     *
     * @param message a {@code String} describing the warning
     * @param sourceName a {@code String} describing the JavaScript source
     * where the warning occured; typically a filename or URL
     * @param offset the warning's 0-indexed char position in the input stream
     * @param length the length of the region contributing to the warning
     */
    void warning(String message, String sourceName, int offset, int length);

    /**
     * Report an error.<p>
     *
     * The implementing class is free to throw an exception if
     * it desires.<p>
     *
     * If execution has not yet begun, the JavaScript engine is
     * free to find additional errors rather than terminating
     * the translation. It will not execute a script that had
     * errors, however.<p>
     *
     * @param message a String describing the error
     * @param sourceName a String describing the JavaScript source
     * where the error occured; typically a filename or URL
     * @param offset 0-indexed char position of the error in the input stream
     * @param length the length of the region contributing to the error
     */
    void error(String message, String sourceName, int offset, int length);
}
