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

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleErrorReporter;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;
import com.google.javascript.rhino.TokenUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A parser for JSDoc comments.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
// TODO(nicksantos): Unify all the JSDocInfo stuff into one package, instead of
// spreading it across multiple packages.
public final class JsDocInfoParser {

  private final JsDocTokenStream stream;
  private final JSDocInfoBuilder jsdocBuilder;
  private final StaticSourceFile sourceFile;
  private final ErrorReporter errorReporter;

  // Use a template node for properties set on all nodes to minimize the
  // memory footprint associated with these (similar to IRFactory).
  private final Node templateNode;


  private void addParserWarning(String messageId, String messageArg) {
    addParserWarning(messageId, messageArg, stream.getLineno(), stream.getCharno());
  }

  private void addParserWarning(String messageId, String messageArg, int lineno, int charno) {
    errorReporter.warning(
        SimpleErrorReporter.getMessage1(messageId, messageArg), getSourceName(), lineno, charno);
  }

  private void addParserWarning(String messageId) {
    addParserWarning(messageId, stream.getLineno(), stream.getCharno());
  }

  private void addParserWarning(String messageId, int lineno, int charno) {
    errorReporter.warning(
        SimpleErrorReporter.getMessage0(messageId), getSourceName(), lineno, charno);
  }

  private void addTypeWarning(String messageId, String messageArg) {
    addTypeWarning(messageId, messageArg, stream.getLineno(), stream.getCharno());
  }

  private void addTypeWarning(String messageId, String messageArg, int lineno, int charno) {
    errorReporter.warning(
        "Bad type annotation. " + SimpleErrorReporter.getMessage1(messageId, messageArg),
        getSourceName(),
        lineno,
        charno);
  }

  private void addTypeWarning(String messageId) {
    addTypeWarning(messageId, stream.getLineno(), stream.getCharno());
  }

  private void addTypeWarning(String messageId, int lineno, int charno) {
    errorReporter.warning(
        "Bad type annotation. " + SimpleErrorReporter.getMessage0(messageId),
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

  private final Map<String, Annotation> annotationNames;
  private final Set<String> suppressionNames;
  private final boolean preserveWhitespace;
  private static final Set<String> modifiesAnnotationKeywords =
      ImmutableSet.of("this", "arguments");
  private static final Set<String> idGeneratorAnnotationKeywords =
      ImmutableSet.of("unique", "consistent", "stable", "mapped");

  private JSDocInfoBuilder fileLevelJsDocBuilder;

  /**
   * Sets the JsDocBuilder for the file-level (root) node of this parse. The
   * parser uses the builder to append any preserve annotations it encounters
   * in JsDoc comments.
   *
   * @param fileLevelJsDocBuilder
   */
  void setFileLevelJsDocBuilder(
      JSDocInfoBuilder fileLevelJsDocBuilder) {
    this.fileLevelJsDocBuilder = fileLevelJsDocBuilder;
  }

  /**
   * Sets the file overview JSDocInfo, in order to warn about multiple uses of
   * the @fileoverview tag in a file.
   */
  void setFileOverviewJSDocInfo(JSDocInfo fileOverviewJSDocInfo) {
    this.fileOverviewJSDocInfo = fileOverviewJSDocInfo;
  }

  private enum State {
    SEARCHING_ANNOTATION,
    SEARCHING_NEWLINE,
    NEXT_IS_ANNOTATION
  }


  JsDocInfoParser(JsDocTokenStream stream,
                  String comment,
                  int commentPosition,
                  StaticSourceFile sourceFile,
                  Config config,
                  ErrorReporter errorReporter) {
    this.stream = stream;

    this.sourceFile = sourceFile;

    this.jsdocBuilder = new JSDocInfoBuilder(config.parseJsDocDocumentation);
    if (comment != null) {
      this.jsdocBuilder.recordOriginalCommentString(comment);
      this.jsdocBuilder.recordOriginalCommentPosition(commentPosition);
    }
    this.annotationNames = config.annotationNames;
    this.suppressionNames = config.suppressionNames;
    this.preserveWhitespace = config.preserveJsDocWhitespace;

    this.errorReporter = errorReporter;
    this.templateNode = this.createTemplateNode();
  }

  private String getSourceName() {
    return sourceFile == null ? null : sourceFile.getName();
  }

  /**
   * Parse a description as a {@code @type}.
   */
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

  private void recordTypeNode(int lineno, int startCharno, Node typeAst,
      boolean matchingLC) {
    if (typeAst != null) {
      int endLineno = stream.getLineno();
      int endCharno = stream.getCharno();
      jsdocBuilder.markTypeNode(
          typeAst, lineno, startCharno, endLineno, endCharno, matchingLC);
    }
  }

  /**
   * Parses a string containing a JsDoc type declaration, returning the
   * type if the parsing succeeded or {@code null} if it failed.
   */
  public static Node parseTypeString(String typeString) {
    JsDocInfoParser parser = getParser(typeString);
    return parser.parseTopLevelTypeExpression(parser.next());
  }

  /**
   * Parses a string containing a JsDoc declaration, returning the entire JSDocInfo
   * if the parsing succeeded or {@code null} if it failed.
   */
  public static JSDocInfo parseJsdoc(String toParse) {
    JsDocInfoParser parser = getParser(toParse);
    parser.parse();
    return parser.retrieveAndResetParsedJSDocInfo();
  }

  private static JsDocInfoParser getParser(String toParse) {
    Config config = new Config(
        new HashSet<String>(),
        new HashSet<String>(),
        false,
        LanguageMode.ECMASCRIPT3);
    JsDocInfoParser parser = new JsDocInfoParser(
        new JsDocTokenStream(toParse),
        toParse,
        0,
        null,
        config,
        NullErrorReporter.forOldRhino());

    return parser;
  }

  /**
   * Parses a {@link JSDocInfo} object. This parsing method reads all tokens
   * returned by the {@link JsDocTokenStream#getJsDocToken()} method until the
   * {@link JsDocToken#EOC} is returned.
   *
   * @return {@code true} if JSDoc information was correctly parsed,
   *     {@code false} otherwise
   */
  boolean parse() {
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
      if (token != JsDocToken.ANNOTATION &&
          token != JsDocToken.EOC) {
        // Mark that there was a description, but don't bother marking
        // what it was.
        jsdocBuilder.recordBlockDescription("");
      }
    }

    return parseHelperLoop(token, new ArrayList<ExtendedTypeInfo>());
  }

  private boolean parseHelperLoop(JsDocToken token,
                                  List<ExtendedTypeInfo> extendedTypes) {
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
                    "msg.bad.fileoverview.visibility.annotation",
                    visibility.toString().toLowerCase());
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
          addParserWarning("msg.unexpected.eof");
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

  private JsDocToken parseAnnotation(JsDocToken token,
      List<ExtendedTypeInfo> extendedTypes) {
    // JSTypes are represented as Rhino AST nodes, and then resolved later.
    JSTypeExpression type;
    int lineno = stream.getLineno();
    int charno = stream.getCharno();

    String annotationName = stream.getString();
    Annotation annotation = annotationNames.get(annotationName);
    if (annotation == null || annotationName.isEmpty()) {
      addParserWarning("msg.bad.jsdoc.tag", annotationName);
    } else {
      // Mark the beginning of the annotation.
      jsdocBuilder.markAnnotation(annotationName, lineno, charno);

      switch (annotation) {
        case NG_INJECT:
          if (jsdocBuilder.isNgInjectRecorded()) {
            addParserWarning("msg.jsdoc.nginject.extra");
          } else {
            jsdocBuilder.recordNgInject(true);
          }
          return eatUntilEOLIfNotAnnotation();

        case JAGGER_INJECT:
          if (jsdocBuilder.isJaggerInjectRecorded()) {
            addParserWarning("msg.jsdoc.jaggerInject.extra");
          } else {
            jsdocBuilder.recordJaggerInject(true);
          }
          return eatUntilEOLIfNotAnnotation();

        case JAGGER_MODULE:
          if (jsdocBuilder.isJaggerModuleRecorded()) {
            addParserWarning("msg.jsdoc.jaggerModule.extra");
          } else {
            jsdocBuilder.recordJaggerModule(true);
          }
          return eatUntilEOLIfNotAnnotation();

        case JAGGER_PROVIDE:
          if (jsdocBuilder.isJaggerProvideRecorded()) {
            addParserWarning("msg.jsdoc.jaggerProvide.extra");
          } else {
            jsdocBuilder.recordJaggerProvide(true);
          }
          return eatUntilEOLIfNotAnnotation();

        case JAGGER_PROVIDE_PROMISE:
          if (jsdocBuilder.isJaggerProvidePromiseRecorded()) {
            addParserWarning("msg.jsdoc.jaggerProvidePromise.extra");
          } else {
            jsdocBuilder.recordJaggerProvidePromise(true);
          }
          return eatUntilEOLIfNotAnnotation();

        case AUTHOR:
          if (jsdocBuilder.shouldParseDocumentation()) {
            ExtractionInfo authorInfo = extractSingleLineBlock();
            String author = authorInfo.string;

            if (author.isEmpty()) {
              addParserWarning("msg.jsdoc.authormissing");
            } else {
              jsdocBuilder.addAuthor(author);
            }
            token = authorInfo.token;
          } else {
            token = eatUntilEOLIfNotAnnotation();
          }
          return token;

        case CONSISTENTIDGENERATOR:
          if (!jsdocBuilder.recordConsistentIdGenerator()) {
            addParserWarning("msg.jsdoc.consistidgen");
          }
          return eatUntilEOLIfNotAnnotation();

        case UNRESTRICTED:
          if (!jsdocBuilder.recordUnrestricted()) {
            addTypeWarning("msg.jsdoc.incompat.type");
          }
          return eatUntilEOLIfNotAnnotation();

        case STRUCT:
          if (!jsdocBuilder.recordStruct()) {
            addTypeWarning("msg.jsdoc.incompat.type");
          }
          return eatUntilEOLIfNotAnnotation();

        case DICT:
          if (!jsdocBuilder.recordDict()) {
            addTypeWarning("msg.jsdoc.incompat.type");
          }
          return eatUntilEOLIfNotAnnotation();

        case CONSTRUCTOR:
          if (!jsdocBuilder.recordConstructor()) {
            if (jsdocBuilder.isInterfaceRecorded()) {
              addTypeWarning("msg.jsdoc.interface.constructor");
            } else {
              addTypeWarning("msg.jsdoc.incompat.type");
            }
          }
          return eatUntilEOLIfNotAnnotation();

        case RECORD:
          if (!jsdocBuilder.recordImplicitMatch()) {
            addTypeWarning("msg.jsdoc.record");
          }
          return eatUntilEOLIfNotAnnotation();

        case DEPRECATED:
          if (!jsdocBuilder.recordDeprecated()) {
            addParserWarning("msg.jsdoc.deprecated");
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
              addTypeWarning("msg.jsdoc.interface.constructor");
            } else {
              addTypeWarning("msg.jsdoc.incompat.type");
            }
          }
          return eatUntilEOLIfNotAnnotation();

        case DESC:
          if (jsdocBuilder.isDescriptionRecorded()) {
            addParserWarning("msg.jsdoc.desc.extra");
            return eatUntilEOLIfNotAnnotation();
          } else {
            ExtractionInfo descriptionInfo =
                extractMultilineTextualBlock(token);

            String description = descriptionInfo.string;

            jsdocBuilder.recordDescription(description);
            token = descriptionInfo.token;
            return token;
          }

        case FILE_OVERVIEW:
          String fileOverview = "";
          if (jsdocBuilder.shouldParseDocumentation() && !lookAheadForAnnotation()) {
            ExtractionInfo fileOverviewInfo = extractMultilineTextualBlock(
                token, getWhitespaceOption(WhitespaceOption.TRIM), false);

            fileOverview = fileOverviewInfo.string;

            token = fileOverviewInfo.token;
          } else {
            token = eatUntilEOLIfNotAnnotation();
          }

          if (!jsdocBuilder.recordFileOverview(fileOverview)) {
            addParserWarning("msg.jsdoc.fileoverview.extra");
          }
          return token;

        case LICENSE:
        case PRESERVE:
          // Always use PRESERVE for @license and @preserve blocks.
          ExtractionInfo preserveInfo = extractMultilineTextualBlock(
              token, WhitespaceOption.PRESERVE, true);

          String preserve = preserveInfo.string;

          if (preserve.length() > 0) {
            if (fileLevelJsDocBuilder != null) {
              fileLevelJsDocBuilder.addLicense(preserve);
            }
          }

          token = preserveInfo.token;
          return token;

        case ENUM:
          token = next();
          lineno = stream.getLineno();
          charno = stream.getCharno();

          type = null;
          if (token != JsDocToken.EOL && token != JsDocToken.EOC) {
            Node typeNode = parseAndRecordTypeNode(token);
            if (typeNode != null && typeNode.getType() == Token.STRING) {
              String typeName = typeNode.getString();
              if (!typeName.equals("number") && !typeName.equals("string")
                  && !typeName.equals("boolean")) {
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
            addTypeWarning("msg.jsdoc.incompat.type", lineno, charno);
          }
          return eatUntilEOLIfNotAnnotation();

        case EXPOSE:
          if (!jsdocBuilder.recordExpose()) {
            addParserWarning("msg.jsdoc.expose");
          }
          return eatUntilEOLIfNotAnnotation();

        case EXTERNS:
          if (!jsdocBuilder.recordExterns()) {
            addParserWarning("msg.jsdoc.externs");
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
            Node typeNode = parseAndRecordTypeNameNode(
                token, lineno, charno, matchingRc);

            lineno = stream.getLineno();
            charno = stream.getCharno();

            typeNode = wrapNode(Token.BANG, typeNode);
            type = createJSTypeExpression(typeNode);

            if (annotation == Annotation.EXTENDS) {
              // record the extended type, check later
              extendedTypes.add(new ExtendedTypeInfo(
                  type, stream.getLineno(), stream.getCharno()));
            } else {
              Preconditions.checkState(
                  annotation == Annotation.IMPLEMENTS);
              if (!jsdocBuilder.recordImplementedInterface(type)) {
                addTypeWarning("msg.jsdoc.implements.duplicate", lineno, charno);
              }
            }
            token = next();
            if (matchingRc) {
              if (token != JsDocToken.RIGHT_CURLY) {
                addTypeWarning("msg.jsdoc.missing.rc");
              } else {
                token = next();
              }
            } else if (token != JsDocToken.EOL &&
                token != JsDocToken.EOF && token != JsDocToken.EOC) {
              addTypeWarning("msg.end.annotation.expected");
            }
          } else {
            addTypeWarning("msg.no.type.name", lineno, charno);
          }
          token = eatUntilEOLIfNotAnnotation(token);
          return token;

        case HIDDEN:
          if (!jsdocBuilder.recordHiddenness()) {
            addParserWarning("msg.jsdoc.hidden");
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
            token = next();
            if (!jsdocBuilder.recordLends(stream.getString())) {
              addTypeWarning("msg.jsdoc.lends.incompatible");
            }
          } else {
            addTypeWarning("msg.jsdoc.lends.missing");
          }

          if (matchingRc && !match(JsDocToken.RIGHT_CURLY)) {
            addTypeWarning("msg.jsdoc.missing.rc");
          }
          return eatUntilEOLIfNotAnnotation();

        case MEANING:
          ExtractionInfo meaningInfo = extractMultilineTextualBlock(token);
          String meaning = meaningInfo.string;
          token = meaningInfo.token;
          if (!jsdocBuilder.recordMeaning(meaning)) {
            addParserWarning("msg.jsdoc.meaning.extra");
          }
          return token;

        case NO_ALIAS:
          if (!jsdocBuilder.recordNoAlias()) {
            addParserWarning("msg.jsdoc.noalias");
          }
          return eatUntilEOLIfNotAnnotation();

        case NO_COMPILE:
          if (!jsdocBuilder.recordNoCompile()) {
            addParserWarning("msg.jsdoc.nocompile");
          }
          return eatUntilEOLIfNotAnnotation();

        case NO_COLLAPSE:
          if (!jsdocBuilder.recordNoCollapse()) {
            addParserWarning("msg.jsdoc.nocollapse");
          }
          return eatUntilEOLIfNotAnnotation();

        case NOT_IMPLEMENTED:
          return eatUntilEOLIfNotAnnotation();

        case INHERIT_DOC:
        case OVERRIDE:
          if (!jsdocBuilder.recordOverride()) {
            addTypeWarning("msg.jsdoc.override");
          }
          return eatUntilEOLIfNotAnnotation();

        case POLYMER_BEHAVIOR:
          if (jsdocBuilder.isPolymerBehaviorRecorded()) {
            addParserWarning("msg.jsdoc.polymerBehavior.extra");
          } else {
            jsdocBuilder.recordPolymerBehavior();
          }
          return eatUntilEOLIfNotAnnotation();

        case THROWS: {
          skipEOLs();
          token = next();
          lineno = stream.getLineno();
          charno = stream.getCharno();
          type = null;

          if (token == JsDocToken.LEFT_CURLY) {
            type = createJSTypeExpression(
                parseAndRecordTypeNode(token));

            if (type == null) {
              // parsing error reported during recursive descent
              // recovering parsing
              return eatUntilEOLIfNotAnnotation();
            }
          }

          // *Update* the token to that after the type annotation.
          token = current();

          // Save the throw type.
          jsdocBuilder.recordThrowType(type);

          boolean isAnnotationNext = lookAheadForAnnotation();

          // Find the throw's description (if applicable).
          if (jsdocBuilder.shouldParseDocumentation() && !isAnnotationNext) {
            ExtractionInfo descriptionInfo =
                extractMultilineTextualBlock(token);

            String description = descriptionInfo.string;

            if (description.length() > 0) {
              jsdocBuilder.recordThrowDescription(type, description);
            }

            token = descriptionInfo.token;
          } else {
            token = eatUntilEOLIfNotAnnotation();
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
            type = createJSTypeExpression(
                parseAndRecordParamTypeNode(token));

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
            addTypeWarning("msg.missing.variable.name", lineno, charno);
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
                reportTypeSyntaxWarning("msg.jsdoc.missing.rb");
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
              addParserWarning("msg.invalid.variable.name", name, lineno, charno);
              name = null;
            } else if (!jsdocBuilder.recordParameter(name, type)) {
              if (jsdocBuilder.hasParameter(name)) {
                addTypeWarning("msg.dup.variable.name", name, lineno, charno);
              } else {
                addTypeWarning("msg.jsdoc.incompat.type", name, lineno, charno);
              }
            }
          }

          if (name == null) {
            token = eatUntilEOLIfNotAnnotation(token);
            return token;
          }

          jsdocBuilder.markName(name, sourceFile, lineno, charno);

          // Find the parameter's description (if applicable).
          if (jsdocBuilder.shouldParseDocumentation()
              && token != JsDocToken.ANNOTATION) {
            ExtractionInfo paramDescriptionInfo =
                extractMultilineTextualBlock(token);

            String paramDescription = paramDescriptionInfo.string;

            if (paramDescription.length() > 0) {
              jsdocBuilder.recordParameterDescription(name,
                  paramDescription);
            }

            token = paramDescriptionInfo.token;
          } else if (token != JsDocToken.EOC && token != JsDocToken.EOF) {
            token = eatUntilEOLIfNotAnnotation();
          }
          return token;

        case PRESERVE_TRY:
          if (!jsdocBuilder.recordPreserveTry()) {
            addParserWarning("msg.jsdoc.preservertry");
          }
          return eatUntilEOLIfNotAnnotation();

        case NO_SIDE_EFFECTS:
          if (!jsdocBuilder.recordNoSideEffects()) {
            addParserWarning("msg.jsdoc.nosideeffects");
          }
          return eatUntilEOLIfNotAnnotation();

        case MODIFIES:
          token = parseModifiesTag(next());
          return token;

        case IMPLICIT_CAST:
          if (!jsdocBuilder.recordImplicitCast()) {
            addTypeWarning("msg.jsdoc.implicitcast");
          }
          return eatUntilEOLIfNotAnnotation();

        case SEE:
          if (jsdocBuilder.shouldParseDocumentation()) {
            ExtractionInfo referenceInfo = extractSingleLineBlock();
            String reference = referenceInfo.string;

            if (reference.isEmpty()) {
              addParserWarning("msg.jsdoc.seemissing");
            } else {
              jsdocBuilder.addReference(reference);
            }

            token = referenceInfo.token;
          } else {
            token = eatUntilEOLIfNotAnnotation();
          }
          return token;

        case STABLEIDGENERATOR:
          if (!jsdocBuilder.recordStableIdGenerator()) {
            addParserWarning("msg.jsdoc.stableidgen");
          }
          return eatUntilEOLIfNotAnnotation();

        case SUPPRESS:
          token = parseSuppressTag(next());
          return token;

        case TEMPLATE: {
          int templateLineno = stream.getLineno();
          int templateCharno = stream.getCharno();
          // Always use TRIM for template TTL expressions.
          ExtractionInfo templateInfo =
              extractMultilineTextualBlock(token, WhitespaceOption.TRIM, false);
          String templateString = templateInfo.string;
          // TTL stands for type transformation language
          // TODO(lpino): This delimiter needs to be further discussed
          String ttlStartDelimiter = ":=";
          String ttlEndDelimiter = "=:";
          String templateNames;
          String typeTransformationExpr = "";
          boolean isTypeTransformation = false;
          boolean validTypeTransformation = true;
          // Detect if there is a type transformation
          if (!templateString.contains(ttlStartDelimiter)) {
            // If there is no type transformation take the first line
            if (templateString.contains("\n")) {
              templateNames =
                  templateString.substring(0, templateString.indexOf('\n'));
            } else {
              templateNames = templateString;
            }
          } else {
            // Split the part with the template type names
            int ttlStartIndex = templateString.indexOf(ttlStartDelimiter);
            templateNames = templateString.substring(0, ttlStartIndex);
            // Check if the type transformation expression ends correctly
            if (!templateString.contains(ttlEndDelimiter)) {
              validTypeTransformation = false;
              addTypeWarning(
                    "msg.jsdoc.typetransformation.missing.delimiter",
                    templateLineno,
                    templateCharno);
              } else {
              isTypeTransformation = true;
              // Split the part of the type transformation
              int ttlEndIndex = templateString.indexOf(ttlEndDelimiter);
              typeTransformationExpr = templateString.substring(
                  ttlStartIndex + ttlStartDelimiter.length(),
                  ttlEndIndex).trim();
            }
          }

          // Obtain the template type names
          List<String> names = Splitter.on(',')
              .trimResults()
              .splitToList(templateNames);

          if (names.size() == 1 && names.get(0).isEmpty()) {
            addTypeWarning("msg.jsdoc.templatemissing", templateLineno, templateCharno);
          } else {
            for (String typeName : names) {
              if (!validTemplateTypeName(typeName)) {
                addTypeWarning(
                    "msg.jsdoc.template.invalid.type.name", templateLineno, templateCharno);
              } else if (!isTypeTransformation) {
                if (!jsdocBuilder.recordTemplateTypeName(typeName)) {
                  addTypeWarning(
                      "msg.jsdoc.template.name.declared.twice", templateLineno, templateCharno);
                }
              }
            }
          }

          if (isTypeTransformation) {
            // A type transformation must be associated to a single type name
            if (names.size() > 1) {
                addTypeWarning(
                    "msg.jsdoc.typetransformation.with.multiple.names",
                    templateLineno, templateCharno);
            }
            if (typeTransformationExpr.isEmpty()) {
              validTypeTransformation = false;
              addTypeWarning(
                  "msg.jsdoc.typetransformation.expression.missing",
                  templateLineno,
                  templateCharno);
            }
            // Build the AST for the type transformation
            if (validTypeTransformation) {
              TypeTransformationParser ttlParser =
                  new TypeTransformationParser(typeTransformationExpr,
                      sourceFile, errorReporter, templateLineno, templateCharno);
              // If the parsing was successful store the type transformation
              if (ttlParser.parseTypeTransformation()
                  && !jsdocBuilder.recordTypeTransformation(
                      names.get(0), ttlParser.getTypeTransformationAst())) {
                addTypeWarning(
                    "msg.jsdoc.template.name.declared.twice", templateLineno, templateCharno);
              }
            }
          }
          token = templateInfo.token;
          return token;
        }

        case IDGENERATOR:
          token = parseIdGeneratorTag(next());
          return token;

        case WIZACTION:
          if (!jsdocBuilder.recordWizaction()) {
            addParserWarning("msg.jsdoc.wizaction");
          }
          return eatUntilEOLIfNotAnnotation();

        case DISPOSES:
          {
          ExtractionInfo templateInfo = extractSingleLineBlock();
          List<String> names = Splitter.on(',')
              .trimResults()
              .splitToList(templateInfo.string);

          if (names.isEmpty() || names.get(0).isEmpty()) {
            addTypeWarning("msg.jsdoc.disposeparameter.missing");
          } else if (!jsdocBuilder.recordDisposesParameter(names)) {
            addTypeWarning("msg.jsdoc.disposeparameter.error");
          }

          token = templateInfo.token;
          return token;
        }

        case VERSION:
          ExtractionInfo versionInfo = extractSingleLineBlock();
          String version = versionInfo.string;

          if (version.isEmpty()) {
            addParserWarning("msg.jsdoc.versionmissing");
          } else {
            if (!jsdocBuilder.recordVersion(version)) {
              addParserWarning("msg.jsdoc.extraversion");
            }
          }

          token = versionInfo.token;
          return token;

        case CONSTANT:
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
              addTypeWarning("msg.jsdoc.incompat.type", lineno, charno);
            }

            boolean isAnnotationNext = lookAheadForAnnotation();

            switch (annotation) {
              case CONSTANT:
                if (!jsdocBuilder.recordConstancy()) {
                  addParserWarning("msg.jsdoc.const");
                }
                break;

              case DEFINE:
                if (!jsdocBuilder.recordDefineType(type)) {
                  addParserWarning("msg.jsdoc.define", lineno, charno);
                }
                if (!isAnnotationNext) {
                  return recordDescription(token);
                }
                break;

              case EXPORT:
                if (!jsdocBuilder.recordExport()) {
                  addParserWarning("msg.jsdoc.export", lineno, charno);
                } else if (!jsdocBuilder.recordVisibility(Visibility.PUBLIC)) {
                  addParserWarning("msg.jsdoc.extra.visibility", lineno, charno);
                }
                if (!isAnnotationNext) {
                  return recordDescription(token);
                }
                break;

              case PRIVATE:
                if (!jsdocBuilder.recordVisibility(Visibility.PRIVATE)) {
                  addParserWarning("msg.jsdoc.extra.visibility", lineno, charno);
                }
                if (!isAnnotationNext) {
                  return recordDescription(token);
                }
                break;

              case PACKAGE:
                if (!jsdocBuilder.recordVisibility(Visibility.PACKAGE)) {
                  addParserWarning("msg.jsdoc.extra.visibility", lineno, charno);
                }
                if (!isAnnotationNext) {
                  return recordDescription(token);
                }
                break;

              case PROTECTED:
                if (!jsdocBuilder.recordVisibility(Visibility.PROTECTED)) {
                  addParserWarning("msg.jsdoc.extra.visibility", lineno, charno);
                }
                if (!isAnnotationNext) {
                  return recordDescription(token);
                }
                break;

              case PUBLIC:
                if (!jsdocBuilder.recordVisibility(Visibility.PUBLIC)) {
                  addParserWarning("msg.jsdoc.extra.visibility", lineno, charno);
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
                  addTypeWarning("msg.jsdoc.incompat.type", lineno, charno);
                  break;
                }

                // TODO(johnlenz): The extractMultilineTextualBlock method
                // and friends look directly at the stream, regardless of
                // last token read, so we don't want to read the first
                // "STRING" out of the stream.

                // Find the return's description (if applicable).
                if (jsdocBuilder.shouldParseDocumentation()
                    && !isAnnotationNext) {
                  ExtractionInfo returnDescriptionInfo =
                      extractMultilineTextualBlock(token);

                  String returnDescription =
                      returnDescriptionInfo.string;

                  if (returnDescription.length() > 0) {
                    jsdocBuilder.recordReturnDescription(
                        returnDescription);
                  }

                  token = returnDescriptionInfo.token;
                } else {
                  token = eatUntilEOLIfNotAnnotation();
                }
                return token;

              case THIS:
                if (!jsdocBuilder.recordThisType(type)) {
                  addTypeWarning("msg.jsdoc.incompat.type", lineno, charno);
                }
                break;

              case TYPEDEF:
                if (!jsdocBuilder.recordTypedef(type)) {
                  addTypeWarning("msg.jsdoc.incompat.type", lineno, charno);
                }
                break;
            }
          }

          return eatUntilEOLIfNotAnnotation();
      }
    }

    return next();
  }

  /**
   * The types in @template annotations must start with a capital letter, and contain
   * only letters, digits, and underscores.
   */
  private static boolean validTemplateTypeName(String name) {
    return !name.isEmpty() && CharMatcher.javaUpperCase().matches(name.charAt(0)) &&
        CharMatcher.javaLetterOrDigit().or(CharMatcher.is('_')).matchesAllOf(name);
  }

  /**
   * Records a marker's description if there is one available and record it in
   * the current marker.
   */
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
          addParserWarning("msg.jsdoc.extends.duplicate", typeInfo.lineno, typeInfo.charno);
        }
      } else {
        if (!jsdocBuilder.recordBaseType(typeInfo.type)) {
          addTypeWarning("msg.jsdoc.incompat.type", typeInfo.lineno, typeInfo.charno);
        }
      }
    }
  }

  /**
   * Parse a {@code @suppress} tag of the form
   * {@code @suppress&#123;warning1|warning2&#125;}.
   *
   * @param token The current token.
   */
  private JsDocToken parseSuppressTag(JsDocToken token) {
    if (token != JsDocToken.LEFT_CURLY) {
      addParserWarning("msg.jsdoc.suppress");
      return token;
    } else {
      Set<String> suppressions = new HashSet<>();
      while (true) {
        if (match(JsDocToken.STRING)) {
          String name = stream.getString();
          if (!suppressionNames.contains(name)) {
            addParserWarning("msg.jsdoc.suppress.unknown", name);
          }

          suppressions.add(stream.getString());
          token = next();
        } else {
          addParserWarning("msg.jsdoc.suppress");
          return token;
        }

        if (match(JsDocToken.PIPE, JsDocToken.COMMA)) {
          token = next();
        } else {
          break;
        }
      }

      if (!match(JsDocToken.RIGHT_CURLY)) {
        addParserWarning("msg.jsdoc.suppress");
      } else {
        token = next();
        if (!jsdocBuilder.recordSuppressions(suppressions)) {
          addParserWarning("msg.jsdoc.suppress.duplicate");
        }
      }
      return eatUntilEOLIfNotAnnotation();
    }
  }

  /**
   * Parse a {@code @modifies} tag of the form
   * {@code @modifies&#123;this|arguments|param&#125;}.
   *
   * @param token The current token.
   */
  private JsDocToken parseModifiesTag(JsDocToken token) {
    if (token == JsDocToken.LEFT_CURLY) {
      Set<String> modifies = new HashSet<>();
      while (true) {
        if (match(JsDocToken.STRING)) {
          String name = stream.getString();
          if (!modifiesAnnotationKeywords.contains(name)
              && !jsdocBuilder.hasParameter(name)) {
            addParserWarning("msg.jsdoc.modifies.unknown", name);
          }

          modifies.add(stream.getString());
          token = next();
        } else {
          addParserWarning("msg.jsdoc.modifies");
          return token;
        }

        if (match(JsDocToken.PIPE)) {
          token = next();
        } else {
          break;
        }
      }

      if (!match(JsDocToken.RIGHT_CURLY)) {
        addParserWarning("msg.jsdoc.modifies");
      } else {
        token = next();
        if (!jsdocBuilder.recordModifies(modifies)) {
          addParserWarning("msg.jsdoc.modifies.duplicate");
        }
      }
    }
    return token;
  }

  /**
   * Parse a {@code @idgenerator} tag of the form
   * {@code @idgenerator} or
   * {@code @idgenerator&#123;consistent&#125;}.
   *
   * @param token The current token.
   */
  private JsDocToken parseIdGeneratorTag(JsDocToken token) {
    String idgenKind = "unique";
    if (token == JsDocToken.LEFT_CURLY) {
      if (match(JsDocToken.STRING)) {
        String name = stream.getString();
        if (!idGeneratorAnnotationKeywords.contains(name)
            && !jsdocBuilder.hasParameter(name)) {
          addParserWarning("msg.jsdoc.idgen.unknown", name);
        }

        idgenKind = name;
        token = next();
      } else {
        addParserWarning("msg.jsdoc.idgen.bad");
        return token;
      }

      if (!match(JsDocToken.RIGHT_CURLY)) {
        addParserWarning("msg.jsdoc.idgen.bad");
      } else {
        token = next();
      }
    }

    switch (idgenKind) {
      case "unique":
        if (!jsdocBuilder.recordIdGenerator()) {
          addParserWarning("msg.jsdoc.idgen.duplicate");
        }
        break;
      case "consistent":
        if (!jsdocBuilder.recordConsistentIdGenerator()) {
          addParserWarning("msg.jsdoc.idgen.duplicate");
        }
        break;
      case "stable":
        if (!jsdocBuilder.recordStableIdGenerator()) {
          addParserWarning("msg.jsdoc.idgen.duplicate");
        }
        break;
      case "mapped":
        if (!jsdocBuilder.recordMappedIdGenerator()) {
          addParserWarning("msg.jsdoc.idgen.duplicate");
        }
        break;
    }

    return token;
  }

  /**
   * Looks for a type expression at the current token and if found,
   * returns it. Note that this method consumes input.
   *
   * @param token The current token.
   * @return The type expression found or null if none.
   */
  Node parseAndRecordTypeNode(JsDocToken token) {
    return parseAndRecordTypeNode(token, stream.getLineno(), stream.getCharno(),
        token == JsDocToken.LEFT_CURLY, false);
  }

  /**
   * Looks for a type expression at the current token and if found,
   * returns it. Note that this method consumes input.
   *
   * @param token The current token.
   * @param lineno The line of the type expression.
   * @param startCharno The starting character position of the type expression.
   * @param matchingLC Whether the type expression starts with a "{".
   * @return The type expression found or null if none.
   */
  private Node parseAndRecordTypeNameNode(JsDocToken token, int lineno,
                                          int startCharno, boolean matchingLC) {
    return parseAndRecordTypeNode(token, lineno, startCharno, matchingLC, true);
  }

  /**
   * Looks for a type expression at the current token and if found,
   * returns it. Note that this method consumes input.
   *
   * Parameter type expressions are special for two reasons:
   * <ol>
   *   <li>They must begin with '{', to distinguish type names from param names.
   *   <li>They may end in '=', to denote optionality.
   * </ol>
   *
   * @param token The current token.
   * @return The type expression found or null if none.
   */
  private Node parseAndRecordParamTypeNode(JsDocToken token) {
    Preconditions.checkArgument(token == JsDocToken.LEFT_CURLY);
    int lineno = stream.getLineno();
    int startCharno = stream.getCharno();

    Node typeNode = parseParamTypeExpressionAnnotation(token);
    recordTypeNode(lineno, startCharno, typeNode, true);
    return typeNode;
  }

  /**
   * Looks for a parameter type expression at the current token and if found,
   * returns it. Note that this method consumes input.
   *
   * @param token The current token.
   * @param lineno The line of the type expression.
   * @param startCharno The starting character position of the type expression.
   * @param matchingLC Whether the type expression starts with a "{".
   * @param onlyParseSimpleNames If true, only simple type names are parsed
   *     (via a call to parseTypeNameAnnotation instead of
   *     parseTypeExpressionAnnotation).
   * @return The type expression found or null if none.
   */
  private Node parseAndRecordTypeNode(JsDocToken token, int lineno,
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
   * Converts a JSDoc token to its string representation.
   */
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

      case ELLIPSIS:
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
   * @param n A node. May be null.
   */
  JSTypeExpression createJSTypeExpression(Node n) {
    return n == null ? null :
        new JSTypeExpression(n, getSourceName());
  }

  /**
   * Tuple for returning both the string extracted and the
   * new token following a call to any of the extract*Block
   * methods.
   */
  private static class ExtractionInfo {
    private final String string;
    private final JsDocToken token;

    public ExtractionInfo(String string, JsDocToken token) {
      this.string = string;
      this.token = token;
    }
  }

  /**
   * Tuple for recording extended types
   */
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
   * Extracts the text found on the current line starting at token. Note that
   * token = token.info; should be called after this method is used to update
   * the token properly in the parser.
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
      jsdocBuilder.markText(line, lineno, charno, lineno,
                            charno + line.length());
    }

    return new ExtractionInfo(line, next());
  }

  private ExtractionInfo extractMultilineTextualBlock(JsDocToken token) {
    return extractMultilineTextualBlock(
        token, getWhitespaceOption(WhitespaceOption.SINGLE_LINE), false);
  }

  private WhitespaceOption getWhitespaceOption(WhitespaceOption defaultValue) {
    return preserveWhitespace ? WhitespaceOption.PRESERVE : defaultValue;
  }

  private enum WhitespaceOption {
    /**
     * Preserves all whitespace and formatting. Needed for licenses and
     * purposely formatted text.
     */
    PRESERVE,

    /** Preserves newlines but trims the output. */
    TRIM,

    /** Removes newlines and turns the output into a single line string. */
    SINGLE_LINE
  }

  /**
   * Extracts the text found on the current line and all subsequent
   * until either an annotation, end of comment or end of file is reached.
   * Note that if this method detects an end of line as the first token, it
   * will quit immediately (indicating that there is no text where it was
   * expected).  Note that token = info.token; should be called after this
   * method is used to update the token properly in the parser.
   *
   * @param token The start token.
   * @param option How to handle whitespace.
   * @param includeAnnotations Whether the extracted text may include
   *     annotations. If set to false, text extraction will stop on the first
   *     encountered annotation token.
   *
   * @return The extraction information.
   */
  private ExtractionInfo extractMultilineTextualBlock(JsDocToken token,
                                                      WhitespaceOption option,
                                                      boolean includeAnnotations) {
    if (token == JsDocToken.EOC || token == JsDocToken.EOL ||
        token == JsDocToken.EOF) {
      return new ExtractionInfo("", token);
    }
    return extractMultilineComment(token, option, true, includeAnnotations);
  }


  /**
   * Extracts the top-level block comment from the JsDoc comment, if any.
   * This method differs from the extractMultilineTextualBlock in that it
   * terminates under different conditions (it doesn't have the same
   * prechecks), it does not first read in the remaining of the current
   * line and its conditions for ignoring the "*" (STAR) are different.
   *
   * @param token The starting token.
   *
   * @return The extraction information.
   */
  private ExtractionInfo extractBlockComment(JsDocToken token) {
    return extractMultilineComment(token, getWhitespaceOption(WhitespaceOption.TRIM), false, false);
  }

  /**
   * Extracts text from the stream until the end of the comment, end of the
   * file, or an annotation token is encountered. If the text is being
   * extracted for a JSDoc marker, the first line in the stream will always be
   * included in the extract text.
   *
   * @param token The starting token.
   * @param option How to handle whitespace.
   * @param isMarker Whether the extracted text is for a JSDoc marker or a
   *     block comment.
   * @param includeAnnotations Whether the extracted text may include
   *     annotations. If set to false, text extraction will stop on the first
   *     encountered annotation token.
   *
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

          if (token == JsDocToken.EOC ||
              token == JsDocToken.EOF ||
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
              jsdocBuilder.markText(multilineText, startLineno, startCharno,
                  endLineno, endCharno);
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
   * Trim characters from only the end of a string.
   * This method will remove all whitespace characters
   * (defined by TokenUtil.isWhitespace(char), in addition to the characters
   * provided, from the end of the provided string.
   *
   * @param s String to be trimmed
   * @return String with whitespace and characters in extraChars removed
   *                   from the end.
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

  /**
   * TypeExpressionAnnotation := TypeExpression |
   *     '{' TopLevelTypeExpression '}'
   */
  private Node parseTypeExpressionAnnotation(JsDocToken token) {
    if (token == JsDocToken.LEFT_CURLY) {
      skipEOLs();
      Node typeNode = parseTopLevelTypeExpression(next());
      if (typeNode != null) {
        skipEOLs();
        if (!match(JsDocToken.RIGHT_CURLY)) {
          reportTypeSyntaxWarning("msg.jsdoc.missing.rc");
        } else {
          next();
        }
      }

      return typeNode;
    } else {
      // TODO(tbreisacher): Add a SuggestedFix for this warning.
      reportTypeSyntaxWarning("msg.jsdoc.missing.braces");
      return parseTypeExpression(token);
    }
  }

  /**
   * ParamTypeExpression :=
   *     OptionalParameterType |
   *     TopLevelTypeExpression |
   *     '...' TopLevelTypeExpression
   *
   * OptionalParameterType :=
   *     TopLevelTypeExpression '='
   *
   */
  private Node parseParamTypeExpression(JsDocToken token) {
    boolean restArg = false;
    if (token == JsDocToken.ELLIPSIS) {
      token = next();
      if (token == JsDocToken.RIGHT_CURLY) {
        restoreLookAhead(token);
        // EMPTY represents the UNKNOWN type in the Type AST.
        return wrapNode(Token.ELLIPSIS, IR.empty());
      }
      restArg = true;
    }

    Node typeNode = parseTopLevelTypeExpression(token);
    if (typeNode != null) {
      skipEOLs();
      if (restArg) {
        typeNode = wrapNode(Token.ELLIPSIS, typeNode);
      } else if (match(JsDocToken.EQUALS)) {
        next();
        skipEOLs();
        typeNode = wrapNode(Token.EQUALS, typeNode);
      }
    }

    return typeNode;
  }


  /**
   * ParamTypeExpressionAnnotation := '{' ParamTypeExpression '}'
   */
  private Node parseParamTypeExpressionAnnotation(JsDocToken token) {
    Preconditions.checkArgument(token == JsDocToken.LEFT_CURLY);

    skipEOLs();

    Node typeNode = parseParamTypeExpression(next());
    if (typeNode != null) {
      if (!match(JsDocToken.RIGHT_CURLY)) {
        reportTypeSyntaxWarning("msg.jsdoc.missing.rc");
      } else {
        next();
      }
    }

    return typeNode;
  }

  /**
   * TypeNameAnnotation := TypeName | '{' TypeName '}'
   */
  private Node parseTypeNameAnnotation(JsDocToken token) {
    if (token == JsDocToken.LEFT_CURLY) {
      skipEOLs();
      Node typeNode = parseTypeName(next());
      if (typeNode != null) {
        skipEOLs();
        if (!match(JsDocToken.RIGHT_CURLY)) {
          reportTypeSyntaxWarning("msg.jsdoc.missing.rc");
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
   * TopLevelTypeExpression := TypeExpression
   *     | TypeUnionList
   *
   * We made this rule up, for the sake of backwards compatibility.
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
   * TypeExpressionList := TopLevelTypeExpression
   *     | TopLevelTypeExpression ',' TypeExpressionList
   */
  private Node parseTypeExpressionList(JsDocToken token) {
    Node typeExpr = parseTopLevelTypeExpression(token);
    if (typeExpr == null) {
      return null;
    }
    Node typeList = IR.block();
    typeList.addChildToBack(typeExpr);
    while (match(JsDocToken.COMMA)) {
      next();
      skipEOLs();
      typeExpr = parseTopLevelTypeExpression(next());
      if (typeExpr == null) {
        return null;
      }
      typeList.addChildToBack(typeExpr);
    }
    return typeList;
  }

  /**
   * TypeExpression := BasicTypeExpression
   *     | '?' BasicTypeExpression
   *     | '!' BasicTypeExpression
   *     | BasicTypeExpression '?'
   *     | BasicTypeExpression '!'
   *     | '?'
   */
  private Node parseTypeExpression(JsDocToken token) {
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
          || token == JsDocToken.EOF) {
        restoreLookAhead(token);
        return newNode(Token.QMARK);
      }

      return wrapNode(Token.QMARK, parseBasicTypeExpression(token));
    } else if (token == JsDocToken.BANG) {
      return wrapNode(Token.BANG, parseBasicTypeExpression(next()));
    } else {
      Node basicTypeExpr = parseBasicTypeExpression(token);
      if (basicTypeExpr != null) {
        if (match(JsDocToken.QMARK)) {
          next();
          return wrapNode(Token.QMARK, basicTypeExpr);
        } else if (match(JsDocToken.BANG)) {
          next();
          return wrapNode(Token.BANG, basicTypeExpr);
        }
      }

      return basicTypeExpr;
    }
  }

  /**
   * ContextTypeExpression := BasicTypeExpression | '?'
   * For expressions on the right hand side of a this: or new:
   */
  private Node parseContextTypeExpression(JsDocToken token) {
    if (token == JsDocToken.QMARK) {
      return newNode(Token.QMARK);
    } else {
      return parseBasicTypeExpression(token);
    }
  }

  /**
   * BasicTypeExpression := '*' | 'null' | 'undefined' | TypeName
   *     | FunctionType | UnionType | RecordType
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
        default:
          return parseTypeName(token);
      }
    }

    restoreLookAhead(token);
    return reportGenericTypeSyntaxWarning();
  }

  /**
   * TypeName := NameExpression | NameExpression TypeApplication
   * TypeApplication := '.<' TypeExpressionList '>'
   */
  private Node parseTypeName(JsDocToken token) {
    if (token != JsDocToken.STRING) {
      return reportGenericTypeSyntaxWarning();
    }

    String typeName = stream.getString();
    int lineno = stream.getLineno();
    int charno = stream.getCharno();
    while (match(JsDocToken.EOL) &&
        typeName.charAt(typeName.length() - 1) == '.') {
      skipEOLs();
      if (match(JsDocToken.STRING)) {
        next();
        typeName += stream.getString();
      }
    }

    Node typeNameNode = newStringNode(typeName, lineno, charno);

    if (match(JsDocToken.LEFT_ANGLE)) {
      next();
      skipEOLs();
      Node memberType = parseTypeExpressionList(next());
      if (memberType != null) {
        typeNameNode.addChildToFront(memberType);

        skipEOLs();
        if (!match(JsDocToken.RIGHT_ANGLE)) {
          return reportTypeSyntaxWarning("msg.jsdoc.missing.gt");
        }

        next();
      }
    }
    return typeNameNode;
  }

  /**
   * FunctionType := 'function' FunctionSignatureType
   * FunctionSignatureType :=
   *    TypeParameters '(' 'this' ':' TypeName, ParametersType ')' ResultType
   *
   * <p>The Node that is produced has type Token.FUNCTION but does not look like a typical
   * function node. If there is a 'this:' or 'new:' type, that type is added as a child.
   * Then, if there are parameters, a PARAM_LIST node is added as a child. Finally, if
   * there is a return type, it is added as a child. This means that the parameters
   * could be the first or second child, and the return type could be
   * the first, second, or third child.
   */
  private Node parseFunctionType(JsDocToken token) {
    // NOTE(nicksantos): We're not implementing generics at the moment, so
    // just throw out TypeParameters.
    if (token != JsDocToken.LEFT_PAREN) {
      restoreLookAhead(token);
      return reportTypeSyntaxWarning("msg.jsdoc.missing.lp");
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
            Node contextType = wrapNode(
                isThis ? Token.THIS : Token.NEW,
                parseContextTypeExpression(next()));
            if (contextType == null) {
              return null;
            }

            functionType.addChildToFront(contextType);
          } else {
            return reportTypeSyntaxWarning("msg.jsdoc.missing.colon");
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
      return reportTypeSyntaxWarning("msg.jsdoc.missing.rp");
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

        if (token == JsDocToken.ELLIPSIS) {
          // In the latest ES4 proposal, there are no type constraints allowed
          // on variable arguments. We support the old syntax for backwards
          // compatibility, but we should gradually tear it out.
          skipEOLs();
          if (match(JsDocToken.RIGHT_PAREN)) {
            paramType = newNode(Token.ELLIPSIS);
          } else {
            skipEOLs();
            paramType = wrapNode(Token.ELLIPSIS, parseTypeExpression(next()));
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
      return reportTypeSyntaxWarning("msg.jsdoc.function.varargs");
    }

    // The right paren will be checked by parseFunctionType

    return paramsType;
  }

  /**
   * ResultType := <empty> | ':' void | ':' TypeExpression
   */
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
   * UnionType := '(' TypeUnionList ')'
   * TypeUnionList := TypeExpression | TypeExpression '|' TypeUnionList
   *
   * We've removed the empty union type.
   */
  private Node parseUnionType(JsDocToken token) {
    return parseUnionTypeWithAlternate(token, null);
  }

  /**
   * Create a new union type, with an alternate that has already been
   * parsed. The alternate may be null.
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
        Preconditions.checkState(token == JsDocToken.PIPE);

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
        return reportTypeSyntaxWarning("msg.jsdoc.missing.rp");
      }
      next();
    }
    if (union.getChildCount() == 1) {
      Node firstChild = union.getFirstChild();
      union.removeChild(firstChild);
      return firstChild;
    }
    return union;
  }

  /**
   * RecordType := '{' FieldTypeList '}'
   */
  private Node parseRecordType(JsDocToken token) {
    Node recordType = newNode(Token.LC);
    Node fieldTypeList = parseFieldTypeList(token);

    if (fieldTypeList == null) {
      return reportGenericTypeSyntaxWarning();
    }

    skipEOLs();
    if (!match(JsDocToken.RIGHT_CURLY)) {
      return reportTypeSyntaxWarning("msg.jsdoc.missing.rc");
    }

    next();

    recordType.addChildToBack(fieldTypeList);
    return recordType;
  }

  /**
   * FieldTypeList := FieldType | FieldType ',' FieldTypeList
   */
  private Node parseFieldTypeList(JsDocToken token) {
    Node fieldTypeList = newNode(Token.LB);

    Set<String> names = new HashSet<>();

    do {
      Node fieldType = parseFieldType(token);

      if (fieldType == null) {
        return null;
      }

      String name = fieldType.isStringKey() ? fieldType.getString()
          : fieldType.getFirstChild().getString();
      if (names.add(name)) {
        fieldTypeList.addChildToBack(fieldType);
      } else {
        addTypeWarning("msg.jsdoc.type.record.duplicate", name);
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

  /**
   * FieldType := FieldName | FieldName ':' TypeExpression
   */
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

  /**
   * FieldName := NameExpression | StringLiteral | NumberLiteral |
   * ReservedIdentifier
   */
  private Node parseFieldName(JsDocToken token) {
    switch (token) {
      case STRING:
        String s = stream.getString();
        Node n = Node.newString(
            Token.STRING_KEY, s, stream.getLineno(), stream.getCharno())
            .clonePropsFrom(templateNode);
        n.setLength(s.length());
        return n;

      default:
        return null;
    }
  }

  private Node wrapNode(int type, Node n) {
    return n == null ? null :
        new Node(type, n, n.getLineno(),
            n.getCharno()).clonePropsFrom(templateNode);
  }

  private Node newNode(int type) {
    return new Node(type, stream.getLineno(),
        stream.getCharno()).clonePropsFrom(templateNode);
  }

  private Node newStringNode(String s) {
    return newStringNode(s, stream.getLineno(), stream.getCharno());
  }

  private Node newStringNode(String s, int lineno, int charno) {
    Node n = Node.newString(s, lineno, charno).clonePropsFrom(templateNode);
    n.setLength(s.length());
    return n;
  }

  // This is similar to IRFactory.createTemplateNode to share common props
  // e.g., source-name, between all nodes.
  private Node createTemplateNode() {
    // The Node type choice is arbitrary.
    Node templateNode = IR.script();
    templateNode.setStaticSourceFile(
      this.sourceFile);
    return templateNode;
  }

  private Node reportTypeSyntaxWarning(String warning) {
    addTypeWarning(warning, stream.getLineno(), stream.getCharno());
    return null;
  }

  private Node reportGenericTypeSyntaxWarning() {
    return reportTypeSyntaxWarning("msg.jsdoc.type.syntax");
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
   * Eats tokens until {@link JsDocToken#EOL} included, and switches back the
   * state to {@link State#SEARCHING_ANNOTATION}.
   */
  private JsDocToken eatTokensUntilEOL() {
    return eatTokensUntilEOL(next());
  }

  /**
   * Eats tokens until {@link JsDocToken#EOL} included, and switches back the
   * state to {@link State#SEARCHING_ANNOTATION}.
   */
  private JsDocToken eatTokensUntilEOL(JsDocToken token) {
    do {
      if (token == JsDocToken.EOL || token == JsDocToken.EOC ||
          token == JsDocToken.EOF) {
        state = State.SEARCHING_ANNOTATION;
        return token;
      }
      token = next();
    } while (true);
  }

  /**
   * Specific value indicating that the {@link #unreadToken} contains no token.
   */
  private static final JsDocToken NO_UNREAD_TOKEN = null;

  /**
   * One token buffer.
   */
  private JsDocToken unreadToken = NO_UNREAD_TOKEN;

  /** Restores the lookahead token to the token stream */
  private void restoreLookAhead(JsDocToken token) {
    unreadToken = token;
  }

  /**
   * Tests whether the next symbol of the token stream matches the specific
   * token.
   */
  private boolean match(JsDocToken token) {
    unreadToken = next();
    return unreadToken == token;
  }

  /**
   * Tests that the next symbol of the token stream matches one of the specified
   * tokens.
   */
  private boolean match(JsDocToken token1, JsDocToken token2) {
    unreadToken = next();
    return unreadToken == token1 || unreadToken == token2;
  }

  /**
   * Gets the next token of the token stream or the buffered token if a matching
   * was previously made.
   */
  private JsDocToken next() {
    if (unreadToken == NO_UNREAD_TOKEN) {
      return stream.getJsDocToken();
    } else {
      return current();
    }
  }

  /**
   * Gets the current token, invalidating it in the process.
   */
  private JsDocToken current() {
    JsDocToken t = unreadToken;
    unreadToken = NO_UNREAD_TOKEN;
    return t;
  }

  /**
   * Skips all EOLs and all empty lines in the JSDoc. Call this method if you
   * want the JSDoc entry to span multiple lines.
   */
  private void skipEOLs() {
    while (match(JsDocToken.EOL)) {
      next();
      if (match(JsDocToken.STAR)) {
        next();
      }
    }
  }

  /**
   * Returns the remainder of the line.
   */
  private String getRemainingJSDocLine() {
    String result = stream.getRemainingJSDocLine();
    unreadToken = NO_UNREAD_TOKEN;
    return result;
  }

  /**
   * Determines whether the parser has been populated with docinfo with a
   * fileoverview tag.
   */
  private boolean hasParsedFileOverviewDocInfo() {
    return jsdocBuilder.isPopulatedWithFileOverview();
  }

  JSDocInfo retrieveAndResetParsedJSDocInfo() {
    return jsdocBuilder.build();
  }

  /**
   * Gets the fileoverview JSDocInfo, if any.
   */
  JSDocInfo getFileOverviewJSDocInfo() {
    return fileOverviewJSDocInfo;
  }

  /**
   * Look ahead for a type annotation by advancing the character stream.
   * Does not modify the token stream.
   * This is kind of a hack, and is only necessary because we use the token
   * stream to parse types, but need the underlying character stream to get
   * JsDoc descriptions.
   * @return Whether we found a type annotation.
   */
  private boolean lookAheadForType() {
    return lookAheadFor('{');
  }

  private boolean lookAheadForAnnotation() {
    return lookAheadFor('@');
  }

  /**
   * Look ahead by advancing the character stream.
   * Does not modify the token stream.
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
