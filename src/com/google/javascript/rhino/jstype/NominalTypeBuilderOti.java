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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.NominalTypeBuilder;
import com.google.javascript.rhino.TypeI;
import java.util.ArrayList;
import java.util.List;

/**
 * OTI implementation of NominalTypeBuilder
 */
public final class NominalTypeBuilderOti implements NominalTypeBuilder {

  /**
   * Factory to create NominalTypeBuilders.  This is important so that beforeFreeze
   * callbacks can be run asynchronously with the coding convention method.
   */
  public static class Factory implements AutoCloseable {
    private final List<Runnable> callbacks = new ArrayList<>();

    /** Makes a NominalTypeBuilder whose callbacks will be called on close. */
    public NominalTypeBuilder builder(FunctionType constructor, ObjectType instance) {
      return new NominalTypeBuilderOti(callbacks, constructor, instance);
    }

    @Override
    public void close() {
      for (Runnable callback : callbacks) {
        callback.run();
      }
    }
  }

  private static class ObjectBuilderImpl implements ObjectBuilder {
    final ObjectType object;
    ObjectBuilderImpl(ObjectType object) {
      this.object = checkNotNull(object);
    }

    @Override
    public void declareProperty(String property, TypeI type, Node defSite) {
      checkArgument(type instanceof JSType);
      object.defineDeclaredProperty(property, (JSType) type, defSite);
    }

    @Override
    public TypeI toTypeI() {
      return object;
    }
  }

  private final List<Runnable> callbacks;
  private final ObjectBuilderImpl constructor;
  private final ObjectBuilderImpl instance;
  private final ObjectBuilderImpl prototype;

  private NominalTypeBuilderOti(
      List<Runnable> callbacks, FunctionType constructor, ObjectType instance) {
    this.callbacks = callbacks;
    this.constructor = new ObjectBuilderImpl(constructor);
    this.instance = new ObjectBuilderImpl(instance);
    this.prototype = new ObjectBuilderImpl(constructor.getPrototypeProperty());
  }

  @Override
  public void beforeFreeze(Runnable task, NominalTypeBuilder... prerequisites) {
    callbacks.add(task);
  }

  @Override
  public ObjectBuilder constructor() {
    return constructor;
  }

  @Override
  public ObjectBuilder instance() {
    return instance;
  }

  @Override
  public ObjectBuilder prototype() {
    return prototype;
  }
}
