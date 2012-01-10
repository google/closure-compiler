/**
 *
 */
package org.mozilla.javascript.tests;

import junit.framework.TestCase;

import org.mozilla.javascript.*;

/**
 * See https://bugzilla.mozilla.org/show_bug.cgi?id=412433
 */
public class Bug412433Test extends TestCase {
    public void testMalformedJavascript2()
    {
        Context context = Context.enter();
        try {
	        ScriptableObject scope = context.initStandardObjects();
	        context.evaluateString(scope, "\"\".split(/[/?,/&]/)", "", 0, null);
        } finally {
            Context.exit();
        }
    }
}
