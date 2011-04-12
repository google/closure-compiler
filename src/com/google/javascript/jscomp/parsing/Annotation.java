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

package com.google.javascript.jscomp.parsing;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * All natively recognized JSDoc annotations.
 * @author nicksantos@google.com (Nick Santos)
 */
enum Annotation {
  AUTHOR,
  CONSTANT,
  CONSTRUCTOR,
  DEFINE,
  DEPRECATED,
  DESC,
  ENUM,
  EXTENDS,
  EXTERNS,
  EXPORT,
  FILE_OVERVIEW,
  HIDDEN,
  IMPLEMENTS,
  IMPLICIT_CAST,
  INHERIT_DOC,
  INTERFACE,
  JAVA_DISPATCH,
  LENDS,
  LICENSE, // same as preserve
  MEANING,
  MODIFIES,
  NO_ALIAS,
  NO_COMPILE,
  NO_SHADOW,
  NO_SIDE_EFFECTS,
  NO_TYPE_CHECK,
  NOT_IMPLEMENTED,
  OVERRIDE,
  PARAM,
  PRESERVE, // same as license
  PRESERVE_TRY,
  PRIVATE,
  PROTECTED,
  PUBLIC,
  RETURN,
  SEE,
  SUPPRESS,
  TEMPLATE,
  THIS,
  THROWS,
  TYPE,
  TYPEDEF,
  VERSION;

  static final Map<String, Annotation> recognizedAnnotations =
      new ImmutableMap.Builder<String, Annotation>().
      put("argument", Annotation.PARAM).
      put("author", Annotation.AUTHOR).
      put("const", Annotation.CONSTANT).
      put("constant", Annotation.CONSTANT).
      put("constructor", Annotation.CONSTRUCTOR).
      put("define", Annotation.DEFINE).
      put("deprecated", Annotation.DEPRECATED).
      put("desc", Annotation.DESC).
      put("enum", Annotation.ENUM).
      put("export", Annotation.EXPORT).
      put("extends", Annotation.EXTENDS).
      put("externs", Annotation.EXTERNS).
      put("fileoverview", Annotation.FILE_OVERVIEW).
      put("final", Annotation.CONSTANT).
      put("hidden", Annotation.HIDDEN).
      put("implements", Annotation.IMPLEMENTS).
      put("implicitCast", Annotation.IMPLICIT_CAST).
      put("inheritDoc", Annotation.INHERIT_DOC).
      put("interface", Annotation.INTERFACE).
      put("javadispatch", Annotation.JAVA_DISPATCH).
      put("lends", Annotation.LENDS).
      put("license", Annotation.LICENSE).
      put("meaning", Annotation.MEANING).
      put("modifies", Annotation.MODIFIES).
      put("noalias", Annotation.NO_ALIAS).
      put("nocompile", Annotation.NO_COMPILE).
      put("noshadow", Annotation.NO_SHADOW).
      put("nosideeffects", Annotation.NO_SIDE_EFFECTS).
      put("notypecheck", Annotation.NO_TYPE_CHECK).
      put("override", Annotation.OVERRIDE).
      put("owner", Annotation.AUTHOR).
      put("param", Annotation.PARAM).
      put("preserve", Annotation.PRESERVE).
      put("preserveTry", Annotation.PRESERVE_TRY).
      put("private", Annotation.PRIVATE).
      put("protected", Annotation.PROTECTED).
      put("public", Annotation.PUBLIC).
      put("return", Annotation.RETURN).
      put("returns", Annotation.RETURN).
      put("see", Annotation.SEE).
      put("suppress", Annotation.SUPPRESS).
      put("template", Annotation.TEMPLATE).
      put("this", Annotation.THIS).
      put("throws", Annotation.THROWS).
      put("type", Annotation.TYPE).
      put("typedef", Annotation.TYPEDEF).
      put("version", Annotation.VERSION).
      build();
}
