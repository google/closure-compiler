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
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * A compiler pass to run various peephole optimizations (e.g. constant folding,
 * some useless code removal, some minimizations).
 */
class PeepholeOptimizationsPass implements CompilerPass {

  private final AbstractCompiler compiler;
  private final String passName;
  // NOTE: Use a native array rather than a List to avoid creating iterators for every node in the
  // AST.
  private final AbstractPeepholeOptimization[] peepholeOptimizations;
  private final @Nullable Supplier<List<AbstractPeepholeOptimization>> parallelOptimizationFactory;
  private boolean retraverseOnChange;

  /** Creates a peephole optimization pass that runs the given optimizations. */
  PeepholeOptimizationsPass(
      AbstractCompiler compiler, String passName, AbstractPeepholeOptimization... optimizations) {
    this(compiler, passName, Arrays.asList(optimizations));
  }

  PeepholeOptimizationsPass(
      AbstractCompiler compiler,
      String passName,
      List<AbstractPeepholeOptimization> optimizations) {
    this(compiler, passName, optimizations, null);
  }

  PeepholeOptimizationsPass(
      AbstractCompiler compiler,
      String passName,
      Supplier<List<AbstractPeepholeOptimization>> optimizationFactory) {
    this(compiler, passName, optimizationFactory.get(), optimizationFactory);
  }

  private PeepholeOptimizationsPass(
      AbstractCompiler compiler,
      String passName,
      List<AbstractPeepholeOptimization> optimizations,
      @Nullable Supplier<List<AbstractPeepholeOptimization>> parallelOptimizationFactory) {
    this.compiler = compiler;
    this.passName = passName;
    this.peepholeOptimizations = optimizations.toArray(new AbstractPeepholeOptimization[0]);
    this.parallelOptimizationFactory = parallelOptimizationFactory;
    this.retraverseOnChange = true;
  }

  @VisibleForTesting
  void setRetraverseOnChange(boolean retraverse) {
    this.retraverseOnChange = retraverse;
  }

  @Override
  public void process(Node externs, Node root) {
    beginTraversal();

    // Repeat to an internal fixed point.
    for (List<Node> changedScopeNodes =
            compiler.getChangeTracker().getChangedScopeNodesForPass(passName);
        changedScopeNodes == null || !changedScopeNodes.isEmpty();
        changedScopeNodes = compiler.getChangeTracker().getChangedScopeNodesForPass(passName)) {

      if (changedScopeNodes == null && canTraverseScriptsConcurrently(root)) {
        traverseScriptsConcurrently(root);
      } else if (changedScopeNodes == null) {
        // changedScopeNodes is null if this is the first run of peepholeOptimizationsPass.
        NodeTraversal.traverse(compiler, root, new PeepCallback(peepholeOptimizations));
      } else if (canTraverseScopesConcurrently(changedScopeNodes)) {
        traverseScopesConcurrently(changedScopeNodes);
      } else {
        NodeTraversal.traverseScopeRoots(
            compiler,
            changedScopeNodes,
            new PeepCallback(peepholeOptimizations),
            /* traverseNested= */ false);
      }

      // Cancel the fixed point if requested.
      if (!retraverseOnChange) {
        break;
      }
    }

    endTraversal();
  }

  private boolean canTraverseScriptsConcurrently(Node root) {
    if (parallelOptimizationFactory == null
        || compiler.getOptions().getNumParallelThreads() <= 1
        || !root.isRoot()
        || !root.hasMoreThanOneChild()) {
      return false;
    }
    for (Node script = root.getFirstChild(); script != null; script = script.getNext()) {
      if (!script.isScript()) {
        return false;
      }
    }
    return true;
  }

  private void traverseScriptsConcurrently(Node root) {
    ArrayList<Node> scripts = new ArrayList<>();
    for (Node script = root.getFirstChild(); script != null; script = script.getNext()) {
      scripts.add(script);
    }
    int parallelism = Math.min(compiler.getOptions().getNumParallelThreads(), scripts.size());
    ChangeTracker.BufferedChanges[] scriptChanges =
        new ChangeTracker.BufferedChanges[scripts.size()];
    ExecutorService executor =
        Executors.newFixedThreadPool(
            parallelism,
            runnable -> {
              Thread thread = new Thread(runnable, "jscompiler-PeepholeOptimizations");
              thread.setDaemon(true);
              return thread;
            });
    ArrayList<Future<?>> futures = new ArrayList<>(parallelism);
    boolean completed = false;
    try {
      for (int workerIndex = 0; workerIndex < parallelism; workerIndex++) {
        int firstScript = workerIndex;
        futures.add(
            executor.submit(
                () -> {
                  AbstractPeepholeOptimization[] optimizations =
                      parallelOptimizationFactory
                          .get()
                          .toArray(new AbstractPeepholeOptimization[0]);
                  beginTraversal(optimizations);
                  try {
                    for (int scriptIndex = firstScript;
                        scriptIndex < scripts.size();
                        scriptIndex += parallelism) {
                      ChangeTracker.BufferedChanges changes =
                          compiler.getChangeTracker().beginBufferingChanges();
                      try {
                        NodeTraversal.traverse(
                            compiler,
                            scripts.get(scriptIndex),
                            new PeepCallback(optimizations));
                      } finally {
                        compiler.getChangeTracker().endBufferingChanges(changes);
                      }
                      scriptChanges[scriptIndex] = changes;
                    }
                  } finally {
                    endTraversal(optimizations);
                  }
                }));
      }
      executor.shutdown();
      awaitParallelTraversals(futures);
      for (ChangeTracker.BufferedChanges changes : scriptChanges) {
        compiler.getChangeTracker().applyBufferedChanges(changes);
      }
      completed = true;
    } finally {
      if (!completed) {
        executor.shutdownNow();
      }
    }
  }

  private boolean canTraverseScopesConcurrently(List<Node> changedScopeNodes) {
    if (parallelOptimizationFactory == null
        || compiler.getOptions().getNumParallelThreads() <= 1
        || changedScopeNodes.size() <= 1) {
      return false;
    }
    Node firstScript = NodeUtil.getEnclosingScript(changedScopeNodes.get(0));
    if (firstScript == null) {
      return false;
    }
    for (int i = 1; i < changedScopeNodes.size(); i++) {
      Node script = NodeUtil.getEnclosingScript(changedScopeNodes.get(i));
      if (script == null) {
        return false;
      }
      if (script != firstScript) {
        return true;
      }
    }
    return false;
  }

  private void traverseScopesConcurrently(List<Node> changedScopeNodes) {
    LinkedHashMap<Node, ArrayList<Node>> scopesByScript = new LinkedHashMap<>();
    for (Node changedScope : changedScopeNodes) {
      scopesByScript
          .computeIfAbsent(NodeUtil.getEnclosingScript(changedScope), unused -> new ArrayList<>())
          .add(changedScope);
    }
    ArrayList<List<Node>> scopeGroups = new ArrayList<>(scopesByScript.values());
    int parallelism = Math.min(compiler.getOptions().getNumParallelThreads(), scopeGroups.size());
    ChangeTracker.BufferedChanges[] groupChanges =
        new ChangeTracker.BufferedChanges[scopeGroups.size()];
    ExecutorService executor =
        Executors.newFixedThreadPool(
            parallelism,
            runnable -> {
              Thread thread = new Thread(runnable, "jscompiler-PeepholeChangedScopes");
              thread.setDaemon(true);
              return thread;
            });
    ArrayList<Future<?>> futures = new ArrayList<>(parallelism);
    boolean completed = false;
    try {
      for (int workerIndex = 0; workerIndex < parallelism; workerIndex++) {
        int firstGroup = workerIndex;
        futures.add(
            executor.submit(
                () -> {
                  AbstractPeepholeOptimization[] optimizations =
                      parallelOptimizationFactory
                          .get()
                          .toArray(new AbstractPeepholeOptimization[0]);
                  beginTraversal(optimizations);
                  try {
                    for (int groupIndex = firstGroup;
                        groupIndex < scopeGroups.size();
                        groupIndex += parallelism) {
                      ChangeTracker.BufferedChanges changes =
                          compiler.getChangeTracker().beginBufferingChanges();
                      try {
                        NodeTraversal.traverseScopeRoots(
                            compiler,
                            scopeGroups.get(groupIndex),
                            new PeepCallback(optimizations),
                            /* traverseNested= */ false);
                      } finally {
                        compiler.getChangeTracker().endBufferingChanges(changes);
                      }
                      groupChanges[groupIndex] = changes;
                    }
                  } finally {
                    endTraversal(optimizations);
                  }
                }));
      }
      executor.shutdown();
      awaitParallelTraversals(futures);
      for (ChangeTracker.BufferedChanges changes : groupChanges) {
        compiler.getChangeTracker().applyBufferedChanges(changes);
      }
      completed = true;
    } finally {
      if (!completed) {
        executor.shutdownNow();
      }
    }
  }

  private static void awaitParallelTraversals(List<Future<?>> futures) {
    for (Future<?> future : futures) {
      try {
        future.get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while running peephole optimizations", e);
      } catch (ExecutionException e) {
        throw new IllegalStateException(
            "Cannot run peephole optimizations concurrently", e.getCause());
      }
    }
  }

  private class PeepCallback extends NodeTraversal.AbstractScopedCallback {
    private final AbstractPeepholeOptimization[] optimizations;

    PeepCallback(AbstractPeepholeOptimization[] optimizations) {
      this.optimizations = optimizations;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      Node currentNode = n;
      for (AbstractPeepholeOptimization optim : optimizations) {
        currentNode = optim.optimizeSubtree(currentNode);
        if (currentNode == null) {
          return;
        }
      }
    }

    @Override
    public void enterScope(NodeTraversal t) {}

    @Override
    public void exitScope(NodeTraversal t) {
      // Call updateFeatures() here, instead of immediately after
      // `optim.optimizeSubtree(currentNode)` in visit, to avoid repeating work for
      // every AST node x every peephole optimization. In practice, profiling shows a small
      // but measurable improvement for large projects. See comments on cl/856752797.
      updateFeatures(t);
    }

    private void updateFeatures(NodeTraversal t) {
      for (AbstractPeepholeOptimization optim : optimizations) {
        var newFeatures = optim.getNewFeatures();
        if (!newFeatures.isEmpty()) {
          NodeUtil.addFeaturesToScript(
              t.getCurrentScript(), FeatureSet.BARE_MINIMUM.with(newFeatures), compiler);
        }
        optim.clearNewFeatures();
      }
    }
  }

  /** Make sure that all the optimizations have the current compiler so they can report errors. */
  private void beginTraversal() {
    beginTraversal(peepholeOptimizations);
  }

  private void beginTraversal(AbstractPeepholeOptimization[] optimizations) {
    for (AbstractPeepholeOptimization optimization : optimizations) {
      optimization.beginTraversal(compiler);
    }
  }

  /** End the traversal. */
  private void endTraversal() {
    endTraversal(peepholeOptimizations);
  }

  private void endTraversal(AbstractPeepholeOptimization[] optimizations) {
    for (AbstractPeepholeOptimization optimization : optimizations) {
      optimization.endTraversal();
    }
  }
}
