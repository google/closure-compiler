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
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.TypeValidator.TypeMismatch;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;

import java.util.Map;
import java.util.Set;

/**
 * InlineProperties attempts to find references to properties that are known to
 * be constants and inline the known value.
 *
 * This pass relies on type information to find these property references and
 * properties are assumed to be constant if either:
 *   - the property is assigned unconditionally in the instance constructor
 *   - the property is assigned unconditionally to the type's prototype
 *
 * The current implementation only inlines immutable values (as defined by
 * NodeUtil.isImmutableValue).
 *
 * @author johnlenz@google.com (John Lenz)
 */
public class InlineProperties implements CompilerPass {

  private final AbstractCompiler compiler;

  static class PropertyInfo {
    PropertyInfo(JSType type, Node value) {
      this.type = type;
      this.value = value;
    }
    final JSType type;
    final Node value;
  }

  private static final PropertyInfo INVALIDATED = new PropertyInfo(
      null, null);

  private final Map<String, PropertyInfo> props = Maps.newHashMap();

  private Set<JSType> invalidatingTypes;

  InlineProperties(AbstractCompiler compiler) {
    this.compiler = compiler;
    buildInvalidatingTypeSet();
    invalidateExternProperties();
  }

  // TODO(johnlenz): this is a direct copy of the invalidation code
  // from AmbiguateProperties, if in the end we don't need to modify it
  // we should move it to a common location.
  private void buildInvalidatingTypeSet() {
    JSTypeRegistry registry = compiler.getTypeRegistry();
    invalidatingTypes = Sets.newHashSet(
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
        registry.getNativeType(JSTypeNative.UNKNOWN_TYPE));

    for (TypeMismatch mis : compiler.getTypeValidator().getMismatches()) {
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

  /**
   * Invalidates the given type, so that no properties on it will be renamed.
   */
  private void addInvalidatingType(JSType type) {
    type = type.restrictByNotNullOrUndefined();
    if (type.isUnionType()) {
      for (JSType alt : type.toMaybeUnionType().getAlternates()) {
        addInvalidatingType(alt);
      }
    }

    invalidatingTypes.add(type);
    ObjectType objType = ObjectType.cast(type);
    if (objType != null && objType.isInstanceType()) {
      invalidatingTypes.add(objType.getImplicitPrototype());
    }
  }

  /** Returns true if properties on this type should not be renamed. */
  private boolean isInvalidatingType(JSType type) {
    if (type.isUnionType()) {
      type = type.restrictByNotNullOrUndefined();
      if (type.isUnionType()) {
        for (JSType alt : type.toMaybeUnionType().getAlternates()) {
          if (isInvalidatingType(alt)) {
            return true;
          }
        }
        return false;
      }
    }
    ObjectType objType = ObjectType.cast(type);
    return objType == null
        || invalidatingTypes.contains(objType)
        || !objType.hasReferenceName()
        || objType.isUnknownType()
        || objType.isEmptyType() /* unresolved types */
        || objType.isEnumType()
        || objType.autoboxesTo() != null;
  }

  /**
   * This method gets the JSType from the Node argument and verifies that it is
   * present.
   */
  private JSType getJSType(Node n) {
    JSType jsType = n.getJSType();
    if (jsType == null) {
      return compiler.getTypeRegistry().getNativeType(
          JSTypeNative.UNKNOWN_TYPE);
    } else {
      return jsType;
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
          invalidatingPropRef = !maybeCandidateDefinition(t, n, parent);
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
        invalidatingPropRef = true;
      }

      if (invalidatingPropRef) {
        Preconditions.checkNotNull(propName);
        invalidateProperty(propName);
      }
    }

    /**
     * @return Whether this is a valid definition for a candidate property.
     */
    private boolean maybeCandidateDefinition(
        NodeTraversal t, Node n, Node parent) {
      Preconditions.checkState(n.isGetProp() && parent.isAssign());
      boolean isCandidate = false;
      Node src = n.getFirstChild();
      String propName = n.getLastChild().getString();

      Node value = parent.getLastChild();
      if (src.isThis()) {
        // This is a simple assignment like:
        //    this.foo = 1;
        if (inConstructor(t)) {
          // This maybe a valid assignment.
          isCandidate = maybeStoreCandidateValue(
              getJSType(src), propName, value);
        }
      } else if (t.inGlobalScope()
          && src.isGetProp()
          && src.getLastChild().getString().equals("prototype")) {
        // This is a prototype assignment like:
        //    x.prototype.foo = 1;
        JSType instanceType = maybeGetInstanceTypeFromPrototypeRef(src);
        if (instanceType != null) {
          isCandidate = maybeStoreCandidateValue(
              instanceType, propName, value);
        }
      }
      return isCandidate;
    }

    private JSType maybeGetInstanceTypeFromPrototypeRef(Node src) {
      JSType ownerType = getJSType(src.getFirstChild());
      if (ownerType.isFunctionType() && ownerType.isConstructor()) {
        FunctionType functionType = ((FunctionType) ownerType);
        return functionType.getInstanceType();
      }
      return null;
    }

    private void invalidateProperty(String propName) {
      props.put(propName, INVALIDATED);
    }

    private boolean maybeStoreCandidateValue(
        JSType type, String propName, Node value) {
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
      Node root = t.getScopeRoot();
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
          compiler.reportCodeChange();
        }
      }
    }

    private boolean isMatchingType(Node n, JSType src) {
      src = src.restrictByNotNullOrUndefined();
      JSType dest = getJSType(n).restrictByNotNullOrUndefined();
      return !isInvalidatingType(dest) && dest.isSubtype(src);
    }
  }
}
