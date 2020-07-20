/*
 * Copyright 2020 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.colors;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableCollection;

/**
 * A user-defined object type. For now each type is defined by a unique class name and file source.
 */
@AutoValue
public abstract class ObjectColor implements Color {

  public static ObjectColor create(String className, String fileName) {
    return new AutoValue_ObjectColor(className, fileName);
  }

  @Override
  public boolean isPrimitive() {
    return false;
  }

  @Override
  public boolean isUnion() {
    return false;
  }

  @Override
  public boolean isObject() {
    return true;
  }

  @Override
  public ImmutableCollection<Color> getAlternates() {
    throw new UnsupportedOperationException();
  }

  public abstract String getClassName();

  public abstract String getFilename();
}
