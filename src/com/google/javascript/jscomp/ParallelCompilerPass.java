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

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.javascript.rhino.Node;

import java.util.List;

/**
 * Pass that splits the AST and spawns workers to process the pieces in
 * different threads.  The implementation uses {@link AstParallelizer} to
 * spread the work so multiple {@link Task}s can execute in parallel without
 * running into race-conditions.
 *
 */
final class ParallelCompilerPass implements CompilerPass {

  private final AstParallelizer splitter;
  private final AbstractCompiler compiler;
  private final int numWorkers;
  private final Supplier<Task> taskSupply;

  private List<Node> worklist;

  /**
   * Similar to {@link CompilerPass} except tasks are not given reference to
   * externs because of possible race conditions since node mutation is usually
   * not atomic. Tasks should not directly call anything from the Compiler,
   * instead they should communicate its findings as a {@link Result} object.
   */
  public static interface Task {
    // TODO(user): Its hard to enforces the tasks are not calling the non-
    // thread-safe methods in Compiler. We should consider not giving Compiler
    // passes a reference to compiler and have them communicate strictly with
    // Result objects.
    public Result processSubtree(Node subtree);
  }

  /**
   * Holds all the information about the ending result of a compilation task on
   * the subtree.
   */
  public static class Result {
    boolean changed = false;
    List<JSError> errors = Lists.newArrayList();
    List<Exception> exceptions = Lists.newArrayList();

    /**
     * creates a result without any error, exceptions or changes to the AST.
     */
    public Result() {}

    /**
     * Creates a result without any error or exceptions.
     */
    public Result(boolean changed) {
      this.changed = changed;
    }

    /**
     * Combines two results.
     */
    private void combine(Result other) {
      changed = changed || other.changed;
      errors.addAll(other.errors);
      exceptions.addAll(other.exceptions);
    }

    /**
     * Inform the compiler of all the changes this result object had recorded.
     * It might trigger a {@link Compiler#recentChange}, re-throw all the
     * exceptions that was thrown or report errors/warnings with
     * {@link Compiler#report(JSError)}. This is typically called after all
     * the worker thread has finished executed.
     */
    public void notifyCompiler(AbstractCompiler c) {
      if (!exceptions.isEmpty()) {
        throw new RuntimeException(exceptions.get(0));
      }
      for (JSError error : errors) {
        c.report(error);
      }
      if (changed) {
        c.reportCodeChange();
      }
    }
  }

  /**
   * Constructor.
   *
   * @param splitter Will be used to split up the AST into smaller subtrees.
   * @param taskSupply A supplier of tasks that should be thread-safe.
   * @param numWorkers Number of worker thread.
   */
  public ParallelCompilerPass(AbstractCompiler compiler,
      AstParallelizer splitter, Supplier<Task> taskSupply, int numWorkers) {
    Preconditions.checkArgument(numWorkers > 0);
    this.taskSupply = taskSupply;
    this.splitter = splitter;
    this.numWorkers = numWorkers;
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    // List of subtree to work with.
    worklist = splitter.split();
    Result r = execute();
    splitter.join();
    r.notifyCompiler(compiler);
  }

  /**
   * Main loop that creates the worker threads and do work.
   *
   * @return the combined result of all task execution on the work list.
   */
  private Result execute() {
    int numChildThread = numWorkers - 1;
    Thread[] workers = new Thread[numChildThread];
    final Result[] results = new Result[numChildThread];

    for (int i = 0; i < numChildThread; i++) {
      final int index = i;
      Thread worker = new Thread() {
        @Override
          public void run() {
            results[index] = processAllTasks();
          }
       };
       workers[i] = worker;
       worker.start();
    }

    Result result = processAllTasks();

    // Wait for the child to finish.
    for (int i = 0; i < numChildThread; i++) {
      try {
        workers[i].join();
      } catch (InterruptedException e) {
        // None of the children thread should be interrupted in the execution
        // of this method. If, for whatever reason, this happens, we will
        // re-throw it and investigate the cause.
        result.exceptions.add(e);
        // One of our thread was interrupted, we'll make the current thread to
        // be interrupted so any callers that is interested in cancellable
        // execution can cancel.
        Thread.currentThread().interrupt();
      }
    }

    // Combine the result.
    for (int i = 0; i < numChildThread; i++) {
      result.combine(results[i]);
    }

    return result;
  }

  private Result processAllTasks() {
    Result result = new Result();
    while(true) {
      Result passResult = processTask();
      if (passResult == null) {
        break;
      } else {
        result.combine(passResult);
      }
    }
    return result;
  }

  /**
   * Get a subtree from the work list and work on it. This method makes a call
   * to the supplier which is also assumed thread-safe. It also calls
   * getTask() which is synchronized.
   *
   * @return The result of performing the task specified by the task supplier
   * on the next subtree from the work list. {@code null} if there are no more
   * work load from the work list.
   */
  private Result processTask() {
    Node subtree = getTask();
    try {
      if (subtree == null) {
        return null;
      } else {
        return taskSupply.get().processSubtree(subtree);
      }
    } catch (Exception e) {
      Result r = new Result(true);
      r.exceptions.add(e);
      return r;
    }
  }

  /** Retrieves a subtree to work on from the work list. This must be atomic. */
  private synchronized Node getTask() {
    // Since the method is protected by a lock, there is no need for a thread-
    // safe list.
    if (worklist.isEmpty()) {
      return null;
    } else {
      return worklist.remove(0);
    }
  }
}
