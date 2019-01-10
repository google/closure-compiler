/*
 * Copyright 2016 The Closure Compiler Authors.
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

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.PolymerBehaviorExtractor.BehaviorDefinition;
import com.google.javascript.jscomp.PolymerPass.MemberDefinition;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Parsed Polymer class (element) definition. Includes convenient fields for rewriting the
 * class.
 */
final class PolymerClassDefinition {
  static enum DefinitionType {
    ObjectLiteral,
    ES6Class
  }

  /** The declaration style used for the Polymer definition */
  final DefinitionType defType;

  /** The Polymer call or class node which defines the Element. */
  final Node definition;

  /** The target node (LHS) for the Polymer element definition. */
  final Node target;

  /** The object literal passed to the call to the Polymer() function. */
  final Node descriptor;

  /** The constructor function for the element. */
  final MemberDefinition constructor;

  /** The name of the native HTML element which this element extends. */
  @Nullable final String nativeBaseElement;

  /** Properties declared in the Polymer "properties" block. */
  final List<MemberDefinition> props;

  /** Methods on the element. */
  @Nullable final List<MemberDefinition> methods;

  /** Flattened list of behavior definitions used by this element. */
  @Nullable final ImmutableList<BehaviorDefinition> behaviors;

  /** Language features that should be carried over to the extraction destination. */
  @Nullable final FeatureSet features;

  PolymerClassDefinition(
      DefinitionType defType,
      Node definition,
      Node target,
      Node descriptor,
      JSDocInfo classInfo,
      MemberDefinition constructor,
      String nativeBaseElement,
      List<MemberDefinition> props,
      List<MemberDefinition> methods,
      ImmutableList<BehaviorDefinition> behaviors,
      FeatureSet features) {
    this.defType = defType;
    this.definition = definition;
    this.target = target;
    checkState(descriptor == null || descriptor.isObjectLit());
    this.descriptor = descriptor;
    this.constructor = constructor;
    this.nativeBaseElement = nativeBaseElement;
    this.props = props;
    this.methods = methods;
    this.behaviors = behaviors;
    this.features = features;
  }

  /**
   * Validates the class definition and if valid, destructively extracts the class definition from
   * the AST.
   */
  @Nullable static PolymerClassDefinition extractFromCallNode(
      Node callNode, AbstractCompiler compiler, GlobalNamespace globalNames) {
    Node descriptor = NodeUtil.getArgumentForCallOrNew(callNode, 0);
    if (descriptor == null || !descriptor.isObjectLit()) {
      // report bad class definition
      compiler.report(JSError.make(callNode, PolymerPassErrors.POLYMER_DESCRIPTOR_NOT_VALID));
      return null;
    }

    int paramCount = callNode.getChildCount() - 1;
    if (paramCount != 1) {
      compiler.report(JSError.make(callNode, PolymerPassErrors.POLYMER_UNEXPECTED_PARAMS));
      return null;
    }

    Node elName = NodeUtil.getFirstPropMatchingKey(descriptor, "is");
    if (elName == null) {
      compiler.report(JSError.make(callNode, PolymerPassErrors.POLYMER_MISSING_IS));
      return null;
    }

    Node target;
    if (NodeUtil.isNameDeclaration(callNode.getGrandparent())) {
      target = IR.name(callNode.getParent().getString());
    } else if (callNode.getParent().isAssign()) {
      target = callNode.getParent().getFirstChild().cloneTree();
    } else {
      String elNameStringBase =
          elName.isQualifiedName()
              ? elName.getQualifiedName().replace('.', '$')
              : elName.getString();
      String elNameString = CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, elNameStringBase);
      elNameString += "Element";
      target = IR.name(elNameString);
    }

    JSDocInfo classInfo = NodeUtil.getBestJSDocInfo(target);

    JSDocInfo ctorInfo = null;
    Node constructor = NodeUtil.getFirstPropMatchingKey(descriptor, "factoryImpl");
    if (constructor == null) {
      constructor = NodeUtil.emptyFunction();
      compiler.reportChangeToChangeScope(constructor);
      constructor.useSourceInfoFromForTree(callNode);
    } else {
      ctorInfo = NodeUtil.getBestJSDocInfo(constructor);
    }

    Node baseClass = NodeUtil.getFirstPropMatchingKey(descriptor, "extends");
    String nativeBaseElement = baseClass == null ? null : baseClass.getString();

    Node behaviorArray = NodeUtil.getFirstPropMatchingKey(descriptor, "behaviors");
    PolymerBehaviorExtractor behaviorExtractor =
        new PolymerBehaviorExtractor(compiler, globalNames);
    ImmutableList<BehaviorDefinition> behaviors = behaviorExtractor.extractBehaviors(behaviorArray);
    List<MemberDefinition> allProperties = new ArrayList<>();
    for (BehaviorDefinition behavior : behaviors) {
      overwriteMembersIfPresent(allProperties, behavior.props);
    }
    overwriteMembersIfPresent(
        allProperties,
        PolymerPassStaticUtils.extractProperties(
            descriptor,
            DefinitionType.ObjectLiteral,
            compiler,
            /** constructor= */
            null));

    FeatureSet newFeatures = null;
    if (!behaviors.isEmpty()) {
      newFeatures = behaviors.get(0).features;
      for (int i = 1; i < behaviors.size(); i++) {
        newFeatures = newFeatures.union(behaviors.get(i).features);
      }
    }

    List<MemberDefinition> methods = new ArrayList<>();
    for (Node keyNode : descriptor.children()) {
      boolean isFunctionDefinition =
          keyNode.isMemberFunctionDef()
              || (keyNode.isStringKey() && keyNode.getFirstChild().isFunction());
      if (isFunctionDefinition) {
        methods.add(
            new MemberDefinition(
                NodeUtil.getBestJSDocInfo(keyNode), keyNode, keyNode.getFirstChild()));
      }
    }

    return new PolymerClassDefinition(
        DefinitionType.ObjectLiteral,
        callNode,
        target,
        descriptor,
        classInfo,
        new MemberDefinition(ctorInfo, null, constructor),
        nativeBaseElement,
        allProperties,
        methods,
        behaviors,
        newFeatures);
  }

  /**
   * Validates the class definition and if valid, extracts the class definition from the AST. As
   * opposed to the Polymer 1 extraction, this operation is non-destructive.
   */
  @Nullable
  static PolymerClassDefinition extractFromClassNode(
      Node classNode, AbstractCompiler compiler, GlobalNamespace globalNames) {
    checkState(classNode != null && classNode.isClass());

    // The supported case is for the config getter to return an object literal descriptor.
    Node propertiesDescriptor = null;
    Node propertiesGetter =
        NodeUtil.getFirstGetterMatchingKey(NodeUtil.getClassMembers(classNode), "properties");
    if (propertiesGetter != null) {
      if (!propertiesGetter.isStaticMember()) {
        // report bad class definition
        compiler.report(
            JSError.make(classNode, PolymerPassErrors.POLYMER_CLASS_PROPERTIES_NOT_STATIC));
      } else {
        for (Node child : NodeUtil.getFunctionBody(propertiesGetter.getFirstChild()).children()) {
          if (child.isReturn()) {
            if (child.hasChildren() && child.getFirstChild().isObjectLit()) {
              propertiesDescriptor = child.getFirstChild();
              break;
            } else {
              compiler.report(
                  JSError.make(
                      propertiesGetter, PolymerPassErrors.POLYMER_CLASS_PROPERTIES_INVALID));
            }
          }
        }
      }
    }

    Node target;
    if (NodeUtil.isNameDeclaration(classNode.getGrandparent())) {
      target = IR.name(classNode.getParent().getString());
    } else if (classNode.getParent().isAssign()
        && classNode.getParent().getFirstChild().isQualifiedName()) {
      target = classNode.getParent().getFirstChild();
    } else if (!classNode.getFirstChild().isEmpty()) {
      target = classNode.getFirstChild();
    } else {
      // issue error - no name found
      compiler.report(JSError.make(classNode, PolymerPassErrors.POLYMER_CLASS_UNNAMED));
      return null;
    }

    JSDocInfo classInfo = NodeUtil.getBestJSDocInfo(classNode);

    JSDocInfo ctorInfo = null;
    Node constructor =
        NodeUtil.getFirstPropMatchingKey(NodeUtil.getClassMembers(classNode), "constructor");
    if (constructor != null) {
      ctorInfo = NodeUtil.getBestJSDocInfo(constructor);
    }

    List<MemberDefinition> allProperties =
        PolymerPassStaticUtils.extractProperties(
            propertiesDescriptor, DefinitionType.ES6Class, compiler, constructor);

    List<MemberDefinition> methods = new ArrayList<>();
    for (Node keyNode : NodeUtil.getClassMembers(classNode).children()) {
      if (!keyNode.isMemberFunctionDef()) {
        continue;
      }
      methods.add(new MemberDefinition(
                      NodeUtil.getBestJSDocInfo(keyNode), keyNode, keyNode.getFirstChild()));
    }

    return new PolymerClassDefinition(
        DefinitionType.ES6Class,
        classNode,
        target,
        propertiesDescriptor,
        classInfo,
        new MemberDefinition(ctorInfo, null, constructor),
        null,
        allProperties,
        methods,
        null,
        null);
  }

  /**
   * Appends a list of new MemberDefinitions to the end of a list and removes any previous
   * MemberDefinition in the list which has the same name as the new member.
   */
  private static void overwriteMembersIfPresent(
      List<MemberDefinition> list, List<MemberDefinition> newMembers) {
    for (MemberDefinition newMember : newMembers) {
      for (MemberDefinition member : list) {
        if (member.name.getString().equals(newMember.name.getString())) {
          list.remove(member);
          break;
        }
      }
      list.add(newMember);
    }
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("defType", defType)
        .add("definition", definition)
        .add("target", target)
        .add("nativeBaseElement", nativeBaseElement)
        .omitNullValues()
        .toString();
  }
}
