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
import com.google.common.base.Ascii;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.PolymerPass.MemberDefinition;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.Token;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Simple static utility functions shared between the {@link PolymerPass} and its helper classes.
 */
final class PolymerPassStaticUtils {
  private static final String VIRTUAL_FILE = "<PolymerPassStaticUtils.java>";
  private static final QualifiedName POLYMER_DOT_ELEMENT = QualifiedName.of("Polymer.Element");

  /** Returns whether the call represents a call to the Polymer function. */
  @VisibleForTesting
  public static boolean isPolymerCall(Node call) {
    if (call == null || !call.isCall()) {
      return false;
    }
    Node name = call.getFirstChild();
    // When imported from an ES module, the rewriting should set the original name.
    // When imported from an goog module (TS), we'll have a GETPROP like
    // `module$polymer$polymer_legacy.Polymer`.
    return name.matchesName("Polymer")
        || "Polymer".equals(name.getOriginalQualifiedName())
        || (name.isGetProp() && name.getString().equals("Polymer"));
  }

  /** Returns whether the class extends PolymerElement. */
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
    // imported from an ES module, the rewriting should set the original name to `PolymerElement`.
    // When imported from an goog module (TS), we'll have a GETPROP like
    // `module$polymer$polymer_element.PolymerElement`.
    // YT Polymer components may also extend the `PolymerElementWithoutHtml` or
    // `PolymerLiteControllerBase` base classes.
    return !heritage.isEmpty()
        && (POLYMER_DOT_ELEMENT.matches(heritage)
            || matches(heritage, "PolymerElement")
            || matches(heritage, "PolymerElementWithoutHtml")
            || matches(heritage, "PolymerLiteControllerBase"));
  }

  private static boolean matches(Node n, String name) {
    return n.matchesName(name)
        || name.equals(n.getOriginalQualifiedName())
        || (n.isGetProp() && n.getString().equals(name));
  }

  /**
   * The "$" member in a Polymer element is a map of statically created nodes in its local DOM. This
   * method is used to rewrite usage of this map from "this.$.foo" to "this.$['foo']" to avoid
   * JSC_POSSIBLE_INEXISTENT_PROPERTY errors. Excludes function calls like "bar.$.foo()" since some
   * libraries place methods under a "$" member.
   */
  static void switchDollarSignPropsToBrackets(Node def, final AbstractCompiler compiler) {
    checkState(def.isObjectLit() || def.isClassMembers());
    for (Node keyNode = def.getFirstChild(); keyNode != null; keyNode = keyNode.getNext()) {
      Node value = keyNode.getFirstChild();
      if (value != null && value.isFunction()) {
        NodeUtil.visitPostOrder(
            value.getLastChild(),
            new NodeUtil.Visitor() {
              @Override
              public void visit(Node n) {
                if (!n.isGetProp()) {
                  return;
                }

                Node dollarSign = n.getFirstChild();
                if (!dollarSign.isGetProp() || !dollarSign.getString().equals("$")) {
                  return;
                }

                // Some libraries like Mojo JS Bindings and jQuery place methods in a "$" member
                // e.g. "foo.$.closePipe()". Avoid converting to brackets for these cases.
                if (n.getParent().isCall() && n.getParent().hasOneChild()) {
                  return;
                }

                Node key = IR.string(n.getString()).clonePropsFrom(n).srcref(n);
                Node getelem = IR.getelem(dollarSign.detach(), key).clonePropsFrom(n).srcref(n);
                n.replaceWith(getelem);
                compiler.reportChangeToEnclosingScope(getelem);
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
    for (Node keyNode = objLit.getFirstChild(); keyNode != null; keyNode = keyNode.getNext()) {
      if (!keyNode.isStringKey()) {
        // We should only quote string keys. If this is not a string key, then we should skip it.
        continue;
      }
      if (!keyNode.getString().equals("listeners")
          && !keyNode.getString().equals("hostAttributes")) {
        continue;
      }
      for (Node keyToQuote = keyNode.getFirstFirstChild();
          keyToQuote != null;
          keyToQuote = keyToQuote.getNext()) {
        if (!keyToQuote.isQuotedStringKey()) {
          keyToQuote.setQuotedStringKey();
          compiler.reportChangeToEnclosingScope(keyToQuote);
        }
      }
    }
  }

  /**
   * Ensures that methods registered in the static {@code observers} block, as well as methods
   * referenced in the {@code observer} and {@code computed} fields on a Polymer property block
   * declaration are exported and marked {@code @noCollapse}.
   */
  static void protectObserverAndPropertyFunctionKeys(Node behaviorObjLit) {
    checkState(behaviorObjLit.isObjectLit());

    Set<String> methodsToProtect = new LinkedHashSet<>();
    methodsToProtect.addAll(addObserverMethodsToProtect(behaviorObjLit));
    methodsToProtect.addAll(addPropertyMethodsToProtect(behaviorObjLit));

    for (Node keyNode = behaviorObjLit.getFirstChild();
        keyNode != null;
        keyNode = keyNode.getNext()) {
      // Note: This also is true for object literal methods.
      if (!NodeUtil.isObjectLitKey(keyNode)) {
        continue;
      }

      String keyName = NodeUtil.getObjectOrClassLitKeyName(keyNode);
      if (!methodsToProtect.contains(keyName)) {
        continue;
      }

      addExportAndNoCollapseToKey(keyNode);
    }
  }

  /** Finds methods referenced in the 'observers' property and returns them as a Set. */
  private static Set<String> addObserverMethodsToProtect(Node behaviorObjLit) {
    Set<String> methodsToProtect = new LinkedHashSet<>();
    Node observers = NodeUtil.getFirstPropMatchingKey(behaviorObjLit, "observers");
    if (observers != null && observers.isArrayLit()) {
      for (Node child = observers.getFirstChild(); child != null; child = child.getNext()) {
        if (!child.isStringLit()) {
          continue;
        }

        String name = extractMethodName(child.getString());
        if (name != null) {
          methodsToProtect.add(name);
        }
      }
    }
    return methodsToProtect;
  }

  /** Finds methods referenced in the 'properties' property and returns them as a Set. */
  private static Set<String> addPropertyMethodsToProtect(Node behaviorObjLit) {
    Set<String> methodsToProtect = new LinkedHashSet<>();
    Node properties = NodeUtil.getFirstPropMatchingKey(behaviorObjLit, "properties");
    if (properties != null && properties.isObjectLit()) {
      for (Node prop = properties.getFirstChild(); prop != null; prop = prop.getNext()) {
        if (!prop.getLastChild().isObjectLit()) {
          continue;
        }

        Node observer = NodeUtil.getFirstPropMatchingKey(prop.getLastChild(), "observer");
        if (observer != null && observer.isStringLit()) {
          methodsToProtect.add(observer.getString());
        }

        Node computed = NodeUtil.getFirstPropMatchingKey(prop.getLastChild(), "computed");
        if (computed != null && computed.isStringLit()) {
          String name = extractMethodName(computed.getString());
          if (name != null) {
            methodsToProtect.add(name);
          }
        }
      }
    }
    return methodsToProtect;
  }

  private static @Nullable String extractMethodName(String signature) {
    int openParenIndex = signature.indexOf('(');
    return openParenIndex > 0 ? signature.substring(0, openParenIndex).trim() : null;
  }

  /**
   * Ensures the given key node (or methodFunction) is exported and adds `@nocollapse` to its JSDoc.
   *
   * @param keyNode The key node from an object literal.
   */
  private static void addExportAndNoCollapseToKey(Node keyNode) {
    JSDocInfo.Builder newDocs = JSDocInfo.Builder.maybeCopyFrom(keyNode.getJSDocInfo());
    newDocs.recordNoCollapse();
    newDocs.recordExport();
    keyNode.setJSDocInfo(newDocs.build());
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

    Map<String, JSDocInfo> constructorPropertyJsDoc = new LinkedHashMap<>();
    if (constructor != null) {
      collectConstructorPropertyJsDoc(constructor, constructorPropertyJsDoc);
    }
    Node enclosingModule = NodeUtil.getEnclosingModuleIfPresent(descriptor);

    ImmutableList.Builder<MemberDefinition> members = ImmutableList.builder();
    for (Node keyNode = properties.getFirstChild(); keyNode != null; keyNode = keyNode.getNext()) {
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
      members.add(
          new MemberDefinition(bestJsDoc, keyNode, keyNode.getFirstChild(), enclosingModule));
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
    for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
      if (child.isGetProp() && child.getFirstChild().isThis()) {
        // We found a "this.foo" expression. Map "foo" to its JSDoc.
        map.put(child.getString(), NodeUtil.getBestJSDocInfo(child));
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
   *
   * @see https://github.com/Polymer/polymer/blob/0.8-preview/PRIMER.md#configuring-properties
   */
  static @Nullable JSTypeExpression getTypeFromProperty(
      MemberDefinition property, AbstractCompiler compiler) {
    if (property.info != null && property.info.hasType()) {
      return property.info.getType().copy();
    }

    Node typeValue;
    if (property.value.isObjectLit()) {
      typeValue = NodeUtil.getFirstPropMatchingKey(property.value, "type");
      if (typeValue == null || !typeValue.isName()) {
        compiler.report(JSError.make(property.name, PolymerPassErrors.POLYMER_INVALID_PROPERTY));
        return null;
      }
    } else if (property.value.isName()) {
      typeValue = property.value;
    } else {
      typeValue = null;
    }

    if (typeValue == null) {
      compiler.report(JSError.make(property.value, PolymerPassErrors.POLYMER_INVALID_PROPERTY));
      return null;
    }

    String typeString = typeValue.getString();
    Node typeNode;
    switch (typeString) {
      case "Boolean", "String", "Number" -> typeNode = IR.string(Ascii.toLowerCase(typeString));
      case "Array", "Function", "Object", "Date" ->
          typeNode = new Node(Token.BANG, IR.string(typeString));
      default -> {
        compiler.report(JSError.make(property.value, PolymerPassErrors.POLYMER_INVALID_PROPERTY));
        typeNode = new Node(Token.QMARK);
      }
    }

    typeNode.srcrefTree(typeValue);
    return new JSTypeExpression(typeNode, VIRTUAL_FILE);
  }

  /**
   * @return The PolymerElement type string for a class definition.
   */
  public static String getPolymerElementType(final PolymerClassDefinition cls) {
    String nativeElementName =
        cls.nativeBaseElement == null
            ? ""
            : CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, cls.nativeBaseElement);
    return String.format("Polymer%sElement", nativeElementName);
  }

  private PolymerPassStaticUtils() {}
}
