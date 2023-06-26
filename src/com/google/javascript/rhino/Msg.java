/*
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Bob Jervis
 *   Google Inc.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

package com.google.javascript.rhino;

/** Error message constants. */
public enum Msg {
  BAD_FILEOVERVIEW_VISIBIIITY_ANNOTATION("{0} visibility not allowed in @fileoverview block"),
  BAD_JSDOC_TAG(
      "illegal use of unknown JSDoc tag \"{0}\"; ignoring it. Place another character before the @"
          + " to stop JSCompiler from parsing it as an annotation."),
  DUP_VARIABLE_NAME("duplicate variable name \"{0}\""),
  END_ANNOTATION_EXPECTED("expected end of line or comment."),
  INVALID_VARIABLE_NAME("invalid param name \"{0}\""),
  JSDOC_ALTERNATEMESSAGEID_EXTRA("extra @alternateMessageId tag"),
  JSDOC_AUTHORMISSING("@author tag missing author"),
  JSDOC_CLOSUREPRIMITIVE_EXTRA("conflicting @closurePrimitive tag"),
  JSDOC_CLOSUREPRIMITIVE_INVALID("invalid id in @closurePrimitive tag."),
  JSDOC_CLOSUREPRIMITIVE_MISSING("missing id in @closurePrimitive tag."),
  JSDOC_COLLAPSIBLEORBREAKMYCODE("extra @collapsibleOrBreakMyCode tag"),
  JSDOC_CONST("conflicting @const tag"),
  JSDOC_CUSTOMELEMENT_EXTRA("extra @customElement tag"),
  JSDOC_DEFINE("conflicting @define tag"),
  JSDOC_DEPRECATED("extra @deprecated tag"),
  JSDOC_DESC_EXTRA("extra @desc tag"),
  JSDOC_EXPORT("extra @export tag"),
  JSDOC_EXTENDS_DUPLICATE("duplicate @extends tag"),
  JSDOC_EXTERNS("extra @externs tag"),
  JSDOC_EXTRAVERSION("conflicting @version tag"),
  JSDOC_EXTRA_VISIBILITY("extra visibility tag"),
  JSDOC_FILEOVERVIEW_EXTRA("extra @fileoverview tag"),
  JSDOC_FINAL("extra @final tag."),
  JSDOC_FUNCTION_NEWNOTOBJECT("constructed type must be an object type"),
  JSDOC_FUNCTION_VARARGS("variable length argument must be last."),
  JSDOC_HIDDEN("extra @hidden tag"),
  JSDOC_IDGEN_BAD("malformed @idGenerator tag"),
  JSDOC_IDGEN_DUPLICATE("extra @idGenerator tag"),
  JSDOC_IDGEN_UNKNOWN("unknown @idGenerator parameter: {0}"),
  JSDOC_IMPLEMENTS_DUPLICATE("duplicate @implements tag."),
  JSDOC_IMPLEMENTS_EXTRAQUALIFIER(
      "@implements/@extends requires a bare interface/record name without ! or ?."),
  JSDOC_IMPLICITCAST("extra @implicitCast tag."),
  JSDOC_IMPORT("Import in typedef is not supported."),
  JSDOC_INCOMPAT_TYPE("type annotation incompatible with other annotations."),
  JSDOC_INTERFACE_CONSTRUCTOR("cannot be both an interface and a constructor."),
  JSDOC_LENDS_INCOMPATIBLE("@lends tag incompatible with other annotations."),
  JSDOC_LENDS_MISSING("missing object name in @lends tag."),
  JSDOC_LOCALEFILE("extra @localeFile tag"),
  JSDOC_LOCALEOBJECT("extra @localeObject tag"),
  JSDOC_LOCALESELECT("extra @localeSelect tag"),
  JSDOC_LOCALEVALUE("extra @localeValue tag"),
  JSDOC_PROVIDE_GOOG("extra @provideGoog tag"),
  JSDOC_PROVIDE_ALREADY_PROVIDED("extra @provideAlreadyProvided tag"),
  JSDOC_MEANING_EXTRA("extra @meaning tag"),
  JSDOC_MISSING_BRACES("Type annotations should have curly braces."),
  JSDOC_MISSING_COLON("expecting colon after this"),
  JSDOC_MISSING_GT("missing closing >"),
  JSDOC_MISSING_LC("missing opening {"),
  JSDOC_MISSING_LP("missing opening ("),
  JSDOC_MISSING_RB("missing closing ]"),
  JSDOC_MISSING_RC("expected closing }"),
  JSDOC_MISSING_RP("missing closing )"),
  JSDOC_MISSING_TYPE_DECLARATION("Missing type declaration."),
  JSDOC_MIXINCLASS_EXTRA("extra @mixinClass tag"),
  JSDOC_MIXINFUNCTION_EXTRA("extra @mixinFunction tag"),
  JSDOC_MODIFIES("malformed @modifies tag"),
  JSDOC_MODIFIES_DUPLICATE("conflicting @modifies tag"),
  JSDOC_MODIFIES_UNKNOWN("unknown @modifies parameter: {0}"),
  JSDOC_MODS("malformed @mods tag"),
  JSDOC_MODS_EXTRA("extra @mods tag"),
  JSDOC_NAME_SYNTAX("name not recognized due to syntax error."),
  JSDOC_NGINJECT_EXTRA("extra @ngInject tag"),
  JSDOC_NOCOLLAPSE("extra @nocollapse tag"),
  JSDOC_NOCOMPILE("extra @nocompile tag"),
  JSDOC_NODTS("extra @nodts tag"),
  JSDOC_NOINLINE("extra @noinline tag"),
  JSDOC_NOSIDEEFFECTS("conflicting @nosideeffects tag"),
  JSDOC_OVERRIDE("extra @override/@inheritDoc tag."),
  JSDOC_POLYMERBEHAVIOR_EXTRA("extra @polymerBehavior tag"),
  JSDOC_POLYMER_EXTRA("extra @polymer tag"),
  JSDOC_PUREORBREAKMYCODE("extra @pureOrBreakMyCode tag"),
  JSDOC_RECORD("conflicting @record tag."),
  JSDOC_SASS_GENERATED_CSS_TS("extra @sassGeneratedCssTs tag"),
  JSDOC_SEEMISSING("@see tag missing description"),
  JSDOC_SUPPRESS("malformed @suppress tag"),
  JSDOC_SUPPRESS_UNKNOWN("unknown @suppress parameter: {0}"),
  JSDOC_TEMPLATE_BOUNDEDGENERICS_USED(
      "Bounded generic semantics are currently still in development"),
  JSDOC_TEMPLATE_BOUNDSWITHTTL("Template types cannot combine bounds and TTL."),
  JSDOC_TEMPLATE_MULTIPLEDECLARATION(
      "Multiple template names cannot be declared with bounds or TTL."),
  JSDOC_TEMPLATE_NAME_MISSING("@template tag missing type name."),
  JSDOC_TEMPLATE_NAME_REDECLARATION("Type name(s) for @template annotation declared twice."),
  JSDOC_TEMPLATE_TYPETRANSFORMATION_EXPRESSIONMISSING("Missing type transformation expression."),
  JSDOC_TEMPLATE_TYPETRANSFORMATION_MISSINGDELIMIIER(
      "Expected end delimiter for a type transformation."),
  JSDOC_TYPE("conflicting @type tag"),
  JSDOC_TYPESUMMARY("extra @typeSummary tag"),
  JSDOC_TYPETRANSFORMATION_EXTRA_PARAM("Found extra parameter in {0}"),
  JSDOC_TYPETRANSFORMATION_INVALID("Invalid {0}"),
  JSDOC_TYPETRANSFORMATION_INVALID_EXPRESSION("Invalid {0} expression"),
  JSDOC_TYPETRANSFORMATION_INVALID_INSIDE("Invalid expression inside {0}"),
  JSDOC_TYPETRANSFORMATION_MISSING_PARAM("Missing parameter in {0}"),
  JSDOC_TYPE_RECORD_DUPLICATE("Duplicate record field {0}."),
  JSDOC_TYPE_SYNTAX("type not recognized due to syntax error."),
  JSDOC_UNNECESSARY_BRACES("braces are not required here"),
  JSDOC_VERSIONMISSING("@version tag missing version information"),
  JSDOC_WIZACTION("extra @wizaction tag"),
  MISSING_VARIABLE_NAME("expecting a variable name in a @param tag."),
  NO_TYPE_NAME("expecting a type name."),
  UNEXPECTED_EOF("Unexpected end of file"),
  JSDOC_WIZCALLBACK("extra @wizcallback tag");

  final String text;

  private Msg(String text) {
    this.text = text;
  }

  public String format() {
    return this.text;
  }

  public String format(Object... args) {
    // Note that this doesn't handle single-quote hence not compatible with MessageFormat.
    String s = this.text;
    for (int i = 0; i < args.length; i++) {
      String toReplace = "{" + i + "}";
      s = s.replace(toReplace, String.valueOf(args[i]));
    }
    return s;
  }
}
