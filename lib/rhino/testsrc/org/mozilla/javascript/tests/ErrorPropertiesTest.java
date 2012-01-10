package org.mozilla.javascript.tests;

import org.junit.Assert;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.ScriptableObject;


/**
 * Unit tests for <a href="https://bugzilla.mozilla.org/show_bug.cgi?id=549604">bug 549604</a>.
 * This tests verify the properties of a JS exception and ensures that they don't change with different optimization levels.
 *
 */
public class ErrorPropertiesTest {
    final static String LS = System.getProperty("line.separator");

    private void testScriptStackTrace(final String script, final String expectedStackTrace) {
        testScriptStackTrace(script, expectedStackTrace, -1);
        testScriptStackTrace(script, expectedStackTrace, 0);
        testScriptStackTrace(script, expectedStackTrace, 1);
    }

    private void testScriptStackTrace(final String script, final String expectedStackTrace,
                                      final int optimizationLevel) {
        try {
            Utils.executeScript(script, optimizationLevel);
        }
        catch (final RhinoException e) {
            Assert.assertEquals(expectedStackTrace, e.getScriptStackTrace());
        }
    }

    @Test
    public void fileName() {
        testIt("try { null.method() } catch (e) { e.fileName }", "myScript.js");
        testIt("try { null.property } catch (e) { e.fileName }", "myScript.js");
    }

    @Test
    public void lineNumber() {
        testIt("try { null.method() } catch (e) { e.lineNumber }", 1);
        testIt("try {\n null.method() \n} catch (e) { e.lineNumber }", 2);
        testIt("\ntry \n{\n null.method() \n} catch (e) { e.lineNumber }", 4);

        testIt("function f() {\n null.method(); \n}\n try { f() } catch (e) { e.lineNumber }", 2);
    }

    @Test
    public void defaultStack() {
        RhinoException.useMozillaStackStyle(false);
        testScriptStackTrace("null.method()", "\tat myScript.js:1" + LS);
        final String script = "function f() \n{\n  null.method();\n}\nf();\n";
        testScriptStackTrace(script, "\tat myScript.js:3 (f)" + LS + "\tat myScript.js:5" + LS);
        testIt("try { null.method() } catch (e) { e.stack }", "\tat myScript.js:1" + LS);
        final String expectedStack = "\tat myScript.js:2 (f)" + LS + "\tat myScript.js:4" + LS;
        testIt("function f() {\n null.method(); \n}\n try { f() } catch (e) { e.stack }", expectedStack);
    }

    @Test
    public void mozillaStack() {
        RhinoException.useMozillaStackStyle(true);
        testScriptStackTrace("null.method()", "@myScript.js:1" + LS);
        final String script = "function f() \n{\n  null.method();\n}\nf();\n";
        testScriptStackTrace(script, "f()@myScript.js:3" + LS + "@myScript.js:5" + LS);
        testIt("try { null.method() } catch (e) { e.stack }", "@myScript.js:1" + LS);
        final String expectedStack = "f()@myScript.js:2" + LS + "@myScript.js:4" + LS;
        testIt("function f() {\n null.method(); \n}\n try { f() } catch (e) { e.stack }", expectedStack);
    }

    private void testIt(final String script, final Object expected) {
        final ContextAction action = new ContextAction() {
            public Object run(final Context cx) {
                try {
                    final ScriptableObject scope = cx.initStandardObjects();
                    final Object o = cx.evaluateString(scope, script,
                            "myScript.js", 1, null);
                    Assert.assertEquals(expected, o);
                    return o;
                }
                catch (final RuntimeException e) {
                    throw e;
                }
                catch (final Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        Utils.runWithAllOptimizationLevels(action);
    }
}
