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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.CodingConvention.AssertionFunctionSpec;
import com.google.javascript.jscomp.CodingConvention.Bind;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.jscomp.newtypes.DeclaredFunctionType;
import com.google.javascript.jscomp.newtypes.FunctionType;
import com.google.javascript.jscomp.newtypes.FunctionTypeBuilder;
import com.google.javascript.jscomp.newtypes.JSType;
import com.google.javascript.jscomp.newtypes.JSTypes;
import com.google.javascript.jscomp.newtypes.QualifiedName;
import com.google.javascript.jscomp.newtypes.TypeEnv;
import com.google.javascript.jscomp.newtypes.UniqueNameGenerator;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TypeI;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * New type inference algorithm.
 *
 * Main differences from the old type checker:
 * - Infers types for unannotated functions
 * - Stricter with missing-property warnings
 * - Stricter when checking the operands of primitive operators
 * - Requires constants to be typed
 * - Tries to warn about misplaced annotations, rather than silently ignore them
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 *
 */
final class NewTypeInference implements CompilerPass {

  static final DiagnosticType MISTYPED_ASSIGN_RHS = DiagnosticType.warning(
      "JSC_NTI_MISTYPED_ASSIGN_RHS",
      "The right side in the assignment is not a subtype of the left side.\n"
      + "left side  : {0}\n"
      + "right side : {1}\n");

  static final DiagnosticType INVALID_OPERAND_TYPE = DiagnosticType.warning(
      "JSC_NTI_INVALID_OPERAND_TYPE",
      "Invalid type(s) for operator {0}.\n"
      + "expected : {1}\n"
      + "found    : {2}\n");

  static final DiagnosticType RETURN_NONDECLARED_TYPE = DiagnosticType.warning(
      "JSC_NTI_RETURN_NONDECLARED_TYPE",
      "Returned type does not match declared return type.\n" +
      "declared : {0}\n" +
      "found    : {1}\n");

  static final DiagnosticType INVALID_INFERRED_RETURN_TYPE =
      DiagnosticType.warning(
          "JSC_NTI_INVALID_INFERRED_RETURN_TYPE",
          "Function called in context that expects incompatible type.\n" +
          "expected : {0}\n" +
          "found    : {1}\n");

  static final DiagnosticType INVALID_ARGUMENT_TYPE = DiagnosticType.warning(
      "JSC_NTI_INVALID_ARGUMENT_TYPE",
      "Invalid type for parameter {0} of function {1}.\n" +
      "expected : {2}\n" +
      "found    : {3}\n");

  static final DiagnosticType CROSS_SCOPE_GOTCHA = DiagnosticType.warning(
      "JSC_NTI_CROSS_SCOPE_GOTCHA",
      "Variable {0} typed inconsistently across scopes.\n" +
      "In outer scope : {1}\n" +
      "In inner scope : {2}\n");

  static final DiagnosticType POSSIBLY_INEXISTENT_PROPERTY =
      DiagnosticType.warning(
          "JSC_NTI_POSSIBLY_INEXISTENT_PROPERTY",
          "Property {0} may not be present on {1}.");

  static final DiagnosticType PROPERTY_ACCESS_ON_NONOBJECT =
      DiagnosticType.warning(
          "JSC_NTI_PROPERTY_ACCESS_ON_NONOBJECT",
          "Cannot access property {0} of non-object type {1}.");

  static final DiagnosticType NOT_UNIQUE_INSTANTIATION =
      DiagnosticType.warning(
          "JSC_NTI_NOT_UNIQUE_INSTANTIATION",
          "When instantiating a polymorphic function,"
          + " you can only specify one type for each type variable.\n"
          + " Found {0} types for type variable {1}: {2},\n"
          + " when instantiating type: {3}");

  static final DiagnosticType FAILED_TO_UNIFY =
      DiagnosticType.warning(
          "JSC_NTI_FAILED_TO_UNIFY",
          "Could not instantiate type {0} with {1}.");

  static final DiagnosticType INVALID_INDEX_TYPE =
      DiagnosticType.warning(
          "JSC_NTI_INVALID_INDEX_TYPE",
          "Invalid type for index.\n"
          + "expected : {0}\n"
          + "found    : {1}\n");

  static final DiagnosticType BOTTOM_INDEX_TYPE =
      DiagnosticType.warning(
          "JSC_NTI_BOTTOM_INDEX_TYPE",
          "This IObject {0} cannot be accessed with a valid type.\n"
          + " Usually the result of a bad union type.\n");

  static final DiagnosticType INVALID_OBJLIT_PROPERTY_TYPE =
      DiagnosticType.warning(
          "JSC_NTI_INVALID_OBJLIT_PROPERTY_TYPE",
          "Object-literal property declared as {0} but has type {1}.");

  static final DiagnosticType FORIN_EXPECTS_OBJECT =
      DiagnosticType.warning(
          "JSC_NTI_FORIN_EXPECTS_OBJECT",
          "For/in expects an object, found type {0}.");

  static final DiagnosticType FORIN_EXPECTS_STRING_KEY =
      DiagnosticType.warning(
          "JSC_NTI_FORIN_EXPECTS_STRING_KEY",
          "For/in creates string keys, but variable has declared type {1}.");

  static final DiagnosticType CONST_REASSIGNED =
      DiagnosticType.warning(
          "JSC_NTI_CONST_REASSIGNED",
          "Cannot change the value of a constant.");

  static final DiagnosticType CONST_PROPERTY_REASSIGNED =
      DiagnosticType.warning(
          "JSC_NTI_CONST_PROPERTY_REASSIGNED",
          "Cannot change the value of a constant property.");

  static final DiagnosticType NOT_A_CONSTRUCTOR =
      DiagnosticType.warning(
          "JSC_NTI_NOT_A_CONSTRUCTOR",
          "Expected a constructor but found type {0}.");

  static final DiagnosticType ASSERT_FALSE =
      DiagnosticType.warning(
          "JSC_NTI_ASSERT_FALSE",
          "Assertion is always false. Please use a throw or fail() instead.");

  static final DiagnosticType UNKNOWN_ASSERTION_TYPE =
      DiagnosticType.warning(
          "JSC_NTI_UNKNOWN_ASSERTION_TYPE",
          "Assert with unknown asserted type.");

  static final DiagnosticType INVALID_THIS_TYPE_IN_BIND =
      DiagnosticType.warning(
          "JSC_NTI_INVALID_THIS_TYPE_IN_BIND",
          "The first argument to bind has type {0} which is not a subtype of"
          + " {1}.");

  static final DiagnosticType CANNOT_BIND_CTOR =
      DiagnosticType.warning(
          "JSC_NTI_CANNOT_BIND_CTOR",
          "We do not support using .bind on constructor functions.");

  static final DiagnosticType GOOG_BIND_EXPECTS_FUNCTION =
      DiagnosticType.warning(
          "JSC_NTI_GOOG_BIND_EXPECTS_FUNCTION",
          "The first argument to goog.bind/goog.partial must be a function,"
          + " found: {0}");

  static final DiagnosticType BOTTOM_PROP =
      DiagnosticType.warning(
          "JSC_NTI_BOTTOM_PROP",
          "Property {0} of {1} cannot have a valid type."
          + "Maybe the result of a union of incompatible types?");

  static final DiagnosticType INVALID_CAST =
      DiagnosticType.warning("JSC_NTI_INVALID_CAST",
          "invalid cast - the types do not have a common subtype\n" +
          "from: {0}\n" +
          "to  : {1}");

  static final DiagnosticType GLOBAL_THIS = DiagnosticType.warning(
      "JSC_NTI_USED_GLOBAL_THIS",
      "Dangerous use of the global THIS object");

  static final DiagnosticType MISSING_RETURN_STATEMENT =
      DiagnosticType.warning(
          "JSC_NTI_MISSING_RETURN_STATEMENT",
          "Missing return statement. Function expected to return {0}.");

  static final DiagnosticType CONSTRUCTOR_NOT_CALLABLE =
      DiagnosticType.warning(
          "JSC_NTI_CONSTRUCTOR_NOT_CALLABLE",
          "Constructor {0} should be called with the \"new\" keyword");

  static final DiagnosticType ILLEGAL_OBJLIT_KEY =
      DiagnosticType.warning(
          "JSC_NTI_ILLEGAL_OBJLIT_KEY",
          "Illegal key, the object literal is a {0}");

  static final DiagnosticType ILLEGAL_PROPERTY_CREATION =
      DiagnosticType.warning(
          "JSC_NTI_ILLEGAL_PROPERTY_CREATION",
          "Cannot add property {0} to a struct instance after it is constructed.");

  static final DiagnosticType IN_USED_WITH_STRUCT =
      DiagnosticType.warning(
          "JSC_NTI_IN_USED_WITH_STRUCT",
          "Cannot use the IN operator with structs");

  public static final DiagnosticType INEXISTENT_PROPERTY =
      DiagnosticType.warning(
          "JSC_NTI_INEXISTENT_PROPERTY",
          "Property {0} never defined on {1}");

  static final DiagnosticType NOT_CALLABLE =
      DiagnosticType.warning(
          "JSC_NTI_NOT_FUNCTION_TYPE",
          "{0} expressions are not callable");

  static final DiagnosticType WRONG_ARGUMENT_COUNT =
      DiagnosticType.warning(
          "JSC_NTI_WRONG_ARGUMENT_COUNT",
          "Function {0}: called with {1} argument(s). " +
          "Function requires at least {2} argument(s){3}.");

  static final DiagnosticType ILLEGAL_PROPERTY_ACCESS =
      DiagnosticType.warning(
          "JSC_NTI_ILLEGAL_PROPERTY_ACCESS",
          "Cannot do {0} access on a {1}");

  static final DiagnosticType UNKNOWN_TYPEOF_VALUE =
      DiagnosticType.warning(
          "JSC_NTI_UNKNOWN_TYPEOF_VALUE",
          "unknown type: {0}");

  static final DiagnosticType UNKNOWN_NAMESPACE_PROPERTY =
      DiagnosticType.warning(
          "JSC_NTI_UNKNOWN_NAMESPACE_PROPERTY",
          "Cannot determine the type of namespace property {0}. "
          + "Maybe a prefix of the property name has been redefined?");

  // Not part of ALL_DIAGNOSTICS because it should not be enabled with
  // --jscomp_error=newCheckTypes. It should only be enabled explicitly.

  static final DiagnosticType NULLABLE_DEREFERENCE =
      DiagnosticType.disabled(
          "JSC_NTI_NULLABLE_DEREFERENCE",
          "Attempt to use nullable type {0}.");

  static final DiagnosticType UNKNOWN_EXPR_TYPE =
      DiagnosticType.disabled(
          "JSC_NTI_UNKNOWN_EXPR_TYPE",
          "This {0} expression has the unknown type.");

  static final DiagnosticGroup ALL_DIAGNOSTICS = new DiagnosticGroup(
      ASSERT_FALSE,
      BOTTOM_INDEX_TYPE,
      BOTTOM_PROP,
      CANNOT_BIND_CTOR,
      CONST_PROPERTY_REASSIGNED,
      CONST_REASSIGNED,
      CONSTRUCTOR_NOT_CALLABLE,
      CROSS_SCOPE_GOTCHA,
      FAILED_TO_UNIFY,
      FORIN_EXPECTS_OBJECT,
      FORIN_EXPECTS_STRING_KEY,
      GLOBAL_THIS,
      GOOG_BIND_EXPECTS_FUNCTION,
      ILLEGAL_OBJLIT_KEY,
      ILLEGAL_PROPERTY_ACCESS,
      ILLEGAL_PROPERTY_CREATION,
      IN_USED_WITH_STRUCT,
      INEXISTENT_PROPERTY,
      INVALID_ARGUMENT_TYPE,
      INVALID_CAST,
      INVALID_INFERRED_RETURN_TYPE,
      INVALID_OBJLIT_PROPERTY_TYPE,
      INVALID_OPERAND_TYPE,
      INVALID_THIS_TYPE_IN_BIND,
      MISSING_RETURN_STATEMENT,
      MISTYPED_ASSIGN_RHS,
      INVALID_INDEX_TYPE,
      NOT_A_CONSTRUCTOR,
      NOT_CALLABLE,
      NOT_UNIQUE_INSTANTIATION,
      POSSIBLY_INEXISTENT_PROPERTY,
      PROPERTY_ACCESS_ON_NONOBJECT,
      RETURN_NONDECLARED_TYPE,
      UNKNOWN_ASSERTION_TYPE,
      UNKNOWN_NAMESPACE_PROPERTY,
      UNKNOWN_TYPEOF_VALUE,
      WRONG_ARGUMENT_COUNT);

  public static class WarningReporter {
    AbstractCompiler compiler;
    WarningReporter(AbstractCompiler compiler) { this.compiler = compiler; }

    void add(JSError warning) {
      String filename = warning.node.getSourceFileName();
      // Avoid some warnings in code generated by the ES6 transpilation.
      // TODO(dimvar): typecheck that code properly and remove this.
      if (filename != null && filename.startsWith(" [synthetic")
          || JSType.mockToString) {
        return;
      }
      compiler.report(warning);
    }
  }

  private WarningReporter warnings;
  private final AbstractCompiler compiler;
  private final CodingConvention convention;
  private Map<DiGraphEdge<Node, ControlFlowGraph.Branch>, TypeEnv> envs;
  private Map<NTIScope, JSType> summaries;
  private Map<Node, DeferredCheck> deferredChecks;
  private ControlFlowGraph<Node> cfg;
  private NTIScope currentScope;
  // This TypeEnv should be computed once per scope
  private TypeEnv typeEnvFromDeclaredTypes = null;
  private GlobalTypeInfo symbolTable;
  private JSTypes commonTypes;
  // RETVAL_ID is used when we calculate the summary type of a function
  private static final String RETVAL_ID = "%return";
  private static final String THIS_ID = "this";
  private static final String GETTER_PREFIX = "%getter_fun";
  private static final String SETTER_PREFIX = "%setter_fun";
  private final String ABSTRACT_METHOD_NAME;
  private final Map<String, AssertionFunctionSpec> assertionFunctionsMap;
  // To avoid creating warning objects for disabled warnings
  private final boolean reportUnknownTypes;
  private final boolean reportNullDeref;

  // Used only for development
  private static boolean showDebuggingPrints = false;
  static boolean measureMem = false;
  private static long peakMem = 0;

  NewTypeInference(AbstractCompiler compiler) {
    this.warnings = new WarningReporter(compiler);
    this.compiler = compiler;
    this.convention = compiler.getCodingConvention();
    this.envs = new LinkedHashMap<>();
    this.summaries = new LinkedHashMap<>();
    this.deferredChecks = new LinkedHashMap<>();
    this.ABSTRACT_METHOD_NAME = convention.getAbstractMethodName();
    this.reportUnknownTypes =
        compiler.getOptions().enables(DiagnosticGroups.REPORT_UNKNOWN_TYPES);
    this.reportNullDeref = compiler.getOptions()
        .enables(DiagnosticGroups.NEW_CHECK_TYPES_ALL_CHECKS);
    assertionFunctionsMap = new LinkedHashMap<>();
    for (AssertionFunctionSpec assertionFunction : convention.getAssertionFunctions()) {
      assertionFunctionsMap.put(
          assertionFunction.getFunctionName(),
          assertionFunction);
    }
  }

  @VisibleForTesting // Only used from tests
  public NTIScope processForTesting(Node externs, Node root) {
    process(externs, root);
    return symbolTable.getGlobalScope();
  }

  @Override
  public void process(Node externs, Node root) {
    try {
      this.symbolTable = (GlobalTypeInfo) compiler.getSymbolTable();
      this.commonTypes = symbolTable.getTypesUtilObject();
      for (NTIScope scope : symbolTable.getScopes()) {
        analyzeFunction(scope);
        envs.clear();
      }
      for (DeferredCheck check : deferredChecks.values()) {
        check.runCheck(summaries, warnings);
      }
      compiler.setSymbolTable(null);
      if (measureMem) {
        System.out.println("Peak mem: " + peakMem + "MB");
      }
    } catch (Exception unexpectedException) {
      String message = unexpectedException.getMessage();
      if (currentScope != null) {
        message += "\nIn scope: " + currentScope;
      }
      this.compiler.throwInternalError(message, unexpectedException);
    }
  }

  static void updatePeakMem() {
    Runtime rt = Runtime.getRuntime();
    long currentUsedMem = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
    if (currentUsedMem > peakMem) {
      peakMem = currentUsedMem;
    }
  }

  private static void println(Object ... objs) {
    if (showDebuggingPrints) {
      StringBuilder b = new StringBuilder();
      for (Object obj : objs) {
        b.append(obj);
      }
      System.out.println(b);
    }
  }

  private TypeEnv getInEnv(DiGraphNode<Node, ControlFlowGraph.Branch> dn) {
    List<DiGraphEdge<Node, ControlFlowGraph.Branch>> inEdges = dn.getInEdges();
    if (inEdges.size() == 1) {
      return envs.get(inEdges.get(0));
    }

    Set<TypeEnv> envSet = new LinkedHashSet<>();
    for (DiGraphEdge<Node, ControlFlowGraph.Branch> de : inEdges) {
      TypeEnv env = envs.get(de);
      if (env != null) {
        envSet.add(env);
      }
    }
    if (envSet.isEmpty()) {
      return null;
    }
    return TypeEnv.join(envSet);
  }

  private TypeEnv getOutEnv(DiGraphNode<Node, ControlFlowGraph.Branch> dn) {
    List<DiGraphEdge<Node, ControlFlowGraph.Branch>> outEdges = dn.getOutEdges();
    if (outEdges.isEmpty()) {
      // This occurs when visiting a throw in the backward direction.
      Preconditions.checkArgument(dn.getValue().isThrow());
      return this.typeEnvFromDeclaredTypes;
    }
    if (outEdges.size() == 1) {
      return envs.get(outEdges.get(0));
    }
    Set<TypeEnv> envSet = new LinkedHashSet<>();
    for (DiGraphEdge<Node, ControlFlowGraph.Branch> de : outEdges) {
      TypeEnv env = envs.get(de);
      if (env != null) {
        envSet.add(env);
      }
    }
    Preconditions.checkState(!envSet.isEmpty());
    return TypeEnv.join(envSet);
  }

  private TypeEnv setOutEnv(
      DiGraphNode<Node, ControlFlowGraph.Branch> dn, TypeEnv e) {
    for (DiGraphEdge<Node, ControlFlowGraph.Branch> de : dn.getOutEdges()) {
      envs.put(de, e);
    }
    return e;
  }

  // Initialize the type environments on the CFG edges before the FWD analysis.
  private void initEdgeEnvsFwd(TypeEnv entryEnv) {
    envs.clear();

    // For function scopes, add the formal parameters and the free variables
    // from outer scopes to the environment.
    Set<String> nonLocals = new LinkedHashSet<>();
    if (currentScope.isFunction()) {
      if (currentScope.getName() != null) {
        nonLocals.add(currentScope.getName());
      }
      nonLocals.addAll(currentScope.getOuterVars());
      nonLocals.addAll(currentScope.getFormals());
      if (currentScope.hasThis()) {
        nonLocals.add(THIS_ID);
      }
      entryEnv = envPutType(entryEnv, RETVAL_ID, JSType.UNDEFINED);
    } else {
      nonLocals.addAll(currentScope.getExterns());
    }
    for (String name : nonLocals) {
      JSType declType = currentScope.getDeclaredTypeOf(name);
      JSType initType =
          declType == null ? envGetType(entryEnv, name) : declType;
      println("Adding non-local ", name,
          " with decltype: ", declType,
          " and inittype: ", initType);
      entryEnv = envPutType(entryEnv, name, initType);
    }

    // For all scopes, add local variables and (local) function definitions
    // to the environment.
    for (String local : currentScope.getLocals()) {
      if (!currentScope.isFunctionNamespace(local)) {
        entryEnv = envPutType(entryEnv, local, JSType.UNDEFINED);
      }
    }
    for (String fnName : currentScope.getLocalFunDefs()) {
      entryEnv = envPutType(entryEnv, fnName, getSummaryOfLocalFunDef(fnName));
    }
    println("Keeping env: ", entryEnv);
    setOutEnv(cfg.getEntry(), entryEnv);
  }

  private TypeEnv getTypeEnvFromDeclaredTypes() {
    TypeEnv env = new TypeEnv();
    Set<String> varNames = currentScope.getOuterVars();
    Set<String> locals = currentScope.getLocals();
    varNames.addAll(locals);
    varNames.addAll(currentScope.getExterns());
    if (currentScope.isFunction()) {
      if (currentScope.getName() != null) {
        varNames.add(currentScope.getName());
      }
      varNames.addAll(currentScope.getFormals());
      if (currentScope.hasThis()) {
        varNames.add(THIS_ID);
      }
      // In the rare case when there is a local variable named "arguments",
      // this entry will be overwritten in the foreach loop below.
      JSType argumentsType;
      DeclaredFunctionType dft = currentScope.getDeclaredFunctionType();
      if (dft.getOptionalArity() == 0 && dft.hasRestFormals()) {
        argumentsType = dft.getRestFormalsType();
      } else {
        argumentsType = JSType.UNKNOWN;
      }
      env = envPutType(env, "arguments",
          commonTypes.getArgumentsArrayType(argumentsType));
    }
    for (String varName : varNames) {
      if (!locals.contains(varName) || !currentScope.isFunctionNamespace(varName)) {
        JSType declType = currentScope.getDeclaredTypeOf(varName);
        env = envPutType(env, varName, declType == null ? JSType.UNKNOWN : declType);
      }
    }
    for (String fnName : currentScope.getLocalFunDefs()) {
      env = envPutType(env, fnName, getSummaryOfLocalFunDef(fnName));
    }
    return env;
  }

  private JSType getSummaryOfLocalFunDef(String name) {
    NTIScope fnScope = currentScope.getScope(name);
    JSType fnType = summaries.get(fnScope);
    if (fnType != null) {
      return fnType;
    }
    // Functions defined in externs have no summary, so use the declared type
    fnType = currentScope.getDeclaredTypeOf(name);
    Preconditions.checkState(fnType.getFunType() != null,
        "Needed function but found %s", fnType);
    return changeTypeIfFunctionNamespace(fnScope, fnType);
  }

  private void buildWorkset(
      DiGraphNode<Node, ControlFlowGraph.Branch> dn,
      List<DiGraphNode<Node, ControlFlowGraph.Branch>> workset) {
    buildWorksetHelper(dn, workset,
        new LinkedHashSet<DiGraphNode<Node, ControlFlowGraph.Branch>>());
  }

  private void buildWorksetHelper(
      DiGraphNode<Node, ControlFlowGraph.Branch> dn,
      List<DiGraphNode<Node, ControlFlowGraph.Branch>> workset,
      Set<DiGraphNode<Node, ControlFlowGraph.Branch>> seen) {
    if (seen.contains(dn) || dn == cfg.getImplicitReturn()) {
      return;
    }
    switch (dn.getValue().getType()) {
      case Token.DO:
      case Token.WHILE:
      case Token.FOR:
        // Do the loop body first, then the loop follow.
        // For DO loops, we do BODY-CONDT-CONDF-FOLLOW
        // Since CONDT is currently unused, this could be optimized.
        List<DiGraphEdge<Node, ControlFlowGraph.Branch>> outEdges =
            dn.getOutEdges();
        seen.add(dn);
        workset.add(dn);
        for (DiGraphEdge<Node, ControlFlowGraph.Branch> outEdge : outEdges) {
          if (outEdge.getValue() == ControlFlowGraph.Branch.ON_TRUE) {
            buildWorksetHelper(outEdge.getDestination(), workset, seen);
          }
        }
        workset.add(dn);
        for (DiGraphEdge<Node, ControlFlowGraph.Branch> outEdge : outEdges) {
          if (outEdge.getValue() == ControlFlowGraph.Branch.ON_FALSE) {
            buildWorksetHelper(outEdge.getDestination(), workset, seen);
          }
        }
        break;
      default:
        // Wait for all other incoming edges at join nodes.
        for (DiGraphEdge<Node, ControlFlowGraph.Branch> inEdge :
            dn.getInEdges()) {
          if (!seen.contains(inEdge.getSource())
              && !inEdge.getSource().getValue().isDo()) {
            return;
          }
        }
        seen.add(dn);
        if (cfg.getEntry() != dn) {
          workset.add(dn);
        }
        // Don't recur for straight-line code
        while (true) {
          List<DiGraphNode<Node, ControlFlowGraph.Branch>> succs =
              cfg.getDirectedSuccNodes(dn);
          if (succs.size() != 1) {
            break;
          }
          DiGraphNode<Node, ControlFlowGraph.Branch> succ = succs.get(0);
          if (succ == cfg.getImplicitReturn()) {
            return;
          }
          // Make sure that succ isn't a join node
          if (cfg.getDirectedPredNodes(succ).size() > 1) {
            break;
          }
          workset.add(succ);
          seen.add(succ);
          dn = succ;
        }
        for (DiGraphNode<Node, ControlFlowGraph.Branch> succ :
            cfg.getDirectedSuccNodes(dn)) {
          buildWorksetHelper(succ, workset, seen);
        }
        break;
    }
  }

  private void analyzeFunction(NTIScope scope) {
    println("=== Analyzing function: ", scope.getReadableName(), " ===");
    currentScope = scope;
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, false, false);
    cfa.process(null, scope.getRoot());
    cfg = cfa.getCfg();
    println(cfg);
    // The size is > 1 when multiple files are compiled
    // Preconditions.checkState(cfg.getEntry().getOutEdges().size() == 1);
    List<DiGraphNode<Node, ControlFlowGraph.Branch>> workset =
        new LinkedList<>();
    buildWorkset(cfg.getEntry(), workset);
    /* println("Workset: ", workset); */
    this.typeEnvFromDeclaredTypes = getTypeEnvFromDeclaredTypes();
    if (scope.isFunction() && scope.hasUndeclaredFormalsOrOuters()) {
      Collections.reverse(workset);
      // Ideally, we would like to only set the in-edges of the implicit return
      // rather than all edges. However, we cannot do that because of a bug in
      // workset construction. (The test testBadWorksetConstruction would fail.)
      // In buildWorksetHelper, if a loop contains break, we add the FOLLOW node
      // of the loop before adding the loop header twice. So, the 2nd addition
      // of the loop header has no effect. We should fix workset creation
      // (eg, by putting edges instead of nodes in seen, or some other way that
      // correctly waits for all incoming edges).
      for (DiGraphEdge<Node, ControlFlowGraph.Branch> e : cfg.getEdges()) {
        envs.put(e, this.typeEnvFromDeclaredTypes);
      }
      analyzeFunctionBwd(workset);
      Collections.reverse(workset);
      // TODO(dimvar): Revisit what we throw away after the bwd analysis
      TypeEnv entryEnv = getEntryTypeEnv();
      initEdgeEnvsFwd(entryEnv);
      if (measureMem) {
        updatePeakMem();
      }
    } else {
      TypeEnv entryEnv = this.typeEnvFromDeclaredTypes;
      initEdgeEnvsFwd(entryEnv);
    }
    this.typeEnvFromDeclaredTypes = null;
    analyzeFunctionFwd(workset);
    if (scope.isFunction()) {
      createSummary(scope);
    }
    if (measureMem) {
      updatePeakMem();
    }
  }

  private void analyzeFunctionBwd(
      List<DiGraphNode<Node, ControlFlowGraph.Branch>> workset) {
    for (DiGraphNode<Node, ControlFlowGraph.Branch> dn : workset) {
      Node n = dn.getValue();
      TypeEnv outEnv = Preconditions.checkNotNull(getOutEnv(dn));
      TypeEnv inEnv;
      println("\tBWD Statment: ", n);
      println("\t\toutEnv: ", outEnv);
      switch (n.getType()) {
        case Token.EXPR_RESULT:
          inEnv = analyzeExprBwd(n.getFirstChild(), outEnv, JSType.UNKNOWN).env;
          break;
        case Token.RETURN: {
          Node retExp = n.getFirstChild();
          if (retExp == null) {
            inEnv = outEnv;
          } else {
            JSType declRetType = currentScope.getDeclaredFunctionType().getReturnType();
            declRetType = declRetType == null ? JSType.UNKNOWN : declRetType;
            inEnv = analyzeExprBwd(retExp, outEnv, declRetType).env;
          }
          break;
        }
        case Token.VAR: {
          if (NodeUtil.isTypedefDecl(n)) {
            inEnv = outEnv;
            break;
          }
          inEnv = outEnv;
          for (Node nameNode = n.getFirstChild(); nameNode != null;
               nameNode = nameNode.getNext()) {
            String varName = nameNode.getString();
            Node rhs = nameNode.getFirstChild();
            JSType declType = currentScope.getDeclaredTypeOf(varName);
            inEnv = envPutType(inEnv, varName, JSType.UNKNOWN);
            if (rhs == null || currentScope.isLocalFunDef(varName)) {
              continue;
            }
            JSType inferredType = envGetType(outEnv, varName);
            JSType requiredType;
            if (declType == null) {
              requiredType = inferredType;
            } else {
              // TODO(dimvar): look if the meet is needed
              requiredType = JSType.meet(declType, inferredType);
              if (requiredType.isBottom()) {
                requiredType = JSType.UNKNOWN;
              }
            }
            inEnv = analyzeExprBwd(rhs, inEnv, requiredType).env;
          }
          break;
        }
        case Token.BLOCK:
        case Token.BREAK:
        case Token.CATCH:
        case Token.CONTINUE:
        case Token.DEFAULT_CASE:
        case Token.DEBUGGER:
        case Token.EMPTY:
        case Token.SCRIPT:
        case Token.TRY:
          inEnv = outEnv;
          break;
        case Token.DO:
        case Token.FOR:
        case Token.IF:
        case Token.WHILE:
          Node expr = NodeUtil.isForIn(n) ?
              n.getFirstChild() : NodeUtil.getConditionExpression(n);
          inEnv = analyzeExprBwd(expr, outEnv).env;
          break;
        case Token.THROW:
        case Token.CASE:
        case Token.SWITCH:
          inEnv = analyzeExprBwd(n.getFirstChild(), outEnv).env;
          break;
        default:
          if (NodeUtil.isStatement(n)) {
            throw new RuntimeException("Unhandled statement type: "
                + Token.name(n.getType()));
          } else {
            inEnv = analyzeExprBwd(n, outEnv).env;
            break;
          }
      }
      println("\t\tinEnv: ", inEnv);
      for (DiGraphEdge<Node, ControlFlowGraph.Branch> de : dn.getInEdges()) {
        envs.put(de, inEnv);
      }
    }
  }

  private void analyzeFunctionFwd(
      List<DiGraphNode<Node, ControlFlowGraph.Branch>> workset) {
    for (DiGraphNode<Node, ControlFlowGraph.Branch> dn : workset) {
      Node n = dn.getValue();
      Node parent = n.getParent();
      Preconditions.checkState(n != null,
          "Implicit return should not be in workset.");
      TypeEnv inEnv = getInEnv(dn);
      TypeEnv outEnv = null;
      if (parent.isScript()
          || (parent.isBlock() && parent.getParent().isFunction())) {
        // All joins have merged; forget changes
        inEnv = inEnv.clearChangeLog();
      }

      println("\tFWD Statment: ", n);
      println("\t\tinEnv: ", inEnv);
      boolean conditional = false;
      switch (n.getType()) {
        case Token.BLOCK:
        case Token.BREAK:
        case Token.CONTINUE:
        case Token.DEFAULT_CASE:
        case Token.DEBUGGER:
        case Token.EMPTY:
        case Token.FUNCTION:
        case Token.SCRIPT:
        case Token.TRY:
          outEnv = inEnv;
          break;
        case Token.CATCH:
          String catchVarname = n.getFirstChild().getString();
          outEnv = envPutType(inEnv, catchVarname, JSType.UNKNOWN);
          break;
        case Token.EXPR_RESULT:
          println("\tsemi ", Token.name(n.getFirstChild().getType()));
          if (n.getBooleanProp(Node.ANALYZED_DURING_GTI)) {
            n.removeProp(Node.ANALYZED_DURING_GTI);
            outEnv = inEnv;
          } else {
            outEnv = analyzeExprFwd(n.getFirstChild(), inEnv, JSType.UNKNOWN).env;
          }
          break;
        case Token.RETURN: {
          Node retExp = n.getFirstChild();
          JSType declRetType = currentScope.getDeclaredFunctionType().getReturnType();
          declRetType = declRetType == null ? JSType.UNKNOWN : declRetType;
          JSType actualRetType;
          if (retExp == null) {
            actualRetType = JSType.UNDEFINED;
            outEnv = envPutType(inEnv, RETVAL_ID, actualRetType);
          } else {
            EnvTypePair retPair = analyzeExprFwd(retExp, inEnv, declRetType);
            actualRetType = retPair.type;
            outEnv = envPutType(retPair.env, RETVAL_ID, actualRetType);
          }
          if (!actualRetType.isSubtypeOf(declRetType)) {
            warnings.add(JSError.make(n, RETURN_NONDECLARED_TYPE,
                    declRetType.toString(), actualRetType.toString()));
          }
          break;
        }
        case Token.DO:
        case Token.IF:
        case Token.FOR:
        case Token.WHILE:
          if (NodeUtil.isForIn(n)) {
            Node obj = n.getSecondChild();
            EnvTypePair pair = analyzeExprFwd(obj, inEnv, pickReqObjType(n));
            pair = mayWarnAboutNullableReferenceAndTighten(n, pair.type, null, inEnv);
            JSType objType = pair.type;
            if (!objType.isSubtypeOf(JSType.TOP_OBJECT)) {
              warnings.add(JSError.make(
                  obj, FORIN_EXPECTS_OBJECT, objType.toString()));
            } else if (objType.isStruct()) {
              warnings.add(JSError.make(obj, IN_USED_WITH_STRUCT));
            }
            Node lhs = n.getFirstChild();
            LValueResultFwd lval = analyzeLValueFwd(lhs, inEnv, JSType.STRING);
            if (lval.declType != null &&
                !commonTypes.isStringScalarOrObj(lval.declType)) {
              warnings.add(JSError.make(lhs, FORIN_EXPECTS_STRING_KEY,
                  lval.declType.toString()));
              outEnv = lval.env;
            } else {
              outEnv = updateLvalueTypeInEnv(lval.env, lhs, lval.ptr, JSType.STRING);
            }
            break;
          }
          conditional = true;
          analyzeConditionalStmFwd(dn, NodeUtil.getConditionExpression(n), inEnv);
          break;
        case Token.CASE: {
          conditional = true;
          // See analyzeExprFwd#Token.CASE for how to handle this precisely
          analyzeConditionalStmFwd(dn, n, inEnv);
          break;
        }
        case Token.VAR:
          outEnv = inEnv;
          if (NodeUtil.isTypedefDecl(n)) {
            break;
          }
          for (Node nameNode = n.getFirstChild(); nameNode != null;
               nameNode = nameNode.getNext()) {
            outEnv = processVarDeclaration(nameNode, outEnv);
          }
          break;
        case Token.SWITCH:
        case Token.THROW:
          outEnv = analyzeExprFwd(n.getFirstChild(), inEnv).env;
          break;
        default:
          if (NodeUtil.isStatement(n)) {
            throw new RuntimeException("Unhandled statement type: "
                + Token.name(n.getType()));
          } else {
            outEnv = analyzeExprFwd(n, inEnv, JSType.UNKNOWN).env;
            break;
          }
      }

      if (!conditional) {
        println("\t\toutEnv: ", outEnv);
        setOutEnv(dn, outEnv);
      }
    }
  }

  private void analyzeConditionalStmFwd(
      DiGraphNode<Node, ControlFlowGraph.Branch> stm, Node cond, TypeEnv inEnv) {
    for (DiGraphEdge<Node, ControlFlowGraph.Branch> outEdge :
        stm.getOutEdges()) {
      JSType specializedType;
      switch (outEdge.getValue()) {
        case ON_TRUE:
          specializedType = JSType.TRUTHY;
          break;
        case ON_FALSE:
          specializedType = JSType.FALSY;
          break;
        case ON_EX:
          specializedType = JSType.UNKNOWN;
          break;
        default:
          throw new RuntimeException(
              "Condition with an unexpected edge type: " + outEdge.getValue());
      }
      envs.put(outEdge,
          analyzeExprFwd(cond, inEnv, JSType.UNKNOWN, specializedType).env);
    }
  }

  private void createSummary(NTIScope fn) {
    Node fnRoot = fn.getRoot();
    Preconditions.checkArgument(!fnRoot.isFromExterns());
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    TypeEnv entryEnv = getEntryTypeEnv();
    TypeEnv exitEnv = getInEnv(cfg.getImplicitReturn());
    if (exitEnv == null) {
      // This function only exits with THROWs
      exitEnv = envPutType(new TypeEnv(), RETVAL_ID, JSType.BOTTOM);
    }

    DeclaredFunctionType declType = fn.getDeclaredFunctionType();
    int reqArity = declType.getRequiredArity();
    int optArity = declType.getOptionalArity();
    if (declType.isGeneric()) {
      builder.addTypeParameters(declType.getTypeParameters());
    }

    // Every trailing undeclared formal whose inferred type is ?
    // or contains undefined can be marked as optional.
    List<String> formals = fn.getFormals();
    for (int i = reqArity - 1; i >= 0; i--) {
      JSType formalType = fn.getDeclaredFunctionType().getFormalType(i);
      if (formalType != null) {
        break;
      }
      String formalName = formals.get(i);
      formalType = getTypeAfterFwd(formalName, entryEnv, exitEnv);
      if (formalType.isUnknown() || JSType.UNDEFINED.isSubtypeOf(formalType)) {
        reqArity--;
      } else {
        break;
      }
    }

    // Collect types of formals in the builder
    int formalIndex = 0;
    for (String formal : formals) {
      JSType formalType = fn.getDeclaredTypeOf(formal);
      if (formalType == null) {
        formalType = getTypeAfterFwd(formal, entryEnv, exitEnv);
      }
      if (formalIndex < reqArity) {
        builder.addReqFormal(formalType);
      } else if (formalIndex < optArity) {
        builder.addOptFormal(formalType);
      }
      formalIndex++;
    }
    if (declType.hasRestFormals()) {
      builder.addRestFormals(declType.getFormalType(formalIndex));
    }

    for (String outer : fn.getOuterVars()) {
      println("Free var ", outer, " going in summary");
      builder.addOuterVarPrecondition(outer, envGetType(entryEnv, outer));
    }

    builder.addNominalType(declType.getNominalType());
    builder.addReceiverType(declType.getReceiverType());
    JSType declRetType = declType.getReturnType();
    JSType actualRetType = Preconditions.checkNotNull(envGetType(exitEnv, RETVAL_ID));

    if (declRetType != null) {
      builder.addRetType(declRetType);
      if (!isAllowedToNotReturn(fn) &&
          !JSType.UNDEFINED.isSubtypeOf(declRetType) &&
          hasPathWithNoReturn(cfg)) {
        warnings.add(JSError.make(
            fnRoot, MISSING_RETURN_STATEMENT, declRetType.toString()));
      }
    } else if (declType.getNominalType() == null) {
      // If someone uses the result of a function that doesn't return, they get a warning.
      builder.addRetType(actualRetType.isBottom() ? JSType.TOP : actualRetType);
    } else {
      // Don't infer a return type for constructors. We want to warn for
      // constructors called without new who don't explicitly declare @return.
      builder.addRetType(JSType.UNDEFINED);
    }
    JSType summary = commonTypes.fromFunctionType(builder.buildFunction());
    println("Function summary for ", fn.getReadableName());
    println("\t", summary);
    summary = changeTypeIfFunctionNamespace(fn, summary);
    summaries.put(fn, summary);
    maybeSetTypeI(fnRoot, summary);
    Node fnNameNode = NodeUtil.getNameNode(fnRoot);
    if (fnNameNode != null) {
      maybeSetTypeI(fnNameNode, summary);
    }
  }

  private JSType changeTypeIfFunctionNamespace(NTIScope fnScope, JSType fnType) {
    NTIScope enclosingScope = fnScope.getParent();
    Node fnNameNode = NodeUtil.getNameNode(fnScope.getRoot());
    JSType namespaceType = null;
    if (fnNameNode == null) {
      return fnType;
    }
    if (fnNameNode.isName()) {
      String fnName = fnNameNode.getString();
      if (enclosingScope.isFunctionNamespace(fnName)) {
        namespaceType = enclosingScope.getDeclaredTypeOf(fnName);
      }
    } else if (fnNameNode.isQualifiedName()) {
      QualifiedName qname = QualifiedName.fromNode(fnNameNode);
      JSType rootNs = enclosingScope.getDeclaredTypeOf(qname.getLeftmostName());
      namespaceType = rootNs == null ? null : rootNs.getProp(qname.getAllButLeftmost());
    }
    if (namespaceType != null && namespaceType.isNamespace()) {
      // Replace the less-precise declared function type
      // with the new function summary.
      return namespaceType.withFunction(
          fnType.getFunTypeIfSingletonObj(), commonTypes.getFunctionType());
    }
    return fnType;
  }

  // TODO(dimvar): To get the adjusted end-of-fwd type for objs, we must be
  // able to know whether a property was added during the evaluation of the
  // function, or was on the object already.
  private JSType getTypeAfterFwd(
      String varName, TypeEnv entryEnv, TypeEnv exitEnv) {
    JSType typeAfterBwd = envGetType(entryEnv, varName);
    if (!typeAfterBwd.hasNonScalar() || typeAfterBwd.getFunType() != null) {
      // The type of a formal after fwd is more precise than the type after bwd,
      // so we use typeAfterFwd in the summary.
      // Trade-off: If the formal is assigned in the body of a function, and the
      // new value has a different type, we compute a wrong summary.
      // Since this is rare, we prefer typeAfterFwd to typeAfterBwd.
      JSType typeAfterFwd = envGetType(exitEnv, varName);
      if (typeAfterFwd != null) {
        return typeAfterFwd;
      }
    }
    return typeAfterBwd;
  }

  private static boolean isAllowedToNotReturn(NTIScope methodScope) {
    Node fn = methodScope.getRoot();
    if (fn.isFromExterns()) {
      return true;
    }
    if (!NodeUtil.isPrototypeMethod(fn)) {
      return false;
    }
    JSType maybeInterface;
    Node ntQnameNode = NodeUtil.getPrototypeClassName(fn.getParent().getFirstChild());
    if (ntQnameNode.isName()) {
      maybeInterface = methodScope.getDeclaredTypeOf(ntQnameNode.getString());
    } else {
      QualifiedName ntQname = QualifiedName.fromNode(ntQnameNode);
      JSType rootNamespace = methodScope.getDeclaredTypeOf(ntQname.getLeftmostName());
      maybeInterface = rootNamespace == null
          ? null : rootNamespace.getProp(ntQname.getAllButLeftmost());
    }
    return maybeInterface != null && maybeInterface.isInterfaceDefinition();
  }

  private static boolean hasPathWithNoReturn(ControlFlowGraph<Node> cfg) {
    for (DiGraphNode<Node, ControlFlowGraph.Branch> dn :
             cfg.getDirectedPredNodes(cfg.getImplicitReturn())) {
      Node stm = dn.getValue();
      if (NodeUtil.isLoopStructure(stm)) {
        Node cond = NodeUtil.getConditionExpression(stm);
        if (!(cond != null && NodeUtil.isImpureTrue(cond))) {
          return true;
        }
      } else if (!stm.isReturn()) {
        return true;
      }
    }
    return false;
  }

  /** Processes a single variable declaration in a VAR statement. */
  private TypeEnv processVarDeclaration(Node nameNode, TypeEnv inEnv) {
    String varName = nameNode.getString();
    JSType declType = currentScope.getDeclaredTypeOf(varName);

    if (currentScope.isLocalFunDef(varName)) {
      return inEnv;
    }
    if (NodeUtil.isNamespaceDecl(nameNode)
        || nameNode.getParent().getBooleanProp(Node.ANALYZED_DURING_GTI)) {
      Preconditions.checkNotNull(declType,
          "Can't skip var declaration with undeclared type at: %s", nameNode);
      maybeSetTypeI(nameNode, declType);
      return envPutType(inEnv, varName, declType);
    }

    Node rhs = nameNode.getFirstChild();
    TypeEnv outEnv = inEnv;
    JSType rhsType;
    if (rhs == null) {
      rhsType = JSType.UNDEFINED;
    } else {
      EnvTypePair pair = analyzeExprFwd(rhs, inEnv,
          declType != null ? declType : JSType.UNKNOWN);
      outEnv = pair.env;
      rhsType = pair.type;
    }
    if (declType != null && rhs != null) {
      if (!rhsType.isSubtypeOf(declType)) {
        warnings.add(JSError.make(
            rhs, MISTYPED_ASSIGN_RHS,
            declType.toString(), rhsType.toString()));
        // Don't flow the wrong initialization
        rhsType = declType;
      } else {
        // We do this to preserve type attributes like declaredness of
        // a property
        rhsType = declType.specialize(rhsType);
      }
    }
    maybeSetTypeI(nameNode, rhsType);
    return envPutType(outEnv, varName, rhsType);
  }

  private EnvTypePair analyzeExprFwd(Node expr, TypeEnv inEnv) {
    return analyzeExprFwd(expr, inEnv, JSType.UNKNOWN, JSType.UNKNOWN);
  }

  private EnvTypePair analyzeExprFwd(
      Node expr, TypeEnv inEnv, JSType requiredType) {
    return analyzeExprFwd(expr, inEnv, requiredType, requiredType);
  }

  /**
   * @param requiredType The context requires this type; warn if the expression
   *                     doesn't have this type.
   * @param specializedType Used in boolean contexts to infer types of names.
   *
   * Invariant: specializedType is a subtype of requiredType.
   */
  private EnvTypePair analyzeExprFwd(
      Node expr, TypeEnv inEnv, JSType requiredType, JSType specializedType) {
    Preconditions.checkArgument(requiredType != null && !requiredType.isBottom());
    EnvTypePair resultPair = null;
    switch (expr.getType()) {
      case Token.EMPTY: // can be created by a FOR with empty condition
        resultPair = new EnvTypePair(inEnv, JSType.UNKNOWN);
        break;
      case Token.FUNCTION: {
        String fnName = symbolTable.getFunInternalName(expr);
        JSType fnType = envGetType(inEnv, fnName);
        Preconditions.checkState(fnType != null, "Could not find type for %s", fnName);
        TypeEnv outEnv = collectTypesForFreeVarsFwd(expr, inEnv);
        resultPair = new EnvTypePair(outEnv, fnType);
        break;
      }
      case Token.FALSE:
      case Token.NULL:
      case Token.NUMBER:
      case Token.STRING:
      case Token.TRUE:
        resultPair = new EnvTypePair(inEnv, scalarValueToType(expr.getType()));
        break;
      case Token.OBJECTLIT:
        resultPair = analyzeObjLitFwd(expr, inEnv, requiredType, specializedType);
        break;
      case Token.THIS: {
        if (!currentScope.hasThis()) {
          mayWarnAboutGlobalThis(expr, currentScope);
          resultPair = new EnvTypePair(inEnv, JSType.UNKNOWN);
        } else {
          // A trimmed-down version of analyzeNameFwd.
          JSType inferredType = envGetType(inEnv, THIS_ID);
          if (!inferredType.isSubtypeOf(requiredType)) {
            return new EnvTypePair(inEnv, inferredType);
          }
          JSType preciseType = inferredType.specialize(specializedType);
          resultPair = EnvTypePair.addBinding(inEnv, THIS_ID, preciseType);
        }
        break;
      }
      case Token.NAME:
        resultPair = analyzeNameFwd(expr, inEnv, requiredType, specializedType);
        break;
      case Token.AND:
      case Token.OR:
        resultPair = analyzeLogicalOpFwd(expr, inEnv, requiredType, specializedType);
        break;
      case Token.INC:
      case Token.DEC:
        resultPair = analyzeIncDecFwd(expr, inEnv, requiredType);
        break;
      case Token.BITNOT:
      case Token.NEG:
        resultPair = analyzeUnaryNumFwd(expr, inEnv);
        break;
      case Token.POS: {
        // We are more permissive with +, because it is used to coerce to number
        resultPair = analyzeExprFwd(expr.getFirstChild(), inEnv);
        resultPair.type = JSType.NUMBER;
        break;
      }
      case Token.TYPEOF: {
        resultPair = analyzeExprFwd(expr.getFirstChild(), inEnv);
        resultPair.type = JSType.STRING;
        break;
      }
      case Token.INSTANCEOF:
        resultPair = analyzeInstanceofFwd(expr, inEnv, specializedType);
        break;
      case Token.ADD:
        resultPair = analyzeAddFwd(expr, inEnv, requiredType);
        break;
      case Token.BITOR:
      case Token.BITAND:
      case Token.BITXOR:
      case Token.DIV:
      case Token.LSH:
      case Token.MOD:
      case Token.MUL:
      case Token.RSH:
      case Token.SUB:
      case Token.URSH:
        resultPair = analyzeBinaryNumericOpFwd(expr, inEnv);
        break;
      case Token.ASSIGN:
        resultPair = analyzeAssignFwd(expr, inEnv, requiredType, specializedType);
        break;
      case Token.ASSIGN_ADD:
        resultPair = analyzeAssignAddFwd(expr, inEnv, requiredType);
        break;
      case Token.ASSIGN_BITOR:
      case Token.ASSIGN_BITXOR:
      case Token.ASSIGN_BITAND:
      case Token.ASSIGN_LSH:
      case Token.ASSIGN_RSH:
      case Token.ASSIGN_URSH:
      case Token.ASSIGN_SUB:
      case Token.ASSIGN_MUL:
      case Token.ASSIGN_DIV:
      case Token.ASSIGN_MOD:
        resultPair = analyzeAssignNumericOpFwd(expr, inEnv);
        break;
      case Token.SHEQ:
      case Token.SHNE:
        resultPair = analyzeStrictComparisonFwd(expr.getType(),
            expr.getFirstChild(), expr.getLastChild(), inEnv, specializedType);
        break;
      case Token.EQ:
      case Token.NE:
        resultPair = analyzeNonStrictComparisonFwd(expr, inEnv, specializedType);
        break;
      case Token.LT:
      case Token.GT:
      case Token.LE:
      case Token.GE:
        resultPair = analyzeLtGtFwd(expr, inEnv);
        break;
      case Token.GETPROP:
        Preconditions.checkState(
            !NodeUtil.isAssignmentOp(expr.getParent()) ||
            !NodeUtil.isLValue(expr));
        if (expr.getBooleanProp(Node.ANALYZED_DURING_GTI)) {
          expr.removeProp(Node.ANALYZED_DURING_GTI);
          resultPair = new EnvTypePair(inEnv, requiredType);
        } else {
          resultPair = analyzePropAccessFwd(
              expr.getFirstChild(), expr.getLastChild().getString(),
              inEnv, requiredType, specializedType);
        }
        break;
      case Token.HOOK:
        resultPair = analyzeHookFwd(expr, inEnv, requiredType, specializedType);
        break;
      case Token.CALL:
      case Token.NEW:
        resultPair = analyzeCallNewFwd(expr, inEnv, requiredType, specializedType);
        break;
      case Token.COMMA:
        resultPair = analyzeExprFwd(
            expr.getLastChild(),
            analyzeExprFwd(expr.getFirstChild(), inEnv).env,
            requiredType,
            specializedType);
        break;
      case Token.NOT: {
        resultPair = analyzeExprFwd(expr.getFirstChild(),
            inEnv, JSType.UNKNOWN, specializedType.negate());
        resultPair.type = resultPair.type.negate().toBoolean();
        break;
      }
      case Token.GETELEM:
        resultPair = analyzeGetElemFwd(expr, inEnv, requiredType, specializedType);
        break;
      case Token.VOID: {
        resultPair = analyzeExprFwd(expr.getFirstChild(), inEnv);
        resultPair.type = JSType.UNDEFINED;
        break;
      }
      case Token.IN:
        resultPair = analyzeInFwd(expr, inEnv, specializedType);
        break;
      case Token.DELPROP: {
        // IRFactory checks that the operand is a name, getprop or getelem.
        // No further warnings here.
        resultPair = analyzeExprFwd(expr.getFirstChild(), inEnv);
        resultPair.type = JSType.BOOLEAN;
        break;
      }
      case Token.REGEXP:
        resultPair = new EnvTypePair(inEnv, commonTypes.getRegexpType());
        break;
      case Token.ARRAYLIT:
        resultPair = analyzeArrayLitFwd(expr, inEnv);
        break;
      case Token.CAST:
        resultPair = analyzeCastFwd(expr, inEnv);
        break;
      case Token.CASE:
        // For a statement of the form: switch (exp1) { ... case exp2: ... }
        // we analyze the case as if it were (exp1 === exp2).
        // We analyze the body of the case when the test is true and the stm
        // following the body when the test is false.
        resultPair = analyzeStrictComparisonFwd(Token.SHEQ,
            expr.getParent().getFirstChild(), expr.getFirstChild(),
            inEnv, specializedType);
        break;
      default:
        throw new RuntimeException("Unhandled expression type: " +
              Token.name(expr.getType()));
    }
    mayWarnAboutUnknownType(expr, resultPair.type);
    // TODO(dimvar): We attach the type to every expression because that's also
    // what the old type inference does.
    // But maybe most of these types are never looked at.
    // See if the passes that use types only need types on a small subset of the
    // nodes, and only store these types to save mem.
    maybeSetTypeI(expr, resultPair.type);
    if (this.currentScope.isFunction()) {
      // In global scope, the env is too big and produces too much output
      println("AnalyzeExprFWD: ", expr,
          " ::reqtype: ", requiredType, " ::spectype: ", specializedType,
          " ::resulttype: ", resultPair.type);
    }
    return resultPair;
  }

  private void mayWarnAboutUnknownType(Node expr, JSType t) {
    boolean isKnownGetElem = expr.isGetElem() && expr.getLastChild().isString();
    if (t.isUnknown()
        && this.reportUnknownTypes
        // Don't warn for expressions whose value isn't used
        && !expr.getParent().isExprResult()
        // The old type checker doesn't warn about unknown getelems.
        // Maybe because we can't do anything about them, so why warn?
        // We mimic that behavior here.
        && (!expr.isGetElem() || isKnownGetElem)) {
      warnings.add(JSError.make(expr, UNKNOWN_EXPR_TYPE,
              Token.name(expr.getType())));
    }
  }

  private EnvTypePair analyzeNameFwd(
      Node expr, TypeEnv inEnv, JSType requiredType, JSType specializedType) {
    String varName = expr.getString();
    if (varName.equals("undefined")) {
      return new EnvTypePair(inEnv, JSType.UNDEFINED);
    }
    JSType inferredType = envGetType(inEnv, varName);
    if (inferredType == null) {
      println("Found global variable ", varName);
      // For now, we don't warn for global variables
      return new EnvTypePair(inEnv, JSType.UNKNOWN);
    }
    println(varName, "'s inferredType: ", inferredType,
        " requiredType:  ", requiredType,
        " specializedType:  ", specializedType);
    if (!inferredType.isSubtypeOf(requiredType)) {
      // The inferred type of a variable is always an upper bound, but
      // sometimes it's also a lower bound, eg, if x was the lhs of an =
      // where we know the type of the rhs.
      // We don't track whether the inferred type is a lower bound, so we
      // conservatively assume that it always is.
      // This is why we warn when !inferredType.isSubtypeOf(requiredType).
      // In some rare cases, the inferred type is only an upper bound,
      // and we would falsely warn.
      // (These usually include the polymorphic operators += and <.)
      // We have a heuristic check to avoid the spurious warnings,
      // but we also miss some true warnings.
      JSType declType = currentScope.getDeclaredTypeOf(varName);
      if (tightenTypeAndDontWarn(varName, expr, declType, inferredType, requiredType)) {
        inferredType = inferredType.specialize(requiredType);
      } else {
        // Propagate incorrect type so that the context catches
        // the mismatch
        return new EnvTypePair(inEnv, inferredType);
      }
    }
    // If preciseType is bottom, there is a condition that can't be true,
    // but that's not necessarily a type error.
    JSType preciseType = inferredType.specialize(specializedType);
    println(varName, "'s preciseType: ", preciseType);
    if (!preciseType.isBottom()
        && (currentScope.isUndeclaredFormal(varName)
            || currentScope.isUndeclaredOuterVar(varName))
        && preciseType.hasNonScalar()) {
      // In the bwd direction, we may infer a loose type and then join w/
      // top and forget it. That's why we also loosen types going fwd.
      preciseType = preciseType.withLoose();
    }
    return EnvTypePair.addBinding(inEnv, varName, preciseType);
  }

  private EnvTypePair analyzeLogicalOpFwd(
      Node expr, TypeEnv inEnv, JSType requiredType, JSType specializedType) {
    int exprKind = expr.getType();
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();
    if ((specializedType.isTrueOrTruthy() && exprKind == Token.AND)
        || (specializedType.isFalseOrFalsy() && exprKind == Token.OR)) {
      EnvTypePair lhsPair =
          analyzeExprFwd(lhs, inEnv, JSType.UNKNOWN, specializedType);
      EnvTypePair rhsPair =
          analyzeExprFwd(rhs, lhsPair.env, JSType.UNKNOWN, specializedType);
      return rhsPair;
    } else if ((specializedType.isFalseOrFalsy() && exprKind == Token.AND)
        || (specializedType.isTrueOrTruthy() && exprKind == Token.OR)) {
      EnvTypePair shortCircuitPair =
          analyzeExprFwd(lhs, inEnv, JSType.UNKNOWN, specializedType);
      EnvTypePair lhsPair = analyzeExprFwd(
          lhs, inEnv, JSType.UNKNOWN, specializedType.negate());
      EnvTypePair rhsPair =
          analyzeExprFwd(rhs, lhsPair.env, JSType.UNKNOWN, specializedType);
      JSType lhsUnspecializedType = JSType.join(shortCircuitPair.type, lhsPair.type);
      return combineLhsAndRhsForLogicalOps(
          exprKind, lhsUnspecializedType, shortCircuitPair, rhsPair);
    } else {
      // Independently of the specializedType, && rhs is only analyzed when
      // lhs is truthy, and || rhs is only analyzed when lhs is falsy.
      JSType stopAfterLhsType = exprKind == Token.AND ? JSType.FALSY : JSType.TRUTHY;
      EnvTypePair shortCircuitPair =
          analyzeExprFwd(lhs, inEnv, JSType.UNKNOWN, stopAfterLhsType);
      EnvTypePair lhsPair = analyzeExprFwd(
          lhs, inEnv, JSType.UNKNOWN, stopAfterLhsType.negate());
      EnvTypePair rhsPair =
          analyzeExprFwd(rhs, lhsPair.env, requiredType, specializedType);
      JSType lhsUnspecializedType = JSType.join(shortCircuitPair.type, lhsPair.type);
      return combineLhsAndRhsForLogicalOps(
          exprKind, lhsUnspecializedType, shortCircuitPair, rhsPair);
    }
  }

  private EnvTypePair combineLhsAndRhsForLogicalOps(int logicalOp,
      JSType lhsUnspecializedType, EnvTypePair lhsPair, EnvTypePair rhsPair) {
    if (logicalOp == Token.OR) {
      if (lhsUnspecializedType.isAnyTruthyType()) {
        return lhsPair;
      }
      if (lhsUnspecializedType.isAnyFalsyType()) {
        return rhsPair;
      }
      return EnvTypePair.join(lhsPair, rhsPair);
    }
    Preconditions.checkState(logicalOp == Token.AND);
    if (lhsUnspecializedType.isAnyFalsyType()) {
      return lhsPair;
    }
    if (lhsUnspecializedType.isAnyTruthyType()) {
      return rhsPair;
    }
    return EnvTypePair.join(lhsPair, rhsPair);
  }

  private EnvTypePair analyzeIncDecFwd(
      Node expr, TypeEnv inEnv, JSType requiredType) {
    mayWarnAboutConst(expr);
    Node ch = expr.getFirstChild();
    if (ch.isGetProp() || ch.isGetElem() && ch.getLastChild().isString()) {
      // We prefer to analyze the child of INC/DEC one extra time here,
      // to putting the @const prop check in analyzePropAccessFwd.
      Node recv = ch.getFirstChild();
      String pname = ch.getLastChild().getString();
      EnvTypePair pair = analyzeExprFwd(recv, inEnv);
      JSType recvType = pair.type;
      if (mayWarnAboutConstProp(ch, recvType, new QualifiedName(pname))) {
        pair.type = requiredType;
        return pair;
      }
    }
    return analyzeUnaryNumFwd(expr, inEnv);
  }

  private EnvTypePair analyzeUnaryNumFwd(Node expr, TypeEnv inEnv) {
    // For inc and dec on a getprop, we don't want to create a property on
    // a struct by accident.
    // But we will get an inexistent-property warning, so we don't check
    // for structness separately here.
    Node child = expr.getFirstChild();
    EnvTypePair pair = analyzeExprFwd(child, inEnv, JSType.NUMBER);
    if (!commonTypes.isNumberScalarOrObj(pair.type)) {
      warnInvalidOperand(child, expr.getType(), JSType.NUMBER, pair.type);
    }
    pair.type = JSType.NUMBER;
    return pair;
  }

  private EnvTypePair analyzeInstanceofFwd(
      Node expr, TypeEnv inEnv, JSType specializedType) {
    Node obj = expr.getFirstChild();
    Node ctor = expr.getLastChild();
    EnvTypePair objPair, ctorPair;

    // First, evaluate ignoring the specialized context
    objPair = analyzeExprFwd(obj, inEnv);
    JSType objType = objPair.type;
    if (!objType.isTop()
        && !objType.isUnknown()
        && !objType.hasNonScalar()
        && !objType.hasTypeVariable()) {
      warnInvalidOperand(
          obj, Token.INSTANCEOF,
          "an object or a union type that includes an object",
          objPair.type);
    }
    ctorPair = analyzeExprFwd(ctor, objPair.env, commonTypes.topFunction());
    JSType ctorType = ctorPair.type;
    FunctionType ctorFunType = ctorType.getFunType();
    boolean mayBeConstructorFunction = ctorFunType != null
        && (ctorFunType.isLoose()
            || ctorFunType.isQmarkFunction()
            || ctorFunType.isSomeConstructorOrInterface());
    if (!(ctorType.isUnknown() || mayBeConstructorFunction)) {
      warnInvalidOperand(
          ctor, Token.INSTANCEOF, "a constructor function", ctorType);
    }
    if (ctorFunType == null
        || !ctorFunType.isUniqueConstructor()
        || (!specializedType.isTrueOrTruthy()
            && !specializedType.isFalseOrFalsy())) {
      ctorPair.type = JSType.BOOLEAN;
      return ctorPair;
    }

    // We are in a specialized context *and* we know the constructor type
    JSType instanceType = ctorFunType.getInstanceTypeOfCtor();
    JSType instanceSpecType = specializedType.isTrueOrTruthy()
        ? objType.specialize(instanceType)
        : objType.removeType(instanceType);
    if (!instanceSpecType.isBottom()) {
      objPair = analyzeExprFwd(obj, inEnv, JSType.UNKNOWN, instanceSpecType);
      ctorPair = analyzeExprFwd(ctor, objPair.env, commonTypes.topFunction());
    }
    ctorPair.type = JSType.BOOLEAN;
    return ctorPair;
  }

  private EnvTypePair analyzeAddFwd(Node expr, TypeEnv inEnv, JSType requiredType) {
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();
    JSType operandType = requiredType.isNumber() ? JSType.NUMBER : JSType.UNKNOWN;
    EnvTypePair lhsPair = analyzeExprFwd(lhs, inEnv, operandType);
    EnvTypePair rhsPair = analyzeExprFwd(rhs, lhsPair.env, operandType);
    JSType lhsType = lhsPair.type;
    JSType rhsType = rhsPair.type;
    if (lhsType.isString() || rhsType.isString()) {
      // Return early and don't warn, since '' + expr is used for type coercions
      rhsPair.type = JSType.STRING;
      return rhsPair;
    }
    if (!commonTypes.isNumStrScalarOrObj(lhsType)) {
      warnInvalidOperand(lhs, expr.getType(), JSType.NUM_OR_STR, lhsType);
    }
    if (!commonTypes.isNumStrScalarOrObj(rhsType)) {
      warnInvalidOperand(rhs, expr.getType(), JSType.NUM_OR_STR, rhsType);
    }
    return new EnvTypePair(rhsPair.env, JSType.plus(lhsType, rhsType));
  }

  private EnvTypePair analyzeBinaryNumericOpFwd(Node expr, TypeEnv inEnv) {
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();
    EnvTypePair lhsPair = analyzeExprFwd(lhs, inEnv, JSType.NUMBER);
    EnvTypePair rhsPair = analyzeExprFwd(rhs, lhsPair.env, JSType.NUMBER);
    if (!commonTypes.isNumberScalarOrObj(lhsPair.type)) {
      warnInvalidOperand(lhs, expr.getType(), JSType.NUMBER, lhsPair.type);
    }
    if (!commonTypes.isNumberScalarOrObj(rhsPair.type)) {
      warnInvalidOperand(rhs, expr.getType(), JSType.NUMBER, rhsPair.type);
    }
    rhsPair.type = JSType.NUMBER;
    return rhsPair;
  }

  private EnvTypePair analyzeAssignFwd(
      Node expr, TypeEnv inEnv, JSType requiredType, JSType specializedType) {
    if (expr.getBooleanProp(Node.ANALYZED_DURING_GTI)) {
      expr.removeProp(Node.ANALYZED_DURING_GTI);
      // No need to set a type on the assignment expression
      return new EnvTypePair(inEnv, requiredType);
    }
    mayWarnAboutConst(expr);
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();
    if (lhs.getBooleanProp(Node.ANALYZED_DURING_GTI)) {
      lhs.removeProp(Node.ANALYZED_DURING_GTI);
      JSType declType = markAndGetTypeOfPreanalyzedNode(lhs, inEnv, true);
      if (rhs.matchesQualifiedName(ABSTRACT_METHOD_NAME)) {
        return new EnvTypePair(inEnv, requiredType);
      }
      EnvTypePair rhsPair = analyzeExprFwd(rhs, inEnv, declType);
      if (!rhsPair.type.isSubtypeOf(declType)
          && !NodeUtil.isPrototypeAssignment(lhs)) {
        warnings.add(JSError.make(expr, MISTYPED_ASSIGN_RHS,
                declType.toString(), rhsPair.type.toString()));
      }
      return rhsPair;
    }
    LValueResultFwd lvalue = analyzeLValueFwd(lhs, inEnv, requiredType);
    JSType declType = lvalue.declType;
    EnvTypePair rhsPair =
        analyzeExprFwd(rhs, lvalue.env, requiredType, specializedType);
    if (declType != null && !rhsPair.type.isSubtypeOf(declType)) {
      warnings.add(JSError.make(expr, MISTYPED_ASSIGN_RHS,
              declType.toString(), rhsPair.type.toString()));
    } else {
      rhsPair.env = updateLvalueTypeInEnv(rhsPair.env, lhs, lvalue.ptr, rhsPair.type);
    }
    return rhsPair;
  }

  private EnvTypePair analyzeAssignAddFwd(
      Node expr, TypeEnv inEnv, JSType requiredType) {
    mayWarnAboutConst(expr);
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();
    JSType lhsReqType =
        specializeKeep2ndWhenBottom(requiredType, JSType.NUM_OR_STR);
    LValueResultFwd lvalue = analyzeLValueFwd(lhs, inEnv, lhsReqType);
    JSType lhsType = lvalue.type;
    if (!lhsType.isSubtypeOf(JSType.NUM_OR_STR)) {
      warnInvalidOperand(lhs, Token.ASSIGN_ADD, JSType.NUM_OR_STR, lhsType);
    }
    // if lhs is a string, rhs can still be a number
    JSType rhsReqType = lhsType.isNumber() ? JSType.NUMBER : JSType.NUM_OR_STR;
    EnvTypePair pair = analyzeExprFwd(rhs, lvalue.env, rhsReqType);
    if (!pair.type.isSubtypeOf(rhsReqType)) {
      warnInvalidOperand(rhs, Token.ASSIGN_ADD, rhsReqType, pair.type);
    }
    return pair;
  }

  private EnvTypePair analyzeAssignNumericOpFwd(Node expr, TypeEnv inEnv) {
    mayWarnAboutConst(expr);
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();
    LValueResultFwd lvalue = analyzeLValueFwd(lhs, inEnv, JSType.NUMBER);
    JSType lhsType = lvalue.type;
    boolean lhsWarned = false;
    if (!commonTypes.isNumberScalarOrObj(lhsType)) {
      warnInvalidOperand(lhs, expr.getType(), JSType.NUMBER, lhsType);
      lhsWarned = true;
    }
    EnvTypePair pair = analyzeExprFwd(rhs, lvalue.env, JSType.NUMBER);
    if (!commonTypes.isNumberScalarOrObj(pair.type)) {
      warnInvalidOperand(rhs, expr.getType(), JSType.NUMBER, pair.type);
    }
    if (!lhsWarned) {
      pair.env = updateLvalueTypeInEnv(pair.env, lhs, lvalue.ptr, JSType.NUMBER);
    }
    pair.type = JSType.NUMBER;
    return pair;
  }

  private EnvTypePair analyzeLtGtFwd(Node expr, TypeEnv inEnv) {
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();
    EnvTypePair lhsPair = analyzeExprFwd(lhs, inEnv);
    EnvTypePair rhsPair = analyzeExprFwd(rhs, lhsPair.env);
    // The type of either side can be specialized based on the other side
    if (lhsPair.type.isScalar() && !rhsPair.type.isScalar()) {
      rhsPair = analyzeExprFwd(rhs, lhsPair.env, lhsPair.type);
    } else if (rhsPair.type.isScalar()) {
      lhsPair = analyzeExprFwd(lhs, inEnv, rhsPair.type);
      rhsPair = analyzeExprFwd(rhs, lhsPair.env, rhsPair.type);
    }
    JSType lhsType = lhsPair.type;
    JSType rhsType = rhsPair.type;
    if (!lhsType.isSubtypeOf(rhsType) && !rhsType.isSubtypeOf(lhsType)
        && !(lhsType.isBoolean() && rhsType.isBoolean())) {
      warnInvalidOperand(expr, expr.getType(), "matching types",
          lhsType + ", " + rhsType);
    }
    rhsPair.type = JSType.BOOLEAN;
    return rhsPair;
  }

  private EnvTypePair analyzeHookFwd(
      Node expr, TypeEnv inEnv, JSType requiredType, JSType specializedType) {
    Node cond = expr.getFirstChild();
    Node thenBranch = cond.getNext();
    Node elseBranch = thenBranch.getNext();
    TypeEnv trueEnv =
        analyzeExprFwd(cond, inEnv, JSType.UNKNOWN, JSType.TRUTHY).env;
    TypeEnv falseEnv =
        analyzeExprFwd(cond, inEnv, JSType.UNKNOWN, JSType.FALSY).env;
    EnvTypePair thenPair =
        analyzeExprFwd(thenBranch, trueEnv, requiredType, specializedType);
    EnvTypePair elsePair =
        analyzeExprFwd(elseBranch, falseEnv, requiredType, specializedType);
    return EnvTypePair.join(thenPair, elsePair);
  }

  private EnvTypePair analyzeCallNewFwd(
      Node expr, TypeEnv inEnv, JSType requiredType, JSType specializedType) {
    if (isPropertyTestCall(expr)) {
      return analyzePropertyTestCallFwd(expr, inEnv, specializedType);
    }
    Node callee = expr.getFirstChild();
    if (isFunctionBind(callee, inEnv, true)) {
      return analyzeFunctionBindFwd(expr, inEnv);
    }
    AssertionFunctionSpec assertionFunctionSpec =
        assertionFunctionsMap.get(callee.getQualifiedName());
    if (assertionFunctionSpec != null) {
      return analyzeAssertionCall(expr, inEnv, assertionFunctionSpec);
    }
    EnvTypePair calleePair = analyzeExprFwd(callee, inEnv, commonTypes.topFunction());
    TypeEnv envAfterCallee = calleePair.env;
    calleePair = mayWarnAboutNullableReferenceAndTighten(
        callee, calleePair.type, null, envAfterCallee);
    JSType calleeType = calleePair.type;
    if (calleeType.isBottom() || !calleeType.isSubtypeOf(commonTypes.topFunction())) {
      warnings.add(JSError.make(
          expr, NOT_CALLABLE, calleeType.toString()));
    }
    FunctionType funType = calleeType.getFunTypeIfSingletonObj();
    if (funType == null
        || funType.isTopFunction() || funType.isQmarkFunction()) {
      return analyzeCallNodeArgsFwdWhenError(expr, envAfterCallee);
    } else if (funType.isLoose()) {
      return analyzeLooseCallNodeFwd(expr, envAfterCallee, requiredType);
    } else if (expr.isCall()
        && funType.isSomeConstructorOrInterface()
        && (funType.getReturnType().isUnknown()
            || funType.getReturnType().isUndefined())) {
      warnings.add(JSError.make(expr, CONSTRUCTOR_NOT_CALLABLE, funType.toString()));
      return analyzeCallNodeArgsFwdWhenError(expr, envAfterCallee);
    } else if (expr.isNew()
        && (!funType.isSomeConstructorOrInterface()
            || funType.isInterfaceDefinition())) {
      warnings.add(JSError.make(expr, NOT_A_CONSTRUCTOR, funType.toString()));
      return analyzeCallNodeArgsFwdWhenError(expr, envAfterCallee);
    }
    int maxArity = funType.getMaxArity();
    int minArity = funType.getMinArity();
    int numArgs = expr.getChildCount() - 1;
    if (numArgs < minArity || numArgs > maxArity) {
      warnings.add(JSError.make(
          expr, WRONG_ARGUMENT_COUNT,
          getReadableCalleeName(callee),
          Integer.toString(numArgs), Integer.toString(minArity),
          " and at most " + maxArity));
      return analyzeCallNodeArgsFwdWhenError(expr, envAfterCallee);
    }
    FunctionType origFunType = funType; // save for later
    if (funType.isGeneric()) {
      Map<String, JSType> typeMap = calcTypeInstantiationFwd(
          expr,
          callee.isGetProp() ? callee.getFirstChild() : null,
          expr.getSecondChild(), funType, envAfterCallee);
      funType = funType.instantiateGenerics(typeMap);
      println("Instantiated function type: " + funType);
    }
    // argTypes collects types of actuals for deferred checks.
    List<JSType> argTypes = new ArrayList<>();
    TypeEnv tmpEnv = analyzeCallNodeArgumentsFwd(
        expr, expr.getSecondChild(), funType, argTypes, envAfterCallee);
    if (callee.isName()) {
      String calleeName = callee.getString();
      if (currentScope.isKnownFunction(calleeName)
          && !currentScope.isExternalFunction(calleeName)) {
        // Local function definitions will be type-checked more
        // exactly using their summaries, and don't need deferred checks
        if (currentScope.isLocalFunDef(calleeName)) {
          tmpEnv = collectTypesForFreeVarsFwd(callee, tmpEnv);
        } else if (!origFunType.isGeneric()) {
          JSType expectedRetType = requiredType;
          println("Updating deferred check with ret: ", expectedRetType,
              " and args: ", argTypes);
          DeferredCheck dc;
          if (expr.isCall()) {
            dc = deferredChecks.get(expr);
            if (dc != null) {
              dc.updateReturn(expectedRetType);
            } else {
              // The backward analysis of a function is skipped when all
              // variables, including outer vars, are declared.
              // So, we check that dc is null iff bwd was skipped.
              Preconditions.checkState(
                  !currentScope.hasUndeclaredFormalsOrOuters(),
                  "No deferred check created in backward direction for %s",
                  expr);
            }
          } else { // call to constructor
            dc = new DeferredCheck(expr, null,
                currentScope, currentScope.getScope(calleeName));
            deferredChecks.put(expr, dc);
          }
          if (dc != null) {
            dc.updateArgTypes(argTypes);
          }
        }
      }
    }
    JSType retType = expr.isNew() ? funType.getThisType() : funType.getReturnType();
    if (retType.isSubtypeOf(requiredType)) {
      retType = retType.specialize(specializedType);
    }
    return new EnvTypePair(tmpEnv, retType);
  }

  private EnvTypePair analyzeFunctionBindFwd(Node call, TypeEnv inEnv) {
    Preconditions.checkArgument(call.isCall());
    Bind bindComponents = this.convention.describeFunctionBind(call, true, false);
    Node boundFunNode = bindComponents.target;
    EnvTypePair pair = analyzeExprFwd(boundFunNode, inEnv);
    TypeEnv env = pair.env;
    FunctionType boundFunType = pair.type.getFunTypeIfSingletonObj();
    if (!pair.type.isSubtypeOf(commonTypes.topFunction())) {
      warnings.add(JSError.make(boundFunNode, GOOG_BIND_EXPECTS_FUNCTION, pair.type.toString()));
    }
    // For some function types, we don't know enough to handle .bind specially.
    if (boundFunType == null
        || boundFunType.isTopFunction()
        || boundFunType.isQmarkFunction()
        || boundFunType.isLoose()) {
      return analyzeCallNodeArgsFwdWhenError(call, env);
    }
    if (boundFunType.isSomeConstructorOrInterface()) {
      warnings.add(JSError.make(call, CANNOT_BIND_CTOR));
      return new EnvTypePair(env, JSType.UNKNOWN);
    }
    // Check if the receiver argument is there
    if (NodeUtil.isGoogBind(call.getFirstChild()) && call.getChildCount() <= 2
        || !NodeUtil.isGoogPartial(call.getFirstChild()) && call.getChildCount() == 1) {
      warnings.add(JSError.make(
          call, WRONG_ARGUMENT_COUNT,
          getReadableCalleeName(call.getFirstChild()),
          "0", "1", ""));
    }
    // Check that there are not too many of the other arguments
    int maxArity = boundFunType.hasRestFormals()
        ? Integer.MAX_VALUE : boundFunType.getMaxArity();
    int numArgs = bindComponents.getBoundParameterCount();
    if (numArgs > maxArity) {
      warnings.add(JSError.make(
          call, WRONG_ARGUMENT_COUNT,
          getReadableCalleeName(call.getFirstChild()),
          Integer.toString(numArgs), "0",
          " and at most " + maxArity));
      return analyzeCallNodeArgsFwdWhenError(call, inEnv);
    }

    // If the bound function is polymorphic, we only support the case where we
    // can completely calculate the type instantiation at the .bind call site.
    // We don't support splitting the instantiation between call sites.
    //
    Node receiver = bindComponents.thisValue;
    if (boundFunType.isGeneric()) {
      Map<String, JSType> typeMap = calcTypeInstantiationFwd(
          call, receiver, bindComponents.parameters, boundFunType, env);
      boundFunType = boundFunType.instantiateGenerics(typeMap);
    }
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    if (receiver != null) {// receiver is null for goog.partial
      JSType reqThisType = boundFunType.getThisType();
      if (reqThisType == null || boundFunType.isSomeConstructorOrInterface()) {
        reqThisType = JSType.join(JSType.NULL, JSType.TOP_OBJECT);
      }
      pair = analyzeExprFwd(receiver, env, reqThisType);
      env = pair.env;
      if (!pair.type.isSubtypeOf(reqThisType)) {
        warnings.add(JSError.make(call, INVALID_THIS_TYPE_IN_BIND,
                pair.type.toString(), reqThisType.toString()));
      }
    }

    // We're passing an arraylist but don't do deferred checks for bind.
    env = analyzeCallNodeArgumentsFwd(call, bindComponents.parameters,
        boundFunType, new ArrayList<JSType>(), env);
    // For any formal not bound here, add it to the resulting function type.
    for (int j = numArgs; j < boundFunType.getMaxArityWithoutRestFormals(); j++) {
      JSType formalType = boundFunType.getFormalType(j);
      if (boundFunType.isRequiredArg(j)) {
        builder.addReqFormal(formalType);
      } else {
        builder.addOptFormal(formalType);
      }
    }
    if (boundFunType.hasRestFormals()) {
      builder.addRestFormals(boundFunType.getRestFormalsType());
    }
    return new EnvTypePair(env, commonTypes.fromFunctionType(
        builder.addRetType(boundFunType.getReturnType()).buildFunction()));
  }

  private TypeEnv analyzeCallNodeArgumentsFwd(Node call, Node firstArg,
      FunctionType funType, List<JSType> argTypesForDeferredCheck,
      TypeEnv inEnv) {
    TypeEnv env = inEnv;
    Node arg = firstArg;
    int i = 0;
    while (arg != null) {
      JSType formalType = funType.getFormalType(i);
      Preconditions.checkState(!formalType.isBottom());
      EnvTypePair pair = analyzeExprFwd(arg, env, formalType);
      JSType argTypeForDeferredCheck = pair.type;
      // Allow passing undefined for an optional argument.
      if (funType.isOptionalArg(i) && pair.type.equals(JSType.UNDEFINED)) {
        argTypeForDeferredCheck = null; // No deferred check needed.
      } else if (!pair.type.isSubtypeOf(formalType)) {
        String fnName = getReadableCalleeName(call.getFirstChild());
        warnings.add(JSError.make(arg, INVALID_ARGUMENT_TYPE,
                Integer.toString(i + 1), fnName,
                formalType.toString(), pair.type.toString()));
        argTypeForDeferredCheck = null; // No deferred check needed.
      }
      argTypesForDeferredCheck.add(argTypeForDeferredCheck);
      env = pair.env;
      arg = arg.getNext();
      i++;
    }
    return env;
  }

  private EnvTypePair analyzeAssertionCall(
      Node callNode, TypeEnv env, AssertionFunctionSpec assertionFunctionSpec) {
    Node firstParam = callNode.getSecondChild();
    if (firstParam == null) {
      return new EnvTypePair(env, JSType.UNKNOWN);
    }
    Node assertedNode = assertionFunctionSpec.getAssertedParam(firstParam);
    if (assertedNode == null) {
      return new EnvTypePair(env, JSType.UNKNOWN);
    }
    JSType assertedType = assertionFunctionSpec.getAssertedNewType(callNode, currentScope);
    if (assertedType.isUnknown()) {
      warnings.add(JSError.make(callNode, NewTypeInference.UNKNOWN_ASSERTION_TYPE));
    }
    EnvTypePair pair = analyzeExprFwd(assertedNode, env, JSType.UNKNOWN, assertedType);
    if (pair.type.isBottom()) {
      JSType t = analyzeExprFwd(assertedNode, env).type.substituteGenericsWithUnknown();
      if (t.isSubtypeOf(assertedType)) {
        pair.type = t;
      } else {
        warnings.add(JSError.make(assertedNode, NewTypeInference.ASSERT_FALSE));
        pair.type = JSType.UNKNOWN;
        pair.env = env;
      }
    }
    return pair;
  }

  private EnvTypePair analyzeGetElemFwd(
      Node expr, TypeEnv inEnv, JSType requiredType, JSType specializedType) {
    Node receiver = expr.getFirstChild();
    Node index = expr.getLastChild();
    JSType reqObjType = pickReqObjType(expr);
    EnvTypePair pair = analyzeExprFwd(receiver, inEnv, reqObjType);
    pair = mayWarnAboutNullableReferenceAndTighten(receiver, pair.type, null, pair.env);
    JSType recvType = pair.type.autobox();
    if (!mayWarnAboutNonObject(receiver, recvType, specializedType)
        && !mayWarnAboutStructPropAccess(receiver, recvType)) {
      JSType indexType = recvType.getIndexType();
      if (indexType != null) {
        pair = analyzeExprFwd(
            index, pair.env, indexType.isBottom() ? JSType.UNKNOWN : indexType);
        mayWarnAboutBadIObjectIndex(index, recvType, pair.type, indexType);
        pair.type = recvType.getIndexedType();
        return pair;
      } else if (index.isString()) {
        return analyzePropAccessFwd(receiver, index.getString(), inEnv,
            requiredType, specializedType);
      }
    }
    pair = analyzeExprFwd(index, pair.env);
    pair.type = JSType.UNKNOWN;
    return pair;
  }

  private EnvTypePair analyzeInFwd(
      Node expr, TypeEnv inEnv, JSType specializedType) {
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();
    JSType reqObjType = pickReqObjType(expr);
    EnvTypePair pair;

    pair = analyzeExprFwd(lhs, inEnv, JSType.NUM_OR_STR);
    if (!pair.type.isSubtypeOf(JSType.NUM_OR_STR)) {
      warnInvalidOperand(lhs, Token.IN, JSType.NUM_OR_STR, pair.type);
    }
    pair = analyzeExprFwd(rhs, pair.env, reqObjType);
    if (!pair.type.isSubtypeOf(JSType.TOP_OBJECT)) {
      warnInvalidOperand(rhs, Token.IN, "Object", pair.type);
      pair.type = JSType.BOOLEAN;
      return pair;
    }
    if (pair.type.isStruct()) {
      warnings.add(JSError.make(rhs, IN_USED_WITH_STRUCT));
      pair.type = JSType.BOOLEAN;
      return pair;
    }

    JSType resultType = JSType.BOOLEAN;
    if (lhs.isString()) {
      QualifiedName pname = new QualifiedName(lhs.getString());
      if (specializedType.isTrueOrTruthy()) {
        pair = analyzeExprFwd(rhs, inEnv, reqObjType,
            reqObjType.withPropertyRequired(pname.getLeftmostName()));
        resultType = JSType.TRUE_TYPE;
      } else if (specializedType.isFalseOrFalsy()) {
        pair = analyzeExprFwd(rhs, inEnv, reqObjType);
        // If the rhs is a loose object, we won't warn about missing
        // properties, despite removing the type here.
        // The only way to have that warning would be to keep track of props
        // that a loose object *cannot* have; but the implementation cost
        // is probably not worth it.
        pair = analyzeExprFwd(
            rhs, inEnv, reqObjType, pair.type.withoutProperty(pname));
        resultType = JSType.FALSE_TYPE;
      }
    }
    pair.type = resultType;
    return pair;
  }

  private EnvTypePair analyzeArrayLitFwd(Node expr, TypeEnv inEnv) {
    TypeEnv env = inEnv;
    JSType elementType = JSType.BOTTOM;
    for (Node arrayElm = expr.getFirstChild(); arrayElm != null;
         arrayElm = arrayElm.getNext()) {
      EnvTypePair pair = analyzeExprFwd(arrayElm, env);
      env = pair.env;
      elementType = JSType.join(elementType, pair.type);
    }
    if (elementType.isBottom()) {
      elementType = JSType.UNKNOWN;
    }
    return new EnvTypePair(env, commonTypes.getArrayInstance(elementType));
  }

  private EnvTypePair analyzeCastFwd(Node expr, TypeEnv inEnv) {
    EnvTypePair pair = analyzeExprFwd(expr.getFirstChild(), inEnv);
    JSType fromType = pair.type;
    JSType toType = symbolTable.getCastType(expr);
    if (!JSType.haveCommonSubtype(fromType, toType) && !fromType.hasTypeVariable()) {
      warnings.add(JSError.make(expr, INVALID_CAST, fromType.toString(), toType.toString()));
    }
    pair.type = toType;
    return pair;
  }

  private EnvTypePair analyzeCallNodeArgsFwdWhenError(
      Node callNode, TypeEnv inEnv) {
    TypeEnv env = inEnv;
    for (Node arg = callNode.getSecondChild(); arg != null; arg = arg.getNext()) {
      env = analyzeExprFwd(arg, env).env;
    }
    return new EnvTypePair(env, JSType.UNKNOWN);
  }

  private EnvTypePair analyzeStrictComparisonFwd(int comparisonOp,
      Node lhs, Node rhs, TypeEnv inEnv, JSType specializedType) {
    if (specializedType.isTrueOrTruthy() || specializedType.isFalseOrFalsy()) {
      if (lhs.isTypeOf()) {
        return analyzeSpecializedTypeof(
            lhs, rhs, comparisonOp, inEnv, specializedType);
      } else if (rhs.isTypeOf()) {
        return analyzeSpecializedTypeof(
            rhs, lhs, comparisonOp, inEnv, specializedType);
      } else if (isGoogTypeof(lhs)) {
        return analyzeGoogTypeof(lhs, rhs, inEnv, specializedType);
      } else if (isGoogTypeof(rhs)) {
        return analyzeGoogTypeof(rhs, lhs, inEnv, specializedType);
      }
    }

    EnvTypePair lhsPair = analyzeExprFwd(lhs, inEnv);
    EnvTypePair rhsPair = analyzeExprFwd(rhs, lhsPair.env);
    // This env may contain types that have been tightened after nullable deref.
    TypeEnv preciseEnv = rhsPair.env;

    if ((comparisonOp == Token.SHEQ && specializedType.isTrueOrTruthy())
        || (comparisonOp == Token.SHNE && specializedType.isFalseOrFalsy())) {
      lhsPair = analyzeExprFwd(lhs, preciseEnv,
          JSType.UNKNOWN, lhsPair.type.specialize(rhsPair.type));
      rhsPair = analyzeExprFwd(rhs, lhsPair.env,
          JSType.UNKNOWN, rhsPair.type.specialize(lhsPair.type));
    } else if ((comparisonOp == Token.SHEQ && specializedType.isFalseOrFalsy()) ||
        (comparisonOp == Token.SHNE && specializedType.isTrueOrTruthy())) {
      JSType lhsType = lhsPair.type;
      JSType rhsType = rhsPair.type;
      if (lhsType.isNullOrUndef()) {
        rhsType = rhsType.removeType(lhsType);
      } else if (rhsType.isNullOrUndef()) {
        lhsType = lhsType.removeType(rhsType);
      }
      lhsPair = analyzeExprFwd(lhs, preciseEnv, JSType.UNKNOWN, lhsType);
      rhsPair = analyzeExprFwd(rhs, lhsPair.env, JSType.UNKNOWN, rhsType);
    }
    rhsPair.type = JSType.BOOLEAN;
    return rhsPair;
  }

  private EnvTypePair analyzeSpecializedTypeof(Node typeof, Node typeString,
      int comparisonOp, TypeEnv inEnv, JSType specializedType) {
    EnvTypePair pair;
    Node typeofRand = typeof.getFirstChild();
    JSType comparedType = getTypeFromString(typeString);
    checkInvalidTypename(typeString);
    if (comparedType.isUnknown()) {
      pair = analyzeExprFwd(typeofRand, inEnv);
      pair = analyzeExprFwd(typeString, pair.env);
    } else if ((specializedType.isTrueOrTruthy()
            && (comparisonOp == Token.SHEQ || comparisonOp == Token.EQ))
        || (specializedType.isFalseOrFalsy()
            && (comparisonOp == Token.SHNE || comparisonOp == Token.NE))) {
      pair = analyzeExprFwd(typeofRand, inEnv, JSType.UNKNOWN, comparedType);
    } else {
      pair = analyzeExprFwd(typeofRand, inEnv);
      JSType rmType = pair.type.removeType(comparedType);
      if (!rmType.isBottom()) {
        pair = analyzeExprFwd(typeofRand, inEnv, JSType.UNKNOWN, rmType);
      }
    }
    pair.type = specializedType.toBoolean();
    return pair;
  }

  private JSType getTypeFromString(Node typeString) {
    if (!typeString.isString()) {
      return JSType.UNKNOWN;
    }
    switch (typeString.getString()) {
      case "number":
        return JSType.NUMBER;
      case "string":
        return JSType.STRING;
      case "boolean":
        return JSType.BOOLEAN;
      case "undefined":
        return JSType.UNDEFINED;
      case "function":
        return commonTypes.looseTopFunction();
      case "object":
        return JSType.join(JSType.NULL, JSType.TOP_OBJECT);
      default:
        return JSType.UNKNOWN;
    }
  }

  private void checkInvalidTypename(Node typeString) {
    if (!typeString.isString()) {
      return;
    }
    String typeName = typeString.getString();
    switch (typeName) {
      case "number":
      case "string":
      case "boolean":
      case "undefined":
      case "function":
      case "object":
      case "unknown": // IE-specific type name
        break;
      default:
        warnings.add(JSError.make(typeString, UNKNOWN_TYPEOF_VALUE, typeName));
    }
  }

  private Map<String, JSType> calcTypeInstantiationFwd(
      Node callNode, Node receiver, Node firstArg, FunctionType funType, TypeEnv typeEnv) {
    return calcTypeInstantiation(callNode, receiver, firstArg, funType, typeEnv, true);
  }

  private Map<String, JSType> calcTypeInstantiationBwd(
      Node callNode, FunctionType funType, TypeEnv typeEnv) {
    return calcTypeInstantiation(
        callNode, null, callNode.getSecondChild(), funType, typeEnv, false);
  }

  /**
   * We don't use the requiredType of the context to unify with the return
   * type. There are several difficulties:
   * 1) A polymorphic function is allowed to return ANY subtype of the
   *    requiredType, so we would need to use a heuristic to determine the type
   *    to unify with.
   * 2) It's hard to give good error messages in cases like: id('str') - 5
   *    We want an invalid-operand-type, not a not-unique-instantiation.
   *
   *
   * We don't take the arg evaluation order into account during instantiation.
   *
   *
   * When calculating the instantiation, when do we use the receiver type?
   * See the following snippet:
   * /**
   *  * @constructor
   *  * @template T
   *  * @param {T} x
   *  * /
   * function Foo(x) {}
   * /**
   *  * @template T
   *  * @param {T} x
   *  * /
   * Foo.prototype.f = function(x) {};
   * Foo.prototype.f.bind(new Foo(123), 'asdf');
   *
   * Here, the receiver type of f is Foo<T>, but the T is the class's T,
   * not the T of f's template declaration.
   * OTOH, if f had a @this annotation that contained T, T would refer to
   * f's T. There is no way of knowing what's the scope of the type variables
   * in the receiver of the function type.
   * But when THIS comes from the class, it is always a singleton object. So,
   * we use a heuristic: if THIS is not a singleton obj, we know it comes from
   * @this, and we use it for the instantiation.
   */
  private ImmutableMap<String, JSType> calcTypeInstantiation(
      Node callNode, Node receiver, Node firstArg,
      FunctionType funType, TypeEnv typeEnv, boolean isFwd) {
    Preconditions.checkState(receiver == null || isFwd);
    List<String> typeParameters = funType.getTypeParameters();
    Multimap<String, JSType> typeMultimap = LinkedHashMultimap.create();
    JSType funRecvType = funType.getThisType();
    if (receiver != null && funRecvType != null && !funRecvType.isSingletonObj()) {
      EnvTypePair pair = analyzeExprFwd(receiver, typeEnv);
      unifyWithSubtypeWarnIfFail(funRecvType, pair.type, typeParameters,
          typeMultimap, receiver, isFwd);
      typeEnv = pair.env;
    }
    Node arg = firstArg;
    int i = 0;
    while (arg != null) {
      EnvTypePair pair =
          isFwd ? analyzeExprFwd(arg, typeEnv) : analyzeExprBwd(arg, typeEnv);
      unifyWithSubtypeWarnIfFail(funType.getFormalType(i), pair.type,
          typeParameters, typeMultimap, arg, isFwd);
      arg = arg.getNext();
      typeEnv = pair.env;
      i++;
    }
    ImmutableMap.Builder<String, JSType> builder = ImmutableMap.builder();
    for (String typeParam : typeParameters) {
      Collection<JSType> types = typeMultimap.get(typeParam);
      if (types.size() > 1) {
        if (isFwd) {
          warnings.add(JSError.make(
              callNode, NOT_UNIQUE_INSTANTIATION,
              Integer.toString(types.size()),
              UniqueNameGenerator.getOriginalName(typeParam),
              types.toString(),
              funType.toString()));
        }
        builder.put(typeParam, JSType.UNKNOWN);
      } else if (types.size() == 1) {
        JSType t = Iterables.getOnlyElement(types);
        builder.put(typeParam, t.isBottom() ? JSType.UNKNOWN : t);
      } else {
        // Put ? for any uninstantiated type variables
        builder.put(typeParam, JSType.UNKNOWN);
      }
    }
    return builder.build();
  }

  private void unifyWithSubtypeWarnIfFail(JSType genericType, JSType concreteType,
      List<String> typeParameters, Multimap<String, JSType> typeMultimap,
      Node toWarnOn, boolean isFwd) {
    if (!genericType.unifyWith(concreteType, typeParameters, typeMultimap) && isFwd) {
      // Unification may fail b/c of types irrelevant to generics, eg,
      // number vs string.
      // In this case, don't warn here; we'll show invalid-arg-type later.
      JSType afterInstantiation = genericType.substituteGenericsWithUnknown();
      if (!genericType.equals(afterInstantiation)
          && concreteType.isSubtypeOf(afterInstantiation)) {
        warnings.add(JSError.make(toWarnOn, FAILED_TO_UNIFY,
                genericType.toString(), concreteType.toString()));
      }
    }
  }

  private EnvTypePair analyzeNonStrictComparisonFwd(
      Node expr, TypeEnv inEnv, JSType specializedType) {
    int tokenType = expr.getType();
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();

    if (specializedType.isTrueOrTruthy() || specializedType.isFalseOrFalsy()) {
      if (lhs.isTypeOf()) {
        return analyzeSpecializedTypeof(
            lhs, rhs, tokenType, inEnv, specializedType);
      } else if (rhs.isTypeOf()) {
        return analyzeSpecializedTypeof(
            rhs, lhs, tokenType, inEnv, specializedType);
      } else if (isGoogTypeof(lhs)) {
        return analyzeGoogTypeof(lhs, rhs, inEnv, specializedType);
      } else if (isGoogTypeof(rhs)) {
        return analyzeGoogTypeof(rhs, lhs, inEnv, specializedType);
      }
    }

    EnvTypePair lhsPair = analyzeExprFwd(lhs, inEnv);
    EnvTypePair rhsPair = analyzeExprFwd(rhs, lhsPair.env);
    // This env may contain types that have been tightened after nullable deref.
    TypeEnv preciseEnv = rhsPair.env;
    JSType lhsType = lhsPair.type;
    JSType rhsType = rhsPair.type;

    if (tokenType == Token.EQ && specializedType.isTrueOrTruthy()
        || tokenType == Token.NE && specializedType.isFalseOrFalsy()) {
      if (lhsType.isNullOrUndef()) {
        rhsPair = analyzeExprFwd(
            rhs, preciseEnv, JSType.UNKNOWN, JSType.NULL_OR_UNDEF);
      } else if (rhsType.isNullOrUndef()) {
        lhsPair = analyzeExprFwd(
            lhs, preciseEnv, JSType.UNKNOWN, JSType.NULL_OR_UNDEF);
        rhsPair = analyzeExprFwd(rhs, lhsPair.env);
      } else if (!JSType.NULL.isSubtypeOf(lhsType) && !JSType.UNDEFINED.isSubtypeOf(lhsType)) {
        rhsType = rhsType.removeType(JSType.NULL_OR_UNDEF);
        rhsPair = analyzeExprFwd(rhs, preciseEnv, JSType.UNKNOWN, rhsType);
      } else if (!JSType.NULL.isSubtypeOf(rhsType) && !JSType.UNDEFINED.isSubtypeOf(rhsType)) {
        lhsType = lhsType.removeType(JSType.NULL_OR_UNDEF);
        lhsPair = analyzeExprFwd(lhs, preciseEnv, JSType.UNKNOWN, lhsType);
        rhsPair = analyzeExprFwd(rhs, lhsPair.env);
      }
    } else if (tokenType == Token.EQ && specializedType.isFalseOrFalsy()
        || tokenType == Token.NE && specializedType.isTrueOrTruthy()) {
      if (lhsType.isNullOrUndef()) {
        rhsType = rhsType.removeType(JSType.NULL_OR_UNDEF);
        rhsPair = analyzeExprFwd(rhs, preciseEnv, JSType.UNKNOWN, rhsType);
      } else if (rhsType.isNullOrUndef()) {
        lhsType = lhsType.removeType(JSType.NULL_OR_UNDEF);
        lhsPair = analyzeExprFwd(lhs, preciseEnv, JSType.UNKNOWN, lhsType);
        rhsPair = analyzeExprFwd(rhs, lhsPair.env);
      }
    }
    rhsPair.type = JSType.BOOLEAN;
    return rhsPair;
  }

  private EnvTypePair analyzeObjLitFwd(
      Node objLit, TypeEnv inEnv, JSType requiredType, JSType specializedType) {
    if (NodeUtil.isEnumDecl(objLit.getParent())) {
      return analyzeEnumObjLitFwd(objLit, inEnv, requiredType);
    }
    JSDocInfo jsdoc = objLit.getJSDocInfo();
    boolean isStruct = jsdoc != null && jsdoc.makesStructs();
    boolean isDict = jsdoc != null && jsdoc.makesDicts();
    TypeEnv env = inEnv;
    JSType result = pickReqObjType(objLit);
    for (Node prop : objLit.children()) {
      if (isStruct && prop.isQuotedString()) {
        warnings.add(JSError.make(prop, ILLEGAL_OBJLIT_KEY, "struct"));
      } else if (isDict && !prop.isQuotedString()) {
        warnings.add(JSError.make(prop, ILLEGAL_OBJLIT_KEY, "dict"));
      }
      String pname = NodeUtil.getObjectLitKeyName(prop);
      // We can't assign to a getter to change its value.
      // We can't do a prop access on a setter.
      // So, we don't associate pname with a getter/setter.
      // We add a property with a name that's weird enough to hopefully avoid
      // an accidental clash.
      if (prop.isGetterDef() || prop.isSetterDef()) {
        EnvTypePair pair = analyzeExprFwd(prop.getFirstChild(), env);
        FunctionType funType = pair.type.getFunType();
        Preconditions.checkNotNull(funType);
        String specialPropName;
        JSType propType;
        if (prop.isGetterDef()) {
          specialPropName = GETTER_PREFIX + pname;
          propType = funType.getReturnType();
        } else {
          specialPropName = SETTER_PREFIX + pname;
          propType = pair.type;
        }
        result = result.withProperty(new QualifiedName(specialPropName), propType);
        env = pair.env;
      } else {
        QualifiedName qname = new QualifiedName(pname);
        JSType jsdocType = symbolTable.getPropDeclaredType(prop);
        JSType reqPtype, specPtype;
        if (jsdocType != null) {
          reqPtype = specPtype = jsdocType;
        } else if (requiredType.mayHaveProp(qname)) {
          reqPtype = specPtype = requiredType.getProp(qname);
          if (specializedType.mayHaveProp(qname)) {
            specPtype = specializedType.getProp(qname);
          }
        } else {
          reqPtype = specPtype = JSType.UNKNOWN;
        }
        EnvTypePair pair = analyzeExprFwd(prop.getFirstChild(), env, reqPtype, specPtype);
        if (jsdocType != null) {
          // First declare it; then set the maybe more precise inferred type
          result = result.withDeclaredProperty(qname, jsdocType, false);
          if (!pair.type.isSubtypeOf(jsdocType)) {
            warnings.add(JSError.make(
                prop, INVALID_OBJLIT_PROPERTY_TYPE,
                jsdocType.toString(), pair.type.toString()));
            pair.type = jsdocType;
          }
        }
        result = result.withProperty(qname, pair.type);
        env = pair.env;
      }
    }
    return new EnvTypePair(env, result);
  }

  private EnvTypePair analyzeEnumObjLitFwd(
      Node objLit, TypeEnv inEnv, JSType requiredType) {
    // We warn about malformed enum declarations in GlobalTypeInfo,
    // so we ignore them here.
    if (objLit.getFirstChild() == null) {
      return new EnvTypePair(inEnv, requiredType);
    }
    String pname = NodeUtil.getObjectLitKeyName(objLit.getFirstChild());
    JSType enumeratedType =
        requiredType.getProp(new QualifiedName(pname)).getEnumeratedType();
    if (enumeratedType == null) {
      // enumeratedType is null only if there is some other type error
      return new EnvTypePair(inEnv, requiredType);
    }
    TypeEnv env = inEnv;
    for (Node prop : objLit.children()) {
      EnvTypePair pair =
          analyzeExprFwd(prop.getFirstChild(), env, enumeratedType);
      if (!pair.type.isSubtypeOf(enumeratedType)) {
        warnings.add(JSError.make(
            prop, INVALID_OBJLIT_PROPERTY_TYPE,
            enumeratedType.toString(), pair.type.toString()));
      }
      env = pair.env;
    }
    return new EnvTypePair(env, requiredType);
  }

  private EnvTypePair analyzeTypePredicate(
      Node call, String typeHint, TypeEnv inEnv, JSType specializedType) {
    int numArgs = call.getChildCount() - 1;
    if (numArgs != 1) {
      warnings.add(JSError.make(call, WRONG_ARGUMENT_COUNT,
              call.getFirstChild().getQualifiedName(),
              Integer.toString(numArgs), "1", "1"));
      return analyzeCallNodeArgsFwdWhenError(call, inEnv);
    }
    EnvTypePair pair = analyzeExprFwd(call.getLastChild(), inEnv);
    if (specializedType.isTrueOrTruthy() || specializedType.isFalseOrFalsy()) {
      pair = analyzeExprFwd(call.getLastChild(), inEnv, JSType.UNKNOWN,
          predicateTransformType(typeHint, specializedType, pair.type));
    }
    pair.type = JSType.BOOLEAN;
    return pair;
  }

  private EnvTypePair analyzeGoogTypeof(
      Node typeof, Node typeString, TypeEnv inEnv, JSType specializedType) {
    return analyzeTypePredicate(typeof,
        typeString.isString() ? typeString.getString() : "",
        inEnv, specializedType);
  }

  private EnvTypePair analyzePropertyTestCallFwd(
      Node call, TypeEnv inEnv, JSType specializedType) {
    return analyzeTypePredicate(call,
        call.getFirstChild().getLastChild().getString(),
        inEnv, specializedType);
  }

  // typeHint can come from goog.typeOf or from any function
  // in CodingConvention's isPropertyTestFunction.
  private JSType predicateTransformType(
      String typeHint, JSType booleanContext, JSType beforeType) {
    switch (typeHint) {
      case "array":
      case "isArray": {
        JSType arrayType = commonTypes.getArrayInstance();
        if (arrayType.isUnknown()) {
          return JSType.UNKNOWN;
        }
        return booleanContext.isTrueOrTruthy()
            ? arrayType : beforeType.removeType(arrayType);
      }
      case "isArrayLike":
        return JSType.TOP_OBJECT.withProperty(
            new QualifiedName("length"), JSType.NUMBER);
      case "boolean":
      case "isBoolean":
        return booleanContext.isTrueOrTruthy()
          ? JSType.BOOLEAN : beforeType.removeType(JSType.BOOLEAN);
      case "function":
      case "isFunction":
        return booleanContext.isTrueOrTruthy()
          ? commonTypes.looseTopFunction()
          : beforeType.removeType(commonTypes.topFunction());
      case "null":
      case "isNull":
        return booleanContext.isTrueOrTruthy()
          ? JSType.NULL : beforeType.removeType(JSType.NULL);
      case "number":
      case "isNumber":
        return booleanContext.isTrueOrTruthy()
          ? JSType.NUMBER : beforeType.removeType(JSType.NUMBER);
      case "string":
      case "isString":
        return booleanContext.isTrueOrTruthy()
          ? JSType.STRING : beforeType.removeType(JSType.STRING);
      case "isDef":
        return booleanContext.isTrueOrTruthy()
            ? beforeType.removeType(JSType.UNDEFINED) : JSType.UNDEFINED;
      case "isDefAndNotNull":
        return booleanContext.isTrueOrTruthy()
            ? beforeType.removeType(JSType.NULL_OR_UNDEF) : JSType.NULL_OR_UNDEF;
      case "isObject":
        // typeof(null) === 'object', but goog.isObject(null) is false
        return booleanContext.isTrueOrTruthy()
            ? JSType.TOP_OBJECT : beforeType.removeType(JSType.TOP_OBJECT);
      case "object":
        // goog.typeOf(expr) === 'object' is true only for non-function objects.
        // Just do sth simple here.
        return JSType.UNKNOWN;
      case "undefined":
        return booleanContext.isTrueOrTruthy()
            ? JSType.UNDEFINED : beforeType.removeType(JSType.UNDEFINED);
      default:
        // For when we can't figure out the type name used with goog.typeOf.
        return JSType.UNKNOWN;
    }
  }

  private boolean tightenTypeAndDontWarn(
      String varName, Node n, JSType declared, JSType inferred, JSType required) {
    boolean isSpecializableTop = declared != null && declared.isTop()
        && (!inferred.isTop() || NodeUtil.isPropertyTest(compiler, n.getParent()));
    boolean fuzzyDeclaration = declared == null || declared.isUnknown() || isSpecializableTop;

    return fuzzyDeclaration
        // The intent is to be looser about warnings in the case when a value
        // is passed to a function (as opposed to being created locally),
        // because then we have less information about the value.
        // The accurate way to do this is to taint types so that we can track
        // where a type comes from (local or not).
        // Without tainting (eg, we used to have locations), we approximate this
        // by checking the variable name.
        && (varName == null || currentScope.isFormalParam(varName)
            || currentScope.isOuterVar(varName))
        // If required is loose, it's easier for it to be a subtype of inferred.
        // We only tighten the type if the non-loose required is also a subtype.
        // Otherwise, we would be skipping warnings too often.
        // This is important b/c analyzePropAccess & analyzePropLvalue introduce
        // loose objects, even if there are no undeclared formals.
        && required.isNonLooseSubtypeOf(inferred);
  }

  //////////////////////////////////////////////////////////////////////////////
  // These functions return true iff they produce a warning

  private boolean mayWarnAboutNonObject(
      Node receiver, JSType recvType, JSType specializedType) {
    // Can happen for IF tests that are never true
    if (recvType.isBottom()) {
      return true;
    }
    // The warning depends on whether we are testing for the existence of a
    // property.
    boolean isNotAnObject = !JSType.haveCommonSubtype(recvType, JSType.TOP_OBJECT);
    boolean mayNotBeAnObject = !recvType.isSubtypeOf(JSType.TOP_OBJECT);
    if (isNotAnObject
        || (!specializedType.isTrueOrTruthy() && !specializedType.isFalseOrFalsy()
            && mayNotBeAnObject)) {
      warnings.add(JSError.make(receiver, PROPERTY_ACCESS_ON_NONOBJECT,
              getPropNameForErrorMsg(receiver.getParent()),
              recvType.toString()));
      return true;
    }
    return false;
  }

  private String getPropNameForErrorMsg(Node propAccessNode) {
    Preconditions.checkArgument(propAccessNode.isGetProp() || propAccessNode.isGetElem());
    Node propNode = propAccessNode.getLastChild();
    if (propNode.isString()) {
      return propNode.getString();
    } else if (propNode.isQualifiedName()) {
      return "[" + propNode.getQualifiedName() + "]";
    }
    return "[unknown property]";
  }

  private boolean mayWarnAboutStructPropAccess(Node obj, JSType type) {
    if (type.mayBeStruct()) {
      warnings.add(JSError.make(obj,
              NewTypeInference.ILLEGAL_PROPERTY_ACCESS, "'[]'", "struct"));
      return true;
    }
    return false;
  }

  private boolean mayWarnAboutDictPropAccess(Node obj, JSType type) {
    if (type.mayBeDict()) {
      warnings.add(JSError.make(obj,
              NewTypeInference.ILLEGAL_PROPERTY_ACCESS, "'.'", "dict"));
      return true;
    }
    return false;
  }

  private boolean mayWarnAboutPropCreation(
      QualifiedName pname, Node getProp, JSType recvType) {
    Preconditions.checkArgument(getProp.isGetProp());
    // For inferred formals used as objects, we don't warn about property
    // creation. Consider:
    //   function f(obj) { obj.prop = 123; }
    // f should accept objects without prop, so we don't require that obj
    // already have prop.
    if (recvType.mayBeStruct() && !recvType.hasProp(pname)) {
      warnings.add(JSError.make(
          getProp, ILLEGAL_PROPERTY_CREATION, pname.toString()));
      return true;
    }
    return false;
  }

  private boolean isPropertyAbsentTest(Node propAccessNode) {
    Node parent = propAccessNode.getParent();
    if (parent.getType() == Token.EQ || parent.getType() == Token.SHEQ) {
      Node other = parent.getFirstChild() == propAccessNode
          ? parent.getSecondChild() : parent.getFirstChild();
      return NodeUtil.isUndefined(other);
    }
    return false;
  }

  private boolean mayWarnAboutInexistentProp(Node propAccessNode,
      JSType recvType, QualifiedName propQname, TypeEnv env) {
    if (!propAccessNode.isGetProp() || isPropertyAbsentTest(propAccessNode)
        || recvType.hasProp(propQname)) {
      return false;
    }
    Node recv = propAccessNode.getFirstChild();
    // TODO(dimvar): recvType is loose only in a rare case that needs to be
    // fixed, related to specializing namespace properties. After fixing that,
    // we need a preconditions check to ban loose types here.
    if (recvType.isLoose()) {
      updateLvalueTypeInEnv(env, recv, QualifiedName.fromNode(recv), JSType.UNKNOWN);
      return false;
    }
    // To avoid giant types in the error message, we use a heuristic:
    // if the receiver is a qualified name whose type is too long, we print
    // the qualified name instead.
    String recvTypeAsString = recvType.toString();
    String pname = propQname.toString();
    String errorMsg;
    if (!recv.isQualifiedName()) {
      errorMsg = recvTypeAsString;
    } else if (recvTypeAsString.length() > 100) {
      errorMsg = recv.getQualifiedName();
    } else {
      errorMsg = recv.getQualifiedName() + " of type " + recvTypeAsString;
    }
    DiagnosticType warningType = recvType.mayHaveProp(propQname)
        ? POSSIBLY_INEXISTENT_PROPERTY : INEXISTENT_PROPERTY;
    warnings.add(JSError.make(propAccessNode, warningType, pname, errorMsg));
    return true;
  }

  private boolean mayWarnAboutConst(Node n) {
    Node lhs = n.getFirstChild();
    if (lhs.isName() && currentScope.isConstVar(lhs.getString())) {
      warnings.add(JSError.make(n, CONST_REASSIGNED));
      return true;
    }
    return false;
  }

  private boolean mayWarnAboutConstProp(
      Node propAccess, JSType recvType, QualifiedName pname) {
    if (recvType.hasConstantProp(pname) &&
        !propAccess.getBooleanProp(Node.CONSTANT_PROPERTY_DEF)) {
      warnings.add(JSError.make(propAccess, CONST_PROPERTY_REASSIGNED));
      return true;
    }
    return false;
  }

  private boolean mayWarnAboutGlobalThis(Node thisExpr, NTIScope currentScope) {
    Preconditions.checkArgument(thisExpr.isThis());
    Preconditions.checkState(!currentScope.hasThis());
    Node parent = thisExpr.getParent();
    if ((parent.isGetProp() || parent.isGetElem())
        // Don't warn for callbacks. Most of them are not annotated but THIS is
        // bound to a legitimate object at runtime. They do lose typechecking
        // for THIS however, but we won't warn.
        && !NodeUtil.isCallOrNewArgument(currentScope.getRoot())) {
      warnings.add(JSError.make(thisExpr, GLOBAL_THIS));
      return true;
    }
    return false;
  }

  private boolean mayWarnAboutBadIObjectIndex(Node n, JSType iobjectType,
      JSType foundIndexType, JSType requiredIndexType) {
    if (requiredIndexType.isBottom()) {
      warnings.add(JSError.make(
          n, NewTypeInference.BOTTOM_INDEX_TYPE, iobjectType.toString()));
      return true;
    }
    if (!foundIndexType.isSubtypeOf(requiredIndexType)) {
      warnings.add(JSError.make(
          n, NewTypeInference.INVALID_INDEX_TYPE,
          requiredIndexType.toString(), foundIndexType.toString()));
      return true;
    }
    return false;
  }

  private EnvTypePair analyzePropAccessFwd(Node receiver, String pname,
      TypeEnv inEnv, JSType requiredType, JSType specializedType) {
    QualifiedName propQname = new QualifiedName(pname);
    Node propAccessNode = receiver.getParent();
    EnvTypePair pair;
    JSType reqObjType = pickReqObjType(propAccessNode);
    JSType recvReqType, recvSpecType;

    // First, analyze the receiver object.
    if (NodeUtil.isPropertyTest(compiler, propAccessNode)
        && !specializedType.isFalseOrFalsy()
        // The NodeUtil method doesn't use types, so it can't see that the
        // else branch of "if (!x.prop)" is a property test.
        || specializedType.isTrueOrTruthy()) {
      recvReqType = reqObjType;
      if (specializedType.isTrueOrTruthy()) {
        // This handles cases like: if (x.prop1 && x.prop1.prop2) { ... }
        // In the THEN branch, the only thing we know about x.prop1 is that it
        // has a truthy property, so x.prop1 should be a loose object to avoid
        // spurious warnings.
        recvSpecType = reqObjType.withLoose().withProperty(propQname, specializedType);
      } else {
        recvSpecType = reqObjType.withProperty(propQname, specializedType);
      }
    } else if (specializedType.isFalseOrFalsy()) {
      recvReqType = recvSpecType = reqObjType;
    } else {
      recvReqType = reqObjType.withProperty(propQname, requiredType);
      recvSpecType = reqObjType.withProperty(propQname, specializedType);
    }
    pair = analyzeExprFwd(receiver, inEnv, recvReqType, recvSpecType);
    pair = mayWarnAboutNullableReferenceAndTighten(
        receiver, pair.type, recvSpecType, pair.env);
    JSType recvType = pair.type.autobox();
    if (recvType.isUnknown()
        || mayWarnAboutNonObject(receiver, recvType, specializedType)) {
      return new EnvTypePair(pair.env, requiredType);
    }
    FunctionType ft = recvType.getFunTypeIfSingletonObj();
    if (ft != null && pname.equals("call")) {
      return new EnvTypePair(pair.env,
          commonTypes.fromFunctionType(ft.transformByCallProperty()));
    }
    if (ft != null && pname.equals("apply")) {
      return new EnvTypePair(pair.env,
          commonTypes.fromFunctionType(ft.transformByApplyProperty(commonTypes)));
    }
    if (this.convention.isSuperClassReference(pname)) {
      if (ft != null && ft.isUniqueConstructor()) {
        JSType result = ft.getSuperPrototype();
        pair.type = result != null ? result : JSType.UNDEFINED;
        return pair;
      }
    }
    if (propAccessNode.isGetProp() &&
        mayWarnAboutDictPropAccess(receiver, recvType)) {
      return new EnvTypePair(pair.env, requiredType);
    }
    if (recvType.isTop()) {
      recvType = JSType.TOP_OBJECT;
    }
    // Then, analyze the property access.
    QualifiedName getterPname = new QualifiedName(GETTER_PREFIX + pname);
    if (recvType.hasProp(getterPname)) {
      return new EnvTypePair(pair.env, recvType.getProp(getterPname));
    }
    JSType resultType = recvType.getProp(propQname);
    if (resultType != null && resultType.isBottom()) {
      warnings.add(JSError.make(propAccessNode, BOTTOM_PROP,
              pname, recvType.toString()));
      return new EnvTypePair(pair.env, JSType.UNKNOWN);
    }
    if (!propAccessNode.getParent().isExprResult()
        && !specializedType.isTrueOrTruthy()
        && !specializedType.isFalseOrFalsy()
        && !recvType.mayBeDict()
        && !mayWarnAboutInexistentProp(propAccessNode, recvType, propQname, pair.env)
        && recvType.hasProp(propQname)
        && !resultType.isSubtypeOf(requiredType)
        && tightenTypeAndDontWarn(
            receiver.isName() ? receiver.getString() : null,
            receiver,
            recvType.getDeclaredProp(propQname),
            resultType, requiredType)) {
      // Tighten the inferred type and don't warn.
      // See analyzeNameFwd for explanation about types as lower/upper bounds.
      resultType = resultType.specialize(requiredType);
      LValueResultFwd lvr =
          analyzeLValueFwd(propAccessNode, inEnv, resultType);
      TypeEnv updatedEnv =
          updateLvalueTypeInEnv(lvr.env, propAccessNode, lvr.ptr, resultType);
      return new EnvTypePair(updatedEnv, resultType);
    }
    // We've already warned about missing props, and never want to return null.
    if (resultType == null) {
      resultType = JSType.UNKNOWN;
    }
    // Any potential type mismatch will be caught by the context
    return new EnvTypePair(pair.env, resultType);
  }

  private TypeEnv updateLvalueTypeInEnv(
      TypeEnv env, Node lvalue, QualifiedName qname, JSType type) {
    Preconditions.checkNotNull(type);
    switch (lvalue.getType()) {
      case Token.NAME:
        return envPutType(env, lvalue.getString(), type);
      case Token.THIS: {
        JSType t = envGetType(env, THIS_ID);
        // Don't specialize THIS in functions where it is unknown.
        return t == null ? env : envPutType(env, THIS_ID, type);
      }
      case Token.VAR: // Can happen iff its parent is a for/in.
        Preconditions.checkState(NodeUtil.isForIn(lvalue.getParent()));
        return envPutType(env, lvalue.getFirstChild().getString(), type);
      case Token.GETPROP:
      case Token.GETELEM: {
        if (qname != null) {
          String objName = qname.getLeftmostName();
          QualifiedName props = qname.getAllButLeftmost();
          JSType objType = envGetType(env, objName);
          // TODO(dimvar): In analyzeNameFwd/Bwd, we are careful to not
          // specialize namespaces, and we need the same check here. But
          // currently, stopping specialization here causes tests to fail,
          // because specializing the namespace is our way of updating its
          // functions after computing summaries. The better solution is to
          // retain Namespace instances after GTI instead of turning them into
          // ObjectTypes, and then update those with the summaries and stop
          // specializing here.
          env = envPutType(env, objName, objType.withProperty(props, type));
        }
        return env;
      }
      default:
        return env;
    }
  }

  private TypeEnv collectTypesForFreeVarsFwd(Node n, TypeEnv env) {
    Preconditions.checkArgument(n.isFunction()
        || n.isName() && NodeUtil.isCallOrNewTarget(n));
    String fnName = n.isFunction() ? symbolTable.getFunInternalName(n) : n.getString();
    NTIScope innerScope = currentScope.getScope(fnName);
    for (String freeVar : innerScope.getOuterVars()) {
      if (innerScope.getDeclaredTypeOf(freeVar) == null) {
        FunctionType summary = summaries.get(innerScope).getFunType();
        JSType outerType = envGetType(env, freeVar);
        if (outerType == null) {
          outerType = JSType.UNKNOWN;
        }
        JSType innerType = summary.getOuterVarPrecondition(freeVar);
        if (// We don't warn if the innerType is a loose object, because we
            // haven't found an easy way to avoid false positives.
            !innerType.isLoose()
            // If n is a function expression, we don't know where it's called,
            // so we don't warn for uninitialized variables.
            && (n.isName() || n.isFunction() && !outerType.isUndefined())
            && !JSType.haveCommonSubtype(outerType, innerType)) {
          warnings.add(JSError.make(n, CROSS_SCOPE_GOTCHA,
                  freeVar, outerType.toString(), innerType.toString()));
        }
        // If n is a callee node, we only want to keep the type in the callee.
        // If n is a function expression, we're not sure if it gets called,
        // so we join the types.
        env = envPutType(env, freeVar,
            n.isFunction() ? JSType.join(innerType, outerType) : innerType);
      }
    }
    return env;
  }

  private EnvTypePair analyzeLooseCallNodeFwd(
      Node callNode, TypeEnv inEnv, JSType requiredType) {
    Preconditions.checkArgument(callNode.isCall() || callNode.isNew());
    Node callee = callNode.getFirstChild();
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    TypeEnv tmpEnv = inEnv;
    for (Node arg = callee.getNext(); arg != null; arg = arg.getNext()) {
      EnvTypePair pair = analyzeExprFwd(arg, tmpEnv);
      tmpEnv = pair.env;
      builder.addReqFormal(pair.type);
    }
    JSType looseRetType = requiredType.isUnknown() ? JSType.BOTTOM : requiredType;
    JSType looseFunctionType = commonTypes.fromFunctionType(
        builder.addRetType(looseRetType).addLoose().buildFunction());
    // Unsound if the arguments and callee have interacting side effects
    EnvTypePair calleePair = analyzeExprFwd(
        callee, tmpEnv, commonTypes.topFunction(), looseFunctionType);
    JSType result = calleePair.type.getFunTypeIfSingletonObj().getReturnType();
    return new EnvTypePair(calleePair.env,
        isImpreciseType(result) ? requiredType : result);
  }

  private static boolean isImpreciseType(JSType t) {
    return t.isBottom() || t.isTop() || t.isUnknown() || t.isUnion()
        || t.isTrueOrTruthy() || t.isFalseOrFalsy()
        || t.isLoose() || t.isNonClassyObject();
  }

  private EnvTypePair analyzeLooseCallNodeBwd(
      Node callNode, TypeEnv outEnv, JSType retType) {
    Preconditions.checkArgument(callNode.isCall() || callNode.isNew());
    Preconditions.checkNotNull(retType);
    Node callee = callNode.getFirstChild();
    TypeEnv tmpEnv = outEnv;
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    for (int i = callNode.getChildCount() - 2; i >= 0; i--) {
      Node arg = callNode.getChildAtIndex(i + 1);
      EnvTypePair pair = analyzeExprBwd(arg, tmpEnv);
      JSType argType = pair.type;
      tmpEnv = pair.env;
      // May wait until FWD to get more precise argument types.
      builder.addReqFormal(isImpreciseType(argType) ? JSType.BOTTOM : argType);
    }
    JSType looseRetType = retType.isUnknown() ? JSType.BOTTOM : retType;
    JSType looseFunctionType = commonTypes.fromFunctionType(
        builder.addRetType(looseRetType).addLoose().buildFunction());
    println("loose function type is ", looseFunctionType);
    EnvTypePair calleePair = analyzeExprBwd(callee, tmpEnv, looseFunctionType);
    return new EnvTypePair(calleePair.env, retType);
  }

  private EnvTypePair analyzeExprBwd(Node expr, TypeEnv outEnv) {
    return analyzeExprBwd(expr, outEnv, JSType.UNKNOWN);
  }

  /**
   * For now, we won't emit any warnings bwd.
   */
  private EnvTypePair analyzeExprBwd(
      Node expr, TypeEnv outEnv, JSType requiredType) {
    Preconditions.checkArgument(requiredType != null, "Required type null at: %s", expr);
    Preconditions.checkArgument(!requiredType.isBottom());
    switch (expr.getType()) {
      case Token.EMPTY: // can be created by a FOR with empty condition
        return new EnvTypePair(outEnv, JSType.UNKNOWN);
      case Token.FUNCTION: {
        String fnName = symbolTable.getFunInternalName(expr);
        return new EnvTypePair(outEnv, envGetType(outEnv, fnName));
      }
      case Token.FALSE:
      case Token.NULL:
      case Token.NUMBER:
      case Token.STRING:
      case Token.TRUE:
        return new EnvTypePair(outEnv, scalarValueToType(expr.getType()));
      case Token.OBJECTLIT:
        return analyzeObjLitBwd(expr, outEnv, requiredType);
      case Token.THIS: {
        // TODO(blickly): Infer a loose type for THIS if we're in a function.
        if (!currentScope.hasThis()) {
          return new EnvTypePair(outEnv, JSType.UNKNOWN);
        }
        JSType thisType = currentScope.getDeclaredTypeOf(THIS_ID);
        return new EnvTypePair(outEnv, thisType);
      }
      case Token.NAME:
        return analyzeNameBwd(expr, outEnv, requiredType);
      case Token.INC:
      case Token.DEC:
      case Token.BITNOT:
      case Token.NEG: // Unary operations on numbers
        return analyzeExprBwd(expr.getFirstChild(), outEnv, JSType.NUMBER);
      case Token.POS: {
        EnvTypePair pair = analyzeExprBwd(expr.getFirstChild(), outEnv);
        pair.type = JSType.NUMBER;
        return pair;
      }
      case Token.TYPEOF: {
        EnvTypePair pair = analyzeExprBwd(expr.getFirstChild(), outEnv);
        pair.type = JSType.STRING;
        return pair;
      }
      case Token.INSTANCEOF: {
        TypeEnv env = analyzeExprBwd(
            expr.getLastChild(), outEnv, commonTypes.topFunction()).env;
        EnvTypePair pair = analyzeExprBwd(expr.getFirstChild(), env);
        pair.type = JSType.BOOLEAN;
        return pair;
      }
      case Token.BITOR:
      case Token.BITAND:
      case Token.BITXOR:
      case Token.DIV:
      case Token.LSH:
      case Token.MOD:
      case Token.MUL:
      case Token.RSH:
      case Token.SUB:
      case Token.URSH:
        return analyzeBinaryNumericOpBwd(expr, outEnv);
      case Token.ADD:
        return analyzeAddBwd(expr, outEnv, requiredType);
      case Token.OR:
      case Token.AND:
        return analyzeLogicalOpBwd(expr, outEnv);
      case Token.SHEQ:
      case Token.SHNE:
      case Token.EQ:
      case Token.NE:
        return analyzeEqNeBwd(expr, outEnv);
      case Token.LT:
      case Token.GT:
      case Token.LE:
      case Token.GE:
        return analyzeLtGtBwd(expr, outEnv);
      case Token.ASSIGN:
        return analyzeAssignBwd(expr, outEnv, requiredType);
      case Token.ASSIGN_ADD:
        return analyzeAssignAddBwd(expr, outEnv, requiredType);
      case Token.ASSIGN_BITOR:
      case Token.ASSIGN_BITXOR:
      case Token.ASSIGN_BITAND:
      case Token.ASSIGN_LSH:
      case Token.ASSIGN_RSH:
      case Token.ASSIGN_URSH:
      case Token.ASSIGN_SUB:
      case Token.ASSIGN_MUL:
      case Token.ASSIGN_DIV:
      case Token.ASSIGN_MOD:
        return analyzeAssignNumericOpBwd(expr, outEnv);
      case Token.GETPROP: {
        Preconditions.checkState(
            !NodeUtil.isAssignmentOp(expr.getParent()) ||
            !NodeUtil.isLValue(expr));
        if (expr.getBooleanProp(Node.ANALYZED_DURING_GTI)) {
          return new EnvTypePair(outEnv, requiredType);
        }
        return analyzePropAccessBwd(expr.getFirstChild(),
            expr.getLastChild().getString(), outEnv, requiredType);
      }
      case Token.HOOK:
        return analyzeHookBwd(expr, outEnv, requiredType);
      case Token.CALL:
      case Token.NEW:
        return analyzeCallNewBwd(expr, outEnv, requiredType);
      case Token.COMMA: {
        EnvTypePair pair = analyzeExprBwd(
            expr.getLastChild(), outEnv, requiredType);
        pair.env = analyzeExprBwd(expr.getFirstChild(), pair.env).env;
        return pair;
      }
      case Token.NOT: {
        EnvTypePair pair = analyzeExprBwd(expr.getFirstChild(), outEnv);
        pair.type = pair.type.negate();
        return pair;
      }
      case Token.GETELEM:
        return analyzeGetElemBwd(expr, outEnv, requiredType);
      case Token.VOID: {
        EnvTypePair pair = analyzeExprBwd(expr.getFirstChild(), outEnv);
        pair.type = JSType.UNDEFINED;
        return pair;
      }
      case Token.IN:
        return analyzeInBwd(expr, outEnv);
      case Token.DELPROP: {
        EnvTypePair pair = analyzeExprBwd(expr.getFirstChild(), outEnv);
        pair.type = JSType.BOOLEAN;
        return pair;
      }
      case Token.VAR: { // Can happen iff its parent is a for/in.
        Node vdecl = expr.getFirstChild();
        String name = vdecl.getString();
        // For/in can never have rhs of its VAR
        Preconditions.checkState(!vdecl.hasChildren());
        return new EnvTypePair(
            envPutType(outEnv, name, JSType.UNKNOWN), JSType.UNKNOWN);
      }
      case Token.REGEXP:
        return new EnvTypePair(outEnv, commonTypes.getRegexpType());
      case Token.ARRAYLIT:
        return analyzeArrayLitBwd(expr, outEnv);
      case Token.CAST:
        EnvTypePair pair = analyzeExprBwd(expr.getFirstChild(), outEnv);
        pair.type = symbolTable.getCastType(expr);
        return pair;
      default:
        throw new RuntimeException("BWD: Unhandled expression type: "
            + Token.name(expr.getType()) + " with parent: " + expr.getParent());
    }
  }

  private EnvTypePair analyzeNameBwd(
      Node expr, TypeEnv outEnv, JSType requiredType) {
    String varName = expr.getString();
    if (varName.equals("undefined")) {
      return new EnvTypePair(outEnv, JSType.UNDEFINED);
    }
    JSType inferredType = envGetType(outEnv, varName);
    println(varName, "'s inferredType: ", inferredType,
        " requiredType:  ", requiredType);
    if (inferredType == null) {
      return new EnvTypePair(outEnv, JSType.UNKNOWN);
    }
    JSType preciseType = inferredType.specialize(requiredType);
    if ((currentScope.isUndeclaredFormal(varName)
        || currentScope.isUndeclaredOuterVar(varName))
        && preciseType.hasNonScalar()) {
      preciseType = preciseType.withLoose();
    }
    println(varName, "'s preciseType: ", preciseType);
    if (preciseType.isBottom()) {
      // If there is a type mismatch, we can propagate the previously
      // inferred type or the required type.
      // Propagating the already inferred type means that the type of the
      // variable is stable throughout the function body.
      // Propagating the required type means that the type chosen for a
      // formal is the one closest to the function header, which helps
      // generate more intuitive warnings in the fwd direction.
      // But there is a small chance that the different types of the same
      // variable flow to other variables and this can also be a source of
      // unintuitive warnings.
      // It's a trade-off.
      JSType declType = currentScope.getDeclaredTypeOf(varName);
      preciseType = declType == null ? requiredType : declType;
    }
    return EnvTypePair.addBinding(outEnv, varName, preciseType);
  }

  private EnvTypePair analyzeBinaryNumericOpBwd(Node expr, TypeEnv outEnv) {
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();
    TypeEnv rhsEnv = analyzeExprBwd(rhs, outEnv, JSType.NUMBER).env;
    EnvTypePair pair = analyzeExprBwd(lhs, rhsEnv, JSType.NUMBER);
    pair.type = JSType.NUMBER;
    return pair;
  }

  private EnvTypePair analyzeAddBwd(Node expr, TypeEnv outEnv, JSType requiredType) {
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();
    JSType operandType = requiredType.isNumber() ? JSType.NUMBER : JSType.UNKNOWN;
    EnvTypePair rhsPair = analyzeExprBwd(rhs, outEnv, operandType);
    EnvTypePair lhsPair = analyzeExprBwd(lhs, rhsPair.env, operandType);
    lhsPair.type = JSType.plus(lhsPair.type, rhsPair.type);
    return lhsPair;
  }

  private EnvTypePair analyzeLogicalOpBwd(Node expr, TypeEnv outEnv) {
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();
    EnvTypePair rhsPair = analyzeExprBwd(rhs, outEnv);
    EnvTypePair lhsPair = analyzeExprBwd(lhs, rhsPair.env);
    lhsPair.type = JSType.join(rhsPair.type, lhsPair.type);
    return lhsPair;
  }

  private EnvTypePair analyzeEqNeBwd(Node expr, TypeEnv outEnv) {
    TypeEnv rhsEnv = analyzeExprBwd(expr.getLastChild(), outEnv).env;
    EnvTypePair pair = analyzeExprBwd(expr.getFirstChild(), rhsEnv);
    pair.type = JSType.BOOLEAN;
    return pair;
  }

  private EnvTypePair analyzeLtGtBwd(Node expr, TypeEnv outEnv) {
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();
    EnvTypePair rhsPair = analyzeExprBwd(rhs, outEnv);
    EnvTypePair lhsPair = analyzeExprBwd(lhs, rhsPair.env);
    JSType meetType = JSType.meet(lhsPair.type, rhsPair.type);
    if (meetType.isBottom()) {
      // Type mismatch, the fwd direction will warn; don't reanalyze
      lhsPair.type = JSType.BOOLEAN;
      return lhsPair;
    }
    rhsPair = analyzeExprBwd(rhs, outEnv, meetType);
    lhsPair = analyzeExprBwd(lhs, rhsPair.env, meetType);
    lhsPair.type = JSType.BOOLEAN;
    return lhsPair;
  }

  private EnvTypePair analyzeAssignBwd(
      Node expr, TypeEnv outEnv, JSType requiredType) {
    if (expr.getBooleanProp(Node.ANALYZED_DURING_GTI)) {
      return new EnvTypePair(outEnv, requiredType);
    }
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();
    if (lhs.getBooleanProp(Node.ANALYZED_DURING_GTI)) {
      return analyzeExprBwd(rhs, outEnv,
          markAndGetTypeOfPreanalyzedNode(lhs, outEnv, false));
    }
    // Here we analyze the LHS twice:
    // Once to find out what should be removed for the slicedEnv,
    // and again to take into account the side effects of the LHS itself.
    LValueResultBwd lvalue = analyzeLValueBwd(lhs, outEnv, requiredType, true);
    TypeEnv slicedEnv = lvalue.env;
    JSType rhsReqType = specializeKeep2ndWhenBottom(lvalue.type, requiredType);
    EnvTypePair pair = analyzeExprBwd(rhs, slicedEnv, rhsReqType);
    pair.env = analyzeLValueBwd(lhs, pair.env, requiredType, true).env;
    return pair;
  }

  private EnvTypePair analyzeAssignAddBwd(
      Node expr, TypeEnv outEnv, JSType requiredType) {
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();
    JSType lhsReqType = specializeKeep2ndWhenBottom(requiredType, JSType.NUM_OR_STR);
    LValueResultBwd lvalue = analyzeLValueBwd(lhs, outEnv, lhsReqType, false);
    // if lhs is a string, rhs can still be a number
    JSType rhsReqType = lvalue.type.isNumber() ? JSType.NUMBER : JSType.NUM_OR_STR;
    EnvTypePair pair = analyzeExprBwd(rhs, outEnv, rhsReqType);
    pair.env = analyzeLValueBwd(lhs, pair.env, lhsReqType, false).env;
    return pair;
  }

  private EnvTypePair analyzeAssignNumericOpBwd(Node expr, TypeEnv outEnv) {
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();
    EnvTypePair pair = analyzeExprBwd(rhs, outEnv, JSType.NUMBER);
    LValueResultBwd lvalue =
        analyzeLValueBwd(lhs, pair.env, JSType.NUMBER, false);
    return new EnvTypePair(lvalue.env, JSType.NUMBER);
  }

  private EnvTypePair analyzeHookBwd(
      Node expr, TypeEnv outEnv, JSType requiredType) {
    Node cond = expr.getFirstChild();
    Node thenBranch = cond.getNext();
    Node elseBranch = thenBranch.getNext();
    EnvTypePair thenPair = analyzeExprBwd(thenBranch, outEnv, requiredType);
    EnvTypePair elsePair = analyzeExprBwd(elseBranch, outEnv, requiredType);
    return analyzeExprBwd(cond, TypeEnv.join(thenPair.env, elsePair.env));
  }

  private EnvTypePair analyzeCallNewBwd(
      Node expr, TypeEnv outEnv, JSType requiredType) {
    Preconditions.checkArgument(expr.isNew() || expr.isCall());
    Node callee = expr.getFirstChild();
    EnvTypePair pair = analyzeExprBwd(callee, outEnv, commonTypes.topFunction());
    TypeEnv envAfterCallee = pair.env;
    FunctionType funType = pair.type.getFunType();
    if (funType == null) {
      return analyzeCallNodeArgumentsBwd(expr, envAfterCallee);
    } else if (funType.isLoose()) {
      return analyzeLooseCallNodeBwd(expr, envAfterCallee, requiredType);
    } else if (expr.isCall() && funType.isSomeConstructorOrInterface()
        || expr.isNew() && !funType.isSomeConstructorOrInterface()) {
      return analyzeCallNodeArgumentsBwd(expr, envAfterCallee);
    } else if (funType.isTopFunction()) {
      return analyzeCallNodeArgumentsBwd(expr, envAfterCallee);
    }
    if (callee.isName() && !funType.isGeneric() && expr.isCall()) {
      createDeferredCheckBwd(expr, requiredType);
    }
    int numArgs = expr.getChildCount() - 1;
    if (numArgs < funType.getMinArity() || numArgs > funType.getMaxArity()) {
      return analyzeCallNodeArgumentsBwd(expr, envAfterCallee);
    }
    if (funType.isGeneric()) {
      Map<String, JSType> typeMap =
          calcTypeInstantiationBwd(expr, funType, envAfterCallee);
      funType = funType.instantiateGenerics(typeMap);
    }
    TypeEnv tmpEnv = envAfterCallee;
    // In bwd direction, analyze arguments in reverse
    for (int i = expr.getChildCount() - 2; i >= 0; i--) {
      JSType formalType = funType.getFormalType(i);
      // The type of a formal can be BOTTOM as the result of a join.
      // Don't use this as a requiredType.
      if (formalType.isBottom()) {
        formalType = JSType.UNKNOWN;
      }
      Node arg = expr.getChildAtIndex(i + 1);
      tmpEnv = analyzeExprBwd(arg, tmpEnv, formalType).env;
      // We don't need deferred checks for args in BWD
    }
    JSType retType =
        expr.isNew() ? funType.getThisType() : funType.getReturnType();
    return new EnvTypePair(tmpEnv, retType);
  }

  private EnvTypePair analyzeGetElemBwd(
      Node expr, TypeEnv outEnv, JSType requiredType) {
    Node receiver = expr.getFirstChild();
    Node index = expr.getLastChild();
    JSType reqObjType = pickReqObjType(expr);
    EnvTypePair pair = analyzeExprBwd(receiver, outEnv, reqObjType);
    JSType recvType = pair.type;
    JSType indexType = recvType.getIndexType();
    if (indexType != null) {
      pair = analyzeExprBwd(index, pair.env, indexType);
      pair.type = recvType.getIndexedType();
      return pair;
    }
    if (index.isString()) {
      return analyzePropAccessBwd(receiver, index.getString(), outEnv, requiredType);
    }
    pair = analyzeExprBwd(index, outEnv);
    pair = analyzeExprBwd(receiver, pair.env, reqObjType);
    pair.type = requiredType;
    return pair;
  }

  private EnvTypePair analyzeInBwd(Node expr, TypeEnv outEnv) {
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();
    EnvTypePair pair = analyzeExprBwd(rhs, outEnv, pickReqObjType(expr));
    pair = analyzeExprBwd(lhs, pair.env, JSType.NUM_OR_STR);
    pair.type = JSType.BOOLEAN;
    return pair;
  }

  private EnvTypePair analyzeArrayLitBwd(Node expr, TypeEnv outEnv) {
    TypeEnv env = outEnv;
    JSType elementType = JSType.BOTTOM;
    for (int i = expr.getChildCount() - 1; i >= 0; i--) {
      Node arrayElm = expr.getChildAtIndex(i);
      EnvTypePair pair = analyzeExprBwd(arrayElm, env);
      env = pair.env;
      elementType = JSType.join(elementType, pair.type);
    }
    if (elementType.isBottom()) {
      elementType = JSType.UNKNOWN;
    }
    return new EnvTypePair(env, commonTypes.getArrayInstance(elementType));
  }

  private EnvTypePair analyzeCallNodeArgumentsBwd(
      Node callNode, TypeEnv outEnv) {
    TypeEnv env = outEnv;
    for (int i = callNode.getChildCount() - 1; i > 0; i--) {
      Node arg = callNode.getChildAtIndex(i);
      env = analyzeExprBwd(arg, env).env;
    }
    return new EnvTypePair(env, JSType.UNKNOWN);
  }

  private void createDeferredCheckBwd(Node expr, JSType requiredType) {
    Preconditions.checkArgument(expr.isCall());
    Preconditions.checkArgument(expr.getFirstChild().isName());
    String calleeName = expr.getFirstChild().getString();
    // Local function definitions will be type-checked more
    // exactly using their summaries, and don't need deferred checks
    if (currentScope.isKnownFunction(calleeName)
        && !currentScope.isLocalFunDef(calleeName)
        && !currentScope.isExternalFunction(calleeName)) {
      NTIScope s = currentScope.getScope(calleeName);
      JSType expectedRetType;
      if (s.getDeclaredFunctionType().getReturnType() == null) {
        expectedRetType = requiredType;
      } else {
        // No deferred check if the return type is declared
        expectedRetType = null;
      }
      println("Putting deferred check of function: ", calleeName,
          " with ret: ", expectedRetType);
      DeferredCheck dc = new DeferredCheck(
          expr, expectedRetType, currentScope, s);
      deferredChecks.put(expr, dc);
    }
  }

  private EnvTypePair analyzePropAccessBwd(
      Node receiver, String pname, TypeEnv outEnv, JSType requiredType) {
    Node propAccessNode = receiver.getParent();
    QualifiedName qname = new QualifiedName(pname);
    JSType reqObjType = pickReqObjType(propAccessNode);
    if (!NodeUtil.isPropertyTest(compiler, propAccessNode)) {
      reqObjType = reqObjType.withProperty(qname, requiredType);
    }
    EnvTypePair pair = analyzeExprBwd(receiver, outEnv, reqObjType);
    JSType receiverType = pair.type;
    JSType propAccessType = receiverType.mayHaveProp(qname) ?
        receiverType.getProp(qname) : requiredType;
    pair.type = propAccessType;
    return pair;
  }

  private EnvTypePair analyzeObjLitBwd(
      Node objLit, TypeEnv outEnv, JSType requiredType) {
    if (NodeUtil.isEnumDecl(objLit.getParent())) {
      return analyzeEnumObjLitBwd(objLit, outEnv, requiredType);
    }
    TypeEnv env = outEnv;
    JSType result = pickReqObjType(objLit);
    for (Node prop = objLit.getLastChild();
         prop != null;
         prop = objLit.getChildBefore(prop)) {
      QualifiedName pname =
          new QualifiedName(NodeUtil.getObjectLitKeyName(prop));
      if (prop.isGetterDef() || prop.isSetterDef()) {
        env = analyzeExprBwd(prop.getFirstChild(), env).env;
      } else {
        JSType jsdocType = symbolTable.getPropDeclaredType(prop);
        JSType reqPtype;
        if (jsdocType != null) {
          reqPtype = jsdocType;
        } else if (requiredType.mayHaveProp(pname)) {
          reqPtype = requiredType.getProp(pname);
        } else {
          reqPtype = JSType.UNKNOWN;
        }
        EnvTypePair pair = analyzeExprBwd(prop.getFirstChild(), env, reqPtype);
        result = result.withProperty(pname, pair.type);
        env = pair.env;
      }
    }
    return new EnvTypePair(env, result);
  }

  private EnvTypePair analyzeEnumObjLitBwd(
      Node objLit, TypeEnv outEnv, JSType requiredType) {
    if (objLit.getFirstChild() == null) {
      return new EnvTypePair(outEnv, requiredType);
    }
    String pname = NodeUtil.getObjectLitKeyName(objLit.getFirstChild());
    JSType enumeratedType =
        requiredType.getProp(new QualifiedName(pname)).getEnumeratedType();
    if (enumeratedType == null) {
      return new EnvTypePair(outEnv, requiredType);
    }
    TypeEnv env = outEnv;
    for (Node prop = objLit.getLastChild();
         prop != null;
         prop = objLit.getChildBefore(prop)) {
      env = analyzeExprBwd(prop.getFirstChild(), env, enumeratedType).env;
    }
    return new EnvTypePair(env, requiredType);
  }

  private boolean isPropertyTestCall(Node expr) {
    if (!expr.isCall()) {
      return false;
    }
    return expr.getFirstChild().isQualifiedName()
        && this.convention.isPropertyTestFunction(expr);
  }

  private boolean isFunctionBind(Node expr, TypeEnv env, boolean isFwd) {
    if (NodeUtil.isFunctionBind(expr)) {
      return true;
    }
    if (!expr.isGetProp() || !expr.isQualifiedName()
        || !expr.getLastChild().getString().equals("bind")) {
      return false;
    }
    Node recv = expr.getFirstChild();
    JSType recvType = isFwd ? analyzeExprFwd(recv, env).type : analyzeExprBwd(recv, env).type;
    return !recvType.isUnknown() && recvType.isSubtypeOf(commonTypes.topFunction());
  }

  private boolean isGoogTypeof(Node expr) {
    if (!expr.isCall()) {
      return false;
    }
    expr = expr.getFirstChild();
    return expr.isGetProp() && expr.getFirstChild().isName() &&
        expr.getFirstChild().getString().equals("goog") &&
        expr.getLastChild().getString().equals("typeOf");
  }

  private static JSType scalarValueToType(int token) {
    switch (token) {
      case Token.NUMBER:
        return JSType.NUMBER;
      case Token.STRING:
        return JSType.STRING;
      case Token.TRUE:
        return JSType.TRUE_TYPE;
      case Token.FALSE:
        return JSType.FALSE_TYPE;
      case Token.NULL:
        return JSType.NULL;
      default:
        throw new RuntimeException("The token isn't a scalar value " +
            Token.name(token));
    }
  }

  private void warnInvalidOperand(
      Node expr, int operatorType, Object expected, Object actual) {
    Preconditions.checkArgument(
        (expected instanceof String) || (expected instanceof JSType));
    Preconditions.checkArgument(
        (actual instanceof String) || (actual instanceof JSType));
    warnings.add(JSError.make(
        expr, INVALID_OPERAND_TYPE, Token.name(operatorType),
        expected.toString(), actual.toString()));
  }

  private static class EnvTypePair {
    TypeEnv env;
    JSType type;

    EnvTypePair(TypeEnv env, JSType type) {
      this.env = env;
      this.type = type;
    }

    static EnvTypePair addBinding(TypeEnv env, String varName, JSType type) {
      return new EnvTypePair(envPutType(env, varName, type), type);
    }

    static EnvTypePair join(EnvTypePair p1, EnvTypePair p2) {
      return new EnvTypePair(TypeEnv.join(p1.env, p2.env),
          JSType.join(p1.type, p2.type));
    }
  }

  private static JSType envGetType(TypeEnv env, String pname) {
    Preconditions.checkArgument(!pname.contains("."));
    return env.getType(pname);
  }

  private static TypeEnv envPutType(TypeEnv env, String varName, JSType type) {
    Preconditions.checkArgument(!varName.contains("."));
    return env.putType(varName, type);
  }

  private static class LValueResultFwd {
    TypeEnv env;
    JSType type;
    JSType declType;
    QualifiedName ptr;

    LValueResultFwd(
        TypeEnv env, JSType type, JSType declType, QualifiedName ptr) {
      Preconditions.checkNotNull(type);
      this.env = env;
      this.type = type;
      this.declType = declType;
      this.ptr = ptr;
    }
  }

  // Some expressions are analyzed during GTI, so they're skipped here.
  // But we must annotate them with a type anyway.
  private JSType markAndGetTypeOfPreanalyzedNode(Node qnameNode, TypeEnv env, boolean isFwd) {
    switch (qnameNode.getType()) {
      case Token.NAME: {
        JSType result = envGetType(env, qnameNode.getString());
        Preconditions.checkNotNull(result, "Null declared type@%s", qnameNode);
        if (isFwd) {
          maybeSetTypeI(qnameNode, result);
        }
        return result;
      }
      case Token.GETPROP: {
        JSType recvType = markAndGetTypeOfPreanalyzedNode(qnameNode.getFirstChild(), env, isFwd);
        String pname = qnameNode.getLastChild().getString();
        JSType result = null;
        if (recvType.isSubtypeOf(JSType.TOP_OBJECT)) {
          result = recvType.getProp(new QualifiedName(pname));
        }

        // TODO(dimvar): revisit this decision?
        // The old type system has a special type for Foo.prototype, even though
        // it is Object with extra properties.
        // We don't have a special type, so, for simplicity when converting to
        // the old types, we cheat here and annotate the Foo.prototype node as
        // a Foo instance.
        if (pname.equals("prototype")
            && (recvType.isConstructor() || recvType.isInterfaceDefinition())) {
          FunctionType ft = recvType.getFunTypeIfSingletonObj();
          result = ft.getInstanceTypeOfCtor();
        }
        if (result == null) {
          warnings.add(JSError.make(qnameNode, UNKNOWN_NAMESPACE_PROPERTY,
                  qnameNode.getQualifiedName()));
          return JSType.UNKNOWN;
        }

        Preconditions.checkNotNull(result, "Null declared type@%s", qnameNode);
        if (isFwd) {
          maybeSetTypeI(qnameNode, result);
        }
        return result;
      }
      default:
        throw new RuntimeException("markAndGetTypeOfPreanalyzedNode: unexpected node "
            + Token.name(qnameNode.getType()));
    }
  }

  private void maybeSetTypeI(Node n, JSType t) {
    TypeI ti = n.getTypeI();
    JSType oldType = (ti instanceof JSType) ? (JSType) ti : null;
    // When creating a function summary, we set a precise type on the function's
    // name node. Since we're visiting inner scopes first, the name node is
    // revisited after the function's scope is analyzed, and its type then can
    // be less precise. So, we keep the summary type.
    // TODO(dimvar): Look into why the name node has a less precise type in the
    // outer scope; we've already computed a good type for it, don't lose it.
    if (oldType == null) {
      n.setTypeI(t);
    }
  }

  private LValueResultFwd analyzeLValueFwd(
      Node expr, TypeEnv inEnv, JSType type) {
    return analyzeLValueFwd(expr, inEnv, type, false);
  }

  private LValueResultFwd analyzeLValueFwd(
      Node expr, TypeEnv inEnv, JSType type, boolean insideQualifiedName) {
    LValueResultFwd lvalResult = null;
    switch (expr.getType()) {
      case Token.THIS: {
        if (currentScope.hasThis()) {
          lvalResult = new LValueResultFwd(inEnv, envGetType(inEnv, THIS_ID),
              currentScope.getDeclaredTypeOf(THIS_ID),
              new QualifiedName(THIS_ID));
        } else {
          mayWarnAboutGlobalThis(expr, currentScope);
          lvalResult = new LValueResultFwd(inEnv, JSType.UNKNOWN, null, null);
        }
        break;
      }
      case Token.NAME: {
        String varName = expr.getString();
        JSType varType = analyzeExprFwd(expr, inEnv).type;
        lvalResult = new LValueResultFwd(inEnv, varType,
            currentScope.getDeclaredTypeOf(varName),
            varType.hasNonScalar() ? new QualifiedName(varName) : null);
        break;
      }
      case Token.GETPROP:
      case Token.GETELEM: {
        Node obj = expr.getFirstChild();
        Node prop = expr.getLastChild();
        QualifiedName pname = expr.isGetProp() || prop.isString()
            ? new QualifiedName(prop.getString()) : null;
        LValueResultFwd recvLvalue = analyzeReceiverLvalFwd(obj, pname, inEnv, type);
        if (!recvLvalue.type.isSubtypeOf(JSType.TOP_OBJECT)) {
          EnvTypePair pair = analyzeExprFwd(prop, recvLvalue.env, type);
          lvalResult = new LValueResultFwd(pair.env, type, null, null);
          break;
        }
        JSType indexType = recvLvalue.type.getIndexType();
        // (1) A getelem where the receiver is an IObject
        if (expr.isGetElem() && indexType != null) {
          lvalResult = analyzeIObjectElmLvalFwd(prop, recvLvalue, indexType);
          break;
        }
        // (2) A getelem where the prop is a string literal is like a getprop
        if (expr.isGetProp() || prop.isString()) {
          lvalResult = analyzePropLValFwd(obj, pname, recvLvalue, type, insideQualifiedName);
          break;
        }
        // (3) All other getelems
        // TODO(dimvar): there is some recomputation here; the receiver will be
        // analyzed again. Some more refactoring can fix this.
        EnvTypePair pair = analyzeExprFwd(expr, recvLvalue.env, type);
        lvalResult = new LValueResultFwd(pair.env, pair.type, null, null);
        break;
      }
      case Token.VAR: { // Can happen iff its parent is a for/in.
        Preconditions.checkState(NodeUtil.isForIn(expr.getParent()));
        Node vdecl = expr.getFirstChild();
        String name = vdecl.getString();
        // For/in can never have rhs of its VAR
        Preconditions.checkState(!vdecl.hasChildren());
        return new LValueResultFwd(inEnv, JSType.STRING, null, new QualifiedName(name));
      }
      default: {
        // Expressions that aren't lvalues should be handled because they may
        // be, e.g., the left child of a getprop.
        // We must check that they are not the direct lvalues.
        Preconditions.checkState(insideQualifiedName);
        EnvTypePair pair = analyzeExprFwd(expr, inEnv, type);
        return new LValueResultFwd(pair.env, pair.type, null, null);
      }
    }
    maybeSetTypeI(expr, lvalResult.type);
    mayWarnAboutUnknownType(expr, lvalResult.type);
    return lvalResult;
  }

  private LValueResultFwd analyzeIObjectElmLvalFwd(
      Node prop, LValueResultFwd recvLvalue, JSType indexType) {
    EnvTypePair pair = analyzeExprFwd(
        prop, recvLvalue.env, indexType.isBottom() ? JSType.UNKNOWN : indexType);
    if (mayWarnAboutBadIObjectIndex(prop, recvLvalue.type, pair.type, indexType)) {
      return new LValueResultFwd(pair.env, JSType.UNKNOWN, null, null);
    }
    JSType inferred = recvLvalue.type.getIndexedType();
    JSType declared = null;
    if (recvLvalue.declType != null) {
      JSType receiverAdjustedDeclType =
          recvLvalue.declType.removeType(JSType.NULL_OR_UNDEF);
      declared = receiverAdjustedDeclType.getIndexedType();
    }
    return new LValueResultFwd(pair.env, inferred, declared, null);
  }

  private EnvTypePair mayWarnAboutNullableReferenceAndTighten(
      Node obj, JSType recvType, JSType maybeSpecType, TypeEnv inEnv) {
    if (!recvType.isUnknown()
        && !recvType.isTop()
        && (JSType.NULL.isSubtypeOf(recvType)
            || JSType.UNDEFINED.isSubtypeOf(recvType))) {
      JSType minusNull = recvType.removeType(JSType.NULL_OR_UNDEF);
      if (!minusNull.isBottom()) {
        if (this.reportNullDeref) {
          warnings.add(JSError.make(
              obj, NULLABLE_DEREFERENCE, recvType.toString()));
        }
        TypeEnv outEnv = inEnv;
        if (obj.isQualifiedName()) {
          QualifiedName qname = QualifiedName.fromNode(obj);
          if (maybeSpecType != null && maybeSpecType.isSubtypeOf(minusNull)) {
            minusNull = maybeSpecType;
          }
          outEnv = updateLvalueTypeInEnv(inEnv, obj, qname, minusNull);
        }
        return new EnvTypePair(outEnv, minusNull);
      }
    }
    return new EnvTypePair(inEnv, recvType);
  }

  private LValueResultFwd analyzePropLValFwd(Node obj, QualifiedName pname,
      LValueResultFwd recvLvalue, JSType requiredType, boolean insideQualifiedName) {
    Preconditions.checkArgument(pname.isIdentifier());
    TypeEnv inEnv = recvLvalue.env;
    JSType recvType = recvLvalue.type;
    if (!recvType.isUnion() && !recvType.isSingletonObj()) {
      // The lvalue is a subtype of TOP_OBJECT, but does not contain an object
      // yet, eg, it is ?, truthy, or bottom.
      recvType = JSType.TOP_OBJECT.withLoose();
    }
    Node propAccessNode = obj.getParent();
    if (propAccessNode.isGetProp() &&
        propAccessNode.getParent().isAssign() &&
        mayWarnAboutPropCreation(pname, propAccessNode, recvType)) {
      return new LValueResultFwd(inEnv, requiredType, null, null);
    }
    if (!insideQualifiedName
        && mayWarnAboutConstProp(propAccessNode, recvType, pname)) {
      return new LValueResultFwd(inEnv, requiredType, null, null);
    }
    if (!recvType.hasProp(pname)) {
      if (recvType.isLoose()) {
        // For loose objects, create the inner property if it doesn't exist.
        recvType = recvType.withProperty(pname, JSType.TOP_OBJECT.withLoose());
        inEnv = updateLvalueTypeInEnv(inEnv, obj, recvLvalue.ptr, recvType);
      } else {
        // Warn for inexistent prop either on the non-top-level of a qualified
        // name, or for assignment ops that won't create a new property.
        boolean warnForInexistentProp = insideQualifiedName ||
            propAccessNode.getParent().getType() != Token.ASSIGN;
        if (warnForInexistentProp
            && !recvType.isUnknown()
            && !recvType.mayBeDict()) {
          mayWarnAboutInexistentProp(propAccessNode, recvType, pname, inEnv);
          return new LValueResultFwd(inEnv, requiredType, null, null);
        }
      }
    }
    if (propAccessNode.isGetElem()) {
      mayWarnAboutStructPropAccess(obj, recvType);
    } else if (propAccessNode.isGetProp()) {
      mayWarnAboutDictPropAccess(obj, recvType);
    }
    QualifiedName setterPname =
        new QualifiedName(SETTER_PREFIX + pname.getLeftmostName());
    if (recvType.hasProp(setterPname)) {
      FunctionType funType = recvType.getProp(setterPname).getFunType();
      Preconditions.checkNotNull(funType);
      JSType formalType = funType.getFormalType(0);
      Preconditions.checkState(!formalType.isBottom());
      return new LValueResultFwd(inEnv, formalType, formalType, null);
    }
    QualifiedName ptr = recvLvalue.ptr == null
        ? null : QualifiedName.join(recvLvalue.ptr, pname);
    return recvType.mayHaveProp(pname)
        ? new LValueResultFwd(
            inEnv, recvType.getProp(pname), recvType.getDeclaredProp(pname), ptr)
        : new LValueResultFwd(inEnv, JSType.UNKNOWN, null, ptr);
  }

  private LValueResultFwd analyzeReceiverLvalFwd(
      Node obj, QualifiedName pname, TypeEnv inEnv, JSType propType) {
    // pname is null when the property name is not known.
    Preconditions.checkArgument(pname == null || pname.isIdentifier());
    JSType reqObjType = pickReqObjType(obj.getParent());
    if (pname != null) {
      reqObjType = reqObjType.withProperty(pname, propType);
    }
    LValueResultFwd lvalue = analyzeLValueFwd(obj, inEnv, reqObjType, true);
    EnvTypePair pair = mayWarnAboutNullableReferenceAndTighten(
        obj, lvalue.type, null, lvalue.env);
    JSType lvalueType = pair.type.autobox();
    if (!lvalueType.isSubtypeOf(JSType.TOP_OBJECT)) {
      warnings.add(JSError.make(obj, PROPERTY_ACCESS_ON_NONOBJECT,
              getPropNameForErrorMsg(obj.getParent()), lvalueType.toString()));
    }
    lvalue.type = lvalueType;
    lvalue.env = pair.env;
    return lvalue;
  }

  private static class LValueResultBwd {
    TypeEnv env;
    JSType type;
    QualifiedName ptr;

    LValueResultBwd(TypeEnv env, JSType type, QualifiedName ptr) {
      Preconditions.checkNotNull(type);
      this.env = env;
      this.type = type;
      this.ptr = ptr;
    }
  }

  private LValueResultBwd analyzeLValueBwd(
      Node expr, TypeEnv outEnv, JSType type, boolean doSlicing) {
    return analyzeLValueBwd(expr, outEnv, type, doSlicing, false);
  }

  /** When {@code doSlicing} is set, remove the lvalue from the returned env */
  private LValueResultBwd analyzeLValueBwd(Node expr, TypeEnv outEnv,
      JSType type, boolean doSlicing, boolean insideQualifiedName) {
    switch (expr.getType()) {
      case Token.THIS:
      case Token.NAME: {
        EnvTypePair pair = analyzeExprBwd(expr, outEnv, type);
        String name = expr.getQualifiedName();
        JSType declType = currentScope.getDeclaredTypeOf(name);
        if (doSlicing) {
          pair.env = envPutType(pair.env, name,
              declType != null ? declType : JSType.UNKNOWN);
        }
        return new LValueResultBwd(pair.env, pair.type,
            pair.type.hasNonScalar() ? new QualifiedName(name) : null);
      }
      case Token.GETPROP: {
        Node obj = expr.getFirstChild();
        QualifiedName pname =
            new QualifiedName(expr.getLastChild().getString());
        return analyzePropLValBwd(obj, pname, outEnv, type, doSlicing);
      }
      case Token.GETELEM: {
        if (expr.getLastChild().isString()) {
          Node obj = expr.getFirstChild();
          QualifiedName pname =
              new QualifiedName(expr.getLastChild().getString());
          return analyzePropLValBwd(obj, pname, outEnv, type, doSlicing);
        }
        EnvTypePair pair = analyzeExprBwd(expr, outEnv, type);
        return new LValueResultBwd(pair.env, pair.type, null);
      }
      default: {
        // Expressions that aren't lvalues should be handled because they may
        // be, e.g., the left child of a getprop.
        // We must check that they are not the direct lvalues.
        Preconditions.checkState(insideQualifiedName);
        EnvTypePair pair = analyzeExprBwd(expr, outEnv, type);
        return new LValueResultBwd(pair.env, pair.type, null);
      }
    }
  }

  private LValueResultBwd analyzePropLValBwd(Node obj, QualifiedName pname,
      TypeEnv outEnv, JSType type, boolean doSlicing) {
    Preconditions.checkArgument(pname.isIdentifier());
    JSType reqObjType =
        pickReqObjType(obj.getParent()).withProperty(pname, type);
    LValueResultBwd lvalue =
        analyzeLValueBwd(obj, outEnv, reqObjType, false, true);
    if (lvalue.ptr != null) {
      lvalue.ptr = QualifiedName.join(lvalue.ptr, pname);
      if (doSlicing) {
        String objName = lvalue.ptr.getLeftmostName();
        QualifiedName props = lvalue.ptr.getAllButLeftmost();
        JSType objType = envGetType(lvalue.env, objName);
        // withoutProperty only removes inferred properties
        JSType slicedObjType = objType.withoutProperty(props);
        lvalue.env = envPutType(lvalue.env, objName, slicedObjType);
      }
    }
    lvalue.type = lvalue.type.mayHaveProp(pname) ?
        lvalue.type.getProp(pname) : JSType.UNKNOWN;
    return lvalue;
  }

  private static JSType pickReqObjType(Node expr) {
    int exprKind = expr.getType();
    switch (exprKind) {
      case Token.OBJECTLIT: {
        JSDocInfo jsdoc = expr.getJSDocInfo();
        if (jsdoc != null && jsdoc.makesStructs()) {
          return JSType.TOP_STRUCT;
        }
        if (jsdoc != null && jsdoc.makesDicts()) {
          return JSType.TOP_DICT;
        }
        return JSType.TOP_OBJECT;
      }
      case Token.FOR:
        Preconditions.checkState(NodeUtil.isForIn(expr));
        return JSType.TOP_OBJECT;
      case Token.GETPROP:
      case Token.GETELEM:
      case Token.IN:
        return JSType.TOP_OBJECT;
      default:
        throw new RuntimeException(
            "Unhandled node for pickReqObjType: " + Token.name(exprKind));
    }
  }

  private static String getReadableCalleeName(Node expr) {
    return expr.isQualifiedName() ? expr.getQualifiedName() : "";
  }

  private static JSType specializeKeep2ndWhenBottom(
      JSType toBeSpecialized, JSType fallback) {
    JSType specializedType = toBeSpecialized.specialize(fallback);
    return specializedType.isBottom() ? fallback : specializedType;
  }

  TypeEnv getEntryTypeEnv() {
    return getOutEnv(cfg.getEntry());
  }

  private static class DeferredCheck {
    final Node callSite;
    final NTIScope callerScope;
    final NTIScope calleeScope;
    // Null types means that they were declared
    // (and should have been checked during inference)
    JSType expectedRetType;
    List<JSType> argTypes;

    DeferredCheck(
        Node callSite,
        JSType expectedRetType,
        NTIScope callerScope,
        NTIScope calleeScope) {
      this.callSite = callSite;
      this.expectedRetType = expectedRetType;
      this.callerScope = callerScope;
      this.calleeScope = calleeScope;
    }

    void updateReturn(JSType expectedRetType) {
      if (this.expectedRetType != null) {
        this.expectedRetType = JSType.meet(this.expectedRetType, expectedRetType);
      }
    }

    void updateArgTypes(List<JSType> argTypes) {
      this.argTypes = argTypes;
    }

    private void runCheck(
        Map<NTIScope, JSType> summaries, WarningReporter warnings) {
      FunctionType fnSummary = summaries.get(this.calleeScope).getFunType();
      println(
          "Running deferred check of function: ", calleeScope.getReadableName(),
          " with FunctionSummary of: ", fnSummary, " and callsite ret: ",
          expectedRetType, " args: ", argTypes);
      if (this.expectedRetType != null &&
          !fnSummary.getReturnType().isSubtypeOf(this.expectedRetType)) {
        warnings.add(JSError.make(
            this.callSite, INVALID_INFERRED_RETURN_TYPE,
            this.expectedRetType.toString(),
            fnSummary.getReturnType().toString()));
      }
      int i = 0;
      Node argNode = callSite.getSecondChild();
      // this.argTypes can be null if in the fwd direction the analysis of the
      // call return prematurely, eg, because of a WRONG_ARGUMENT_COUNT.
      if (this.argTypes == null) {
        return;
      }
      for (JSType argType : this.argTypes) {
        JSType formalType = fnSummary.getFormalType(i);
        if (argNode.isName() && callerScope.isKnownFunction(argNode.getString())) {
          argType = summaries.get(callerScope.getScope(argNode.getString()));
        }
        if (argType != null && !argType.isSubtypeOf(formalType)) {
          warnings.add(JSError.make(
              argNode, INVALID_ARGUMENT_TYPE,
              Integer.toString(i + 1), calleeScope.getReadableName(),
              formalType.toString(),
              argType.toString()));
        }
        i++;
        argNode = argNode.getNext();
      }
    }

    @Override
    public boolean equals(Object o) {
      Preconditions.checkArgument(o instanceof DeferredCheck);
      DeferredCheck dc2 = (DeferredCheck) o;
      return callSite == dc2.callSite &&
          callerScope == dc2.callerScope &&
          calleeScope == dc2.calleeScope &&
          Objects.equals(expectedRetType, dc2.expectedRetType) &&
          Objects.equals(argTypes, dc2.argTypes);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          callSite, callerScope, calleeScope, expectedRetType, argTypes);
    }
  }
}
