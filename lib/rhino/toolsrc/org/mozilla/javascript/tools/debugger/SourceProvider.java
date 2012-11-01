/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.javascript.tools.debugger;

import org.mozilla.javascript.debug.DebuggableScript;


/**
 * Interface to provide a source of scripts to the debugger.
 * @version $Id: SourceProvider.java,v 1.1 2009/10/23 12:49:58 szegedia%freemail.hu Exp $
 */
public interface SourceProvider {

    /**
     * Returns the source of the script.
     * @param script the script object
     * @return the source code of the script, or null if it can not be provided
     * (the provider is not expected to decompile the script, so if it doesn't
     * have a readily available source text, it is free to return null).
     */
    String getSource(DebuggableScript script);
}