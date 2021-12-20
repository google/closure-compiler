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

package com.google.javascript.rhino;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.nullToEmpty;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * JSDoc information describing JavaScript code. JSDoc is represented as a unified object with
 * fields for each JSDoc annotation, even though some combinations are incorrect. For instance, if a
 * JSDoc describes an enum, it cannot have information about a return type. This implementation
 * takes advantage of such incompatibilities to reuse fields for multiple purposes, reducing memory
 * consumption.
 *
 * <p>Constructing {@link JSDocInfo} objects is simplified by {@link JSDocInfo.Builder} which
 * provides early incompatibility detection.
 */
public class JSDocInfo implements Serializable {
  private static final long serialVersionUID = 1L;

  private static class Property<T> implements Comparable<Property<Object>> {
    private static int bitCounter = 0;
    static final Property<?>[] values = new Property<?>[64];

    final String name;
    final int bit;
    final long mask;

    private Property(String name) {
      this.name = name;
      this.bit = bitCounter++;
      this.mask = 1L << this.bit;
      if (this.bit > 63) {
        throw new AssertionError("Too many Properties");
      }
      values[this.bit] = this;
    }

    @Nullable
    @SuppressWarnings("unchecked") // cast to T is unsafe but guaranteed by builder
    T get(JSDocInfo info) {
      if ((info.propertyKeysBitset & mask) == 0) {
        return null;
      }
      return (T) info.propertyValues.get(Long.bitCount(info.propertyKeysBitset & (mask - 1)));
    }

    T clone(T arg, @Nullable TypeTransform transform) {
      return arg;
    }

    boolean isDefault(T value) {
      return value == null
          || value == Visibility.INHERITED
          || (value instanceof Collection && ((Collection<?>) value).isEmpty())
          || (value instanceof Map && ((Map<?, ?>) value).isEmpty());
    }

    boolean equalValues(T left, T right) {
      return left.equals(right);
    }

    Iterable<JSTypeExpression> getTypeExpressions(T value) {
      return ImmutableList.of();
    }

    // NOTE: These need to be comparable so that we can iterate over them to build the list.
    @Override
    public int compareTo(Property<Object> that) {
      return this.bit - that.bit;
    }
  }

  private static final class MarkerListProperty extends Property<ArrayList<Marker>> {
    MarkerListProperty(String name) {
      super(name);
    }

    @Override
    boolean equalValues(ArrayList<Marker> a, ArrayList<Marker> b) {
      if (a.size() != b.size()) {
        return false;
      }
      for (int i = 0; i < a.size(); i++) {
        Marker m1 = a.get(i);
        Marker m2 = b.get(i);
        if ((m1 == null) != (m2 == null) || (m1 != null && !m1.isEquivalentTo(m2))) {
          return false;
        }
      }
      return true;
    }
    // NOTE: documentation props are never cloned
  }

  private static final class TypeProperty extends Property<JSTypeExpression> {
    TypeProperty(String name) {
      super(name);
    }

    @Override
    JSTypeExpression clone(JSTypeExpression arg, TypeTransform transform) {
      return arg != null && transform != null ? transform.apply(arg) : arg;
    }

    @Override
    boolean equalValues(JSTypeExpression left, JSTypeExpression right) {
      return left.isEquivalentTo(right);
    }

    @Override
    ImmutableList<JSTypeExpression> getTypeExpressions(JSTypeExpression type) {
      return ImmutableList.of(type);
    }
  }

  private static final class TypeListProperty extends Property<ArrayList<JSTypeExpression>> {
    TypeListProperty(String name) {
      super(name);
    }

    List<JSTypeExpression> getUnmodifiable(JSDocInfo info) {
      ArrayList<JSTypeExpression> value = get(info);
      return value != null ? Collections.unmodifiableList(value) : ImmutableList.of();
    }

    @Override
    ArrayList<JSTypeExpression> clone(ArrayList<JSTypeExpression> arg, TypeTransform transform) {
      ArrayList<JSTypeExpression> out = new ArrayList<>(arg);
      if (transform != null) {
        for (int i = 0; i < out.size(); i++) {
          JSTypeExpression elem = out.get(i);
          if (elem != null) {
            out.set(i, transform.apply(elem));
          }
        }
      }
      return out;
    }

    @Override
    boolean equalValues(ArrayList<JSTypeExpression> left, ArrayList<JSTypeExpression> right) {
      if (left.size() != right.size()) {
        return false;
      }
      final Iterator<JSTypeExpression> leftIterator = left.iterator();
      final Iterator<JSTypeExpression> rightIterator = right.iterator();
      while (leftIterator.hasNext()) {
        final JSTypeExpression leftExpr = leftIterator.next();
        final JSTypeExpression rightExpr = rightIterator.next();
        if (!leftExpr.isEquivalentTo(rightExpr)) {
          return false;
        }
      }
      return true;
    }

    @Override
    Iterable<JSTypeExpression> getTypeExpressions(ArrayList<JSTypeExpression> types) {
      return types;
    }
  }

  private static final class TypeMapProperty
      extends Property<LinkedHashMap<String, JSTypeExpression>> {
    TypeMapProperty(String name) {
      super(name);
    }

    @Override
    LinkedHashMap<String, JSTypeExpression> clone(
        LinkedHashMap<String, JSTypeExpression> arg, TypeTransform transform) {
      LinkedHashMap<String, JSTypeExpression> out = new LinkedHashMap<>(arg);
      if (transform != null) {
        for (Map.Entry<String, JSTypeExpression> entry : out.entrySet()) {
          JSTypeExpression elem = entry.getValue();
          if (elem != null) {
            entry.setValue(transform.apply(elem));
          }
        }
      }
      return out;
    }

    @Override
    boolean equalValues(
        LinkedHashMap<String, JSTypeExpression> left,
        LinkedHashMap<String, JSTypeExpression> right) {
      final Set<String> leftKeys = left.keySet();
      final Set<String> rightKeys = right.keySet();
      if (!leftKeys.equals(rightKeys)) {
        return false;
      }
      for (String key : leftKeys) {
        final JSTypeExpression leftExpr = left.get(key);
        final JSTypeExpression rightExpr = right.get(key);
        if (!areEquivalent(leftExpr, rightExpr)) {
          return false;
        }
      }
      return true;
    }

    private boolean areEquivalent(JSTypeExpression expr1, JSTypeExpression expr2) {
      return Objects.equals(expr1, expr2) || (expr1 != null && expr1.isEquivalentTo(expr2));
    }

    @Override
    Iterable<JSTypeExpression> getTypeExpressions(LinkedHashMap<String, JSTypeExpression> map) {
      return map.values();
    }
  }

  private static final class NodeMapProperty extends Property<LinkedHashMap<String, Node>> {
    NodeMapProperty(String name) {
      super(name);
    }

    @Override
    LinkedHashMap<String, Node> clone(LinkedHashMap<String, Node> arg, TypeTransform transform) {
      return new LinkedHashMap<>(arg);
    }

    @Override
    boolean equalValues(LinkedHashMap<String, Node> left, LinkedHashMap<String, Node> right) {
      if (left.size() != right.size()) {
        return false;
      }
      for (Map.Entry<String, Node> entry : left.entrySet()) {
        Node rightValue = right.get(entry.getKey());
        if (rightValue == null || !entry.getValue().isEquivalentTo(rightValue)) {
          return false;
        }
      }
      return true;
    }
  }

  private static enum Bit {
    /** Whether the type annotation was inlined. */
    INLINE_TYPE,
    /** Whether to include documentation. */
    INCLUDE_DOCUMENTATION,

    // The following are all straightforward annotations.
    CONST,
    CONSTRUCTOR,
    DEFINE,
    HIDDEN,
    TYPE_SUMMARY,
    FINAL,
    OVERRIDE,

    DEPRECATED,
    INTERFACE,
    EXPORT,
    ENHANCED_NAMESPACE,
    NOINLINE,
    FILEOVERVIEW,
    IMPLICITCAST,
    NOSIDEEFFECTS,
    EXTERNS,
    NOCOMPILE,
    UNRESTRICTED,
    STRUCT,
    DICT,
    NOCOLLAPSE,
    RECORD,
    ABSTRACT,
    PURE_OR_BREAK_MY_CODE,
    COLLAPSIBLE_OR_BREAK_MY_CODE,

    NG_INJECT,
    WIZ_ACTION,
    POLYMER_BEHAVIOR,
    POLYMER,
    CUSTOM_ELEMENT,
    MIXIN_CLASS,
    MIXIN_FUNCTION,

    LOCALE_FILE,
    LOCALE_SELECT,
    LOCALE_OBJECT,
    LOCALE_VALUE,

    // `@provideGoog` only appears in base.js
    PROVIDE_GOOG;

    final String name;
    final long mask;

    private Bit() {
      if (ordinal() > 63) {
        throw new AssertionError("Too many Bits");
      }
      this.name = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name());
      this.mask = 1L << ordinal();
    }
  }

  @FunctionalInterface
  private interface TypeTransform {
    JSTypeExpression apply(JSTypeExpression arg);
  }

  private final long propertyBits;
  private final long propertyKeysBitset;
  private final ImmutableList<Object> propertyValues;

  @SuppressWarnings("unchecked")
  private JSDocInfo(long bits, TreeMap<Property<?>, Object> props) {
    long keys = 0;
    ImmutableList.Builder<Object> values = ImmutableList.builder();
    for (Map.Entry<Property<?>, Object> entry : props.entrySet()) {
      Property<Object> prop = (Property<Object>) entry.getKey();
      Object value = entry.getValue();
      if (!prop.isDefault(value)) {
        keys |= prop.mask;
        values.add(value);
      } else {
      }
    }
    this.propertyBits = bits;
    this.propertyKeysBitset = keys;
    this.propertyValues = values.build();
  }

  private boolean checkBit(Bit bit) {
    return (propertyBits & bit.mask) != 0;
  }

  private TreeMap<Property<?>, Object> asMap() {
    TreeMap<Property<?>, Object> map = new TreeMap<>();
    long bits = propertyKeysBitset;
    int index = 0;
    while (bits > 0) {
      int low = Long.numberOfTrailingZeros(bits);
      bits &= ~(1L << low);
      map.put(Property.values[low], propertyValues.get(index++));
    }
    return map;
  }

  /**
   * Visibility categories. The {@link Visibility#ordinal()} can be used as a numerical indicator of
   * privacy, where 0 is the most private. This means that the {@link Visibility#compareTo} method
   * can be used to determine if a visibility is more permissive than another.
   */
  public enum Visibility {
    PRIVATE,
    PACKAGE,
    PROTECTED,
    PUBLIC,

    // If visibility is not specified, we just assume that visibility
    // is inherited from the super class.  This should never be included
    // in the map of an actual built JSDocInfo (though it may be in the
    // builder), but will be returned by default if none is specified.
    INHERITED,
  }

  private enum IdGenerator {
    XID,
    CONSISTENT,
    UNIQUE,
    STABLE,
    MAPPED,
  }

  private static final Property<Visibility> VISIBILITY = new Property<>("visibility");
  private static final TypeProperty TYPE = new TypeProperty("type");
  private static final TypeProperty RETURN_TYPE = new TypeProperty("returnType");
  private static final TypeProperty ENUM_PARAMETER_TYPE = new TypeProperty("enumParameterType");
  private static final TypeProperty TYPEDEF_TYPE = new TypeProperty("typedefType");
  private static final TypeProperty THIS_TYPE = new TypeProperty("thisType");
  private static final Property<Integer> ORIGINAL_COMMENT_POSITION =
      new Property<>("originalCommentPosition");
  private static final Property<IdGenerator> ID_GENERATOR = new Property<>("idGenerator");
  private static final TypeProperty BASE_TYPE = new TypeProperty("baseType");
  private static final TypeListProperty EXTENDED_INTERFACES =
      new TypeListProperty("extendedInterfaces");
  private static final TypeListProperty IMPLEMENTED_INTERFACES =
      new TypeListProperty("extendedInterfaces");
  private static final TypeMapProperty PARAMETERS = new TypeMapProperty("parameters");
  private static final Property<List<String>> THROWS_ANNOTATIONS = new Property<>("throws");
  private static final TypeMapProperty TEMPLATE_TYPE_NAMES =
      new TypeMapProperty("templateTypeNames");
  private static final NodeMapProperty TYPE_TRANSFORMATIONS =
      new NodeMapProperty("typeTransformations");

  private static final Property<String> DESCRIPTION = new Property<>("description");
  private static final Property<String> MEANING = new Property<>("meaning");
  private static final Property<String> ALTERNATE_MESSAGE_ID = new Property<>("alternateMessageId");
  private static final Property<String> DEPRECATION_REASON = new Property<>("deprecationReason");
  private static final Property<String> LICENSE = new Property<>("license");

  // For example, `@suppress {first, second} Some description applying to the set (of both first and
  // second).`
  // For example, `@suppress {first} Some description applying to only to first.`
  private static final Property<ImmutableMap<ImmutableSet<String>, String>> SUPPRESSIONS =
      new Property<>("suppressions");
  private static final Property<ImmutableSet<String>> MODIFIES = new Property<>("modifies");
  private static final TypeProperty LENDS_NAME = new TypeProperty("lendsName");
  private static final Property<String> CLOSURE_PRIMITIVE_ID = new Property<>("closurePrimitiveId");

  // NOTE: The following properties are "documentation properties", which do _not_ get deeply copied
  // when JSDocInfo is cloned (i.e. none of these Properties override {@link Property#clone}).
  private static final Property<String> SOURCE_COMMENT = new Property<>("sourceComment");
  private static final Property<ArrayList<Marker>> MARKERS = new MarkerListProperty("markers");
  private static final Property<LinkedHashMap<String, String>> PARAMETER_DESCRIPTIONS =
      new Property<>("parameterDescriptions");
  private static final Property<String> BLOCK_DESCRIPTION = new Property<>("blockDescription");
  private static final Property<String> FILEOVERVIEW_DESCRIPTION =
      new Property<>("fileoverviewDescription");
  private static final Property<String> RETURN_DESCRIPTION = new Property<>("returnDescription");
  private static final Property<String> VERSION = new Property<>("version");
  private static final Property<String> ENHANCED_NAMESPACE = new Property<>("enhance");

  private static final Property<List<String>> AUTHORS = new Property<>("authors");
  private static final Property<List<String>> SEES = new Property<>("sees");

  private abstract static class ComparableSourcePosition<T> extends SourcePosition<T> {
    abstract boolean isEquivalentTo(SourcePosition<T> that);
  }

  /** A piece of information (found in a marker) which contains a position with a string. */
  public static class StringPosition extends ComparableSourcePosition<String> {
    @Override
    boolean isEquivalentTo(SourcePosition<String> that) {
      return that != null && isSamePositionAs(that) && Objects.equals(getItem(), that.getItem());
    }
  }

  /**
   * A piece of information (found in a marker) which contains a position with a string that has no
   * leading or trailing whitespace.
   */
  static class TrimmedStringPosition extends StringPosition {
    @Override
    public void setItem(String item) {
      checkArgument(
          item.charAt(0) != ' ' && item.charAt(item.length() - 1) != ' ',
          "String has leading or trailing whitespace");
      super.setItem(item);
    }
  }

  /** A piece of information (found in a marker) which contains a position with a name node. */
  public static class NamePosition extends ComparableSourcePosition<Node> {
    @Override
    boolean isEquivalentTo(SourcePosition<Node> that) {
      if (that == null
          || !isSamePositionAs(that)
          || (getItem() == null) != (that.getItem() == null)) {
        return false;
      }
      return getItem() == null || getItem().isEquivalentTo(that.getItem());
    }
  }

  /**
   * A piece of information (found in a marker) which contains a position with a type expression
   * syntax tree.
   */
  public static class TypePosition extends ComparableSourcePosition<Node> {
    private boolean brackets = false;

    /** Returns whether the type has curly braces around it. */
    public boolean hasBrackets() {
      return brackets;
    }

    void setHasBrackets(boolean newVal) {
      brackets = newVal;
    }

    @Override
    boolean isEquivalentTo(SourcePosition<Node> that) {
      if (!(that instanceof TypePosition)
          || !isSamePositionAs(that)
          || brackets != ((TypePosition) that).brackets
          || (getItem() == null) != (that.getItem() == null)) {
        return false;
      }
      return getItem() == null || getItem().isEquivalentTo(that.getItem());
    }
  }

  /**
   * Defines a class for containing the parsing information for this JSDocInfo. For each annotation
   * found in the JsDoc, a marker will be created indicating the annotation itself, the name of the
   * annotation (if any; for example, a @param has a name, but a @return does not), the textual
   * description found on that annotation and, if applicable, the type declaration. All this
   * information is only collected if documentation collection is turned on.
   */
  public static final class Marker {
    private TrimmedStringPosition annotation;
    private NamePosition nameNode;
    private StringPosition description;
    private TypePosition type;

    /** Gets the position information for the annotation name. (e.g., "param") */
    public StringPosition getAnnotation() {
      return annotation;
    }

    void setAnnotation(TrimmedStringPosition p) {
      annotation = p;
    }

    /** Gets the position information for the name found in an @param tag. */
    public NamePosition getNameNode() {
      return nameNode;
    }

    void setNameNode(NamePosition p) {
      nameNode = p;
    }

    /** Gets the position information for the description found in a block tag. */
    public StringPosition getDescription() {
      return description;
    }

    void setDescription(StringPosition p) {
      description = p;
    }

    /**
     * Gets the position information for the type expression found in some block tags, like "@param"
     * and "@return".
     */
    public TypePosition getType() {
      return type;
    }

    void setType(TypePosition p) {
      type = p;
    }

    private boolean isEquivalentTo(Marker that) {
      return areEquivalent(this.annotation, that.annotation)
          && areEquivalent(this.nameNode, that.nameNode)
          && areEquivalent(this.description, that.description)
          && areEquivalent(this.type, that.type);
    }

    private static <T> boolean areEquivalent(
        @Nullable ComparableSourcePosition<T> p1, @Nullable ComparableSourcePosition<T> p2) {
      return (p1 == null) == (p2 == null) && (p1 == null || p1.isEquivalentTo(p2));
    }
  }

  /** Create a new JSDocInfo.Builder object. */
  public static Builder builder() {
    return new Builder();
  }

  public Builder toBuilder() {
    return toBuilder(null);
  }

  @SuppressWarnings("unchecked")
  private Builder toBuilder(TypeTransform transform) {
    Builder builder = new Builder();
    // inline type isn't copied...?
    builder.bits = propertyBits & ~Bit.INLINE_TYPE.mask;
    builder.props = asMap();
    builder.populated = true;
    for (Map.Entry<Property<?>, Object> entry : builder.props.entrySet()) {
      entry.setValue(((Property<Object>) entry.getKey()).clone(entry.getValue(), transform));
    }
    return builder;
  }

  @SuppressWarnings("MissingOverride") // Adding @Override breaks the GWT compilation.
  public JSDocInfo clone() {
    return clone(false);
  }

  /**
   * Clones this JSDoc but replaces the given names in any type related annotation with unknown
   * type.
   *
   * @return returns the the cloned JSDocInfo
   */
  public JSDocInfo cloneAndReplaceTypeNames(Set<String> names) {
    return toBuilder((type) -> type.replaceNamesWithUnknownType(names)).build();
  }

  public JSDocInfo clone(boolean cloneTypeNodes) {
    return cloneTypeNodes ? toBuilder(JSTypeExpression::copy).build() : toBuilder().build();
  }

  @VisibleForTesting
  @SuppressWarnings("unchecked")
  public static boolean areEquivalent(JSDocInfo jsDoc1, JSDocInfo jsDoc2) {
    if ((jsDoc1 == null) != (jsDoc2 == null)) {
      return false;
    } else if (jsDoc1 == null) {
      return true;
    }
    if (((jsDoc1.propertyBits ^ jsDoc2.propertyBits) & ~EQUIVALENCE_IGNORED_BITS) != 0) {
      return false;
    }
    if (jsDoc1.propertyKeysBitset != jsDoc2.propertyKeysBitset) {
      return false;
    }
    long bits = jsDoc1.propertyKeysBitset;
    int index = 0;
    while (bits > 0) {
      int low = Long.numberOfTrailingZeros(bits);
      bits &= ~(1L << low);
      Property<Object> prop = (Property<Object>) Property.values[low];
      Object a = jsDoc1.propertyValues.get(index);
      Object b = jsDoc2.propertyValues.get(index++);
      if ((a == null) != (b == null) || (a != null && !prop.equalValues(a, b))) {
        return false;
      }
    }
    return true;
  }

  // NOTE: includeDocumentation and inlineType are not part of equivalence.
  private static final long EQUIVALENCE_IGNORED_BITS =
      Bit.INCLUDE_DOCUMENTATION.mask | Bit.INLINE_TYPE.mask;

  boolean isDocumentationIncluded() {
    return checkBit(Bit.INCLUDE_DOCUMENTATION);
  }

  /** Returns whether any {@code @idGenerator} or {@code idGenerator {?}} annotation is present. */
  public boolean isAnyIdGenerator() {
    return (propertyKeysBitset & ID_GENERATOR.mask) != 0;
  }

  /** Returns whether the {@code @idGenerator {consistent}} is present on this {@link JSDocInfo}. */
  public boolean isConsistentIdGenerator() {
    return ID_GENERATOR.get(this) == IdGenerator.CONSISTENT;
  }

  /** Returns whether the {@code @idGenerator {stable}} is present on this {@link JSDocInfo}. */
  public boolean isStableIdGenerator() {
    return ID_GENERATOR.get(this) == IdGenerator.STABLE;
  }

  /** Returns whether the {@code @idGenerator {xid}} is present on this {@link JSDocInfo}. */
  public boolean isXidGenerator() {
    return ID_GENERATOR.get(this) == IdGenerator.XID;
  }

  /** Returns whether the {@code @idGenerator {mapped}} is present on this {@link JSDocInfo}. */
  public boolean isMappedIdGenerator() {
    return ID_GENERATOR.get(this) == IdGenerator.MAPPED;
  }

  /** Returns whether the {@code @idGenerator} is present on this {@link JSDocInfo}. */
  public boolean isIdGenerator() {
    return ID_GENERATOR.get(this) == IdGenerator.UNIQUE;
  }

  /** Returns whether this {@link JSDocInfo} implies that annotated value is constant. */
  public boolean isConstant() {
    // @desc is used with goog.getMsg to define messages to be translated,
    // and thus must be @const in order for translation to work correctly.
    return (propertyBits & (Bit.CONST.mask | Bit.DEFINE.mask | Bit.FINAL.mask)) != 0
        || (propertyKeysBitset & DESCRIPTION.mask) != 0;
  }

  /** Returns whether the {@code @const} annotation is present on this {@link JSDocInfo}. */
  public boolean hasConstAnnotation() {
    return checkBit(Bit.CONST);
  }

  /** Returns whether the {@code @final} annotation is present on this {@link JSDocInfo}. */
  public boolean isFinal() {
    return checkBit(Bit.FINAL);
  }

  /** Returns whether the {@code @constructor} annotation is present on this {@link JSDocInfo}. */
  public boolean isConstructor() {
    return checkBit(Bit.CONSTRUCTOR);
  }

  /** Returns whether the {@code @abstract} annotation is present on this {@link JSDocInfo}. */
  public boolean isAbstract() {
    return checkBit(Bit.ABSTRACT);
  }

  /** Returns whether the {@code @record} annotation is present on this {@link JSDocInfo}. */
  public boolean usesImplicitMatch() {
    return checkBit(Bit.RECORD);
  }

  /** Returns whether the {@code @unrestricted} annotation is present on this {@link JSDocInfo}. */
  public boolean makesUnrestricted() {
    return checkBit(Bit.UNRESTRICTED);
  }

  /** Returns whether the {@code @struct} annotation is present on this {@link JSDocInfo}. */
  public boolean makesStructs() {
    return checkBit(Bit.STRUCT);
  }

  /** Returns whether the {@code @dict} annotation is present on this {@link JSDocInfo}. */
  public boolean makesDicts() {
    return checkBit(Bit.DICT);
  }

  /**
   * Returns whether the {@code @define} annotation is present on this {@link JSDocInfo}. If this
   * annotation is present, then the {@link #getType()} method will retrieve the define type.
   */
  public boolean isDefine() {
    return checkBit(Bit.DEFINE);
  }

  /** Returns whether the {@code @hidden} annotation is present on this {@link JSDocInfo}. */
  public boolean isHidden() {
    return checkBit(Bit.HIDDEN);
  }

  /** Returns whether the {@code @override} annotation is present on this {@link JSDocInfo}. */
  public boolean isOverride() {
    return checkBit(Bit.OVERRIDE);
  }

  /** Returns whether the {@code @deprecated} annotation is present on this {@link JSDocInfo}. */
  public boolean isDeprecated() {
    return checkBit(Bit.DEPRECATED);
  }

  /** Returns whether the {@code @interface} annotation is present on this {@link JSDocInfo}. */
  public boolean isInterface() {
    return (propertyBits & (Bit.INTERFACE.mask | Bit.RECORD.mask)) != 0;
  }

  public boolean isConstructorOrInterface() {
    return (propertyBits & (Bit.CONSTRUCTOR.mask | Bit.INTERFACE.mask | Bit.RECORD.mask)) != 0;
  }

  /** Returns whether the {@code @export} annotation is present on this {@link JSDocInfo}. */
  public boolean isExport() {
    return checkBit(Bit.EXPORT);
  }

  /** Returns whether the {@code @implicitCast} annotation is present on this {@link JSDocInfo}. */
  public boolean isImplicitCast() {
    return checkBit(Bit.IMPLICITCAST);
  }

  /** Returns whether the {@code @nosideeffects} annotation is present on this {@link JSDocInfo}. */
  public boolean isNoSideEffects() {
    return checkBit(Bit.NOSIDEEFFECTS);
  }

  /** Returns whether the {@code @externs} annotation is present on this {@link JSDocInfo}. */
  public boolean isExterns() {
    return checkBit(Bit.EXTERNS);
  }

  /** Returns whether the {@code @typeSummary} annotation is present on this {@link JSDocInfo}. */
  public boolean isTypeSummary() {
    return checkBit(Bit.TYPE_SUMMARY);
  }

  /** Returns whether the {@code @nocompile} annotation is present on this {@link JSDocInfo}. */
  public boolean isNoCompile() {
    return checkBit(Bit.NOCOMPILE);
  }

  /** Returns whether the {@code @nocollapse} annotation is present on this {@link JSDocInfo}. */
  public boolean isNoCollapse() {
    return checkBit(Bit.NOCOLLAPSE);
  }

  /** Returns whether the {@code @noinline} annotation is present on this {@link JSDocInfo}. */
  public boolean isNoInline() {
    return checkBit(Bit.NOINLINE);
  }

  /**
   * Returns whether the {@code @collapsibleOrBreakMyCode} annotation is present on this {@link
   * JSDocInfo}.
   */
  public boolean isCollapsibleOrBreakMyCode() {
    return checkBit(Bit.COLLAPSIBLE_OR_BREAK_MY_CODE);
  }

  /**
   * Returns whether the {@code @pureOrBreakMyCode} annotation is present on this {@link JSDocInfo}.
   */
  public boolean isPureOrBreakMyCode() {
    return checkBit(Bit.PURE_OR_BREAK_MY_CODE);
  }

  /** Returns whether the {@code @localeFile} annotation is present on this {@link JSDocInfo}. */
  public boolean isLocaleFile() {
    return checkBit(Bit.LOCALE_FILE);
  }

  /** Returns whether the {@code @localeFile} annotation is present on this {@link JSDocInfo}. */
  public boolean isLocaleSelect() {
    return checkBit(Bit.LOCALE_SELECT);
  }

  /** Returns whether the {@code @localeFile} annotation is present on this {@link JSDocInfo}. */
  public boolean isLocaleObject() {
    return checkBit(Bit.LOCALE_OBJECT);
  }

  /** Returns whether the {@code @localeFile} annotation is present on this {@link JSDocInfo}. */
  public boolean isLocaleValue() {
    return checkBit(Bit.LOCALE_VALUE);
  }

  /** Returns whether the {@code @provideGoog} annotation is present on this {@link JSDocInfo}. */
  public boolean isProvideGoog() {
    return checkBit(Bit.PROVIDE_GOOG);
  }

  /**
   * Returns whether there is a declaration present on this {@link JSDocInfo}.
   *
   * <p>Does not consider `@const` (without a following type) to indicate a declaration. Whether you
   * want this method, or the`containsDeclaration` that includes const, depends on whether you want
   * to consider {@code /** @const * / a.b.c = 0} a declaration or not.
   */
  public boolean containsDeclarationExcludingTypelessConst() {
    return (propertyBits
                & (Bit.CONSTRUCTOR.mask
                    | Bit.DEFINE.mask
                    | Bit.OVERRIDE.mask
                    | Bit.EXPORT.mask
                    | Bit.DEPRECATED.mask
                    | Bit.INTERFACE.mask
                    | Bit.IMPLICITCAST.mask
                    | Bit.NOSIDEEFFECTS.mask
                    | Bit.RECORD.mask))
            != 0
        || (propertyKeysBitset
                & (TYPE.mask
                    | RETURN_TYPE.mask
                    | ENUM_PARAMETER_TYPE.mask
                    | TYPEDEF_TYPE.mask
                    | THIS_TYPE.mask
                    | PARAMETERS.mask
                    | IMPLEMENTED_INTERFACES.mask
                    | BASE_TYPE.mask
                    | VISIBILITY.mask))
            != 0;
  }

  /** Returns whether this JSDoc contains a type declaration such as {@code /** @type {string}} */
  public boolean containsTypeDeclaration() {
    return (propertyKeysBitset
            & (TYPE.mask
                | RETURN_TYPE.mask
                | ENUM_PARAMETER_TYPE.mask
                | TYPEDEF_TYPE.mask
                | THIS_TYPE.mask
                | PARAMETERS.mask
                | BASE_TYPE.mask))
        != 0;
  }

  /**
   * Returns whether there is a declaration present on this {@link JSDocInfo}, including a
   * typeless @const like {@code /** @const * / a.b.c = 0}
   */
  public boolean containsDeclaration() {
    return containsDeclarationExcludingTypelessConst() || checkBit(Bit.CONST);
  }

  /**
   * @deprecated This method is quite heuristic, looking for @type annotations that start with
   *     "function". Other methods like containsDeclaration() and containsTypeDefinition are
   *     generally preferred.
   * @return Whether there is a declaration of a callable type.
   */
  @Deprecated
  public boolean containsFunctionDeclaration() {
    if (checkBit(Bit.CONSTRUCTOR)
        || (propertyKeysBitset & (RETURN_TYPE.mask | THIS_TYPE.mask | PARAMETERS.mask)) != 0) {
      return true;
    }
    JSTypeExpression type = TYPE.get(this);
    if (type != null) {
      return type.getRoot().isFunction();
    }
    return checkBit(Bit.NOSIDEEFFECTS);
  }

  // For jsdocs that create new types. Not to be confused with jsdocs that
  // declare the type of a variable or property.
  public boolean containsTypeDefinition() {
    return (propertyBits & (Bit.CONSTRUCTOR.mask | Bit.INTERFACE.mask)) != 0
        || (propertyKeysBitset & (ENUM_PARAMETER_TYPE.mask | TYPEDEF_TYPE.mask)) != 0;
  }

  /** @return whether the {@code @code} is present within this {@link JSDocInfo}. */
  public boolean isAtSignCodePresent() {
    final String entireComment = getOriginalCommentString();
    return (entireComment == null) ? false : entireComment.contains("@code");
  }

  /**
   * Gets the visibility specified by {@code @private}, {@code @protected} or {@code @public}
   * annotation. If no visibility is specified, visibility is inherited from the base class.
   */
  public Visibility getVisibility() {
    Visibility visibility = VISIBILITY.get(this);
    return visibility != null ? visibility : Visibility.INHERITED;
  }

  /**
   * Gets the type of a given named parameter.
   *
   * @param parameter the parameter's name
   * @return the parameter's type or {@code null} if this parameter is not defined or has a {@code
   *     null} type
   */
  public JSTypeExpression getParameterType(String parameter) {
    LinkedHashMap<String, JSTypeExpression> params = PARAMETERS.get(this);
    return params != null ? params.get(parameter) : null;
  }

  /** Returns whether the parameter is defined. */
  public boolean hasParameter(String parameter) {
    LinkedHashMap<String, JSTypeExpression> params = PARAMETERS.get(this);
    return params != null && params.containsKey(parameter);
  }

  /**
   * Returns whether the parameter has an attached type.
   *
   * @return {@code true} if the parameter has an attached type, {@code false} if the parameter has
   *     no attached type or does not exist.
   */
  public boolean hasParameterType(String parameter) {
    return getParameterType(parameter) != null;
  }

  /**
   * Returns the set of names of the defined parameters. The iteration order of the returned set is
   * the order in which parameters are defined in the JSDoc, rather than the order in which the
   * function declares them.
   *
   * @return the set of names of the defined parameters. The returned set is immutable.
   */
  public Set<String> getParameterNames() {
    LinkedHashMap<String, JSTypeExpression> params = PARAMETERS.get(this);
    return params != null ? ImmutableSet.copyOf(params.keySet()) : ImmutableSet.of();
  }

  /**
   * Returns the nth name in the defined parameters. The iteration order is the order in which
   * parameters are defined in the JSDoc, rather than the order in which the function declares them.
   */
  public String getParameterNameAt(int index) {
    LinkedHashMap<String, JSTypeExpression> params = PARAMETERS.get(this);
    if (params == null || index >= params.size()) {
      return null;
    }
    return Iterables.get(params.keySet(), index);
  }

  /** Gets the number of parameters defined. */
  public int getParameterCount() {
    LinkedHashMap<String, JSTypeExpression> params = PARAMETERS.get(this);
    return params != null ? params.size() : 0;
  }

  /** Returns the list of thrown types and descriptions as text. */
  public List<String> getThrowsAnnotations() {
    List<String> annotations = THROWS_ANNOTATIONS.get(this);
    return annotations != null ? annotations : ImmutableList.of();
  }

  /**
   * Returns whether an enum parameter type, specified using the {@code @enum} annotation, is
   * present on this JSDoc.
   */
  public boolean hasEnumParameterType() {
    return ENUM_PARAMETER_TYPE.get(this) != null;
  }

  /**
   * Returns whether a typedef parameter type, specified using the {@code @typedef} annotation, is
   * present on this JSDoc.
   */
  public boolean hasTypedefType() {
    return TYPEDEF_TYPE.get(this) != null;
  }

  /** Returns whether this {@link JSDocInfo} contains a type for {@code @return} annotation. */
  public boolean hasReturnType() {
    return RETURN_TYPE.get(this) != null;
  }

  /**
   * Returns whether a type, specified using the {@code @type} annotation, is present on this JSDoc.
   */
  public boolean hasType() {
    return TYPE.get(this) != null;
  }

  public boolean hasTypeInformation() {
    return (propertyKeysBitset
            & (TYPE.mask | TYPEDEF_TYPE.mask | ENUM_PARAMETER_TYPE.mask | RETURN_TYPE.mask))
        != 0;
  }

  /** Returns whether the type annotation was inlined. */
  public boolean isInlineType() {
    return checkBit(Bit.INLINE_TYPE);
  }

  /** Gets the return type specified by the {@code @return} annotation. */
  public JSTypeExpression getReturnType() {
    return RETURN_TYPE.get(this);
  }

  /** Gets the enum parameter type specified by the {@code @enum} annotation. */
  public JSTypeExpression getEnumParameterType() {
    return ENUM_PARAMETER_TYPE.get(this);
  }

  /** Gets the typedef type specified by the {@code @type} annotation. */
  public JSTypeExpression getTypedefType() {
    return TYPEDEF_TYPE.get(this);
  }

  /** Gets the type specified by the {@code @type} annotation. */
  public JSTypeExpression getType() {
    return TYPE.get(this);
  }

  /** Gets the type specified by the {@code @this} annotation. */
  public JSTypeExpression getThisType() {
    return THIS_TYPE.get(this);
  }

  /** Returns whether this {@link JSDocInfo} contains a type for {@code @this} annotation. */
  public boolean hasThisType() {
    return THIS_TYPE.get(this) != null;
  }

  /** Gets the base type specified by the {@code @extends} annotation. */
  public JSTypeExpression getBaseType() {
    return BASE_TYPE.get(this);
  }

  /** Gets the description specified by the {@code @desc} annotation. */
  public String getDescription() {
    return DESCRIPTION.get(this);
  }

  /**
   * Gets the meaning specified by the {@code @meaning} annotation.
   *
   * <p>In localization systems, two messages with the same content but different "meanings" may be
   * translated differently. By default, we use the name of the variable that the message is
   * initialized to as the "meaning" of the message.
   *
   * <p>But some code generators (like Closure Templates) inject their own meaning with the jsdoc
   * {@code @meaning} annotation.
   */
  public String getMeaning() {
    return MEANING.get(this);
  }

  /**
   * Gets the alternate message ID specified by the {@code @alternateMessageId} annotation.
   *
   * <p>In localization systems, if we migrate from one message ID algorithm to another, we can
   * specify the old one via {@code @alternateMessageId}. This allows the product to use the
   * previous translation while waiting for the new one to be translated.
   *
   * <p>Some code generators (like Closure Templates) inject this.
   */
  public String getAlternateMessageId() {
    return ALTERNATE_MESSAGE_ID.get(this);
  }

  /**
   * Gets the name we're lending to in a {@code @lends} annotation.
   *
   * <p>In many reflection APIs, you pass an anonymous object to a function, and that function mixes
   * the anonymous object into another object. The {@code @lends} annotation allows the type system
   * to track those property assignments.
   */
  public JSTypeExpression getLendsName() {
    return LENDS_NAME.get(this);
  }

  public boolean hasLendsName() {
    return getLendsName() != null;
  }

  /** Returns the {@code @closurePrimitive {id}} identifier */
  public String getClosurePrimitiveId() {
    return CLOSURE_PRIMITIVE_ID.get(this);
  }

  /** Whether this JSDoc is annotated with {@code @closurePrimitive} */
  public boolean hasClosurePrimitiveId() {
    return CLOSURE_PRIMITIVE_ID.get(this) != null;
  }

  /** Returns whether JSDoc is annotated with {@code @ngInject} annotation. */
  public boolean isNgInject() {
    return checkBit(Bit.NG_INJECT);
  }

  /** Returns whether JSDoc is annotated with {@code @wizaction} annotation. */
  public boolean isWizaction() {
    return checkBit(Bit.WIZ_ACTION);
  }

  /** Returns whether JSDoc is annotated with {@code @polymerBehavior} annotation. */
  public boolean isPolymerBehavior() {
    return checkBit(Bit.POLYMER_BEHAVIOR);
  }

  /** Returns whether JSDoc is annotated with {@code @polymer} annotation. */
  public boolean isPolymer() {
    return checkBit(Bit.POLYMER);
  }

  /** Returns whether JSDoc is annotated with {@code @customElement} annotation. */
  public boolean isCustomElement() {
    return checkBit(Bit.CUSTOM_ELEMENT);
  }

  /** Returns whether JSDoc is annotated with {@code @mixinClass} annotation. */
  public boolean isMixinClass() {
    return checkBit(Bit.MIXIN_CLASS);
  }

  /** Returns whether JSDoc is annotated with {@code @mixinFunction} annotation. */
  public boolean isMixinFunction() {
    return checkBit(Bit.MIXIN_FUNCTION);
  }

  /** Gets the description specified by the {@code @license} annotation. */
  public String getLicense() {
    return LICENSE.get(this);
  }

  @Override
  public String toString() {
    return toStringVerbose();
  }

  @VisibleForTesting
  public String toStringVerbose() {
    MoreObjects.ToStringHelper helper =
        MoreObjects.toStringHelper(this)
            .add("bitset", (propertyBits == 0) ? null : Long.toHexString(propertyBits));

    for (Bit b : Bit.values()) {
      if (checkBit(b)) {
        helper.add("bit:" + b.name, true);
      }
    }

    long bits = propertyKeysBitset;
    int index = 0;
    while (bits > 0) {
      int low = Long.numberOfTrailingZeros(bits);
      bits &= ~(1L << low);
      helper = helper.add(Property.values[low].name, propertyValues.get(index++));
    }

    return helper.omitNullValues().toString();
  }

  /** Returns whether this {@link JSDocInfo} contains a type for {@code @extends} annotation. */
  public boolean hasBaseType() {
    return getBaseType() != null;
  }

  /**
   * Returns the types specified by the {@code @implements} annotation.
   *
   * @return An immutable list of JSTypeExpression objects that can be resolved to types.
   */
  public List<JSTypeExpression> getImplementedInterfaces() {
    return IMPLEMENTED_INTERFACES.getUnmodifiable(this);
  }

  /** Gets the number of interfaces specified by the {@code @implements} annotation. */
  public int getImplementedInterfaceCount() {
    ArrayList<?> list = IMPLEMENTED_INTERFACES.get(this);
    return list != null ? list.size() : 0;
  }

  /**
   * Returns the interfaces extended by an interface
   *
   * @return An immutable list of JSTypeExpression objects that can be resolved to types.
   */
  public List<JSTypeExpression> getExtendedInterfaces() {
    return EXTENDED_INTERFACES.getUnmodifiable(this);
  }

  /** Gets the number of extended interfaces specified */
  public int getExtendedInterfacesCount() {
    ArrayList<?> list = EXTENDED_INTERFACES.get(this);
    return list != null ? list.size() : 0;
  }

  /** Returns the deprecation reason or null if none specified. */
  public String getDeprecationReason() {
    return DEPRECATION_REASON.get(this);
  }

  /** Returns the set of suppressed warnings. */
  public Set<String> getSuppressions() {
    ImmutableMap<ImmutableSet<String>, String> suppressions = SUPPRESSIONS.get(this);
    if (suppressions == null) {
      return ImmutableSet.of();
    }
    Set<ImmutableSet<String>> nameSets = suppressions.keySet();
    Set<String> names = new LinkedHashSet<>();
    for (Set<String> nameSet : nameSets) {
      names.addAll(nameSet);
    }
    return ImmutableSet.copyOf(names);
  }

  /**
   * Returns a map containing key=set of suppressions, and value=the corresponding description for
   * the set.
   */
  public ImmutableMap<ImmutableSet<String>, String> getSuppressionsAndTheirDescription() {
    ImmutableMap<ImmutableSet<String>, String> suppressions = SUPPRESSIONS.get(this);
    return suppressions != null ? suppressions : ImmutableMap.of();
  }

  /** Returns the set of sideeffect notations. */
  public Set<String> getModifies() {
    ImmutableSet<String> modifies = MODIFIES.get(this);
    return modifies != null ? modifies : ImmutableSet.of();
  }

  /** Returns whether a description exists for the parameter with the specified name. */
  public boolean hasDescriptionForParameter(String name) {
    LinkedHashMap<String, String> params = PARAMETER_DESCRIPTIONS.get(this);
    return params != null && params.containsKey(name);
  }

  /** Returns the description for the parameter with the given name, if its exists. */
  public String getDescriptionForParameter(String name) {
    LinkedHashMap<String, String> params = PARAMETER_DESCRIPTIONS.get(this);
    return params != null ? params.get(name) : null;
  }

  /** Returns the list of authors or null if none. */
  public List<String> getAuthors() {
    return AUTHORS.get(this);
  }

  /** Returns the list of references or null if none. */
  public List<String> getReferences() {
    return SEES.get(this);
  }

  /** Returns the version or null if none. */
  public String getVersion() {
    return VERSION.get(this);
  }

  /** Returns the description of the returned object or null if none specified. */
  public String getReturnDescription() {
    return RETURN_DESCRIPTION.get(this);
  }

  /** Returns the block-level description or null if none specified. */
  public String getBlockDescription() {
    return BLOCK_DESCRIPTION.get(this);
  }

  /** Returns whether this has a fileoverview flag. */
  public boolean hasFileOverview() {
    return checkBit(Bit.FILEOVERVIEW);
  }

  /** Returns the file overview or null if none specified. */
  public String getFileOverview() {
    return FILEOVERVIEW_DESCRIPTION.get(this);
  }

  /** Returns whether this has an enhanced namespace. */
  public boolean hasEnhance() {
    return checkBit(Bit.ENHANCED_NAMESPACE);
  }

  /** Returns the enhanced namespace or null if none is specified. */
  public String getEnhance() {
    return ENHANCED_NAMESPACE.get(this);
  }

  /** Gets the list of all markers for the documentation in this JSDoc. */
  public Collection<Marker> getMarkers() {
    ArrayList<Marker> markers = MARKERS.get(this);
    return markers != null ? markers : ImmutableList.of();
  }

  /**
   * Gets the @template type names.
   *
   * <p>Excludes @template types from TTL; get those with {@link #getTypeTransformations()}
   */
  public ImmutableList<String> getTemplateTypeNames() {
    LinkedHashMap<String, JSTypeExpression> map = TEMPLATE_TYPE_NAMES.get(this);
    return map != null ? ImmutableList.copyOf(map.keySet()) : ImmutableList.of();
  }

  public ImmutableMap<String, JSTypeExpression> getTemplateTypes() {
    LinkedHashMap<String, JSTypeExpression> map = TEMPLATE_TYPE_NAMES.get(this);
    return map != null ? ImmutableMap.copyOf(map) : ImmutableMap.of();
  }

  /** Gets the type transformations. */
  public ImmutableMap<String, Node> getTypeTransformations() {
    LinkedHashMap<String, Node> map = TYPE_TRANSFORMATIONS.get(this);
    return map != null ? ImmutableMap.copyOf(map) : ImmutableMap.of();
  }

  /**
   * Returns a collection of all JSTypeExpressions that are a part of this JSDocInfo.
   *
   * <p>This includes:
   *
   * <ul>
   *   <li>base type
   *   <li>@extends
   *   <li>@implements
   *   <li>@lend
   *   <li>@param
   *   <li>@return
   *   <li>@template
   *   <li>@this
   *   <li>@throws
   *   <li>@type
   * </ul>
   *
   * Any future type specific JSDoc should make sure to add the appropriate nodes here.
   *
   * @return collection of all type nodes
   */
  @SuppressWarnings("unchecked")
  public Collection<JSTypeExpression> getTypeExpressions() {
    ImmutableList.Builder<JSTypeExpression> builder = ImmutableList.builder();
    long bits = propertyKeysBitset;
    int index = 0;
    while (bits > 0) {
      int low = Long.numberOfTrailingZeros(bits);
      bits &= ~(1L << low);
      Property<Object> prop = (Property<Object>) Property.values[low];
      for (JSTypeExpression type : prop.getTypeExpressions(propertyValues.get(index++))) {
        if (type != null) {
          builder.add(type);
        }
      }
    }
    return builder.build();
  }

  /**
   * Returns a collection of all type nodes that are a part of this JSDocInfo.
   *
   * <p>This includes:
   *
   * <ul>
   *   <li>@extends
   *   <li>@implements
   *   <li>@lend
   *   <li>@param
   *   <li>@return
   *   <li>@template
   *   <li>@this
   *   <li>@throws
   *   <li>@type
   * </ul>
   *
   * Any future type specific JSDoc should make sure to add the appropriate nodes here.
   *
   * @return collection of all type nodes
   */
  public Collection<Node> getTypeNodes() {
    List<Node> nodes = new ArrayList<>();

    for (JSTypeExpression type : getTypeExpressions()) {
      nodes.add(type.getRoot());
    }

    return nodes;
  }

  public boolean hasModifies() {
    return MODIFIES.get(this) != null;
  }

  /**
   * Returns the original JSDoc comment string. Returns null unless parseJsDocDocumentation is
   * enabled via the ParserConfig.
   */
  public String getOriginalCommentString() {
    return SOURCE_COMMENT.get(this);
  }

  public int getOriginalCommentPosition() {
    Integer pos = ORIGINAL_COMMENT_POSITION.get(this);
    return pos != null ? pos : 0;
  }

  /** Get the value of the @modifies{this} annotation stored in the doc info. */
  public boolean modifiesThis() {
    return getModifies().contains("this");
  }

  /** Returns whether the @modifies annotation includes "arguments" or any named parameters. */
  public boolean hasSideEffectsArgumentsAnnotation() {
    Set<String> modifies = this.getModifies();
    // TODO(johnlenz): if we start tracking parameters individually
    // this should simply be a check for "arguments".
    return (modifies.size() > 1 || (modifies.size() == 1 && !modifies.contains("this")));
  }

  /**
   * A builder for {@link JSDocInfo} objects. This builder is required because JSDocInfo instances
   * have immutable structure. It provides early incompatibility detection among properties stored
   * on the {@code JSDocInfo} object being created.
   */
  public static class Builder {
    TreeMap<Property<?>, Object> props = new TreeMap<>();
    long bits = 0L;

    // whether the current JSDocInfo has valuable information
    boolean populated;
    // the current marker, if any.
    Marker currentMarker;
    // the set of unique license texts
    Set<String> licenseTexts;

    public static Builder copyFrom(JSDocInfo info) {
      return info.toBuilder();
    }

    public static Builder maybeCopyFrom(@Nullable JSDocInfo info) {
      return info != null ? info.toBuilder() : JSDocInfo.builder().parseDocumentation();
    }

    /**
     * Returns a JSDocInfo.Builder that contains a copy of the given JSDocInfo in which only the
     * {@code @type} field of the JSDocInfo is replaced with the given typeExpression. This is done
     * to prevent generating code in the client module which references local variables from another
     * module.
     */
    public static Builder maybeCopyFromWithNewType(
        JSDocInfo info, JSTypeExpression typeExpression) {
      if (info == null) {
        return JSDocInfo.builder().parseDocumentation().setType(typeExpression);
      }
      return info.toBuilder().setType(typeExpression);
    }

    public static Builder copyFromWithNewType(JSDocInfo info, JSTypeExpression typeExpression) {
      return info.toBuilder().setType(typeExpression);
    }

    /**
     * Returns a JSDocInfo.Builder that contains a JSDoc in which all module local types (which may
     * be inside {@code @param}, {@code @type} or {@code @returns} are replaced with unknown. This
     * is done to prevent generating code in the client module which references local variables from
     * another module.
     */
    public static Builder maybeCopyFromAndReplaceNames(
        JSDocInfo info, Set<String> moduleLocalNamesToReplace) {
      return info != null
          ? copyFromAndReplaceNames(info, moduleLocalNamesToReplace)
          : JSDocInfo.builder().parseDocumentation();
    }

    private static Builder copyFromAndReplaceNames(JSDocInfo info, Set<String> oldNames) {
      return info.cloneAndReplaceTypeNames(oldNames).toBuilder(); // TODO - populated
    }

    /**
     * Configures the builder to parse documentation. This should be called immediately after
     * instantiating the builder if documentation should be included, since it enables various
     * operations to do work that would otherwise be no-ops.
     */
    public Builder parseDocumentation() {
      setBit(Bit.INCLUDE_DOCUMENTATION, true);
      return this;
    }

    public boolean shouldParseDocumentation() {
      return checkBit(Bit.INCLUDE_DOCUMENTATION);
    }

    /**
     * Sets the original JSDoc comment string. This is a no-op if the builder isn't configured to
     * record documentation.
     */
    public void recordOriginalCommentString(String sourceComment) {
      if (shouldParseDocumentation()) {
        populated = true;
        setProp(SOURCE_COMMENT, sourceComment);
      }
    }

    /** Sets the position of original JSDoc comment. */
    public void recordOriginalCommentPosition(int position) {
      if (shouldParseDocumentation()) {
        populated = true;
        setProp(ORIGINAL_COMMENT_POSITION, position);
      }
    }

    /**
     * Returns whether this builder is populated with information that can be used to {@link #build}
     * a {@link JSDocInfo} object that has a fileoverview tag.
     */
    public boolean isPopulatedWithFileOverview() {
      return populated
          && (bits
                  & (Bit.FILEOVERVIEW.mask
                      | Bit.EXTERNS.mask
                      | Bit.NOCOMPILE.mask
                      | Bit.TYPE_SUMMARY.mask
                      | Bit.ENHANCED_NAMESPACE.mask))
              != 0;
    }

    /** Returns whether this builder recorded a description. */
    public boolean isDescriptionRecorded() {
      return props.get(DESCRIPTION) != null;
    }

    /**
     * Builds a {@link JSDocInfo} object based on the populated information and returns it.
     *
     * @return a {@link JSDocInfo} object populated with the values given to this builder. If no
     *     value was populated, this method simply returns {@code null}
     */
    public JSDocInfo build() {
      return build(/* always= */ false);
    }

    /**
     * Builds a {@link JSDocInfo} object based on the populated information and returns it.
     *
     * @param always Return an default JSDoc object.
     * @return a {@link JSDocInfo} object populated with the values given to this builder. If no
     *     value was populated and {@code always} is false, returns {@code null}. If {@code always}
     *     is true, returns a default JSDocInfo.
     */
    public JSDocInfo build(boolean always) {
      if (populated || always) {
        JSDocInfo info = new JSDocInfo(bits, props);
        populated = false;
        return info;
      }
      return null;
    }

    /**
     * Builds a {@link JSDocInfo} object based on the populated information and returns it. Once
     * this method is called, the builder can be reused to build another {@link JSDocInfo} object.
     *
     * @return a {@link JSDocInfo} object populated with the values given to this builder. If no
     *     value was populated, this method simply returns {@code null}
     */
    public JSDocInfo buildAndReset() {
      JSDocInfo info = build();
      bits &= Bit.INCLUDE_DOCUMENTATION.mask; // only keep this one flag
      props.clear();
      populated = false;
      return info;
    }

    /**
     * Adds a marker to the current JSDocInfo and populates the marker with the annotation
     * information.
     */
    public void markAnnotation(String annotation, int lineno, int charno) {
      Marker marker = addMarker();
      if (marker != null) {
        TrimmedStringPosition position = new TrimmedStringPosition();
        position.setItem(annotation);
        position.setPositionInformation(lineno, charno, lineno, charno + annotation.length());
        marker.setAnnotation(position);
        populated = true;
      }
      currentMarker = marker;
    }

    private Marker addMarker() {
      if (shouldParseDocumentation()) {
        ArrayList<Marker> markers = getProp(MARKERS);
        if (markers == null) {
          setProp(MARKERS, markers = new ArrayList<>());
        }
        Marker marker = new Marker();
        markers.add(marker);
        return marker;
      }
      return null;
    }

    /** Adds a textual block to the current marker. */
    public void markText(
        String text, int startLineno, int startCharno, int endLineno, int endCharno) {
      if (currentMarker != null) {
        StringPosition position = new StringPosition();
        position.setItem(text);
        position.setPositionInformation(startLineno, startCharno, endLineno, endCharno);
        currentMarker.setDescription(position);
      }
    }

    /** Adds a type declaration to the current marker. */
    public void markTypeNode(
        Node typeNode, int lineno, int startCharno, int endLineno, int endCharno, boolean hasLC) {
      if (currentMarker != null) {
        TypePosition position = new TypePosition();
        position.setItem(typeNode);
        position.setHasBrackets(hasLC);
        position.setPositionInformation(lineno, startCharno, endLineno, endCharno);
        currentMarker.setType(position);
      }
    }

    /** Adds a name declaration to the current marker. */
    public void markName(String name, Node templateNode, int lineno, int charno) {
      if (currentMarker != null) {
        // Record the name as both a SourcePosition<String> and a
        // SourcePosition<Node>. The <String> form is deprecated,
        // because <Node> is more consistent with how other name
        // references are handled (see #markTypeNode)
        //
        // TODO(nicksantos): Remove all uses of the Name position
        // and replace them with the NameNode position.
        TrimmedStringPosition position = new TrimmedStringPosition();
        position.setItem(name);
        position.setPositionInformation(lineno, charno, lineno, charno + name.length());

        NamePosition nodePos = new NamePosition();
        Node node = Node.newString(Token.NAME, name).setLinenoCharno(lineno, charno);
        node.setLength(name.length());
        if (templateNode != null) {
          node.setStaticSourceFileFrom(templateNode);
        }
        nodePos.setItem(node);
        nodePos.setPositionInformation(lineno, charno, lineno, charno + name.length());
        currentMarker.setNameNode(nodePos);
      }
    }

    /**
     * Records a block-level description.
     *
     * @return {@code true} if the description was recorded.
     */
    public boolean recordBlockDescription(String description) {
      populated = true;
      if (!shouldParseDocumentation()) {
        return true;
      }
      return populateProp(BLOCK_DESCRIPTION, description);
    }

    /**
     * Records a visibility.
     *
     * @return {@code true} if the visibility was recorded and {@code false} if it was already
     *     defined
     */
    public boolean recordVisibility(Visibility visibility) {
      if (getProp(VISIBILITY) == null) {
        populated = true;
        setProp(VISIBILITY, visibility);
        return true;
      }
      return false;
    }

    public void overwriteVisibility(Visibility visibility) {
      populated = true;
      setProp(VISIBILITY, visibility);
    }

    /**
     * Records a typed parameter.
     *
     * @return {@code true} if the typed parameter was recorded and {@code false} if a parameter
     *     with the same name was already defined
     */
    public boolean recordParameter(String parameterName, JSTypeExpression type) {
      return !hasAnySingletonTypeTags() && populatePropEntry(PARAMETERS, parameterName, type);
    }

    /**
     * Records a parameter's description.
     *
     * @return {@code true} if the parameter's description was recorded and {@code false} if a
     *     parameter with the same name was already defined
     */
    public boolean recordParameterDescription(String parameterName, String description) {
      if (!shouldParseDocumentation()) {
        return true;
      }
      return populatePropEntry(PARAMETER_DESCRIPTIONS, parameterName, description);
    }

    /**
     * Records a template type name.
     *
     * @return {@code true} if the template type name was recorded and {@code false} if the input
     *     template type name was already defined.
     */
    public boolean recordTemplateTypeName(String name) {
      return recordTemplateTypeName(name, null);
    }

    public boolean recordTemplateTypeName(String name, JSTypeExpression bound) {
      if (bound == null) {
        bound = JSTypeExpression.IMPLICIT_TEMPLATE_BOUND;
      }
      Map<String, Node> transformations = getProp(TYPE_TRANSFORMATIONS);
      if ((transformations != null && transformations.containsKey(name))
          || props.containsKey(TYPEDEF_TYPE)) {
        return false;
      }
      return populatePropEntry(TEMPLATE_TYPE_NAMES, name, bound);
    }

    /** Records a type transformation expression together with its template type name. */
    public boolean recordTypeTransformation(String name, Node expr) {
      Map<String, JSTypeExpression> names = getProp(TEMPLATE_TYPE_NAMES);
      if (names != null && names.containsKey(name)) {
        return false;
      }
      return populatePropEntry(TYPE_TRANSFORMATIONS, name, expr);
    }

    /**
     * Records a throw annotation description.
     *
     * @return {@code true} if the type's description was recorded. The description
     *     of a throw annotation is the text including the type.
     */
    public boolean recordThrowsAnnotation(String annotation) {
      populated = true;
      // TODO(user): Does it make sense to check for singleton tags here?
      // Note that if the @throws annotation appears before a singleton tag like @type,
      // the throws annotation is preserved, but if it appears after the singleton tag,
      // it gets dropped.
      if (!hasAnySingletonTypeTags()) {
        List<String> throwsAnnotations = getPropWithDefault(THROWS_ANNOTATIONS, ArrayList::new);
        if (shouldParseDocumentation()) {
          throwsAnnotations.add(annotation);
        } else if (throwsAnnotations.isEmpty()) {
          // Add at least one annotation so that PureFunctionIdentifier knows about
          // the side effect.
          throwsAnnotations.add("");
        }
        return true;
      }
      return false;
    }

    /** Adds an author to the current information. */
    public boolean recordAuthor(String author) {
      populated = true;
      if (shouldParseDocumentation()) {
        getPropWithDefault(AUTHORS, ArrayList::new).add(author);
      }
      // NOTE: this could be removed, since it's always true.
      return true;
    }

    /** Adds a reference ("@see") to the current information. */
    public boolean recordReference(String reference) {
      populated = true;
      if (shouldParseDocumentation()) {
        getPropWithDefault(SEES, ArrayList::new).add(reference);
      }
      // NOTE: this could be removed, since it's always true.
      return true;
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link
     * JSDocInfo#isConsistentIdGenerator()} flag set to {@code true}.
     *
     * @return {@code true} if the consistentIdGenerator flag was recorded and {@code false} if it
     *     was already recorded
     */
    public boolean recordConsistentIdGenerator() {
      return populateProp(ID_GENERATOR, IdGenerator.CONSISTENT);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link
     * JSDocInfo#isStableIdGenerator()} flag set to {@code true}.
     *
     * @return {@code true} if the stableIdGenerator flag was recorded and {@code false} if it was
     *     already recorded.
     */
    public boolean recordStableIdGenerator() {
      return populateProp(ID_GENERATOR, IdGenerator.STABLE);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link
     * JSDocInfo#isXidGenerator()} flag set to {@code true}.
     *
     * @return {@code true} if the isXidGenerator flag was recorded and {@code false} if it was
     *     already recorded.
     */
    public boolean recordXidGenerator() {
      return populateProp(ID_GENERATOR, IdGenerator.XID);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link
     * JSDocInfo#isStableIdGenerator()} flag set to {@code true}.
     *
     * @return {@code true} if the stableIdGenerator flag was recorded and {@code false} if it was
     *     already recorded.
     */
    public boolean recordMappedIdGenerator() {
      return populateProp(ID_GENERATOR, IdGenerator.MAPPED);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link
     * JSDocInfo#isIdGenerator()} flag set to {@code true}.
     *
     * @return {@code true} if the idGenerator flag was recorded and {@code false} if it was already
     *     recorded
     */
    public boolean recordIdGenerator() {
      return populateProp(ID_GENERATOR, IdGenerator.UNIQUE);
    }

    /** Records the version. */
    public boolean recordVersion(String version) {
      if (!shouldParseDocumentation()) {
        return true;
      }
      return populateProp(VERSION, version);
    }

    /** Records the deprecation reason. */
    public boolean recordDeprecationReason(String reason) {
      return populateProp(DEPRECATION_REASON, reason);
    }

    /** Returns whether a deprecation reason has been recorded. */
    public boolean isDeprecationReasonRecorded() {
      return getProp(DEPRECATION_REASON) != null;
    }

    public void recordSuppressions(ImmutableSet<String> suppressions, String description) {
      populated = true;
      ImmutableMap.Builder<ImmutableSet<String>, String> mapBuilder = ImmutableMap.builder();
      ImmutableMap<ImmutableSet<String>, String> current = getProp(SUPPRESSIONS);
      if (current != null) {
        if (current.containsKey(suppressions)) {
          // Exact @suppress warning exists already. Return without recording.
          return;
        }
        mapBuilder.putAll(current);
      }
      mapBuilder.put(suppressions, description);
      ImmutableMap<ImmutableSet<String>, String> suppressionsMap = mapBuilder.buildOrThrow();
      setProp(SUPPRESSIONS, suppressionsMap);
    }

    /**
     * Records the list of suppressed warnings, possibly adding to the set of already configured
     * warnings.
     */
    public void recordSuppressions(Set<String> suppressions) {
      recordSuppressions(ImmutableSet.copyOf(suppressions), "");
    }

    public void recordSuppression(String suppression) {
      recordSuppressions(ImmutableSet.of(suppression), "");
    }

    /** Records the list of modifies warnings. */
    public boolean recordModifies(Set<String> modifies) {
      return !hasAnySingletonSideEffectTags()
          && populateProp(MODIFIES, ImmutableSet.copyOf(modifies));
    }

    /**
     * Records a type.
     *
     * @return {@code true} if the type was recorded and {@code false} if it is invalid or was
     *     already defined
     */
    public boolean recordType(JSTypeExpression type) {
      return type != null && !hasAnyTypeRelatedTags() && populateProp(TYPE, type);
    }

    public void recordInlineType() {
      populateBit(Bit.INLINE_TYPE, true);
    }

    /**
     * Records that the {@link JSDocInfo} being built should be populated with a {@code typedef}'d
     * type.
     */
    public boolean recordTypedef(JSTypeExpression type) {
      return type != null
          && !hasAnyTypeRelatedTags()
          && getProp(TEMPLATE_TYPE_NAMES) == null
          && populateProp(TYPEDEF_TYPE, type);
    }

    /**
     * Records a return type.
     *
     * @return {@code true} if the return type was recorded and {@code false} if it is invalid or
     *     was already defined
     */
    public boolean recordReturnType(JSTypeExpression type) {
      return type != null && !hasAnySingletonTypeTags() && populateProp(RETURN_TYPE, type);
    }

    /**
     * Records a return description
     *
     * @return {@code true} if the return description was recorded and {@code false} if it is
     *     invalid or was already defined
     */
    public boolean recordReturnDescription(String description) {
      if (!shouldParseDocumentation()) {
        return true;
      }
      return populateProp(RETURN_DESCRIPTION, description);
    }

    /**
     * Records the type of a define.
     *
     * <p>'Define' values are special constants that may be manipulated by the compiler. They are
     * designed to mimic the #define command in the C preprocessor.
     */
    public boolean recordDefineType(JSTypeExpression type) {
      if (type != null && !checkBit(Bit.CONST) && !checkBit(Bit.DEFINE) && recordType(type)) {
        return populateBit(Bit.DEFINE, true);
      }
      return false;
    }

    /**
     * Records a parameter type to an enum.
     *
     * @return {@code true} if the enum's parameter type was recorded and {@code false} if it was
     *     invalid or already defined
     */
    public boolean recordEnumParameterType(JSTypeExpression type) {
      if (type != null && !hasAnyTypeRelatedTags()) {
        setProp(ENUM_PARAMETER_TYPE, type);
        populated = true;
        return true;
      }
      return false;
    }

    // TODO(tbreisacher): Disallow nullable types here. If someone writes
    // "@this {Foo}" in their JS we automatically treat it as though they'd written
    // "@this {!Foo}". But, if the type node is created in the compiler
    // (e.g. in the WizPass) we should explicitly add the '!'
    /**
     * Records a type for {@code @this} annotation.
     *
     * @return {@code true} if the type was recorded and {@code false} if it is invalid or if it
     *     collided with {@code @enum} or {@code @type} annotations
     */
    public boolean recordThisType(JSTypeExpression type) {
      return type != null && !hasAnySingletonTypeTags() && populateProp(THIS_TYPE, type);
    }

    /**
     * Records a base type.
     *
     * @return {@code true} if the base type was recorded and {@code false} if it was already
     *     defined
     */
    public boolean recordBaseType(JSTypeExpression type) {
      return type != null && !hasAnySingletonTypeTags() && populateProp(BASE_TYPE, type);
    }

    /**
     * Changes a base type, even if one has already been set on currentInfo.
     *
     * @return {@code true} if the base type was changed successfully.
     */
    public boolean changeBaseType(JSTypeExpression type) {
      if (type != null && !hasAnySingletonTypeTags()) {
        setProp(BASE_TYPE, type);
        populated = true;
        return true;
      }
      return false;
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isConstant()}
     * flag set to {@code true}.
     *
     * @return {@code true} if the constancy was recorded and {@code false} if it was already
     *     defined
     */
    public boolean recordConstancy() {
      return populateBit(Bit.CONST, true);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isConstant()}
     * flag set to {@code false}.
     *
     * @return {@code true} if the mutability was recorded and {@code false} if it was already
     *     defined
     */
    public boolean recordMutable() {
      return populateBit(Bit.CONST, false);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isFinal()}
     * flag set to {@code true}.
     *
     * @return {@code true} if the finality was recorded and {@code false} if it was already defined
     */
    public boolean recordFinality() {
      return populateBit(Bit.FINAL, true);
    }

    /**
     * Records a description giving context for translation (i18n).
     *
     * @return {@code true} if the description was recorded and {@code false} if the description was
     *     invalid or was already defined
     */
    public boolean recordDescription(String description) {
      return populateProp(DESCRIPTION, description);
    }

    /**
     * Records a meaning giving context for translation (i18n). Different meanings will result in
     * different translations.
     *
     * @return {@code true} If the meaning was successfully updated.
     */
    public boolean recordMeaning(String meaning) {
      return populateProp(MEANING, meaning);
    }

    /**
     * Records an ID for an alternate message to be used if this message is not yet translated.
     *
     * @return {@code true} If the alternate message ID was successfully updated.
     */
    public boolean recordAlternateMessageId(String alternateMessageId) {
      return populateProp(ALTERNATE_MESSAGE_ID, alternateMessageId);
    }

    /**
     * Records an identifier for a Closure Primitive. function.
     *
     * @return {@code true} If the id was successfully updated.
     */
    public boolean recordClosurePrimitiveId(String closurePrimitiveId) {
      return populateProp(CLOSURE_PRIMITIVE_ID, closurePrimitiveId);
    }

    /**
     * Records a fileoverview description.
     *
     * @return {@code true} if the description was recorded and {@code false} if the description was
     *     invalid or was already defined.
     */
    public boolean recordFileOverview(String description) {
      setBit(Bit.FILEOVERVIEW, true);
      populated = true;
      if (!shouldParseDocumentation()) {
        return true;
      }
      return populateProp(FILEOVERVIEW_DESCRIPTION, description);
    }

    /**
     * Records enhanced namespace.
     *
     * @return {@code true} If the enhanced namespace was recorded.
     */
    public boolean recordEnhance(String namespace) {
      setBit(Bit.ENHANCED_NAMESPACE, true);
      populated = true;
      return populateProp(ENHANCED_NAMESPACE, namespace);
    }

    public boolean recordLicense(String license) {
      setProp(LICENSE, license);
      populated = true;
      return true;
    }

    public boolean addLicense(String license) {
      // The vast majority of JSDoc doesn't have @license so it make sense to be lazy about building
      // the HashSet.
      if (licenseTexts == null) {
        // The HashSet is only used to remove duplicates, it is never read beyond the add,
        // so LinkedHashSet is not required.
        licenseTexts = new HashSet<>();
      }

      if (!licenseTexts.add(license)) {
        return false;
      }

      String txt = getProp(LICENSE);
      return recordLicense(nullToEmpty(txt) + license);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isHidden()}
     * flag set to {@code true}.
     *
     * @return {@code true} if the hiddenness was recorded and {@code false} if it was already
     *     defined
     */
    public boolean recordHiddenness() {
      return populateBit(Bit.HIDDEN, true);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link
     * JSDocInfo#isNoCompile()} flag set to {@code true}.
     *
     * @return {@code true} if the no compile flag was recorded and {@code false} if it was already
     *     recorded
     */
    public boolean recordNoCompile() {
      return populateBit(Bit.NOCOMPILE, true);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link
     * JSDocInfo#isNoCollapse()} flag set to {@code true}.
     *
     * @return {@code true} if the no collapse flag was recorded and {@code false} if it was already
     *     recorded
     */
    public boolean recordNoCollapse() {
      return populateBit(Bit.NOCOLLAPSE, true);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isNoInline()}
     * flag set to {@code true}.
     *
     * @return {@code true} if the no inline flag was recorded and {@code false} if it was already
     *     recorded
     */
    public boolean recordNoInline() {
      return populateBit(Bit.NOINLINE, true);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link
     * JSDocInfo#isPureOrBreakMyCode()} flag set to {@code true}.
     *
     * @return {@code true} if the no pureOrBreakMyCode flag was recorded and {@code false} if it
     *     was already recorded
     */
    public boolean recordPureOrBreakMyCode() {
      return populateBit(Bit.PURE_OR_BREAK_MY_CODE, true);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link
     * JSDocInfo#isCollapsibleOrBreakMyCode()} flag set to {@code true}.
     *
     * @return {@code true} if the no collapsibleOrBreakMyCode flag was recorded and {@code false}
     *     if it was already recorded
     */
    public boolean recordCollapsibleOrBreakMyCode() {
      return populateBit(Bit.COLLAPSIBLE_OR_BREAK_MY_CODE, true);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link
     * JSDocInfo#isConstructor()} flag set to {@code true}.
     *
     * @return {@code true} if the constructor was recorded and {@code false} if it was already
     *     defined or it was incompatible with the existing flags
     */
    public boolean recordConstructor() {
      return !hasAnySingletonTypeTags()
          && !isConstructorOrInterface()
          && populateBit(Bit.CONSTRUCTOR, true);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link
     * JSDocInfo#usesImplicitMatch()} flag set to {@code true}.
     *
     * @return {@code true} if the {@code @record} tag was recorded and {@code false} if it was
     *     already defined or it was incompatible with the existing flags
     */
    public boolean recordImplicitMatch() {
      return !hasAnySingletonTypeTags()
          && !isConstructorOrInterface()
          && populateBit(Bit.RECORD, true)
          && populateBit(Bit.INTERFACE, true);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#xxx()} flag
     * set to {@code true}.
     *
     * @return {@code true} if the no inline flag was recorded and {@code false} if it was already
     *     recorded
     */
    public boolean recordLocaleFile() {
      return populateBit(Bit.LOCALE_FILE, true);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#xxx()} flag
     * set to {@code true}.
     *
     * @return {@code true} if the no inline flag was recorded and {@code false} if it was already
     *     recorded
     */
    public boolean recordLocaleObject() {
      return populateBit(Bit.LOCALE_OBJECT, true);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#xxx()} flag
     * set to {@code true}.
     *
     * @return {@code true} if the no inline flag was recorded and {@code false} if it was already
     *     recorded
     */
    public boolean recordLocaleValue() {
      return populateBit(Bit.LOCALE_VALUE, true);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#xxx()} flag
     * set to {@code true}.
     *
     * @return {@code true} if the no inline flag was recorded and {@code false} if it was already
     *     recorded
     */
    public boolean recordLocaleSelect() {
      return populateBit(Bit.LOCALE_SELECT, true);
    }

    public boolean recordProvideGoog() {
      return populateBit(Bit.PROVIDE_GOOG, true);
    }

    /**
     * Whether the {@link JSDocInfo} being built will have its {@link JSDocInfo#isConstructor()}
     * flag set to {@code true}.
     */
    public boolean isConstructorRecorded() {
      return checkBit(Bit.CONSTRUCTOR);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link
     * JSDocInfo#makesUnrestricted()} flag set to {@code true}.
     *
     * @return {@code true} if annotation was recorded and {@code false} if it was already defined
     *     or it was incompatible with the existing flags
     */
    public boolean recordUnrestricted() {
      return !hasAnySingletonTypeTags()
          && ((bits & (Bit.INTERFACE.mask | Bit.DICT.mask | Bit.STRUCT.mask)) == 0)
          && populateBit(Bit.UNRESTRICTED, true);
    }

    public boolean isUnrestrictedRecorded() {
      return checkBit(Bit.UNRESTRICTED);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isAbstract()}
     * flag set to {@code true}.
     *
     * @return {@code true} if the flag was recorded and {@code false} if it was already defined or
     *     it was incompatible with the existing flags
     */
    public boolean recordAbstract() {
      return !hasAnySingletonTypeTags()
          && ((bits & (Bit.INTERFACE.mask | Bit.FINAL.mask)) == 0)
          && getProp(VISIBILITY) != Visibility.PRIVATE
          && populateBit(Bit.ABSTRACT, true);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link
     * JSDocInfo#makesStructs()} flag set to {@code true}.
     *
     * @return {@code true} if the struct was recorded and {@code false} if it was already defined
     *     or it was incompatible with the existing flags
     */
    public boolean recordStruct() {
      return !hasAnySingletonTypeTags()
          && ((bits & (Bit.DICT.mask | Bit.UNRESTRICTED.mask)) == 0)
          && populateBit(Bit.STRUCT, true);
    }

    public boolean isStructRecorded() {
      return checkBit(Bit.STRUCT);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#makesDicts()}
     * flag set to {@code true}.
     *
     * @return {@code true} if the dict was recorded and {@code false} if it was already defined or
     *     it was incompatible with the existing flags
     */
    public boolean recordDict() {
      return !hasAnySingletonTypeTags()
          && ((bits & (Bit.STRUCT.mask | Bit.UNRESTRICTED.mask)) == 0)
          && populateBit(Bit.DICT, true);
    }

    public boolean isDictRecorded() {
      return checkBit(Bit.DICT);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isOverride()}
     * flag set to {@code true}.
     */
    public boolean recordOverride() {
      return populateBit(Bit.OVERRIDE, true);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link
     * JSDocInfo#isDeprecated()} flag set to {@code true}.
     */
    public boolean recordDeprecated() {
      return populateBit(Bit.DEPRECATED, true);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link
     * JSDocInfo#isInterface()} flag set to {@code true}.
     *
     * @return {@code true} if the flag was recorded and {@code false} if it was already defined or
     *     it was incompatible with the existing flags
     */
    public boolean recordInterface() {
      return !hasAnySingletonTypeTags()
          && ((bits & (Bit.CONSTRUCTOR.mask | Bit.ABSTRACT.mask)) == 0)
          && populateBit(Bit.INTERFACE, true);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isExport()}
     * flag set to {@code true}.
     */
    public boolean recordExport() {
      return populateBit(Bit.EXPORT, true);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isExport()}
     * flag set to {@code false}.
     */
    public boolean removeExport() {
      return populateBit(Bit.EXPORT, false);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link
     * JSDocInfo#isImplicitCast()} flag set to {@code true}.
     */
    public boolean recordImplicitCast() {
      return populateBit(Bit.IMPLICITCAST, true);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link
     * JSDocInfo#isNoSideEffects()} flag set to {@code true}.
     */
    public boolean recordNoSideEffects() {
      return !hasAnySingletonSideEffectTags() && populateBit(Bit.NOSIDEEFFECTS, true);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isExterns()}
     * flag set to {@code true}.
     */
    public boolean recordExterns() {
      return populateBit(Bit.EXTERNS, true);
    }

    /**
     * Records that the {@link JSDocInfo} being built should have its {@link
     * JSDocInfo#isTypeSummary()} flag set to {@code true}.
     */
    public boolean recordTypeSummary() {
      return populateBit(Bit.TYPE_SUMMARY, true);
    }

    /**
     * Whether the {@link JSDocInfo} being built will have its {@link JSDocInfo#isInterface()} flag
     * set to {@code true}.
     */
    public boolean isInterfaceRecorded() {
      return checkBit(Bit.INTERFACE);
    }

    /** @return Whether a parameter of the given name has already been recorded. */
    public boolean hasParameter(String name) {
      Map<String, JSTypeExpression> params = getProp(PARAMETERS);
      return params != null && params.containsKey(name);
    }

    /** Records an implemented interface. */
    public boolean recordImplementedInterface(JSTypeExpression interfaceType) {
      return interfaceType != null && addUnique(IMPLEMENTED_INTERFACES, interfaceType);
    }

    /** Records an extended interface type. */
    public boolean recordExtendedInterface(JSTypeExpression interfaceType) {
      return interfaceType != null && addUnique(EXTENDED_INTERFACES, interfaceType);
    }

    private boolean addUnique(Property<ArrayList<JSTypeExpression>> prop, JSTypeExpression elem) {
      ArrayList<JSTypeExpression> list = getPropWithDefault(prop, ArrayList::new);
      if (list.stream().anyMatch(elem::isEquivalentTo)) {
        return false;
      } else {
        list.add(elem);
        populated = true;
        return true;
      }
    }

    /** Records that we're lending to another name. */
    public boolean recordLends(JSTypeExpression name) {
      return !hasAnyTypeRelatedTags() && populateProp(LENDS_NAME, name);
    }

    /** Returns whether current JSDoc is annotated with {@code @ngInject}. */
    public boolean isNgInjectRecorded() {
      return checkBit(Bit.NG_INJECT);
    }

    /** Records that we'd like to add {@code $inject} property inferred from parameters. */
    public boolean recordNgInject(boolean ngInject) {
      return populateBit(Bit.NG_INJECT, true);
    }

    /** Returns whether current JSDoc is annotated with {@code @wizaction}. */
    public boolean isWizactionRecorded() {
      return checkBit(Bit.WIZ_ACTION);
    }

    /** Records that this method is to be exposed as a wizaction. */
    public boolean recordWizaction() {
      return populateBit(Bit.WIZ_ACTION, true);
    }

    /** Returns whether current JSDoc is annotated with {@code @polymerBehavior}. */
    public boolean isPolymerBehaviorRecorded() {
      return checkBit(Bit.POLYMER_BEHAVIOR);
    }

    /** Records that this method is to be exposed as a polymerBehavior. */
    public boolean recordPolymerBehavior() {
      return populateBit(Bit.POLYMER_BEHAVIOR, true);
    }

    /** Returns whether current JSDoc is annotated with {@code @polymer}. */
    public boolean isPolymerRecorded() {
      return checkBit(Bit.POLYMER);
    }

    /** Records that this method is to be exposed as a polymer element. */
    public boolean recordPolymer() {
      return populateBit(Bit.POLYMER, true);
    }

    /** Returns whether current JSDoc is annotated with {@code @customElement}. */
    public boolean isCustomElementRecorded() {
      return checkBit(Bit.CUSTOM_ELEMENT);
    }

    /** Records that this method is to be exposed as a customElement. */
    public boolean recordCustomElement() {
      return populateBit(Bit.CUSTOM_ELEMENT, true);
    }

    /** Returns whether current JSDoc is annotated with {@code @mixinClass}. */
    public boolean isMixinClassRecorded() {
      return checkBit(Bit.MIXIN_CLASS);
    }

    /** Records that this method is to be exposed as a mixinClass. */
    public boolean recordMixinClass() {
      return populateBit(Bit.MIXIN_CLASS, true);
    }

    /** Returns whether current JSDoc is annotated with {@code @mixinFunction}. */
    public boolean isMixinFunctionRecorded() {
      return checkBit(Bit.MIXIN_FUNCTION);
    }

    /** Records that this method is to be exposed as a mixinFunction. */
    public boolean recordMixinFunction() {
      return populateBit(Bit.MIXIN_FUNCTION, true);
    }

    // TODO(sdh): this is a new method - consider removing it in favor of recordType?
    // The main difference is that this force-sets the type, while recordType backs off.
    // This is useful for (e.g.) copyFromWithNewType.
    Builder setType(JSTypeExpression type) {
      props.remove(RETURN_TYPE);
      props.remove(ENUM_PARAMETER_TYPE);
      props.remove(TYPEDEF_TYPE);
      setProp(TYPE, type);
      return this;
    }

    /**
     * Whether the current doc info has other type tags, like {@code @param} or {@code @return} or
     * {@code @type} or etc.
     */
    private boolean hasAnyTypeRelatedTags() {
      return (bits & (Bit.CONSTRUCTOR.mask | Bit.INTERFACE.mask | Bit.ABSTRACT.mask)) != 0
          || hasAnyParameters()
          || getProp(RETURN_TYPE) != null
          || getProp(BASE_TYPE) != null
          || !isPropEmpty(EXTENDED_INTERFACES)
          || getProp(LENDS_NAME) != null
          || getProp(THIS_TYPE) != null
          || hasAnySingletonTypeTags();
    }

    private boolean hasAnyParameters() {
      Map<?, ?> params = getProp(PARAMETERS);
      return params != null && !params.isEmpty();
    }

    private boolean isPropEmpty(Property<? extends Collection<?>> prop) {
      Collection<?> c = getProp(prop);
      return c == null || c.isEmpty();
    }

    /**
     * Whether the current doc info has any of the singleton type tags that may not appear with
     * other type tags, like {@code @type} or {@code @typedef}.
     */
    private boolean hasAnySingletonTypeTags() {
      return getProp(TYPE) != null
          || getProp(TYPEDEF_TYPE) != null
          || getProp(ENUM_PARAMETER_TYPE) != null;
    }

    /**
     * Whether the current doc info has any of the singleton type tags that may not appear with
     * other type tags, like {@code @type} or {@code @typedef}.
     */
    private boolean hasAnySingletonSideEffectTags() {
      return checkBit(Bit.NOSIDEEFFECTS) || !isPropEmpty(MODIFIES);
    }

    private boolean isConstructorOrInterface() {
      return (bits & (Bit.CONSTRUCTOR.mask | Bit.INTERFACE.mask)) != 0;
    }

    private <T> void setProp(Property<T> prop, T value) {
      props.put(prop, value);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private <T> T getProp(Property<T> prop) {
      return (T) props.get(prop);
    }

    private <T> T getPropWithDefault(Property<T> prop, Supplier<T> supplier) {
      T value = getProp(prop);
      if (value == null) {
        setProp(prop, value = supplier.get());
      }
      return value;
    }

    private <K, V> boolean putPropEntry(Property<LinkedHashMap<K, V>> prop, K key, V value) {
      return getPropWithDefault(prop, LinkedHashMap::new).putIfAbsent(key, value) == null;
    }

    private <K, V> boolean populatePropEntry(Property<LinkedHashMap<K, V>> prop, K key, V value) {
      return putPropEntry(prop, key, value) && (populated = true);
    }

    private <T> boolean populateProp(Property<T> prop, T value) {
      populated = true;
      return props.putIfAbsent(prop, value) == null;
    }

    private boolean checkBit(Bit bit) {
      return (bits & bit.mask) != 0;
    }

    private void setBit(Bit bit, boolean value) {
      if (value) {
        bits |= bit.mask;
      } else {
        bits &= ~bit.mask;
      }
    }

    private boolean populateBit(Bit bit, boolean value) {
      if (checkBit(bit) != value) {
        setBit(bit, value);
        return populated = true;
      }
      return false;
    }
  }
}
