/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// API class

package org.mozilla.javascript;

/**
 * This class provides support for implementing Java-style synchronized
 * methods in Javascript.
 *
 * Synchronized functions are created from ordinary Javascript
 * functions by the <code>Synchronizer</code> constructor, e.g.
 * <code>new Packages.org.mozilla.javascript.Synchronizer(fun)</code>.
 * The resulting object is a function that establishes an exclusive
 * lock on the <code>this</code> object of its invocation.
 *
 * The Rhino shell provides a short-cut for the creation of
 * synchronized methods: <code>sync(fun)</code> has the same effect as
 * calling the above constructor.
 *
 * @see org.mozilla.javascript.Delegator
 */

public class Synchronizer extends Delegator {

    private Object syncObject;

    /**
     * Create a new synchronized function from an existing one.
     *
     * @param obj the existing function
     */
    public Synchronizer(Scriptable obj) {
        super(obj);
    }

    /**
     * Create a new synchronized function from an existing one using
     * an explicit object as synchronization object.
     *
     * @param obj the existing function
     * @param syncObject the object to synchronized on
     */
    public Synchronizer(Scriptable obj, Object syncObject) {
        super(obj);
        this.syncObject = syncObject;
    }

    /**
     * @see org.mozilla.javascript.Function#call
     */
    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj,
                       Object[] args)
    {
        Object sync = syncObject != null ? syncObject : thisObj;
        synchronized(sync instanceof Wrapper ? ((Wrapper)sync).unwrap() : sync) {
            return ((Function)obj).call(cx,scope,thisObj,args);
        }
    }
}
