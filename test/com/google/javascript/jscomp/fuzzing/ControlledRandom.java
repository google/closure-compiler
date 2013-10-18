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

import com.google.common.collect.Maps;

import java.util.HashMap;
import java.util.Random;

/**
 * A random number generator that allow us to control the i-th random int value
 * @author zplin@google.com (Zhongpeng Lin)
 */
public class ControlledRandom extends Random {
  public ControlledRandom() {
    super(3);
  }
  /**
   * @param seed
   */
  public ControlledRandom(long seed) {
    super(seed);
  }

  private int count = 0;
  private final HashMap<Integer, Integer> overrides = Maps.newHashMap();

  @Override
  public int nextInt(int n) {
    count++;
    if (overrides.containsKey(count)) {
      return overrides.get(count);
    } else {
      return super.nextInt(n);
    }
  }

  /**
   * Override i-th random number with the value given
   */
  public void addOverride(int i, int value) {
    overrides.put(i, value);
  }
}
