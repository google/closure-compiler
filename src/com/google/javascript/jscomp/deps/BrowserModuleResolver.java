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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.ErrorHandler;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.deps.ModuleLoader.ModuleResolverFactory;
import com.google.javascript.jscomp.deps.ModuleLoader.PathEscaper;
import javax.annotation.Nullable;

/**
 * Resolution algorithm for Browsers.
 *
 * <p>Only unambiguous paths are supported. Paths must specify a file extension.
 */
public class BrowserModuleResolver extends ModuleResolver {

  public static final ModuleResolverFactory FACTORY = BrowserModuleResolver::new;

  public BrowserModuleResolver(
      ImmutableSet<String> modulePaths,
      ImmutableList<String> moduleRootPaths,
      ErrorHandler errorHandler,
      PathEscaper pathEscaper) {
    super(modulePaths, moduleRootPaths, errorHandler, pathEscaper);
  }

  @Override
  @Nullable
  public String resolveJsModule(
      String scriptAddress, String moduleAddress, String sourcename, int lineno, int colno) {

    if (ModuleLoader.isAmbiguousIdentifier(moduleAddress)) {
      errorHandler.report(
          CheckLevel.WARNING,
          JSError.make(
              sourcename,
              lineno,
              colno,
              ModuleLoader.INVALID_MODULE_PATH,
              moduleAddress,
              ModuleLoader.ResolutionMode.BROWSER.toString()));
      return null;
    }

    String loadAddress = locate(scriptAddress, moduleAddress);
    if (loadAddress == null) {
      errorHandler.report(
          CheckLevel.WARNING,
          JSError.make(sourcename, lineno, colno, ModuleLoader.LOAD_WARNING, moduleAddress));
    }
    return loadAddress;
  }
}
