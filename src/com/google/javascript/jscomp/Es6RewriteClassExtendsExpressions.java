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

package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.javascript.jscomp.deps.ModuleNames;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/**
 * Extracts ES6 class extends expressions and creates an alias.
 *
 * <p>Example: Before:
 *
 * <p><code>class Foo extends Bar() {}</code>
 *
 * <p>After:
 *
 * <p><code>
 *   const $jscomp$classextends$var0 = Bar();
 *   class Foo extends $jscomp$classextends$var0 {}
 * </code>
 *
 * <p>This must be done before {@link Es6ConvertSuper}, because that pass only handles extends
 * clauses which are simple NAME or GETPROP nodes.
 */
public final class Es6RewriteClassExtendsExpressions extends NodeTraversal.AbstractPostOrderCallback
    implements HotSwapCompilerPass {

  static final String CLASS_EXTENDS_VAR = "$classextends$var";

  private final AbstractCompiler compiler;
  private int classExtendsVarCounter = 0;
  private static final FeatureSet features = FeatureSet.BARE_MINIMUM.with(Feature.CLASSES);

  Es6RewriteClassExtendsExpressions(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, externs, features, this);
    TranspilationPasses.processTranspile(compiler, root, features, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, features, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isClass() && needsExtendsDecomposing(n)) {
      if (canDecomposeSimply(n)) {
        extractExtends(t, n);
      } else {
        decomposeInIIFE(t, n);
      }
    }
  }

  private boolean needsExtendsDecomposing(Node classNode) {
    checkArgument(classNode.isClass());
    if (classNode.getSecondChild().isEmpty() || classNode.getSecondChild().isQualifiedName()) {
      return false;
    }

    return true;
  }

  /**
   * Find common cases where we can safely decompose class extends expressions which are not
   * qualified names. Enables transpilation of complex extends expressions.
   *
   * <p>We can only decompose the expression in a limited set of cases to avoid changing evaluation
   * order of side-effect causing statements.
   */
  private boolean canDecomposeSimply(Node classNode) {
    Node ancestor = classNode.getParent();
    switch (ancestor.getToken()) {
      case RETURN:
        ancestor = ancestor.getParent();
        break;

      case NAME:
        if (ancestor.getParent() != null
            && NodeUtil.isNameDeclaration(ancestor.getParent())
            && ancestor.getParent().getFirstChild() == ancestor) {
          ancestor = ancestor.getGrandparent();
        } else {
          return false;
        }
        break;

      case ASSIGN:
        if (classNode.getPrevious() != null
            && ancestor.getParent() != null
            && ancestor.getParent().isExprResult()
            && (classNode.getPrevious().isQualifiedName()
                || isSimpleGetPropOrElem(classNode.getPrevious()))) {
          ancestor = ancestor.getGrandparent();
        } else {
          return false;
        }
        break;
    }

    if (NodeUtil.isStatementParent(ancestor)) {
      return true;
    }
    return false;
  }

  private boolean isSimpleGetPropOrElem(Node prop) {
    checkArgument(prop.isGetElem() || prop.isGetProp());
    if (!prop.getSecondChild().isString()) {
      return false;
    }
    if (prop.getFirstChild().isQualifiedName()) {
      return true;
    }
    if (prop.getFirstChild().isGetElem()) {
      return isSimpleGetPropOrElem(prop.getFirstChild());
    }
    return false;
  }

  private void extractExtends(NodeTraversal t, Node classNode) {
    String name =
        ModuleNames.fileToJsIdentifier(classNode.getStaticSourceFile().getName())
            + CLASS_EXTENDS_VAR
            + (classExtendsVarCounter++);

    Node statement = NodeUtil.getEnclosingStatement(classNode);
    Node originalExtends = classNode.getSecondChild();
    originalExtends.replaceWith(IR.name(name).useSourceInfoFrom(originalExtends));
    Node extendsAlias =
        IR.constNode(IR.name(name), originalExtends)
            .useSourceInfoIfMissingFromForTree(originalExtends);
    statement.getParent().addChildBefore(extendsAlias, statement);
    NodeUtil.addFeatureToScript(NodeUtil.getEnclosingScript(classNode), Feature.CONST_DECLARATIONS);
    t.reportCodeChange(classNode);
  }

  /**
   * When a class is used in an expressions where adding an alias as the previous statement might
   * change execution order of a side-effect causing statement, wrap the class in an IIFE so that
   * decomposition can happen safely.
   */
  private void decomposeInIIFE(NodeTraversal t, Node classNode) {
    Node placeholder = IR.function(IR.name(""), IR.paramList(), IR.block());
    classNode.replaceWith(placeholder);
    Node functionBody = IR.block(IR.returnNode(classNode));
    Node function = IR.function(IR.name(""), IR.paramList(), functionBody);
    Node call = IR.call(function).useSourceInfoIfMissingFromForTree(classNode);
    call.putBooleanProp(Node.FREE_CALL, true);
    placeholder.replaceWith(call);
    t.reportCodeChange(call);
    extractExtends(t, classNode);
  }
}
