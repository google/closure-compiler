/*
 * Copyright 2021 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CompilerOptions.ChunkOutputType;
import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.rhino.Node;

/**
 * Perform inlining of aliases and collapsing of qualified names in order to improve later
 * optimizations, such as RemoveUnusedCode.
 */
class InlineAndCollapseProperties implements CompilerPass {

  private final AbstractCompiler compiler;
  private final PropertyCollapseLevel propertyCollapseLevel;
  private final ChunkOutputType chunkOutputType;
  private final boolean haveModulesBeenRewritten;
  private final ResolutionMode moduleResolutionMode;

  private InlineAndCollapseProperties(Builder builder) {
    this.compiler = builder.compiler;
    this.propertyCollapseLevel = builder.propertyCollapseLevel;
    this.chunkOutputType = builder.chunkOutputType;
    this.haveModulesBeenRewritten = builder.haveModulesBeenRewritten;
    this.moduleResolutionMode = builder.moduleResolutionMode;
  }

  static final class Builder {
    final AbstractCompiler compiler;
    private PropertyCollapseLevel propertyCollapseLevel;
    private ChunkOutputType chunkOutputType;
    private boolean haveModulesBeenRewritten;
    private ResolutionMode moduleResolutionMode;

    Builder(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    public Builder setPropertyCollapseLevel(PropertyCollapseLevel propertyCollapseLevel) {
      this.propertyCollapseLevel = propertyCollapseLevel;
      return this;
    }

    public Builder setChunkOutputType(ChunkOutputType chunkOutputType) {
      this.chunkOutputType = chunkOutputType;
      return this;
    }

    public Builder setHaveModulesBeenRewritten(boolean haveModulesBeenRewritten) {
      this.haveModulesBeenRewritten = haveModulesBeenRewritten;
      return this;
    }

    public Builder setModuleResolutionMode(ResolutionMode moduleResolutionMode) {
      this.moduleResolutionMode = moduleResolutionMode;
      return this;
    }

    InlineAndCollapseProperties build() {
      return new InlineAndCollapseProperties(this);
    }
  }

  static final Builder builder(AbstractCompiler compiler) {
    return new Builder(compiler);
  }

  @Override
  public void process(Node externs, Node root) {
    switch (propertyCollapseLevel) {
      case NONE:
        performMinimalInliningAndNoCollapsing(externs, root);
        break;
      case MODULE_EXPORT:
        performMinimalInliningAndModuleExportCollapsing(externs, root);
        break;
      case ALL:
        performAggressiveInliningAndCollapsing(externs, root);
        break;
    }
  }

  private void performMinimalInliningAndNoCollapsing(Node externs, Node root) {
    // TODO(b/124915436): Remove InlineAliases completely after cleaning up the codebase.
    new InlineAliases(compiler).process(externs, root);
  }

  private void performMinimalInliningAndModuleExportCollapsing(Node externs, Node root) {
    // TODO(b/124915436): Remove InlineAliases completely after cleaning up the codebase.
    new InlineAliases(compiler).process(externs, root);
    new CollapseProperties(
            compiler,
            propertyCollapseLevel,
            chunkOutputType,
            haveModulesBeenRewritten,
            moduleResolutionMode)
        .process(externs, root);
  }

  private void performAggressiveInliningAndCollapsing(Node externs, Node root) {
    new ConcretizeStaticInheritanceForInlining(compiler).process(externs, root);
    new AggressiveInlineAliases(compiler).process(externs, root);
    new CollapseProperties(
            compiler,
            propertyCollapseLevel,
            chunkOutputType,
            haveModulesBeenRewritten,
            moduleResolutionMode)
        .process(externs, root);
  }
}
