/**
 *
 */
package org.mozilla.javascript.tests;

import junit.framework.TestCase;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;

/**
 */
public class StackTraceTest extends TestCase {
    final static String LS = System.getProperty("line.separator");

	/**
	 * As of CVS head on May, 11. 2009, stacktrace information is lost when a call to some
	 * native function has been made.
	 */
    public void testFailureStackTrace() {
        RhinoException.useMozillaStackStyle(false);
        final String source1 = "function f2() { throw 'hello'; }; f2();";
        final String source2 = "function f2() { 'H'.toLowerCase(); throw 'hello'; }; f2();";
        final String source3 = "function f2() { new java.lang.String('H').toLowerCase(); throw 'hello'; }; f2();";
        final String result = "\tat test.js (f2)" + LS + "\tat test.js" + LS;

        runWithExpectedStackTrace(source1, result);
        runWithExpectedStackTrace(source2, result);
        runWithExpectedStackTrace(source3, result);
    }

	private void runWithExpectedStackTrace(final String _source, final String _expectedStackTrace)
	{
        final ContextAction action = new ContextAction() {
        	public Object run(Context cx) {
        		final Scriptable scope = cx.initStandardObjects();
        		try {
        			cx.evaluateString(scope, _source, "test.js", 0, null);
        		}
        		catch (final JavaScriptException e)
        		{
        			assertEquals(_expectedStackTrace, e.getScriptStackTrace());
        			return null;
        		}
        		throw new RuntimeException("Exception expected!");
        	}
        };
        Utils.runWithOptimizationLevel(action, -1);
	}
 }
