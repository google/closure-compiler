/*
 * Copyright 2016 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.deps.DependencyInfo;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import java.util.Map;
import java.util.TreeMap;

/** A DependencyInfo class that determines load flags by parsing the AST just-in-time. */
public class LazyParsedDependencyInfo extends DependencyInfo.Base {

  private final DependencyInfo delegate;
  private JsAst ast;
  private final transient AbstractCompiler compiler;

  private ImmutableMap<String, String> loadFlags;

  public LazyParsedDependencyInfo(DependencyInfo delegate, JsAst ast, AbstractCompiler compiler) {
    this.delegate = checkNotNull(delegate);
    this.ast = checkNotNull(ast);
    this.compiler = checkNotNull(compiler);
  }

  @Override
  public ImmutableMap<String, String> getLoadFlags() {
    if (loadFlags == null) {
      Map<String, String> loadFlagsBuilder = new TreeMap<>();
      loadFlagsBuilder.putAll(delegate.getLoadFlags());
      FeatureSet features = ast.getFeatures(compiler);
      if (features.has(Feature.MODULES)) {
        String previousModule = loadFlagsBuilder.get("module");
        if (previousModule != null && !previousModule.equals("es6")) {
          compiler.report(JSError.make(ModuleLoader.MODULE_CONFLICT, getName()));
        }
        loadFlagsBuilder.put("module", "es6");
      }
      String version = features.version();
      if (!version.equals("es3")) {
        loadFlagsBuilder.put("lang", version);
      }
      loadFlags = ImmutableMap.copyOf(loadFlagsBuilder);

      // Don't preserve the full AST longer than necessary.  It can consume a lot of memory.
      ast = null;
    }
    return loadFlags;
  }

  @Override
  public String getName() {
    return delegate.getName();
  }

  @Override
  public String getPathRelativeToClosureBase() {
    return delegate.getPathRelativeToClosureBase();
  }

  @Override
  public ImmutableList<Require> getRequires() {
    return delegate.getRequires();
  }

  @Override
  public ImmutableList<String> getTypeRequires() {
    return delegate.getTypeRequires();
  }

  @Override
  public ImmutableList<String> getProvides() {
    return delegate.getProvides();
  }
}
