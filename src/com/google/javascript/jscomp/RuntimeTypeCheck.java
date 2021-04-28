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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Comparator.naturalOrder;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;
import javax.annotation.Nullable;

/**
 * Inserts run-time type assertions.
 *
 * <p>We add markers to user-defined interfaces and classes in order to check if an object conforms
 * to that type.
 *
 * <p>For each function, we insert a run-time type assertion for each parameter and return value for
 * which the compiler has a type.
 *
 * <p>The JavaScript code which implements the type assertions is in js/runtime_type_check.js.
 */
class RuntimeTypeCheck implements CompilerPass {

  private static final Comparator<JSType> ALPHA =
      new Comparator<JSType>() {
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
  private final JSTypeRegistry typeRegistry;
  private final String logFunction;

  RuntimeTypeCheck(AbstractCompiler compiler, @Nullable String logFunction) {
    this.compiler = compiler;
    this.typeRegistry = compiler.getTypeRegistry();
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
   * <p>For example, for a class C, we add {@code C.prototype['instance_of__C']}, and for each
   * interface I it implements , we add {@code C.prototype['implements__I']}.
   *
   * <p>Since interfaces are not a run-time JS concept, we use these markers to recognize an
   * interface implementation at runtime. We also use markers for user-defined classes, so that we
   * can easily recognize them independently of which module they are defined in and whether the
   * module is loaded.
   */
  private static class AddMarkers extends NodeTraversal.AbstractPostOrderCallback {

    private final AbstractCompiler compiler;
    private NodeTraversal traversal;

    private AddMarkers(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void visit(NodeTraversal t, Node node, Node unused) {
      this.traversal = t;

      @Nullable FunctionType funType = JSType.toMaybeFunctionType(node.getJSType());

      switch (node.getToken()) {
        case FUNCTION:
          if (NodeUtil.isEs6Constructor(node)) {
            break; // "constructor" members are not constructors.
          }

          visitPossibleClassDeclaration(
              funType, findNodeToInsertAfter(node), this::addMarkerToFunction, node);
          break;

        case CLASS:
          visitPossibleClassDeclaration(
              funType, node.getChildAtIndex(2), this::addMarkerToClass, node);
          break;

        default:
          break;
      }
    }

    private interface MarkerInserter {
      Node insert(String markerName, @Nullable String className, Node insertionPoint, Node srcref);
    }

    private void visitPossibleClassDeclaration(
        @Nullable FunctionType funType, Node insertionPoint, MarkerInserter inserter, Node srcref) {
      // Validate the class type.
      if (funType == null || funType.getSource() == null || !funType.isConstructor()) {
        return;
      }

      @Nullable String className = NodeUtil.getName(funType.getSource());

      // Assemble the marker names. Class marker first, then interfaces sorted aphabetically.
      ArrayList<String> markerNames = new ArrayList<>();
      for (ObjectType interfaceType : funType.getAllImplementedInterfaces()) {
        markerNames.add("implements__" + interfaceType.getReferenceName());
      }
      markerNames.sort(naturalOrder()); // Sort to ensure deterministic output.
      if (className != null) {
        // We can't generate markers for anonymous classes, but there's also no way to specify them
        // as a parameter type, so there will never be any checks for them either.
        markerNames.add(0, "instance_of__" + className);
      }

      // Insert the markers.
      for (String markerName : markerNames) {
        insertionPoint = inserter.insert(markerName, className, insertionPoint, srcref);
      }
    }

    /**
     * Adds a computed property method, with the name of the marker, to the class.
     *
     * <pre>{@code
     * class C {
     *   ['instance_of__C']() {}
     * }
     * }</pre>
     */
    private Node addMarkerToClass(
        String markerName, @Nullable String unused, Node classMembers, Node srcref) {
      Node function = IR.function(IR.name(""), IR.paramList(), IR.block());
      Node member = IR.computedProp(IR.string(markerName), function).srcrefTree(srcref);
      member.putBooleanProp(Node.COMPUTED_PROP_METHOD, true);
      classMembers.addChildToBack(member);

      compiler.reportChangeToEnclosingScope(member);
      compiler.reportChangeToChangeScope(function);
      NodeUtil.addFeatureToScript(
          traversal.getCurrentScript(), Feature.COMPUTED_PROPERTIES, compiler);
      return classMembers;
    }

    /**
     * Assigns a {@code true} prop, with the name of the marker. to the prototype of the class.
     *
     * <pre>{@code
     * /** @constructor *\/
     * function C() { }
     *
     * C.prototype['instance_of__C'] = true;
     * }</pre>
     */
    private Node addMarkerToFunction(
        String markerName, @Nullable String className, Node nodeToInsertAfter, Node srcref) {
      if (className == null) {
        // This can happen with anonymous classes declared with the type `Function`.
        return nodeToInsertAfter;
      }

      Node classNode = NodeUtil.newQName(compiler, className);
      Node assign =
          IR.exprResult(
                  IR.assign(
                      IR.getelem(IR.getprop(classNode, "prototype"), IR.string(markerName)),
                      IR.trueNode()))
              .srcrefTree(srcref);

      assign.insertAfter(nodeToInsertAfter);
      compiler.reportChangeToEnclosingScope(assign);
      return assign;
    }

    /**
     * Find the node to insert the markers after. Typically, this node corresponds to the
     * constructor declaration, but we want to skip any of the white-listed function calls.
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
      return NodeUtil.isExprCall(next)
          && compiler.getCodingConvention().getClassesDefinedByCall(next.getFirstChild()) != null;
    }
  }

  /**
   * Insert calls to the run-time type checking function {@code checkType}, which takes an
   * expression to check and a list of checkers (one of which must match). It returns the expression
   * back to facilitate checking of return values. We have checkers for value types, class types
   * (user-defined and externed), and interface types.
   */
  private class AddChecks extends NodeTraversal.AbstractPostOrderCallback {

    private AddChecks() {}

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (NodeUtil.isInSyntheticScript(n)) {
        return;
      }

      switch (n.getToken()) {
        case FUNCTION:
          visitFunction(n);
          break;

        case RETURN:
        case YIELD:
          visitTerminal(t, n);
          break;

        default:
          break;
      }
    }

    /** Insert checks for the parameters of the function. */
    private void visitFunction(Node n) {
      Node block = n.getLastChild();
      Node insertionPoint = null;

      // To satisfy normalization constraints, the type checking must be
      // added after any inner function declarations.
      for (Node next = block.getFirstChild();
          next != null && NodeUtil.isFunctionDeclaration(next);
          next = next.getNext()) {
        insertionPoint = next;
      }

      for (Node paramName : paramNamesOf(NodeUtil.getFunctionParameters(n))) {
        checkState(paramName.isName(), paramName);

        Node checkNode = createCheckTypeCallNode(paramName.getJSType(), paramName.cloneTree());

        if (checkNode == null) {
          // We don't know how to check this parameter type.
          paramName = paramName.getNext();
          continue;
        }

        checkNode = IR.exprResult(checkNode).srcrefTreeIfMissing(paramName);
        if (insertionPoint == null) {
          block.addChildToFront(checkNode);
        } else {
          checkNode.insertAfter(insertionPoint);
        }

        compiler.reportChangeToEnclosingScope(block);
        insertionPoint = checkNode;
      }
    }

    private void visitTerminal(NodeTraversal t, Node n) {
      Node function = t.getEnclosingFunction();

      FunctionType funType = JSType.toMaybeFunctionType(function.getJSType());
      if (funType == null) {
        return;
      }

      Node retValue = n.getFirstChild();
      if (retValue == null) {
        return;
      }

      // Transform the documented return type of the function into the appropriate terminal type
      // based on any function or terminator modifiers. (e.g. `async`, `yield*`)
      JSType expectedTerminalType = funType.getReturnType();
      if (function.isGeneratorFunction()) {
        expectedTerminalType = JsIterables.getElementType(expectedTerminalType, typeRegistry);
      }
      if (function.isAsyncFunction()) {
        expectedTerminalType =
            Promises.createAsyncReturnableType(typeRegistry, expectedTerminalType);
      }
      if (n.isYieldAll()) {
        expectedTerminalType = JsIterables.createIterableTypeOf(expectedTerminalType, typeRegistry);
      }

      Node checkNode = createCheckTypeCallNode(expectedTerminalType, retValue.cloneTree());
      if (checkNode == null) {
        return;
      }

      retValue.replaceWith(checkNode.srcrefTreeIfMissing(retValue));
      t.reportCodeChange();
    }

    /**
     * Creates a function call to check that the given expression matches the given type at runtime.
     *
     * <p>For example, if the type is {@code (string|Foo)}, the function call is {@code
     * checkType(expr, [valueChecker('string'), classChecker('Foo')])}.
     *
     * @return the function call node or {@code null} if the type is not checked
     */
    private Node createCheckTypeCallNode(JSType type, Node expr) {
      final Collection<JSType> alternates;
      if (type.isUnionType()) {
        alternates = new TreeSet<>(ALPHA); // Sorted to ensure deterministic output
        alternates.addAll(type.toMaybeUnionType().getAlternates());
      } else {
        alternates = ImmutableList.of(type);
      }

      Node arrayNode = IR.arraylit();
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
     * Creates a node which evaluates to a checker for the given type (which must not be a union).
     * We have checkers for value types, classes and interfaces.
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
        return IR.call(jsCode("valueChecker"), IR.string(type.toString()));
      } else if (type.isInstanceType()) {
        ObjectType objType = (ObjectType) type;

        String refName = objType.getReferenceName();

        if (refName.equals("Object")) {
          return jsCode("objectChecker");
        }

        StaticSourceFile sourceFile = NodeUtil.getSourceFile(objType.getConstructor().getSource());
        if (sourceFile == null || sourceFile.isExtern()) {
          return IR.call(jsCode("externClassChecker"), IR.string(refName));
        }

        return IR.call(
            jsCode(objType.getConstructor().isInterface() ? "interfaceChecker" : "classChecker"),
            IR.string(refName));

      } else if (type.isFunctionType()) {
        return IR.call(jsCode("valueChecker"), IR.string("function"));
      } else {
        // We don't check this type (e.g. unknown & all types).
        return null;
      }
    }
  }

  /**
   * Returns the NAME parameter nodes of a FUNCTION.
   *
   * <p>This lookup abstracts over the other legal node types in a PARAM_LIST. It includes only
   * those nodes that declare bindings within the function body.
   */
  private static ImmutableList<Node> paramNamesOf(Node paramList) {
    checkArgument(paramList.isParamList(), paramList);

    ImmutableList.Builder<Node> builder = ImmutableList.builder();
    NodeUtil.getParamOrPatternNames(paramList, builder::add);
    return builder.build();
  }

  private void addBoilerplateCode() {
    Node newNode = compiler.ensureLibraryInjected("runtime_type_check", false);
    if (newNode != null) {
      injectCustomLogFunction(newNode);
    }
  }

  @VisibleForTesting
  void injectCustomLogFunction(Node node) {
    if (logFunction == null) {
      return;
    }
    checkState(
        NodeUtil.isValidQualifiedName(compiler.getFeatureSet(), logFunction),
        "%s is not a valid qualified name",
        logFunction);
    Node logOverride =
        IR.exprResult(
                IR.assign(
                    NodeUtil.newQName(compiler, "$jscomp.typecheck.log"),
                    NodeUtil.newQName(compiler, logFunction)))
            .srcrefTree(node);
    checkState(node.getParent().isScript(), node.getParent());
    logOverride.insertAfter(node);
    compiler.reportChangeToEnclosingScope(node);
  }

  private Node jsCode(String prop) {
    return NodeUtil.newQName(compiler, "$jscomp.typecheck." + prop);
  }
}
