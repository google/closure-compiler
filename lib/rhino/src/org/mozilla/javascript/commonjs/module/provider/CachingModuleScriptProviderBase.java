/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.commonjs.module.provider;

import java.io.Reader;
import java.io.Serializable;
import java.net.URI;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.commonjs.module.ModuleScript;
import org.mozilla.javascript.commonjs.module.ModuleScriptProvider;

/**
 * Abstract base class that implements caching of loaded module scripts. It
 * uses a {@link ModuleSourceProvider} to obtain the source text of the
 * scripts. It supports a cache revalidation mechanism based on validator
 * objects returned from the {@link ModuleSourceProvider}. Instances of this
 * class and its subclasses are thread safe (and written to perform decently
 * under concurrent access).
 * @version $Id: CachingModuleScriptProviderBase.java,v 1.3 2011/04/07 20:26:12 hannes%helma.at Exp $
 */
public abstract class CachingModuleScriptProviderBase
implements ModuleScriptProvider, Serializable
{
    private static final long serialVersionUID = 1L;

    private static final int loadConcurrencyLevel =
        Runtime.getRuntime().availableProcessors() * 8;
    private static final int loadLockShift;
    private static final int loadLockMask;
    private static final int loadLockCount;
    static {
        int sshift = 0;
        int ssize = 1;
        while (ssize < loadConcurrencyLevel) {
            ++sshift;
            ssize <<= 1;
        }
        loadLockShift = 32 - sshift;
        loadLockMask = ssize - 1;
        loadLockCount = ssize;
    }
    private final Object[] loadLocks = new Object[loadLockCount]; {
        for(int i = 0; i < loadLocks.length; ++i) {
            loadLocks[i] = new Object();
        }
    }

    private final ModuleSourceProvider moduleSourceProvider;

    /**
     * Creates a new module script provider with the specified source.
     * @param moduleSourceProvider provider for modules' source code
     */
    protected CachingModuleScriptProviderBase(
            ModuleSourceProvider moduleSourceProvider) {
        this.moduleSourceProvider = moduleSourceProvider;
    }

    public ModuleScript getModuleScript(Context cx, String moduleId,
            URI moduleUri, URI baseUri, Scriptable paths) throws Exception
    {
        final CachedModuleScript cachedModule1 = getLoadedModule(moduleId);
        final Object validator1 = getValidator(cachedModule1);
        final ModuleSource moduleSource = (moduleUri == null)
                ? moduleSourceProvider.loadSource(moduleId, paths, validator1)
                : moduleSourceProvider.loadSource(moduleUri, baseUri, validator1);
        if(moduleSource == ModuleSourceProvider.NOT_MODIFIED) {
            return cachedModule1.getModule();
        }
        if(moduleSource == null) {
            return null;
        }
        final Reader reader = moduleSource.getReader();
        try {
            final int idHash = moduleId.hashCode();
            synchronized(loadLocks[(idHash >>> loadLockShift) & loadLockMask]) {
                final CachedModuleScript cachedModule2 = getLoadedModule(moduleId);
                if(cachedModule2 != null) {
                    if(!equal(validator1, getValidator(cachedModule2))) {
                        return cachedModule2.getModule();
                    }
                }
                final URI sourceUri = moduleSource.getUri();
                final ModuleScript moduleScript = new ModuleScript(
                        cx.compileReader(reader, sourceUri.toString(), 1,
                                moduleSource.getSecurityDomain()),
                        sourceUri, moduleSource.getBase());
                putLoadedModule(moduleId, moduleScript,
                        moduleSource.getValidator());
                return moduleScript;
            }
        }
        finally {
            reader.close();
        }
    }

    /**
     * Store a loaded module script for later retrieval using
     * {@link #getLoadedModule(String)}.
     * @param moduleId the ID of the module
     * @param moduleScript the module script
     * @param validator the validator for the module's source text entity
     */
    protected abstract void putLoadedModule(String moduleId,
            ModuleScript moduleScript, Object validator);

    /**
     * Retrieves an already loaded moduleScript stored using
     * {@link #putLoadedModule(String, ModuleScript, Object)}.
     * @param moduleId the ID of the module
     * @return a cached module script, or null if the module is not loaded.
     */
    protected abstract CachedModuleScript getLoadedModule(String moduleId);

    /**
     * Instances of this class represent a loaded and cached module script.
     * @version $Id: CachingModuleScriptProviderBase.java,v 1.3 2011/04/07 20:26:12 hannes%helma.at Exp $
     */
    public static class CachedModuleScript {
        private final ModuleScript moduleScript;
        private final Object validator;

        /**
         * Creates a new cached module script.
         * @param moduleScript the module script itself
         * @param validator a validator for the moduleScript's source text
         * entity.
         */
        public CachedModuleScript(ModuleScript moduleScript, Object validator) {
            this.moduleScript = moduleScript;
            this.validator = validator;
        }

        /**
         * Returns the module script.
         * @return the module script.
         */
        ModuleScript getModule() {
            return moduleScript;
        }

        /**
         * Returns the validator for the module script's source text entity.
         * @return the validator for the module script's source text entity.
         */
        Object getValidator() {
            return validator;
        }
    }

    private static Object getValidator(CachedModuleScript cachedModule) {
        return cachedModule == null ? null : cachedModule.getValidator();
    }

    private static boolean equal(Object o1, Object o2) {
        return o1 == null ? o2 == null : o1.equals(o2);
    }

    /**
     * Returns the internal concurrency level utilized by caches in this JVM.
     * @return the internal concurrency level utilized by caches in this JVM.
     */
    protected static int getConcurrencyLevel() {
        return loadLockCount;
    }
}