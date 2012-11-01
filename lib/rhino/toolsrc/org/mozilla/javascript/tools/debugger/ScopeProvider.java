/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.javascript.tools.debugger;

import org.mozilla.javascript.Scriptable;

/**
 * Interface to provide a scope object for script evaluation to the debugger.
 */
public interface ScopeProvider {

    /**
     * Returns the scope object to be used for script evaluation.
     */
    Scriptable getScope();
}