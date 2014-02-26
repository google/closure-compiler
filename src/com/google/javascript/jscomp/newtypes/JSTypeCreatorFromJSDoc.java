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

  public JSType getNodeTypeDeclaration(
      JSDocInfo jsdoc, RawNominalType ownerType, DeclaredTypeRegistry registry) {
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
      Preconditions.checkState(fieldTypeNode.getType() == Token.COLON);
      Node fieldNameNode = fieldTypeNode.getFirstChild();
      String fieldName = fieldNameNode.getString();
      if (fieldName.startsWith("'") || fieldName.startsWith("\"")) {
        fieldName = fieldName.substring(1, fieldName.length() - 1);
      }
      JSType fieldType = getTypeFromNodeHelper(
          fieldTypeNode.getLastChild(), ownerType, registry, typeParameters);
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
        isNonnullAndContains(ownerType.getTemplateVars(), typeName);
  }

  private JSType getNamedTypeHelper(Node n, RawNominalType ownerType,
      DeclaredTypeRegistry registry,
      ImmutableList<String> outerTypeParameters)
      throws UnknownTypeException {
    String typeName = n.getString();
    if (typeName.equals("boolean")) {
      return JSType.BOOLEAN;
    } else if (typeName.equals("null")) {
      return JSType.NULL;
    } else if (typeName.equals("number")) {
      return JSType.NUMBER;
    } else if (typeName.equals("string")) {
      return JSType.STRING;
    } else if (typeName.equals("undefined")) {
      return JSType.UNDEFINED;
    } else if (hasTypeVariable(outerTypeParameters, ownerType, typeName)) {
      return JSType.fromTypeVar(typeName);
    } else { // it must be a class name
      JSType namedType = registry.lookupTypeByName(typeName);
      if (namedType == null) {
        unknownTypeNames.put(n, typeName);
        throw new UnknownTypeException("Unhandled type: " + typeName);
      }
      if (!n.hasChildren()) {
        return namedType;
      }
      // Compute instantiation of polymorphic class/interface.
      Preconditions.checkState(n.getFirstChild().isBlock());
      ImmutableList.Builder<JSType> typeList = ImmutableList.builder();
      for (Node child : n.getFirstChild().children()) {
        JSType childType = getTypeFromNodeHelper(
                child, ownerType, registry, outerTypeParameters);
        typeList.add(childType);
      }
      NominalType uninstantiated = namedType.getNominalTypeIfUnique();
      ImmutableList<JSType> typeArguments = typeList.build();
      ImmutableList<String> typeParameters =
          uninstantiated.getRawNominalType().getTemplateVars();
      if (typeArguments.size() != typeParameters.size()) {
        warn("Invalid generics instantiation.\n" +
            "Expected " + typeParameters.size() + " type arguments, but " +
            typeArguments.size() + " were passed.", n);
        return JSType.fromObjectType(ObjectType.fromNominalType(
            uninstantiated));
      }
      return JSType.fromObjectType(ObjectType.fromNominalType(
          uninstantiated.instantiateGenerics(typeArguments)));
    }
  }

  // Don't confuse with getFunTypeFromAtTypeJsdoc; the function below computes a
  // type that doesn't have an associated AST node.
  private JSType getFunTypeHelper(Node n, RawNominalType ownerType,
      DeclaredTypeRegistry registry,
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
              builder.addRestFormals(
                  getTypeFromNodeHelper(
                      arg.getFirstChild(), ownerType, registry,
                      typeParameters));
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
    return builder.buildType();
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

  public ImmutableList<NominalType> getImplementedInterfaces(
      JSDocInfo jsdoc, RawNominalType ownerType, DeclaredTypeRegistry registry,
      ImmutableList<String> typeParameters) {
    ImmutableList.Builder<NominalType> builder = ImmutableList.builder();
    for (JSTypeExpression texp: jsdoc.getImplementedInterfaces()) {
      Node expRoot = texp.getRootNode();
      if (hasKnownType(expRoot, ownerType, registry, typeParameters)) {
        builder.add(
            getNominalType(expRoot, ownerType, registry, typeParameters));
      }
    }
    return builder.build();
  }

  public ImmutableList<NominalType> getExtendedInterfaces(
      JSDocInfo jsdoc, RawNominalType ownerType, DeclaredTypeRegistry registry,
      ImmutableList<String> typeParameters) {
    ImmutableList.Builder<NominalType> builder = ImmutableList.builder();
    for (JSTypeExpression texp: jsdoc.getExtendedInterfaces()) {
      Node expRoot = texp.getRootNode();
      if (hasKnownType(expRoot, ownerType, registry, typeParameters)) {
        builder.add(
            getNominalType(expRoot, ownerType, registry, typeParameters));
      }
    }
    return builder.build();
  }

  /**
   * Consumes either a "classic" function jsdoc with @param, @return, etc,
   * or a jsdoc with @type{function ...} and finds the types of the formal
   * parameters and the return value. It returns a builder because the callers
   * of this function must separately handle @constructor, @interface, etc.
   */
  public FunctionTypeBuilder getFunctionType(
      JSDocInfo jsdoc, Node funNode, RawNominalType ownerType,
      DeclaredTypeRegistry registry) {
    try {
      if (jsdoc != null && jsdoc.getType() != null) {
        Node jsdocNode = jsdoc.getType().getRootNode();
        if (jsdocNode.getType() == Token.FUNCTION) {
          return getFunTypeFromAtTypeJsdoc(jsdoc, funNode, ownerType, registry);
        } else {
          warn("The function is annotated with a non-function jsdoc. " +
              "Ignoring jsdoc.", funNode);
        }
      }
      return getFunTypeFromTypicalFunctionJsdoc(
          jsdoc, funNode, ownerType, registry, false);
    } catch (FunctionTypeBuilder.WrongParameterOrderException e) {
      warn("Wrong parameter order: required parameters are first, " +
          "then optional, then varargs. Ignoring jsdoc.", funNode);
      return getFunTypeFromTypicalFunctionJsdoc(
          null, funNode, ownerType, registry, true);
    }
  }

  private FunctionTypeBuilder getFunTypeFromAtTypeJsdoc(
      JSDocInfo jsdoc, Node funNode, RawNominalType ownerType,
      DeclaredTypeRegistry registry) {
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
        builder.addOptFormal(null);
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
              builder.addOptFormal(null);
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
    builder.addRetType(getTypeFromNode(childJsdoc, ownerType, registry, null));

    return builder;
  }

  private FunctionTypeBuilder getFunTypeFromTypicalFunctionJsdoc(
      JSDocInfo jsdoc, Node funNode, RawNominalType ownerType,
      DeclaredTypeRegistry registry,
      boolean ignoreJsdoc /* for when the jsdoc is malformed */) {
    Preconditions.checkArgument(!ignoreJsdoc || jsdoc == null);
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    Node params = funNode.getFirstChild().getNext();
    ImmutableList<String> typeParameters = null;

    // TODO(user): need more @template warnings
    // - warn for multiple @template annotations
    // - warn for @template annotation w/out usage

    if (jsdoc != null) {
      typeParameters = jsdoc.getTemplateTypeNames();
      if (typeParameters.size() > 0) {
        builder.addTypeParameters(typeParameters);
      }
    }
    for (Node param = params.getFirstChild();
         param != null;
         param = param.getNext()) {
      String pname = param.getQualifiedName();
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
        // TODO(user): The support for inline optional parameters is currently
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
    if (inlineRetJsdoc != null) {
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

  void warn(String msg, Node faultyNode) {
    reporter.warning(msg, faultyNode.getSourceFileName(),
        faultyNode.getLineno(), faultyNode.getCharno());
  }

}
