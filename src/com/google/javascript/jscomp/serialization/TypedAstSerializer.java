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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorId;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;

/** Transforms a compiler AST into a serialized TypedAst object. */
@GwtIncompatible("protobuf.lite")
final class TypedAstSerializer {

  private final AbstractCompiler compiler;
  private final SerializationOptions serializationMode;
  private final StringPool.Builder stringPool = StringPool.builder();
  private int previousLine;
  private int previousColumn;
  private final ArrayDeque<SourceFile> subtreeSourceFiles = new ArrayDeque<>();
  private final LinkedHashMap<SourceFile, Integer> sourceFilePointers = new LinkedHashMap<>();

  private TypeSerializer typeSerializer = null;

  TypedAstSerializer(AbstractCompiler compiler, SerializationOptions serializationMode) {
    this.compiler = compiler;
    this.serializationMode = serializationMode;
  }

  /** Transforms the given compiler AST root nodes into into a serialized TypedAst object */
  TypedAst serializeRoots(Node externsRoot, Node jsRoot) {
    checkArgument(externsRoot.isRoot());
    checkArgument(jsRoot.isRoot());

    if (this.compiler.hasOptimizationColors()) {
      this.typeSerializer = createColorTypeSerializer(compiler, serializationMode, stringPool);
    } else if (this.compiler.hasTypeCheckingRun()) {
      // The AST has JSTypes, but we want to serialize colors instead.
      // TODO(bradfordcsmith): Change this branch to throw an error and delete the related logic.
      //     Nothing should be using it anymore.
      this.typeSerializer = createJSTypeSerializer(compiler, serializationMode, stringPool);
    } else {
      this.typeSerializer = new NoOpTypeSerializer();
    }

    TypedAst.Builder builder = TypedAst.newBuilder();
    for (Node script = externsRoot.getFirstChild(); script != null; script = script.getNext()) {
      if (NodeUtil.isFromTypeSummary(script)) {
        continue;
      }
      builder.addExternAst(serializeScriptNode(script));
    }
    for (Node script = jsRoot.getFirstChild(); script != null; script = script.getNext()) {
      if (NodeUtil.isFromTypeSummary(script)) {
        continue;
      }
      builder.addCodeAst(serializeScriptNode(script));
    }

    SourceFilePool sourceFiles =
        SourceFilePool.newBuilder()
            .addAllSourceFile(
                this.sourceFilePointers.keySet().stream()
                    .map(SourceFile::getProto)
                    .collect(toImmutableList()))
            .build();

    if (this.compiler.getExternProperties() != null) {
      ExternsSummary.Builder externsSummary = ExternsSummary.newBuilder();
      for (String prop : this.compiler.getExternProperties()) {
        externsSummary.addPropNamePtr(this.stringPool.put(prop));
      }
      builder.setExternsSummary(externsSummary);
    }

    return builder
        .setTypePool(typeSerializer.generateTypePool())
        .setStringPool(this.stringPool.build().toProto())
        .setSourceFilePool(sourceFiles)
        .build();
  }

  private LazyAst serializeScriptNode(Node script) {
    checkState(script.isScript());
    previousLine = previousColumn = 0;

    int sourceFile = getSourceFilePointer(script);
    AstNode scriptProto = visit(script);
    this.subtreeSourceFiles.clear();

    return LazyAst.newBuilder()
        .setScript(scriptProto.toByteString())
        .setSourceFile(sourceFile)
        .build();
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
    OptimizationJsdoc serializedJsdoc =
        JSDocSerializer.serializeJsdoc(n.getJSDocInfo(), stringPool);
    if (serializedJsdoc != null) {
      builder.setJsdoc(serializedJsdoc);
    }
    builder.setKind(kindTranslator(n));
    valueTranslator(builder, n);
    builder.addAllBooleanProperty(n.serializeProperties());
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
    typeSerializer.addTypeForNode(n, builder);
  }

  private void valueTranslator(AstNode.Builder builder, Node n) {
    switch (n.getToken()) {
      case GETPROP:
      case OPTCHAIN_GETPROP:
      case MEMBER_FUNCTION_DEF:
      case MEMBER_FIELD_DEF:
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
                .setCookedStringPointer(
                    n.getCookedString() == null ? -1 : this.stringPool.put(n.getCookedString()))
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

      case ASSIGN_OR:
        return NodeKind.ASSIGN_OR;
      case ASSIGN_AND:
        return NodeKind.ASSIGN_AND;
      case ASSIGN_COALESCE:
        return NodeKind.ASSIGN_COALESCE;

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
      case MEMBER_FIELD_DEF:
        return NodeKind.FIELD_DECLARATION;
      case COMPUTED_FIELD_DEF:
        return NodeKind.COMPUTED_PROP_FIELD;
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
        // Closure-type-system-specific token
      case CAST:
        // Unused tokens
      case PLACEHOLDER1:
      case PLACEHOLDER2:
      case PLACEHOLDER3:
        break;
    }
    throw new IllegalStateException("Unserializable token for node: " + n);
  }

  /** Used to provide TypePointers for serializing Nodes and to generate the TypePool. */
  interface TypeSerializer {
    /** If appropriate for `node` add a `TypePointer` to `astNodeBuilder` */
    void addTypeForNode(Node node, AstNode.Builder astNodeBuilder);

    /** Returns a `TypePool` containing the types used by `addTypeForNode()` */
    TypePool generateTypePool();
  }

  /** Used when type checking has not been done. */
  private static class NoOpTypeSerializer implements TypeSerializer {

    @Override
    public void addTypeForNode(Node node, AstNode.Builder astNodeBuilder) {
      // Do nothing.
    }

    @Override
    public TypePool generateTypePool() {
      return TypePool.getDefaultInstance(); // empty TypePool
    }
  }

  /** Create the `TypeSerializer` appropriate for an AST that contains JSTypes. */
  private static JSTypeSerializer createJSTypeSerializer(
      AbstractCompiler compiler,
      SerializationOptions serializationMode,
      StringPool.Builder stringPoolBuilder) {
    final SerializeTypesToPointers serializeTypesToPointers =
        SerializeTypesToPointers.create(compiler, stringPoolBuilder, serializationMode);
    // Gather and serialize all the types now.
    serializeTypesToPointers.gatherTypesOnAst(compiler.getRoot());
    return new JSTypeSerializer(
        serializeTypesToPointers.getTypePointersByJstype(), serializeTypesToPointers.getTypePool());
  }

  /** Used when the AST's JSTypes have not been converted to Colors */
  private static class JSTypeSerializer implements TypeSerializer {
    // Everything is pre-calculated with this form of serialization.
    private final IdentityHashMap<JSType, TypePointer> typesToPointers;
    private final TypePool typePool;

    private JSTypeSerializer(
        IdentityHashMap<JSType, TypePointer> typesToPointers, TypePool typePool) {
      this.typesToPointers = typesToPointers;
      this.typePool = typePool;
    }

    @Override
    public void addTypeForNode(Node node, AstNode.Builder astNodeBuilder) {
      JSType type = node.getJSType();
      if (type != null) {
        astNodeBuilder.setType(
            checkNotNull(typesToPointers.get(type), "cannot find TypePointer for %s", type));
      }
    }

    @Override
    public TypePool generateTypePool() {
      return typePool;
    }
  }

  /** Creates a `TypeSerializer` that knows how to serialize `Color`s from the AST. */
  private ColorTypeSerializer createColorTypeSerializer(
      AbstractCompiler compiler,
      SerializationOptions serializationMode,
      StringPool.Builder stringPoolBuilder) {
    // Gather all the property names that are actually used in the AST.
    final ImmutableSet<String> usedPropertyNames = collectUsedPropertyNames(compiler);
    final ColorSerializer colorSerializer =
        new ColorSerializer(
            serializationMode,
            // lookup / allocate strings from the shared string pool
            stringPoolBuilder::put,
            // only include property names known to be used in the AST
            usedPropertyNames::contains);
    return new ColorTypeSerializer(colorSerializer, compiler.getColorRegistry());
  }

  /** Add all unquoted property names appearing in the AST. */
  private static ImmutableSet<String> collectUsedPropertyNames(AbstractCompiler compiler) {
    final ImmutableSet.Builder<String> propertyNamesBuilder = ImmutableSet.builder();

    NodeTraversal.traverse(
        compiler,
        compiler.getRoot(),
        new NodeTraversal.Callback() {
          @Override
          public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
            // We won't serialize type summary files, so we don't care about property names
            // appearing in them.
            return !n.isScript() || !NodeUtil.isFromTypeSummary(n);
          }

          @Override
          public void visit(NodeTraversal t, Node n, Node parent) {
            switch (n.getToken()) {
              case GETPROP: // "name" from (someObject.name)
              case OPTCHAIN_GETPROP: // "name" from (someObject?.name)
                propertyNamesBuilder.add(n.getString());
                break;
              case STRING_KEY: // "name" from obj = {name: 0}
              case MEMBER_FUNCTION_DEF: // "name" from class C { name() {} }
              case MEMBER_FIELD_DEF: // "name" from class C { name = 0; }
              case GETTER_DEF: // "name" from class C { get name() {} }
              case SETTER_DEF: // "name" from class C { set name(n) {} }
                if (!n.isQuotedString()) {
                  propertyNamesBuilder.add(n.getString());
                }
                break;
              default:
            }
          }
        });
    return propertyNamesBuilder.build();
  }

  /** Used when the AST has `Color`s rather than `JSType`s */
  private static class ColorTypeSerializer implements TypeSerializer {
    private final ColorSerializer colorSerializer;
    private final ColorRegistry colorRegistry;

    private ColorTypeSerializer(ColorSerializer colorSerializer, ColorRegistry colorRegistry) {
      this.colorSerializer = colorSerializer;
      this.colorRegistry = colorRegistry;
    }

    @Override
    public void addTypeForNode(Node node, AstNode.Builder astNodeBuilder) {
      Color color = node.getColor();
      if (color != null) {
        astNodeBuilder.setType(colorSerializer.addColor(color));
      }
    }

    @Override
    public TypePool generateTypePool() {
      final ImmutableSetMultimap<ColorId, String> mismatchLocationsForDebugging =
          colorRegistry.getMismatchLocationsForDebugging();
      return colorSerializer.generateTypePool(
          colorRegistry::getDisambiguationSupertypes,
          color -> mismatchLocationsForDebugging.get(color.getId()));
    }
  }
}
