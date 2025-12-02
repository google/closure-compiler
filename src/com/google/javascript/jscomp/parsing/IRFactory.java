/*
 * Copyright 2013 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.base.JSCompObjects.identical;
import static java.lang.Integer.parseInt;
import static java.util.Comparator.comparingInt;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.jscomp.parsing.Config.JsDocParsing;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.jscomp.parsing.parser.IdentifierToken;
import com.google.javascript.jscomp.parsing.parser.LiteralToken;
import com.google.javascript.jscomp.parsing.parser.SourceFile;
import com.google.javascript.jscomp.parsing.parser.TemplateLiteralToken;
import com.google.javascript.jscomp.parsing.parser.TokenType;
import com.google.javascript.jscomp.parsing.parser.trees.ArgumentListTree;
import com.google.javascript.jscomp.parsing.parser.trees.ArrayLiteralExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ArrayPatternTree;
import com.google.javascript.jscomp.parsing.parser.trees.AwaitExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.BinaryOperatorTree;
import com.google.javascript.jscomp.parsing.parser.trees.BlockTree;
import com.google.javascript.jscomp.parsing.parser.trees.BreakStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.CallExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.CaseClauseTree;
import com.google.javascript.jscomp.parsing.parser.trees.CatchTree;
import com.google.javascript.jscomp.parsing.parser.trees.ClassDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.CommaExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.Comment;
import com.google.javascript.jscomp.parsing.parser.trees.ComprehensionForTree;
import com.google.javascript.jscomp.parsing.parser.trees.ComprehensionIfTree;
import com.google.javascript.jscomp.parsing.parser.trees.ComprehensionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ComputedPropertyDefinitionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ComputedPropertyFieldTree;
import com.google.javascript.jscomp.parsing.parser.trees.ComputedPropertyGetterTree;
import com.google.javascript.jscomp.parsing.parser.trees.ComputedPropertyMethodTree;
import com.google.javascript.jscomp.parsing.parser.trees.ComputedPropertySetterTree;
import com.google.javascript.jscomp.parsing.parser.trees.ConditionalExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ContinueStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.DebuggerStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.DefaultClauseTree;
import com.google.javascript.jscomp.parsing.parser.trees.DefaultParameterTree;
import com.google.javascript.jscomp.parsing.parser.trees.DoWhileStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.DynamicImportTree;
import com.google.javascript.jscomp.parsing.parser.trees.EmptyStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.ExportDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.ExportSpecifierTree;
import com.google.javascript.jscomp.parsing.parser.trees.ExpressionStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.FieldDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.FinallyTree;
import com.google.javascript.jscomp.parsing.parser.trees.ForAwaitOfStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.ForInStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.ForOfStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.ForStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.FormalParameterListTree;
import com.google.javascript.jscomp.parsing.parser.trees.FunctionDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.GetAccessorTree;
import com.google.javascript.jscomp.parsing.parser.trees.IdentifierExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.IfStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.ImportDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.ImportMetaExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ImportSpecifierTree;
import com.google.javascript.jscomp.parsing.parser.trees.IterRestTree;
import com.google.javascript.jscomp.parsing.parser.trees.IterSpreadTree;
import com.google.javascript.jscomp.parsing.parser.trees.LabelledStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.LiteralExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.MemberExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.MemberLookupExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.MissingPrimaryExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.NewExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.NewTargetExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.NullTree;
import com.google.javascript.jscomp.parsing.parser.trees.ObjectLiteralExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ObjectPatternTree;
import com.google.javascript.jscomp.parsing.parser.trees.ObjectSpreadTree;
import com.google.javascript.jscomp.parsing.parser.trees.OptChainCallExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.OptionalMemberExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.OptionalMemberLookupExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ParenExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ParseTree;
import com.google.javascript.jscomp.parsing.parser.trees.ParseTreeType;
import com.google.javascript.jscomp.parsing.parser.trees.ProgramTree;
import com.google.javascript.jscomp.parsing.parser.trees.PropertyNameAssignmentTree;
import com.google.javascript.jscomp.parsing.parser.trees.ReturnStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.SetAccessorTree;
import com.google.javascript.jscomp.parsing.parser.trees.SuperExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.SwitchStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.TemplateLiteralExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.TemplateLiteralPortionTree;
import com.google.javascript.jscomp.parsing.parser.trees.TemplateSubstitutionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ThisExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ThrowStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.TryStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.UnaryExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.UpdateExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.UpdateExpressionTree.OperatorPosition;
import com.google.javascript.jscomp.parsing.parser.trees.VariableDeclarationListTree;
import com.google.javascript.jscomp.parsing.parser.trees.VariableDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.VariableStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.WhileStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.WithStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.YieldExpressionTree;
import com.google.javascript.jscomp.parsing.parser.util.SourcePosition;
import com.google.javascript.jscomp.parsing.parser.util.SourceRange;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.NonJSDocComment;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;
import com.google.javascript.rhino.dtoa.DToA;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/** IRFactory transforms the external AST to the internal AST. */
class IRFactory {

  static final String GETTER_ERROR_MESSAGE =
      "getters are not supported in older versions of JavaScript. "
          + "If you are targeting newer versions of JavaScript, "
          + "set the appropriate language_in option.";

  static final String SETTER_ERROR_MESSAGE =
      "setters are not supported in older versions of JavaScript. "
          + "If you are targeting newer versions of JavaScript, "
          + "set the appropriate language_in option.";

  static final String INVALID_ES3_PROP_NAME =
      "Keywords and reserved words are not allowed as unquoted property "
          + "names in older versions of JavaScript. "
          + "If you are targeting newer versions of JavaScript, "
          + "set the appropriate language_in option.";

  static final String INVALID_ES5_STRICT_OCTAL =
      "Octal integer literals are not supported in strict mode.";

  static final String INVALID_OCTAL_DIGIT = "Invalid octal digit in octal literal.";

  static final String STRING_CONTINUATION_WARNING =
      "String continuations are not recommended. See"
          + " https://google.github.io/styleguide/jsguide.html#features-strings-no-line-continuations";

  static final String OCTAL_STRING_LITERAL_WARNING =
      "Octal literals in strings are not supported in this language mode.";

  static final String DUPLICATE_PARAMETER = "Duplicate parameter name \"%s\"";

  static final String DUPLICATE_LABEL = "Duplicate label \"%s\"";

  static final String UNLABELED_BREAK = "unlabelled break must be inside loop or switch";

  static final String UNEXPECTED_CONTINUE = "continue must be inside loop";

  static final String UNEXPECTED_LABELLED_CONTINUE =
      "continue can only use labels of iteration statements";

  static final String UNEXPECTED_RETURN = "return must be inside function";
  static final String UNEXPECTED_YIELD = "yield must be inside generator function";
  static final String UNEXPECTED_AWAIT = "await must be inside asynchronous function";
  static final String UNEXPECTED_NEW_DOT_TARGET = "new.target must be inside a function";
  static final String UNDEFINED_LABEL = "undefined label \"%s\"";

  private final StaticSourceFile sourceFile;
  private final @Nullable String sourceName;

  /** Source file reference that also contains the file content. */
  private final SourceFile fileWithContent;

  private final Config config;
  private final ErrorReporter errorReporter;
  private final TransformDispatcher transformDispatcher;

  private static final ImmutableSet<String> ES5_RESERVED_KEYWORDS =
      ImmutableSet.of(
          // From Section 7.6.1.2
          "class", "const", "enum", "export", "extends", "import", "super");
  private static final ImmutableSet<String> ES5_STRICT_RESERVED_KEYWORDS =
      ImmutableSet.of(
          // From Section 7.6.1.2
          "class",
          "const",
          "enum",
          "export",
          "extends",
          "import",
          "super",
          "implements",
          "interface",
          "let",
          "package",
          "private",
          "protected",
          "public",
          "static",
          "yield");

  /** If non-null, use this set of keywords instead of TokenStream.isKeyword(). */
  private final @Nullable ImmutableSet<String> reservedKeywords;

  private final Set<Comment> parsedComments = new LinkedHashSet<>();

  private final LinkedHashSet<String> licenseBuilder = new LinkedHashSet<>();
  private @Nullable JSDocInfo firstFileoverview = null;

  // Use a template node for properties set on all nodes to minimize the
  // memory footprint associated with these.
  // templateNode acts as the current template node for the IRFactory. The template node is updated
  // from defaultTemplateNode to closureUnawareTemplateNode whenever a new closure-unaware code
  // range is entered in the transform method and swapped back to the defaultTemplateNode when the
  // closure-unaware code range is exited.
  // This is safe because while on a closure unaware code range, only nodes that should have the
  // IS_IN_CLOSURE_UNAWARE_SUBTREE property are created.
  // An alternative was to directly update the templateNode's propListHead, but we don't want to
  // expose the propListHead outside of the Node class.
  private Node templateNode;
  private final Node defaultTemplateNode;
  private final Node closureUnawareTemplateNode;

  private final CommentTracker jsdocTracker;
  private final CommentTracker nonJsdocTracker;
  private final JsDocInfoParser.JsDocSourceKind jsDocSourceKind;

  private boolean currentFileIsExterns = false;

  private FeatureSet features = FeatureSet.BARE_MINIMUM;
  private Node resultNode;

  /** Represents a line and column number in a file. */
  private record LineAndColumn(int lineNo, int column) implements Comparable<LineAndColumn> {

    static LineAndColumn fromSourcePosition(SourcePosition pos) {
      return new LineAndColumn(pos.line, pos.column);
    }

    @Override
    public int compareTo(LineAndColumn o) {
      return comparingInt(LineAndColumn::lineNo)
          .thenComparingInt(LineAndColumn::column)
          .compare(this, o);
    }
  }

  private final RangeSet<LineAndColumn> closureUnawareCodeRanges = TreeRangeSet.create();

  private IRFactory(
      StaticSourceFile sourceFile,
      Config config,
      ErrorReporter errorReporter,
      ImmutableList<Comment> comments,
      SourceFile fileWithContent,
      JsDocInfoParser.JsDocSourceKind jsDocSourceKind) {
    this.jsdocTracker = new CommentTracker(comments, (c) -> c.type == Comment.Type.JSDOC);
    this.nonJsdocTracker = new CommentTracker(comments, (c) -> c.type != Comment.Type.JSDOC);
    this.sourceFile = sourceFile;
    this.fileWithContent = fileWithContent;
    // The template node properties are applied to all nodes in this transform.
    this.defaultTemplateNode = createTemplateNode();
    this.templateNode = defaultTemplateNode;
    this.closureUnawareTemplateNode = createClosureUnawareTemplateNode();

    // Sometimes this will be null in tests.
    this.sourceName = sourceFile == null ? null : sourceFile.getName();

    this.config = config;
    this.errorReporter = errorReporter;
    this.transformDispatcher = new TransformDispatcher();

    if (config.strictMode().isStrict()) {
      reservedKeywords = ES5_STRICT_RESERVED_KEYWORDS;
    } else if (config.languageMode() == LanguageMode.ECMASCRIPT3) {
      reservedKeywords = null; // use TokenStream.isKeyword instead
    } else {
      reservedKeywords = ES5_RESERVED_KEYWORDS;
    }
    this.jsDocSourceKind = jsDocSourceKind;
  }

  private static final class CommentTracker {
    private final ImmutableList<Comment> source;
    private final Predicate<Comment> filter;
    private int index = -1;
    private int previousIndex = -1;

    CommentTracker(ImmutableList<Comment> source, Predicate<Comment> filter) {
      this.source = source;
      this.filter = filter;

      this.advance();
    }

    @Nullable Comment current() {
      return (this.index >= this.source.size()) ? null : this.source.get(this.index);
    }

    void advance() {
      this.previousIndex = this.index;
      while (true) {
        this.index++; // Always advance at least one element.

        Comment c = this.current();
        if (c == null || this.filter.test(c)) {
          break;
        }
      }
    }

    void backtrack() {
      this.index = this.previousIndex;
    }

    boolean hasPendingCommentBefore(SourcePosition pos) {
      Comment c = this.current();
      // The line number matters for trailing comments
      return c != null && c.location.end.line <= pos.line && c.location.end.offset <= pos.offset;
    }
  }

  // Create a template node to use as a source of common attributes, this allows
  // the prop structure to be shared among all the node from this source file.
  // This reduces the cost of these properties to O(nodes) to O(files).
  private Node createTemplateNode() {
    // The Node type choice is arbitrary.
    Node templateNode = new Node(Token.SCRIPT);
    templateNode.setStaticSourceFile(sourceFile);
    return templateNode;
  }

  private Node createClosureUnawareTemplateNode() {
    // We clone the default template node, so that the source file property, and other potential
    // properties are shared
    Node closureUnawareTemplateNode = defaultTemplateNode.cloneNode();

    // We add the IS_IN_CLOSURE_UNAWARE_SUBTREE property to the head of the property list.
    closureUnawareTemplateNode.setIsInClosureUnawareSubtree(true);
    return closureUnawareTemplateNode;
  }

  public static IRFactory transformTree(
      ProgramTree tree,
      StaticSourceFile sourceFile,
      Config config,
      ErrorReporter errorReporter,
      SourceFile file) {
    JsDocInfoParser.JsDocSourceKind jsDocSourceKind =
        sourceFile.isTypeScriptSource()
            ? JsDocInfoParser.JsDocSourceKind.TSICKLE
            : JsDocInfoParser.JsDocSourceKind.NORMAL;
    IRFactory irFactory =
        new IRFactory(
            sourceFile, config, errorReporter, tree.sourceComments, file, jsDocSourceKind);

    // don't call transform as we don't want standard jsdoc handling.
    Node n = irFactory.transformDispatcher.process(tree);
    irFactory.setSourceInfo(n, tree);

    if (tree.sourceComments != null) {
      for (Comment comment : tree.sourceComments) {
        if ((comment.type == Comment.Type.JSDOC || comment.type == Comment.Type.IMPORTANT)
            && !irFactory.parsedComments.contains(comment)) {
          boolean useLicensesOnlyConfig =
              irFactory.withinClosureUnawareCodeRange(
                  comment.location.start.line, comment.location.start.column);
          irFactory.handlePossibleFileOverviewJsDoc(comment, useLicensesOnlyConfig);
        }
      }

      if (n.isScript()) {
        SourcePosition endOfFilePos = tree.location.end;
        // Handle end of file comments that are still pending
        NonJSDocComment nonJSDocComment =
            irFactory.parseNonJSDocCommentAt(endOfFilePos, /* isInline= */ false);
        if (nonJSDocComment != null) {
          // pending end-of-file comment exists && parsing mode is set to INCLUDE_ALL_COMMENTS
          n.setTrailingNonJSDocComment(nonJSDocComment);
        }
      }
    }

    irFactory.setFileOverviewJsDoc(n);

    irFactory.validateAll(n);
    irFactory.resultNode = n;

    return irFactory;
  }

  Node getResultNode() {
    return resultNode;
  }

  FeatureSet getFeatures() {
    return features;
  }

  private void validateAll(Node n) {
    ArrayDeque<Node> work = new ArrayDeque<>();
    while (n != null) {
      validate(n);
      Node nextSibling = n.getNext();
      Node firstChild = n.getFirstChild();
      if (firstChild != null) {
        if (nextSibling != null) {
          // handle the siblings later
          work.push(nextSibling);
        }
        n = firstChild;
      } else if (nextSibling != null) {
        // no children, handle the next sibling
        n = nextSibling;
      } else {
        // no siblings, continue with work we have saved on the work queue
        n = work.poll();
      }
    }
    checkState(work.isEmpty());
  }

  private void validate(Node n) {
    validateParameters(n);
    validateBreakContinue(n);
    validateReturn(n);
    validateYield(n);
    validateAwait(n);
    validateNewDotTarget(n);
    validateLabel(n);
    validateBlockScopedFunctions(n);
  }

  private void validateAwait(Node n) {
    if (!n.isAwait()) {
      return;
    }

    Node parent = n;
    while ((parent = parent.getParent()) != null) {
      // The await is in a class static block.
      // e.g. `class C { static { await; } }`
      if (parent.isClassMembers()) {
        errorReporter.error(UNEXPECTED_AWAIT, sourceName, n.getLineno(), n.getCharno());
        return;
      }
      if (parent.isAsyncFunction()) {
        return;
      }
      if (parent.isFunction()) {
        // The await is in a non-async function.
        // e.g. `function f() { return await 5; }`
        // nested function e.g. `async function f() { function f2() { return await 5; } }`
        errorReporter.error(UNEXPECTED_AWAIT, sourceName, n.getLineno(), n.getCharno());
        return;
      }
    }
    errorReporter.error(UNEXPECTED_AWAIT, sourceName, n.getLineno(), n.getCharno());
  }

  private void validateYield(Node n) {
    if (!n.isYield()) {
      return;
    }

    Node parent = n;
    while ((parent = parent.getParent()) != null) {
      // The yield is in a class static block.
      // e.g. `class C { static { yield; } }`
      if (parent.isClassMembers()) {
        errorReporter.error(UNEXPECTED_YIELD, sourceName, n.getLineno(), n.getCharno());
        return;
      }
      if (parent.isGeneratorFunction()) {
        return;
      }
    }
  }

  private void validateReturn(Node n) {
    if (!n.isReturn()) {
      return;
    }

    Node parent = n;
    while ((parent = parent.getParent()) != null) {
      // The return is in a class static block.
      // e.g. `class C { static { return; } }`
      if (parent.isClassMembers()) {
        errorReporter.error(UNEXPECTED_RETURN, sourceName, n.getLineno(), n.getCharno());
        return;
      }
      if (parent.isFunction()) {
        return;
      }
    }
    errorReporter.error(UNEXPECTED_RETURN, sourceName, n.getLineno(), n.getCharno());
  }

  private void validateNewDotTarget(Node n) {
    if (n.getToken() != Token.NEW_TARGET) {
      return;
    }

    Node parent = n;
    while ((parent = parent.getParent()) != null) {
      if (parent.isFunction()) {
        return;
      }
    }
    errorReporter.error(UNEXPECTED_NEW_DOT_TARGET, sourceName, n.getLineno(), n.getCharno());
  }

  private void validateBreakContinue(Node n) {
    if (!n.isBreak() && !n.isContinue()) {
      return;
    }

    Node labelName = n.getFirstChild();
    if (labelName != null) {
      Node parent = n.getParent();
      while (!parent.isLabel() || !labelsMatch(parent, labelName)) {
        if (parent.isFunction() || parent.isScript() || parent.isClassMembers()) {
          // report missing label
          errorReporter.error(
              String.format(UNDEFINED_LABEL, labelName.getString()),
              sourceName,
              n.getLineno(),
              n.getCharno());
          break;
        }
        parent = parent.getParent();
      }

      if (parent.isLabel() && labelsMatch(parent, labelName)) {
        if (n.isContinue() && !isContinueTarget(parent.getLastChild())) {
          // report invalid continue target
          errorReporter.error(
              UNEXPECTED_LABELLED_CONTINUE, sourceName, n.getLineno(), n.getCharno());
        }
      }
    } else if (n.isContinue()) {
      Node parent = n.getParent();
      while (!isContinueTarget(parent)) {
        if (parent.isFunction() || parent.isScript() || parent.isClassMembers()) {
          // report invalid continue
          errorReporter.error(UNEXPECTED_CONTINUE, sourceName, n.getLineno(), n.getCharno());
          break;
        }
        parent = parent.getParent();
      }
    } else {
      Node parent = n.getParent();
      while (!isBreakTarget(parent)) {
        if (parent.isFunction() || parent.isScript() || parent.isClassMembers()) {
          // report invalid break
          errorReporter.error(UNLABELED_BREAK, sourceName, n.getLineno(), n.getCharno());
          break;
        }
        parent = parent.getParent();
      }
    }
  }

  private static boolean isBreakTarget(Node n) {
    return switch (n.getToken()) {
      case FOR, FOR_IN, FOR_OF, FOR_AWAIT_OF, WHILE, DO, SWITCH -> true;
      default -> false;
    };
  }

  private static boolean isContinueTarget(Node n) {
    return switch (n.getToken()) {
      case FOR, FOR_IN, FOR_OF, FOR_AWAIT_OF, WHILE, DO -> true;
      default -> false;
    };
  }

  private static boolean labelsMatch(Node label, Node labelName) {
    return label.getFirstChild().getString().equals(labelName.getString());
  }

  private void validateLabel(Node n) {
    if (!n.isLabel()) {
      return;
    }

    Node labelName = n.getFirstChild();
    for (Node parent = n.getParent();
        parent != null && !parent.isFunction();
        parent = parent.getParent()) {
      if (parent.isLabel() && labelsMatch(parent, labelName)) {
        errorReporter.error(
            String.format(DUPLICATE_LABEL, labelName.getString()),
            sourceName,
            n.getLineno(),
            n.getCharno());
        break;
      }
    }
  }

  private void validateParameters(Node n) {
    if (!n.isParamList()) {
      return;
    }

    Set<String> seenNames = new LinkedHashSet<>();
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      ParsingUtil.getParamOrPatternNames(
          c,
          (Node param) -> {
            String paramName = param.getString();
            if (!seenNames.add(paramName)) {
              errorReporter.warning(
                  String.format(DUPLICATE_PARAMETER, paramName),
                  sourceName,
                  param.getLineno(),
                  param.getCharno());
            }
          });
    }
  }

  private void validateBlockScopedFunctions(Node n) {
    if (n.isFunction() && n.getParent().isBlock() && !n.getGrandparent().isFunction()) {
      maybeWarnForFeature(n, Feature.BLOCK_SCOPED_FUNCTION_DECLARATION);
    }
  }

  private void setFileOverviewJsDoc(Node irNode) {
    JSDocInfo.Builder fileoverview =
        (this.firstFileoverview == null) ? JSDocInfo.builder() : this.firstFileoverview.toBuilder();

    if (!this.licenseBuilder.isEmpty()) {
      fileoverview.recordLicense(String.join("", this.licenseBuilder));
    }

    irNode.setJSDocInfo(fileoverview.build(false));
  }

  Node transformBlock(ParseTree node) {
    Node irNode = transform(node);
    if (irNode.isBlock()) {
      return irNode;
    }

    Node newBlock = irNode.isEmpty() ? newNode(Token.BLOCK) : newNode(Token.BLOCK, irNode);
    setSourceInfo(newBlock, irNode);
    newBlock.setIsAddedBlock(true);

    return newBlock;
  }

  /**
   * @return true if the jsDocParser represents a fileoverview.
   */
  private boolean handlePossibleFileOverviewJsDoc(JsDocInfoParser jsDocParser) {
    if (jsDocParser.getLicenseText() != null) {
      this.licenseBuilder.add(jsDocParser.getLicenseText());
    }

    JSDocInfo newFileoverview = jsDocParser.getFileOverviewJSDocInfo();
    if (identical(newFileoverview, this.firstFileoverview)) {
      return false;
    }

    if (this.firstFileoverview == null) {
      this.firstFileoverview = newFileoverview;
      this.currentFileIsExterns = newFileoverview.isExterns();
    } else {
      JSDocInfo.Builder merged = this.firstFileoverview.toBuilder();
      if (!newFileoverview.getSuppressions().isEmpty()) {
        merged.recordSuppressions(newFileoverview.getSuppressions());
      }
      if (newFileoverview.isExterns()) {
        this.currentFileIsExterns = true;
        merged.recordExterns();
      }
      if (newFileoverview.isNoCoverage()) {
        merged.recordNoCoverage();
      }
      this.firstFileoverview = merged.build();
    }

    return true;
  }

  private void handlePossibleFileOverviewJsDoc(Comment comment, boolean useLicensesOnlyConfig) {
    JsDocInfoParser jsDocParser = createJsDocInfoParser(comment, useLicensesOnlyConfig);
    parsedComments.add(comment);
    handlePossibleFileOverviewJsDoc(jsDocParser);
  }

  private Comment getJSDocCommentAt(SourcePosition pos) {
    Comment closestPreviousComment = null;
    while (this.jsdocTracker.hasPendingCommentBefore(pos)) {
      closestPreviousComment = this.jsdocTracker.current();
      this.jsdocTracker.advance();
    }

    return closestPreviousComment;
  }

  private @Nullable JSDocInfo parseJSDocInfoFrom(Comment comment) {
    if (comment == null) {
      return null;
    }

    if (withinClosureUnawareCodeRange(comment.location.start.line, comment.location.start.column)) {
      // Within closure-unaware code, we don't parse jsdoc as if it is load-bearing.
      // This early-return prevents the comments here from being recorded as being parsed,
      // and this later allows all these jsdoc comments to be treated as "top-level" comments that
      // might contain license info / "important" comments (the only form of JSDoc that should
      // happen for closure-unaware code).
      this.jsdocTracker.backtrack();
      return null;
    }

    JsDocInfoParser jsDocParser = createJsDocInfoParser(comment);
    parsedComments.add(comment);
    if (handlePossibleFileOverviewJsDoc(jsDocParser)) {
      // Returning null here means that the comment was treated as a fileoverview comment, and
      // JSDocInfo should not then be attached to a specific node, so we return null;
      return null;
    }
    return jsDocParser.retrieveAndResetParsedJSDocInfo();
  }

  private @Nullable JSDocInfo parseJSDocInfoOnTree(ParseTree tree) {
    switch (tree.type) {
      case EXPRESSION_STATEMENT, LABELLED_STATEMENT, EXPORT_DECLARATION, TEMPLATE_SUBSTITUTION -> {
        return null;
      }
      case CALL_EXPRESSION,
          CONDITIONAL_EXPRESSION,
          COMMA_EXPRESSION,
          BINARY_OPERATOR,
          MEMBER_EXPRESSION,
          MEMBER_LOOKUP_EXPRESSION,
          UPDATE_EXPRESSION -> {
        {
          ParseTree nearest = findNearestNode(tree);
          if (nearest.type == ParseTreeType.PAREN_EXPRESSION) {
            return null;
          }
        }
      }
      default -> {}
    }

    return parseJSDocInfoFrom(getJSDocCommentAt(tree.getStart()));
  }

  @Nullable JSDocInfo parseJSDocInfoOnToken(
      com.google.javascript.jscomp.parsing.parser.Token token) {
    return parseJSDocInfoFrom(getJSDocCommentAt(token.getStart()));
  }

  @Nullable JSDocInfo parseInlineJSDocAt(SourcePosition pos) {
    if (withinClosureUnawareCodeRange(pos.line, pos.column)) {
      // Within closure-unaware code, we don't parse jsdoc as if it is load-bearing.
      // This early-return prevents the comments here from being recorded as being parsed,
      // and this later allows all these jsdoc comments to be treated as "top-level" comments that
      // might contain license info / "important" comments (the only form of JSDoc that should
      // happen for closure-unaware code).
      // Unlike other points in IRFactory where we explicitly backtrack the jsdocTracker, we don't
      // do that here because we've actually never advanced it yet - that happens in
      // getJSDocCommentAt.
      return null;
    }
    Comment comment = getJSDocCommentAt(pos);
    return (comment != null && !comment.value.contains("@"))
        ? parseInlineTypeDoc(comment)
        : parseJSDocInfoFrom(comment);
  }

  private static final Comparator<Comment> COMMENT_START_POSITION_COMPARATOR =
      comparingInt((Comment x) -> x.location.start.line)
          .thenComparingInt(x -> x.location.start.column);

  private boolean hasAnyPendingCommentBefore(
      SourcePosition pos, boolean withinClosureUnawareCodeRange) {
    return this.nonJsdocTracker.hasPendingCommentBefore(pos)
        || (withinClosureUnawareCodeRange && this.jsdocTracker.hasPendingCommentBefore(pos));
  }

  /**
   * Creates a single NonJSDocComment from every comment associated with this node; or null if there
   * are no such comments.
   *
   * <p>It would be legal to replace all comments associated with this node with that one string.
   */
  private @Nullable NonJSDocComment parseNonJSDocCommentAt(SourcePosition pos, boolean isInline) {
    boolean withinClosureUnawareCodeRange = withinClosureUnawareCodeRange(pos.line, pos.column);

    StringBuilder result = new StringBuilder();
    Comment firstComment = null;
    Comment lastComment = null;

    while (hasAnyPendingCommentBefore(pos, withinClosureUnawareCodeRange)) {
      Comment currentComment = this.nonJsdocTracker.current();
      boolean commentFromJsdocTracker = false;

      if (withinClosureUnawareCodeRange) {
        Comment currentJsDocComment = this.jsdocTracker.current();
        if (currentComment == null
            || (currentJsDocComment != null
                && COMMENT_START_POSITION_COMPARATOR.compare(currentComment, currentJsDocComment)
                    > 0)) {

          if (currentJsDocComment.value.contains("@license")) {
            // Skip this so it can be parsed for the license contents (which happens when a comment
            // isn't inserted into the parsedComments set).
            this.jsdocTracker.advance();
            continue;
          }

          commentFromJsdocTracker = true;
          currentComment = currentJsDocComment;
        }
      }

      if (firstComment == null) {
        firstComment = currentComment;
      }

      if (lastComment != null) {
        for (int blankCount = currentComment.location.start.line - lastComment.location.end.line;
            blankCount > 0;
            blankCount--) {
          result.append("\n");
        }
      }
      result.append(currentComment.value);

      lastComment = currentComment;
      if (commentFromJsdocTracker) {
        this.parsedComments.add(currentComment);
        this.jsdocTracker.advance();
      } else {
        this.nonJsdocTracker.advance();
      }
    }

    if (firstComment == null) {
      return null; // We didn't find any relevant comments.
    }

    if (config.jsDocParsingMode() != JsDocParsing.INCLUDE_ALL_COMMENTS) {
      return null;
    }

    NonJSDocComment nonJSDocComment =
        new NonJSDocComment(
            firstComment.location.start, lastComment.location.end, result.toString());
    nonJSDocComment.setEndsAsLineComment(lastComment.type == Comment.Type.LINE);
    nonJSDocComment.setIsInline(isInline);
    return nonJSDocComment;
  }

  /**
   * Creates a single NonJSDocComment from the comment immediately following this node; or null if
   * there is no such comment.
   *
   * @param tokenEnd The end position after which we're looking for a trailing comment
   * @param possibleNextTokenStart last source position that we're interested in when looking for a
   *     trailing comment
   */
  private @Nullable NonJSDocComment parseTrailingNonJSDocCommentAt(
      SourcePosition tokenEnd, SourcePosition possibleNextTokenStart) {
    if (config.jsDocParsingMode() != JsDocParsing.INCLUDE_ALL_COMMENTS) {
      return null;
    }

    if (!this.nonJsdocTracker.hasPendingCommentBefore(possibleNextTokenStart)) {
      return null;
    }

    StringBuilder result = new StringBuilder();
    Comment comment = this.nonJsdocTracker.current();

    // Disregard comments that don't start on the same line
    if (tokenEnd.line != comment.location.start.line) {
      return null;
    }

    // Disregard comments that may be within the current statement
    if (comment.location.start.offset <= tokenEnd.offset) {
      return null;
    }

    // Check if there are only whitespace characters between the current token and the start of the
    // comment.
    String preCommentText =
        this.fileWithContent.contents.substring(tokenEnd.offset + 1, comment.location.start.offset);

    if (!preCommentText.trim().isEmpty()) {
      return null;
    }
    // TODO(user): handle multiple trailing comments like /* c1 */ /* c2 */ // c3
    result.append(comment.value);
    this.nonJsdocTracker.advance();

    NonJSDocComment nonJSDocComment =
        new NonJSDocComment(comment.location.start, comment.location.end, result.toString());
    nonJSDocComment.setEndsAsLineComment(comment.type == Comment.Type.LINE);
    return nonJSDocComment;
  }

  private static ParseTree findNearestNode(ParseTree tree) {
    while (true) {
      switch (tree.type) {
        case EXPRESSION_STATEMENT -> {
          tree = tree.asExpressionStatement().expression;
          continue;
        }
        case CALL_EXPRESSION -> {
          tree = tree.asCallExpression().operand;
          continue;
        }
        case BINARY_OPERATOR -> {
          tree = tree.asBinaryOperator().left;
          continue;
        }
        case CONDITIONAL_EXPRESSION -> {
          tree = tree.asConditionalExpression().condition;
          continue;
        }
        case MEMBER_EXPRESSION -> {
          tree = tree.asMemberExpression().operand;
          continue;
        }
        case MEMBER_LOOKUP_EXPRESSION -> {
          tree = tree.asMemberLookupExpression().operand;
          continue;
        }
        case UPDATE_EXPRESSION -> {
          tree = tree.asUpdateExpression().operand;
          continue;
        }
        case COMMA_EXPRESSION -> {
          tree = tree.asCommaExpression().expressions.get(0);
          continue;
        }
        default -> {
          return tree;
        }
      }
    }
  }

  Node transform(ParseTree tree) {
    JSDocInfo info = parseJSDocInfoOnTree(tree);
    NonJSDocComment comment = parseNonJSDocCommentAt(tree.getStart(), false);
    boolean isRootOfClosureUnawareSubTree = (info != null && info.isClosureUnawareCode());

    if (isRootOfClosureUnawareSubTree) {
      templateNode = closureUnawareTemplateNode;
      SourceRange nextClosureUnawareRange = tree.location;
      LineAndColumn nextClosureUnawareRangeStart =
          LineAndColumn.fromSourcePosition(nextClosureUnawareRange.start);
      LineAndColumn nextClosureUnawareRangeEnd =
          LineAndColumn.fromSourcePosition(nextClosureUnawareRange.end);
      this.closureUnawareCodeRanges.add(
          Range.closed(nextClosureUnawareRangeStart, nextClosureUnawareRangeEnd));
    }

    Node node = transformDispatcher.process(tree);

    if (info != null) {
      node = maybeInjectCastNode(tree, info, node);
      node.setJSDocInfo(info);
    }
    if (comment != null) {
      node.setNonJSDocComment(comment);
    }
    if (isRootOfClosureUnawareSubTree) {
      templateNode = defaultTemplateNode;
    }
    setSourceInfo(node, tree);
    return node;
  }

  private boolean withinClosureUnawareCodeRange(int line, int lineColumnNo) {
    if (this.closureUnawareCodeRanges.isEmpty()) {
      return false;
    }

    return this.closureUnawareCodeRanges.contains(new LineAndColumn(line, lineColumnNo));
  }

  private Node maybeInjectCastNode(ParseTree node, JSDocInfo info, Node irNode) {
    if (node.type == ParseTreeType.PAREN_EXPRESSION && info.hasType()) {
      irNode = newNode(Token.CAST, irNode);
    }
    return irNode;
  }

  /**
   * Names and destructuring patterns, in parameters or variable declarations are special, because
   * they can have inline type docs attached.
   *
   * <pre>function f(/** string &#42;/ x) {}</pre>
   *
   * annotates 'x' as a string.
   *
   * @see <a href="http://code.google.com/p/jsdoc-toolkit/wiki/InlineDocs">Using Inline Doc
   *     Comments</a>
   */
  Node transformNodeWithInlineComments(ParseTree tree) {
    JSDocInfo info = parseInlineJSDocAt(tree.getStart());
    NonJSDocComment comment = parseNonJSDocCommentAt(tree.getStart(), true);

    Node node = transformDispatcher.process(tree);

    if (info != null) {
      node.setJSDocInfo(info);
    }
    if (comment != null) {
      node.setNonJSDocComment(comment);
    }
    setSourceInfo(node, tree);
    return node;
  }

  static int lineno(ParseTree node) {
    return lineno(node.location.start);
  }

  static int lineno(com.google.javascript.jscomp.parsing.parser.Token token) {
    return lineno(token.location.start);
  }

  static int lineno(SourcePosition location) {
    // location lines start at zero, our AST starts at 1.
    return location.line + 1;
  }

  static int charno(ParseTree node) {
    return charno(node.location.start);
  }

  static int charno(com.google.javascript.jscomp.parsing.parser.Token token) {
    return charno(token.location.start);
  }

  static int charno(SourcePosition location) {
    return location.column;
  }

  static String languageFeatureWarningMessage(Feature feature) {
    LanguageMode forFeature = LanguageMode.minimumRequiredFor(feature);

    if (forFeature == LanguageMode.UNSUPPORTED) {
      return "This language feature is not currently supported by the compiler: " + feature;
    } else {
      return "This language feature is only supported for "
          + LanguageMode.minimumRequiredFor(feature)
          + " mode or better: "
          + feature;
    }
  }

  void maybeWarnForFeature(ParseTree node, Feature feature) {
    features = features.with(feature);
    if (withinClosureUnawareCodeRange(node.location.start.line, node.location.start.column)) {
      return;
    }
    if (!isSupportedForInputLanguageMode(feature)) {
      errorReporter.warning(
          languageFeatureWarningMessage(feature), sourceName, lineno(node), charno(node));
    }
  }

  void maybeWarnForFeature(
      com.google.javascript.jscomp.parsing.parser.Token token, Feature feature) {
    features = features.with(feature);
    if (withinClosureUnawareCodeRange(token.location.start.line, token.location.start.column)) {
      return;
    }
    if (!isSupportedForInputLanguageMode(feature)) {
      errorReporter.warning(
          languageFeatureWarningMessage(feature), sourceName, lineno(token), charno(token));
    }
  }

  void maybeWarnForFeature(Node node, Feature feature) {
    features = features.with(feature);
    if (withinClosureUnawareCodeRange(node.getLineno(), node.getCharno())) {
      return;
    }
    if (!isSupportedForInputLanguageMode(feature)) {
      errorReporter.warning(
          languageFeatureWarningMessage(feature), sourceName, node.getLineno(), node.getCharno());
    }
  }

  void setSourceInfo(Node node, Node ref) {
    node.setLinenoCharno(ref.getLineno(), ref.getCharno());
    setLengthFrom(node, ref);
  }

  void setSourceInfo(Node irNode, ParseTree node) {
    if (irNode.getLineno() == -1) {
      setSourceInfo(irNode, node.location.start, node.location.end);
    }
  }

  void setSourceInfo(Node irNode, com.google.javascript.jscomp.parsing.parser.Token token) {
    setSourceInfo(irNode, token.location.start, token.location.end);
  }

  void setSourceInfo(Node node, SourcePosition start, SourcePosition end) {
    if (node.getLineno() == -1) {
      // If we didn't already set the line, then set it now. This avoids
      // cases like ParenthesizedExpression where we just return a previous
      // node, but don't want the new node to get its parent's line number.
      node.setLinenoCharno(lineno(start), charno(start));
      setLength(node, start, end);
    }
  }

  private JsDocInfoParser createJsDocInfoParser(Comment node) {
    return createJsDocInfoParser(node, false);
  }

  /**
   * Creates a JsDocInfoParser and parses the JsDoc string.
   *
   * <p>Used both for handling individual JSDoc comments and for handling file-level JSDoc comments
   * (@fileoverview and @license).
   *
   * @param node The JsDoc Comment node to parse.
   * @return A JsDocInfoParser. Will contain either fileoverview JsDoc, or normal JsDoc, or no JsDoc
   *     (if the method parses to the wrong level).
   */
  private JsDocInfoParser createJsDocInfoParser(Comment node, boolean useLicensesOnlyConfig) {
    String comment = node.value;
    int lineno = lineno(node.location.start);
    int charno = charno(node.location.start);
    int position = node.location.start.offset;

    Config config = this.config;
    if (useLicensesOnlyConfig) {
      config = config.toBuilder().setJsDocParsingMode(JsDocParsing.LICENSE_COMMENTS_ONLY).build();
    }

    // The JsDocInfoParser expects the comment without the initial '/**'.
    int numOpeningChars = 3;
    JsDocInfoParser jsdocParser =
        new JsDocInfoParser(
            new JsDocTokenStream(
                comment.substring(numOpeningChars), lineno, charno + numOpeningChars),
            comment,
            position,
            templateNode,
            config,
            jsDocSourceKind,
            errorReporter);
    jsdocParser.setFileOverviewJSDocInfo(this.firstFileoverview);
    if (node.type == Comment.Type.IMPORTANT && node.value.length() > 0) {
      jsdocParser.parseImportantComment();
    } else {
      jsdocParser.parse();
    }

    return jsdocParser;
  }

  /** Parses inline type info. */
  private JSDocInfo parseInlineTypeDoc(Comment node) {
    String comment = node.value;
    int lineno = lineno(node.location.start);
    int charno = charno(node.location.start);

    // The JsDocInfoParser expects the comment without the initial '/**'.
    int numOpeningChars = 3;
    JsDocInfoParser parser =
        new JsDocInfoParser(
            new JsDocTokenStream(
                comment.substring(numOpeningChars), lineno, charno + numOpeningChars),
            comment,
            node.location.start.offset,
            templateNode,
            config,
            jsDocSourceKind,
            errorReporter);
    return parser.parseInlineTypeDoc();
  }

  // Set the length on the node if we're in IDE mode.
  void setLength(Node node, SourcePosition start, SourcePosition end) {
    node.setLength(end.offset - start.offset);
  }

  void setLengthFrom(Node node, Node ref) {
    node.setLength(ref.getLength());
  }

  private static final QualifiedName GOOG_MODULE = QualifiedName.of("goog.module");

  private class TransformDispatcher {

    /**
     * Tracks whether a certain language feature is currently in scope. Use in conjunction with
     * try-with-resources to auto-handle decrementing.
     */
    private static class ScopeTracker {
      private int usageCount = 0;

      boolean inScope() {
        return usageCount > 0;
      }

      AutoDecrement increment() {
        return maybeIncrement(true);
      }

      AutoDecrement maybeIncrement(boolean condition) {
        if (condition) {
          checkState(usageCount >= 0);
          ++usageCount;
        }
        return new AutoDecrement() {
          private boolean closed = false;

          @Override
          public void close() {
            checkState(!closed);
            if (condition) {
              --usageCount;
              checkState(usageCount >= 0);
            }
            closed = true;
          }
        };
      }
    }

    // Variant of AutoCloseable that does not throw.
    private interface AutoDecrement extends AutoCloseable {
      @Override
      void close();
    }

    // Tracks whether a class is currently in scope. Presently used to detect valid usage of
    // private fields.
    private final ScopeTracker classScope = new ScopeTracker();

    // Used to detect an IdentifierExpression in the following context:
    // Valid: `class A { #x; static isA(o) { return #x in o; } }`
    // Invalid: `class A { #x; } function isA(o) { return #x in o; } }`
    private final ScopeTracker privateIdLhsOfInScope = new ScopeTracker();

    /** Indicates the valid values usages for an identifier. */
    private enum IdentifierType {
      /** The identifier can never be private. */
      STANDARD,
      /** The identifier can be private if it is in a class scope. */
      CAN_BE_PRIVATE
    }

    /**
     * Creates a StringNode using the value of an IdentifierToken. Reports an error if the
     * identifier value is private and the identifier type is STANDARD or the identifier is not
     * within the scope of a class.
     */
    Node newStringNodeFromIdentifier(
        Token type, IdentifierType identifierType, IdentifierToken identifierToken) {
      // Private properties can only ever be referenced from within the scope of a class. If we're
      // not in a class, we always use STANDARD rules.
      if (!classScope.inScope()) {
        identifierType = IdentifierType.STANDARD;
      }

      String value;
      if (identifierToken.isPrivateIdentifier()) {
        if (identifierType == IdentifierType.CAN_BE_PRIVATE) {
          maybeWarnForFeature(identifierToken, Feature.PRIVATE_CLASS_PROPERTIES);
        } else {
          errorReporter.error(
              "Private identifiers may not be used in this context",
              sourceName,
              identifierToken.location.start.line,
              identifierToken.location.start.column);
        }
        value = identifierToken.getMaybePrivateValue();
      } else {
        value = identifierToken.getValue();
      }

      Node node = newStringNode(type, value);
      if (identifierType == IdentifierType.CAN_BE_PRIVATE
          && identifierToken.isPrivateIdentifier()) {
        node.setPrivateIdentifier();
      }
      return node;
    }

    /**
     * Transforms the given object key `input` into a Node with token `output`.
     *
     * <p>Depending on `input`, this may add quoting, such as for numerical values. For example, in
     * `{2: null}`, `2` will be transformed into a quoted string. This adjustment loses some
     * accuracy about the source code, but simplifies the AST.
     *
     * <p>Get and set accessors process their name using this method. For classes, this can be a
     * private property in which case <code>identifierType</code> will be set to <code>
     * CAN_BE_PRIVATE</code>. E.g. <code>class C { get #f() { return value; } }</code>.
     */
    private Node processObjectLitKey(
        com.google.javascript.jscomp.parsing.parser.Token input,
        Token output,
        IdentifierType identifierType) {
      if (input == null) {
        return createMissingExpressionNode();
      }

      if (input.type == TokenType.IDENTIFIER) {
        return processName(input.asIdentifier(), output, identifierType);
      }

      LiteralToken literal = input.asLiteral();
      JSDocInfo jsDocInfo = parseJSDocInfoOnToken(literal);
      NonJSDocComment comment = parseNonJSDocCommentAt(literal.getStart(), true);

      final Node node =
          switch (input.type) {
            case NUMBER -> newStringNode(output, DToA.numberToString(normalizeNumber(literal)));
            case BIGINT -> newStringNode(output, normalizeBigInt(literal).toString());
            default -> newStringNode(output, normalizeString(literal, false));
          };

      if (jsDocInfo != null) {
        node.setJSDocInfo(jsDocInfo);
      }
      if (comment != null) {
        node.setNonJSDocComment(comment);
      }

      setSourceInfo(node, literal);
      node.putBooleanProp(Node.QUOTED_PROP, true);
      return node;
    }

    Node processComprehension(ComprehensionTree tree) {
      return unsupportedLanguageFeature(tree, "array/generator comprehensions");
    }

    Node processComprehensionFor(ComprehensionForTree tree) {
      return unsupportedLanguageFeature(tree, "array/generator comprehensions");
    }

    Node processComprehensionIf(ComprehensionIfTree tree) {
      return unsupportedLanguageFeature(tree, "array/generator comprehensions");
    }

    Node processArrayLiteral(ArrayLiteralExpressionTree tree) {
      Node node = newNode(Token.ARRAYLIT);
      node.setTrailingComma(tree.hasTrailingComma);
      for (ParseTree child : tree.elements) {
        Node c = transform(child);
        node.addChildToBack(c);
      }
      return node;
    }

    Node processArrayPattern(ArrayPatternTree tree) {
      maybeWarnForFeature(tree, Feature.ARRAY_DESTRUCTURING);

      Node node = newNode(Token.ARRAY_PATTERN);
      for (ParseTree child : tree.elements) {
        final Node elementNode;
        switch (child.type) {
          case DEFAULT_PARAMETER ->
              // processDefaultParameter() knows how to find and apply inline JSDoc to the right
              // node
              elementNode = processDefaultParameter(child.asDefaultParameter());
          case ITER_REST -> {
            maybeWarnForFeature(child, Feature.ARRAY_PATTERN_REST);
            elementNode = transformNodeWithInlineComments(child);
          }
          default -> elementNode = transformNodeWithInlineComments(child);
        }
        node.addChildToBack(elementNode);
      }
      return node;
    }

    Node processObjectPattern(ObjectPatternTree tree) {
      maybeWarnForFeature(tree, Feature.OBJECT_DESTRUCTURING);

      Node node = newNode(Token.OBJECT_PATTERN);
      for (ParseTree child : tree.fields) {
        Node childNode = processObjectPatternElement(child);
        node.addChildToBack(childNode);
      }
      return node;
    }

    private Node processObjectPatternElement(ParseTree child) {
      switch (child.type) {
        case DEFAULT_PARAMETER -> {
          // shorthand with a default value
          // let { /** inlineType */ name = default } = something;
          return processObjectPatternShorthandWithDefault(child.asDefaultParameter());
        }
        case PROPERTY_NAME_ASSIGNMENT -> {
          return processObjectPatternPropertyNameAssignment(child.asPropertyNameAssignment());
        }
        case COMPUTED_PROPERTY_DEFINITION -> {
          // let {[expression]: /** inlineType */ name} = something;
          ComputedPropertyDefinitionTree computedPropertyDefinition =
              child.asComputedPropertyDefinition();
          return processObjectPatternComputedPropertyDefinition(computedPropertyDefinition);
        }
        case OBJECT_REST -> {
          // let {...restObject} = someObject;
          maybeWarnForFeature(child, Feature.OBJECT_PATTERN_REST);
          Node target = transformNodeWithInlineComments(child.asObjectRest().assignmentTarget);
          Node rest = newNode(Token.OBJECT_REST, target);
          setSourceInfo(rest, child);
          return rest;
        }
        default -> throw new IllegalStateException("Unexpected object pattern element: " + child);
      }
    }

    /**
     * Process an object pattern element using shorthand and a default value.
     *
     * <p>e.g. the `/** inlineType * / name = default` part of this code
     *
     * <pre><code>
     * let { /** inlineType * / name = default } = something;
     * </code></pre>
     */
    private Node processObjectPatternShorthandWithDefault(DefaultParameterTree defaultParameter) {
      Node defaultValueNode = processDefaultParameter(defaultParameter);
      // Store in AST as non-shorthand form & just note it was originally shorthand
      // {name: /**inlineType */ name = default }
      Node nameNode = defaultValueNode.getFirstChild();
      Node stringKeyNode =
          maybeAddNonJsDocComment(
              newStringNode(Token.STRING_KEY, nameNode.getString()), defaultParameter.getStart());
      setSourceInfo(stringKeyNode, nameNode);
      stringKeyNode.setShorthandProperty(true);
      stringKeyNode.addChildToBack(defaultValueNode);
      return stringKeyNode;
    }

    /**
     * Processes property name assignments in an object pattern.
     *
     * <p>Covers these cases.
     *
     * <pre><code>
     *   let {name} = someObject;
     *   let {key: name} = someObject;
     *   let {key: name = something} = someObject;
     * </code></pre>
     */
    private Node processObjectPatternPropertyNameAssignment(
        PropertyNameAssignmentTree propertyNameAssignment) {
      Node key =
          processObjectLitKey(
              propertyNameAssignment.name, Token.STRING_KEY, IdentifierType.STANDARD);
      ParseTree targetTree = propertyNameAssignment.value;
      final Node valueNode;
      if (targetTree == null) {
        // `let { /** inlineType */ key } = something;`
        // The key is also the target name.
        valueNode =
            processNameWithInlineComments(
                propertyNameAssignment.name.asIdentifier(), IdentifierType.STANDARD);
        key.setShorthandProperty(true);
      } else {
        valueNode = processDestructuringElementTarget(targetTree);
      }
      key.addChildToFront(valueNode);
      return key;
    }

    private Node processDestructuringElementTarget(ParseTree targetTree) {
      final Node valueNode;
      if (targetTree.type == ParseTreeType.DEFAULT_PARAMETER) {
        // let {key: /** inlineType */ name = default} = something;
        // let [/** inlineType */ name = default] = something;
        // processDefaultParameter() knows how to apply the inline JSDoc, if any, to the right node
        valueNode = processDefaultParameter(targetTree.asDefaultParameter());
      } else if (targetTree.type == ParseTreeType.IDENTIFIER_EXPRESSION) {
        // let {key: /** inlineType */ name} = something
        // let [/** inlineType */ name] = something
        // Allow inline JSDoc on the name, since we may well be declaring it here.
        valueNode =
            processNameWithInlineComments(
                targetTree.asIdentifierExpression(), IdentifierType.STANDARD);
      } else {
        // ({prop: /** string */ ns.a.b} = someObject);
        // NOTE: CheckJSDoc will report an error for this case, since we want qualified names to be
        // declared with individual statements, like `/** @type {string} */ ns.a.b;`
        valueNode = transformNodeWithInlineComments(targetTree);
      }
      return valueNode;
    }

    /**
     * Processes a computed property in an object pattern.
     *
     * <p>Covers these cases:
     *
     * <pre><code>
     *   let {[expression]: name} = someObject;
     *   let {[expression]: name = defaultValue} = someObject;
     * </code></pre>
     */
    private Node processObjectPatternComputedPropertyDefinition(
        ComputedPropertyDefinitionTree computedPropertyDefinition) {
      maybeWarnForFeature(computedPropertyDefinition, Feature.COMPUTED_PROPERTIES);

      Node expressionNode = transform(computedPropertyDefinition.property);

      ParseTree valueTree = computedPropertyDefinition.value;
      Node valueNode = processDestructuringElementTarget(valueTree);
      Node computedPropertyNode = newNode(Token.COMPUTED_PROP, expressionNode, valueNode);
      setSourceInfo(computedPropertyNode, computedPropertyDefinition);
      return computedPropertyNode;
    }

    Node processAstRoot(ProgramTree rootNode) {
      Node scriptNode = newNode(Token.SCRIPT);
      for (ParseTree child : rootNode.sourceElements) {
        scriptNode.addChildToBack(transform(child));
      }
      parseDirectives(scriptNode);
      boolean isGoogModule = isGoogModuleFile(scriptNode);
      if (isGoogModule || features.has(Feature.MODULES)) {
        Node moduleNode = newNode(Token.MODULE_BODY);
        setSourceInfo(moduleNode, rootNode);
        moduleNode.addChildrenToBack(scriptNode.removeChildren());
        scriptNode.addChildToBack(moduleNode);
        if (isGoogModule) {
          scriptNode.putBooleanProp(Node.GOOG_MODULE, true);
        } else {
          scriptNode.putBooleanProp(Node.ES6_MODULE, true);
        }
      }
      return scriptNode;
    }

    private boolean isGoogModuleFile(Node scriptNode) {
      checkArgument(scriptNode.isScript());
      if (!scriptNode.hasChildren()) {
        return false;
      }
      Node exprResult = scriptNode.getFirstChild();
      if (!exprResult.isExprResult()) {
        return false;
      }
      Node call = exprResult.getFirstChild();
      if (!call.isCall()) {
        return false;
      }
      return GOOG_MODULE.matches(call.getFirstChild())
          && call.hasTwoChildren()
          && call.getSecondChild().isStringLit();
    }

    /**
     * Parse the directives, encode them in the AST, and remove their nodes.
     *
     * <p>The only suported directive is "use strict".
     *
     * <p>For information on ES5 directives, see section 14.1 of ECMA-262, Edition 5.
     */
    private void parseDirectives(Node node) {
      boolean useStrict = false;
      while (true) {
        Node statement = node.getFirstChild();
        if (statement == null || !statement.isExprResult()) {
          break;
        }

        Node directive = statement.getFirstChild();
        if (!directive.isStringLit() || !directive.getString().equals("use strict")) {
          break;
        }

        useStrict = true;
        // TODO(nickreid): Should we also remove other directives that aren't "use strict"?
        statement.detach();
      }

      if (useStrict) {
        node.setUseStrict(true);
      }
    }

    Node processBlock(BlockTree blockNode) {
      Node node = newNode(Token.BLOCK);
      for (ParseTree child : blockNode.statements) {
        Node childNode = transform(child);
        node.addChildToBack(childNode);
        attachPossibleTrailingComment(childNode, child.getEnd());
      }

      NonJSDocComment lastComment = parseNonJSDocCommentAt(blockNode.getEnd(), false);
      addExtraTrailingComment(node, lastComment);
      return node;
    }

    Node processBreakStatement(BreakStatementTree statementNode) {
      Node node = newNode(Token.BREAK);
      if (statementNode.hasLabel()) {
        Node labelName = transformLabelName(statementNode.name);
        node.addChildToBack(labelName);
      }
      return node;
    }

    Node transformLabelName(IdentifierToken token) {
      Node label =
          maybeAddNonJsDocComment(
              newStringNodeFromIdentifier(Token.LABEL_NAME, IdentifierType.STANDARD, token),
              token.getStart());
      setSourceInfo(label, token);
      return label;
    }

    Node processConditionalExpression(ConditionalExpressionTree exprNode) {
      return newNode(
          Token.HOOK,
          transform(exprNode.condition),
          transform(exprNode.left),
          transform(exprNode.right));
    }

    Node processContinueStatement(ContinueStatementTree statementNode) {
      Node node = newNode(Token.CONTINUE);
      if (statementNode.hasLabel()) {
        Node labelName = transformLabelName(statementNode.name);
        node.addChildToBack(labelName);
      }
      return node;
    }

    Node processDoLoop(DoWhileStatementTree loopNode) {
      return newNode(Token.DO, transformBlock(loopNode.body), transform(loopNode.condition));
    }

    Node processElementGet(MemberLookupExpressionTree getNode) {
      return newNode(
          Token.GETELEM, transform(getNode.operand), transform(getNode.memberExpression));
    }

    Node processOptChainElementGet(OptionalMemberLookupExpressionTree getNode) {
      maybeWarnForFeature(getNode, Feature.OPTIONAL_CHAINING);
      Node getElem =
          newNode(
              Token.OPTCHAIN_GETELEM,
              transform(getNode.operand),
              transform(getNode.memberExpression));
      getElem.setIsOptionalChainStart(getNode.isStartOfOptionalChain);
      return getElem;
    }

    @SuppressWarnings("unused") // for symmetry all the process* methods take a ParseTree
    Node processEmptyStatement(EmptyStatementTree unused) {
      return newNode(Token.EMPTY);
    }

    Node processExpressionStatement(ExpressionStatementTree statementNode) {
      Node node = newNode(Token.EXPR_RESULT);
      node.addChildToBack(transform(statementNode.expression));
      return node;
    }

    Node processForInLoop(ForInStatementTree loopNode) {
      // TODO(bradfordcsmith): Rename initializer to something more intuitive like "lhs"
      Node initializer = transform(loopNode.initializer);
      return newNode(
          Token.FOR_IN, initializer, transform(loopNode.collection), transformBlock(loopNode.body));
    }

    Node processForOf(ForOfStatementTree loopNode) {
      maybeWarnForFeature(loopNode, Feature.FOR_OF);
      Node initializer = transform(loopNode.initializer);
      return newNode(
          Token.FOR_OF, initializer, transform(loopNode.collection), transformBlock(loopNode.body));
    }

    Node processForAwaitOf(ForAwaitOfStatementTree loopNode) {
      maybeWarnForFeature(loopNode, Feature.FOR_AWAIT_OF);
      Node initializer = transform(loopNode.initializer);
      return newNode(
          Token.FOR_AWAIT_OF,
          initializer,
          transform(loopNode.collection),
          transformBlock(loopNode.body));
    }

    Node processForLoop(ForStatementTree loopNode) {
      Node node =
          newNode(
              Token.FOR,
              transformOrEmpty(loopNode.initializer, loopNode),
              transformOrEmpty(loopNode.condition, loopNode),
              transformOrEmpty(loopNode.increment, loopNode));
      node.addChildToBack(transformBlock(loopNode.body));
      return node;
    }

    Node transformOrEmpty(ParseTree tree, ParseTree parent) {
      if (tree == null) {
        Node n = newNode(Token.EMPTY);
        setSourceInfo(n, parent);
        return n;
      }
      return transform(tree);
    }

    Node transformOrEmpty(IdentifierToken token, ParseTree parent) {
      if (token == null) {
        Node n = newNode(Token.EMPTY);
        setSourceInfo(n, parent);
        return n;
      }
      return processName(token, Token.NAME, IdentifierType.STANDARD);
    }

    Node processFunctionCall(CallExpressionTree callNode) {
      Node node = newNode(Token.CALL, transform(callNode.operand));
      node.setTrailingComma(callNode.arguments.hasTrailingComma);
      ArgumentListTree argumentsTree = callNode.arguments;
      // For each arg, represents a location (end SourcePosition) such that all
      // trailing comments before this location correspond to that arg.
      List<SourcePosition> zones =
          getEndOfArgCommentZones(
              argumentsTree.arguments, argumentsTree.commaPositions, argumentsTree.location.end);
      int argCount = 0;
      for (ParseTree child : callNode.arguments.arguments) {
        Node childNode = transform(child);
        node.addChildToBack(childNode);
        // The non-trailing comments are already attached to `childNode` in `transform(child)`
        // call. Now we must attach possible trailing comments to `childNode`.

        attachPossibleTrailingCommentsForArg(childNode, zones.get(argCount));
      }
      annotateCalls(node);
      return node;
    }

    /**
     * There are two types of calls we are interested in calls without explicit "this" values (what
     * we are call "free" calls) and direct call to eval.
     */
    private void annotateCalls(Node n) {
      checkState(n.isCall() || n.isOptChainCall() || n.isTaggedTemplateLit(), n);

      // Keep track of of the "this" context of a call.  A call without an
      // explicit "this" is a free call.
      Node callee = n.getFirstChild();

      // ignore cast nodes.
      while (callee.isCast()) {
        callee = callee.getFirstChild();
      }

      if (!isNormalOrOptChainGet(callee)) {
        // This call originally was not passed a `this` value.
        // Inlining could change the callee into a property reference of some kind.
        // The code printer will recognize the `FREE_CALL` property and wrap the real callee with
        // `(0, real.callee)(args)` when necessary to avoid changing the calling behavior.
        n.putBooleanProp(Node.FREE_CALL, true);

        if (callee.isName() && callee.getString().equals("eval")) {
          // Keep track of the context in which eval is called. It is important
          // to distinguish between "(0, eval)()" and "eval()".
          callee.putBooleanProp(Node.DIRECT_EVAL, true);
        } else if (callee.isComma() && callee.getFirstChild().isNumber()) {
          // The input code may actually already contain calls of the form
          // `(0, real.callee)(arg1, arg2)`. For example, the TypeScript compiler outputs code like
          // this in some cases.
          // This makes it hard for us to connect calls with the called functions, though, so we
          // will simplify these cases. The `FREE_CALL` property we've just applied will tell the
          // code printer to put this wrapping back later.
          final Node realCallee = callee.getSecondChild();
          callee.replaceWith(realCallee.detach());
          // TODO(bradfordcsmith): Why do I get an NPE if I try to report this code change?
        }
      }
    }

    /** Is this a GETPROP, OPTCHAIN_GETPROP, GETELEM, or OPTCHAIN_GETELEM? */
    public boolean isNormalOrOptChainGet(Node n) {
      return n.isGetProp() || n.isGetElem() || n.isOptChainGetProp() || n.isOptChainGetElem();
    }

    /**
     * Calculates, for each arg, a location (end SourcePosition) such that all trailing comments
     * before this location correspond to that arg. Can be used while processing both function calls
     * (ArgsList) as well as declarations (ParamList).
     *
     * @param args list of arguments or formal parameters (ParseTree nodes)
     * @param commaPositions list of SourcePositions corresponding to commas in the argsList or
     *     formal parameter list
     * @param argListEndPosition SourcePosition of the end of argsList or paramList
     */
    List<SourcePosition> getEndOfArgCommentZones(
        ImmutableList<ParseTree> args,
        ImmutableList<SourcePosition> commaPositions,
        SourcePosition argListEndPosition) {
      ImmutableList.Builder<SourcePosition> zones = ImmutableList.builder();
      int commaCount = 0;
      for (ParseTree arg : args) {
        if (args.size() > commaCount + 1) {
          // there is a next arg after this arg
          ParseTree nextParam = args.get(commaCount + 1);
          if (nextParam.location.start.line > arg.location.end.line) {
            // Next arg is on a new line; all trailing comments on this line belong to this arg
            // create a source position to represent the end of current line
            SourcePosition tempSourcePos =
                new SourcePosition(
                    null,
                    /* offset= */ Integer.MAX_VALUE,
                    arg.location.end.line,
                    Integer.MAX_VALUE /*col */);
            zones.add(tempSourcePos);
          } else {
            // Next arg is on the same line; trailing comments before the comma belong to this arg
            SourcePosition commaPosition = commaPositions.get(commaCount);
            zones.add(commaPosition);
          }
        } else {
          // last arg; trailing comments till the end of argList belong to this arg
          zones.add(argListEndPosition);
        }
        commaCount++;
      }
      return zones.build();
    }

    /**
     * Attaches trailing comments associated with this arg or formal param to it.
     *
     * @param paramNode The node to which we're attaching trailing comment
     * @param endZone The end location until which we fetch pending comments for attachment
     */
    void attachPossibleTrailingCommentsForArg(Node paramNode, SourcePosition endZone) {
      NonJSDocComment trailingComment = parseNonJSDocCommentAt(endZone, true);
      if (trailingComment == null) {
        return;
      }
      paramNode.setTrailingNonJSDocComment(trailingComment);
    }

    /**
     * Attaches trailing comments associated with this node.
     *
     * @param node The node to which we're attaching trailing comment
     * @param tokenEnd The end location after which we're looking for a trailing comment
     */
    void attachPossibleTrailingComment(Node node, SourcePosition tokenEnd) {
      // Check for pending comments on the rest of the current line.
      SourcePosition nextLine = new SourcePosition(null, Integer.MAX_VALUE, tokenEnd.line + 1, 0);
      NonJSDocComment trailingComment = parseTrailingNonJSDocCommentAt(tokenEnd, nextLine);
      if (trailingComment == null) {
        return;
      }
      node.setTrailingNonJSDocComment(trailingComment);
    }

    void addExtraTrailingComment(Node node, NonJSDocComment lastComment) {
      if (lastComment == null) {
        return;
      }
      if (!node.hasChildren()) {
        node.setTrailingNonJSDocComment(lastComment);
        return;
      }

      Node lastChild = node.getLastChild();
      NonJSDocComment currentComment = lastChild.getTrailingNonJSDocComment();

      if (currentComment == null) {
        // We add a line break here as this is cannot be a trailing comment on the same line as the
        // last child.
        // TODO(user): pass in proper child tree end position to get the number of line breaks
        // right.
        SourcePosition newStart =
            new SourcePosition(
                /* file */ null,
                lastComment.getEndPosition().offset - 1,
                lastComment.getEndPosition().line - 1,
                0);

        NonJSDocComment newlineComment =
            new NonJSDocComment(
                newStart, lastComment.getEndPosition(), "\n" + lastComment.getCommentString());
        node.getLastChild().setTrailingNonJSDocComment(newlineComment);
        return;
      }

      int blankLines = lastComment.getStartPosition().line - currentComment.getEndPosition().line;
      int numWhiteSpace = 0;
      if (blankLines == 0) {
        numWhiteSpace =
            lastComment.getStartPosition().column - currentComment.getEndPosition().column - 1;
      }

      StringBuilder comment = new StringBuilder().append(currentComment.getCommentString());
      for (int i = 0; i < numWhiteSpace; i++) {
        comment.append(" ");
      }
      for (int i = 0; i < blankLines; i++) {
        comment.append("\n");
      }
      comment.append(lastComment.getCommentString());

      NonJSDocComment allComments =
          new NonJSDocComment(
              currentComment.getStartPosition(), lastComment.getEndPosition(), comment.toString());
      allComments.setEndsAsLineComment(lastComment.isEndingAsLineComment());
      allComments.setIsInline(true);
      node.getLastChild().setTrailingNonJSDocComment(allComments);
    }

    Node processOptChainFunctionCall(OptChainCallExpressionTree callNode) {
      maybeWarnForFeature(callNode, Feature.OPTIONAL_CHAINING);
      Node node = newNode(Token.OPTCHAIN_CALL, transform(callNode.operand));
      node.setTrailingComma(callNode.hasTrailingComma);
      for (ParseTree child : callNode.arguments.arguments) {
        node.addChildToBack(transform(child));
      }
      node.setIsOptionalChainStart(callNode.isStartOfOptionalChain);
      annotateCalls(node);
      return node;
    }

    Node processFunction(FunctionDeclarationTree functionTree) {
      boolean isDeclaration = (functionTree.kind == FunctionDeclarationTree.Kind.DECLARATION);
      boolean isMember = (functionTree.kind == FunctionDeclarationTree.Kind.MEMBER);
      boolean isArrow = (functionTree.kind == FunctionDeclarationTree.Kind.ARROW);
      boolean isAsync = functionTree.isAsync;
      boolean isGenerator = functionTree.isGenerator;
      boolean isSignature = (functionTree.functionBody.type == ParseTreeType.EMPTY_STATEMENT);

      IdentifierType identifierType =
          functionTree.isClassMember ? IdentifierType.CAN_BE_PRIVATE : IdentifierType.STANDARD;

      if (isGenerator) {
        maybeWarnForFeature(functionTree, Feature.GENERATORS);
      }

      if (isMember) {
        maybeWarnForFeature(functionTree, Feature.MEMBER_DECLARATIONS);
      }

      if (isArrow) {
        maybeWarnForFeature(functionTree, Feature.ARROW_FUNCTIONS);
      }

      if (isAsync) {
        maybeWarnForFeature(functionTree, Feature.ASYNC_FUNCTIONS);
      }

      if (isGenerator && isAsync) {
        maybeWarnForFeature(functionTree, Feature.ASYNC_GENERATORS);
      }

      IdentifierToken name = functionTree.name;
      Node newName;
      if (name != null) {
        newName = processNameWithInlineComments(name, identifierType);
      } else {
        if (isDeclaration || isMember) {
          errorReporter.error(
              "unnamed function statement", sourceName, lineno(functionTree), charno(functionTree));

          // Return the bare minimum to put the AST in a valid state.
          newName = createMissingNameNode();
        } else {
          newName = newStringNode(Token.NAME, "");
        }

        // Old Rhino tagged the empty name node with the line number of the
        // declaration.
        setSourceInfo(newName, functionTree);
      }

      Node node = newNode(Token.FUNCTION);
      if (isMember) {
        newName.setString("");
      }
      node.addChildToBack(newName);

      node.addChildToBack(transform(functionTree.formalParameterList));

      Node bodyNode = transform(functionTree.functionBody);
      if (!isArrow && !isSignature && !bodyNode.isBlock()) {
        // When in "keep going" mode the parser tries to parse some constructs the
        // compiler doesn't support, repair it here.
        checkState(config.runMode() == Config.RunMode.KEEP_GOING);
        bodyNode = IR.block();
      }
      parseDirectives(bodyNode);
      node.addChildToBack(bodyNode);

      node.setIsGeneratorFunction(isGenerator);
      node.setIsArrowFunction(isArrow);
      node.setIsAsyncFunction(isAsync);
      node.putBooleanProp(Node.OPT_ES6_TYPED, functionTree.isOptional);

      attachPossibleTrailingComment(node, functionTree.getEnd());

      Node result;

      if (isMember) {
        setSourceInfo(node, functionTree);
        Node member = newStringNodeFromIdentifier(Token.MEMBER_FUNCTION_DEF, identifierType, name);
        member.addChildToBack(node);
        member.setStaticMember(functionTree.isStatic);
        // The source info should only include the identifier, not the entire function expression
        setSourceInfo(member, name);
        result = member;
      } else {
        result = node;
      }

      return result;
    }

    // class fields: general case
    Node processField(FieldDeclarationTree tree) {
      maybeWarnForFeature(tree, Feature.PUBLIC_CLASS_FIELDS);

      Node node =
          maybeAddNonJsDocComment(
              newStringNodeFromIdentifier(
                  Token.MEMBER_FIELD_DEF, IdentifierType.CAN_BE_PRIVATE, tree.name),
              tree.getStart());
      if (tree.initializer != null) {
        Node initializer = transform(tree.initializer);
        node.addChildToBack(initializer);
        setLength(node, tree.location.start, tree.location.end);
      }

      node.putBooleanProp(Node.STATIC_MEMBER, tree.isStatic);

      return node;
    }

    // class fields: computed property fields
    Node processComputedPropertyField(ComputedPropertyFieldTree tree) {
      maybeWarnForFeature(tree, Feature.PUBLIC_CLASS_FIELDS);

      Node key = transform(tree.property);

      Node node =
          (tree.initializer != null)
              ? newNode(Token.COMPUTED_FIELD_DEF, key, transform(tree.initializer))
              : newNode(Token.COMPUTED_FIELD_DEF, key);

      node.putBooleanProp(Node.STATIC_MEMBER, tree.isStatic);

      return node;
    }

    Node processFormalParameterList(FormalParameterListTree tree) {
      Node params = newNode(Token.PARAM_LIST);
      params.setTrailingComma(tree.hasTrailingComma);
      if (!checkParameters(tree)) {
        return params;
      }

      ImmutableList<ParseTree> parameters = tree.parameters;
      List<SourcePosition> zones =
          getEndOfArgCommentZones(parameters, tree.commaPositions, tree.location.end);
      int argCount = 0;

      for (ParseTree param : tree.parameters) {
        final Node paramNode;
        switch (param.type) {
          case DEFAULT_PARAMETER ->
              // processDefaultParameter() knows how to find and apply inline JSDoc to the right
              // node
              paramNode = processDefaultParameter(param.asDefaultParameter());
          case ITER_REST -> {
            maybeWarnForFeature(param, Feature.REST_PARAMETERS);
            paramNode = transformNodeWithInlineComments(param);
          }
          default -> {
            paramNode = transformNodeWithInlineComments(param);
            // Reusing the logic to attach trailing comments used from call-site argsList
            attachPossibleTrailingCommentsForArg(paramNode, zones.get(argCount));
          }
        }

        // Children must be simple names, default parameters, rest
        // parameters, or destructuring patterns.
        checkState(
            paramNode.isName()
                || paramNode.isRest()
                || paramNode.isArrayPattern()
                || paramNode.isObjectPattern()
                || paramNode.isDefaultValue());
        params.addChildToBack(paramNode);
        argCount++;
      }

      return params;
    }

    Node processDefaultParameter(DefaultParameterTree tree) {
      maybeWarnForFeature(tree, Feature.DEFAULT_PARAMETERS);
      ParseTree targetTree = tree.lhs;
      Node targetNode;
      if (targetTree.type == ParseTreeType.IDENTIFIER_EXPRESSION) {
        // allow inline JSDoc on an identifier
        // let { /** inlineType */ x = defaultValue } = someObject;
        // TODO(bradfordcsmith): Do we need to allow inline JSDoc for qualified names, too?
        targetNode =
            processNameWithInlineComments(
                targetTree.asIdentifierExpression(), IdentifierType.STANDARD);
      } else {
        // ({prop: /** string */ ns.a.b = 'foo'} = someObject);
        // NOTE: CheckJSDoc will report an error for this case, since we want qualified names to be
        // declared with individual statements, like `/** @type {string} */ ns.a.b;`
        targetNode = transformNodeWithInlineComments(targetTree);
      }
      final Node defaultValueExpression = transform(tree.defaultValue);
      Node defaultValueNode = newNode(Token.DEFAULT_VALUE, targetNode, defaultValueExpression);
      reportErrorIfYieldOrAwaitInDefaultValue(defaultValueNode);
      setSourceInfo(defaultValueNode, tree);
      return defaultValueNode;
    }

    Node processIterRest(IterRestTree tree) {
      Node target = transformNodeWithInlineComments(tree.assignmentTarget);
      return newNode(Token.ITER_REST, target);
    }

    Node processIterSpread(IterSpreadTree tree) {
      maybeWarnForFeature(tree, Feature.SPREAD_EXPRESSIONS);

      return newNode(Token.ITER_SPREAD, transform(tree.expression));
    }

    Node processObjectSpread(ObjectSpreadTree tree) {
      maybeWarnForFeature(tree, Feature.OBJECT_LITERALS_WITH_SPREAD);

      return newNode(Token.OBJECT_SPREAD, transform(tree.expression));
    }

    Node processIfStatement(IfStatementTree statementNode) {
      Node node = newNode(Token.IF);
      node.addChildToBack(transform(statementNode.condition));
      node.addChildToBack(transformBlock(statementNode.ifClause));
      if (statementNode.elseClause != null) {
        node.addChildToBack(transformBlock(statementNode.elseClause));
      }
      return node;
    }

    private void markBinaryExpressionFeatures(BinaryOperatorTree exprNode) {
      switch (exprNode.operator.type) {
        case STAR_STAR:
        case STAR_STAR_EQUAL:
          maybeWarnForFeature(exprNode, Feature.EXPONENT_OP);
          break;
        case QUESTION_QUESTION:
          maybeWarnForFeature(exprNode, Feature.NULL_COALESCE_OP);
          break;
        case QUESTION_QUESTION_EQUAL:
          maybeWarnForFeature(exprNode, Feature.NULL_COALESCE_OP);
        // fall through
        case OR_EQUAL:
        case AND_EQUAL:
          maybeWarnForFeature(exprNode, Feature.LOGICAL_ASSIGNMENT);
          break;
        default:
          break;
      }
    }

    Node processBinaryExpression(BinaryOperatorTree exprNode) {
      if (jsdocTracker.hasPendingCommentBefore(exprNode.right.location.start)
          || nonJsdocTracker.hasPendingCommentBefore(exprNode.right.location.start)) {
        markBinaryExpressionFeatures(exprNode);
        return newNode(
            transformBinaryTokenType(exprNode.operator.type),
            transformLhsOfBinaryTree(exprNode),
            transform(exprNode.right));
      } else {
        // No pending comments, we can traverse out of order.
        return processBinaryExpressionHelper(exprNode);
      }
    }

    // Deep binary ops (typical string concatentations) can cause stack overflows,
    // avoid recursing in this case and loop instead.
    private Node processBinaryExpressionHelper(BinaryOperatorTree exprTree) {
      Node root = null;
      Node current = null;
      Node previous = null;
      while (exprTree != null) {
        markBinaryExpressionFeatures(exprTree);
        previous = current;
        // Skip the first child but recurse normally into the right operand as typically this isn't
        // deep and because we have already checked that there isn't any JSDoc we can traverse
        // out of order.
        current =
            newNode(transformBinaryTokenType(exprTree.operator.type), transform(exprTree.right));
        // We have inlined "transform" here. Normally, we would need to handle the JSDoc here but we
        // know there is no JSDoc to attach, which simplifies things.
        setSourceInfo(current, exprTree);

        // As the iteration continues add the left operand.
        if (previous != null) {
          previous.addChildToFront(current);
        }

        if (exprTree.left instanceof BinaryOperatorTree binaryOperatorTree) {
          // continue with the left hand child
          exprTree = binaryOperatorTree;
        } else {
          // Finish things off, add the left operand to the current node.
          Node leftNode = transformLhsOfBinaryTree(exprTree);
          current.addChildToFront(leftNode);
          // Nothing left to do.
          exprTree = null;
        }

        // Save the top binary op, this is the result to return.
        if (root == null) {
          root = current;
        }
      }
      return root;
    }

    private Node transformLhsOfBinaryTree(BinaryOperatorTree exprTree) {
      boolean lhsOfInIsPrivateId =
          exprTree.operator.type == TokenType.IN
              && exprTree.left instanceof IdentifierExpressionTree
              && exprTree.left.asIdentifierExpression().identifierToken.isPrivateIdentifier();

      try (AutoDecrement ignored = privateIdLhsOfInScope.maybeIncrement(lhsOfInIsPrivateId)) {
        return transform(exprTree.left);
      }
    }

    @SuppressWarnings("unused") // for symmetry all the process* methods take a ParseTree
    Node processDebuggerStatement(DebuggerStatementTree unused) {
      return newNode(Token.DEBUGGER);
    }

    @SuppressWarnings("unused") // for symmetry all the process* methods take a ParseTree
    Node processThisExpression(ThisExpressionTree unused) {
      return newNode(Token.THIS);
    }

    Node processLabeledStatement(LabelledStatementTree labelTree) {
      Node statement = transform(labelTree.statement);
      if (statement.isFunction()
          || statement.isClass()
          || statement.isLet()
          || statement.isConst()) {
        errorReporter.error(
            "Lexical declarations are only allowed at top level or inside a block.",
            sourceName,
            lineno(labelTree),
            charno(labelTree));
        return statement; // drop the LABEL node so that the resulting AST is valid
      }
      return newNode(Token.LABEL, transformLabelName(labelTree.name), statement);
    }

    Node processName(IdentifierExpressionTree nameNode, IdentifierType identifierType) {
      return processName(nameNode.identifierToken, Token.NAME, identifierType);
    }

    Node processName(IdentifierToken identifierToken, Token output, IdentifierType identifierType) {
      NonJSDocComment comment = parseNonJSDocCommentAt(identifierToken.getStart(), true);

      Node node = newStringNodeFromIdentifier(output, identifierType, identifierToken);

      if (output == Token.NAME) {
        maybeWarnReservedKeyword(identifierToken);

        JSDocInfo info = parseJSDocInfoOnToken(identifierToken);
        if (info != null) {
          node.setJSDocInfo(info);
        }
      }

      if (comment != null) {
        node.setNonJSDocComment(comment);
      }
      setSourceInfo(node, identifierToken);
      return node;
    }

    Node processString(LiteralToken token) {
      checkArgument(token.type == TokenType.STRING);
      NonJSDocComment comment = parseNonJSDocCommentAt(token.getStart(), true);

      Node node = newStringNode(Token.STRINGLIT, normalizeString(token, false));

      if (comment != null) {
        node.setNonJSDocComment(comment);
      }
      setSourceInfo(node, token);
      return node;
    }

    Node processTemplateLiteralToken(TemplateLiteralToken token) {
      checkArgument(
          token.type == TokenType.NO_SUBSTITUTION_TEMPLATE
              || token.type == TokenType.TEMPLATE_HEAD
              || token.type == TokenType.TEMPLATE_MIDDLE
              || token.type == TokenType.TEMPLATE_TAIL);
      Node node;
      if (token.hasError()) {
        node = newTemplateLitStringNode(null, token.value);
      } else {
        node = newTemplateLitStringNode(normalizeString(token, true), token.value);
      }
      setSourceInfo(node, token);
      return node;
    }

    private Node processNameWithInlineComments(
        IdentifierExpressionTree identifierExpression, IdentifierType identifierType) {
      return processNameWithInlineComments(identifierExpression.identifierToken, identifierType);
    }

    Node processNameWithInlineComments(
        IdentifierToken identifierToken, IdentifierType identifierType) {
      JSDocInfo info = parseInlineJSDocAt(identifierToken.getStart());
      NonJSDocComment comment = parseNonJSDocCommentAt(identifierToken.getStart(), false);

      maybeWarnReservedKeyword(identifierToken);
      Node node = newStringNodeFromIdentifier(Token.NAME, identifierType, identifierToken);

      if (info != null) {
        node.setJSDocInfo(info);
      }
      if (comment != null) {
        node.setNonJSDocComment(comment);
      }
      setSourceInfo(node, identifierToken);
      return node;
    }

    private void maybeWarnKeywordProperty(Node node) {
      if (currentFileIsExterns) {
        return;
      }
      if (!TokenStream.isKeyword(node.getString())) {
        return;
      }

      features = features.with(Feature.KEYWORDS_AS_PROPERTIES);
      if (config.languageMode() == LanguageMode.ECMASCRIPT3) {
        errorReporter.warning(
            INVALID_ES3_PROP_NAME, sourceName, node.getLineno(), node.getCharno());
      }
    }

    private void maybeWarnReservedKeyword(IdentifierToken token) {
      // We're just checking for keywords here, not actually using the identifier so we don't care
      // about potentially running into a misplaced private identifier.
      String identifier = token.getMaybePrivateValue();
      boolean isIdentifier = false;
      if (TokenStream.isKeyword(identifier)) {
        features = features.with(Feature.ES3_KEYWORDS_AS_IDENTIFIERS);
        isIdentifier = config.languageMode() == LanguageMode.ECMASCRIPT3;
      }
      if (reservedKeywords != null && reservedKeywords.contains(identifier)) {
        features = features.with(Feature.KEYWORDS_AS_PROPERTIES);
        isIdentifier = config.languageMode() == LanguageMode.ECMASCRIPT3;
      }
      if (isIdentifier) {
        errorReporter.error(
            "identifier is a reserved word",
            sourceName,
            lineno(token.location.start),
            charno(token.location.start));
      }
    }

    Node processNewExpression(NewExpressionTree exprNode) {
      Node node = newNode(Token.NEW, transform(exprNode.operand));
      node.setTrailingComma(exprNode.hasTrailingComma);
      if (exprNode.arguments != null) {
        for (ParseTree arg : exprNode.arguments.arguments) {
          node.addChildToBack(transform(arg));
        }
      }
      return node;
    }

    Node processNumberLiteral(LiteralExpressionTree literalNode) {
      double value = normalizeNumber(literalNode.literalToken.asLiteral());
      Node number = newNumberNode(value);
      setSourceInfo(number, literalNode);
      return number;
    }

    Node processBigIntLiteral(LiteralExpressionTree literalNode) {
      maybeWarnForFeature(literalNode, Feature.BIGINT);
      BigInteger value = normalizeBigInt(literalNode.literalToken.asLiteral());
      Node bigint = newBigIntNode(value);
      setSourceInfo(bigint, literalNode);
      return bigint;
    }

    Node processObjectLiteral(ObjectLiteralExpressionTree objTree) {
      Node node = newNode(Token.OBJECTLIT);
      node.setTrailingComma(objTree.hasTrailingComma);
      boolean maybeWarn = false;
      for (ParseTree el : objTree.propertyNameAndValues) {
        if (el.type == ParseTreeType.DEFAULT_PARAMETER) {
          // (e.g. var o = { x=4 };) This is only parsed for compatibility with object patterns.
          errorReporter.error(
              "Default value cannot appear at top level of an object literal.",
              sourceName,
              lineno(el),
              0);
          continue;
        } else if (el.type == ParseTreeType.GET_ACCESSOR && maybeReportGetter(el)) {
          continue;
        } else if (el.type == ParseTreeType.SET_ACCESSOR && maybeReportSetter(el)) {
          continue;
        }

        Node key = transform(el);
        if (!key.isComputedProp()
            && !key.isQuotedStringKey()
            && !key.isSpread()
            && !currentFileIsExterns) {
          maybeWarnKeywordProperty(key);
        }
        if (key.isShorthandProperty()) {
          maybeWarn = true;
        }

        node.addChildToBack(key);
      }
      if (maybeWarn) {
        maybeWarnForFeature(objTree, Feature.SHORTHAND_OBJECT_PROPERTIES);
      }
      return node;
    }

    Node processComputedPropertyDefinition(ComputedPropertyDefinitionTree tree) {
      maybeWarnForFeature(tree, Feature.COMPUTED_PROPERTIES);

      return newNode(Token.COMPUTED_PROP, transform(tree.property), transform(tree.value));
    }

    Node processComputedPropertyMethod(ComputedPropertyMethodTree tree) {
      maybeWarnForFeature(tree, Feature.COMPUTED_PROPERTIES);

      Node n = newNode(Token.COMPUTED_PROP, transform(tree.property), transform(tree.method));
      n.putBooleanProp(Node.COMPUTED_PROP_METHOD, true);
      if (tree.method.asFunctionDeclaration().isStatic) {
        n.setStaticMember(true);
      }
      return n;
    }

    Node processComputedPropertyGetter(ComputedPropertyGetterTree tree) {
      maybeWarnForFeature(tree, Feature.COMPUTED_PROPERTIES);

      Node key = transform(tree.property);
      Node body = transform(tree.body);
      Node function = IR.function(IR.name(""), IR.paramList(), body);
      function.srcrefTreeIfMissing(body);
      Node n = newNode(Token.COMPUTED_PROP, key, function);
      n.putBooleanProp(Node.COMPUTED_PROP_GETTER, true);
      n.putBooleanProp(Node.STATIC_MEMBER, tree.isStatic);
      return n;
    }

    Node processComputedPropertySetter(ComputedPropertySetterTree tree) {
      maybeWarnForFeature(tree, Feature.COMPUTED_PROPERTIES);

      Node key = transform(tree.property);

      Node paramList = processFormalParameterList(tree.parameter);
      setSourceInfo(paramList, tree.parameter);

      Node body = transform(tree.body);

      Node function = IR.function(IR.name(""), paramList, body);
      function.srcrefTreeIfMissing(body);

      Node n = newNode(Token.COMPUTED_PROP, key, function);
      n.putBooleanProp(Node.COMPUTED_PROP_SETTER, true);
      n.putBooleanProp(Node.STATIC_MEMBER, tree.isStatic);
      return n;
    }

    Node processGetAccessor(GetAccessorTree tree) {
      Node key =
          processObjectLitKey(
              tree.propertyName,
              Token.GETTER_DEF,
              tree.isClassMember ? IdentifierType.CAN_BE_PRIVATE : IdentifierType.STANDARD);
      Node body = transform(tree.body);
      Node dummyName = newStringNode(Token.NAME, "");
      setSourceInfo(dummyName, tree.body);
      Node paramList = newNode(Token.PARAM_LIST);
      setSourceInfo(paramList, tree.body);
      Node value = newNode(Token.FUNCTION, dummyName, paramList, body);
      setSourceInfo(value, tree.body);
      key.addChildToFront(value);
      key.setStaticMember(tree.isStatic);
      return key;
    }

    Node processSetAccessor(SetAccessorTree tree) {
      Node key =
          processObjectLitKey(
              tree.propertyName,
              Token.SETTER_DEF,
              tree.isClassMember ? IdentifierType.CAN_BE_PRIVATE : IdentifierType.STANDARD);

      Node paramList = processFormalParameterList(tree.parameter);
      setSourceInfo(paramList, tree.parameter);

      Node body = transform(tree.body);

      Node dummyName = newStringNode(Token.NAME, "");
      setSourceInfo(dummyName, tree.propertyName);

      Node value = newNode(Token.FUNCTION, dummyName, paramList, body);
      setSourceInfo(value, tree.body);

      key.addChildToFront(value);
      key.setStaticMember(tree.isStatic);
      return key;
    }

    Node processPropertyNameAssignment(PropertyNameAssignmentTree tree) {
      Node key = processObjectLitKey(tree.name, Token.STRING_KEY, IdentifierType.STANDARD);
      if (tree.value != null) {
        key.addChildToFront(transform(tree.value));
      } else {
        Node value =
            maybeAddNonJsDocComment(
                    newStringNode(Token.NAME, key.getString()), tree.name.getStart())
                .srcref(key);
        key.setShorthandProperty(true);
        key.addChildToFront(value);
      }
      return key;
    }

    private void checkParenthesizedExpression(ParenExpressionTree exprNode) {
      if (exprNode.expression.type == ParseTreeType.COMMA_EXPRESSION) {
        ImmutableList<ParseTree> commaNodes = exprNode.expression.asCommaExpression().expressions;
        ParseTree lastChild = Iterables.getLast(commaNodes);
        if (lastChild.type == ParseTreeType.ITER_REST) {
          errorReporter.error(
              "A rest parameter must be in a parameter list.",
              sourceName,
              lineno(lastChild),
              charno(lastChild));
        }
      }
    }

    Node processParenthesizedExpression(ParenExpressionTree exprNode) {
      checkParenthesizedExpression(exprNode);
      Node expr = transform(exprNode.expression);
      expr.setIsParenthesized(true);
      return expr;
    }

    Node processPropertyGet(MemberExpressionTree getNode) {
      Node leftChild = transform(getNode.operand);
      IdentifierToken propName = getNode.memberName;
      if (propName == null) {
        return leftChild;
      }

      Node getProp =
          maybeAddNonJsDocComment(
              newStringNodeFromIdentifier(Token.GETPROP, IdentifierType.CAN_BE_PRIVATE, propName),
              getNode.memberName.getStart());
      getProp.addChildToBack(leftChild);
      setSourceInfo(getProp, propName);
      maybeWarnKeywordProperty(getProp);
      return getProp;
    }

    Node processOptChainPropertyGet(OptionalMemberExpressionTree getNode) {
      maybeWarnForFeature(getNode, Feature.OPTIONAL_CHAINING);

      Node leftChild = transform(getNode.operand);
      IdentifierToken propName = getNode.memberName;
      if (propName == null) {
        return leftChild;
      }

      Node getProp =
          maybeAddNonJsDocComment(
              newStringNodeFromIdentifier(
                  Token.OPTCHAIN_GETPROP, IdentifierType.CAN_BE_PRIVATE, propName),
              getNode.memberName.getStart());
      getProp.addChildToBack(leftChild);
      getProp.setIsOptionalChainStart(getNode.isStartOfOptionalChain);
      setSourceInfo(getProp, propName);
      maybeWarnKeywordProperty(getProp);
      return getProp;
    }

    Node processRegExpLiteral(LiteralExpressionTree literalTree) {
      LiteralToken token = literalTree.literalToken.asLiteral();
      Node literalStringNode = newStringNode(normalizeRegex(token));
      // TODO(johnlenz): fix the source location.
      setSourceInfo(literalStringNode, token);
      Node node = newNode(Token.REGEXP, literalStringNode);

      String rawRegex = token.value;
      int lastSlash = rawRegex.lastIndexOf('/');
      String flags = "";
      if (lastSlash < rawRegex.length()) {
        flags = rawRegex.substring(lastSlash + 1);
      }
      validateRegExpFlags(literalTree, flags);

      if (!flags.isEmpty()) {
        Node flagsNode = newStringNode(flags);
        // TODO(johnlenz): fix the source location.
        setSourceInfo(flagsNode, token);
        node.addChildToBack(flagsNode);
      }
      return node;
    }

    private void validateRegExpFlags(LiteralExpressionTree tree, String flags) {
      for (char flag : Lists.charactersOf(flags)) {
        switch (flag) {
          case 'g', 'i', 'm' -> {}
          case 'u', 'y' -> {
            Feature feature = flag == 'u' ? Feature.REGEXP_FLAG_U : Feature.REGEXP_FLAG_Y;
            maybeWarnForFeature(tree, feature);
          }
          case 's' -> maybeWarnForFeature(tree, Feature.REGEXP_FLAG_S);
          case 'd' -> maybeWarnForFeature(tree, Feature.REGEXP_FLAG_D);
          default ->
              errorReporter.error(
                  "Invalid RegExp flag '" + flag + "'", sourceName, lineno(tree), charno(tree));
        }
      }
    }

    Node processReturnStatement(ReturnStatementTree statementNode) {
      Node node = newNode(Token.RETURN);
      if (statementNode.expression != null) {
        node.addChildToBack(transform(statementNode.expression));
      }
      return node;
    }

    Node processStringLiteral(LiteralExpressionTree literalTree) {
      LiteralToken token = literalTree.literalToken.asLiteral();
      return processString(token);
    }

    Node processTemplateLiteral(TemplateLiteralExpressionTree tree) {
      maybeWarnForFeature(tree, Feature.TEMPLATE_LITERALS);
      Node templateLitNode = newNode(Token.TEMPLATELIT);
      setSourceInfo(templateLitNode, tree);
      Node node =
          tree.operand == null
              ? templateLitNode
              : newNode(Token.TAGGED_TEMPLATELIT, transform(tree.operand), templateLitNode);
      for (ParseTree child : tree.elements) {
        templateLitNode.addChildToBack(transform(child));
      }
      if (node.isTaggedTemplateLit()) {
        annotateCalls(node);
      }
      return node;
    }

    Node processTemplateLiteralPortion(TemplateLiteralPortionTree tree) {
      return processTemplateLiteralToken(tree.value.asTemplateLiteral());
    }

    Node processTemplateSubstitution(TemplateSubstitutionTree tree) {
      return newNode(Token.TEMPLATELIT_SUB, transform(tree.expression));
    }

    Node processSwitchCase(CaseClauseTree caseNode) {
      ParseTree expr = caseNode.expression;
      Node node = newNode(Token.CASE, transform(expr));
      Node block = newNode(Token.BLOCK);
      block.setIsAddedBlock(true);
      setSourceInfo(block, caseNode);
      if (caseNode.statements != null) {
        for (ParseTree child : caseNode.statements) {
          block.addChildToBack(transform(child));
        }
      }
      node.addChildToBack(block);
      return node;
    }

    Node processSwitchDefault(DefaultClauseTree caseNode) {
      Node node = newNode(Token.DEFAULT_CASE);
      Node block = newNode(Token.BLOCK);
      block.setIsAddedBlock(true);
      setSourceInfo(block, caseNode);
      if (caseNode.statements != null) {
        for (ParseTree child : caseNode.statements) {
          block.addChildToBack(transform(child));
        }
      }
      node.addChildToBack(block);
      return node;
    }

    Node processSwitchStatement(SwitchStatementTree statementNode) {
      Node node = newNode(Token.SWITCH, transform(statementNode.expression));
      Node switchBody = newNode(Token.SWITCH_BODY);
      setSourceInfo(switchBody, statementNode);
      node.addChildToBack(switchBody);
      for (ParseTree child : statementNode.caseClauses) {
        switchBody.addChildToBack(transform(child));
      }
      return node;
    }

    Node processThrowStatement(ThrowStatementTree statementNode) {
      return newNode(Token.THROW, transform(statementNode.value));
    }

    Node processTryStatement(TryStatementTree statementNode) {
      Node node = newNode(Token.TRY, transformBlock(statementNode.body));
      Node block = newNode(Token.BLOCK);
      boolean lineSet = false;

      ParseTree cc = statementNode.catchBlock;
      if (cc != null) {
        // Mark the enclosing block at the same line as the first catch
        // clause.
        setSourceInfo(block, cc);
        lineSet = true;
        block.addChildToBack(transform(cc));
      }

      node.addChildToBack(block);

      ParseTree finallyBlock = statementNode.finallyBlock;
      if (finallyBlock != null) {
        node.addChildToBack(transformBlock(finallyBlock));
      }

      // If we didn't set the line on the catch clause, then
      // we've got an empty catch clause.  Set its line to be the same
      // as the finally block (to match Old Rhino's behavior.)
      if (!lineSet && (finallyBlock != null)) {
        setSourceInfo(block, finallyBlock);
      }

      return node;
    }

    Node processCatchClause(CatchTree clauseNode) {
      if (clauseNode.exception.type == ParseTreeType.EMPTY_STATEMENT) {
        maybeWarnForFeature(clauseNode, Feature.OPTIONAL_CATCH_BINDING);
      }

      return newNode(
          Token.CATCH, transform(clauseNode.exception), transformBlock(clauseNode.catchBody));
    }

    Node processFinally(FinallyTree finallyNode) {
      return transformBlock(finallyNode.block);
    }

    Node processUnaryExpression(UnaryExpressionTree exprNode) {
      Token type = transformUnaryTokenType(exprNode.operator.type);
      Node operand = transform(exprNode.operand);

      switch (type) {
        case DELPROP -> {
          if (!(operand.isGetProp()
              || operand.isGetElem()
              || operand.isName()
              || operand.isOptChainGetProp()
              || operand.isOptChainGetElem())) {
            String msg = "Invalid delete operand. Only properties can be deleted.";
            errorReporter.error(msg, sourceName, operand.getLineno(), 0);
          }
        }
        case POS -> {
          if (operand.isBigInt()) {
            errorReporter.error(
                "Cannot convert a BigInt value to a number", sourceName, operand.getLineno(), 0);
          }
        }
        default -> {}
      }

      return newNode(type, operand);
    }

    Node processUpdateExpression(UpdateExpressionTree updateExpr) {
      Token type = transformUpdateTokenType(updateExpr.operator.type);
      Node operand = transform(updateExpr.operand);
      return createUpdateNode(
          type, updateExpr.operatorPosition == OperatorPosition.POSTFIX, operand);
    }

    private Node createUpdateNode(Token type, boolean postfix, Node operand) {
      Node assignTarget = operand.isCast() ? operand.getFirstChild() : operand;
      if (!assignTarget.isValidAssignmentTarget()) {
        errorReporter.error(
            String.format(
                "Invalid %s %s operand.",
                (postfix ? "postfix" : "prefix"), (type == Token.INC ? "increment" : "decrement")),
            sourceName,
            operand.getLineno(),
            operand.getCharno());
      }
      Node node = newNode(type, operand);
      node.putBooleanProp(Node.INCRDECR_PROP, postfix);
      return node;
    }

    Node processVariableStatement(VariableStatementTree stmt) {
      // TODO(moz): Figure out why we still need the special handling
      return transformDispatcher.process(stmt.declarations);
    }

    Node processVariableDeclarationList(VariableDeclarationListTree decl) {
      Token declType;
      switch (decl.declarationType) {
        case CONST -> {
          maybeWarnForFeature(decl, Feature.CONST_DECLARATIONS);
          declType = Token.CONST;
        }
        case LET -> {
          maybeWarnForFeature(decl, Feature.LET_DECLARATIONS);
          declType = Token.LET;
        }
        case VAR -> declType = Token.VAR;
        default -> throw new IllegalStateException();
      }

      Node node = newNode(declType);
      for (VariableDeclarationTree child : decl.declarations) {
        node.addChildToBack(transformNodeWithInlineComments(child));
      }

      attachPossibleTrailingComment(node, decl.getEnd());
      return node;
    }

    Node processVariableDeclaration(VariableDeclarationTree decl) {
      Node node = transformNodeWithInlineComments(decl.lvalue);
      Node lhs = node.isDestructuringPattern() ? newNode(Token.DESTRUCTURING_LHS, node) : node;
      if (decl.initializer != null) {
        Node initializer = transform(decl.initializer);
        lhs.addChildToBack(initializer);
        setLength(lhs, decl.location.start, decl.location.end);
      }
      return lhs;
    }

    Node processWhileLoop(WhileStatementTree stmt) {
      return newNode(Token.WHILE, transform(stmt.condition), transformBlock(stmt.body));
    }

    Node processWithStatement(WithStatementTree stmt) {
      return newNode(Token.WITH, transform(stmt.expression), transformBlock(stmt.body));
    }

    @SuppressWarnings("unused") // for symmetry all the process* methods take a ParseTree
    Node processMissingExpression(MissingPrimaryExpressionTree unused) {
      // This will already have been reported as an error by the parser.
      // Try to create something valid that ide mode might be able to
      // continue with.
      return createMissingExpressionNode();
    }

    private Node createMissingNameNode() {
      return newStringNode(Token.NAME, "__missing_name__");
    }

    private Node createMissingExpressionNode() {
      return newStringNode(Token.NAME, "__missing_expression__");
    }

    Node processIllegalToken(ParseTree node) {
      errorReporter.error("Unsupported syntax: " + node.type, sourceName, lineno(node), 0);
      return newNode(Token.EMPTY);
    }

    /** Reports an illegal getter and returns true if the language mode is too low. */
    boolean maybeReportGetter(ParseTree node) {
      features = features.with(Feature.GETTER);
      if (config.languageMode() == LanguageMode.ECMASCRIPT3) {
        errorReporter.error(GETTER_ERROR_MESSAGE, sourceName, lineno(node), 0);
        return true;
      }
      return false;
    }

    /** Reports an illegal setter and returns true if the language mode is too low. */
    boolean maybeReportSetter(ParseTree node) {
      features = features.with(Feature.SETTER);
      if (config.languageMode() == LanguageMode.ECMASCRIPT3) {
        errorReporter.error(SETTER_ERROR_MESSAGE, sourceName, lineno(node), 0);
        return true;
      }
      return false;
    }

    Node processBooleanLiteral(LiteralExpressionTree literal) {
      return newNode(transformBooleanTokenType(literal.literalToken.type));
    }

    @SuppressWarnings("unused") // for symmetry all the process* methods take a ParseTree
    Node processNullLiteral(LiteralExpressionTree unused) {
      return newNode(Token.NULL);
    }

    @SuppressWarnings("unused") // for symmetry all the process* methods take a ParseTree
    Node processNull(NullTree unused) {
      // NOTE: This is not a NULL literal but a placeholder node such as in
      // an array with "holes".
      return newNode(Token.EMPTY);
    }

    Node processCommaExpression(CommaExpressionTree tree) {
      Node root = newNode(Token.COMMA);
      SourcePosition start = tree.expressions.get(0).location.start;
      SourcePosition end = tree.expressions.get(1).location.end;
      setSourceInfo(root, start, end);
      for (ParseTree expr : tree.expressions) {
        int count = root.getChildCount();
        if (count < 2) {
          root.addChildToBack(transform(expr));
        } else {
          end = expr.location.end;
          root = newNode(Token.COMMA, root, transform(expr));
          setSourceInfo(root, start, end);
        }
      }
      return root;
    }

    Node processClassDeclaration(ClassDeclarationTree tree) {
      maybeWarnForFeature(tree, Feature.CLASSES);

      Node name = transformOrEmpty(tree.name, tree);
      Node superClass = transformOrEmpty(tree.superClass, tree);

      Node classMembers = newNode(Token.CLASS_MEMBERS);
      setSourceInfo(classMembers, tree);

      try (AutoDecrement ignored = classScope.increment()) {
        boolean hasConstructor = false;
        for (ParseTree child : tree.elements) {
          switch (child.type) {
            case COMPUTED_PROPERTY_GETTER, COMPUTED_PROPERTY_SETTER, GET_ACCESSOR, SET_ACCESSOR ->
                features = features.with(Feature.CLASS_GETTER_SETTER);
            case BLOCK -> features = features.with(Feature.CLASS_STATIC_BLOCK);
            default -> {}
          }

          boolean childIsCtor = validateClassConstructorMember(child); // Has side-effects.
          if (childIsCtor) {
            if (hasConstructor) {
              errorReporter.error(
                  "Class may have only one constructor.", //
                  sourceName,
                  lineno(child),
                  charno(child));
            }
            hasConstructor = true;
          }

          classMembers.addChildToBack(transform(child));
        }
      }

      // When nested inside of multiple classes, only run the validation once the top-most class
      // exits scope.
      if (!classScope.inScope()) {
        validatePrivatePropertyUsage(
            Collections.unmodifiableSet(getPrivatePropsAndValidateUniqueness(classMembers)),
            classMembers);
      }

      Node classNode = newNode(Token.CLASS, name, superClass, classMembers);
      attachPossibleTrailingComment(classNode, tree.getEnd());

      return classNode;
    }

    /**
     * Validates that all referenced private properties within the scope of <code>classMembers
     * </code> are found in <code>privatePropNames</code> and private fields are not deleted (both
     * of these cases are syntax errors).
     *
     * <p>Recurses into inner classes and successively adds to <code>privatePropNames</code> such
     * that outer class private properties are available to inner classes.
     */
    private void validatePrivatePropertyUsage(Set<String> privatePropNames, Node classMembers) {
      checkState(classMembers.isClassMembers());
      if (!classMembers.hasChildren()) {
        return;
      }

      // Uses a stack object instead of recursion to avoid a stack overflow.
      ArrayDeque<Node> visitStack = new ArrayDeque<>();
      visitStack.addLast(classMembers.getFirstChild());
      while (!visitStack.isEmpty()) {
        Node node = visitStack.removeLast();

        // Check that these usages reference a private property declared in the class:
        //   this.#privateProp
        //   x.#privateProp
        //   x?.#privateProp
        //   #privateProp in x
        if (node.isPrivateIdentifier()
            && (node.isGetProp()
                || node.isOptChainGetProp()
                || (node.isName() && node.hasParent() && node.getParent().isIn()))
            && !privatePropNames.contains(node.getString())) {
          String propType = node.getParent().isCall() ? "methods" : "fields";
          errorReporter.error(
              "Private " + propType + " must be declared in an enclosing class",
              sourceName,
              node.getLineno(),
              node.getCharno());
        }

        // Checks that private fields are not deleted:
        //   delete this.#privateProp
        //   delete x.#privateProp
        //   delete x?.#privateProp
        if (node.isDelProp()) {
          Node firstChild = checkNotNull(node.getFirstChild());
          if (firstChild.isPrivateIdentifier()
              && (firstChild.isGetProp() || firstChild.isOptChainGetProp())) {
            errorReporter.error(
                "Private fields cannot be deleted", sourceName, node.getLineno(), node.getCharno());
          }
        }

        if (node.isClassMembers()) {
          // Recurse into the inner class with the outer and inner class private properties.
          Set<String> innerClassPrivatePropNames = getPrivatePropsAndValidateUniqueness(node);
          innerClassPrivatePropNames.addAll(privatePropNames);
          validatePrivatePropertyUsage(
              Collections.unmodifiableSet(innerClassPrivatePropNames), node);
        } else {
          if (node.getNext() != null) {
            visitStack.addLast(node.getNext());
          }
          if (node.hasChildren()) {
            visitStack.addLast(node.getFirstChild());
          }
        }
      }
    }

    /**
     * Returns the set of private properties declared in the class corresponding to <code>
     * classMembers</code>.
     *
     * <p>While gathering private properties, validates that there are no duplicate property names.
     * Note that, for private getters and setters, one getter and one setter can have the same name
     * provided they have the same static or non-static modifier.
     */
    private Set<String> getPrivatePropsAndValidateUniqueness(Node classMembers) {
      // Track all this to ensure we don't have duplicate private getters and setters.
      Set<String> privateGetterNames = new TreeSet<>();
      Set<String> privateSetterNames = new TreeSet<>();
      Set<String> privateStaticGetterNames = new TreeSet<>();
      Set<String> privateStaticSetterNames = new TreeSet<>();
      Set<String> privateFieldAndMethodNames = new TreeSet<>();

      Set<String> privatePropNames = new TreeSet<>();
      for (Node curNode = classMembers.getFirstChild();
          curNode != null;
          curNode = curNode.getNext()) {
        if (!curNode.isPrivateIdentifier()) {
          continue;
        }

        String propName = curNode.getString();
        checkState(
            curNode.isGetterDef()
                || curNode.isSetterDef()
                || curNode.isMemberFieldDef()
                || curNode.isMemberFunctionDef(),
            "Private property '%s' has an unsupported token type: %s",
            propName,
            curNode.getToken());

        boolean isStatic = curNode.isStaticMember();
        boolean alreadyDeclared = privatePropNames.contains(propName);
        switch (curNode.getToken()) {
          case GETTER_DEF -> {
            if (alreadyDeclared) {
              boolean alreadyDeclaredIsOtherThanItsSetter =
                  privateFieldAndMethodNames.contains(propName)
                      || privateGetterNames.contains(propName)
                      || privateStaticGetterNames.contains(propName)
                      || (isStatic
                          ? privateSetterNames.contains(propName)
                          : privateStaticSetterNames.contains(propName));
              checkState(
                  alreadyDeclaredIsOtherThanItsSetter
                      || (isStatic
                          ? privateStaticSetterNames.contains(propName)
                          : privateSetterNames.contains(propName)));
              alreadyDeclared = alreadyDeclaredIsOtherThanItsSetter;
            }

            if (isStatic) {
              privateStaticGetterNames.add(propName);
            } else {
              privateGetterNames.add(propName);
            }
          }
          case SETTER_DEF -> {
            if (alreadyDeclared) {
              boolean alreadyDeclaredIsOtherThanItsGetter =
                  privateFieldAndMethodNames.contains(propName)
                      || privateSetterNames.contains(propName)
                      || privateStaticSetterNames.contains(propName)
                      || (isStatic
                          ? privateGetterNames.contains(propName)
                          : privateStaticGetterNames.contains(propName));
              checkState(
                  alreadyDeclaredIsOtherThanItsGetter
                      || (isStatic
                          ? privateStaticGetterNames.contains(propName)
                          : privateGetterNames.contains(propName)));
              alreadyDeclared = alreadyDeclaredIsOtherThanItsGetter;
            }

            if (curNode.isStaticMember()) {
              privateStaticSetterNames.add(propName);
            } else {
              privateSetterNames.add(propName);
            }
          }
          case MEMBER_FIELD_DEF, MEMBER_FUNCTION_DEF -> privateFieldAndMethodNames.add(propName);
          default -> {}
        }
        if (alreadyDeclared) {
          errorReporter.error(
              "Identifier '" + propName + "' has already been declared",
              sourceName,
              curNode.getLineno(),
              curNode.getCharno());
        }

        privatePropNames.add(propName);
      }
      return privatePropNames;
    }

    /** Returns {@code true} iff this member is a legal class constructor. */
    private boolean validateClassConstructorMember(ParseTree member) {
      final com.google.javascript.jscomp.parsing.parser.Token memberName;
      final boolean isStatic;
      final boolean hasIllegalModifier;
      switch (member.type) {
        case GET_ACCESSOR -> {
          GetAccessorTree getter = member.asGetAccessor();
          memberName = getter.propertyName;
          isStatic = getter.isStatic;
          hasIllegalModifier = true;
        }
        case SET_ACCESSOR -> {
          SetAccessorTree setter = member.asSetAccessor();
          memberName = setter.propertyName;
          isStatic = setter.isStatic;
          hasIllegalModifier = true;
        }
        case FUNCTION_DECLARATION -> {
          FunctionDeclarationTree method = member.asFunctionDeclaration();
          memberName = method.name;
          isStatic = method.isStatic;
          hasIllegalModifier = method.isGenerator || method.isAsync;
        }
        default -> {
          // Computed properties aren't an issue here because they aren't used as the class
          // constructor, regardless of their name.
          return false;
        }
      }

      if (isStatic) {
        // Statics are fine because they're never the class constructor.
        return false;
      }

      if (!memberName.type.equals(TokenType.IDENTIFIER)
          || !memberName.asIdentifier().valueEquals("constructor")) {
        // There's only a potential issue if the member is named "constructor".
        // TODO(b/123769080): Also check for quoted string literals with the value "constructor".
        return false;
      }

      if (hasIllegalModifier) {
        errorReporter.error(
            "Class constructor may not be getter, setter, async, or generator.",
            sourceName,
            lineno(member),
            charno(member));
        return false;
      }

      return true;
    }

    Node processSuper(SuperExpressionTree tree) {
      maybeWarnForFeature(tree, Feature.SUPER);
      return newNode(Token.SUPER);
    }

    Node processNewTarget(NewTargetExpressionTree tree) {
      maybeWarnForFeature(tree, Feature.NEW_TARGET);
      return newNode(Token.NEW_TARGET);
    }

    Node processYield(YieldExpressionTree tree) {
      Node yield = newNode(Token.YIELD);
      if (tree.expression != null) {
        yield.addChildToBack(transform(tree.expression));
      }
      yield.setYieldAll(tree.isYieldAll);
      return yield;
    }

    Node processAwait(AwaitExpressionTree tree) {
      maybeWarnForFeature(tree, Feature.ASYNC_FUNCTIONS);
      Node await = newNode(Token.AWAIT);
      await.addChildToBack(transform(tree.expression));
      return await;
    }

    Node processExportDecl(ExportDeclarationTree tree) {
      maybeWarnForFeature(tree, Feature.MODULES);
      Node decls = null;
      if (tree.isExportAll) {
        checkState(tree.declaration == null && tree.exportSpecifierList == null);
      } else if (tree.declaration != null) {
        checkState(tree.exportSpecifierList == null);
        decls = transform(tree.declaration);
      } else {
        decls = transformList(Token.EXPORT_SPECS, tree.exportSpecifierList);
      }
      if (decls == null) {
        decls = newNode(Token.EMPTY);
      }
      setSourceInfo(decls, tree);
      Node export = newNode(Token.EXPORT, decls);
      if (tree.from != null) {
        Node from = processString(tree.from);
        export.addChildToBack(from);
      }

      export.putBooleanProp(Node.EXPORT_ALL_FROM, tree.isExportAll);
      export.putBooleanProp(Node.EXPORT_DEFAULT, tree.isDefault);
      return export;
    }

    Node processExportSpec(ExportSpecifierTree tree) {
      Node importedName = processName(tree.importedName, Token.NAME, IdentifierType.STANDARD);
      Node exportSpec = newNode(Token.EXPORT_SPEC, importedName);
      if (tree.destinationName == null) {
        exportSpec.setShorthandProperty(true);
        exportSpec.addChildToBack(importedName.cloneTree());
      } else {
        Node destinationName =
            processName(tree.destinationName, Token.NAME, IdentifierType.STANDARD);
        exportSpec.addChildToBack(destinationName);
      }
      return exportSpec;
    }

    Node processImportDecl(ImportDeclarationTree tree) {
      maybeWarnForFeature(tree, Feature.MODULES);

      Node firstChild = transformOrEmpty(tree.defaultBindingIdentifier, tree);
      Node secondChild;
      if (tree.nameSpaceImportIdentifier == null) {
        secondChild = transformListOrEmpty(Token.IMPORT_SPECS, tree.importSpecifierList);
        // Currently source info is "import {foo} from '...';" expression. If needed this should be
        // changed to use only "{foo}" part.
        setSourceInfo(secondChild, tree);
      } else {
        secondChild =
            newStringNodeFromIdentifier(
                Token.IMPORT_STAR, IdentifierType.STANDARD, tree.nameSpaceImportIdentifier);
        setSourceInfo(secondChild, tree.nameSpaceImportIdentifier);
      }
      Node thirdChild = processString(tree.moduleSpecifier);

      return newNode(Token.IMPORT, firstChild, secondChild, thirdChild);
    }

    Node processImportSpec(ImportSpecifierTree tree) {
      Node importedName = processName(tree.importedName, Token.NAME, IdentifierType.STANDARD);
      Node importSpec = newNode(Token.IMPORT_SPEC, importedName);
      if (tree.destinationName == null) {
        importSpec.setShorthandProperty(true);
        importSpec.addChildToBack(importedName.cloneTree());
      } else {
        importSpec.addChildToBack(
            processName(tree.destinationName, Token.NAME, IdentifierType.STANDARD));
      }
      return importSpec;
    }

    Node processDynamicImport(DynamicImportTree dynamicImportNode) {
      maybeWarnForFeature(dynamicImportNode, Feature.DYNAMIC_IMPORT);
      Node argument = transform(dynamicImportNode.argument);
      return newNode(Token.DYNAMIC_IMPORT, argument);
    }

    Node processImportMeta(ImportMetaExpressionTree tree) {
      maybeWarnForFeature(tree, Feature.MODULES);
      maybeWarnForFeature(tree, Feature.IMPORT_META);
      return newNode(Token.IMPORT_META);
    }

    private boolean checkParameters(FormalParameterListTree tree) {
      ImmutableList<ParseTree> params = tree.parameters;
      boolean good = true;
      for (int i = 0; i < params.size(); i++) {
        ParseTree param = params.get(i);
        if (param.type == ParseTreeType.ITER_REST) {
          if (i != params.size() - 1) {
            errorReporter.error(
                "A rest parameter must be last in a parameter list.",
                sourceName,
                lineno(param),
                charno(param));
            good = false;
          } else if (tree.hasTrailingComma) {
            errorReporter.error(
                "A trailing comma must not follow a rest parameter.",
                sourceName,
                lineno(param),
                charno(param));
            good = false;
          }
        }
      }
      return good;
    }

    private Node transformList(Token type, ImmutableList<ParseTree> list) {
      Node n = newNode(type);
      for (ParseTree tree : list) {
        n.addChildToBack(transform(tree));
      }
      return n;
    }

    private Node transformListOrEmpty(Token type, ImmutableList<ParseTree> list) {
      if (list == null || list.isEmpty()) {
        return newNode(Token.EMPTY);
      } else {
        return transformList(type, list);
      }
    }

    Node unsupportedLanguageFeature(ParseTree node, String feature) {
      errorReporter.error(
          "unsupported language feature: " + feature, sourceName, lineno(node), charno(node));
      return createMissingExpressionNode();
    }

    Node processLiteralExpression(LiteralExpressionTree expr) {
      return switch (expr.literalToken.type) {
        case NUMBER -> processNumberLiteral(expr);
        case STRING -> processStringLiteral(expr);
        case BIGINT -> processBigIntLiteral(expr);
        case FALSE, TRUE -> processBooleanLiteral(expr);
        case NULL -> processNullLiteral(expr);
        case REGULAR_EXPRESSION -> processRegExpLiteral(expr);
        default ->
            throw new IllegalStateException(
                "Unexpected literal type: "
                    + expr.literalToken.getClass()
                    + " type: "
                    + expr.literalToken.type);
      };
    }

    public Node process(ParseTree node) {
      switch (node.type) {
        case BINARY_OPERATOR -> {
          return processBinaryExpression(node.asBinaryOperator());
        }
        case ARRAY_LITERAL_EXPRESSION -> {
          return processArrayLiteral(node.asArrayLiteralExpression());
        }
        case TEMPLATE_LITERAL_EXPRESSION -> {
          return processTemplateLiteral(node.asTemplateLiteralExpression());
        }
        case TEMPLATE_LITERAL_PORTION -> {
          return processTemplateLiteralPortion(node.asTemplateLiteralPortion());
        }
        case TEMPLATE_SUBSTITUTION -> {
          return processTemplateSubstitution(node.asTemplateSubstitution());
        }
        case UNARY_EXPRESSION -> {
          return processUnaryExpression(node.asUnaryExpression());
        }
        case BLOCK -> {
          return processBlock(node.asBlock());
        }
        case BREAK_STATEMENT -> {
          return processBreakStatement(node.asBreakStatement());
        }
        case CALL_EXPRESSION -> {
          return processFunctionCall(node.asCallExpression());
        }
        case OPT_CHAIN_CALL_EXPRESSION -> {
          return processOptChainFunctionCall(node.asOptChainCallExpression());
        }
        case CASE_CLAUSE -> {
          return processSwitchCase(node.asCaseClause());
        }
        case DEFAULT_CLAUSE -> {
          return processSwitchDefault(node.asDefaultClause());
        }
        case CATCH -> {
          return processCatchClause(node.asCatch());
        }
        case CONTINUE_STATEMENT -> {
          return processContinueStatement(node.asContinueStatement());
        }
        case DO_WHILE_STATEMENT -> {
          return processDoLoop(node.asDoWhileStatement());
        }
        case EMPTY_STATEMENT -> {
          return processEmptyStatement(node.asEmptyStatement());
        }
        case EXPRESSION_STATEMENT -> {
          return processExpressionStatement(node.asExpressionStatement());
        }
        case DEBUGGER_STATEMENT -> {
          return processDebuggerStatement(node.asDebuggerStatement());
        }
        case THIS_EXPRESSION -> {
          return processThisExpression(node.asThisExpression());
        }
        case FOR_STATEMENT -> {
          return processForLoop(node.asForStatement());
        }
        case FOR_IN_STATEMENT -> {
          return processForInLoop(node.asForInStatement());
        }
        case FUNCTION_DECLARATION -> {
          return processFunction(node.asFunctionDeclaration());
        }
        case MEMBER_LOOKUP_EXPRESSION -> {
          return processElementGet(node.asMemberLookupExpression());
        }
        case OPT_CHAIN_MEMBER_LOOKUP_EXPRESSION -> {
          return processOptChainElementGet(node.asOptionalMemberLookupExpression());
        }
        case MEMBER_EXPRESSION -> {
          return processPropertyGet(node.asMemberExpression());
        }
        case OPT_CHAIN_MEMBER_EXPRESSION -> {
          return processOptChainPropertyGet(node.asOptionalMemberExpression());
        }
        case CONDITIONAL_EXPRESSION -> {
          return processConditionalExpression(node.asConditionalExpression());
        }
        case IF_STATEMENT -> {
          return processIfStatement(node.asIfStatement());
        }
        case LABELLED_STATEMENT -> {
          return processLabeledStatement(node.asLabelledStatement());
        }
        case PAREN_EXPRESSION -> {
          return processParenthesizedExpression(node.asParenExpression());
        }
        case IDENTIFIER_EXPRESSION -> {
          return processName(
              node.asIdentifierExpression(),
              privateIdLhsOfInScope.inScope()
                  ? IdentifierType.CAN_BE_PRIVATE
                  : IdentifierType.STANDARD);
        }
        case NEW_EXPRESSION -> {
          return processNewExpression(node.asNewExpression());
        }
        case OBJECT_LITERAL_EXPRESSION -> {
          return processObjectLiteral(node.asObjectLiteralExpression());
        }
        case COMPUTED_PROPERTY_DEFINITION -> {
          return processComputedPropertyDefinition(node.asComputedPropertyDefinition());
        }
        case COMPUTED_PROPERTY_GETTER -> {
          return processComputedPropertyGetter(node.asComputedPropertyGetter());
        }
        case COMPUTED_PROPERTY_METHOD -> {
          return processComputedPropertyMethod(node.asComputedPropertyMethod());
        }
        case COMPUTED_PROPERTY_SETTER -> {
          return processComputedPropertySetter(node.asComputedPropertySetter());
        }
        case RETURN_STATEMENT -> {
          return processReturnStatement(node.asReturnStatement());
        }
        case UPDATE_EXPRESSION -> {
          return processUpdateExpression(node.asUpdateExpression());
        }
        case PROGRAM -> {
          return processAstRoot(node.asProgram());
        }
        case LITERAL_EXPRESSION -> {
          // STRING, NUMBER, TRUE, FALSE, NULL, REGEXP
          return processLiteralExpression(node.asLiteralExpression());
        }
        case SWITCH_STATEMENT -> {
          return processSwitchStatement(node.asSwitchStatement());
        }
        case THROW_STATEMENT -> {
          return processThrowStatement(node.asThrowStatement());
        }
        case TRY_STATEMENT -> {
          return processTryStatement(node.asTryStatement());
        }
        case VARIABLE_STATEMENT -> {
          // var const let
          return processVariableStatement(node.asVariableStatement());
        }
        case VARIABLE_DECLARATION_LIST -> {
          return processVariableDeclarationList(node.asVariableDeclarationList());
        }
        case VARIABLE_DECLARATION -> {
          return processVariableDeclaration(node.asVariableDeclaration());
        }
        case WHILE_STATEMENT -> {
          return processWhileLoop(node.asWhileStatement());
        }
        case WITH_STATEMENT -> {
          return processWithStatement(node.asWithStatement());
        }
        case COMMA_EXPRESSION -> {
          return processCommaExpression(node.asCommaExpression());
        }
        case NULL -> {
          // this is not the null literal
          return processNull(node.asNull());
        }
        case FINALLY -> {
          return processFinally(node.asFinally());
        }
        case MISSING_PRIMARY_EXPRESSION -> {
          return processMissingExpression(node.asMissingPrimaryExpression());
        }
        case PROPERTY_NAME_ASSIGNMENT -> {
          return processPropertyNameAssignment(node.asPropertyNameAssignment());
        }
        case GET_ACCESSOR -> {
          return processGetAccessor(node.asGetAccessor());
        }
        case SET_ACCESSOR -> {
          return processSetAccessor(node.asSetAccessor());
        }
        case FORMAL_PARAMETER_LIST -> {
          return processFormalParameterList(node.asFormalParameterList());
        }
        case CLASS_DECLARATION -> {
          return processClassDeclaration(node.asClassDeclaration());
        }
        case SUPER_EXPRESSION -> {
          return processSuper(node.asSuperExpression());
        }
        case NEW_TARGET_EXPRESSION -> {
          return processNewTarget(node.asNewTargetExpression());
        }
        case YIELD_EXPRESSION -> {
          return processYield(node.asYieldStatement());
        }
        case AWAIT_EXPRESSION -> {
          return processAwait(node.asAwaitExpression());
        }
        case FOR_OF_STATEMENT -> {
          return processForOf(node.asForOfStatement());
        }
        case FOR_AWAIT_OF_STATEMENT -> {
          return processForAwaitOf(node.asForAwaitOfStatement());
        }
        case EXPORT_DECLARATION -> {
          return processExportDecl(node.asExportDeclaration());
        }
        case EXPORT_SPECIFIER -> {
          return processExportSpec(node.asExportSpecifier());
        }
        case IMPORT_DECLARATION -> {
          return processImportDecl(node.asImportDeclaration());
        }
        case IMPORT_SPECIFIER -> {
          return processImportSpec(node.asImportSpecifier());
        }
        case DYNAMIC_IMPORT_EXPRESSION -> {
          return processDynamicImport(node.asDynamicImportExpression());
        }
        case IMPORT_META_EXPRESSION -> {
          return processImportMeta(node.asImportMetaExpression());
        }
        case ARRAY_PATTERN -> {
          return processArrayPattern(node.asArrayPattern());
        }
        case OBJECT_PATTERN -> {
          return processObjectPattern(node.asObjectPattern());
        }
        case COMPREHENSION -> {
          return processComprehension(node.asComprehension());
        }
        case COMPREHENSION_FOR -> {
          return processComprehensionFor(node.asComprehensionFor());
        }
        case COMPREHENSION_IF -> {
          return processComprehensionIf(node.asComprehensionIf());
        }
        case DEFAULT_PARAMETER -> {
          return processDefaultParameter(node.asDefaultParameter());
        }
        case ITER_REST -> {
          return processIterRest(node.asIterRest());
        }
        case ITER_SPREAD -> {
          return processIterSpread(node.asIterSpread());
          // ES2019
        }
        case OBJECT_REST -> {
          return processObjectPatternElement(node.asObjectRest());
        }
        case OBJECT_SPREAD -> {
          return processObjectSpread(node.asObjectSpread());
          // ES2022
        }
        case FIELD_DECLARATION -> {
          return processField(node.asFieldDeclaration());
        }
        case COMPUTED_PROPERTY_FIELD -> {
          return processComputedPropertyField(node.asComputedPropertyField());
        }
        case ARGUMENT_LIST -> {
          // TODO(johnlenz): handle these or remove parser support
        }
        default -> {}
      }
      return processIllegalToken(node);
    }
  }

  private void reportErrorIfYieldOrAwaitInDefaultValue(Node defaultValueNode) {
    Node yieldNode = findNodeTypeInExpression(defaultValueNode, Token.YIELD);
    if (yieldNode != null) {
      errorReporter.error(
          "`yield` is illegal in parameter default value.",
          yieldNode.getSourceFileName(),
          yieldNode.getLineno(),
          yieldNode.getCharno());
    }
    Node awaitNode = findNodeTypeInExpression(defaultValueNode, Token.AWAIT);
    if (awaitNode != null) {
      errorReporter.error(
          "`await` is illegal in parameter default value.",
          awaitNode.getSourceFileName(),
          awaitNode.getLineno(),
          awaitNode.getCharno());
    }
  }

  /**
   * Tries to find a node with the given token in the given expression. Returns the first one found
   * in pre-order traversal or `null` if none found. Will not traverse into function or class
   * expressions.
   */
  private static @Nullable Node findNodeTypeInExpression(Node expressionNode, Token token) {
    Deque<Node> worklist = new ArrayDeque<>();
    worklist.add(expressionNode);
    while (!worklist.isEmpty()) {
      Node node = worklist.remove();
      if (node.getToken() == token) {
        return node;
      } else if (!node.isFunction() && !node.isClass()) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
          worklist.add(child);
        }
      }
    }
    return null;
  }

  String normalizeRegex(LiteralToken token) {
    String value = token.value;
    final int lastSlash = value.lastIndexOf('/');
    int cur = value.indexOf('\\');
    if (cur == -1) {
      return value.substring(1, lastSlash);
    }

    StringBuilder result = new StringBuilder();
    int start = 1;
    while (cur != -1) {
      result.append(value, start, cur);

      cur++; // skip the escape char.
      char c = value.charAt(cur);
      switch (c) {
        // Characters for which the backslash is semantically important.
        case '^':
        case '$':
        case '\\':
        case '/':
        case '.':
        case '*':
        case '+':
        case '?':
        case '(':
        case ')':
        case '[':
        case ']':
        case '{':
        case '}':
        case '|':
        case '-':
        case 'b':
        case 'B':
        case 'c':
        case 'd':
        case 'D':
        case 'f':
        case 'n':
        case 'p': // 2018 unicode property escapes
        case 'P': // 2018 unicode property escapes
        case 'r':
        case 's':
        case 'S':
        case 't':
        case 'u':
        case 'v':
        case 'w':
        case 'W':
        case 'x':
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
          result.append('\\');
        // fallthrough
        default:
          // For all other characters, the backslash has no effect, so just append the next char.
          result.append(c);
      }

      start = cur + 1;
      cur = value.indexOf('\\', start);
    }
    result.append(value, start, lastSlash);
    return result.toString();
  }

  String normalizeString(LiteralToken token, boolean templateLiteral) {
    String value = token.value;
    // <CR><LF> and <CR> are normalized as <LF>. For raw template literal string values: this is the
    // spec behaviour. For regular string literals: they can only be part of a line continuation,
    // which we want to scrub.
    if (value.indexOf('\r') >= 0) {
      value = value.replaceAll("\r\n?", "\n");
    }

    int start = templateLiteral ? 0 : 1; // skip the leading quote
    int cur = value.indexOf('\\');
    if (cur == -1) {
      // short circuit no escapes.
      return templateLiteral ? value : value.substring(1, value.length() - 1);
    }
    StringBuilder result = new StringBuilder();
    while (cur != -1) {
      result.append(value, start, cur);

      cur += 1; // skip the escape char.
      char c = value.charAt(cur);
      switch (c) {
        case 'b':
          result.append('\b');
          break;
        case 'f':
          result.append('\f');
          break;
        case 'n':
          result.append('\n');
          break;
        case 'r':
          result.append('\r');
          break;
        case 't':
          result.append('\t');
          break;
        case 'v':
          result.append('\u000B');
          break;
        case '\n':
          // line continuation, skip the line break
          maybeWarnForFeature(token, Feature.STRING_CONTINUATION);
          errorReporter.warning(
              STRING_CONTINUATION_WARNING,
              sourceName,
              lineno(token.location.start),
              charno(token.location.start));
          break;
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
          int numDigits;

          if (cur + 1 < value.length() && isOctalDigit(value.charAt(cur + 1))) {
            if (cur + 2 < value.length() && isOctalDigit(value.charAt(cur + 2))) {
              numDigits = 3;
            } else {
              numDigits = 2;
            }
          } else {
            numDigits = 1;
          }

          if (inStrictContext() || templateLiteral) {
            if (c == '0' && numDigits == 1) {
              // No warning: "\0" followed by a character which is not an octal digit
              // is allowed in strict mode.
            } else {
              errorReporter.warning(
                  OCTAL_STRING_LITERAL_WARNING,
                  sourceName,
                  lineno(token.location.start),
                  charno(token.location.start));
            }
          }

          result.append((char) parseInt(value.substring(cur, cur + numDigits), 8));
          cur += numDigits - 1;

          break;
        case 'x':
          result.append(
              (char) (hexdigit(value.charAt(cur + 1)) * 0x10 + hexdigit(value.charAt(cur + 2))));
          cur += 2;
          break;
        case 'u':
          int escapeEnd;
          String hexDigits;
          if (value.charAt(cur + 1) != '{') {
            // Simple escape with exactly four hex digits: \\uXXXX
            escapeEnd = cur + 5;
            hexDigits = value.substring(cur + 1, escapeEnd);
          } else {
            // Escape with braces can have any number of hex digits: \\u{XXXXXXX}
            escapeEnd = cur + 2;
            while (Character.digit(value.charAt(escapeEnd), 0x10) >= 0) {
              escapeEnd++;
            }
            hexDigits = value.substring(cur + 2, escapeEnd);
            escapeEnd++;
          }
          int codePointValue = parseInt(hexDigits, 0x10);
          if (codePointValue > 0x10ffff) {
            errorReporter.error(
                "Undefined Unicode code-point",
                sourceName,
                lineno(token.location.start),
                charno(token.location.start));

            // Compilation should stop, but we should finish the string and find more errors.
            // These appends are just to have a placeholder for the errored normalization.
            result.append("\\u{");
            result.append(hexDigits);
            result.append("}");
          } else {
            result.append(Character.toChars(codePointValue));
          }
          cur = escapeEnd - 1;
          break;
        case '\'':
        case '"':
        case '\\':
        default:
          result.append(c);
          break;
      }
      start = cur + 1;
      cur = value.indexOf('\\', start);
    }
    // skip the trailing quote.
    result.append(value, start, templateLiteral ? value.length() : value.length() - 1);

    return result.toString();
  }

  boolean isSupportedForInputLanguageMode(Feature feature) {
    return config.languageMode().featureSet.has(feature);
  }

  private boolean inStrictContext() {
    // TODO(johnlenz): in ECMASCRIPT5/6 is a "mixed" mode and we should track the context
    // that we are in, if we want to support it.
    return config.strictMode().isStrict();
  }

  double normalizeNumber(LiteralToken token) {
    String value = token.value;
    if (value.contains("_")) {
      value = removeNumericSeparators(value, token);
    }
    SourceRange location = token.location;
    int length = value.length();
    checkState(length > 0);
    checkState(value.charAt(0) != '-' && value.charAt(0) != '+');
    if (value.charAt(0) == '.') {
      return Double.parseDouble('0' + value);
    } else if (value.charAt(0) == '0' && length > 1) {
      switch (value.charAt(1)) {
        case '.', 'e', 'E' -> {
          return Double.parseDouble(value);
        }
        case 'b', 'B' -> {
          maybeWarnForFeature(token, Feature.BINARY_LITERALS);
          double v = 0;
          int c = 1;
          while (++c < length) {
            v = (v * 2) + binarydigit(value.charAt(c));
          }
          return v;
        }
        case 'o', 'O' -> {
          maybeWarnForFeature(token, Feature.OCTAL_LITERALS);
          double v = 0;
          int c = 1;
          while (++c < length) {
            v = (v * 8) + octaldigit(value.charAt(c));
          }
          return v;
        }
        case 'x', 'X' -> {
          double v = 0;
          int c = 1;
          while (++c < length) {
            v = (v * 0x10) + hexdigit(value.charAt(c));
          }
          return v;
        }
        case '0', '1', '2', '3', '4', '5', '6', '7' -> {
          double v = 0;
          int c = 0;
          while (++c < length) {
            char digit = value.charAt(c);
            if (isOctalDigit(digit)) {
              v = (v * 8) + octaldigit(digit);
            } else {
              errorReporter.error(
                  INVALID_OCTAL_DIGIT, sourceName, lineno(location.start), charno(location.start));
              return 0;
            }
          }
          if (inStrictContext()) {
            errorReporter.error(
                INVALID_ES5_STRICT_OCTAL,
                sourceName,
                lineno(location.start),
                charno(location.start));
          } else {
            errorReporter.warning(
                INVALID_ES5_STRICT_OCTAL,
                sourceName,
                lineno(location.start),
                charno(location.start));
          }
          return v;
        }
        case '8', '9' -> {
          errorReporter.error(
              INVALID_OCTAL_DIGIT, sourceName, lineno(location.start), charno(location.start));
          return 0;
        }
        default ->
            throw new IllegalStateException(
                "Unexpected character in number literal: " + value.charAt(1));
      }
    } else {
      return Double.parseDouble(value);
    }
  }

  BigInteger normalizeBigInt(LiteralToken token) {
    String value = token.value;
    value = value.substring(0, value.indexOf('n'));
    if (value.contains("_")) {
      value = removeNumericSeparators(value, token);
    }
    int length = value.length();
    checkState(length > 0);
    checkState(value.charAt(0) != '-' && value.charAt(0) != '+');
    if (value.charAt(0) == '0' && length > 1) {
      return switch (value.charAt(1)) {
        case 'b', 'B' -> {
          maybeWarnForFeature(token, Feature.BINARY_LITERALS);
          yield new BigInteger(value.substring(2), 2);
        }
        case 'o', 'O' -> {
          maybeWarnForFeature(token, Feature.OCTAL_LITERALS);
          yield new BigInteger(value.substring(2), 8);
        }
        case 'x', 'X' -> new BigInteger(value.substring(2), 16);
        case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' ->
            throw new IllegalStateException("Nonzero BigInts can't have a leading zero");
        default ->
            throw new IllegalStateException(
                "Unexpected character in bigint literal: " + value.charAt(1));
      };
    } else {
      return new BigInteger(value);
    }
  }

  private String removeNumericSeparators(String value, LiteralToken token) {
    maybeWarnForFeature(token, Feature.NUMERIC_SEPARATOR);
    return value.replace("_", "");
  }

  private static int binarydigit(char c) {
    if (c >= '0' && c <= '1') {
      return (c - '0');
    }
    throw new IllegalStateException("unexpected: " + c);
  }

  private static boolean isOctalDigit(char c) {
    return c >= '0' && c <= '7';
  }

  private static int octaldigit(char c) {
    if (isOctalDigit(c)) {
      return (c - '0');
    }
    throw new IllegalStateException("unexpected: " + c);
  }

  private static int hexdigit(char c) {
    return switch (c) {
      case '0' -> 0;
      case '1' -> 1;
      case '2' -> 2;
      case '3' -> 3;
      case '4' -> 4;
      case '5' -> 5;
      case '6' -> 6;
      case '7' -> 7;
      case '8' -> 8;
      case '9' -> 9;
      case 'a', 'A' -> 10;
      case 'b', 'B' -> 11;
      case 'c', 'C' -> 12;
      case 'd', 'D' -> 13;
      case 'e', 'E' -> 14;
      case 'f', 'F' -> 15;
      default -> throw new IllegalStateException("unexpected: " + c);
    };
  }

  static Token transformBooleanTokenType(TokenType token) {
    return switch (token) {
      case TRUE -> Token.TRUE;
      case FALSE -> Token.FALSE;
      default -> throw new IllegalStateException(String.valueOf(token));
    };
  }

  static Token transformUpdateTokenType(TokenType token) {
    return switch (token) {
      case PLUS_PLUS -> Token.INC;
      case MINUS_MINUS -> Token.DEC;
      default -> throw new IllegalStateException(String.valueOf(token));
    };
  }

  static Token transformUnaryTokenType(TokenType token) {
    return switch (token) {
      case BANG -> Token.NOT;
      case TILDE -> Token.BITNOT;
      case PLUS -> Token.POS;
      case MINUS -> Token.NEG;
      case DELETE -> Token.DELPROP;
      case TYPEOF -> Token.TYPEOF;
      case VOID -> Token.VOID;
      default -> throw new IllegalStateException(String.valueOf(token));
    };
  }

  static Token transformBinaryTokenType(TokenType token) {
    return switch (token) {
      case BAR -> Token.BITOR;
      case CARET -> Token.BITXOR;
      case AMPERSAND -> Token.BITAND;
      case EQUAL_EQUAL -> Token.EQ;
      case NOT_EQUAL -> Token.NE;
      case OPEN_ANGLE -> Token.LT;
      case LESS_EQUAL -> Token.LE;
      case CLOSE_ANGLE -> Token.GT;
      case GREATER_EQUAL -> Token.GE;
      case LEFT_SHIFT -> Token.LSH;
      case RIGHT_SHIFT -> Token.RSH;
      case UNSIGNED_RIGHT_SHIFT -> Token.URSH;
      case PLUS -> Token.ADD;
      case MINUS -> Token.SUB;
      case STAR -> Token.MUL;
      case SLASH -> Token.DIV;
      case PERCENT -> Token.MOD;
      case STAR_STAR -> Token.EXPONENT;
      case EQUAL_EQUAL_EQUAL -> Token.SHEQ;
      case NOT_EQUAL_EQUAL -> Token.SHNE;
      case IN -> Token.IN;
      case INSTANCEOF -> Token.INSTANCEOF;
      case COMMA -> Token.COMMA;
      case EQUAL -> Token.ASSIGN;
      case BAR_EQUAL -> Token.ASSIGN_BITOR;
      case CARET_EQUAL -> Token.ASSIGN_BITXOR;
      case AMPERSAND_EQUAL -> Token.ASSIGN_BITAND;
      case LEFT_SHIFT_EQUAL -> Token.ASSIGN_LSH;
      case RIGHT_SHIFT_EQUAL -> Token.ASSIGN_RSH;
      case UNSIGNED_RIGHT_SHIFT_EQUAL -> Token.ASSIGN_URSH;
      case PLUS_EQUAL -> Token.ASSIGN_ADD;
      case MINUS_EQUAL -> Token.ASSIGN_SUB;
      case STAR_EQUAL -> Token.ASSIGN_MUL;
      case STAR_STAR_EQUAL -> Token.ASSIGN_EXPONENT;
      case SLASH_EQUAL -> Token.ASSIGN_DIV;
      case PERCENT_EQUAL -> Token.ASSIGN_MOD;
      case OR -> Token.OR;
      case AND -> Token.AND;
      case QUESTION_QUESTION -> Token.COALESCE;
      case OR_EQUAL -> Token.ASSIGN_OR;
      case AND_EQUAL -> Token.ASSIGN_AND;
      case QUESTION_QUESTION_EQUAL -> Token.ASSIGN_COALESCE;
      default -> throw new IllegalStateException(String.valueOf(token));
    };
  }

  // Simple helper to create nodes and set the initial node properties.
  Node newNode(Token type) {
    return new Node(type).clonePropsFrom(templateNode);
  }

  Node newNode(Token type, Node child1) {
    return new Node(type, child1).clonePropsFrom(templateNode);
  }

  Node newNode(Token type, Node child1, Node child2) {
    return new Node(type, child1, child2).clonePropsFrom(templateNode);
  }

  Node newNode(Token type, Node child1, Node child2, Node child3) {
    return new Node(type, child1, child2, child3).clonePropsFrom(templateNode);
  }

  Node newStringNode(String value) {
    return IR.string(value).clonePropsFrom(templateNode);
  }

  Node newStringNode(Token type, String value) {
    return Node.newString(type, value).clonePropsFrom(templateNode);
  }

  /** Attaches any pending JSDoc comments to the given node. */
  @CanIgnoreReturnValue
  Node maybeAddNonJsDocComment(Node node, SourcePosition start) {
    NonJSDocComment comment = parseNonJSDocCommentAt(start, false);
    if (comment != null) {
      node.setNonJSDocComment(comment);
    }
    return node;
  }

  Node newTemplateLitStringNode(@Nullable String cooked, String raw) {
    return Node.newTemplateLitString(cooked, raw).clonePropsFrom(templateNode);
  }

  Node newNumberNode(Double value) {
    return IR.number(value).clonePropsFrom(templateNode);
  }

  Node newBigIntNode(BigInteger value) {
    return Node.newBigInt(value).clonePropsFrom(templateNode);
  }
}
