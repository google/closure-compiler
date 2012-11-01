/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.ScriptNode;
import org.mozilla.javascript.ast.Jump;
import org.mozilla.javascript.ast.FunctionNode;

/**
 * Generates bytecode for the Interpreter.
 */
class CodeGenerator extends Icode {

    private static final int MIN_LABEL_TABLE_SIZE = 32;
    private static final int MIN_FIXUP_TABLE_SIZE = 40;

    private CompilerEnvirons compilerEnv;

    private boolean itsInFunctionFlag;
    private boolean itsInTryFlag;

    private InterpreterData itsData;

    private ScriptNode scriptOrFn;
    private int iCodeTop;
    private int stackDepth;
    private int lineNumber;
    private int doubleTableTop;

    private ObjToIntMap strings = new ObjToIntMap(20);
    private int localTop;
    private int[] labelTable;
    private int labelTableTop;

    // fixupTable[i] = (label_index << 32) | fixup_site
    private long[] fixupTable;
    private int fixupTableTop;
    private ObjArray literalIds = new ObjArray();

    private int exceptionTableTop;

    // ECF_ or Expression Context Flags constants: for now only TAIL
    private static final int ECF_TAIL = 1 << 0;

    public InterpreterData compile(CompilerEnvirons compilerEnv,
                                   ScriptNode tree,
                                   String encodedSource,
                                   boolean returnFunction)
    {
        this.compilerEnv = compilerEnv;

        if (Token.printTrees) {
            System.out.println("before transform:");
            System.out.println(tree.toStringTree(tree));
        }

        new NodeTransformer().transform(tree);

        if (Token.printTrees) {
            System.out.println("after transform:");
            System.out.println(tree.toStringTree(tree));
        }

        if (returnFunction) {
            scriptOrFn = tree.getFunctionNode(0);
        } else {
            scriptOrFn = tree;
        }
        itsData = new InterpreterData(compilerEnv.getLanguageVersion(),
                                      scriptOrFn.getSourceName(),
                                      encodedSource,
                                      ((AstRoot)tree).isInStrictMode());
        itsData.topLevel = true;

        if (returnFunction) {
            generateFunctionICode();
        } else {
            generateICodeFromTree(scriptOrFn);
        }
        return itsData;
    }

    private void generateFunctionICode()
    {
        itsInFunctionFlag = true;

        FunctionNode theFunction = (FunctionNode)scriptOrFn;

        itsData.itsFunctionType = theFunction.getFunctionType();
        itsData.itsNeedsActivation = theFunction.requiresActivation();
        if (theFunction.getFunctionName() != null) {
            itsData.itsName = theFunction.getName();
        }
        if (theFunction.isGenerator()) {
          addIcode(Icode_GENERATOR);
          addUint16(theFunction.getBaseLineno() & 0xFFFF);
        }

        generateICodeFromTree(theFunction.getLastChild());
    }

    private void generateICodeFromTree(Node tree)
    {
        generateNestedFunctions();

        generateRegExpLiterals();

        visitStatement(tree, 0);
        fixLabelGotos();
        // add RETURN_RESULT only to scripts as function always ends with RETURN
        if (itsData.itsFunctionType == 0) {
            addToken(Token.RETURN_RESULT);
        }

        if (itsData.itsICode.length != iCodeTop) {
            // Make itsData.itsICode length exactly iCodeTop to save memory
            // and catch bugs with jumps beyond icode as early as possible
            byte[] tmp = new byte[iCodeTop];
            System.arraycopy(itsData.itsICode, 0, tmp, 0, iCodeTop);
            itsData.itsICode = tmp;
        }
        if (strings.size() == 0) {
            itsData.itsStringTable = null;
        } else {
            itsData.itsStringTable = new String[strings.size()];
            ObjToIntMap.Iterator iter = strings.newIterator();
            for (iter.start(); !iter.done(); iter.next()) {
                String str = (String)iter.getKey();
                int index = iter.getValue();
                if (itsData.itsStringTable[index] != null) Kit.codeBug();
                itsData.itsStringTable[index] = str;
            }
        }
        if (doubleTableTop == 0) {
            itsData.itsDoubleTable = null;
        } else if (itsData.itsDoubleTable.length != doubleTableTop) {
            double[] tmp = new double[doubleTableTop];
            System.arraycopy(itsData.itsDoubleTable, 0, tmp, 0,
                             doubleTableTop);
            itsData.itsDoubleTable = tmp;
        }
        if (exceptionTableTop != 0
            && itsData.itsExceptionTable.length != exceptionTableTop)
        {
            int[] tmp = new int[exceptionTableTop];
            System.arraycopy(itsData.itsExceptionTable, 0, tmp, 0,
                             exceptionTableTop);
            itsData.itsExceptionTable = tmp;
        }

        itsData.itsMaxVars = scriptOrFn.getParamAndVarCount();
        // itsMaxFrameArray: interpret method needs this amount for its
        // stack and sDbl arrays
        itsData.itsMaxFrameArray = itsData.itsMaxVars
                                   + itsData.itsMaxLocals
                                   + itsData.itsMaxStack;

        itsData.argNames = scriptOrFn.getParamAndVarNames();
        itsData.argIsConst = scriptOrFn.getParamAndVarConst();
        itsData.argCount = scriptOrFn.getParamCount();

        itsData.encodedSourceStart = scriptOrFn.getEncodedSourceStart();
        itsData.encodedSourceEnd = scriptOrFn.getEncodedSourceEnd();

        if (literalIds.size() != 0) {
            itsData.literalIds = literalIds.toArray();
        }

        if (Token.printICode) Interpreter.dumpICode(itsData);
    }

    private void generateNestedFunctions()
    {
        int functionCount = scriptOrFn.getFunctionCount();
        if (functionCount == 0) return;

        InterpreterData[] array = new InterpreterData[functionCount];
        for (int i = 0; i != functionCount; i++) {
            FunctionNode fn = scriptOrFn.getFunctionNode(i);
            CodeGenerator gen = new CodeGenerator();
            gen.compilerEnv = compilerEnv;
            gen.scriptOrFn = fn;
            gen.itsData = new InterpreterData(itsData);
            gen.generateFunctionICode();
            array[i] = gen.itsData;
        }
        itsData.itsNestedFunctions = array;
    }

    private void generateRegExpLiterals()
    {
        int N = scriptOrFn.getRegexpCount();
        if (N == 0) return;

        Context cx = Context.getContext();
        RegExpProxy rep = ScriptRuntime.checkRegExpProxy(cx);
        Object[] array = new Object[N];
        for (int i = 0; i != N; i++) {
            String string = scriptOrFn.getRegexpString(i);
            String flags = scriptOrFn.getRegexpFlags(i);
            array[i] = rep.compileRegExp(cx, string, flags);
        }
        itsData.itsRegExpLiterals = array;
    }

    private void updateLineNumber(Node node)
    {
        int lineno = node.getLineno();
        if (lineno != lineNumber && lineno >= 0) {
            if (itsData.firstLinePC < 0) {
                itsData.firstLinePC = lineno;
            }
            lineNumber = lineno;
            addIcode(Icode_LINE);
            addUint16(lineno & 0xFFFF);
        }
    }

    private RuntimeException badTree(Node node)
    {
        throw new RuntimeException(node.toString());
    }

    private void visitStatement(Node node, int initialStackDepth)
    {
        int type = node.getType();
        Node child = node.getFirstChild();
        switch (type) {

          case Token.FUNCTION:
            {
                int fnIndex = node.getExistingIntProp(Node.FUNCTION_PROP);
                int fnType = scriptOrFn.getFunctionNode(fnIndex).
                                 getFunctionType();
                // Only function expressions or function expression
                // statements need closure code creating new function
                // object on stack as function statements are initialized
                // at script/function start.
                // In addition, function expressions can not be present here
                // at statement level, they must only be present as expressions.
                if (fnType == FunctionNode.FUNCTION_EXPRESSION_STATEMENT) {
                    addIndexOp(Icode_CLOSURE_STMT, fnIndex);
                } else {
                    if (fnType != FunctionNode.FUNCTION_STATEMENT) {
                        throw Kit.codeBug();
                    }
                }
                // For function statements or function expression statements
                // in scripts, we need to ensure that the result of the script
                // is the function if it is the last statement in the script.
                // For example, eval("function () {}") should return a
                // function, not undefined.
                if (!itsInFunctionFlag) {
                    addIndexOp(Icode_CLOSURE_EXPR, fnIndex);
                    stackChange(1);
                    addIcode(Icode_POP_RESULT);
                    stackChange(-1);
                }
            }
            break;

          case Token.LABEL:
          case Token.LOOP:
          case Token.BLOCK:
          case Token.EMPTY:
          case Token.WITH:
            updateLineNumber(node);
          case Token.SCRIPT:
            // fall through
            while (child != null) {
                visitStatement(child, initialStackDepth);
                child = child.getNext();
            }
            break;

          case Token.ENTERWITH:
            visitExpression(child, 0);
            addToken(Token.ENTERWITH);
            stackChange(-1);
            break;

          case Token.LEAVEWITH:
            addToken(Token.LEAVEWITH);
            break;

          case Token.LOCAL_BLOCK:
            {
                int local = allocLocal();
                node.putIntProp(Node.LOCAL_PROP, local);
                updateLineNumber(node);
                while (child != null) {
                    visitStatement(child, initialStackDepth);
                    child = child.getNext();
                }
                addIndexOp(Icode_LOCAL_CLEAR, local);
                releaseLocal(local);
            }
            break;

          case Token.DEBUGGER:
            addIcode(Icode_DEBUGGER);
            break;

          case Token.SWITCH:
            updateLineNumber(node);
            // See comments in IRFactory.createSwitch() for description
            // of SWITCH node
            {
                visitExpression(child, 0);
                for (Jump caseNode = (Jump)child.getNext();
                     caseNode != null;
                     caseNode = (Jump)caseNode.getNext())
                {
                    if (caseNode.getType() != Token.CASE)
                        throw badTree(caseNode);
                    Node test = caseNode.getFirstChild();
                    addIcode(Icode_DUP);
                    stackChange(1);
                    visitExpression(test, 0);
                    addToken(Token.SHEQ);
                    stackChange(-1);
                    // If true, Icode_IFEQ_POP will jump and remove case
                    // value from stack
                    addGoto(caseNode.target, Icode_IFEQ_POP);
                    stackChange(-1);
                }
                addIcode(Icode_POP);
                stackChange(-1);
            }
            break;

          case Token.TARGET:
            markTargetLabel(node);
            break;

          case Token.IFEQ :
          case Token.IFNE :
            {
                Node target = ((Jump)node).target;
                visitExpression(child, 0);
                addGoto(target, type);
                stackChange(-1);
            }
            break;

          case Token.GOTO:
            {
                Node target = ((Jump)node).target;
                addGoto(target, type);
            }
            break;

          case Token.JSR:
            {
                Node target = ((Jump)node).target;
                addGoto(target, Icode_GOSUB);
            }
            break;

          case Token.FINALLY:
            {
                // Account for incomming GOTOSUB address
                stackChange(1);
                int finallyRegister = getLocalBlockRef(node);
                addIndexOp(Icode_STARTSUB, finallyRegister);
                stackChange(-1);
                while (child != null) {
                    visitStatement(child, initialStackDepth);
                    child = child.getNext();
                }
                addIndexOp(Icode_RETSUB, finallyRegister);
            }
            break;

          case Token.EXPR_VOID:
          case Token.EXPR_RESULT:
            updateLineNumber(node);
            visitExpression(child, 0);
            addIcode((type == Token.EXPR_VOID) ? Icode_POP : Icode_POP_RESULT);
            stackChange(-1);
            break;

          case Token.TRY:
            {
                Jump tryNode = (Jump)node;
                int exceptionObjectLocal = getLocalBlockRef(tryNode);
                int scopeLocal = allocLocal();

                addIndexOp(Icode_SCOPE_SAVE, scopeLocal);

                int tryStart = iCodeTop;
                boolean savedFlag = itsInTryFlag;
                itsInTryFlag = true;
                while (child != null) {
                    visitStatement(child, initialStackDepth);
                    child = child.getNext();
                }
                itsInTryFlag = savedFlag;

                Node catchTarget = tryNode.target;
                if (catchTarget != null) {
                    int catchStartPC
                        = labelTable[getTargetLabel(catchTarget)];
                    addExceptionHandler(
                        tryStart, catchStartPC, catchStartPC,
                        false, exceptionObjectLocal, scopeLocal);
                }
                Node finallyTarget = tryNode.getFinally();
                if (finallyTarget != null) {
                    int finallyStartPC
                        = labelTable[getTargetLabel(finallyTarget)];
                    addExceptionHandler(
                        tryStart, finallyStartPC, finallyStartPC,
                        true, exceptionObjectLocal, scopeLocal);
                }

                addIndexOp(Icode_LOCAL_CLEAR, scopeLocal);
                releaseLocal(scopeLocal);
            }
            break;

          case Token.CATCH_SCOPE:
            {
                int localIndex = getLocalBlockRef(node);
                int scopeIndex = node.getExistingIntProp(Node.CATCH_SCOPE_PROP);
                String name = child.getString();
                child = child.getNext();
                visitExpression(child, 0); // load expression object
                addStringPrefix(name);
                addIndexPrefix(localIndex);
                addToken(Token.CATCH_SCOPE);
                addUint8(scopeIndex != 0 ? 1 : 0);
                stackChange(-1);
            }
            break;

          case Token.THROW:
            updateLineNumber(node);
            visitExpression(child, 0);
            addToken(Token.THROW);
            addUint16(lineNumber & 0xFFFF);
            stackChange(-1);
            break;

          case Token.RETHROW:
            updateLineNumber(node);
            addIndexOp(Token.RETHROW, getLocalBlockRef(node));
            break;

          case Token.RETURN:
            updateLineNumber(node);
            if (node.getIntProp(Node.GENERATOR_END_PROP, 0) != 0) {
                // We're in a generator, so change RETURN to GENERATOR_END
                addIcode(Icode_GENERATOR_END);
                addUint16(lineNumber & 0xFFFF);
            } else if (child != null) {
                visitExpression(child, ECF_TAIL);
                addToken(Token.RETURN);
                stackChange(-1);
            } else {
                addIcode(Icode_RETUNDEF);
            }
            break;

          case Token.RETURN_RESULT:
            updateLineNumber(node);
            addToken(Token.RETURN_RESULT);
            break;

          case Token.ENUM_INIT_KEYS:
          case Token.ENUM_INIT_VALUES:
          case Token.ENUM_INIT_ARRAY:
            visitExpression(child, 0);
            addIndexOp(type, getLocalBlockRef(node));
            stackChange(-1);
            break;

          case Icode_GENERATOR:
            break;

          default:
            throw badTree(node);
        }

        if (stackDepth != initialStackDepth) {
            throw Kit.codeBug();
        }
    }

    private void visitExpression(Node node, int contextFlags)
    {
        int type = node.getType();
        Node child = node.getFirstChild();
        int savedStackDepth = stackDepth;
        switch (type) {

          case Token.FUNCTION:
            {
                int fnIndex = node.getExistingIntProp(Node.FUNCTION_PROP);
                FunctionNode fn = scriptOrFn.getFunctionNode(fnIndex);
                // See comments in visitStatement for Token.FUNCTION case
                if (fn.getFunctionType() != FunctionNode.FUNCTION_EXPRESSION) {
                    throw Kit.codeBug();
                }
                addIndexOp(Icode_CLOSURE_EXPR, fnIndex);
                stackChange(1);
            }
            break;

          case Token.LOCAL_LOAD:
            {
                int localIndex = getLocalBlockRef(node);
                addIndexOp(Token.LOCAL_LOAD, localIndex);
                stackChange(1);
            }
            break;

          case Token.COMMA:
            {
                Node lastChild = node.getLastChild();
                while (child != lastChild) {
                    visitExpression(child, 0);
                    addIcode(Icode_POP);
                    stackChange(-1);
                    child = child.getNext();
                }
                // Preserve tail context flag if any
                visitExpression(child, contextFlags & ECF_TAIL);
            }
            break;

          case Token.USE_STACK:
            // Indicates that stack was modified externally,
            // like placed catch object
            stackChange(1);
            break;

          case Token.REF_CALL:
          case Token.CALL:
          case Token.NEW:
            {
                if (type == Token.NEW) {
                    visitExpression(child, 0);
                } else {
                    generateCallFunAndThis(child);
                }
                int argCount = 0;
                while ((child = child.getNext()) != null) {
                    visitExpression(child, 0);
                    ++argCount;
                }
                int callType = node.getIntProp(Node.SPECIALCALL_PROP,
                                               Node.NON_SPECIALCALL);
                if (type != Token.REF_CALL && callType != Node.NON_SPECIALCALL) {
                    // embed line number and source filename
                    addIndexOp(Icode_CALLSPECIAL, argCount);
                    addUint8(callType);
                    addUint8(type == Token.NEW ? 1 : 0);
                    addUint16(lineNumber & 0xFFFF);
                } else {
                    // Only use the tail call optimization if we're not in a try
                    // or we're not generating debug info (since the
                    // optimization will confuse the debugger)
                    if (type == Token.CALL && (contextFlags & ECF_TAIL) != 0 &&
                        !compilerEnv.isGenerateDebugInfo() && !itsInTryFlag)
                    {
                        type = Icode_TAIL_CALL;
                    }
                    addIndexOp(type, argCount);
                }
                // adjust stack
                if (type == Token.NEW) {
                    // new: f, args -> result
                    stackChange(-argCount);
                } else {
                    // call: f, thisObj, args -> result
                    // ref_call: f, thisObj, args -> ref
                    stackChange(-1 - argCount);
                }
                if (argCount > itsData.itsMaxCalleeArgs) {
                    itsData.itsMaxCalleeArgs = argCount;
                }
            }
            break;

          case Token.AND:
          case Token.OR:
            {
                visitExpression(child, 0);
                addIcode(Icode_DUP);
                stackChange(1);
                int afterSecondJumpStart = iCodeTop;
                int jump = (type == Token.AND) ? Token.IFNE : Token.IFEQ;
                addGotoOp(jump);
                stackChange(-1);
                addIcode(Icode_POP);
                stackChange(-1);
                child = child.getNext();
                // Preserve tail context flag if any
                visitExpression(child, contextFlags & ECF_TAIL);
                resolveForwardGoto(afterSecondJumpStart);
            }
            break;

          case Token.HOOK:
            {
                Node ifThen = child.getNext();
                Node ifElse = ifThen.getNext();
                visitExpression(child, 0);
                int elseJumpStart = iCodeTop;
                addGotoOp(Token.IFNE);
                stackChange(-1);
                // Preserve tail context flag if any
                visitExpression(ifThen, contextFlags & ECF_TAIL);
                int afterElseJumpStart = iCodeTop;
                addGotoOp(Token.GOTO);
                resolveForwardGoto(elseJumpStart);
                stackDepth = savedStackDepth;
                // Preserve tail context flag if any
                visitExpression(ifElse, contextFlags & ECF_TAIL);
                resolveForwardGoto(afterElseJumpStart);
            }
            break;

          case Token.GETPROP:
          case Token.GETPROPNOWARN:
            visitExpression(child, 0);
            child = child.getNext();
            addStringOp(type, child.getString());
            break;

          case Token.DELPROP:
            boolean isName = child.getType() == Token.BINDNAME;
            visitExpression(child, 0);
            child = child.getNext();
            visitExpression(child, 0);
            if (isName) {
                // special handling for delete name
                addIcode(Icode_DELNAME);
            } else {
                addToken(Token.DELPROP);
            }
            stackChange(-1);
            break;

          case Token.GETELEM:
          case Token.BITAND:
          case Token.BITOR:
          case Token.BITXOR:
          case Token.LSH:
          case Token.RSH:
          case Token.URSH:
          case Token.ADD:
          case Token.SUB:
          case Token.MOD:
          case Token.DIV:
          case Token.MUL:
          case Token.EQ:
          case Token.NE:
          case Token.SHEQ:
          case Token.SHNE:
          case Token.IN:
          case Token.INSTANCEOF:
          case Token.LE:
          case Token.LT:
          case Token.GE:
          case Token.GT:
            visitExpression(child, 0);
            child = child.getNext();
            visitExpression(child, 0);
            addToken(type);
            stackChange(-1);
            break;

          case Token.POS:
          case Token.NEG:
          case Token.NOT:
          case Token.BITNOT:
          case Token.TYPEOF:
          case Token.VOID:
            visitExpression(child, 0);
            if (type == Token.VOID) {
                addIcode(Icode_POP);
                addIcode(Icode_UNDEF);
            } else {
                addToken(type);
            }
            break;

          case Token.GET_REF:
          case Token.DEL_REF:
            visitExpression(child, 0);
            addToken(type);
            break;

          case Token.SETPROP:
          case Token.SETPROP_OP:
            {
                visitExpression(child, 0);
                child = child.getNext();
                String property = child.getString();
                child = child.getNext();
                if (type == Token.SETPROP_OP) {
                    addIcode(Icode_DUP);
                    stackChange(1);
                    addStringOp(Token.GETPROP, property);
                    // Compensate for the following USE_STACK
                    stackChange(-1);
                }
                visitExpression(child, 0);
                addStringOp(Token.SETPROP, property);
                stackChange(-1);
            }
            break;

          case Token.SETELEM:
          case Token.SETELEM_OP:
            visitExpression(child, 0);
            child = child.getNext();
            visitExpression(child, 0);
            child = child.getNext();
            if (type == Token.SETELEM_OP) {
                addIcode(Icode_DUP2);
                stackChange(2);
                addToken(Token.GETELEM);
                stackChange(-1);
                // Compensate for the following USE_STACK
                stackChange(-1);
            }
            visitExpression(child, 0);
            addToken(Token.SETELEM);
            stackChange(-2);
            break;

          case Token.SET_REF:
          case Token.SET_REF_OP:
            visitExpression(child, 0);
            child = child.getNext();
            if (type == Token.SET_REF_OP) {
                addIcode(Icode_DUP);
                stackChange(1);
                addToken(Token.GET_REF);
                // Compensate for the following USE_STACK
                stackChange(-1);
            }
            visitExpression(child, 0);
            addToken(Token.SET_REF);
            stackChange(-1);
            break;

          case Token.STRICT_SETNAME:
          case Token.SETNAME:
            {
                String name = child.getString();
                visitExpression(child, 0);
                child = child.getNext();
                visitExpression(child, 0);
                addStringOp(type, name);
                stackChange(-1);
            }
            break;

          case Token.SETCONST:
            {
                String name = child.getString();
                visitExpression(child, 0);
                child = child.getNext();
                visitExpression(child, 0);
                addStringOp(Icode_SETCONST, name);
                stackChange(-1);
            }
            break;

          case Token.TYPEOFNAME:
            {
                int index = -1;
                // use typeofname if an activation frame exists
                // since the vars all exist there instead of in jregs
                if (itsInFunctionFlag && !itsData.itsNeedsActivation)
                    index = scriptOrFn.getIndexForNameNode(node);
                if (index == -1) {
                    addStringOp(Icode_TYPEOFNAME, node.getString());
                    stackChange(1);
                } else {
                    addVarOp(Token.GETVAR, index);
                    stackChange(1);
                    addToken(Token.TYPEOF);
                }
            }
            break;

          case Token.BINDNAME:
          case Token.NAME:
          case Token.STRING:
            addStringOp(type, node.getString());
            stackChange(1);
            break;

          case Token.INC:
          case Token.DEC:
            visitIncDec(node, child);
            break;

          case Token.NUMBER:
            {
                double num = node.getDouble();
                int inum = (int)num;
                if (inum == num) {
                    if (inum == 0) {
                        addIcode(Icode_ZERO);
                        // Check for negative zero
                        if (1.0 / num < 0.0) {
                            addToken(Token.NEG);
                        }
                    } else if (inum == 1) {
                        addIcode(Icode_ONE);
                    } else if ((short)inum == inum) {
                        addIcode(Icode_SHORTNUMBER);
                        // write short as uin16 bit pattern
                        addUint16(inum & 0xFFFF);
                    } else {
                        addIcode(Icode_INTNUMBER);
                        addInt(inum);
                    }
                } else {
                    int index = getDoubleIndex(num);
                    addIndexOp(Token.NUMBER, index);
                }
                stackChange(1);
            }
            break;

          case Token.GETVAR:
            {
                if (itsData.itsNeedsActivation) Kit.codeBug();
                int index = scriptOrFn.getIndexForNameNode(node);
                addVarOp(Token.GETVAR, index);
                stackChange(1);
            }
            break;

          case Token.SETVAR:
            {
                if (itsData.itsNeedsActivation) Kit.codeBug();
                int index = scriptOrFn.getIndexForNameNode(child);
                child = child.getNext();
                visitExpression(child, 0);
                addVarOp(Token.SETVAR, index);
            }
            break;

          case Token.SETCONSTVAR:
            {
                if (itsData.itsNeedsActivation) Kit.codeBug();
                int index = scriptOrFn.getIndexForNameNode(child);
                child = child.getNext();
                visitExpression(child, 0);
                addVarOp(Token.SETCONSTVAR, index);
            }
            break;

          case Token.NULL:
          case Token.THIS:
          case Token.THISFN:
          case Token.FALSE:
          case Token.TRUE:
            addToken(type);
            stackChange(1);
            break;

          case Token.ENUM_NEXT:
          case Token.ENUM_ID:
            addIndexOp(type, getLocalBlockRef(node));
            stackChange(1);
            break;

          case Token.REGEXP:
            {
                int index = node.getExistingIntProp(Node.REGEXP_PROP);
                addIndexOp(Token.REGEXP, index);
                stackChange(1);
            }
            break;

          case Token.ARRAYLIT:
          case Token.OBJECTLIT:
            visitLiteral(node, child);
            break;

          case Token.ARRAYCOMP:
            visitArrayComprehension(node, child, child.getNext());
            break;

          case Token.REF_SPECIAL:
            visitExpression(child, 0);
            addStringOp(type, (String)node.getProp(Node.NAME_PROP));
            break;

          case Token.REF_MEMBER:
          case Token.REF_NS_MEMBER:
          case Token.REF_NAME:
          case Token.REF_NS_NAME:
            {
                int memberTypeFlags = node.getIntProp(Node.MEMBER_TYPE_PROP, 0);
                // generate possible target, possible namespace and member
                int childCount = 0;
                do {
                    visitExpression(child, 0);
                    ++childCount;
                    child = child.getNext();
                } while (child != null);
                addIndexOp(type, memberTypeFlags);
                stackChange(1 - childCount);
            }
            break;

          case Token.DOTQUERY:
            {
                int queryPC;
                updateLineNumber(node);
                visitExpression(child, 0);
                addIcode(Icode_ENTERDQ);
                stackChange(-1);
                queryPC = iCodeTop;
                visitExpression(child.getNext(), 0);
                addBackwardGoto(Icode_LEAVEDQ, queryPC);
            }
            break;

          case Token.DEFAULTNAMESPACE :
          case Token.ESCXMLATTR :
          case Token.ESCXMLTEXT :
            visitExpression(child, 0);
            addToken(type);
            break;

          case Token.YIELD:
            if (child != null) {
                visitExpression(child, 0);
            } else {
                addIcode(Icode_UNDEF);
                stackChange(1);
            }
            addToken(Token.YIELD);
            addUint16(node.getLineno() & 0xFFFF);
            break;

          case Token.WITHEXPR: {
            Node enterWith = node.getFirstChild();
            Node with = enterWith.getNext();
            visitExpression(enterWith.getFirstChild(), 0);
            addToken(Token.ENTERWITH);
            stackChange(-1);
            visitExpression(with.getFirstChild(), 0);
            addToken(Token.LEAVEWITH);
            break;
          }

          default:
            throw badTree(node);
        }
        if (savedStackDepth + 1 != stackDepth) {
            Kit.codeBug();
        }
    }

    private void generateCallFunAndThis(Node left)
    {
        // Generate code to place on stack function and thisObj
        int type = left.getType();
        switch (type) {
          case Token.NAME: {
            String name = left.getString();
            // stack: ... -> ... function thisObj
            addStringOp(Icode_NAME_AND_THIS, name);
            stackChange(2);
            break;
          }
          case Token.GETPROP:
          case Token.GETELEM: {
            Node target = left.getFirstChild();
            visitExpression(target, 0);
            Node id = target.getNext();
            if (type == Token.GETPROP) {
                String property = id.getString();
                // stack: ... target -> ... function thisObj
                addStringOp(Icode_PROP_AND_THIS, property);
                stackChange(1);
            } else {
                visitExpression(id, 0);
                // stack: ... target id -> ... function thisObj
                addIcode(Icode_ELEM_AND_THIS);
            }
            break;
          }
          default:
            // Including Token.GETVAR
            visitExpression(left, 0);
            // stack: ... value -> ... function thisObj
            addIcode(Icode_VALUE_AND_THIS);
            stackChange(1);
            break;
        }
    }


    private void visitIncDec(Node node, Node child)
    {
        int incrDecrMask = node.getExistingIntProp(Node.INCRDECR_PROP);
        int childType = child.getType();
        switch (childType) {
          case Token.GETVAR : {
            if (itsData.itsNeedsActivation) Kit.codeBug();
            int i = scriptOrFn.getIndexForNameNode(child);
            addVarOp(Icode_VAR_INC_DEC, i);
            addUint8(incrDecrMask);
            stackChange(1);
            break;
          }
          case Token.NAME : {
            String name = child.getString();
            addStringOp(Icode_NAME_INC_DEC, name);
            addUint8(incrDecrMask);
            stackChange(1);
            break;
          }
          case Token.GETPROP : {
            Node object = child.getFirstChild();
            visitExpression(object, 0);
            String property = object.getNext().getString();
            addStringOp(Icode_PROP_INC_DEC, property);
            addUint8(incrDecrMask);
            break;
          }
          case Token.GETELEM : {
            Node object = child.getFirstChild();
            visitExpression(object, 0);
            Node index = object.getNext();
            visitExpression(index, 0);
            addIcode(Icode_ELEM_INC_DEC);
            addUint8(incrDecrMask);
            stackChange(-1);
            break;
          }
          case Token.GET_REF : {
            Node ref = child.getFirstChild();
            visitExpression(ref, 0);
            addIcode(Icode_REF_INC_DEC);
            addUint8(incrDecrMask);
            break;
          }
          default : {
            throw badTree(node);
          }
        }
    }

    private void visitLiteral(Node node, Node child)
    {
        int type = node.getType();
        int count;
        Object[] propertyIds = null;
        if (type == Token.ARRAYLIT) {
            count = 0;
            for (Node n = child; n != null; n = n.getNext()) {
                ++count;
            }
        } else if (type == Token.OBJECTLIT) {
            propertyIds = (Object[])node.getProp(Node.OBJECT_IDS_PROP);
            count = propertyIds.length;
        } else {
            throw badTree(node);
        }
        addIndexOp(Icode_LITERAL_NEW, count);
        stackChange(2);
        while (child != null) {
            int childType = child.getType();
            if (childType == Token.GET) {
                visitExpression(child.getFirstChild(), 0);
                addIcode(Icode_LITERAL_GETTER);
            } else if (childType == Token.SET) {
                visitExpression(child.getFirstChild(), 0);
                addIcode(Icode_LITERAL_SETTER);
            } else {
                visitExpression(child, 0);
                addIcode(Icode_LITERAL_SET);
            }
            stackChange(-1);
            child = child.getNext();
        }
        if (type == Token.ARRAYLIT) {
            int[] skipIndexes = (int[])node.getProp(Node.SKIP_INDEXES_PROP);
            if (skipIndexes == null) {
                addToken(Token.ARRAYLIT);
            } else {
                int index = literalIds.size();
                literalIds.add(skipIndexes);
                addIndexOp(Icode_SPARE_ARRAYLIT, index);
            }
        } else {
            int index = literalIds.size();
            literalIds.add(propertyIds);
            addIndexOp(Token.OBJECTLIT, index);
        }
        stackChange(-1);
    }

    private void visitArrayComprehension(Node node, Node initStmt, Node expr)
    {
        // A bit of a hack: array comprehensions are implemented using
        // statement nodes for the iteration, yet they appear in an
        // expression context. So we pass the current stack depth to
        // visitStatement so it can check that the depth is not altered
        // by statements.
        visitStatement(initStmt, stackDepth);
        visitExpression(expr, 0);
    }

    private int getLocalBlockRef(Node node)
    {
        Node localBlock = (Node)node.getProp(Node.LOCAL_BLOCK_PROP);
        return localBlock.getExistingIntProp(Node.LOCAL_PROP);
    }

    private int getTargetLabel(Node target)
    {
        int label = target.labelId();
        if (label != -1) {
            return label;
        }
        label = labelTableTop;
        if (labelTable == null || label == labelTable.length) {
            if (labelTable == null) {
                labelTable = new int[MIN_LABEL_TABLE_SIZE];
            }else {
                int[] tmp = new int[labelTable.length * 2];
                System.arraycopy(labelTable, 0, tmp, 0, label);
                labelTable = tmp;
            }
        }
        labelTableTop = label + 1;
        labelTable[label] = -1;

        target.labelId(label);
        return label;
    }

    private void markTargetLabel(Node target)
    {
        int label = getTargetLabel(target);
        if (labelTable[label] != -1) {
            // Can mark label only once
            Kit.codeBug();
        }
        labelTable[label] = iCodeTop;
    }

    private void addGoto(Node target, int gotoOp)
    {
        int label = getTargetLabel(target);
        if (!(label < labelTableTop)) Kit.codeBug();
        int targetPC = labelTable[label];

        if (targetPC != -1) {
            addBackwardGoto(gotoOp, targetPC);
        } else {
            int gotoPC = iCodeTop;
            addGotoOp(gotoOp);
            int top = fixupTableTop;
            if (fixupTable == null || top == fixupTable.length) {
                if (fixupTable == null) {
                    fixupTable = new long[MIN_FIXUP_TABLE_SIZE];
                } else {
                    long[] tmp = new long[fixupTable.length * 2];
                    System.arraycopy(fixupTable, 0, tmp, 0, top);
                    fixupTable = tmp;
                }
            }
            fixupTableTop = top + 1;
            fixupTable[top] = ((long)label << 32) | gotoPC;
        }
    }

    private void fixLabelGotos()
    {
        for (int i = 0; i < fixupTableTop; i++) {
            long fixup = fixupTable[i];
            int label = (int)(fixup >> 32);
            int jumpSource = (int)fixup;
            int pc = labelTable[label];
            if (pc == -1) {
                // Unlocated label
                throw Kit.codeBug();
            }
            resolveGoto(jumpSource, pc);
        }
        fixupTableTop = 0;
    }

    private void addBackwardGoto(int gotoOp, int jumpPC)
    {
        int fromPC = iCodeTop;
        // Ensure that this is a jump backward
        if (fromPC <= jumpPC) throw Kit.codeBug();
        addGotoOp(gotoOp);
        resolveGoto(fromPC, jumpPC);
    }

    private void resolveForwardGoto(int fromPC)
    {
        // Ensure that forward jump skips at least self bytecode
        if (iCodeTop < fromPC + 3) throw Kit.codeBug();
        resolveGoto(fromPC, iCodeTop);
    }

    private void resolveGoto(int fromPC, int jumpPC)
    {
        int offset = jumpPC - fromPC;
        // Ensure that jumps do not overlap
        if (0 <= offset && offset <= 2) throw Kit.codeBug();
        int offsetSite = fromPC + 1;
        if (offset != (short)offset) {
            if (itsData.longJumps == null) {
                itsData.longJumps = new UintMap();
            }
            itsData.longJumps.put(offsetSite, jumpPC);
            offset = 0;
        }
        byte[] array = itsData.itsICode;
        array[offsetSite] = (byte)(offset >> 8);
        array[offsetSite + 1] = (byte)offset;
    }

    private void addToken(int token)
    {
        if (!Icode.validTokenCode(token)) throw Kit.codeBug();
        addUint8(token);
    }

    private void addIcode(int icode)
    {
        if (!Icode.validIcode(icode)) throw Kit.codeBug();
        // Write negative icode as uint8 bits
        addUint8(icode & 0xFF);
    }

    private void addUint8(int value)
    {
        if ((value & ~0xFF) != 0) throw Kit.codeBug();
        byte[] array = itsData.itsICode;
        int top = iCodeTop;
        if (top == array.length) {
            array = increaseICodeCapacity(1);
        }
        array[top] = (byte)value;
        iCodeTop = top + 1;
    }

    private void addUint16(int value)
    {
        if ((value & ~0xFFFF) != 0) throw Kit.codeBug();
        byte[] array = itsData.itsICode;
        int top = iCodeTop;
        if (top + 2 > array.length) {
            array = increaseICodeCapacity(2);
        }
        array[top] = (byte)(value >>> 8);
        array[top + 1] = (byte)value;
        iCodeTop = top + 2;
    }

    private void addInt(int i)
    {
        byte[] array = itsData.itsICode;
        int top = iCodeTop;
        if (top + 4 > array.length) {
            array = increaseICodeCapacity(4);
        }
        array[top] = (byte)(i >>> 24);
        array[top + 1] = (byte)(i >>> 16);
        array[top + 2] = (byte)(i >>> 8);
        array[top + 3] = (byte)i;
        iCodeTop = top + 4;
    }

    private int getDoubleIndex(double num)
    {
        int index = doubleTableTop;
        if (index == 0) {
            itsData.itsDoubleTable = new double[64];
        } else if (itsData.itsDoubleTable.length == index) {
            double[] na = new double[index * 2];
            System.arraycopy(itsData.itsDoubleTable, 0, na, 0, index);
            itsData.itsDoubleTable = na;
        }
        itsData.itsDoubleTable[index] = num;
        doubleTableTop = index + 1;
        return index;
    }

    private void addGotoOp(int gotoOp)
    {
        byte[] array = itsData.itsICode;
        int top = iCodeTop;
        if (top + 3 > array.length) {
            array = increaseICodeCapacity(3);
        }
        array[top] = (byte)gotoOp;
        // Offset would written later
        iCodeTop = top + 1 + 2;
    }

    private void addVarOp(int op, int varIndex)
    {
        switch (op) {
          case Token.SETCONSTVAR:
            if (varIndex < 128) {
                addIcode(Icode_SETCONSTVAR1);
                addUint8(varIndex);
                return;
            }
            addIndexOp(Icode_SETCONSTVAR, varIndex);
            return;
          case Token.GETVAR:
          case Token.SETVAR:
            if (varIndex < 128) {
                addIcode(op == Token.GETVAR ? Icode_GETVAR1 : Icode_SETVAR1);
                addUint8(varIndex);
                return;
            }
            // fallthrough
          case Icode_VAR_INC_DEC:
            addIndexOp(op, varIndex);
            return;
        }
        throw Kit.codeBug();
    }

    private void addStringOp(int op, String str)
    {
        addStringPrefix(str);
        if (Icode.validIcode(op)) {
            addIcode(op);
        } else {
            addToken(op);
        }
    }

    private void addIndexOp(int op, int index)
    {
        addIndexPrefix(index);
        if (Icode.validIcode(op)) {
            addIcode(op);
        } else {
            addToken(op);
        }
    }

    private void addStringPrefix(String str)
    {
        int index = strings.get(str, -1);
        if (index == -1) {
            index = strings.size();
            strings.put(str, index);
        }
        if (index < 4) {
            addIcode(Icode_REG_STR_C0 - index);
        } else if (index <= 0xFF) {
            addIcode(Icode_REG_STR1);
            addUint8(index);
         } else if (index <= 0xFFFF) {
            addIcode(Icode_REG_STR2);
            addUint16(index);
         } else {
            addIcode(Icode_REG_STR4);
            addInt(index);
        }
    }

    private void addIndexPrefix(int index)
    {
        if (index < 0) Kit.codeBug();
        if (index < 6) {
            addIcode(Icode_REG_IND_C0 - index);
        } else if (index <= 0xFF) {
            addIcode(Icode_REG_IND1);
            addUint8(index);
         } else if (index <= 0xFFFF) {
            addIcode(Icode_REG_IND2);
            addUint16(index);
         } else {
            addIcode(Icode_REG_IND4);
            addInt(index);
        }
    }

    private void addExceptionHandler(int icodeStart, int icodeEnd,
                                     int handlerStart, boolean isFinally,
                                     int exceptionObjectLocal, int scopeLocal)
    {
        int top = exceptionTableTop;
        int[] table = itsData.itsExceptionTable;
        if (table == null) {
            if (top != 0) Kit.codeBug();
            table = new int[Interpreter.EXCEPTION_SLOT_SIZE * 2];
            itsData.itsExceptionTable = table;
        } else if (table.length == top) {
            table = new int[table.length * 2];
            System.arraycopy(itsData.itsExceptionTable, 0, table, 0, top);
            itsData.itsExceptionTable = table;
        }
        table[top + Interpreter.EXCEPTION_TRY_START_SLOT]  = icodeStart;
        table[top + Interpreter.EXCEPTION_TRY_END_SLOT]    = icodeEnd;
        table[top + Interpreter.EXCEPTION_HANDLER_SLOT]    = handlerStart;
        table[top + Interpreter.EXCEPTION_TYPE_SLOT]     = isFinally ? 1 : 0;
        table[top + Interpreter.EXCEPTION_LOCAL_SLOT]    = exceptionObjectLocal;
        table[top + Interpreter.EXCEPTION_SCOPE_SLOT]    = scopeLocal;

        exceptionTableTop = top + Interpreter.EXCEPTION_SLOT_SIZE;
    }

    private byte[] increaseICodeCapacity(int extraSize)
    {
        int capacity = itsData.itsICode.length;
        int top = iCodeTop;
        if (top + extraSize <= capacity) throw Kit.codeBug();
        capacity *= 2;
        if (top + extraSize > capacity) {
            capacity = top + extraSize;
        }
        byte[] array = new byte[capacity];
        System.arraycopy(itsData.itsICode, 0, array, 0, top);
        itsData.itsICode = array;
        return array;
    }

    private void stackChange(int change)
    {
        if (change <= 0) {
            stackDepth += change;
        } else {
            int newDepth = stackDepth + change;
            if (newDepth > itsData.itsMaxStack) {
                itsData.itsMaxStack = newDepth;
            }
            stackDepth = newDepth;
        }
    }

    private int allocLocal()
    {
        int localSlot = localTop;
        ++localTop;
        if (localTop > itsData.itsMaxLocals) {
            itsData.itsMaxLocals = localTop;
        }
        return localSlot;
    }

    private void releaseLocal(int localSlot)
    {
        --localTop;
        if (localSlot != localTop) Kit.codeBug();
    }
}
