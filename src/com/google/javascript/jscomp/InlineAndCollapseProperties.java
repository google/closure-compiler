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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.javascript.jscomp.CompilerOptions.ChunkOutputType;
import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.rhino.Node;
import java.util.Optional;
import java.util.function.Consumer;

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

  /**
   * Used by `AggressiveInlineAliasesTest` to enable execution of the aggressive inlining logic
   * without doing any collapsing.
   */
  private final boolean testAggressiveInliningOnly;

  /**
   * Supplied by `AggressiveInlineAliasesTest`.
   *
   * <p>The `GlobalNamespace` created by `AggressiveInlineAliases` will be passed to this `Consumer`
   * for examination.
   */
  private final Optional<Consumer<GlobalNamespace>> optionalGlobalNamespaceTester;

  private InlineAndCollapseProperties(Builder builder) {
    this.compiler = builder.compiler;
    this.propertyCollapseLevel = builder.propertyCollapseLevel;
    this.chunkOutputType = builder.chunkOutputType;
    this.haveModulesBeenRewritten = builder.haveModulesBeenRewritten;
    this.moduleResolutionMode = builder.moduleResolutionMode;
    this.testAggressiveInliningOnly = builder.testAggressiveInliningOnly;
    this.optionalGlobalNamespaceTester = builder.optionalGlobalNamespaceTester;
  }

  static final class Builder {
    final AbstractCompiler compiler;
    private PropertyCollapseLevel propertyCollapseLevel;
    private ChunkOutputType chunkOutputType;
    private boolean haveModulesBeenRewritten;
    private ResolutionMode moduleResolutionMode;
    private boolean testAggressiveInliningOnly = false;
    private Optional<Consumer<GlobalNamespace>> optionalGlobalNamespaceTester = Optional.empty();

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

    @VisibleForTesting
    public Builder testAggressiveInliningOnly(Consumer<GlobalNamespace> globalNamespaceTester) {
      this.testAggressiveInliningOnly = true;
      this.optionalGlobalNamespaceTester = Optional.of(globalNamespaceTester);
      return this;
    }

    InlineAndCollapseProperties build() {
      return new InlineAndCollapseProperties(this);
    }
  }

  static Builder builder(AbstractCompiler compiler) {
    return new Builder(compiler);
  }

  @Override
  public void process(Node externs, Node root) {
    checkState(
        !testAggressiveInliningOnly || propertyCollapseLevel == PropertyCollapseLevel.ALL,
        "testAggressiveInlining is invalid for: %s",
        propertyCollapseLevel);
    switch (propertyCollapseLevel) {
      case NONE:
        performMinimalInliningAndNoCollapsing(externs, root);
        break;
      case MODULE_EXPORT:
        performMinimalInliningAndModuleExportCollapsing(externs, root);
        break;
      case ALL:
        if (testAggressiveInliningOnly) {
          performAggressiveInliningForTest(externs, root);
        } else {
          performAggressiveInliningAndCollapsing(externs, root);
        }
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

  private void performAggressiveInliningForTest(Node externs, Node root) {
    final AggressiveInlineAliases aggressiveInlineAliases = new AggressiveInlineAliases(compiler);
    aggressiveInlineAliases.process(externs, root);
    optionalGlobalNamespaceTester
        .get()
        .accept(aggressiveInlineAliases.getLastUsedGlobalNamespace());
  }
}
