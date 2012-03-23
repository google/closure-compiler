/**
 *
 */
package org.mozilla.javascript.tests;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.ast.AstRoot;

/**
 *
 */
public class Bug687669Test {
    private Context cx;
    private ScriptableObject scope;

    @Before
    public void setUp() {
        cx = Context.enter();
        cx.setLanguageVersion(Context.VERSION_1_8);
        scope = cx.initStandardObjects();
    }

    @After
    public void tearDown() {
        Context.exit();
    }

    private Object eval(CharSequence cs) {
        return cx.evaluateString(scope, cs.toString(), "<eval>", 1, null);
    }

    private AstRoot parse(CharSequence cs) {
        CompilerEnvirons compilerEnv = new CompilerEnvirons();
        compilerEnv.initFromContext(cx);
        ErrorReporter compilationErrorReporter = compilerEnv.getErrorReporter();
        Parser p = new Parser(compilerEnv, compilationErrorReporter);
        return p.parse(cs.toString(), "<eval>", 1);
    }

    private String toSource(CharSequence cs) {
        return parse(cs).toSource();
    }

    @Test
    public void testEval() {
        // test EmptyStatement node doesn't infer with return values (in
        // contrast to wrapping EmptyExpression into an ExpressionStatement)
        assertEquals(1d, eval("1;;;;"));
        assertEquals(Undefined.instance, eval("(function(){1;;;;})()"));
        assertEquals(1d, eval("(function(){return 1;;;;})()"));
    }

    @Test
    public void testToSource() {
        assertEquals("L1:\n  ;\n", toSource("L1:;"));
        assertEquals("L1:\n  ;\na = 1;\n", toSource("L1:; a=1;"));

        assertEquals("if (1) \n;\n", toSource("if(1);"));
        assertEquals("if (1) \n;\na = 1;\n", toSource("if(1); a=1;"));

        assertEquals("if (1) \na = 1;\n", toSource("if(1)a=1;"));
        assertEquals("if (1) \na = 1;\na = 1;\n", toSource("if(1)a=1; a=1;"));

        assertEquals("if (1) \n;\n;\n;\n;\n", toSource("if(1);;;;"));
    }

}
