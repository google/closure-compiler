package org.mozilla.javascript.tests;

import junit.framework.TestCase;


import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.ScriptableObject;

/**
 * Test for delete that should apply for properties defined in prototype chain.
 * See https://bugzilla.mozilla.org/show_bug.cgi?id=510504
 */
public class DeletePropertyTest extends TestCase {
	/**
	 * delete should not delete anything in the prototype chain.
	 */
	@Test
	public void testDeletePropInPrototype() throws Exception {
		final String script = "Array.prototype.foo = function() {};\n"
			+ "Array.prototype[1] = function() {};\n"
			+ "var t = [];\n"
			+ "[].foo();\n"
			+ "for (var i in t) delete t[i];\n"
			+ "[].foo();\n"
			+ "[][1]();\n";

		final ContextAction action = new ContextAction()
		{
			public Object run(final Context _cx)
			{
				final ScriptableObject scope = _cx.initStandardObjects();
				final Object result = _cx.evaluateString(scope, script, "test script", 0, null);
				return null;
			}
		};

		Utils.runWithAllOptimizationLevels(action);
	}
}