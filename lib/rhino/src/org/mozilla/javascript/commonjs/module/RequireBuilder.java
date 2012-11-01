/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.commonjs.module;

import java.io.Serializable;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;

/**
 * A builder for {@link Require} instances. Useful when you're creating many
 * instances of {@link Require} that are identical except for their top-level
 * scope and current {@link Context}. Also useful if you prefer configuring it
 * using named setters instead of passing many parameters in a constructor.
 * Every setter returns "this", so you can easily chain their invocations for
 * additional convenience.
 * @version $Id: RequireBuilder.java,v 1.4 2011/04/07 20:26:11 hannes%helma.at Exp $
 */
public class RequireBuilder implements Serializable
{
    private static final long serialVersionUID = 1L;

    private boolean sandboxed = true;
    private ModuleScriptProvider moduleScriptProvider;
    private Script preExec;
    private Script postExec;

    /**
     * Sets the {@link ModuleScriptProvider} for the {@link Require} instances
     * that this builder builds.
     * @param moduleScriptProvider the module script provider for the
     * {@link Require} instances that this builder builds.
     * @return this, so you can chain ("fluidize") setter invocations
     */
    public RequireBuilder setModuleScriptProvider(
            ModuleScriptProvider moduleScriptProvider)
    {
        this.moduleScriptProvider = moduleScriptProvider;
        return this;
    }

    /**
     * Sets the script that should execute in every module's scope after the
     * module's own script has executed.
     * @param postExec the post-exec script.
     * @return this, so you can chain ("fluidize") setter invocations
     */
    public RequireBuilder setPostExec(Script postExec) {
        this.postExec = postExec;
        return this;
    }

    /**
     * Sets the script that should execute in every module's scope before the
     * module's own script has executed.
     * @param preExec the pre-exec script.
     * @return this, so you can chain ("fluidize") setter invocations
     */
    public RequireBuilder setPreExec(Script preExec) {
        this.preExec = preExec;
        return this;
    }

    /**
     * Sets whether the created require() instances will be sandboxed.
     * See {@link Require#Require(Context, Scriptable, ModuleScriptProvider,
     * Script, Script, boolean)} for explanation.
     * @param sandboxed true if the created require() instances will be
     * sandboxed.
     * @return this, so you can chain ("fluidize") setter invocations
     */
    public RequireBuilder setSandboxed(boolean sandboxed) {
        this.sandboxed = sandboxed;
        return this;
    }

    /**
     * Creates a new require() function. You are still responsible for invoking
     * either {@link Require#install(Scriptable)} or
     * {@link Require#requireMain(Context, String)} to effectively make it
     * available to its JavaScript program.
     * @param cx the current context
     * @param globalScope the global scope containing the JS standard natives.
     * @return a new Require instance.
     */
    public Require createRequire(Context cx, Scriptable globalScope) {
        return new Require(cx, globalScope, moduleScriptProvider, preExec,
                postExec, sandboxed);
    }
}