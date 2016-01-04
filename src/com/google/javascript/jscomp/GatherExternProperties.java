/*
 * Copyright 2014 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.EnumElementType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.NamedType;
import com.google.javascript.rhino.jstype.NoType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.ProxyObjectType;
import com.google.javascript.rhino.jstype.TemplateType;
import com.google.javascript.rhino.jstype.TemplatizedType;
import com.google.javascript.rhino.jstype.UnionType;
import com.google.javascript.rhino.jstype.Visitor;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Gathers property names defined in externs.
 */
class GatherExternProperties extends AbstractPostOrderCallback
    implements CompilerPass {
  private final Set<String> externProperties = new LinkedHashSet<>();
  private final AbstractCompiler compiler;
  private final ExtractRecordTypePropertyNames typeVisitor =
      new ExtractRecordTypePropertyNames();

  public GatherExternProperties(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, externs, this);
    compiler.setExternProperties(ImmutableSet.copyOf(externProperties));
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.GETPROP:
        // Gathers "name" from (someObject.name).
        Node dest = n.getSecondChild();
        if (dest.isString()) {
          externProperties.add(dest.getString());
        }
        break;
      case Token.OBJECTLIT:
        // Gathers "name" and "address" from ({name: null, address: null}).
        for (Node child = n.getFirstChild();
             child != null;
             child = child.getNext()) {
          externProperties.add(child.getString());
        }
        break;
    }

    // Gather field names from the type of the node (if any).
    JSType type = n.getJSType();
    if (type != null) {
      typeVisitor.visitOnce(type);
    }

    // Gather field names from the @typedef declaration.
    // Typedefs are declared on qualified name nodes.
    if (n.isQualifiedName()) {
      // Get the JSDoc for the current node and check if it contains a
      // typedef.
      JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(n);
      if (jsDoc != null && jsDoc.hasTypedefType()) {
        // Get the corresponding type by looking at the type registry.
        JSType typedefType = compiler.getTypeIRegistry().getType(n.getQualifiedName());
        if (typedefType != null) {
          typeVisitor.visitOnce(typedefType);
        }
      }
    }
  }

  private class ExtractRecordTypePropertyNames
      implements Visitor<Set<String>> {
    private final Set<JSType> seenTypes = Sets.newIdentityHashSet();

    public void visitOnce(JSType type) {
      // Handle recursive types by only ever visiting the same type once.
      if (seenTypes.add(type)) {
        type.visit(this);
      }
    }

    // Interesting cases first, no-ops later.

    @Override
    public Set<String> caseEnumElementType(EnumElementType type) {
      // Descend into the enum's element type.
      // @enum {T}
      visitOnce(type.getPrimitiveType());

      return externProperties;
    }

    @Override
    public Set<String> caseFunctionType(FunctionType type) {
      // Visit parameter types.
      // function(T1, T2), as well as @param {T}
      for (Node param : type.getParameters()) {
        visitOnce(param.getJSType());
      }

      // Visit the return type.
      // function(): T, as well as @return {T}
      visitOnce(type.getReturnType());

      // @interface
      if (type.isInterface()) {
        // Visit the extended interfaces.
        // @extends {T}
        for (JSType extendedType : type.getExtendedInterfaces()) {
          visitOnce(extendedType);
        }
      }

      // @constructor
      if (type.isConstructor()) {
        // Visit the implemented interfaces.
        // @implements {T}
        for (JSType implementedType : type.getOwnImplementedInterfaces()) {
          visitOnce(implementedType);
        }

        // Visit the parent class (if any).
        // @extends {T}
        JSType superClass = type.getPrototype().getImplicitPrototype();
        if (superClass != null) {
          visitOnce(superClass);
        }
      }
      return externProperties;
    }

    @Override
    public Set<String> caseObjectType(ObjectType type) {
      // Record types.
      // {a: T1, b: T2}.
      if (type.isRecordType()) {
        for (String propertyName : type.getOwnPropertyNames()) {
          // After type inference it is possible that some nodes in externs
          // can have types which are defined in non-extern code. To avoid
          // bleeding property names of such types into externs we check that
          // the node for each property was defined in externs.
          if (type.getPropertyNode(propertyName).isFromExterns()) {
            externProperties.add(propertyName);
            visitOnce(type.getPropertyType(propertyName));
          }
        }
      }

      return externProperties;
    }

    @Override
    public Set<String> caseNamedType(NamedType type) {
      // Treat as all other proxy objects.
      return caseProxyObjectType(type);
    }

    @Override
    public Set<String> caseProxyObjectType(ProxyObjectType type) {
      // Visit the proxied type.
      // @typedef {T}
      type.visitReferenceType(this);

      return externProperties;
    }

    @Override
    public Set<String> caseUnionType(UnionType type) {
      // Visit the alternatives.
      // T1|T2|T3
      for (JSType alternateType : type.getAlternates()) {
        visitOnce(alternateType);
      }
      return externProperties;
    }

    @Override
    public Set<String> caseTemplatizedType(TemplatizedType type) {
      // Visit the type arguments.
      // SomeType.<T1, T2>
      for (JSType templateType : type.getTemplateTypes()) {
        visitOnce(templateType);
      }
      return externProperties;
    }

    @Override
    public Set<String> caseNoType(NoType type) {
      return externProperties;
    }

    @Override
    public Set<String> caseAllType() {
      return externProperties;
    }

    @Override
    public Set<String> caseBooleanType() {
      return externProperties;
    }

    @Override
    public Set<String> caseNoObjectType() {
      return externProperties;
    }

    @Override
    public Set<String> caseUnknownType() {
      return externProperties;
    }

    @Override
    public Set<String> caseNullType() {
      return externProperties;
    }

    @Override
    public Set<String> caseNumberType() {
      return externProperties;
    }

    @Override
    public Set<String> caseStringType() {
      return externProperties;
    }

    @Override
    public Set<String> caseVoidType() {
      return externProperties;
    }

    @Override
    public Set<String> caseTemplateType(TemplateType templateType) {
      return externProperties;
    }
  }
}
