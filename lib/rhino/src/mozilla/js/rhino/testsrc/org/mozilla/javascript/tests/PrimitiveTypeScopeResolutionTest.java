package org.mozilla.javascript.tests;

import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * Unit tests for <a href="https://bugzilla.mozilla.org/show_bug.cgi?id=374918">Bug 374918 -
 * String primitive prototype wrongly resolved when used with many top scopes</a>
 */
public class PrimitiveTypeScopeResolutionTest
{
    /**
     */
    @Test
    public void functionCall() {
        String str2 = "function f() {\n"
            + "String.prototype.foo = function() { return 'from 2' }; \n"
            + "var s2 = 's2';\n"
            + "var s2Foo = s2.foo();\n"
            + "if (s2Foo != 'from 2') throw 's2 got: ' + s2Foo;\n" // fails
            + "}";

        String str1 = "String.prototype.foo = function() { return 'from 1'};"
            + "scope2.f()";
    	testWithTwoScopes(str1, str2);
    }

    /**
     */
    @Test
    public void propertyAccess() {
        String str2 = "function f() { String.prototype.foo = 'from 2'; \n"
            + "var s2 = 's2';\n"
            + "var s2Foo = s2.foo;\n"
            + "if (s2Foo != 'from 2') throw 's2 got: ' + s2Foo;\n" // fails
            + "}";

        String str1 = "String.prototype.foo = 'from 1'; scope2.f()";
    	testWithTwoScopes(str1, str2);
    }

    /**
     */
    @Test
    public void elementAccess() {
        String str2 = "function f() { String.prototype.foo = 'from 2'; \n"
            + "var s2 = 's2';\n"
            + "var s2Foo = s2['foo'];\n"
            + "if (s2Foo != 'from 2') throw 's2 got: ' + s2Foo;\n" // fails
            + "}";

        String str1 = "String.prototype.foo = 'from 1'; scope2.f()";
    	testWithTwoScopes(str1, str2);
    }

    private void testWithTwoScopes(final String scriptScope1,
                                   final String scriptScope2)
    {
    	final ContextAction action = new ContextAction()
    	{
    		public Object run(final Context cx)
    		{
    	        final Scriptable scope1 = cx.initStandardObjects(
    	            new MySimpleScriptableObject("scope1"));
    	        final Scriptable scope2 = cx.initStandardObjects(
    	            new MySimpleScriptableObject("scope2"));
    	        cx.evaluateString(scope2, scriptScope2, "source2", 1, null);

    	        scope1.put("scope2", scope1, scope2);

    	        return cx.evaluateString(scope1, scriptScope1, "source1", 1,
    	                                 null);
    		}
    	};
    	Utils.runWithAllOptimizationLevels(action);
    }

	/**
	 * Simple utility allowing to better see the concerned scope while debugging
	 */
	static class MySimpleScriptableObject extends ScriptableObject
	{
        private static final long serialVersionUID = 1L;
        private String label_;
		MySimpleScriptableObject(String label)
		{
			label_ = label;
		}
		@Override
		public String getClassName()
		{
			return "MySimpleScriptableObject";
		}

		@Override
		public String toString()
		{
			return label_;
		}
	}

    public static class MyObject extends ScriptableObject {
        private static final long serialVersionUID = 1L;

    @Override
      public String getClassName()
      {
          return "MyObject";
      }

      public Object readPropFoo(final Scriptable s) {
          return ScriptableObject.getProperty(s, "foo");
      }
  }

  /**
   * Test that FunctionObject use the right top scope to convert a primitive
   * to an object
   */
  @Test
  public void functionObjectPrimitiveToObject() throws Exception {
      final String scriptScope2 = "function f() {\n"
          + "String.prototype.foo = 'from 2'; \n"
          + "var s2 = 's2';\n"
          + "var s2Foo = s2.foo;\n"
          + "var s2FooReadByFunction = myObject.readPropFoo(s2);\n"
          + "if (s2Foo != s2FooReadByFunction)\n"
          + "throw 's2 got: ' + s2FooReadByFunction;\n"
          + "}";

      // define object with custom method
      final MyObject myObject = new MyObject();
      final String[] functionNames = { "readPropFoo" };
      myObject.defineFunctionProperties(functionNames, MyObject.class,
          ScriptableObject.EMPTY);

      final String scriptScope1 = "String.prototype.foo = 'from 1'; scope2.f()";

      final ContextAction action = new ContextAction()
      {
          public Object run(final Context cx)
          {
              final Scriptable scope1 = cx.initStandardObjects(
                  new MySimpleScriptableObject("scope1"));
              final Scriptable scope2 = cx.initStandardObjects(
                  new MySimpleScriptableObject("scope2"));

              scope2.put("myObject", scope2, myObject);
              cx.evaluateString(scope2, scriptScope2, "source2", 1, null);

              scope1.put("scope2", scope1, scope2);

              return cx.evaluateString(scope1, scriptScope1, "source1", 1, null);
          }
      };
      Utils.runWithAllOptimizationLevels(action);
  }
}
