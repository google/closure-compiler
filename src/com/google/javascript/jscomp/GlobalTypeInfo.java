/*
 * Copyright 2013 The Closure Compiler Authors.
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

package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.newtypes.JSType;
import com.google.javascript.jscomp.newtypes.JSTypeCreatorFromJSDoc;
import com.google.javascript.jscomp.newtypes.JSTypes;
import com.google.javascript.jscomp.newtypes.RawNominalType;
import com.google.javascript.jscomp.newtypes.UniqueNameGenerator;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.ObjectTypeI;
import com.google.javascript.rhino.StaticScope;
import com.google.javascript.rhino.TypeI;
import com.google.javascript.rhino.TypeIEnv;
import com.google.javascript.rhino.TypeIRegistry;
import com.google.javascript.rhino.jstype.JSTypeNative;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Contains information about all scopes; for every variable reference computes
 * whether it is local, a formal parameter, etc.; and computes information about
 * the class hierarchy.
 *
 * <p>Used by the new type inference. See go/jscompiler-new-type-checker for the
 * latest updates.
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public class GlobalTypeInfo implements TypeIRegistry {

  // We collect function scopes during CollectNamedTypes, and put them in this list out-to-in
  // (global scope first, then functions in global scope, etc).
  // At the end of GlobalTypeInfo, we rearrange them in the order in which they will be
  // processed during NewTypeInference. See GlobalTypeInfoCollector#reorderScopesForNTI.
  private List<NTIScope> scopes;
  private NTIScope globalScope;

  private final List<TypeMismatch> mismatches;
  private final List<TypeMismatch> implicitInterfaceUses;
  private final JSTypeCreatorFromJSDoc typeParser;
  private final Map<Node, String> anonFunNames = new LinkedHashMap<>();
  private final UniqueNameGenerator varNameGen;

  private final JSTypes commonTypes;
  private final Set<String> unknownTypeNames;
  // It's useful to know all properties defined anywhere in the program.
  // For a property access on ?, we can warn if the property isn't defined;
  // same for Object in compatibility mode.
  private final Set<String> allPropertyNames = new LinkedHashSet<>();
  // All property names that appear in externs. This means that with NTI,
  // we don't need to run GatherExternProperties, which uses the OTI-specific
  // Visitor interface.
  private final Set<String> externPropertyNames = new LinkedHashSet<>();
  // The collection of all RawNominalTypes.
  private Collection<RawNominalType> rawNominalTypes;

  GlobalTypeInfo(AbstractCompiler compiler, Set<String> unknownTypeNames) {
    // TODO(dimvar): it's bad style to refer to DiagnosticGroups after DefaultPassConfig.
    // When we have a nicer way to know whether we're in compatibility mode, use that
    // here instead (and in the constructor of NewTypeInference).
    boolean inCompatibilityMode =
        compiler.getOptions().disables(DiagnosticGroups.NEW_CHECK_TYPES_EXTRA_CHECKS);
    this.varNameGen = new UniqueNameGenerator();
    this.mismatches = new ArrayList<>();
    this.implicitInterfaceUses = new ArrayList<>();
    this.allPropertyNames.add("prototype");
    this.unknownTypeNames = unknownTypeNames;
    this.commonTypes = JSTypes.init(inCompatibilityMode);
    this.typeParser = new JSTypeCreatorFromJSDoc(
        this.getCommonTypes(),
        compiler.getCodingConvention(),
        this.varNameGen,
        new RecordPropertyCallBack());
  }

  class RecordPropertyCallBack implements Function<Node, Void>, Serializable {
    @Override
    public Void apply(Node pnameNode) {
      recordPropertyName(pnameNode);
      return null;
    }
  }

  void setGlobalScope(NTIScope globalScope) {
    this.globalScope = globalScope;
  }

  void recordPropertyName(Node pnameNode) {
    String pname = pnameNode.getString();
    allPropertyNames.add(pname);
    if (pnameNode.isFromExterns()) {
      externPropertyNames.add(pname);
    }
  }

  Set<String> getExternPropertyNames() {
    return this.externPropertyNames;
  }

  public JSTypeCreatorFromJSDoc getTypeParser() {
    return this.typeParser;
  }

  public UniqueNameGenerator getVarNameGen() {
    return this.varNameGen;
  }

  Map<Node, String> getAnonFunNames() {
    return this.anonFunNames;
  }

  List<TypeMismatch> getMismatches() {
    return this.mismatches;
  }

  List<TypeMismatch> getImplicitInterfaceUses() {
    return this.implicitInterfaceUses;
  }

  Collection<String> getAllPropertyNames() {
    return this.allPropertyNames;
  }

  boolean isPropertyDefined(String pname) {
    return allPropertyNames.contains(pname);
  }

  List<NTIScope> getScopes() {
    return this.scopes;
  }

  void setScopes(List<NTIScope> scopes) {
    this.scopes = scopes;
  }

  NTIScope getGlobalScope() {
    return this.globalScope;
  }

  Set<String> getUnknownTypeNames() {
    return this.unknownTypeNames;
  }

  JSTypes getCommonTypes() {
    return this.commonTypes;
  }

  // Differs from the similar method in NTIScope class on how it treats qnames.
  String getFunInternalName(Node n) {
    checkArgument(n.isFunction());
    if (anonFunNames.containsKey(n)) {
      return anonFunNames.get(n);
    }
    Node fnNameNode = NodeUtil.getNameNode(n);
    // We don't want to use qualified names here
    checkNotNull(fnNameNode);
    checkState(fnNameNode.isName(), "Expected name, found: %s", fnNameNode);
    return fnNameNode.getString();
  }

  void setRawNominalTypes(Collection<RawNominalType> rawNominalTypes) {
    checkState(this.rawNominalTypes == null);

    this.rawNominalTypes = new ArrayList<>(rawNominalTypes);
  }

  @Override
  public TypeI createTypeFromCommentNode(Node n) {
    return typeParser.getTypeOfCommentNode(n, null, globalScope);
  }

  @Override
  public JSType getNativeFunctionType(JSTypeNative typeId) {
    return getNativeType(typeId);
  }

  @Override
  public JSType getNativeObjectType(JSTypeNative typeId) {
    return getNativeType(typeId);
  }

  @Override
  public JSType getNativeType(JSTypeNative typeId) {
    return this.commonTypes.getNativeType(typeId);
  }

  @Override
  public String getReadableTypeName(Node n) {
    // TODO(rluble): remove the method from the JSTypeRegistry interface and inline the invocations
    // when the OTI is removed.
    return n.getTypeI().getDisplayName();
  }

  @Override
  public JSType getGlobalType(String typeName) {
    return getType(null, typeName);
  }

  @Override
  public JSType getType(String typeName) {
    return getType(null, typeName);
  }

  @SuppressWarnings("unchecked")
  @Override
  public JSType getType(StaticScope scope, String typeName) {
    // Primitives are not present in the global scope, so hardcode them
    switch (typeName) {
      case "boolean":
        return commonTypes.BOOLEAN;
      case "number":
        return commonTypes.NUMBER;
      case "string":
        return commonTypes.STRING;
      case "null":
        return commonTypes.NULL;
      case "undefined":
      case "void":
        return commonTypes.UNDEFINED;
      default:
        return this.globalScope.getType(typeName);
    }
  }

  @Override
  public String createGetterPropName(String originalPropName) {
    return this.commonTypes.createGetterPropName(originalPropName);
  }

  @Override
  public String createSetterPropName(String originalPropName) {
    return this.commonTypes.createSetterPropName(originalPropName);
  }

  @Override
  public TypeI createUnionType(List<? extends TypeI> members) {
    checkArgument(!members.isEmpty(), "Cannot create union type with no members");
    JSType result = commonTypes.BOTTOM;
    for (TypeI t : members) {
      result = JSType.join(result, (JSType) t);
    }
    return result;
  }

  /**
   * This method is only called by TTL and the typeEnv passed is the global scope.
   * We ignore the typeEnv here because createTypeFromCommentNode already uses the global scope.
   */
  @Override
  public TypeI evaluateTypeExpression(JSTypeExpression expr, TypeIEnv<TypeI> typeEnv) {
    return createTypeFromCommentNode(expr.getRoot());
  }

  @Override
  public TypeI evaluateTypeExpressionInGlobalScope(JSTypeExpression expr) {
    return createTypeFromCommentNode(expr.getRoot());
  }

  @Override
  public JSType createRecordType(Map<String, ? extends TypeI> props) {
    return JSType.fromProperties(this.commonTypes, (Map<String, JSType>) props);
  }

  @Override
  public JSType instantiateGenericType(
      ObjectTypeI genericType, ImmutableList<? extends TypeI> typeArgs) {
    JSType t = (JSType) genericType;
    int numTypeParams = t.getTypeParameters().size();
    int numTypeArgs = typeArgs.size();
    if (numTypeArgs == numTypeParams) {
      return t.instantiateGenerics(typeArgs);
    }
    ArrayList<JSType> newTypeArgs = new ArrayList<>(numTypeParams);
    for (int i = 0; i < numTypeParams; i++) {
      newTypeArgs.add(i < numTypeArgs ? (JSType) typeArgs.get(i) : this.commonTypes.UNKNOWN);
    }
    return t.instantiateGenerics(newTypeArgs);
  }

  @Override
  public JSType buildRecordTypeFromObject(ObjectTypeI obj) {
    return JSType.buildRecordTypeFromObject(this.commonTypes, (JSType) obj);
  }

  @GwtIncompatible("ObjectInputStream")
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    for (RawNominalType rawNominalType : this.rawNominalTypes) {
      rawNominalType.unfreezeForDeserialization();
    }
    for (RawNominalType rawNominalType : this.rawNominalTypes) {
      rawNominalType.fixSubtypesAfterDeserialization();
    }
    for (RawNominalType rawNominalType : this.rawNominalTypes) {
      rawNominalType.refreezeAfterDeserialization();
    }
  }
}
