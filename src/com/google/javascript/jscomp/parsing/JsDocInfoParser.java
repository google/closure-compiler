/*
 * Copyright 2007 The Closure Compiler Authors.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Msg;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;
import com.google.javascript.rhino.TokenUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

// TODO(nicksantos): Unify all the JSDocInfo stuff into one package, instead of
// spreading it across multiple packages.

/** A parser for JSDoc comments. */
public final class JsDocInfoParser {

  // There's no colon because the colon is parsed as a token before the TTL is extracted.
  private static final String TTL_START_DELIMITER = "=";
  private static final String TTL_END_DELIMITER = "=:";

  private static final CharMatcher TEMPLATE_NAME_MATCHER =
      CharMatcher.javaLetterOrDigit().or(CharMatcher.is('_'));

  @VisibleForTesting
  public static final String BAD_TYPE_WIKI_LINK =
      " See https://github.com/google/closure-compiler/wiki/Annotating-JavaScript-for-the-Closure-Compiler"
          + " for more information.";

  private final JsDocTokenStream stream;
  private final JSDocInfo.Builder jsdocBuilder;
  private final ErrorReporter errorReporter;

  // Use a template node for properties set on all nodes to minimize the
  // memory footprint associated with these (similar to IRFactory).
  private final Node templateNode;

  private void addParserWarning(Msg msg, String messageArg) {
    addParserWarning(msg, messageArg, stream.getLineno(), stream.getCharno());
  }

  private void addParserWarning(Msg msg, String messageArg, int lineno, int charno) {
    errorReporter.warning(msg.format(messageArg), getSourceName(), lineno, charno);
  }

  private void addParserWarning(Msg msg) {
    addParserWarning(msg, stream.getLineno(), stream.getCharno());
  }

  private void addParserWarning(Msg msg, int lineno, int charno) {
    errorReporter.warning(msg.format(), getSourceName(), lineno, charno);
  }

  private void addTypeWarning(Msg msg, String messageArg) {
    addTypeWarning(msg, messageArg, stream.getLineno(), stream.getCharno());
  }

  private void addTypeWarning(Msg msg, String messageArg, int lineno, int charno) {
    errorReporter.warning(
        "Bad type annotation. " + msg.format(messageArg) + BAD_TYPE_WIKI_LINK,
        getSourceName(),
        lineno,
        charno);
  }

  private void addTypeWarning(Msg msg) {
    addTypeWarning(msg, stream.getLineno(), stream.getCharno());
  }

  private void addTypeWarning(Msg msg, int lineno, int charno) {
    errorReporter.warning(
        "Bad type annotation. " + msg.format() + BAD_TYPE_WIKI_LINK,
        getSourceName(),
        lineno,
        charno);
  }

  private void addMissingTypeWarning(int lineno, int charno) {
    errorReporter.warning("Missing type declaration.", getSourceName(), lineno, charno);
  }

  // The DocInfo with the fileoverview tag for the whole file.
  private JSDocInfo fileOverviewJSDocInfo = null;
  private State state;

  private final Map<String, Annotation> annotations;
  private final Set<String> suppressionNames;
  private final Set<String> closurePrimitiveNames;
  private final boolean preserveWhitespace;
  private static final Set<String> modifiesAnnotationKeywords =
      ImmutableSet.of("this", "arguments");
  private static final Set<String> idGeneratorAnnotationKeywords =
      ImmutableSet.of("unique", "consistent", "stable", "mapped", "xid");
  private static final Set<String> primitiveTypes =
      ImmutableSet.of("number", "string", "boolean", "symbol");

  @Nullable private String licenseText;

  private void setLicenseTextMonotonic(String x) {
    checkNotNull(x);
    checkState(this.licenseText == null);
    this.licenseText = x;
  }

  @Nullable
  String getLicenseText() {
    return this.licenseText;
  }

  /**
   * Sets the file overview JSDocInfo, in order to warn about multiple uses of the @fileoverview tag
   * in a file.
   */
  void setFileOverviewJSDocInfo(JSDocInfo fileOverviewJSDocInfo) {
    this.fileOverviewJSDocInfo = fileOverviewJSDocInfo;
  }

  public StaticSourceFile getSourceFile() {
    return templateNode.getStaticSourceFile();
  }

  private enum State {
    SEARCHING_ANNOTATION,
    SEARCHING_NEWLINE,
    NEXT_IS_ANNOTATION
  }

  public JsDocInfoParser(
      JsDocTokenStream stream,
      String comment,
      int commentPosition,
      Node templateNode,
      Config config,
      ErrorReporter errorReporter) {
    this.stream = stream;

    boolean parseDocumentation = config.jsDocParsingMode().shouldParseDescriptions();
    this.jsdocBuilder = JSDocInfo.builder();
    if (parseDocumentation) {
      this.jsdocBuilder.parseDocumentation();
    }
    if (comment != null) {
      this.jsdocBuilder.recordOriginalCommentString(comment);
      this.jsdocBuilder.recordOriginalCommentPosition(commentPosition);
    }
    this.annotations = config.annotations();
    this.suppressionNames = config.suppressionNames();
    this.closurePrimitiveNames = config.closurePrimitiveNames();
    this.preserveWhitespace = config.jsDocParsingMode().shouldPreserveWhitespace();

    this.errorReporter = errorReporter;
    this.templateNode = templateNode == null ? IR.script() : templateNode;
  }

  private String getSourceName() {
    StaticSourceFile sourceFile = getSourceFile();
    return sourceFile == null ? null : sourceFile.getName();
  }

  /** Parse a description as a {@code @type}. */
  public JSDocInfo parseInlineTypeDoc() {
    skipEOLs();

    JsDocToken token = next();
    int lineno = stream.getLineno();
    int startCharno = stream.getCharno();
    Node typeAst = parseParamTypeExpression(token);
    recordTypeNode(lineno, startCharno, typeAst, token == JsDocToken.LEFT_CURLY);

    JSTypeExpression expr = createJSTypeExpression(typeAst);
    if (expr != null) {
      jsdocBuilder.recordType(expr);
      jsdocBuilder.recordInlineType();
      return retrieveAndResetParsedJSDocInfo();
    }
    return null;
  }

  private void recordTypeNode(int lineno, int startCharno, Node typeAst, boolean matchingLC) {
    if (typeAst != null) {
      int endLineno = stream.getLineno();
      int endCharno = stream.getCharno();
      jsdocBuilder.markTypeNode(typeAst, lineno, startCharno, endLineno, endCharno, matchingLC);
    }
  }

  /**
   * Parses a string containing a JsDoc type declaration, returning the type if the parsing
   * succeeded or {@code null} if it failed.
   */
  public static Node parseTypeString(String typeString) {
    JsDocInfoParser parser = getParser(typeString);
    return parser.parseTopLevelTypeExpression(parser.next());
  }

  /**
   * Parses a string containing a JsDoc declaration, returning the entire JSDocInfo if the parsing
   * succeeded or {@code null} if it failed.
   */
  public static JSDocInfo parseJsdoc(String toParse) {
    JsDocInfoParser parser = getParser(toParse);
    parser.parse();
    return parser.retrieveAndResetParsedJSDocInfo();
  }

  @VisibleForTesting
  public static JSDocInfo parseFileOverviewJsdoc(String toParse) {
    JsDocInfoParser parser = getParser(toParse);
    parser.parse();
    return parser.getFileOverviewJSDocInfo();
  }

  private static JsDocInfoParser getParser(String toParse) {
    Config config =
        Config.builder()
            .setLanguageMode(LanguageMode.ECMASCRIPT3)
            .setStrictMode(Config.StrictMode.SLOPPY)
            .setClosurePrimitiveNames(ImmutableSet.of("testPrimitive"))
            .build();
    return new JsDocInfoParser(
        new JsDocTokenStream(toParse), toParse, 0, null, config, ErrorReporter.NULL_INSTANCE);
  }

  /**
   * Parses a {@link JSDocInfo} object. This parsing method reads all tokens returned by the {@link
   * JsDocTokenStream#getJsDocToken()} method until the {@link JsDocToken#EOC} is returned.
   *
   * @return {@code true} if JSDoc information was correctly parsed, {@code false} otherwise
   */
  public boolean parse() {
    state = State.SEARCHING_ANNOTATION;
    skipEOLs();

    JsDocToken token = next();

    // Always record that we have a comment.
    if (jsdocBuilder.shouldParseDocumentation()) {
      ExtractionInfo blockInfo = extractBlockComment(token);
      token = blockInfo.token;
      if (!blockInfo.string.isEmpty()) {
        jsdocBuilder.recordBlockDescription(blockInfo.string);
      }
    } else {
      if (token != JsDocToken.ANNOTATION && token != JsDocToken.EOC) {
        // Mark that there was a description, but don't bother marking
        // what it was.
        jsdocBuilder.recordBlockDescription("");
      }
    }

    return parseHelperLoop(token, new ArrayList<ExtendedTypeInfo>());
  }

  /**
   * Important comments begin with /*! They are treated as license blocks, but no further JSDoc
   * parsing is performed
   */
  void parseImportantComment() {
    state = State.SEARCHING_ANNOTATION;
    skipEOLs();

    JsDocToken token = next();

    ExtractionInfo info = extractMultilineComment(token, WhitespaceOption.PRESERVE, false, true);

    // An extra space is added by the @license annotation
    // so we need to add one here so they will be identical
    String license = " " + info.string;

    this.setLicenseTextMonotonic(license);
    if (jsdocBuilder.shouldParseDocumentation()) {
      jsdocBuilder.recordBlockDescription(license);
    } else {
      jsdocBuilder.recordBlockDescription("");
    }
  }

  private boolean parseHelperLoop(JsDocToken token, List<ExtendedTypeInfo> extendedTypes) {
    while (true) {
      switch (token) {
        case ANNOTATION:
          if (state == State.SEARCHING_ANNOTATION) {
            state = State.SEARCHING_NEWLINE;
            token = parseAnnotation(token, extendedTypes);
          } else {
            token = next();
          }
          break;

        case EOC:
          boolean success = true;
          // TODO(johnlenz): It should be a parse error to have an @extends
          // or similiar annotations in a file overview block.
          checkExtendedTypes(extendedTypes);
          if (hasParsedFileOverviewDocInfo()) {
            fileOverviewJSDocInfo = retrieveAndResetParsedJSDocInfo();
            Visibility visibility = fileOverviewJSDocInfo.getVisibility();
            switch (visibility) {
              case PRIVATE: // fallthrough
              case PROTECTED:
                // PRIVATE and PROTECTED are not allowed in @fileoverview JsDoc.
                addParserWarning(
                    Msg.BAD_FILEOVERVIEW_VISIBIIITY_ANNOTATION,
                    Ascii.toLowerCase(visibility.toString()));
                success = false;
                break;
              default:
                // PACKAGE, PUBLIC, and (implicitly) INHERITED are allowed
                // in @fileoverview JsDoc.
                break;
            }
          }
          return success;

        case EOF:
          // discard any accumulated information
          jsdocBuilder.build();
          addParserWarning(Msg.UNEXPECTED_EOF);
          checkExtendedTypes(extendedTypes);
          return false;

        case EOL:
          if (state == State.SEARCHING_NEWLINE) {
            state = State.SEARCHING_ANNOTATION;
          }
          token = next();
          break;

        default:
          if (token == JsDocToken.STAR && state == State.SEARCHING_ANNOTATION) {
            token = next();
          } else {
            state = State.SEARCHING_NEWLINE;
            token = eatTokensUntilEOL();
          }
          break;
      }
    }
  }

  private JsDocToken parseAnnotation(JsDocToken token, List<ExtendedTypeInfo> extendedTypes) {
    // JSTypes are represented as Rhino AST nodes, and then resolved later.
    JSTypeExpression type;
    int lineno = stream.getLineno();
    int charno = stream.getCharno();

    String annotationName = stream.getString();
    Annotation annotation = annotations.get(annotationName);
    if (annotation == null || annotationName.isEmpty()) {
      addParserWarning(Msg.BAD_JSDOC_TAG, annotationName);
    } else {
      // Mark the beginning of the annotation.
      jsdocBuilder.markAnnotation(annotationName, lineno, charno);

      switch (annotation) {
        case NG_INJECT:
          if (jsdocBuilder.isNgInjectRecorded()) {
            addParserWarning(Msg.JSDOC_NGINJECT_EXTRA);
          } else {
            jsdocBuilder.recordNgInject(true);
          }
          return eatUntilEOLIfNotAnnotation();

        case ABSTRACT:
          if (!jsdocBuilder.recordAbstract()) {
            addTypeWarning(Msg.JSDOC_INCOMPAT_TYPE);
          }
          return eatUntilEOLIfNotAnnotation();

        case AUTHOR:
          if (jsdocBuilder.shouldParseDocumentation()) {
            ExtractionInfo authorInfo = extractSingleLineBlock();
            String author = authorInfo.string;

            if (author.isEmpty()) {
              addParserWarning(Msg.JSDOC_AUTHORMISSING);
            } else {
              jsdocBuilder.recordAuthor(author);
            }
            token = authorInfo.token;
          } else {
            token = eatUntilEOLIfNotAnnotation();
          }
          return token;

        case UNRESTRICTED:
          if (!jsdocBuilder.recordUnrestricted()) {
            addTypeWarning(Msg.JSDOC_INCOMPAT_TYPE);
          }
          return eatUntilEOLIfNotAnnotation();

        case STRUCT:
          if (!jsdocBuilder.recordStruct()) {
            addTypeWarning(Msg.JSDOC_INCOMPAT_TYPE);
          }
          return eatUntilEOLIfNotAnnotation();

        case DICT:
          if (!jsdocBuilder.recordDict()) {
            addTypeWarning(Msg.JSDOC_INCOMPAT_TYPE);
          }
          return eatUntilEOLIfNotAnnotation();

        case COLLAPSIBLE_OR_BREAK_MY_CODE:
          if (!jsdocBuilder.recordCollapsibleOrBreakMyCode()) {
            addParserWarning(Msg.JSDOC_COLLAPSIBLEORBREAKMYCODE);
          }
          return eatUntilEOLIfNotAnnotation();
        case CONSTRUCTOR:
          if (!jsdocBuilder.recordConstructor()) {
            if (jsdocBuilder.isInterfaceRecorded()) {
              addTypeWarning(Msg.JSDOC_INTERFACE_CONSTRUCTOR);
            } else {
              addTypeWarning(Msg.JSDOC_INCOMPAT_TYPE);
            }
          }
          return eatUntilEOLIfNotAnnotation();

        case RECORD:
          if (!jsdocBuilder.recordImplicitMatch()) {
            addTypeWarning(Msg.JSDOC_RECORD);
          }
          return eatUntilEOLIfNotAnnotation();

        case DEPRECATED:
          if (!jsdocBuilder.recordDeprecated()) {
            addParserWarning(Msg.JSDOC_DEPRECATED);
          }

          // Find the reason/description, if any.
          ExtractionInfo reasonInfo = extractMultilineTextualBlock(token);

          String reason = reasonInfo.string;

          if (reason.length() > 0) {
            jsdocBuilder.recordDeprecationReason(reason);
          }

          token = reasonInfo.token;
          return token;

        case INTERFACE:
          if (!jsdocBuilder.recordInterface()) {
            if (jsdocBuilder.isConstructorRecorded()) {
              addTypeWarning(Msg.JSDOC_INTERFACE_CONSTRUCTOR);
            } else {
              addTypeWarning(Msg.JSDOC_INCOMPAT_TYPE);
            }
          }
          return eatUntilEOLIfNotAnnotation();

        case DESC:
          if (jsdocBuilder.isDescriptionRecorded()) {
            addParserWarning(Msg.JSDOC_DESC_EXTRA);
            return eatUntilEOLIfNotAnnotation();
          } else {
            ExtractionInfo descriptionInfo = extractMultilineTextualBlock(token);

            String description = descriptionInfo.string;

            jsdocBuilder.recordDescription(description);
            token = descriptionInfo.token;
            return token;
          }

        case FILE_OVERVIEW:
          String fileOverview = "";
          if (jsdocBuilder.shouldParseDocumentation() && !lookAheadForAnnotation()) {
            ExtractionInfo fileOverviewInfo =
                extractMultilineTextualBlock(
                    token, getWhitespaceOption(WhitespaceOption.TRIM), false);

            fileOverview = fileOverviewInfo.string;

            token = fileOverviewInfo.token;
          } else {
            token = eatUntilEOLIfNotAnnotation();
          }

          if (!jsdocBuilder.recordFileOverview(fileOverview)) {
            addParserWarning(Msg.JSDOC_FILEOVERVIEW_EXTRA);
          }
          return token;

        case LICENSE:
        case PRESERVE:
          // Always use PRESERVE for @license and @preserve blocks.
          ExtractionInfo preserveInfo =
              extractMultilineTextualBlock(token, WhitespaceOption.PRESERVE, true);

          String preserve = preserveInfo.string;

          if (preserve.length() > 0) {
            this.setLicenseTextMonotonic(preserve);
          }

          token = preserveInfo.token;
          return token;

        case ENHANCE:
          ExtractionInfo enhanceInfo = extractSingleLineBlock();
          String enhance = enhanceInfo.string;
          token = enhanceInfo.token;

          jsdocBuilder.recordEnhance(enhance);
          return token;

        case ENUM:
          token = next();
          lineno = stream.getLineno();
          charno = stream.getCharno();

          type = null;
          if (token != JsDocToken.EOL && token != JsDocToken.EOC) {
            Node typeNode = parseAndRecordTypeNode(token);
            if (typeNode != null && typeNode.isStringLit()) {
              String typeName = typeNode.getString();
              if (!primitiveTypes.contains(typeName)) {
                typeNode = wrapNode(Token.BANG, typeNode);
              }
            }
            type = createJSTypeExpression(typeNode);
          } else {
            restoreLookAhead(token);
          }

          if (type == null) {
            type = createJSTypeExpression(newStringNode("number"));
          }
          if (!jsdocBuilder.recordEnumParameterType(type)) {
            addTypeWarning(Msg.JSDOC_INCOMPAT_TYPE, lineno, charno);
          }
          return eatUntilEOLIfNotAnnotation();

        case EXTERNS:
          if (!jsdocBuilder.recordExterns()) {
            addParserWarning(Msg.JSDOC_EXTERNS);
          }
          return eatUntilEOLIfNotAnnotation();

        case TYPE_SUMMARY:
          if (!jsdocBuilder.recordTypeSummary()) {
            addParserWarning(Msg.JSDOC_TYPESUMMARY);
          }
          return eatUntilEOLIfNotAnnotation();

        case EXTENDS:
        case IMPLEMENTS:
          skipEOLs();
          token = next();
          lineno = stream.getLineno();
          charno = stream.getCharno();
          boolean matchingRc = false;

          if (token == JsDocToken.LEFT_CURLY) {
            token = next();
            matchingRc = true;
          }

          if (token == JsDocToken.STRING) {
            Node typeNode = parseAndRecordTypeNameNode(token, lineno, charno, matchingRc);

            lineno = stream.getLineno();
            charno = stream.getCharno();

            typeNode = wrapNode(Token.BANG, typeNode);
            type = createJSTypeExpression(typeNode);

            if (annotation == Annotation.EXTENDS) {
              // record the extended type, check later
              extendedTypes.add(new ExtendedTypeInfo(type, stream.getLineno(), stream.getCharno()));
            } else {
              checkState(annotation == Annotation.IMPLEMENTS);
              if (!jsdocBuilder.recordImplementedInterface(type)) {
                addTypeWarning(Msg.JSDOC_IMPLEMENTS_DUPLICATE, lineno, charno);
              }
            }
            token = next();
            if (matchingRc) {
              if (token != JsDocToken.RIGHT_CURLY) {
                addTypeWarning(Msg.JSDOC_MISSING_RC);
              } else {
                token = next();
              }
            } else if (token != JsDocToken.EOL
                && token != JsDocToken.EOF
                && token != JsDocToken.EOC) {
              addTypeWarning(Msg.END_ANNOTATION_EXPECTED);
            }
          } else if (token == JsDocToken.BANG || token == JsDocToken.QMARK) {
            addTypeWarning(Msg.JSDOC_IMPLEMENTS_EXTRAQUALIFIER, lineno, charno);
          } else {
            addTypeWarning(Msg.NO_TYPE_NAME, lineno, charno);
          }
          token = eatUntilEOLIfNotAnnotation(token);
          return token;

        case HIDDEN:
          if (!jsdocBuilder.recordHiddenness()) {
            addParserWarning(Msg.JSDOC_HIDDEN);
          }
          return eatUntilEOLIfNotAnnotation();

        case LENDS:
          skipEOLs();

          matchingRc = false;
          if (match(JsDocToken.LEFT_CURLY)) {
            token = next();
            matchingRc = true;
          }

          if (match(JsDocToken.STRING)) {
            JSTypeExpression lendsExpression = createJSTypeExpression(parseNameExpression(next()));
            if (!jsdocBuilder.recordLends(lendsExpression)) {
              addTypeWarning(Msg.JSDOC_LENDS_INCOMPATIBLE);
            }
          } else {
            addTypeWarning(Msg.JSDOC_LENDS_MISSING);
          }

          if (matchingRc && !match(JsDocToken.RIGHT_CURLY)) {
            addTypeWarning(Msg.JSDOC_MISSING_RC);
          }
          return eatUntilEOLIfNotAnnotation();

        case MEANING:
          ExtractionInfo meaningInfo = extractMultilineTextualBlock(token);
          String meaning = meaningInfo.string;
          token = meaningInfo.token;
          if (!jsdocBuilder.recordMeaning(meaning)) {
            addParserWarning(Msg.JSDOC_MEANING_EXTRA);
          }
          return token;

        case ALTERNATE_MESSAGE_ID:
          ExtractionInfo alternateMessageIdInfo = extractSingleLineBlock();
          String alternateMessageId = alternateMessageIdInfo.string;
          token = alternateMessageIdInfo.token;
          if (!jsdocBuilder.recordAlternateMessageId(alternateMessageId)) {
            addParserWarning(Msg.JSDOC_ALTERNATEMESSAGEID_EXTRA);
          }
          return token;

        case CLOSURE_PRIMITIVE:
          skipEOLs();
          return parseClosurePrimitiveTag(next());

        case NO_COMPILE:
          if (!jsdocBuilder.recordNoCompile()) {
            addParserWarning(Msg.JSDOC_NOCOMPILE);
          }
          return eatUntilEOLIfNotAnnotation();

        case NO_COLLAPSE:
          if (!jsdocBuilder.recordNoCollapse()) {
            addParserWarning(Msg.JSDOC_NOCOLLAPSE);
          }
          return eatUntilEOLIfNotAnnotation();

        case NO_INLINE:
          if (!jsdocBuilder.recordNoInline()) {
            addParserWarning(Msg.JSDOC_NOINLINE);
          }
          return eatUntilEOLIfNotAnnotation();

        case LOCALE_FILE:
          if (!jsdocBuilder.recordLocaleFile()) {
            addParserWarning(Msg.JSDOC_LOCALEFILE);
          }
          return eatUntilEOLIfNotAnnotation();

        case LOCALE_OBJECT:
          if (!jsdocBuilder.recordLocaleObject()) {
            addParserWarning(Msg.JSDOC_LOCALEOBJECT);
          }
          return eatUntilEOLIfNotAnnotation();

        case LOCALE_SELECT:
          if (!jsdocBuilder.recordLocaleSelect()) {
            addParserWarning(Msg.JSDOC_LOCALESELECT);
          }
          return eatUntilEOLIfNotAnnotation();

        case LOCALE_VALUE:
          if (!jsdocBuilder.recordLocaleValue()) {
            addParserWarning(Msg.JSDOC_LOCALEVALUE);
          }
          return eatUntilEOLIfNotAnnotation();

        case PROVIDE_GOOG:
          if (!jsdocBuilder.recordProvideGoog()) {
            addParserWarning(Msg.JSDOC_PROVIDE_GOOG);
          }
          return eatUntilEOLIfNotAnnotation();
        case PURE_OR_BREAK_MY_CODE:
          if (!jsdocBuilder.recordPureOrBreakMyCode()) {
            addParserWarning(Msg.JSDOC_PUREORBREAKMYCODE);
          }
          return eatUntilEOLIfNotAnnotation();

        case NOT_IMPLEMENTED:
          return eatUntilEOLIfNotAnnotation();

        case INHERIT_DOC:
        case OVERRIDE:
          if (!jsdocBuilder.recordOverride()) {
            addTypeWarning(Msg.JSDOC_OVERRIDE);
          }
          return eatUntilEOLIfNotAnnotation();

        case POLYMER:
          if (jsdocBuilder.isPolymerRecorded()) {
            addParserWarning(Msg.JSDOC_POLYMER_EXTRA);
          } else {
            jsdocBuilder.recordPolymer();
          }
          return eatUntilEOLIfNotAnnotation();

        case POLYMER_BEHAVIOR:
          if (jsdocBuilder.isPolymerBehaviorRecorded()) {
            addParserWarning(Msg.JSDOC_POLYMERBEHAVIOR_EXTRA);
          } else {
            jsdocBuilder.recordPolymerBehavior();
          }
          return eatUntilEOLIfNotAnnotation();

        case CUSTOM_ELEMENT:
          if (jsdocBuilder.isCustomElementRecorded()) {
            addParserWarning(Msg.JSDOC_CUSTOMELEMENT_EXTRA);
          } else {
            jsdocBuilder.recordCustomElement();
          }
          return eatUntilEOLIfNotAnnotation();

        case MIXIN_CLASS:
          if (jsdocBuilder.isMixinClassRecorded()) {
            addParserWarning(Msg.JSDOC_MIXINCLASS_EXTRA);
          } else {
            jsdocBuilder.recordMixinClass();
          }
          return eatUntilEOLIfNotAnnotation();

        case MIXIN_FUNCTION:
          if (jsdocBuilder.isMixinFunctionRecorded()) {
            addParserWarning(Msg.JSDOC_MIXINFUNCTION_EXTRA);
          } else {
            jsdocBuilder.recordMixinFunction();
          }
          return eatUntilEOLIfNotAnnotation();

        case THROWS:
          {
            lineno = stream.getLineno();
            charno = stream.getCharno();
            if (!lookAheadForAnnotation()) {
              ExtractionInfo throwsInfo = extractMultilineTextualBlock(token);
              String throwsAnnotation = throwsInfo.string;
              throwsAnnotation = throwsAnnotation.trim();
              if (throwsAnnotation.length() > 0) {
                jsdocBuilder.recordThrowsAnnotation(throwsAnnotation);
              }
              token = throwsInfo.token;
            }
            return token;
          }

        case PARAM:
          skipEOLs();
          token = next();
          lineno = stream.getLineno();
          charno = stream.getCharno();
          type = null;

          boolean hasParamType = false;

          if (token == JsDocToken.LEFT_CURLY) {
            type = createJSTypeExpression(parseAndRecordParamTypeNode(token));

            if (type == null) {
              // parsing error reported during recursive descent
              // recovering parsing
              return eatUntilEOLIfNotAnnotation();
            }
            skipEOLs();
            token = next();
            lineno = stream.getLineno();
            charno = stream.getCharno();
            hasParamType = true;
          }

          String name = null;
          boolean isBracketedParam = JsDocToken.LEFT_SQUARE == token;
          if (isBracketedParam) {
            token = next();
          }

          if (JsDocToken.STRING != token) {
            addTypeWarning(Msg.MISSING_VARIABLE_NAME, lineno, charno);
          } else {
            if (!hasParamType) {
              addMissingTypeWarning(stream.getLineno(), stream.getCharno());
            }

            name = stream.getString();

            if (isBracketedParam) {
              token = next();

              // Throw out JsDocToolkit's "default" parameter
              // annotation.  It makes no sense under our type
              // system.
              if (JsDocToken.EQUALS == token) {
                token = next();
                if (JsDocToken.STRING == token) {
                  token = next();
                }
              }

              if (JsDocToken.RIGHT_SQUARE != token) {
                reportTypeSyntaxWarning(Msg.JSDOC_MISSING_RB);
              } else if (type != null) {
                // Make the type expression optional, if it isn't
                // already.
                type = JSTypeExpression.makeOptionalArg(type);
              }
            }

            // We do not handle the JsDocToolkit method
            // for handling properties of params, so if the param name has a DOT
            // in it, report a warning and throw it out.
            // See https://github.com/google/closure-compiler/issues/499
            if (!TokenStream.isJSIdentifier(name)) {
              addParserWarning(Msg.INVALID_VARIABLE_NAME, name, lineno, charno);
              name = null;
            } else if (!jsdocBuilder.recordParameter(name, type)) {
              if (jsdocBuilder.hasParameter(name)) {
                addTypeWarning(Msg.DUP_VARIABLE_NAME, name, lineno, charno);
              } else {
                addTypeWarning(Msg.JSDOC_INCOMPAT_TYPE, name, lineno, charno);
              }
            }
          }

          if (name == null) {
            token = eatUntilEOLIfNotAnnotation(token);
            return token;
          }

          jsdocBuilder.markName(name, templateNode, lineno, charno);

          // Find the parameter's description (if applicable).
          if (jsdocBuilder.shouldParseDocumentation() && token != JsDocToken.ANNOTATION) {
            ExtractionInfo paramDescriptionInfo = extractMultilineTextualBlock(token);

            String paramDescription = paramDescriptionInfo.string;

            if (paramDescription.length() > 0) {
              jsdocBuilder.recordParameterDescription(name, paramDescription);
            }

            token = paramDescriptionInfo.token;
          } else if (token != JsDocToken.EOC && token != JsDocToken.EOF) {
            token = eatUntilEOLIfNotAnnotation();
          }
          return token;

        case NO_SIDE_EFFECTS:
          if (!jsdocBuilder.recordNoSideEffects()) {
            addParserWarning(Msg.JSDOC_NOSIDEEFFECTS);
          }
          return eatUntilEOLIfNotAnnotation();

        case MODIFIES:
          token = parseModifiesTag(next());
          return token;

        case IMPLICIT_CAST:
          if (!jsdocBuilder.recordImplicitCast()) {
            addTypeWarning(Msg.JSDOC_IMPLICITCAST);
          }
          return eatUntilEOLIfNotAnnotation();

        case SEE:
          if (jsdocBuilder.shouldParseDocumentation()) {
            ExtractionInfo referenceInfo = extractSingleLineBlock();
            String reference = referenceInfo.string;

            if (reference.isEmpty()) {
              addParserWarning(Msg.JSDOC_SEEMISSING);
            } else {
              jsdocBuilder.recordReference(reference);
            }

            token = referenceInfo.token;
          } else {
            token = eatUntilEOLIfNotAnnotation();
          }
          return token;

        case SUPPRESS:
          token = parseSuppressTag(next());
          return token;

        case TEMPLATE:
          {
            // Attempt to parse a template bound type.
            JSTypeExpression boundTypeExpression = null;
            if (match(JsDocToken.LEFT_CURLY)) {
              addParserWarning(Msg.JSDOC_TEMPLATE_BOUNDEDGENERICS_USED, lineno, charno);

              Node boundTypeNode = parseTypeExpressionAnnotation(next());
              if (boundTypeNode != null) {
                boundTypeExpression = createJSTypeExpression(boundTypeNode);
              }
            }

            // Collect any names of template types.
            List<String> templateNames = new ArrayList<>();
            if (!match(JsDocToken.COLON)) {
              // Skip colons so as not to consume TTLs accidentally.
              // Parse a list of comma separated names.
              do {
                @Nullable Node nameNode = parseNameExpression(next());
                @Nullable String templateName = (nameNode == null) ? null : nameNode.getString();

                if (validTemplateTypeName(templateName)) {
                  templateNames.add(templateName);
                }
              } while (eatIfMatch(JsDocToken.COMMA));
            }

            // Look for some TTL. Always use TRIM for template TTL expressions.
            @Nullable Node ttlAst = null;
            if (match(JsDocToken.COLON)) {
              // TODO(nickreid): Fix `extractMultilineTextualBlock` so the result includes the
              // current token. At present, it reads the stream directly and bypasses any previously
              // read tokens.
              ExtractionInfo ttlExtraction =
                  extractMultilineTextualBlock(current(), WhitespaceOption.TRIM, false);
              token = ttlExtraction.token;
              ttlAst = parseTtlAst(ttlExtraction, lineno, charno);
            } else {
              token = eatUntilEOLIfNotAnnotation(current());
            }

            // Validate the number of declared template names, which depends on bounds and TTL.
            switch (templateNames.size()) {
              case 0:
                addTypeWarning(Msg.JSDOC_TEMPLATE_NAME_MISSING, lineno, charno);
                return token; // There's nothing we can connect a bound or TTL to.
              case 1:
                break;
              default:
                if (boundTypeExpression != null || ttlAst != null) {
                  addTypeWarning(Msg.JSDOC_TEMPLATE_MULTIPLEDECLARATION, lineno, charno);
                }
                break;
            }

            if (boundTypeExpression != null && ttlAst != null) {
              addTypeWarning(Msg.JSDOC_TEMPLATE_BOUNDSWITHTTL, lineno, charno);
              return token; // It's undecidable what the user intent was.
            }

            // Based on the form form of the declaration, record the information in the JsDocInfo.
            if (ttlAst != null) {
              if (!jsdocBuilder.recordTypeTransformation(templateNames.get(0), ttlAst)) {
                addTypeWarning(Msg.JSDOC_TEMPLATE_NAME_REDECLARATION, lineno, charno);
              }
            } else if (boundTypeExpression != null) {
              if (!jsdocBuilder.recordTemplateTypeName(templateNames.get(0), boundTypeExpression)) {
                addTypeWarning(Msg.JSDOC_TEMPLATE_NAME_REDECLARATION, lineno, charno);
              }
            } else {
              for (String templateName : templateNames) {
                if (!jsdocBuilder.recordTemplateTypeName(templateName)) {
                  addTypeWarning(Msg.JSDOC_TEMPLATE_NAME_REDECLARATION, lineno, charno);
                }
              }
            }

            return token;
          }

        case IDGENERATOR:
          token = parseIdGeneratorTag(next());
          return token;
        case SOY_MODULE:
        case SOY_TEMPLATE:
          return eatUntilEOLIfNotAnnotation();
        case WIZACTION:
          if (!jsdocBuilder.recordWizaction()) {
            addParserWarning(Msg.JSDOC_WIZACTION);
          }
          return eatUntilEOLIfNotAnnotation();

        case VERSION:
          ExtractionInfo versionInfo = extractSingleLineBlock();
          String version = versionInfo.string;

          if (version.isEmpty()) {
            addParserWarning(Msg.JSDOC_VERSIONMISSING);
          } else {
            if (!jsdocBuilder.recordVersion(version)) {
              addParserWarning(Msg.JSDOC_EXTRAVERSION);
            }
          }

          token = versionInfo.token;
          return token;

        case CONSTANT:
        case FINAL:
        case DEFINE:
        case EXPORT:
        case RETURN:
        case PACKAGE:
        case PRIVATE:
        case PROTECTED:
        case PUBLIC:
        case THIS:
        case TYPE:
        case TYPEDEF:
          lineno = stream.getLineno();
          charno = stream.getCharno();

          Node typeNode = null;
          boolean hasType = lookAheadForType();
          boolean isAlternateTypeAnnotation =
              annotation == Annotation.PACKAGE
                  || annotation == Annotation.PRIVATE
                  || annotation == Annotation.PROTECTED
                  || annotation == Annotation.PUBLIC
                  || annotation == Annotation.CONSTANT
                  || annotation == Annotation.FINAL
                  || annotation == Annotation.EXPORT;
          boolean canSkipTypeAnnotation =
              isAlternateTypeAnnotation || annotation == Annotation.RETURN;
          type = null;

          if (annotation == Annotation.RETURN && !hasType) {
            addMissingTypeWarning(stream.getLineno(), stream.getCharno());
          }

          if (hasType || !canSkipTypeAnnotation) {
            skipEOLs();
            token = next();
            typeNode = parseAndRecordTypeNode(token);

            if (annotation == Annotation.THIS) {
              typeNode = wrapNode(Token.BANG, typeNode);
            }
            type = createJSTypeExpression(typeNode);
          }

          // The error was reported during recursive descent
          // recovering parsing
          boolean hasError = type == null && !canSkipTypeAnnotation;
          if (!hasError) {
            // Record types for @type.
            // If the @package, @private, @protected, or @public annotations
            // have a type attached, pretend that they actually wrote:
            // @type {type}\n@private
            // This will have some weird behavior in some cases
            // (for example, @private can now be used as a type-cast),
            // but should be mostly OK.
            if (((type != null && isAlternateTypeAnnotation) || annotation == Annotation.TYPE)
                && !jsdocBuilder.recordType(type)) {
              addTypeWarning(Msg.JSDOC_INCOMPAT_TYPE, lineno, charno);
            }

            boolean isAnnotationNext = lookAheadForAnnotation();

            switch (annotation) {
              case CONSTANT:
                if (!jsdocBuilder.recordConstancy()) {
                  addParserWarning(Msg.JSDOC_CONST);
                }
                break;

              case FINAL:
                if (!jsdocBuilder.recordFinality()) {
                  addTypeWarning(Msg.JSDOC_FINAL);
                }
                break;

              case DEFINE:
                if (!jsdocBuilder.recordDefineType(type)) {
                  addParserWarning(Msg.JSDOC_DEFINE, lineno, charno);
                }
                if (!isAnnotationNext) {
                  return recordDescription(token);
                }
                break;

              case EXPORT:
                if (!jsdocBuilder.recordExport()) {
                  addParserWarning(Msg.JSDOC_EXPORT, lineno, charno);
                }
                if (!isAnnotationNext) {
                  return recordDescription(token);
                }
                break;

              case PRIVATE:
                if (!jsdocBuilder.recordVisibility(Visibility.PRIVATE)) {
                  addParserWarning(Msg.JSDOC_EXTRA_VISIBILITY, lineno, charno);
                }
                if (!isAnnotationNext) {
                  return recordDescription(token);
                }
                break;

              case PACKAGE:
                if (!jsdocBuilder.recordVisibility(Visibility.PACKAGE)) {
                  addParserWarning(Msg.JSDOC_EXTRA_VISIBILITY, lineno, charno);
                }
                if (!isAnnotationNext) {
                  return recordDescription(token);
                }
                break;

              case PROTECTED:
                if (!jsdocBuilder.recordVisibility(Visibility.PROTECTED)) {
                  addParserWarning(Msg.JSDOC_EXTRA_VISIBILITY, lineno, charno);
                }
                if (!isAnnotationNext) {
                  return recordDescription(token);
                }
                break;

              case PUBLIC:
                if (!jsdocBuilder.recordVisibility(Visibility.PUBLIC)) {
                  addParserWarning(Msg.JSDOC_EXTRA_VISIBILITY, lineno, charno);
                }
                if (!isAnnotationNext) {
                  return recordDescription(token);
                }
                break;

              case RETURN:
                if (type == null) {
                  type = createJSTypeExpression(newNode(Token.QMARK));
                }

                if (!jsdocBuilder.recordReturnType(type)) {
                  addTypeWarning(Msg.JSDOC_INCOMPAT_TYPE, lineno, charno);
                  break;
                }

                // TODO(johnlenz): The extractMultilineTextualBlock method
                // and friends look directly at the stream, regardless of
                // last token read, so we don't want to read the first
                // "STRING" out of the stream.

                // Find the return's description (if applicable).
                if (jsdocBuilder.shouldParseDocumentation() && !isAnnotationNext) {
                  ExtractionInfo returnDescriptionInfo = extractMultilineTextualBlock(token);

                  String returnDescription = returnDescriptionInfo.string;

                  if (returnDescription.length() > 0) {
                    jsdocBuilder.recordReturnDescription(returnDescription);
                  }

                  token = returnDescriptionInfo.token;
                } else {
                  token = eatUntilEOLIfNotAnnotation();
                }
                return token;

              case THIS:
                if (!jsdocBuilder.recordThisType(type)) {
                  addTypeWarning(Msg.JSDOC_INCOMPAT_TYPE, lineno, charno);
                }
                break;

              case TYPEDEF:
                if (!jsdocBuilder.recordTypedef(type)) {
                  addTypeWarning(Msg.JSDOC_INCOMPAT_TYPE, lineno, charno);
                }
                break;
              default:
                break;
            }
          }

          return eatUntilEOLIfNotAnnotation();
      }
    }

    return next();
  }

  @Nullable
  private Node parseTtlAst(ExtractionInfo ttlExtraction, int lineno, int charno) {
    String ttlExpression = ttlExtraction.string;
    if (!ttlExpression.startsWith(TTL_START_DELIMITER)) {
      return null;
    }

    ttlExpression = ttlExpression.substring(TTL_START_DELIMITER.length());

    int endIndex = ttlExpression.indexOf(TTL_END_DELIMITER);
    if (endIndex >= 0) {
      ttlExpression = ttlExpression.substring(0, endIndex);
    } else {
      addTypeWarning(Msg.JSDOC_TEMPLATE_TYPETRANSFORMATION_MISSINGDELIMIIER, lineno, charno);
    }
    ttlExpression = ttlExpression.trim();

    if (ttlExpression.isEmpty()) {
      addTypeWarning(Msg.JSDOC_TEMPLATE_TYPETRANSFORMATION_EXPRESSIONMISSING, lineno, charno);
      return null;
    }

    // Build the AST for the type transformation
    TypeTransformationParser ttlParser =
        new TypeTransformationParser(ttlExpression, getSourceFile(), errorReporter, lineno, charno);
    if (!ttlParser.parseTypeTransformation()) {
      return null;
    }

    return ttlParser.getTypeTransformationAst();
  }

  /** The types in @template annotations must contain only letters, digits, and underscores. */
  private static boolean validTemplateTypeName(String name) {
    return name != null && !name.isEmpty() && TEMPLATE_NAME_MATCHER.matchesAllOf(name);
  }

  /**
   * Records a marker's description if there is one available and record it in the current marker.
   */
  // TODO(rishipal): This function is a misnomer as it does not record anything.
  private JsDocToken recordDescription(JsDocToken token) {
    // Find marker's description (if applicable).
    if (jsdocBuilder.shouldParseDocumentation()) {
      ExtractionInfo descriptionInfo = extractMultilineTextualBlock(token);
      token = descriptionInfo.token;
    } else {
      token = eatTokensUntilEOL(token);
    }
    return token;
  }

  private void checkExtendedTypes(List<ExtendedTypeInfo> extendedTypes) {
    for (ExtendedTypeInfo typeInfo : extendedTypes) {
      // If interface, record the multiple extended interfaces
      if (jsdocBuilder.isInterfaceRecorded()) {
        if (!jsdocBuilder.recordExtendedInterface(typeInfo.type)) {
          addParserWarning(Msg.JSDOC_EXTENDS_DUPLICATE, typeInfo.lineno, typeInfo.charno);
        }
      } else {
        if (!jsdocBuilder.recordBaseType(typeInfo.type)) {
          addTypeWarning(Msg.JSDOC_INCOMPAT_TYPE, typeInfo.lineno, typeInfo.charno);
        }
      }
    }
  }

  /**
   * Parse a {@code @suppress} tag of the form {@code @suppress&#123;warning1|warning2&#125;}.
   *
   * @param token The current token.
   */
  private JsDocToken parseSuppressTag(JsDocToken token) {
    if (token != JsDocToken.LEFT_CURLY) {
      addParserWarning(Msg.JSDOC_SUPPRESS);
      return token;
    } else {
      Set<String> suppressions = new HashSet<>();
      while (true) {
        if (match(JsDocToken.STRING)) {
          String name = stream.getString();
          if (!suppressionNames.contains(name)) {
            addParserWarning(Msg.JSDOC_SUPPRESS_UNKNOWN, name);
          }

          suppressions.add(stream.getString());
          token = next();
        } else {
          addParserWarning(Msg.JSDOC_SUPPRESS);
          return token;
        }

        if (match(JsDocToken.PIPE, JsDocToken.COMMA)) {
          token = next();
        } else {
          break;
        }
      }

      if (!match(JsDocToken.RIGHT_CURLY)) {
        addParserWarning(Msg.JSDOC_SUPPRESS);
      } else {
        token = next();
        // Find the suppressions' description (if applicable).
        if (jsdocBuilder.shouldParseDocumentation() && token != JsDocToken.ANNOTATION) {
          ExtractionInfo suppressDescriptionInfo = extractMultilineTextualBlock(token);
          String suppressDescription = suppressDescriptionInfo.string;
          jsdocBuilder.recordSuppressions(ImmutableSet.copyOf(suppressions), suppressDescription);
          token = suppressDescriptionInfo.token;
        } else if (token != JsDocToken.EOC && token != JsDocToken.EOF) {
          token = eatUntilEOLIfNotAnnotation();
          jsdocBuilder.recordSuppressions(suppressions);
        }
      }
      return token;
    }
  }

  /**
   * Parse a {@code @closurePrimitive} tag
   *
   * @param token The current token.
   */
  private JsDocToken parseClosurePrimitiveTag(JsDocToken token) {
    if (token != JsDocToken.LEFT_CURLY) {
      addParserWarning(Msg.JSDOC_MISSING_LC);
      return token;
    } else if (match(JsDocToken.STRING)) {
      String name = stream.getString();
      if (!closurePrimitiveNames.contains(name)) {
        addParserWarning(Msg.JSDOC_CLOSUREPRIMITIVE_INVALID, name);
      } else if (!jsdocBuilder.recordClosurePrimitiveId(name)) {
        addParserWarning(Msg.JSDOC_CLOSUREPRIMITIVE_EXTRA);
      }
      token = next();
    } else {
      addParserWarning(Msg.JSDOC_CLOSUREPRIMITIVE_MISSING);
      return token;
    }

    if (!match(JsDocToken.RIGHT_CURLY)) {
      addParserWarning(Msg.JSDOC_MISSING_RC);
    } else {
      token = next();
    }
    return eatUntilEOLIfNotAnnotation();
  }

  /**
   * Parse a {@code @modifies} tag of the form {@code @modifies&#123;this|arguments|param&#125;}.
   *
   * @param token The current token.
   */
  private JsDocToken parseModifiesTag(JsDocToken token) {
    if (token == JsDocToken.LEFT_CURLY) {
      Set<String> modifies = new HashSet<>();
      while (true) {
        if (match(JsDocToken.STRING)) {
          String name = stream.getString();
          if (!modifiesAnnotationKeywords.contains(name) && !jsdocBuilder.hasParameter(name)) {
            addParserWarning(Msg.JSDOC_MODIFIES_UNKNOWN, name);
          }

          modifies.add(stream.getString());
          token = next();
        } else {
          addParserWarning(Msg.JSDOC_MODIFIES);
          return token;
        }

        if (match(JsDocToken.PIPE)) {
          token = next();
        } else {
          break;
        }
      }

      if (!match(JsDocToken.RIGHT_CURLY)) {
        addParserWarning(Msg.JSDOC_MODIFIES);
      } else {
        token = next();
        if (!jsdocBuilder.recordModifies(modifies)) {
          addParserWarning(Msg.JSDOC_MODIFIES_DUPLICATE);
        }
      }
    }
    return token;
  }

  /**
   * Parse a {@code @idgenerator} tag of the form {@code @idgenerator} or
   * {@code @idgenerator&#123;consistent&#125;}.
   *
   * @param token The current token.
   */
  private JsDocToken parseIdGeneratorTag(JsDocToken token) {
    String idgenKind = "unique";
    if (token == JsDocToken.LEFT_CURLY) {
      if (match(JsDocToken.STRING)) {
        String name = stream.getString();
        if (!idGeneratorAnnotationKeywords.contains(name) && !jsdocBuilder.hasParameter(name)) {
          addParserWarning(Msg.JSDOC_IDGEN_UNKNOWN, name);
        }

        idgenKind = name;
        token = next();
      } else {
        addParserWarning(Msg.JSDOC_IDGEN_BAD);
        return token;
      }

      if (!match(JsDocToken.RIGHT_CURLY)) {
        addParserWarning(Msg.JSDOC_IDGEN_BAD);
      } else {
        token = next();
      }
    }

    switch (idgenKind) {
      case "unique":
        if (!jsdocBuilder.recordIdGenerator()) {
          addParserWarning(Msg.JSDOC_IDGEN_DUPLICATE);
        }
        break;
      case "consistent":
        if (!jsdocBuilder.recordConsistentIdGenerator()) {
          addParserWarning(Msg.JSDOC_IDGEN_DUPLICATE);
        }
        break;
      case "stable":
        if (!jsdocBuilder.recordStableIdGenerator()) {
          addParserWarning(Msg.JSDOC_IDGEN_DUPLICATE);
        }
        break;
      case "xid":
        if (!jsdocBuilder.recordXidGenerator()) {
          addParserWarning(Msg.JSDOC_IDGEN_DUPLICATE);
        }
        break;
      case "mapped":
        if (!jsdocBuilder.recordMappedIdGenerator()) {
          addParserWarning(Msg.JSDOC_IDGEN_DUPLICATE);
        }
        break;
    }

    return token;
  }

  /**
   * Looks for a type expression at the current token and if found, returns it. Note that this
   * method consumes input.
   *
   * @param token The current token.
   * @return The type expression found or null if none.
   */
  Node parseAndRecordTypeNode(JsDocToken token) {
    return parseAndRecordTypeNode(
        token, stream.getLineno(), stream.getCharno(), token == JsDocToken.LEFT_CURLY, false);
  }

  /**
   * Looks for a parameter type expression at the current token and if found, returns it. Note that
   * this method consumes input.
   *
   * @param token The current token.
   * @param lineno The line of the type expression.
   * @param startCharno The starting character position of the type expression.
   * @param matchingLC Whether the type expression starts with a "{".
   * @param onlyParseSimpleNames If true, only simple type names are parsed (via a call to
   *     parseTypeNameAnnotation instead of parseTypeExpressionAnnotation).
   * @return The type expression found or null if none.
   */
  private Node parseAndRecordTypeNode(
      JsDocToken token,
      int lineno,
      int startCharno,
      boolean matchingLC,
      boolean onlyParseSimpleNames) {
    Node typeNode;

    if (onlyParseSimpleNames) {
      typeNode = parseTypeNameAnnotation(token);
    } else {
      typeNode = parseTypeExpressionAnnotation(token);
    }

    recordTypeNode(lineno, startCharno, typeNode, matchingLC);
    return typeNode;
  }

  /**
   * Looks for a type expression at the current token and if found, returns it. Note that this
   * method consumes input.
   *
   * @param token The current token.
   * @param lineno The line of the type expression.
   * @param startCharno The starting character position of the type expression.
   * @param matchingLC Whether the type expression starts with a "{".
   * @return The type expression found or null if none.
   */
  private Node parseAndRecordTypeNameNode(
      JsDocToken token, int lineno, int startCharno, boolean matchingLC) {
    return parseAndRecordTypeNode(token, lineno, startCharno, matchingLC, true);
  }

  /**
   * Looks for a type expression at the current token and if found, returns it. Note that this
   * method consumes input.
   *
   * <p>Parameter type expressions are special for two reasons:
   *
   * <ol>
   *   <li>They must begin with '{', to distinguish type names from param names.
   *   <li>They may end in '=', to denote optionality.
   * </ol>
   *
   * @param token The current token.
   * @return The type expression found or null if none.
   */
  private Node parseAndRecordParamTypeNode(JsDocToken token) {
    checkArgument(token == JsDocToken.LEFT_CURLY);
    int lineno = stream.getLineno();
    int startCharno = stream.getCharno();

    Node typeNode = parseParamTypeExpressionAnnotation(token);
    recordTypeNode(lineno, startCharno, typeNode, true);
    return typeNode;
  }

  /** Converts a JSDoc token to its string representation. */
  private String toString(JsDocToken token) {
    switch (token) {
      case ANNOTATION:
        return "@" + stream.getString();

      case BANG:
        return "!";

      case COMMA:
        return ",";

      case COLON:
        return ":";

      case RIGHT_ANGLE:
        return ">";

      case LEFT_SQUARE:
        return "[";

      case LEFT_CURLY:
        return "{";

      case LEFT_PAREN:
        return "(";

      case LEFT_ANGLE:
        return "<";

      case QMARK:
        return "?";

      case PIPE:
        return "|";

      case RIGHT_SQUARE:
        return "]";

      case RIGHT_CURLY:
        return "}";

      case RIGHT_PAREN:
        return ")";

      case STAR:
        return "*";

      case ITER_REST:
        return "...";

      case EQUALS:
        return "=";

      case STRING:
        return stream.getString();

      default:
        throw new IllegalStateException(token.toString());
    }
  }

  /**
   * Constructs a new {@code JSTypeExpression}.
   *
   * @param n A node. May be null.
   */
  JSTypeExpression createJSTypeExpression(Node n) {
    return n == null ? null : new JSTypeExpression(n, getSourceName());
  }

  /**
   * Tuple for returning both the string extracted and the new token following a call to any of the
   * extract*Block methods.
   */
  private static class ExtractionInfo {
    private final String string;
    private final JsDocToken token;

    public ExtractionInfo(String string, JsDocToken token) {
      this.string = string;
      this.token = token;
    }
  }

  /** Tuple for recording extended types */
  private static class ExtendedTypeInfo {
    final JSTypeExpression type;
    final int lineno;
    final int charno;

    public ExtendedTypeInfo(JSTypeExpression type, int lineno, int charno) {
      this.type = type;
      this.lineno = lineno;
      this.charno = charno;
    }
  }

  /**
   * Extracts the text found on the current line starting at token. Note that token = token.info;
   * should be called after this method is used to update the token properly in the parser.
   *
   * @return The extraction information.
   */
  private ExtractionInfo extractSingleLineBlock() {

    // Get the current starting point.
    stream.update();
    int lineno = stream.getLineno();
    int charno = stream.getCharno() + 1;

    String line = getRemainingJSDocLine().trim();

    // Record the textual description.
    if (line.length() > 0) {
      jsdocBuilder.markText(line, lineno, charno, lineno, charno + line.length());
    }

    return new ExtractionInfo(line, next());
  }

  private ExtractionInfo extractMultilineTextualBlock(JsDocToken token) {
    return extractMultilineTextualBlock(
        token, getWhitespaceOption(WhitespaceOption.SINGLE_LINE), false);
  }

  /**
   * Extracts the text found on the current line and all subsequent until either an annotation, end
   * of comment or end of file is reached. Note that if this method detects an end of line as the
   * first token, it will quit immediately (indicating that there is no text where it was expected).
   * Note that token = info.token; should be called after this method is used to update the token
   * properly in the parser.
   *
   * @param token The start token.
   * @param option How to handle whitespace.
   * @param includeAnnotations Whether the extracted text may include annotations. If set to false,
   *     text extraction will stop on the first encountered annotation token.
   * @return The extraction information.
   */
  private ExtractionInfo extractMultilineTextualBlock(
      JsDocToken token, WhitespaceOption option, boolean includeAnnotations) {
    if (token == JsDocToken.EOC || token == JsDocToken.EOL || token == JsDocToken.EOF) {
      return new ExtractionInfo("", token);
    }
    return extractMultilineComment(token, option, true, includeAnnotations);
  }

  private WhitespaceOption getWhitespaceOption(WhitespaceOption defaultValue) {
    return preserveWhitespace ? WhitespaceOption.PRESERVE : defaultValue;
  }

  private enum WhitespaceOption {
    /**
     * Preserves all whitespace and formatting. Needed for licenses and purposely formatted text.
     */
    PRESERVE,

    /** Preserves newlines but trims the output. */
    TRIM,

    /** Removes newlines and turns the output into a single line string. */
    SINGLE_LINE
  }

  /**
   * Extracts the top-level block comment from the JsDoc comment, if any. This method differs from
   * the extractMultilineTextualBlock in that it terminates under different conditions (it doesn't
   * have the same prechecks), it does not first read in the remaining of the current line and its
   * conditions for ignoring the "*" (STAR) are different.
   *
   * @param token The starting token.
   * @return The extraction information.
   */
  private ExtractionInfo extractBlockComment(JsDocToken token) {
    return extractMultilineComment(token, getWhitespaceOption(WhitespaceOption.TRIM), false, false);
  }

  /**
   * Extracts text from the stream until the end of the comment, end of the file, or an annotation
   * token is encountered. If the text is being extracted for a JSDoc marker, the first line in the
   * stream will always be included in the extract text.
   *
   * @param token The starting token.
   * @param option How to handle whitespace.
   * @param isMarker Whether the extracted text is for a JSDoc marker or a block comment.
   * @param includeAnnotations Whether the extracted text may include annotations. If set to false,
   *     text extraction will stop on the first encountered annotation token.
   * @return The extraction information.
   */
  private ExtractionInfo extractMultilineComment(
      JsDocToken token, WhitespaceOption option, boolean isMarker, boolean includeAnnotations) {

    StringBuilder builder = new StringBuilder();
    int startLineno = -1;
    int startCharno = -1;

    if (isMarker) {
      stream.update();
      startLineno = stream.getLineno();
      startCharno = stream.getCharno() + 1;

      String line = getRemainingJSDocLine();
      if (option != WhitespaceOption.PRESERVE) {
        line = line.trim();
      }
      builder.append(line);

      state = State.SEARCHING_ANNOTATION;
      token = next();
    }

    boolean ignoreStar = false;

    // Track the start of the line to count whitespace that
    // the tokenizer skipped. Because this case is rare, it's easier
    // to do this here than in the tokenizer.
    int lineStartChar = -1;

    do {
      switch (token) {
        case STAR:
          if (ignoreStar) {
            // Mark the position after the star as the new start of the line.
            lineStartChar = stream.getCharno() + 1;
            ignoreStar = false;
          } else {
            // The star is part of the comment.
            padLine(builder, lineStartChar, option);
            lineStartChar = -1;
            builder.append('*');
          }

          token = next();
          while (token == JsDocToken.STAR) {
            if (lineStartChar != -1) {
              padLine(builder, lineStartChar, option);
              lineStartChar = -1;
            }
            builder.append('*');
            token = next();
          }
          continue;

        case EOL:
          if (option != WhitespaceOption.SINGLE_LINE) {
            builder.append('\n');
          }

          ignoreStar = true;
          lineStartChar = 0;
          token = next();
          continue;

        default:
          ignoreStar = false;
          state = State.SEARCHING_ANNOTATION;

          boolean isEOC = token == JsDocToken.EOC;
          if (!isEOC) {
            padLine(builder, lineStartChar, option);
            lineStartChar = -1;
          }

          if (token == JsDocToken.EOC
              || token == JsDocToken.EOF
              ||
              // When we're capturing a license block, annotations
              // in the block are OK.
              (token == JsDocToken.ANNOTATION && !includeAnnotations)) {
            String multilineText = builder.toString();

            if (option != WhitespaceOption.PRESERVE) {
              multilineText = multilineText.trim();
            }

            if (isMarker && !multilineText.isEmpty()) {
              int endLineno = stream.getLineno();
              int endCharno = stream.getCharno();
              jsdocBuilder.markText(multilineText, startLineno, startCharno, endLineno, endCharno);
            }

            return new ExtractionInfo(multilineText, token);
          }

          builder.append(toString(token));

          String line = getRemainingJSDocLine();

          if (option != WhitespaceOption.PRESERVE) {
            line = trimEnd(line);
          }

          builder.append(line);
          token = next();
      }
    } while (true);
  }

  private void padLine(StringBuilder builder, int lineStartChar, WhitespaceOption option) {
    if (lineStartChar != -1 && option == WhitespaceOption.PRESERVE) {
      int numSpaces = stream.getCharno() - lineStartChar;
      for (int i = 0; i < numSpaces; i++) {
        builder.append(' ');
      }
    } else if (builder.length() > 0) {
      if (builder.charAt(builder.length() - 1) != '\n' || option == WhitespaceOption.PRESERVE) {
        builder.append(' ');
      }
    }
  }

  /**
   * Trim characters from only the end of a string. This method will remove all whitespace
   * characters (defined by TokenUtil.isWhitespace(char), in addition to the characters provided,
   * from the end of the provided string.
   *
   * @param s String to be trimmed
   * @return String with whitespace and characters in extraChars removed from the end.
   */
  private static String trimEnd(String s) {
    int trimCount = 0;
    while (trimCount < s.length()) {
      char ch = s.charAt(s.length() - trimCount - 1);
      if (TokenUtil.isWhitespace(ch)) {
        trimCount++;
      } else {
        break;
      }
    }

    if (trimCount == 0) {
      return s;
    }
    return s.substring(0, s.length() - trimCount);
  }

  // Based on ES4 grammar proposed on July 10, 2008.
  // http://wiki.ecmascript.org/doku.php?id=spec:spec
  // Deliberately written to line up with the actual grammar rules,
  // for maximum flexibility.

  // TODO(nicksantos): The current implementation tries to maintain backwards
  // compatibility with previous versions of the spec whenever we can.
  // We should try to gradually withdraw support for these.

  /** TypeExpressionAnnotation := TypeExpression | '{' TopLevelTypeExpression '}' */
  private Node parseTypeExpressionAnnotation(JsDocToken token) {
    if (token == JsDocToken.LEFT_CURLY) {
      skipEOLs();
      Node typeNode = parseTopLevelTypeExpression(next());
      if (typeNode != null) {
        skipEOLs();
        if (!match(JsDocToken.RIGHT_CURLY)) {
          if (typeNode.isStringLit() && "import".equals(typeNode.getString())) {
            reportTypeSyntaxWarning(Msg.JSDOC_IMPORT);
          } else {
            reportTypeSyntaxWarning(Msg.JSDOC_MISSING_RC);
          }
        } else {
          next();
        }
      }

      return typeNode;
    } else {
      // TODO(tbreisacher): Add a SuggestedFix for this warning.
      reportTypeSyntaxWarning(Msg.JSDOC_MISSING_BRACES);
      return parseTypeExpression(token);
    }
  }

  /**
   * Parse a ParamTypeExpression:
   *
   * <pre>
   * ParamTypeExpression :=
   *     OptionalParameterType |
   *     TopLevelTypeExpression |
   *     '...' TopLevelTypeExpression
   *
   * OptionalParameterType :=
   *     TopLevelTypeExpression '='
   * </pre>
   */
  private Node parseParamTypeExpression(JsDocToken token) {
    boolean restArg = false;
    if (token == JsDocToken.ITER_REST) {
      token = next();
      if (token == JsDocToken.RIGHT_CURLY) {
        restoreLookAhead(token);
        // EMPTY represents the UNKNOWN type in the Type AST.
        return wrapNode(Token.ITER_REST, newNode(Token.EMPTY));
      }
      restArg = true;
    }

    Node typeNode = parseTopLevelTypeExpression(token);
    if (typeNode != null) {
      skipEOLs();
      if (restArg) {
        typeNode = wrapNode(Token.ITER_REST, typeNode);
      } else if (match(JsDocToken.EQUALS)) {
        next();
        skipEOLs();
        typeNode = wrapNode(Token.EQUALS, typeNode);
      }
    }

    return typeNode;
  }

  /** ParamTypeExpressionAnnotation := '{' ParamTypeExpression '}' */
  private Node parseParamTypeExpressionAnnotation(JsDocToken token) {
    checkArgument(token == JsDocToken.LEFT_CURLY);

    skipEOLs();

    Node typeNode = parseParamTypeExpression(next());
    if (typeNode != null) {
      if (!match(JsDocToken.RIGHT_CURLY)) {
        reportTypeSyntaxWarning(Msg.JSDOC_MISSING_RC);
      } else {
        next();
      }
    }

    return typeNode;
  }

  /** TypeNameAnnotation := TypeName | '{' TypeName '}' */
  private Node parseTypeNameAnnotation(JsDocToken token) {
    if (token == JsDocToken.LEFT_CURLY) {
      skipEOLs();
      Node typeNode = parseTypeName(next());
      if (typeNode != null) {
        skipEOLs();
        if (!match(JsDocToken.RIGHT_CURLY)) {
          reportTypeSyntaxWarning(Msg.JSDOC_MISSING_RC);
        } else {
          next();
        }
      }

      return typeNode;
    } else {
      return parseTypeName(token);
    }
  }

  /**
   * TopLevelTypeExpression := TypeExpression | TypeUnionList
   *
   * <p>We made this rule up, for the sake of backwards compatibility.
   */
  private Node parseTopLevelTypeExpression(JsDocToken token) {
    Node typeExpr = parseTypeExpression(token);
    if (typeExpr != null) {
      // top-level unions are allowed
      if (match(JsDocToken.PIPE)) {
        next();
        skipEOLs();
        token = next();
        return parseUnionTypeWithAlternate(token, typeExpr);
      }
    }
    return typeExpr;
  }

  /**
   * TypeExpressionList := TopLevelTypeExpression | TopLevelTypeExpression ',' TypeExpressionList
   */
  private Node parseTypeExpressionList(String typeName, JsDocToken token) {
    Node typeExpr = parseTopLevelTypeExpression(token);
    if (typeExpr == null) {
      return null;
    }
    Node typeList = newNode(Token.BLOCK);
    int numTypeExprs = 1;
    typeList.addChildToBack(typeExpr);
    while (match(JsDocToken.COMMA)) {
      next();
      skipEOLs();
      typeExpr = parseTopLevelTypeExpression(next());
      if (typeExpr == null) {
        return null;
      }
      numTypeExprs++;
      typeList.addChildToBack(typeExpr);
    }
    if (typeName.equals("Object") && numTypeExprs == 1) {
      // Unlike other generic types, Object<V> means Object<?, V>, not Object<V, ?>.
      typeList.addChildToFront(newNode(Token.QMARK));
    }
    return typeList;
  }

  /**
   * TypeExpression := BasicTypeExpression | '?' BasicTypeExpression | '!' BasicTypeExpression |
   * BasicTypeExpression '?' | BasicTypeExpression '!' | '?'
   */
  private Node parseTypeExpression(JsDocToken token) {
    // Save the source position before we consume additional tokens.
    int lineno = stream.getLineno();
    int charno = stream.getCharno();
    if (token == JsDocToken.QMARK) {
      // A QMARK could mean that a type is nullable, or that it's unknown.
      // We use look-ahead 1 to determine whether it's unknown. Otherwise,
      // we assume it means nullable. There are 8 cases:
      // {?} - right curly
      // ? - EOF (possible when the parseTypeString method is given a bare type expression)
      // {?=} - equals
      // {function(?, number)} - comma
      // {function(number, ?)} - right paren
      // {function(): ?|number} - pipe
      // {Array.<?>} - greater than
      // /** ? */ - EOC (inline types)
      // I'm not a big fan of using look-ahead for this, but it makes
      // the type language a lot nicer.
      token = next();
      if (token == JsDocToken.COMMA
          || token == JsDocToken.EQUALS
          || token == JsDocToken.RIGHT_SQUARE
          || token == JsDocToken.RIGHT_CURLY
          || token == JsDocToken.RIGHT_PAREN
          || token == JsDocToken.PIPE
          || token == JsDocToken.RIGHT_ANGLE
          || token == JsDocToken.EOC
          || token == JsDocToken.EOL
          || token == JsDocToken.EOF) {
        restoreLookAhead(token);
        return newNode(Token.QMARK);
      }

      return wrapNode(Token.QMARK, parseBasicTypeExpression(token), lineno, charno);
    } else if (token == JsDocToken.BANG) {
      return wrapNode(Token.BANG, parseBasicTypeExpression(next()), lineno, charno);
    } else {
      Node basicTypeExpr = parseBasicTypeExpression(token);
      lineno = stream.getLineno();
      charno = stream.getCharno();
      if (basicTypeExpr != null) {
        if (match(JsDocToken.QMARK)) {
          next();
          return wrapNode(Token.QMARK, basicTypeExpr, lineno, charno);
        } else if (match(JsDocToken.BANG)) {
          next();
          return wrapNode(Token.BANG, basicTypeExpr, lineno, charno);
        }
      }

      return basicTypeExpr;
    }
  }

  /**
   * ContextTypeExpression := BasicTypeExpression | '?' For expressions on the right hand side of a
   * this: or new:
   */
  private Node parseContextTypeExpression(JsDocToken token) {
    if (token == JsDocToken.QMARK) {
      return newNode(Token.QMARK);
    } else {
      return parseBasicTypeExpression(token);
    }
  }

  /**
   * BasicTypeExpression := '*' | 'null' | 'undefined' | TypeName | FunctionType | UnionType |
   * RecordType | TypeofType
   */
  private Node parseBasicTypeExpression(JsDocToken token) {
    if (token == JsDocToken.STAR) {
      return newNode(Token.STAR);
    } else if (token == JsDocToken.LEFT_CURLY) {
      skipEOLs();
      return parseRecordType(next());
    } else if (token == JsDocToken.LEFT_PAREN) {
      skipEOLs();
      return parseUnionType(next());
    } else if (token == JsDocToken.STRING) {
      String string = stream.getString();
      switch (string) {
        case "function":
          skipEOLs();
          return parseFunctionType(next());
        case "null":
        case "undefined":
          return newStringNode(string);
        case "typeof":
          skipEOLs();
          return parseTypeofType(next());
        default:
          return parseTypeName(token);
      }
    }

    restoreLookAhead(token);
    return reportGenericTypeSyntaxWarning();
  }

  private Node parseNameExpression(JsDocToken token) {
    if (token != JsDocToken.STRING) {
      addParserWarning(Msg.JSDOC_NAME_SYNTAX, stream.getLineno(), stream.getCharno());
      return null;
    }

    final int startLineno = stream.getLineno();
    final int startCharno = stream.getCharno();
    final int startOffset = stream.getCursor() - stream.getString().length();

    StringBuilder typeName = new StringBuilder(stream.getString());
    int endOffset = stream.getCursor();
    while (match(JsDocToken.EOL) && typeName.toString().endsWith(".")) {
      skipEOLs();
      if (match(JsDocToken.STRING)) {
        next();
        endOffset = stream.getCursor();
        typeName.append(stream.getString());
      }
    }

    // What we're doing by concatenating these tokens is really hacky. We want that to be obvious.
    Node str = newStringNode(typeName.toString());
    str.setLinenoCharno(startLineno, startCharno);
    str.setLength(endOffset - startOffset);
    return str;
  }

  /**
   * Parse a TypeName:
   *
   * <pre>{@code
   * TypeName := NameExpression | NameExpression TypeApplication
   * TypeApplication := '.'? '<' TypeExpressionList '>'
   * }</pre>
   */
  private Node parseTypeName(JsDocToken token) {
    Node typeNameNode = parseNameExpression(token);

    if (match(JsDocToken.LEFT_ANGLE)) {
      next();
      skipEOLs();
      Node memberType = parseTypeExpressionList(typeNameNode.getString(), next());
      if (memberType != null) {
        typeNameNode.addChildToFront(memberType);

        skipEOLs();
        if (!match(JsDocToken.RIGHT_ANGLE)) {
          return reportTypeSyntaxWarning(Msg.JSDOC_MISSING_GT);
        }

        next();
      }
    }
    return typeNameNode;
  }

  /** TypeofType := 'typeof' NameExpression | 'typeof' '(' NameExpression ')' */
  private Node parseTypeofType(JsDocToken token) {
    if (token == JsDocToken.LEFT_CURLY) {
      return reportTypeSyntaxWarning(Msg.JSDOC_UNNECESSARY_BRACES);
    }

    Node typeofType = newNode(Token.TYPEOF);
    Node name = parseNameExpression(token);
    if (name == null) {
      return null;
    }

    skipEOLs();
    typeofType.addChildToFront(name);
    return typeofType;
  }

  /**
   * Parse a FunctionType:
   *
   * <pre>
   * FunctionType := 'function' FunctionSignatureType
   * FunctionSignatureType :=
   *    TypeParameters '(' 'this' ':' TypeName, ParametersType ')' ResultType
   * </pre>
   *
   * <p>The Node that is produced has type Token.FUNCTION but does not look like a typical function
   * node. If there is a 'this:' or 'new:' type, that type is added as a child. Then, if there are
   * parameters, a PARAM_LIST node is added as a child. Finally, if there is a return type, it is
   * added as a child. This means that the parameters could be the first or second child, and the
   * return type could be the first, second, or third child.
   */
  private Node parseFunctionType(JsDocToken token) {
    // NOTE(nicksantos): We're not implementing generics at the moment, so
    // just throw out TypeParameters.
    if (token != JsDocToken.LEFT_PAREN) {
      restoreLookAhead(token);
      return reportTypeSyntaxWarning(Msg.JSDOC_MISSING_LP);
    }

    Node functionType = newNode(Token.FUNCTION);
    Node parameters = null;
    skipEOLs();
    if (!match(JsDocToken.RIGHT_PAREN)) {
      token = next();

      boolean hasParams = true;
      if (token == JsDocToken.STRING) {
        String tokenStr = stream.getString();
        boolean isThis = "this".equals(tokenStr);
        boolean isNew = "new".equals(tokenStr);
        if (isThis || isNew) {
          if (match(JsDocToken.COLON)) {
            next();
            skipEOLs();
            Node contextType =
                wrapNode(isThis ? Token.THIS : Token.NEW, parseContextTypeExpression(next()));
            if (contextType == null) {
              return null;
            }

            functionType.addChildToFront(contextType);
          } else {
            return reportTypeSyntaxWarning(Msg.JSDOC_MISSING_COLON);
          }

          if (match(JsDocToken.COMMA)) {
            next();
            skipEOLs();
            token = next();
          } else {
            hasParams = false;
          }
        }
      }

      if (hasParams) {
        parameters = parseParametersType(token);
        if (parameters == null) {
          return null;
        }
      }
    }

    if (parameters != null) {
      functionType.addChildToBack(parameters);
    }

    skipEOLs();
    if (!match(JsDocToken.RIGHT_PAREN)) {
      return reportTypeSyntaxWarning(Msg.JSDOC_MISSING_RP);
    }

    skipEOLs();
    next();
    Node resultType = parseResultType();
    if (resultType == null) {
      return null;
    } else {
      functionType.addChildToBack(resultType);
    }
    return functionType;
  }

  /**
   * Parse a ParametersType:
   *
   * <pre>
   * ParametersType := RestParameterType | NonRestParametersType
   *     | NonRestParametersType ',' RestParameterType
   * RestParameterType := '...' Identifier
   * NonRestParametersType := ParameterType ',' NonRestParametersType
   *     | ParameterType
   *     | OptionalParametersType
   * OptionalParametersType := OptionalParameterType
   *     | OptionalParameterType, OptionalParametersType
   * OptionalParameterType := ParameterType=
   * ParameterType := TypeExpression | Identifier ':' TypeExpression
   * </pre>
   */
  // NOTE(nicksantos): The official ES4 grammar forces optional and rest
  // arguments to come after the required arguments. Our parser does not
  // enforce this. Instead we allow them anywhere in the function at parse-time,
  // and then warn about them during type resolution.
  //
  // In theory, it might be mathematically nicer to do the order-checking here.
  // But in practice, the order-checking for structural functions is exactly
  // the same as the order-checking for @param annotations. And the latter
  // has to happen during type resolution. Rather than duplicate the
  // order-checking in two places, we just do all of it in type resolution.
  private Node parseParametersType(JsDocToken token) {
    Node paramsType = newNode(Token.PARAM_LIST);
    boolean isVarArgs = false;
    Node paramType = null;
    if (token != JsDocToken.RIGHT_PAREN) {
      do {
        if (paramType != null) {
          // skip past the comma
          next();
          skipEOLs();
          token = next();
        }

        if (token == JsDocToken.ITER_REST) {
          // In the latest ES4 proposal, there are no type constraints allowed
          // on variable arguments. We support the old syntax for backwards
          // compatibility, but we should gradually tear it out.
          skipEOLs();
          if (match(JsDocToken.RIGHT_PAREN)) {
            paramType = newNode(Token.ITER_REST);
          } else {
            skipEOLs();
            paramType = wrapNode(Token.ITER_REST, parseTypeExpression(next()));
            skipEOLs();
          }

          isVarArgs = true;
        } else {
          paramType = parseTypeExpression(token);
          if (match(JsDocToken.EQUALS)) {
            skipEOLs();
            next();
            paramType = wrapNode(Token.EQUALS, paramType);
          }
        }

        if (paramType == null) {
          return null;
        }
        paramsType.addChildToBack(paramType);
        if (isVarArgs) {
          break;
        }
      } while (match(JsDocToken.COMMA));
    }

    if (isVarArgs && match(JsDocToken.COMMA)) {
      return reportTypeSyntaxWarning(Msg.JSDOC_FUNCTION_VARARGS);
    }

    // The right paren will be checked by parseFunctionType

    return paramsType;
  }

  /** ResultType := <empty> | ':' void | ':' TypeExpression */
  private Node parseResultType() {
    skipEOLs();
    if (!match(JsDocToken.COLON)) {
      return newNode(Token.EMPTY);
    }

    next();
    skipEOLs();
    if (match(JsDocToken.STRING) && "void".equals(stream.getString())) {
      next();
      return newNode(Token.VOID);
    } else {
      return parseTypeExpression(next());
    }
  }

  /**
   * UnionType := '(' TypeUnionList ')' TypeUnionList := TypeExpression | TypeExpression '|'
   * TypeUnionList
   *
   * <p>We've removed the empty union type.
   */
  private Node parseUnionType(JsDocToken token) {
    return parseUnionTypeWithAlternate(token, null);
  }

  /**
   * Create a new union type, with an alternate that has already been parsed. The alternate may be
   * null.
   */
  private Node parseUnionTypeWithAlternate(JsDocToken token, Node alternate) {
    Node union = newNode(Token.PIPE);
    if (alternate != null) {
      union.addChildToBack(alternate);
    }

    Node expr = null;
    do {
      if (expr != null) {
        skipEOLs();
        token = next();
        checkState(token == JsDocToken.PIPE);

        skipEOLs();
        token = next();
      }
      expr = parseTypeExpression(token);
      if (expr == null) {
        return null;
      }

      union.addChildToBack(expr);
    } while (match(JsDocToken.PIPE));

    if (alternate == null) {
      skipEOLs();
      if (!match(JsDocToken.RIGHT_PAREN)) {
        return reportTypeSyntaxWarning(Msg.JSDOC_MISSING_RP);
      }
      next();
    }
    if (union.hasOneChild()) {
      Node firstChild = union.getFirstChild();
      firstChild.detach();
      return firstChild;
    }
    return union;
  }

  /** RecordType := '{' FieldTypeList '}' */
  private Node parseRecordType(JsDocToken token) {
    Node recordType = newNode(Token.LC);
    Node fieldTypeList = parseFieldTypeList(token);

    if (fieldTypeList == null) {
      return reportGenericTypeSyntaxWarning();
    }

    skipEOLs();
    if (!match(JsDocToken.RIGHT_CURLY)) {
      return reportTypeSyntaxWarning(Msg.JSDOC_MISSING_RC);
    }

    next();

    recordType.addChildToBack(fieldTypeList);
    return recordType;
  }

  /** FieldTypeList := FieldType | FieldType ',' FieldTypeList */
  private Node parseFieldTypeList(JsDocToken token) {
    Node fieldTypeList = newNode(Token.LB);

    Set<String> names = new HashSet<>();

    do {
      Node fieldType = parseFieldType(token);

      if (fieldType == null) {
        return null;
      }

      String name =
          fieldType.isStringKey() ? fieldType.getString() : fieldType.getFirstChild().getString();
      if (names.add(name)) {
        fieldTypeList.addChildToBack(fieldType);
      } else {
        addTypeWarning(Msg.JSDOC_TYPE_RECORD_DUPLICATE, name);
      }

      skipEOLs();
      if (!match(JsDocToken.COMMA)) {
        break;
      }

      // Move to the comma token.
      next();

      // Move to the token past the comma
      skipEOLs();

      if (match(JsDocToken.RIGHT_CURLY)) {
        // Allow trailing comma (ie, right curly following the comma)
        break;
      }

      token = next();
    } while (true);

    return fieldTypeList;
  }

  /** FieldType := FieldName | FieldName ':' TypeExpression */
  private Node parseFieldType(JsDocToken token) {
    Node fieldName = parseFieldName(token);

    if (fieldName == null) {
      return null;
    }

    skipEOLs();
    if (!match(JsDocToken.COLON)) {
      return fieldName;
    }

    // Move to the colon.
    next();

    // Move to the token after the colon and parse
    // the type expression.
    skipEOLs();
    Node typeExpression = parseTypeExpression(next());

    if (typeExpression == null) {
      return null;
    }

    Node fieldType = newNode(Token.COLON);
    fieldType.addChildToBack(fieldName);
    fieldType.addChildToBack(typeExpression);
    return fieldType;
  }

  /** FieldName := NameExpression | StringLiteral | NumberLiteral | ReservedIdentifier */
  private Node parseFieldName(JsDocToken token) {
    if (token != JsDocToken.STRING) {
      return null;
    }
    String s = stream.getString();
    Node n =
        Node.newString(Token.STRING_KEY, s)
            .setLinenoCharno(stream.getLineno(), stream.getCharno())
            .clonePropsFrom(templateNode);
    n.setLength(s.length());
    return n;
  }

  private Node wrapNode(Token type, Node n) {
    return n == null ? null : wrapNode(type, n, n.getLineno(), n.getCharno());
  }

  private Node wrapNode(Token type, Node n, int lineno, int charno) {
    return n == null
        ? null
        : new Node(type, n).setLinenoCharno(lineno, charno).clonePropsFrom(templateNode);
  }

  private Node newNode(Token type) {
    return new Node(type)
        .setLinenoCharno(stream.getLineno(), stream.getCharno())
        .clonePropsFrom(templateNode);
  }

  private Node newStringNode(String s) {
    Node n =
        Node.newString(s)
            .setLinenoCharno(stream.getLineno(), stream.getCharno())
            .clonePropsFrom(templateNode);
    n.setLength(s.length());
    return n;
  }

  private Node reportTypeSyntaxWarning(Msg warning) {
    addTypeWarning(warning, stream.getLineno(), stream.getCharno());
    return null;
  }

  private Node reportGenericTypeSyntaxWarning() {
    return reportTypeSyntaxWarning(Msg.JSDOC_TYPE_SYNTAX);
  }

  private JsDocToken eatUntilEOLIfNotAnnotation() {
    return eatUntilEOLIfNotAnnotation(next());
  }

  private JsDocToken eatUntilEOLIfNotAnnotation(JsDocToken token) {
    if (token == JsDocToken.ANNOTATION) {
      state = State.SEARCHING_ANNOTATION;
      return token;
    }
    return eatTokensUntilEOL(token);
  }

  /**
   * Eats tokens until {@link JsDocToken#EOL} included, and switches back the state to {@link
   * State#SEARCHING_ANNOTATION}.
   */
  private JsDocToken eatTokensUntilEOL() {
    return eatTokensUntilEOL(next());
  }

  /**
   * Eats tokens until {@link JsDocToken#EOL} included, and switches back the state to {@link
   * State#SEARCHING_ANNOTATION}.
   */
  private JsDocToken eatTokensUntilEOL(JsDocToken token) {
    do {
      if (token == JsDocToken.EOL || token == JsDocToken.EOC || token == JsDocToken.EOF) {
        state = State.SEARCHING_ANNOTATION;
        return token;
      }
      token = next();
    } while (true);
  }

  /** Specific value indicating that the {@link #unreadToken} contains no token. */
  private static final JsDocToken NO_UNREAD_TOKEN = null;

  /** One token buffer. */
  private JsDocToken unreadToken = NO_UNREAD_TOKEN;

  /** Restores the lookahead token to the token stream */
  private void restoreLookAhead(JsDocToken token) {
    unreadToken = token;
  }

  /** Tests whether the next symbol of the token stream matches the specific token. */
  private boolean match(JsDocToken token) {
    unreadToken = next();
    return unreadToken == token;
  }

  /** Tests that the next symbol of the token stream matches one of the specified tokens. */
  private boolean match(JsDocToken token1, JsDocToken token2) {
    unreadToken = next();
    return unreadToken == token1 || unreadToken == token2;
  }

  /** If the next token matches {@code expected}, consume it and return {@code true}. */
  private boolean eatIfMatch(JsDocToken token) {
    if (match(token)) {
      next();
      return true;
    }
    return false;
  }

  /**
   * Gets the next token of the token stream or the buffered token if a matching was previously
   * made.
   */
  private JsDocToken next() {
    if (unreadToken == NO_UNREAD_TOKEN) {
      return stream.getJsDocToken();
    } else {
      return current();
    }
  }

  /** Gets the current token, invalidating it in the process. */
  private JsDocToken current() {
    JsDocToken t = unreadToken;
    unreadToken = NO_UNREAD_TOKEN;
    return t;
  }

  /**
   * Skips all EOLs and all empty lines in the JSDoc. Call this method if you want the JSDoc entry
   * to span multiple lines.
   */
  private void skipEOLs() {
    while (match(JsDocToken.EOL)) {
      next();
      if (match(JsDocToken.STAR)) {
        next();
      }
    }
  }

  /** Returns the remainder of the line. */
  private String getRemainingJSDocLine() {
    String result = stream.getRemainingJSDocLine();
    unreadToken = NO_UNREAD_TOKEN;
    return result;
  }

  /** Determines whether the parser has been populated with docinfo with a fileoverview tag. */
  private boolean hasParsedFileOverviewDocInfo() {
    return jsdocBuilder.isPopulatedWithFileOverview();
  }

  public JSDocInfo retrieveAndResetParsedJSDocInfo() {
    return jsdocBuilder.build();
  }

  /** Gets the fileoverview JSDocInfo, if any. */
  JSDocInfo getFileOverviewJSDocInfo() {
    return fileOverviewJSDocInfo;
  }

  /**
   * Look ahead for a type annotation by advancing the character stream. Does not modify the token
   * stream. This is kind of a hack, and is only necessary because we use the token stream to parse
   * types, but need the underlying character stream to get JsDoc descriptions.
   *
   * @return Whether we found a type annotation.
   */
  private boolean lookAheadForType() {
    return lookAheadFor('{');
  }

  private boolean lookAheadForAnnotation() {
    return lookAheadFor('@');
  }

  /**
   * Look ahead by advancing the character stream. Does not modify the token stream.
   *
   * @return Whether we found the char.
   */
  private boolean lookAheadFor(char expect) {
    boolean matched = false;
    int c;
    while (true) {
      c = stream.getChar();
      if (c == ' ') {
        continue;
      } else if (c == expect) {
        matched = true;
        break;
      } else {
        break;
      }
    }
    stream.ungetChar(c);
    return matched;
  }
}
