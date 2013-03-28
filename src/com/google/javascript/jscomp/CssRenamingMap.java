/*
 * Copyright 2009 The Closure Compiler Authors.
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

/**
 * Interface used by {@link ReplaceCssNames} to substitute CSS class names.
 */
public interface CssRenamingMap extends RenamingMap {

  /** Kind of renaming map */
  public static enum Style {
    BY_WHOLE,
    BY_PART,
  }

  @Override
  String get(String value);

  Style getStyle();

  /** ByPart renaming map */
  public abstract static class ByPart implements CssRenamingMap {
    @Override
    public abstract String get(String value);

    @Override
    public Style getStyle() {
      return Style.BY_PART;
    }
  }

  /** ByWhole renaming map */
  public abstract static class ByWhole implements CssRenamingMap {
    @Override
    public abstract String get(String value);

    @Override
    public Style getStyle() {
      return Style.BY_WHOLE;
    }
  }
}
