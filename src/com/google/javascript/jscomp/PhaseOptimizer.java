/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * An object that optimizes the order of compiler passes.
 *
 * @author nicksantos@google.com (Nick Santos)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
class PhaseOptimizer implements CompilerPass {

  private static final Logger logger = Logger.getLogger(PhaseOptimizer.class.getName());
  private final AbstractCompiler compiler;
  private final PerformanceTracker tracker;
  private final List<CompilerPass> passes;
  private boolean inLoop;
  private PassFactory validityCheck;
  private boolean printAstHashcodes = false;

  private double progress = 0.0;
  private double progressStep = 0.0;
  private ProgressRange progressRange;

  // These fields are used during optimization loops.
  // They are declared here for two reasons:
  // 1) Loop and ScopedChangeHandler can communicate via shared state
  // 2) Compiler talks to PhaseOptimizer, not Loop or ScopedChangeHandler
  private NamedPass currentPass;
  // For each pass, remember the time at the end of the pass's last run.
  private Map<NamedPass, Integer> lastRuns;
  // The time of the last change made to the program by any pass.
  private int lastChange;
  private static final int START_TIME = 0;
  private final Node jsRoot;

  private final boolean useSizeHeuristicToStopOptimizationLoop;

  // Checks that passes have reported code changes correctly.
  private ChangeVerifier changeVerifier;

  /**
   * When processing loopable passes in order, the PhaseOptimizer can be in one
   * of these two states.
   * <p>
   * This enum is used by Loop/process only, but enum types can't be local.
   */
  enum State {
    RUN_PASSES_NOT_RUN_IN_PREV_ITER,
    RUN_PASSES_THAT_CHANGED_STH_IN_PREV_ITER
  }

  /**
   * NOTE(dimvar): There used to be some code that tried various orderings of loopable passes and
   * picked the fastest one. This code became stale gradually and I decided to remove it. It was
   * also never tried after the new pass scheduler was written. If we need to revisit this order in
   * the future, we should write new code to do it.
   *
   * <p>It is important that inlineVariables and peepholeOptimizations run after inlineFunctions,
   * because inlineFunctions relies on them to clean up patterns it introduces. This affects our
   * size-based loop-termination heuristic.
   */
  @VisibleForTesting
  static final ImmutableList<String> OPTIMAL_ORDER =
      ImmutableList.of(
          PassNames.INLINE_FUNCTIONS,
          PassNames.INLINE_VARIABLES,
          PassNames.DEAD_ASSIGNMENT_ELIMINATION,
          PassNames.COLLAPSE_OBJECT_LITERALS,
          PassNames.REMOVE_UNUSED_CODE,
          PassNames.REMOVE_UNUSED_PROTOTYPE_PROPERTIES,
          PassNames.REMOVE_UNUSED_CLASS_PROPERTIES,
          PassNames.PEEPHOLE_OPTIMIZATIONS,
          PassNames.REMOVE_UNREACHABLE_CODE);

  static final ImmutableList<String> CODE_REMOVING_PASSES =
      ImmutableList.of(PassNames.PEEPHOLE_OPTIMIZATIONS, PassNames.REMOVE_UNREACHABLE_CODE);

  static final int MAX_LOOPS = 100;
  static final String OPTIMIZE_LOOP_ERROR =
      "Fixed point loop exceeded the maximum number of iterations.";

  /** @see CompilerOptions#optimizationLoopMaxIterations */
  private final int optimizationLoopMaxIterations;

  /**
   * @param comp the compiler that owns/creates this.
   * @param tracker an optional performance tracker
   */
  PhaseOptimizer(AbstractCompiler comp, PerformanceTracker tracker) {
    this.compiler = comp;
    this.jsRoot = comp.getJsRoot();
    this.tracker = tracker;
    this.passes = new ArrayList<>();
    this.inLoop = false;
    this.lastChange = START_TIME;
    this.useSizeHeuristicToStopOptimizationLoop =
        comp.getOptions().useSizeHeuristicToStopOptimizationLoop;
    int maxIterations = comp.getOptions().optimizationLoopMaxIterations;
    if (maxIterations > 0 && maxIterations <= MAX_LOOPS) {
      this.optimizationLoopMaxIterations = maxIterations;
    } else {
      this.optimizationLoopMaxIterations = MAX_LOOPS;
    }
  }

  PhaseOptimizer withProgress(ProgressRange range) {
    this.progressRange = range;
    return this;
  }

  /**
   * Add the passes generated by the given factories to the compile sequence.
   * <p>
   * Automatically pulls multi-run passes into fixed point loops. If there
   * are 1 or more multi-run passes in a row, they will run together in
   * the same fixed point loop. The passes will run until they are finished
   * making changes.
   * <p>
   * The PhaseOptimizer is free to tweak the order and frequency of multi-run
   * passes in a fixed-point loop.
   */
  void consume(List<PassFactory> factories) {
    Loop currentLoop = new Loop();
    for (PassFactory factory : factories) {
      if (factory.isOneTimePass()) {
        if (currentLoop.isPopulated()) {
          passes.add(currentLoop);
          currentLoop = new Loop();
        }
        addOneTimePass(factory);
      } else {
        currentLoop.addLoopedPass(factory);
      }
    }

    if (currentLoop.isPopulated()) {
      passes.add(currentLoop);
    }
  }

  /**
   * Add the pass generated by the given factory to the compile sequence.
   * This pass will be run once.
   */
  @VisibleForTesting
  void addOneTimePass(PassFactory factory) {
    passes.add(new NamedPass(factory));
  }

  /**
   * Add a loop to the compile sequence. This loop will continue running
   * until the AST stops changing.
   * @return The loop structure. Pass suppliers should be added to the loop.
   */
  Loop addFixedPointLoop() {
    Loop loop = new Loop();
    passes.add(loop);
    return loop;
  }

  /**
   * Adds a checker to be run after every pass. Intended for development.
   */
  void setValidityCheck(PassFactory validityCheck) {
    this.validityCheck = validityCheck;
    this.changeVerifier = new ChangeVerifier(compiler).snapshot(jsRoot);
  }

  /**
   * Sets the hashcode of the AST to be logged every pass.
   * Intended for development.
   */
  void setPrintAstHashcodes(boolean printAstHashcodes) {
    this.printAstHashcodes = printAstHashcodes;
  }

  /**
   * Run all the passes in the optimizer.
   */
  @Override
  public void process(Node externs, Node root) {
    progress = 0.0;
    progressStep = 0.0;
    if (progressRange != null) {
      progressStep = (progressRange.maxValue - progressRange.initialValue)
          / passes.size();
      progress = progressRange.initialValue;
    }

    // When looking at this code, one can mistakenly think that the instance of
    // PhaseOptimizer keeps all compiler passes live. This would be undesirable. A pass can
    // create large data structures that are only useful to the pass, and we shouldn't
    // retain them until the end of the compilation. But if you look at
    // NamedPass#process, the actual pass is created and immediately executed, and no
    // reference to it is retained in PhaseOptimizer:
    //   factory.create(compiler).process(externs, root);
    for (CompilerPass pass : passes) {
      if (Thread.interrupted()) {
        throw new RuntimeException(new InterruptedException());
      }
      pass.process(externs, root);
      if (hasHaltingErrors()) {
        return;
      }
    }
  }

  private void maybePrintAstHashcodes(String passName, Node root) {
    if (printAstHashcodes) {
      String hashCodeMsg = "AST hashCode after " + passName + ": "
          + compiler.toSource(root).hashCode();
      logger.info(hashCodeMsg);
    }
  }

  /**
   * Runs the validity check if it is available.
   */
  private void maybeRunValidityCheck(String passName, Node externs, Node root) {
    if (validityCheck == null) {
      return;
    }
    try {
      validityCheck.create(compiler).process(externs, root);
      changeVerifier.checkRecordedChanges(passName, jsRoot);
    } catch (Exception e) {
      throw new IllegalStateException("Validity checks failed for pass: " + passName, e);
    }
  }

  private boolean hasHaltingErrors() {
    return compiler.hasHaltingErrors();
  }

  /**
   * A single compiler pass.
   */
  class NamedPass implements CompilerPass {
    final String name;
    private final PassFactory factory;
    private Tracer tracer;

    NamedPass(PassFactory factory) {
      this.name = factory.getName();
      this.factory = factory;
    }

    @Override
    public void process(Node externs, Node root) {
      FeatureSet featuresInAst = compiler.getFeatureSet();
      FeatureSet featuresSupportedByPass = factory.featureSet();
      if (compiler.getOptions().shouldSkipUnsupportedPasses()
          && !featuresSupportedByPass.contains(featuresInAst)) {
        // NOTE: this warning ONLY appears in code using the Google-internal runner.
        // Both CommandLineRunner.java and gwt/client/GwtRunner.java explicitly set the logging
        // level to Level.OFF to avoid seeing this warning.
        // See https://github.com/google/closure-compiler/pull/2998 for why.
        logger.warning("Skipping pass " + name);
        FeatureSet unsupportedFeatures = featuresInAst.without(featuresSupportedByPass);
        logger.warning("AST contains unsupported features: " + unsupportedFeatures);
        return;
      }

      logger.fine("Running pass " + name);
      if (validityCheck != null) {
        // Before running the pass, clone the AST so you can check the
        // changed AST against the clone after the pass finishes.
        changeVerifier = new ChangeVerifier(compiler).snapshot(jsRoot);
      }
      if (tracker != null) {
        tracker.recordPassStart(name, factory.isOneTimePass());
      }
      tracer = new Tracer("Compiler", name);

      compiler.beforePass(name);

      // Delay the creation of the actual pass until *after* all previous passes
      // have been processed.
      // Some precondition checks rely on this, eg, in CoalesceVariableNames.
      factory.create(compiler).process(externs, root);

      compiler.afterPass(name);

      try {
        if (progressRange == null) {
          compiler.setProgress(-1, name);
        } else {
          progress += progressStep;
          compiler.setProgress(progress, name);
        }
        // Don't move this line in the IF. We create a Tracer even when the tracker
        // is null; so we must also stop the tracer when the tracker is null.
        // Otherwise, Tracer.ThreadTrace#events can become too big.
        long traceRuntime = tracer.stop();
        if (tracker != null) {
          tracker.recordPassStop(name, traceRuntime);
        }
        maybePrintAstHashcodes(name, root);
        maybeRunValidityCheck(name, externs, root);
      } catch (IllegalStateException e) {
        // TODO(johnlenz): Remove this once the normalization checks report
        // errors instead of exceptions.
        throw new RuntimeException("Validity check failed for " + name, e);
      }
    }

    @Override
    public String toString() {
      return "pass: " + name;
    }
  }

  boolean hasScopeChanged(Node n) {
    // Outside loops we don't track changed scopes, so we visit them all.
    if (!inLoop) {
      return true;
    }
    int timeOfLastRun = lastRuns.get(currentPass);
    // A pass looks at all functions when it first runs
    return timeOfLastRun == START_TIME
        || n.getChangeTime() > timeOfLastRun;
  }

  /**
   * A change handler that marks scopes as changed when reportChange is called.
   */
  private class ScopedChangeHandler implements CodeChangeHandler {
    private int lastCodeChangeQuery;

    ScopedChangeHandler() {
      this.lastCodeChangeQuery = compiler.getChangeStamp();
    }

    @Override
    public void reportChange() {
      lastChange = compiler.getChangeStamp();
    }

    private boolean hasCodeChangedSinceLastCall() {
      boolean result = lastChange > lastCodeChangeQuery;
      lastCodeChangeQuery = compiler.getChangeStamp();
      // The next call to the method will happen at a different time
      compiler.incrementChangeStamp();
      return result;
    }
  }

  /**
   * A compound pass that contains atomic passes and runs them until they reach
   * a fixed point.
   * <p>
   * Notice that this is a non-static class, because it includes the closure
   * of PhaseOptimizer.
   */
  @VisibleForTesting
  class Loop implements CompilerPass {
    private final List<NamedPass> myPasses = new ArrayList<>();
    private final Set<String> myNames = new HashSet<>();
    private ScopedChangeHandler scopeHandler;
    private boolean isCodeRemovalLoop = false;
    private int howmanyIterationsUnderThreshold = 0;

    void addLoopedPass(PassFactory factory) {
      String name = factory.getName();
      Preconditions.checkArgument(!myNames.contains(name),
          "Already a pass with name '%s' in this loop", name);
      myNames.add(name);
      myPasses.add(new NamedPass(factory));
    }

    @Override
    public void process(Node externs, Node root) {
      checkState(!inLoop, "Nested loops are forbidden");
      inLoop = true;
      optimizePasses();
      this.isCodeRemovalLoop = isCodeRemovalLoop();

      // Set up function-change tracking
      scopeHandler = new ScopedChangeHandler();
      compiler.addChangeHandler(scopeHandler);

      // lastRuns is initialized before each loop. This way, when a pass is run
      // in the 2nd loop for the 1st time, it looks at all scopes.
      lastRuns = new HashMap<>();
      for (NamedPass pass : myPasses) {
        lastRuns.put(pass, START_TIME);
      }
      // Contains a pass iff it made changes the last time it was run.
      Set<NamedPass> madeChanges = new HashSet<>();
      // Contains a pass iff it was run during the last inner loop.
      Set<NamedPass> runInPrevIter = new HashSet<>();
      State state = State.RUN_PASSES_NOT_RUN_IN_PREV_ITER;
      boolean lastIterMadeChanges;
      int count = 1;
      int astSize = NodeUtil.countAstSize(root);
      int previousAstSize = astSize;

      // The loop starts at state RUN_PASSES_NOT_RUN_IN_PREV_ITER and runs all passes.
      // After that, it goes to state RUN_PASSES_THAT_CHANGED_STH_IN_PREV_ITER, and
      // runs several iterations in that state, until there are no longer any changes
      // to the AST, and then it goes back to RUN_PASSES_NOT_RUN_IN_PREV_ITER.
      // We call one sequence of RUN_PASSES_NOT_RUN_IN_PREV_ITER followed by
      // RUN_PASSES_THAT_CHANGED_STH_IN_PREV_ITER a batch.
      // At the end of every loop batch, if the batch made so few changes that the
      // changed percentage of the AST is below some threshold, we stop the loop
      // without waiting to reach a fixpoint.

      try {
        while (true) {
          if (count > optimizationLoopMaxIterations && this.isCodeRemovalLoop) {
            return;
          }
          if (count > MAX_LOOPS) {
            compiler.throwInternalError(OPTIMIZE_LOOP_ERROR, null);
          }
          count++;
          lastIterMadeChanges = false;
          for (NamedPass pass : myPasses) {
            if ((state == State.RUN_PASSES_NOT_RUN_IN_PREV_ITER
                    && !runInPrevIter.contains(pass))
                || (state == State.RUN_PASSES_THAT_CHANGED_STH_IN_PREV_ITER
                        && madeChanges.contains(pass))) {
              compiler.incrementChangeStamp();
              currentPass = pass;
              pass.process(externs, root);
              runInPrevIter.add(pass);
              lastRuns.put(pass, compiler.getChangeStamp());
              if (hasHaltingErrors()) {
                return;
              } else if (scopeHandler.hasCodeChangedSinceLastCall()) {
                madeChanges.add(pass);
                lastIterMadeChanges = true;
              } else {
                madeChanges.remove(pass);
              }
            } else {
              runInPrevIter.remove(pass);
            }
          }

          previousAstSize = astSize;
          astSize = NodeUtil.countAstSize(root);
          if (state == State.RUN_PASSES_NOT_RUN_IN_PREV_ITER) {
            if (lastIterMadeChanges && isAstSufficientlyChanging(previousAstSize, astSize)) {
              state = State.RUN_PASSES_THAT_CHANGED_STH_IN_PREV_ITER;
            } else {
              return;
            }
          } else {
            checkState(state == State.RUN_PASSES_THAT_CHANGED_STH_IN_PREV_ITER);
            if (!lastIterMadeChanges || !isAstSufficientlyChanging(previousAstSize, astSize)) {
              state = State.RUN_PASSES_NOT_RUN_IN_PREV_ITER;
            }
          }
        }
      } finally {
        inLoop = false;
        compiler.removeChangeHandler(scopeHandler);
      }
    }

    /**
     * If two loop batches in a row made the code less than 0.05% smaller than the previous
     * batches, stop before the fixpoint.
     * The 0.05% threshold is based on the following heuristic: 1% size difference matters
     * to our users. 0.1% size difference is borderline relevant. 0.05% difference
     * between loop batches is unlikely to grow the final output more than 0.1%.
     *
     * Use this criterion only for the two code-removing loops.
     * The code-motion loop may move code around but not remove code, so this criterion
     * is not correct for stopping early.
     *
     * NOTE: the size heuristic is not robust when passes in the code-removing loop increase
     * the AST size; all passes in the loop must make the code smaller. Otherwise, what may seem
     * like a small size difference may indeed be big changes, and we miss it because we don't
     * compute the AST size after each pass. This can happen because inlineFunctions may increase
     * code size, and relies on peephole and inlineVariables to clean up. As long as these passes
     * run after it in the loop, we should be OK.
     */
    private boolean isAstSufficientlyChanging(int oldAstSize, int newAstSize) {
      if (useSizeHeuristicToStopOptimizationLoop && this.isCodeRemovalLoop) {
        float percentChange = 100 * (Math.abs(newAstSize - oldAstSize) / (float) oldAstSize);
        if (percentChange < 0.05) {
          this.howmanyIterationsUnderThreshold++;
        } else {
          this.howmanyIterationsUnderThreshold = 0;
        }
        return this.howmanyIterationsUnderThreshold < 2;
      }
      return true;
    }

    /** Re-arrange the passes in an optimal order. */
    private void optimizePasses() {
      // It's important that this ordering is deterministic, so that
      // multiple compiles with the same input produce exactly the same
      // results.
      //
      // To do this, grab any passes we recognize, and move them to the end
      // in an "optimal" order.
      List<NamedPass> optimalPasses = new ArrayList<>();
      for (String passInOptimalOrder : OPTIMAL_ORDER) {
        for (NamedPass loopablePass : myPasses) {
          if (loopablePass.name.equals(passInOptimalOrder)) {
            optimalPasses.add(loopablePass);
            break;
          }
        }
      }

      myPasses.removeAll(optimalPasses);
      myPasses.addAll(optimalPasses);
    }

    boolean isPopulated() {
      return !myPasses.isEmpty();
    }

    private boolean isCodeRemovalLoop() {
      for (NamedPass pass : this.myPasses) {
        if (CODE_REMOVING_PASSES.contains(pass.name)) {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * An object used when running many NamedPass loopable passes as a Loop pass,
   * to keep track of how far along we are.
   */
  static class ProgressRange {
    public final double initialValue;
    public final double maxValue;

    public ProgressRange(double initialValue, double maxValue) {
      this.initialValue = initialValue;
      this.maxValue = maxValue;
    }
  }
}
