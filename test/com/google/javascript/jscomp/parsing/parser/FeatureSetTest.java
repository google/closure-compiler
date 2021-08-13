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
    assertFS(FeatureSet.ES_UNSUPPORTED).contains(FeatureSet.ES_NEXT_IN);
    assertFS(FeatureSet.ES_NEXT_IN).contains(FeatureSet.ES_NEXT);
    assertFS(FeatureSet.ES_NEXT).contains(FeatureSet.ES2021);
    assertFS(FeatureSet.ES2021).contains(FeatureSet.ES2020);
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
    assertFS(FeatureSet.ES2021_MODULES.without(Feature.MODULES)).equals(FeatureSet.ES2021);
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
    assertThat(FeatureSet.ES3.version()).isEqualTo("es3");
    assertThat(FeatureSet.ES5.version()).isEqualTo("es5");
    assertThat(FeatureSet.ES2015.version()).isEqualTo("es6");
    assertThat(FeatureSet.ES2015_MODULES.version()).isEqualTo("es6");
    assertThat(FeatureSet.ES2016.version()).isEqualTo("es7");
    assertThat(FeatureSet.ES2016_MODULES.version()).isEqualTo("es7");
    assertThat(FeatureSet.ES2017.version()).isEqualTo("es8");
    assertThat(FeatureSet.ES2017_MODULES.version()).isEqualTo("es8");
    assertThat(FeatureSet.ES2018.version()).isEqualTo("es9");
    assertThat(FeatureSet.ES2018_MODULES.version()).isEqualTo("es9");
    assertThat(FeatureSet.ES2019.version()).isEqualTo("es_2019");
    assertThat(FeatureSet.ES2019_MODULES.version()).isEqualTo("es_2019");
    assertThat(FeatureSet.ES2020.version()).isEqualTo("es_2020");
    assertThat(FeatureSet.ES2020_MODULES.version()).isEqualTo("es_2020");
    assertThat(FeatureSet.ES2021.version()).isEqualTo("es_2021");
    assertThat(FeatureSet.ES2021_MODULES.version()).isEqualTo("es_2021");
    assertThat(FeatureSet.ALL.version()).isEqualTo("all");
  }

  @Test
  public void testEsNextAndNewer() {
    // ES_NEXT, ES_NEXT_IN, and ES_UNSUPPORTED are moving targets that may or may not have any
    // unique features.  If any of these `FeatureSet`s are identical to a lower FeatureSet,
    // `version()` will return the lowest equivalent version that contains features.
    // This could be es_XXX, es_next, etc. and will change as new features are added and removed
    // from these `FeatureSet`s.
    assertThat(FeatureSet.ES_NEXT.version()).isEqualTo("es_next");
    assertThat(FeatureSet.ES_NEXT_IN.version()).isEqualTo("es_next");
    assertThat(FeatureSet.ES_UNSUPPORTED.version()).isEqualTo("es_next");
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
    assertFS(FeatureSet.valueOf("es_2021")).equals(FeatureSet.ES2021);
    assertFS(FeatureSet.valueOf("es_next")).equals(FeatureSet.ES_NEXT);
    assertFS(FeatureSet.valueOf("es_next_in")).equals(FeatureSet.ES_NEXT_IN);
    assertFS(FeatureSet.valueOf("es_unsupported")).equals(FeatureSet.ES_UNSUPPORTED);
    assertFS(FeatureSet.valueOf("all")).equals(FeatureSet.ALL);
    assertThrows(IllegalArgumentException.class, () -> FeatureSet.valueOf("bad"));
  }
}
