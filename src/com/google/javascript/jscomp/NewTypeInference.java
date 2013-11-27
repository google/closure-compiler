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
import com.google.javascript.jscomp.newtypes.TypeUtils;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Collection;
import java.util.Collections;
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
      "You thought we werent going to notice? Guess again.\n" +
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

  Set<JSError> warnings = Sets.newHashSet();

  private final AbstractCompiler compiler;
  Map<DiGraphEdge<Node, ControlFlowGraph.Branch>, TypeEnv> envs;
  Map<Scope, JSType> summaries;
  Map<Node, DeferredCheck> deferredChecks;
  ControlFlowGraph<Node> cfg;
  Node jsRoot;
  Scope currentScope;
  GlobalTypeInfo symbolTable;
  static final String RETVAL_ID = "%return";

  NewTypeInference(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.envs = Maps.newHashMap();
    this.summaries = Maps.newHashMap();
    this.deferredChecks = Maps.newHashMap();
    this.symbolTable = new GlobalTypeInfo(compiler);
  }

  @Override
  public void process(Node externs, Node root) {
    jsRoot = root;
    symbolTable.prepareAst(root);
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
    if (cfg.getOutEdges(n).size() == 0) { // true for Token.THROW
      return new TypeEnv();
    }
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
      if (currentScope.isConstructor() || currentScope.isPrototypeMethod()) {
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
        summaryType = summaryType.withProperty("prototype", JSType.TOP_OBJECT);
      }
      entryEnv = envPutType(entryEnv, fnName, summaryType);
    }
    System.out.println("Keeping env: " + entryEnv);
    envs.put(entryOutEdge, entryEnv);
  }

  // Initialize the type environments on the CFG edges before the BWD analysis.
  private void initEdgeEnvsBwd() {
    TypeEnv env = new TypeEnv();
    Set<String> varNames = currentScope.getOuterVars();
    varNames.addAll(currentScope.getFormals());
    varNames.addAll(currentScope.getLocals());
    if (currentScope.isConstructor() || currentScope.isPrototypeMethod()) {
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
        summaryType = summaryType.withProperty("prototype", JSType.TOP_OBJECT);
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
    switch (dn.getValue().getType()) {
      case Token.DO:
      case Token.WHILE:
      case Token.FOR: // Do the loop body first, then the loop follow.
        // For DO loops, we do BODY-CONDT-CONDF-FOLLOW
        // Since CONDT is currently unused, this could be optimized.
        seen.add(dn);
        workset.add(dn);
        for (DiGraphEdge<Node, ControlFlowGraph.Branch> outEdge :
            cfg.getOutEdges(dn.getValue())) {
          if (outEdge.getValue() == ControlFlowGraph.Branch.ON_TRUE) {
            buildWorksetHelper(outEdge.getDestination(), workset, seen);
          }
        }
        workset.add(dn);
        for (DiGraphEdge<Node, ControlFlowGraph.Branch> outEdge :
            cfg.getOutEdges(dn.getValue())) {
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
    System.out.println("=== Analyzing function: " +
        scope.getReadableName() + " ===");
    currentScope = scope;
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, false, false);
    cfa.process(null, scope.getRoot());
    cfg = cfa.getCfg();
    System.out.println(cfg);
    // The size is > 1 when multiple files are compiled
    // Preconditions.checkState(cfg.getEntry().getOutEdges().size() == 1);
    List<DiGraphNode<Node, ControlFlowGraph.Branch>> workset =
        Lists.newLinkedList();
    buildWorkset(cfg.getEntry(), workset);
    /* System.out.println("Workset: " + workset); */
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
      TypeEnv outEnv = getOutEnv(n);
      TypeEnv inEnv;
      System.out.println("\tBWD Statment: " + n);
      System.out.println("\t\toutEnv: " + outEnv);
      switch (n.getType()) {
        case Token.EXPR_RESULT:
          inEnv = analyzeExprBwd(n.getFirstChild(), outEnv, JSType.TOP).env;
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
            JSType requiredType = (declType == null) ?
                JSType.UNKNOWN : declType;
            inEnv = analyzeExprBwd(rhs, inEnv,
                JSType.meet(requiredType, envGetType(outEnv, varName))).env;
          }
          break;
        }
        default:
          inEnv = outEnv;
          break;
      }
      System.out.println("\t\tinEnv: " + inEnv);
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

      System.out.println("\tFWD Statment: " + n);
      System.out.println("\t\tinEnv: " + inEnv);
      boolean conditional = false;
      switch (n.getType()) {
        case Token.FUNCTION:
        case Token.BLOCK:
        case Token.EMPTY:
        case Token.SCRIPT:
          outEnv = inEnv;
          break;
        case Token.EXPR_RESULT:
          System.out.println("\tsemi " +
              Token.name(n.getFirstChild().getType()));
          outEnv = analyzeExprFwd(n.getFirstChild(), inEnv, JSType.TOP).env;
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
        case Token.WHILE: {
          conditional = true;
          Node cond = NodeUtil.getConditionExpression(n);
          for (DiGraphEdge<Node, ControlFlowGraph.Branch> outEdge :
               cfg.getOutEdges(n)) {
            JSType specializedType;
            switch (outEdge.getValue()) {
              case ON_TRUE:
                specializedType = JSType.TRUTHY;
                break;
              case ON_FALSE:
                specializedType = JSType.FALSY;
                break;
              default:
                throw new RuntimeException(
                    "A condition with an edge that is neither true nor false?");
            }
            envs.put(outEdge,
                analyzeExprFwd(cond, inEnv, JSType.TOP, specializedType).env);
          }
          break;
        }
        case Token.VAR:
          outEnv = inEnv;
          for (Node nameNode = n.getFirstChild(); nameNode != null;
               nameNode = nameNode.getNext()) {
            outEnv = processVarDeclaration(nameNode, outEnv);
          }
          break;
        case Token.TRY:
        case Token.CATCH:
        case Token.THROW:
        case Token.BREAK:
        case Token.CONTINUE:
        case Token.SWITCH:
          outEnv = inEnv;
          break;
        default:
          if (NodeUtil.isStatement(n)) {
            throw new RuntimeException("Unhandled statement type: "
                + Token.name(n.getType()));
          } else {
            outEnv = analyzeExprFwd(n, inEnv, JSType.TOP).env;
            break;
          }
      }

      if (!conditional) {
        System.out.println("\t\toutEnv: " + outEnv);
        setOutEnv(n, outEnv);
      }
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
    for (String formal : fn.getFormals()) {
      formalType = fn.getDeclaredTypeOf(formal);
      if (formalType != null) {
        if (formalIndex < reqArity) {
          builder.addReqFormal(formalType);
        } else if (formalIndex < optArity) {
          builder.addOptFormal(formalType);
        }
      } else {
        formalType = envGetType(entryEnv, formal);
        // TODO(user): fix so we get the adjusted end-of-fwd type for objs too
        if (!formalType.hasNonScalar() || formalType.getFunType() != null) {
          for (String taintedVarName : taints.get(formal)) {
            JSType taintType = envGetType(exitEnv, taintedVarName);
            formalType = JSType.meet(taintType, formalType);
          }
        }
        // TODO(blickly): Use location to infer polymorphism
        builder.addReqFormal(formalType.withLocation(null));
      }
      formalIndex++;
    }
    if (declType.hasRestFormals()) {
      builder.addRestFormals(declType.getFormalType(formalIndex));
    }

    for (String outer : fn.getOuterVars()) {
      System.out.println("Free var " + outer + " going in summary");
      JSType outerType = envGetType(entryEnv, outer);
      for (String taintedVarName : taints.get(outer)) {
        JSType taintType = envGetType(exitEnv, taintedVarName);
        outerType = JSType.meet(taintType, outerType);
      }
      // TODO(blickly): Use location to infer polymorphism
      builder.addOuterVarPrecondition(outer, outerType.withLocation(null));
    }

    builder.addClass(declType.getClassType());
    JSType declRetType = declType.getReturnType();
    JSType actualRetType = envGetType(exitEnv, RETVAL_ID);

    if (declRetType == null) {
      // TODO(blickly): Use location to infer polymorphism
      builder.addRetType(actualRetType.withLocation(null));
    } else {
      builder.addRetType(declRetType);
      if (!JSType.UNDEFINED.isSubtypeOf(declRetType) &&
          hasPathWithNoReturn(cfg)) {
        warnings.add(JSError.make(fn.getRoot(),
                CheckMissingReturn.MISSING_RETURN_STATEMENT,
                declRetType.toString()));
      }
    }
    JSType summary = builder.buildType();
    System.out.println("Function summary for " + fn.getReadableName());
    System.out.println("\t" + summary);
    summaries.put(fn, summary);
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
    if (currentScope.isLocalFunDef(varName)) {
      return inEnv;
    }
    Node rhs = nameNode.getFirstChild();
    TypeEnv outEnv = inEnv;
    JSType rhsType;
    JSType declType = currentScope.getDeclaredTypeOf(varName);
    if (rhs == null) {
      rhsType = JSType.UNDEFINED;
    } else {
      EnvTypePair pair = analyzeExprFwd(rhs, inEnv,
          declType != null ? declType : JSType.TOP);
      outEnv = pair.env;
      rhsType = pair.type;
    }
    if (!NodeUtil.isNamespaceDecl(nameNode) &&
        declType != null && rhs != null && !rhsType.isSubtypeOf(declType)) {
      warnings.add(JSError.make(
          rhs, MISTYPED_ASSIGN_RHS,
          declType.toString(), rhsType.toString()));
      // Don't flow the wrong initialization
      rhsType = declType;
    }
    return envPutType(outEnv, varName, rhsType);
  }

  private EnvTypePair analyzeExprFwd(Node expr, TypeEnv inEnv) {
    return analyzeExprFwd(expr, inEnv, JSType.TOP, JSType.TOP);
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
          String pname = NodeUtil.getObjectLitKeyName(key);
          JSType reqPtype = requiredType.mayHaveProp(pname) ?
              requiredType.getProp(pname) : JSType.TOP;
          JSType specPtype = specializedType.mayHaveProp(pname) ?
              specializedType.getProp(pname) : JSType.TOP;
          EnvTypePair pair =
              analyzeExprFwd(key.getLastChild(), env, reqPtype, specPtype);
          result = result.withProperty(pname, pair.type);
          env = pair.env;
        }
        return new EnvTypePair(env, result);
      }
      case Token.THIS: {
        Preconditions.checkState(
            currentScope.isConstructor() || currentScope.isPrototypeMethod());
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
          System.out.println(varName + "'s inferredType: " + inferredType +
              " requiredType:  " + requiredType);
          if (!inferredType.isSubtypeOf(requiredType)) {
            // Propagate incorrect type so that context catches the mismatch
            return new EnvTypePair(inEnv, inferredType);
          }

          // If preciseType is bottom, there is a condition that can't be true,
          // but that's not necessarily a type error.
          JSType preciseType = inferredType.specialize(specializedType);
          System.out.println(varName + "'s preciseType: " + preciseType);
          if (currentScope.isUndeclaredFormal(varName) &&
              requiredType.hasNonScalar()) {
            // In the bwd direction, we may infer a loose type and then join w/
            // top and forget it. That's why we also loosen types going fwd.
            preciseType = preciseType.withLoose();
          }
          return EnvTypePair.addBinding(inEnv, varName, preciseType);
        }

        System.out.println("Found global variable " + varName);
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
              analyzeExprFwd(lhs, inEnv, JSType.TOP, specializedType);
          EnvTypePair rhsPair =
              analyzeExprFwd(rhs, lhsPair.env, JSType.TOP, specializedType);
          return rhsPair;
        } else if ((specializedType.isFalsy() && exprKind == Token.AND) ||
                   (specializedType.isTruthy() && exprKind == Token.OR)) {
          EnvTypePair shortCircuitPair =
              analyzeExprFwd(lhs, inEnv, JSType.TOP, specializedType);
          JSType negatedType = specializedType.isTruthy() ?
              JSType.FALSY : JSType.TRUTHY;
          EnvTypePair lhsPair =
              analyzeExprFwd(lhs, inEnv, JSType.TOP, negatedType);
          EnvTypePair rhsPair =
              analyzeExprFwd(rhs, lhsPair.env, JSType.TOP, specializedType);
          return EnvTypePair.join(rhsPair, shortCircuitPair);
        } else {
          EnvTypePair lhsPair = analyzeExprFwd(lhs, inEnv);
          EnvTypePair rhsPair = analyzeExprFwd(rhs, lhsPair.env);
          return rhsPair;
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
          warnings.add(JSError.make(
              child, INVALID_OPERAND_TYPE,
              Token.name(expr.getType()),
              JSType.NUMBER.toString(), pair.type.toString()));
        }
        return new EnvTypePair(pair.env, JSType.NUMBER);
      }
      case Token.TYPEOF: {
        // TODO(user): recognize patterns like (typeof x === 'string')
        // to improve specializedType
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
          warnings.add(JSError.make(
              obj, INVALID_OPERAND_TYPE, "instanceof",
              "an object or a union type that includes an object",
              objPair.type.toString()));
        }
        ctorPair = analyzeExprFwd(ctor, objPair.env, JSType.topFunction());
        JSType ctorType = ctorPair.type;
        FunctionType ctorFunType = ctorType.getFunType();
        if (!ctorType.isSubtypeOf(JSType.topFunction()) ||
            !ctorFunType.isConstructor()) {
          warnings.add(JSError.make(
              ctor, INVALID_OPERAND_TYPE, "instanceof",
              "a constructor function",
              ctorType.toString()));
        }
        if (ctorFunType == null || !ctorFunType.isConstructor() ||
            (!specializedType.isTruthy() && !specializedType.isFalsy())) {
          ctorPair.type = JSType.BOOLEAN;
          return ctorPair;
        }

        // We are in a specialized context *and* we know the constructor type
        JSType instanceType = ctorFunType.getTypeOfThis();
        objPair = analyzeExprFwd(obj, inEnv, JSType.TOP,
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
        JSType numOrString = JSType.join(JSType.NUMBER, JSType.STRING);
        EnvTypePair lhsPair = analyzeExprFwd(lhs, inEnv, numOrString);
        EnvTypePair rhsPair = analyzeExprFwd(rhs, lhsPair.env, numOrString);
        JSType lhsType = lhsPair.type;
        JSType rhsType = rhsPair.type;
        if (!lhsType.isSubtypeOf(numOrString)) {
          warnings.add(JSError.make(
              lhs, INVALID_OPERAND_TYPE,
              Token.name(expr.getType()),
              numOrString.toString(), lhsType.toString()));
        }
        if (!rhsType.isSubtypeOf(numOrString)) {
          warnings.add(JSError.make(
              rhs, INVALID_OPERAND_TYPE,
              Token.name(expr.getType()),
              numOrString.toString(), rhsType.toString()));
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
          warnings.add(JSError.make(
              lhs, INVALID_OPERAND_TYPE,
              Token.name(expr.getType()),
              JSType.NUMBER.toString(), lhsPair.type.toString()));
        }
        if (!rhsPair.type.isSubtypeOf(JSType.NUMBER)) {
          warnings.add(JSError.make(
              rhs, INVALID_OPERAND_TYPE,
              Token.name(expr.getType()),
              JSType.NUMBER.toString(), rhsPair.type.toString()));
        }
        return new EnvTypePair(rhsPair.env, JSType.NUMBER);
      }
      case Token.ASSIGN: { // Fwd
        Node lhs = expr.getFirstChild();
        Node rhs = expr.getLastChild();

        LValueResult lvalue = analyzeLValueFwd(lhs, inEnv, requiredType);
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
        String opname = Token.name(expr.getType());

        LValueResult lvalue = analyzeLValueFwd(lhs, inEnv, JSType.NUMBER);
        JSType lhsType = lvalue.type;
        if (lhsType != null && !lhsType.isSubtypeOf(JSType.NUMBER)) {
          warnings.add(JSError.make(lhs, INVALID_OPERAND_TYPE, opname,
                  JSType.NUMBER.toString(), lhsType.toString()));
        }
        EnvTypePair pair = analyzeExprFwd(rhs, lvalue.env, JSType.NUMBER);
        if (!pair.type.isSubtypeOf(JSType.NUMBER)) {
          warnings.add(JSError.make(rhs, INVALID_OPERAND_TYPE, opname,
                  JSType.NUMBER.toString(), pair.type.toString()));
        }
        return new EnvTypePair(
            updateLvalueTypeInEnv(pair.env, lhs, lvalue.ptr, JSType.NUMBER),
            JSType.NUMBER);
      }
      case Token.SHEQ:
      case Token.SHNE: {
        Node lhs = expr.getFirstChild();
        Node rhs = expr.getLastChild();
        EnvTypePair lhsPair = analyzeExprFwd(lhs, inEnv);
        EnvTypePair rhsPair = analyzeExprFwd(rhs, lhsPair.env);

        if ((exprKind == Token.SHEQ && specializedType.isTruthy()) ||
            (exprKind == Token.SHNE && specializedType.isFalsy())) {
          JSType meetType = JSType.meet(lhsPair.type, rhsPair.type);
          lhsPair = analyzeExprFwd(lhs, rhsPair.env, JSType.TOP, meetType);
          rhsPair = analyzeExprFwd(rhs, lhsPair.env, JSType.TOP, meetType);
        } else if ((exprKind == Token.SHEQ && specializedType.isFalsy()) ||
            (exprKind == Token.SHNE && specializedType.isTruthy())) {
          JSType lhsType = lhsPair.type;
          JSType rhsType = rhsPair.type;
          if (lhsType.equals(JSType.NULL) ||
              lhsType.equals(JSType.UNDEFINED)) {
            rhsType = rhsType.removeType(lhsType);
          } else if (rhsType.equals(JSType.NULL) ||
                     rhsType.equals(JSType.UNDEFINED)) {
            lhsType = lhsType.removeType(rhsType);
          }
          lhsPair = analyzeExprFwd(lhs, rhsPair.env, JSType.TOP, lhsType);
          rhsPair = analyzeExprFwd(rhs, lhsPair.env, JSType.TOP, rhsType);
        }
        rhsPair.type = JSType.BOOLEAN;
        return rhsPair;
      }
      case Token.LT:
      case Token.GT:
      case Token.LE:
      case Token.GE: {
        Node lhs = expr.getFirstChild();
        Node rhs = expr.getLastChild();
        EnvTypePair lhsPair = analyzeExprFwd(lhs, inEnv);
        EnvTypePair rhsPair = analyzeExprFwd(rhs, lhsPair.env);
        // The type of either side can be specialized based on the other side
        if (lhsPair.type.isScalar() && rhsPair.type.isUnknown()) {
          rhsPair = analyzeExprFwd(rhs, lhsPair.env, lhsPair.type);
        } else if (lhsPair.type.isUnknown() && rhsPair.type.isScalar()) {
          lhsPair = analyzeExprFwd(lhs, inEnv, rhsPair.type);
          rhsPair = analyzeExprFwd(rhs, lhsPair.env, rhsPair.type);
        } else if (lhs.isQualifiedName() && lhsPair.type.isUnknown() &&
            rhs.isQualifiedName() && rhsPair.type.isUnknown()) {
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
          warnings.add(JSError.make(expr, INVALID_OPERAND_TYPE,
                  Token.name(exprKind),
                  "matching scalar types",
                  lhsType.toString() + ", " + rhsType.toString()));
        }
        return new EnvTypePair(rhsPair.env, JSType.BOOLEAN);
      }
      case Token.GETPROP:
        Preconditions.checkState(!NodeUtil.isLValue(expr));
        return analyzePropAccessFwd(
            expr.getFirstChild(), expr.getLastChild().getString(),
            inEnv, requiredType, specializedType);
      case Token.HOOK: {
        Node cond = expr.getFirstChild();
        Node thenBranch = cond.getNext();
        Node elseBranch = thenBranch.getNext();
        TypeEnv trueEnv =
            analyzeExprFwd(cond, inEnv, JSType.TOP, JSType.TRUE_TYPE).env;
        TypeEnv falseEnv =
            analyzeExprFwd(cond, inEnv, JSType.TOP, JSType.FALSE_TYPE).env;
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
          return new EnvTypePair(inEnv, requiredType);
        } else if (funType.isLoose()) {
          // TODO(blickly): analyzeLooseConstructor
          return analyzeLooseCallNodeFwd(expr, inEnv, requiredType);
        } else if (expr.isCall() && funType.isConstructor()) {
          warnings.add(JSError.make(
              expr, TypeCheck.CONSTRUCTOR_NOT_CALLABLE, funType.toString()));
          return new EnvTypePair(inEnv, requiredType);
        } else if (expr.isNew() && !funType.isConstructor()) {
          warnings.add(JSError.make(
              expr, TypeCheck.NOT_A_CONSTRUCTOR, funType.toString()));
          return new EnvTypePair(inEnv, requiredType);
        }
        int maxArity = funType.getMaxArity();
        int minArity = funType.getMinArity();
        int numArgs = expr.getChildCount() - 1;
        if (numArgs < minArity || numArgs > maxArity) {
          warnings.add(JSError.make(
              expr, TypeCheck.WRONG_ARGUMENT_COUNT, "",
              Integer.toString(numArgs), Integer.toString(minArity),
              " and at most " + maxArity));
        }
        // argTypes collects types of actuals for deferred checks.
        List<JSType> argTypes = Lists.newArrayList();
        TypeEnv tmpEnv = inEnv;
        for (int i = 0; i < numArgs; i++) {
          JSType formalType =
              (i < maxArity) ? funType.getFormalType(i) : JSType.TOP;
          if (formalType.isBottom()) {
            warnings.add(JSError.make(expr, CALL_FUNCTION_WITH_BOTTOM_FORMAL,
                  Integer.toString(i)));
            formalType = JSType.TOP;
          }
          Node arg = expr.getChildAtIndex(i + 1);
          EnvTypePair pair = analyzeExprFwd(arg, tmpEnv, formalType);
          if (!pair.type.isSubtypeOf(formalType)) {
            warnings.add(JSError.make(arg, INVALID_ARGUMENT_TYPE,
                    Integer.toString(i + 1), "",
                    formalType.toString(), pair.type.toString()));
            pair.type = JSType.UNKNOWN; // No deferred check needed.
          }
          if (i < maxArity) {
            Preconditions.checkState(!pair.type.equals(JSType.topFunction()));
            argTypes.add(pair.type);
          }
          tmpEnv = pair.env;
        }
        JSType retType = funType.getReturnType();
        if (callee.isName()) {
          String calleeName = callee.getQualifiedName();
          if (currentScope.isKnownFunction(calleeName)) {
            // Local function definitions will be type-checked more
            // exactly using their summaries, and don't need deferred checks
            if (currentScope.isLocalFunDef(calleeName)) {
              collectTypesForFreeVarsFwd(callee, tmpEnv);
            } else {
              JSType expectedRetType = requiredType;
              System.out.println("Updating deferred check with ret: " +
                  expectedRetType + " and args: " + argTypes);
              DeferredCheck dc;
              if (expr.isCall()) {
                dc = deferredChecks.get(expr);
                dc.updateReturn(expectedRetType);
              } else {
                dc = new DeferredCheck(
                    expr, currentScope, currentScope.getScope(calleeName));
                dc.updateReturn(JSType.TOP);
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
        EnvTypePair pair = analyzeExprFwd(
            expr.getFirstChild(), inEnv, JSType.TOP, specializedType.negate());
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
      case Token.EQ:
      case Token.NE:
      case Token.ARRAYLIT:
      case Token.ASSIGN_ADD:
        return new EnvTypePair(inEnv, requiredType);
      default:
        throw new RuntimeException("Unhandled expression type: "
            + Token.name(expr.getType()));
    }
  }

  private EnvTypePair analyzePropAccessFwd(Node receiver, String pname,
      TypeEnv inEnv, JSType requiredType, JSType specializedType) {
    Node propAccessNode = receiver.getParent();
    EnvTypePair pair = analyzeExprFwd(receiver, inEnv,
        JSType.TOP_OBJECT.withProperty(pname, requiredType));
    JSType objType = pair.type;
    if (!objType.isSubtypeOf(JSType.TOP_OBJECT)) {
      warnings.add(JSError.make(
          receiver, PROPERTY_ACCESS_ON_NONOBJECT, pname, objType.toString()));
      return new EnvTypePair(pair.env, requiredType);
    }
    JSType resultType = objType.getProp(pname);
    if (!specializedType.isTruthy() && !specializedType.isFalsy()) {
      if (!objType.mayHaveProp(pname)) {
        warnings.add(JSError.make(propAccessNode, TypeCheck.INEXISTENT_PROPERTY,
                pname, objType.toString()));
        // Since we warn here, return unknown to prevent further warnings.
        resultType = JSType.UNKNOWN;
      } else if (!objType.hasProp(pname)) {
        warnings.add(JSError.make(
            propAccessNode, NewTypeInference.POSSIBLY_INEXISTENT_PROPERTY,
            pname, objType.toString()));
      }
    } else if (receiver.isName() && specializedType.isTruthy()) {
      String objName = receiver.getQualifiedName();
      objType = objType.withPropertyRequired(pname);
      pair.env = envPutType(pair.env, objName, objType);
    }
    // Any potential type mismatch will be caught by the context
    return new EnvTypePair(pair.env, resultType);

  }

  private static TypeEnv updateLvalueTypeInEnv(
      TypeEnv env, Node lvalue, String qname, JSType type) {
    if (lvalue.isName()) {
      return envPutType(env, lvalue.getQualifiedName(), type);
    }
    Preconditions.checkState(lvalue.isGetProp());
    if (qname != null) {
      String objName = TypeUtils.getQnameRoot(qname);
      String props = TypeUtils.getPropPath(qname);
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
    Preconditions.checkArgument(callNode.isCall());
    Node callee = callNode.getFirstChild();
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    TypeEnv tmpEnv = inEnv;
    for (Node arg = callee.getNext(); arg != null; arg = arg.getNext()) {
      EnvTypePair pair = analyzeExprFwd(arg, tmpEnv);
      tmpEnv = pair.env;
      builder.addReqFormal(pair.type);
    }
    JSType looseRetType = retType.isTop() ? JSType.BOTTOM : retType;
    JSType looseFunctionType =
        builder.addRetType(looseRetType).addLoose().buildType();
    // Unsound if the arguments and callee have interacting side effects
    EnvTypePair calleePair = analyzeExprFwd(
        callee, tmpEnv, JSType.topFunction(), looseFunctionType);
    return new EnvTypePair(calleePair.env,
        retType.isTop() ? JSType.UNKNOWN : retType);
  }

  private EnvTypePair analyzeLooseCallNodeBwd(
      Node callNode, TypeEnv outEnv, JSType retType) {
    Preconditions.checkArgument(callNode.isCall());
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
    JSType looseRetType = retType.isTop() ? JSType.BOTTOM : retType;
    JSType looseFunctionType =
        builder.addRetType(looseRetType).addLoose().buildType();
    looseFunctionType.getFunType().checkValid();
    System.out.println("loose function type is " + looseFunctionType);
    EnvTypePair calleePair = analyzeExprBwd(callee, tmpEnv, looseFunctionType);
    return new EnvTypePair(calleePair.env,
        retType.isTop() ? JSType.UNKNOWN : retType);
  }

  private EnvTypePair analyzeExprBwd(Node expr, TypeEnv outEnv) {
    return analyzeExprBwd(expr, outEnv, JSType.TOP);
  }

  /**
   * For now, we won't emit any warnings bwd.
   */
  private EnvTypePair analyzeExprBwd(
      Node expr, TypeEnv outEnv, JSType requiredType) {
    Preconditions.checkArgument(
        requiredType != null && !requiredType.isBottom());
    int exprKind = expr.getType();
    switch (exprKind) {
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
          String pname = NodeUtil.getObjectLitKeyName(key);
          JSType reqPtype = requiredType.mayHaveProp(pname) ?
              requiredType.getProp(pname) : JSType.TOP;
          EnvTypePair pair = analyzeExprBwd(key.getLastChild(), env, reqPtype);
          result = result.withProperty(pname, pair.type);
          env = pair.env;
        }
        return new EnvTypePair(env, result);
      }
      case Token.THIS: {
        Preconditions.checkState(
            currentScope.isConstructor() || currentScope.isPrototypeMethod());
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
          inferredType = JSType.TOP;
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
          // variable is stable throught the function body.
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
        EnvTypePair rhsPair = analyzeExprBwd(rhs, outEnv, JSType.NUMBER);
        EnvTypePair lhsPair = analyzeExprBwd(lhs, rhsPair.env, JSType.NUMBER);
        return new EnvTypePair(lhsPair.env, JSType.NUMBER);
      }
      case Token.ADD: {
        Node lhs = expr.getFirstChild();
        Node rhs = expr.getLastChild();
        JSType numOrString = JSType.join(JSType.NUMBER, JSType.STRING);
        EnvTypePair rhsPair = analyzeExprBwd(rhs, outEnv, numOrString);
        EnvTypePair lhsPair = analyzeExprBwd(lhs, rhsPair.env, numOrString);
        return new EnvTypePair(
            lhsPair.env, JSType.plus(lhsPair.type, rhsPair.type));
      }
      case Token.SHEQ:
      case Token.SHNE: {
        Node lhs = expr.getFirstChild();
        Node rhs = expr.getLastChild();
        TypeEnv rhsEnv = analyzeExprBwd(rhs, outEnv).env;
        return analyzeExprBwd(lhs, rhsEnv);
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
          return new EnvTypePair(lhsPair.env, JSType.BOOLEAN);
        }
        rhsPair = analyzeExprBwd(rhs, outEnv, meetType);
        lhsPair = analyzeExprBwd(lhs, rhsPair.env, meetType);
        return new EnvTypePair(lhsPair.env, JSType.BOOLEAN);
      }
      case Token.ASSIGN: { // Bwd
        Node lhs = expr.getFirstChild();
        Node rhs = expr.getLastChild();
        // Here we analyze the LHS twice:
        // Once to find out what should be removed for the slicedEnv,
        // and again to take into account the side effects of the LHS itself.
        LValueResult lvalue = analyzeLValueBwd(lhs, outEnv, requiredType, true);
        TypeEnv slicedEnv = lvalue.env;
        JSType rhsReqType = lvalue.type != null ?
            specializeBwd(lvalue.type, requiredType) : requiredType;
        EnvTypePair rhsPair = analyzeExprBwd(rhs, slicedEnv, rhsReqType);
        rhsPair.env = analyzeLValueBwd(lhs, rhsPair.env, JSType.TOP, true).env;
        return rhsPair;
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
        LValueResult lvalue =
            analyzeLValueBwd(lhs, pair.env, JSType.NUMBER, false);
        return new EnvTypePair(lvalue.env, JSType.NUMBER);
      }
      case Token.GETPROP: {
        Preconditions.checkState(!NodeUtil.isLValue(expr));
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
          return new EnvTypePair(outEnv, requiredType);
        } else if (funType.isLoose()) {
          // TODO(blickly): analyzeLooseConstructor
          return analyzeLooseCallNodeBwd(expr, outEnv, requiredType);
        } else if (expr.isCall() && funType.isConstructor() ||
            expr.isNew() && !funType.isConstructor()) {
          return new EnvTypePair(outEnv, requiredType);
        } else if (funType.isTopFunction()) {
          return new EnvTypePair(outEnv, requiredType);
        }
        int arity = funType.getMaxArity();
        TypeEnv tmpEnv = outEnv;
        // In bwd direction, analyze arguments in reverse
        for (int i = expr.getChildCount() - 2; i >= 0; i--) {
          JSType formalType =
              i < arity ? funType.getFormalType(i) : JSType.TOP;
          // The type of a formal can be BOTTOM as the result of a join.
          // Don't use this as a requiredType.
          if (formalType.isBottom()) {
            formalType = JSType.TOP;
          }
          Node arg = expr.getChildAtIndex(i + 1);
          tmpEnv = analyzeExprBwd(arg, tmpEnv, formalType).env;
          // We don't need deferred checks for args in BWD
        }
        if (callee.isName()) {
          String calleeName = callee.getQualifiedName();
          if (currentScope.isKnownFunction(calleeName)) {
            // Local function definitions will be type-checked more
            // exactly using their summaries, and don't need deferred checks
            if (currentScope.isLocalFunDef(calleeName)) {
              tmpEnv = collectTypesForFreeVarsBwd(callee, tmpEnv);
            } else if (expr.isCall()) {
              Scope s = currentScope.getScope(calleeName);
              JSType expectedRetType = JSType.TOP;
              if (s.getDeclaredType().getReturnType() == null) {
                expectedRetType = requiredType;
              }
              System.out.println("Putting deferred check of function: " +
                  calleeName + " with ret: " + expectedRetType);
              DeferredCheck dc = new DeferredCheck(expr, currentScope, s);
              dc.updateReturn(expectedRetType);
              deferredChecks.put(expr, dc);
            }
          }
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
        EnvTypePair pair = analyzeExprBwd(receiver, outEnv, JSType.TOP_OBJECT);
        pair = analyzeExprBwd(index, pair.env);
        pair.type = requiredType;
        return pair;
      }
      case Token.EQ:
      case Token.NE:
      case Token.ARRAYLIT:
      case Token.ASSIGN_ADD:
        return new EnvTypePair(outEnv, requiredType);
      default:
        throw new RuntimeException("BWD: Unhandled expression type: "
            + Token.name(expr.getType()));
    }
  }

  private EnvTypePair analyzePropAccessBwd(
      Node receiver, String pname, TypeEnv outEnv, JSType requiredType) {
    EnvTypePair pair = analyzeExprBwd(receiver, outEnv,
        JSType.TOP_OBJECT.withProperty(pname, requiredType));
    JSType receiverType = pair.type;
    JSType propAccessType = receiverType.mayHaveProp(pname) ?
        receiverType.getProp(pname) : requiredType;
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

  private static JSType envGetType(TypeEnv env, String qName) {
    if (TypeUtils.isIdentifier(qName)) {
      return env.getType(qName);
    }
    String objName = TypeUtils.getQnameRoot(qName);
    String props = TypeUtils.getPropPath(qName);
    return env.getType(objName).getProp(props);
  }

  private static TypeEnv envPutType(TypeEnv env, String varName, JSType type) {
    Preconditions.checkArgument(TypeUtils.isIdentifier(varName));
    JSType oldType = env.getType(varName);
    if (oldType != null && oldType.equals(type) &&
        Objects.equal(oldType.getLocation(), type.getLocation())) {
      return env;
    }
    return env.putType(varName, type);
  }

  class LValueResult {
    TypeEnv env;
    JSType type;
    JSType declType;
    String ptr;

    LValueResult(TypeEnv env, JSType type, JSType declType, String ptr) {
      this.env = env;
      this.type = type;
      this.declType = declType;
      this.ptr = ptr;
    }
  }

  private LValueResult analyzeLValueFwd(
      Node expr, TypeEnv inEnv, JSType type) {
    return analyzeLValueFwd(expr, inEnv, type, false);
  }

  private LValueResult analyzeLValueFwd(
      Node expr, TypeEnv inEnv, JSType type, boolean isRecursiveCall) {
    switch (expr.getType()) {
      case Token.THIS:
      case Token.NAME: {
        String varName = expr.getQualifiedName();
        JSType varType = envGetType(inEnv, varName);
        return new LValueResult(inEnv, varType,
            currentScope.getDeclaredTypeOf(varName),
            varType.hasNonScalar() ? varName : null);
      }
      case Token.NEW: {
        EnvTypePair pair = analyzeExprFwd(expr, inEnv, type);
        return new LValueResult(pair.env, pair.type, null, null);
      }
      case Token.GETPROP: {
        Node obj = expr.getFirstChild();
        String pname = expr.getLastChild().getString();
        LValueResult lvalue = analyzeLValueFwd(obj, inEnv,
            JSType.TOP_OBJECT.withProperty(pname, type), true);
        if (!lvalue.type.isSubtypeOf(JSType.TOP_OBJECT)) {
          warnings.add(JSError.make(obj, PROPERTY_ACCESS_ON_NONOBJECT,
                pname, lvalue.type.toString()));
          return new LValueResult(lvalue.env, type, null, null);
        }
        if (isRecursiveCall && !lvalue.type.isUnknown() &&
            lvalue.type.isSubtypeOf(JSType.TOP_OBJECT) &&
            !lvalue.type.mayHaveProp(pname)) {
          warnings.add(JSError.make(obj, TypeCheck.INEXISTENT_PROPERTY,
                pname, lvalue.type.toString()));
          return new LValueResult(lvalue.env, type, null, null);
        }
        return new LValueResult(lvalue.env, lvalue.type.getProp(pname),
            lvalue.type.getDeclaredProp(pname),
            lvalue.ptr == null ? null : lvalue.ptr + "." + pname);
      }
      case Token.OBJECTLIT: {
        EnvTypePair etPair = analyzeExprFwd(expr, inEnv, type);
        return new LValueResult(etPair.env, etPair.type, null, null);
      }
    }
    throw new RuntimeException(
        "analyzeLValueFwd: unknown lhs expression @ node " + expr);
  }

  /** When {@code doSlicing} is set, remove the lvalue from the returned env */
  private LValueResult analyzeLValueBwd(
      Node expr, TypeEnv outEnv, JSType type, boolean doSlicing) {
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
        return new LValueResult(pair.env, pair.type, declType,
            pair.type.hasNonScalar() ? name : null);
      }
      case Token.NEW: {
        EnvTypePair pair = analyzeExprBwd(expr, outEnv, type);
        return new LValueResult(pair.env, pair.type, null, null);
      }
      case Token.GETPROP: {
        Node obj = expr.getFirstChild();
        String pname = expr.getLastChild().getString();
        LValueResult lvalue = analyzeLValueBwd(obj, outEnv,
                JSType.TOP_OBJECT.withProperty(pname, type), false);
        if (lvalue.ptr != null) {
          lvalue.ptr += "." + pname;
          if (doSlicing) {
            String objName = TypeUtils.getQnameRoot(lvalue.ptr);
            String props = TypeUtils.getPropPath(lvalue.ptr);
            JSType objType = envGetType(lvalue.env, objName);
            JSType propDeclType = lvalue.type.getDeclaredProp(pname);
            JSType slicedObjType = propDeclType == null ?
                objType.withoutProperty(props) :
                objType.withProperty(props, propDeclType);
            lvalue.env = envPutType(lvalue.env, objName, slicedObjType);
          }
        }
        if (lvalue.type.mayHaveProp(pname)) {
          lvalue.type = lvalue.type.getProp(pname);
        }
        return lvalue;
      }
      case Token.OBJECTLIT: {
        EnvTypePair etPair = analyzeExprBwd(expr, outEnv, type);
        return new LValueResult(etPair.env, etPair.type, null, null);
      }
    }
    throw new RuntimeException(
        "analyzeLValueBwd: unknown lhs expression @ node " + expr);
  }

  private static JSType specializeBwd(JSType inferred, JSType required) {
    JSType specializedType = inferred.specialize(required);
    if (!specializedType.isInhabitable()) {
      return required;
    }
    return specializedType;
  }

  public Collection<JSError> getWarnings() {
    Collection<JSError> allWarnings = Sets.newHashSet(warnings);
    allWarnings.addAll(symbolTable.getWarnings());
    return allWarnings;
  }

  public TypeEnv getInitTypeEnv() {
    return getOutEnv(cfg.getEntry().getValue());
  }

  public TypeEnv getFinalTypeEnv() {
    return getInEnv(cfg.getImplicitReturn().getValue());
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
      Preconditions.checkState(this.argTypes == null);
      this.argTypes = argTypes;
    }

    private void runCheck(
        Map<Scope, JSType> summaries, Collection<JSError> warnings) {
      FunctionType fnSummary = summaries.get(this.calleeScope).getFunType();
      System.out.println(
          "Running deferred check of function: " +
          calleeScope.getReadableName() +
          " with FunctionSummary of: " + fnSummary +
          " and callsite ret: " + expectedRetType + " args: " + argTypes);
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
      Preconditions.checkArgument(TypeUtils.isIdentifier(n));
      return typeMap.get(n);
    }

    TypeEnv putType(String n, JSType t) {
      Preconditions.checkArgument(TypeUtils.isIdentifier(n));
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
