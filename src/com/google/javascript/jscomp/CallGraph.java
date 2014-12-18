/*
 * Copyright 2010 The Closure Compiler Authors.
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
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.graph.DiGraph;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

/**
 * A pass the uses a {@link DefinitionProvider} to compute a call graph for an
 * AST.
 *
 * <p>A {@link CallGraph} connects {@link Function}s to {@link Callsite}s and
 * vice versa: each function in the graph links to the callsites it contains and
 * each callsite links to the functions it could call. Similarly, each callsite
 * links to the function that contains it and each function links to the
 * callsites that could call it.
 *
 * <p>The callgraph is not precise. That is, a callsite may indicate it can
 * call a function when in fact it does not do so in the running program.
 *
 * <p>The callgraph is also not complete: in some cases it may be unable to
 * determine some targets of a callsite. In this case,
 * Callsite.hasUnknownTarget() will return true.
 *
 * <p>The CallGraph doesn't (currently) have functions for externally defined
 * functions; however, callsites that target externs will have hasExternTarget()
 * return true.
 *
 * <p>TODO(dcc): Have CallGraph (optionally?) include functions for externs.
 *
 * @author dcc@google.com (Devin Coughlin)
 */
public class CallGraph implements CompilerPass {
  private final AbstractCompiler compiler;

  /**
   * Maps an AST node (with type Token.CALL or Token.NEW) to a Callsite object.
   */
  private final Map<Node, Callsite> callsitesByNode;

  /** Maps an AST node (with type Token.FUNCTION) to a Function object. */
  private final Map<Node, Function> functionsByNode;

  /**
   * Will the call graph support looking up the callsites that could call a
   * given function?
   */
  private final boolean computeBackwardGraph;

  /**
   * Will the call graph support looking up the functions that a given callsite
   * can call?
   */
  private final boolean computeForwardGraph;

  /** Has the CallGraph already been constructed? */
  private boolean alreadyRun = false;

  /** The name we give the main function. */
  @VisibleForTesting
  public static final String MAIN_FUNCTION_NAME = "{main}";

  /**
   *  Represents the global function. Calling getBody() on this
   *  function will yield the global script/block.
   *
   *  TODO(dcc): having a single main function is somewhat misleading. Perhaps
   *  it might be better to make CallGraph module aware and have one per
   *  module?
   */
  private Function mainFunction;

  /**
   * Creates a call graph object supporting the specified lookups.
   *
   * At least one (and possibly both) of computeForwardGraph and
   * computeBackwardGraph must be true.
   *
   * @param compiler The compiler
   * @param computeForwardGraph Should the call graph allow lookup of the target
   *        functions a given callsite could call?
   * @param computeBackwardGraph Should the call graph allow lookup of the
   *        callsites that could call a given function?
   */
  public CallGraph(AbstractCompiler compiler, boolean computeForwardGraph,
      boolean computeBackwardGraph) {
    Preconditions.checkArgument(computeForwardGraph || computeBackwardGraph);

    this.compiler = compiler;

    this.computeForwardGraph = computeForwardGraph;
    this.computeBackwardGraph = computeBackwardGraph;

    callsitesByNode = Maps.newLinkedHashMap();
    functionsByNode = Maps.newLinkedHashMap();
  }

  /**
   * Creates a call graph object support both forward and backward lookups.
   */
  public CallGraph(AbstractCompiler compiler) {
    this(compiler, true, true);
  }

  /**
   * Builds a call graph for the given externsRoot and jsRoot.
   * This method must not be called more than once per CallGraph instance.
   */
  @Override
  public void process(Node externsRoot, Node jsRoot) {
    Preconditions.checkState(!alreadyRun);

    DefinitionProvider definitionProvider = constructDefinitionProvider(externsRoot, jsRoot);

    createFunctionsAndCallsites(jsRoot, definitionProvider);

    fillInFunctionInformation(definitionProvider);

    alreadyRun = true;
  }

  /**
   * Returns the call graph Function object corresponding to the provided
   * AST Token.FUNCTION node, or null if no such object exists.
   */
  public Function getFunctionForAstNode(Node functionNode) {
    Preconditions.checkArgument(functionNode.isFunction());

    return functionsByNode.get(functionNode);
  }

  /**
   * Returns a Function object representing the "main" global function.
   */
  public Function getMainFunction() {
    return mainFunction;
  }

  /**
   * Returns a collection of all functions (including the main function)
   * in the call graph.
   */
  public Collection<Function> getAllFunctions() {
    return functionsByNode.values();
  }

  /**
   * Finds a function with the given name. Throws an exception if
   * there are no functions or multiple functions with the name. This is
   * for testing purposes only.
   */
  @VisibleForTesting
  public Function getUniqueFunctionWithName(final String desiredName) {
    Collection<Function> functions =
        Collections2.filter(getAllFunctions(),
            new Predicate<Function>() {
              @Override
              public boolean apply(Function function) {

                String functionName = function.getName();
                // Anonymous functions will have null names,
                // so it is important to handle that correctly here
                if (functionName != null && desiredName != null) {
                  return desiredName.equals(functionName);
                } else {
                  return desiredName == functionName;
                }
              }
            }
        );

    if (functions.size() == 1) {
      return functions.iterator().next();
    } else {
      throw new IllegalStateException("Found " + functions.size()
          + " functions with name " + desiredName);
    }
  }

  /**
   * Returns the call graph Callsite object corresponding to the provided
   * AST Token.CALL or Token.NEW node, or null if no such object exists.
   */
  public Callsite getCallsiteForAstNode(Node callsiteNode) {
    Preconditions.checkArgument(callsiteNode.isCall() ||
        callsiteNode.isNew());

    return callsitesByNode.get(callsiteNode);
  }

  /**
   * Returns a collection of all callsites in the call graph.
   */
  public Collection<Callsite> getAllCallsites() {
   return callsitesByNode.values();
  }

  /**
   * Creates {@link Function}s and {@link Callsite}s in a single
   * AST traversal.
   */
  private void createFunctionsAndCallsites(Node jsRoot,
      final DefinitionProvider provider) {
    // Create fake function representing global execution
    mainFunction = createFunction(jsRoot);

    NodeTraversal.traverse(compiler, jsRoot, new AbstractPostOrderCallback() {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        int nodeType = n.getType();

        if (nodeType == Token.CALL || nodeType == Token.NEW) {
          Callsite callsite = createCallsite(n);

          Node containingFunctionNode = t.getScopeRoot();

          Function containingFunction =
              functionsByNode.get(containingFunctionNode);

          if (containingFunction == null) {
            containingFunction = createFunction(containingFunctionNode);
          }
          callsite.containingFunction = containingFunction;
          containingFunction.addCallsiteInFunction(callsite);

          connectCallsiteToTargets(callsite, provider);

        } else if (n.isFunction()) {
          if (!functionsByNode.containsKey(n)) {
            createFunction(n);
          }
        }
      }
    });
  }

  /**
   * Create a Function object for given an Token.FUNCTION AST node.
   *
   * This is the bottleneck for Function creation: all Functions should
   * be created with this method.
   */
  private Function createFunction(Node functionNode) {
    Function function = new Function(functionNode);
    functionsByNode.put(functionNode, function);

    return function;
  }

  private Callsite createCallsite(Node callsiteNode) {
    Callsite callsite = new Callsite(callsiteNode);
    callsitesByNode.put(callsiteNode, callsite);

    return callsite;
  }

  /**
   * Maps a Callsite to the Function(s) it could call
   * and each Function to the Callsite(s) that could call it.
   *
   * If the definitionProvider cannot determine the target of the Callsite,
   * the Callsite's hasUnknownTarget field is set to true.
   *
   * If the definitionProvider determines that the target of the Callsite
   * could be an extern-defined function, then the Callsite's hasExternTarget
   * field is set to true.
   *
   * @param callsite The callsite for which target functions should be found
   * @param definitionProvider The DefinitionProvider used to determine
   *    targets of callsites.
   */
  private void connectCallsiteToTargets(Callsite callsite,
      DefinitionProvider definitionProvider) {
    Collection<Definition> definitions =
      lookupDefinitionsForTargetsOfCall(callsite.getAstNode(),
          definitionProvider);

    if (definitions == null) {
      callsite.hasUnknownTarget = true;
    } else {
      for (Definition definition : definitions) {
        if (definition.isExtern()) {
          callsite.hasExternTarget = true;
        } else {
          Node target = definition.getRValue();

          if (target != null && target.isFunction()) {
            Function targetFunction = functionsByNode.get(target);

            if (targetFunction == null) {
              targetFunction = createFunction(target);
            }

            if (computeForwardGraph) {
              callsite.addPossibleTarget(targetFunction);
            }

            if (computeBackwardGraph) {
              targetFunction.addCallsitePossiblyTargetingFunction(callsite);
            }
          } else {
            callsite.hasUnknownTarget = true;
          }
        }
      }
    }
  }

  /**
   * Fills in function information (such as whether the function is ever
   * aliased or whether it is exposed to .call or .apply) using the
   * definition provider.
   *
   * We do this here, rather than when connecting the callgraph, to make sure
   * that we have correct information for all functions, rather than just
   * functions that are actually called.
   */
  private void fillInFunctionInformation(DefinitionProvider provider) {
    SimpleDefinitionFinder finder = (SimpleDefinitionFinder) provider;

    for (DefinitionSite definitionSite : finder.getDefinitionSites()) {
      Definition definition = definitionSite.definition;

      Function function = lookupFunctionForDefinition(definition);

      if (function != null) {
        for (UseSite useSite : finder.getUseSites(definition)) {
          updateFunctionForUse(function, useSite.node);
        }
      }
    }
  }

  /**
   * Updates {@link Function} information (such as whether is is aliased
   * or exposed to .apply or .call based a site where the function is used.
   *
   * Note: this method may be called multiple times per Function, each time
   * with a different useNode.
   */
  private void updateFunctionForUse(Function function, Node useNode) {
    Node useParent = useNode.getParent();
    int parentType = useParent.getType();

    if ((parentType == Token.CALL || parentType == Token.NEW)
        && useParent.getFirstChild() == useNode) {
      // Regular call sites don't count as aliases
    } else if (NodeUtil.isGet(useParent)) {
      // GET{PROP,ELEM} don't count as aliases
      // but we have to check for using them in .call and .apply.

      if (useParent.isGetProp()) {
        Node gramps = useParent.getParent();
        if (NodeUtil.isFunctionObjectApply(gramps) ||
            NodeUtil.isFunctionObjectCall(gramps)) {
          function.isExposedToCallOrApply = true;
        }
      }
    } else {
      function.isAliased = true;
    }
  }

  /**
   * Returns a {@link CallGraph.Function} for the passed in {@link Definition}
   * or null if the definition isn't for a function.
   */
  private Function lookupFunctionForDefinition(Definition definition) {
    if (definition != null && !definition.isExtern()) {
      Node rValue = definition.getRValue();

      if (rValue != null && rValue.isFunction()) {
        Function function = functionsByNode.get(rValue);
        Preconditions.checkNotNull(function);

        return function;
      }
    }

    return null;
  }

  /**
   * Constructs and returns a directed graph where the nodes are functions and
   * the edges are callsites connecting callers to callees.
   *
   * It is safe to call this method on both forward and backwardly constructed
   * CallGraphs.
   */
  public DiGraph<Function, Callsite> getForwardDirectedGraph() {
    return constructDirectedGraph(true);
  }

  /**
   * Constructs and returns a directed graph where the nodes are functions and
   * the edges are callsites connecting callees to callers.
   *
   * It is safe to call this method on both forward and backwardly constructed
   * CallGraphs.
   */
  public DiGraph<Function, Callsite> getBackwardDirectedGraph() {
    return constructDirectedGraph(false);
  }

  private static void digraphConnect(DiGraph<Function, Callsite> digraph,
      Function caller,
      Callsite callsite,
      Function callee,
      boolean forward) {

    Function source;
    Function destination;

    if (forward) {
      source = caller;
      destination = callee;
    } else {
      source = callee;
      destination = caller;
    }

    digraph.connect(source, callsite, destination);
  }

  /**
   * Constructs a digraph of the call graph. If {@code forward} is true, then
   * the edges in the digraph will go from callers to callees, if false then
   * the edges will go from callees to callers.
   *
   * It is safe to run this method on both a forwardly constructed callgraph
   * and a backwardly constructed callgraph, regardless of the value of
   * {@code forward}.
   *
   * @param forward If true then the digraph will be a forward digraph.
   */
  private DiGraph<Function, Callsite> constructDirectedGraph(boolean forward) {
    DiGraph<Function, Callsite>digraph =
        LinkedDirectedGraph.createWithoutAnnotations();

    // Create nodes in call graph
    for (Function function : getAllFunctions()) {
      digraph.createNode(function);
    }

    if (computeForwardGraph) {
      // The CallGraph is a forward graph, so go from callers to callees
      for (Function caller : getAllFunctions()) {
        for (Callsite callsite : caller.getCallsitesInFunction()) {
          for (Function callee : callsite.getPossibleTargets()) {
            digraphConnect(digraph, caller, callsite, callee,
                forward);
          }
        }
      }
    } else {
      // The CallGraph is a backward graph, so go from callees to callers
      for (Function callee : getAllFunctions()) {
        for (Callsite callsite :
            callee.getCallsitesPossiblyTargetingFunction()) {

          Function caller = callsite.getContainingFunction();
          digraphConnect(digraph, caller, callsite, callee,
              forward);
        }
      }
    }

    return digraph;
  }

  /**
   * Constructs a DefinitionProvider that can be used to determine the
   * targets of callsites.
   *
   * We use SimpleNameFinder because in practice it does
   * not appear to be less precise than NameReferenceGraph and is at least an
   * order of magnitude faster on large compiles.
   */
  private DefinitionProvider constructDefinitionProvider(Node externsRoot,
        Node jsRoot) {
    SimpleDefinitionFinder defFinder = new SimpleDefinitionFinder(compiler);
    defFinder.process(externsRoot, jsRoot);
    return defFinder;
  }

  /**
   * Queries the definition provider for the definitions that could be the
   * targets of the given callsite node.
   *
   * This is complicated by the fact that NameReferenceGraph and
   * SimpleDefinitionProvider (the two definition providers we currently
   * use) differ on the types of target nodes they will analyze.
   */
  private Collection<Definition> lookupDefinitionsForTargetsOfCall(
      Node callsite, DefinitionProvider definitionProvider) {
    Preconditions.checkArgument(callsite.isCall()
        || callsite.isNew());

    Node targetExpression = callsite.getFirstChild();

    Collection<Definition> definitions =
        definitionProvider.getDefinitionsReferencedAt(targetExpression);

    if (definitions != null && !definitions.isEmpty()) {
      return definitions;
    }

    return null;
  }

  /**
   * An inner class that represents functions in the call graph.
   * A Function knows how to get its AST node and what Callsites
   * it contains.
   */
  public class Function {

    private final Node astNode;

    private boolean isAliased = false;

    private boolean isExposedToCallOrApply = false;

    private Collection<Callsite> callsitesInFunction;

    private Collection<Callsite> callsitesPossiblyTargetingFunction;

    private Function(Node functionAstNode) {
      astNode = functionAstNode;
    }

    /**
     * Does this function represent the global "main" function?
     */
    public boolean isMain() {
      return (this == CallGraph.this.mainFunction);
    }

    /**
     * Returns the underlying AST node for the function. This usually
     * has type Token.FUNCTION but in the case of the "main" function
     * will have type Token.BLOCK.
     */
    public Node getAstNode() {
      return astNode;
    }

    /**
     * Returns the AST node for the body of the function. If this function
     * is the main function, it will return the global block.
     */
    public Node getBodyNode() {
      if (isMain()) {
        return astNode;
      } else {
        return NodeUtil.getFunctionBody(astNode);
      }
    }

    /**
     * Gets the name of this function. Returns null if the function is
     * anonymous.
     */
    public String getName() {
      if (isMain()) {
        return MAIN_FUNCTION_NAME;
      } else {
        return NodeUtil.getFunctionName(astNode);
      }
    }

    /**
     * Returns the callsites in this function.
     */
    public Collection<Callsite> getCallsitesInFunction() {
      if (callsitesInFunction != null) {
        return callsitesInFunction;
      } else {
        return ImmutableList.of();
      }
    }

    private void addCallsiteInFunction(Callsite callsite) {
      if (callsitesInFunction == null) {
        callsitesInFunction = new LinkedList<>();
      }
      callsitesInFunction.add(callsite);
    }

    /**
     * Returns a collection of callsites that might call this function.
     *
     * getCallsitesPossiblyTargetingFunction() is a best effort only: the
     * collection may include callsites that do not actually call this function
     * and if this function is exported or aliased may be missing actual
     * targets.
     *
     * This method should not be called on a Function from a CallGraph
     * that was constructed with {@code computeBackwardGraph} {@code false}.
     */
    public Collection<Callsite> getCallsitesPossiblyTargetingFunction() {
      if (computeBackwardGraph) {
        if (callsitesPossiblyTargetingFunction != null) {
          return callsitesPossiblyTargetingFunction;
        } else {
          return ImmutableList.of();
        }
      } else {
        throw new UnsupportedOperationException("Cannot call " +
            "getCallsitesPossiblyTargetingFunction() on a Function "
            + "from a non-backward CallGraph");
      }
    }

    private void addCallsitePossiblyTargetingFunction(Callsite callsite) {
      Preconditions.checkState(computeBackwardGraph);
      if (callsitesPossiblyTargetingFunction == null) {
        callsitesPossiblyTargetingFunction =
            new LinkedList<>();
      }
      callsitesPossiblyTargetingFunction.add(callsite);
    }

    /**
     * Returns true if the function is aliased.
     */
    public boolean isAliased() {
      return isAliased;
    }

    /**
     * Returns true if the function is ever exposed to ".call" or ".apply".
     */
    public boolean isExposedToCallOrApply() {
      return isExposedToCallOrApply;
    }
  }

  /**
   * An inner class that represents call sites in the call graph.
   * A Callsite knows how to get its AST node, what its containing
   * Function is, and what its target Functions are.
   */
  public class Callsite {
    private final Node astNode;

    private boolean hasUnknownTarget = false;
    private boolean hasExternTarget = false;

    private Function containingFunction = null;

    private Collection<Function> possibleTargets;

    private Callsite(Node callsiteAstNode) {
      astNode = callsiteAstNode;
    }

    public Node getAstNode() {
      return astNode;
    }

    public Function getContainingFunction() {
      return containingFunction;
    }

    /**
     * Returns the possible target functions that this callsite could call.
     *
     * These targets do not include functions defined in externs. If this
     * callsite could call an extern function, then hasExternTarget() will
     * return true.
     *
     * getKnownTargets() is a best effort only: the collection may include
     * other functions that are not actual targets and (if hasUnknownTargets()
     * is true) may be missing actual targets.
     *
     * This method should not be called on a Callsite from a CallGraph
     * that was constructed with {@code computeForwardGraph} {@code false}.
     */
    public Collection<Function> getPossibleTargets() {
      if (computeForwardGraph) {
        if (possibleTargets != null) {
          return possibleTargets;
        } else {
          return ImmutableList.of();
        }
      } else {
        throw new UnsupportedOperationException("Cannot call " +
            "getPossibleTargets() on a Callsite from a non-forward " +
            "CallGraph");
      }
    }

    private void addPossibleTarget(Function target) {
      Preconditions.checkState(computeForwardGraph);

      if (possibleTargets == null) {
        possibleTargets = new LinkedList<>();
      }
      possibleTargets.add(target);
    }

    /**
     * If true, then DefinitionProvider used in callgraph construction
     * was unable find all target functions of this callsite.
     *
     * If false, then getKnownTargets() contains all the possible targets of
     * this callsite (and, perhaps, additional targets as well).
     */
    public boolean hasUnknownTarget() {
      return hasUnknownTarget;
    }

    /**
     * If true, then this callsite could target a function defined in the
     * externs. If false, then not.
     */
    public boolean hasExternTarget() {
      return hasExternTarget;
    }
  }
}
