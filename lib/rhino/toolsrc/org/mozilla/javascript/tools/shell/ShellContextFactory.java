/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tools.shell;

import org.mozilla.javascript.*;

public class ShellContextFactory extends ContextFactory
{
    private boolean strictMode;
    private boolean warningAsError;
    private int languageVersion = Context.VERSION_1_7;
    private int optimizationLevel;
    private boolean generatingDebug;
    private boolean allowReservedKeywords = true;
    private ErrorReporter errorReporter;
    private String characterEncoding;

    @Override
    protected boolean hasFeature(Context cx, int featureIndex)
    {
        switch (featureIndex) {
          case Context.FEATURE_STRICT_VARS:
          case Context.FEATURE_STRICT_EVAL:
          case Context.FEATURE_STRICT_MODE:
            return strictMode;

          case Context.FEATURE_RESERVED_KEYWORD_AS_IDENTIFIER:
            return allowReservedKeywords;

          case Context.FEATURE_WARNING_AS_ERROR:
            return warningAsError;

          case Context.FEATURE_LOCATION_INFORMATION_IN_ERROR:
            return generatingDebug;
        }
        return super.hasFeature(cx, featureIndex);
    }

    @Override
    protected void onContextCreated(Context cx)
    {
        cx.setLanguageVersion(languageVersion);
        cx.setOptimizationLevel(optimizationLevel);
        if (errorReporter != null) {
            cx.setErrorReporter(errorReporter);
        }
        cx.setGeneratingDebug(generatingDebug);
        super.onContextCreated(cx);
    }

    public void setStrictMode(boolean flag)
    {
        checkNotSealed();
        this.strictMode = flag;
    }

    public void setWarningAsError(boolean flag)
    {
        checkNotSealed();
        this.warningAsError = flag;
    }

    public void setLanguageVersion(int version)
    {
        Context.checkLanguageVersion(version);
        checkNotSealed();
        this.languageVersion = version;
    }

    public void setOptimizationLevel(int optimizationLevel)
    {
        Context.checkOptimizationLevel(optimizationLevel);
        checkNotSealed();
        this.optimizationLevel = optimizationLevel;
    }

    public void setErrorReporter(ErrorReporter errorReporter)
    {
        if (errorReporter == null) throw new IllegalArgumentException();
        this.errorReporter = errorReporter;
    }

    public void setGeneratingDebug(boolean generatingDebug)
    {
        this.generatingDebug = generatingDebug;
    }

    public String getCharacterEncoding()
    {
        return characterEncoding;
    }

    public void setCharacterEncoding(String characterEncoding)
    {
        this.characterEncoding = characterEncoding;
    }

    public void setAllowReservedKeywords(boolean allowReservedKeywords) {
        this.allowReservedKeywords = allowReservedKeywords;
    }
}
