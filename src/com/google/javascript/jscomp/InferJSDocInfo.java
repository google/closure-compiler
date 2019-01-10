/*
 * Copyright 2009 The Closure Compiler Authors.
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
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import javax.annotation.Nullable;

/**
 * Sets the {@link JSDocInfo} on all {@code JSType}s, including their properties, using the JSDoc on
 * the node defining that type or property.
 *
 * <p>This pass propagates JSDocs across the type graph, but not across the symbol graph. For
 * example:
 *
 * <pre>{@code
 * /**
 *  * I'm a user type!
 *  * @constructor
 *  *\/
 * var Foo = function() { };
 *
 * var Bar = Foo;
 * }</pre>
 *
 * will assign the "I'm a user type!" JSDoc from the `Foo` node to type "Foo" and type "ctor{Foo}".
 * However, this pass says nothing about JSDocs being propagated to the `Bar` node.
 *
 * <p>JSDoc is initially attached to AST Nodes at parse time. There are 3 cases where JSDocs get
 * attached to the type system, always from the associated declaration node:
 *
 * <ul>
 *   <li>Nominal types (e.g. constructors, interfaces, enums).
 *   <li>Object type properties (e.g. `Foo.prototype.bar`).
 *   <li>Anonymous structural types, including functions. (Some function types with the same
 *       signature are unique and therefore can have distinct JSDocs.) (b/111070482: it's unclear
 *       why we support this.)
 * </ul>
 *
 * <p>#1 is fairly straight-forward with the additional detail that JSDocs are propagated to both
 * the instance type <em>and</em> the declaration type (i.e. the ctor or enum type). #2 should also
 * be mostly self-explanatory; it covers scenarios like the following:
 *
 * <pre>{@code
 * /**
 *  * I'm a method!
 *  * @param {number} x
 *  * @return {number}
 *  *\/
 * Foo.prototype.bar = function(x) { ... };
 * }</pre>
 *
 * in which JSDocInfo will appear on the "bar" slot of `Foo.prototype` and `Foo`. The function type
 * used as the RHS of the assignments (i.e. `function(this:Foo, number): number`) is not considered.
 * Note that this example would work equally well if `bar` were declared abstract. #3 is a bit
 * trickier; it covers types such as the following declarations:
 *
 * <pre>{@code
 * /** I'm an anonymous structural object type! *\/
 * var myObject = {a: 5, b: 'Hello'};
 *
 * /**
 *  * I'm an anonymous structural function type!
 *  * @param {number} x
 *  * @return {string}
 *  *\/
 * var myFunction = function(x) { ... };
 *
 * }</pre>
 *
 * which define unique types with their own JSDoc attributes. Object literal or function types with
 * the same structure will get different JSDocs despite possibly comparing equal. Additionally, when
 * assigning instances of these types as properties of nominal types (e.g. using `myFunction` as the
 * RHS of #2) the structural type JSDoc plays no part.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class InferJSDocInfo extends AbstractPostOrderCallback implements HotSwapCompilerPass {
  private final AbstractCompiler compiler;

  InferJSDocInfo(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    if (externs != null) {
      NodeTraversal.traverse(compiler, externs, this);
    }
    if (root != null) {
      NodeTraversal.traverse(compiler, root, this);
    }
  }

  @Override
  public void hotSwapScript(Node root, Node originalRoot) {
    checkNotNull(root);
    checkState(root.isScript());
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
        // Infer JSDocInfo on types of all type declarations on variables.
      case NAME:
        {
          if (parent == null) {
            return;
          }

          // Only allow JSDoc on variable declarations, named functions, named classes, and assigns.
          final JSDocInfo typeDoc;
          final JSType inferredType;
          if (NodeUtil.isNameDeclaration(parent)) {
            // Case: `/** ... */ (var|let|const) x = function() { ... }`.
            // Case: `(var|let|const) /** ... */ x = function() { ... }`.
            JSDocInfo nameInfo = n.getJSDocInfo();
            typeDoc = (nameInfo != null) ? nameInfo : parent.getJSDocInfo();

            inferredType = n.getJSType();
          } else if (NodeUtil.isFunctionDeclaration(parent)
              || NodeUtil.isClassDeclaration(parent)) {
            // Case: `/** ... */ function f() { ... }`.
            // Case: `/** ... */ class Foo() { ... }`.
            typeDoc = parent.getJSDocInfo();
            inferredType = parent.getJSType();
          } else if (parent.isAssign() && n.isFirstChildOf(parent)) {
            // Case: `/** ... */ x = function () { ... }`
            typeDoc = parent.getJSDocInfo();
            inferredType = n.getJSType();
          } else {
            return;
          }

          if (typeDoc == null) {
            return;
          }

          // If we have no type, or the type already has a JSDocInfo, then we're done.
          ObjectType objType = dereferenced(inferredType);
          if (objType == null || objType.getJSDocInfo() != null) {
            return;
          }

          attachJSDocInfoToNominalTypeOrShape(objType, typeDoc, n.getString());
        }
        return;

      case STRING_KEY:
      case GETTER_DEF:
      case SETTER_DEF:
      case MEMBER_FUNCTION_DEF:
        {
          JSDocInfo typeDoc = n.getJSDocInfo();
          if (typeDoc == null) {
            return;
          }

          final ObjectType owningType;
          if (parent.isClassMembers()) {
            FunctionType ctorType = JSType.toMaybeFunctionType(parent.getParent().getJSType());
            if (ctorType == null) {
              return;
            }

            owningType = n.isStaticMember() ? ctorType : ctorType.getPrototype();
          } else {
            owningType = dereferenced(parent.getJSType());
          }
          if (owningType == null) {
            return;
          }

          String propName = n.getString();
          if (owningType.hasOwnProperty(propName)) {
            owningType.setPropertyJSDocInfo(propName, typeDoc);
          }
        }
        return;

      case GETPROP:
        {
          // Infer JSDocInfo on properties.
          // There are two ways to write doc comments on a property.
          final JSDocInfo typeDoc;
          if (parent.isAssign() && n.isFirstChildOf(parent)) {
            // Case: `/** @deprecated */ obj.prop = ...;`
            typeDoc = parent.getJSDocInfo();
          } else if (parent.isExprResult()) {
            // Case: `/** @deprecated */ obj.prop;`
            typeDoc = n.getJSDocInfo();
          } else {
            return;
          }

          if (typeDoc == null) {
            return;
          }

          ObjectType lhsType = dereferenced(n.getFirstChild().getJSType());
          if (lhsType == null) {
            return;
          }

          // Put the JSDoc in the property slot, if there is one.
          String propName = n.getLastChild().getString();
          if (lhsType.hasOwnProperty(propName)) {
            lhsType.setPropertyJSDocInfo(propName, typeDoc);
          }

          // Put the JSDoc in any constructors or function shapes as well.
          ObjectType propType = dereferenced(lhsType.getPropertyType(propName));
          if (propType != null) {
            attachJSDocInfoToNominalTypeOrShape(propType, typeDoc, n.getQualifiedName());
          }
        }
        return;

      default:
        return;
    }
  }

  /** Nullsafe wrapper for {@code JSType#dereference()}. */
  private static ObjectType dereferenced(@Nullable JSType type) {
    return type == null ? null : type.dereference();
  }

  /** Handle cases #1 and #3 in the class doc. */
  private static void attachJSDocInfoToNominalTypeOrShape(
      ObjectType objType, JSDocInfo docInfo, @Nullable String qName) {

    if (objType.isConstructor() || objType.isInterface()) {
      if (!isReferenceNameOf(objType, qName)) {
        return;
      }

      objType.setJSDocInfo(docInfo);
      JSType.toMaybeFunctionType(objType).getInstanceType().setJSDocInfo(docInfo);
    } else if (objType.isEnumType()) {
      // Given: `/** @enum {number} */ MyEnum = { FOO: 0 };`
      // Then: typeOf(MyEnum).referenceName() == "enum{MyEnum}"
      // Then: typeOf(MyEnum.FOO).referenceName() == "MyEnum"
      ObjectType elementType = objType.toMaybeEnumType().getElementsType();
      if (!isReferenceNameOf(elementType, qName)) {
        return;
      }

      objType.setJSDocInfo(docInfo);
      elementType.setJSDocInfo(docInfo);
    } else if (!objType.isNativeObjectType() && objType.isFunctionType()) {
      // Anonymous function types identified by their parameter and return types. Remember there can
      // be many unique but equal instances.
      objType.setJSDocInfo(docInfo);
    }
  }

  private static boolean isReferenceNameOf(ObjectType type, String name) {
    return type.hasReferenceName() && type.getReferenceName().equals(name);
  }
}

