/*
 * Copyright 2008 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.deps;

import java.util.Objects;

/**
 * Class used to represent a "virtual" file.
 */
public class VirtualFile implements SourceFile {
  private final String name;
  private final String code;

  public VirtualFile(String name, String code) {
    this.name = name;
    this.code = code;
  }

  @Override public String getName() {
    return name;
  }

  @Override public String getContent() {
    return code;
  }

  @Override public boolean wasModified() {
    return false;
  }

  @Override public int hashCode() {
    return Objects.hash(name, code);
  }

  @Override public boolean equals(Object o) {
    if (o instanceof VirtualFile) {
      VirtualFile vf = (VirtualFile) o;
      return Objects.equals(name, vf.name)
          && Objects.equals(code, vf.code);
    }
    return false;
  }
}
