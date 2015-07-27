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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NewTypeInference.WarningReporter;
import com.google.javascript.jscomp.newtypes.Declaration;
import com.google.javascript.jscomp.newtypes.DeclaredFunctionType;
import com.google.javascript.jscomp.newtypes.DeclaredTypeRegistry;
import com.google.javascript.jscomp.newtypes.EnumType;
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
final class NTIScope implements DeclaredTypeRegistry {
  private final NTIScope parent;
  private final Node root;
  // Name on the function AST node; null for top scope & anonymous functions
  private final String name;
  private final JSTypes commonTypes;
  // Becomes true after removeTmpData is run; so it's true during NTI.
  private boolean isFinalized = false;

  // A local w/out declared type is mapped to null, not to JSType.UNKNOWN.
  private final Map<String, JSType> locals = new LinkedHashMap<>();
  private final Map<String, JSType> externs;
  private final Set<String> constVars = new LinkedHashSet<>();
  private final List<String> formals;
  // outerVars are the variables that appear free in this scope
  // and are defined in an enclosing scope.
  private final Set<String> outerVars = new LinkedHashSet<>();
  // When a function is also used as a namespace, we add entries to both
  // localFunDefs and localNamespaces. After removeTmpData (when NTI runs),
  // the function has an entry in localFunDefs, and in locals or externs.
  private final Map<String, NTIScope> localFunDefs = new LinkedHashMap<>();
  private Set<String> unknownTypeNames = new LinkedHashSet<>();
  private Map<String, RawNominalType> localClassDefs = new LinkedHashMap<>();
  private Map<String, Typedef> localTypedefs = new LinkedHashMap<>();
  private Map<String, EnumType> localEnums = new LinkedHashMap<>();
  private Map<String, NamespaceLit> localNamespaces = new LinkedHashMap<>();
  // The set qualifiedEnums is used for enum resolution, and then discarded.
  private Set<EnumType> qualifiedEnums = new LinkedHashSet<>();

  // declaredType is null for top level, but never null for functions,
  // even those without jsdoc.
  // Any inferred parameters or return will be set to null individually.
  private DeclaredFunctionType declaredType;

  NTIScope(Node root, NTIScope parent, List<String> formals, JSTypes commonTypes) {
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
    Preconditions.checkArgument(root.isFunction());
    return NodeUtil.getFunctionBody(root);
  }

  /** Used only for error messages; null for top scope */
  String getReadableName() {
    // TODO(dimvar): don't return null for anonymous functions
    return isTopLevel() ? null : NodeUtil.getFunctionName(root);
  }

  String getName() {
    return name;
  }

  void setDeclaredType(DeclaredFunctionType declaredType) {
    this.declaredType = declaredType;
    // In NTI, we set the type of a function node after we create the summary.
    // NTI doesn't analyze externs, so we set the type for extern functions here.
    if (this.root.isFromExterns()) {
      this.root.setTypeI(getCommonTypes().fromFunctionType(declaredType.toFunctionType()));
    }
  }

  public DeclaredFunctionType getDeclaredFunctionType() {
    return declaredType;
  }

  boolean isFunction() {
    return root.isFunction();
  }

  boolean isTopLevel() {
    return parent == null;
  }

  boolean isConstructor() {
    if (!root.isFunction()) {
      return false;
    }
    JSDocInfo fnDoc = NodeUtil.getBestJSDocInfo(root);
    return fnDoc != null && fnDoc.isConstructor();
  }

  boolean isPrototypeMethod() {
    Preconditions.checkArgument(root != null);
    return NodeUtil.isPrototypeMethod(root);
  }

  void addUnknownTypeNames(List<String> names) {
    // TODO(dimvar): if sm uses a goog.forwardDeclare in a local scope, give
    // an error instead of crashing.
    Preconditions.checkState(this.isTopLevel());
    unknownTypeNames.addAll(names);
  }

  Set<String> getUnknownTypeNames() {
    Preconditions.checkState(this.isTopLevel());
    return this.unknownTypeNames;
  }

  void addLocalFunDef(String name, NTIScope scope) {
    Preconditions.checkArgument(!name.isEmpty());
    Preconditions.checkArgument(!name.contains("."));
    Preconditions.checkArgument(!isDefinedLocally(name, false));
    localFunDefs.put(name, scope);
  }

  boolean isFormalParam(String name) {
    return formals.contains(name);
  }

  boolean isLocalFunDef(String name) {
    return localFunDefs.containsKey(name);
  }

  boolean isFunctionNamespace(String name) {
    Preconditions.checkArgument(!name.contains("."));
    Preconditions.checkState(isFinalized);
    Declaration d = getDeclaration(name, false);
    if (d == null || d.getFunctionScope() == null || d.getTypeOfSimpleDecl() == null) {
      return false;
    }
    return d.getTypeOfSimpleDecl().getObjTypeIfSingletonObj() != null;
  }

  // In other languages, type names and variable names are in distinct
  // namespaces and don't clash.
  // But because our typedefs and enums are var declarations, they are in the
  // same namespace as other variables.
  boolean isDefinedLocally(String name, boolean includeTypes) {
    Preconditions.checkNotNull(name);
    Preconditions.checkState(!name.contains("."));
    if (locals.containsKey(name) || formals.contains(name)
        || localFunDefs.containsKey(name) || "this".equals(name)
        || externs.containsKey(name)
        || localNamespaces.containsKey(name)
        || localTypedefs.containsKey(name)
        || localEnums.containsKey(name)) {
      return true;
    }
    if (includeTypes) {
      return unknownTypeNames.contains(name)
          || declaredType != null && declaredType.isTypeVariableDefinedLocally(name);
    }
    return false;
  }

  boolean isDefined(Node qnameNode) {
    Preconditions.checkArgument(qnameNode.isQualifiedName());
    if (qnameNode.isName()) {
      return isDefinedLocally(qnameNode.getString(), false);
    } else if (qnameNode.isThis()) {
      return true;
    }
    QualifiedName qname = QualifiedName.fromNode(qnameNode);
    String leftmost = qname.getLeftmostName();
    if (isNamespace(leftmost)) {
      return getNamespace(leftmost).isDefined(qname.getAllButLeftmost());
    }
    return parent == null ? false : parent.isDefined(qnameNode);
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
            || getNamespace(leftmost)
            .hasSubnamespace(qname.getAllButLeftmost()));
  }

  boolean isNamespace(String name) {
    Preconditions.checkArgument(!name.contains("."));
    Declaration decl = getDeclaration(name, false);
    return decl != null && decl.getNamespace() != null;
  }

  boolean isVisibleInScope(String name) {
    Preconditions.checkArgument(!name.contains("."));
    return isDefinedLocally(name, false)
        || name.equals(this.name)
        || (parent != null && parent.isVisibleInScope(name));
  }

  boolean isConstVar(String name) {
    Preconditions.checkArgument(!name.contains("."));
    Declaration decl = getDeclaration(name, false);
    return decl != null && decl.isConstant();
  }

  boolean isOuterVarEarly(String name) {
    Preconditions.checkArgument(!name.contains("."));
    return !isDefinedLocally(name, false)
        && parent != null && parent.isVisibleInScope(name);
  }

  boolean isUndeclaredFormal(String name) {
    Preconditions.checkArgument(!name.contains("."));
    return formals.contains(name) && getDeclaredTypeOf(name) == null;
  }

  List<String> getFormals() {
    return new ArrayList<>(formals);
  }

  Set<String> getOuterVars() {
    return new LinkedHashSet<>(outerVars);
  }

  Set<String> getLocalFunDefs() {
    return ImmutableSet.copyOf(localFunDefs.keySet());
  }

  boolean isOuterVar(String name) {
    return outerVars.contains(name);
  }

  boolean hasThis() {
    return isFunction() && getDeclaredFunctionType().getThisType() != null;
  }

  RawNominalType getNominalType(QualifiedName qname) {
    Declaration decl = getDeclaration(qname, false);
    return decl == null ? null : decl.getNominal();
  }

  @Override
  public JSTypes getCommonTypes() {
    if (isTopLevel()) {
      return commonTypes;
    }
    return parent.getCommonTypes();
  }

  @Override
  public JSType getDeclaredTypeOf(String name) {
    Preconditions.checkArgument(!name.contains("."));
    if ("this".equals(name)) {
      if (!hasThis()) {
        return null;
      }
      return getDeclaredFunctionType().getThisType().getInstanceAsJSType();
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
        return getCommonTypes().fromFunctionType(
            funScope.getDeclaredFunctionType().toFunctionType());
      }
      Preconditions.checkState(decl.getNamespace() == null);
      return null;
    }
    // When a function is a namespace, the parent scope has a better type.
    if (name.equals(this.name) && !parent.isFunctionNamespace(name)) {
      return getCommonTypes()
          .fromFunctionType(getDeclaredFunctionType().toFunctionType());
    }
    if (parent != null) {
      return parent.getDeclaredTypeOf(name);
    }
    return null;
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
    Preconditions.checkArgument(!fnName.contains("."));
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
    Preconditions.checkState(s != null);
    return s;
  }

  Set<String> getLocals() {
    return ImmutableSet.copyOf(locals.keySet());
  }

  Set<String> getExterns() {
    return ImmutableSet.copyOf(externs.keySet());
  }

  // Like addLocal, but used when the type is already defined locally
  void addSimpleType(Node qnameNode, JSType declType) {
    Preconditions.checkState(qnameNode.isName());
    String name = qnameNode.getString();
    if (qnameNode.isFromExterns()) {
      externs.put(name, declType);
    } else {
      locals.put(name, declType);
    }
  }

  void addLocal(String name, JSType declType,
      boolean isConstant, boolean isFromExterns) {
    Preconditions.checkArgument(!name.contains("."));
    Preconditions.checkArgument(!isDefinedLocally(name, false));
    if (isConstant) {
      constVars.add(name);
    }
    if (isFromExterns) {
      externs.put(name, declType);
    } else {
      locals.put(name, declType);
    }
  }

  void addNamespace(Node qnameNode, boolean isFromExterns) {
    Preconditions.checkArgument(!isNamespace(qnameNode));
    if (qnameNode.isName()) {
      String varName = qnameNode.getString();
      localNamespaces.put(varName, new NamespaceLit(varName));
      if (isFromExterns) {
        // We don't know the full type of a namespace until after we see all
        // its properties. But we want to add it to the externs, otherwise it
        // is treated as a local and initialized to the wrong thing in NTI.
        externs.put(qnameNode.getString(), null);
      }
    } else {
      QualifiedName qname = QualifiedName.fromNode(qnameNode);
      Namespace ns = getNamespace(qname.getLeftmostName());
      ns.addSubnamespace(qname.getAllButLeftmost());
    }
  }

  void updateType(String name, JSType newDeclType) {
    if (isDefinedLocally(name, false)) {
      locals.put(name, newDeclType);
    } else if (parent != null) {
      parent.updateType(name, newDeclType);
    } else {
      throw new RuntimeException(
          "Cannot update type of unknown variable: " + name);
    }
  }

  void addOuterVar(String name) {
    outerVars.add(name);
  }

  void addNominalType(Node qnameNode, RawNominalType rawNominalType) {
    if (qnameNode.isName()) {
      Preconditions.checkState(
          !localClassDefs.containsKey(qnameNode.getString()));
      localClassDefs.put(qnameNode.getString(), rawNominalType);
    } else {
      Preconditions.checkArgument(!isDefined(qnameNode));
      QualifiedName qname = QualifiedName.fromNode(qnameNode);
      Namespace ns = getNamespace(qname.getLeftmostName());
      ns.addNominalType(qname.getAllButLeftmost(), rawNominalType);
    }
  }

  void addTypedef(Node qnameNode, Typedef td) {
    if (qnameNode.isName()) {
      Preconditions.checkState(
          !localTypedefs.containsKey(qnameNode.getString()));
      localTypedefs.put(qnameNode.getString(), td);
    } else {
      Preconditions.checkState(!isDefined(qnameNode));
      QualifiedName qname = QualifiedName.fromNode(qnameNode);
      Namespace ns = getNamespace(qname.getLeftmostName());
      ns.addTypedef(qname.getAllButLeftmost(), td);
    }
  }

  Typedef getTypedef(String name) {
    Preconditions.checkState(!name.contains("."));
    Declaration decl = getDeclaration(name, false);
    return decl == null ? null : decl.getTypedef();
  }

  void addEnum(Node qnameNode, EnumType e) {
    if (qnameNode.isName()) {
      Preconditions.checkState(
          !localEnums.containsKey(qnameNode.getString()));
      localEnums.put(qnameNode.getString(), e);
    } else {
      Preconditions.checkState(!isDefined(qnameNode));
      QualifiedName qname = QualifiedName.fromNode(qnameNode);
      Namespace ns = getNamespace(qname.getLeftmostName());
      ns.addEnum(qname.getAllButLeftmost(), e);
      qualifiedEnums.add(e);
    }
  }

  EnumType getEnum(QualifiedName qname) {
    Declaration decl = getDeclaration(qname, false);
    return decl == null ? null : decl.getEnum();
  }

  Namespace getNamespace(QualifiedName qname) {
    Namespace ns = getNamespace(qname.getLeftmostName());
    return qname.isIdentifier()
        ? ns : ns.getSubnamespace(qname.getAllButLeftmost());
  }

  private Declaration getLocalDeclaration(String name, boolean includeTypes) {
    Preconditions.checkArgument(!name.contains("."));
    if (!isDefinedLocally(name, includeTypes)) {
      return null;
    }
    JSType type = null;
    boolean isForwardDeclaration = false;
    boolean isTypeVar = false;
    if (locals.containsKey(name)) {
      type = locals.get(name);
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
      if (isFinalized && externs.containsKey(name)) {
        type = externs.get(name);
      }
    } else if (localTypedefs.containsKey(name) || localNamespaces.containsKey(name)
        || localEnums.containsKey(name) || localClassDefs.containsKey(name)) {
      // Any further declarations are shadowed
    } else if (declaredType != null && declaredType.isTypeVariableDefinedLocally(name)) {
      isTypeVar = true;
      type = JSType.fromTypeVar(name);
    } else if (externs.containsKey(name)) {
      type = externs.get(name);
    } else if (unknownTypeNames.contains(name)) {
      isForwardDeclaration = true;
    }
    return new Declaration(
        type,
        localTypedefs.get(name),
        localNamespaces.get(name),
        localEnums.get(name),
        localFunDefs.get(name),
        localClassDefs.get(name),
        isTypeVar,
        constVars.contains(name),
        isForwardDeclaration);
  }

  public Declaration getDeclaration(QualifiedName qname, boolean includeTypes) {
    if (qname.isIdentifier()) {
      return getDeclaration(qname.getLeftmostName(), includeTypes);
    }
    Preconditions.checkState(!this.isFinalized,
        "Namespaces are removed from scopes after finalization");
    Namespace ns = getNamespace(qname.getLeftmostName());
    if (ns == null) {
      return null;
    }
    Declaration decl = ns.getDeclaration(qname.getAllButLeftmost());
    if (decl == null && unknownTypeNames.contains(qname.toString())) {
      return new Declaration(
          JSType.UNKNOWN, null, null, null, null, null, false, false, true);
    }
    return decl;
  }

  public Declaration getDeclaration(String name, boolean includeTypes) {
    Preconditions.checkArgument(!name.contains("."));
    Declaration decl = getLocalDeclaration(name, includeTypes);
    if (decl != null) {
      return decl;
    }
    return parent == null ? null : parent.getDeclaration(name, includeTypes);
  }

  Namespace getNamespace(String name) {
    Preconditions.checkArgument(!name.contains("."));
    Declaration decl = getDeclaration(name, false);
    return decl == null ? null : decl.getNamespace();
  }

  void resolveTypedefs(JSTypeCreatorFromJSDoc typeParser) {
    for (Typedef td : localTypedefs.values()) {
      if (!td.isResolved()) {
        typeParser.resolveTypedef(td, this);
      }
    }
  }

  void resolveEnums(JSTypeCreatorFromJSDoc typeParser) {
    for (EnumType e : localEnums.values()) {
      if (!e.isResolved()) {
        typeParser.resolveEnum(e, this);
      }
    }
    for (EnumType e : qualifiedEnums) {
      if (!e.isResolved()) {
        typeParser.resolveEnum(e, this);
      }
    }
    qualifiedEnums = null;
  }

  // If cannot declare the type, it returns a Node to be used by GlobalTypeInfo
  // for a warning
  Node mayDeclareUnknownType(QualifiedName qname, WarningReporter warnings) {
    if (qname.isIdentifier() || null == getNamespace(qname.getLeftmostName())) {
      // TODO(dimvar): If the code before the return is deleted, no unit tests fail.
      // That's because we consider the unknown type a global variable, which we type ?.
      // Any reason to keep this code, or just delete it?
      String name = qname.getLeftmostName();
      if (!locals.containsKey(name)) {
        externs.put(name, JSType.UNKNOWN);
      }
      return null;
    }
    Namespace leftmost = getNamespace(qname.getLeftmostName());
    QualifiedName props = qname.getAllButLeftmost();
    // The forward declared type may be on an undeclared namespace.
    // e.g. 'ns.Foo.Bar.Baz' when we don't even have a definition for ns.Foo.
    // Thus, we need to find the prefix of the qname that is not declared.
    while (!props.isIdentifier()
        && !leftmost.hasSubnamespace(props.getAllButRightmost())) {
      props = props.getAllButRightmost();
    }
    Namespace ns = props.isIdentifier()
        ? leftmost : leftmost.getSubnamespace(props.getAllButRightmost());
    if (ns.isNamespaceFinalized()) {
      Preconditions.checkNotNull(ns.getConstDeclNode(),
          "Namespace %s was finalized incorrectly", ns.getName());
      return ns.getConstDeclNode();
    }
    String pname = props.getRightmostName();
    ns.addUndeclaredProperty(pname, null, JSType.UNKNOWN, /* isConst */ false);
    return null;
  }

  void removeTmpData() {
    unknownTypeNames = ImmutableSet.of();
    JSTypes commonTypes = getCommonTypes();
    // For now, we put types of namespaces directly into the locals.
    // Alternatively, we could move this into NewTypeInference.initEdgeEnvs
    for (Map.Entry<String, NamespaceLit> entry : localNamespaces.entrySet()) {
      String name = entry.getKey();
      NamespaceLit nslit = entry.getValue();
      nslit.finalizeNamespace(null);
      JSType t = nslit.toJSType(commonTypes);
      // If it's a function namespace, add the function type to the result
      if (localFunDefs.containsKey(name)) {
        t = t.withFunction(
            localFunDefs.get(name).getDeclaredFunctionType().toFunctionType(),
            commonTypes.getFunctionType());
      }
      if (externs.containsKey(name)) {
        externs.put(name, t);
      } else {
        locals.put(name, t);
      }
    }
    for (Map.Entry<String, EnumType> entry : localEnums.entrySet()) {
      EnumType et = entry.getValue();
      et.finalizeNamespace(null);
      locals.put(entry.getKey(), et.toJSType(commonTypes));
    }
    for (String typedefName : localTypedefs.keySet()) {
      locals.put(typedefName, JSType.UNDEFINED);
    }
    localNamespaces = ImmutableMap.of();
    localClassDefs = ImmutableMap.of();
    localTypedefs = ImmutableMap.of();
    localEnums = ImmutableMap.of();
    isFinalized = true;
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
