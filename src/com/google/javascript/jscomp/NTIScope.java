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

package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.newtypes.Declaration;
import com.google.javascript.jscomp.newtypes.DeclaredFunctionType;
import com.google.javascript.jscomp.newtypes.DeclaredTypeRegistry;
import com.google.javascript.jscomp.newtypes.EnumType;
import com.google.javascript.jscomp.newtypes.FunctionNamespace;
import com.google.javascript.jscomp.newtypes.JSType;
import com.google.javascript.jscomp.newtypes.JSTypeCreatorFromJSDoc;
import com.google.javascript.jscomp.newtypes.JSTypes;
import com.google.javascript.jscomp.newtypes.Namespace;
import com.google.javascript.jscomp.newtypes.NamespaceLit;
import com.google.javascript.jscomp.newtypes.QualifiedName;
import com.google.javascript.jscomp.newtypes.RawNominalType;
import com.google.javascript.jscomp.newtypes.Typedef;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.TypeIEnv;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
final class NTIScope implements DeclaredTypeRegistry, Serializable, TypeIEnv<JSType> {

  static enum VarKind {
    DECLARED,
    INFERRED
  }

  static class LocalVarInfo implements Serializable {
    // When we don't know the type of a local variable, this field is null, not ?.
    private final JSType type;
    private final VarKind kind;
    // Whether this variable is referenced in other scopes.
    private final boolean escapes;

    private LocalVarInfo(JSType type, VarKind kind, boolean escapes) {
      this.type = type;
      this.kind = kind;
      this.escapes = escapes;
    }

    static LocalVarInfo makeDeclared(JSType t) {
      return new LocalVarInfo(t, VarKind.DECLARED, false);
    }

    LocalVarInfo withEscaped() {
      return new LocalVarInfo(this.type, this.kind, true);
    }

    JSType getInferredType() {
      return this.kind == VarKind.INFERRED ? type : null;
    }

    JSType getDeclaredType() {
      return this.kind == VarKind.DECLARED ? type : null;
    }

    @Override
    public String toString() {
      return "LocalVarInfo(" + this.type + "," + this.kind + "," + this.escapes + ")";
    }
  }

  private final NTIScope parent;
  private final Node root;
  // Name on the function AST node; null for top scope & anonymous functions
  private final String name;
  private final JSTypes commonTypes;
  // Becomes true after freezeScope is run; so it's true during NTI.
  private boolean isFrozen = false;

  private final Map<String, LocalVarInfo> locals = new LinkedHashMap<>();
  private final Map<String, JSType> externs;
  private final Set<String> constVars = new LinkedHashSet<>();
  private final List<String> formals;
  // outerVars are the variables that appear free in this scope
  // and are defined in an outer scope.
  private final Set<String> outerVars = new LinkedHashSet<>();
  // When a function is also used as a namespace, we add entries to both
  // localFunDefs and localNamespaces. After freezeScope (when NTI runs),
  // the function has an entry in localFunDefs, and in locals or externs.
  private final Map<String, NTIScope> localFunDefs = new LinkedHashMap<>();
  private ImmutableSet<String> unknownTypeNames = ImmutableSet.of();
  private final Map<String, Typedef> localTypedefs = new LinkedHashMap<>();
  // Typedefs defined inside this scope, but on a namespace, not as local variables
  private Set<Typedef> namespaceTypedefs = new LinkedHashSet<>();
  private Map<String, Namespace> localNamespaces = new LinkedHashMap<>();
  // The namespace map that we preserve post-finalization, purely for use
  // in GlobalTypeInfo for symbol table purposes.
  private Map<String, Namespace> preservedNamespaces;
  // The set localEnums is used for enum resolution, and then discarded.
  private Set<EnumType> localEnums = new LinkedHashSet<>();

  // For top level, the DeclaredFunctionType just includes a type for THIS.
  // For functions, the DeclaredFunctionType is never null, even those without jsdoc.
  // Any inferred parameters or return will be set to null individually.
  private DeclaredFunctionType declaredType;
  // This field is used to typecheck the body of a function that uses TTL.
  // We instantiate the TTL variables to ?.
  // If a function does not use TTL, this field has the same value as declaredType.
  // TODO(dimvar): instead, instantiate the non-TTL generics to ? and evaluate the TTL variables.
  private DeclaredFunctionType declaredTypeForOwnBody;

  NTIScope(Node root, NTIScope parent, List<String> formals, JSTypes commonTypes) {
    checkNotNull(commonTypes);
    if (parent == null) {
      this.name = null;
      this.externs = new LinkedHashMap<>();
    } else {
      String nameOnAst = root.getFirstChild().getString();
      this.name = nameOnAst.isEmpty() ? null : nameOnAst;
      this.externs = ImmutableMap.of();
    }
    this.root = root;
    this.parent = parent;
    this.formals = formals;
    this.commonTypes = commonTypes;
  }

  Node getRoot() {
    return this.root;
  }

  NTIScope getParent() {
    return this.parent;
  }

  Node getBody() {
    checkArgument(root.isFunction());
    return NodeUtil.getFunctionBody(root);
  }

  /** Used only for error messages; null for top scope */
  String getReadableName() {
    // TODO(dimvar): don't return null for anonymous functions
    return isTopLevel() ? null : NodeUtil.getName(root);
  }

  String getName() {
    return name;
  }

  @Override
  public JSTypes getCommonTypes() {
    return this.commonTypes;
  }

  void setDeclaredType(DeclaredFunctionType declaredType) {
    checkNotNull(declaredType);
    this.declaredType = this.declaredTypeForOwnBody = declaredType;
    // In NTI, we set the type of a function node after we create the summary.
    // NTI doesn't analyze externs, so we set the type for extern functions here.
    if (this.root.isFromExterns()) {
      this.root.setTypeI(this.commonTypes.fromFunctionType(declaredType.toFunctionType()));
    }
    if (!declaredType.getTypeParameters().getTypeTransformations().isEmpty()) {
      this.declaredTypeForOwnBody = declaredType.instantiateGenericsWithUnknown();
    }
  }

  @Override
  public DeclaredFunctionType getDeclaredFunctionType() {
    return this.declaredType;
  }

  public DeclaredFunctionType getDeclaredTypeForOwnBody() {
    return this.declaredTypeForOwnBody;
  }

  boolean isFunction() {
    return root.isFunction();
  }

  boolean isTopLevel() {
    return parent == null;
  }

  boolean isConstructor() {
    JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(root);
    return isFunction() && jsdoc != null && jsdoc.isConstructor();
  }

  boolean isInterface() {
    JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(root);
    return isFunction() && jsdoc != null && jsdoc.isInterface();
  }

  boolean isPrototypeMethod() {
    checkArgument(root != null);
    return NodeUtil.isPrototypeMethod(root);
  }

  void addUnknownTypeNames(Set<String> names) {
    // TODO(dimvar): if sm uses a goog.forwardDeclare in a local scope, give
    // an error instead of crashing.
    checkState(this.isTopLevel());
    this.unknownTypeNames = ImmutableSet.copyOf(names);
  }

  void addLocalFunDef(String name, NTIScope scope) {
    checkArgument(!name.isEmpty());
    checkArgument(!name.contains("."));
    checkArgument(!isDefinedLocally(name, false));
    localFunDefs.put(name, scope);
  }

  boolean isFormalParam(String name) {
    return formals.contains(name);
  }

  boolean isFormalParamInAnyAncestorScope(String name) {
    return isFormalParam(name)
        || (this.parent != null && this.parent.isFormalParamInAnyAncestorScope(name));
  }

  boolean isLocalFunDef(String name) {
    return localFunDefs.containsKey(name);
  }

  boolean isFunctionNamespace(String name) {
    checkArgument(!name.contains("."));
    checkState(isFrozen);
    Declaration d = getDeclaration(name, false);
    if (d == null || d.getFunctionScope() == null || d.getTypeOfSimpleDecl() == null) {
      return false;
    }
    return d.getTypeOfSimpleDecl().isNamespace();
  }

  // In other languages, type names and variable names are in distinct
  // namespaces and don't clash.
  // But because our typedefs and enums are var declarations, they are in the
  // same namespace as other variables.
  boolean isDefinedLocally(String name, boolean includeTypes) {
    checkNotNull(name);
    checkState(!name.contains("."));
    if (locals.containsKey(name)
        || formals.contains(name)
        || localNamespaces.containsKey(name)
        || localFunDefs.containsKey(name)
        || "this".equals(name)
        || externs.containsKey(name)
        || localTypedefs.containsKey(name)) {
      return true;
    }
    if (includeTypes) {
      return unknownTypeNames.contains(name)
          || (declaredType != null && declaredType.isTypeVariableDefinedLocally(name));
    }
    return false;
  }

  boolean isDefined(Node qnameNode) {
    checkArgument(qnameNode.isQualifiedName());
    return isDefined(QualifiedName.fromNode(qnameNode));
  }

  // For variables it is the same as isDefinedLocally; for properties it looks
  // for a definition in any scope.
  boolean isDefined(QualifiedName qname) {
    String leftmost = qname.getLeftmostName();
    if (qname.isIdentifier()) {
      return leftmost.equals("this") || isDefinedLocally(leftmost, false);
    }
    if (isNamespace(leftmost)) {
      return getNamespace(leftmost).isDefined(qname.getAllButLeftmost());
    }
    return parent == null ? false : parent.isDefined(qname);
  }

  boolean isNamespace(Node expr) {
    if (expr.isName()) {
      return isNamespace(expr.getString());
    }
    if (!expr.isGetProp()) {
      return false;
    }
    return isNamespace(QualifiedName.fromNode(expr));
  }

  boolean isNamespace(QualifiedName qname) {
    if (qname == null) {
      return false;
    }
    String leftmost = qname.getLeftmostName();
    return isNamespace(leftmost)
        && (qname.isIdentifier()
            || getNamespace(leftmost).hasSubnamespace(qname.getAllButLeftmost()));
  }

  boolean isNamespace(String name) {
    checkArgument(!name.contains("."));
    Declaration decl = getDeclaration(name, false);
    if (decl == null) {
      return false;
    }
    JSType simpleType = decl.getTypeOfSimpleDecl();
    return decl.getNamespace() != null || (simpleType != null && simpleType.isNamespace());
  }

  boolean isVisibleInScope(String name) {
    checkArgument(!name.contains("."));
    return isDefinedLocally(name, false)
        || name.equals(this.name)
        || (parent != null && parent.isVisibleInScope(name));
  }

  boolean isConstVar(String name) {
    checkArgument(!name.contains("."));
    Declaration decl = getDeclaration(name, false);
    return decl != null && decl.isConstant();
  }

  boolean isOuterVarEarly(String name) {
    checkArgument(!name.contains("."));
    return !isDefinedLocally(name, false)
        && parent != null && parent.isVisibleInScope(name);
  }

  boolean isGlobalVar(String varName) {
    NTIScope s = this;
    while (s.parent != null) {
      if (s.isDefinedLocally(varName, false)) {
        return false;
      }
      s = s.parent;
    }
    return true;
  }

  boolean isUndeclaredFormal(String name) {
    checkArgument(!name.contains("."));
    return formals.contains(name) && getDeclaredTypeOf(name) == null;
  }

  List<String> getFormals() {
    return new ArrayList<>(formals);
  }

  Set<String> getOuterVars() {
    return new LinkedHashSet<>(outerVars);
  }

  ImmutableSet<String> getLocalFunDefs() {
    return ImmutableSet.copyOf(localFunDefs.keySet());
  }

  boolean isOuterVar(String name) {
    return outerVars.contains(name);
  }

  boolean isUndeclaredOuterVar(String name) {
    return outerVars.contains(name) && getDeclaredTypeOf(name) == null;
  }

  boolean isEscapedVar(String name) {
    LocalVarInfo info = this.locals.get(name);
    return info != null && info.escapes;
  }

  boolean hasThis() {
    DeclaredFunctionType dft = this.declaredType;
    // dft is null early during GlobalTypeInfo
    return dft != null && dft.getThisType() != null;
  }

  /**
   * Returns the inferred type of {@code name}.
   * Only for names declared with VAR (and let in the future), not for other kinds of bound names.
   */
  JSType getInferredTypeOf(String name) {
    if (this.locals.containsKey(name)) {
      return this.locals.get(name).getInferredType();
    }
    return parent == null ? null : parent.getInferredTypeOf(name);
  }

  @Override
  public JSType getDeclaredTypeOf(String name) {
    checkArgument(!name.contains("."));
    if ("this".equals(name)) {
      if (!hasThis()) {
        return null;
      }
      return getDeclaredTypeForOwnBody().getThisType();
    }
    Declaration decl = getLocalDeclaration(name, false);
    if (decl != null) {
      if (decl.getTypeOfSimpleDecl() != null) {
        Preconditions.checkState(!decl.getTypeOfSimpleDecl().isBottom(),
            "%s was bottom", name);
        return decl.getTypeOfSimpleDecl();
      }
      NTIScope funScope = (NTIScope) decl.getFunctionScope();
      if (funScope != null) {
        Preconditions.checkNotNull(
            funScope.getDeclaredFunctionType(),
            "decl=%s, funScope=%s", decl, funScope);
        return this.commonTypes.fromFunctionType(
            funScope.getDeclaredFunctionType().toFunctionType());
      }
      checkState(decl.getNamespace() == null);
      return null;
    }
    // When a function is a namespace, the parent scope has a better type.
    if (name.equals(this.name) && !parent.isFunctionNamespace(name)) {
      return this.commonTypes.fromFunctionType(getDeclaredFunctionType().toFunctionType());
    }
    return parent == null ? null : parent.getDeclaredTypeOf(name);
  }

  boolean hasUndeclaredFormalsOrOuters() {
    for (String formal : formals) {
      if (getDeclaredTypeOf(formal) == null) {
        return true;
      }
    }
    for (String outer : outerVars) {
      JSType declType = getDeclaredTypeOf(outer);
      if (declType == null
          // Undeclared functions have a non-null declared type,
          //  but they always have a return type of unknown
          || (declType.getFunType() != null
              && !declType.getFunType().isSomeConstructorOrInterface()
              && declType.getFunType().getReturnType().isUnknown())) {
        return true;
      }
    }
    return false;
  }

  private NTIScope getScopeHelper(QualifiedName qname) {
    Declaration decl = getDeclaration(qname, false);
    return decl == null ? null : (NTIScope) decl.getFunctionScope();
  }

  boolean isKnownFunction(String fnName) {
    checkArgument(!fnName.contains("."));
    return getScopeHelper(new QualifiedName(fnName)) != null;
  }

  boolean isKnownFunction(QualifiedName qname) {
    return getScopeHelper(qname) != null;
  }

  boolean isExternalFunction(String fnName) {
    NTIScope s = getScopeHelper(new QualifiedName(fnName));
    return s.root.isFromExterns();
  }

  NTIScope getScope(String fnName) {
    NTIScope s = getScopeHelper(new QualifiedName(fnName));
    checkState(s != null);
    return s;
  }

  ImmutableSet<String> getLocals() {
    return ImmutableSet.copyOf(locals.keySet());
  }

  ImmutableSet<String> getExterns() {
    return ImmutableSet.copyOf(externs.keySet());
  }

  // We don't check for duplicates here, mainly because we add some
  // intentionally during the two phases of GlobalTypeInfo.
  // If a variable is declared many times in a scope, the last definition
  // overwrites the previous ones. For correctness, we rely on the fact that
  // the var-check passes run before type checking.
  void addDeclaredLocal(String name, JSType type, boolean isConstant, boolean isFromExterns) {
    checkArgument(!name.contains("."));
    if (isConstant) {
      constVars.add(name);
    }
    if (isFromExterns) {
      externs.put(name, type);
    } else {
      LocalVarInfo info = this.locals.get(name);
      this.locals.put(
          name, new LocalVarInfo(type, VarKind.DECLARED, info != null && info.escapes));
    }
  }

  void addInferredLocal(String name, JSType type) {
    checkArgument(!name.contains("."));
    LocalVarInfo info = this.locals.get(name);
    this.locals.put(name, new LocalVarInfo(type, VarKind.INFERRED, info != null && info.escapes));
  }

  void clearInferredTypeOfVar(String name) {
    LocalVarInfo info = this.locals.get(name);
    if (info != null && info.getInferredType() != null) {
      this.locals.put(name, new LocalVarInfo(null, VarKind.INFERRED, info.escapes));
    } else if (!isDefinedLocally(name, false) && this.parent != null) {
      this.parent.clearInferredTypeOfVar(name);
    }
  }

  static void mayRecordEscapedVar(NTIScope s, String name) {
    if (s.isDefinedLocally(name, false)) {
      return;
    }
    while (s != null) {
      if (s.isDefinedLocally(name, false)) {
        LocalVarInfo info = s.locals.get(name);
        if (info != null) {
          s.locals.put(name, info.withEscaped());
        }
        return;
      }
      s = s.parent;
    }
  }

  RawNominalType getNominalType(QualifiedName qname) {
    Declaration decl = getDeclaration(qname, false);
    return decl == null ? null : decl.getNominal();
  }

  Typedef getTypedef(String name) {
    return getTypedef(QualifiedName.fromQualifiedString(name));
  }

  Typedef getTypedef(QualifiedName qname) {
    Declaration decl;
    if (qname.isIdentifier()) {
      decl = getDeclaration(qname, true);
    } else {
      Namespace ns = getNamespace(qname.getLeftmostName());
      decl = ns == null ? null : ns.getDeclaration(qname.getAllButLeftmost());
    }
    return decl == null ? null : decl.getTypedef();
  }

  EnumType getEnum(QualifiedName qname) {
    Declaration decl = getDeclaration(qname, false);
    return decl == null ? null : decl.getEnum();
  }

  Namespace getNamespace(String name) {
    checkArgument(!name.contains("."));
    Declaration decl = getDeclaration(name, false);
    return decl == null ? null : decl.getNamespace();
  }

  void addFunNamespace(Node qnameNode) {
    if (qnameNode.isName()) {
      String varName = qnameNode.getString();
      checkArgument(isDefinedLocally(varName, false));
      checkState(!this.localNamespaces.containsKey(varName));
      NTIScope s = checkNotNull(this.localFunDefs.get(varName));
      this.localNamespaces.put(varName,
          new FunctionNamespace(this.commonTypes, varName, s, qnameNode));
    } else {
      checkArgument(!isNamespace(qnameNode));
      QualifiedName qname = QualifiedName.fromNode(qnameNode);
      Namespace ns = getNamespace(qname.getLeftmostName());
      NTIScope s = (NTIScope) ns.getDeclaration(qname).getFunctionScope();
      ns.addNamespace(qname.getAllButLeftmost(),
          new FunctionNamespace(this.commonTypes, qname.toString(), s, qnameNode));
    }
  }

  void addNamespaceLit(QualifiedName qname, Node defSite) {
    addNamespace(qname, defSite, new NamespaceLit(this.commonTypes, qname.toString(), defSite));
  }

  void addOuterVar(String name) {
    outerVars.add(name);
  }

  void addTypedef(Node qnameNode, Typedef td) {
    if (qnameNode.isName()) {
      checkState(!localTypedefs.containsKey(qnameNode.getString()));
      localTypedefs.put(qnameNode.getString(), td);
    } else {
      checkState(!isDefined(qnameNode));
      QualifiedName qname = QualifiedName.fromNode(qnameNode);
      Namespace ns = getNamespace(qname.getLeftmostName());
      ns.addTypedef(qname.getAllButLeftmost(), td);
      namespaceTypedefs.add(td);
    }
  }

  void addNamespace(Node qnameNode, Namespace ns) {
    addNamespace(QualifiedName.fromNode(qnameNode), qnameNode, ns);
  }

  void addNamespace(QualifiedName qname, Node defSite, Namespace ns) {
    if (ns instanceof EnumType) {
      this.localEnums.add((EnumType) ns);
    }
    if (qname.isIdentifier()) {
      String varName = qname.getLeftmostName();
      Preconditions.checkState(!this.localNamespaces.containsKey(varName),
          "Namespace %s already defined.", varName);
      this.localNamespaces.put(varName, ns);
      if (defSite.isFromExterns() && !this.externs.containsKey(varName)) {
        // We don't know the full type of a namespace until after we see all
        // its properties. But we want to add it to the externs, otherwise it
        // is treated as a local and initialized to the wrong thing in NTI.
        this.externs.put(varName, null);
      }
    } else {
      checkState(!isDefined(qname));
      Namespace rootns = getNamespace(qname.getLeftmostName());
      rootns.addNamespace(qname.getAllButLeftmost(), ns);
    }
  }

  Namespace getNamespace(QualifiedName qname) {
    Namespace ns = getNamespace(qname.getLeftmostName());
    return (ns == null || qname.isIdentifier())
        ? ns : ns.getSubnamespace(qname.getAllButLeftmost());
  }

  private Declaration getLocalDeclaration(String name, boolean includeTypes) {
    checkArgument(!name.contains("."));
    if (!isDefinedLocally(name, includeTypes)) {
      return null;
    }
    DeclaredFunctionType declaredType = getDeclaredTypeForOwnBody();
    JSType type = null;
    boolean isTypeVar = false;
    if ("this".equals(name)) {
      type = getDeclaredTypeOf("this");
    } else if (locals.containsKey(name)) {
      type = locals.get(name).getDeclaredType();
    } else if (formals.contains(name)) {
      int formalIndex = formals.indexOf(name);
      if (declaredType != null && formalIndex != -1) {
        JSType formalType = declaredType.getFormalType(formalIndex);
        if (formalType != null && !formalType.isBottom()) {
          type = formalType;
        }
      }
    } else if (localFunDefs.containsKey(name)) {
      // After finalization, the externs contain the correct type for
      // external function namespaces, don't rely on localFunDefs
      if (isFrozen && externs.containsKey(name)) {
        type = externs.get(name);
      }
    } else if (localTypedefs.containsKey(name) || localNamespaces.containsKey(name)) {
      // Any further declarations are shadowed
    } else if (declaredType != null && declaredType.isTypeVariableDefinedLocally(name)) {
      isTypeVar = true;
      type = JSType.fromTypeVar(this.commonTypes, declaredType.getTypeVariableDefinedLocally(name));
    } else if (externs.containsKey(name)) {
      type = externs.get(name);
    }
    Namespace ns = null;
    if (localNamespaces.containsKey(name)) {
      ns = localNamespaces.get(name);
    } else if (preservedNamespaces != null) {
      ns = preservedNamespaces.get(name);
    }

    return new Declaration(type, localTypedefs.get(name),
        ns, localFunDefs.get(name), isTypeVar,
        constVars.contains(name));
  }

  @Override
  public Declaration getDeclaration(QualifiedName qname, boolean includeTypes) {
    if (qname.isIdentifier()) {
      return getDeclaration(qname.getLeftmostName(), includeTypes);
    }
    Namespace ns = getNamespace(qname.getLeftmostName());
    if (ns == null) {
      return maybeGetForwardDeclaration(qname.toString());
    }
    Declaration decl = ns.getDeclaration(qname.getAllButLeftmost());
    return decl != null ? decl : maybeGetForwardDeclaration(qname.toString());
  }

  private Declaration maybeGetForwardDeclaration(String qname) {
    NTIScope globalScope = this;
    while (globalScope.parent != null) {
      globalScope = globalScope.parent;
    }
    if (globalScope.unknownTypeNames.contains(qname)) {
      return new Declaration(this.commonTypes.UNKNOWN, null, null, null, false, false);
    }
    return null;
  }

  public Declaration getDeclaration(String name, boolean includeTypes) {
    checkArgument(!name.contains("."));
    Declaration decl = getLocalDeclaration(name, includeTypes);
    if (decl != null) {
      return decl;
    }
    return parent == null ? null : parent.getDeclaration(name, includeTypes);
  }

  private Namespace getNamespaceAfterFreezing(String typeName) {
    checkNotNull(preservedNamespaces, "Failed to preserve namespaces post-finalization");
    QualifiedName qname = QualifiedName.fromQualifiedString(typeName);
    Namespace ns = preservedNamespaces.get(qname.getLeftmostName());
    if (ns != null && !qname.isIdentifier()) {
      ns = ns.getSubnamespace(qname.getAllButLeftmost());
    }
    return ns;
  }

  /**
   * Given the name of a namespace that is a nominal type, returns an instance of that type.
   * Given the name of another namespace, returns the namespace type.
   */
  public JSType getType(String typeName) {
    Namespace ns = getNamespaceAfterFreezing(typeName);
    if (ns == null) {
      return null;
    }
    return ns instanceof RawNominalType
        ? ((RawNominalType) ns).getInstanceAsJSType()
        : ns.toJSType();
  }

  @Override
  public JSType getNamespaceOrTypedefType(String typeName) {
    Namespace ns = getNamespaceAfterFreezing(typeName);
    if (ns != null) {
      return ns.toJSType();
    }
    Typedef td = getTypedef(typeName);
    return td == null ? null : td.getType();
  }

  @Override
  public JSDocInfo getJsdocOfTypeDeclaration(String typeName) {
    JSType t = getType(typeName);
    if (t != null) {
      Node defSite = t.getSource();
      if (defSite != null) {
        return NodeUtil.getBestJSDocInfo(defSite);
      }
    }
    return null;
  }

  void resolveTypedefs(JSTypeCreatorFromJSDoc typeParser) {
    for (Typedef td : this.localTypedefs.values()) {
      typeParser.resolveTypedef(td, this);
    }
    for (Typedef td : this.namespaceTypedefs) {
      typeParser.resolveTypedef(td, this);
    }
    this.namespaceTypedefs = null;
  }

  void resolveEnums(JSTypeCreatorFromJSDoc typeParser) {
    for (EnumType e : this.localEnums) {
      typeParser.resolveEnum(e, this);
    }
    this.localEnums = null;
  }

  void freezeScope() {
    Preconditions.checkNotNull(this.declaredType, "No declared type for scope: %s", this.root);
    unknownTypeNames = ImmutableSet.of();
    // For now, we put types of namespaces directly into the locals.
    // Alternatively, we could move this into NewTypeInference.initEdgeEnvs
    for (Map.Entry<String, Namespace> entry : localNamespaces.entrySet()) {
      String name = entry.getKey();
      Namespace ns = entry.getValue();
      if (ns instanceof NamespaceLit) {
        constVars.add(name);
      }
      JSType t = ns.toJSType();
      if (externs.containsKey(name)) {
        externs.put(name, t);
      } else {
        locals.put(name, LocalVarInfo.makeDeclared(t));
      }
    }
    for (String typedefName : localTypedefs.keySet()) {
      locals.put(typedefName, LocalVarInfo.makeDeclared(this.commonTypes.UNDEFINED));
    }
    copyOuterVarsTransitively(this);
    preservedNamespaces = localNamespaces;
    localNamespaces = ImmutableMap.of();
    isFrozen = true;
  }

  // A scope must know about the free variables used in outer scopes,
  // otherwise we end up with invalid type envs.
  private static void copyOuterVarsTransitively(NTIScope s) {
    if (s.isTopLevel()) {
      return;
    }
    NTIScope parent = s.parent;
    Set<String> outerVars = s.outerVars;
    while (parent.isFunction()) {
      boolean copiedOneVar = false;
      for (String v : outerVars) {
        if (!parent.isDefinedLocally(v, false)) {
          copiedOneVar = true;
          parent.addOuterVar(v);
        }
      }
      if (!copiedOneVar) {
        break;
      }
      outerVars = parent.outerVars;
      parent = parent.parent;
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (isTopLevel()) {
      sb.append("<TOP SCOPE>");
    } else {
      sb.append(getReadableName());
      sb.append('(');
      Joiner.on(',').appendTo(sb, formals);
      sb.append(')');
    }
    sb.append(" with root: ");
    sb.append(root);
    return sb.toString();
  }
}
