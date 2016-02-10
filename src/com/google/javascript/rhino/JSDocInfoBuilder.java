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

import com.google.common.base.Preconditions;
import com.google.javascript.rhino.JSDocInfo.Visibility;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * A builder for {@link JSDocInfo} objects. This builder abstracts the
 * construction process of {@link JSDocInfo} objects whilst minimizing the
 * number of instances of {@link JSDocInfo} objects. It provides early
 * incompatibility detection among properties stored on the {@code JSDocInfo}
 * object being created.
 *
 */
public final class JSDocInfoBuilder {
  // the current JSDoc which is being populated
  private JSDocInfo currentInfo;

  // whether the current JSDocInfo has valuable information
  private boolean populated;

  // whether to include the documentation itself when parsing the JsDoc
  private boolean parseDocumentation;

  // the current marker, if any.
  private JSDocInfo.Marker currentMarker;

  public JSDocInfoBuilder(boolean parseDocumentation) {
    this(new JSDocInfo(parseDocumentation), parseDocumentation, false);
  }

  private JSDocInfoBuilder(
      JSDocInfo info, boolean parseDocumentation, boolean populated) {
    this.currentInfo = info;
    this.parseDocumentation = parseDocumentation;
    this.populated = populated;
  }

  public static JSDocInfoBuilder copyFrom(JSDocInfo info) {
    JSDocInfo clone = info.clone();
    if (clone.getVisibility() == Visibility.INHERITED) {
      clone.setVisibility(null);
    }
    return new JSDocInfoBuilder(clone, info.isDocumentationIncluded(), true);
  }

  public static JSDocInfoBuilder maybeCopyFrom(@Nullable JSDocInfo info) {
    if (info == null) {
      return new JSDocInfoBuilder(true);
    }
    return copyFrom(info);
  }

  /**
   * Sets the original JSDoc comment string. This is a no-op if the builder
   * isn't configured to record documentation.
   */
  public void recordOriginalCommentString(String sourceComment) {
    if (parseDocumentation) {
      currentInfo.setOriginalCommentString(sourceComment);
    }
  }

  /**
   * Sets the position of original JSDoc comment.
   */
  public void recordOriginalCommentPosition(int position) {
    if (parseDocumentation) {
      currentInfo.setOriginalCommentPosition(position);
    }
  }

  public boolean shouldParseDocumentation() {
    return parseDocumentation;
  }

  /**
   * Returns whether this builder is populated with information that can be
   * used to {@link #build} a {@link JSDocInfo} object.
   */
  public boolean isPopulated() {
    return populated;
  }

  /**
   * Returns whether this builder is populated with information that can be
   * used to {@link #build} a {@link JSDocInfo} object that has a
   * fileoverview tag.
   */
  public boolean isPopulatedWithFileOverview() {
    return isPopulated() &&
        (currentInfo.hasFileOverview() || currentInfo.isExterns() ||
         currentInfo.isNoCompile());
  }

  /**
   * Returns whether this builder recorded a description.
   */
  public boolean isDescriptionRecorded() {
    return currentInfo.getDescription() != null;
  }


  /**
   * Builds a {@link JSDocInfo} object based on the populated information and
   * returns it.
   *
   * @return a {@link JSDocInfo} object populated with the values given to this
   *     builder. If no value was populated, this method simply returns
   *     {@code null}
   */
  public JSDocInfo build() {
    return build(false);
  }

  /**
   * Builds a {@link JSDocInfo} object based on the populated information and
   * returns it. Once this method is called, the builder can be reused to build
   * another {@link JSDocInfo} object.
   *
   * @return a {@link JSDocInfo} object populated with the values given to this
   *     builder. If no value was populated, this method simply returns
   *     {@code null}
   */
  public JSDocInfo buildAndReset() {
    JSDocInfo info = build(false);
    if (currentInfo == null) {
      currentInfo = new JSDocInfo(parseDocumentation);
      populated = false;
    }
    return info;
  }

  /**
   * Builds a {@link JSDocInfo} object based on the populated information and
   * returns it.
   *
   * @param always Return an default JSDoc object.
   * @return a {@link JSDocInfo} object populated with the values given to this
   *     builder. If no value was populated and {@code always} is false, returns
   *     {@code null}. If {@code always} is true, returns a default JSDocInfo.
   */
  public JSDocInfo build(boolean always) {
    if (populated || always) {
      Preconditions.checkState(currentInfo != null);
      JSDocInfo built = currentInfo;
      currentInfo = null;
      populateDefaults(built);
      populated = false;
      return built;
    } else {
      return null;
    }
  }

  /** Generate defaults when certain parameters are not specified. */
  private static void populateDefaults(JSDocInfo info) {
    if (info.getVisibility() == null) {
      info.setVisibility(Visibility.INHERITED);
    }
  }

  /**
   * Adds a marker to the current JSDocInfo and populates the marker with the
   * annotation information.
   */
  public void markAnnotation(String annotation, int lineno, int charno) {
    JSDocInfo.Marker marker = currentInfo.addMarker();

    if (marker != null) {
      JSDocInfo.TrimmedStringPosition position =
          new JSDocInfo.TrimmedStringPosition();
      position.setItem(annotation);
      position.setPositionInformation(lineno, charno, lineno,
          charno + annotation.length());
      marker.setAnnotation(position);
      populated = true;
    }

    currentMarker = marker;
  }

  /**
   * Adds a textual block to the current marker.
   */
  public void markText(String text, int startLineno, int startCharno,
      int endLineno, int endCharno) {
    if (currentMarker != null) {
      JSDocInfo.StringPosition position = new JSDocInfo.StringPosition();
      position.setItem(text);
      position.setPositionInformation(startLineno, startCharno,
          endLineno, endCharno);
      currentMarker.setDescription(position);
    }
  }

  /**
   * Adds a type declaration to the current marker.
   */
  public void markTypeNode(Node typeNode, int lineno, int startCharno,
      int endLineno, int endCharno, boolean hasLC) {
    if (currentMarker != null) {
      JSDocInfo.TypePosition position = new JSDocInfo.TypePosition();
      position.setItem(typeNode);
      position.setHasBrackets(hasLC);
      position.setPositionInformation(lineno, startCharno,
          endLineno, endCharno);
      currentMarker.setType(position);
    }
  }

  /**
   * Adds a name declaration to the current marker.
   */
  public void markName(String name, StaticSourceFile file,
      int lineno, int charno) {
    if (currentMarker != null) {
      // Record the name as both a SourcePosition<String> and a
      // SourcePosition<Node>. The <String> form is deprecated,
      // because <Node> is more consistent with how other name
      // references are handled (see #markTypeNode)
      //
      // TODO(nicksantos): Remove all uses of the Name position
      // and replace them with the NameNode position.
      JSDocInfo.TrimmedStringPosition position =
          new JSDocInfo.TrimmedStringPosition();
      position.setItem(name);
      position.setPositionInformation(lineno, charno,
          lineno, charno + name.length());
      currentMarker.setName(position);

      JSDocInfo.NamePosition nodePos = new JSDocInfo.NamePosition();
      Node node = Node.newString(Token.NAME, name, lineno, charno);
      node.setLength(name.length());
      node.setStaticSourceFile(file);
      nodePos.setItem(node);
      nodePos.setPositionInformation(lineno, charno,
          lineno, charno + name.length());
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
    return currentInfo.documentBlock(description);
  }

  /**
   * Records a visibility.
   *
   * @return {@code true} if the visibility was recorded and {@code false}
   *     if it was already defined
   */
  public boolean recordVisibility(Visibility visibility) {
    if (currentInfo.getVisibility() == null) {
      populated = true;
      currentInfo.setVisibility(visibility);
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records a typed parameter.
   *
   * @return {@code true} if the typed parameter was recorded and
   *     {@code false} if a parameter with the same name was already defined
   */
  public boolean recordParameter(String parameterName, JSTypeExpression type) {
    if (!hasAnySingletonTypeTags()
        && currentInfo.declareParam(type, parameterName)) {
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records a parameter's description.
   *
   * @return {@code true} if the parameter's description was recorded and
   *     {@code false} if a parameter with the same name was already defined
   */
  public boolean recordParameterDescription(
      String parameterName, String description) {
    if (currentInfo.documentParam(parameterName, description)) {
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records a template type name.
   *
   * @return {@code true} if the template type name was recorded and
   *     {@code false} if the input template type name was already defined.
   */
  public boolean recordTemplateTypeName(String name) {
    if (currentInfo.declareTemplateTypeName(name)) {
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records a type transformation expression together with its template
   * type name.
   */
  public boolean recordTypeTransformation(String name, Node expr) {
    if (currentInfo.declareTypeTransformation(name, expr)) {
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records a thrown type.
   */
  public boolean recordThrowType(JSTypeExpression type) {
    if (type != null && !hasAnySingletonTypeTags()) {
      currentInfo.declareThrows(type);
      populated = true;
      return true;
    }
    return false;
  }

  /**
   * Records a throw type's description.
   *
   * @return {@code true} if the type's description was recorded and
   *     {@code false} if a description with the same type was already defined
   */
  public boolean recordThrowDescription(
      JSTypeExpression type, String description) {
    if (currentInfo.documentThrows(type, description)) {
      populated = true;
      return true;
    } else {
      return false;
    }
  }


  /**
   * Adds an author to the current information.
   */
  public boolean addAuthor(String author) {
    if (currentInfo.documentAuthor(author)) {
      populated = true;
      return true;
    } else {
      return false;
    }
  }


  /**
   * Adds a reference ("@see") to the current information.
   */
  public boolean addReference(String reference) {
    if (currentInfo.documentReference(reference)) {
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records that the {@link JSDocInfo} being built should have its
   * {@link JSDocInfo#isConsistentIdGenerator()} flag set to
   * {@code true}.
   *
   * @return {@code true} if the consistentIdGenerator flag was recorded and
   *     {@code false} if it was already recorded
   */
  public boolean recordConsistentIdGenerator() {
    if (!currentInfo.isConsistentIdGenerator()) {
      currentInfo.setConsistentIdGenerator(true);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link
   * JSDocInfo#isStableIdGenerator()} flag set to {@code true}.
   *
   * @return {@code true} if the stableIdGenerator flag was recorded and {@code false} if it was
   *     already recorded.
   */
  public boolean recordStableIdGenerator() {
    if (!currentInfo.isStableIdGenerator()) {
      currentInfo.setStableIdGenerator(true);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link
   * JSDocInfo#isStableIdGenerator()} flag set to {@code true}.
   *
   * @return {@code true} if the stableIdGenerator flag was recorded and {@code false} if it was
   *     already recorded.
   */
  public boolean recordMappedIdGenerator() {
    if (!currentInfo.isMappedIdGenerator()) {
      currentInfo.setMappedIdGenerator(true);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records the version.
   */
  public boolean recordVersion(String version) {
    if (currentInfo.documentVersion(version)) {
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records the deprecation reason.
   */
  public boolean recordDeprecationReason(String reason) {
    if (currentInfo.setDeprecationReason(reason)) {
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Returns whether a deprecation reason has been recorded.
   */
  public boolean isDeprecationReasonRecorded() {
    return currentInfo.getDeprecationReason() != null;
  }

  /**
   * Records the list of suppressed warnings.
   */
  public boolean recordSuppressions(Set<String> suppressions) {
    if (currentInfo.setSuppressions(suppressions)) {
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  public void addSuppression(String suppression) {
    currentInfo.addSuppression(suppression);
    populated = true;
  }

  /**
   * Records the list of modifies warnings.
   */
  public boolean recordModifies(Set<String> modifies) {
    if (!hasAnySingletonSideEffectTags()
        && currentInfo.setModifies(modifies)) {
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records a type.
   *
   * @return {@code true} if the type was recorded and {@code false} if
   *     it is invalid or was already defined
   */
  public boolean recordType(JSTypeExpression type) {
    if (type != null && !hasAnyTypeRelatedTags()) {
      currentInfo.setType(type);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  public void recordInlineType() {
    currentInfo.setInlineType();
  }

  /**
   * Records that the {@link JSDocInfo} being built should be populated
   * with a {@code typedef}'d type.
   */
  public boolean recordTypedef(JSTypeExpression type) {
    if (type != null && !hasAnyTypeRelatedTags() && currentInfo.declareTypedefType(type)) {
      populated = true;
      return true;
    }
    return false;
  }

  /**
   * Records that the {@link JSDocInfo} being built should have its
   * {@link JSDocInfo#isIdGenerator()} flag set to
   * {@code true}.
   *
   * @return {@code true} if the idGenerator flag was recorded and {@code false}
   *     if it was already recorded
   */
  public boolean recordIdGenerator() {
    if (!currentInfo.isIdGenerator()) {
      currentInfo.setIdGenerator(true);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records a return type.
   *
   * @return {@code true} if the return type was recorded and {@code false} if
   *     it is invalid or was already defined
   */
  public boolean recordReturnType(JSTypeExpression jsType) {
    if (jsType != null && currentInfo.getReturnType() == null
        && !hasAnySingletonTypeTags()) {
      currentInfo.setReturnType(jsType);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records a return description
   *
   * @return {@code true} if the return description was recorded and
   *     {@code false} if it is invalid or was already defined
   */
  public boolean recordReturnDescription(String description) {
    if (currentInfo.documentReturn(description)) {
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records the type of a define.
   *
   * 'Define' values are special constants that may be manipulated by
   * the compiler. They are designed to mimic the #define command in
   * the C preprocessor.
   */
  public boolean recordDefineType(JSTypeExpression type) {
    if (type != null &&
        !currentInfo.isConstant() &&
        !currentInfo.isDefine() &&
        recordType(type)) {
      currentInfo.setDefine(true);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records a parameter type to an enum.
   *
   * @return {@code true} if the enum's parameter type was recorded and
   *     {@code false} if it was invalid or already defined
   */
  public boolean recordEnumParameterType(JSTypeExpression type) {
    if (type != null && !hasAnyTypeRelatedTags()) {
      currentInfo.setEnumParameterType(type);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  // TODO(tbreisacher): Disallow nullable types here. If someone writes
  // "@this {Foo}" in their JS we automatically treat it as though they'd written
  // "@this {!Foo}". But, if the type node is created in the compiler
  // (e.g. in the WizPass) we should explicitly add the '!'
  /**
   * Records a type for {@code @this} annotation.
   *
   * @return {@code true} if the type was recorded and
   *     {@code false} if it is invalid or if it collided with {@code @enum} or
   *     {@code @type} annotations
   */
  public boolean recordThisType(JSTypeExpression type) {
    if (type != null && !hasAnySingletonTypeTags() &&
        !currentInfo.hasThisType()) {
      currentInfo.setThisType(type);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records a base type.
   *
   * @return {@code true} if the base type was recorded and {@code false}
   *     if it was already defined
   */
  public boolean recordBaseType(JSTypeExpression jsType) {
    if (jsType != null && !hasAnySingletonTypeTags() &&
        !currentInfo.hasBaseType()) {
      currentInfo.setBaseType(jsType);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Changes a base type, even if one has already been set on currentInfo.
   *
   * @return {@code true} if the base type was changed successfully.
   */
  public boolean changeBaseType(JSTypeExpression jsType) {
    if (jsType != null && !hasAnySingletonTypeTags()) {
      currentInfo.setBaseType(jsType);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records that the {@link JSDocInfo} being built should have its
   * {@link JSDocInfo#isConstant()} flag set to {@code true}.
   *
   * @return {@code true} if the constancy was recorded and {@code false}
   *     if it was already defined
   */
  public boolean recordConstancy() {
    if (!currentInfo.isConstant()) {
      currentInfo.setConstant(true);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records a description giving context for translation (i18n).
   *
   * @return {@code true} if the description was recorded and {@code false}
   *     if the description was invalid or was already defined
   */
  public boolean recordDescription(String description) {
    if (description != null && currentInfo.getDescription() == null) {
      currentInfo.setDescription(description);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records a meaning giving context for translation (i18n). Different
   * meanings will result in different translations.
   *
   * @return {@code true} If the meaning was successfully updated.
   */
  public boolean recordMeaning(String meaning) {
    if (meaning != null && currentInfo.getMeaning() == null) {
      currentInfo.setMeaning(meaning);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records a fileoverview description.
   *
   * @return {@code true} if the description was recorded and {@code false}
   *     if the description was invalid or was already defined.
   */
  public boolean recordFileOverview(String description) {
    if (currentInfo.documentFileOverview(description)) {
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  public boolean recordLicense(String license) {
    currentInfo.setLicense(license);
    populated = true;
    return true;
  }

  public boolean addLicense(String license) {
    String txt = currentInfo.getLicense();
    if (txt == null) {
      txt = "";
    }
    currentInfo.setLicense(txt + license);
    populated = true;
    return true;
  }

  /**
   * Records that the {@link JSDocInfo} being built should have its
   * {@link JSDocInfo#isHidden()} flag set to {@code true}.
   *
   * @return {@code true} if the hiddenness was recorded and {@code false}
   *     if it was already defined
   */
  public boolean recordHiddenness() {
    if (!currentInfo.isHidden()) {
      currentInfo.setHidden(true);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records that the {@link JSDocInfo} being built should have its
   * {@link JSDocInfo#isNoCompile()} flag set to {@code true}.
   *
   * @return {@code true} if the no compile flag was recorded and {@code false}
   *     if it was already recorded
   */
  public boolean recordNoCompile() {
    if (!currentInfo.isNoCompile()) {
      currentInfo.setNoCompile(true);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records that the {@link JSDocInfo} being built should have its
   * {@link JSDocInfo#isNoCollapse()} flag set to {@code true}.
   *
   * @return {@code true} if the no collapse flag was recorded and {@code false}
   *     if it was already recorded
   */
  public boolean recordNoCollapse() {
    if (!currentInfo.isNoCollapse()) {
      currentInfo.setNoCollapse(true);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records that the {@link JSDocInfo} being built should have its
   * {@link JSDocInfo#isConstructor()} flag set to {@code true}.
   *
   * @return {@code true} if the constructor was recorded and {@code false}
   *     if it was already defined or it was incompatible with the existing
   *     flags
   */
  public boolean recordConstructor() {
    if (!hasAnySingletonTypeTags() && !currentInfo.isConstructorOrInterface()) {
      currentInfo.setConstructor(true);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records that the {@link JSDocInfo} being built should have its
   * {@link JSDocInfo#usesImplicitMatch()} flag set to {@code true}.
   *
   * @return {@code true} if the {@code @record} tag was recorded and {@code false}
   *     if it was already defined or it was incompatible with the existing
   *     flags
   */
  public boolean recordImplicitMatch() {
    if (!hasAnySingletonTypeTags() &&
        !currentInfo.isInterface() &&
        !currentInfo.isConstructor()) {
      currentInfo.setInterface(true);
      currentInfo.setImplicitMatch(true);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Whether the {@link JSDocInfo} being built will have its
   * {@link JSDocInfo#isConstructor()} flag set to {@code true}.
   */
  public boolean isConstructorRecorded() {
    return currentInfo.isConstructor();
  }

  /**
   * Records that the {@link JSDocInfo} being built should have its
   * {@link JSDocInfo#makesUnrestricted()} flag set to {@code true}.
   *
   * @return {@code true} if annotation was recorded and {@code false}
   * if it was already defined or it was incompatible with the existing flags
   */
  public boolean recordUnrestricted() {
    if (hasAnySingletonTypeTags() || currentInfo.isInterface() ||
        currentInfo.makesDicts() || currentInfo.makesStructs() ||
        currentInfo.makesUnrestricted()) {
      return false;
    }
    currentInfo.setUnrestricted();
    populated = true;
    return true;
  }

  public boolean isUnrestrictedRecorded() {
    return currentInfo.makesUnrestricted();
  }

  /**
   * Records that the {@link JSDocInfo} being built should have its
   * {@link JSDocInfo#makesStructs()} flag set to {@code true}.
   *
   * @return {@code true} if the struct was recorded and {@code false}
   * if it was already defined or it was incompatible with the existing flags
   */
  public boolean recordStruct() {
    if (hasAnySingletonTypeTags()
        || currentInfo.makesDicts() || currentInfo.makesStructs()
        || currentInfo.makesUnrestricted()) {
      return false;
    }
    currentInfo.setStruct();
    populated = true;
    return true;
  }

  public boolean isStructRecorded() {
    return currentInfo.makesStructs();
  }

  /**
   * Records that the {@link JSDocInfo} being built should have its
   * {@link JSDocInfo#makesDicts()} flag set to {@code true}.
   *
   * @return {@code true} if the dict was recorded and {@code false}
   * if it was already defined or it was incompatible with the existing flags
   */
  public boolean recordDict() {
    if (hasAnySingletonTypeTags()
        || currentInfo.makesDicts() || currentInfo.makesStructs()
        || currentInfo.makesUnrestricted()) {
      return false;
    }
    currentInfo.setDict();
    populated = true;
    return true;
  }

  public boolean isDictRecorded() {
    return currentInfo.makesDicts();
  }

  /**
   * Records that the {@link JSDocInfo} being built should have its
   * {@link JSDocInfo#shouldPreserveTry()} flag set to {@code true}.
   */
  public boolean recordPreserveTry() {
    if (!currentInfo.shouldPreserveTry()) {
      currentInfo.setShouldPreserveTry(true);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records that the {@link JSDocInfo} being built should have its
   * {@link JSDocInfo#isOverride()} flag set to {@code true}.
   */
  public boolean recordOverride() {
    if (!currentInfo.isOverride()) {
      currentInfo.setOverride(true);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records that the {@link JSDocInfo} being built should have its
   * {@link JSDocInfo#isNoAlias()} flag set to {@code true}.
   */
  public boolean recordNoAlias() {
    if (!currentInfo.isNoAlias()) {
      currentInfo.setNoAlias(true);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records that the {@link JSDocInfo} being built should have its
   * {@link JSDocInfo#isDeprecated()} flag set to {@code true}.
   */
  public boolean recordDeprecated() {
    if (!currentInfo.isDeprecated()) {
      currentInfo.setDeprecated(true);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records that the {@link JSDocInfo} being built should have its
   * {@link JSDocInfo#isInterface()} flag set to {@code true}.
   *
   * @return {@code true} if the flag was recorded and {@code false}
   * if it was already defined or it was incompatible with the existing flags
   */
  public boolean recordInterface() {
    if (hasAnySingletonTypeTags() ||
        currentInfo.isConstructor() || currentInfo.isInterface()) {
      return false;
    }
    currentInfo.setInterface(true);
    populated = true;
    return true;
  }

  /**
   * Records that the {@link JSDocInfo} being built should have its
   * {@link JSDocInfo#isExport()} flag set to {@code true}.
   */
  public boolean recordExport() {
    if (!currentInfo.isExport()) {
      currentInfo.setExport(true);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records that the {@link JSDocInfo} being built should have its
   * {@link JSDocInfo#isExpose()} flag set to {@code true}.
   */
  public boolean recordExpose() {
    if (!currentInfo.isExpose()) {
      currentInfo.setExpose(true);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records that the {@link JSDocInfo} being built should have its
   * {@link JSDocInfo#isImplicitCast()} flag set to {@code true}.
   */
  public boolean recordImplicitCast() {
    if (!currentInfo.isImplicitCast()) {
      currentInfo.setImplicitCast(true);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records that the {@link JSDocInfo} being built should have its
   * {@link JSDocInfo#isNoSideEffects()} flag set to {@code true}.
   */
  public boolean recordNoSideEffects() {
    if (!hasAnySingletonSideEffectTags()
        && !currentInfo.isNoSideEffects()) {
      currentInfo.setNoSideEffects(true);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records that the {@link JSDocInfo} being built should have its
   * {@link JSDocInfo#isExterns()} flag set to {@code true}.
   */
  public boolean recordExterns() {
    if (!currentInfo.isExterns()) {
      currentInfo.setExterns(true);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Whether the {@link JSDocInfo} being built will have its
   * {@link JSDocInfo#isInterface()} flag set to {@code true}.
   */
  public boolean isInterfaceRecorded() {
    return currentInfo.isInterface();
  }

  /**
   * @return Whether a parameter of the given name has already been recorded.
   */
  public boolean hasParameter(String name) {
    return currentInfo.hasParameter(name);
  }

  /**
   * Records an implemented interface.
   */
  public boolean recordImplementedInterface(JSTypeExpression interfaceName) {
    if (currentInfo.addImplementedInterface(interfaceName)) {
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records an extended interface type.
   */
  public boolean recordExtendedInterface(JSTypeExpression interfaceType) {
    if (currentInfo.addExtendedInterface(interfaceType)) {
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Records that we're lending to another name.
   */
  public boolean recordLends(String name) {
    if (!hasAnyTypeRelatedTags()) {
      currentInfo.setLendsName(name);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Returns whether current JSDoc is annotated with {@code @ngInject}.
   */
  public boolean isNgInjectRecorded() {
    return currentInfo.isNgInject();
  }

  /**
   * Records that we'd like to add {@code $inject} property inferred from
   * parameters.
   */
  public boolean recordNgInject(boolean ngInject) {
    if (!isNgInjectRecorded()) {
      currentInfo.setNgInject(ngInject);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Returns whether current JSDoc is annotated with {@code @jaggerInject}.
   */
  public boolean isJaggerInjectRecorded() {
    return currentInfo.isJaggerInject();
  }

  /**
   * Records annotation with {@code @jaggerInject}.
   */
  public boolean recordJaggerInject(boolean inject) {
    if (!isJaggerInjectRecorded()) {
      currentInfo.setJaggerInject(inject);
      populated = true;
      return true;
    }

    return false;
  }

  /**
   * Returns whether current JSDoc is annotated with {@code @jaggerModule}.
   */
  public boolean isJaggerModuleRecorded() {
    return currentInfo.isJaggerModule();
  }

  /**
   * Records annotation with {@code @jaggerModule}.
   */
  public boolean recordJaggerModule(boolean jaggerModule) {
    if (!isJaggerModuleRecorded()) {
      currentInfo.setJaggerModule(jaggerModule);
      populated = true;
      return true;
    }

    return false;
  }

  /**
   * Returns whether current JSDoc is annotated with {@code @jaggerProvide}.
   */
  public boolean isJaggerProvideRecorded() {
    return currentInfo.isJaggerProvide();
  }

  /**
   * Records annotation with {@code @jaggerProvide}.
   */
  public boolean recordJaggerProvide(boolean jaggerProvide) {
    if (!isJaggerProvideRecorded()) {
      currentInfo.setJaggerProvide(jaggerProvide);
      populated = true;
      return true;
    }

    return false;
  }

  /**
   * Returns whether current JSDoc is annotated with {@code @jaggerProvide}.
   */
  public boolean isJaggerProvidePromiseRecorded() {
    return currentInfo.isJaggerProvidePromise();
  }

  /**
   * Records annotation with {@code @jaggerProvide}.
   */
  public boolean recordJaggerProvidePromise(boolean jaggerPromise) {
    if (!isJaggerProvidePromiseRecorded()) {
      currentInfo.setJaggerProvidePromise(jaggerPromise);
      populated = true;
      return true;
    }

    return false;
  }

  /**
   * Returns whether current JSDoc is annotated with {@code @wizaction}.
   */
  public boolean isWizactionRecorded() {
    return currentInfo.isWizaction();
  }

  /**
   * Records that this method is to be exposed as a wizaction.
   */
  public boolean recordWizaction() {
    if (!isWizactionRecorded()) {
      currentInfo.setWizaction(true);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Returns whether current JSDoc is annotated with {@code @polymerBehavior}.
   */
  public boolean isPolymerBehaviorRecorded() {
    return currentInfo.isPolymerBehavior();
  }

  /**
   * Records that this method is to be exposed as a polymerBehavior.
   */
  public boolean recordPolymerBehavior() {
    if (!isPolymerBehaviorRecorded()) {
      currentInfo.setPolymerBehavior(true);
      populated = true;
      return true;
    } else {
      return false;
    }
  }

  public void mergePropertyBitfieldFrom(JSDocInfo other) {
    currentInfo.mergePropertyBitfieldFrom(other);
  }

  /**
   * Records a parameter that gets disposed.
   *
   * @return {@code true} if all the parameters was recorded and
   *     {@code false} if a parameter with the same name was already defined
   */
  public boolean recordDisposesParameter(List<String> parameterNames) {
    for (String parameterName : parameterNames) {
      if ((currentInfo.hasParameter(parameterName) ||
          parameterName.equals("*")) &&
          currentInfo.setDisposedParameter(parameterName)) {
        populated = true;
      } else {
        return false;
      }
    }
    return true;
  }

  /**
   * Whether the current doc info has other type tags, like
   * {@code @param} or {@code @return} or {@code @type} or etc.
   */
  private boolean hasAnyTypeRelatedTags() {
    return currentInfo.isConstructor() ||
        currentInfo.isInterface() ||
        currentInfo.getParameterCount() > 0 ||
        currentInfo.hasReturnType() ||
        currentInfo.hasBaseType() ||
        currentInfo.getExtendedInterfacesCount() > 0 ||
        currentInfo.getLendsName() != null ||
        currentInfo.hasThisType() ||
        hasAnySingletonTypeTags();
  }

  /**
   * Whether the current doc info has any of the singleton type
   * tags that may not appear with other type tags, like
   * {@code @type} or {@code @typedef}.
   */
  private boolean hasAnySingletonTypeTags() {
    return currentInfo.hasType() ||
        currentInfo.hasTypedefType() ||
        currentInfo.hasEnumParameterType();
  }

  /**
   * Whether the current doc info has any of the singleton type
   * tags that may not appear with other type tags, like
   * {@code @type} or {@code @typedef}.
   */
  private boolean hasAnySingletonSideEffectTags() {
    return currentInfo.isNoSideEffects() ||
        currentInfo.hasModifies();
  }
}
