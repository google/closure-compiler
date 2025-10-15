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
import org.jspecify.annotations.Nullable;

/**
 * Class that deserializes an AstNode-tree representing a SCRIPT into a Node-tree.
 *
 * <p>This process depends on other information from the TypedAST format, but the output it limited
 * to only a single SCRIPT. The other deserialized content must be provided beforehand.
 */
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
                FeatureContext.NONE,
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

    private Node visit(
        AstNode astNode, @Nullable FeatureContext context, @Nullable Node sourceFileTemplate) {
      if (sourceFileTemplate == null || astNode.getSourceFile() != 0) {
        // 0 == 'not set'
        sourceFileTemplate =
            this.owner()
                .createSourceInfoTemplate(this.owner().filePool.get(astNode.getSourceFile() - 1));
      }

      int currentLine = this.previousLine + astNode.getRelativeLine();
      int currentColumn = this.previousColumn + astNode.getRelativeColumn();

      Node n = this.owner().deserializeSingleNode(astNode, sourceFileTemplate);
      if (astNode.hasType() && this.owner().colorPoolShard.isPresent()) {
        n.setColor(this.owner().colorPoolShard.get().getColor(astNode.getType()));
      }
      long properties = astNode.getBooleanProperties();
      if (properties > 0) {
        n.deserializeProperties(
            filterOutCastProp(properties), sourceFileTemplate.getIsInClosureUnawareSubtree());
      }
      n.setJSDocInfo(JSDocSerializer.deserializeJsdoc(astNode.getJsdoc(), stringPool));
      n.setLinenoCharno(currentLine, currentColumn);
      this.previousLine = currentLine;
      this.previousColumn = currentColumn;
      if (context != null) {
        this.recordScriptFeatures(context, n);
      }

      @Nullable FeatureContext newContext = contextFor(context, n);
      if (Node.hasBitSet(properties, NodeProperty.CLOSURE_UNAWARE_SHADOW.getNumber())) {
        AstNode serializedShadowChild = astNode.getChild(0);
        Node closureUnawareSubtreeTemplateNode = sourceFileTemplate.cloneTree();
        closureUnawareSubtreeTemplateNode.setIsInClosureUnawareSubtree(true);
        // Unlike normal deserialization, we want to avoid recording features for code within the
        // shadow because we don't run transpilation passes over it and later stages of the compiler
        // attempt to validate that transpilation has successfully run over the entire AST and no
        // features remain that won't work in the given language output level.
        Node shadowedCode =
            this.visit(serializedShadowChild, null, closureUnawareSubtreeTemplateNode);
        this.owner().setOriginalNameIfPresent(serializedShadowChild, shadowedCode);
        // The shadowed code is only the "source" parts of the shadow structure, and does not
        // include the synthetic code that is needed for the compiler to consider it a valid
        // standalone AST. We recreate that here.
        // This must be kept in sync with the shadow structure created by TypedAstSerializer.
        Node shadowRoot = IR.root(IR.script(IR.exprResult(shadowedCode)));
        shadowRoot.clonePropsFrom(closureUnawareSubtreeTemplateNode);
        shadowRoot.getFirstChild().clonePropsFrom(closureUnawareSubtreeTemplateNode);
        shadowRoot.getFirstFirstChild().clonePropsFrom(closureUnawareSubtreeTemplateNode);

        n.setClosureUnawareShadow(shadowRoot);
        return n;
      }
      int children = astNode.getChildCount();

      for (int i = 0; i < children; i++) {
        AstNode child = astNode.getChild(i);
        Node deserializedChild = this.visit(child, newContext, sourceFileTemplate);
        n.addChildToBack(deserializedChild);
        this.owner().setOriginalNameIfPresent(child, deserializedChild);
      }

      return n;
    }

    private void recordScriptFeatures(FeatureContext context, Node node) {
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

          if (context.equals(FeatureContext.BLOCK_SCOPE)) {
            this.scriptFeatures =
                this.scriptFeatures.with(Feature.BLOCK_SCOPED_FUNCTION_DECLARATION);
          }
          return;

        case PARAM_LIST:
        case CALL:
        case NEW:
          return;

        case STRING_KEY:
          if (node.isShorthandProperty() && context.equals(FeatureContext.OBJECT_LITERAL)) {
            this.addScriptFeature(Feature.SHORTHAND_OBJECT_PROPERTIES);
          }
          return;

        case DEFAULT_VALUE:
          if (context.equals(FeatureContext.PARAM_LIST)) {
            this.addScriptFeature(Feature.DEFAULT_PARAMETERS);
          }
          return;

        case GETTER_DEF:
          this.addScriptFeature(Feature.GETTER);
          if (context.equals(FeatureContext.CLASS_MEMBERS)) {
            this.addScriptFeature(Feature.CLASS_GETTER_SETTER);
          }
          return;

        case REGEXP:
          this.addScriptFeature(Feature.REGEXP_SYNTAX);
          return;

        case SETTER_DEF:
          this.addScriptFeature(Feature.SETTER);
          if (context.equals(FeatureContext.CLASS_MEMBERS)) {
            this.addScriptFeature(Feature.CLASS_GETTER_SETTER);
          }
          return;

        case BLOCK:
          if (context.equals(FeatureContext.CLASS_MEMBERS)) {
            this.addScriptFeature(Feature.CLASS_STATIC_BLOCK);
          }
          return;

        case EMPTY:
          if (context.equals(FeatureContext.CATCH)) {
            this.addScriptFeature(Feature.OPTIONAL_CATCH_BINDING);
          }
          return;
        case ITER_REST:
          if (context.equals(FeatureContext.PARAM_LIST)) {
            this.addScriptFeature(Feature.REST_PARAMETERS);
          } else {
            this.addScriptFeature(Feature.ARRAY_PATTERN_REST);
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
      n.setOriginalNameFromStringPool(
          this.stringPool.getInternedStrings(), astNode.getOriginalNamePointer());
    }
  }

  /**
   * Creates a new string node with the given token & string value of the AstNode
   *
   * <p>Prefer calling this method over calling a regular Node.* or IR.* method when possible. This
   * method integrates with {@link RhinoStringPool} to cache String interning results.
   */
  private Node stringNode(Token token, AstNode n, Node templateNode) {
    Node str =
        Node.newString(token, this.stringPool.getInternedStrings(), n.getStringValuePointer());
    str.clonePropsFrom(templateNode);
    return str;
  }

  private Node newNodeFromTemplate(Token token, Node templateNode) {
    Node n = templateNode.cloneTree();
    n.setToken(token);
    return n;
  }

  private Node deserializeSingleNode(AstNode n, Node templateNode) {
    switch (n.getKind()) {
      case SOURCE_FILE:
        return newNodeFromTemplate(Token.SCRIPT, templateNode);

      case NUMBER_LITERAL:
        Node numberNode = IR.number(n.getDoubleValue());
        numberNode.clonePropsFrom(templateNode);
        return numberNode;
      case STRING_LITERAL:
        return stringNode(Token.STRINGLIT, n, templateNode);
      case IDENTIFIER:
        return stringNode(Token.NAME, n, templateNode);
      case FALSE:
        return newNodeFromTemplate(Token.FALSE, templateNode);
      case TRUE:
        return newNodeFromTemplate(Token.TRUE, templateNode);
      case NULL:
        return newNodeFromTemplate(Token.NULL, templateNode);
      case THIS:
        return newNodeFromTemplate(Token.THIS, templateNode);
      case VOID:
        return newNodeFromTemplate(Token.VOID, templateNode);
      case BIGINT_LITERAL:
        String bigintString = this.stringPool.get(n.getStringValuePointer());
        Node bi = IR.bigint(new BigInteger(bigintString));
        bi.clonePropsFrom(templateNode);
        return bi;
      case REGEX_LITERAL:
        return newNodeFromTemplate(Token.REGEXP, templateNode);
      case ARRAY_LITERAL:
        return newNodeFromTemplate(Token.ARRAYLIT, templateNode);
      case OBJECT_LITERAL:
        return newNodeFromTemplate(Token.OBJECTLIT, templateNode);

      case ASSIGNMENT:
        return newNodeFromTemplate(Token.ASSIGN, templateNode);
      case CALL:
        return newNodeFromTemplate(Token.CALL, templateNode);
      case NEW:
        return newNodeFromTemplate(Token.NEW, templateNode);
      case PROPERTY_ACCESS:
        return stringNode(Token.GETPROP, n, templateNode);
      case ELEMENT_ACCESS:
        return newNodeFromTemplate(Token.GETELEM, templateNode);

      case COMMA:
        return newNodeFromTemplate(Token.COMMA, templateNode);
      case BOOLEAN_OR:
        return newNodeFromTemplate(Token.OR, templateNode);
      case BOOLEAN_AND:
        return newNodeFromTemplate(Token.AND, templateNode);
      case HOOK:
        return newNodeFromTemplate(Token.HOOK, templateNode);
      case EQUAL:
        return newNodeFromTemplate(Token.EQ, templateNode);
      case NOT_EQUAL:
        return newNodeFromTemplate(Token.NE, templateNode);
      case LESS_THAN:
        return newNodeFromTemplate(Token.LT, templateNode);
      case LESS_THAN_EQUAL:
        return newNodeFromTemplate(Token.LE, templateNode);
      case GREATER_THAN:
        return newNodeFromTemplate(Token.GT, templateNode);
      case GREATER_THAN_EQUAL:
        return newNodeFromTemplate(Token.GE, templateNode);
      case TRIPLE_EQUAL:
        return newNodeFromTemplate(Token.SHEQ, templateNode);
      case NOT_TRIPLE_EQUAL:
        return newNodeFromTemplate(Token.SHNE, templateNode);
      case NOT:
        return newNodeFromTemplate(Token.NOT, templateNode);
      case POSITIVE:
        return newNodeFromTemplate(Token.POS, templateNode);
      case NEGATIVE:
        return newNodeFromTemplate(Token.NEG, templateNode);
      case TYPEOF:
        return newNodeFromTemplate(Token.TYPEOF, templateNode);
      case INSTANCEOF:
        return newNodeFromTemplate(Token.INSTANCEOF, templateNode);
      case IN:
        return newNodeFromTemplate(Token.IN, templateNode);

      case ADD:
        return newNodeFromTemplate(Token.ADD, templateNode);
      case SUBTRACT:
        return newNodeFromTemplate(Token.SUB, templateNode);
      case MULTIPLY:
        return newNodeFromTemplate(Token.MUL, templateNode);
      case DIVIDE:
        return newNodeFromTemplate(Token.DIV, templateNode);
      case MODULO:
        return newNodeFromTemplate(Token.MOD, templateNode);
      case EXPONENT:
        return newNodeFromTemplate(Token.EXPONENT, templateNode);
      case BITWISE_NOT:
        return newNodeFromTemplate(Token.BITNOT, templateNode);
      case BITWISE_OR:
        return newNodeFromTemplate(Token.BITOR, templateNode);
      case BITWISE_AND:
        return newNodeFromTemplate(Token.BITAND, templateNode);
      case BITWISE_XOR:
        return newNodeFromTemplate(Token.BITXOR, templateNode);
      case LEFT_SHIFT:
        return newNodeFromTemplate(Token.LSH, templateNode);
      case RIGHT_SHIFT:
        return newNodeFromTemplate(Token.RSH, templateNode);
      case UNSIGNED_RIGHT_SHIFT:
        return newNodeFromTemplate(Token.URSH, templateNode);
      case PRE_INCREMENT:
        return newNodeFromTemplate(Token.INC, templateNode);
      case POST_INCREMENT:
        Node postInc = newNodeFromTemplate(Token.INC, templateNode);
        postInc.putBooleanProp(Node.INCRDECR_PROP, true);
        return postInc;
      case PRE_DECREMENT:
        return newNodeFromTemplate(Token.DEC, templateNode);
      case POST_DECREMENT:
        Node postDec = newNodeFromTemplate(Token.DEC, templateNode);
        postDec.putBooleanProp(Node.INCRDECR_PROP, true);
        return postDec;

      case ASSIGN_ADD:
        return newNodeFromTemplate(Token.ASSIGN_ADD, templateNode);
      case ASSIGN_SUBTRACT:
        return newNodeFromTemplate(Token.ASSIGN_SUB, templateNode);
      case ASSIGN_MULTIPLY:
        return newNodeFromTemplate(Token.ASSIGN_MUL, templateNode);
      case ASSIGN_DIVIDE:
        return newNodeFromTemplate(Token.ASSIGN_DIV, templateNode);
      case ASSIGN_MODULO:
        return newNodeFromTemplate(Token.ASSIGN_MOD, templateNode);
      case ASSIGN_EXPONENT:
        return newNodeFromTemplate(Token.ASSIGN_EXPONENT, templateNode);
      case ASSIGN_BITWISE_OR:
        return newNodeFromTemplate(Token.ASSIGN_BITOR, templateNode);
      case ASSIGN_BITWISE_AND:
        return newNodeFromTemplate(Token.ASSIGN_BITAND, templateNode);
      case ASSIGN_BITWISE_XOR:
        return newNodeFromTemplate(Token.ASSIGN_BITXOR, templateNode);
      case ASSIGN_LEFT_SHIFT:
        return newNodeFromTemplate(Token.ASSIGN_LSH, templateNode);
      case ASSIGN_RIGHT_SHIFT:
        return newNodeFromTemplate(Token.ASSIGN_RSH, templateNode);
      case ASSIGN_UNSIGNED_RIGHT_SHIFT:
        return newNodeFromTemplate(Token.ASSIGN_URSH, templateNode);

      case YIELD:
        return newNodeFromTemplate(Token.YIELD, templateNode);
      case AWAIT:
        return newNodeFromTemplate(Token.AWAIT, templateNode);
      case DELETE:
        return newNodeFromTemplate(Token.DELPROP, templateNode);
      case TAGGED_TEMPLATELIT:
        return newNodeFromTemplate(Token.TAGGED_TEMPLATELIT, templateNode);
      case TEMPLATELIT:
        return newNodeFromTemplate(Token.TEMPLATELIT, templateNode);
      case TEMPLATELIT_SUB:
        return newNodeFromTemplate(Token.TEMPLATELIT_SUB, templateNode);
      case TEMPLATELIT_STRING:
        {
          TemplateStringValue templateStringValue = n.getTemplateStringValue();
          Node templateLitString =
              Node.newTemplateLitString(
                  this.stringPool.getInternedStrings(),
                  templateStringValue.getCookedStringPointer(),
                  templateStringValue.getRawStringPointer());
          templateLitString.clonePropsFrom(templateNode);
          return templateLitString;
        }
      case NEW_TARGET:
        return newNodeFromTemplate(Token.NEW_TARGET, templateNode);
      case COMPUTED_PROP:
        return newNodeFromTemplate(Token.COMPUTED_PROP, templateNode);
      case IMPORT_META:
        return newNodeFromTemplate(Token.IMPORT_META, templateNode);
      case OPTCHAIN_PROPERTY_ACCESS:
        return stringNode(Token.OPTCHAIN_GETPROP, n, templateNode);
      case OPTCHAIN_CALL:
        return newNodeFromTemplate(Token.OPTCHAIN_CALL, templateNode);
      case OPTCHAIN_ELEMENT_ACCESS:
        return newNodeFromTemplate(Token.OPTCHAIN_GETELEM, templateNode);
      case COALESCE:
        return newNodeFromTemplate(Token.COALESCE, templateNode);
      case DYNAMIC_IMPORT:
        return newNodeFromTemplate(Token.DYNAMIC_IMPORT, templateNode);

      case ASSIGN_OR:
        return newNodeFromTemplate(Token.ASSIGN_OR, templateNode);
      case ASSIGN_AND:
        return newNodeFromTemplate(Token.ASSIGN_AND, templateNode);
      case ASSIGN_COALESCE:
        return newNodeFromTemplate(Token.ASSIGN_COALESCE, templateNode);

      case EXPRESSION_STATEMENT:
        return newNodeFromTemplate(Token.EXPR_RESULT, templateNode);
      case BREAK_STATEMENT:
        return newNodeFromTemplate(Token.BREAK, templateNode);
      case CONTINUE_STATEMENT:
        return newNodeFromTemplate(Token.CONTINUE, templateNode);
      case DEBUGGER_STATEMENT:
        return newNodeFromTemplate(Token.DEBUGGER, templateNode);
      case DO_STATEMENT:
        return newNodeFromTemplate(Token.DO, templateNode);
      case FOR_STATEMENT:
        return newNodeFromTemplate(Token.FOR, templateNode);
      case FOR_IN_STATEMENT:
        return newNodeFromTemplate(Token.FOR_IN, templateNode);
      case FOR_OF_STATEMENT:
        return newNodeFromTemplate(Token.FOR_OF, templateNode);
      case FOR_AWAIT_OF_STATEMENT:
        return newNodeFromTemplate(Token.FOR_AWAIT_OF, templateNode);
      case IF_STATEMENT:
        return newNodeFromTemplate(Token.IF, templateNode);
      case RETURN_STATEMENT:
        return newNodeFromTemplate(Token.RETURN, templateNode);
      case SWITCH_STATEMENT:
        return newNodeFromTemplate(Token.SWITCH, templateNode);
      case SWITCH_BODY:
        return newNodeFromTemplate(Token.SWITCH_BODY, templateNode);
      case THROW_STATEMENT:
        return newNodeFromTemplate(Token.THROW, templateNode);
      case TRY_STATEMENT:
        return newNodeFromTemplate(Token.TRY, templateNode);
      case WHILE_STATEMENT:
        return newNodeFromTemplate(Token.WHILE, templateNode);
      case EMPTY:
        return newNodeFromTemplate(Token.EMPTY, templateNode);
      case WITH:
        return newNodeFromTemplate(Token.WITH, templateNode);
      case IMPORT:
        return newNodeFromTemplate(Token.IMPORT, templateNode);
      case EXPORT:
        return newNodeFromTemplate(Token.EXPORT, templateNode);

      case VAR_DECLARATION:
        return newNodeFromTemplate(Token.VAR, templateNode);
      case CONST_DECLARATION:
        return newNodeFromTemplate(Token.CONST, templateNode);
      case LET_DECLARATION:
        return newNodeFromTemplate(Token.LET, templateNode);
      case FUNCTION_LITERAL:
        return newNodeFromTemplate(Token.FUNCTION, templateNode);
      case CLASS_LITERAL:
        return newNodeFromTemplate(Token.CLASS, templateNode);

      case BLOCK:
        return newNodeFromTemplate(Token.BLOCK, templateNode);
      case LABELED_STATEMENT:
        return newNodeFromTemplate(Token.LABEL, templateNode);
      case LABELED_NAME:
        return stringNode(Token.LABEL_NAME, n, templateNode);
      case CLASS_MEMBERS:
        return newNodeFromTemplate(Token.CLASS_MEMBERS, templateNode);
      case METHOD_DECLARATION:
        return stringNode(Token.MEMBER_FUNCTION_DEF, n, templateNode);
      case FIELD_DECLARATION:
        return stringNode(Token.MEMBER_FIELD_DEF, n, templateNode);
      case COMPUTED_PROP_FIELD:
        return newNodeFromTemplate(Token.COMPUTED_FIELD_DEF, templateNode);
      case PARAMETER_LIST:
        return newNodeFromTemplate(Token.PARAM_LIST, templateNode);
      case RENAMABLE_STRING_KEY:
        return stringNode(Token.STRING_KEY, n, templateNode);
      case QUOTED_STRING_KEY:
        Node quotedStringKey = stringNode(Token.STRING_KEY, n, templateNode);
        quotedStringKey.setQuotedStringKey();
        return quotedStringKey;
      case CASE:
        return newNodeFromTemplate(Token.CASE, templateNode);
      case DEFAULT_CASE:
        return newNodeFromTemplate(Token.DEFAULT_CASE, templateNode);
      case CATCH:
        return newNodeFromTemplate(Token.CATCH, templateNode);
      case SUPER:
        return newNodeFromTemplate(Token.SUPER, templateNode);
      case ARRAY_PATTERN:
        return newNodeFromTemplate(Token.ARRAY_PATTERN, templateNode);
      case OBJECT_PATTERN:
        return newNodeFromTemplate(Token.OBJECT_PATTERN, templateNode);
      case DESTRUCTURING_LHS:
        return newNodeFromTemplate(Token.DESTRUCTURING_LHS, templateNode);
      case DEFAULT_VALUE:
        return newNodeFromTemplate(Token.DEFAULT_VALUE, templateNode);

      case RENAMABLE_GETTER_DEF:
      case QUOTED_GETTER_DEF:
        Node getterDef = stringNode(Token.GETTER_DEF, n, templateNode);
        if (n.getKind().equals(NodeKind.QUOTED_GETTER_DEF)) {
          getterDef.setQuotedStringKey();
        }
        return getterDef;
      case RENAMABLE_SETTER_DEF:
      case QUOTED_SETTER_DEF:
        Node setterDef = stringNode(Token.SETTER_DEF, n, templateNode);
        if (n.getKind().equals(NodeKind.QUOTED_SETTER_DEF)) {
          setterDef.setQuotedStringKey();
        }
        return setterDef;

      case IMPORT_SPECS:
        return newNodeFromTemplate(Token.IMPORT_SPECS, templateNode);
      case IMPORT_SPEC:
        return newNodeFromTemplate(Token.IMPORT_SPEC, templateNode);
      case IMPORT_STAR:
        return stringNode(Token.IMPORT_STAR, n, templateNode);
      case EXPORT_SPECS:
        return newNodeFromTemplate(Token.EXPORT_SPECS, templateNode);
      case EXPORT_SPEC:
        return newNodeFromTemplate(Token.EXPORT_SPEC, templateNode);
      case MODULE_BODY:
        return newNodeFromTemplate(Token.MODULE_BODY, templateNode);
      case ITER_REST:
        return newNodeFromTemplate(Token.ITER_REST, templateNode);
      case ITER_SPREAD:
        return newNodeFromTemplate(Token.ITER_SPREAD, templateNode);
      case OBJECT_REST:
        return newNodeFromTemplate(Token.OBJECT_REST, templateNode);
      case OBJECT_SPREAD:
        return newNodeFromTemplate(Token.OBJECT_SPREAD, templateNode);

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

  /**
   * Parent context of a node while deserializing, specifically for the purpose of tracking {@link
   * com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature}
   *
   * <p>This models only the direct parent of a node. e.g. nested nodes within a function body
   * should not have the FUNCTION context. Only direct children of the function would. The intent is
   * to have a way to model the parent Node of a node being visited before the AST is fully built,
   * as the parent pointer may not have been instantiated yet.
   */
  private enum FeatureContext {
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
    switch (node.getToken()) {
      case PARAM_LIST:
        return FeatureContext.PARAM_LIST;
      case CLASS_MEMBERS:
        return FeatureContext.CLASS_MEMBERS;
      case CLASS:
        return FeatureContext.CLASS;
      case CATCH:
        return FeatureContext.CATCH;
      case BLOCK:
        // a function body is not a block scope - BLOCK is just overloaded. all other references to
        // BLOCK are block scopes.
        if (parentContext.equals(FeatureContext.FUNCTION)) {
          return FeatureContext.NONE;
        }
        return FeatureContext.BLOCK_SCOPE;
      case FUNCTION:
        return FeatureContext.FUNCTION;
      case OBJECTLIT:
        return FeatureContext.OBJECT_LITERAL;
      default:
        return FeatureContext.NONE;
    }
  }
}
