/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.commonjs.module.provider;

import java.net.URLConnection;

import org.mozilla.javascript.Context;

/**
 * Interface for URL connection based security domain providers. Used by
 * {@link UrlModuleSourceProvider} to create Rhino security domain objects for
 * newly loaded scripts (see {@link Context#compileReader(java.io.Reader,
 * String, int, Object)}) based on the properties obtainable through their URL
 * connection.
 * @version $Id: UrlConnectionSecurityDomainProvider.java,v 1.3 2011/04/07 20:26:12 hannes%helma.at Exp $
 */
public interface UrlConnectionSecurityDomainProvider
{
    /**
     * Create a new security domain object for a script source identified by
     * its URL connection.
     * @param urlConnection the URL connection of the script source
     * @return the security domain object for the script source. Can be null if
     * no security domain object can be created, although it is advisable for
     * the implementations to be able to create a security domain object for
     * any URL connection.
     */
    public Object getSecurityDomain(URLConnection urlConnection);
}