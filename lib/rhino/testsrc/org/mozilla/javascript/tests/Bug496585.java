package org.mozilla.javascript.tests;

import org.mozilla.javascript.Function;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.Context;
import org.junit.Test;

public class Bug496585 {
    public void method(String one, Function function) {
        System.out.println("string+function");
    }

    public void method(String... strings) {
        System.out.println("string[]");
    }

    @Test
    public void callOverloadedFunction() {
        new ContextFactory().call(new ContextAction() {
            public Object run(Context cx) {
                cx.evaluateString(
                    cx.initStandardObjects(),
                    "new org.mozilla.javascript.tests.Bug496585().method('one', 'two', 'three')",
                    "<test>", 1, null);
                cx.evaluateString(
                    cx.initStandardObjects(),
                    "new org.mozilla.javascript.tests.Bug496585().method('one', function() {})",
                    "<test>", 1, null);
                return null;
            }
        });
    }
}
