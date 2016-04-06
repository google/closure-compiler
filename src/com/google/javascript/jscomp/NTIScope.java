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
  // Becomes true after finalizeScope is run; so it's true during NTI.
  private boolean isFinalized = false;

  // A local w/out declared type is mapped to null, not to JSType.UNKNOWN.
  private final Map<String, JSType> locals = new LinkedHashMap<>();
  private final Map<String, JSType> externs;
  private final Set<String> constVars = new LinkedHashSet<>();
  private final List<String> formals;
  // Variables that are defined in this scope and used in inner scopes.
  private Set<String> escapedVars = new LinkedHashSet<>();
  // outerVars are the variables that appear free in this scope
  // and are defined in an outer scope.
  private final Set<String> outerVars = new LinkedHashSet<>();
  // When a function is also used as a namespace, we add entries to both
  // localFunDefs and localNamespaces. After finalizeScope (when NTI runs),
  // the function has an entry in localFunDefs, and in locals or externs.
  private final Map<String, NTIScope> localFunDefs = new LinkedHashMap<>();
  private ImmutableSet<String> unknownTypeNames = ImmutableSet.of();
  private Map<String, Typedef> localTypedefs = new LinkedHashMap<>();
  private Map<String, Namespace> localNamespaces = new LinkedHashMap<>();
  // The set localEnums is used for enum resolution, and then discarded.
  private Set<EnumType> localEnums = new LinkedHashSet<>();

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
    return isTopLevel() ? null : NodeUtil.getName(root);
  }

  String getName() {
    return name;
  }

  void setDeclaredType(DeclaredFunctionType declaredType) {
    Preconditions.checkNotNull(declaredType);
    this.declaredType = declaredType;
    // In NTI, we set the type of a function node after we create the summary.
    // NTI doesn't analyze externs, so we set the type for extern functions here.
    if (this.root.isFromExterns()) {
      this.root.setTypeI(getCommonTypes().fromFunctionType(declaredType.toFunctionType()));
    }
  }

  @Override
  public DeclaredFunctionType getDeclaredFunctionType() {
    return this.declaredType;
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

  void addUnknownTypeNames(Set<String> names) {
    // TODO(dimvar): if sm uses a goog.forwardDeclare in a local scope, give
    // an error instead of crashing.
    Preconditions.checkState(this.isTopLevel());
    this.unknownTypeNames = ImmutableSet.copyOf(names);
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
    return d.getTypeOfSimpleDecl().isNamespace();
  }

  // In other languages, type names and variable names are in distinct
  // namespaces and don't clash.
  // But because our typedefs and enums are var declarations, they are in the
  // same namespace as other variables.
  boolean isDefinedLocally(String name, boolean includeTypes) {
    Preconditions.checkNotNull(name);
    Preconditions.checkState(!name.contains("."));
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
          || declaredType != null && declaredType.isTypeVariableDefinedLocally(name);
    }
    return false;
  }

  // For variables it is the same as isDefinedLocally; for properties it looks
  // for a definition in any scope.
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
            || getNamespace(leftmost).hasSubnamespace(qname.getAllButLeftmost()));
  }

  boolean isNamespace(String name) {
    Preconditions.checkArgument(!name.contains("."));
    Declaration decl = getDeclaration(name, false);
    if (decl == null) {
      return false;
    }
    JSType simpleType = decl.getTypeOfSimpleDecl();
    return decl.getNamespace() != null
        || simpleType != null && simpleType.isNamespace();
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

  boolean isGlobalVar(String varName) {
    NTIScope s = this;
    while (s.parent != null) {
      if (isDefinedLocally(varName, false)) {
        return false;
      }
      s = s.parent;
    }
    return true;
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

  boolean isUndeclaredOuterVar(String name) {
    return outerVars.contains(name) && getDeclaredTypeOf(name) == null;
  }

  boolean isEscapedVar(String name) {
    return this.escapedVars.contains(name);
  }

  boolean hasThis() {
    if (!isFunction()) {
      return false;
    }
    DeclaredFunctionType dft = getDeclaredFunctionType();
    // dft is null for function scopes early during GlobalTypeInfo
    return dft != null && dft.getThisType() != null;
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
      return getDeclaredFunctionType().getThisType();
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

  // We don't check for duplicates here, mainly because we add some
  // intentionally during the two phases of GlobalTypeInfo.
  // If a variable is declared many times in a scope, the last definition
  // overwrites the previous ones. For correctness, we rely on the fact that
  // the var-check passes run before type checking.
  void addLocal(String name, JSType declType,
      boolean isConstant, boolean isFromExterns) {
    Preconditions.checkArgument(!name.contains("."));
    if (isConstant) {
      constVars.add(name);
    }
    if (isFromExterns) {
      externs.put(name, declType);
    } else {
      locals.put(name, declType);
    }
  }

  static void mayRecordEscapedVar(NTIScope s, String name) {
    if (s.isDefinedLocally(name, false)) {
      return;
    }
    while (s != null) {
      if (s.isDefinedLocally(name, false)) {
        s.escapedVars.add(name);
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
    Preconditions.checkState(!name.contains("."));
    Declaration decl = getDeclaration(name, false);
    return decl == null ? null : decl.getTypedef();
  }

  EnumType getEnum(QualifiedName qname) {
    Declaration decl = getDeclaration(qname, false);
    return decl == null ? null : decl.getEnum();
  }

  Namespace getNamespace(String name) {
    Preconditions.checkArgument(!name.contains("."));
    Declaration decl = getDeclaration(name, false);
    return decl == null ? null : decl.getNamespace();
  }

  void addFunNamespace(Node qnameNode) {
    if (qnameNode.isName()) {
      String varName = qnameNode.getString();
      Preconditions.checkArgument(isDefinedLocally(varName, false));
      Preconditions.checkState(!this.localNamespaces.containsKey(varName));
      NTIScope s = Preconditions.checkNotNull(this.localFunDefs.get(varName));
      this.localNamespaces.put(varName, new FunctionNamespace(varName, s));
    } else {
      Preconditions.checkArgument(!isNamespace(qnameNode));
      QualifiedName qname = QualifiedName.fromNode(qnameNode);
      Namespace ns = getNamespace(qname.getLeftmostName());
      NTIScope s = (NTIScope) ns.getDeclaration(qname).getFunctionScope();
      ns.addNamespace(qname.getAllButLeftmost(),
          new FunctionNamespace(qname.toString(), s));
    }
  }

  void addNamespaceLit(Node qnameNode) {
    addNamespace(qnameNode, new NamespaceLit(qnameNode.getQualifiedName()));
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

  void addNamespace(Node qnameNode, Namespace ns) {
    if (ns instanceof EnumType) {
      this.localEnums.add((EnumType) ns);
    }
    if (qnameNode.isName()) {
      String varName = qnameNode.getString();
      Preconditions.checkState(!this.localNamespaces.containsKey(varName));
      this.localNamespaces.put(varName, ns);
      if (qnameNode.isFromExterns() && !this.externs.containsKey(varName)) {
        // We don't know the full type of a namespace until after we see all
        // its properties. But we want to add it to the externs, otherwise it
        // is treated as a local and initialized to the wrong thing in NTI.
        this.externs.put(qnameNode.getString(), null);
      }
    } else {
      Preconditions.checkState(!isDefined(qnameNode));
      QualifiedName qname = QualifiedName.fromNode(qnameNode);
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
    Preconditions.checkArgument(!name.contains("."));
    if (!isDefinedLocally(name, includeTypes)) {
      return null;
    }
    JSType type = null;
    boolean isTypeVar = false;
    if ("this".equals(name)) {
      type = getDeclaredTypeOf("this");
    } else if (locals.containsKey(name)) {
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
    } else if (localTypedefs.containsKey(name) || localNamespaces.containsKey(name)) {
      // Any further declarations are shadowed
    } else if (declaredType != null && declaredType.isTypeVariableDefinedLocally(name)) {
      isTypeVar = true;
      type = JSType.fromTypeVar(declaredType.getTypeVariableDefinedLocally(name));
    } else if (externs.containsKey(name)) {
      type = externs.get(name);
    }
    return new Declaration(type, localTypedefs.get(name),
        localNamespaces.get(name), localFunDefs.get(name), isTypeVar,
        constVars.contains(name));
  }

  @Override
  public Declaration getDeclaration(QualifiedName qname, boolean includeTypes) {
    if (qname.isIdentifier()) {
      return getDeclaration(qname.getLeftmostName(), includeTypes);
    }
    Preconditions.checkState(!this.isFinalized,
        "Namespaces are removed from scopes after finalization");
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
      return new Declaration(JSType.UNKNOWN, null, null, null, false, false);
    }
    return null;
  }

  public Declaration getDeclaration(String name, boolean includeTypes) {
    Preconditions.checkArgument(!name.contains("."));
    Declaration decl = getLocalDeclaration(name, includeTypes);
    if (decl != null) {
      return decl;
    }
    return parent == null ? null : parent.getDeclaration(name, includeTypes);
  }

  void resolveTypedefs(JSTypeCreatorFromJSDoc typeParser) {
    for (Typedef td : localTypedefs.values()) {
      if (!td.isResolved()) {
        typeParser.resolveTypedef(td, this);
      }
    }
  }

  void resolveEnums(JSTypeCreatorFromJSDoc typeParser) {
    for (EnumType e : localEnums) {
      if (!e.isResolved()) {
        typeParser.resolveEnum(e, this);
      }
    }
    localEnums = null;
  }

  void finalizeScope() {
    Preconditions.checkState(isTopLevel() || this.declaredType != null,
        "No declared type for function-scope: %s", this.root);
    unknownTypeNames = ImmutableSet.of();
    JSTypes commonTypes = getCommonTypes();
    // For now, we put types of namespaces directly into the locals.
    // Alternatively, we could move this into NewTypeInference.initEdgeEnvs
    for (Map.Entry<String, Namespace> entry : localNamespaces.entrySet()) {
      String name = entry.getKey();
      Namespace ns = entry.getValue();
      JSType t;
      if (ns instanceof NamespaceLit) {
        constVars.add(name);
        NamespaceLit nslit = (NamespaceLit) ns;
        // The argument to maybeSetWindowInstance should only be non-null for
        // window, but we don't check here to avoid hard-coding the name.
        // Enforced in GlobalTypeInfo.
        nslit.maybeSetWindowInstance(externs.get(name));
        t = nslit.toJSType(commonTypes);
      } else {
        t = ns.toJSType(commonTypes);
      }
      if (externs.containsKey(name)) {
        externs.put(name, t);
      } else {
        locals.put(name, t);
      }
    }
    for (String typedefName : localTypedefs.keySet()) {
      locals.put(typedefName, JSType.UNDEFINED);
    }
    copyOuterVarsTransitively(this);
    localNamespaces = ImmutableMap.of();
    localTypedefs = ImmutableMap.of();
    escapedVars = ImmutableSet.of();
    isFinalized = true;
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
