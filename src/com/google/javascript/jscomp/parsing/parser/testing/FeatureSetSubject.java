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
package com.google.javascript.jscomp.parsing.parser.testing;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import javax.annotation.CheckReturnValue;

/**
 * A Truth Subject for FeatureSet. Usage:
 * <pre>
 *   import static com.google.javascript.jscomp.parsing.parser.testing.FeatureSetSubject.assertFS;
 *   ...
 *   assertFS(features).contains(otherFeatures);
 * </pre>
 */
public class FeatureSetSubject extends Subject<FeatureSetSubject, FeatureSet> {
  private static final Subject.Factory<FeatureSetSubject, FeatureSet> FACTORY =
    FeatureSetSubject::new;

  @CheckReturnValue
  public static FeatureSetSubject assertFS(FeatureSet fs) {
    return assertAbout(FACTORY).that(fs);
  }

  public FeatureSetSubject(FailureMetadata failureMetadata, FeatureSet featureSet) {
    super(failureMetadata, featureSet);
  }

  public void contains(FeatureSet other) {
    if (!actual().contains(other)) {
      failWithRawMessage("Expected a FeatureSet containing: %s\nBut got: %s", other, actual());
    }
  }

  public void has(Feature feature) {
    if (!actual().has(feature)) {
      failWithRawMessage("Expected a FeatureSet that has: %s\nBut got: %s", feature, actual());
    }
  }

  public void doesNotHave(Feature feature) {
    if (actual().has(feature)) {
      failWithRawMessage(
          "Expected a FeatureSet that doesn't have: %s\nBut got: %s", feature, actual());
    }
  }
}
