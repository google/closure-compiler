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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.rhino.FunctionTypeI;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.NominalTypeBuilder;
import com.google.javascript.rhino.ObjectTypeI;
import com.google.javascript.rhino.TypeI;

/** NTI implementation of NominalTypeBuilder. */
public final class NominalTypeBuilderNti implements NominalTypeBuilder {

  private final NominalType nt;

  public NominalTypeBuilderNti(NominalType nt) {
    this.nt = nt;
  }

  @Override
  public NominalTypeBuilder superClass() {
    NominalType superClass = nt.getInstantiatedSuperclass();
    return superClass != null ? new NominalTypeBuilderNti(superClass) : null;
  }

  @Override
  public FunctionTypeI constructor() {
    FunctionType ctor = nt.getConstructorFunction();
    checkState(ctor != null);
    return nt.getCommonTypes().fromFunctionType(ctor);
  }

  @Override
  public ObjectTypeI instance() {
    return nt.getInstanceAsJSType();
  }

  @Override
  public ObjectTypeI prototypeOrInstance() {
    return instance();
  }

  @Override
  public void declarePrototypeProperty(String name, TypeI type, Node defSite) {
    checkArgument(type instanceof JSType);
    if (!name.equals("constructor")) {
      // TODO(sdh): to avoid tricky type-checking issues (such as prototype methods being marked
      // as function(this:Foo.prototype) instead of function(this:Foo)), NTI doesn't add the
      // "constructor" property to the prototype directly (see comment in RawNominalType#freeze).
      // Figure out if there's a better way to work around this.
      nt.getRawNominalType().addProtoProperty(name, defSite, (JSType) type, true);
    }
  }

  @Override
  public void declareInstanceProperty(String name, TypeI type, Node defSite) {
    checkArgument(type instanceof JSType);
    // NOTE: When GlobalTypeInfoCollector adds instance and prototype properties, it also
    // adds them to its propertyDefs table. But this table is used primarily for checking
    // for valid annotations (e.g. @override), so we can safely ignore it here.
    nt.getRawNominalType().addInstanceProperty(name, defSite, (JSType) type, true);
  }

  @Override
  public void declareConstructorProperty(String name, TypeI type, Node defSite) {
    checkArgument(type instanceof JSType);
    nt.getRawNominalType().addCtorProperty(name, defSite, (JSType) type, true);
  }
}
