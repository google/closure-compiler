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

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.AbstractCompiler.MostRecentTypechecker;
import com.google.javascript.jscomp.CodingConvention.Bind;
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
import com.google.javascript.jscomp.newtypes.NominalType;
import com.google.javascript.jscomp.newtypes.ObjectKind;
import com.google.javascript.jscomp.newtypes.QualifiedName;
import com.google.javascript.jscomp.newtypes.RawNominalType;
import com.google.javascript.jscomp.newtypes.Typedef;
import com.google.javascript.jscomp.newtypes.UniqueNameGenerator;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TypeI;
import com.google.javascript.rhino.TypeIRegistry;
import com.google.javascript.rhino.jstype.JSTypeNative;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Contains information about all scopes; for every variable reference computes
 * whether it is local, a formal parameter, etc.; and computes information about
 * the class hierarchy.
 *
 * <p>Used by the new type inference. See go/jscompiler-new-type-checker for the
 * latest updates.
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
class GlobalTypeInfo implements CompilerPass, TypeIRegistry {

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

  static final DiagnosticType FUNCTION_CONSTRUCTOR_NOT_DEFINED =
      DiagnosticType.error(
          "JSC_NTI_FUNCTION_CONSTRUCTOR_NOT_DEFINED",
          "You must provide externs that define the built-in Function constructor.");

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

  static final DiagnosticType SUPER_INTERFACES_HAVE_INCOMPATIBLE_PROPERTIES =
      DiagnosticType.warning(
          "JSC_NTI_SUPER_INTERFACES_HAVE_INCOMPATIBLE_PROPERTIES",
          "Interface {0} has a property {1} with incompatible types in "
          + "its super interfaces: {2}");

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
      FUNCTION_CONSTRUCTOR_NOT_DEFINED,
      INEXISTENT_PARAM,
      INTERFACE_METHOD_NOT_IMPLEMENTED,
      INTERFACE_METHOD_NOT_EMPTY,
      INVALID_INTERFACE_PROP_INITIALIZER,
      INVALID_PROP_OVERRIDE,
      LENDS_ON_BAD_TYPE,
      ONE_TYPE_FOR_MANY_VARS,
      REDECLARED_PROPERTY,
      STRUCT_WITHOUT_CTOR_OR_INTERF,
      SUPER_INTERFACES_HAVE_INCOMPATIBLE_PROPERTIES,
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

  // An out-to-in list of the scopes, built during CollectNamedTypes
  // This will be reversed at the end of GlobalTypeInfo to make sure
  // that the scopes can be processed in-to-out in NewTypeInference.
  private final List<NTIScope> scopes = new ArrayList<>();
  private NTIScope globalScope;
  private WarningReporter warnings;
  private final JSTypeCreatorFromJSDoc typeParser;
  private final AbstractCompiler compiler;
  private final CodingConvention convention;
  private final Map<Node, String> anonFunNames = new LinkedHashMap<>();
  // Uses %, which is not allowed in identifiers, to avoid naming clashes
  // with existing functions.
  private static final String ANON_FUN_PREFIX = "%anon_fun";
  private static final String WINDOW_INSTANCE = "window";
  private static final String WINDOW_CLASS = "Window";
  private DefaultNameGenerator funNameGen;
  private UniqueNameGenerator varNameGen;
  // Only for original definitions, not for aliased constructors
  private Map<Node, RawNominalType> nominaltypesByNode = new LinkedHashMap<>();
  // Keyed on RawNominalTypes and property names
  private HashBasedTable<RawNominalType, String, PropertyDef> propertyDefs =
      HashBasedTable.create();
  // TODO(dimvar): Eventually attach these to nodes, like the current types.
  private final Map<Node, JSType> castTypes = new LinkedHashMap<>();
  private final Map<Node, JSType> declaredObjLitProps = new LinkedHashMap<>();

  private final JSTypes commonTypes;
  private final Set<String> unknownTypeNames;
  // It's useful to know all properties defined anywhere in the program.
  // For a property access on ?, we can warn if the property isn't defined;
  // same for Object in compatibility mode.
  private final Set<String> allPropertyNames = new LinkedHashSet<>();
  // All property names that appear in externs. This means that with NTI,
  // we don't need to run GatherExternProperties, which uses the OTI-specific
  // Visitor interface.
  private final Set<String> externPropertyNames = new LinkedHashSet<>();

  GlobalTypeInfo(AbstractCompiler compiler, Set<String> unknownTypeNames) {
    // TODO(dimvar): it's bad style to refer to DiagnosticGroups after DefaultPassConfig.
    // When we have a nicer way to know whether we're in compatibility mode, use that
    // here instead (and in the constructor of NewTypeInference).
    boolean inCompatibilityMode =
        compiler.getOptions().disables(DiagnosticGroups.NEW_CHECK_TYPES_EXTRA_CHECKS);

    this.warnings = new WarningReporter(compiler);
    this.compiler = compiler;
    this.unknownTypeNames = unknownTypeNames;
    this.convention = compiler.getCodingConvention();
    this.varNameGen = new UniqueNameGenerator();
    this.funNameGen = new DefaultNameGenerator(ImmutableSet.<String>of(), "", null);
    this.commonTypes = JSTypes.init(inCompatibilityMode);
    Function<String, Void> callback = new Function<String, Void>() {
      @Override
      public Void apply(String pname) {
        allPropertyNames.add(pname);
        externPropertyNames.add(pname);
        return null;
      }
    };
    this.typeParser = new JSTypeCreatorFromJSDoc(
        this.commonTypes, this.convention, this.varNameGen, callback);
    this.allPropertyNames.add("prototype");
  }

  Collection<NTIScope> getScopes() {
    return scopes;
  }

  NTIScope getGlobalScope() {
    return this.globalScope;
  }

  JSTypes getCommonTypes() {
    return this.commonTypes;
  }

  JSType getCastType(Node n) {
    JSType t = castTypes.get(n);
    Preconditions.checkNotNull(t);
    return t;
  }

  JSType getPropDeclaredType(Node n) {
    return declaredObjLitProps.get(n);
  }

  boolean isPropertyDefined(String pname) {
    return this.allPropertyNames.contains(pname);
  }

  void recordPropertyName(String pname, Node defSite) {
    allPropertyNames.add(pname);
    if (defSite.isFromExterns()) {
      externPropertyNames.add(pname);
    }
  }

  // Differs from the similar method in NTIScope class on how it treats qnames.
  String getFunInternalName(Node n) {
    Preconditions.checkArgument(n.isFunction());
    if (anonFunNames.containsKey(n)) {
      return anonFunNames.get(n);
    }
    Node fnNameNode = NodeUtil.getNameNode(n);
    // We don't want to use qualified names here
    Preconditions.checkState(fnNameNode != null);
    Preconditions.checkState(fnNameNode.isName());
    return fnNameNode.getString();
  }

  @Override
  public void process(Node externs, Node root) {
    Preconditions.checkNotNull(warnings, "Cannot rerun GlobalTypeInfo.process");
    Preconditions.checkArgument(externs == null || externs.isRoot());
    Preconditions.checkArgument(root.isRoot());

    this.compiler.setMostRecentTypechecker(MostRecentTypechecker.NTI);

    this.globalScope = new NTIScope(root, null, ImmutableList.<String>of(), commonTypes);
    this.globalScope.addUnknownTypeNames(this.unknownTypeNames);
    scopes.add(this.globalScope);

    // Processing of a scope is split into many separate phases, and it's not
    // straightforward to remember which phase does what.

    // (1) Find names of classes, interfaces, typedefs, enums, and namespaces
    //   defined in the global scope.
    CollectNamedTypes rootCnt = new CollectNamedTypes(this.globalScope);
    if (externs != null) {
      NodeTraversal.traverseEs6(compiler, externs, rootCnt);
    }
    NodeTraversal.traverseEs6(compiler, root, rootCnt);
    // (2) Determine the type represented by each typedef and each enum
    this.globalScope.resolveTypedefs(typeParser);
    this.globalScope.resolveEnums(typeParser);
    // (3) Repeat steps 1-2 for all the other scopes (outer-to-inner)
    for (int i = 1; i < scopes.size(); i++) {
      NTIScope s = scopes.get(i);
      CollectNamedTypes cnt = new CollectNamedTypes(s);
      NodeTraversal.traverseEs6(compiler, s.getBody(), cnt);
      s.resolveTypedefs(typeParser);
      s.resolveEnums(typeParser);
      if (NewTypeInference.measureMem) {
        NewTypeInference.updatePeakMem();
      }
    }

    // If the Function constructor isn't defined, we cannot create function
    // types. Exit early.
    if (this.commonTypes.getFunctionType() == null) {
      warnings.add(JSError.make(root, FUNCTION_CONSTRUCTOR_NOT_DEFINED));
      return;
    }

    // (4) The bulk of the global-scope processing happens here:
    //     - Create scopes for functions
    //     - Declare properties on types
    ProcessScope rootPs = new ProcessScope(this.globalScope);
    if (externs != null) {
      NodeTraversal.traverseEs6(compiler, externs, rootPs);
    }
    NodeTraversal.traverseEs6(compiler, root, rootPs);
    // (5) Things that must happen after the traversal of the scope
    rootPs.finishProcessingScope();

    // (6) Repeat steps 4-5 for all the other scopes (outer-to-inner)
    for (int i = 1; i < scopes.size(); i++) {
      NTIScope s = scopes.get(i);
      ProcessScope ps = new ProcessScope(s);
      NodeTraversal.traverseEs6(compiler, s.getBody(), ps);
      ps.finishProcessingScope();
      if (NewTypeInference.measureMem) {
        NewTypeInference.updatePeakMem();
      }
    }

    // (7) Adjust types of properties based on inheritance information.
    //     Report errors in the inheritance chain. Do Window last.
    RawNominalType win = null;
    for (Map.Entry<Node, RawNominalType> entry : nominaltypesByNode.entrySet()) {
      RawNominalType rawType = entry.getValue();
      if (rawType.getName().equals(WINDOW_CLASS) && entry.getKey().isFromExterns()) {
        win = rawType;
        continue;
      }
      checkAndFinalizeNominalType(rawType);
      if (rawType.isBuiltinObject()) {
        this.commonTypes.getLiteralObjNominalType().getRawNominalType().finalize();
      }
    }
    JSType globalThisType;
    if (win != null) {
      // Copy properties from window to Window.prototype, because in rare cases
      // people pass window around rather than using it directly.
      Namespace winNs = this.globalScope.getNamespace(WINDOW_INSTANCE);
      if (winNs != null) {
        winNs.copyWindowProperties(this.commonTypes, win);
      }
      checkAndFinalizeNominalType(win);
      // Type the global THIS as window
      globalThisType = win.getInstanceAsJSType();
    } else {
      // Type the global THIS as a loose object
      globalThisType = this.commonTypes.getTopObject().withLoose();
    }
    this.globalScope.setDeclaredType(
        (new FunctionTypeBuilder(this.commonTypes)).
        addReceiverType(globalThisType).buildDeclaration());

    nominaltypesByNode = null;
    propertyDefs = null;
    for (NTIScope s : scopes) {
      s.finalizeScope();
    }
    Map<Node, String> unknownTypes = typeParser.getUnknownTypesMap();
    for (Map.Entry<Node, String> unknownTypeEntry : unknownTypes.entrySet()) {
      this.warnings.add(JSError.make(unknownTypeEntry.getKey(),
              UNRECOGNIZED_TYPE_NAME, unknownTypeEntry.getValue()));
    }
    // The jsdoc parser doesn't have access to the error functions in the jscomp
    // package, so we collect its warnings here.
    for (JSError warning : typeParser.getWarnings()) {
      this.warnings.add(warning);
    }
    this.warnings = null;
    this.varNameGen = null;
    this.funNameGen = null;

    // If a scope s1 contains a scope s2, then s2 must be before s1 in scopes.
    // The type inference relies on this fact to process deeper scopes
    // before shallower scopes.
    Collections.reverse(scopes);

    this.compiler.setExternProperties(ImmutableSet.copyOf(this.externPropertyNames));
  }

  private Collection<PropertyDef> getPropDefsFromInterface(
      NominalType nominalType, String pname) {
    Preconditions.checkArgument(nominalType.isFinalized());
    Preconditions.checkArgument(
        nominalType.isInterface() || nominalType.isBuiltinObject());
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

  private PropertyDef getPropDefFromClass(NominalType nominalType, String pname) {
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

  private void checkAndFinalizeNominalType(RawNominalType rawType) {
    if (rawType.isFinalized()) {
      return;
    }
    NominalType superClass = rawType.getSuperClass();
    Set<String> nonInheritedPropNames = rawType.getAllOwnProps();
    if (superClass != null && !superClass.isFinalized()) {
      checkAndFinalizeNominalType(superClass.getRawNominalType());
    }
    for (NominalType superInterf : rawType.getInterfaces()) {
      if (!superInterf.isFinalized()) {
        checkAndFinalizeNominalType(superInterf.getRawNominalType());
      }
    }

    Multimap<String, DeclaredFunctionType> propMethodTypesToProcess =
        LinkedHashMultimap.create();
    Multimap<String, JSType> propTypesToProcess = LinkedHashMultimap.create();
    // Collect inherited types for extended classes
    if (superClass != null) {
      Preconditions.checkState(superClass.isFinalized());
      // TODO(blickly): Can we optimize this to skip unnecessary iterations?
      for (String pname : superClass.getAllPropsOfClass()) {
        if (superClass.isAbstractClass()
            && superClass.hasAbstractMethod(pname)
            && !rawType.isAbstractClass()
            && !rawType.mayHaveOwnProp(pname)) {
          warnings.add(JSError.make(
              rawType.getDefSite(), ABSTRACT_METHOD_NOT_IMPLEMENTED_IN_CONCRETE_CLASS,
              pname, superClass.getName()));
        }
        nonInheritedPropNames.remove(pname);
        checkSuperProperty(rawType, superClass, pname,
            propMethodTypesToProcess, propTypesToProcess);
      }
    }

    // Collect inherited types for extended/implemented interfaces
    for (NominalType superInterf : rawType.getInterfaces()) {
      Preconditions.checkState(superInterf.isFinalized());
      for (String pname : superInterf.getAllPropsOfInterface()) {
        nonInheritedPropNames.remove(pname);
        checkSuperProperty(rawType, superInterf, pname,
            propMethodTypesToProcess, propTypesToProcess);
      }
    }

    // Munge inherited types of methods
    for (String pname : propMethodTypesToProcess.keySet()) {
      Collection<DeclaredFunctionType> methodTypes =
          propMethodTypesToProcess.get(pname);
      Preconditions.checkState(!methodTypes.isEmpty());
      PropertyDef localPropDef =
          propertyDefs.get(rawType, pname);
      // To find the declared type of a method, we must meet declared types
      // from all inherited methods.
      DeclaredFunctionType superMethodType = DeclaredFunctionType.meet(methodTypes);
      DeclaredFunctionType localMethodType = localPropDef.methodType;
      boolean getsTypeFromParent = getsTypeInfoFromParentMethod(localPropDef);
      if (superMethodType == null) {
        // If the inherited types are not compatible, pick one.
        superMethodType = methodTypes.iterator().next();
        warnings.add(JSError.make(localPropDef.defSite,
                SUPER_INTERFACES_HAVE_INCOMPATIBLE_PROPERTIES,
                rawType.getName(), pname, methodTypes.toString()));
      } else if (getsTypeFromParent
          && localMethodType.getMaxArity() > superMethodType.getMaxArity()) {
        // When getsTypeFromParent is true, we will miss the invalid override
        // earlier, so we check here.
        warnings.add(JSError.make(
            localPropDef.defSite, INVALID_PROP_OVERRIDE, pname,
            superMethodType.toFunctionType().toString(),
            localMethodType.toFunctionType().toString()));
      }
      DeclaredFunctionType updatedMethodType = localMethodType
          .withTypeInfoFromSuper(superMethodType, getsTypeFromParent);
      localPropDef.updateMethodType(updatedMethodType);
      propTypesToProcess.put(pname,
          commonTypes.fromFunctionType(updatedMethodType.toFunctionType()));
    }

    // Check inherited types of all props
 add_interface_props:
    for (String pname : propTypesToProcess.keySet()) {
      Collection<JSType> defs = propTypesToProcess.get(pname);
      Preconditions.checkState(!defs.isEmpty());
      JSType resultType = commonTypes.TOP;
      for (JSType inheritedType : defs) {
        resultType = JSType.meet(resultType, inheritedType);
        if (!resultType.isBottom()) {
          resultType = inheritedType;
        } else {
          warnings.add(JSError.make(
              rawType.getDefSite(),
              SUPER_INTERFACES_HAVE_INCOMPATIBLE_PROPERTIES,
              rawType.getName(), pname,
              defs.toString()));
          continue add_interface_props;
        }
      }
      // TODO(dimvar): check if we can have @const props here
      rawType.addProtoProperty(pname, null, resultType, false);
    }

    // Warn when inheriting from incompatible IObject types
    if (rawType.inheritsFromIObject()) {
      JSType wrapped = rawType.getInstanceAsJSType();
      if (wrapped.getIndexType() == null) {
        warnings.add(JSError.make(
            rawType.getDefSite(),
            SUPER_INTERFACES_HAVE_INCOMPATIBLE_PROPERTIES,
            rawType.getName(),
            "IObject<K,V>#index",
            "the keys K have types that can't be joined."));
      } else if (wrapped.getIndexedType() == null) {
        warnings.add(JSError.make(
            rawType.getDefSite(),
            SUPER_INTERFACES_HAVE_INCOMPATIBLE_PROPERTIES,
            rawType.getName(),
            "IObject<K,V>#index",
            "the values V should have a common subtype."));
      }
    }

    // Warn for a prop declared with @override that isn't overriding anything.
    for (String pname : nonInheritedPropNames) {
      PropertyDef propDef = propertyDefs.get(rawType, pname);
      Preconditions.checkState(propDef != null || rawType.getName().equals(WINDOW_CLASS));
      if (propDef != null) {
        Node propDefsite = propDef.defSite;
        JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(propDefsite);
        if (jsdoc != null && jsdoc.isOverride()) {
          warnings.add(JSError.make(propDefsite, UNKNOWN_OVERRIDE,
                  pname, rawType.getName()));
        }
      }
    }

    // Finalize nominal type once all properties are added.
    rawType.finalize();
  }

  // TODO(dimvar): the finalization method and this one should be cleaned up;
  // they are very hard to understand.
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
    if (superType.isInterface() && current.isClass()
        && !current.mayHaveProp(pname)) {
      warnings.add(JSError.make(
          inheritedPropDefs.iterator().next().defSite,
          INTERFACE_METHOD_NOT_IMPLEMENTED,
          pname, superType.toString(), current.toString()));
      return;
    }
    PropertyDef localPropDef = propertyDefs.get(current, pname);
    JSType localPropType = localPropDef == null
        ? null : current.getInstancePropDeclaredType(pname);
    if (localPropDef != null && superType.isClass()
        && localPropType != null
        && localPropType.getFunType() != null
        && superType.hasConstantProp(pname)) {
      // TODO(dimvar): This doesn't work for multiple levels in the hierarchy.
      // Clean up how we process inherited properties and then fix this.
      warnings.add(JSError.make(
          localPropDef.defSite, CANNOT_OVERRIDE_FINAL_METHOD, pname));
      return;
    }
    if (localPropType == null && superType.isInterface()) {
      // Add property from interface to class
      propTypesToProcess.put(pname, inheritedPropType);
    } else if (localPropType != null
        && !getsTypeInfoFromParentMethod(localPropDef)
        && !isValidOverride(localPropType, inheritedPropType)) {
      warnings.add(JSError.make(
          localPropDef.defSite, INVALID_PROP_OVERRIDE, pname,
          inheritedPropType.toString(), localPropType.toString()));
    } else if (localPropType != null && localPropDef.methodType != null) {
      // If we are looking at a method definition, munging may be needed
      for (PropertyDef inheritedPropDef : inheritedPropDefs) {
        if (inheritedPropDef.methodType != null) {
          propMethodTypesToProcess.put(pname,
              inheritedPropDef.methodType.substituteNominalGenerics(superType));
        }
      }
    }
  }

  private boolean isValidOverride(JSType localPropType, JSType inheritedPropType) {
    FunctionType localFunType = localPropType.getFunTypeIfSingletonObj();
    FunctionType inheritedFunType = inheritedPropType.getFunTypeIfSingletonObj();
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
    return jsdoc == null || (jsdoc.isOverride() && !jsdoc.containsFunctionDeclaration());
  }

  /**
   * Collects names of classes, interfaces, namespaces, typedefs and enums.
   * This way, if a type name appears before its declaration, we know what
   * it refers to.
   */
  private class CollectNamedTypes extends AbstractShallowCallback {
    private final NTIScope currentScope;

    CollectNamedTypes(NTIScope s) {
      this.currentScope = s;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case FUNCTION: {
          visitFunctionEarly(n);
          break;
        }
        case VAR: {
          Node nameNode = n.getFirstChild();
          String varName = nameNode.getString();
          if (NodeUtil.isNamespaceDecl(nameNode)) {
            visitObjlitNamespace(nameNode);
          } else if (NodeUtil.isTypedefDecl(nameNode)) {
            visitTypedef(nameNode);
          } else if (NodeUtil.isEnumDecl(nameNode)) {
            visitEnum(nameNode);
          } else if (isAliasedNamespaceDefinition(nameNode)) {
            visitAliasedNamespace(nameNode);
          } else if (varName.equals(WINDOW_INSTANCE)
              && nameNode.isFromExterns()) {
            visitWindowVar(nameNode);
          } else if (isCtorDefinedByCall(nameNode)) {
            visitNewCtorDefinedByCall(nameNode);
          } else if (isCtorWithoutFunctionLiteral(nameNode)) {
            visitNewCtorWithoutFunctionLiteral(nameNode);
          }
          if (!n.isFromExterns()
              && !this.currentScope.isDefinedLocally(varName, false)) {
            // Add a dummy local to avoid shadowing errors, and to calculate
            // escaped variables.
            this.currentScope.addLocal(varName, commonTypes.UNKNOWN, false, false);
          }
          break;
        }
        case NAME: {
          if (this.currentScope.isFunction()) {
            NTIScope.mayRecordEscapedVar(this.currentScope, n.getString());
          }
          break;
        }
        case EXPR_RESULT: {
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
          break;
        }
        default:
          break;
      }
    }

    private void visitWindowVar(Node nameNode) {
      JSType typeInJsdoc = getVarTypeFromAnnotation(nameNode, this.currentScope);
      if (!this.currentScope.isDefinedLocally(WINDOW_INSTANCE, false)) {
        this.currentScope.addLocal(WINDOW_INSTANCE, typeInJsdoc, false, true);
        return;
      }
      // The externs may contain multiple definitions of window, or they may add
      // properties to the window instance before "var window" is defined.
      // In those cases, we want to pick the definition that has the Window
      // nominal type.
      NominalType maybeWin = typeInJsdoc == null
          ? null : typeInJsdoc.getNominalTypeIfSingletonObj();
      if (maybeWin != null && maybeWin.getName().equals(WINDOW_CLASS)) {
        this.currentScope.addLocal(WINDOW_INSTANCE, typeInJsdoc, false, true);
      }
    }

    private void processQualifiedDefinition(Node qnameNode) {
      Preconditions.checkArgument(qnameNode.isGetProp());
      Preconditions.checkArgument(qnameNode.isQualifiedName());
      Node recv = qnameNode.getFirstChild();
      if (!currentScope.isNamespace(recv)
          && !mayCreateFunctionNamespace(recv)
          && !mayCreateWindowNamespace(recv)) {
        return;
      }
      if (NodeUtil.isNamespaceDecl(qnameNode)) {
        visitObjlitNamespace(qnameNode);
      } else if (NodeUtil.isTypedefDecl(qnameNode)) {
        visitTypedef(qnameNode);
      } else if (NodeUtil.isEnumDecl(qnameNode)) {
        visitEnum(qnameNode);
      } else if (isAliasedNamespaceDefinition(qnameNode)) {
        visitAliasedNamespace(qnameNode);
      } else if (isQualifiedFunctionDefinition(qnameNode)) {
        maybeAddFunctionToNamespace(qnameNode);
      }
    }

    private boolean isAliasedNamespaceDefinition(Node qnameNode) {
      Node rhs = NodeUtil.getRValueOfLValue(qnameNode);
      if (rhs == null || !rhs.isQualifiedName()) {
        return false;
      }
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(qnameNode);
      if (jsdoc == null) {
        return (qnameNode.isFromExterns()
            // When assigning to a namespace prop and the rhs is also a namespace, consider
            // it an alias, even if there is no @const. An ES6-module default export is
            // transpiled to this form.
            || qnameNode.isGetProp() && this.currentScope.isNamespace(qnameNode.getFirstChild()))
            // If the aliased namespace is a variable, do the aliasing at
            // declaration, not at a random assignment later.
            && (qnameNode.getParent().isVar() || !qnameNode.isName())
            && this.currentScope.isNamespace(rhs);
      } else {
        return jsdoc.isConstructorOrInterface() || jsdoc.hasConstAnnotation();
      }
    }

    private boolean isQualifiedFunctionDefinition(Node qnameNode) {
      Preconditions.checkArgument(qnameNode.isGetProp());
      Preconditions.checkArgument(qnameNode.isQualifiedName());
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
      Preconditions.checkState(!currentScope.isNamespace(qname));
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
        globalScope.addNamespaceLit(qnameNode);
        return true;
      }
      return false;
    }

    private void visitObjlitNamespace(Node qnameNode) {
      if (qnameNode.isGetProp()) {
        markAssignNodeAsAnalyzed(qnameNode.getParent());
      }
      if (currentScope.isDefined(qnameNode)) {
        if (qnameNode.isGetProp()
            && !NodeUtil.getRValueOfLValue(qnameNode).isOr()) {
          warnings.add(JSError.make(qnameNode, REDECLARED_PROPERTY,
                  qnameNode.getLastChild().getString(),
                  qnameNode.getFirstChild().getQualifiedName()));
        }
        return;
      }
      currentScope.addNamespaceLit(qnameNode);
      // If the object literal that defines the namespace has properties,
      // some of them may define aliased namespaces. This is rare in hand-written
      // code, but it happens often in code generated by the rewrite of
      // goog.module exports.
      Node maybeObjlit = NodeUtil.getRValueOfLValue(qnameNode);
      if (maybeObjlit.isOr()) {
        maybeObjlit = maybeObjlit.getLastChild();
      }
      Preconditions.checkState(maybeObjlit.isObjectLit(),
          "Expected object literal, found %s", maybeObjlit);
      for (Node propNode : maybeObjlit.children()) {
        if (isAliasedNamespaceDefinition(propNode)) {
          // Pretend that the alias was defined as an assignment to a qname
          Node fakeGetprop =
              IR.getprop(qnameNode.cloneTree(), IR.string(propNode.getString()));
          IR.assign(fakeGetprop, propNode.getFirstChild().cloneTree());
          visitAliasedNamespace(fakeGetprop);
        }
      }
    }

    private void markAssignNodeAsAnalyzed(Node maybeAssign) {
      if (maybeAssign.isAssign()) {
        maybeAssign.putBooleanProp(Node.ANALYZED_DURING_GTI, true);
      } else {
        // No initializer for the property
        Preconditions.checkState(maybeAssign.isExprResult());
      }
    }

    private void visitTypedef(Node qnameNode) {
      Preconditions.checkState(qnameNode.isQualifiedName());
      qnameNode.putBooleanProp(Node.ANALYZED_DURING_GTI, true);
      if (NodeUtil.getRValueOfLValue(qnameNode) != null) {
        warnings.add(JSError.make(qnameNode, CANNOT_INIT_TYPEDEF));
      }
      if (currentScope.isDefined(qnameNode)) {
        return;
      }
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(qnameNode);
      Typedef td = Typedef.make(jsdoc.getTypedefType(), jsdoc.makesStructs());
      currentScope.addTypedef(qnameNode, td);
    }

    private void visitEnum(Node qnameNode) {
      Preconditions.checkState(qnameNode.isQualifiedName());
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
        recordPropertyName(pname, qnameNode);
        propNames.add(pname);
      }
      currentScope.addNamespace(qnameNode,
          EnumType.make(
              commonTypes,
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
          new NTIScope(fn, this.currentScope, collectFormals(fn, fnDoc), commonTypes);
      if (!fn.isFromExterns()) {
        scopes.add(fnScope);
      }
      this.currentScope.addLocalFunDef(internalName, fnScope);
      maybeRecordNominalType(fn, nameNode, fnDoc, isRedeclaration);
    }

    private String createFunctionInternalName(Node fn, Node nameNode) {
      String internalName = null;
      if (nameNode == null || !nameNode.isName()
          || nameNode.getParent().isAssign()) {
        // Anonymous functions, qualified names, and stray assignments
        // (eg, f = function(x) { ... }; ) get gensymed names.
        internalName = ANON_FUN_PREFIX + funNameGen.generateNextName();
        anonFunNames.put(fn, internalName);
      } else if (currentScope.isDefinedLocally(nameNode.getString(), false)) {
        String fnName = nameNode.getString();
        Preconditions.checkState(!fnName.contains("."));
        internalName = ANON_FUN_PREFIX + funNameGen.generateNextName();
        anonFunNames.put(fn, internalName);
      } else {
        // fnNameNode is undefined simple name
        internalName = nameNode.getString();
      }
      return internalName;
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
      Preconditions.checkState(nameNode == null || nameNode.isQualifiedName());
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
          builder.add(varNameGen.getNextName(typeParam));
        }
        ImmutableList<String> typeParameters = builder.build();
        RawNominalType rawType;
        ObjectKind objKind = fnDoc.makesStructs()
            ? ObjectKind.STRUCT : (fnDoc.makesDicts() ? ObjectKind.DICT : ObjectKind.UNRESTRICTED);
        if (fnDoc.isConstructor()) {
          rawType = RawNominalType.makeClass(
              commonTypes, defSite, qname, typeParameters, objKind, fnDoc.isAbstract());
        } else if (fnDoc.usesImplicitMatch()) {
          rawType = RawNominalType.makeStructuralInterface(
              commonTypes, defSite, qname, typeParameters, objKind);
        } else {
          Preconditions.checkState(fnDoc.isInterface());
          rawType = RawNominalType.makeNominalInterface(
              commonTypes, defSite, qname, typeParameters, objKind);
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
            defSite.getParent().getFirstChild().putBooleanProp(Node.ANALYZED_DURING_GTI, true);
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

    private void maybeRecordBuiltinType(
        String name, RawNominalType rawType) {
      switch (name) {
        case "Arguments":
          commonTypes.setArgumentsType(rawType);
          break;
        case "Function":
          commonTypes.setFunctionType(rawType);
          break;
        case "Object": {
          commonTypes.setObjectType(rawType);
          // Create a separate raw type for object literals
          RawNominalType objLitRawType = RawNominalType.makeClass(
              commonTypes, rawType.getDefSite(), JSTypes.OBJLIT_CLASS_NAME,
              ImmutableList.<String>of(), ObjectKind.UNRESTRICTED, false);
          objLitRawType.addSuperClass(rawType.getAsNominalType());
          commonTypes.setLiteralObjNominalType(objLitRawType);
          break;
        }
        case "Number":
          commonTypes.setNumberInstance(rawType.getInstanceAsJSType());
          break;
        case "String":
          commonTypes.setStringInstance(rawType.getInstanceAsJSType());
          break;
        case "Boolean":
          commonTypes.setBooleanInstance(rawType.getInstanceAsJSType());
          break;
        case "RegExp":
          commonTypes.setRegexpInstance(rawType.getInstanceAsJSType());
          break;
        case "Array":
          commonTypes.setArrayType(rawType);
          break;
        case "IObject":
          commonTypes.setIObjectType(rawType);
          break;
      }
    }

    private void visitAliasedNamespace(Node lhs) {
      if (this.currentScope.isDefined(lhs)) {
        return;
      }
      Node rhs = NodeUtil.getRValueOfLValue(lhs);
      QualifiedName rhsQname = QualifiedName.fromNode(rhs);
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(lhs);

      if (jsdoc != null && jsdoc.isConstructorOrInterface()) {
        RawNominalType rawType = this.currentScope.getNominalType(rhsQname);
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
      Namespace ns = this.currentScope.getNamespace(rhsQname);
      if (ns != null) {
        lhs.getParent().putBooleanProp(Node.ANALYZED_DURING_GTI, true);
        this.currentScope.addNamespace(lhs, ns);
      }
    }

    private void maybeAddFunctionToNamespace(Node funQname) {
      Namespace ns = currentScope.getNamespace(QualifiedName.fromNode(funQname.getFirstChild()));
      String internalName = getFunInternalName(funQname.getParent().getLastChild());
      NTIScope s = currentScope.getScope(internalName);
      QualifiedName pname = new QualifiedName(funQname.getLastChild().getString());
      if (!ns.isDefined(pname)) {
        ns.addNamespace(pname,
            new FunctionNamespace(commonTypes, funQname.getQualifiedName(), s, funQname));
      }
    }

    private void visitNewCtorDefinedByCall(Node qnameNode) {
      Preconditions.checkState(qnameNode.isName() || qnameNode.isGetProp());
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(qnameNode);
      Node rhs = NodeUtil.getRValueOfLValue(qnameNode);
      maybeRecordNominalType(rhs, qnameNode, jsdoc, false);
    }

    private void visitNewCtorWithoutFunctionLiteral(Node qnameNode) {
      Preconditions.checkState(qnameNode.isName() || qnameNode.isGetProp());
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(qnameNode);
      maybeRecordNominalType(qnameNode, qnameNode, jsdoc, false);
    }
  }

  private class ProcessScope extends AbstractShallowCallback {
    private final NTIScope currentScope;
    private Set<Node> lendsObjlits = new LinkedHashSet<>();

    ProcessScope(NTIScope currentScope) {
      this.currentScope = currentScope;
    }

    void finishProcessingScope() {
      for (Node objlit : lendsObjlits) {
        processLendsNode(objlit);
      }
      lendsObjlits = null;
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
      QualifiedName lendsQname = QualifiedName.fromQualifiedString(lendsName);
      if (currentScope.isNamespace(lendsQname)) {
        processLendsToNamespace(lendsQname, lendsName, objlit);
      } else {
        RawNominalType rawType = checkValidLendsToPrototypeAndGetClass(
            lendsQname, lendsName, objlit);
        if (rawType != null) {
          for (Node prop : objlit.children()) {
            String pname =  NodeUtil.getObjectLitKeyName(prop);
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
        JSType propDeclType = declaredObjLitProps.get(prop);
        if (propDeclType != null) {
          borrowerNamespace.addProperty(pname, prop, propDeclType, false);
        } else {
          JSType t = simpleInferExprType(prop.getFirstChild());
          if (t == null) {
            t = commonTypes.UNKNOWN;
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
                  && !isClassPropertyDeclaration(
                      parent.getFirstChild(), currentScope))) {
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
          } else if (currentScope.isOuterVarEarly(name)) {
            currentScope.addOuterVar(name);
          } else if (// Typedef variables can't be referenced in the source.
              currentScope.getTypedef(name) != null
              || (!name.equals(currentScope.getName())
                  && !currentScope.isDefinedLocally(name, false))) {
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
            recordPropertyName(n.getLastChild().getString(), n);
          }
          if (n.getFirstChild().isName()
              && n.getFirstChild().getString().startsWith("$jscomp$destructuring$")) {
            recordPropertyName(n.getLastChild().getString(), n);
          }
          if (parent.isExprResult() && n.isQualifiedName()) {
            visitPropertyDeclaration(n);
          }
          break;
        case ASSIGN: {
          Node lvalue = n.getFirstChild();
          if (lvalue.isGetProp() && lvalue.isQualifiedName()) {
            visitPropertyDeclaration(lvalue);
          }
          break;
        }
        case CAST:
          castTypes.put(n,
              getDeclaredTypeOfNode(n.getJSDocInfo(), currentScope));
          break;
        case OBJECTLIT:
          visitObjectLit(n, parent);
          break;
        case CALL:
          visitCall(n);
          break;
        default:
          break;
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
          this.currentScope.addLocal(
              name, commonTypes.UNKNOWN, false, nameNode.isFromExterns());
        }
        return;
      }
      Node initializer = nameNode.getFirstChild();
      if (initializer != null && initializer.isFunction()) {
        return;
      }
      if (parent.isCatch()) {
        this.currentScope.addLocal(name, commonTypes.UNKNOWN, false, false);
      } else {
        boolean isConst = isConst(nameNode);
        JSType declType = getVarTypeFromAnnotation(nameNode, this.currentScope);
        if (declType == null) {
          declType = mayInferFromRhsIfConst(nameNode);
        }
        this.currentScope.addLocal(name, declType, isConst, nameNode.isFromExterns());
      }
    }

    private void visitObjectLit(Node objLitNode, Node parent) {
      JSDocInfo jsdoc = objLitNode.getJSDocInfo();
      if (jsdoc != null && jsdoc.getLendsName() != null) {
        lendsObjlits.add(objLitNode);
      }
      Node maybeLvalue = parent.isAssign() ? parent.getFirstChild() : parent;
      if (NodeUtil.isNamespaceDecl(maybeLvalue)
          && currentScope.isNamespace(maybeLvalue)) {
        for (Node prop : objLitNode.children()) {
          recordPropertyName(prop.getString(), prop);
          visitNamespacePropertyDeclaration(prop, maybeLvalue, prop.getString());
        }
      } else if (!NodeUtil.isEnumDecl(maybeLvalue)
          && !NodeUtil.isPrototypeAssignment(maybeLvalue)) {
        for (Node prop : objLitNode.children()) {
          recordPropertyName(prop.getString(), prop);
          if (prop.getJSDocInfo() != null) {
            declaredObjLitProps.put(prop,
                getDeclaredTypeOfNode(
                    prop.getJSDocInfo(), currentScope));
          }
          if (isAnnotatedAsConst(prop)) {
            warnings.add(JSError.make(prop, MISPLACED_CONST_ANNOTATION));
          }
        }
      }
    }

    private void visitCall(Node call) {
      String className = convention.getSingletonGetterClassName(call);
      if (className == null) {
        return;
      }
      QualifiedName qname = QualifiedName.fromQualifiedString(className);
      RawNominalType rawType = this.currentScope.getNominalType(qname);
      if (rawType != null) {
        JSType instanceType = rawType.getInstanceAsJSType();
        FunctionType getInstanceFunType =
            (new FunctionTypeBuilder(commonTypes)).addRetType(instanceType).buildFunction();
        JSType getInstanceType = commonTypes.fromFunctionType(getInstanceFunType);
        convention.applySingletonGetterNew(rawType, getInstanceType, instanceType);
      }
    }

    private void visitPropertyDeclaration(Node getProp) {
      recordPropertyName(getProp.getLastChild().getString(), getProp);
      // Class property
      if (isClassPropertyDeclaration(getProp, currentScope)) {
        visitClassPropertyDeclaration(getProp);
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
      else if (isStaticCtorProp(getProp, currentScope)) {
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

    private boolean isStaticCtorProp(Node getProp, NTIScope s) {
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

    /** Compute the declared type for a given scope. */
    private NTIScope visitFunctionLate(Node fn, RawNominalType ownerType) {
      Preconditions.checkArgument(fn.isFunction());
      String internalName = getFunInternalName(fn);
      NTIScope fnScope = currentScope.getScope(internalName);
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(fn);
      DeclaredFunctionType declFunType = fnScope.getDeclaredFunctionType();
      if (declFunType == null) {
        declFunType = computeFnDeclaredType(
            jsdoc, internalName, fn, ownerType, currentScope);
        fnScope.setDeclaredType(declFunType);
      }
      return fnScope;
    }

    // Used when declaring constructor and namespace properties. Prototype property
    // declarations are similar, but different enough that they didn't neatly fit
    // in this method (eg, redeclaration warnings are stricter).
    PropertyType getPropTypeHelper(
        JSDocInfo jsdoc, Node initializer, RawNominalType thisType) {
      PropertyType result = new PropertyType();
      DeclaredFunctionType dft = null;
      if (initializer != null && initializer.isFunction()) {
        dft = visitFunctionLate(initializer, thisType).getDeclaredFunctionType();
      }
      if (jsdoc != null && jsdoc.hasType()) {
        result.declType = getDeclaredTypeOfNode(jsdoc, currentScope);
      } else if (initializer != null && initializer.isFunction()) {
        JSType funType = commonTypes.fromFunctionType(dft.toFunctionType());
        if ((jsdoc != null && jsdoc.containsFunctionDeclaration())
            || NodeUtil.functionHasInlineJsdocs(initializer)) {
          result.declType = funType;
        } else {
          result.inferredFunType = funType;
        }
      }
      return result;
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
      if (initializer != null && initializer.isFunction()) {
        parent.putBooleanProp(Node.ANALYZED_DURING_GTI, true);
      }
      // We only add properties to the prototype of a class if the
      // property creations are in the same scope as the constructor
      if (!currentScope.isDefined(ctorNameNode)) {
        warnings.add(JSError.make(getProp, CTOR_IN_DIFFERENT_SCOPE));
      }
      mayWarnAboutInterfacePropInit(rawType, initializer);
      mayAddPropToPrototype(
          rawType, getProp.getLastChild().getString(), getProp, initializer);
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
      Preconditions.checkArgument(getProp.isGetProp());
      Node protoObjNode = getProp.getParent().getLastChild();
      if (!protoObjNode.isObjectLit()) {
        return;
      }
      Node ctorNameNode = NodeUtil.getPrototypeClassName(getProp);
      QualifiedName ctorQname = QualifiedName.fromNode(ctorNameNode);
      RawNominalType rawType = currentScope.getNominalType(ctorQname);
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
      Preconditions.checkArgument(getProp.isGetProp());
      mayVisitWeirdCtorDefinition(getProp);
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
      JSType propDeclType = getDeclaredTypeOfNode(jsdoc, currentScope);
      boolean isConst = isConst(getProp);
      if (propDeclType != null || isConst) {
        JSType previousPropType = classType.getCtorPropDeclaredType(pname);
        if (classType.hasCtorProp(pname)
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
        classType.addUndeclaredCtorProperty(pname, getProp);
      }
    }

    private void mayVisitWeirdCtorDefinition(Node getProp) {
      if (isCtorDefinedByCall(getProp)) {
        computeFnDeclaredType(
            NodeUtil.getBestJSDocInfo(getProp),
            getProp.getQualifiedName(),
            getProp.getNext(), null, this.currentScope);
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
      Preconditions.checkArgument(getProp.isGetProp());
      mayVisitWeirdCtorDefinition(getProp);
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
      Preconditions.checkArgument(declNode.isGetProp() || declNode.isStringKey()
          || declNode.isGetterDef() || declNode.isSetterDef(),
          declNode);
      Preconditions.checkArgument(currentScope.isNamespace(recv));
      if (declNode.isGetterDef()) {
        pname = JSType.createGetterPropName(pname);
      } else if (declNode.isSetterDef()) {
        pname = JSType.createSetterPropName(pname);
      }
      // Named types have already been crawled in CollectNamedTypes
      if (declNode.isStringKey() && currentScope.isNamespace(declNode.getFirstChild())) {
        return;
      }
      EnumType et = currentScope.getEnum(QualifiedName.fromNode(recv));
      // If there is a reassignment to one of the enum's members, don't consider
      // that a definition of a new property.
      if (et != null && et.enumLiteralHasKey(pname)) {
        return;
      }

      Namespace ns = currentScope.getNamespace(QualifiedName.fromNode(recv));
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(declNode);
      Node initializer = NodeUtil.getRValueOfLValue(declNode);
      PropertyType pt = getPropTypeHelper(jsdoc, initializer, null);
      JSType propDeclType = pt.declType;
      JSType propInferredFunType = pt.inferredFunType;
      boolean isConst = isConst(declNode);
      if (propDeclType != null || isConst) {
        JSType previousPropType = ns.getPropDeclaredType(pname);
        if (ns.hasSubnamespace(new QualifiedName(pname))
            || (ns.hasProp(pname)
                && previousPropType != null
                && !suppressDupPropWarning(jsdoc, propDeclType, previousPropType))) {
          warnings.add(JSError.make(
              declNode, REDECLARED_PROPERTY, pname, "namespace " + ns));
          declNode.getParent().putBooleanProp(Node.ANALYZED_DURING_GTI, true);
          return;
        }
        if (propDeclType == null) {
          propDeclType = mayInferFromRhsIfConst(declNode);
        }
        ns.addProperty(pname, declNode, propDeclType, isConst);
        declNode.putBooleanProp(Node.ANALYZED_DURING_GTI, true);
        if (declNode.isGetProp() && isConst) {
          declNode.putBooleanProp(Node.CONSTANT_PROPERTY_DEF, true);
        }
      } else if (propInferredFunType != null) {
        ns.addUndeclaredProperty(pname, declNode, propInferredFunType, false);
      } else {
        // Try to infer the prop type, but don't say that the prop is declared.
        JSType t = initializer == null
            ? null : simpleInferExprType(initializer);
        if (t == null) {
          t = commonTypes.UNKNOWN;
        }
        ns.addUndeclaredProperty(pname, declNode, t, false);
      }
    }

    private void visitClassPropertyDeclaration(Node getProp) {
      Preconditions.checkArgument(getProp.isGetProp());
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
      String pname = getProp.getLastChild().getString();
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(getProp);
      PropertyType pt = getPropTypeHelper(jsdoc, initializer, rawType);
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
          rawType.addClassProperty(pname, getProp, propDeclType, isConst);
        }
        if (isConst) {
          getProp.putBooleanProp(Node.CONSTANT_PROPERTY_DEF, true);
        }
      } else if (mayAddPropToType(getProp, rawType)) {
        if (propInferredFunType != null) {
          rawType.addUndeclaredClassProperty(pname, propInferredFunType, getProp);
        } else {
          rawType.addUndeclaredClassProperty(pname, commonTypes.UNKNOWN, getProp);
        }

      }
      // Only add the definition node if the property is not already defined.
      if (!propertyDefs.contains(rawType, pname)) {
        propertyDefs.put(rawType, pname,
            new PropertyDef(getProp, null, null));
      }
    }

    private void visitOtherPropertyDeclaration(Node getProp) {
      Preconditions.checkArgument(getProp.isGetProp());
      Preconditions.checkArgument(getProp.isQualifiedName());
      if (isCtorWithoutFunctionLiteral(getProp)) {
        computeFnDeclaredType(
            NodeUtil.getBestJSDocInfo(getProp),
            getProp.getQualifiedName(),
            getProp, null, this.currentScope);
        return;
      }
      if (isAnnotatedAsConst(getProp)) {
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
      JSType recvType = simpleInferExprType(recv);
      if (recvType == null) {
        return;
      }
      recvType = recvType.removeType(commonTypes.NULL);
      NominalType nt = recvType.getNominalTypeIfSingletonObj();
      // Don't add stray properties to Object.
      if (nt == null || nt.equals(commonTypes.getObjectType())) {
        return;
      }
      RawNominalType rawType = nt.getRawNominalType();
      String pname = getProp.getLastChild().getString();
      JSType declType = getDeclaredTypeOfNode(
          NodeUtil.getBestJSDocInfo(getProp), currentScope);
      if (declType != null) {
        declType = declType.substituteGenericsWithUnknown();
        if (mayWarnAboutExistingProp(rawType, pname, getProp, declType)) {
          return;
        }
        rawType.addPropertyWhichMayNotBeOnAllInstances(pname, declType);
      } else if (!rawType.mayHaveProp(pname)) {
        rawType.addPropertyWhichMayNotBeOnAllInstances(pname, null);
      }
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

    // If a @const doesn't have a declared type, we use the initializer to
    // infer a type.
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
      JSType rhsType = simpleInferExprType(rhs);
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
      Preconditions.checkArgument(
          constExpr.isQualifiedName() || constExpr.isStringKey(),
          constExpr);
      return constExpr.isQualifiedName()
          ? constExpr.getQualifiedName() : constExpr.getString();
    }

    private FunctionType simpleInferFunctionType(Node n) {
      if (n.isQualifiedName()) {
        Declaration decl = currentScope.getDeclaration(QualifiedName.fromNode(n), false);
        if (decl == null) {
          JSType t = simpleInferExprType(n);
          if (t != null) {
            return t.getFunTypeIfSingletonObj();
          }
        } else if (decl.getNominal() != null) {
          return decl.getNominal().getConstructorFunction();
        } else if (decl.getFunctionScope() != null) {
          DeclaredFunctionType funType = decl.getFunctionScope().getDeclaredFunctionType();
          if (funType != null) {
            return funType.toFunctionType();
          }
        } else if (decl.getNamespace() != null) {
          Namespace ns = decl.getNamespace();
          if (ns instanceof FunctionNamespace) {
            DeclaredFunctionType funType =
                ((FunctionNamespace) ns).getScope().getDeclaredFunctionType();
            return Preconditions.checkNotNull(funType).toFunctionType();
          }
        } else if (decl.getTypeOfSimpleDecl() != null) {
          return decl.getTypeOfSimpleDecl().getFunTypeIfSingletonObj();
        }
      }
      JSType t = simpleInferExprType(n);
      return t == null ? null : t.getFunTypeIfSingletonObj();
    }

    private JSType simpleInferCallNewType(Node n) {
      Node callee = n.getFirstChild();
      // We special-case the function goog.getMsg, which is used by the
      // compiler for i18n.
      if (callee.matchesQualifiedName("goog.getMsg")) {
        return commonTypes.STRING;
      }
      FunctionType funType = simpleInferFunctionType(callee);
      if (funType == null) {
        return null;
      }
      if (funType.isGeneric()) {
        ImmutableList.Builder<JSType> argTypes = ImmutableList.builder();
        for (Node argNode = n.getSecondChild();
             argNode != null;
             argNode = argNode.getNext()) {
          JSType t = simpleInferExprType(argNode);
          if (t == null) {
            return null;
          }
          argTypes.add(t);
        }
        funType = funType.instantiateGenericsFromArgumentTypes(argTypes.build());
        if (funType == null) {
          return null;
        }
      }
      JSType retType = n.isNew() ? funType.getThisType() : funType.getReturnType();
      return retType;
    }

    private JSType simpleInferExprType(Node n) {
      switch (n.getToken()) {
        case REGEXP:
          return commonTypes.getRegexpType();
        case CAST:
          return castTypes.get(n);
        case ARRAYLIT: {
          if (!n.hasChildren()) {
            return commonTypes.getArrayInstance();
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
        case TRUE:
          return commonTypes.TRUE_TYPE;
        case FALSE:
          return commonTypes.FALSE_TYPE;
        case THIS:
          return this.currentScope.getDeclaredTypeOf("this");
        case NAME:
          return simpleInferDeclaration(
              this.currentScope.getDeclaration(n.getString(), false));
        case OBJECTLIT: {
          JSType objLitType = commonTypes.getEmptyObjectLiteral();
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
        case GETPROP:
          return simpleInferPropAccessType(
              n.getFirstChild(), n.getLastChild().getString());
        case GETELEM:
          return simpleInferGetelemType(n);
        case COMMA:
        case ASSIGN:
          return simpleInferExprType(n.getLastChild());
        case CALL:
        case NEW:
          return simpleInferCallNewType(n);
        case AND:
        case OR:
          return simpleInferAndOrType(n);
        case HOOK: {
          JSType lhs = simpleInferExprType(n.getSecondChild());
          JSType rhs = simpleInferExprType(n.getLastChild());
          return lhs == null || rhs == null ? null : JSType.join(lhs, rhs);
        }
        default:
          switch (NodeUtil.getKnownValueType(n)) {
            case NULL:
              return commonTypes.NULL;
            case VOID:
              return commonTypes.UNDEFINED;
            case NUMBER:
              return commonTypes.NUMBER;
            case STRING:
              return commonTypes.STRING;
            case BOOLEAN:
              return commonTypes.BOOLEAN;
            default:
              return null;
          }
      }
    }

    private JSType simpleInferPrototypeProperty(Node recv, String pname) {
      QualifiedName recvQname = QualifiedName.fromNode(recv);
      Declaration decl = this.currentScope.getDeclaration(recvQname, false);
      if (decl != null) {
        Namespace ns = decl.getNamespace();
        if (ns instanceof RawNominalType) {
          return ((RawNominalType) ns).getProtoPropDeclaredType(pname);
        }
      }
      return null;
    }

    private JSType simpleInferPropAccessType(Node recv, String pname) {
      if (recv.isGetProp() && recv.getLastChild().getString().equals("prototype")) {
        return simpleInferPrototypeProperty(recv.getFirstChild(), pname);
      }
      QualifiedName propQname = new QualifiedName(pname);
      if (recv.isQualifiedName()) {
        QualifiedName recvQname = QualifiedName.fromNode(recv);
        Declaration decl = this.currentScope.getDeclaration(recvQname, false);
        if (decl != null) {
          EnumType et = decl.getEnum();
          if (et != null && et.enumLiteralHasKey(pname)) {
            return et.getEnumeratedType();
          }
          Namespace ns = decl.getNamespace();
          if (ns != null) {
            return simpleInferDeclaration(ns.getDeclaration(propQname));
          }
        }
      }
      JSType recvType = simpleInferExprType(recv);
      if (recvType != null && recvType.isScalar()) {
        recvType = recvType.autobox();
      }
      if (recvType != null && recvType.mayHaveProp(propQname)) {
        return recvType.getProp(propQname);
      }
      return null;
    }

    private JSType simpleInferGetelemType(Node n) {
      Preconditions.checkState(n.isGetElem());
      Node recv = n.getFirstChild();
      Node propNode = n.getLastChild();
      // As in NewTypeInference.java, we try to treat bracket accesses with a
      // string literal as precisely as dot accesses.
      if (propNode.isString()) {
        JSType propType = simpleInferPropAccessType(recv, propNode.getString());
        if (propType != null) {
          return propType;
        }
      }
      JSType recvType = simpleInferExprType(recv);
      if (recvType != null) {
        JSType indexType = recvType.getIndexType();
        if (indexType != null) {
          JSType propType = simpleInferExprType(propNode);
          if (propType != null && propType.isSubtypeOf(indexType)) {
            return recvType.getIndexedType();
          }
        }
      }
      return null;
    }

    private JSType simpleInferDeclaration(Declaration decl) {
      if (decl == null) {
        return null;
      }
      // Namespaces (literals, enums, constructors) get populated during
      // ProcessScope, so it's generally NOT safe to convert them to jstypes
      // until after ProcessScope is done.
      if (decl.getNamespace() != null) {
        return null;
      }
      if (decl.getTypeOfSimpleDecl() != null) {
        return decl.getTypeOfSimpleDecl();
      }
      NTIScope funScope = (NTIScope) decl.getFunctionScope();
      if (funScope != null) {
        DeclaredFunctionType dft = funScope.getDeclaredFunctionType();
        if (dft == null) {
          return null;
        }
        return commonTypes.fromFunctionType(dft.toFunctionType());
      }
      return null;
    }

    private JSType simpleInferAndOrType(Node n) {
      Preconditions.checkState(n.isOr() || n.isAnd());
      JSType lhs = simpleInferExprType(n.getFirstChild());
      if (lhs == null) {
        return null;
      }
      JSType rhs = simpleInferExprType(n.getSecondChild());
      if (rhs == null) {
        return null;
      }
      if (lhs.equals(rhs)) {
        return lhs;
      }
      if (n.isAnd()) {
        return JSType.join(lhs.specialize(commonTypes.FALSY), rhs);
      }
      return JSType.join(lhs.specialize(commonTypes.TRUTHY), rhs);
    }

    private boolean mayAddPropToType(Node getProp, RawNominalType rawType) {
      if (!rawType.isStruct()) {
        return true;
      }
      Node parent = getProp.getParent();
      return ((parent.isAssign() && getProp == parent.getFirstChild()) || parent.isExprResult())
          && currentScope.isConstructor();
    }

    private boolean mayWarnAboutExistingProp(RawNominalType classType,
        String pname, Node propCreationNode, JSType typeInJsdoc) {
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(propCreationNode);
      JSType previousPropType = classType.getInstancePropDeclaredType(pname);
      if (classType.mayHaveOwnProp(pname)
          && previousPropType != null
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
      Preconditions.checkArgument(
          declNode.isFunction() || declNode.isQualifiedName() || declNode.isCall());

      // For an unannotated function, check if we can grab a type signature for
      // it from the surrounding code where it appears.
      if (fnDoc == null && !NodeUtil.functionHasInlineJsdocs(declNode)) {
        DeclaredFunctionType t = getDeclaredFunctionTypeFromContext(
            functionName, declNode, parentScope);
        if (t != null) {
          return t;
        }
      }
      // TODO(dimvar): warn if multiple jsdocs for a fun
      RawNominalType ctorType = nominaltypesByNode.get(declNode);
      FunctionAndSlotType result = typeParser.getFunctionType(
          fnDoc, functionName, declNode, ctorType, ownerType, parentScope);
      Node qnameNode;
      if (declNode.isQualifiedName()) {
        qnameNode = declNode;
      } else if (declNode.isFunction()) {
        qnameNode = NodeUtil.getNameNode(declNode);
      } else {
        qnameNode = NodeUtil.getBestLValue(declNode);
      }
      if (result.slotType != null && qnameNode != null && qnameNode.isName()) {
        parentScope.addLocal(qnameNode.getString(),
            result.slotType, false, qnameNode.isFromExterns());
      }
      if (ctorType != null) {
        FunctionType ft = result.functionType.toFunctionType();
        ctorType.setCtorFunction(ft);
        if (ctorType.isBuiltinObject()) {
          commonTypes.getLiteralObjNominalType().getRawNominalType().setCtorFunction(ft);
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

    // We only return a non-null result if the arity of declNode matches the
    // arity we get from declaredTypeAsJSType.
    private DeclaredFunctionType computeFnDeclaredTypeFromCallee(
        Node declNode, JSType declaredTypeAsJSType) {
      Preconditions.checkArgument(declNode.isFunction());
      Preconditions.checkArgument(declNode.getParent().isCall());
      Preconditions.checkNotNull(declaredTypeAsJSType);

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
      int numFormals = declNode.getSecondChild().getChildCount();
      int reqArity = declType.getRequiredArity();
      int optArity = declType.getOptionalArity();
      boolean hasRestFormals = declType.hasRestFormals();
      if (reqArity == optArity && !hasRestFormals) {
        return numFormals == reqArity ? declType : null;
      }
      if ((numFormals == optArity && !hasRestFormals)
          || (numFormals == (optArity + 1) && hasRestFormals)) {
        return declType;
      }
      return null;
    }

    // Returns null if it can't find a suitable type in the context
    private DeclaredFunctionType getDeclaredFunctionTypeFromContext(
        String functionName, Node declNode, NTIScope parentScope) {
      Node parent = declNode.getParent();
      Node maybeBind = parent.isCall() ? parent.getFirstChild() : parent;

      // The function literal is used with .bind or goog.bind
      if (NodeUtil.isFunctionBind(maybeBind) && !NodeUtil.isGoogPartial(maybeBind)) {
        Node call = maybeBind.getParent();
        Bind bindComponents = convention.describeFunctionBind(call, true, false);
        JSType recvType = bindComponents.thisValue == null
            ? null : simpleInferExprType(bindComponents.thisValue);
        if (recvType == null) {
          return null;
        }
        // Use typeParser for the formals, and only add the receiver type here.
        DeclaredFunctionType allButRecvType = typeParser.getFunctionType(
            null, functionName, declNode, null, null, parentScope).functionType;
        return allButRecvType.withReceiverType(recvType);
      }

      // The function literal is an argument at a call
      if (parent.isCall() && declNode != parent.getFirstChild()) {
        DeclaredFunctionType calleeDeclType = getDeclaredFunctionTypeOfCalleeIfAny(
            parent.getFirstChild(), parentScope);
        if (calleeDeclType != null && !calleeDeclType.isGeneric()) {
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
          pname = JSType.createGetterPropName(pname);
        } else if (defSite.isSetterDef()) {
          pname = JSType.createSetterPropName(pname);
        }
      } else if (jsdoc != null && jsdoc.containsFunctionDeclaration()
          // This can happen with stray jsdocs in an object literal
          && !defSite.isStringKey()) {
        // We're parsing a function declaration without a function initializer
        methodType = computeFnDeclaredType(jsdoc, pname, defSite, rawType, currentScope);
      }

      // Find the declared type of the property.
      if (jsdoc != null && jsdoc.hasType()) {
        propDeclType = typeParser.getDeclaredTypeOfNode(jsdoc, rawType, currentScope);
      } else if (methodType != null) {
        propDeclType = commonTypes.fromFunctionType(methodType.toFunctionType());
      }
      if (defSite.isGetterDef()) {
        FunctionType ft = propDeclType.getFunTypeIfSingletonObj();
        if (ft != null) {
          propDeclType = ft.getReturnType();
        }
      }
      propertyDefs.put(rawType, pname, new PropertyDef(defSite, methodType, methodScope));

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
        rawType.addUndeclaredProtoProperty(pname, defSite);
      }
    }

    // TODO(dimvar): This method is used to avoid a spurious warning in ES6
    // externs, where a prototype property is declared using GETELEM.
    // We'll remove this when we properly handle ES6.
    private RawNominalType maybeGetOwnerType(Node funNode, Node parent) {
      Preconditions.checkArgument(funNode.isFunction());
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
      if (jsdoc != null
          && jsdoc.hasType() && !jsdoc.containsFunctionDeclaration()) {
        return false;
      }
      return this.currentScope.isNamespace(getProp)
          || NodeUtil.isTypedefDecl(getProp);
    }
  }

  private JSType getDeclaredTypeOfNode(JSDocInfo jsdoc, NTIScope s) {
    return typeParser.getDeclaredTypeOfNode(jsdoc, null, s);
  }

  private JSType getVarTypeFromAnnotation(Node nameNode, NTIScope currentScope) {
    Preconditions.checkArgument(nameNode.getParent().isVar());
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

  private DeclaredFunctionType getDeclaredFunctionTypeOfCalleeIfAny(
      Node fn, NTIScope currentScope) {
    Preconditions.checkArgument(fn.getParent().isCall());
    if (fn.isThis() || (!fn.isFunction() && !fn.isQualifiedName())) {
      return null;
    }
    if (fn.isFunction()) {
      return currentScope.getScope(getFunInternalName(fn)).getDeclaredFunctionType();
    }
    Preconditions.checkState(fn.isQualifiedName());
    Declaration decl = currentScope.getDeclaration(QualifiedName.fromNode(fn), false);
    if (decl == null) {
      return null;
    }
    if (decl.getFunctionScope() != null) {
      return decl.getFunctionScope().getDeclaredFunctionType();
    }
    if (decl.getTypeOfSimpleDecl() != null) {
      FunctionType funType = decl.getTypeOfSimpleDecl().getFunType();
      if (funType != null && !funType.isGeneric()) {
        return funType.toDeclaredFunctionType();
      }
    }
    return null;
  }

  private static boolean isClassPropertyDeclaration(Node n, NTIScope s) {
    Node parent = n.getParent();
    return n.isGetProp()
        && n.getFirstChild().isThis()
        && ((parent.isAssign() && parent.getFirstChild().equals(n)) || parent.isExprResult())
        && (s.isConstructor() || s.isPrototypeMethod());
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
    if (NodeUtil.isExprAssign(n)
        && isPrototypeProperty(n.getFirstFirstChild())) {
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
        || defSite.isGetterDef() || defSite.isSetterDef()) {
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
            && NodeUtil.isConstantByConvention(
                this.convention, fromDefsiteToName(defSite)));
  }

  private static boolean isCtorDefinedByCall(Node qnameNode) {
    if (!qnameNode.isName() && !qnameNode.isGetProp()) {
      return false;
    }
    JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(qnameNode);
    Node rhs = NodeUtil.getRValueOfLValue(qnameNode);
    return jsdoc != null && jsdoc.isConstructor() && rhs != null && rhs.isCall();
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
      Preconditions.checkNotNull(defSite);
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

  @Override
  public TypeI createTypeFromCommentNode(Node n) {
    return typeParser.getTypeOfCommentNode(n, null, globalScope);
  }

  @Override
  public JSType getNativeFunctionType(JSTypeNative typeId) {
    return getNativeType(typeId);
  }

  @Override
  public JSType getNativeObjectType(JSTypeNative typeId) {
    return getNativeType(typeId);
  }

  @Override
  public JSType getNativeType(JSTypeNative typeId) {
    return this.commonTypes.getNativeType(typeId);
  }

  @Override
  public String getReadableTypeName(Node n) {
    // TODO(aravindpg): could implement in a more sophisticated way, following the
    // implementation in JSTypeRegistry.
    if (n.getTypeI() == null) {
      return "<node (" + compiler.toSource(n) + ")>";
    }
    return n.getTypeI().getDisplayName();
  }

  @Override
  public JSType getType(String typeName) {
    // Primitives are not present in the global scope, so hardcode them
    switch (typeName) {
      case "boolean":
        return commonTypes.BOOLEAN;
      case "number":
        return commonTypes.NUMBER;
      case "string":
        return commonTypes.STRING;
      case "null":
        return commonTypes.NULL;
      case "undefined":
      case "void":
        return commonTypes.UNDEFINED;
      default:
        return globalScope.getType(typeName);
    }
  }

  @Override
  public TypeI createUnionType(List<? extends TypeI> members) {
    Preconditions.checkArgument(!members.isEmpty(), "Cannot create union type with no members");
    JSType result = commonTypes.BOTTOM;
    for (TypeI t : members) {
      result = JSType.join(result, (JSType) t);
    }
    return result;
  }

  @Override
  public TypeI evaluateTypeExpressionInGlobalScope(JSTypeExpression expr) {
    return createTypeFromCommentNode(expr.getRoot());
  }
}
