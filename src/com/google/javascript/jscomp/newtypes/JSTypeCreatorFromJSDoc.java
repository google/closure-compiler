/*
 * Copyright 2013 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.newtypes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.newtypes.NominalType.RawNominalType;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleErrorReporter;
import com.google.javascript.rhino.Token;

import java.util.Map;
import java.util.Set;

/**
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public class JSTypeCreatorFromJSDoc {

  /** Exception for when unrecognized type names are encountered */
  public static class UnknownTypeException extends Exception {
    UnknownTypeException(String cause) {
      super(cause);
    }
  }

  private SimpleErrorReporter reporter = new SimpleErrorReporter();
  // Unknown type names indexed by JSDoc AST node at which they were found.
  private Map<Node, String> unknownTypeNames = Maps.newHashMap();

  public JSType getNodeTypeDeclaration(JSDocInfo jsdoc,
      RawNominalType ownerType, DeclaredTypeRegistry registry) {
    if (jsdoc == null) {
      return null;
    }
    return getTypeFromJSTypeExpression(
        jsdoc.getType(), ownerType, registry, null);
  }

  public Set<String> getWarnings() {
    Set<String> warnings = Sets.newHashSet();
    if (reporter.warnings() != null) {
      warnings.addAll(reporter.warnings());
    }
    return warnings;
  }

  public Map<Node, String> getUnknownTypesMap() {
    return unknownTypeNames;
  }

  private JSType getTypeFromJSTypeExpression(
      JSTypeExpression expr, RawNominalType ownerType,
      DeclaredTypeRegistry registry, ImmutableList<String> typeParameters) {
    if (expr == null) {
      return null;
    }
    JSType result = getTypeFromNode(expr.getRootNode(), ownerType, registry,
        typeParameters);
    return result;
  }

  // Very similar to JSTypeRegistry#createFromTypeNodesInternal
  // n is a jsdoc node, not an AST node; the same class (Node) is used for both
  @VisibleForTesting
  JSType getTypeFromNode(Node n, RawNominalType ownerType,
      DeclaredTypeRegistry registry, ImmutableList<String> typeParameters) {
    try {
      return getTypeFromNodeHelper(n, ownerType, registry, typeParameters);
    } catch (UnknownTypeException e) {
      return JSType.UNKNOWN;
    }
  }

  private JSType getTypeFromNodeHelper(
      Node n, RawNominalType ownerType, DeclaredTypeRegistry registry,
      ImmutableList<String> typeParameters)
      throws UnknownTypeException {
    Preconditions.checkNotNull(n);
    switch (n.getType()) {
      case Token.LC:
        return getRecordTypeHelper(n, ownerType, registry, typeParameters);
      case Token.EMPTY: // for function types that don't declare a return type
        return JSType.UNKNOWN;
      case Token.VOID:
        return JSType.UNDEFINED;
      case Token.LB:
        warn("The [] type syntax is no longer supported." +
             " Please use Array.<T> instead.", n);
        return JSType.UNKNOWN;
      case Token.STRING:
        return getNamedTypeHelper(n, ownerType, registry, typeParameters);
      case Token.PIPE: {
        JSType union = JSType.BOTTOM;
        for (Node child = n.getFirstChild(); child != null;
             child = child.getNext()) {
          union = JSType.join(union, getTypeFromNodeHelper(
              child, ownerType, registry, typeParameters));
        }
        return union;
      }
      case Token.BANG: {
        return getTypeFromNodeHelper(
            n.getFirstChild(), ownerType, registry, typeParameters)
            .removeType(JSType.NULL);
      }
      case Token.QMARK: {
        Node child = n.getFirstChild();
        if (child == null) {
          return JSType.UNKNOWN;
        } else {
          return JSType.join(JSType.NULL, getTypeFromNodeHelper(
              child, ownerType, registry, typeParameters));
        }
      }
      case Token.STAR:
        return JSType.TOP;
      case Token.FUNCTION:
        return getFunTypeHelper(n, ownerType, registry, typeParameters);
      default:
        throw new IllegalArgumentException("Unsupported type exp: " +
            Token.name(n.getType()) + " " + n.toStringTree());
    }
  }

  private JSType getRecordTypeHelper(Node n, RawNominalType ownerType,
      DeclaredTypeRegistry registry,
      ImmutableList<String> typeParameters)
      throws UnknownTypeException {
    Map<String, JSType> fields = Maps.newHashMap();
    // For each of the fields in the record type.
    for (Node fieldTypeNode = n.getFirstChild().getFirstChild();
         fieldTypeNode != null;
         fieldTypeNode = fieldTypeNode.getNext()) {
      boolean isFieldTypeDeclared = fieldTypeNode.getType() == Token.COLON;
      Node fieldNameNode = isFieldTypeDeclared ?
          fieldTypeNode.getFirstChild() : fieldTypeNode;
      String fieldName = fieldNameNode.getString();
      if (fieldName.startsWith("'") || fieldName.startsWith("\"")) {
        fieldName = fieldName.substring(1, fieldName.length() - 1);
      }
      JSType fieldType = !isFieldTypeDeclared ? JSType.UNKNOWN :
          getTypeFromNodeHelper(fieldTypeNode.getLastChild(), ownerType,
              registry, typeParameters);
      // TODO(blickly): Allow optional properties
      fields.put(fieldName, fieldType);
    }
    return JSType.fromObjectType(ObjectType.fromProperties(fields));
  }

  private static boolean isNonnullAndContains(
      ImmutableList<String> collection, String str) {
    return collection != null && collection.contains(str);
  }

  private static boolean hasTypeVariable(
      ImmutableList<String> typeParameters, RawNominalType ownerType,
      String typeName) {
    return isNonnullAndContains(typeParameters, typeName) ||
        ownerType != null &&
        isNonnullAndContains(ownerType.getTypeParameters(), typeName);
  }

  private JSType getNamedTypeHelper(Node n, RawNominalType ownerType,
      DeclaredTypeRegistry registry,
      ImmutableList<String> outerTypeParameters)
      throws UnknownTypeException {
    String typeName = n.getString();
    switch (typeName) {
      case "boolean":
        return JSType.BOOLEAN;
      case "null":
        return JSType.NULL;
      case "number":
        return JSType.NUMBER;
      case "string":
        return JSType.STRING;
      case "undefined":
      case "void":
        return JSType.UNDEFINED;
      case "Function":
        return JSType.join(JSType.qmarkFunction(), JSType.NULL);
      case "Object":
        return JSType.join(JSType.TOP_OBJECT, JSType.NULL);
      default: {
        if (hasTypeVariable(outerTypeParameters, ownerType, typeName)) {
          return JSType.fromTypeVar(typeName);
        } else {
          // It's either a typedef, a type variable or a nominal-type name
          if (registry.getTypedef(typeName) != null) {
            return getTypedefType(typeName, registry);
          }
          JSType namedType = registry.lookupTypeByName(typeName);
          if (namedType == null) {
            unknownTypeNames.put(n, typeName);
            throw new UnknownTypeException("Unhandled type: " + typeName);
          }
          if (namedType.isTypeVariable()) {
            return namedType;
          }
          return JSType.join(JSType.NULL, getNominalTypeHelper(
              namedType, n, ownerType, registry, outerTypeParameters));
        }
      }
    }
  }

  public JSType getTypedefType(String name, DeclaredTypeRegistry registry) {
    Typedef td = registry.getTypedef(name);
    Preconditions.checkState(td != null, "getTypedef should only be " +
        "called when we know that the typedef is defined");
    if (td.isResolved()) {
      return td.getType();
    }
    JSTypeExpression texp = td.getTypeExpr();
    JSType tdType;
    if (texp == null) {
      warn("Circular typedefs are not allowed.",
          td.getTypeExprForErrorReporting().getRootNode());
      tdType = JSType.UNKNOWN;
    } else {
      tdType = getTypeFromJSTypeExpression(texp, null, registry, null);
    }
    td.resolveTypedef(tdType);
    return tdType;
  }

  private JSType getNominalTypeHelper(JSType namedType, Node n,
      RawNominalType ownerType, DeclaredTypeRegistry registry,
      ImmutableList<String> outerTypeParameters)
      throws UnknownTypeException {
    NominalType uninstantiated = namedType.getNominalTypeIfUnique();
    RawNominalType rawType = uninstantiated.getRawNominalType();
    if (!rawType.isGeneric() && !n.hasChildren()) {
      return namedType;
    }
    if (!n.hasChildren()) {
      ImmutableList.Builder<JSType> typeList = ImmutableList.builder();
      for (String unused : rawType.getTypeParameters()) {
        typeList.add(JSType.UNKNOWN);
      }
      return JSType.fromObjectType(ObjectType.fromNominalType(
          uninstantiated.instantiateGenerics(typeList.build())));
    }
    // Compute instantiation of polymorphic class/interface.
    Preconditions.checkState(n.getFirstChild().isBlock());
    ImmutableList.Builder<JSType> typeList = ImmutableList.builder();
    for (Node child : n.getFirstChild().children()) {
      JSType childType = getTypeFromNodeHelper(
          child, ownerType, registry, outerTypeParameters);
      typeList.add(childType);
    }
    ImmutableList<JSType> typeArguments = typeList.build();
    ImmutableList<String> typeParameters = rawType.getTypeParameters();
    if (typeArguments.size() != typeParameters.size()) {
      warn("Invalid generics instantiation.\n" +
          "Expected " + typeParameters.size() + " type arguments, but " +
          typeArguments.size() + " were passed.", n);
      return JSType.fromObjectType(ObjectType.fromNominalType(
          uninstantiated.instantiateGenerics(JSType.fixLengthOfTypeList(
              typeParameters.size(), typeArguments))));
    }
    return JSType.fromObjectType(ObjectType.fromNominalType(
        uninstantiated.instantiateGenerics(typeArguments)));
  }

  // Don't confuse with getFunTypeFromAtTypeJsdoc; the function below computes a
  // type that doesn't have an associated AST node.
  private JSType getFunTypeHelper(Node n, RawNominalType ownerType,
      DeclaredTypeRegistry registry,
      ImmutableList<String> typeParameters)
      throws UnknownTypeException {
    return getFunTypeBuilder(n, ownerType, registry, typeParameters)
        .buildType();
  }

  private FunctionTypeBuilder getFunTypeBuilder(
      Node n, RawNominalType ownerType, DeclaredTypeRegistry registry,
      ImmutableList<String> typeParameters)
      throws UnknownTypeException {
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    Node child = n.getFirstChild();
    if (child.getType() == Token.THIS) {
      builder.addReceiverType(getNominalType(
          child.getFirstChild(), ownerType, registry, typeParameters));
      child = child.getNext();
    } else if (child.getType() == Token.NEW) {
      builder.addNominalType(getNominalType(
          child.getFirstChild(), ownerType, registry, typeParameters));
      child = child.getNext();
    }
    if (child.getType() == Token.PARAM_LIST) {
      for (Node arg = child.getFirstChild(); arg != null; arg = arg.getNext()) {
        try {
          switch (arg.getType()) {
            case Token.EQUALS:
              builder.addOptFormal(
                  getTypeFromNodeHelper(
                      arg.getFirstChild(), ownerType, registry,
                      typeParameters));
              break;
            case Token.ELLIPSIS:
              Node restNode = arg.getFirstChild();
              builder.addRestFormals(restNode == null ? JSType.UNKNOWN :
                  getTypeFromNodeHelper(
                      restNode, ownerType, registry, typeParameters));
              break;
            default:
              builder.addReqFormal(
                  getTypeFromNodeHelper(arg, ownerType, registry,
                    typeParameters));
              break;
          }
        } catch (FunctionTypeBuilder.WrongParameterOrderException e) {
          warn("Wrong parameter order: required parameters are first, " +
              "then optional, then varargs", n);
        }
      }
      child = child.getNext();
    }
    builder.addRetType(
        getTypeFromNodeHelper(child, ownerType, registry, typeParameters));
    return builder;
  }

  public boolean hasKnownType(
      Node n, RawNominalType ownerType, DeclaredTypeRegistry registry,
      ImmutableList<String> typeParameters) {
    try {
      getTypeFromNodeHelper(n, ownerType, registry, typeParameters);
    } catch (UnknownTypeException e) {
      return false;
    }
    return true;
  }

  public NominalType getNominalType(Node n, RawNominalType ownerType,
      DeclaredTypeRegistry registry, ImmutableList<String> typeParameters) {
    JSType wrappedClass =
        getTypeFromNode(n, ownerType, registry, typeParameters);
    if (wrappedClass == null) {
      return null;
    }
    return wrappedClass.getNominalTypeIfUnique();
  }

  public ImmutableSet<NominalType> getImplementedInterfaces(
      JSDocInfo jsdoc, RawNominalType ownerType, DeclaredTypeRegistry registry,
      ImmutableList<String> typeParameters) {
    return getInterfacesHelper(
        jsdoc, ownerType, registry, typeParameters, true);
  }

  public ImmutableSet<NominalType> getExtendedInterfaces(
      JSDocInfo jsdoc, RawNominalType ownerType, DeclaredTypeRegistry registry,
      ImmutableList<String> typeParameters) {
    return getInterfacesHelper(
        jsdoc, ownerType, registry, typeParameters, false);
  }

  private ImmutableSet<NominalType> getInterfacesHelper(
      JSDocInfo jsdoc, RawNominalType ownerType, DeclaredTypeRegistry registry,
      ImmutableList<String> typeParameters, boolean implementedIntfs) {
    ImmutableSet.Builder<NominalType> builder = ImmutableSet.builder();
    for (JSTypeExpression texp : (implementedIntfs ?
          jsdoc.getImplementedInterfaces() :
          jsdoc.getExtendedInterfaces())) {
      Node expRoot = texp.getRootNode();
      if (hasKnownType(expRoot, ownerType, registry, typeParameters)) {
        NominalType nt =
            getNominalType(expRoot, ownerType, registry, typeParameters);
        if (nt != null && nt.isInterface()) {
          builder.add(nt);
        } else {
          String errorMsg = implementedIntfs ?
              "Cannot implement non-interface" :
              "Cannot extend non-interface";
          warn(errorMsg, jsdoc.getAssociatedNode());
        }
      }
    }
    return builder.build();
  }

  private static boolean isQmarkFunction(Node jsdocNode) {
    if (jsdocNode.getType() == Token.BANG) {
      jsdocNode = jsdocNode.getFirstChild();
    }
    return jsdocNode.isString() && jsdocNode.getString().equals("Function");
  }

  /**
   * Consumes either a "classic" function jsdoc with @param, @return, etc,
   * or a jsdoc with @type{function ...} and finds the types of the formal
   * parameters and the return value. It returns a builder because the callers
   * of this function must separately handle @constructor, @interface, etc.
   */
  public FunctionTypeBuilder getFunctionType(
      JSDocInfo jsdoc, Node declNode, RawNominalType ownerType,
      DeclaredTypeRegistry registry) {
    try {
      if (jsdoc != null && jsdoc.getType() != null) {
        Node jsdocNode = jsdoc.getType().getRootNode();
        int tokenType = jsdocNode.getType();
        if (tokenType == Token.FUNCTION) {
          if (declNode.isFunction()) {
            return getFunTypeFromAtTypeJsdoc(
                jsdoc, declNode, ownerType, registry);
          } else {
            try {
              // TODO(blickly): Use typeParameters here
              return getFunTypeBuilder(jsdocNode, ownerType, registry, null);
            } catch (UnknownTypeException e) {
              return FunctionTypeBuilder.qmarkFunctionBuilder();
            }
          }
        } else if (isQmarkFunction(jsdocNode)) {
          return FunctionTypeBuilder.qmarkFunctionBuilder();
        } else {
          warn("The function is annotated with a non-function jsdoc. " +
              "Ignoring jsdoc.", declNode);
        }
      }
      return getFunTypeFromTypicalFunctionJsdoc(
          jsdoc, declNode, ownerType, registry, false);
    } catch (FunctionTypeBuilder.WrongParameterOrderException e) {
      warn("Wrong parameter order: required parameters are first, " +
          "then optional, then varargs. Ignoring jsdoc.", declNode);
      return getFunTypeFromTypicalFunctionJsdoc(
          null, declNode, ownerType, registry, true);
    }
  }

  private FunctionTypeBuilder getFunTypeFromAtTypeJsdoc(
      JSDocInfo jsdoc, Node funNode, RawNominalType ownerType,
      DeclaredTypeRegistry registry) {
    Preconditions.checkArgument(funNode.isFunction());
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    Node childJsdoc = jsdoc.getType().getRootNode().getFirstChild();
    Node param = funNode.getFirstChild().getNext().getFirstChild();
    Node paramType;
    boolean warnedForMissingTypes = false;
    boolean warnedForInlineJsdoc = false;

    if (childJsdoc.getType() == Token.THIS) {
      builder.addReceiverType(
          getNominalType(childJsdoc.getFirstChild(), ownerType, registry,
            null));
      childJsdoc = childJsdoc.getNext();
    } else if (childJsdoc.getType() == Token.NEW) {
      builder.addNominalType(
          getNominalType(childJsdoc.getFirstChild(), ownerType, registry,
            null));
      childJsdoc = childJsdoc.getNext();
    }
    if (childJsdoc.getType() == Token.PARAM_LIST) {
      paramType = childJsdoc.getFirstChild();
      childJsdoc = childJsdoc.getNext(); // go to the return type
    } else { // empty parameter list
      paramType = null;
    }

    while (param != null) {
      if (paramType == null) {
        if (!warnedForMissingTypes) {
          warn("The function has more formal parameters than the types " +
              "declared in the JSDoc", funNode);
          warnedForMissingTypes = true;
        }
        builder.addOptFormal(JSType.UNKNOWN);
      } else {
        if (!warnedForInlineJsdoc && param.getJSDocInfo() != null) {
          warn("The function cannot have both an @type jsdoc and inline " +
              "jsdocs. Ignoring inline jsdocs.", param);
          warnedForInlineJsdoc = true;
        }
        switch (paramType.getType()) {
          case Token.EQUALS:
            builder.addOptFormal(getTypeFromNode(
                    paramType.getFirstChild(), ownerType, registry, null));
            break;
          case Token.ELLIPSIS:
            if (!warnedForMissingTypes) {
              warn("The function has more formal parameters than the types " +
                  "declared in the JSDoc", funNode);
              warnedForMissingTypes = true;
              builder.addOptFormal(JSType.UNKNOWN);
            }
            break;
          default:
            builder.addReqFormal(
                getTypeFromNode(paramType, ownerType, registry, null));
            break;
        }
        paramType = paramType.getNext();
      }
      param = param.getNext();
    }

    if (paramType != null) {
      if (paramType.getType() == Token.ELLIPSIS) {
        builder.addRestFormals(getTypeFromNode(
                paramType.getFirstChild(), ownerType, registry, null));
      } else {
        warn("The function has fewer formal parameters than the types " +
            "declared in the JSDoc", funNode);
      }
    }
    if (!warnedForInlineJsdoc &&
        funNode.getFirstChild().getJSDocInfo() != null) {
      warn("The function cannot have both an @type jsdoc and inline " +
          "jsdocs. Ignoring the inline return jsdoc.", funNode);
    }
    if (jsdoc.getReturnType() != null) {
      warn("The function cannot have both an @type jsdoc and @return " +
          "jsdoc. Ignoring @return jsdoc.", funNode);
    }
    if (funNode.getParent().isSetterDef()) {
      if (childJsdoc != null) {
        warn("Cannot declare a return type on a setter", funNode);
      }
      builder.addRetType(JSType.UNDEFINED);
    } else {
      builder.addRetType(
          getTypeFromNode(childJsdoc, ownerType, registry, null));
    }

    return builder;
  }

  private FunctionTypeBuilder getFunTypeFromTypicalFunctionJsdoc(
      JSDocInfo jsdoc, Node funNode, RawNominalType ownerType,
      DeclaredTypeRegistry registry,
      boolean ignoreJsdoc /* for when the jsdoc is malformed */) {
    Preconditions.checkArgument(!ignoreJsdoc || jsdoc == null);
    if (!funNode.isFunction()) {
      // TODO(blickly): Support typical function jsdoc without initializer
      return FunctionTypeBuilder.qmarkFunctionBuilder();
    }
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    Node params = funNode.getFirstChild().getNext();
    ImmutableList<String> typeParameters = null;
    Node parent = funNode.getParent();

    // TODO(dimvar): need more @template warnings
    // - warn for multiple @template annotations
    // - warn for @template annotation w/out usage

    if (jsdoc != null) {
      typeParameters = jsdoc.getTemplateTypeNames();
      if (!typeParameters.isEmpty()) {
        if (parent.isSetterDef() || parent.isGetterDef()) {
          ignoreJsdoc = true;
          jsdoc = null;
          warn("@template can't be used with getters/setters", funNode);
        } else {
          builder.addTypeParameters(typeParameters);
        }
      }
    }
    for (Node param = params.getFirstChild();
         param != null;
         param = param.getNext()) {
      String pname = param.getString();
      JSType inlineParamType = ignoreJsdoc ? null :
          getNodeTypeDeclaration(param.getJSDocInfo(), ownerType, registry);
      boolean isRequired = true, isRestFormals = false;
      JSTypeExpression texp = jsdoc == null ?
          null : jsdoc.getParameterType(pname);
      Node jsdocNode = texp == null ? null : texp.getRootNode();
      if (jsdocNode != null && jsdocNode.getType() == Token.EQUALS) {
        isRequired = false;
        jsdocNode = jsdocNode.getFirstChild();
      } else if (jsdocNode != null && jsdocNode.getType() == Token.ELLIPSIS) {
        isRequired = false;
        isRestFormals = true;
        jsdocNode = jsdocNode.getFirstChild();
      }
      JSType fnParamType = null;
      if (jsdocNode != null) {
        fnParamType =
            getTypeFromNode(jsdocNode, ownerType, registry, typeParameters);
      }
      if (inlineParamType != null) {
        // TODO(dimvar): The support for inline optional parameters is currently
        // broken, so this is always a required parameter. See b/11481388. Fix.
        builder.addReqFormal(inlineParamType);
        if (fnParamType != null) {
          warn("Found two JsDoc comments for formal parameter " + pname, param);
        }
      } else if (isRequired) {
        builder.addReqFormal(fnParamType);
      } else if (isRestFormals) {
        builder.addRestFormals(fnParamType);
      } else {
        builder.addOptFormal(fnParamType);
      }
    }

    JSDocInfo inlineRetJsdoc = ignoreJsdoc ? null :
        funNode.getFirstChild().getJSDocInfo();
    JSTypeExpression retTypeExp = jsdoc == null ? null : jsdoc.getReturnType();
    if (parent.isSetterDef()) {
      // inline returns for setters are attached to the function body.
      // Consider fixing this.
      inlineRetJsdoc = ignoreJsdoc ? null :
          funNode.getLastChild().getJSDocInfo();
      if (retTypeExp != null || inlineRetJsdoc != null) {
        warn("Cannot declare a return type on a setter", funNode);
      }
      builder.addRetType(JSType.UNDEFINED);
    } else if (inlineRetJsdoc != null) {
      builder.addRetType(getNodeTypeDeclaration(
          inlineRetJsdoc, ownerType, registry));
      if (retTypeExp != null) {
        warn("Found two JsDoc comments for the return type", funNode);
      }
    } else {
      builder.addRetType(getTypeFromJSTypeExpression(
              retTypeExp, ownerType, registry, typeParameters));
    }

    return builder;
  }

  // /** @param {...?} var_args */ function f(var_args) { ... }
  // var_args shouldn't be used in the body of f
  public boolean isRestArg(JSDocInfo funJsdoc, String formalParamName) {
    if (funJsdoc == null) {
      return false;
    }
    JSTypeExpression texp = funJsdoc.getParameterType(formalParamName);
    Node jsdocNode = texp == null ? null : texp.getRootNode();
    return jsdocNode != null && jsdocNode.getType() == Token.ELLIPSIS;
  }

  void warn(String msg, Node faultyNode) {
    reporter.warning(msg, faultyNode.getSourceFileName(),
        faultyNode.getLineno(), faultyNode.getCharno());
  }

}
