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
import static com.google.javascript.jscomp.base.JSCompDoubles.isPositive;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.DoNotCall;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.serialization.NodeProperty;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import com.google.javascript.rhino.jstype.JSType;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.nullness.Nullable;

/**
 * This class implements the root of the intermediate representation.
 *
 */
public class Node {

  enum Prop {
    // Is this Node within parentheses
    IS_PARENTHESIZED,
    // Contains non-JSDoc comment
    NON_JSDOC_COMMENT,
    TRAILING_NON_JSDOC_COMMENT,
    // Contains a JSDocInfo object
    JSDOC_INFO,
    // Whether incrdecr is pre (false) or post (true)
    INCRDECR,
    // Set to indicate a quoted object lit key
    QUOTED,
    // A synthetic block. Used to make processing simpler, and does not represent a real block in
    // the source.
    SYNTHETIC,
    // Used to indicate BLOCK that is added
    ADDED_BLOCK,
    // Function or constructor call side effect flags.
    SIDE_EFFECT_FLAGS,
    // The variable or property is constant.
    // TODO(lukes): either document the differences or otherwise reconcile with CONSTANT_VAR_FLAGS
    IS_CONSTANT_NAME,
    // The variable creates a namespace.
    IS_NAMESPACE,
    // The presence of the "use strict" directive on this node.
    USE_STRICT,
    // ES5 distinguishes between direct and indirect calls to eval.
    DIRECT_EVAL,
    // A CALL without an explicit "this" value.
    FREE_CALL,
    // A StaticSourceFile indicating the file where this node lives.
    SOURCE_FILE,
    // The id of the input associated with this node.
    INPUT_ID,
    // For passes that work only on changed funs.
    CHANGE_TIME,
    // An object that's used for goog.object.reflect-style reflection.
    REFLECTED_OBJECT,
    // Set if class member definition is static
    STATIC_MEMBER,
    // Set if the node is a Generator function or member method.
    GENERATOR_FN,
    // Set if the node is an arrow function.
    ARROW_FN,
    // http://tc39.github.io/ecmascript-asyncawait/
    ASYNC_FN,
    // Set if a yield is a "yield all"
    YIELD_ALL,
    // Set if a export is a "default" export
    EXPORT_DEFAULT,
    // Set if an export is a "*"
    EXPORT_ALL_FROM,
    // A variable is inferred or declared as const meaning it is only ever assigned once at its
    // declaration site.
    // This is an int prop that holds a bitset of {@link ConstantVarFlags} values.
    CONSTANT_VAR_FLAGS,
    // Used by the ES6-to-ES3 translator.
    IS_GENERATOR_MARKER,
    // Used by the ES6-to-ES3 translator.
    IS_GENERATOR_SAFE,
    // A computed property that has the method syntax
    //   ( [prop]() {...} )
    // rather than the property definition syntax
    //   ( [prop]: value ).
    COMPUTED_PROP_METHOD,
    // A computed property in a getter, e.g. var obj = { get [prop]() {...} };
    COMPUTED_PROP_GETTER,
    // A computed property in a setter, e.g. var obj = 32;
    COMPUTED_PROP_SETTER,
    // A computed property that's a variable, e.g. [prop]: string;
    COMPUTED_PROP_VARIABLE,
    // Used to attach TypeDeclarationNode ASTs to Nodes which represent a typed NAME or FUNCTION.
    DECLARED_TYPE_EXPR,
    // The type of an expression before the cast. This will be present only if the expression is
    // casted.
    TYPE_BEFORE_CAST,
    // Indicates that this epxression was casted but we don't necessarily know to which type
    COLOR_FROM_CAST,
    // The node is an optional parameter or property in ES6 Typed syntax.
    OPT_ES6_TYPED,
    // Generic type list in ES6 typed syntax.
    GENERIC_TYPE,
    // "implements" clause in ES6 typed syntax.
    IMPLEMENTS,
    // This node is a TypeScript ConstructSignature
    CONSTRUCT_SIGNATURE,
    // TypeScript accessibility modifiers (public, protected, private)
    ACCESS_MODIFIER,
    // Indicates the node should not be indexed by analysis tools.
    NON_INDEXABLE,
    // Parse results stored on SCRIPT nodes to allow replaying parse warnings/errors when cloning
    // cached ASTs.
    PARSE_RESULTS,
    // Indicates that a SCRIPT node is a goog.module. Remains set after the goog.module is
    // desugared.
    GOOG_MODULE,
    // Attaches a FeatureSet to SCRIPT nodes.
    FEATURE_SET,
    // Indicates a TypeScript abstract method or class, for use in Migrants
    IS_TYPESCRIPT_ABSTRACT,
    // For passes that work only on deleted funs.
    DELETED,
    // Indicates that the node is an alias or a name from goog.require'd module or ES6
    // module. Aliases are desugared and inlined by compiler passes but we need to preserve them for
    // building index.
    MODULE_ALIAS,
    // Mark a parameter as unused. Used to defer work from RemovedUnusedVars to OptimizeParameters.
    IS_UNUSED_PARAMETER,
    // Mark a property as a module export so that collase properties can act on it.
    MODULE_EXPORT,
    // Indicates that a property {x:x} was originally parsed as {x}.
    IS_SHORTHAND_PROPERTY,
    // Indicates that a SCRIPT node is or was an ES module. Remains set after the module is
    // rewritten.
    ES6_MODULE,
    // Record the type associated with a @typedef to enable looking up typedef in the AST possible
    // without saving the type scope.
    TYPEDEF_TYPE,
    // Indicate that a OPTCHAIN_GETPROP, OPTCHAIN_GETELEM, or OPTCHAIN_CALL is the start of an
    // optional chain.
    START_OF_OPT_CHAIN,
    // Indicates a trailing comma in an array literal, object literal, parameter list, or argument
    // list
    TRAILING_COMMA,
    // Indicates that a variable declaration was synthesized to provide a declaration of some
    // name referenced in code but never defined, as most compiler passes expect that to be an
    // invariant.
    // Only present in the "synthetic externs file". Builds initialized using a
    // "TypedAST filesystem" will delete any such declarations present in a different compilation
    // shard
    SYNTHESIZED_UNFULFILLED_NAME_DECLARATION,
    // Marks a function for eager compile by wrapping with (). This has potential performance
    // benefits when focused on critical functions but needs to be sparingly applied, since too many
    // functions eager compiled will lead to performance regressions.
    MARK_FOR_PARENTHESIZE
  }

  // Avoid cloning "values" repeatedly in hot code, we save it off now.
  private static final Prop[] PROP_VALUES = Prop.values();

  /**
   * Get the NonJSDoc comment string attached to this node.
   *
   * @return the information or empty string if no nonJSDoc is attached to this node
   */
  public final String getNonJSDocCommentString() {
    if (getProp(Prop.NON_JSDOC_COMMENT) == null) {
      return "";
    }
    return ((NonJSDocComment) getProp(Prop.NON_JSDOC_COMMENT)).getCommentString();
  }

  public final NonJSDocComment getNonJSDocComment() {
    return (NonJSDocComment) getProp(Prop.NON_JSDOC_COMMENT);
  }

  public final String getTrailingNonJSDocCommentString() {
    if (getProp(Prop.TRAILING_NON_JSDOC_COMMENT) == null) {
      return "";
    }
    return ((NonJSDocComment) getProp(Prop.TRAILING_NON_JSDOC_COMMENT)).getCommentString();
  }

  public final NonJSDocComment getTrailingNonJSDocComment() {
    return (NonJSDocComment) getProp(Prop.TRAILING_NON_JSDOC_COMMENT);
  }

  /** Sets the NonJSDoc comment attached to this node. */
  public final Node setNonJSDocComment(NonJSDocComment comment) {
    putProp(Prop.NON_JSDOC_COMMENT, comment);
    return this;
  }

  public final Node setTrailingNonJSDocComment(NonJSDocComment comment) {
    putProp(Prop.TRAILING_NON_JSDOC_COMMENT, comment);
    return this;
  }

  /** Sets whether this node was inside original source-level parentheses. */
  public final void setIsParenthesized(boolean b) {
    checkState(IR.mayBeExpression(this));
    putBooleanProp(Prop.IS_PARENTHESIZED, b);
  }

  /** Check whether node was inside original source-level parentheses. */
  public final boolean getIsParenthesized() {
    return getBooleanProp(Prop.IS_PARENTHESIZED);
  }

  /** Sets whether this node is should be parenthesized in output. */
  public final void setMarkForParenthesize(boolean value) {
    checkState(IR.mayBeExpression(this));
    putBooleanProp(Prop.MARK_FOR_PARENTHESIZE, value);
  }

  /** Check whether node should be parenthesized in output. */
  public final boolean getMarkForParenthesize() {
    return getBooleanProp(Prop.MARK_FOR_PARENTHESIZE);
  }

  // TODO(sdh): Get rid of these by using accessor methods instead.
  // These export instances of a private type, which is awkward but a side effect is that it
  // prevents anyone from introducing problemmatic uses of the general-purpose accessors.
  public static final Prop INCRDECR_PROP = Prop.INCRDECR;
  public static final Prop QUOTED_PROP = Prop.QUOTED;
  public static final Prop IS_CONSTANT_NAME = Prop.IS_CONSTANT_NAME;
  public static final Prop IS_NAMESPACE = Prop.IS_NAMESPACE;
  public static final Prop DIRECT_EVAL = Prop.DIRECT_EVAL;
  public static final Prop FREE_CALL = Prop.FREE_CALL;
  public static final Prop REFLECTED_OBJECT = Prop.REFLECTED_OBJECT;
  public static final Prop STATIC_MEMBER = Prop.STATIC_MEMBER;
  public static final Prop GENERATOR_FN = Prop.GENERATOR_FN;
  public static final Prop YIELD_ALL = Prop.YIELD_ALL;
  public static final Prop EXPORT_DEFAULT = Prop.EXPORT_DEFAULT;
  public static final Prop EXPORT_ALL_FROM = Prop.EXPORT_ALL_FROM;
  public static final Prop COMPUTED_PROP_METHOD = Prop.COMPUTED_PROP_METHOD;
  public static final Prop COMPUTED_PROP_GETTER = Prop.COMPUTED_PROP_GETTER;
  public static final Prop COMPUTED_PROP_SETTER = Prop.COMPUTED_PROP_SETTER;
  public static final Prop COMPUTED_PROP_VARIABLE = Prop.COMPUTED_PROP_VARIABLE;
  public static final Prop OPT_ES6_TYPED = Prop.OPT_ES6_TYPED;
  public static final Prop GENERIC_TYPE_LIST = Prop.GENERIC_TYPE;
  public static final Prop IMPLEMENTS = Prop.IMPLEMENTS;
  public static final Prop CONSTRUCT_SIGNATURE = Prop.CONSTRUCT_SIGNATURE;
  public static final Prop ACCESS_MODIFIER = Prop.ACCESS_MODIFIER;
  public static final Prop PARSE_RESULTS = Prop.PARSE_RESULTS;
  public static final Prop GOOG_MODULE = Prop.GOOG_MODULE;
  public static final Prop FEATURE_SET = Prop.FEATURE_SET;
  public static final Prop IS_TYPESCRIPT_ABSTRACT = Prop.IS_TYPESCRIPT_ABSTRACT;
  public static final Prop MODULE_ALIAS = Prop.MODULE_ALIAS;
  public static final Prop MODULE_EXPORT = Prop.MODULE_EXPORT;
  public static final Prop IS_SHORTHAND_PROPERTY = Prop.IS_SHORTHAND_PROPERTY;
  public static final Prop ES6_MODULE = Prop.ES6_MODULE;

  private static final class NumberNode extends Node {

    private static final long serialVersionUID = 1L;

    private double number;

    NumberNode(double number) {
      super(Token.NUMBER);
      this.setDouble(number);
    }

    @Override
    public boolean isEquivalentTo(
        Node node, boolean compareType, boolean recur, boolean jsDoc, boolean sideEffect) {
      return super.isEquivalentTo(node, compareType, recur, jsDoc, sideEffect)
          && (this.number == ((NumberNode) node).number); // -0.0 and NaN are forbidden.
    }

    @Override
    NumberNode cloneNode(boolean cloneTypeExprs) {
      NumberNode clone = new NumberNode(number);
      copyBaseNodeFields(this, clone, cloneTypeExprs);
      return clone;
    }
  }

  private static final class BigIntNode extends Node {
    private static final long serialVersionUID = 1L;

    private BigInteger bigint;

    BigIntNode(BigInteger bigint) {
      super(Token.BIGINT);
      setBigInt(bigint);
    }

    @Override
    public boolean isEquivalentTo(
        Node node, boolean compareType, boolean recur, boolean jsDoc, boolean sideEffect) {
      return super.isEquivalentTo(node, compareType, recur, jsDoc, sideEffect)
          && getBigInt().equals(node.getBigInt());
    }

    @Override
    BigIntNode cloneNode(boolean cloneTypeExprs) {
      BigIntNode clone = new BigIntNode(bigint);
      copyBaseNodeFields(this, clone, cloneTypeExprs);
      return clone;
    }
  }

  private static final class StringNode extends Node {

    private static final long serialVersionUID = 1L;

    private String str;

    // Only for cloneNode
    private StringNode(Token token) {
      super(token);
    }

    StringNode(Token token, String str) {
      super(token);
      setString(str);
    }

    @Override
    public boolean isEquivalentTo(
        Node node, boolean compareType, boolean recur, boolean jsDoc, boolean sideEffect) {
      return super.isEquivalentTo(node, compareType, recur, jsDoc, sideEffect)
          && RhinoStringPool.uncheckedEquals(this.str, ((StringNode) node).str);
    }

    @Override
    StringNode cloneNode(boolean cloneTypeExprs) {
      StringNode clone = new StringNode(this.getToken());
      copyBaseNodeFields(this, clone, cloneTypeExprs);
      clone.str = this.str;
      return clone;
    }
  }

  private static final class TemplateLiteralSubstringNode extends Node {

    /**
     * The cooked version of the template literal substring.
     *
     * <p>Null iff the raw string contains an uncookable escape sequence.
     * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Template_literals#es2018_revision_of_illegal_escape_sequences
     */
    private final @Nullable String cooked;

    /** The raw version of the template literal substring, is not null */
    private final String raw;

    TemplateLiteralSubstringNode(@Nullable String cooked, String raw) {
      super(Token.TEMPLATELIT_STRING);
      // RhinoStringPool is null-hostile.
      this.cooked = (cooked == null) ? null : RhinoStringPool.addOrGet(cooked);
      this.raw = RhinoStringPool.addOrGet(raw);
    }

    @Override
    public boolean isEquivalentTo(
        Node node, boolean compareType, boolean recur, boolean jsDoc, boolean sideEffect) {
      if (!super.isEquivalentTo(node, compareType, recur, jsDoc, sideEffect)) {
        return false;
      }

      TemplateLiteralSubstringNode castNode = (TemplateLiteralSubstringNode) node;
      return RhinoStringPool.uncheckedEquals(this.raw, castNode.raw)
          && RhinoStringPool.uncheckedEquals(this.cooked, castNode.cooked);
    }

    @Override
    TemplateLiteralSubstringNode cloneNode(boolean cloneTypeExprs) {
      TemplateLiteralSubstringNode clone = new TemplateLiteralSubstringNode(this.cooked, this.raw);
      copyBaseNodeFields(this, clone, cloneTypeExprs);
      return clone;
    }
  }

  private abstract static class PropListItem {
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
      this.objectValue = checkNotNull(objectValue);
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
      checkState(this.intValue != 0);
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

  public Node(Token token) {
    this.token = token;
  }

  public Node(Token token, Node child) {
    this(token);
    this.first = child;

    child.checkDetached();
    // child.next remains null;
    child.previous = child;
    child.parent = this;
  }

  public Node(Token token, Node left, Node right) {
    this(token);
    this.first = left;

    left.checkDetached();
    left.next = right;
    left.previous = right;
    left.parent = this;

    right.checkDetached();
    // right.next remains null;
    right.previous = left;
    right.parent = this;
  }

  public Node(Token token, Node left, Node mid, Node right) {
    this(token);
    this.first = left;

    left.checkDetached();
    left.next = mid;
    left.previous = right;
    left.parent = this;

    mid.checkDetached();
    mid.next = right;
    mid.previous = left;
    mid.parent = this;

    right.checkDetached();
    // right.next remains null;
    right.previous = mid;
    right.parent = this;
  }

  public static Node newNumber(double number) {
    return new NumberNode(number);
  }

  public static Node newBigInt(BigInteger bigint) {
    return new BigIntNode(bigint);
  }

  public static Node newString(String str) {
    return new StringNode(Token.STRINGLIT, str);
  }

  public static Node newString(Token token, String str) {
    return new StringNode(token, str);
  }

  public static Node newTemplateLitString(String cooked, String raw) {
    return new TemplateLiteralSubstringNode(cooked, raw);
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

  public final @Nullable Node getFirstChild() {
    return first;
  }

  /**
   * Get the first child of the first child. This method assumes that the first child exists.
   *
   * @return The first child of the first child.
   */
  public final @Nullable Node getFirstFirstChild() {
    return first.first;
  }

  public final @Nullable Node getSecondChild() {
    return first.next;
  }

  public final @Nullable Node getLastChild() {
    return first != null ? first.previous : null;
  }

  public final @Nullable Node getNext() {
    return next;
  }

  public final @Nullable Node getPrevious() {
    return this == parent.first ? null : previous;
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
        child,
        child.parent,
        this);
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

  /**
   * Add all children to the front of this node.
   *
   * @param children first of a list of sibling nodes who have no parent. NOTE: Usually you would
   *     get this argument from a removeChildren() call. A single detached node will not work
   *     because its sibling pointers will not be correctly initialized.
   */
  public final void addChildrenToFront(@Nullable Node children) {
    if (children == null) {
      return; // removeChildren() returns null when there are none
    }
    // NOTE: If there is only one sibling, its previous pointer must point to itself.
    // Null indicates a fully detached node.
    checkNotNull(children.previous, children);
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

  public final void insertAfter(Node existing) {
    existing.checkAttached();
    this.checkDetached();

    final Node existingParent = existing.parent;
    final Node existingNext = existing.next;

    this.parent = existingParent;

    existing.next = this;
    this.previous = existing;

    if (existingNext == null) {
      existingParent.first.previous = this;
      // this.next remains null
    } else {
      existingNext.previous = this;
      this.next = existingNext;
    }
  }

  public final void insertBefore(Node existing) {
    existing.checkAttached();
    this.checkDetached();

    final Node existingParent = existing.parent;
    final Node existingPrevious = existing.previous;

    this.parent = existingParent;

    this.next = existing;
    existing.previous = this;

    this.previous = existingPrevious;
    if (existingPrevious.next == null) {
      existingParent.first = this;
      // existingPrevious.next remains null
    } else {
      // existingParent.first remains existing
      existingPrevious.next = this;
    }
  }

  /**
   * Add all children after 'node'. If 'node' is null, add them to the front of this node.
   *
   * @param children first of a list of sibling nodes who have no parent. NOTE: Usually you would
   *     get this argument from a removeChildren() call. A single detached node will not work
   *     because its sibling pointers will not be correctly initialized.
   */
  public final void addChildrenAfter(@Nullable Node children, @Nullable Node node) {
    if (children == null) {
      return; // removeChildren() returns null when there are none
    }
    checkArgument(node == null || node.parent == this);
    // NOTE: If there is only one sibling, its previous pointer must point to itself.
    // Null indicates a fully detached node.
    checkNotNull(children.previous, children);
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

  /** Swaps `replacement` and its subtree into the position of `this`. */
  public final void replaceWith(Node replacement) {
    this.checkAttached();
    replacement.checkDetached();

    final Node existingParent = this.parent;
    final Node existingNext = this.next;
    final Node existingPrevious = this.previous;

    // Copy over important information.
    // TODO(nickreid): Stop doing this. It's totally unexpected.
    replacement.srcrefIfMissing(this);

    // The sequence below also has to work when `this` is an only child, ehich can cause many of the
    // variables to point to the same object.

    this.parent = null;
    replacement.parent = existingParent;

    this.previous = null;
    replacement.previous = existingPrevious;
    if (existingPrevious.next == null) {
      existingParent.first = replacement;
      // existingPrevious.next remains null
    } else {
      // existingParent.first is unchanged;
      existingPrevious.next = replacement;
    }

    if (existingNext == null) {
      // this.next remains null;
      existingParent.first.previous = replacement;
      // replacement.next remains null
    } else {
      this.next = null;
      existingNext.previous = replacement;
      replacement.next = existingNext;
    }
  }

  /** Removes this node from its parent, but retains its subtree. */
  public final Node detach() {
    this.checkAttached();

    final Node existingParent = this.parent;
    final Node existingNext = this.next;
    final Node existingPrevious = this.previous;

    // The sequence below also has to work when `this` is an only child or has a single sibling,
    // which can cause many of the variables to point to the same object.

    this.parent = null;

    if (existingNext == null) {
      // this.next remains null;
      existingParent.first.previous = existingPrevious;
    } else {
      this.next = null;
      existingNext.previous = existingPrevious;
    }

    this.previous = null;
    if (existingPrevious.next == null) {
      existingParent.first = existingNext;
      // existingPrevious.next remains null
    } else {
      // existingParent.first is unchanged;
      existingPrevious.next = existingNext;
    }

    return this;
  }

  private final void checkAttached() {
    checkState(this.parent != null, "Has no parent: %s", this);
  }

  private final void checkDetached() {
    checkState(this.parent == null, "Has parent: %s", this);
    checkState(this.next == null, "Has next: %s", this);
    checkState(this.previous == null, "Has previous: %s", this);
  }

  /**
   * Removes the first child of Node. Equivalent to: node.removeChild(node.getFirstChild());
   *
   * @return The removed Node.
   */
  public final @Nullable Node removeFirstChild() {
    Node child = first;
    if (child != null) {
      child.detach();
    }
    return child;
  }

  /**
   * Remove all children, but leave them linked to each other.
   *
   * @return The first child node
   */
  public final @Nullable Node removeChildren() {
    Node children = first;
    for (Node child = first; child != null; child = child.next) {
      child.parent = null;
    }
    first = null;
    return children;
  }

  /** Removes all children from this node and isolates the children from each other. */
  public final void detachChildren() {
    for (Node child = first; child != null; ) {
      Node nextChild = child.next;
      child.parent = null;
      child.next = null;
      child.previous = null;
      child = nextChild;
    }
    first = null;
  }

  @VisibleForTesting
  final @Nullable PropListItem lookupProperty(Prop prop) {
    byte propType = (byte) prop.ordinal();
    PropListItem x = propListHead;
    while (x != null && propType != x.propType) {
      x = x.next;
    }
    return x;
  }

  /**
   * Clone the properties from the provided node without copying the property object. The receiving
   * node may not have any existing properties.
   *
   * @param other The node to clone properties from.
   * @return this node.
   */
  public final Node clonePropsFrom(Node other) {
    checkState(this.propListHead == null, "Node has existing properties.");
    this.propListHead = other.propListHead;
    return this;
  }

  /**
   * Checks for invalid or missing properties and feeds error messages for any violations to the
   * given `Consumer`.
   *
   * <p>We use a `Consumer` to avoid the cost of building a usually-empty list every time this
   * method is called.
   */
  public void validateProperties(Consumer<String> violationMessageConsumer) {
    if (propListHead == null) {
      // TODO(bradfordcsmith): Fix the bugs that prevent enabling this validation.
      //
      // In particular:
      //
      // 1. CoverageInstrumentationPass and BranchCoverageInstrumentationCallback
      // create a bunch of nodes without valid source reference info, and it isn't obvious what
      // source reference info they should be using.
      //
      // 2. b/186056977 covers an issue blocking correcting a violation in `VarCheck`
      //
      // 3. Fixing a violation in DeclaredGlobalExternsOnWindow makes an unexpected change to
      //    the pre-computed TypedAst for the runtime libraries.
      //
      // if (token != Token.ROOT) {
      //   violationMessageConsumer.accept("non-ROOT has no properties");
      // }
      return;
    }

    if (token == Token.ROOT) {
      // ROOT tokens should never have properties
      violationMessageConsumer.accept("ROOT has properties");
    }

    for (PropListItem propListItem = propListHead;
        propListItem != null;
        propListItem = propListItem.next) {
      final Prop prop = PROP_VALUES[propListItem.propType];
      // Catch it if the definition of Prop ever changes so that the ordinals don't line up.
      checkState(prop.ordinal() == propListItem.propType, "ordinal doesn't match: %s", prop);

      // TODO(bradfordcsmith): This is not yet an exhaustive list of validations.
      // Other validations should be added as it is found useful to have them.
      // Some property validation is done independently in `AstValidator` and could possibly be
      // moved here.
      //
      // This method was added in response to a bug that created an invalid IS_PARENTHESIZED
      // property that was discovered by a check in `deserializeProperties()`, so initially
      // this method was created to cover the checks previously done there.
      switch (prop) {
        case IS_PARENTHESIZED:
        case MARK_FOR_PARENTHESIZE:
          if (!IR.mayBeExpression(this)) {
            violationMessageConsumer.accept("non-expression is parenthesized");
          }
          break;
        case ARROW_FN:
          if (!isFunction()) {
            violationMessageConsumer.accept("invalid ARROW_FN prop");
          }
          break;
        case ASYNC_FN:
          if (!isFunction()) {
            violationMessageConsumer.accept("invalid ASYNC_FN prop");
          }
          break;
        case SYNTHETIC:
          if (!isBlock()) {
            violationMessageConsumer.accept("invalid SYNTHETIC prop");
          }
          break;
        case COLOR_FROM_CAST:
          if (getColor() == null) {
            violationMessageConsumer.accept("COLOR_FROM_CAST with no Color");
          }
          break;
        case START_OF_OPT_CHAIN:
          if (!(isOptChainCall() || isOptChainGetElem() || isOptChainGetProp())) {
            violationMessageConsumer.accept("START_OF_OPT_CHAIN on non-optional Node");
          }
          break;
        case CONSTANT_VAR_FLAGS:
          if (!(isName() || isImportStar())) {
            violationMessageConsumer.accept("invalid CONST_VAR_FLAGS");
          }
          break;
        case SYNTHESIZED_UNFULFILLED_NAME_DECLARATION:
          // note: we could relax this restriction if VarCheck needed to generate other forms of
          // synthetic externs
          if (!isVar() || !hasOneChild() || !getFirstChild().isName()) {
            violationMessageConsumer.accept(
                "Expected all synthetic unfulfilled declarations to be `var <name>`");
          }
          break;
        default:
          // No validation is currently done for other properties
          break;
      }
    }
  }

  /**
   * @param item The item to inspect
   * @param prop The property to look for
   * @return The replacement list if the property was removed, or 'item' otherwise.
   */
  private static @Nullable PropListItem rebuildListWithoutProp(
      @Nullable PropListItem item, Prop prop) {
    if (item == null) {
      return null;
    } else if (item.propType == prop.ordinal()) {
      return item.next;
    } else {
      PropListItem result = rebuildListWithoutProp(item.next, prop);
      return (result == item.next) ? item : item.chain(result);
    }
  }

  public final @Nullable Object getProp(Prop propType) {
    PropListItem item = lookupProperty(propType);
    if (item == null) {
      return null;
    }
    return item.getObjectValue();
  }

  public final boolean getBooleanProp(Prop propType) {
    return getIntProp(propType) != 0;
  }

  /** Returns the integer value for the property, or 0 if the property is not defined. */
  private int getIntProp(Prop propType) {
    PropListItem item = lookupProperty(propType);
    if (item == null) {
      return 0;
    }
    return item.getIntValue();
  }

  public final void putProp(Prop prop, @Nullable Object value) {
    this.propListHead = rebuildListWithoutProp(this.propListHead, prop);
    if (value != null) {
      this.propListHead = new ObjectPropListItem((byte) prop.ordinal(), value, this.propListHead);
    }
  }

  public final void putBooleanProp(Prop propType, boolean value) {
    putIntProp(propType, value ? 1 : 0);
  }

  public final void putIntProp(Prop prop, int value) {
    this.propListHead = rebuildListWithoutProp(this.propListHead, prop);
    if (value != 0) {
      this.propListHead = new IntPropListItem((byte) prop.ordinal(), value, this.propListHead);
    }
  }

  static long nodePropertyToBit(NodeProperty prop) {
    return 1L << prop.getNumber();
  }

  static long setNodePropertyBit(long bitset, NodeProperty prop) {
    return bitset | nodePropertyToBit(prop);
  }

  static long removeNodePropertyBit(long bitset, NodeProperty prop) {
    return bitset & ~nodePropertyToBit(prop);
  }

  static boolean hasNodePropertyBitSet(long bitset, NodeProperty prop) {
    return (bitset & nodePropertyToBit(prop)) != 0;
  }

  static boolean hasBitSet(long bitset, int bit) {
    return (bitset & 1L << bit) != 0;
  }

  public final long serializeProperties() {
    long propSet = 0;
    for (PropListItem propListItem = this.propListHead;
        propListItem != null;
        propListItem = propListItem.next) {
      Prop prop = PROP_VALUES[propListItem.propType];

      switch (prop) {
        case TYPE_BEFORE_CAST:
          propSet = setNodePropertyBit(propSet, NodeProperty.COLOR_FROM_CAST);
          break;
        case CONSTANT_VAR_FLAGS:
          int intVal = propListItem.getIntValue();
          if (anyBitSet(intVal, ConstantVarFlags.INFERRED)) {
            propSet = setNodePropertyBit(propSet, NodeProperty.IS_INFERRED_CONSTANT);
          }
          if (anyBitSet(intVal, ConstantVarFlags.DECLARED)) {
            propSet = setNodePropertyBit(propSet, NodeProperty.IS_DECLARED_CONSTANT);
          }
          break;
        case SIDE_EFFECT_FLAGS:
          propSet = setNodePropertySideEffectFlags(propSet, propListItem.getIntValue());
          break;
        default:
          if (propListItem instanceof Node.IntPropListItem) {
            NodeProperty nodeProperty = PropTranslator.serialize(prop);
            if (nodeProperty != null) {
              propSet = setNodePropertyBit(propSet, nodeProperty);
            }
          }
          break;
      }
    }
    return propSet;
  }

  /**
   * Update a bit field to be used for serialized node properties to include bits from the
   * `SIDE_EFFECT_FLAGS` Node property.
   *
   * @param propSet the bit field to update
   * @param sideEffectFlags the integer value from the `SIDE_EFFECT_FLAGS` property
   * @return the updated property set
   */
  private long setNodePropertySideEffectFlags(long propSet, int sideEffectFlags) {
    if (anyBitSet(sideEffectFlags, SideEffectFlags.MUTATES_GLOBAL_STATE)) {
      propSet = setNodePropertyBit(propSet, NodeProperty.MUTATES_GLOBAL_STATE);
    }
    if (anyBitSet(sideEffectFlags, SideEffectFlags.MUTATES_THIS)) {
      propSet = setNodePropertyBit(propSet, NodeProperty.MUTATES_THIS);
    }
    if (anyBitSet(sideEffectFlags, SideEffectFlags.MUTATES_ARGUMENTS)) {
      propSet = setNodePropertyBit(propSet, NodeProperty.MUTATES_ARGUMENTS);
    }
    if (anyBitSet(sideEffectFlags, SideEffectFlags.THROWS)) {
      propSet = setNodePropertyBit(propSet, NodeProperty.THROWS);
    }
    return propSet;
  }

  public final void deserializeProperties(long propSet) {
    if (this.isRoot()) {
      checkState(this.propListHead == null, this.propListHead);
    } else {
      checkState(this.propListHead.propType == Prop.SOURCE_FILE.ordinal(), this.propListHead);
    }

    // We'll gather the bits for CONST_VAR_FLAGS and SIDE_EFFECT_FLAGS into these variables.
    int constantVarFlags = 0;
    int sideEffectFlags = 0;
    // Exclude the sign bit for clarity.
    for (int i = 0; i < 63; i++) {
      if (!hasBitSet(propSet, i)) {
        continue;
      }
      NodeProperty nodeProperty = NodeProperty.forNumber(i);
      switch (nodeProperty) {
        case IS_DECLARED_CONSTANT:
          constantVarFlags |= ConstantVarFlags.DECLARED;
          break;
        case IS_INFERRED_CONSTANT:
          constantVarFlags |= ConstantVarFlags.INFERRED;
          break;
        case MUTATES_GLOBAL_STATE:
          sideEffectFlags |= SideEffectFlags.MUTATES_GLOBAL_STATE;
          break;
        case MUTATES_THIS:
          sideEffectFlags |= SideEffectFlags.MUTATES_THIS;
          break;
        case MUTATES_ARGUMENTS:
          sideEffectFlags |= SideEffectFlags.MUTATES_ARGUMENTS;
          break;
        case THROWS:
          sideEffectFlags |= SideEffectFlags.THROWS;
          break;
        default:
          // All other properties are booleans that are 1-to-1 equivalent with Node properties.
          Prop prop = PropTranslator.deserialize(nodeProperty);
          if (prop == null) {
            throw new IllegalStateException("Can not translate " + nodeProperty + " to AST Prop");
          }
          this.propListHead = new IntPropListItem((byte) prop.ordinal(), 1, this.propListHead);
          break;
      }
    }

    // Store the CONSTANT_VAR_FLAGS
    if (constantVarFlags != 0) {
      this.propListHead =
          new IntPropListItem(
              (byte) Prop.CONSTANT_VAR_FLAGS.ordinal(), constantVarFlags, this.propListHead);
    }

    if (sideEffectFlags != 0) {
      this.propListHead =
          new IntPropListItem(
              (byte) Prop.SIDE_EFFECT_FLAGS.ordinal(), sideEffectFlags, this.propListHead);
    }

    // Make sure the deserialized properties are valid.
    validateProperties(
        // NOTE: errorMessage will never be null, but just passing `false` to `checkState()`
        // triggers warning messages from some code analysis tools.
        errorMessage ->
            checkState(errorMessage != null, "deserialize error: %s: %s", errorMessage, this));
  }

  /** Sets the syntactical type specified on this node. */
  public final void setDeclaredTypeExpression(Node typeExpression) {
    putProp(Prop.DECLARED_TYPE_EXPR, typeExpression);
  }

  /**
   * Returns the syntactical type specified on this node. Not to be confused with {@link
   * #getJSType()} which returns the compiler-inferred type.
   */
  public final @Nullable Node getDeclaredTypeExpression() {
    return (Node) getProp(Prop.DECLARED_TYPE_EXPR);
  }

  /** Sets the type of this node before casting. */
  public final void setJSTypeBeforeCast(JSType type) {
    putProp(Prop.TYPE_BEFORE_CAST, type);
  }

  /**
   * Returns the type of this node before casting. This annotation will only exist on the first
   * child of a CAST node after type checking.
   */
  public final @Nullable JSType getJSTypeBeforeCast() {
    return (JSType) getProp(Prop.TYPE_BEFORE_CAST);
  }

  /**
   * Indicate that this node's color comes from a type assertion. Only set when colors are present;
   * when JSTypes are on the AST we instead preserve the actual JSType before the type assertion.
   */
  public final void setColorFromTypeCast() {
    checkState(getColor() != null, "Only use on nodes with colors present");
    putBooleanProp(Prop.COLOR_FROM_CAST, true);
  }

  /**
   * Indicates that this node's color comes from a type assertion. Only set when colors are present.
   */
  public final boolean isColorFromTypeCast() {
    return getBooleanProp(Prop.COLOR_FROM_CAST);
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

  public final double getDouble() {
    return ((NumberNode) this).number;
  }

  public final void setDouble(double x) {
    checkState(!Double.isNaN(x), x);
    checkState(isPositive(x), x);
    ((NumberNode) this).number = x;
  }

  public final BigInteger getBigInt() {
    return ((BigIntNode) this).bigint;
  }

  public final void setBigInt(BigInteger number) {
    checkNotNull(number);
    checkState(number.signum() >= 0, number);
    ((BigIntNode) this).bigint = number;
  }

  public final String getString() {
    return ((StringNode) this).str;
  }

  public final void setString(String str) {
    ((StringNode) this).str = RhinoStringPool.addOrGet(str); // RhinoStringPool is null-hostile.
  }

  public final String getRawString() {
    return ((TemplateLiteralSubstringNode) this).raw;
  }

  public final @Nullable String getCookedString() {
    return ((TemplateLiteralSubstringNode) this).cooked;
  }

  @Override
  public final String toString() {
    return toString(true, true, true);
  }

  public final String toString(boolean printSource, boolean printAnnotations, boolean printType) {
    StringBuilder sb = new StringBuilder();
    toString(sb, printSource, printAnnotations, printType);
    return sb.toString();
  }

  private void toString(
      StringBuilder sb, boolean printSource, boolean printAnnotations, boolean printType) {
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
        sb.append(':');
        sb.append(getCharno());
        sb.append(' ');
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
        Prop type = PROP_VALUES[keys[i]];
        PropListItem x = lookupProperty(type);
        sb.append(" [");
        sb.append(Ascii.toLowerCase(String.valueOf(type)));
        sb.append(": ");
        sb.append(x);
        sb.append(']');
      }
      if (this.originalName != null) {
        sb.append(" [original_name: ");
        sb.append(this.originalName);
        sb.append(']');
      }
    }

    if (printType && jstypeOrColor != null) {
      String typeString = jstypeOrColor.toString();
      if (typeString != null) {
        sb.append(" : ");
        sb.append(typeString);
      }
    }
  }

  private static String createJsonPair(String name, String value) {
    return "\"" + name + "\":\"" + value + "\"";
  }

  private static String createJsonPairRawValue(String name, String value) {
    return "\"" + name + "\":" + value;
  }

  private void toJson(Appendable sb) throws IOException {
    sb.append('{');
    sb.append(createJsonPair("token", token.toString()));
    if (this instanceof StringNode) {
      sb.append(',');
      sb.append(createJsonPair("string", getString()));
    } else if (token == Token.FUNCTION) {
      sb.append(',');
      // In the case of JsDoc trees, the first child is often not a string
      // which causes exceptions to be thrown when calling toString or
      // toStringTree.
      if (first == null || first.token != Token.NAME) {
        sb.append(createJsonPair("functionName", "<invalid>"));
      } else {
        sb.append(createJsonPair("functionName", first.getString()));
      }
    } else if (token == Token.NUMBER) {
      sb.append(',');
      sb.append(createJsonPair("number", String.valueOf(getDouble())));
    }
    int lineno = getLineno();
    if (lineno != -1) {
      sb.append(',');
      sb.append("\"sourceLocation\":{");
      sb.append(createJsonPairRawValue("line", String.valueOf(lineno)));
      sb.append(',');
      sb.append(createJsonPairRawValue("col", String.valueOf(getCharno())));
      if (length != 0) {
        sb.append(',');
        sb.append(createJsonPairRawValue("length", String.valueOf(length)));
      }
      sb.append("}");
    }

    if (this.originalName != null) {
      sb.append(",");
      sb.append(createJsonPair("original_name", this.originalName));
    }

    byte[] keys = getSortedPropTypes();
    if (keys.length != 0) {
      sb.append(",");
      sb.append("\"props\":{");
      for (int i = 0; i < keys.length; i++) {
        Prop type = PROP_VALUES[keys[i]];
        PropListItem x = lookupProperty(type);
        sb.append(
            createJsonPair(
                Ascii.toLowerCase(String.valueOf(type)),
                x.toString().replace("\n", "\\n").replace("\"", "\\\"")));
        if (i + 1 < keys.length) {
          sb.append(',');
        }
      }
      sb.append("}");
    }

    if (jstypeOrColor != null) {
      String typeString = jstypeOrColor.toString();
      if (typeString != null) {
        sb.append(',');
        sb.append(createJsonPair("typeString", typeString.replace("\"", "\\\"")));
      }
    }

    if (this.first != null) {
      sb.append(',');
      sb.append("\"children\":[");
      for (Node child = this.first; child != null; child = child.next) {
        child.toJson(sb);
        if (child.next != null) {
          sb.append(',');
        }
      }
      sb.append("]");
    }
    sb.append('}');
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

  private static void toStringTreeHelper(Node n, int level, Appendable sb) throws IOException {
    for (int i = 0; i != level; ++i) {
      sb.append("    ");
    }
    sb.append(n.toString());
    sb.append('\n');
    for (Node cursor = n.first; cursor != null; cursor = cursor.next) {
      toStringTreeHelper(cursor, level + 1, sb);
    }
  }

  public final void appendJsonTree(Appendable appendable) throws IOException {
    toJsonTreeHelper(this, appendable);
  }

  private static void toJsonTreeHelper(Node n, Appendable sb) throws IOException {
    n.toJson(sb);
  }

  private transient Token token; // Type of the token of the node; NAME for example
  private transient @Nullable Node next; // next sibling, a linked list
  private transient @Nullable Node previous; // previous sibling, a circular linked list
  private transient @Nullable Node first; // first element of a linked list of children
    private transient @Nullable  Node parent;
  // We get the last child as first.previous. But last.next is null, not first.

  /**
   * Source position of this node. The position is encoded with the column number in the low 12 bits
   * of the integer, and the line number in the rest. Create some handy constants so we can change
   * this size if we want.
   */
  private transient int linenoCharno = -1;

  /** The length of the code represented by the node. */
  private transient int length;

  private transient @Nullable Object jstypeOrColor;

  private transient @Nullable String originalName;

  /**
   * Linked list of properties. Since vast majority of nodes would have no more than 2 properties,
   * linked list saves memory and provides fast lookup. If this does not holds, propListHead can be
   * replaced by UintMap.
   */
  private transient @Nullable PropListItem propListHead;

  // ==========================================================================
  // Source position management

  public final void setStaticSourceFileFrom(Node other) {
    // Make sure source file prop nodes are not duplicated.
    if (other.propListHead != null
        && (this.propListHead == null
            || (this.propListHead.propType == Prop.SOURCE_FILE.ordinal()
                && this.propListHead.next == null))) {
      // Either the node has only Prop.SOURCE_FILE as a property or has not properties.
      PropListItem tail = other.propListHead;
      while (tail.next != null) {
        tail = tail.next;
      }
      if (tail.propType == Prop.SOURCE_FILE.ordinal()) {
        propListHead = tail;
        return;
      }
    }
    setStaticSourceFile(other.getStaticSourceFile());
  }

  public final Node setStaticSourceFile(@Nullable StaticSourceFile file) {
    this.putProp(Prop.SOURCE_FILE, file);
    return this;
  }

  /** Sets the source file to a non-extern file of the given name. */
  public final void setSourceFileForTesting(String name) {
    this.putProp(Prop.SOURCE_FILE, new SimpleSourceFile(name, SourceKind.STRONG));
  }

  // TODO(johnlenz): make this final
  public @Nullable String getSourceFileName() {
    StaticSourceFile file = getStaticSourceFile();
    return file == null ? null : file.getName();
  }

  /** Returns the source file associated with this input. */
  public @Nullable StaticSourceFile getStaticSourceFile() {
    return ((StaticSourceFile) this.getProp(Prop.SOURCE_FILE));
  }

  /** Sets the ID of the input this Node came from. */
  public void setInputId(InputId inputId) {
    this.putProp(Prop.INPUT_ID, inputId);
  }

  /** Returns the Id of the CompilerInput associated with this Node. */
   public @Nullable  InputId getInputId() {
    return ((InputId) this.getProp(Prop.INPUT_ID));
  }

  /**
   * The original name of this node, if the node has been renamed.
   *
   * <p>Do not use original name to make optimization decisions. The original intent was to preserve
   * some naming for lightly optimized code to put into source maps. It is not rigorously defined
   * and is not a suitable replacement for a canonical identifier. "Original name" is not associated
   * with any scope and easily transfers to unrelated values. Its existance and use beyond its
   * original purpose has delayed creating useful more precise alternatives.
   *
   * @deprecated "original name" is poorly defined.
   */
  @Deprecated
  public final @Nullable String getOriginalName() {
    return this.originalName;
  }

  public final void setOriginalName(String s) {
    this.originalName = (s == null) ? null : RhinoStringPool.addOrGet(s);
  }

  // Copy the original
  public final void setOriginalNameFromName(Node name) {
    this.originalName = name.getString();
  }

  /** Whether this node should be indexed by static analysis / code indexing tools. */
  public final boolean isIndexable() {
    return !this.getBooleanProp(Prop.NON_INDEXABLE);
  }

  public final void makeNonIndexable() {
    this.putBooleanProp(Prop.NON_INDEXABLE, true);
  }

  public final void makeNonIndexableRecursive() {
    this.makeNonIndexable();
    for (Node child = this.first; child != null; child = child.getNext()) {
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
    if (this.linenoCharno == -1) {
      return -1;
    } else {
      return this.linenoCharno >>> CHARNO_BITS;
    }
  }

  // Returns the 0-based column number
  public final int getCharno() {
    if (this.linenoCharno == -1) {
      return -1;
    } else {
      return this.linenoCharno & MAX_COLUMN_NUMBER;
    }
  }

  public final String getLocation() {
    return this.getSourceFileName() + ":" + this.getLineno() + ":" + this.getCharno();
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
    return linenoCharno;
  }

  /**
   * CHARNO_BITS represents how many of the lower-order bits of linenoCharno are reserved for
   * storing the column number. Bits above these store the line number. This gives us decent
   * position information for everything except files already passed through a minimizer, where
   * lines might be longer than 4096 characters.
   */
  private static final int CHARNO_BITS = 12;

  /**
   * MAX_COLUMN_NUMBER represents the maximum column number that can be represented. JSCompiler's
   * modifications to Rhino cause all tokens located beyond the maximum column to MAX_COLUMN_NUMBER.
   */
  public static final int MAX_COLUMN_NUMBER = (1 << CHARNO_BITS) - 1;

  /**
   * Merges the line number and character number in one integer.
   *
   * <p>The charno takes the first 12 bits and the line number takes the rest. If the charno is
   * greater than (2^12)-1 it is adjusted to (2^12)-1
   */
  public final Node setLinenoCharno(int lineno, int charno) {
    if (lineno < 0 || charno < 0) {
      this.linenoCharno = -1;
      return this;
    }

    if (charno > MAX_COLUMN_NUMBER) {
      charno = MAX_COLUMN_NUMBER;
    }
    this.linenoCharno = (lineno << CHARNO_BITS) | charno;

    return this;
  }

  // ==========================================================================
  // Iteration

  /**
   * Return an iterable object that iterates over this node's children. The iterator does not
   * support the optional operation {@link Iterator#remove()}.
   *
   * <p>To iterate over a node's children, one can write
   *
   * <pre>Node n = ...;
   * for (Node child : n.children()) { ...</pre>
   *
   * NOTE: Do not use 'children' for recursive descent of the AST. The overhead of using iterators
   * rather then getFirstChild()/getNext() is very significant. We have deprecated it as it is easy
   * to misuse.
   */
  @Deprecated
  public final Iterable<Node> children() {
    if (first == null) {
      return Collections.emptySet();
    } else {
      return new SiblingNodeIterable(first);
    }
  }

  /**
   * @see Node#children()
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
   * @see Node#children()
   */
  private static final class SiblingNodeIterator implements Iterator<Node> {
      private @Nullable  Node current;

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

  final @Nullable PropListItem getPropListHeadForTesting() {
    return propListHead;
  }

  final void setPropListHead(@Nullable PropListItem propListHead) {
    this.propListHead = propListHead;
  }

   public final @Nullable  Node getParent() {
    return parent;
  }

  public final boolean hasParent() {
    return parent != null;
  }

   public final @Nullable  Node getGrandparent() {
    return parent == null ? null : parent.parent;
  }

  /**
   * Gets the ancestor node relative to this.
   *
   * @param level 0 = this, 1 = the parent, etc.
   */
   public final @Nullable  Node getAncestor(int level) {
    checkArgument(level >= 0);
    Node node = this;
    while (node != null && level-- > 0) {
      node = node.getParent();
    }
    return node;
  }

  /** Is this Node the same as {@code node} or a descendant of {@code node}? */
  public final boolean isDescendantOf(Node node) {
    for (Node n = this; n != null; n = n.parent) {
      if (n == node) {
        return true;
      }
    }
    return false;
  }

  public final boolean isOnlyChildOf(Node possibleParent) {
    return possibleParent == getParent() && getPrevious() == null && getNext() == null;
  }

  public final boolean isFirstChildOf(Node possibleParent) {
    return possibleParent == getParent() && getPrevious() == null;
  }

  public final boolean isSecondChildOf(Node possibleParent) {
    Node previousNode = getPrevious();

    return previousNode != null && previousNode.isFirstChildOf(possibleParent);
  }

  /** Iterates all of the node's ancestors excluding itself. */
  public final AncestorIterable getAncestors() {
    return new AncestorIterable(this.getParent());
  }

  /** Iterator to go up the ancestor tree. */
  public static final class AncestorIterable implements Iterable<Node> {
      private @Nullable  Node cur;

    /**
     * @param cur The node to start.
     */
    AncestorIterable(@Nullable Node cur) {
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
   * Check for one child more efficiently than by iterating over all the children as is done with
   * Node.getChildCount().
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
   * Check for zero or one child more efficiently than by iterating over all the children as is done
   * with Node.getChildCount().
   *
   * @return Whether the node has no children or exactly one child.
   */
  public final boolean hasZeroOrOneChild() {
    return first == getLastChild();
  }

  /**
   * Check for more than one child more efficiently than by iterating over all the children as is
   * done with Node.getChildCount().
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

  /** Returns true if this node is equivalent semantically to another */
  public final boolean isEquivalentTo(Node node) {
    return isEquivalentTo(node, false, true, false, false);
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
   * @param recurse Whether to compare the children of the current node. If not, only the count of
   *     the children are compared.
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

    if (compareType && !Objects.equals(this.jstypeOrColor, node.jstypeOrColor)) {
      return false;
    }

    if (jsDoc && !JSDocInfo.areEquivalent(getJSDocInfo(), node.getJSDocInfo())) {
      return false;
    }

    Node thisDte = this.getDeclaredTypeExpression();
    Node thatDte = node.getDeclaredTypeExpression();
    if (thisDte == thatDte) {
      // Do nothing
    } else if (thisDte == null || thatDte == null) {
      return false;
    } else if (!thisDte.isEquivalentTo(thatDte, compareType, recurse, jsDoc)) {
      return false;
    }

    EnumSet<Prop> propSet = EnumSet.noneOf(Prop.class);
    for (PropListItem propListItem = this.propListHead;
        propListItem != null;
        propListItem = propListItem.next) {
      Prop prop = PROP_VALUES[propListItem.propType];
      propSet.add(prop);
    }
    for (PropListItem propListItem = node.propListHead;
        propListItem != null;
        propListItem = propListItem.next) {
      Prop prop = PROP_VALUES[propListItem.propType];
      propSet.add(prop);
    }

    for (Prop prop : propSet) {
      if (PROP_MAP_FOR_EQUALITY_KEYS.contains(prop)) {
        Function<Node, Object> getter = PROP_MAP_FOR_EQUALITY.get(prop);
        if (!Objects.equals(getter.apply(this), getter.apply(node))) {
          return false;
        }
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
      for (Node n = first, n2 = node.first; n != null; n = n.next, n2 = n2.next) {
        if (!n.isEquivalentTo(n2, compareType, recurse, jsDoc, sideEffect)) {
          return false;
        }
      }
    }

    return true;
  }

  /**
   * Accessors for {@link Node} properties that should also be compared when comparing nodes for
   * equality.
   *
   * <p>We'd prefer to list the props that should be ignored rather than the ones that should be
   * checked, but that was too difficult initially. In general these properties are ones that show
   * up as keywords / symbols which don't have their own nodes.
   *
   * <p>Accessor functions are used rather than {@link Prop}s to encode the correct way of reading
   * the prop.
   */
  private static final ImmutableMap<Prop, Function<Node, Object>> PROP_MAP_FOR_EQUALITY =
      new ImmutableMap.Builder<Prop, Function<Node, Object>>()
          .put(Prop.ARROW_FN, Node::isArrowFunction)
          .put(Prop.ASYNC_FN, Node::isAsyncFunction)
          .put(Prop.GENERATOR_FN, Node::isGeneratorFunction)
          .put(Prop.START_OF_OPT_CHAIN, Node::isOptionalChainStart)
          .put(Prop.STATIC_MEMBER, Node::isStaticMember)
          .put(Prop.YIELD_ALL, Node::isYieldAll)
          .put(Prop.EXPORT_DEFAULT, (n) -> n.getIntProp(Prop.EXPORT_DEFAULT))
          .put(Prop.EXPORT_ALL_FROM, (n) -> n.getIntProp(Prop.EXPORT_ALL_FROM))
          .put(Prop.INCRDECR, (n) -> n.getIntProp(Prop.INCRDECR))
          .put(Prop.QUOTED, (n) -> n.getIntProp(Prop.QUOTED))
          .put(Prop.FREE_CALL, (n) -> n.getBooleanProp(Prop.FREE_CALL))
          .put(Prop.COMPUTED_PROP_METHOD, (n) -> n.getBooleanProp(Prop.COMPUTED_PROP_METHOD))
          .put(Prop.COMPUTED_PROP_GETTER, (n) -> n.getBooleanProp(Prop.COMPUTED_PROP_GETTER))
          .put(Prop.COMPUTED_PROP_SETTER, (n) -> n.getBooleanProp(Prop.COMPUTED_PROP_SETTER))
          .buildOrThrow();

  /** Used for faster Map.containsKey() lookups in PROP_MAP_FOR_EQUALITY */
  private static final EnumSet<Prop> PROP_MAP_FOR_EQUALITY_KEYS =
      EnumSet.copyOf(PROP_MAP_FOR_EQUALITY.keySet());

  /**
   * This function takes a set of GETPROP nodes and produces a string that is each property
   * separated by dots. If the node ultimately under the left sub-tree is not a simple name, this is
   * not a valid qualified name.
   *
   * @return a null if this is not a qualified name, or a dot-separated string of the name and
   *     properties.
   */
   public final @Nullable  String getQualifiedName() {
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

   public final @Nullable  QualifiedName getQualifiedNameObject() {
    return isQualifiedName() ? new QualifiedName.NodeQname(this) : null;
  }

  /**
   * Helper method for {@link #getQualifiedName} to handle GETPROP nodes.
   *
   * @param reserve The number of characters of space to reserve in the StringBuilder
   * @return {@code null} if this is not a qualified name or a StringBuilder if it is a complex
   *     qualified name.
   */
   private @Nullable  StringBuilder getQualifiedNameForGetProp(int reserve) {
    String propName = this.getString();
    reserve += 1 + propName.length(); // +1 for the '.'
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
   * @deprecated "original name" is poorly defined. See #getOriginalName
   */
  @Deprecated public final @Nullable  String getOriginalQualifiedName() {
    if (token == Token.NAME) {
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

      String right = this.getOriginalName();
      if (right == null) {
        right = this.getString();
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
   * Returns whether a node corresponds to a simple or a qualified name, such as <code>x</code> or
   * <code>a.b.c</code> or <code>this.a</code>.
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

      case MEMBER_FUNCTION_DEF:
        // These are explicitly *not* qualified name components.
      default:
        return false;
    }
  }

  /**
   * Returns whether a node matches a simple name, such as <code>x</code>, returns false if this is
   * not a NAME node.
   */
  public final boolean matchesName(String name) {
    if (token != Token.NAME) {
      return false;
    }
    String internalString = getString();
    return !internalString.isEmpty() && name.equals(internalString);
  }

  /**
   * Check that if two NAME node match, returns false if either node is not a NAME node. As a empty
   * string is not considered a valid Name (it is an AST placeholder), empty strings are never
   * considered to be matches.
   */
  public final boolean matchesName(Node n) {
    if (token != Token.NAME || n.token != Token.NAME) {
      return false;
    }

    // ==, rather than equal as it is interned in setString
    String internalString = getString();
    return !internalString.isEmpty()
        && RhinoStringPool.uncheckedEquals(internalString, n.getString());
  }

  /**
   * Returns whether a node matches a simple or a qualified name, such as <code>x</code> or <code>
   * a.b.c</code> or <code>this.a</code>.
   */
  public final boolean matchesQualifiedName(String name) {
    return matchesQualifiedName(name, name.length());
  }

  /**
   * Returns whether a node matches a simple or a qualified name, such as <code>x</code> or <code>
   * a.b.c</code> or <code>this.a</code>.
   */
  private boolean matchesQualifiedName(String qname, int endIndex) {
    int start = qname.lastIndexOf('.', endIndex - 1) + 1;

    switch (this.getToken()) {
      case NAME:
      case IMPORT_STAR:
        String name = getString();
        return start == 0 && !name.isEmpty() && name.length() == endIndex && qname.startsWith(name);
      case THIS:
        return start == 0 && 4 == endIndex && qname.startsWith("this");
      case SUPER:
        return start == 0 && 5 == endIndex && qname.startsWith("super");
      case GETPROP:
        String prop = this.getString();
        return start > 1
            && prop.length() == endIndex - start
            && prop.regionMatches(0, qname, start, endIndex - start)
            && getFirstChild().matchesQualifiedName(qname, start - 1);

      case MEMBER_FUNCTION_DEF:
        // These are explicitly *not* qualified name components.
      default:
        return false;
    }
  }

  /**
   * Returns whether a node matches a simple or a qualified name, such as <code>x</code> or <code>
   * a.b.c</code> or <code>this.a</code>.
   */
  public final boolean matchesQualifiedName(Node n) {
    if (n.token != token) {
      return false;
    }

    switch (token) {
      case NAME:
        return this.matchesName(n);
      case THIS:
      case SUPER:
        return true;
      case GETPROP:
        return RhinoStringPool.uncheckedEquals(this.getString(), n.getString())
            && getFirstChild().matchesQualifiedName(n.getFirstChild());

      case MEMBER_FUNCTION_DEF:
        // These are explicitly *not* qualified name components.
      default:
        return false;
    }
  }

  /**
   * Returns whether a node corresponds to a simple or a qualified name without a "this" reference,
   * such as <code>a.b.c</code>, but not <code>this.a</code> .
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

  @DoNotCall
  @GwtIncompatible
  @Override
  public final Object clone() {
    throw new UnsupportedOperationException("Did you mean cloneNode?");
  }

  /** Returns a detached clone of the Node, specifically excluding its children. */
  @CheckReturnValue
  public final Node cloneNode() {
    return cloneNode(false);
  }

  /** Returns a detached clone of the Node, specifically excluding its children. */
  @CheckReturnValue
  Node cloneNode(boolean cloneTypeExprs) {
    Node clone = new Node(token);
    copyBaseNodeFields(this, clone, cloneTypeExprs);
    return clone;
  }

  private static void copyBaseNodeFields(Node source, Node dest, boolean cloneTypeExprs) {
    dest.linenoCharno = source.linenoCharno;
    dest.length = source.length;
    dest.jstypeOrColor = source.jstypeOrColor;
    dest.originalName = source.originalName;
    dest.propListHead = source.propListHead;

    // TODO(johnlenz): Remove this once JSTypeExpression are immutable
    if (cloneTypeExprs) {
      JSDocInfo info = source.getJSDocInfo();
      if (info != null) {
        dest.setJSDocInfo(info.clone(true));
      }
    }
  }

  /** Returns a detached clone of the Node and all its children. */
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

  /** Copy the source info from `other` onto `this`. */
  public final Node srcref(Node other) {
    setStaticSourceFileFrom(other);
    this.originalName = other.originalName;
    linenoCharno = other.linenoCharno;
    length = other.length;
    return this;
  }

  /** For all Nodes in the subtree of `this`, copy the source info from `other`. */
  public final Node srcrefTree(Node other) {
    this.srcref(other);
    for (Node child = first; child != null; child = child.next) {
      child.srcrefTree(other);
    }
    return this;
  }

  /** Iff source info is not set on `this`, copy the source info from `other`. */
  public final Node srcrefIfMissing(Node other) {
    if (getStaticSourceFile() == null) {
      setStaticSourceFileFrom(other);
      linenoCharno = other.linenoCharno;
      length = other.length;
    }

    // TODO(lharker): should this be inside the above if condition?
    // If the node already has a source file, it seems strange to
    // go ahead and set the original name anyway.
    if (this.originalName == null) {
      this.originalName = other.originalName;
    }

    return this;
  }

  /**
   * For all Nodes in the subtree of `this`, iff source info is not set, copy the source info from
   * `other`.
   */
  public final Node srcrefTreeIfMissing(Node other) {
    this.srcrefIfMissing(other);
    for (Node child = first; child != null; child = child.next) {
      child.srcrefTreeIfMissing(other);
    }
    return this;
  }

  // ==========================================================================
  // Custom annotations

  /**
   * Returns the compiler inferred type on this node. Not to be confused with {@link
   * #getDeclaredTypeExpression()} which returns the syntactically specified type.
   */
   public final @Nullable  JSType getJSType() {
    return (this.jstypeOrColor instanceof JSType) ? (JSType) this.jstypeOrColor : null;
  }

  /** Returns the compiled inferred type on this node, or throws an NPE if there isn't one. */
  public final JSType getJSTypeRequired() {
    return checkNotNull(this.getJSType(), "no jstypeOrColor: %s", this);
  }

  public final Node setJSType(@Nullable JSType x) {
    checkState(this.jstypeOrColor == null || this.jstypeOrColor instanceof JSType, this);
    this.jstypeOrColor = x;
    return this;
  }

  /**
   * Returns the compiled inferred type on this node. Not to be confused with {@link
   * #getDeclaredTypeExpression()} which returns the syntactically specified type.
   */
   public final @Nullable  Color getColor() {
    return (this.jstypeOrColor instanceof Color) ? (Color) this.jstypeOrColor : null;
  }

  public final Node setColor(@Nullable Color x) {
    checkState(this.jstypeOrColor == null || this.jstypeOrColor instanceof Color, this);
    this.jstypeOrColor = x;
    return this;
  }

  /** Copies a nodes JSType or Color (if present) */
  public final Node copyTypeFrom(Node other) {
    this.jstypeOrColor = other.jstypeOrColor;
    return this;
  }

  /**
   * Get the {@link JSDocInfo} attached to this node.
   *
   * @return the information or {@code null} if no JSDoc is attached to this node
   */
   public final @Nullable  JSDocInfo getJSDocInfo() {
    return (JSDocInfo) getProp(Prop.JSDOC_INFO);
  }

  /** Sets the {@link JSDocInfo} attached to this node. */
  public final Node setJSDocInfo(JSDocInfo info) {
    putProp(Prop.JSDOC_INFO, info);
    return this;
  }

  /** This node was last changed at {@code time} */
  public final void setChangeTime(int time) {
    putIntProp(Prop.CHANGE_TIME, time);
  }

  /** Returns the time of the last change for this node */
  public final int getChangeTime() {
    return getIntProp(Prop.CHANGE_TIME);
  }

  public final void setDeleted(boolean deleted) {
    putBooleanProp(Prop.DELETED, deleted);
  }

  public final boolean isDeleted() {
    return getBooleanProp(Prop.DELETED);
  }

  /** If this node represents a typedef declaration, the associated JSType */
  public final void setTypedefTypeProp(JSType type) {
    putProp(Prop.TYPEDEF_TYPE, type);
  }

  /** If this node represents a typedef declaration, the associated JSType */
  public final JSType getTypedefTypeProp() {
    return (JSType) getProp(Prop.TYPEDEF_TYPE);
  }

  /** Sets the value for isUnusedParameter() */
  public final void setUnusedParameter(boolean unused) {
    putBooleanProp(Prop.IS_UNUSED_PARAMETER, unused);
  }

  /**
   * Is this node an unused function parameter declaration?
   *
   * <p>Set by RemoveUnusedVars
   */
  public final boolean isUnusedParameter() {
    return getBooleanProp(Prop.IS_UNUSED_PARAMETER);
  }

  /** Sets the isShorthandProperty annotation. */
  public final void setShorthandProperty(boolean shorthand) {
    putBooleanProp(Prop.IS_SHORTHAND_PROPERTY, shorthand);
  }

  /** Whether this {x:x} property was originally parsed as {x}. */
  public final boolean isShorthandProperty() {
    return getBooleanProp(Prop.IS_SHORTHAND_PROPERTY);
  }

  /** Returns whether this node is an optional node in the ES6 Typed syntax. */
  public final boolean isOptionalEs6Typed() {
    return getBooleanProp(Prop.OPT_ES6_TYPED);
  }

  /** Sets whether this is a synthetic block that should not be considered a real source block. */
  public final void setIsSyntheticBlock(boolean val) {
    checkState(token == Token.BLOCK);
    putBooleanProp(Prop.SYNTHETIC, val);
  }

  /**
   * Returns whether this is a synthetic block that should not be considered a real source block.
   */
  public final boolean isSyntheticBlock() {
    return getBooleanProp(Prop.SYNTHETIC);
  }

  public final void setIsSynthesizedUnfulfilledNameDeclaration(boolean val) {
    checkState(
        token == Token.VAR && hasOneChild() && getFirstChild().isName(),
        // we could relax this restriction if VarCheck wanted to generate other forms of synthetic
        // externs
        "Expected all synthetic unfulfilled declarations to be `var <name>`, found %s",
        this);
    putBooleanProp(Prop.SYNTHESIZED_UNFULFILLED_NAME_DECLARATION, val);
  }

  public final boolean isSynthesizedUnfulfilledNameDeclaration() {
    return getBooleanProp(Prop.SYNTHESIZED_UNFULFILLED_NAME_DECLARATION);
  }

  /** Sets whether this node contained the "use strict" directive. */
  public final void setUseStrict(boolean x) {
    this.putBooleanProp(Prop.USE_STRICT, x);
  }

  /** Returns whether this node contained the "use strict" directive. */
  public final boolean isUseStrict() {
    return this.getBooleanProp(Prop.USE_STRICT);
  }

  /**
   * Sets whether this is an added block that should not be considered a real source block. Eg: In
   * "if (true) x;", the "x;" is put under an added block in the AST.
   */
  public final void setIsAddedBlock(boolean val) {
    putBooleanProp(Prop.ADDED_BLOCK, val);
  }

  /** Returns whether this is an added block that should not be considered a real source block. */
  public final boolean isAddedBlock() {
    return getBooleanProp(Prop.ADDED_BLOCK);
  }

  /**
   * Sets whether this node is a static member node. This method is meaningful only on {@link
   * Token#GETTER_DEF}, {@link Token#SETTER_DEF} or {@link Token#MEMBER_FUNCTION_DEF} nodes
   * contained within {@link Token#CLASS}.
   */
  public final void setStaticMember(boolean isStatic) {
    putBooleanProp(Prop.STATIC_MEMBER, isStatic);
  }

  /**
   * Returns whether this node is a static member node. This method is meaningful only on {@link
   * Token#GETTER_DEF}, {@link Token#SETTER_DEF} or {@link Token#MEMBER_FUNCTION_DEF} nodes
   * contained within {@link Token#CLASS}.
   */
  public final boolean isStaticMember() {
    return getBooleanProp(Prop.STATIC_MEMBER);
  }

  /**
   * Sets whether this node is a generator node. This method is meaningful only on {@link
   * Token#FUNCTION} or {@link Token#MEMBER_FUNCTION_DEF} nodes.
   */
  public final void setIsGeneratorFunction(boolean isGenerator) {
    putBooleanProp(Prop.GENERATOR_FN, isGenerator);
  }

  /** Returns whether this node is a generator function node. */
  public final boolean isGeneratorFunction() {
    return getBooleanProp(Prop.GENERATOR_FN);
  }

  /**
   * Sets whether this node subtree contains YIELD nodes.
   *
   * <p>It's used in the translation of generators.
   */
  public final void setGeneratorMarker(boolean isGeneratorMarker) {
    putBooleanProp(Prop.IS_GENERATOR_MARKER, isGeneratorMarker);
  }

  /**
   * Returns whether this node was marked as containing YIELD nodes.
   *
   * <p>It's used in the translation of generators.
   */
  public final boolean isGeneratorMarker() {
    return getBooleanProp(Prop.IS_GENERATOR_MARKER);
  }

  /** Set the value for isGeneratorSafe() */
  public final void setGeneratorSafe(boolean isGeneratorSafe) {
    putBooleanProp(Prop.IS_GENERATOR_SAFE, isGeneratorSafe);
  }

  /**
   * Used when translating ES6 generators. If this returns true, this Node was generated by the
   * compiler, and it is safe to copy this node to the transpiled output with no further changes.
   */
  public final boolean isGeneratorSafe() {
    return getBooleanProp(Prop.IS_GENERATOR_SAFE);
  }

  /**
   * Sets whether this node is the start of an optional chain. This method is meaningful only on
   * {@link Token#OPTCHAIN_GETELEM}, {@link Token#OPTCHAIN_GETPROP}, {@link Token#OPTCHAIN_CALL}
   */
  public final void setIsOptionalChainStart(boolean isOptionalChainStart) {
    checkState(
        !isOptionalChainStart || isOptChainGetElem() || isOptChainGetProp() || isOptChainCall(),
        "cannot make a non-optional node the start of an optional chain.");
    putBooleanProp(Prop.START_OF_OPT_CHAIN, isOptionalChainStart);
  }

  /** Returns whether this node is an optional chaining node. */
  public final boolean isOptionalChainStart() {
    return getBooleanProp(Prop.START_OF_OPT_CHAIN);
  }

  /**
   * Sets whether this node is a arrow function node. This method is meaningful only on {@link
   * Token#FUNCTION}
   */
  public final void setIsArrowFunction(boolean isArrow) {
    checkState(isFunction());
    putBooleanProp(Prop.ARROW_FN, isArrow);
  }

  /** Returns whether this node is a arrow function node. */
  public final boolean isArrowFunction() {
    return isFunction() && getBooleanProp(Prop.ARROW_FN);
  }

  /**
   * Sets whether this node is an async function node. This method is meaningful only on {@link
   * Token#FUNCTION}
   */
  public void setIsAsyncFunction(boolean isAsync) {
    checkState(isFunction());
    putBooleanProp(Prop.ASYNC_FN, isAsync);
  }

  /** Returns whether this is an async function node. */
  public final boolean isAsyncFunction() {
    return isFunction() && getBooleanProp(Prop.ASYNC_FN);
  }

  /** Returns whether this is an async generator function node. */
  public final boolean isAsyncGeneratorFunction() {
    return isAsyncFunction() && isGeneratorFunction();
  }

  /**
   * Sets whether this node is a generator node. This method is meaningful only on {@link
   * Token#FUNCTION} or {@link Token#MEMBER_FUNCTION_DEF} nodes.
   */
  public final void setYieldAll(boolean isGenerator) {
    putBooleanProp(Prop.YIELD_ALL, isGenerator);
  }

  /**
   * Returns whether this node is a generator node. This method is meaningful only on {@link
   * Token#FUNCTION} or {@link Token#MEMBER_FUNCTION_DEF} nodes.
   */
  public final boolean isYieldAll() {
    return getBooleanProp(Prop.YIELD_ALL);
  }

  /** Indicates that there was a trailing comma in this list */
  public final void setTrailingComma(boolean hasTrailingComma) {
    putBooleanProp(Prop.TRAILING_COMMA, hasTrailingComma);
  }

  /** Returns true if there was a trailing comma in the orginal code */
  public final boolean hasTrailingComma() {
    return getBooleanProp(Prop.TRAILING_COMMA);
  }

  /**
   * Marks this function or constructor call's side effect flags. This property is only meaningful
   * for {@link Token#CALL} and {@link Token#NEW} nodes.
   */
  public final void setSideEffectFlags(int flags) {
    checkState(
        this.isCall() || this.isOptChainCall() || this.isNew() || this.isTaggedTemplateLit(),
        "Side-effect flags can only be set on invocation nodes; got %s",
        this);

    // We invert the flags before setting because we also invert the them when getting; they go
    // full circle.
    //
    // We apply the mask after inversion so that if all flags are being set (all 1s), it's
    // equivalent to storing a 0, the default value of an int-prop, which has no memory cost.
    putIntProp(Prop.SIDE_EFFECT_FLAGS, ~flags & SideEffectFlags.USED_BITS_MASK);
  }

  public final void setSideEffectFlags(SideEffectFlags flags) {
    setSideEffectFlags(flags.valueOf());
  }

  /** Returns the side effects flags for this node. */
  public final int getSideEffectFlags() {
    // Int props default to 0, but we want the default for side-effect flags to be all 1s.
    // Therefore, we invert the value returned here. This is correct for non-defaults because we
    // also invert when setting the flags.
    return ~getIntProp(Prop.SIDE_EFFECT_FLAGS) & SideEffectFlags.USED_BITS_MASK;
  }

  /**
   * A helper class for getting and setting invocation side-effect flags.
   *
   * <p>The following values are of interest:
   *
   * <ol>
   *   <li>Is global state mutated? ({@code MUTATES_GLOBAL_STATE})
   *   <li>Is the receiver (`this`) mutated? ({@code MUTATES_THIS})
   *   <li>Are any arguments mutated? ({@code MUTATES_ARGUMENTS})
   *   <li>Does the call throw an error? ({@code THROWS})
   * </ol>
   *
   * @author johnlenz@google.com (John Lenz)
   */
  public static final class SideEffectFlags {
    public static final int MUTATES_GLOBAL_STATE = 1;
    public static final int MUTATES_THIS = 2;
    public static final int MUTATES_ARGUMENTS = 4;
    public static final int THROWS = 8;

    private static final int USED_BITS_MASK = (1 << 4) - 1;
    public static final int NO_SIDE_EFFECTS = 0;
    public static final int ALL_SIDE_EFFECTS =
        MUTATES_GLOBAL_STATE | MUTATES_THIS | MUTATES_ARGUMENTS | THROWS;

    // A bitfield indicating the flag statuses. All used bits set to 1 means "global state changes".
    // A value of 0 means "no side effects"
    private int value = ALL_SIDE_EFFECTS;

    public SideEffectFlags() {}

    public SideEffectFlags(int value) {
      this.value = value;
    }

    public int valueOf() {
      return value;
    }

    /** All side-effect occur and the returned results are non-local. */
    public SideEffectFlags setAllFlags() {
      value = ALL_SIDE_EFFECTS;
      return this;
    }

    /** No side-effects occur */
    public SideEffectFlags clearAllFlags() {
      value = NO_SIDE_EFFECTS;
      return this;
    }

    public SideEffectFlags setMutatesGlobalState() {
      // Modify global means everything must be assumed to be modified.
      value |= MUTATES_GLOBAL_STATE | MUTATES_ARGUMENTS | MUTATES_THIS;
      return this;
    }

    public SideEffectFlags setThrows() {
      value |= THROWS;
      return this;
    }

    public SideEffectFlags setMutatesThis() {
      value |= MUTATES_THIS;
      return this;
    }

    public SideEffectFlags setMutatesArguments() {
      value |= MUTATES_ARGUMENTS;
      return this;
    }

    @Override
    @DoNotCall // For debugging only.
    public String toString() {
      StringBuilder builder = new StringBuilder("Side effects: ");

      if ((value & MUTATES_THIS) != 0) {
        builder.append("this ");
      }
      if ((value & MUTATES_GLOBAL_STATE) != 0) {
        builder.append("global ");
      }
      if ((value & THROWS) != 0) {
        builder.append("throw ");
      }
      if ((value & MUTATES_ARGUMENTS) != 0) {
        builder.append("args ");
      }

      return builder.toString();
    }
  }

  /** Returns whether the only side-effect is "modifies this" or there are no side effects. */
  public final boolean isOnlyModifiesThisCall() {
    // TODO(nickreid): Delete this; check if MUTATES_THIS is actually set. This was left in to
    // maintain existing behaviour but it makes the name of this method misleading.
    int sideEffectsBesidesMutatesThis = getSideEffectFlags() & ~SideEffectFlags.MUTATES_THIS;
    return sideEffectsBesidesMutatesThis == SideEffectFlags.NO_SIDE_EFFECTS;
  }

  /** Returns whether the only side-effect is "modifies arguments" or there are no side effects. */
  public final boolean isOnlyModifiesArgumentsCall() {
    // TODO(nickreid): Delete this; check if MUTATES_ARGUMENTS is actually set. This was left in to
    // maintain existing behaviour but it makes the name of this method misleading.
    int sideEffectsBesidesMutatesArguments =
        getSideEffectFlags() & ~SideEffectFlags.MUTATES_ARGUMENTS;
    return sideEffectsBesidesMutatesArguments == SideEffectFlags.NO_SIDE_EFFECTS;
  }

  /** Returns true if this node is a function or constructor call that has no side effects. */
  public final boolean isNoSideEffectsCall() {
    return getSideEffectFlags() == SideEffectFlags.NO_SIDE_EFFECTS;
  }

  /** Returns true if this is a new/call that may mutate its arguments. */
  public final boolean mayMutateArguments() {
    return allBitsSet(getSideEffectFlags(), SideEffectFlags.MUTATES_ARGUMENTS);
  }

  /** Returns true if this is a new/call that may mutate global state or throw. */
  public final boolean mayMutateGlobalStateOrThrow() {
    return anyBitSet(
        getSideEffectFlags(), SideEffectFlags.MUTATES_GLOBAL_STATE | SideEffectFlags.THROWS);
  }

  /** Returns true iff all the set bits in {@code mask} are also set in {@code value}. */
  private static boolean allBitsSet(int value, int mask) {
    return (value & mask) == mask;
  }

  /** Returns true iff any the bit set in {@code mask} is also set in {@code value}. */
  private static boolean anyBitSet(int value, int mask) {
    return (value & mask) != 0;
  }

  /**
   * Constants for the {@link Prop#CONSTANT_VAR_FLAGS} bit set property.
   *
   * <ul>
   *   <li>{@link ConstantVarFlags#DECLARED} means the name was declared using annotation or syntax
   *       indicating it must be constant
   *   <li>{@link ConstantVarFlags#INFERRED} means the compiler can see that it is assigned exactly
   *       once, whether or not it was declared to be constant.
   * </ul>
   *
   * <p>Either, both, or neither may be set for any name.
   */
  private static final class ConstantVarFlags {
    // each constant should be a distinct power of 2.

    static final int DECLARED = 1;
    static final int INFERRED = 2;

    private ConstantVarFlags() {}
  }

  private final int getConstantVarFlags() {
    return getIntProp(Prop.CONSTANT_VAR_FLAGS);
  }

  private final void setConstantVarFlag(int flag, boolean value) {
    int flags = getConstantVarFlags();
    if (value) {
      flags = flags | flag;
    } else {
      flags = flags & ~flag;
    }
    putIntProp(Prop.CONSTANT_VAR_FLAGS, flags);
  }

  /**
   * Returns whether this variable is declared as a constant.
   *
   * <p>The compiler considers a variable to be declared if:
   *
   * <ul>
   *   <li>it is declared with the {@code const} keyword, or
   *   <li>It is declared with a jsdoc {@code @const} annotation, or
   *   <li>The current coding convention considers it to be a constant.
   * </ul>
   *
   * <p>Only valid to call on a {@linkplain #isName name} node.
   */
  public final boolean isDeclaredConstantVar() {
    checkState(
        isName() || isImportStar(),
        "Should only be called on name or import * nodes. Found %s",
        this);
    return anyBitSet(getConstantVarFlags(), ConstantVarFlags.DECLARED);
  }

  /**
   * Sets this variable to be a declared constant.
   *
   * <p>See {@link #isDeclaredConstantVar} for the rules.
   */
  public final void setDeclaredConstantVar(boolean value) {
    checkState(
        isName() || isImportStar(),
        "Should only be called on name or import * nodes. Found %s",
        this);
    setConstantVarFlag(ConstantVarFlags.DECLARED, value);
  }

  /**
   * Returns whether this variable is inferred to be constant.
   *
   * <p>The compiler infers a variable to be a constant if:
   *
   * <ul>
   *   <li>It is assigned at its declaration site, and
   *   <li>It is never reassigned during its lifetime, and
   *   <li>It is not defined by an extern.
   * </ul>
   *
   * <p>Only valid to call on a {@linkplain #isName name} node.
   */
  public final boolean isInferredConstantVar() {
    checkState(
        isName() || isImportStar(),
        "Should only be called on name or import * nodes. Found %s",
        this);
    return anyBitSet(getConstantVarFlags(), ConstantVarFlags.INFERRED);
  }

  /**
   * Sets this variable to be an inferred constant. *
   *
   * <p>See {@link #isInferredConstantVar} for the rules.
   */
  public final void setInferredConstantVar(boolean value) {
    checkState(
        isName() || isImportStar(),
        "Should only be called on name or import * nodes. Found %s",
        this);
    setConstantVarFlag(ConstantVarFlags.INFERRED, value);
  }

  public final boolean isQuotedStringKey() {
    return (this instanceof StringNode) && this.getBooleanProp(Prop.QUOTED);
  }

  public final void setQuotedStringKey() {
    checkState(this instanceof StringNode, this);
    this.putBooleanProp(Prop.QUOTED, true);
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

  public final boolean isAssignAnd() {
    return this.token == Token.ASSIGN_AND;
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
    return isBlock();
  }

  public final boolean isBlock() {
    return this.token == Token.BLOCK;
  }

  public final boolean isRoot() {
    return this.token == Token.ROOT;
  }

  public final boolean isAwait() {
    return this.token == Token.AWAIT;
  }

  public final boolean isBigInt() {
    return this.token == Token.BIGINT;
  }

  public final boolean isBitNot() {
    return this.token == Token.BITNOT;
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

  public final boolean isExponent() {
    return this.token == Token.EXPONENT;
  }

  public final boolean isAssignExponent() {
    return this.token == Token.ASSIGN_EXPONENT;
  }

  public final boolean isExport() {
    return this.token == Token.EXPORT;
  }

  public final boolean isExportSpec() {
    return this.token == Token.EXPORT_SPEC;
  }

  public final boolean isExportSpecs() {
    return this.token == Token.EXPORT_SPECS;
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

  public final boolean isForAwaitOf() {
    return this.token == Token.FOR_AWAIT_OF;
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

  public final boolean isImportMeta() {
    return this.token == Token.IMPORT_META;
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

  public final boolean isInterface() {
    return this.token == Token.INTERFACE;
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

  public final boolean isMemberFieldDef() {
    return this.token == Token.MEMBER_FIELD_DEF;
  }

  public final boolean isComputedFieldDef() {
    return this.token == Token.COMPUTED_FIELD_DEF;
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

  public final boolean isSHNE() {
    return this.token == Token.SHNE;
  }

  public final boolean isEQ() {
    return this.token == Token.EQ;
  }

  public final boolean isSHEQ() {
    return this.token == Token.SHEQ;
  }

  public final boolean isNeg() {
    return this.token == Token.NEG;
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

  public final boolean isNullishCoalesce() {
    return this.token == Token.COALESCE;
  }

  public final boolean isAssignNullishCoalesce() {
    return this.token == Token.ASSIGN_COALESCE;
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

  public final boolean isOptChainCall() {
    return this.token == Token.OPTCHAIN_CALL;
  }

  public final boolean isOptChainGetElem() {
    return this.token == Token.OPTCHAIN_GETELEM;
  }

  public final boolean isOptChainGetProp() {
    return this.token == Token.OPTCHAIN_GETPROP;
  }

  public final boolean isOr() {
    return this.token == Token.OR;
  }

  public final boolean isAssignOr() {
    return this.token == Token.ASSIGN_OR;
  }

  public final boolean isParamList() {
    return this.token == Token.PARAM_LIST;
  }

  public final boolean isRegExp() {
    return this.token == Token.REGEXP;
  }

  public final boolean isRest() {
    return this.token == Token.ITER_REST || this.token == Token.OBJECT_REST;
  }

  public final boolean isObjectRest() {
    return this.token == Token.OBJECT_REST;
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
    return this.token == Token.ITER_SPREAD || this.token == Token.OBJECT_SPREAD;
  }

  public final boolean isString() {
    return this.token == Token.STRINGLIT;
  }

  public final boolean isStringKey() {
    return this.token == Token.STRING_KEY;
  }

  public final boolean isStringLit() {
    return this.token == Token.STRINGLIT;
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

  public final boolean isTemplateLitString() {
    return this.token == Token.TEMPLATELIT_STRING;
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

  public final boolean isDeclare() {
    return this.token == Token.DECLARE;
  }
}
