/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/**
 *
 */
package org.mozilla.javascript.tests;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.ScriptableObject;

/**
 *
 */
public class Bug714204Test {
    private Context cx;
    private ScriptableObject scope;

    @Before
    public void setUp() {
        cx = Context.enter();
        scope = cx.initStandardObjects();
        cx.setLanguageVersion(170);
    }

    @After
    public void tearDown() {
        Context.exit();
    }

    @Test
    public void test_assign_this() {
        StringBuilder sb = new StringBuilder();
        sb.append("function F() {\n");
        sb.append("  [this.x] = arguments;\n");
        sb.append("}\n");
        sb.append("var f = new F('a');\n");
        sb.append("(f.x == 'a')\n");
        Script script = cx.compileString(sb.toString(), "<eval>", 1, null);
        Object result = script.exec(cx, scope);
        assertEquals(Boolean.TRUE, result);
    }

    @Test(expected = EvaluatorException.class)
    public void test_var_this() {
        StringBuilder sb = new StringBuilder();
        sb.append("function F() {\n");
        sb.append("  var [this.x] = arguments;\n");
        sb.append("}\n");
        cx.compileString(sb.toString(), "<eval>", 1, null);
    }

    @Test(expected = EvaluatorException.class)
    public void test_let_this() {
        StringBuilder sb = new StringBuilder();
        sb.append("function F() {\n");
        sb.append("  let [this.x] = arguments;\n");
        sb.append("}\n");
        cx.compileString(sb.toString(), "<eval>", 1, null);
    }

    @Test(expected = EvaluatorException.class)
    public void test_const_this() {
        StringBuilder sb = new StringBuilder();
        sb.append("function F() {\n");
        sb.append("  const [this.x] = arguments;\n");
        sb.append("}\n");
        cx.compileString(sb.toString(), "<eval>", 1, null);
    }

    @Test(expected = EvaluatorException.class)
    public void test_args_this() {
        StringBuilder sb = new StringBuilder();
        sb.append("function F([this.x]) {\n");
        sb.append("}\n");
        cx.compileString(sb.toString(), "<eval>", 1, null);
    }
}
