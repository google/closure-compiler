/*
 * Copyright 2012 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * InlineProperties attempts to find references to properties that are known to be constants and
 * inline the known value.
 *
 * <p>This pass relies on type information to find these property references and properties are
 * assumed to be constant if they are assigned exactly once, unconditionally, in either of the
 * following contexts: (1) statically on a constructor, or (2) on a class's prototype.
 *
 * <p>The current implementation only inlines immutable values (as defined by
 * NodeUtil.isImmutableValue).
 */
final class InlineProperties implements CompilerPass {

  private final AbstractCompiler compiler;
  private final ColorRegistry registry;

  private static class PropertyInfo {
    PropertyInfo(Color color, Node value) {
      this.color = color;
      this.value = value;
    }

    final Color color;
    final Node value;
  }

  private static final PropertyInfo INVALIDATED = new PropertyInfo(null, null);

  private final Map<String, PropertyInfo> props = new HashMap<>();

  InlineProperties(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.registry = compiler.getColorRegistry();
    invalidateExternProperties();
  }

  private void invalidateExternProperties() {
    // Invalidate properties defined in externs.
    for (String name : compiler.getExternProperties()) {
      props.put(name, INVALIDATED);
    }
  }

  /** This method gets the JSType from the Node argument and verifies that it is present. */
  private Color getColor(Node n) {
    Color color = n.getColor();

    if (color == null) {
      return StandardColors.UNKNOWN;
    } else {
      return color;
    }
  }

  @Override
  public void process(Node externs, Node root) {
    // Find and replace the properties in non-extern AST.
    NodeTraversal.traverse(compiler, root, new GatherCandidates());
    NodeTraversal.traverse(compiler, root, new ReplaceCandidates());
  }

  class GatherCandidates extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      // These are assigned at most once in the branches below
      final boolean invalidatingPropRef;
      final String propName;
      if (n.isGetProp()) {
        propName = n.getString();
        if (parent.isAssign()) {
          invalidatingPropRef = !maybeRecordCandidateGetpropDefinition(t, n, parent);
        } else if (NodeUtil.isLValue(n)) {
          // Other LValue references invalidate
          // e.g. in an enhanced for loop or a destructuring statement
          invalidatingPropRef = true;
        } else if (parent.isDelProp()) {
          // Deletes invalidate
          invalidatingPropRef = true;
        } else {
          // A property read doesn't invalidate
          invalidatingPropRef = false;
        }
      } else if ((n.isStringKey() && !n.getParent().isObjectPattern())
          || n.isGetterDef()
          || n.isSetterDef()
          || n.isMemberFunctionDef()) {
        propName = n.getString();
        // For now, any object literal key invalidates
        // TODO(johnlenz): support prototype properties like:
        //   foo.prototype = { a: 1, b: 2 };
        // TODO(johnlenz): Object.create(), Object.createProperty
        // and getter/setter defs and member functions also invalidate
        // since we do not inline functions in this pass
        // Note that string keys in destructuring patterns are fine, since they just access the prop
        invalidatingPropRef = true;
      } else if (n.isMemberFieldDef()) {
        propName = n.getString();
        invalidatingPropRef = !maybeRecordCandidateClassFieldDefinition(n);
      } else {
        return;
      }

      if (invalidatingPropRef) {
        checkNotNull(propName);
        invalidateProperty(propName);
      }
    }

    /** @return Whether this is a valid definition for a candidate class field. */
    private boolean maybeRecordCandidateClassFieldDefinition(Node n) {
      checkState(n.isMemberFieldDef(), n);
      Node src = n.getFirstChild();
      String propName = n.getString();
      Node classNode = n.getGrandparent();
      final Color c;

      if (n.isStaticMember()) {
        c = getColor(classNode);
      } else {
        ImmutableSet<Color> possibleInstances = getColor(classNode).getInstanceColors();
        c =
            possibleInstances.isEmpty()
                ? StandardColors.UNKNOWN
                : Color.createUnion(possibleInstances);
      }

      return maybeStoreCandidateValue(c, propName, src);
    }

    /** @return Whether this is a valid definition for a candidate property. */
    private boolean maybeRecordCandidateGetpropDefinition(NodeTraversal t, Node n, Node parent) {
      checkState(n.isGetProp() && parent.isAssign(), n);
      Node src = n.getFirstChild();
      String propName = n.getString();
      Node value = parent.getLastChild();

      if (src.isThis()) {
        // This is a simple assignment like:
        //    this.foo = 1;
        if (inConstructor(t)) {
          // This may be a valid assignment.
          return maybeStoreCandidateValue(getColor(src), propName, value);
        }
        return false;
      } else if (t.inGlobalHoistScope() && src.isGetProp() && src.getString().equals("prototype")) {
        // This is a prototype assignment like:
        //    x.prototype.foo = 1;
        Color instanceType = getColor(src);
        return maybeStoreCandidateValue(instanceType, propName, value);
      } else if (t.inGlobalHoistScope()) {
        // This is a static assignment like:
        //    x.foo = 1;
        Color targetType = getColor(src);
        if (targetType != null && targetType.isConstructor()) {
          return maybeStoreCandidateValue(targetType, propName, value);
        }
      }
      return false;
    }

    private void invalidateProperty(String propName) {
      props.put(propName, INVALIDATED);
    }

    /**
     * Adds the candidate property to the map if it meets all constness and immutability criteria,
     * and is not already present in the map. If the property was already present, it is
     * invalidated. Returns true if the property was successfully added.
     */
    private boolean maybeStoreCandidateValue(Color color, String propName, Node value) {
      checkNotNull(value);
      if (!props.containsKey(propName)
          && !color.isInvalidating()
          && NodeUtil.isImmutableValue(value)
          && NodeUtil.isExecutedExactlyOnce(value)) {
        props.put(propName, new PropertyInfo(color, value));
        return true;
      }
      return false;
    }

    /**
     * Returns whether the traversal is directly in an ES6 class constructor or an @constructor
     * function
     *
     * <p>This returns false for nested functions inside ctors, including arrow functions (even
     * though the `this` is the same). This pass only cares about property definitions executed once
     * per ctor invocation, and in general we don't know how many times an arrow fn will be
     * executed. In the future, we could special case arrow fn IIFEs in this pass if it becomes
     * useful.
     */
    private boolean inConstructor(NodeTraversal t) {
      Node root = t.getEnclosingFunction();
      if (root == null) { // we might be in the global scope
        return false;
      }
      return NodeUtil.isConstructor(root);
    }
  }

  class ReplaceCandidates extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isGetProp() && !NodeUtil.isLValue(n)) {
        Node target = n.getFirstChild();
        String propName = n.getString();
        PropertyInfo info = props.get(propName);
        if (info != null && info != INVALIDATED && isMatchingType(target, info.color)) {
          Node replacement = info.value.cloneTree();
          if (compiler.getAstAnalyzer().mayHaveSideEffects(n.getFirstChild())) {
            replacement = IR.comma(n.removeFirstChild(), replacement).srcref(n);
          }
          n.replaceWith(replacement);
          compiler.reportChangeToEnclosingScope(replacement);
        }
      }
    }

    private boolean isMatchingType(Node n, Color src) {
      src = removeNullAndUndefinedIfUnion(src);
      Color dest = removeNullAndUndefinedIfUnion(getColor(n));
      if (dest.isInvalidating()) {
        return false;
      }
      if (dest.isUnion() || src.isUnion()) {
        return false;
      }
      return hasInSupertypesList(dest, src);
    }

    private boolean hasInSupertypesList(Color subCtor, Color superCtor) {
      try {
        if (!this.hasInSupertypesListSeenSet.add(subCtor)) {
          return false;
        }
        if (subCtor == null || superCtor == null) {
          return false;
        }
        if (subCtor.equals(superCtor)) {
          return true;
        }

        for (Color immediateSupertype : registry.getDisambiguationSupertypes(subCtor)) {
          if (!immediateSupertype.isUnion() && hasInSupertypesList(immediateSupertype, superCtor)) {
            return true;
          }
        }
        return false;
      } finally {
        this.hasInSupertypesListSeenSet.remove(subCtor);
      }
    }

    private final LinkedHashSet<Color> hasInSupertypesListSeenSet = new LinkedHashSet<>();
  }

  private static Color removeNullAndUndefinedIfUnion(Color original) {
    return original.isUnion() ? original.subtractNullOrVoid() : original;
  }
}
