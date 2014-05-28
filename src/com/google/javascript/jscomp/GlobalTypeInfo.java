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
import com.google.javascript.jscomp.NewTypeInference.WarningReporter;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowCallback;
import com.google.javascript.jscomp.newtypes.DeclaredFunctionType;
import com.google.javascript.jscomp.newtypes.DeclaredTypeRegistry;
import com.google.javascript.jscomp.newtypes.EnumType;
import com.google.javascript.jscomp.newtypes.FunctionType;
import com.google.javascript.jscomp.newtypes.FunctionTypeBuilder;
import com.google.javascript.jscomp.newtypes.JSType;
import com.google.javascript.jscomp.newtypes.JSTypeCreatorFromJSDoc;
import com.google.javascript.jscomp.newtypes.NominalType;
import com.google.javascript.jscomp.newtypes.NominalType.RawNominalType;
import com.google.javascript.jscomp.newtypes.ObjectType;
import com.google.javascript.jscomp.newtypes.QualifiedName;
import com.google.javascript.jscomp.newtypes.Typedef;
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

  static final DiagnosticType IMPLEMENTS_WITHOUT_CONSTRUCTOR =
      DiagnosticType.warning(
          "JSC_IMPLEMENTS_WITHOUT_CONSTRUCTOR",
          "@implements used without @constructor or @interface for {0}");

  static final DiagnosticType CONST_WITHOUT_INITIALIZER =
      DiagnosticType.warning(
          "JSC_CONST_WITHOUT_INITIALIZER",
          "Constants must be initialized when they are defined.");

  static final DiagnosticType COULD_NOT_INFER_CONST_TYPE =
      DiagnosticType.warning(
          "JSC_COULD_NOT_INFER_CONST_TYPE",
          "All constants must be typed. The compiler could not infer the type" +
          "of this constant. Please use an explicit type annotation.");

  static final DiagnosticType MISPLACED_CONST_ANNOTATION =
      DiagnosticType.warning(
          "JSC_MISPLACED_CONST_ANNOTATION",
          "This property cannot be @const." +
          "The @const annotation is only allowed for " +
          "properties of namespaces, prototype properties, " +
          "static properties of constructors, " +
          "and properties of the form this.prop declared inside constructors.");

  static final DiagnosticType CANNOT_OVERRIDE_FINAL_METHOD =
      DiagnosticType.warning(
      "JSC_CANNOT_OVERRIDE_FINAL_METHOD",
      "Final method {0} cannot be overriden.");

  static final DiagnosticType CANNOT_INIT_TYPEDEF =
      DiagnosticType.warning(
      "JSC_CANNOT_INIT_TYPEDEF",
      "A typedef variable represents a type name; " +
      "it cannot be assigned a value.");

  static final DiagnosticType ANONYMOUS_NOMINAL_TYPE =
      DiagnosticType.warning(
          "JSC_ANONYMOUS_NOMINAL_TYPE",
          "Must specify a name when defining a class or interface.");

  static final DiagnosticType MALFORMED_ENUM =
      DiagnosticType.warning(
          "JSC_MALFORMED_ENUM",
          "An enum must be initialized to a non-empty object literal.");

  static final DiagnosticType DUPLICATE_PROP_IN_ENUM =
      DiagnosticType.warning(
          "JSC_DUPLICATE_PROP_IN_ENUM",
          "Property {0} appears twice in the enum declaration.");

  static final DiagnosticGroup ALL_DIAGNOSTICS = new DiagnosticGroup(
      ANONYMOUS_NOMINAL_TYPE,
      CANNOT_INIT_TYPEDEF,
      CANNOT_OVERRIDE_FINAL_METHOD,
      CONSTRUCTOR_REQUIRED,
      CONST_WITHOUT_INITIALIZER,
      COULD_NOT_INFER_CONST_TYPE,
      CTOR_IN_DIFFERENT_SCOPE,
      DICT_IMPLEMENTS_INTERF,
      DUPLICATE_JSDOC,
      DUPLICATE_PROP_IN_ENUM,
      EXTENDS_NON_OBJECT,
      EXTENDS_NOT_ON_CTOR_OR_INTERF,
      REDECLARED_PROPERTY,
      IMPLEMENTS_WITHOUT_CONSTRUCTOR,
      INEXISTENT_PARAM,
      INHERITANCE_CYCLE,
      INTERFACE_WITH_A_BODY,
      INVALID_PROP_OVERRIDE,
      MALFORMED_ENUM,
      MISPLACED_CONST_ANNOTATION,
      UNRECOGNIZED_TYPE_NAME,
      RhinoErrorReporter.BAD_JSDOC_ANNOTATION,
      TypeCheck.CONFLICTING_EXTENDED_TYPE,
      TypeCheck.CONFLICTING_IMPLEMENTED_TYPE,
      TypeCheck.CONFLICTING_SHAPE_TYPE,
      TypeCheck.ENUM_NOT_CONSTANT,
      TypeCheck.INCOMPATIBLE_EXTENDED_PROPERTY_TYPE,
      TypeCheck.MULTIPLE_VAR_DEF,
      TypeCheck.UNKNOWN_OVERRIDE,
      TypeValidator.INTERFACE_METHOD_NOT_IMPLEMENTED,
      VarCheck.UNDEFINED_VAR_ERROR,
      VariableReferenceCheck.REDECLARED_VARIABLE,
      VariableReferenceCheck.UNDECLARED_REFERENCE);

  // Invariant: if a scope s1 contains a scope s2, then s2 is before s1 in
  // scopes. The type inference relies on this fact to process deeper scopes
  // before shallower scopes.
  private final Deque<Scope> scopes = Lists.newLinkedList();
  private Scope globalScope;
  private final Deque<Scope> scopeWorkset = Lists.newLinkedList();
  private WarningReporter warnings;
  private JSTypeCreatorFromJSDoc typeParser = new JSTypeCreatorFromJSDoc();
  private final AbstractCompiler compiler;
  private final CodingConvention convention;
  private final Map<Node, String> anonFunNames = Maps.newHashMap();
  private static final String ANON_FUN_PREFIX = "%anon_fun";
  private int freshId = 1;
  private Map<Node, RawNominalType> nominaltypesByNode = Maps.newHashMap();
  // Keyed on RawNominalTypes and property names
  private HashBasedTable<RawNominalType, String, PropertyDef> propertyDefs =
      HashBasedTable.create();
  // TODO(dimvar): Eventually attach these to nodes, like the current types.
  private Map<Node, JSType> castTypes = Maps.newHashMap();
  private Map<Node, JSType> declaredObjLitProps = Maps.newHashMap();

  GlobalTypeInfo(AbstractCompiler compiler) {
    this.warnings = new WarningReporter(compiler);
    this.compiler = compiler;
    this.convention = compiler.getCodingConvention();
  }

  Collection<Scope> getScopes() {
    return scopes;
  }

  Scope getGlobalScope() {
    return globalScope;
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
    Node fnNameNode = NodeUtil.getFunctionNameNode(n);
    // We don't want to use qualified names here
    if (fnNameNode != null && fnNameNode.isName()) {
      return fnNameNode.getString();
    }
    return anonFunNames.get(n);
  }

  @Override
  public void process(Node externs, Node root) {
    Preconditions.checkArgument(externs == null || externs.isSyntheticBlock());
    Preconditions.checkArgument(root.isSyntheticBlock());
    globalScope = new Scope(root, null, new ArrayList<String>(), null);
    scopes.addFirst(globalScope);

    // Processing of a scope is split into many separate phases, and it's not
    // straightforward to remember which phase does what.

    // (1) Find names of classes, interfaces and typedefs defined
    //     in the global scope
    CollectNamedTypes rootCnt = new CollectNamedTypes(globalScope);
    if (externs != null) {
      new NodeTraversal(compiler, rootCnt).traverse(externs);
    }
    new NodeTraversal(compiler, rootCnt).traverse(root);
    // (2) Determine the type represented by each typedef and each enum
    globalScope.resolveTypedefs(typeParser);
    globalScope.resolveEnums(typeParser);
    // (3) The bulk of the global-scope processing happens here:
    //     - Create scopes for functions
    //     - Declare properties on types
    ProcessScope rootPs = new ProcessScope(globalScope);
    if (externs != null) {
      new NodeTraversal(compiler, rootPs).traverse(externs);
    }
    new NodeTraversal(compiler, rootPs).traverse(root);
    // (4) Things that must happen after the traversal of the scope
    rootPs.finishProcessingScope();

    // (5) Repeat steps 1-4 for all the other scopes (outer-to-inner)
    while (!scopeWorkset.isEmpty()) {
      Scope s = scopeWorkset.removeFirst();
      Node scopeBody = s.getBody();
      new NodeTraversal(compiler, new CollectNamedTypes(s)).traverse(scopeBody);
      s.resolveTypedefs(typeParser);
      s.resolveEnums(typeParser);
      ProcessScope ps = new ProcessScope(s);
      new NodeTraversal(compiler, ps).traverse(scopeBody);
      ps.finishProcessingScope();
    }

    // (6) Adjust types of properties based on inheritance information.
    //     Report errors in the inheritance chain.
    reportInheritanceErrors();

    nominaltypesByNode = null;
    propertyDefs = null;
    for (Scope s : scopes) {
      s.removeTmpData();
    }
    Map<Node, String> unknownTypes = typeParser.getUnknownTypesMap();
    for (Map.Entry<Node, String> unknownTypeEntry : unknownTypes.entrySet()) {
      warnings.add(JSError.make(unknownTypeEntry.getKey(),
              UNRECOGNIZED_TYPE_NAME, unknownTypeEntry.getValue()));
    }
    // The jsdoc parser doesn't have access to the error functions in the jscomp
    // package, so we collect its warnings here.
    for (String warningText : typeParser.getWarnings()) {
      // TODO(blickly): Make warnings better
      warnings.add(JSError.make(
          root, RhinoErrorReporter.BAD_JSDOC_ANNOTATION, warningText));
    }
    typeParser = null;
    compiler.setSymbolTable(this);
    warnings = null;
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
    while (nominalType.getPropDeclaredType(pname) != null) {
      Preconditions.checkArgument(nominalType.isFinalized());
      Preconditions.checkArgument(nominalType.isClass());

      if (propertyDefs.get(nominalType.getId(), pname) != null) {
        return propertyDefs.get(nominalType.getId(), pname);
      }
      nominalType = nominalType.getInstantiatedSuperclass();
    }
    return null;
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
            propertyDefs.get(rawNominalType, pname);
        // To find the declared type of a method, we must meet declared types
        // from all inherited methods.
        DeclaredFunctionType superMethodType =
            DeclaredFunctionType.meet(methodTypes);
        DeclaredFunctionType updatedMethodType =
            localPropDef.methodType.withTypeInfoFromSuper(superMethodType);
        localPropDef.updateMethodType(updatedMethodType);
        propTypesToProcess.put(pname,
            JSType.fromFunctionType(updatedMethodType.toFunctionType()));
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
        // TODO(dimvar): check if we can have @const props here
        rawNominalType.addProtoProperty(pname, resultType, false);
      }

      // Warn for a prop declared with @override that isn't overriding anything.
      for (String pname : nonInheritedPropNames) {
        Node defSite = propertyDefs.get(rawNominalType, pname).defSite;
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
    PropertyDef localPropDef = propertyDefs.get(current, pname);
    JSType localPropType = localPropDef == null ? null :
        current.getPropDeclaredType(pname);
    if (localPropDef != null && superType.isClass() &&
        localPropType.getFunType() != null &&
        superType.hasConstantProp(pname)) {
      // TODO(dimvar): This doesn't work for multiple levels in the hierarchy.
      // Clean up how we process inherited properties and then fix this.
      warnings.add(JSError.make(
          localPropDef.defSite, CANNOT_OVERRIDE_FINAL_METHOD, pname));
      return;
    }
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
      } else if (localPropDef.methodType != null) {
        // If we are looking at a method definition, munging may be needed
        for (PropertyDef inheritedPropDef : inheritedPropDefs) {
          if (inheritedPropDef.methodType != null) {
            propMethodTypesToProcess.put(pname, inheritedPropDef.methodType);
          }
        }
      }
    }
  }

  /**
   * Collects names of classes, interfaces and typedefs.
   * This way, if a type name appears before its declaration, we know what
   * it refers to.
   */
  private class CollectNamedTypes extends AbstractShallowCallback {
    private final Scope currentScope;

    CollectNamedTypes(Scope s) {
      this.currentScope = s;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.FUNCTION:
          initFnScope(n, currentScope);
          break;
        case Token.VAR:
          if (NodeUtil.isTypedefDecl(n)) {
            if (n.getFirstChild().getFirstChild() != null) {
              warnings.add(JSError.make(n, CANNOT_INIT_TYPEDEF));
            }
            String varName = n.getFirstChild().getString();
            if (currentScope.isDefinedLocally(varName)) {
              warnings.add(JSError.make(
                  n, VariableReferenceCheck.REDECLARED_VARIABLE, varName));
              break;
            }
            JSDocInfo jsdoc = n.getJSDocInfo();
            Typedef td = Typedef.make(jsdoc.getTypedefType());
            currentScope.addTypedef(varName, td);
          } else if (NodeUtil.isEnumDecl(n)) {
            // TODO(dimvar): Currently, we don't handle adding static properties
            // to an enum after its definition.
            // Treat enum literals as namespaces.
            String varName = n.getFirstChild().getString();
            if (currentScope.isDefinedLocally(varName)) {
              warnings.add(JSError.make(
                  n, VariableReferenceCheck.REDECLARED_VARIABLE, varName));
              break;
            }
            Node init = n.getFirstChild().getFirstChild();
            if (init == null || !init.isObjectLit() ||
                init.getFirstChild() == null) {
              warnings.add(JSError.make(n, MALFORMED_ENUM));
              currentScope.addLocal(varName, JSType.UNKNOWN, false);
              break;
            }
            JSDocInfo jsdoc = n.getJSDocInfo();

            Set<String> propNames = Sets.newHashSet();
            for (Node prop : init.children()) {
              String pname = NodeUtil.getObjectLitKeyName(prop);
              if (propNames.contains(pname)) {
                warnings.add(JSError.make(n, DUPLICATE_PROP_IN_ENUM, pname));
              }
              if (!convention.isValidEnumKey(pname)) {
                warnings.add(
                    JSError.make(prop, TypeCheck.ENUM_NOT_CONSTANT, pname));
              }
              propNames.add(pname);
            }
            currentScope.addEnum(varName,
                EnumType.make(varName, jsdoc.getEnumParameterType(),
                    ImmutableSet.copyOf(propNames)));
          }
          break;
        case Token.GETPROP:
          // TODO(dimvar): Creating types on namespaces is broken now.
          // Fix that and then handle typedefs on namespaces.
          break;
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
        if (qname == null) {
          warnings.add(JSError.make(fn, ANONYMOUS_NOMINAL_TYPE));
          return;
        }
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
    private Set<Node> lendsObjlits = Sets.newHashSet();

    ProcessScope(Scope currentScope) {
      this.currentScope = currentScope;
      this.undeclaredVars = HashMultimap.create();
    }

    void finishProcessingScope() {
      for (Node objlit : lendsObjlits) {
        processLendsNode(objlit);
      }
      lendsObjlits = null;

      for (Node nameNode : undeclaredVars.values()) {
        warnings.add(JSError.make(nameNode,
              VarCheck.UNDEFINED_VAR_ERROR, nameNode.getString()));
      }
    }

    // @lends can lend properties to an object X being defined in the same
    // statement as the @lends. To make sure that we've seen the definition of
    // X, we process @lends annotations after we've traversed the scope.
    void processLendsNode(Node objlit) {
      JSDocInfo jsdoc = objlit.getJSDocInfo();
      String lendsName = jsdoc.getLendsName();
      Preconditions.checkNotNull(lendsName);
      // TODO(dimvar): handle @lends for objects with qualified names
      Preconditions.checkState(!lendsName.contains("."));
      JSType borrowerType = currentScope.getDeclaredTypeOf(lendsName);
      if (borrowerType == null || borrowerType.isUnknown()) {
        warnings.add(JSError.make(objlit, TypedScopeCreator.LENDS_ON_NON_OBJECT,
                lendsName, "unknown"));
        return;
      }
      if (!borrowerType.isSubtypeOf(JSType.TOP_OBJECT)) {
        warnings.add(JSError.make(objlit, TypedScopeCreator.LENDS_ON_NON_OBJECT,
                lendsName, borrowerType.toString()));
        return;
      }
      if (!currentScope.isNamespace(lendsName)) {
        // TODO(dimvar): Handle @lends for constructors and prototypes
        return;
      }
      for (Node prop : objlit.children()) {
        String pname = NodeUtil.getObjectLitKeyName(prop);
        QualifiedName qname = new QualifiedName(pname);
        JSType propDeclType = declaredObjLitProps.get(prop);
        if (propDeclType != null) {
          currentScope.updateTypeOfLocal(lendsName,
              borrowerType.withDeclaredProperty(qname, propDeclType, false));
        } else {
          JSType t = simpleInferExprType(prop.getFirstChild());
          if (t == null) {
            t = JSType.UNKNOWN;
          }
          currentScope.updateTypeOfLocal(
              lendsName, borrowerType.withProperty(qname, t));
        }
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
            if (NodeUtil.isTypedefDecl(parent) || NodeUtil.isEnumDecl(parent)) {
              break;
            }
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
              } else if (parent.isCatch()) {
                currentScope.addLocal(name, JSType.UNKNOWN, false);
              } else {
                boolean isConst = NodeUtil.hasConstAnnotation(parent);
                JSType declType = getVarTypeFromAnnotation(n);
                if (isConst && !mayWarnAboutNoInit(n) && declType == null) {
                  declType = inferConstTypeFromRhs(n);
                }
                currentScope.addLocal(name, declType, isConst);
              }
            }
          } else if (currentScope.isOuterVarEarly(name)) {
            currentScope.addOuterVar(name);
          } else if (// Typedef variables can't be referenced in the source.
              currentScope.getTypedef(name) != null ||
              !name.equals(currentScope.getName()) &&
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
          JSDocInfo jsdoc = n.getJSDocInfo();
          if (jsdoc != null && jsdoc.getLendsName() != null) {
            lendsObjlits.add(n);
          }
          for (Node prop : n.children()) {
            if (prop.getJSDocInfo() != null) {
              declaredObjLitProps.put(prop,
                  getTypeDeclarationFromJsdoc(
                      prop.getJSDocInfo(), currentScope));
            }
            if (NodeUtil.hasConstAnnotation(prop)) {
              warnings.add(JSError.make(prop, MISPLACED_CONST_ANNOTATION));
            }
          }
          break;
        }
      }
    }

    private void visitPropertyDeclaration(Node getProp) {
      // Class property
      if (isClassPropAccess(getProp, currentScope)) {
        if (NodeUtil.hasConstAnnotation(getProp) &&
            currentScope.isPrototypeMethod()) {
          warnings.add(JSError.make(getProp, MISPLACED_CONST_ANNOTATION));
        }
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
      if (isPropDecl(getProp) &&
          currentScope.isNamespace(getProp.getFirstChild())) {
        visitNamespacePropertyDeclaration(getProp);
        return;
      }
      // Other property
      if (NodeUtil.hasConstAnnotation(getProp)) {
        warnings.add(JSError.make(getProp, MISPLACED_CONST_ANNOTATION));
      }
    }

    private boolean isClassPropAccess(Node n, Scope s) {
      return n.isGetProp() && n.getFirstChild().isThis() &&
          (s.isConstructor() || s.isPrototypeMethod());
    }

    private boolean isStaticCtorProp(Node getProp, Scope s) {
      Preconditions.checkArgument(getProp.isGetProp());
      if (!getProp.isQualifiedName()) {
        return false;
      }
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
      Scope fnScope = computeFnScope(fn, ownerType, currentScope);
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
          // Qualified names will be removed in removeTmpData
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
        if (initializer != null && initializer.isFunction()) {
          visitFunctionDef(initializer, null);
        }
        // We don't look at assignments to prototypes of non-constructors.
        return;
      }
      // We only add properties to the prototype of a class if the
      // property creations are in the same scope as the constructor
      if (!currentScope.isDefinedLocally(ctorName)) {
        warnings.add(JSError.make(getProp, CTOR_IN_DIFFERENT_SCOPE));
        if (initializer != null && initializer.isFunction()) {
          visitFunctionDef(initializer, rawType);
        }
        return;
      }
      String pname = NodeUtil.getPrototypePropertyName(getProp);
      // Find the declared type of the property.
      JSType propDeclType;
      DeclaredFunctionType methodType;
      Scope methodScope;
      if (initializer != null && initializer.isFunction()) {
        // TODO(dimvar): we must do this for any function "defined" as the rhs
        // of an assignment to a property, not just when the property is a
        // prototype property.
        methodScope = visitFunctionDef(initializer, rawType);
        methodType = methodScope.getDeclaredType();
        propDeclType = JSType.fromFunctionType(methodType.toFunctionType());
      } else {
        methodScope = null;
        JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(getProp);
        if (jsdoc != null && jsdoc.containsFunctionDeclaration()) {
          // We're parsing a function declaration without a function initializer
          methodType = computeFnDeclaredType(
              jsdoc, pname, getProp, rawType, currentScope);
          propDeclType = JSType.fromFunctionType(methodType.toFunctionType());
        } else if (jsdoc != null && jsdoc.hasType()) {
          // We are parsing a non-function prototype property
          methodType = null;
          propDeclType =
              typeParser.getNodeTypeDeclaration(jsdoc, rawType, currentScope);
        } else {
          methodType = null;
          propDeclType = null;
        }
      }
      propertyDefs.put(rawType, pname,
          new PropertyDef(getProp, methodType, methodScope));
      // Add the property to the class with the appropriate type.
      boolean isConst = NodeUtil.hasConstAnnotation(getProp);
      if (propDeclType != null || isConst) {
        if (mayWarnAboutExistingProp(rawType, pname, getProp, propDeclType)) {
          return;
        }
        if (isConst && !mayWarnAboutNoInit(getProp) && propDeclType == null) {
          propDeclType = inferConstTypeFromRhs(getProp);
        }
        rawType.addProtoProperty(pname, propDeclType, isConst);
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
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(getProp);
      JSType propDeclType = getTypeDeclarationFromJsdoc(jsdoc, currentScope);
      boolean isConst = NodeUtil.hasConstAnnotation(getProp);
      if (propDeclType != null || isConst) {
        JSType previousPropType = classType.getCtorPropDeclaredType(pname);
        if (classType.hasCtorProp(pname) &&
            previousPropType != null &&
            !suppressDupPropWarning(jsdoc, propDeclType, previousPropType)) {
          warnings.add(JSError.make(getProp, REDECLARED_PROPERTY,
                  pname, classType.toString()));
          return;
        }
        if (isConst && !mayWarnAboutNoInit(getProp) && propDeclType == null) {
          propDeclType = inferConstTypeFromRhs(getProp);
        }
        classType.addCtorProperty(pname, propDeclType, isConst);
      } else {
        classType.addUndeclaredCtorProperty(pname);
      }
    }

    private void visitNamespacePropertyDeclaration(Node getProp) {
      Preconditions.checkArgument(getProp.isGetProp());
      QualifiedName qname = QualifiedName.fromGetprop(getProp);
      String leftmost = qname.getLeftmostName();
      QualifiedName allButLeftmost = qname.getAllButLeftmost();
      JSType currentType = currentScope.getDeclaredTypeOf(leftmost);
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(getProp);
      JSType typeInJsdoc = getTypeDeclarationFromJsdoc(jsdoc, currentScope);
      boolean isConst = NodeUtil.hasConstAnnotation(getProp);
      if (NodeUtil.isNamespaceDecl(getProp)) {
        currentScope.updateTypeOfLocal(leftmost,
            currentType.withProperty(allButLeftmost, JSType.TOP_OBJECT));
      } else if (typeInJsdoc != null || isConst) {
        JSType previousPropType = currentType.getDeclaredProp(allButLeftmost);
        if (currentType.mayHaveProp(allButLeftmost) &&
            previousPropType != null &&
            !suppressDupPropWarning(jsdoc, typeInJsdoc, previousPropType)) {
          warnings.add(JSError.make(getProp, REDECLARED_PROPERTY,
                  allButLeftmost.toString(), currentType.toString()));
          return;
        }
        JSType declType = typeInJsdoc;
        if (isConst && !mayWarnAboutNoInit(getProp) && declType == null) {
          declType = inferConstTypeFromRhs(getProp);
        }
        currentScope.updateTypeOfLocal(leftmost,
            currentType.withDeclaredProperty(
                allButLeftmost, declType, isConst));
      } else if (getProp.getParent().isAssign()) {
        // Try to infer the prop type, but don't say that the prop is declared.
        JSType t = simpleInferExprType(getProp.getParent().getLastChild());
        if (t == null) {
          t = JSType.UNKNOWN;
        }
        currentScope.updateTypeOfLocal(leftmost,
            currentType.withProperty(allButLeftmost, t));
      } else {
        currentScope.updateTypeOfLocal(leftmost,
            currentType.withProperty(allButLeftmost, JSType.UNKNOWN));
      }
    }

    private void visitClassPropertyDeclaration(Node getProp) {
      Preconditions.checkArgument(getProp.isGetProp());
      NominalType thisType = currentScope.getDeclaredType().getThisType();
      if (thisType == null) {
        // This will get caught in NewTypeInference
        return;
      }
      RawNominalType rawNominalType = thisType.getRawNominalType();
      String pname = getProp.getLastChild().getString();
      // TODO(blickly): Support @param, @return style fun declarations here.
      JSType declType = getTypeDeclarationFromJsdoc(
          NodeUtil.getBestJSDocInfo(getProp), currentScope);
      boolean isConst = NodeUtil.hasConstAnnotation(getProp);
      if (declType != null || isConst) {
        mayWarnAboutExistingProp(rawNominalType, pname, getProp, declType);
        // Intentionally, we keep going even if we warned for redeclared prop.
        // The reason is that if a prop is defined on a class and on its proto
        // with conflicting types, we prefer the type of the class.
        if (isConst && !mayWarnAboutNoInit(getProp) && declType == null) {
          declType = inferConstTypeFromRhs(getProp);
        }
        if (mayAddPropToType(getProp, rawNominalType)) {
          rawNominalType.addClassProperty(pname, declType, isConst);
        }
      } else if (mayAddPropToType(getProp, rawNominalType)) {
        rawNominalType.addUndeclaredClassProperty(pname);
      }
      propertyDefs.put(rawNominalType, pname,
          new PropertyDef(getProp, null, null));
    }

    boolean mayWarnAboutNoInit(Node constExpr) {
      if (constExpr.isFromExterns()) {
        return false;
      }
      boolean noInit = true;
      if (constExpr.isName()) {
        Preconditions.checkState(constExpr.getParent().isVar());
        noInit = constExpr.getFirstChild() == null;
      } else {
        Preconditions.checkState(constExpr.isGetProp());
        noInit = !constExpr.getParent().isAssign();
      }
      if (noInit) {
        warnings.add(JSError.make(constExpr, CONST_WITHOUT_INITIALIZER));
        return true;
      }
      return false;
    }

    // If a @const doesn't have a declared type, we use the initializer to
    // infer a type.
    // When we cannot infer the type of the initializer, we warn.
    // This way, people do not need to remember the cases where the compiler
    // can infer the type of a constant; we tell them if we cannot infer it.
    // This function is called only when the @const has no declared type.
    private JSType inferConstTypeFromRhs(Node constExpr) {
      if (constExpr.isFromExterns()) {
        warnings.add(JSError.make(constExpr, COULD_NOT_INFER_CONST_TYPE));
        return null;
      }
      Node rhs;
      if (constExpr.isName()) {
        Preconditions.checkState(constExpr.getParent().isVar());
        rhs = constExpr.getFirstChild();
      } else {
        Preconditions.checkState(constExpr.isGetProp() &&
            constExpr.getParent().isAssign());
        rhs = constExpr.getParent().getLastChild();
      }
      JSType rhsType = simpleInferExprType(rhs);
      if (rhsType == null) {
        warnings.add(JSError.make(constExpr, COULD_NOT_INFER_CONST_TYPE));
        return null;
      }
      return rhsType;
    }

    private JSType simpleInferExprType(Node n) {
      switch (n.getType()) {
        // To do this, we must know the type of RegExp w/out looking at externs.
        // case Token.REGEXP:
        // As above, we must know about Array.
        // case Token.ARRAYLIT:
        case Token.BITAND:
        case Token.BITNOT:
        case Token.BITOR:
        case Token.BITXOR:
        case Token.DEC:
        case Token.DIV:
        case Token.INC:
        case Token.LSH:
        case Token.MOD:
        case Token.MUL:
        case Token.NEG:
        case Token.NUMBER:
        case Token.POS:
        case Token.RSH:
        case Token.SUB:
        case Token.URSH:
          return JSType.NUMBER;
        case Token.STRING:
        case Token.TYPEOF:
          return JSType.STRING;
        case Token.TRUE:
          return JSType.TRUE_TYPE;
        case Token.FALSE:
          return JSType.FALSE_TYPE;
        case Token.EQ:
        case Token.GE:
        case Token.GT:
        case Token.IN:
        case Token.INSTANCEOF:
        case Token.LE:
        case Token.LT:
        case Token.NE:
        case Token.NOT:
        case Token.SHEQ:
        case Token.SHNE:
          return JSType.BOOLEAN;
        case Token.NULL:
          return JSType.NULL;
        case Token.VOID:
          return JSType.UNDEFINED;
        case Token.NAME: {
          String varName = n.getString();
          if (varName.equals("undefined")) {
            return JSType.UNDEFINED;
          }
          return currentScope.getDeclaredTypeOf(varName);
        }
        case Token.OBJECTLIT: {
          JSType objLitType = JSType.TOP_OBJECT;
          for (Node prop : n.children()) {
            JSType propType = simpleInferExprType(prop.getFirstChild());
            if (propType == null) {
              return null;
            }
            objLitType = objLitType.withProperty(
                new QualifiedName(NodeUtil.getObjectLitKeyName(prop)),
                propType);
          }
          return objLitType;
        }
        case Token.GETPROP:
          JSType recvType = simpleInferExprType(n.getFirstChild());
          if (recvType == null) {
            return null;
          }
          QualifiedName qname = new QualifiedName(n.getLastChild().getString());
          JSType propType = recvType.getProp(qname);
          return propType == null ? null : propType;
        case Token.COMMA:
          return simpleInferExprType(n.getLastChild());
        case Token.CALL:
        case Token.NEW:
          JSType ratorType = simpleInferExprType(n.getFirstChild());
          if (ratorType == null) {
            return null;
          }
          FunctionType funType = ratorType.getFunType();
          return funType == null ? null : funType.getReturnType();
        default:
          return null;
      }
    }

    private boolean mayAddPropToType(Node getProp, RawNominalType rawType) {
      if (!rawType.isStruct()) {
        return true;
      }
      Node parent = getProp.getParent();
      return parent.isAssign() && getProp == parent.getFirstChild() &&
          currentScope.isConstructor();
    }

    private boolean mayWarnAboutExistingProp(RawNominalType classType,
        String pname, Node propCreationNode, JSType typeInJsdoc) {
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(propCreationNode);
      JSType previousPropType = classType.getPropDeclaredType(pname);
      if (classType.mayHaveOwnProp(pname) &&
          previousPropType != null &&
          !suppressDupPropWarning(jsdoc, typeInJsdoc, previousPropType)) {
        warnings.add(JSError.make(propCreationNode, REDECLARED_PROPERTY,
                pname, classType.toString()));
        return true;
      }
      return false;
    }

    // All suppressions happen in SuppressDocWarningsGuard.java, except one.
    // At a duplicate property definition annotated with @suppress {duplicate},
    // if the type in the jsdoc is the same as the already declared type,
    // then don't warn.
    // Type info is required to enforce this, so the current type inference
    // does it in TypeValidator.java, and we do it here.
    // This is a hacky suppression.
    // 1) Why is it just specific to "duplicate" and to properties?
    // 2) The docs say that it's only allowed in the top level, but the code
    //    allows it in all scopes.
    // For now, we implement it b/c it exists in the current type inference.
    // But I wouldn't mind if we stopped supporting it.
    private boolean suppressDupPropWarning(
        JSDocInfo propCreationJsdoc, JSType typeInJsdoc, JSType previousType) {
      if (propCreationJsdoc == null ||
          !propCreationJsdoc.getSuppressions().contains("duplicate")) {
        return false;
      }
      return typeInJsdoc != null && previousType != null &&
          typeInJsdoc.equals(previousType);
    }

    private DeclaredFunctionType computeFnDeclaredType(
        JSDocInfo fnDoc, String functionName, Node declNode,
        RawNominalType ownerType, Scope parentScope) {
      Preconditions.checkArgument(
          declNode.isFunction() || declNode.isGetProp());

      ImmutableList<String> typeParameters =
          fnDoc == null ? null : fnDoc.getTemplateTypeNames();

      // TODO(dimvar): warn if multiple jsdocs for a fun

      // Compute the types of formals and the return type
      FunctionTypeBuilder builder =
          typeParser.getFunctionType(fnDoc, declNode, ownerType, parentScope);

      // Look at other annotations, eg, @constructor
      if (fnDoc != null) {
        NominalType parentClass = null;
        // TODO(dimvar): ignore @extends {Object} on constructors,
        // it should be a no-op.
        if (fnDoc.hasBaseType()) {
          if (!fnDoc.isConstructor()) {
            warnings.add(JSError.make(
                declNode, EXTENDS_NOT_ON_CTOR_OR_INTERF, functionName));
          } else {
            Node docNode = fnDoc.getBaseType().getRootNode();
            if (typeParser.hasKnownType(
                docNode, ownerType, parentScope, typeParameters)) {
              parentClass = typeParser.getNominalType(
                      docNode, ownerType, parentScope, typeParameters);
              if (parentClass == null) {
                warnings.add(JSError.make(
                    declNode, EXTENDS_NON_OBJECT, functionName,
                    docNode.toStringTree()));
              } else if (!parentClass.isClass()) {
                warnings.add(JSError.make(
                    declNode, TypeCheck.CONFLICTING_EXTENDED_TYPE,
                    "constructor", functionName));
                parentClass = null;
              }
            }
          }
        }
        RawNominalType ctorType =
            declNode.isFunction() ? nominaltypesByNode.get(declNode) : null;
        ImmutableSet<NominalType> implementedIntfs =
            typeParser.getImplementedInterfaces(
                fnDoc, ownerType, parentScope, typeParameters);

        if (ctorType == null &&
            (fnDoc.isConstructor() || fnDoc.isInterface())) {
          // Anonymous type, don't register it.
          return builder.buildDeclaration();
        } else if (fnDoc.isConstructor()) {
          String className = ctorType.toString();
          if (parentClass != null) {
            if (!ctorType.addSuperClass(parentClass)) {
              warnings.add(JSError.make(
                  declNode, INHERITANCE_CYCLE, className));
            } else if (ctorType.isStruct() && !parentClass.isStruct()) {
              warnings.add(JSError.make(
                  declNode, TypeCheck.CONFLICTING_SHAPE_TYPE,
                      className, "struct", "struct"));
            } else if (ctorType.isDict() && !parentClass.isDict()) {
              warnings.add(JSError.make(
                  declNode, TypeCheck.CONFLICTING_SHAPE_TYPE, className,
                  "dict", "dict"));
            }
          }
          if (ctorType.isDict() && !implementedIntfs.isEmpty()) {
            warnings.add(JSError.make(
                declNode, DICT_IMPLEMENTS_INTERF, className));
          }
          boolean noCycles = ctorType.addInterfaces(implementedIntfs);
          Preconditions.checkState(noCycles);
          builder.addNominalType(NominalType.fromRaw(ctorType));
        } else if (fnDoc.isInterface()) {
          if (declNode.isFunction() &&
              !NodeUtil.isEmptyBlock(NodeUtil.getFunctionBody(declNode))) {
            warnings.add(JSError.make(declNode, INTERFACE_WITH_A_BODY));
          }
          if (!implementedIntfs.isEmpty()) {
            warnings.add(JSError.make(declNode,
                TypeCheck.CONFLICTING_IMPLEMENTED_TYPE, functionName));
          }
          boolean noCycles = ctorType.addInterfaces(
              typeParser.getExtendedInterfaces(
                  fnDoc, ownerType, parentScope, typeParameters));
          if (!noCycles) {
            warnings.add(JSError.make(
                declNode, INHERITANCE_CYCLE, ctorType.toString()));
          }
          builder.addNominalType(NominalType.fromRaw(ctorType));
        } else if (!implementedIntfs.isEmpty()) {
          warnings.add(JSError.make(
              declNode, IMPLEMENTS_WITHOUT_CONSTRUCTOR, functionName));
        }
      }

      if (ownerType != null) {
        builder.addReceiverType(NominalType.fromRaw(ownerType));
      }

      return builder.buildDeclaration();
    }

    /**
     * Compute the declared type for a given scope.
     */
    private Scope computeFnScope(
        Node fn, RawNominalType ownerType, Scope parentScope) {
      Preconditions.checkArgument(fn.isFunction());
      JSDocInfo fnDoc = NodeUtil.getFunctionJSDocInfo(fn);
      String functionName = getFunInternalName(fn);
      DeclaredFunctionType declFunType = computeFnDeclaredType(
        fnDoc, functionName, fn, ownerType, parentScope);

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
      return new Scope(fn, parentScope, formals, declFunType);
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
    final Node defSite; // The getProp of the property definition
    DeclaredFunctionType methodType; // null for non-method property decls
    final Scope methodScope; // null for decls without function on the RHS

    PropertyDef(
        Node defSite, DeclaredFunctionType methodType, Scope methodScope) {
      Preconditions.checkArgument(defSite.isGetProp());
      this.defSite = defSite;
      this.methodType = methodType;
      this.methodScope = methodScope;
    }

    void updateMethodType(DeclaredFunctionType updatedType) {
      this.methodType = updatedType;
      if (this.methodScope != null) {
        this.methodScope.setDeclaredType(updatedType);
      }
    }
  }

  static class Scope implements DeclaredTypeRegistry {
    private final Scope parent;
    private final Node root;
    // Name on the function AST node; null for top scope & anonymous functions
    private final String name;

    // A local w/out declared type is mapped to null, not to JSType.UNKNOWN.
    private final Map<String, JSType> locals = Maps.newHashMap();
    private final Set<String> constVars = Sets.newHashSet();
    private final ArrayList<String> formals;
    // outerVars are the variables that appear free in this scope
    // and are defined in an enclosing scope.
    private final Set<String> outerVars = Sets.newHashSet();
    private final Map<String, Scope> localFunDefs = Maps.newHashMap();
    private Map<String, RawNominalType> localClassDefs = Maps.newHashMap();
    private Map<String, Typedef> localTypedefs = Maps.newHashMap();
    private Map<String, EnumType> localEnums = Maps.newHashMap();
    private Set<String> localNamespaces = Sets.newHashSet();

    // declaredType is null for top level, but never null for functions,
    // even those without jsdoc.
    // Any inferred parameters or return will be set to null individually.
    private DeclaredFunctionType declaredType;

    private Scope(Node root, Scope parent, ArrayList<String> formals,
        DeclaredFunctionType declaredType) {
      if (parent == null) {
        this.name = null;
      } else {
        String nameOnAst = root.getFirstChild().getString();
        this.name = nameOnAst.isEmpty() ? null : nameOnAst;
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

    /** Used only for error messages; null for top scope */
    String getReadableName() {
      // TODO(dimvar): don't return null for anonymous functions
      return parent == null ? null : NodeUtil.getFunctionName(root);
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
      Preconditions.checkArgument(!name.isEmpty() && !isDefinedLocally(name));
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

    // In other languages, type names and variable names are in distinct
    // namespaces and don't clash.
    // But because our typedefs and enums are var declarations, they are in the
    // same namespace as other variables.
    boolean isDefinedLocally(String name) {
      Preconditions.checkNotNull(name);
      return locals.containsKey(name) || formals.contains(name) ||
          localFunDefs.containsKey(name) || "this".equals(name) ||
          (localTypedefs != null && localTypedefs.containsKey(name)) ||
          (localEnums != null && localEnums.containsKey(name));
    }

    private boolean isNamespace(Node expr) {
      if (expr.isName()) {
        return localNamespaces.contains(expr.getString());
      }
      if (!expr.isGetProp()) {
        return false;
      }
      QualifiedName qname = QualifiedName.fromGetprop(expr);
      if (qname == null) {
        return false;
      }
      String leftmost = qname.getLeftmostName();
      if (!localNamespaces.contains(leftmost)) {
        return false;
      }
      JSType propType = getDeclaredTypeOf(leftmost)
          .getProp(qname.getAllButLeftmost());
      if (propType == null) {
        return false;
      }
      return propType.isRecordType();
    }

    private boolean isNamespace(String name) {
      Preconditions.checkState(!name.contains("."));
      return localNamespaces.contains(name);
    }

    private boolean isVisibleInScope(String name) {
      Preconditions.checkNotNull(name);
      return isDefinedLocally(name) ||
          name.equals(this.name) ||
          (parent != null && parent.isVisibleInScope(name));
    }

    boolean isConstVar(String name) {
      return constVars.contains(name) ||
          parent != null && parent.isConstVar(name);
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
      // First see if it's a type variable
      if (declaredType != null && declaredType.isGeneric() &&
          declaredType.getTypeParameters().contains(name)) {
        return JSType.fromTypeVar(name);
      }
      // Then if it's a class/interface name
      RawNominalType rawNominalType = localClassDefs.get(name);
      if (rawNominalType != null) {
        return JSType.fromObjectType(ObjectType.fromNominalType(
            NominalType.fromRaw(rawNominalType)));
      }
      // O/w keep looking in the parent scope
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
        if (formalType == null || formalType.isBottom()) {
          return null;
        }
        return formalType;
      }
      JSType localType = locals.get(name);
      if (localType != null) {
        Preconditions.checkState(!localType.isBottom());
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

    private void addLocal(String name, JSType declType, boolean isConstant) {
      Preconditions.checkArgument(!isDefinedLocally(name));
      if (isConstant) {
        constVars.add(name);
      }
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
      Preconditions.checkArgument(!name.isEmpty());
      localClassDefs.put(name, rawNominalType);
    }

    private void addTypedef(String name, Typedef td) {
      Preconditions.checkArgument(!isDefinedLocally(name));
      localTypedefs.put(name, td);
    }

    public Typedef getTypedef(String name) {
      if (isDefinedLocally(name)) {
        return localTypedefs.get(name);
      }
      if (parent != null) {
        return parent.getTypedef(name);
      }
      return null;
    }

    private void addEnum(String name, EnumType e) {
      Preconditions.checkArgument(!isDefinedLocally(name));
      localEnums.put(name, e);
    }

    public EnumType getEnum(String name) {
      if (isDefinedLocally(name)) {
        return localEnums.get(name);
      }
      if (parent != null) {
        return parent.getEnum(name);
      }
      return null;
    }

    private void resolveTypedefs(JSTypeCreatorFromJSDoc typeParser) {
      for (Map.Entry<String, Typedef> entry : localTypedefs.entrySet()) {
        String name = entry.getKey();
        Typedef td = entry.getValue();
        if (!td.isResolved()) {
          typeParser.resolveTypedef(name, this);
        }
      }
    }

    private void resolveEnums(JSTypeCreatorFromJSDoc typeParser) {
      for (Map.Entry<String, EnumType> entry : localEnums.entrySet()) {
        String name = entry.getKey();
        EnumType e = entry.getValue();
        if (!e.isResolved()) {
          typeParser.resolveEnum(name, this);
        }
        locals.put(name, e.getObjLitType());
      }
    }

    // When debugging, this method can be called at the start of removeTmpData,
    // to make sure everything is OK.
    private void sanityCheck() {
      Set<String> names;
      // dom(localClassDefs) is a subset of dom(localFunDefs)
      names = localFunDefs.keySet();
      for (String s : localClassDefs.keySet()) {
        Preconditions.checkState(names.contains(s));
      }
      // constVars is a subset of dom(locals)
      names = locals.keySet();
      for (String s : constVars) {
        Preconditions.checkState(names.contains(s));
      }
      // localNamespaces is a subset of dom(locals)
      for (String s : localNamespaces) {
        Preconditions.checkState(names.contains(s));
      }
      // The domains of locals, formals, localFunDefs and localTypedefs are
      // pairwise disjoint.
      names = Sets.newHashSet(formals);
      for (String s : locals.keySet()) {
        Preconditions.checkState(!names.contains(s),
            "Name %s is defined twice.", s);
        names.add(s);
      }
      for (String s : localFunDefs.keySet()) {
        Preconditions.checkState(!names.contains(s),
            "Name %s is defined twice.", s);
        names.add(s);
      }
      for (String s : localTypedefs.keySet()) {
        Preconditions.checkState(!names.contains(s),
            "Name %s is defined twice.", s);
      }
    }

    private void removeTmpData() {
      // sanityCheck();
      Iterator<String> it = localFunDefs.keySet().iterator();
      while (it.hasNext()) {
        String name = it.next();
        if (name.contains(".")) {
          it.remove();
        }
      }
      localNamespaces = null;
      localClassDefs = null;
      localTypedefs = null;
    }
  }
}
