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

package com.google.javascript.rhino.jstype;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.base.JSCompObjects.identical;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.StaticScope;
import com.google.javascript.rhino.StaticSlot;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.nullness.Nullable;

/**
 * A {@code NamedType} is a named reference to some other type.  This provides
 * a convenient mechanism for implementing forward references to types; a
 * {@code NamedType} can be used as a placeholder until its reference is
 * resolved.  It is also useful for representing type names in JsDoc type
 * annotations, some of which may never be resolved (as they may refer to
 * types in host systems not yet supported by JSCompiler, such as the JVM.)<p>
 *
 * An important distinction: {@code NamedType} is a type name reference,
 * whereas {@link ObjectType} is a named type object, such as an Enum name.
 * The Enum itself is typically used only in a dot operator to name one of its
 * constants, or in a declaration, where its name will appear in a
 * NamedType.<p>
 *
 * A {@code NamedType} is not currently a full-fledged typedef, because it
 * cannot resolve to any JavaScript type.  It can only resolve to a named
 * {@link JSTypeRegistry} type, or to {@link FunctionType} or
 * {@link EnumType}.<p>
 *
 * If full typedefs are to be supported, then each method on each type class
 * needs to be reviewed to make sure that everything works correctly through
 * typedefs.  Alternatively, we would need to walk through the parse tree and
 * unroll each reference to a {@code NamedType} to its resolved type before
 * applying the rest of the analysis.<p>
 *
 * TODO(user): Revisit all of this logic.<p>
 *
 * The existing typing logic is hacky.  Unresolved types should get processed
 * in a more consistent way, but with the Rhino merge coming, there will be
 * much that has to be changed.<p>
 *
 */
public final class NamedType extends ProxyObjectType {
  private static final JSTypeClass TYPE_CLASS = JSTypeClass.NAMED;

  static int nominalHashCode(ObjectType type) {
    checkState(type.hasReferenceName());
    String name = checkNotNull(type.getReferenceName());
    return name.hashCode();
  }

  private final String reference;
  private final String sourceName;
  private final int lineno;
  private final int charno;
  private final ResolutionKind resolutionKind;

  private @Nullable StaticTypedScope resolutionScope;

  /** Validates the type resolution. */
  private transient Predicate<JSType> validator;

  // The following instance properties (`propertyContinuations`, `templateTypes`, and
  // `restrictByNull`) are used to indicate that some type operation should be applied to the type
  // after resolution. This is necessary because type operations are not well-defined when applied
  // to unresolved NamedTypes.
  //
  // TODO(lharker): Generalize this pattern instead of storing these arbitrary fields.

  /** Property-defining continuations. */
  private transient @Nullable List<PropertyContinuation> propertyContinuations = null;

  /**
   * Template types defined on a named, not yet resolved type, or {@code null} if none. These are
   * ignored during resolution, for backwards compatibility with existing usage. This field is not
   * used for JSCompiler's type checking; it is only needed by Clutz.
   */
  private final ImmutableList<JSType> templateTypes;

  /** Applies the "!" operator to the resolved type, which removes null and undefined */
  private final boolean restrictByNull;

  private NamedType(Builder builder) {
    super(builder.registry, builder.referencedType);
    checkNotNull(builder.referenceName);
    checkNotNull(builder.resolutionKind);
    checkNotNull(builder.templateTypes);
    if (builder.resolutionKind.equals(ResolutionKind.TYPEOF)) {
      checkState(builder.referenceName.startsWith("typeof "));
    }
    // TODO(lharker): enforce that the scope is not null

    this.restrictByNull = builder.restrictByNull;
    this.resolutionScope = builder.scope;
    this.reference = builder.referenceName;
    this.sourceName = builder.sourceName;
    this.lineno = builder.lineno;
    this.charno = builder.charno;
    this.templateTypes = builder.templateTypes;
    this.resolutionKind = builder.resolutionKind;

    registry.getResolver().resolveIfClosed(this, TYPE_CLASS);
  }

  @Override
  JSTypeClass getTypeClass() {
    return TYPE_CLASS;
  }

  /** Returns a new non-null version of this type. */
  JSType getBangType() {
    if (restrictByNull) {
      return this;
    } else if (isResolved()) {
      // Already resolved, just restrict.
      // TODO(b/146173738): just return getReferencedType().restrictByNotNullOrUndefined() after
      // fixing how conformance checks handle unresolved types.
      return this.isNoResolvedType() || this.isUnknownType()
          ? this
          : getReferencedType().restrictByNotNullOrUndefined();
    }
    return this.toBuilder().setRestrictByNull(true).build();
  }

  @Override
  public ImmutableList<JSType> getTemplateTypes() {
    return templateTypes;
  }

  @Override
  boolean defineProperty(String propertyName, JSType type,
      boolean inferred, Node propertyNode) {
    if (!isResolved()) {
      // If this is an unresolved object type, we need to save all its
      // properties and define them when it is resolved.
      if (propertyContinuations == null) {
        propertyContinuations = new ArrayList<>();
      }
      propertyContinuations.add(
          new PropertyContinuation(
              propertyName, type, inferred, propertyNode));
      return true;
    } else {
      return super.defineProperty(
          propertyName, type, inferred, propertyNode);
    }
  }

  private void finishPropertyContinuations() {
    ObjectType referencedObjType = getReferencedObjTypeInternal();
    if (referencedObjType != null
        && !referencedObjType.isUnknownType()
        && propertyContinuations != null) {
      for (PropertyContinuation c : propertyContinuations) {
        c.commit(this);
      }
    }
    propertyContinuations = null;
  }

  /** Returns the type to which this refers (which is unknown if unresolved). */
  public JSType getReferencedType() {
    return getReferencedTypeInternal();
  }

  @Override
  public String getReferenceName() {
    return reference;
  }

  @Override
  void appendTo(TypeStringBuilder sb) {
    JSType type = this.getReferencedType();
    if (!isResolved() || type.isNoResolvedType()) {
      sb.append(getReferenceName());
    } else {
      sb.append(type);
    }
  }

  @Override
  public NamedType toMaybeNamedType() {
    return this;
  }

  @Override
  public boolean isNominalType() {
    return isResolved() ? super.isNominalType() : true;
  }

  @Override
  int recursionUnsafeHashCode() {
    // Recall that equality on `NamedType` uses only the name until successful resolution, then
    // delegates to the resolved type.
    return isSuccessfullyResolved() ? super.recursionUnsafeHashCode() : nominalHashCode(this);
  }

  /**
   * Resolve the referenced type within the enclosing scope.
   */
  @Override
  JSType resolveInternal(ErrorReporter reporter) {
    ImmutableList<JSType> resolvedTypeArgs =
        JSTypeIterations.mapTypes((t) -> t.resolve(reporter), this.templateTypes);

    if (resolutionKind.equals(ResolutionKind.NONE)) {
      // In some cases (e.g. typeof(ns) when the actual type is just a literal object), a NamedType
      // is created solely for the purpose of naming an already-known type. When that happens,
      // there's nothing to look up, so just resolve the referenced type.
      return super.resolveInternal(reporter);
    }
    checkState(
        getReferencedType().isUnknownType(),
        "NamedTypes given a referenced type pre-resolution should have ResolutionKind.NONE");

    if (resolutionScope == null) {
      return this;
    }

    // TODO(user): Investigate whether it is really necessary to keep two
    // different mechanisms for resolving named types, and if so, which order
    // makes more sense.
    boolean unused = resolveTypeof(reporter) || resolveViaRegistry(reporter);

    super.resolveInternal(reporter);
    if (detectInheritanceCycle()) {
      handleTypeCycle(reporter);
    }
    finishPropertyContinuations();

    JSType result = getReferencedType();
    if (isSuccessfullyResolved()) {
      this.resolutionScope = null;

      ObjectType resultAsObject = result.toMaybeObjectType();

      if (resultAsObject == null) {
        // For non-object types there is no need to handle template parameters or
        // interface aliases, so we can just return now.
        return result;
      }

      if (resolvedTypeArgs.isEmpty() || !resultAsObject.isRawTypeOfTemplatizedType()) {
        // No template parameters need to be resolved.
        return result;
      }

      int numKeys = result.getTemplateParamCount();
      // TODO(b/287880204): report an error if there are too many type arguments
      if (numKeys < resolvedTypeArgs.size()) {
        resolvedTypeArgs = resolvedTypeArgs.subList(0, numKeys);
      }

      result = registry.createTemplatizedType(resultAsObject, resolvedTypeArgs);
      setReferencedType(result.resolve(reporter));
    }

    return result;
  }

  /**
   * Resolves a named type by looking it up in the registry.
   * @return True if we resolved successfully.
   */
  private boolean resolveViaRegistry(ErrorReporter reporter) {
    JSType type = registry.getType(resolutionScope, reference);
    if (type == null) {
      handleUnresolvedType(reporter);
      return false;
    }
    setReferencedAndResolvedType(type, reporter);
    return true;
  }

  private boolean resolveTypeof(ErrorReporter reporter) {
    if (!resolutionKind.equals(ResolutionKind.TYPEOF)) {
      return false;
    }

    // TODO(sdh): require var to be const?
    String scopeName = reference.substring("typeof ".length());
    JSType type = resolutionScope.lookupQualifiedName(QualifiedName.of(scopeName));
    if (type == null || type.isUnknownType()) {
      if (registry.isForwardDeclaredType(scopeName)) {
        // Preserve the "typeof" as a `NoResolvedType`.
        // This is depended on by Clutz so it can generate `typeof ImportedType` instead of `any`
        // when `ImportedType` is not defined in the files it can see.
        setReferencedType(new NoResolvedType(registry, getReferenceName(), getTemplateTypes()));
        if (validator != null) {
          var unused = validator.apply(getReferencedType());
        }
      } else {
        warning(reporter, "Missing type for `typeof` value. The value must be declared and const.");
        setReferencedAndResolvedType(registry.getNativeType(JSTypeNative.UNKNOWN_TYPE), reporter);
      }
    } else {
      if (type.isLiteralObject()) {
        // Create an extra layer of wrapping so that the "typeof" name is preserved for namespaces.
        // This is depended on by Clutz to prevent infinite loops in self-referential typeof types.
        JSType objlit = type;
        type =
            NamedType.builder(registry, getReferenceName())
                .setResolutionKind(ResolutionKind.NONE)
                .setReferencedType(objlit)
                .build();
      }
      setReferencedAndResolvedType(type, reporter);
    }

    return true;
  }

  private void setReferencedAndResolvedType(
      JSType type, ErrorReporter reporter) {
    if (restrictByNull) {
      type = type.restrictByNotNullOrUndefined();
    }
    if (validator != null) {
      var unused = validator.apply(type);
    }
    setReferencedType(type);
    checkEnumElementCycle(reporter);
    checkProtoCycle(reporter);
  }

  private void handleTypeCycle(ErrorReporter reporter) {
    setReferencedType(
        registry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE));
    warning(reporter, "Cycle detected in inheritance chain of type " + reference);
  }

  private void checkEnumElementCycle(ErrorReporter reporter) {
    JSType referencedType = getReferencedType();
    if (referencedType instanceof EnumElementType
        && identical(this, ((EnumElementType) referencedType).getPrimitiveType())) {
      handleTypeCycle(reporter);
    }
  }

  private void checkProtoCycle(ErrorReporter reporter) {
    JSType referencedType = getReferencedType();
    if (identical(referencedType, this)) {
      handleTypeCycle(reporter);
    }
  }

  /** Warns about this type being unresolved iff it's not a forward-declared type name */
  private void handleUnresolvedType(ErrorReporter reporter) {
    boolean isForwardDeclared = registry.isForwardDeclaredType(reference);
    if (!isForwardDeclared) {
      String msg = "Bad type annotation. Unknown type " + reference;
      // Look for a local variable that shadows a global namespace to give a clearer message.
      String root =
          reference.contains(".") ? reference.substring(0, reference.indexOf(".")) : reference;
      if (localVariableShadowsGlobalNamespace(root)) {
        msg += "\nIt's possible that a local variable called '" + root
            + "' is shadowing the intended global namespace.";
      }
      warning(reporter, msg);
    } else {
      setReferencedType(new NoResolvedType(registry, getReferenceName(), getTemplateTypes()));
      if (validator != null) {
        var unused = validator.apply(getReferencedType());
      }
    }
  }

  /**
   * Check for an obscure but very confusing error condition where a local variable shadows a
   * global namespace.
   */
  private boolean localVariableShadowsGlobalNamespace(String root) {
    StaticSlot rootVar = resolutionScope.getSlot(root);
    if (rootVar != null) {
      checkNotNull(rootVar.getScope(), rootVar);
      StaticScope parent = rootVar.getScope().getParentScope();
      if (parent != null) {
        StaticSlot globalVar = parent.getSlot(root);
        return globalVar != null;
      }
    }
    return false;
  }

  @Override
  public boolean setValidator(Predicate<JSType> validator) {
    // If the type is already resolved, we can validate it now. If
    // the type has not been resolved yet, we need to wait till its
    // resolved before we can validate it.
    if (this.isResolved()) {
      return super.setValidator(validator);
    } else {
      this.validator = validator;
      return true;
    }
  }

  void warning(ErrorReporter reporter, String message) {
    reporter.warning(message, sourceName, lineno, charno);
  }

  /** Store enough information to define a property at a later time. */
  private static final class PropertyContinuation {
    private final String propertyName;
    private final JSType type;
    private final boolean inferred;
    private final Node propertyNode;

    private PropertyContinuation(
        String propertyName,
        JSType type,
        boolean inferred,
        Node propertyNode) {
      this.propertyName = propertyName;
      this.type = type;
      this.inferred = inferred;
      this.propertyNode = propertyNode;
    }

    void commit(ObjectType target) {
      target.defineProperty(
          propertyName, type, inferred, propertyNode);
    }
  }

  @Override
  public boolean isObject() {
    if (isEnumElementType()) {
      return toMaybeEnumElementType().isObject();
    }
    return super.isObject();
  }

  @Override
  public <T> T visit(Visitor<T> visitor) {
    return visitor.caseNamedType(this);
  }

  static Builder builder(JSTypeRegistry registry, String reference) {
    return new Builder(registry, reference);
  }

  enum ResolutionKind {
    NONE,
    TYPE_NAME,
    TYPEOF
  }

  Builder toBuilder() {
    checkState(!isResolved(), "Only call toBuilder on unresolved NamedTypes");
    return new Builder(this.registry, this.reference)
        .setScope(this.resolutionScope)
        .setResolutionKind(this.resolutionKind)
        .setErrorReportingLocation(this.sourceName, this.lineno, this.charno)
        .setTemplateTypes(this.templateTypes)
        .setReferencedType(getReferencedType())
        .setRestrictByNull(this.restrictByNull);
  }

  static final class Builder {
    private final JSTypeRegistry registry;
    private ResolutionKind resolutionKind;
    private final String referenceName;
    private StaticTypedScope scope;
    private String sourceName;
    private int lineno;
    private int charno;
    private JSType referencedType;
    private boolean restrictByNull;
    private ImmutableList<JSType> templateTypes = ImmutableList.of();

    private Builder(JSTypeRegistry registry, String referenceName) {
      this.registry = registry;
      this.referenceName = referenceName;
      this.referencedType = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
    }

    Builder setScope(StaticTypedScope scope) {
      this.scope = scope;
      return this;
    }

    Builder setResolutionKind(ResolutionKind resolutionKind) {
      this.resolutionKind = resolutionKind;
      return this;
    }

    Builder setErrorReportingLocation(String sourceName, int lineno, int charno) {
      this.sourceName = sourceName;
      this.lineno = lineno;
      this.charno = charno;
      return this;
    }

    Builder setErrorReportingLocationFrom(Node source) {
      this.sourceName = source.getSourceFileName();
      this.lineno = source.getLineno();
      this.charno = source.getCharno();
      return this;
    }

    Builder setTemplateTypes(ImmutableList<JSType> templateTypes) {
      this.templateTypes = templateTypes;
      return this;
    }

    Builder setReferencedType(JSType referencedType) {
      this.referencedType = referencedType;
      return this;
    }

    private Builder setRestrictByNull(boolean restrictByNull) {
      this.restrictByNull = restrictByNull;
      return this;
    }

    NamedType build() {
      return new NamedType(this);
    }
  }
}
