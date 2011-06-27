package org.mozilla.javascript.tests;

import junit.framework.TestCase;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.ScriptableObject;

/**
 * Test for overloaded array concat with non-dense arg.
 * See https://bugzilla.mozilla.org/show_bug.cgi?id=477604
 */
public class ArrayConcatTest extends TestCase {

    public void testArrayConcat() {
		final String script = "var a = ['a0', 'a1'];\n"
			+ "a[3] = 'a3';\n"
			+ "var b = ['b1', 'b2'];\n"
			+ "b.concat(a)";

		final ContextAction action = new ContextAction()
		{
			public Object run(final Context _cx)
			{
				final ScriptableObject scope = _cx.initStandardObjects();
				final Object result = _cx.evaluateString(scope, script, "test script", 0, null);
				assertEquals("b1,b2,a0,a1,,a3", Context.toString(result));
				return null;
			}
		};

		Utils.runWithAllOptimizationLevels(action);
    }
}
