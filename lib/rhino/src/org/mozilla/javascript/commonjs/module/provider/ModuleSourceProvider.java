/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.commonjs.module.provider;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.mozilla.javascript.Scriptable;

/**
 * Implemented by objects that can provide the source text for the script. The
 * design of the interface supports cache revalidation.
 * @version $Id: ModuleSourceProvider.java,v 1.3 2011/04/07 20:26:12 hannes%helma.at Exp $
 */
public interface ModuleSourceProvider
{
    /**
     * A special return value for {@link #loadSource(String, Scriptable,
     * Object)} and {@link #loadSource(URI, URI, Object)} that signifies that the
     * cached representation is still valid according to the passed validator.
     */
    public static final ModuleSource NOT_MODIFIED = new ModuleSource(null,
            null, null, null, null);

    /**
     * Returns the script source of the requested module. More specifically, it
     * resolves the module ID to a resource. If it can not resolve it, null is
     * returned. If the caller passes a non-null validator, and the source
     * provider recognizes it, and the validator applies to the same resource
     * that the provider would use to load the source, and the validator
     * validates the current cached representation of the resource (using
     * whatever semantics for validation that this source provider implements),
     * then {@link #NOT_MODIFIED} should be returned. Otherwise, it should
     * return a {@link ModuleSource} object with the actual source text of the
     * module, preferrably a validator for it, and a security domain, where
     * applicable.
     * @param moduleId the ID of the module. An implementation must only accept
     * an absolute ID, starting with a term.
     * @param paths the value of the require() function's "paths" attribute. If
     * the require() function is sandboxed, it will be null, otherwise it will
     * be a JavaScript Array object. It is up to the provider implementation
     * whether and how it wants to honor the contents of the array.
     * @param validator a validator for an existing loaded and cached module.
     * This will either be null, or an object that this source provider
     * returned earlier as part of a {@link ModuleSource}. It can be used to
     * validate the existing cached module and avoid reloading it.
     * @return a script representing the code of the module. Null should be
     * returned if the script is not found. {@link #NOT_MODIFIED} should be
     * returned if the passed validator validates the current representation of
     * the module (the currently cached module script).
     * @throws IOException if there was an I/O problem reading the script
     * @throws URISyntaxException if the final URI could not be constructed.
     * @throws IllegalArgumentException if the module ID is syntactically not a
     * valid absolute module identifier.
     */
    public ModuleSource loadSource(String moduleId, Scriptable paths, Object validator)
            throws IOException, URISyntaxException;

    /**
     * Returns the script source of the requested module from the given URI.
     * The URI is absolute but does not contain a file name extension such as
     * ".js", which may be specific to the ModuleSourceProvider implementation.
     * <p>
     * If the resource is not found, null is returned. If the caller passes a
     * non-null validator, and the source provider recognizes it, and the
     * validator applies to the same resource that the provider would use to
     * load the source, and the validator validates the current cached
     * representation of the resource (using whatever semantics for validation
     * that this source provider implements), then {@link #NOT_MODIFIED}
     * should be returned. Otherwise, it should return a {@link ModuleSource}
     * object with the actual source text of the module, preferrably a
     * validator for it, and a security domain, where applicable.
     * @param uri the absolute URI from which to load the module source, but
     * without an extension such as ".js".
     * @param baseUri the module path base URI from which <code>uri</code>
     * was derived.
     * @param validator a validator for an existing loaded and cached module.
     * This will either be null, or an object that this source provider
     * returned earlier as part of a {@link ModuleSource}. It can be used to
     * validate the existing cached module and avoid reloading it.
     * @return a script representing the code of the module. Null should be
     * returned if the script is not found. {@link #NOT_MODIFIED} should be
     * returned if the passed validator validates the current representation of
     * the module (the currently cached module script).
     * @throws IOException if there was an I/O problem reading the script
     * @throws URISyntaxException if the final URI could not be constructed
     */
    public ModuleSource loadSource(URI uri, URI baseUri, Object validator)
            throws IOException, URISyntaxException;
}