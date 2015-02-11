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
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.CodingConvention.AssertionFunctionSpec;
import com.google.javascript.jscomp.CodingConvention.Bind;
import com.google.javascript.jscomp.GlobalTypeInfo.Scope;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.jscomp.newtypes.DeclaredFunctionType;
import com.google.javascript.jscomp.newtypes.FunctionType;
import com.google.javascript.jscomp.newtypes.FunctionTypeBuilder;
import com.google.javascript.jscomp.newtypes.JSType;
import com.google.javascript.jscomp.newtypes.JSTypes;
import com.google.javascript.jscomp.newtypes.QualifiedName;
import com.google.javascript.jscomp.newtypes.TypeEnv;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.StaticSourceFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * New type inference algorithm.
 *
 * Under development. Use cautiously.
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 *
 * Features left to implement:
 * - @private (maybe)
 * - @protected (maybe)
 * - arguments array
 * - separate scope for catch variables
 * - closure-specific constructs
 * - bounded quantification for generics
 */
public class NewTypeInference implements CompilerPass {

  static final DiagnosticType MISTYPED_ASSIGN_RHS = DiagnosticType.warning(
      "JSC_MISTYPED_ASSIGN_RHS",
      "The right side in the assignment is not a subtype of the left side.\n" +
      "left side  : {0}\n" +
      "right side : {1}\n");

  static final DiagnosticType INVALID_OPERAND_TYPE = DiagnosticType.warning(
      "JSC_INVALID_OPERAND_TYPE",
      "Invalid type(s) for operator {0}.\n" +
      "expected : {1}\n" +
      "found    : {2}\n");

  static final DiagnosticType RETURN_NONDECLARED_TYPE = DiagnosticType.warning(
      "JSC_RETURN_NONDECLARED_TYPE",
      "Returned type does not match declared return type.\n" +
      "declared : {0}\n" +
      "found    : {1}\n");

  static final DiagnosticType INVALID_INFERRED_RETURN_TYPE =
      DiagnosticType.warning(
          "JSC_INVALID_INFERRED_RETURN_TYPE",
          "Function called in context that expects incompatible type.\n" +
          "expected : {0}\n" +
          "found    : {1}\n");

  static final DiagnosticType INVALID_ARGUMENT_TYPE = DiagnosticType.warning(
      "JSC_INVALID_ARGUMENT_TYPE",
      "Invalid type for parameter {0} of function {1}.\n" +
      "expected : {2}\n" +
      "found    : {3}\n");

  static final DiagnosticType CROSS_SCOPE_GOTCHA = DiagnosticType.warning(
      "JSC_CROSS_SCOPE_GOTCHA",
      "You thought we weren't going to notice? Guess again.\n" +
      "Variable {0} typed inconsistently across scopes.\n" +
      "In outer scope : {1}\n" +
      "In inner scope : {2}\n");

  static final DiagnosticType POSSIBLY_INEXISTENT_PROPERTY =
      DiagnosticType.warning(
          "JSC_POSSIBLY_INEXISTENT_PROPERTY",
          "Property {0} may not be present on {1}.");

  static final DiagnosticType NULLABLE_DEREFERENCE =
      DiagnosticType.warning(
          "JSC_NULLABLE_DEREFERENCE",
          "Attempt to use nullable type {0}.");

  static final DiagnosticType PROPERTY_ACCESS_ON_NONOBJECT =
      DiagnosticType.warning(
          "JSC_PROPERTY_ACCESS_ON_NONOBJECT",
          "Cannot access property {0} of non-object type {1}.");

  static final DiagnosticType CALL_FUNCTION_WITH_BOTTOM_FORMAL =
      DiagnosticType.warning(
          "JSC_CALL_FUNCTION_WITH_BOTTOM_FORMAL",
          "The #{0} formal parameter of this function has an invalid type, " +
          "which prevents the function from being called.\n" +
          "Please change the type.");

  static final DiagnosticType NOT_UNIQUE_INSTANTIATION =
      DiagnosticType.warning(
          "JSC_NOT_UNIQUE_INSTANTIATION",
          "Illegal instantiation for type variable {0}.\n" +
          "You can only specify one type. Found {1}.");

  static final DiagnosticType FAILED_TO_UNIFY =
      DiagnosticType.warning(
          "JSC_FAILED_TO_UNIFY",
          "Could not instantiate type {0} with {1}.");

  static final DiagnosticType NON_NUMERIC_ARRAY_INDEX =
      DiagnosticType.warning(
          "JSC_NON_NUMERIC_ARRAY_INDEX",
          "Expected numeric array index but found {0}.");

  static final DiagnosticType INVALID_OBJLIT_PROPERTY_TYPE =
      DiagnosticType.warning(
          "JSC_INVALID_OBJLIT_PROPERTY_TYPE",
          "Object-literal property declared as {0} but has type {1}.");

  static final DiagnosticType FORIN_EXPECTS_OBJECT =
      DiagnosticType.warning(
          "JSC_FORIN_EXPECTS_OBJECT",
          "For/in expects an object, found type {0}.");

  static final DiagnosticType FORIN_EXPECTS_STRING_KEY =
      DiagnosticType.warning(
          "JSC_FORIN_EXPECTS_STRING_KEY",
          "For/in creates string keys, but variable has declared type {1}.");

  static final DiagnosticType CONST_REASSIGNED =
      DiagnosticType.warning(
          "JSC_CONST_REASSIGNED",
          "Cannot change the value of a constant.");

  static final DiagnosticType NOT_A_CONSTRUCTOR =
      DiagnosticType.warning(
          "JSC_NOT_A_CONSTRUCTOR",
          "Expected a constructor but found type {0}.");

  static final DiagnosticType ASSERT_FALSE =
      DiagnosticType.warning(
          "JSC_ASSERT_FALSE",
          "Assertion is always false. Please use a throw or fail() instead.");

  static final DiagnosticType UNKNOWN_ASSERTION_TYPE =
      DiagnosticType.warning(
          "JSC_UNKNOWN_ASSERTION_TYPE",
          "Assert with unknown asserted type.");

  static final DiagnosticType INVALID_THIS_TYPE_IN_BIND =
      DiagnosticType.warning(
          "JSC_INVALID_THIS_TYPE_IN_BIND",
          "The first argument to bind has type {0} which is not a subtype of"
          + " {1}.");

  static final DiagnosticType CANNOT_BIND_CTOR =
      DiagnosticType.warning(
          "JSC_CANNOT_BIND_CTOR",
          "We do not support using .bind on constructor functions.");

  static final DiagnosticType GOOG_BIND_EXPECTS_FUNCTION =
      DiagnosticType.warning(
          "JSC_GOOG_BIND_EXPECTS_FUNCTION",
          "The first argument to goog.bind/goog.partial must be a function.");

  static final DiagnosticGroup ALL_DIAGNOSTICS = new DiagnosticGroup(
      ASSERT_FALSE,
      CALL_FUNCTION_WITH_BOTTOM_FORMAL,
      CANNOT_BIND_CTOR,
      CONST_REASSIGNED,
      CROSS_SCOPE_GOTCHA,
      FAILED_TO_UNIFY,
      FORIN_EXPECTS_OBJECT,
      FORIN_EXPECTS_STRING_KEY,
      GOOG_BIND_EXPECTS_FUNCTION,
      INVALID_ARGUMENT_TYPE,
      INVALID_INFERRED_RETURN_TYPE,
      INVALID_OBJLIT_PROPERTY_TYPE,
      INVALID_OPERAND_TYPE,
      INVALID_THIS_TYPE_IN_BIND,
      MISTYPED_ASSIGN_RHS,
      NON_NUMERIC_ARRAY_INDEX,
      NOT_A_CONSTRUCTOR,
      NOT_UNIQUE_INSTANTIATION,
      NULLABLE_DEREFERENCE,
      POSSIBLY_INEXISTENT_PROPERTY,
      PROPERTY_ACCESS_ON_NONOBJECT,
      RETURN_NONDECLARED_TYPE,
      UNKNOWN_ASSERTION_TYPE,
      CheckGlobalThis.GLOBAL_THIS,
      CheckMissingReturn.MISSING_RETURN_STATEMENT,
      TypeCheck.CONSTRUCTOR_NOT_CALLABLE,
      TypeCheck.ILLEGAL_OBJLIT_KEY,
      TypeCheck.ILLEGAL_PROPERTY_CREATION,
      TypeCheck.IN_USED_WITH_STRUCT,
      TypeCheck.INEXISTENT_PROPERTY,
      TypeCheck.NOT_CALLABLE,
      TypeCheck.WRONG_ARGUMENT_COUNT,
      TypeValidator.ILLEGAL_PROPERTY_ACCESS,
      TypeValidator.INVALID_CAST,
      TypeValidator.UNKNOWN_TYPEOF_VALUE);

  private static String getFileWhereWarningOccurred(JSError warning) {
    StaticSourceFile f = warning.node.getStaticSourceFile();
    return f == null ? "" : f.getName();
  }

  public static class WarningReporter {
    AbstractCompiler compiler;
    WarningReporter(AbstractCompiler compiler) { this.compiler = compiler; }

    void add(JSError warning) {
      // We check the file name to avoid some warnings in code generated
      // by the ES6 transpilation passes.
      // TODO(dimvar): typecheck that code properly and remove this.
      if (getFileWhereWarningOccurred(warning).startsWith(" [synthetic")
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
  private Map<Scope, JSType> summaries;
  private Map<Node, DeferredCheck> deferredChecks;
  private ControlFlowGraph<Node> cfg;
  private Scope currentScope;
  private GlobalTypeInfo symbolTable;
  private JSTypes commonTypes;
  private static final String RETVAL_ID = "%return";
  private static final String GETTER_PREFIX = "%getter_fun";
  private static final String SETTER_PREFIX = "%setter_fun";
  private final String ABSTRACT_METHOD_NAME;
  private final Map<String, AssertionFunctionSpec> assertionFunctionsMap;
  private static final QualifiedName NUMERIC_INDEX = new QualifiedName("0");
  private final boolean isClosurePassOn;

  // Used only for development
  private static boolean showDebuggingPrints = false;
  static boolean measureMem = false;
  private static long peakMem = 0;

  NewTypeInference(AbstractCompiler compiler, boolean isClosurePassOn) {
    this.warnings = new WarningReporter(compiler);
    this.compiler = compiler;
    this.convention = compiler.getCodingConvention();
    this.envs = new HashMap<>();
    this.summaries = new HashMap<>();
    this.deferredChecks = new HashMap<>();
    this.isClosurePassOn = isClosurePassOn;
    this.ABSTRACT_METHOD_NAME = convention.getAbstractMethodName();
    assertionFunctionsMap = new HashMap<>();
    for (AssertionFunctionSpec assertionFunction :
             convention.getAssertionFunctions()) {
      assertionFunctionsMap.put(assertionFunction.getFunctionName(),
          assertionFunction);
    }
  }

  @VisibleForTesting // Only used from tests
  public Scope processForTesting(Node externs, Node root) {
    process(externs, root);
    return symbolTable.getGlobalScope();
  }

  @Override
  public void process(Node externs, Node root) {
    try {
      symbolTable = compiler.getSymbolTable();
      commonTypes = symbolTable.getTypesUtilObject();
      for (Scope scope : symbolTable.getScopes()) {
        analyzeFunction(scope);
        envs.clear();
      }
      for (DeferredCheck check : deferredChecks.values()) {
        check.runCheck(summaries, warnings);
      }
      if (measureMem) {
        System.out.println("Peak mem: " + peakMem + "MB");
      }
    } catch (Exception unexpectedException) {
      String message = unexpectedException.getMessage();
      if (currentScope != null) {
        message += "\nIn scope: " + currentScope;
      }
      compiler.throwInternalError(message, unexpectedException);
    }
  }

  static void updatePeakMem() {
    Runtime rt = Runtime.getRuntime();
    long currentUsedMem = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
    if (currentUsedMem > peakMem) {
      peakMem = currentUsedMem;
    }
  }

  private boolean isArrayType(JSType t) {
    if (commonTypes.getArrayInstance().isUnknown() // no externs
        || t.isUnknown() || t.isLoose()
        || t.isEnumElement() && t.getEnumeratedType().isUnknown()) {
      return false;
    }
    return t.isSubtypeOf(commonTypes.getArrayInstance());
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

    Set<TypeEnv> envSet = new HashSet<>();
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
    Preconditions.checkArgument(!dn.getOutEdges().isEmpty());
    List<DiGraphEdge<Node, ControlFlowGraph.Branch>> outEdges =
        dn.getOutEdges();
    if (outEdges.size() == 1) {
      return envs.get(outEdges.get(0));
    }

    Set<TypeEnv> envSet = new HashSet<>();
    for (DiGraphEdge<Node, ControlFlowGraph.Branch> de : outEdges) {
      TypeEnv env = envs.get(de);
      if (env != null) {
        envSet.add(env);
      }
    }
    return TypeEnv.join(envSet);
  }

  private TypeEnv setOutEnv(
      DiGraphNode<Node, ControlFlowGraph.Branch> dn, TypeEnv e) {
    for (DiGraphEdge<Node, ControlFlowGraph.Branch> de : dn.getOutEdges()) {
      envs.put(de, e);
    }
    return e;
  }

  private TypeEnv setInEnv(
      DiGraphNode<Node, ControlFlowGraph.Branch> dn, TypeEnv e) {
    for (DiGraphEdge<Node, ControlFlowGraph.Branch> de : dn.getInEdges()) {
      envs.put(de, e);
    }
    return e;
  }

  // Initialize the type environments on the CFG edges before the FWD analysis.
  private void initEdgeEnvsFwd(TypeEnv entryEnv) {
    envs.clear();

    // For function scopes, add the formal parameters and the free variables
    // from outer scopes to the environment.
    Set<String> nonLocals = new HashSet<>();
    if (currentScope.isFunction()) {
      if (currentScope.getName() != null) {
        nonLocals.add(currentScope.getName());
      }
      nonLocals.addAll(currentScope.getOuterVars());
      nonLocals.addAll(currentScope.getFormals());
      if (currentScope.hasThis()) {
        nonLocals.add("this");
      }
      entryEnv = envPutType(entryEnv, RETVAL_ID, JSType.UNDEFINED);
    } else {
      nonLocals.addAll(currentScope.getExterns());
    }
    for (String name : nonLocals) {
      JSType declType = currentScope.getDeclaredTypeOf(name);
      JSType initType = declType == null
          ? envGetType(entryEnv, name) : pickInitialType(declType);
      entryEnv = envPutType(entryEnv, name, initType);
    }

    // For all scopes, add local variables and (local) function definitions
    // to the environment.
    for (String local : currentScope.getLocals()) {
      entryEnv = envPutType(entryEnv, local, JSType.UNDEFINED);
    }
    for (String fnName : currentScope.getLocalFunDefs()) {
      JSType summaryType = getSummaryOfLocalFunDef(fnName);
      FunctionType fnType = summaryType.getFunType();
      if (fnType.isConstructor() || fnType.isInterfaceDefinition()) {
        summaryType = fnType.createConstructorObject(commonTypes.getFunctionType());
      } else {
        summaryType = summaryType.withProperty(
            new QualifiedName("prototype"), JSType.TOP_OBJECT);
      }
      entryEnv = envPutType(entryEnv, fnName, summaryType);
    }
    println("Keeping env: ", entryEnv);
    setOutEnv(cfg.getEntry(), entryEnv);
  }

  private TypeEnv getTypeEnvFromDeclaredTypes() {
    TypeEnv env = new TypeEnv();
    Set<String> varNames = currentScope.getOuterVars();
    varNames.addAll(currentScope.getLocals());
    varNames.addAll(currentScope.getExterns());
    if (currentScope.isFunction()) {
      if (currentScope.getName() != null) {
        varNames.add(currentScope.getName());
      }
      varNames.addAll(currentScope.getFormals());
      if (currentScope.hasThis()) {
        varNames.add("this");
      }
    }
    for (String varName : varNames) {
      JSType declType = currentScope.getDeclaredTypeOf(varName);
      env = envPutType(env, varName,
          declType == null ? JSType.UNKNOWN : pickInitialType(declType));
    }
    for (String fnName : currentScope.getLocalFunDefs()) {
      JSType summaryType = getSummaryOfLocalFunDef(fnName);
      FunctionType fnType = summaryType.getFunType();
      if (fnType.isConstructor() || fnType.isInterfaceDefinition()) {
        summaryType = fnType.createConstructorObject(commonTypes.getFunctionType());
      } else {
        summaryType = summaryType.withProperty(
            new QualifiedName("prototype"), JSType.TOP_OBJECT);
      }
      env = envPutType(env, fnName, summaryType);
    }
    return env;
  }

  private JSType getSummaryOfLocalFunDef(String name) {
    JSType summaryType = summaries.get(currentScope.getScope(name));
    if (summaryType == null) {
      // Functions defined in externs have no summary, so use the declared type
      summaryType = currentScope.getDeclaredTypeOf(name);
    }
    return summaryType;
  }

  // Initialize the type environments on the CFG edges before the BWD analysis.
  private void initEdgeEnvsBwd() {
    TypeEnv env = getTypeEnvFromDeclaredTypes();
    // Ideally, we would like to only set the in edges of the implicit return
    // rather than all edges. However, throws can have out edges not connected
    // to the implicit return, so we simply initialize all edges.
    initEdgeEnvs(env);
  }

  private void initEdgeEnvs(TypeEnv env) {
    for (DiGraphEdge<Node, ControlFlowGraph.Branch> e : cfg.getEdges()) {
      envs.put(e, env);
    }
  }

  private JSType pickInitialType(JSType declType) {
    Preconditions.checkNotNull(declType);
    FunctionType funType = declType.getFunTypeIfSingletonObj();
    if (funType == null) {
      return declType;
    } else if (funType.isConstructor() || funType.isInterfaceDefinition()) {
      // TODO(dimvar): when declType is a union, consider also creating
      // appropriate ctor objs. (This is going to be rare.)
      return funType.createConstructorObject(commonTypes.getFunctionType());
    } else {
      return declType.withProperty(
          new QualifiedName("prototype"), JSType.TOP_OBJECT);
    }
  }

  private void buildWorkset(
      DiGraphNode<Node, ControlFlowGraph.Branch> dn,
      List<DiGraphNode<Node, ControlFlowGraph.Branch>> workset) {
    buildWorksetHelper(dn, workset,
        new HashSet<DiGraphNode<Node, ControlFlowGraph.Branch>>());
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

  private void analyzeFunction(Scope scope) {
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
    if (scope.isFunction() && scope.hasUndeclaredFormalsOrOuters()) {
      Collections.reverse(workset);
      initEdgeEnvsBwd();
      analyzeFunctionBwd(workset);
      Collections.reverse(workset);
      // TODO(dimvar): Revisit what we throw away after the bwd analysis
      TypeEnv entryEnv = getEntryTypeEnv();
      initEdgeEnvsFwd(entryEnv);
      if (measureMem) {
        updatePeakMem();
      }
    } else {
      TypeEnv entryEnv = getTypeEnvFromDeclaredTypes();
      initEdgeEnvsFwd(entryEnv);
    }
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
      if (n.isThrow()) { // Throw statements have no out edges.
        // TODO(blickly): Support analyzing the body of the THROW
        continue;
      }
      TypeEnv outEnv = getOutEnv(dn);
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
            JSType declRetType = currentScope.getDeclaredType().getReturnType();
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
      setInEnv(dn, inEnv);
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
        case Token.CATCH:
        case Token.CONTINUE:
        case Token.DEFAULT_CASE:
        case Token.DEBUGGER:
        case Token.EMPTY:
        case Token.FUNCTION:
        case Token.SCRIPT:
        case Token.TRY:
          outEnv = inEnv;
          break;
        case Token.EXPR_RESULT:
          println("\tsemi ", Token.name(n.getFirstChild().getType()));
          outEnv = analyzeExprFwd(n.getFirstChild(), inEnv, JSType.UNKNOWN).env;
          break;
        case Token.RETURN: {
          Node retExp = n.getFirstChild();
          JSType declRetType = currentScope.getDeclaredType().getReturnType();
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
            Node obj = n.getChildAtIndex(1);
            EnvTypePair pair = analyzeExprFwd(obj, inEnv, pickReqObjType(n));
            JSType objType = pair.type;
            if (!objType.isSubtypeOf(JSType.TOP_OBJECT)) {
              warnings.add(JSError.make(
                  obj, FORIN_EXPECTS_OBJECT, objType.toString()));
            } else if (objType.isStruct()) {
              warnings.add(JSError.make(obj, TypeCheck.IN_USED_WITH_STRUCT));
            }
            Node lhs = n.getFirstChild();
            LValueResultFwd lval = analyzeLValueFwd(lhs, inEnv, JSType.STRING);
            if (lval.declType != null &&
                !commonTypes.isStringScalarOrObj(lval.declType)) {
              warnings.add(JSError.make(lhs, FORIN_EXPECTS_STRING_KEY,
                  lval.declType.toString()));
              outEnv = lval.env;
            } else {
              outEnv = updateLvalueTypeInEnv(
                 lval.env, lhs, lval.ptr, JSType.STRING);
            }
            break;
          }
          conditional = true;
          analyzeConditionalStmFwd(
              dn, NodeUtil.getConditionExpression(n), inEnv);
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

  // TODO(dimvar): differentiate for/in to not treat its cond as boolean, but
  // as an assignment to a string.
  private void analyzeConditionalStmFwd(
      DiGraphNode<Node, ControlFlowGraph.Branch> stm,
      Node cond, TypeEnv inEnv) {
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

  private void createSummary(Scope fn) {
    TypeEnv entryEnv = getEntryTypeEnv();
    TypeEnv exitEnv = getFinalTypeEnv();
    FunctionTypeBuilder builder = new FunctionTypeBuilder();

    DeclaredFunctionType declType = fn.getDeclaredType();
    int reqArity = declType.getRequiredArity();
    int optArity = declType.getOptionalArity();
    if (declType.isGeneric()) {
      builder.addTypeParameters(declType.getTypeParameters());
    }

    // Every trailing undeclared formal whose inferred type is ?
    // or contains undefined can be marked as optional.
    List<String> formals = fn.getFormals();
    for (int i = reqArity - 1; i >= 0; i--) {
      JSType formalType = fn.getDeclaredType().getFormalType(i);
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
      builder.addOuterVarPrecondition(
          outer, getTypeAfterFwd(outer, entryEnv, exitEnv));
    }

    builder.addNominalType(declType.getNominalType());
    builder.addReceiverType(declType.getReceiverType());
    JSType declRetType = declType.getReturnType();
    JSType actualRetType = envGetType(exitEnv, RETVAL_ID);

    if (declRetType != null) {
      builder.addRetType(declRetType);
      if (!isAllowedToNotReturn(fn) &&
          !JSType.UNDEFINED.isSubtypeOf(declRetType) &&
          hasPathWithNoReturn(cfg)) {
        warnings.add(JSError.make(fn.getRoot(),
                CheckMissingReturn.MISSING_RETURN_STATEMENT,
                declRetType.toString()));
      }
    } else if (declType.getNominalType() == null) {
      builder.addRetType(actualRetType);
    } else {
      // Don't infer a return type for constructors
      builder.addRetType(JSType.UNKNOWN);
    }
    JSType summary = commonTypes.fromFunctionType(builder.buildFunction());
    println("Function summary for ", fn.getReadableName());
    println("\t", summary);
    summaries.put(fn, summary);
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

  private static boolean isAllowedToNotReturn(Scope methodScope) {
    Node fn = methodScope.getRoot();
    if (fn.isFromExterns()) {
      return true;
    }
    if (!NodeUtil.isPrototypeMethod(fn)) {
      return false;
    }
    // TODO(dimvar): We need all the qname here before .prototype, not just
    // the qname root; see testAnnotatedPropertyOnInterface1
    String typeName =
        NodeUtil.getRootOfQualifiedName(fn.getParent().getFirstChild())
        .getString();
    JSType t = methodScope.getDeclaredTypeOf(typeName);
    return t != null && t.isInterfaceDefinition();
  }

  private static boolean hasPathWithNoReturn(ControlFlowGraph<Node> cfg) {
    for (DiGraphNode<Node, ControlFlowGraph.Branch> dn :
             cfg.getDirectedPredNodes(cfg.getImplicitReturn())) {
      if (!dn.getValue().isReturn()) {
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
    if (NodeUtil.isNamespaceDecl(nameNode)) {
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
    Preconditions.checkArgument(
        requiredType != null && !requiredType.isBottom());
    switch (expr.getType()) {
      case Token.EMPTY: // can be created by a FOR with empty condition
        return new EnvTypePair(inEnv, JSType.UNKNOWN);
      case Token.FUNCTION: {
        String fnName = symbolTable.getFunInternalName(expr);
        JSType fnType = envGetType(inEnv, fnName);
        Preconditions.checkState(fnType != null,
            "Could not find type for %s", fnName);
        return new EnvTypePair(inEnv, fnType);
      }
      case Token.FALSE:
      case Token.NULL:
      case Token.NUMBER:
      case Token.STRING:
      case Token.TRUE:
        return new EnvTypePair(inEnv, scalarValueToType(expr.getType()));
      case Token.OBJECTLIT:
        return analyzeObjLitFwd(expr, inEnv, requiredType, specializedType);
      case Token.THIS: {
        if (!currentScope.hasThis()) {
          warnings.add(JSError.make(expr, CheckGlobalThis.GLOBAL_THIS));
          return new EnvTypePair(inEnv, JSType.UNKNOWN);
        }
        JSType thisType = currentScope.getDeclaredTypeOf("this");
        return new EnvTypePair(inEnv, thisType);
      }
      case Token.NAME:
        return analyzeNameFwd(expr, inEnv, requiredType, specializedType);
      case Token.AND:
      case Token.OR:
        return analyzeLogicalOpFwd(expr, inEnv, requiredType, specializedType);
      case Token.INC:
      case Token.DEC:
        return analyzeIncDecFwd(expr, inEnv, requiredType);
      case Token.BITNOT:
      case Token.NEG:
        return analyzeUnaryNumFwd(expr, inEnv);
      case Token.POS: {
        // We are more permissive with +, because it is used to coerce to number
        EnvTypePair pair = analyzeExprFwd(expr.getFirstChild(), inEnv);
        pair.type = JSType.NUMBER;
        return pair;
      }
      case Token.TYPEOF: {
        EnvTypePair pair = analyzeExprFwd(expr.getFirstChild(), inEnv);
        pair.type = JSType.STRING;
        return pair;
      }
      case Token.INSTANCEOF:
        return analyzeInstanceofFwd(expr, inEnv, specializedType);
      case Token.ADD:
        return analyzeAddFwd(expr, inEnv);
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
        return analyzeBinaryNumericOpFwd(expr, inEnv);
      case Token.ASSIGN:
        return analyzeAssignFwd(expr, inEnv, requiredType, specializedType);
      case Token.ASSIGN_ADD:
        return analyzeAssignAddFwd(expr, inEnv, requiredType);
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
        return analyzeAssignNumericOpFwd(expr, inEnv);
      case Token.SHEQ:
      case Token.SHNE:
        return analyzeStrictComparisonFwd(expr.getType(),
            expr.getFirstChild(), expr.getLastChild(), inEnv, specializedType);
      case Token.EQ:
      case Token.NE:
        return analyzeNonStrictComparisonFwd(expr, inEnv, specializedType);
      case Token.LT:
      case Token.GT:
      case Token.LE:
      case Token.GE:
        return analyzeLtGtFwd(expr, inEnv);
      case Token.GETPROP:
        Preconditions.checkState(
            !NodeUtil.isAssignmentOp(expr.getParent()) ||
            !NodeUtil.isLValue(expr));
        if (expr.getBooleanProp(Node.ANALYZED_DURING_GTI)) {
          expr.removeProp(Node.ANALYZED_DURING_GTI);
          return new EnvTypePair(inEnv, requiredType);
        }
        return analyzePropAccessFwd(
            expr.getFirstChild(), expr.getLastChild().getString(),
            inEnv, requiredType, specializedType);
      case Token.HOOK:
        return analyzeHookFwd(expr, inEnv, requiredType, specializedType);
      case Token.CALL:
      case Token.NEW:
        return analyzeCallNewFwd(expr, inEnv, requiredType, specializedType);
      case Token.COMMA:
        return analyzeExprFwd(
            expr.getLastChild(),
            analyzeExprFwd(expr.getFirstChild(), inEnv).env,
            requiredType,
            specializedType);
      case Token.NOT: {
        EnvTypePair pair = analyzeExprFwd(expr.getFirstChild(),
            inEnv, JSType.UNKNOWN, specializedType.negate());
        pair.type = pair.type.negate().toBoolean();
        return pair;
      }
      case Token.GETELEM:
        return analyzeGetElemFwd(expr, inEnv, requiredType, specializedType);
      case Token.VOID: {
        EnvTypePair pair = analyzeExprFwd(expr.getFirstChild(), inEnv);
        pair.type = JSType.UNDEFINED;
        return pair;
      }
      case Token.IN:
        return analyzeInFwd(expr, inEnv, specializedType);
      case Token.DELPROP: {
        // IRFactory checks that the operand is a name, getprop or getelem.
        // No further warnings here.
        EnvTypePair pair = analyzeExprFwd(expr.getFirstChild(), inEnv);
        pair.type = JSType.BOOLEAN;
        return pair;
      }
      case Token.REGEXP:
        return new EnvTypePair(inEnv, commonTypes.getRegexpType());
      case Token.ARRAYLIT:
        return analyzeArrayLitFwd(expr, inEnv);
      case Token.CAST:
        return analyzeCastFwd(expr, inEnv);
      case Token.CASE:
        // For a statement of the form: switch (exp1) { ... case exp2: ... }
        // we analyze the case as if it were (exp1 === exp2).
        // We analyze the body of the case when the test is true and the stm
        // following the body when the test is false.
        return analyzeStrictComparisonFwd(Token.SHEQ,
            expr.getParent().getFirstChild(), expr.getFirstChild(),
            inEnv, specializedType);
      default:
        throw new RuntimeException("Unhandled expression type: " +
              Token.name(expr.getType()));
    }
  }

  private EnvTypePair analyzeNameFwd(
      Node expr, TypeEnv inEnv, JSType requiredType, JSType specializedType) {
    String varName = expr.getString();
    if (varName.equals("undefined")) {
      return new EnvTypePair(inEnv, JSType.UNDEFINED);
    }
    if (currentScope.isLocalVar(varName) ||
        currentScope.isLocalExtern(varName) ||
        currentScope.isFormalParam(varName) ||
        currentScope.isLocalFunDef(varName) ||
        currentScope.isOuterVar(varName) ||
        varName.equals(currentScope.getName())) {
      JSType inferredType = envGetType(inEnv, varName);
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
        if (tightenTypeAndDontWarn(
            varName, declType, inferredType, requiredType)) {
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
      if (!preciseType.isBottom() &&
          currentScope.isUndeclaredFormal(varName) &&
          preciseType.hasNonScalar()) {
        // In the bwd direction, we may infer a loose type and then join w/
        // top and forget it. That's why we also loosen types going fwd.
        preciseType = preciseType.withLoose();
      }
      return EnvTypePair.addBinding(inEnv, varName, preciseType);
    }
    println("Found global variable ", varName);
    // For now, we don't warn for global variables
    return new EnvTypePair(inEnv, JSType.UNKNOWN);
  }

  private EnvTypePair analyzeLogicalOpFwd(
      Node expr, TypeEnv inEnv, JSType requiredType, JSType specializedType) {
    int exprKind = expr.getType();
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();
    if ((specializedType.isTruthy() && exprKind == Token.AND) ||
        (specializedType.isFalsy() && exprKind == Token.OR)) {
      EnvTypePair lhsPair =
          analyzeExprFwd(lhs, inEnv, JSType.UNKNOWN, specializedType);
      EnvTypePair rhsPair =
          analyzeExprFwd(rhs, lhsPair.env, JSType.UNKNOWN, specializedType);
      return rhsPair;
    } else if ((specializedType.isFalsy() && exprKind == Token.AND) ||
        (specializedType.isTruthy() && exprKind == Token.OR)) {
      EnvTypePair shortCircuitPair =
          analyzeExprFwd(lhs, inEnv, JSType.UNKNOWN, specializedType);
      EnvTypePair lhsPair = analyzeExprFwd(
          lhs, inEnv, JSType.UNKNOWN, specializedType.negate());
      EnvTypePair rhsPair =
          analyzeExprFwd(rhs, lhsPair.env, JSType.UNKNOWN, specializedType);
      return EnvTypePair.join(rhsPair, shortCircuitPair);
    } else {
      // Independently of the specializedType, && rhs is only analyzed when
      // lhs is truthy, and || rhs is only analyzed when lhs is falsy.
      JSType stopAfterLhsType = exprKind == Token.AND ?
          JSType.FALSY : JSType.TRUTHY;
      EnvTypePair shortCircuitPair =
          analyzeExprFwd(lhs, inEnv, requiredType, stopAfterLhsType);
      EnvTypePair lhsPair = analyzeExprFwd(
          lhs, inEnv, JSType.UNKNOWN, stopAfterLhsType.negate());
      EnvTypePair rhsPair =
          analyzeExprFwd(rhs, lhsPair.env, requiredType, specializedType);
      return EnvTypePair.join(rhsPair, shortCircuitPair);
    }
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
    if (!objType.equals(JSType.TOP) &&
        !objType.equals(JSType.UNKNOWN) &&
        !objType.hasNonScalar()) {
      warnInvalidOperand(
          obj, Token.INSTANCEOF,
          "an object or a union type that includes an object",
          objPair.type);
    }
    ctorPair = analyzeExprFwd(ctor, objPair.env, commonTypes.topFunction());
    JSType ctorType = ctorPair.type;
    FunctionType ctorFunType = ctorType.getFunType();
    if (!ctorType.isUnknown() &&
        (!ctorType.isSubtypeOf(commonTypes.topFunction()) ||
            !ctorFunType.isQmarkFunction() && !ctorFunType.isConstructor())) {
      warnInvalidOperand(
          ctor, Token.INSTANCEOF, "a constructor function", ctorType);
    }
    if (ctorFunType == null || !ctorFunType.isConstructor() ||
        (!specializedType.isTruthy() && !specializedType.isFalsy())) {
      ctorPair.type = JSType.BOOLEAN;
      return ctorPair;
    }

    // We are in a specialized context *and* we know the constructor type
    JSType instanceType = ctorFunType.getInstanceTypeOfCtor();
    JSType instanceSpecType = specializedType.isTruthy()
        ? objPair.type.specialize(instanceType)
        : objPair.type.removeType(instanceType);
    if (!instanceSpecType.isBottom()) {
      objPair = analyzeExprFwd(obj, inEnv, JSType.UNKNOWN, instanceSpecType);
      ctorPair = analyzeExprFwd(ctor, objPair.env, commonTypes.topFunction());
    }
    ctorPair.type = JSType.BOOLEAN;
    return ctorPair;
  }

  private EnvTypePair analyzeAddFwd(Node expr, TypeEnv inEnv) {
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();
    EnvTypePair lhsPair = analyzeExprFwd(lhs, inEnv, JSType.NUM_OR_STR);
    EnvTypePair rhsPair = analyzeExprFwd(rhs, lhsPair.env, JSType.NUM_OR_STR);
    JSType lhsType = lhsPair.type;
    JSType rhsType = rhsPair.type;
    if (lhsType.isString() || rhsType.isString()) {
      // Don't warn, since '' + expr is used for type coercions
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
      return new EnvTypePair(inEnv, requiredType);
    }
    mayWarnAboutConst(expr);
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();
    if (lhs.getBooleanProp(Node.ANALYZED_DURING_GTI)) {
      lhs.removeProp(Node.ANALYZED_DURING_GTI);
      if (rhs.matchesQualifiedName(ABSTRACT_METHOD_NAME)) {
        return new EnvTypePair(inEnv, requiredType);
      }
      JSType declType = getDeclaredTypeOfQname(lhs, inEnv);
      EnvTypePair rhsPair = analyzeExprFwd(rhs, inEnv, declType);
      if (!rhsPair.type.isSubtypeOf(declType)) {
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
      rhsPair.env = updateLvalueTypeInEnv(
          rhsPair.env, lhs, lvalue.ptr, rhsPair.type);
    }
    return rhsPair;
  }

  private EnvTypePair analyzeAssignAddFwd(
      Node expr, TypeEnv inEnv, JSType requiredType) {
    mayWarnAboutConst(expr);
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();
    JSType lhsReqType =
        specializeWithCorrection(requiredType, JSType.NUM_OR_STR);
    LValueResultFwd lvalue = analyzeLValueFwd(lhs, inEnv, lhsReqType);
    JSType lhsType = lvalue.type;
    if (!lhsType.isSubtypeOf(JSType.NUM_OR_STR)) {
      warnInvalidOperand(lhs, Token.ASSIGN_ADD, JSType.NUM_OR_STR, lhsType);
    }
    // if lhs is a string, rhs can still be a number
    JSType rhsReqType = lhsType.equals(JSType.NUMBER) ?
        JSType.NUMBER : JSType.NUM_OR_STR;
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
      pair.env =
          updateLvalueTypeInEnv(pair.env, lhs, lvalue.ptr, JSType.NUMBER);
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
    } else if (lhs.isName() && lhsPair.type.isUnknown() &&
        rhs.isName() && rhsPair.type.isUnknown()) {
      TypeEnv env = envPutType(rhsPair.env, lhs.getString(), JSType.TOP_SCALAR);
      env = envPutType(rhsPair.env, rhs.getString(), JSType.TOP_SCALAR);
      return new EnvTypePair(env, JSType.BOOLEAN);
    }
    JSType lhsType = lhsPair.type;
    JSType rhsType = rhsPair.type;
    if (!lhsType.isSubtypeOf(JSType.TOP_SCALAR) ||
        !rhsType.isSubtypeOf(JSType.TOP_SCALAR) ||
        !JSType.areCompatibleScalarTypes(lhsType, rhsType)) {
      warnInvalidOperand(expr, expr.getType(), "matching scalar types", lhsType + ", " + rhsType);
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
        analyzeExprFwd(cond, inEnv, JSType.UNKNOWN, JSType.TRUE_TYPE).env;
    TypeEnv falseEnv =
        analyzeExprFwd(cond, inEnv, JSType.UNKNOWN, JSType.FALSE_TYPE).env;
    EnvTypePair thenPair =
        analyzeExprFwd(thenBranch, trueEnv, requiredType, specializedType);
    EnvTypePair elsePair =
        analyzeExprFwd(elseBranch, falseEnv, requiredType, specializedType);
    return EnvTypePair.join(thenPair, elsePair);
  }

  private EnvTypePair analyzeCallNewFwd(
      Node expr, TypeEnv inEnv, JSType requiredType, JSType specializedType) {
    if (isClosureSpecificCall(expr)) {
      return analyzeClosureCallFwd(expr, inEnv, specializedType);
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
    EnvTypePair calleePair =
        analyzeExprFwd(callee, inEnv, commonTypes.topFunction());
    calleePair = mayWarnAboutNullableReferenceAndTighten(
        callee, calleePair.type, inEnv, commonTypes.topFunction());
    JSType calleeType = calleePair.type;
    if (!calleeType.isSubtypeOf(commonTypes.topFunction())) {
      warnings.add(JSError.make(
          expr, TypeCheck.NOT_CALLABLE, calleeType.toString()));
    }
    FunctionType funType = calleeType.getFunType();
    if (funType == null
        || funType.isTopFunction() || funType.isQmarkFunction()) {
      return analyzeCallNodeArgsFwdWhenError(expr, inEnv);
    } else if (funType.isLoose()) {
      return analyzeLooseCallNodeFwd(expr, inEnv, requiredType);
    } else if (expr.isCall() && funType.isConstructor()
        && funType.getReturnType().isUnknown()) {
      warnings.add(JSError.make(
          expr, TypeCheck.CONSTRUCTOR_NOT_CALLABLE, funType.toString()));
      return analyzeCallNodeArgsFwdWhenError(expr, inEnv);
    } else if (expr.isNew() && !funType.isConstructor()) {
      warnings.add(JSError.make(
          expr, NOT_A_CONSTRUCTOR, funType.toString()));
      return analyzeCallNodeArgsFwdWhenError(expr, inEnv);
    }
    int maxArity = funType.getMaxArity();
    int minArity = funType.getMinArity();
    int numArgs = expr.getChildCount() - 1;
    if (numArgs < minArity || numArgs > maxArity) {
      warnings.add(JSError.make(
          expr, TypeCheck.WRONG_ARGUMENT_COUNT,
          getReadableCalleeName(callee),
          Integer.toString(numArgs), Integer.toString(minArity),
          " and at most " + maxArity));
      return analyzeCallNodeArgsFwdWhenError(expr, inEnv);
    }
    FunctionType origFunType = funType; // save for later
    if (funType.isGeneric()) {
      Map<String, JSType> typeMap = calcTypeInstantiationFwd(
          expr, expr.getChildAtIndex(1), funType, inEnv);
      funType = funType.instantiateGenerics(typeMap);
      println("Instantiated function type: " + funType);
    }
    // argTypes collects types of actuals for deferred checks.
    List<JSType> argTypes = new ArrayList<>();
    TypeEnv tmpEnv = analyzeCallNodeArgumentsFwd(
        expr, expr.getChildAtIndex(1), funType, argTypes, inEnv);
    if (callee.isName()) {
      String calleeName = callee.getQualifiedName();
      if (currentScope.isKnownFunction(calleeName)
          && !currentScope.isExternalFunction(calleeName)) {
        // Local function definitions will be type-checked more
        // exactly using their summaries, and don't need deferred checks
        if (currentScope.isLocalFunDef(calleeName)) {
          collectTypesForFreeVarsFwd(callee, tmpEnv);
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
    JSType retType =
        expr.isNew() ? funType.getThisType() : funType.getReturnType();
    return new EnvTypePair(tmpEnv, retType);
  }

  private EnvTypePair analyzeFunctionBindFwd(Node call, TypeEnv inEnv) {
    Preconditions.checkArgument(call.isCall());
    Bind bindComponents = convention.describeFunctionBind(call, true, false);
    Node boundFunNode = bindComponents.target;
    EnvTypePair pair = analyzeExprFwd(boundFunNode, inEnv);
    TypeEnv env = pair.env;
    FunctionType boundFunType = pair.type.getFunTypeIfSingletonObj();
    if (!pair.type.isSubtypeOf(commonTypes.topFunction())) {
      warnings.add(JSError.make(boundFunNode, GOOG_BIND_EXPECTS_FUNCTION));
    }
    // For some function types, we don't know enough to handle .bind specially.
    if (boundFunType == null
        || boundFunType.isTopFunction()
        || boundFunType.isQmarkFunction()
        || boundFunType.isLoose()) {
      return analyzeCallNodeArgsFwdWhenError(call, env);
    }
    if (boundFunType.isConstructor()) {
      warnings.add(JSError.make(call, CANNOT_BIND_CTOR));
      return new EnvTypePair(env, JSType.UNKNOWN);
    }
    // Check if the receiver argument is there
    if (isGoogBind(call) && call.getChildCount() <= 2
        || !isGoogPartial(call) && call.getChildCount() == 1) {
      warnings.add(JSError.make(
          call, TypeCheck.WRONG_ARGUMENT_COUNT,
          getReadableCalleeName(call.getFirstChild()),
          "0", "1", ""));
    }
    // Check that there are not too many of the other arguments
    int maxArity = boundFunType.hasRestFormals()
        ? Integer.MAX_VALUE : boundFunType.getMaxArity();
    int numArgs = bindComponents.getBoundParameterCount();
    if (numArgs > maxArity) {
      warnings.add(JSError.make(
          call, TypeCheck.WRONG_ARGUMENT_COUNT,
          getReadableCalleeName(call.getFirstChild()),
          Integer.toString(numArgs), "0",
          " and at most " + maxArity));
      return analyzeCallNodeArgsFwdWhenError(call, inEnv);
    }

    // If the bound function is polymorphic, we only support the case where we
    // can completely calculate the type instantiation at the .bind call site.
    // We don't support splitting the instantiation between call sites.
    //
    // Also, we don't use the THIS argument when calculating the instantiation
    // of a .bind call.
    // See the following snippet:
    // /**
    //  * @constructor
    //  * @template T
    //  * @param {T} x
    //  */
    // function Foo(x) {}
    // /**
    //  * @template T
    //  * @param {T} x
    //  */
    // Foo.prototype.f = function(x) {};
    // Foo.prototype.f.bind(new Foo(123), 'asdf');
    //
    // Here, the receiver type of f is Foo<T>, but the T is the class's T,
    // not the T of f's template declaration.
    // Otoh, if f had an @this annotation that contained T, T would refer to
    // f's T. There is no way of knowing what's the scope of the type variables
    // in the receiver of the function type, that's why we don't use it here.
    if (boundFunType.isGeneric()) {
      Map<String, JSType> typeMap = calcTypeInstantiationFwd(
          call, bindComponents.parameters, boundFunType, env);
      boundFunType = boundFunType.instantiateGenerics(typeMap);
    }
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    Node receiver = bindComponents.thisValue;
    if (receiver != null) {// receiver is null for goog.partial
      JSType reqThisType = boundFunType.getThisType();
      if (reqThisType == null || boundFunType.isConstructor()) {
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
    for (int j = numArgs; j < boundFunType.getMaxArity(); j++) {
      JSType formalType = boundFunType.getFormalType(j);
      if (boundFunType.isRequiredArg(j)) {
        builder.addReqFormal(formalType);
      } else if (boundFunType.isOptionalArg(j)) {
        builder.addOptFormal(formalType);
      } else {
        builder.addRestFormals(formalType);
        break; // To avoid iterating to Integer.MAX_VALUE
      }
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
      if (formalType.isBottom()) {
        warnings.add(JSError.make(call, CALL_FUNCTION_WITH_BOTTOM_FORMAL,
                Integer.toString(i)));
        formalType = JSType.UNKNOWN;
      }
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
    Node left = callNode.getFirstChild();
    Node firstParam = left.getNext();
    if (firstParam == null) {
      return new EnvTypePair(env, JSType.UNKNOWN);
    }
    Node assertedNode = assertionFunctionSpec.getAssertedParam(firstParam);
    if (assertedNode == null) {
      return new EnvTypePair(env, JSType.UNKNOWN);
    }
    JSType assertedType =
        (JSType) assertionFunctionSpec.getAssertedType(callNode, currentScope);
    if (assertedType.isUnknown()) {
      warnings.add(
          JSError.make(callNode, NewTypeInference.UNKNOWN_ASSERTION_TYPE));
    }
    EnvTypePair pair =
        analyzeExprFwd(assertedNode, env, JSType.UNKNOWN, assertedType);
    if (pair.type.isBottom()) {
      JSType t = analyzeExprFwd(assertedNode, env)
          .type.substituteGenericsWithUnknown();
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
    pair = mayWarnAboutNullableReferenceAndTighten(
        receiver, pair.type, pair.env, JSType.TOP_OBJECT);
    JSType recvType = pair.type.autobox(commonTypes);
    // TODO(dimvar): we don't know the prop name here so we're passing the
    // empty string. Consider improving the error msg.
    if (!mayWarnAboutNonObject(receiver, "", recvType, specializedType) &&
        !mayWarnAboutStructPropAccess(receiver, recvType)) {
      if (isArrayType(recvType)) {
        pair = analyzeExprFwd(index, pair.env, JSType.NUMBER);
        if (!commonTypes.isNumberScalarOrObj(pair.type)) {
          warnings.add(JSError.make(
              index, NewTypeInference.NON_NUMERIC_ARRAY_INDEX,
              pair.type.toString()));
        }
        pair.type = getArrayElementType(recvType);
        Preconditions.checkState(pair.type != null,
            "Array type %s has no element type at node: %s", recvType, expr);
        return pair;
      } else if (index.isString()) {
        return analyzePropAccessFwd(receiver, index.getString(), inEnv,
            requiredType, specializedType);
      }
    }
    pair = analyzeExprFwd(index, pair.env);
    pair.type = requiredType;
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
      warnings.add(JSError.make(rhs, TypeCheck.IN_USED_WITH_STRUCT));
      pair.type = JSType.BOOLEAN;
      return pair;
    }

    JSType resultType = JSType.BOOLEAN;
    if (lhs.isString()) {
      QualifiedName pname = new QualifiedName(lhs.getString());
      if (specializedType.isTruthy()) {
        pair = analyzeExprFwd(rhs, inEnv, reqObjType,
            reqObjType.withPropertyRequired(pname.getLeftmostName()));
        resultType = JSType.TRUE_TYPE;
      } else if (specializedType.isFalsy()) {
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
    if (!toType.isSubtypeOf(fromType) && !fromType.isSubtypeOf(toType)) {
      warnings.add(JSError.make(expr, TypeValidator.INVALID_CAST,
              fromType.toString(), toType.toString()));
    }
    pair.type = toType;
    return pair;
  }

  private EnvTypePair analyzeCallNodeArgsFwdWhenError(
      Node callNode, TypeEnv inEnv) {
    TypeEnv env = inEnv;
    for (Node arg = callNode.getFirstChild().getNext(); arg != null;
        arg = arg.getNext()) {
      env = analyzeExprFwd(arg, env).env;
    }
    return new EnvTypePair(env, JSType.UNKNOWN);
  }

  private EnvTypePair analyzeStrictComparisonFwd(int comparisonOp,
      Node lhs, Node rhs, TypeEnv inEnv, JSType specializedType) {
    if (specializedType.isTruthy() || specializedType.isFalsy()) {
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

    if ((comparisonOp == Token.SHEQ && specializedType.isTruthy()) ||
        (comparisonOp == Token.SHNE && specializedType.isFalsy())) {
      JSType meetType = JSType.meet(lhsPair.type, rhsPair.type);
      lhsPair = analyzeExprFwd(lhs, inEnv, JSType.UNKNOWN, meetType);
      rhsPair = analyzeExprFwd(rhs, lhsPair.env, JSType.UNKNOWN, meetType);
    } else if ((comparisonOp == Token.SHEQ && specializedType.isFalsy()) ||
        (comparisonOp == Token.SHNE && specializedType.isTruthy())) {
      JSType lhsType = lhsPair.type;
      JSType rhsType = rhsPair.type;
      if (lhsType.equals(JSType.NULL) ||
          lhsType.equals(JSType.UNDEFINED)) {
        rhsType = rhsType.removeType(lhsType);
      } else if (rhsType.equals(JSType.NULL) ||
          rhsType.equals(JSType.UNDEFINED)) {
        lhsType = lhsType.removeType(rhsType);
      }
      lhsPair = analyzeExprFwd(lhs, inEnv, JSType.UNKNOWN, lhsType);
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
    } else if ((specializedType.isTruthy() &&
         (comparisonOp == Token.SHEQ || comparisonOp == Token.EQ)) ||
        (specializedType.isFalsy() &&
         (comparisonOp == Token.SHNE || comparisonOp == Token.NE))) {
      pair = analyzeExprFwd(typeofRand, inEnv, JSType.UNKNOWN, comparedType);
    } else {
      pair = analyzeExprFwd(typeofRand, inEnv);
      pair = analyzeExprFwd(typeofRand, inEnv, JSType.UNKNOWN,
          pair.type.removeType(comparedType));
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
        warnings.add(JSError.make(
            typeString, TypeValidator.UNKNOWN_TYPEOF_VALUE, typeName));
    }
  }

  private Map<String, JSType> calcTypeInstantiationFwd(
      Node callNode, Node firstArg, FunctionType funType, TypeEnv typeEnv) {
    return calcTypeInstantiation(callNode, firstArg, funType, typeEnv, true);
  }

  private Map<String, JSType> calcTypeInstantiationBwd(
      Node callNode, FunctionType funType, TypeEnv typeEnv) {
    return calcTypeInstantiation(
        callNode, callNode.getChildAtIndex(1), funType, typeEnv, false);
  }

  /*
   * We don't use the requiredType of the context to unify with the return
   * type. There are several difficulties:
   * 1) A polymorphic function is allowed to return ANY subtype of the
   *    requiredType, so we would need to use a heuristic to determine the type
   *    to unify with.
   * 2) It's hard to give good error messages in cases like: id('str') - 5
   *    We want an invalid-operand-type, not a not-unique-instantiation.
   *
   * We don't take the arg evaluation order into account during instantiation.
   */
  private ImmutableMap<String, JSType> calcTypeInstantiation(Node callNode,
      Node firstArg, FunctionType funType, TypeEnv typeEnv, boolean isFwd) {
    List<String> typeParameters = funType.getTypeParameters();
    Multimap<String, JSType> typeMultimap = HashMultimap.create();
    Node arg = firstArg;
    int i = 0;
    while (arg != null) {
      EnvTypePair pair =
          isFwd ? analyzeExprFwd(arg, typeEnv) : analyzeExprBwd(arg, typeEnv);
      JSType unifTarget = funType.getFormalType(i);
      JSType unifSource = pair.type;
      if (!unifTarget.unifyWith(unifSource, typeParameters, typeMultimap)) {
        // Unification may fail b/c of types irrelevant to generics, eg,
        // number vs string.
        // In this case, don't warn here; we'll show invalid-arg-type later.
        JSType targetAfterInstantiation =
            unifTarget.substituteGenericsWithUnknown();
        if (!unifTarget.equals(targetAfterInstantiation)
            && unifSource.isSubtypeOf(targetAfterInstantiation)) {
          warnings.add(JSError.make(arg, FAILED_TO_UNIFY,
                  unifTarget.toString(), unifSource.toString()));
        }
      }
      arg = arg.getNext();
      typeEnv = pair.env;
      i++;
    }
    ImmutableMap.Builder<String, JSType> builder = ImmutableMap.builder();
    for (String typeParam : typeParameters) {
      Collection<JSType> types = typeMultimap.get(typeParam);
      if (types.size() > 1) {
        if (isFwd) {
          warnings.add(JSError.make(callNode, NOT_UNIQUE_INSTANTIATION,
                typeParam, types.toString()));
        }
        builder.put(typeParam, JSType.UNKNOWN);
      } else if (types.size() == 1) {
        builder.put(typeParam, Iterables.getOnlyElement(types));
      } else {
        // Put ? for any uninstantiated type variables
        builder.put(typeParam, JSType.UNKNOWN);
      }
    }
    return builder.build();
  }

  private EnvTypePair analyzeNonStrictComparisonFwd(
      Node expr, TypeEnv inEnv, JSType specializedType) {
    int tokenType = expr.getType();
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();

    if (specializedType.isTruthy() || specializedType.isFalsy()) {
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
    JSType lhsType = lhsPair.type;
    JSType rhsType = rhsPair.type;

    if (tokenType == Token.EQ && specializedType.isTruthy() ||
        tokenType == Token.NE && specializedType.isFalsy()) {
      if (lhsType.isNullOrUndef()) {
        rhsPair = analyzeExprFwd(
            rhs, lhsPair.env, JSType.UNKNOWN, JSType.NULL_OR_UNDEF);
      } else if (rhsType.isNullOrUndef()) {
        lhsPair = analyzeExprFwd(
            lhs, inEnv, JSType.UNKNOWN, JSType.NULL_OR_UNDEF);
        rhsPair = analyzeExprFwd(rhs, lhsPair.env);
      } else if (!JSType.NULL_OR_UNDEF.isSubtypeOf(lhsType)) {
        rhsType = rhsType.removeType(JSType.NULL_OR_UNDEF);
        rhsPair = analyzeExprFwd(rhs, lhsPair.env, JSType.UNKNOWN, rhsType);
      } else if (!JSType.NULL_OR_UNDEF.isSubtypeOf(rhsType)) {
        lhsType = lhsType.removeType(JSType.NULL_OR_UNDEF);
        lhsPair = analyzeExprFwd(lhs, inEnv, JSType.UNKNOWN, lhsType);
        rhsPair = analyzeExprFwd(rhs, lhsPair.env);
      }
    } else if (tokenType == Token.EQ && specializedType.isFalsy() ||
        tokenType == Token.NE && specializedType.isTruthy()) {
      if (lhsType.isNullOrUndef()) {
        rhsType = rhsType.removeType(JSType.NULL_OR_UNDEF);
        rhsPair = analyzeExprFwd(rhs, lhsPair.env, JSType.UNKNOWN, rhsType);
      } else if (rhsType.isNullOrUndef()) {
        lhsType = lhsType.removeType(JSType.NULL_OR_UNDEF);
        lhsPair = analyzeExprFwd(lhs, inEnv, JSType.UNKNOWN, lhsType);
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
        warnings.add(
            JSError.make(prop, TypeCheck.ILLEGAL_OBJLIT_KEY, "struct"));
      } else if (isDict && !prop.isQuotedString()) {
        warnings.add(JSError.make(prop, TypeCheck.ILLEGAL_OBJLIT_KEY, "dict"));
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
        result = result.withProperty(
            new QualifiedName(specialPropName), propType);
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
        EnvTypePair pair =
            analyzeExprFwd(prop.getFirstChild(), env, reqPtype, specPtype);
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

  private EnvTypePair analyzeGoogTypePredicate(
      Node call, String typeHint, TypeEnv inEnv, JSType specializedType) {
    int numArgs = call.getChildCount() - 1;
    if (numArgs != 1) {
      warnings.add(JSError.make(call, TypeCheck.WRONG_ARGUMENT_COUNT,
              call.getFirstChild().getQualifiedName(),
              Integer.toString(numArgs), "1", "1"));
      return analyzeCallNodeArgsFwdWhenError(call, inEnv);
    }
    EnvTypePair pair = analyzeExprFwd(call.getLastChild(), inEnv);
    if (specializedType.isTruthy() || specializedType.isFalsy()) {
      pair = analyzeExprFwd(call.getLastChild(), inEnv, JSType.UNKNOWN,
          googPredicateTransformType(typeHint, specializedType, pair.type));
    }
    pair.type = JSType.BOOLEAN;
    return pair;
  }

  private EnvTypePair analyzeGoogTypeof(
      Node typeof, Node typeString, TypeEnv inEnv, JSType specializedType) {
    return analyzeGoogTypePredicate(typeof,
        typeString.isString() ? typeString.getString() : "",
        inEnv, specializedType);
  }

  private EnvTypePair analyzeClosureCallFwd(
      Node call, TypeEnv inEnv, JSType specializedType) {
    return analyzeGoogTypePredicate(call,
        call.getFirstChild().getLastChild().getString(),
        inEnv, specializedType);
  }

  // typeHint can come from goog.isXXX and from goog.typeOf.
  private JSType googPredicateTransformType(
      String typeHint, JSType booleanContext, JSType beforeType) {
    switch (typeHint) {
      case "array":
      case "isArray":
        JSType arrayType = commonTypes.getArrayInstance();
        if (arrayType.isUnknown()) {
          return JSType.UNKNOWN;
        }
        return booleanContext.isTruthy() ?
            arrayType : beforeType.removeType(arrayType);
      case "boolean":
      case "isBoolean":
        return booleanContext.isTruthy() ?
            JSType.BOOLEAN : beforeType.removeType(JSType.BOOLEAN);
      case "function":
      case "isFunction":
        return booleanContext.isTruthy()
            ? commonTypes.looseTopFunction()
            : beforeType.removeType(commonTypes.topFunction());
      case "null":
      case "isNull":
        return booleanContext.isTruthy() ?
            JSType.NULL : beforeType.removeType(JSType.NULL);
      case "number":
      case "isNumber":
        return booleanContext.isTruthy() ?
            JSType.NUMBER : beforeType.removeType(JSType.NUMBER);
      case "string":
      case "isString":
        return booleanContext.isTruthy() ?
            JSType.STRING : beforeType.removeType(JSType.STRING);
      case "isDef":
        return booleanContext.isTruthy() ?
            beforeType.removeType(JSType.UNDEFINED) : JSType.UNDEFINED;
      case "isDefAndNotNull":
        return booleanContext.isTruthy() ?
            beforeType.removeType(JSType.NULL_OR_UNDEF) : JSType.NULL_OR_UNDEF;
      case "isObject":
        // typeof(null) === 'object', but goog.isObject(null) is false
        return booleanContext.isTruthy() ?
            JSType.TOP_OBJECT : beforeType.removeType(JSType.TOP_OBJECT);
      case "object":
        // goog.typeOf(expr) === 'object' is true only for non-function objects.
        // Just do sth simple here.
        return JSType.UNKNOWN;
      case "undefined":
        return booleanContext.isTruthy() ?
            JSType.UNDEFINED : beforeType.removeType(JSType.UNDEFINED);
      default:
        // For when we can't figure out the type name used with goog.typeOf.
        return JSType.UNKNOWN;
    }
  }

  private boolean tightenTypeAndDontWarn(
      String varName, JSType declared, JSType inferred, JSType required) {
    boolean fuzzyDeclaration = declared == null || declared.isUnknown() ||
        (declared.isTop() && !inferred.isTop());
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
      Node receiver, String pname, JSType recvType, JSType specializedType) {
    // The warning depends on whether we are testing for the existence of a
    // property.
    boolean isNotAnObject =
        JSType.BOTTOM.equals(JSType.meet(recvType, JSType.TOP_OBJECT));
    boolean mayNotBeAnObject = !recvType.isSubtypeOf(JSType.TOP_OBJECT);
    if (isNotAnObject ||
        (!specializedType.isTruthy() && !specializedType.isFalsy() &&
            mayNotBeAnObject)) {
      warnings.add(JSError.make(receiver, PROPERTY_ACCESS_ON_NONOBJECT,
          pname, recvType.toString()));
      return true;
    }
    return false;
  }

  private boolean mayWarnAboutStructPropAccess(Node obj, JSType type) {
    if (type.isStruct()) {
      warnings.add(JSError.make(obj,
              TypeValidator.ILLEGAL_PROPERTY_ACCESS, "'[]'", "struct"));
      return true;
    }
    return false;
  }

  private boolean mayWarnAboutDictPropAccess(Node obj, JSType type) {
    if (type.isDict()) {
      warnings.add(JSError.make(obj,
              TypeValidator.ILLEGAL_PROPERTY_ACCESS, "'.'", "dict"));
      return true;
    }
    return false;
  }

  private boolean mayWarnAboutPropCreation(
      QualifiedName pname, Node getProp, JSType recvType) {
    Preconditions.checkArgument(getProp.isGetProp());
    // Inferred formals used as objects have a loose type.
    // For these, we don't warn about property creation.
    // Consider: function f(obj) { obj.prop = 123; }
    // We want f to be able to take objects without prop, so we don't want to
    // require that obj be a struct that already has prop.
    if (recvType.isStruct() && !recvType.isLooseStruct() &&
        !recvType.hasProp(pname)) {
      warnings.add(JSError.make(getProp, TypeCheck.ILLEGAL_PROPERTY_CREATION));
      return true;
    }
    return false;
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
      warnings.add(JSError.make(propAccess, CONST_REASSIGNED));
      return true;
    }
    return false;
  }

  //////////////////////////////////////////////////////////////////////////////

  private EnvTypePair analyzePropAccessFwd(Node receiver, String pname,
      TypeEnv inEnv, JSType requiredType, JSType specializedType) {
    QualifiedName propQname = new QualifiedName(pname);
    Node propAccessNode = receiver.getParent();
    EnvTypePair pair;
    JSType reqObjType = pickReqObjType(propAccessNode).withLoose();
    JSType recvReqType, recvSpecType;

    // First, analyze the receiver object.
    if (specializedType.isTruthy() || specializedType.isFalsy()) {
      recvReqType = JSType.UNKNOWN;
      recvSpecType = reqObjType.withProperty(propQname, requiredType);
    } else {
      recvReqType = reqObjType.withProperty(propQname, requiredType);
      recvSpecType = reqObjType.withProperty(propQname, specializedType);
    }
    pair = analyzeExprFwd(receiver, inEnv, recvReqType, recvSpecType);
    pair = mayWarnAboutNullableReferenceAndTighten(
        receiver, pair.type, pair.env, JSType.TOP_OBJECT);
    JSType recvType = pair.type.autobox(commonTypes);
    if (recvType.isUnknown() ||
        mayWarnAboutNonObject(receiver, pname, recvType, specializedType)) {
      return new EnvTypePair(pair.env, requiredType);
    }
    if (convention.isSuperClassReference(pname)) {
      FunctionType ft = recvType.getFunTypeIfSingletonObj();
      if (ft != null && ft.isConstructor()) {
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
    if (!propAccessNode.getParent().isExprResult() &&
        !specializedType.isTruthy() && !specializedType.isFalsy()) {
      if (!recvType.mayHaveProp(propQname)) {
        // TODO(dimvar): maybe don't warn if the getprop is inside a typeof,
        // see testMissingProperty8 (who relies on that for prop checking?)
        warnings.add(JSError.make(propAccessNode, TypeCheck.INEXISTENT_PROPERTY,
                pname, recvType.toString()));
      } else if (!recvType.hasProp(propQname)) {
        warnings.add(JSError.make(
            propAccessNode, POSSIBLY_INEXISTENT_PROPERTY,
            pname, recvType.toString()));
      } else if (recvType.hasProp(propQname) &&
          !resultType.isSubtypeOf(requiredType) &&
          tightenTypeAndDontWarn(
              receiver.isName() ? receiver.getString() : null,
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
    }
    // We've already warned about missing props, and never want to return null.
    if (resultType == null) {
      resultType = JSType.UNKNOWN;
    }
    // Any potential type mismatch will be caught by the context
    return new EnvTypePair(pair.env, resultType);
  }

  private static TypeEnv updateLvalueTypeInEnv(
      TypeEnv env, Node lvalue, QualifiedName qname, JSType type) {
    Preconditions.checkNotNull(type);
    if (lvalue.isName()) {
      return envPutType(env, lvalue.getString(), type);
    } else if (lvalue.isVar()) { // Can happen iff its parent is a for/in.
      Preconditions.checkState(NodeUtil.isForIn(lvalue.getParent()));
      return envPutType(env, lvalue.getFirstChild().getString(), type);
    }
    Preconditions.checkState(lvalue.isGetProp() || lvalue.isGetElem());
    if (qname != null) {
      String objName = qname.getLeftmostName();
      QualifiedName props = qname.getAllButLeftmost();
      JSType objType = envGetType(env, objName);
      env = envPutType(env, objName, objType.withProperty(props, type));
    }
    return env;
  }

  private void collectTypesForFreeVarsFwd(Node callee, TypeEnv env) {
    Scope calleeScope = currentScope.getScope(callee.getQualifiedName());
    for (String freeVar : calleeScope.getOuterVars()) {
      if (calleeScope.getDeclaredTypeOf(freeVar) == null) {
        FunctionType summary = summaries.get(calleeScope).getFunType();
        JSType outerType = envGetType(env, freeVar);
        JSType innerType = summary.getOuterVarPrecondition(freeVar);
        if (outerType != null && JSType.meet(outerType, innerType).isBottom()) {
          warnings.add(JSError.make(callee, CROSS_SCOPE_GOTCHA,
                  freeVar, outerType.toString(), innerType.toString()));
        }
      }
    }
  }

  private TypeEnv collectTypesForFreeVarsBwd(Node callee, TypeEnv env) {
    Scope calleeScope = currentScope.getScope(callee.getQualifiedName());
    for (String freeVar : calleeScope.getOuterVars()) {
      if (!currentScope.isDefinedLocally(freeVar) &&
          !currentScope.isOuterVar(freeVar)) {
        continue;
      }
      // Practice will inform what the right decision here is.
      //   * We could ignore the call, giving poor inference around closures.
      //   * We could use info from the call, giving possibly strange errors.
      //   * Or we could just throw up our hands and give a very general type.
      // For now, we do option 3, but this is open to change.
      JSType declType = currentScope.getDeclaredTypeOf(freeVar);
      env = envPutType(env, freeVar,
          declType != null ? declType : JSType.UNKNOWN);
    }
    return env;
  }

  private EnvTypePair analyzeLooseCallNodeFwd(
      Node callNode, TypeEnv inEnv, JSType retType) {
    Preconditions.checkArgument(callNode.isCall() || callNode.isNew());
    Node callee = callNode.getFirstChild();
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    TypeEnv tmpEnv = inEnv;
    for (Node arg = callee.getNext(); arg != null; arg = arg.getNext()) {
      EnvTypePair pair = analyzeExprFwd(arg, tmpEnv);
      tmpEnv = pair.env;
      builder.addReqFormal(pair.type);
    }
    JSType looseRetType = retType.isUnknown() ? JSType.BOTTOM : retType;
    JSType looseFunctionType = commonTypes.fromFunctionType(
        builder.addRetType(looseRetType).addLoose().buildFunction());
    // Unsound if the arguments and callee have interacting side effects
    EnvTypePair calleePair = analyzeExprFwd(
        callee, tmpEnv, commonTypes.topFunction(), looseFunctionType);
    return new EnvTypePair(calleePair.env, retType);
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
      tmpEnv = analyzeExprBwd(arg, tmpEnv).env;
      // Wait until FWD to get more precise argument types.
      builder.addReqFormal(JSType.BOTTOM);
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
        JSType thisType = currentScope.getDeclaredTypeOf("this");
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
        return analyzeAddBwd(expr, outEnv);
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
    if (inferredType == null) { // Needed for the free vars in the tests
      return new EnvTypePair(outEnv, JSType.UNKNOWN);
    }
    JSType preciseType = inferredType.specialize(requiredType);
    if (currentScope.isUndeclaredFormal(varName)
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

  private EnvTypePair analyzeAddBwd(Node expr, TypeEnv outEnv) {
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();
    EnvTypePair rhsPair = analyzeExprBwd(rhs, outEnv, JSType.NUM_OR_STR);
    EnvTypePair lhsPair = analyzeExprBwd(lhs, rhsPair.env, JSType.NUM_OR_STR);
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
      return analyzeExprBwd(rhs, outEnv, getDeclaredTypeOfQname(lhs, outEnv));
    }
    // Here we analyze the LHS twice:
    // Once to find out what should be removed for the slicedEnv,
    // and again to take into account the side effects of the LHS itself.
    LValueResultBwd lvalue = analyzeLValueBwd(lhs, outEnv, requiredType, true);
    TypeEnv slicedEnv = lvalue.env;
    JSType rhsReqType = specializeWithCorrection(lvalue.type, requiredType);
    EnvTypePair pair = analyzeExprBwd(rhs, slicedEnv, rhsReqType);
    pair.env = analyzeLValueBwd(lhs, pair.env, requiredType, true).env;
    return pair;
  }

  private EnvTypePair analyzeAssignAddBwd(
      Node expr, TypeEnv outEnv, JSType requiredType) {
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getLastChild();
    JSType lhsReqType = specializeWithCorrection(
        requiredType, JSType.NUM_OR_STR);
    LValueResultBwd lvalue = analyzeLValueBwd(lhs, outEnv, lhsReqType, false);
    // if lhs is a string, rhs can still be a number
    JSType rhsReqType = lvalue.type.equals(JSType.NUMBER) ?
        JSType.NUMBER : JSType.NUM_OR_STR;
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
    JSType calleeTypeGeneral =
        analyzeExprBwd(callee, outEnv, commonTypes.topFunction()).type;
    FunctionType funType = calleeTypeGeneral.getFunType();
    if (funType == null) {
      return analyzeCallNodeArgumentsBwd(expr, outEnv);
    } else if (funType.isLoose()) {
      return analyzeLooseCallNodeBwd(expr, outEnv, requiredType);
    } else if (expr.isCall() && funType.isConstructor() ||
        expr.isNew() && !funType.isConstructor()) {
      return analyzeCallNodeArgumentsBwd(expr, outEnv);
    } else if (funType.isTopFunction()) {
      return analyzeCallNodeArgumentsBwd(expr, outEnv);
    }
    if (callee.isName() && !funType.isGeneric() && expr.isCall()) {
      createDeferredCheckBwd(expr, requiredType);
    }
    int numArgs = expr.getChildCount() - 1;
    if (numArgs < funType.getMinArity() || numArgs > funType.getMaxArity()) {
      return analyzeCallNodeArgumentsBwd(expr, outEnv);
    }
    if (funType.isGeneric()) {
      Map<String, JSType> typeMap =
          calcTypeInstantiationBwd(expr, funType, outEnv);
      funType = funType.instantiateGenerics(typeMap);
    }
    TypeEnv tmpEnv = outEnv;
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
    if (callee.isName() && currentScope.isLocalFunDef(callee.getString())) {
      tmpEnv = collectTypesForFreeVarsBwd(callee, tmpEnv);
    }
    JSType retType =
        expr.isNew() ? funType.getThisType() : funType.getReturnType();
    return new EnvTypePair(tmpEnv, retType);
  }

  private JSType getArrayElementType(JSType arrayType) {
    Preconditions.checkState(
        isArrayType(arrayType), "Expected array but found %s", arrayType);
    return arrayType.getProp(NUMERIC_INDEX);
  }

  private EnvTypePair analyzeGetElemBwd(
      Node expr, TypeEnv outEnv, JSType requiredType) {
    Node receiver = expr.getFirstChild();
    Node index = expr.getLastChild();
    JSType reqObjType = pickReqObjType(expr);
    EnvTypePair pair = analyzeExprBwd(receiver, outEnv, reqObjType);
    JSType recvType = pair.type;
    if (isArrayType(recvType)) {
      pair = analyzeExprBwd(index, pair.env, JSType.NUMBER);
      pair.type = getArrayElementType(recvType);
      return pair;
    }
    if (index.isString()) {
      return analyzePropAccessBwd(
          receiver, index.getString(), outEnv, requiredType);
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
    String calleeName = expr.getFirstChild().getQualifiedName();
    // Local function definitions will be type-checked more
    // exactly using their summaries, and don't need deferred checks
    if (currentScope.isKnownFunction(calleeName)
        && !currentScope.isLocalFunDef(calleeName)
        && !currentScope.isExternalFunction(calleeName)) {
      Scope s = currentScope.getScope(calleeName);
      JSType expectedRetType;
      if (s.getDeclaredType().getReturnType() == null) {
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
    JSType reqObjType = pickReqObjType(propAccessNode).withLoose();
    // In the BWD direction we don't have specialized types, so we use
    // isPropertyTest to avoid spurious addition of properties to loose objects.
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

  private boolean isClosureSpecificCall(Node expr) {
    if (!isClosurePassOn || !expr.isCall()) {
      return false;
    }
    return expr.getFirstChild().isQualifiedName()
        && convention.isPropertyTestFunction(expr);
  }

  private boolean isGoogBind(Node n) {
    return n.isCall()
        && n.getFirstChild().isQualifiedName()
        && n.getFirstChild().matchesQualifiedName("goog.bind");
  }

  private boolean isGoogPartial(Node n) {
    return n.isCall()
        && n.getFirstChild().isQualifiedName()
        && n.getFirstChild().matchesQualifiedName("goog.partial");
  }

  private boolean isFunctionBind(Node expr, TypeEnv env, boolean isFwd) {
    if (!expr.isGetProp()) {
      return false;
    }
    Node recv = expr.getFirstChild();
    if (!recv.isFunction() && !recv.isQualifiedName()) {
      return false;
    }
    if (isGoogBind(expr.getParent()) || isGoogPartial(expr.getParent())) {
      return true;
    }
    if (!expr.getLastChild().getString().equals("bind")) {
      return false;
    }
    JSType recvType = isFwd
        ? analyzeExprFwd(recv, env).type : analyzeExprBwd(recv, env).type;
    return !recvType.isUnknown()
        && recvType.isSubtypeOf(commonTypes.topFunction());
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

  private JSType getDeclaredTypeOfQname(Node qnameNode, TypeEnv env) {
    switch (qnameNode.getType()) {
      case Token.NAME: {
        JSType result = envGetType(env, qnameNode.getString());
        Preconditions.checkNotNull(result, "Null declared type@%s", qnameNode);
        return result;
      }
      case Token.GETPROP: {
        Preconditions.checkState(qnameNode.isQualifiedName());
        JSType recvType =
            getDeclaredTypeOfQname(qnameNode.getFirstChild(), env);
        JSType result = recvType.getProp(
            new QualifiedName(qnameNode.getLastChild().getString()));
        Preconditions.checkNotNull(result, "Null declared type@%s", qnameNode);
        return result;
      }
      default:
        throw new RuntimeException("getDeclaredTypeOfQname: unexpected node "
            + Token.name(qnameNode.getType()));
    }
  }

  private LValueResultFwd analyzeLValueFwd(
      Node expr, TypeEnv inEnv, JSType type) {
    return analyzeLValueFwd(expr, inEnv, type, false);
  }

  private LValueResultFwd analyzeLValueFwd(
      Node expr, TypeEnv inEnv, JSType type, boolean insideQualifiedName) {
    switch (expr.getType()) {
      case Token.THIS: {
        if (currentScope.hasThis()) {
          return new LValueResultFwd(inEnv, envGetType(inEnv, "this"),
              currentScope.getDeclaredTypeOf("this"),
              new QualifiedName("this"));
        } else {
          warnings.add(JSError.make(expr, CheckGlobalThis.GLOBAL_THIS));
          return new LValueResultFwd(inEnv, JSType.UNKNOWN, null, null);
        }
      }
      case Token.NAME: {
        String varName = expr.getString();
        JSType varType = analyzeExprFwd(expr, inEnv).type;
        return new LValueResultFwd(inEnv, varType,
            currentScope.getDeclaredTypeOf(varName),
            varType.hasNonScalar() ? new QualifiedName(varName) : null);
      }
      case Token.GETPROP: {
        Node obj = expr.getFirstChild();
        QualifiedName pname =
            new QualifiedName(expr.getLastChild().getString());
        return analyzePropLValFwd(obj, pname, inEnv, type, insideQualifiedName);
      }
      case Token.GETELEM: {
        Node obj = expr.getFirstChild();
        Node prop = expr.getLastChild();
        // (1) A getelem where the prop is a string literal is like a getprop
        if (prop.isString()) {
          QualifiedName pname = new QualifiedName(prop.getString());
          return analyzePropLValFwd(
              obj, pname, inEnv, type, insideQualifiedName);
        }
        // (2) A getelem where the receiver is an array
        LValueResultFwd lvalue =
            analyzeLValueFwd(obj, inEnv, JSType.UNKNOWN, true);
        if (isArrayType(lvalue.type)) {
          return analyzeArrayElmLvalFwd(prop, lvalue);
        }
        // (3) All other getelems
        EnvTypePair pair = analyzeExprFwd(expr, inEnv, type);
        return new LValueResultFwd(pair.env, pair.type, null, null);
      }
      case Token.VAR: { // Can happen iff its parent is a for/in.
        Preconditions.checkState(NodeUtil.isForIn(expr.getParent()));
        Node vdecl = expr.getFirstChild();
        String name = vdecl.getString();
        // For/in can never have rhs of its VAR
        Preconditions.checkState(!vdecl.hasChildren());
        return new LValueResultFwd(
            inEnv, JSType.STRING, null, new QualifiedName(name));
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
  }

  private LValueResultFwd analyzeArrayElmLvalFwd(
      Node prop, LValueResultFwd lvalue) {
    EnvTypePair pair = analyzeExprFwd(prop, lvalue.env, JSType.NUMBER);
    if (!pair.type.equals(JSType.NUMBER)) {
      // Some unknown computed property; don't treat as element access.
      return new LValueResultFwd(pair.env, JSType.UNKNOWN, null, null);
    }
    JSType inferred = getArrayElementType(lvalue.type);
    JSType declared = null;
    if (lvalue.declType != null) {
      JSType receiverAdjustedDeclType =
          lvalue.declType.removeType(JSType.NULL_OR_UNDEF);
      if (isArrayType(receiverAdjustedDeclType)) {
        declared = getArrayElementType(receiverAdjustedDeclType);
      }
    }
    return new LValueResultFwd(pair.env, inferred, declared, null);
  }

  private EnvTypePair mayWarnAboutNullableReferenceAndTighten(
      Node obj, JSType recvType, TypeEnv inEnv, JSType requiredType) {
    if (!recvType.isUnknown()
        && (JSType.NULL.isSubtypeOf(recvType)
            || JSType.UNDEFINED.isSubtypeOf(recvType))) {
      JSType minusNull = recvType.removeType(JSType.NULL_OR_UNDEF);
      if (!minusNull.isBottom() && minusNull.isSubtypeOf(requiredType)) {
        warnings.add(JSError.make(
            obj, NULLABLE_DEREFERENCE, recvType.toString()));
        TypeEnv outEnv = inEnv;
        if (obj.isQualifiedName()) {
          QualifiedName qname = QualifiedName.fromNode(obj);
          outEnv = updateLvalueTypeInEnv(inEnv, obj, qname, minusNull);
        }
        return new EnvTypePair(outEnv, minusNull);
      }
    }
    return new EnvTypePair(inEnv, recvType);
  }

  private LValueResultFwd analyzePropLValFwd(Node obj, QualifiedName pname,
      TypeEnv inEnv, JSType type, boolean insideQualifiedName) {
    Preconditions.checkArgument(pname.isIdentifier());
    String pnameAsString = pname.getLeftmostName();
    JSType reqObjType =
        pickReqObjType(obj.getParent()).withLoose().withProperty(pname, type);
    LValueResultFwd lvalue = analyzeLValueFwd(obj, inEnv, reqObjType, true);
    EnvTypePair pair = mayWarnAboutNullableReferenceAndTighten(
        obj, lvalue.type, lvalue.env, JSType.TOP_OBJECT);
    TypeEnv lvalueEnv = pair.env;
    JSType lvalueType = pair.type.autobox(commonTypes);
    if (!lvalueType.isSubtypeOf(JSType.TOP_OBJECT)) {
      warnings.add(JSError.make(obj, PROPERTY_ACCESS_ON_NONOBJECT,
          pnameAsString, lvalueType.toString()));
      return new LValueResultFwd(lvalueEnv, type, null, null);
    }
    Node propAccessNode = obj.getParent();
    if (propAccessNode.isGetProp() &&
        propAccessNode.getParent().isAssign() &&
        mayWarnAboutPropCreation(pname, propAccessNode, lvalueType)) {
      return new LValueResultFwd(lvalueEnv, type, null, null);
    }
    if (!insideQualifiedName &&
        mayWarnAboutConstProp(propAccessNode, lvalueType, pname)) {
      return new LValueResultFwd(lvalueEnv, type, null, null);
    }
    if (!lvalueType.hasProp(pname)) {
      if (insideQualifiedName && lvalueType.isLoose()) {
        // For loose objects, create the inner property if it doesn't exist.
        lvalueType = lvalueType.withProperty(
            pname, JSType.TOP_OBJECT.withLoose());
        if (lvalueType.isDict() && propAccessNode.isGetProp()) {
          lvalueType = lvalueType.specialize(JSType.TOP_STRUCT);
        } else if (lvalueType.isStruct() && propAccessNode.isGetElem()) {
          lvalueType = lvalueType.specialize(JSType.TOP_DICT);
        }
        lvalueEnv = updateLvalueTypeInEnv(
            lvalueEnv, obj, lvalue.ptr, lvalueType);
      } else {
        // Warn for inexistent prop either on the non-top-level of a qualified
        // name, or for assignment ops that won't create a new property.
        boolean warnForInexistentProp = insideQualifiedName ||
            propAccessNode.getParent().getType() != Token.ASSIGN;
        if (warnForInexistentProp &&
            !lvalueType.isUnknown() &&
            !lvalueType.isDict()) {
          DiagnosticType dt = lvalueType.mayHaveProp(pname)
              ? POSSIBLY_INEXISTENT_PROPERTY : TypeCheck.INEXISTENT_PROPERTY;
          warnings.add(
              JSError.make(obj, dt, pnameAsString, lvalueType.toString()));
          return new LValueResultFwd(lvalueEnv, type, null, null);
        }
      }
    }
    if (propAccessNode.isGetElem()) {
      mayWarnAboutStructPropAccess(obj, lvalueType);
    } else if (propAccessNode.isGetProp()) {
      mayWarnAboutDictPropAccess(obj, lvalueType);
    }
    QualifiedName setterPname =
        new QualifiedName(SETTER_PREFIX + pnameAsString);
    if (lvalueType.hasProp(setterPname)) {
      FunctionType funType = lvalueType.getProp(setterPname).getFunType();
      Preconditions.checkNotNull(funType);
      JSType formalType = funType.getFormalType(0);
      Preconditions.checkState(!formalType.isBottom());
      return new LValueResultFwd(lvalueEnv, formalType, formalType, null);
    }
    return new LValueResultFwd(
        lvalueEnv,
        lvalueType.mayHaveProp(pname) ?
            lvalueType.getProp(pname) : JSType.UNKNOWN,
        lvalueType.mayHaveProp(pname) ?
            lvalueType.getDeclaredProp(pname) : null,
        lvalue.ptr == null ? null : QualifiedName.join(lvalue.ptr, pname)
    );
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
        pickReqObjType(obj.getParent()).withLoose().withProperty(pname, type);
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
      case Token.GETPROP:
        return JSType.TOP_STRUCT;
      case Token.GETELEM:
      case Token.IN:
        return JSType.TOP_DICT;
      case Token.FOR:
        Preconditions.checkState(NodeUtil.isForIn(expr));
        return JSType.TOP_DICT;
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
      default:
        throw new RuntimeException(
            "Unhandled node for pickReqObjType: " + Token.name(exprKind));
    }
  }

  private static String getReadableCalleeName(Node expr) {
    return expr.isQualifiedName() ? expr.getQualifiedName() : "";
  }

  private static JSType specializeWithCorrection(
      JSType inferred, JSType required) {
    JSType specializedType = inferred.specialize(required);
    if (specializedType.isBottom()) {
      return required;
    }
    return specializedType;
  }

  TypeEnv getEntryTypeEnv() {
    return getOutEnv(cfg.getEntry());
  }

  private TypeEnv getFinalTypeEnv() {
    TypeEnv env = getInEnv(cfg.getImplicitReturn());
    if (env == null) {
      // This function only exits with THROWs
      env = new TypeEnv();
      return envPutType(env, RETVAL_ID, JSType.BOTTOM);
    }
    return env;
  }

  private static class DeferredCheck {
    final Node callSite;
    final Scope callerScope;
    final Scope calleeScope;
    // Null types means that they were declared
    // (and should have been checked during inference)
    JSType expectedRetType;
    List<JSType> argTypes;

    DeferredCheck(
        Node callSite,
        JSType expectedRetType,
        Scope callerScope,
        Scope calleeScope) {
      this.callSite = callSite;
      this.expectedRetType = expectedRetType;
      this.callerScope = callerScope;
      this.calleeScope = calleeScope;
    }

    void updateReturn(JSType expectedRetType) {
      if (this.expectedRetType != null) {
        this.expectedRetType =
            JSType.meet(this.expectedRetType, expectedRetType);
      }
    }

    void updateArgTypes(List<JSType> argTypes) {
      this.argTypes = argTypes;
    }

    private void runCheck(
        Map<Scope, JSType> summaries, WarningReporter warnings) {
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
      Node argNode = callSite.getFirstChild().getNext();
      // this.argTypes can be null if in the fwd direction the analysis of the
      // call return prematurely, eg, because of a WRONG_ARGUMENT_COUNT.
      if (this.argTypes == null) {
        return;
      }
      for (JSType argType : this.argTypes) {
        JSType formalType = fnSummary.getFormalType(i);
        if (argNode.isName() &&
            callerScope.isKnownFunction(argNode.getString())) {
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
