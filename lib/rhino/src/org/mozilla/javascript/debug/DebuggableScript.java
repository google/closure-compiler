/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// API class

package org.mozilla.javascript.debug;

/**
 * This interface exposes debugging information from executable
 * code (either functions or top-level scripts).
 */
public interface DebuggableScript
{
    public boolean isTopLevel();

    /**
     * Returns true if this is a function, false if it is a script.
     */
    public boolean isFunction();

    /**
     * Get name of the function described by this script.
     * Return null or an empty string if this script is not a function.
     */
    public String getFunctionName();

    /**
     * Get number of declared parameters in the function.
     * Return 0 if this script is not a function.
     *
     * @see #getParamAndVarCount()
     * @see #getParamOrVarName(int index)
     */
    public int getParamCount();

    /**
     * Get number of declared parameters and local variables.
     * Return number of declared global variables if this script is not a
     * function.
     *
     * @see #getParamCount()
     * @see #getParamOrVarName(int index)
     */
    public int getParamAndVarCount();

    /**
     * Get name of a declared parameter or local variable.
     * <tt>index</tt> should be less then {@link #getParamAndVarCount()}.
     * If <tt>index&nbsp;&lt;&nbsp;{@link #getParamCount()}</tt>, return
     * the name of the corresponding parameter, otherwise return the name
     * of variable.
     * If this script is not function, return the name of the declared
     * global variable.
     */
    public String getParamOrVarName(int index);

    /**
     * Get the name of the source (usually filename or URL)
     * of the script.
     */
    public String getSourceName();

    /**
     * Returns true if this script or function were runtime-generated
     * from JavaScript using <tt>eval</tt> function or <tt>Function</tt>
     * or <tt>Script</tt> constructors.
     */
    public boolean isGeneratedScript();

    /**
     * Get array containing the line numbers that
     * that can be passed to <code>DebugFrame.onLineChange()<code>.
     * Note that line order in the resulting array is arbitrary
     */
    public int[] getLineNumbers();

    public int getFunctionCount();

    public DebuggableScript getFunction(int index);

    public DebuggableScript getParent();

}
