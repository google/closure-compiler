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

  @Test
  public void testEsOrdering() {
    assertFS(FeatureSet.TS_UNSUPPORTED).contains(FeatureSet.TYPESCRIPT);
    assertFS(FeatureSet.TYPESCRIPT).contains(FeatureSet.ES_NEXT);
    assertFS(FeatureSet.ES_UNSUPPORTED).contains(FeatureSet.ES_NEXT);
    assertFS(FeatureSet.ES_NEXT_IN).contains(FeatureSet.ES_NEXT);
    assertFS(FeatureSet.ES_NEXT).contains(FeatureSet.ES2020);
    assertFS(FeatureSet.ES2020).contains(FeatureSet.ES2019);
    assertFS(FeatureSet.ES2019).contains(FeatureSet.ES2018);
    assertFS(FeatureSet.ES2018).contains(FeatureSet.ES8);
    assertFS(FeatureSet.ES8).contains(FeatureSet.ES7);
    assertFS(FeatureSet.ES7).contains(FeatureSet.ES6);
    assertFS(FeatureSet.ES6).contains(FeatureSet.ES5);
    assertFS(FeatureSet.ES5).contains(FeatureSet.ES3);
    assertFS(FeatureSet.ES3).contains(FeatureSet.BARE_MINIMUM);
  }

  @Test
  public void testEsModuleOrdering() {
    assertFS(FeatureSet.ES2020_MODULES.without(Feature.MODULES)).equals(FeatureSet.ES2020);
    assertFS(FeatureSet.ES2019_MODULES.without(Feature.MODULES)).equals(FeatureSet.ES2019);
    assertFS(FeatureSet.ES2018_MODULES.without(Feature.MODULES)).equals(FeatureSet.ES2018);
    assertFS(FeatureSet.ES8_MODULES.without(Feature.MODULES)).equals(FeatureSet.ES8);
    assertFS(FeatureSet.ES7_MODULES.without(Feature.MODULES)).equals(FeatureSet.ES7);
    assertFS(FeatureSet.ES6_MODULES.without(Feature.MODULES)).equals(FeatureSet.ES6);
  }

  @Test
  public void testVersionForDebugging() {
    // ES_NEXT, ES_UNSUPPORTED, TS_UNSUPPORTED are tested separately - see below
    assertThat(FeatureSet.ES3.versionForDebugging()).isEqualTo("es3");
    assertThat(FeatureSet.ES5.versionForDebugging()).isEqualTo("es5");
    assertThat(FeatureSet.ES6.versionForDebugging()).isEqualTo("es6");
    assertThat(FeatureSet.ES6_MODULES.versionForDebugging()).isEqualTo("es6");
    assertThat(FeatureSet.ES7.versionForDebugging()).isEqualTo("es7");
    assertThat(FeatureSet.ES7_MODULES.versionForDebugging()).isEqualTo("es7");
    assertThat(FeatureSet.ES8.versionForDebugging()).isEqualTo("es8");
    assertThat(FeatureSet.ES8_MODULES.versionForDebugging()).isEqualTo("es8");
    assertThat(FeatureSet.ES2018.versionForDebugging()).isEqualTo("es9");
    assertThat(FeatureSet.ES2018_MODULES.versionForDebugging()).isEqualTo("es9");
    assertThat(FeatureSet.ES2019.versionForDebugging()).isEqualTo("es_2019");
    assertThat(FeatureSet.ES2019_MODULES.versionForDebugging()).isEqualTo("es_2019");
    assertThat(FeatureSet.ES2020.versionForDebugging()).isEqualTo("es_2020");
    assertThat(FeatureSet.ES2020_MODULES.versionForDebugging()).isEqualTo("es_2020");
    assertThat(FeatureSet.TYPESCRIPT.versionForDebugging()).isEqualTo("ts");
  }

  @Test
  public void testEsNext() {
    // ES_NEXT currently has no features so es_2020
    // will be returned by versionForDebugging().
    // This will change when new features are added to ES_NEXT/ES_2020, so this test case will
    // then have to change.
    // This is on purpose so the test case serves as documentation that we intentionally
    // have ES_NEXT the same as or different from the latest supported ES version.
    assertThat(FeatureSet.ES_NEXT.versionForDebugging()).isEqualTo("es_2020");
  }

  @Test
  public void testEsUnsupported() {
    // ES_UNSUPPORTED is currently has more features than ES_NEXT, so versionForDebugging() will
    // return es_unsupported
    // This will change when new features are added to ES_NEXT/ES_UNSUPPORTED, so this test case
    // will then have to change.
    // This is on purpose so the test case serves as documentation that we intentionally
    // have ES_UNSUPPORTED the same as or different from ES_NEXT
    assertThat(FeatureSet.ES_UNSUPPORTED.versionForDebugging()).isEqualTo("es_unsupported");
  }

  @Test
  public void testTsUnsupported() {
    // TS_UNSUPPORTED is currently has more features than TYPESCRIPT, so versionForDebugging() will
    // return ts_unsupported
    // This will change when new features are added to TYPESCRIPT/TS_UNSUPPORTED, so this test case
    // will then have to change.
    // This is on purpose so the test case serves as documentation that we intentionally
    // have TS_UNSUPPORTED the same as or different from TYPESCRIPT
    assertThat(FeatureSet.TS_UNSUPPORTED.versionForDebugging()).isEqualTo("ts_unsupported");
  }

  @Test
  public void testValueOf() {
    assertFS(FeatureSet.valueOf("es3")).equals(FeatureSet.ES3);
    assertFS(FeatureSet.valueOf("es5")).equals(FeatureSet.ES5);
    assertFS(FeatureSet.valueOf("es6")).equals(FeatureSet.ES6);
    assertFS(FeatureSet.valueOf("es7")).equals(FeatureSet.ES7);
    assertFS(FeatureSet.valueOf("es8")).equals(FeatureSet.ES8);
    assertFS(FeatureSet.valueOf("es2018")).equals(FeatureSet.ES2018);
    assertFS(FeatureSet.valueOf("es9")).equals(FeatureSet.ES2018);
    assertFS(FeatureSet.valueOf("es_2019")).equals(FeatureSet.ES2019);
    assertFS(FeatureSet.valueOf("es_2020")).equals(FeatureSet.ES2020);
    assertFS(FeatureSet.valueOf("es_next")).equals(FeatureSet.ES_NEXT);
    assertFS(FeatureSet.valueOf("es_next_in")).equals(FeatureSet.ES_NEXT_IN);
    assertFS(FeatureSet.valueOf("es_unsupported")).equals(FeatureSet.ES_UNSUPPORTED);
    assertFS(FeatureSet.valueOf("ts")).equals(FeatureSet.TYPESCRIPT);
    assertThrows(IllegalArgumentException.class, () -> FeatureSet.valueOf("bad"));
  }
}
