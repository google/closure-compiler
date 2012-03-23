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
import org.mozilla.javascript.ast.AstRoot;

/**
 *
 */
public class Bug689308Test {
    private Context cx;

    @Before
    public void setUp() {
        cx = Context.enter();
        cx.setLanguageVersion(Context.VERSION_1_8);
    }

    @After
    public void tearDown() {
        Context.exit();
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
    public void testToSourceArray() {
        assertEquals("[];\n", toSource("[]"));
        assertEquals("[,];\n", toSource("[,]"));
        assertEquals("[, ,];\n", toSource("[,,]"));
        assertEquals("[, , ,];\n", toSource("[,,,]"));

        assertEquals("[1];\n", toSource("[1]"));
        assertEquals("[1];\n", toSource("[1,]"));
        assertEquals("[, 1];\n", toSource("[,1]"));
        assertEquals("[1, 1];\n", toSource("[1,1]"));
    }
}
