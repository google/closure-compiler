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

import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;

import java.io.Serializable;

/**
 * A property slot of an object.
 * @author nicksantos@google.com (Nick Santos)
 */
public final class Property
    implements Serializable, StaticTypedSlot<JSType>, StaticTypedRef<JSType> {
  private static final long serialVersionUID = 1L;

  /**
   * Property's name.
   */
  private final String name;

  /**
   * Property's type.
   */
  private JSType type;

  /**
   * Whether the property's type is inferred.
   */
  private final boolean inferred;

  /**
   * The node corresponding to this property, e.g., a GETPROP node that
   * declares this property.
   */
  private Node propertyNode;

  /**  The JSDocInfo for this property. */
  private JSDocInfo docInfo = null;

  Property(String name, JSType type, boolean inferred,
      Node propertyNode) {
    this.name = name;
    this.type = type;
    this.inferred = inferred;
    this.propertyNode = propertyNode;
  }

  @Override
      public String getName() {
    return name;
  }

  @Override
      public Node getNode() {
    return propertyNode;
  }

  @Override
      public StaticSourceFile getSourceFile() {
    return propertyNode == null ? null : propertyNode.getStaticSourceFile();
  }

  @Override
      public Property getSymbol() {
    return this;
  }

  @Override
      public Property getDeclaration() {
    return propertyNode == null ? null : this;
  }

  @Override
      public JSType getType() {
    return type;
  }

  @Override
      public boolean isTypeInferred() {
    return inferred;
  }

  boolean isFromExterns() {
    return propertyNode == null ? false : propertyNode.isFromExterns();
  }

  void setType(JSType type) {
    this.type = type;
  }

  @Override public JSDocInfo getJSDocInfo() {
    return this.docInfo;
  }

  void setJSDocInfo(JSDocInfo info) {
    this.docInfo = info;
  }

  public void setNode(Node n) {
    this.propertyNode = n;
  }

  public String toString() {
    return "Property { "
        + " name: " + this.name
        + ", type:" + this.type
        + ", inferred: " + this.inferred
        + "}";
  }
}
