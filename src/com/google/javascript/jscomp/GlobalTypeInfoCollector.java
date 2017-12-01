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

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.javascript.jscomp.AbstractCompiler.MostRecentTypechecker;
import com.google.javascript.jscomp.CodingConvention.Bind;
import com.google.javascript.jscomp.CodingConvention.DelegateRelationship;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.jscomp.NewTypeInference.WarningReporter;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowCallback;
import com.google.javascript.jscomp.newtypes.Declaration;
import com.google.javascript.jscomp.newtypes.DeclaredFunctionType;
import com.google.javascript.jscomp.newtypes.EnumType;
import com.google.javascript.jscomp.newtypes.FunctionNamespace;
import com.google.javascript.jscomp.newtypes.FunctionType;
import com.google.javascript.jscomp.newtypes.FunctionTypeBuilder;
import com.google.javascript.jscomp.newtypes.JSType;
import com.google.javascript.jscomp.newtypes.JSTypeCreatorFromJSDoc;
import com.google.javascript.jscomp.newtypes.JSTypeCreatorFromJSDoc.FunctionAndSlotType;
import com.google.javascript.jscomp.newtypes.JSTypes;
import com.google.javascript.jscomp.newtypes.Namespace;
import com.google.javascript.jscomp.newtypes.NamespaceLit;
import com.google.javascript.jscomp.newtypes.NominalType;
import com.google.javascript.jscomp.newtypes.NominalTypeBuilderNti;
import com.google.javascript.jscomp.newtypes.ObjectKind;
import com.google.javascript.jscomp.newtypes.QualifiedName;
import com.google.javascript.jscomp.newtypes.RawNominalType;
import com.google.javascript.jscomp.newtypes.Typedef;
import com.google.javascript.jscomp.newtypes.UniqueNameGenerator;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.NominalTypeBuilder;
import com.google.javascript.rhino.SimpleSourceFile;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Populates GlobalTypeInfo.
 *
 * <p>Used by the new type inference. See go/jscompiler-new-type-checker for the
 * latest updates.
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public class GlobalTypeInfoCollector implements CompilerPass {

  static final DiagnosticType DUPLICATE_JSDOC = DiagnosticType.warning(
      "JSC_NTI_DUPLICATE_JSDOC",
      "Found two JsDoc comments for variable: {0}.\n");

  static final DiagnosticType REDECLARED_PROPERTY = DiagnosticType.warning(
      "JSC_NTI_REDECLARED_PROPERTY",
      "Found two declarations for property {0} on {1}.\n");

  static final DiagnosticType INVALID_PROP_OVERRIDE = DiagnosticType.warning(
      "JSC_NTI_INVALID_PROP_OVERRIDE",
      "Invalid redeclaration of property {0}.\n"
          + "inherited type  : {1}\n"
          + "overriding type : {2}\n");

  static final DiagnosticType CTOR_IN_DIFFERENT_SCOPE = DiagnosticType.warning(
      "JSC_NTI_CTOR_IN_DIFFERENT_SCOPE",
      "Modifying the prototype is only allowed if the constructor is "
          + "in the same scope\n");

  static final DiagnosticType UNRECOGNIZED_TYPE_NAME = DiagnosticType.warning(
      "JSC_NTI_UNRECOGNIZED_TYPE_NAME",
      "Type annotation references non-existent type {0}.");

  static final DiagnosticType STRUCT_WITHOUT_CTOR_OR_INTERF = DiagnosticType.warning(
      "JSC_NTI_STRUCT_WITHOUT_CTOR_OR_INTERF",
      "@struct used without @constructor, @interface, or @record.");

  static final DiagnosticType DICT_WITHOUT_CTOR = DiagnosticType.warning(
      "JSC_NTI_DICT_WITHOUT_CTOR",
      "@dict used without @constructor.");

  static final DiagnosticType EXPECTED_CONSTRUCTOR = DiagnosticType.warning(
      "JSC_NTI_EXPECTED_CONSTRUCTOR",
      "Expected constructor name but found {0}.");

  static final DiagnosticType EXPECTED_INTERFACE = DiagnosticType.warning(
      "JSC_NTI_EXPECTED_INTERFACE",
      "Expected interface name but found {0}.");

  static final DiagnosticType INEXISTENT_PARAM = DiagnosticType.warning(
      "JSC_NTI_INEXISTENT_PARAM",
      "parameter {0} does not appear in {1}''s parameter list");

  static final DiagnosticType CONST_WITHOUT_INITIALIZER =
      DiagnosticType.warning(
          "JSC_NTI_CONST_WITHOUT_INITIALIZER",
          "Constants must be initialized when they are defined.");

  static final DiagnosticType COULD_NOT_INFER_CONST_TYPE =
      DiagnosticType.warning(
          "JSC_NTI_COULD_NOT_INFER_CONST_TYPE",
          "All constants must be typed. The compiler could not infer the type "
              + "of constant {0}. Please use an explicit type annotation. "
              + "For more information, see:\n"
              + "https://github.com/google/closure-compiler/wiki/Using-NTI-(new-type-inference)#warnings-about-uninferred-constants");

  static final DiagnosticType MISPLACED_CONST_ANNOTATION =
      DiagnosticType.warning(
          "JSC_NTI_MISPLACED_CONST_ANNOTATION",
          "This property cannot be @const. "
              + "The @const annotation is only allowed for "
              + "properties of namespaces, prototype properties, "
              + "static properties of constructors, and "
              + "properties of the form this.prop declared inside constructors "
              + "and prototype methods.");

  static final DiagnosticType CANNOT_OVERRIDE_FINAL_METHOD =
      DiagnosticType.warning(
          "JSC_NTI_CANNOT_OVERRIDE_FINAL_METHOD",
          "Final method {0} cannot be overriden.");

  static final DiagnosticType CANNOT_INIT_TYPEDEF =
      DiagnosticType.warning(
          "JSC_NTI_CANNOT_INIT_TYPEDEF",
          "A typedef variable represents a type name; it cannot be assigned a value.");

  static final DiagnosticType ANONYMOUS_NOMINAL_TYPE =
      DiagnosticType.warning(
          "JSC_NTI_ANONYMOUS_NOMINAL_TYPE",
          "Must specify a name when defining a class or interface.");

  static final DiagnosticType MALFORMED_ENUM =
      DiagnosticType.warning(
          "JSC_NTI_MALFORMED_ENUM",
          "An enum must be initialized to a non-empty object literal.");

  static final DiagnosticType DUPLICATE_PROP_IN_ENUM =
      DiagnosticType.warning(
          "JSC_NTI_DUPLICATE_PROP_IN_ENUM",
          "Property {0} appears twice in the enum declaration.");

  static final DiagnosticType LENDS_ON_BAD_TYPE =
      DiagnosticType.warning(
          "JSC_NTI_LENDS_ON_BAD_TYPE",
          "May only lend properties to namespaces, constructors and their"
              + " prototypes. Found {0}.");

  static final DiagnosticType INVALID_INTERFACE_PROP_INITIALIZER =
      DiagnosticType.warning(
          "JSC_NTI_INVALID_INTERFACE_PROP_INITIALIZER",
          "Invalid initialization of interface property.");

  static final DiagnosticType SETTER_WITH_RETURN =
      DiagnosticType.warning(
          "JSC_NTI_SETTER_WITH_RETURN",
          "Cannot declare a return type on a setter.");

  static final DiagnosticType WRONG_PARAMETER_COUNT =
      DiagnosticType.warning(
          "JSC_NTI_WRONG_PARAMETER_COUNT",
          "Function definition does not have the declared number of parameters.\n"
              + "Expected: {0}\n"
              + "Found: {1}");

  static final DiagnosticType CANNOT_ADD_PROPERTIES_TO_TYPEDEF =
      DiagnosticType.warning(
          "JSC_NTI_CANNOT_ADD_PROPERTIES_TO_TYPEDEF",
          "A typedef should only be used in type annotations, not as a value."
              + " Adding properties to typedefs is not allowed.");

  static final DiagnosticType ANCESTOR_TYPES_HAVE_INCOMPATIBLE_PROPERTIES =
      DiagnosticType.warning(
          "JSC_NTI_ANCESTOR_TYPES_HAVE_INCOMPATIBLE_PROPERTIES",
          "Type {0} has a property {1} with incompatible types in its ancestor types: {2}");

  static final DiagnosticType ONE_TYPE_FOR_MANY_VARS = DiagnosticType.warning(
      "JSC_NTI_ONE_TYPE_FOR_MANY_VARS",
      "Having one type annotation for multiple variables is not allowed.");

  static final DiagnosticType UNKNOWN_OVERRIDE =
      DiagnosticType.warning(
          "JSC_NTI_UNKNOWN_OVERRIDE",
          "property {0} not defined on any supertype of {1}");

  static final DiagnosticType INTERFACE_METHOD_NOT_IMPLEMENTED =
      DiagnosticType.warning(
          "JSC_NTI_INTERFACE_METHOD_NOT_IMPLEMENTED",
          "property {0} on interface {1} is not implemented by type {2}");

  static final DiagnosticType INTERFACE_METHOD_NOT_EMPTY =
      DiagnosticType.warning(
          "JSC_NTI_INTERFACE_METHOD_NOT_EMPTY",
          "interface member functions must have an empty body");

  static final DiagnosticType ABSTRACT_METHOD_IN_CONCRETE_CLASS =
      DiagnosticType.warning(
          "JSC_NTI_ABSTRACT_METHOD_IN_CONCRETE_CLASS",
          "Abstract methods can only appear in abstract classes. "
              + "Please declare class {0} as @abstract");

  static final DiagnosticType ABSTRACT_METHOD_IN_INTERFACE =
      DiagnosticType.warning(
          "JSC_NTI_ABSTRACT_METHOD_IN_INTERFACE",
          "Abstract methods cannot appear in interfaces");

  static final DiagnosticType ABSTRACT_METHOD_NOT_IMPLEMENTED_IN_CONCRETE_CLASS =
      DiagnosticType.warning(
          "JSC_NTI_ABSTRACT_METHOD_NOT_IMPLEMENTED_IN_CONCRETE_CLASS",
          "Abstract method {0} from superclass {1} not implemented");

  static final DiagnosticGroup COMPATIBLE_DIAGNOSTICS = new DiagnosticGroup(
      ABSTRACT_METHOD_IN_CONCRETE_CLASS,
      CANNOT_OVERRIDE_FINAL_METHOD,
      DICT_WITHOUT_CTOR,
      DUPLICATE_PROP_IN_ENUM,
      EXPECTED_CONSTRUCTOR,
      EXPECTED_INTERFACE,
      INEXISTENT_PARAM,
      INTERFACE_METHOD_NOT_IMPLEMENTED,
      INTERFACE_METHOD_NOT_EMPTY,
      INVALID_INTERFACE_PROP_INITIALIZER,
      INVALID_PROP_OVERRIDE,
      LENDS_ON_BAD_TYPE,
      ONE_TYPE_FOR_MANY_VARS,
      REDECLARED_PROPERTY,
      STRUCT_WITHOUT_CTOR_OR_INTERF,
      ANCESTOR_TYPES_HAVE_INCOMPATIBLE_PROPERTIES,
      UNKNOWN_OVERRIDE,
      UNRECOGNIZED_TYPE_NAME,
      WRONG_PARAMETER_COUNT);

  static final DiagnosticGroup NEW_DIAGNOSTICS = new DiagnosticGroup(
      ABSTRACT_METHOD_IN_INTERFACE,
      ABSTRACT_METHOD_NOT_IMPLEMENTED_IN_CONCRETE_CLASS,
      ANONYMOUS_NOMINAL_TYPE,
      CANNOT_ADD_PROPERTIES_TO_TYPEDEF,
      CANNOT_INIT_TYPEDEF,
      CONST_WITHOUT_INITIALIZER,
      COULD_NOT_INFER_CONST_TYPE,
      CTOR_IN_DIFFERENT_SCOPE,
      DUPLICATE_JSDOC,
      MALFORMED_ENUM,
      MISPLACED_CONST_ANNOTATION,
      SETTER_WITH_RETURN);

  private WarningReporter warnings;
  private final transient AbstractCompiler compiler;
  private final CodingConvention convention;
  // Uses %, which is not allowed in identifiers, to avoid naming clashes
  // with existing functions.
  private static final String ANON_FUN_PREFIX = "%anon_fun";
  private static final String WINDOW_INSTANCE = "window";
  private static final String WINDOW_CLASS = "Window";

  private DefaultNameGenerator funNameGen;
  // Only for original definitions, not for aliased constructors
  private Map<Node, RawNominalType> nominaltypesByNode = new LinkedHashMap<>();
  // propertyDefs collects places in the AST where properties are defined.
  // For properties that are methods, PropertyDef includes some extra information.
  // We use propertyDefs to handle inheritance issues, e.g., invalid property overrides.
  // Keyed on RawNominalTypes and property names.
  private HashBasedTable<RawNominalType, String, PropertyDef> propertyDefs =
      HashBasedTable.create();
  private final Set<RawNominalType> inProgressFreezes = new LinkedHashSet<>();
  private final GlobalTypeInfo globalTypeInfo;
  private final SimpleInference simpleInference;
  private final OrderedExterns orderedExterns;
  private RawNominalType window;
  // This list is populated during CollectNamedTypes and is complete during ProcessScope.
  private final List<NTIScope> scopes;

  public GlobalTypeInfoCollector(AbstractCompiler compiler) {
    this.warnings = new WarningReporter(compiler);
    this.compiler = compiler;
    this.funNameGen = new DefaultNameGenerator(ImmutableSet.<String>of(), "", null);
    this.globalTypeInfo = compiler.getGlobalTypeInfo();
    this.simpleInference = new SimpleInference(this.globalTypeInfo);
    this.convention = compiler.getCodingConvention();
    this.orderedExterns = new OrderedExterns();
    this.scopes = new ArrayList<>();
  }

  @Override
  public void process(Node externs, Node root) {
    checkNotNull(warnings, "Cannot rerun GlobalTypeInfoCollector.process");
    checkArgument(externs == null || externs.isRoot());
    checkArgument(root.isRoot(), "Root must be ROOT, but is %s", root.getToken());

    this.compiler.setMostRecentTypechecker(MostRecentTypechecker.NTI);

    NTIScope globalScope = new NTIScope(root, null, ImmutableList.<String>of(), getCommonTypes());
    globalScope.addUnknownTypeNames(this.globalTypeInfo.getUnknownTypeNames());
    this.globalTypeInfo.setGlobalScope(globalScope);
    this.scopes.add(globalScope);

    // Processing of a scope is split into many separate phases, and it's not
    // straightforward to remember which phase does what.

    // (1) Find names of classes, interfaces, typedefs, enums, and namespaces
    //   defined in the global scope.
    CollectNamedTypes rootCnt = new CollectNamedTypes(globalScope);
    NodeTraversal.traverseEs6(this.compiler, externs, this.orderedExterns);
    rootCnt.collectNamedTypesInExterns();
    defineObjectAndFunctionIfMissing();
    NodeTraversal.traverseEs6(compiler, root, rootCnt);
    // (2) Determine the type represented by each typedef and each enum
    globalScope.resolveTypedefs(getTypeParser());
    globalScope.resolveEnums(getTypeParser());
    // (3) Repeat steps 1-2 for all the other scopes (outer-to-inner)
    for (int i = 1; i < this.scopes.size(); i++) {
      NTIScope s = this.scopes.get(i);
      CollectNamedTypes cnt = new CollectNamedTypes(s);
      NodeTraversal.traverseEs6(compiler, s.getBody(), cnt);
      s.resolveTypedefs(getTypeParser());
      s.resolveEnums(getTypeParser());
      if (NewTypeInference.measureMem) {
        NewTypeInference.updatePeakMem();
      }
    }

    // (4) The bulk of the global-scope processing happens here:
    //     - Create scopes for functions
    //     - Declare properties on types
    ProcessScope rootPs = new ProcessScope(globalScope);
    if (externs != null) {
      NodeTraversal.traverseEs6(compiler, externs, rootPs);
    }
    NodeTraversal.traverseEs6(compiler, root, rootPs);
    // (5) Things that must happen after the traversal of the scope
    rootPs.finishProcessingScope();

    // (6) Repeat steps 4-5 for all the other scopes (outer-to-inner)
    for (int i = 1; i < this.scopes.size(); i++) {
      NTIScope s = this.scopes.get(i);
      ProcessScope ps = new ProcessScope(s);
      NodeTraversal.traverseEs6(compiler, s.getBody(), ps);
      ps.finishProcessingScope();
      if (NewTypeInference.measureMem) {
        NewTypeInference.updatePeakMem();
      }
    }

    // (7) Adjust types of properties based on inheritance information.
    //     Report errors in the inheritance chain. Do Window last.
    Collection<RawNominalType> windows = new ArrayList<>();
    for (Map.Entry<Node, RawNominalType> entry : nominaltypesByNode.entrySet()) {
      RawNominalType rawType = entry.getValue();
      if (this.window != null && rawType.hasAncestorClass(this.window)) {
        windows.add(rawType);
        continue;
      }
      checkAndFreezeNominalType(rawType);
    }
    JSType globalThisType = null;
    if (this.window != null) {
      // Copy properties from window to Window.prototype, because sometimes
      // people pass window around rather than using it directly.
      Namespace winNs = globalScope.getNamespace(WINDOW_INSTANCE);
      if (winNs != null) {
        winNs.copyWindowProperties(getCommonTypes(), this.window);
      }
      for (RawNominalType rawType : windows) {
        checkAndFreezeNominalType(rawType);
      }
      if (winNs != null) {
        ((NamespaceLit) winNs).setWindowType(this.window.getAsNominalType());
        // Type the global THIS as window
        globalThisType = winNs.toJSType();
      }
    }
    if (globalThisType == null) {
      // Type the global THIS as a loose object
      globalThisType = getCommonTypes().getTopObject().withLoose();
    }
    getCommonTypes().setGlobalThis(globalThisType);
    globalScope.setDeclaredType(
        (new FunctionTypeBuilder(getCommonTypes())).
            addReceiverType(globalThisType).buildDeclaration());

    this.globalTypeInfo.setRawNominalTypes(nominaltypesByNode.values());
    nominaltypesByNode = null;
    propertyDefs = null;
    for (NTIScope s : this.scopes) {
      s.freezeScope();
    }
    this.simpleInference.setScopesAreFrozen();

    // Traverse the externs and annotate them with types.
    // Only works for the top level, not inside function bodies.
    NodeTraversal.traverseEs6(
        this.compiler, externs, new NodeTraversal.AbstractShallowCallback() {
          @Override
          public void visit(NodeTraversal t, Node n, Node parent) {
            if (n.isQualifiedName()) {
              Declaration d = getGlobalScope().getDeclaration(QualifiedName.fromNode(n), false);
              JSType type = simpleInferDeclaration(d);
              if (type == null) {
                type = simpleInferExpr(n, getGlobalScope());
              }
              // Type-based passes expect the externs to be annotated, so use ? when type is null.
              n.setTypeI(type != null ? type : getCommonTypes().UNKNOWN);
            }
          }
        });

    Map<Node, String> unknownTypes = getTypeParser().getUnknownTypesMap();
    for (Map.Entry<Node, String> unknownTypeEntry : unknownTypes.entrySet()) {
      this.warnings.add(JSError.make(unknownTypeEntry.getKey(),
          UNRECOGNIZED_TYPE_NAME, unknownTypeEntry.getValue()));
    }
    // The jsdoc parser doesn't have access to the error functions in the jscomp
    // package, so we collect its warnings here.
    for (JSError warning : getTypeParser().getWarnings()) {
      this.warnings.add(warning);
    }
    this.warnings = null;
    this.funNameGen = null;

    reorderScopesForNTI();

    this.compiler.setExternProperties(ImmutableSet.copyOf(getExternPropertyNames()));
  }

  /**
   * After ProcessScope, scopes are stored in a list; shallower scopes appear before deeper
   * scopes (i.e., the top level is first). This method rearranges them in the order in which
   * we will process them in NewTypeInference. The order in which scopes are processed in NTI is
   * crucial for good type checking, because each function is only checked once; there is no
   * global-fixpoint phase.
   *
   * Unannotated callbacks are processed at the end. This means that by the time we typecheck a
   * callback, we have checked the surrounding scope, and we have inferred a good signature.
   *
   * For the rest of the scopes, we process deeper scopes before shallower scopes
   * (the top level is processed last). Then, if a declared function f is called in the same
   * scope S in which it is defined, we typecheck S after f, and we have a function summary for f.
   *
   * TODO(dimvar): We also want to use summaries when type checking method calls to unannotated
   * methods. To do that, we will need to process all methods in the beginning, and then the
   * remaining scopes.
   */
  private void reorderScopesForNTI() {
    List<NTIScope> scopes = this.scopes;
    ArrayList<NTIScope> result = new ArrayList<>(scopes.size());
    ArrayList<NTIScope> callbacks = new ArrayList<>();
    for (int i = scopes.size() - 1; i >= 0; i--) {
      NTIScope s = scopes.get(i);
      if (NodeUtil.isUnannotatedCallback(s.getRoot())) {
        callbacks.add(s);
      } else {
        result.add(s);
      }
    }
    // Outer unannotated callbacks are processed before inner unannotated callbacks, so that if
    // a callback contains another callback, the outer one is analyzed first.
    Collections.reverse(callbacks);
    result.addAll(callbacks);
    this.globalTypeInfo.setScopes(result);
  }

  private void setWindow(RawNominalType rawType) {
    this.window = rawType;
  }

  private static boolean isWindowRawType(RawNominalType rawType) {
    return rawType.getName().equals(WINDOW_CLASS) && rawType.getDefSite().isFromExterns();
  }

  private RawNominalType dummyRawTypeForMissingExterns(String name) {
    Node defSite = NodeUtil.emptyFunction();
    defSite.setStaticSourceFile(new SimpleSourceFile("", true));
    return RawNominalType.makeClass(
        getCommonTypes(), defSite, name, ImmutableList.of(), ObjectKind.UNRESTRICTED, false);
  }

  private void defineObjectAndFunctionIfMissing() {
    JSTypes commonTypes = getCommonTypes();
    if (commonTypes.getObjectType() == null) {
      commonTypes.setObjectType(dummyRawTypeForMissingExterns("Object"));
    }
    if (commonTypes.getLiteralObjNominalType() == null) {
      RawNominalType objLitRawType = dummyRawTypeForMissingExterns(JSTypes.OBJLIT_CLASS_NAME);
      objLitRawType.addSuperClass(commonTypes.getObjectType());
      commonTypes.setLiteralObjNominalType(objLitRawType);
    }
    if (commonTypes.getFunctionType() == null) {
      commonTypes.setFunctionType(dummyRawTypeForMissingExterns("Function"));
    }
  }

  private ImmutableCollection<PropertyDef> getPropDefsFromType(NominalType nt, String pname) {
    if (nt.isClass()) {
      PropertyDef propdef = getPropDefFromClass(nt, pname);
      // propdef can be null when nt is a class created by a mixin application. The class may
      // have prototype properties but we can't see where these properties are defined.
      return propdef == null ? ImmutableSet.of() : ImmutableSet.of(propdef);
    }
    return getPropDefsFromInterface(nt, pname);
  }

  private ImmutableCollection<PropertyDef> getPropDefsFromInterface(
      NominalType nominalType, String pname) {
    checkArgument(nominalType.isFrozen());
    checkArgument(nominalType.isInterface() || nominalType.isBuiltinObject());
    if (nominalType.getPropDeclaredType(pname) == null) {
      return ImmutableSet.of();
    } else if (propertyDefs.get(nominalType.getId(), pname) != null) {
      PropertyDef propDef = propertyDefs.get(nominalType.getId(), pname);
      return ImmutableSet.of(
          nominalType.isGeneric() ? propDef.substituteNominalGenerics(nominalType) : propDef);
    }
    ImmutableSet.Builder<PropertyDef> result = ImmutableSet.builder();
    for (NominalType interf : nominalType.getInstantiatedInterfaces()) {
      result.addAll(getPropDefsFromInterface(interf, pname));
    }
    return result.build();
  }

  private PropertyDef getPropDefFromClass(NominalType nominalType, String pname) {
    while (nominalType.getPropDeclaredType(pname) != null) {
      checkArgument(nominalType.isFrozen());
      checkArgument(nominalType.isClass());
      if (propertyDefs.get(nominalType.getId(), pname) != null) {
        PropertyDef propDef = propertyDefs.get(nominalType.getId(), pname);
        return nominalType.isGeneric() ? propDef.substituteNominalGenerics(nominalType) : propDef;
      }
      nominalType = nominalType.getInstantiatedSuperclass();
    }
    return null;
  }

  /**
   * Reconcile the properties of this nominal type with the properties it inherits from its
   * supertypes, and then freeze the type.
   *
   * NOTE(dimvar): The logic is somewhat convoluted, but I'm not sure how to make it clearer.
   * There is just a lot of case analysis involved.
   *
   * - Collect all super definitions of methods and other properties.
   * - During the collection process, warn when a property on this type is not a subtype of
   *   an inherited property on the supertype.
   * - If there are multiple inherited definitions for a property, meet them to find a single
   *   inherited type. If they meet to bottom, warn.
   * - If this type has its own definition of the property, and not just inherits it, adjust the
   *   type of the local definition based on the inherited property type.
   */
  private void checkAndFreezeNominalType(RawNominalType rawType) {
    if (rawType.isFrozen()) {
      return;
    }
    checkState(inProgressFreezes.add(rawType),
        "Cycle in freeze order: %s (%s)", rawType, inProgressFreezes);
    NominalType superClass = rawType.getSuperClass();
    Set<String> nonInheritedPropNames = rawType.getAllNonInheritedProps();
    if (superClass != null && !superClass.isFrozen()) {
      checkAndFreezeNominalType(superClass.getRawNominalType());
    }
    for (NominalType superInterf : rawType.getInterfaces()) {
      if (!superInterf.isFrozen()) {
        checkAndFreezeNominalType(superInterf.getRawNominalType());
      }
    }

    // If this type defines a method m, collect all inherited definitions of m here in order
    // to possibly adjust the type of m.
    Multimap<String, DeclaredFunctionType> superMethodTypes = LinkedHashMultimap.create();
    // For each property p on this type, regardless of whether it is defined on the type or just
    // inherited, collect all inherited definitions of p here.
    Multimap<String, JSType> superPropTypes = LinkedHashMultimap.create();
    // Collect inherited types for extended classes
    if (superClass != null) {
      checkState(superClass.isFrozen());
      // TODO(blickly): Can we optimize this to skip unnecessary iterations?
      for (String pname : superClass.getPropertyNames()) {
        if (superClass.isAbstractClass()
            && superClass.hasAbstractMethod(pname)
            && !rawType.isAbstractClass()
            && !rawType.mayHaveOwnNonStrayProp(pname)) {
          warnings.add(JSError.make(
              rawType.getDefSite(), ABSTRACT_METHOD_NOT_IMPLEMENTED_IN_CONCRETE_CLASS,
              pname, superClass.getName()));
        }
        nonInheritedPropNames.remove(pname);
        checkSuperProperty(rawType, superClass, pname, superMethodTypes, superPropTypes);
      }
    }

    // Collect inherited types for extended/implemented interfaces
    for (NominalType superInterf : rawType.getInterfaces()) {
      checkState(superInterf.isFrozen());
      for (String pname : superInterf.getPropertyNames()) {
        nonInheritedPropNames.remove(pname);
        checkSuperProperty(rawType, superInterf, pname, superMethodTypes, superPropTypes);
      }
    }

    // Munge inherited types of methods
    for (String pname : superMethodTypes.keySet()) {
      Collection<DeclaredFunctionType> methodTypes = superMethodTypes.get(pname);
      checkState(!methodTypes.isEmpty());
      PropertyDef localPropDef = checkNotNull(propertyDefs.get(rawType, pname));
      // To find the declared type of a method, we must meet declared types
      // from all inherited methods.
      DeclaredFunctionType superMethodType = DeclaredFunctionType.meet(methodTypes);
      DeclaredFunctionType localMethodType = localPropDef.methodType;
      boolean getsTypeFromParent = getsTypeInfoFromParentMethod(localPropDef);
      if (superMethodType == null) {
        // If the inherited types are not compatible, pick one.
        superMethodType = methodTypes.iterator().next();
      } else if (getsTypeFromParent
          && localMethodType.getMaxArity() > superMethodType.getMaxArity()) {
        // When getsTypeFromParent is true, we miss the invalid override earlier, so we check here.
        warnings.add(JSError.make(
            localPropDef.defSite, INVALID_PROP_OVERRIDE, pname,
            superMethodType.toFunctionType().toString(),
            localMethodType.toFunctionType().toString()));
      }
      DeclaredFunctionType updatedMethodType =
          localMethodType.withTypeInfoFromSuper(superMethodType, getsTypeFromParent);
      localPropDef.updateMethodType(updatedMethodType);
      superPropTypes.put(pname,
          getCommonTypes().fromFunctionType(updatedMethodType.toFunctionType()));
    }

    // Check inherited types of all properties
    for (String pname : superPropTypes.keySet()) {
      Collection<JSType> defs = superPropTypes.get(pname);
      checkState(!defs.isEmpty());
      JSType inheritedPropType = getCommonTypes().TOP;
      for (JSType inheritedType : defs) {
        inheritedPropType = JSType.meet(inheritedPropType, inheritedType);
        if (inheritedPropType.isBottom()) {
          warnings.add(
              JSError.make(
                  rawType.getDefSite(),
                  ANCESTOR_TYPES_HAVE_INCOMPATIBLE_PROPERTIES,
                  rawType.getName(),
                  pname,
                  defs.toString()));
          break;
        }
      }
      // Adjust the type of the local property definition based on the inherited type.
      PropertyDef localPropDef = propertyDefs.get(rawType, pname);
      if (localPropDef != null) {
        JSType updatedPropType;
        if (localPropDef.methodType == null) {
          JSType t = rawType.getInstancePropDeclaredType(pname);
          updatedPropType = t == null ? inheritedPropType : t.specialize(inheritedPropType);
        } else {
          // When the property is a method, the local type already includes the meet of the
          // inherited types, from the earlier loop. We don't use inheritedPropType in this branch,
          // because if the inherited method type has static properties (e.g., framework-specific
          // passes can add such properties), we don't want to inherit these.
          FunctionType ft = localPropDef.methodType.toFunctionType();
          updatedPropType = getCommonTypes().fromFunctionType(ft);
        }
        // TODO(dimvar): check if we can have @const props here
        rawType.addProtoProperty(pname, null, updatedPropType, false);
      }
    }

    // Warn when inheriting from incompatible IObject types
    if (rawType.inheritsFromIObject()) {
      JSType wrapped = rawType.getInstanceAsJSType();
      if (wrapped.getIndexType() == null) {
        warnings.add(
            JSError.make(
                rawType.getDefSite(),
                ANCESTOR_TYPES_HAVE_INCOMPATIBLE_PROPERTIES,
                rawType.getName(),
                "IObject<K,V>#index",
                "the keys K have types that can't be joined."));
      } else if (wrapped.getIndexedType() == null) {
        warnings.add(
            JSError.make(
                rawType.getDefSite(),
                ANCESTOR_TYPES_HAVE_INCOMPATIBLE_PROPERTIES,
                rawType.getName(),
                "IObject<K,V>#index",
                "the values V should have a common subtype."));
      }
    }

    // Warn for a prop declared with @override that isn't overriding anything.
    for (String pname : nonInheritedPropNames) {
      PropertyDef propDef = propertyDefs.get(rawType, pname);
      if (propDef != null) {
        Node propDefsite = propDef.defSite;
        JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(propDefsite);
        if (jsdoc != null && jsdoc.isOverride()) {
          warnings.add(JSError.make(propDefsite, UNKNOWN_OVERRIDE, pname, rawType.getName()));
        }
      }
    }

    // Freeze nominal type once all properties are added.
    rawType.freeze();
    if (rawType.isBuiltinObject()) {
      NominalType literalObj = getCommonTypes().getLiteralObjNominalType();
      if (!literalObj.isFrozen()) {
        literalObj.getRawNominalType().freeze();
      }
    }
    inProgressFreezes.remove(rawType);
  }

  /**
   * Collect inherited property definitions and warn if the local definition of a property
   * disagrees with the inherited definition.
   */
  private void checkSuperProperty(
      RawNominalType current, NominalType superType, String pname,
      Multimap<String, DeclaredFunctionType> superMethodTypes,
      Multimap<String, JSType> superPropTypes) {
    JSType inheritedPropType = superType.getPropDeclaredType(pname);
    if (inheritedPropType == null) {
      // No need to go further for undeclared props.
      return;
    }

    Collection<PropertyDef> inheritedPropDefs = getPropDefsFromType(superType, pname);

    if (current.isClass() && superType.isInterface()) {
      // If a class is defined by mixin application, add missing property defs from the
      // super interface, o/w checkSuperProperty will break for its subclasses.
      if (isCtorDefinedByCall(current)) {
        for (PropertyDef inheritedDef : inheritedPropDefs) {
          if (!current.mayHaveProp(pname)) {
            propertyDefs.put(current, pname, inheritedDef);
          }
        }
      } else if (!current.mayHaveNonStrayProp(pname)) {
        warnings.add(JSError.make(
            inheritedPropDefs.iterator().next().defSite,
            INTERFACE_METHOD_NOT_IMPLEMENTED,
            pname, superType.toString(), current.toString()));
        return;
      }
    }

    PropertyDef localPropDef = propertyDefs.get(current, pname);
    JSType localPropType = localPropDef == null ? null : current.getInstancePropDeclaredType(pname);

    if (localPropType != null
        && superType.isClass()
        && superType.hasConstantProp(pname)
        && localPropType.getFunType() != null) {
      // TODO(dimvar): This doesn't work for multiple levels in the hierarchy.
      // Clean up how we process inherited properties and then fix this.
      warnings.add(JSError.make(localPropDef.defSite, CANNOT_OVERRIDE_FINAL_METHOD, pname));
      return;
    }

    if (localPropType != null
        && !getsTypeInfoFromParentMethod(localPropDef)
        && !isValidOverride(localPropType, inheritedPropType)) {
      warnings.add(JSError.make(
          localPropDef.defSite, INVALID_PROP_OVERRIDE, pname,
          inheritedPropType.toString(), localPropType.toString()));
      return;
    }

    superPropTypes.put(pname, inheritedPropType);
    if (localPropType != null && localPropDef.methodType != null) {
      // If we are looking at a method definition, munging may be needed
      for (PropertyDef inheritedPropDef : inheritedPropDefs) {
        if (inheritedPropDef.methodType != null) {
          superMethodTypes.put(pname, inheritedPropDef.methodType);
        }
      }
    }
  }

  private boolean isValidOverride(JSType localPropType, JSType inheritedPropType) {
    FunctionType localFunType = localPropType.getFunType();
    FunctionType inheritedFunType = inheritedPropType.getFunType();
    if (localFunType == null) {
      return localPropType.isSubtypeOf(inheritedPropType);
    } else if (inheritedFunType == null) {
      return false;
    } else {
      return localFunType.isValidOverride(inheritedFunType);
    }
  }

  private static boolean getsTypeInfoFromParentMethod(PropertyDef pd) {
    if (pd == null || pd.methodType == null) {
      return false;
    }
    JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(pd.defSite);
    if (jsdoc == null) {
      return true;
    }
    return (jsdoc.isOverride() || jsdoc.isExport()) && !jsdoc.containsFunctionDeclaration();
  }

  private boolean isAliasedTypedef(Node lhsQnameNode, NTIScope s) {
    return getAliasedTypedef(lhsQnameNode, s) != null;
  }

  /**
   * If lhs represents the lhs of a typedef-aliasing statement, return that typedef.
   */
  @Nullable
  private Typedef getAliasedTypedef(Node lhs, NTIScope s) {
    if (!NodeUtil.isAliasedConstDefinition(lhs)) {
      return null;
    }
    Node rhs = NodeUtil.getRValueOfLValue(lhs);
    checkState(rhs != null && rhs.isQualifiedName());
    return s.getTypedef(QualifiedName.fromNode(rhs));
  }

  /**
   * Each node in the iterable is either a function expression or a statement.
   * The statement can be of: a function, a var, an expr_result containing an assignment,
   * or an expr_result containing a getprop.
   * The statement represents an externs definition of a qualified name.
   * We iterate over qnames from shorter (ie, variables) to longer.
   * For qnames with the same length, we visit them in the order in which they are defined
   * in the source.
   */
  private static class OrderedExterns extends AbstractShallowCallback implements Iterable<Node> {
    /**
     * treeKeys ensures that the iteration will be in increasing order of qname length:
     * variables first, simple getprops second, and so on.
     * arrayListValues ensures that for qnames of the same length, the order of iteration
     * follows the order of the definitions in the source.
     */
    final ListMultimap<Integer, Node> orderedExternDefs =
        MultimapBuilder.treeKeys().arrayListValues().build();

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case VAR:
        case FUNCTION:
          addDefinition(n);
          break;
        case EXPR_RESULT: {
          Node expr = n.getFirstChild();
          if (expr.isAssign() || expr.isGetProp()) {
            addDefinition(n);
          }
          break;
        }
        default:
          break;
      }
    }

    Node getDefinedQname(Node definition) {
      switch (definition.getToken()) {
        case VAR:
          return definition.getFirstChild();
        case FUNCTION: {
          Node name = NodeUtil.getNameNode(definition);
          // Return a non-null node for anonymous functions
          return name == null ? definition.getFirstChild() : name;
        }
        case EXPR_RESULT: {
          Node expr = definition.getFirstChild();
          if (expr.isGetProp()) {
            return expr;
          }
          checkState(expr.isAssign(), "Expected assignment, found %s", expr);
          return expr.getFirstChild();
        }
        default:
          throw new RuntimeException("Unexpected definition " + definition);
      }
    }

    void addDefinition(Node definition) {
      Node qname = getDefinedQname(definition);
      int len = NodeUtil.getLengthOfQname(qname);
      this.orderedExternDefs.put(len, definition);
    }

    @Override
    public Iterator<Node> iterator() {
      return orderedExternDefs.values().iterator();
    }
  }

  /**
   * Collects names of classes, interfaces, namespaces, typedefs and enums.
   * This way, if a type name appears before its declaration, we know what it refers to.
   */
  private class CollectNamedTypes extends AbstractShallowCallback {
    private final NTIScope currentScope;
    private Node nameNodeDefiningWindow = null;

    CollectNamedTypes(NTIScope s) {
      this.currentScope = s;
    }

    void collectNamedTypesInExterns() {
      for (Node definition : orderedExterns) {
        visitNode(definition);
      }
      // Visit the definition of window last, to ensure that the Window type has been defined.
      if (this.nameNodeDefiningWindow != null) {
        visitWindowVar(nameNodeDefiningWindow);
      }
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      visitNode(n);
    }

    public void visitNode(Node n) {
      switch (n.getToken()) {
        case FUNCTION:
          visitFunctionEarly(n);
          break;
        case VAR:
          visitVar(n);
          break;
        case NAME:
          visitName(n);
          break;
        case EXPR_RESULT:
          visitExprResult(n);
          break;
        default:
          break;
      }
    }

    private void visitExprResult(Node n) {
      checkArgument(n.isExprResult());
      Node expr = n.getFirstChild();
      switch (expr.getToken()) {
        case ASSIGN:
          Node lhs = expr.getFirstChild();
          if (isCtorDefinedByCall(lhs)) {
            visitNewCtorDefinedByCall(lhs);
            return;
          }
          if (!lhs.isGetProp()) {
            return;
          }
          expr = lhs;
          // fall through
        case GETPROP:
          if (isCtorWithoutFunctionLiteral(expr)) {
            visitNewCtorWithoutFunctionLiteral(expr);
            return;
          }
          if (isPrototypeProperty(expr)
              || NodeUtil.referencesThis(expr)
              || !expr.isQualifiedName()) {
            // Class & prototype properties are handled in ProcessScope
            return;
          }
          processQualifiedDefinition(expr);
          break;
        default:
          break;
      }
    }

    private void visitName(Node n) {
      checkArgument(n.isName());
      if (this.currentScope.isFunction()) {
        NTIScope.mayRecordEscapedVar(this.currentScope, n.getString());
      }
    }

    private void visitVar(Node n) {
      checkArgument(n.isVar());
      Node nameNode = n.getFirstChild();
      String varName = nameNode.getString();
      if (NodeUtil.isNamespaceDecl(nameNode)) {
        visitObjlitNamespace(new QualifiedName(varName), nameNode);
      } else if (NodeUtil.isTypedefDecl(nameNode)) {
        visitTypedef(nameNode);
      } else if (NodeUtil.isEnumDecl(nameNode)) {
        visitEnum(nameNode);
      } else if (isAliasedTypedef(nameNode, this.currentScope)) {
        visitAliasedTypedef(nameNode);
      } else if (isAliasedNamespaceDefinition(nameNode)) {
        visitAliasedNamespace(new QualifiedName(varName), nameNode);
      } else if (varName.equals(WINDOW_INSTANCE) && nameNode.isFromExterns()) {
        this.nameNodeDefiningWindow = nameNode;
        // We call visitWindowVar at the end, to ensure the Window type has been defined.
        // Add a dummy extern here to define the variable as an extern.
        // We don't have a unit test for this, but it is needed to avoid spuriously registering
        // window as a non-extern typed ? in some builds.
        this.currentScope.addDeclaredLocal(WINDOW_INSTANCE, getCommonTypes().UNKNOWN, false, true);
      } else if (isCtorDefinedByCall(nameNode)) {
        visitNewCtorDefinedByCall(nameNode);
      } else if (isCtorWithoutFunctionLiteral(nameNode)) {
        visitNewCtorWithoutFunctionLiteral(nameNode);
      }
      if (!n.isFromExterns()
          && !this.currentScope.isDefinedLocally(varName, false)) {
        // Add a dummy local to avoid shadowing errors, and to calculate escaped variables.
        this.currentScope.addDeclaredLocal(varName, getCommonTypes().UNKNOWN, false, false);
      }
    }

    private void visitWindowVar(Node nameNode) {
      NTIScope scope = getGlobalScope();
      JSType typeInJsdoc = getVarTypeFromAnnotation(nameNode, scope);
      if (!scope.isDefinedLocally(WINDOW_INSTANCE, false)) {
        scope.addDeclaredLocal(WINDOW_INSTANCE, typeInJsdoc, false, true);
        return;
      }
      // The externs may contain multiple definitions of window, or they may add
      // properties to the window instance before "var window" is defined.
      // In those cases, we want to pick the definition that has the Window
      // nominal type.
      NominalType maybeWin = typeInJsdoc == null
          ? null : typeInJsdoc.getNominalTypeIfSingletonObj();
      if (maybeWin != null && maybeWin.getName().equals(WINDOW_CLASS)) {
        scope.addDeclaredLocal(WINDOW_INSTANCE, typeInJsdoc, false, true);
      }
    }

    private void processQualifiedDefinition(Node qnameNode) {
      checkArgument(qnameNode.isGetProp());
      checkArgument(qnameNode.isQualifiedName());
      Node recv = qnameNode.getFirstChild();
      if (!this.currentScope.isNamespace(recv)
          && !mayCreateFunctionNamespace(recv)
          && !mayCreateWindowNamespace(recv)) {
        return;
      }
      if (NodeUtil.isNamespaceDecl(qnameNode)) {
        visitObjlitNamespace(QualifiedName.fromNode(qnameNode), qnameNode);
      } else if (NodeUtil.isTypedefDecl(qnameNode)) {
        visitTypedef(qnameNode);
      } else if (NodeUtil.isEnumDecl(qnameNode)) {
        visitEnum(qnameNode);
      } else if (isAliasedTypedef(qnameNode, this.currentScope)) {
        visitAliasedTypedef(qnameNode);
      } else if (isAliasedNamespaceDefinition(qnameNode)) {
        visitAliasedNamespace(QualifiedName.fromNode(qnameNode), qnameNode);
      } else if (isAliasingGlobalThis(qnameNode)) {
        visitGlobalThisAlias(qnameNode);
      } else if (isQualifiedFunctionDefinition(qnameNode)) {
        maybeAddFunctionToNamespace(qnameNode);
      }
    }

    private boolean isAliasingGlobalThis(Node qnameNode) {
      return convention.isAliasingGlobalThis(qnameNode.getParent())
          && this.currentScope.isTopLevel();
    }

    private void visitGlobalThisAlias(Node qnameNode) {
      Namespace ns = getGlobalScope().getNamespace(WINDOW_INSTANCE);
      if (ns != null) {
        qnameNode.getParent().putBooleanProp(Node.ANALYZED_DURING_GTI, true);
        this.currentScope.addNamespace(qnameNode, ns);
      }
    }

    private boolean isAliasedNamespaceDefinition(Node qnameNode) {
      Node rhs = NodeUtil.getRValueOfLValue(qnameNode);
      if (rhs == null || !rhs.isQualifiedName()) {
        return false;
      }
      Node parent = qnameNode.getParent();
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(qnameNode);
      if (jsdoc != null) {
        return jsdoc.isConstructorOrInterface() || jsdoc.hasConstAnnotation();
      }
      // You can only alias variable namespaces at declaration, not at a random assignment later.
      if (qnameNode.isName() && !parent.isVar()) {
        return false;
      }
      if (!this.currentScope.isNamespace(rhs)) {
        return false;
      }
      return qnameNode.isFromExterns()
          // An ES6-module default export is transpiled to an assignment without @const,
          // but we still consider it an alias.
          || (qnameNode.isGetProp()
              && qnameNode.getLastChild().getString().equals("default"))
          // Aliased object-literal property; can happen after goog.module rewriting.
          || (qnameNode.isStringKey()
              && this.currentScope.isNamespace(NodeUtil.getBestLValue(parent)));
    }

    private boolean isQualifiedFunctionDefinition(Node qnameNode) {
      checkArgument(qnameNode.isGetProp());
      checkArgument(qnameNode.isQualifiedName());
      Node parent = qnameNode.getParent();
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(qnameNode);
      return parent.isAssign()
          && parent.getParent().isExprResult()
          && parent.getLastChild().isFunction()
          && (jsdoc == null || jsdoc.containsFunctionDeclaration());
    }

    // Returns true iff it creates a new function namespace
    private boolean mayCreateFunctionNamespace(Node qnameNode) {
      if (!qnameNode.isQualifiedName()) {
        return false;
      }
      QualifiedName qname = QualifiedName.fromNode(qnameNode);
      checkState(!currentScope.isNamespace(qname));
      if (!currentScope.isKnownFunction(qname)) {
        return false;
      }
      if (qnameNode.isGetProp()) {
        markAssignNodeAsAnalyzed(qnameNode.getGrandparent());
      }
      NTIScope s;
      if (qnameNode.isName()) {
        // s is the scope that contains the function
        s = currentScope.getScope(qnameNode.getString()).getParent();
      } else {
        s = currentScope;
      }
      s.addFunNamespace(qnameNode);
      return true;
    }

    private boolean mayCreateWindowNamespace(Node qnameNode) {
      if (qnameNode.isName() && qnameNode.getString().equals(WINDOW_INSTANCE)
          && this.currentScope.isGlobalVar(WINDOW_INSTANCE)) {
        getGlobalScope().addNamespaceLit(new QualifiedName(WINDOW_INSTANCE), qnameNode);
        return true;
      }
      return false;
    }

    private void visitObjlitNamespace(QualifiedName qname, Node defSite) {
      if (defSite.isGetProp()) {
        markAssignNodeAsAnalyzed(defSite.getParent());
      }
      if (currentScope.isDefined(qname)) {
        if (defSite.isGetProp() && !NodeUtil.getRValueOfLValue(defSite).isOr()) {
          warnings.add(JSError.make(defSite, REDECLARED_PROPERTY,
              defSite.getLastChild().getString(),
              defSite.getFirstChild().getQualifiedName()));
        }
        return;
      }
      currentScope.addNamespaceLit(qname, defSite);
      // If the object literal that defines the namespace has properties,
      // some of them may define aliased namespaces. This is rare in hand-written
      // code, but it happens often in code generated by the rewrite of
      // goog.module exports.
      Node maybeObjlit = NodeUtil.getRValueOfLValue(defSite);
      if (maybeObjlit.isOr()) {
        maybeObjlit = maybeObjlit.getLastChild();
      }
      Preconditions.checkState(maybeObjlit.isObjectLit(),
          "Expected object literal, found %s", maybeObjlit);
      for (Node propNode : maybeObjlit.children()) {
        if (!propNode.isStringKey()) {
          continue;
        }
        QualifiedName propQname = new QualifiedName(propNode.getString());
        if (isAliasedNamespaceDefinition(propNode)) {
          visitAliasedNamespace(QualifiedName.join(qname, propQname), propNode);
        } else {
          JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(propNode);
          Node propVal = propNode.getLastChild();
          if (jsdoc != null && jsdoc.hasConstAnnotation() && propVal.isObjectLit()) {
            visitObjlitNamespace(QualifiedName.join(qname, propQname), propNode);
          }
        }
      }
    }

    private void markAssignNodeAsAnalyzed(Node maybeAssign) {
      if (maybeAssign.isAssign()) {
        maybeAssign.putBooleanProp(Node.ANALYZED_DURING_GTI, true);
      } else {
        // No initializer for the property
        checkState(maybeAssign.isExprResult());
      }
    }

    private void visitTypedef(Node qnameNode) {
      checkState(qnameNode.isQualifiedName());
      qnameNode.putBooleanProp(Node.ANALYZED_DURING_GTI, true);
      if (NodeUtil.getRValueOfLValue(qnameNode) != null) {
        warnings.add(JSError.make(qnameNode, CANNOT_INIT_TYPEDEF));
      }
      if (currentScope.isDefined(qnameNode)) {
        return;
      }
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(qnameNode);
      Typedef td = Typedef.make(qnameNode, jsdoc.getTypedefType());
      currentScope.addTypedef(qnameNode, td);
    }

    private void visitEnum(Node qnameNode) {
      checkState(qnameNode.isQualifiedName());
      qnameNode.putBooleanProp(Node.ANALYZED_DURING_GTI, true);
      if (currentScope.isDefined(qnameNode)) {
        return;
      }
      Node init = NodeUtil.getRValueOfLValue(qnameNode);
      // First check if the definition is an alias of a previous enum.
      if (init != null && init.isQualifiedName()) {
        EnumType et = currentScope.getEnum(QualifiedName.fromNode(init));
        if (et != null) {
          currentScope.addNamespace(qnameNode, et);
          return;
        }
      }
      // Then check if the enum initializer is an object literal.
      if (init == null || !init.isObjectLit() || init.getFirstChild() == null) {
        warnings.add(JSError.make(qnameNode, MALFORMED_ENUM));
        return;
      }
      // Last, read the object-literal properties and create the EnumType.
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(qnameNode);
      Set<String> propNames = new LinkedHashSet<>();
      for (Node prop : init.children()) {
        String pname = NodeUtil.getObjectLitKeyName(prop);
        if (propNames.contains(pname)) {
          warnings.add(JSError.make(qnameNode, DUPLICATE_PROP_IN_ENUM, pname));
        }
        recordPropertyName(prop);
        propNames.add(pname);
      }
      currentScope.addNamespace(qnameNode,
          EnumType.make(
              getCommonTypes(),
              qnameNode.getQualifiedName(),
              qnameNode,
              jsdoc.getEnumParameterType(), ImmutableSet.copyOf(propNames)));
    }

    private void visitFunctionEarly(Node fn) {
      JSDocInfo fnDoc = NodeUtil.getBestJSDocInfo(fn);
      Node nameNode = NodeUtil.getNameNode(fn);
      String internalName = createFunctionInternalName(fn, nameNode);
      boolean isRedeclaration;
      if (nameNode == null || !nameNode.isQualifiedName()) {
        isRedeclaration = false;
      } else if (nameNode.isName()) {
        isRedeclaration = currentScope.isDefinedLocally(nameNode.getString(), false);
      } else {
        Preconditions.checkState(
            nameNode.isGetProp(), "Expected getprop, found %s", nameNode.getToken());
        isRedeclaration = currentScope.isDefined(nameNode);
        if (isRedeclaration && fnDoc != null) {
          nameNode.getParent().putBooleanProp(Node.ANALYZED_DURING_GTI, true);
          warnings.add(JSError.make(nameNode, REDECLARED_PROPERTY,
              nameNode.getLastChild().getString(),
              nameNode.getFirstChild().getQualifiedName()));
        }
      }
      NTIScope fnScope =
          new NTIScope(fn, this.currentScope, collectFormals(fn, fnDoc), getCommonTypes());
      if (!fn.isFromExterns()) {
        GlobalTypeInfoCollector.this.scopes.add(fnScope);
      }
      this.currentScope.addLocalFunDef(internalName, fnScope);
      maybeRecordNominalType(fn, nameNode, fnDoc, isRedeclaration);
    }

    private String createFunctionInternalName(Node fn, Node nameNode) {
      String internalName = null;
      // The name of a function expression isn't visible in the scope where the function expression
      // appears, so don't use it as the internalName.
      if (nameNode == fn.getFirstChild() && !NodeUtil.isFunctionDeclaration(fn)) {
        nameNode = null;
      }
      if (nameNode == null || !nameNode.isName()
          || nameNode.getParent().isAssign()) {
        // Anonymous functions, qualified names, and stray assignments
        // (eg, f = function(x) { ... }; ) get gensymed names.
        internalName = ANON_FUN_PREFIX + funNameGen.generateNextName();
        getAnonFunNames().put(fn, internalName);
      } else if (currentScope.isDefinedLocally(nameNode.getString(), false)) {
        String fnName = nameNode.getString();
        checkState(!fnName.contains("."));
        internalName = ANON_FUN_PREFIX + funNameGen.generateNextName();
        getAnonFunNames().put(fn, internalName);
      } else {
        // fnNameNode is undefined simple name
        internalName = nameNode.getString();
      }
      return internalName;
    }

    private ArrayList<String> collectFormals(Node fn, JSDocInfo fnDoc) {
      checkArgument(fn.isFunction());
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
          if (!formals.contains(formalInJsdoc)
              && !tmpRestFormals.contains(formalInJsdoc)) {
            String functionName = NodeUtil.getNearestFunctionName(fn);
            warnings.add(JSError.make(fn, INEXISTENT_PARAM, formalInJsdoc, functionName));
          }
        }
      }
      return formals;
    }

    private void maybeRecordNominalType(
        Node defSite, Node nameNode, JSDocInfo fnDoc, boolean isRedeclaration) {
      checkState(nameNode == null || nameNode.isQualifiedName());
      if (fnDoc == null) {
        return;
      }
      if (fnDoc.isConstructorOrInterface()) {
        if (nameNode == null) {
          warnings.add(JSError.make(defSite, ANONYMOUS_NOMINAL_TYPE));
          nameNode = IR.name(ANON_FUN_PREFIX + funNameGen.generateNextName());
          nameNode.useSourceInfoFrom(defSite);
        }
        String qname = nameNode.getQualifiedName();
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (String typeParam : fnDoc.getTemplateTypeNames()) {
          builder.add(getVarNameGen().getNextName(typeParam));
        }
        ImmutableList<String> typeParameters = builder.build();
        RawNominalType rawType;
        ObjectKind objKind = fnDoc.makesStructs()
            ? ObjectKind.STRUCT
            : (fnDoc.makesDicts() ? ObjectKind.DICT : ObjectKind.UNRESTRICTED);
        if (fnDoc.isConstructor()) {
          rawType = RawNominalType.makeClass(
              getCommonTypes(), defSite, qname, typeParameters, objKind,
              fnDoc.isAbstract());
        } else if (fnDoc.usesImplicitMatch()) {
          rawType = RawNominalType.makeStructuralInterface(
              getCommonTypes(), defSite, qname, typeParameters, objKind);
        } else {
          checkState(fnDoc.isInterface());
          rawType = RawNominalType.makeNominalInterface(
              getCommonTypes(), defSite, qname, typeParameters, objKind);
        }
        if (isWindowRawType(rawType)) {
          setWindow(rawType);
        }
        nominaltypesByNode.put(defSite, rawType);
        if (isRedeclaration) {
          return;
        }
        Node firstChild = nameNode.getFirstChild();
        if (nameNode.isName()
            || currentScope.isNamespace(firstChild)
            || mayCreateFunctionNamespace(firstChild)
            || mayCreateWindowNamespace(firstChild)) {
          if (nameNode.isGetProp()) {
            if (defSite.isFunction()) {
              defSite.getParent().putBooleanProp(Node.ANALYZED_DURING_GTI, true);
            } else {
              defSite.getParent().getFirstChild().putBooleanProp(Node.ANALYZED_DURING_GTI, true);
            }
          } else if (currentScope.isTopLevel()) {
            maybeRecordBuiltinType(qname, rawType);
          }
          currentScope.addNamespace(nameNode, rawType);
        }
      } else if (fnDoc.makesStructs()) {
        warnings.add(JSError.make(defSite, STRUCT_WITHOUT_CTOR_OR_INTERF));
      }
      if (fnDoc.makesDicts() && !fnDoc.isConstructor()) {
        warnings.add(JSError.make(defSite, DICT_WITHOUT_CTOR));
      }
    }

    private void maybeRecordBuiltinType(String name, RawNominalType rawType) {
      switch (name) {
        case "Arguments":
          getCommonTypes().setArgumentsType(rawType);
          break;
        case "Function":
          getCommonTypes().setFunctionType(rawType);
          break;
        case "Object": {
          getCommonTypes().setObjectType(rawType);
          // Create a separate raw type for object literals
          RawNominalType objLitRawType = RawNominalType.makeClass(
              getCommonTypes(), rawType.getDefSite(), JSTypes.OBJLIT_CLASS_NAME,
              ImmutableList.<String>of(), ObjectKind.UNRESTRICTED, false);
          objLitRawType.addSuperClass(rawType.getAsNominalType());
          getCommonTypes().setLiteralObjNominalType(objLitRawType);
          break;
        }
        case "Number":
          getCommonTypes().setNumberInstance(rawType.getInstanceAsJSType());
          break;
        case "String":
          getCommonTypes().setStringInstance(rawType.getInstanceAsJSType());
          break;
        case "Boolean":
          getCommonTypes().setBooleanInstance(rawType.getInstanceAsJSType());
          break;
        case "RegExp":
          getCommonTypes().setRegexpInstance(rawType.getInstanceAsJSType());
          break;
        case "Array":
          getCommonTypes().setArrayType(rawType);
          break;
        case "IObject":
          getCommonTypes().setIObjectType(rawType);
          break;
        case "IArrayLike":
          getCommonTypes().setIArrayLikeType(rawType);
          break;
        case "Iterable":
          getCommonTypes().setIterableType(rawType);
          break;
        case "Iterator":
          getCommonTypes().setIteratorType(rawType);
          break;
        case "IIterableResult":
          getCommonTypes().setIIterableResultType(rawType);
          break;
        case "Generator":
          getCommonTypes().setGeneratorType(rawType);
          break;
        case "ITemplateArray":
          getCommonTypes().setITemplateArrayType(rawType);
          break;
        default:
          // No other type names are added to commonTypes.
          break;
      }
    }

    private void visitAliasedNamespace(QualifiedName qname, Node lhs) {
      if (this.currentScope.isDefined(qname)) {
        return;
      }
      Node rhs = NodeUtil.getRValueOfLValue(lhs);
      QualifiedName rhsQname = QualifiedName.fromNode(rhs);
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(lhs);
      Namespace ns = this.currentScope.getNamespace(rhsQname);
      if (jsdoc != null && jsdoc.isConstructorOrInterface()) {
        RawNominalType rawType = ns instanceof RawNominalType ? (RawNominalType) ns : null;
        if (jsdoc.isConstructor()
            && (rawType == null || rawType.isInterface())) {
          warnings.add(JSError.make(rhs, EXPECTED_CONSTRUCTOR, rhsQname.toString()));
          return;
        }
        if (jsdoc.isInterface()
            && (rawType == null || rawType.isClass())) {
          warnings.add(JSError.make(rhs, EXPECTED_INTERFACE, rhsQname.toString()));
          return;
        }
      }
      if (ns != null) {
        lhs.getParent().putBooleanProp(Node.ANALYZED_DURING_GTI, true);
        this.currentScope.addNamespace(qname, lhs, ns);
      }
    }

    private void visitAliasedTypedef(Node lhs) {
      if (this.currentScope.isDefined(lhs)) {
        return;
      }
      lhs.getParent().putBooleanProp(Node.ANALYZED_DURING_GTI, true);
      Typedef td = checkNotNull(getAliasedTypedef(lhs, this.currentScope));
      this.currentScope.addTypedef(lhs, td);
    }

    private void maybeAddFunctionToNamespace(Node funQname) {
      Namespace ns = currentScope.getNamespace(QualifiedName.fromNode(funQname.getFirstChild()));
      String internalName = getFunInternalName(funQname.getParent().getLastChild());
      NTIScope s = currentScope.getScope(internalName);
      QualifiedName pname = new QualifiedName(funQname.getLastChild().getString());
      if (!ns.isDefined(pname)) {
        ns.addNamespace(pname,
            new FunctionNamespace(getCommonTypes(), funQname.getQualifiedName(), s, funQname));
      }
    }

    private void visitNewCtorDefinedByCall(Node qnameNode) {
      checkState(qnameNode.isName() || qnameNode.isGetProp());
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(qnameNode);
      Node rhs = NodeUtil.getRValueOfLValue(qnameNode);
      maybeRecordNominalType(rhs, qnameNode, jsdoc, false);
    }

    private void visitNewCtorWithoutFunctionLiteral(Node qnameNode) {
      checkState(qnameNode.isName() || qnameNode.isGetProp());
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(qnameNode);
      maybeRecordNominalType(qnameNode, qnameNode, jsdoc, false);
    }
  }

  private class ProcessScope extends AbstractShallowCallback {
    private final NTIScope currentScope;
    private final List<NominalTypeBuilder> delegateProxies = new ArrayList<>();
    private final Map<String, String> delegateCallingConventions = new HashMap<>();
    private Set<Node> lendsObjlits = new LinkedHashSet<>();

    ProcessScope(NTIScope currentScope) {
      this.currentScope = currentScope;
    }

    void finishProcessingScope() {
      for (Node objlit : lendsObjlits) {
        processLendsNode(objlit);
      }
      lendsObjlits = null;
      convention.defineDelegateProxyPrototypeProperties(
          globalTypeInfo, delegateProxies, delegateCallingConventions);
    }

    /**
     * @lends can lend properties to an object X being defined in the same statement as the
     * @lends. To make sure that we've seen the definition of X, we process @lends annotations
     * after we've traversed the scope.
     * @lends can only add properties to namespaces, constructors and prototypes
     */
    void processLendsNode(Node objlit) {
      JSDocInfo jsdoc = objlit.getJSDocInfo();
      String lendsName = jsdoc.getLendsName();
      checkNotNull(lendsName);
      QualifiedName lendsQname = QualifiedName.fromQualifiedString(lendsName);
      if (currentScope.isNamespace(lendsQname)) {
        processLendsToNamespace(lendsQname, lendsName, objlit);
      } else {
        RawNominalType rawType =
            checkValidLendsToPrototypeAndGetClass(lendsQname, lendsName, objlit);
        if (rawType != null) {
          for (Node prop : objlit.children()) {
            String pname = NodeUtil.getObjectLitKeyName(prop);
            mayAddPropToPrototype(rawType, pname, prop, prop.getFirstChild());
          }
        } else {
          // When rawType is null, some invalid declaration prevented the
          // raw type from being registered. We must still compute the
          // declared type of any function literals.
          for (Node prop : objlit.children()) {
            Node propInit = prop.getFirstChild();
            if (propInit.isFunction()) {
              visitFunctionLate(propInit, null);
            }
          }
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
        JSType propDeclType = (JSType) prop.getTypeI();
        if (propDeclType != null) {
          borrowerNamespace.addProperty(pname, prop, propDeclType, false);
        } else {
          JSType t = simpleInferExpr(prop.getFirstChild(), this.currentScope);
          if (t == null) {
            t = getCommonTypes().UNKNOWN;
          }
          borrowerNamespace.addProperty(pname, prop, t, false);
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
      switch (n.getToken()) {
        case FUNCTION:
          Node grandparent = parent.getParent();
          // We skip property and prototype declarations because if we compute their
          // declared type here it will be wrong, and it will block the correct
          // computation later.
          if (grandparent == null
              || (!isPrototypePropertyDeclaration(grandparent)
              && !isPropertyDeclarationOnThis(parent.getFirstChild(), this.currentScope))) {
            RawNominalType ownerType = maybeGetOwnerType(n, parent);
            visitFunctionLate(n, ownerType);
          }
          break;
        case NAME: {
          String name = n.getString();
          if (name == null || parent.isFunction()) {
            return;
          }
          // TODO(dimvar): Handle local scopes introduced by catch properly,
          // after we decide what to do with variables in general, eg, will we
          // use unique numeric ids?
          if (parent.isVar() || parent.isCatch()) {
            visitVar(n, parent);
          } else if (this.currentScope.isOuterVarEarly(name)) {
            this.currentScope.addOuterVar(name);
          } else if (// Typedef variables can't be referenced in the source.
              this.currentScope.getTypedef(name) != null
                  || (!name.equals(this.currentScope.getName())
                  && !this.currentScope.isDefinedLocally(name, false))) {
          }
          break;
        }
        case GETPROP:
          if (NodeUtil.isPropertyTest(compiler, n) || isPropertyAbsentTest(n)) {
            // Consider a property access x.foo, where x is a loose type and foo is not
            // defined anywhere in the program. If we only warn when x doesn't have the
            // property foo, we'll basically never warn for loose types;
            // because of inference, loose types always "have" the properties accessed
            // on them. So, we warn even if x has foo. Then, to avoid spurious warnings,
            // we consider property tests as definition sites, otherwise we would warn
            // for code like this: function f(x) { if (x.foo) { return x.foo + 1; } }
            recordPropertyName(n.getLastChild());
          }
          if (n.getFirstChild().isName()
              && n.getFirstChild().getString().startsWith("$jscomp$destructuring$")) {
            recordPropertyName(n.getLastChild());
          }
          if (parent.isExprResult() && n.isQualifiedName()) {
            visitPropertyDeclaration(n);
          }
          convention.checkForCallingConventionDefinitions(n, delegateCallingConventions);
          break;
        case ASSIGN: {
          Node lvalue = n.getFirstChild();
          if (lvalue.isQualifiedName()) {
            clearInferredTypeIfVar(NodeUtil.getRootOfQualifiedName(lvalue));
            if (lvalue.isGetProp()) {
              visitPropertyDeclaration(lvalue);
            }
          }
          break;
        }
        case CAST:
          n.setTypeI(getDeclaredTypeOfNode(n.getJSDocInfo(), this.currentScope));
          break;
        case OBJECTLIT:
          if (!NodeUtil.isObjectLitKey(parent)) {
            Node lval = NodeUtil.getBestLValue(n);
            visitObjectLit(n, QualifiedName.fromNode(lval));
          }
          break;
        case CALL:
          visitCall(n);
          break;
        default:
          break;
      }
    }

    /**
     * When a local variable is assigned after its definition, don't try to infer its type.
     * It's easy to have spurious warnings in that case.
     */
    private void clearInferredTypeIfVar(Node qnameRoot) {
      if (qnameRoot.isName()) {
        String name = qnameRoot.getString();
        this.currentScope.clearInferredTypeOfVar(name);
      }
    }

    private boolean isPropertyAbsentTest(Node propAccessNode) {
      Node parent = propAccessNode.getParent();
      if (parent.getToken() == Token.EQ || parent.getToken() == Token.SHEQ) {
        Node other = parent.getFirstChild() == propAccessNode
            ? parent.getSecondChild() : parent.getFirstChild();
        return NodeUtil.isUndefined(other);
      }
      return parent.isNot() && parent.getParent().isIf();
    }

    private void visitVar(Node nameNode, Node parent) {
      String name = nameNode.getString();
      boolean isDefinedLocally = this.currentScope.isDefinedLocally(name, false);
      if (isCtorDefinedByCall(nameNode)) {
        computeFnDeclaredType(NodeUtil.getBestJSDocInfo(nameNode), name,
            nameNode.getFirstChild(), null, this.currentScope);
        return;
      }
      if (isCtorWithoutFunctionLiteral(nameNode)) {
        computeFnDeclaredType(NodeUtil.getBestJSDocInfo(nameNode), name,
            nameNode, null, this.currentScope);
        return;
      }
      if (isDefinedLocally && this.currentScope.isNamespace(name)) {
        return;
      }
      if (NodeUtil.isTypedefDecl(nameNode) || NodeUtil.isEnumDecl(nameNode)) {
        if (!isDefinedLocally) {
          // Malformed enum or typedef
          this.currentScope.addDeclaredLocal(
              name, getCommonTypes().UNKNOWN, false, nameNode.isFromExterns());
        }
        return;
      }
      Node initializer = nameNode.getFirstChild();
      if (initializer != null && initializer.isFunction()) {
        return;
      }
      if (parent.isCatch()) {
        this.currentScope.addDeclaredLocal(name, getCommonTypes().UNKNOWN, false, false);
      } else {
        boolean isConst = isConst(nameNode);
        boolean isDeclared = true;
        JSType type = getVarTypeFromAnnotation(nameNode, this.currentScope);
        if (type == null) {
          if (isConst) {
            type = mayInferFromRhsIfConst(nameNode);
          } else if (initializer != null) {
            isDeclared = false;
            type = simpleInferExpr(initializer, this.currentScope);
          }
        }
        if (isDeclared) {
          this.currentScope.addDeclaredLocal(name, type, isConst, nameNode.isFromExterns());
        } else {
          this.currentScope.addInferredLocal(name, type);
        }
      }
    }

    private void visitObjectLit(Node objLitNode, QualifiedName lvalueQname) {
      Node parent = objLitNode.getParent();
      JSDocInfo jsdoc = objLitNode.getJSDocInfo();
      if (jsdoc != null && jsdoc.getLendsName() != null) {
        lendsObjlits.add(objLitNode);
      }
      Node maybeLvalue = parent.isAssign() ? parent.getFirstChild() : parent;
      if (currentScope.isNamespace(lvalueQname)) {
        for (Node prop : objLitNode.children()) {
          if (!prop.isComputedProp()) {
            visitNamespacePropertyDeclaration(prop, lvalueQname, prop.getString());
          }
        }
      } else if (!NodeUtil.isEnumDecl(maybeLvalue)
          && !NodeUtil.isPrototypeAssignment(maybeLvalue)) {
        // For object literals that are not "special" (namespaces, enums, etc),
        // record their declared properties so we can typecheck them later.
        for (Node prop : objLitNode.children()) {
          JSDocInfo propJsdoc = prop.getJSDocInfo();
          if (propJsdoc != null) {
            JSType propType = getDeclaredTypeOfNode(propJsdoc, currentScope);
            prop.setTypeI(propType);
          }
          if (isAnnotatedAsConst(prop)) {
            warnings.add(JSError.make(prop, MISPLACED_CONST_ANNOTATION));
          }
        }
      }
      // Record property names and recur to nested object literals.
      for (Node prop : objLitNode.children()) {
        Node keyNode = NodeUtil.getObjectLitKeyNode(prop);
        if (keyNode != null) {
          recordPropertyName(keyNode);
        }
        if (prop.hasChildren() && prop.getFirstChild().isObjectLit()) {
          QualifiedName nestedQname = lvalueQname == null || keyNode == null
              ? null : QualifiedName.join(lvalueQname, new QualifiedName(keyNode.getString()));
          visitObjectLit(prop.getFirstChild(), nestedQname);
        }
      }
    }

    private void visitCall(Node call) {
      // Check various coding conventions to see if any additional handling is needed.
      SubclassRelationship relationship = convention.getClassesDefinedByCall(call);
      if (relationship != null) {
        applySubclassRelationship(relationship);
      }
      String className = convention.getSingletonGetterClassName(call);
      if (className != null) {
        applySingletonGetter(className);
      }
      DelegateRelationship delegateRelationship = convention.getDelegateRelationship(call);
      if (delegateRelationship != null) {
        applyDelegateRelationship(delegateRelationship);
      }
    }

    private void applySubclassRelationship(SubclassRelationship rel) {
      if (rel.superclassName.equals(rel.subclassName)) {
        // Note: this is a messed up situation, but it's dealt with elsewhere, so let it go here.
        return;
      }
      RawNominalType superClass = findInScope(rel.superclassName);
      RawNominalType subClass = findInScope(rel.subclassName);
      if (superClass != null && superClass.getConstructorFunction() != null
          && subClass != null && subClass.getConstructorFunction() != null) {
        convention.applySubclassRelationship(
            new NominalTypeBuilderNti(superClass.getAsNominalType()),
            new NominalTypeBuilderNti(subClass.getAsNominalType()),
            rel.type);
      }
    }

    private void applySingletonGetter(String className) {
      RawNominalType rawType = findInScope(className);
      if (rawType != null) {
        JSType instanceType = rawType.getInstanceAsJSType();
        JSType getInstanceType =
            new FunctionTypeBuilder(getCommonTypes()).addRetType(instanceType).buildType();
        convention.applySingletonGetter(
            new NominalTypeBuilderNti(rawType.getAsNominalType()), getInstanceType);
      }
    }

    private void applyDelegateRelationship(DelegateRelationship rel) {
      RawNominalType delegateBase = findInScope(rel.delegateBase);
      RawNominalType delegator = findInScope(rel.delegator);
      RawNominalType delegateSuper = findInScope(convention.getDelegateSuperclassName());
      // Note: OTI also verified that getConstructor() was non-null on each of these, but since
      // they're all coming from RawNominalTypes, there should be no way that can fail here.
      if (delegator != null && delegateBase != null && delegateSuper != null) {
        // A function that takes some constructor, and returns the corresponding delegate.
        JSType findDelegate =
            new FunctionTypeBuilder(getCommonTypes())
                .addReqFormal(getCommonTypes().qmarkFunction())
                .addRetType(JSType.join(getCommonTypes().NULL, delegateBase.getInstanceAsJSType()))
                .buildType();

        // Normally, types are defined during CollectNamedTypes, but here we are defining a class
        // during ProcessScope. (We do add it to nominaltypesByNode with a fake defSite.)
        // The late creation doesn't seem to cause an issue, but worth noting.
        RawNominalType delegateProxy = RawNominalType.makeClass(
            getCommonTypes(),
            delegateBase.getDefSite() /* defSite */,
            delegateBase.getName() + "(Proxy)" /* name */,
            null /* typeParameters */,
            ObjectKind.UNRESTRICTED,
            false /* isAbstract */);
        nominaltypesByNode.put(new Node(null), delegateProxy);
        delegateProxy.addSuperClass(delegateBase.getAsNominalType());
        delegateProxy.setCtorFunction(
            new FunctionTypeBuilder(getCommonTypes())
                .addRetType(delegateProxy.getInstanceAsJSType())
                .addNominalType(delegateProxy.getInstanceAsJSType())
                .buildFunction());
        convention.applyDelegateRelationship(
            new NominalTypeBuilderNti(delegateSuper.getAsNominalType()),
            new NominalTypeBuilderNti(delegateBase.getAsNominalType()),
            new NominalTypeBuilderNti(delegator.getAsNominalType()),
            delegateProxy.getInstanceAsJSType(),
            findDelegate);
        delegateProxies.add(new NominalTypeBuilderNti(delegateProxy.getAsNominalType()));
      }
    }

    private RawNominalType findInScope(String qname) {
      return currentScope.getNominalType(QualifiedName.fromQualifiedString(qname));
    }

    private void visitPropertyDeclaration(Node getProp) {
      recordPropertyName(getProp.getLastChild());
      // Property declaration on THIS; most commonly a class property
      if (isPropertyDeclarationOnThis(getProp, currentScope)) {
        visitPropertyDeclarationOnThis(getProp);
      }
      // Prototype property
      else if (isPrototypeProperty(getProp)) {
        visitPrototypePropertyDeclaration(getProp);
      }
      // Direct assignment to the prototype
      else if (NodeUtil.isPrototypeAssignment(getProp)) {
        getProp.putBooleanProp(Node.ANALYZED_DURING_GTI, true);
        visitPrototypeAssignment(getProp);
      }
      // "Static" property on constructor
      else if (isStaticCtorProp(getProp)) {
        visitConstructorPropertyDeclaration(getProp);
      }
      // Namespace property
      else if (currentScope.isNamespace(getProp.getFirstChild())) {
        visitNamespacePropertyDeclaration(getProp);
      }
      // Other property
      else {
        visitOtherPropertyDeclaration(getProp);
      }
    }

    private boolean isStaticCtorProp(Node getProp) {
      checkArgument(getProp.isGetProp());
      if (!getProp.isQualifiedName()) {
        return false;
      }
      return null != currentScope.getNominalType(QualifiedName.fromNode(getProp.getFirstChild()));
    }

    /** Compute the declared type for a given scope. */
    private NTIScope visitFunctionLate(Node fn, RawNominalType ownerType) {
      checkArgument(fn.isFunction());
      String internalName = getFunInternalName(fn);
      NTIScope fnScope = currentScope.getScope(internalName);
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(fn);
      DeclaredFunctionType declFunType = fnScope.getDeclaredFunctionType();
      if (declFunType == null) {
        declFunType = computeFnDeclaredType(jsdoc, internalName, fn, ownerType, currentScope);
        fnScope.setDeclaredType(declFunType);
      }
      return fnScope;
    }

    // Used when declaring constructor and namespace properties. Prototype property
    // declarations are similar, but different enough that they didn't neatly fit
    // in this method (eg, redeclaration warnings are stricter).
    PropertyType getPropTypeHelper(JSDocInfo jsdoc, Node declNode, RawNominalType thisType) {
      Node initializer = NodeUtil.getRValueOfLValue(declNode);
      PropertyType result = new PropertyType();
      DeclaredFunctionType dft = null;
      if (initializer != null && initializer.isFunction()) {
        dft = visitFunctionLate(initializer, thisType).getDeclaredFunctionType();
      }
      if (jsdoc != null && jsdoc.hasType()) {
        result.declType = getDeclaredTypeOfNode(jsdoc, currentScope);
      } else if (jsdoc != null && jsdoc.containsFunctionDeclaration()
          && (initializer == null || !initializer.isFunction())) {
        // We're parsing a function declaration without a function initializer
        checkState(declNode.isGetProp(), declNode);
        dft = computeFnDeclaredType(
            jsdoc, declNode.getLastChild().getString(), declNode, null, currentScope);
        result.declType = getCommonTypes().fromFunctionType(dft.toFunctionType());
      } else if (initializer != null && initializer.isFunction()) {
        JSType funType = getCommonTypes().fromFunctionType(dft.toFunctionType());
        if ((jsdoc != null && jsdoc.containsFunctionDeclaration())
            || NodeUtil.functionHasInlineJsdocs(initializer)) {
          result.declType = funType;
        } else {
          result.inferredFunType = funType;
        }
      }
      return result;
    }

    /**
     * Called for prototype-property declarations of the form:
     * Foo.prototype.someprop = ...;
     */
    private void visitPrototypePropertyDeclaration(Node getProp) {
      checkArgument(getProp.isGetProp());
      Node ctorNameNode = NodeUtil.getPrototypeClassName(getProp);
      QualifiedName ctorQname = QualifiedName.fromNode(ctorNameNode);
      RawNominalType ownerType = currentScope.getNominalType(ctorQname);
      // We only add properties to the prototype of a class if the
      // property creations are in the same scope as the constructor
      if (ownerType != null && !currentScope.isDefined(ctorNameNode)) {
        warnings.add(JSError.make(getProp, CTOR_IN_DIFFERENT_SCOPE));
      }
      visitPrototypePropertyDeclaration(getProp, ownerType);
    }

    /**
     * Called for the common prototype-property declarations, but also for property
     * declarations on THIS inside the constructor of a record or interface.
     */
    private void visitPrototypePropertyDeclaration(Node getProp, RawNominalType ownerType) {
      checkArgument(getProp.isGetProp());
      Node parent = getProp.getParent();
      Node initializer = parent.isAssign() ? parent.getLastChild() : null;
      if (ownerType == null) {
        if (initializer != null && initializer.isFunction()) {
          visitFunctionLate(initializer, null);
        }
        // We don't look at assignments to prototypes of non-constructors.
        return;
      }
      if (initializer != null && initializer.isFunction()) {
        parent.putBooleanProp(Node.ANALYZED_DURING_GTI, true);
      }
      mayWarnAboutInterfacePropInit(ownerType, initializer);
      mayAddPropToPrototype(ownerType, getProp.getLastChild().getString(), getProp, initializer);
    }

    private void mayWarnAboutInterfacePropInit(RawNominalType rawType, Node initializer) {
      if (rawType.isInterface() && initializer != null) {
        String abstractMethodName = convention.getAbstractMethodName();
        if (initializer.isFunction()
            && !NodeUtil.isEmptyFunctionExpression(initializer)) {
          warnings.add(JSError.make(initializer, INTERFACE_METHOD_NOT_EMPTY));
        } else if (!initializer.isFunction()
            && !initializer.matchesQualifiedName(abstractMethodName)) {
          warnings.add(JSError.make(initializer, INVALID_INTERFACE_PROP_INITIALIZER));
        }
      }
    }

    private void visitPrototypeAssignment(Node getProp) {
      checkArgument(getProp.isGetProp());
      Node protoObjNode = getProp.getParent().getLastChild();
      if (!protoObjNode.isObjectLit()) {
        return;
      }
      Node ctorNameNode = NodeUtil.getPrototypeClassName(getProp);
      QualifiedName ctorQname = QualifiedName.fromNode(ctorNameNode);
      RawNominalType rawType = currentScope.getNominalType(ctorQname);
      for (Node objLitChild : protoObjNode.children()) {
        recordPropertyName(objLitChild);
      }
      if (rawType == null) {
        for (Node objLitChild : protoObjNode.children()) {
          Node initializer = objLitChild.getLastChild();
          if (initializer != null && initializer.isFunction()) {
            visitFunctionLate(initializer, null);
          }
        }
        return;
      }
      getProp.putBooleanProp(Node.ANALYZED_DURING_GTI, true);
      for (Node objLitChild : protoObjNode.children()) {
        mayAddPropToPrototype(rawType, objLitChild.getString(), objLitChild,
            objLitChild.getLastChild());
      }
    }

    private void visitConstructorPropertyDeclaration(Node getProp) {
      checkArgument(getProp.isGetProp());
      mayVisitWeirdCtorDefinition(getProp);
      // Named types have already been crawled in CollectNamedTypes
      if (isNamedType(getProp)) {
        return;
      }
      QualifiedName ctorQname = QualifiedName.fromNode(getProp.getFirstChild());
      RawNominalType classType = currentScope.getNominalType(ctorQname);
      String pname = getProp.getLastChild().getString();
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(getProp);
      JSType propDeclType;
      if (jsdoc != null && !jsdoc.hasType() && jsdoc.containsFunctionDeclaration()) {
        FunctionAndSlotType fst =
            getTypeParser().getFunctionType(jsdoc, pname, getProp, null, null, this.currentScope);
        propDeclType = getCommonTypes().fromFunctionType(fst.functionType.toFunctionType());
      } else {
        propDeclType = getDeclaredTypeOfNode(jsdoc, currentScope);
      }
      boolean isConst = isConst(getProp);
      if (propDeclType != null || isConst) {
        JSType previousPropType = classType.getCtorPropDeclaredType(pname);
        if (classType.hasStaticProp(pname)
            && previousPropType != null
            && !suppressDupPropWarning(jsdoc, propDeclType, previousPropType)) {
          warnings.add(JSError.make(
              getProp, REDECLARED_PROPERTY, pname, "type " + classType));
          return;
        }
        if (propDeclType == null) {
          propDeclType = mayInferFromRhsIfConst(getProp);
        }
        classType.addCtorProperty(pname, getProp, propDeclType, isConst);
        getProp.putBooleanProp(Node.ANALYZED_DURING_GTI, true);
        if (isConst) {
          getProp.putBooleanProp(Node.CONSTANT_PROPERTY_DEF, true);
        }
      } else {
        JSType inferredType = null;
        Node initializer = NodeUtil.getRValueOfLValue(getProp);
        if (initializer != null) {
          inferredType = simpleInferExpr(initializer, this.currentScope);
        }
        if (inferredType == null) {
          inferredType = getCommonTypes().UNKNOWN;
        }
        classType.addUndeclaredCtorProperty(pname, getProp, inferredType);
      }
    }

    private void mayVisitWeirdCtorDefinition(Node getProp) {
      if (isCtorDefinedByCall(getProp)) {
        computeFnDeclaredType(
            NodeUtil.getBestJSDocInfo(getProp),
            getProp.getQualifiedName(),
            getProp.getNext(), null, currentScope);
        return;
      }
      if (isCtorWithoutFunctionLiteral(getProp)) {
        computeFnDeclaredType(
            NodeUtil.getBestJSDocInfo(getProp),
            getProp.getQualifiedName(),
            getProp, null, this.currentScope);
        return;
      }
    }

    private void visitNamespacePropertyDeclaration(Node getProp) {
      checkArgument(getProp.isGetProp());
      mayVisitWeirdCtorDefinition(getProp);
      // Named types have already been crawled in CollectNamedTypes
      if (isNamedType(getProp) || isAliasedTypedef(getProp, this.currentScope)) {
        return;
      }
      Node recv = getProp.getFirstChild();
      String pname = getProp.getLastChild().getString();
      visitNamespacePropertyDeclaration(getProp, QualifiedName.fromNode(recv), pname);
    }

    private void visitNamespacePropertyDeclaration(
        Node defSite, QualifiedName nsQname, String pname) {
      checkArgument(defSite.isGetProp() || NodeUtil.isObjLitProperty(defSite), defSite);
      checkArgument(currentScope.isNamespace(nsQname));
      if (defSite.isGetterDef()) {
        pname = getCommonTypes().createGetterPropName(pname);
      } else if (defSite.isSetterDef()) {
        pname = getCommonTypes().createSetterPropName(pname);
      }
      if (defSite.isStringKey()) {
        QualifiedName propQname = new QualifiedName(defSite.getString());
        // Namespaces have already been crawled in CollectNamedTypes
        if (currentScope.isNamespace(QualifiedName.join(nsQname, propQname))) {
          return;
        }
      }
      EnumType et = currentScope.getEnum(nsQname);
      // If there is a reassignment to one of the enum's members, don't consider
      // that a definition of a new property.
      if (et != null && et.enumLiteralHasKey(pname)) {
        return;
      }

      Namespace ns = currentScope.getNamespace(nsQname);
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(defSite);
      PropertyType pt = getPropTypeHelper(jsdoc, defSite, null);
      JSType propDeclType = pt.declType;
      JSType propInferredFunType = pt.inferredFunType;
      boolean isConst = isConst(defSite);
      if (propDeclType != null || isConst) {
        JSType previousPropType = ns.getPropDeclaredType(pname);
        defSite.putBooleanProp(Node.ANALYZED_DURING_GTI, true);
        if (ns.hasSubnamespace(new QualifiedName(pname))
            || (ns.hasStaticProp(pname)
                && previousPropType != null
                && !suppressDupPropWarning(jsdoc, propDeclType, previousPropType))) {
          warnings.add(JSError.make(
              defSite, REDECLARED_PROPERTY, pname, "namespace " + ns));
          defSite.getParent().putBooleanProp(Node.ANALYZED_DURING_GTI, true);
          return;
        }
        if (propDeclType == null) {
          propDeclType = mayInferFromRhsIfConst(defSite);
        }
        ns.addProperty(pname, defSite, propDeclType, isConst);
        if (defSite.isGetProp() && isConst) {
          defSite.putBooleanProp(Node.CONSTANT_PROPERTY_DEF, true);
        }
      } else if (propInferredFunType != null) {
        ns.addUndeclaredProperty(pname, defSite, propInferredFunType, false);
      } else {
        // Try to infer the prop type, but don't say that the prop is declared.
        Node initializer = NodeUtil.getRValueOfLValue(defSite);
        JSType t = initializer == null ? null : simpleInferExpr(initializer, this.currentScope);
        if (t == null) {
          t = getCommonTypes().UNKNOWN;
        }
        ns.addUndeclaredProperty(pname, defSite, t, false);
      }
    }

    private void visitPropertyDeclarationOnThis(Node getProp) {
      checkArgument(getProp.isGetProp());
      JSType t = currentScope.getDeclaredFunctionType().getThisType();
      NominalType thisType = t == null ? null : t.getNominalTypeIfSingletonObj();
      Node parent = getProp.getParent();
      Node initializer = parent.isAssign() ? parent.getLastChild() : null;
      if (thisType == null) {
        if (initializer != null && initializer.isFunction()) {
          visitFunctionLate(initializer, null);
        }
        // This will get caught in NewTypeInference
        return;
      }
      RawNominalType rawType = thisType.getRawNominalType();
      // In ES6, we allow declaring a record or interface as a class and declaring the
      // properties on THIS in the constructor. Handle those here.
      if (rawType.isInterface()) {
        visitPrototypePropertyDeclaration(getProp, rawType);
        return;
      }
      String pname = getProp.getLastChild().getString();
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(getProp);
      PropertyType pt = getPropTypeHelper(jsdoc, getProp, rawType);
      JSType propDeclType = pt.declType;
      JSType propInferredFunType = pt.inferredFunType;
      boolean isConst = isConst(getProp);
      if (initializer != null && initializer.isFunction()) {
        parent.putBooleanProp(Node.ANALYZED_DURING_GTI, true);
      }
      if (propDeclType != null || isConst) {
        mayWarnAboutExistingProp(rawType, pname, getProp, propDeclType);
        // Intentionally, we keep going even if we warned for redeclared prop.
        // The reason is that if a prop is defined on a class and on its proto
        // with conflicting types, we prefer the type of the class.
        if (propDeclType == null) {
          propDeclType = mayInferFromRhsIfConst(getProp);
        }
        if (mayAddPropToType(getProp, rawType)) {
          rawType.addInstanceProperty(pname, getProp, propDeclType, isConst);
        }
        if (isConst) {
          getProp.putBooleanProp(Node.CONSTANT_PROPERTY_DEF, true);
        }
      } else if (mayAddPropToType(getProp, rawType)) {
        if (propInferredFunType != null) {
          rawType.addUndeclaredInstanceProperty(pname, propInferredFunType, getProp);
        } else {
          rawType.addUndeclaredInstanceProperty(pname, getCommonTypes().UNKNOWN, getProp);
        }
      }
      // Only add the definition node if the property is not already defined.
      if (!propertyDefs.contains(rawType, pname)) {
        propertyDefs.put(rawType, pname, new PropertyDef(getProp, null, null));
      }
    }

    private boolean isTranspiledLoopVariable(Node getProp) {
      Node recv = getProp.getFirstChild();
      return recv.isName() && recv.getString().startsWith("$jscomp$loop$");
    }

    private void visitOtherPropertyDeclaration(Node getProp) {
      checkArgument(getProp.isGetProp());
      checkArgument(getProp.isQualifiedName());
      if (isCtorWithoutFunctionLiteral(getProp)) {
        computeFnDeclaredType(
            NodeUtil.getBestJSDocInfo(getProp),
            getProp.getQualifiedName(),
            getProp, null, this.currentScope);
        return;
      }
      if (isAnnotatedAsConst(getProp) && !isTranspiledLoopVariable(getProp)) {
        warnings.add(JSError.make(getProp, MISPLACED_CONST_ANNOTATION));
      }
      Node recv = getProp.getFirstChild();
      QualifiedName recvQname = QualifiedName.fromNode(recv);
      Declaration d = this.currentScope.getDeclaration(recvQname, false);
      if (d != null && d.getTypedef() != null) {
        warnings.add(JSError.make(getProp, CANNOT_ADD_PROPERTIES_TO_TYPEDEF));
        getProp.getParent().putBooleanProp(Node.ANALYZED_DURING_GTI, true);
        return;
      }
      if (isPrototypeProperty(getProp.getFirstChild())) {
        mayAddPropertyToPrototypeMethod(getProp);
        return;
      }
      JSType recvType = simpleInferExpr(recv, this.currentScope);
      if (recvType == null) {
        return;
      }
      recvType = recvType.removeType(getCommonTypes().NULL_OR_UNDEFINED);
      NominalType nt = recvType.getNominalTypeIfSingletonObj();
      // Don't add stray properties to Object.
      if (nt == null || nt.equals(getCommonTypes().getObjectType())) {
        return;
      }
      RawNominalType rawType = nt.getRawNominalType();
      String pname = getProp.getLastChild().getString();
      JSType declType =
          getDeclaredTypeOfNode(NodeUtil.getBestJSDocInfo(getProp), this.currentScope);
      if (declType != null) {
        declType = declType.substituteGenericsWithUnknown();
        JSType prevType = rawType.getInstancePropDeclaredType(pname);
        // JSCompiler doesn't allow declaring function types with properties yet.
        // The workaround way is to define a function typedef, then declare a variable
        // of that type, and declare properties on that variable.
        // These properties get added to Function. Don't warn about duplicate definitions
        // in this case.
        if (nt.isFunction() && prevType != null) {
          declType = JSType.join(declType, prevType);
          if (declType.isBottom()) {
            declType = getCommonTypes().UNKNOWN;
          }
        } else if (mayWarnAboutExistingProp(rawType, pname, getProp, declType)) {
          return;
        }
        rawType.addPropertyWhichMayNotBeOnAllInstances(pname, declType);
      } else if (!rawType.mayHaveProp(pname)) {
        rawType.addPropertyWhichMayNotBeOnAllInstances(pname, null);
      }
    }

    /**
     * Handle property declarations on prototype methods, such as:
     *   Foo.prototype.f = function() {};
     *   Foo.prototype.f.numprop = 123;
     * We need to handle these definitions because some frameworks define such properties.
     * Note that these declarations are still not as general as declarations on namespaces, e.g.,
     * we don't handle definitions of new types on prototype methods.
     */
    void mayAddPropertyToPrototypeMethod(Node getProp) {
      Node protoProp = getProp.getFirstChild();
      if (!isPrototypeProperty(protoProp)) {
        return;
      }
      String protoPropName = protoProp.getLastChild().getString();
      QualifiedName rawtypeQname = QualifiedName.fromNode(protoProp.getFirstFirstChild());
      RawNominalType ownerType = this.currentScope.getNominalType(rawtypeQname);
      if (ownerType == null) {
        return;
      }
      PropertyDef def = propertyDefs.get(ownerType, protoPropName);
      if (def == null || def.methodType == null) {
        return;
      }
      Node rhs = NodeUtil.getRValueOfLValue(getProp);
      JSType newPropType = rhs == null ? null : simpleInferExpr(rhs, this.currentScope);
      if (newPropType == null) {
        newPropType = getCommonTypes().UNKNOWN;
      }
      if (newPropType.isConstructor() || newPropType.isInterfaceDefinition()) {
        // A prototype property is not a namespace, so don't allow defining types on it.
        return;
      }
      String newPropName = getProp.getLastChild().getString();
      JSType protoPropType = ownerType.getProtoPropDeclaredType(protoPropName);
      if (protoPropType == null) {
        protoPropType = getCommonTypes().fromFunctionType(def.methodType.toFunctionType());
      }
      ownerType.updateProtoProperty(
          protoPropName,
          protoPropType.withProperty(new QualifiedName(newPropName), newPropType));
    }

    boolean mayWarnAboutNoInit(Node constExpr) {
      if (constExpr.isFromExterns()) {
        return false;
      }
      Node initializer = NodeUtil.getRValueOfLValue(constExpr);
      if (initializer == null) {
        warnings.add(JSError.make(constExpr, CONST_WITHOUT_INITIALIZER));
        return true;
      }
      return false;
    }

    private JSType mayInferFromRhsIfConst(Node lvalueNode) {
      JSDocInfo info = NodeUtil.getBestJSDocInfo(lvalueNode);
      if (info != null && info.containsFunctionDeclaration()) {
        return null;
      }
      if (isConst(lvalueNode) && !mayWarnAboutNoInit(lvalueNode)) {
        return inferConstTypeFromRhs(lvalueNode);
      }
      return null;
    }

    // If a @const doesn't have a declared type, we use the initializer to infer a type.
    // When we cannot infer the type of the initializer, we warn.
    // This way, people do not need to remember the cases where the compiler
    // can infer the type of a constant; we tell them if we cannot infer it.
    // This function is called only when the @const has no declared type.
    private JSType inferConstTypeFromRhs(Node constExpr) {
      if (constExpr.isFromExterns()) {
        warnings.add(JSError.make(
            constExpr, COULD_NOT_INFER_CONST_TYPE,
            getNodeNameForConstWarning(constExpr)));
        return null;
      }
      Node rhs = NodeUtil.getRValueOfLValue(constExpr);
      JSType rhsType = simpleInferExpr(rhs, this.currentScope);
      boolean isUnescapedVar = constExpr.isName()
          && constExpr.getParent().isVar()
          && !this.currentScope.isEscapedVar(constExpr.getString());
      if ((rhsType == null || rhsType.isUnknown()) && !isUnescapedVar) {
        warnings.add(JSError.make(
            constExpr, COULD_NOT_INFER_CONST_TYPE,
            getNodeNameForConstWarning(constExpr)));
        return null;
      }
      return rhsType;
    }

    private String getNodeNameForConstWarning(Node constExpr) {
      checkArgument(constExpr.isQualifiedName() || constExpr.isStringKey(), constExpr);
      return constExpr.isQualifiedName()
          ? constExpr.getQualifiedName() : constExpr.getString();
    }

    private boolean mayAddPropToType(Node getProp, RawNominalType rawType) {
      if (!rawType.isStruct()) {
        return true;
      }
      Node parent = getProp.getParent();
      return ((parent.isAssign() && getProp == parent.getFirstChild()) || parent.isExprResult())
          && this.currentScope.isConstructor();
    }

    private boolean mayWarnAboutExistingProp(RawNominalType classType,
        String pname, Node propCreationNode, JSType typeInJsdoc) {
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(propCreationNode);
      JSType previousPropType = classType.getInstancePropDeclaredType(pname);
      if (classType.mayHaveNonInheritedProp(pname)
          && previousPropType != null
          // TODO(sdh): Remove this special case once all explicit decls are removed b/67509620
          && !previousPropType.toString().endsWith("(Proxy)")
          && !suppressDupPropWarning(jsdoc, typeInJsdoc, previousPropType)) {
        warnings.add(JSError.make(
            propCreationNode, REDECLARED_PROPERTY, pname, "type " + classType));
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
    //    https://github.com/google/closure-compiler/wiki/Warnings#suppress-tags
    // For now, we implement it b/c it exists in the current type inference.
    // But I wouldn't mind if we stopped supporting it.
    private boolean suppressDupPropWarning(
        JSDocInfo propCreationJsdoc, JSType typeInJsdoc, JSType previousType) {
      if (propCreationJsdoc == null
          || !propCreationJsdoc.getSuppressions().contains("duplicate")) {
        return false;
      }
      return typeInJsdoc == null || typeInJsdoc.equals(previousType);
    }

    private DeclaredFunctionType computeFnDeclaredType(
        JSDocInfo fnDoc, String functionName, Node declNode,
        RawNominalType ownerType, NTIScope parentScope) {
      checkArgument(declNode.isFunction() || declNode.isQualifiedName() || declNode.isCall());

      // For an unannotated function, check if we can grab a type signature for
      // it from the surrounding code where it appears.
      if (fnDoc == null && !NodeUtil.functionHasInlineJsdocs(declNode)) {
        DeclaredFunctionType t =
            getDeclaredFunctionTypeFromContext(functionName, declNode, parentScope);
        if (t != null) {
          return t;
        }
      }
      // TODO(dimvar): warn if multiple jsdocs for a fun
      RawNominalType ctorType = nominaltypesByNode.get(declNode);
      FunctionAndSlotType result = getTypeParser().getFunctionType(
          fnDoc, functionName, declNode, ctorType, ownerType, parentScope);
      // If the function does not have a declared THIS type, see if it's used in a bind,
      // and if so, try to infer a THIS type from the object pass to bind.
      if (result.functionType.getReceiverType() == null) {
        JSType bindRecvType = getReceiverTypeOfBind(declNode);
        result.functionType = result.functionType.withReceiverType(bindRecvType);
      }
      Node qnameNode;
      if (declNode.isQualifiedName()) {
        qnameNode = declNode;
      } else if (declNode.isFunction()) {
        qnameNode = NodeUtil.getNameNode(declNode);
      } else {
        qnameNode = NodeUtil.getBestLValue(declNode);
      }
      if (result.slotType != null && qnameNode != null && qnameNode.isName()) {
        parentScope.addDeclaredLocal(
            qnameNode.getString(), result.slotType, false, qnameNode.isFromExterns());
      }
      if (ctorType != null) {
        FunctionType ft = result.functionType.toFunctionType();
        ctorType.setCtorFunction(ft);
        if (ctorType.isBuiltinObject()) {
          getCommonTypes().getLiteralObjNominalType().getRawNominalType().setCtorFunction(ft);
        }
      }
      if (declNode.isFunction()) {
        maybeWarnFunctionDeclaration(declNode, result.functionType);
      }
      return result.functionType;
    }

    private void maybeWarnFunctionDeclaration(Node funNode, DeclaredFunctionType funType) {
      if (funNode.getParent().isSetterDef()) {
        JSType returnType = funType.getReturnType();
        if (returnType != null && !returnType.isUnknown() && !returnType.isUndefined()) {
          warnings.add(JSError.make(funNode, SETTER_WITH_RETURN));
        }
      }
      int declaredArity = funType.getOptionalArity();
      int parameterCount = funNode.getSecondChild().getChildCount();
      if (!funType.hasRestFormals() && parameterCount != declaredArity) {
        warnings.add(JSError.make(funNode, WRONG_PARAMETER_COUNT,
            String.valueOf(declaredArity), String.valueOf(parameterCount)));
      }
    }

    /**
     * Computes a declared type for an unannotated callback using the corresponding formal
     * type from the callee.
     * We redo this in NewTypeInference and may find a better type for the callback there,
     * but we still need to do it here as well, to infer constants inside the callback.
     */
    private DeclaredFunctionType computeFnDeclaredTypeFromCallee(
        Node callback, JSType declaredTypeAsJSType) {
      checkArgument(callback.isFunction());
      checkArgument(callback.getParent().isCall());

      if (declaredTypeAsJSType == null) {
        return null;
      }
      FunctionType funType = declaredTypeAsJSType.getFunType();
      if (funType == null
          || funType.isUniqueConstructor()
          || funType.isInterfaceDefinition()) {
        return null;
      }
      DeclaredFunctionType declType = funType.toDeclaredFunctionType();
      if (declType == null) {
        return null;
      }
      return NodeUtil.getApproxRequiredArity(callback) <= declType.getMaxArity()
          ? declType : null;
    }

    // Returns null if it can't find a suitable type in the context
    private DeclaredFunctionType getDeclaredFunctionTypeFromContext(
        String functionName, Node declNode, NTIScope parentScope) {
      Node parent = declNode.getParent();

      if (parent.isCast()) {
        JSDocInfo jsdoc = parent.getJSDocInfo();
        JSType castType = getDeclaredTypeOfNode(jsdoc, parentScope);
        FunctionType funType = castType.getFunType();
        if (funType != null) {
          return funType.toDeclaredFunctionType();
        }
        return null;
      }

      JSType bindRecvType = getReceiverTypeOfBind(declNode);
      if (bindRecvType != null) {
        // Use typeParser for the formals, and only add the receiver type here.
        DeclaredFunctionType allButRecvType = getTypeParser().getFunctionType(
            null, functionName, declNode, null, null, parentScope).functionType;
        return allButRecvType.withReceiverType(bindRecvType);
      }

      // The function literal is an argument at a call
      if (NodeUtil.isUnannotatedCallback(declNode)) {
        Node callee = parent.getFirstChild();
        JSType calleeType = simpleInferExpr(callee, this.currentScope);
        FunctionType calleeFunType = calleeType == null ? null : calleeType.getFunType();
        if (calleeFunType != null) {
          if (calleeFunType.isGeneric()) {
            calleeFunType = simpleInferInstantiatedCallee(
                parent, calleeFunType, false, this.currentScope);
            if (calleeFunType == null) {
              return null;
            }
          }
          int index = parent.getIndexOfChild(declNode) - 1;
          JSType callbackType = calleeFunType.getFormalType(index);
          DeclaredFunctionType t = computeFnDeclaredTypeFromCallee(declNode, callbackType);
          if (t != null) {
            return t;
          }
        }
      }

      return null;
    }

    /**
     * If the argument function is used in a bind in the AST, infer the receiver type.
     * Return null otherwise.
     */
    private JSType getReceiverTypeOfBind(Node funDeclNode) {
      Node parent = funDeclNode.getParent();
      Node maybeBind = parent.isCall() ? parent.getFirstChild() : parent;
      // The function literal is used with .bind or goog.bind
      if (NodeUtil.isFunctionBind(maybeBind) && !NodeUtil.isGoogPartial(maybeBind)) {
        Node call = maybeBind.getParent();
        Bind bindComponents = convention.describeFunctionBind(call, true, false);
        return bindComponents.thisValue == null
            ? null : simpleInferExpr(bindComponents.thisValue, this.currentScope);
      }
      return null;
    }

    /**
     * Called for the usual style of prototype-property definitions,
     * but also for @lends and for direct assignments of object literals to prototypes.
     */
    private void mayAddPropToPrototype(
        RawNominalType rawType, String pname, Node defSite, Node initializer) {
      NTIScope methodScope = null;
      DeclaredFunctionType methodType = null;
      JSType propDeclType = null;

      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(defSite);
      if (initializer != null && initializer.isFunction()) {
        methodScope = visitFunctionLate(initializer, rawType);
        methodType = methodScope.getDeclaredFunctionType();
        if (defSite.isGetterDef()) {
          pname = getCommonTypes().createGetterPropName(pname);
        } else if (defSite.isSetterDef()) {
          pname = getCommonTypes().createSetterPropName(pname);
        }
      } else if (jsdoc != null && jsdoc.containsFunctionDeclaration()
          // This can happen with stray jsdocs in an object literal
          && !defSite.isStringKey()) {
        // We're parsing a function declaration without a function initializer
        methodType = computeFnDeclaredType(jsdoc, pname, defSite, rawType, currentScope);
      }

      // Find the declared type of the property.
      if (jsdoc != null && jsdoc.hasType()) {
        propDeclType = getTypeParser().getDeclaredTypeOfNode(jsdoc, rawType, currentScope);
      } else if (methodType != null) {
        propDeclType = getCommonTypes().fromFunctionType(methodType.toFunctionType());
      }
      if (defSite.isGetterDef()) {
        FunctionType ft = propDeclType.getFunTypeIfSingletonObj();
        if (ft != null) {
          propDeclType = ft.getReturnType();
        }
      }
      PropertyDef def = new PropertyDef(defSite, methodType, methodScope);
      propertyDefs.put(rawType, pname, def);

      // Warn for abstract methods not in abstract classes
      if (methodType != null && methodType.isAbstract() && !rawType.isAbstractClass()) {
        if (rawType.isClass()) {
          warnings.add(JSError.make(defSite, ABSTRACT_METHOD_IN_CONCRETE_CLASS, rawType.getName()));
        } else if (rawType.isInterface()) {
          warnings.add(JSError.make(defSite, ABSTRACT_METHOD_IN_INTERFACE));
        }
      }

      // Add the property to the class with the appropriate type.
      boolean isConst = isConst(defSite);
      if (propDeclType != null || isConst) {
        if (mayWarnAboutExistingProp(rawType, pname, defSite, propDeclType)) {
          return;
        }
        if (propDeclType == null) {
          propDeclType = mayInferFromRhsIfConst(defSite);
        }
        rawType.addProtoProperty(pname, defSite, propDeclType, isConst);
        if (defSite.isGetProp()) { // Don't bother saving for @lends
          defSite.putBooleanProp(Node.ANALYZED_DURING_GTI, true);
          if (isConst) {
            defSite.putBooleanProp(Node.CONSTANT_PROPERTY_DEF, true);
          }
        }
      } else {
        JSType inferredType = null;
        if (initializer != null) {
          inferredType = simpleInferExpr(initializer, this.currentScope);
        }
        if (inferredType == null) {
          inferredType = getCommonTypes().UNKNOWN;
        }
        rawType.addUndeclaredProtoProperty(pname, defSite, inferredType);
      }
    }

    // TODO(dimvar): This method is used to avoid a spurious warning in ES6
    // externs, where a prototype property is declared using GETELEM.
    // We'll remove this when we properly handle ES6.
    private RawNominalType maybeGetOwnerType(Node funNode, Node parent) {
      checkArgument(funNode.isFunction());
      if (parent.isAssign() && parent.getFirstChild().isGetElem()) {
        Node recv = parent.getFirstFirstChild();
        if (recv.isGetProp() && recv.getLastChild().getString().equals("prototype")) {
          QualifiedName qname = QualifiedName.fromNode(recv.getFirstChild());
          if (qname != null) {
            return this.currentScope.getNominalType(qname);
          }
        }
      }
      return null;
    }

    private boolean isNamedType(Node getProp) {
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(getProp);
      if (jsdoc != null && jsdoc.hasType() && !jsdoc.containsFunctionDeclaration()) {
        return false;
      }
      return this.currentScope.isNamespace(getProp) || NodeUtil.isTypedefDecl(getProp);
    }
  }

  private JSType getDeclaredTypeOfNode(JSDocInfo jsdoc, NTIScope s) {
    return getTypeParser().getDeclaredTypeOfNode(jsdoc, null, s);
  }

  private JSType getVarTypeFromAnnotation(Node nameNode, NTIScope currentScope) {
    checkArgument(nameNode.getParent().isVar());
    Node varNode = nameNode.getParent();
    JSType varType =
        getDeclaredTypeOfNode(varNode.getJSDocInfo(), currentScope);
    if (varNode.hasMoreThanOneChild() && varType != null) {
      warnings.add(JSError.make(varNode, ONE_TYPE_FOR_MANY_VARS));
    }
    String varName = nameNode.getString();
    JSType nameNodeType =
        getDeclaredTypeOfNode(nameNode.getJSDocInfo(), currentScope);
    if (nameNodeType != null) {
      if (varType != null) {
        warnings.add(JSError.make(nameNode, DUPLICATE_JSDOC, varName));
      }
      return nameNodeType;
    } else {
      return varType;
    }
  }

  private static boolean isPropertyDeclarationOnThis(Node n, NTIScope s) {
    Node parent = n.getParent();
    return n.isGetProp()
        && n.getFirstChild().isThis()
        && ((parent.isAssign() && parent.getFirstChild().equals(n)) || parent.isExprResult())
        && (s.isConstructor() || s.isInterface() || s.isPrototypeMethod());
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

  private boolean isPrototypePropertyDeclaration(Node n) {
    if (NodeUtil.isExprAssign(n)
        && isPrototypeProperty(n.getFirstFirstChild())
        // When the prototype property is not on a qualified name, we can't generally
        // find the name of the class, so we don't do anything.
        && n.getFirstFirstChild().isQualifiedName()) {
      Node protoProp = n.getFirstFirstChild();
      // record the "prototype" property
      recordPropertyName(protoProp.getFirstChild().getLastChild());
      return true;
    }
    // We are looking for either an object literal being assigned to a
    // prototype, or an object literal being lent to a prototype.
    if (n.isObjectLit()) {
      Node parent = n.getParent();
      if (parent.isAssign()
          && parent.getParent().isExprResult()
          && parent.getFirstChild().isGetProp()
          && parent.getFirstChild().getLastChild().getString().equals("prototype")) {
        return true;
      }
      JSDocInfo jsdoc = n.getJSDocInfo();
      return jsdoc != null && jsdoc.getLendsName() != null
          && jsdoc.getLendsName().endsWith("prototype");
    }
    return false;
  }

  private static boolean isAnnotatedAsConst(Node defSite) {
    JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(defSite);
    return jsdoc != null && jsdoc.hasConstAnnotation() && !jsdoc.isConstructor();
  }

  private static Node fromDefsiteToName(Node defSite) {
    if (defSite.isGetProp()) {
      return defSite.getLastChild();
    }
    if (defSite.isName() || defSite.isStringKey()
        || defSite.isGetterDef() || defSite.isSetterDef()
        || defSite.isMemberFunctionDef()) {
      return defSite;
    }
    throw new RuntimeException("Unknown defsite: " + defSite.getToken());
  }

  private boolean isConst(Node defSite) {
    return isAnnotatedAsConst(defSite)
        // Don't consider an all-caps variable in externs to be constant.
        // 1) External code may not follow the coding convention.
        // 2) We generate synthetic externs to export local properties,
        //    which may be in all caps.
        || (!defSite.isFromExterns()
            && NodeUtil.isConstantByConvention(this.convention, fromDefsiteToName(defSite)));
  }

  private static boolean isCtorWithoutFunctionLiteral(Node qnameNode) {
    if (!qnameNode.isFromExterns()) {
      return false;
    }
    JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(qnameNode);
    if (jsdoc == null || !jsdoc.isConstructor()) {
      return false;
    }
    if (qnameNode.isName()) {
      return qnameNode.getParent().isVar() && !qnameNode.hasChildren();
    }
    if (qnameNode.isGetProp()) {
      return qnameNode.getParent().isExprResult();
    }
    return false;
  }

  private static boolean isCtorDefinedByCall(RawNominalType rawType) {
    return isCtorDefinedByCall(NodeUtil.getBestLValue(rawType.getDefSite()));
  }

  static boolean isCtorDefinedByCall(Node qnameNode) {
    if (!qnameNode.isName() && !qnameNode.isGetProp()) {
      return false;
    }
    JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(qnameNode);
    Node rhs = NodeUtil.getRValueOfLValue(qnameNode);
    return jsdoc != null && jsdoc.isConstructor() && rhs != null && rhs.isCall();
  }

  // Utility class when analyzing property declarations
  private static class PropertyType {
  // The declared type of the property, from the jsdoc.
    JSType declType = null;
    // When the property doesn't have a jsdoc, and is initialized to a function,
    // this field stores a "bare-bones" function type.
    JSType inferredFunType = null;
  }

  private static class PropertyDef {
    final Node defSite; // The getProp/objectLitKey of the property definition
    DeclaredFunctionType methodType; // null for non-method property decls
    final NTIScope methodScope; // null for decls without function on the RHS

    PropertyDef(
        Node defSite, DeclaredFunctionType methodType, NTIScope methodScope) {
      checkNotNull(defSite);
      checkArgument(defSite.isGetProp() || NodeUtil.isObjectLitKey(defSite));
      this.defSite = defSite;
      this.methodType = methodType;
      this.methodScope = methodScope;
    }

    PropertyDef substituteNominalGenerics(NominalType nt) {
      checkArgument(nt.isGeneric(), nt);
      if (this.methodType == null) {
        return this;
      }
      PropertyDef def = new PropertyDef(
          this.defSite, this.methodType.substituteNominalGenerics(nt), this.methodScope);
      return def;
    }

    void updateMethodType(DeclaredFunctionType updatedType) {
      this.methodType = updatedType;
      if (this.methodScope != null) {
        this.methodScope.setDeclaredType(updatedType);
      }
    }

    @Override
    public String toString() {
      return "PropertyDef(" + defSite + ", " + methodType + ")";
    }
  }

  private Map<Node, String> getAnonFunNames() {
    return this.globalTypeInfo.getAnonFunNames();
  }

  private NTIScope getGlobalScope() {
    return this.globalTypeInfo.getGlobalScope();
  }

  private JSTypes getCommonTypes() {
    return this.globalTypeInfo.getCommonTypes();
  }

  private JSTypeCreatorFromJSDoc getTypeParser() {
    return this.globalTypeInfo.getTypeParser();
  }

  private Set<String> getExternPropertyNames() {
    return this.globalTypeInfo.getExternPropertyNames();
  }

  private String getFunInternalName(Node n) {
    return this.globalTypeInfo.getFunInternalName(n);
  }

  private UniqueNameGenerator getVarNameGen() {
    return this.globalTypeInfo.getVarNameGen();
  }

  private void recordPropertyName(Node pnameNode) {
    this.globalTypeInfo.recordPropertyName(pnameNode);
  }

  private JSType simpleInferDeclaration(Declaration decl) {
    return this.simpleInference.inferDeclaration(decl);
  }

  private JSType simpleInferExpr(Node n, NTIScope scope) {
    return this.simpleInference.inferExpr(n, scope);
  }

  private FunctionType simpleInferInstantiatedCallee(
      Node call, FunctionType calleeType, boolean bailForUntypedArguments, NTIScope scope) {
    return this.simpleInference.inferInstantiatedCallee(
        call, calleeType, bailForUntypedArguments, scope);
  }
}
