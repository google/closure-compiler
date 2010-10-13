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

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.javascript.rhino.ErrorReporter;

/**
 * A {@code NamedType} is a named reference to some other type.  This provides
 * a convenient mechanism for implementing forward references to types; a
 * {@code NamedType} can be used as a placeholder until its reference is
 * resolved.  It is also useful for representing type names in jsdoc type
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
class NamedType extends ProxyObjectType {
  private static final long serialVersionUID = 1L;

  private final String reference;
  private final String sourceName;
  private final int lineno;
  private final int charno;

  /**
   * Validates the type resolution.
   */
  private Predicate<JSType> validator;

  /**
   * If true, don't warn about unresolveable type names.
   *
   * NOTE(nicksantos): A lot of third-party code doesn't use our type syntax.
   * They have code like
   * {@code @return} the bus.
   * and they clearly don't mean that "the" is a type. In these cases, we're
   * forgiving and try to guess whether or not "the" is a type when it's not
   * clear.
   */
  private boolean forgiving = false;

  /**
   * Create a named type based on the reference.
   */
  NamedType(JSTypeRegistry registry, String reference,
      String sourceName, int lineno, int charno) {
    super(registry, registry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE));

    Preconditions.checkNotNull(reference);
    this.reference = reference;
    this.sourceName = sourceName;
    this.lineno = lineno;
    this.charno = charno;
  }

  @Override
  void forgiveUnknownNames() {
    forgiving = true;
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
  public String toString() {
    return reference;
  }

  @Override
  public boolean hasReferenceName() {
    return true;
  }

  @Override
  boolean isNamedType() {
    return true;
  }

  @Override
  public boolean isNominalType() {
    return true;
  }

  /**
   * Two named types are equivalent if they are the same {@code
   * ObjectType} object.  This is complicated by the fact that isEquivalent
   * is sometimes called before we have a chance to resolve the type
   * names.
   *
   * @return {@code true} iff {@code that} == {@code this} or {@code that}
   *         is a {@link NamedType} whose reference is the same as ours,
   *         or {@code that} is the type we reference.
   */
  @Override
  public boolean isEquivalentTo(JSType that) {
    if (this == that) {
      return true;
    }

    ObjectType objType = ObjectType.cast(that);
    if (objType != null) {
      return objType.isNominalType() &&
          reference.equals(objType.getReferenceName());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return reference.hashCode();
  }

  /**
   * Resolve the referenced type within the enclosing scope.
   */
  @Override
  JSType resolveInternal(ErrorReporter t, StaticScope<JSType> enclosing) {
    // TODO(user): Investigate whether it is really necessary to keep two
    // different mechanisms for resolving named types, and if so, which order
    // makes more sense. Now, resolution via registry is first in order to
    // avoid triggering the warnings built into the resolution via properties.
    boolean resolved = resolveViaRegistry(t, enclosing);
    if (detectImplicitPrototypeCycle()) {
      handleTypeCycle(t);
    }

    if (resolved) {
      super.resolveInternal(t, enclosing);
      return registry.isLastGeneration() ?
          getReferencedType() : this;
    }

    resolveViaProperties(t, enclosing);
    if (detectImplicitPrototypeCycle()) {
      handleTypeCycle(t);
    }

    super.resolveInternal(t, enclosing);
    return registry.isLastGeneration() ?
        getReferencedType() : this;
  }

  /**
   * Resolves a named type by looking it up in the registry.
   * @return True if we resolved successfully.
   */
  private boolean resolveViaRegistry(
      ErrorReporter t, StaticScope<JSType> enclosing) {
    JSType type = registry.getType(reference);
    if (type != null) {
      setReferencedAndResolvedType(type, t, enclosing);
      return true;
    }
    return false;
  }

  /**
   * Resolves a named type by looking up its first component in the scope, and
   * subsequent components as properties. The scope must have been fully
   * parsed and a symbol table constructed.
   */
  private void resolveViaProperties(ErrorReporter t,
                                    StaticScope<JSType> enclosing) {
    JSType value = lookupViaProperties(t, enclosing);
    // last component of the chain
    if ((value instanceof FunctionType) &&
        (value.isConstructor() || value.isInterface())) {
      FunctionType functionType = (FunctionType) value;
      setReferencedAndResolvedType(
          functionType.getInstanceType(), t, enclosing);
    } else if (value instanceof EnumType) {
      setReferencedAndResolvedType(
          ((EnumType) value).getElementsType(), t, enclosing);
    } else {
      // We've been running into issues where people forward-declare
      // non-named types. (This is legitimate...our dependency management
      // code doubles as our forward-declaration code.)
      //
      // So if the type does resolve to an actual value, but it's not named,
      // then don't respect the forward declaration.
      handleUnresolvedType(t, value == null || value.isUnknownType());
    }
  }

  /**
   * Resolves a type by looking up its first component in the scope, and
   * subsequent components as properties. The scope must have been fully
   * parsed and a symbol table constructed.
   * @return The type of the symbol, or null if the type could not be found.
   */
  private JSType lookupViaProperties( ErrorReporter t,
      StaticScope<JSType> enclosing) {
    String[] componentNames = reference.split("\\.", -1);
    if (componentNames[0].length() == 0) {
      return null;
    }
    StaticSlot<JSType> slot = enclosing.getSlot(componentNames[0]);
    if (slot == null) {
      return null;
    }
    // If the first component has a type of 'Unknown', then any type
    // names using it should be regarded as silently 'Unknown' rather than be
    // noisy about it.
    JSType slotType = slot.getType();
    if (slotType == null || slotType.isAllType() || slotType.isNoType()) {
      return null;
    }
    JSType value = getTypedefType(t, slot, componentNames[0]);
    if (value == null) {
      return null;
    }

    // resolving component by component
    for (int i = 1; i < componentNames.length; i++) {
      ObjectType parentClass = ObjectType.cast(value);
      if (parentClass == null) {
        return null;
      }
      if (componentNames[i].length() == 0) {
        return null;
      }
      value = parentClass.getPropertyType(componentNames[i]);
    }
    return value;
  }

  private void setReferencedAndResolvedType(JSType type, ErrorReporter t,
      StaticScope<JSType> enclosing) {
    if (validator != null) {
      validator.apply(type);
    }
    setReferencedType(type);
    checkEnumElementCycle(t);
    setResolvedTypeInternal(getReferencedType());
  }

  private void handleTypeCycle(ErrorReporter t) {
    setReferencedType(
        registry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE));
    t.warning("Cycle detected in inheritance chain of type " + reference,
        sourceName, lineno, null, charno);
    setResolvedTypeInternal(getReferencedType());
  }

  private void checkEnumElementCycle(ErrorReporter t) {
    JSType referencedType = getReferencedType();
    if (referencedType instanceof EnumElementType &&
        ((EnumElementType) referencedType).getPrimitiveType() == this) {
      handleTypeCycle(t);
    }
  }

  // Warns about this type being unresolved iff it's not a forward-declared
  // type name.
  private void handleUnresolvedType(
      ErrorReporter t, boolean ignoreForwardReferencedTypes) {
    if (registry.isLastGeneration()) {
      boolean isForwardDeclared =
          ignoreForwardReferencedTypes &&
          registry.isForwardDeclaredType(reference);
      boolean beForgiving = forgiving || isForwardDeclared;
      if (!beForgiving && registry.isLastGeneration()) {
        t.warning("Unknown type " + reference, sourceName, lineno, null,
            charno);
      } else {
        setReferencedType(
            registry.getNativeObjectType(
                JSTypeNative.CHECKED_UNKNOWN_TYPE));

        if (registry.isLastGeneration() && validator != null) {
          validator.apply(getReferencedType());
        }
      }

      setResolvedTypeInternal(getReferencedType());
    } else {
      setResolvedTypeInternal(this);
    }
  }

  JSType getTypedefType(ErrorReporter t, StaticSlot<JSType> slot, String name) {
    JSType type = slot.getType();
    if (type != null) {
      return type;
    }
    handleUnresolvedType(t, true);
    return null;
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
}
