/*
 * Copyright 2015 The Closure Compiler Authors.
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
import static com.google.common.base.Throwables.throwIfUnchecked;

import com.google.common.annotations.GwtIncompatible;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Run the compiler in a separate thread with a larger stack */
class CompilerExecutor {
  // We use many recursive algorithms that use O(d) memory in the depth
  // of the tree.
  // Also, (de)serialization between phases can involve a lot of recursion.
  static final long COMPILER_STACK_SIZE = (1 << 26); // About 64MB

  /**
   * Use a dedicated compiler thread per Compiler instance.
   */
  private Thread compilerThread = null;

  /** Whether to use threads. */
  private boolean useThreads = true;

  private int timeout = 0;

  /**
   * Under JRE 1.6, the JS Compiler overflows the stack when running on some
   * large or complex JS code. When threads are available, we run all compile
   * jobs on a separate thread with a larger stack.
   *
   * That way, we don't have to increase the stack size for *every* thread
   * (which is what -Xss does).
   */
  @GwtIncompatible("java.util.concurrent.ExecutorService")
  ExecutorService getExecutorService() {
    return getDefaultExecutorService();
  }

  static ExecutorService getDefaultExecutorService() {
    return Executors.newSingleThreadExecutor(new ThreadFactory() {
      @Override
      public Thread newThread(Runnable r) {
        Thread t = new Thread(null, r, "jscompiler", COMPILER_STACK_SIZE);
        t.setDaemon(true);  // Do not prevent the JVM from exiting.
        return t;
      }
    });
  }

  void disableThreads() {
    useThreads = false;
  }

  void setTimeout(int timeout) {
    this.timeout = timeout;
  }

  @SuppressWarnings("unchecked")
  <T> T runInCompilerThread(final Callable<T> callable, final boolean dumpTraceReport) {
    ExecutorService executor = getExecutorService();
    T result = null;
    final Throwable[] exception = new Throwable[1];

    checkState(
        compilerThread == null || compilerThread == Thread.currentThread(),
        "Please do not share the Compiler across threads");

    // If the compiler thread is available, use it.
    if (useThreads && compilerThread == null) {
      try {
        Callable<T> bootCompilerThread = new Callable<T>() {
          @Override
          public T call() {
            try {
              compilerThread = Thread.currentThread();
              if (dumpTraceReport) {
                Tracer.initCurrentThreadTrace();
              }
              return callable.call();
            } catch (Throwable e) {
              exception[0] = e;
            } finally {
              compilerThread = null;
              if (dumpTraceReport) {
                Tracer.logCurrentThreadTrace();
              }
              Tracer.clearCurrentThreadTrace();
            }
            return null;
          }
        };

        Future<T> future = executor.submit(bootCompilerThread);
        if (timeout > 0) {
          result = future.get(timeout, TimeUnit.SECONDS);
        } else {
          result = future.get();
        }
      } catch (InterruptedException | TimeoutException | ExecutionException e) {
        throw new RuntimeException(e);
      } finally {
        executor.shutdown();
      }
    } else {
      try {
        result = callable.call();
      } catch (Exception e) {
        exception[0] = e;
      }
    }

    // Pass on any exception caught by the runnable object.
    if (exception[0] != null) {
      throwIfUnchecked(exception[0]);
      throw new RuntimeException(exception[0]);
    }

    return result;
  }
}
