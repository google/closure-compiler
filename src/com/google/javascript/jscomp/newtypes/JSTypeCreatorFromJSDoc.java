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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CodingConvention;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.newtypes.NominalType.RawNominalType;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public class JSTypeCreatorFromJSDoc {
  public static final DiagnosticType INVALID_GENERICS_INSTANTIATION =
      DiagnosticType.warning(
        "JSC_INVALID_GENERICS_INSTANTIATION",
        "Invalid generics instantiation for {0}.\n"
        + "Expected {1} type argument(s), but found {2}.");

  public static final DiagnosticType BAD_JSDOC_ANNOTATION =
      DiagnosticType.warning(
        "JSC_BAD_JSDOC_ANNOTATION",
        "Bad JSDoc annotation. {0}");

  public static final DiagnosticType EXTENDS_NON_OBJECT =
      DiagnosticType.warning(
          "JSC_EXTENDS_NON_OBJECT",
          "{0} extends non-object type {1}.\n");

  public static final DiagnosticType EXTENDS_NOT_ON_CTOR_OR_INTERF =
      DiagnosticType.warning(
          "JSC_EXTENDS_NOT_ON_CTOR_OR_INTERF",
          "@extends used without @constructor or @interface for {0}.\n");

  public static final DiagnosticType INHERITANCE_CYCLE =
      DiagnosticType.warning(
          "JSC_INHERITANCE_CYCLE",
          "Cycle detected in inheritance chain of type {0}");

  public static final DiagnosticType DICT_IMPLEMENTS_INTERF =
      DiagnosticType.warning(
          "JSC_DICT_IMPLEMENTS_INTERF",
          "Class {0} is a dict. Dicts can't implement interfaces.");

  public static final DiagnosticType IMPLEMENTS_WITHOUT_CONSTRUCTOR =
      DiagnosticType.warning(
          "JSC_IMPLEMENTS_WITHOUT_CONSTRUCTOR",
          "@implements used without @constructor or @interface for {0}");

  public static final DiagnosticType CONFLICTING_SHAPE_TYPE =
      DiagnosticType.warning(
          "JSC_CONFLICTING_SHAPE_TYPE",
          "{1} cannot extend this type; {0}s can only extend {0}s");

  public static final DiagnosticType CONFLICTING_EXTENDED_TYPE =
      DiagnosticType.warning(
          "JSC_CONFLICTING_EXTENDED_TYPE",
          "{1} cannot extend this type; {0}s can only extend {0}s");

  public static final DiagnosticType CONFLICTING_IMPLEMENTED_TYPE =
    DiagnosticType.warning(
        "JSC_CONFLICTING_IMPLEMENTED_TYPE",
        "{0} cannot implement this type; "
        + "an interface can only extend, but not implement interfaces");

  private final CodingConvention convention;

  // Used to communicate state between methods when resolving enum types
  private int howmanyTypeVars = 0;

  private static final JSType OBJECT_OR_NULL =
      JSType.join(JSType.TOP_OBJECT, JSType.NULL);

  /** Exception for when unrecognized type names are encountered */
  public static class UnknownTypeException extends Exception {
    UnknownTypeException(String cause) {
      super(cause);
    }
  }

  private Set<JSError> warnings = new HashSet<>();
  // Unknown type names indexed by JSDoc AST node at which they were found.
  private Map<Node, String> unknownTypeNames = new HashMap<>();

  public JSTypeCreatorFromJSDoc(CodingConvention convention) {
    this.convention = convention;
  }

  private static DeclaredFunctionType qmarkFunctionDeclared =
      FunctionTypeBuilder.qmarkFunctionBuilder().buildDeclaration();
  private JSType qmarkFunctionOrNull = null;

  private JSType getQmarkFunctionOrNull(JSTypes commonTypes) {
    if (qmarkFunctionOrNull == null) {
      qmarkFunctionOrNull =
          JSType.join(commonTypes.qmarkFunction(), JSType.NULL);
    }
    return qmarkFunctionOrNull;
  }

  public JSType getNodeTypeDeclaration(JSDocInfo jsdoc,
      RawNominalType ownerType, DeclaredTypeRegistry registry) {
    return getNodeTypeDeclaration(jsdoc, registry, ownerType == null
        ? ImmutableList.<String>of() : ownerType.getTypeParameters());
  }

  private JSType getNodeTypeDeclaration(JSDocInfo jsdoc,
      DeclaredTypeRegistry registry, ImmutableList<String> typeParameters) {
    if (jsdoc == null) {
      return null;
    }
    return getTypeFromJSTypeExpression(
        jsdoc.getType(), registry, typeParameters);
  }

  public Set<JSError> getWarnings() {
    return warnings;
  }

  public Map<Node, String> getUnknownTypesMap() {
    return unknownTypeNames;
  }

  private JSType getTypeFromJSTypeExpression(JSTypeExpression expr,
      DeclaredTypeRegistry registry, ImmutableList<String> typeParameters) {
    if (expr == null) {
      return null;
    }
    return getTypeFromNode(expr.getRoot(), registry, typeParameters == null
        ? ImmutableList.<String>of() : typeParameters);
  }

  // Very similar to JSTypeRegistry#createFromTypeNodesInternal
  // n is a jsdoc node, not an AST node; the same class (Node) is used for both
  private JSType getTypeFromNode(Node n, DeclaredTypeRegistry registry,
      ImmutableList<String> typeParameters) {
    try {
      return getTypeFromNodeHelper(n, registry, typeParameters);
    } catch (UnknownTypeException e) {
      return JSType.UNKNOWN;
    }
  }

  private JSType getMaybeTypeFromNode(Node n, DeclaredTypeRegistry registry,
      ImmutableList<String> typeParameters) {
    try {
      return getTypeFromNodeHelper(n, registry, typeParameters);
    } catch (UnknownTypeException e) {
      return null;
    }
  }

  private JSType getTypeFromNodeHelper(Node n, DeclaredTypeRegistry registry,
      ImmutableList<String> typeParameters) throws UnknownTypeException {
    Preconditions.checkNotNull(n);
    Preconditions.checkNotNull(typeParameters);
    switch (n.getType()) {
      case Token.LC:
        return getRecordTypeHelper(n, registry, typeParameters);
      case Token.EMPTY: // for function types that don't declare a return type
        return JSType.UNKNOWN;
      case Token.VOID:
        return JSType.UNDEFINED;
      case Token.LB:
        warn("The [] type syntax is no longer supported." +
             " Please use Array.<T> instead.", n);
        return JSType.UNKNOWN;
      case Token.STRING:
        return getNamedTypeHelper(n, registry, typeParameters);
      case Token.PIPE: {
        // The way JSType.join works, Subtype|Supertype is equal to Supertype,
        // so when programmers write un-normalized unions, we normalize them
        // silently. We may also want to warn.
        JSType union = JSType.BOTTOM;
        for (Node child = n.getFirstChild(); child != null;
             child = child.getNext()) {
          // TODO(dimvar): When the union has many things, we join and throw
          // away types, except the result of the last join. Very inefficient.
          // Consider optimizing.
          JSType nextType = getTypeFromNodeHelper(child, registry, typeParameters);
          if (nextType.isUnknown()) {
            warn("This union type is equivalent to '?'.", n);
            return JSType.UNKNOWN;
          }
          union = JSType.join(union, nextType);
        }
        return union;
      }
      case Token.BANG: {
        JSType nullableType = getTypeFromNodeHelper(
            n.getFirstChild(), registry, typeParameters);
        if (nullableType.isTypeVariable()) {
          warn("Cannot use ! to restrict type variable type.\n"
              + "Prefer to make type argument non-nullable and add "
              + "null explicitly where needed (e.g. through ?T or T|null)", n);
        }
        return nullableType.removeType(JSType.NULL);
      }
      case Token.QMARK: {
        Node child = n.getFirstChild();
        if (child == null) {
          return JSType.UNKNOWN;
        } else {
          return JSType.join(JSType.NULL,
              getTypeFromNodeHelper(child, registry, typeParameters));
        }
      }
      case Token.STAR:
        return JSType.TOP;
      case Token.FUNCTION:
        return getFunTypeHelper(n, null, registry, typeParameters);
      default:
        throw new IllegalArgumentException("Unsupported type exp: " +
            Token.name(n.getType()) + " " + n.toStringTree());
    }
  }

  private JSType getRecordTypeHelper(Node n, DeclaredTypeRegistry registry,
      ImmutableList<String> typeParameters)
      throws UnknownTypeException {
    Map<String, JSType> fields = new HashMap<>();
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
          getTypeFromNodeHelper(
              fieldTypeNode.getLastChild(), registry, typeParameters);
      // TODO(blickly): Allow optional properties
      fields.put(fieldName, fieldType);
    }
    return JSType.fromObjectType(ObjectType.fromProperties(fields));
  }

  private JSType getNamedTypeHelper(Node n, DeclaredTypeRegistry registry,
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
        return getQmarkFunctionOrNull(registry.getCommonTypes());
      case "Object":
        return OBJECT_OR_NULL;
      default: {
        if (outerTypeParameters.contains(typeName)) {
          return JSType.fromTypeVar(typeName);
        } else {
          // It's either a typedef, an enum, a type variable or a nominal type
          Typedef td = registry.getTypedef(typeName);
          if (td != null) {
            return getTypedefType(td, registry);
          }
          EnumType e = registry.getEnum(typeName);
          if (e != null) {
            return getEnumPropType(e, registry);
          }
          JSType namedType = registry.lookupTypeByName(typeName);
          if (namedType == null) {
            unknownTypeNames.put(n, typeName);
            throw new UnknownTypeException("Unhandled type: " + typeName);
          }
          if (namedType.isTypeVariable()) {
            howmanyTypeVars++;
            return namedType;
          }
          if (namedType.isUnknown()) {
            return namedType;
          }
          return getNominalTypeHelper(
              namedType, n, registry, outerTypeParameters);
        }
      }
    }
  }

  private JSType getTypedefType(Typedef td, DeclaredTypeRegistry registry) {
    resolveTypedef(td, registry);
    return td.getType();
  }

  public void resolveTypedef(Typedef td, DeclaredTypeRegistry registry) {
    Preconditions.checkState(td != null, "getTypedef should only be " +
        "called when we know that the typedef is defined");
    if (td.isResolved()) {
      return;
    }
    JSTypeExpression texp = td.getTypeExpr();
    JSType tdType;
    if (texp == null) {
      warn("Circular type definitions are not allowed.",
          td.getTypeExprForErrorReporting().getRoot());
      tdType = JSType.UNKNOWN;
    } else {
      tdType = getTypeFromJSTypeExpression(texp, registry, null);
    }
    td.resolveTypedef(tdType);
  }

  private JSType getEnumPropType(EnumType e, DeclaredTypeRegistry registry) {
    resolveEnum(e, registry);
    return e.getPropType();
  }

  public void resolveEnum(EnumType e, DeclaredTypeRegistry registry) {
    Preconditions.checkState(e != null, "getEnum should only be " +
        "called when we know that the enum is defined");
    if (e.isResolved()) {
      return;
    }
    JSTypeExpression texp = e.getTypeExpr();
    JSType enumeratedType;
    if (texp == null) {
      warn("Circular type definitions are not allowed.",
          e.getTypeExprForErrorReporting().getRoot());
      enumeratedType = JSType.UNKNOWN;
    } else {
      int numTypeVars = howmanyTypeVars;
      enumeratedType = getTypeFromJSTypeExpression(texp, registry, null);
      if (howmanyTypeVars > numTypeVars) {
        warn("An enum type cannot include type variables.", texp.getRoot());
        enumeratedType = JSType.UNKNOWN;
        howmanyTypeVars = numTypeVars;
      } else if (enumeratedType.isTop()) {
        warn("An enum type cannot be *. " +
            "Use ? if you do not want the elements checked.",
            texp.getRoot());
        enumeratedType = JSType.UNKNOWN;
      } else if (enumeratedType.isUnion()) {
        warn("An enum type cannot be a union type.", texp.getRoot());
        enumeratedType = JSType.UNKNOWN;
      }
    }
    e.resolveEnum(enumeratedType);
  }

  private JSType getNominalTypeHelper(JSType namedType, Node n,
      DeclaredTypeRegistry registry, ImmutableList<String> outerTypeParameters)
      throws UnknownTypeException {
    NominalType uninstantiated = namedType.getNominalTypeIfUnique();
    RawNominalType rawType = uninstantiated.getRawNominalType();
    if (!rawType.isGeneric() && !n.hasChildren()) {
      return rawType.getInstanceAsNullableJSType();
    }
    ImmutableList.Builder<JSType> typeList = ImmutableList.builder();
    if (n.hasChildren()) {
      // Compute instantiation of polymorphic class/interface.
      Preconditions.checkState(n.getFirstChild().isBlock());
      for (Node child : n.getFirstChild().children()) {
        typeList.add(
            getTypeFromNodeHelper(child, registry, outerTypeParameters));
      }
    }
    ImmutableList<JSType> typeArguments = typeList.build();
    ImmutableList<String> typeParameters = rawType.getTypeParameters();
    int typeArgsSize = typeArguments.size();
    int typeParamsSize = typeParameters.size();
    if (typeArgsSize != typeParamsSize) {
      String nominalTypeName = uninstantiated.getName();
      if (!nominalTypeName.equals("Object")) {
        // TODO(dimvar): remove this once we handle parameterized Object
        warnings.add(JSError.make(
            n, INVALID_GENERICS_INSTANTIATION,
            nominalTypeName, String.valueOf(typeParamsSize),
            String.valueOf(typeArgsSize)));
      }
      return JSType.join(JSType.NULL,
          JSType.fromObjectType(ObjectType.fromNominalType(
              uninstantiated.instantiateGenerics(
                  fixLengthOfTypeList(typeParameters.size(), typeArguments)))));
    }
    return JSType.join(JSType.NULL,
        JSType.fromObjectType(ObjectType.fromNominalType(
            uninstantiated.instantiateGenerics(typeArguments))));
  }

  private static List<JSType> fixLengthOfTypeList(
      int desiredLength, List<JSType> typeList) {
    int length = typeList.size();
    if (length == desiredLength) {
      return typeList;
    }
    ImmutableList.Builder<JSType> builder = ImmutableList.builder();
    for (int i = 0; i < desiredLength; i++) {
      builder.add(i < length ? typeList.get(i) : JSType.UNKNOWN);
    }
    return builder.build();
  }

  // Don't confuse with getFunTypeFromAtTypeJsdoc; the function below computes a
  // type that doesn't have an associated AST node.
  private JSType getFunTypeHelper(Node jsdocNode, RawNominalType ownerType,
      DeclaredTypeRegistry registry, ImmutableList<String> typeParameters)
      throws UnknownTypeException {
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    if (ownerType != null) {
      builder.addReceiverType(ownerType.getAsNominalType());
    }
    fillInFunTypeBuilder(
        jsdocNode, ownerType, registry, typeParameters, builder);
    return registry.getCommonTypes().fromFunctionType(builder.buildFunction());
  }

  private void fillInFunTypeBuilder(
      Node jsdocNode, RawNominalType ownerType, DeclaredTypeRegistry registry,
      ImmutableList<String> typeParameters, FunctionTypeBuilder builder)
      throws UnknownTypeException {
    Node child = jsdocNode.getFirstChild();
    if (child.getType() == Token.THIS) {
      if (ownerType == null) {
        builder.addReceiverType(
            getNominalType(child.getFirstChild(), registry, typeParameters));
      }
      child = child.getNext();
    } else if (child.getType() == Token.NEW) {
      builder.addNominalType(
          getNominalType(child.getFirstChild(), registry, typeParameters));
      child = child.getNext();
    }
    if (child.getType() == Token.PARAM_LIST) {
      for (Node arg = child.getFirstChild(); arg != null; arg = arg.getNext()) {
        try {
          switch (arg.getType()) {
            case Token.EQUALS:
              builder.addOptFormal(getTypeFromNodeHelper(
                  arg.getFirstChild(), registry, typeParameters));
              break;
            case Token.ELLIPSIS:
              Node restNode = arg.getFirstChild();
              builder.addRestFormals(restNode == null ? JSType.UNKNOWN :
                  getTypeFromNodeHelper(restNode, registry, typeParameters));
              break;
            default:
              builder.addReqFormal(
                  getTypeFromNodeHelper(arg, registry, typeParameters));
              break;
          }
        } catch (FunctionTypeBuilder.WrongParameterOrderException e) {
          warn("Wrong parameter order: required parameters are first, " +
              "then optional, then varargs", jsdocNode);
        }
      }
      child = child.getNext();
    }
    builder.addRetType(getTypeFromNodeHelper(child, registry, typeParameters));
  }

  // May return null;
  private NominalType getNominalType(Node n,
      DeclaredTypeRegistry registry, ImmutableList<String> typeParameters) {
    return getTypeFromNode(n, registry, typeParameters)
        .getNominalTypeIfUnique();
  }

  private ImmutableSet<NominalType> getImplementedInterfaces(
      JSDocInfo jsdoc, DeclaredTypeRegistry registry,
      ImmutableList<String> typeParameters) {
    return getInterfacesHelper(jsdoc, registry, typeParameters, true);
  }

  private ImmutableSet<NominalType> getExtendedInterfaces(
      JSDocInfo jsdoc, DeclaredTypeRegistry registry,
      ImmutableList<String> typeParameters) {
    return getInterfacesHelper(jsdoc, registry, typeParameters, false);
  }

  private ImmutableSet<NominalType> getInterfacesHelper(
      JSDocInfo jsdoc, DeclaredTypeRegistry registry,
      ImmutableList<String> typeParameters, boolean implementedIntfs) {
    ImmutableSet.Builder<NominalType> builder = ImmutableSet.builder();
    for (JSTypeExpression texp : (implementedIntfs ?
          jsdoc.getImplementedInterfaces() :
          jsdoc.getExtendedInterfaces())) {
      Node expRoot = texp.getRoot();
      JSType interfaceType =
          getMaybeTypeFromNode(expRoot, registry, typeParameters);
      if (interfaceType != null) {
        NominalType nt = interfaceType.getNominalTypeIfUnique();
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
   *
   * constructorType is non-null iff this function is a constructor or
   * interface declaration.
   */
  public DeclaredFunctionType getFunctionType(
      JSDocInfo jsdoc, String functionName, Node declNode,
      RawNominalType constructorType, RawNominalType ownerType,
      DeclaredTypeRegistry registry) {
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    if (ownerType != null) {
      builder.addReceiverType(ownerType.getAsNominalType());
    }
    try {
      if (jsdoc != null && jsdoc.getType() != null) {
        Node jsdocNode = jsdoc.getType().getRoot();
        int tokenType = jsdocNode.getType();
        if (tokenType == Token.FUNCTION) {
          if (declNode.isFunction()) {
            return getFunTypeFromAtTypeJsdoc(
                jsdoc, declNode, ownerType, registry, builder);
          }
          try {
            // TODO(blickly): Use typeParameters here
            fillInFunTypeBuilder(jsdocNode, ownerType, registry,
                ImmutableList.<String>of(), builder);
            return builder.buildDeclaration();
          } catch (UnknownTypeException e) {
            return qmarkFunctionDeclared;
          }
        }
        if (isQmarkFunction(jsdocNode)) {
          return qmarkFunctionDeclared;
        } else {
          warn("The function is annotated with a non-function jsdoc. " +
              "Ignoring jsdoc.", declNode);
          return getFunTypeFromTypicalFunctionJsdoc(null, functionName,
              declNode, constructorType, ownerType, registry, builder, true);
        }
      }
      return getFunTypeFromTypicalFunctionJsdoc(jsdoc, functionName,
          declNode, constructorType, ownerType, registry, builder, false);
    } catch (FunctionTypeBuilder.WrongParameterOrderException e) {
      warn("Wrong parameter order: required parameters are first, " +
          "then optional, then varargs. Ignoring jsdoc.", declNode);
      return qmarkFunctionDeclared;
    }
  }

  private DeclaredFunctionType getFunTypeFromAtTypeJsdoc(
      JSDocInfo jsdoc, Node funNode, RawNominalType ownerType,
      DeclaredTypeRegistry registry, FunctionTypeBuilder builder) {
    Preconditions.checkArgument(funNode.isFunction());
    Node childJsdoc = jsdoc.getType().getRoot().getFirstChild();
    Node param = funNode.getFirstChild().getNext().getFirstChild();
    Node paramType;
    boolean warnedForMissingTypes = false;
    boolean warnedForInlineJsdoc = false;
    ImmutableList<String> typeParameters = ownerType == null
        ? ImmutableList.<String>of() : ownerType.getTypeParameters();

    if (childJsdoc.getType() == Token.THIS) {
      if (ownerType == null) {
        builder.addReceiverType(getNominalType(
            childJsdoc.getFirstChild(), registry, typeParameters));
      }
      childJsdoc = childJsdoc.getNext();
    } else if (childJsdoc.getType() == Token.NEW) {
      builder.addNominalType(
          getNominalType(childJsdoc.getFirstChild(), registry, typeParameters));
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
                paramType.getFirstChild(), registry, typeParameters));
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
                getTypeFromNode(paramType, registry, typeParameters));
            break;
        }
        paramType = paramType.getNext();
      }
      param = param.getNext();
    }

    if (paramType != null) {
      if (paramType.getType() == Token.ELLIPSIS) {
        builder.addRestFormals(getTypeFromNode(
            paramType.getFirstChild(), registry, typeParameters));
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
      builder.addRetType(getTypeFromNode(childJsdoc, registry, typeParameters));
    }

    return builder.buildDeclaration();
  }

  private static class ParamIterator {
    Iterator<String> paramNames;
    Node params;
    int index = -1;

    ParamIterator(Node params, JSDocInfo jsdoc) {
      Preconditions.checkArgument(params != null || jsdoc != null);
      if (params != null) {
        this.params = params;
        this.paramNames = null;
      } else {
        this.params = null;
        this.paramNames = jsdoc.getParameterNames().iterator();
      }
    }

    boolean hasNext() {
      if (paramNames != null) {
        return paramNames.hasNext();
      }
      return index + 1 < params.getChildCount();
    }

    String nextString() {
      if (paramNames != null) {
        return paramNames.next();
      }
      index++;
      return params.getChildAtIndex(index).getString();
    }

    Node getNode() {
      if (paramNames != null) {
        return null;
      }
      return params.getChildAtIndex(index);
    }
  }

  private DeclaredFunctionType getFunTypeFromTypicalFunctionJsdoc(
      JSDocInfo jsdoc, String functionName, Node funNode,
      RawNominalType constructorType, RawNominalType ownerType,
      DeclaredTypeRegistry registry, FunctionTypeBuilder builder,
      boolean ignoreJsdoc /* for when the jsdoc is malformed */) {
    Preconditions.checkArgument(!ignoreJsdoc || jsdoc == null);
    Preconditions.checkArgument(!ignoreJsdoc || funNode.isFunction());
    ImmutableList<String> typeParameters = ImmutableList.of();
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
    if (ownerType != null) {
      ImmutableList.Builder<String> paramsBuilder =
          new ImmutableList.Builder<>();
      paramsBuilder.addAll(typeParameters);
      paramsBuilder.addAll(ownerType.getTypeParameters());
      typeParameters = paramsBuilder.build();
    }

    fillInFormalParameterTypes(
        jsdoc, funNode, typeParameters, registry, builder, ignoreJsdoc);
    fillInReturnType(
        jsdoc, funNode, parent, typeParameters, registry, builder, ignoreJsdoc);
    if (jsdoc == null) {
      return builder.buildDeclaration();
    }

    // Look at other annotations, eg, @constructor
    NominalType parentClass = getMaybeParentClass(
        jsdoc, functionName, funNode, typeParameters, registry);
    ImmutableSet<NominalType> implementedIntfs = getImplementedInterfaces(
        jsdoc, registry, typeParameters);
    if (constructorType == null
        && (jsdoc.isConstructor() || jsdoc.isInterface())) {
      // Anonymous type, don't register it.
      return builder.buildDeclaration();
    } else if (jsdoc.isConstructor()) {
      handleConstructorAnnotation(functionName, funNode, constructorType,
          parentClass, implementedIntfs, registry, builder);
    } else if (jsdoc.isInterface()) {
      handleInterfaceAnnotation(jsdoc, functionName, funNode, constructorType,
          implementedIntfs, typeParameters, registry, builder);
    } else if (!implementedIntfs.isEmpty()) {
      warnings.add(JSError.make(
          funNode, IMPLEMENTS_WITHOUT_CONSTRUCTOR, functionName));
    }

    if (jsdoc.hasThisType() && ownerType == null) {
      Node thisNode = jsdoc.getThisType().getRoot();
      JSType thisType =
          getMaybeTypeFromNode(thisNode, registry, typeParameters);
      // TODO(dimvar): thisType may be non-null but have a null
      // thisTypeAsNominal.
      // We currently only support nominal types for the receiver type, but
      // people use other types as well: unions, records, etc.
      // Decide what to do about those.
      NominalType thisTypeAsNominal = thisType == null
          ? null : thisType.getNominalTypeIfUnique();
      builder.addReceiverType(thisTypeAsNominal);
    }

    return builder.buildDeclaration();
  }

  private void fillInFormalParameterTypes(
      JSDocInfo jsdoc, Node funNode,
      ImmutableList<String> typeParameters,
      DeclaredTypeRegistry registry, FunctionTypeBuilder builder,
      boolean ignoreJsdoc /* for when the jsdoc is malformed */) {
    boolean ignoreFunNode  = !funNode.isFunction();
    Node params = ignoreFunNode ? null : funNode.getFirstChild().getNext();
    ParamIterator iterator = new ParamIterator(params, jsdoc);
    while (iterator.hasNext()) {
      String pname = iterator.nextString();
      Node param = iterator.getNode();
      JSType inlineParamType = (ignoreJsdoc || ignoreFunNode)
          ? null : getNodeTypeDeclaration(
              param.getJSDocInfo(), registry, typeParameters);
      boolean isRequired = true;
      boolean isRestFormals = false;
      JSTypeExpression texp =
          jsdoc == null ? null : jsdoc.getParameterType(pname);
      Node jsdocNode = texp == null ? null : texp.getRoot();
      if (param != null) {
        if (convention.isOptionalParameter(param)) {
          isRequired = false;
        } else if (convention.isVarArgsParameter(param)) {
          isRequired = false;
          isRestFormals = true;
        }
      }
      JSType fnParamType = null;
      if (jsdocNode != null) {
        if (jsdocNode.getType() == Token.EQUALS) {
          isRequired = false;
          jsdocNode = jsdocNode.getFirstChild();
        } else if (jsdocNode.getType() == Token.ELLIPSIS) {
          isRequired = false;
          isRestFormals = true;
          jsdocNode = jsdocNode.getFirstChild();
        }
        fnParamType = getTypeFromNode(jsdocNode, registry, typeParameters);
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
        builder.addRestFormals(
            fnParamType == null ? JSType.UNKNOWN : fnParamType);
      } else {
        builder.addOptFormal(fnParamType);
      }
    }
  }

  private void fillInReturnType(
      JSDocInfo jsdoc, Node funNode, Node parent,
      ImmutableList<String> typeParameters,
      DeclaredTypeRegistry registry, FunctionTypeBuilder builder,
      boolean ignoreJsdoc /* for when the jsdoc is malformed */) {
    JSDocInfo inlineRetJsdoc =
        ignoreJsdoc ? null : funNode.getFirstChild().getJSDocInfo();
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
      builder.addRetType(
          getNodeTypeDeclaration(inlineRetJsdoc, registry, typeParameters));
      if (retTypeExp != null) {
        warn("Found two JsDoc comments for the return type", funNode);
      }
    } else {
      builder.addRetType(
          getTypeFromJSTypeExpression(retTypeExp, registry, typeParameters));
    }
  }

  private NominalType getMaybeParentClass(
      JSDocInfo jsdoc, String functionName, Node funNode,
      ImmutableList<String> typeParameters, DeclaredTypeRegistry registry) {
    if (!jsdoc.hasBaseType()) {
      return null;
    }
    if (!jsdoc.isConstructor()) {
      warnings.add(JSError.make(
          funNode, EXTENDS_NOT_ON_CTOR_OR_INTERF, functionName));
      return null;
    }
    Node docNode = jsdoc.getBaseType().getRoot();
    JSType extendedType =
        getMaybeTypeFromNode(docNode, registry, typeParameters);
    if (extendedType == null) {
      return null;
    }
    NominalType parentClass = extendedType.getNominalTypeIfUnique();
    if (parentClass != null && parentClass.isClass()) {
      return parentClass;
    }
    if (parentClass == null) {
      warnings.add(JSError.make(funNode, EXTENDS_NON_OBJECT,
              functionName, extendedType.toString()));
    } else {
      Preconditions.checkState(parentClass.isInterface());
      warnings.add(JSError.make(funNode, CONFLICTING_EXTENDED_TYPE,
              "constructor", functionName));
    }
    return null;
  }

  private void handleConstructorAnnotation(
      String functionName, Node funNode, RawNominalType constructorType,
      NominalType parentClass, ImmutableSet<NominalType> implementedIntfs,
      DeclaredTypeRegistry registry, FunctionTypeBuilder builder) {
    String className = constructorType.toString();
    NominalType builtinObject = registry.getCommonTypes().getObjectType();
    if (parentClass == null && !functionName.equals("Object")) {
      parentClass = builtinObject;
    }
    if (parentClass != null) {
      if (!constructorType.addSuperClass(parentClass)) {
        warnings.add(JSError.make(funNode, INHERITANCE_CYCLE, className));
      } else if (parentClass != builtinObject) {
        if (constructorType.isStruct() && !parentClass.isStruct()) {
          warnings.add(JSError.make(
              funNode, CONFLICTING_SHAPE_TYPE, "struct", className));
        } else if (constructorType.isDict() && !parentClass.isDict()) {
          warnings.add(JSError.make(
              funNode, CONFLICTING_SHAPE_TYPE, "dict", className));
        }
      }
    }
    if (constructorType.isDict() && !implementedIntfs.isEmpty()) {
      warnings.add(JSError.make(funNode, DICT_IMPLEMENTS_INTERF, className));
    }
    boolean noCycles = constructorType.addInterfaces(implementedIntfs);
    Preconditions.checkState(noCycles);
    builder.addNominalType(constructorType.getAsNominalType());
  }

  private void handleInterfaceAnnotation(
      JSDocInfo jsdoc, String functionName, Node funNode,
      RawNominalType constructorType,
      ImmutableSet<NominalType> implementedIntfs,
      ImmutableList<String> typeParameters,
      DeclaredTypeRegistry registry, FunctionTypeBuilder builder) {
    if (!implementedIntfs.isEmpty()) {
      warnings.add(JSError.make(
          funNode, CONFLICTING_IMPLEMENTED_TYPE, functionName));
    }
    boolean noCycles = constructorType.addInterfaces(
        getExtendedInterfaces(jsdoc, registry, typeParameters));
    if (!noCycles) {
      warnings.add(JSError.make(
          funNode, INHERITANCE_CYCLE, constructorType.toString()));
    }
    builder.addNominalType(constructorType.getAsNominalType());
  }

  // /** @param {...?} var_args */ function f(var_args) { ... }
  // var_args shouldn't be used in the body of f
  public static boolean isRestArg(JSDocInfo funJsdoc, String formalParamName) {
    if (funJsdoc == null) {
      return false;
    }
    JSTypeExpression texp = funJsdoc.getParameterType(formalParamName);
    Node jsdocNode = texp == null ? null : texp.getRoot();
    return jsdocNode != null && jsdocNode.getType() == Token.ELLIPSIS;
  }

  // TODO(blickly): Add more DiagnosticTypes and remove this method
  void warn(String msg, Node faultyNode) {
    warnings.add(JSError.make(faultyNode, BAD_JSDOC_ANNOTATION, msg));
  }

}
