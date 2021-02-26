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

import static com.google.javascript.jscomp.parsing.parser.testing.FeatureSetSubject.assertFS;

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link SyncCompilerFeatures} */
@RunWith(JUnit4.class)
public final class SyncCompilerFeaturesTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new SyncCompilerFeatures(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    return super.getOptions();
  }

  @Test
  public void emptyExternsAndSourcesYieldsBareMinimumFeatureSet() {
    testSame(externs(""), srcs(""));

    assertFS(getLastCompiler().getFeatureSet()).equals(FeatureSet.BARE_MINIMUM);
  }

  @Test
  public void featuresUsedInExternsAndSourcesAreMergedIntoCompilersFeatureSet() {
    testSame(externs("class C {}"), srcs("import.meta.url"));

    assertFS(getLastCompiler().getFeatureSet())
        .equals(
            FeatureSet.BARE_MINIMUM.with(Feature.CLASSES, Feature.IMPORT_META, Feature.MODULES));
  }
}
