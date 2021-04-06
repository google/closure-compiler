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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.parsing.parser.testing.FeatureSetSubject.assertFS;
import static com.google.javascript.rhino.testing.Asserts.assertThrows;

import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link FeatureSet}. */
@RunWith(JUnit4.class)
public final class FeatureSetTest {
  @Test
  public void testContains() {
    assertFS(FeatureSet.ALL).has(Feature.TYPE_ANNOTATION);
    assertFS(FeatureSet.ALL).has(Feature.MODULES);
  }

  @Test
  public void testWithoutModules() {
    assertFS(FeatureSet.ALL.without(Feature.MODULES)).has(Feature.TYPE_ANNOTATION);
    assertFS(FeatureSet.ALL.without(Feature.MODULES)).doesNotHave(Feature.MODULES);
  }

  @Test
  public void testWithoutTypes() {
    assertFS(FeatureSet.ALL.withoutTypes()).doesNotHave(Feature.TYPE_ANNOTATION);
    assertFS(FeatureSet.ALL.withoutTypes()).has(Feature.MODULES);
  }

  @Test
  public void testEsOrdering() {
    assertFS(FeatureSet.ALL).contains(FeatureSet.ES_UNSUPPORTED);
    assertFS(FeatureSet.ES_UNSUPPORTED).contains(FeatureSet.ES_NEXT);
    assertFS(FeatureSet.ES_NEXT_IN).contains(FeatureSet.ES_NEXT);
    assertFS(FeatureSet.ES_NEXT).contains(FeatureSet.ES2020);
    assertFS(FeatureSet.ES2020).contains(FeatureSet.ES2019);
    assertFS(FeatureSet.ES2019).contains(FeatureSet.ES2018);
    assertFS(FeatureSet.ES2018).contains(FeatureSet.ES2017);
    assertFS(FeatureSet.ES2017).contains(FeatureSet.ES2016);
    assertFS(FeatureSet.ES2016).contains(FeatureSet.ES2015);
    assertFS(FeatureSet.ES2015).contains(FeatureSet.ES5);
    assertFS(FeatureSet.ES5).contains(FeatureSet.ES3);
    assertFS(FeatureSet.ES3).contains(FeatureSet.BARE_MINIMUM);
  }

  @Test
  public void testEsModuleOrdering() {
    assertFS(FeatureSet.ES2020_MODULES.without(Feature.MODULES)).equals(FeatureSet.ES2020);
    assertFS(FeatureSet.ES2019_MODULES.without(Feature.MODULES)).equals(FeatureSet.ES2019);
    assertFS(FeatureSet.ES2018_MODULES.without(Feature.MODULES)).equals(FeatureSet.ES2018);
    assertFS(FeatureSet.ES2017_MODULES.without(Feature.MODULES)).equals(FeatureSet.ES2017);
    assertFS(FeatureSet.ES2016_MODULES.without(Feature.MODULES)).equals(FeatureSet.ES2016);
    assertFS(FeatureSet.ES2015_MODULES.without(Feature.MODULES)).equals(FeatureSet.ES2015);
  }

  @Test
  public void testVersionForDebugging() {
    // ES_NEXT, ES_UNSUPPORTED are tested separately - see below
    assertThat(FeatureSet.ES3.versionForDebugging()).isEqualTo("es3");
    assertThat(FeatureSet.ES5.versionForDebugging()).isEqualTo("es5");
    assertThat(FeatureSet.ES2015.versionForDebugging()).isEqualTo("es6");
    assertThat(FeatureSet.ES2015_MODULES.versionForDebugging()).isEqualTo("es6");
    assertThat(FeatureSet.ES2016.versionForDebugging()).isEqualTo("es7");
    assertThat(FeatureSet.ES2016_MODULES.versionForDebugging()).isEqualTo("es7");
    assertThat(FeatureSet.ES2017.versionForDebugging()).isEqualTo("es8");
    assertThat(FeatureSet.ES2017_MODULES.versionForDebugging()).isEqualTo("es8");
    assertThat(FeatureSet.ES2018.versionForDebugging()).isEqualTo("es9");
    assertThat(FeatureSet.ES2018_MODULES.versionForDebugging()).isEqualTo("es9");
    assertThat(FeatureSet.ES2019.versionForDebugging()).isEqualTo("es_2019");
    assertThat(FeatureSet.ES2019_MODULES.versionForDebugging()).isEqualTo("es_2019");
    assertThat(FeatureSet.ES2020.versionForDebugging()).isEqualTo("es_2020");
    assertThat(FeatureSet.ES2020_MODULES.versionForDebugging()).isEqualTo("es_2020");
    assertThat(FeatureSet.ALL.versionForDebugging()).isEqualTo("all");
  }

  @Test
  public void testEsNext() {
    // ES_NEXT currently has no feature, hence ES_2020 will be returned by versionForDebugging().
    // This will change when new features are added to ES_NEXT/ES_2021, so this test case will
    // then have to change.
    // This is on purpose so the test case serves as documentation that we intentionally
    // have ES_NEXT the same as or different from the latest supported ES version.
    assertThat(FeatureSet.ES_NEXT.versionForDebugging()).isEqualTo("es_2020");
  }

  @Test
  public void testEsNextIn() {
    // ES_NEXT_IN currently has one or more features that are not in other feature sets, so its name
    // will be returned by versionForDebugging().
    // This will change when those features are added to ES_NEXT/ES_XXXX, so this test case will
    // then have to change.
    // This is on purpose so the test case serves as documentation that we intentionally
    // have ES_NEXT_IN the same as or different from the latest supported ES version.
    assertThat(FeatureSet.ES_NEXT_IN.versionForDebugging()).isEqualTo("es_next_in");
  }

  @Test
  public void testEsUnsupported() {
    // ES_UNSUPPORTED is currently has the same features as ES_NEXT_IN, so versionForDebugging()
    // will
    // return es_next_in
    // This will change when new features are added to ES_NEXT/ES_UNSUPPORTED, so this test case
    // will then have to change.
    // This is on purpose so the test case serves as documentation that we intentionally
    // have ES_UNSUPPORTED the same as or different from ES_NEXT
    assertThat(FeatureSet.ES_UNSUPPORTED.versionForDebugging()).isEqualTo("es_next_in");
  }

  @Test
  public void testValueOf() {
    assertFS(FeatureSet.valueOf("es3")).equals(FeatureSet.ES3);
    assertFS(FeatureSet.valueOf("es5")).equals(FeatureSet.ES5);
    assertFS(FeatureSet.valueOf("es6")).equals(FeatureSet.ES2015);
    assertFS(FeatureSet.valueOf("es7")).equals(FeatureSet.ES2016);
    assertFS(FeatureSet.valueOf("es8")).equals(FeatureSet.ES2017);
    assertFS(FeatureSet.valueOf("es_2018")).equals(FeatureSet.ES2018);
    assertFS(FeatureSet.valueOf("es9")).equals(FeatureSet.ES2018);
    assertFS(FeatureSet.valueOf("es_2019")).equals(FeatureSet.ES2019);
    assertFS(FeatureSet.valueOf("es_2020")).equals(FeatureSet.ES2020);
    assertFS(FeatureSet.valueOf("es_next")).equals(FeatureSet.ES_NEXT);
    assertFS(FeatureSet.valueOf("es_next_in")).equals(FeatureSet.ES_NEXT_IN);
    assertFS(FeatureSet.valueOf("es_unsupported")).equals(FeatureSet.ES_UNSUPPORTED);
    assertFS(FeatureSet.valueOf("all")).equals(FeatureSet.ALL);
    assertThrows(IllegalArgumentException.class, () -> FeatureSet.valueOf("bad"));
  }
}
