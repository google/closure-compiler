/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.commonjs.module.provider;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * A base implementation for all module script providers that actually load
 * module scripts. Performs validation of identifiers, allows loading from
 * preferred locations (attempted before require.paths), from require.paths
 * itself, and from fallback locations (attempted after require.paths). Note
 * that while this base class strives to be as generic as possible, it does
 * have loading from an URI built into its design, for the simple reason that
 * the require.paths is defined in terms of URIs.
 * @version $Id: ModuleSourceProviderBase.java,v 1.3 2011/04/07 20:26:12 hannes%helma.at Exp $
 */
public abstract class ModuleSourceProviderBase implements
        ModuleSourceProvider, Serializable
{
    private static final long serialVersionUID = 1L;

    public ModuleSource loadSource(String moduleId, Scriptable paths,
            Object validator) throws IOException, URISyntaxException
    {
        if(!entityNeedsRevalidation(validator)) {
            return NOT_MODIFIED;
        }

        ModuleSource moduleSource = loadFromPrivilegedLocations(
                moduleId, validator);
        if(moduleSource != null) {
            return moduleSource;
        }
        if(paths != null) {
            moduleSource = loadFromPathArray(moduleId, paths,
                    validator);
            if(moduleSource != null) {
                return moduleSource;
            }
        }
        return loadFromFallbackLocations(moduleId, validator);
    }

    public ModuleSource loadSource(URI uri, URI base, Object validator)
            throws IOException, URISyntaxException {
        return loadFromUri(uri, base, validator);
    }

    private ModuleSource loadFromPathArray(String moduleId,
            Scriptable paths, Object validator) throws IOException
    {
        final long llength = ScriptRuntime.toUint32(
                ScriptableObject.getProperty(paths, "length"));
        // Yeah, I'll ignore entries beyond Integer.MAX_VALUE; so sue me.
        int ilength = llength > Integer.MAX_VALUE ? Integer.MAX_VALUE :
            (int)llength;

        for(int i = 0; i < ilength; ++i) {
            final String path = ensureTrailingSlash(
                    ScriptableObject.getTypedProperty(paths, i, String.class));
            try {
                URI uri =  new URI(path);
                if (!uri.isAbsolute()) {
                    uri = new File(path).toURI().resolve("");
                }
                final ModuleSource moduleSource = loadFromUri(
                        uri.resolve(moduleId), uri, validator);
                if(moduleSource != null) {
                    return moduleSource;
                }
            }
            catch(URISyntaxException e) {
                throw new MalformedURLException(e.getMessage());
            }
        }
        return null;
    }

    private static String ensureTrailingSlash(String path) {
        return path.endsWith("/") ? path : path.concat("/");
    }

    /**
     * Override to determine whether according to the validator, the cached
     * module script needs revalidation. A validator can carry expiry
     * information. If the cached representation is not expired, it doesn'
     * t need revalidation, otherwise it does. When no cache revalidation is
     * required, the external resource will not be contacted at all, so some
     * level of expiry (staleness tolerance) can greatly enhance performance.
     * The default implementation always returns true so it will always require
     * revalidation.
     * @param validator the validator
     * @return returns true if the cached module needs revalidation.
     */
    protected boolean entityNeedsRevalidation(Object validator) {
        return true;
    }

    /**
     * Override in a subclass to load a module script from a logical URI. The
     * URI is absolute but does not have a file name extension such as ".js".
     * It is up to the ModuleSourceProvider implementation to add such an
     * extension.
     * @param uri the URI of the script, without file name extension.
     * @param base the base URI the uri was resolved from.
     * @param validator a validator that can be used to revalidate an existing
     * cached source at the URI. Can be null if there is no cached source
     * available.
     * @return the loaded module script, or null if it can't be found, or
     * {@link ModuleSourceProvider#NOT_MODIFIED} if it revalidated the existing
     * cached source against the URI.
     * @throws IOException if the module script was found, but an I/O exception
     * prevented it from being loaded.
     * @throws URISyntaxException if the final URI could not be constructed
     */
    protected abstract ModuleSource loadFromUri(URI uri, URI base,
            Object validator) throws IOException, URISyntaxException;

    /**
     * Override to obtain a module source from privileged locations. This will
     * be called before source is attempted to be obtained from URIs specified
     * in require.paths.
     * @param moduleId the ID of the module
     * @param validator a validator that can be used to validate an existing
     * cached script. Can be null if there is no cached script available.
     * @return the loaded module script, or null if it can't be found in the
     * privileged locations, or {@link ModuleSourceProvider#NOT_MODIFIED} if
     * the existing cached module script is still valid.
     * @throws IOException if the module script was found, but an I/O exception
     * prevented it from being loaded.
     * @throws URISyntaxException if the final URI could not be constructed.
     */
    protected ModuleSource loadFromPrivilegedLocations(
            String moduleId, Object validator)
            throws IOException, URISyntaxException
    {
        return null;
    }

    /**
     * Override to obtain a module source from fallback locations. This will
     * be called after source is attempted to be obtained from URIs specified
     * in require.paths.
     * @param moduleId the ID of the module
     * @param validator a validator that can be used to validate an existing
     * cached script. Can be null if there is no cached script available.
     * @return the loaded module script, or null if it can't be found in the
     * privileged locations, or {@link ModuleSourceProvider#NOT_MODIFIED} if
     * the existing cached module script is still valid.
     * @throws IOException if the module script was found, but an I/O exception
     * prevented it from being loaded.
     * @throws URISyntaxException if the final URI could not be constructed.
     */
    protected ModuleSource loadFromFallbackLocations(
            String moduleId, Object validator)
            throws IOException, URISyntaxException
    {
        return null;
    }
}