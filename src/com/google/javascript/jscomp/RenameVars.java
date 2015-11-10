/*
 * Copyright 2004 The Closure Compiler Authors.
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

import static com.google.common.base.Strings.nullToEmpty;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.rhino.Node;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.annotation.Nullable;

/**
 * RenameVars renames all the variables names into short names, to reduce code
 * size and also to obfuscate the code.
 *
 */
final class RenameVars implements CompilerPass {

  /**
   * Limit on number of locals in a scope for temporary local renaming
   * when {@code preferStableNames} is true.
   */
  private static final int MAX_LOCALS_IN_SCOPE_TO_TEMP_RENAME = 1000;

  private final AbstractCompiler compiler;

  /** List of global NAME nodes */
  private final ArrayList<Node> globalNameNodes = new ArrayList<>();

  /** List of local NAME nodes */
  private final ArrayList<Node> localNameNodes = new ArrayList<>();

  /**
   * Maps a name node to its pseudo name, null if we are not generating so
   * there will be no overhead unless we are debugging.
   */
  private final Map<Node, String> pseudoNameMap;

  /** Set of extern variable names */
  private final Set<String> externNames = new HashSet<>();

  /** Set of reserved variable names */
  private final Set<String> reservedNames;

  /** The renaming map */
  private final Map<String, String> renameMap = new HashMap<>();

  /** The previously used rename map. */
  private final VariableMap prevUsedRenameMap;

  /** The global name prefix */
  private final String prefix;

  /** Counter for each assignment */
  private int assignmentCount = 0;

  /** Logs all name assignments */
  private StringBuilder assignmentLog;

  // Logic for bleeding functions, where the name leaks into the outer
  // scope on IE but not on other browsers.
  private final Set<Var> localBleedingFunctions = new HashSet<>();
  private final ArrayListMultimap<Scope, Var> localBleedingFunctionsPerScope =
      ArrayListMultimap.create();

  class Assignment {
    final String oldName;
    final int orderOfOccurrence;
    String newName;
    int count; // Number of times this is referenced

    Assignment(String name) {
      this.oldName = name;
      this.newName = null;
      this.count = 0;

      // Represents the order at which a symbol appears in the source.
      this.orderOfOccurrence = assignmentCount++;
    }

    /**
     * Assigns the new name.
     */
    void setNewName(String newName) {
      Preconditions.checkState(this.newName == null);
      this.newName = newName;
    }
  }

  /** Maps an old name to a new name assignment */
  private final Map<String, Assignment> assignments =
      new HashMap<>();

  /** Whether renaming should apply to local variables only. */
  private final boolean localRenamingOnly;

  /**
   * Whether function expression names should be preserved. Typically, for
   * debugging purposes.
   *
   * @see NameAnonymousFunctions
   */
  private final boolean preserveFunctionExpressionNames;

  private final boolean shouldShadow;

  private final boolean preferStableNames;

  /** Characters that shouldn't be used in variable names. */
  private final char[] reservedCharacters;

  /** A prefix to distinguish temporary local names from global names */
  // TODO(user): No longer needs to be public when shadowing doesn't use it.
  public static final String LOCAL_VAR_PREFIX = "L ";

  // Shared name generator
  private final NameGenerator nameGenerator;

  /*
   * nameGenerator is a shared NameGenerator that this instance can use;
   * the instance may reset or reconfigure it, so the caller should
   * not expect any state to be preserved.
   */
  RenameVars(AbstractCompiler compiler, String prefix,
      boolean localRenamingOnly, boolean preserveFunctionExpressionNames,
      boolean generatePseudoNames, boolean shouldShadow,
      boolean preferStableNames, VariableMap prevUsedRenameMap,
      @Nullable char[] reservedCharacters,
      @Nullable Set<String> reservedNames,
      NameGenerator nameGenerator) {
    this.compiler = compiler;
    this.prefix = nullToEmpty(prefix);
    this.localRenamingOnly = localRenamingOnly;
    this.preserveFunctionExpressionNames = preserveFunctionExpressionNames;
    if (generatePseudoNames) {
      this.pseudoNameMap = new HashMap<>();
    } else {
      this.pseudoNameMap = null;
    }
    this.prevUsedRenameMap = prevUsedRenameMap;
    this.reservedCharacters = reservedCharacters;
    this.shouldShadow = shouldShadow;
    this.preferStableNames = preferStableNames;
    if (reservedNames == null) {
      this.reservedNames = new HashSet<>();
    } else {
      this.reservedNames = new HashSet<>(reservedNames);
    }
    this.nameGenerator = nameGenerator;
  }

  /**
   * Iterate through the nodes, collect all the NAME nodes that need to be
   * renamed, and count how many times each variable name is referenced.
   *
   * There are 2 passes:
   * - externs: keep track of the global vars in the externNames_ map.
   * - source: keep track of all name references in globalNameNodes_, and
   *   localNameNodes_.
   *
   * To get shorter local variable renaming, we rename local variables to a
   * temporary name "LOCAL_VAR_PREFIX + index" where index is the index of the
   * variable declared in the local scope stack.
   * e.g.
   * Foo(fa, fb) {
   *   var c = function(d, e) { return fa; }
   * }
   * The indexes are: fa:0, fb:1, c:2, d:3, e:4
   *
   * In that way, local variable names are reused in each global function.
   * e.g. the final code might look like
   * function x(a,b) { ... }
   * function y(a,b,c) { ... }
   */
  class ProcessVars extends AbstractPostOrderCallback
      implements ScopedCallback {
    private final boolean isExternsPass_;

    ProcessVars(boolean isExterns) {
      isExternsPass_ = isExterns;
    }

    @Override
    public void enterScope(NodeTraversal t) {
      if (t.inGlobalHoistScope() ||
          !shouldTemporarilyRenameLocalsInScope(t.getScope())) {
        return;
      }
      Scope scope = t.getScope();
      Iterator<Var> it = scope.getVars();
      while (it.hasNext()) {
        Var current = it.next();
        if (current.isBleedingFunction()) {
          localBleedingFunctions.add(current);
          localBleedingFunctionsPerScope.put(
              scope.getParent(), current);
        }
      }
    }

    @Override
    public void exitScope(NodeTraversal t) {}

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!n.isName()) {
        return;
      }

      String name = n.getString();

      // Ignore anonymous functions
      if (name.isEmpty()) {
        return;
      }

      // Is this local or Global?
      // Bleeding functions should be treated as part of their outer
      // scope, because IE has bugs in how it handles bleeding
      // functions.
      Var var = t.getScope().getVar(name);
      boolean local = (var != null) && var.isLocal() &&
          (!var.scope.getParent().isGlobal() ||
           !var.isBleedingFunction());

      // Never rename references to the arguments array
      if (var != null && var.isArguments()) {
        reservedNames.add(name);
        return;
      }

      // Are we renaming global variables?
      if (!local && localRenamingOnly) {
        reservedNames.add(name);
        return;
      }

      // Are we renaming function expression names?
      if (preserveFunctionExpressionNames && var != null
          && NodeUtil.isFunctionExpression(var.getParentNode())) {
        reservedNames.add(name);
        return;
      }

      // Check if we can rename this.
      if (!okToRenameVar(name, local)) {
        if (local) {
          // Blindly de-uniquify for the Prototype library for issue 103.
          String newName = MakeDeclaredNamesUnique.ContextualRenameInverter
              .getOriginalName(name);
          if (!newName.equals(name)) {
            n.setString(newName);
          }
        }
        return;
      }

      if (isExternsPass_) {
        // Keep track of extern globals.
        if (!local) {
          externNames.add(name);
        }
        return;
      }

      if (pseudoNameMap != null) {
        recordPseudoName(n);
      }

      if (local && shouldTemporarilyRenameLocalsInScope(var.getScope())) {
        // Give local variables a temporary name based on the
        // variable's index in the scope to enable name reuse across
        // locals in independent scopes.
        String tempName = LOCAL_VAR_PREFIX + getLocalVarIndex(var);
        incCount(tempName);
        localNameNodes.add(n);
        n.setString(tempName);
      } else if (var != null) { // Not an extern
        // If it's global, increment global count
        incCount(name);
        globalNameNodes.add(n);
      }
    }

    // Increment count of an assignment
    void incCount(String name) {
      Assignment s = assignments.get(name);
      if (s == null) {
        s = new Assignment(name);
        assignments.put(name, s);
      }
      s.count++;
    }
  }

  /**
   * Sorts Assignment objects by their count, breaking ties by their order of
   * occurrence in the source to ensure a deterministic total ordering.
   */
  private static final Comparator<Assignment> FREQUENCY_COMPARATOR =
      new Comparator<Assignment>() {
    @Override
    public int compare(Assignment a1, Assignment a2) {
      if (a1.count != a2.count) {
        return a2.count - a1.count;
      }
      // Break a tie using the order in which the variable first appears in
      // the source.
      return ORDER_OF_OCCURRENCE_COMPARATOR.compare(a1, a2);
    }
  };

  /**
   * Sorts Assignment objects by the order the variable name first appears in
   * the source.
   */
  private static final Comparator<Assignment> ORDER_OF_OCCURRENCE_COMPARATOR =
      new Comparator<Assignment>() {
        @Override
        public int compare(Assignment a1, Assignment a2) {
          return a1.orderOfOccurrence - a2.orderOfOccurrence;
        }
      };

  @Override
  public void process(Node externs, Node root) {
    assignmentLog = new StringBuilder();

    // Do variable reference counting.
    NodeTraversal.traverseEs6(compiler, externs, new ProcessVars(true));
    NodeTraversal.traverseEs6(compiler, root, new ProcessVars(false));

    // Make sure that new names don't overlap with extern names.
    reservedNames.addAll(externNames);

    // Rename vars, sorted by frequency of occurrence to minimize code size.
    SortedSet<Assignment> varsByFrequency =
        new TreeSet<>(FREQUENCY_COMPARATOR);
    varsByFrequency.addAll(assignments.values());

    if (shouldShadow) {
      new ShadowVariables(
          compiler, assignments, varsByFrequency, pseudoNameMap).process(
              externs, root);
    }

    // First try to reuse names from an earlier compilation.
    if (prevUsedRenameMap != null) {
      reusePreviouslyUsedVariableMap();
    }

    // Assign names, sorted by descending frequency to minimize code size.
    assignNames(varsByFrequency);

    boolean changed = false;

    // Rename the globals!
    for (Node n : globalNameNodes) {
      String newName = getNewGlobalName(n);
      // Note: if newName is null, then oldName is an extern.
      if (newName != null) {
        n.setString(newName);
        changed = true;
      }
    }

    // Rename the locals!
    for (Node n : localNameNodes) {
      String newName = getNewLocalName(n);
      if (newName != null) {
        n.setString(newName);
        changed = true;
      }
    }

    if (changed) {
      compiler.reportCodeChange();
    }

    // Lastly, write the name assignments to the debug log.
    compiler.addToDebugLog("JS var assignments:\n" + assignmentLog);
    assignmentLog = null;
  }

  private String getNewGlobalName(Node n) {
    String oldName = n.getString();
    Assignment a = assignments.get(oldName);
    if (a.newName != null && !a.newName.equals(oldName)) {
      if (pseudoNameMap != null) {
        return pseudoNameMap.get(n);
      }
      return a.newName;
    } else {
      return null;
    }
  }

  private String getNewLocalName(Node n) {
    String oldTempName = n.getString();
    Assignment a = assignments.get(oldTempName);
    if (!a.newName.equals(oldTempName)) {
      if (pseudoNameMap != null) {
        return pseudoNameMap.get(n);
      }
      return a.newName;
    }
    return null;
  }

  private void recordPseudoName(Node n) {
    // Variable names should be in a different name space than
    // property pseudo names.
    pseudoNameMap.put(n, '$' + n.getString() + "$$");
  }

  /**
   * Runs through the assignments and reuses as many names as possible from the
   * previously used variable map. Updates reservedNames with the set of names
   * that were reused.
   */
  private void reusePreviouslyUsedVariableMap() {
    // If prevUsedRenameMap had duplicate values then this pass would be
    // non-deterministic.
    // In such a case, the following will throw an IllegalArgumentException.
    Preconditions.checkNotNull(prevUsedRenameMap.getNewNameToOriginalNameMap());
    for (Assignment a : assignments.values()) {
      String prevNewName = prevUsedRenameMap.lookupNewName(a.oldName);
      if (prevNewName == null || reservedNames.contains(prevNewName)) {
        continue;
      }

      if (a.oldName.startsWith(LOCAL_VAR_PREFIX)
          || (!externNames.contains(a.oldName)
              && prevNewName.startsWith(prefix))) {
        reservedNames.add(prevNewName);
        finalizeNameAssignment(a, prevNewName);
      }
    }
  }

  /**
   * Determines which new names to substitute for the original names.
   */
  private void assignNames(SortedSet<Assignment> varsToRename) {
    NameGenerator globalNameGenerator = null;
    NameGenerator localNameGenerator = null;

    globalNameGenerator = nameGenerator;
    nameGenerator.reset(reservedNames, prefix, reservedCharacters);

    // Local variables never need a prefix.
    // Also, we need to avoid conflicts between global and local variable
    // names; we do this by having using the same generator (not two
    // instances). The case where global variables have a prefix (and
    // therefore we use two different generators) but a local variable name
    // might nevertheless conflict with a global one is not handled.
    localNameGenerator =
        prefix.isEmpty()
        ? globalNameGenerator
        : nameGenerator.clone(reservedNames, "", reservedCharacters);

    // Generated names and the assignments for non-local vars.
    List<Assignment> pendingAssignments = new ArrayList<>();
    List<String> generatedNamesForAssignments = new ArrayList<>();

    for (Assignment a : varsToRename) {
      if (a.newName != null) {
        continue;
      }

      if (externNames.contains(a.oldName)) {
        continue;
      }

      String newName;
      if (a.oldName.startsWith(LOCAL_VAR_PREFIX)) {
        // For local variable, we make the assignment right away.
        newName = localNameGenerator.generateNextName();
        finalizeNameAssignment(a, newName);
      } else {
        // For non-local variable, delay finalizing the name assignment
        // until we know how many new names we'll have of length 2, 3, etc.
        newName = globalNameGenerator.generateNextName();
        pendingAssignments.add(a);
        generatedNamesForAssignments.add(newName);
      }
      reservedNames.add(newName);
    }

    // Now that we have a list of generated names, and a list of variable
    // Assignment objects, we assign the generated names to the vars as
    // follows:
    // 1) The most frequent vars get the shorter names.
    // 2) If N number of vars are going to be assigned names of the same
    //    length, we assign the N names based on the order at which the vars
    //    first appear in the source. This makes the output somewhat less
    //    random, because symbols declared close together are assigned names
    //    that are quite similar. With this heuristic, the output is more
    //    compressible.
    //    For instance, the output may look like:
    //    var da = "..", ea = "..";
    //    function fa() { .. } function ga() { .. }

    int numPendingAssignments = generatedNamesForAssignments.size();
    for (int i = 0; i < numPendingAssignments;) {
      SortedSet<Assignment> varsByOrderOfOccurrence =
          new TreeSet<>(ORDER_OF_OCCURRENCE_COMPARATOR);

      // Add k number of Assignment to the set, where k is the number of
      // generated names of the same length.
      int len = generatedNamesForAssignments.get(i).length();
      for (int j = i; j < numPendingAssignments
          && generatedNamesForAssignments.get(j).length() == len; j++) {
        varsByOrderOfOccurrence.add(pendingAssignments.get(j));
      }

      // Now, make the assignments
      for (Assignment a : varsByOrderOfOccurrence) {
        finalizeNameAssignment(a, generatedNamesForAssignments.get(i));
        ++i;
      }
    }
  }

  /**
   * Makes a final name assignment.
   */
  private void finalizeNameAssignment(Assignment a, String newName) {
    a.setNewName(newName);

    // Keep track of the mapping
    renameMap.put(a.oldName, newName);

    // Log the mapping
    assignmentLog.append(a.oldName).append(" => ").append(newName).append('\n');
  }

  /**
   * Gets the variable map.
   */
  VariableMap getVariableMap() {
    return new VariableMap(ImmutableMap.copyOf(renameMap));
  }

  /**
   * Determines whether a variable name is okay to rename.
   */
  private boolean okToRenameVar(String name, boolean isLocal) {
    return !compiler.getCodingConvention().isExported(name, isLocal);
  }

  /**
   * Returns the index within the scope stack.
   * e.g. function Foo(a) { var b; function c(d) { } }
   * a = 0, b = 1, c = 2, d = 3
   */
  private int getLocalVarIndex(Var v) {
    int num = v.index;
    Scope s = v.scope.getParent();
    if (s == null) {
      throw new IllegalArgumentException("Var is not local");
    }

    boolean isBleedingIntoScope = s.getParent() != null &&
        localBleedingFunctions.contains(v);

    while (s.getParent() != null) {
      if (isBleedingIntoScope) {
        num += localBleedingFunctionsPerScope.get(s).indexOf(v) + 1;
        isBleedingIntoScope = false;
      } else {
        num += localBleedingFunctionsPerScope.get(s).size();
      }
      if (shouldTemporarilyRenameLocalsInScope(s)) {
        num += s.getVarCount();
      }
      s = s.getParent();
    }
    return num;
  }

  /**
   * Returns true if the local variables in a scope should be given
   * temporary names (eg, 'L 123') prior to renaming to allow reuse of
   * names across scopes.  With {@code preferStableNames}, temporary
   * renaming is disabled if the number of locals in the scope is
   * above a heuristic threshold to allow effective reuse of rename
   * maps (see {@code prevUsedRenameMap}).  In scopes with many
   * variables the temporary name given to a variable is unlikely to
   * be the same temporary name used when the rename map was created.
   */
  private boolean shouldTemporarilyRenameLocalsInScope(Scope s) {
    return (!preferStableNames ||
        s.getVarCount() <= MAX_LOCALS_IN_SCOPE_TO_TEMP_RENAME);
  }
}
