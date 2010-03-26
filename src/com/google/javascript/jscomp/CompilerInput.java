/*
 * Copyright 2009 Google Inc.
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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowCallback;
import com.google.javascript.jscomp.deps.DependencyInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * A class for the internal representation of an input to the compiler.
 * Wraps a {@link SourceAst} and maintain state such as module for the input and
 * whether the input is an extern. Also calculates provided and required types.
 *
*
 */
public class CompilerInput implements SourceAst, DependencyInfo {
  private static final long serialVersionUID = 1L;

  // Info about where the file lives.
  private JSModule module;
  private final boolean isExtern;
  final private String name;

  // The AST.
  private final SourceAst ast;

  // Provided and required symbols.
  private final Set<String> provides = Sets.newHashSet();
  private final Set<String> requires = Sets.newHashSet();

  public CompilerInput(SourceAst ast) {
    this(ast, ast.getSourceFile().getName(), false);
  }

  public CompilerInput(SourceAst ast, boolean isExtern) {
    this(ast, ast.getSourceFile().getName(), isExtern);
  }

  public CompilerInput(SourceAst ast, String inputName, boolean isExtern) {
    this.ast = ast;
    this.name = inputName;
    this.isExtern = isExtern;
  }

  public CompilerInput(JSSourceFile file) {
    this(file, false);
  }

  public CompilerInput(JSSourceFile file, boolean isExtern) {
    this.ast = new JsAst(file);
    this.name = file.getName();
    this.isExtern = isExtern;
  }

  /** Returns a name for this input. Must be unique across all inputs. */
  @Override
  public String getName() {
    return name;
  }

  /** Gets the path relative to closure-base, if one is available. */
  @Override
  public String getPathRelativeToClosureBase() {
    // TODO(nicksantos): Implement me.
    throw new UnsupportedOperationException();
  }

  @Override
  public Node getAstRoot(AbstractCompiler compiler) {
    return ast.getAstRoot(compiler);
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

  /** Returns the SourceAst object on which this input is based. */
  public SourceAst getSourceAst() {
    return ast;
  }

  /** Gets a list of types depended on by this input. */
  public Collection<String> getRequires(AbstractCompiler compiler) {
    if (getAstRoot(compiler) != null) {
      DepsFinder deps = new DepsFinder(compiler, true);
      NodeTraversal.traverse(compiler, getAstRoot(compiler), deps);
      requires.addAll(deps.types);
      return requires;
    } else {
      return ImmutableSet.<String>of();
    }
  }

  /** Gets a list of types depended on by this input. */
  @Override
  public Collection<String> getRequires() {
    // TODO(nicksantos): Implement me.
    throw new UnsupportedOperationException();
  }

  /** Gets a list of types provided by this input. */
  public Collection<String> getProvides(AbstractCompiler compiler) {
    if (getAstRoot(compiler) != null) {
      DepsFinder deps = new DepsFinder(compiler, false);
      NodeTraversal.traverse(compiler, getAstRoot(compiler), deps);
      provides.addAll(deps.types);
      return provides;
    } else {
      return ImmutableSet.<String>of();
    }
  }

  /** Gets a list of types provided by this input. */
  @Override
  public Collection<String> getProvides() {
    // TODO(nicksantos): Implement me.
    throw new UnsupportedOperationException();
  }

  private class DepsFinder extends AbstractShallowCallback {
    private boolean findRequire;
    private List<String> types;
    private CodingConvention codingConvention;

    DepsFinder(AbstractCompiler compiler, boolean findRequire) {
      this.findRequire = findRequire;
      this.codingConvention = compiler.getCodingConvention();
      this.types = Lists.newArrayList();
    }

    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.CALL:
          String className = findRequire
              ? codingConvention.extractClassNameIfRequire(n, parent)
              : codingConvention.extractClassNameIfProvide(n, parent);
          if (className != null) {
            types.add(className);
          }
          break;
      }
    }
  }

  /**
   * Gets the source line for the indicated line number.
   *
   * @param lineNumber the line number, 1 being the first line of the file.
   * @return The line indicated. Does not include the newline at the end
   *     of the file. Returns {@code null} if it does not exist,
   *     or if there was an IO exception.
   */
  public String getLine(int lineNumber) {
    return getSourceFile().getLine(lineNumber);
  }

  /**
   * Get a region around the indicated line number. The exact definition of a
   * region is implementation specific, but it must contain the line indicated
   * by the line number. A region must not start or end by a carriage return.
   *
   * @param lineNumber the line number, 1 being the first line of the file.
   * @return The line indicated. Returns {@code null} if it does not exist,
   *     or if there was an IO exception.
   */
  public Region getRegion(int lineNumber) {
    return getSourceFile().getRegion(lineNumber);
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

  public boolean isExtern() {
    return isExtern;
  }
}
