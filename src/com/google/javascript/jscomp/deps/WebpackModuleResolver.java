/*
 * Copyright 2018 The Closure Compiler Authors.
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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.ErrorHandler;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Resolution algorithm for Webpack. Modules are located by a map of numeric ids to module paths.
 *
 * <p>As the compiler normally locates modules by path string, webpack numeric ids are converted to
 * strings.
 */
public class WebpackModuleResolver extends NodeModuleResolver {
  private final ImmutableMap<String, String> modulesById;

  public WebpackModuleResolver(
      ImmutableSet<String> modulePaths,
      ImmutableList<String> moduleRootPaths,
      Map<String, String> modulesById,
      ErrorHandler errorHandler) {
    super(modulePaths, moduleRootPaths, null, errorHandler);

    this.modulesById = ImmutableMap.copyOf(modulesById);
  }

  @Override
  @Nullable
  public String resolveJsModule(
      String scriptAddress, String moduleAddress, String sourcename, int lineno, int colno) {
    String loadAddress = modulesById.get(moduleAddress);
    if (loadAddress == null) {
      // Module paths may still be used in type nodes so we need to fall back
      // to node module resolution for those.
      return super.resolveJsModule(scriptAddress, moduleAddress, sourcename, lineno, colno);
    }
    return loadAddress;
  }
}
