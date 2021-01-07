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

import com.google.javascript.rhino.JSDocInfo.Visibility;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A builder for {@link JSDocInfo} objects. This builder abstracts the construction process of
 * {@link JSDocInfo} objects whilst minimizing the number of instances of {@link JSDocInfo} objects.
 * It provides early incompatibility detection among properties stored on the {@code JSDocInfo}
 * object being created.
 *
 * <p>This class is superseded by JSDocInfo.Builder and will soon be deleted.
 *
 */
public abstract class JSDocInfoBuilder {

  public static JSDocInfo.Builder copyFrom(JSDocInfo info) {
    return info.toBuilder();
  }

  public static JSDocInfo.Builder maybeCopyFrom(@Nullable JSDocInfo info) {
    return info != null ? info.toBuilder() : JSDocInfo.builder().parseDocumentation();
  }

  /**
   * Returns a JSDocInfo.Builder that contains a copy of the given JSDocInfo in which only the
   * {@code @type} field of the JSDocInfo is replaced with the given typeExpression. This is done to
   * prevent generating code in the client module which references local variables from another
   * module.
   */
  public static JSDocInfo.Builder maybeCopyFromWithNewType(
      JSDocInfo info, JSTypeExpression typeExpression) {
    if (info == null) {
      return JSDocInfo.builder().parseDocumentation().setType(typeExpression);
    }
    return info.toBuilder().setType(typeExpression);
  }

  public static JSDocInfo.Builder copyFromWithNewType(
      JSDocInfo info, JSTypeExpression typeExpression) {
    return info.toBuilder().setType(typeExpression);
  }

  /**
   * Returns a JSDocInfo.Builder that contains a JSDoc in which all module local types (which may be
   * inside {@code @param}, {@code @type} or {@code @returns} are replaced with unknown. This is
   * done to prevent generating code in the client module which references local variables from
   * another module.
   */
  public static JSDocInfo.Builder maybeCopyFromAndReplaceNames(
      JSDocInfo info, Set<String> moduleLocalNamesToReplace) {
    return info != null
        ? copyFromAndReplaceNames(info, moduleLocalNamesToReplace)
        : JSDocInfo.builder().parseDocumentation();
  }

  private static JSDocInfo.Builder copyFromAndReplaceNames(JSDocInfo info, Set<String> oldNames) {
    return info.cloneAndReplaceTypeNames(oldNames).toBuilder(); // TODO - populated
  }

  // package-private constructor
  JSDocInfoBuilder() {}

  /**
   * Configures the builder to parse documentation. This should be called immediately after
   * instantiating the builder if documentation should be included, since it enables various
   * operations to do work that would otherwise be no-ops.
   */
  public abstract JSDocInfoBuilder parseDocumentation();

  /**
   * Sets the original JSDoc comment string. This is a no-op if the builder isn't configured to
   * record documentation.
   */
  public abstract void recordOriginalCommentString(String sourceComment);

  /** Sets the position of original JSDoc comment. */
  public abstract void recordOriginalCommentPosition(int position);

  public abstract boolean shouldParseDocumentation();

  /**
   * Returns whether this builder is populated with information that can be used to {@link #build} a
   * {@link JSDocInfo} object that has a fileoverview tag.
   */
  public abstract boolean isPopulatedWithFileOverview();

  /** Returns whether this builder recorded a description. */
  public abstract boolean isDescriptionRecorded();

  /**
   * Builds a {@link JSDocInfo} object based on the populated information and returns it.
   *
   * @return a {@link JSDocInfo} object populated with the values given to this builder. If no value
   *     was populated, this method simply returns {@code null}
   */
  public abstract JSDocInfo build();

  /**
   * Builds a {@link JSDocInfo} object based on the populated information and returns it.
   *
   * @param always Return an default JSDoc object.
   * @return a {@link JSDocInfo} object populated with the values given to this builder. If no value
   *     was populated and {@code always} is false, returns {@code null}. If {@code always} is true,
   *     returns a default JSDocInfo.
   */
  public abstract JSDocInfo build(boolean always);

  /**
   * Builds a {@link JSDocInfo} object based on the populated information and returns it. Once this
   * method is called, the builder can be reused to build another {@link JSDocInfo} object.
   *
   * @return a {@link JSDocInfo} object populated with the values given to this builder. If no value
   *     was populated, this method simply returns {@code null}
   */
  public abstract JSDocInfo buildAndReset();

  /**
   * Adds a marker to the current JSDocInfo and populates the marker with the annotation
   * information.
   */
  public abstract void markAnnotation(String annotation, int lineno, int charno);

  /** Adds a textual block to the current marker. */
  public abstract void markText(
      String text, int startLineno, int startCharno, int endLineno, int endCharno);

  /** Adds a type declaration to the current marker. */
  public abstract void markTypeNode(
      Node typeNode, int lineno, int startCharno, int endLineno, int endCharno, boolean hasLC);

  /** Adds a name declaration to the current marker. */
  public abstract void markName(String name, Node templateNode, int lineno, int charno);

  /**
   * Records a block-level description.
   *
   * @return {@code true} if the description was recorded.
   */
  public abstract boolean recordBlockDescription(String description);

  /**
   * Records a visibility.
   *
   * @return {@code true} if the visibility was recorded and {@code false} if it was already defined
   */
  public abstract boolean recordVisibility(Visibility visibility);

  public abstract void overwriteVisibility(Visibility visibility);

  /**
   * Records a typed parameter.
   *
   * @return {@code true} if the typed parameter was recorded and {@code false} if a parameter with
   *     the same name was already defined
   */
  public abstract boolean recordParameter(String parameterName, JSTypeExpression type);

  /**
   * Records a parameter's description.
   *
   * @return {@code true} if the parameter's description was recorded and {@code false} if a
   *     parameter with the same name was already defined
   */
  public abstract boolean recordParameterDescription(String parameterName, String description);

  /**
   * Records a template type name.
   *
   * @return {@code true} if the template type name was recorded and {@code false} if the input
   *     template type name was already defined.
   */
  public abstract boolean recordTemplateTypeName(String name);

  public abstract boolean recordTemplateTypeName(String name, JSTypeExpression bound);

  /** Records a type transformation expression together with its template type name. */
  public abstract boolean recordTypeTransformation(String name, Node expr);

  /** Records a thrown type. */
  public abstract boolean recordThrowType(JSTypeExpression type);

  /**
   * Records a throw type's description.
   *
   * @return {@code true} if the type's description was recorded and {@code false} if a description
   *     with the same type was already defined
   */
  public abstract boolean recordThrowDescription(JSTypeExpression type, String description);

  /** Adds an author to the current information. */
  public abstract boolean addAuthor(String author);

  /** Adds a reference ("@see") to the current information. */
  public abstract boolean addReference(String reference);

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link
   * JSDocInfo#isConsistentIdGenerator()} flag set to {@code true}.
   *
   * @return {@code true} if the consistentIdGenerator flag was recorded and {@code false} if it was
   *     already recorded
   */
  public abstract boolean recordConsistentIdGenerator();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link
   * JSDocInfo#isStableIdGenerator()} flag set to {@code true}.
   *
   * @return {@code true} if the stableIdGenerator flag was recorded and {@code false} if it was
   *     already recorded.
   */
  public abstract boolean recordStableIdGenerator();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link
   * JSDocInfo#isXidGenerator()} flag set to {@code true}.
   *
   * @return {@code true} if the isXidGenerator flag was recorded and {@code false} if it was
   *     already recorded.
   */
  public abstract boolean recordXidGenerator();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link
   * JSDocInfo#isStableIdGenerator()} flag set to {@code true}.
   *
   * @return {@code true} if the stableIdGenerator flag was recorded and {@code false} if it was
   *     already recorded.
   */
  public abstract boolean recordMappedIdGenerator();

  /** Records the version. */
  public abstract boolean recordVersion(String version);

  /** Records the deprecation reason. */
  public abstract boolean recordDeprecationReason(String reason);

  /** Returns whether a deprecation reason has been recorded. */
  public abstract boolean isDeprecationReasonRecorded();

  /**
   * Records the list of suppressed warnings, possibly adding to the set of already configured
   * warnings.
   */
  public abstract void recordSuppressions(Set<String> suppressions);

  public abstract void addSuppression(String suppression);

  /** Records the list of modifies warnings. */
  public abstract boolean recordModifies(Set<String> modifies);

  /**
   * Records a type.
   *
   * @return {@code true} if the type was recorded and {@code false} if it is invalid or was already
   *     defined
   */
  public abstract boolean recordType(JSTypeExpression type);

  public abstract void recordInlineType();

  /**
   * Records that the {@link JSDocInfo} being built should be populated with a {@code typedef}'d
   * type.
   */
  public abstract boolean recordTypedef(JSTypeExpression type);

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link
   * JSDocInfo#isIdGenerator()} flag set to {@code true}.
   *
   * @return {@code true} if the idGenerator flag was recorded and {@code false} if it was already
   *     recorded
   */
  public abstract boolean recordIdGenerator();

  /**
   * Records a return type.
   *
   * @return {@code true} if the return type was recorded and {@code false} if it is invalid or was
   *     already defined
   */
  public abstract boolean recordReturnType(JSTypeExpression jsType);

  /**
   * Records a return description
   *
   * @return {@code true} if the return description was recorded and {@code false} if it is invalid
   *     or was already defined
   */
  public abstract boolean recordReturnDescription(String description);

  /**
   * Records the type of a define.
   *
   * <p>'Define' values are special constants that may be manipulated by the compiler. They are
   * designed to mimic the #define command in the C preprocessor.
   */
  public abstract boolean recordDefineType(JSTypeExpression type);

  /**
   * Records a parameter type to an enum.
   *
   * @return {@code true} if the enum's parameter type was recorded and {@code false} if it was
   *     invalid or already defined
   */
  public abstract boolean recordEnumParameterType(JSTypeExpression type);

  /**
   * Records a type for {@code @this} annotation.
   *
   * @return {@code true} if the type was recorded and {@code false} if it is invalid or if it
   *     collided with {@code @enum} or {@code @type} annotations
   */
  public abstract boolean recordThisType(JSTypeExpression type);

  /**
   * Records a base type.
   *
   * @return {@code true} if the base type was recorded and {@code false} if it was already defined
   */
  public abstract boolean recordBaseType(JSTypeExpression jsType);

  /**
   * Changes a base type, even if one has already been set on currentInfo.
   *
   * @return {@code true} if the base type was changed successfully.
   */
  public abstract boolean changeBaseType(JSTypeExpression jsType);

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isConstant()}
   * flag set to {@code true}.
   *
   * @return {@code true} if the constancy was recorded and {@code false} if it was already defined
   */
  public abstract boolean recordConstancy();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isConstant()}
   * flag set to {@code false}.
   *
   * @return {@code true} if the mutability was recorded and {@code false} if it was already defined
   */
  public abstract boolean recordMutable();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isFinal()} flag
   * set to {@code true}.
   *
   * @return {@code true} if the finality was recorded and {@code false} if it was already defined
   */
  public abstract boolean recordFinality();

  /**
   * Records a description giving context for translation (i18n).
   *
   * @return {@code true} if the description was recorded and {@code false} if the description was
   *     invalid or was already defined
   */
  public abstract boolean recordDescription(String description);

  /**
   * Records a meaning giving context for translation (i18n). Different meanings will result in
   * different translations.
   *
   * @return {@code true} If the meaning was successfully updated.
   */
  public abstract boolean recordMeaning(String meaning);

  /**
   * Records an ID for an alternate message to be used if this message is not yet translated.
   *
   * @return {@code true} If the alternate message ID was successfully updated.
   */
  public abstract boolean recordAlternateMessageId(String alternateMessageId);

  /**
   * Records an identifier for a Closure Primitive. function.
   *
   * @return {@code true} If the id was successfully updated.
   */
  public abstract boolean recordClosurePrimitiveId(String closurePrimitiveId);

  /**
   * Records a fileoverview description.
   *
   * @return {@code true} if the description was recorded and {@code false} if the description was
   *     invalid or was already defined.
   */
  public abstract boolean recordFileOverview(String description);

  public abstract boolean recordLicense(String license);

  public abstract boolean addLicense(String license);

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isHidden()}
   * flag set to {@code true}.
   *
   * @return {@code true} if the hiddenness was recorded and {@code false} if it was already defined
   */
  public abstract boolean recordHiddenness();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isNoCompile()}
   * flag set to {@code true}.
   *
   * @return {@code true} if the no compile flag was recorded and {@code false} if it was already
   *     recorded
   */
  public abstract boolean recordNoCompile();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isNoCollapse()}
   * flag set to {@code true}.
   *
   * @return {@code true} if the no collapse flag was recorded and {@code false} if it was already
   *     recorded
   */
  public abstract boolean recordNoCollapse();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isNoInline()}
   * flag set to {@code true}.
   *
   * @return {@code true} if the no inline flag was recorded and {@code false} if it was already
   *     recorded
   */
  public abstract boolean recordNoInline();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link
   * JSDocInfo#isConstructor()} flag set to {@code true}.
   *
   * @return {@code true} if the constructor was recorded and {@code false} if it was already
   *     defined or it was incompatible with the existing flags
   */
  public abstract boolean recordConstructor();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link
   * JSDocInfo#usesImplicitMatch()} flag set to {@code true}.
   *
   * @return {@code true} if the {@code @record} tag was recorded and {@code false} if it was
   *     already defined or it was incompatible with the existing flags
   */
  public abstract boolean recordImplicitMatch();

  /**
   * Whether the {@link JSDocInfo} being built will have its {@link JSDocInfo#isConstructor()} flag
   * set to {@code true}.
   */
  public abstract boolean isConstructorRecorded();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link
   * JSDocInfo#makesUnrestricted()} flag set to {@code true}.
   *
   * @return {@code true} if annotation was recorded and {@code false} if it was already defined or
   *     it was incompatible with the existing flags
   */
  public abstract boolean recordUnrestricted();

  public abstract boolean isUnrestrictedRecorded();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isAbstract()}
   * flag set to {@code true}.
   *
   * @return {@code true} if the flag was recorded and {@code false} if it was already defined or it
   *     was incompatible with the existing flags
   */
  public abstract boolean recordAbstract();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#makesStructs()}
   * flag set to {@code true}.
   *
   * @return {@code true} if the struct was recorded and {@code false} if it was already defined or
   *     it was incompatible with the existing flags
   */
  public abstract boolean recordStruct();

  public abstract boolean isStructRecorded();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#makesDicts()}
   * flag set to {@code true}.
   *
   * @return {@code true} if the dict was recorded and {@code false} if it was already defined or it
   *     was incompatible with the existing flags
   */
  public abstract boolean recordDict();

  public abstract boolean isDictRecorded();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isOverride()}
   * flag set to {@code true}.
   */
  public abstract boolean recordOverride();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isDeprecated()}
   * flag set to {@code true}.
   */
  public abstract boolean recordDeprecated();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isInterface()}
   * flag set to {@code true}.
   *
   * @return {@code true} if the flag was recorded and {@code false} if it was already defined or it
   *     was incompatible with the existing flags
   */
  public abstract boolean recordInterface();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isExport()}
   * flag set to {@code true}.
   */
  public abstract boolean recordExport();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isExport()}
   * flag set to {@code false}.
   */
  public abstract boolean removeExport();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isExpose()}
   * flag set to {@code true}.
   */
  public abstract boolean recordExpose();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link
   * JSDocInfo#isImplicitCast()} flag set to {@code true}.
   */
  public abstract boolean recordImplicitCast();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link
   * JSDocInfo#isNoSideEffects()} flag set to {@code true}.
   */
  public abstract boolean recordNoSideEffects();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link JSDocInfo#isExterns()}
   * flag set to {@code true}.
   */
  public abstract boolean recordExterns();

  /**
   * Records that the {@link JSDocInfo} being built should have its {@link
   * JSDocInfo#isTypeSummary()} flag set to {@code true}.
   */
  public abstract boolean recordTypeSummary();

  /**
   * Whether the {@link JSDocInfo} being built will have its {@link JSDocInfo#isInterface()} flag
   * set to {@code true}.
   */
  public abstract boolean isInterfaceRecorded();

  /** @return Whether a parameter of the given name has already been recorded. */
  public abstract boolean hasParameter(String name);

  /** Records an implemented interface. */
  public abstract boolean recordImplementedInterface(JSTypeExpression interfaceName);

  /** Records an extended interface type. */
  public abstract boolean recordExtendedInterface(JSTypeExpression interfaceType);

  /** Records that we're lending to another name. */
  public abstract boolean recordLends(JSTypeExpression name);

  /** Returns whether current JSDoc is annotated with {@code @ngInject}. */
  public abstract boolean isNgInjectRecorded();

  /** Records that we'd like to add {@code $inject} property inferred from parameters. */
  public abstract boolean recordNgInject(boolean ngInject);

  /** Returns whether current JSDoc is annotated with {@code @wizaction}. */
  public abstract boolean isWizactionRecorded();

  /** Records that this method is to be exposed as a wizaction. */
  public abstract boolean recordWizaction();

  /** Returns whether current JSDoc is annotated with {@code @polymerBehavior}. */
  public abstract boolean isPolymerBehaviorRecorded();

  /** Records that this method is to be exposed as a polymerBehavior. */
  public abstract boolean recordPolymerBehavior();

  /** Returns whether current JSDoc is annotated with {@code @polymer}. */
  public abstract boolean isPolymerRecorded();

  /** Records that this method is to be exposed as a polymer element. */
  public abstract boolean recordPolymer();

  /** Returns whether current JSDoc is annotated with {@code @customElement}. */
  public abstract boolean isCustomElementRecorded();

  /** Records that this method is to be exposed as a customElement. */
  public abstract boolean recordCustomElement();

  /** Returns whether current JSDoc is annotated with {@code @mixinClass}. */
  public abstract boolean isMixinClassRecorded();

  /** Records that this method is to be exposed as a mixinClass. */
  public abstract boolean recordMixinClass();

  /** Returns whether current JSDoc is annotated with {@code @mixinFunction}. */
  public abstract boolean isMixinFunctionRecorded();

  /** Records that this method is to be exposed as a mixinFunction. */
  public abstract boolean recordMixinFunction();
}
