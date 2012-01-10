package org.mozilla.javascript.tests;

import org.mozilla.javascript.*;
import junit.framework.TestCase;

/**
 * Example of defining global functions.
 *
 */
public class DefineFunctionPropertiesTest extends TestCase {
    ScriptableObject global;
    static Object key = "DefineFunctionPropertiesTest";

    /**
     * Demonstrates how to create global functions in JavaScript
     * from static methods defined in Java.
     */
    @Override
    public void setUp() {
        Context cx = Context.enter();
        try {
            global = cx.initStandardObjects();
            String[] names = { "f", "g" };
            global.defineFunctionProperties(names,
                    DefineFunctionPropertiesTest.class,
                    ScriptableObject.DONTENUM);
        } finally {
            Context.exit();
        }
    }

    /**
     * Simple global function that doubles its input.
     */
    public static int f(int a) {
        return a * 2;
    }

    /**
     * Simple test: call 'f' defined above
     */
    public void testSimpleFunction() {
        Context cx = Context.enter();
        try {
            Object result = cx.evaluateString(global, "f(7) + 1",
                    "test source", 1, null);
            assertEquals(15.0, result);
        } finally {
            Context.exit();
        }
    }

    /**
     * More complicated example: this form of call allows variable
     * argument lists, and allows access to the 'this' object. For
     * a global function, the 'this' object is the global object.
     * In this case we look up a value that we associated with the global
     * object using {@link ScriptableObject#getAssociatedValue(Object)}.
     */
    public static Object g(Context cx, Scriptable thisObj, Object[] args,
            Function funObj)
    {
        Object arg = args.length > 0 ? args[0] : Undefined.instance;
        Object privateValue = Undefined.instance;
        if (thisObj instanceof ScriptableObject) {
            privateValue =
                ((ScriptableObject) thisObj).getAssociatedValue(key);
        }
        return arg.toString() + privateValue;
    }

    /**
     * Associate a value with the global scope and call function 'g'
     * defined above.
     */
    public void testPrivateData() {
        Context cx = Context.enter();
        try {
            global.associateValue(key, "bar");
            Object result = cx.evaluateString(global, "g('foo');",
                    "test source", 1, null);
            assertEquals("foobar", result);
        } finally {
            Context.exit();
        }
    }
}
