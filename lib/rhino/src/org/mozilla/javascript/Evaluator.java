/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import org.mozilla.javascript.ast.ScriptNode;

import java.util.List;

/**
 * Abstraction of evaluation, which can be implemented either by an
 * interpreter or compiler.
 */
public interface Evaluator {

    /**
     * Compile the script or function from intermediate representation
     * tree into an executable form.
     *
     * @param compilerEnv Compiler environment
     * @param tree parse tree
     * @param encodedSource encoding of the source code for decompilation
     * @param returnFunction if true, compiling a function
     * @return an opaque object that can be passed to either
     *         createFunctionObject or createScriptObject, depending on the
     *         value of returnFunction
     */
    public Object compile(CompilerEnvirons compilerEnv,
                          ScriptNode tree,
                          String encodedSource,
                          boolean returnFunction);

    /**
     * Create a function object.
     *
     * @param cx Current context
     * @param scope scope of the function
     * @param bytecode opaque object returned by compile
     * @param staticSecurityDomain security domain
     * @return Function object that can be called
     */
    public Function createFunctionObject(Context cx, Scriptable scope,
            Object bytecode, Object staticSecurityDomain);

    /**
     * Create a script object.
     *
     * @param bytecode opaque object returned by compile
     * @param staticSecurityDomain security domain
     * @return Script object that can be evaluated
     */
    public Script createScriptObject(Object bytecode,
                                     Object staticSecurityDomain);

    /**
     * Capture stack information from the given exception.
     * @param ex an exception thrown during execution
     */
    public void captureStackInfo(RhinoException ex);

    /**
     * Get the source position information by examining the stack.
     * @param cx Context
     * @param linep Array object of length >= 1; getSourcePositionFromStack
     *              will assign the line number to linep[0].
     * @return the name of the file or other source container
     */
    public String getSourcePositionFromStack(Context cx, int[] linep);

    /**
     * Given a native stack trace, patch it with script-specific source
     * and line information
     * @param ex exception
     * @param nativeStackTrace the native stack trace
     * @return patched stack trace
     */
    public String getPatchedStack(RhinoException ex,
                                  String nativeStackTrace);

    /**
     * Get the script stack for the given exception
     * @param ex exception from execution
     * @return list of strings for the stack trace
     */
    public List<String> getScriptStack(RhinoException ex);

    /**
     * Mark the given script to indicate it was created by a call to
     * eval() or to a Function constructor.
     * @param script script to mark as from eval
     */
    public void setEvalScriptFlag(Script script);
}
