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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.javascript.rhino.jstype.JSType;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * This class implements the root of the intermediate representation.
 *
 */

public class Node implements Serializable {

  private static final long serialVersionUID = 1L;

  public static final byte
      JSDOC_INFO_PROP   = 29,     // contains a JSDocInfo object

      VAR_ARGS_NAME     = 30,     // the name node is a variable length
                                  // argument placeholder.
      INCRDECR_PROP      = 32,    // whether incrdecr is pre (false) or post (true)
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
      ASYNC_FN           = 61, // http://tc39.github.io/ecmascript-asyncawait/
      YIELD_ALL          = 62, // Set if a yield is a "yield all"
      EXPORT_DEFAULT     = 63, // Set if a export is a "default" export
      EXPORT_ALL_FROM    = 64, // Set if an export is a "*"
      IS_CONSTANT_VAR    = 65, // A lexical variable is inferred const
      GENERATOR_MARKER   = 66, // Used by the ES6-to-ES3 translator.
      GENERATOR_SAFE     = 67, // Used by the ES6-to-ES3 translator.

      RAW_STRING_VALUE   = 71,    // Used to support ES6 tagged template literal.
      COMPUTED_PROP_METHOD = 72,  // A computed property that has the method
                                  // syntax ( [prop]() {...} ) rather than the
                                  // property definition syntax ( [prop]: value ).
      COMPUTED_PROP_GETTER = 73,  // A computed property in a getter, e.g.
                                  // var obj = { get [prop]() {...} };
      COMPUTED_PROP_SETTER = 74,  // A computed property in a setter, e.g.
                                  // var obj = { set [prop](val) {...} };
      COMPUTED_PROP_VARIABLE = 75, // A computed property that's a variable, e.g. [prop]: string;
      ANALYZED_DURING_GTI  = 76,  // In GlobalTypeInfo, we mark some AST nodes
                                  // to avoid analyzing them during
                                  // NewTypeInference. We remove this attribute
                                  // in the fwd direction of NewTypeInference.
      CONSTANT_PROPERTY_DEF = 77, // Used to communicate information between
                                  // GlobalTypeInfo and NewTypeInference.
                                  // We use this to tag getprop nodes that
                                  // declare properties.
      DECLARED_TYPE_EXPR = 78,    // Used to attach TypeDeclarationNode ASTs to
                                  // Nodes which represent a typed NAME or
                                  // FUNCTION.
                                  //
      TYPE_BEFORE_CAST = 79,      // The type of an expression before the cast.
                                  // This will be present only if the expression is casted.
      OPT_ES6_TYPED = 80,         // The node is an optional parameter or property
                                  // in ES6 Typed syntax.
      GENERIC_TYPE_LIST = 81,     // Generic type list in ES6 typed syntax.
      IMPLEMENTS = 82,            // "implements" clause in ES6 typed syntax.
      CONSTRUCT_SIGNATURE = 83,   // This node is a TypeScript ConstructSignature
      ACCESS_MODIFIER = 84,       // TypeScript accessibility modifiers (public, protected, private)
      NON_INDEXABLE = 85,         // Indicates the node should not be indexed by analysis tools.
      PARSE_RESULTS = 86,         // Parse results stored on SCRIPT nodes to allow replaying
                                  // parse warnings/errors when cloning cached ASTs.
      GOOG_MODULE = 87,           // Indicates that a SCRIPT node is a goog.module. Remains set
                                  // after the goog.module is desugared.
      GOOG_MODULE_REQUIRE = 88,   // Node is a goog.require() as desugared by goog.module()
      FEATURE_SET = 89,           // Attaches a FeatureSet to SCRIPT nodes.
      IS_MODULE_NAME = 90,        // Indicates that a STRING node represents a namespace from
                                  // goog.module() or goog.require() call.
      WAS_PREVIOUSLY_PROVIDED = 91, // Indicates a namespace that was provided at some point in the
                                  // past.
      IS_ES6_CLASS = 92,          // Indicates that a FUNCTION node is converted from an ES6 class
      TRANSPILED = 93,            // Indicates that a SCRIPT represents a transpiled file
      DELETED = 94,               // For passes that work only on deleted funs.
      GOOG_MODULE_ALIAS = 95,     // Indicates that the node is an alias of goog.require'd module.
                                  // Aliases are desugared and inlined by compiler passes but we
                                  // need to preserve them for building index.
      IS_UNUSED_PARAMETER = 96,   // Mark a parameter as unused. Used to defer work from
                                  // RemovedUnusedVars to OptimizeParameters.
      MODULE_EXPORT = 97;         // Mark a property as a module export so that collase properties
                                  // can act on it.


  private static final String propToString(byte propType) {
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
        case SLASH_V:            return "slash_v";
        case INFERRED_FUNCTION:  return "inferred";
        case CHANGE_TIME:        return "change_time";
        case REFLECTED_OBJECT:   return "reflected_object";
        case STATIC_MEMBER:      return "static_member";
        case GENERATOR_FN:       return "generator_fn";
        case ARROW_FN:           return "arrow_fn";
        case ASYNC_FN:           return "async_fn";
        case YIELD_ALL:          return "yield_all";
        case EXPORT_DEFAULT:     return "export_default";
        case EXPORT_ALL_FROM:    return "export_all_from";
        case IS_CONSTANT_VAR:    return "is_constant_var";
        case GENERATOR_MARKER:   return "is_generator_marker";
        case GENERATOR_SAFE:     return "is_generator_safe";
        case RAW_STRING_VALUE:   return "raw_string_value";
        case COMPUTED_PROP_METHOD: return "computed_prop_method";
        case COMPUTED_PROP_GETTER: return "computed_prop_getter";
        case COMPUTED_PROP_SETTER: return "computed_prop_setter";
        case COMPUTED_PROP_VARIABLE: return "computed_prop_variable";
        case ANALYZED_DURING_GTI:  return "analyzed_during_gti";
        case CONSTANT_PROPERTY_DEF: return "constant_property_def";
        case DECLARED_TYPE_EXPR: return "declared_type_expr";
        case TYPE_BEFORE_CAST:   return "type_before_cast";
        case OPT_ES6_TYPED:      return "opt_es6_typed";
        case GENERIC_TYPE_LIST:       return "generic_type";
        case IMPLEMENTS:         return "implements";
        case CONSTRUCT_SIGNATURE: return "construct_signature";
        case ACCESS_MODIFIER:    return "access_modifier";
        case NON_INDEXABLE:      return "non_indexable";
        case PARSE_RESULTS:      return "parse_results";
        case GOOG_MODULE:        return "goog_module";
        case GOOG_MODULE_REQUIRE: return "goog_module_require";
        case FEATURE_SET:        return "feature_set";
        case IS_MODULE_NAME:     return "is_module_name";
        case WAS_PREVIOUSLY_PROVIDED: return "was_previously_provided";
        case IS_ES6_CLASS:       return "is_es6_class";
        case TRANSPILED:         return "transpiled";
        case DELETED:            return "DELETED";
        case GOOG_MODULE_ALIAS:  return "goog_module_alias";
        case IS_UNUSED_PARAMETER: return "is_unused_parameter";
        case MODULE_EXPORT:      return "module_export";
        default:
          throw new IllegalStateException("unexpected prop id " + propType);
      }
  }

  /**
   * Represents a node in the type declaration AST.
   */
  public static final class TypeDeclarationNode extends Node {

    private static final long serialVersionUID = 1L;
    private String str; // This is used for specialized signatures.

    public TypeDeclarationNode(Token nodeType, String str) {
      super(nodeType);
      this.str = str;
    }

    public TypeDeclarationNode(Token nodeType) {
      super(nodeType);
    }

    public TypeDeclarationNode(Token nodeType, Node child) {
      super(nodeType, child);
    }

    /**
     * returns the string content.
     * @return non null.
     */
    @Override
    public String getString() {
      return str;
    }

    @Override
    public TypeDeclarationNode cloneNode(boolean cloneTypeExprs) {
      return copyNodeFields(new TypeDeclarationNode(token, str), cloneTypeExprs);
    }
  }

  private static final class NumberNode extends Node {

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
    public boolean isEquivalentTo(
        Node node, boolean compareType, boolean recur, boolean jsDoc, boolean sideEffect) {
      boolean equiv = super.isEquivalentTo(node, compareType, recur, jsDoc, sideEffect);
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

    @Override
    public NumberNode cloneNode(boolean cloneTypeExprs) {
      return copyNodeFields(new NumberNode(number), cloneTypeExprs);
    }
  }

  private static final class StringNode extends Node {

    private static final long serialVersionUID = 1L;

    // Only for cloneNode
    private StringNode(Token token) {
      super(token);
    }

    StringNode(Token token, String str) {
      super(token);
      setString(str);
    }

    StringNode(Token token, String str, int lineno, int charno) {
      super(token, lineno, charno);
      setString(str);
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
      // Intern the string reference so that serialization won't save repeated strings.
      this.str = str.intern();
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public boolean isEquivalentTo(
        Node node, boolean compareType, boolean recur, boolean jsDoc, boolean sideEffect) {
      return (super.isEquivalentTo(node, compareType, recur, jsDoc, sideEffect)
          && this.str == (((StringNode) node).str));
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

    @Override
    public StringNode cloneNode(boolean cloneTypeExprs) {
      StringNode clone = new StringNode(token);
      clone.str = str;
      return copyNodeFields(clone, cloneTypeExprs);
    }

    @GwtIncompatible("ObjectInputStream")
    private void readObject(java.io.ObjectInputStream in) throws Exception {
      in.defaultReadObject();

      this.str = this.str.intern();
    }
  }

  private abstract static class PropListItem implements Serializable {
    final @Nullable PropListItem next;
    final byte propType;

    PropListItem(byte propType, @Nullable PropListItem next) {
      this.propType = propType;
      this.next = next;
    }

    public abstract int getIntValue();
    public abstract Object getObjectValue();
    public abstract PropListItem chain(@Nullable PropListItem next);
  }

  // A base class for Object storing props
  private static final class ObjectPropListItem extends PropListItem {
    private final Object objectValue;

    ObjectPropListItem(byte propType, Object objectValue, @Nullable PropListItem next) {
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
    public PropListItem chain(@Nullable PropListItem next) {
      return new ObjectPropListItem(propType, objectValue, next);
    }
  }

  // A base class for int storing props
  private static final class IntPropListItem extends PropListItem {
    final int intValue;

    IntPropListItem(byte propType, int intValue, @Nullable PropListItem next) {
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
    public PropListItem chain(@Nullable PropListItem next) {
      return new IntPropListItem(propType, intValue, next);
    }
  }

  public Node(Token nodeType) {
    token = nodeType;
    parent = null;
    sourcePosition = -1;
  }

  public Node(Token nodeType, Node child) {
    checkArgument(child.parent == null, "new child has existing parent");
    checkArgument(child.next == null, "new child has existing next sibling");
    checkArgument(child.previous == null, "new child has existing previous sibling");

    token = nodeType;
    parent = null;
    first = child;
    child.next = null;
    child.previous = first;
    child.parent = this;
    sourcePosition = -1;
  }

  public Node(Token nodeType, Node left, Node right) {
    checkArgument(left.parent == null, "first new child has existing parent");
    checkArgument(left.next == null, "first new child has existing next sibling");
    checkArgument(left.previous == null, "first new child has existing previous sibling");
    checkArgument(right.parent == null, "second new child has existing parent");
    checkArgument(right.next == null, "second new child has existing next sibling");
    checkArgument(right.previous == null, "second new child has existing previous sibling");
    token = nodeType;
    parent = null;
    first = left;
    left.next = right;
    left.previous = right;
    left.parent = this;
    right.next = null;
    right.previous = left;
    right.parent = this;
    sourcePosition = -1;
  }

  public Node(Token nodeType, Node left, Node mid, Node right) {
    checkArgument(left.parent == null);
    checkArgument(left.next == null);
    checkArgument(left.previous == null);
    checkArgument(mid.parent == null);
    checkArgument(mid.next == null);
    checkArgument(mid.previous == null);
    checkArgument(right.parent == null);
    checkArgument(right.next == null);
    checkArgument(right.previous == null);
    token = nodeType;
    parent = null;
    first = left;
    left.next = mid;
    left.previous = right;
    left.parent = this;
    mid.next = right;
    mid.previous = left;
    mid.parent = this;
    right.next = null;
    right.previous = mid;
    right.parent = this;
    sourcePosition = -1;
  }

  Node(Token nodeType, Node left, Node mid, Node mid2, Node right) {
    checkArgument(left.parent == null);
    checkArgument(left.next == null);
    checkArgument(left.previous == null);
    checkArgument(mid.parent == null);
    checkArgument(mid.next == null);
    checkArgument(mid.previous == null);
    checkArgument(mid2.parent == null);
    checkArgument(mid2.next == null);
    checkArgument(mid2.previous == null);
    checkArgument(right.parent == null);
    checkArgument(right.next == null);
    checkArgument(right.previous == null);
    token = nodeType;
    parent = null;
    first = left;
    left.next = mid;
    left.previous = right;
    left.parent = this;
    mid.next = mid2;
    mid.previous = left;
    mid.parent = this;
    mid2.next = right;
    mid2.previous = mid;
    mid2.parent = this;
    right.next = null;
    right.previous = mid2;
    right.parent = this;
    sourcePosition = -1;
  }

  public Node(Token nodeType, int lineno, int charno) {
    token = nodeType;
    parent = null;
    sourcePosition = mergeLineCharNo(lineno, charno);
  }

  public Node(Token nodeType, Node child, int lineno, int charno) {
    this(nodeType, child);
    sourcePosition = mergeLineCharNo(lineno, charno);
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

  public static Node newString(Token token, String str) {
    return new StringNode(token, str);
  }

  public static Node newString(String str, int lineno, int charno) {
    return new StringNode(Token.STRING, str, lineno, charno);
  }

  public static Node newString(Token token, String str, int lineno, int charno) {
    return new StringNode(token, str, lineno, charno);
  }

  public final Token getToken() {
    return token;
  }

  public final void setToken(Token token) {
    this.token = token;
  }

  public final boolean hasChildren() {
    return first != null;
  }

  public final Node getOnlyChild() {
    checkState(hasOneChild());
    return first;
  }

  @Nullable
  public final Node getFirstChild() {
    return first;
  }

  /**
   * Get the first child of the first child. This method assumes that the first child exists.
   *
   * @return The first child of the first child.
   */
  @Nullable
  public final Node getFirstFirstChild() {
    return first.first;
  }

  @Nullable
  public final Node getSecondChild() {
    return first.next;
  }

  @Nullable
  public final Node getLastChild() {
    return first != null ? first.previous : null;
  }

  @Nullable
  public final Node getNext() {
    return next;
  }

  @Nullable
  public final Node getPrevious() {
    return this == parent.first ? null : previous;
  }

  @Nullable
  private final Node getPrevious(@Nullable Node firstSibling) {
    return this == firstSibling ? null : previous;
  }

  @Nullable
  public final Node getChildBefore(Node child) {
    return child.getPrevious(first);
  }

  /**
   * Gets the ith child, note that this is O(N) where N is the number of children.
   *
   * @param i The index
   * @return The ith child
   */
  public final Node getChildAtIndex(int i) {
    Node n = first;
    while (i > 0) {
      n = n.next;
      i--;
    }
    return n;
  }

  /**
   * Gets the index of a child, note that this is O(N) where N is the number of children.
   *
   * @param child The child
   * @return The index of the child
   */
  public final int getIndexOfChild(Node child) {
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

  public final void addChildToFront(Node child) {
    checkArgument(child.parent == null);
    checkArgument(child.next == null);
    checkArgument(child.previous == null);
    child.parent = this;
    child.next = first;
    if (first == null) {
      // NOTE: child.next remains null
      child.previous = child;
    } else {
      Node last = first.previous;
      // NOTE: last.next remains null
      child.previous = last;
      child.next = first;
      first.previous = child;
    }
    first = child;
  }

  public final void addChildToBack(Node child) {
    checkArgument(
        child.parent == null,
        "Cannot add already-owned child node.\nChild: %s\nExisting parent: %s\nNew parent: %s",
        child, child.parent, this);
    checkArgument(child.next == null);
    checkArgument(child.previous == null);

    if (first == null) {
      // NOTE: child.next remains null
      child.previous = child;
      first = child;
    } else {
      Node last = first.previous;
      last.next = child;
      // NOTE: child.next remains null
      child.previous = last;
      first.previous = child;
    }

    child.parent = this;
  }

  public final void addChildrenToFront(Node children) {
    if (children == null) {
      return; // removeChildren() returns null when there are none
    }
    for (Node child = children; child != null; child = child.next) {
      checkArgument(child.parent == null);
      child.parent = this;
    }

    Node lastSib = children.previous;
    if (first != null) {
      Node last = first.previous;
      // NOTE: last.next remains null
      children.previous = last;
      lastSib.next = first;
      first.previous = lastSib;
    }
    first = children;
  }

  public final void addChildrenToBack(Node children) {
    addChildrenAfter(children, getLastChild());
  }

  /**
   * Add 'child' before 'node'.
   */
  public final void addChildBefore(Node newChild, Node node) {
    checkArgument(node.parent == this, "The existing child node of the parent should not be null.");
    checkArgument(newChild.next == null, "The new child node has next siblings.");
    checkArgument(newChild.previous == null, "The new child node has previous siblings.");
    checkArgument(newChild.parent == null, "The new child node already has a parent.");
    if (first == node) {
      Node last = first.previous;
      // NOTE: last.next remains null
      newChild.parent = this;
      newChild.next = first;
      newChild.previous = last;
      first.previous = newChild;
      first = newChild;
    } else {
      addChildAfter(newChild, node.previous);
    }
  }

  /**
   * Add 'child' after 'node'.
   */
  public final void addChildAfter(Node newChild, Node node) {
    checkArgument(newChild.next == null, "The new child node has next siblings.");
    checkArgument(newChild.previous == null, "The new child node has previous siblings.");
    // NOTE: newChild.next remains null
    newChild.previous = newChild;
    addChildrenAfter(newChild, node);
  }

  /** Add all children after 'node'. */
  public final void addChildrenAfter(@Nullable Node children, @Nullable Node node) {
    if (children == null) {
      return; // removeChildren() returns null when there are none
    }
    checkArgument(node == null || node.parent == this);
    checkNotNull(children.previous);
    if (node == null) {
      addChildrenToFront(children);
      return;
    }

    for (Node child = children; child != null; child = child.next) {
      checkArgument(child.parent == null);
      child.parent = this;
    }

    Node lastSibling = children.previous;
    Node nodeAfter = node.next;
    lastSibling.next = nodeAfter;
    if (nodeAfter == null) {
      first.previous = lastSibling;
    } else {
      nodeAfter.previous = lastSibling;
    }
    node.next = children;
    children.previous = node;
  }

  /** Detach a child from its parent and siblings. */
  public final void removeChild(Node child) {
    checkState(child.parent == this, "%s is not the parent of %s", this, child);
    checkNotNull(child.previous);

    Node last = first.previous;
    Node prevSibling = child.previous;
    Node nextSibling = child.next;
    if (first == child) {
      first = nextSibling;
      if (nextSibling != null) {
        nextSibling.previous = last;
      }
      // last.next remains null
    } else if (child == last) {
      first.previous = prevSibling;
      prevSibling.next = null;
    } else {
      prevSibling.next = nextSibling;
      nextSibling.previous = prevSibling;
    }

    child.next = null;
    child.previous = null;
    child.parent = null;
  }

  /**
   * Detaches Node and replaces it with newNode.
   */
  public final void replaceWith(Node newNode) {
    parent.replaceChild(this, newNode);
  }

  /** Detaches child from Node and replaces it with newChild. */
  public final void replaceChild(Node child, Node newChild) {
    checkArgument(newChild.next == null, "The new child node has next siblings.");
    checkArgument(newChild.previous == null, "The new child node has previous siblings.");
    checkArgument(newChild.parent == null, "The new child node already has a parent.");
    checkState(child.parent == this, "%s is not the parent of %s", this, child);

    // Copy over important information.
    newChild.useSourceInfoIfMissingFrom(child);
    newChild.parent = this;

    Node nextSibling = child.next;
    Node prevSibling = child.previous;

    Node last = first.previous;

    if (child == prevSibling) {  // first and only child
      first = newChild;
      first.previous = newChild;
    } else {
      if (child == first) {
        first = newChild;
        // prevSibling == last, and last.next remains null
      } else {
        prevSibling.next = newChild;
      }

      if (child == last) {
        first.previous = newChild;
      } else {
        nextSibling.previous = newChild;
      }

      newChild.previous = prevSibling;
    }
    newChild.next = nextSibling;  // maybe null

    child.next = null;
    child.previous = null;
    child.parent = null;
  }

  public final void replaceChildAfter(Node prevChild, Node newChild) {
    checkNotNull(prevChild.next, "prev doesn't have a sibling to replace.");
    replaceChild(prevChild.next, newChild);
  }

  /** Detaches the child after the given child, or the first child if prev is null. */
  public final void replaceFirstOrChildAfter(@Nullable Node prev, Node newChild) {
    Node target = prev == null ? first : prev.next;
    checkNotNull(target, "prev doesn't have a sibling to replace.");
    replaceChild(target, newChild);
  }

  @VisibleForTesting
  @Nullable
  final PropListItem lookupProperty(byte propType) {
    PropListItem x = propListHead;
    while (x != null && propType != x.propType) {
      x = x.next;
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
  public final Node clonePropsFrom(Node other) {
    checkState(this.propListHead == null, "Node has existing properties.");
    this.propListHead = other.propListHead;
    return this;
  }

  public final void removeProp(byte propType) {
    PropListItem result = removeProp(propListHead, propType);
    if (result != propListHead) {
      propListHead = result;
    }
  }

  public final boolean hasProps() {
    return propListHead != null;
  }

  /**
   * @param item The item to inspect
   * @param propType The property to look for
   * @return The replacement list if the property was removed, or 'item' otherwise.
   */
  @Nullable
  private final PropListItem removeProp(@Nullable PropListItem item, byte propType) {
    if (item == null) {
      return null;
    } else if (item.propType == propType) {
      return item.next;
    } else {
      PropListItem result = removeProp(item.next, propType);
      if (result != item.next) {
        return item.chain(result);
      } else {
        return item;
      }
    }
  }

  @Nullable
  public final Object getProp(byte propType) {
    PropListItem item = lookupProperty(propType);
    if (item == null) {
      return null;
    }
    return item.getObjectValue();
  }

  public final boolean getBooleanProp(byte propType) {
    return getIntProp(propType) != 0;
  }

  /**
   * Returns the integer value for the property, or 0 if the property
   * is not defined.
   */
  public final int getIntProp(byte propType) {
    PropListItem item = lookupProperty(propType);
    if (item == null) {
      return 0;
    }
    return item.getIntValue();
  }

  public final int getExistingIntProp(byte propType) {
    PropListItem item = lookupProperty(propType);
    if (item == null) {
      throw new IllegalStateException("missing prop: " + propType);
    }
    return item.getIntValue();
  }

  public final void putProp(byte propType, @Nullable Object value) {
    removeProp(propType);
    if (value != null) {
      propListHead = createProp(propType, value, propListHead);
    }
  }

  public final void putBooleanProp(byte propType, boolean value) {
    putIntProp(propType, value ? 1 : 0);
  }

  public final void putIntProp(byte propType, int value) {
    removeProp(propType);
    if (value != 0) {
      propListHead = createProp(propType, value, propListHead);
    }
  }

  /**
   * Sets the syntactical type specified on this node.
   * @param typeExpression
   */
  public final void setDeclaredTypeExpression(TypeDeclarationNode typeExpression) {
    putProp(DECLARED_TYPE_EXPR, typeExpression);
  }

  /**
   * Returns the syntactical type specified on this node. Not to be confused with {@link
   * #getJSType()} which returns the compiler-inferred type.
   */
  @Nullable
  public final TypeDeclarationNode getDeclaredTypeExpression() {
    return (TypeDeclarationNode) getProp(DECLARED_TYPE_EXPR);
  }

  final PropListItem createProp(byte propType, Object value, @Nullable PropListItem next) {
    return new ObjectPropListItem(propType, value, next);
  }

  final PropListItem createProp(byte propType, int value, @Nullable PropListItem next) {
    return new IntPropListItem(propType, value, next);
  }

  /**
   * Returns the type of this node before casting. This annotation will only exist on the first
   * child of a CAST node after type checking.
   */
  @Nullable
  public final JSType getJSTypeBeforeCast() {
    return (JSType) getTypeIBeforeCast();
  }

  @Nullable
  public final TypeI getTypeIBeforeCast() {
    return (TypeI) getProp(TYPE_BEFORE_CAST);
  }

  // Gets all the property types, in sorted order.
  private byte[] getSortedPropTypes() {
    int count = 0;
    for (PropListItem x = propListHead; x != null; x = x.next) {
      count++;
    }

    byte[] keys = new byte[count];
    for (PropListItem x = propListHead; x != null; x = x.next) {
      count--;
      keys[count] = x.propType;
    }

    Arrays.sort(keys);
    return keys;
  }

  /** Can only be called when <tt>getType() == TokenStream.NUMBER</tt> */
  public double getDouble() {
    if (this.token == Token.NUMBER) {
      throw new IllegalStateException(
          "Number node not created with Node.newNumber");
    } else {
      throw new UnsupportedOperationException(this + " is not a number node");
    }
  }

  /**
   * Can only be called when <tt>getType() == Token.NUMBER</tt>
   *
   * @param value value to set.
   */
  public void setDouble(double value) {
    if (this.token == Token.NUMBER) {
      throw new IllegalStateException(
          "Number node not created with Node.newNumber");
    } else {
      throw new UnsupportedOperationException(this + " is not a string node");
    }
  }

  /** Can only be called when node has String context. */
  public String getString() {
    if (this.token == Token.STRING) {
      throw new IllegalStateException(
          "String node not created with Node.newString");
    } else {
      throw new UnsupportedOperationException(this + " is not a string node");
    }
  }

  /**
   * Can only be called for a Token.STRING or Token.NAME.
   *
   * @param value the value to set.
   */
  public void setString(String value) {
    if (this.token == Token.STRING || this.token == Token.NAME) {
      throw new IllegalStateException(
          "String node not created with Node.newString");
    } else {
      throw new UnsupportedOperationException(this + " is not a string node");
    }
  }

  @Override
  public final String toString() {
    return toString(true, true, true);
  }

  public final String toString(
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
    sb.append(token);
    if (this instanceof StringNode) {
      sb.append(' ');
      sb.append(getString());
    } else if (token == Token.FUNCTION) {
      sb.append(' ');
      // In the case of JsDoc trees, the first child is often not a string
      // which causes exceptions to be thrown when calling toString or
      // toStringTree.
      if (first == null || first.token != Token.NAME) {
        sb.append("<invalid>");
      } else {
        sb.append(first.getString());
      }
    } else if (token == Token.NUMBER) {
      sb.append(' ');
      sb.append(getDouble());
    }
    if (printSource) {
      int lineno = getLineno();
      if (lineno != -1) {
        sb.append(' ');
        sb.append(lineno);
      }
      if (length != 0) {
        sb.append(" [length: ");
        sb.append(length);
        sb.append(']');
      }
    }

    if (printAnnotations) {
      byte[] keys = getSortedPropTypes();
      for (int i = 0; i < keys.length; i++) {
        byte type = keys[i];
        PropListItem x = lookupProperty(type);
        sb.append(" [");
        sb.append(propToString(type));
        sb.append(": ");
        sb.append(x);
        sb.append(']');
      }
    }

    if (printType && typei != null) {
      String typeString = typei.toString();
      if (typeString != null) {
        sb.append(" : ");
        sb.append(typeString);
      }
    }
  }

  @CheckReturnValue
  public final String toStringTree() {
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

  public final void appendStringTree(Appendable appendable) throws IOException {
    toStringTreeHelper(this, 0, appendable);
  }

  private static void toStringTreeHelper(Node n, int level, Appendable sb)
      throws IOException {
    for (int i = 0; i != level; ++i) {
      sb.append("    ");
    }
    sb.append(n.toString());
    sb.append('\n');
    for (Node cursor = n.first; cursor != null; cursor = cursor.next) {
      toStringTreeHelper(cursor, level + 1, sb);
    }
  }

  transient Token token;           // Type of the token of the node; NAME for example
  @Nullable transient Node next; // next sibling, a linked list
  @Nullable transient Node previous; // previous sibling, a circular linked list
  @Nullable transient Node first; // first element of a linked list of children
  // We get the last child as first.previous. But last.next is null, not first.

  /**
   * Linked list of properties. Since vast majority of nodes would have no more than 2 properties,
   * linked list saves memory and provides fast lookup. If this does not holds, propListHead can be
   * replaced by UintMap.
   */
  @Nullable private transient PropListItem propListHead;

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
  private transient int sourcePosition;

  /** The length of the code represented by the node. */
  private transient int length;

  @Nullable private transient TypeI typei;

  @Nullable protected transient Node parent;

  //==========================================================================
  // Source position management

  public final void setStaticSourceFileFrom(Node other) {
    // Make sure source file prop nodes are not duplicated.
    if (other.propListHead != null
        && (this.propListHead == null
            || (this.propListHead.propType == STATIC_SOURCE_FILE
               && this.propListHead.next == null))) {
      // Either the node has only STATIC_SOURCE_FILE as a property or has not properties.
      PropListItem tail = other.propListHead;
      while (tail.next != null) {
        tail = tail.next;
      }
      if (tail.propType == STATIC_SOURCE_FILE) {
        propListHead = tail;
        return;
      }
    }
    setStaticSourceFile(other.getStaticSourceFile());
  }

  public final void setStaticSourceFile(@Nullable StaticSourceFile file) {
    this.putProp(STATIC_SOURCE_FILE, file);
  }

  /** Sets the source file to a non-extern file of the given name. */
  public final void setSourceFileForTesting(String name) {
    this.putProp(STATIC_SOURCE_FILE, new SimpleSourceFile(name, false));
  }

  // TODO(johnlenz): make this final
  @Nullable
  public String getSourceFileName() {
    StaticSourceFile file = getStaticSourceFile();
    return file == null ? null : file.getName();
  }

  /** Returns the source file associated with this input. */
  @Nullable
  public StaticSourceFile getStaticSourceFile() {
    return ((StaticSourceFile) this.getProp(STATIC_SOURCE_FILE));
  }

  /**
   * @param inputId
   */
  public void setInputId(InputId inputId) {
    this.putProp(INPUT_ID, inputId);
  }

  /** @return The Id of the CompilerInput associated with this Node. */
  @Nullable
  public InputId getInputId() {
    return ((InputId) this.getProp(INPUT_ID));
  }

  /** The original name of this node, if the node has been renamed. */
  @Nullable
  public String getOriginalName() {
    return (String) this.getProp(ORIGINALNAME_PROP);
  }

  public void setOriginalName(String originalName) {
    this.putProp(ORIGINALNAME_PROP, originalName);
  }

  /**
   * Whether this node should be indexed by static analysis / code indexing tools.
   */
  public final boolean isIndexable() {
    return !this.getBooleanProp(NON_INDEXABLE);
  }

  public final void makeNonIndexable() {
    this.putBooleanProp(NON_INDEXABLE, true);
  }

  public final void makeNonIndexableRecursive() {
    this.makeNonIndexable();
    for (Node child : children()) {
      child.makeNonIndexableRecursive();
    }
  }

  public final boolean isFromExterns() {
    StaticSourceFile file = getStaticSourceFile();
    return file == null ? false : file.isExtern();
  }

  public final int getLength() {
    return this.length;
  }

  public final void setLength(int length) {
    this.length = length;
  }

  public final int getLineno() {
    return extractLineno(sourcePosition);
  }

  // Returns the 0-based column number
  public final int getCharno() {
    return extractCharno(sourcePosition);
  }

  // TODO(johnlenz): make this final
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

  public final int getSourcePosition() {
    return sourcePosition;
  }

  public final void setLineno(int lineno) {
      int charno = getCharno();
      if (charno == -1) {
        charno = 0;
      }
      sourcePosition = mergeLineCharNo(lineno, charno);
  }

  public final void setCharno(int charno) {
      sourcePosition = mergeLineCharNo(getLineno(), charno);
  }

  public final void setSourceEncodedPosition(int sourcePosition) {
    this.sourcePosition = sourcePosition;
  }

  public final void setSourceEncodedPositionForTree(int sourcePosition) {
    this.sourcePosition = sourcePosition;

    for (Node child = first; child != null; child = child.next) {
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
  public final Iterable<Node> children() {
    if (first == null) {
      return Collections.emptySet();
    } else {
      return new SiblingNodeIterable(first);
    }
  }

  /**
   * <p>Return an iterable object that iterates over this node's siblings,
   * <b>including this Node</b> but not any siblings that are before this one.
   *
   * <p>The iterator does not support the optional
   * operation {@link Iterator#remove()}.</p>
   *
   * <p>To iterate over a node's siblings including itself, one can write</p>
   * <pre>Node n = ...;
   * for (Node sibling : n.siblings()) { ...</pre>
   */
  public final Iterable<Node> siblings() {
    return new SiblingNodeIterable(this);
  }

  /**
   * @see Node#siblings()
   */
  private static final class SiblingNodeIterable implements Iterable<Node> {
    private final Node start;

    SiblingNodeIterable(Node start) {
      this.start = start;
    }
    @Override
    public Iterator<Node> iterator() {
      return new SiblingNodeIterator(start);
    }
  }

  /**
   * @see Node#siblings()
   */
  private static final class SiblingNodeIterator implements Iterator<Node> {
    @Nullable private Node current;

    SiblingNodeIterator(Node start) {
      this.current = start;
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
      Node n = current;
      current = current.getNext();
      return n;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  // ==========================================================================
  // Accessors

  @Nullable
  final PropListItem getPropListHeadForTesting() {
    return propListHead;
  }

  final void setPropListHead(@Nullable PropListItem propListHead) {
    this.propListHead = propListHead;
  }

  @Nullable
  public final Node getParent() {
    return parent;
  }

  @Nullable
  public final Node getGrandparent() {
    return parent == null ? null : parent.parent;
  }

  /**
   * Gets the ancestor node relative to this.
   *
   * @param level 0 = this, 1 = the parent, etc.
   */
  @Nullable
  public final Node getAncestor(int level) {
    checkArgument(level >= 0);
    Node node = this;
    while (node != null && level-- > 0) {
      node = node.getParent();
    }
    return node;
  }

  /** @return True if this Node is {@code node} or a descendant of {@code node}. */
  public final boolean isDescendantOf(Node node) {
    for (Node n = this; n != null; n = n.parent) {
      if (n == node) {
        return true;
      }
    }
    return false;
  }

  /**
   * Iterates all of the node's ancestors excluding itself.
   */
  public final AncestorIterable getAncestors() {
    return new AncestorIterable(checkNotNull(this.getParent()));
  }

  /**
   * Iterator to go up the ancestor tree.
   */
  public static final class AncestorIterable implements Iterable<Node> {
    @Nullable private Node cur;

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
  public final boolean hasOneChild() {
    return first != null && first.next == null;
  }

  /**
   * Check for two children more efficiently than {@code getChildCount() == 2}
   *
   * @return Whether the node has exactly two children.
   */
  public final boolean hasTwoChildren() {
    return first != null && first.next != null && first.next == getLastChild();
  }

  /**
   * Check for zero or one child more efficiently than by iterating over all the
   * children as is done with Node.getChildCount().
   *
   * @return Whether the node has no children or exactly one child.
   */
  public final boolean hasZeroOrOneChild() {
    return first == getLastChild();
  }

  /**
   * Check for more than one child more efficiently than by iterating over all
   * the children as is done with Node.getChildCount().
   *
   * @return Whether the node more than one child.
   */
  public final boolean hasMoreThanOneChild() {
    return first != null && first.next != null;
  }

  /**
   * Check for has exactly the number of specified children.
   *
   * @return Whether the node has exactly the number of children specified.
   */
  public final boolean hasXChildren(int x) {
    int c = 0;
    for (Node n = first; n != null && c <= x; n = n.next) {
      c++;
    }
    return c == x;
  }

  public final int getChildCount() {
    int c = 0;
    for (Node n = first; n != null; n = n.next) {
      c++;
    }
    return c;
  }

  // Intended for testing and verification only.
  public final boolean hasChild(Node child) {
    for (Node n = first; n != null; n = n.next) {
      if (child == n) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if the subtree under this node is the same as another subtree. Returns null if it's
   * equal, or a message describing the differences. Should be called with {@code this} as the
   * "expected" node and {@code actual} as the "actual" node.
   */
  @VisibleForTesting
  @Nullable
  public final String checkTreeEquals(Node actual) {
      NodeMismatch diff = checkTreeEqualsImpl(actual);
      if (diff != null) {
        return "Node tree inequality:" +
            "\nTree1:\n" + toStringTree() +
            "\n\nTree2:\n" + actual.toStringTree() +
            "\n\nSubtree1: " + diff.nodeExpected.toStringTree() +
            "\n\nSubtree2: " + diff.nodeActual.toStringTree();
      }
      return null;
  }

  /**
   * Checks if the subtree under this node is the same as another subtree. Returns null if it's
   * equal, or a message describing the differences. Considers two nodes to be unequal if their
   * JSDocInfo doesn't match. Should be called with {@code this} as the "expected" node and {@code
   * actual} as the "actual" node.
   *
   * @see JSDocInfo#equals(Object)
   */
  @VisibleForTesting
  @Nullable
  public final String checkTreeEqualsIncludingJsDoc(Node actual) {
      NodeMismatch diff = checkTreeEqualsImpl(actual, true);
      if (diff != null) {
        if (diff.nodeActual.isEquivalentTo(diff.nodeExpected, false, true, false)) {
          // The only difference is that the JSDoc is different on
          // the subtree.
          String jsDocActual = diff.nodeActual.getJSDocInfo() == null ?
              "(none)" :
              diff.nodeActual.getJSDocInfo().toStringVerbose();

          String jsDocExpected = diff.nodeExpected.getJSDocInfo() == null ?
              "(none)" :
              diff.nodeExpected.getJSDocInfo().toStringVerbose();

          return "Node tree inequality:" +
              "\nTree:\n" + toStringTree() +
              "\n\nJSDoc differs on subtree: " + diff.nodeExpected +
              "\nExpected JSDoc: " + jsDocExpected +
              "\nActual JSDoc  : " + jsDocActual;
        }
        return "Node tree inequality:" +
            "\nExpected tree:\n" + toStringTree() +
            "\n\nActual tree:\n" + actual.toStringTree() +
            "\n\nExpected subtree: " + diff.nodeExpected.toStringTree() +
            "\n\nActual subtree: " + diff.nodeActual.toStringTree();
      }
      return null;
  }

  /**
   * Compare this node to the given node recursively and return the first pair of nodes that differs
   * doing a preorder depth-first traversal. Package private for testing. Returns null if the nodes
   * are equivalent. Should be called with {@code this} as the "expected" node and {@code actual} as
   * the "actual" node.
   */
  @Nullable
  final NodeMismatch checkTreeEqualsImpl(Node actual) {
    return checkTreeEqualsImpl(actual, false);
  }

  /**
   * Compare this node to the given node recursively and return the first pair of nodes that differs
   * doing a preorder depth-first traversal. Should be called with {@code this} as the "expected"
   * node and {@code actual} as the "actual" node.
   *
   * @param jsDoc Whether to check for differences in JSDoc.
   */
  @Nullable
  private NodeMismatch checkTreeEqualsImpl(Node actual, boolean jsDoc) {
    if (!isEquivalentTo(actual, false, false, jsDoc)) {
      return new NodeMismatch(this, actual);
    }

    for (Node expectedChild = first, actualChild = actual.first;
         expectedChild != null;
         expectedChild = expectedChild.next, actualChild = actualChild.next) {
      NodeMismatch res = expectedChild.checkTreeEqualsImpl(actualChild, jsDoc);
      if (res != null) {
        return res;
      }
    }
    return null;
  }

  /** Returns true if this node is equivalent semantically to another */
  public final boolean isEquivalentTo(Node node) {
    return isEquivalentTo(node, false, true, false, false);
  }

  /** Checks equivalence without going into child nodes */
  public final boolean isEquivalentToShallow(Node node) {
    return isEquivalentTo(node, false, false, false, false);
  }

  /** Returns true if this node is equivalent semantically to another including side effects. */
  public final boolean isEquivalentWithSideEffectsTo(Node node) {
    return isEquivalentTo(node, false, true, false, true);
  }

  /** Returns true if this node is equivalent semantically to another including side effects. */
  public final boolean isEquivalentWithSideEffectsToShallow(Node node) {
    return isEquivalentTo(node, false, false, false, true);
  }

  /**
   * Returns true if this node is equivalent semantically to another and the types are equivalent.
   */
  public final boolean isEquivalentToTyped(Node node) {
    return isEquivalentTo(node, true, true, true, false);
  }

  /**
   * @param compareType Whether to compare the JSTypes of the nodes.
   * @param recurse Whether to compare the children of the current node. If not, only the count of
   *     the children are compared.
   * @param jsDoc Whether to check that the JsDoc of the nodes are equivalent.
   * @return Whether this node is equivalent semantically to the provided node.
   */
  final boolean isEquivalentTo(Node node, boolean compareType, boolean recurse, boolean jsDoc) {
    return isEquivalentTo(node, compareType, recurse, jsDoc, false);
  }

  /**
   * @param compareType Whether to compare the JSTypes of the nodes.
   * @param recurse Whether to compare the children of the current node. If not, only the count
   *     of the children are compared.
   * @param jsDoc Whether to check that the JsDoc of the nodes are equivalent.
   * @param sideEffect Whether to check that the side-effect flags of the nodes are equivalent.
   * @return Whether this node is equivalent semantically to the provided node.
   */
  public boolean isEquivalentTo(
      Node node, boolean compareType, boolean recurse, boolean jsDoc, boolean sideEffect) {
    if (token != node.token
        || getChildCount() != node.getChildCount()
        || this.getClass() != node.getClass()) {
      return false;
    }

    if (compareType && !JSType.isEquivalent(getJSType(), node.getJSType())) {
      return false;
    }

    if (jsDoc && !JSDocInfo.areEquivalent(getJSDocInfo(), node.getJSDocInfo())) {
      return false;
    }

    TypeDeclarationNode thisTDN = this.getDeclaredTypeExpression();
    TypeDeclarationNode thatTDN = node.getDeclaredTypeExpression();
    if ((thisTDN != null || thatTDN != null)
        && (thisTDN == null
            || thatTDN == null
            || !thisTDN.isEquivalentTo(thatTDN, compareType, recurse, jsDoc))) {
      return false;
    }

    if (token == Token.INC || token == Token.DEC) {
      int post1 = this.getIntProp(INCRDECR_PROP);
      int post2 = node.getIntProp(INCRDECR_PROP);
      if (post1 != post2) {
        return false;
      }
    } else if (token == Token.STRING || token == Token.STRING_KEY) {
      if (token == Token.STRING_KEY) {
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
    } else if (token == Token.CALL) {
      if (this.getBooleanProp(FREE_CALL) != node.getBooleanProp(FREE_CALL)) {
        return false;
      }
    } else if (token == Token.FUNCTION) {
      if (this.isArrowFunction() != node.isArrowFunction()) {
        return false;
      }
    }

    if (sideEffect) {
      if (this.getSideEffectFlags() != node.getSideEffectFlags()) {
        return false;
      }

      if (this.isUnusedParameter() != node.isUnusedParameter()) {
        return false;
      }
    }

    if (recurse) {
      for (Node n = first, n2 = node.first;
           n != null;
           n = n.next, n2 = n2.next) {
        if (!n.isEquivalentTo(n2, compareType, recurse, jsDoc, sideEffect)) {
          return false;
        }
      }
    }

    return true;
  }

  /**
   * This function takes a set of GETPROP nodes and produces a string that is each property
   * separated by dots. If the node ultimately under the left sub-tree is not a simple name, this is
   * not a valid qualified name.
   *
   * @return a null if this is not a qualified name, or a dot-separated string of the name and
   *     properties.
   */
  @Nullable
  public final String getQualifiedName() {
    switch (token) {
      case NAME:
        String name = getString();
        return name.isEmpty() ? null : name;
      case GETPROP:
        StringBuilder builder = getQualifiedNameForGetProp(0);
        return builder != null ? builder.toString() : null;
      case THIS:
        return "this";
      case SUPER:
        return "super";
      default:
        return null;
    }
  }

  /**
   * Helper method for {@link #getQualifiedName} to handle GETPROP nodes.
   *
   * @param reserve The number of characters of space to reserve in the StringBuilder
   * @return {@code null} if this is not a qualified name or a StringBuilder if it is a complex
   *     qualified name.
   */
  @Nullable
  private StringBuilder getQualifiedNameForGetProp(int reserve) {
    String propName = getLastChild().getString();
    reserve += 1 + propName.length();  // +1 for the '.'
    StringBuilder builder;
    if (first.isGetProp()) {
      builder = first.getQualifiedNameForGetProp(reserve);
      if (builder == null) {
        return null;
      }
    } else {
      String left = first.getQualifiedName();
      if (left == null) {
        return null;
      }
      builder = new StringBuilder(left.length() + reserve);
      builder.append(left);
    }
    builder.append('.').append(propName);
    return builder;
  }

  /**
   * This function takes a set of GETPROP nodes and produces a string that is each property
   * separated by dots. If the node ultimately under the left sub-tree is not a simple name, this is
   * not a valid qualified name. This method returns the original name of each segment rather than
   * the renamed version.
   *
   * @return a null if this is not a qualified name, or a dot-separated string of the name and
   *     properties.
   */
  @Nullable
  public final String getOriginalQualifiedName() {
    if (token == Token.NAME || getBooleanProp(IS_MODULE_NAME)) {
      String name = getOriginalName();
      if (name == null) {
        name = getString();
      }
      return name.isEmpty() ? null : name;
    } else if (token == Token.GETPROP) {
      String left = getFirstChild().getOriginalQualifiedName();
      if (left == null) {
        return null;
      }
      String right = getLastChild().getOriginalName();
      if (right == null) {
        right = getLastChild().getString();
      }

      return left + "." + right;
    } else if (token == Token.THIS) {
      return "this";
    } else if (token == Token.SUPER) {
      return "super";
    } else {
      return null;
    }
  }


  /**
   * Returns whether a node corresponds to a simple or a qualified name, such as
   * <code>x</code> or <code>a.b.c</code> or <code>this.a</code>.
   */
  public final boolean isQualifiedName() {
    switch (this.getToken()) {
      case NAME:
        return !getString().isEmpty();
      case THIS:
      case SUPER:
        return true;
      case GETPROP:
        return getFirstChild().isQualifiedName();
      default:
        return false;
    }
  }

  /**
   * Returns whether a node matches a simple or a qualified name, such as
   * <code>x</code> or <code>a.b.c</code> or <code>this.a</code>.
   */
  public final boolean matchesQualifiedName(String name) {
    return name != null && matchesQualifiedName(name, name.length());
  }

  /**
   * Returns whether a node matches a simple or a qualified name, such as
   * <code>x</code> or <code>a.b.c</code> or <code>this.a</code>.
   */
  private boolean matchesQualifiedName(String qname, int endIndex) {
    int start = qname.lastIndexOf('.', endIndex - 1) + 1;

    switch (this.getToken()) {
      case NAME:
      case MEMBER_FUNCTION_DEF:
        String name = getString();
        return start == 0 && !name.isEmpty() && name.length() == endIndex && qname.startsWith(name);
      case THIS:
        return start == 0 && 4 == endIndex && qname.startsWith("this");
      case SUPER:
        return start == 0 && 5 == endIndex && qname.startsWith("super");
      case GETPROP:
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
   * Returns whether a node matches a simple or a qualified name, such as <code>x</code> or <code>
   * a.b.c</code> or <code>this.a</code>.
   */
  @SuppressWarnings("ReferenceEquality")
  public final boolean matchesQualifiedName(Node n) {
    if (n == null || n.token != token) {
      return false;
    }
    switch (token) {
      case NAME:
        // ==, rather than equal as it is intern'd in setString
        return !getString().isEmpty() && getString() == n.getString();
      case THIS:
      case SUPER:
        return true;
      case GETPROP:
        // ==, rather than equal as it is intern'd in setString
        return getLastChild().getString() == n.getLastChild().getString()
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
  public final boolean isUnscopedQualifiedName() {
    switch (this.getToken()) {
      case NAME:
        return !getString().isEmpty();
      case GETPROP:
        return getFirstChild().isUnscopedQualifiedName();
      default:
        return false;
    }
  }

  public final boolean isValidAssignmentTarget() {
    switch (this.getToken()) {
      case NAME:
      case GETPROP:
      case GETELEM:
      case ARRAY_PATTERN:
      case OBJECT_PATTERN:
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
  public final Node detachFromParent() {
    return detach();
  }

  /**
   * Removes this node from its parent. Equivalent to:
   * node.getParent().removeChild();
   */
  public final Node detach() {
    checkNotNull(parent);
    parent.removeChild(this);
    return this;
  }

  /**
   * Removes the first child of Node. Equivalent to: node.removeChild(node.getFirstChild());
   *
   * @return The removed Node.
   */
  @Nullable
  public final Node removeFirstChild() {
    Node child = first;
    if (child != null) {
      removeChild(child);
    }
    return child;
  }

  /** @return A Node that is the head of the list of children. */
  @Nullable
  public final Node removeChildren() {
    Node children = first;
    for (Node child = first; child != null; child = child.next) {
      child.parent = null;
    }
    first = null;
    return children;
  }

  /**
   * Removes all children from this node and isolates the children from each
   * other.
   */
  public final void detachChildren() {
    for (Node child = first; child != null;) {
      Node nextChild = child.next;
      child.parent = null;
      child.next = null;
      child.previous = null;
      child = nextChild;
    }
    first = null;
  }

  public final Node removeChildAfter(Node prev) {
    Node target = prev.next;
    checkNotNull(target, "no next sibling.");
    removeChild(target);
    return target;
  }

  /** Remove the child after the given child, or the first child if given null. */
  public final Node removeFirstOrChildAfter(@Nullable Node prev) {
    checkArgument(prev == null || prev.parent == this, "invalid node.");
    Node target = prev == null ? first : prev.next;

    checkNotNull(target, "no next sibling.");
    removeChild(target);
    return target;
  }

  /**
   * @return A detached clone of the Node, specifically excluding its children.
   */
  @CheckReturnValue
  public final Node cloneNode() {
    return cloneNode(false);
  }

  /**
   * @return A detached clone of the Node, specifically excluding its children.
   */
  @CheckReturnValue
  protected Node cloneNode(boolean cloneTypeExprs) {
    return copyNodeFields(new Node(token), cloneTypeExprs);
  }

  final <T extends Node> T copyNodeFields(T dst, boolean cloneTypeExprs) {
    dst.setSourceEncodedPosition(this.sourcePosition);
    dst.setLength(this.getLength());
    dst.setTypeI(this.typei);
    dst.setPropListHead(this.propListHead);

    // TODO(johnlenz): Remove this once JSTypeExpression are immutable
    if (cloneTypeExprs) {
      JSDocInfo info = this.getJSDocInfo();
      if (info != null) {
        this.setJSDocInfo(info.clone(true));
      }
    }
    return dst;
  }

  /**
   * @return A detached clone of the Node and all its children.
   */
  @CheckReturnValue
  public final Node cloneTree() {
    return cloneTree(false);
  }

  @CheckReturnValue
  public final Node cloneTree(boolean cloneTypeExprs) {
    Node result = cloneNode(cloneTypeExprs);
    Node firstChild = null;
    Node lastChild = null;
    if (this.hasChildren()) {
      for (Node n2 = getFirstChild(); n2 != null; n2 = n2.next) {
        Node n2clone = n2.cloneTree(cloneTypeExprs);
        n2clone.parent = result;
        if (firstChild == null) {
          firstChild = n2clone;
          lastChild = firstChild;
        } else {
          lastChild.next = n2clone;
          n2clone.previous = lastChild;
          lastChild = n2clone;
        }
      }
      firstChild.previous = lastChild;
      lastChild.next = null;
      result.first = firstChild;
    }
    return result;
  }

  /**
   * Overwrite all the source information in this node with
   * that of {@code other}.
   */
  public final Node useSourceInfoFrom(Node other) {
    setStaticSourceFileFrom(other);
    putProp(ORIGINALNAME_PROP, other.getProp(ORIGINALNAME_PROP));
    sourcePosition = other.sourcePosition;
    length = other.length;
    return this;
  }

  public final Node srcref(Node other) {
    return useSourceInfoFrom(other);
  }

  /**
   * Overwrite all the source information in this node and its subtree with
   * that of {@code other}.
   */
  public final Node useSourceInfoFromForTree(Node other) {
    useSourceInfoFrom(other);
    for (Node child = first; child != null; child = child.next) {
      child.useSourceInfoFromForTree(other);
    }

    return this;
  }

  public final Node srcrefTree(Node other) {
    return useSourceInfoFromForTree(other);
  }

  /**
   * Overwrite all the source information in this node with
   * that of {@code other} iff the source info is missing.
   */
  public final Node useSourceInfoIfMissingFrom(Node other) {
    if (getStaticSourceFile() == null) {
      setStaticSourceFileFrom(other);
      sourcePosition = other.sourcePosition;
      length = other.length;
    }

    if (getProp(ORIGINALNAME_PROP) == null) {
      putProp(ORIGINALNAME_PROP, other.getProp(ORIGINALNAME_PROP));
    }

    return this;
  }

  /**
   * Overwrite all the source information in this node and its subtree with
   * that of {@code other} iff the source info is missing.
   */
  public final Node useSourceInfoIfMissingFromForTree(Node other) {
    useSourceInfoIfMissingFrom(other);
    for (Node child = first; child != null; child = child.next) {
      child.useSourceInfoIfMissingFromForTree(other);
    }

    return this;
  }

  //==========================================================================
  // Custom annotations

  /**
   * Returns the compiled inferred type on this node. Not to be confused with {@link
   * #getDeclaredTypeExpression()} which returns the syntactically specified type.
   */
  @Nullable
  public JSType getJSType() {  // TODO(johnlenz): make this final
    return typei instanceof JSType ? (JSType) typei : null;
  }

  public final void setJSType(@Nullable JSType jsType) {
    this.typei = jsType;
  }

  @Nullable
  public final TypeI getTypeI() {
    return typei;
  }

  public final void setTypeI(@Nullable TypeI type) {
    this.typei = type;
  }

  /**
   * Gets the OTI {@link JSType} associated with this node if any, and null otherwise.
   *
   * <p>NTI and OTI don't annotate the exact same AST nodes with types. (For example, OTI doesn't
   * annotate dead code.) When OTI runs after NTI, the checks that use type information must only
   * see the old types. They can call this method to avoid getting a new type for an AST node where
   * OTI did not add a type. Calls to this method are intended to be temporary. As we migrate passes
   * to support NTI natively, we will be replacing calls to this method with calls to getTypeI.
   */
  @Nullable
  public final TypeI getTypeIIfOld() {
    return typei instanceof JSType ? typei : null;
  }

  /**
   * Get the {@link JSDocInfo} attached to this node.
   *
   * @return the information or {@code null} if no JSDoc is attached to this node
   */
  @Nullable
  public final JSDocInfo getJSDocInfo() {
    return (JSDocInfo) getProp(JSDOC_INFO_PROP);
  }

  /**
   * Sets the {@link JSDocInfo} attached to this node.
   */
  public final Node setJSDocInfo(JSDocInfo info) {
    putProp(JSDOC_INFO_PROP, info);
    return this;
  }

  /** This node was last changed at {@code time} */
  public final void setChangeTime(int time) {
    putIntProp(CHANGE_TIME, time);
  }

  /** Returns the time of the last change for this node */
  public final int getChangeTime() {
    return getIntProp(CHANGE_TIME);
  }

  public final void setDeleted(boolean deleted) {
    putBooleanProp(DELETED, deleted);
  }

  public final boolean isDeleted() {
    return getBooleanProp(DELETED);
  }

  public final void setUnusedParameter(boolean unused) {
    putBooleanProp(IS_UNUSED_PARAMETER, unused);
  }

  /**
   * @return Whether a parameter was function to be unused. Set by RemoveUnusedVars
   */
  public final boolean isUnusedParameter() {
    return getBooleanProp(IS_UNUSED_PARAMETER);
  }

  /**
   * Sets whether this node is a variable length argument node. This
   * method is meaningful only on {@link Token#NAME} nodes
   * used to define a {@link Token#FUNCTION}'s argument list.
   */
  public final void setVarArgs(boolean varArgs) {
    putBooleanProp(VAR_ARGS_NAME, varArgs);
  }

  /**
   * Returns whether this node is a variable length argument node. This
   * method's return value is meaningful only on {@link Token#NAME} nodes
   * used to define a {@link Token#FUNCTION}'s argument list.
   */
  public final boolean isVarArgs() {
    return getBooleanProp(VAR_ARGS_NAME);
  }

  /**
   * Sets whether this node is an optional argument node. This
   * method is meaningful only on {@link Token#NAME} nodes
   * used to define a {@link Token#FUNCTION}'s argument list.
   */
  public final void setOptionalArg(boolean optionalArg) {
    putBooleanProp(OPT_ARG_NAME, optionalArg);
  }

  /**
   * Returns whether this node is an optional argument node. This
   * method's return value is meaningful only on {@link Token#NAME} nodes
   * used to define a {@link Token#FUNCTION}'s argument list.
   */
  public final boolean isOptionalArg() {
    return getBooleanProp(OPT_ARG_NAME);
  }

  /**
   * Returns whether this node is an optional node in the ES6 Typed syntax.
   */
  public final boolean isOptionalEs6Typed() {
    return getBooleanProp(OPT_ES6_TYPED);
  }

  /**
   * Sets whether this is a synthetic block that should not be considered
   * a real source block.
   */
  public final void setIsSyntheticBlock(boolean val) {
    checkState(token == Token.BLOCK);
    putBooleanProp(SYNTHETIC_BLOCK_PROP, val);
  }

  /**
   * Returns whether this is a synthetic block that should not be considered
   * a real source block.
   */
  public final boolean isSyntheticBlock() {
    return getBooleanProp(SYNTHETIC_BLOCK_PROP);
  }

  /**
   * Sets the ES5 directives on this node.
   */
  public final void setDirectives(Set<String> val) {
    putProp(DIRECTIVES, val);
  }

  /** Returns the set of ES5 directives for this node. */
  @Nullable
  public final Set<String> getDirectives() {
    return (Set<String>) getProp(DIRECTIVES);
  }

  /**
   * Sets whether this is an added block that should not be considered
   * a real source block. Eg: In "if (true) x;", the "x;" is put under an added
   * block in the AST.
   */
  public final void setIsAddedBlock(boolean val) {
    putBooleanProp(ADDED_BLOCK, val);
  }

  /**
   * Returns whether this is an added block that should not be considered
   * a real source block.
   */
  public final boolean isAddedBlock() {
    return getBooleanProp(ADDED_BLOCK);
  }

  /**
   * Sets whether this node is a static member node. This
   * method is meaningful only on {@link Token#GETTER_DEF},
   * {@link Token#SETTER_DEF} or {@link Token#MEMBER_FUNCTION_DEF} nodes contained
   * within {@link Token#CLASS}.
   */
  public final void setStaticMember(boolean isStatic) {
    putBooleanProp(STATIC_MEMBER, isStatic);
  }

  /**
   * Returns whether this node is a static member node. This
   * method is meaningful only on {@link Token#GETTER_DEF},
   * {@link Token#SETTER_DEF} or {@link Token#MEMBER_FUNCTION_DEF} nodes contained
   * within {@link Token#CLASS}.
   */
  public final boolean isStaticMember() {
    return getBooleanProp(STATIC_MEMBER);
  }

  /**
   * Sets whether this node is a generator node. This
   * method is meaningful only on {@link Token#FUNCTION} or
   * {@link Token#MEMBER_FUNCTION_DEF} nodes.
   */
  public final void setIsGeneratorFunction(boolean isGenerator) {
    putBooleanProp(GENERATOR_FN, isGenerator);
  }

  /**
   * Returns whether this node is a generator function node.
   */
  public final boolean isGeneratorFunction() {
    return getBooleanProp(GENERATOR_FN);
  }

  /**
   * Sets whether this node is a marker used in the translation of generators.
   */
  public final void setGeneratorMarker(boolean isGeneratorMarker) {
    putBooleanProp(GENERATOR_MARKER, isGeneratorMarker);
  }

  /**
   * Returns whether this node is a marker used in the translation of generators.
   */
  public final boolean isGeneratorMarker() {
    return getBooleanProp(GENERATOR_MARKER);
  }

  /**
   * @see #isGeneratorSafe()
   */
  public final void setGeneratorSafe(boolean isGeneratorSafe) {
    putBooleanProp(GENERATOR_SAFE, isGeneratorSafe);
  }

  /**
   * Used when translating ES6 generators. If this returns true, this Node
   * was generated by the compiler, and it is safe to copy this node to the
   * transpiled output with no further changes.
   */
  public final boolean isGeneratorSafe() {
    return getBooleanProp(GENERATOR_SAFE);
  }

  /**
   * Sets whether this node is a arrow function node. This
   * method is meaningful only on {@link Token#FUNCTION}
   */
  public final void setIsArrowFunction(boolean isArrow) {
    checkState(isFunction());
    putBooleanProp(ARROW_FN, isArrow);
  }

  /**
   * Returns whether this node is a arrow function node.
   */
  public final boolean isArrowFunction() {
    return isFunction() && getBooleanProp(ARROW_FN);
  }

  /**
   * Sets whether this node is an async function node. This
   * method is meaningful only on {@link Token#FUNCTION}
   */
  public void setIsAsyncFunction(boolean isAsync) {
    checkState(isFunction());
    putBooleanProp(ASYNC_FN, isAsync);
  }

  /**
   * Returns whether this is an async function node.
   */
  public final boolean isAsyncFunction() {
    return isFunction() && getBooleanProp(ASYNC_FN);
  }

  /**
   * Sets whether this node is a generator node. This
   * method is meaningful only on {@link Token#FUNCTION} or
   * {@link Token#MEMBER_FUNCTION_DEF} nodes.
   */
  public final void setYieldAll(boolean isGenerator) {
    putBooleanProp(YIELD_ALL, isGenerator);
  }

  /**
   * Returns whether this node is a generator node. This
   * method is meaningful only on {@link Token#FUNCTION} or
   * {@link Token#MEMBER_FUNCTION_DEF} nodes.
   */
  public final boolean isYieldAll() {
    return getBooleanProp(YIELD_ALL);
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
  public final void setSideEffectFlags(int flags) {
    checkArgument(
        this.isCall() || this.isNew() || this.isTaggedTemplateLit(),
        "setIsNoSideEffectsCall only supports call-like nodes, got %s",
        this);

    putIntProp(SIDE_EFFECT_FLAGS, flags);
  }

  public final void setSideEffectFlags(SideEffectFlags flags) {
    setSideEffectFlags(flags.valueOf());
  }

  /**
   * Returns the side effects flags for this node.
   */
  public final int getSideEffectFlags() {
    return getIntProp(SIDE_EFFECT_FLAGS);
  }

  /**
   * A helper class for getting and setting the side-effect flags.
   * @author johnlenz@google.com (John Lenz)
   */
  public static final class SideEffectFlags {
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

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder("Side effects: ");
      if ((value & Node.FLAG_THIS_UNMODIFIED) == 0) {
        builder.append("this ");
      }

      if ((value & Node.FLAG_GLOBAL_STATE_UNMODIFIED) == 0) {
        builder.append("global ");
      }

      if ((value & Node.FLAG_NO_THROWS) == 0) {
        builder.append("throw ");
      }

      if ((value & Node.FLAG_ARGUMENTS_UNMODIFIED) == 0) {
        builder.append("args ");
      }

      if ((value & Node.FLAG_LOCAL_RESULTS) == 0) {
        builder.append("return ");
      }
      return builder.toString();
    }
  }

  /**
   * @return Whether the only side-effect is "modifies this"
   */
  public final boolean isOnlyModifiesThisCall() {
    return areBitFlagsSet(
        getSideEffectFlags() & Node.NO_SIDE_EFFECTS,
        Node.FLAG_GLOBAL_STATE_UNMODIFIED
            | Node.FLAG_ARGUMENTS_UNMODIFIED
            | Node.FLAG_NO_THROWS);
  }

  /**
   * @return Whether the only side-effect is "modifies arguments"
   */
  public final boolean isOnlyModifiesArgumentsCall() {
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
  public final boolean isNoSideEffectsCall() {
    return areBitFlagsSet(getSideEffectFlags(), NO_SIDE_EFFECTS);
  }

  /**
   * Returns true if this node is a function or constructor call that
   * returns a primitive or a local object (an object that has no other
   * references).
   */
  public final boolean isLocalResultCall() {
    return areBitFlagsSet(getSideEffectFlags(), FLAG_LOCAL_RESULTS);
  }

  /** Returns true if this is a new/call that may mutate its arguments. */
  public final boolean mayMutateArguments() {
    return !areBitFlagsSet(getSideEffectFlags(), FLAG_ARGUMENTS_UNMODIFIED);
  }

  /** Returns true if this is a new/call that may mutate global state or throw. */
  public final boolean mayMutateGlobalStateOrThrow() {
    return !areBitFlagsSet(getSideEffectFlags(),
        FLAG_GLOBAL_STATE_UNMODIFIED | FLAG_NO_THROWS);
  }

  /**
   * returns true if all the flags are set in value.
   */
  private static boolean areBitFlagsSet(int value, int flags) {
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
    throw new IllegalStateException(this + " is not a StringNode");
  }

  static final class NodeMismatch {
    final Node nodeExpected;
    final Node nodeActual;

    NodeMismatch(Node nodeExpected, Node nodeActual) {
      this.nodeExpected = nodeExpected;
      this.nodeActual = nodeActual;
    }

    @Override
    public boolean equals(@Nullable Object object) {
      if (object instanceof NodeMismatch) {
        NodeMismatch that = (NodeMismatch) object;
        return that.nodeExpected.equals(this.nodeExpected)
            && that.nodeActual.equals(this.nodeActual);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(nodeExpected, nodeActual);
    }
  }


  /*** AST type check methods ***/

  public final boolean isAdd() {
    return this.token == Token.ADD;
  }

  public final boolean isSub() {
    return this.token == Token.SUB;
  }

  public final boolean isAnd() {
    return this.token == Token.AND;
  }

  public final boolean isArrayLit() {
    return this.token == Token.ARRAYLIT;
  }

  public final boolean isArrayPattern() {
    return this.token == Token.ARRAY_PATTERN;
  }

  public final boolean isAssign() {
    return this.token == Token.ASSIGN;
  }

  public final boolean isAssignAdd() {
    return this.token == Token.ASSIGN_ADD;
  }

  public final boolean isNormalBlock() {
    return this.token == Token.BLOCK;
  }

  public final boolean isRoot() {
    return this.token == Token.ROOT;
  }

  public final boolean isAwait() {
    return this.token == Token.AWAIT;
  }

  public final boolean isBreak() {
    return this.token == Token.BREAK;
  }

  public final boolean isCall() {
    return this.token == Token.CALL;
  }

  public final boolean isCase() {
    return this.token == Token.CASE;
  }

  public final boolean isCast() {
    return this.token == Token.CAST;
  }

  public final boolean isCatch() {
    return this.token == Token.CATCH;
  }

  public final boolean isClass() {
    return this.token == Token.CLASS;
  }

  public final boolean isClassMembers() {
    return this.token == Token.CLASS_MEMBERS;
  }

  public final boolean isComma() {
    return this.token == Token.COMMA;
  }

  public final boolean isComputedProp() {
    return this.token == Token.COMPUTED_PROP;
  }

  public final boolean isContinue() {
    return this.token == Token.CONTINUE;
  }

  public final boolean isConst() {
    return this.token == Token.CONST;
  }

  public final boolean isDebugger() {
    return this.token == Token.DEBUGGER;
  }

  public final boolean isDec() {
    return this.token == Token.DEC;
  }

  public final boolean isDefaultCase() {
    return this.token == Token.DEFAULT_CASE;
  }

  public final boolean isDefaultValue() {
    return this.token == Token.DEFAULT_VALUE;
  }

  public final boolean isDelProp() {
    return this.token == Token.DELPROP;
  }

  public final boolean isDestructuringLhs() {
    return this.token == Token.DESTRUCTURING_LHS;
  }

  public final boolean isDestructuringPattern() {
    return isObjectPattern() || isArrayPattern();
  }

  public final boolean isDo() {
    return this.token == Token.DO;
  }

  public final boolean isEmpty() {
    return this.token == Token.EMPTY;
  }

  public final boolean isExport() {
    return this.token == Token.EXPORT;
  }

  public final boolean isExportSpec() {
    return this.token == Token.EXPORT_SPEC;
  }

  public final boolean isExprResult() {
    return this.token == Token.EXPR_RESULT;
  }

  public final boolean isFalse() {
    return this.token == Token.FALSE;
  }

  public final boolean isVanillaFor() {
    return this.token == Token.FOR;
  }

  public final boolean isForIn() {
    return this.token == Token.FOR_IN;
  }

  public final boolean isForOf() {
    return this.token == Token.FOR_OF;
  }

  public final boolean isFunction() {
    return this.token == Token.FUNCTION;
  }

  public final boolean isGetterDef() {
    return this.token == Token.GETTER_DEF;
  }

  public final boolean isGetElem() {
    return this.token == Token.GETELEM;
  }

  public final boolean isGetProp() {
    return this.token == Token.GETPROP;
  }

  public final boolean isHook() {
    return this.token == Token.HOOK;
  }

  public final boolean isIf() {
    return this.token == Token.IF;
  }

  public final boolean isImport() {
    return this.token == Token.IMPORT;
  }

  public final boolean isImportStar() {
    return this.token == Token.IMPORT_STAR;
  }

  public final boolean isImportSpec() {
    return this.token == Token.IMPORT_SPEC;
  }

  public final boolean isImportSpecs() {
    return this.token == Token.IMPORT_SPECS;
  }

  public final boolean isIn() {
    return this.token == Token.IN;
  }

  public final boolean isInc() {
    return this.token == Token.INC;
  }

  public final boolean isInstanceOf() {
    return this.token == Token.INSTANCEOF;
  }

  public final boolean isInterfaceMembers() {
    return this.token == Token.INTERFACE_MEMBERS;
  }

  public final boolean isRecordType() {
    return this.token == Token.RECORD_TYPE;
  }

  public final boolean isCallSignature() {
    return this.token == Token.CALL_SIGNATURE;
  }

  public final boolean isIndexSignature() {
    return this.token == Token.INDEX_SIGNATURE;
  }

  public final boolean isLabel() {
    return this.token == Token.LABEL;
  }

  public final boolean isLabelName() {
    return this.token == Token.LABEL_NAME;
  }

  public final boolean isLet() {
    return this.token == Token.LET;
  }

  public final boolean isMemberFunctionDef() {
    return this.token == Token.MEMBER_FUNCTION_DEF;
  }

  public final boolean isMemberVariableDef() {
    return this.token == Token.MEMBER_VARIABLE_DEF;
  }

  public final boolean isModuleBody() {
    return this.token == Token.MODULE_BODY;
  }

  public final boolean isName() {
    return this.token == Token.NAME;
  }

  public final boolean isNE() {
    return this.token == Token.NE;
  }

  public final boolean isNew() {
    return this.token == Token.NEW;
  }

  public final boolean isNot() {
    return this.token == Token.NOT;
  }

  public final boolean isNull() {
    return this.token == Token.NULL;
  }

  public final boolean isNumber() {
    return this.token == Token.NUMBER;
  }

  public final boolean isObjectLit() {
    return this.token == Token.OBJECTLIT;
  }

  public final boolean isObjectPattern() {
    return this.token == Token.OBJECT_PATTERN;
  }

  public final boolean isOr() {
    return this.token == Token.OR;
  }

  public final boolean isParamList() {
    return this.token == Token.PARAM_LIST;
  }

  public final boolean isRegExp() {
    return this.token == Token.REGEXP;
  }

  public final boolean isRest() {
    return this.token == Token.REST;
  }

  public final boolean isReturn() {
    return this.token == Token.RETURN;
  }

  public final boolean isScript() {
    return this.token == Token.SCRIPT;
  }

  public final boolean isSetterDef() {
    return this.token == Token.SETTER_DEF;
  }

  public final boolean isSpread() {
    return this.token == Token.SPREAD;
  }

  public final boolean isString() {
    return this.token == Token.STRING;
  }

  public final boolean isStringKey() {
    return this.token == Token.STRING_KEY;
  }

  public final boolean isSuper() {
    return this.token == Token.SUPER;
  }

  public final boolean isSwitch() {
    return this.token == Token.SWITCH;
  }

  public final boolean isTaggedTemplateLit() {
    return this.token == Token.TAGGED_TEMPLATELIT;
  }

  public final boolean isTemplateLit() {
    return this.token == Token.TEMPLATELIT;
  }

  public final boolean isTemplateLitSub() {
    return this.token == Token.TEMPLATELIT_SUB;
  }

  public final boolean isThis() {
    return this.token == Token.THIS;
  }

  public final boolean isThrow() {
    return this.token == Token.THROW;
  }

  public final boolean isTrue() {
    return this.token == Token.TRUE;
  }

  public final boolean isTry() {
    return this.token == Token.TRY;
  }

  public final boolean isTypeOf() {
    return this.token == Token.TYPEOF;
  }

  public final boolean isVar() {
    return this.token == Token.VAR;
  }

  public final boolean isVoid() {
    return this.token == Token.VOID;
  }

  public final boolean isWhile() {
    return this.token == Token.WHILE;
  }

  public final boolean isWith() {
    return this.token == Token.WITH;
  }

  public final boolean isYield() {
    return this.token == Token.YIELD;
  }

  @GwtIncompatible("ObjectOutputStream")
  private void writeObject(java.io.ObjectOutputStream out) throws Exception {
    // Do not call out.defaultWriteObject() as all the fields and transient and this class does not
    // have a superclass.

    checkState(Token.values().length < Byte.MAX_VALUE - Byte.MIN_VALUE);
    out.writeByte(token.ordinal());

    writeEncodedInt(out, sourcePosition);
    writeEncodedInt(out, length);

    // Serialize the embedded children linked list here to limit the depth of recursion (and avoid
    // serializing redundant information like the previous reference)
    Node currentChild = first;
    while (currentChild != null) {
      out.writeObject(currentChild);
      currentChild = currentChild.next;
    }
    // Null marks the end of the children.
    out.writeObject(null);
    out.writeObject(typei);
    out.writeObject(propListHead);
  }

  @GwtIncompatible("ObjectInputStream")
  private void readObject(java.io.ObjectInputStream in) throws Exception {
    // Do not call in.defaultReadObject() as all the fields and transient and this class does not
    // have a superclass.

    token = Token.values()[in.readUnsignedByte()];
    sourcePosition = readEncodedInt(in);
    length = readEncodedInt(in);

    // Deserialize the children list restoring the value of the previous reference.
    first = (Node) in.readObject();
    if (first != null) {
      checkState(first.parent == null);
      first.parent = this;

      Node currentChild;
      Node lastChild = first;
      while ((currentChild = (Node) in.readObject()) != null) {
        checkState(currentChild.parent == null);
        currentChild.parent = this;
        // previous is never null, either it points to the previous sibling or if it is the first
        // sibling it points to the last one.
        checkState(currentChild.previous == null);
        currentChild.previous = lastChild;
        checkState(lastChild.next == null);
        lastChild.next = currentChild;
        lastChild = currentChild;
      }
      // Close the reverse circular list.
      checkState(first.previous == null);
      first.previous = lastChild;
    }
    typei = (TypeI) in.readObject();
    propListHead = (PropListItem) in.readObject();
  }

  /**
   * Encode integers using variable length encoding.
   *
   * Encodes an integer as a sequence of 7-bit values with a continuation bit. For example the
   * number 3912 (0111 0100 1000) is encoded in two bytes as follows 0xC80E (1100 1000 0000 1110),
   * i.e. first byte will be the lower 7 bits with a continuation bit set and second byte will
   * consist of the upper 7 bits with the continuation bit unset.
   *
   * This encoding aims to reduce the serialized footprint for the most common values, reducing the
   * footprint for all positive values that are smaller than 2^21 (~2000000):
   *           0 -       127 are encoded in one byte
   *         128 -     16384 are encoded in two bytes
   *       16385 -   2097152 are encoded in three bytes
   *     2097153 - 268435456 are encoded in four bytes.
   *     values greater than 268435456 and negative values are encoded in 5 bytes.
   *
   * Most values for the length field will be encoded with one byte and most values for
   * sourcePosition will be encoded with 2 or 3 bytes. (Value -1, which is used to mark absence will
   * use 5 bytes, and could be accommodated in the present scheme by an offset of 1 if it leads to
   * size improvements).
   */
  @GwtIncompatible("ObjectOutput")
  private void writeEncodedInt(ObjectOutput out, int value) throws IOException {
    while (value > 0X7f || value < 0) {
      out.writeByte(((value & 0X7f) | 0x80));
      value >>>= 7;
    }
    out.writeByte(value);
  }

  @GwtIncompatible("ObjectInput")
  private int readEncodedInt(ObjectInput in) throws IOException {
    int value = 0;
    int shift = 0;
    byte current;

    while ((current = in.readByte()) < 0) {
      value |= (current & 0x7f) << shift;
      shift += 7;
    }
    value |= current << shift;
    return value;
  }
}
