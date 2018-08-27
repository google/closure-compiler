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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSType.Nullability;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * A code generator that outputs type annotations for functions and
 * constructors.
 */
class TypedCodeGenerator extends CodeGenerator {
  private final JSTypeRegistry registry;
  private final JSDocInfoPrinter jsDocInfoPrinter;

  TypedCodeGenerator(
      CodeConsumer consumer, CompilerOptions options, JSTypeRegistry registry) {
    super(consumer, options);
    checkNotNull(registry);
    this.registry = registry;
    this.jsDocInfoPrinter = new JSDocInfoPrinter(options.getUseOriginalNamesInOutput());
  }

  @Override
  protected void add(Node n, Context context) {
    Node parent = n.getParent();
    if (parent != null && (parent.isBlock() || parent.isScript())) {
      if (n.isFunction()) {
        add(getFunctionAnnotation(n));
      } else if (n.isExprResult()
          && n.getFirstChild().isAssign()) {
        Node assign = n.getFirstChild();
        if (NodeUtil.isNamespaceDecl(assign.getFirstChild())) {
          add(jsDocInfoPrinter.print(assign.getJSDocInfo()));
        } else {
          Node rhs = assign.getLastChild();
          add(getTypeAnnotation(rhs));
        }
      } else if (n.isVar()
          && n.getFirstFirstChild() != null) {
        if (NodeUtil.isNamespaceDecl(n.getFirstChild())) {
          add(jsDocInfoPrinter.print(n.getJSDocInfo()));
        } else {
          add(getTypeAnnotation(n.getFirstFirstChild()));
        }
      }
    }

    super.add(n, context);
  }

  private String getTypeAnnotation(Node node) {
    // Only add annotations for things with JSDoc, or function literals.
    JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(node);
    if (jsdoc == null && !node.isFunction()) {
      return "";
    }

    JSType type = node.getJSType();
    if (type == null) {
      return "";
    } else if (type.isFunctionType()) {
      return getFunctionAnnotation(node);
    } else if (type.isEnumType()) {
      return "/** @enum {"
          + type.toMaybeObjectType()
              .getEnumeratedTypeOfEnumObject()
              .toAnnotationString(Nullability.EXPLICIT)
          + "} */\n";
    } else if (!type.isUnknownType()
        && !type.isEmptyType()
        && !type.isVoidType()
        && !type.isFunctionPrototypeType()) {
      return "/** @type {" + node.getJSType().toAnnotationString(Nullability.EXPLICIT) + "} */\n";
    } else {
      return "";
    }
  }

  /**
   * @param fnNode A node for a function for which to generate a type annotation
   */
  private String getFunctionAnnotation(Node fnNode) {
    JSType type = fnNode.getJSType();
    checkState(fnNode.isFunction() || type.isFunctionType());

    if (type == null || type.isUnknownType()) {
      return "";
    }

    FunctionType funType = type.toMaybeFunctionType();
    if (type.equals(registry.getNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE))) {
      return "/** @type {!Function} */\n";
    }
    StringBuilder sb = new StringBuilder("/**\n");
    Node paramNode = null;
    // We need to use the child nodes of the function as the nodes for the
    // parameters of the function type do not have the real parameter names.
    // FUNCTION
    //   NAME
    //   PARAM_LIST
    //     NAME param1
    //     NAME param2
    if (fnNode != null && fnNode.isFunction()) {
      paramNode = NodeUtil.getFunctionParameters(fnNode).getFirstChild();
    }

    // Param types
    int minArity = funType.getMinArity();
    int maxArity = funType.getMaxArity();
    List<JSType> formals = ImmutableList.copyOf(funType.getParameterTypes());
    for (int i = 0; i < formals.size(); i++) {
      sb.append(" * ");
      appendAnnotation(sb, "param", getParameterJSDocType(formals, i, minArity, maxArity));
      String parameterName = getParameterJSDocName(paramNode, i);
      sb.append(" ").append(parameterName).append("\n");
      if (paramNode != null) {
        paramNode = paramNode.getNext();
      }
    }

    // Return type
    JSType retType = funType.getReturnType();
    if (retType != null
        && !retType.isEmptyType() // There is no annotation for the empty type.
        && !funType.isInterface() // Interfaces never return a value.
        && !(funType.isConstructor() && retType.isVoidType())) {
      sb.append(" * ");
      appendAnnotation(sb, "return", retType.toAnnotationString(Nullability.EXPLICIT));
      sb.append("\n");
    }

    if (funType.isConstructor()) {
      appendConstructorAnnotations(sb, funType);
    } else if (funType.isInterface()) {
      appendInterfaceAnnotations(sb, funType);
    } else {
      JSType thisType = funType.getTypeOfThis();
      if (thisType != null && !thisType.isUnknownType() && !thisType.isVoidType()) {
        if (fnNode == null || !thisType.equals(findMethodOwner(fnNode))) {
          sb.append(" * ");
          appendAnnotation(sb, "this", thisType.toAnnotationString(Nullability.EXPLICIT));
          sb.append("\n");
        }
      }
    }

    Collection<JSType> typeParams = funType.getTypeParameters();
    if (!typeParams.isEmpty()) {
      sb.append(" * @template ");
      Joiner.on(",").appendTo(sb, Iterables.transform(typeParams, new Function<JSType, String>() {
        @Override public String apply(JSType var) {
          return formatTypeVar(var);
        }
      }));
      sb.append("\n");
    }

    sb.append(" */\n");
    return sb.toString();
  }

  /**
   * Return the name of the parameter to be used in JSDoc, generating one for destructuring
   * parameters.
   *
   * @param paramNode child node of a parameter list
   * @param paramIndex position of child in the list
   * @return name to use in JSDoc
   */
  private String getParameterJSDocName(Node paramNode, int paramIndex) {
    Node nameNode = null;
    if (paramNode != null) {
      checkArgument(paramNode.getParent().isParamList(), paramNode);
      if (paramNode.isRest()) {
        // use `restParam` of `...restParam`
        // restParam might still be a destructuring pattern
        paramNode = paramNode.getOnlyChild();
      } else if (paramNode.isDefaultValue()) {
        // use `defaultParam` of `defaultParam = something`
        // defaultParam might still be a destructuring pattern
        paramNode = paramNode.getFirstChild();
      }
      if (paramNode.isName()) {
        nameNode = paramNode;
      } else {
        checkState(paramNode.isObjectPattern() || paramNode.isArrayPattern(), paramNode);
        nameNode = null; // must generate a fake name
      }
    }
    if (nameNode == null) {
      return "p" + paramIndex;
    } else {
      checkState(nameNode.isName(), nameNode);
      return nameNode.getString();
    }
  }

  private String formatTypeVar(JSType var) {
    return var.toAnnotationString(Nullability.IMPLICIT);
  }

  // TODO(dimvar): it's awkward that we print @constructor after the extends/implements;
  // we should print it first, like users write it. Same for @interface and @record.
  private void appendConstructorAnnotations(StringBuilder sb, FunctionType funType) {
    FunctionType superConstructor = funType.getInstanceType().getSuperClassConstructor();
    if (superConstructor != null) {
      ObjectType superInstance = superConstructor.getInstanceType();
      if (!superInstance.toString().equals("Object")) {
        sb.append(" * ");
        appendAnnotation(sb, "extends", superInstance.toAnnotationString(Nullability.IMPLICIT));
        sb.append("\n");
      }
    }
    // Avoid duplicates, add implemented type to a set first
    Set<String> interfaces = new TreeSet<>();
    for (ObjectType interfaze : funType.getAncestorInterfaces()) {
      interfaces.add(interfaze.toAnnotationString(Nullability.IMPLICIT));
    }
    for (String interfaze : interfaces) {
      sb.append(" * ");
      appendAnnotation(sb, "implements", interfaze);
      sb.append("\n");
    }
    sb.append(" * @constructor\n");
  }

  private void appendInterfaceAnnotations(StringBuilder sb, FunctionType funType) {
    Set<String> interfaces = new TreeSet<>();
    for (ObjectType interfaceType : funType.getAncestorInterfaces()) {
      interfaces.add(interfaceType.toAnnotationString(Nullability.IMPLICIT));
    }
    for (String interfaze : interfaces) {
      sb.append(" * ");
      appendAnnotation(sb, "extends", interfaze);
      sb.append("\n");
    }
    if (funType.isStructuralInterface()) {
      sb.append(" * @record\n");
    } else {
      sb.append(" * @interface\n");
    }
  }

  // TODO(sdh): This whole method could be deleted if we don't mind adding
  // additional @this annotations where they're not actually necessary.
  /**
   * Given a method definition node, returns the {@link ObjectType} corresponding
   * to the class the method is defined on, or null if it is not a prototype method.
   */
  private ObjectType findMethodOwner(Node n) {
    if (n == null) {
      return null;
    }
    Node parent = n.getParent();
    FunctionType ctor = null;
    if (parent.isAssign()) {
      Node target = parent.getFirstChild();
      if (NodeUtil.isPrototypeProperty(target)) {
        // TODO(johnlenz): handle non-global types
        JSType type = registry.getGlobalType(target.getFirstFirstChild().getQualifiedName());
        ctor = type != null ? ((ObjectType) type).getConstructor() : null;
      }
    } else if (parent.isClass()) {
      // TODO(sdh): test this case once the type checker understands ES6 classes
      ctor = parent.getJSType().toMaybeFunctionType();
    }
    return ctor != null ? ctor.getInstanceType() : null;
  }

  private static void appendAnnotation(StringBuilder sb, String name, String type) {
    sb.append("@").append(name).append(" {").append(type).append("}");
  }

  /** Creates a JSDoc-suitable String representation of the type of a parameter. */
  private String getParameterJSDocType(List<JSType> types, int index, int minArgs, int maxArgs) {
    JSType type = types.get(index);
    if (index < minArgs) {
      return type.toAnnotationString(Nullability.EXPLICIT);
    }
    boolean isRestArgument = maxArgs == Integer.MAX_VALUE && index == types.size() - 1;
    if (isRestArgument) {
      return "..." + restrictByUndefined(type).toAnnotationString(Nullability.EXPLICIT);
    }
    return restrictByUndefined(type).toAnnotationString(Nullability.EXPLICIT) + "=";
  }

  /** Removes undefined from a union type. */
  private JSType restrictByUndefined(JSType type) {
    // If not voidable, there's nothing to do. If not nullable then the easiest
    // thing is to simply remove both null and undefined. If nullable, then add
    // null back into the union after removing null and undefined.
    if (!type.isVoidable()) {
      return type;
    }
    JSType restricted = type.restrictByNotNullOrUndefined();
    if (type.isNullable()) {
      JSType nullType = registry.getNativeType(JSTypeNative.NULL_TYPE);
      return registry.createUnionType(ImmutableList.of(restricted, nullType));
    }
    // The bottom type cannot appear in a jsdoc
    return restricted.isEmptyType() ? type : restricted;
  }
}
