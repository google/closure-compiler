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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.io.CharStreams;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.UnionType;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;

import javax.annotation.Nullable;

/**
 * Inserts runtime type assertions.
 *
 * <p>We add markers to user-defined interfaces and classes in order to check if
 * an object conforms to that type.
 *
 * <p>For each function, we insert a runtime type assertion for each parameter
 * and return value for which the compiler has a type.
 *
 * <p>The JavaScript code which implements the type assertions is in
 * js/runtime-type-check.js.
 *
 */
class RuntimeTypeCheck implements CompilerPass {

  private static final Comparator<JSType> ALPHA = new Comparator<JSType>() {
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
    NodeTraversal.traverse(compiler, root, new AddChecks(compiler));
    addBoilerplateCode();
  }

  /**
   * Inserts marker properties for user-defined interfaces and classes.
   *
   * <p>For example, for a class C, we add
   * {@code C.prototype['instance_of__C']}, and for each interface I it
   * implements , we add {@code C.prototype['implements__I']}.
   *
   * <p>Since interfaces are not a runtime JS concept, we use these markers to
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
      if (NodeUtil.isFunction(n)) {
        visitFunction(t, n);
      }
    }

    private void visitFunction(NodeTraversal t, Node n) {
      FunctionType funType = (FunctionType) n.getJSType();
      if (!funType.isConstructor()) {
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

      Node classNode = NodeUtil.newQualifiedNameNode(className, -1, -1);

      Node marker = Node.newString(
              interfaceType == null ?
              "instance_of__" + className :
              "implements__" + interfaceType.getReferenceName());

      Node assign = new Node(Token.EXPR_RESULT, new Node(Token.ASSIGN,
          new Node(Token.GETELEM,
              new Node(Token.GETPROP,
                  classNode,
                  Node.newString("prototype")), marker),
          new Node(Token.TRUE)));

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

    private Node findEnclosingConstructorDeclaration(Node n) {
      while (n.getParent().getType() != Token.SCRIPT &&
          n.getParent().getType() != Token.BLOCK) {
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
   * Insert calls to the runtime type checking function {@code checkType}, which
   * takes an expression to check and a list of checkers (one of which must
   * match). It returns the expression back to facilitate checking of return
   * values. We have checkers for value types, class types (user-defined and
   * externed), and interface types.
   */
  private static class AddChecks
      extends NodeTraversal.AbstractPostOrderCallback {

    private final AbstractCompiler compiler;

    private AddChecks(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (NodeUtil.isFunction(n)) {
        visitFunction(t, n);
      } else if (n.getType() == Token.RETURN) {
        visitReturn(t, n);
      }
    }

    /**
     * Insert checks for the parameters of the function.
     */
    private void visitFunction(NodeTraversal t, Node n) {
      FunctionType funType = (FunctionType) n.getJSType();
      Node block = n.getLastChild();
      Node paramName = NodeUtil.getFnParameters(n).getFirstChild();
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

        checkNode = new Node(Token.EXPR_RESULT, checkNode);
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
      FunctionType funType = (FunctionType) function.getJSType();

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
      Node arrayNode = new Node(Token.ARRAYLIT);
      Collection<JSType> alternates;
      if (type.isUnionType()) {
        alternates = Sets.newTreeSet(ALPHA);
        Iterables.addAll(alternates, ((UnionType)type).getAlternates());
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
      return new Node(Token.CALL, jsCode("checkType"), expr, arrayNode);
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
        return new Node(Token.CALL,
            jsCode("valueChecker"),
            Node.newString(type.toString()));

      } else if (type.isInstanceType()) {
        ObjectType objType = (ObjectType) type;

        String refName = objType.getReferenceName();

        String sourceName =
            NodeUtil.getSourceName(objType.getConstructor().getSource());
        CompilerInput sourceInput = compiler.getInput(sourceName);
        if (sourceInput == null || sourceInput.isExtern()) {
          return new Node(Token.CALL,
                  jsCode("externClassChecker"),
                  Node.newString(refName));
        }

        return new Node(Token.CALL,
                jsCode(objType.getConstructor().isInterface() ?
                        "interfaceChecker" : "classChecker"),
                Node.newString(refName));

      } else {
        // We don't check this type (e.g. unknown & all types).
        return null;
      }
    }
  }

  private void addBoilerplateCode() {
    Node js = getBoilerplateCode(compiler, logFunction);
    compiler.getNodeForCodeInsertion(null).addChildrenToFront(
        js.removeChildren());
    compiler.reportCodeChange();
  }

  private static Node jsCode(String prop) {
    return NodeUtil.newQualifiedNameNode("jscomp.typecheck." + prop, -1, -1);
  }

  @VisibleForTesting
  static Node getBoilerplateCode(
      AbstractCompiler compiler, @Nullable String logFunction) {
    String boilerplateCode;
    try {
      boilerplateCode = CharStreams.toString(new InputStreamReader(
          RuntimeTypeCheck.class.getResourceAsStream(
          "js/runtime_type_check.js"), Charsets.UTF_8));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    boilerplateCode = boilerplateCode.replace("%%LOG%%",
        logFunction == null ? "function(warning, expr) {}" : logFunction);

    return Normalize.parseAndNormalizeSyntheticCode(
        compiler, boilerplateCode, "jscomp_runtimeTypeCheck_");
  }
}
