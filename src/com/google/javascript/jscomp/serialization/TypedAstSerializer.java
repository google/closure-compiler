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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;

/** Transforms a compiler AST into a serialized TypedAst object. */
final class TypedAstSerializer {

  private final AbstractCompiler compiler;
  private final SerializationOptions serializationMode;
  private final StringPool.Builder stringPool;
  private int previousLine;
  private int previousColumn;
  private final ArrayDeque<SourceFile> subtreeSourceFiles = new ArrayDeque<>();
  private final LinkedHashMap<SourceFile, Integer> sourceFilePointers = new LinkedHashMap<>();

  private IdentityHashMap<JSType, TypePointer> typesToPointers = null;

  private TypedAstSerializer(
      AbstractCompiler compiler,
      SerializationOptions serializationMode,
      StringPool.Builder stringPoolBuilder) {
    this.compiler = compiler;
    this.serializationMode = serializationMode;
    this.stringPool = stringPoolBuilder;
  }

  static TypedAstSerializer createFromRegistryWithOptions(
      AbstractCompiler compiler, SerializationOptions serializationMode) {
    StringPool.Builder stringPoolBuilder = StringPool.builder();
    return new TypedAstSerializer(compiler, serializationMode, stringPoolBuilder);
  }

  /** Transforms the given compiler AST root nodes into into a serialized TypedAst object */
  TypedAst serializeRoots(Node externsRoot, Node jsRoot) {
    checkArgument(externsRoot.isRoot());
    checkArgument(jsRoot.isRoot());

    final TypePool typePool;
    if (this.compiler.hasTypeCheckingRun()) {
      SerializeTypesToPointers typeSerializer =
          SerializeTypesToPointers.create(this.compiler, this.stringPool, this.serializationMode);
      typeSerializer.gatherTypesOnAst(jsRoot.getParent());
      this.typesToPointers = typeSerializer.getTypePointersByJstype();
      typePool = typeSerializer.getTypePool();
    } else {
      this.typesToPointers = new IdentityHashMap<>();
      typePool = TypePool.getDefaultInstance();
    }

    TypedAst.Builder builder = TypedAst.newBuilder();
    for (Node script = externsRoot.getFirstChild(); script != null; script = script.getNext()) {
      if (NodeUtil.isFromTypeSummary(script)) {
        continue;
      }
      builder.addExternFile(serializeScriptNode(script));
    }
    for (Node script = jsRoot.getFirstChild(); script != null; script = script.getNext()) {
      builder.addCodeFile(serializeScriptNode(script));
    }

    SourceFilePool sourceFiles =
        SourceFilePool.newBuilder()
            .addAllSourceFile(
                this.sourceFilePointers.keySet().stream()
                    .map(SourceFile::getProto)
                    .collect(toImmutableList()))
            .build();
    return builder
        .setTypePool(typePool)
        .setStringPool(this.stringPool.build().toProto())
        .setSourceFilePool(sourceFiles)
        .build();
  }

  private AstNode serializeScriptNode(Node script) {
    checkState(script.isScript());
    previousLine = previousColumn = 0;

    return visit(script);
  }

  private AstNode.Builder createWithPositionInfo(Node n) {
    checkState(n.getLength() >= 0);
    int currentLine = n.getLineno();
    int currentColumn = n.getCharno();
    AstNode.Builder builder =
        AstNode.newBuilder()
            .setRelativeLine(currentLine - previousLine)
            .setRelativeColumn(currentColumn - previousColumn);
    previousLine = currentLine;
    previousColumn = currentColumn;
    return builder;
  }

  private AstNode visit(Node n) {
    AstNode.Builder builder = createWithPositionInfo(n);
    addType(n, builder);
    OptimizationJsdoc serializedJsdoc = JsdocSerializer.serializeJsdoc(n.getJSDocInfo());
    if (serializedJsdoc != null) {
      builder.setJsdoc(serializedJsdoc);
    }
    builder.setKind(kindTranslator(n));
    valueTranslator(builder, n);
    builder.addAllBooleanProperty(booleanPropTranslator(n));
    int sourceFile = getSourceFilePointer(n);
    builder.setSourceFile(sourceFile);

    for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
      builder.addChild(visit(child));
    }

    if (sourceFile != 0) {
      subtreeSourceFiles.removeLast();
    }
    setOriginalName(builder, n);

    return builder.build();
  }

  private int getSourceFilePointer(Node n) {
    SourceFile sourceFile = (SourceFile) n.getStaticSourceFile();
    if (sourceFile == null) {
      // TODO(b/186056977): enforce that SourceFile is not null in externs as well as code.
      checkState(
          subtreeSourceFiles.peekLast().isExtern(),
          "Unexpected null SourceFile for node %s with parent %s",
          n.toStringTree(),
          n.getParent());
      return 0; // not set
    }

    if (sourceFile.equals(subtreeSourceFiles.peekLast())) {
      // To save space, only serialize a SourceFile for a node if it is different than the parent's
      // source.
      return 0; // not set
    }

    subtreeSourceFiles.addLast(sourceFile);
    return this.sourceFilePointers.computeIfAbsent(
        sourceFile, (f) -> 1 + this.sourceFilePointers.size());
  }

  private void addType(Node n, AstNode.Builder builder) {
    if (!compiler.hasTypeCheckingRun()) {
      // early return because some Nodes have non-null JSTypes even when typechecking has not run
      // TODO(b/185918953): enforce that nodes only have types after typechecking.
      return;
    }

    JSType type = n.getJSType();
    if (type != null) {
      builder.setType(this.typesToPointers.get(type));
    }
  }

  private EnumSet<NodeProperty> booleanPropTranslator(Node n) {
    EnumSet<NodeProperty> props = EnumSet.noneOf(NodeProperty.class);
    if (n.isArrowFunction()) {
      props.add(NodeProperty.ARROW_FN);
    }
    if (n.isAsyncFunction()) {
      props.add(NodeProperty.ASYNC_FN);
    }
    if (n.isGeneratorFunction()) {
      props.add(NodeProperty.GENERATOR_FN);
    }
    if (n.isYieldAll()) {
      props.add(NodeProperty.YIELD_ALL);
    }
    if (n.getIsParenthesized()) {
      props.add(NodeProperty.IS_PARENTHESIZED);
    }
    if (n.isSyntheticBlock()) {
      props.add(NodeProperty.SYNTHETIC);
    }
    if (n.isAddedBlock()) {
      props.add(NodeProperty.ADDED_BLOCK);
    }
    if (n.isStaticMember()) {
      props.add(NodeProperty.STATIC_MEMBER);
    }
    if (n.isYieldAll()) {
      props.add(NodeProperty.YIELD_ALL);
    }
    if (n.isGeneratorMarker()) {
      props.add(NodeProperty.IS_GENERATOR_MARKER);
    }
    if (n.isGeneratorSafe()) {
      props.add(NodeProperty.IS_GENERATOR_SAFE);
    }
    if (n.getJSTypeBeforeCast() != null) {
      props.add(NodeProperty.COLOR_FROM_CAST);
    }
    if (!n.isIndexable()) {
      props.add(NodeProperty.NON_INDEXABLE);
    }
    if (n.isDeleted()) {
      props.add(NodeProperty.DELETED);
    }
    if (n.isUnusedParameter()) {
      props.add(NodeProperty.IS_UNUSED_PARAMETER);
    }
    if (n.isShorthandProperty()) {
      props.add(NodeProperty.IS_SHORTHAND_PROPERTY);
    }
    if (n.isOptionalChainStart()) {
      props.add(NodeProperty.START_OF_OPT_CHAIN);
    }
    if (n.hasTrailingComma()) {
      props.add(NodeProperty.TRAILING_COMMA);
    }

    if (n.getBooleanProp(Node.IS_CONSTANT_NAME)) {
      props.add(NodeProperty.IS_CONSTANT_NAME);
    }
    if ((n.isName() || n.isImportStar()) && n.isDeclaredConstantVar()) {
      props.add(NodeProperty.IS_DECLARED_CONSTANT);
    }
    if ((n.isName() || n.isImportStar()) && n.isInferredConstantVar()) {
      props.add(NodeProperty.IS_INFERRED_CONSTANT);
    }
    if (n.getBooleanProp(Node.IS_NAMESPACE)) {
      props.add(NodeProperty.IS_NAMESPACE);
    }
    if (n.getBooleanProp(Node.DIRECT_EVAL)) {
      props.add(NodeProperty.DIRECT_EVAL);
    }
    if (n.getBooleanProp(Node.FREE_CALL)) {
      props.add(NodeProperty.FREE_CALL);
    }
    if (n.getBooleanProp(Node.SLASH_V)) {
      props.add(NodeProperty.SLASH_V);
    }
    if (n.getBooleanProp(Node.REFLECTED_OBJECT)) {
      props.add(NodeProperty.REFLECTED_OBJECT);
    }
    if (n.getBooleanProp(Node.EXPORT_DEFAULT)) {
      props.add(NodeProperty.EXPORT_DEFAULT);
    }
    if (n.getBooleanProp(Node.EXPORT_ALL_FROM)) {
      props.add(NodeProperty.EXPORT_ALL_FROM);
    }
    if (n.getBooleanProp(Node.COMPUTED_PROP_METHOD)) {
      props.add(NodeProperty.COMPUTED_PROP_METHOD);
    }
    if (n.getBooleanProp(Node.COMPUTED_PROP_GETTER)) {
      props.add(NodeProperty.COMPUTED_PROP_GETTER);
    }
    if (n.getBooleanProp(Node.COMPUTED_PROP_SETTER)) {
      props.add(NodeProperty.COMPUTED_PROP_SETTER);
    }
    if (n.getBooleanProp(Node.COMPUTED_PROP_VARIABLE)) {
      props.add(NodeProperty.COMPUTED_PROP_VARIABLE);
    }
    if (n.getBooleanProp(Node.GOOG_MODULE)) {
      props.add(NodeProperty.GOOG_MODULE);
    }
    if (n.getBooleanProp(Node.TRANSPILED)) {
      props.add(NodeProperty.TRANSPILED);
    }
    if (n.getBooleanProp(Node.MODULE_ALIAS)) {
      props.add(NodeProperty.MODULE_ALIAS);
    }
    if (n.getBooleanProp(Node.MODULE_EXPORT)) {
      props.add(NodeProperty.MODULE_EXPORT);
    }
    if (n.getBooleanProp(Node.ES6_MODULE)) {
      props.add(NodeProperty.ES6_MODULE);
    }
    return props;
  }

  private void valueTranslator(AstNode.Builder builder, Node n) {
    switch (n.getToken()) {
      case GETPROP:
      case OPTCHAIN_GETPROP:
      case MEMBER_FUNCTION_DEF:
      case NAME:
      case STRINGLIT:
      case STRING_KEY:
      case GETTER_DEF:
      case SETTER_DEF:
      case LABEL_NAME:
      case IMPORT_STAR:
        builder.setStringValuePointer(this.stringPool.put(n.getString()));
        return;
      case TEMPLATELIT_STRING:
        builder.setTemplateStringValue(
            TemplateStringValue.newBuilder()
                .setRawStringPointer(this.stringPool.put(n.getRawString()))
                .setCookedStringPointer(this.stringPool.put(n.getCookedString()))
                .build());
        return;
      case NUMBER:
        builder.setDoubleValue(n.getDouble());
        return;
      case BIGINT:
        builder.setStringValuePointer(this.stringPool.put(n.getBigInt().toString()));
        return;
      default:
        // No value
        return;
    }
  }

  private void setOriginalName(AstNode.Builder builder, Node n) {
    String originalName = n.getOriginalName();
    if (originalName == null) {
      builder.setOriginalNamePointer(0); // equivalent to 'not set'
      return;
    }

    builder.setOriginalNamePointer(stringPool.put(originalName));
  }

  private NodeKind kindTranslator(Node n) {
    switch (n.getToken()) {
      case SCRIPT:
        return NodeKind.SOURCE_FILE;

      case NUMBER:
        return NodeKind.NUMBER_LITERAL;
      case STRINGLIT:
        return NodeKind.STRING_LITERAL;
      case BIGINT:
        return NodeKind.BIGINT_LITERAL;
      case REGEXP:
        return NodeKind.REGEX_LITERAL;
      case FALSE:
        return NodeKind.FALSE;
      case TRUE:
        return NodeKind.TRUE;
      case NULL:
        return NodeKind.NULL;
      case THIS:
        return NodeKind.THIS;
      case VOID:
        return NodeKind.VOID;
      case ARRAYLIT:
        return NodeKind.ARRAY_LITERAL;
      case OBJECTLIT:
        return NodeKind.OBJECT_LITERAL;
      case NAME:
        return NodeKind.IDENTIFIER;

      case ASSIGN:
        return NodeKind.ASSIGNMENT;
      case CALL:
        return NodeKind.CALL;
      case NEW:
        return NodeKind.NEW;
      case GETPROP:
        return NodeKind.PROPERTY_ACCESS;
      case GETELEM:
        return NodeKind.ELEMENT_ACCESS;

      case COMMA:
        return NodeKind.COMMA;
      case OR:
        return NodeKind.BOOLEAN_OR;
      case AND:
        return NodeKind.BOOLEAN_AND;
      case HOOK:
        return NodeKind.HOOK;
      case EQ:
        return NodeKind.EQUAL;
      case NE:
        return NodeKind.NOT_EQUAL;
      case LT:
        return NodeKind.LESS_THAN;
      case LE:
        return NodeKind.LESS_THAN_EQUAL;
      case GT:
        return NodeKind.GREATER_THAN;
      case GE:
        return NodeKind.GREATER_THAN_EQUAL;
      case SHEQ:
        return NodeKind.TRIPLE_EQUAL;
      case SHNE:
        return NodeKind.NOT_TRIPLE_EQUAL;
      case NOT:
        return NodeKind.NOT;
      case POS:
        return NodeKind.POSITIVE;
      case NEG:
        return NodeKind.NEGATIVE;
      case TYPEOF:
        return NodeKind.TYPEOF;
      case INSTANCEOF:
        return NodeKind.INSTANCEOF;
      case IN:
        return NodeKind.IN;

      case ADD:
        return NodeKind.ADD;
      case SUB:
        return NodeKind.SUBTRACT;
      case MUL:
        return NodeKind.MULTIPLY;
      case DIV:
        return NodeKind.DIVIDE;
      case MOD:
        return NodeKind.MODULO;
      case EXPONENT:
        return NodeKind.EXPONENT;
      case BITNOT:
        return NodeKind.BITWISE_NOT;
      case BITOR:
        return NodeKind.BITWISE_OR;
      case BITAND:
        return NodeKind.BITWISE_AND;
      case BITXOR:
        return NodeKind.BITWISE_XOR;
      case LSH:
        return NodeKind.LEFT_SHIFT;
      case RSH:
        return NodeKind.RIGHT_SHIFT;
      case URSH:
        return NodeKind.UNSIGNED_RIGHT_SHIFT;
      case INC:
        return n.getBooleanProp(Node.INCRDECR_PROP)
            ? NodeKind.POST_INCREMENT
            : NodeKind.PRE_INCREMENT;
      case DEC:
        return n.getBooleanProp(Node.INCRDECR_PROP)
            ? NodeKind.POST_DECREMENT
            : NodeKind.PRE_DECREMENT;

      case ASSIGN_ADD:
        return NodeKind.ASSIGN_ADD;
      case ASSIGN_SUB:
        return NodeKind.ASSIGN_SUBTRACT;
      case ASSIGN_MUL:
        return NodeKind.ASSIGN_MULTIPLY;
      case ASSIGN_DIV:
        return NodeKind.ASSIGN_DIVIDE;
      case ASSIGN_MOD:
        return NodeKind.ASSIGN_MODULO;
      case ASSIGN_EXPONENT:
        return NodeKind.ASSIGN_EXPONENT;
      case ASSIGN_BITOR:
        return NodeKind.ASSIGN_BITWISE_OR;
      case ASSIGN_BITAND:
        return NodeKind.ASSIGN_BITWISE_AND;
      case ASSIGN_BITXOR:
        return NodeKind.ASSIGN_BITWISE_XOR;
      case ASSIGN_LSH:
        return NodeKind.ASSIGN_LEFT_SHIFT;
      case ASSIGN_RSH:
        return NodeKind.ASSIGN_RIGHT_SHIFT;
      case ASSIGN_URSH:
        return NodeKind.ASSIGN_UNSIGNED_RIGHT_SHIFT;

      case YIELD:
        return NodeKind.YIELD;
      case AWAIT:
        return NodeKind.AWAIT;
      case DELPROP:
        return NodeKind.DELETE;
      case TAGGED_TEMPLATELIT:
        return NodeKind.TAGGED_TEMPLATELIT;
      case TEMPLATELIT:
        return NodeKind.TEMPLATELIT;
      case TEMPLATELIT_SUB:
        return NodeKind.TEMPLATELIT_SUB;
      case TEMPLATELIT_STRING:
        return NodeKind.TEMPLATELIT_STRING;
      case NEW_TARGET:
        return NodeKind.NEW_TARGET;
      case COMPUTED_PROP:
        return NodeKind.COMPUTED_PROP;
      case IMPORT_META:
        return NodeKind.IMPORT_META;
      case OPTCHAIN_GETPROP:
        return NodeKind.OPTCHAIN_PROPERTY_ACCESS;
      case OPTCHAIN_CALL:
        return NodeKind.OPTCHAIN_CALL;
      case OPTCHAIN_GETELEM:
        return NodeKind.OPTCHAIN_ELEMENT_ACCESS;
      case COALESCE:
        return NodeKind.COALESCE;
      case DYNAMIC_IMPORT:
        return NodeKind.DYNAMIC_IMPORT;

      case CAST:
        return NodeKind.CAST;

      case EXPR_RESULT:
        return NodeKind.EXPRESSION_STATEMENT;
      case BREAK:
        return NodeKind.BREAK_STATEMENT;
      case CONTINUE:
        return NodeKind.CONTINUE_STATEMENT;
      case DEBUGGER:
        return NodeKind.DEBUGGER_STATEMENT;
      case DO:
        return NodeKind.DO_STATEMENT;
      case FOR:
        return NodeKind.FOR_STATEMENT;
      case FOR_IN:
        return NodeKind.FOR_IN_STATEMENT;
      case FOR_OF:
        return NodeKind.FOR_OF_STATEMENT;
      case FOR_AWAIT_OF:
        return NodeKind.FOR_AWAIT_OF_STATEMENT;
      case IF:
        return NodeKind.IF_STATEMENT;
      case RETURN:
        return NodeKind.RETURN_STATEMENT;
      case SWITCH:
        return NodeKind.SWITCH_STATEMENT;
      case THROW:
        return NodeKind.THROW_STATEMENT;
      case TRY:
        return NodeKind.TRY_STATEMENT;
      case WHILE:
        return NodeKind.WHILE_STATEMENT;
      case EMPTY:
        return NodeKind.EMPTY;
      case WITH:
        return NodeKind.WITH;
      case IMPORT:
        return NodeKind.IMPORT;
      case EXPORT:
        return NodeKind.EXPORT;

      case VAR:
        return NodeKind.VAR_DECLARATION;
      case CONST:
        return NodeKind.CONST_DECLARATION;
      case LET:
        return NodeKind.LET_DECLARATION;
      case FUNCTION:
        return NodeKind.FUNCTION_LITERAL;
      case CLASS:
        return NodeKind.CLASS_LITERAL;

      case BLOCK:
        return NodeKind.BLOCK;
      case LABEL:
        return NodeKind.LABELED_STATEMENT;
      case LABEL_NAME:
        return NodeKind.LABELED_NAME;
      case CLASS_MEMBERS:
        return NodeKind.CLASS_MEMBERS;
      case MEMBER_FUNCTION_DEF:
        return NodeKind.METHOD_DECLARATION;
      case PARAM_LIST:
        return NodeKind.PARAMETER_LIST;
      case STRING_KEY:
        return n.isQuotedString() ? NodeKind.QUOTED_STRING_KEY : NodeKind.RENAMABLE_STRING_KEY;
      case CASE:
        return NodeKind.CASE;
      case DEFAULT_CASE:
        return NodeKind.DEFAULT_CASE;
      case CATCH:
        return NodeKind.CATCH;
      case SUPER:
        return NodeKind.SUPER;
      case ARRAY_PATTERN:
        return NodeKind.ARRAY_PATTERN;
      case OBJECT_PATTERN:
        return NodeKind.OBJECT_PATTERN;
      case DESTRUCTURING_LHS:
        return NodeKind.DESTRUCTURING_LHS;
      case DEFAULT_VALUE:
        return NodeKind.DEFAULT_VALUE;
      case GETTER_DEF:
        return n.isQuotedString() ? NodeKind.QUOTED_GETTER_DEF : NodeKind.RENAMABLE_GETTER_DEF;
      case SETTER_DEF:
        return n.isQuotedString() ? NodeKind.QUOTED_SETTER_DEF : NodeKind.RENAMABLE_SETTER_DEF;

      case IMPORT_SPECS:
        return NodeKind.IMPORT_SPECS;
      case IMPORT_SPEC:
        return NodeKind.IMPORT_SPEC;
      case IMPORT_STAR:
        return NodeKind.IMPORT_STAR;
      case EXPORT_SPECS:
        return NodeKind.EXPORT_SPECS;
      case EXPORT_SPEC:
        return NodeKind.EXPORT_SPEC;
      case MODULE_BODY:
        return NodeKind.MODULE_BODY;
      case ITER_REST:
        return NodeKind.ITER_REST;
      case ITER_SPREAD:
        return NodeKind.ITER_SPREAD;
      case OBJECT_REST:
        return NodeKind.OBJECT_REST;
      case OBJECT_SPREAD:
        return NodeKind.OBJECT_SPREAD;

        // Explicitly unsupported token types. Not serialized.
      case ROOT:
        // TS type tokens
      case STRING_TYPE:
      case BOOLEAN_TYPE:
      case NUMBER_TYPE:
      case FUNCTION_TYPE:
      case PARAMETERIZED_TYPE:
      case UNION_TYPE:
      case ANY_TYPE:
      case NULLABLE_TYPE:
      case VOID_TYPE:
      case REST_PARAMETER_TYPE:
      case NAMED_TYPE:
      case RECORD_TYPE:
      case UNDEFINED_TYPE:
      case OPTIONAL_PARAMETER:
      case ARRAY_TYPE:
      case GENERIC_TYPE:
      case GENERIC_TYPE_LIST:
      case INTERFACE:
      case INTERFACE_EXTENDS:
      case INTERFACE_MEMBERS:
      case MEMBER_VARIABLE_DEF:
      case ENUM:
      case ENUM_MEMBERS:
      case IMPLEMENTS:
      case TYPE_ALIAS:
      case DECLARE:
      case INDEX_SIGNATURE:
      case CALL_SIGNATURE:
      case NAMESPACE:
      case NAMESPACE_ELEMENTS:
        // JSDoc tokens
      case ANNOTATION:
      case PIPE:
      case STAR:
      case EOC:
      case QMARK:
      case BANG:
      case EQUALS:
      case LB:
      case LC:
      case COLON:
        // Unused tokens
      case PLACEHOLDER1:
      case PLACEHOLDER2:
      case PLACEHOLDER3:
        break;
    }
    throw new IllegalStateException("Unserializable token for node: " + n);
  }
}
