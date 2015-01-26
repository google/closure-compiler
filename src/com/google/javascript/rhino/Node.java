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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.SimpleSourceFile;
import com.google.javascript.rhino.jstype.StaticSourceFile;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * This class implements the root of the intermediate representation.
 *
 */

public class Node implements Cloneable, Serializable {

  private static final long serialVersionUID = 1L;

  public static final int
      JSDOC_INFO_PROP   = 29,     // contains a TokenStream.JSDocInfo object
      VAR_ARGS_NAME     = 30,     // the name node is a variable length
                                  // argument placeholder.
      INCRDECR_PROP      = 32,    // pre or post type of increment/decrement
      QUOTED_PROP        = 36,    // set to indicate a quoted object lit key
      OPT_ARG_NAME       = 37,    // The name node is an optional argument.
      SYNTHETIC_BLOCK_PROP = 38,  // A synthetic block. Used to make
                                  // processing simpler, and does not
                                  // represent a real block in the source.
      ADDED_BLOCK        = 39,    // Used to indicate BLOCK that is added
      ORIGINALNAME_PROP  = 40,    // The original name of the node, before
                                  // renaming.
      SIDE_EFFECT_FLAGS  = 42,    // Function or constructor call side effect
                                  // flags
      // Coding convention props
      IS_CONSTANT_NAME   = 43,    // The variable or property is constant.
      IS_NAMESPACE       = 46,    // The variable creates a namespace.
      DIRECTIVES         = 48,    // The ES5 directives on this node.
      DIRECT_EVAL        = 49,    // ES5 distinguishes between direct and
                                  // indirect calls to eval.
      FREE_CALL          = 50,    // A CALL without an explicit "this" value.
      STATIC_SOURCE_FILE = 51,    // A StaticSourceFile indicating the file
                                  // where this node lives.
      LENGTH             = 52,    // The length of the code represented by
                                  // this node.
      INPUT_ID           = 53,    // The id of the input associated with this
                                  // node.
      SLASH_V            = 54,    // Whether a STRING node contains a \v
                                  // vertical tab escape. This is a total hack.
                                  // See comments in IRFactory about this.
      INFERRED_FUNCTION  = 55,    // Marks a function whose parameter types
                                  // have been inferred.
      CHANGE_TIME        = 56,    // For passes that work only on changed funs.
      REFLECTED_OBJECT   = 57,    // An object that's used for goog.object.reflect-style reflection.
      STATIC_MEMBER      = 58,    // Set if class member definition is static
      GENERATOR_FN       = 59,    // Set if the node is a Generator function or
                                  // member method.
      ARROW_FN           = 60,
      YIELD_FOR          = 61,    // Set if a yield is a "yield all"
      EXPORT_DEFAULT     = 62,    // Set if a export is a "default" export
      EXPORT_ALL_FROM    = 63,    // Set if an export is a "*"
      IS_CONSTANT_VAR    = 64,    // A lexical variable is inferred const
      GENERATOR_MARKER   = 65,    // Used by the ES6-to-ES3 translator.
      GENERATOR_SAFE     = 66,    // Used by the ES6-to-ES3 translator.

      COOKED_STRING      = 70,    // Used to support ES6 tagged template literal.
      RAW_STRING_VALUE   = 71,    // Used to support ES6 tagged template literal.
      COMPUTED_PROP_METHOD = 72,  // A computed property that has the method
                                  // syntax ( [prop]() {...} ) rather than the
                                  // property definition syntax ( [prop]: value ).
      COMPUTED_PROP_GETTER = 73,  // A computed property in a getter, e.g.
                                  // var obj = { get [prop]() {...} };
      COMPUTED_PROP_SETTER = 74,  // A computed property in a setter, e.g.
                                  // var obj = { set [prop](val) {...} };
      ANALYZED_DURING_GTI  = 75,  // In GlobalTypeInfo, we mark some AST nodes
                                  // to avoid analyzing them during
                                  // NewTypeInference. We remove this attribute
                                  // in the fwd direction of NewTypeInference.
      CONSTANT_PROPERTY_DEF = 76, // Used to communicate information between
                                  // GlobalTypeInfo and NewTypeInference.
                                  // We use this to tag getprop nodes that
                                  // declare properties.
      DECLARED_TYPE_EXPR = 77;    // Used to attach TypeDeclarationNode ASTs to
                                  // Nodes which represent a typed NAME or
                                  // FUNCTION.


  public static final int   // flags for INCRDECR_PROP
      DECR_FLAG = 0x1,
      POST_FLAG = 0x2;

  private static final String propToString(int propType) {
      switch (propType) {
        case VAR_ARGS_NAME:      return "var_args_name";

        case JSDOC_INFO_PROP:    return "jsdoc_info";

        case INCRDECR_PROP:      return "incrdecr";
        case QUOTED_PROP:        return "quoted";
        case OPT_ARG_NAME:       return "opt_arg";

        case SYNTHETIC_BLOCK_PROP: return "synthetic";
        case ADDED_BLOCK:        return "added_block";
        case ORIGINALNAME_PROP:  return "originalname";
        case SIDE_EFFECT_FLAGS:  return "side_effect_flags";

        case IS_CONSTANT_NAME:   return "is_constant_name";
        case IS_NAMESPACE:       return "is_namespace";
        case DIRECTIVES:         return "directives";
        case DIRECT_EVAL:        return "direct_eval";
        case FREE_CALL:          return "free_call";
        case STATIC_SOURCE_FILE: return "source_file";
        case INPUT_ID:           return "input_id";
        case LENGTH:             return "length";
        case SLASH_V:            return "slash_v";
        case INFERRED_FUNCTION:  return "inferred";
        case CHANGE_TIME:        return "change_time";
        case REFLECTED_OBJECT:   return "reflected_object";
        case STATIC_MEMBER:      return "static_member";
        case GENERATOR_FN:       return "generator_fn";
        case ARROW_FN:           return "arrow_fn";
        case YIELD_FOR:          return "yield_for";
        case EXPORT_DEFAULT:     return "export_default";
        case EXPORT_ALL_FROM:    return "export_all_from";
        case IS_CONSTANT_VAR:    return "is_constant_var";
        case GENERATOR_MARKER:   return "is_generator_marker";
        case GENERATOR_SAFE:     return "is_generator_safe";
        case COOKED_STRING:      return "cooked_string";
        case RAW_STRING_VALUE:   return "raw_string_value";
        case COMPUTED_PROP_METHOD: return "computed_prop_method";
        case COMPUTED_PROP_GETTER: return "computed_prop_getter";
        case COMPUTED_PROP_SETTER: return "computed_prop_setter";
        case ANALYZED_DURING_GTI:  return "analyzed_during_gti";
        case CONSTANT_PROPERTY_DEF: return "constant_property_def";
        case DECLARED_TYPE_EXPR: return "declared_type_expr";
        default:
          throw new IllegalStateException("unexpected prop id " + propType);
      }
  }

  /**
   * Represents a node in the type declaration AST.
   */
  public static class TypeDeclarationNode extends Node {

    private static final long serialVersionUID = 1L;

    public TypeDeclarationNode(int nodeType) {
      super(nodeType);
    }

    public TypeDeclarationNode(int nodeType, Node child) {
      super(nodeType, child);
    }

    public TypeDeclarationNode(int nodeType, Node left, Node right) {
      super(nodeType, left, right);
    }

    public TypeDeclarationNode(int nodeType, Node left, Node mid, Node right) {
      super(nodeType, left, mid, right);
    }
  }

  private static class NumberNode extends Node {

    private static final long serialVersionUID = 1L;

    NumberNode(double number) {
      super(Token.NUMBER);
      this.number = number;
    }

    public NumberNode(double number, int lineno, int charno) {
      super(Token.NUMBER, lineno, charno);
      this.number = number;
    }

    @Override
    public double getDouble() {
      return this.number;
    }

    @Override
    public void setDouble(double d) {
      this.number = d;
    }

    @Override
    boolean isEquivalentTo(
        Node node, boolean compareType, boolean recur, boolean jsDoc) {
      boolean equiv = super.isEquivalentTo(node, compareType, recur, jsDoc);
      if (equiv) {
        double thisValue = getDouble();
        double thatValue = ((NumberNode) node).getDouble();
        if (thisValue == thatValue) {
          // detect the difference between 0.0 and -0.0.
          return (thisValue != 0.0) || (1 / thisValue == 1 / thatValue);
        }
      }
      return false;
    }

    private double number;
  }

  private static class StringNode extends Node {

    private static final long serialVersionUID = 1L;

    StringNode(int type, String str) {
      super(type);
      if (null == str) {
        throw new IllegalArgumentException("StringNode: str is null");
      }
      this.str = str;
    }

    StringNode(int type, String str, int lineno, int charno) {
      super(type, lineno, charno);
      if (null == str) {
        throw new IllegalArgumentException("StringNode: str is null");
      }
      this.str = str;
    }

    /**
     * returns the string content.
     * @return non null.
     */
    @Override
    public String getString() {
      return this.str;
    }

    /**
     * sets the string content.
     * @param str the new value.  Non null.
     */
    @Override
    public void setString(String str) {
      if (null == str) {
        throw new IllegalArgumentException("StringNode: str is null");
      }
      this.str = str;
    }

    @Override
    boolean isEquivalentTo(
        Node node, boolean compareType, boolean recur, boolean jsDoc) {
      return (super.isEquivalentTo(node, compareType, recur, jsDoc)
          && this.str.equals(((StringNode) node).str));
    }

    /**
     * If the property is not defined, this was not a quoted key.  The
     * QUOTED_PROP int property is only assigned to STRING tokens used as
     * object lit keys.
     * @return true if this was a quoted string key in an object literal.
     */
    @Override
    public boolean isQuotedString() {
      return getBooleanProp(QUOTED_PROP);
    }

    /**
     * This should only be called for STRING nodes created in object lits.
     */
    @Override
    public void setQuotedString() {
      putBooleanProp(QUOTED_PROP, true);
    }

    private String str;
  }

  // PropListItems must be immutable so that they can be shared.
  private interface PropListItem {
    int getType();
    PropListItem getNext();
    PropListItem chain(PropListItem next);
    Object getObjectValue();
    int getIntValue();
  }

  private abstract static class AbstractPropListItem
      implements PropListItem, Serializable {
    private static final long serialVersionUID = 1L;

    private final PropListItem next;
    private final int propType;

    AbstractPropListItem(int propType, PropListItem next) {
      this.propType = propType;
      this.next = next;
    }

    @Override
    public int getType() {
      return propType;
    }

    @Override
    public PropListItem getNext() {
      return next;
    }

    @Override
    public abstract PropListItem chain(PropListItem next);
  }

  // A base class for Object storing props
  private static class ObjectPropListItem
      extends AbstractPropListItem {
    private static final long serialVersionUID = 1L;

    private final Object objectValue;

    ObjectPropListItem(int propType, Object objectValue, PropListItem next) {
      super(propType, next);
      this.objectValue = objectValue;
    }

    @Override
    public int getIntValue() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object getObjectValue() {
      return objectValue;
    }

    @Override
    public String toString() {
      return String.valueOf(objectValue);
    }

    @Override
    public PropListItem chain(PropListItem next) {
      return new ObjectPropListItem(getType(), objectValue, next);
    }
  }

  // A base class for int storing props
  private static class IntPropListItem extends AbstractPropListItem {
    private static final long serialVersionUID = 1L;

    final int intValue;

    IntPropListItem(int propType, int intValue, PropListItem next) {
      super(propType, next);
      this.intValue = intValue;
    }

    @Override
    public int getIntValue() {
      return intValue;
    }

    @Override
    public Object getObjectValue() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return String.valueOf(intValue);
    }

    @Override
    public PropListItem chain(PropListItem next) {
      return new IntPropListItem(getType(), intValue, next);
    }
  }

  public Node(int nodeType) {
    type = nodeType;
    parent = null;
    sourcePosition = -1;
  }

  public Node(int nodeType, Node child) {
    Preconditions.checkArgument(child.parent == null,
        "new child has existing parent");
    Preconditions.checkArgument(child.next == null,
        "new child has existing sibling");

    type = nodeType;
    parent = null;
    first = last = child;
    child.next = null;
    child.parent = this;
    sourcePosition = -1;
  }

  public Node(int nodeType, Node left, Node right) {
    Preconditions.checkArgument(left.parent == null,
        "first new child has existing parent");
    Preconditions.checkArgument(left.next == null,
        "first new child has existing sibling");
    Preconditions.checkArgument(right.parent == null,
        "second new child has existing parent");
    Preconditions.checkArgument(right.next == null,
        "second new child has existing sibling");
    type = nodeType;
    parent = null;
    first = left;
    last = right;
    left.next = right;
    left.parent = this;
    right.next = null;
    right.parent = this;
    sourcePosition = -1;
  }

  public Node(int nodeType, Node left, Node mid, Node right) {
    Preconditions.checkArgument(left.parent == null);
    Preconditions.checkArgument(left.next == null);
    Preconditions.checkArgument(mid.parent == null);
    Preconditions.checkArgument(mid.next == null);
    Preconditions.checkArgument(right.parent == null);
    Preconditions.checkArgument(right.next == null);
    type = nodeType;
    parent = null;
    first = left;
    last = right;
    left.next = mid;
    left.parent = this;
    mid.next = right;
    mid.parent = this;
    right.next = null;
    right.parent = this;
    sourcePosition = -1;
  }

  public Node(int nodeType, Node left, Node mid, Node mid2, Node right) {
    Preconditions.checkArgument(left.parent == null);
    Preconditions.checkArgument(left.next == null);
    Preconditions.checkArgument(mid.parent == null);
    Preconditions.checkArgument(mid.next == null);
    Preconditions.checkArgument(mid2.parent == null);
    Preconditions.checkArgument(mid2.next == null);
    Preconditions.checkArgument(right.parent == null);
    Preconditions.checkArgument(right.next == null);
    type = nodeType;
    parent = null;
    first = left;
    last = right;
    left.next = mid;
    left.parent = this;
    mid.next = mid2;
    mid.parent = this;
    mid2.next = right;
    mid2.parent = this;
    right.next = null;
    right.parent = this;
    sourcePosition = -1;
  }

  public Node(int nodeType, int lineno, int charno) {
    type = nodeType;
    parent = null;
    sourcePosition = mergeLineCharNo(lineno, charno);
  }

  public Node(int nodeType, Node child, int lineno, int charno) {
    this(nodeType, child);
    sourcePosition = mergeLineCharNo(lineno, charno);
  }

  public Node(int nodeType, Node left, Node right, int lineno, int charno) {
    this(nodeType, left, right);
    sourcePosition = mergeLineCharNo(lineno, charno);
  }

  public Node(int nodeType, Node left, Node mid, Node right,
      int lineno, int charno) {
    this(nodeType, left, mid, right);
    sourcePosition = mergeLineCharNo(lineno, charno);
  }

  public Node(int nodeType, Node left, Node mid, Node mid2, Node right,
      int lineno, int charno) {
    this(nodeType, left, mid, mid2, right);
    sourcePosition = mergeLineCharNo(lineno, charno);
  }

  public Node(int nodeType, Node[] children, int lineno, int charno) {
    this(nodeType, children);
    sourcePosition = mergeLineCharNo(lineno, charno);
  }

  public Node(int nodeType, Node[] children) {
    this.type = nodeType;
    parent = null;
    if (children.length != 0) {
      this.first = children[0];
      this.last = children[children.length - 1];

      for (int i = 1; i < children.length; i++) {
        if (null != children[i - 1].next) {
          // fail early on loops. implies same node in array twice
          throw new IllegalArgumentException("duplicate child");
        }
        children[i - 1].next = children[i];
        Preconditions.checkArgument(children[i - 1].parent == null);
        children[i - 1].parent = this;
      }
      Preconditions.checkArgument(children[children.length - 1].parent == null);
      children[children.length - 1].parent = this;

      if (null != this.last.next) {
        // fail early on loops. implies same node in array twice
        throw new IllegalArgumentException("duplicate child");
      }
    }
  }

  public static Node newNumber(double number) {
    return new NumberNode(number);
  }

  public static Node newNumber(double number, int lineno, int charno) {
    return new NumberNode(number, lineno, charno);
  }

  public static Node newString(String str) {
    return new StringNode(Token.STRING, str);
  }

  public static Node newString(int type, String str) {
    return new StringNode(type, str);
  }

  public static Node newString(String str, int lineno, int charno) {
    return new StringNode(Token.STRING, str, lineno, charno);
  }

  public static Node newString(int type, String str, int lineno, int charno) {
    return new StringNode(type, str, lineno, charno);
  }

  public int getType() {
    return type;
  }

  public void setType(int type) {
    this.type = type;
  }

  public boolean hasChildren() {
    return first != null;
  }

  public Node getFirstChild() {
    return first;
  }

  public Node getLastChild() {
    return last;
  }

  public Node getNext() {
    return next;
  }

  public Node getChildBefore(Node child) {
    if (child == first) {
      return null;
    }
    Node n = first;
    if (n == null) {
      throw new RuntimeException("node is not a child");
    }

    while (n.next != child) {
      n = n.next;
      if (n == null) {
        throw new RuntimeException("node is not a child");
      }
    }
    return n;
  }

  public Node getChildAtIndex(int i) {
    Node n = first;
    while (i > 0) {
      n = n.next;
      i--;
    }
    return n;
  }

  public int getIndexOfChild(Node child) {
    Node n = first;
    int i = 0;
    while (n != null) {
      if (child == n) {
        return i;
      }

      n = n.next;
      i++;
    }
    return -1;
  }

  public Node getLastSibling() {
    Node n = this;
    while (n.next != null) {
      n = n.next;
    }
    return n;
  }

  public void addChildToFront(Node child) {
    Preconditions.checkArgument(child.parent == null);
    Preconditions.checkArgument(child.next == null);
    child.parent = this;
    child.next = first;
    first = child;
    if (last == null) {
      last = child;
    }
  }

  public void addChildToBack(Node child) {
    Preconditions.checkArgument(child.parent == null);
    Preconditions.checkArgument(child.next == null);
    child.parent = this;
    child.next = null;
    if (last == null) {
      first = last = child;
      return;
    }
    last.next = child;
    last = child;
  }

  public void addChildrenToFront(Node children) {
    for (Node child = children; child != null; child = child.next) {
      Preconditions.checkArgument(child.parent == null);
      child.parent = this;
    }
    Node lastSib = children.getLastSibling();
    lastSib.next = first;
    first = children;
    if (last == null) {
      last = lastSib;
    }
  }

  public void addChildrenToBack(Node children) {
    addChildrenAfter(children, getLastChild());
  }

  /**
   * Add 'child' before 'node'.
   */
  public void addChildBefore(Node newChild, Node node) {
    Preconditions.checkArgument(node != null && node.parent == this,
        "The existing child node of the parent should not be null.");
    Preconditions.checkArgument(newChild.next == null,
        "The new child node has siblings.");
    Preconditions.checkArgument(newChild.parent == null,
        "The new child node already has a parent.");
    if (first == node) {
      newChild.parent = this;
      newChild.next = first;
      first = newChild;
      return;
    }
    Node prev = getChildBefore(node);
    addChildAfter(newChild, prev);
  }

  /**
   * Add 'child' after 'node'.
   */
  public void addChildAfter(Node newChild, Node node) {
    Preconditions.checkArgument(newChild.next == null,
        "The new child node has siblings.");
    addChildrenAfter(newChild, node);
  }

  /**
   * Add all children after 'node'.
   */
  public void addChildrenAfter(Node children, Node node) {
    Preconditions.checkArgument(node == null || node.parent == this);
    for (Node child = children; child != null; child = child.next) {
      Preconditions.checkArgument(child.parent == null);
      child.parent = this;
    }

    Node lastSibling = children.getLastSibling();
    if (node != null) {
      Node oldNext = node.next;
      node.next = children;
      lastSibling.next = oldNext;
      if (node == last) {
        last = lastSibling;
      }
    } else {
      // Append to the beginning.
      if (first != null) {
        lastSibling.next = first;
      } else {
        last = lastSibling;
      }
      first = children;
    }
  }

  /**
   * Detach a child from its parent and siblings.
   */
  public void removeChild(Node child) {
    Node prev = getChildBefore(child);
    if (prev == null) {
      first = first.next;
    } else {
      prev.next = child.next;
    }
    if (child == last) {
      last = prev;
    }
    child.next = null;
    child.parent = null;
  }

  /**
   * Detaches child from Node and replaces it with newChild.
   */
  public void replaceChild(Node child, Node newChild) {
    Preconditions.checkArgument(newChild.next == null,
        "The new child node has siblings.");
    Preconditions.checkArgument(newChild.parent == null,
        "The new child node already has a parent.");

    // Copy over important information.
    newChild.copyInformationFrom(child);

    newChild.next = child.next;
    newChild.parent = this;
    if (child == first) {
      first = newChild;
    } else {
      Node prev = getChildBefore(child);
      prev.next = newChild;
    }
    if (child == last) {
      last = newChild;
    }
    child.next = null;
    child.parent = null;
  }

  public void replaceChildAfter(Node prevChild, Node newChild) {
    Preconditions.checkArgument(prevChild.parent == this,
        "prev is not a child of this node.");

    Preconditions.checkArgument(newChild.next == null,
        "The new child node has siblings.");
    Preconditions.checkArgument(newChild.parent == null,
        "The new child node already has a parent.");

    // Copy over important information.
    newChild.copyInformationFrom(prevChild);

    Node child = prevChild.next;
    newChild.next = child.next;
    newChild.parent = this;
    prevChild.next = newChild;
    if (child == last) {
      last = newChild;
    }
    child.next = null;
    child.parent = null;
  }

  @VisibleForTesting
  PropListItem lookupProperty(int propType) {
    PropListItem x = propListHead;
    while (x != null && propType != x.getType()) {
      x = x.getNext();
    }
    return x;
  }

  /**
   * Clone the properties from the provided node without copying
   * the property object.  The receiving node may not have any
   * existing properties.
   * @param other The node to clone properties from.
   * @return this node.
   */
  public Node clonePropsFrom(Node other) {
    Preconditions.checkState(this.propListHead == null,
        "Node has existing properties.");
    this.propListHead = other.propListHead;
    return this;
  }

  public void removeProp(int propType) {
    PropListItem result = removeProp(propListHead, propType);
    if (result != propListHead) {
      propListHead = result;
    }
  }

  /**
   * @param item The item to inspect
   * @param propType The property to look for
   * @return The replacement list if the property was removed, or
   *   'item' otherwise.
   */
  private PropListItem removeProp(PropListItem item, int propType) {
    if (item == null) {
      return null;
    } else if (item.getType() == propType) {
      return item.getNext();
    } else {
      PropListItem result = removeProp(item.getNext(), propType);
      if (result != item.getNext()) {
        return item.chain(result);
      } else {
        return item;
      }
    }
  }

  public Object getProp(int propType) {
    PropListItem item = lookupProperty(propType);
    if (item == null) {
      return null;
    }
    return item.getObjectValue();
  }

  public boolean getBooleanProp(int propType) {
    return getIntProp(propType) != 0;
  }

  /**
   * Returns the integer value for the property, or 0 if the property
   * is not defined.
   */
  public int getIntProp(int propType) {
    PropListItem item = lookupProperty(propType);
    if (item == null) {
      return 0;
    }
    return item.getIntValue();
  }

  public int getExistingIntProp(int propType) {
    PropListItem item = lookupProperty(propType);
    if (item == null) {
      throw new IllegalStateException("missing prop: " + propType);
    }
    return item.getIntValue();
  }

  public void putProp(int propType, Object value) {
    removeProp(propType);
    if (value != null) {
      propListHead = createProp(propType, value, propListHead);
    }
  }

  public void putBooleanProp(int propType, boolean value) {
    putIntProp(propType, value ? 1 : 0);
  }

  public void putIntProp(int propType, int value) {
    removeProp(propType);
    if (value != 0) {
      propListHead = createProp(propType, value, propListHead);
    }
  }

  /**
   * TODO(alexeagle): this should take a TypeDeclarationNode
   * @param typeExpression
   */
  public void setDeclaredTypeExpression(Node typeExpression) {
    putProp(DECLARED_TYPE_EXPR, typeExpression);
  }

  /**
   * Returns the syntactical type specified on this node. Not to be confused
   * with {@link #getJSType()} which returns the compiler-inferred type.
   */
  public TypeDeclarationNode getDeclaredTypeExpression() {
    return (TypeDeclarationNode) getProp(DECLARED_TYPE_EXPR);
  }

  PropListItem createProp(int propType, Object value, PropListItem next) {
    return new ObjectPropListItem(propType, value, next);
  }

  PropListItem createProp(int propType, int value, PropListItem next) {
    return new IntPropListItem(propType, value, next);
  }

  // Gets all the property types, in sorted order.
  private int[] getSortedPropTypes() {
    int count = 0;
    for (PropListItem x = propListHead; x != null; x = x.getNext()) {
      count++;
    }

    int[] keys = new int[count];
    for (PropListItem x = propListHead; x != null; x = x.getNext()) {
      count--;
      keys[count] = x.getType();
    }

    Arrays.sort(keys);
    return keys;
  }

  /** Can only be called when <tt>getType() == TokenStream.NUMBER</tt> */
  public double getDouble() throws UnsupportedOperationException {
    if (this.getType() == Token.NUMBER) {
      throw new IllegalStateException(
          "Number node not created with Node.newNumber");
    } else {
      throw new UnsupportedOperationException(this + " is not a number node");
    }
  }

  /**
   * Can only be called when <tt>getType() == Token.NUMBER</tt>
   * @param value value to set.
   */
  public void setDouble(double value) throws UnsupportedOperationException {
    if (this.getType() == Token.NUMBER) {
      throw new IllegalStateException(
          "Number node not created with Node.newNumber");
    } else {
      throw new UnsupportedOperationException(this + " is not a string node");
    }
  }

  /** Can only be called when node has String context. */
  public String getString() throws UnsupportedOperationException {
    if (this.getType() == Token.STRING) {
      throw new IllegalStateException(
          "String node not created with Node.newString");
    } else {
      throw new UnsupportedOperationException(this + " is not a string node");
    }
  }

  /**
   * Can only be called for a Token.STRING or Token.NAME.
   * @param value the value to set.
   */
  public void setString(String value) throws UnsupportedOperationException {
    if (this.getType() == Token.STRING || this.getType() == Token.NAME) {
      throw new IllegalStateException(
          "String node not created with Node.newString");
    } else {
      throw new UnsupportedOperationException(this + " is not a string node");
    }
  }

  @Override
  public String toString() {
    return toString(true, true, true);
  }

  public String toString(
      boolean printSource,
      boolean printAnnotations,
      boolean printType) {
    StringBuilder sb = new StringBuilder();
    toString(sb, printSource, printAnnotations, printType);
    return sb.toString();
  }

  private void toString(
      StringBuilder sb,
      boolean printSource,
      boolean printAnnotations,
      boolean printType) {
    sb.append(Token.name(type));
    if (this instanceof StringNode) {
      sb.append(' ');
      sb.append(getString());
    } else if (type == Token.FUNCTION) {
      sb.append(' ');
      // In the case of JsDoc trees, the first child is often not a string
      // which causes exceptions to be thrown when calling toString or
      // toStringTree.
      if (first == null || first.getType() != Token.NAME) {
        sb.append("<invalid>");
      } else {
        sb.append(first.getString());
      }
    } else if (type == Token.NUMBER) {
      sb.append(' ');
      sb.append(getDouble());
    }
    if (printSource) {
      int lineno = getLineno();
      if (lineno != -1) {
        sb.append(' ');
        sb.append(lineno);
      }
    }

    if (printAnnotations) {
      int[] keys = getSortedPropTypes();
      for (int i = 0; i < keys.length; i++) {
        int type = keys[i];
        PropListItem x = lookupProperty(type);
        sb.append(" [");
        sb.append(propToString(type));
        sb.append(": ");
        String value;
        switch (type) {
          default:
            value = x.toString();
            break;
        }
        sb.append(value);
        sb.append(']');
      }
    }

    if (printType) {
      if (typei != null) {
        String typeString = typei.toString();
        if (typeString != null) {
          sb.append(" : ");
          sb.append(typeString);
        }
      }
    }
  }


  public String toStringTree() {
    return toStringTreeImpl();
  }

  private String toStringTreeImpl() {
    try {
      StringBuilder s = new StringBuilder();
      appendStringTree(s);
      return s.toString();
    } catch (IOException e) {
      throw new RuntimeException("Should not happen\n" + e);
    }
  }

  public void appendStringTree(Appendable appendable) throws IOException {
    toStringTreeHelper(this, 0, appendable);
  }

  private static void toStringTreeHelper(Node n, int level, Appendable sb)
      throws IOException {
    for (int i = 0; i != level; ++i) {
      sb.append("    ");
    }
    sb.append(n.toString());
    sb.append('\n');
    for (Node cursor = n.getFirstChild();
         cursor != null;
         cursor = cursor.getNext()) {
      toStringTreeHelper(cursor, level + 1, sb);
    }
  }

  int type;              // type of the node; Token.NAME for example
  Node next;             // next sibling
  private Node first;    // first element of a linked list of children
  private Node last;     // last element of a linked list of children

  /**
   * Linked list of properties. Since vast majority of nodes would have
   * no more then 2 properties, linked list saves memory and provides
   * fast lookup. If this does not holds, propListHead can be replaced
   * by UintMap.
   */
  private PropListItem propListHead;

  /**
   * COLUMN_BITS represents how many of the lower-order bits of
   * sourcePosition are reserved for storing the column number.
   * Bits above these store the line number.
   * This gives us decent position information for everything except
   * files already passed through a minimizer, where lines might
   * be longer than 4096 characters.
   */
  public static final int COLUMN_BITS = 12;

  /**
   * MAX_COLUMN_NUMBER represents the maximum column number that can
   * be represented.  JSCompiler's modifications to Rhino cause all
   * tokens located beyond the maximum column to MAX_COLUMN_NUMBER.
   */
  public static final int MAX_COLUMN_NUMBER = (1 << COLUMN_BITS) - 1;

  /**
   * COLUMN_MASK stores a value where bits storing the column number
   * are set, and bits storing the line are not set.  It's handy for
   * separating column number from line number.
   */
  public static final int COLUMN_MASK = MAX_COLUMN_NUMBER;

  /**
   * Source position of this node. The position is encoded with the
   * column number in the low 12 bits of the integer, and the line
   * number in the rest.  Create some handy constants so we can change this
   * size if we want.
   */
  private int sourcePosition;

  private TypeI typei;

  private Node parent;

  //==========================================================================
  // Source position management

  public void setStaticSourceFile(StaticSourceFile file) {
    this.putProp(STATIC_SOURCE_FILE, file);
  }

  /** Sets the source file to a non-extern file of the given name. */
  public void setSourceFileForTesting(String name) {
    this.putProp(STATIC_SOURCE_FILE, new SimpleSourceFile(name, false));
  }

  public String getSourceFileName() {
    StaticSourceFile file = getStaticSourceFile();
    return file == null ? null : file.getName();
  }

  /** Returns the source file associated with this input. May be null */
  public StaticSourceFile getStaticSourceFile() {
    return ((StaticSourceFile) this.getProp(STATIC_SOURCE_FILE));
  }

  /**
   * @param inputId
   */
  public void setInputId(InputId inputId) {
    this.putProp(INPUT_ID, inputId);
  }

  /**
   * @return The Id of the CompilerInput associated with this Node.
   */
  public InputId getInputId() {
    return ((InputId) this.getProp(INPUT_ID));
  }

  public boolean isFromExterns() {
    StaticSourceFile file = getStaticSourceFile();
    return file == null ? false : file.isExtern();
  }

  public int getLength() {
    return getIntProp(LENGTH);
  }

  public void setLength(int length) {
    putIntProp(LENGTH, length);
  }

  public int getLineno() {
    return extractLineno(sourcePosition);
  }

  public int getCharno() {
    return extractCharno(sourcePosition);
  }

  public int getSourceOffset() {
    StaticSourceFile file = getStaticSourceFile();
    if (file == null) {
      return -1;
    }
    int lineno = getLineno();
    if (lineno == -1) {
      return -1;
    }
    return file.getLineOffset(lineno) + getCharno();
  }

  public int getSourcePosition() {
    return sourcePosition;
  }

  public void setLineno(int lineno) {
      int charno = getCharno();
      if (charno == -1) {
        charno = 0;
      }
      sourcePosition = mergeLineCharNo(lineno, charno);
  }

  public void setCharno(int charno) {
      sourcePosition = mergeLineCharNo(getLineno(), charno);
  }

  public void setSourceEncodedPosition(int sourcePosition) {
    this.sourcePosition = sourcePosition;
  }

  public void setSourceEncodedPositionForTree(int sourcePosition) {
    this.sourcePosition = sourcePosition;

    for (Node child = getFirstChild();
         child != null; child = child.getNext()) {
      child.setSourceEncodedPositionForTree(sourcePosition);
    }
  }

  /**
   * Merges the line number and character number in one integer. The Character
   * number takes the first 12 bits and the line number takes the rest. If
   * the character number is greater than <code>2<sup>12</sup>-1</code> it is
   * adjusted to <code>2<sup>12</sup>-1</code>.
   */
  protected static int mergeLineCharNo(int lineno, int charno) {
    if (lineno < 0 || charno < 0) {
      return -1;
    } else if ((charno & ~COLUMN_MASK) != 0) {
      return lineno << COLUMN_BITS | COLUMN_MASK;
    } else {
      return lineno << COLUMN_BITS | (charno & COLUMN_MASK);
    }
  }

  /**
   * Extracts the line number and character number from a merged line char
   * number (see {@link #mergeLineCharNo(int, int)}).
   */
  protected static int extractLineno(int lineCharNo) {
    if (lineCharNo == -1) {
      return -1;
    } else {
      return lineCharNo >>> COLUMN_BITS;
    }
  }

  /**
   * Extracts the character number and character number from a merged line
   * char number (see {@link #mergeLineCharNo(int, int)}).
   */
  protected static int extractCharno(int lineCharNo) {
    if (lineCharNo == -1) {
      return -1;
    } else {
      return lineCharNo & COLUMN_MASK;
    }
  }

  //==========================================================================
  // Iteration

  /**
   * <p>Return an iterable object that iterates over this node's children.
   * The iterator does not support the optional operation
   * {@link Iterator#remove()}.</p>
   *
   * <p>To iterate over a node's children, one can write</p>
   * <pre>Node n = ...;
   * for (Node child : n.children()) { ...</pre>
   */
  public Iterable<Node> children() {
    if (first == null) {
      return Collections.emptySet();
    } else {
      return new SiblingNodeIterable(first);
    }
  }

  /**
   * <p>Return an iterable object that iterates over this node's siblings.
   * The iterator does not support the optional operation
   * {@link Iterator#remove()}.</p>
   *
   * <p>To iterate over a node's siblings, one can write</p>
   * <pre>Node n = ...;
   * for (Node sibling : n.siblings()) { ...</pre>
   */
  public Iterable<Node> siblings() {
    return new SiblingNodeIterable(this);
  }

  /**
   * @see Node#siblings()
   */
  private static final class SiblingNodeIterable
      implements Iterable<Node>, Iterator<Node> {
    private final Node start;
    private Node current;
    private boolean used;

    SiblingNodeIterable(Node start) {
      this.start = start;
      this.current = start;
      this.used = false;
    }

    @Override
    public Iterator<Node> iterator() {
      if (!used) {
        used = true;
        return this;
      } else {
        // We have already used the current object as an iterator;
        // we must create a new SiblingNodeIterable based on this
        // iterable's start node.
        //
        // Since the primary use case for Node.children is in for
        // loops, this branch is extremely unlikely.
        return (new SiblingNodeIterable(start)).iterator();
      }
    }

    @Override
    public boolean hasNext() {
      return current != null;
    }

    @Override
    public Node next() {
      if (current == null) {
        throw new NoSuchElementException();
      }
      try {
        return current;
      } finally {
        current = current.getNext();
      }
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  // ==========================================================================
  // Accessors

  PropListItem getPropListHeadForTesting() {
    return propListHead;
  }

  public Node getParent() {
    return parent;
  }

  /**
   * Gets the ancestor node relative to this.
   *
   * @param level 0 = this, 1 = the parent, etc.
   */
  public Node getAncestor(int level) {
    Preconditions.checkArgument(level >= 0);
    Node node = this;
    while (node != null && level-- > 0) {
      node = node.getParent();
    }
    return node;
  }

  /**
   * Iterates all of the node's ancestors excluding itself.
   */
  public AncestorIterable getAncestors() {
    return new AncestorIterable(this.getParent());
  }

  /**
   * Iterator to go up the ancestor tree.
   */
  public static class AncestorIterable implements Iterable<Node> {
    private Node cur;

    /**
     * @param cur The node to start.
     */
    AncestorIterable(Node cur) {
      this.cur = cur;
    }

    @Override
    public Iterator<Node> iterator() {
      return new Iterator<Node>() {
        @Override
        public boolean hasNext() {
          return cur != null;
        }

        @Override
        public Node next() {
          if (!hasNext()) {
            throw new NoSuchElementException();
          }
          Node n = cur;
          cur = cur.getParent();
          return n;
        }

        @Override
        public void remove() {
          throw new UnsupportedOperationException();
        }
      };
    }
  }

  /**
   * Check for one child more efficiently than by iterating over all the
   * children as is done with Node.getChildCount().
   *
   * @return Whether the node has exactly one child.
   */
  public boolean hasOneChild() {
    return first != null && first == last;
  }

  /**
   * Check for more than one child more efficiently than by iterating over all
   * the children as is done with Node.getChildCount().
   *
   * @return Whether the node more than one child.
   */
  public boolean hasMoreThanOneChild() {
    return first != null && first != last;
  }

  public int getChildCount() {
    int c = 0;
    for (Node n = first; n != null; n = n.next) {
      c++;
    }
    return c;
  }

  // Intended for testing and verification only.
  public boolean hasChild(Node child) {
    for (Node n = first; n != null; n = n.getNext()) {
      if (child == n) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if the subtree under this node is the same as another subtree.
   * Returns null if it's equal, or a message describing the differences.
   * Should be called with {@code this} as the "expected" node and
   * {@code actual} as the "actual" node.
   */
  @VisibleForTesting
  public String checkTreeEquals(Node actual) {
      NodeMismatch diff = checkTreeEqualsImpl(actual);
      if (diff != null) {
        return "Node tree inequality:" +
            "\nTree1:\n" + toStringTree() +
            "\n\nTree2:\n" + actual.toStringTree() +
            "\n\nSubtree1: " + diff.nodeA.toStringTree() +
            "\n\nSubtree2: " + diff.nodeB.toStringTree();
      }
      return null;
  }

  /**
   * Checks if the subtree under this node is the same as another subtree.
   * Returns null if it's equal, or a message describing the differences.
   * Considers two nodes to be unequal if their JSDocInfo doesn't match.
   * Should be called with {@code this} as the "expected" node and
   * {@code actual} as the "actual" node.
   *
   * @see JSDocInfo#equals(Object)
   */
  @VisibleForTesting
  public String checkTreeEqualsIncludingJsDoc(Node actual) {
      NodeMismatch diff = checkTreeEqualsImpl(actual, true);
      if (diff != null) {
        if (diff.nodeA.isEquivalentTo(diff.nodeB, false, true, false)) {
          // The only difference is that the JSDoc is different on
          // the subtree.
          String jsDoc1 = diff.nodeA.getJSDocInfo() == null ?
              "(none)" :
              diff.nodeA.getJSDocInfo().toStringVerbose();

          String jsDoc2 = diff.nodeB.getJSDocInfo() == null ?
              "(none)" :
              diff.nodeB.getJSDocInfo().toStringVerbose();

          return "Node tree inequality:" +
              "\nTree:\n" + toStringTree() +
              "\n\nJSDoc differs on subtree: " + diff.nodeA +
              "\nExpected JSDoc: " + jsDoc1 +
              "\nActual JSDoc  : " + jsDoc2;
        }
        return "Node tree inequality:" +
            "\nExpected tree:\n" + toStringTree() +
            "\n\nActual tree:\n" + actual.toStringTree() +
            "\n\nExpected subtree: " + diff.nodeA.toStringTree() +
            "\n\nActual subtree: " + diff.nodeB.toStringTree();
      }
      return null;
  }

  /**
   * Compare this node to node2 recursively and return the first pair of nodes
   * that differs doing a preorder depth-first traversal. Package private for
   * testing. Returns null if the nodes are equivalent.
   */
  NodeMismatch checkTreeEqualsImpl(Node node2) {
    return checkTreeEqualsImpl(node2, false);
  }

  /**
   * Compare this node to node2 recursively and return the first pair of nodes
   * that differs doing a preorder depth-first traversal.
   * @param jsDoc Whether to check for differences in JSDoc.
   */
  private NodeMismatch checkTreeEqualsImpl(Node node2, boolean jsDoc) {
    if (!isEquivalentTo(node2, false, false, jsDoc)) {
      return new NodeMismatch(this, node2);
    }

    NodeMismatch res = null;
    Node n, n2;
    for (n = first, n2 = node2.first;
         res == null && n != null;
         n = n.next, n2 = n2.next) {
      if (node2 == null) {
        throw new IllegalStateException();
      }
      res = n.checkTreeEqualsImpl(n2, jsDoc);
      if (res != null) {
        return res;
      }
    }
    return res;
  }

  /** Returns true if this node is equivalent semantically to another */
  public boolean isEquivalentTo(Node node) {
    return isEquivalentTo(node, false, true, false);
  }

  /** Checks equivalence without going into child nodes */
  public boolean isEquivalentToShallow(Node node) {
    return isEquivalentTo(node, false, false, false);
  }

  /**
   * Returns true if this node is equivalent semantically to another and
   * the types are equivalent.
   */
  public boolean isEquivalentToTyped(Node node) {
    return isEquivalentTo(node, true, true, true);
  }

  /**
   * @param compareType Whether to compare the JSTypes of the nodes.
   * @param recurse Whether to compare the children of the current node, if
   *    not only the the count of the children are compared.
   * @param jsDoc Whether to check that the JsDoc of the nodes are equivalent.
   * @return Whether this node is equivalent semantically to the provided node.
   */
  boolean isEquivalentTo(
      Node node, boolean compareType, boolean recurse, boolean jsDoc) {
    if (type != node.getType()
        || getChildCount() != node.getChildCount()
        || this.getClass() != node.getClass()) {
      return false;
    }

    if (compareType && !JSType.isEquivalent((JSType) typei, node.getJSType())) {
      return false;
    }

    if (jsDoc && !JSDocInfo.areEquivalent(getJSDocInfo(), node.getJSDocInfo())) {
      return false;
    }

    if (this.getDeclaredTypeExpression() != null || node.getDeclaredTypeExpression() != null) {
      if (this.getDeclaredTypeExpression() == null || node.getDeclaredTypeExpression() == null
          || !this.getDeclaredTypeExpression()
          .isEquivalentTo(node.getDeclaredTypeExpression(), compareType, recurse, jsDoc)) {
        return false;
      }
    }

    if (type == Token.INC || type == Token.DEC) {
      int post1 = this.getIntProp(INCRDECR_PROP);
      int post2 = node.getIntProp(INCRDECR_PROP);
      if (post1 != post2) {
        return false;
      }
    } else if (type == Token.STRING || type == Token.STRING_KEY) {
      if (type == Token.STRING_KEY) {
        int quoted1 = this.getIntProp(QUOTED_PROP);
        int quoted2 = node.getIntProp(QUOTED_PROP);
        if (quoted1 != quoted2) {
          return false;
        }
      }

      int slashV1 = this.getIntProp(SLASH_V);
      int slashV2 = node.getIntProp(SLASH_V);
      if (slashV1 != slashV2) {
        return false;
      }
    } else if (type == Token.CALL) {
      if (this.getBooleanProp(FREE_CALL) != node.getBooleanProp(FREE_CALL)) {
        return false;
      }
    } else if (type == Token.FUNCTION) {
      if (this.isArrowFunction() != node.isArrowFunction()) {
        return false;
      }
    }

    if (recurse) {
      Node n, n2;
      for (n = first, n2 = node.first;
           n != null;
           n = n.next, n2 = n2.next) {
        if (!n.isEquivalentTo(n2, compareType, recurse, jsDoc)) {
          return false;
        }
      }
    }

    return true;
  }

  /**
   * This function takes a set of GETPROP nodes and produces a string that is
   * each property separated by dots. If the node ultimately under the left
   * sub-tree is not a simple name, this is not a valid qualified name.
   *
   * @return a null if this is not a qualified name, or a dot-separated string
   *         of the name and properties.
   */
  public String getQualifiedName() {
    if (type == Token.NAME) {
      String name = getString();
      return name.isEmpty() ? null : name;
    } else if (type == Token.GETPROP) {
      String left = getFirstChild().getQualifiedName();
      if (left == null) {
        return null;
      }
      return left + "." + getLastChild().getString();
    } else if (type == Token.THIS) {
      return "this";
    } else if (type == Token.SUPER) {
      return "super";
    } else {
      return null;
    }
  }

  /**
   * Returns whether a node corresponds to a simple or a qualified name, such as
   * <code>x</code> or <code>a.b.c</code> or <code>this.a</code>.
   */
  public boolean isQualifiedName() {
    switch (getType()) {
      case Token.NAME:
        return !getString().isEmpty();
      case Token.THIS:
        return true;
      case Token.GETPROP:
        return getFirstChild().isQualifiedName();
      default:
        return false;
    }
  }

  /**
   * Returns whether a node matches a simple or a qualified name, such as
   * <code>x</code> or <code>a.b.c</code> or <code>this.a</code>.
   */
  public boolean matchesQualifiedName(String name) {
    return name != null && matchesQualifiedName(name, name.length());
  }

  /**
   * Returns whether a node matches a simple or a qualified name, such as
   * <code>x</code> or <code>a.b.c</code> or <code>this.a</code>.
   */
  private boolean matchesQualifiedName(String qname, int endIndex) {
    int start = qname.lastIndexOf('.', endIndex - 1) + 1;

    switch (getType()) {
      case Token.NAME:
        String name = getString();
        return start == 0 && !name.isEmpty() &&
           name.length() == endIndex && qname.startsWith(name);
      case Token.THIS:
        return start == 0 && 4 == endIndex && qname.startsWith("this");
      case Token.SUPER:
        return start == 0 && 5 == endIndex && qname.startsWith("super");
      case Token.GETPROP:
        String prop = getLastChild().getString();
        return start > 1
            && prop.length() == endIndex - start
            && prop.regionMatches(0, qname, start, endIndex - start)
            && getFirstChild().matchesQualifiedName(qname, start - 1);
      default:
        return false;
    }
  }

  /**
   * Returns whether a node matches a simple or a qualified name, such as
   * <code>x</code> or <code>a.b.c</code> or <code>this.a</code>.
   */
  public boolean matchesQualifiedName(Node n) {
    if (n == null || n.type != type) {
      return false;
    }
    switch (type) {
      case Token.NAME:
        return !getString().isEmpty() && getString().equals(n.getString());
      case Token.THIS:
        return true;
      case Token.SUPER:
        return true;
      case Token.GETPROP:
        return getLastChild().getString().equals(n.getLastChild().getString())
            && getFirstChild().matchesQualifiedName(n.getFirstChild());
      default:
        return false;
    }
  }

  /**
   * Returns whether a node corresponds to a simple or a qualified name without
   * a "this" reference, such as <code>a.b.c</code>, but not <code>this.a</code>
   * .
   */
  public boolean isUnscopedQualifiedName() {
    switch (getType()) {
      case Token.NAME:
        return !getString().isEmpty();
      case Token.GETPROP:
        return getFirstChild().isUnscopedQualifiedName();
      default:
        return false;
    }
  }

  public boolean isValidAssignmentTarget() {
    switch (getType()) {
      // TODO(tbreisacher): Remove CAST from this list, and disallow
      // the cryptic case from cl/41958159.
      case Token.CAST:
      case Token.DEFAULT_VALUE:
      case Token.NAME:
      case Token.GETPROP:
      case Token.GETELEM:
      case Token.ARRAY_PATTERN:
      case Token.OBJECT_PATTERN:
        return true;
      default:
        return false;
    }
  }

  // ==========================================================================
  // Mutators

  /**
   * Removes this node from its parent. Equivalent to:
   * node.getParent().removeChild();
   */
  public Node detachFromParent() {
    Preconditions.checkState(parent != null);
    parent.removeChild(this);
    return this;
  }

  /**
   * Removes the first child of Node. Equivalent to:
   * node.removeChild(node.getFirstChild());
   *
   * @return The removed Node.
   */
  public Node removeFirstChild() {
    Node child = first;
    if (child != null) {
      removeChild(child);
    }
    return child;
  }

  /**
   * @return A Node that is the head of the list of children.
   */
  public Node removeChildren() {
    Node children = first;
    for (Node child = first; child != null; child = child.getNext()) {
      child.parent = null;
    }
    first = null;
    last = null;
    return children;
  }

  /**
   * Removes all children from this node and isolates the children from each
   * other.
   */
  public void detachChildren() {
    for (Node child = first; child != null;) {
      Node nextChild = child.getNext();
      child.parent = null;
      child.next = null;
      child = nextChild;
    }
    first = null;
    last = null;
  }

  public Node removeChildAfter(Node prev) {
    Preconditions.checkArgument(prev.parent == this,
        "prev is not a child of this node.");
    Preconditions.checkArgument(prev.next != null,
        "no next sibling.");

    Node child = prev.next;
    prev.next = child.next;
    if (child == last) {
      last = prev;
    }
    child.next = null;
    child.parent = null;
    return child;
  }

  /**
   * @return A detached clone of the Node, specifically excluding its children.
   */
  public Node cloneNode() {
    Node result;
    try {
      result = (Node) super.clone();
      // PropListItem lists are immutable and can be shared so there is no
      // need to clone them here.
      result.next = null;
      result.first = null;
      result.last = null;
      result.parent = null;
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e.getMessage());
    }
    return result;
  }

  /**
   * @return A detached clone of the Node and all its children.
   */
  public Node cloneTree() {
    Node result = cloneNode();
    for (Node n2 = getFirstChild(); n2 != null; n2 = n2.getNext()) {
      Node n2clone = n2.cloneTree();
      n2clone.parent = result;
      if (result.last != null) {
        result.last.next = n2clone;
      }
      if (result.first == null) {
        result.first = n2clone;
      }
      result.last = n2clone;
    }
    return result;
  }

  /**
   * Copies source file and name information from the other
   * node given to the current node. Used for maintaining
   * debug information across node append and remove operations.
   * @return this
   */
  // TODO(nicksantos): The semantics of this method are ill-defined. Delete it.
  public Node copyInformationFrom(Node other) {
    if (getProp(ORIGINALNAME_PROP) == null) {
      putProp(ORIGINALNAME_PROP, other.getProp(ORIGINALNAME_PROP));
    }

    if (getProp(STATIC_SOURCE_FILE) == null) {
      putProp(STATIC_SOURCE_FILE, other.getProp(STATIC_SOURCE_FILE));
      sourcePosition = other.sourcePosition;
    }

    return this;
  }

  /**
   * Copies source file and name information from the other node to the
   * entire tree rooted at this node.
   * @return this
   */
  // TODO(nicksantos): The semantics of this method are ill-defined. Delete it.
  public Node copyInformationFromForTree(Node other) {
    copyInformationFrom(other);
    for (Node child = getFirstChild();
         child != null; child = child.getNext()) {
      child.copyInformationFromForTree(other);
    }

    return this;
  }

  /**
   * Overwrite all the source information in this node with
   * that of {@code other}.
   */
  public Node useSourceInfoFrom(Node other) {
    putProp(ORIGINALNAME_PROP, other.getProp(ORIGINALNAME_PROP));
    putProp(STATIC_SOURCE_FILE, other.getProp(STATIC_SOURCE_FILE));
    sourcePosition = other.sourcePosition;
    return this;
  }

  public Node srcref(Node other) {
    return useSourceInfoFrom(other);
  }

  /**
   * Overwrite all the source information in this node and its subtree with
   * that of {@code other}.
   */
  public Node useSourceInfoFromForTree(Node other) {
    useSourceInfoFrom(other);
    for (Node child = getFirstChild();
         child != null; child = child.getNext()) {
      child.useSourceInfoFromForTree(other);
    }

    return this;
  }

  public Node srcrefTree(Node other) {
    return useSourceInfoFromForTree(other);
  }

  /**
   * Overwrite all the source information in this node with
   * that of {@code other} iff the source info is missing.
   */
  public Node useSourceInfoIfMissingFrom(Node other) {
    if (getProp(ORIGINALNAME_PROP) == null) {
      putProp(ORIGINALNAME_PROP, other.getProp(ORIGINALNAME_PROP));
    }

    if (getProp(STATIC_SOURCE_FILE) == null) {
      putProp(STATIC_SOURCE_FILE, other.getProp(STATIC_SOURCE_FILE));
      sourcePosition = other.sourcePosition;
    }

    return this;
  }

  /**
   * Overwrite all the source information in this node and its subtree with
   * that of {@code other} iff the source info is missing.
   */
  public Node useSourceInfoIfMissingFromForTree(Node other) {
    useSourceInfoIfMissingFrom(other);
    for (Node child = getFirstChild();
         child != null; child = child.getNext()) {
      child.useSourceInfoIfMissingFromForTree(other);
    }

    return this;
  }

  //==========================================================================
  // Custom annotations

  /**
   * Returns the compiled inferred type on this node. Not to be confused
   * with {@link #getDeclaredTypeExpression()} which returns the syntactically
   * specified type.
   */
  public JSType getJSType() {
    return (JSType) typei;
  }

  public void setJSType(JSType jsType) {
      this.typei = jsType;
  }

  public TypeI getTypeI() {
    return typei;
  }

  public void setTypeI(TypeI type) {
    this.typei = type;
  }

  public FileLevelJsDocBuilder getJsDocBuilderForNode() {
    return new FileLevelJsDocBuilder();
  }

  /**
   * An inner class that provides back-door access to the license
   * property of the JSDocInfo property for this node. This is only
   * meant to be used for top-level script nodes where the
   * {@link com.google.javascript.jscomp.parsing.JsDocInfoParser} needs to
   * be able to append directly to the top-level node, not just the
   * current node.
   */
  public class FileLevelJsDocBuilder {
    public void append(String fileLevelComment) {
      JSDocInfo jsDocInfo = getJSDocInfo();
      if (jsDocInfo == null) {
        // TODO(user): Is there a way to determine whether to
        // parse the JsDoc documentation from here?
        jsDocInfo = new JSDocInfo(false);
      }
      String license = jsDocInfo.getLicense();
      if (license == null) {
        license = "";
      }
      jsDocInfo.setLicense(license + fileLevelComment);
      setJSDocInfo(jsDocInfo);
    }
  }

  /**
   * Get the {@link JSDocInfo} attached to this node.
   * @return the information or {@code null} if no JSDoc is attached to this
   * node
   */
  public JSDocInfo getJSDocInfo() {
    return (JSDocInfo) getProp(JSDOC_INFO_PROP);
  }

  /**
   * Sets the {@link JSDocInfo} attached to this node.
   */
  public Node setJSDocInfo(JSDocInfo info) {
    putProp(JSDOC_INFO_PROP, info);
    return this;
  }

  /** This node was last changed at {@code time} */
  public void setChangeTime(int time) {
    putIntProp(CHANGE_TIME, time);
  }

  /** Returns the time of the last change for this node */
  public int getChangeTime() {
    return getIntProp(CHANGE_TIME);
  }

  /**
   * Sets whether this node is a variable length argument node. This
   * method is meaningful only on {@link Token#NAME} nodes
   * used to define a {@link Token#FUNCTION}'s argument list.
   */
  public void setVarArgs(boolean varArgs) {
    putBooleanProp(VAR_ARGS_NAME, varArgs);
  }

  /**
   * Returns whether this node is a variable length argument node. This
   * method's return value is meaningful only on {@link Token#NAME} nodes
   * used to define a {@link Token#FUNCTION}'s argument list.
   */
  public boolean isVarArgs() {
    return getBooleanProp(VAR_ARGS_NAME);
  }

  /**
   * Sets whether this node is an optional argument node. This
   * method is meaningful only on {@link Token#NAME} nodes
   * used to define a {@link Token#FUNCTION}'s argument list.
   */
  public void setOptionalArg(boolean optionalArg) {
    putBooleanProp(OPT_ARG_NAME, optionalArg);
  }

  /**
   * Returns whether this node is an optional argument node. This
   * method's return value is meaningful only on {@link Token#NAME} nodes
   * used to define a {@link Token#FUNCTION}'s argument list.
   */
  public boolean isOptionalArg() {
    return getBooleanProp(OPT_ARG_NAME);
  }

  /**
   * Sets whether this is a synthetic block that should not be considered
   * a real source block.
   */
  public void setIsSyntheticBlock(boolean val) {
    putBooleanProp(SYNTHETIC_BLOCK_PROP, val);
  }

  /**
   * Returns whether this is a synthetic block that should not be considered
   * a real source block.
   */
  public boolean isSyntheticBlock() {
    return getBooleanProp(SYNTHETIC_BLOCK_PROP);
  }

  /**
   * Sets the ES5 directives on this node.
   */
  public void setDirectives(Set<String> val) {
    putProp(DIRECTIVES, val);
  }

  /**
   * Returns the set of ES5 directives for this node.
   */
  @SuppressWarnings("unchecked")
  public Set<String> getDirectives() {
    return (Set<String>) getProp(DIRECTIVES);
  }

  /**
   * Adds a warning to be suppressed. This is indistinguishable
   * from having a {@code @suppress} tag in the code.
   */
  public void addSuppression(String warning) {
    if (getJSDocInfo() == null) {
      setJSDocInfo(new JSDocInfo(false));
    }
    getJSDocInfo().addSuppression(warning);
  }

  /**
   * Sets whether this is an added block that should not be considered
   * a real source block. Eg: In "if (true) x;", the "x;" is put under an added
   * block in the AST.
   */
  public void setIsAddedBlock(boolean val) {
    putBooleanProp(ADDED_BLOCK, val);
  }

  /**
   * Returns whether this is an added block that should not be considered
   * a real source block.
   */
  public boolean isAddedBlock() {
    return getBooleanProp(ADDED_BLOCK);
  }

  /**
   * Sets whether this node is a static member node. This
   * method is meaningful only on {@link Token#GETTER_DEF},
   * {@link Token#SETTER_DEF} or {@link Token#MEMBER_DEF} nodes contained
   * within {@link Token#CLASS}.
   */
  public void setStaticMember(boolean isStatic) {
    putBooleanProp(STATIC_MEMBER, isStatic);
  }

  /**
   * Returns whether this node is a static member node. This
   * method is meaningful only on {@link Token#GETTER_DEF},
   * {@link Token#SETTER_DEF} or {@link Token#MEMBER_DEF} nodes contained
   * within {@link Token#CLASS}.
   */
  public boolean isStaticMember() {
    return getBooleanProp(STATIC_MEMBER);
  }

  /**
   * Sets whether this node is a generator node. This
   * method is meaningful only on {@link Token#FUNCTION} or
   * {@link Token#MEMBER_DEF} nodes.
   */
  public void setIsGeneratorFunction(boolean isGenerator) {
    putBooleanProp(GENERATOR_FN, isGenerator);
  }

  /**
   * Returns whether this node is a generator function node.
   */
  public boolean isGeneratorFunction() {
    return getBooleanProp(GENERATOR_FN);
  }

  /**
   * Sets whether this node is a marker used in the translation of generators.
   */
  public void setGeneratorMarker(boolean isGeneratorMarker) {
    putBooleanProp(GENERATOR_MARKER, isGeneratorMarker);
  }

  /**
   * Returns whether this node is a marker used in the translation of generators.
   */
  public boolean isGeneratorMarker() {
    return getBooleanProp(GENERATOR_MARKER);
  }

  /**
   * @see #isGeneratorSafe()
   */
  public void setGeneratorSafe(boolean isGeneratorSafe) {
    putBooleanProp(GENERATOR_SAFE, isGeneratorSafe);
  }

  /**
   * Used when translating ES6 generators. If this returns true, this Node
   * was generated by the compiler, and it is safe to copy this node to the
   * transpiled output with no further changes.
   */
  public boolean isGeneratorSafe() {
    return getBooleanProp(GENERATOR_SAFE);
  }

  /**
   * Sets whether this node is a arrow function node. This
   * method is meaningful only on {@link Token#FUNCTION}
   */
  public void setIsArrowFunction(boolean isArrow) {
    putBooleanProp(ARROW_FN, isArrow);
  }

  /**
   * Returns whether this node is a arrow function node.
   */
  public boolean isArrowFunction() {
    return getBooleanProp(ARROW_FN);
  }

  /**
   * Sets whether this node is a generator node. This
   * method is meaningful only on {@link Token#FUNCTION} or
   * {@link Token#MEMBER_DEF} nodes.
   */
  public void setYieldFor(boolean isGenerator) {
    putBooleanProp(YIELD_FOR, isGenerator);
  }

  /**
   * Returns whether this node is a generator node. This
   * method is meaningful only on {@link Token#FUNCTION} or
   * {@link Token#MEMBER_DEF} nodes.
   */
  public boolean isYieldFor() {
    return getBooleanProp(YIELD_FOR);
  }

  // There are four values of interest:
  //   global state changes
  //   this state changes
  //   arguments state changes
  //   whether the call throws an exception
  //   locality of the result
  // We want a value of 0 to mean "global state changes and
  // unknown locality of result".

  public static final int FLAG_GLOBAL_STATE_UNMODIFIED = 1;
  public static final int FLAG_THIS_UNMODIFIED = 2;
  public static final int FLAG_ARGUMENTS_UNMODIFIED = 4;
  public static final int FLAG_NO_THROWS = 8;
  public static final int FLAG_LOCAL_RESULTS = 16;

  public static final int SIDE_EFFECTS_FLAGS_MASK = 31;

  public static final int SIDE_EFFECTS_ALL = 0;
  public static final int NO_SIDE_EFFECTS =
    FLAG_GLOBAL_STATE_UNMODIFIED
    | FLAG_THIS_UNMODIFIED
    | FLAG_ARGUMENTS_UNMODIFIED
    | FLAG_NO_THROWS;

  /**
   * Marks this function or constructor call's side effect flags.
   * This property is only meaningful for {@link Token#CALL} and
   * {@link Token#NEW} nodes.
   */
  public void setSideEffectFlags(int flags) {
    Preconditions.checkArgument(
        getType() == Token.CALL || getType() == Token.NEW,
        "setIsNoSideEffectsCall only supports CALL and NEW nodes, got %s",
        Token.name(getType()));

    putIntProp(SIDE_EFFECT_FLAGS, flags);
  }

  public void setSideEffectFlags(SideEffectFlags flags) {
    setSideEffectFlags(flags.valueOf());
  }

  /**
   * Returns the side effects flags for this node.
   */
  public int getSideEffectFlags() {
    return getIntProp(SIDE_EFFECT_FLAGS);
  }

  /**
   * A helper class for getting and setting the side-effect flags.
   * @author johnlenz@google.com (John Lenz)
   */
  public static class SideEffectFlags {
    private int value = Node.SIDE_EFFECTS_ALL;

    public SideEffectFlags() {
    }

    public SideEffectFlags(int value) {
      this.value = value;
    }

    public int valueOf() {
      return value;
    }

    /** All side-effect occur and the returned results are non-local. */
    public SideEffectFlags setAllFlags() {
      value = Node.SIDE_EFFECTS_ALL;
      return this;
    }

    /** No side-effects occur and the returned results are local. */
    public SideEffectFlags clearAllFlags() {
      value = Node.NO_SIDE_EFFECTS | Node.FLAG_LOCAL_RESULTS;
      return this;
    }

    public boolean areAllFlagsSet() {
      return value == Node.SIDE_EFFECTS_ALL;
    }

    /**
     * Preserve the return result flag, but clear the others:
     *   no global state change, no throws, no this change, no arguments change
     */
    public void clearSideEffectFlags() {
      value |= Node.NO_SIDE_EFFECTS;
    }

    public SideEffectFlags setMutatesGlobalState() {
      // Modify global means everything must be assumed to be modified.
      removeFlag(Node.FLAG_GLOBAL_STATE_UNMODIFIED);
      removeFlag(Node.FLAG_ARGUMENTS_UNMODIFIED);
      removeFlag(Node.FLAG_THIS_UNMODIFIED);
      return this;
    }

    public SideEffectFlags setThrows() {
      removeFlag(Node.FLAG_NO_THROWS);
      return this;
    }

    public SideEffectFlags setMutatesThis() {
      removeFlag(Node.FLAG_THIS_UNMODIFIED);
      return this;
    }

    public SideEffectFlags setMutatesArguments() {
      removeFlag(Node.FLAG_ARGUMENTS_UNMODIFIED);
      return this;
    }

    public SideEffectFlags setReturnsTainted() {
      removeFlag(Node.FLAG_LOCAL_RESULTS);
      return this;
    }

    private void removeFlag(int flag) {
      value &= ~flag;
    }
  }

  /**
   * @return Whether the only side-effect is "modifies this"
   */
  public boolean isOnlyModifiesThisCall() {
    return areBitFlagsSet(
        getSideEffectFlags() & Node.NO_SIDE_EFFECTS,
        Node.FLAG_GLOBAL_STATE_UNMODIFIED
            | Node.FLAG_ARGUMENTS_UNMODIFIED
            | Node.FLAG_NO_THROWS);
  }

  /**
   * @return Whether the only side-effect is "modifies arguments"
   */
  public boolean isOnlyModifiesArgumentsCall() {
    return areBitFlagsSet(
        getSideEffectFlags() & Node.NO_SIDE_EFFECTS,
        Node.FLAG_GLOBAL_STATE_UNMODIFIED
            | Node.FLAG_THIS_UNMODIFIED
            | Node.FLAG_NO_THROWS);
  }

  /**
   * Returns true if this node is a function or constructor call that
   * has no side effects.
   */
  public boolean isNoSideEffectsCall() {
    return areBitFlagsSet(getSideEffectFlags(), NO_SIDE_EFFECTS);
  }

  /**
   * Returns true if this node is a function or constructor call that
   * returns a primitive or a local object (an object that has no other
   * references).
   */
  public boolean isLocalResultCall() {
    return areBitFlagsSet(getSideEffectFlags(), FLAG_LOCAL_RESULTS);
  }

  /** Returns true if this is a new/call that may mutate its arguments. */
  public boolean mayMutateArguments() {
    return !areBitFlagsSet(getSideEffectFlags(), FLAG_ARGUMENTS_UNMODIFIED);
  }

  /** Returns true if this is a new/call that may mutate global state or throw. */
  public boolean mayMutateGlobalStateOrThrow() {
    return !areBitFlagsSet(getSideEffectFlags(),
        FLAG_GLOBAL_STATE_UNMODIFIED | FLAG_NO_THROWS);
  }

  /**
   * returns true if all the flags are set in value.
   */
  private boolean areBitFlagsSet(int value, int flags) {
    return (value & flags) == flags;
  }

  /**
   * This should only be called for STRING nodes children of OBJECTLIT.
   */
  public boolean isQuotedString() {
    return false;
  }

  /**
   * This should only be called for STRING nodes children of OBJECTLIT.
   */
  public void setQuotedString() {
    throw new IllegalStateException("not a StringNode");
  }

  static class NodeMismatch {
    final Node nodeA;
    final Node nodeB;

    NodeMismatch(Node nodeA, Node nodeB) {
      this.nodeA = nodeA;
      this.nodeB = nodeB;
    }

    @Override
    public boolean equals(Object object) {
      if (object instanceof NodeMismatch) {
        NodeMismatch that = (NodeMismatch) object;
        return that.nodeA.equals(this.nodeA) && that.nodeB.equals(this.nodeB);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(nodeA, nodeB);
    }
  }


  /*** AST type check methods ***/

  public boolean isAdd() {
    return this.getType() == Token.ADD;
  }

  public boolean isAnd() {
    return this.getType() == Token.AND;
  }

  public boolean isArrayLit() {
    return this.getType() == Token.ARRAYLIT;
  }

  public boolean isArrayPattern() {
    return this.getType() == Token.ARRAY_PATTERN;
  }

  public boolean isAssign() {
    return this.getType() == Token.ASSIGN;
  }

  public boolean isAssignAdd() {
    return this.getType() == Token.ASSIGN_ADD;
  }

  public boolean isBlock() {
    return this.getType() == Token.BLOCK;
  }

  public boolean isBreak() {
    return this.getType() == Token.BREAK;
  }

  public boolean isCall() {
    return this.getType() == Token.CALL;
  }

  public boolean isCase() {
    return this.getType() == Token.CASE;
  }

  public boolean isCast() {
    return this.getType() == Token.CAST;
  }

  public boolean isCatch() {
    return this.getType() == Token.CATCH;
  }

  public boolean isClass() {
    return this.getType() == Token.CLASS;
  }

  public boolean isClassMembers() {
    return this.getType() == Token.CLASS_MEMBERS;
  }

  public boolean isComma() {
    return this.getType() == Token.COMMA;
  }

  public boolean isComputedProp() {
    return this.getType() == Token.COMPUTED_PROP;
  }

  public boolean isContinue() {
    return this.getType() == Token.CONTINUE;
  }

  public boolean isConst() {
    return this.getType() == Token.CONST;
  }

  public boolean isDebugger() {
    return this.getType() == Token.DEBUGGER;
  }

  public boolean isDec() {
    return this.getType() == Token.DEC;
  }

  public boolean isDefaultCase() {
    return this.getType() == Token.DEFAULT_CASE;
  }

  public boolean isDefaultValue() {
    return this.getType() == Token.DEFAULT_VALUE;
  }

  public boolean isDelProp() {
    return this.getType() == Token.DELPROP;
  }

  public boolean isDestructuringPattern() {
    return isObjectPattern() || isArrayPattern();
  }

  public boolean isDo() {
    return this.getType() == Token.DO;
  }

  public boolean isEmpty() {
    return this.getType() == Token.EMPTY;
  }

  public boolean isExport() {
    return this.getType() == Token.EXPORT;
  }

  public boolean isExprResult() {
    return this.getType() == Token.EXPR_RESULT;
  }

  public boolean isFalse() {
    return this.getType() == Token.FALSE;
  }

  public boolean isFor() {
    return this.getType() == Token.FOR;
  }

  public boolean isForOf() {
    return this.getType() == Token.FOR_OF;
  }

  public boolean isFunction() {
    return this.getType() == Token.FUNCTION;
  }

  public boolean isGetterDef() {
    return this.getType() == Token.GETTER_DEF;
  }

  public boolean isGetElem() {
    return this.getType() == Token.GETELEM;
  }

  public boolean isGetProp() {
    return this.getType() == Token.GETPROP;
  }

  public boolean isHook() {
    return this.getType() == Token.HOOK;
  }

  public boolean isIf() {
    return this.getType() == Token.IF;
  }

  public boolean isImport() {
    return this.getType() == Token.IMPORT;
  }

  public boolean isIn() {
    return this.getType() == Token.IN;
  }

  public boolean isInc() {
    return this.getType() == Token.INC;
  }

  public boolean isInstanceOf() {
    return this.getType() == Token.INSTANCEOF;
  }

  public boolean isLabel() {
    return this.getType() == Token.LABEL;
  }

  public boolean isLabelName() {
    return this.getType() == Token.LABEL_NAME;
  }

  public boolean isLet() {
    return this.getType() == Token.LET;
  }

  public boolean isMemberDef() {
    return this.getType() == Token.MEMBER_DEF;
  }

  public boolean isName() {
    return this.getType() == Token.NAME;
  }

  public boolean isNE() {
    return this.getType() == Token.NE;
  }

  public boolean isNew() {
    return this.getType() == Token.NEW;
  }

  public boolean isNot() {
    return this.getType() == Token.NOT;
  }

  public boolean isNull() {
    return this.getType() == Token.NULL;
  }

  public boolean isNumber() {
    return this.getType() == Token.NUMBER;
  }

  public boolean isObjectLit() {
    return this.getType() == Token.OBJECTLIT;
  }

  public boolean isObjectPattern() {
    return this.getType() == Token.OBJECT_PATTERN;
  }

  public boolean isOr() {
    return this.getType() == Token.OR;
  }

  public boolean isParamList() {
    return this.getType() == Token.PARAM_LIST;
  }

  public boolean isRegExp() {
    return this.getType() == Token.REGEXP;
  }

  public boolean isRest() {
    return this.getType() == Token.REST;
  }

  public boolean isReturn() {
    return this.getType() == Token.RETURN;
  }

  public boolean isScript() {
    return this.getType() == Token.SCRIPT;
  }

  public boolean isSetterDef() {
    return this.getType() == Token.SETTER_DEF;
  }

  public boolean isSpread() {
    return this.getType() == Token.SPREAD;
  }

  public boolean isString() {
    return this.getType() == Token.STRING;
  }

  public boolean isStringKey() {
    return this.getType() == Token.STRING_KEY;
  }

  public boolean isSuper() {
    return this.getType() == Token.SUPER;
  }

  public boolean isSwitch() {
    return this.getType() == Token.SWITCH;
  }

  public boolean isThis() {
    return this.getType() == Token.THIS;
  }

  public boolean isThrow() {
    return this.getType() == Token.THROW;
  }

  public boolean isTrue() {
    return this.getType() == Token.TRUE;
  }

  public boolean isTry() {
    return this.getType() == Token.TRY;
  }

  public boolean isTypeOf() {
    return this.getType() == Token.TYPEOF;
  }

  public boolean isVar() {
    return this.getType() == Token.VAR;
  }

  public boolean isVoid() {
    return this.getType() == Token.VOID;
  }

  public boolean isWhile() {
    return this.getType() == Token.WHILE;
  }

  public boolean isWith() {
    return this.getType() == Token.WITH;
  }

  public boolean isYield() {
    return this.getType() == Token.YIELD;
  }
}
