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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.deps.DependencyInfo;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * A DependencyInfo class that determines load flags by parsing the AST just-in-time.
 */
public class LazyParsedDependencyInfo implements DependencyInfo {

  static final DiagnosticType MODULE_CONFLICT = DiagnosticType.warning(
      "JSC_MODULE_CONFLICT", "File has both goog.module and ES6 modules: {0}");

  private final DependencyInfo delegate;
  private final JsAst ast;
  private final AbstractCompiler compiler;

  private ImmutableMap<String, String> loadFlags;

  public LazyParsedDependencyInfo(DependencyInfo delegate, JsAst ast, AbstractCompiler compiler) {
    this.delegate = Preconditions.checkNotNull(delegate);
    this.ast = Preconditions.checkNotNull(ast);
    this.compiler = Preconditions.checkNotNull(compiler);
  }

  @Override
  public ImmutableMap<String, String> getLoadFlags() {
    if (loadFlags == null) {
      Map<String, String> loadFlagsBuilder = new TreeMap<>();
      loadFlagsBuilder.putAll(delegate.getLoadFlags());
      FeatureSet features = ((JsAst) ast).getFeatures(compiler);
      if (features.hasEs6Modules()) {
        if (loadFlagsBuilder.containsKey("module")) {
          compiler.report(JSError.make(MODULE_CONFLICT, getName()));
        }
        loadFlagsBuilder.put("module", "es6");
      }
      String version = features.version();
      if (!version.equals("es3")) {
        loadFlagsBuilder.put("lang", version);
      }
      loadFlags = ImmutableMap.copyOf(loadFlagsBuilder);
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
  public Collection<String> getRequires() {
    return delegate.getRequires();
  }

  @Override
  public Collection<String> getProvides() {
    return delegate.getProvides();
  }

  @Override
  public boolean isModule() {
    return "goog".equals(getLoadFlags().get("module"));
  }
}
