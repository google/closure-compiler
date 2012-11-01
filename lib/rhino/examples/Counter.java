/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import org.mozilla.javascript.*;
import org.mozilla.javascript.annotations.JSFunction;
import org.mozilla.javascript.annotations.JSConstructor;
import org.mozilla.javascript.annotations.JSGetter;

public class Counter extends ScriptableObject {
    private static final long serialVersionUID = 438270592527335642L;

    // The zero-argument constructor used by Rhino runtime to create instances
    public Counter() { }

    // @JSConstructor annotation defines the JavaScript constructor
    @JSConstructor
    public Counter(int a) { count = a; }

    // The class name is defined by the getClassName method
    @Override
    public String getClassName() { return "Counter"; }

    // The method getCount defines the count property.
    @JSGetter
    public int getCount() { return count++; }

    // Methods can be defined the @JSFunction annotation.
    // Here we define resetCount for JavaScript.
    @JSFunction
    public void resetCount() { count = 0; }

    private int count;
}
