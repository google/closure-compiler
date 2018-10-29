/*
 * Copyright 2017 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.parsing.parser;

import static com.google.javascript.jscomp.parsing.parser.testing.FeatureSetSubject.assertFS;

import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link FeatureSet}. */
@RunWith(JUnit4.class)
public final class FeatureSetTest {
  @Test
  public void testContains() {
    assertFS(FeatureSet.TYPESCRIPT).has(Feature.AMBIENT_DECLARATION);
    assertFS(FeatureSet.TYPESCRIPT).has(Feature.MODULES);
  }

  @Test
  public void testWithoutModules() {
    assertFS(FeatureSet.TYPESCRIPT.without(Feature.MODULES)).has(Feature.AMBIENT_DECLARATION);
    assertFS(FeatureSet.TYPESCRIPT.without(Feature.MODULES)).doesNotHave(Feature.MODULES);
  }

  @Test
  public void testWithoutTypes() {
    assertFS(FeatureSet.TYPESCRIPT.withoutTypes()).doesNotHave(Feature.AMBIENT_DECLARATION);
    assertFS(FeatureSet.TYPESCRIPT.withoutTypes()).has(Feature.MODULES);
  }
}
