/*
 * Copyright 2019 The Closure Compiler Authors.
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

import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * A helper class to prebuild DependencyInfo from a list of {@link CompilerInput}. Dependency info
 * (requires, annotations, etc.) is parsed and generated the first time we try to get it for an
 * input. Get them all now using multiple threads, so they'll be parsed in parallel and already
 * available during the rest of the compilation.
 */
class PrebuildDependencyInfo {
  private final int numParallelThreads;

  PrebuildDependencyInfo(int numParalleThreads) {
    this.numParallelThreads = numParalleThreads;
  }

  void prebuild(Iterable<CompilerInput> allInputs) {
    ThreadFactory threadFactory =
        new ThreadFactory() {
          @Override
          public Thread newThread(Runnable r) {
            Thread t =
                new Thread(
                    null,
                    r,
                    "jscompiler-PrebuildDependencyInfo",
                    CompilerExecutor.COMPILER_STACK_SIZE);
            t.setDaemon(true); // Do not prevent the JVM from exiting.
            return t;
          }
        };
    ThreadPoolExecutor poolExecutor =
        new ThreadPoolExecutor(
            numParallelThreads,
            numParallelThreads,
            Integer.MAX_VALUE,
            SECONDS,
            new LinkedBlockingQueue<Runnable>(),
            threadFactory);
    ListeningExecutorService executorService = MoreExecutors.listeningDecorator(poolExecutor);
    List<ListenableFuture<?>> futureList = new ArrayList<>(Iterables.size(allInputs));
    // TODO(moz): Support canceling all parsing on the first halting error
    for (final CompilerInput input : allInputs) {
      futureList.add(executorService.submit(input::getDependencyInfo));
    }

    poolExecutor.shutdown();
    try {
      Futures.allAsList(futureList).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }
}
