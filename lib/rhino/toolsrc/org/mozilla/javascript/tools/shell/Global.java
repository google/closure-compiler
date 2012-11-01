/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tools.shell;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.mozilla.javascript.*;
import org.mozilla.javascript.commonjs.module.Require;
import org.mozilla.javascript.commonjs.module.RequireBuilder;
import org.mozilla.javascript.commonjs.module.provider.SoftCachingModuleScriptProvider;
import org.mozilla.javascript.commonjs.module.provider.UrlModuleSourceProvider;
import org.mozilla.javascript.tools.ToolErrorReporter;
import org.mozilla.javascript.serialize.*;

/**
 * This class provides for sharing functions across multiple threads.
 * This is of particular interest to server applications.
 *
 */
public class Global extends ImporterTopLevel
{
    static final long serialVersionUID = 4029130780977538005L;

    NativeArray history;
    boolean attemptedJLineLoad;
    private ShellConsole console;
    private InputStream inStream;
    private PrintStream outStream;
    private PrintStream errStream;
    private boolean sealedStdLib = false;
    boolean initialized;
    private QuitAction quitAction;
    private String[] prompts = { "js> ", "  > " };
    private HashMap<String,String> doctestCanonicalizations;

    public Global()
    {
    }

    public Global(Context cx)
    {
        init(cx);
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Set the action to call from quit().
     */
    public void initQuitAction(QuitAction quitAction)
    {
        if (quitAction == null)
            throw new IllegalArgumentException("quitAction is null");
        if (this.quitAction != null)
            throw new IllegalArgumentException("The method is once-call.");

        this.quitAction = quitAction;
    }

    public void init(ContextFactory factory)
    {
        factory.call(new ContextAction() {
                public Object run(Context cx)
                {
                    init(cx);
                    return null;
                }
            });
    }

    public void init(Context cx)
    {
        // Define some global functions particular to the shell. Note
        // that these functions are not part of ECMA.
        initStandardObjects(cx, sealedStdLib);
        String[] names = {
            "defineClass",
            "deserialize",
            "doctest",
            "gc",
            "help",
            "load",
            "loadClass",
            "print",
            "quit",
            "readFile",
            "readUrl",
            "runCommand",
            "seal",
            "serialize",
            "spawn",
            "sync",
            "toint32",
            "version",
        };
        defineFunctionProperties(names, Global.class,
                                 ScriptableObject.DONTENUM);

        // Set up "environment" in the global scope to provide access to the
        // System environment variables.
        Environment.defineClass(this);
        Environment environment = new Environment(this);
        defineProperty("environment", environment,
                       ScriptableObject.DONTENUM);

        history = (NativeArray) cx.newArray(this, 0);
        defineProperty("history", history, ScriptableObject.DONTENUM);

        initialized = true;
    }

    public Require installRequire(Context cx, List<String> modulePath,
                                  boolean sandboxed) {
        RequireBuilder rb = new RequireBuilder();
        rb.setSandboxed(sandboxed);
        List<URI> uris = new ArrayList<URI>();
        if (modulePath != null) {
            for (String path : modulePath) {
                try {
                    URI uri = new URI(path);
                    if (!uri.isAbsolute()) {
                        // call resolve("") to canonify the path
                        uri = new File(path).toURI().resolve("");
                    }
                    if (!uri.toString().endsWith("/")) {
                        // make sure URI always terminates with slash to
                        // avoid loading from unintended locations
                        uri = new URI(uri + "/");
                    }
                    uris.add(uri);
                } catch (URISyntaxException usx) {
                    throw new RuntimeException(usx);
                }
            }
        }
        rb.setModuleScriptProvider(
                new SoftCachingModuleScriptProvider(
                        new UrlModuleSourceProvider(uris, null)));
        Require require = rb.createRequire(cx, this);
        require.install(this);
        return require;
    }

    /**
     * Print a help message.
     *
     * This method is defined as a JavaScript function.
     */
    public static void help(Context cx, Scriptable thisObj,
                            Object[] args, Function funObj)
    {
        PrintStream out = getInstance(funObj).getOut();
        out.println(ToolErrorReporter.getMessage("msg.help"));
    }

    public static void gc(Context cx, Scriptable thisObj,
            Object[] args, Function funObj)
    {
        System.gc();
    }


    /**
     * Print the string values of its arguments.
     *
     * This method is defined as a JavaScript function.
     * Note that its arguments are of the "varargs" form, which
     * allows it to handle an arbitrary number of arguments
     * supplied to the JavaScript function.
     *
     */
    public static Object print(Context cx, Scriptable thisObj,
                               Object[] args, Function funObj)
    {
        PrintStream out = getInstance(funObj).getOut();
        for (int i=0; i < args.length; i++) {
            if (i > 0)
                out.print(" ");

            // Convert the arbitrary JavaScript value into a string form.
            String s = Context.toString(args[i]);

            out.print(s);
        }
        out.println();
        return Context.getUndefinedValue();
    }

    /**
     * Call embedding-specific quit action passing its argument as
     * int32 exit code.
     *
     * This method is defined as a JavaScript function.
     */
    public static void quit(Context cx, Scriptable thisObj,
                            Object[] args, Function funObj)
    {
        Global global = getInstance(funObj);
        if (global.quitAction != null) {
            int exitCode = (args.length == 0 ? 0
                            : ScriptRuntime.toInt32(args[0]));
            global.quitAction.quit(cx, exitCode);
        }
    }

    /**
     * Get and set the language version.
     *
     * This method is defined as a JavaScript function.
     */
    public static double version(Context cx, Scriptable thisObj,
                                 Object[] args, Function funObj)
    {
        double result = cx.getLanguageVersion();
        if (args.length > 0) {
            double d = Context.toNumber(args[0]);
            cx.setLanguageVersion((int) d);
        }
        return result;
    }

    /**
     * Load and execute a set of JavaScript source files.
     *
     * This method is defined as a JavaScript function.
     *
     */
    public static void load(Context cx, Scriptable thisObj,
                            Object[] args, Function funObj)
    {
        for (Object arg : args) {
            String file = Context.toString(arg);
            try {
                Main.processFile(cx, thisObj, file);
            } catch (IOException ioex) {
                String msg = ToolErrorReporter.getMessage(
                        "msg.couldnt.read.source", file, ioex.getMessage());
                throw Context.reportRuntimeError(msg);
            } catch (VirtualMachineError ex) {
                // Treat StackOverflow and OutOfMemory as runtime errors
                ex.printStackTrace();
                String msg = ToolErrorReporter.getMessage(
                        "msg.uncaughtJSException", ex.toString());
                throw Context.reportRuntimeError(msg);
            }
        }
    }

    /**
     * Load a Java class that defines a JavaScript object using the
     * conventions outlined in ScriptableObject.defineClass.
     * <p>
     * This method is defined as a JavaScript function.
     * @exception IllegalAccessException if access is not available
     *            to a reflected class member
     * @exception InstantiationException if unable to instantiate
     *            the named class
     * @exception InvocationTargetException if an exception is thrown
     *            during execution of methods of the named class
     * @see org.mozilla.javascript.ScriptableObject#defineClass(Scriptable,Class)
     */
    @SuppressWarnings({"unchecked"})
    public static void defineClass(Context cx, Scriptable thisObj,
                                   Object[] args, Function funObj)
        throws IllegalAccessException, InstantiationException,
               InvocationTargetException
    {
        Class<?> clazz = getClass(args);
        if (!Scriptable.class.isAssignableFrom(clazz)) {
            throw reportRuntimeError("msg.must.implement.Scriptable");
        }
        ScriptableObject.defineClass(thisObj, (Class<? extends Scriptable>)clazz);
    }

    /**
     * Load and execute a script compiled to a class file.
     * <p>
     * This method is defined as a JavaScript function.
     * When called as a JavaScript function, a single argument is
     * expected. This argument should be the name of a class that
     * implements the Script interface, as will any script
     * compiled by jsc.
     *
     * @exception IllegalAccessException if access is not available
     *            to the class
     * @exception InstantiationException if unable to instantiate
     *            the named class
     */
    public static void loadClass(Context cx, Scriptable thisObj,
                                 Object[] args, Function funObj)
        throws IllegalAccessException, InstantiationException
    {
        Class<?> clazz = getClass(args);
        if (!Script.class.isAssignableFrom(clazz)) {
            throw reportRuntimeError("msg.must.implement.Script");
        }
        Script script = (Script) clazz.newInstance();
        script.exec(cx, thisObj);
    }

    private static Class<?> getClass(Object[] args) {
        if (args.length == 0) {
            throw reportRuntimeError("msg.expected.string.arg");
        }
        Object arg0 = args[0];
        if (arg0 instanceof Wrapper) {
            Object wrapped = ((Wrapper)arg0).unwrap();
            if (wrapped instanceof Class)
                return (Class<?>)wrapped;
        }
        String className = Context.toString(args[0]);
        try {
            return Class.forName(className);
        }
        catch (ClassNotFoundException cnfe) {
            throw reportRuntimeError("msg.class.not.found", className);
        }
    }

    public static void serialize(Context cx, Scriptable thisObj,
                                 Object[] args, Function funObj)
        throws IOException
    {
        if (args.length < 2) {
            throw Context.reportRuntimeError(
                "Expected an object to serialize and a filename to write " +
                "the serialization to");
        }
        Object obj = args[0];
        String filename = Context.toString(args[1]);
        FileOutputStream fos = new FileOutputStream(filename);
        Scriptable scope = ScriptableObject.getTopLevelScope(thisObj);
        ScriptableOutputStream out = new ScriptableOutputStream(fos, scope);
        out.writeObject(obj);
        out.close();
    }

    public static Object deserialize(Context cx, Scriptable thisObj,
                                     Object[] args, Function funObj)
        throws IOException, ClassNotFoundException
    {
        if (args.length < 1) {
            throw Context.reportRuntimeError(
                "Expected a filename to read the serialization from");
        }
        String filename = Context.toString(args[0]);
        FileInputStream fis = new FileInputStream(filename);
        Scriptable scope = ScriptableObject.getTopLevelScope(thisObj);
        ObjectInputStream in = new ScriptableInputStream(fis, scope);
        Object deserialized = in.readObject();
        in.close();
        return Context.toObject(deserialized, scope);
    }

    public String[] getPrompts(Context cx) {
        if (ScriptableObject.hasProperty(this, "prompts")) {
            Object promptsJS = ScriptableObject.getProperty(this,
                                                            "prompts");
            if (promptsJS instanceof Scriptable) {
                Scriptable s = (Scriptable) promptsJS;
                if (ScriptableObject.hasProperty(s, 0) &&
                    ScriptableObject.hasProperty(s, 1))
                {
                    Object elem0 = ScriptableObject.getProperty(s, 0);
                    if (elem0 instanceof Function) {
                        elem0 = ((Function) elem0).call(cx, this, s,
                                new Object[0]);
                    }
                    prompts[0] = Context.toString(elem0);
                    Object elem1 = ScriptableObject.getProperty(s, 1);
                    if (elem1 instanceof Function) {
                        elem1 = ((Function) elem1).call(cx, this, s,
                                new Object[0]);
                    }
                    prompts[1] = Context.toString(elem1);
                }
            }
        }
        return prompts;
    }

    /**
     * Example: doctest("js> function f() {\n  >   return 3;\n  > }\njs> f();\n3\n"); returns 2
     * (since 2 tests were executed).
     */
    public static Object doctest(Context cx, Scriptable thisObj,
                                 Object[] args, Function funObj)
    {
    	if (args.length == 0) {
    		return Boolean.FALSE;
    	}
    	String session = Context.toString(args[0]);
        Global global = getInstance(funObj);
        return new Integer(global.runDoctest(cx, global, session, null, 0));
    }

    public int runDoctest(Context cx, Scriptable scope, String session,
                          String sourceName, int lineNumber)
    {
        doctestCanonicalizations = new HashMap<String,String>();
        String[] lines = session.split("[\n\r]+");
        String prompt0 = this.prompts[0].trim();
        String prompt1 = this.prompts[1].trim();
        int testCount = 0;
        int i = 0;
        while (i < lines.length && !lines[i].trim().startsWith(prompt0)) {
            i++; // skip lines that don't look like shell sessions
        }
    	while (i < lines.length) {
    		String inputString = lines[i].trim().substring(prompt0.length());
            inputString += "\n";
    		i++;
    		while (i < lines.length && lines[i].trim().startsWith(prompt1)) {
    			inputString += lines[i].trim().substring(prompt1.length());
    			inputString += "\n";
    			i++;
    		}
            String expectedString = "";
            while (i < lines.length &&
                   !lines[i].trim().startsWith(prompt0))
            {
                expectedString += lines[i] + "\n";
                i++;
            }
    		PrintStream savedOut = this.getOut();
    		PrintStream savedErr = this.getErr();
    		ByteArrayOutputStream out = new ByteArrayOutputStream();
    		ByteArrayOutputStream err = new ByteArrayOutputStream();
    		this.setOut(new PrintStream(out));
    		this.setErr(new PrintStream(err));
    		String resultString = "";
    		ErrorReporter savedErrorReporter = cx.getErrorReporter();
    		cx.setErrorReporter(new ToolErrorReporter(false, this.getErr()));
    		try {
    		    testCount++;
	    		Object result = cx.evaluateString(scope, inputString,
	    				            "doctest input", 1, null);
	            if (result != Context.getUndefinedValue() &&
	                    !(result instanceof Function &&
	                      inputString.trim().startsWith("function")))
	            {
	            	resultString = Context.toString(result);
	            }
    		} catch (RhinoException e) {
                ToolErrorReporter.reportException(cx.getErrorReporter(), e);
    		} finally {
    		    this.setOut(savedOut);
    		    this.setErr(savedErr);
        		cx.setErrorReporter(savedErrorReporter);
    			resultString += err.toString() + out.toString();
    		}
    		if (!doctestOutputMatches(expectedString, resultString)) {
    		    String message = "doctest failure running:\n" +
                    inputString +
                    "expected: " + expectedString +
                    "actual: " + resultString + "\n";
    		    if (sourceName != null)
                    throw Context.reportRuntimeError(message, sourceName,
                            lineNumber+i-1, null, 0);
    		    else
                    throw Context.reportRuntimeError(message);
    		}
    	}
    	return testCount;
    }

    /**
     * Compare actual result of doctest to expected, modulo some
     * acceptable differences. Currently just trims the strings
     * before comparing, but should ignore differences in line numbers
     * for error messages for example.
     *
     * @param expected the expected string
     * @param actual the actual string
     * @return true iff actual matches expected modulo some acceptable
     *      differences
     */
    private boolean doctestOutputMatches(String expected, String actual) {
        expected = expected.trim();
        actual = actual.trim().replace("\r\n", "\n");
        if (expected.equals(actual))
            return true;
        for (Map.Entry<String,String> entry: doctestCanonicalizations.entrySet()) {
            expected = expected.replace(entry.getKey(), entry.getValue());
        }
        if (expected.equals(actual))
            return true;
        // java.lang.Object.toString() prints out a unique hex number associated
        // with each object. This number changes from run to run, so we want to
        // ignore differences between these numbers in the output. We search for a
        // regexp that matches the hex number preceded by '@', then enter mappings into
        // "doctestCanonicalizations" so that we ensure that the mappings are
        // consistent within a session.
        Pattern p = Pattern.compile("@[0-9a-fA-F]+");
        Matcher expectedMatcher = p.matcher(expected);
        Matcher actualMatcher = p.matcher(actual);
        for (;;) {
            if (!expectedMatcher.find())
                return false;
            if (!actualMatcher.find())
                return false;
            if (actualMatcher.start() != expectedMatcher.start())
                return false;
            int start = expectedMatcher.start();
            if (!expected.substring(0, start).equals(actual.substring(0, start)))
                return false;
            String expectedGroup = expectedMatcher.group();
            String actualGroup = actualMatcher.group();
            String mapping = doctestCanonicalizations.get(expectedGroup);
            if (mapping == null) {
                doctestCanonicalizations.put(expectedGroup, actualGroup);
                expected = expected.replace(expectedGroup, actualGroup);
            } else if (!actualGroup.equals(mapping)) {
                return false; // wrong object!
            }
            if (expected.equals(actual))
                return true;
        }
    }

    /**
     * The spawn function runs a given function or script in a different
     * thread.
     *
     * js> function g() { a = 7; }
     * js> a = 3;
     * 3
     * js> spawn(g)
     * Thread[Thread-1,5,main]
     * js> a
     * 3
     */
    public static Object spawn(Context cx, Scriptable thisObj, Object[] args,
                               Function funObj)
    {
        Scriptable scope = funObj.getParentScope();
        Runner runner;
        if (args.length != 0 && args[0] instanceof Function) {
            Object[] newArgs = null;
            if (args.length > 1 && args[1] instanceof Scriptable) {
                newArgs = cx.getElements((Scriptable) args[1]);
            }
            if (newArgs == null) { newArgs = ScriptRuntime.emptyArgs; }
            runner = new Runner(scope, (Function) args[0], newArgs);
        } else if (args.length != 0 && args[0] instanceof Script) {
            runner = new Runner(scope, (Script) args[0]);
        } else {
            throw reportRuntimeError("msg.spawn.args");
        }
        runner.factory = cx.getFactory();
        Thread thread = new Thread(runner);
        thread.start();
        return thread;
    }

    /**
     * The sync function creates a synchronized function (in the sense
     * of a Java synchronized method) from an existing function. The
     * new function synchronizes on the the second argument if it is
     * defined, or otherwise the <code>this</code> object of
     * its invocation.
     * js> var o = { f : sync(function(x) {
     *       print("entry");
     *       Packages.java.lang.Thread.sleep(x*1000);
     *       print("exit");
     *     })};
     * js> spawn(function() {o.f(5);});
     * Thread[Thread-0,5,main]
     * entry
     * js> spawn(function() {o.f(5);});
     * Thread[Thread-1,5,main]
     * js>
     * exit
     * entry
     * exit
     */
    public static Object sync(Context cx, Scriptable thisObj, Object[] args,
                              Function funObj)
    {
        if (args.length >= 1 && args.length <= 2 && args[0] instanceof Function) {
            Object syncObject = null;
            if (args.length == 2 && args[1] != Undefined.instance) {
                syncObject = args[1];
            }
            return new Synchronizer((Function)args[0], syncObject);
        }
        else {
            throw reportRuntimeError("msg.sync.args");
        }
    }

    /**
     * Execute the specified command with the given argument and options
     * as a separate process and return the exit status of the process.
     * <p>
     * Usage:
     * <pre>
     * runCommand(command)
     * runCommand(command, arg1, ..., argN)
     * runCommand(command, arg1, ..., argN, options)
     * </pre>
     * All except the last arguments to runCommand are converted to strings
     * and denote command name and its arguments. If the last argument is a
     * JavaScript object, it is an option object. Otherwise it is converted to
     * string denoting the last argument and options objects assumed to be
     * empty.
     * The following properties of the option object are processed:
     * <ul>
     * <li><tt>args</tt> - provides an array of additional command arguments
     * <li><tt>env</tt> - explicit environment object. All its enumerable
     *   properties define the corresponding environment variable names.
     * <li><tt>input</tt> - the process input. If it is not
     *   java.io.InputStream, it is converted to string and sent to the process
     *   as its input. If not specified, no input is provided to the process.
     * <li><tt>output</tt> - the process output instead of
     *   java.lang.System.out. If it is not instance of java.io.OutputStream,
     *   the process output is read, converted to a string, appended to the
     *   output property value converted to string and put as the new value of
     *   the output property.
     * <li><tt>err</tt> - the process error output instead of
     *   java.lang.System.err. If it is not instance of java.io.OutputStream,
     *   the process error output is read, converted to a string, appended to
     *   the err property value converted to string and put as the new
     *   value of the err property.
     * </ul>
     */
    public static Object runCommand(Context cx, Scriptable thisObj,
                                    Object[] args, Function funObj)
        throws IOException
    {
        int L = args.length;
        if (L == 0 || (L == 1 && args[0] instanceof Scriptable)) {
            throw reportRuntimeError("msg.runCommand.bad.args");
        }

        InputStream in = null;
        OutputStream out = null, err = null;
        ByteArrayOutputStream outBytes = null, errBytes = null;
        Object outObj = null, errObj = null;
        String[] environment = null;
        Scriptable params = null;
        Object[] addArgs = null;
        if (args[L - 1] instanceof Scriptable) {
            params = (Scriptable)args[L - 1];
            --L;
            Object envObj = ScriptableObject.getProperty(params, "env");
            if (envObj != Scriptable.NOT_FOUND) {
                if (envObj == null) {
                    environment = new String[0];
                } else {
                    if (!(envObj instanceof Scriptable)) {
                        throw reportRuntimeError("msg.runCommand.bad.env");
                    }
                    Scriptable envHash = (Scriptable)envObj;
                    Object[] ids = ScriptableObject.getPropertyIds(envHash);
                    environment = new String[ids.length];
                    for (int i = 0; i != ids.length; ++i) {
                        Object keyObj = ids[i], val;
                        String key;
                        if (keyObj instanceof String) {
                            key = (String)keyObj;
                            val = ScriptableObject.getProperty(envHash, key);
                        } else {
                            int ikey = ((Number)keyObj).intValue();
                            key = Integer.toString(ikey);
                            val = ScriptableObject.getProperty(envHash, ikey);
                        }
                        if (val == ScriptableObject.NOT_FOUND) {
                            val = Undefined.instance;
                        }
                        environment[i] = key+'='+ScriptRuntime.toString(val);
                    }
                }
            }
            Object inObj = ScriptableObject.getProperty(params, "input");
            if (inObj != Scriptable.NOT_FOUND) {
                in = toInputStream(inObj);
            }
            outObj = ScriptableObject.getProperty(params, "output");
            if (outObj != Scriptable.NOT_FOUND) {
                out = toOutputStream(outObj);
                if (out == null) {
                    outBytes = new ByteArrayOutputStream();
                    out = outBytes;
                }
            }
            errObj = ScriptableObject.getProperty(params, "err");
            if (errObj != Scriptable.NOT_FOUND) {
                err = toOutputStream(errObj);
                if (err == null) {
                    errBytes = new ByteArrayOutputStream();
                    err = errBytes;
                }
            }
            Object addArgsObj = ScriptableObject.getProperty(params, "args");
            if (addArgsObj != Scriptable.NOT_FOUND) {
                Scriptable s = Context.toObject(addArgsObj,
                                                getTopLevelScope(thisObj));
                addArgs = cx.getElements(s);
            }
        }
        Global global = getInstance(funObj);
        if (out == null) {
            out = (global != null) ? global.getOut() : System.out;
        }
        if (err == null) {
            err = (global != null) ? global.getErr() : System.err;
        }
        // If no explicit input stream, do not send any input to process,
        // in particular, do not use System.in to avoid deadlocks
        // when waiting for user input to send to process which is already
        // terminated as it is not always possible to interrupt read method.

        String[] cmd = new String[(addArgs == null) ? L : L + addArgs.length];
        for (int i = 0; i != L; ++i) {
            cmd[i] = ScriptRuntime.toString(args[i]);
        }
        if (addArgs != null) {
            for (int i = 0; i != addArgs.length; ++i) {
                cmd[L + i] = ScriptRuntime.toString(addArgs[i]);
            }
        }

        int exitCode = runProcess(cmd, environment, in, out, err);
        if (outBytes != null) {
            String s = ScriptRuntime.toString(outObj) + outBytes.toString();
            ScriptableObject.putProperty(params, "output", s);
        }
        if (errBytes != null) {
            String s = ScriptRuntime.toString(errObj) + errBytes.toString();
            ScriptableObject.putProperty(params, "err", s);
        }

        return new Integer(exitCode);
    }

    /**
     * The seal function seals all supplied arguments.
     */
    public static void seal(Context cx, Scriptable thisObj, Object[] args,
                            Function funObj)
    {
        for (int i = 0; i != args.length; ++i) {
            Object arg = args[i];
            if (!(arg instanceof ScriptableObject) || arg == Undefined.instance)
            {
                if (!(arg instanceof Scriptable) || arg == Undefined.instance)
                {
                    throw reportRuntimeError("msg.shell.seal.not.object");
                } else {
                    throw reportRuntimeError("msg.shell.seal.not.scriptable");
                }
            }
        }

        for (int i = 0; i != args.length; ++i) {
            Object arg = args[i];
            ((ScriptableObject)arg).sealObject();
        }
    }

    /**
     * The readFile reads the given file content and convert it to a string
     * using the specified character coding or default character coding if
     * explicit coding argument is not given.
     * <p>
     * Usage:
     * <pre>
     * readFile(filePath)
     * readFile(filePath, charCoding)
     * </pre>
     * The first form converts file's context to string using the default
     * character coding.
     */
    public static Object readFile(Context cx, Scriptable thisObj, Object[] args,
                                  Function funObj)
        throws IOException
    {
        if (args.length == 0) {
            throw reportRuntimeError("msg.shell.readFile.bad.args");
        }
        String path = ScriptRuntime.toString(args[0]);
        String charCoding = null;
        if (args.length >= 2) {
            charCoding = ScriptRuntime.toString(args[1]);
        }

        return readUrl(path, charCoding, true);
    }

    /**
     * The readUrl opens connection to the given URL, read all its data
     * and converts them to a string
     * using the specified character coding or default character coding if
     * explicit coding argument is not given.
     * <p>
     * Usage:
     * <pre>
     * readUrl(url)
     * readUrl(url, charCoding)
     * </pre>
     * The first form converts file's context to string using the default
     * charCoding.
     */
    public static Object readUrl(Context cx, Scriptable thisObj, Object[] args,
                                 Function funObj)
        throws IOException
    {
        if (args.length == 0) {
            throw reportRuntimeError("msg.shell.readUrl.bad.args");
        }
        String url = ScriptRuntime.toString(args[0]);
        String charCoding = null;
        if (args.length >= 2) {
            charCoding = ScriptRuntime.toString(args[1]);
        }

        return readUrl(url, charCoding, false);
    }

    /**
     * Convert the argument to int32 number.
     */
    public static Object toint32(Context cx, Scriptable thisObj, Object[] args,
                                 Function funObj)
    {
        Object arg = (args.length != 0 ? args[0] : Undefined.instance);
        if (arg instanceof Integer)
            return arg;
        return ScriptRuntime.wrapInt(ScriptRuntime.toInt32(arg));
    }

    private boolean loadJLine(Charset cs) {
        if (!attemptedJLineLoad) {
            // Check if we can use JLine for better command line handling
            attemptedJLineLoad = true;
            console = ShellConsole.getConsole(this, cs);
        }
        return console != null;
    }

    public ShellConsole getConsole(Charset cs) {
        if (!loadJLine(cs)) {
            console = ShellConsole.getConsole(getIn(), getErr(), cs);
        }
        return console;
    }

    public InputStream getIn() {
        if (inStream == null && !attemptedJLineLoad) {
            if (loadJLine(Charset.defaultCharset())) {
                inStream = console.getIn();
            }
        }
        return inStream == null ? System.in : inStream;
    }

    public void setIn(InputStream in) {
        inStream = in;
    }

    public PrintStream getOut() {
        return outStream == null ? System.out : outStream;
    }

    public void setOut(PrintStream out) {
        outStream = out;
    }

    public PrintStream getErr() {
        return errStream == null ? System.err : errStream;
    }

    public void setErr(PrintStream err) {
        errStream = err;
    }

    public void setSealedStdLib(boolean value)
    {
        sealedStdLib = value;
    }

    private static Global getInstance(Function function)
    {
        Scriptable scope = function.getParentScope();
        if (!(scope instanceof Global))
            throw reportRuntimeError("msg.bad.shell.function.scope",
                                     String.valueOf(scope));
        return (Global)scope;
    }

    /**
     * Runs the given process using Runtime.exec().
     * If any of in, out, err is null, the corresponding process stream will
     * be closed immediately, otherwise it will be closed as soon as
     * all data will be read from/written to process
     *
     * @return Exit value of process.
     * @throws IOException If there was an error executing the process.
     */
    private static int runProcess(String[] cmd, String[] environment,
                                  InputStream in, OutputStream out,
                                  OutputStream err)
        throws IOException
    {
        Process p;
        if (environment == null) {
            p = Runtime.getRuntime().exec(cmd);
        } else {
            p = Runtime.getRuntime().exec(cmd, environment);
        }

        try {
            PipeThread inThread = null;
            if (in != null) {
                inThread = new PipeThread(false, in, p.getOutputStream());
                inThread.start();
            } else {
                p.getOutputStream().close();
            }

            PipeThread outThread = null;
            if (out != null) {
                outThread = new PipeThread(true, p.getInputStream(), out);
                outThread.start();
            } else {
                p.getInputStream().close();
            }

            PipeThread errThread = null;
            if (err != null) {
                errThread = new PipeThread(true, p.getErrorStream(), err);
                errThread.start();
            } else {
                p.getErrorStream().close();
            }

            // wait for process completion
            for (;;) {
                try {
                    p.waitFor();
                    if (outThread != null) {
                        outThread.join();
                    }
                    if (inThread != null) {
                        inThread.join();
                    }
                    if (errThread != null) {
                        errThread.join();
                    }
                    break;
                } catch (InterruptedException ignore) {
                }
            }

            return p.exitValue();
        } finally {
            p.destroy();
        }
    }

    static void pipe(boolean fromProcess, InputStream from, OutputStream to)
        throws IOException
    {
        try {
            final int SIZE = 4096;
            byte[] buffer = new byte[SIZE];
            for (;;) {
                int n;
                if (!fromProcess) {
                    n = from.read(buffer, 0, SIZE);
                } else {
                    try {
                        n = from.read(buffer, 0, SIZE);
                    } catch (IOException ex) {
                        // Ignore exception as it can be cause by closed pipe
                        break;
                    }
                }
                if (n < 0) { break; }
                if (fromProcess) {
                    to.write(buffer, 0, n);
                    to.flush();
                } else {
                    try {
                        to.write(buffer, 0, n);
                        to.flush();
                    } catch (IOException ex) {
                        // Ignore exception as it can be cause by closed pipe
                        break;
                    }
                }
            }
        } finally {
            try {
                if (fromProcess) {
                    from.close();
                } else {
                    to.close();
                }
            } catch (IOException ex) {
                // Ignore errors on close. On Windows JVM may throw invalid
                // refrence exception if process terminates too fast.
            }
        }
    }

    private static InputStream toInputStream(Object value)
        throws IOException
    {
        InputStream is = null;
        String s = null;
        if (value instanceof Wrapper) {
            Object unwrapped = ((Wrapper)value).unwrap();
            if (unwrapped instanceof InputStream) {
                is = (InputStream)unwrapped;
            } else if (unwrapped instanceof byte[]) {
                is = new ByteArrayInputStream((byte[])unwrapped);
            } else if (unwrapped instanceof Reader) {
                s = readReader((Reader)unwrapped);
            } else if (unwrapped instanceof char[]) {
                s = new String((char[])unwrapped);
            }
        }
        if (is == null) {
            if (s == null) { s = ScriptRuntime.toString(value); }
            is = new ByteArrayInputStream(s.getBytes());
        }
        return is;
    }

    private static OutputStream toOutputStream(Object value) {
        OutputStream os = null;
        if (value instanceof Wrapper) {
            Object unwrapped = ((Wrapper)value).unwrap();
            if (unwrapped instanceof OutputStream) {
                os = (OutputStream)unwrapped;
            }
        }
        return os;
    }

    private static String readUrl(String filePath, String charCoding,
                                  boolean urlIsFile)
        throws IOException
    {
        int chunkLength;
        InputStream is = null;
        try {
            if (!urlIsFile) {
                URL urlObj = new URL(filePath);
                URLConnection uc = urlObj.openConnection();
                is = uc.getInputStream();
                chunkLength = uc.getContentLength();
                if (chunkLength <= 0)
                    chunkLength = 1024;
                if (charCoding == null) {
                    String type = uc.getContentType();
                    if (type != null) {
                        charCoding = getCharCodingFromType(type);
                    }
                }
            } else {
                File f = new File(filePath);
                if (!f.exists()) {
                    throw new FileNotFoundException("File not found: " + filePath);
                } else if (!f.canRead()) {
                    throw new IOException("Cannot read file: " + filePath);
                }
                long length = f.length();
                chunkLength = (int)length;
                if (chunkLength != length)
                    throw new IOException("Too big file size: "+length);

                if (chunkLength == 0) { return ""; }

                is = new FileInputStream(f);
            }

            Reader r;
            if (charCoding == null) {
                r = new InputStreamReader(is);
            } else {
                r = new InputStreamReader(is, charCoding);
            }
            return readReader(r, chunkLength);

        } finally {
            if (is != null)
                is.close();
        }
    }

    private static String getCharCodingFromType(String type)
    {
        int i = type.indexOf(';');
        if (i >= 0) {
            int end = type.length();
            ++i;
            while (i != end && type.charAt(i) <= ' ') {
                ++i;
            }
            String charset = "charset";
            if (charset.regionMatches(true, 0, type, i, charset.length()))
            {
                i += charset.length();
                while (i != end && type.charAt(i) <= ' ') {
                    ++i;
                }
                if (i != end && type.charAt(i) == '=') {
                    ++i;
                    while (i != end && type.charAt(i) <= ' ') {
                        ++i;
                    }
                    if (i != end) {
                        // i is at the start of non-empty
                        // charCoding spec
                        while (type.charAt(end -1) <= ' ') {
                            --end;
                        }
                        return type.substring(i, end);
                    }
                }
            }
        }
        return null;
    }

    private static String readReader(Reader reader)
        throws IOException
    {
        return readReader(reader, 4096);
    }

    private static String readReader(Reader reader, int initialBufferSize)
        throws IOException
    {
        char[] buffer = new char[initialBufferSize];
        int offset = 0;
        for (;;) {
            int n = reader.read(buffer, offset, buffer.length - offset);
            if (n < 0) { break;    }
            offset += n;
            if (offset == buffer.length) {
                char[] tmp = new char[buffer.length * 2];
                System.arraycopy(buffer, 0, tmp, 0, offset);
                buffer = tmp;
            }
        }
        return new String(buffer, 0, offset);
    }

    static RuntimeException reportRuntimeError(String msgId) {
        String message = ToolErrorReporter.getMessage(msgId);
        return Context.reportRuntimeError(message);
    }

    static RuntimeException reportRuntimeError(String msgId, String msgArg)
    {
        String message = ToolErrorReporter.getMessage(msgId, msgArg);
        return Context.reportRuntimeError(message);
    }
}


class Runner implements Runnable, ContextAction {

    Runner(Scriptable scope, Function func, Object[] args) {
        this.scope = scope;
        f = func;
        this.args = args;
    }

    Runner(Scriptable scope, Script script) {
        this.scope = scope;
        s = script;
    }

    public void run()
    {
        factory.call(this);
    }

    public Object run(Context cx)
    {
        if (f != null)
            return f.call(cx, scope, scope, args);
        else
            return s.exec(cx, scope);
    }

    ContextFactory factory;
    private Scriptable scope;
    private Function f;
    private Script s;
    private Object[] args;
}

class PipeThread extends Thread {

    PipeThread(boolean fromProcess, InputStream from, OutputStream to) {
        setDaemon(true);
        this.fromProcess = fromProcess;
        this.from = from;
        this.to = to;
    }

    @Override
    public void run() {
        try {
            Global.pipe(fromProcess, from, to);
        } catch (IOException ex) {
            throw Context.throwAsScriptRuntimeEx(ex);
        }
    }

    private boolean fromProcess;
    private InputStream from;
    private OutputStream to;
}
