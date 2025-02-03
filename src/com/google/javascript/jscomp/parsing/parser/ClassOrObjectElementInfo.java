/*
 * Copyright 2025 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.parsing.parser;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.parsing.parser.trees.ParseTree;
import com.google.javascript.jscomp.parsing.parser.util.SourcePosition;

/**
 * Gathers information for ES6 class / Object literal methods, getters, setters and fields.
 *
 * <p>Gathers: the start position, whether it is a class element (false for object literal), whether
 * it is static (only for class elements), and, after construction, the name IdentifierToken or
 * ParseTree.
 *
 * <p>Used by {@link Parser} but separated out to enforce visibility.
 */
class ClassOrObjectElementInfo {
  /** The start position of the element. */
  final SourcePosition start;

  /** Whether this is a class member. False for object literal elements. */
  final boolean isClassMember;

  /** Whether this class element is static. Can only be true if {@link #isClassMember} is true. */
  final boolean isStatic;

  private IdentifierToken name;
  private ParseTree nameExpr;

  private ClassOrObjectElementInfo(SourcePosition start, boolean isClassMember, boolean isStatic) {
    this.start = start;
    this.isClassMember = isClassMember;
    this.isStatic = isStatic;
  }

  static ClassOrObjectElementInfo createClassMemberInfo(SourcePosition start, boolean isStatic) {
    return new ClassOrObjectElementInfo(start, /* isClassMember= */ true, isStatic);
  }

  static ClassOrObjectElementInfo createObjectLiteralElementInfo(SourcePosition start) {
    return new ClassOrObjectElementInfo(start, /* isClassMember= */ false, /* isStatic= */ false);
  }

  void setName(IdentifierToken name) {
    checkState(nameExpr == null);
    this.name = name;
  }

  boolean hasName() {
    return name != null;
  }

  IdentifierToken getName() {
    return checkNotNull(name);
  }

  void setNameExpr(ParseTree nameExpr) {
    checkState(name == null);
    this.nameExpr = nameExpr;
  }

  boolean hasNameExpr() {
    return nameExpr != null;
  }

  ParseTree getNameExpr() {
    return checkNotNull(nameExpr);
  }
}
