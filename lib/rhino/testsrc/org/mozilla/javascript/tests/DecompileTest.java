package org.mozilla.javascript.tests;


import org.junit.Assert;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.Script;

/**
 * Test for {@link Context#decompileScript(Script, int)}.
 */
public class DecompileTest {

	/**
	 * As of head of trunk on 30.09.09, decompile of "new Date()" returns "new Date" without parentheses.
	 * @see <a href="https://bugzilla.mozilla.org/show_bug.cgi?id=519692">Bug 519692</a>
	 */
	@Test
	public void newObject0Arg()
	{
		final String source = "var x = new Date().getTime();";
		final ContextAction action = new ContextAction() {
			public Object run(final Context cx) {
				final Script script = cx.compileString(source, "my script", 0, null);
				Assert.assertEquals(source, cx.decompileScript(script, 4).trim());
				return null;
			}
		};
		Utils.runWithAllOptimizationLevels(action);
	}
}
