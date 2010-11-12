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

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.annotation.Nullable;


/**
 * RenameVars renames all the variables names into short names, to reduce
 * code size and also to obfuscate the code.
 *
 */
final class RenameVars implements CompilerPass {
  private final AbstractCompiler compiler;

  /** List of global NAME nodes */
  private final ArrayList<Node> globalNameNodes = new ArrayList<Node>();

  /** List of local NAME nodes */
  private final ArrayList<Node> localNameNodes = new ArrayList<Node>();

  /**
   * Maps a name node to its pseudo name, null if we are not generating so
   * there will not no overhead unless we are debugging.
   */
  private final Map<Node, String> pseudoNameMap;

  /** Set of extern variable names */
  private final Set<String> externNames = new HashSet<String>();

  /** Set of reserved variable names */
  private final Set<String> reservedNames;

  /** The renaming map */
  private final Map<String, String> renameMap = new HashMap<String, String>();

  /** The previously used rename map. */
  private final VariableMap prevUsedRenameMap;

  /** The global name prefix */
  private final String prefix;

  /** Counter for each assignment */
  private int assignmentCount = 0;

  /** Logs all name assignments */
  private StringBuilder assignmentLog;

  class Assignment {
    final CompilerInput input;
    final String oldName;
    final int orderOfOccurrence;
    String newName;
    int count;                          // Number of times this is referenced

    Assignment(String name, CompilerInput input) {
      this.input = input;
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
  private final SortedMap<String, Assignment> assignments =
      new TreeMap<String, Assignment>();

  /** Whether renaming should apply to local variables only. */
  private final boolean localRenamingOnly;

  /**
   * Whether function expression names should be preserved. Typically, for
   * debugging purposes.
   * @see NameAnonymousFunctions
   */
  private boolean preserveFunctionExpressionNames;

  /** Characters that shouldn't be used in variable names. */
  private final char[] reservedCharacters;

  /** A prefix to distinguish temporary local names from global names */
  private static final String LOCAL_VAR_PREFIX = "L ";

  RenameVars(AbstractCompiler compiler,
      String prefix,
      boolean localRenamingOnly,
      boolean preserveFunctionExpressionNames,
      boolean generatePseudoNames,
      VariableMap prevUsedRenameMap,
      @Nullable char[] reservedCharacters,
      @Nullable Set<String> reservedNames) {
    this.compiler = compiler;
    this.prefix = prefix == null ? "" : prefix;
    this.localRenamingOnly = localRenamingOnly;
    this.preserveFunctionExpressionNames = preserveFunctionExpressionNames;
    if (generatePseudoNames) {
      this.pseudoNameMap = Maps.newHashMap();
    } else {
      this.pseudoNameMap = null;
    }
    this.prevUsedRenameMap = prevUsedRenameMap;
    this.reservedCharacters = reservedCharacters;
    if (reservedNames == null) {
      this.reservedNames = Sets.newHashSet();
    } else {
      this.reservedNames = Sets.newHashSet(reservedNames);
    }
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
  class ProcessVars extends AbstractPostOrderCallback {
    private final boolean isExternsPass_;

    ProcessVars(boolean isExterns) {
      isExternsPass_ = isExterns;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.getType() != Token.NAME) {
        return;
      }

      String name = n.getString();

      // Ignore anonymous functions
      if (name.length() == 0) {
        return;
      }

      // Is this local or Global?
      Scope.Var var = t.getScope().getVar(name);
      boolean local = (var != null) && var.isLocal();

      // Are we renaming global variables?
      if (!local && localRenamingOnly) {
        reservedNames.add(name);
        return;
      }

      // Are we renaming function expression names?
      if (preserveFunctionExpressionNames
          && var != null
          && NodeUtil.isFunctionExpression(var.getParentNode())) {
        reservedNames.add(name);
        return;
      }

      // Check if we can rename this.
      if (!okToRenameVar(name, local)) {
        if (local) {
          // Blindly de-uniquify for the Prototype library for issue 103.
          String newName =
            MakeDeclaredNamesUnique.ContextualRenameInverter.getOrginalName(
                name);
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

      if (local) {
        // Local var: assign a new name
        String tempName = LOCAL_VAR_PREFIX + var.getLocalVarIndex();
        incCount(tempName, null);
        localNameNodes.add(n);
        n.setString(tempName);
      } else if (var != null) {  // Not an extern
        // If it's global, increment global count
        incCount(name, var.input);
        globalNameNodes.add(n);
      }
    }

    // Increment count of an assignment
    void incCount(String name, CompilerInput input) {
      Assignment s = assignments.get(name);
      if (s == null) {
        s = new Assignment(name, input);
        assignments.put(name, s);
      }
      s.count++;
    }
  }

  /**
   * Sorts Assignment objects by their count, breaking ties by their
   * order of occurrence in the source to ensure a deterministic total
   * ordering.
   */
  private static final Comparator<Assignment> FREQUENCY_COMPARATOR =
    new Comparator<Assignment>() {
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
      public int compare(Assignment a1, Assignment a2) {
        return a1.orderOfOccurrence - a2.orderOfOccurrence;
      }
    };

  @Override
  public void process(Node externs, Node root) {
    assignmentLog = new StringBuilder();

    // Do variable reference counting.
    NodeTraversal.traverse(compiler, externs, new ProcessVars(true));
    NodeTraversal.traverse(compiler, root, new ProcessVars(false));

    // Make sure that new names don't overlap with extern names.
    reservedNames.addAll(externNames);

    // Rename vars, sorted by frequency of occurrence to minimize code size.
    SortedSet<Assignment> varsByFrequency =
        new TreeSet<Assignment>(FREQUENCY_COMPARATOR);
    varsByFrequency.addAll(assignments.values());

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
    int count = 0;
    for (Node n : localNameNodes) {
      String newName = getNewLocalName(n);
      if (newName != null) {
        n.setString(newName);
        changed = true;
      }
      count++;
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
    pseudoNameMap.put(n, '$' + n.getString() + "$$" );
  }

  /**
   * Runs through the assignments and reuses as many names as possible from the
   * previously used variable map. Updates reservedNames with the set of names
   * that were reused.
   */
  private void reusePreviouslyUsedVariableMap() {
    for (Assignment a : assignments.values()) {
      String prevNewName = prevUsedRenameMap.lookupNewName(a.oldName);
      if (prevNewName == null || reservedNames.contains(prevNewName)) {
        continue;
      }

      if (a.oldName.startsWith(LOCAL_VAR_PREFIX) ||
          (!externNames.contains(a.oldName) &&
           prevNewName.startsWith(prefix))) {
        reservedNames.add(prevNewName);
        finalizeNameAssignment(a, prevNewName);
      }
    }
  }

  /**
   * Determines which new names to substitute for the original names.
   */
  private void assignNames(Set<Assignment> varsToRename) {
    NameGenerator globalNameGenerator =
        new NameGenerator(reservedNames, prefix, reservedCharacters);

    // Local variables never need a prefix.
    NameGenerator localNameGenerator = prefix.isEmpty() ?
        globalNameGenerator : new NameGenerator(reservedNames, "",
        reservedCharacters);

    // Generated names and the assignments for non-local vars.
    List<Assignment> pendingAssignments = new ArrayList<Assignment>();
    List<String> generatedNamesForAssignments = new ArrayList<String>();

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
          new TreeSet<Assignment>(ORDER_OF_OCCURRENCE_COMPARATOR);

      // Add k number of Assignment to the set, where k is the number of
      // generated names of the same length.
      int len = generatedNamesForAssignments.get(i).length();
      for (int j = i;
           j < numPendingAssignments &&
               generatedNamesForAssignments.get(j).length() == len;
           j++) {
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
    assignmentLog.append(a.oldName).append(" => ").append(newName).
        append('\n');
  }

  /**
   * Gets the variable map.
   */
  VariableMap getVariableMap() {
    return new VariableMap(renameMap);
  }

  /**
   * Determines whether a variable name is okay to rename.
   */
  private boolean okToRenameVar(String name, boolean isLocal) {
    return !compiler.getCodingConvention().isExported(name, isLocal);
  }
}
