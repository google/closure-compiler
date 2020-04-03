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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.Node;

/** Sets compiler feature set to features used in the externs and sources */
public final class SyncCompilerFeatures implements CompilerPass {
  private final AbstractCompiler compiler;

  SyncCompilerFeatures(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    FeatureSet featureSet = FeatureSet.BARE_MINIMUM;
    for (Node rootNode : ImmutableList.of(externs, root)) {
      for (Node childNode = rootNode.getFirstChild();
          childNode != null;
          childNode = childNode.getNext()) {
        checkState(childNode.isScript());
        FeatureSet featuresInScript = NodeUtil.getFeatureSetOfScript(childNode);
        if (featuresInScript != null) {
          featureSet = featureSet.with(featuresInScript);
        }
      }
    }
    compiler.setFeatureSet(featureSet);
  }
}
