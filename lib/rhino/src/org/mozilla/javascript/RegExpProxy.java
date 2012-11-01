/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

/**
 * A proxy for the regexp package, so that the regexp package can be
 * loaded optionally.
 *
 */
public interface RegExpProxy
{
    // Types of regexp actions
    public static final int RA_MATCH   = 1;
    public static final int RA_REPLACE = 2;
    public static final int RA_SEARCH  = 3;

    public boolean isRegExp(Scriptable obj);

    public Object compileRegExp(Context cx, String source, String flags);

    public Scriptable wrapRegExp(Context cx, Scriptable scope,
                                 Object compiled);

    public Object action(Context cx, Scriptable scope,
                         Scriptable thisObj, Object[] args,
                         int actionType);

    public int find_split(Context cx, Scriptable scope, String target,
                          String separator, Scriptable re,
                          int[] ip, int[] matchlen,
                          boolean[] matched, String[][] parensp);

    public Object js_split(Context _cx, Scriptable _scope,
                           String thisString, Object[] _args);
}
