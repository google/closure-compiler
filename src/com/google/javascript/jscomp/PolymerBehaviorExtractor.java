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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import com.google.javascript.jscomp.PolymerPass.MemberDefinition;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Finds the Polymer behavior definitions associated with Polymer element definitions.
 * @see https://www.polymer-project.org/1.0/docs/devguide/behaviors
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
        PolymerPassStaticUtils.quoteListenerAndHostAttributeKeys(behaviorName, compiler);
        if (NodeUtil.getFirstPropMatchingKey(behaviorName, "is") != null) {
          compiler.report(JSError.make(behaviorName, PolymerPassErrors.POLYMER_INVALID_BEHAVIOR));
        }
        behaviors.add(
            new BehaviorDefinition(
                PolymerPassStaticUtils.extractProperties(
                    behaviorName,
                    PolymerClassDefinition.DefinitionType.ObjectLiteral,
                    compiler,
                    /** constructor= */
                    null),
                getBehaviorFunctionsToCopy(behaviorName),
                getNonPropertyMembersToCopy(behaviorName),
                !NodeUtil.isInFunction(behaviorName),
                (FeatureSet) NodeUtil.getEnclosingScript(behaviorName).getProp(Node.FEATURE_SET)));
        continue;
      }

      ResolveBehaviorNameResult resolveResult = resolveBehaviorName(behaviorName);
      if (resolveResult == null) {
        compiler.report(JSError.make(behaviorName, PolymerPassErrors.POLYMER_UNQUALIFIED_BEHAVIOR));
        continue;
      }
      Node behaviorValue = resolveResult.node;

      if (behaviorValue.isArrayLit()) {
        // Individual behaviors can also be arrays of behaviors. Parse them recursively.
        behaviors.addAll(extractBehaviors(behaviorValue));
      } else if (behaviorValue.isObjectLit()) {
        PolymerPassStaticUtils.switchDollarSignPropsToBrackets(behaviorValue, compiler);
        PolymerPassStaticUtils.quoteListenerAndHostAttributeKeys(behaviorValue, compiler);
        if (NodeUtil.getFirstPropMatchingKey(behaviorValue, "is") != null) {
          compiler.report(JSError.make(behaviorValue, PolymerPassErrors.POLYMER_INVALID_BEHAVIOR));
        }
        behaviors.add(
            new BehaviorDefinition(
                PolymerPassStaticUtils.extractProperties(
                    behaviorValue,
                    PolymerClassDefinition.DefinitionType.ObjectLiteral,
                    compiler,
                    /** constructor= */
                    null),
                getBehaviorFunctionsToCopy(behaviorValue),
                getNonPropertyMembersToCopy(behaviorValue),
                resolveResult.isGlobalDeclaration,
                (FeatureSet) NodeUtil.getEnclosingScript(behaviorValue).getProp(Node.FEATURE_SET)));
      } else {
        compiler.report(JSError.make(behaviorName, PolymerPassErrors.POLYMER_UNQUALIFIED_BEHAVIOR));
      }
    }

    return behaviors.build();
  }

  private static class ResolveBehaviorNameResult {
    final Node node;
    final boolean isGlobalDeclaration;

    public ResolveBehaviorNameResult(Node node, boolean isGlobalDeclaration) {
      this.node = node;
      this.isGlobalDeclaration = isGlobalDeclaration;
    }
  }

  /**
   * Resolve an identifier, which is presumed to refer to a Polymer Behavior declaration, using the
   * global namespace. Recurses to resolve assignment chains of any length.
   *
   * @param nameNode The NAME, GETPROP, or CAST node containing the identifier.
   * @return The behavior declaration node, or null if it couldn't be resolved.
   */
  @Nullable
  private ResolveBehaviorNameResult resolveBehaviorName(Node nameNode) {
    String name = getQualifiedNameThroughCast(nameNode);
    if (name == null) {
      return null;
    }
    Name globalName = globalNames.getSlot(name);
    if (globalName == null) {
      return null;
    }

    boolean isGlobalDeclaration = true;

    // Use any set as a backup declaration, even if it's local.
    Ref declarationRef = globalName.getDeclaration();
    if (declarationRef == null) {
      List<Ref> behaviorRefs = globalName.getRefs();
      for (Ref ref : behaviorRefs) {
        if (ref.isSet()) {
          isGlobalDeclaration = false;
          declarationRef = ref;
          break;
        }
      }
    }
    if (declarationRef == null) {
      return null;
    }

    Node declarationNode = declarationRef.getNode();
    if (declarationNode == null) {
      return null;
    }
    Node rValue = NodeUtil.getRValueOfLValue(declarationNode);
    if (rValue == null) {
      return null;
    }

    if (rValue.isQualifiedName()) {
      // Another identifier; recurse.
      return resolveBehaviorName(rValue);
    }

    JSDocInfo behaviorInfo = NodeUtil.getBestJSDocInfo(declarationNode);
    if (behaviorInfo == null || !behaviorInfo.isPolymerBehavior()) {
      compiler.report(
          JSError.make(declarationNode, PolymerPassErrors.POLYMER_UNANNOTATED_BEHAVIOR));
    }

    return new ResolveBehaviorNameResult(rValue, isGlobalDeclaration);
  }

  /**
   * @return A list of functions from a behavior which should be copied to the element prototype.
   */
  private static ImmutableList<MemberDefinition> getBehaviorFunctionsToCopy(Node behaviorObjLit) {
    checkState(behaviorObjLit.isObjectLit());
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
   * Similar to {@link Node#getQualifiedName} but also handles CAST nodes. For example, given a
   * GETPROP representing "(/** @type {?} *\/ (x)).y.z" returns "x.y.z". Returns null if node is
   * not a NAME, GETPROP, or CAST. See b/64389806 for Polymer-specific context.
   */
  @Nullable
  private static String getQualifiedNameThroughCast(Node node) {
    if (node.isName()) {
      String name = node.getString();
      return name.isEmpty() ? null : name;
    } else if (node.isGetProp()) {
      String left = getQualifiedNameThroughCast(node.getFirstChild());
      if (left == null) {
        return null;
      }
      String right = node.getLastChild().getString();
      return left + "." + right;
    } else if (node.isCast()) {
      return getQualifiedNameThroughCast(node.getFirstChild());
    }
    return null;
  }

  /**
   * @return A list of MemberDefinitions in a behavior which are not in the properties block, but
   *     should still be copied to the element prototype.
   */
  private static ImmutableList<MemberDefinition> getNonPropertyMembersToCopy(Node behaviorObjLit) {
    checkState(behaviorObjLit.isObjectLit());
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
     * Whether this Behavior is declared in the global scope.
     */
    final boolean isGlobalDeclaration;

    /**
     * Language features to carry over to the extraction destination.
     */
    final FeatureSet features;

    BehaviorDefinition(
        List<MemberDefinition> props, List<MemberDefinition> functionsToCopy,
        List<MemberDefinition> nonPropertyMembersToCopy, boolean isGlobalDeclaration,
        FeatureSet features) {
      this.props = props;
      this.functionsToCopy = functionsToCopy;
      this.nonPropertyMembersToCopy = nonPropertyMembersToCopy;
      this.isGlobalDeclaration = isGlobalDeclaration;
      this.features = features;
    }
  }
}
