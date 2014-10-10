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

import com.google.gson.JsonObject;

import java.util.Random;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
class FuzzingContext {
  Random random;
  ScopeManager scopeManager;
  JsonObject config;
  boolean strict;
  StringNumberGenerator snGenerator;

  FuzzingContext(Random random, JsonObject config, boolean strict) {
    this.random = random;
    this.scopeManager = new ScopeManager(random);
    this.snGenerator = new StringNumberGenerator(random);
    this.config = config;
    this.strict = strict;
  }
}
