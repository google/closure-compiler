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

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import java.util.LinkedHashMap;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Traverses the AST, collecting connections between {@link JSType}s, property accesses, and their
 * accociated {@link Node}s.
 *
 * <p>Also collects declarations of constructors even if no property accesses are visible.
 *
 * <p>This callback is intended for both source and externs.
 */
final class ColorFindPropertyReferences extends AbstractPostOrderCallback {

  /**
   * Tests whether the named JS function is a "property reflector"; a function that treats a string
   * literal as a property name.
   *
   * <p>Such a function is assumed to have the following signature:
   *
   * <ol>
   *   <li>A string literal that is the name being reflected.
   *   <li>An object on which the property is referenced. (Optional)
   *   <li>Var args.
   * </ol>
   */
  @FunctionalInterface
  interface IsPropertyReflector {
    boolean test(Node nameNode);
  }

  private @Nullable LinkedHashMap<String, PropertyClustering> propIndex = new LinkedHashMap<>();

  private final ColorGraphNodeFactory colorGraphNodeFactory;
  private final IsPropertyReflector isPropertyReflector;

  ColorFindPropertyReferences(
      ColorGraphNodeFactory colorGraphNodeFactory, IsPropertyReflector isPropertyReflector) {
    this.colorGraphNodeFactory = colorGraphNodeFactory;
    this.isPropertyReflector = isPropertyReflector;
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
      case OPTCHAIN_GETPROP:
        this.registerPropertyUse(t, n, n.getFirstChild().getColor());
        break;
      case OBJECTLIT:
        this.handleObjectLit(t, n);
        break;
      case CALL:
        this.handleCall(t, n);
        break;
      case CLASS:
        this.handleClass(t, n);
        break;
      case OBJECT_PATTERN:
        this.handleObjectPattern(t, n);
        break;
      case FUNCTION:
        this.handleFunction(n);
        break;
      default:
        break;
    }
  }

  private void handleObjectLit(NodeTraversal t, Node n) {
    // Object.defineProperties literals are handled at the CALL node.
    if (n.getParent().isCall() && NodeUtil.isObjectDefinePropertiesDefinition(n.getParent())) {
      return;
    }

    Color owner = n.getColor();
    this.traverseObjectlitLike(t, n, (m) -> owner);
  }

  /** Examines calls in case they are Object.defineProperties calls */
  private void handleCall(NodeTraversal t, Node call) {
    Node target = call.getFirstChild();
    if (!target.isQualifiedName()) {
      return;
    }

    if (this.isPropertyReflector.test(target)) {
      this.handlePropertyReflectorCall(t, call);
    } else if (NodeUtil.isObjectDefinePropertiesDefinition(call)) {
      this.handleObjectDefineProperties(t, call);
    }
  }

  private void handleClass(NodeTraversal t, Node classNode) {
    Color classType = classNode.getColor();
    this.traverseObjectlitLike(
        t,
        NodeUtil.getClassMembers(classNode),
        (m) -> {
          if (m.isStaticMember()) {
            return classType;
          } else if (m.isMemberFieldDef()) {
            ImmutableSet<Color> classInstanceType = classType.getInstanceColors();
            return classInstanceType.isEmpty()
                ? StandardColors.UNKNOWN
                : Color.createUnion(classInstanceType);
          } else {
            checkState(m.isMemberFunctionDef() || m.isGetterDef() || m.isSetterDef(), m);
            ImmutableSet<Color> classPrototypeType = classType.getPrototypes();
            return classPrototypeType.isEmpty()
                ? StandardColors.UNKNOWN
                : Color.createUnion(classPrototypeType);
          }
        });
    colorGraphNodeFactory.createNode(classType);
  }

  private void handleFunction(Node fnNode) {
    // Ensure the flattener knows about the class constructor and instance. Even if we don't see any
    // direct property references off either type, it's possible that a property is referenced
    // via a superclass or implemented interface.
    // e.g. `const /** !FooInterface */ x = new Foo(); x.method();`
    Color fnType = fnNode.getColor();
    if (fnType != null && !fnType.getInstanceColors().isEmpty()) {
      this.colorGraphNodeFactory.createNode(fnType);
    }
  }

  private void handleObjectPattern(NodeTraversal t, Node pattern) {
    Color owner = pattern.getColor();
    this.traverseObjectlitLike(t, pattern, (m) -> owner);
  }

  private void handlePropertyReflectorCall(NodeTraversal t, Node call) {
    Node name = call.getSecondChild();
    if (name == null || !name.isStringLit()) {
      return;
    }

    Node obj = name.getNext();
    Color objColor = (obj == null) ? null : obj.getColor();
    this.registerPropertyUse(t, name, objColor);
  }

  private void handleObjectDefineProperties(NodeTraversal t, Node call) {
    Node typeObj = call.getSecondChild();
    Node objectLiteral = typeObj.getNext();
    if (!objectLiteral.isObjectLit()) {
      return;
    }

    Color type = typeObj.getColor();
    this.traverseObjectlitLike(t, objectLiteral, (m) -> type);
  }

  private void traverseObjectlitLike(NodeTraversal t, Node n, Function<Node, Color> memberOwnerFn) {
    // The keys in an object pattern are r-values, not l-values, but they are still accesses.
    checkState(n.isObjectLit() || n.isObjectPattern() || n.isClassMembers());

    for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
      switch (child.getToken()) {
        case BLOCK:
        case COMPUTED_PROP:
        case COMPUTED_FIELD_DEF:
        case OBJECT_REST:
        case OBJECT_SPREAD:
          continue;

        case STRING_KEY:
        case MEMBER_FUNCTION_DEF:
        case MEMBER_FIELD_DEF:
        case GETTER_DEF:
        case SETTER_DEF:
          if (child.isQuotedStringKey()) {
            continue; // These won't be renamed due to our assumptions. Ignore them.
          }

          this.registerPropertyUse(t, child, memberOwnerFn.apply(child));
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
  private void registerPropertyUse(NodeTraversal t, Node site, Color owner) {
    PropertyClustering prop =
        this.propIndex.computeIfAbsent(site.getString(), PropertyClustering::new);
    ColorGraphNode flatOwner = this.colorGraphNodeFactory.createNode(owner);

    // Set the initial condition for flowing this property along the graph.
    flatOwner.getAssociatedProps().put(prop, ColorGraphNode.PropAssociation.AST);
    // Make sure there's a cluster for this name/type combination.
    prop.getClusters().add(flatOwner);
    // Record the site to rename once clusters are found. If it's an extern, we won't rename anyway.
    prop.getUseSites().put(site, flatOwner);

    // Track the cluster of types whose properties must keep their original name after
    // disambiguation. Note: an "enum type" is the type of an enum object like "{STOP: 0, GO: 1}".
    // NOTE: we can't use site.isFromExterns() because sometimes nodes have source file information
    // that doesn't match the containing script. This could lead to renaming properties that are
    // in externs if the property node didn't have an externs source file. Related: b/186056977.
    if (t.getCurrentScript().isFromExterns()
        || (owner != null && owner.getPropertiesKeepOriginalName())) {
      prop.registerOriginalNameType(flatOwner);
    }
  }
}
