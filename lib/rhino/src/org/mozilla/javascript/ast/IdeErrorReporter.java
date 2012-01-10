/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Steve Yegge
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

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
