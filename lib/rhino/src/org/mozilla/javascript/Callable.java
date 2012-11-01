/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

/**
 * Generic notion of callable object that can execute some script-related code
 * upon request with specified values for script scope and this objects.
 */
public interface Callable
{
    /**
     * Perform the call.
     *
     * @param cx the current Context for this thread
     * @param scope the scope to use to resolve properties.
     * @param thisObj the JavaScript <code>this</code> object
     * @param args the array of arguments
     * @return the result of the call
     */
    public Object call(Context cx, Scriptable scope, Scriptable thisObj,
                       Object[] args);
}

