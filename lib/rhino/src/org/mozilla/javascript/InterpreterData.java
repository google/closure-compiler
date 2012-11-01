/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.io.Serializable;

import org.mozilla.javascript.debug.DebuggableScript;

final class InterpreterData implements Serializable, DebuggableScript
{
    static final long serialVersionUID = 5067677351589230234L;

    static final int INITIAL_MAX_ICODE_LENGTH = 1024;
    static final int INITIAL_STRINGTABLE_SIZE = 64;
    static final int INITIAL_NUMBERTABLE_SIZE = 64;

    InterpreterData(int languageVersion, String sourceFile,
                    String encodedSource, boolean isStrict)
    {
        this.languageVersion = languageVersion;
        this.itsSourceFile = sourceFile;
        this.encodedSource = encodedSource;
        this.isStrict = isStrict;
        init();
    }

    InterpreterData(InterpreterData parent)
    {
        this.parentData = parent;
        this.languageVersion = parent.languageVersion;
        this.itsSourceFile = parent.itsSourceFile;
        this.encodedSource = parent.encodedSource;

        init();
    }

    private void init()
    {
        itsICode = new byte[INITIAL_MAX_ICODE_LENGTH];
        itsStringTable = new String[INITIAL_STRINGTABLE_SIZE];
    }

    String itsName;
    String itsSourceFile;
    boolean itsNeedsActivation;
    int itsFunctionType;

    String[] itsStringTable;
    double[] itsDoubleTable;
    InterpreterData[] itsNestedFunctions;
    Object[] itsRegExpLiterals;

    byte[] itsICode;

    int[] itsExceptionTable;

    int itsMaxVars;
    int itsMaxLocals;
    int itsMaxStack;
    int itsMaxFrameArray;

    // see comments in NativeFuncion for definition of argNames and argCount
    String[] argNames;
    boolean[] argIsConst;
    int argCount;

    int itsMaxCalleeArgs;

    String encodedSource;
    int encodedSourceStart;
    int encodedSourceEnd;

    int languageVersion;

    boolean isStrict;
    boolean topLevel;

    Object[] literalIds;

    UintMap longJumps;

    int firstLinePC = -1; // PC for the first LINE icode

    InterpreterData parentData;

    boolean evalScriptFlag; // true if script corresponds to eval() code

    public boolean isTopLevel()
    {
        return topLevel;
    }

    public boolean isFunction()
    {
        return itsFunctionType != 0;
    }

    public String getFunctionName()
    {
        return itsName;
    }

    public int getParamCount()
    {
        return argCount;
    }

    public int getParamAndVarCount()
    {
        return argNames.length;
    }

    public String getParamOrVarName(int index)
    {
        return argNames[index];
    }

    public boolean getParamOrVarConst(int index)
    {
        return argIsConst[index];
    }

    public String getSourceName()
    {
        return itsSourceFile;
    }

    public boolean isGeneratedScript()
    {
        return ScriptRuntime.isGeneratedScript(itsSourceFile);
    }

    public int[] getLineNumbers()
    {
        return Interpreter.getLineNumbers(this);
    }

    public int getFunctionCount()
    {
        return (itsNestedFunctions == null) ? 0 : itsNestedFunctions.length;
    }

    public DebuggableScript getFunction(int index)
    {
        return itsNestedFunctions[index];
    }

    public DebuggableScript getParent()
    {
         return parentData;
    }
}
