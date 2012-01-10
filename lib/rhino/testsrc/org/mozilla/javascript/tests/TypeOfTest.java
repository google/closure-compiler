package org.mozilla.javascript.tests;

import junit.framework.TestCase;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * Takes care that it's possible to customize the result of the typeof operator.
 * See https://bugzilla.mozilla.org/show_bug.cgi?id=463996
 * Includes fix and test for https://bugzilla.mozilla.org/show_bug.cgi?id=453360
 */
public class TypeOfTest extends TestCase
{
	public static class Foo extends ScriptableObject {
        private static final long serialVersionUID = -8771045033217033529L;
        private final String typeOfValue_;

        public Foo(final String _typeOfValue)
		{
        	typeOfValue_ = _typeOfValue;
		}

        @Override
        public String getTypeOf()
        {
        	return typeOfValue_;
        }

		@Override
		public String getClassName()
		{
			return "Foo";
		}
	}

	/**
	 * ECMA 11.4.3 says that typeof on host object is Implementation-dependent
	 */
	public void testCustomizeTypeOf() throws Exception
	{
		testCustomizeTypeOf("object", new Foo("object"));
		testCustomizeTypeOf("blabla", new Foo("blabla"));
	}

	/**
	 * ECMA 11.4.3 says that typeof on host object is Implementation-dependent
	 */
	public void test0() throws Exception
	{
        final Function f = new BaseFunction()
        {
        	@Override
        	public Object call(Context _cx, Scriptable _scope, Scriptable _thisObj,
        			Object[] _args)
        	{
        		return _args[0].getClass().getName();
        	}
        };
		final ContextAction action = new ContextAction()
		{
			public Object run(final Context context)
			{
				final Scriptable scope = context.initStandardObjects();
				scope.put("myObj", scope, f);
				return context.evaluateString(scope, "typeof myObj", "test script", 1, null);
			}
		};
		doTest("function", action);
	}

	private void testCustomizeTypeOf(final String expected, final Scriptable obj)
	{
		final ContextAction action = new ContextAction()
		{
			public Object run(final Context context)
			{
				final Scriptable scope = context.initStandardObjects();
				scope.put("myObj", scope, obj);
				return context.evaluateString(scope, "typeof myObj", "test script", 1, null);
			}
		};
		doTest(expected, action);
	}

	/**
	 * See https://bugzilla.mozilla.org/show_bug.cgi?id=453360
	 */
	public void testBug453360() throws Exception
	{
		doTest("object", "typeof new RegExp();");
		doTest("object", "typeof /foo/;");
	}

	private void doTest(String expected, final String script)
	{
		final ContextAction action = new ContextAction()
		{
			public Object run(final Context context)
			{
				final Scriptable scope = context.initStandardObjects();
				return context.evaluateString(scope, script, "test script", 1, null);
			}
		};
		doTest(expected, action);
	}

	private void doTest(final String expected, final ContextAction action)
	{
		doTest(-1, expected, action);
		doTest(0, expected, action);
		doTest(1, expected, action);
	}

	private void doTest(final int optimizationLevel, final String expected, final ContextAction action)
	{
		Object o = new ContextFactory().call(new ContextAction()
			{
				public Object run(final Context context)
				{
					context.setOptimizationLevel(optimizationLevel);
					return Context.toString(action.run(context));
				}
			});
		assertEquals(expected, o);
	}
}