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

package com.google.javascript.jscomp;

/** GWT compatible no-op replacement for {@code FunctionInformationMap} */
public final class FunctionInformationMap {
  public static final class Builder {
    public Builder addEntry(Entry value) {
      throw new UnsupportedOperationException(
          "FunctionInformationMap.Builder.addEntry not implemented");
    }

    public FunctionInformationMap build() {
      throw new UnsupportedOperationException(
          "FunctionInformationMap.Builder.build not implemented");
    }
  }

  public static final class Entry {
    public static final class Builder {
      public Builder setId(int value) {
        throw new UnsupportedOperationException(
            "FunctionInformationMap.Entry.Builder.setId not implemented");
      }

      public Builder setSourceName(String value) {
        throw new UnsupportedOperationException(
            "FunctionInformationMap.Entry.Builder.setSourceName not implemented");
      }

      public Builder setLineNumber(int value) {
        throw new UnsupportedOperationException(
            "FunctionInformationMap.Entry.Builder.setLineNumber not implemented");
      }

      public Builder setModuleName(String value) {
        throw new UnsupportedOperationException(
            "FunctionInformationMap.Entry.Builder.setModuleName not implemented");
      }

      public Builder setSize(int value) {
        throw new UnsupportedOperationException(
            "FunctionInformationMap.Entry.Builder.setSize not implemented");
      }

      public Builder setName(String value) {
        throw new UnsupportedOperationException(
            "FunctionInformationMap.Entry.Builder.setName not implemented");
      }

      public Builder setCompiledSource(String value) {
        throw new UnsupportedOperationException(
            "FunctionInformationMap.Entry.Builder.setCompiledSource not implemented");
      }

      public Entry build() {
        throw new UnsupportedOperationException(
            "FunctionInformationMap.Entry.Builder.build not implemented");
      }
    }

    public static Builder newBuilder() {
      throw new UnsupportedOperationException(
          "FunctionInformationMap.Entry.newBuilder not implemented");
    }
  }

  public static Builder newBuilder() {
    throw new UnsupportedOperationException("FunctionInformationMap.newBuilder not implemented");
  }
}
