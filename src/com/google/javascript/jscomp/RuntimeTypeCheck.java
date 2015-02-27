/*
 * Copyright 2010 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;

import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;

import javax.annotation.Nullable;

/**
 * Inserts run-time type assertions.
 *
 * <p>We add markers to user-defined interfaces and classes in order to check if
 * an object conforms to that type.
 *
 * <p>For each function, we insert a run-time type assertion for each parameter
 * and return value for which the compiler has a type.
 *
 * <p>The JavaScript code which implements the type assertions is in
 * js/runtime-type-check.js.
 *
 */
class RuntimeTypeCheck implements CompilerPass {

  private static final Comparator<JSType> ALPHA = new Comparator<JSType>() {
    @Override
    public int compare(JSType t1, JSType t2) {
      return getName(t1).compareTo(getName(t2));
    }

    private String getName(JSType type) {
      if (type.isInstanceType()) {
        return ((ObjectType) type).getReferenceName();
      } else if (type.isNullType()
          || type.isBooleanValueType()
          || type.isNumberValueType()
          || type.isStringValueType()
          || type.isVoidType()) {
        return type.toString();
      } else {
        // Type unchecked at runtime, so we don't care about the sorting order.
        return "";
      }
    }
  };

  private final AbstractCompiler compiler;
  private final String logFunction;

  RuntimeTypeCheck(AbstractCompiler compiler, @Nullable String logFunction) {
    this.compiler = compiler;
    this.logFunction = logFunction;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new AddMarkers(compiler));
    NodeTraversal.traverse(compiler, root, new AddChecks());
    addBoilerplateCode();
    new Normalize(compiler, false).process(externs, root);
  }

  /**
   * Inserts marker properties for user-defined interfaces and classes.
   *
   * <p>For example, for a class C, we add
   * {@code C.prototype['instance_of__C']}, and for each interface I it
   * implements , we add {@code C.prototype['implements__I']}.
   *
   * <p>Since interfaces are not a run-time JS concept, we use these markers to
   * recognize an interface implementation at runtime. We also use markers for
   * user-defined classes, so that we can easily recognize them independently of
   * which module they are defined in and whether the module is loaded.
   */
  private static class AddMarkers
      extends NodeTraversal.AbstractPostOrderCallback {

    private final AbstractCompiler compiler;

    private AddMarkers(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isFunction()) {
        visitFunction(n);
      }
    }

    private void visitFunction(Node n) {
      FunctionType funType = n.getJSType().toMaybeFunctionType();
      if (funType != null && !funType.isConstructor()) {
        return;
      }

      Node nodeToInsertAfter = findNodeToInsertAfter(n);

      nodeToInsertAfter = addMarker(funType, nodeToInsertAfter, null);

      TreeSet<ObjectType> stuff = Sets.newTreeSet(ALPHA);
      Iterables.addAll(stuff, funType.getAllImplementedInterfaces());
      for (ObjectType interfaceType : stuff) {
        nodeToInsertAfter =
            addMarker(funType, nodeToInsertAfter, interfaceType);
      }
    }

    private Node addMarker(
            FunctionType funType,
            Node nodeToInsertAfter,
            @Nullable ObjectType interfaceType) {

      if (funType.getSource() == null) {
        return nodeToInsertAfter;
      }

      String className = NodeUtil.getFunctionName(funType.getSource());

      // This can happen with anonymous classes declared with the type
      // {@code Function}.
      if (className == null) {
        return nodeToInsertAfter;
      }

      Node classNode = NodeUtil.newQName(
          compiler, className);

      Node marker = IR.string(
              interfaceType == null ?
              "instance_of__" + className :
              "implements__" + interfaceType.getReferenceName());

      Node assign = IR.exprResult(IR.assign(
          IR.getelem(
              IR.getprop(
                  classNode,
                  IR.string("prototype")), marker),
          IR.trueNode()));

      nodeToInsertAfter.getParent().addChildAfter(assign, nodeToInsertAfter);
      compiler.reportCodeChange();
      nodeToInsertAfter = assign;
      return nodeToInsertAfter;
    }

    /**
     * Find the node to insert the markers after. Typically, this node
     * corresponds to the constructor declaration, but we want to skip any of
     * the white-listed function calls.
     *
     * @param n the constructor function node
     * @return the node to insert after
     */
    private Node findNodeToInsertAfter(Node n) {
      Node nodeToInsertAfter = findEnclosingConstructorDeclaration(n);

      Node next = nodeToInsertAfter.getNext();
      while (next != null && isClassDefiningCall(next)) {
        nodeToInsertAfter = next;
        next = nodeToInsertAfter.getNext();
      }

      return nodeToInsertAfter;
    }

    private static Node findEnclosingConstructorDeclaration(Node n) {
      while (!n.getParent().isScript() && !n.getParent().isBlock()) {
        n = n.getParent();
      }
      return n;
    }

    private boolean isClassDefiningCall(Node next) {
      return NodeUtil.isExprCall(next) &&
          compiler.getCodingConvention().getClassesDefinedByCall(
              next.getFirstChild()) != null;
    }
  }

  /**
   * Insert calls to the run-time type checking function {@code checkType}, which
   * takes an expression to check and a list of checkers (one of which must
   * match). It returns the expression back to facilitate checking of return
   * values. We have checkers for value types, class types (user-defined and
   * externed), and interface types.
   */
  private class AddChecks
      extends NodeTraversal.AbstractPostOrderCallback {

    private AddChecks() {
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isFunction()) {
        visitFunction(n);
      } else if (n.isReturn()) {
        visitReturn(t, n);
      }
    }

    /**
     * Insert checks for the parameters of the function.
     */
    private void visitFunction(Node n) {
      FunctionType funType = JSType.toMaybeFunctionType(n.getJSType());
      Node block = n.getLastChild();
      Node paramName = NodeUtil.getFunctionParameters(n).getFirstChild();
      Node insertionPoint = null;

      // To satisfy normalization constraints, the type checking must be
      // added after any inner function declarations.
      for (Node next = block.getFirstChild();
           next != null && NodeUtil.isFunctionDeclaration(next);
           next = next.getNext()) {
        insertionPoint = next;
      }

      for (Node paramType : funType.getParameters()) {
        // Can this ever happen?
        if (paramName == null) {
          return;
        }

        Node checkNode = createCheckTypeCallNode(
            paramType.getJSType(), paramName.cloneTree());

        if (checkNode == null) {
          // We don't know how to check this parameter type.
          paramName = paramName.getNext();
          continue;
        }

        checkNode = IR.exprResult(checkNode);
        if (insertionPoint == null) {
          block.addChildToFront(checkNode);
        } else {
          block.addChildAfter(checkNode, insertionPoint);
        }

        compiler.reportCodeChange();
        paramName = paramName.getNext();
        insertionPoint = checkNode;
      }
    }

    private void visitReturn(NodeTraversal t, Node n) {
      Node function = t.getEnclosingFunction();
      FunctionType funType = function.getJSType().toMaybeFunctionType();

      Node retValue = n.getFirstChild();
      if (retValue == null) {
        return;
      }

      Node checkNode = createCheckTypeCallNode(
          funType.getReturnType(), retValue.cloneTree());

      if (checkNode == null) {
        return;
      }

      n.replaceChild(retValue, checkNode);
      compiler.reportCodeChange();
    }

    /**
     * Creates a function call to check that the given expression matches the
     * given type at runtime.
     *
     * <p>For example, if the type is {@code (string|Foo)}, the function call is
     * {@code checkType(expr, [valueChecker('string'), classChecker('Foo')])}.
     *
     * @return the function call node or {@code null} if the type is not checked
     */
    private Node createCheckTypeCallNode(JSType type, Node expr) {
      Node arrayNode = IR.arraylit();
      Collection<JSType> alternates;
      if (type.isUnionType()) {
        alternates = Sets.newTreeSet(ALPHA);
        alternates.addAll(type.toMaybeUnionType().getAlternates());
      } else {
        alternates = ImmutableList.of(type);
      }
      for (JSType alternate : alternates) {
        Node checkerNode = createCheckerNode(alternate);
        if (checkerNode == null) {
          return null;
        }
        arrayNode.addChildToBack(checkerNode);
      }
      return IR.call(jsCode("checkType"), expr, arrayNode);
    }

    /**
     * Creates a node which evaluates to a checker for the given type (which
     * must not be a union). We have checkers for value types, classes and
     * interfaces.
     *
     * @return the checker node or {@code null} if the type is not checked
     */
    private Node createCheckerNode(JSType type) {
      if (type.isNullType()) {
        return jsCode("nullChecker");

      } else if (type.isBooleanValueType()
          || type.isNumberValueType()
          || type.isStringValueType()
          || type.isVoidType()) {
        return IR.call(
            jsCode("valueChecker"),
            IR.string(type.toString()));

      } else if (type.isInstanceType()) {
        ObjectType objType = (ObjectType) type;

        String refName = objType.getReferenceName();

        if (refName.equals("Object")) {
          return jsCode("objectChecker");
        }

        StaticSourceFile sourceFile =
            NodeUtil.getSourceFile(objType.getConstructor().getSource());
        if (sourceFile == null || sourceFile.isExtern()) {
          return IR.call(
                  jsCode("externClassChecker"),
                  IR.string(refName));
        }

        return IR.call(
                jsCode(objType.getConstructor().isInterface() ?
                        "interfaceChecker" : "classChecker"),
                IR.string(refName));

      } else {
        // We don't check this type (e.g. unknown & all types).
        return null;
      }
    }
  }

  private void addBoilerplateCode() {
    Node newNode = compiler.ensureLibraryInjected("runtime_type_check", true);
    if (newNode != null && logFunction != null) {
      // Inject the custom log function.
      Node logOverride = IR.exprResult(
          IR.assign(
              NodeUtil.newQName(
                  compiler,
                  "$jscomp.typecheck.log"),
              NodeUtil.newQName(
                  compiler,
                  logFunction)));
      newNode.getParent().addChildAfter(logOverride, newNode);
      compiler.reportCodeChange();
    }
  }

  private Node jsCode(String prop) {
    return NodeUtil.newQName(
        compiler, "$jscomp.typecheck." + prop);
  }
}
