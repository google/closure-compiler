/*
 * Copyright 2014 The Closure Compiler Authors.
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

package com.google.javascript.refactoring;

import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;

/**
 * Class that contains common Matchers that are useful to everyone.
 *
 * @author mknichel@google.com (Mark Knichel)
 */
public final class Matchers {
  // TODO(mknichel): Make sure all this code works with goog.scope.

  /**
   * Returns a Matcher that matches every node.
   */
  public static Matcher anything() {
    return new Matcher() {
      @Override public boolean matches(Node node, NodeMetadata metadata) {
        return true;
      }
    };
  }

  /**
   * Returns a Matcher that returns true only if all of the provided
   * matchers match.
   */
  public static Matcher allOf(final Matcher... matchers) {
    return new Matcher() {
      @Override public boolean matches(Node node, NodeMetadata metadata) {
        for (Matcher m : matchers) {
          if (!m.matches(node, metadata)) {
            return false;
          }
        }
        return true;
      }
    };
  }

  /**
   * Returns a Matcher that returns true if any of the provided matchers match.
   */
  public static Matcher anyOf(final Matcher... matchers) {
    return new Matcher() {
      @Override public boolean matches(Node node, NodeMetadata metadata) {
        for (Matcher m : matchers) {
          if (m.matches(node, metadata)) {
            return true;
          }
        }
        return false;
      }
    };
  }

  /**
   * Returns a Matcher that matches the opposite of the provided matcher.
   */
  public static Matcher not(final Matcher matcher) {
    return new Matcher() {
      @Override public boolean matches(Node node, NodeMetadata metadata) {
        return !matcher.matches(node, metadata);
      }
    };
  }

  /**
   * Returns a matcher that matches any constructor definitions.
   */
  public static Matcher constructor() {
    return constructor(null);
  }

  /**
   * Returns a matcher that matches constructor definitions of the specified
   * name.
   * @param name The name of the class constructor to match.
   */
  public static Matcher constructor(final String name) {
    return new Matcher() {
      @Override public boolean matches(Node node, NodeMetadata metadata) {
        JSDocInfo info = node.getJSDocInfo();
        if (info != null && info.isConstructor()) {
          Node firstChild = node.getFirstChild();
          // TODO(mknichel): Make sure this works with the following cases:
          // ns = {
          //   /** @constructor */
          //   name: function() {}
          // }
          if (name == null) {
            return true;
          }
          if ((firstChild.isGetProp() || firstChild.isName())
              && firstChild.matchesQualifiedName(name)) {
            return true;
          }
        }
        return false;
      }
    };
  }

  /**
   * Returns a Matcher that matches constructing new objects. This will match
   * the NEW node of the JS Compiler AST.
   */
  public static Matcher newClass() {
    return new Matcher() {
      @Override public boolean matches(Node node, NodeMetadata metadata) {
        return node.isNew();
      }
    };
  }

  /**
   * Returns a Matcher that matches constructing objects of the provided class
   * name. This will match the NEW node of the JS Compiler AST.
   * @param className The name of the class to return matching NEW nodes.
   */
  public static Matcher newClass(final String className) {
    return new Matcher() {
      @Override public boolean matches(Node node, NodeMetadata metadata) {
        if (!node.isNew()) {
          return false;
        }
        JSType providedJsType = getJsType(metadata, className);
        if (providedJsType == null) {
          return false;
        }

        JSType jsType = node.getJSType();
        if (jsType == null) {
          return false;
        }
        jsType = jsType.restrictByNotNullOrUndefined();
        return areTypesEquivalentIgnoringGenerics(jsType, providedJsType);
      }
    };
  }

  /**
   * Returns a Matcher that matches any function call.
   */
  public static Matcher functionCall() {
    return functionCall(null);
  }

  /**
   * Returns a Matcher that matches any function call that has the given
   * number of arguments.
   */
  public static Matcher functionCallWithNumArgs(final int numArgs) {
    return new Matcher() {
      @Override public boolean matches(Node node, NodeMetadata metadata) {
        return node.isCall() && (node.getChildCount() - 1) == numArgs;
      }
    };
  }

  /**
   * Returns a Matcher that matches any function call that has the given
   * number of arguments and the given name.
   * @param name The name of the function to match. For non-static functions,
   *     this must be the fully qualified name that includes the type of the
   *     object. For instance: {@code ns.AppContext.prototype.get} will match
   *     {@code appContext.get} and {@code this.get} when called from the
   *     AppContext class.
   */
  public static Matcher functionCallWithNumArgs(final String name, final int numArgs) {
    return allOf(functionCallWithNumArgs(numArgs), functionCall(name));
  }

  /**
   * Returns a Matcher that matches all nodes that are function calls that match
   * the provided name.
   * @param name The name of the function to match. For non-static functions,
   *     this must be the fully qualified name that includes the type of the
   *     object. For instance: {@code ns.AppContext.prototype.get} will match
   *     {@code appContext.get} and {@code this.get} when called from the
   *     AppContext class.
   */
  public static Matcher functionCall(final String name) {
    return new Matcher() {
      @Override public boolean matches(Node node, NodeMetadata metadata) {
        // TODO(mknichel): Handle the case when functions are applied through .call or .apply.
        return node.isCall() && propertyAccess(name).matches(node.getFirstChild(), metadata);
      }
    };
  }

  /**
   * Returns a Matcher that matches any property access.
   */
  public static Matcher propertyAccess() {
    return propertyAccess(null);
  }

  /**
   * Returns a Matcher that matches nodes representing a GETPROP access of
   * an object property.
   * @param name The name of the property to match. For non-static properties,
   *     this must be the fully qualified name that includes the type of the
   *     object. For instance: {@code ns.AppContext.prototype.root}
   *     will match {@code appContext.root} and {@code this.root} when accessed
   *     from the AppContext.
   */
  public static Matcher propertyAccess(final String name) {
    return new Matcher() {
      @Override public boolean matches(Node node, NodeMetadata metadata) {
        if (node.isGetProp()) {
          if (name == null) {
            return true;
          }
          if (name.equals(node.getQualifiedName())) {
            return true;
          } else if (name.contains(".prototype.")) {
            return matchesPrototypeInstanceVar(node, metadata, name);
          }
        }
        return false;
      }
    };
  }

  /**
   * Returns a Matcher that matches definitions of any enum.
   */
  public static Matcher enumDefinition() {
    return new Matcher() {
      @Override public boolean matches(Node node, NodeMetadata metadata) {
        JSType jsType = node.getJSType();
        return jsType != null && jsType.isEnumType();
      }
    };
  }

  /**
   * Returns a Matcher that matches definitions of an enum of the given type.
   */
  public static Matcher enumDefinitionOfType(final String type) {
    return new Matcher() {
      @Override public boolean matches(Node node, NodeMetadata metadata) {
        JSType providedJsType = getJsType(metadata, type);
        if (providedJsType == null) {
          return false;
        }
        providedJsType = providedJsType.restrictByNotNullOrUndefined();

        JSType jsType = node.getJSType();
        return jsType != null && jsType.isEnumType() && providedJsType.isEquivalentTo(
            jsType.toMaybeEnumType().getElementsType().getPrimitiveType());
      }
    };
  }

  /**
   * Returns a Matcher that matches an ASSIGN node where the RHS of the assignment matches the given
   * rhsMatcher.
   */
  public static Matcher assignmentWithRhs(final Matcher rhsMatcher) {
    return new Matcher() {
      @Override public boolean matches(Node node, NodeMetadata metadata) {
        return node.isAssign() && rhsMatcher.matches(node.getLastChild(), metadata);
      }
    };
  }

  /**
   * Returns a Matcher that matches a declaration of a variable on the
   * prototype of a class.
   */
  public static Matcher prototypeVariableDeclaration() {
    return matcherForPrototypeDeclaration(false /* requireFunctionType */);
  }

  /**
   * Returns a Matcher that matches a declaration of a method on the
   * prototype of a class.
   */
  public static Matcher prototypeMethodDeclaration() {
    return matcherForPrototypeDeclaration(true /* requireFunctionType */);
  }

  /**
   * Returns a Matcher that matches nodes that contain JS Doc that specify the
   * {@code @type} annotation equivalent to the provided type.
   */
  public static Matcher jsDocType(final String type) {
    return new Matcher() {
      @Override public boolean matches(Node node, NodeMetadata metadata) {
        JSType providedJsType = getJsType(metadata, type);
        if (providedJsType == null) {
          return false;
        }
        providedJsType = providedJsType.restrictByNotNullOrUndefined();
        // The JSDoc for a var declaration is on the VAR node, but the type only
        // exists on the NAME node.
        // TODO(mknichel): Make NodeUtil.getBestJSDoc public and use that.
        JSDocInfo jsDoc = node.getParent().isVar()
            ? node.getParent().getJSDocInfo() : node.getJSDocInfo();
        JSType jsType = node.getJSType();
        return jsDoc != null && jsType != null
            && providedJsType.isEquivalentTo(jsType.restrictByNotNullOrUndefined());
      }
    };
  }

  /**
   * Returns a Matcher that matches against properties that are declared in the constructor.
   */
  public static Matcher constructorPropertyDeclaration() {
    return new Matcher() {
      @Override public boolean matches(Node node, NodeMetadata metadata) {
        // This will match against code that looks like:
        // /** @constructor */
        // function constructor() {
        //   this.variable = 3;
        // }
        if (!node.isAssign()
            || !node.getFirstChild().isGetProp()
            || !node.getFirstFirstChild().isThis()) {
          return false;
        }
        while (node != null && !node.isFunction()) {
          node = node.getParent();
        }
        if (node != null && node.isFunction()) {
          JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(node);
          if (jsDoc != null) {
            return jsDoc.isConstructor();
          }
        }
        return false;
      }
    };
  }

  /**
   * Returns a Matcher that matches against nodes that are declared {@code @private}.
   */
  public static Matcher isPrivate() {
    return new Matcher() {
      @Override public boolean matches(Node node, NodeMetadata metadata) {
        JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(node);
        if (jsDoc != null) {
          return jsDoc.getVisibility() == Visibility.PRIVATE;
        }
        return false;
      }
    };
  }

  private static JSType getJsType(NodeMetadata metadata, String type) {
    return metadata.getCompiler().getTypeRegistry().getType(type);
  }

  private static JSType getJsType(NodeMetadata metadata, JSTypeNative nativeType) {
    return metadata.getCompiler().getTypeRegistry().getNativeType(nativeType);
  }

  private static boolean areTypesEquivalentIgnoringGenerics(JSType a, JSType b) {
    boolean equivalent = a.isEquivalentTo(b);
    if (equivalent) {
      return true;
    }
    if (a.isTemplatizedType()) {
      return a.toMaybeTemplatizedType().getReferencedType().isEquivalentTo(b);
    }
    return false;
  }

  /**
   * Checks to see if the node represents an access of an instance variable
   * on an object given a prototype declaration of an object. For instance,
   * {@code ns.AppContext.prototype.get} will match {@code appContext.get}
   * or {@code this.get} when accessed from within the AppContext object.
   */
  private static boolean matchesPrototypeInstanceVar(Node node, NodeMetadata metadata,
      String name) {
    String[] parts = name.split(".prototype.");
    String className = parts[0];
    String propertyName = parts[1];
    JSType providedJsType = getJsType(metadata, className);
    if (providedJsType == null) {
      return false;
    }
    JSType jsType = null;
    if (node.hasChildren()) {
      jsType = node.getFirstChild().getJSType();
    }
    if (jsType == null) {
      return false;
    }
    jsType = jsType.restrictByNotNullOrUndefined();
    if (!jsType.isUnknownType()
        && !jsType.isAllType()
        && jsType.isSubtype(providedJsType)) {
      if (node.isName() && propertyName.equals(node.getString())) {
        return true;
      } else if (node.isGetProp()
          && propertyName.equals(node.getLastChild().getString())) {
        return true;
      }
    }
    return false;
  }

  private static Matcher matcherForPrototypeDeclaration(final boolean requireFunctionType) {
    return new Matcher() {
      @Override public boolean matches(Node node, NodeMetadata metadata) {
        // TODO(mknichel): Figure out which node is the best to return for this
        // function: the GETPROP node, or the ASSIGN node when the property is
        // being assigned to.
        // TODO(mknichel): Support matching:
        // foo.prototype = {
        //   bar: 1
        // };
        Node firstChild = node.getFirstChild();
        if (node.isGetProp() && firstChild.isGetProp()
            && firstChild.getLastChild().isString()
            && "prototype".equals(firstChild.getLastChild().getString())) {
          JSType fnJsType = getJsType(metadata, JSTypeNative.FUNCTION_FUNCTION_TYPE);
          JSType jsType = node.getJSType();
          if (jsType == null) {
            return false;
          } else if (requireFunctionType) {
            return jsType.canCastTo(fnJsType);
          } else {
            return !jsType.canCastTo(fnJsType);
          }
        }
        return false;
      }
    };
  }

  // TODO(mknichel): Add matchers for:
  // - Constructor with argument types
  // - Function call with argument types
  // - Function definitions.
  // - Property definitions, references
  // - IsStatic
  // - JsDocMatcher

  /** Prevent instantiation. */
  private Matchers() {}
}
