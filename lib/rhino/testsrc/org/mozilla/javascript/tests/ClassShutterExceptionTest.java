/**
 *
 */
package org.mozilla.javascript.tests;

import junit.framework.TestCase;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.EvaluatorException;

/**
 */
public class ClassShutterExceptionTest extends TestCase {
    private static Context.ClassShutterSetter classShutterSetter;

    /**
     * Define a ClassShutter that prevents access to all Java classes.
     */
    static class OpaqueShutter implements ClassShutter {
        public boolean visibleToScripts(String name) {
            return false;
        }
    }

    public void helper(String source) {
        Context cx = Context.enter();
        Context.ClassShutterSetter setter = cx.getClassShutterSetter();
        try {
            Scriptable globalScope = cx.initStandardObjects();
            if (setter == null) {
                setter = classShutterSetter;
            } else {
                classShutterSetter = setter;
            }
            setter.setClassShutter(new OpaqueShutter());
            cx.evaluateString(globalScope, source, "test source", 1, null);
        } finally {
            setter.setClassShutter(null);
            Context.exit();
        }
    }

    public void testClassShutterException() {
        try {
            helper("java.lang.System.out.println('hi');");
            fail();
        } catch (RhinoException e) {
            // OpaqueShutter should prevent access to java.lang...
            return;
        }
    }

    public void testThrowingException() {
        // JavaScript exceptions with no reference to Java
        // should not be affected by the ClassShutter
        helper("try { throw 3; } catch (e) { }");
    }

    public void testThrowingEcmaError() {
        try {
            // JavaScript exceptions with no reference to Java
            // should not be affected by the ClassShutter
            helper("friggin' syntax error!");
            fail("Should have thrown an exception");
        } catch (EvaluatorException e) {
            // should have thrown an exception for syntax error
        }
    }

    public void testThrowingEvaluatorException() {
            // JavaScript exceptions with no reference to Java
            // should not be affected by the ClassShutter
            helper("try { eval('for;if;else'); } catch (e) { }");
    }
 }
