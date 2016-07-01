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

import com.google.javascript.jscomp.ExpressionDecomposer.DecompositionType;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.HashSet;
import java.util.Set;

/**
 * Extracts ES6 classes defined in function calls to local constants.
 * <p>
 * Example:
 * Before: <code>foo(class { constructor() {} });</code>
 * After:
 * <code>
 *   const $jscomp$classdecl$var0 = class { constructor() {} };
 *   foo($jscomp$classdecl$var0);
 * </code>
 * <p>
 * This must be done before {@link Es6ToEs3Converter}, because that pass only handles classes
 * that are declarations or simple assignments.
 * @see Es6ToEs3Converter#visitClass(Node, Node)
 */
public final class Es6ExtractClasses
    extends NodeTraversal.AbstractPostOrderCallback implements HotSwapCompilerPass {

  static final String CLASS_DECL_VAR = "$classdecl$var";

  private final AbstractCompiler compiler;
  private final ExpressionDecomposer expressionDecomposer;
  private int classDeclVarCounter = 0;

  Es6ExtractClasses(AbstractCompiler compiler) {
    this.compiler = compiler;
    Set<String> consts = new HashSet<>();
    this.expressionDecomposer = new ExpressionDecomposer(
        compiler,
        compiler.getUniqueNameIdSupplier(),
        consts,
        Scope.createGlobalScope(new Node(Token.SCRIPT)));
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseRootsEs6(compiler, this, externs, root);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverseEs6(compiler, scriptRoot, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isClass() && shouldExtractClass(n, parent)) {
      extractClass(n, parent);
    }
  }

  private boolean shouldExtractClass(Node classNode, Node parent) {
    if (NodeUtil.isClassDeclaration(classNode)
        || parent.isName()
        || (parent.isAssign() && parent.getParent().isExprResult())) {
      // No need to extract. Handled directly by Es6ToEs3Converter.ClassDeclarationMetadata#create.
      return false;
    }
    // Don't extract the class if it's not safe to do so. For example,
    // var c = maybeTrue() && class extends someSideEffect() {};
    // TODO(brndn): it is possible to be less conservative. If the classNode is DECOMPOSABLE,
    // we could use the expression decomposer to move it out of the way.
    return expressionDecomposer.canExposeExpression(classNode) == DecompositionType.MOVABLE;
  }

  private void extractClass(Node classNode, Node parent) {
    String name = ES6ModuleLoader.toJSIdentifier(
        ES6ModuleLoader.createUri(classNode.getStaticSourceFile().getName()))
        + CLASS_DECL_VAR
        + (classDeclVarCounter++);
    Node statement = NodeUtil.getEnclosingStatement(parent);
    parent.replaceChild(classNode, IR.name(name));
    Node classDeclaration = IR.constNode(IR.name(name), classNode)
        .useSourceInfoIfMissingFromForTree(classNode);
    statement.getParent().addChildBefore(classDeclaration, statement);
    compiler.reportCodeChange();
  }
}
