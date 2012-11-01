/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.commonjs.module.provider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.mozilla.javascript.commonjs.module.ModuleScript;

/**
 * A module script provider that uses a module source provider to load modules
 * and caches the loaded modules. It strongly references the loaded modules,
 * thus a module once loaded will not be eligible for garbage collection before
 * the module provider itself becomes eligible.
 * @version $Id: StrongCachingModuleScriptProvider.java,v 1.3 2011/04/07 20:26:12 hannes%helma.at Exp $
 */
public class StrongCachingModuleScriptProvider extends CachingModuleScriptProviderBase
{
    private static final long serialVersionUID = 1L;

    private final Map<String, CachedModuleScript> modules =
        new ConcurrentHashMap<String, CachedModuleScript>(16, .75f, getConcurrencyLevel());

    /**
     * Creates a new module provider with the specified module source provider.
     * @param moduleSourceProvider provider for modules' source code
     */
    public StrongCachingModuleScriptProvider(
            ModuleSourceProvider moduleSourceProvider)
    {
        super(moduleSourceProvider);
    }

    @Override
    protected CachedModuleScript getLoadedModule(String moduleId) {
        return modules.get(moduleId);
    }

    @Override
    protected void putLoadedModule(String moduleId, ModuleScript moduleScript,
            Object validator) {
        modules.put(moduleId, new CachedModuleScript(moduleScript, validator));
    }
}