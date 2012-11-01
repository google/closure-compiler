/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import org.mozilla.javascript.*;
import org.mozilla.javascript.annotations.JSConstructor;
import org.mozilla.javascript.annotations.JSFunction;
import org.mozilla.javascript.annotations.JSGetter;

import java.io.*;
import java.util.List;
import java.util.ArrayList;

/**
 * Define a simple JavaScript File object.
 *
 * This isn't intended to be any sort of definitive attempt at a
 * standard File object for JavaScript, but instead is an example
 * of a more involved definition of a host object.
 *
 * Example of use of the File object:
 * <pre>
 * js> defineClass("File")
 * js> file = new File("myfile.txt");
 * [object File]
 * js> file.writeLine("one");                       <i>only now is file actually opened</i>
 * js> file.writeLine("two");
 * js> file.writeLine("thr", "ee");
 * js> file.close();                                <i>must close file before we can reopen for reading</i>
 * js> var a = file.readLines();                    <i>creates and fills an array with the contents of the file</i>
 * js> a;
 * one,two,three
 * js>
 * </pre>
 *
 *
 * File errors or end-of-file signaled by thrown Java exceptions will
 * be wrapped as JavaScript exceptions when called from JavaScript,
 * and may be caught within JavaScript.
 *
 */
public class File extends ScriptableObject {

    /**
     *
     */
    private static final long serialVersionUID = 2549960399774237828L;
    /**
     * The zero-parameter constructor.
     *
     * When Context.defineClass is called with this class, it will
     * construct File.prototype using this constructor.
     */
    public File() {
    }

    /**
     * The Java method defining the JavaScript File constructor.
     *
     * If the constructor has one or more arguments, and the
     * first argument is not undefined, the argument is converted
     * to a string as used as the filename.<p>
     *
     * Otherwise System.in or System.out is assumed as appropriate
     * to the use.
     */
    @JSConstructor
    public static Scriptable jsConstructor(Context cx, Object[] args,
                                           Function ctorObj,
                                           boolean inNewExpr)
    {
        File result = new File();
        if (args.length == 0 || args[0] == Context.getUndefinedValue()) {
            result.name = "";
            result.file = null;
        } else {
            result.name = Context.toString(args[0]);
            result.file = new java.io.File(result.name);
        }
        return result;
    }

    /**
     * Returns the name of this JavaScript class, "File".
     */
    @Override
    public String getClassName() {
        return "File";
    }

    /**
     * Get the name of the file.
     *
     * Used to define the "name" property.
     */
    @JSGetter
    public String getName() {
        return name;
    }

    /**
     * Read the remaining lines in the file and return them in an array.
     *
     * Implements a JavaScript function.<p>
     *
     * This is a good example of creating a new array and setting
     * elements in that array.
     *
     * @exception IOException if an error occurred while accessing the file
     *            associated with this object
     */
    @JSFunction
    public Object readLines()
        throws IOException
    {
        List<String> list = new ArrayList<String>();
        String s;
        while ((s = readLine()) != null) {
            list.add(s);
        }
        String[] lines = list.toArray(new String[list.size()]);
        Scriptable scope = ScriptableObject.getTopLevelScope(this);
        Context cx = Context.getCurrentContext();
        return cx.newObject(scope, "Array", lines);
    }

    /**
     * Read a line.
     *
     * Implements a JavaScript function.
     * @exception IOException if an error occurred while accessing the file
     *            associated with this object, or EOFException if the object
     *            reached the end of the file
     */
    @JSFunction
    public String readLine() throws IOException {
        return getReader().readLine();
    }

    /**
     * Read a character.
     *
     * @exception IOException if an error occurred while accessing the file
     *            associated with this object, or EOFException if the object
     *            reached the end of the file
     */
    @JSFunction
    public String readChar() throws IOException {
        int i = getReader().read();
        if (i == -1)
            return null;
        char[] charArray = { (char) i };
        return new String(charArray);
    }

    /**
     * Write strings.
     *
     * Implements a JavaScript function. <p>
     *
     * This function takes a variable number of arguments, converts
     * each argument to a string, and writes that string to the file.
     * @exception IOException if an error occurred while accessing the file
     *            associated with this object
     */
    @JSFunction
    public static void write(Context cx, Scriptable thisObj,
                                        Object[] args, Function funObj)
        throws IOException
    {
        write0(thisObj, args, false);
    }

    /**
     * Write strings and a newline.
     *
     * Implements a JavaScript function.
     * @exception IOException if an error occurred while accessing the file
     *            associated with this object
     *
     */
    @JSFunction
    public static void writeLine(Context cx, Scriptable thisObj,
                                            Object[] args, Function funObj)
        throws IOException
    {
        write0(thisObj, args, true);
    }

    @JSGetter
    public int getLineNumber()
        throws FileNotFoundException
    {
        return getReader().getLineNumber();
    }

    /**
     * Close the file. It may be reopened.
     *
     * Implements a JavaScript function.
     * @exception IOException if an error occurred while accessing the file
     *            associated with this object
     */
    @JSFunction
    public void close() throws IOException {
        if (reader != null) {
            reader.close();
            reader = null;
        } else if (writer != null) {
            writer.close();
            writer = null;
        }
    }

    /**
     * Finalizer.
     *
     * Close the file when this object is collected.
     */
    @Override
    protected void finalize() {
        try {
            close();
        }
        catch (IOException e) {
        }
    }

    /**
     * Get the Java reader.
     */
    @JSFunction("getReader")
    public Object getJSReader() {
        if (reader == null)
            return null;
        // Here we use toObject() to "wrap" the BufferedReader object
        // in a Scriptable object so that it can be manipulated by
        // JavaScript.
        Scriptable parent = ScriptableObject.getTopLevelScope(this);
        return Context.javaToJS(reader, parent);
    }

    /**
     * Get the Java writer.
     *
     * @see File#getReader
     *
     */
    @JSFunction
    public Object getWriter() {
        if (writer == null)
            return null;
        Scriptable parent = ScriptableObject.getTopLevelScope(this);
        return Context.javaToJS(writer, parent);
    }

    /**
     * Get the reader, checking that we're not already writing this file.
     */
    private LineNumberReader getReader() throws FileNotFoundException {
        if (writer != null) {
            throw Context.reportRuntimeError("already writing file \""
                                             + name
                                             + "\"");
        }
        if (reader == null)
            reader = new LineNumberReader(file == null
                                        ? new InputStreamReader(System.in)
                                        : new FileReader(file));
        return reader;
    }

    /**
     * Perform the guts of write and writeLine.
     *
     * Since the two functions differ only in whether they write a
     * newline character, move the code into a common subroutine.
     *
     */
    private static void write0(Scriptable thisObj, Object[] args, boolean eol)
        throws IOException
    {
        File thisFile = checkInstance(thisObj);
        if (thisFile.reader != null) {
            throw Context.reportRuntimeError("already writing file \""
                                             + thisFile.name
                                             + "\"");
        }
        if (thisFile.writer == null)
            thisFile.writer = new BufferedWriter(
                thisFile.file == null ? new OutputStreamWriter(System.out)
                                      : new FileWriter(thisFile.file));
        for (int i=0; i < args.length; i++) {
            String s = Context.toString(args[i]);
            thisFile.writer.write(s, 0, s.length());
        }
        if (eol)
            thisFile.writer.newLine();
    }

    /**
     * Perform the instanceof check and return the downcasted File object.
     *
     * This is necessary since methods may reside in the File.prototype
     * object and scripts can dynamically alter prototype chains. For example:
     * <pre>
     * js> defineClass("File");
     * js> o = {};
     * [object Object]
     * js> o.__proto__ = File.prototype;
     * [object File]
     * js> o.write("hi");
     * js: called on incompatible object
     * </pre>
     * The runtime will take care of such checks when non-static Java methods
     * are defined as JavaScript functions.
     */
    private static File checkInstance(Scriptable obj) {
        if (obj == null || !(obj instanceof File)) {
            throw Context.reportRuntimeError("called on incompatible object");
        }
        return (File) obj;
    }

    /**
     * Some private data for this class.
     */
    private String name;
    private java.io.File file;  // may be null, meaning to use System.out or .in
    private LineNumberReader reader;
    private BufferedWriter writer;
}
