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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A property slot of an object.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public final class Property implements StaticTypedSlot, StaticTypedRef {

  /** What kind of property this is - currently the spec only supports strings & symbols. */
  public enum KeyKind {
    STRING,
    SYMBOL
  }

  /**
   * A valid key for a property. May be either a string or a well-known symbol such as
   * Symbol.iterator.
   */
  public sealed interface Key {
    KeyKind kind();

    @Nullable String string();

    @Nullable KnownSymbolType symbol();

    String humanReadableName();

    default boolean matches(String stringKey) {
      return this.kind().equals(KeyKind.STRING) && this.string().equals(stringKey);
    }
  }

  /** A property string key, such as a in {a: 0}; */
  public record StringKey(String string) implements Key {
    public StringKey {
      checkNotNull(string);
    }

    @Override
    public KnownSymbolType symbol() {
      throw new UnsupportedOperationException();
    }

    @Override
    public KeyKind kind() {
      return KeyKind.STRING;
    }

    @Override
    public String humanReadableName() {
      return string();
    }
  }

  /** A property well-known symbol key, such as Symbol.iterator. */
  public record SymbolKey(KnownSymbolType symbol) implements Key {
    public SymbolKey {
      checkNotNull(symbol);
    }

    @Override
    public String string() {
      throw new UnsupportedOperationException();
    }

    @Override
    public KeyKind kind() {
      return KeyKind.SYMBOL;
    }

    @Override
    public String humanReadableName() {
      return symbol().getDisplayName();
    }
  }

  /** A property instance associated with particular owner type. */
  public static final class OwnedProperty {
    private final ObjectType owner;
    private final Property value;

    public OwnedProperty(ObjectType owner, Property value) {
      this.owner = owner;
      this.value = value;
    }

    public ObjectType getOwner() {
      return owner;
    }

    public Property getValue() {
      return value;
    }

    public ObjectType getOwnerInstanceType() {
      return owner.isFunctionPrototypeType() ? owner.getOwnerFunction().getInstanceType() : owner;
    }

    public boolean isOwnedByInterface() {
      return owner.isFunctionPrototypeType()
          ? owner.getOwnerFunction().isInterface()
          : owner.isInterface();
    }
  }

  private static final long serialVersionUID = 1L;

  /**
   * Property's name. Either a String or a KnownSymbolType.
   *
   * <p>NOTE(lharker): storing this as a Key would be correct & more type-safe, but that caused some
   * OOMs when I tried it, presumably because of the extra memory usage. So we store this as a plain
   * Object for now. All the non-private API methods should use Key though.
   */
  private final Object name;

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

  /** The JSDocInfo for this property. */
  private @Nullable JSDocInfo docInfo = null;

  Property(String name, JSType type, boolean inferred, Node propertyNode) {
    this.name = checkNotNull(name);
    this.type = checkNotNull(type, "Null type specified for %s", name);
    this.inferred = inferred;
    this.propertyNode = propertyNode;
  }

  Property(Key name, JSType type, boolean inferred, Node propertyNode) {
    checkNotNull(name);
    this.name =
        switch (name.kind()) {
          case STRING -> name.string();
          case SYMBOL -> name.symbol();
        };
    this.type = checkNotNull(type, "Null type specified for %s", name);
    this.inferred = inferred;
    this.propertyNode = propertyNode;
  }

  @Override
  public String getName() {
    return switch (name) {
      case String s -> s;
      case KnownSymbolType s -> s.getDisplayName();
      default -> throw new AssertionError();
    };
  }

  @Override
  public Node getNode() {
    return propertyNode;
  }

  @Override
  public @Nullable StaticSourceFile getSourceFile() {
    return propertyNode == null ? null : propertyNode.getStaticSourceFile();
  }

  @Override
  public Property getSymbol() {
    return this;
  }

  @Override
  public @Nullable Property getDeclaration() {
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
    this.type = checkNotNull(type, "Null type specified for property %s", name);
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

  @Override
  public String toString() {
    return "Property { "
        + " name: " + this.name
        + ", type:" + this.type
        + ", inferred: " + this.inferred
        + "}";
  }

  @Override
  public StaticTypedScope getScope() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type);
  }
}
