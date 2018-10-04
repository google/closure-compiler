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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.Es6ToEs3Util.CANNOT_CONVERT;

import com.google.javascript.jscomp.ExpressionDecomposer.DecompositionType;
import com.google.javascript.jscomp.deps.ModuleNames;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import java.util.ArrayDeque;
import java.util.Deque;
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
 * This must be done before {@link Es6RewriteClass}, because that pass only handles classes
 * that are declarations or simple assignments.
 * @see Es6RewriteClass#visitClass(NodeTraversal, Node, Node)
 */
public final class Es6ExtractClasses
    extends NodeTraversal.AbstractPostOrderCallback implements HotSwapCompilerPass {

  static final String CLASS_DECL_VAR = "$classdecl$var";

  private final AbstractCompiler compiler;
  private final ExpressionDecomposer expressionDecomposer;
  private int classDeclVarCounter = 0;
  private static final FeatureSet features = FeatureSet.BARE_MINIMUM.with(Feature.CLASSES);

  Es6ExtractClasses(AbstractCompiler compiler) {
    this.compiler = compiler;
    Set<String> consts = new HashSet<>();
    this.expressionDecomposer = new ExpressionDecomposer(
        compiler,
        compiler.getUniqueNameIdSupplier(),
        consts,
        Scope.createGlobalScope(new Node(Token.SCRIPT)),
        compiler.getOptions().allowMethodCallDecomposing());
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(
        compiler, externs, features, this, new SelfReferenceRewriter());
    TranspilationPasses.processTranspile(
        compiler, root, features, this, new SelfReferenceRewriter());
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(
        compiler, scriptRoot, features, this, new SelfReferenceRewriter());
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isClass() && shouldExtractClass(n, parent)) {
      extractClass(t, n, parent);
    }
  }

  private class SelfReferenceRewriter implements NodeTraversal.Callback {
    private class ClassDescription {
      Node nameNode;
      String outerName;

      ClassDescription(Node nameNode, String outerName) {
        this.nameNode = nameNode;
        this.outerName = outerName;
      }
    }

    private final Deque<ClassDescription> classStack = new ArrayDeque<>();

    private boolean needsInnerNameRewriting(Node classNode, Node parent) {
      checkArgument(classNode.isClass());
      return classNode.getFirstChild().isName() && parent.isName();
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (n.isClass() && needsInnerNameRewriting(n, parent)) {
        classStack.addFirst(new ClassDescription(n.getFirstChild(), parent.getString()));
      }
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case CLASS:
          if (needsInnerNameRewriting(n, parent)) {
            classStack.removeFirst();
            n.replaceChild(n.getFirstChild(), IR.empty().useSourceInfoFrom(n.getFirstChild()));
            compiler.reportChangeToEnclosingScope(n);
          }
          break;
        case NAME:
          maybeUpdateClassSelfRef(t, n, parent);
          break;
        default:
          break;
      }
    }

    private void maybeUpdateClassSelfRef(NodeTraversal t, Node nameNode, Node parent) {
      for (ClassDescription klass : classStack) {
        if (nameNode != klass.nameNode && nameNode.matchesQualifiedName(klass.nameNode)) {
          Var var = t.getScope().getVar(nameNode.getString());
          if (var != null && var.getNameNode() == klass.nameNode) {
            Node newNameNode =
                IR.name(klass.outerName)
                    .setJSType(nameNode.getJSType())
                    .useSourceInfoFrom(nameNode);
            parent.replaceChild(nameNode, newNameNode);
            compiler.reportChangeToEnclosingScope(newNameNode);
            return;
          }
        }
      }
    }
  }

  private boolean shouldExtractClass(Node classNode, Node parent) {
    boolean isAnonymous = classNode.getFirstChild().isEmpty();
    if (NodeUtil.isClassDeclaration(classNode)
        || (isAnonymous && parent.isName())
        || (isAnonymous
            && parent.isAssign()
            && parent.getFirstChild().isQualifiedName()
            && parent.getParent().isExprResult())) {
      // No need to extract. Handled directly by Es6ToEs3Converter.ClassDeclarationMetadata#create.
      return false;
    }

    if (NodeUtil.mayHaveSideEffects(classNode)
        // Don't extract the class if it's not safe to do so. For example,
        // var c = maybeTrue() && class extends someSideEffect() {};
        // TODO(brndn): it is possible to be less conservative. If the classNode is DECOMPOSABLE,
        // we could use the expression decomposer to move it out of the way.
        || expressionDecomposer.canExposeExpression(classNode) != DecompositionType.MOVABLE) {
      compiler.report(
          JSError.make(classNode, CANNOT_CONVERT, "class expression that cannot be extracted"));
      return false;
    }

    return true;
  }

  private void extractClass(NodeTraversal t, Node classNode, Node parent) {
    String name = ModuleNames.fileToJsIdentifier(classNode.getStaticSourceFile().getName())
        + CLASS_DECL_VAR
        + (classDeclVarCounter++);
    JSDocInfo info = NodeUtil.getBestJSDocInfo(classNode);

    Node statement = NodeUtil.getEnclosingStatement(parent);
    JSType classType = classNode.getJSType();
    checkState(!compiler.hasTypeCheckingRun() || classType != null);
    // class name node used as LHS in newly created assignment
    Node classNameLhs = IR.name(name).setJSType(classType);
    // class name node that replaces the class literal in the original statement
    Node classNameRhs = classNameLhs.cloneTree();
    parent.replaceChild(classNode, classNameRhs);
    Node classDeclaration =
        IR.constNode(classNameLhs, classNode).useSourceInfoIfMissingFromForTree(classNode);
    NodeUtil.addFeatureToScript(t.getCurrentFile(), Feature.CONST_DECLARATIONS);
    classDeclaration.setJSDocInfo(JSDocInfoBuilder.maybeCopyFrom(info).build());
    statement.getParent().addChildBefore(classDeclaration, statement);

    // If the original statement was a variable declaration or qualified name assignment like
    // like these:
    // var ClassName = class {...
    // OR
    // some.qname.ClassName = class {...
    //
    // We will have changed the original statement to
    //
    // var ClassName = generatedName;
    // OR
    // some.qname.ClassName = generatedName;
    //
    // This is creating a type alias for a class, but since there's no literal class on the RHS,
    // it doesn't look like one. Add at-constructor JSDoc to make it clear that this is happening.
    //
    // This was added to fix a specific problem where the original definition was for an abstract
    // class, so its JSDoc included at-abstract.
    // This caused ClosureCodeRemoval to think this rewritten assignment was a removable abstract
    // method definition instead of the definition of an abstract class.
    //
    // TODO(b/117292942): Make ClosureCodeRemoval smarter so this hack isn't necessary to
    // prevent incorrect removal of assignments.
    if (NodeUtil.isNameDeclaration(statement)
        && statement.hasOneChild()
        && statement.getOnlyChild() == parent) {
      // var ClassName = generatedName;
      addAtConstructor(statement);
    } else if (statement.isExprResult()) {
      Node expr = statement.getOnlyChild();
      if (expr.isAssign()
          && expr.getFirstChild().isQualifiedName()
          && expr.getSecondChild() == classNameRhs) {
        // some.qname.ClassName = generatedName;
        addAtConstructor(expr);
      }
    }
    compiler.reportChangeToEnclosingScope(classDeclaration);
  }

  /**
   * Add at-constructor to the JSDoc of the given node.
   *
   * @param node
   */
  private void addAtConstructor(Node node) {
    JSDocInfoBuilder builder = JSDocInfoBuilder.maybeCopyFrom(node.getJSDocInfo());
    builder.recordConstructor();
    node.setJSDocInfo(builder.build());
  }
}
