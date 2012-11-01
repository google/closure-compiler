/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.util.Set;

import org.mozilla.javascript.ast.ErrorCollector;

public class CompilerEnvirons
{
    public CompilerEnvirons()
    {
        errorReporter = DefaultErrorReporter.instance;
        languageVersion = Context.VERSION_DEFAULT;
        generateDebugInfo = true;
        reservedKeywordAsIdentifier = true;
        allowMemberExprAsFunctionName = false;
        xmlAvailable = true;
        optimizationLevel = 0;
        generatingSource = true;
        strictMode = false;
        warningAsError = false;
        generateObserverCount = false;
        allowSharpComments = false;
    }

    public void initFromContext(Context cx)
    {
        setErrorReporter(cx.getErrorReporter());
        languageVersion = cx.getLanguageVersion();
        generateDebugInfo = (!cx.isGeneratingDebugChanged()
                             || cx.isGeneratingDebug());
        reservedKeywordAsIdentifier
            = cx.hasFeature(Context.FEATURE_RESERVED_KEYWORD_AS_IDENTIFIER);
        allowMemberExprAsFunctionName
            = cx.hasFeature(Context.FEATURE_MEMBER_EXPR_AS_FUNCTION_NAME);
        strictMode
            = cx.hasFeature(Context.FEATURE_STRICT_MODE);
        warningAsError = cx.hasFeature(Context.FEATURE_WARNING_AS_ERROR);
        xmlAvailable
            = cx.hasFeature(Context.FEATURE_E4X);

        optimizationLevel = cx.getOptimizationLevel();

        generatingSource = cx.isGeneratingSource();
        activationNames = cx.activationNames;

        // Observer code generation in compiled code :
        generateObserverCount = cx.generateObserverCount;
    }

    public final ErrorReporter getErrorReporter()
    {
        return errorReporter;
    }

    public void setErrorReporter(ErrorReporter errorReporter)
    {
        if (errorReporter == null) throw new IllegalArgumentException();
        this.errorReporter = errorReporter;
    }

    public final int getLanguageVersion()
    {
        return languageVersion;
    }

    public void setLanguageVersion(int languageVersion)
    {
        Context.checkLanguageVersion(languageVersion);
        this.languageVersion = languageVersion;
    }

    public final boolean isGenerateDebugInfo()
    {
        return generateDebugInfo;
    }

    public void setGenerateDebugInfo(boolean flag)
    {
        this.generateDebugInfo = flag;
    }

    public final boolean isReservedKeywordAsIdentifier()
    {
        return reservedKeywordAsIdentifier;
    }

    public void setReservedKeywordAsIdentifier(boolean flag)
    {
        reservedKeywordAsIdentifier = flag;
    }

    /**
     * Extension to ECMA: if 'function &lt;name&gt;' is not followed
     * by '(', assume &lt;name&gt; starts a {@code memberExpr}
     */
    public final boolean isAllowMemberExprAsFunctionName()
    {
        return allowMemberExprAsFunctionName;
    }

    public void setAllowMemberExprAsFunctionName(boolean flag)
    {
        allowMemberExprAsFunctionName = flag;
    }

    public final boolean isXmlAvailable()
    {
        return xmlAvailable;
    }

    public void setXmlAvailable(boolean flag)
    {
        xmlAvailable = flag;
    }

    public final int getOptimizationLevel()
    {
        return optimizationLevel;
    }

    public void setOptimizationLevel(int level)
    {
        Context.checkOptimizationLevel(level);
        this.optimizationLevel = level;
    }

    public final boolean isGeneratingSource()
    {
        return generatingSource;
    }

    public boolean getWarnTrailingComma() {
        return warnTrailingComma;
    }

    public void setWarnTrailingComma(boolean warn) {
        warnTrailingComma = warn;
    }

    public final boolean isStrictMode()
    {
        return strictMode;
    }

    public void setStrictMode(boolean strict)
    {
        strictMode = strict;
    }

    public final boolean reportWarningAsError()
    {
        return warningAsError;
    }

    /**
     * Specify whether or not source information should be generated.
     * <p>
     * Without source information, evaluating the "toString" method
     * on JavaScript functions produces only "[native code]" for
     * the body of the function.
     * Note that code generated without source is not fully ECMA
     * conformant.
     */
    public void setGeneratingSource(boolean generatingSource)
    {
        this.generatingSource = generatingSource;
    }

    /**
     * @return true iff code will be generated with callbacks to enable
     * instruction thresholds
     */
    public boolean isGenerateObserverCount() {
        return generateObserverCount;
    }

    /**
     * Turn on or off generation of code with callbacks to
     * track the count of executed instructions.
     * Currently only affects JVM byte code generation: this slows down the
     * generated code, but code generated without the callbacks will not
     * be counted toward instruction thresholds. Rhino's interpretive
     * mode does instruction counting without inserting callbacks, so
     * there is no requirement to compile code differently.
     * @param generateObserverCount if true, generated code will contain
     * calls to accumulate an estimate of the instructions executed.
     */
    public void setGenerateObserverCount(boolean generateObserverCount) {
        this.generateObserverCount = generateObserverCount;
    }

    public boolean isRecordingComments() {
        return recordingComments;
    }

    public void setRecordingComments(boolean record) {
        recordingComments = record;
    }

    public boolean isRecordingLocalJsDocComments() {
        return recordingLocalJsDocComments;
    }

    public void setRecordingLocalJsDocComments(boolean record) {
        recordingLocalJsDocComments = record;
    }

    /**
     * Turn on or off full error recovery.  In this mode, parse errors do not
     * throw an exception, and the parser attempts to build a full syntax tree
     * from the input.  Useful for IDEs and other frontends.
     */
    public void setRecoverFromErrors(boolean recover) {
        recoverFromErrors = recover;
    }

    public boolean recoverFromErrors() {
        return recoverFromErrors;
    }

    /**
     * Puts the parser in "IDE" mode.  This enables some slightly more expensive
     * computations, such as figuring out helpful error bounds.
     */
    public void setIdeMode(boolean ide) {
        ideMode = ide;
    }

    public boolean isIdeMode() {
        return ideMode;
    }

    public Set<String> getActivationNames() {
        return activationNames;
    }

    public void setActivationNames(Set<String> activationNames) {
        this.activationNames = activationNames;
    }

    /**
     * Mozilla sources use the C preprocessor.
     */
    public void setAllowSharpComments(boolean allow) {
        allowSharpComments = allow;
    }

    public boolean getAllowSharpComments() {
        return allowSharpComments;
    }

    /**
     * Returns a {@code CompilerEnvirons} suitable for using Rhino
     * in an IDE environment.  Most features are enabled by default.
     * The {@link ErrorReporter} is set to an {@link ErrorCollector}.
     */
    public static CompilerEnvirons ideEnvirons() {
        CompilerEnvirons env = new CompilerEnvirons();
        env.setRecoverFromErrors(true);
        env.setRecordingComments(true);
        env.setStrictMode(true);
        env.setWarnTrailingComma(true);
        env.setLanguageVersion(170);
        env.setReservedKeywordAsIdentifier(true);
        env.setIdeMode(true);
        env.setErrorReporter(new ErrorCollector());
        return env;
    }

    private ErrorReporter errorReporter;

    private int languageVersion;
    private boolean generateDebugInfo;
    private boolean reservedKeywordAsIdentifier;
    private boolean allowMemberExprAsFunctionName;
    private boolean xmlAvailable;
    private int optimizationLevel;
    private boolean generatingSource;
    private boolean strictMode;
    private boolean warningAsError;
    private boolean generateObserverCount;
    private boolean recordingComments;
    private boolean recordingLocalJsDocComments;
    private boolean recoverFromErrors;
    private boolean warnTrailingComma;
    private boolean ideMode;
    private boolean allowSharpComments;
    Set<String> activationNames;
}
