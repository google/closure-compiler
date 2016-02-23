/*
 * Copyright 2016 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.newtypes;

import com.google.common.collect.ImmutableList;

/**
 * Uses a counter to create unique names for functions, variables, type
 * variables, etc.
 * For a name foo, the generated name is of the form foo#COUNT; the original
 * name can be recovered from the unique name.
 */
public class UniqueNameGenerator {
  private int count;

  public UniqueNameGenerator() {
    this.count = 1;
  }

  public int getNextNumber() {
    return this.count++;
  }

  public String getNextName(String name) {
    return name + "#" + getNextNumber();
  }

  public static String findGeneratedName(String name, ImmutableList<String> names) {
    if (name.contains("#")) {
      return names.contains(name) ? name : null;
    }
    for (String name2 : names) {
      if (name.equals(getOriginalName(name2))) {
        return name2;
      }
    }
    return null;
  }

  public static String getOriginalName(String name) {
    return name.substring(0, name.indexOf('#'));
  }
}
