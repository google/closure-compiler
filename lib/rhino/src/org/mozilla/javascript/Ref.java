/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.io.Serializable;

/**
 * Generic notion of reference object that know how to query/modify the
 * target objects based on some property/index.
 */
public abstract class Ref implements Serializable
{
    
    static final long serialVersionUID = 4044540354730911424L;
    
    public boolean has(Context cx)
    {
        return true;
    }

    public abstract Object get(Context cx);

    public abstract Object set(Context cx, Object value);

    public boolean delete(Context cx)
    {
        return false;
    }

}

