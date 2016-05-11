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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.deps.DependencyInfo;
import com.google.javascript.jscomp.deps.JsFileParser;
import com.google.javascript.jscomp.deps.SimpleDependencyInfo;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A class for the internal representation of an input to the compiler.
 * Wraps a {@link SourceAst} and maintain state such as module for the input and
 * whether the input is an extern. Also calculates provided and required types.
 *
 */
public class CompilerInput implements SourceAst, DependencyInfo {

  private static final long serialVersionUID = 1L;

  // Info about where the file lives.
  private JSModule module;
  private final InputId id;

  // The AST.
  private final SourceAst ast;

  // DependencyInfo to delegate to.
  private DependencyInfo dependencyInfo;
  private final List<String> extraRequires = new ArrayList<>();
  private final List<String> extraProvides = new ArrayList<>();

  // An AbstractCompiler for doing parsing.
  // We do not want to persist this across serialized state.
  private transient AbstractCompiler compiler;

  public CompilerInput(SourceAst ast) {
    this(ast, ast.getSourceFile().getName(), false);
  }

  public CompilerInput(SourceAst ast, boolean isExtern) {
    this(ast, ast.getInputId(), isExtern);
  }

  public CompilerInput(SourceAst ast, String inputId, boolean isExtern) {
    this(ast, new InputId(inputId), isExtern);
  }

  public CompilerInput(SourceAst ast, InputId inputId, boolean isExtern) {
    this.ast = ast;
    this.id = inputId;

    // TODO(nicksantos): Add a precondition check here. People are passing
    // in null, but they should not be.
    if (ast != null && ast.getSourceFile() != null) {
      ast.getSourceFile().setIsExtern(isExtern);
    }
  }

  public CompilerInput(SourceFile file) {
    this(file, false);
  }

  public CompilerInput(SourceFile file, boolean isExtern) {
    this(new JsAst(file), isExtern);
  }

  /** Returns a name for this input. Must be unique across all inputs. */
  @Override
  public InputId getInputId() {
    return id;
  }

  /** Returns a name for this input. Must be unique across all inputs. */
  @Override
  public String getName() {
    return id.getIdName();
  }

  /** Gets the path relative to closure-base, if one is available. */
  @Override
  public String getPathRelativeToClosureBase() {
    // TODO(nicksantos): Implement me.
    throw new UnsupportedOperationException();
  }

  @Override
  public Node getAstRoot(AbstractCompiler compiler) {
    Node root = ast.getAstRoot(compiler);
    // The root maybe null if the AST can not be created.
    if (root != null) {
      Preconditions.checkState(root.isScript());
      Preconditions.checkNotNull(root.getInputId());
    }
    return root;
  }

  @Override
  public void clearAst() {
    ast.clearAst();
  }

  @Override
  public SourceFile getSourceFile() {
    return ast.getSourceFile();
  }

  @Override
  public void setSourceFile(SourceFile file) {
    ast.setSourceFile(file);
  }

  /** Sets an abstract compiler for doing parsing. */
  public void setCompiler(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  /** Gets a list of types depended on by this input. */
  @Override
  public Collection<String> getRequires() {
    return getDependencyInfo().getRequires();
  }

  /**
   * Gets a list of types depended on by this input,
   * but does not attempt to regenerate the dependency information.
   * Typically this occurs from module rewriting.
   */
  Collection<String> getKnownRequires() {
    return concat(
        dependencyInfo != null ? dependencyInfo.getRequires() : ImmutableList.<String>of(),
        extraRequires);
  }

  /** Gets a list of types provided by this input. */
  @Override
  public Collection<String> getProvides() {
    return getDependencyInfo().getProvides();
  }

  /**
   * Gets a list of types provided, but does not attempt to
   * regenerate the dependency information. Typically this occurs
   * from module rewriting.
   */
  Collection<String> getKnownProvides() {
    return concat(
        dependencyInfo != null ? dependencyInfo.getProvides() : ImmutableList.<String>of(),
        extraProvides);
  }

  // TODO(nicksantos): Remove addProvide/addRequire/removeRequire once
  // there is better support for discovering non-closure dependencies.

  /**
   * Registers a type that this input defines.
   */
  public void addProvide(String provide) {
    extraProvides.add(provide);
  }

  /**
   * Registers a type that this input depends on.
   */
  public void addRequire(String require) {
    extraRequires.add(require);
  }

  /**
   * Returns the DependencyInfo object, generating it lazily if necessary.
   */
  private DependencyInfo getDependencyInfo() {
    if (dependencyInfo == null) {
      dependencyInfo = generateDependencyInfo();
    }
    if (!extraRequires.isEmpty() || !extraProvides.isEmpty()) {
      dependencyInfo = new SimpleDependencyInfo(
          getName(),
          getName(),
          concat(dependencyInfo.getProvides(), extraProvides),
          concat(dependencyInfo.getRequires(), extraRequires),
          dependencyInfo.getLoadFlags());
      extraRequires.clear();
      extraProvides.clear();
    }
    return dependencyInfo;
  }

  /**
   * Generates the DependencyInfo by scanning and/or parsing the file.
   * This is called lazily by getDependencyInfo, and does not take into
   * account any extra requires/provides added by {@link #addRequire}
   * or {@link #addProvide}.
   */
  private DependencyInfo generateDependencyInfo() {
    Preconditions.checkNotNull(compiler, "Expected setCompiler to be called first: %s", this);
    Preconditions.checkNotNull(
        compiler.getErrorManager(), "Expected compiler to call an error manager: %s", this);

    // If the code is NOT a JsAst, then it was not originally JS code.
    // Look at the Ast for dependency info.
    if (!(ast instanceof JsAst)) {

      DepsFinder finder = new DepsFinder(compiler.getCodingConvention());
      Node root = getAstRoot(compiler);
      if (root == null) {
        return SimpleDependencyInfo.EMPTY;
      }

      finder.visitTree(getAstRoot(compiler));

      // TODO(nicksantos|user): This caching behavior is a bit
      // odd, and only works if you assume the exact call flow that
      // clients are currently using.  In that flow, they call
      // getProvides(), then remove the goog.provide calls from the
      // AST, and then call getProvides() again.
      //
      // This won't work for any other call flow, or any sort of incremental
      // compilation scheme. The API needs to be fixed so callers aren't
      // doing weird things like this, and then we should get rid of the
      // multiple-scan strategy.
      return new SimpleDependencyInfo("", "", finder.provides, finder.requires, finder.loadFlags);
    } else {
      // Otherwise, look at the source code.
      // Note: it's OK to use getName() instead of
      // getPathRelativeToClosureBase() here because we're not using
      // this to generate deps files. (We're only using it for
      // symbol dependencies.)
      try {
        DependencyInfo info =
            (new JsFileParser(compiler.getErrorManager()))
            .setIncludeGoogBase(true)
            .parseFile(getName(), getName(), getCode());
        return new LazyParsedDependencyInfo(info, (JsAst) ast, compiler);
      } catch (IOException e) {
        compiler.getErrorManager().report(CheckLevel.ERROR,
            JSError.make(AbstractCompiler.READ_ERROR, getName()));
        return SimpleDependencyInfo.EMPTY;
      }
    }
  }

  private static class DepsFinder {
    private final Map<String, String> loadFlags = new TreeMap<>();
    private final List<String> provides = new ArrayList<>();
    private final List<String> requires = new ArrayList<>();
    private final CodingConvention codingConvention;

    DepsFinder(CodingConvention codingConvention) {
      this.codingConvention = codingConvention;
    }

    void visitTree(Node n) {
      visitSubtree(n, null);
      Preconditions.checkArgument(n.isScript());
      FeatureSet features = (FeatureSet) n.getProp(Node.FEATURE_SET);
      if (features != null) {
        // Only add the "lang" load flag if it's not the default (es3), so that
        // legacy deps files will remain unchanged (i.e. load flags omitted).
        String version = features.version();
        if (!version.equals("es3")) {
          loadFlags.put("lang", version);
        }
      }
    }

    void visitSubtree(Node n, Node parent) {
      if (n.isCall()) {
        boolean isModuleDetected =  codingConvention.extractIsModuleFile(n, parent);

        if (isModuleDetected) {
          loadFlags.put("module", "goog");
        }

        String require =
            codingConvention.extractClassNameIfRequire(n, parent);
        if (require != null) {
          requires.add(require);
        }

        String provide =
            codingConvention.extractClassNameIfProvide(n, parent);
        if (provide != null) {
          provides.add(provide);
        }
        return;
      } else if (parent != null && !parent.isExprResult() && !parent.isScript()) {
        return;
      }

      for (Node child = n.getFirstChild();
           child != null; child = child.getNext()) {
        visitSubtree(child, n);
      }
    }
  }

  public String getCode() throws IOException {
    return getSourceFile().getCode();
  }

  /** Returns the module to which the input belongs. */
  public JSModule getModule() {
    return module;
  }

  /** Sets the module to which the input belongs. */
  public void setModule(JSModule module) {
    // An input may only belong to one module.
    Preconditions.checkArgument(
        module == null || this.module == null || this.module == module);
    this.module = module;
  }

  /** Overrides the module to which the input belongs. */
  void overrideModule(JSModule module) {
    this.module = module;
  }

  public boolean isExtern() {
    if (ast == null || ast.getSourceFile() == null) {
      return false;
    }
    return ast.getSourceFile().isExtern();
  }

  void setIsExtern(boolean isExtern) {
    if (ast == null || ast.getSourceFile() == null) {
      return;
    }
    ast.getSourceFile().setIsExtern(isExtern);
  }

  public int getLineOffset(int lineno) {
    return ast.getSourceFile().getLineOffset(lineno);
  }

  /** @return The number of lines in this input. */
  public int getNumLines() {
    return ast.getSourceFile().getNumLines();
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public ImmutableMap<String, String> getLoadFlags() {
    return getDependencyInfo().getLoadFlags();
  }

  @Override
  public boolean isModule() {
    return "goog".equals(getLoadFlags().get("module"));
  }

  private static <T> Set<T> concat(Iterable<T> first, Iterable<T> second) {
    return ImmutableSet.<T>builder().addAll(first).addAll(second).build();
  }
}
