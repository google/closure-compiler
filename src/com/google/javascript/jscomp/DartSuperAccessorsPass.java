/*
 * Copyright 2015 The Closure Compiler Authors.
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
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/**
 * Converts {@code super} getter and setter calls in order to support the output
 * of the Dart Dev Compiler (https://github.com/dart-lang/dev_compiler). This has
 * to run before the {@link Es6ConvertSuper} pass.
 *
 * <p>Note that the approach taken here doesn't lend itself to good optimizations of getters and
 * setters. An alternative approach is needed to generate fully optimizable output before general
 * ES6&rarr;ES5 lowering of super accessor expressions can be rolled out.
 *
 * <p>TODO(ochafik): Add support for `super.x++` (and --, pre/post variants).
 *
 * @author ochafik@google.com (Olivier Chafik)
 */
public final class DartSuperAccessorsPass implements NodeTraversal.Callback,
    HotSwapCompilerPass {
  static final String CALL_SUPER_GET = "$jscomp.superGet";
  static final String CALL_SUPER_SET = "$jscomp.superSet";

  private final AbstractCompiler compiler;
  /**
   * Whether JSCompiler_renameProperty can and should be used (i.e. if we think a RenameProperties
   * pass will be run).
   */
  private final boolean renameProperties;

  public DartSuperAccessorsPass(AbstractCompiler compiler) {
    this.compiler = compiler;
    CompilerOptions options = compiler.getOptions();

    this.renameProperties = options.propertyRenaming == PropertyRenamingPolicy.ALL_UNQUOTED;

    Preconditions.checkState(options.getLanguageOut().isEs5OrHigher(),
        "Dart super accessors pass requires ES5+ output");

    // We currently rely on JSCompiler_renameProperty, which is not type-aware.
    // We would need something like goog.reflect.object (with the super class type),
    // but right now this would yield much larger code.
    Preconditions.checkState(!options.ambiguateProperties && !options.disambiguateProperties,
        "Dart super accessors pass is not compatible with property (dis)ambiguation yet");
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (isSuperGet(n)) {
      visitSuperGet(n);
      return false;
    } else if (isSuperSet(n)) {
      if (!n.isAssign()) {
        n = normalizeAssignmentOp(n);
      }
      visitSuperSet(n);
      return false;
    }
    return true;
  }

  private static boolean isCalled(Node n) {
    Node parent = n.getParent();
    return parent.isCall() && (n == parent.getFirstChild());
  }

  /** Transforms `a += b` to `a = a + b`. */
  private static Node normalizeAssignmentOp(Node n) {
    Node lhs = n.getFirstChild();
    Node rhs = n.getLastChild();
    Node newRhs = new Node(NodeUtil.getOpFromAssignmentOp(n),
        lhs.cloneTree(), rhs.cloneTree()).srcrefTree(n);
    return replace(n, IR.assign(lhs.cloneTree(), newRhs).srcrefTree(n));
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {}

  private boolean isSuperGet(Node n) {
    return (n.isGetProp() || n.isGetElem())
        && !isCalled(n)
        && n.getFirstChild().isSuper()
        && isInsideInstanceMember(n);
  }

  private boolean isSuperSet(Node n) {
    return NodeUtil.isAssignmentOp(n) && isSuperGet(n.getFirstChild());
  }

  /**
   * Returns true if this node is or is enclosed by an instance member definition
   * (non-static method, getter or setter).
   */
  private static boolean isInsideInstanceMember(Node n) {
    while (n != null) {
      if (n.isMemberFunctionDef()
          || n.isGetterDef()
          || n.isSetterDef()
          || n.isComputedProp()) {
        return !n.isStaticMember();
      }
      if (n.isClass()) {
        // Stop at the first enclosing class.
        return false;
      }
      n = n.getParent();
    }
    return false;
  }

  private void visitSuperGet(Node superGet) {
    Node name = superGet.getLastChild().cloneTree();
    Node callSuperGet = IR.call(
        NodeUtil.newQName(compiler, CALL_SUPER_GET),
        IR.thisNode(),
        superGet.isGetProp() ? renameProperty(name) : name);
    replace(superGet, callSuperGet.srcrefTree(superGet));
    reportEs6Change();
  }

  private void visitSuperSet(Node superSet) {
    Preconditions.checkArgument(superSet.isAssign());

    // First, recurse on the assignment's right-hand-side.
    NodeTraversal.traverseEs6(compiler, superSet.getLastChild(), this);
    Node rhs = superSet.getLastChild();

    Node superGet = superSet.getFirstChild();

    Node name = superGet.getLastChild().cloneTree();
    Node callSuperSet = IR.call(
        NodeUtil.newQName(compiler, CALL_SUPER_SET),
        IR.thisNode(),
        superGet.isGetProp() ? renameProperty(name) : name,
        rhs.cloneTree());
    replace(superSet, callSuperSet.srcrefTree(superSet));
    reportEs6Change();
  }

  private void reportEs6Change() {
    compiler.ensureLibraryInjected("es6_dart_runtime", false);
    compiler.reportCodeChange();
  }

  private static Node replace(Node original, Node replacement) {
    original.getParent().replaceChild(original, replacement);
    return replacement;
  }

  /**
   * Wraps a property string in a JSCompiler_renameProperty call.
   *
   * <p>Should only be called in phases running before {@link RenameProperties},
   * if such a pass is even used (see {@link #renameProperties}).
   */
  private Node renameProperty(Node propertyName) {
    Preconditions.checkArgument(propertyName.isString());
    if (!renameProperties) {
      return propertyName;
    }
    Node call = IR.call(IR.name(NodeUtil.JSC_PROPERTY_NAME_FN), propertyName);
    call.srcrefTree(propertyName);
    call.putBooleanProp(Node.FREE_CALL, true);
    call.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    return call;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, externs, this);
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }
}
