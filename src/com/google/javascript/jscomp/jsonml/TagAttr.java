/*
 * Copyright 2010 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.jsonml;

import java.util.HashMap;
import java.util.Map;

/**
 * List of attributes that a JsonML element may have.
 *
 * @author dhans@google.com (Daniel Hans)
 */
public enum TagAttr {
  BODY("body"),
  DIRECTIVE("directive"),
  END_COLUMN("endColumn"),
  END_LINE("endLine"),
  FLAGS("flags"),
  IS_PREFIX("isPrefix"),
  LABEL("label"),
  NAME("name"),
  OP("op"),
  OPAQUE_POSITION("opaque_position"),
  SOURCE("source"),
  START_COLUMN("startColumn"),
  START_LINE("startLine"),
  TYPE("type"),
  VALUE("value");

  private final String name;
  private static final Map<String, TagAttr> lookup =
      new HashMap<String, TagAttr>();

  static {
    for (TagAttr t : TagAttr.values()) {
      lookup.put(t.getName(), t);
    }
  }

  private String getName() {
    return name;
  }

  private TagAttr(String name) {
    this.name = name;
  }

  public static TagAttr get(String name) {
    return lookup.get(name);
  }

  @Override
  public String toString() {
    return name;
  }
}
