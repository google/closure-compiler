/* -*- Mode: java; tab-width: 4; indent-tabs-mode: 1; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.javascript.tools.idswitch;

public class IdValuePair
{
    public final int idLength;
    public final String id;
    public final String value;

    private int lineNumber;

    public IdValuePair(String id, String value) {
        this.idLength = id.length();
        this.id = id;
        this.value = value;
    }

    public int getLineNumber() { return lineNumber; }

    public void setLineNumber(int value) { lineNumber = value; }
}

