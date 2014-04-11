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

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * WarningsGuard that represents just a chain of other guards. For example we
 * could have following chain
 * 1) all warnings outside of /foo/ should be suppressed
 * 2) errors with key JSC_BAR should be marked as warning
 * 3) the rest should be reported as error
 *
 * This class is designed for such behavior.
 *
 * @author anatol@google.com (Anatol Pomazau)
 */
public class ComposeWarningsGuard extends WarningsGuard {

  private static final long serialVersionUID = 1L;

  // The order that the guards were added in.
  private final Map<WarningsGuard, Integer> orderOfAddition = Maps.newHashMap();
  private int numberOfAdds = 0;

  private final Comparator<WarningsGuard> guardComparator =
      new GuardComparator(orderOfAddition);
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
      return orderOfAddition.get(b).intValue() -
          orderOfAddition.get(a).intValue();
    }
  }

  // The order that the guards are applied in.
  private final TreeSet<WarningsGuard> guards =
      new TreeSet<>(guardComparator);

  public ComposeWarningsGuard(List<WarningsGuard> guards) {
    addGuards(guards);
  }

  public ComposeWarningsGuard(WarningsGuard... guards) {
    this(Lists.newArrayList(guards));
  }

  void addGuard(WarningsGuard guard) {
    if (guard instanceof ComposeWarningsGuard) {
      ComposeWarningsGuard composeGuard = (ComposeWarningsGuard) guard;
      if (composeGuard.demoteErrors) {
        this.demoteErrors = composeGuard.demoteErrors;
      }

      // Reverse the guards, so that they have the same order in the result.
      addGuards(Lists.newArrayList(composeGuard.guards.descendingSet()));
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
  public CheckLevel level(JSError error) {
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
  public boolean disables(DiagnosticGroup group) {
    nextSingleton:
    for (DiagnosticType type : group.getTypes()) {
      DiagnosticGroup singleton = DiagnosticGroup.forType(type);

      for (WarningsGuard guard : guards) {
        if (guard.disables(singleton)) {
          continue nextSingleton;
        } else if (guard.enables(singleton)) {
          return false;
        }
      }

      return false;
    }

    return true;
  }

  /**
   * Determines whether this guard will "elevate" the status of any disabled
   * diagnostic type in the group to a warning or an error.
   */
  @Override
  public boolean enables(DiagnosticGroup group) {
    for (WarningsGuard guard : guards) {
      if (guard.enables(group)) {
        return true;
      } else if (guard.disables(group)) {
        return false;
      }
    }

    return false;
  }

  List<WarningsGuard> getGuards() {
    return Collections.unmodifiableList(Lists.newArrayList(guards));
  }

  /**
   * Make a warnings guard that's the same as this one but with
   * all escalating guards turned down.
   */
  ComposeWarningsGuard makeEmergencyFailSafeGuard() {
    ComposeWarningsGuard safeGuard = new ComposeWarningsGuard();
    safeGuard.demoteErrors = true;
    for (WarningsGuard guard : guards.descendingSet()) {
      safeGuard.addGuard(guard);
    }
    return safeGuard;
  }

  @Override
  public String toString() {
    return Joiner.on(", ").join(guards);
  }
}
