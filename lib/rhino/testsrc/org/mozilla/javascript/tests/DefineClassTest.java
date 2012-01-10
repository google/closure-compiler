package org.mozilla.javascript.tests;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.mozilla.javascript.*;
import org.mozilla.javascript.annotations.*;

public class DefineClassTest {

    Scriptable scope;

    @Test
    public void testAnnotatedHostObject() {
        Context cx = Context.enter();
        try {
            Object result = evaluate(cx, "a = new AnnotatedHostObject(); a.initialized;");
            assertEquals(result, Boolean.TRUE);
            assertEquals(evaluate(cx, "a.instanceFunction();"), "instanceFunction");
            assertEquals(evaluate(cx, "a.namedFunction();"), "namedFunction");
            assertEquals(evaluate(cx, "AnnotatedHostObject.staticFunction();"), "staticFunction");
            assertEquals(evaluate(cx, "AnnotatedHostObject.namedStaticFunction();"), "namedStaticFunction");
            assertNull(evaluate(cx, "a.foo;"));
            assertEquals(evaluate(cx, "a.foo = 'foo'; a.foo;"), "FOO");
            assertEquals(evaluate(cx, "a.bar;"), "bar");

            // Setting a property with no setting should be silently
            // ignored in non-strict mode.
            evaluate(cx, "a.bar = 'new bar'");
            assertEquals("bar", evaluate(cx, "a.bar;"));
        } finally {
            Context.exit();
        }
    }

    @Test
    public void testTraditionalHostObject() {
        Context cx = Context.enter();
        try {
            Object result = evaluate(cx, "t = new TraditionalHostObject(); t.initialized;");
            assertEquals(result, Boolean.TRUE);
            assertEquals(evaluate(cx, "t.instanceFunction();"), "instanceFunction");
            assertEquals(evaluate(cx, "TraditionalHostObject.staticFunction();"), "staticFunction");
            assertNull(evaluate(cx, "t.foo;"));
            assertEquals(evaluate(cx, "t.foo = 'foo'; t.foo;"), "FOO");
            assertEquals(evaluate(cx, "t.bar;"), "bar");

            // Setting a property with no setting should be silently
            // ignored in non-strict mode.
            evaluate(cx, "t.bar = 'new bar'");
            assertEquals("bar", evaluate(cx, "t.bar;"));
        } finally {
            Context.exit();
        }
    }

    private Object evaluate(Context cx, String str) {
        return cx.evaluateString(scope, str, "<testsrc>", 0, null);
    }


    @Before
    public void init() throws Exception {
        Context cx = Context.enter();
        try {
            scope = cx.initStandardObjects();
            ScriptableObject.defineClass(scope, AnnotatedHostObject.class);
            ScriptableObject.defineClass(scope, TraditionalHostObject.class);
        } finally {
            Context.exit();
        }
    }

    public static class AnnotatedHostObject extends ScriptableObject {

        String foo, bar = "bar";

        public AnnotatedHostObject() {}

        @Override
        public String getClassName() {
            return "AnnotatedHostObject";
        }

        @JSConstructor
        public void jsConstructorMethod() {
            put("initialized", this, Boolean.TRUE);
        }

        @JSFunction
        public Object instanceFunction() {
            return "instanceFunction";
        }

        @JSFunction("namedFunction")
        public Object someFunctionName() {
            return "namedFunction";
        }

        @JSStaticFunction
        public static Object staticFunction() {
            return "staticFunction";
        }

        @JSStaticFunction("namedStaticFunction")
        public static Object someStaticFunctionName() {
            return "namedStaticFunction";
        }

        @JSGetter
        public String getFoo() {
            return foo;
        }

        @JSSetter
        public void setFoo(String foo) {
            this.foo = foo.toUpperCase();
        }

        @JSGetter("bar")
        public String getMyBar() {
            return bar;
        }

        public void setBar(String bar) {
            this.bar = bar.toUpperCase();
        }
    }

    public static class TraditionalHostObject extends ScriptableObject {

        String foo, bar = "bar";

        public TraditionalHostObject() {}

        @Override
        public String getClassName() {
            return "TraditionalHostObject";
        }

        public void jsConstructor() {
            put("initialized", this, Boolean.TRUE);
        }

        public Object jsFunction_instanceFunction() {
            return "instanceFunction";
        }

        public static Object jsStaticFunction_staticFunction() {
            return "staticFunction";
        }

        public String jsGet_foo() {
            return foo;
        }

        public void jsSet_foo(String foo) {
            this.foo = foo.toUpperCase();
        }

        public String jsGet_bar() {
            return bar;
        }

        // not a JS setter
        public void setBar(String bar) {
            this.bar = bar.toUpperCase();
        }

    }

}
