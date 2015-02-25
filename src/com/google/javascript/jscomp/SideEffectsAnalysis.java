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

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowCallback;
import com.google.javascript.jscomp.ReferenceCollectingCallback.Reference;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceCollection;
import com.google.javascript.jscomp.VariableVisibilityAnalysis.VariableVisibility;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * A pass that analyzes side effects to determine when it is safe to move
 * code from one program point to another.
 *
 * In its current form, SideEffectsAnalysis is very incomplete; this is
 * mostly a sketch to prototype the interface and the broad strokes of
 * a possible implementation based on flow-insensitive MOD and REF sets.
 *
 * See:
 *
 * Banning, John. "An efficient way to find the side effects of procedure
 *      calls and the aliases of variables." POPL '79.
 *
 * For an introduction to MOD and REF sets.
 *
 * @author dcc@google.com (Devin Coughlin)
 */
 class SideEffectsAnalysis implements CompilerPass {

   /**
    * The type of location abstraction to use for this analysis.
    */
  enum LocationAbstractionMode {
    /** See {@link DegenerateLocationAbstraction} for details. */
    DEGENERATE,
    /** See {@link VisibilityLocationAbstraction} for details. */
    VISIBILITY_BASED
  }

  private static final Predicate<Node> NOT_FUNCTION_PREDICATE =
      new Predicate<Node>() {
    @Override
    public boolean apply(Node input) {
      return !input.isFunction();
    }
  };

  private AbstractCompiler compiler;

  /** The location abstraction used to calculate the effects of code */
  private LocationAbstraction locationAbstraction;

  /** The kind of location abstraction to use */
  private final LocationAbstractionMode locationAbstractionIdentifier;

  /**
   * Constructs a new SideEffectsAnalysis with the given location abstraction.
   *
   * @param compiler A compiler instance
   * @param locationAbstractionMode The location abstraction to use. {@code
   *    DEGENERATE} will use {@link DegenerateLocationAbstraction} while
   *    {@code VISIBILITY_BASED} will use {@link VisibilityLocationAbstraction}
   *
   */
  public SideEffectsAnalysis(AbstractCompiler compiler,
      LocationAbstractionMode locationAbstractionMode) {
    this.compiler = compiler;

    this.locationAbstractionIdentifier = locationAbstractionMode;
  }

  public SideEffectsAnalysis(AbstractCompiler compiler) {
    this(compiler, LocationAbstractionMode.DEGENERATE);
  }

  @Override
  public void process(Node externs, Node root) {
    switch(locationAbstractionIdentifier) {
      case DEGENERATE:
        locationAbstraction = new DegenerateLocationAbstraction();
        break;
      case VISIBILITY_BASED:
        locationAbstraction = createVisibilityAbstraction(externs, root);
        break;
      default:
        throw new IllegalStateException("Unrecognized location abstraction " +
            "identifier: " + locationAbstractionIdentifier);
    }

    // In the future, this method
    // will construct a callgraph and calculate side effects summaries
    // for all functions.
    // TODO(dcc): Add per-function side effects summaries.
  }

  private LocationAbstraction createVisibilityAbstraction(Node externs,
      Node root) {
    VariableVisibilityAnalysis variableVisibility =
        new VariableVisibilityAnalysis(compiler);

    variableVisibility.process(externs, root);

    VariableUseDeclarationMap variableMap =
        new VariableUseDeclarationMap(compiler);

    variableMap.mapUses(root);

   return new VisibilityLocationAbstraction(compiler,
       variableVisibility, variableMap);
  }

  /**
   * Determines whether it is safe to move code ({@code source}) across
   * an environment to another program point (immediately preceding
   * {@code destination}).
   *
   * <p>The notion of "environment" is optimization-specific, but it should
   * include any code that could be executed between the source program point
   * and the destination program point.
   *
   * {@code destination} must not be a descendant of {@code source}.
   *
   * @param source The node that would be moved
   * @param environment An environment representing the code across which
   *    the source will be moved.
   * @param destination The node before which the source would be moved
   * @return Whether it is safe to move the source to the destination
   */
  public boolean safeToMoveBefore(Node source,
      AbstractMotionEnvironment environment,
      Node destination) {
    Preconditions.checkNotNull(locationAbstraction);
    Preconditions.checkArgument(!nodeHasAncestor(destination, source));

    // It is always safe to move pure code.
    if (isPure(source)) {
      return true;
    }

    // Don't currently support interprocedural analysis
    if (nodeHasCall(source)) {
      return false;
    }

    LocationSummary sourceLocationSummary =
        locationAbstraction.calculateLocationSummary(source);

    EffectLocation sourceModSet = sourceLocationSummary.getModSet();

    // If the source has side effects, then we require that the source
    // is executed exactly as many times as the destination.
    if (!sourceModSet.isEmpty() &&
        !nodesHaveSameControlFlow(source, destination)) {
        return false;
    }

    EffectLocation sourceRefSet = sourceLocationSummary.getRefSet();

    Set<Node> environmentNodes = environment.calculateEnvironment();

    for (Node environmentNode : environmentNodes) {
      if (nodeHasCall(environmentNode)) {
        return false;
      }
    }

    LocationSummary environmentLocationSummary =
        locationAbstraction.calculateLocationSummary(environmentNodes);

    EffectLocation environmentModSet = environmentLocationSummary.getModSet();

    EffectLocation environmentRefSet = environmentLocationSummary.getRefSet();

    // If MOD(environment) intersects REF(source) then moving the
    // source across the environment could cause the source
    // to read an incorrect value.
    // If REF(environment) intersects MOD(source) then moving the
    // source across the environment could cause the environment
    // to read an incorrect value.
    // If MOD(environment) intersects MOD(source) then moving the
    // source across the environment could cause some later code that reads
    // a modified location to get an incorrect value.

    return !environmentModSet.intersectsLocation(sourceRefSet)
        && !environmentRefSet.intersectsLocation(sourceModSet)
        && !environmentModSet.intersectsLocation(sourceModSet);
  }

  /**
   * Returns true if the node is pure, that is it side effect free and does it
   * not depend on its environment?
   * @param node node to check.
   */
  private static boolean isPure(Node node) {
    // For now, we conservatively assume all code is not pure.
    // TODO(dcc): Implement isPure().
    return false;
  }

  /**
   * Returns true if the two nodes have the same control flow properties,
   * that is, is node1 be executed every time node2 is executed and vice versa?
   */
  private static boolean nodesHaveSameControlFlow(Node node1, Node node2) {
    /*
     * We conservatively approximate this with the following criteria:
     *
     * Define the "deepest control dependent block" for a node to be the
     * closest ancestor whose *parent* is a control structure and where that
     * ancestor may or may be executed depending on the parent.
     *
     * So, for example, in:
     * if (a) {
     *  b;
     * } else {
     *  c;
     * }
     *
     * a has not deepest control dependent block.
     * b's deepest control dependent block is the "then" block of the IF.
     * c's deepest control dependent block is the "else" block of the IF.
     *
     * We'll say two nodes have the same control flow if
     *
     * 1) they have the same deepest control dependent block
     * 2) that block is either a CASE (which can't have early exits) or it
     * doesn't have any early exits (e.g. breaks, continues, returns.)
     *
     */

    Node node1DeepestControlDependentBlock =
        closestControlDependentAncestor(node1);

    Node node2DeepestControlDependentBlock =
      closestControlDependentAncestor(node2);

    if (node1DeepestControlDependentBlock ==
        node2DeepestControlDependentBlock) {

      if (node2DeepestControlDependentBlock != null) {
        // CASE is complicated because we have to deal with fall through and
        // because some BREAKs are early exits and some are not.
        // For now, we don't allow movement within a CASE.
        //
        // TODO(dcc): be less conservative about movement within CASE
        if (node2DeepestControlDependentBlock.isCase()) {
          return false;
        }

        // Don't allow breaks, continues, returns in control dependent
        // block because we don't actually create a control-flow graph
        // and so don't know if early exits site between the source
        // and the destination.
        //
        // This is overly conservative as it doesn't allow, for example,
        // moving in the following case:
        // while (a) {
        //   source();
        //
        //   while(b) {
        //     break;
        //   }
        //
        //   destination();
        // }
        //
        // To fully support this kind of movement, we'll probably have to use
        // a CFG-based analysis rather than just looking at the AST.
        //
        // TODO(dcc): have nodesHaveSameControlFlow() use a CFG
        Predicate<Node> isEarlyExitPredicate = new Predicate<Node>() {
          @Override
          public boolean apply(Node input) {
            int nodeType = input.getType();

            return nodeType == Token.RETURN
                || nodeType == Token.BREAK
                || nodeType == Token.CONTINUE;
          }
        };

        return !NodeUtil.has(node2DeepestControlDependentBlock,
            isEarlyExitPredicate, NOT_FUNCTION_PREDICATE);
      } else {
        return true;
      }
    } else {
      return false;
    }
  }

  /**
   * Returns true if the number of times the child executes depends on the
   * parent.
   *
   * For example, the guard of an IF is not control dependent on the
   * IF, but its two THEN/ELSE blocks are.
   *
   * Also, the guard of WHILE and DO are control dependent on the parent
   * since the number of times it executes depends on the parent.
   */
  private static boolean isControlDependentChild(Node child) {
    Node parent = child.getParent();

    if (parent == null) {
      return false;
    }

    ArrayList<Node> siblings = Lists.newArrayList(parent.children());

    int indexOfChildInParent = siblings.indexOf(child);

    switch(parent.getType()) {
      case Token.IF:
      case Token.HOOK:
        return (indexOfChildInParent == 1 || indexOfChildInParent == 2);
      case Token.WHILE:
      case Token.DO:
        return true;
      case Token.FOR:
        // Only initializer is not control dependent
        return indexOfChildInParent != 0;
      case Token.SWITCH:
          return indexOfChildInParent > 0;
      case Token.AND:
        return true;
      case Token.OR:
        return true;
      case Token.FUNCTION:
        return true;

      default:
        return false;
    }
  }

  private static Node closestControlDependentAncestor(Node node) {
    if (isControlDependentChild(node)) {
      return node;
    }

    // Note: node is not considered one of its ancestors
    for (Node ancestor : node.getAncestors()) {
      if (isControlDependentChild(ancestor)) {
        return ancestor;
      }
    }

    return null;
  }

  /**
   * Returns true if {@code possibleAncestor} is an ancestor of{@code node}.
   * A node is not considered to be an ancestor of itself.
   */
  private static boolean nodeHasAncestor(Node node, Node possibleAncestor) {
    // Note node is not in node.getAncestors()

    for (Node ancestor : node.getAncestors()) {
      if (ancestor == possibleAncestor) {
        return true;
      }
    }

    return false;
  }

  /**
   * Returns true if a node has a CALL or a NEW descendant.
   */
  private static boolean nodeHasCall(Node node) {
    return NodeUtil.has(node, new Predicate<Node>() {
      @Override
      public boolean apply(Node input) {
        return input.isCall() || input.isNew();
      }},
      NOT_FUNCTION_PREDICATE);
  }

  /**
   * Represents an environment across which code might be moved, i.e. the set
   * of code that could be run in between the source and the destination.
   *
   * SideEffectAnalysis characterizes the code to be moved and the environment
   * in order to determine if they interact in such a way as to make the move
   * unsafe.
   *
   * Since determining the environment for an optimization can be tricky,
   * we provide several concrete subclasses that common classes of optimizations
   * may be able to reuse.
   */
  public abstract static class AbstractMotionEnvironment {

    /**
     * Calculates the set of nodes that this environment represents.
     */
    public abstract Set<Node> calculateEnvironment();
  }

  /**
   * An environment for motion within a function. Given a
   * control flow graph and a source and destination node in the control
   * flow graph, instances of this object will calculate the environment between
   * the source and destination.
   */
  public static class IntraproceduralMotionEnvironment
      extends AbstractMotionEnvironment {

    /**
     * Creates an intraprocedural motion environment.
     *
     * @param controlFlowGraph A control flow graph for function in which
     * code will be moved
     * @param cfgSource The code to be moved
     * @param cfgDestination The node immediately before which cfgSource
     * will be moved
     */
    public IntraproceduralMotionEnvironment(
        ControlFlowGraph<Node> controlFlowGraph,
        Node cfgSource,
        Node cfgDestination) {

    }

    @Override
    public Set<Node> calculateEnvironment() {
      // TODO(dcc): Implement IntraproceduralMotionEnvironment
      return null;
    }
  }

  /**
   * An environment for motion between modules. Given a
   * module graph and as well as source and destination nodes and modules,
   * instances of this object will calculate the environment between the source
   * and destination.
   */
  public static class CrossModuleMotionEnvironment
      extends AbstractMotionEnvironment {

    /**
     * Creates a cross module code motion environment.
     *
     * @param sourceNode The code to be moved
     * @param sourceModule The module for the code to be moved
     * @param destinationNode The node before which sourceNode will be inserted
     * @param destinationModule The module that destination is in
     * @param moduleGraph The module graph of the entire program
     */
    public CrossModuleMotionEnvironment(Node sourceNode,
        JSModule sourceModule,
        Node destinationNode,
        JSModule destinationModule,
        JSModuleGraph moduleGraph) {

    }

    @Override
    public Set<Node> calculateEnvironment() {
      // TODO(dcc): Implement CrossModuleMotionEnvironment
      return null;
    }
  }
    /**
     * A low-level concrete environment that allows the client to specify
     * the environment nodes directly. Clients may wish to use this environment
     * if none of the higher-level environments fit their needs.
     */
  public static class RawMotionEnvironment
      extends AbstractMotionEnvironment {
    Set<Node> environment;

    public RawMotionEnvironment(Set<Node> environment) {
      this.environment = environment;
    }

    @Override
    public Set<Node> calculateEnvironment() {
      return environment;
    }
  }

  /*
   * A combined representation for location set summaries.
   *
   * Basically, it is often easier to shuffle MOD/REF around together; this is
   * a value class for that purpose.
   */
  private static class LocationSummary {

    private EffectLocation modSet;
    private EffectLocation refSet;

    public LocationSummary(EffectLocation modSet, EffectLocation refSet) {
      this.modSet = modSet;
      this.refSet = refSet;
    }

    public EffectLocation getModSet() {
      return modSet;
    }

    public EffectLocation getRefSet() {
      return refSet;
    }
  }

  /**
   * Interface representing the notion of an effect location -- an abstract
   * location that can be modified or referenced.
   *
   * <p>Since there are an infinite number of possible concrete locations
   * in a running program, this abstraction must be imprecise (i.e. there
   * will be some distinct concrete locations that are indistinguishable
   * under the abstraction).
   *
   * <p>Different location abstractions will provide their
   * own implementations of this interface, based on the level and kind
   * of precision they provide.
   */
  private static interface EffectLocation {

    /**
     * Does the receiver's effect location intersect a given effect location?
     * That is, could any of the concrete storage locations (fields, variables,
     * etc.) represented by the receiver be contained in the set of concrete
     * storage locations represented by the given abstract effect location.
     */
    public boolean intersectsLocation(EffectLocation otherLocation);

    /**
     * Returns the result of merging the given effect location with
     * the receiver. The concrete locations represented by the result must
     * include all the concrete locations represented by each of the merged
     * locations and may also possibly include more (i.e., a join may
     * introduce a loss of precision).
     */
    public EffectLocation join(EffectLocation otherLocation);

    /**
     * Does the effect location represent any possible concrete locations?
     */
    public boolean isEmpty();
  }

  /**
   * An abstract class representing a location abstraction. (Here "abstraction"
   * means an imprecise representation of concrete side effects.)
   *
   * <p>Implementations of this class will each provide own their
   * implementation(s) of SideEffectLocation and methods to determine the side
   * effect locations of a given piece of code.
   */
  private abstract static class LocationAbstraction  {

    /** Calculates the abstraction-specific side effects
     * for the node.
     */
    abstract LocationSummary calculateLocationSummary(Node node);

    /**
     * Returns an abstraction-specific EffectLocation representing
     * no location.
     *
     * <p>The bottom location joined with any location should return
     * that location.
     */
    abstract EffectLocation getBottomLocation();

    /**
     * Calculates the abstraction-specific side effects
     * for the node.
     */
    public LocationSummary calculateLocationSummary(Set<Node> nodes) {
      EffectLocation modAccumulator = getBottomLocation();
      EffectLocation refAccumulator = getBottomLocation();

      for (Node node : nodes) {
        LocationSummary nodeLocationSummary = calculateLocationSummary(node);

        modAccumulator = modAccumulator.join(nodeLocationSummary.getModSet());
        refAccumulator = refAccumulator.join(nodeLocationSummary.getRefSet());
      }

      return new LocationSummary(modAccumulator, refAccumulator);
    }
  }
  /**
   * A very imprecise location abstraction in which there are only two abstract
   * locations: one representing all concrete locations and one for bottom
   * (no concrete locations).
   *
   * This implementation is a thin wrapper on NodeUtil.mayHaveSideEffects()
   * and NodeUtil.canBeSideEffected() -- it doesn't add any real value other
   * than to prototype the LocationAbstraction interface.
   */
  private static class DegenerateLocationAbstraction
      extends LocationAbstraction {

    private static final EffectLocation EVERY_LOCATION =
        new DegenerateEffectLocation();

    private static final EffectLocation NO_LOCATION =
        new DegenerateEffectLocation();

    @Override
    EffectLocation getBottomLocation() {
      return NO_LOCATION;
    }

    @Override
    public LocationSummary calculateLocationSummary(Node node) {
      return new LocationSummary(calculateModSet(node), calculateRefSet(node));
    }

    static EffectLocation calculateRefSet(Node node) {
      if (NodeUtil.canBeSideEffected(node)) {
        return EVERY_LOCATION;
      } else {
        return NO_LOCATION;
      }
    }

    static EffectLocation calculateModSet(Node node) {
      if (NodeUtil.mayHaveSideEffects(node)) {
        return EVERY_LOCATION;
      } else {
        return NO_LOCATION;
      }
    }

    private static class DegenerateEffectLocation implements EffectLocation {
       @Override
      public EffectLocation join(EffectLocation otherLocation) {
        if (otherLocation == EVERY_LOCATION) {
          return otherLocation;
        } else {
          return this;
        }
      }

      @Override
      public boolean intersectsLocation(EffectLocation otherLocation) {
        return this == EVERY_LOCATION && otherLocation == EVERY_LOCATION;
      }

      @Override
      public boolean isEmpty() {
        return this == NO_LOCATION;
      }
    }
  }

  /**
   * A location abstraction based on the visibility of concrete locations.
   *
   * A global variables are treated as one common location, as are all heap
   * storage locations.
   *
   * Local variables are broken up into two classes, one for truly local
   * variables and one for local variables captured by an inner scope. Each
   * of these classes has their own separate location representing the
   * variables in the class.
   *
   * Parameter variables are considered to be heap locations since they
   * can be accessed via the arguments object which itself can be aliased.
   *
   * A more precise analysis could:
   *    1) put parameters on the heap only when "arguments" is actually used
   *        in a method
   *    2) recognize that GETPROPs cannot access or modify parameters, only
   *        GETELEMs
   *
   * TODO(dcc): Don't merge parameters with the heap unless necessary.
   *
   * Internally, abstract locations are represented as integers
   * with bits set (masks) representing the storage classes in the location, so
   * that joining is bit-wise ORing and intersection is bitwise AND.
   */
  private static class VisibilityLocationAbstraction
      extends LocationAbstraction {

    /** The "bottom" location. Used to signify an empty location set */
    private static final int VISIBILITY_LOCATION_NONE = 0;

    /** The "top" location. Used to signify the set containing all locations */
    private static final int UNKNOWN_LOCATION_MASK = 0xFFFFFFFF;

    private static final int LOCAL_VARIABLE_LOCATION_MASK = 1 << 1;

    private static final int CAPTURED_LOCAL_VARIABLE_LOCATION_MASK = 1 << 2;

    private static final int GLOBAL_VARIABLE_LOCATION_MASK = 1 << 3;

    private static final int HEAP_LOCATION_MASK = 1 << 4;

    AbstractCompiler compiler;

    VariableVisibilityAnalysis variableVisibilityAnalysis;
    VariableUseDeclarationMap variableUseMap;

    private VisibilityLocationAbstraction(AbstractCompiler compiler,
        VariableVisibilityAnalysis variableVisibilityAnalysis,
        VariableUseDeclarationMap variableUseMap) {
      this.compiler = compiler;
      this.variableVisibilityAnalysis = variableVisibilityAnalysis;
      this.variableUseMap = variableUseMap;
    }

    /**
     * Calculates the MOD/REF summary for the given node.
     */
    @Override
    LocationSummary calculateLocationSummary(Node node) {
      int visibilityRefLocations = VISIBILITY_LOCATION_NONE;
      int visibilityModLocations = VISIBILITY_LOCATION_NONE;

      for (Node reference : findStorageLocationReferences(node)) {
        int effectMask;

        if (reference.isName()) {
          // Variable access
          effectMask = effectMaskForVariableReference(reference);
         } else {
          // Heap access
          effectMask = HEAP_LOCATION_MASK;
        }

        if (storageNodeIsLValue(reference)) {
          visibilityModLocations |= effectMask;
        }

        if (storageNodeIsRValue(reference)) {
          visibilityRefLocations |= effectMask;
        }
      }

      VisibilityBasedEffectLocation modSet =
          new VisibilityBasedEffectLocation(visibilityModLocations);

      VisibilityBasedEffectLocation refSet =
        new VisibilityBasedEffectLocation(visibilityRefLocations);

      return new LocationSummary(modSet, refSet);
    }

    /**
     * Returns the set of references to storage locations (both variables
     * and the heap) under {@code root}.
     */
    private Set<Node> findStorageLocationReferences(Node root) {
      final Set<Node> references = Sets.newHashSet();

      NodeTraversal.traverse(compiler, root, new AbstractShallowCallback() {
        @Override
        public void visit(NodeTraversal t, Node n, Node parent) {
          if (NodeUtil.isGet(n)
              || (n.isName() && !parent.isFunction())) {
            references.add(n);
          }
        }
      });

      return references;
    }

    /**
     * Calculates the effect mask for a variable reference.
     */
    private int effectMaskForVariableReference(Node variableReference) {
      Preconditions.checkArgument(variableReference.isName());

      int effectMask = VISIBILITY_LOCATION_NONE;

      Node declaringNameNode =
        variableUseMap.findDeclaringNameNodeForUse(variableReference);

      if (declaringNameNode != null) {
        VariableVisibility visibility =
          variableVisibilityAnalysis.getVariableVisibility(declaringNameNode);

        switch (visibility) {
          case LOCAL:
            effectMask = LOCAL_VARIABLE_LOCATION_MASK;
            break;
          case CAPTURED_LOCAL:
            effectMask = CAPTURED_LOCAL_VARIABLE_LOCATION_MASK;
            break;
          case PARAMETER:
            // Parameters are considered to be on the heap since they
            // can be accessed via the arguments object.
            effectMask = HEAP_LOCATION_MASK;
            break;
          case GLOBAL:
            effectMask = GLOBAL_VARIABLE_LOCATION_MASK;
            break;
          default:
            throw new IllegalStateException("Unrecognized variable" +
                " visibility: " + visibility);
        }
      } else {
        // Couldn't find a variable for the reference
        effectMask = UNKNOWN_LOCATION_MASK;
      }

      return effectMask;
    }

    @Override
    EffectLocation getBottomLocation() {
      return new VisibilityBasedEffectLocation(VISIBILITY_LOCATION_NONE);
    }

    /**
     * Returns true if the node is a storage node.
     *
     * Only NAMEs, GETPROPs, and GETELEMs are storage nodes.
     */
    private static boolean isStorageNode(Node node) {
      return node.isName() || NodeUtil.isGet(node);
    }

    /**
     * Return true if the storage node is an r-value.
     */
    private static boolean storageNodeIsRValue(Node node) {
      Preconditions.checkArgument(isStorageNode(node));

      // We consider all names to be r-values unless
      // LHS of Token.ASSIGN
      // LHS of of for in expression
      // Child of VAR

      Node parent = node.getParent();

      if (storageNodeIsLValue(node)) {
        // Assume l-value is NOT an r-value
        // unless it is a non-simple assign
        // or an increment/decrement

        boolean nonSimpleAssign =
          NodeUtil.isAssignmentOp(parent) && !parent.isAssign();

        return (nonSimpleAssign
            || parent.isDec()
            || parent.isInc());
      }

      return true;
    }

    /**
     * Return true if the storage node is an l-value.
     */
    private static boolean storageNodeIsLValue(Node node) {
      Preconditions.checkArgument(isStorageNode(node));
      return NodeUtil.isLValue(node);
    }

    /**
     * An abstract effect location based the visibility of the
     * concrete storage location.
     *
     * See {@link VisibilityLocationAbstraction} for deeper description
     * of this abstraction.
     *
     * The effect locations are stored as bits set on an integer, so
     * intersect, join, etc. are the standard bitwise operations.
     */
    private static class VisibilityBasedEffectLocation
        implements EffectLocation {
      int visibilityMask = VISIBILITY_LOCATION_NONE;

      public VisibilityBasedEffectLocation(int visibilityMask) {
        this.visibilityMask = visibilityMask;
      }

      @Override
      public boolean intersectsLocation(EffectLocation otherLocation) {
        Preconditions.checkArgument(otherLocation instanceof
            VisibilityBasedEffectLocation);

        int otherMask =
            ((VisibilityBasedEffectLocation) otherLocation).visibilityMask;

        return (visibilityMask & otherMask) > 0;
      }

      @Override
      public boolean isEmpty() {
        return visibilityMask == VISIBILITY_LOCATION_NONE;
      }

      @Override
      public EffectLocation join(EffectLocation otherLocation) {
        Preconditions.checkArgument(otherLocation instanceof
            VisibilityBasedEffectLocation);

        int otherMask =
            ((VisibilityBasedEffectLocation) otherLocation).visibilityMask;

        int joinedMask = visibilityMask | otherMask;

        return new VisibilityBasedEffectLocation(joinedMask);
      }
    }
  }

  /**
   * Maps NAME nodes that refer to variables to the NAME
   * nodes that declared them.
   */
  private static class VariableUseDeclarationMap {

    private AbstractCompiler compiler;

    // Maps a using name to its declaring name
    private Map<Node, Node> referencesByNameNode;

    public VariableUseDeclarationMap(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    /**
     * Adds a map from each use NAME in {@code root} to its corresponding
     * declaring name, *provided the declaration is also under root*.
     *
     * If the declaration is not under root, then the reference will
     * not be added to the map.
     */
    public void mapUses(Node root) {
      referencesByNameNode = Maps.newHashMap();

      ReferenceCollectingCallback callback =
        new ReferenceCollectingCallback(compiler,
            ReferenceCollectingCallback.DO_NOTHING_BEHAVIOR);

      NodeTraversal.traverse(compiler, root, callback);

      for (Var variable : callback.getAllSymbols()) {
        ReferenceCollection referenceCollection =
            callback.getReferences(variable);

        for (Reference reference : referenceCollection.references) {
          Node referenceNameNode = reference.getNode();

          // Note that this counts a declaration as a reference to itself
          referencesByNameNode.put(referenceNameNode, variable.getNameNode());
        }
      }
    }

    /**
     * Returns the NAME node for the declaration of the variable
     * that {@code usingNameNode} refers to, if it is in the map,
     * or {@code null} otherwise.
     */
    public Node findDeclaringNameNodeForUse(Node usingNameNode) {
      Preconditions.checkArgument(usingNameNode.isName());

      return referencesByNameNode.get(usingNameNode);
    }
  }
}
