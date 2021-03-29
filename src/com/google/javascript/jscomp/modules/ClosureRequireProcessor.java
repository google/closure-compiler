/*
 * Copyright 2019 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.modules;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.modules.Binding.CreatedBy;
import com.google.javascript.rhino.Node;
import javax.annotation.Nullable;

/**
 * Handles creating an {@link Import} from goog.require(Type) or goog.forwardDeclare.
 *
 * <p>This logic can be used by both goog.modules and ES modules
 */
final class ClosureRequireProcessor {
  private final Node nameDeclaration;
  private final CreatedBy requireKind;

  /** Represents a goog.require(Type) or goog.forwardDeclare */
  @AutoValue
  abstract static class Require {
    /** The name local to the module with the require; e.g. `b` in `const b = goog.require('a');` */
    abstract String localName();
    /** An {@link Import} containing all metadata about this require */
    abstract Import importRecord();
    /** Whether this is a goog.require, goog.requireType, or goog.forwardDeclare */
    abstract Binding.CreatedBy createdBy();

    private static Require create(
        String localName, Import importRecord, Binding.CreatedBy createdBy) {
      checkArgument(createdBy.isClosureImport());
      return new AutoValue_ClosureRequireProcessor_Require(localName, importRecord, createdBy);
    }
  }

  private ClosureRequireProcessor(Node nameDeclaration, Binding.CreatedBy requireKind) {
    checkArgument(NodeUtil.isNameDeclaration(nameDeclaration));
    this.nameDeclaration = nameDeclaration;
    this.requireKind = requireKind;
  }

  /**
   * Returns all Require built from the given statement, or null if it is not a require
   *
   * @param nameDeclaration a VAR, LET, or CONST
   * @return all Requires contained in this declaration
   */
  static ImmutableList<Require> getAllRequires(Node nameDeclaration) {
    Node rhs =
        nameDeclaration.getFirstChild().isDestructuringLhs()
            ? nameDeclaration.getFirstChild().getSecondChild()
            : nameDeclaration.getFirstFirstChild();
    // This may be a require, requireType, or forwardDeclare.
    Binding.CreatedBy requireKind = getModuleDependencyTypeFromRhs(rhs);
    if (requireKind == null) {
      return ImmutableList.of();
    }

    return new ClosureRequireProcessor(nameDeclaration, requireKind).getAllRequiresInDeclaration();
  }

  private static final ImmutableMap<String, CreatedBy> GOOG_DEPENDENCY_CALLS =
      ImmutableMap.of(
          "require",
          CreatedBy.GOOG_REQUIRE,
          "requireType",
          CreatedBy.GOOG_REQUIRE_TYPE,
          "forwardDeclare",
          CreatedBy.GOOG_FORWARD_DECLARE);

  /**
   * Checks if the given rvalue is a goog.require(Type) or goog.forwardDeclare call, and if so
   * returns which one.
   *
   * @return A Closure require (where {@link CreatedBy#isClosureImport()} is true) or null.
   */
  @Nullable
  private static CreatedBy getModuleDependencyTypeFromRhs(@Nullable Node value) {
    if (value == null
        || !value.isCall()
        || !value.hasTwoChildren()
        || !value.getSecondChild().isStringLit()) {
      return null;
    }
    Node callee = value.getFirstChild();
    if (!callee.isGetProp()) {
      return null;
    }
    Node owner = callee.getFirstChild();
    if (!owner.isName() || !owner.getString().equals("goog")) {
      return null;
    }
    return GOOG_DEPENDENCY_CALLS.get(callee.getString());
  }

  /** Returns a new list of all required names in {@link #nameDeclaration} */
  private ImmutableList<Require> getAllRequiresInDeclaration() {
    Node rhs =
        nameDeclaration.getFirstChild().isDestructuringLhs()
            ? nameDeclaration.getFirstChild().getSecondChild()
            : nameDeclaration.getFirstFirstChild();

    String namespace = rhs.getSecondChild().getString();

    if (nameDeclaration.getFirstChild().isName()) {
      // const modA = goog.require('modA');
      Node lhs = nameDeclaration.getFirstChild();
      return ImmutableList.of(
          Require.create(
              lhs.getString(),
              Import.builder()
                  .moduleRequest(namespace)
                  .localName(lhs.getString())
                  .importName(Export.NAMESPACE)
                  .importNode(nameDeclaration)
                  .nameNode(lhs)
                  .build(),
              requireKind));
    } else {
      // const {x, y} = goog.require('modA');
      Node objectPattern = nameDeclaration.getFirstFirstChild();
      if (!objectPattern.isObjectPattern()) {
        // bad JS, ignore
        return ImmutableList.of();
      }
      return getAllRequiresFromDestructuring(objectPattern, namespace);
    }
  }

  /** Returns all requires from destructruring, like `const {x, y, z} = goog.require('a');` */
  private ImmutableList<Require> getAllRequiresFromDestructuring(
      Node objectPattern, String namespace) {
    ImmutableList.Builder<Require> requireBuilder = ImmutableList.builder();
    for (Node key = objectPattern.getFirstChild(); key != null; key = key.getNext()) {
      if (!key.isStringKey()) {
        // Bad code, just ignore. We warn elsewhere.
        continue;
      }
      Node lhs = key.getOnlyChild();
      if (!lhs.isName()) {
        // Bad code ( e.g. `const {a = 0} = goog.require(...)`). We warn elsewhere.
        continue;
      }

      requireBuilder.add(
          Require.create(
              lhs.getString(),
              Import.builder()
                  .moduleRequest(namespace)
                  .localName(lhs.getString())
                  .importName(key.getString())
                  .importNode(nameDeclaration)
                  .nameNode(lhs)
                  .build(),
              requireKind));
    }
    return requireBuilder.build();
  }
}
