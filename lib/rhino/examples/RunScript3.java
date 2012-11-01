/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import org.mozilla.javascript.*;

/**
 * RunScript3: Example of using JavaScript objects
 *
 * Collects its arguments from the command line, executes the
 * script, and then ...
 *
 */
public class RunScript3 {
    public static void main(String args[])
    {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();

            // Collect the arguments into a single string.
            String s = "";
            for (int i=0; i < args.length; i++) {
                s += args[i];
            }

            // Now evaluate the string we've collected. We'll ignore the result.
            cx.evaluateString(scope, s, "<cmd>", 1, null);

            // Print the value of variable "x"
            Object x = scope.get("x", scope);
            if (x == Scriptable.NOT_FOUND) {
                System.out.println("x is not defined.");
            } else {
                System.out.println("x = " + Context.toString(x));
            }

            // Call function "f('my arg')" and print its result.
            Object fObj = scope.get("f", scope);
            if (!(fObj instanceof Function)) {
                System.out.println("f is undefined or not a function.");
            } else {
                Object functionArgs[] = { "my arg" };
                Function f = (Function)fObj;
                Object result = f.call(cx, scope, scope, functionArgs);
                String report = "f('my args') = " + Context.toString(result);
                System.out.println(report);
            }
        } finally {
            Context.exit();
        }
    }
}
