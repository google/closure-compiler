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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
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
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

/**
 * <p>JSDoc information describing JavaScript code. JSDoc is represented as a
 * unified object with fields for each JSDoc annotation, even though some
 * combinations are incorrect. For instance, if a JSDoc describes an enum,
 * it cannot have information about a return type. This implementation
 * takes advantage of such incompatibilities to reuse fields for multiple
 * purposes, reducing memory consumption.</p>
 *
 * <p>Constructing {@link JSDocInfo} objects is simplified by
 * {@link JSDocInfoBuilder} which provides early incompatibility detection.</p>
 *
 */
public class JSDocInfo implements Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * Visibility categories. The {@link Visibility#ordinal()} can be used as a
   * numerical indicator of privacy, where 0 is the most private. This means
   * that the {@link Visibility#compareTo} method can be used to
   * determine if a visibility is more permissive than another.
   */
  public enum Visibility {
    PRIVATE,
    PACKAGE,
    PROTECTED,
    PUBLIC,

    // If visibility is not specified, we just assume that visibility
    // is inherited from the super class.
    INHERITED
  }

  // Bitfield property indicies.
  class Property {
    static final int
      NG_INJECT = 0,
      WIZ_ACTION = 1,

       // Flags for Jagger dependency injection prototype
      JAGGER_INJECT = 2,
      JAGGER_MODULE = 3,
      JAGGER_PROVIDE_PROMISE = 4,
      JAGGER_PROVIDE = 5,

      POLYMER_BEHAVIOR = 6;
  }

  private static final class LazilyInitializedInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    // Function information
    private JSTypeExpression baseType;
    private ArrayList<JSTypeExpression> extendedInterfaces;
    private ArrayList<JSTypeExpression> implementedInterfaces;
    private LinkedHashMap<String, JSTypeExpression> parameters;
    private ArrayList<JSTypeExpression> thrownTypes;
    private ArrayList<String> templateTypeNames;
    private Set<String> disposedParameters;
    private LinkedHashMap<String, Node> typeTransformations;

    // Other information
    private String description;
    private String meaning;
    private String deprecated;
    private String license;
    private ImmutableSet<String> suppressions;
    private ImmutableSet<String> modifies;
    private String lendsName;

    // Bit flags for properties.
    private int propertyBitField;

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("bitfield", (propertyBitField == 0)
                           ? null : Integer.toHexString(propertyBitField))
          .add("baseType", baseType)
          .add("extendedInterfaces", extendedInterfaces)
          .add("implementedInterfaces", implementedInterfaces)
          .add("parameters", parameters)
          .add("thrownTypes", thrownTypes)
          .add("templateTypeNames", templateTypeNames)
          .add("disposedParameters", disposedParameters)
          .add("typeTransformations", typeTransformations)
          .add("description", description)
          .add("meaning", meaning)
          .add("deprecated", deprecated)
          .add("license", license)
          .add("suppressions", suppressions)
          .add("lendsName", lendsName)
          .omitNullValues()
          .toString();
    }

    protected LazilyInitializedInfo clone() {
      return clone(false);
    }

    protected LazilyInitializedInfo clone(boolean cloneTypeNodes) {
      LazilyInitializedInfo other = new LazilyInitializedInfo();
      other.baseType = cloneType(baseType, cloneTypeNodes);
      other.extendedInterfaces = cloneTypeList(extendedInterfaces, cloneTypeNodes);
      other.implementedInterfaces = cloneTypeList(implementedInterfaces, cloneTypeNodes);
      other.parameters = cloneTypeMap(parameters, cloneTypeNodes);
      other.thrownTypes = cloneTypeList(thrownTypes, cloneTypeNodes);
      other.templateTypeNames = templateTypeNames == null ? null
          : new ArrayList<>(templateTypeNames);
      other.disposedParameters = disposedParameters == null ? null
          : new HashSet<>(disposedParameters);
      other.typeTransformations = typeTransformations == null ? null
          : new LinkedHashMap<>(typeTransformations);

      other.description = description;
      other.meaning = meaning;
      other.deprecated = deprecated;
      other.license = license;
      other.suppressions = suppressions == null ? null : ImmutableSet.copyOf(suppressions);
      other.modifies = modifies == null ? null :  ImmutableSet.copyOf(modifies);
      other.lendsName = lendsName;

      other.propertyBitField = propertyBitField;
      return other;
    }

    protected ArrayList<JSTypeExpression> cloneTypeList(
        ArrayList<JSTypeExpression> list, boolean cloneTypeExpressionNodes) {
      ArrayList<JSTypeExpression> newlist = null;
      if (list != null) {
        newlist = new ArrayList<>(list.size());
        for (JSTypeExpression expr : list) {
          newlist.add(cloneType(expr, cloneTypeExpressionNodes));
        }
      }
      return newlist;
    }

    protected LinkedHashMap<String, JSTypeExpression> cloneTypeMap(
        LinkedHashMap<String, JSTypeExpression> map, boolean cloneTypeExpressionNodes) {
      LinkedHashMap<String, JSTypeExpression> newmap = null;
      if (map != null) {
        newmap = new LinkedHashMap<>();
        for (Entry<String, JSTypeExpression> entry : map.entrySet()) {
          JSTypeExpression value = entry.getValue();
          newmap.put(entry.getKey(), cloneType(value, cloneTypeExpressionNodes));
        }
      }
      return newmap;
    }

    // TODO(nnaze): Consider putting bit-fiddling logic in a reusable
    // location.
    void setBit(int bitIndex, boolean value) {
      int mask = getMaskForBitIndex(bitIndex);
      if (value) {
        propertyBitField |= mask;
      } else {
        propertyBitField ^= mask;
      }
    }

    boolean isBitSet(int bitIndex) {
      int mask = getMaskForBitIndex(bitIndex);
      return (mask & propertyBitField) != 0;
    }

    private int getMaskForBitIndex(int bitIndex) {
        Preconditions.checkArgument(bitIndex >= 0,
            "Bit index should be non-negative integer");
      return 1 << bitIndex;
    }
  }

  private static final class LazilyInitializedDocumentation {
    private String sourceComment;
    private ArrayList<Marker> markers;

    private LinkedHashMap<String, String> parameters;
    private LinkedHashMap<JSTypeExpression, String> throwsDescriptions;
    private String blockDescription;
    private String fileOverview;
    private String returnDescription;
    private String version;

    private List<String> authors;
    private List<String> sees;
  }

  /**
   * A piece of information (found in a marker) which contains a position
   * with a string.
   */
  public static class StringPosition extends SourcePosition<String> {
    static boolean areEquivalent(StringPosition p1, StringPosition p2) {
      if (p1 == null && p2 == null) {
        return true;
      }

      if ((p1 == null && p2 != null) || (p1 != null && p2 == null)) {
        return false;
      }

      return Objects.equals(p1.getItem(), p2.getItem())
          && p1.getStartLine() == p2.getStartLine()
          && p1.getPositionOnStartLine() == p2.getPositionOnStartLine()
          && p1.getEndLine() == p2.getEndLine()
          && p1.getPositionOnEndLine() == p2.getPositionOnEndLine();
    }
  }

  /**
   * A piece of information (found in a marker) which contains a position
   * with a string that has no leading or trailing whitespace.
   */
  static class TrimmedStringPosition extends StringPosition {
    @Override public void setItem(String item) {
      Preconditions.checkArgument(
          item.charAt(0) != ' ' && item.charAt(item.length() - 1) != ' ',
          "String has leading or trailing whitespace");
      super.setItem(item);
    }
  }

  /**
   * A piece of information (found in a marker) which contains a position
   * with a name node.
   */
  public static class NamePosition extends SourcePosition<Node> {
    static boolean areEquivalent(NamePosition p1, NamePosition p2) {
      if (p1 == null && p2 == null) {
        return true;
      }

      if ((p1 == null && p2 != null) || (p1 != null && p2 == null)) {
        return false;
      }

      if ((p1.getItem() == null && p2.getItem() != null)
          || (p1.getItem() != null && p2.getItem() == null)) {
        return false;
      }

      return (p1.getItem() == null && p2.getItem() == null
              || p1.getItem().isEquivalentTo(p2.getItem()))
          && p1.getStartLine() == p2.getStartLine()
          && p1.getPositionOnStartLine() == p2.getPositionOnStartLine()
          && p1.getEndLine() == p2.getEndLine()
          && p1.getPositionOnEndLine() == p2.getPositionOnEndLine();
    }
  }

  /**
   * A piece of information (found in a marker) which contains a position
   * with a type expression syntax tree.
   */
  public static class TypePosition extends SourcePosition<Node> {
    private boolean brackets = false;

    /** Returns whether the type has curly braces around it. */
    public boolean hasBrackets() {
      return brackets;
    }

    void setHasBrackets(boolean newVal) {
      brackets = newVal;
    }

    static boolean areEquivalent(TypePosition p1, TypePosition p2) {
      if (p1 == null && p2 == null) {
        return true;
      }

      if ((p1 == null && p2 != null) || (p1 != null && p2 == null)) {
        return false;
      }

      if ((p1.getItem() == null && p2.getItem() != null)
          || (p1.getItem() != null && p2.getItem() == null)) {
        return false;
      }

      return (p1.getItem() == null && p2.getItem() == null
              || p1.getItem().isEquivalentTo(p2.getItem()))
          && p1.getStartLine() == p2.getStartLine()
          && p1.getPositionOnStartLine() == p2.getPositionOnStartLine()
          && p1.getEndLine() == p2.getEndLine()
          && p1.getPositionOnEndLine() == p2.getPositionOnEndLine()
          && p1.brackets == p2.brackets;
    }
  }

  /**
   * Defines a class for containing the parsing information
   * for this JSDocInfo. For each annotation found in the
   * JsDoc, a marker will be created indicating the annotation
   * itself, the name of the annotation (if any; for example,
   * a @param has a name, but a @return does not), the
   * textual description found on that annotation and, if applicable,
   * the type declaration. All this information is only collected
   * if documentation collection is turned on.
   */
  public static final class Marker {
    private TrimmedStringPosition annotation;
    private TrimmedStringPosition name;
    private NamePosition nameNode;
    private StringPosition description;
    private TypePosition type;

    /**
     * Gets the position information for the annotation name. (e.g., "param")
     */
    public StringPosition getAnnotation() {
      return annotation;
    }

    void setAnnotation(TrimmedStringPosition p) {
      annotation = p;
    }

    /**
     * Gets the position information for the name found
     * in a @param tag.
     * @deprecated Use #getNameNode
     */
    @Deprecated
    public StringPosition getName() {
      return name;
    }

    void setName(TrimmedStringPosition p) {
      name = p;
    }

    /**
     * Gets the position information for the name found
     * in an @param tag.
     */
    public NamePosition getNameNode() {
      return nameNode;
    }

    void setNameNode(NamePosition p) {
      nameNode = p;
    }

    /**
     * Gets the position information for the description found
     * in a block tag.
     */
    public StringPosition getDescription() {
      return description;
    }

    void setDescription(StringPosition p) {
      description = p;
    }

    /**
     * Gets the position information for the type expression found
     * in some block tags, like "@param" and "@return".
     */
    public TypePosition getType() {
      return type;
    }

    void setType(TypePosition p) {
      type = p;
    }

    private static boolean areEquivalent(Marker m1, Marker m2) {
      if (m1 == null && m2 == null) {
        return true;
      }

      if ((m1 == null && m2 != null) || (m1 != null && m2 == null)) {
        return false;
      }

      return TrimmedStringPosition.areEquivalent(m1.annotation, m2.annotation)
          && TrimmedStringPosition.areEquivalent(m1.name, m2.name)
          && NamePosition.areEquivalent(m1.nameNode, m2.nameNode)
          && StringPosition.areEquivalent(m1.description, m2.description)
          && TypePosition.areEquivalent(m1.type, m2.type);
    }
  }

  private LazilyInitializedInfo info;

  private LazilyInitializedDocumentation documentation;

  private Visibility visibility;

  /**
   * The {@link #isConstant()}, {@link #isConstructor()}, {@link #isInterface},
   * {@link #isHidden()} and {@link #shouldPreserveTry()} flags as well as
   * whether the {@link #type} field stores a value for {@link #getType()},
   * {@link #getReturnType()} or {@link #getEnumParameterType()}.
   *
   * @see #setFlag(boolean, int)
   * @see #getFlag(int)
   * @see #setType(JSTypeExpression, int)
   * @see #getType(int)
   */
  private int bitset;

  /**
   * The type for {@link #getType()}, {@link #getReturnType()} or
   * {@link #getEnumParameterType()}. The knowledge of which one is recorded is
   * stored in the {@link #bitset} field.
   *
   * @see #setType(JSTypeExpression, int)
   * @see #getType(int)
   */
  private JSTypeExpression type;

  /**
   * The type for {@link #getThisType()}.
   */
  private JSTypeExpression thisType;

  /**
   * Whether the type annotation was inlined.
   */
  private boolean inlineType;

  /**
   * Whether to include documentation.
   *
   * @see JSDocInfo.LazilyInitializedDocumentation
   */
  private boolean includeDocumentation;

  /**
   * Position of the original comment.
   */
  private int originalCommentPosition;

  // We use a bit map to represent whether or not the JSDoc contains
  // one of the "boolean" annotation types (annotations like @constructor,
  // for which the presence of the annotation alone is significant).

  // Mask all the boolean annotation types
  private static final int MASK_FLAGS         = 0x3FFFFFFF;

  private static final int MASK_CONSTANT      = 0x00000001; // @const
  private static final int MASK_CONSTRUCTOR   = 0x00000002; // @constructor
  private static final int MASK_DEFINE        = 0x00000004; // @define
  private static final int MASK_HIDDEN        = 0x00000008; // @hidden
  private static final int MASK_PRESERVETRY   = 0x00000010; // @preserveTry
  @SuppressWarnings("unused")
  private static final int MASK_UNUSED_1      = 0x00000020; //
  private static final int MASK_OVERRIDE      = 0x00000040; // @override
  private static final int MASK_NOALIAS       = 0x00000080; // @noalias
  private static final int MASK_DEPRECATED    = 0x00000100; // @deprecated
  private static final int MASK_INTERFACE     = 0x00000200; // @interface
  private static final int MASK_EXPORT        = 0x00000400; // @export
  private static final int MASK_FILEOVERVIEW  = 0x00001000; // @fileoverview
  private static final int MASK_IMPLICITCAST  = 0x00002000; // @implicitCast
  private static final int MASK_NOSIDEEFFECTS = 0x00004000; // @nosideeffects
  private static final int MASK_EXTERNS       = 0x00008000; // @externs
  @SuppressWarnings("unused")
  private static final int MASK_UNUSED_2      = 0x00010000; //
  private static final int MASK_NOCOMPILE     = 0x00020000; // @nocompile
  private static final int MASK_CONSISTIDGEN  = 0x00040000; // @consistentIdGenerator
  private static final int MASK_IDGEN         = 0x00080000; // @idGenerator
  private static final int MASK_EXPOSE        = 0x00100000; // @expose
  private static final int MASK_UNRESTRICTED  = 0x00200000; // @unrestricted
  private static final int MASK_STRUCT        = 0x00400000; // @struct
  private static final int MASK_DICT          = 0x00800000; // @dict
  private static final int MASK_STALBEIDGEN   = 0x01000000; // @stableIdGenerator
  private static final int MASK_MAPPEDIDGEN   = 0x02000000; // @idGenerator {mapped}
  private static final int MASK_NOCOLLAPSE    = 0x04000000; // @nocollapse
  private static final int MASK_RECORD        = 0x08000000; // @record

  // 3 bit type field stored in the top 3 bits of the most significant
  // nibble.
  private static final int MASK_TYPEFIELD    = 0xE0000000; // 1110...
  private static final int TYPEFIELD_TYPE    = 0x20000000; // 0010...
  private static final int TYPEFIELD_RETURN  = 0x40000000; // 0100...
  private static final int TYPEFIELD_ENUM    = 0x60000000; // 0110...
  private static final int TYPEFIELD_TYPEDEF = 0x80000000; // 1000...

  /**
   * Creates a {@link JSDocInfo} object. This object should be created using
   * a {@link JSDocInfoBuilder}.
   */
  JSDocInfo(boolean includeDocumentation) {
    this.includeDocumentation = includeDocumentation;
  }

  // Visible for testing.
  JSDocInfo() {}

  public JSDocInfo clone() {
    return clone(false);
  }

  public JSDocInfo clone(boolean cloneTypeNodes) {
    JSDocInfo other = new JSDocInfo();
    other.info = this.info == null ? null : this.info.clone(cloneTypeNodes);
    other.documentation = this.documentation;
    other.visibility = this.visibility;
    other.bitset = this.bitset;
    other.type = cloneType(this.type, cloneTypeNodes);
    other.thisType = cloneType(this.thisType, cloneTypeNodes);
    other.includeDocumentation = this.includeDocumentation;
    other.originalCommentPosition = this.originalCommentPosition;
    return other;
  }

  private static JSTypeExpression cloneType(JSTypeExpression expr, boolean cloneTypeNodes) {
    if (expr != null) {
      return cloneTypeNodes ? expr.clone() : expr;
    }
    return null;
  }

  @VisibleForTesting
  public static boolean areEquivalent(JSDocInfo jsDoc1, JSDocInfo jsDoc2) {
    if (jsDoc1 == null && jsDoc2 == null) {
      return true;
    }
    if (jsDoc1 == null || jsDoc2 == null) {
      return false;
    }

    if (!Objects.equals(jsDoc1.getParameterNames(), jsDoc2.getParameterNames())) {
      return false;
    }
    for (String param : jsDoc1.getParameterNames()) {
      if (!Objects.equals(jsDoc1.getParameterType(param), jsDoc2.getParameterType(param))) {
        return false;
      }
    }

    if (jsDoc1.getMarkers().size() != jsDoc2.getMarkers().size()) {
      return false;
    }
    Iterator<Marker> it1 = jsDoc1.getMarkers().iterator();
    Iterator<Marker> it2 = jsDoc2.getMarkers().iterator();
    while (it1.hasNext()) {
      if (!Marker.areEquivalent(it1.next(), it2.next())) {
        return false;
      }
    }

    return Objects.equals(jsDoc1.getAuthors(), jsDoc2.getAuthors())
        && Objects.equals(jsDoc1.getBaseType(), jsDoc2.getBaseType())
        && Objects.equals(jsDoc1.getBlockDescription(), jsDoc2.getBlockDescription())
        && Objects.equals(jsDoc1.getFileOverview(), jsDoc2.getFileOverview())
        && Objects.equals(jsDoc1.getImplementedInterfaces(), jsDoc2.getImplementedInterfaces())
        && Objects.equals(jsDoc1.getEnumParameterType(), jsDoc2.getEnumParameterType())
        && Objects.equals(jsDoc1.getExtendedInterfaces(), jsDoc2.getExtendedInterfaces())
        && Objects.equals(jsDoc1.getLendsName(), jsDoc2.getLendsName())
        && Objects.equals(jsDoc1.getLicense(), jsDoc2.getLicense())
        && Objects.equals(jsDoc1.getMeaning(), jsDoc2.getMeaning())
        && Objects.equals(jsDoc1.getModifies(), jsDoc2.getModifies())
        && Objects.equals(jsDoc1.getOriginalCommentString(), jsDoc2.getOriginalCommentString())
        && Objects.equals(jsDoc1.getPropertyBitField(), jsDoc2.getPropertyBitField())
        && Objects.equals(jsDoc1.getReferences(), jsDoc2.getReferences())
        && Objects.equals(jsDoc1.getReturnDescription(), jsDoc2.getReturnDescription())
        && Objects.equals(jsDoc1.getReturnType(), jsDoc2.getReturnType())
        && Objects.equals(jsDoc1.getSuppressions(), jsDoc2.getSuppressions())
        && Objects.equals(jsDoc1.getTemplateTypeNames(), jsDoc2.getTemplateTypeNames())
        && Objects.equals(jsDoc1.getThisType(), jsDoc2.getThisType())
        && Objects.equals(jsDoc1.getThrownTypes(), jsDoc2.getThrownTypes())
        && Objects.equals(jsDoc1.getTypedefType(), jsDoc2.getTypedefType())
        && Objects.equals(jsDoc1.getType(), jsDoc2.getType())
        && Objects.equals(jsDoc1.getVersion(), jsDoc2.getVersion())
        && Objects.equals(jsDoc1.getVisibility(), jsDoc2.getVisibility())
        && jsDoc1.bitset == jsDoc2.bitset;
  }

  boolean isDocumentationIncluded() {
    return includeDocumentation;
  }

  void setConsistentIdGenerator(boolean value) {
    setFlag(value, MASK_CONSISTIDGEN);
  }

  void setStableIdGenerator(boolean value) {
    setFlag(value, MASK_STALBEIDGEN);
  }

  void setMappedIdGenerator(boolean value) {
    setFlag(value, MASK_MAPPEDIDGEN);
  }

  void setConstant(boolean value) {
    setFlag(value, MASK_CONSTANT);
  }

  void setConstructor(boolean value) {
    setFlag(value, MASK_CONSTRUCTOR);
  }

  void setUnrestricted() {
    setFlag(true, MASK_UNRESTRICTED);
  }

  void setStruct() {
    setFlag(true, MASK_STRUCT);
  }

  void setDict() {
    setFlag(true, MASK_DICT);
  }

  void setDefine(boolean value) {
    setFlag(value, MASK_DEFINE);
  }

  void setHidden(boolean value) {
    setFlag(value, MASK_HIDDEN);
  }

  void setShouldPreserveTry(boolean value) {
    setFlag(value, MASK_PRESERVETRY);
  }

  void setOverride(boolean value) {
    setFlag(value, MASK_OVERRIDE);
  }

  void setNoAlias(boolean value) {
    setFlag(value, MASK_NOALIAS);
  }

  void setDeprecated(boolean value) {
    setFlag(value, MASK_DEPRECATED);
  }

  void setInterface(boolean value) {
    setFlag(value, MASK_INTERFACE);
  }

  void setExport(boolean value) {
    setFlag(value, MASK_EXPORT);
  }

  void setExpose(boolean value) {
    setFlag(value, MASK_EXPOSE);
  }

  void setIdGenerator(boolean value) {
    setFlag(value, MASK_IDGEN);
  }

  void setImplicitCast(boolean value) {
    setFlag(value, MASK_IMPLICITCAST);
  }

  void setNoSideEffects(boolean value) {
    setFlag(value, MASK_NOSIDEEFFECTS);
  }

  void setExterns(boolean value) {
    setFlag(value, MASK_EXTERNS);
  }

  void setNoCompile(boolean value) {
    setFlag(value, MASK_NOCOMPILE);
  }

  void setNoCollapse(boolean value) {
    setFlag(value, MASK_NOCOLLAPSE);
  }

  private void setFlag(boolean value, int mask) {
    if (value) {
      bitset |= mask;
    } else {
      bitset &= ~mask;
    }
  }

  void setImplicitMatch(boolean value) {
    setFlag(value, MASK_RECORD);
  }

  /**
   * @return whether the {@code @consistentIdGenerator} is present on
   * this {@link JSDocInfo}
   */
  public boolean isConsistentIdGenerator() {
    return getFlag(MASK_CONSISTIDGEN);
  }

  /**
   * @return whether the {@code @stableIdGenerator} is present on this {@link JSDocInfo}.
   */
  public boolean isStableIdGenerator() {
    return getFlag(MASK_STALBEIDGEN);
  }

  /**
   * @return whether the {@code @stableIdGenerator} is present on this {@link JSDocInfo}.
   */
  public boolean isMappedIdGenerator() {
    return getFlag(MASK_MAPPEDIDGEN);
  }

  /**
   * Returns whether the {@code @const} annotation is present on this
   * {@link JSDocInfo}.
   */
  public boolean isConstant() {
    return getFlag(MASK_CONSTANT) || isDefine();
  }

  public boolean hasConstAnnotation() {
    return getFlag(MASK_CONSTANT);
  }

  /**
   * Returns whether the {@code @constructor} annotation is present on this
   * {@link JSDocInfo}.
   */
  public boolean isConstructor() {
    return getFlag(MASK_CONSTRUCTOR);
  }

  /**
   * Returns whether the {@code @record} annotation is present on this
   * {@link JSDocInfo}.
   */
  public boolean usesImplicitMatch() {
    return getFlag(MASK_RECORD);
  }

  /**
   * Returns whether the {@code @unrestricted} annotation is present on this
   * {@link JSDocInfo}.
   */
  public boolean makesUnrestricted() {
    return getFlag(MASK_UNRESTRICTED);
  }

  /**
   * Returns whether the {@code @struct} annotation is present on this
   * {@link JSDocInfo}.
   */
  public boolean makesStructs() {
    return getFlag(MASK_STRUCT);
  }

  /**
   * Returns whether the {@code @dict} annotation is present on this
   * {@link JSDocInfo}.
   */
  public boolean makesDicts() {
    return getFlag(MASK_DICT);
  }

  /**
   * Returns whether the {@code @define} annotation is present on this
   * {@link JSDocInfo}. If this annotation is present, then the
   * {@link #getType()} method will retrieve the define type.
   */
  public boolean isDefine() {
    return getFlag(MASK_DEFINE);
  }

  /**
   * Returns whether the {@code @hidden} annotation is present on this
   * {@link JSDocInfo}.
   */
  public boolean isHidden() {
    return getFlag(MASK_HIDDEN);
  }

  /**
   * Returns whether the {@code @preserveTry} annotation is present on this
   * {@link JSDocInfo}.
   */
  public boolean shouldPreserveTry() {
    return getFlag(MASK_PRESERVETRY);
  }

  /**
   * Returns whether the {@code @override} annotation is present on this
   * {@link JSDocInfo}.
   */
  public boolean isOverride() {
    return getFlag(MASK_OVERRIDE);
  }

  /**
   * Returns whether the {@code @noalias} annotation is present on this
   * {@link JSDocInfo}.
   */
  public boolean isNoAlias() {
    return getFlag(MASK_NOALIAS);
  }

  /**
   * Returns whether the {@code @deprecated} annotation is present on this
   * {@link JSDocInfo}.
   */
  public boolean isDeprecated() {
    return getFlag(MASK_DEPRECATED);
  }

  /**
   * Returns whether the {@code @interface} annotation is present on this
   * {@link JSDocInfo}.
   */
  public boolean isInterface() {
    return getFlag(MASK_INTERFACE) || getFlag(MASK_RECORD);
  }

  public boolean isConstructorOrInterface() {
    return isConstructor() || isInterface();
  }

  /**
   * Returns whether the {@code @export} annotation is present on this
   * {@link JSDocInfo}.
   */
  public boolean isExport() {
    return getFlag(MASK_EXPORT);
  }

  /**
   * Returns whether the {@code @expose} annotation is present on this
   * {@link JSDocInfo}.
   */
  public boolean isExpose() {
    return getFlag(MASK_EXPOSE);
  }

  /**
   * @return whether the {@code @idGenerator} is present on
   * this {@link JSDocInfo}
   */
  public boolean isIdGenerator() {
    return getFlag(MASK_IDGEN);
  }

  /**
   * Returns whether the {@code @implicitCast} annotation is present on this
   * {@link JSDocInfo}.
   */
  public boolean isImplicitCast() {
    return getFlag(MASK_IMPLICITCAST);
  }

  /**
   * Returns whether the {@code @nosideeffects} annotation is present on this
   * {@link JSDocInfo}.
   */
  public boolean isNoSideEffects() {
    return getFlag(MASK_NOSIDEEFFECTS);
  }

  /**
   * Returns whether the {@code @externs} annotation is present on this
   * {@link JSDocInfo}.
   */
  public boolean isExterns() {
    return getFlag(MASK_EXTERNS);
  }

  /**
   * Returns whether the {@code @nocompile} annotation is present on this
   * {@link JSDocInfo}.
   */
  public boolean isNoCompile() {
    return getFlag(MASK_NOCOMPILE);
  }

  /**
   * Returns whether the {@code @nocompile} annotation is present on this
   * {@link JSDocInfo}.
   */
  public boolean isNoCollapse() {
    return getFlag(MASK_NOCOLLAPSE);
  }

  /**
   * @return Whether there is a declaration present on this {@link JSDocInfo}.
   */
  public boolean containsDeclaration() {
    return (hasType()
        || hasReturnType()
        || hasEnumParameterType()
        || hasTypedefType()
        || hasThisType()
        || getParameterCount() > 0
        || visibility != Visibility.INHERITED
        || getFlag(MASK_CONSTANT
            | MASK_CONSTRUCTOR
            | MASK_DEFINE
            | MASK_OVERRIDE
            | MASK_NOALIAS
            | MASK_EXPORT
            | MASK_EXPOSE
            | MASK_DEPRECATED
            | MASK_INTERFACE
            | MASK_IMPLICITCAST
            | MASK_NOSIDEEFFECTS
            | MASK_RECORD));
  }

  /**
   * @return Whether there is a declaration of a callable type.
   */
  public boolean containsFunctionDeclaration() {
    boolean hasFunctionType = hasType() && getType().getRoot().isFunction();
    return hasFunctionType
        || hasReturnType()
        || hasThisType()
        || getParameterCount() > 0
        || getFlag(MASK_CONSTRUCTOR)
        || (getFlag(MASK_NOSIDEEFFECTS) && !hasType());
  }

  // For jsdocs that create new types. Not to be confused with jsdocs that
  // declare the type of a variable or property.
  public boolean containsTypeDefinition() {
    return isConstructor() || isInterface()
        || hasEnumParameterType() || hasTypedefType();
  }

  private boolean getFlag(int mask) {
    return (bitset & mask) != 0x00;
  }

  void setVisibility(Visibility visibility) {
    this.visibility = visibility;
  }

  private void lazyInitInfo() {
    if (info == null) {
      info = new LazilyInitializedInfo();
    }
  }

  /**
   * Lazily initializes the documentation information object, but only
   * if the JSDocInfo was told to keep such information around.
   */
  private boolean lazyInitDocumentation() {
    if (!includeDocumentation) {
      return false;
    }

    if (documentation == null) {
      documentation = new LazilyInitializedDocumentation();
    }

    return true;
  }

  /**
   * Adds a marker to the documentation (if it exists) and
   * returns the marker. Returns null otherwise.
   */
  Marker addMarker() {
    if (!lazyInitDocumentation()) {
      return null;
    }

    if (documentation.markers == null) {
      documentation.markers = new ArrayList<>();
    }

    Marker marker = new Marker();
    documentation.markers.add(marker);
    return marker;
  }

  /**
   * Sets the deprecation reason.
   *
   * @param reason The deprecation reason
   */
  boolean setDeprecationReason(String reason) {
    lazyInitInfo();

    if (info.deprecated != null) {
      return false;
    }

    info.deprecated = reason;
    return true;
  }

  /**
   * Add a suppressed warning.
   */
  void addSuppression(String suppression) {
    lazyInitInfo();

    if (info.suppressions == null) {
      info.suppressions = ImmutableSet.of(suppression);
    } else {
      info.suppressions = new ImmutableSet.Builder<String>()
          .addAll(info.suppressions)
          .add(suppression)
          .build();
    }
  }

  /**
   * Sets suppressed warnings.
   * @param suppressions A list of suppressed warning types.
   */
  boolean setSuppressions(Set<String> suppressions) {
    lazyInitInfo();

    if (info.suppressions != null) {
      return false;
    }

    info.suppressions = ImmutableSet.copyOf(suppressions);
    return true;
  }

  /**
   * Sets modifies values.
   * @param modifies A list of modifies types.
   */
  boolean setModifies(Set<String> modifies) {
    lazyInitInfo();

    if (info.modifies != null) {
      return false;
    }

    info.modifies = ImmutableSet.copyOf(modifies);
    return true;
  }

  /**
   * Documents the version.
   */
  boolean documentVersion(String version) {
    if (!lazyInitDocumentation()) {
      return true;
    }

    if (documentation.version != null) {
      return false;
    }

    documentation.version = version;
    return true;
  }

  /**
   * Documents a reference (i.e. adds a "see" reference to the list).
   */
  boolean documentReference(String reference) {
    if (!lazyInitDocumentation()) {
      return true;
    }

    if (documentation.sees == null) {
      documentation.sees = new ArrayList<>();
    }

    documentation.sees.add(reference);
    return true;
  }

  /**
   * Documents the author (i.e. adds it to the author list).
   */
  boolean documentAuthor(String author) {
    if (!lazyInitDocumentation()) {
      return true;
    }

    if (documentation.authors == null) {
      documentation.authors = new ArrayList<>();
    }

    documentation.authors.add(author);
    return true;
  }

  /**
   * Documents the throws (i.e. adds it to the throws list).
   */
  boolean documentThrows(JSTypeExpression type, String throwsDescription) {
    if (!lazyInitDocumentation()) {
      return true;
    }

    if (documentation.throwsDescriptions == null) {
      documentation.throwsDescriptions = new LinkedHashMap<>();
    }

    if (!documentation.throwsDescriptions.containsKey(type)) {
      documentation.throwsDescriptions.put(type, throwsDescription);
      return true;
    }

    return false;
  }


  /**
   * Documents a parameter. Parameters are described using the {@code @param}
   * annotation.
   *
   * @param parameter the parameter's name
   * @param description the parameter's description
   */
  boolean documentParam(String parameter, String description) {
    if (!lazyInitDocumentation()) {
      return true;
    }

    if (documentation.parameters == null) {
      documentation.parameters = new LinkedHashMap<>();
    }

    if (!documentation.parameters.containsKey(parameter)) {
      documentation.parameters.put(parameter, description);
      return true;
    } else {
      return false;
    }
  }

  /**
   * Documents the block-level comment/description.
   *
   * @param description the description
   */
  boolean documentBlock(String description) {
    if (!lazyInitDocumentation()) {
      return true;
    }

    if (documentation.blockDescription != null) {
      return false;
    }

    documentation.blockDescription = description;
    return true;
  }

  /**
   * Documents the fileoverview comment/description.
   *
   * @param description the description
   */
  boolean documentFileOverview(String description) {
    setFlag(true, MASK_FILEOVERVIEW);
    if (!lazyInitDocumentation()) {
      return true;
    }

    if (documentation.fileOverview != null) {
      return false;
    }

    documentation.fileOverview = description;
    return true;
  }

  /**
   * Documents the return value. Return value is described using the
   * {@code @return} annotation.
   *
   * @param description the return value's description
   */
  boolean documentReturn(String description) {
    if (!lazyInitDocumentation()) {
      return true;
    }

    if (documentation.returnDescription != null) {
      return false;
    }

    documentation.returnDescription = description;
    return true;
  }

  /**
   * Declares a parameter. Parameters are described using the {@code @param}
   * annotation.
   *
   * @param jsType the parameter's type, it may be {@code null} when the
   *     {@code @param} annotation did not specify a type.
   * @param parameter the parameter's name
   */
  boolean declareParam(JSTypeExpression jsType, String parameter) {
    lazyInitInfo();
    if (info.parameters == null) {
      info.parameters = new LinkedHashMap<>();
    }
    if (!info.parameters.containsKey(parameter)) {
      info.parameters.put(parameter, jsType);
      return true;
    } else {
      return false;
    }
  }

  /**
   * Declares a template type name. Template type names are described using the
   * {@code @template} annotation.
   *
   * @param newTemplateTypeName the template type name.
   */
  boolean declareTemplateTypeName(String newTemplateTypeName) {
    lazyInitInfo();

    if (isTypeTransformationName(newTemplateTypeName) || hasTypedefType()) {
      return false;
    }
    if (info.templateTypeNames == null){
      info.templateTypeNames = new ArrayList<>();
    } else if (info.templateTypeNames.contains(newTemplateTypeName)) {
      return false;
    }

    info.templateTypeNames.add(newTemplateTypeName);
    return true;
  }

  private boolean isTemplateTypeName(String name) {
    if (info.templateTypeNames == null) {
      return false;
    }
    return info.templateTypeNames.contains(name);
  }

  private boolean isTypeTransformationName(String name) {
    if (info.typeTransformations == null) {
      return false;
    }
    return info.typeTransformations.containsKey(name);
  }

  /**
   * Declares a type transformation expression. These expressions are described
   * using a {@code @template} annotation of the form
   * {@code @template T := TTL-Expr =:}
   *
   * @param newName The name associated to the type transformation.
   * @param expr The type transformation expression.
   */
  boolean declareTypeTransformation(String newName, Node expr) {
    lazyInitInfo();

    if (isTemplateTypeName(newName)) {
      return false;
    }
    if (info.typeTransformations == null){
      // A LinkedHashMap is used to keep the insertion order. The type
      // transformation expressions will be evaluated in this order.
      info.typeTransformations = new LinkedHashMap<>();
    } else if (info.typeTransformations.containsKey(newName)) {
      return false;
    }
    info.typeTransformations.put(newName, expr);
    return true;
  }

  /**
   * Declares that the method throws a given type.
   *
   * @param jsType The type that can be thrown by the method.
   */
  boolean declareThrows(JSTypeExpression jsType) {
    lazyInitInfo();

    if (info.thrownTypes == null) {
      info.thrownTypes = new ArrayList<>();
    }

    info.thrownTypes.add(jsType);
    return true;
  }

  /**
   * Gets the visibility specified by {@code @private}, {@code @protected} or
   * {@code @public} annotation. If no visibility is specified, visibility
   * is inherited from the base class.
   */
  public Visibility getVisibility() {
    return visibility;
  }

  /**
   * Gets the type of a given named parameter.
   * @param parameter the parameter's name
   * @return the parameter's type or {@code null} if this parameter is not
   *     defined or has a {@code null} type
   */
  public JSTypeExpression getParameterType(String parameter) {
    if (info == null || info.parameters == null) {
      return null;
    }
    return info.parameters.get(parameter);
  }

  /**
   * Returns whether the parameter is defined.
   */
  public boolean hasParameter(String parameter) {
    if (info == null || info.parameters == null) {
      return false;
    }
    return info.parameters.containsKey(parameter);
  }

  /**
   * Returns whether the parameter has an attached type.
   *
   * @return {@code true} if the parameter has an attached type, {@code false}
   *     if the parameter has no attached type or does not exist.
   */
  public boolean hasParameterType(String parameter) {
    return getParameterType(parameter) != null;
  }

  /**
   * Returns the set of names of the defined parameters. The iteration order
   * of the returned set is the order in which parameters are defined in the
   * JSDoc, rather than the order in which the function declares them.
   *
   * @return the set of names of the defined parameters. The returned set is
   *     immutable.
   */
  public Set<String> getParameterNames() {
    if (info == null || info.parameters == null) {
      return ImmutableSet.of();
    }
    return ImmutableSet.copyOf(info.parameters.keySet());
  }

  /**
   * Returns the nth name in the defined parameters. The iteration order
   * is the order in which parameters are defined in the JSDoc, rather
   * than the order in which the function declares them.
   */
  public String getParameterNameAt(int index) {
    if (info == null || info.parameters == null) {
      return null;
    }
    return Iterables.get(info.parameters.keySet(), index);
  }

  /**
   * Gets the number of parameters defined.
   */
  public int getParameterCount() {
    if (info == null || info.parameters == null) {
      return 0;
    }
    return info.parameters.size();
  }

  void setType(JSTypeExpression type) {
    setType(type, TYPEFIELD_TYPE);
  }

  void setInlineType() {
    this.inlineType = true;
  }

  void setReturnType(JSTypeExpression type) {
    setType(type, TYPEFIELD_RETURN);
  }

  void setEnumParameterType(JSTypeExpression type) {
    setType(type, TYPEFIELD_ENUM);
  }

  boolean declareTypedefType(JSTypeExpression type) {
    if (getTemplateTypeNames().isEmpty()) {
      setType(type, TYPEFIELD_TYPEDEF);
      return true;
    }
    return false;
  }

  private void setType(JSTypeExpression type, int mask) {
    if ((bitset & MASK_TYPEFIELD) != 0) {
      throw new IllegalStateException(
          "API tried to add two incompatible type tags. "
          + "This should have been blocked and emitted a warning.");
    }
    this.bitset = (bitset & MASK_FLAGS) | mask;
    this.type = type;
  }

  /**
   * Returns the list of thrown types.
   */
  public List<JSTypeExpression> getThrownTypes() {
    if (info == null || info.thrownTypes == null) {
      return ImmutableList.of();
    }
    return Collections.unmodifiableList(info.thrownTypes);
  }

  /**
   * Get the message for a given thrown type.
   */
  public String getThrowsDescriptionForType(JSTypeExpression type) {
    if (documentation == null || documentation.throwsDescriptions == null) {
      return null;
    }

    return documentation.throwsDescriptions.get(type);
  }

  /**
   * Returns whether a type, specified using the {@code @type} annotation, is
   * present on this JSDoc.
   */
  public boolean hasType() {
    return hasType(TYPEFIELD_TYPE);
  }

  /**
   * Returns whether an enum parameter type, specified using the {@code @enum}
   * annotation, is present on this JSDoc.
   */
  public boolean hasEnumParameterType() {
    return hasType(TYPEFIELD_ENUM);
  }

  /**
   * Returns whether a typedef parameter type, specified using the
   * {@code @typedef} annotation, is present on this JSDoc.
   */
  public boolean hasTypedefType() {
    return hasType(TYPEFIELD_TYPEDEF);
  }

  /**
   * Returns whether this {@link JSDocInfo} contains a type for {@code @return}
   * annotation.
   */
  public boolean hasReturnType() {
    return hasType(TYPEFIELD_RETURN);
  }

  private boolean hasType(int mask) {
    return (bitset & MASK_TYPEFIELD) == mask;
  }

  public boolean hasTypeInformation() {
    return (bitset & MASK_TYPEFIELD) != 0;
  }

  /**
   * Gets the type specified by the {@code @type} annotation.
   */
  public JSTypeExpression getType() {
    return getType(TYPEFIELD_TYPE);
  }

  /**
   * Returns whether the type annotation was inlined.
   */
  public boolean isInlineType() {
    return inlineType;
  }

  /**
   * Gets the return type specified by the {@code @return} annotation.
   */
  public JSTypeExpression getReturnType() {
    return getType(TYPEFIELD_RETURN);
  }

  /**
   * Gets the enum parameter type specified by the {@code @enum} annotation.
   */
  public JSTypeExpression getEnumParameterType() {
    return getType(TYPEFIELD_ENUM);
  }

  /**
   * Gets the typedef type specified by the {@code @type} annotation.
   */
  public JSTypeExpression getTypedefType() {
    return getType(TYPEFIELD_TYPEDEF);
  }

  private JSTypeExpression getType(int typefield) {
    if ((MASK_TYPEFIELD & bitset) == typefield) {
      return type;
    } else {
      return null;
    }
  }

  /**
   * Gets the type specified by the {@code @this} annotation.
   */
  public JSTypeExpression getThisType() {
    return thisType;
  }

  /**
   * Sets the type specified by the {@code @this} annotation.
   */
  void setThisType(JSTypeExpression type) {
    this.thisType = type;
  }

  /**
   * Returns whether this {@link JSDocInfo} contains a type for {@code @this}
   * annotation.
   */
  public boolean hasThisType() {
    return thisType != null;
  }

  void setBaseType(JSTypeExpression type) {
    lazyInitInfo();
    info.baseType = type;
  }

  /**
   * Gets the base type specified by the {@code @extends} annotation.
   */
  public JSTypeExpression getBaseType() {
    return (info == null) ? null : info.baseType;
  }

  /**
   * Gets the description specified by the {@code @desc} annotation.
   */
  public String getDescription() {
    return (info == null) ? null : info.description;
  }

  void setDescription(String desc) {
    lazyInitInfo();
    info.description = desc;
  }

  /**
   * Gets the meaning specified by the {@code @meaning} annotation.
   *
   * <p>In localization systems, two messages with the same content but
   * different "meanings" may be translated differently. By default, we
   * use the name of the variable that the message is initialized to as
   * the "meaning" of the message.
   *
   * <p>But some code generators (like Closure Templates) inject their own
   * meaning with the jsdoc {@code @meaning} annotation.
   */
  public String getMeaning() {
    return (info == null) ? null : info.meaning;
  }

  void setMeaning(String meaning) {
    lazyInitInfo();
    info.meaning = meaning;
  }

  /**
   * Gets the name we're lending to in a {@code @lends} annotation.
   *
   * <p>In many reflection APIs, you pass an anonymous object to a function,
   * and that function mixes the anonymous object into another object.
   * The {@code @lends} annotation allows the type system to track
   * those property assignments.
   */
  public String getLendsName() {
    return (info == null) ? null : info.lendsName;
  }

  void setLendsName(String name) {
    lazyInitInfo();
    info.lendsName = name;
  }

  /**
   * Returns whether JSDoc is annotated with {@code @ngInject} annotation.
   */
  public boolean isNgInject() {
    return (info != null) && info.isBitSet(Property.NG_INJECT);
  }

  void setNgInject(boolean ngInject) {
    lazyInitInfo();
    info.setBit(Property.NG_INJECT, ngInject);
  }

  /**
   * Returns whether JSDoc is annotated with {@code @jaggerInject} annotation.
   */
  public boolean isJaggerInject() {
    return (info != null) && info.isBitSet(Property.JAGGER_INJECT);
  }

  void setJaggerInject(boolean jaggerInject) {
    lazyInitInfo();
    info.setBit(Property.JAGGER_INJECT, jaggerInject);
  }

  /**
   * Returns whether JSDoc is annotated with {@code @jaggerProvidePromise} annotation.
   */
  public boolean isJaggerProvide() {
    return (info != null) && info.isBitSet(Property.JAGGER_PROVIDE);
  }

  void setJaggerProvide(boolean jaggerProvide) {
    lazyInitInfo();
    info.setBit(Property.JAGGER_PROVIDE, jaggerProvide);
  }

  /**
   * Returns whether JSDoc is annotated with {@code @jaggerProvidePromise} annotation.
   */
  public boolean isJaggerProvidePromise() {
    return (info != null) && info.isBitSet(Property.JAGGER_PROVIDE_PROMISE);
  }

  void setJaggerProvidePromise(boolean jaggerProvidePromise) {
    lazyInitInfo();
    info.setBit(Property.JAGGER_PROVIDE_PROMISE, jaggerProvidePromise);
  }

  /**
   * Returns whether JSDoc is annotated with {@code @jaggerModule} annotation.
   */
  public boolean isJaggerModule() {
    return (info != null) && info.isBitSet(Property.JAGGER_MODULE);
  }

  void setJaggerModule(boolean jaggerModule) {
    lazyInitInfo();
    info.setBit(Property.JAGGER_MODULE, jaggerModule);
  }

  /**
   * Returns whether JSDoc is annotated with {@code @wizaction} annotation.
   */
  public boolean isWizaction() {
    return (info != null) && info.isBitSet(Property.WIZ_ACTION);
  }

  void setWizaction(boolean wizaction) {
    lazyInitInfo();
    info.setBit(Property.WIZ_ACTION, wizaction);
  }

  /**
   * Returns whether JSDoc is annotated with {@code @polymerBehavior} annotation.
   */
  public boolean isPolymerBehavior() {
    return (info != null) && info.isBitSet(Property.POLYMER_BEHAVIOR);
  }

  void setPolymerBehavior(boolean polymerBehavior) {
    lazyInitInfo();
    info.setBit(Property.POLYMER_BEHAVIOR, polymerBehavior);
  }

  /**
   * Returns whether JSDoc is annotated with {@code @disposes} annotation.
   */
  public boolean isDisposes() {
    return (info == null) ? false : info.disposedParameters != null;
  }

  boolean setDisposedParameter(String parameterName) {
    lazyInitInfo();
    // Lazily initialize disposedParameters
    if (info.disposedParameters == null) {
      info.disposedParameters = new HashSet<>();
    }

    if (info.disposedParameters.contains(parameterName)) {
      return false;
    } else {
      info.disposedParameters.add(parameterName);
      return true;
    }
  }

  /**
   * Return whether the function disposes of specified parameter.
   */
  public boolean disposesOf(String parameterName) {
    return isDisposes() && info.disposedParameters.contains(parameterName);
  }

  /**
   * Gets the description specified by the {@code @license} annotation.
   */
  public String getLicense() {
    return (info == null) ? null : info.license;
  }

  /**
   * @param license String containing new license text.
   */
  void setLicense(String license) {
    lazyInitInfo();
    info.license = license;
  }

  @Override
  public String toString() {
    return "JSDocInfo";
  }

  @VisibleForTesting
  public String toStringVerbose() {
    return MoreObjects.toStringHelper(this)
        .add("bitset", (bitset == 0) ? null : Integer.toHexString(bitset))
        .add("documentation", documentation)
        .add("info", info)
        .add("originalComment", getOriginalCommentString())
        .add("thisType", thisType)
        .add("type", type)
        .add("visibility", visibility)
        .omitNullValues()
        .toString();
  }

  /**
   * Returns whether this {@link JSDocInfo} contains a type for {@code @extends}
   * annotation.
   */
  public boolean hasBaseType() {
    return getBaseType() != null;
  }

  /**
   * Adds an implemented interface. Returns whether the interface was added. If
   * the interface was already present in the list, it won't get added again.
   */
  boolean addImplementedInterface(JSTypeExpression interfaceName) {
    lazyInitInfo();
    if (info.implementedInterfaces == null) {
      info.implementedInterfaces = new ArrayList<>(2);
    }
    if (info.implementedInterfaces.contains(interfaceName)) {
      return false;
    }
    info.implementedInterfaces.add(interfaceName);
    return true;
  }

  /**
   * Returns the types specified by the {@code @implements} annotation.
   *
   * @return An immutable list of JSTypeExpression objects that can
   *    be resolved to types.
   */
  public List<JSTypeExpression> getImplementedInterfaces() {
    if (info == null || info.implementedInterfaces == null) {
      return ImmutableList.of();
    }
    return Collections.unmodifiableList(info.implementedInterfaces);
  }

  /**
   * Gets the number of interfaces specified by the {@code @implements}
   * annotation.
   */
  public int getImplementedInterfaceCount() {
    if (info == null || info.implementedInterfaces == null) {
      return 0;
    }
    return info.implementedInterfaces.size();
  }

  /**
   * Adds an extended interface (for interface only).
   * Returns whether the type was added.
   * if the type was already present in the list, it won't get added again.
   */
  boolean addExtendedInterface(JSTypeExpression type) {
    lazyInitInfo();
    if (info.extendedInterfaces == null) {
      info.extendedInterfaces = new ArrayList<>(2);
    }
    if (info.extendedInterfaces.contains(type)) {
      return false;
    }
    info.extendedInterfaces.add(type);
    return true;
  }

  /**
   * Returns the interfaces extended by an interface
   *
   * @return An immutable list of JSTypeExpression objects that can
   *    be resolved to types.
   */
  public List<JSTypeExpression> getExtendedInterfaces() {
    if (info == null || info.extendedInterfaces == null) {
      return ImmutableList.of();
    }
    return Collections.unmodifiableList(info.extendedInterfaces);
  }

  /**
   * Gets the number of extended interfaces specified
   */
  public int getExtendedInterfacesCount() {
    if (info == null || info.extendedInterfaces == null) {
      return 0;
    }
    return info.extendedInterfaces.size();
  }

  /**
   * Returns the deprecation reason or null if none specified.
   */
  public String getDeprecationReason() {
    return info == null ? null : info.deprecated;
  }

  /**
   * Returns the set of suppressed warnings.
   */
  public Set<String> getSuppressions() {
    Set<String> suppressions = info == null ? null : info.suppressions;
    return suppressions == null ? Collections.<String>emptySet() : suppressions;
  }

  /**
   * Returns the set of sideeffect notations.
   */
  public Set<String> getModifies() {
    Set<String> modifies = info == null ? null : info.modifies;
    return modifies == null ? Collections.<String>emptySet() : modifies;
  }

  private Integer getPropertyBitField() {
    return info == null ? null : info.propertyBitField;
  }

  void mergePropertyBitfieldFrom(JSDocInfo other) {
    if (other.info != null) {
      lazyInitInfo();
      info.propertyBitField |= other.getPropertyBitField();
    }
  }

  /**
   * Returns whether a description exists for the parameter with the specified
   * name.
   */
  public boolean hasDescriptionForParameter(String name) {
    if (documentation == null || documentation.parameters == null) {
      return false;
    }

    return documentation.parameters.containsKey(name);
  }

  /**
   * Returns the description for the parameter with the given name, if its
   * exists.
   */
  public String getDescriptionForParameter(String name) {
    if (documentation == null || documentation.parameters == null) {
      return null;
    }

    return documentation.parameters.get(name);
  }

  /**
   * Returns the list of authors or null if none.
   */
  public Collection<String> getAuthors() {
    return documentation == null ? null : documentation.authors;
  }

  /**
   * Returns the list of references or null if none.
   */
  public Collection<String> getReferences() {
    return documentation == null ? null : documentation.sees;
  }

  /**
   * Returns the version or null if none.
   */
  public String getVersion() {
    return documentation == null ? null : documentation.version;
  }

  /**
   * Returns the description of the returned object or null if none specified.
   */
  public String getReturnDescription() {
    return documentation == null ? null : documentation.returnDescription;
  }

  /**
   * Returns the block-level description or null if none specified.
   */
  public String getBlockDescription() {
    return documentation == null ? null : documentation.blockDescription;
  }

  /**
   * Returns whether this has a fileoverview flag.
   */
  public boolean hasFileOverview() {
    return getFlag(MASK_FILEOVERVIEW);
  }

  /**
   * Returns the file overview or null if none specified.
   */
  public String getFileOverview() {
    return documentation == null ? null : documentation.fileOverview;
  }

  /** Gets the list of all markers for the documentation in this JSDoc. */
  public Collection<Marker> getMarkers() {
    return (documentation == null || documentation.markers == null)
        ? ImmutableList.<Marker>of() : documentation.markers;
  }

  /** Gets the template type name. */
  public ImmutableList<String> getTemplateTypeNames() {
    if (info == null || info.templateTypeNames == null) {
      return ImmutableList.of();
    }
    return ImmutableList.copyOf(info.templateTypeNames);
  }

  /** Gets the type transformations. */
  public ImmutableMap<String, Node> getTypeTransformations() {
    if (info == null || info.typeTransformations == null) {
      return ImmutableMap.<String, Node>of();
    }
    return ImmutableMap.copyOf(info.typeTransformations);
  }

  /**
   * Returns a collection of all type nodes that are a part of this JSDocInfo.
   * This includes @type, @this, @extends, @implements, @param, @throws,
   * and @return.  Any future type specific JSDoc should make sure to add the
   * appropriate nodes here.
   * @return collection of all type nodes
   */
  public Collection<Node> getTypeNodes() {
    List<Node> nodes = new ArrayList<>();

    if (type != null) {
      nodes.add(type.getRoot());
    }

    if (thisType != null) {
      nodes.add(thisType.getRoot());
    }

    if (info != null) {
      if (info.baseType != null) {
        nodes.add(info.baseType.getRoot());
      }

      if (info.extendedInterfaces != null) {
        for (JSTypeExpression interfaceType : info.extendedInterfaces) {
          nodes.add(interfaceType.getRoot());
        }
      }

      if (info.implementedInterfaces != null) {
        for (JSTypeExpression interfaceType : info.implementedInterfaces) {
          nodes.add(interfaceType.getRoot());
        }
      }

      if (info.parameters != null) {
        for (JSTypeExpression parameterType : info.parameters.values()) {
          if (parameterType != null) {
            nodes.add(parameterType.getRoot());
          }
        }
      }

      if (info.thrownTypes != null) {
        for (JSTypeExpression thrownType : info.thrownTypes) {
          if (thrownType != null) {
            nodes.add(thrownType.getRoot());
          }
        }
      }
    }

    return nodes;
  }

  public boolean hasModifies() {
    return info != null && info.modifies != null;
  }

  /**
   * Returns the original JSDoc comment string. Returns null unless
   * parseJsDocDocumentation is enabled via the ParserConfig.
   */
  public String getOriginalCommentString() {
    return documentation == null ? null : documentation.sourceComment;
  }

  void setOriginalCommentString(String sourceComment) {
    if (!lazyInitDocumentation()) {
      return;
    }
    documentation.sourceComment = sourceComment;
  }

  public int getOriginalCommentPosition() {
    return originalCommentPosition;
  }

  void setOriginalCommentPosition(int position) {
    originalCommentPosition = position;
  }
}
