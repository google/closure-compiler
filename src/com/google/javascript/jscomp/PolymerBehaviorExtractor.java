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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import com.google.javascript.jscomp.PolymerPass.MemberDefinition;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

import java.util.List;

/**
 * Helpers to extract behaviors from Polymer element declarations.
 */
final class PolymerBehaviorExtractor {

  private static final ImmutableSet<String> BEHAVIOR_NAMES_NOT_TO_COPY = ImmutableSet.of(
        "created", "attached", "detached", "attributeChanged", "configure", "ready",
        "properties", "listeners", "observers", "hostAttributes");

  private final AbstractCompiler compiler;
  private final GlobalNamespace globalNames;

  PolymerBehaviorExtractor(AbstractCompiler compiler, GlobalNamespace globalNames) {
    this.compiler = compiler;
    this.globalNames = globalNames;
  }

  /**
   * Extracts all Behaviors from an array literal, recursively. Entries in the array can be
   * object literals or array literals (of other behaviors). Behavior names must be
   * global, fully qualified names.
   * @see https://github.com/Polymer/polymer/blob/0.8-preview/PRIMER.md#behaviors
   * @return A list of all {@code BehaviorDefinitions} in the array.
   */
  ImmutableList<BehaviorDefinition> extractBehaviors(Node behaviorArray) {
    if (behaviorArray == null) {
      return ImmutableList.of();
    }

    if (!behaviorArray.isArrayLit()) {
      compiler.report(
          JSError.make(behaviorArray, PolymerPassErrors.POLYMER_INVALID_BEHAVIOR_ARRAY));
      return ImmutableList.of();
    }

    ImmutableList.Builder<BehaviorDefinition> behaviors = ImmutableList.builder();
    for (Node behaviorName : behaviorArray.children()) {
      if (behaviorName.isObjectLit()) {
        PolymerPassStaticUtils.switchDollarSignPropsToBrackets(behaviorName, compiler);
        PolymerPassStaticUtils.quoteListenerAndHostAttributeKeys(behaviorName);
        behaviors.add(new BehaviorDefinition(
            PolymerPassStaticUtils.extractProperties(behaviorName),
            getBehaviorFunctionsToCopy(behaviorName),
            getNonPropertyMembersToCopy(behaviorName),
            !NodeUtil.isInFunction(behaviorName)));
        continue;
      }

      Name behaviorGlobalName = globalNames.getSlot(behaviorName.getQualifiedName());
      boolean isGlobalDeclaration = true;
      if (behaviorGlobalName == null) {
        compiler.report(JSError.make(behaviorName, PolymerPassErrors.POLYMER_UNQUALIFIED_BEHAVIOR));
        continue;
      }

      Ref behaviorDeclaration = behaviorGlobalName.getDeclaration();

      // Use any set as a backup declaration, even if it's local.
      if (behaviorDeclaration == null) {
        List<Ref> behaviorRefs = behaviorGlobalName.getRefs();
        for (Ref ref : behaviorRefs) {
          if (ref.isSet()) {
            isGlobalDeclaration = false;
            behaviorDeclaration = ref;
            break;
          }
        }
      }

      if (behaviorDeclaration == null) {
        compiler.report(JSError.make(behaviorName, PolymerPassErrors.POLYMER_UNQUALIFIED_BEHAVIOR));
        continue;
      }

      Node behaviorDeclarationNode = behaviorDeclaration.getNode();
      JSDocInfo behaviorInfo = NodeUtil.getBestJSDocInfo(behaviorDeclarationNode);
      if (behaviorInfo == null || !behaviorInfo.isPolymerBehavior()) {
        compiler.report(
            JSError.make(behaviorDeclarationNode, PolymerPassErrors.POLYMER_UNANNOTATED_BEHAVIOR));
      }

      Node behaviorValue = NodeUtil.getRValueOfLValue(behaviorDeclarationNode);

      if (behaviorValue == null) {
        compiler.report(JSError.make(behaviorName, PolymerPassErrors.POLYMER_UNQUALIFIED_BEHAVIOR));
      } else if (behaviorValue.isArrayLit()) {
        // Individual behaviors can also be arrays of behaviors. Parse them recursively.
        behaviors.addAll(extractBehaviors(behaviorValue));
      } else if (behaviorValue.isObjectLit()) {
        PolymerPassStaticUtils.switchDollarSignPropsToBrackets(behaviorValue, compiler);
        PolymerPassStaticUtils.quoteListenerAndHostAttributeKeys(behaviorValue);
        behaviors.add(new BehaviorDefinition(
            PolymerPassStaticUtils.extractProperties(behaviorValue),
            getBehaviorFunctionsToCopy(behaviorValue),
            getNonPropertyMembersToCopy(behaviorValue),
            isGlobalDeclaration));
      } else {
        compiler.report(JSError.make(behaviorName, PolymerPassErrors.POLYMER_UNQUALIFIED_BEHAVIOR));
      }
    }

    return behaviors.build();
  }

  /**
   * @return A list of functions from a behavior which should be copied to the element prototype.
   */
  private static ImmutableList<MemberDefinition> getBehaviorFunctionsToCopy(Node behaviorObjLit) {
    Preconditions.checkState(behaviorObjLit.isObjectLit());
    ImmutableList.Builder<MemberDefinition> functionsToCopy = ImmutableList.builder();

    for (Node keyNode : behaviorObjLit.children()) {
      boolean isFunctionDefinition = (keyNode.isStringKey() && keyNode.getFirstChild().isFunction())
          || keyNode.isMemberFunctionDef();
      if (isFunctionDefinition && !BEHAVIOR_NAMES_NOT_TO_COPY.contains(keyNode.getString())) {
        functionsToCopy.add(new MemberDefinition(NodeUtil.getBestJSDocInfo(keyNode), keyNode,
          keyNode.getFirstChild()));
      }
    }

    return functionsToCopy.build();
  }

  /**
   * @return A list of MemberDefinitions in a behavior which are not in the properties block, but
   *     should still be copied to the element prototype.
   */
  private static ImmutableList<MemberDefinition> getNonPropertyMembersToCopy(Node behaviorObjLit) {
    Preconditions.checkState(behaviorObjLit.isObjectLit());
    ImmutableList.Builder<MemberDefinition> membersToCopy = ImmutableList.builder();

    for (Node keyNode : behaviorObjLit.children()) {
      boolean isNonFunctionMember = keyNode.isGetterDef()
          || (keyNode.isStringKey() && !keyNode.getFirstChild().isFunction());
      if (isNonFunctionMember && !BEHAVIOR_NAMES_NOT_TO_COPY.contains(keyNode.getString())) {
        membersToCopy.add(new MemberDefinition(NodeUtil.getBestJSDocInfo(keyNode), keyNode,
          keyNode.getFirstChild()));
      }
    }

    return membersToCopy.build();
  }

  /**
   * Parsed definition of a Polymer Behavior. Includes members which should be copied to elements
   * which use the behavior.
   */
  static final class BehaviorDefinition {
    /**
     * Properties declared in the behavior 'properties' block.
     */
    final List<MemberDefinition> props;

    /**
     * Functions intended to be copied to elements which use this Behavior.
     */
    final List<MemberDefinition> functionsToCopy;

    /**
     * Other members intended to be copied to elements which use this Behavior.
     */
    final List<MemberDefinition> nonPropertyMembersToCopy;

    /**
     * Whether this Behvaior is declared in the global scope.
     */
    final boolean isGlobalDeclaration;

    BehaviorDefinition(
        List<MemberDefinition> props, List<MemberDefinition> functionsToCopy,
        List<MemberDefinition> nonPropertyMembersToCopy, boolean isGlobalDeclaration) {
      this.props = props;
      this.functionsToCopy = functionsToCopy;
      this.nonPropertyMembersToCopy = nonPropertyMembersToCopy;
      this.isGlobalDeclaration = isGlobalDeclaration;
    }
  }
}
