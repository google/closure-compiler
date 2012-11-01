/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import org.mozilla.javascript.*;

/**
 * An example illustrating how to create a JavaScript object and retrieve
 * properties and call methods.
 * <p>
 * Output should be:
 * <pre>
 * count = 0
 * count = 1
 * resetCount
 * count = 0
 * </pre>
 */
public class CounterTest {

    public static void main(String[] args) throws Exception
    {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            ScriptableObject.defineClass(scope, Counter.class);

            Scriptable testCounter = cx.newObject(scope, "Counter");

            Object count = ScriptableObject.getProperty(testCounter, "count");
            System.out.println("count = " + count);

            count = ScriptableObject.getProperty(testCounter, "count");
            System.out.println("count = " + count);

            ScriptableObject.callMethod(testCounter,
                                        "resetCount",
                                        new Object[0]);
            System.out.println("resetCount");

            count = ScriptableObject.getProperty(testCounter, "count");
            System.out.println("count = " + count);
        } finally {
            Context.exit();
        }
    }

}
