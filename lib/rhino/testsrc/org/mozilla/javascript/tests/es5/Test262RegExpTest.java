package org.mozilla.javascript.tests.es5;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.ScriptableObject;

/**
 */
public class Test262RegExpTest {
    private Context cx;
    private ScriptableObject scope;

    @Before
    public void setUp() {
        cx = Context.enter();
        scope = cx.initStandardObjects();
    }

    @After
    public void tearDown() {
        Context.exit();
    }

    @Test
    public void testS15_10_2_9_A1_T4() {
        String source = "/\\b(\\w+) \\2\\b/.test('do you listen the the band');";
        String sourceName = "Conformance/15_Native/15.10_RegExp_Objects/15.10.2_Pattern_Semantics/15.10.2.9_AtomEscape/S15.10.2.9_A1_T4.js";
        cx.evaluateString(scope, source, sourceName, 0, null);
    }

    @Test
    public void testS15_10_2_11_A1_T2() {
        List<String> sources = new ArrayList<String>();
        sources.add("/\\1/.exec('');");
        sources.add("/\\2/.exec('');");
        sources.add("/\\3/.exec('');");
        sources.add("/\\4/.exec('');");
        sources.add("/\\5/.exec('');");
        sources.add("/\\6/.exec('');");
        sources.add("/\\7/.exec('');");
        sources.add("/\\8/.exec('');");
        sources.add("/\\9/.exec('');");
        sources.add("/\\10/.exec('');");

        String sourceName = "Conformance/15_Native/15.10_RegExp_Objects/15.10.2_Pattern_Semantics/15.10.2.11_DecimalEscape/S15.10.2.11_A1_T2.js";
        for (String source : sources) {
            cx.evaluateString(scope, source, sourceName, 0, null);
        }
    }

    @Test
    public void testS15_10_2_11_A1_T3() {
        String source = "/(?:A)\\2/.exec('AA');";
        String sourceName = "Conformance/15_Native/15.10_RegExp_Objects/15.10.2_Pattern_Semantics/15.10.2.11_DecimalEscape/S15.10.2.11_A1_T3.js";
        cx.evaluateString(scope, source, sourceName, 0, null);
    }

    @Test(expected = EcmaError.class)
    public void testS15_10_2_15_A1_T4() {
        String source = "(new RegExp('[\\\\Db-G]').exec('a'))";
        String sourceName = "Conformance/15_Native/15.10_RegExp_Objects/15.10.2_Pattern_Semantics/15.10.2.15_NonemptyClassRanges/S15.10.2.15_A1_T4.js";
        cx.evaluateString(scope, source, sourceName, 0, null);
    }

    @Test(expected = EcmaError.class)
    public void testS15_10_2_15_A1_T5() {
        String source = "(new RegExp('[\\\\sb-G]').exec('a'))";
        String sourceName = "Conformance/15_Native/15.10_RegExp_Objects/15.10.2_Pattern_Semantics/15.10.2.15_NonemptyClassRanges/S15.10.2.15_A1_T5.js";
        cx.evaluateString(scope, source, sourceName, 0, null);
    }

    @Test(expected = EcmaError.class)
    public void testS15_10_2_15_A1_T6() {
        String source = "(new RegExp('[\\\\Sb-G]').exec('a'))";
        String sourceName = "Conformance/15_Native/15.10_RegExp_Objects/15.10.2_Pattern_Semantics/15.10.2.15_NonemptyClassRanges/S15.10.2.15_A1_T6.js";
        cx.evaluateString(scope, source, sourceName, 0, null);
    }

    @Test(expected = EcmaError.class)
    public void testS15_10_2_15_A1_T7() {
        String source = "(new RegExp('[\\\\wb-G]').exec('a'))";
        String sourceName = "Conformance/15_Native/15.10_RegExp_Objects/15.10.2_Pattern_Semantics/15.10.2.15_NonemptyClassRanges/S15.10.2.15_A1_T7.js";
        cx.evaluateString(scope, source, sourceName, 0, null);
    }

    @Test(expected = EcmaError.class)
    public void testS15_10_2_15_A1_T8() {
        String source = "(new RegExp('[\\\\Wb-G]').exec('a'))";
        String sourceName = "Conformance/15_Native/15.10_RegExp_Objects/15.10.2_Pattern_Semantics/15.10.2.15_NonemptyClassRanges/S15.10.2.15_A1_T8.js";
        cx.evaluateString(scope, source, sourceName, 0, null);
    }
}
