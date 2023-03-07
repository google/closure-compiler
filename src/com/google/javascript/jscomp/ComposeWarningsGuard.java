/*
 * Copyright 2008 The Closure Compiler Authors.
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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.base.Tri;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import org.jspecify.nullness.Nullable;

/**
 * WarningsGuard that represents just a chain of other guards. For example we could have following
 * chain 1) all warnings outside of /foo/ should be suppressed 2) errors with key JSC_BAR should be
 * marked as warning 3) the rest should be reported as error
 *
 * <p>This class is designed for such behavior.
 */
public final class ComposeWarningsGuard extends WarningsGuard {

  private static final long serialVersionUID = 1L;

  // The order that the guards were added in.
  private final Map<WarningsGuard, Integer> orderOfAddition = new LinkedHashMap<>();
  private int numberOfAdds = 0;

  private final Comparator<WarningsGuard> guardComparator = new GuardComparator(orderOfAddition);
  private boolean demoteErrors = false;

  private static class GuardComparator
      implements Comparator<WarningsGuard>, Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<WarningsGuard, Integer> orderOfAddition;
    private GuardComparator(Map<WarningsGuard, Integer> orderOfAddition) {
      this.orderOfAddition = orderOfAddition;
    }

    @Override
    public int compare(WarningsGuard a, WarningsGuard b) {
      int priorityDiff = a.getPriority() - b.getPriority();
      if (priorityDiff != 0) {
        return priorityDiff;
      }

      // If the warnings guards have the same priority, the one that
      // was added last wins.
      return orderOfAddition.get(b).intValue() - orderOfAddition.get(a).intValue();
    }
  }

  // The order that the guards are applied in.
  private final TreeSet<WarningsGuard> guards = new TreeSet<>(guardComparator);

  public ComposeWarningsGuard(List<WarningsGuard> guards) {
    addGuards(guards);
  }

  public ComposeWarningsGuard(WarningsGuard... guards) {
    this(ImmutableList.copyOf(guards));
  }

  void addGuard(WarningsGuard guard) {
    if (guard instanceof ComposeWarningsGuard) {
      ComposeWarningsGuard composeGuard = (ComposeWarningsGuard) guard;
      if (composeGuard.demoteErrors) {
        this.demoteErrors = composeGuard.demoteErrors;
      }

      // Reverse the guards, so that they have the same order in the result.
      addGuards(new ArrayList<>(composeGuard.guards.descendingSet()));
    } else {
      numberOfAdds++;
      orderOfAddition.put(guard, numberOfAdds);
      guards.remove(guard);
      guards.add(guard);
    }
  }

  private void addGuards(Iterable<WarningsGuard> guards) {
    for (WarningsGuard guard : guards) {
      addGuard(guard);
    }
  }

  @Override
  public @Nullable CheckLevel level(JSError error) {
    for (WarningsGuard guard : guards) {
      CheckLevel newLevel = guard.level(error);
      if (newLevel != null) {
        if (demoteErrors && newLevel == CheckLevel.ERROR) {
          return CheckLevel.WARNING;
        }
        return newLevel;
      }
    }
    return null;
  }

  @Override
  public Tri mustRunChecks(DiagnosticGroup group) {
    // TODO(b/189635620): Merge these helper methods. Why are they asymmetric?
    boolean enable = this.enables(group);
    boolean disable = this.disables(group);

    checkState(!enable || !disable, "%s applied to %s", this, group);
    if (enable) {
      return Tri.TRUE;
    } else if (disable) {
      return Tri.FALSE;
    } else {
      return Tri.UNKNOWN;
    }
  }

  private boolean disables(DiagnosticGroup group) {
    nextSingleton:
    for (DiagnosticType type : group.getTypes()) {
      DiagnosticGroup singleton = DiagnosticGroup.forType(type);

      for (WarningsGuard guard : guards) {
        switch (guard.mustRunChecks(singleton)) {
          case TRUE:
            return false;
          case FALSE:
            continue nextSingleton;
          case UNKNOWN:
            break;
        }
      }

      return false;
    }

    return true;
  }

  private boolean enables(DiagnosticGroup group) {
    for (WarningsGuard guard : guards) {
      switch (guard.mustRunChecks(group)) {
        case TRUE:
          return true;
        case FALSE:
          return false;
        case UNKNOWN:
          break;
      }
    }

    return false;
  }

  SortedSet<WarningsGuard> getGuards() {
    return Collections.unmodifiableSortedSet(this.guards);
  }

  @Override
  public String toString() {
    return Joiner.on(", ").join(guards);
  }
}
