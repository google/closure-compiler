/*
 * Copyright 2008 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.disambiguate;

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.PropertyRenamingDiagnostics;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Traverses the AST, collecting connections between {@link JSType}s, property access, and their
 * accociated {@link Node}s.
 *
 * <p>This callback is intended for both source and externs.
 */
final class FindPropertyReferences extends AbstractPostOrderCallback {

  /**
   * Tests whether the named JS function is a "property definer", similar to
   * `Object.defineProperty`.
   *
   * <p>Such a function is assumed to accept two or three arguments:
   *
   * <ol>
   *   <li>A string literal that is the name being defined.
   *   <li>An object on which to define the property.
   *   <li>An property descriptor. (optional)
   * </ol>
   *
   * <p>This allows the compiler to recognize calls to such functions as property definitions, even
   * though they may be custom-defined in user code and the property name is quoted.
   */
  @FunctionalInterface
  interface IsPropertyDefiner {
    boolean test(String name);
  }

  private LinkedHashMap<String, PropertyClustering> propIndex = new LinkedHashMap<>();

  private final TypeFlattener flattener;
  private final Consumer<JSError> errorCb;
  private final IsPropertyDefiner isPropertyDefiner;

  FindPropertyReferences(
      TypeFlattener flattener, Consumer<JSError> errorCb, IsPropertyDefiner isPropertyDefiner) {
    this.flattener = flattener;
    this.errorCb = errorCb;
    this.isPropertyDefiner = isPropertyDefiner;
  }

  LinkedHashMap<String, PropertyClustering> getPropertyIndex() {
    LinkedHashMap<String, PropertyClustering> tmp = this.propIndex;
    this.propIndex = null;
    return tmp;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case GETPROP:
        this.registerPropertyUse(n.getLastChild(), n.getFirstChild().getJSType());
        break;
      case OBJECTLIT:
        this.handleObjectLit(n);
        break;
      case CALL:
        this.handleCall(n);
        break;
      case CLASS:
        this.handleClass(n);
        break;
      case OBJECT_PATTERN:
        this.handleObjectPattern(n);
        break;
      default:
        break;
    }
  }

  private void handleObjectLit(Node n) {
    // Object.defineProperties literals are handled at the CALL node.
    if (n.getParent().isCall() && NodeUtil.isObjectDefinePropertiesDefinition(n.getParent())) {
      return;
    }

    JSType owner = n.getJSType();
    this.traverseObjectlitLike(n, (m) -> owner);
  }

  /** Examines calls in case they are Object.defineProperties calls */
  private void handleCall(Node call) {
    Node target = call.getFirstChild();
    if (!target.isQualifiedName()) {
      return;
    }

    String functionName = target.getOriginalQualifiedName();
    if (functionName != null && this.isPropertyDefiner.test(functionName)) {
      this.handlePropertyDefiningFunctionCall(call, functionName);
    } else if (NodeUtil.isObjectDefinePropertiesDefinition(call)) {
      this.handleObjectDefineProperties(call);
    }
  }

  private void handleClass(Node classNode) {
    JSType classType = classNode.getJSType();
    JSType classPrototypeType =
        // the class type may not be a function type if it was in a cast, so treat it as unknown
        classType.isFunctionType() ? classType.toMaybeFunctionType().getPrototypeProperty() : null;

    this.traverseObjectlitLike(
        NodeUtil.getClassMembers(classNode),
        (m) -> m.isStaticMember() ? classType : classPrototypeType);
  }

  private void handleObjectPattern(Node pattern) {
    JSType owner = pattern.getJSType();
    this.traverseObjectlitLike(pattern, (m) -> owner);
  }

  private void handlePropertyDefiningFunctionCall(Node call, String renameFunctionName) {
    int childCount = call.getChildCount();
    int argCount = childCount - 1;
    if (argCount != 1 && argCount != 2) {
      this.errorCb.accept(
          JSError.make(
              call,
              PropertyRenamingDiagnostics.INVALID_RENAME_FUNCTION,
              renameFunctionName,
              " Must be called with 1 or 2 arguments"));
      return;
    }

    if (!call.getSecondChild().isString()) {
      this.errorCb.accept(
          JSError.make(
              call,
              PropertyRenamingDiagnostics.INVALID_RENAME_FUNCTION,
              renameFunctionName,
              " The first argument must be a string literal."));
      return;
    }

    String propName = call.getSecondChild().getString();

    if (propName.contains(".")) {
      this.errorCb.accept(
          JSError.make(
              call,
              PropertyRenamingDiagnostics.INVALID_RENAME_FUNCTION,
              renameFunctionName,
              " The first argument must not be a property path."));
      return;
    }

    Node obj = call.getChildAtIndex(2);
    this.registerPropertyUse(call.getSecondChild(), obj.getJSType());
  }

  private void handleObjectDefineProperties(Node call) {
    Node typeObj = call.getSecondChild();
    Node objectLiteral = typeObj.getNext();
    if (!objectLiteral.isObjectLit()) {
      return;
    }

    JSType type = typeObj.getJSType();
    this.traverseObjectlitLike(objectLiteral, (m) -> type);
  }

  private void traverseObjectlitLike(Node n, Function<Node, JSType> memberOwnerFn) {
    // The keys in an object pattern are r-values, not l-values, but they are still accesses.
    checkState(n.isObjectLit() || n.isObjectPattern() || n.isClassMembers());

    for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
      switch (child.getToken()) {
        case COMPUTED_PROP:
        case OBJECT_REST:
        case OBJECT_SPREAD:
          continue;

        case STRING_KEY:
        case MEMBER_FUNCTION_DEF:
        case GETTER_DEF:
        case SETTER_DEF:
          if (child.isQuotedString()) {
            continue; // These won't be renamed due to our assumptions. Ignore them.
          }

          this.registerPropertyUse(child, memberOwnerFn.apply(child));
          break;

        default:
          throw new IllegalStateException(
              "Unexpected child of " + n.getToken() + ": " + child.toStringTree());
      }
    }
  }

  /**
   * Update all datastructures as necessary to consider property use {@code site} from type {@code
   * owner}.
   */
  private void registerPropertyUse(Node site, JSType owner) {
    PropertyClustering prop =
        this.propIndex.computeIfAbsent(site.getString(), PropertyClustering::new);
    FlatType flatOwner = this.flattener.flatten(owner);

    // Set the initial condition for flowing this property along the graph.
    flatOwner.getAssociatedProps().add(prop);
    // Make sure there's a cluster for this name/type combination.
    prop.getClusters().add(flatOwner);
    // Record the site to rename once clusters are found. If it's an extern, we won't rename anyway.
    prop.getUseSites().put(site, flatOwner);

    // Track the externs cluster accordingly.
    if (site.isFromExterns()) {
      prop.registerExternType(flatOwner);
    }
  }
}
