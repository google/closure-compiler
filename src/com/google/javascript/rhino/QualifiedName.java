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
 *   Norris Boyd
 *   Roger Lawrence
 *   Mike McCabe
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

import com.google.common.collect.ImmutableList;
import javax.annotation.Nullable;

/**
 * Abstraction over a qualified name. Unifies Node-based qualified names and string-based names,
 * allowing to lazily parse strings and represent a pre-parsed qualified name without the overhead
 * of a whole Node. Essentially, a qualified name is a linked list of {@linkplain #getComponent
 * components}, starting from the outermost property access and ending with the root of the name,
 * which is a {@linkplain #isSimple simple name} with no {@linkplain #getOwner owner}.
 */
public abstract class QualifiedName {

  // All subclasses must be defined in this file.
  private QualifiedName() {}

  public static QualifiedName of(String string) {
    int lastIndex = 0;
    int index;
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    do {
      index = string.indexOf('.', lastIndex);
      builder.add(string.substring(lastIndex, index < 0 ? string.length() : index).intern());
      lastIndex = index + 1;
    } while (index >= 0);
    ImmutableList<String> terms = builder.build();
    return new StringListQname(terms, terms.size());
  }

  /**
   * Returns the qualified name of the owner, or null for simple names. For the name "foo.bar.baz",
   * this returns an object representing "foo.bar".
   */
  @Nullable
  public abstract QualifiedName getOwner();

  /**
   * Returns outer-most term of this qualified name, or the entire name for simple names. For the
   * name "foo.bar.baz", this returns "baz".
   */
  public abstract String getComponent();

  /** Returns true if this is a simple name. */
  public abstract boolean isSimple();

  // TODO(sdh): We'll probably want a getRoot() method, which would be useful once this is
  // integrated
  // further into TypedScopeCreator.

  /** Appends the joined qualified name to the given StringBuilder. */
  abstract void appendTo(StringBuilder sb);

  /** Checks whether the given node matches this name. */
  public abstract boolean matches(Node n);

  /**
   * Returns the components of this name as an iterable of strings, starting at the root. For the
   * qualified name foo.bar.baz, this returns ["foo", "bar", "baz"].
   */
  public Iterable<String> components() {
    ImmutableList.Builder<String> components = ImmutableList.builder();
    buildComponents(components);
    return components.build();
  }

  private void buildComponents(ImmutableList.Builder<String> builder) {
    QualifiedName owner = getOwner();
    if (owner != null) {
      owner.buildComponents(builder);
    }
    builder.add(getComponent());
  }

  /** Returns the qualified name as a string. */
  public String join() {
    StringBuilder sb = new StringBuilder();
    appendTo(sb);
    return sb.toString();
  }

  /**
   * Returns a new qualified name object with {@code this} name as the owner and the given string as
   * the property name.
   */
  public QualifiedName getprop(String propertyName) {
    return new GetpropQname(this, propertyName);
  }

  /** A qualified name based on a list of string terms. */
  private static class StringListQname extends QualifiedName {
    final ImmutableList<String> terms;
    final int size;

    StringListQname(ImmutableList<String> terms, int size) {
      this.terms = terms;
      this.size = size;
    }

    @Override
    public QualifiedName getOwner() {
      return size > 1 ? new StringListQname(terms, size - 1) : null;
    }

    @Override
    public String getComponent() {
      return terms.get(size - 1);
    }

    @Override
    public boolean isSimple() {
      return size == 1;
    }

    @Override
    void appendTo(StringBuilder sb) {
      for (int i = 0; i < size; i++) {
        if (i > 0) {
          sb.append('.');
        }
        sb.append(terms.get(i));
      }
    }

    @Override
    public Iterable<String> components() {
      return terms.subList(0, size);
    }

    @Override
    public boolean matches(Node n) {
      int pos = size - 1;
      while (pos > 0 && n.isGetProp()) {
        // NOTE: these strings are all interned, so we can do identity comparison.
        if (n.getLastChild().getString() != terms.get(pos)) {
          return false;
        }
        pos--;
        n = n.getFirstChild();
      }
      if (pos > 0) {
        return false;
      }
      switch (n.getToken()) {
        case NAME:
        case MEMBER_FUNCTION_DEF:
          return terms.get(0) == n.getString();
        case THIS:
          return terms.get(0) == THIS;
        case SUPER:
          return terms.get(0) == SUPER;
        default:
          return false;
      }
    }
  }

  /** A qualified name built with an extra property access on an existing qualified name. */
  private static class GetpropQname extends QualifiedName {
    final QualifiedName owner;
    final String prop;

    GetpropQname(QualifiedName owner, String prop) {
      this.owner = owner;
      this.prop = prop.intern();
    }

    @Override
    public QualifiedName getOwner() {
      return owner;
    }

    @Override
    public String getComponent() {
      return prop;
    }

    @Override
    public boolean isSimple() {
      return false;
    }

    @Override
    void appendTo(StringBuilder sb) {
      owner.appendTo(sb);
      sb.append('.').append(prop);
    }

    @Override
    public boolean matches(Node n) {
      return n.isGetProp()
          && n.getLastChild().getString() == prop
          && owner.matches(n.getFirstChild());
    }
  }

  /**
   * A qualified name from a node. The precondition that the node is actually a qualified name is
   * not actually checked here, though it will throw IllegalStateException eventually if it is not.
   */
  static final class NodeQname extends QualifiedName {
    private final Node node;

    NodeQname(Node n) {
      this.node = n;
    }

    @Override
    public QualifiedName getOwner() {
      return node.isGetProp() ? new NodeQname(node.getFirstChild()) : null;
    }

    @Override
    public String getComponent() {
      switch (node.getToken()) {
        case GETPROP:
          return node.getLastChild().getString();
        case THIS:
          return THIS;
        case SUPER:
          return SUPER;
        case NAME:
        case MEMBER_FUNCTION_DEF:
          return node.getString();
        default:
          throw new IllegalStateException("Not a qualified name: " + node);
      }
    }

    @Override
    public boolean isSimple() {
      return !node.isGetProp();
    }

    @Override
    void appendTo(StringBuilder sb) {
      sb.append(join());
    }

    @Override
    public String join() {
      return node.getQualifiedName();
    }

    @Override
    public boolean matches(Node n) {
      return n.matchesQualifiedName(node);
    }
  }

  private static final String THIS = "this".intern();
  private static final String SUPER = "super".intern();
}
