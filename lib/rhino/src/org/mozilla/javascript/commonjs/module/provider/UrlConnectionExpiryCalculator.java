/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.commonjs.module.provider;

import java.net.URLConnection;

/**
 * Implemented by objects that can be used as heuristic strategies for
 * calculating the expiry of a cached resource in cases where the server of the
 * resource doesn't provide explicit expiry information.
 * @version $Id: UrlConnectionExpiryCalculator.java,v 1.3 2011/04/07 20:26:12 hannes%helma.at Exp $
 */
public interface UrlConnectionExpiryCalculator
{
    /**
     * Given a URL connection, returns a calculated heuristic expiry time (in
     * terms of milliseconds since epoch) for the resource.
     * @param urlConnection the URL connection for the resource
     * @return the expiry for the resource
     */
    public long calculateExpiry(URLConnection urlConnection);
}
