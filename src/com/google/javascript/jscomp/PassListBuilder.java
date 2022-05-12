/*
 * Copyright 2022 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

/**
 * This class exists to funnel the list of passes through a central mechanism to ensure that the
 * PassFactory can define and enforce conditions that much be met in order for the pass they create
 * to be added to the compilation process.
 */
public final class PassListBuilder {
  private final CompilerOptions options;
  private final List<PassFactory> passes = new ArrayList<>();

  public PassListBuilder(CompilerOptions options) {
    this.options = options;
  }

  public ImmutableList<PassFactory> build() {
    return ImmutableList.copyOf(passes);
  }

  public void addAll(PassListBuilder other) {
    // Don't bother to check conditions here as we assume that they were
    // checked when initially added to the other PassListBuilder.
    passes.addAll(other.build());
  }

  /** Add only if the PassFactory condition evaluates to true. */
  public void maybeAdd(PassFactory factory) {
    if (factory.getCondition().apply(options)) {
      passes.add(factory);
    }
  }

  /**
   * Insert the given pass factory before the factory of the given name. Throws if the specified
   * pass is not present
   */
  public void addBefore(PassFactory factory, String passName) {
    passes.add(findIndexByName(passName), factory);
  }

  /**
   * Insert the given pass factory before the factory of the given name. Throws if the specified
   * pass is not present
   */
  public void addAfter(PassFactory factory, String passName) {
    passes.add(findIndexByName(passName) + 1, factory);
  }

  /**
   * Returns the list of pass up to and including the named pass, otherwise return an empty set of
   * passes.
   *
   * <p>Do not add new uses of this method.
   *
   * @deprecated replace uses of this method with a more precise definition of a pass configuration.
   */
  @Deprecated
  public void addAllUpTo(PassListBuilder other, String passName) {
    ImmutableList<PassFactory> otherPasses = other.build();

    int index = -1;
    for (int i = 0; i < otherPasses.size(); i++) {
      if (passName.equals(otherPasses.get(i).getName())) {
        index = i;
        break;
      }
    }
    // Return every check up to and including the "checkConformance" check. Return empty list if
    // "checkConformance" not found. This list may include some unnecessary checks that run before
    // conformance. However, there's no other reliable way to find a list of all the passes
    // that conformance depends on.
    passes.addAll(otherPasses.subList(0, index + 1));
  }

  public PassFactory findByName(String name) {
    for (PassFactory pass : passes) {
      if (pass.getName().equals(name)) {
        return pass;
      }
    }
    throw new IllegalArgumentException("No factory named '" + name + "' in the factory list");
  }

  /**
   * Remove a pass with the specified name.
   *
   * @deprecated Do not add new uses to the method. This method exists only for migration purposes.
   *     Removing passes piecemeal is very fragile.
   */
  @Deprecated
  public void removeByName(String name) {
    for (int i = 0; i < passes.size(); i++) {
      if (passes.get(i).getName().equals(name)) {
        passes.remove(i);
        return;
      }
    }
  }

  /** Throws an exception if no pass with the given name exists. */
  private int findIndexByName(String name) {
    for (int i = 0; i < passes.size(); i++) {
      if (passes.get(i).getName().equals(name)) {
        return i;
      }
    }

    throw new IllegalArgumentException("No factory named '" + name + "' in the factory list");
  }

  public boolean contains(PassFactory factory) {
    return passes.contains(factory);
  }

  /** Verify that all the passes are one-time passes. */
  public void assertAllOneTimePasses() {
    for (PassFactory pass : passes) {
      checkState(!pass.isRunInFixedPointLoop());
    }
  }

  /** Verify that all the passes are multi-run passes. */
  public void assertAllLoopablePasses() {
    for (PassFactory pass : passes) {
      checkState(pass.isRunInFixedPointLoop());
    }
  }

  /** Asserts that if both PassFactory are present, pass1 is ordered before pass2. */
  public void assertPassOrder(PassFactory pass1, PassFactory pass2, String msg) {
    int pass1Index = passes.indexOf(pass1);
    int pass2Index = passes.indexOf(pass2);
    if (pass1Index != -1 && pass2Index != -1) {
      checkState(pass1Index < pass2Index, msg);
    }
  }
}
