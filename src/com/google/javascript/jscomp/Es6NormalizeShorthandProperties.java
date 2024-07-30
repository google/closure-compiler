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
package com.google.javascript.jscomp;

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;

/**
 * Normalizes shorthand object properties. This should be one of the first things done when
 * transpiling from ES6 down to ES5, as it allows all the following checks and transpilations to not
 * care about shorthand and destructured assignments.
 */
public final class Es6NormalizeShorthandProperties extends AbstractPeepholeTranspilation {

  Es6NormalizeShorthandProperties(AbstractCompiler compiler) {}

  @Override
  @SuppressWarnings("CanIgnoreReturnValueSuggester")
  Node transpileSubtree(Node subtree) {
    if (subtree.isStringKey()) {
      subtree.setShorthandProperty(false);
    }
    return subtree;
  }

  @Override
  FeatureSet getTranspiledAwayFeatures() {
    return FeatureSet.BARE_MINIMUM.with(Feature.EXTENDED_OBJECT_LITERALS);
  }
}
