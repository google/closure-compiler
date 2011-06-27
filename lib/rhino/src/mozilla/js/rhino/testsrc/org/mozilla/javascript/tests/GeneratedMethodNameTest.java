package org.mozilla.javascript.tests;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;

import junit.framework.TestCase;

/**
 * Takes care that the name of the method generated for a function "looks like" the original function name.
 * See https://bugzilla.mozilla.org/show_bug.cgi?id=460726
 */
public class GeneratedMethodNameTest extends TestCase
{
	public void testStandardFunction() throws Exception {
		final String scriptCode = "function myFunc() {\n"
			+ " var m = javaNameGetter.readCurrentFunctionJavaName();\n"
			+ "  if (m != 'myFunc') throw 'got '  + m;"
			+ "}\n"
			+ "myFunc();";
		doTest(scriptCode);
	}

	public void testFunctionDollar() throws Exception {
		final String scriptCode = "function $() {\n"
			+ " var m = javaNameGetter.readCurrentFunctionJavaName();\n"
			+ "  if (m != '$') throw 'got '  + m;"
			+ "}\n"
			+ "$();";
		doTest(scriptCode);
	}

	public void testScriptName() throws Exception {
		final String scriptCode =
		  "var m = javaNameGetter.readCurrentFunctionJavaName();\n"
			+ "if (m != 'script') throw 'got '  + m;";
		doTest(scriptCode);
	}

	public void testConstructor() throws Exception {
		final String scriptCode = "function myFunc() {\n"
			+ " var m = javaNameGetter.readCurrentFunctionJavaName();\n"
			+ "  if (m != 'myFunc') throw 'got '  + m;"
			+ "}\n"
			+ "new myFunc();";
		doTest(scriptCode);
	}

	public void testAnonymousFunction() throws Exception {
		final String scriptCode = "var myFunc = function() {\n"
			+ " var m = javaNameGetter.readCurrentFunctionJavaName();\n"
			+ "  if (m != 'anonymous') throw 'got '  + m;"
			+ "}\n"
			+ "myFunc();";
		doTest(scriptCode);
	}

	public class JavaNameGetter {
	    public String readCurrentFunctionJavaName() {
            final Throwable t = new RuntimeException();
            // remove prefix and suffix of method name
            return t.getStackTrace()[8].getMethodName().
                replaceFirst("_[^_]*_(.*)_[^_]*", "$1");
	    }
	}

	public void doTest(final String scriptCode) throws Exception {
		final Context cx = ContextFactory.getGlobal().enterContext();
		try {
            Scriptable topScope = cx.initStandardObjects();
    		topScope.put("javaNameGetter", topScope, new JavaNameGetter());
    		Script script = cx.compileString(scriptCode, "myScript", 1, null);
    		script.exec(cx, topScope);
		} finally {
		    Context.exit();
		}
	}
}
