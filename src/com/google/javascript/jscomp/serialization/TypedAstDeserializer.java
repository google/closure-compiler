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

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleSourceFile;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import com.google.javascript.rhino.Token;
import java.math.BigInteger;
import java.util.List;

/**
 * Class that deserializes a TypedAst proto into the JSCompiler AST structure.
 *
 * <p>NOTE: This is a work in progress, and incomplete. In particular, optimization types/colors are
 * currently not yet implemented and are dropped during deserialization.
 */
final class TypedAstDeserializer {

  private final TypedAst typedAst;
  private FeatureSet currentFileFeatures = null;
  private int previousLine;
  private int previousColumn;

  private TypedAstDeserializer(TypedAst typedAst) {
    this.typedAst = typedAst;
  }

  /** Transforms a given TypedAst object into a compiler AST (represented as a IR.root node) */
  static Node deserialize(TypedAst typedAst) {
    return new TypedAstDeserializer(typedAst).deserializeToScriptNodes();
  }

  private Node deserializeToScriptNodes() {
    Node externRoot = IR.root();
    Node codeRoot = IR.root();
    for (JavascriptFile file : typedAst.getExternFileList()) {
      externRoot.addChildToBack(deserializeScriptNode(file));
    }
    for (JavascriptFile file : typedAst.getSourceFileList()) {
      codeRoot.addChildToBack(deserializeScriptNode(file));
    }
    return IR.root(externRoot, codeRoot);
  }

  private Node deserializeScriptNode(JavascriptFile file) {
    currentFileFeatures = FeatureSet.BARE_MINIMUM;
    previousLine = previousColumn = 0;
    Node scriptNode = visit(file.getRoot());
    SimpleSourceFile sourceFile = new SimpleSourceFile(file.getFilename(), SourceKind.STRONG);
    scriptNode.setStaticSourceFile(sourceFile);
    scriptNode.setInputId(new InputId(sourceFile.getName()));
    scriptNode.putProp(Node.FEATURE_SET, currentFileFeatures);
    currentFileFeatures = null;
    return scriptNode;
  }

  private Node visit(AstNode astNode) {
    int currentLine = previousLine + astNode.getRelativeLine();
    int currentColumn = previousColumn + astNode.getRelativeColumn();
    Node n = deserializeSingleNode(astNode);
    deserializeProperties(n, astNode);
    n.setJSDocInfo(JsdocSerializer.deserializeJsdoc(astNode.getJsdoc()));
    n.setLineno(currentLine);
    n.setCharno(currentColumn);
    previousLine = currentLine;
    previousColumn = currentColumn;
    for (AstNode child : astNode.getChildList()) {
      n.addChildToBack(visit(child));
    }
    return n;
  }

  private String getStringByPointer(int pointer) {
    List<String> stringPool = typedAst.getStringPool().getStringsList();
    checkState(
        stringPool.size() > pointer,
        "Found pointer <%s> that points outside of string pool. Pool contents:\n%s",
        pointer,
        stringPool);
    return stringPool.get(pointer);
  }

  private String getString(AstNode n) {
    int pointer = n.getStringValuePointer();
    return getStringByPointer(pointer);
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
        currentFileFeatures = currentFileFeatures.with(Feature.BIGINT);
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
        switch (n.getValueCase()) {
          case STRING_VALUE_POINTER:
            return Node.newString(Token.GETPROP, getString(n));
          default:
            return new Node(Token.GETPROP);
        }
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
        currentFileFeatures = currentFileFeatures.with(Feature.EXPONENT_OP);
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
        currentFileFeatures = currentFileFeatures.with(Feature.TEMPLATE_LITERALS);
        return new Node(Token.TAGGED_TEMPLATELIT);
      case TEMPLATELIT:
        currentFileFeatures = currentFileFeatures.with(Feature.TEMPLATE_LITERALS);
        return new Node(Token.TEMPLATELIT);
      case TEMPLATELIT_SUB:
        return new Node(Token.TEMPLATELIT_SUB);
      case TEMPLATELIT_STRING:
        TemplateStringValue templateStringValue = n.getTemplateStringValue();
        String rawString = getStringByPointer(templateStringValue.getRawStringPointer());
        String cookedString = getStringByPointer(templateStringValue.getCookedStringPointer());
        return Node.newTemplateLitString(cookedString, rawString);
      case NEW_TARGET:
        currentFileFeatures = currentFileFeatures.with(Feature.NEW_TARGET);
        return new Node(Token.NEW_TARGET);
      case COMPUTED_PROP:
        currentFileFeatures = currentFileFeatures.with(Feature.COMPUTED_PROPERTIES);
        return new Node(Token.COMPUTED_PROP);
      case IMPORT_META:
        return new Node(Token.IMPORT_META);
      case OPTCHAIN_PROPERTY_ACCESS:
        currentFileFeatures = currentFileFeatures.with(Feature.OPTIONAL_CHAINING);
        return n.hasStringValuePointer()
            ? Node.newString(Token.OPTCHAIN_GETPROP, getString(n))
            : new Node(Token.OPTCHAIN_GETPROP);
      case OPTCHAIN_CALL:
        currentFileFeatures = currentFileFeatures.with(Feature.OPTIONAL_CHAINING);
        return new Node(Token.OPTCHAIN_CALL);
      case OPTCHAIN_ELEMENT_ACCESS:
        currentFileFeatures = currentFileFeatures.with(Feature.OPTIONAL_CHAINING);
        return new Node(Token.OPTCHAIN_GETELEM);
      case COALESCE:
        currentFileFeatures = currentFileFeatures.with(Feature.NULL_COALESCE_OP);
        return new Node(Token.COALESCE);
      case DYNAMIC_IMPORT:
        currentFileFeatures = currentFileFeatures.with(Feature.DYNAMIC_IMPORT);
        return new Node(Token.DYNAMIC_IMPORT);

      case CAST:
        return new Node(Token.CAST);

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
        currentFileFeatures = currentFileFeatures.with(Feature.FOR_OF);
        return new Node(Token.FOR_OF);
      case FOR_AWAIT_OF_STATEMENT:
        currentFileFeatures = currentFileFeatures.with(Feature.FOR_AWAIT_OF);
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
        currentFileFeatures = currentFileFeatures.with(Feature.MODULES);
        return new Node(Token.IMPORT);
      case EXPORT:
        currentFileFeatures = currentFileFeatures.with(Feature.MODULES);
        return new Node(Token.EXPORT);

      case VAR_DECLARATION:
        return new Node(Token.VAR);
      case CONST_DECLARATION:
        currentFileFeatures = currentFileFeatures.with(Feature.CONST_DECLARATIONS);
        return new Node(Token.CONST);
      case LET_DECLARATION:
        currentFileFeatures = currentFileFeatures.with(Feature.LET_DECLARATIONS);
        return new Node(Token.LET);
      case FUNCTION_LITERAL:
        return new Node(Token.FUNCTION);
      case CLASS_LITERAL:
        currentFileFeatures = currentFileFeatures.with(Feature.CLASSES);
        return new Node(Token.CLASS);

      case BLOCK:
        return new Node(Token.BLOCK);
      case LABELED_STATEMENT:
        return new Node(Token.LABEL);
      case LABELED_NAME:
        return IR.labelName(getString(n));
      case CLASS_MEMBERS:
        currentFileFeatures = currentFileFeatures.with(Feature.MEMBER_DECLARATIONS);
        return new Node(Token.CLASS_MEMBERS);
      case METHOD_DECLARATION:
        currentFileFeatures = currentFileFeatures.with(Feature.MEMBER_DECLARATIONS);
        return Node.newString(Token.MEMBER_FUNCTION_DEF, getString(n));
      case PARAMETER_LIST:
        return new Node(Token.PARAM_LIST);
      case RENAMABLE_STRING_KEY:
        return IR.stringKey(getString(n));
      case QUOTED_STRING_KEY:
        Node quotedStringKey = IR.stringKey(getString(n));
        quotedStringKey.setQuotedString();
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
        currentFileFeatures = currentFileFeatures.with(Feature.ARRAY_DESTRUCTURING);
        return new Node(Token.ARRAY_PATTERN);
      case OBJECT_PATTERN:
        currentFileFeatures = currentFileFeatures.with(Feature.OBJECT_DESTRUCTURING);
        return new Node(Token.OBJECT_PATTERN);
      case DESTRUCTURING_LHS:
        return new Node(Token.DESTRUCTURING_LHS);
      case DEFAULT_VALUE:
        return new Node(Token.DEFAULT_VALUE);
      case GETTER_DEF:
        currentFileFeatures = currentFileFeatures.with(Feature.GETTER);
        return Node.newString(Token.GETTER_DEF, getString(n));
      case SETTER_DEF:
        currentFileFeatures = currentFileFeatures.with(Feature.SETTER);
        return Node.newString(Token.SETTER_DEF, getString(n));

      case IMPORT_SPECS:
        return new Node(Token.IMPORT_SPECS);
      case IMPORT_SPEC:
        return new Node(Token.IMPORT_SPEC);
      case IMPORT_STAR:
        return new Node(Token.IMPORT_STAR);
      case EXPORT_SPECS:
        return new Node(Token.EXPORT_SPECS);
      case EXPORT_SPEC:
        return new Node(Token.EXPORT_SPEC);
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

  private void deserializeProperties(Node n, AstNode serializedNode) {
    for (NodeProperty prop : serializedNode.getBooleanPropertyList()) {
      switch (prop) {
        case ARROW_FN:
          currentFileFeatures = currentFileFeatures.with(Feature.ARROW_FUNCTIONS);
          n.setIsArrowFunction(true);
          continue;
        case ASYNC_FN:
          currentFileFeatures = currentFileFeatures.with(Feature.ASYNC_FUNCTIONS);
          n.setIsAsyncFunction(true);
          continue;
        case GENERATOR_FN:
          currentFileFeatures = currentFileFeatures.with(Feature.GENERATORS);
          n.setIsGeneratorFunction(true);
          continue;
        case IS_PARENTHESIZED:
          n.setIsParenthesized(true);
          continue;
        case SYNTHETIC:
          n.setIsSyntheticBlock(true);
          continue;
        case ADDED_BLOCK:
          n.setIsAddedBlock(true);
          continue;
        case IS_CONSTANT_NAME:
          n.putBooleanProp(Node.IS_CONSTANT_NAME, true);
          continue;
        case IS_NAMESPACE:
          n.putBooleanProp(Node.IS_NAMESPACE, true);
          continue;
        case DIRECT_EVAL:
          n.putBooleanProp(Node.DIRECT_EVAL, true);
          continue;
        case FREE_CALL:
          n.putBooleanProp(Node.FREE_CALL, true);
          continue;
        case SLASH_V:
          n.putBooleanProp(Node.SLASH_V, true);
          continue;
        case REFLECTED_OBJECT:
          n.putBooleanProp(Node.REFLECTED_OBJECT, true);
          continue;
        case STATIC_MEMBER:
          n.setStaticMember(true);
          continue;
        case YIELD_ALL:
          n.setYieldAll(true);
          continue;
        case EXPORT_DEFAULT:
          n.putBooleanProp(Node.EXPORT_DEFAULT, true);
          continue;
        case EXPORT_ALL_FROM:
          n.putBooleanProp(Node.EXPORT_ALL_FROM, true);
          continue;
        case IS_GENERATOR_MARKER:
          n.setGeneratorMarker(true);
          continue;
        case IS_GENERATOR_SAFE:
          n.setGeneratorSafe(true);
          continue;
        case COMPUTED_PROP_METHOD:
          n.putBooleanProp(Node.COMPUTED_PROP_METHOD, true);
          continue;
        case COMPUTED_PROP_GETTER:
          n.putBooleanProp(Node.COMPUTED_PROP_GETTER, true);
          continue;
        case COMPUTED_PROP_SETTER:
          n.putBooleanProp(Node.COMPUTED_PROP_SETTER, true);
          continue;
        case COMPUTED_PROP_VARIABLE:
          n.putBooleanProp(Node.COMPUTED_PROP_VARIABLE, true);
          continue;
        case COLOR_FROM_CAST:
          n.setColorFromTypeCast();
          continue;
        case NON_INDEXABLE:
          n.makeNonIndexable();
          continue;
        case GOOG_MODULE:
          n.putBooleanProp(Node.GOOG_MODULE, true);
          continue;
        case WAS_PREVIOUSLY_PROVIDED:
          n.putBooleanProp(Node.WAS_PREVIOUSLY_PROVIDED, true);
          continue;
        case TRANSPILED:
          n.putBooleanProp(Node.TRANSPILED, true);
          continue;
        case DELETED:
          n.setDeleted(true);
          continue;
        case MODULE_ALIAS:
          n.putBooleanProp(Node.MODULE_ALIAS, true);
          continue;
        case IS_UNUSED_PARAMETER:
          n.setUnusedParameter(true);
          continue;
        case MODULE_EXPORT:
          n.putBooleanProp(Node.MODULE_EXPORT, true);
          continue;
        case IS_SHORTHAND_PROPERTY:
          n.setShorthandProperty(true);
          continue;
        case ES6_MODULE:
          n.putBooleanProp(Node.ES6_MODULE, true);
          continue;
        case START_OF_OPT_CHAIN:
          n.setIsOptionalChainStart(true);
          continue;
        case TRAILING_COMMA:
          n.setTrailingComma(true);
          continue;
        case DEFINE_NAME:
        case FEATURE_SET:
        case SIDE_EFFECT_FLAGS:
        case DIRECTIVES:
        case CONSTANT_VAR_FLAGS:
        case DECLARED_TYPE_EXPR:
          // We'll need to reevaluate these once we support non-binary node properties
        case UNRECOGNIZED:
        case NODE_PROPERTY_UNSPECIFIED:
          throw new RuntimeException("Hit unhandled node property: " + prop);
      }
    }
  }
}
