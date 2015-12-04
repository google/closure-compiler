/*
 * Copyright 2015 The Closure Compiler Authors.
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

package com.google.debugging.sourcemap.proto;

/** GWT compatible no-op replacement of {@code Mapping} */
public final class Mapping {
  public static final class OriginalMapping {
    public static final class Builder {
      public Builder setOriginalFile(String value) {
        throw new UnsupportedOperationException(
            "Mapping.OriginalMapping.Builder.setOriginalFile not implemented");
      }

      public Builder setColumnPosition(int value) {
        throw new UnsupportedOperationException(
            "Mapping.OriginalMapping.Builder.setColumnPosition not implemented");
      }

      public OriginalMapping build() {
        throw new UnsupportedOperationException(
            "Mapping.OriginalMapping.Builder.build not implemented");
      }
    }

    public String getOriginalFile() {
      throw new UnsupportedOperationException(
          "Mapping.OriginalMapping.getOriginalFile not implemented");
    }

    public int getLineNumber() {
      throw new UnsupportedOperationException(
          "Mapping.OriginalMapping.getLineNumber not implemented");
    }

    public int getColumnPosition() {
      throw new UnsupportedOperationException(
          "Mapping.OriginalMapping.getColumnPosition not implemented");
    }

    public Builder toBuilder() {
      throw new UnsupportedOperationException("Mapping.OriginalMapping.toBuilder not implemented");
    }
  }
}
