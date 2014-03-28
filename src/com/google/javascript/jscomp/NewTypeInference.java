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
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.GlobalTypeInfo.Scope;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.jscomp.newtypes.DeclaredFunctionType;
import com.google.javascript.jscomp.newtypes.FunctionType;
import com.google.javascript.jscomp.newtypes.FunctionTypeBuilder;
import com.google.javascript.jscomp.newtypes.JSType;
import com.google.javascript.jscomp.newtypes.QualifiedName;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * New type inference algorithm.
 *
 * Under development. DO NOT USE!
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 *
 * Features left to implement:
 * - struct/dict
 * - @const/@final
 * - @enum
 * - @typedef
 * - @define
 * - @lends
 * - @private (maybe)
 * - @protected (maybe)
 * - separate scope for catch variables
 * - closure-specific constructs
 * - getters/setters
 */
public class NewTypeInference implements CompilerPass {

  static final DiagnosticType MISTYPED_ASSIGN_RHS = DiagnosticType.warning(
      "JSC_MISTYPED_ASSIGN_RHS",
      "The right-hand side in the assignment is not a subtype of the " +
      "declared type.\n" +
      "declared : {0}\n" +
      "found    : {1}\n");

  static final DiagnosticType INVALID_OPERAND_TYPE = DiagnosticType.warning(
      "JSC_INVALID_OPERAND_TYPE",
      "Invalid type(s) for operator {0}.\n" +
      "expected : {1}\n" +
      "found    : {2}\n");

  static final DiagnosticType RETURN_NONDECLARED_TYPE = DiagnosticType.warning(
      "JSC_RETURN_NONDECLARED_TYPE",
      "Returned type does not match declared return type.\n " +
      "declared : {0}\n" +
      "found    : {1}\n");

  static final DiagnosticType INVALID_INFERRED_RETURN_TYPE =
      DiagnosticType.warning(
          "JSC_INVALID_INFERRED_RETURN_TYPE",
          "Function called in context that expects incompatible type.\n " +
          "expected : {0}\n" +
          "found    : {1}\n");

  static final DiagnosticType INVALID_ARGUMENT_TYPE = DiagnosticType.warning(
      "JSC_INVALID_ARGUMENT_TYPE",
      "Invalid type for parameter {0} of function {1}.\n " +
      "expected : {2}\n" +
      "found    : {3}\n");

  static final DiagnosticType CROSS_SCOPE_GOTCHA = DiagnosticType.warning(
      "JSC_CROSS_SCOPE_GOTCHA",
      "You thought we weren't going to notice? Guess again.\n" +
      "Variable {0} typed inconsistently across scopes.\n " +
      "In outer scope : {1}\n" +
      "In inner scope : {2}\n");

  static final DiagnosticType POSSIBLY_INEXISTENT_PROPERTY =
      DiagnosticType.warning(
          "JSC_POSSIBLY_INEXISTENT_PROPERTY",
          "Property {0} may not be present on {1}.");

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

  private Set<JSError> warnings;
  private final AbstractCompiler compiler;
  Map<DiGraphEdge<Node, ControlFlowGraph.Branch>, TypeEnv> envs;
  Map<Scope, JSType> summaries;
  Map<Node, DeferredCheck> deferredChecks;
  ControlFlowGraph<Node> cfg;
  Node jsRoot;
  Scope currentScope;
  GlobalTypeInfo symbolTable;
  static final String RETVAL_ID = "%return";
  private JSType arrayType, regexpType; // used for array and regexp literals
  private static boolean debugging = false;

  NewTypeInference(AbstractCompiler compiler) {
    this.warnings = Sets.newHashSet();
    this.compiler = compiler;
    this.envs = Maps.newHashMap();
    this.summaries = Maps.newHashMap();
    this.deferredChecks = Maps.newHashMap();
  }

  @VisibleForTesting
  public Scope processForTesting(Node externs, Node root) {
    process(externs, root);
    return symbolTable.getGlobalScope();
  }

  @Override
  public void process(Node externs, Node root) {
    jsRoot = root;
    symbolTable = compiler.getSymbolTable();

    // The type inference needs to know about the Array and RegExp types, in
    // order to handle array and regexp literals. This info comes from externs.
    // If it's not there, don't bother be more precise than JSType.UNKNOWN.
    Scope gs = symbolTable.getGlobalScope();
    JSType arrayCtor = gs.getDeclaredTypeOf("Array");
    arrayType = arrayCtor == null ?
        JSType.UNKNOWN : arrayCtor.getFunType().getReturnType();
    JSType regexpCtor = gs.getDeclaredTypeOf("RegExp");
    regexpType = regexpCtor == null ?
        JSType.UNKNOWN : regexpCtor.getFunType().getReturnType();

    for (Scope scope : symbolTable.getScopes()) {
      analyzeFunction(scope);
    }
    for (DeferredCheck check : deferredChecks.values()) {
      check.runCheck(summaries, warnings);
    }
    for (JSError warning : warnings) {
      compiler.report(warning);
    }
  }

  private static void println(Object ... objs) {
    if (debugging) {
      StringBuilder b = new StringBuilder();
      for (Object obj: objs) {
        b.append(obj == null ? "null" : obj.toString());
      }
      System.out.println(b.toString());
    }
  }

  private static Node getConditionExpression(Node n) {
    return NodeUtil.isForIn(n) ?
        n.getFirstChild() : NodeUtil.getConditionExpression(n);
  }

  private TypeEnv getInEnv(Node n) {
    Preconditions.checkArgument(cfg.getInEdges(n).size() > 0);
    TypeEnv inEnv = null;
    for (DiGraphEdge<Node, ControlFlowGraph.Branch> de : cfg.getInEdges(n)) {
      TypeEnv env = envs.get(de);
      inEnv = (inEnv == null) ? env : TypeEnv.join(inEnv, env);
    }
    return inEnv == null ? new TypeEnv() : inEnv;
  }

  private TypeEnv getOutEnv(Node n) {
    Preconditions.checkState(cfg.getOutEdges(n).size() > 0);
    TypeEnv outEnv = null;
    for (DiGraphEdge<Node, ControlFlowGraph.Branch> de : cfg.getOutEdges(n)) {
      TypeEnv env = envs.get(de);
      outEnv = (outEnv == null) ? env : TypeEnv.join(outEnv, env);
    }
    return outEnv == null ? new TypeEnv() : outEnv;
  }

  private TypeEnv setOutEnv(Node n, TypeEnv e) {
    for (DiGraphEdge<Node, ControlFlowGraph.Branch> de :
        cfg.getOutEdges(n)) {
      envs.put(de, e);
    }
    return e;
  }

  private TypeEnv setInEnv(Node n, TypeEnv e) {
    for (DiGraphEdge<Node, ControlFlowGraph.Branch> de :
        cfg.getInEdges(n)) {
      envs.put(de, e);
    }
    return e;
  }

  // Initialize the type environments on the CFG edges before the FWD analysis.
  private void initEdgeEnvsFwd() {
    // TODO(user): Revisit what we throw away after the bwd analysis
    DiGraphNode<Node, ControlFlowGraph.Branch> entry = cfg.getEntry();
    DiGraphEdge<Node, ControlFlowGraph.Branch> entryOutEdge =
        cfg.getOutEdges(entry.getValue()).get(0);
    TypeEnv entryEnv = envs.get(entryOutEdge);
    initEdgeEnvs(new TypeEnv());

    // For function scopes, add the formal parameters and the free variables
    // from outer scopes to the environment.
    if (currentScope.isFunction()) {
      Set<String> formalsAndOuters = currentScope.getOuterVars();
      formalsAndOuters.addAll(currentScope.getFormals());
      if (currentScope.hasThis()) {
        formalsAndOuters.add("this");
      }
      for (String name : formalsAndOuters) {
        JSType declType = currentScope.getDeclaredTypeOf(name);
        JSType initType;
        if (declType == null) {
          initType = envGetType(entryEnv, name);
        } else if (declType.getFunTypeIfSingletonObj() != null &&
            declType.getFunTypeIfSingletonObj().isConstructor()) {
          initType =
              declType.getFunTypeIfSingletonObj().createConstructorObject();
        } else {
          initType = declType;
        }
        entryEnv = envPutType(entryEnv, name, initType.withLocation(name));
      }
      entryEnv = envPutType(entryEnv, RETVAL_ID, JSType.UNDEFINED);
    }

    // For all scopes, add local variables and (local) function definitions
    // to the environment.
    for (String local : currentScope.getLocals()) {
      entryEnv = envPutType(entryEnv, local, JSType.UNDEFINED);
    }
    for (String fnName : currentScope.getLocalFunDefs()) {
      JSType summaryType = summaries.get(currentScope.getScope(fnName));
      FunctionType fnType = summaryType.getFunType();
      if (fnType.isConstructor()) {
        summaryType = fnType.createConstructorObject();
      } else {
        summaryType = summaryType.withProperty(
            new QualifiedName("prototype"), JSType.TOP_OBJECT);
      }
      entryEnv = envPutType(entryEnv, fnName, summaryType);
    }
    println("Keeping env: ", entryEnv);
    envs.put(entryOutEdge, entryEnv);
  }

  // Initialize the type environments on the CFG edges before the BWD analysis.
  private void initEdgeEnvsBwd() {
    TypeEnv env = new TypeEnv();
    Set<String> varNames = currentScope.getOuterVars();
    varNames.addAll(currentScope.getFormals());
    varNames.addAll(currentScope.getLocals());
    if (currentScope.hasThis()) {
      varNames.add("this");
    }
    for (String varName : varNames) {
      JSType declType = currentScope.getDeclaredTypeOf(varName);
      if (declType == null) {
        env = envPutType(env, varName, JSType.UNKNOWN);
      // TODO(blickly): Fix discrepancy in constructor initialization.
      } else {
        env = envPutType(env, varName, declType);
      }
    }
    for (String fnName : currentScope.getLocalFunDefs()) {
      JSType summaryType = summaries.get(currentScope.getScope(fnName));
      FunctionType fnType = summaryType.getFunType();
      if (fnType.isConstructor()) {
        summaryType = fnType.createConstructorObject();
      } else {
        summaryType = summaryType.withProperty(
            new QualifiedName("prototype"), JSType.TOP_OBJECT);
      }
      env = envPutType(env, fnName, summaryType);
    }
    initEdgeEnvs(env);
  }

  private void initEdgeEnvs(TypeEnv env) {
    for (DiGraphEdge<Node, ControlFlowGraph.Branch> e : cfg.getEdges()) {
      envs.put(e, env);
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
    Node currentNode = dn.getValue();
    switch (currentNode.getType()) {
      case Token.DO:
      case Token.WHILE:
      case Token.FOR:
        // Do the loop body first, then the loop follow.
        // For DO loops, we do BODY-CONDT-CONDF-FOLLOW
        // Since CONDT is currently unused, this could be optimized.
        List<DiGraphEdge<Node, ControlFlowGraph.Branch>> outEdges =
            cfg.getOutEdges(dn.getValue());
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
            cfg.getInEdges(dn.getValue())) {
          if (!seen.contains(inEdge.getSource())
              && !inEdge.getSource().getValue().isDo()) {
            return;
          }
        }
        seen.add(dn);
        if (cfg.getEntry() != dn) {
          workset.add(dn);
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
        Lists.newLinkedList();
    buildWorkset(cfg.getEntry(), workset);
    /* println("Workset: ", workset); */
    Collections.reverse(workset);
    initEdgeEnvsBwd();
    analyzeFunctionBwd(workset);
    Collections.reverse(workset);
    initEdgeEnvsFwd();
    analyzeFunctionFwd(workset);
    if (scope.isFunction()) {
      createSummary(scope);
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
      TypeEnv outEnv = getOutEnv(n);
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
          inEnv = null;
          for (Node nameNode = n.getFirstChild(); nameNode != null;
               nameNode = nameNode.getNext()) {
            String varName = nameNode.getQualifiedName();
            Node rhs = nameNode.getFirstChild();
            JSType declType = currentScope.getDeclaredTypeOf(varName);
            inEnv = envPutType(outEnv, varName, JSType.UNKNOWN);
            if (rhs == null || currentScope.isLocalFunDef(varName)) {
              continue;
            }
            JSType inferredType = envGetType(outEnv, varName);
            JSType requiredType;
            if (declType == null) {
              requiredType = inferredType;
            } else {
              // TODO(user): look if the meet is needed
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
        case Token.EMPTY:
        case Token.SCRIPT:
        case Token.TRY:
          inEnv = outEnv;
          break;
        case Token.DO:
        case Token.FOR:
        case Token.IF:
        case Token.WHILE:
          inEnv = analyzeExprBwd(getConditionExpression(n), outEnv).env;
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
      setInEnv(n, inEnv);
    }
  }

  private void analyzeFunctionFwd(
      List<DiGraphNode<Node, ControlFlowGraph.Branch>> workset) {
    for (DiGraphNode<Node, ControlFlowGraph.Branch> dn : workset) {
      Node n = dn.getValue();
      Preconditions.checkState(n != null,
          "Implicit return should not be in workset.");
      TypeEnv inEnv = getInEnv(n);
      TypeEnv outEnv = null;

      println("\tFWD Statment: ", n);
      println("\t\tinEnv: ", inEnv);
      boolean conditional = false;
      switch (n.getType()) {
        case Token.BLOCK:
        case Token.BREAK:
        case Token.CATCH:
        case Token.CONTINUE:
        case Token.DEFAULT_CASE:
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
          conditional = true;
          analyzeConditionalStmFwd(n, getConditionExpression(n), inEnv);
          break;
        case Token.CASE: {
          conditional = true;
          // See analyzeExprFwd#Token.CASE for how to handle this precisely
          analyzeConditionalStmFwd(n, n, inEnv);
          break;
        }
        case Token.VAR:
          outEnv = inEnv;
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
        setOutEnv(n, outEnv);
      }
    }
  }

  private void analyzeConditionalStmFwd(Node stm, Node cond, TypeEnv inEnv) {
    for (DiGraphEdge<Node, ControlFlowGraph.Branch> outEdge :
             cfg.getOutEdges(stm)) {
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
    TypeEnv entryEnv = getInitTypeEnv();
    TypeEnv exitEnv = getFinalTypeEnv();
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    // Each formal and outer var is bound to some value at the start of the
    // fun. Get the names of vars where this value may have flowed to.
    Multimap<String, String> taints = exitEnv.getTaints();

    JSType formalType;
    int formalIndex = 0;
    DeclaredFunctionType declType = fn.getDeclaredType();
    int reqArity = declType.getRequiredArity();
    int optArity = declType.getOptionalArity();
    if (declType.isGeneric()) {
      builder.addTypeParameters(declType.getTypeParameters());
    }
    for (String formal : fn.getFormals()) {
      formalType = fn.getDeclaredTypeOf(formal);
      if (formalType == null) {
        formalType = envGetType(entryEnv, formal);
        // TODO(user): fix so we get the adjusted end-of-fwd type for objs too
        if (!formalType.hasNonScalar() || formalType.getFunType() != null) {
          for (String taintedVarName : taints.get(formal)) {
            JSType taintType = envGetType(exitEnv, taintedVarName);
            formalType = JSType.meet(taintType, formalType);
          }
        }
        // TODO(blickly): Use location to infer polymorphism
        formalType = formalType.withLocation(null);
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
      JSType outerType = envGetType(entryEnv, outer);
      for (String taintedVarName : taints.get(outer)) {
        JSType taintType = envGetType(exitEnv, taintedVarName);
        outerType = JSType.meet(taintType, outerType);
      }
      // TODO(blickly): Use location to infer polymorphism
      builder.addOuterVarPrecondition(outer, outerType.withLocation(null));
    }

    builder.addNominalType(declType.getNominalType());
    JSType declRetType = declType.getReturnType();
    JSType actualRetType = envGetType(exitEnv, RETVAL_ID);

    if (declRetType == null) {
      // TODO(blickly): Use location to infer polymorphism
      builder.addRetType(actualRetType.withLocation(null));
    } else {
      builder.addRetType(declRetType);
      if (!isAllowedToNotReturn(fn) &&
          !JSType.UNDEFINED.isSubtypeOf(declRetType) &&
          hasPathWithNoReturn(cfg)) {
        warnings.add(JSError.make(fn.getRoot(),
                CheckMissingReturn.MISSING_RETURN_STATEMENT,
                declRetType.toString()));
      }
    }
    JSType summary = builder.buildType();
    println("Function summary for ", fn.getReadableName());
    println("\t", summary);
    summaries.put(fn, summary);
  }

  private static boolean isAllowedToNotReturn(Scope methodScope) {
    Node fn = methodScope.getRoot();
    if (fn.isFromExterns()) {
      return true;
    }
    if (!NodeUtil.isPrototypeMethod(fn)) {
      return false;
    }
    // TODO(user): We need all the qname here before .prototype, not just
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
    String varName = nameNode.getQualifiedName();
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
    int exprKind = expr.getType();
    switch (exprKind) {
      case Token.EMPTY: // can be created by a FOR with empty condition
        return new EnvTypePair(inEnv, JSType.UNKNOWN);
      case Token.FUNCTION: {
        String fnName = symbolTable.getFunInternalName(expr);
        return new EnvTypePair(inEnv, envGetType(inEnv, fnName));
      }
      case Token.FALSE:
      case Token.NULL:
      case Token.NUMBER:
      case Token.STRING:
      case Token.TRUE:
        return new EnvTypePair(inEnv, scalarValueToType(exprKind));
      case Token.OBJECTLIT: {
        JSType result = JSType.TOP_OBJECT;
        TypeEnv env = inEnv;
        for (Node key: expr.children()) {
          QualifiedName pname = new QualifiedName(NodeUtil.getObjectLitKeyName(key));
          JSType reqPtype = requiredType.mayHaveProp(pname) ?
              requiredType.getProp(pname) : JSType.UNKNOWN;
          JSType specPtype = specializedType.mayHaveProp(pname) ?
              specializedType.getProp(pname) : JSType.UNKNOWN;
          EnvTypePair pair =
              analyzeExprFwd(key.getLastChild(), env, reqPtype, specPtype);
          result = result.withProperty(pname, pair.type);
          env = pair.env;
        }
        return new EnvTypePair(env, result);
      }
      case Token.THIS: {
        if (!currentScope.hasThis()) {
          warnings.add(JSError.make(expr, CheckGlobalThis.GLOBAL_THIS));
          return new EnvTypePair(inEnv, JSType.UNKNOWN);
        }
        JSType thisType = currentScope.getDeclaredTypeOf("this");
        return new EnvTypePair(inEnv, thisType);
      }
      case Token.NAME: { // Fwd
        String varName = expr.getQualifiedName();
        if (varName.equals("undefined")) {
          return new EnvTypePair(inEnv, JSType.UNDEFINED);
        }

        if (currentScope.isLocalVar(varName) ||
            currentScope.isFormalParam(varName) ||
            currentScope.isLocalFunDef(varName) ||
            currentScope.isOuterVar(varName)) {
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
            // We have a heuristic check here to avoid the spurious warnings,
            // but we also miss some true warnings.
            if (currentScope.getDeclaredTypeOf(varName) == null &&
                requiredType.isSubtypeOf(inferredType)) {
              inferredType = requiredType;
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
              requiredType.hasNonScalar()) {
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
      case Token.AND:
      case Token.OR: {
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
      case Token.INC:
      case Token.DEC:
      case Token.BITNOT:
      case Token.POS:
      case Token.NEG: { // Unary operations on numbers
        Node child = expr.getFirstChild();
        EnvTypePair pair = analyzeExprFwd(child, inEnv, JSType.NUMBER);
        if (!pair.type.isSubtypeOf(JSType.NUMBER)) {
          warnInvalidOperand(child, expr.getType(), JSType.NUMBER, pair.type);
        }
        pair.type = JSType.NUMBER;
        return pair;
      }
      case Token.TYPEOF: {
        EnvTypePair pair = analyzeExprFwd(expr.getFirstChild(), inEnv);
        pair.type = JSType.STRING;
        return pair;
      }
      case Token.INSTANCEOF: {
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
        ctorPair = analyzeExprFwd(ctor, objPair.env, JSType.topFunction());
        JSType ctorType = ctorPair.type;
        FunctionType ctorFunType = ctorType.getFunType();
        if (!ctorType.isUnknown() &&
            (!ctorType.isSubtypeOf(JSType.topFunction()) ||
                !ctorFunType.isConstructor())) {
          warnInvalidOperand(
              ctor, Token.INSTANCEOF, "a constructor function", ctorType);
        }
        if (ctorFunType == null || !ctorFunType.isConstructor() ||
            (!specializedType.isTruthy() && !specializedType.isFalsy())) {
          ctorPair.type = JSType.BOOLEAN;
          return ctorPair;
        }

        // We are in a specialized context *and* we know the constructor type
        JSType instanceType = ctorFunType.getTypeOfThis();
        objPair = analyzeExprFwd(obj, inEnv, JSType.UNKNOWN,
            specializedType.isTruthy() ?
            objPair.type.specialize(instanceType) :
            objPair.type.removeType(instanceType));
        ctorPair = analyzeExprFwd(ctor, objPair.env, JSType.topFunction());
        ctorPair.type = JSType.BOOLEAN;
        return ctorPair;
      }
      case Token.ADD: {
        Node lhs = expr.getFirstChild();
        Node rhs = expr.getLastChild();
        EnvTypePair lhsPair = analyzeExprFwd(lhs, inEnv, JSType.NUM_OR_STR);
        EnvTypePair rhsPair =
            analyzeExprFwd(rhs, lhsPair.env, JSType.NUM_OR_STR);
        JSType lhsType = lhsPair.type;
        JSType rhsType = rhsPair.type;
        if (!lhsType.isSubtypeOf(JSType.NUM_OR_STR)) {
          warnInvalidOperand(lhs, expr.getType(), JSType.NUM_OR_STR, lhsType);
        }
        if (!rhsType.isSubtypeOf(JSType.NUM_OR_STR)) {
          warnInvalidOperand(rhs, expr.getType(), JSType.NUM_OR_STR, rhsType);
        }
        return new EnvTypePair(rhsPair.env, JSType.plus(lhsType, rhsType));
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
      case Token.URSH: { // Binary operations on numbers
        Node lhs = expr.getFirstChild();
        Node rhs = expr.getLastChild();
        EnvTypePair lhsPair = analyzeExprFwd(lhs, inEnv, JSType.NUMBER);
        EnvTypePair rhsPair = analyzeExprFwd(rhs, lhsPair.env, JSType.NUMBER);
        if (!lhsPair.type.isSubtypeOf(JSType.NUMBER)) {
          warnInvalidOperand(lhs, expr.getType(), JSType.NUMBER, lhsPair.type);
        }
        if (!rhsPair.type.isSubtypeOf(JSType.NUMBER)) {
          warnInvalidOperand(rhs, expr.getType(), JSType.NUMBER, rhsPair.type);
        }
        rhsPair.type = JSType.NUMBER;
        return rhsPair;
      }
      case Token.ASSIGN: { // Fwd
        Node lhs = expr.getFirstChild();
        Node rhs = expr.getLastChild();
        LValueResultFwd lvalue = analyzeLValueFwd(lhs, inEnv, requiredType);
        JSType declType = lvalue.declType;
        EnvTypePair rhsPair =
            analyzeExprFwd(rhs, lvalue.env, requiredType, specializedType);
        JSType rhsType = rhsPair.type;
        if (declType != null && !rhsType.isSubtypeOf(declType)) {
          warnings.add(JSError.make(expr, MISTYPED_ASSIGN_RHS,
              declType.toString(), rhsType.toString()));
          // Don't flow the wrong initialization
          rhsType = declType;
        }
        return new EnvTypePair(
            updateLvalueTypeInEnv(rhsPair.env, lhs, lvalue.ptr, rhsType),
            rhsType);
      }
      case Token.ASSIGN_ADD: {
        Node lhs = expr.getFirstChild();
        Node rhs = expr.getLastChild();
        JSType lhsReqType = specializeWithCorrection(
            requiredType, JSType.NUM_OR_STR);
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
      case Token.ASSIGN_BITOR:
      case Token.ASSIGN_BITXOR:
      case Token.ASSIGN_BITAND:
      case Token.ASSIGN_LSH:
      case Token.ASSIGN_RSH:
      case Token.ASSIGN_URSH:
      case Token.ASSIGN_SUB:
      case Token.ASSIGN_MUL:
      case Token.ASSIGN_DIV:
      case Token.ASSIGN_MOD: {
        Node lhs = expr.getFirstChild();
        Node rhs = expr.getLastChild();
        LValueResultFwd lvalue = analyzeLValueFwd(lhs, inEnv, JSType.NUMBER);
        JSType lhsType = lvalue.type;
        if (!lhsType.isSubtypeOf(JSType.NUMBER)) {
          warnInvalidOperand(lhs, expr.getType(), JSType.NUMBER, lhsType);
        }
        EnvTypePair pair = analyzeExprFwd(rhs, lvalue.env, JSType.NUMBER);
        if (!pair.type.isSubtypeOf(JSType.NUMBER)) {
          warnInvalidOperand(rhs, expr.getType(), JSType.NUMBER, pair.type);
        }
        return new EnvTypePair(
            updateLvalueTypeInEnv(pair.env, lhs, lvalue.ptr, JSType.NUMBER),
            JSType.NUMBER);
      }
      case Token.SHEQ:
      case Token.SHNE:
        return analyzeStrictComparisonFwd(exprKind,
            expr.getFirstChild(), expr.getLastChild(), inEnv, specializedType);
      case Token.EQ:
      case Token.NE:
        return analyzeNonStrictComparisonFwd(expr, inEnv, specializedType);
      case Token.LT:
      case Token.GT:
      case Token.LE:
      case Token.GE: {
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
          TypeEnv env = envPutType(
              rhsPair.env, lhs.getQualifiedName(), JSType.TOP_SCALAR);
          env = envPutType(
              rhsPair.env, rhs.getQualifiedName(), JSType.TOP_SCALAR);
          return new EnvTypePair(env, JSType.BOOLEAN);
        }
        JSType lhsType = lhsPair.type;
        JSType rhsType = rhsPair.type;

        if (!lhsType.isSubtypeOf(JSType.TOP_SCALAR) ||
            !rhsType.isSubtypeOf(JSType.TOP_SCALAR) ||
            !JSType.areCompatibleScalarTypes(lhsType, rhsType)) {
          warnInvalidOperand(expr, exprKind, "matching scalar types",
              lhsType.toString() + ", " + rhsType.toString());
        }
        rhsPair.type = JSType.BOOLEAN;
        return rhsPair;
      }
      case Token.GETPROP:
        Preconditions.checkState(
            !NodeUtil.isAssignmentOp(expr.getParent()) ||
            !NodeUtil.isLValue(expr));
        return analyzePropAccessFwd(
            expr.getFirstChild(), expr.getLastChild().getString(),
            inEnv, requiredType, specializedType);
      case Token.HOOK: {
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
      case Token.CALL: // Fwd
      case Token.NEW: {
        Node callee = expr.getFirstChild();
        EnvTypePair calleePair =
            analyzeExprFwd(callee, inEnv, JSType.topFunction());
        JSType calleeType = calleePair.type;
        if (!calleeType.isSubtypeOf(JSType.topFunction())) {
          warnings.add(JSError.make(
              expr, TypeCheck.NOT_CALLABLE, calleeType.toString()));
        }
        FunctionType funType = calleeType.getFunType();
        if (funType == null || funType.isTopFunction()) {
          return analyzeCallNodeArgumentsFwd(expr, inEnv);
        } else if (funType.isLoose()) {
          return analyzeLooseCallNodeFwd(expr, inEnv, requiredType);
        } else if (expr.isCall() && funType.isConstructor()) {
          warnings.add(JSError.make(
              expr, TypeCheck.CONSTRUCTOR_NOT_CALLABLE, funType.toString()));
          return analyzeCallNodeArgumentsFwd(expr, inEnv);
        } else if (expr.isNew() && !funType.isConstructor()) {
          warnings.add(JSError.make(
              expr, TypeCheck.NOT_A_CONSTRUCTOR, funType.toString()));
          return analyzeCallNodeArgumentsFwd(expr, inEnv);
        }
        int maxArity = funType.getMaxArity();
        int minArity = funType.getMinArity();
        int numArgs = expr.getChildCount() - 1;
        if (numArgs < minArity || numArgs > maxArity) {
          warnings.add(JSError.make(
              expr, TypeCheck.WRONG_ARGUMENT_COUNT, "",
              Integer.toString(numArgs), Integer.toString(minArity),
              " and at most " + maxArity));
          return analyzeCallNodeArgumentsFwd(expr, inEnv);
        }
        FunctionType origFunType = funType; // save for later
        if (funType.isGeneric()) {
          Map<String, JSType> typeMap =
              calcTypeInstantiationFwd(expr, funType, inEnv);
          funType = funType.instantiateGenerics(typeMap);
          println("Instantiated function type: " + funType);
        }
        // argTypes collects types of actuals for deferred checks.
        List<JSType> argTypes = Lists.newArrayList();
        TypeEnv tmpEnv = inEnv;
        Node arg = expr.getChildAtIndex(1);
        for (int i = 0; i < numArgs; i++) {
          JSType formalType = funType.getFormalType(i);
          if (formalType.isBottom()) {
            warnings.add(JSError.make(expr, CALL_FUNCTION_WITH_BOTTOM_FORMAL,
                  Integer.toString(i)));
            formalType = JSType.UNKNOWN;
          }
          EnvTypePair pair = analyzeExprFwd(arg, tmpEnv, formalType);
          // Allow passing undefined for an optional argument.
          if (i >= minArity && pair.type.equals(JSType.UNDEFINED)) {
            pair.type = JSType.UNKNOWN; // No deferred check needed.
          } else if (!pair.type.isSubtypeOf(formalType)) {
            warnings.add(JSError.make(arg, INVALID_ARGUMENT_TYPE,
                    Integer.toString(i + 1), "",
                    formalType.toString(), pair.type.toString()));
            pair.type = JSType.UNKNOWN; // No deferred check needed.
          }
          Preconditions.checkState(!pair.type.equals(JSType.topFunction()));
          argTypes.add(pair.type);
          tmpEnv = pair.env;
          arg = arg.getNext();
        }
        JSType retType = funType.getReturnType();
        if (callee.isName()) {
          String calleeName = callee.getQualifiedName();
          if (currentScope.isKnownFunction(calleeName)) {
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
                Preconditions.checkState(dc != null,
                    "No deferred check created in backward direction for " +
                    expr);
                dc.updateReturn(expectedRetType);
              } else {
                dc = new DeferredCheck(
                    expr, currentScope, currentScope.getScope(calleeName));
                dc.updateReturn(JSType.UNKNOWN);
                deferredChecks.put(expr, dc);
              }
              dc.updateArgTypes(argTypes);
            }
          }
        }
        return new EnvTypePair(tmpEnv, retType);
      }
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
      case Token.GETELEM: {
        Node receiver = expr.getFirstChild();
        Node index = expr.getLastChild();
        if (index.isString()) {
          return analyzePropAccessFwd(receiver, index.getString(),
              inEnv, requiredType, specializedType);
        }
        EnvTypePair pair = analyzeExprFwd(index, inEnv);
        pair = analyzeExprFwd(receiver, pair.env, JSType.TOP_OBJECT);
        pair.type = requiredType;
        return pair;
      }
      case Token.VOID: {
        EnvTypePair pair = analyzeExprFwd(expr.getFirstChild(), inEnv);
        pair.type = JSType.UNDEFINED;
        return pair;
      }
      case Token.IN: {
        Node lhs = expr.getFirstChild();
        Node rhs = expr.getLastChild();
        EnvTypePair pair;

        pair = analyzeExprFwd(lhs, inEnv, JSType.NUM_OR_STR);
        if (!pair.type.isSubtypeOf(JSType.NUM_OR_STR)) {
          warnInvalidOperand(lhs, Token.IN, JSType.NUM_OR_STR, pair.type);
        }
        pair = analyzeExprFwd(rhs, pair.env, JSType.TOP_OBJECT);
        if (!pair.type.isSubtypeOf(JSType.TOP_OBJECT)) {
          warnInvalidOperand(rhs, Token.IN, "Object", pair.type);
          pair.type = JSType.BOOLEAN;
          return pair;
        }

        JSType resultType = JSType.BOOLEAN;
        if (lhs.isString()) {
          QualifiedName pname = new QualifiedName(lhs.getString());
          if (specializedType.isTruthy()) {
            pair = analyzeExprFwd(rhs, inEnv, JSType.TOP_OBJECT,
                JSType.TOP_OBJECT.withPropertyRequired(pname.getLeftmostName()));
            resultType = JSType.TRUE_TYPE;
          } else if (specializedType.isFalsy()) {
            pair = analyzeExprFwd(rhs, inEnv, JSType.TOP_OBJECT);
            // If the rhs is a loose object, we won't warn about missing
            // properties, despite removing the type here.
            // The only way to have that warning would be to keep track of props
            // that a loose object *cannot* have; but the implementation cost
            // is probably not worth it.
            pair = analyzeExprFwd(rhs, inEnv, JSType.TOP_OBJECT,
                pair.type.withoutProperty(pname));
            resultType = JSType.FALSE_TYPE;
          }
        }
        pair.type = resultType;
        return pair;
      }
      case Token.DELPROP: {
        // IRFactory checks that the operand is a name, getprop or getelem.
        // No further warnings here.
        EnvTypePair pair = analyzeExprFwd(expr.getFirstChild(), inEnv);
        pair.type = JSType.BOOLEAN;
        return pair;
      }
      case Token.VAR: { // Can happen iff its parent is a for/in.
        Node vdecl = expr.getFirstChild();
        String name = vdecl.getQualifiedName();
        // For/in can never have rhs of its VAR
        Preconditions.checkState(!vdecl.hasChildren());
        return new EnvTypePair(
            envPutType(inEnv, name, JSType.STRING), JSType.STRING);
      }
      case Token.REGEXP:
        return new EnvTypePair(inEnv, regexpType);
      case Token.ARRAYLIT:
        TypeEnv env = inEnv;
        for (Node arrayElm = expr.getFirstChild(); arrayElm != null;
             arrayElm = arrayElm.getNext()) {
          env = analyzeExprFwd(arrayElm, env).env;
        }
        return new EnvTypePair(env, arrayType);
      case Token.CAST:
        EnvTypePair pair = analyzeExprFwd(expr.getFirstChild(), inEnv);
        JSType fromType = pair.type;
        JSType toType = symbolTable.getCastType(expr);
        if (!toType.isSubtypeOf(fromType) && !fromType.isSubtypeOf(toType)) {
          warnings.add(JSError.make(expr, TypeValidator.INVALID_CAST,
                  fromType.toString(), toType.toString()));
        }
        pair.type = toType;
        return pair;
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

  private EnvTypePair analyzeCallNodeArgumentsFwd(
      Node callNode, TypeEnv inEnv) {
    TypeEnv env = inEnv;
    for (Node arg = callNode.getFirstChild(); arg != null;
        arg = arg.getNext()) {
      env = analyzeExprFwd(arg, env).env;
    }
    return new EnvTypePair(env, JSType.UNKNOWN);

  }

  private EnvTypePair analyzeStrictComparisonFwd(int comparisonOp,
      Node lhs, Node rhs, TypeEnv inEnv, JSType specializedType) {
    if ((specializedType.isTruthy() || specializedType.isFalsy()) &&
        (lhs.isTypeOf() || rhs.isTypeOf())) {
      if (lhs.isTypeOf()) {
        return analyzeSpecializedTypeof(
            lhs, rhs, comparisonOp, inEnv, specializedType);
      } else {
        return analyzeSpecializedTypeof(
            rhs, lhs, comparisonOp, inEnv, specializedType);
      }
    }

    EnvTypePair lhsPair = analyzeExprFwd(lhs, inEnv);
    EnvTypePair rhsPair = analyzeExprFwd(rhs, lhsPair.env);

    if ((comparisonOp == Token.SHEQ && specializedType.isTruthy()) ||
        (comparisonOp == Token.SHNE && specializedType.isFalsy())) {
      JSType meetType = JSType.meet(lhsPair.type, rhsPair.type);
      lhsPair = analyzeExprFwd(lhs, rhsPair.env, JSType.UNKNOWN, meetType);
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
      lhsPair = analyzeExprFwd(lhs, rhsPair.env, JSType.UNKNOWN, lhsType);
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
    if (comparedType.isUnknown()) {
      pair = analyzeExprFwd(typeofRand, inEnv);
      pair = analyzeExprFwd(typeString, pair.env);
    } else if ((specializedType.isTruthy() &&
         (comparisonOp == Token.SHEQ || comparisonOp == Token.EQ)) ||
        (specializedType.isFalsy() &&
         (comparisonOp == Token.SHNE || comparisonOp == Token.NE))) {
      pair = analyzeExprFwd(typeofRand, inEnv, JSType.UNKNOWN, comparedType);
    } else {
      pair = analyzeExprFwd(
          typeofRand, inEnv, JSType.UNKNOWN, comparedType.negate());
    }
    pair.type = specializedType.toBoolean();
    return pair;
  }

  private static JSType getTypeFromString(Node typeString) {
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
        return JSType.topFunction();
      case "object":
        return JSType.join(JSType.NULL, JSType.TOP_OBJECT);
      default:
        return JSType.UNKNOWN;
    }
  }

  private Map<String, JSType> calcTypeInstantiationFwd(
      Node callNode, FunctionType funType, TypeEnv typeEnv) {
    return calcTypeInstantiation(callNode, funType, typeEnv, true);
  }

  private Map<String, JSType> calcTypeInstantiationBwd(
      Node callNode, FunctionType funType, TypeEnv typeEnv) {
    return calcTypeInstantiation(callNode, funType, typeEnv, false);
  }

  /*
   * We don't use the requiredType of the context to unify with the return
   * type. There are several difficulties:
   * 1) A polymorhpic function is allowed to return ANY subtype of the
   *    requiredType, so we would need to use a heuristic to determine the type
   *    to unify with.
   * 2) It's hard to give good error messages in cases like: id('str') - 5
   *    We want an invalid-operand-type, not a not-unique-instantiation.
   *
   * We don't take the arg evaluation order into account during instantiation.
   */
  private ImmutableMap<String, JSType> calcTypeInstantiation(
      Node callNode, FunctionType funType, TypeEnv typeEnv, boolean isFwd) {
    List<String> typeParameters = funType.getTypeParameters();
    Multimap<String, JSType> typeMultimap = HashMultimap.create();
    Node arg = callNode.getChildAtIndex(1);
    int i = 0;
    while (arg != null) {
      EnvTypePair pair = isFwd ?
          analyzeExprFwd(arg, typeEnv) : analyzeExprBwd(arg, typeEnv);
      JSType unifTarget = funType.getFormalType(i);
      JSType unifSource = pair.type;
      if (!unifTarget.unifyWith(unifSource, typeParameters, typeMultimap)) {
        // Unification may fail b/c of types irrelevant to generics, eg,
        // number vs string.
        // In this case, don't warn here; we'll show invalid-arg-type later.
        HashMap<String, JSType> tmpTypeMap = Maps.newHashMap();
        for (String typeParam: typeParameters) {
          tmpTypeMap.put(typeParam, JSType.UNKNOWN);
        }
        if (unifSource.isSubtypeOf(unifTarget.substituteGenerics(tmpTypeMap))) {
          warnings.add(JSError.make(arg, FAILED_TO_UNIFY,
                  unifTarget.toString(), unifSource.toString()));
        }
      }
      arg = arg.getNext();
      typeEnv = pair.env;
      i++;
    }
    ImmutableMap.Builder builder = ImmutableMap.builder();
    for (String typeParam: typeParameters) {
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

    if ((specializedType.isTruthy() || specializedType.isFalsy()) &&
        (lhs.isTypeOf() || rhs.isTypeOf())) {
      if (lhs.isTypeOf()) {
        return analyzeSpecializedTypeof(
            lhs, rhs, expr.getType(), inEnv, specializedType);
      } else {
        return analyzeSpecializedTypeof(
            rhs, lhs, expr.getType(), inEnv, specializedType);
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
        rhsType = rhsType.removeType(JSType.NULL).removeType(JSType.UNDEFINED);
        rhsPair = analyzeExprFwd(rhs, lhsPair.env, JSType.UNKNOWN, rhsType);
      } else if (!JSType.NULL_OR_UNDEF.isSubtypeOf(rhsType)) {
        lhsType = lhsType.removeType(JSType.NULL).removeType(JSType.UNDEFINED);
        lhsPair = analyzeExprFwd(lhs, inEnv, JSType.UNKNOWN, lhsType);
        rhsPair = analyzeExprFwd(rhs, lhsPair.env);
      }
    } else if (tokenType == Token.EQ && specializedType.isFalsy() ||
        tokenType == Token.NE && specializedType.isTruthy()) {
      if (lhsType.isNullOrUndef()) {
        rhsType = rhsType.removeType(JSType.NULL).removeType(JSType.UNDEFINED);
        rhsPair = analyzeExprFwd(rhs, lhsPair.env, JSType.UNKNOWN, rhsType);
      } else if (rhsType.isNullOrUndef()) {
        lhsType = lhsType.removeType(JSType.NULL).removeType(JSType.UNDEFINED);
        lhsPair = analyzeExprFwd(lhs, inEnv, JSType.UNKNOWN, lhsType);
        rhsPair = analyzeExprFwd(rhs, lhsPair.env);
      }
    }
    rhsPair.type = JSType.BOOLEAN;
    return rhsPair;
  }

  private EnvTypePair analyzePropAccessFwd(Node receiver, String pname,
      TypeEnv inEnv, JSType requiredType, JSType specializedType) {
    QualifiedName propQname = new QualifiedName(pname);
    Node propAccessNode = receiver.getParent();
    EnvTypePair pair;
    JSType objWithProp = JSType.TOP_OBJECT.withProperty(propQname, requiredType);
    JSType recvReqType, recvSpecType, recvType;

    // First, analyze the receiver object.
    if (specializedType.isTruthy() || specializedType.isFalsy()) {
      recvReqType = JSType.UNKNOWN;
      recvSpecType = objWithProp;
    } else {
      recvReqType = recvSpecType = objWithProp;
    }
    pair = analyzeExprFwd(receiver, inEnv, recvReqType, recvSpecType);
    recvType = pair.type;
    // The warning depends on whether we are testing for the existence of a
    // property.
    boolean isNotAnObject =
        JSType.BOTTOM.equals(JSType.meet(recvType, JSType.TOP_OBJECT));
    boolean mayNotBeAnObject = !recvType.isSubtypeOf(JSType.TOP_OBJECT);
    if (isNotAnObject ||
        (!specializedType.isTruthy() && !specializedType.isFalsy() &&
            mayNotBeAnObject)) {
      warnings.add(JSError.make(
          receiver, PROPERTY_ACCESS_ON_NONOBJECT, pname, recvType.toString()));
      return new EnvTypePair(pair.env, requiredType);
    }

    // Then, analyze the property access.
    JSType resultType = recvType.getProp(propQname);
    if (!propAccessNode.getParent().isExprResult() &&
        !specializedType.isTruthy() && !specializedType.isFalsy()) {
      if (!recvType.mayHaveProp(propQname)) {
        // TODO(user): maybe don't warn if the getprop is inside a typeof,
        // see testMissingProperty8 (who relies on that for prop checking?)
        warnings.add(JSError.make(propAccessNode, TypeCheck.INEXISTENT_PROPERTY,
                pname, recvType.toString()));
      } else if (!recvType.hasProp(propQname)) {
        warnings.add(JSError.make(
            propAccessNode, NewTypeInference.POSSIBLY_INEXISTENT_PROPERTY,
            pname, recvType.toString()));
      } else if (recvType.hasInferredProp(propQname) &&
          !resultType.isSubtypeOf(requiredType) &&
          requiredType.isSubtypeOf(resultType)) {
        // Tighten the inferred type and don't warn.
        // See Token.NAME fwd for explanation about types as lower/upper bounds.
        LValueResultFwd lvr = analyzeLValueFwd(
            propAccessNode, inEnv, requiredType);
        TypeEnv updatedEnv = updateLvalueTypeInEnv(
            lvr.env, propAccessNode, lvr.ptr, requiredType);
        return new EnvTypePair(updatedEnv, requiredType);
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
    if (lvalue.isName()) {
      return envPutType(env, lvalue.getQualifiedName(), type);
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
      FunctionType summary = summaries.get(calleeScope).getFunType();
      JSType outerType = envGetType(env, freeVar);
      JSType innerType = summary.getOuterVarPrecondition(freeVar);
      if (outerType != null && JSType.meet(outerType, innerType).isBottom()) {
        warnings.add(JSError.make(callee, CROSS_SCOPE_GOTCHA,
            freeVar, outerType.toString(), innerType.toString()));
      }
    }
  }

  private TypeEnv collectTypesForFreeVarsBwd(Node callee, TypeEnv env) {
    Scope calleeScope = currentScope.getScope(callee.getQualifiedName());
    for (String freeVar : calleeScope.getOuterVars()) {
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
    JSType looseFunctionType =
        builder.addRetType(looseRetType).addLoose().buildType();
    // Unsound if the arguments and callee have interacting side effects
    EnvTypePair calleePair = analyzeExprFwd(
        callee, tmpEnv, JSType.topFunction(), looseFunctionType);
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
    JSType looseFunctionType =
        builder.addRetType(looseRetType).addLoose().buildType();
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
    Preconditions.checkArgument(requiredType != null);
    Preconditions.checkArgument(!requiredType.isBottom());
    int exprKind = expr.getType();
    switch (exprKind) {
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
        return new EnvTypePair(outEnv, scalarValueToType(exprKind));
      case Token.OBJECTLIT: {
        JSType result = JSType.TOP_OBJECT;
        TypeEnv env = outEnv;
        for (Node key = expr.getLastChild();
             key != null;
             key = expr.getChildBefore(key)) {
          QualifiedName pname =
              new QualifiedName(NodeUtil.getObjectLitKeyName(key));
          JSType reqPtype = requiredType.mayHaveProp(pname) ?
              requiredType.getProp(pname) : JSType.UNKNOWN;
          EnvTypePair pair = analyzeExprBwd(key.getLastChild(), env, reqPtype);
          result = result.withProperty(pname, pair.type);
          env = pair.env;
        }
        return new EnvTypePair(env, result);
      }
      case Token.THIS: {
        // TODO(blickly): Infer a loose type for THIS if we're in a function.
        if (!currentScope.hasThis()) {
          return new EnvTypePair(outEnv, JSType.UNKNOWN);
        }
        JSType thisType = currentScope.getDeclaredTypeOf("this");
        return new EnvTypePair(outEnv, thisType);
      }
      case Token.NAME: { // Bwd
        String varName = expr.getQualifiedName();
        if (varName.equals("undefined")) {
          return new EnvTypePair(outEnv, JSType.UNDEFINED);
        }

        JSType inferredType = envGetType(outEnv, varName);
        if (inferredType == null) { // Needed for the free vars in the tests
          inferredType = JSType.UNKNOWN;
        }

        JSType preciseType = inferredType.specialize(requiredType);
        if (currentScope.isUndeclaredFormal(varName) &&
            requiredType.hasNonScalar()) {
          preciseType = preciseType.withLoose();
        }

        if (!preciseType.isInhabitable()) {
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
      case Token.INC:
      case Token.DEC:
      case Token.BITNOT:
      case Token.POS:
      case Token.NEG: // Unary operations on numbers
        return analyzeExprBwd(expr.getFirstChild(), outEnv, JSType.NUMBER);
      case Token.TYPEOF: {
        EnvTypePair pair = analyzeExprBwd(expr.getFirstChild(), outEnv);
        pair.type = JSType.STRING;
        return pair;
      }
      case Token.INSTANCEOF: {
        TypeEnv env = analyzeExprBwd(
            expr.getLastChild(), outEnv, JSType.topFunction()).env;
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
      case Token.URSH: { // Binary operations on numbers
        Node lhs = expr.getFirstChild();
        Node rhs = expr.getLastChild();
        TypeEnv rhsEnv = analyzeExprBwd(rhs, outEnv, JSType.NUMBER).env;
        EnvTypePair pair = analyzeExprBwd(lhs, rhsEnv, JSType.NUMBER);
        pair.type = JSType.NUMBER;
        return pair;
      }
      case Token.ADD: {
        Node lhs = expr.getFirstChild();
        Node rhs = expr.getLastChild();
        EnvTypePair rhsPair = analyzeExprBwd(rhs, outEnv, JSType.NUM_OR_STR);
        EnvTypePair lhsPair =
            analyzeExprBwd(lhs, rhsPair.env, JSType.NUM_OR_STR);
        lhsPair.type = JSType.plus(lhsPair.type, rhsPair.type);
        return lhsPair;
      }
      case Token.OR:
      case Token.AND: {
        Node lhs = expr.getFirstChild();
        Node rhs = expr.getLastChild();
        EnvTypePair rhsPair = analyzeExprBwd(rhs, outEnv);
        EnvTypePair lhsPair = analyzeExprBwd(lhs, rhsPair.env);
        lhsPair.type = JSType.join(rhsPair.type, lhsPair.type);
        return lhsPair;
      }
      case Token.SHEQ:
      case Token.SHNE:
      case Token.EQ:
      case Token.NE: {
        Node lhs = expr.getFirstChild();
        Node rhs = expr.getLastChild();
        TypeEnv rhsEnv = analyzeExprBwd(rhs, outEnv).env;
        EnvTypePair pair = analyzeExprBwd(lhs, rhsEnv);
        pair.type = JSType.BOOLEAN;
        return pair;
      }
      case Token.LT:
      case Token.GT:
      case Token.LE:
      case Token.GE: {
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
      case Token.ASSIGN: { // Bwd
        Node lhs = expr.getFirstChild();
        Node rhs = expr.getLastChild();
        // Here we analyze the LHS twice:
        // Once to find out what should be removed for the slicedEnv,
        // and again to take into account the side effects of the LHS itself.
        LValueResultBwd lvalue = analyzeLValueBwd(
            lhs, outEnv, requiredType, true);
        TypeEnv slicedEnv = lvalue.env;
        JSType rhsReqType = specializeWithCorrection(lvalue.type, requiredType);
        EnvTypePair pair = analyzeExprBwd(rhs, slicedEnv, rhsReqType);
        pair.env = analyzeLValueBwd(lhs, pair.env, requiredType, true).env;
        return pair;
      }
      case Token.ASSIGN_ADD: {
        Node lhs = expr.getFirstChild();
        Node rhs = expr.getLastChild();
        JSType lhsReqType = specializeWithCorrection(
            requiredType, JSType.NUM_OR_STR);
        LValueResultBwd lvalue = analyzeLValueBwd(
            lhs, outEnv, lhsReqType, false);
        // if lhs is a string, rhs can still be a number
        JSType rhsReqType = lvalue.type.equals(JSType.NUMBER) ?
            JSType.NUMBER : JSType.NUM_OR_STR;
        EnvTypePair pair = analyzeExprBwd(rhs, outEnv, rhsReqType);
        pair.env = analyzeLValueBwd(lhs, pair.env, lhsReqType, false).env;
        return pair;
      }
      case Token.ASSIGN_BITOR:
      case Token.ASSIGN_BITXOR:
      case Token.ASSIGN_BITAND:
      case Token.ASSIGN_LSH:
      case Token.ASSIGN_RSH:
      case Token.ASSIGN_URSH:
      case Token.ASSIGN_SUB:
      case Token.ASSIGN_MUL:
      case Token.ASSIGN_DIV:
      case Token.ASSIGN_MOD: {
        Node lhs = expr.getFirstChild();
        Node rhs = expr.getLastChild();
        EnvTypePair pair = analyzeExprBwd(rhs, outEnv, JSType.NUMBER);
        LValueResultBwd lvalue =
            analyzeLValueBwd(lhs, pair.env, JSType.NUMBER, false);
        return new EnvTypePair(lvalue.env, JSType.NUMBER);
      }
      case Token.GETPROP: {
        Preconditions.checkState(
            !NodeUtil.isAssignmentOp(expr.getParent()) ||
            !NodeUtil.isLValue(expr));
        return analyzePropAccessBwd(expr.getFirstChild(),
            expr.getLastChild().getString(), outEnv, requiredType);
      }
      case Token.HOOK: {
        Node cond = expr.getFirstChild();
        Node thenBranch = cond.getNext();
        Node elseBranch = thenBranch.getNext();
        EnvTypePair thenPair =
            analyzeExprBwd(thenBranch, outEnv, requiredType);
        EnvTypePair elsePair =
            analyzeExprBwd(elseBranch, outEnv, requiredType);
        return analyzeExprBwd(cond, TypeEnv.join(thenPair.env, elsePair.env));
      }
      case Token.CALL: // Bwd
      case Token.NEW: {
        Node callee = expr.getFirstChild();
        JSType calleeTypeGeneral =
            analyzeExprBwd(callee, outEnv, JSType.topFunction()).type;
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
        if (numArgs < funType.getMinArity() ||
            numArgs > funType.getMaxArity()) {
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
        if (callee.isName() &&
            currentScope.isLocalFunDef(callee.getString())) {
          tmpEnv = collectTypesForFreeVarsBwd(callee, tmpEnv);
        }
        return new EnvTypePair(tmpEnv, funType.getReturnType());
      }
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
      case Token.GETELEM: {
        Node receiver = expr.getFirstChild();
        Node index = expr.getLastChild();
        if (index.isString()) {
          return analyzePropAccessBwd(
              receiver, index.getString(), outEnv, requiredType);
        }
        EnvTypePair pair = analyzeExprBwd(index, outEnv);
        pair = analyzeExprBwd(receiver, pair.env, JSType.TOP_OBJECT);
        pair.type = requiredType;
        return pair;
      }
      case Token.VOID: {
        EnvTypePair pair = analyzeExprBwd(expr.getFirstChild(), outEnv);
        pair.type = JSType.UNDEFINED;
        return pair;
      }
      case Token.IN: {
        Node lhs = expr.getFirstChild();
        Node rhs = expr.getLastChild();
        EnvTypePair pair = analyzeExprBwd(rhs, outEnv, JSType.TOP_OBJECT);
        pair = analyzeExprBwd(lhs, pair.env, JSType.NUM_OR_STR);
        pair.type = JSType.BOOLEAN;
        return pair;
      }
      case Token.DELPROP: {
        EnvTypePair pair = analyzeExprBwd(expr.getFirstChild(), outEnv);
        pair.type = JSType.BOOLEAN;
        return pair;
      }
      case Token.VAR: { // Can happen iff its parent is a for/in.
        Node vdecl = expr.getFirstChild();
        String name = vdecl.getQualifiedName();
        // For/in can never have rhs of its VAR
        Preconditions.checkState(!vdecl.hasChildren());
        return new EnvTypePair(
            envPutType(outEnv, name, JSType.UNKNOWN), JSType.UNKNOWN);
      }
      case Token.REGEXP:
        return new EnvTypePair(outEnv, regexpType);
      case Token.ARRAYLIT:
        TypeEnv env = outEnv;
        for (int i = expr.getChildCount() - 1; i >= 0; i--) {
          Node arrayElm = expr.getChildAtIndex(i);
          env = analyzeExprBwd(arrayElm, env).env;
        }
        return new EnvTypePair(env, arrayType);
      case Token.CAST:
        EnvTypePair pair = analyzeExprBwd(expr.getFirstChild(), outEnv);
        pair.type = symbolTable.getCastType(expr);
        return pair;
      default:
        throw new RuntimeException("BWD: Unhandled expression type: "
            + Token.name(expr.getType()) + " with parent: " + expr.getParent());
    }
  }

  private EnvTypePair analyzeCallNodeArgumentsBwd(
      Node callNode, TypeEnv outEnv) {
    TypeEnv env = outEnv;
    for (int i = callNode.getChildCount() - 1; i >= 0; i--) {
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
    if (currentScope.isKnownFunction(calleeName) &&
        !currentScope.isLocalFunDef(calleeName)) {
      Scope s = currentScope.getScope(calleeName);
      JSType expectedRetType = JSType.UNKNOWN;
      if (s.getDeclaredType().getReturnType() == null) {
        expectedRetType = requiredType;
      }
      println("Putting deferred check of function: ", calleeName,
          " with ret: ", expectedRetType);
      DeferredCheck dc = new DeferredCheck(expr, currentScope, s);
      dc.updateReturn(expectedRetType);
      deferredChecks.put(expr, dc);
    }
  }

  private EnvTypePair analyzePropAccessBwd(
      Node receiver, String pname, TypeEnv outEnv, JSType requiredType) {
    QualifiedName qname = new QualifiedName(pname);
    EnvTypePair pair = analyzeExprBwd(receiver, outEnv,
        JSType.TOP_OBJECT.withProperty(qname, requiredType));
    JSType receiverType = pair.type;
    JSType propAccessType = receiverType.mayHaveProp(qname) ?
        receiverType.getProp(qname) : requiredType;
    pair.type = propAccessType;
    return pair;
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
    JSType oldType = env.getType(varName);
    if (oldType != null && oldType.equals(type) &&
        Objects.equal(oldType.getLocation(), type.getLocation())) {
      return env;
    }
    return env.putType(varName, type);
  }

  private class LValueResultFwd {
    TypeEnv env;
    JSType type;
    JSType declType;
    QualifiedName ptr;

    LValueResultFwd(TypeEnv env, JSType type, JSType declType, QualifiedName ptr) {
      Preconditions.checkNotNull(type);
      this.env = env;
      this.type = type;
      this.declType = declType;
      this.ptr = ptr;
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
        JSType varType = envGetType(inEnv, varName);
        return new LValueResultFwd(inEnv, varType,
            currentScope.getDeclaredTypeOf(varName),
            varType.hasNonScalar() ? new QualifiedName(varName) : null);
      }
      case Token.GETPROP: {
        Node obj = expr.getFirstChild();
        QualifiedName pname = new QualifiedName(expr.getLastChild().getString());
        return analyzePropLValFwd(obj, pname, inEnv, type, insideQualifiedName);
      }
      case Token.GETELEM: {
        if (expr.getLastChild().isString()) {
          Node obj = expr.getFirstChild();
          QualifiedName pname = new QualifiedName(expr.getLastChild().getString());
          return analyzePropLValFwd(
              obj, pname, inEnv, type, insideQualifiedName);
        }
        return new LValueResultFwd(inEnv, type, null, null);
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

  private LValueResultFwd analyzePropLValFwd(Node obj, QualifiedName pname,
      TypeEnv inEnv, JSType type, boolean insideQualifiedName) {
    Preconditions.checkArgument(pname.isIdentifier());
    LValueResultFwd lvalue = analyzeLValueFwd(obj, inEnv,
        JSType.TOP_OBJECT.withProperty(pname, type), true);
    if (!lvalue.type.isSubtypeOf(JSType.TOP_OBJECT)) {
      warnings.add(JSError.make(obj, PROPERTY_ACCESS_ON_NONOBJECT,
            pname.getLeftmostName(), lvalue.type.toString()));
      return new LValueResultFwd(lvalue.env, type, null, null);
    }
    // Warn for inexistent property either on the non-top-level of a qualified
    // name, or for assignment ops that won't create a new property.
    boolean warnForInexistentProp = insideQualifiedName ||
        obj.getParent().getParent().getType() != Token.ASSIGN;
    if (warnForInexistentProp && !lvalue.type.isUnknown() &&
        !lvalue.type.mayHaveProp(pname)) {
      warnings.add(JSError.make(obj, TypeCheck.INEXISTENT_PROPERTY,
            pname.getLeftmostName(), lvalue.type.toString()));
      return new LValueResultFwd(lvalue.env, type, null, null);
    }
    JSType lvalueType = lvalue.type.mayHaveProp(pname) ?
        lvalue.type.getProp(pname) : JSType.UNKNOWN;
    return new LValueResultFwd(lvalue.env, lvalueType,
        lvalue.type.getDeclaredProp(pname),
        lvalue.ptr == null ? null : QualifiedName.join(lvalue.ptr, pname));
  }

  private class LValueResultBwd {
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
        return new LValueResultBwd(outEnv, type, null);
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
    LValueResultBwd lvalue = analyzeLValueBwd(
        obj, outEnv, JSType.TOP_OBJECT.withProperty(pname, type), false, true);
    if (lvalue.ptr != null) {
      lvalue.ptr = QualifiedName.join(lvalue.ptr, pname);
      if (doSlicing) {
        String objName = lvalue.ptr.getLeftmostName();
        QualifiedName props = lvalue.ptr.getAllButLeftmost();
        JSType objType = envGetType(lvalue.env, objName);
        JSType propDeclType = lvalue.type.getDeclaredProp(pname);
        JSType slicedObjType = propDeclType == null ?
            objType.withoutProperty(props) :
            objType.withDeclaredProperty(props, propDeclType);
        lvalue.env = envPutType(lvalue.env, objName, slicedObjType);
      }
    }
    lvalue.type = lvalue.type.mayHaveProp(pname) ?
        lvalue.type.getProp(pname) : JSType.UNKNOWN;
    return lvalue;
  }

  private static JSType specializeWithCorrection(
      JSType inferred, JSType required) {
    JSType specializedType = inferred.specialize(required);
    if (!specializedType.isInhabitable()) {
      return required;
    }
    return specializedType;
  }

  Collection<JSError> getWarnings() {
    Collection<JSError> allWarnings = Sets.newHashSet(warnings);
    allWarnings.addAll(symbolTable.getWarnings());
    return allWarnings;
  }

  TypeEnv getInitTypeEnv() {
    return getOutEnv(cfg.getEntry().getValue());
  }

  TypeEnv getFinalTypeEnv() {
    Node n = cfg.getImplicitReturn().getValue();
    if (cfg.getInEdges(n).size() == 0) {
      // This function only exits with THROWs
      TypeEnv e = new TypeEnv();
      return envPutType(e, RETVAL_ID, JSType.BOTTOM);
    } else {
      return getInEnv(n);
    }
  }

  @VisibleForTesting // Only used from tests
  JSType getFinalType(String varName) {
    return getFinalTypeEnv().getType(varName);
  }

  @VisibleForTesting // Only used from tests
  JSType getFormalType(int argpos) {
    Preconditions.checkState(summaries.size() == 1);
    return summaries.values().iterator().next()
        .getFunType().getFormalType(argpos);
  }

  @VisibleForTesting // Only used from tests
  JSType getReturnType() {
    Preconditions.checkState(summaries.size() == 1);
    return summaries.values().iterator().next().getFunType().getReturnType();
  }

  @VisibleForTesting // Only used from tests
  JSType getDeclaredType(String varName) {
    return currentScope.getDeclaredTypeOf(varName);
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
        Scope callerScope,
        Scope calleeScope) {
      this.callSite = callSite;
      this.callerScope = callerScope;
      this.calleeScope = calleeScope;
    }

    void updateReturn(JSType expectedRetType) {
      this.expectedRetType = this.expectedRetType != null ?
          JSType.meet(this.expectedRetType, expectedRetType) : expectedRetType;
    }

    void updateArgTypes(List<JSType> argTypes) {
      this.argTypes = argTypes;
    }

    private void runCheck(
        Map<Scope, JSType> summaries, Collection<JSError> warnings) {
      FunctionType fnSummary = summaries.get(this.calleeScope).getFunType();
      println(
          "Running deferred check of function: ", calleeScope.getReadableName(),
          " with FunctionSummary of: ", fnSummary, " and callsite ret: ",
          expectedRetType, " args: ", argTypes);
      if (!fnSummary.getReturnType().isSubtypeOf(this.expectedRetType)) {
        warnings.add(JSError.make(
            this.callSite, INVALID_INFERRED_RETURN_TYPE,
            this.expectedRetType.toString(),
            fnSummary.getReturnType().toString()));
      }
      int i = 0;
      Node argNode = callSite.getFirstChild().getNext();
      for (JSType argType : this.argTypes) {
        JSType formalType = fnSummary.getFormalType(i);
        Preconditions.checkState(!formalType.equals(JSType.topFunction()));
        if (argNode.isName() &&
            callerScope.isKnownFunction(argNode.getQualifiedName())) {
          String argName = argNode.getQualifiedName();
          argType = summaries.get(callerScope.getScope(argName));
        }
        if (!argType.isSubtypeOf(formalType)) {
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
          Objects.equal(expectedRetType, dc2.expectedRetType) &&
          Objects.equal(argTypes, dc2.argTypes);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(
          callSite, callerScope, calleeScope, expectedRetType, argTypes);
    }
  }

  /**
   * A persistent map from variables to abstract values (types)
   */
  static class TypeEnv { // TODO(blickly): Use structural sharing in this class.
    private final Map<String, JSType> typeMap;

    TypeEnv() {
      this.typeMap = Maps.newHashMap();
    }

    JSType getType(String n) {
      Preconditions.checkArgument(!n.contains("."));
      return typeMap.get(n);
    }

    TypeEnv putType(String n, JSType t) {
      Preconditions.checkArgument(!n.contains("."));
      TypeEnv newEnv = new TypeEnv();
      newEnv.typeMap.putAll(typeMap);
      newEnv.typeMap.put(n, t);
      return newEnv;
    }

    static TypeEnv join(TypeEnv e1, TypeEnv e2) {
      TypeEnv newEnv = new TypeEnv();
      for (String n : e1.typeMap.keySet()) {
        JSType otherType = e2.getType(n);
        newEnv.typeMap.put(n, otherType == null ?
            e1.getType(n) : JSType.join(otherType, e1.getType(n)));
      }
      for (String n : e2.typeMap.keySet()) {
        JSType otherType = e1.getType(n);
        newEnv.typeMap.put(n, otherType == null ?
            e2.getType(n) : JSType.join(otherType, e2.getType(n)));
      }
      return newEnv;
    }

    Multimap<String, String> getTaints() {
      Multimap<String, String> taints = HashMultimap.create();
      for (Map.Entry<String, JSType> entry : typeMap.entrySet()) {
        String formal = entry.getValue().getLocation();
        if (formal != null) {
          taints.put(formal, entry.getKey());
        }
      }
      return taints;
    }

    @Override
    public String toString() {
      Objects.ToStringHelper helper = Objects.toStringHelper(this.getClass());
      for (String key : typeMap.keySet()) {
        helper.add(key, getType(key));
      }
      return helper.toString();
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || !(o instanceof TypeEnv)) {
        return false;
      }
      TypeEnv other = (TypeEnv) o;
      return this.typeMap.equals(other.typeMap);
    }

    @Override
    public int hashCode() {
      return typeMap.hashCode();
    }
  }
}
