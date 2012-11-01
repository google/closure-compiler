/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// API class

package org.mozilla.javascript.debug;

import org.mozilla.javascript.Context;

/**
Interface to implement if the application is interested in receiving debug
information.
*/
public interface Debugger {

/**
Called when compilation of a particular function or script into internal
bytecode is done.

@param cx current Context for this thread
@param fnOrScript object describing the function or script
@param source the function or script source
*/
    void handleCompilationDone(Context cx, DebuggableScript fnOrScript,
                               String source);

/**
Called when execution entered a particular function or script.

@return implementation of DebugFrame which receives debug information during
        the function or script execution or null otherwise
*/
    DebugFrame getFrame(Context cx, DebuggableScript fnOrScript);
}
