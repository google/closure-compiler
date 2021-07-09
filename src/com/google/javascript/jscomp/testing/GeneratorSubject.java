/*
 * Copyright 2021 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.testing;

import static com.google.common.truth.Truth.assertAbout;
import static java.util.stream.Collectors.joining;

import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Subject for a lazy sequence of values. */
public final class GeneratorSubject extends Subject {

  /** Create some result, optionally based on an index. */
  public interface Generator<U> {
    U generate(int index);
  }

  private static final int MAX_GENERATION_COUNT = 1000;

  private final Generator<?> source;

  private GeneratorSubject(FailureMetadata metadata, Generator<?> source) {
    super(metadata, source);
    this.source = source;
  }

  public static Factory<GeneratorSubject, Generator<?>> generators() {
    return GeneratorSubject::new;
  }

  public static GeneratorSubject assertGenerator(Generator<?> actual) {
    return assertAbout(generators()).that(actual);
  }

  public void generatesAtLeast(Object... expected) {
    LinkedHashSet<Object> expectedSet = new LinkedHashSet<>();
    Collections.addAll(expectedSet, expected);

    LinkedHashSet<Object> missingSet = new LinkedHashSet<>(expectedSet);
    LinkedHashSet<Object> foundSet = new LinkedHashSet<>();
    LinkedHashSet<Object> extraSet = new LinkedHashSet<>();

    int i = 0;
    for (; i < MAX_GENERATION_COUNT; i++) {
      if (missingSet.isEmpty()) {
        return;
      }

      Object v = this.source.generate(i);
      if (expectedSet.contains(v)) {
        foundSet.add(v);
        missingSet.remove(v);
      } else {
        extraSet.add(v);
      }
    }

    failWithoutActual(
        Fact.fact("total generation count", i + 1),
        Fact.fact("found expected", renderSet(foundSet)),
        Fact.fact("still missing", renderSet(missingSet)),
        Fact.fact("found extras", renderSet(extraSet)));
  }

  private static String renderSet(Set<?> set) {
    return "[" + set.stream().map(Object::toString).collect(joining(", ")) + "]";
  }
}
