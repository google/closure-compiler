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

import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.PolymerBehaviorExtractor.BehaviorDefinition;
import com.google.javascript.jscomp.PolymerPass.MemberDefinition;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Parsed Polymer class (element) definition. Includes convenient fields for rewriting the
 * class.
 */
final class PolymerClassDefinition {

  /** The target node (LHS) for the Polymer element definition. */
  final Node target;

  /** The object literal passed to the call to the Polymer() function. */
  final Node descriptor;

  /** The constructor function for the element. */
  final MemberDefinition constructor;

  /** The name of the native HTML element which this element extends. */
  final String nativeBaseElement;

  /** Properties declared in the Polymer "properties" block. */
  final List<MemberDefinition> props;

  /** Flattened list of behavior definitions used by this element. */
  final ImmutableList<BehaviorDefinition> behaviors;

  PolymerClassDefinition(
      Node target,
      Node descriptor,
      JSDocInfo classInfo,
      MemberDefinition constructor,
      String nativeBaseElement,
      List<MemberDefinition> props,
      ImmutableList<BehaviorDefinition> behaviors) {
    this.target = target;
    Preconditions.checkState(descriptor.isObjectLit());
    this.descriptor = descriptor;
    this.constructor = constructor;
    this.nativeBaseElement = nativeBaseElement;
    this.props = props;
    this.behaviors = behaviors;
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

    String elNameString = CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, elName.getString());
    elNameString += "Element";

    Node target;
    if (NodeUtil.isNameDeclaration(callNode.getGrandparent())) {
      target = IR.name(callNode.getParent().getString());
    } else if (callNode.getParent().isAssign()) {
      target = callNode.getParent().getFirstChild().cloneTree();
    } else {
      target = IR.name(elNameString);
    }

    target.useSourceInfoIfMissingFrom(callNode);
    JSDocInfo classInfo = NodeUtil.getBestJSDocInfo(target);

    JSDocInfo ctorInfo = null;
    Node constructor = NodeUtil.getFirstPropMatchingKey(descriptor, "factoryImpl");
    if (constructor == null) {
      constructor = IR.function(IR.name(""), IR.paramList(), IR.block());
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
    List<MemberDefinition> allProperties = new LinkedList<>();
    for (BehaviorDefinition behavior : behaviors) {
      overwriteMembersIfPresent(allProperties, behavior.props);
    }
    overwriteMembersIfPresent(allProperties, PolymerPassStaticUtils.extractProperties(descriptor));

    return new PolymerClassDefinition(
        target,
        descriptor,
        classInfo,
        new MemberDefinition(ctorInfo, null, constructor),
        nativeBaseElement,
        allProperties,
        behaviors);
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
}
