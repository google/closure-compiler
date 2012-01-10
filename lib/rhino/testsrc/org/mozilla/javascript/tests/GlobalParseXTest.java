/**
 *
 */
package org.mozilla.javascript.tests;

import junit.framework.TestCase;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.Scriptable;

/**
 * Tests for global functions parseFloat and parseInt.
 */
public class GlobalParseXTest extends TestCase {

	/**
	 * Test for bug #501972
	 * https://bugzilla.mozilla.org/show_bug.cgi?id=501972
	 * Leading whitespaces should be ignored with following white space chars
	 * (see ECMA spec 15.1.2.3)
	 * <TAB>, <SP>, <NBSP>, <FF>, <VT>, <CR>, <LF>, <LS>, <PS>, <USP>
	 */
    public void testParseFloatAndIntWhiteSpaces() {
    	testParseFloatWhiteSpaces("\\u00A0 "); // <NBSP>

    	testParseFloatWhiteSpaces("\\t ");
    	testParseFloatWhiteSpaces("\\u00A0 "); // <NBSP>
    	testParseFloatWhiteSpaces("\\u000C "); // <FF>
    	testParseFloatWhiteSpaces("\\u000B "); // <VT>
    	testParseFloatWhiteSpaces("\\u000D "); // <CR>
    	testParseFloatWhiteSpaces("\\u000A "); // <LF>
    	testParseFloatWhiteSpaces("\\u2028 "); // <LS>
    	testParseFloatWhiteSpaces("\\u2029 "); // <PS>
    }

    private void testParseFloatWhiteSpaces(final String prefix) {
        assertEvaluates("789", "String(parseInt('" + prefix + "789 '))");
        assertEvaluates("7.89", "String(parseFloat('" + prefix + "7.89 '))");
    }

	/**
	 * Test for bug #531436
	 * https://bugzilla.mozilla.org/show_bug.cgi?id=531436
	 * Trailing noise should be ignored
	 * (see ECMA spec 15.1.2.3)
	 */
    public void testParseFloatTrailingNoise() {
    	testParseFloat("7890", "789e1");
    	testParseFloat("7890", "789E1");
    	testParseFloat("7890", "789E+1");
    	testParseFloat("7890", "789E+1e");
    	testParseFloat("789", "7890E-1");
    	testParseFloat("789", "7890E-1e");

    	testParseFloat("789", "789hello");
    	testParseFloat("789", "789e");
    	testParseFloat("789", "789E");
    	testParseFloat("789", "789e+");
    	testParseFloat("789", "789Efgh");
    	testParseFloat("789", "789efgh");
    	testParseFloat("789", "789e-");
    	testParseFloat("789", "789e-hello");
    	testParseFloat("789", "789e+hello");
    	testParseFloat("789", "789+++hello");
    	testParseFloat("789", "789-e-+hello");
    	testParseFloat("789", "789e+e++hello");
    	testParseFloat("789", "789e-e++hello");
    }

    private void testParseFloat(final String expected, final String value) {
        assertEvaluates(expected, "String(parseFloat('" + value + "'))");
    }

    private void assertEvaluates(final Object expected, final String source) {
        final ContextAction action = new ContextAction() {
            public Object run(Context cx) {
                final Scriptable scope = cx.initStandardObjects();
                final Object rep = cx.evaluateString(scope, source, "test.js",
                        0, null);
                assertEquals(expected, rep);
                return null;
            }
        };
        Utils.runWithAllOptimizationLevels(action);
    }
 }
