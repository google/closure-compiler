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
import org.jspecify.nullness.Nullable;

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
    switch (prop) {
      case ARROW_FN:
        return NodeProperty.ARROW_FN;
      case ASYNC_FN:
        return NodeProperty.ASYNC_FN;
      case GENERATOR_FN:
        return NodeProperty.GENERATOR_FN;
      case YIELD_ALL:
        return NodeProperty.YIELD_ALL;
      case IS_PARENTHESIZED:
        return NodeProperty.IS_PARENTHESIZED;
      case SYNTHETIC:
        return NodeProperty.SYNTHETIC;
      case ADDED_BLOCK:
        return NodeProperty.ADDED_BLOCK;
      case STATIC_MEMBER:
        return NodeProperty.STATIC_MEMBER;
      case IS_GENERATOR_MARKER:
        return NodeProperty.IS_GENERATOR_MARKER;
      case IS_GENERATOR_SAFE:
        return NodeProperty.IS_GENERATOR_SAFE;
      case COLOR_FROM_CAST:
        return NodeProperty.COLOR_FROM_CAST;
      case NON_INDEXABLE:
        return NodeProperty.NON_INDEXABLE;
      case DELETED:
        return NodeProperty.DELETED;
      case IS_UNUSED_PARAMETER:
        return NodeProperty.IS_UNUSED_PARAMETER;
      case IS_SHORTHAND_PROPERTY:
        return NodeProperty.IS_SHORTHAND_PROPERTY;
      case START_OF_OPT_CHAIN:
        return NodeProperty.START_OF_OPT_CHAIN;
      case TRAILING_COMMA:
        return NodeProperty.TRAILING_COMMA;
      case IS_CONSTANT_NAME:
        return NodeProperty.IS_CONSTANT_NAME;
      case IS_NAMESPACE:
        return NodeProperty.IS_NAMESPACE;
      case DIRECT_EVAL:
        return NodeProperty.DIRECT_EVAL;
      case FREE_CALL:
        return NodeProperty.FREE_CALL;
      case REFLECTED_OBJECT:
        return NodeProperty.REFLECTED_OBJECT;
      case EXPORT_DEFAULT:
        return NodeProperty.EXPORT_DEFAULT;
      case EXPORT_ALL_FROM:
        return NodeProperty.EXPORT_ALL_FROM;
      case COMPUTED_PROP_METHOD:
        return NodeProperty.COMPUTED_PROP_METHOD;
      case COMPUTED_PROP_GETTER:
        return NodeProperty.COMPUTED_PROP_GETTER;
      case COMPUTED_PROP_SETTER:
        return NodeProperty.COMPUTED_PROP_SETTER;
      case COMPUTED_PROP_VARIABLE:
        return NodeProperty.COMPUTED_PROP_VARIABLE;
      case GOOG_MODULE:
        return NodeProperty.GOOG_MODULE;
      case TRANSPILED:
        return NodeProperty.TRANSPILED;
      case MODULE_ALIAS:
        return NodeProperty.MODULE_ALIAS;
      case MODULE_EXPORT:
        return NodeProperty.MODULE_EXPORT;
      case ES6_MODULE:
        return NodeProperty.ES6_MODULE;
      case CONSTANT_VAR_FLAGS:
        return NodeProperty.CONSTANT_VAR_FLAGS;
      case SYNTHESIZED_UNFULFILLED_NAME_DECLARATION:
        return NodeProperty.SYNTHESIZED_UNFULFILLED_NAME_DECLARATION;
      case SIDE_EFFECT_FLAGS:
      case DECLARED_TYPE_EXPR:
      case FEATURE_SET:
      case TYPE_BEFORE_CAST:
      case NON_JSDOC_COMMENT:
      case TRAILING_NON_JSDOC_COMMENT:
      case JSDOC_INFO:
      case INCRDECR:
      case QUOTED:
      case USE_STRICT:
      case SOURCE_FILE:
      case INPUT_ID:
      case CHANGE_TIME:
      case OPT_ES6_TYPED:
      case GENERIC_TYPE:
      case IMPLEMENTS:
      case CONSTRUCT_SIGNATURE:
      case ACCESS_MODIFIER:
      case PARSE_RESULTS:
      case IS_TYPESCRIPT_ABSTRACT:
      case TYPEDEF_TYPE:
      case MARK_FOR_PARENTHESIZE:
        // These cases cannot be translated to a NodeProperty
        return null;
    }
    throw new AssertionError();
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
