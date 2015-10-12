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
 *   Nick Santos
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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.util.HashSet;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

/**
 * Representation for a collection of properties on an object.
 * @author nicksantos@google.com (Nick Santos)
 */
class PropertyMap implements Serializable {
  private static final long serialVersionUID = 1L;

  private static final PropertyMap EMPTY_MAP = new PropertyMap(
      ImmutableMap.<String, Property>of());

  private static final Function<ObjectType, PropertyMap> PROP_MAP_FROM_TYPE =
      new Function<ObjectType, PropertyMap>() {
    @Override public PropertyMap apply(ObjectType t) {
      return t.getPropertyMap();
    }
  };

  // A place to get the inheritance structure.
  // Because the extended interfaces are resolved dynamically, this gets
  // messy :(. If type-resolution was more well-defined, we could
  // just reference primary parents and secondary parents directly.
  private ObjectType parentSource = null;

  // The map of our own properties.
  private final Map<String, Property> properties;

  PropertyMap() {
    this(Maps.<String, Property>newTreeMap());
  }

  private PropertyMap(Map<String, Property> underlyingMap) {
    this.properties = underlyingMap;
  }

  static PropertyMap immutableEmptyMap() {
    return EMPTY_MAP;
  }

  void setParentSource(ObjectType ownerType) {
    if (this != EMPTY_MAP) {
      this.parentSource = ownerType;
    }
  }

  /** Returns the direct parent of this property map. */
  PropertyMap getPrimaryParent() {
    if (parentSource == null) {
      return null;
    }
    ObjectType iProto = parentSource.getImplicitPrototype();
    return iProto == null ? null : iProto.getPropertyMap();
  }

  /**
   * Returns the secondary parents of this property map, for interfaces that
   * need multiple inheritance.
   */
  Iterable<PropertyMap> getSecondaryParents() {
    if (parentSource == null) {
      return ImmutableList.of();
    }
    Iterable<ObjectType> extendedInterfaces =
        parentSource.getCtorExtendedInterfaces();

    // Most of the time, this will be empty.
    if (Iterables.isEmpty(extendedInterfaces)) {
      return ImmutableList.of();
    }

    return Iterables.transform(extendedInterfaces, PROP_MAP_FROM_TYPE);
  }

  Property getSlot(String name) {
    if (properties.containsKey(name)) {
      return properties.get(name);
    }
    PropertyMap primaryParent = getPrimaryParent();
    if (primaryParent != null) {
      Property prop = primaryParent.getSlot(name);
      if (prop != null) {
        return prop;
      }
    }
    for (PropertyMap p : getSecondaryParents()) {
      if (p != null) {
        Property prop = p.getSlot(name);
        if (prop != null) {
          return prop;
        }
      }
    }
    return null;
  }

  Property getOwnProperty(String propertyName) {
    return properties.get(propertyName);
  }

  int getPropertiesCount() {
    PropertyMap primaryParent = getPrimaryParent();
    if (primaryParent == null) {
      return this.properties.size();
    }
    Set<String> props = new HashSet<>();
    collectPropertyNames(props);
    return props.size();
  }

  Set<String> getOwnPropertyNames() {
    return properties.keySet();
  }

  void collectPropertyNames(Set<String> props) {
    props.addAll(properties.keySet());
    PropertyMap primaryParent = getPrimaryParent();
    if (primaryParent != null) {
      primaryParent.collectPropertyNames(props);
    }
    for (PropertyMap p : getSecondaryParents()) {
      if (p != null) {
        p.collectPropertyNames(props);
      }
    }
  }

  boolean removeProperty(String name) {
    return properties.remove(name) != null;
  }

  void putProperty(String name, Property newProp) {
    Property oldProp = properties.get(name);
    if (oldProp != null) {
      // This is to keep previously inferred JsDoc info, e.g., in a
      // replaceScript scenario.
      newProp.setJSDocInfo(oldProp.getJSDocInfo());
    }
    properties.put(name, newProp);
  }

  Iterable<Property> values() {
    return properties.values();
  }
}
