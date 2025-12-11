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

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import org.jspecify.annotations.Nullable;

/**
 * Class that deserializes an AstNode-tree representing a SCRIPT into a Node-tree.
 *
 * <p>This process depends on other information from the TypedAST format, but the output it limited
 * to only a single SCRIPT. The other deserialized content must be provided beforehand.
 */
public final class FeatureCollector {

  private FeatureSet scriptFeatures = FeatureSet.BARE_MINIMUM;

  public FeatureSet allFeatures() {
    return this.scriptFeatures;
  }

  public @Nullable FeatureContext visitSingleNode(@Nullable FeatureContext context, Node node) {
    if (context != null) {
      this.recordScriptFeatures(context, node);
    }
    return contextFor(context, node);
  }

  private void recordScriptFeatures(FeatureContext context, Node node) {
    switch (node.getToken()) {
      case FUNCTION -> {
        if (node.isAsyncGeneratorFunction()) {
          this.addScriptFeature(Feature.ASYNC_GENERATORS);
        }
        if (node.isArrowFunction()) {
          this.addScriptFeature(Feature.ARROW_FUNCTIONS);
        }
        if (node.isAsyncFunction()) {
          this.addScriptFeature(Feature.ASYNC_FUNCTIONS);
        }
        if (node.isGeneratorFunction()) {
          this.addScriptFeature(Feature.GENERATORS);
        }

        if (context.equals(FeatureContext.BLOCK_SCOPE)) {
          this.scriptFeatures = this.scriptFeatures.with(Feature.BLOCK_SCOPED_FUNCTION_DECLARATION);
        }
      }
      case PARAM_LIST, CALL, NEW -> {}
      case STRING_KEY -> {
        if (node.isShorthandProperty() && context.equals(FeatureContext.OBJECT_LITERAL)) {
          this.addScriptFeature(Feature.SHORTHAND_OBJECT_PROPERTIES);
        }
      }
      case DEFAULT_VALUE -> {
        if (context.equals(FeatureContext.PARAM_LIST)) {
          this.addScriptFeature(Feature.DEFAULT_PARAMETERS);
        }
      }
      case GETTER_DEF -> {
        this.addScriptFeature(Feature.GETTER);
        if (context.equals(FeatureContext.CLASS_MEMBERS)) {
          this.addScriptFeature(Feature.CLASS_GETTER_SETTER);
        }
      }
      case REGEXP -> this.addScriptFeature(Feature.REGEXP_SYNTAX);
      case SETTER_DEF -> {
        this.addScriptFeature(Feature.SETTER);
        if (context.equals(FeatureContext.CLASS_MEMBERS)) {
          this.addScriptFeature(Feature.CLASS_GETTER_SETTER);
        }
      }
      case BLOCK -> {
        if (context.equals(FeatureContext.CLASS_MEMBERS)) {
          this.addScriptFeature(Feature.CLASS_STATIC_BLOCK);
        }
      }
      case EMPTY -> {
        if (context.equals(FeatureContext.CATCH)) {
          this.addScriptFeature(Feature.OPTIONAL_CATCH_BINDING);
        }
      }
      case ITER_REST -> {
        if (context.equals(FeatureContext.PARAM_LIST)) {
          this.addScriptFeature(Feature.REST_PARAMETERS);
        } else {
          this.addScriptFeature(Feature.ARRAY_PATTERN_REST);
        }
      }
      case ITER_SPREAD -> this.addScriptFeature(Feature.SPREAD_EXPRESSIONS);
      case OBJECT_REST -> this.addScriptFeature(Feature.OBJECT_PATTERN_REST);
      case OBJECT_SPREAD -> this.addScriptFeature(Feature.OBJECT_LITERALS_WITH_SPREAD);
      case BIGINT -> this.addScriptFeature(Feature.BIGINT);
      case EXPONENT, ASSIGN_EXPONENT -> this.addScriptFeature(Feature.EXPONENT_OP);
      case TAGGED_TEMPLATELIT, TEMPLATELIT -> this.addScriptFeature(Feature.TEMPLATE_LITERALS);
      case NEW_TARGET -> this.addScriptFeature(Feature.NEW_TARGET);
      case COMPUTED_PROP -> {
        this.addScriptFeature(Feature.COMPUTED_PROPERTIES);
        boolean isGetter = node.getBooleanProp(Node.COMPUTED_PROP_GETTER);
        boolean isSetter = node.getBooleanProp(Node.COMPUTED_PROP_SETTER);
        boolean isClassMember = context.equals(FeatureContext.CLASS_MEMBERS);
        if (isGetter) {
          this.addScriptFeature(Feature.GETTER);
        } else if (isSetter) {
          this.addScriptFeature(Feature.SETTER);
        }
        if ((isGetter || isSetter) && isClassMember) {
          this.addScriptFeature(Feature.CLASS_GETTER_SETTER);
        }
      }
      case OPTCHAIN_GETPROP, OPTCHAIN_CALL, OPTCHAIN_GETELEM ->
          this.addScriptFeature(Feature.OPTIONAL_CHAINING);
      case COALESCE -> this.addScriptFeature(Feature.NULL_COALESCE_OP);
      case DYNAMIC_IMPORT -> this.addScriptFeature(Feature.DYNAMIC_IMPORT);
      case ASSIGN_OR, ASSIGN_AND -> this.addScriptFeature(Feature.LOGICAL_ASSIGNMENT);
      case ASSIGN_COALESCE -> {
        this.addScriptFeature(Feature.NULL_COALESCE_OP);
        this.addScriptFeature(Feature.LOGICAL_ASSIGNMENT);
      }
      case FOR_OF -> this.addScriptFeature(Feature.FOR_OF);
      case FOR_AWAIT_OF -> this.addScriptFeature(Feature.FOR_AWAIT_OF);
      case IMPORT, EXPORT -> this.addScriptFeature(Feature.MODULES);
      case CONST -> this.addScriptFeature(Feature.CONST_DECLARATIONS);
      case LET -> this.addScriptFeature(Feature.LET_DECLARATIONS);
      case CLASS -> this.addScriptFeature(Feature.CLASSES);
      case MEMBER_FUNCTION_DEF -> this.addScriptFeature(Feature.MEMBER_DECLARATIONS);
      case MEMBER_FIELD_DEF, COMPUTED_FIELD_DEF ->
          this.addScriptFeature(Feature.PUBLIC_CLASS_FIELDS);
      case SUPER -> this.addScriptFeature(Feature.SUPER);
      case ARRAY_PATTERN -> this.addScriptFeature(Feature.ARRAY_DESTRUCTURING);
      case OBJECT_PATTERN -> this.addScriptFeature(Feature.OBJECT_DESTRUCTURING);
      default -> {}
    }
  }

  private void addScriptFeature(Feature x) {
    this.scriptFeatures = this.scriptFeatures.with(x);
  }

  /**
   * Parent context of a node while deserializing, specifically for the purpose of tracking {@link
   * com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature}
   *
   * <p>This models only the direct parent of a node. e.g. nested nodes within a function body
   * should not have the FUNCTION context. Only direct children of the function would. The intent is
   * to have a way to model the parent Node of a node being visited before the AST is fully built,
   * as the parent pointer may not have been instantiated yet.
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

  private static @Nullable FeatureContext contextFor(
      @Nullable FeatureContext parentContext, Node node) {
    if (parentContext == null) {
      return null;
    }
    return switch (node.getToken()) {
      case PARAM_LIST -> FeatureContext.PARAM_LIST;
      case CLASS_MEMBERS -> FeatureContext.CLASS_MEMBERS;
      case CLASS -> FeatureContext.CLASS;
      case CATCH -> FeatureContext.CATCH;
      case BLOCK ->
          // a function body is not a block scope - BLOCK is just overloaded. all other references
          // to BLOCK are block scopes.
          parentContext.equals(FeatureContext.FUNCTION)
              ? FeatureContext.NONE
              : FeatureContext.BLOCK_SCOPE;
      case FUNCTION -> FeatureContext.FUNCTION;
      case OBJECTLIT -> FeatureContext.OBJECT_LITERAL;
      default -> FeatureContext.NONE;
    };
  }
}
