/*
 * Copyright 2017 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp.newtypes;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.NominalTypeBuilder;
import com.google.javascript.rhino.TypeI;

/** NTI implementation of NominalTypeBuilder. */
public final class NominalTypeBuilderNti implements NominalTypeBuilder {

  /**
   * A table of late properties, which define a "prerequisite" relationship when freezing
   * RawNominalTypes.
   */
  public static final class LateProperties {
    private final Multimap<RawNominalType, RawNominalType> prerequisites =
        MultimapBuilder.hashKeys().linkedHashSetValues().build();
    private final Multimap<RawNominalType, Runnable> runnables =
        MultimapBuilder.hashKeys().arrayListValues().build();

    /** Returns an iterable over all prerequisites that must be frozen before the given type. */
    public Iterable<RawNominalType> prerequisites(RawNominalType type) {
      return prerequisites.get(type);
    }

    /**
     * Performs all late processing that must occur after freezing type's prerequisites but before
     * freezing the type itself.
     */
    public void defineProperties(RawNominalType type) {
      for (Runnable prop : runnables.get(type)) {
        prop.run();
      }
      runnables.removeAll(type);
      prerequisites.removeAll(type);
    }

    /** Adds a property. */
    void add(RawNominalType target, RawNominalType prerequisite, Runnable runnable) {
      prerequisites.put(target, prerequisite);
      runnables.put(target, runnable);
    }
  }

  private final LateProperties props;
  private final RawNominalType raw;

  public NominalTypeBuilderNti(LateProperties props, RawNominalType raw) {
    this.props = props;
    this.raw = raw;
  }

  @Override
  public void beforeFreeze(Runnable runnable, NominalTypeBuilder... prerequisites) {
    for (NominalTypeBuilder prerequisite : prerequisites) {
      props.prerequisites.put(raw, ((NominalTypeBuilderNti) prerequisite).raw);
    }
    props.runnables.put(raw, runnable);
  }

  @Override
  public ObjectBuilder constructor() {
    return new ObjectBuilderImpl(PropertySlot.CONSTRUCTOR);
  }

  @Override
  public ObjectBuilder instance() {
    return new ObjectBuilderImpl(PropertySlot.INSTANCE);
  }

  @Override
  public ObjectBuilder prototype() {
    return new ObjectBuilderImpl(PropertySlot.PROTOTYPE);
  }

  /** Private implementation of ObjectBuilder. */
  private class ObjectBuilderImpl implements ObjectBuilder {

    final PropertySlot slot;

    ObjectBuilderImpl(PropertySlot slot) {
      this.slot = slot;
    }

    RawNominalType raw() {
      return raw;
    }

    @Override
    public void declareProperty(String property, TypeI type, Node defSite) {
      slot.addProperty(raw, property, (JSType) type, defSite);
    }

    @Override
    public TypeI toTypeI() {
      return slot.toTypeI(raw);
    }
  }

  /** The three different slots that can have properties defined on them. */
  private enum PropertySlot {
    CONSTRUCTOR {
      @Override
      void addProperty(RawNominalType target, String property, JSType type, Node defSite) {
        target.addCtorProperty(property, defSite, type, true);
      }
      @Override
      TypeI toTypeI(RawNominalType raw) {
        FunctionType ctor = raw.getConstructorFunction();
        checkState(ctor != null);
        return raw.getCommonTypes().fromFunctionType(ctor);
      }
    },
    INSTANCE {
      @Override
      void addProperty(RawNominalType target, String property, JSType type, Node defSite) {
        // NOTE: When GlobalTypeInfoCollector adds instance and prototype properties, it also
        // adds them to its propertyDefs table. But this table is used primarily for checking
        // for valid annotations (e.g. @override), so we can safely ignore it here.
        target.addInstanceProperty(property, defSite, type, true);
      }
      @Override
      TypeI toTypeI(RawNominalType raw) {
        return raw.getInstanceAsJSType();
      }
    },
    PROTOTYPE {
      @Override
      void addProperty(RawNominalType target, String property, JSType type, Node defSite) {
        if (!property.equals("constructor")) {
          // TODO(sdh): NTI doesn't do well if prototype.constructor is explicitly added.
          // (i.e. this can lead to prototype method definition nodes being marked as
          // type function(this:Foo.prototype) instead of simply function(this:Foo).
          // Figure out if there's a better way to work around this.
          target.addProtoProperty(property, defSite, type, true);
        }
      }
      @Override
      TypeI toTypeI(RawNominalType raw) {
        checkState(raw.isFrozen());
        return raw.getCtorPropDeclaredType("prototype");
      }
    };

    /** Adds a property at this slot on the target. */
    abstract void addProperty(RawNominalType target, String property, JSType type, Node defSite);
    /** Returns a TypeI if possible, or throws IllegalStateException. */
    abstract TypeI toTypeI(RawNominalType raw);
  }
}
