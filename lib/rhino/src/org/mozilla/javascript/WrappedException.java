/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

/**
 * A wrapper for runtime exceptions.
 *
 * Used by the JavaScript runtime to wrap and propagate exceptions that occur
 * during runtime.
 *
 */
public class WrappedException extends EvaluatorException
{
    static final long serialVersionUID = -1551979216966520648L;

    /**
     * @see Context#throwAsScriptRuntimeEx(Throwable e)
     */
    public WrappedException(Throwable exception)
    {
        super("Wrapped "+exception.toString());
        this.exception = exception;
        Kit.initCause(this, exception);

        int[] linep = { 0 };
        String sourceName = Context.getSourcePositionFromStack(linep);
        int lineNumber = linep[0];
        if (sourceName != null) {
            initSourceName(sourceName);
        }
        if (lineNumber != 0) {
            initLineNumber(lineNumber);
        }
    }

    /**
     * Get the wrapped exception.
     *
     * @return the exception that was presented as a argument to the
     *         constructor when this object was created
     */
    public Throwable getWrappedException()
    {
        return exception;
    }

    /**
     * @deprecated Use {@link #getWrappedException()} instead.
     */
    public Object unwrap()
    {
        return getWrappedException();
    }

    private Throwable exception;
}
