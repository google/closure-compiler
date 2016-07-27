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

/**
 * GWT compatible replacement of {@code Mapping}, which is a generated Java protocol buffer
 * unsuitable for use in the GWT Closure.
 *
 * This is not intended to match the generated class exactly, it just implements the required
 * methods.
 */
public final class Mapping {
  private Mapping() {}

  public static final class OriginalMapping {
    public static final class Builder {
      private String originalFile = null;
      private int columnPosition = 0;
      private int lineNumber = 0;
      private String identifier = null;

      public Builder setOriginalFile(String value) {
        this.originalFile = value;
        return this;
      }

      public Builder setLineNumber(int value) {
        this.lineNumber = value;
        return this;
      }

      public Builder setColumnPosition(int value) {
        this.columnPosition = value;
        return this;
      }

      public Builder setIdentifier(String value) {
        this.identifier = value;
        return this;
      }

      public OriginalMapping build() {
        return new OriginalMapping(originalFile, lineNumber, columnPosition, identifier);
      }
    }

    public static Builder newBuilder() {
      return new Builder();
    }

    private final String originalFile;
    private final int lineNumber;
    private final int columnPosition;
    private final String identifier;

    OriginalMapping(String originalFile, int lineNumber, int columnPosition, String identifier) {
      this.originalFile = originalFile;
      this.lineNumber = lineNumber;
      this.columnPosition = columnPosition;
      this.identifier = identifier;
    }

    public String getOriginalFile() {
      return originalFile;
    }

    public int getLineNumber() {
      return lineNumber;
    }

    public int getColumnPosition() {
      return columnPosition;
    }

    public String getIdentifier() {
      return identifier;
    }

    public Builder toBuilder() {
      return new Builder()
          .setOriginalFile(originalFile)
          .setLineNumber(lineNumber)
          .setColumnPosition(columnPosition)
          .setIdentifier(identifier);
    }
  }
}
