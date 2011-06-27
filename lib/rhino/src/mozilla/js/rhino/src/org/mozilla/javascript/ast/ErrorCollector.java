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
 *   Norris Boyd
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

package org.mozilla.javascript.ast;

import org.mozilla.javascript.EvaluatorException;

import java.util.ArrayList;
import java.util.List;

/**
 * An error reporter that gathers the errors and warnings for later display.
 * This a useful {@link org.mozilla.javascript.ErrorReporter} when the
 * {@link org.mozilla.javascript.CompilerEnvirons} is set to
 * ide-mode (for IDEs).
 *
 */
public class ErrorCollector implements IdeErrorReporter {

    private List<ParseProblem> errors = new ArrayList<ParseProblem>();

    /**
     * This is not called during AST generation.
     * {@link #warning(String,String,int,int)} is used instead.
     * @throws UnsupportedOperationException
     */
    public void warning(String message, String sourceName, int line,
                        String lineSource, int lineOffset) {
        throw new UnsupportedOperationException();
    }

    /**
     * @inheritDoc
     */
    public void warning(String message, String sourceName, int offset, int length)
    {
        errors.add(new ParseProblem(ParseProblem.Type.Warning,
                                    message, sourceName,
                                    offset, length));
    }

    /**
     * This is not called during AST generation.
     * {@link #warning(String,String,int,int)} is used instead.
     * @throws UnsupportedOperationException
     */
    public void error(String message, String sourceName, int line,
                      String lineSource, int lineOffset)
    {
        throw new UnsupportedOperationException();
    }

    /**
     * @inheritDoc
     */
    public void error(String message, String sourceName,
                      int fileOffset, int length)
    {
        errors.add(new ParseProblem(ParseProblem.Type.Error,
                                    message, sourceName,
                                    fileOffset, length));
    }

    /**
     * @inheritDoc
     */
    public EvaluatorException runtimeError(String message, String sourceName,
                                           int line, String lineSource,
                                           int lineOffset)
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the list of errors and warnings produced during parsing.
     */
    public List<ParseProblem> getErrors() {
        return errors;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(errors.size() * 100);
        for (ParseProblem pp : errors) {
            sb.append(pp.toString()).append("\n");
        }
        return sb.toString();
    }
}
