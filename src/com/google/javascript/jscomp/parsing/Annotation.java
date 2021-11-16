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

/** All natively recognized JSDoc annotations. */
enum Annotation {
  NG_INJECT,
  ABSTRACT,
  ALTERNATE_MESSAGE_ID,
  AUTHOR,
  CLOSURE_PRIMITIVE,
  COLLAPSIBLE_OR_BREAK_MY_CODE,
  CONSTANT,
  CONSTRUCTOR,
  CUSTOM_ELEMENT,
  RECORD,
  DEFINE,
  DEPRECATED,
  DESC,
  DICT,
  ENHANCE,
  ENUM,
  EXTENDS,
  EXTERNS,
  EXPORT,
  FILE_OVERVIEW,
  FINAL,
  HIDDEN,
  IDGENERATOR,
  IMPLEMENTS,
  IMPLICIT_CAST,
  INHERIT_DOC,
  INTERFACE,
  LENDS,
  LICENSE, // same as preserve
  LOCALE_FILE,
  LOCALE_OBJECT,
  LOCALE_SELECT,
  LOCALE_VALUE,
  MEANING,
  MIXIN_CLASS,
  MIXIN_FUNCTION,
  MODIFIES,
  NO_COLLAPSE,
  NO_COMPILE,
  NO_INLINE,
  NO_SIDE_EFFECTS,
  NOT_IMPLEMENTED,
  OVERRIDE,
  PACKAGE,
  PARAM,
  POLYMER,
  POLYMER_BEHAVIOR,
  PRESERVE, // same as license
  PRIVATE,
  PROTECTED,
  PROVIDE_GOOG, // @provideGoog - appears only in base.js
  PUBLIC,
  PURE_OR_BREAK_MY_CODE,
  RETURN,
  SEE,
  SOY_MODULE,
  SOY_TEMPLATE,
  STRUCT,
  SUPPRESS,
  TEMPLATE,
  THIS,
  THROWS,
  TYPE,
  TYPEDEF,
  TYPE_SUMMARY,
  UNRESTRICTED,
  VERSION,
  WIZACTION;

  static final Map<String, Annotation> recognizedAnnotations =
      new ImmutableMap.Builder<String, Annotation>()
          .put("ngInject", Annotation.NG_INJECT)
          .put("abstract", Annotation.ABSTRACT)
          .put("alternateMessageId", Annotation.ALTERNATE_MESSAGE_ID)
          .put("argument", Annotation.PARAM)
          .put("author", Annotation.AUTHOR)
          .put("closurePrimitive", Annotation.CLOSURE_PRIMITIVE)
          .put("const", Annotation.CONSTANT)
          .put("collapsibleOrBreakMyCode", Annotation.COLLAPSIBLE_OR_BREAK_MY_CODE)
          .put("constant", Annotation.CONSTANT)
          .put("constructor", Annotation.CONSTRUCTOR)
          .put("customElement", Annotation.CUSTOM_ELEMENT)
          .put("copyright", Annotation.LICENSE)
          .put("define", Annotation.DEFINE)
          .put("deprecated", Annotation.DEPRECATED)
          .put("desc", Annotation.DESC)
          .put("dict", Annotation.DICT)
          .put("enum", Annotation.ENUM)
          .put("enhance", Annotation.ENHANCE)
          .put("export", Annotation.EXPORT)
          .put("extends", Annotation.EXTENDS)
          .put("externs", Annotation.EXTERNS)
          .put("fileoverview", Annotation.FILE_OVERVIEW)
          .put("final", Annotation.FINAL)
          .put("hidden", Annotation.HIDDEN)
          .put("idGenerator", Annotation.IDGENERATOR)
          .put("implements", Annotation.IMPLEMENTS)
          .put("implicitCast", Annotation.IMPLICIT_CAST)
          .put("inheritDoc", Annotation.INHERIT_DOC)
          .put("interface", Annotation.INTERFACE)
          .put("record", Annotation.RECORD)
          .put("lends", Annotation.LENDS)
          .put("license", Annotation.LICENSE)
          .put("localeFile", Annotation.LOCALE_FILE)
          .put("localeObject", Annotation.LOCALE_OBJECT)
          .put("localeSelect", Annotation.LOCALE_SELECT)
          .put("localeValue", Annotation.LOCALE_VALUE)
          .put("meaning", Annotation.MEANING)
          .put("mixinClass", Annotation.MIXIN_CLASS)
          .put("mixinFunction", Annotation.MIXIN_FUNCTION)
          .put("modifies", Annotation.MODIFIES)
          .put("nocollapse", Annotation.NO_COLLAPSE)
          .put("nocompile", Annotation.NO_COMPILE)
          .put("noinline", Annotation.NO_INLINE)
          .put("nosideeffects", Annotation.NO_SIDE_EFFECTS)
          .put("override", Annotation.OVERRIDE)
          .put("owner", Annotation.AUTHOR)
          .put("package", Annotation.PACKAGE)
          .put("param", Annotation.PARAM)
          .put("polymer", Annotation.POLYMER)
          .put("polymerBehavior", Annotation.POLYMER_BEHAVIOR)
          .put("preserve", Annotation.PRESERVE)
          .put("private", Annotation.PRIVATE)
          .put("protected", Annotation.PROTECTED)
          .put("provideGoog", Annotation.PROVIDE_GOOG)
          .put("public", Annotation.PUBLIC)
          .put("pureOrBreakMyCode", Annotation.PURE_OR_BREAK_MY_CODE)
          .put("return", Annotation.RETURN)
          .put("returns", Annotation.RETURN)
          .put("see", Annotation.SEE)
          .put("soyModule", Annotation.SOY_MODULE)
          .put("soyTemplate", Annotation.SOY_TEMPLATE)
          .put("struct", Annotation.STRUCT)
          .put("suppress", Annotation.SUPPRESS)
          .put("template", Annotation.TEMPLATE)
          .put("this", Annotation.THIS)
          .put("throws", Annotation.THROWS)
          .put("type", Annotation.TYPE)
          .put("typedef", Annotation.TYPEDEF)
          .put("typeSummary", Annotation.TYPE_SUMMARY)
          .put("unrestricted", Annotation.UNRESTRICTED)
          .put("version", Annotation.VERSION)
          .put("wizaction", Annotation.WIZACTION)
          .buildOrThrow();
}
