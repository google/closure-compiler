/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import org.mozilla.javascript.*;

/**
 * RunScript4: Execute scripts in an environment that includes the
 *             example Counter class.
 *
 */
public class RunScript4 {
    public static void main(String args[])
        throws Exception
    {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();

            // Use the Counter class to define a Counter constructor
            // and prototype in JavaScript.
            ScriptableObject.defineClass(scope, Counter.class);

            // Create an instance of Counter and assign it to
            // the top-level variable "myCounter". This is
            // equivalent to the JavaScript code
            //    myCounter = new Counter(7);
            Object[] arg = { new Integer(7) };
            Scriptable myCounter = cx.newObject(scope, "Counter", arg);
            scope.put("myCounter", scope, myCounter);

            String s = "";
            for (int i=0; i < args.length; i++) {
                s += args[i];
            }
            Object result = cx.evaluateString(scope, s, "<cmd>", 1, null);
            System.err.println(Context.toString(result));
        } finally {
            Context.exit();
        }
    }

}
