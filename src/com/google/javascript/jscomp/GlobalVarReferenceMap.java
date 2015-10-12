/*
 * Copyright 2011 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.ReferenceCollectingCallback.Reference;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceCollection;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceMap;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * An implementation for {@code ReferenceMap} that is specific to global scope
 * and can be used in different passes. In other words instead of relying on
 * Var object it relies on the name of the variable. It also supports hot-swap
 * update of reference map for a specific script.
 *
 * @see ReferenceCollectingCallback#exitScope(NodeTraversal)
 *
 * @author bashir@google.com (Bashir Sadjad)
 */
class GlobalVarReferenceMap implements ReferenceMap {

  private Map<String, ReferenceCollection> refMap = null;

  private final Map<InputId, Integer> inputOrder;

  /**
   * @param inputs The ordered list of all inputs for the compiler.
   */
  GlobalVarReferenceMap(List<CompilerInput> inputs, List<CompilerInput> externs) {
    inputOrder = new HashMap<>();
    int ind = 0;
    for (CompilerInput extern : externs) {
      inputOrder.put(extern.getInputId(), ind);
      ind++;
    }
    for (CompilerInput input : inputs) {
      inputOrder.put(input.getInputId(), ind);
      ind++;
    }
  }

  @Override
  public ReferenceCollection getReferences(Var var) {
    if (!var.isGlobal()) {
      return null;
    }
    return refMap.get(var.getName());
  }

  /**
   * Resets global var reference map with the new provide map.
   *
   * @param globalRefMap The reference map result of a
   *     {@link ReferenceCollectingCallback} pass collected from the whole AST.
   */
  private void resetGlobalVarReferences(
      Map<Var, ReferenceCollection> globalRefMap) {
    refMap = new LinkedHashMap<>();
    for (Entry<Var, ReferenceCollection> entry : globalRefMap.entrySet()) {
      Var var = entry.getKey();
      if (var.isGlobal()) {
        refMap.put(var.getName(), entry.getValue());
      }
    }
  }

  /**
   * Updates the internal reference map based on the provided parameters. If
   * {@code scriptRoot} is not SCRIPT, it basically replaces the internal map
   * with the new one, otherwise it replaces all the information associated to
   * the given script.
   *
   * @param refMapPatch The reference map result of a
   *     {@link ReferenceCollectingCallback} pass which might be collected from
   *     the whole AST or just a sub-tree associated to a SCRIPT node.
   * @param root AST sub-tree root on which reference collection was done.
   */
  void updateGlobalVarReferences(Map<Var, ReferenceCollection>
      refMapPatch, Node root) {
    if (refMap == null || !root.isScript()) {
      resetGlobalVarReferences(refMapPatch);
      return;
    }

    InputId inputId = root.getInputId();
    Preconditions.checkNotNull(inputId);
    // Note there are two assumptions here (i) the order of compiler inputs
    // has not changed and (ii) all references are in the order they appear
    // in AST (this is enforced in ReferenceCollectionCallback).
    removeScriptReferences(inputId);
    for (Entry<Var, ReferenceCollection> entry : refMapPatch.entrySet()) {
      Var var = entry.getKey();
      if (var.isGlobal()) {
        replaceReferences(var.getName(), inputId, entry.getValue());
      }
    }
  }

  private void removeScriptReferences(InputId inputId) {
    Preconditions.checkNotNull(inputId);

    if (!inputOrder.containsKey(inputId)) {
      return; // Input did not exist when last computed, so skip
    }
    // TODO(bashir): If this is too slow it is not too difficult to make it
    // faster with keeping an index for variables accessed in sourceName.
    for (ReferenceCollection collection : refMap.values()) {
      if (collection == null) {
        continue;
      }
      List<Reference> oldRefs = collection.references;
      SourceRefRange range = findSourceRefRange(oldRefs, inputId);
      List<Reference> newRefs = new ArrayList<>(range.refsBefore());
      newRefs.addAll(range.refsAfter());
      collection.references = newRefs;
    }
  }

  private void replaceReferences(String varName, InputId inputId,
      ReferenceCollection newSourceCollection) {
    ReferenceCollection combined = new ReferenceCollection();
    List<Reference> combinedRefs = combined.references;
    ReferenceCollection oldCollection = refMap.get(varName);
    refMap.put(varName, combined);
    if (oldCollection == null) {
      combinedRefs.addAll(newSourceCollection.references);
      return;
    }
    // otherwise replace previous references that are from sourceName
    SourceRefRange range = findSourceRefRange(oldCollection.references,
      inputId);
    combinedRefs.addAll(range.refsBefore());
    combinedRefs.addAll(newSourceCollection.references);
    combinedRefs.addAll(range.refsAfter());
  }

  /**
   * Finds the range of references associated to {@code sourceName}. Note that
   * even if there is no sourceName references the returned information can be
   * used to decide where to insert new sourceName refs.
   */
  private SourceRefRange findSourceRefRange(List<Reference> refList,
      InputId inputId) {
    Preconditions.checkNotNull(inputId);

    // TODO(bashir): We can do binary search here, but since this is fast enough
    // right now, we just do a linear search for simplicity.
    int lastBefore = -1;
    int firstAfter = refList.size();
    int index = 0;

    Preconditions.checkState(inputOrder.containsKey(inputId), inputId.getIdName());
    int sourceInputOrder = inputOrder.get(inputId);
    for (Reference ref : refList) {
      Preconditions.checkNotNull(ref.getInputId());
      int order = inputOrder.get(ref.getInputId());
      if (order < sourceInputOrder) {
        lastBefore = index;
      } else if (order > sourceInputOrder) {
        firstAfter = index;
        break;
      }
      index++;
    }
    return new SourceRefRange(refList, lastBefore, firstAfter);
  }

  private static class SourceRefRange {
    private final int lastBefore;
    private final int firstAfter;
    private final List<Reference> refList;

    SourceRefRange(List<Reference> refList, int lastBefore,
        int firstAfter) {
      this.lastBefore = Math.max(lastBefore, -1);
      this.firstAfter = Math.min(firstAfter, refList.size());
      this.refList = refList;
    }

    /** Note that the returned list is backed by {@code refList}! */
    List<Reference> refsBefore() {
      return refList.subList(0, lastBefore + 1);
    }

    /** Note that the returned list is backed by {@code refList}! */
    List<Reference> refsAfter() {
      return refList.subList(firstAfter, refList.size());
    }
  }

  /**
   * @param globalScope a new Global Scope to replace the scope of references
   *        with.
   */
  public void updateReferencesWithGlobalScope(Scope globalScope) {
    for (ReferenceCollection collection : refMap.values()) {
      List<Reference> newRefs = new ArrayList<>(collection.references.size());
      for (Reference ref : collection.references) {
        if (ref.getScope() != globalScope) {
          newRefs.add(ref.cloneWithNewScope(globalScope));
        } else {
          newRefs.add(ref);
        }
      }
      collection.references = newRefs;
    }
  }

  /**
   * A CleanupPass implementation that will replace references to old Syntactic
   * Global Scopes generated in previous compile runs with references to the
   * Global Typed Scope.
   *
   * @author tylerg@google.com (Tyler Goodwin)
   */
  static class GlobalVarRefCleanupPass implements HotSwapCompilerPass {

    private final AbstractCompiler compiler;

    public GlobalVarRefCleanupPass(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void hotSwapScript(Node scriptRoot, Node originalRoot) {
      GlobalVarReferenceMap refMap = compiler.getGlobalVarReferences();
      if (refMap != null) {
        refMap.updateReferencesWithGlobalScope(compiler.getTopScope());
      }
    }

    @Override
    public void process(Node externs, Node root) {
      // GlobalVarRefCleanupPass should not do work during process.
    }
  }
}
