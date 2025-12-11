/*
 * Copyright 2025 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.parsing;

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import java.util.List;

/**
 * Class that deserializes an AstNode-tree representing a SCRIPT into a Node-tree.
 *
 * <p>This process depends on other information from the TypedAST format, but the output it limited
 * to only a single SCRIPT. The other deserialized content must be provided beforehand.
 */
public final class FeatureCollector {

  private FeatureSet scriptFeatures = FeatureSet.BARE_MINIMUM;

  public FeatureSet allFeatures() {
    return scriptFeatures;
  }

  /**
   * Records features for a single node.
   *
   * @param contextStack Ancestor node contexts. Last is the parent of node.
   * @param node The node to visit.
   */
  public FeatureContext visitSingleNode(List<FeatureContext> contextStack, Node node) {
    checkState(
        contextStack.size() >= 1,
        "The provided feature context stack was empty. It must have at least one element.");

    recordScriptFeatures(contextStack, node);
    return contextFor(contextStack, node);
  }

  private void recordScriptFeatures(List<FeatureContext> contextStack, Node node) {
    FeatureContext parentContext = contextStack.getLast();
    switch (node.getToken()) {
      case FUNCTION -> {
        if (node.isAsyncGeneratorFunction()) {
          addScriptFeature(Feature.ASYNC_GENERATORS);
        }
        if (node.isArrowFunction()) {
          addScriptFeature(Feature.ARROW_FUNCTIONS);
        }
        if (node.isAsyncFunction()) {
          addScriptFeature(Feature.ASYNC_FUNCTIONS);
        }
        if (node.isGeneratorFunction()) {
          addScriptFeature(Feature.GENERATORS);
        }

        if (parentContext.equals(FeatureContext.BLOCK_SCOPE)) {
          scriptFeatures = scriptFeatures.with(Feature.BLOCK_SCOPED_FUNCTION_DECLARATION);
        }
      }
      case PARAM_LIST, CALL, NEW -> {}
      case STRING_KEY -> {
        if (node.isShorthandProperty() && parentContext.equals(FeatureContext.OBJECT_LITERAL)) {
          addScriptFeature(Feature.SHORTHAND_OBJECT_PROPERTIES);
        }
      }
      case DEFAULT_VALUE -> {
        if (parentContext.equals(FeatureContext.PARAM_LIST)) {
          addScriptFeature(Feature.DEFAULT_PARAMETERS);
        }
      }
      case GETTER_DEF -> {
        addScriptFeature(Feature.GETTER);
        if (parentContext.equals(FeatureContext.CLASS_MEMBERS)) {
          addScriptFeature(Feature.CLASS_GETTER_SETTER);
        }
      }
      case REGEXP -> addScriptFeature(Feature.REGEXP_SYNTAX);
      case SETTER_DEF -> {
        addScriptFeature(Feature.SETTER);
        if (parentContext.equals(FeatureContext.CLASS_MEMBERS)) {
          addScriptFeature(Feature.CLASS_GETTER_SETTER);
        }
      }
      case BLOCK -> {
        if (parentContext.equals(FeatureContext.CLASS_MEMBERS)) {
          addScriptFeature(Feature.CLASS_STATIC_BLOCK);
        }
      }
      case EMPTY -> {
        if (parentContext.equals(FeatureContext.CATCH)) {
          addScriptFeature(Feature.OPTIONAL_CATCH_BINDING);
        }
      }
      case ITER_REST -> {
        if (parentContext.equals(FeatureContext.PARAM_LIST)) {
          addScriptFeature(Feature.REST_PARAMETERS);
        } else {
          addScriptFeature(Feature.ARRAY_PATTERN_REST);
        }
      }
      case ITER_SPREAD -> addScriptFeature(Feature.SPREAD_EXPRESSIONS);
      case OBJECT_REST -> addScriptFeature(Feature.OBJECT_PATTERN_REST);
      case OBJECT_SPREAD -> addScriptFeature(Feature.OBJECT_LITERALS_WITH_SPREAD);
      case BIGINT -> addScriptFeature(Feature.BIGINT);
      case EXPONENT, ASSIGN_EXPONENT -> addScriptFeature(Feature.EXPONENT_OP);
      case TAGGED_TEMPLATELIT, TEMPLATELIT -> addScriptFeature(Feature.TEMPLATE_LITERALS);
      case NEW_TARGET -> addScriptFeature(Feature.NEW_TARGET);
      case COMPUTED_PROP -> {
        addScriptFeature(Feature.COMPUTED_PROPERTIES);
        boolean isGetter = node.getBooleanProp(Node.COMPUTED_PROP_GETTER);
        boolean isSetter = node.getBooleanProp(Node.COMPUTED_PROP_SETTER);
        boolean isClassMember = parentContext.equals(FeatureContext.CLASS_MEMBERS);
        if (isGetter) {
          addScriptFeature(Feature.GETTER);
        } else if (isSetter) {
          addScriptFeature(Feature.SETTER);
        }
        if ((isGetter || isSetter) && isClassMember) {
          addScriptFeature(Feature.CLASS_GETTER_SETTER);
        }
      }
      case OPTCHAIN_GETPROP, OPTCHAIN_CALL, OPTCHAIN_GETELEM ->
          addScriptFeature(Feature.OPTIONAL_CHAINING);
      case COALESCE -> addScriptFeature(Feature.NULL_COALESCE_OP);
      case DYNAMIC_IMPORT -> addScriptFeature(Feature.DYNAMIC_IMPORT);
      case ASSIGN_OR, ASSIGN_AND -> addScriptFeature(Feature.LOGICAL_ASSIGNMENT);
      case ASSIGN_COALESCE -> {
        addScriptFeature(Feature.NULL_COALESCE_OP);
        addScriptFeature(Feature.LOGICAL_ASSIGNMENT);
      }
      case FOR_OF -> addScriptFeature(Feature.FOR_OF);
      case FOR_AWAIT_OF -> addScriptFeature(Feature.FOR_AWAIT_OF);
      case IMPORT, EXPORT -> addScriptFeature(Feature.MODULES);
      case CONST -> addScriptFeature(Feature.CONST_DECLARATIONS);
      case LET -> addScriptFeature(Feature.LET_DECLARATIONS);
      case CLASS -> addScriptFeature(Feature.CLASSES);
      case MEMBER_FUNCTION_DEF -> addScriptFeature(Feature.MEMBER_DECLARATIONS);
      case MEMBER_FIELD_DEF, COMPUTED_FIELD_DEF -> addScriptFeature(Feature.PUBLIC_CLASS_FIELDS);
      case SUPER -> addScriptFeature(Feature.SUPER);
      case ARRAY_PATTERN -> addScriptFeature(Feature.ARRAY_DESTRUCTURING);
      case OBJECT_PATTERN -> addScriptFeature(Feature.OBJECT_DESTRUCTURING);
      default -> {}
    }
  }

  private void addScriptFeature(Feature x) {
    scriptFeatures = scriptFeatures.with(x);
  }

  /**
   * Context of a node while deserializing, specifically for the purpose of tracking {@link
   * com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature}
   *
   * <p>These contexts are pushed onto a stack as the AST is traversed. The intent is to have a way
   * to model the ancestry of a node being visited before the AST is fully built, as parent pointers
   * may not have been instantiated yet.
   */
  public static enum FeatureContext {
    PARAM_LIST,
    CLASS_MEMBERS,
    CLASS,
    CATCH,
    // the top of a block scope, e.g. within an if/while/for loop block, or a plain `{ }` block
    BLOCK_SCOPE,
    FUNCTION,
    // includes objects like `return {x, y};` but /not/ object destructuring like `const {x} = o;`
    OBJECT_LITERAL,
    NONE;
  }

  private static FeatureContext contextFor(List<FeatureContext> contextStack, Node node) {
    return switch (node.getToken()) {
      case PARAM_LIST -> FeatureContext.PARAM_LIST;
      case CLASS_MEMBERS -> FeatureContext.CLASS_MEMBERS;
      case CLASS -> FeatureContext.CLASS;
      case CATCH -> FeatureContext.CATCH;
      case BLOCK ->
          // a function body is not a block scope - BLOCK is just overloaded. all other references
          // to BLOCK are block scopes.
          contextStack.getLast().equals(FeatureContext.FUNCTION)
              ? FeatureContext.NONE
              : FeatureContext.BLOCK_SCOPE;
      case FUNCTION -> FeatureContext.FUNCTION;
      case OBJECTLIT -> FeatureContext.OBJECT_LITERAL;
      default -> FeatureContext.NONE;
    };
  }
}
