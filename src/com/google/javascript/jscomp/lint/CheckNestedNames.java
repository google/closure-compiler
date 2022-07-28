/*
 * Copyright 2020 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.lint;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import org.jspecify.nullness.Nullable;

/**
 * Checks if code has a module-level static property assignment (`X.Y`) inside a `goog.module`. If
 * yes, reports a linter warning on the `X.Y` if:
 *
 * <ol>
 *   <li>X is any name {object, class, function, interface, enum, typedef}.
 *   <li>Y is {class, interface, enum, typedef}
 * </ol>
 *
 * <p>For example, reports a linter finding in the following:
 *
 * <pre>{ @code
 *
 * class C {}
 * let obj = {...};
 *
 * /**
 * * Reports nested name E on class C.
 * * @enum {number}
 * * /
 * C.E={...}; // WARNING:
 *
 * /**
 * * Reports nested interface I on variable obj.
 * * @interface
 * * /
 * obj.I=class {}; // WARNING:
 *
 * // reports nested name D on class C.
 * C.D=class {};
 * }
 * </pre>
 *
 * <pre>{@code
 * // Does not report on nested functions, or any nested property assigned to a value.
 * C.F = function() {}
 * obj.S = '';
 * obj.Some = new Something();
 * }</pre>
 */
public final class CheckNestedNames implements CompilerPass, NodeTraversal.Callback {

  public static final DiagnosticType NESTED_NAME_IN_GOOG_MODULE =
      DiagnosticType.disabled(
          "JSC_NESTED_NAME_IN_GOOG_MODULE",
          "A nested {0} is created on the name `{1}`."
              + " Fix this linter finding by converting the module-level static property"
              + " assignment on `{1}` into a module-level flat name (i.e. change `{1}.prop = ...`"
              + " into `{1}_prop = ...`. You can (if required) export this flat name using named"
              + " exports (`exports.{1}_prop = {1}_prop`)."
          );

  private final AbstractCompiler compiler;

  private enum DeclarationKind {
    CLASS,
    ENUM,
    TYPEDEF,
    INTERFACE,
  }

  private static final ImmutableMap<DeclarationKind, String> NESTED_KIND_TO_REPORT =
      ImmutableMap.of(
          DeclarationKind.CLASS, "class",
          DeclarationKind.ENUM, "enum",
          DeclarationKind.INTERFACE, "interface",
          DeclarationKind.TYPEDEF, "typedef");

  public CheckNestedNames(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public final boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    if (n.isScript()) {
      // only traverse inside goog.modules
      return n.getBooleanProp(Node.GOOG_MODULE);
    }
    // only warn on static property assignments of module-level names
    return nodeTraversal.inGlobalOrModuleScope();
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    checkArgument(t.inGlobalOrModuleScope());
    if (!n.isGetProp() || !n.isQualifiedName()) {
      return;
    }
    Node targetGetProp = getTargetGetProp(n);
    if ((parent.isAssign() || parent.isExprResult())
        && n.isFirstChildOf(parent)
        && !isDotPrototype(targetGetProp)) {
      Node owner = targetGetProp.getFirstChild();
      if (!owner.isName() || owner.getString().equals("exports")) {
        return; //  For example `this` or `super` or `exports.SomeEnum = {}`
      }
      String ownerName = owner.getString();
      DeclarationKind declarationKind = getNestedDeclarationKind(n, parent);
      if (NESTED_KIND_TO_REPORT.containsKey(declarationKind)) {
        // Found a nested name that created an inner class, interface, typedef or enum.
        String nestedKind = NESTED_KIND_TO_REPORT.get(declarationKind);
        t.report(targetGetProp, NESTED_NAME_IN_GOOG_MODULE, nestedKind, ownerName);
      }
    }
  }

  /** Gives `a.b` from `a.b.c.d...`. */
  private static Node getTargetGetProp(Node n) {
    checkArgument(n.isGetProp(), n);
    if (n.getFirstChild().isGetProp()) {
      return getTargetGetProp(n.getFirstChild());
    }
    return n;
  }

  /** True for `someExpression.prototype`. */
  private static boolean isDotPrototype(Node getProp) {
    return getProp.isGetProp() && getProp.getString().equals("prototype");
  }

  private @Nullable DeclarationKind getNestedDeclarationKind(Node lhs, Node parent) {
    checkArgument(lhs.isFirstChildOf(parent), lhs);
    // Handle `/** @typedef {...} */` X.Y` or `/** @const */ X.Y = SomeTypeDefName`
    if (lhs.getTypedefTypeProp() != null) {
      return DeclarationKind.TYPEDEF;
    }

    JSType type = lhs.getJSType();
    if (type != null) {
      // check whether it's an enum, interface or typedef first.
      if (type.isEnumType()) {
        return DeclarationKind.ENUM;
      } else if (type.isInterface()) {
        return DeclarationKind.INTERFACE;
      } else if (type.isConstructor()) {
        return DeclarationKind.CLASS;
      }
    }

    return null;
  }
}
