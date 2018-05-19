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

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import java.util.HashMap;
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
 *
 * @author johnlenz@google.com (John Lenz)
 */
final class InlineProperties implements CompilerPass {

  private final AbstractCompiler compiler;

  private static class PropertyInfo {
    PropertyInfo(JSType type, Node value) {
      this.type = type;
      this.value = value;
    }
    final JSType type;
    final Node value;
  }

  private static final PropertyInfo INVALIDATED = new PropertyInfo(null, null);

  private final Map<String, PropertyInfo> props = new HashMap<>();

  private final InvalidatingTypes invalidatingTypes;

  InlineProperties(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.invalidatingTypes = new InvalidatingTypes.Builder(compiler.getTypeRegistry())
        // TODO(sdh): consider allowing inlining properties of global this
        // (we already reserve extern'd names, so this should be safe).
        .disallowGlobalThis()
        .addTypesInvalidForPropertyRenaming()
        // NOTE: Mismatches are less important to this pass than to (dis)ambiguate properties.
        // This pass doesn't remove values (it only inlines them when the type is known), so
        // it isn't necessary to invalidate due to implicit interface uses.
        .addAllTypeMismatches(compiler.getTypeMismatches())
        .build();
    invalidateExternProperties();
  }

  private void invalidateExternProperties() {
    // Invalidate properties defined in externs.
    for (String name : compiler.getExternProperties()) {
      props.put(name, INVALIDATED);
    }
  }

  /** This method gets the JSType from the Node argument and verifies that it is present. */
  private JSType getJSType(Node n) {
    JSType type = n.getJSType();
    if (type == null) {
      return compiler.getTypeRegistry().getNativeType(JSTypeNative.UNKNOWN_TYPE);
    } else {
      return type;
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
      boolean invalidatingPropRef = false;
      String propName = null;
      if (n.isGetProp()) {
        propName = n.getLastChild().getString();
        if (parent.isAssign()) {
          invalidatingPropRef = !isValidCandidateDefinition(t, n, parent);
        } else if (NodeUtil.isLValue(n)) {
          // Other LValue references invalidate
          invalidatingPropRef = true;
        } else if (parent.isDelProp()) {
          // Deletes invalidate
          invalidatingPropRef = true;
        } else {
          // A property read doesn't invalidate
          invalidatingPropRef = false;
        }
      } else if (n.isStringKey()) {
        propName = n.getString();
        // For now, any object literal key invalidates
        // TODO(johnlenz): support prototype properties like:
        //   foo.prototype = { a: 1, b: 2 };
        // TODO(johnlenz): Object.create(), Object.createProperty
        invalidatingPropRef = true;
      }

      if (invalidatingPropRef) {
        checkNotNull(propName);
        invalidateProperty(propName);
      }
    }

    /** @return Whether this is a valid definition for a candidate property. */
    private boolean isValidCandidateDefinition(NodeTraversal t, Node n, Node parent) {
      checkState(n.isGetProp() && parent.isAssign(), n);
      Node src = n.getFirstChild();
      String propName = n.getLastChild().getString();

      Node value = parent.getLastChild();
      if (src.isThis()) {
        // This is a simple assignment like:
        //    this.foo = 1;
        if (inConstructor(t)) {
          // This maybe a valid assignment.
          return maybeStoreCandidateValue(getJSType(src), propName, value);
        }
      } else if (t.inGlobalHoistScope()
          && src.isGetProp()
          && src.getLastChild().getString().equals("prototype")) {
        // This is a prototype assignment like:
        //    x.prototype.foo = 1;
        JSType instanceType = maybeGetInstanceTypeFromPrototypeRef(src);
        if (instanceType != null) {
          return maybeStoreCandidateValue(instanceType, propName, value);
        }
      } else if (t.inGlobalHoistScope()) {
        // This is a static assignment like:
        //    x.foo = 1;
        JSType targetType = getJSType(src);
        if (targetType != null && targetType.isConstructor()) {
          return maybeStoreCandidateValue(targetType, propName, value);
        }
      }
      return false;
    }

    private JSType maybeGetInstanceTypeFromPrototypeRef(Node src) {
      JSType ownerType = getJSType(src.getFirstChild());
      if (ownerType.isConstructor()) {
        FunctionType functionType = ownerType.toMaybeFunctionType();
        return functionType.getInstanceType();
      }
      return null;
    }

    private void invalidateProperty(String propName) {
      props.put(propName, INVALIDATED);
    }

    /**
     * Adds the candidate property to the map if it meets all constness and immutability criteria,
     * and is not already present in the map. If the property was already present, it is
     * invalidated. Returns true if the property was successfully added.
     */
    private boolean maybeStoreCandidateValue(JSType type, String propName, Node value) {
      checkNotNull(value);
      if (!props.containsKey(propName)
          && !invalidatingTypes.isInvalidating(type)
          && NodeUtil.isImmutableValue(value)
          && NodeUtil.isExecutedExactlyOnce(value)) {
        props.put(propName, new PropertyInfo(type, value));
        return true;
      }
      return false;
    }

    private boolean inConstructor(NodeTraversal t) {
      Node root = t.getEnclosingFunction();
      if (root == null) {
        return false;
      }
      JSDocInfo info = NodeUtil.getBestJSDocInfo(root);
      return info != null && info.isConstructor();
    }
  }

  class ReplaceCandidates extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isGetProp() && !NodeUtil.isLValue(n)) {
        Node target = n.getFirstChild();
        String propName = n.getLastChild().getString();
        PropertyInfo info = props.get(propName);
        if (info != null
            && info != INVALIDATED
            && isMatchingType(target, info.type)) {
          Node replacement = info.value.cloneTree();
          if (NodeUtil.mayHaveSideEffects(n.getFirstChild(), compiler)) {
            replacement = IR.comma(n.removeFirstChild(), replacement).srcref(n);
          }
          parent.replaceChild(n, replacement);
          compiler.reportChangeToEnclosingScope(replacement);
        }
      }
    }

    private boolean isMatchingType(Node n, JSType src) {
      src = src.restrictByNotNullOrUndefined();
      JSType dest = getJSType(n).restrictByNotNullOrUndefined();
      if (!invalidatingTypes.isInvalidating(dest)) {
        if (dest.isConstructor() || src.isConstructor()) {
          // Don't inline constructor properties referenced from
          // subclass constructor references. This would be appropriate
          // for ES6 class with Class-side inheritence but not
          // traditional Closure classes from which subclass constructor
          // don't inherit the super-classes constructor properties.
          return dest.equals(src);
        } else {
          return dest.isSubtypeOf(src);
        }
      }
      return false;
    }
  }
}
