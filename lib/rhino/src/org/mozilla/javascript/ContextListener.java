/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// API class

package org.mozilla.javascript;

/**
 * @deprecated Embeddings that wish to customize newly created
 * {@link Context} instances should implement
 * {@link ContextFactory.Listener}.
 */
public interface ContextListener extends ContextFactory.Listener
{

    /**
     * @deprecated Rhino runtime never calls the method.
     */
    public void contextEntered(Context cx);

    /**
     * @deprecated Rhino runtime never calls the method.
     */
    public void contextExited(Context cx);
}
