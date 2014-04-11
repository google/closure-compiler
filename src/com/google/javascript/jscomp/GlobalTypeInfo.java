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

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowCallback;
import com.google.javascript.jscomp.newtypes.DeclaredFunctionType;
import com.google.javascript.jscomp.newtypes.DeclaredTypeRegistry;
import com.google.javascript.jscomp.newtypes.FunctionTypeBuilder;
import com.google.javascript.jscomp.newtypes.JSType;
import com.google.javascript.jscomp.newtypes.JSTypeCreatorFromJSDoc;
import com.google.javascript.jscomp.newtypes.NominalType;
import com.google.javascript.jscomp.newtypes.NominalType.RawNominalType;
import com.google.javascript.jscomp.newtypes.ObjectType;
import com.google.javascript.jscomp.newtypes.QualifiedName;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Contains information about all scopes; for every variable reference computes
 * whether it is local, a formal parameter, etc.; and computes information about
 * the class hierarchy.
 *
 * Under development. DO NOT USE!
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
class GlobalTypeInfo implements CompilerPass {

  static final DiagnosticType DUPLICATE_JSDOC = DiagnosticType.warning(
      "JSC_DUPLICATE_JSDOC",
      "Found two JsDoc comments for variable: {0}.\n");

  static final DiagnosticType REDECLARED_PROPERTY = DiagnosticType.warning(
      "JSC_REDECLARED_PROPERTY",
      "Found two declarations for property {0} of type {1}.\n");

  static final DiagnosticType INVALID_PROP_OVERRIDE = DiagnosticType.warning(
      "JSC_INVALID_PROP_OVERRIDE",
      "Invalid redeclaration of property {0}.\n" +
      "inherited type  : {1}\n" +
      "overriding type : {2}\n");

  static final DiagnosticType EXTENDS_NOT_ON_CTOR_OR_INTERF =
      DiagnosticType.warning(
          "JSC_EXTENDS_NOT_ON_CTOR_OR_INTERF",
          "@extends used without @constructor or @interface for {0}.\n");

  static final DiagnosticType EXTENDS_NON_OBJECT = DiagnosticType.warning(
      "JSC_EXTENDS_NON_OBJECT",
      "{0} extends non-object type {1}.\n");

  static final DiagnosticType CTOR_IN_DIFFERENT_SCOPE = DiagnosticType.warning(
      "JSC_CTOR_IN_DIFFERENT_SCOPE",
      "Modifying the prototype is only allowed if the constructor is " +
      "in the same scope\n");

  static final DiagnosticType UNRECOGNIZED_TYPE_NAME = DiagnosticType.warning(
      "JSC_UNRECOGNIZED_TYPE_NAME",
      "Type annotation references non-existent type {0}.");

  static final DiagnosticType INTERFACE_WITH_A_BODY = DiagnosticType.warning(
      "JSC_INTERFACE_WITH_A_BODY",
      "Interface definitions should have an empty body.");

  static final DiagnosticType INHERITANCE_CYCLE = DiagnosticType.warning(
      "JSC_INHERITANCE_CYCLE",
      "Cycle detected in inheritance chain of type {0}");

  static final DiagnosticType DICT_IMPLEMENTS_INTERF = DiagnosticType.warning(
      "JSC_DICT_IMPLEMENTS_INTERF",
      "Class {0} is a dict. Dicts can't implement interfaces.");

  static final DiagnosticType CONSTRUCTOR_REQUIRED = DiagnosticType.warning(
      "JSC_CONSTRUCTOR_REQUIRED",
      "{0} used without @constructor.");

  static final DiagnosticType INEXISTENT_PARAM = DiagnosticType.warning(
      "JSC_INEXISTENT_PARAM",
      "parameter {0} does not appear in {1}''s parameter list");

  // Invariant: if a scope s1 contains a scope s2, then s2 is before s1 in
  // scopes. The type inference relies on this fact to process deeper scopes
  // before shallower scopes.
  private final Deque<Scope> scopes = Lists.newLinkedList();
  private Scope globalScope;
  private final Deque<Scope> scopeWorkset = Lists.newLinkedList();
  private final Set<JSError> warnings = Sets.newHashSet();
  private final JSTypeCreatorFromJSDoc typeParser =
      new JSTypeCreatorFromJSDoc();
  private final AbstractCompiler compiler;
  private final Map<Node, String> anonFunNames = Maps.newHashMap();
  private static final String ANON_FUN_PREFIX = "%anon_fun";
  private int freshId = 1;
  private Map<Node, RawNominalType> nominaltypesByNode = Maps.newHashMap();
  // Keyed on RawNominalType ids and property names
  private HashBasedTable<Integer, String, PropertyDef> propertyDefs =
      HashBasedTable.create();
  // TODO(dimvar): Eventually attach these to nodes, like the current types.
  private Map<Node, JSType> castTypes = Maps.newHashMap();
  private Map<Node, JSType> declaredObjLitProps = Maps.newHashMap();

  GlobalTypeInfo(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  Collection<Scope> getScopes() {
    return scopes;
  }

  Scope getGlobalScope() {
    return globalScope;
  }

  Collection<JSError> getWarnings() {
    return warnings;
  }

  JSType getCastType(Node n) {
    JSType t = castTypes.get(n);
    Preconditions.checkNotNull(t);
    return t;
  }

  JSType getPropDeclaredType(Node n) {
    return declaredObjLitProps.get(n);
  }

  // Differs from the similar method in Scope class on how it treats qnames.
  String getFunInternalName(Node n) {
    Preconditions.checkArgument(n.isFunction());
    String nonAnonFnName = NodeUtil.getFunctionName(n);
    // We don't want to use qualified names here
    if (nonAnonFnName != null && !nonAnonFnName.contains(".")) {
      return nonAnonFnName;
    }
    return anonFunNames.get(n);
  }

  @Override
  public void process(Node externs, Node root) {
    Preconditions.checkArgument(externs == null || externs.isSyntheticBlock());
    Preconditions.checkArgument(root.isSyntheticBlock());
    globalScope = new Scope(root, null, new ArrayList<String>(), null);
    scopes.addFirst(globalScope);

    CollectNamedTypes rootCnt = new CollectNamedTypes(globalScope);
    if (externs != null) {
      new NodeTraversal(compiler, rootCnt).traverse(externs);
    }
    new NodeTraversal(compiler, rootCnt).traverse(root);
    ProcessScope rootPs = new ProcessScope(globalScope);
    if (externs != null) {
      new NodeTraversal(compiler, rootPs).traverse(externs);
    }
    new NodeTraversal(compiler, rootPs).traverse(root);
    rootPs.finishProcessingScope();

    // loop through the workset (outer-to-inner scopes)
    while (!scopeWorkset.isEmpty()) {
      Scope s = scopeWorkset.removeFirst();
      Node scopeBody = s.getBody();
      new NodeTraversal(compiler, new CollectNamedTypes(s)).traverse(scopeBody);
      ProcessScope ps = new ProcessScope(s);
      new NodeTraversal(compiler, ps).traverse(scopeBody);
      ps.finishProcessingScope();
    }

    // Report errors in the inheritance chain, after we're done constructing it.
    reportInheritanceErrors();

    nominaltypesByNode = null;
    propertyDefs = null;
    for (Scope s : scopes) {
      s.finalizeScope();
    }
    Map<Node, String> unknownTypes = typeParser.getUnknownTypesMap();
    for (Node unknownTypeNode : unknownTypes.keySet()) {
      warnings.add(JSError.make(unknownTypeNode, UNRECOGNIZED_TYPE_NAME,
            unknownTypes.get(unknownTypeNode)));
    }
    // The jsdoc parser doesn't have access to the error functions in the jscomp
    // package, so we collect its warnings here.
    for (String warningText : typeParser.getWarnings()) {
      // TODO(blickly): Make warnings better
      warnings.add(JSError.make(
          root, RhinoErrorReporter.BAD_JSDOC_ANNOTATION, warningText));
    }

    compiler.setSymbolTable(this);
  }

  private Collection<PropertyDef> getPropDefsFromInterface(
      NominalType nominalType, String pname) {
    Preconditions.checkArgument(nominalType.isFinalized());
    Preconditions.checkArgument(nominalType.isInterface());
    if (nominalType.getPropDeclaredType(pname) == null) {
      return ImmutableSet.of();
    } else if (propertyDefs.get(nominalType.getId(), pname) != null) {
      return ImmutableSet.of(propertyDefs.get(nominalType.getId(), pname));
    }
    ImmutableSet.Builder<PropertyDef> result = ImmutableSet.builder();
    for (NominalType interf : nominalType.getInstantiatedInterfaces()) {
      result.addAll(getPropDefsFromInterface(interf, pname));
    }
    return result.build();
  }

  private PropertyDef getPropDefFromClass(
      NominalType nominalType, String pname) {
    Preconditions.checkArgument(nominalType.isFinalized());
    Preconditions.checkArgument(nominalType.isClass());
    if (nominalType.getPropDeclaredType(pname) == null) {
      return null;
    } else if (propertyDefs.get(nominalType.getId(), pname) != null) {
      return propertyDefs.get(nominalType.getId(), pname);
    }
    return getPropDefFromClass(nominalType.getInstantiatedSuperclass(), pname);
  }

  /** Report all errors that must be checked at the end of GlobalTypeInfo */
  private void reportInheritanceErrors() {
    Deque<Node> workset = Lists.newLinkedList(nominaltypesByNode.keySet());
    int iterations = 0;
    final int MAX_ITERATIONS = 50000;
  workset_loop:
    while (!workset.isEmpty()) {
      // TODO(blickly): Fix this infinite loop and remove these counters
      Preconditions.checkState(iterations < MAX_ITERATIONS);
      Node funNode = workset.removeFirst();
      RawNominalType rawNominalType = nominaltypesByNode.get(funNode);
      NominalType superClass = rawNominalType.getSuperClass();
      Set<String> nonInheritedPropNames = rawNominalType.getAllOwnProps();
      if (superClass != null && !superClass.isFinalized()) {
        workset.addLast(funNode);
        iterations++;
        continue workset_loop;
      }
      for (NominalType superInterf : rawNominalType.getInterfaces()) {
        if (!superInterf.isFinalized()) {
          workset.addLast(funNode);
          iterations++;
          continue workset_loop;
        }
      }

      Multimap<String, DeclaredFunctionType> propMethodTypesToProcess =
          HashMultimap.create();
      Multimap<String, JSType> propTypesToProcess = HashMultimap.create();
      // Collect inherited types for extended classes
      if (superClass != null) {
        Preconditions.checkState(superClass.isFinalized());
        // TODO(blickly): Can we optimize this to skip unnecessary iterations?
        for (String pname : superClass.getAllPropsOfClass()) {
          nonInheritedPropNames.remove(pname);
          checkSuperProperty(rawNominalType, superClass, pname,
              propMethodTypesToProcess, propTypesToProcess);
        }
      }
      // Collect inherited types for extended/implemented interfaces
      for (NominalType superInterf : rawNominalType.getInterfaces()) {
        Preconditions.checkState(superInterf.isFinalized());
        for (String pname : superInterf.getAllPropsOfInterface()) {
          nonInheritedPropNames.remove(pname);
          checkSuperProperty(rawNominalType, superInterf, pname,
              propMethodTypesToProcess, propTypesToProcess);
        }
      }
      // Munge inherited types of methods
      for (String pname : propMethodTypesToProcess.keySet()) {
        Collection<DeclaredFunctionType> methodTypes =
            propMethodTypesToProcess.get(pname);
        Preconditions.checkState(!methodTypes.isEmpty());
        PropertyDef localPropDef =
            propertyDefs.get(rawNominalType.getId(), pname);
        DeclaredFunctionType propDeclType =
            localPropDef.methodScope.getDeclaredType();
        // To find the declared type of a method, we must meet declared types
        // from all inherited methods.
        DeclaredFunctionType superMethodType =
            DeclaredFunctionType.meet(methodTypes);
        propDeclType = propDeclType.withTypeInfoFromSuper(superMethodType);
        // TODO(blickly): Save DeclaredFunctionTypes somewhere other than
        // inside Scopes so that we can access them even for methods that
        // do not create a Scope at definition site.
        localPropDef.methodScope.setDeclaredType(propDeclType);
        propTypesToProcess.put(pname,
            JSType.fromFunctionType(propDeclType.toFunctionType()));
      }
      // Check inherited types of all props
    add_interface_props:
      for (String pname : propTypesToProcess.keySet()) {
        Collection<JSType> defs = propTypesToProcess.get(pname);
        Preconditions.checkState(!defs.isEmpty());
        JSType resultType = JSType.TOP;
        for (JSType inheritedType : defs) {
          if (inheritedType.isSubtypeOf(resultType)) {
            resultType = inheritedType;
          } else if (!resultType.isSubtypeOf(inheritedType)) {
            // TOOD(blickly): Fix this error message to include supertype names
            warnings.add(JSError.make(
                funNode, TypeCheck.INCOMPATIBLE_EXTENDED_PROPERTY_TYPE,
                NodeUtil.getFunctionName(funNode), pname, "", ""));
            continue add_interface_props;
          }
        }
        rawNominalType.addProtoProperty(pname, resultType);
      }

      // Warn for a prop declared with @override that isn't overriding anything.
      for (String pname : nonInheritedPropNames) {
        Node defSite = propertyDefs.get(rawNominalType.getId(), pname).defSite;
        JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(defSite);
        if (jsdoc != null && jsdoc.isOverride()) {
          warnings.add(JSError.make(defSite, TypeCheck.UNKNOWN_OVERRIDE,
                  pname, rawNominalType.getName()));
        }
      }

      // Finalize nominal type once all properties are added.
      rawNominalType.finalizeNominalType();
    }
  }

  private void checkSuperProperty(
      RawNominalType current, NominalType superType, String pname,
      Multimap<String, DeclaredFunctionType> propMethodTypesToProcess,
      Multimap<String, JSType> propTypesToProcess) {
    JSType inheritedPropType = superType.getPropDeclaredType(pname);
    if (inheritedPropType == null) {
      // No need to go further for undeclared props.
      return;
    }
    Collection<PropertyDef> inheritedPropDefs;
    if (superType.isInterface()) {
      inheritedPropDefs = getPropDefsFromInterface(superType, pname);
    } else {
      inheritedPropDefs =
          ImmutableSet.of(getPropDefFromClass(superType, pname));
    }
    if (superType.isInterface() && current.isClass() &&
        !current.mayHaveProp(pname)) {
      warnings.add(JSError.make(
          inheritedPropDefs.iterator().next().defSite,
          TypeValidator.INTERFACE_METHOD_NOT_IMPLEMENTED,
          pname, superType.toString(), current.toString()));
      return;
    }
    PropertyDef localPropDef = propertyDefs.get(current.getId(), pname);
    JSType localPropType = localPropDef == null ? null :
        current.getPropDeclaredType(pname);
    // System.out.println("nominalType: " + current + "'s " + pname +
    //     " localPropType: " + localPropType +
    //     " with super: " + superType +
    //     " inheritedPropType: " + inheritedPropType);
    if (localPropType != null &&
        !localPropType.isSubtypeOf(inheritedPropType)) {
      warnings.add(JSError.make(
          localPropDef.defSite, INVALID_PROP_OVERRIDE, pname,
          inheritedPropType.toString(), localPropType.toString()));
    } else {
      if (localPropType == null) {
        // Add property from interface to class
        propTypesToProcess.put(pname, inheritedPropType);
      } else if (localPropDef.methodScope != null) {
        // If we are looking at a method definition, munging may be needed
        for (PropertyDef inheritedPropDef : inheritedPropDefs) {
          propMethodTypesToProcess.put(pname,
              inheritedPropDef.methodScope.getDeclaredType());
        }
      }
    }
  }

  /**
   * Create scopes for functions within the given function.
   * This involves naming the function, finding the formals, creating
   * a new scope, and if it is a constructur, creating a new NominalType.
   */
  private class CollectNamedTypes extends AbstractShallowCallback {
    private final Scope currentScope;

    CollectNamedTypes(Scope s) {
      this.currentScope = s;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isFunction()) {
        initFnScope(n, currentScope);
      }
    }

    private void initFnScope(Node fn, Scope parentScope) {
      String qname = NodeUtil.getFunctionName(fn);
      // Qualified names also need a gensymed name.
      if (qname == null || qname.contains(".")) {
        anonFunNames.put(fn, ANON_FUN_PREFIX + freshId);
        freshId++;
      }
      JSDocInfo fnDoc = NodeUtil.getFunctionJSDocInfo(fn);
      if (fnDoc != null && (fnDoc.isConstructor() || fnDoc.isInterface())) {
        ImmutableList<String> typeParameters = fnDoc.getTemplateTypeNames();
        RawNominalType rawNominalType;
        if (fnDoc.isInterface()) {
          rawNominalType = RawNominalType.makeInterface(qname, typeParameters);
        } else if (fnDoc.makesStructs()) {
          rawNominalType =
              RawNominalType.makeStructClass(qname, typeParameters);
        } else if (fnDoc.makesDicts()) {
          rawNominalType = RawNominalType.makeDictClass(qname, typeParameters);
        } else {
          rawNominalType =
              RawNominalType.makeUnrestrictedClass(qname, typeParameters);
        }
        nominaltypesByNode.put(fn, rawNominalType);
        parentScope.addNominalType(qname, rawNominalType);
      } else if (fnDoc != null) {
        if (fnDoc.makesStructs()) {
          warnings.add(JSError.make(fn, CONSTRUCTOR_REQUIRED, "@struct"));
        } else if (fnDoc.makesDicts()) {
          warnings.add(JSError.make(fn, CONSTRUCTOR_REQUIRED, "@dict"));
        }
      }
    }
  }

  private JSType getTypeDeclarationFromJsdoc(JSDocInfo jsdoc, Scope s) {
    return typeParser.getNodeTypeDeclaration(jsdoc, null, s);
  }

  private class ProcessScope extends AbstractShallowCallback {
    private final Scope currentScope;
    /**
     * Keep track of undeclared vars as they are crawled to warn about
     * use before declaration and undeclared variables.
     * We use a multimap so we can give all warnings rather than just the first.
     */
    private final Multimap<String, Node> undeclaredVars;

    ProcessScope(Scope currentScope) {
      this.currentScope = currentScope;
      this.undeclaredVars = HashMultimap.create();
    }

    void finishProcessingScope() {
      for (Node nameNode : undeclaredVars.values()) {
        warnings.add(JSError.make(nameNode,
              VarCheck.UNDEFINED_VAR_ERROR, nameNode.getString()));
      }
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.FUNCTION:
          Node grandparent = parent.getParent();
          if (grandparent == null ||
              (!grandparent.isVar() &&
                  !NodeUtil.isPrototypePropertyDeclaration(grandparent))) {
            visitFunctionDef(n, null);
          }
          break;

        case Token.NAME: {
          String name = n.getString();
          if (name == null || "undefined".equals(name) || parent.isFunction()) {
            return;
          }
          // TODO(dimvar): Handle local scopes introduced by catch properly,
          // after we decide what to do with variables in general, eg, will we
          // use unique numeric ids?
          if (parent.isVar() || parent.isCatch()) {
            if (currentScope.isDefinedLocally(name)) {
              warnings.add(JSError.make(
                  n, VariableReferenceCheck.REDECLARED_VARIABLE, name));
            } else {
              for (Node useBeforeDeclNode : undeclaredVars.get(name)) {
                warnings.add(JSError.make(useBeforeDeclNode,
                    VariableReferenceCheck.UNDECLARED_REFERENCE, name));
              }
              undeclaredVars.removeAll(name);
              Node initializer = n.getFirstChild();
              if (initializer != null && initializer.isFunction()) {
                visitFunctionDef(initializer, null);
              } else if (initializer != null && NodeUtil.isNamespaceDecl(n)) {
                currentScope.addNamespace(name);
              } else {
                currentScope.addLocal(name, parent.isVar() ?
                    getVarTypeFromAnnotation(n) : JSType.UNKNOWN);
              }
            }
          } else if (currentScope.isOuterVarEarly(name)) {
            currentScope.addOuterVar(name);
          } else if (!name.equals(currentScope.getName()) &&
              !currentScope.isDefinedLocally(name)) {
            undeclaredVars.put(name, n);
          }
          break;
        }

        case Token.GETPROP:
          if (parent.isExprResult()) {
            visitPropertyDeclaration(n);
          }
          break;

        case Token.ASSIGN: {
          Node lvalue = n.getFirstChild();
          if (lvalue.isGetProp()) {
            visitPropertyDeclaration(lvalue);
          }
          break;
        }

        case Token.CAST:
          castTypes.put(n,
              getTypeDeclarationFromJsdoc(n.getJSDocInfo(), currentScope));
          break;

        case Token.OBJECTLIT: {
          for (Node prop : n.children()) {
            if (prop.getJSDocInfo() != null) {
              declaredObjLitProps.put(prop,
                  getTypeDeclarationFromJsdoc(
                      prop.getJSDocInfo(), currentScope));
            }
          }
        }
      }
    }

    private void visitPropertyDeclaration(Node getProp) {
      // Class property
      if (isClassPropAccess(getProp, currentScope)) {
        visitClassPropertyDeclaration(getProp);
        return;
      }
      // Prototype property
      if (isPropDecl(getProp) && NodeUtil.isPrototypeProperty(getProp)) {
        visitPrototypePropertyDeclaration(getProp);
        return;
      }
      // "Static" property on constructor
      if (isPropDecl(getProp) &&
          isStaticCtorProp(getProp, currentScope)) {
        visitConstructorPropertyDeclaration(getProp);
        return;
      }
      // Namespace property
      if (isPropDecl(getProp) && currentScope.isNamespace(
          getProp.getFirstChild().getQualifiedName())) {
        visitNamespacePropertyDeclaration(getProp);
      }
    }

    private boolean isClassPropAccess(Node n, Scope s) {
      return n.isGetProp() && n.getFirstChild().isThis() &&
          (s.isConstructor() || s.isPrototypeMethod());
    }

    private boolean isStaticCtorProp(Node getProp, Scope s) {
      Preconditions.checkArgument(getProp.isGetProp());
      String receiverObjName = getProp.getFirstChild().getQualifiedName();
      return s.isLocalFunDef(receiverObjName) && s.getScope(receiverObjName)
          .getDeclaredType().getNominalType() != null;
    }

    private boolean isPropDecl(Node getProp) {
      Preconditions.checkArgument(getProp.isGetProp());
      Node parent = getProp.getParent();
      return parent.isExprResult() ||
          (parent.isAssign() && parent.getParent().isExprResult());
    }

    /** Returns the newly created scope for this function */
    private Scope visitFunctionDef(Node fn, RawNominalType ownerType) {
      Preconditions.checkArgument(fn.isFunction());
      Scope fnScope = computeFnDeclaredType(fn, ownerType, currentScope);
      scopes.addFirst(fnScope);
      String fnName = NodeUtil.getFunctionName(fn);
      String internalName = getFunInternalName(fn);
      if (fnName != null && currentScope.isDefinedLocally(fnName)) {
        if (!fnName.contains(".")) {
          warnings.add(JSError.make(
              fn, VariableReferenceCheck.REDECLARED_VARIABLE, fnName));
        }
        // Associate the anonymous function with the previous declaration to
        // avoid null-pointer exceptions in NewTypeInference.
        if (internalName.startsWith(ANON_FUN_PREFIX)) {
          currentScope.addLocalFunDef(
              internalName, currentScope.getScope(fnName));
        }
      } else {
        currentScope.addLocalFunDef(internalName, fnScope);
        if (fnName != null && fnName.contains(".")) {
          // Qualified names will be removed in finalizeScope
          currentScope.addLocalFunDef(fnName, fnScope);
        }
      }
      if (fnName != null && !fnName.contains(".")) {
        undeclaredVars.removeAll(fnName);
      }
      scopeWorkset.add(fnScope);
      return fnScope;
    }

    private void visitPrototypePropertyDeclaration(Node getProp) {
      Preconditions.checkArgument(getProp.isGetProp());
      Node parent = getProp.getParent();
      Node initializer = parent.isAssign() ? parent.getLastChild() : null;
      Node ctorNameNode = NodeUtil.getPrototypeClassName(getProp);
      String ctorName = ctorNameNode.getQualifiedName();
      RawNominalType rawType = currentScope.getNominalType(ctorName);

      if (rawType == null) {
        // We don't look at assignments to prototypes of non-constructors.
        return;
      }
      // We only add properties to the prototype of a class if the
      // property creations are in the same scope as the constructor
      if (!currentScope.isDefinedLocally(ctorName)) {
        warnings.add(JSError.make(getProp, CTOR_IN_DIFFERENT_SCOPE));
        return;
      }
      String pname = NodeUtil.getPrototypePropertyName(getProp);
      // Find the declared type of the property.
      JSType propDeclType;
      Scope methodScope = null;
      if (initializer != null && initializer.isFunction()) {
        // TODO(dimvar): we must do this for any function "defined" as the rhs
        // of an assignment to a property, not just when the property is a
        // prototype property.
        methodScope = visitFunctionDef(initializer, rawType);
        propDeclType = JSType.fromFunctionType(
            methodScope.getDeclaredType().toFunctionType());
      } else {
        propDeclType = typeParser.getNodeTypeDeclaration(
            NodeUtil.getBestJSDocInfo(getProp), rawType, currentScope);
      }
      propertyDefs.put(rawType.getId(), pname,
          new PropertyDef(getProp, methodScope));
      // Add the property to the class with the appropriate type.
      if (propDeclType != null) {
        if (mayWarnAboutExistingProp(rawType, pname, getProp)) {
          return;
        }
        rawType.addProtoProperty(pname, propDeclType);
      } else {
        rawType.addUndeclaredProtoProperty(pname);
      }
    }

    private void visitConstructorPropertyDeclaration(Node getProp) {
      Preconditions.checkArgument(getProp.isGetProp());
      String ctorName = getProp.getFirstChild().getQualifiedName();
      Preconditions.checkState(currentScope.isLocalFunDef(ctorName));
      RawNominalType classType = currentScope.getNominalType(ctorName);
      String pname = getProp.getLastChild().getString();
      JSType propDeclType = getTypeDeclarationFromJsdoc(
          NodeUtil.getBestJSDocInfo(getProp), currentScope);
      if (propDeclType != null) {
        if (classType.hasCtorProp(pname) &&
            classType.getCtorPropDeclaredType(pname) != null) {
          warnings.add(JSError.make(getProp, REDECLARED_PROPERTY,
                  pname, classType.toString()));
          return;
        }
        classType.addCtorProperty(pname, propDeclType);
      } else {
        classType.addUndeclaredCtorProperty(pname);
      }
    }

    private void visitNamespacePropertyDeclaration(Node getProp) {
      Preconditions.checkArgument(getProp.isGetProp());
      QualifiedName qname = QualifiedName.fromGetprop(getProp);
      String leftmost = qname.getLeftmostName();
      QualifiedName allButLeftmost = qname.getAllButLeftmost();
      JSType typeInJsdoc = getTypeDeclarationFromJsdoc(
          NodeUtil.getBestJSDocInfo(getProp), currentScope);
      JSType currentType = currentScope.getDeclaredTypeOf(leftmost);
      if (typeInJsdoc == null) {
        currentScope.updateTypeOfLocal(leftmost,
            currentType.withProperty(allButLeftmost, JSType.UNKNOWN));
        return;
      }
      if (currentType.mayHaveProp(allButLeftmost) &&
          currentType.getDeclaredProp(allButLeftmost) != null) {
        warnings.add(JSError.make(getProp, REDECLARED_PROPERTY,
                getProp.getQualifiedName(), currentType.toString()));
        return;
      }
      currentScope.updateTypeOfLocal(leftmost,
          currentType.withDeclaredProperty(allButLeftmost, typeInJsdoc));
    }

    private void visitClassPropertyDeclaration(Node getProp) {
      Preconditions.checkArgument(getProp.isGetProp());
      NominalType thisType = currentScope.getDeclaredType().getThisType();
      RawNominalType rawNominalType = thisType.getRawNominalType();
      String pname = getProp.getLastChild().getString();
      // TODO(blickly): Support @param, @return style fun declarations here.
      JSType declaredType = getTypeDeclarationFromJsdoc(
          NodeUtil.getBestJSDocInfo(getProp), currentScope);
      if (declaredType != null) {
        mayWarnAboutExistingProp(rawNominalType, pname, getProp);
      }
      if (mayAddPropToType(getProp, rawNominalType)) {
        if (declaredType != null) {
          rawNominalType.addClassProperty(pname, declaredType);
        } else {
          rawNominalType.addUndeclaredClassProperty(pname);
        }
      }
      propertyDefs.put(rawNominalType.getId(), pname,
          new PropertyDef(getProp, null));
    }

    private boolean mayAddPropToType(Node getProp, RawNominalType rawType) {
      if (!rawType.isStruct()) {
        return true;
      }
      Node parent = getProp.getParent();
      return parent.isAssign() && getProp == parent.getFirstChild() &&
          currentScope.isConstructor();
    }

    private boolean mayWarnAboutExistingProp(
        RawNominalType classType, String pname, Node propCreationNode) {
      if (classType.mayHaveOwnProp(pname) &&
          classType.getPropDeclaredType(pname) != null) {
        warnings.add(JSError.make(propCreationNode, REDECLARED_PROPERTY,
                pname, classType.toString()));
        return true;
      }
      return false;
    }

    /**
     * Compute the declared type for a given scope.
     */
    private Scope computeFnDeclaredType(
        Node fn, RawNominalType ownerType, Scope parentScope) {
      Preconditions.checkArgument(fn.isFunction());
      JSDocInfo fnDoc = NodeUtil.getFunctionJSDocInfo(fn);
      ImmutableList<String> typeParameters =
          fnDoc == null ? null : fnDoc.getTemplateTypeNames();

      // TODO(dimvar): warn if multiple jsdocs for a fun

      // Compute the types of formals and the return type
      FunctionTypeBuilder builder =
          typeParser.getFunctionType(fnDoc, fn, ownerType, parentScope);

      // Look at other annotations, eg, @constructor
      String functionName = getFunInternalName(fn);
      if (fnDoc != null) {
        NominalType parentClass = null;
        // TODO(dimvar): ignore @extends {Object} on constructors,
        // it should be a no-op.
        if (fnDoc.hasBaseType()) {
          if (!fnDoc.isConstructor()) {
            warnings.add(JSError.make(
                fn, EXTENDS_NOT_ON_CTOR_OR_INTERF, functionName));
          } else {
            Node docNode = fnDoc.getBaseType().getRootNode();
            if (typeParser.hasKnownType(
                docNode, ownerType, parentScope, typeParameters)) {
              parentClass = typeParser.getNominalType(
                      docNode, ownerType, parentScope, typeParameters);
              if (parentClass == null) {
                warnings.add(JSError.make(fn, EXTENDS_NON_OBJECT, functionName,
                      docNode.toStringTree()));
              } else if (!parentClass.isClass()) {
                warnings.add(JSError.make(
                    fn, TypeCheck.CONFLICTING_EXTENDED_TYPE,
                    "constructor", functionName));
                parentClass = null;
              }
            }
          }
        }
        RawNominalType rawNominalType = nominaltypesByNode.get(fn);
        if (fnDoc.isConstructor()) {
          String className = rawNominalType.toString();
          if (parentClass != null) {
            if (!rawNominalType.addSuperClass(parentClass)) {
              warnings.add(JSError.make(fn, INHERITANCE_CYCLE, className));
            } else if (rawNominalType.isStruct() && !parentClass.isStruct()) {
              warnings.add(JSError.make(fn, TypeCheck.CONFLICTING_SHAPE_TYPE,
                      className, "struct", "struct"));
            } else if (rawNominalType.isDict() && !parentClass.isDict()) {
              warnings.add(JSError.make(fn, TypeCheck.CONFLICTING_SHAPE_TYPE,
                      className, "dict", "dict"));
            }
          }
          ImmutableSet<NominalType> implementedIntfs =
              typeParser.getImplementedInterfaces(
                  fnDoc, ownerType, parentScope, typeParameters);
          if (rawNominalType.isDict() && !implementedIntfs.isEmpty()) {
            warnings.add(JSError.make(fn, DICT_IMPLEMENTS_INTERF, className));
          }
          boolean noCycles = rawNominalType.addInterfaces(implementedIntfs);
          Preconditions.checkState(noCycles);
          builder.addNominalType(NominalType.fromRaw(rawNominalType));
        } else if (fnDoc.isInterface()) {
          if (!NodeUtil.isEmptyBlock(NodeUtil.getFunctionBody(fn))) {
            warnings.add(JSError.make(fn, INTERFACE_WITH_A_BODY));
          }
          ImmutableSet<NominalType> implemented =
              typeParser.getImplementedInterfaces(
                  fnDoc, ownerType, parentScope, typeParameters);
          if (!implemented.isEmpty()) {
            warnings.add(JSError.make(
                fn, TypeCheck.CONFLICTING_IMPLEMENTED_TYPE, functionName));
          }
          boolean noCycles = rawNominalType.addInterfaces(
              typeParser.getExtendedInterfaces(
                  fnDoc, ownerType, parentScope, typeParameters));
          if (!noCycles) {
            warnings.add(JSError.make(
                fn, INHERITANCE_CYCLE, rawNominalType.toString()));
          }
          builder.addNominalType(NominalType.fromRaw(rawNominalType));
        }
      }

      if (NodeUtil.isPrototypeMethod(fn)) {
        Node lhsNode = fn.getParent().getFirstChild();
        String className =
            NodeUtil.getPrototypeClassName(lhsNode).getQualifiedName();
        builder.addReceiverType(parentScope.getScope(className)
            .getDeclaredType().getNominalType());
      }

      DeclaredFunctionType declFunType = builder.buildDeclaration();

      // Collect the names of the formals.
      // If a formal is a placeholder for variable arity, eg,
      // /** @param {...?} var_args */ function f(var_args) { ... }
      // then we don't collect it.
      // But to decide that we can't just use the jsdoc b/c the type parser
      // may ignore the jsdoc; the only reliable way is to collect the names of
      // formals after building the declared function type.
      ArrayList<String> formals = Lists.newArrayList();
      // tmpRestFormals is used only for error checking
      ArrayList<String> tmpRestFormals = Lists.newArrayList();
      Node param = NodeUtil.getFunctionParameters(fn).getFirstChild();
      int optionalArity = declFunType.getOptionalArity();
      int formalIndex = 0;
      while (param != null) {
        if (!typeParser.isRestArg(fnDoc, param.getString()) ||
            // The jsdoc says restarg, but the jsdoc was ignored
            formalIndex < optionalArity) {
          formals.add(param.getString());
        } else {
          tmpRestFormals.add(param.getString());
        }
        param = param.getNext();
        formalIndex++;
      }
      if (fnDoc != null) {
        for (String formalInJsdoc : fnDoc.getParameterNames()) {
          if (!formals.contains(formalInJsdoc) &&
              !tmpRestFormals.contains(formalInJsdoc)) {
            warnings.add(JSError.make(
                fn, INEXISTENT_PARAM, formalInJsdoc, functionName));
          }
        }
      }
      Scope s = new Scope(fn, parentScope, formals, declFunType);
      for (String restFormal : tmpRestFormals) {
        // Consider var_args as outer vars
        s.addOuterVar(restFormal);
      }
      return s;
    }

    private JSType getVarTypeFromAnnotation(Node nameNode) {
      Preconditions.checkArgument(nameNode.getParent().isVar());
      Node varNode = nameNode.getParent();
      JSType varType =
          getTypeDeclarationFromJsdoc(varNode.getJSDocInfo(), currentScope);
      if (varNode.getChildCount() > 1 && varType != null) {
        warnings.add(JSError.make(varNode, TypeCheck.MULTIPLE_VAR_DEF));
      }
      String varName = nameNode.getString();
      JSType nameNodeType =
          getTypeDeclarationFromJsdoc(nameNode.getJSDocInfo(), currentScope);
      if (nameNodeType != null) {
        if (varType != null) {
          warnings.add(JSError.make(nameNode, DUPLICATE_JSDOC, varName));
        }
        return nameNodeType;
      } else {
        return varType;
      }
    }

  }

  private static class PropertyDef {
    Node defSite; // The getProp of the property definition
    Scope methodScope; // null for non-method property declarations

    PropertyDef(Node defSite, Scope methodScope) {
      Preconditions.checkArgument(defSite.isGetProp());
      this.defSite = defSite;
      this.methodScope = methodScope;
    }
  }

  static class Scope implements DeclaredTypeRegistry {
    private final Scope parent;
    private final Node root;
    // Name on the function AST node; null for top scope & anonymous functions
    private final String name;
    // Used only for error messages; null for top scope
    private final String readableName;

    private final Map<String, JSType> locals = Maps.newHashMap();
    private final ArrayList<String> formals;
    private final Set<String> outerVars = Sets.newHashSet();
    private final Map<String, Scope> localFunDefs = Maps.newHashMap();
    private Map<String, RawNominalType> localClassDefs = Maps.newHashMap();
    private Set<String> localNamespaces = Sets.newHashSet();

    // declaredType is null for top level, but never null for functions,
    // even those without jsdoc.
    // Any inferred parameters or return will be set to null individually.
    private DeclaredFunctionType declaredType;

    private Scope(Node root, Scope parent, ArrayList<String> formals,
        DeclaredFunctionType declaredType) {
      if (parent == null) {
        this.name = null;
        this.readableName = null;
      } else {
        String nameOnAst = root.getFirstChild().getString();
        this.name = nameOnAst.isEmpty() ? null : nameOnAst;
        this.readableName = NodeUtil.getFunctionName(root);
      }
      this.root = root;
      this.parent = parent;
      this.formals = formals;
      this.declaredType = declaredType;
    }

    Node getRoot() {
      return root;
    }

    private Node getBody() {
      Preconditions.checkArgument(root.isFunction());
      return NodeUtil.getFunctionBody(root);
    }

    // TODO(dimvar): don't return null for anonymous functions
    String getReadableName() {
      return readableName;
    }

    String getName() {
      return name;
    }

    private void setDeclaredType(DeclaredFunctionType declaredType) {
      this.declaredType = declaredType;
    }

    DeclaredFunctionType getDeclaredType() {
      return declaredType;
    }

    boolean isFunction() {
      return root.isFunction();
    }

    private boolean isConstructor() {
      if (!root.isFunction()) {
        return false;
      }
      JSDocInfo fnDoc = NodeUtil.getFunctionJSDocInfo(root);
      return fnDoc != null && fnDoc.isConstructor();
    }

    private boolean isPrototypeMethod() {
      Preconditions.checkArgument(root != null);
      return NodeUtil.isPrototypeMethod(root);
    }

    private void addLocalFunDef(String name, Scope scope) {
      localFunDefs.put(name, scope);
    }

    boolean isFormalParam(String name) {
      return formals.contains(name);
    }

    boolean isLocalVar(String name) {
      return locals.containsKey(name);
    }

    boolean isLocalFunDef(String name) {
      return localFunDefs.containsKey(name);
    }

    boolean isDefinedLocally(String name) {
      Preconditions.checkNotNull(name);
      return locals.containsKey(name) || formals.contains(name) ||
          localFunDefs.containsKey(name) || "this".equals(name);
    }

    private boolean isNamespace(String name) {
      return localNamespaces.contains(name);
    }

    private boolean isVisibleInScope(String name) {
      Preconditions.checkNotNull(name);
      return isDefinedLocally(name) ||
          name.equals(this.name) ||
          (parent != null && parent.isVisibleInScope(name));
    }

    private boolean isOuterVarEarly(String name) {
      return !isDefinedLocally(name) &&
          parent != null && parent.isVisibleInScope(name);
    }

    boolean isUndeclaredFormal(String name) {
      return formals.contains(name) && getDeclaredTypeOf(name) == null;
    }

    List<String> getFormals() {
      return Lists.newArrayList(formals);
    }

    Set<String> getOuterVars() {
      return Sets.newHashSet(outerVars);
    }

    Set<String> getLocalFunDefs() {
      return Sets.newHashSet(localFunDefs.keySet());
    }

    boolean isOuterVar(String name) {
      return outerVars.contains(name);
    }

    boolean hasThis() {
      if (isFunction() && getDeclaredType().getThisType() != null) {
        return true;
      }
      return false;
    }

    private RawNominalType getNominalType(String name) {
      RawNominalType rnt = localClassDefs.get(name);
      if (rnt != null) {
        return rnt;
      }
      return parent == null ? null : parent.getNominalType(name);
    }

    // Only used during symbol-table construction, not during type inference.
    @Override
    public JSType lookupTypeByName(String name) {
      if (declaredType != null && declaredType.isGeneric()) {
        if (declaredType.getTypeParameters().contains(name)) {
          return JSType.fromTypeVar(name);
        }
      }
      if (localClassDefs != null) {
        RawNominalType rawNominalType = localClassDefs.get(name);
        if (rawNominalType != null) {
          return JSType.fromObjectType(ObjectType.fromNominalType(
              NominalType.fromRaw(rawNominalType)));
        }
      }
      return parent == null ? null : parent.lookupTypeByName(name);
    }

    JSType getDeclaredTypeOf(String name) {
      Preconditions.checkArgument(name.indexOf('.') == -1);
      if ("this".equals(name)) {
        if (!hasThis()) {
          return null;
        }
        return JSType.fromObjectType(ObjectType.fromNominalType(
            getDeclaredType().getThisType()));
      }
      int formalIndex = formals.indexOf(name);
      if (formalIndex != -1) {
        JSType formalType = declaredType.getFormalType(formalIndex);
        if (formalType != null && formalType.isBottom()) {
          return null;
        }
        return formalType;
      }
      JSType localType = locals.get(name);
      if (localType != null) {
        return localType;
      }
      Scope s = localFunDefs.get(name);
      if (s != null) {
        return JSType.fromFunctionType(s.getDeclaredType().toFunctionType());
      }
      if (name.equals(this.name)) {
        return JSType.fromFunctionType(getDeclaredType().toFunctionType());
      }
      if (parent != null) {
        return parent.getDeclaredTypeOf(name);
      }
      return null;
    }

    private Scope getScopeHelper(String fnName) {
      Scope s = localFunDefs.get(fnName);
      if (s != null) {
        return s;
      } else if (parent != null) {
        return parent.getScopeHelper(fnName);
      }
      return null;
    }

    boolean isKnownFunction(String fnName) {
      return getScopeHelper(fnName) != null;
    }

    Scope getScope(String fnName) {
      Scope s = getScopeHelper(fnName);
      Preconditions.checkState(s != null);
      return s;
    }

    Set<String> getLocals() {
      return ImmutableSet.copyOf(locals.keySet());
    }

    private void addLocal(String name, JSType declType) {
      Preconditions.checkArgument(!isDefinedLocally(name));
      locals.put(name, declType);
    }

    private void addNamespace(String name) {
      Preconditions.checkArgument(!isDefinedLocally(name));
      locals.put(name, JSType.TOP_OBJECT);
      localNamespaces.add(name);
    }

    private void updateTypeOfLocal(String name, JSType newDeclType) {
      locals.put(name, newDeclType);
    }

    private void addOuterVar(String name) {
      outerVars.add(name);
    }

    private void addNominalType(String name, RawNominalType rawNominalType) {
      localClassDefs.put(name, rawNominalType);
    }

    private void finalizeScope() {
      Iterator<String> it = localFunDefs.keySet().iterator();
      while (it.hasNext()) {
        String name = it.next();
        if (name.contains(".")) {
          it.remove();
        }
      }
      localNamespaces = null;
      localClassDefs = null;
    }
  }
}
