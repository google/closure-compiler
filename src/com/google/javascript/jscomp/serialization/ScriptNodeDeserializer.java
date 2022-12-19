/*
 * Copyright 2021 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.serialization;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.ExtensionRegistry;
import java.io.IOException;
import java.math.BigInteger;
import org.jspecify.nullness.Nullable;

/**
 * Class that deserializes an AstNode-tree representing a SCRIPT into a Node-tree.
 *
 * <p>This process depends on other information from the TypedAST format, but the output it limited
 * to only a single SCRIPT. The other deserialized content must be provided beforehand.
 */
@GwtIncompatible("protobuf.lite")
final class ScriptNodeDeserializer {

  private final SourceFile sourceFile;
  private final ByteString scriptBytes;
  private final String sourceMappingURL;
  private final Optional<ColorPool.ShardView> colorPoolShard;
  private final StringPool stringPool;
  private final ImmutableList<SourceFile> filePool;

  ScriptNodeDeserializer(
      LazyAst ast,
      StringPool stringPool,
      Optional<ColorPool.ShardView> colorPoolShard,
      ImmutableList<SourceFile> filePool) {
    this.scriptBytes = ast.getScript();
    this.sourceFile = filePool.get(ast.getSourceFile() - 1);
    this.sourceMappingURL = ast.getSourceMappingUrl();
    this.colorPoolShard = colorPoolShard;
    this.stringPool = stringPool;
    this.filePool = filePool;
  }

  public Node deserializeNew() {
    return new Runner().run();
  }

  private final class Runner {
    private FeatureSet scriptFeatures = FeatureSet.BARE_MINIMUM;
    private int previousLine = 0;
    private int previousColumn = 0;

    Node run() {
      try {
        CodedInputStream astStream = this.owner().scriptBytes.newCodedInput();
        astStream.setRecursionLimit(Integer.MAX_VALUE); // The real limit is stack space.

        Node scriptNode =
            this.visit(
                AstNode.parseFrom(astStream, ExtensionRegistry.getEmptyRegistry()),
                null,
                this.owner().createSourceInfoTemplate(this.owner().sourceFile));
        scriptNode.putProp(Node.FEATURE_SET, this.scriptFeatures);
        return scriptNode;
      } catch (IOException ex) {
        throw new MalformedTypedAstException(this.owner().sourceFile, ex);
      }
    }

    private ScriptNodeDeserializer owner() {
      return ScriptNodeDeserializer.this;
    }

    private Node visit(AstNode astNode, @Nullable Node parent, @Nullable Node sourceFileTemplate) {
      if (sourceFileTemplate == null || astNode.getSourceFile() != 0) {
        // 0 == 'not set'
        sourceFileTemplate =
            this.owner()
                .createSourceInfoTemplate(this.owner().filePool.get(astNode.getSourceFile() - 1));
      }

      int currentLine = this.previousLine + astNode.getRelativeLine();
      int currentColumn = this.previousColumn + astNode.getRelativeColumn();

      Node n = this.owner().deserializeSingleNode(astNode);
      n.setStaticSourceFileFrom(sourceFileTemplate);
      if (astNode.hasType() && this.owner().colorPoolShard.isPresent()) {
        n.setColor(this.owner().colorPoolShard.get().getColor(astNode.getType()));
      }
      long properties = astNode.getBooleanProperties();
      if (properties > 0) {
        n.deserializeProperties(filterOutCastProp(astNode.getBooleanProperties()));
      }
      n.setJSDocInfo(JSDocSerializer.deserializeJsdoc(astNode.getJsdoc(), stringPool));
      n.setLinenoCharno(currentLine, currentColumn);
      this.previousLine = currentLine;
      this.previousColumn = currentColumn;

      int children = astNode.getChildCount();
      for (int i = 0; i < children; i++) {
        AstNode child = astNode.getChild(i);
        Node deserializedChild = this.visit(child, n, sourceFileTemplate);
        n.addChildToBack(deserializedChild);
        // record script features here instead of while visiting child because some features are
        // context-dependent, and we need to know the parent and/or grandparent.
        this.recordScriptFeatures(parent, n, deserializedChild);
        this.owner().setOriginalNameIfPresent(child, deserializedChild);
      }

      return n;
    }

    private void recordScriptFeatures(Node grandparent, Node parent, Node node) {
      if (parent.isClass() && !node.isEmpty() && node.isSecondChildOf(parent)) {
        this.addScriptFeature(Feature.CLASS_EXTENDS);
      }

      switch (node.getToken()) {
        case FUNCTION:
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

          if (parent.isBlock() && !grandparent.isFunction()) {
            this.scriptFeatures =
                this.scriptFeatures.with(Feature.BLOCK_SCOPED_FUNCTION_DECLARATION);
          }
          return;

        case PARAM_LIST:
        case CALL:
        case NEW:
          if (node.hasTrailingComma()) {
            this.addScriptFeature(Feature.TRAILING_COMMA_IN_PARAM_LIST);
          }
          return;

        case STRING_KEY:
          if (node.isShorthandProperty()) {
            this.addScriptFeature(Feature.EXTENDED_OBJECT_LITERALS);
          }
          return;

        case DEFAULT_VALUE:
          if (parent.isParamList()) {
            this.addScriptFeature(Feature.DEFAULT_PARAMETERS);
          }
          return;

        case GETTER_DEF:
          this.addScriptFeature(Feature.GETTER);
          if (parent.isClassMembers()) {
            this.addScriptFeature(Feature.CLASS_GETTER_SETTER);
          }
          return;

        case SETTER_DEF:
          this.addScriptFeature(Feature.SETTER);
          if (parent.isClassMembers()) {
            this.addScriptFeature(Feature.CLASS_GETTER_SETTER);
          }
          return;

        case BLOCK:
          if (parent.isClassMembers()) {
            this.addScriptFeature(Feature.CLASS_STATIC_BLOCK);
          }
          return;

        case EMPTY:
          if (parent.isCatch()) {
            this.addScriptFeature(Feature.OPTIONAL_CATCH_BINDING);
          }
          return;
        case ITER_REST:
          this.addScriptFeature(Feature.ARRAY_PATTERN_REST);
          if (parent.isParamList()) {
            this.addScriptFeature(Feature.REST_PARAMETERS);
          }
          return;
        case ITER_SPREAD:
          this.addScriptFeature(Feature.SPREAD_EXPRESSIONS);
          return;
        case OBJECT_REST:
          this.addScriptFeature(Feature.OBJECT_PATTERN_REST);
          return;
        case OBJECT_SPREAD:
          this.addScriptFeature(Feature.OBJECT_LITERALS_WITH_SPREAD);
          return;
        case BIGINT:
          this.addScriptFeature(Feature.BIGINT);
          return;
        case EXPONENT:
        case ASSIGN_EXPONENT:
          this.addScriptFeature(Feature.EXPONENT_OP);
          return;
        case TAGGED_TEMPLATELIT:
        case TEMPLATELIT:
          this.addScriptFeature(Feature.TEMPLATE_LITERALS);
          return;
        case NEW_TARGET:
          this.addScriptFeature(Feature.NEW_TARGET);
          return;
        case COMPUTED_PROP:
          this.addScriptFeature(Feature.COMPUTED_PROPERTIES);
          return;
        case OPTCHAIN_GETPROP:
        case OPTCHAIN_CALL:
        case OPTCHAIN_GETELEM:
          this.addScriptFeature(Feature.OPTIONAL_CHAINING);
          return;
        case COALESCE:
          this.addScriptFeature(Feature.NULL_COALESCE_OP);
          return;
        case DYNAMIC_IMPORT:
          this.addScriptFeature(Feature.DYNAMIC_IMPORT);
          return;
        case ASSIGN_OR:
        case ASSIGN_AND:
          this.addScriptFeature(Feature.LOGICAL_ASSIGNMENT);
          return;
        case ASSIGN_COALESCE:
          this.addScriptFeature(Feature.NULL_COALESCE_OP);
          this.addScriptFeature(Feature.LOGICAL_ASSIGNMENT);
          return;

        case FOR_OF:
          this.addScriptFeature(Feature.FOR_OF);
          return;
        case FOR_AWAIT_OF:
          this.addScriptFeature(Feature.FOR_AWAIT_OF);
          return;

        case IMPORT:
        case EXPORT:
          this.addScriptFeature(Feature.MODULES);
          return;

        case CONST:
          this.addScriptFeature(Feature.CONST_DECLARATIONS);
          return;
        case LET:
          this.addScriptFeature(Feature.LET_DECLARATIONS);
          return;
        case CLASS:
          this.addScriptFeature(Feature.CLASSES);
          return;
        case CLASS_MEMBERS:
        case MEMBER_FUNCTION_DEF:
          this.addScriptFeature(Feature.MEMBER_DECLARATIONS);
          return;
        case MEMBER_FIELD_DEF:
        case COMPUTED_FIELD_DEF:
          this.addScriptFeature(Feature.PUBLIC_CLASS_FIELDS);
          return;
        case SUPER:
          this.addScriptFeature(Feature.SUPER);
          return;
        case ARRAY_PATTERN:
          this.addScriptFeature(Feature.ARRAY_DESTRUCTURING);
          return;
        case OBJECT_PATTERN:
          this.addScriptFeature(Feature.OBJECT_DESTRUCTURING);
          return;

        default:
          return;
      }
    }

    private void addScriptFeature(Feature x) {
      this.scriptFeatures = this.scriptFeatures.with(x);
    }
  }

  public String getSourceMappingURL() {
    return sourceMappingURL;
  }

  public SourceFile getSourceFile() {
    return sourceFile;
  }

  /**
   * Create a template node to use as a source of common attributes.
   *
   * <p>This allows the prop structure to be shared among all the node from this source file. This
   * reduces the cost of these properties to O(nodes) to O(files).
   */
  private Node createSourceInfoTemplate(SourceFile file) {
    // The Node type choice is arbitrary.
    Node sourceInfoTemplate = new Node(Token.SCRIPT);
    sourceInfoTemplate.setStaticSourceFile(file);
    return sourceInfoTemplate;
  }

  private void setOriginalNameIfPresent(AstNode astNode, Node n) {
    if (astNode.getOriginalNamePointer() != 0) {
      n.setOriginalName(this.stringPool.get(astNode.getOriginalNamePointer()));
    }
  }

  private String getString(AstNode n) {
    return this.stringPool.get(n.getStringValuePointer());
  }

  private Node deserializeSingleNode(AstNode n) {
    switch (n.getKind()) {
      case SOURCE_FILE:
        return new Node(Token.SCRIPT);

      case NUMBER_LITERAL:
        return IR.number(n.getDoubleValue());
      case STRING_LITERAL:
        return IR.string(getString(n));
      case IDENTIFIER:
        return IR.name(getString(n));
      case FALSE:
        return new Node(Token.FALSE);
      case TRUE:
        return new Node(Token.TRUE);
      case NULL:
        return new Node(Token.NULL);
      case THIS:
        return new Node(Token.THIS);
      case VOID:
        return new Node(Token.VOID);
      case BIGINT_LITERAL:
        return IR.bigint(new BigInteger(getString(n)));
      case REGEX_LITERAL:
        return new Node(Token.REGEXP);
      case ARRAY_LITERAL:
        return new Node(Token.ARRAYLIT);
      case OBJECT_LITERAL:
        return new Node(Token.OBJECTLIT);

      case ASSIGNMENT:
        return new Node(Token.ASSIGN);
      case CALL:
        return new Node(Token.CALL);
      case NEW:
        return new Node(Token.NEW);
      case PROPERTY_ACCESS:
        return Node.newString(Token.GETPROP, getString(n));
      case ELEMENT_ACCESS:
        return new Node(Token.GETELEM);

      case COMMA:
        return new Node(Token.COMMA);
      case BOOLEAN_OR:
        return new Node(Token.OR);
      case BOOLEAN_AND:
        return new Node(Token.AND);
      case HOOK:
        return new Node(Token.HOOK);
      case EQUAL:
        return new Node(Token.EQ);
      case NOT_EQUAL:
        return new Node(Token.NE);
      case LESS_THAN:
        return new Node(Token.LT);
      case LESS_THAN_EQUAL:
        return new Node(Token.LE);
      case GREATER_THAN:
        return new Node(Token.GT);
      case GREATER_THAN_EQUAL:
        return new Node(Token.GE);
      case TRIPLE_EQUAL:
        return new Node(Token.SHEQ);
      case NOT_TRIPLE_EQUAL:
        return new Node(Token.SHNE);
      case NOT:
        return new Node(Token.NOT);
      case POSITIVE:
        return new Node(Token.POS);
      case NEGATIVE:
        return new Node(Token.NEG);
      case TYPEOF:
        return new Node(Token.TYPEOF);
      case INSTANCEOF:
        return new Node(Token.INSTANCEOF);
      case IN:
        return new Node(Token.IN);

      case ADD:
        return new Node(Token.ADD);
      case SUBTRACT:
        return new Node(Token.SUB);
      case MULTIPLY:
        return new Node(Token.MUL);
      case DIVIDE:
        return new Node(Token.DIV);
      case MODULO:
        return new Node(Token.MOD);
      case EXPONENT:
        return new Node(Token.EXPONENT);
      case BITWISE_NOT:
        return new Node(Token.BITNOT);
      case BITWISE_OR:
        return new Node(Token.BITOR);
      case BITWISE_AND:
        return new Node(Token.BITAND);
      case BITWISE_XOR:
        return new Node(Token.BITXOR);
      case LEFT_SHIFT:
        return new Node(Token.LSH);
      case RIGHT_SHIFT:
        return new Node(Token.RSH);
      case UNSIGNED_RIGHT_SHIFT:
        return new Node(Token.URSH);
      case PRE_INCREMENT:
        return new Node(Token.INC);
      case POST_INCREMENT:
        Node postInc = new Node(Token.INC);
        postInc.putBooleanProp(Node.INCRDECR_PROP, true);
        return postInc;
      case PRE_DECREMENT:
        return new Node(Token.DEC);
      case POST_DECREMENT:
        Node postDec = new Node(Token.DEC);
        postDec.putBooleanProp(Node.INCRDECR_PROP, true);
        return postDec;

      case ASSIGN_ADD:
        return new Node(Token.ASSIGN_ADD);
      case ASSIGN_SUBTRACT:
        return new Node(Token.ASSIGN_SUB);
      case ASSIGN_MULTIPLY:
        return new Node(Token.ASSIGN_MUL);
      case ASSIGN_DIVIDE:
        return new Node(Token.ASSIGN_DIV);
      case ASSIGN_MODULO:
        return new Node(Token.ASSIGN_MOD);
      case ASSIGN_EXPONENT:
        return new Node(Token.ASSIGN_EXPONENT);
      case ASSIGN_BITWISE_OR:
        return new Node(Token.ASSIGN_BITOR);
      case ASSIGN_BITWISE_AND:
        return new Node(Token.ASSIGN_BITAND);
      case ASSIGN_BITWISE_XOR:
        return new Node(Token.ASSIGN_BITXOR);
      case ASSIGN_LEFT_SHIFT:
        return new Node(Token.ASSIGN_LSH);
      case ASSIGN_RIGHT_SHIFT:
        return new Node(Token.ASSIGN_RSH);
      case ASSIGN_UNSIGNED_RIGHT_SHIFT:
        return new Node(Token.ASSIGN_URSH);

      case YIELD:
        return new Node(Token.YIELD);
      case AWAIT:
        return new Node(Token.AWAIT);
      case DELETE:
        return new Node(Token.DELPROP);
      case TAGGED_TEMPLATELIT:
        return new Node(Token.TAGGED_TEMPLATELIT);
      case TEMPLATELIT:
        return new Node(Token.TEMPLATELIT);
      case TEMPLATELIT_SUB:
        return new Node(Token.TEMPLATELIT_SUB);
      case TEMPLATELIT_STRING:
        {
          TemplateStringValue templateStringValue = n.getTemplateStringValue();
          int cookedPointer = templateStringValue.getCookedStringPointer();
          String cookedString = cookedPointer == -1 ? null : this.stringPool.get(cookedPointer);
          String rawString = this.stringPool.get(templateStringValue.getRawStringPointer());
          return Node.newTemplateLitString(cookedString, rawString);
        }
      case NEW_TARGET:
        return new Node(Token.NEW_TARGET);
      case COMPUTED_PROP:
        return new Node(Token.COMPUTED_PROP);
      case IMPORT_META:
        return new Node(Token.IMPORT_META);
      case OPTCHAIN_PROPERTY_ACCESS:
        return Node.newString(Token.OPTCHAIN_GETPROP, getString(n));
      case OPTCHAIN_CALL:
        return new Node(Token.OPTCHAIN_CALL);
      case OPTCHAIN_ELEMENT_ACCESS:
        return new Node(Token.OPTCHAIN_GETELEM);
      case COALESCE:
        return new Node(Token.COALESCE);
      case DYNAMIC_IMPORT:
        return new Node(Token.DYNAMIC_IMPORT);

      case ASSIGN_OR:
        return new Node(Token.ASSIGN_OR);
      case ASSIGN_AND:
        return new Node(Token.ASSIGN_AND);
      case ASSIGN_COALESCE:
        return new Node(Token.ASSIGN_COALESCE);

      case EXPRESSION_STATEMENT:
        return new Node(Token.EXPR_RESULT);
      case BREAK_STATEMENT:
        return new Node(Token.BREAK);
      case CONTINUE_STATEMENT:
        return new Node(Token.CONTINUE);
      case DEBUGGER_STATEMENT:
        return new Node(Token.DEBUGGER);
      case DO_STATEMENT:
        return new Node(Token.DO);
      case FOR_STATEMENT:
        return new Node(Token.FOR);
      case FOR_IN_STATEMENT:
        return new Node(Token.FOR_IN);
      case FOR_OF_STATEMENT:
        return new Node(Token.FOR_OF);
      case FOR_AWAIT_OF_STATEMENT:
        return new Node(Token.FOR_AWAIT_OF);
      case IF_STATEMENT:
        return new Node(Token.IF);
      case RETURN_STATEMENT:
        return new Node(Token.RETURN);
      case SWITCH_STATEMENT:
        return new Node(Token.SWITCH);
      case THROW_STATEMENT:
        return new Node(Token.THROW);
      case TRY_STATEMENT:
        return new Node(Token.TRY);
      case WHILE_STATEMENT:
        return new Node(Token.WHILE);
      case EMPTY:
        return new Node(Token.EMPTY);
      case WITH:
        return new Node(Token.WITH);
      case IMPORT:
        return new Node(Token.IMPORT);
      case EXPORT:
        return new Node(Token.EXPORT);

      case VAR_DECLARATION:
        return new Node(Token.VAR);
      case CONST_DECLARATION:
        return new Node(Token.CONST);
      case LET_DECLARATION:
        return new Node(Token.LET);
      case FUNCTION_LITERAL:
        return new Node(Token.FUNCTION);
      case CLASS_LITERAL:
        return new Node(Token.CLASS);

      case BLOCK:
        return new Node(Token.BLOCK);
      case LABELED_STATEMENT:
        return new Node(Token.LABEL);
      case LABELED_NAME:
        return IR.labelName(getString(n));
      case CLASS_MEMBERS:
        return new Node(Token.CLASS_MEMBERS);
      case METHOD_DECLARATION:
        return Node.newString(Token.MEMBER_FUNCTION_DEF, getString(n));
      case FIELD_DECLARATION:
        return Node.newString(Token.MEMBER_FIELD_DEF, getString(n));
      case COMPUTED_PROP_FIELD:
        return new Node(Token.COMPUTED_FIELD_DEF);
      case PARAMETER_LIST:
        return new Node(Token.PARAM_LIST);
      case RENAMABLE_STRING_KEY:
        return IR.stringKey(getString(n));
      case QUOTED_STRING_KEY:
        Node quotedStringKey = IR.stringKey(getString(n));
        quotedStringKey.setQuotedStringKey();
        return quotedStringKey;
      case CASE:
        return new Node(Token.CASE);
      case DEFAULT_CASE:
        return new Node(Token.DEFAULT_CASE);
      case CATCH:
        return new Node(Token.CATCH);
      case SUPER:
        return new Node(Token.SUPER);
      case ARRAY_PATTERN:
        return new Node(Token.ARRAY_PATTERN);
      case OBJECT_PATTERN:
        return new Node(Token.OBJECT_PATTERN);
      case DESTRUCTURING_LHS:
        return new Node(Token.DESTRUCTURING_LHS);
      case DEFAULT_VALUE:
        return new Node(Token.DEFAULT_VALUE);

      case RENAMABLE_GETTER_DEF:
      case QUOTED_GETTER_DEF:
        Node getterDef = Node.newString(Token.GETTER_DEF, getString(n));
        if (n.getKind().equals(NodeKind.QUOTED_GETTER_DEF)) {
          getterDef.setQuotedStringKey();
        }
        return getterDef;
      case RENAMABLE_SETTER_DEF:
      case QUOTED_SETTER_DEF:
        Node setterDef = Node.newString(Token.SETTER_DEF, getString(n));
        if (n.getKind().equals(NodeKind.QUOTED_SETTER_DEF)) {
          setterDef.setQuotedStringKey();
        }
        return setterDef;

      case IMPORT_SPECS:
        return new Node(Token.IMPORT_SPECS);
      case IMPORT_SPEC:
        return new Node(Token.IMPORT_SPEC);
      case IMPORT_STAR:
        return Node.newString(Token.IMPORT_STAR, getString(n));
      case EXPORT_SPECS:
        return new Node(Token.EXPORT_SPECS);
      case EXPORT_SPEC:
        return new Node(Token.EXPORT_SPEC);
      case MODULE_BODY:
        return new Node(Token.MODULE_BODY);
      case ITER_REST:
        return new Node(Token.ITER_REST);
      case ITER_SPREAD:
        return new Node(Token.ITER_SPREAD);
      case OBJECT_REST:
        return new Node(Token.OBJECT_REST);
      case OBJECT_SPREAD:
        return new Node(Token.OBJECT_SPREAD);

      case NODE_KIND_UNSPECIFIED:
      case UNRECOGNIZED:
        break;
    }
    throw new IllegalStateException("Unexpected serialized kind for AstNode: " + n);
  }

  /**
   * If no colors are being deserialized, filters out any NodeProperty.COLOR_BEFORE_CASTs
   *
   * <p>This is because it doesn't make sense to have that property present on nodes that don't have
   * colors.
   */
  private long filterOutCastProp(long nodeProperties) {
    if (colorPoolShard.isPresent()) {
      return nodeProperties; // we are deserializing colors, so this is fine.
    }
    return nodeProperties & ~(1L << NodeProperty.COLOR_FROM_CAST.getNumber());
  }
}
