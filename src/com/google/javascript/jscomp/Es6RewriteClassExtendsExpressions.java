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
import static com.google.common.base.Preconditions.checkNotNull;

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
 *
 * TODO(bradfordcsmith): This pass may no longer be necessary once the typechecker passes have all
 *     been updated to understand ES6 classes.
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
    // TODO(bradfordcsmith): Do we really need to run this on externs?
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
    Node superClassNode = classNode.getSecondChild();
    return !superClassNode.isEmpty() & !superClassNode.isQualifiedName();
  }

  /**
   * Find common cases where we can safely decompose class extends expressions which are not
   * qualified names. Enables transpilation of complex extends expressions.
   *
   * <p>We can only decompose the expression in a limited set of cases to avoid changing evaluation
   * order of side-effect causing statements.
   */
  private boolean canDecomposeSimply(Node classNode) {
    Node enclosingStatement = checkNotNull(NodeUtil.getEnclosingStatement(classNode), classNode);
    if (enclosingStatement == classNode) {
      // `class Foo extends some_expression {}`
      // can always be converted to
      // ```
      // const tmpvar = some_expression;
      // class Foo extends tmpvar {}
      // ```
      return true;
    } else {
      Node classNodeParent = classNode.getParent();
      if (NodeUtil.isNameDeclaration(enclosingStatement)
          && classNodeParent.isName()
          && classNodeParent.isFirstChildOf(enclosingStatement)) {
        // `const Foo = class extends some_expression {}, maybe_other_var;`
        // can always be converted to
        // ```
        // const tmpvar = some_expression;
        // const Foo = class extends tmpvar {}, maybe_other_var;
        // ```
        return true;
      } else if (enclosingStatement.isExprResult()
          && classNodeParent.isOnlyChildOf(enclosingStatement)
          && classNodeParent.isAssign()
          && classNode.isSecondChildOf(classNodeParent)) {
        // `lhs = class extends some_expression {};`
        Node lhsNode = classNodeParent.getFirstChild();
        // We can extract a temporary variable for some_expression as long as lhs expression
        // has no side effects.
        return !NodeUtil.mayHaveSideEffects(lhsNode);
      } else {
        return false;
      }
    }
  }

  private void extractExtends(NodeTraversal t, Node classNode) {
    String name =
        ModuleNames.fileToJsIdentifier(classNode.getStaticSourceFile().getName())
            + CLASS_EXTENDS_VAR
            + classExtendsVarCounter++;

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
    // converts
    // `class X extends something {}`
    // to
    // `(function() { return class X extends something {}; })()`
    Node functionBody = IR.block();
    Node function = IR.function(IR.name(""), IR.paramList(), functionBody);
    Node call = NodeUtil.newCallNode(function);
    classNode.replaceWith(call);
    functionBody.addChildToBack(IR.returnNode(classNode));
    call.useSourceInfoIfMissingFromForTree(classNode);
    // NOTE: extractExtends() will end up reporting the change for the new function, so we only
    //     need to report the change to the enclosing scope
    t.reportCodeChange(call);
    // Now do the extends expression extraction within the IIFE
    extractExtends(t, classNode);
  }
}
