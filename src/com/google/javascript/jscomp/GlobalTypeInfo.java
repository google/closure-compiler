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
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.NewTypeInference.WarningReporter;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowCallback;
import com.google.javascript.jscomp.newtypes.DeclaredFunctionType;
import com.google.javascript.jscomp.newtypes.DeclaredTypeRegistry;
import com.google.javascript.jscomp.newtypes.EnumType;
import com.google.javascript.jscomp.newtypes.FunctionType;
import com.google.javascript.jscomp.newtypes.JSType;
import com.google.javascript.jscomp.newtypes.JSTypeCreatorFromJSDoc;
import com.google.javascript.jscomp.newtypes.JSTypes;
import com.google.javascript.jscomp.newtypes.Namespace;
import com.google.javascript.jscomp.newtypes.NamespaceLit;
import com.google.javascript.jscomp.newtypes.NominalType;
import com.google.javascript.jscomp.newtypes.NominalType.RawNominalType;
import com.google.javascript.jscomp.newtypes.QualifiedName;
import com.google.javascript.jscomp.newtypes.Typedef;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
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

  static final DiagnosticType CTOR_IN_DIFFERENT_SCOPE = DiagnosticType.warning(
      "JSC_CTOR_IN_DIFFERENT_SCOPE",
      "Modifying the prototype is only allowed if the constructor is " +
      "in the same scope\n");

  static final DiagnosticType UNRECOGNIZED_TYPE_NAME = DiagnosticType.warning(
      "JSC_UNRECOGNIZED_TYPE_NAME",
      "Type annotation references non-existent type {0}.");

  static final DiagnosticType STRUCTDICT_WITHOUT_CTOR = DiagnosticType.warning(
      "JSC_STRUCTDICT_WITHOUT_CTOR",
      "{0} used without @constructor.");

  static final DiagnosticType EXPECTED_CONSTRUCTOR = DiagnosticType.warning(
      "JSC_EXPECTED_CONSTRUCTOR",
      "Expected constructor name but found {0}.");

  static final DiagnosticType EXPECTED_INTERFACE = DiagnosticType.warning(
      "JSC_EXPECTED_INTERFACE",
      "Expected interface name but found {0}.");

  static final DiagnosticType INEXISTENT_PARAM = DiagnosticType.warning(
      "JSC_INEXISTENT_PARAM",
      "parameter {0} does not appear in {1}''s parameter list");

  static final DiagnosticType CONST_WITHOUT_INITIALIZER =
      DiagnosticType.warning(
          "JSC_CONST_WITHOUT_INITIALIZER",
          "Constants must be initialized when they are defined.");

  static final DiagnosticType COULD_NOT_INFER_CONST_TYPE =
      DiagnosticType.warning(
          "JSC_COULD_NOT_INFER_CONST_TYPE",
          "All constants must be typed. The compiler could not infer the type "
          + "of this constant. Please use an explicit type annotation.");

  static final DiagnosticType MISPLACED_CONST_ANNOTATION =
      DiagnosticType.warning(
          "JSC_MISPLACED_CONST_ANNOTATION",
          "This property cannot be @const. " +
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

  static final DiagnosticType UNDECLARED_NAMESPACE =
      DiagnosticType.warning(
          "JSC_UNDECLARED_NAMESPACE",
          "Undeclared reference to {0}.");

  static final DiagnosticType LENDS_ON_BAD_TYPE =
      DiagnosticType.warning(
          "JSC_LENDS_ON_BAD_TYPE",
          "May only lend properties to namespaces, constructors and their"
          + " prototypes. Found {0}.");

  static final DiagnosticGroup ALL_DIAGNOSTICS = new DiagnosticGroup(
      ANONYMOUS_NOMINAL_TYPE,
      CANNOT_INIT_TYPEDEF,
      CANNOT_OVERRIDE_FINAL_METHOD,
      CONST_WITHOUT_INITIALIZER,
      COULD_NOT_INFER_CONST_TYPE,
      CTOR_IN_DIFFERENT_SCOPE,
      DUPLICATE_JSDOC,
      DUPLICATE_PROP_IN_ENUM,
      EXPECTED_CONSTRUCTOR,
      EXPECTED_INTERFACE,
      REDECLARED_PROPERTY,
      INEXISTENT_PARAM,
      INVALID_PROP_OVERRIDE,
      LENDS_ON_BAD_TYPE,
      MALFORMED_ENUM,
      MISPLACED_CONST_ANNOTATION,
      STRUCTDICT_WITHOUT_CTOR,
      UNDECLARED_NAMESPACE,
      UNRECOGNIZED_TYPE_NAME,
      TypeCheck.CONFLICTING_EXTENDED_TYPE,
      TypeCheck.ENUM_NOT_CONSTANT,
      TypeCheck.INCOMPATIBLE_EXTENDED_PROPERTY_TYPE,
      TypeCheck.MULTIPLE_VAR_DEF,
      TypeCheck.UNKNOWN_OVERRIDE,
      TypeValidator.INTERFACE_METHOD_NOT_IMPLEMENTED //,
      // VarCheck.UNDEFINED_VAR_ERROR,
      // VariableReferenceCheck.REDECLARED_VARIABLE,
      // VariableReferenceCheck.EARLY_REFERENCE
      );

  // An out-to-in list of the scopes, built during CollectNamedTypes
  // This will be reversed at the end of GlobalTypeInfo to make sure
  // that the scopes can be processed in-to-out in NewTypeInference.
  private final List<Scope> scopes = new ArrayList<>();
  private Scope globalScope;
  private WarningReporter warnings;
  private JSTypeCreatorFromJSDoc typeParser;
  private final AbstractCompiler compiler;
  private final CodingConvention convention;
  private final Map<Node, String> anonFunNames = new HashMap<>();
  private static final String ANON_FUN_PREFIX = "%anon_fun";
  private int freshId = 1;
  // Only for original definitions, not for aliased constructors
  private Map<Node, RawNominalType> nominaltypesByNode = new HashMap<>();
  // Keyed on RawNominalTypes and property names
  private HashBasedTable<RawNominalType, String, PropertyDef> propertyDefs =
      HashBasedTable.create();
  // TODO(dimvar): Eventually attach these to nodes, like the current types.
  private Map<Node, JSType> castTypes = new HashMap<>();
  private Map<Node, JSType> declaredObjLitProps = new HashMap<>();

  private JSTypes commonTypes;

  GlobalTypeInfo(AbstractCompiler compiler) {
    this.warnings = new WarningReporter(compiler);
    this.compiler = compiler;
    this.convention = compiler.getCodingConvention();
    this.typeParser = new JSTypeCreatorFromJSDoc(this.convention);
    this.commonTypes = JSTypes.make();
  }

  Collection<Scope> getScopes() {
    return scopes;
  }

  Scope getGlobalScope() {
    return globalScope;
  }

  JSTypes getTypesUtilObject() {
    return commonTypes;
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
    if (anonFunNames.containsKey(n)) {
      return anonFunNames.get(n);
    }
    Node fnNameNode = NodeUtil.getFunctionNameNode(n);
    // We don't want to use qualified names here
    Preconditions.checkState(fnNameNode != null);
    Preconditions.checkState(fnNameNode.isName());
    return fnNameNode.getString();
  }

  @Override
  public void process(Node externs, Node root) {
    Preconditions.checkArgument(externs == null || externs.isSyntheticBlock());
    Preconditions.checkArgument(root.isSyntheticBlock());
    globalScope =
        new Scope(root, null, ImmutableList.<String>of(), commonTypes);
    scopes.add(globalScope);

    // Processing of a scope is split into many separate phases, and it's not
    // straightforward to remember which phase does what.

    // (1) Find names of classes, interfaces, typedefs, enums, and namespaces
    //   defined in the global scope.
    CollectNamedTypes rootCnt = new CollectNamedTypes(globalScope);
    if (externs != null) {
      new NodeTraversal(compiler, rootCnt).traverse(externs);
    }
    new NodeTraversal(compiler, rootCnt).traverse(root);
    // (2) Determine the type represented by each typedef and each enum
    globalScope.resolveTypedefs(typeParser);
    globalScope.resolveEnums(typeParser);
    // (3) Repeat steps 1-2 for all the other scopes (outer-to-inner)
    for (int i = 1; i < scopes.size(); i++) {
      Scope s = scopes.get(i);
      CollectNamedTypes cnt = new CollectNamedTypes(s);
      new NodeTraversal(compiler, cnt).traverse(s.getBody());
      s.resolveTypedefs(typeParser);
      s.resolveEnums(typeParser);
      if (NewTypeInference.measureMem) {
        NewTypeInference.updatePeakMem();
      }
    }

    // (4) The bulk of the global-scope processing happens here:
    //     - Create scopes for functions
    //     - Declare properties on types
    ProcessScope rootPs = new ProcessScope(globalScope);
    if (externs != null) {
      new NodeTraversal(compiler, rootPs).traverse(externs);
    }
    new NodeTraversal(compiler, rootPs).traverse(root);
    // (5) Things that must happen after the traversal of the scope
    rootPs.finishProcessingScope();

    // (6) Repeat steps 4-5 for all the other scopes (outer-to-inner)
    for (int i = 1; i < scopes.size(); i++) {
      Scope s = scopes.get(i);
      ProcessScope ps = new ProcessScope(s);
      new NodeTraversal(compiler, ps).traverse(s.getBody());
      ps.finishProcessingScope();
      if (NewTypeInference.measureMem) {
        NewTypeInference.updatePeakMem();
      }
    }

    // (7) Adjust types of properties based on inheritance information.
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
    for (JSError warning : typeParser.getWarnings()) {
      warnings.add(warning);
    }
    typeParser = null;
    compiler.setSymbolTable(this);
    warnings = null;

    // If a scope s1 contains a scope s2, then s2 must be before s1 in scopes.
    // The type inference relies on this fact to process deeper scopes
    // before shallower scopes.
    Collections.reverse(scopes);
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
    Deque<Node> workset = new LinkedList<>(nominaltypesByNode.keySet());
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
            commonTypes.fromFunctionType(updatedMethodType.toFunctionType()));
      }
      // Check inherited types of all props
    add_interface_props:
      for (String pname : propTypesToProcess.keySet()) {
        Collection<JSType> defs = propTypesToProcess.get(pname);
        Preconditions.checkState(!defs.isEmpty());
        JSType resultType = JSType.TOP;
        for (JSType inheritedType : defs) {
          resultType = JSType.meet(resultType, inheritedType);
          if (!resultType.isBottom()) {
            resultType = inheritedType;
          } else {
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
        current.getInstancePropDeclaredType(pname);
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
    if (localPropType == null) {
      // Add property from interface to class
      propTypesToProcess.put(pname, inheritedPropType);
    } else if (!localPropType.isSubtypeOf(inheritedPropType)) {
      warnings.add(JSError.make(
          localPropDef.defSite, INVALID_PROP_OVERRIDE, pname,
          inheritedPropType.toString(), localPropType.toString()));
    } else if (localPropDef.methodType != null) {
      // If we are looking at a method definition, munging may be needed
      for (PropertyDef inheritedPropDef : inheritedPropDefs) {
        if (inheritedPropDef.methodType != null) {
          propMethodTypesToProcess.put(pname, inheritedPropDef.methodType);
        }
      }
    }
  }

  /**
   * Collects names of classes, interfaces, namespaces, typedefs and enums.
   * This way, if a type name appears before its declaration, we know what
   * it refers to.
   */
  private class CollectNamedTypes extends AbstractShallowCallback {
    private final Scope currentScope;

    CollectNamedTypes(Scope s) {
      this.currentScope = s;
    }

    private void processQualifiedDefinition(Node qnameNode) {
      Preconditions.checkArgument(qnameNode.isGetProp());
      Preconditions.checkArgument(qnameNode.isQualifiedName());
      Node recv = qnameNode.getFirstChild();
      if (!currentScope.isNamespace(recv)) {
        return;
      }
      if (NodeUtil.isNamespaceDecl(qnameNode)) {
        visitNamespace(qnameNode);
      } else if (NodeUtil.isTypedefDecl(qnameNode)) {
        visitTypedef(qnameNode);
      } else if (NodeUtil.isEnumDecl(qnameNode)) {
        visitEnum(qnameNode);
      } else if (NodeUtil.isAliasedNominalTypeDecl(qnameNode)) {
        maybeRecordAliasedNominalType(qnameNode);
      } else if (!currentScope.isDefined(qnameNode)) {
        Namespace ns = currentScope.getNamespace(QualifiedName.fromNode(recv));
        String pname = qnameNode.getLastChild().getString();
        // A program can have an error where a namespace property is defined
        // twice: the first time with a non-namespace type and the second time
        // as a namespace.
        // Adding the non-namespace property here as undeclared prevents us
        // from mistakenly using the second definition later. We use ? for now,
        // but may find a better type in ProcessScope.
        ns.addUndeclaredProperty(pname, JSType.UNKNOWN, /* isConst */ false);
      }
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.FUNCTION: {
          visitFunctionEarly(n);
          break;
        }
        case Token.VAR: {
          Node nameNode = n.getFirstChild();
          if (NodeUtil.isNamespaceDecl(nameNode)) {
            visitNamespace(nameNode);
          } else if (NodeUtil.isTypedefDecl(nameNode)) {
            visitTypedef(nameNode);
          } else if (NodeUtil.isEnumDecl(nameNode)) {
            visitEnum(nameNode);
          } else if (NodeUtil.isAliasedNominalTypeDecl(nameNode)) {
            maybeRecordAliasedNominalType(nameNode);
          }
          break;
        }
        case Token.EXPR_RESULT: {
          Node expr = n.getFirstChild();
          switch (expr.getType()) {
            case Token.ASSIGN:
              if (!expr.getFirstChild().isGetProp()) {
                return;
              }
              expr = expr.getFirstChild();
              // fall through
            case Token.GETPROP:
              if (isPrototypeProperty(expr)
                  || NodeUtil.referencesThis(expr)
                  || !expr.isQualifiedName()) {
                // Class & prototype properties are handled in ProcessScope
                return;
              }
              processQualifiedDefinition(expr);
              break;
            case Token.CALL: {
              List<String> decls = convention.identifyTypeDeclarationCall(expr);
              if (decls == null || decls.isEmpty()) {
                return;
              }
              currentScope.addUnknownTypeNames(decls);
              break;
            }
          }
          break;
        }
      }
    }

    private void visitNamespace(Node qnameNode) {
      if (currentScope.isDefined(qnameNode)) {
        return;
      }
      if (qnameNode.isGetProp()) {
        Preconditions.checkState(qnameNode.getParent().isAssign());
        qnameNode.getParent().putBooleanProp(Node.ANALYZED_DURING_GTI, true);
      }
      currentScope.addNamespace(qnameNode);
    }

    private void visitTypedef(Node qnameNode) {
      Preconditions.checkState(qnameNode.isQualifiedName());
      qnameNode.putBooleanProp(Node.ANALYZED_DURING_GTI, true);
      if (NodeUtil.getInitializer(qnameNode) != null) {
        warnings.add(JSError.make(qnameNode, CANNOT_INIT_TYPEDEF));
      }
      // if (qnameNode.isName()
      //     && currentScope.isDefinedLocally(qnameNode.getString())) {
      //   warnings.add(JSError.make(
      //       qnameNode,
      //       VariableReferenceCheck.REDECLARED_VARIABLE,
      //       qnameNode.getQualifiedName()));
      // }
      if (currentScope.isDefined(qnameNode)) {
        return;
      }
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(qnameNode);
      Typedef td = Typedef.make(jsdoc.getTypedefType());
      currentScope.addTypedef(qnameNode, td);
    }

    private void visitEnum(Node qnameNode) {
      Preconditions.checkState(qnameNode.isQualifiedName());
      qnameNode.putBooleanProp(Node.ANALYZED_DURING_GTI, true);
      // if (qnameNode.isName()
      //     && currentScope.isDefinedLocally(qnameNode.getString())) {
      //   String qname = qnameNode.getQualifiedName();
      //   warnings.add(JSError.make(qnameNode,
      //           VariableReferenceCheck.REDECLARED_VARIABLE, qname));
      // }
      if (currentScope.isDefined(qnameNode)) {
        return;
      }
      Node init = NodeUtil.getInitializer(qnameNode);
      // First check if the definition is an alias of a previous enum.
      if (init != null && init.isQualifiedName()) {
        EnumType et = currentScope.getEnum(init.getQualifiedName());
        if (et != null) {
          currentScope.addEnum(qnameNode, et);
          return;
        }
      }
      // Then check if the enum initializer is an object literal.
      if (init == null || !init.isObjectLit() ||
          init.getFirstChild() == null) {
        warnings.add(JSError.make(qnameNode, MALFORMED_ENUM));
        return;
      }
      // Last, read the object-literal properties and create the EnumType.
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(qnameNode);
      Set<String> propNames = new HashSet<>();
      for (Node prop : init.children()) {
        String pname = NodeUtil.getObjectLitKeyName(prop);
        if (propNames.contains(pname)) {
          warnings.add(JSError.make(qnameNode, DUPLICATE_PROP_IN_ENUM, pname));
        }
        if (!convention.isValidEnumKey(pname)) {
          warnings.add(
              JSError.make(prop, TypeCheck.ENUM_NOT_CONSTANT, pname));
        }
        propNames.add(pname);
      }
      currentScope.addEnum(qnameNode,
          EnumType.make(
              qnameNode.getQualifiedName(),
              jsdoc.getEnumParameterType(),
              ImmutableSet.copyOf(propNames)));
    }

    private void visitFunctionEarly(Node fn) {
      JSDocInfo fnDoc = NodeUtil.getFunctionJSDocInfo(fn);
      Node nameNode = NodeUtil.getFunctionNameNode(fn);
      String internalName = createFunctionInternalName(fn, nameNode);
      boolean isRedeclaration;
      if (nameNode == null || !nameNode.isQualifiedName()) {
        isRedeclaration = false;
      } else if (nameNode.isName()) {
        isRedeclaration = currentScope.isDefinedLocally(nameNode.getString());
      } else {
        isRedeclaration = currentScope.isDefined(nameNode);
      }
      ArrayList<String> formals = collectFormals(fn, fnDoc);
      createFunctionScope(fn, formals, internalName);
      maybeRecordNominalType(fn, nameNode, fnDoc, isRedeclaration);
    }

    private String createFunctionInternalName(Node fn, Node nameNode) {
      String internalName = null;
      if (nameNode == null || !nameNode.isName()) {
        // Anonymous and qualified names need gensymed names.
        internalName = ANON_FUN_PREFIX + freshId;
        anonFunNames.put(fn, internalName);
        freshId++;
      } else if (currentScope.isDefinedLocally(nameNode.getString())) {
        String fnName = nameNode.getString();
        Preconditions.checkState(!fnName.contains("."));
        // warnings.add(JSError.make(
        //     fn, VariableReferenceCheck.REDECLARED_VARIABLE, fnName));
        // Redeclared variables also need gensymed names
        internalName = ANON_FUN_PREFIX + freshId;
        anonFunNames.put(fn, internalName);
        freshId++;
      } else {
        // fnNameNode is undefined simple name
        internalName = nameNode.getString();
      }
      return internalName;
    }

    private void createFunctionScope(
        Node fn, ArrayList<String> formals, String internalName) {
      Scope fnScope = new Scope(fn, currentScope, formals, null);
      if (!fn.isFromExterns()) {
        scopes.add(fnScope);
      }
      currentScope.addLocalFunDef(internalName, fnScope);
    }

    private ArrayList<String> collectFormals(Node fn, JSDocInfo fnDoc) {
      Preconditions.checkArgument(fn.isFunction());
      // Collect the names of the formals.
      // If a formal is a placeholder for variable arity, eg,
      // /** @param {...?} var_args */ function f(var_args) { ... }
      // then we don't collect it.
      // But to decide that we can't just use the jsdoc b/c the type parser
      // may ignore the jsdoc; the only reliable way is to collect the names of
      // formals after building the declared function type.
      ArrayList<String> formals = new ArrayList<>();
      // tmpRestFormals is used only for error checking
      ArrayList<String> tmpRestFormals = new ArrayList<>();
      Node param = NodeUtil.getFunctionParameters(fn).getFirstChild();
      while (param != null) {
        if (JSTypeCreatorFromJSDoc.isRestArg(fnDoc, param.getString())
            && param.getNext() == null) {
          tmpRestFormals.add(param.getString());
        } else {
          formals.add(param.getString());
        }
        param = param.getNext();
      }
      if (fnDoc != null) {
        for (String formalInJsdoc : fnDoc.getParameterNames()) {
          if (!formals.contains(formalInJsdoc) &&
              !tmpRestFormals.contains(formalInJsdoc)) {
            String functionName = NodeUtil.getFunctionName(fn);
            warnings.add(JSError.make(
                fn, INEXISTENT_PARAM, formalInJsdoc, functionName));
          }
        }
      }
      return formals;
    }

    private void maybeRecordNominalType(
        Node fn, Node nameNode, JSDocInfo fnDoc, boolean isRedeclaration) {
      if (fnDoc != null && (fnDoc.isConstructor() || fnDoc.isInterface())) {
        QualifiedName qname = QualifiedName.fromNode(nameNode);
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
        if (isRedeclaration) {
          return;
        }
        if (nameNode.isName()
            || currentScope.isNamespace(nameNode.getFirstChild())) {
          if (nameNode.isGetProp()) {
            fn.getParent().getFirstChild()
                .putBooleanProp(Node.ANALYZED_DURING_GTI, true);
          } else if (currentScope.isTopLevel()) {
            maybeRecordBuiltinType(nameNode.getString(), rawNominalType);
          }
          currentScope.addNominalType(nameNode, rawNominalType);
        }
      } else if (fnDoc != null) {
        if (fnDoc.makesStructs()) {
          warnings.add(JSError.make(fn, STRUCTDICT_WITHOUT_CTOR, "@struct"));
        } else if (fnDoc.makesDicts()) {
          warnings.add(JSError.make(fn, STRUCTDICT_WITHOUT_CTOR, "@dict"));
        }
      }
    }

   private void maybeRecordBuiltinType(
       String name, RawNominalType rawNominalType) {
     switch (name) {
       case "Function":
         commonTypes.setFunctionType(rawNominalType);
         break;
       case "Object":
         commonTypes.setObjectType(rawNominalType);
         break;
       case "Number":
         commonTypes.setNumberInstance(rawNominalType.getInstanceAsJSType());
         break;
       case "String":
         commonTypes.setStringInstance(rawNominalType.getInstanceAsJSType());
         break;
       case "Boolean":
         commonTypes.setBooleanInstance(rawNominalType.getInstanceAsJSType());
         break;
       case "RegExp":
         commonTypes.setRegexpInstance(rawNominalType.getInstanceAsJSType());
         break;
       case "Array":
         commonTypes.setArrayType(rawNominalType);
         break;
     }
   }

    private void maybeRecordAliasedNominalType(Node nameNode) {
      Preconditions.checkArgument(nameNode.isQualifiedName());
      Node aliasedDef = nameNode.getParent();
      Preconditions.checkState(aliasedDef.isVar() || aliasedDef.isAssign());
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(aliasedDef);
      Node init = NodeUtil.getInitializer(nameNode);
      RawNominalType rawType =
          currentScope.getNominalType(QualifiedName.fromNode(init));
      String initQname = init.getQualifiedName();
      if (jsdoc.isConstructor()) {
        if (rawType == null || rawType.isInterface()) {
          warnings.add(JSError.make(init, EXPECTED_CONSTRUCTOR, initQname));
          return;
        }
      } else if (jsdoc.isInterface()) {
        if (rawType == null || !rawType.isInterface()) {
          warnings.add(JSError.make(init, EXPECTED_INTERFACE, initQname));
          return;
        }
      }
      // TODO(dimvar): If init is an unknown type name, we shouldn't warn;
      // Also, associate nameNode with an unknown type name when returning early
      currentScope.addNominalType(nameNode, rawType);
    }
  }

  private class ProcessScope extends AbstractShallowCallback {
    private final Scope currentScope;
    /**
     * Keep track of undeclared vars as they are crawled to warn about
     * use before declaration and undeclared variables.
     * We use a multimap so we can give all warnings rather than just the first.
     */
    private final Multimap<String, Node> undeclaredVars;
    private Set<Node> lendsObjlits = new HashSet<>();

    ProcessScope(Scope currentScope) {
      this.currentScope = currentScope;
      this.undeclaredVars = HashMultimap.create();
    }

    void finishProcessingScope() {
      for (Node objlit : lendsObjlits) {
        processLendsNode(objlit);
      }
      lendsObjlits = null;

      // for (Node nameNode : undeclaredVars.values()) {
      //   warnings.add(JSError.make(nameNode,
      //         VarCheck.UNDEFINED_VAR_ERROR, nameNode.getString()));
      // }
    }

    /**
     * @lends can lend properties to an object X being defined in the same
     * statement as the @lends. To make sure that we've seen the definition of
     * X, we process @lends annotations after we've traversed the scope.
     *
     * @lends can only add properties to namespaces, constructors and prototypes
     */
    void processLendsNode(Node objlit) {
      JSDocInfo jsdoc = objlit.getJSDocInfo();
      String lendsName = jsdoc.getLendsName();
      Preconditions.checkNotNull(lendsName);
      QualifiedName lendsQname = QualifiedName.fromQname(lendsName);
      if (currentScope.isNamespace(lendsQname)) {
        processLendsToNamespace(lendsQname, lendsName, objlit);
      } else {
        RawNominalType rawType = checkValidLendsToPrototypeAndGetClass(
            lendsQname, lendsName, objlit);
        if (rawType == null) {
          return;
        }
        for (Node prop : objlit.children()) {
          String pname =  NodeUtil.getObjectLitKeyName(prop);
          mayAddPropToPrototype(rawType, pname, prop, prop.getFirstChild());
        }
      }
    }

    void processLendsToNamespace(
        QualifiedName lendsQname, String lendsName, Node objlit) {
      RawNominalType rawType = currentScope.getNominalType(lendsQname);
      if (rawType != null && rawType.isInterface()) {
        warnings.add(JSError.make(objlit, LENDS_ON_BAD_TYPE, lendsName));
        return;
      }
      Namespace borrowerNamespace = currentScope.getNamespace(lendsQname);
      for (Node prop : objlit.children()) {
        String pname = NodeUtil.getObjectLitKeyName(prop);
        JSType propDeclType = declaredObjLitProps.get(prop);
        if (propDeclType != null) {
          borrowerNamespace.addProperty(pname, propDeclType, false);
        } else {
          JSType t = simpleInferExprType(prop.getFirstChild());
          if (t == null) {
            t = JSType.UNKNOWN;
          }
          borrowerNamespace.addProperty(pname, t, false);
        }
      }
    }

    RawNominalType checkValidLendsToPrototypeAndGetClass(
        QualifiedName lendsQname, String lendsName, Node objlit) {
      if (!lendsQname.getRightmostName().equals("prototype")) {
        warnings.add(JSError.make(objlit, LENDS_ON_BAD_TYPE, lendsName));
        return null;
      }
      QualifiedName recv = lendsQname.getAllButRightmost();
      RawNominalType rawType = currentScope.getNominalType(recv);
      if (rawType == null || rawType.isInterface()) {
        warnings.add(JSError.make(objlit, LENDS_ON_BAD_TYPE, lendsName));
      }
      return rawType;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.FUNCTION:
          Node grandparent = parent.getParent();
          if (grandparent == null ||
              !isPrototypePropertyDeclaration(grandparent)) {
            visitFunctionLate(n, null);
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
            if (NodeUtil.isNamespaceDecl(n) || NodeUtil.isTypedefDecl(n)
                || NodeUtil.isEnumDecl(n)) {
                if (!currentScope.isDefinedLocally(name)) {
                  // Malformed enum or typedef
                  currentScope.addLocal(
                      name, JSType.UNKNOWN, false, n.isFromExterns());
                }
              break;
            }
            Node initializer = n.getFirstChild();
            if (initializer != null && initializer.isFunction()) {
              break;
            } else if (currentScope.isDefinedLocally(name)) {
              // warnings.add(JSError.make(
              //     n, VariableReferenceCheck.REDECLARED_VARIABLE, name));
            } else {
              // for (Node useBeforeDeclNode : undeclaredVars.get(name)) {
              //   warnings.add(JSError.make(useBeforeDeclNode,
              //       VariableReferenceCheck.EARLY_REFERENCE, name));
              // }
              undeclaredVars.removeAll(name);
              if (parent.isCatch()) {
                currentScope.addLocal(
                    name, JSType.UNKNOWN, false, n.isFromExterns());
              } else {
                boolean isConst = isConst(parent);
                JSType declType = getVarTypeFromAnnotation(n);
                if (isConst && !mayWarnAboutNoInit(n) && declType == null) {
                  declType = inferConstTypeFromRhs(n);
                }
                currentScope.addLocal(
                    name, declType, isConst, n.isFromExterns());
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
          if (lvalue.isGetProp() && parent.isExprResult()) {
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
          Node receiver = parent.isAssign() ? parent.getFirstChild() : parent;
          if (NodeUtil.isNamespaceDecl(receiver)
              && currentScope.isNamespace(receiver)) {
            for (Node prop : n.children()) {
              visitNamespacePropertyDeclaration(
                  prop, receiver, prop.getString());
            }
          } else {
            for (Node prop : n.children()) {
              if (prop.getJSDocInfo() != null) {
                declaredObjLitProps.put(prop,
                    getTypeDeclarationFromJsdoc(
                        prop.getJSDocInfo(), currentScope));
              }
              if (isAnnotatedAsConst(prop)) {
                warnings.add(JSError.make(prop, MISPLACED_CONST_ANNOTATION));
              }
            }
          }
          break;
        }
      }
    }

    private void visitPropertyDeclaration(Node getProp) {
      // Class property
      if (isClassPropAccess(getProp, currentScope)) {
        if (isAnnotatedAsConst(getProp) && currentScope.isPrototypeMethod()) {
          warnings.add(JSError.make(getProp, MISPLACED_CONST_ANNOTATION));
        }
        visitClassPropertyDeclaration(getProp);
        return;
      }
      // Prototype property
      if (isPropertyDeclaration(getProp) && isPrototypeProperty(getProp)) {
        visitPrototypePropertyDeclaration(getProp);
        return;
      }
      // "Static" property on constructor
      if (isPropertyDeclaration(getProp) &&
          isStaticCtorProp(getProp, currentScope)) {
        visitConstructorPropertyDeclaration(getProp);
        return;
      }
      // Namespace property
      if (isPropertyDeclaration(getProp) &&
          currentScope.isNamespace(getProp.getFirstChild())) {
        visitNamespacePropertyDeclaration(getProp);
        return;
      }
      // Other property
      if (isAnnotatedAsConst(getProp)) {
        warnings.add(JSError.make(getProp, MISPLACED_CONST_ANNOTATION));
      }
    }

    private boolean isStaticCtorProp(Node getProp, Scope s) {
      Preconditions.checkArgument(getProp.isGetProp());
      if (!getProp.isQualifiedName()) {
        return false;
      }
      Node receiverObj = getProp.getFirstChild();
      if (!s.isLocalFunDef(receiverObj.getQualifiedName())) {
        return false;
      }
      return null != currentScope.getNominalType(
          QualifiedName.fromNode(receiverObj));
    }

    /** Returns the newly created scope for this function */
    private Scope visitFunctionLate(Node fn, RawNominalType ownerType) {
      Preconditions.checkArgument(fn.isFunction());
      String fnName = NodeUtil.getFunctionName(fn);
      if (fnName != null && !fnName.contains(".")) {
        undeclaredVars.removeAll(fnName);
      }
      String internalName = getFunInternalName(fn);
      Scope fnScope = currentScope.getScope(internalName);
      updateFnScope(fnScope, ownerType);
      return fnScope;
    }

    private void visitPrototypePropertyDeclaration(Node getProp) {
      Preconditions.checkArgument(getProp.isGetProp());
      Node parent = getProp.getParent();
      Node initializer = parent.isAssign() ? parent.getLastChild() : null;
      Node ctorNameNode = NodeUtil.getPrototypeClassName(getProp);
      QualifiedName ctorQname = QualifiedName.fromNode(ctorNameNode);
      RawNominalType rawType = currentScope.getNominalType(ctorQname);

      if (rawType == null) {
        if (initializer != null && initializer.isFunction()) {
          visitFunctionLate(initializer, null);
        }
        // We don't look at assignments to prototypes of non-constructors.
        return;
      }
      // We only add properties to the prototype of a class if the
      // property creations are in the same scope as the constructor
      // TODO(blickly): Rethink this
      if (!currentScope.isDefined(ctorNameNode)) {
        warnings.add(JSError.make(getProp, CTOR_IN_DIFFERENT_SCOPE));
        if (initializer != null && initializer.isFunction()) {
          visitFunctionLate(initializer, rawType);
        }
        return;
      }
      String pname = NodeUtil.getPrototypePropertyName(getProp);
      mayAddPropToPrototype(rawType, pname, getProp, initializer);
    }

    private void visitConstructorPropertyDeclaration(Node getProp) {
      Preconditions.checkArgument(getProp.isGetProp());
      // Named types have already been crawled in CollectNamedTypes
      if (isNamedType(getProp)) {
        return;
      }
      String ctorName = getProp.getFirstChild().getQualifiedName();
      QualifiedName ctorQname = QualifiedName.fromNode(getProp.getFirstChild());
      Preconditions.checkState(currentScope.isLocalFunDef(ctorName));
      RawNominalType classType = currentScope.getNominalType(ctorQname);
      String pname = getProp.getLastChild().getString();
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(getProp);
      JSType propDeclType = getTypeAtPropDeclNode(getProp, jsdoc);
      boolean isConst = isConst(getProp);
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
        getProp.putBooleanProp(Node.ANALYZED_DURING_GTI, true);
        if (isConst) {
          getProp.putBooleanProp(Node.CONSTANT_PROPERTY_DEF, true);
        }
      } else {
        classType.addUndeclaredCtorProperty(pname);
      }
    }

    private void visitNamespacePropertyDeclaration(Node getProp) {
      Preconditions.checkArgument(getProp.isGetProp());
      // Named types have already been crawled in CollectNamedTypes
      if (isNamedType(getProp)) {
        return;
      }
      Node recv = getProp.getFirstChild();
      String pname = getProp.getLastChild().getString();
      visitNamespacePropertyDeclaration(getProp, recv, pname);
    }

    private void visitNamespacePropertyDeclaration(
        Node declNode, Node recv, String pname) {
      Preconditions.checkArgument(
          declNode.isGetProp() || declNode.isStringKey());
      Preconditions.checkArgument(currentScope.isNamespace(recv));
      EnumType et = currentScope.getEnum(recv.getQualifiedName());
      // If there is a reassignment to one of the enum's members, don't consider
      // that a definition of a new property.
      if (et != null && et.enumLiteralHasKey(pname)) {
        return;
      }
      Namespace ns = currentScope.getNamespace(QualifiedName.fromNode(recv));
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(declNode);
      JSType propDeclType = getTypeAtPropDeclNode(declNode, jsdoc);
      boolean isConst = isConst(declNode);
      if (propDeclType != null || isConst) {
        JSType previousPropType = ns.getPropDeclaredType(pname);
        if (ns.hasProp(pname) &&
            previousPropType != null &&
            !suppressDupPropWarning(jsdoc, propDeclType, previousPropType)) {
          warnings.add(JSError.make(declNode, REDECLARED_PROPERTY,
                  pname, ns.toString()));
          return;
        }
        if (isConst && !mayWarnAboutNoInit(declNode) && propDeclType == null) {
          propDeclType = inferConstTypeFromRhs(declNode);
        }
        ns.addProperty(pname, propDeclType, isConst);
        declNode.putBooleanProp(Node.ANALYZED_DURING_GTI, true);
        if (declNode.isGetProp() && isConst) {
          declNode.putBooleanProp(Node.CONSTANT_PROPERTY_DEF, true);
        }
      } else {
        // Try to infer the prop type, but don't say that the prop is declared.
        Node initializer = NodeUtil.getInitializer(declNode);
        JSType t = initializer == null
            ? null : simpleInferExprType(initializer);
        if (t == null) {
          t = JSType.UNKNOWN;
        }
        ns.addUndeclaredProperty(pname, t, false);
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
      boolean isConst = isConst(getProp);
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
        if (isConst) {
          getProp.putBooleanProp(Node.CONSTANT_PROPERTY_DEF, true);
        }
      } else if (mayAddPropToType(getProp, rawNominalType)) {
        rawNominalType.addUndeclaredClassProperty(pname);
      }
      propertyDefs.put(rawNominalType, pname,
          new PropertyDef(getProp, null, null));
    }

    private JSType getTypeAtPropDeclNode(Node declNode, JSDocInfo jsdoc) {
      Preconditions.checkArgument(!currentScope.isNamespace(declNode));
      Node initializer = NodeUtil.getInitializer(declNode);
      if (initializer != null && initializer.isFunction()) {
        return commonTypes.fromFunctionType(
            currentScope.getScope(getFunInternalName(initializer))
            .getDeclaredType().toFunctionType());
      }
      return getTypeDeclarationFromJsdoc(jsdoc, currentScope);
    }

    boolean mayWarnAboutNoInit(Node constExpr) {
      if (constExpr.isFromExterns()) {
        return false;
      }
      Node initializer = NodeUtil.getInitializer(constExpr);
      if (initializer == null) {
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
      Node rhs = NodeUtil.getInitializer(constExpr);
      JSType rhsType = simpleInferExprType(rhs);
      if (rhsType == null) {
        warnings.add(JSError.make(constExpr, COULD_NOT_INFER_CONST_TYPE));
        return null;
      }
      return rhsType;
    }

    private JSType simpleInferExprType(Node n) {
      switch (n.getType()) {
        case Token.REGEXP:
          return commonTypes.getRegexpType();
        case Token.ARRAYLIT: {
          if (!n.hasChildren()) {
            return null;
          }
          Node child = n.getFirstChild();
          JSType arrayType = simpleInferExprType(child);
          if (arrayType == null) {
            return null;
          }
          while (null != (child = child.getNext())) {
            if (!arrayType.equals(simpleInferExprType(child))) {
              return null;
            }
          }
          return commonTypes.getArrayInstance(arrayType);
        }
        case Token.TRUE:
          return JSType.TRUE_TYPE;
        case Token.FALSE:
          return JSType.FALSE_TYPE;
        case Token.NAME: {
          String varName = n.getString();
          if (varName.equals("undefined")) {
            return JSType.UNDEFINED;
          } else if (currentScope.isNamespaceLiteral(varName)) {
            // Namespaces (literals, enums, constructors) get populated during
            // ProcessScope, so it's NOT safe to convert them to jstypes until
            // after ProcessScope is done. So, we don't try to do sth clever
            // here to find the type of a namespace property.
            // However, in the GETPROP case, we special-case for enum
            // properties, because enums get resolved right after
            // CollectNamedTypes, so we know the enumerated type.
            // (But we still don't know the types of enum properties outside
            // the object-literal declaration.)
            return null;
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
          Node recv = n.getFirstChild();
          if (recv.isQualifiedName()) {
            EnumType et = currentScope.getEnum(recv.getQualifiedName());
            if (et != null
                && et.enumLiteralHasKey(n.getLastChild().getString())) {
              return et.getEnumeratedType();
            }
            if (currentScope.isNamespace(recv)) {
              return currentScope.lookupTypeByQname(QualifiedName.fromNode(n));
            }
            JSType recvType = simpleInferExprType(recv);
            QualifiedName qname =
                new QualifiedName(n.getLastChild().getString());
            if (recvType != null && recvType.mayHaveProp(qname)) {
              return recvType.getProp(qname);
            }
          }
          return null;
        case Token.COMMA:
        case Token.ASSIGN:
          return simpleInferExprType(n.getLastChild());
        case Token.CALL:
        case Token.NEW: {
          Node callee = n.getFirstChild();
          // We special-case the function goog.getMsg, which is used by the
          // compiler for i18n.
          if (callee.matchesQualifiedName("goog.getMsg")) {
            return JSType.STRING;
          }
          JSType calleeType = simpleInferExprType(callee);
          if (calleeType == null) {
            return null;
          }
          FunctionType funType = calleeType.getFunType();
          if (funType == null) {
            return null;
          }
          if (funType.isGeneric()) {
            ImmutableList.Builder<JSType> argTypes = ImmutableList.builder();
            for (Node argNode = n.getFirstChild().getNext();
                 argNode != null;
                 argNode = argNode.getNext()) {
              JSType t = simpleInferExprType(argNode);
              if (t == null) {
                return null;
              }
              argTypes.add(t);
            }
            funType = funType
                .instantiateGenericsFromArgumentTypes(argTypes.build());
            if (funType == null) {
              return null;
            }
          }
          JSType retType =
              n.isNew() ? funType.getThisType() : funType.getReturnType();
          return retType;
        }
        default:
          switch (NodeUtil.getKnownValueType(n)) {
            case NULL:
              return JSType.NULL;
            case VOID:
              return JSType.UNDEFINED;
            case NUMBER:
              return JSType.NUMBER;
            case STRING:
              return JSType.STRING;
            case BOOLEAN:
              return JSType.BOOLEAN;
            case UNDETERMINED:
            default:
              return null;
          }
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
      JSType previousPropType = classType.getInstancePropDeclaredType(pname);
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

      Node parent = declNode.getParent();
      // An unannotated function may appear in argument position.
      // In that case, we use jsdoc info from the callee's jsdoc (if any).
      if (fnDoc == null
          && !NodeUtil.functionHasInlineJsdocs(declNode)
          && parent.isCall()
          && declNode != parent.getFirstChild()) {
        FunctionType calleeDeclType = getDeclaredFunctionTypeOfCalleeIfAny(
            parent.getFirstChild(), parentScope);
        if (calleeDeclType != null) {
          int index = parent.getIndexOfChild(declNode) - 1;
          JSType declTypeFromCallee = calleeDeclType.getFormalType(index);
          if (declTypeFromCallee != null) {
            DeclaredFunctionType t =
                computeFnDeclaredTypeFromCallee(declNode, declTypeFromCallee);
            if (t != null) {
              return t;
            }
          }
        }
      }
      // When any of the above IFs fails, fall through to treat the function as
      // a function without jsdoc.

      // TODO(dimvar): warn if multiple jsdocs for a fun
      RawNominalType ctorType =
          declNode.isFunction() ? nominaltypesByNode.get(declNode) : null;
      DeclaredFunctionType result = typeParser.getFunctionType(
          fnDoc, functionName, declNode, ctorType, ownerType, parentScope);
      if (ctorType != null) {
        ctorType.setCtorFunction(
            result.toFunctionType(), commonTypes.getFunctionType());
      }
      return result;
    }

    // We only return a non-null result if the arity of declNode matches the
    // arity we get from declaredTypeAsJSType.
    private DeclaredFunctionType computeFnDeclaredTypeFromCallee(
        Node declNode, JSType declaredTypeAsJSType) {
      Preconditions.checkArgument(declNode.isFunction());
      Preconditions.checkArgument(declNode.getParent().isCall());
      Preconditions.checkNotNull(declaredTypeAsJSType);

      FunctionType funType = declaredTypeAsJSType.getFunType();
      if (funType == null) {
        return null;
      }
      DeclaredFunctionType declType = funType.toDeclaredFunctionType();
      if (declType == null) {
        return null;
      }
      int numFormals = declNode.getChildAtIndex(1).getChildCount();
      int reqArity = declType.getRequiredArity();
      int optArity = declType.getOptionalArity();
      boolean hasRestFormals = declType.hasRestFormals();
      if (reqArity == optArity && !hasRestFormals) {
        return numFormals == reqArity ? declType : null;
      }
      if (numFormals == optArity && !hasRestFormals
          || numFormals == (optArity + 1) && hasRestFormals) {
        return declType;
      }
      return null;
    }

    /**
     * Compute the declared type for a given scope.
     */
    private void updateFnScope(Scope fnScope, RawNominalType ownerType) {
      Node fn = fnScope.getRoot();
      Preconditions.checkState(fn.isFunction());
      JSDocInfo fnDoc = NodeUtil.getFunctionJSDocInfo(fn);
      String functionName = getFunInternalName(fn);
      DeclaredFunctionType declFunType = computeFnDeclaredType(
        fnDoc, functionName, fn, ownerType, currentScope);
      fnScope.setDeclaredType(declFunType);
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

    /**
     * Called for the usual style of prototype-property definitions,
     * but also for @lends.
     */
    private void mayAddPropToPrototype(
        RawNominalType rawType, String pname, Node defSite, Node initializer) {
      Scope methodScope;
      DeclaredFunctionType methodType;
      JSType propDeclType;

      // Find the declared type of the property.
      if (initializer != null && initializer.isFunction()) {
        if (initializer.getLastChild().hasChildren() && rawType.isInterface()) {
          warnings.add(JSError.make(initializer.getLastChild(),
                  TypeCheck.INTERFACE_METHOD_NOT_EMPTY));
        }

        // TODO(dimvar): we must do this for any function "defined" as the rhs
        // of an assignment to a property, not just when the property is a
        // prototype property.
        methodScope = visitFunctionLate(initializer, rawType);
        methodType = methodScope.getDeclaredType();
        propDeclType =
            commonTypes.fromFunctionType(methodType.toFunctionType());
      } else {
        JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(defSite);
        if (jsdoc != null && jsdoc.containsFunctionDeclaration()) {
          // We're parsing a function declaration without a function initializer
          methodScope = null;
          methodType = computeFnDeclaredType(
              jsdoc, pname, defSite, rawType, currentScope);
          propDeclType =
              commonTypes.fromFunctionType(methodType.toFunctionType());
        } else if (jsdoc != null && jsdoc.hasType()) {
          // We are parsing a non-function prototype property
          methodScope = null;
          methodType = null;
          propDeclType =
              typeParser.getNodeTypeDeclaration(jsdoc, rawType, currentScope);
        } else {
          methodScope = null;
          methodType = null;
          propDeclType = null;
        }
      }
      propertyDefs.put(
          rawType, pname, new PropertyDef(defSite, methodType, methodScope));

      // Add the property to the class with the appropriate type.
      boolean isConst = isConst(defSite);
      if (propDeclType != null || isConst) {
        if (mayWarnAboutExistingProp(rawType, pname, defSite, propDeclType)) {
          return;
        }
        if (defSite.isGetProp() && propDeclType == null
            && isConst && !mayWarnAboutNoInit(defSite)) {
          propDeclType = inferConstTypeFromRhs(defSite);
        }
        rawType.addProtoProperty(pname, propDeclType, isConst);
        if (defSite.isGetProp()) { // Don't bother saving for @lends
          defSite.putBooleanProp(Node.ANALYZED_DURING_GTI, true);
          if (isConst) {
            defSite.putBooleanProp(Node.CONSTANT_PROPERTY_DEF, true);
          }
        }
      } else {
        rawType.addUndeclaredProtoProperty(pname);
      }
    }

    private boolean isNamedType(Node getProp) {
      return currentScope.isNamespace(getProp)
          || NodeUtil.isTypedefDecl(getProp);
    }
  }

  private JSType getTypeDeclarationFromJsdoc(JSDocInfo jsdoc, Scope s) {
    return typeParser.getNodeTypeDeclaration(jsdoc, null, s);
  }

  private FunctionType getDeclaredFunctionTypeOfCalleeIfAny(
      Node fn, Scope currentScope) {
    Preconditions.checkArgument(fn.getParent().isCall());
    if (!fn.isFunction()
        && (!fn.isQualifiedName() || fn.isThis())) {
      return null;
    }
    if (fn.isFunction()) {
      return currentScope.getScope(getFunInternalName(fn))
          .getDeclaredType().toFunctionType();
    }
    if (fn.isName()) {
      JSType type = currentScope.getDeclaredTypeOf(fn.getString());
      return type == null ? null : type.getFunType();
    }
    Preconditions.checkState(fn.isGetProp());
    Node recv = fn.getFirstChild();
    QualifiedName recvQname = QualifiedName.fromNode(recv);
    Preconditions.checkNotNull(recvQname);
    if (!currentScope.isNamespace(recvQname)) {
      return null;
    }
    JSType type = currentScope.getNamespace(recvQname)
        .getPropDeclaredType(fn.getLastChild().getString());
    return type == null ? null : type.getFunType();
  }

  private static boolean isClassPropAccess(Node n, Scope s) {
    return n.isGetProp() && n.getFirstChild().isThis() &&
        (s.isConstructor() || s.isPrototypeMethod());
  }

  // TODO(blickly): Move to NodeUtil
  private static boolean isPropertyDeclaration(Node getProp) {
    Preconditions.checkArgument(getProp.isGetProp());
    Node parent = getProp.getParent();
    return parent.isExprResult() ||
        (parent.isAssign() && parent.getParent().isExprResult());
  }

  // In contrast to the NodeUtil method, here we only accept properties directly
  // on the prototype, and return false for names such as Foo.prototype.bar.baz
  private static boolean isPrototypeProperty(Node getProp) {
    if (!getProp.isGetProp()) {
      return false;
    }
    Node recv = getProp.getFirstChild();
    return recv.isGetProp()
        && recv.getLastChild().getString().equals("prototype");
  }

  private static boolean isPrototypePropertyDeclaration(Node n) {
    return NodeUtil.isExprAssign(n)
        && isPrototypeProperty(n.getFirstChild().getFirstChild());
  }

  private static boolean isAnnotatedAsConst(Node defSite) {
    return NodeUtil.hasConstAnnotation(defSite)
        && !NodeUtil.getBestJSDocInfo(defSite).isConstructor();
  }

  private static Node fromDefsiteToName(Node defSite) {
    if (defSite.isVar()) {
      return defSite.getFirstChild();
    }
    if (defSite.isGetProp()) {
      return defSite.getLastChild();
    }
    if (defSite.isStringKey()) {
      return defSite;
    }
    throw new RuntimeException("Unknown defsite: "
        + Token.name(defSite.getType()));
  }

  private boolean isConst(Node defSite) {
    return isAnnotatedAsConst(defSite)
        || NodeUtil.isConstantByConvention(
            this.convention, fromDefsiteToName(defSite));
  }

  private static class PropertyDef {
    final Node defSite; // The getProp/objectLitKey of the property definition
    DeclaredFunctionType methodType; // null for non-method property decls
    final Scope methodScope; // null for decls without function on the RHS

    PropertyDef(
        Node defSite, DeclaredFunctionType methodType, Scope methodScope) {
      Preconditions.checkArgument(
          defSite.isGetProp() || NodeUtil.isObjectLitKey(defSite));
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
    private final JSTypes commonTypes;

    // A local w/out declared type is mapped to null, not to JSType.UNKNOWN.
    private final Map<String, JSType> locals = new HashMap<>();
    private final Map<String, JSType> externs;
    private final Set<String> constVars = new HashSet<>();
    private final List<String> formals;
    // outerVars are the variables that appear free in this scope
    // and are defined in an enclosing scope.
    private final Set<String> outerVars = new HashSet<>();
    private final Map<String, Scope> localFunDefs = new HashMap<>();
    private Set<String> unknownTypeNames = new HashSet<>();
    private Map<String, RawNominalType> localClassDefs = new HashMap<>();
    private Map<String, Typedef> localTypedefs = new HashMap<>();
    private Map<String, EnumType> localEnums = new HashMap<>();
    private Map<String, NamespaceLit> localNamespaces = new HashMap<>();
    // The set qualifiedEnums is used for enum resolution, and then discarded.
    private Set<EnumType> qualifiedEnums = new HashSet<>();

    // declaredType is null for top level, but never null for functions,
    // even those without jsdoc.
    // Any inferred parameters or return will be set to null individually.
    private DeclaredFunctionType declaredType;

    private Scope(
        Node root, Scope parent, List<String> formals, JSTypes commonTypes) {
      if (parent == null) {
        this.name = null;
        this.externs = new HashMap<>();
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
      return root;
    }

    private Node getBody() {
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

    private void setDeclaredType(DeclaredFunctionType declaredType) {
      this.declaredType = declaredType;
    }

    DeclaredFunctionType getDeclaredType() {
      return declaredType;
    }

    boolean isFunction() {
      return root.isFunction();
    }

    private boolean isTopLevel() {
      return parent == null;
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

    private void addUnknownTypeNames(List<String> names) {
      Preconditions.checkState(this.isTopLevel());
      unknownTypeNames.addAll(names);
    }

    private void addLocalFunDef(String name, Scope scope) {
      Preconditions.checkArgument(!name.isEmpty());
      Preconditions.checkArgument(!name.contains("."));
      Preconditions.checkArgument(!isDefinedLocally(name));
      localFunDefs.put(name, scope);
    }

    boolean isFormalParam(String name) {
      return formals.contains(name);
    }

    boolean isLocalVar(String name) {
      return locals.containsKey(name);
    }

    boolean isLocalExtern(String name) {
      return externs.containsKey(name);
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
      Preconditions.checkState(!name.contains("."));
      return locals.containsKey(name) || formals.contains(name)
          || localFunDefs.containsKey(name) || "this".equals(name)
          || externs.containsKey(name)
          || localNamespaces != null && localNamespaces.containsKey(name)
          || localTypedefs != null && localTypedefs.containsKey(name)
          || localEnums != null && localEnums.containsKey(name);
    }

    private boolean isDefined(Node qnameNode) {
      Preconditions.checkArgument(qnameNode.isQualifiedName());
      if (qnameNode.isThis()) {
        return true;
      } else if (qnameNode.isName()) {
        return isDefinedLocally(qnameNode.getString());
      }
      QualifiedName qname = QualifiedName.fromNode(qnameNode);
      String leftmost = qname.getLeftmostName();
      if (isNamespace(leftmost)) {
        return getNamespace(leftmost).isDefined(qname.getAllButLeftmost());
      }
      return parent == null ? false : parent.isDefined(qnameNode);
    }

    private boolean isNamespace(Node expr) {
      if (expr.isName()) {
        return isNamespace(expr.getString());
      }
      if (!expr.isGetProp()) {
        return false;
      }
      return isNamespace(QualifiedName.fromNode(expr));
    }

    private boolean isNamespace(QualifiedName qname) {
      if (qname == null) {
        return false;
      }
      String leftmost = qname.getLeftmostName();
      return isNamespace(leftmost)
          && (qname.isIdentifier()
              || getNamespace(leftmost)
              .hasSubnamespace(qname.getAllButLeftmost()));
    }

    private boolean isNamespace(String name) {
      Preconditions.checkArgument(!name.contains("."));
      return localNamespaces.containsKey(name) ||
          localClassDefs.containsKey(name) ||
          localEnums.containsKey(name) ||
          parent != null && parent.isNamespace(name);
    }

    private boolean isNamespaceLiteral(String name) {
      return localNamespaces.containsKey(name);
    }

    private boolean isVisibleInScope(String name) {
      Preconditions.checkArgument(!name.contains("."));
      return isDefinedLocally(name) ||
          name.equals(this.name) ||
          (parent != null && parent.isVisibleInScope(name));
    }

    boolean isConstVar(String name) {
      Preconditions.checkArgument(!name.contains("."));
      return constVars.contains(name) ||
          parent != null && parent.isConstVar(name);
    }

    private boolean isOuterVarEarly(String name) {
      Preconditions.checkArgument(!name.contains("."));
      return !isDefinedLocally(name) &&
          parent != null && parent.isVisibleInScope(name);
    }

    boolean isUndeclaredFormal(String name) {
      Preconditions.checkArgument(!name.contains("."));
      return formals.contains(name) && getDeclaredTypeOf(name) == null;
    }

    List<String> getFormals() {
      return new ArrayList<>(formals);
    }

    Set<String> getOuterVars() {
      return new HashSet<>(outerVars);
    }

    Set<String> getLocalFunDefs() {
      return new HashSet<>(localFunDefs.keySet());
    }

    boolean isOuterVar(String name) {
      return outerVars.contains(name);
    }

    boolean hasThis() {
      return isFunction() && getDeclaredType().getThisType() != null;
    }

    private RawNominalType getNominalType(QualifiedName qname) {
      if (qname.isIdentifier()) {
        String name = qname.getLeftmostName();
        RawNominalType rnt = localClassDefs.get(name);
        if (rnt != null) {
          return rnt;
        }
        return parent == null ? null : parent.getNominalType(qname);
      }
      Namespace ns = getNamespace(qname.getLeftmostName());
      if (ns == null) {
        return null;
      }
      return ns.getNominalType(qname.getAllButLeftmost());
    }

    // Only used during symbol-table construction, not during type inference.
    private JSType lookupTypeByQname(QualifiedName qname) {
      Preconditions.checkArgument(!qname.isIdentifier());
      Namespace ns = getNamespace(qname.getLeftmostName());
      if (ns == null) {
        return null;
      }
      RawNominalType rawType = ns.getNominalType(qname.getAllButLeftmost());
      if (rawType == null) {
        return null;
      }
      return rawType.getInstanceAsJSType();
    }

    // Only used during symbol-table construction, not during type inference.
    @Override
    public JSType lookupTypeByName(String name) {
      if (name.contains(".")) {
        JSType type = lookupTypeByQname(QualifiedName.fromQname(name));
        return type != null ? type : getUnresolvedTypeByName(name);
      }

      // First see if it's a type variable
      if (declaredType != null && declaredType.isTypeVariableInScope(name)) {
        return JSType.fromTypeVar(name);
      }
      // Then if it's a class/interface name
      RawNominalType rawNominalType = localClassDefs.get(name);
      if (rawNominalType != null) {
        return rawNominalType.getInstanceAsJSType();
      }
      JSType t = getUnresolvedTypeByName(name);
      if (t != null) {
        return t;
      }
      // O/w keep looking in the parent scope
      return parent == null ? null : parent.lookupTypeByName(name);
    }

    JSType getUnresolvedTypeByName(String name) {
      if (unknownTypeNames.contains(name)) {
        return JSType.UNKNOWN;
      }
      return null;
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
        return getDeclaredType().getThisType().getInstanceAsJSType();
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
        Preconditions.checkState(!localType.isBottom(), "%s was bottom", name);
        return localType;
      }
      JSType externType = externs.get(name);
      if (externType != null) {
        Preconditions.checkState(!externType.isBottom());
        return externType;
      }
      Scope s = localFunDefs.get(name);
      if (s != null && s.getDeclaredType() != null) {
        return getCommonTypes()
            .fromFunctionType(s.getDeclaredType().toFunctionType());
      }
      if (name.equals(this.name)) {
        return getCommonTypes()
            .fromFunctionType(getDeclaredType().toFunctionType());
      }
      if (localNamespaces != null) {
        Namespace ns = localNamespaces.get(name);
        if (ns != null) {
          return ns.toJSType();
        }
      }
      // For names defined in the current scope w/out a declared type
      if (isDefinedLocally(name)) {
        return null;
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
      } else if (parent != null && !isDefinedLocally(fnName)) {
        return parent.getScopeHelper(fnName);
      }
      return null;
    }

    boolean isKnownFunction(String fnName) {
      return getScopeHelper(fnName) != null;
    }

    boolean isExternalFunction(String fnName) {
      Scope s = Preconditions.checkNotNull(getScopeHelper(fnName));
      return s.root.isFromExterns();
    }

    Scope getScope(String fnName) {
      Scope s = getScopeHelper(fnName);
      Preconditions.checkState(s != null);
      return s;
    }

    Set<String> getLocals() {
      return ImmutableSet.copyOf(locals.keySet());
    }

    Set<String> getExterns() {
      return ImmutableSet.copyOf(externs.keySet());
    }

    private void addLocal(String name, JSType declType,
        boolean isConstant, boolean isFromExterns) {
      Preconditions.checkArgument(!isDefinedLocally(name));
      if (isConstant) {
        constVars.add(name);
      }
      if (isFromExterns) {
        externs.put(name, declType);
      } else {
        locals.put(name, declType);
      }
    }

    private void addNamespace(Node qnameNode) {
      Preconditions.checkArgument(!isNamespace(qnameNode));
      if (qnameNode.isName()) {
        localNamespaces.put(qnameNode.getString(), new NamespaceLit());
      } else {
        QualifiedName qname = QualifiedName.fromNode(qnameNode);
        Namespace ns = getNamespace(qname.getLeftmostName());
        ns.addSubnamespace(qname.getAllButLeftmost());
      }
    }

    private void updateType(String name, JSType newDeclType) {
      if (locals.containsKey(name)) {
        locals.put(name, newDeclType);
      } else if (parent != null) {
        parent.updateType(name, newDeclType);
      } else {
        throw new RuntimeException(
            "Cannot update type of unknown variable: " + name);
      }
    }

    private void addOuterVar(String name) {
      outerVars.add(name);
    }

    private void addNominalType(Node qnameNode, RawNominalType rawNominalType) {
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

    private void addTypedef(Node qnameNode, Typedef td) {
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

    @Override
    public Typedef getTypedef(String name) {
      if (!name.contains(".")) {
        if (isDefinedLocally(name)) {
          return localTypedefs.get(name);
        }
      } else {
        QualifiedName qname = QualifiedName.fromQname(name);
        Namespace ns = getNamespace(qname.getLeftmostName());
        if (ns != null) {
          return ns.getTypedef(qname.getAllButLeftmost());
        }
      }
      if (parent != null) {
        return parent.getTypedef(name);
      }
      return null;
    }

    private void addEnum(Node qnameNode, EnumType e) {
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

    @Override
    public EnumType getEnum(String name) {
      if (name == null) {
        return null;
      }
      if (!name.contains(".")) {
        if (isDefinedLocally(name)) {
          return localEnums.get(name);
        }
      } else {
        QualifiedName qname = QualifiedName.fromQname(name);
        Namespace ns = getNamespace(qname.getLeftmostName());
        if (ns != null) {
          return ns.getEnumType(qname.getAllButLeftmost());
        }
      }
      if (parent != null) {
        return parent.getEnum(name);
      }
      return null;
    }

    private Namespace getNamespace(QualifiedName qname) {
      Namespace ns = getNamespace(qname.getLeftmostName());
      return qname.isIdentifier()
          ? ns : ns.getSubnamespace(qname.getAllButLeftmost());
    }

    private Namespace getNamespace(String name) {
      Preconditions.checkArgument(!name.contains("."));
      Namespace ns = localNamespaces.get(name);
      if (ns != null) {
        return ns;
      }
      ns = localClassDefs.get(name);
      if (ns != null) {
        return ns;
      }
      ns = localEnums.get(name);
      if (ns != null) {
        return ns;
      }
      return parent == null ? null : parent.getNamespace(name);
    }

    private void resolveTypedefs(JSTypeCreatorFromJSDoc typeParser) {
      for (Typedef td : localTypedefs.values()) {
        if (!td.isResolved()) {
          typeParser.resolveTypedef(td, this);
        }
      }
    }

    private void resolveEnums(JSTypeCreatorFromJSDoc typeParser) {
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

    private void removeTmpData() {
      unknownTypeNames = null;
      // For now, we put types of namespaces directly into the locals.
      // Alternatively, we could move this into NewTypeInference.initEdgeEnvs
      for (Map.Entry<String, NamespaceLit> entry : localNamespaces.entrySet()) {
        locals.put(entry.getKey(), entry.getValue().toJSType());
      }
      for (Map.Entry<String, EnumType> entry : localEnums.entrySet()) {
        locals.put(entry.getKey(), entry.getValue().toJSType());
      }
      localNamespaces = null;
      localClassDefs = null;
      localTypedefs = null;
      localEnums = null;
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
}
