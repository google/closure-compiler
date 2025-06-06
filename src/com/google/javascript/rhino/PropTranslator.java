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

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.serialization.NodeProperty;
import com.google.javascript.rhino.Node.Prop;
import org.jspecify.annotations.Nullable;

/**
 * A translator for converting between Rhino node properties and TypedAST proto node properties used
 * by the Rhino Node class.
 */
@SuppressWarnings("ProtocolBufferOrdinal")
final class PropTranslator {
  private static final Prop[] protoToRhinoProp = new Prop[NodeProperty.values().length];
  private static final NodeProperty[] rhinoToProtoProp = new NodeProperty[Prop.values().length];

  static NodeProperty serialize(Prop x) {
    return rhinoToProtoProp[x.ordinal()];
  }

  static Prop deserialize(NodeProperty x) {
    return protoToRhinoProp[x.ordinal()];
  }

  private PropTranslator() {
    throw new AssertionError();
  }

  static {
    setProps();
    checkUnexpectedNullProtoProps();
  }

  private static final void setProps() {
    for (Prop rhinoProp : Prop.values()) {
      NodeProperty protoProp = serializeProp(rhinoProp);
      if (protoProp != null) {
        // Boolean props are stored as a bitset, see Node#deserializeProperties
        checkState(
            protoProp.getNumber() < 63, "enum %s value %s", protoProp, protoProp.getNumber());
        protoToRhinoProp[protoProp.ordinal()] = rhinoProp;
        rhinoToProtoProp[rhinoProp.ordinal()] = protoProp;
      }
    }
  }

  private static final @Nullable NodeProperty serializeProp(Prop prop) {
    return switch (prop) {
      case ARROW_FN -> NodeProperty.ARROW_FN;
      case ASYNC_FN -> NodeProperty.ASYNC_FN;
      case GENERATOR_FN -> NodeProperty.GENERATOR_FN;
      case YIELD_ALL -> NodeProperty.YIELD_ALL;
      case IS_PARENTHESIZED -> NodeProperty.IS_PARENTHESIZED;
      case SYNTHETIC -> NodeProperty.SYNTHETIC;
      case ADDED_BLOCK -> NodeProperty.ADDED_BLOCK;
      case STATIC_MEMBER -> NodeProperty.STATIC_MEMBER;
      case IS_GENERATOR_MARKER -> NodeProperty.IS_GENERATOR_MARKER;
      case IS_GENERATOR_SAFE -> NodeProperty.IS_GENERATOR_SAFE;
      case COLOR_FROM_CAST -> NodeProperty.COLOR_FROM_CAST;
      case NON_INDEXABLE -> NodeProperty.NON_INDEXABLE;
      case DELETED -> NodeProperty.DELETED;
      case IS_UNUSED_PARAMETER -> NodeProperty.IS_UNUSED_PARAMETER;
      case IS_SHORTHAND_PROPERTY -> NodeProperty.IS_SHORTHAND_PROPERTY;
      case START_OF_OPT_CHAIN -> NodeProperty.START_OF_OPT_CHAIN;
      case TRAILING_COMMA -> NodeProperty.TRAILING_COMMA;
      case IS_CONSTANT_NAME -> NodeProperty.IS_CONSTANT_NAME;
      case IS_NAMESPACE -> NodeProperty.IS_NAMESPACE;
      case DIRECT_EVAL -> NodeProperty.DIRECT_EVAL;
      case FREE_CALL -> NodeProperty.FREE_CALL;
      case REFLECTED_OBJECT -> NodeProperty.REFLECTED_OBJECT;
      case EXPORT_DEFAULT -> NodeProperty.EXPORT_DEFAULT;
      case EXPORT_ALL_FROM -> NodeProperty.EXPORT_ALL_FROM;
      case COMPUTED_PROP_METHOD -> NodeProperty.COMPUTED_PROP_METHOD;
      case COMPUTED_PROP_GETTER -> NodeProperty.COMPUTED_PROP_GETTER;
      case COMPUTED_PROP_SETTER -> NodeProperty.COMPUTED_PROP_SETTER;
      case COMPUTED_PROP_VARIABLE -> NodeProperty.COMPUTED_PROP_VARIABLE;
      case GOOG_MODULE -> NodeProperty.GOOG_MODULE;
      case MODULE_ALIAS -> NodeProperty.MODULE_ALIAS;
      case MODULE_EXPORT -> NodeProperty.MODULE_EXPORT;
      case ES6_MODULE -> NodeProperty.ES6_MODULE;
      case CONSTANT_VAR_FLAGS -> NodeProperty.CONSTANT_VAR_FLAGS;
      case SYNTHESIZED_UNFULFILLED_NAME_DECLARATION ->
          NodeProperty.SYNTHESIZED_UNFULFILLED_NAME_DECLARATION;
      case CLOSURE_UNAWARE_SHADOW,
          SIDE_EFFECT_FLAGS,
          DECLARED_TYPE_EXPR,
          FEATURE_SET,
          TYPE_BEFORE_CAST,
          NON_JSDOC_COMMENT,
          TRAILING_NON_JSDOC_COMMENT,
          JSDOC_INFO,
          INCRDECR,
          QUOTED,
          USE_STRICT,
          SOURCE_FILE,
          INPUT_ID,
          CHANGE_TIME,
          OPT_ES6_TYPED,
          GENERIC_TYPE,
          IMPLEMENTS,
          CONSTRUCT_SIGNATURE,
          ACCESS_MODIFIER,
          IS_TYPESCRIPT_ABSTRACT,
          TYPEDEF_TYPE,
          PRIVATE_IDENTIFIER ->
          // These cases cannot be translated to a NodeProperty
          null;
    };
  }

  private static final void checkUnexpectedNullProtoProps() {
    for (NodeProperty protoProp : NodeProperty.values()) {
      switch (protoProp) {
        case NODE_PROPERTY_UNSPECIFIED:
          // this is the "no properties are set" value
          break;
        case IS_DECLARED_CONSTANT:
        case IS_INFERRED_CONSTANT:
          // These are used for the CONSTANT_VAR_FLAGS bit field property
          break;
        case UNRECOGNIZED:
        case UNUSED_11:
          // unused
          break;
        case MUTATES_GLOBAL_STATE:
        case MUTATES_THIS:
        case MUTATES_ARGUMENTS:
        case THROWS:
          // these are used for the SIDE_EFFECT_FLAGS bit field property
          break;
        case CLOSURE_UNAWARE_SHADOW:
          // this is a special case where the property is a Node pointer, not a boolean
          break;
        default:
          // everything else should be a 1-to-1 match with a boolean property
          checkState(
              protoToRhinoProp[protoProp.ordinal()] != null,
              "Hit unhandled node property: %s",
              protoProp);
      }
    }
  }
}
