/*
 * Copyright 2010 Google Inc.
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
import com.google.javascript.rhino.Node;

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
 * Banning, John. “An efficient way to find the side effects of procedure
 *      calls and the aliases of variables.” POPL ‘79.
 * 
 * For an introduction to MOD and REF sets.
 * 
 * @author dcc@google.com (Devin Coughlin)
 */
public class SideEffectsAnalysis implements CompilerPass {

  /** The location abstraction used to calculate the effects of code */
  private LocationAbstraction locationAbstraction;
   
  @Override
  public void process(Node externs, Node root) {
    locationAbstraction = new DegenerateLocationAbstraction();
    
    // Currently performs no analysis. In the future, this method
    // will construct a callgraph and calculate side effects summaries
    // for all functions.
    // TODO(dcc): Add per-function side effects summaries.
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

    if (!environmentModSet.intersectsLocation(sourceRefSet)
        && !environmentRefSet.intersectsLocation(sourceModSet)
        && !environmentModSet.intersectsLocation(sourceModSet)) {
      return true;
    } 

    return false;
  }
  
  /**
   * Is the node pure? That is, is it side effect free and does it not depend
   * on its environment?
   */
  private boolean isPure(Node node) {
    // For now, we conservatively assume all code is not pure.
    // TODO(dcc): Implement isPure().
    return false;
  }
  
  /**
   * Do the two nodes have the same control flow properties? That is,
   * will node1 be executed every time node2 is executed and vice versa?
   */
  private boolean nodesHaveSameControlFlow(Node node1, Node node2) {
    // For now, we conservatively assume not.
    // TODO(dcc): Implement nodesHaveSameControlFlow().
    return false;
  }
  
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
     * Calculates the set of nodes that this environment represnts.
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
     * if none  of the higher-level environments fit their needs.
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
        new DenegenerateEffectLocation();
    
    private static final EffectLocation NO_LOCATION =
        new DenegenerateEffectLocation();
    
    @Override
    EffectLocation getBottomLocation() {
      return NO_LOCATION;
    }
    
    @Override
    public LocationSummary calculateLocationSummary(Node node) {
      return new LocationSummary(calculateModSet(node), calculateRefSet(node));
    }
    
    EffectLocation calculateRefSet(Node node) {
      if (NodeUtil.canBeSideEffected(node)) {
        return EVERY_LOCATION;
      } else {
        return NO_LOCATION;
      }
    }
    
    EffectLocation calculateModSet(Node node) {
      if (NodeUtil.mayHaveSideEffects(node)) {
        return EVERY_LOCATION;
      } else {
        return NO_LOCATION;
      }
    }
    
    private static class DenegenerateEffectLocation implements EffectLocation {
       public EffectLocation join(EffectLocation otherLocation) {
        if (otherLocation == EVERY_LOCATION) {
          return otherLocation;
        } else {
          return this;
        }
      }
      
      public boolean intersectsLocation(EffectLocation otherLocation) {
        return this == EVERY_LOCATION && otherLocation == EVERY_LOCATION;
      }
      
      public boolean isEmpty() {
        return this == NO_LOCATION;
      }
    }
  }
}
