/*
 * Copyright 2017 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.deps;

import static com.google.common.collect.ImmutableSortedSet.toImmutableSortedSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.ErrorHandler;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.deps.ModuleLoader.ModuleResolverFactory;
import com.google.javascript.jscomp.deps.ModuleLoader.PathEscaper;
import java.util.Comparator;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Limited superset of the {@link BrowserModuleResolver} that allows for replacing some path
 * prefixes before resolving.
 */
public class BrowserWithTransformedPrefixesModuleResolver extends ModuleResolver {

  static final DiagnosticType TRANSFORMED_PATH_IS_AMBIGUOUS =
      DiagnosticType.error(
          "JSC_TRANSFORMED_PATH_IS_AMBIGUOUS",
          "Replacing \"{0}\" with \"{1}\" in the import path \"{2}\" is an ambiguous address "
              + "(\"{3}\").");

  /** Factory for {@link BrowserWithTransformedPrefixesModuleResolver}. */
  public static final class Factory implements ModuleResolverFactory {
    private final ImmutableMap<String, String> prefixReplacements;

    public Factory(
        ImmutableMap<String, String> prefixReplacements) {
      this.prefixReplacements = prefixReplacements;
    }

    @Override
    public ModuleResolver create(
        ImmutableSet<String> modulePaths,
        ImmutableList<String> moduleRootPaths,
        ErrorHandler errorHandler,
        PathEscaper pathEscaper) {
      return new BrowserWithTransformedPrefixesModuleResolver(
          modulePaths, moduleRootPaths, errorHandler, pathEscaper, prefixReplacements);
    }
  }

  /**
   * Struct of prefix and replacement. Has a natural ordering from longest prefix to shortest
   * prefix.
   */
  @AutoValue
  abstract static class PrefixReplacement {
    abstract String prefix();
    abstract String replacement();

    public static PrefixReplacement of(String prefix, String replacement) {
      return new AutoValue_BrowserWithTransformedPrefixesModuleResolver_PrefixReplacement(
          prefix, replacement);
    }
  }

  private final ImmutableSet<PrefixReplacement> prefixReplacements;

  public BrowserWithTransformedPrefixesModuleResolver(
      ImmutableSet<String> modulePaths,
      ImmutableList<String> moduleRootPaths,
      ErrorHandler errorHandler,
      PathEscaper pathEscaper,
      ImmutableMap<String, String> prefixReplacements) {
    super(modulePaths, moduleRootPaths, errorHandler, pathEscaper);
    Set<PrefixReplacement> p =
        prefixReplacements
            .entrySet()
            .stream()
            .map(entry -> PrefixReplacement.of(entry.getKey(), entry.getValue()))
            .collect(
                toImmutableSortedSet(
                    // Sort by length in descending order to prefixes are applied most specific to
                    // least specific.
                    Comparator.<PrefixReplacement>comparingInt(r -> r.prefix().length())
                        .reversed()
                        .thenComparing(r -> r.prefix())));
    this.prefixReplacements = ImmutableSet.copyOf(p);
  }

  @Nullable
  @Override
  public String resolveJsModule(
      String scriptAddress, String moduleAddress, String sourcename, int lineno, int colno) {
    String transformedAddress = moduleAddress;
    for (PrefixReplacement prefixReplacement : prefixReplacements) {
      if (moduleAddress.startsWith(prefixReplacement.prefix())) {
        transformedAddress =
            prefixReplacement.replacement()
                + moduleAddress.substring(prefixReplacement.prefix().length());

        if (ModuleLoader.isAmbiguousIdentifier(transformedAddress)) {
          errorHandler.report(
              CheckLevel.WARNING,
              JSError.make(
                  sourcename,
                  lineno,
                  colno,
                  TRANSFORMED_PATH_IS_AMBIGUOUS,
                  prefixReplacement.prefix(),
                  prefixReplacement.replacement(),
                  moduleAddress,
                  transformedAddress));
        }
        break;
      }
    }

    // If ambiguous after the loop it was not transformed and the original moduleAddress is
    // ambiguous.
    if (ModuleLoader.isAmbiguousIdentifier(transformedAddress)) {
      errorHandler.report(
          CheckLevel.WARNING,
          JSError.make(
              sourcename,
              lineno,
              colno,
              ModuleLoader.INVALID_MODULE_PATH,
              transformedAddress,
              ModuleLoader.ResolutionMode.BROWSER_WITH_TRANSFORMED_PREFIXES.toString()));
      return null;
    }

    String loadAddress = locate(scriptAddress, transformedAddress);
    if (transformedAddress == null) {
      errorHandler.report(
          CheckLevel.WARNING,
          JSError.make(sourcename, lineno, colno, ModuleLoader.LOAD_WARNING, moduleAddress));
    }
    return loadAddress;
  }

  @Override
  public String resolveModuleAsPath(String scriptAddress, String moduleAddress) {
    if (ModuleLoader.isRelativeIdentifier(moduleAddress)) {
      return super.resolveModuleAsPath(scriptAddress, moduleAddress);
    }

    String transformedAddress = moduleAddress;
    for (PrefixReplacement prefixReplacement : prefixReplacements) {
      if (moduleAddress.startsWith(prefixReplacement.prefix())) {
        transformedAddress =
            prefixReplacement.replacement()
                + moduleAddress.substring(prefixReplacement.prefix().length());
        break;
      }
    }

    return ModuleLoader.normalize(transformedAddress, moduleRootPaths);
  }
}
