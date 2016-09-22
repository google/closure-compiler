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

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CodingConvention;
import com.google.javascript.jscomp.DiagnosticGroup;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * During GlobalTypeInfo, this class parses type ASTs inside jsdocs and converts them
 * to JSTypes.
 *
 * There isn't a clear distinction which warnings should be signaled here and which
 * ones in GlobalTypeInfo; we give the warning in whichever class is most convenient.
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public final class JSTypeCreatorFromJSDoc {
  public static final DiagnosticType INVALID_GENERICS_INSTANTIATION =
      DiagnosticType.warning(
        "JSC_NTI_INVALID_GENERICS_INSTANTIATION",
        "Invalid generics instantiation{0}.\n"
        + "Expected {1} type argument(s), but found {2}");

  public static final DiagnosticType EXTENDS_NON_OBJECT =
      DiagnosticType.warning(
          "JSC_NTI_EXTENDS_NON_OBJECT",
          "{0} extends non-object type {1}.\n");

  public static final DiagnosticType EXTENDS_NOT_ON_CTOR_OR_INTERF =
      DiagnosticType.warning(
          "JSC_NTI_EXTENDS_NOT_ON_CTOR_OR_INTERF",
          "@extends used without @constructor or @interface for {0}.\n");

  public static final DiagnosticType INHERITANCE_CYCLE =
      DiagnosticType.warning(
          "JSC_NTI_INHERITANCE_CYCLE",
          "Cycle detected in inheritance chain of type {0}");

  public static final DiagnosticType DICT_IMPLEMENTS_INTERF =
      DiagnosticType.warning(
          "JSC_NTI_DICT_IMPLEMENTS_INTERF",
          "Class {0} is a dict. Dicts can't implement interfaces");

  public static final DiagnosticType IMPLEMENTS_WITHOUT_CONSTRUCTOR =
      DiagnosticType.warning(
          "JSC_NTI_IMPLEMENTS_WITHOUT_CONSTRUCTOR",
          "@implements used without @constructor or @interface for {0}");

  public static final DiagnosticType CONFLICTING_EXTENDED_TYPE =
      DiagnosticType.warning(
          "JSC_NTI_CONFLICTING_EXTENDED_TYPE",
          "{1} cannot extend this type; {0}s can only extend {0}s");

  public static final DiagnosticType CONFLICTING_IMPLEMENTED_TYPE =
    DiagnosticType.warning(
        "JSC_NTI_CONFLICTING_IMPLEMENTED_TYPE",
        "{0} cannot implement this type; "
        + "an interface can only extend, but not implement interfaces");

  public static final DiagnosticType UNION_IS_UNINHABITABLE =
    DiagnosticType.warning(
        "JSC_NTI_UNION_IS_UNINHABITABLE",
        "Union of {0} with {1} would create an impossible type");

  public static final DiagnosticType NEW_EXPECTS_OBJECT_OR_TYPEVAR =
    DiagnosticType.warning(
        "JSC_NTI_NEW_EXPECTS_OBJECT_OR_TYPEVAR",
        "The \"new:\" annotation only accepts object types and type variables; "
        + "found {0}");

  public static final DiagnosticType BAD_ARRAY_TYPE_SYNTAX =
    DiagnosticType.warning(
        "JSC_NTI_BAD_ARRAY_TYPE_SYNTAX",
        "The [] type syntax is not supported. Please use Array<T> instead");

  public static final DiagnosticType CANNOT_MAKE_TYPEVAR_NON_NULL =
    DiagnosticType.warning(
        "JSC_NTI_CANNOT_MAKE_TYPEVAR_NON_NULL",
        "Cannot use ! to restrict type variable type.\n"
        + "Prefer to make type argument non-nullable and add "
        + "null explicitly where needed (e.g. through ?T or T|null)");

  public static final DiagnosticType CIRCULAR_TYPEDEF_ENUM =
    DiagnosticType.warning(
        "JSC_NTI_CIRCULAR_TYPEDEF_ENUM",
        "Circular typedefs/enums are not allowed");

  public static final DiagnosticType ENUM_WITH_TYPEVARS =
    DiagnosticType.warning(
        "JSC_NTI_ENUM_WITH_TYPEVARS",
        "An enum type cannot include type variables");

  public static final DiagnosticType ENUM_IS_TOP =
    DiagnosticType.warning(
        "JSC_NTI_ENUM_IS_TOP",
        "An enum type cannot be *. "
        + "Use ? if you do not want the elements checked");

  // TODO(dimvar): This may prove to be too strict, may revisit.
  public static final DiagnosticType ENUM_IS_UNION =
    DiagnosticType.warning(
        "JSC_NTI_ENUM_IS_UNION",
        "An enum type cannot be a union type");

  public static final DiagnosticType WRONG_PARAMETER_ORDER =
    DiagnosticType.warning(
        "JSC_NTI_WRONG_PARAMETER_ORDER",
        "Wrong parameter order: required parameters are first, "
        + "then optional, then varargs");

  public static final DiagnosticType IMPLEMENTS_NON_INTERFACE =
    DiagnosticType.warning(
        "JSC_NTI_IMPLEMENTS_NON_INTERFACE",
        "Cannot implement non-interface {0}");

  public static final DiagnosticType EXTENDS_NON_INTERFACE =
    DiagnosticType.warning(
        "JSC_NTI_EXTENDS_NON_INTERFACE",
        "Cannot extend non-interface {0}");

  public static final DiagnosticType FUNCTION_WITH_NONFUNC_JSDOC =
    DiagnosticType.warning(
        "JSC_NTI_FUNCTION_WITH_NONFUNC_JSDOC",
        "The function is annotated with a non-function jsdoc. "
        + "Ignoring jsdoc");

  public static final DiagnosticType TEMPLATED_GETTER_SETTER =
    DiagnosticType.warning(
        "JSC_NTI_TEMPLATED_GETTER_SETTER",
        "@template can't be used with getters/setters");

  public static final DiagnosticType TWO_JSDOCS =
    DiagnosticType.warning(
        "JSC_NTI_TWO_JSDOCS",
        "Found two JsDoc comments for {0}");

  public static final DiagnosticGroup COMPATIBLE_DIAGNOSTICS = new DiagnosticGroup(
      BAD_ARRAY_TYPE_SYNTAX,
      CIRCULAR_TYPEDEF_ENUM,
      CONFLICTING_EXTENDED_TYPE,
      CONFLICTING_IMPLEMENTED_TYPE,
      EXTENDS_NON_INTERFACE,
      EXTENDS_NON_OBJECT,
      EXTENDS_NOT_ON_CTOR_OR_INTERF,
      IMPLEMENTS_NON_INTERFACE,
      IMPLEMENTS_WITHOUT_CONSTRUCTOR,
      INHERITANCE_CYCLE,
      NEW_EXPECTS_OBJECT_OR_TYPEVAR,
      TEMPLATED_GETTER_SETTER,
      TWO_JSDOCS,
      WRONG_PARAMETER_ORDER);

  public static final DiagnosticGroup NEW_DIAGNOSTICS = new DiagnosticGroup(
      CANNOT_MAKE_TYPEVAR_NON_NULL,
      DICT_IMPLEMENTS_INTERF,
      ENUM_IS_TOP,
      // TODO(dimvar): ENUM_IS_UNION is rare, but it happens. Should we support it?
      ENUM_IS_UNION,
      ENUM_WITH_TYPEVARS,
      FUNCTION_WITH_NONFUNC_JSDOC,
      INVALID_GENERICS_INSTANTIATION,
      UNION_IS_UNINHABITABLE);

  private final CodingConvention convention;
  private final UniqueNameGenerator nameGen;
  private final JSTypes commonTypes;

  // Callback passed by GlobalTypeInfo to record property names
  private final Function<String, Void> recordPropertyName;

  // Used to communicate state between methods when resolving enum types
  private int howmanyTypeVars = 0;

  private Set<JSError> warnings = new LinkedHashSet<>();
  // Unknown type names indexed by JSDoc AST node at which they were found.
  private Map<Node, String> unknownTypeNames = new LinkedHashMap<>();

  public JSTypeCreatorFromJSDoc(JSTypes commonTypes,
      CodingConvention convention, UniqueNameGenerator nameGen,
      Function<String, Void> recordPropertyName) {
    Preconditions.checkNotNull(commonTypes);
    this.commonTypes = commonTypes;
    this.qmarkFunctionDeclared = new FunctionAndSlotType(
        null, DeclaredFunctionType.qmarkFunctionDeclaration(commonTypes));
    this.convention = convention;
    this.nameGen = nameGen;
    this.recordPropertyName = recordPropertyName;
  }

  private FunctionAndSlotType qmarkFunctionDeclared;
  private static final boolean NULLABLE_TYPES_BY_DEFAULT = true;

  public JSType maybeMakeNullable(JSType t) {
    if (NULLABLE_TYPES_BY_DEFAULT) {
      return JSType.join(this.commonTypes.NULL, t);
    }
    return t;
  }

  public JSType getDeclaredTypeOfNode(JSDocInfo jsdoc, RawNominalType ownerType,
      DeclaredTypeRegistry registry) {
    return getDeclaredTypeOfNode(jsdoc, registry, ownerType == null
        ? ImmutableList.<String>of() : ownerType.getTypeParameters());
  }

  public JSType getTypeOfCommentNode(
      Node n, RawNominalType ownerType, DeclaredTypeRegistry registry) {
    return getTypeFromComment(
        n,
        registry,
        ownerType == null ? ImmutableList.<String>of() : ownerType.getTypeParameters());
  }

  private JSType getDeclaredTypeOfNode(JSDocInfo jsdoc,
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
    return getTypeFromComment(expr.getRoot(), registry, typeParameters);
  }

  // Very similar to JSTypeRegistry#createFromTypeNodesInternal
  // n is a jsdoc node, not an AST node; the same class (Node) is used for both
  private JSType getTypeFromComment(Node n, DeclaredTypeRegistry registry,
      ImmutableList<String> typeParameters) {
    return getTypeFromCommentHelper(n, registry, typeParameters);
  }

  private JSType getTypeFromCommentHelper(
      Node n, DeclaredTypeRegistry registry, ImmutableList<String> typeParameters) {
    Preconditions.checkNotNull(n);
    if (typeParameters == null) {
      typeParameters = ImmutableList.of();
    }
    switch (n.getToken()) {
      case LC:
        return getRecordTypeHelper(n, registry, typeParameters);
      case EMPTY: // for function types that don't declare a return type
        return this.commonTypes.UNKNOWN;
      case VOID:
        // TODO(dimvar): void can be represented in 2 ways: Token.VOID and a
        // Token.STRING whose getString() is "void".
        // Change jsdoc parsing to only have one representation.
        return this.commonTypes.UNDEFINED;
      case LB:
        warnings.add(JSError.make(n, BAD_ARRAY_TYPE_SYNTAX));
        return this.commonTypes.UNKNOWN;
      case STRING:
        return getNamedTypeHelper(n, registry, typeParameters);
      case PIPE: {
        // The way JSType.join works, Subtype|Supertype is equal to Supertype,
        // so when programmers write un-normalized unions, we normalize them
        // silently. We may also want to warn.
        JSType union = this.commonTypes.BOTTOM;
        for (Node child = n.getFirstChild(); child != null;
             child = child.getNext()) {
          // TODO(dimvar): When the union has many things, we join and throw
          // away types, except the result of the last join. Very inefficient.
          // Consider optimizing.
          JSType nextType = getTypeFromCommentHelper(child, registry, typeParameters);
          if (nextType.isUnknown()) {
            return this.commonTypes.UNKNOWN;
          }
          JSType nextUnion = JSType.join(union, nextType);
          if (nextUnion.isBottom()) {
            warnings.add(JSError.make(n, UNION_IS_UNINHABITABLE,
                    nextType.toString(), union.toString()));
            return this.commonTypes.UNKNOWN;
          }
          union = nextUnion;
        }
        return union;
      }
      case BANG: {
        JSType nullableType = getTypeFromCommentHelper(
            n.getFirstChild(), registry, typeParameters);
        if (nullableType.isTypeVariable()) {
          warnings.add(JSError.make(n, CANNOT_MAKE_TYPEVAR_NON_NULL));
        }
        return nullableType.removeType(this.commonTypes.NULL);
      }
      case QMARK: {
        Node child = n.getFirstChild();
        if (child == null) {
          return this.commonTypes.UNKNOWN;
        } else {
          return JSType.join(this.commonTypes.NULL,
              getTypeFromCommentHelper(child, registry, typeParameters));
        }
      }
      case STAR:
        return this.commonTypes.TOP;
      case FUNCTION:
        return getFunTypeHelper(n, registry, typeParameters);
      default:
        throw new IllegalArgumentException(
            "Unsupported type exp: " + n.getToken() + " " + n.toStringTree());
    }
  }

  // Looks at the type AST without evaluating it
  private boolean isUnionWithUndefined(Node n) {
    if (n == null || n.getToken() != Token.PIPE) {
      return false;
    }
    for (Node child : n.children()) {
      if (child.getToken() == Token.VOID
          || child.getToken() == Token.STRING
              && (child.getString().equals("void") || child.getString().equals("undefined"))) {
        return true;
      }
    }
    return false;
  }

  private JSType getRecordTypeHelper(Node n, DeclaredTypeRegistry registry,
      ImmutableList<String> typeParameters) {
    Map<String, Property> props = new LinkedHashMap<>();
    for (Node propNode = n.getFirstFirstChild();
         propNode != null;
         propNode = propNode.getNext()) {
      boolean isPropDeclared = propNode.getToken() == Token.COLON;
      Node propNameNode = isPropDeclared ? propNode.getFirstChild() : propNode;
      String propName = propNameNode.getString();
      if (propName.startsWith("'") || propName.startsWith("\"")) {
        propName = propName.substring(1, propName.length() - 1);
      }
      if (n.isFromExterns()) {
        this.recordPropertyName.apply(propName);
      }
      JSType propType = !isPropDeclared
          ? this.commonTypes.UNKNOWN
          : getTypeFromCommentHelper(propNode.getLastChild(), registry, typeParameters);
      Property prop;
      if (propType.equals(this.commonTypes.UNDEFINED)
          || isUnionWithUndefined(propNode.getLastChild())) {
        prop = Property.makeOptional(null, propType, propType);
      } else {
        prop = Property.make(propType, propType);
      }
      props.put(propName, prop);
    }
    return JSType.fromObjectType(ObjectType.fromProperties(this.commonTypes, props));
  }

  private JSType getNamedTypeHelper(
      Node n, DeclaredTypeRegistry registry, ImmutableList<String> outerTypeParameters) {
    String typeName = n.getString();
    switch (typeName) {
      case "boolean":
        checkInvalidGenericsInstantiation(n);
        return this.commonTypes.BOOLEAN;
      case "null":
        checkInvalidGenericsInstantiation(n);
        return this.commonTypes.NULL;
      case "number":
        checkInvalidGenericsInstantiation(n);
        return this.commonTypes.NUMBER;
      case "string":
        checkInvalidGenericsInstantiation(n);
        return this.commonTypes.STRING;
      case "undefined":
      case "void":
        checkInvalidGenericsInstantiation(n);
        return this.commonTypes.UNDEFINED;
      case "Function":
        checkInvalidGenericsInstantiation(n);
        return maybeMakeNullable(this.commonTypes.qmarkFunction());
      case "Object":
        // We don't generally handle parameterized Object<...>, but we want to
        // at least not warn about inexistent properties on it, so we type it
        // as @dict.
        return maybeMakeNullable(n.hasChildren()
            ? this.commonTypes.TOP_DICT : this.commonTypes.TOP_OBJECT);
      default:
        return lookupTypeByName(typeName, n, registry, outerTypeParameters);
    }
  }

  private JSType lookupTypeByName(String name, Node n,
      DeclaredTypeRegistry registry, ImmutableList<String> outerTypeParameters) {
    String tvar = UniqueNameGenerator.findGeneratedName(name, outerTypeParameters);
    if (tvar != null) {
      checkInvalidGenericsInstantiation(n);
      return JSType.fromTypeVar(this.commonTypes, tvar);
    }
    Declaration decl = registry.getDeclaration(QualifiedName.fromQualifiedString(name), true);
    if (decl == null) {
      unknownTypeNames.put(n, name);
      return this.commonTypes.UNKNOWN;
    }
    // It's either a typedef, an enum, a type variable, a nominal type, or a
    // forward-declared type.
    if (decl.getTypedef() != null) {
      checkInvalidGenericsInstantiation(n);
      return getTypedefType(decl.getTypedef(), registry);
    }
    if (decl.getEnum() != null) {
      checkInvalidGenericsInstantiation(n);
      return getEnumPropType(decl.getEnum(), registry);
    }
    if (decl.isTypeVar()) {
      checkInvalidGenericsInstantiation(n);
      howmanyTypeVars++;
      return decl.getTypeOfSimpleDecl();
    }
    if (decl.getNominal() != null) {
      return getNominalTypeHelper(decl.getNominal(), n, registry, outerTypeParameters);
    }
    // Forward-declared type
    return this.commonTypes.UNKNOWN;
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
      warnings.add(JSError.make(
          td.getTypeExprForErrorReporting().getRoot(), CIRCULAR_TYPEDEF_ENUM));
      tdType = this.commonTypes.UNKNOWN;
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
      warnings.add(JSError.make(
          e.getTypeExprForErrorReporting().getRoot(), CIRCULAR_TYPEDEF_ENUM));
      enumeratedType = this.commonTypes.UNKNOWN;
    } else {
      int numTypeVars = howmanyTypeVars;
      enumeratedType = getTypeFromJSTypeExpression(texp, registry, null);
      if (howmanyTypeVars > numTypeVars) {
        warnings.add(JSError.make(texp.getRoot(), ENUM_WITH_TYPEVARS));
        enumeratedType = this.commonTypes.UNKNOWN;
        howmanyTypeVars = numTypeVars;
      } else if (enumeratedType.isTop()) {
        warnings.add(JSError.make(texp.getRoot(), ENUM_IS_TOP));
        enumeratedType = this.commonTypes.UNKNOWN;
      } else if (enumeratedType.isUnion()) {
        warnings.add(JSError.make(texp.getRoot(), ENUM_IS_UNION));
        enumeratedType = this.commonTypes.UNKNOWN;
      }
    }
    e.resolveEnum(enumeratedType);
  }

  private void checkInvalidGenericsInstantiation(Node n) {
    if (n.hasChildren()) {
      Preconditions.checkState(n.getFirstChild().isBlock(), n);
      warnings.add(JSError.make(n, INVALID_GENERICS_INSTANTIATION,
              "", "0", String.valueOf(n.getFirstChild().getChildCount())));
    }
  }

  private JSType getNominalTypeHelper(RawNominalType rawType, Node n,
      DeclaredTypeRegistry registry, ImmutableList<String> outerTypeParameters) {
    NominalType uninstantiated = rawType.getAsNominalType();
    if (!rawType.isGeneric() && !n.hasChildren()) {
      return rawType.getInstanceWithNullability(NULLABLE_TYPES_BY_DEFAULT);
    }
    ImmutableList.Builder<JSType> typeList = ImmutableList.builder();
    if (n.hasChildren()) {
      // Compute instantiation of polymorphic class/interface.
      Preconditions.checkState(n.getFirstChild().isBlock(), n);
      for (Node child : n.getFirstChild().children()) {
        typeList.add(
            getTypeFromCommentHelper(child, registry, outerTypeParameters));
      }
    }
    ImmutableList<JSType> typeArguments = typeList.build();
    ImmutableList<String> typeParameters = rawType.getTypeParameters();
    int typeArgsSize = typeArguments.size();
    int typeParamsSize = typeParameters.size();
    if (typeArgsSize != typeParamsSize) {
      // We used to also warn when (typeArgsSize < typeParamsSize), but it
      // happens so often that we stopped. Array, Object and goog.Promise are
      // common culprits, but many other types as well.
      if (typeArgsSize > typeParamsSize) {
        warnings.add(JSError.make(
            n, INVALID_GENERICS_INSTANTIATION,
            " for type " + uninstantiated.getName(),
            String.valueOf(typeParamsSize),
            String.valueOf(typeArgsSize)));
      }
      return maybeMakeNullable(JSType.fromObjectType(ObjectType.fromNominalType(
              uninstantiated.instantiateGenerics(
                  fixLengthOfTypeList(typeParameters.size(), typeArguments)))));
    }
    return maybeMakeNullable(JSType.fromObjectType(ObjectType.fromNominalType(
            uninstantiated.instantiateGenerics(typeArguments))));
  }

  private List<JSType> fixLengthOfTypeList(
      int desiredLength, List<JSType> typeList) {
    int length = typeList.size();
    if (length == desiredLength) {
      return typeList;
    }
    ImmutableList.Builder<JSType> builder = ImmutableList.builder();
    for (int i = 0; i < desiredLength; i++) {
      builder.add(i < length ? typeList.get(i) : this.commonTypes.UNKNOWN);
    }
    return builder.build();
  }

  // Computes a type from a jsdoc that includes a function type, rather than
  // one that includes @param, @return, etc.
  private JSType getFunTypeHelper(
      Node jsdocNode, DeclaredTypeRegistry registry, ImmutableList<String> typeParameters) {
    FunctionTypeBuilder builder = new FunctionTypeBuilder(this.commonTypes);
    fillInFunTypeBuilder(jsdocNode, null, registry, typeParameters, builder);
    return this.commonTypes.fromFunctionType(builder.buildFunction());
  }

  private void fillInFunTypeBuilder(
      Node jsdocNode, RawNominalType ownerType, DeclaredTypeRegistry registry,
      ImmutableList<String> typeParameters, FunctionTypeBuilder builder) {
    Node child = jsdocNode.getFirstChild();
    if (child.getToken() == Token.THIS) {
      if (ownerType == null) {
        builder.addReceiverType(
            getThisOrNewType(child.getFirstChild(), registry, typeParameters));
      }
      child = child.getNext();
    } else if (child.getToken() == Token.NEW) {
      Node newTypeNode = child.getFirstChild();
      JSType t = getThisOrNewType(newTypeNode, registry, typeParameters);
      if (!t.isSubtypeOf(this.commonTypes.TOP_OBJECT)
          && (!t.hasTypeVariable() || t.hasScalar())) {
        warnings.add(JSError.make(
            newTypeNode, NEW_EXPECTS_OBJECT_OR_TYPEVAR, t.toString()));
      }
      builder.addNominalType(t);
      child = child.getNext();
    }
    if (child.getToken() == Token.PARAM_LIST) {
      for (Node arg = child.getFirstChild(); arg != null; arg = arg.getNext()) {
        try {
          switch (arg.getToken()) {
            case EQUALS:
              builder.addOptFormal(getTypeFromCommentHelper(
                  arg.getFirstChild(), registry, typeParameters));
              break;
            case ELLIPSIS:
              Node restNode = arg.getFirstChild();
              builder.addRestFormals(restNode == null ? this.commonTypes.UNKNOWN :
                  getTypeFromCommentHelper(restNode, registry, typeParameters));
              break;
            default:
              builder.addReqFormal(
                  getTypeFromCommentHelper(arg, registry, typeParameters));
              break;
          }
        } catch (FunctionTypeBuilder.WrongParameterOrderException e) {
          warnings.add(JSError.make(jsdocNode, WRONG_PARAMETER_ORDER));
          builder.addPlaceholderFormal();
        }
      }
      child = child.getNext();
    }
    builder.addRetType(
        getTypeFromCommentHelper(child, registry, typeParameters));
  }

  private JSType getThisOrNewType(Node n,
      DeclaredTypeRegistry registry, ImmutableList<String> typeParameters) {
    JSType t = getTypeFromComment(n, registry, typeParameters);
    return t.isSingletonObjWithNull() ? t.removeType(this.commonTypes.NULL) : t;
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
      JSType interfaceType = getTypeFromComment(expRoot, registry, typeParameters);
      NominalType nt = interfaceType.getNominalTypeIfSingletonObj();
      if (nt != null && nt.isInterface()) {
        builder.add(nt);
      } else if (implementedIntfs) {
        warnings.add(JSError.make(
            expRoot, IMPLEMENTS_NON_INTERFACE, interfaceType.toString()));
      } else {
        warnings.add(JSError.make(
            expRoot, EXTENDS_NON_INTERFACE, interfaceType.toString()));
      }
    }
    return builder.build();
  }

  public static class FunctionAndSlotType {
    public JSType slotType;
    public DeclaredFunctionType functionType;

    public FunctionAndSlotType(JSType slotType, DeclaredFunctionType functionType) {
      this.slotType = slotType;
      this.functionType = functionType;
    }
  }

  /**
   * Consumes either a "classic" function jsdoc with @param, @return, etc,
   * or a jsdoc with @type {function ...} and finds the types of the formal
   * parameters and the return value. It returns a builder because the callers
   * of this function must separately handle @constructor, @interface, etc.
   *
   * constructorType is non-null iff this function is a constructor or
   * interface declaration.
   */
  public FunctionAndSlotType getFunctionType(
      JSDocInfo jsdoc, String functionName, Node declNode,
      RawNominalType constructorType, RawNominalType ownerType,
      DeclaredTypeRegistry registry) {
    FunctionTypeBuilder builder = new FunctionTypeBuilder(this.commonTypes);
    if (ownerType != null) {
      builder.addReceiverType(ownerType.getInstanceAsJSType());
    }
    try {
      if (jsdoc != null && jsdoc.getType() != null) {
        JSType simpleType = getDeclaredTypeOfNode(jsdoc, ownerType, registry);
        if (simpleType.isUnknown() || simpleType.isTop()) {
          return qmarkFunctionDeclared;
        }
        FunctionType funType = simpleType.getFunType();
        if (funType != null) {
          JSType slotType = simpleType.isFunctionType() ? null : simpleType;
          DeclaredFunctionType declType = funType.toDeclaredFunctionType();
          if (ownerType != null && funType.getThisType() == null) {
            declType = declType.withReceiverType(ownerType.getInstanceAsJSType());
          }
          return new FunctionAndSlotType(slotType, declType);
        } else {
          warnings.add(JSError.make(declNode, FUNCTION_WITH_NONFUNC_JSDOC));
          jsdoc = null;
        }
      }
      DeclaredFunctionType declType = getFunTypeFromTypicalFunctionJsdoc(
          jsdoc, functionName, declNode,
          constructorType, ownerType, registry, builder);
      return new FunctionAndSlotType(null, declType);
    } catch (FunctionTypeBuilder.WrongParameterOrderException e) {
      warnings.add(JSError.make(declNode, WRONG_PARAMETER_ORDER));
      return qmarkFunctionDeclared;
    }
  }

  private static class ParamIterator {
    /** The parameter names from the JSDocInfo. Only set if 'params' is null. */
    Iterator<String> paramNames;

    /**
     * The PARAM_LIST node containing the function parameters. Only set if
     * 'paramNames' is null.
     */
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
      DeclaredTypeRegistry registry, FunctionTypeBuilder builder) {
    ImmutableList.Builder<String> typeParamsBuilder = ImmutableList.builder();
    ImmutableList<String> typeParameters = ImmutableList.of();
    Node parent = funNode.getParent();

    // TODO(dimvar): need more @template warnings
    // - warn for multiple @template annotations
    // - warn for @template annotation w/out usage

    boolean ignoreJsdoc = false;
    if (jsdoc != null) {
      if (constructorType != null) {
        // We have created new names for these type variables in GTI, don't
        // create new ones here.
        typeParamsBuilder.addAll(constructorType.getTypeParameters());
      } else {
        for (String typeParam : jsdoc.getTemplateTypeNames()) {
          typeParamsBuilder.add(this.nameGen.getNextName(typeParam));
        }
      }
      // We don't properly support the type transformation language; we treat
      // its type variables as ordinary type variables.
      for (String typeParam : jsdoc.getTypeTransformations().keySet()) {
        typeParamsBuilder.add(this.nameGen.getNextName(typeParam));
      }
      typeParameters = typeParamsBuilder.build();
      if (!typeParameters.isEmpty()) {
        if (parent.isSetterDef() || parent.isGetterDef()) {
          ignoreJsdoc = true;
          jsdoc = null;
          warnings.add(JSError.make(funNode, TEMPLATED_GETTER_SETTER));
        } else {
          builder.addTypeParameters(typeParameters);
        }
      }
    }
    if (ownerType != null) {
      typeParamsBuilder.addAll(ownerType.getTypeParameters());
      typeParameters = typeParamsBuilder.build();
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
    if (constructorType == null && jsdoc.isConstructorOrInterface()) {
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

    if (jsdoc.hasThisType()) {
      Node thisRoot = jsdoc.getThisType().getRoot();
      Preconditions.checkState(thisRoot.getToken() == Token.BANG);
      builder.addReceiverType(
          getThisOrNewType(thisRoot.getFirstChild(), registry, typeParameters));
    }

    builder.addAbstract(jsdoc.isAbstract());
    return builder.buildDeclaration();
  }

  private void fillInFormalParameterTypes(
      JSDocInfo jsdoc, Node funNode,
      ImmutableList<String> typeParameters,
      DeclaredTypeRegistry registry, FunctionTypeBuilder builder,
      boolean ignoreJsdoc /* for when the jsdoc is malformed */) {
    boolean ignoreFunNode  = !funNode.isFunction();
    Node params = ignoreFunNode ? null : funNode.getSecondChild();
    ParamIterator iterator = new ParamIterator(params, jsdoc);
    while (iterator.hasNext()) {
      String pname = iterator.nextString();
      Node param = iterator.getNode();
      ParameterKind p = ParameterKind.REQUIRED;
      if (param != null && convention.isOptionalParameter(param)) {
        p = ParameterKind.OPTIONAL;
      } else if (param != null && convention.isVarArgsParameter(param)) {
        p = ParameterKind.REST;
      }
      ParameterType inlineParamType = (ignoreJsdoc || ignoreFunNode || param.getJSDocInfo() == null)
          ? null : parseParameter(param.getJSDocInfo().getType(), p, registry, typeParameters);
      ParameterType fnParamType = inlineParamType;
      JSTypeExpression jsdocExp = jsdoc == null ? null : jsdoc.getParameterType(pname);
      if (jsdocExp != null) {
        if (inlineParamType == null) {
          fnParamType = parseParameter(jsdocExp, p, registry, typeParameters);
        } else {
          warnings.add(JSError.make(
              param, TWO_JSDOCS, "formal parameter " + pname));
        }
      }
      JSType t  = null;
      if (fnParamType != null) {
        p = fnParamType.kind;
        t = fnParamType.type;
      }
      switch (p) {
          case REQUIRED:
            builder.addReqFormal(t);
            break;
          case OPTIONAL:
            builder.addOptFormal(t);
            break;
          case REST:
            builder.addRestFormals(t != null ? t : this.commonTypes.UNKNOWN);
            break;
      }
    }
  }

  private void fillInReturnType(
      JSDocInfo jsdoc, Node funNode, Node parent,
      ImmutableList<String> typeParameters,
      DeclaredTypeRegistry registry, FunctionTypeBuilder builder,
      boolean ignoreJsdoc /* for when the jsdoc is malformed */) {
    JSDocInfo inlineRetJsdoc = ignoreJsdoc || !funNode.isFunction()
        ? null : funNode.getFirstChild().getJSDocInfo();
    JSTypeExpression retTypeExp = jsdoc == null ? null : jsdoc.getReturnType();
    if (parent.isSetterDef() && retTypeExp == null) {
      // inline returns for getters/setters are not parsed
      builder.addRetType(this.commonTypes.UNDEFINED);
    } else if (inlineRetJsdoc != null) {
      builder.addRetType(
          getDeclaredTypeOfNode(inlineRetJsdoc, registry, typeParameters));
      if (retTypeExp != null) {
        warnings.add(JSError.make(funNode, TWO_JSDOCS, "the return type"));
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
    JSType extendedType = getTypeFromComment(docNode, registry, typeParameters);
    NominalType parentClass = extendedType.getNominalTypeIfSingletonObj();
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
    NominalType builtinObject = this.commonTypes.getObjectType();
    if (parentClass == null && !functionName.equals("Object")) {
      parentClass = builtinObject;
    }
    if (parentClass != null && !constructorType.addSuperClass(parentClass)) {
      warnings.add(JSError.make(funNode, INHERITANCE_CYCLE, className));
    }
    if (constructorType.isDict() && !implementedIntfs.isEmpty()) {
      warnings.add(JSError.make(funNode, DICT_IMPLEMENTS_INTERF, className));
    }
    boolean noCycles = constructorType.addInterfaces(implementedIntfs);
    Preconditions.checkState(noCycles);
    builder.addNominalType(constructorType.getInstanceAsJSType());
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
    ImmutableSet<NominalType> extendedInterfaces =
        getExtendedInterfaces(jsdoc, registry, typeParameters);
    boolean noCycles = constructorType.addInterfaces(
        extendedInterfaces.isEmpty()
        ? ImmutableSet.of(this.commonTypes.getObjectType())
        : extendedInterfaces);
    if (!noCycles) {
      warnings.add(JSError.make(
          funNode, INHERITANCE_CYCLE, constructorType.toString()));
    }
    builder.addNominalType(constructorType.getInstanceAsJSType());
  }

  // /** @param {...?} var_args */ function f(var_args) { ... }
  // var_args shouldn't be used in the body of f
  public static boolean isRestArg(JSDocInfo funJsdoc, String formalParamName) {
    if (funJsdoc == null) {
      return false;
    }
    JSTypeExpression texp = funJsdoc.getParameterType(formalParamName);
    Node jsdocNode = texp == null ? null : texp.getRoot();
    return jsdocNode != null && jsdocNode.getToken() == Token.ELLIPSIS;
  }

  private ParameterType parseParameter(
      JSTypeExpression jsdoc, ParameterKind p,
      DeclaredTypeRegistry registry, ImmutableList<String> typeParameters) {
    if (jsdoc == null) {
      return null;
    }
    return parseParameter(jsdoc.getRoot(), p, registry, typeParameters);
  }

  private ParameterType parseParameter(
      Node jsdoc, ParameterKind p,
      DeclaredTypeRegistry registry, ImmutableList<String> typeParameters) {
    if (jsdoc == null) {
      return null;
    }
    switch (jsdoc.getToken()) {
      case EQUALS:
        p = ParameterKind.OPTIONAL;
        jsdoc = jsdoc.getFirstChild();
        break;
      case ELLIPSIS:
        p = ParameterKind.REST;
        jsdoc = jsdoc.getFirstChild();
        break;
      default:
        break;
    }
    JSType t = getTypeFromComment(jsdoc, registry, typeParameters);
    return new ParameterType(t, p);
  }

  private static class ParameterType {
    private JSType type;
    private ParameterKind kind;

    ParameterType(JSType type, ParameterKind kind) {
      this.type = type;
      this.kind = kind;
    }
  }

  private static enum ParameterKind {
    REQUIRED,
    OPTIONAL,
    REST,
  }
}
