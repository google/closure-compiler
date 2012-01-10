package org.mozilla.javascript.tests;

import java.lang.reflect.Method;

import junit.framework.TestCase;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * Takes care that it's possible to set <code>null</code> value
 * when using custom setter for a {@link Scriptable} object.
 * See https://bugzilla.mozilla.org/show_bug.cgi?id=461138
 */
public class CustomSetterAcceptNullScriptableTest extends TestCase
{
	public static class Foo extends ScriptableObject {
        private static final long serialVersionUID = -8771045033217033529L;

        @Override
		public String getClassName()
		{
			return "Foo";
		}

		public void setMyProp(final Foo2 s) { }
	}

	public static class Foo2 extends ScriptableObject {
        private static final long serialVersionUID = -8880603824656138628L;

        @Override
		public String getClassName()
		{
			return "Foo2";
		}
	}

	public void testSetNullForScriptableSetter() throws Exception {

		final String scriptCode = "foo.myProp = new Foo2();\n"
			+ "foo.myProp = null;";

		final ContextFactory factory = new ContextFactory();
		final Context cx = factory.enterContext();

		try {
	        final ScriptableObject topScope = cx.initStandardObjects();
	        final Foo foo = new Foo();

	        // define custom setter method
	        final Method setMyPropMethod = Foo.class.getMethod("setMyProp", Foo2.class);
	        foo.defineProperty("myProp", null, null, setMyPropMethod, ScriptableObject.EMPTY);

	        topScope.put("foo", topScope, foo);

	        ScriptableObject.defineClass(topScope, Foo2.class);

	        cx.evaluateString(topScope, scriptCode, "myScript", 1, null);
		}
		finally {
			Context.exit();
		}
	}
}
