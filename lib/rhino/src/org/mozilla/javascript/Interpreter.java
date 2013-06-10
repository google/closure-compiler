/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.ast.ScriptNode;
import org.mozilla.javascript.ScriptRuntime.NoSuchMethodShim;
import org.mozilla.javascript.debug.DebugFrame;

import static org.mozilla.javascript.UniqueTag.DOUBLE_MARK;

public final class Interpreter extends Icode implements Evaluator
{
    // data for parsing
    InterpreterData itsData;

    static final int EXCEPTION_TRY_START_SLOT  = 0;
    static final int EXCEPTION_TRY_END_SLOT    = 1;
    static final int EXCEPTION_HANDLER_SLOT    = 2;
    static final int EXCEPTION_TYPE_SLOT       = 3;
    static final int EXCEPTION_LOCAL_SLOT      = 4;
    static final int EXCEPTION_SCOPE_SLOT      = 5;
    // SLOT_SIZE: space for try start/end, handler, start, handler type,
    //            exception local and scope local
    static final int EXCEPTION_SLOT_SIZE       = 6;

    /**
     * Class to hold data corresponding to one interpreted call stack frame.
     */
    private static class CallFrame implements Cloneable, Serializable
    {
        static final long serialVersionUID = -2843792508994958978L;

        CallFrame parentFrame;
        // amount of stack frames before this one on the interpretation stack
        int frameIndex;
        // If true indicates read-only frame that is a part of continuation
        boolean frozen;

        InterpretedFunction fnOrScript;
        InterpreterData idata;

// Stack structure
// stack[0 <= i < localShift]: arguments and local variables
// stack[localShift <= i <= emptyStackTop]: used for local temporaries
// stack[emptyStackTop < i < stack.length]: stack data
// sDbl[i]: if stack[i] is UniqueTag.DOUBLE_MARK, sDbl[i] holds the number value

        Object[] stack;
        int[] stackAttributes;
        double[] sDbl;
        CallFrame varSource; // defaults to this unless continuation frame
        int localShift;
        int emptyStackTop;

        DebugFrame debuggerFrame;
        boolean useActivation;
        boolean isContinuationsTopFrame;

        Scriptable thisObj;

// The values that change during interpretation

        Object result;
        double resultDbl;
        int pc;
        int pcPrevBranch;
        int pcSourceLineStart;
        Scriptable scope;

        int savedStackTop;
        int savedCallOp;
        Object throwable;

        CallFrame cloneFrozen()
        {
            if (!frozen) Kit.codeBug();

            CallFrame copy;
            try {
                copy = (CallFrame)clone();
            } catch (CloneNotSupportedException ex) {
                throw new IllegalStateException();
            }

            // clone stack but keep varSource to point to values
            // from this frame to share variables.

            copy.stack = stack.clone();
            copy.stackAttributes = stackAttributes.clone();
            copy.sDbl = sDbl.clone();

            copy.frozen = false;
            return copy;
        }
    }

    private static final class ContinuationJump implements Serializable
    {
        static final long serialVersionUID = 7687739156004308247L;

        CallFrame capturedFrame;
        CallFrame branchFrame;
        Object result;
        double resultDbl;

        ContinuationJump(NativeContinuation c, CallFrame current)
        {
            this.capturedFrame = (CallFrame)c.getImplementation();
            if (this.capturedFrame == null || current == null) {
                // Continuation and current execution does not share
                // any frames if there is nothing to capture or
                // if there is no currently executed frames
                this.branchFrame = null;
            } else {
                // Search for branch frame where parent frame chains starting
                // from captured and current meet.
                CallFrame chain1 = this.capturedFrame;
                CallFrame chain2 = current;

                // First work parents of chain1 or chain2 until the same
                // frame depth.
                int diff = chain1.frameIndex - chain2.frameIndex;
                if (diff != 0) {
                    if (diff < 0) {
                        // swap to make sure that
                        // chain1.frameIndex > chain2.frameIndex and diff > 0
                        chain1 = current;
                        chain2 = this.capturedFrame;
                        diff = -diff;
                    }
                    do {
                        chain1 = chain1.parentFrame;
                    } while (--diff != 0);
                    if (chain1.frameIndex != chain2.frameIndex) Kit.codeBug();
                }

                // Now walk parents in parallel until a shared frame is found
                // or until the root is reached.
                while (chain1 != chain2 && chain1 != null) {
                    chain1 = chain1.parentFrame;
                    chain2 = chain2.parentFrame;
                }

                this.branchFrame = chain1;
                if (this.branchFrame != null && !this.branchFrame.frozen)
                    Kit.codeBug();
            }
        }
    }

    private static CallFrame captureFrameForGenerator(CallFrame frame) {
      frame.frozen = true;
      CallFrame result = frame.cloneFrozen();
      frame.frozen = false;

      // now isolate this frame from its previous context
      result.parentFrame = null;
      result.frameIndex = 0;

      return result;
    }

    static {
        // Checks for byte code consistencies, good compiler can eliminate them

        if (Token.LAST_BYTECODE_TOKEN > 127) {
            String str = "Violation of Token.LAST_BYTECODE_TOKEN <= 127";
            System.err.println(str);
            throw new IllegalStateException(str);
        }
        if (MIN_ICODE < -128) {
            String str = "Violation of Interpreter.MIN_ICODE >= -128";
            System.err.println(str);
            throw new IllegalStateException(str);
        }
    }

    public Object compile(CompilerEnvirons compilerEnv,
                          ScriptNode tree,
                          String encodedSource,
                          boolean returnFunction)
    {
        CodeGenerator cgen = new CodeGenerator();
        itsData = cgen.compile(compilerEnv, tree, encodedSource, returnFunction);
        return itsData;
    }

    public Script createScriptObject(Object bytecode, Object staticSecurityDomain)
    {
        if(bytecode != itsData)
        {
            Kit.codeBug();
        }
        return InterpretedFunction.createScript(itsData,
                                                staticSecurityDomain);
    }

    public void setEvalScriptFlag(Script script) {
        ((InterpretedFunction)script).idata.evalScriptFlag = true;
    }


    public Function createFunctionObject(Context cx, Scriptable scope,
            Object bytecode, Object staticSecurityDomain)
    {
        if(bytecode != itsData)
        {
            Kit.codeBug();
        }
        return InterpretedFunction.createFunction(cx, scope, itsData,
                                                  staticSecurityDomain);
    }

    private static int getShort(byte[] iCode, int pc) {
        return (iCode[pc] << 8) | (iCode[pc + 1] & 0xFF);
    }

    private static int getIndex(byte[] iCode, int pc) {
        return ((iCode[pc] & 0xFF) << 8) | (iCode[pc + 1] & 0xFF);
    }

    private static int getInt(byte[] iCode, int pc) {
        return (iCode[pc] << 24) | ((iCode[pc + 1] & 0xFF) << 16)
               | ((iCode[pc + 2] & 0xFF) << 8) | (iCode[pc + 3] & 0xFF);
    }

    private static int getExceptionHandler(CallFrame frame,
                                           boolean onlyFinally)
    {
        int[] exceptionTable = frame.idata.itsExceptionTable;
        if (exceptionTable == null) {
            // No exception handlers
            return -1;
        }

        // Icode switch in the interpreter increments PC immediately
        // and it is necessary to subtract 1 from the saved PC
        // to point it before the start of the next instruction.
        int pc = frame.pc - 1;

        // OPT: use binary search
        int best = -1, bestStart = 0, bestEnd = 0;
        for (int i = 0; i != exceptionTable.length; i += EXCEPTION_SLOT_SIZE) {
            int start = exceptionTable[i + EXCEPTION_TRY_START_SLOT];
            int end = exceptionTable[i + EXCEPTION_TRY_END_SLOT];
            if (!(start <= pc && pc < end)) {
                continue;
            }
            if (onlyFinally && exceptionTable[i + EXCEPTION_TYPE_SLOT] != 1) {
                continue;
            }
            if (best >= 0) {
                // Since handlers always nest and they never have shared end
                // although they can share start  it is sufficient to compare
                // handlers ends
                if (bestEnd < end) {
                    continue;
                }
                // Check the above assumption
                if (bestStart > start) Kit.codeBug(); // should be nested
                if (bestEnd == end) Kit.codeBug();  // no ens sharing
            }
            best = i;
            bestStart = start;
            bestEnd = end;
        }
        return best;
    }

    static void dumpICode(InterpreterData idata)
    {
        if (!Token.printICode) {
            return;
        }

        byte iCode[] = idata.itsICode;
        int iCodeLength = iCode.length;
        String[] strings = idata.itsStringTable;
        PrintStream out = System.out;
        out.println("ICode dump, for " + idata.itsName
                    + ", length = " + iCodeLength);
        out.println("MaxStack = " + idata.itsMaxStack);

        int indexReg = 0;
        for (int pc = 0; pc < iCodeLength; ) {
            out.flush();
            out.print(" [" + pc + "] ");
            int token = iCode[pc];
            int icodeLength = bytecodeSpan(token);
            String tname = Icode.bytecodeName(token);
            int old_pc = pc;
            ++pc;
            switch (token) {
              default:
                if (icodeLength != 1) Kit.codeBug();
                out.println(tname);
                break;

              case Icode_GOSUB :
              case Token.GOTO :
              case Token.IFEQ :
              case Token.IFNE :
              case Icode_IFEQ_POP :
              case Icode_LEAVEDQ : {
                int newPC = pc + getShort(iCode, pc) - 1;
                out.println(tname + " " + newPC);
                pc += 2;
                break;
              }
              case Icode_VAR_INC_DEC :
              case Icode_NAME_INC_DEC :
              case Icode_PROP_INC_DEC :
              case Icode_ELEM_INC_DEC :
              case Icode_REF_INC_DEC: {
                int incrDecrType = iCode[pc];
                out.println(tname + " " + incrDecrType);
                ++pc;
                break;
              }

              case Icode_CALLSPECIAL : {
                int callType = iCode[pc] & 0xFF;
                boolean isNew =  (iCode[pc + 1] != 0);
                int line = getIndex(iCode, pc+2);
                out.println(tname+" "+callType+" "+isNew+" "+indexReg+" "+line);
                pc += 4;
                break;
              }

              case Token.CATCH_SCOPE:
                {
                    boolean afterFisrtFlag =  (iCode[pc] != 0);
                    out.println(tname+" "+afterFisrtFlag);
                    ++pc;
                }
                break;
              case Token.REGEXP :
                out.println(tname+" "+idata.itsRegExpLiterals[indexReg]);
                break;
              case Token.OBJECTLIT :
              case Icode_SPARE_ARRAYLIT :
                out.println(tname+" "+idata.literalIds[indexReg]);
                break;
              case Icode_CLOSURE_EXPR :
              case Icode_CLOSURE_STMT :
                out.println(tname+" "+idata.itsNestedFunctions[indexReg]);
                break;
              case Token.CALL :
              case Icode_TAIL_CALL :
              case Token.REF_CALL :
              case Token.NEW :
                out.println(tname+' '+indexReg);
                break;
              case Token.THROW :
              case Token.YIELD :
              case Icode_GENERATOR :
              case Icode_GENERATOR_END :
              {
                int line = getIndex(iCode, pc);
                out.println(tname + " : " + line);
                pc += 2;
                break;
              }
              case Icode_SHORTNUMBER : {
                int value = getShort(iCode, pc);
                out.println(tname + " " + value);
                pc += 2;
                break;
              }
              case Icode_INTNUMBER : {
                int value = getInt(iCode, pc);
                out.println(tname + " " + value);
                pc += 4;
                break;
              }
              case Token.NUMBER : {
                double value = idata.itsDoubleTable[indexReg];
                out.println(tname + " " + value);
                break;
              }
              case Icode_LINE : {
                int line = getIndex(iCode, pc);
                out.println(tname + " : " + line);
                pc += 2;
                break;
              }
              case Icode_REG_STR1: {
                String str = strings[0xFF & iCode[pc]];
                out.println(tname + " \"" + str + '"');
                ++pc;
                break;
              }
              case Icode_REG_STR2: {
                String str = strings[getIndex(iCode, pc)];
                out.println(tname + " \"" + str + '"');
                pc += 2;
                break;
              }
              case Icode_REG_STR4: {
                String str = strings[getInt(iCode, pc)];
                out.println(tname + " \"" + str + '"');
                pc += 4;
                break;
              }
              case Icode_REG_IND_C0:
                  indexReg = 0;
                  out.println(tname);
                  break;
              case Icode_REG_IND_C1:
                  indexReg = 1;
                  out.println(tname);
                  break;
              case Icode_REG_IND_C2:
                  indexReg = 2;
                  out.println(tname);
                  break;
              case Icode_REG_IND_C3:
                  indexReg = 3;
                  out.println(tname);
                  break;
              case Icode_REG_IND_C4:
                  indexReg = 4;
                  out.println(tname);
                  break;
              case Icode_REG_IND_C5:
                  indexReg = 5;
                  out.println(tname);
                  break;
              case Icode_REG_IND1: {
                indexReg = 0xFF & iCode[pc];
                out.println(tname+" "+indexReg);
                ++pc;
                break;
              }
              case Icode_REG_IND2: {
                indexReg = getIndex(iCode, pc);
                out.println(tname+" "+indexReg);
                pc += 2;
                break;
              }
              case Icode_REG_IND4: {
                indexReg = getInt(iCode, pc);
                out.println(tname+" "+indexReg);
                pc += 4;
                break;
              }
              case Icode_GETVAR1:
              case Icode_SETVAR1:
              case Icode_SETCONSTVAR1:
                indexReg = iCode[pc];
                out.println(tname+" "+indexReg);
                ++pc;
                break;
            }
            if (old_pc + icodeLength != pc) Kit.codeBug();
        }

        int[] table = idata.itsExceptionTable;
        if (table != null) {
            out.println("Exception handlers: "
                         +table.length / EXCEPTION_SLOT_SIZE);
            for (int i = 0; i != table.length;
                 i += EXCEPTION_SLOT_SIZE)
            {
                int tryStart       = table[i + EXCEPTION_TRY_START_SLOT];
                int tryEnd         = table[i + EXCEPTION_TRY_END_SLOT];
                int handlerStart   = table[i + EXCEPTION_HANDLER_SLOT];
                int type           = table[i + EXCEPTION_TYPE_SLOT];
                int exceptionLocal = table[i + EXCEPTION_LOCAL_SLOT];
                int scopeLocal     = table[i + EXCEPTION_SCOPE_SLOT];

                out.println(" tryStart="+tryStart+" tryEnd="+tryEnd
                            +" handlerStart="+handlerStart
                            +" type="+(type == 0 ? "catch" : "finally")
                            +" exceptionLocal="+exceptionLocal);
            }
        }
        out.flush();
    }

    private static int bytecodeSpan(int bytecode)
    {
        switch (bytecode) {
            case Token.THROW :
            case Token.YIELD:
            case Icode_GENERATOR:
            case Icode_GENERATOR_END:
                // source line
                return 1 + 2;

            case Icode_GOSUB :
            case Token.GOTO :
            case Token.IFEQ :
            case Token.IFNE :
            case Icode_IFEQ_POP :
            case Icode_LEAVEDQ :
                // target pc offset
                return 1 + 2;

            case Icode_CALLSPECIAL :
                // call type
                // is new
                // line number
                return 1 + 1 + 1 + 2;

            case Token.CATCH_SCOPE:
                // scope flag
                return 1 + 1;

            case Icode_VAR_INC_DEC:
            case Icode_NAME_INC_DEC:
            case Icode_PROP_INC_DEC:
            case Icode_ELEM_INC_DEC:
            case Icode_REF_INC_DEC:
                // type of ++/--
                return 1 + 1;

            case Icode_SHORTNUMBER :
                // short number
                return 1 + 2;

            case Icode_INTNUMBER :
                // int number
                return 1 + 4;

            case Icode_REG_IND1:
                // ubyte index
                return 1 + 1;

            case Icode_REG_IND2:
                // ushort index
                return 1 + 2;

            case Icode_REG_IND4:
                // int index
                return 1 + 4;

            case Icode_REG_STR1:
                // ubyte string index
                return 1 + 1;

            case Icode_REG_STR2:
                // ushort string index
                return 1 + 2;

            case Icode_REG_STR4:
                // int string index
                return 1 + 4;

            case Icode_GETVAR1:
            case Icode_SETVAR1:
            case Icode_SETCONSTVAR1:
                // byte var index
                return 1 + 1;

            case Icode_LINE :
                // line number
                return 1 + 2;
        }
        if (!validBytecode(bytecode)) throw Kit.codeBug();
        return 1;
    }

    static int[] getLineNumbers(InterpreterData data)
    {
        UintMap presentLines = new UintMap();

        byte[] iCode = data.itsICode;
        int iCodeLength = iCode.length;
        for (int pc = 0; pc != iCodeLength;) {
            int bytecode = iCode[pc];
            int span = bytecodeSpan(bytecode);
            if (bytecode == Icode_LINE) {
                if (span != 3) Kit.codeBug();
                int line = getIndex(iCode, pc + 1);
                presentLines.put(line, 0);
            }
            pc += span;
        }

        return presentLines.getKeys();
    }

    public void captureStackInfo(RhinoException ex)
    {
        Context cx = Context.getCurrentContext();
        if (cx == null || cx.lastInterpreterFrame == null) {
            // No interpreter invocations
            ex.interpreterStackInfo = null;
            ex.interpreterLineData = null;
            return;
        }
        // has interpreter frame on the stack
        CallFrame[] array;
        if (cx.previousInterpreterInvocations == null
            || cx.previousInterpreterInvocations.size() == 0)
        {
            array = new CallFrame[1];
        } else {
            int previousCount = cx.previousInterpreterInvocations.size();
            if (cx.previousInterpreterInvocations.peek()
                == cx.lastInterpreterFrame)
            {
                // It can happen if exception was generated after
                // frame was pushed to cx.previousInterpreterInvocations
                // but before assignment to cx.lastInterpreterFrame.
                // In this case frames has to be ignored.
                --previousCount;
            }
            array = new CallFrame[previousCount + 1];
            cx.previousInterpreterInvocations.toArray(array);
        }
        array[array.length - 1]  = (CallFrame)cx.lastInterpreterFrame;

        int interpreterFrameCount = 0;
        for (int i = 0; i != array.length; ++i) {
            interpreterFrameCount += 1 + array[i].frameIndex;
        }

        int[] linePC = new int[interpreterFrameCount];
        // Fill linePC with pc positions from all interpreter frames.
        // Start from the most nested frame
        int linePCIndex = interpreterFrameCount;
        for (int i = array.length; i != 0;) {
            --i;
            CallFrame frame = array[i];
            while (frame != null) {
                --linePCIndex;
                linePC[linePCIndex] = frame.pcSourceLineStart;
                frame = frame.parentFrame;
            }
        }
        if (linePCIndex != 0) Kit.codeBug();

        ex.interpreterStackInfo = array;
        ex.interpreterLineData = linePC;
    }

    public String getSourcePositionFromStack(Context cx, int[] linep)
    {
        CallFrame frame = (CallFrame)cx.lastInterpreterFrame;
        InterpreterData idata = frame.idata;
        if (frame.pcSourceLineStart >= 0) {
            linep[0] = getIndex(idata.itsICode, frame.pcSourceLineStart);
        } else {
            linep[0] = 0;
        }
        return idata.itsSourceFile;
    }

    public String getPatchedStack(RhinoException ex,
                                  String nativeStackTrace)
    {
        String tag = "org.mozilla.javascript.Interpreter.interpretLoop";
        StringBuffer sb = new StringBuffer(nativeStackTrace.length() + 1000);
        String lineSeparator = SecurityUtilities.getSystemProperty("line.separator");

        CallFrame[] array = (CallFrame[])ex.interpreterStackInfo;
        int[] linePC = ex.interpreterLineData;
        int arrayIndex = array.length;
        int linePCIndex = linePC.length;
        int offset = 0;
        while (arrayIndex != 0) {
            --arrayIndex;
            int pos = nativeStackTrace.indexOf(tag, offset);
            if (pos < 0) {
                break;
            }

            // Skip tag length
            pos += tag.length();
            // Skip until the end of line
            for (; pos != nativeStackTrace.length(); ++pos) {
                char c = nativeStackTrace.charAt(pos);
                if (c == '\n' || c == '\r') {
                    break;
                }
            }
            sb.append(nativeStackTrace.substring(offset, pos));
            offset = pos;

            CallFrame frame = array[arrayIndex];
            while (frame != null) {
                if (linePCIndex == 0) Kit.codeBug();
                --linePCIndex;
                InterpreterData idata = frame.idata;
                sb.append(lineSeparator);
                sb.append("\tat script");
                if (idata.itsName != null && idata.itsName.length() != 0) {
                    sb.append('.');
                    sb.append(idata.itsName);
                }
                sb.append('(');
                sb.append(idata.itsSourceFile);
                int pc = linePC[linePCIndex];
                if (pc >= 0) {
                    // Include line info only if available
                    sb.append(':');
                    sb.append(getIndex(idata.itsICode, pc));
                }
                sb.append(')');
                frame = frame.parentFrame;
            }
        }
        sb.append(nativeStackTrace.substring(offset));

        return sb.toString();
    }

    public List<String> getScriptStack(RhinoException ex) {
        ScriptStackElement[][] stack = getScriptStackElements(ex);
        List<String> list = new ArrayList<String>(stack.length);
        String lineSeparator =
                SecurityUtilities.getSystemProperty("line.separator");
        for (ScriptStackElement[] group : stack) {
            StringBuilder sb = new StringBuilder();
            for (ScriptStackElement elem : group) {
                elem.renderJavaStyle(sb);
                sb.append(lineSeparator);
            }
            list.add(sb.toString());
        }
        return list;
    }

    public ScriptStackElement[][] getScriptStackElements(RhinoException ex)
    {
        if (ex.interpreterStackInfo == null) {
            return null;
        }

        List<ScriptStackElement[]> list = new ArrayList<ScriptStackElement[]>();

        CallFrame[] array = (CallFrame[])ex.interpreterStackInfo;
        int[] linePC = ex.interpreterLineData;
        int arrayIndex = array.length;
        int linePCIndex = linePC.length;
        while (arrayIndex != 0) {
            --arrayIndex;
            CallFrame frame = array[arrayIndex];
            List<ScriptStackElement> group = new ArrayList<ScriptStackElement>();
            while (frame != null) {
                if (linePCIndex == 0) Kit.codeBug();
                --linePCIndex;
                InterpreterData idata = frame.idata;
                String fileName = idata.itsSourceFile;
                String functionName = null;
                int lineNumber = -1;
                int pc = linePC[linePCIndex];
                if (pc >= 0) {
                    lineNumber = getIndex(idata.itsICode, pc);
                }
                if (idata.itsName != null && idata.itsName.length() != 0) {
                    functionName = idata.itsName;
                }
                frame = frame.parentFrame;
                group.add(new ScriptStackElement(fileName, functionName, lineNumber));
            }
            list.add(group.toArray(new ScriptStackElement[group.size()]));
        }
        return list.toArray(new ScriptStackElement[list.size()][]);
    }

    static String getEncodedSource(InterpreterData idata)
    {
        if (idata.encodedSource == null) {
            return null;
        }
        return idata.encodedSource.substring(idata.encodedSourceStart,
                                             idata.encodedSourceEnd);
    }

    private static void initFunction(Context cx, Scriptable scope,
                                     InterpretedFunction parent, int index)
    {
        InterpretedFunction fn;
        fn = InterpretedFunction.createFunction(cx, scope, parent, index);
        ScriptRuntime.initFunction(cx, scope, fn, fn.idata.itsFunctionType,
                                   parent.idata.evalScriptFlag);
    }

    static Object interpret(InterpretedFunction ifun,
                            Context cx, Scriptable scope,
                            Scriptable thisObj, Object[] args)
    {
        if (!ScriptRuntime.hasTopCall(cx)) Kit.codeBug();

        if (cx.interpreterSecurityDomain != ifun.securityDomain) {
            Object savedDomain = cx.interpreterSecurityDomain;
            cx.interpreterSecurityDomain = ifun.securityDomain;
            try {
                return ifun.securityController.callWithDomain(
                    ifun.securityDomain, cx, ifun, scope, thisObj, args);
            } finally {
                cx.interpreterSecurityDomain = savedDomain;
            }
        }

        CallFrame frame = new CallFrame();
        initFrame(cx, scope, thisObj, args, null, 0, args.length,
                  ifun, null, frame);
        frame.isContinuationsTopFrame = cx.isContinuationsTopCall;
        cx.isContinuationsTopCall = false;

        return interpretLoop(cx, frame, null);
    }

    static class GeneratorState {
        GeneratorState(int operation, Object value) {
            this.operation = operation;
            this.value = value;
        }
        int operation;
        Object value;
        RuntimeException returnedException;
    }

    public static Object resumeGenerator(Context cx,
                                         Scriptable scope,
                                         int operation,
                                         Object savedState,
                                         Object value)
    {
      CallFrame frame = (CallFrame) savedState;
      GeneratorState generatorState = new GeneratorState(operation, value);
      if (operation == NativeGenerator.GENERATOR_CLOSE) {
          try {
              return interpretLoop(cx, frame, generatorState);
          } catch (RuntimeException e) {
              // Only propagate exceptions other than closingException
              if (e != value)
                  throw e;
          }
          return Undefined.instance;
      }
      Object result = interpretLoop(cx, frame, generatorState);
      if (generatorState.returnedException != null)
          throw generatorState.returnedException;
      return result;
    }

    public static Object restartContinuation(NativeContinuation c, Context cx,
                                             Scriptable scope, Object[] args)
    {
        if (!ScriptRuntime.hasTopCall(cx)) {
            return ScriptRuntime.doTopCall(c, cx, scope, null, args);
        }

        Object arg;
        if (args.length == 0) {
            arg = Undefined.instance;
        } else {
            arg = args[0];
        }

        CallFrame capturedFrame = (CallFrame)c.getImplementation();
        if (capturedFrame == null) {
            // No frames to restart
            return arg;
        }

        ContinuationJump cjump = new ContinuationJump(c, null);

        cjump.result = arg;
        return interpretLoop(cx, null, cjump);
    }

    private static Object interpretLoop(Context cx, CallFrame frame,
                                        Object throwable)
    {
        // throwable holds exception object to rethrow or catch
        // It is also used for continuation restart in which case
        // it holds ContinuationJump

        final Object DBL_MRK = DOUBLE_MARK;
        final Object undefined = Undefined.instance;

        final boolean instructionCounting = (cx.instructionThreshold != 0);
        // arbitrary number to add to instructionCount when calling
        // other functions
        final int INVOCATION_COST = 100;
        // arbitrary exception cost for instruction counting
        final int EXCEPTION_COST = 100;

        String stringReg = null;
        int indexReg = -1;

        if (cx.lastInterpreterFrame != null) {
            // save the top frame from the previous interpretLoop
            // invocation on the stack
            if (cx.previousInterpreterInvocations == null) {
                cx.previousInterpreterInvocations = new ObjArray();
            }
            cx.previousInterpreterInvocations.push(cx.lastInterpreterFrame);
        }

        // When restarting continuation throwable is not null and to jump
        // to the code that rewind continuation state indexReg should be set
        // to -1.
        // With the normal call throwable == null and indexReg == -1 allows to
        // catch bugs with using indeReg to access array elements before
        // initializing indexReg.

        GeneratorState generatorState = null;
        if (throwable != null) {
            if (throwable instanceof GeneratorState) {
              generatorState = (GeneratorState) throwable;

              // reestablish this call frame
              enterFrame(cx, frame, ScriptRuntime.emptyArgs, true);
              throwable = null;
            } else if (!(throwable instanceof ContinuationJump)) {
                // It should be continuation
                Kit.codeBug();
            }
        }

        Object interpreterResult = null;
        double interpreterResultDbl = 0.0;

        StateLoop: for (;;) {
            withoutExceptions: try {

                if (throwable != null) {
                    // Need to return both 'frame' and 'throwable' from
                    // 'processThrowable', so just added a 'throwable'
                    // member in 'frame'.
                    frame = processThrowable(cx, throwable, frame, indexReg,
                                             instructionCounting);
                    throwable = frame.throwable;
                    frame.throwable = null;
                } else {
                    if (generatorState == null && frame.frozen) Kit.codeBug();
                }

                // Use local variables for constant values in frame
                // for faster access
                Object[] stack = frame.stack;
                double[] sDbl = frame.sDbl;
                Object[] vars = frame.varSource.stack;
                double[] varDbls = frame.varSource.sDbl;
                int[] varAttributes = frame.varSource.stackAttributes;
                byte[] iCode = frame.idata.itsICode;
                String[] strings = frame.idata.itsStringTable;

                // Use local for stackTop as well. Since execption handlers
                // can only exist at statement level where stack is empty,
                // it is necessary to save/restore stackTop only across
                // function calls and normal returns.
                int stackTop = frame.savedStackTop;

                // Store new frame in cx which is used for error reporting etc.
                cx.lastInterpreterFrame = frame;

                Loop: for (;;) {

                    // Exception handler assumes that PC is already incremented
                    // pass the instruction start when it searches the
                    // exception handler
                    int op = iCode[frame.pc++];
                    jumplessRun: {

    // Back indent to ease implementation reading
switch (op) {
    case Icode_GENERATOR: {
        if (!frame.frozen) {
          // First time encountering this opcode: create new generator
          // object and return
          frame.pc--; // we want to come back here when we resume
          CallFrame generatorFrame = captureFrameForGenerator(frame);
          generatorFrame.frozen = true;
          NativeGenerator generator = new NativeGenerator(frame.scope,
              generatorFrame.fnOrScript, generatorFrame);
          frame.result = generator;
          break Loop;
        } else {
          // We are now resuming execution. Fall through to YIELD case.
        }
    }
    // fall through...
    case Token.YIELD: {
        if (!frame.frozen) {
            return freezeGenerator(cx, frame, stackTop, generatorState);
        } else {
            Object obj = thawGenerator(frame, stackTop, generatorState, op);
            if (obj != Scriptable.NOT_FOUND) {
                throwable = obj;
                break withoutExceptions;
            }
            continue Loop;
        }
    }
    case Icode_GENERATOR_END: {
      // throw StopIteration
      frame.frozen = true;
      int sourceLine = getIndex(iCode, frame.pc);
      generatorState.returnedException = new JavaScriptException(
          NativeIterator.getStopIterationObject(frame.scope),
          frame.idata.itsSourceFile, sourceLine);
      break Loop;
    }
    case Token.THROW: {
        Object value = stack[stackTop];
        if (value == DBL_MRK) value = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        --stackTop;

        int sourceLine = getIndex(iCode, frame.pc);
        throwable = new JavaScriptException(value,
                                            frame.idata.itsSourceFile,
                                            sourceLine);
        break withoutExceptions;
    }
    case Token.RETHROW: {
        indexReg += frame.localShift;
        throwable = stack[indexReg];
        break withoutExceptions;
    }
    case Token.GE :
    case Token.LE :
    case Token.GT :
    case Token.LT : {
        stackTop = doCompare(frame, op, stack, sDbl, stackTop);
        continue Loop;
    }
    case Token.IN :
    case Token.INSTANCEOF : {
        stackTop = doInOrInstanceof(cx, op, stack, sDbl, stackTop);
        continue Loop;
    }
    case Token.EQ :
    case Token.NE : {
        --stackTop;
        boolean valBln = doEquals(stack, sDbl, stackTop);
        valBln ^= (op == Token.NE);
        stack[stackTop] = ScriptRuntime.wrapBoolean(valBln);
        continue Loop;
    }
    case Token.SHEQ :
    case Token.SHNE : {
        --stackTop;
        boolean valBln = doShallowEquals(stack, sDbl, stackTop);
        valBln ^= (op == Token.SHNE);
        stack[stackTop] = ScriptRuntime.wrapBoolean(valBln);
        continue Loop;
    }
    case Token.IFNE :
        if (stack_boolean(frame, stackTop--)) {
            frame.pc += 2;
            continue Loop;
        }
        break jumplessRun;
    case Token.IFEQ :
        if (!stack_boolean(frame, stackTop--)) {
            frame.pc += 2;
            continue Loop;
        }
        break jumplessRun;
    case Icode_IFEQ_POP :
        if (!stack_boolean(frame, stackTop--)) {
            frame.pc += 2;
            continue Loop;
        }
        stack[stackTop--] = null;
        break jumplessRun;
    case Token.GOTO :
        break jumplessRun;
    case Icode_GOSUB :
        ++stackTop;
        stack[stackTop] = DBL_MRK;
        sDbl[stackTop] = frame.pc + 2;
        break jumplessRun;
    case Icode_STARTSUB :
        if (stackTop == frame.emptyStackTop + 1) {
            // Call from Icode_GOSUB: store return PC address in the local
            indexReg += frame.localShift;
            stack[indexReg] = stack[stackTop];
            sDbl[indexReg] = sDbl[stackTop];
            --stackTop;
        } else {
            // Call from exception handler: exception object is already stored
            // in the local
            if (stackTop != frame.emptyStackTop) Kit.codeBug();
        }
        continue Loop;
    case Icode_RETSUB : {
        // indexReg: local to store return address
        if (instructionCounting) {
            addInstructionCount(cx, frame, 0);
        }
        indexReg += frame.localShift;
        Object value = stack[indexReg];
        if (value != DBL_MRK) {
            // Invocation from exception handler, restore object to rethrow
            throwable = value;
            break withoutExceptions;
        }
        // Normal return from GOSUB
        frame.pc = (int)sDbl[indexReg];
        if (instructionCounting) {
            frame.pcPrevBranch = frame.pc;
        }
        continue Loop;
    }
    case Icode_POP :
        stack[stackTop] = null;
        stackTop--;
        continue Loop;
    case Icode_POP_RESULT :
        frame.result = stack[stackTop];
        frame.resultDbl = sDbl[stackTop];
        stack[stackTop] = null;
        --stackTop;
        continue Loop;
    case Icode_DUP :
        stack[stackTop + 1] = stack[stackTop];
        sDbl[stackTop + 1] = sDbl[stackTop];
        stackTop++;
        continue Loop;
    case Icode_DUP2 :
        stack[stackTop + 1] = stack[stackTop - 1];
        sDbl[stackTop + 1] = sDbl[stackTop - 1];
        stack[stackTop + 2] = stack[stackTop];
        sDbl[stackTop + 2] = sDbl[stackTop];
        stackTop += 2;
        continue Loop;
    case Icode_SWAP : {
        Object o = stack[stackTop];
        stack[stackTop] = stack[stackTop - 1];
        stack[stackTop - 1] = o;
        double d = sDbl[stackTop];
        sDbl[stackTop] = sDbl[stackTop - 1];
        sDbl[stackTop - 1] = d;
        continue Loop;
    }
    case Token.RETURN :
        frame.result = stack[stackTop];
        frame.resultDbl = sDbl[stackTop];
        --stackTop;
        break Loop;
    case Token.RETURN_RESULT :
        break Loop;
    case Icode_RETUNDEF :
        frame.result = undefined;
        break Loop;
    case Token.BITNOT : {
        int rIntValue = stack_int32(frame, stackTop);
        stack[stackTop] = DBL_MRK;
        sDbl[stackTop] = ~rIntValue;
        continue Loop;
    }
    case Token.BITAND :
    case Token.BITOR :
    case Token.BITXOR :
    case Token.LSH :
    case Token.RSH : {
        stackTop = doBitOp(frame, op, stack, sDbl, stackTop);
        continue Loop;
    }
    case Token.URSH : {
        double lDbl = stack_double(frame, stackTop - 1);
        int rIntValue = stack_int32(frame, stackTop) & 0x1F;
        stack[--stackTop] = DBL_MRK;
        sDbl[stackTop] = ScriptRuntime.toUint32(lDbl) >>> rIntValue;
        continue Loop;
    }
    case Token.NEG :
    case Token.POS : {
        double rDbl = stack_double(frame, stackTop);
        stack[stackTop] = DBL_MRK;
        if (op == Token.NEG) {
            rDbl = -rDbl;
        }
        sDbl[stackTop] = rDbl;
        continue Loop;
    }
    case Token.ADD :
        --stackTop;
        doAdd(stack, sDbl, stackTop, cx);
        continue Loop;
    case Token.SUB :
    case Token.MUL :
    case Token.DIV :
    case Token.MOD : {
        stackTop = doArithmetic(frame, op, stack, sDbl, stackTop);
        continue Loop;
    }
    case Token.NOT :
        stack[stackTop] = ScriptRuntime.wrapBoolean(
                              !stack_boolean(frame, stackTop));
        continue Loop;
    case Token.BINDNAME :
        stack[++stackTop] = ScriptRuntime.bind(cx, frame.scope, stringReg);
        continue Loop;
    case Token.STRICT_SETNAME:
    case Token.SETNAME : {
        Object rhs = stack[stackTop];
        if (rhs == DBL_MRK) rhs = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        --stackTop;
        Scriptable lhs = (Scriptable)stack[stackTop];
        stack[stackTop] = op == Token.SETNAME ?
                ScriptRuntime.setName(lhs, rhs, cx,
                                      frame.scope, stringReg) :
                ScriptRuntime.strictSetName(lhs, rhs, cx,
                                      frame.scope, stringReg);
        continue Loop;
    }
    case Icode_SETCONST: {
        Object rhs = stack[stackTop];
        if (rhs == DBL_MRK) rhs = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        --stackTop;
        Scriptable lhs = (Scriptable)stack[stackTop];
        stack[stackTop] = ScriptRuntime.setConst(lhs, rhs, cx, stringReg);
        continue Loop;
    }
    case Token.DELPROP :
    case Icode_DELNAME : {
        stackTop = doDelName(cx, op, stack, sDbl, stackTop);
        continue Loop;
    }
    case Token.GETPROPNOWARN : {
        Object lhs = stack[stackTop];
        if (lhs == DBL_MRK) lhs = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        stack[stackTop] = ScriptRuntime.getObjectPropNoWarn(lhs, stringReg, cx);
        continue Loop;
    }
    case Token.GETPROP : {
        Object lhs = stack[stackTop];
        if (lhs == DBL_MRK) lhs = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        stack[stackTop] = ScriptRuntime.getObjectProp(lhs, stringReg, cx, frame.scope);
        continue Loop;
    }
    case Token.SETPROP : {
        Object rhs = stack[stackTop];
        if (rhs == DBL_MRK) rhs = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        --stackTop;
        Object lhs = stack[stackTop];
        if (lhs == DBL_MRK) lhs = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        stack[stackTop] = ScriptRuntime.setObjectProp(lhs, stringReg, rhs, cx);
        continue Loop;
    }
    case Icode_PROP_INC_DEC : {
        Object lhs = stack[stackTop];
        if (lhs == DBL_MRK) lhs = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        stack[stackTop] = ScriptRuntime.propIncrDecr(lhs, stringReg,
                                                     cx, iCode[frame.pc]);
        ++frame.pc;
        continue Loop;
    }
    case Token.GETELEM : {
        stackTop = doGetElem(cx, frame, stack, sDbl, stackTop);
        continue Loop;
    }
    case Token.SETELEM : {
        stackTop = doSetElem(cx, stack, sDbl, stackTop);
        continue Loop;
    }
    case Icode_ELEM_INC_DEC: {
        stackTop = doElemIncDec(cx, frame, iCode, stack, sDbl, stackTop);
        continue Loop;
    }
    case Token.GET_REF : {
        Ref ref = (Ref)stack[stackTop];
        stack[stackTop] = ScriptRuntime.refGet(ref, cx);
        continue Loop;
    }
    case Token.SET_REF : {
        Object value = stack[stackTop];
        if (value == DBL_MRK) value = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        --stackTop;
        Ref ref = (Ref)stack[stackTop];
        stack[stackTop] = ScriptRuntime.refSet(ref, value, cx);
        continue Loop;
    }
    case Token.DEL_REF : {
        Ref ref = (Ref)stack[stackTop];
        stack[stackTop] = ScriptRuntime.refDel(ref, cx);
        continue Loop;
    }
    case Icode_REF_INC_DEC : {
        Ref ref = (Ref)stack[stackTop];
        stack[stackTop] = ScriptRuntime.refIncrDecr(ref, cx, iCode[frame.pc]);
        ++frame.pc;
        continue Loop;
    }
    case Token.LOCAL_LOAD :
        ++stackTop;
        indexReg += frame.localShift;
        stack[stackTop] = stack[indexReg];
        sDbl[stackTop] = sDbl[indexReg];
        continue Loop;
    case Icode_LOCAL_CLEAR :
        indexReg += frame.localShift;
        stack[indexReg] = null;
        continue Loop;
    case Icode_NAME_AND_THIS :
        // stringReg: name
        ++stackTop;
        stack[stackTop] = ScriptRuntime.getNameFunctionAndThis(stringReg,
                                                               cx, frame.scope);
        ++stackTop;
        stack[stackTop] = ScriptRuntime.lastStoredScriptable(cx);
        continue Loop;
    case Icode_PROP_AND_THIS: {
        Object obj = stack[stackTop];
        if (obj == DBL_MRK) obj = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        // stringReg: property
        stack[stackTop] = ScriptRuntime.getPropFunctionAndThis(obj, stringReg,
                                                               cx, frame.scope);
        ++stackTop;
        stack[stackTop] = ScriptRuntime.lastStoredScriptable(cx);
        continue Loop;
    }
    case Icode_ELEM_AND_THIS: {
        Object obj = stack[stackTop - 1];
        if (obj == DBL_MRK) obj = ScriptRuntime.wrapNumber(sDbl[stackTop - 1]);
        Object id = stack[stackTop];
        if (id == DBL_MRK) id = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        stack[stackTop - 1] = ScriptRuntime.getElemFunctionAndThis(obj, id, cx);
        stack[stackTop] = ScriptRuntime.lastStoredScriptable(cx);
        continue Loop;
    }
    case Icode_VALUE_AND_THIS : {
        Object value = stack[stackTop];
        if (value == DBL_MRK) value = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        stack[stackTop] = ScriptRuntime.getValueFunctionAndThis(value, cx);
        ++stackTop;
        stack[stackTop] = ScriptRuntime.lastStoredScriptable(cx);
        continue Loop;
    }
    case Icode_CALLSPECIAL : {
        if (instructionCounting) {
            cx.instructionCount += INVOCATION_COST;
        }
        stackTop = doCallSpecial(cx, frame, stack, sDbl, stackTop, iCode, indexReg);
        continue Loop;
    }
    case Token.CALL :
    case Icode_TAIL_CALL :
    case Token.REF_CALL : {
        if (instructionCounting) {
            cx.instructionCount += INVOCATION_COST;
        }
        // stack change: function thisObj arg0 .. argN -> result
        // indexReg: number of arguments
        stackTop -= 1 + indexReg;

        // CALL generation ensures that fun and funThisObj
        // are already Scriptable and Callable objects respectively
        Callable fun = (Callable)stack[stackTop];
        Scriptable funThisObj = (Scriptable)stack[stackTop + 1];
        if (op == Token.REF_CALL) {
            Object[] outArgs = getArgsArray(stack, sDbl, stackTop + 2,
                                            indexReg);
            stack[stackTop] = ScriptRuntime.callRef(fun, funThisObj,
                                                    outArgs, cx);
            continue Loop;
        }
        Scriptable calleeScope = frame.scope;
        if (frame.useActivation) {
            calleeScope = ScriptableObject.getTopLevelScope(frame.scope);
        }
        if (fun instanceof InterpretedFunction) {
            InterpretedFunction ifun = (InterpretedFunction)fun;
            if (frame.fnOrScript.securityDomain == ifun.securityDomain) {
                CallFrame callParentFrame = frame;
                CallFrame calleeFrame = new CallFrame();
                if (op == Icode_TAIL_CALL) {
                    // In principle tail call can re-use the current
                    // frame and its stack arrays but it is hard to
                    // do properly. Any exceptions that can legally
                    // happen during frame re-initialization including
                    // StackOverflowException during innocent looking
                    // System.arraycopy may leave the current frame
                    // data corrupted leading to undefined behaviour
                    // in the catch code bellow that unwinds JS stack
                    // on exceptions. Then there is issue about frame release
                    // end exceptions there.
                    // To avoid frame allocation a released frame
                    // can be cached for re-use which would also benefit
                    // non-tail calls but it is not clear that this caching
                    // would gain in performance due to potentially
                    // bad interaction with GC.
                    callParentFrame = frame.parentFrame;
                    // Release the current frame. See Bug #344501 to see why
                    // it is being done here.
                    exitFrame(cx, frame, null);
                }
                initFrame(cx, calleeScope, funThisObj, stack, sDbl,
                          stackTop + 2, indexReg, ifun, callParentFrame,
                          calleeFrame);
                if (op != Icode_TAIL_CALL) {
                    frame.savedStackTop = stackTop;
                    frame.savedCallOp = op;
                }
                frame = calleeFrame;
                continue StateLoop;
            }
        }

        if (fun instanceof NativeContinuation) {
            // Jump to the captured continuation
            ContinuationJump cjump;
            cjump = new ContinuationJump((NativeContinuation)fun, frame);

            // continuation result is the first argument if any
            // of continuation call
            if (indexReg == 0) {
                cjump.result = undefined;
            } else {
                cjump.result = stack[stackTop + 2];
                cjump.resultDbl = sDbl[stackTop + 2];
            }

            // Start the real unwind job
            throwable = cjump;
            break withoutExceptions;
        }

        if (fun instanceof IdFunctionObject) {
            IdFunctionObject ifun = (IdFunctionObject)fun;
            if (NativeContinuation.isContinuationConstructor(ifun)) {
                frame.stack[stackTop] = captureContinuation(cx,
                        frame.parentFrame, false);
                continue Loop;
            }
            // Bug 405654 -- make best effort to keep Function.apply and
            // Function.call within this interpreter loop invocation
            if (BaseFunction.isApplyOrCall(ifun)) {
                Callable applyCallable = ScriptRuntime.getCallable(funThisObj);
                if (applyCallable instanceof InterpretedFunction) {
                    InterpretedFunction iApplyCallable = (InterpretedFunction)applyCallable;
                    if (frame.fnOrScript.securityDomain == iApplyCallable.securityDomain) {
                        frame = initFrameForApplyOrCall(cx, frame, indexReg,
                                stack, sDbl, stackTop, op, calleeScope, ifun,
                                iApplyCallable);
                        continue StateLoop;
                    }
                }
            }
        }

        // Bug 447697 -- make best effort to keep __noSuchMethod__ within this
        // interpreter loop invocation
        if (fun instanceof NoSuchMethodShim) {
            // get the shim and the actual method
            NoSuchMethodShim noSuchMethodShim = (NoSuchMethodShim) fun;
            Callable noSuchMethodMethod = noSuchMethodShim.noSuchMethodMethod;
            // if the method is in fact an InterpretedFunction
            if (noSuchMethodMethod instanceof InterpretedFunction) {
                InterpretedFunction ifun = (InterpretedFunction) noSuchMethodMethod;
                if (frame.fnOrScript.securityDomain == ifun.securityDomain) {
                    frame = initFrameForNoSuchMethod(cx, frame, indexReg, stack, sDbl,
                                             stackTop, op, funThisObj, calleeScope,
                                             noSuchMethodShim, ifun);
                    continue StateLoop;
                }
            }
        }

        cx.lastInterpreterFrame = frame;
        frame.savedCallOp = op;
        frame.savedStackTop = stackTop;
        stack[stackTop] = fun.call(cx, calleeScope, funThisObj,
                getArgsArray(stack, sDbl, stackTop + 2, indexReg));

        continue Loop;
    }
    case Token.NEW : {
        if (instructionCounting) {
            cx.instructionCount += INVOCATION_COST;
        }
        // stack change: function arg0 .. argN -> newResult
        // indexReg: number of arguments
        stackTop -= indexReg;

        Object lhs = stack[stackTop];
        if (lhs instanceof InterpretedFunction) {
            InterpretedFunction f = (InterpretedFunction)lhs;
            if (frame.fnOrScript.securityDomain == f.securityDomain) {
                Scriptable newInstance = f.createObject(cx, frame.scope);
                CallFrame calleeFrame = new CallFrame();
                initFrame(cx, frame.scope, newInstance, stack, sDbl,
                          stackTop + 1, indexReg, f, frame,
                          calleeFrame);

                stack[stackTop] = newInstance;
                frame.savedStackTop = stackTop;
                frame.savedCallOp = op;
                frame = calleeFrame;
                continue StateLoop;
            }
        }
        if (!(lhs instanceof Function)) {
            if (lhs == DBL_MRK) lhs = ScriptRuntime.wrapNumber(sDbl[stackTop]);
            throw ScriptRuntime.notFunctionError(lhs);
        }
        Function fun = (Function)lhs;

        if (fun instanceof IdFunctionObject) {
            IdFunctionObject ifun = (IdFunctionObject)fun;
            if (NativeContinuation.isContinuationConstructor(ifun)) {
                frame.stack[stackTop] =
                    captureContinuation(cx, frame.parentFrame, false);
                continue Loop;
            }
        }

        Object[] outArgs = getArgsArray(stack, sDbl, stackTop + 1, indexReg);
        stack[stackTop] = fun.construct(cx, frame.scope, outArgs);
        continue Loop;
    }
    case Token.TYPEOF : {
        Object lhs = stack[stackTop];
        if (lhs == DBL_MRK) lhs = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        stack[stackTop] = ScriptRuntime.typeof(lhs);
        continue Loop;
    }
    case Icode_TYPEOFNAME :
        stack[++stackTop] = ScriptRuntime.typeofName(frame.scope, stringReg);
        continue Loop;
    case Token.STRING :
        stack[++stackTop] = stringReg;
        continue Loop;
    case Icode_SHORTNUMBER :
        ++stackTop;
        stack[stackTop] = DBL_MRK;
        sDbl[stackTop] = getShort(iCode, frame.pc);
        frame.pc += 2;
        continue Loop;
    case Icode_INTNUMBER :
        ++stackTop;
        stack[stackTop] = DBL_MRK;
        sDbl[stackTop] = getInt(iCode, frame.pc);
        frame.pc += 4;
        continue Loop;
    case Token.NUMBER :
        ++stackTop;
        stack[stackTop] = DBL_MRK;
        sDbl[stackTop] = frame.idata.itsDoubleTable[indexReg];
        continue Loop;
    case Token.NAME :
        stack[++stackTop] = ScriptRuntime.name(cx, frame.scope, stringReg);
        continue Loop;
    case Icode_NAME_INC_DEC :
        stack[++stackTop] = ScriptRuntime.nameIncrDecr(frame.scope, stringReg,
                                                       cx, iCode[frame.pc]);
        ++frame.pc;
        continue Loop;
    case Icode_SETCONSTVAR1:
        indexReg = iCode[frame.pc++];
        // fallthrough
    case Token.SETCONSTVAR :
        stackTop = doSetConstVar(frame, stack, sDbl, stackTop, vars, varDbls,
                                 varAttributes, indexReg);
        continue Loop;
    case Icode_SETVAR1:
        indexReg = iCode[frame.pc++];
        // fallthrough
    case Token.SETVAR :
        stackTop = doSetVar(frame, stack, sDbl, stackTop, vars, varDbls,
                            varAttributes, indexReg);
        continue Loop;
    case Icode_GETVAR1:
        indexReg = iCode[frame.pc++];
        // fallthrough
    case Token.GETVAR :
        stackTop = doGetVar(frame, stack, sDbl, stackTop, vars, varDbls, indexReg);
        continue Loop;
    case Icode_VAR_INC_DEC : {
        stackTop = doVarIncDec(cx, frame, stack, sDbl, stackTop,
                               vars, varDbls, indexReg);
        continue Loop;
    }
    case Icode_ZERO :
        ++stackTop;
        stack[stackTop] = DBL_MRK;
        sDbl[stackTop] = 0;
        continue Loop;
    case Icode_ONE :
        ++stackTop;
        stack[stackTop] = DBL_MRK;
        sDbl[stackTop] = 1;
        continue Loop;
    case Token.NULL :
        stack[++stackTop] = null;
        continue Loop;
    case Token.THIS :
        stack[++stackTop] = frame.thisObj;
        continue Loop;
    case Token.THISFN :
        stack[++stackTop] = frame.fnOrScript;
        continue Loop;
    case Token.FALSE :
        stack[++stackTop] = Boolean.FALSE;
        continue Loop;
    case Token.TRUE :
        stack[++stackTop] = Boolean.TRUE;
        continue Loop;
    case Icode_UNDEF :
        stack[++stackTop] = undefined;
        continue Loop;
    case Token.ENTERWITH : {
        Object lhs = stack[stackTop];
        if (lhs == DBL_MRK) lhs = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        --stackTop;
        frame.scope = ScriptRuntime.enterWith(lhs, cx, frame.scope);
        continue Loop;
    }
    case Token.LEAVEWITH :
        frame.scope = ScriptRuntime.leaveWith(frame.scope);
        continue Loop;
    case Token.CATCH_SCOPE : {
        // stack top: exception object
        // stringReg: name of exception variable
        // indexReg: local for exception scope
        --stackTop;
        indexReg += frame.localShift;

        boolean afterFirstScope =  (frame.idata.itsICode[frame.pc] != 0);
        Throwable caughtException = (Throwable)stack[stackTop + 1];
        Scriptable lastCatchScope;
        if (!afterFirstScope) {
            lastCatchScope = null;
        } else {
            lastCatchScope = (Scriptable)stack[indexReg];
        }
        stack[indexReg] = ScriptRuntime.newCatchScope(caughtException,
                                                      lastCatchScope, stringReg,
                                                      cx, frame.scope);
        ++frame.pc;
        continue Loop;
    }
    case Token.ENUM_INIT_KEYS :
    case Token.ENUM_INIT_VALUES :
    case Token.ENUM_INIT_ARRAY : {
        Object lhs = stack[stackTop];
        if (lhs == DBL_MRK) lhs = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        --stackTop;
        indexReg += frame.localShift;
        int enumType = op == Token.ENUM_INIT_KEYS
                         ? ScriptRuntime.ENUMERATE_KEYS :
                       op == Token.ENUM_INIT_VALUES
                         ? ScriptRuntime.ENUMERATE_VALUES :
                       ScriptRuntime.ENUMERATE_ARRAY;
        stack[indexReg] = ScriptRuntime.enumInit(lhs, cx, enumType);
        continue Loop;
    }
    case Token.ENUM_NEXT :
    case Token.ENUM_ID : {
        indexReg += frame.localShift;
        Object val = stack[indexReg];
        ++stackTop;
        stack[stackTop] = (op == Token.ENUM_NEXT)
                          ? (Object)ScriptRuntime.enumNext(val)
                          : (Object)ScriptRuntime.enumId(val, cx);
        continue Loop;
    }
    case Token.REF_SPECIAL : {
        //stringReg: name of special property
        Object obj = stack[stackTop];
        if (obj == DBL_MRK) obj = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        stack[stackTop] = ScriptRuntime.specialRef(obj, stringReg, cx);
        continue Loop;
    }
    case Token.REF_MEMBER: {
        //indexReg: flags
        stackTop = doRefMember(cx, stack, sDbl, stackTop, indexReg);
        continue Loop;
    }
    case Token.REF_NS_MEMBER: {
        //indexReg: flags
        stackTop = doRefNsMember(cx, stack, sDbl, stackTop, indexReg);
        continue Loop;
    }
    case Token.REF_NAME: {
        //indexReg: flags
        Object name = stack[stackTop];
        if (name == DBL_MRK) name = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        stack[stackTop] = ScriptRuntime.nameRef(name, cx, frame.scope,
                                                indexReg);
        continue Loop;
    }
    case Token.REF_NS_NAME: {
        //indexReg: flags
        stackTop = doRefNsName(cx, frame, stack, sDbl, stackTop, indexReg);
        continue Loop;
    }
    case Icode_SCOPE_LOAD :
        indexReg += frame.localShift;
        frame.scope = (Scriptable)stack[indexReg];
        continue Loop;
    case Icode_SCOPE_SAVE :
        indexReg += frame.localShift;
        stack[indexReg] = frame.scope;
        continue Loop;
    case Icode_CLOSURE_EXPR :
        stack[++stackTop] = InterpretedFunction.createFunction(cx, frame.scope,
                                                               frame.fnOrScript,
                                                               indexReg);
        continue Loop;
    case Icode_CLOSURE_STMT :
        initFunction(cx, frame.scope, frame.fnOrScript, indexReg);
        continue Loop;
    case Token.REGEXP :
        Object re = frame.idata.itsRegExpLiterals[indexReg];
        stack[++stackTop] = ScriptRuntime.wrapRegExp(cx, frame.scope, re);
        continue Loop;
    case Icode_LITERAL_NEW :
        // indexReg: number of values in the literal
        ++stackTop;
        stack[stackTop] = new int[indexReg];
        ++stackTop;
        stack[stackTop] = new Object[indexReg];
        sDbl[stackTop] = 0;
        continue Loop;
    case Icode_LITERAL_SET : {
        Object value = stack[stackTop];
        if (value == DBL_MRK) value = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        --stackTop;
        int i = (int)sDbl[stackTop];
        ((Object[])stack[stackTop])[i] = value;
        sDbl[stackTop] = i + 1;
        continue Loop;
    }
    case Icode_LITERAL_GETTER : {
        Object value = stack[stackTop];
        --stackTop;
        int i = (int)sDbl[stackTop];
        ((Object[])stack[stackTop])[i] = value;
        ((int[])stack[stackTop - 1])[i] = -1;
        sDbl[stackTop] = i + 1;
        continue Loop;
    }
    case Icode_LITERAL_SETTER : {
        Object value = stack[stackTop];
        --stackTop;
        int i = (int)sDbl[stackTop];
        ((Object[])stack[stackTop])[i] = value;
        ((int[])stack[stackTop - 1])[i] = +1;
        sDbl[stackTop] = i + 1;
        continue Loop;
    }
    case Token.ARRAYLIT :
    case Icode_SPARE_ARRAYLIT :
    case Token.OBJECTLIT : {
        Object[] data = (Object[])stack[stackTop];
        --stackTop;
        int[] getterSetters = (int[])stack[stackTop];
        Object val;
        if (op == Token.OBJECTLIT) {
            Object[] ids = (Object[])frame.idata.literalIds[indexReg];
            val = ScriptRuntime.newObjectLiteral(ids, data, getterSetters, cx,
                    frame.scope);
        } else {
            int[] skipIndexces = null;
            if (op == Icode_SPARE_ARRAYLIT) {
                skipIndexces = (int[])frame.idata.literalIds[indexReg];
            }
            val = ScriptRuntime.newArrayLiteral(data, skipIndexces, cx,
                                                frame.scope);
        }
        stack[stackTop] = val;
        continue Loop;
    }
    case Icode_ENTERDQ : {
        Object lhs = stack[stackTop];
        if (lhs == DBL_MRK) lhs = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        --stackTop;
        frame.scope = ScriptRuntime.enterDotQuery(lhs, frame.scope);
        continue Loop;
    }
    case Icode_LEAVEDQ : {
        boolean valBln = stack_boolean(frame, stackTop);
        Object x = ScriptRuntime.updateDotQuery(valBln, frame.scope);
        if (x != null) {
            stack[stackTop] = x;
            frame.scope = ScriptRuntime.leaveDotQuery(frame.scope);
            frame.pc += 2;
            continue Loop;
        }
        // reset stack and PC to code after ENTERDQ
        --stackTop;
        break jumplessRun;
    }
    case Token.DEFAULTNAMESPACE : {
        Object value = stack[stackTop];
        if (value == DBL_MRK) value = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        stack[stackTop] = ScriptRuntime.setDefaultNamespace(value, cx);
        continue Loop;
    }
    case Token.ESCXMLATTR : {
        Object value = stack[stackTop];
        if (value != DBL_MRK) {
            stack[stackTop] = ScriptRuntime.escapeAttributeValue(value, cx);
        }
        continue Loop;
    }
    case Token.ESCXMLTEXT : {
        Object value = stack[stackTop];
        if (value != DBL_MRK) {
            stack[stackTop] = ScriptRuntime.escapeTextValue(value, cx);
        }
        continue Loop;
    }
    case Icode_DEBUGGER:
        if (frame.debuggerFrame != null) {
            frame.debuggerFrame.onDebuggerStatement(cx);
        }
        continue Loop;
    case Icode_LINE :
        frame.pcSourceLineStart = frame.pc;
        if (frame.debuggerFrame != null) {
            int line = getIndex(iCode, frame.pc);
            frame.debuggerFrame.onLineChange(cx, line);
        }
        frame.pc += 2;
        continue Loop;
    case Icode_REG_IND_C0:
        indexReg = 0;
        continue Loop;
    case Icode_REG_IND_C1:
        indexReg = 1;
        continue Loop;
    case Icode_REG_IND_C2:
        indexReg = 2;
        continue Loop;
    case Icode_REG_IND_C3:
        indexReg = 3;
        continue Loop;
    case Icode_REG_IND_C4:
        indexReg = 4;
        continue Loop;
    case Icode_REG_IND_C5:
        indexReg = 5;
        continue Loop;
    case Icode_REG_IND1:
        indexReg = 0xFF & iCode[frame.pc];
        ++frame.pc;
        continue Loop;
    case Icode_REG_IND2:
        indexReg = getIndex(iCode, frame.pc);
        frame.pc += 2;
        continue Loop;
    case Icode_REG_IND4:
        indexReg = getInt(iCode, frame.pc);
        frame.pc += 4;
        continue Loop;
    case Icode_REG_STR_C0:
        stringReg = strings[0];
        continue Loop;
    case Icode_REG_STR_C1:
        stringReg = strings[1];
        continue Loop;
    case Icode_REG_STR_C2:
        stringReg = strings[2];
        continue Loop;
    case Icode_REG_STR_C3:
        stringReg = strings[3];
        continue Loop;
    case Icode_REG_STR1:
        stringReg = strings[0xFF & iCode[frame.pc]];
        ++frame.pc;
        continue Loop;
    case Icode_REG_STR2:
        stringReg = strings[getIndex(iCode, frame.pc)];
        frame.pc += 2;
        continue Loop;
    case Icode_REG_STR4:
        stringReg = strings[getInt(iCode, frame.pc)];
        frame.pc += 4;
        continue Loop;
    default :
        dumpICode(frame.idata);
        throw new RuntimeException("Unknown icode : " + op
                                 + " @ pc : " + (frame.pc-1));
}  // end of interpreter switch

                    } // end of jumplessRun label block

                    // This should be reachable only for jump implementation
                    // when pc points to encoded target offset
                    if (instructionCounting) {
                        addInstructionCount(cx, frame, 2);
                    }
                    int offset = getShort(iCode, frame.pc);
                    if (offset != 0) {
                        // -1 accounts for pc pointing to jump opcode + 1
                        frame.pc += offset - 1;
                    } else {
                        frame.pc = frame.idata.longJumps.
                                       getExistingInt(frame.pc);
                    }
                    if (instructionCounting) {
                        frame.pcPrevBranch = frame.pc;
                    }
                    continue Loop;

                } // end of Loop: for

                exitFrame(cx, frame, null);
                interpreterResult = frame.result;
                interpreterResultDbl = frame.resultDbl;
                if (frame.parentFrame != null) {
                    frame = frame.parentFrame;
                    if (frame.frozen) {
                        frame = frame.cloneFrozen();
                    }
                    setCallResult(
                        frame, interpreterResult, interpreterResultDbl);
                    interpreterResult = null; // Help GC
                    continue StateLoop;
                }
                break StateLoop;

            }  // end of interpreter withoutExceptions: try
            catch (Throwable ex) {
                if (throwable != null) {
                    // This is serious bug and it is better to track it ASAP
                    ex.printStackTrace(System.err);
                    throw new IllegalStateException();
                }
                throwable = ex;
            }

            // This should be reachable only after above catch or from
            // finally when it needs to propagate exception or from
            // explicit throw
            if (throwable == null) Kit.codeBug();

            // Exception type
            final int EX_CATCH_STATE = 2; // Can execute JS catch
            final int EX_FINALLY_STATE = 1; // Can execute JS finally
            final int EX_NO_JS_STATE = 0; // Terminate JS execution

            int exState;
            ContinuationJump cjump = null;

            if (generatorState != null &&
                generatorState.operation == NativeGenerator.GENERATOR_CLOSE &&
                throwable == generatorState.value)
            {
                exState = EX_FINALLY_STATE;
            } else if (throwable instanceof JavaScriptException) {
                exState = EX_CATCH_STATE;
            } else if (throwable instanceof EcmaError) {
                // an offical ECMA error object,
                exState = EX_CATCH_STATE;
            } else if (throwable instanceof EvaluatorException) {
                exState = EX_CATCH_STATE;
            } else if (throwable instanceof ContinuationPending) {
                exState = EX_NO_JS_STATE;
            } else if (throwable instanceof RuntimeException) {
                exState = cx.hasFeature(Context.FEATURE_ENHANCED_JAVA_ACCESS)
                          ? EX_CATCH_STATE
                          : EX_FINALLY_STATE;
            } else if (throwable instanceof Error) {
                exState = cx.hasFeature(Context.FEATURE_ENHANCED_JAVA_ACCESS)
                          ? EX_CATCH_STATE
                          : EX_NO_JS_STATE;
            } else if (throwable instanceof ContinuationJump) {
                // It must be ContinuationJump
                exState = EX_FINALLY_STATE;
                cjump = (ContinuationJump)throwable;
            } else {
                exState = cx.hasFeature(Context.FEATURE_ENHANCED_JAVA_ACCESS)
                          ? EX_CATCH_STATE
                          : EX_FINALLY_STATE;
            }

            if (instructionCounting) {
                try {
                    addInstructionCount(cx, frame, EXCEPTION_COST);
                } catch (RuntimeException ex) {
                    throwable = ex;
                    exState = EX_FINALLY_STATE;
                } catch (Error ex) {
                    // Error from instruction counting
                    //     => unconditionally terminate JS
                    throwable = ex;
                    cjump = null;
                    exState = EX_NO_JS_STATE;
                }
            }
            if (frame.debuggerFrame != null
                && throwable instanceof RuntimeException)
            {
                // Call debugger only for RuntimeException
                RuntimeException rex = (RuntimeException)throwable;
                try {
                    frame.debuggerFrame.onExceptionThrown(cx, rex);
                } catch (Throwable ex) {
                    // Any exception from debugger
                    //     => unconditionally terminate JS
                    throwable = ex;
                    cjump = null;
                    exState = EX_NO_JS_STATE;
                }
            }

            for (;;) {
                if (exState != EX_NO_JS_STATE) {
                    boolean onlyFinally = (exState != EX_CATCH_STATE);
                    indexReg = getExceptionHandler(frame, onlyFinally);
                    if (indexReg >= 0) {
                        // We caught an exception, restart the loop
                        // with exception pending the processing at the loop
                        // start
                        continue StateLoop;
                    }
                }
                // No allowed exception handlers in this frame, unwind
                // to parent and try to look there

                exitFrame(cx, frame, throwable);

                frame = frame.parentFrame;
                if (frame == null) { break; }
                if (cjump != null && cjump.branchFrame == frame) {
                    // Continuation branch point was hit,
                    // restart the state loop to reenter continuation
                    indexReg = -1;
                    continue StateLoop;
                }
            }

            // No more frames, rethrow the exception or deal with continuation
            if (cjump != null) {
                if (cjump.branchFrame != null) {
                    // The above loop should locate the top frame
                    Kit.codeBug();
                }
                if (cjump.capturedFrame != null) {
                    // Restarting detached continuation
                    indexReg = -1;
                    continue StateLoop;
                }
                // Return continuation result to the caller
                interpreterResult = cjump.result;
                interpreterResultDbl = cjump.resultDbl;
                throwable = null;
            }
            break StateLoop;

        } // end of StateLoop: for(;;)

        // Do cleanups/restorations before the final return or throw

        if (cx.previousInterpreterInvocations != null
            && cx.previousInterpreterInvocations.size() != 0)
        {
            cx.lastInterpreterFrame
                = cx.previousInterpreterInvocations.pop();
        } else {
            // It was the last interpreter frame on the stack
            cx.lastInterpreterFrame = null;
            // Force GC of the value cx.previousInterpreterInvocations
            cx.previousInterpreterInvocations = null;
        }

        if (throwable != null) {
            if (throwable instanceof RuntimeException) {
                throw (RuntimeException)throwable;
            } else {
                // Must be instance of Error or code bug
                throw (Error)throwable;
            }
        }

        return (interpreterResult != DBL_MRK)
               ? interpreterResult
               : ScriptRuntime.wrapNumber(interpreterResultDbl);
    }

    private static int doInOrInstanceof(Context cx, int op, Object[] stack,
                                        double[] sDbl, int stackTop) {
        Object rhs = stack[stackTop];
        if (rhs == DOUBLE_MARK) rhs = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        --stackTop;
        Object lhs = stack[stackTop];
        if (lhs == DOUBLE_MARK) lhs = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        boolean valBln;
        if (op == Token.IN) {
            valBln = ScriptRuntime.in(lhs, rhs, cx);
        } else {
            valBln = ScriptRuntime.instanceOf(lhs, rhs, cx);
        }
        stack[stackTop] = ScriptRuntime.wrapBoolean(valBln);
        return stackTop;
    }

    private static int doCompare(CallFrame frame, int op, Object[] stack,
                                 double[] sDbl, int stackTop) {
        --stackTop;
        Object rhs = stack[stackTop + 1];
        Object lhs = stack[stackTop];
        boolean valBln;
        object_compare:
        {
            number_compare:
            {
                double rDbl, lDbl;
                if (rhs == DOUBLE_MARK) {
                    rDbl = sDbl[stackTop + 1];
                    lDbl = stack_double(frame, stackTop);
                } else if (lhs == DOUBLE_MARK) {
                    rDbl = ScriptRuntime.toNumber(rhs);
                    lDbl = sDbl[stackTop];
                } else {
                    break number_compare;
                }
                switch (op) {
                    case Token.GE:
                        valBln = (lDbl >= rDbl);
                        break object_compare;
                    case Token.LE:
                        valBln = (lDbl <= rDbl);
                        break object_compare;
                    case Token.GT:
                        valBln = (lDbl > rDbl);
                        break object_compare;
                    case Token.LT:
                        valBln = (lDbl < rDbl);
                        break object_compare;
                    default:
                        throw Kit.codeBug();
                }
            }
            switch (op) {
                case Token.GE:
                    valBln = ScriptRuntime.cmp_LE(rhs, lhs);
                    break;
                case Token.LE:
                    valBln = ScriptRuntime.cmp_LE(lhs, rhs);
                    break;
                case Token.GT:
                    valBln = ScriptRuntime.cmp_LT(rhs, lhs);
                    break;
                case Token.LT:
                    valBln = ScriptRuntime.cmp_LT(lhs, rhs);
                    break;
                default:
                    throw Kit.codeBug();
            }
        }
        stack[stackTop] = ScriptRuntime.wrapBoolean(valBln);
        return stackTop;
    }

    private static int doBitOp(CallFrame frame, int op, Object[] stack,
                               double[] sDbl, int stackTop) {
        int lIntValue = stack_int32(frame, stackTop - 1);
        int rIntValue = stack_int32(frame, stackTop);
        stack[--stackTop] = DOUBLE_MARK;
        switch (op) {
          case Token.BITAND:
            lIntValue &= rIntValue;
            break;
          case Token.BITOR:
            lIntValue |= rIntValue;
            break;
          case Token.BITXOR:
            lIntValue ^= rIntValue;
            break;
          case Token.LSH:
            lIntValue <<= rIntValue;
            break;
          case Token.RSH:
            lIntValue >>= rIntValue;
            break;
        }
        sDbl[stackTop] = lIntValue;
        return stackTop;
    }

    private static int doDelName(Context cx, int op, Object[] stack,
                                 double[] sDbl, int stackTop) {
        Object rhs = stack[stackTop];
        if (rhs == DOUBLE_MARK) rhs = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        --stackTop;
        Object lhs = stack[stackTop];
        if (lhs == DOUBLE_MARK) lhs = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        stack[stackTop] = ScriptRuntime.delete(lhs, rhs, cx, op == Icode_DELNAME);
        return stackTop;
    }

    private static int doGetElem(Context cx, CallFrame frame, Object[] stack,
                                 double[] sDbl, int stackTop) {
        --stackTop;
        Object lhs = stack[stackTop];
        if (lhs == DOUBLE_MARK) {
            lhs = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        }
        Object value;
        Object id = stack[stackTop + 1];
        if (id != DOUBLE_MARK) {
            value = ScriptRuntime.getObjectElem(lhs, id, cx, frame.scope);
        } else {
            double d = sDbl[stackTop + 1];
            value = ScriptRuntime.getObjectIndex(lhs, d, cx);
        }
        stack[stackTop] = value;
        return stackTop;
    }

    private static int doSetElem(Context cx, Object[] stack, double[] sDbl,
                                 int stackTop) {
        stackTop -= 2;
        Object rhs = stack[stackTop + 2];
        if (rhs == DOUBLE_MARK) {
            rhs = ScriptRuntime.wrapNumber(sDbl[stackTop + 2]);
        }
        Object lhs = stack[stackTop];
        if (lhs == DOUBLE_MARK) {
            lhs = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        }
        Object value;
        Object id = stack[stackTop + 1];
        if (id != DOUBLE_MARK) {
            value = ScriptRuntime.setObjectElem(lhs, id, rhs, cx);
        } else {
            double d = sDbl[stackTop + 1];
            value = ScriptRuntime.setObjectIndex(lhs, d, rhs, cx);
        }
        stack[stackTop] = value;
        return stackTop;
    }

    private static int doElemIncDec(Context cx, CallFrame frame, byte[] iCode,
                                    Object[] stack, double[] sDbl, int stackTop) {
        Object rhs = stack[stackTop];
        if (rhs == DOUBLE_MARK) rhs = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        --stackTop;
        Object lhs = stack[stackTop];
        if (lhs == DOUBLE_MARK) lhs = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        stack[stackTop] = ScriptRuntime.elemIncrDecr(lhs, rhs, cx,
                                                     iCode[frame.pc]);
        ++frame.pc;
        return stackTop;
    }

    private static int doCallSpecial(Context cx, CallFrame frame,
                                     Object[] stack, double[] sDbl,
                                     int stackTop, byte[] iCode,
                                     int indexReg) {
        int callType = iCode[frame.pc] & 0xFF;
        boolean isNew =  (iCode[frame.pc + 1] != 0);
        int sourceLine = getIndex(iCode, frame.pc + 2);

        // indexReg: number of arguments
        if (isNew) {
            // stack change: function arg0 .. argN -> newResult
            stackTop -= indexReg;

            Object function = stack[stackTop];
            if (function == DOUBLE_MARK)
                function = ScriptRuntime.wrapNumber(sDbl[stackTop]);
            Object[] outArgs = getArgsArray(
                                   stack, sDbl, stackTop + 1, indexReg);
            stack[stackTop] = ScriptRuntime.newSpecial(
                                  cx, function, outArgs, frame.scope, callType);
        } else {
            // stack change: function thisObj arg0 .. argN -> result
            stackTop -= 1 + indexReg;

            // Call code generation ensure that stack here
            // is ... Callable Scriptable
            Scriptable functionThis = (Scriptable)stack[stackTop + 1];
            Callable function = (Callable)stack[stackTop];
            Object[] outArgs = getArgsArray(
                                   stack, sDbl, stackTop + 2, indexReg);
            stack[stackTop] = ScriptRuntime.callSpecial(
                                  cx, function, functionThis, outArgs,
                                  frame.scope, frame.thisObj, callType,
                                  frame.idata.itsSourceFile, sourceLine);
        }
        frame.pc += 4;
        return stackTop;
    }

    private static int doSetConstVar(CallFrame frame, Object[] stack,
                                     double[] sDbl, int stackTop,
                                     Object[] vars, double[] varDbls,
                                     int[] varAttributes, int indexReg) {
        if (!frame.useActivation) {
            if ((varAttributes[indexReg] & ScriptableObject.READONLY) == 0) {
                throw Context.reportRuntimeError1("msg.var.redecl",
                                                  frame.idata.argNames[indexReg]);
            }
            if ((varAttributes[indexReg] & ScriptableObject.UNINITIALIZED_CONST)
                != 0)
            {
                vars[indexReg] = stack[stackTop];
                varAttributes[indexReg] &= ~ScriptableObject.UNINITIALIZED_CONST;
                varDbls[indexReg] = sDbl[stackTop];
            }
        } else {
            Object val = stack[stackTop];
            if (val == DOUBLE_MARK) val = ScriptRuntime.wrapNumber(sDbl[stackTop]);
            String stringReg = frame.idata.argNames[indexReg];
            if (frame.scope instanceof ConstProperties) {
                ConstProperties cp = (ConstProperties)frame.scope;
                cp.putConst(stringReg, frame.scope, val);
            } else
                throw Kit.codeBug();
        }
        return stackTop;
    }

    private static int doSetVar(CallFrame frame, Object[] stack,
                                double[] sDbl, int stackTop,
                                Object[] vars, double[] varDbls,
                                int[] varAttributes, int indexReg) {
        if (!frame.useActivation) {
            if ((varAttributes[indexReg] & ScriptableObject.READONLY) == 0) {
                vars[indexReg] = stack[stackTop];
                varDbls[indexReg] = sDbl[stackTop];
            }
        } else {
            Object val = stack[stackTop];
            if (val == DOUBLE_MARK) val = ScriptRuntime.wrapNumber(sDbl[stackTop]);
            String stringReg = frame.idata.argNames[indexReg];
            frame.scope.put(stringReg, frame.scope, val);
        }
        return stackTop;
    }

    private static int doGetVar(CallFrame frame, Object[] stack,
                                double[] sDbl, int stackTop,
                                Object[] vars, double[] varDbls,
                                int indexReg) {
        ++stackTop;
        if (!frame.useActivation) {
            stack[stackTop] = vars[indexReg];
            sDbl[stackTop] = varDbls[indexReg];
        } else {
            String stringReg = frame.idata.argNames[indexReg];
            stack[stackTop] = frame.scope.get(stringReg, frame.scope);
        }
        return stackTop;
    }

    private static int doVarIncDec(Context cx, CallFrame frame,
                                   Object[] stack, double[] sDbl,
                                   int stackTop, Object[] vars,
                                   double[] varDbls, int indexReg) {
        // indexReg : varindex
        ++stackTop;
        int incrDecrMask = frame.idata.itsICode[frame.pc];
        if (!frame.useActivation) {
            stack[stackTop] = DOUBLE_MARK;
            Object varValue = vars[indexReg];
            double d;
            if (varValue == DOUBLE_MARK) {
                d = varDbls[indexReg];
            } else {
                d = ScriptRuntime.toNumber(varValue);
                vars[indexReg] = DOUBLE_MARK;
            }
            double d2 = ((incrDecrMask & Node.DECR_FLAG) == 0)
                        ? d + 1.0 : d - 1.0;
            varDbls[indexReg] = d2;
            sDbl[stackTop] = ((incrDecrMask & Node.POST_FLAG) == 0) ? d2 : d;
        } else {
            String varName = frame.idata.argNames[indexReg];
            stack[stackTop] = ScriptRuntime.nameIncrDecr(frame.scope, varName,
                                                         cx, incrDecrMask);
        }
        ++frame.pc;
        return stackTop;
    }

    private static int doRefMember(Context cx, Object[] stack, double[] sDbl,
                                   int stackTop, int flags) {
        Object elem = stack[stackTop];
        if (elem == DOUBLE_MARK) elem = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        --stackTop;
        Object obj = stack[stackTop];
        if (obj == DOUBLE_MARK) obj = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        stack[stackTop] = ScriptRuntime.memberRef(obj, elem, cx, flags);
        return stackTop;
    }

    private static int doRefNsMember(Context cx, Object[] stack, double[] sDbl,
                                     int stackTop, int flags) {
        Object elem = stack[stackTop];
        if (elem == DOUBLE_MARK) elem = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        --stackTop;
        Object ns = stack[stackTop];
        if (ns == DOUBLE_MARK) ns = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        --stackTop;
        Object obj = stack[stackTop];
        if (obj == DOUBLE_MARK) obj = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        stack[stackTop] = ScriptRuntime.memberRef(obj, ns, elem, cx, flags);
        return stackTop;
    }

    private static int doRefNsName(Context cx, CallFrame frame,
                                   Object[] stack, double[] sDbl,
                                   int stackTop, int flags) {
        Object name = stack[stackTop];
        if (name == DOUBLE_MARK) name = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        --stackTop;
        Object ns = stack[stackTop];
        if (ns == DOUBLE_MARK) ns = ScriptRuntime.wrapNumber(sDbl[stackTop]);
        stack[stackTop] = ScriptRuntime.nameRef(ns, name, cx, frame.scope, flags);
        return stackTop;
    }

    /**
     * Call __noSuchMethod__.
     */
    private static CallFrame initFrameForNoSuchMethod(Context cx,
            CallFrame frame, int indexReg, Object[] stack, double[] sDbl,
            int stackTop, int op, Scriptable funThisObj, Scriptable calleeScope,
            NoSuchMethodShim noSuchMethodShim, InterpretedFunction ifun)
    {
        // create an args array from the stack
        Object[] argsArray = null;
        // exactly like getArgsArray except that the first argument
        // is the method name from the shim
        int shift = stackTop + 2;
        Object[] elements = new Object[indexReg];
        for (int i=0; i < indexReg; ++i, ++shift) {
            Object val = stack[shift];
            if (val == DOUBLE_MARK) {
                val = ScriptRuntime.wrapNumber(sDbl[shift]);
            }
            elements[i] = val;
        }
        argsArray = new Object[2];
        argsArray[0] = noSuchMethodShim.methodName;
        argsArray[1] = cx.newArray(calleeScope, elements);

        // exactly the same as if it's a regular InterpretedFunction
        CallFrame callParentFrame = frame;
        CallFrame calleeFrame = new CallFrame();
        if (op == Icode_TAIL_CALL) {
            callParentFrame = frame.parentFrame;
            exitFrame(cx, frame, null);
        }
        // init the frame with the underlying method with the
        // adjusted args array and shim's function
        initFrame(cx, calleeScope, funThisObj, argsArray, null,
          0, 2, ifun, callParentFrame, calleeFrame);
        if (op != Icode_TAIL_CALL) {
            frame.savedStackTop = stackTop;
            frame.savedCallOp = op;
        }
        return calleeFrame;
    }

    private static boolean doEquals(Object[] stack, double[] sDbl,
                                    int stackTop) {
        Object rhs = stack[stackTop + 1];
        Object lhs = stack[stackTop];
        if (rhs == DOUBLE_MARK) {
            if (lhs == DOUBLE_MARK) {
                return (sDbl[stackTop] == sDbl[stackTop + 1]);
            } else {
                return ScriptRuntime.eqNumber(sDbl[stackTop + 1], lhs);
            }
        } else {
            if (lhs == DOUBLE_MARK) {
                return ScriptRuntime.eqNumber(sDbl[stackTop], rhs);
            } else {
                return ScriptRuntime.eq(lhs, rhs);
            }
        }
    }

    private static boolean doShallowEquals(Object[] stack, double[] sDbl,
                                           int stackTop)
    {
        Object rhs = stack[stackTop + 1];
        Object lhs = stack[stackTop];
        final Object DBL_MRK = DOUBLE_MARK;
        double rdbl, ldbl;
        if (rhs == DBL_MRK) {
            rdbl = sDbl[stackTop + 1];
            if (lhs == DBL_MRK) {
                ldbl = sDbl[stackTop];
            } else if (lhs instanceof Number) {
                ldbl = ((Number)lhs).doubleValue();
            } else {
                return false;
            }
        } else if (lhs == DBL_MRK) {
            ldbl = sDbl[stackTop];
            if (rhs instanceof Number) {
                rdbl = ((Number)rhs).doubleValue();
            } else {
                return false;
            }
        } else {
            return ScriptRuntime.shallowEq(lhs, rhs);
        }
        return (ldbl == rdbl);
    }

    private static CallFrame processThrowable(Context cx, Object throwable,
                                              CallFrame frame, int indexReg,
                                              boolean instructionCounting)
    {
        // Recovering from exception, indexReg contains
        // the index of handler

        if (indexReg >= 0) {
            // Normal exception handler, transfer
            // control appropriately

            if (frame.frozen) {
                // XXX Deal with exceptios!!!
                frame = frame.cloneFrozen();
            }

            int[] table = frame.idata.itsExceptionTable;

            frame.pc = table[indexReg + EXCEPTION_HANDLER_SLOT];
            if (instructionCounting) {
                frame.pcPrevBranch = frame.pc;
            }

            frame.savedStackTop = frame.emptyStackTop;
            int scopeLocal = frame.localShift
                             + table[indexReg
                                     + EXCEPTION_SCOPE_SLOT];
            int exLocal = frame.localShift
                             + table[indexReg
                                     + EXCEPTION_LOCAL_SLOT];
            frame.scope = (Scriptable)frame.stack[scopeLocal];
            frame.stack[exLocal] = throwable;

            throwable = null;
        } else {
            // Continuation restoration
            ContinuationJump cjump = (ContinuationJump)throwable;

            // Clear throwable to indicate that exceptions are OK
            throwable = null;

            if (cjump.branchFrame != frame) Kit.codeBug();

            // Check that we have at least one frozen frame
            // in the case of detached continuation restoration:
            // unwind code ensure that
            if (cjump.capturedFrame == null) Kit.codeBug();

            // Need to rewind branchFrame, capturedFrame
            // and all frames in between
            int rewindCount = cjump.capturedFrame.frameIndex + 1;
            if (cjump.branchFrame != null) {
                rewindCount -= cjump.branchFrame.frameIndex;
            }

            int enterCount = 0;
            CallFrame[] enterFrames = null;

            CallFrame x = cjump.capturedFrame;
            for (int i = 0; i != rewindCount; ++i) {
                if (!x.frozen) Kit.codeBug();
                if (isFrameEnterExitRequired(x)) {
                    if (enterFrames == null) {
                        // Allocate enough space to store the rest
                        // of rewind frames in case all of them
                        // would require to enter
                        enterFrames = new CallFrame[rewindCount
                                                    - i];
                    }
                    enterFrames[enterCount] = x;
                    ++enterCount;
                }
                x = x.parentFrame;
            }

            while (enterCount != 0) {
                // execute enter: walk enterFrames in the reverse
                // order since they were stored starting from
                // the capturedFrame, not branchFrame
                --enterCount;
                x = enterFrames[enterCount];
                enterFrame(cx, x, ScriptRuntime.emptyArgs, true);
            }

            // Continuation jump is almost done: capturedFrame
            // points to the call to the function that captured
            // continuation, so clone capturedFrame and
            // emulate return that function with the suplied result
            frame = cjump.capturedFrame.cloneFrozen();
            setCallResult(frame, cjump.result, cjump.resultDbl);
            // restart the execution
        }
        frame.throwable = throwable;
        return frame;
    }

    private static Object freezeGenerator(Context cx, CallFrame frame,
                                          int stackTop,
                                          GeneratorState generatorState)
    {
          if (generatorState.operation == NativeGenerator.GENERATOR_CLOSE) {
              // Error: no yields when generator is closing
              throw ScriptRuntime.typeError0("msg.yield.closing");
          }
          // return to our caller (which should be a method of NativeGenerator)
          frame.frozen = true;
          frame.result = frame.stack[stackTop];
          frame.resultDbl = frame.sDbl[stackTop];
          frame.savedStackTop = stackTop;
          frame.pc--; // we want to come back here when we resume
          ScriptRuntime.exitActivationFunction(cx);
          return (frame.result != DOUBLE_MARK)
              ? frame.result
              : ScriptRuntime.wrapNumber(frame.resultDbl);
    }

    private static Object thawGenerator(CallFrame frame, int stackTop,
                                        GeneratorState generatorState, int op)
    {
          // we are resuming execution
          frame.frozen = false;
          int sourceLine = getIndex(frame.idata.itsICode, frame.pc);
          frame.pc += 2; // skip line number data
          if (generatorState.operation == NativeGenerator.GENERATOR_THROW) {
              // processing a call to <generator>.throw(exception): must
              // act as if exception was thrown from resumption point
              return new JavaScriptException(generatorState.value,
                                                  frame.idata.itsSourceFile,
                                                  sourceLine);
          }
          if (generatorState.operation == NativeGenerator.GENERATOR_CLOSE) {
              return generatorState.value;
          }
          if (generatorState.operation != NativeGenerator.GENERATOR_SEND)
              throw Kit.codeBug();
          if (op == Token.YIELD)
              frame.stack[stackTop] = generatorState.value;
          return Scriptable.NOT_FOUND;
    }

    private static CallFrame initFrameForApplyOrCall(Context cx, CallFrame frame,
            int indexReg, Object[] stack, double[] sDbl, int stackTop, int op,
            Scriptable calleeScope, IdFunctionObject ifun,
            InterpretedFunction iApplyCallable)
    {
        Scriptable applyThis;
        if (indexReg != 0) {
            Object obj = stack[stackTop + 2];
            if (obj == DOUBLE_MARK)
                obj = ScriptRuntime.wrapNumber(sDbl[stackTop + 2]);
            applyThis = ScriptRuntime.toObjectOrNull(cx, obj);
        }
        else {
            applyThis = null;
        }
        if (applyThis == null) {
            // This covers the case of args[0] == (null|undefined) as well.
            applyThis = ScriptRuntime.getTopCallScope(cx);
        }
        if(op == Icode_TAIL_CALL) {
            exitFrame(cx, frame, null);
            frame = frame.parentFrame;
        }
        else {
            frame.savedStackTop = stackTop;
            frame.savedCallOp = op;
        }
        CallFrame calleeFrame = new CallFrame();
        if(BaseFunction.isApply(ifun)) {
            Object[] callArgs = indexReg < 2 ? ScriptRuntime.emptyArgs :
                ScriptRuntime.getApplyArguments(cx, stack[stackTop + 3]);
            initFrame(cx, calleeScope, applyThis, callArgs, null, 0,
                    callArgs.length, iApplyCallable, frame, calleeFrame);
        }
        else {
            // Shift args left
            for(int i = 1; i < indexReg; ++i) {
                stack[stackTop + 1 + i] = stack[stackTop + 2 + i];
                sDbl[stackTop + 1 + i] = sDbl[stackTop + 2 + i];
            }
            int argCount = indexReg < 2 ? 0 : indexReg - 1;
            initFrame(cx, calleeScope, applyThis, stack, sDbl, stackTop + 2,
                    argCount, iApplyCallable, frame, calleeFrame);
        }

        frame = calleeFrame;
        return frame;
    }

    private static void initFrame(Context cx, Scriptable callerScope,
                                  Scriptable thisObj,
                                  Object[] args, double[] argsDbl,
                                  int argShift, int argCount,
                                  InterpretedFunction fnOrScript,
                                  CallFrame parentFrame, CallFrame frame)
    {
        InterpreterData idata = fnOrScript.idata;

        boolean useActivation = idata.itsNeedsActivation;
        DebugFrame debuggerFrame = null;
        if (cx.debugger != null) {
            debuggerFrame = cx.debugger.getFrame(cx, idata);
            if (debuggerFrame != null) {
                useActivation = true;
            }
        }

        if (useActivation) {
            // Copy args to new array to pass to enterActivationFunction
            // or debuggerFrame.onEnter
            if (argsDbl != null) {
                args = getArgsArray(args, argsDbl, argShift, argCount);
            }
            argShift = 0;
            argsDbl = null;
        }

        Scriptable scope;
        if (idata.itsFunctionType != 0) {
            scope = fnOrScript.getParentScope();

            if (useActivation) {
                scope = ScriptRuntime.createFunctionActivation(
                            fnOrScript, scope, args);
            }
        } else {
            scope = callerScope;
            ScriptRuntime.initScript(fnOrScript, thisObj, cx, scope,
                                     fnOrScript.idata.evalScriptFlag);
        }

        if (idata.itsNestedFunctions != null) {
            if (idata.itsFunctionType != 0 && !idata.itsNeedsActivation)
                Kit.codeBug();
            for (int i = 0; i < idata.itsNestedFunctions.length; i++) {
                InterpreterData fdata = idata.itsNestedFunctions[i];
                if (fdata.itsFunctionType == FunctionNode.FUNCTION_STATEMENT) {
                    initFunction(cx, scope, fnOrScript, i);
                }
            }
        }

        // Initialize args, vars, locals and stack

        int emptyStackTop = idata.itsMaxVars + idata.itsMaxLocals - 1;
        int maxFrameArray = idata.itsMaxFrameArray;
        if (maxFrameArray != emptyStackTop + idata.itsMaxStack + 1)
            Kit.codeBug();

        Object[] stack;
        int[] stackAttributes;
        double[] sDbl;
        boolean stackReuse;
        if (frame.stack != null && maxFrameArray <= frame.stack.length) {
            // Reuse stacks from old frame
            stackReuse = true;
            stack = frame.stack;
            stackAttributes = frame.stackAttributes;
            sDbl = frame.sDbl;
        } else {
            stackReuse = false;
            stack = new Object[maxFrameArray];
            stackAttributes = new int[maxFrameArray];
            sDbl = new double[maxFrameArray];
        }

        int varCount = idata.getParamAndVarCount();
        for (int i = 0; i < varCount; i++) {
            if (idata.getParamOrVarConst(i))
                stackAttributes[i] = ScriptableObject.CONST;
        }
        int definedArgs = idata.argCount;
        if (definedArgs > argCount) { definedArgs = argCount; }

        // Fill the frame structure

        frame.parentFrame = parentFrame;
        frame.frameIndex = (parentFrame == null)
                           ? 0 : parentFrame.frameIndex + 1;
        if(frame.frameIndex > cx.getMaximumInterpreterStackDepth())
        {
            throw Context.reportRuntimeError("Exceeded maximum stack depth");
        }
        frame.frozen = false;

        frame.fnOrScript = fnOrScript;
        frame.idata = idata;

        frame.stack = stack;
        frame.stackAttributes = stackAttributes;
        frame.sDbl = sDbl;
        frame.varSource = frame;
        frame.localShift = idata.itsMaxVars;
        frame.emptyStackTop = emptyStackTop;

        frame.debuggerFrame = debuggerFrame;
        frame.useActivation = useActivation;

        frame.thisObj = thisObj;

        // Initialize initial values of variables that change during
        // interpretation.
        frame.result = Undefined.instance;
        frame.pc = 0;
        frame.pcPrevBranch = 0;
        frame.pcSourceLineStart = idata.firstLinePC;
        frame.scope = scope;

        frame.savedStackTop = emptyStackTop;
        frame.savedCallOp = 0;

        System.arraycopy(args, argShift, stack, 0, definedArgs);
        if (argsDbl != null) {
            System.arraycopy(argsDbl, argShift, sDbl, 0, definedArgs);
        }
        for (int i = definedArgs; i != idata.itsMaxVars; ++i) {
            stack[i] = Undefined.instance;
        }
        if (stackReuse) {
            // Clean the stack part and space beyond stack if any
            // of the old array to allow to GC objects there
            for (int i = emptyStackTop + 1; i != stack.length; ++i) {
                stack[i] = null;
            }
        }

        enterFrame(cx, frame, args, false);
    }

    private static boolean isFrameEnterExitRequired(CallFrame frame)
    {
        return frame.debuggerFrame != null || frame.idata.itsNeedsActivation;
    }

    private static void enterFrame(Context cx, CallFrame frame, Object[] args,
                                   boolean continuationRestart)
    {
        boolean usesActivation = frame.idata.itsNeedsActivation;
        boolean isDebugged = frame.debuggerFrame != null;
        if(usesActivation || isDebugged) {
            Scriptable scope = frame.scope;
            if(scope == null) {
                Kit.codeBug();
            } else if (continuationRestart) {
                // Walk the parent chain of frame.scope until a NativeCall is
                // found. Normally, frame.scope is a NativeCall when called
                // from initFrame() for a debugged or activatable function.
                // However, when called from interpretLoop() as part of
                // restarting a continuation, it can also be a NativeWith if
                // the continuation was captured within a "with" or "catch"
                // block ("catch" implicitly uses NativeWith to create a scope
                // to expose the exception variable).
                for(;;) {
                    if(scope instanceof NativeWith) {
                        scope = scope.getParentScope();
                        if (scope == null || (frame.parentFrame != null &&
                                              frame.parentFrame.scope == scope))
                        {
                            // If we get here, we didn't find a NativeCall in
                            // the call chain before reaching parent frame's
                            // scope. This should not be possible.
                            Kit.codeBug();
                            break; // Never reached, but keeps the static analyzer
                            // happy about "scope" not being null 5 lines above.
                        }
                    }
                    else {
                        break;
                    }
                }
            }
            if (isDebugged) {
                frame.debuggerFrame.onEnter(cx, scope, frame.thisObj, args);
            }
            // Enter activation only when itsNeedsActivation true,
            // since debugger should not interfere with activation
            // chaining
            if (usesActivation) {
                ScriptRuntime.enterActivationFunction(cx, scope);
            }
        }
    }

    private static void exitFrame(Context cx, CallFrame frame,
                                  Object throwable)
    {
        if (frame.idata.itsNeedsActivation) {
            ScriptRuntime.exitActivationFunction(cx);
        }

        if (frame.debuggerFrame != null) {
            try {
                if (throwable instanceof Throwable) {
                    frame.debuggerFrame.onExit(cx, true, throwable);
                } else {
                    Object result;
                    ContinuationJump cjump = (ContinuationJump)throwable;
                    if (cjump == null) {
                        result = frame.result;
                    } else {
                        result = cjump.result;
                    }
                    if (result == DOUBLE_MARK) {
                        double resultDbl;
                        if (cjump == null) {
                            resultDbl = frame.resultDbl;
                        } else {
                            resultDbl = cjump.resultDbl;
                        }
                        result = ScriptRuntime.wrapNumber(resultDbl);
                    }
                    frame.debuggerFrame.onExit(cx, false, result);
                }
            } catch (Throwable ex) {
                System.err.println(
"RHINO USAGE WARNING: onExit terminated with exception");
                ex.printStackTrace(System.err);
            }
        }
    }

    private static void setCallResult(CallFrame frame,
                                      Object callResult,
                                      double callResultDbl)
    {
        if (frame.savedCallOp == Token.CALL) {
            frame.stack[frame.savedStackTop] = callResult;
            frame.sDbl[frame.savedStackTop] = callResultDbl;
        } else if (frame.savedCallOp == Token.NEW) {
            // If construct returns scriptable,
            // then it replaces on stack top saved original instance
            // of the object.
            if (callResult instanceof Scriptable) {
                frame.stack[frame.savedStackTop] = callResult;
            }
        } else {
            Kit.codeBug();
        }
        frame.savedCallOp = 0;
    }

    public static NativeContinuation captureContinuation(Context cx) {
        if (cx.lastInterpreterFrame == null ||
            !(cx.lastInterpreterFrame instanceof CallFrame))
        {
            throw new IllegalStateException("Interpreter frames not found");
        }
        return captureContinuation(cx, (CallFrame)cx.lastInterpreterFrame, true);
    }

    private static NativeContinuation captureContinuation(Context cx, CallFrame frame,
        boolean requireContinuationsTopFrame)
    {
        NativeContinuation c = new NativeContinuation();
        ScriptRuntime.setObjectProtoAndParent(
            c, ScriptRuntime.getTopCallScope(cx));

        // Make sure that all frames are frozen
        CallFrame x = frame;
        CallFrame outermost = frame;
        while (x != null && !x.frozen) {
            x.frozen = true;
            // Allow to GC unused stack space
            for (int i = x.savedStackTop + 1; i != x.stack.length; ++i) {
                // Allow to GC unused stack space
                x.stack[i] = null;
                x.stackAttributes[i] = ScriptableObject.EMPTY;
            }
            if (x.savedCallOp == Token.CALL) {
                // the call will always overwrite the stack top with the result
                x.stack[x.savedStackTop] = null;
            } else {
                if (x.savedCallOp != Token.NEW) Kit.codeBug();
                // the new operator uses stack top to store the constructed
                // object so it shall not be cleared: see comments in
                // setCallResult
            }
            outermost = x;
            x = x.parentFrame;
        }

        if (requireContinuationsTopFrame) {
            while (outermost.parentFrame != null)
                outermost = outermost.parentFrame;

            if (!outermost.isContinuationsTopFrame) {
                throw new IllegalStateException("Cannot capture continuation " +
                        "from JavaScript code not called directly by " +
                        "executeScriptWithContinuations or " +
                        "callFunctionWithContinuations");
            }
        }

        c.initImplementation(frame);
        return c;
    }

    private static int stack_int32(CallFrame frame, int i)
    {
        Object x = frame.stack[i];
        if (x == UniqueTag.DOUBLE_MARK) {
            return ScriptRuntime.toInt32(frame.sDbl[i]);
        } else {
            return ScriptRuntime.toInt32(x);
        }
    }

    private static double stack_double(CallFrame frame, int i)
    {
        Object x = frame.stack[i];
        if (x != UniqueTag.DOUBLE_MARK) {
            return ScriptRuntime.toNumber(x);
        } else {
            return frame.sDbl[i];
        }
    }

    private static boolean stack_boolean(CallFrame frame, int i)
    {
        Object x = frame.stack[i];
        if (x == Boolean.TRUE) {
            return true;
        } else if (x == Boolean.FALSE) {
            return false;
        } else if (x == UniqueTag.DOUBLE_MARK) {
            double d = frame.sDbl[i];
            return !Double.isNaN(d) && d != 0.0;
        } else if (x == null || x == Undefined.instance) {
            return false;
        } else if (x instanceof Number) {
            double d = ((Number)x).doubleValue();
            return (!Double.isNaN(d) && d != 0.0);
        } else if (x instanceof Boolean) {
            return ((Boolean)x).booleanValue();
        } else {
            return ScriptRuntime.toBoolean(x);
        }
    }

    private static void doAdd(Object[] stack, double[] sDbl, int stackTop,
                              Context cx)
    {
        Object rhs = stack[stackTop + 1];
        Object lhs = stack[stackTop];
        double d;
        boolean leftRightOrder;
        if (rhs == DOUBLE_MARK) {
            d = sDbl[stackTop + 1];
            if (lhs == DOUBLE_MARK) {
                sDbl[stackTop] += d;
                return;
            }
            leftRightOrder = true;
            // fallthrough to object + number code
        } else if (lhs == DOUBLE_MARK) {
            d = sDbl[stackTop];
            lhs = rhs;
            leftRightOrder = false;
            // fallthrough to object + number code
        } else {
            if (lhs instanceof Scriptable || rhs instanceof Scriptable) {
                stack[stackTop] = ScriptRuntime.add(lhs, rhs, cx);
            } else if (lhs instanceof CharSequence || rhs instanceof CharSequence) {
                CharSequence lstr = ScriptRuntime.toCharSequence(lhs);
                CharSequence rstr = ScriptRuntime.toCharSequence(rhs);
                stack[stackTop] = new ConsString(lstr, rstr);
            } else {
                double lDbl = (lhs instanceof Number)
                    ? ((Number)lhs).doubleValue() : ScriptRuntime.toNumber(lhs);
                double rDbl = (rhs instanceof Number)
                    ? ((Number)rhs).doubleValue() : ScriptRuntime.toNumber(rhs);
                stack[stackTop] = DOUBLE_MARK;
                sDbl[stackTop] = lDbl + rDbl;
            }
            return;
        }

        // handle object(lhs) + number(d) code
        if (lhs instanceof Scriptable) {
            rhs = ScriptRuntime.wrapNumber(d);
            if (!leftRightOrder) {
                Object tmp = lhs;
                lhs = rhs;
                rhs = tmp;
            }
            stack[stackTop] = ScriptRuntime.add(lhs, rhs, cx);
        } else if (lhs instanceof CharSequence) {
            CharSequence lstr = (CharSequence)lhs;
            CharSequence rstr = ScriptRuntime.toCharSequence(d);
            if (leftRightOrder) {
                stack[stackTop] = new ConsString(lstr, rstr);
            } else {
                stack[stackTop] = new ConsString(rstr, lstr);
            }
        } else {
            double lDbl = (lhs instanceof Number)
                ? ((Number)lhs).doubleValue() : ScriptRuntime.toNumber(lhs);
            stack[stackTop] = DOUBLE_MARK;
            sDbl[stackTop] = lDbl + d;
        }
    }

    private static int doArithmetic(CallFrame frame, int op, Object[] stack,
                                    double[] sDbl, int stackTop) {
        double rDbl = stack_double(frame, stackTop);
        --stackTop;
        double lDbl = stack_double(frame, stackTop);
        stack[stackTop] = DOUBLE_MARK;
        switch (op) {
          case Token.SUB:
            lDbl -= rDbl;
            break;
          case Token.MUL:
            lDbl *= rDbl;
            break;
          case Token.DIV:
            lDbl /= rDbl;
            break;
          case Token.MOD:
            lDbl %= rDbl;
            break;
        }
        sDbl[stackTop] = lDbl;
        return stackTop;
    }

    private static Object[] getArgsArray(Object[] stack, double[] sDbl,
                                         int shift, int count)
    {
        if (count == 0) {
            return ScriptRuntime.emptyArgs;
        }
        Object[] args = new Object[count];
        for (int i = 0; i != count; ++i, ++shift) {
            Object val = stack[shift];
            if (val == UniqueTag.DOUBLE_MARK) {
                val = ScriptRuntime.wrapNumber(sDbl[shift]);
            }
            args[i] = val;
        }
        return args;
    }

    private static void addInstructionCount(Context cx, CallFrame frame,
                                            int extra)
    {
        cx.instructionCount += frame.pc - frame.pcPrevBranch + extra;
        if (cx.instructionCount > cx.instructionThreshold) {
            cx.observeInstructionCount(cx.instructionCount);
            cx.instructionCount = 0;
        }
    }
}
