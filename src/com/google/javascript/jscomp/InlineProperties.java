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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.FunctionTypeI;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.ObjectTypeI;
import com.google.javascript.rhino.TypeI;
import com.google.javascript.rhino.TypeIRegistry;
import com.google.javascript.rhino.jstype.JSTypeNative;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
    PropertyInfo(TypeI type, Node value) {
      this.type = type;
      this.value = value;
    }
    final TypeI type;
    final Node value;
  }

  private static final PropertyInfo INVALIDATED = new PropertyInfo(null, null);

  private final Map<String, PropertyInfo> props = new HashMap<>();

  private final Set<TypeI> invalidatingTypes = new HashSet<>();

  InlineProperties(AbstractCompiler compiler) {
    this.compiler = compiler;
    buildInvalidatingTypeSet();
    invalidateExternProperties();
  }

  // TODO(johnlenz): this is a direct copy of the invalidation code
  // from AmbiguateProperties, if in the end we don't need to modify it
  // we should move it to a common location.
  private void buildInvalidatingTypeSet() {
    TypeIRegistry registry = compiler.getTypeIRegistry();
    invalidatingTypes.addAll(
        ImmutableList.of(
            registry.getNativeType(JSTypeNative.ALL_TYPE),
            registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
            registry.getNativeType(JSTypeNative.NO_TYPE),
            registry.getNativeType(JSTypeNative.NULL_TYPE),
            registry.getNativeType(JSTypeNative.VOID_TYPE),
            registry.getNativeType(JSTypeNative.FUNCTION_FUNCTION_TYPE),
            registry.getNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE),
            registry.getNativeType(JSTypeNative.FUNCTION_PROTOTYPE),
            registry.getNativeType(JSTypeNative.GLOBAL_THIS),
            registry.getNativeType(JSTypeNative.OBJECT_TYPE),
            registry.getNativeType(JSTypeNative.OBJECT_PROTOTYPE),
            registry.getNativeType(JSTypeNative.OBJECT_FUNCTION_TYPE),
            registry.getNativeType(JSTypeNative.TOP_LEVEL_PROTOTYPE),
            registry.getNativeType(JSTypeNative.UNKNOWN_TYPE)));

    for (TypeMismatch mis : compiler.getTypeMismatches()) {
      addInvalidatingType(mis.typeA);
      addInvalidatingType(mis.typeB);
    }
  }

  private void invalidateExternProperties() {
    // Invalidate properties defined in externs.
    for (String name : compiler.getExternProperties()) {
      props.put(name, INVALIDATED);
    }
  }

  /** Invalidates the given type, so that no properties on it will be inlined. */
  private void addInvalidatingType(TypeI type) {
    type = type.restrictByNotNullOrUndefined();
    if (type.isUnionType()) {
      for (TypeI alt : type.getUnionMembers()) {
        addInvalidatingType(alt);
      }
    }

    invalidatingTypes.add(type);
    ObjectTypeI objType = type.toMaybeObjectType();
    if (objType != null && objType.isInstanceType()) {
      invalidatingTypes.add(objType.getPrototypeObject());
    }
  }

  /** Returns true if properties on this type should not be inlined. */
  private boolean isInvalidatingType(TypeI type) {
    if (type.isUnionType()) {
      type = type.restrictByNotNullOrUndefined();
      if (type.isUnionType()) {
        for (TypeI alt : type.getUnionMembers()) {
          if (isInvalidatingType(alt)) {
            return true;
          }
        }
        return false;
      }
    }
    ObjectTypeI objType = type.toMaybeObjectType();
    return objType == null
        || invalidatingTypes.contains(objType)
        || objType.isUnknownObject()
        || objType.isUnknownType()
        || objType.isBottom()
        || objType.isEnumObject()
        || objType.isBoxableScalar()
        || !(type.isConstructor() || objType.isInstanceType());
  }

  /** This method gets the JSType from the Node argument and verifies that it is present. */
  private TypeI getTypeI(Node n) {
    TypeI type = n.getTypeI();
    if (type == null) {
      return compiler.getTypeIRegistry().getNativeType(JSTypeNative.UNKNOWN_TYPE);
    } else {
      return type;
    }
  }

  @Override
  public void process(Node externs, Node root) {
    // Find and replace the properties in non-extern AST.
    NodeTraversal.traverseEs6(compiler, root, new GatherCandidates());
    NodeTraversal.traverseEs6(compiler, root, new ReplaceCandidates());
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
        Preconditions.checkNotNull(propName);
        invalidateProperty(propName);
      }
    }

    /** @return Whether this is a valid definition for a candidate property. */
    private boolean isValidCandidateDefinition(NodeTraversal t, Node n, Node parent) {
      Preconditions.checkState(n.isGetProp() && parent.isAssign(), n);
      Node src = n.getFirstChild();
      String propName = n.getLastChild().getString();

      Node value = parent.getLastChild();
      if (src.isThis()) {
        // This is a simple assignment like:
        //    this.foo = 1;
        if (inConstructor(t)) {
          // This maybe a valid assignment.
          return maybeStoreCandidateValue(getTypeI(src), propName, value);
        }
      } else if (t.inGlobalHoistScope()
          && src.isGetProp()
          && src.getLastChild().getString().equals("prototype")) {
        // This is a prototype assignment like:
        //    x.prototype.foo = 1;
        TypeI instanceType = maybeGetInstanceTypeFromPrototypeRef(src);
        if (instanceType != null) {
          return maybeStoreCandidateValue(instanceType, propName, value);
        }
      } else if (t.inGlobalHoistScope()) {
        // This is a static assignment like:
        //    x.foo = 1;
        TypeI targetType = getTypeI(src);
        if (targetType != null && targetType.isConstructor()) {
          return maybeStoreCandidateValue(targetType, propName, value);
        }
      }
      return false;
    }

    private TypeI maybeGetInstanceTypeFromPrototypeRef(Node src) {
      TypeI ownerType = getTypeI(src.getFirstChild());
      if (ownerType.isConstructor()) {
        FunctionTypeI functionType = ownerType.toMaybeFunctionType();
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
    private boolean maybeStoreCandidateValue(TypeI type, String propName, Node value) {
      Preconditions.checkNotNull(value);
      if (!props.containsKey(propName)
          && !isInvalidatingType(type)
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

    private boolean isMatchingType(Node n, TypeI src) {
      src = src.restrictByNotNullOrUndefined();
      TypeI dest = getTypeI(n).restrictByNotNullOrUndefined();
      if (!isInvalidatingType(dest)) {
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
