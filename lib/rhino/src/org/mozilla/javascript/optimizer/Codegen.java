/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */


package org.mozilla.javascript.optimizer;

import org.mozilla.javascript.*;
import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.ast.Jump;
import org.mozilla.javascript.ast.Name;
import org.mozilla.javascript.ast.ScriptNode;
import org.mozilla.classfile.*;

import java.util.*;
import java.lang.reflect.Constructor;

import static org.mozilla.classfile.ClassFileWriter.ACC_FINAL;
import static org.mozilla.classfile.ClassFileWriter.ACC_PRIVATE;
import static org.mozilla.classfile.ClassFileWriter.ACC_PUBLIC;
import static org.mozilla.classfile.ClassFileWriter.ACC_STATIC;
import static org.mozilla.classfile.ClassFileWriter.ACC_VOLATILE;

/**
 * This class generates code for a given IR tree.
 *
 */

public class Codegen implements Evaluator
{
    public void captureStackInfo(RhinoException ex) {
        throw new UnsupportedOperationException();
    }

    public String getSourcePositionFromStack(Context cx, int[] linep) {
        throw new UnsupportedOperationException();
    }

    public String getPatchedStack(RhinoException ex, String nativeStackTrace) {
        throw new UnsupportedOperationException();
    }

    public List<String> getScriptStack(RhinoException ex) {
        throw new UnsupportedOperationException();
    }

    public void setEvalScriptFlag(Script script) {
        throw new UnsupportedOperationException();
    }

    public Object compile(CompilerEnvirons compilerEnv,
                          ScriptNode tree,
                          String encodedSource,
                          boolean returnFunction)
    {
        int serial;
        synchronized (globalLock) {
            serial = ++globalSerialClassCounter;
        }

        String baseName = "c";
        if (tree.getSourceName().length() > 0) {
          baseName = tree.getSourceName().replaceAll("\\W", "_");
          if (!Character.isJavaIdentifierStart(baseName.charAt(0))) {
            baseName = "_" + baseName;
          }
        }

        String mainClassName = "org.mozilla.javascript.gen." + baseName + "_" + serial;

        byte[] mainClassBytes = compileToClassFile(compilerEnv, mainClassName,
                                                   tree, encodedSource,
                                                   returnFunction);

        return new Object[] { mainClassName, mainClassBytes };
    }

    public Script createScriptObject(Object bytecode,
                                     Object staticSecurityDomain)
    {
        Class<?> cl = defineClass(bytecode, staticSecurityDomain);

        Script script;
        try {
            script = (Script)cl.newInstance();
        } catch (Exception ex) {
            throw new RuntimeException
                ("Unable to instantiate compiled class:" + ex.toString());
        }
        return script;
    }

    public Function createFunctionObject(Context cx, Scriptable scope,
                                         Object bytecode,
                                         Object staticSecurityDomain)
    {
        Class<?> cl = defineClass(bytecode, staticSecurityDomain);

        NativeFunction f;
        try {
            Constructor<?>ctor = cl.getConstructors()[0];
            Object[] initArgs = { scope, cx, Integer.valueOf(0) };
            f = (NativeFunction)ctor.newInstance(initArgs);
        } catch (Exception ex) {
            throw new RuntimeException
                ("Unable to instantiate compiled class:"+ex.toString());
        }
        return f;
    }

    private Class<?> defineClass(Object bytecode,
                                 Object staticSecurityDomain)
    {
        Object[] nameBytesPair = (Object[])bytecode;
        String className = (String)nameBytesPair[0];
        byte[] classBytes = (byte[])nameBytesPair[1];

        // The generated classes in this case refer only to Rhino classes
        // which must be accessible through this class loader
        ClassLoader rhinoLoader = getClass().getClassLoader();
        GeneratedClassLoader loader;
        loader = SecurityController.createLoader(rhinoLoader,
                                                 staticSecurityDomain);
        Exception e;
        try {
            Class<?> cl = loader.defineClass(className, classBytes);
            loader.linkClass(cl);
            return cl;
        } catch (SecurityException x) {
            e = x;
        } catch (IllegalArgumentException x) {
            e = x;
        }
        throw new RuntimeException("Malformed optimizer package " + e);
    }

    public byte[] compileToClassFile(CompilerEnvirons compilerEnv,
                                     String mainClassName,
                                     ScriptNode scriptOrFn,
                                     String encodedSource,
                                     boolean returnFunction)
    {
        this.compilerEnv = compilerEnv;

        transform(scriptOrFn);

        if (Token.printTrees) {
            System.out.println(scriptOrFn.toStringTree(scriptOrFn));
        }

        if (returnFunction) {
            scriptOrFn = scriptOrFn.getFunctionNode(0);
        }

        initScriptNodesData(scriptOrFn);

        this.mainClassName = mainClassName;
        this.mainClassSignature
            = ClassFileWriter.classNameToSignature(mainClassName);

        try {
            return generateCode(encodedSource);
        } catch (ClassFileWriter.ClassFileFormatException e) {
            throw reportClassFileFormatException(scriptOrFn, e.getMessage());
        }
    }

    private RuntimeException reportClassFileFormatException(
        ScriptNode scriptOrFn,
        String message)
    {
        String msg = scriptOrFn instanceof FunctionNode
        ? ScriptRuntime.getMessage2("msg.while.compiling.fn",
            ((FunctionNode)scriptOrFn).getFunctionName(), message)
        : ScriptRuntime.getMessage1("msg.while.compiling.script", message);
        return Context.reportRuntimeError(msg, scriptOrFn.getSourceName(),
            scriptOrFn.getLineno(), null, 0);
    }

    private void transform(ScriptNode tree)
    {
        initOptFunctions_r(tree);

        int optLevel = compilerEnv.getOptimizationLevel();

        Map<String,OptFunctionNode> possibleDirectCalls = null;
        if (optLevel > 0) {
           /*
            * Collect all of the contained functions into a hashtable
            * so that the call optimizer can access the class name & parameter
            * count for any call it encounters
            */
            if (tree.getType() == Token.SCRIPT) {
                int functionCount = tree.getFunctionCount();
                for (int i = 0; i != functionCount; ++i) {
                    OptFunctionNode ofn = OptFunctionNode.get(tree, i);
                    if (ofn.fnode.getFunctionType()
                        == FunctionNode.FUNCTION_STATEMENT)
                    {
                        String name = ofn.fnode.getName();
                        if (name.length() != 0) {
                            if (possibleDirectCalls == null) {
                                possibleDirectCalls = new HashMap<String,OptFunctionNode>();
                            }
                            possibleDirectCalls.put(name, ofn);
                        }
                    }
                }
            }
        }

        if (possibleDirectCalls != null) {
            directCallTargets = new ObjArray();
        }

        OptTransformer ot = new OptTransformer(possibleDirectCalls,
                                               directCallTargets);
        ot.transform(tree);

        if (optLevel > 0) {
            (new Optimizer()).optimize(tree);
        }
    }

    private static void initOptFunctions_r(ScriptNode scriptOrFn)
    {
        for (int i = 0, N = scriptOrFn.getFunctionCount(); i != N; ++i) {
            FunctionNode fn = scriptOrFn.getFunctionNode(i);
            new OptFunctionNode(fn);
            initOptFunctions_r(fn);
        }
    }

    private void initScriptNodesData(ScriptNode scriptOrFn)
    {
        ObjArray x = new ObjArray();
        collectScriptNodes_r(scriptOrFn, x);

        int count = x.size();
        scriptOrFnNodes = new ScriptNode[count];
        x.toArray(scriptOrFnNodes);

        scriptOrFnIndexes = new ObjToIntMap(count);
        for (int i = 0; i != count; ++i) {
            scriptOrFnIndexes.put(scriptOrFnNodes[i], i);
        }
    }

    private static void collectScriptNodes_r(ScriptNode n,
                                                 ObjArray x)
    {
        x.add(n);
        int nestedCount = n.getFunctionCount();
        for (int i = 0; i != nestedCount; ++i) {
            collectScriptNodes_r(n.getFunctionNode(i), x);
        }
    }

    private byte[] generateCode(String encodedSource)
    {
        boolean hasScript = (scriptOrFnNodes[0].getType() == Token.SCRIPT);
        boolean hasFunctions = (scriptOrFnNodes.length > 1 || !hasScript);

        String sourceFile = null;
        if (compilerEnv.isGenerateDebugInfo()) {
            sourceFile = scriptOrFnNodes[0].getSourceName();
        }

        ClassFileWriter cfw = new ClassFileWriter(mainClassName,
                                                  SUPER_CLASS_NAME,
                                                  sourceFile);
        cfw.addField(ID_FIELD_NAME, "I", ACC_PRIVATE);

        if (hasFunctions) {
            generateFunctionConstructor(cfw);
        }

        if (hasScript) {
            cfw.addInterface("org/mozilla/javascript/Script");
            generateScriptCtor(cfw);
            generateMain(cfw);
            generateExecute(cfw);
        }

        generateCallMethod(cfw);
        generateResumeGenerator(cfw);

        generateNativeFunctionOverrides(cfw, encodedSource);

        int count = scriptOrFnNodes.length;
        for (int i = 0; i != count; ++i) {
            ScriptNode n = scriptOrFnNodes[i];

            BodyCodegen bodygen = new BodyCodegen();
            bodygen.cfw = cfw;
            bodygen.codegen = this;
            bodygen.compilerEnv = compilerEnv;
            bodygen.scriptOrFn = n;
            bodygen.scriptOrFnIndex = i;

            try {
                bodygen.generateBodyCode();
            } catch (ClassFileWriter.ClassFileFormatException e) {
                throw reportClassFileFormatException(n, e.getMessage());
            }

            if (n.getType() == Token.FUNCTION) {
                OptFunctionNode ofn = OptFunctionNode.get(n);
                generateFunctionInit(cfw, ofn);
                if (ofn.isTargetOfDirectCall()) {
                    emitDirectConstructor(cfw, ofn);
                }
            }
        }

        emitRegExpInit(cfw);
        emitConstantDudeInitializers(cfw);

        return cfw.toByteArray();
    }

    private void emitDirectConstructor(ClassFileWriter cfw,
                                       OptFunctionNode ofn)
    {
/*
    we generate ..
        Scriptable directConstruct(<directCallArgs>) {
            Scriptable newInstance = createObject(cx, scope);
            Object val = <body-name>(cx, scope, newInstance, <directCallArgs>);
            if (val instanceof Scriptable) {
                return (Scriptable) val;
            }
            return newInstance;
        }
*/
        cfw.startMethod(getDirectCtorName(ofn.fnode),
                        getBodyMethodSignature(ofn.fnode),
                        (short)(ACC_STATIC | ACC_PRIVATE));

        int argCount = ofn.fnode.getParamCount();
        int firstLocal = (4 + argCount * 3) + 1;

        cfw.addALoad(0); // this
        cfw.addALoad(1); // cx
        cfw.addALoad(2); // scope
        cfw.addInvoke(ByteCode.INVOKEVIRTUAL,
                      "org/mozilla/javascript/BaseFunction",
                      "createObject",
                      "(Lorg/mozilla/javascript/Context;"
                      +"Lorg/mozilla/javascript/Scriptable;"
                      +")Lorg/mozilla/javascript/Scriptable;");
        cfw.addAStore(firstLocal);

        cfw.addALoad(0);
        cfw.addALoad(1);
        cfw.addALoad(2);
        cfw.addALoad(firstLocal);
        for (int i = 0; i < argCount; i++) {
            cfw.addALoad(4 + (i * 3));
            cfw.addDLoad(5 + (i * 3));
        }
        cfw.addALoad(4 + argCount * 3);
        cfw.addInvoke(ByteCode.INVOKESTATIC,
                      mainClassName,
                      getBodyMethodName(ofn.fnode),
                      getBodyMethodSignature(ofn.fnode));
        int exitLabel = cfw.acquireLabel();
        cfw.add(ByteCode.DUP); // make a copy of direct call result
        cfw.add(ByteCode.INSTANCEOF, "org/mozilla/javascript/Scriptable");
        cfw.add(ByteCode.IFEQ, exitLabel);
        // cast direct call result
        cfw.add(ByteCode.CHECKCAST, "org/mozilla/javascript/Scriptable");
        cfw.add(ByteCode.ARETURN);
        cfw.markLabel(exitLabel);

        cfw.addALoad(firstLocal);
        cfw.add(ByteCode.ARETURN);

        cfw.stopMethod((short)(firstLocal + 1));
    }

    static boolean isGenerator(ScriptNode node)
    {
        return (node.getType() == Token.FUNCTION ) &&
                ((FunctionNode)node).isGenerator();
    }

    // How dispatch to generators works:
    // Two methods are generated corresponding to a user-written generator.
    // One of these creates a generator object (NativeGenerator), which is
    // returned to the user. The other method contains all of the body code
    // of the generator.
    // When a user calls a generator, the call() method dispatches control to
    // to the method that creates the NativeGenerator object. Subsequently when
    // the user invokes .next(), .send() or any such method on the generator
    // object, the resumeGenerator() below dispatches the call to the
    // method corresponding to the generator body. As a matter of convention
    // the generator body is given the name of the generator activation function
    // appended by "_gen".
    private void generateResumeGenerator(ClassFileWriter cfw)
    {
        boolean hasGenerators = false;
        for (int i=0; i < scriptOrFnNodes.length; i++) {
            if (isGenerator(scriptOrFnNodes[i]))
            	hasGenerators = true;
        }

        // if there are no generators defined, we don't implement a
        // resumeGenerator(). The base class provides a default implementation.
        if (!hasGenerators)
            return;

        cfw.startMethod("resumeGenerator",
                        "(Lorg/mozilla/javascript/Context;" +
                        "Lorg/mozilla/javascript/Scriptable;" +
                        "ILjava/lang/Object;" +
                        "Ljava/lang/Object;)Ljava/lang/Object;",
                        (short)(ACC_PUBLIC | ACC_FINAL));

        // load arguments for dispatch to the corresponding *_gen method
        cfw.addALoad(0);
        cfw.addALoad(1);
        cfw.addALoad(2);
        cfw.addALoad(4);
        cfw.addALoad(5);
        cfw.addILoad(3);

        cfw.addLoadThis();
        cfw.add(ByteCode.GETFIELD, cfw.getClassName(), ID_FIELD_NAME, "I");

        int startSwitch = cfw.addTableSwitch(0, scriptOrFnNodes.length - 1);
        cfw.markTableSwitchDefault(startSwitch);
        int endlabel = cfw.acquireLabel();

        for (int i = 0; i < scriptOrFnNodes.length; i++) {
            ScriptNode n = scriptOrFnNodes[i];
            cfw.markTableSwitchCase(startSwitch, i, (short)6);
            if (isGenerator(n)) {
                String type = "(" +
                              mainClassSignature +
                              "Lorg/mozilla/javascript/Context;" +
                              "Lorg/mozilla/javascript/Scriptable;" +
                              "Ljava/lang/Object;" +
                              "Ljava/lang/Object;I)Ljava/lang/Object;";
                cfw.addInvoke(ByteCode.INVOKESTATIC,
                              mainClassName,
                              getBodyMethodName(n) + "_gen",
                              type);
                cfw.add(ByteCode.ARETURN);
            } else {
                cfw.add(ByteCode.GOTO, endlabel);
            }
        }

        cfw.markLabel(endlabel);
        pushUndefined(cfw);
        cfw.add(ByteCode.ARETURN);


        // this method uses as many locals as there are arguments (hence 6)
        cfw.stopMethod((short)6);
    }

    private void generateCallMethod(ClassFileWriter cfw)
    {
        cfw.startMethod("call",
                        "(Lorg/mozilla/javascript/Context;" +
                        "Lorg/mozilla/javascript/Scriptable;" +
                        "Lorg/mozilla/javascript/Scriptable;" +
                        "[Ljava/lang/Object;)Ljava/lang/Object;",
                        (short)(ACC_PUBLIC | ACC_FINAL));

        // Generate code for:
        // if (!ScriptRuntime.hasTopCall(cx)) {
        //     return ScriptRuntime.doTopCall(this, cx, scope, thisObj, args);
        // }

        int nonTopCallLabel = cfw.acquireLabel();
        cfw.addALoad(1); //cx
        cfw.addInvoke(ByteCode.INVOKESTATIC,
                      "org/mozilla/javascript/ScriptRuntime",
                      "hasTopCall",
                      "(Lorg/mozilla/javascript/Context;"
                      +")Z");
        cfw.add(ByteCode.IFNE, nonTopCallLabel);
        cfw.addALoad(0);
        cfw.addALoad(1);
        cfw.addALoad(2);
        cfw.addALoad(3);
        cfw.addALoad(4);
        cfw.addInvoke(ByteCode.INVOKESTATIC,
                      "org/mozilla/javascript/ScriptRuntime",
                      "doTopCall",
                      "(Lorg/mozilla/javascript/Callable;"
                      +"Lorg/mozilla/javascript/Context;"
                      +"Lorg/mozilla/javascript/Scriptable;"
                      +"Lorg/mozilla/javascript/Scriptable;"
                      +"[Ljava/lang/Object;"
                      +")Ljava/lang/Object;");
        cfw.add(ByteCode.ARETURN);
        cfw.markLabel(nonTopCallLabel);

        // Now generate switch to call the real methods
        cfw.addALoad(0);
        cfw.addALoad(1);
        cfw.addALoad(2);
        cfw.addALoad(3);
        cfw.addALoad(4);

        int end = scriptOrFnNodes.length;
        boolean generateSwitch = (2 <= end);

        int switchStart = 0;
        int switchStackTop = 0;
        if (generateSwitch) {
            cfw.addLoadThis();
            cfw.add(ByteCode.GETFIELD, cfw.getClassName(), ID_FIELD_NAME, "I");
            // do switch from (1,  end - 1) mapping 0 to
            // the default case
            switchStart = cfw.addTableSwitch(1, end - 1);
        }

        for (int i = 0; i != end; ++i) {
            ScriptNode n = scriptOrFnNodes[i];
            if (generateSwitch) {
                if (i == 0) {
                    cfw.markTableSwitchDefault(switchStart);
                    switchStackTop = cfw.getStackTop();
                } else {
                    cfw.markTableSwitchCase(switchStart, i - 1,
                                            switchStackTop);
                }
            }
            if (n.getType() == Token.FUNCTION) {
                OptFunctionNode ofn = OptFunctionNode.get(n);
                if (ofn.isTargetOfDirectCall()) {
                    int pcount = ofn.fnode.getParamCount();
                    if (pcount != 0) {
                        // loop invariant:
                        // stack top == arguments array from addALoad4()
                        for (int p = 0; p != pcount; ++p) {
                            cfw.add(ByteCode.ARRAYLENGTH);
                            cfw.addPush(p);
                            int undefArg = cfw.acquireLabel();
                            int beyond = cfw.acquireLabel();
                            cfw.add(ByteCode.IF_ICMPLE, undefArg);
                            // get array[p]
                            cfw.addALoad(4);
                            cfw.addPush(p);
                            cfw.add(ByteCode.AALOAD);
                            cfw.add(ByteCode.GOTO, beyond);
                            cfw.markLabel(undefArg);
                            pushUndefined(cfw);
                            cfw.markLabel(beyond);
                            // Only one push
                            cfw.adjustStackTop(-1);
                            cfw.addPush(0.0);
                            // restore invariant
                            cfw.addALoad(4);
                        }
                    }
                }
            }
            cfw.addInvoke(ByteCode.INVOKESTATIC,
                          mainClassName,
                          getBodyMethodName(n),
                          getBodyMethodSignature(n));
            cfw.add(ByteCode.ARETURN);
        }
        cfw.stopMethod((short)5);
        // 5: this, cx, scope, js this, args[]
    }

    private void generateMain(ClassFileWriter cfw)
    {
        cfw.startMethod("main", "([Ljava/lang/String;)V",
                        (short)(ACC_PUBLIC | ACC_STATIC));

        // load new ScriptImpl()
        cfw.add(ByteCode.NEW, cfw.getClassName());
        cfw.add(ByteCode.DUP);
        cfw.addInvoke(ByteCode.INVOKESPECIAL, cfw.getClassName(),
                      "<init>", "()V");
         // load 'args'
        cfw.add(ByteCode.ALOAD_0);
        // Call mainMethodClass.main(Script script, String[] args)
        cfw.addInvoke(ByteCode.INVOKESTATIC,
                      mainMethodClass,
                      "main",
                      "(Lorg/mozilla/javascript/Script;[Ljava/lang/String;)V");
        cfw.add(ByteCode.RETURN);
        // 1 = String[] args
        cfw.stopMethod((short)1);
    }

    private void generateExecute(ClassFileWriter cfw)
    {
        cfw.startMethod("exec",
                        "(Lorg/mozilla/javascript/Context;"
                        +"Lorg/mozilla/javascript/Scriptable;"
                        +")Ljava/lang/Object;",
                        (short)(ACC_PUBLIC | ACC_FINAL));

        final int CONTEXT_ARG = 1;
        final int SCOPE_ARG = 2;

        cfw.addLoadThis();
        cfw.addALoad(CONTEXT_ARG);
        cfw.addALoad(SCOPE_ARG);
        cfw.add(ByteCode.DUP);
        cfw.add(ByteCode.ACONST_NULL);
        cfw.addInvoke(ByteCode.INVOKEVIRTUAL,
                      cfw.getClassName(),
                      "call",
                      "(Lorg/mozilla/javascript/Context;"
                      +"Lorg/mozilla/javascript/Scriptable;"
                      +"Lorg/mozilla/javascript/Scriptable;"
                      +"[Ljava/lang/Object;"
                      +")Ljava/lang/Object;");

        cfw.add(ByteCode.ARETURN);
        // 3 = this + context + scope
        cfw.stopMethod((short)3);
    }

    private void generateScriptCtor(ClassFileWriter cfw)
    {
        cfw.startMethod("<init>", "()V", ACC_PUBLIC);

        cfw.addLoadThis();
        cfw.addInvoke(ByteCode.INVOKESPECIAL, SUPER_CLASS_NAME,
                      "<init>", "()V");
        // set id to 0
        cfw.addLoadThis();
        cfw.addPush(0);
        cfw.add(ByteCode.PUTFIELD, cfw.getClassName(), ID_FIELD_NAME, "I");

        cfw.add(ByteCode.RETURN);
        // 1 parameter = this
        cfw.stopMethod((short)1);
    }

    private void generateFunctionConstructor(ClassFileWriter cfw)
    {
        final int SCOPE_ARG = 1;
        final int CONTEXT_ARG = 2;
        final int ID_ARG = 3;

        cfw.startMethod("<init>", FUNCTION_CONSTRUCTOR_SIGNATURE, ACC_PUBLIC);
        cfw.addALoad(0);
        cfw.addInvoke(ByteCode.INVOKESPECIAL, SUPER_CLASS_NAME,
                      "<init>", "()V");

        cfw.addLoadThis();
        cfw.addILoad(ID_ARG);
        cfw.add(ByteCode.PUTFIELD, cfw.getClassName(), ID_FIELD_NAME, "I");

        cfw.addLoadThis();
        cfw.addALoad(CONTEXT_ARG);
        cfw.addALoad(SCOPE_ARG);

        int start = (scriptOrFnNodes[0].getType() == Token.SCRIPT) ? 1 : 0;
        int end = scriptOrFnNodes.length;
        if (start == end) throw badTree();
        boolean generateSwitch = (2 <= end - start);

        int switchStart = 0;
        int switchStackTop = 0;
        if (generateSwitch) {
            cfw.addILoad(ID_ARG);
            // do switch from (start + 1,  end - 1) mapping start to
            // the default case
            switchStart = cfw.addTableSwitch(start + 1, end - 1);
        }

        for (int i = start; i != end; ++i) {
            if (generateSwitch) {
                if (i == start) {
                    cfw.markTableSwitchDefault(switchStart);
                    switchStackTop = cfw.getStackTop();
                } else {
                    cfw.markTableSwitchCase(switchStart, i - 1 - start,
                                            switchStackTop);
                }
            }
            OptFunctionNode ofn = OptFunctionNode.get(scriptOrFnNodes[i]);
            cfw.addInvoke(ByteCode.INVOKESPECIAL,
                          mainClassName,
                          getFunctionInitMethodName(ofn),
                          FUNCTION_INIT_SIGNATURE);
            cfw.add(ByteCode.RETURN);
        }

        // 4 = this + scope + context + id
        cfw.stopMethod((short)4);
    }

    private void generateFunctionInit(ClassFileWriter cfw,
                                      OptFunctionNode ofn)
    {
        final int CONTEXT_ARG = 1;
        final int SCOPE_ARG = 2;
        cfw.startMethod(getFunctionInitMethodName(ofn),
                        FUNCTION_INIT_SIGNATURE,
                        (short)(ACC_PRIVATE | ACC_FINAL));

        // Call NativeFunction.initScriptFunction
        cfw.addLoadThis();
        cfw.addALoad(CONTEXT_ARG);
        cfw.addALoad(SCOPE_ARG);
        cfw.addInvoke(ByteCode.INVOKEVIRTUAL,
                      "org/mozilla/javascript/NativeFunction",
                      "initScriptFunction",
                      "(Lorg/mozilla/javascript/Context;"
                      +"Lorg/mozilla/javascript/Scriptable;"
                      +")V");

        // precompile all regexp literals
        if (ofn.fnode.getRegexpCount() != 0) {
            cfw.addALoad(CONTEXT_ARG);
            cfw.addInvoke(ByteCode.INVOKESTATIC, mainClassName,
                          REGEXP_INIT_METHOD_NAME, REGEXP_INIT_METHOD_SIGNATURE);
        }

        cfw.add(ByteCode.RETURN);
        // 3 = (scriptThis/functionRef) + scope + context
        cfw.stopMethod((short)3);
    }

    private void generateNativeFunctionOverrides(ClassFileWriter cfw,
                                                 String encodedSource)
    {
        // Override NativeFunction.getLanguageVersion() with
        // public int getLanguageVersion() { return <version-constant>; }

        cfw.startMethod("getLanguageVersion", "()I", ACC_PUBLIC);

        cfw.addPush(compilerEnv.getLanguageVersion());
        cfw.add(ByteCode.IRETURN);

        // 1: this and no argument or locals
        cfw.stopMethod((short)1);

        // The rest of NativeFunction overrides require specific code for each
        // script/function id

        final int Do_getFunctionName      = 0;
        final int Do_getParamCount        = 1;
        final int Do_getParamAndVarCount  = 2;
        final int Do_getParamOrVarName    = 3;
        final int Do_getEncodedSource     = 4;
        final int Do_getParamOrVarConst   = 5;
        final int SWITCH_COUNT            = 6;

        for (int methodIndex = 0; methodIndex != SWITCH_COUNT; ++methodIndex) {
            if (methodIndex == Do_getEncodedSource && encodedSource == null) {
                continue;
            }

            // Generate:
            //   prologue;
            //   switch over function id to implement function-specific action
            //   epilogue

            short methodLocals;
            switch (methodIndex) {
              case Do_getFunctionName:
                methodLocals = 1; // Only this
                cfw.startMethod("getFunctionName", "()Ljava/lang/String;",
                                ACC_PUBLIC);
                break;
              case Do_getParamCount:
                methodLocals = 1; // Only this
                cfw.startMethod("getParamCount", "()I",
                                ACC_PUBLIC);
                break;
              case Do_getParamAndVarCount:
                methodLocals = 1; // Only this
                cfw.startMethod("getParamAndVarCount", "()I",
                                ACC_PUBLIC);
                break;
              case Do_getParamOrVarName:
                methodLocals = 1 + 1; // this + paramOrVarIndex
                cfw.startMethod("getParamOrVarName", "(I)Ljava/lang/String;",
                                ACC_PUBLIC);
                break;
              case Do_getParamOrVarConst:
                methodLocals = 1 + 1 + 1; // this + paramOrVarName
                cfw.startMethod("getParamOrVarConst", "(I)Z",
                                ACC_PUBLIC);
                break;
              case Do_getEncodedSource:
                methodLocals = 1; // Only this
                cfw.startMethod("getEncodedSource", "()Ljava/lang/String;",
                                ACC_PUBLIC);
                cfw.addPush(encodedSource);
                break;
              default:
                throw Kit.codeBug();
            }

            int count = scriptOrFnNodes.length;

            int switchStart = 0;
            int switchStackTop = 0;
            if (count > 1) {
                // Generate switch but only if there is more then one
                // script/function
                cfw.addLoadThis();
                cfw.add(ByteCode.GETFIELD, cfw.getClassName(),
                        ID_FIELD_NAME, "I");

                // do switch from 1 .. count - 1 mapping 0 to the default case
                switchStart = cfw.addTableSwitch(1, count - 1);
            }

            for (int i = 0; i != count; ++i) {
                ScriptNode n = scriptOrFnNodes[i];
                if (i == 0) {
                    if (count > 1) {
                        cfw.markTableSwitchDefault(switchStart);
                        switchStackTop = cfw.getStackTop();
                    }
                } else {
                    cfw.markTableSwitchCase(switchStart, i - 1,
                                            switchStackTop);
                }

                // Impelemnet method-specific switch code
                switch (methodIndex) {
                  case Do_getFunctionName:
                    // Push function name
                    if (n.getType() == Token.SCRIPT) {
                        cfw.addPush("");
                    } else {
                        String name = ((FunctionNode)n).getName();
                        cfw.addPush(name);
                    }
                    cfw.add(ByteCode.ARETURN);
                    break;

                  case Do_getParamCount:
                    // Push number of defined parameters
                    cfw.addPush(n.getParamCount());
                    cfw.add(ByteCode.IRETURN);
                    break;

                  case Do_getParamAndVarCount:
                    // Push number of defined parameters and declared variables
                    cfw.addPush(n.getParamAndVarCount());
                    cfw.add(ByteCode.IRETURN);
                    break;

                  case Do_getParamOrVarName:
                    // Push name of parameter using another switch
                    // over paramAndVarCount
                    int paramAndVarCount = n.getParamAndVarCount();
                    if (paramAndVarCount == 0) {
                        // The runtime should never call the method in this
                        // case but to make bytecode verifier happy return null
                        // as throwing execption takes more code
                        cfw.add(ByteCode.ACONST_NULL);
                        cfw.add(ByteCode.ARETURN);
                    } else if (paramAndVarCount == 1) {
                        // As above do not check for valid index but always
                        // return the name of the first param
                        cfw.addPush(n.getParamOrVarName(0));
                        cfw.add(ByteCode.ARETURN);
                    } else {
                        // Do switch over getParamOrVarName
                        cfw.addILoad(1); // param or var index
                        // do switch from 1 .. paramAndVarCount - 1 mapping 0
                        // to the default case
                        int paramSwitchStart = cfw.addTableSwitch(
                                                   1, paramAndVarCount - 1);
                        for (int j = 0; j != paramAndVarCount; ++j) {
                            if (cfw.getStackTop() != 0) Kit.codeBug();
                            String s = n.getParamOrVarName(j);
                            if (j == 0) {
                                cfw.markTableSwitchDefault(paramSwitchStart);
                            } else {
                                cfw.markTableSwitchCase(paramSwitchStart, j - 1,
                                                        0);
                            }
                            cfw.addPush(s);
                            cfw.add(ByteCode.ARETURN);
                        }
                    }
                    break;

                    case Do_getParamOrVarConst:
                        // Push name of parameter using another switch
                        // over paramAndVarCount
                        paramAndVarCount = n.getParamAndVarCount();
                        boolean [] constness = n.getParamAndVarConst();
                        if (paramAndVarCount == 0) {
                            // The runtime should never call the method in this
                            // case but to make bytecode verifier happy return null
                            // as throwing execption takes more code
                            cfw.add(ByteCode.ICONST_0);
                            cfw.add(ByteCode.IRETURN);
                        } else if (paramAndVarCount == 1) {
                            // As above do not check for valid index but always
                            // return the name of the first param
                            cfw.addPush(constness[0]);
                            cfw.add(ByteCode.IRETURN);
                        } else {
                            // Do switch over getParamOrVarName
                            cfw.addILoad(1); // param or var index
                            // do switch from 1 .. paramAndVarCount - 1 mapping 0
                            // to the default case
                            int paramSwitchStart = cfw.addTableSwitch(
                                                       1, paramAndVarCount - 1);
                            for (int j = 0; j != paramAndVarCount; ++j) {
                                if (cfw.getStackTop() != 0) Kit.codeBug();
                                if (j == 0) {
                                    cfw.markTableSwitchDefault(paramSwitchStart);
                                } else {
                                    cfw.markTableSwitchCase(paramSwitchStart, j - 1,
                                                            0);
                                }
                                cfw.addPush(constness[j]);
                                cfw.add(ByteCode.IRETURN);
                            }
                        }
                      break;

                  case Do_getEncodedSource:
                    // Push number encoded source start and end
                    // to prepare for encodedSource.substring(start, end)
                    cfw.addPush(n.getEncodedSourceStart());
                    cfw.addPush(n.getEncodedSourceEnd());
                    cfw.addInvoke(ByteCode.INVOKEVIRTUAL,
                                  "java/lang/String",
                                  "substring",
                                  "(II)Ljava/lang/String;");
                    cfw.add(ByteCode.ARETURN);
                    break;

                  default:
                    throw Kit.codeBug();
                }
            }

            cfw.stopMethod(methodLocals);
        }
    }

    private void emitRegExpInit(ClassFileWriter cfw)
    {
        // precompile all regexp literals

        int totalRegCount = 0;
        for (int i = 0; i != scriptOrFnNodes.length; ++i) {
            totalRegCount += scriptOrFnNodes[i].getRegexpCount();
        }
        if (totalRegCount == 0) {
            return;
        }

        cfw.startMethod(REGEXP_INIT_METHOD_NAME, REGEXP_INIT_METHOD_SIGNATURE,
                (short)(ACC_STATIC | ACC_PRIVATE));
        cfw.addField("_reInitDone", "Z",
                     (short)(ACC_STATIC | ACC_PRIVATE | ACC_VOLATILE));
        cfw.add(ByteCode.GETSTATIC, mainClassName, "_reInitDone", "Z");
        int doInit = cfw.acquireLabel();
        cfw.add(ByteCode.IFEQ, doInit);
        cfw.add(ByteCode.RETURN);
        cfw.markLabel(doInit);

        // get regexp proxy and store it in local slot 1
        cfw.addALoad(0); // context
        cfw.addInvoke(ByteCode.INVOKESTATIC,
                      "org/mozilla/javascript/ScriptRuntime",
                      "checkRegExpProxy",
                      "(Lorg/mozilla/javascript/Context;"
                      +")Lorg/mozilla/javascript/RegExpProxy;");
        cfw.addAStore(1); // proxy

        // We could apply double-checked locking here but concurrency
        // shouldn't be a problem in practice
        for (int i = 0; i != scriptOrFnNodes.length; ++i) {
            ScriptNode n = scriptOrFnNodes[i];
            int regCount = n.getRegexpCount();
            for (int j = 0; j != regCount; ++j) {
                String reFieldName = getCompiledRegexpName(n, j);
                String reFieldType = "Ljava/lang/Object;";
                String reString = n.getRegexpString(j);
                String reFlags = n.getRegexpFlags(j);
                cfw.addField(reFieldName, reFieldType,
                             (short)(ACC_STATIC | ACC_PRIVATE));
                cfw.addALoad(1); // proxy
                cfw.addALoad(0); // context
                cfw.addPush(reString);
                if (reFlags == null) {
                    cfw.add(ByteCode.ACONST_NULL);
                } else {
                    cfw.addPush(reFlags);
                }
                cfw.addInvoke(ByteCode.INVOKEINTERFACE,
                              "org/mozilla/javascript/RegExpProxy",
                              "compileRegExp",
                              "(Lorg/mozilla/javascript/Context;"
                              +"Ljava/lang/String;Ljava/lang/String;"
                              +")Ljava/lang/Object;");
                cfw.add(ByteCode.PUTSTATIC, mainClassName,
                        reFieldName, reFieldType);
            }
        }

        cfw.addPush(1);
        cfw.add(ByteCode.PUTSTATIC, mainClassName, "_reInitDone", "Z");
        cfw.add(ByteCode.RETURN);
        cfw.stopMethod((short)2);
    }

    private void emitConstantDudeInitializers(ClassFileWriter cfw)
    {
        int N = itsConstantListSize;
        if (N == 0)
            return;

        cfw.startMethod("<clinit>", "()V", (short)(ACC_STATIC | ACC_FINAL));

        double[] array = itsConstantList;
        for (int i = 0; i != N; ++i) {
            double num = array[i];
            String constantName = "_k" + i;
            String constantType = getStaticConstantWrapperType(num);
            cfw.addField(constantName, constantType,
                        (short)(ACC_STATIC | ACC_PRIVATE));
            int inum = (int)num;
            if (inum == num) {
                cfw.addPush(inum);
                cfw.addInvoke(ByteCode.INVOKESTATIC, "java/lang/Integer",
                              "valueOf", "(I)Ljava/lang/Integer;");
            } else {
                cfw.addPush(num);
                addDoubleWrap(cfw);
            }
            cfw.add(ByteCode.PUTSTATIC, mainClassName,
                    constantName, constantType);
        }

        cfw.add(ByteCode.RETURN);
        cfw.stopMethod((short)0);
    }

    void pushNumberAsObject(ClassFileWriter cfw, double num)
    {
        if (num == 0.0) {
            if (1 / num > 0) {
                // +0.0
                cfw.add(ByteCode.GETSTATIC,
                        "org/mozilla/javascript/optimizer/OptRuntime",
                        "zeroObj", "Ljava/lang/Double;");
            } else {
                cfw.addPush(num);
                addDoubleWrap(cfw);
            }

        } else if (num == 1.0) {
            cfw.add(ByteCode.GETSTATIC,
                    "org/mozilla/javascript/optimizer/OptRuntime",
                    "oneObj", "Ljava/lang/Double;");
            return;

        } else if (num == -1.0) {
            cfw.add(ByteCode.GETSTATIC,
                    "org/mozilla/javascript/optimizer/OptRuntime",
                    "minusOneObj", "Ljava/lang/Double;");

        } else if (Double.isNaN(num)) {
            cfw.add(ByteCode.GETSTATIC,
                    "org/mozilla/javascript/ScriptRuntime",
                    "NaNobj", "Ljava/lang/Double;");

        } else if (itsConstantListSize >= 2000) {
            // There appears to be a limit in the JVM on either the number
            // of static fields in a class or the size of the class
            // initializer. Either way, we can't have any more than 2000
            // statically init'd constants.
            cfw.addPush(num);
            addDoubleWrap(cfw);

        } else {
            int N = itsConstantListSize;
            int index = 0;
            if (N == 0) {
                itsConstantList = new double[64];
            } else {
                double[] array = itsConstantList;
                while (index != N && array[index] != num) {
                    ++index;
                }
                if (N == array.length) {
                    array = new double[N * 2];
                    System.arraycopy(itsConstantList, 0, array, 0, N);
                    itsConstantList = array;
                }
            }
            if (index == N) {
                itsConstantList[N] = num;
                itsConstantListSize = N + 1;
            }
            String constantName = "_k" + index;
            String constantType = getStaticConstantWrapperType(num);
            cfw.add(ByteCode.GETSTATIC, mainClassName,
                    constantName, constantType);
        }
    }

    private static void addDoubleWrap(ClassFileWriter cfw)
    {
        cfw.addInvoke(ByteCode.INVOKESTATIC,
                      "org/mozilla/javascript/optimizer/OptRuntime",
                      "wrapDouble", "(D)Ljava/lang/Double;");
    }

    private static String getStaticConstantWrapperType(double num)
    {
        int inum = (int)num;
        if (inum == num) {
            return "Ljava/lang/Integer;";
        } else {
            return "Ljava/lang/Double;";
        }
    }
    static void pushUndefined(ClassFileWriter cfw)
    {
        cfw.add(ByteCode.GETSTATIC, "org/mozilla/javascript/Undefined",
                "instance", "Ljava/lang/Object;");
    }

    int getIndex(ScriptNode n)
    {
        return scriptOrFnIndexes.getExisting(n);
    }

    String getDirectCtorName(ScriptNode n)
    {
        return "_n" + getIndex(n);
    }

    String getBodyMethodName(ScriptNode n)
    {
        return "_c_" + cleanName(n) + "_" + getIndex(n);
    }

    /**
     * Gets a Java-compatible "informative" name for the the ScriptOrFnNode
     */
    String cleanName(final ScriptNode n)
    {
      String result = "";
      if (n instanceof FunctionNode) {
        Name name = ((FunctionNode) n).getFunctionName();
        if (name == null) {
          result = "anonymous";
        } else {
          result = name.getIdentifier();
        }
      } else {
        result = "script";
      }
      return result;
    }

    String getBodyMethodSignature(ScriptNode n)
    {
        StringBuffer sb = new StringBuffer();
        sb.append('(');
        sb.append(mainClassSignature);
        sb.append("Lorg/mozilla/javascript/Context;"
                  +"Lorg/mozilla/javascript/Scriptable;"
                  +"Lorg/mozilla/javascript/Scriptable;");
        if (n.getType() == Token.FUNCTION) {
            OptFunctionNode ofn = OptFunctionNode.get(n);
            if (ofn.isTargetOfDirectCall()) {
                int pCount = ofn.fnode.getParamCount();
                for (int i = 0; i != pCount; i++) {
                    sb.append("Ljava/lang/Object;D");
                }
            }
        }
        sb.append("[Ljava/lang/Object;)Ljava/lang/Object;");
        return sb.toString();
    }

    String getFunctionInitMethodName(OptFunctionNode ofn)
    {
        return "_i"+getIndex(ofn.fnode);
    }

    String getCompiledRegexpName(ScriptNode n, int regexpIndex)
    {
        return "_re"+getIndex(n)+"_"+regexpIndex;
    }

    static RuntimeException badTree()
    {
        throw new RuntimeException("Bad tree in codegen");
    }

     public void setMainMethodClass(String className)
     {
         mainMethodClass = className;
     }

     static final String DEFAULT_MAIN_METHOD_CLASS
        = "org.mozilla.javascript.optimizer.OptRuntime";

    private static final String SUPER_CLASS_NAME
        = "org.mozilla.javascript.NativeFunction";

    static final String ID_FIELD_NAME = "_id";

    static final String REGEXP_INIT_METHOD_NAME = "_reInit";
    static final String REGEXP_INIT_METHOD_SIGNATURE
        =  "(Lorg/mozilla/javascript/Context;)V";

    static final String FUNCTION_INIT_SIGNATURE
        =  "(Lorg/mozilla/javascript/Context;"
           +"Lorg/mozilla/javascript/Scriptable;"
           +")V";

   static final String FUNCTION_CONSTRUCTOR_SIGNATURE
        = "(Lorg/mozilla/javascript/Scriptable;"
          +"Lorg/mozilla/javascript/Context;I)V";

    private static final Object globalLock = new Object();
    private static int globalSerialClassCounter;

    private CompilerEnvirons compilerEnv;

    private ObjArray directCallTargets;
    ScriptNode[] scriptOrFnNodes;
    private ObjToIntMap scriptOrFnIndexes;

    private String mainMethodClass = DEFAULT_MAIN_METHOD_CLASS;

    String mainClassName;
    String mainClassSignature;

    private double[] itsConstantList;
    private int itsConstantListSize;
}


class BodyCodegen
{
    void generateBodyCode()
    {
        isGenerator = Codegen.isGenerator(scriptOrFn);

        // generate the body of the current function or script object
        initBodyGeneration();

        if (isGenerator) {

            // All functions in the generated bytecode have a unique name. Every
            // generator has a unique prefix followed by _gen
            String type = "(" +
                          codegen.mainClassSignature +
                          "Lorg/mozilla/javascript/Context;" +
                          "Lorg/mozilla/javascript/Scriptable;" +
                          "Ljava/lang/Object;" +
                          "Ljava/lang/Object;I)Ljava/lang/Object;";
            cfw.startMethod(codegen.getBodyMethodName(scriptOrFn) + "_gen",
                    type,
                    (short)(ACC_STATIC | ACC_PRIVATE));
        } else {
            cfw.startMethod(codegen.getBodyMethodName(scriptOrFn),
                    codegen.getBodyMethodSignature(scriptOrFn),
                    (short)(ACC_STATIC | ACC_PRIVATE));
        }

        generatePrologue();
        Node treeTop;
        if (fnCurrent != null) {
            treeTop = scriptOrFn.getLastChild();
        } else {
            treeTop = scriptOrFn;
        }
        generateStatement(treeTop);
        generateEpilogue();

        cfw.stopMethod((short)(localsMax + 1));

        if (isGenerator) {
            // generate the user visible method which when invoked will
            // return a generator object
            generateGenerator();
        }

        if (literals != null) {
            // literals list may grow while we're looping
            for (int i = 0; i < literals.size(); i++) {
                Node node = literals.get(i);
                int type = node.getType();
                switch (type) {
                    case Token.OBJECTLIT:
                        generateObjectLiteralFactory(node, i + 1);
                        break;
                    case Token.ARRAYLIT:
                        generateArrayLiteralFactory(node, i + 1);
                        break;
                    default:
                        Kit.codeBug(Token.typeToName(type));
                }
            }
        }

    }

    // This creates a the user-facing function that returns a NativeGenerator
    // object.
    private void generateGenerator()
    {
        cfw.startMethod(codegen.getBodyMethodName(scriptOrFn),
                        codegen.getBodyMethodSignature(scriptOrFn),
                        (short)(ACC_STATIC | ACC_PRIVATE));

        initBodyGeneration();
        argsLocal = firstFreeLocal++;
        localsMax = firstFreeLocal;

        // get top level scope
        if (fnCurrent != null)
        {
            // Unless we're in a direct call use the enclosing scope
            // of the function as our variable object.
            cfw.addALoad(funObjLocal);
            cfw.addInvoke(ByteCode.INVOKEINTERFACE,
                          "org/mozilla/javascript/Scriptable",
                          "getParentScope",
                          "()Lorg/mozilla/javascript/Scriptable;");
            cfw.addAStore(variableObjectLocal);
        }

        // generators are forced to have an activation record
        cfw.addALoad(funObjLocal);
        cfw.addALoad(variableObjectLocal);
        cfw.addALoad(argsLocal);
        addScriptRuntimeInvoke("createFunctionActivation",
                               "(Lorg/mozilla/javascript/NativeFunction;"
                               +"Lorg/mozilla/javascript/Scriptable;"
                               +"[Ljava/lang/Object;"
                               +")Lorg/mozilla/javascript/Scriptable;");
        cfw.addAStore(variableObjectLocal);

        // create a function object
        cfw.add(ByteCode.NEW, codegen.mainClassName);
        // Call function constructor
        cfw.add(ByteCode.DUP);
        cfw.addALoad(variableObjectLocal);
        cfw.addALoad(contextLocal);           // load 'cx'
        cfw.addPush(scriptOrFnIndex);
        cfw.addInvoke(ByteCode.INVOKESPECIAL, codegen.mainClassName,
                      "<init>", Codegen.FUNCTION_CONSTRUCTOR_SIGNATURE);

        generateNestedFunctionInits();

        // create the NativeGenerator object that we return
        cfw.addALoad(variableObjectLocal);
        cfw.addALoad(thisObjLocal);
        cfw.addLoadConstant(maxLocals);
        cfw.addLoadConstant(maxStack);
        addOptRuntimeInvoke("createNativeGenerator",
                               "(Lorg/mozilla/javascript/NativeFunction;"
                               +"Lorg/mozilla/javascript/Scriptable;"
                               +"Lorg/mozilla/javascript/Scriptable;II"
                               +")Lorg/mozilla/javascript/Scriptable;");

        cfw.add(ByteCode.ARETURN);
        cfw.stopMethod((short)(localsMax + 1));
    }

    private void generateNestedFunctionInits()
    {
        int functionCount = scriptOrFn.getFunctionCount();
        for (int i = 0; i != functionCount; i++) {
            OptFunctionNode ofn = OptFunctionNode.get(scriptOrFn, i);
            if (ofn.fnode.getFunctionType()
                    == FunctionNode.FUNCTION_STATEMENT)
            {
                visitFunction(ofn, FunctionNode.FUNCTION_STATEMENT);
            }
        }
    }

    private void initBodyGeneration()
    {
        varRegisters = null;
        if (scriptOrFn.getType() == Token.FUNCTION) {
            fnCurrent = OptFunctionNode.get(scriptOrFn);
            hasVarsInRegs = !fnCurrent.fnode.requiresActivation();
            if (hasVarsInRegs) {
                int n = fnCurrent.fnode.getParamAndVarCount();
                if (n != 0) {
                    varRegisters = new short[n];
                }
            }
            inDirectCallFunction = fnCurrent.isTargetOfDirectCall();
            if (inDirectCallFunction && !hasVarsInRegs) Codegen.badTree();
        } else {
            fnCurrent = null;
            hasVarsInRegs = false;
            inDirectCallFunction = false;
        }

        locals = new int[MAX_LOCALS];

        funObjLocal = 0;
        contextLocal = 1;
        variableObjectLocal = 2;
        thisObjLocal = 3;
        localsMax = (short) 4;  // number of parms + "this"
        firstFreeLocal = 4;

        popvLocal = -1;
        argsLocal = -1;
        itsZeroArgArray = -1;
        itsOneArgArray = -1;
        epilogueLabel = -1;
        enterAreaStartLabel = -1;
        generatorStateLocal = -1;
    }

    /**
     * Generate the prologue for a function or script.
     */
    private void generatePrologue()
    {
        if (inDirectCallFunction) {
            int directParameterCount = scriptOrFn.getParamCount();
            // 0 is reserved for function Object 'this'
            // 1 is reserved for context
            // 2 is reserved for parentScope
            // 3 is reserved for script 'this'
            if (firstFreeLocal != 4) Kit.codeBug();
            for (int i = 0; i != directParameterCount; ++i) {
                varRegisters[i] = firstFreeLocal;
                // 3 is 1 for Object parm and 2 for double parm
                firstFreeLocal += 3;
            }
            if (!fnCurrent.getParameterNumberContext()) {
                // make sure that all parameters are objects
                itsForcedObjectParameters = true;
                for (int i = 0; i != directParameterCount; ++i) {
                    short reg = varRegisters[i];
                    cfw.addALoad(reg);
                    cfw.add(ByteCode.GETSTATIC,
                            "java/lang/Void",
                            "TYPE",
                            "Ljava/lang/Class;");
                    int isObjectLabel = cfw.acquireLabel();
                    cfw.add(ByteCode.IF_ACMPNE, isObjectLabel);
                    cfw.addDLoad(reg + 1);
                    addDoubleWrap();
                    cfw.addAStore(reg);
                    cfw.markLabel(isObjectLabel);
                }
            }
        }

        if (fnCurrent != null) {
            // Use the enclosing scope of the function as our variable object.
            cfw.addALoad(funObjLocal);
            cfw.addInvoke(ByteCode.INVOKEINTERFACE,
                          "org/mozilla/javascript/Scriptable",
                          "getParentScope",
                          "()Lorg/mozilla/javascript/Scriptable;");
            cfw.addAStore(variableObjectLocal);
        }

        // reserve 'args[]'
        argsLocal = firstFreeLocal++;
        localsMax = firstFreeLocal;

        // Generate Generator specific prelude
        if (isGenerator) {

            // reserve 'args[]'
            operationLocal = firstFreeLocal++;
            localsMax = firstFreeLocal;

            // Local 3 is a reference to a GeneratorState object. The rest
            // of codegen expects local 3 to be a reference to the thisObj.
            // So move the value in local 3 to generatorStateLocal, and load
            // the saved thisObj from the GeneratorState object.
            cfw.addALoad(thisObjLocal);
            generatorStateLocal = firstFreeLocal++;
            localsMax = firstFreeLocal;
            cfw.add(ByteCode.CHECKCAST, OptRuntime.GeneratorState.CLASS_NAME);
            cfw.add(ByteCode.DUP);
            cfw.addAStore(generatorStateLocal);
            cfw.add(ByteCode.GETFIELD,
                    OptRuntime.GeneratorState.CLASS_NAME,
                    OptRuntime.GeneratorState.thisObj_NAME,
                    OptRuntime.GeneratorState.thisObj_TYPE);
            cfw.addAStore(thisObjLocal);

            if (epilogueLabel == -1) {
                epilogueLabel = cfw.acquireLabel();
            }

            List<Node> targets = ((FunctionNode)scriptOrFn).getResumptionPoints();
            if (targets != null) {
                // get resumption point
                generateGetGeneratorResumptionPoint();

                // generate dispatch table
                generatorSwitch = cfw.addTableSwitch(0,
                    targets.size() + GENERATOR_START);
                generateCheckForThrowOrClose(-1, false, GENERATOR_START);
            }
        }

        // Compile RegExp literals if this is a script. For functions
        // this is performed during instantiation in functionInit
        if (fnCurrent == null && scriptOrFn.getRegexpCount() != 0) {
            cfw.addALoad(contextLocal);
            cfw.addInvoke(ByteCode.INVOKESTATIC, codegen.mainClassName,
                          Codegen.REGEXP_INIT_METHOD_NAME,
                          Codegen.REGEXP_INIT_METHOD_SIGNATURE);
        }

        if (compilerEnv.isGenerateObserverCount())
            saveCurrentCodeOffset();

        if (hasVarsInRegs) {
            // No need to create activation. Pad arguments if need be.
            int parmCount = scriptOrFn.getParamCount();
            if (parmCount > 0 && !inDirectCallFunction) {
                // Set up args array
                // check length of arguments, pad if need be
                cfw.addALoad(argsLocal);
                cfw.add(ByteCode.ARRAYLENGTH);
                cfw.addPush(parmCount);
                int label = cfw.acquireLabel();
                cfw.add(ByteCode.IF_ICMPGE, label);
                cfw.addALoad(argsLocal);
                cfw.addPush(parmCount);
                addScriptRuntimeInvoke("padArguments",
                                       "([Ljava/lang/Object;I"
                                       +")[Ljava/lang/Object;");
                cfw.addAStore(argsLocal);
                cfw.markLabel(label);
            }

            int paramCount = fnCurrent.fnode.getParamCount();
            int varCount = fnCurrent.fnode.getParamAndVarCount();
            boolean [] constDeclarations = fnCurrent.fnode.getParamAndVarConst();

            // REMIND - only need to initialize the vars that don't get a value
            // before the next call and are used in the function
            short firstUndefVar = -1;
            for (int i = 0; i != varCount; ++i) {
                short reg = -1;
                if (i < paramCount) {
                    if (!inDirectCallFunction) {
                        reg = getNewWordLocal();
                        cfw.addALoad(argsLocal);
                        cfw.addPush(i);
                        cfw.add(ByteCode.AALOAD);
                        cfw.addAStore(reg);
                    }
                } else if (fnCurrent.isNumberVar(i)) {
                    reg = getNewWordPairLocal(constDeclarations[i]);
                    cfw.addPush(0.0);
                    cfw.addDStore(reg);
                } else {
                    reg = getNewWordLocal(constDeclarations[i]);
                    if (firstUndefVar == -1) {
                        Codegen.pushUndefined(cfw);
                        firstUndefVar = reg;
                    } else {
                        cfw.addALoad(firstUndefVar);
                    }
                    cfw.addAStore(reg);
                }
                if (reg >= 0) {
                    if (constDeclarations[i]) {
                        cfw.addPush(0);
                        cfw.addIStore(reg + (fnCurrent.isNumberVar(i) ? 2 : 1));
                    }
                    varRegisters[i] = reg;
                }

                // Add debug table entry if we're generating debug info
                if (compilerEnv.isGenerateDebugInfo()) {
                    String name = fnCurrent.fnode.getParamOrVarName(i);
                    String type = fnCurrent.isNumberVar(i)
                                      ? "D" : "Ljava/lang/Object;";
                    int startPC = cfw.getCurrentCodeOffset();
                    if (reg < 0) {
                        reg = varRegisters[i];
                    }
                    cfw.addVariableDescriptor(name, type, startPC, reg);
                }
            }

            // Skip creating activation object.
            return;
        }

        // skip creating activation object for the body of a generator. The
        // activation record required by a generator has already been created
        // in generateGenerator().
        if (isGenerator)
            return;


        String debugVariableName;
        if (fnCurrent != null) {
            debugVariableName = "activation";
            cfw.addALoad(funObjLocal);
            cfw.addALoad(variableObjectLocal);
            cfw.addALoad(argsLocal);
            addScriptRuntimeInvoke("createFunctionActivation",
                                   "(Lorg/mozilla/javascript/NativeFunction;"
                                   +"Lorg/mozilla/javascript/Scriptable;"
                                   +"[Ljava/lang/Object;"
                                   +")Lorg/mozilla/javascript/Scriptable;");
            cfw.addAStore(variableObjectLocal);
            cfw.addALoad(contextLocal);
            cfw.addALoad(variableObjectLocal);
            addScriptRuntimeInvoke("enterActivationFunction",
                                   "(Lorg/mozilla/javascript/Context;"
                                   +"Lorg/mozilla/javascript/Scriptable;"
                                   +")V");
        } else {
            debugVariableName = "global";
            cfw.addALoad(funObjLocal);
            cfw.addALoad(thisObjLocal);
            cfw.addALoad(contextLocal);
            cfw.addALoad(variableObjectLocal);
            cfw.addPush(0); // false to indicate it is not eval script
            addScriptRuntimeInvoke("initScript",
                                   "(Lorg/mozilla/javascript/NativeFunction;"
                                   +"Lorg/mozilla/javascript/Scriptable;"
                                   +"Lorg/mozilla/javascript/Context;"
                                   +"Lorg/mozilla/javascript/Scriptable;"
                                   +"Z"
                                   +")V");
        }

        enterAreaStartLabel = cfw.acquireLabel();
        epilogueLabel = cfw.acquireLabel();
        cfw.markLabel(enterAreaStartLabel);

        generateNestedFunctionInits();

        // default is to generate debug info
        if (compilerEnv.isGenerateDebugInfo()) {
            cfw.addVariableDescriptor(debugVariableName,
                    "Lorg/mozilla/javascript/Scriptable;",
                    cfw.getCurrentCodeOffset(), variableObjectLocal);
        }

        if (fnCurrent == null) {
            // OPT: use dataflow to prove that this assignment is dead
            popvLocal = getNewWordLocal();
            Codegen.pushUndefined(cfw);
            cfw.addAStore(popvLocal);

            int linenum = scriptOrFn.getEndLineno();
            if (linenum != -1)
              cfw.addLineNumberEntry((short)linenum);

        } else {
            if (fnCurrent.itsContainsCalls0) {
                itsZeroArgArray = getNewWordLocal();
                cfw.add(ByteCode.GETSTATIC,
                        "org/mozilla/javascript/ScriptRuntime",
                        "emptyArgs", "[Ljava/lang/Object;");
                cfw.addAStore(itsZeroArgArray);
            }
            if (fnCurrent.itsContainsCalls1) {
                itsOneArgArray = getNewWordLocal();
                cfw.addPush(1);
                cfw.add(ByteCode.ANEWARRAY, "java/lang/Object");
                cfw.addAStore(itsOneArgArray);
            }
        }
    }

    private void generateGetGeneratorResumptionPoint()
    {
        cfw.addALoad(generatorStateLocal);
        cfw.add(ByteCode.GETFIELD,
                OptRuntime.GeneratorState.CLASS_NAME,
                OptRuntime.GeneratorState.resumptionPoint_NAME,
                OptRuntime.GeneratorState.resumptionPoint_TYPE);
    }

    private void generateSetGeneratorResumptionPoint(int nextState)
    {
        cfw.addALoad(generatorStateLocal);
        cfw.addLoadConstant(nextState);
        cfw.add(ByteCode.PUTFIELD,
                OptRuntime.GeneratorState.CLASS_NAME,
                OptRuntime.GeneratorState.resumptionPoint_NAME,
                OptRuntime.GeneratorState.resumptionPoint_TYPE);
    }

    private void generateGetGeneratorStackState()
    {
        cfw.addALoad(generatorStateLocal);
        addOptRuntimeInvoke("getGeneratorStackState",
                    "(Ljava/lang/Object;)[Ljava/lang/Object;");
    }

    private void generateEpilogue()
    {
        if (compilerEnv.isGenerateObserverCount())
            addInstructionCount();
        if (isGenerator) {
            // generate locals initialization
            Map<Node,int[]> liveLocals = ((FunctionNode)scriptOrFn).getLiveLocals();
            if (liveLocals != null) {
                List<Node> nodes = ((FunctionNode)scriptOrFn).getResumptionPoints();
                for (int i = 0; i < nodes.size(); i++) {
                    Node node = nodes.get(i);
                    int[] live = liveLocals.get(node);
                    if (live != null) {
                        cfw.markTableSwitchCase(generatorSwitch,
                            getNextGeneratorState(node));
                        generateGetGeneratorLocalsState();
                        for (int j = 0; j < live.length; j++) {
                                cfw.add(ByteCode.DUP);
                                cfw.addLoadConstant(j);
                                cfw.add(ByteCode.AALOAD);
                                cfw.addAStore(live[j]);
                        }
                        cfw.add(ByteCode.POP);
                        cfw.add(ByteCode.GOTO, getTargetLabel(node));
                    }
                }
            }

            // generate dispatch tables for finally
            if (finallys != null) {
                for (Node n: finallys.keySet()) {
                    if (n.getType() == Token.FINALLY) {
                        FinallyReturnPoint ret = finallys.get(n);
                        // the finally will jump here
                        cfw.markLabel(ret.tableLabel, (short)1);

                        // start generating a dispatch table
                        int startSwitch = cfw.addTableSwitch(0,
                                            ret.jsrPoints.size() - 1);
                        int c = 0;
                        cfw.markTableSwitchDefault(startSwitch);
                        for (int i = 0; i < ret.jsrPoints.size(); i++) {
                            // generate gotos back to the JSR location
                            cfw.markTableSwitchCase(startSwitch, c);
                            cfw.add(ByteCode.GOTO,
                                    ret.jsrPoints.get(i).intValue());
                            c++;
                        }
                    }
                }
            }
        }

        if (epilogueLabel != -1) {
            cfw.markLabel(epilogueLabel);
        }

        if (hasVarsInRegs) {
            cfw.add(ByteCode.ARETURN);
            return;
        } else if (isGenerator) {
            if (((FunctionNode)scriptOrFn).getResumptionPoints() != null) {
                cfw.markTableSwitchDefault(generatorSwitch);
            }

            // change state for re-entry
            generateSetGeneratorResumptionPoint(GENERATOR_TERMINATE);

            // throw StopIteration
            cfw.addALoad(variableObjectLocal);
            addOptRuntimeInvoke("throwStopIteration",
                    "(Ljava/lang/Object;)V");

            Codegen.pushUndefined(cfw);
            cfw.add(ByteCode.ARETURN);

        } else if (fnCurrent == null) {
            cfw.addALoad(popvLocal);
            cfw.add(ByteCode.ARETURN);
        } else {
            generateActivationExit();
            cfw.add(ByteCode.ARETURN);

            // Generate catch block to catch all and rethrow to call exit code
            // under exception propagation as well.

            int finallyHandler = cfw.acquireLabel();
            cfw.markHandler(finallyHandler);
            short exceptionObject = getNewWordLocal();
            cfw.addAStore(exceptionObject);

            // Duplicate generateActivationExit() in the catch block since it
            // takes less space then full-featured ByteCode.JSR/ByteCode.RET
            generateActivationExit();

            cfw.addALoad(exceptionObject);
            releaseWordLocal(exceptionObject);
            // rethrow
            cfw.add(ByteCode.ATHROW);

            // mark the handler
            cfw.addExceptionHandler(enterAreaStartLabel, epilogueLabel,
                                    finallyHandler, null); // catch any
        }
    }

    private void generateGetGeneratorLocalsState() {
        cfw.addALoad(generatorStateLocal);
        addOptRuntimeInvoke("getGeneratorLocalsState",
                                "(Ljava/lang/Object;)[Ljava/lang/Object;");
    }

    private void generateActivationExit()
    {
        if (fnCurrent == null || hasVarsInRegs) throw Kit.codeBug();
        cfw.addALoad(contextLocal);
        addScriptRuntimeInvoke("exitActivationFunction",
                               "(Lorg/mozilla/javascript/Context;)V");
    }

    private void generateStatement(Node node)
    {
        updateLineNumber(node);
        int type = node.getType();
        Node child = node.getFirstChild();
        switch (type) {
              case Token.LOOP:
              case Token.LABEL:
              case Token.WITH:
              case Token.SCRIPT:
              case Token.BLOCK:
              case Token.EMPTY:
                // no-ops.
                if (compilerEnv.isGenerateObserverCount()) {
                    // Need to add instruction count even for no-ops to catch
                    // cases like while (1) {}
                    addInstructionCount(1);
                }
                while (child != null) {
                    generateStatement(child);
                    child = child.getNext();
                }
                break;

              case Token.LOCAL_BLOCK: {
                boolean prevLocal = inLocalBlock;
                inLocalBlock = true;
                int local = getNewWordLocal();
                if (isGenerator) {
                    cfw.add(ByteCode.ACONST_NULL);
                    cfw.addAStore(local);
                }
                node.putIntProp(Node.LOCAL_PROP, local);
                while (child != null) {
                    generateStatement(child);
                    child = child.getNext();
                }
                releaseWordLocal((short)local);
                node.removeProp(Node.LOCAL_PROP);
                inLocalBlock = prevLocal;
                break;
              }

              case Token.FUNCTION: {
                int fnIndex = node.getExistingIntProp(Node.FUNCTION_PROP);
                OptFunctionNode ofn = OptFunctionNode.get(scriptOrFn, fnIndex);
                int t = ofn.fnode.getFunctionType();
                if (t == FunctionNode.FUNCTION_EXPRESSION_STATEMENT) {
                    visitFunction(ofn, t);
                } else {
                    if (t != FunctionNode.FUNCTION_STATEMENT) {
                        throw Codegen.badTree();
                    }
                }
                break;
              }

              case Token.TRY:
                visitTryCatchFinally((Jump)node, child);
                break;

              case Token.CATCH_SCOPE:
                {
                    // nothing stays on the stack on entry into a catch scope
                    cfw.setStackTop((short) 0);

                    int local = getLocalBlockRegister(node);
                    int scopeIndex
                        = node.getExistingIntProp(Node.CATCH_SCOPE_PROP);

                    String name = child.getString(); // name of exception
                    child = child.getNext();
                    generateExpression(child, node); // load expression object
                    if (scopeIndex == 0) {
                        cfw.add(ByteCode.ACONST_NULL);
                    } else {
                        // Load previous catch scope object
                        cfw.addALoad(local);
                    }
                    cfw.addPush(name);
                    cfw.addALoad(contextLocal);
                    cfw.addALoad(variableObjectLocal);

                    addScriptRuntimeInvoke(
                        "newCatchScope",
                        "(Ljava/lang/Throwable;"
                        +"Lorg/mozilla/javascript/Scriptable;"
                        +"Ljava/lang/String;"
                        +"Lorg/mozilla/javascript/Context;"
                        +"Lorg/mozilla/javascript/Scriptable;"
                        +")Lorg/mozilla/javascript/Scriptable;");
                    cfw.addAStore(local);
                }
                break;

              case Token.THROW:
                generateExpression(child, node);
                if (compilerEnv.isGenerateObserverCount())
                    addInstructionCount();
                generateThrowJavaScriptException();
                break;

              case Token.RETHROW:
                if (compilerEnv.isGenerateObserverCount())
                    addInstructionCount();
                cfw.addALoad(getLocalBlockRegister(node));
                cfw.add(ByteCode.ATHROW);
                break;

              case Token.RETURN_RESULT:
              case Token.RETURN:
                if (!isGenerator) {
                    if (child != null) {
                        generateExpression(child, node);
                    } else if (type == Token.RETURN) {
                        Codegen.pushUndefined(cfw);
                    } else {
                        if (popvLocal < 0) throw Codegen.badTree();
                        cfw.addALoad(popvLocal);
                    }
                }
                if (compilerEnv.isGenerateObserverCount())
                    addInstructionCount();
                if (epilogueLabel == -1) {
                    if (!hasVarsInRegs) throw Codegen.badTree();
                    epilogueLabel = cfw.acquireLabel();
                }
                cfw.add(ByteCode.GOTO, epilogueLabel);
                break;

              case Token.SWITCH:
                if (compilerEnv.isGenerateObserverCount())
                    addInstructionCount();
                visitSwitch((Jump)node, child);
                break;

              case Token.ENTERWITH:
                generateExpression(child, node);
                cfw.addALoad(contextLocal);
                cfw.addALoad(variableObjectLocal);
                addScriptRuntimeInvoke(
                    "enterWith",
                    "(Ljava/lang/Object;"
                    +"Lorg/mozilla/javascript/Context;"
                    +"Lorg/mozilla/javascript/Scriptable;"
                    +")Lorg/mozilla/javascript/Scriptable;");
                cfw.addAStore(variableObjectLocal);
                incReferenceWordLocal(variableObjectLocal);
                break;

              case Token.LEAVEWITH:
                cfw.addALoad(variableObjectLocal);
                addScriptRuntimeInvoke(
                    "leaveWith",
                    "(Lorg/mozilla/javascript/Scriptable;"
                    +")Lorg/mozilla/javascript/Scriptable;");
                cfw.addAStore(variableObjectLocal);
                decReferenceWordLocal(variableObjectLocal);
                break;

              case Token.ENUM_INIT_KEYS:
              case Token.ENUM_INIT_VALUES:
              case Token.ENUM_INIT_ARRAY:
                generateExpression(child, node);
                cfw.addALoad(contextLocal);
                int enumType = type == Token.ENUM_INIT_KEYS
                                   ? ScriptRuntime.ENUMERATE_KEYS :
                               type == Token.ENUM_INIT_VALUES
                                   ? ScriptRuntime.ENUMERATE_VALUES :
                               ScriptRuntime.ENUMERATE_ARRAY;
                cfw.addPush(enumType);
                addScriptRuntimeInvoke("enumInit",
                                       "(Ljava/lang/Object;"
                                       +"Lorg/mozilla/javascript/Context;"
                                       +"I"
                                       +")Ljava/lang/Object;");
                cfw.addAStore(getLocalBlockRegister(node));
                break;

              case Token.EXPR_VOID:
                if (child.getType() == Token.SETVAR) {
                    /* special case this so as to avoid unnecessary
                    load's & pop's */
                    visitSetVar(child, child.getFirstChild(), false);
                }
                else if (child.getType() == Token.SETCONSTVAR) {
                    /* special case this so as to avoid unnecessary
                    load's & pop's */
                    visitSetConstVar(child, child.getFirstChild(), false);
                }
                else if (child.getType() == Token.YIELD) {
                    generateYieldPoint(child, false);
                }
                else {
                    generateExpression(child, node);
                    if (node.getIntProp(Node.ISNUMBER_PROP, -1) != -1)
                        cfw.add(ByteCode.POP2);
                    else
                        cfw.add(ByteCode.POP);
                }
                break;

              case Token.EXPR_RESULT:
                generateExpression(child, node);
                if (popvLocal < 0) {
                    popvLocal = getNewWordLocal();
                }
                cfw.addAStore(popvLocal);
                break;

              case Token.TARGET:
                {
                    if (compilerEnv.isGenerateObserverCount())
                        addInstructionCount();
                    int label = getTargetLabel(node);
                    cfw.markLabel(label);
                    if (compilerEnv.isGenerateObserverCount())
                        saveCurrentCodeOffset();
                }
                break;

              case Token.JSR:
              case Token.GOTO:
              case Token.IFEQ:
              case Token.IFNE:
                if (compilerEnv.isGenerateObserverCount())
                    addInstructionCount();
                visitGoto((Jump)node, type, child);
                break;

              case Token.FINALLY:
                {
                    // This is the non-exception case for a finally block. In
                    // other words, since we inline finally blocks wherever
                    // jsr was previously used, and jsr is only used when the
                    // function is not a generator, we don't need to generate
                    // this case if the function isn't a generator.
                    if (!isGenerator) {
                        break;
                    }

                    if (compilerEnv.isGenerateObserverCount())
                        saveCurrentCodeOffset();
                    // there is exactly one value on the stack when enterring
                    // finally blocks: the return address (or its int encoding)
                    cfw.setStackTop((short)1);

                    // Save return address in a new local
                    int finallyRegister = getNewWordLocal();

                    int finallyStart = cfw.acquireLabel();
                    int finallyEnd = cfw.acquireLabel();
                    cfw.markLabel(finallyStart);

                    generateIntegerWrap();
                    cfw.addAStore(finallyRegister);

                    while (child != null) {
                        generateStatement(child);
                        child = child.getNext();
                    }

                    cfw.addALoad(finallyRegister);
                    cfw.add(ByteCode.CHECKCAST, "java/lang/Integer");
                    generateIntegerUnwrap();
                    FinallyReturnPoint ret = finallys.get(node);
                    ret.tableLabel = cfw.acquireLabel();
                    cfw.add(ByteCode.GOTO, ret.tableLabel);

                    releaseWordLocal((short)finallyRegister);
                    cfw.markLabel(finallyEnd);
                }
                break;

              case Token.DEBUGGER:
                break;

              default:
                throw Codegen.badTree();
        }

    }

    private void generateIntegerWrap()
    {
        cfw.addInvoke(ByteCode.INVOKESTATIC, "java/lang/Integer", "valueOf",
                "(I)Ljava/lang/Integer;");
    }


    private void generateIntegerUnwrap()
    {
        cfw.addInvoke(ByteCode.INVOKEVIRTUAL, "java/lang/Integer",
                "intValue", "()I");
    }


    private void generateThrowJavaScriptException()
    {
        cfw.add(ByteCode.NEW,
                        "org/mozilla/javascript/JavaScriptException");
        cfw.add(ByteCode.DUP_X1);
        cfw.add(ByteCode.SWAP);
        cfw.addPush(scriptOrFn.getSourceName());
        cfw.addPush(itsLineNumber);
        cfw.addInvoke(
                    ByteCode.INVOKESPECIAL,
                    "org/mozilla/javascript/JavaScriptException",
                    "<init>",
                    "(Ljava/lang/Object;Ljava/lang/String;I)V");
        cfw.add(ByteCode.ATHROW);
    }

    private int getNextGeneratorState(Node node)
    {
        int nodeIndex = ((FunctionNode)scriptOrFn).getResumptionPoints()
                .indexOf(node);
        return nodeIndex + GENERATOR_YIELD_START;
    }

    private void generateExpression(Node node, Node parent)
    {
        int type = node.getType();
        Node child = node.getFirstChild();
        switch (type) {
              case Token.USE_STACK:
                break;

              case Token.FUNCTION:
                if (fnCurrent != null || parent.getType() != Token.SCRIPT) {
                    int fnIndex = node.getExistingIntProp(Node.FUNCTION_PROP);
                    OptFunctionNode ofn = OptFunctionNode.get(scriptOrFn,
                                                             fnIndex);
                    int t = ofn.fnode.getFunctionType();
                    if (t != FunctionNode.FUNCTION_EXPRESSION) {
                        throw Codegen.badTree();
                    }
                    visitFunction(ofn, t);
                }
                break;

              case Token.NAME:
                {
                    cfw.addALoad(contextLocal);
                    cfw.addALoad(variableObjectLocal);
                    cfw.addPush(node.getString());
                    addScriptRuntimeInvoke(
                        "name",
                        "(Lorg/mozilla/javascript/Context;"
                        +"Lorg/mozilla/javascript/Scriptable;"
                        +"Ljava/lang/String;"
                        +")Ljava/lang/Object;");
                }
                break;

              case Token.CALL:
              case Token.NEW:
                {
                    int specialType = node.getIntProp(Node.SPECIALCALL_PROP,
                                                      Node.NON_SPECIALCALL);
                    if (specialType == Node.NON_SPECIALCALL) {
                        OptFunctionNode target;
                        target = (OptFunctionNode)node.getProp(
                                     Node.DIRECTCALL_PROP);

                        if (target != null) {
                            visitOptimizedCall(node, target, type, child);
                        } else if (type == Token.CALL) {
                            visitStandardCall(node, child);
                        } else {
                            visitStandardNew(node, child);
                        }
                    } else {
                        visitSpecialCall(node, type, specialType, child);
                    }
                }
                break;

              case Token.REF_CALL:
                generateFunctionAndThisObj(child, node);
                // stack: ... functionObj thisObj
                child = child.getNext();
                generateCallArgArray(node, child, false);
                cfw.addALoad(contextLocal);
                addScriptRuntimeInvoke(
                    "callRef",
                    "(Lorg/mozilla/javascript/Callable;"
                    +"Lorg/mozilla/javascript/Scriptable;"
                    +"[Ljava/lang/Object;"
                    +"Lorg/mozilla/javascript/Context;"
                    +")Lorg/mozilla/javascript/Ref;");
                break;

              case Token.NUMBER:
                {
                    double num = node.getDouble();
                    if (node.getIntProp(Node.ISNUMBER_PROP, -1) != -1) {
                        cfw.addPush(num);
                    } else {
                        codegen.pushNumberAsObject(cfw, num);
                    }
                }
                break;

              case Token.STRING:
                cfw.addPush(node.getString());
                break;

              case Token.THIS:
                cfw.addALoad(thisObjLocal);
                break;

              case Token.THISFN:
                cfw.add(ByteCode.ALOAD_0);
                break;

              case Token.NULL:
                cfw.add(ByteCode.ACONST_NULL);
                break;

              case Token.TRUE:
                cfw.add(ByteCode.GETSTATIC, "java/lang/Boolean",
                        "TRUE", "Ljava/lang/Boolean;");
                break;

              case Token.FALSE:
                cfw.add(ByteCode.GETSTATIC, "java/lang/Boolean",
                        "FALSE", "Ljava/lang/Boolean;");
                break;

              case Token.REGEXP:
                {
                    // Create a new wrapper around precompiled regexp
                    cfw.addALoad(contextLocal);
                    cfw.addALoad(variableObjectLocal);
                    int i = node.getExistingIntProp(Node.REGEXP_PROP);
                    cfw.add(ByteCode.GETSTATIC, codegen.mainClassName,
                            codegen.getCompiledRegexpName(scriptOrFn, i),
                            "Ljava/lang/Object;");
                    cfw.addInvoke(ByteCode.INVOKESTATIC,
                                  "org/mozilla/javascript/ScriptRuntime",
                                  "wrapRegExp",
                                  "(Lorg/mozilla/javascript/Context;"
                                  +"Lorg/mozilla/javascript/Scriptable;"
                                  +"Ljava/lang/Object;"
                                  +")Lorg/mozilla/javascript/Scriptable;");
                }
                break;

              case Token.COMMA: {
                Node next = child.getNext();
                while (next != null) {
                    generateExpression(child, node);
                    cfw.add(ByteCode.POP);
                    child = next;
                    next = next.getNext();
                }
                generateExpression(child, node);
                break;
              }

              case Token.ENUM_NEXT:
              case Token.ENUM_ID: {
                int local = getLocalBlockRegister(node);
                cfw.addALoad(local);
                if (type == Token.ENUM_NEXT) {
                    addScriptRuntimeInvoke(
                        "enumNext", "(Ljava/lang/Object;)Ljava/lang/Boolean;");
                } else {
                    cfw.addALoad(contextLocal);
                    addScriptRuntimeInvoke("enumId",
                                           "(Ljava/lang/Object;"
                                           +"Lorg/mozilla/javascript/Context;"
                                           +")Ljava/lang/Object;");
                }
                break;
              }

              case Token.ARRAYLIT:
                visitArrayLiteral(node, child, false);
                break;

              case Token.OBJECTLIT:
                visitObjectLiteral(node, child, false);
                break;

              case Token.NOT: {
                int trueTarget = cfw.acquireLabel();
                int falseTarget = cfw.acquireLabel();
                int beyond = cfw.acquireLabel();
                generateIfJump(child, node, trueTarget, falseTarget);

                cfw.markLabel(trueTarget);
                cfw.add(ByteCode.GETSTATIC, "java/lang/Boolean",
                                        "FALSE", "Ljava/lang/Boolean;");
                cfw.add(ByteCode.GOTO, beyond);
                cfw.markLabel(falseTarget);
                cfw.add(ByteCode.GETSTATIC, "java/lang/Boolean",
                                        "TRUE", "Ljava/lang/Boolean;");
                cfw.markLabel(beyond);
                cfw.adjustStackTop(-1);
                break;
              }

              case Token.BITNOT:
                generateExpression(child, node);
                addScriptRuntimeInvoke("toInt32", "(Ljava/lang/Object;)I");
                cfw.addPush(-1);         // implement ~a as (a ^ -1)
                cfw.add(ByteCode.IXOR);
                cfw.add(ByteCode.I2D);
                addDoubleWrap();
                break;

              case Token.VOID:
                generateExpression(child, node);
                cfw.add(ByteCode.POP);
                Codegen.pushUndefined(cfw);
                break;

              case Token.TYPEOF:
                generateExpression(child, node);
                addScriptRuntimeInvoke("typeof",
                                       "(Ljava/lang/Object;"
                                       +")Ljava/lang/String;");
                break;

              case Token.TYPEOFNAME:
                visitTypeofname(node);
                break;

              case Token.INC:
              case Token.DEC:
                visitIncDec(node);
                break;

              case Token.OR:
              case Token.AND: {
                    generateExpression(child, node);
                    cfw.add(ByteCode.DUP);
                    addScriptRuntimeInvoke("toBoolean",
                                           "(Ljava/lang/Object;)Z");
                    int falseTarget = cfw.acquireLabel();
                    if (type == Token.AND)
                        cfw.add(ByteCode.IFEQ, falseTarget);
                    else
                        cfw.add(ByteCode.IFNE, falseTarget);
                    cfw.add(ByteCode.POP);
                    generateExpression(child.getNext(), node);
                    cfw.markLabel(falseTarget);
                }
                break;

              case Token.HOOK : {
                    Node ifThen = child.getNext();
                    Node ifElse = ifThen.getNext();
                    generateExpression(child, node);
                    addScriptRuntimeInvoke("toBoolean",
                                           "(Ljava/lang/Object;)Z");
                    int elseTarget = cfw.acquireLabel();
                    cfw.add(ByteCode.IFEQ, elseTarget);
                    short stack = cfw.getStackTop();
                    generateExpression(ifThen, node);
                    int afterHook = cfw.acquireLabel();
                    cfw.add(ByteCode.GOTO, afterHook);
                    cfw.markLabel(elseTarget, stack);
                    generateExpression(ifElse, node);
                    cfw.markLabel(afterHook);
                }
                break;

              case Token.ADD: {
                    generateExpression(child, node);
                    generateExpression(child.getNext(), node);
                    switch (node.getIntProp(Node.ISNUMBER_PROP, -1)) {
                      case Node.BOTH:
                        cfw.add(ByteCode.DADD);
                        break;
                      case Node.LEFT:
                        addOptRuntimeInvoke("add",
                            "(DLjava/lang/Object;)Ljava/lang/Object;");
                        break;
                      case Node.RIGHT:
                        addOptRuntimeInvoke("add",
                            "(Ljava/lang/Object;D)Ljava/lang/Object;");
                        break;
                      default:
                        if (child.getType() == Token.STRING) {
                            addScriptRuntimeInvoke("add",
                                "(Ljava/lang/CharSequence;"
                                +"Ljava/lang/Object;"
                                +")Ljava/lang/CharSequence;");
                        } else if (child.getNext().getType() == Token.STRING) {
                            addScriptRuntimeInvoke("add",
                                "(Ljava/lang/Object;"
                                +"Ljava/lang/CharSequence;"
                                +")Ljava/lang/CharSequence;");
                        } else {
                            cfw.addALoad(contextLocal);
                            addScriptRuntimeInvoke("add",
                                "(Ljava/lang/Object;"
                                +"Ljava/lang/Object;"
                                +"Lorg/mozilla/javascript/Context;"
                                +")Ljava/lang/Object;");
                        }
                    }
                }
                break;

              case Token.MUL:
                visitArithmetic(node, ByteCode.DMUL, child, parent);
                break;

              case Token.SUB:
                visitArithmetic(node, ByteCode.DSUB, child, parent);
                break;

              case Token.DIV:
              case Token.MOD:
                visitArithmetic(node, type == Token.DIV
                                      ? ByteCode.DDIV
                                      : ByteCode.DREM, child, parent);
                break;

              case Token.BITOR:
              case Token.BITXOR:
              case Token.BITAND:
              case Token.LSH:
              case Token.RSH:
              case Token.URSH:
                visitBitOp(node, type, child);
                break;

              case Token.POS:
              case Token.NEG:
                generateExpression(child, node);
                addObjectToDouble();
                if (type == Token.NEG) {
                    cfw.add(ByteCode.DNEG);
                }
                addDoubleWrap();
                break;

              case Token.TO_DOUBLE:
                // cnvt to double (not Double)
                generateExpression(child, node);
                addObjectToDouble();
                break;

              case Token.TO_OBJECT: {
                // convert from double
                int prop = -1;
                if (child.getType() == Token.NUMBER) {
                    prop = child.getIntProp(Node.ISNUMBER_PROP, -1);
                }
                if (prop != -1) {
                    child.removeProp(Node.ISNUMBER_PROP);
                    generateExpression(child, node);
                    child.putIntProp(Node.ISNUMBER_PROP, prop);
                } else {
                    generateExpression(child, node);
                    addDoubleWrap();
                }
                break;
              }

              case Token.IN:
              case Token.INSTANCEOF:
              case Token.LE:
              case Token.LT:
              case Token.GE:
              case Token.GT: {
                int trueGOTO = cfw.acquireLabel();
                int falseGOTO = cfw.acquireLabel();
                visitIfJumpRelOp(node, child, trueGOTO, falseGOTO);
                addJumpedBooleanWrap(trueGOTO, falseGOTO);
                break;
              }

              case Token.EQ:
              case Token.NE:
              case Token.SHEQ:
              case Token.SHNE: {
                int trueGOTO = cfw.acquireLabel();
                int falseGOTO = cfw.acquireLabel();
                visitIfJumpEqOp(node, child, trueGOTO, falseGOTO);
                addJumpedBooleanWrap(trueGOTO, falseGOTO);
                break;
              }

              case Token.GETPROP:
              case Token.GETPROPNOWARN:
                visitGetProp(node, child);
                break;

              case Token.GETELEM:
                generateExpression(child, node); // object
                generateExpression(child.getNext(), node);  // id
                cfw.addALoad(contextLocal);
                if (node.getIntProp(Node.ISNUMBER_PROP, -1) != -1) {
                    addScriptRuntimeInvoke(
                        "getObjectIndex",
                        "(Ljava/lang/Object;D"
                        +"Lorg/mozilla/javascript/Context;"
                        +")Ljava/lang/Object;");
                }
                else {
                    cfw.addALoad(variableObjectLocal);
                	addScriptRuntimeInvoke(
                        "getObjectElem",
                        "(Ljava/lang/Object;"
                        +"Ljava/lang/Object;"
                        +"Lorg/mozilla/javascript/Context;"
                        +"Lorg/mozilla/javascript/Scriptable;"
                        +")Ljava/lang/Object;");
                }
                break;

              case Token.GET_REF:
                generateExpression(child, node); // reference
                cfw.addALoad(contextLocal);
                addScriptRuntimeInvoke(
                    "refGet",
                    "(Lorg/mozilla/javascript/Ref;"
                    +"Lorg/mozilla/javascript/Context;"
                    +")Ljava/lang/Object;");
                break;

              case Token.GETVAR:
                visitGetVar(node);
                break;

              case Token.SETVAR:
                visitSetVar(node, child, true);
                break;

              case Token.SETNAME:
                visitSetName(node, child);
                break;

              case Token.STRICT_SETNAME:
                  visitStrictSetName(node, child);
                  break;

              case Token.SETCONST:
                visitSetConst(node, child);
                break;

              case Token.SETCONSTVAR:
                visitSetConstVar(node, child, true);
                break;

              case Token.SETPROP:
              case Token.SETPROP_OP:
                visitSetProp(type, node, child);
                break;

              case Token.SETELEM:
              case Token.SETELEM_OP:
                visitSetElem(type, node, child);
                break;

              case Token.SET_REF:
              case Token.SET_REF_OP:
                {
                    generateExpression(child, node);
                    child = child.getNext();
                    if (type == Token.SET_REF_OP) {
                        cfw.add(ByteCode.DUP);
                        cfw.addALoad(contextLocal);
                        addScriptRuntimeInvoke(
                            "refGet",
                            "(Lorg/mozilla/javascript/Ref;"
                            +"Lorg/mozilla/javascript/Context;"
                            +")Ljava/lang/Object;");
                    }
                    generateExpression(child, node);
                    cfw.addALoad(contextLocal);
                    addScriptRuntimeInvoke(
                        "refSet",
                        "(Lorg/mozilla/javascript/Ref;"
                        +"Ljava/lang/Object;"
                        +"Lorg/mozilla/javascript/Context;"
                        +")Ljava/lang/Object;");
                }
                break;

              case Token.DEL_REF:
                generateExpression(child, node);
                cfw.addALoad(contextLocal);
                addScriptRuntimeInvoke("refDel",
                                       "(Lorg/mozilla/javascript/Ref;"
                                       +"Lorg/mozilla/javascript/Context;"
                                       +")Ljava/lang/Object;");
                break;

              case Token.DELPROP:
                boolean isName = child.getType() == Token.BINDNAME;
                generateExpression(child, node);
                child = child.getNext();
                generateExpression(child, node);
                cfw.addALoad(contextLocal);
                cfw.addPush(isName);
                addScriptRuntimeInvoke("delete",
                                       "(Ljava/lang/Object;"
                                       +"Ljava/lang/Object;"
                                       +"Lorg/mozilla/javascript/Context;"
                                       +"Z)Ljava/lang/Object;");
                break;

              case Token.BINDNAME:
                {
                    while (child != null) {
                        generateExpression(child, node);
                        child = child.getNext();
                    }
                    // Generate code for "ScriptRuntime.bind(varObj, "s")"
                    cfw.addALoad(contextLocal);
                    cfw.addALoad(variableObjectLocal);
                    cfw.addPush(node.getString());
                    addScriptRuntimeInvoke(
                        "bind",
                        "(Lorg/mozilla/javascript/Context;"
                        +"Lorg/mozilla/javascript/Scriptable;"
                        +"Ljava/lang/String;"
                        +")Lorg/mozilla/javascript/Scriptable;");
                }
                break;

              case Token.LOCAL_LOAD:
                cfw.addALoad(getLocalBlockRegister(node));
                break;

              case Token.REF_SPECIAL:
                {
                    String special = (String)node.getProp(Node.NAME_PROP);
                    generateExpression(child, node);
                    cfw.addPush(special);
                    cfw.addALoad(contextLocal);
                    addScriptRuntimeInvoke(
                        "specialRef",
                        "(Ljava/lang/Object;"
                        +"Ljava/lang/String;"
                        +"Lorg/mozilla/javascript/Context;"
                        +")Lorg/mozilla/javascript/Ref;");
                }
                break;

              case Token.REF_MEMBER:
              case Token.REF_NS_MEMBER:
              case Token.REF_NAME:
              case Token.REF_NS_NAME:
                {
                    int memberTypeFlags
                        = node.getIntProp(Node.MEMBER_TYPE_PROP, 0);
                    // generate possible target, possible namespace and member
                    do {
                        generateExpression(child, node);
                        child = child.getNext();
                    } while (child != null);
                    cfw.addALoad(contextLocal);
                    String methodName, signature;
                    switch (type) {
                      case Token.REF_MEMBER:
                        methodName = "memberRef";
                        signature = "(Ljava/lang/Object;"
                                    +"Ljava/lang/Object;"
                                    +"Lorg/mozilla/javascript/Context;"
                                    +"I"
                                    +")Lorg/mozilla/javascript/Ref;";
                        break;
                      case Token.REF_NS_MEMBER:
                        methodName = "memberRef";
                        signature = "(Ljava/lang/Object;"
                                    +"Ljava/lang/Object;"
                                    +"Ljava/lang/Object;"
                                    +"Lorg/mozilla/javascript/Context;"
                                    +"I"
                                    +")Lorg/mozilla/javascript/Ref;";
                        break;
                      case Token.REF_NAME:
                        methodName = "nameRef";
                        signature = "(Ljava/lang/Object;"
                                    +"Lorg/mozilla/javascript/Context;"
                                    +"Lorg/mozilla/javascript/Scriptable;"
                                    +"I"
                                    +")Lorg/mozilla/javascript/Ref;";
                        cfw.addALoad(variableObjectLocal);
                        break;
                      case Token.REF_NS_NAME:
                        methodName = "nameRef";
                        signature = "(Ljava/lang/Object;"
                                    +"Ljava/lang/Object;"
                                    +"Lorg/mozilla/javascript/Context;"
                                    +"Lorg/mozilla/javascript/Scriptable;"
                                    +"I"
                                    +")Lorg/mozilla/javascript/Ref;";
                        cfw.addALoad(variableObjectLocal);
                        break;
                      default:
                        throw Kit.codeBug();
                    }
                    cfw.addPush(memberTypeFlags);
                    addScriptRuntimeInvoke(methodName, signature);
                }
                break;

              case Token.DOTQUERY:
                visitDotQuery(node, child);
                break;

              case Token.ESCXMLATTR:
                generateExpression(child, node);
                cfw.addALoad(contextLocal);
                addScriptRuntimeInvoke("escapeAttributeValue",
                                       "(Ljava/lang/Object;"
                                       +"Lorg/mozilla/javascript/Context;"
                                       +")Ljava/lang/String;");
                break;

              case Token.ESCXMLTEXT:
                generateExpression(child, node);
                cfw.addALoad(contextLocal);
                addScriptRuntimeInvoke("escapeTextValue",
                                       "(Ljava/lang/Object;"
                                       +"Lorg/mozilla/javascript/Context;"
                                       +")Ljava/lang/String;");
                break;

              case Token.DEFAULTNAMESPACE:
                generateExpression(child, node);
                cfw.addALoad(contextLocal);
                addScriptRuntimeInvoke("setDefaultNamespace",
                                       "(Ljava/lang/Object;"
                                       +"Lorg/mozilla/javascript/Context;"
                                       +")Ljava/lang/Object;");
                break;

              case Token.YIELD:
                generateYieldPoint(node, true);
                break;

              case Token.WITHEXPR: {
                Node enterWith = child;
                Node with = enterWith.getNext();
                Node leaveWith = with.getNext();
                generateStatement(enterWith);
                generateExpression(with.getFirstChild(), with);
                generateStatement(leaveWith);
                break;
              }

              case Token.ARRAYCOMP: {
                Node initStmt = child;
                Node expr = child.getNext();
                generateStatement(initStmt);
                generateExpression(expr, node);
                break;
              }

              default:
                throw new RuntimeException("Unexpected node type "+type);
        }

    }

    private void generateYieldPoint(Node node, boolean exprContext) {
        // save stack state
        int top = cfw.getStackTop();
        maxStack = maxStack > top ? maxStack : top;
        if (cfw.getStackTop() != 0) {
            generateGetGeneratorStackState();
            for (int i = 0; i < top; i++) {
                cfw.add(ByteCode.DUP_X1);
                cfw.add(ByteCode.SWAP);
                cfw.addLoadConstant(i);
                cfw.add(ByteCode.SWAP);
                cfw.add(ByteCode.AASTORE);
            }
            // pop the array object
            cfw.add(ByteCode.POP);
        }

        // generate the yield argument
        Node child = node.getFirstChild();
        if (child != null)
            generateExpression(child, node);
        else
            Codegen.pushUndefined(cfw);

        // change the resumption state
        int nextState = getNextGeneratorState(node);
        generateSetGeneratorResumptionPoint(nextState);

        boolean hasLocals = generateSaveLocals(node);

        cfw.add(ByteCode.ARETURN);

        generateCheckForThrowOrClose(getTargetLabel(node),
                hasLocals, nextState);

        // reconstruct the stack
        if (top != 0) {
            generateGetGeneratorStackState();
            for (int i = 0; i < top; i++) {
                cfw.add(ByteCode.DUP);
                cfw.addLoadConstant(top - i - 1);
                cfw.add(ByteCode.AALOAD);
                cfw.add(ByteCode.SWAP);
            }
            cfw.add(ByteCode.POP);
        }

        // load return value from yield
        if (exprContext) {
            cfw.addALoad(argsLocal);
        }
    }

    private void generateCheckForThrowOrClose(int label,
                                              boolean hasLocals,
                                              int nextState) {
        int throwLabel = cfw.acquireLabel();
        int closeLabel = cfw.acquireLabel();

        // throw the user provided object, if the operation is .throw()
        cfw.markLabel(throwLabel);
        cfw.addALoad(argsLocal);
        generateThrowJavaScriptException();

        // throw our special internal exception if the generator is being closed
        cfw.markLabel(closeLabel);
        cfw.addALoad(argsLocal);
        cfw.add(ByteCode.CHECKCAST, "java/lang/Throwable");
        cfw.add(ByteCode.ATHROW);

        // mark the re-entry point
        // jump here after initializing the locals
        if (label != -1)
            cfw.markLabel(label);
        if (!hasLocals) {
            // jump here directly if there are no locals
            cfw.markTableSwitchCase(generatorSwitch, nextState);
        }

        // see if we need to dispatch for .close() or .throw()
        cfw.addILoad(operationLocal);
        cfw.addLoadConstant(NativeGenerator.GENERATOR_CLOSE);
        cfw.add(ByteCode.IF_ICMPEQ, closeLabel);
        cfw.addILoad(operationLocal);
        cfw.addLoadConstant(NativeGenerator.GENERATOR_THROW);
        cfw.add(ByteCode.IF_ICMPEQ, throwLabel);
    }

    private void generateIfJump(Node node, Node parent,
                                int trueLabel, int falseLabel)
    {
        // System.out.println("gen code for " + node.toString());

        int type = node.getType();
        Node child = node.getFirstChild();

        switch (type) {
          case Token.NOT:
            generateIfJump(child, node, falseLabel, trueLabel);
            break;

          case Token.OR:
          case Token.AND: {
            int interLabel = cfw.acquireLabel();
            if (type == Token.AND) {
                generateIfJump(child, node, interLabel, falseLabel);
            }
            else {
                generateIfJump(child, node, trueLabel, interLabel);
            }
            cfw.markLabel(interLabel);
            child = child.getNext();
            generateIfJump(child, node, trueLabel, falseLabel);
            break;
          }

          case Token.IN:
          case Token.INSTANCEOF:
          case Token.LE:
          case Token.LT:
          case Token.GE:
          case Token.GT:
            visitIfJumpRelOp(node, child, trueLabel, falseLabel);
            break;

          case Token.EQ:
          case Token.NE:
          case Token.SHEQ:
          case Token.SHNE:
            visitIfJumpEqOp(node, child, trueLabel, falseLabel);
            break;

          default:
            // Generate generic code for non-optimized jump
            generateExpression(node, parent);
            addScriptRuntimeInvoke("toBoolean", "(Ljava/lang/Object;)Z");
            cfw.add(ByteCode.IFNE, trueLabel);
            cfw.add(ByteCode.GOTO, falseLabel);
        }
    }

    private void visitFunction(OptFunctionNode ofn, int functionType)
    {
        int fnIndex = codegen.getIndex(ofn.fnode);
        cfw.add(ByteCode.NEW, codegen.mainClassName);
        // Call function constructor
        cfw.add(ByteCode.DUP);
        cfw.addALoad(variableObjectLocal);
        cfw.addALoad(contextLocal);           // load 'cx'
        cfw.addPush(fnIndex);
        cfw.addInvoke(ByteCode.INVOKESPECIAL, codegen.mainClassName,
                      "<init>", Codegen.FUNCTION_CONSTRUCTOR_SIGNATURE);

        if (functionType == FunctionNode.FUNCTION_EXPRESSION) {
            // Leave closure object on stack and do not pass it to
            // initFunction which suppose to connect statements to scope
            return;
        }
        cfw.addPush(functionType);
        cfw.addALoad(variableObjectLocal);
        cfw.addALoad(contextLocal);           // load 'cx'
        addOptRuntimeInvoke("initFunction",
                            "(Lorg/mozilla/javascript/NativeFunction;"
                            +"I"
                            +"Lorg/mozilla/javascript/Scriptable;"
                            +"Lorg/mozilla/javascript/Context;"
                            +")V");
    }

    private int getTargetLabel(Node target)
    {
        int labelId = target.labelId();
        if (labelId == -1) {
            labelId = cfw.acquireLabel();
            target.labelId(labelId);
        }
        return labelId;
    }

    private void visitGoto(Jump node, int type, Node child)
    {
        Node target = node.target;
        if (type == Token.IFEQ || type == Token.IFNE) {
            if (child == null) throw Codegen.badTree();
            int targetLabel = getTargetLabel(target);
            int fallThruLabel = cfw.acquireLabel();
            if (type == Token.IFEQ)
                generateIfJump(child, node, targetLabel, fallThruLabel);
            else
                generateIfJump(child, node, fallThruLabel, targetLabel);
            cfw.markLabel(fallThruLabel);
        } else {
            if (type == Token.JSR) {
                if (isGenerator) {
                    addGotoWithReturn(target);
                } else {
                    // This assumes that JSR is only ever used for finally
                    inlineFinally(target);
                }
            } else {
                addGoto(target, ByteCode.GOTO);
            }
        }
    }

    private void addGotoWithReturn(Node target) {
        FinallyReturnPoint ret = finallys.get(target);
        cfw.addLoadConstant(ret.jsrPoints.size());
        addGoto(target, ByteCode.GOTO);
        int retLabel = cfw.acquireLabel();
        cfw.markLabel(retLabel);
        ret.jsrPoints.add(Integer.valueOf(retLabel));
    }

    private void generateArrayLiteralFactory(Node node, int count) {
        String methodName = codegen.getBodyMethodName(scriptOrFn) + "_literal" + count;
        initBodyGeneration();
        argsLocal = firstFreeLocal++;
        localsMax = firstFreeLocal;
        cfw.startMethod(methodName, "(Lorg/mozilla/javascript/Context;"
                +"Lorg/mozilla/javascript/Scriptable;"
                +"Lorg/mozilla/javascript/Scriptable;"
                +"[Ljava/lang/Object;"
                +")Lorg/mozilla/javascript/Scriptable;",
                ACC_PRIVATE);
        visitArrayLiteral(node, node.getFirstChild(), true);
        cfw.add(ByteCode.ARETURN);
        cfw.stopMethod((short)(localsMax + 1));
    }

    private void generateObjectLiteralFactory(Node node, int count) {
        String methodName = codegen.getBodyMethodName(scriptOrFn) + "_literal" + count;
        initBodyGeneration();
        argsLocal = firstFreeLocal++;
        localsMax = firstFreeLocal;
        cfw.startMethod(methodName, "(Lorg/mozilla/javascript/Context;"
                +"Lorg/mozilla/javascript/Scriptable;"
                +"Lorg/mozilla/javascript/Scriptable;"
                +"[Ljava/lang/Object;"
                +")Lorg/mozilla/javascript/Scriptable;",
                ACC_PRIVATE);
        visitObjectLiteral(node, node.getFirstChild(), true);
        cfw.add(ByteCode.ARETURN);
        cfw.stopMethod((short)(localsMax + 1));
    }


    private void visitArrayLiteral(Node node, Node child, boolean topLevel)
    {
        int count = 0;
        for (Node cursor = child; cursor != null; cursor = cursor.getNext()) {
            ++count;
        }

        // If code budget is tight swap out literals into separate method
        if (!topLevel && (count > 10 || cfw.getCurrentCodeOffset() > 30000)
                && !hasVarsInRegs && !isGenerator && !inLocalBlock) {
            if (literals == null) {
                literals = new LinkedList<Node>();
            }
            literals.add(node);
            String methodName = codegen.getBodyMethodName(scriptOrFn) + "_literal" + literals.size();
            cfw.addALoad(funObjLocal);
            cfw.addALoad(contextLocal);
            cfw.addALoad(variableObjectLocal);
            cfw.addALoad(thisObjLocal);
            cfw.addALoad(argsLocal);
            cfw.addInvoke(ByteCode.INVOKEVIRTUAL, codegen.mainClassName, methodName,
                    "(Lorg/mozilla/javascript/Context;"
                        +"Lorg/mozilla/javascript/Scriptable;"
                        +"Lorg/mozilla/javascript/Scriptable;"
                        +"[Ljava/lang/Object;"
                        +")Lorg/mozilla/javascript/Scriptable;");
            return;
        }

        // load array to store array literal objects
        addNewObjectArray(count);
        for (int i = 0; i != count; ++i) {
            cfw.add(ByteCode.DUP);
            cfw.addPush(i);
            generateExpression(child, node);
            cfw.add(ByteCode.AASTORE);
            child = child.getNext();
        }
        int[] skipIndexes = (int[])node.getProp(Node.SKIP_INDEXES_PROP);
        if (skipIndexes == null) {
            cfw.add(ByteCode.ACONST_NULL);
            cfw.add(ByteCode.ICONST_0);
        } else {
            cfw.addPush(OptRuntime.encodeIntArray(skipIndexes));
            cfw.addPush(skipIndexes.length);
        }
        cfw.addALoad(contextLocal);
        cfw.addALoad(variableObjectLocal);
        addOptRuntimeInvoke("newArrayLiteral",
             "([Ljava/lang/Object;"
             +"Ljava/lang/String;"
             +"I"
             +"Lorg/mozilla/javascript/Context;"
             +"Lorg/mozilla/javascript/Scriptable;"
             +")Lorg/mozilla/javascript/Scriptable;");
    }

    private void visitObjectLiteral(Node node, Node child, boolean topLevel)
    {
        Object[] properties = (Object[])node.getProp(Node.OBJECT_IDS_PROP);
        int count = properties.length;

        // If code budget is tight swap out literals into separate method
        if (!topLevel && (count > 10 || cfw.getCurrentCodeOffset() > 30000)
                && !hasVarsInRegs && !isGenerator && !inLocalBlock) {
            if (literals == null) {
                literals = new LinkedList<Node>();
            }
            literals.add(node);
            String methodName = codegen.getBodyMethodName(scriptOrFn) + "_literal" + literals.size();
            cfw.addALoad(funObjLocal);
            cfw.addALoad(contextLocal);
            cfw.addALoad(variableObjectLocal);
            cfw.addALoad(thisObjLocal);
            cfw.addALoad(argsLocal);
            cfw.addInvoke(ByteCode.INVOKEVIRTUAL, codegen.mainClassName, methodName,
                    "(Lorg/mozilla/javascript/Context;"
                        +"Lorg/mozilla/javascript/Scriptable;"
                        +"Lorg/mozilla/javascript/Scriptable;"
                        +"[Ljava/lang/Object;"
                        +")Lorg/mozilla/javascript/Scriptable;");
            return;
        }

        // load array with property ids
        addNewObjectArray(count);
        for (int i = 0; i != count; ++i) {
            cfw.add(ByteCode.DUP);
            cfw.addPush(i);
            Object id = properties[i];
            if (id instanceof String) {
                cfw.addPush((String)id);
            } else {
                cfw.addPush(((Integer)id).intValue());
                addScriptRuntimeInvoke("wrapInt", "(I)Ljava/lang/Integer;");
            }
            cfw.add(ByteCode.AASTORE);
        }
        // load array with property values
        addNewObjectArray(count);
        Node child2 = child;
        for (int i = 0; i != count; ++i) {
            cfw.add(ByteCode.DUP);
            cfw.addPush(i);
            int childType = child2.getType();
            if (childType == Token.GET || childType == Token.SET) {
                generateExpression(child2.getFirstChild(), node);
            } else {
                generateExpression(child2, node);
            }
            cfw.add(ByteCode.AASTORE);
            child2 = child2.getNext();
        }
        // check if object literal actually has any getters or setters
        boolean hasGetterSetters = false;
        child2 = child;
        for (int i = 0; i != count; ++i) {
            int childType = child2.getType();
            if (childType == Token.GET || childType == Token.SET) {
                hasGetterSetters = true;
                break;
            }
            child2 = child2.getNext();
        }
        // create getter/setter flag array
        if (hasGetterSetters) {
            cfw.addPush(count);
            cfw.add(ByteCode.NEWARRAY, ByteCode.T_INT);
            child2 = child;
            for (int i = 0; i != count; ++i) {
                cfw.add(ByteCode.DUP);
                cfw.addPush(i);
                int childType = child2.getType();
                if (childType == Token.GET) {
                    cfw.add(ByteCode.ICONST_M1);
                } else if (childType == Token.SET) {
                    cfw.add(ByteCode.ICONST_1);
                } else {
                    cfw.add(ByteCode.ICONST_0);
                }
                cfw.add(ByteCode.IASTORE);
                child2 = child2.getNext();
            }
        } else {
            cfw.add(ByteCode.ACONST_NULL);
        }

        cfw.addALoad(contextLocal);
        cfw.addALoad(variableObjectLocal);
        addScriptRuntimeInvoke("newObjectLiteral",
             "([Ljava/lang/Object;"
             +"[Ljava/lang/Object;"
             +"[I"
             +"Lorg/mozilla/javascript/Context;"
             +"Lorg/mozilla/javascript/Scriptable;"
             +")Lorg/mozilla/javascript/Scriptable;");
    }

    private void visitSpecialCall(Node node, int type, int specialType,
                                  Node child)
    {
        cfw.addALoad(contextLocal);

        if (type == Token.NEW) {
            generateExpression(child, node);
            // stack: ... cx functionObj
        } else {
            generateFunctionAndThisObj(child, node);
            // stack: ... cx functionObj thisObj
        }
        child = child.getNext();

        generateCallArgArray(node, child, false);

        String methodName;
        String callSignature;

        if (type == Token.NEW) {
            methodName = "newObjectSpecial";
            callSignature = "(Lorg/mozilla/javascript/Context;"
                            +"Ljava/lang/Object;"
                            +"[Ljava/lang/Object;"
                            +"Lorg/mozilla/javascript/Scriptable;"
                            +"Lorg/mozilla/javascript/Scriptable;"
                            +"I" // call type
                            +")Ljava/lang/Object;";
            cfw.addALoad(variableObjectLocal);
            cfw.addALoad(thisObjLocal);
            cfw.addPush(specialType);
        } else {
            methodName = "callSpecial";
            callSignature = "(Lorg/mozilla/javascript/Context;"
                            +"Lorg/mozilla/javascript/Callable;"
                            +"Lorg/mozilla/javascript/Scriptable;"
                            +"[Ljava/lang/Object;"
                            +"Lorg/mozilla/javascript/Scriptable;"
                            +"Lorg/mozilla/javascript/Scriptable;"
                            +"I" // call type
                            +"Ljava/lang/String;I"  // filename, linenumber
                            +")Ljava/lang/Object;";
            cfw.addALoad(variableObjectLocal);
            cfw.addALoad(thisObjLocal);
            cfw.addPush(specialType);
            String sourceName = scriptOrFn.getSourceName();
            cfw.addPush(sourceName == null ? "" : sourceName);
            cfw.addPush(itsLineNumber);
        }

        addOptRuntimeInvoke(methodName, callSignature);
    }

    private void visitStandardCall(Node node, Node child)
    {
        if (node.getType() != Token.CALL) throw Codegen.badTree();

        Node firstArgChild = child.getNext();
        int childType = child.getType();

        String methodName;
        String signature;

        if (firstArgChild == null) {
            if (childType == Token.NAME) {
                // name() call
                String name = child.getString();
                cfw.addPush(name);
                methodName = "callName0";
                signature = "(Ljava/lang/String;"
                            +"Lorg/mozilla/javascript/Context;"
                            +"Lorg/mozilla/javascript/Scriptable;"
                            +")Ljava/lang/Object;";
            } else if (childType == Token.GETPROP) {
                // x.name() call
                Node propTarget = child.getFirstChild();
                generateExpression(propTarget, node);
                Node id = propTarget.getNext();
                String property = id.getString();
                cfw.addPush(property);
                methodName = "callProp0";
                signature = "(Ljava/lang/Object;"
                            +"Ljava/lang/String;"
                            +"Lorg/mozilla/javascript/Context;"
                            +"Lorg/mozilla/javascript/Scriptable;"
                            +")Ljava/lang/Object;";
            } else if (childType == Token.GETPROPNOWARN) {
                throw Kit.codeBug();
            } else {
                generateFunctionAndThisObj(child, node);
                methodName = "call0";
                signature = "(Lorg/mozilla/javascript/Callable;"
                            +"Lorg/mozilla/javascript/Scriptable;"
                            +"Lorg/mozilla/javascript/Context;"
                            +"Lorg/mozilla/javascript/Scriptable;"
                            +")Ljava/lang/Object;";
            }

        } else if (childType == Token.NAME) {
            // XXX: this optimization is only possible if name
            // resolution
            // is not affected by arguments evaluation and currently
            // there are no checks for it
            String name = child.getString();
            generateCallArgArray(node, firstArgChild, false);
            cfw.addPush(name);
            methodName = "callName";
            signature = "([Ljava/lang/Object;"
                        +"Ljava/lang/String;"
                        +"Lorg/mozilla/javascript/Context;"
                        +"Lorg/mozilla/javascript/Scriptable;"
                        +")Ljava/lang/Object;";
        } else {
            int argCount = 0;
            for (Node arg = firstArgChild; arg != null; arg = arg.getNext()) {
                ++argCount;
            }
            generateFunctionAndThisObj(child, node);
            // stack: ... functionObj thisObj
            if (argCount == 1) {
                generateExpression(firstArgChild, node);
                methodName = "call1";
                signature = "(Lorg/mozilla/javascript/Callable;"
                            +"Lorg/mozilla/javascript/Scriptable;"
                            +"Ljava/lang/Object;"
                            +"Lorg/mozilla/javascript/Context;"
                            +"Lorg/mozilla/javascript/Scriptable;"
                            +")Ljava/lang/Object;";
            } else if (argCount == 2) {
                generateExpression(firstArgChild, node);
                generateExpression(firstArgChild.getNext(), node);
                methodName = "call2";
                signature = "(Lorg/mozilla/javascript/Callable;"
                            +"Lorg/mozilla/javascript/Scriptable;"
                            +"Ljava/lang/Object;"
                            +"Ljava/lang/Object;"
                            +"Lorg/mozilla/javascript/Context;"
                            +"Lorg/mozilla/javascript/Scriptable;"
                            +")Ljava/lang/Object;";
            } else {
                generateCallArgArray(node, firstArgChild, false);
                methodName = "callN";
                signature = "(Lorg/mozilla/javascript/Callable;"
                            +"Lorg/mozilla/javascript/Scriptable;"
                            +"[Ljava/lang/Object;"
                            +"Lorg/mozilla/javascript/Context;"
                            +"Lorg/mozilla/javascript/Scriptable;"
                            +")Ljava/lang/Object;";
            }
        }

        cfw.addALoad(contextLocal);
        cfw.addALoad(variableObjectLocal);
        addOptRuntimeInvoke(methodName, signature);
    }

    private void visitStandardNew(Node node, Node child)
    {
        if (node.getType() != Token.NEW) throw Codegen.badTree();

        Node firstArgChild = child.getNext();

        generateExpression(child, node);
        // stack: ... functionObj
        cfw.addALoad(contextLocal);
        cfw.addALoad(variableObjectLocal);
        // stack: ... functionObj cx scope
        generateCallArgArray(node, firstArgChild, false);
        addScriptRuntimeInvoke(
            "newObject",
            "(Ljava/lang/Object;"
            +"Lorg/mozilla/javascript/Context;"
            +"Lorg/mozilla/javascript/Scriptable;"
            +"[Ljava/lang/Object;"
            +")Lorg/mozilla/javascript/Scriptable;");
    }

    private void visitOptimizedCall(Node node, OptFunctionNode target,
                                    int type, Node child)
    {
        Node firstArgChild = child.getNext();
        String className = codegen.mainClassName;

        short thisObjLocal = 0;
        if (type == Token.NEW) {
            generateExpression(child, node);
        } else {
            generateFunctionAndThisObj(child, node);
            thisObjLocal = getNewWordLocal();
            cfw.addAStore(thisObjLocal);
        }
        // stack: ... functionObj

        int beyond = cfw.acquireLabel();
        int regularCall = cfw.acquireLabel();

        cfw.add(ByteCode.DUP);
        cfw.add(ByteCode.INSTANCEOF, className);
        cfw.add(ByteCode.IFEQ, regularCall);
        cfw.add(ByteCode.CHECKCAST, className);
        cfw.add(ByteCode.DUP);
        cfw.add(ByteCode.GETFIELD, className, Codegen.ID_FIELD_NAME, "I");
        cfw.addPush(codegen.getIndex(target.fnode));
        cfw.add(ByteCode.IF_ICMPNE, regularCall);

        // stack: ... directFunct
        cfw.addALoad(contextLocal);
        cfw.addALoad(variableObjectLocal);
        // stack: ... directFunc cx scope

        if (type == Token.NEW) {
            cfw.add(ByteCode.ACONST_NULL);
        } else {
            cfw.addALoad(thisObjLocal);
        }
        // stack: ... directFunc cx scope thisObj
/*
Remember that directCall parameters are paired in 1 aReg and 1 dReg
If the argument is an incoming arg, just pass the orginal pair thru.
Else, if the argument is known to be typed 'Number', pass Void.TYPE
in the aReg and the number is the dReg
Else pass the JS object in the aReg and 0.0 in the dReg.
*/
        Node argChild = firstArgChild;
        while (argChild != null) {
            int dcp_register = nodeIsDirectCallParameter(argChild);
            if (dcp_register >= 0) {
                cfw.addALoad(dcp_register);
                cfw.addDLoad(dcp_register + 1);
            } else if (argChild.getIntProp(Node.ISNUMBER_PROP, -1)
                       == Node.BOTH)
            {
                cfw.add(ByteCode.GETSTATIC,
                        "java/lang/Void",
                        "TYPE",
                        "Ljava/lang/Class;");
                generateExpression(argChild, node);
            } else {
                generateExpression(argChild, node);
                cfw.addPush(0.0);
            }
            argChild = argChild.getNext();
        }

        cfw.add(ByteCode.GETSTATIC,
                "org/mozilla/javascript/ScriptRuntime",
                "emptyArgs", "[Ljava/lang/Object;");
        cfw.addInvoke(ByteCode.INVOKESTATIC,
                      codegen.mainClassName,
                      (type == Token.NEW)
                          ? codegen.getDirectCtorName(target.fnode)
                          : codegen.getBodyMethodName(target.fnode),
                      codegen.getBodyMethodSignature(target.fnode));

        cfw.add(ByteCode.GOTO, beyond);

        cfw.markLabel(regularCall);
        // stack: ... functionObj
        cfw.addALoad(contextLocal);
        cfw.addALoad(variableObjectLocal);
        // stack: ... functionObj cx scope
        if (type != Token.NEW) {
            cfw.addALoad(thisObjLocal);
            releaseWordLocal(thisObjLocal);
            // stack: ... functionObj cx scope thisObj
        }
        // XXX: this will generate code for the child array the second time,
        // so expression code generation better not to alter tree structure...
        generateCallArgArray(node, firstArgChild, true);

        if (type == Token.NEW) {
            addScriptRuntimeInvoke(
                "newObject",
                "(Ljava/lang/Object;"
                +"Lorg/mozilla/javascript/Context;"
                +"Lorg/mozilla/javascript/Scriptable;"
                +"[Ljava/lang/Object;"
                +")Lorg/mozilla/javascript/Scriptable;");
        } else {
            cfw.addInvoke(ByteCode.INVOKEINTERFACE,
                "org/mozilla/javascript/Callable",
                "call",
                "(Lorg/mozilla/javascript/Context;"
                +"Lorg/mozilla/javascript/Scriptable;"
                +"Lorg/mozilla/javascript/Scriptable;"
                +"[Ljava/lang/Object;"
                +")Ljava/lang/Object;");
        }

        cfw.markLabel(beyond);
    }

    private void generateCallArgArray(Node node, Node argChild, boolean directCall)
    {
        int argCount = 0;
        for (Node child = argChild; child != null; child = child.getNext()) {
            ++argCount;
        }
        // load array object to set arguments
        if (argCount == 1 && itsOneArgArray >= 0) {
            cfw.addALoad(itsOneArgArray);
        } else {
            addNewObjectArray(argCount);
        }
        // Copy arguments into it
        for (int i = 0; i != argCount; ++i) {
            // If we are compiling a generator an argument could be the result
            // of a yield. In that case we will have an immediate on the stack
            // which we need to avoid
            if (!isGenerator) {
                cfw.add(ByteCode.DUP);
                cfw.addPush(i);
            }

            if (!directCall) {
                generateExpression(argChild, node);
            } else {
                // If this has also been a directCall sequence, the Number
                // flag will have remained set for any parameter so that
                // the values could be copied directly into the outgoing
                // args. Here we want to force it to be treated as not in
                // a Number context, so we set the flag off.
                int dcp_register = nodeIsDirectCallParameter(argChild);
                if (dcp_register >= 0) {
                    dcpLoadAsObject(dcp_register);
                } else {
                    generateExpression(argChild, node);
                    int childNumberFlag
                            = argChild.getIntProp(Node.ISNUMBER_PROP, -1);
                    if (childNumberFlag == Node.BOTH) {
                        addDoubleWrap();
                    }
                }
            }

            // When compiling generators, any argument to a method may be a
            // yield expression. Hence we compile the argument first and then
            // load the argument index and assign the value to the args array.
            if (isGenerator) {
                short tempLocal = getNewWordLocal();
                cfw.addAStore(tempLocal);
                cfw.add(ByteCode.CHECKCAST, "[Ljava/lang/Object;");
                cfw.add(ByteCode.DUP);
                cfw.addPush(i);
                cfw.addALoad(tempLocal);
                releaseWordLocal(tempLocal);
            }

            cfw.add(ByteCode.AASTORE);

            argChild = argChild.getNext();
        }
    }

    private void generateFunctionAndThisObj(Node node, Node parent)
    {
        // Place on stack (function object, function this) pair
        int type = node.getType();
        switch (node.getType()) {
          case Token.GETPROPNOWARN:
            throw Kit.codeBug();

          case Token.GETPROP:
          case Token.GETELEM: {
            Node target = node.getFirstChild();
            generateExpression(target, node);
            Node id = target.getNext();
            if (type == Token.GETPROP) {
                String property = id.getString();
                cfw.addPush(property);
                cfw.addALoad(contextLocal);
                cfw.addALoad(variableObjectLocal);
                addScriptRuntimeInvoke(
                    "getPropFunctionAndThis",
                    "(Ljava/lang/Object;"
                    +"Ljava/lang/String;"
                    +"Lorg/mozilla/javascript/Context;"
                    +"Lorg/mozilla/javascript/Scriptable;"
                    +")Lorg/mozilla/javascript/Callable;");
            } else {
                generateExpression(id, node);  // id
                if (node.getIntProp(Node.ISNUMBER_PROP, -1) != -1)
                    addDoubleWrap();
                cfw.addALoad(contextLocal);
                addScriptRuntimeInvoke(
                    "getElemFunctionAndThis",
                    "(Ljava/lang/Object;"
                    +"Ljava/lang/Object;"
                    +"Lorg/mozilla/javascript/Context;"
                    +")Lorg/mozilla/javascript/Callable;");
            }
            break;
          }

          case Token.NAME: {
            String name = node.getString();
            cfw.addPush(name);
            cfw.addALoad(contextLocal);
            cfw.addALoad(variableObjectLocal);
            addScriptRuntimeInvoke(
                "getNameFunctionAndThis",
                "(Ljava/lang/String;"
                +"Lorg/mozilla/javascript/Context;"
                +"Lorg/mozilla/javascript/Scriptable;"
                +")Lorg/mozilla/javascript/Callable;");
            break;
          }

          default: // including GETVAR
            generateExpression(node, parent);
            cfw.addALoad(contextLocal);
            addScriptRuntimeInvoke(
                "getValueFunctionAndThis",
                "(Ljava/lang/Object;"
                +"Lorg/mozilla/javascript/Context;"
                +")Lorg/mozilla/javascript/Callable;");
            break;
        }
        // Get thisObj prepared by get(Name|Prop|Elem|Value)FunctionAndThis
        cfw.addALoad(contextLocal);
        addScriptRuntimeInvoke(
            "lastStoredScriptable",
            "(Lorg/mozilla/javascript/Context;"
            +")Lorg/mozilla/javascript/Scriptable;");
    }

    private void updateLineNumber(Node node)
    {
        itsLineNumber = node.getLineno();
        if (itsLineNumber == -1)
            return;
        cfw.addLineNumberEntry((short)itsLineNumber);
    }

    private void visitTryCatchFinally(Jump node, Node child)
    {
        /* Save the variable object, in case there are with statements
         * enclosed by the try block and we catch some exception.
         * We'll restore it for the catch block so that catch block
         * statements get the right scope.
         */

        // OPT we only need to do this if there are enclosed WITH
        // statements; could statically check and omit this if there aren't any.

        // XXX OPT Maybe instead do syntactic transforms to associate
        // each 'with' with a try/finally block that does the exitwith.

        short savedVariableObject = getNewWordLocal();
        cfw.addALoad(variableObjectLocal);
        cfw.addAStore(savedVariableObject);

        /*
         * Generate the code for the tree; most of the work is done in IRFactory
         * and NodeTransformer;  Codegen just adds the java handlers for the
         * javascript catch and finally clauses.  */

        int startLabel = cfw.acquireLabel();
        cfw.markLabel(startLabel, (short)0);

        Node catchTarget = node.target;
        Node finallyTarget = node.getFinally();
        int[] handlerLabels = new int[EXCEPTION_MAX];

        exceptionManager.pushExceptionInfo(node);
        if (catchTarget != null) {
            handlerLabels[JAVASCRIPT_EXCEPTION] = cfw.acquireLabel();
            handlerLabels[EVALUATOR_EXCEPTION] = cfw.acquireLabel();
            handlerLabels[ECMAERROR_EXCEPTION] = cfw.acquireLabel();
            Context cx = Context.getCurrentContext();
            if (cx != null &&
                cx.hasFeature(Context.FEATURE_ENHANCED_JAVA_ACCESS)) {
                handlerLabels[THROWABLE_EXCEPTION] = cfw.acquireLabel();
            }
        }
        if (finallyTarget != null) {
            handlerLabels[FINALLY_EXCEPTION] = cfw.acquireLabel();
        }
        exceptionManager.setHandlers(handlerLabels, startLabel);

        // create a table for the equivalent of JSR returns
        if (isGenerator && finallyTarget != null) {
            FinallyReturnPoint ret = new FinallyReturnPoint();
            if (finallys == null) {
                finallys = new HashMap<Node,FinallyReturnPoint>();
            }
            // add the finally target to hashtable
            finallys.put(finallyTarget, ret);
            // add the finally node as well to the hash table
            finallys.put(finallyTarget.getNext(), ret);
        }

        while (child != null) {
            if (child == catchTarget) {
                int catchLabel = getTargetLabel(catchTarget);
                exceptionManager.removeHandler(JAVASCRIPT_EXCEPTION,
                                               catchLabel);
                exceptionManager.removeHandler(EVALUATOR_EXCEPTION,
                                               catchLabel);
                exceptionManager.removeHandler(ECMAERROR_EXCEPTION,
                                               catchLabel);
                exceptionManager.removeHandler(THROWABLE_EXCEPTION,
                                               catchLabel);
            }
            generateStatement(child);
            child = child.getNext();
        }

        // control flow skips the handlers
        int realEnd = cfw.acquireLabel();
        cfw.add(ByteCode.GOTO, realEnd);

        int exceptionLocal = getLocalBlockRegister(node);
        // javascript handler; unwrap exception and GOTO to javascript
        // catch area.
        if (catchTarget != null) {
            // get the label to goto
            int catchLabel = catchTarget.labelId();

            // If the function is a generator, then handlerLabels will consist
            // of zero labels. generateCatchBlock will create its own label
            // in this case. The extra parameter for the label is added for
            // the case of non-generator functions that inline finally blocks.

            generateCatchBlock(JAVASCRIPT_EXCEPTION, savedVariableObject,
                               catchLabel, exceptionLocal,
                               handlerLabels[JAVASCRIPT_EXCEPTION]);
            /*
             * catch WrappedExceptions, see if they are wrapped
             * JavaScriptExceptions. Otherwise, rethrow.
             */
            generateCatchBlock(EVALUATOR_EXCEPTION, savedVariableObject,
                               catchLabel, exceptionLocal,
                               handlerLabels[EVALUATOR_EXCEPTION]);

            /*
                we also need to catch EcmaErrors and feed the
                associated error object to the handler
            */
            generateCatchBlock(ECMAERROR_EXCEPTION, savedVariableObject,
                               catchLabel, exceptionLocal,
                               handlerLabels[ECMAERROR_EXCEPTION]);

            Context cx = Context.getCurrentContext();
            if (cx != null &&
                cx.hasFeature(Context.FEATURE_ENHANCED_JAVA_ACCESS))
            {
                generateCatchBlock(THROWABLE_EXCEPTION, savedVariableObject,
                                   catchLabel, exceptionLocal,
                                   handlerLabels[THROWABLE_EXCEPTION]);
            }
        }

        // finally handler; catch all exceptions, store to a local; JSR to
        // the finally, then re-throw.
        if (finallyTarget != null) {
            int finallyHandler = cfw.acquireLabel();
            int finallyEnd = cfw.acquireLabel();
            cfw.markHandler(finallyHandler);
            if (!isGenerator) {
                cfw.markLabel(handlerLabels[FINALLY_EXCEPTION]);
            }
            cfw.addAStore(exceptionLocal);

            // reset the variable object local
            cfw.addALoad(savedVariableObject);
            cfw.addAStore(variableObjectLocal);

            // get the label to JSR to
            int finallyLabel = finallyTarget.labelId();
            if (isGenerator)
                addGotoWithReturn(finallyTarget);
            else {
                inlineFinally(finallyTarget, handlerLabels[FINALLY_EXCEPTION],
                              finallyEnd);
            }

            // rethrow
            cfw.addALoad(exceptionLocal);
            if (isGenerator)
                cfw.add(ByteCode.CHECKCAST, "java/lang/Throwable");
            cfw.add(ByteCode.ATHROW);

            cfw.markLabel(finallyEnd);
            // mark the handler
            if (isGenerator) {
                cfw.addExceptionHandler(startLabel, finallyLabel,
                                        finallyHandler, null); // catch any
            }
        }
        releaseWordLocal(savedVariableObject);
        cfw.markLabel(realEnd);

        if (!isGenerator) {
            exceptionManager.popExceptionInfo();
        }
    }

    private static final int JAVASCRIPT_EXCEPTION  = 0;
    private static final int EVALUATOR_EXCEPTION   = 1;
    private static final int ECMAERROR_EXCEPTION   = 2;
    private static final int THROWABLE_EXCEPTION   = 3;
    // Finally catch-alls are technically Throwable, but we want a distinction
    // for the exception manager and we want to use a null string instead of
    // an explicit Throwable string.
    private static final int FINALLY_EXCEPTION = 4;
    private static final int EXCEPTION_MAX = 5;

    private void generateCatchBlock(int exceptionType,
                                    short savedVariableObject,
                                    int catchLabel,
                                    int exceptionLocal,
                                    int handler)
    {
        if (handler == 0) {
            handler = cfw.acquireLabel();
        }
        cfw.markHandler(handler);

        // MS JVM gets cranky if the exception object is left on the stack
        cfw.addAStore(exceptionLocal);

        // reset the variable object local
        cfw.addALoad(savedVariableObject);
        cfw.addAStore(variableObjectLocal);

        String exceptionName = exceptionTypeToName(exceptionType);

        cfw.add(ByteCode.GOTO, catchLabel);
    }

    private String exceptionTypeToName(int exceptionType)
    {
        if (exceptionType == JAVASCRIPT_EXCEPTION) {
            return "org/mozilla/javascript/JavaScriptException";
        } else if (exceptionType == EVALUATOR_EXCEPTION) {
            return "org/mozilla/javascript/EvaluatorException";
        } else if (exceptionType == ECMAERROR_EXCEPTION) {
            return "org/mozilla/javascript/EcmaError";
        } else if (exceptionType == THROWABLE_EXCEPTION) {
            return "java/lang/Throwable";
        } else if (exceptionType == FINALLY_EXCEPTION) {
            return null;
        } else {
            throw Kit.codeBug();
        }
    }

    /**
     * Manages placement of exception handlers for non-generator functions.
     *
     * For generator functions, there are mechanisms put into place to emulate
     * jsr by using a goto with a return label. That is one mechanism for
     * implementing finally blocks. The other, which is implemented by Sun,
     * involves duplicating the finally block where jsr instructions would
     * normally be. However, inlining finally blocks causes problems with
     * translating exception handlers. Instead of having one big bytecode range
     * for each exception, we now have to skip over the inlined finally blocks.
     * This class is meant to help implement this.
     *
     * Every time a try block is encountered during translation, exception
     * information should be pushed into the manager, which is treated as a
     * stack. The addHandler() and setHandlers() methods may be used to register
     * exceptionHandlers for the try block; removeHandler() is used to reverse
     * the operation. At the end of the try/catch/finally, the exception state
     * for it should be popped.
     *
     * The important function here is markInlineFinally. This finds which
     * finally block on the exception state stack is being inlined and skips
     * the proper exception handlers until the finally block is generated.
     */
    private class ExceptionManager
    {
        ExceptionManager()
        {
            exceptionInfo = new LinkedList<ExceptionInfo>();
        }

        /**
         * Push a new try block onto the exception information stack.
         *
         * @param node an exception handling node (node.getType() ==
         *             Token.TRY)
         */
        void pushExceptionInfo(Jump node)
        {
            Node fBlock = getFinallyAtTarget(node.getFinally());
            ExceptionInfo ei = new ExceptionInfo(node, fBlock);
            exceptionInfo.add(ei);
        }

        /**
         * Register an exception handler for the try block at the top of the
         * exception information stack.
         *
         * @param exceptionType one of the integer constants representing an
         *                      exception type
         * @param handlerLabel the label of the exception handler
         * @param startLabel the label where the exception handling begins
         */
        void addHandler(int exceptionType, int handlerLabel, int startLabel)
        {
            ExceptionInfo top = getTop();
            top.handlerLabels[exceptionType] = handlerLabel;
            top.exceptionStarts[exceptionType] = startLabel;
        }

        /**
         * Register multiple exception handlers for the top try block. If the
         * exception type maps to a zero label, then it is ignored.
         *
         * @param handlerLabels a map from integer constants representing an
         *                      exception type to the label of the exception
         *                      handler
         * @param startLabel the label where all of the exception handling
         *                   begins
         */
        void setHandlers(int[] handlerLabels, int startLabel)
        {
            ExceptionInfo top = getTop();
            for (int i = 0; i < handlerLabels.length; i++) {
                if (handlerLabels[i] != 0) {
                    addHandler(i, handlerLabels[i], startLabel);
                }
            }
        }

        /**
         * Remove an exception handler for the top try block.
         *
         * @param exceptionType one of the integer constants representing an
         *                      exception type
         * @param endLabel a label representing the end of the last bytecode
         *                 that should be handled by the exception
         * @returns the label of the exception handler associated with the
         *          exception type
         */
        int removeHandler(int exceptionType, int endLabel)
        {
            ExceptionInfo top = getTop();
            if (top.handlerLabels[exceptionType] != 0) {
                int handlerLabel = top.handlerLabels[exceptionType];
                endCatch(top, exceptionType, endLabel);
                top.handlerLabels[exceptionType] = 0;
                return handlerLabel;
            }
            return 0;
        }

        /**
         * Remove the top try block from the exception information stack.
         */
        void popExceptionInfo()
        {
            exceptionInfo.removeLast();
        }

        /**
         * Mark the start of an inlined finally block.
         *
         * When a finally block is inlined, any exception handlers that are
         * lexically inside of its try block should not cover the range of the
         * exception block. We scan from the innermost try block outward until
         * we find the try block that matches the finally block. For any block
         * whose exception handlers that aren't currently stopped by a finally
         * block, we stop the handlers at the beginning of the finally block
         * and set it as the finally block that has stopped the handlers. This
         * prevents other inlined finally blocks from prematurely ending skip
         * ranges and creating bad exception handler ranges.
         *
         * @param finallyBlock the finally block that is being inlined
         * @param finallyStart the label of the beginning of the inlined code
         */
        void markInlineFinallyStart(Node finallyBlock, int finallyStart)
        {
            // Traverse the stack in LIFO order until the try block
            // corresponding to the finally block has been reached. We must
            // traverse backwards because the earlier exception handlers in
            // the exception handler table have priority when determining which
            // handler to use. Therefore, we start with the most nested try
            // block and move outward.
            ListIterator<ExceptionInfo> iter =
                    exceptionInfo.listIterator(exceptionInfo.size());
            while (iter.hasPrevious()) {
                ExceptionInfo ei = iter.previous();
                for (int i = 0; i < EXCEPTION_MAX; i++) {
                    if (ei.handlerLabels[i] != 0 && ei.currentFinally == null) {
                        endCatch(ei, i, finallyStart);
                        ei.exceptionStarts[i] = 0;
                        ei.currentFinally = finallyBlock;
                    }
                }
                if (ei.finallyBlock == finallyBlock) {
                    break;
                }
            }
        }

        /**
         * Mark the end of an inlined finally block.
         *
         * For any set of exception handlers that have been stopped by the
         * inlined block, resume exception handling at the end of the finally
         * block.
         *
         * @param finallyBlock the finally block that is being inlined
         * @param finallyEnd the label of the end of the inlined code
         */
        void markInlineFinallyEnd(Node finallyBlock, int finallyEnd)
        {
            ListIterator<ExceptionInfo> iter =
                    exceptionInfo.listIterator(exceptionInfo.size());
            while (iter.hasPrevious()) {
                ExceptionInfo ei = iter.previous();
                for (int i = 0; i < EXCEPTION_MAX; i++) {
                    if (ei.handlerLabels[i] != 0 &&
                        ei.currentFinally == finallyBlock) {
                        ei.exceptionStarts[i] = finallyEnd;
                        ei.currentFinally = null;
                    }
                }
                if (ei.finallyBlock == finallyBlock) {
                    break;
                }
            }
        }

        /**
         * Mark off the end of a bytecode chunk that should be handled by an
         * exceptionHandler.
         *
         * The caller of this method must appropriately mark the start of the
         * next bytecode chunk or remove the handler.
         */
        private void endCatch(ExceptionInfo ei, int exceptionType, int catchEnd)
        {
            if (ei.exceptionStarts[exceptionType] == 0) {
                throw new IllegalStateException("bad exception start");
            }

            int currentStart = ei.exceptionStarts[exceptionType];
            int currentStartPC = cfw.getLabelPC(currentStart);
            int catchEndPC = cfw.getLabelPC(catchEnd);
            if (currentStartPC != catchEndPC) {
                cfw.addExceptionHandler(ei.exceptionStarts[exceptionType],
                                        catchEnd,
                                        ei.handlerLabels[exceptionType],
                                        exceptionTypeToName(exceptionType));
            }
        }

        private ExceptionInfo getTop()
        {
            return exceptionInfo.getLast();
        }

        private class ExceptionInfo
        {
            ExceptionInfo(Jump node, Node finallyBlock)
            {
                this.node = node;
                this.finallyBlock = finallyBlock;
                handlerLabels = new int[EXCEPTION_MAX];
                exceptionStarts = new int[EXCEPTION_MAX];
                currentFinally = null;
            }

            Jump node;
            Node finallyBlock;
            int[] handlerLabels;
            int[] exceptionStarts;
            // The current finally block that has temporarily ended the
            // exception handler ranges
            Node currentFinally;
        }

        // A stack of try/catch block information ordered by lexical scoping
        private LinkedList<ExceptionInfo> exceptionInfo;
    }

    private ExceptionManager exceptionManager = new ExceptionManager();

    /**
     * Inline a FINALLY node into the method bytecode.
     *
     * This method takes a label that points to the real start of the finally
     * block as implemented in the bytecode. This is because in some cases,
     * the finally block really starts before any of the code in the Node. For
     * example, the catch-all-rethrow finally block has a few instructions
     * prior to the finally block made by the user.
     *
     * In addition, an end label that should be unmarked is given as a method
     * parameter. It is the responsibility of any callers of this method to
     * mark the label.
     *
     * The start and end labels of the finally block are used to exclude the
     * inlined block from the proper exception handler. For example, an inlined
     * finally block should not be handled by a catch-all-rethrow.
     *
     * @param finallyTarget a TARGET node directly preceding a FINALLY node or
     *                      a FINALLY node itself
     * @param finallyStart a pre-marked label that indicates the actual start
     *                     of the finally block in the bytecode.
     * @param finallyEnd an unmarked label that will indicate the actual end
     *                   of the finally block in the bytecode.
     */
    private void inlineFinally(Node finallyTarget, int finallyStart,
                               int finallyEnd) {
        Node fBlock = getFinallyAtTarget(finallyTarget);
        fBlock.resetTargets();
        Node child = fBlock.getFirstChild();
        exceptionManager.markInlineFinallyStart(fBlock, finallyStart);
        while (child != null) {
            generateStatement(child);
            child = child.getNext();
        }
        exceptionManager.markInlineFinallyEnd(fBlock, finallyEnd);
    }

    private void inlineFinally(Node finallyTarget) {
        int finallyStart = cfw.acquireLabel();
        int finallyEnd = cfw.acquireLabel();
        cfw.markLabel(finallyStart);
        inlineFinally(finallyTarget, finallyStart, finallyEnd);
        cfw.markLabel(finallyEnd);
    }

    /**
     * Get a FINALLY node at a point in the IR.
     *
     * This is strongly dependent on the generated IR. If the node is a TARGET,
     * it only check the next node to see if it is a FINALLY node.
     */
    private Node getFinallyAtTarget(Node node) {
        if (node == null) {
            return null;
        } else if (node.getType() == Token.FINALLY) {
            return node;
        } else if (node != null && node.getType() == Token.TARGET) {
            Node fBlock = node.getNext();
            if (fBlock != null && fBlock.getType() == Token.FINALLY) {
                return fBlock;
            }
        }
        throw Kit.codeBug("bad finally target");
    }

    private boolean generateSaveLocals(Node node)
    {
        int count = 0;
        for (int i = 0; i < firstFreeLocal; i++) {
            if (locals[i] != 0)
                count++;
        }

        if (count == 0) {
            ((FunctionNode)scriptOrFn).addLiveLocals(node, null);
            return false;
        }

        // calculate the max locals
        maxLocals = maxLocals > count ? maxLocals : count;

        // create a locals list
        int[] ls = new int[count];
        int s = 0;
        for (int i = 0; i < firstFreeLocal; i++) {
            if (locals[i] != 0) {
                ls[s] = i;
                s++;
            }
        }

        // save the locals
        ((FunctionNode)scriptOrFn).addLiveLocals(node, ls);

        // save locals
        generateGetGeneratorLocalsState();
        for (int i = 0; i < count; i++) {
            cfw.add(ByteCode.DUP);
            cfw.addLoadConstant(i);
            cfw.addALoad(ls[i]);
            cfw.add(ByteCode.AASTORE);
        }
        // pop the array off the stack
        cfw.add(ByteCode.POP);

        return true;
    }

    private void visitSwitch(Jump switchNode, Node child)
    {
        // See comments in IRFactory.createSwitch() for description
        // of SWITCH node

        generateExpression(child, switchNode);
        // save selector value
        short selector = getNewWordLocal();
        cfw.addAStore(selector);

        for (Jump caseNode = (Jump)child.getNext();
             caseNode != null;
             caseNode = (Jump)caseNode.getNext())
        {
            if (caseNode.getType() != Token.CASE)
                throw Codegen.badTree();
            Node test = caseNode.getFirstChild();
            generateExpression(test, caseNode);
            cfw.addALoad(selector);
            addScriptRuntimeInvoke("shallowEq",
                                   "(Ljava/lang/Object;"
                                   +"Ljava/lang/Object;"
                                   +")Z");
            addGoto(caseNode.target, ByteCode.IFNE);
        }
        releaseWordLocal(selector);
    }

    private void visitTypeofname(Node node)
    {
        if (hasVarsInRegs) {
            int varIndex = fnCurrent.fnode.getIndexForNameNode(node);
            if (varIndex >= 0) {
                if (fnCurrent.isNumberVar(varIndex)) {
                    cfw.addPush("number");
                } else if (varIsDirectCallParameter(varIndex)) {
                    int dcp_register = varRegisters[varIndex];
                    cfw.addALoad(dcp_register);
                    cfw.add(ByteCode.GETSTATIC, "java/lang/Void", "TYPE",
                            "Ljava/lang/Class;");
                    int isNumberLabel = cfw.acquireLabel();
                    cfw.add(ByteCode.IF_ACMPEQ, isNumberLabel);
                    short stack = cfw.getStackTop();
                    cfw.addALoad(dcp_register);
                    addScriptRuntimeInvoke("typeof",
                                           "(Ljava/lang/Object;"
                                           +")Ljava/lang/String;");
                    int beyond = cfw.acquireLabel();
                    cfw.add(ByteCode.GOTO, beyond);
                    cfw.markLabel(isNumberLabel, stack);
                    cfw.addPush("number");
                    cfw.markLabel(beyond);
                } else {
                    cfw.addALoad(varRegisters[varIndex]);
                    addScriptRuntimeInvoke("typeof",
                                           "(Ljava/lang/Object;"
                                           +")Ljava/lang/String;");
                }
                return;
            }
        }
        cfw.addALoad(variableObjectLocal);
        cfw.addPush(node.getString());
        addScriptRuntimeInvoke("typeofName",
                               "(Lorg/mozilla/javascript/Scriptable;"
                               +"Ljava/lang/String;"
                               +")Ljava/lang/String;");
    }

    /**
     * Save the current code offset. This saved code offset is used to
     * compute instruction counts in subsequent calls to
     * {@link #addInstructionCount()}.
     */
    private void saveCurrentCodeOffset() {
        savedCodeOffset = cfw.getCurrentCodeOffset();
    }

    /**
     * Generate calls to ScriptRuntime.addInstructionCount to keep track of
     * executed instructions and call <code>observeInstructionCount()</code>
     * if a threshold is exceeded.<br>
     * Calculates the count from getCurrentCodeOffset - savedCodeOffset
     */
    private void addInstructionCount() {
        int count = cfw.getCurrentCodeOffset() - savedCodeOffset;
        // TODO we used to return for count == 0 but that broke the following:
        //    while(true) continue; (see bug 531600)
        // To be safe, we now always count at least 1 instruction when invoked.
        addInstructionCount(Math.max(count, 1));
    }

    /**
     * Generate calls to ScriptRuntime.addInstructionCount to keep track of
     * executed instructions and call <code>observeInstructionCount()</code>
     * if a threshold is exceeded.<br>
     * Takes the count as a parameter - used to add monitoring to loops and
     * other blocks that don't have any ops - this allows
     * for monitoring/killing of while(true) loops and such.
     */
    private void addInstructionCount(int count) {
        cfw.addALoad(contextLocal);
        cfw.addPush(count);
        addScriptRuntimeInvoke("addInstructionCount",
                "(Lorg/mozilla/javascript/Context;"
                +"I)V");
    }

    private void visitIncDec(Node node)
    {
        int incrDecrMask = node.getExistingIntProp(Node.INCRDECR_PROP);
        Node child = node.getFirstChild();
        switch (child.getType()) {
          case Token.GETVAR:
            if (!hasVarsInRegs) Kit.codeBug();
            boolean post = ((incrDecrMask & Node.POST_FLAG) != 0);
            int varIndex = fnCurrent.getVarIndex(child);
            short reg = varRegisters[varIndex];
            if (node.getIntProp(Node.ISNUMBER_PROP, -1) != -1) {
                int offset = varIsDirectCallParameter(varIndex) ? 1 : 0;
                cfw.addDLoad(reg + offset);
                if (post) {
                    cfw.add(ByteCode.DUP2);
                }
                cfw.addPush(1.0);
                if ((incrDecrMask & Node.DECR_FLAG) == 0) {
                    cfw.add(ByteCode.DADD);
                } else {
                    cfw.add(ByteCode.DSUB);
                }
                if (!post) {
                    cfw.add(ByteCode.DUP2);
                }
                cfw.addDStore(reg + offset);
            } else {
                if (varIsDirectCallParameter(varIndex)) {
                    dcpLoadAsObject(reg);
                } else {
                    cfw.addALoad(reg);
                }
                if (post) {
                    cfw.add(ByteCode.DUP);
                }
                addObjectToDouble();
                cfw.addPush(1.0);
                if ((incrDecrMask & Node.DECR_FLAG) == 0) {
                    cfw.add(ByteCode.DADD);
                } else {
                    cfw.add(ByteCode.DSUB);
                }
                addDoubleWrap();
                if (!post) {
                    cfw.add(ByteCode.DUP);
                }
                cfw.addAStore(reg);
                break;
            }
            break;
          case Token.NAME:
            cfw.addALoad(variableObjectLocal);
            cfw.addPush(child.getString());          // push name
            cfw.addALoad(contextLocal);
            cfw.addPush(incrDecrMask);
            addScriptRuntimeInvoke("nameIncrDecr",
                "(Lorg/mozilla/javascript/Scriptable;"
                +"Ljava/lang/String;"
                +"Lorg/mozilla/javascript/Context;"
                +"I)Ljava/lang/Object;");
            break;
          case Token.GETPROPNOWARN:
            throw Kit.codeBug();
          case Token.GETPROP: {
            Node getPropChild = child.getFirstChild();
            generateExpression(getPropChild, node);
            generateExpression(getPropChild.getNext(), node);
            cfw.addALoad(contextLocal);
            cfw.addPush(incrDecrMask);
            addScriptRuntimeInvoke("propIncrDecr",
                                   "(Ljava/lang/Object;"
                                   +"Ljava/lang/String;"
                                   +"Lorg/mozilla/javascript/Context;"
                                   +"I)Ljava/lang/Object;");
            break;
          }
          case Token.GETELEM: {
            Node elemChild = child.getFirstChild();
            generateExpression(elemChild, node);
            generateExpression(elemChild.getNext(), node);
            cfw.addALoad(contextLocal);
            cfw.addPush(incrDecrMask);
            if (elemChild.getNext().getIntProp(Node.ISNUMBER_PROP, -1) != -1) {
              addOptRuntimeInvoke("elemIncrDecr",
                  "(Ljava/lang/Object;"
                  +"D"
                  +"Lorg/mozilla/javascript/Context;"
                  +"I"
                  +")Ljava/lang/Object;");
            } else {
              addScriptRuntimeInvoke("elemIncrDecr",
                  "(Ljava/lang/Object;"
                  +"Ljava/lang/Object;"
                  +"Lorg/mozilla/javascript/Context;"
                  +"I"
                  +")Ljava/lang/Object;");
            }
            break;
          }
          case Token.GET_REF: {
            Node refChild = child.getFirstChild();
            generateExpression(refChild, node);
            cfw.addALoad(contextLocal);
            cfw.addPush(incrDecrMask);
            addScriptRuntimeInvoke(
                "refIncrDecr",
                "(Lorg/mozilla/javascript/Ref;"
                +"Lorg/mozilla/javascript/Context;"
                +"I)Ljava/lang/Object;");
            break;
          }
          default:
            Codegen.badTree();
        }
    }

    private static boolean isArithmeticNode(Node node)
    {
        int type = node.getType();
        return (type == Token.SUB)
                  || (type == Token.MOD)
                        || (type == Token.DIV)
                              || (type == Token.MUL);
    }

    private void visitArithmetic(Node node, int opCode, Node child,
                                 Node parent)
    {
        int childNumberFlag = node.getIntProp(Node.ISNUMBER_PROP, -1);
        if (childNumberFlag != -1) {
            generateExpression(child, node);
            generateExpression(child.getNext(), node);
            cfw.add(opCode);
        }
        else {
            boolean childOfArithmetic = isArithmeticNode(parent);
            generateExpression(child, node);
            if (!isArithmeticNode(child))
                addObjectToDouble();
            generateExpression(child.getNext(), node);
            if (!isArithmeticNode(child.getNext()))
                  addObjectToDouble();
            cfw.add(opCode);
            if (!childOfArithmetic) {
                addDoubleWrap();
            }
        }
    }

    private void visitBitOp(Node node, int type, Node child)
    {
        int childNumberFlag = node.getIntProp(Node.ISNUMBER_PROP, -1);
        generateExpression(child, node);

        // special-case URSH; work with the target arg as a long, so
        // that we can return a 32-bit unsigned value, and call
        // toUint32 instead of toInt32.
        if (type == Token.URSH) {
            addScriptRuntimeInvoke("toUint32", "(Ljava/lang/Object;)J");
            generateExpression(child.getNext(), node);
            addScriptRuntimeInvoke("toInt32", "(Ljava/lang/Object;)I");
            // Looks like we need to explicitly mask the shift to 5 bits -
            // LUSHR takes 6 bits.
            cfw.addPush(31);
            cfw.add(ByteCode.IAND);
            cfw.add(ByteCode.LUSHR);
            cfw.add(ByteCode.L2D);
            addDoubleWrap();
            return;
        }
        if (childNumberFlag == -1) {
            addScriptRuntimeInvoke("toInt32", "(Ljava/lang/Object;)I");
            generateExpression(child.getNext(), node);
            addScriptRuntimeInvoke("toInt32", "(Ljava/lang/Object;)I");
        }
        else {
            addScriptRuntimeInvoke("toInt32", "(D)I");
            generateExpression(child.getNext(), node);
            addScriptRuntimeInvoke("toInt32", "(D)I");
        }
        switch (type) {
          case Token.BITOR:
            cfw.add(ByteCode.IOR);
            break;
          case Token.BITXOR:
            cfw.add(ByteCode.IXOR);
            break;
          case Token.BITAND:
            cfw.add(ByteCode.IAND);
            break;
          case Token.RSH:
            cfw.add(ByteCode.ISHR);
            break;
          case Token.LSH:
            cfw.add(ByteCode.ISHL);
            break;
          default:
            throw Codegen.badTree();
        }
        cfw.add(ByteCode.I2D);
        if (childNumberFlag == -1) {
            addDoubleWrap();
        }
    }

    private int nodeIsDirectCallParameter(Node node)
    {
        if (node.getType() == Token.GETVAR
            && inDirectCallFunction && !itsForcedObjectParameters)
        {
            int varIndex = fnCurrent.getVarIndex(node);
            if (fnCurrent.isParameter(varIndex)) {
                return varRegisters[varIndex];
            }
        }
        return -1;
    }

    private boolean varIsDirectCallParameter(int varIndex)
    {
        return fnCurrent.isParameter(varIndex)
            && inDirectCallFunction && !itsForcedObjectParameters;
    }

    private void genSimpleCompare(int type, int trueGOTO, int falseGOTO)
    {
        if (trueGOTO == -1) throw Codegen.badTree();
        switch (type) {
            case Token.LE :
                cfw.add(ByteCode.DCMPG);
                cfw.add(ByteCode.IFLE, trueGOTO);
                break;
            case Token.GE :
                cfw.add(ByteCode.DCMPL);
                cfw.add(ByteCode.IFGE, trueGOTO);
                break;
            case Token.LT :
                cfw.add(ByteCode.DCMPG);
                cfw.add(ByteCode.IFLT, trueGOTO);
                break;
            case Token.GT :
                cfw.add(ByteCode.DCMPL);
                cfw.add(ByteCode.IFGT, trueGOTO);
                break;
            default :
                throw Codegen.badTree();

        }
        if (falseGOTO != -1)
            cfw.add(ByteCode.GOTO, falseGOTO);
    }

    private void visitIfJumpRelOp(Node node, Node child,
                                  int trueGOTO, int falseGOTO)
    {
        if (trueGOTO == -1 || falseGOTO == -1) throw Codegen.badTree();
        int type = node.getType();
        Node rChild = child.getNext();
        if (type == Token.INSTANCEOF || type == Token.IN) {
            generateExpression(child, node);
            generateExpression(rChild, node);
            cfw.addALoad(contextLocal);
            addScriptRuntimeInvoke(
                (type == Token.INSTANCEOF) ? "instanceOf" : "in",
                "(Ljava/lang/Object;"
                +"Ljava/lang/Object;"
                +"Lorg/mozilla/javascript/Context;"
                +")Z");
            cfw.add(ByteCode.IFNE, trueGOTO);
            cfw.add(ByteCode.GOTO, falseGOTO);
            return;
        }
        int childNumberFlag = node.getIntProp(Node.ISNUMBER_PROP, -1);
        int left_dcp_register = nodeIsDirectCallParameter(child);
        int right_dcp_register = nodeIsDirectCallParameter(rChild);
        if (childNumberFlag != -1) {
            // Force numeric context on both parameters and optimize
            // direct call case as Optimizer currently does not handle it

            if (childNumberFlag != Node.RIGHT) {
                // Left already has number content
                generateExpression(child, node);
            } else if (left_dcp_register != -1) {
                dcpLoadAsNumber(left_dcp_register);
            } else {
                generateExpression(child, node);
                addObjectToDouble();
            }

            if (childNumberFlag != Node.LEFT) {
                // Right already has number content
                generateExpression(rChild, node);
            } else if (right_dcp_register != -1) {
                dcpLoadAsNumber(right_dcp_register);
            } else {
                generateExpression(rChild, node);
                addObjectToDouble();
            }

            genSimpleCompare(type, trueGOTO, falseGOTO);

        } else {
            if (left_dcp_register != -1 && right_dcp_register != -1) {
                // Generate code to dynamically check for number content
                // if both operands are dcp
                short stack = cfw.getStackTop();
                int leftIsNotNumber = cfw.acquireLabel();
                cfw.addALoad(left_dcp_register);
                cfw.add(ByteCode.GETSTATIC,
                        "java/lang/Void",
                        "TYPE",
                        "Ljava/lang/Class;");
                cfw.add(ByteCode.IF_ACMPNE, leftIsNotNumber);
                cfw.addDLoad(left_dcp_register + 1);
                dcpLoadAsNumber(right_dcp_register);
                genSimpleCompare(type, trueGOTO, falseGOTO);
                if (stack != cfw.getStackTop()) throw Codegen.badTree();

                cfw.markLabel(leftIsNotNumber);
                int rightIsNotNumber = cfw.acquireLabel();
                cfw.addALoad(right_dcp_register);
                cfw.add(ByteCode.GETSTATIC,
                        "java/lang/Void",
                        "TYPE",
                        "Ljava/lang/Class;");
                cfw.add(ByteCode.IF_ACMPNE, rightIsNotNumber);
                cfw.addALoad(left_dcp_register);
                addObjectToDouble();
                cfw.addDLoad(right_dcp_register + 1);
                genSimpleCompare(type, trueGOTO, falseGOTO);
                if (stack != cfw.getStackTop()) throw Codegen.badTree();

                cfw.markLabel(rightIsNotNumber);
                // Load both register as objects to call generic cmp_*
                cfw.addALoad(left_dcp_register);
                cfw.addALoad(right_dcp_register);

            } else {
                generateExpression(child, node);
                generateExpression(rChild, node);
            }

            if (type == Token.GE || type == Token.GT) {
                cfw.add(ByteCode.SWAP);
            }
            String routine = ((type == Token.LT)
                      || (type == Token.GT)) ? "cmp_LT" : "cmp_LE";
            addScriptRuntimeInvoke(routine,
                                   "(Ljava/lang/Object;"
                                   +"Ljava/lang/Object;"
                                   +")Z");
            cfw.add(ByteCode.IFNE, trueGOTO);
            cfw.add(ByteCode.GOTO, falseGOTO);
        }
    }

    private void visitIfJumpEqOp(Node node, Node child,
                                 int trueGOTO, int falseGOTO)
    {
        if (trueGOTO == -1 || falseGOTO == -1) throw Codegen.badTree();

        short stackInitial = cfw.getStackTop();
        int type = node.getType();
        Node rChild = child.getNext();

        // Optimize if one of operands is null
        if (child.getType() == Token.NULL || rChild.getType() == Token.NULL) {
            // eq is symmetric in this case
            if (child.getType() == Token.NULL) {
                child = rChild;
            }
            generateExpression(child, node);
            if (type == Token.SHEQ || type == Token.SHNE) {
                int testCode = (type == Token.SHEQ)
                                ? ByteCode.IFNULL : ByteCode.IFNONNULL;
                cfw.add(testCode, trueGOTO);
            } else {
                if (type != Token.EQ) {
                    // swap false/true targets for !=
                    if (type != Token.NE) throw Codegen.badTree();
                    int tmp = trueGOTO;
                    trueGOTO = falseGOTO;
                    falseGOTO = tmp;
                }
                cfw.add(ByteCode.DUP);
                int undefCheckLabel = cfw.acquireLabel();
                cfw.add(ByteCode.IFNONNULL, undefCheckLabel);
                short stack = cfw.getStackTop();
                cfw.add(ByteCode.POP);
                cfw.add(ByteCode.GOTO, trueGOTO);
                cfw.markLabel(undefCheckLabel, stack);
                Codegen.pushUndefined(cfw);
                cfw.add(ByteCode.IF_ACMPEQ, trueGOTO);
            }
            cfw.add(ByteCode.GOTO, falseGOTO);
        } else {
            int child_dcp_register = nodeIsDirectCallParameter(child);
            if (child_dcp_register != -1
                && rChild.getType() == Token.TO_OBJECT)
            {
                Node convertChild = rChild.getFirstChild();
                if (convertChild.getType() == Token.NUMBER) {
                    cfw.addALoad(child_dcp_register);
                    cfw.add(ByteCode.GETSTATIC,
                            "java/lang/Void",
                            "TYPE",
                            "Ljava/lang/Class;");
                    int notNumbersLabel = cfw.acquireLabel();
                    cfw.add(ByteCode.IF_ACMPNE, notNumbersLabel);
                    cfw.addDLoad(child_dcp_register + 1);
                    cfw.addPush(convertChild.getDouble());
                    cfw.add(ByteCode.DCMPL);
                    if (type == Token.EQ)
                        cfw.add(ByteCode.IFEQ, trueGOTO);
                    else
                        cfw.add(ByteCode.IFNE, trueGOTO);
                    cfw.add(ByteCode.GOTO, falseGOTO);
                    cfw.markLabel(notNumbersLabel);
                    // fall thru into generic handling
                }
            }

            generateExpression(child, node);
            generateExpression(rChild, node);

            String name;
            int testCode;
            switch (type) {
              case Token.EQ:
                name = "eq";
                testCode = ByteCode.IFNE;
                break;
              case Token.NE:
                name = "eq";
                testCode = ByteCode.IFEQ;
                break;
              case Token.SHEQ:
                name = "shallowEq";
                testCode = ByteCode.IFNE;
                break;
              case Token.SHNE:
                name = "shallowEq";
                testCode = ByteCode.IFEQ;
                break;
              default:
                throw Codegen.badTree();
            }
            addScriptRuntimeInvoke(name,
                                   "(Ljava/lang/Object;"
                                   +"Ljava/lang/Object;"
                                   +")Z");
            cfw.add(testCode, trueGOTO);
            cfw.add(ByteCode.GOTO, falseGOTO);
        }
        if (stackInitial != cfw.getStackTop()) throw Codegen.badTree();
    }

    private void visitSetName(Node node, Node child)
    {
        String name = node.getFirstChild().getString();
        while (child != null) {
            generateExpression(child, node);
            child = child.getNext();
        }
        cfw.addALoad(contextLocal);
        cfw.addALoad(variableObjectLocal);
        cfw.addPush(name);
        addScriptRuntimeInvoke(
            "setName",
            "(Lorg/mozilla/javascript/Scriptable;"
            +"Ljava/lang/Object;"
            +"Lorg/mozilla/javascript/Context;"
            +"Lorg/mozilla/javascript/Scriptable;"
            +"Ljava/lang/String;"
            +")Ljava/lang/Object;");
    }

    private void visitStrictSetName(Node node, Node child)
    {
        String name = node.getFirstChild().getString();
        while (child != null) {
            generateExpression(child, node);
            child = child.getNext();
        }
        cfw.addALoad(contextLocal);
        cfw.addALoad(variableObjectLocal);
        cfw.addPush(name);
        addScriptRuntimeInvoke(
            "strictSetName",
            "(Lorg/mozilla/javascript/Scriptable;"
            +"Ljava/lang/Object;"
            +"Lorg/mozilla/javascript/Context;"
            +"Lorg/mozilla/javascript/Scriptable;"
            +"Ljava/lang/String;"
            +")Ljava/lang/Object;");
    }

    private void visitSetConst(Node node, Node child)
    {
        String name = node.getFirstChild().getString();
        while (child != null) {
            generateExpression(child, node);
            child = child.getNext();
        }
        cfw.addALoad(contextLocal);
        cfw.addPush(name);
        addScriptRuntimeInvoke(
            "setConst",
            "(Lorg/mozilla/javascript/Scriptable;"
            +"Ljava/lang/Object;"
            +"Lorg/mozilla/javascript/Context;"
            +"Ljava/lang/String;"
            +")Ljava/lang/Object;");
    }

    private void visitGetVar(Node node)
    {
        if (!hasVarsInRegs) Kit.codeBug();
        int varIndex = fnCurrent.getVarIndex(node);
        short reg = varRegisters[varIndex];
        if (varIsDirectCallParameter(varIndex)) {
            // Remember that here the isNumber flag means that we
            // want to use the incoming parameter in a Number
            // context, so test the object type and convert the
            //  value as necessary.
            if (node.getIntProp(Node.ISNUMBER_PROP, -1) != -1) {
                dcpLoadAsNumber(reg);
            } else {
                dcpLoadAsObject(reg);
            }
        } else if (fnCurrent.isNumberVar(varIndex)) {
            cfw.addDLoad(reg);
        } else {
            cfw.addALoad(reg);
        }
    }

    private void visitSetVar(Node node, Node child, boolean needValue)
    {
        if (!hasVarsInRegs) Kit.codeBug();
        int varIndex = fnCurrent.getVarIndex(node);
        generateExpression(child.getNext(), node);
        boolean isNumber = (node.getIntProp(Node.ISNUMBER_PROP, -1) != -1);
        short reg = varRegisters[varIndex];
        boolean [] constDeclarations = fnCurrent.fnode.getParamAndVarConst();
        if (constDeclarations[varIndex]) {
            if (!needValue) {
                if (isNumber)
                    cfw.add(ByteCode.POP2);
                else
                    cfw.add(ByteCode.POP);
            }
        }
        else if (varIsDirectCallParameter(varIndex)) {
            if (isNumber) {
                if (needValue) cfw.add(ByteCode.DUP2);
                cfw.addALoad(reg);
                cfw.add(ByteCode.GETSTATIC,
                        "java/lang/Void",
                        "TYPE",
                        "Ljava/lang/Class;");
                int isNumberLabel = cfw.acquireLabel();
                int beyond = cfw.acquireLabel();
                cfw.add(ByteCode.IF_ACMPEQ, isNumberLabel);
                short stack = cfw.getStackTop();
                addDoubleWrap();
                cfw.addAStore(reg);
                cfw.add(ByteCode.GOTO, beyond);
                cfw.markLabel(isNumberLabel, stack);
                cfw.addDStore(reg + 1);
                cfw.markLabel(beyond);
            }
            else {
                if (needValue) cfw.add(ByteCode.DUP);
                cfw.addAStore(reg);
            }
        } else {
            boolean isNumberVar = fnCurrent.isNumberVar(varIndex);
            if (isNumber) {
                if (isNumberVar) {
                    cfw.addDStore(reg);
                    if (needValue) cfw.addDLoad(reg);
                } else {
                    if (needValue) cfw.add(ByteCode.DUP2);
                    // Cannot save number in variable since !isNumberVar,
                    // so convert to object
                    addDoubleWrap();
                    cfw.addAStore(reg);
                }
            } else {
                if (isNumberVar) Kit.codeBug();
                cfw.addAStore(reg);
                if (needValue) cfw.addALoad(reg);
            }
        }
    }

    private void visitSetConstVar(Node node, Node child, boolean needValue)
    {
        if (!hasVarsInRegs) Kit.codeBug();
        int varIndex = fnCurrent.getVarIndex(node);
        generateExpression(child.getNext(), node);
        boolean isNumber = (node.getIntProp(Node.ISNUMBER_PROP, -1) != -1);
        short reg = varRegisters[varIndex];
        int beyond = cfw.acquireLabel();
        int noAssign = cfw.acquireLabel();
        if (isNumber) {
            cfw.addILoad(reg + 2);
            cfw.add(ByteCode.IFNE, noAssign);
            short stack = cfw.getStackTop();
            cfw.addPush(1);
            cfw.addIStore(reg + 2);
            cfw.addDStore(reg);
            if (needValue) {
                cfw.addDLoad(reg);
                cfw.markLabel(noAssign, stack);
            } else {
                cfw.add(ByteCode.GOTO, beyond);
                cfw.markLabel(noAssign, stack);
                cfw.add(ByteCode.POP2);
            }
        }
        else {
            cfw.addILoad(reg + 1);
            cfw.add(ByteCode.IFNE, noAssign);
            short stack = cfw.getStackTop();
            cfw.addPush(1);
            cfw.addIStore(reg + 1);
            cfw.addAStore(reg);
            if (needValue) {
                cfw.addALoad(reg);
                cfw.markLabel(noAssign, stack);
            } else {
                cfw.add(ByteCode.GOTO, beyond);
                cfw.markLabel(noAssign, stack);
                cfw.add(ByteCode.POP);
            }
        }
        cfw.markLabel(beyond);
    }

    private void visitGetProp(Node node, Node child)
    {
        generateExpression(child, node); // object
        Node nameChild = child.getNext();
        generateExpression(nameChild, node);  // the name
        if (node.getType() == Token.GETPROPNOWARN) {
            cfw.addALoad(contextLocal);
            addScriptRuntimeInvoke(
                "getObjectPropNoWarn",
                "(Ljava/lang/Object;"
                +"Ljava/lang/String;"
                +"Lorg/mozilla/javascript/Context;"
                +")Ljava/lang/Object;");
            return;
        }
        /*
            for 'this.foo' we call getObjectProp(Scriptable...) which can
            skip some casting overhead.
        */
        int childType = child.getType();
        if (childType == Token.THIS && nameChild.getType() == Token.STRING) {
            cfw.addALoad(contextLocal);
            addScriptRuntimeInvoke(
                "getObjectProp",
                "(Lorg/mozilla/javascript/Scriptable;"
                +"Ljava/lang/String;"
                +"Lorg/mozilla/javascript/Context;"
                +")Ljava/lang/Object;");
        } else {
            cfw.addALoad(contextLocal);
            cfw.addALoad(variableObjectLocal);
            addScriptRuntimeInvoke(
                "getObjectProp",
                "(Ljava/lang/Object;"
                +"Ljava/lang/String;"
                +"Lorg/mozilla/javascript/Context;"
                +"Lorg/mozilla/javascript/Scriptable;"
                +")Ljava/lang/Object;");
        }
    }

    private void visitSetProp(int type, Node node, Node child)
    {
        Node objectChild = child;
        generateExpression(child, node);
        child = child.getNext();
        if (type == Token.SETPROP_OP) {
            cfw.add(ByteCode.DUP);
        }
        Node nameChild = child;
        generateExpression(child, node);
        child = child.getNext();
        if (type == Token.SETPROP_OP) {
            // stack: ... object object name -> ... object name object name
            cfw.add(ByteCode.DUP_X1);
            //for 'this.foo += ...' we call thisGet which can skip some
            //casting overhead.
            if (objectChild.getType() == Token.THIS
                && nameChild.getType() == Token.STRING)
            {
                cfw.addALoad(contextLocal);
                addScriptRuntimeInvoke(
                    "getObjectProp",
                    "(Lorg/mozilla/javascript/Scriptable;"
                    +"Ljava/lang/String;"
                    +"Lorg/mozilla/javascript/Context;"
                    +")Ljava/lang/Object;");
            } else {
                cfw.addALoad(contextLocal);
                addScriptRuntimeInvoke(
                    "getObjectProp",
                    "(Ljava/lang/Object;"
                    +"Ljava/lang/String;"
                    +"Lorg/mozilla/javascript/Context;"
                    +")Ljava/lang/Object;");
            }
        }
        generateExpression(child, node);
        cfw.addALoad(contextLocal);
        addScriptRuntimeInvoke(
            "setObjectProp",
            "(Ljava/lang/Object;"
            +"Ljava/lang/String;"
            +"Ljava/lang/Object;"
            +"Lorg/mozilla/javascript/Context;"
            +")Ljava/lang/Object;");
    }

    private void visitSetElem(int type, Node node, Node child)
    {
        generateExpression(child, node);
        child = child.getNext();
        if (type == Token.SETELEM_OP) {
            cfw.add(ByteCode.DUP);
        }
        generateExpression(child, node);
        child = child.getNext();
        boolean indexIsNumber = (node.getIntProp(Node.ISNUMBER_PROP, -1) != -1);
        if (type == Token.SETELEM_OP) {
            if (indexIsNumber) {
                // stack: ... object object number
                //        -> ... object number object number
                cfw.add(ByteCode.DUP2_X1);
                cfw.addALoad(contextLocal);
                addOptRuntimeInvoke(
                    "getObjectIndex",
                    "(Ljava/lang/Object;D"
                    +"Lorg/mozilla/javascript/Context;"
                    +")Ljava/lang/Object;");
            } else {
                // stack: ... object object indexObject
                //        -> ... object indexObject object indexObject
                cfw.add(ByteCode.DUP_X1);
                cfw.addALoad(contextLocal);
                addScriptRuntimeInvoke(
                    "getObjectElem",
                    "(Ljava/lang/Object;"
                    +"Ljava/lang/Object;"
                    +"Lorg/mozilla/javascript/Context;"
                    +")Ljava/lang/Object;");
            }
        }
        generateExpression(child, node);
        cfw.addALoad(contextLocal);
        if (indexIsNumber) {
            addScriptRuntimeInvoke(
                "setObjectIndex",
                "(Ljava/lang/Object;"
                +"D"
                +"Ljava/lang/Object;"
                +"Lorg/mozilla/javascript/Context;"
                +")Ljava/lang/Object;");
        } else {
            addScriptRuntimeInvoke(
                "setObjectElem",
                "(Ljava/lang/Object;"
                +"Ljava/lang/Object;"
                +"Ljava/lang/Object;"
                +"Lorg/mozilla/javascript/Context;"
                +")Ljava/lang/Object;");
        }
    }

    private void visitDotQuery(Node node, Node child)
    {
        updateLineNumber(node);
        generateExpression(child, node);
        cfw.addALoad(variableObjectLocal);
        addScriptRuntimeInvoke("enterDotQuery",
                               "(Ljava/lang/Object;"
                               +"Lorg/mozilla/javascript/Scriptable;"
                               +")Lorg/mozilla/javascript/Scriptable;");
        cfw.addAStore(variableObjectLocal);

        // add push null/pop with label in between to simplify code for loop
        // continue when it is necessary to pop the null result from
        // updateDotQuery
        cfw.add(ByteCode.ACONST_NULL);
        int queryLoopStart = cfw.acquireLabel();
        cfw.markLabel(queryLoopStart); // loop continue jumps here
        cfw.add(ByteCode.POP);

        generateExpression(child.getNext(), node);
        addScriptRuntimeInvoke("toBoolean", "(Ljava/lang/Object;)Z");
        cfw.addALoad(variableObjectLocal);
        addScriptRuntimeInvoke("updateDotQuery",
                               "(Z"
                               +"Lorg/mozilla/javascript/Scriptable;"
                               +")Ljava/lang/Object;");
        cfw.add(ByteCode.DUP);
        cfw.add(ByteCode.IFNULL, queryLoopStart);
        // stack: ... non_null_result_of_updateDotQuery
        cfw.addALoad(variableObjectLocal);
        addScriptRuntimeInvoke("leaveDotQuery",
                               "(Lorg/mozilla/javascript/Scriptable;"
                               +")Lorg/mozilla/javascript/Scriptable;");
        cfw.addAStore(variableObjectLocal);
    }

    private int getLocalBlockRegister(Node node)
    {
        Node localBlock = (Node)node.getProp(Node.LOCAL_BLOCK_PROP);
        int localSlot = localBlock.getExistingIntProp(Node.LOCAL_PROP);
        return localSlot;
    }

    private void dcpLoadAsNumber(int dcp_register)
    {
        cfw.addALoad(dcp_register);
        cfw.add(ByteCode.GETSTATIC,
                "java/lang/Void",
                "TYPE",
                "Ljava/lang/Class;");
        int isNumberLabel = cfw.acquireLabel();
        cfw.add(ByteCode.IF_ACMPEQ, isNumberLabel);
        short stack = cfw.getStackTop();
        cfw.addALoad(dcp_register);
        addObjectToDouble();
        int beyond = cfw.acquireLabel();
        cfw.add(ByteCode.GOTO, beyond);
        cfw.markLabel(isNumberLabel, stack);
        cfw.addDLoad(dcp_register + 1);
        cfw.markLabel(beyond);
    }

    private void dcpLoadAsObject(int dcp_register)
    {
        cfw.addALoad(dcp_register);
        cfw.add(ByteCode.GETSTATIC,
                "java/lang/Void",
                "TYPE",
                "Ljava/lang/Class;");
        int isNumberLabel = cfw.acquireLabel();
        cfw.add(ByteCode.IF_ACMPEQ, isNumberLabel);
        short stack = cfw.getStackTop();
        cfw.addALoad(dcp_register);
        int beyond = cfw.acquireLabel();
        cfw.add(ByteCode.GOTO, beyond);
        cfw.markLabel(isNumberLabel, stack);
        cfw.addDLoad(dcp_register + 1);
        addDoubleWrap();
        cfw.markLabel(beyond);
    }

    private void addGoto(Node target, int jumpcode)
    {
        int targetLabel = getTargetLabel(target);
        cfw.add(jumpcode, targetLabel);
    }

    private void addObjectToDouble()
    {
        addScriptRuntimeInvoke("toNumber", "(Ljava/lang/Object;)D");
    }

    private void addNewObjectArray(int size)
    {
        if (size == 0) {
            if (itsZeroArgArray >= 0) {
                cfw.addALoad(itsZeroArgArray);
            } else {
                cfw.add(ByteCode.GETSTATIC,
                        "org/mozilla/javascript/ScriptRuntime",
                        "emptyArgs", "[Ljava/lang/Object;");
            }
        } else {
            cfw.addPush(size);
            cfw.add(ByteCode.ANEWARRAY, "java/lang/Object");
        }
    }

    private void addScriptRuntimeInvoke(String methodName,
                                        String methodSignature)
    {
        cfw.addInvoke(ByteCode.INVOKESTATIC,
                      "org.mozilla.javascript.ScriptRuntime",
                      methodName,
                      methodSignature);
    }

    private void addOptRuntimeInvoke(String methodName,
                                     String methodSignature)
    {
        cfw.addInvoke(ByteCode.INVOKESTATIC,
                      "org/mozilla/javascript/optimizer/OptRuntime",
                      methodName,
                      methodSignature);
    }

    private void addJumpedBooleanWrap(int trueLabel, int falseLabel)
    {
        cfw.markLabel(falseLabel);
        int skip = cfw.acquireLabel();
        cfw.add(ByteCode.GETSTATIC, "java/lang/Boolean",
                                "FALSE", "Ljava/lang/Boolean;");
        cfw.add(ByteCode.GOTO, skip);
        cfw.markLabel(trueLabel);
        cfw.add(ByteCode.GETSTATIC, "java/lang/Boolean",
                                "TRUE", "Ljava/lang/Boolean;");
        cfw.markLabel(skip);
        cfw.adjustStackTop(-1);   // only have 1 of true/false
    }

    private void addDoubleWrap()
    {
        addOptRuntimeInvoke("wrapDouble", "(D)Ljava/lang/Double;");
    }

    /**
     * Const locals use an extra slot to hold the has-been-assigned-once flag at
     * runtime.
     * @param isConst true iff the variable is const
     * @return the register for the word pair (double/long)
     */
    private short getNewWordPairLocal(boolean isConst)
    {
        short result = getConsecutiveSlots(2, isConst);
        if (result < (MAX_LOCALS - 1)) {
            locals[result] = 1;
            locals[result + 1] = 1;
            if (isConst)
                locals[result + 2] = 1;
            if (result == firstFreeLocal) {
                for (int i = firstFreeLocal + 2; i < MAX_LOCALS; i++) {
                    if (locals[i] == 0) {
                        firstFreeLocal = (short) i;
                        if (localsMax < firstFreeLocal)
                            localsMax = firstFreeLocal;
                        return result;
                    }
                }
            }
            else {
                return result;
            }
        }
        throw Context.reportRuntimeError("Program too complex " +
                                         "(out of locals)");
    }

    private short getNewWordLocal(boolean isConst)
    {
        short result = getConsecutiveSlots(1, isConst);
        if (result < (MAX_LOCALS - 1)) {
            locals[result] = 1;
            if (isConst)
                locals[result + 1] = 1;
            if (result == firstFreeLocal) {
                for (int i = firstFreeLocal + 2; i < MAX_LOCALS; i++) {
                    if (locals[i] == 0) {
                        firstFreeLocal = (short) i;
                        if (localsMax < firstFreeLocal)
                            localsMax = firstFreeLocal;
                        return result;
                    }
                }
            }
            else {
                return result;
            }
        }
        throw Context.reportRuntimeError("Program too complex " +
                                         "(out of locals)");
    }

    private short getNewWordLocal()
    {
        short result = firstFreeLocal;
        locals[result] = 1;
        for (int i = firstFreeLocal + 1; i < MAX_LOCALS; i++) {
            if (locals[i] == 0) {
                firstFreeLocal = (short) i;
                if (localsMax < firstFreeLocal)
                    localsMax = firstFreeLocal;
                return result;
            }
        }
        throw Context.reportRuntimeError("Program too complex " +
                                         "(out of locals)");
    }

    private short getConsecutiveSlots(int count, boolean isConst) {
        if (isConst)
            count++;
        short result = firstFreeLocal;
        while (true) {
            if (result >= (MAX_LOCALS - 1))
                break;
            int i;
            for (i = 0; i < count; i++)
                if (locals[result + i] != 0)
                    break;
            if (i >= count)
                break;
            result++;
        }
        return result;
    }

    // This is a valid call only for a local that is allocated by default.
    private void incReferenceWordLocal(short local)
    {
        locals[local]++;
    }

    // This is a valid call only for a local that is allocated by default.
    private void decReferenceWordLocal(short local)
    {
        locals[local]--;
    }

    private void releaseWordLocal(short local)
    {
        if (local < firstFreeLocal)
            firstFreeLocal = local;
        locals[local] = 0;
    }


    static final int GENERATOR_TERMINATE = -1;
    static final int GENERATOR_START = 0;
    static final int GENERATOR_YIELD_START = 1;

    ClassFileWriter cfw;
    Codegen codegen;
    CompilerEnvirons compilerEnv;
    ScriptNode scriptOrFn;
    public int scriptOrFnIndex;
    private int savedCodeOffset;

    private OptFunctionNode fnCurrent;

    private static final int MAX_LOCALS = 1024;
    private int[] locals;
    private short firstFreeLocal;
    private short localsMax;

    private int itsLineNumber;

    private boolean hasVarsInRegs;
    private short[] varRegisters;
    private boolean inDirectCallFunction;
    private boolean itsForcedObjectParameters;
    private int enterAreaStartLabel;
    private int epilogueLabel;
    private boolean inLocalBlock;

    // special known locals. If you add a new local here, be sure
    // to initialize it to -1 in initBodyGeneration
    private short variableObjectLocal;
    private short popvLocal;
    private short contextLocal;
    private short argsLocal;
    private short operationLocal;
    private short thisObjLocal;
    private short funObjLocal;
    private short itsZeroArgArray;
    private short itsOneArgArray;
    private short generatorStateLocal;

    private boolean isGenerator;
    private int generatorSwitch;
    private int maxLocals = 0;
    private int maxStack = 0;

    private Map<Node,FinallyReturnPoint> finallys;
    private List<Node> literals;

    static class FinallyReturnPoint {
        public List<Integer> jsrPoints  = new ArrayList<Integer>();
        public int tableLabel = 0;
    }
}
