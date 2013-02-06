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

package com.google.javascript.rhino.jstype;

import com.google.common.collect.Maps;
import com.google.javascript.rhino.Node;

import java.util.Collections;
import java.util.HashMap;

/**
 * A builder for record types.
 *
 */
public class RecordTypeBuilder {
  private boolean isEmpty = true;
  private boolean isDeclared = true;
  private final JSTypeRegistry registry;
  private final HashMap<String, RecordProperty> properties = Maps.newHashMap();

  public RecordTypeBuilder(JSTypeRegistry registry) {
    this.registry = registry;
  }

  /** See the comments on RecordType about synthetic types. */
  void setSynthesized(boolean synthesized) {
    isDeclared = !synthesized;
  }

  /**
   * Adds a property with the given name and type to the record type.
   * @param name the name of the new property
   * @param type the JSType of the new property
   * @param propertyNode the node that holds this property definition
   * @return The builder itself for chaining purposes, or null if there's
   *          a duplicate.
   */
  public RecordTypeBuilder addProperty(String name, JSType type, Node
      propertyNode) {
    isEmpty = false;
    if (properties.containsKey(name)) {
      return null;
    }
    properties.put(name, new RecordProperty(type, propertyNode));
    return this;
  }

  /**
   * Creates a record.
   * @return The record type.
   */
  public JSType build() {
     // If we have an empty record, simply return the object type.
    if (isEmpty) {
       return registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    }

    return new RecordType(
        registry, Collections.unmodifiableMap(properties), isDeclared);
  }

  static class RecordProperty {
    private final JSType type;
    private final Node propertyNode;

    RecordProperty(JSType type, Node propertyNode) {
      this.type = type;
      this.propertyNode = propertyNode;
    }

    public JSType getType() {
      return type;
    }

    public Node getPropertyNode() {
      return propertyNode;
    }
  }
}
