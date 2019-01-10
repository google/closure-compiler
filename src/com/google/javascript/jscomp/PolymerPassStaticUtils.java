/*
 * Copyright 2016 The Closure Compiler Authors.
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
package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_MISPLACED_PROPERTY_JSDOC;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.PolymerPass.MemberDefinition;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Simple static utility functions shared between the {@link PolymerPass} and its helper classes.
 */
final class PolymerPassStaticUtils {
  private static final String VIRTUAL_FILE = "<PolymerPassStaticUtils.java>";

  /** @return Whether the call represents a call to the Polymer function. */
  @VisibleForTesting
  public static boolean isPolymerCall(Node call) {
    if (call == null || !call.isCall()) {
      return false;
    }
    Node name = call.getFirstChild();
    // When imported from an ES module, we'll have a GETPROP like
    // `module$polymer$polymer_legacy.Polymer`.
    return name.matchesQualifiedName("Polymer")
        || "Polymer".equals(name.getOriginalQualifiedName())
        || (name.isGetProp() && name.getLastChild().getString().equals("Polymer"));
  }

  /** @return Whether the class extends PolymerElement. */
  @VisibleForTesting
  public static boolean isPolymerClass(Node cls) {
    if (cls == null || !cls.isClass()) {
      return false;
    }
    // A class with the @polymer annotation is always considered a Polymer element.
    JSDocInfo info = NodeUtil.getBestJSDocInfo(cls);
    if (info != null && info.isPolymer()) {
      return true;
    }
    Node heritage = cls.getSecondChild();
    // In Polymer 3, the base class was renamed from `Polymer.Element` to `PolymerElement`. When
    // imported from an ES module, we'll have a GETPROP like
    // `module$polymer$polymer_element.PolymerElement`.
    return !heritage.isEmpty()
        && (heritage.matchesQualifiedName("Polymer.Element")
            || heritage.matchesQualifiedName("PolymerElement")
            || "PolymerElement".equals(heritage.getOriginalQualifiedName())
            || (heritage.isGetProp()
                && heritage.getLastChild().getString().equals("PolymerElement")));
  }

  /** Switches all "this.$.foo" to "this.$['foo']". */
  static void switchDollarSignPropsToBrackets(Node def, final AbstractCompiler compiler) {
    checkState(def.isObjectLit() || def.isClassMembers());
    for (Node keyNode : def.children()) {
      Node value = keyNode.getFirstChild();
      if (value != null && value.isFunction()) {
        NodeUtil.visitPostOrder(
            value.getLastChild(),
            new NodeUtil.Visitor() {
              @Override
              public void visit(Node n) {
                if (n.isString()
                    && n.getString().equals("$")
                    && n.getParent().isGetProp()
                    && n.getGrandparent().isGetProp()) {
                  Node dollarChildProp = n.getGrandparent();
                  dollarChildProp.setToken(Token.GETELEM);
                  compiler.reportChangeToEnclosingScope(dollarChildProp);
                }
              }
            });
      }
    }
  }

  /**
   * Makes sure that the keys for listeners and hostAttributes blocks are quoted to avoid renaming.
   */
  static void quoteListenerAndHostAttributeKeys(Node objLit, AbstractCompiler compiler) {
    checkState(objLit.isObjectLit());
    for (Node keyNode : objLit.children()) {
      if (keyNode.isComputedProp()) {
        continue;
      }
      if (!keyNode.getString().equals("listeners")
          && !keyNode.getString().equals("hostAttributes")) {
        continue;
      }
      for (Node keyToQuote : keyNode.getFirstChild().children()) {
        if (!keyToQuote.isQuotedString()) {
          keyToQuote.setQuotedString();
          compiler.reportChangeToEnclosingScope(keyToQuote);
        }
      }
    }
  }

  /**
   * Finds a list of {@link MemberDefinition}s for the {@code properties} block of the given
   * descriptor Object literal.
   *
   * @param descriptor The Polymer properties configuration object literal node.
   * @param constructor If we are finding the properties of an ES6 class, the constructor function
   *     node for that class, otherwise null. We'll prefer JSDoc from property initialization
   *     statements in this constructor over the JSDoc within the Polymer properties configuration
   *     object.
   */
  static ImmutableList<MemberDefinition> extractProperties(
      Node descriptor,
      PolymerClassDefinition.DefinitionType defType,
      AbstractCompiler compiler,
      @Nullable Node constructor) {
    Node properties = descriptor;
    if (defType == PolymerClassDefinition.DefinitionType.ObjectLiteral) {
      properties = NodeUtil.getFirstPropMatchingKey(descriptor, "properties");
    }
    if (properties == null) {
      return ImmutableList.of();
    }

    Map<String, JSDocInfo> constructorPropertyJsDoc = new HashMap<>();
    if (constructor != null) {
      collectConstructorPropertyJsDoc(constructor, constructorPropertyJsDoc);
    }

    ImmutableList.Builder<MemberDefinition> members = ImmutableList.builder();
    for (Node keyNode : properties.children()) {
      // The JSDoc for a Polymer property in the constructor should win over the JSDoc in the
      // Polymer properties configuration object.
      JSDocInfo constructorJsDoc = constructorPropertyJsDoc.get(keyNode.getString());
      JSDocInfo propertiesConfigJsDoc = NodeUtil.getBestJSDocInfo(keyNode);
      JSDocInfo bestJsDoc;
      if (constructorJsDoc != null) {
        bestJsDoc = constructorJsDoc;
        if (propertiesConfigJsDoc != null) {
          // Warn if the user put JSDoc in both places.
          compiler.report(JSError.make(keyNode, POLYMER_MISPLACED_PROPERTY_JSDOC));
        }
      } else {
        bestJsDoc = propertiesConfigJsDoc;
      }
      members.add(new MemberDefinition(bestJsDoc, keyNode, keyNode.getFirstChild()));
    }
    return members.build();
  }

  /**
   * Find the properties that are initialized in the given constructor, and return a map from each
   * property name to its JSDoc.
   *
   * @param node The constructor function node to traverse.
   * @param map The map from property name to JSDoc.
   */
  private static void collectConstructorPropertyJsDoc(Node node, Map<String, JSDocInfo> map) {
    checkNotNull(node);
    for (Node child : node.children()) {
      if (child.isGetProp()
          && child.getFirstChild().isThis()
          && child.getSecondChild().isString()) {
        // We found a "this.foo" expression. Map "foo" to its JSDoc.
        map.put(child.getSecondChild().getString(), NodeUtil.getBestJSDocInfo(child));
      } else {
        // Recurse through every other kind of node, because properties are not necessarily declared
        // at the top level of the constructor body; e.g. they could be declared as part of an
        // assignment, or within an if statement. We're being overly loose here, e.g. we shouldn't
        // traverse into a nested function where "this" doesn't refer to our prototype, but
        // hopefully this is good enough for our purposes.
        collectConstructorPropertyJsDoc(child, map);
      }
    }
  }

  /**
   * Gets the JSTypeExpression for a given property using its "type" key.
   * @see https://github.com/Polymer/polymer/blob/0.8-preview/PRIMER.md#configuring-properties
   */
  static JSTypeExpression getTypeFromProperty(
      MemberDefinition property, AbstractCompiler compiler) {
    if (property.info != null && property.info.hasType()) {
      return property.info.getType();
    }

    String typeString;
    if (property.value.isObjectLit()) {
      Node typeValue = NodeUtil.getFirstPropMatchingKey(property.value, "type");
      if (typeValue == null || !typeValue.isName()) {
        compiler.report(JSError.make(property.name, PolymerPassErrors.POLYMER_INVALID_PROPERTY));
        return null;
      }
      typeString = typeValue.getString();
    } else if (property.value.isName()) {
      typeString = property.value.getString();
    } else {
      typeString = "";
    }

    Node typeNode;
    switch (typeString) {
      case "Boolean":
      case "String":
      case "Number":
        typeNode = IR.string(typeString.toLowerCase());
        break;
      case "Array":
      case "Function":
      case "Object":
      case "Date":
        typeNode = new Node(Token.BANG, IR.string(typeString));
        break;
      default:
        compiler.report(JSError.make(property.name, PolymerPassErrors.POLYMER_INVALID_PROPERTY));
        return null;
    }

    return new JSTypeExpression(typeNode, VIRTUAL_FILE);
  }

  /**
   * @return The PolymerElement type string for a class definition.
   */
  public static String getPolymerElementType(final PolymerClassDefinition cls) {
    String nativeElementName = cls.nativeBaseElement == null ? ""
        : CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, cls.nativeBaseElement);
    return SimpleFormat.format("Polymer%sElement", nativeElementName);
  }
}
