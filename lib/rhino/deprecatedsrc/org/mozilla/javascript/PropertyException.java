/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// API class

package org.mozilla.javascript;

/**
 * @deprecated This exception is no longer thrown by Rhino runtime.
 */
public class PropertyException extends RuntimeException
{
    static final long serialVersionUID = -8221564865490676219L;

    public PropertyException(String detail) {
        super(detail);
    }

}
