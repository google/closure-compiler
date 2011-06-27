package org.mozilla.javascript.tests;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Script;

import junit.framework.TestCase;

/**
 * Takes care that the class name of the generated class "looks like"
 * the provided script name.
 * See https://bugzilla.mozilla.org/show_bug.cgi?id=460283
 */
public class GeneratedClassNameTest extends TestCase
{
	public void testGeneratedClassName() throws Exception {
		doTest("myScript_js", "myScript.js");
		doTest("foo", "foo");
		doTest("c", "");
		doTest("_1", "1");
		doTest("_", "_");
		doTest("unnamed_script", null);
		doTest("some_dir_some_foo_js", "some/dir/some/foo.js");
		doTest("some_dir_some_foo_js", "some\\dir\\some\\foo.js");
		doTest("_12_foo_34_js", "12 foo 34.js");
	}

	private void doTest(final String expectedName, final String scriptName)
	    throws Exception
	{
	    final Script script = (Script) ContextFactory.getGlobal().call(
	      new ContextAction() {
	        public Object run(final Context context) {
	          return context.compileString("var f = 1", scriptName, 1, null);
	        }
	      });

	    // remove serial number
	    String name = script.getClass().getSimpleName();
	    assertEquals(expectedName, name.substring(0, name.lastIndexOf('_')));
	}
}
