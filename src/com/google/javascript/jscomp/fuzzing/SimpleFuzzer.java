/*
 * Copyright 2013 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.fuzzing;

import com.google.common.collect.Sets;
import com.google.javascript.rhino.Node;

import java.util.Set;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
class SimpleFuzzer extends AbstractFuzzer {

  private int nodeType;
  private String configName;
  private Type dataType;

  SimpleFuzzer(int nodeType, String configName, Type dataType) {
    super(null);
    this.nodeType = nodeType;
    this.configName = configName;
    this.dataType = dataType;
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#generate(int)
   */
  @Override
  protected Node generate(int budget, Set<Type> types) {
    return new Node(nodeType);
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#isEnough(int)
   */
  @Override
  protected boolean isEnough(int budget) {
    return budget >= 1;
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
   */
  @Override
  protected String getConfigName() {
    return configName;
  }

  @Override
  protected Set<Type> supportedTypes() {
    return Sets.immutableEnumSet(dataType);
  }
}
