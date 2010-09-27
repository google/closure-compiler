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
      // Rhino's AST captures data flow. These are the annotations
      // it used. We've mostly torn them out.
      LOCAL_BLOCK_PROP  = -3,
      OBJECT_IDS_PROP   = -2,
      CATCH_SCOPE_PROP  = -1,
      LABEL_ID_PROP     =  0,

      TARGET_PROP       =  1,
      BREAK_PROP        =  2,
      CONTINUE_PROP     =  3,
      ENUM_PROP         =  4,
      FUNCTION_PROP     =  5,
      TEMP_PROP         =  6,
      LOCAL_PROP        =  7,
      CODEOFFSET_PROP   =  8,
      FIXUPS_PROP       =  9,
      VARS_PROP         = 10,
      USES_PROP         = 11,
      REGEXP_PROP       = 12,
      CASES_PROP        = 13,
      DEFAULT_PROP      = 14,
      CASEARRAY_PROP    = 15,
      SOURCENAME_PROP   = 16,
      TYPE_PROP         = 17,
      SPECIAL_PROP_PROP = 18,
      LABEL_PROP        = 19,
      FINALLY_PROP      = 20,
      LOCALCOUNT_PROP   = 21,
  /*
      the following properties are defined and manipulated by the
      optimizer -
      TARGETBLOCK_PROP - the block referenced by a branch node
      VARIABLE_PROP - the variable referenced by a BIND or NAME node
      LASTUSE_PROP - that variable node is the last reference before
                      a new def or the end of the block
      ISNUMBER_PROP - this node generates code on Number children and
                      delivers a Number result (as opposed to Objects)
      DIRECTCALL_PROP - this call node should emit code to test the function
                        object against the known class and call diret if it
                        matches.
  */

      TARGETBLOCK_PROP  = 22,
      VARIABLE_PROP     = 23,
      LASTUSE_PROP      = 24,
      ISNUMBER_PROP     = 25,
      DIRECTCALL_PROP   = 26,

      SPECIALCALL_PROP  = 27,
      DEBUGSOURCE_PROP  = 28,
      JSDOC_INFO_PROP   = 29,     // contains a TokenStream.JSDocInfo object
      VAR_ARGS_NAME     = 29,     // the name node is a variable length
                                  // argument placeholder. It can never be
                                  // used in conjunction with JSDOC_INFO_PROP.
      SKIP_INDEXES_PROP  = 30,    // array of skipped indexes of array literal
      INCRDECR_PROP      = 31,    // pre or post type of increment/decrement
      MEMBER_TYPE_PROP   = 32,    // type of element access operation
      NAME_PROP          = 33,    // property name
      PARENTHESIZED_PROP = 34,    // expression is parenthesized
      QUOTED_PROP        = 35,    // set to indicate a quoted object lit key
      OPT_ARG_NAME       = 36,    // The name node is an optional argument.
      SYNTHETIC_BLOCK_PROP = 37,  // A synthetic block. Used to make
                                  // processing simpler, and does not
                                  // represent a real block in the source.
      EMPTY_BLOCK        = 38,    // Used to indicate BLOCK that replaced
                                  // EMPTY nodes.
      ORIGINALNAME_PROP  = 39,    // The original name of the node, before
                                  // renaming.
      BRACELESS_TYPE     = 40,    // The type syntax without curly braces.
      SIDE_EFFECT_FLAGS  = 41,    // Function or constructor call side effect
                                  // flags
      // Coding convention props
      IS_CONSTANT_NAME   = 42,    // The variable or property is constant.
      IS_OPTIONAL_PARAM  = 43,    // The parameter is optional.
      IS_VAR_ARGS_PARAM  = 44,    // The parameter is a var_args.
      IS_NAMESPACE       = 45,    // The variable creates a namespace.
      IS_DISPATCHER      = 46,    // The function is a dispatcher function,
                                  // probably generated from Java code, and
                                  // should be resolved to the proper
                                  // overload if possible.
      DIRECTIVES         = 47,    // The ES5 directives on this node.
      DIRECT_EVAL        = 48,    // ES5 distinguishes between direct and
                                  // indirect calls to eval.
      FREE_CALL          = 49,    // A CALL without an explicit "this" value.
                                  //
      LAST_PROP          = 49;

  // values of ISNUMBER_PROP to specify
  // which of the children are Number types
  public static final int
      BOTH = 0,
      LEFT = 1,
      RIGHT = 2;

  public static final int    // values for SPECIALCALL_PROP
      NON_SPECIALCALL  = 0,
      SPECIALCALL_EVAL = 1,
      SPECIALCALL_WITH = 2;

  public static final int   // flags for INCRDECR_PROP
      DECR_FLAG = 0x1,
      POST_FLAG = 0x2;

  public static final int   // flags for MEMBER_TYPE_PROP
      PROPERTY_FLAG    = 0x1, // property access: element is valid name
      ATTRIBUTE_FLAG   = 0x2, // x.@y or x..@y
      DESCENDANTS_FLAG = 0x4; // x..y or x..@i

  private static final String propToString(int propType) {
      switch (propType) {
        case LOCAL_BLOCK_PROP:   return "local_block";
        case OBJECT_IDS_PROP:    return "object_ids_prop";
        case CATCH_SCOPE_PROP:   return "catch_scope_prop";
        case LABEL_ID_PROP:      return "label_id_prop";
        case TARGET_PROP:        return "target";
        case BREAK_PROP:         return "break";
        case CONTINUE_PROP:      return "continue";
        case ENUM_PROP:          return "enum";
        case FUNCTION_PROP:      return "function";
        case TEMP_PROP:          return "temp";
        case LOCAL_PROP:         return "local";
        case CODEOFFSET_PROP:    return "codeoffset";
        case FIXUPS_PROP:        return "fixups";
        case VARS_PROP:          return "vars";
        case USES_PROP:          return "uses";
        case REGEXP_PROP:        return "regexp";
        case CASES_PROP:         return "cases";
        case DEFAULT_PROP:       return "default";
        case CASEARRAY_PROP:     return "casearray";
        case SOURCENAME_PROP:    return "sourcename";
        case TYPE_PROP:          return "type";
        case SPECIAL_PROP_PROP:  return "special_prop";
        case LABEL_PROP:         return "label";
        case FINALLY_PROP:       return "finally";
        case LOCALCOUNT_PROP:    return "localcount";

        case TARGETBLOCK_PROP:   return "targetblock";
        case VARIABLE_PROP:      return "variable";
        case LASTUSE_PROP:       return "lastuse";
        case ISNUMBER_PROP:      return "isnumber";
        case DIRECTCALL_PROP:    return "directcall";

        case SPECIALCALL_PROP:   return "specialcall";
        case DEBUGSOURCE_PROP:   return "debugsource";

        case JSDOC_INFO_PROP:    return "jsdoc_info";

        case SKIP_INDEXES_PROP:  return "skip_indexes";
        case INCRDECR_PROP:      return "incrdecr";
        case MEMBER_TYPE_PROP:   return "member_type";
        case NAME_PROP:          return "name";
        case PARENTHESIZED_PROP: return "parenthesized";
        case QUOTED_PROP:        return "quoted";

        case SYNTHETIC_BLOCK_PROP: return "synthetic";
        case EMPTY_BLOCK: return "empty_block";
        case ORIGINALNAME_PROP: return "originalname";
        case SIDE_EFFECT_FLAGS: return "side_effect_flags";

        case IS_CONSTANT_NAME:   return "is_constant_name";
        case IS_OPTIONAL_PARAM:  return "is_optional_param";
        case IS_VAR_ARGS_PARAM:  return "is_var_args_param";
        case IS_NAMESPACE:       return "is_namespace";
        case IS_DISPATCHER:      return "is_dispatcher";
        case DIRECTIVES:         return "directives";
        case DIRECT_EVAL:        return "direct_eval";
        case FREE_CALL:          return "free_call";
        default:
          Kit.codeBug();
      }
      return null;
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
    public boolean isEquivalentTo(Node node) {
      return (node instanceof NumberNode
          && getDouble() == ((NumberNode) node).getDouble());
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
    public boolean isEquivalentTo(Node node) {
      return (node instanceof StringNode &&
         this.str.equals(((StringNode) node).str));
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

  // PropListItems are immutable so that they can be shared.
  private static class PropListItem implements Serializable {
    private static final long serialVersionUID = 1L;

    final PropListItem next;
    final int type;
    final int intValue;
    final Object objectValue;

    PropListItem(int type, int intValue, PropListItem next) {
      this(type, intValue, null, next);
    }

    PropListItem(int type, Object objectValue, PropListItem next) {
      this(type, 0, objectValue, next);
    }

    PropListItem(
        int type, int intValue, Object objectValue, PropListItem next) {
      this.type = type;
      this.intValue = intValue;
      this.objectValue = objectValue;
      this.next = next;
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
    for (Node child = children; child != null; child = child.next) {
      Preconditions.checkArgument(child.parent == null);
      child.parent = this;
    }
    if (last != null) {
      last.next = children;
    }
    last = children.getLastSibling();
    if (first == null) {
      first = children;
    }
  }

  /**
   * Add 'child' before 'node'.
   */
  public void addChildBefore(Node newChild, Node node) {
    Preconditions.checkArgument(node != null,
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
    Preconditions.checkArgument(newChild.parent == null,
        "The new child node already has a parent.");
    newChild.parent = this;
    newChild.next = node.next;
    node.next = newChild;
    if (last == node) {
        last = newChild;
    }
  }

  /**
   * Detach a child from its parent and siblings.
   */
  public void removeChild(Node child) {
    Node prev = getChildBefore(child);
    if (prev == null)
        first = first.next;
    else
        prev.next = child.next;
    if (child == last) last = prev;
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
    if (child == last)
        last = newChild;
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
    if (child == last)
        last = newChild;
    child.next = null;
    child.parent = null;
  }

  @VisibleForTesting
  PropListItem lookupProperty(int propType) {
    PropListItem x = propListHead;
    while (x != null && propType != x.type) {
      x = x.next;
    }
    return x;
  }

  /**
   * Clone the properties from the provided node without copying
   * the property object.  The recieving node may not have any
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
    } else if (item.type == propType) {
      return item.next;
    } else {
      PropListItem result = removeProp(item.next, propType);
      if (result != item.next) {
        return new PropListItem(
            item.type, item.intValue, item.objectValue, result);
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
    return item.objectValue;
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
    return item.intValue;
  }

  public int getExistingIntProp(int propType) {
    PropListItem item = lookupProperty(propType);
    if (item == null) {
      Kit.codeBug();
    }
    return item.intValue;
  }

  public void putProp(int propType, Object value) {
    removeProp(propType);
    if (value != null) {
      propListHead = new PropListItem(propType, value, propListHead);
    }
  }

  public void putBooleanProp(int propType, boolean value) {
    putIntProp(propType, value ? 1 : 0);
  }

  public void putIntProp(int propType, int value) {
    removeProp(propType);
    if (value != 0) {
      propListHead = new PropListItem(propType, value, propListHead);
    }
  }

  // Gets all the property types, in sorted order.
  private int[] getSortedPropTypes() {
    int count = 0;
    for (PropListItem x = propListHead; x != null; x = x.next) {
      count++;
    }

    int[] keys = new int[count];
    for (PropListItem x = propListHead; x != null; x = x.next) {
      count--;
      keys[count] = x.type;
    }

    Arrays.sort(keys);
    return keys;
  }

  public int getLineno() {
    return extractLineno(sourcePosition);
  }

  public int getCharno() {
    return extractCharno(sourcePosition);
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

  /** Can only be called when <tt>getType() == TokenStream.NUMBER</tt> */
  public void setDouble(double s) throws UnsupportedOperationException {
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

  /** Can only be called when node has String context. */
  public void setString(String s) throws UnsupportedOperationException {
    if (this.getType() == Token.STRING) {
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
    if (Token.printTrees) {
        StringBuilder sb = new StringBuilder();
        toString(sb, printSource, printAnnotations, printType);
        return sb.toString();
    }
    return String.valueOf(type);
  }

  private void toString(
      StringBuilder sb,
      boolean printSource,
      boolean printAnnotations,
      boolean printType) {
    if (Token.printTrees) {
      sb.append(Token.name(type));
      if (this instanceof StringNode) {
        sb.append(' ');
        sb.append(getString());
      } else if (type == Token.FUNCTION) {
        sb.append(' ');
        // In the case of JsDoc trees, the first child is often not a string
        // which causes exceptions to be thrown when calling toString or
        // toStringTree.
        if (first.getType() == Token.STRING) {
          sb.append(first.getString());
        }
      } else if (this instanceof ScriptOrFnNode) {
        ScriptOrFnNode sof = (ScriptOrFnNode) this;
        if (this instanceof FunctionNode) {
          FunctionNode fn = (FunctionNode) this;
          sb.append(' ');
          sb.append(fn.getFunctionName());
        }
        if (printSource) {
          sb.append(" [source name: ");
          sb.append(sof.getSourceName());
          sb.append("] [encoded source length: ");
          sb.append(sof.getEncodedSourceEnd() - sof.getEncodedSourceStart());
          sb.append("] [base line: ");
          sb.append(sof.getBaseLineno());
          sb.append("] [end line: ");
          sb.append(sof.getEndLineno());
          sb.append(']');
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
            case TARGETBLOCK_PROP: // can't add this as it recurses
              value = "target block property";
              break;
            case LOCAL_BLOCK_PROP: // can't add this as it is dull
              value = "last local block";
              break;
            case ISNUMBER_PROP:
              switch (x.intValue) {
                case BOTH:
                  value = "both";
                  break;
                case RIGHT:
                  value = "right";
                  break;
                case LEFT:
                  value = "left";
                  break;
                default:
                  throw Kit.codeBug();
              }
              break;
            case SPECIALCALL_PROP:
              switch (x.intValue) {
                case SPECIALCALL_EVAL:
                  value = "eval";
                  break;
                case SPECIALCALL_WITH:
                  value = "with";
                  break;
                default:
                  // NON_SPECIALCALL should not be stored
                  throw Kit.codeBug();
              }
              break;
            default:
              Object obj = x.objectValue;
              if (obj != null) {
                value = obj.toString();
              } else {
                value = String.valueOf(x.intValue);
              }
              break;
          }
          sb.append(value);
          sb.append(']');
        }
      }

      if (printType) {
        if (jsType != null) {
          String jsTypeString = jsType.toString();
          if (jsTypeString != null) {
            sb.append(" : ");
            sb.append(jsTypeString);
          }
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
    if (Token.printTrees) {
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

  private JSType jsType;

  private Node parent;

  //==========================================================================
  // Source position management

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
   * <p>Return an iterable object that iterates over this nodes's children.
   * The iterator does not support the optional operation
   * {@link Iterator#remove()}.</p>
   *
   * <p>To iterate over a node's siblings, one can write</p>
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
   * <p>Return an iterable object that iterates over this nodes's siblings.
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

    public boolean hasNext() {
      return current != null;
    }

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

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  // ==========================================================================
  // Accessors

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

    public Iterator<Node> iterator() {
      return new Iterator<Node>() {
        public boolean hasNext() {
          return cur != null;
        }

        public Node next() {
          if (!hasNext()) throw new NoSuchElementException();
          Node n = cur;
          cur = cur.getParent();
          return n;
        }

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
    for (Node n = first; n != null; n = n.next)
      c++;

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
   */
  public String checkTreeEquals(Node node2) {
      NodeMismatch diff = checkTreeEqualsImpl(node2);
      if (diff != null) {
        return "Node tree inequality:" +
            "\nTree1:\n" + toStringTree() +
            "\n\nTree2:\n" + node2.toStringTree() +
            "\n\nSubtree1: " + diff.nodeA.toStringTree() +
            "\n\nSubtree2: " + diff.nodeB.toStringTree();
      }
      return null;
  }

  /**
   * If this is a compilation pass and not a test, do not construct error
   * strings. Instead return true if the trees are equal.
   */
  public boolean checkTreeEqualsSilent(Node node2) {
    return checkTreeEqualsImpl(node2) == null;
  }

  /**
   * Helper function to ignore differences in Node subclasses that are no longer
   * used.
   */
  @SuppressWarnings("unchecked")
  static private Class getNodeClass(Node n) {
    Class c = n.getClass();
    if (c == FunctionNode.class || c == ScriptOrFnNode.class) {
      return Node.class;
    }
    return c;
  }

  /**
   * Compare this node to node2 recursively and return the first pair of nodes
   * that differs doing a preorder depth-first traversal. Package private for
   * testing. Returns null if the nodes are equivalent.
   */
  NodeMismatch checkTreeEqualsImpl(Node node2) {
    boolean eq = false;

    if (type == node2.getType() && getChildCount() == node2.getChildCount()
        && getNodeClass(this) == getNodeClass(node2)) {
      eq = this.isEquivalentTo(node2);
    }

    if (!eq) {
      return new NodeMismatch(this, node2);
    }

    NodeMismatch res = null;
    Node n, n2;
    for (n = first, n2 = node2.first;
         res == null && n != null;
         n = n.next, n2 = n2.next) {
      res = n.checkTreeEqualsImpl(n2);
      if (res != null) {
        return res;
      }
    }
    return res;
  }

  /**
   * Checks if the subtree under this node is the same as another subtree
   * including types. Returns null if it's equal, or a message describing the
   * differences.
   */
  public boolean checkTreeTypeAwareEqualsSilent(Node node2) {
    return checkTreeTypeAwareEqualsImpl(node2) == null;
  }

  /**
   * Compare this node to node2 recursively and return the first pair of nodes
   * that differs doing a preorder depth-first traversal. Package private for
   * testing. Returns null if the nodes are equivalent.
   */
  NodeMismatch checkTreeTypeAwareEqualsImpl(Node node2) {
    boolean eq = false;

    if (type == node2.getType()
        && getChildCount() == node2.getChildCount()
        && getClass() == node2.getClass()
        && JSType.isEquivalent(jsType, node2.getJSType())) {

      eq = this.isEquivalentTo(node2);
    }

    if (!eq) {
      return new NodeMismatch(this, node2);
    }

    NodeMismatch res = null;
    Node n, n2;
    for (n = first, n2 = node2.first;
         res == null && n != null;
         n = n.next, n2 = n2.next) {
      res = n.checkTreeTypeAwareEqualsImpl(n2);
      if (res != null) {
        return res;
      }
    }
    return res;
  }

  public static String tokenToName(int token) {
    switch (token) {
      case Token.ERROR:           return "error";
      case Token.EOF:             return "eof";
      case Token.EOL:             return "eol";
      case Token.ENTERWITH:       return "enterwith";
      case Token.LEAVEWITH:       return "leavewith";
      case Token.RETURN:          return "return";
      case Token.GOTO:            return "goto";
      case Token.IFEQ:            return "ifeq";
      case Token.IFNE:            return "ifne";
      case Token.SETNAME:         return "setname";
      case Token.BITOR:           return "bitor";
      case Token.BITXOR:          return "bitxor";
      case Token.BITAND:          return "bitand";
      case Token.EQ:              return "eq";
      case Token.NE:              return "ne";
      case Token.LT:              return "lt";
      case Token.LE:              return "le";
      case Token.GT:              return "gt";
      case Token.GE:              return "ge";
      case Token.LSH:             return "lsh";
      case Token.RSH:             return "rsh";
      case Token.URSH:            return "ursh";
      case Token.ADD:             return "add";
      case Token.SUB:             return "sub";
      case Token.MUL:             return "mul";
      case Token.DIV:             return "div";
      case Token.MOD:             return "mod";
      case Token.BITNOT:          return "bitnot";
      case Token.NEG:             return "neg";
      case Token.NEW:             return "new";
      case Token.DELPROP:         return "delprop";
      case Token.TYPEOF:          return "typeof";
      case Token.GETPROP:         return "getprop";
      case Token.SETPROP:         return "setprop";
      case Token.GETELEM:         return "getelem";
      case Token.SETELEM:         return "setelem";
      case Token.CALL:            return "call";
      case Token.NAME:            return "name";
      case Token.NUMBER:          return "number";
      case Token.STRING:          return "string";
      case Token.NULL:            return "null";
      case Token.THIS:            return "this";
      case Token.FALSE:           return "false";
      case Token.TRUE:            return "true";
      case Token.SHEQ:            return "sheq";
      case Token.SHNE:            return "shne";
      case Token.REGEXP:          return "regexp";
      case Token.POS:             return "pos";
      case Token.BINDNAME:        return "bindname";
      case Token.THROW:           return "throw";
      case Token.IN:              return "in";
      case Token.INSTANCEOF:      return "instanceof";
      case Token.GETVAR:          return "getvar";
      case Token.SETVAR:          return "setvar";
      case Token.TRY:             return "try";
      case Token.TYPEOFNAME:      return "typeofname";
      case Token.THISFN:          return "thisfn";
      case Token.SEMI:            return "semi";
      case Token.LB:              return "lb";
      case Token.RB:              return "rb";
      case Token.LC:              return "lc";
      case Token.RC:              return "rc";
      case Token.LP:              return "lp";
      case Token.RP:              return "rp";
      case Token.COMMA:           return "comma";
      case Token.ASSIGN:          return "assign";
      case Token.ASSIGN_BITOR:    return "assign_bitor";
      case Token.ASSIGN_BITXOR:   return "assign_bitxor";
      case Token.ASSIGN_BITAND:   return "assign_bitand";
      case Token.ASSIGN_LSH:      return "assign_lsh";
      case Token.ASSIGN_RSH:      return "assign_rsh";
      case Token.ASSIGN_URSH:     return "assign_ursh";
      case Token.ASSIGN_ADD:      return "assign_add";
      case Token.ASSIGN_SUB:      return "assign_sub";
      case Token.ASSIGN_MUL:      return "assign_mul";
      case Token.ASSIGN_DIV:      return "assign_div";
      case Token.ASSIGN_MOD:      return "assign_mod";
      case Token.HOOK:            return "hook";
      case Token.COLON:           return "colon";
      case Token.OR:              return "or";
      case Token.AND:             return "and";
      case Token.INC:             return "inc";
      case Token.DEC:             return "dec";
      case Token.DOT:             return "dot";
      case Token.FUNCTION:        return "function";
      case Token.EXPORT:          return "export";
      case Token.IMPORT:          return "import";
      case Token.IF:              return "if";
      case Token.ELSE:            return "else";
      case Token.SWITCH:          return "switch";
      case Token.CASE:            return "case";
      case Token.DEFAULT:         return "default";
      case Token.WHILE:           return "while";
      case Token.DO:              return "do";
      case Token.FOR:             return "for";
      case Token.BREAK:           return "break";
      case Token.CONTINUE:        return "continue";
      case Token.VAR:             return "var";
      case Token.WITH:            return "with";
      case Token.CATCH:           return "catch";
      case Token.FINALLY:         return "finally";
      case Token.RESERVED:        return "reserved";
      case Token.NOT:             return "not";
      case Token.VOID:            return "void";
      case Token.BLOCK:           return "block";
      case Token.ARRAYLIT:        return "arraylit";
      case Token.OBJECTLIT:       return "objectlit";
      case Token.LABEL:           return "label";
      case Token.TARGET:          return "target";
      case Token.LOOP:            return "loop";
      case Token.EXPR_VOID:       return "expr_void";
      case Token.EXPR_RESULT:     return "expr_result";
      case Token.JSR:             return "jsr";
      case Token.SCRIPT:          return "script";
      case Token.EMPTY:           return "empty";
      case Token.GET_REF:         return "get_ref";
      case Token.REF_SPECIAL:     return "ref_special";
    }
    return "<unknown="+token+">";
  }

  /** Returns true if this node is equivalent semantically to another */
  public boolean isEquivalentTo(Node node) {
    if (type == Token.ARRAYLIT) {
      try {
        int[] indices1 = (int[]) getProp(Node.SKIP_INDEXES_PROP);
        int[] indices2 = (int[]) node.getProp(Node.SKIP_INDEXES_PROP);
        if (indices1 == null) {
          if (indices2 != null) {
            return false;
          }
        } else if (indices2 == null) {
          return false;
        } else if (indices1.length != indices2.length) {
          return false;
        } else {
          for (int i = 0; i < indices1.length; i++) {
            if (indices1[i] != indices2[i]) {
              return false;
            }
          }
        }
      } catch (Exception e) {
        return false;
      }
    } else if (type == Token.INC || type == Token.DEC) {
      int post1 = this.getIntProp(INCRDECR_PROP);
      int post2 = node.getIntProp(INCRDECR_PROP);
      if (post1 != post2) {
        return false;
      }
    } else if (type == Token.STRING) {
      int quoted1 = this.getIntProp(QUOTED_PROP);
      int quoted2 = node.getIntProp(QUOTED_PROP);
      if (quoted1 != quoted2) {
        return false;
      }
    }
    return true;
  }

  public boolean hasSideEffects() {
    switch (type) {
      case Token.EXPR_VOID:
      case Token.COMMA:
        if (last != null)
          return last.hasSideEffects();
        else
          return true;

      case Token.HOOK:
        if (first == null || first.next == null || first.next.next == null) {
          Kit.codeBug();
        }
        return first.next.hasSideEffects() && first.next.next.hasSideEffects();

      case Token.ERROR: // Avoid cascaded error messages
      case Token.EXPR_RESULT:
      case Token.ASSIGN:
      case Token.ASSIGN_ADD:
      case Token.ASSIGN_SUB:
      case Token.ASSIGN_MUL:
      case Token.ASSIGN_DIV:
      case Token.ASSIGN_MOD:
      case Token.ASSIGN_BITOR:
      case Token.ASSIGN_BITXOR:
      case Token.ASSIGN_BITAND:
      case Token.ASSIGN_LSH:
      case Token.ASSIGN_RSH:
      case Token.ASSIGN_URSH:
      case Token.ENTERWITH:
      case Token.LEAVEWITH:
      case Token.RETURN:
      case Token.GOTO:
      case Token.IFEQ:
      case Token.IFNE:
      case Token.NEW:
      case Token.DELPROP:
      case Token.SETNAME:
      case Token.SETPROP:
      case Token.SETELEM:
      case Token.CALL:
      case Token.THROW:
      case Token.RETHROW:
      case Token.SETVAR:
      case Token.CATCH_SCOPE:
      case Token.RETURN_RESULT:
      case Token.SET_REF:
      case Token.DEL_REF:
      case Token.REF_CALL:
      case Token.TRY:
      case Token.SEMI:
      case Token.INC:
      case Token.DEC:
      case Token.EXPORT:
      case Token.IMPORT:
      case Token.IF:
      case Token.ELSE:
      case Token.SWITCH:
      case Token.WHILE:
      case Token.DO:
      case Token.FOR:
      case Token.BREAK:
      case Token.CONTINUE:
      case Token.VAR:
      case Token.CONST:
      case Token.WITH:
      case Token.CATCH:
      case Token.FINALLY:
      case Token.BLOCK:
      case Token.LABEL:
      case Token.TARGET:
      case Token.LOOP:
      case Token.JSR:
      case Token.SETPROP_OP:
      case Token.SETELEM_OP:
      case Token.LOCAL_BLOCK:
      case Token.SET_REF_OP:
        return true;

      default:
        return false;
    }
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
      return getString();
    } else if (type == Token.GETPROP) {
      String left = getFirstChild().getQualifiedName();
      if (left == null) {
        return null;
      }
      return left + "." + getLastChild().getString();
    } else if (type == Token.THIS) {
      return "this";
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
      case Token.THIS:
        return true;
      case Token.GETPROP:
        return getFirstChild().isQualifiedName();
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
        return true;
      case Token.GETPROP:
        return getFirstChild().isUnscopedQualifiedName();
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
    if (child == last) last = prev;
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
  public Node copyInformationFrom(Node other) {
    if (getProp(ORIGINALNAME_PROP) == null) {
        putProp(ORIGINALNAME_PROP, other.getProp(ORIGINALNAME_PROP));
    }

    if (getProp(SOURCENAME_PROP) == null) {
        putProp(SOURCENAME_PROP, other.getProp(SOURCENAME_PROP));
        sourcePosition = other.sourcePosition;
    }

    return this;
  }

  /**
   * Copies source file and name information from the other node to the
   * entire tree rooted at this node.
   * @return this
   */
  public Node copyInformationFromForTree(Node other) {
    copyInformationFrom(other);
    for (Node child = getFirstChild();
         child != null; child = child.getNext()) {
      child.copyInformationFromForTree(other);
    }

    return this;
  }

  //==========================================================================
  // Custom annotations

  public JSType getJSType() {
      return jsType;
  }

  public void setJSType(JSType jsType) {
      this.jsType = jsType;
  }

  public FileLevelJsDocBuilder getJsDocBuilderForNode() {
    return new FileLevelJsDocBuilder();
  }

  /**
   * An inner class that provides back-door access to the license
   * property of the JSDocInfo property for this node. This is only
   * meant to be used for top level script nodes where the
   * {@link com.google.javascript.jscomp.parsing.JsDocInfoParser} needs to
   * be able to append directly to the top level node, not just the
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
  public void setJSDocInfo(JSDocInfo info) {
      putProp(JSDOC_INFO_PROP, info);
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
   * Sets whether this is a synthetic block that should not be considered
   * a real source block.
   */
  public void setWasEmptyNode(boolean val) {
    putBooleanProp(EMPTY_BLOCK, val);
  }

  /**
   * Returns whether this is a synthetic block that should not be considered
   * a real source block.
   */
  public boolean wasEmptyNode() {
    return getBooleanProp(EMPTY_BLOCK);
  }

  // There are four values of interest:
  //   global state changes
  //   this state changes
  //   arguments state changes
  //   whether the call throws an exception
  //   locality of the result
  // We want a value of 0 to mean "global state changes and
  // unknown locality of result".

  final public static int FLAG_GLOBAL_STATE_UNMODIFIED = 1;
  final public static int FLAG_THIS_UNMODIFIED = 2;
  final public static int FLAG_ARGUMENTS_UNMODIFIED = 4;
  final public static int FLAG_NO_THROWS = 8;
  final public static int FLAG_LOCAL_RESULTS = 16;

  final public static int SIDE_EFFECTS_FLAGS_MASK = 31;

  final public static int SIDE_EFFECTS_ALL = 0;
  final public static int NO_SIDE_EFFECTS =
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
       "setIsNoSideEffectsCall only supports CALL and NEW nodes, got " +
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
    public void setAllFlags() {
      value = Node.SIDE_EFFECTS_ALL;
    }

    /** No side-effects occur and the returned results are local. */
    public void clearAllFlags() {
      value = Node.NO_SIDE_EFFECTS | Node.FLAG_LOCAL_RESULTS;
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

    public void setMutatesGlobalState() {
      // Modify global means everything must be assumed to be modified.
      removeFlag(Node.FLAG_GLOBAL_STATE_UNMODIFIED);
      removeFlag(Node.FLAG_ARGUMENTS_UNMODIFIED);
      removeFlag(Node.FLAG_THIS_UNMODIFIED);
    }

    public void setThrows() {
      removeFlag(Node.FLAG_NO_THROWS);
    }

    public void setMutatesThis() {
      removeFlag(Node.FLAG_THIS_UNMODIFIED);
    }

    public void setMutatesArguments() {
      removeFlag(Node.FLAG_ARGUMENTS_UNMODIFIED);
    }

    public void setReturnsTainted() {
      removeFlag(Node.FLAG_LOCAL_RESULTS);
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
    Kit.codeBug();
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
}
