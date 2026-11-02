/*
 * Copyright 2009 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CompilerOptions.BrowserFeaturesetYear;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CompilerOptions}. */
@RunWith(JUnit4.class)
public final class CompilerOptionsTest {

  @Test
  public void testBrowserFeaturesetYearOptionSetsLanguageOut() {
    CompilerOptions options = new CompilerOptions();
    options.setBrowserFeaturesetYear(2012);
    assertThat(options.getOutputFeatureSet())
        .isEqualTo(LanguageMode.ECMASCRIPT5_STRICT.toFeatureSet());

    options.setBrowserFeaturesetYear(2019);
    assertThat(options.getOutputFeatureSet())
        .isEqualTo(LanguageMode.ECMASCRIPT_2017.toFeatureSet());

    options.setBrowserFeaturesetYear(2020);
    assertThat(options.getOutputFeatureSet()).isEqualTo(FeatureSet.BROWSER_2020);

    options.setBrowserFeaturesetYear(2021);
    assertThat(options.getOutputFeatureSet()).isEqualTo(FeatureSet.BROWSER_2021);

    options.setBrowserFeaturesetYear(2022);
    assertThat(options.getOutputFeatureSet()).isEqualTo(FeatureSet.BROWSER_2022);

    options.setBrowserFeaturesetYear(2023);
    assertThat(options.getOutputFeatureSet()).isEqualTo(FeatureSet.BROWSER_2023);

    options.setBrowserFeaturesetYear(2024);
    assertThat(options.getOutputFeatureSet()).isEqualTo(FeatureSet.BROWSER_2024);

    options.setBrowserFeaturesetYear(2025);
    assertThat(options.getOutputFeatureSet()).isEqualTo(FeatureSet.BROWSER_2025);

    options.setBrowserFeaturesetYear(2026);
    assertThat(options.getOutputFeatureSet()).isEqualTo(FeatureSet.BROWSER_2026);
  }

  @Test
  public void testBrowserFeaturesetYearOptionSetsAssumeES5() {
    CompilerOptions options = new CompilerOptions();
    options.setBrowserFeaturesetYear(2012);
    assertThat(options.getDefineReplacements().get("$jscomp.ASSUME_ES5").getToken())
        .isEqualTo(Token.FALSE);
    options.setBrowserFeaturesetYear(2019);
    assertThat(options.getDefineReplacements().get("$jscomp.ASSUME_ES5").getToken())
        .isEqualTo(Token.TRUE);
  }

  @Test
  public void testBrowserFeaturesetYearOptionSetsAssumeES6() {
    CompilerOptions options = new CompilerOptions();
    options.setBrowserFeaturesetYear(2012);
    assertThat(options.getDefineReplacements().get("$jscomp.ASSUME_ES6").getToken())
        .isEqualTo(Token.FALSE);
    options.setBrowserFeaturesetYear(2018);
    assertThat(options.getDefineReplacements().get("$jscomp.ASSUME_ES6").getToken())
        .isEqualTo(Token.TRUE);
    options.setBrowserFeaturesetYear(2020);
    assertThat(options.getDefineReplacements().get("$jscomp.ASSUME_ES2020").getToken())
        .isEqualTo(Token.FALSE);
    options.setBrowserFeaturesetYear(2021);
    assertThat(options.getDefineReplacements().get("$jscomp.ASSUME_ES2020").getToken())
        .isEqualTo(Token.TRUE);
  }

  @Test
  public void testMinimumBrowserFeatureSetYearRequiredFor() {
    assertThat(BrowserFeaturesetYear.minimumRequiredFor(Feature.GETTER))
        .isEqualTo(BrowserFeaturesetYear.YEAR_2012);
    assertThat(BrowserFeaturesetYear.minimumRequiredFor(Feature.CLASSES))
        .isEqualTo(BrowserFeaturesetYear.YEAR_2018);
    assertThat(BrowserFeaturesetYear.minimumRequiredFor(Feature.REGEXP_UNICODE_PROPERTY_ESCAPE))
        .isEqualTo(BrowserFeaturesetYear.YEAR_2021);
  }

  @Test
  public void testMinimumBrowserFeatureSetYearRequiredFor_returnsUnspecifiedIfUnsupported() {
    // Newer features, in particular anything in ES_NEXT, may not be part of a browser featureset
    // year yet.
    assertThat(BrowserFeaturesetYear.minimumRequiredFor(Feature.ES_NEXT_RUNTIME)).isNull();
  }

  @Test
  public void testDefines() {
    CompilerOptions options = new CompilerOptions();
    options.setDefineToBooleanLiteral("trueVar", true);
    options.setDefineToBooleanLiteral("falseVar", false);
    options.setDefineToNumberLiteral("threeVar", 3);
    options.setDefineToStringLiteral("strVar", "str");

    ImmutableMap<String, Node> actual = options.getDefineReplacements();
    assertEquivalent(new Node(Token.TRUE), actual.get("trueVar"));
    assertEquivalent(new Node(Token.FALSE), actual.get("falseVar"));
    assertEquivalent(Node.newNumber(3), actual.get("threeVar"));
    assertEquivalent(Node.newString("str"), actual.get("strVar"));
  }

  public void assertEquivalent(Node a, Node b) {
    assertThat(a.isEquivalentTo(b)).isTrue();
  }

  @Test
  public void testLanguageModeFromString() {
    assertThat(LanguageMode.fromString("ECMASCRIPT3")).isEqualTo(LanguageMode.ECMASCRIPT3);
    // Whitespace should be trimmed, characters converted to uppercase and leading 'ES' replaced
    // with 'ECMASCRIPT'.
    assertThat(LanguageMode.fromString("  es3  ")).isEqualTo(LanguageMode.ECMASCRIPT3);
    assertThat(LanguageMode.fromString("junk")).isNull();

    assertThat(LanguageMode.fromString("ECMASCRIPT_2020")).isEqualTo(LanguageMode.ECMASCRIPT_2020);
    // Whitespace should be trimmed, characters converted to uppercase and leading 'ES' replaced
    // with 'ECMASCRIPT'.
    assertThat(LanguageMode.fromString("  es_2020  ")).isEqualTo(LanguageMode.ECMASCRIPT_2020);
    assertThat(LanguageMode.fromString("  es2020  "))
        .isNull(); // generates invalid "ECMASCRIPT2020"
    assertThat(LanguageMode.fromString("junk")).isNull();
  }

  @Test
  public void testEmitUseStrictWorksInEs3() {
    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(true);

    assertThat(options.shouldEmitUseStrict()).isTrue();
  }

  @Test
  public void testRemoveRegexFromPath() {
    CompilerOptions options = new CompilerOptions();
    Pattern pattern = options.getConformanceRemoveRegexFromPath().get();
    assertThat(pattern.matcher("/blaze-out/k8-fastbin/bin/some/path").replaceAll(""))
        .isEqualTo("some/path");
    assertThat(pattern.matcher("blaze-out/k8-fastbin/bin/some/path").replaceAll(""))
        .isEqualTo("some/path");
    assertThat(pattern.matcher("google3/blaze-out/k8-fastbin/bin/some/path").replaceAll(""))
        .isEqualTo("some/path");
    assertThat(pattern.matcher("google3/blaze-out/k8-fastbin/genfiles/some/path").replaceAll(""))
        .isEqualTo("some/path");
    assertThat(pattern.matcher("something/google3/blaze-out/k8-fastbin/some/path").replaceAll(""))
        .isEqualTo("blaze-out/k8-fastbin/some/path");
    assertThat(
            pattern.matcher("/something/google3/blaze-out/k8-fastbin/bin/some/path").replaceAll(""))
        .isEqualTo("some/path");
    assertThat(pattern.matcher("google3/some/path").replaceAll("")).isEqualTo("some/path");

    assertThat(pattern.matcher("google3/foo/blaze-out/some/path").replaceAll(""))
        .isEqualTo("foo/blaze-out/some/path");
    assertThat(pattern.matcher("google3/blaze-out/foo/blaze-out/some/path").replaceAll(""))
        .isEqualTo("blaze-out/foo/blaze-out/some/path");

    assertThat(pattern.matcher("bazel-out/k8-fastbin/bin/some/path").replaceAll(""))
        .isEqualTo("some/path");
    assertThat(pattern.matcher("bazel-out/k8-fastbin/genfiles/some/path").replaceAll(""))
        .isEqualTo("some/path");
  }

  @Test
  public void testAllInstanceFieldsArePrivate() {
    Field[] fields = CompilerOptions.class.getDeclaredFields();
    List<String> nonPrivateInstanceFields = new ArrayList<>();
    for (Field field : fields) {
      if (field.isSynthetic()) {
        continue;
      }
      int modifiers = field.getModifiers();
      if (!Modifier.isStatic(modifiers) && !Modifier.isPrivate(modifiers)) {
        nonPrivateInstanceFields.add(field.getName());
      }
    }
    assertWithMessage("All instance fields in CompilerOptions must be private.")
        .that(nonPrivateInstanceFields)
        .isEmpty();
  }

  @Test
  public void testTranspilePublicClassFields() {
    CompilerOptions options = new CompilerOptions();
    options.setBrowserFeaturesetYear(2023);
    assertThat(options.getTranspilePublicClassFields()).isFalse();
    assertThat(options.getOutputFeatureSet().has(Feature.PUBLIC_CLASS_FIELDS)).isTrue();

    options.setTranspilePublicClassFields(true);
    assertThat(options.getTranspilePublicClassFields()).isTrue();
    assertThat(options.getOutputFeatureSet().has(Feature.PUBLIC_CLASS_FIELDS)).isFalse();

    options.setTranspilePublicClassFields(false);
    assertThat(options.getTranspilePublicClassFields()).isFalse();
    assertThat(options.getOutputFeatureSet().has(Feature.PUBLIC_CLASS_FIELDS)).isTrue();
  }

  @Test
  public void testRequiresTypesForOptimization() {
    CompilerOptions options = new CompilerOptions();
    assertThat(options.requiresTypesForOptimization()).isFalse();

    options.setClosurePass(true);
    assertThat(options.requiresTypesForOptimization()).isFalse();

    options.setClosurePass(false);
    options.setRemoveClosureAsserts(true);
    assertThat(options.requiresTypesForOptimization()).isFalse();

    options.setRemoveClosureAsserts(false);
    options.setRemoveAbstractMethods(true);
    assertThat(options.requiresTypesForOptimization()).isFalse();

    options.setClosurePass(true);
    assertThat(options.requiresTypesForOptimization()).isTrue();

    options.setRemoveAbstractMethods(false);
    options.setRemoveClosureAsserts(true);
    assertThat(options.requiresTypesForOptimization()).isTrue();
  }

  @Test
  // Safe to ignore: verifying backward compatibility and determinism of deprecated strip options.
  @SuppressWarnings("deprecation")
  public void testCollectionFieldsPreserveInsertionOrder() {
    CompilerOptions options = new CompilerOptions();

    Set<String> types = new LinkedHashSet<>();
    types.add("typeB");
    types.add("typeA");
    types.add("typeC");
    options.setStripTypes(types);
    assertThat(options.getStripTypes()).containsExactly("typeB", "typeA", "typeC").inOrder();

    Set<String> suffixes = new LinkedHashSet<>();
    suffixes.add("suffixB");
    suffixes.add("suffixA");
    suffixes.add("suffixC");
    options.setStripNameSuffixes(suffixes);
    assertThat(options.getStripNameSuffixes())
        .containsExactly("suffixB", "suffixA", "suffixC")
        .inOrder();

    Set<String> prefixes = new LinkedHashSet<>();
    prefixes.add("prefixB");
    prefixes.add("prefixA");
    prefixes.add("prefixC");
    options.setStripNamePrefixes(prefixes);
    assertThat(options.getStripNamePrefixes())
        .containsExactly("prefixB", "prefixA", "prefixC")
        .inOrder();

    CompilerPass pass1 = (externs, root) -> {};
    CompilerPass pass2 = (externs, root) -> {};
    options.addCustomPass(CustomPassExecutionTime.BEFORE_CHECKS, pass1);
    options.addCustomPass(CustomPassExecutionTime.BEFORE_CHECKS, pass2);
    assertThat(options.getCustomPassesAt(CustomPassExecutionTime.BEFORE_CHECKS))
        .containsExactly(pass1, pass2)
        .inOrder();
  }

  @Test
  // Safe to ignore: verifying backward compatibility and determinism of deprecated strip options.
  @SuppressWarnings("deprecation")
  public void testToStringDeterministicFormat() {
    CompilerOptions options1 = new CompilerOptions();
    options1.setDefineToBooleanLiteral("defineA", true);
    options1.setDefineToBooleanLiteral("defineB", false);
    options1.setStripTypes(ImmutableSet.of("typeA", "typeB"));
    options1.setStripNameSuffixes(ImmutableSet.of("suffixA", "suffixB"));
    options1.setStripNamePrefixes(ImmutableSet.of("prefixA", "prefixB"));
    options1.setIdGenerators(ImmutableSet.of("idGenA", "idGenB"));

    CompilerOptions options2 = new CompilerOptions();
    options2.setDefineToBooleanLiteral("defineA", true);
    options2.setDefineToBooleanLiteral("defineB", false);
    options2.setStripTypes(ImmutableSet.of("typeA", "typeB"));
    options2.setStripNameSuffixes(ImmutableSet.of("suffixA", "suffixB"));
    options2.setStripNamePrefixes(ImmutableSet.of("prefixA", "prefixB"));
    options2.setIdGenerators(ImmutableSet.of("idGenA", "idGenB"));

    String s1 = options1.toString();
    String s2 = options2.toString();

    assertThat(s1).startsWith("CompilerOptions{");
    assertThat(s1).endsWith("}");
    assertThat(s1).doesNotContain("@"); // No identity hash codes
    assertThat(s1).isEqualTo(s2);

    String expected =
        "CompilerOptions{aliasStringsMode=NONE, ambiguateProperties=false, angularPass=false,"
            + " assumeGettersArePure=true,"
            + " assumePropertiesAreStaticallyAnalyzable=true, assumeStrictThis=false,"
            + " browserResolverPrefixReplacements={}, checkDeterminism=false,"
            + " checkSuspiciousCode=false, checkSymbols=false, checkTypes=false, checksOnly=false,"
            + " chunksToPrintAfterEachPassRegexList=[], closurePass=false,"
            + " coalesceVariableNames=false, collapseAnonymousFunctions=false,"
            + " collapseObjectLiterals=false, collapseProperties=NONE,"
            + " collapseVariableDeclarations=false, colorizeErrorOutput=false,"
            + " computeFunctionSideEffects=false, conformanceConfigs=[],"
            + " conformanceRemoveRegexFromPath=Optional.of(^((.*/)?google3/)?(/?(blaze|bazel)-out/[^/]+/(bin|genfiles)/)?),"
            + " conformanceReportingMode=IGNORE_LIBRARY_LEVEL_BEHAVIOR_SPECIFIED_IN_CONFIG,"
            + " continueAfterErrors=false, convertToDottedProperties=false,"
            + " crossChunkCodeMotion=false, crossChunkCodeMotionNoStubMethods=false,"
            + " crossChunkMethodMotion=false, deadAssignmentElimination=false,"
            + " defineReplacements={defineA=TRUE, defineB=FALSE},"
            + " dependencyOptions=DependencyOptions[mode=NONE, entryPoints=[]], devMode=OFF,"
            + " devirtualizeMethods=false, disambiguateProperties=false,"
            + " emitUseStrict=Optional.absent(), enableModuleRewriting=true, environment=BROWSER,"
            + " errorFormat=FULL, es6ModuleTranspilation=COMPILE,"
            + " es6SubclassTranspilation=CONCISE_UNSAFE, exportLocalPropertyDefinitions=true,"
            + " exportTestFunctions=false, extractPrototypeMemberDeclarations=OFF,"
            + " filesToPrintAfterEachPassRegexList=[], flowSensitiveInlineVariables=false,"
            + " foldConstants=false, forceLibraryInjection=[], gatherCssNames=false,"
            + " generateExports=true, generatePseudoNames=false, generateTypedExterns=false,"
            + " idGenerators={idGenA=INCONSISTENT, idGenB=INCONSISTENT}, incrementalCheckMode=OFF,"
            + " inferConsts=true, inferTypes=false, inlineConstantVars=false,"
            + " inlineFunctionsLevel=NONE, inlineGetters=false, inlineLocalVariables=false,"
            + " inlineProperties=false, inlineVariables=false, inputDelimiter=// Input %num%,"
            + " inputSourceMaps={}, instrumentForCoverageOnly=false,"
            + " instrumentForCoverageOption=NONE, isolatePolyfills=false, j2clMinifierEnabled=true,"
            + " j2clPassMode=AUTO, labelRenaming=false, languageIn=ECMASCRIPT_NEXT,"
            + " languageOutIsDefaultStrict=Optional.absent(), lineBreak=false,"
            + " lineLengthThreshold=500, markAsCompiled=false, maxFunctionSizeAfterInlining=-1,"
            + " moduleRoots=[./], nameGenerator=com.google.javascript.jscomp.DefaultNameGenerator,"
            + " numParallelThreads=1, optimizeCalls=false, optimizeESClassConstructors=false,"
            + " optimizeLocalAccessForGlobalSymbolNamespace=DISABLED,"
            + " outputFeatureSet=Optional.absent(), outputJs=NORMAL, outputJsStringUsage=false,"
            + " parentChunkCanSeeSymbolsDeclaredInChildren=false,"
            + " parseJsDocDocumentation=TYPES_ONLY, pathEscaper=ESCAPE, polymerPass=false,"
            + " preferSingleQuotes=false, preferStableNames=false,"
            + " preserveDetailedSourceInfo=false, preserveGoogProvidesAndRequires=false,"
            + " preserveNonJSDocComments=false, preserveTypeAnnotations=false, prettyPrint=false,"
            + " printConfig=false, printInputDelimiter=false, printSourceAfterEachPass=false,"
            + " processCommonJSModules=false, productionInstrumentationArrayName=,"
            + " propertiesThatMustDisambiguate=[], propertyRenaming=OFF,"
            + " propertyRenamingOnlyCompilationMode=false, protectHiddenSideEffects=false,"
            + " qnameUsesToPrintAfterEachPassRegexList=[], quoteKeywordProperties=false,"
            + " removeAbstractMethods=false, removeClosureAsserts=false, removeJ2clAsserts=true,"
            + " removeUnusedClassProperties=false, removeUnusedLocalVars=false,"
            + " removeUnusedPrototypeProperties=false, removeUnusedVars=false,"
            + " renamePrefixNamespaceAssumeCrossChunkNames=false, replaceIdGenerators=true,"
            + " replaceMessagesWithChromeI18n=false, replaceStringsFunctionDescriptions=[],"
            + " replaceStringsPlaceholderToken=, reserveRawExports=false,"
            + " rewriteFunctionExpressions=false,"
            + " rewriteGlobalDeclarationsForTryCatchWrapping=false,"
            + " rewriteModulesBeforeTypechecking=false, rewritePolyfills=false,"
            + " runtimeLibraryMode=INJECT, skipNonTranspilationPasses=false,"
            + " smartNameRemoval=false, sourceMapDetailLevel=ALL, sourceMapFormat=DEFAULT,"
            + " sourceMapLocationMappings=[], strictMessageReplacement=false,"
            + " stripNamePrefixes=[prefixA, prefixB], stripNameSuffixes=[suffixA, suffixB],"
            + " stripTypes=[typeA, typeB], summaryDetailLevel=1, tracer=OFF, trustedStrings=false,"
            + " tweakProcessing=OFF, useTypesForLocalOptimization=false, variableRenaming=OFF,"
            + " warningsGuard=, wrapGoogModulesForWhitespaceOnly=true}";
    assertThat(s1).isEqualTo(expected);
  }
}
