/*
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
 * Portions created by the Initial Developer are Copyright (C) 1997-2000
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Norris Boyd
 *   Brendan Eich
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

// API class

package com.google.javascript.rhino;

import java.beans.*;
import java.io.*;
import java.util.Hashtable;
import java.util.Locale;

/**
 * This class represents the runtime context of an executing script.
 *
 * Before executing a script, an instance of Context must be created
 * and associated with the thread that will be executing the script.
 * The Context will be used to store information about the executing
 * of the script such as the call stack. Contexts are associated with
 * the current thread  using the {@link #enter()} method.<p>
 *
 * Different forms of script execution are supported. Scripts may be
 * evaluated from the source directly, or first compiled and then later
 * executed. Interactive execution is also supported.<p>
 *
 * Some aspects of script execution, such as type conversions and
 * object creation, may be accessed directly through methods of
 * Context.
 *
 */
public class Context
{
    /**
     * Language versions.
     *
     * All integral values are reserved for future version numbers.
     */

    /**
     * The unknown version.
     */
    public static final int VERSION_UNKNOWN =   -1;

    /**
     * The default version.
     */
    public static final int VERSION_DEFAULT =    0;

    /**
     * JavaScript 1.0
     */
    public static final int VERSION_1_0 =      100;

    /**
     * JavaScript 1.1
     */
    public static final int VERSION_1_1 =      110;

    /**
     * JavaScript 1.2
     */
    public static final int VERSION_1_2 =      120;

    /**
     * JavaScript 1.3
     */
    public static final int VERSION_1_3 =      130;

    /**
     * JavaScript 1.4
     */
    public static final int VERSION_1_4 =      140;

    /**
     * JavaScript 1.5
     */
    public static final int VERSION_1_5 =      150;

    /**
     * JavaScript 1.5
     */
    public static final int VERSION_1_6 =      160;

    /**
     * Controls behaviour of <tt>Date.prototype.getYear()</tt>.
     * If <tt>hasFeature(FEATURE_NON_ECMA_GET_YEAR)</tt> returns true,
     * Date.prototype.getYear subtructs 1900 only if 1900 <= date < 2000.
     * The default behavior of {@link #hasFeature(int)} is always to subtruct
     * 1900 as rquired by ECMAScript B.2.4.
     */
    public static final int FEATURE_NON_ECMA_GET_YEAR = 1;

    /**
     * Control if member expression as function name extension is available.
     * If <tt>hasFeature(FEATURE_MEMBER_EXPR_AS_FUNCTION_NAME)</tt> returns
     * true, allow <tt>function memberExpression(args) { body }</tt> to be
     * syntax sugar for <tt>memberExpression = function(args) { body }</tt>,
     * when memberExpression is not a simple identifier.
     * See ECMAScript-262, section 11.2 for definition of memberExpression.
     * By default {@link #hasFeature(int)} returns false.
     */
    public static final int FEATURE_MEMBER_EXPR_AS_FUNCTION_NAME = 2;

    /**
     * Control if reserved keywords are treated as identifiers.
     * If <tt>hasFeature(RESERVED_KEYWORD_AS_IDENTIFIER)</tt> returns true,
     * treat future reserved keyword (see  Ecma-262, section 7.5.3) as ordinary
     * identifiers but warn about this usage.
     *
     * By default {@link #hasFeature(int)} returns false.
     */
    public static final int FEATURE_RESERVED_KEYWORD_AS_IDENTIFIER = 3;

    /**
     * Control if <tt>toString()</tt> should returns the same result
     * as  <tt>toSource()</tt> when applied to objects and arrays.
     * If <tt>hasFeature(FEATURE_TO_STRING_AS_SOURCE)</tt> returns true,
     * calling <tt>toString()</tt> on JS objects gives the same result as
     * calling <tt>toSource()</tt>. That is it returns JS source with code
     * to create an object with all enumeratable fields of the original object.
     * <p>
     * By default {@link #hasFeature(int)} returns true only if
     * the current JS version is set to {@link #VERSION_1_2}.
     */
    public static final int FEATURE_TO_STRING_AS_SOURCE = 4;

    /**
     * Control if properties <tt>__proto__</tt> and <tt>__parent__</tt>
     * are treated specially.
     * If <tt>hasFeature(FEATURE_PARENT_PROTO_PROPRTIES)</tt> returns true,
     * treat <tt>__parent__</tt> and <tt>__proto__</tt> as special properties.
     * <p>
     * The properties allow to query and set scope and prototype chains for the
     * objects. The special meaning of the properties is available
     * only when they are used as the right hand side of the dot operator.
     * For example, while <tt>x.__proto__ = y</tt> changes the prototype
     * chain of the object <tt>x</tt> to point to <tt>y</tt>,
     * <tt>x["__proto__"] = y</tt> simply assigns a new value to the property
     * <tt>__proto__</tt> in <tt>x</tt> even when the feature is on.
     *
     * By default {@link #hasFeature(int)} returns true.
     */
    public static final int FEATURE_PARENT_PROTO_PROPRTIES = 5;

    /**
     * Control if support for E4X(ECMAScript for XML) extension is available.
     * If hasFeature(FEATURE_E4X) returns true, the XML syntax is available.
     * <p>
     * By default {@link #hasFeature(int)} returns true if
     * the current JS version is set to {@link #VERSION_DEFAULT}
     * or is greater then {@link #VERSION_1_6}.
     * @since 1.6 Release 1
     */
    public static final int FEATURE_E4X = 6;

    /**
     * Control if dynamic scope should be used for name access.
     * If hasFeature(FEATURE_DYNAMIC_SCOPE) returns true, then the name lookup
     * during name resolution will use the top scope of the script or function
     * which is at the top of JS execution stack instead of the top scope of the
     * script or function from the current stack frame if the top scope of
     * the top stack frame contains the top scope of the current stack frame
     * on its prototype chain.
     * <p>
     * This is useful to define shared scope containing functions that can
     * be called from scripts and functions using private scopes.
     * <p>
     * By default {@link #hasFeature(int)} returns false.
     * @since 1.6 Release 1
     */
    public static final int FEATURE_DYNAMIC_SCOPE = 7;

    /**
     * Control if strict variable mode is enabled.
     * When the feature is on Rhino reports runtime errors if assignment
     * to a global variable that does not exist is executed. When the feature
     * is off such assignments creates new variable in the global scope  as
     * required by ECMA 262.
     * <p>
     * By default {@link #hasFeature(int)} returns false.
     * @since 1.6 Release 1
     */
    public static final int FEATURE_STRICT_VARS = 8;

    /**
     * Control if strict eval mode is enabled.
     * When the feature is on Rhino reports runtime errors if non-string
     * argument is passed to the eval function. When the feature is off
     * eval simply return non-string argument as is without performing any
     * evaluation as required by ECMA 262.
     * <p>
     * By default {@link #hasFeature(int)} returns false.
     * @since 1.6 Release 1
     */
    public static final int FEATURE_STRICT_EVAL = 9;

    /**
     * When the feature is on Rhino will add a "fileName" and "lineNumber"
     * properties to Error objects automatically. When the feature is off, you
     * have to explicitly pass them as the second and third argument to the
     * Error constructor. Note that neither behaviour is fully ECMA 262
     * compliant (as 262 doesn't specify a three-arg constructor), but keeping
     * the feature off results in Error objects that don't have
     * additional non-ECMA properties when constructed using the ECMA-defined
     * single-arg constructor and is thus desirable if a stricter ECMA
     * compliance is desired, specifically adherence to the point 15.11.5. of
     * the standard.
     * <p>
     * By default {@link #hasFeature(int)} returns false.
     * @since 1.6 Release 6
     */
    public static final int FEATURE_LOCATION_INFORMATION_IN_ERROR = 10;

    /**
     * Controls whether JS 1.5 'strict mode' is enabled.
     * When the feature is on, Rhino reports more than a dozen different
     * warnings.  When the feature is off, these warnings are not generated.
     * FEATURE_STRICT_MODE implies FEATURE_STRICT_VARS and FEATURE_STRICT_EVAL.
     * <p>
     * By default {@link #hasFeature(int)} returns false.
     * @since 1.6 Release 6
     */
    public static final int FEATURE_STRICT_MODE = 11;

    /**
     * Controls whether a warning should be treated as an error.
     * @since 1.6 Release 6
     */
    public static final int FEATURE_WARNING_AS_ERROR = 12;

    public static final String languageVersionProperty = "language version";
    public static final String errorReporterProperty   = "error reporter";

    /**
     * Convinient value to use as zero-length array of objects.
     */
    public static final Object[] emptyArgs = ScriptRuntime.emptyArgs;

    /**
     * Create a new Context.
     *
     * Note that the Context must be associated with a thread before
     * it can be used to execute a script.
     *
     * @see #enter()
     */
    public Context()
    {
        setLanguageVersion(VERSION_DEFAULT);
    }

    /**
     * Get a context associated with the current thread, creating
     * one if need be.
     *
     * The Context stores the execution state of the JavaScript
     * engine, so it is required that the context be entered
     * before execution may begin. Once a thread has entered
     * a Context, then getCurrentContext() may be called to find
     * the context that is associated with the current thread.
     * <p>
     * Calling <code>enter()</code> will
     * return either the Context currently associated with the
     * thread, or will create a new context and associate it
     * with the current thread. Each call to <code>enter()</code>
     * must have a matching call to <code>exit()</code>. For example,
     * <pre>
     *      Context cx = Context.enter();
     *      try {
     *          ...
     *          cx.evaluateString(...);
     *      }
     *      finally { Context.exit(); }
     * </pre>
     * @return a Context associated with the current thread
     * @see #getCurrentContext
     * @see #exit
     */
    public static Context enter() {
        return enter(null);
    }

    /**
     * Get a Context associated with the current thread, using
     * the given Context if need be.
     * <p>
     * The same as <code>enter()</code> except that <code>cx</code>
     * is associated with the current thread and returned if
     * the current thread has no associated context and <code>cx</code>
     * is not associated with any other thread.
     * @param cx a Context to associate with the thread if possible
     * @return a Context associated with the current thread
     */
    public static Context enter(Context cx) {

        Context old = getCurrentContext();

        if (cx == null) {
            if (old != null) {
                cx = old;
            } else {
                cx = new Context();
                setThreadContext(cx);
            }
        } else {
            if (cx.enterCount != 0) {
                // The suplied context must be the context for
                // the current thread if it is already entered
                if (cx != old) {
                    throw new RuntimeException
                        ("Cannot enter Context active on another thread");
                }
            } else {
                if (old != null) {
                    cx = old;
                } else {
                    setThreadContext(cx);
                }
            }
        }
        ++cx.enterCount;
        return cx;
     }

    /**
     * Exit a block of code requiring a Context.
     *
     * Calling <code>exit()</code> will remove the association between
     * the current thread and a Context if the prior call to
     * <code>enter()</code> on this thread newly associated a Context
     * with this thread.
     * Once the current thread no longer has an associated Context,
     * it cannot be used to execute JavaScript until it is again associated
     * with a Context.
     *
     * @see #enter
     */
    public static void exit() {
        boolean released = false;
        Context cx = getCurrentContext();
        if (cx == null) {
            throw new RuntimeException
                ("Calling Context.exit without previous Context.enter");
        }
        if (cx.enterCount < 1) Kit.codeBug();
        --cx.enterCount;
        if (cx.enterCount == 0) {
            released = true;
            setThreadContext(null);
        }
    }

    /**
     * Get the current Context.
     *
     * The current Context is per-thread; this method looks up
     * the Context associated with the current thread. <p>
     *
     * @return the Context associated with the current thread, or
     *         null if no context is associated with the current
     *         thread.
     */
    public static Context getCurrentContext() {
        return threadContexts.get();
    }

    private static void setThreadContext(Context cx) {
        threadContexts.set(cx);
    }

    private static ThreadLocal<Context> threadContexts
        = new ThreadLocal<Context>();

    /**
     * Checks if this is a sealed Context. A sealed Context instance does not
     * allow to modify any of its properties and will throw an exception
     * on any such attempt.
     * @see #seal(Object sealKey)
     */
    public final boolean isSealed()
    {
        return sealed;
    }

    /**
     * Seal this Context object so any attempt to modify any of its properties
     * including calling {@link #enter()} and {@link #exit()} methods will
     * throw an exception.
     * <p>
     * If <tt>sealKey</tt> is not null, calling
     * {@link #unseal(Object sealKey)} with the same key unseals
     * the object. If <tt>sealKey</tt> is null, unsealing is no longer possible.
     *
     * @see #isSealed()
     * @see #unseal(Object)
     */
    public final void seal(Object sealKey)
    {
        if (sealed) onSealedMutation();
        sealed = true;
        this.sealKey = sealKey;
    }

    /**
     * Unseal previously sealed Context object.
     * The <tt>sealKey</tt> argument should not be null and should match
     * <tt>sealKey</tt> suplied with the last call to
     * {@link #seal(Object)} or an exception will be thrown.
     *
     * @see #isSealed()
     * @see #seal(Object sealKey)
     */
    public final void unseal(Object sealKey)
    {
        if (sealKey == null) throw new IllegalArgumentException();
        if (this.sealKey != sealKey) throw new IllegalArgumentException();
        if (!sealed) throw new IllegalStateException();
        sealed = false;
        this.sealKey = null;
    }

    static void onSealedMutation()
    {
        throw new IllegalStateException();
    }

    /**
     * Get the current language version.
     * <p>
     * The language version number affects JavaScript semantics as detailed
     * in the overview documentation.
     *
     * @return an integer that is one of VERSION_1_0, VERSION_1_1, etc.
     */
    public final int getLanguageVersion()
    {
       return version;
    }

    /**
     * Set the language version.
     *
     * <p>
     * Setting the language version will affect functions and scripts compiled
     * subsequently. See the overview documentation for version-specific
     * behavior.
     *
     * @param version the version as specified by VERSION_1_0, VERSION_1_1, etc.
     */
    public void setLanguageVersion(int version)
    {
        if (sealed) onSealedMutation();
        checkLanguageVersion(version);
        Object listeners = propertyListeners;
        if (listeners != null && version != this.version) {
            firePropertyChangeImpl(listeners, languageVersionProperty,
                               new Integer(this.version),
                               new Integer(version));
        }
        this.version = version;
    }

    public static boolean isValidLanguageVersion(int version)
    {
        switch (version) {
            case VERSION_DEFAULT:
            case VERSION_1_0:
            case VERSION_1_1:
            case VERSION_1_2:
            case VERSION_1_3:
            case VERSION_1_4:
            case VERSION_1_5:
            case VERSION_1_6:
                return true;
        }
        return false;
    }

    public static void checkLanguageVersion(int version)
    {
        if (isValidLanguageVersion(version)) {
            return;
        }
        throw new IllegalArgumentException("Bad language version: "+version);
    }

    /**
     * Get the implementation version.
     *
     * <p>
     * The implementation version is of the form
     * <pre>
     *    "<i>name langVer</i> <code>release</code> <i>relNum date</i>"
     * </pre>
     * where <i>name</i> is the name of the product, <i>langVer</i> is
     * the language version, <i>relNum</i> is the release number, and
     * <i>date</i> is the release date for that specific
     * release in the form "yyyy mm dd".
     *
     * @return a string that encodes the product, language version, release
     *         number, and date.
     */
    public final String getImplementationVersion()
    {
        // XXX Probably it would be better to embed this directly into source
        // with special build preprocessing but that would require some ant
        // tweaking and then replacing token in resource files was simpler
        if (implementationVersion == null) {
            implementationVersion
                = ScriptRuntime.getMessage0("implementation.version");
        }
        return implementationVersion;
    }

    /**
     * Get the current error reporter.
     *
     * @see com.google.javascript.rhino.ErrorReporter
     */
    public final ErrorReporter getErrorReporter()
    {
        return errorReporter;
    }

    /**
     * Change the current error reporter.
     *
     * @return the previous error reporter
     * @see com.google.javascript.rhino.ErrorReporter
     */
    public final ErrorReporter setErrorReporter(ErrorReporter reporter)
    {
        if (sealed) onSealedMutation();
        if (reporter == null) throw new IllegalArgumentException();
        ErrorReporter old = getErrorReporter();
        if (reporter == old) {
            return old;
        }
        Object listeners = propertyListeners;
        if (listeners != null) {
            firePropertyChangeImpl(listeners, errorReporterProperty,
                                   old, reporter);
        }
        this.errorReporter = reporter;
        return old;
    }

    /**
     * Get the current locale.  Returns the default locale if none has
     * been set.
     *
     * @see java.util.Locale
     */

    public final Locale getLocale()
    {
        if (locale == null)
            locale = Locale.getDefault();
        return locale;
    }

    /**
     * Set the current locale.
     *
     * @see java.util.Locale
     */
    public final Locale setLocale(Locale loc)
    {
        if (sealed) onSealedMutation();
        Locale result = locale;
        locale = loc;
        return result;
    }

    /**
     * Register an object to receive notifications when a bound property
     * has changed
     * @see java.beans.PropertyChangeEvent
     * @see #removePropertyChangeListener(java.beans.PropertyChangeListener)
     * @param l the listener
     */
    public final void addPropertyChangeListener(PropertyChangeListener l)
    {
        if (sealed) onSealedMutation();
        propertyListeners = Kit.addListener(propertyListeners, l);
    }

    /**
     * Remove an object from the list of objects registered to receive
     * notification of changes to a bounded property
     * @see java.beans.PropertyChangeEvent
     * @see #addPropertyChangeListener(java.beans.PropertyChangeListener)
     * @param l the listener
     */
    public final void removePropertyChangeListener(PropertyChangeListener l)
    {
        if (sealed) onSealedMutation();
        propertyListeners = Kit.removeListener(propertyListeners, l);
    }

    /**
     * Notify any registered listeners that a bounded property has changed
     * @see #addPropertyChangeListener(java.beans.PropertyChangeListener)
     * @see #removePropertyChangeListener(java.beans.PropertyChangeListener)
     * @see java.beans.PropertyChangeListener
     * @see java.beans.PropertyChangeEvent
     * @param  property  the bound property
     * @param  oldValue  the old value
     * @param  newValue  the new value
     */
    final void firePropertyChange(String property, Object oldValue,
                                  Object newValue)
    {
        Object listeners = propertyListeners;
        if (listeners != null) {
            firePropertyChangeImpl(listeners, property, oldValue, newValue);
        }
    }

    private void firePropertyChangeImpl(Object listeners, String property,
                                        Object oldValue, Object newValue)
    {
        for (int i = 0; ; ++i) {
            Object l = Kit.getListener(listeners, i);
            if (l == null)
                break;
            if (l instanceof PropertyChangeListener) {
                PropertyChangeListener pcl = (PropertyChangeListener)l;
                pcl.propertyChange(new PropertyChangeEvent(
                    this, property, oldValue, newValue));
            }
        }
    }

    /**
     * Report a warning using the error reporter for the current thread.
     *
     * @param message the warning message to report
     * @param sourceName a string describing the source, such as a filename
     * @param lineno the starting line number
     * @param lineSource the text of the line (may be null)
     * @param lineOffset the offset into lineSource where problem was detected
     * @see com.google.javascript.rhino.ErrorReporter
     */
    public static void reportWarning(String message, String sourceName,
                                     int lineno, String lineSource,
                                     int lineOffset)
    {
        Context cx = Context.getContext();
        cx.getErrorReporter().warning(message, sourceName, lineno,
                                      lineSource, lineOffset);
    }

    /**
     * Report a warning using the error reporter for the current thread.
     *
     * @param message the warning message to report
     * @see com.google.javascript.rhino.ErrorReporter
     */
    public static void reportWarning(String message)
    {
        int[] linep = { 0 };
        String filename = getSourcePositionFromStack(linep);
        Context.reportWarning(message, filename, linep[0], null, 0);
    }

    /**
     * Report an error using the error reporter for the current thread.
     *
     * @param message the error message to report
     * @param sourceName a string describing the source, such as a filename
     * @param lineno the starting line number
     * @param lineSource the text of the line (may be null)
     * @param lineOffset the offset into lineSource where problem was detected
     * @see com.google.javascript.rhino.ErrorReporter
     */
    public static void reportError(String message, String sourceName,
                                   int lineno, String lineSource,
                                   int lineOffset)
    {
        Context cx = getCurrentContext();
        if (cx != null) {
            cx.getErrorReporter().error(message, sourceName, lineno,
                                        lineSource, lineOffset);
        } else {
            throw new EvaluatorException(message, sourceName, lineno,
                                         lineSource, lineOffset);
        }
    }

    /**
     * Report an error using the error reporter for the current thread.
     *
     * @param message the error message to report
     * @see com.google.javascript.rhino.ErrorReporter
     */
    public static void reportError(String message)
    {
        int[] linep = { 0 };
        String filename = getSourcePositionFromStack(linep);
        Context.reportError(message, filename, linep[0], null, 0);
    }

    /**
     * Report a runtime error using the error reporter for the current thread.
     *
     * @param message the error message to report
     * @param sourceName a string describing the source, such as a filename
     * @param lineno the starting line number
     * @param lineSource the text of the line (may be null)
     * @param lineOffset the offset into lineSource where problem was detected
     * @return a runtime exception that will be thrown to terminate the
     *         execution of the script
     * @see com.google.javascript.rhino.ErrorReporter
     */
    public static EvaluatorException reportRuntimeError(String message,
                                                        String sourceName,
                                                        int lineno,
                                                        String lineSource,
                                                        int lineOffset)
    {
        Context cx = getCurrentContext();
        if (cx != null) {
            return cx.getErrorReporter().
                            runtimeError(message, sourceName, lineno,
                                         lineSource, lineOffset);
        } else {
            throw new EvaluatorException(message, sourceName, lineno,
                                         lineSource, lineOffset);
        }
    }

    static EvaluatorException reportRuntimeError0(String messageId)
    {
        String msg = ScriptRuntime.getMessage0(messageId);
        return reportRuntimeError(msg);
    }

    static EvaluatorException reportRuntimeError1(String messageId,
                                                  Object arg1)
    {
        String msg = ScriptRuntime.getMessage1(messageId, arg1);
        return reportRuntimeError(msg);
    }

    static EvaluatorException reportRuntimeError2(String messageId,
                                                  Object arg1, Object arg2)
    {
        String msg = ScriptRuntime.getMessage2(messageId, arg1, arg2);
        return reportRuntimeError(msg);
    }

    static EvaluatorException reportRuntimeError3(String messageId,
                                                  Object arg1, Object arg2,
                                                  Object arg3)
    {
        String msg = ScriptRuntime.getMessage3(messageId, arg1, arg2, arg3);
        return reportRuntimeError(msg);
    }

    static EvaluatorException reportRuntimeError4(String messageId,
                                                  Object arg1, Object arg2,
                                                  Object arg3, Object arg4)
    {
        String msg
            = ScriptRuntime.getMessage4(messageId, arg1, arg2, arg3, arg4);
        return reportRuntimeError(msg);
    }

    /**
     * Report a runtime error using the error reporter for the current thread.
     *
     * @param message the error message to report
     * @see com.google.javascript.rhino.ErrorReporter
     */
    public static EvaluatorException reportRuntimeError(String message)
    {
        int[] linep = { 0 };
        String filename = getSourcePositionFromStack(linep);
        return Context.reportRuntimeError(message, filename, linep[0], null, 0);
    }

    /**
     * Tell whether debug information is being generated.
     * @since 1.3
     */
    public final boolean isGeneratingDebug()
    {
        return generatingDebug;
    }

    /**
     * Tell whether source information is being generated.
     * @since 1.3
     */
    public final boolean isGeneratingSource()
    {
        return generatingSource;
    }

    /**
     * Specify whether or not source information should be generated.
     * <p>
     * Without source information, evaluating the "toString" method
     * on JavaScript functions produces only "[native code]" for
     * the body of the function.
     * Note that code generated without source is not fully ECMA
     * conformant.
     * @since 1.3
     */
    public final void setGeneratingSource(boolean generatingSource)
    {
        if (sealed) onSealedMutation();
        this.generatingSource = generatingSource;
    }

    /**
     * Get the current optimization level.
     * <p>
     * The optimization level is expressed as an integer between -1 and
     * 9.
     * @since 1.3
     *
     */
    public final int getOptimizationLevel()
    {
        return optimizationLevel;
    }

    public static boolean isValidOptimizationLevel(int optimizationLevel)
    {
        return -1 <= optimizationLevel && optimizationLevel <= 9;
    }

    public static void checkOptimizationLevel(int optimizationLevel)
    {
        if (isValidOptimizationLevel(optimizationLevel)) {
            return;
        }
        throw new IllegalArgumentException(
            "Optimization level outside [-1..9]: "+optimizationLevel);
    }

    /**
     * Get a value corresponding to a key.
     * <p>
     * Since the Context is associated with a thread it can be
     * used to maintain values that can be later retrieved using
     * the current thread.
     * <p>
     * Note that the values are maintained with the Context, so
     * if the Context is disassociated from the thread the values
     * cannot be retreived. Also, if private data is to be maintained
     * in this manner the key should be a java.lang.Object
     * whose reference is not divulged to untrusted code.
     * @param key the key used to lookup the value
     * @return a value previously stored using putThreadLocal.
     */
    public final Object getThreadLocal(Object key)
    {
        if (hashtable == null)
            return null;
        return hashtable.get(key);
    }

    /**
     * Put a value that can later be retrieved using a given key.
     * <p>
     * @param key the key used to index the value
     * @param value the value to save
     */
    public final void putThreadLocal(Object key, Object value)
    {
        if (sealed) onSealedMutation();
        if (hashtable == null)
            hashtable = new Hashtable<Object, Object>();
        hashtable.put(key, value);
    }

    /**
     * Remove values from thread-local storage.
     * @param key the key for the entry to remove.
     * @since 1.5 release 2
     */
    public final void removeThreadLocal(Object key)
    {
        if (sealed) onSealedMutation();
        if (hashtable == null)
            return;
        hashtable.remove(key);
    }

    /**
     * @deprecated
     * @see #FEATURE_DYNAMIC_SCOPE
     * @see #hasFeature(int)
     */
    @Deprecated
    public final boolean hasCompileFunctionsWithDynamicScope()
    {
        return compileFunctionsWithDynamicScopeFlag;
    }

    /**
     * @deprecated
     * @see #FEATURE_DYNAMIC_SCOPE
     * @see #hasFeature(int)
     */
    @Deprecated
    public final void setCompileFunctionsWithDynamicScope(boolean flag)
    {
        if (sealed) onSealedMutation();
        compileFunctionsWithDynamicScopeFlag = flag;
    }

    /**
     * Return the debugger context data associated with current context.
     * @return the debugger data, or null if debugger is not attached
     */
    public final Object getDebuggerContextData()
    {
        return debuggerData;
    }
    /**
     * Implementation of {@link Context#hasFeature(int featureIndex)}.
     * This can be used to customize {@link Context} without introducing
     * additional subclasses.
     */
    protected boolean hasFeature(int featureIndex)
    {
        int version;
        switch (featureIndex) {
          case Context.FEATURE_NON_ECMA_GET_YEAR:
           /*
            * During the great date rewrite of 1.3, we tried to track the
            * evolving ECMA standard, which then had a definition of
            * getYear which always subtracted 1900.  Which we
            * implemented, not realizing that it was incompatible with
            * the old behavior...  now, rather than thrash the behavior
            * yet again, we've decided to leave it with the - 1900
            * behavior and point people to the getFullYear method.  But
            * we try to protect existing scripts that have specified a
            * version...
            */
            version = getLanguageVersion();
            return (version == Context.VERSION_1_0
                    || version == Context.VERSION_1_1
                    || version == Context.VERSION_1_2);

          case Context.FEATURE_MEMBER_EXPR_AS_FUNCTION_NAME:
            return false;

          case Context.FEATURE_RESERVED_KEYWORD_AS_IDENTIFIER:
            return false;

          case Context.FEATURE_TO_STRING_AS_SOURCE:
            version = getLanguageVersion();
            return version == Context.VERSION_1_2;

          case Context.FEATURE_PARENT_PROTO_PROPRTIES:
            return true;

          case Context.FEATURE_E4X:
            version = getLanguageVersion();
            return (version == Context.VERSION_DEFAULT
                    || version >= Context.VERSION_1_6);

          case Context.FEATURE_DYNAMIC_SCOPE:
            return false;

          case Context.FEATURE_STRICT_VARS:
            return false;

          case Context.FEATURE_STRICT_EVAL:
            return false;

          case FEATURE_STRICT_MODE:
            // Make JSCompiler run in non=strict mode always.
            return false;

          case FEATURE_WARNING_AS_ERROR:
            // Let warnings stay warnings for now.
            return false;
        }
        // It is a bug to call the method with unknown featureIndex
        throw new IllegalArgumentException(String.valueOf(featureIndex));
    }

    /**
     * Get/Set threshold of executed instructions counter that triggers call to
     * <code>observeInstructionCount()</code>.
     * When the threshold is zero, instruction counting is disabled,
     * otherwise each time the run-time executes at least the threshold value
     * of script instructions, <code>observeInstructionCount()</code> will
     * be called.
     */
    public final int getInstructionObserverThreshold()
    {
        return instructionThreshold;
    }

    public final void setInstructionObserverThreshold(int threshold)
    {
        if (sealed) onSealedMutation();
        if (threshold < 0) throw new IllegalArgumentException();
        instructionThreshold = threshold;
    }

    /********** end of API **********/

    static Context getContext() {
        Context cx = getCurrentContext();
        if (cx == null) {
            throw new RuntimeException(
                "No Context associated with current Thread");
        }
        return cx;
    }

    final boolean isVersionECMA1()
    {
        return version == VERSION_DEFAULT || version >= VERSION_1_3;
    }

    static String getSourcePositionFromStack(int[] linep)
    {
        Context cx = getCurrentContext();
        if (cx == null)
            return null;
        /**
         * A bit of a hack, but the only way to get filename and line
         * number from an enclosing frame.
         */
        CharArrayWriter writer = new CharArrayWriter();
        RuntimeException re = new RuntimeException();
        re.printStackTrace(new PrintWriter(writer));
        String s = writer.toString();
        int open = -1;
        int close = -1;
        int colon = -1;
        for (int i=0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ':')
                colon = i;
            else if (c == '(')
                open = i;
            else if (c == ')')
                close = i;
            else if (c == '\n' && open != -1 && close != -1 && colon != -1 &&
                     open < colon && colon < close)
            {
                String fileStr = s.substring(open + 1, colon);
                if (!fileStr.endsWith(".java")) {
                    String lineStr = s.substring(colon + 1, close);
                    try {
                        linep[0] = Integer.parseInt(lineStr);
                        if (linep[0] < 0) {
                            linep[0] = 0;
                        }
                        return fileStr;
                    }
                    catch (NumberFormatException e) {
                        // fall through
                    }
                }
                open = close = colon = -1;
            }
        }

        return null;
    }

    public final boolean isGeneratingDebugChanged()
    {
        return generatingDebugChanged;
    }

    /**
     * Add a name to the list of names forcing the creation of real
     * activation objects for functions.
     *
     * @param name the name of the object to add to the list
     */
    public void addActivationName(String name)
    {
        if (sealed) onSealedMutation();
        if (activationNames == null)
            activationNames = new Hashtable<Object, Object>(5);
        activationNames.put(name, name);
    }

    /**
     * Check whether the name is in the list of names of objects
     * forcing the creation of activation objects.
     *
     * @param name the name of the object to test
     *
     * @return true if an function activation object is needed.
     */
    public final boolean isActivationNeeded(String name)
    {
        return activationNames != null && activationNames.containsKey(name);
    }

    /**
     * Remove a name from the list of names forcing the creation of real
     * activation objects for functions.
     *
     * @param name the name of the object to remove from the list
     */
    public void removeActivationName(String name)
    {
        if (sealed) onSealedMutation();
        if (activationNames != null)
            activationNames.remove(name);
    }

    private static String implementationVersion;

    private boolean sealed;
    private Object sealKey;

    // for Objects, Arrays to tag themselves as being printed out,
    // so they don't print themselves out recursively.
    // Use ObjToIntMap instead of java.util.HashSet for JDK 1.1 compatibility
    ObjToIntMap iterating;

    Object interpreterSecurityDomain;

    int version;

    private ErrorReporter errorReporter;
    private Locale locale;
    private boolean generatingDebug;
    private boolean generatingDebugChanged;
    private boolean generatingSource=true;
    boolean compileFunctionsWithDynamicScopeFlag;
    boolean useDynamicScope;
    private Object debuggerData;
    private int enterCount;
    private int optimizationLevel;
    private Object propertyListeners;
    private Hashtable<Object, Object> hashtable;

    /**
     * This is the list of names of objects forcing the creation of
     * function activation records.
     */
    Hashtable<Object, Object> activationNames;

    // For the interpreter to store the last frame for error reports etc.
    Object lastInterpreterFrame;

    // For the interpreter to store information about previous invocations
    // interpreter invocations
    ObjArray previousInterpreterInvocations;

    // For instruction counting (interpreter only)
    int instructionCount;
    int instructionThreshold;

    // It can be used to return the second index-like result from function
    int scratchIndex;

    // It can be used to return the second uint32 result from function
    long scratchUint32;
}
