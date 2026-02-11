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

package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.NominalTypeBuilder;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.jstype.FunctionType;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * This describes the Closure-specific JavaScript coding conventions.
 */
@Immutable
public final class ClosureCodingConvention extends CodingConventions.Proxy {

  private static final long serialVersionUID = 1L;

  static final DiagnosticType OBJECTLIT_EXPECTED = DiagnosticType.warning(
      "JSC_REFLECT_OBJECTLIT_EXPECTED",
      "Object literal expected as second argument");

  public ClosureCodingConvention() {
    this(CodingConventions.getDefault());
  }

  public ClosureCodingConvention(CodingConvention wrapped) {
    super(wrapped);
  }

  /**
   * Closure's goog.inherits adds a {@code superClass_} property to the
   * subclass, and a {@code constructor} property.
   */
  @Override
  public void applySubclassRelationship(
      final NominalTypeBuilder parent, final NominalTypeBuilder child, SubclassType type) {
    super.applySubclassRelationship(parent, child, type);
    if (type == SubclassType.INHERITS) {
      final FunctionType childCtor = child.constructor();
      child.declareConstructorProperty(
          "superClass_", parent.prototypeOrInstance(), childCtor.getSource());
      // Notice that constructor functions do not need to be covariant on the superclass.
      // So if G extends F, new G() and new F() can accept completely different argument
      // types, but G.prototype.constructor needs to be covariant on F.prototype.constructor.
      // To get around this, we just turn off type-checking on arguments and return types
      // of G.prototype.constructor.
      FunctionType qmarkCtor = childCtor.forgetParameterAndReturnTypes();
      child.declarePrototypeProperty("constructor", qmarkCtor, childCtor.getSource());
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Understands several different inheritance patterns that occur in Google code (various uses
   * of {@code inherits} and {@code mixin}).
   */
  @Override
  public @Nullable SubclassRelationship getClassesDefinedByCall(Node callNode) {
    SubclassRelationship relationship =
        super.getClassesDefinedByCall(callNode);
    if (relationship != null) {
      return relationship;
    }

    Node callName = callNode.getFirstChild();
    SubclassType type = typeofClassDefiningName(callName);
    if (type != null) {
      // Possible formats for a class-defining method call:
      // goog.inherits(SubClass, SuperClass)
      // goog$inherits(SubClass, SuperClass)
      // ValueType.mixin(SubClass, SuperClass, ...) // used by J2CL.
      // ValueType$mixin(SubClass, SuperClass, ...)
      if (callNode.getChildCount() < 3) {
        return null;
      }

      // goog.inherits(SubClass, SuperClass)
      Node subclass = callName.getNext();
      Node superclass = subclass.getNext();

      if (type == SubclassType.MIXIN) {
        // Strip off the prototype from the name.
        if (endsWithPrototype(superclass)) {
          superclass = superclass.getFirstChild();
        }
        if (endsWithPrototype(subclass)) {
          subclass = subclass.getFirstChild();
        }
      }

      // bail out if either of the side of the "inherits"
      // isn't a real class name. This prevents us from
      // doing something weird in cases like:
      // goog.inherits(MySubClass, cond ? SuperClass1 : BaseClass2)
      if (subclass != null
          && subclass.isUnscopedQualifiedName()
          && superclass.isUnscopedQualifiedName()) {
        return new SubclassRelationship(type, subclass, superclass);
      }
    }

    return null;
  }

  /**
   * Determines whether the given node is a class-defining name, like "inherits" or "mixin."
   *
   * @return The type of class-defining name, or null.
   */
  private static @Nullable SubclassType typeofClassDefiningName(Node callName) {
    // Check if the method name matches one of the class-defining methods.
    String methodName = null;
    if (callName.isGetProp()) {
      methodName = callName.getString();
    } else if (callName.isName()) {
      String name = callName.getString();
      int dollarIndex = name.lastIndexOf('$');
      if (dollarIndex != -1) {
        methodName = name.substring(dollarIndex + 1);
      }
    }

    if (methodName != null) {
      if (methodName.equals("inherits")) {
        return SubclassType.INHERITS;
      } else if (methodName.equals("mixin")) {
        return SubclassType.MIXIN;
      }
    }
    return null;
  }

  @Override
  public boolean isSuperClassReference(String propertyName) {
    return "superClass_".equals(propertyName) ||
        super.isSuperClassReference(propertyName);
  }

  /**
   * Given a qualified name node, returns whether "prototype" is at the end.
   * For example:
   * a.b.c => false
   * a.b.c.prototype => true
   */
  private static boolean endsWithPrototype(Node qualifiedName) {
    return qualifiedName.isGetProp() && qualifiedName.getString().equals("prototype");
  }

  /**
   * @return Whether the node indicates that the file represents a "module", a file whose top level
   * declarations are not in global scope.
   */
  @Override
  public boolean extractIsModuleFile(Node node, Node parent) {
    String namespace = extractClassNameIfGoog(node, parent, "goog.module");
    return namespace != null;
  }

  /**
   * Extracts X from goog.provide('X'), if the applied Node is goog.
   *
   * @return The extracted class name, or null.
   */
  @Override
  public String extractClassNameIfProvide(Node node, Node parent) {
    String namespace = extractClassNameIfGoog(node, parent, "goog.provide");
    if (namespace == null) {
      namespace = extractClassNameIfGoog(node, parent, "goog.module");
    }
    return namespace;
  }

  /**
   * Extracts X from goog.require('X'), if the applied Node is goog.
   *
   * @return The extracted class name, or null.
   */
  @Override
  public String extractClassNameIfRequire(Node node, Node parent) {
    return extractClassNameIfGoog(node, parent, "goog.require");
  }

  private static String extractClassNameIfGoog(Node node, Node parent,
      String functionName){
    String className = null;
    if (NodeUtil.isExprCall(parent)) {
      Node callee = node.getFirstChild();
      if (callee != null && callee.isGetProp() && callee.matchesQualifiedName(functionName)) {
        Node target = callee.getNext();
        if (target != null && target.isStringLit()) {
          className = target.getString();
        }
      }
    }
    return className;
  }

  /**
   * Use closure's implementation.
   * @return closure's function name for exporting properties.
   */
  @Override
  public String getExportPropertyFunction() {
    return "goog.exportProperty";
  }

  /**
   * Use closure's implementation.
   * @return closure's function name for exporting symbols.
   */
  @Override
  public String getExportSymbolFunction() {
    return "goog.exportSymbol";
  }

  private static final QualifiedName GOOG_FORWARDDECLARE = QualifiedName.of("goog.forwardDeclare");

  @Override
  public List<String> identifyTypeDeclarationCall(Node n) {
    Node callName = n.getFirstChild();
    // Identify forward declaration of form goog.forwardDeclare('foo.bar')
    if (GOOG_FORWARDDECLARE.matches(callName) && n.hasTwoChildren()) {
      Node typeDeclaration = n.getSecondChild();
      if (typeDeclaration.isStringLit()) {
        return ImmutableList.of(typeDeclaration.getString());
      }
    }

    return super.identifyTypeDeclarationCall(n);
  }

  @Override
  public String getAbstractMethodName() {
    return "goog.abstractMethod";
  }

  private static final QualifiedName GOOG_ADDSINGLETONGETTER =
      QualifiedName.of("goog.addSingletonGetter");
  private static final QualifiedName GOOG_ADDSINGLETONGETTER_MANGLED =
      QualifiedName.of("goog$addSingletonGetter");

  @Override
  public String getSingletonGetterClassName(Node callNode) {
    Node callArg = callNode.getFirstChild();
    // Use both the original name and the post-CollapseProperties name.
    if (callNode.hasTwoChildren()
        && (GOOG_ADDSINGLETONGETTER.matches(callArg)
            || GOOG_ADDSINGLETONGETTER_MANGLED.matches(callArg))) {
      return callArg.getNext().getQualifiedName();
    }
    return super.getSingletonGetterClassName(callNode);
  }

  @Override
  public void applySingletonGetter(NominalTypeBuilder classType, FunctionType getterType) {
    Node defSite = classType.constructor().getSource();
    classType.declareConstructorProperty("getInstance", getterType, defSite);
    classType.declareConstructorProperty("instance_", classType.instance(), defSite);
  }

  @Override
  public boolean isPropertyTestFunction(Node call) {
    checkArgument(call.isCall());
    // Avoid building the qualified name and check for
    // "goog.isArrayLike", "goog.isObject"
    Node target = call.getFirstChild();
    if (target.isGetProp()) {
      Node src = target.getFirstChild();
      String prop = target.getString();
      if (src.isName()
          && src.getString().equals("goog")
          && (prop.equals("isArrayLike") || prop.equals("isObject"))) {
        return true;
      }
    }

    return super.isPropertyTestFunction(call);
  }

  private static final QualifiedName GOOG_REFLECT_OBJECTPROPERTY =
      QualifiedName.of("goog.reflect.objectProperty");
  private static final QualifiedName GOOG_REFLECT_OBJECTPROPERTY_MANGLED =
      QualifiedName.of("goog$reflect$objectProperty");

  @Override
  public boolean isPropertyRenameFunction(Node nameNode) {
    if (super.isPropertyRenameFunction(nameNode)) {
      return true;
    }
    return GOOG_REFLECT_OBJECTPROPERTY.matches(nameNode)
        || GOOG_REFLECT_OBJECTPROPERTY_MANGLED.matches(nameNode);
  }

  @Override
  public boolean isFunctionCallThatAlwaysThrows(Node n) {
    return super.isFunctionCallThatAlwaysThrows(n)
        || CodingConventions.defaultIsFunctionCallThatAlwaysThrows(n, "goog.asserts.fail");
  }

  private static final QualifiedName GOOG_REFLECT_OBJECT = QualifiedName.of("goog.reflect.object");
  private static final QualifiedName JSCOMP_REFLECTOBJECT =
      QualifiedName.of("$jscomp.reflectObject");

  @Override
  public @Nullable ObjectLiteralCast getObjectLiteralCast(Node callNode) {
    Preconditions.checkArgument(callNode.isCall(), "Expected call node but found %s", callNode);
    ObjectLiteralCast proxyCast = super.getObjectLiteralCast(callNode);
    if (proxyCast != null) {
      return proxyCast;
    }

    Node callName = callNode.getFirstChild();
    if (!(GOOG_REFLECT_OBJECT.matches(callName) || JSCOMP_REFLECTOBJECT.matches(callName))
        || !callNode.hasXChildren(3)) {
      return null;
    }

    Node typeNode = callName.getNext();
    if (!typeNode.isQualifiedName()) {
      return null;
    }

    Node objectNode = typeNode.getNext();
    if (!objectNode.isObjectLit()) {
      return new ObjectLiteralCast(null, null, OBJECTLIT_EXPECTED);
    }

    return new ObjectLiteralCast(typeNode.getQualifiedName(), typeNode.getNext(), null);
  }

  @Override
  public ImmutableSet<AssertionFunctionSpec> getAssertionFunctions() {
    return ImmutableSet.<AssertionFunctionSpec>builder()
        .addAll(super.getAssertionFunctions())
        .add(
            AssertionFunctionSpec.forTruthy().setFunctionName("goog.asserts.assert").build(),
            createGoogAssertOnReturn("Array"),
            createGoogAssertOnReturn("Boolean"),
            createGoogAssertOnReturn("Element"),
            createGoogAssertOnReturn("Function"),
            createGoogAssertOnReturn("Instanceof"),
            createGoogAssertOnReturn("Number"),
            createGoogAssertOnReturn("Object"),
            createGoogAssertOnReturn("String"))
        .build();
  }

  /** Returns a new assertion function goog.asserts.assert[assertedTypeName] */
  private static AssertionFunctionSpec createGoogAssertOnReturn(String assertedTypeName) {
    return AssertionFunctionSpec.forMatchesReturn()
        .setFunctionName("goog.asserts.assert" + assertedTypeName)
        .build();
  }

  private static final QualifiedName GOOG_BIND = QualifiedName.of("goog.bind");
  private static final QualifiedName GOOG_PARTIAL = QualifiedName.of("goog.partial");

  @Override
  public @Nullable Bind describeFunctionBind(Node n, boolean checkTypes) {
    if (!n.isCall()) {
      return null;
    }
    Node callTarget = n.getFirstChild();
    if (callTarget.isQualifiedName()) {
      if (GOOG_BIND.matches(callTarget) || callTarget.matchesName("goog$bind")) {
        // goog.bind(fn, self, args...);
        Node fn = callTarget.getNext();
        if (fn == null) {
          return null;
        }
        Node thisValue = safeNext(fn);
        Node parameters = safeNext(thisValue);
        return new Bind(fn, thisValue, parameters);
      }

      if (GOOG_PARTIAL.matches(callTarget) || callTarget.matchesName("goog$partial")) {
        // goog.partial(fn, args...);
        Node fn = callTarget.getNext();
        if (fn == null) {
          return null;
        }
        Node thisValue = null;
        Node parameters = safeNext(fn);
        return new Bind(fn, thisValue, parameters);
      }
    }
    return super.describeFunctionBind(n, checkTypes);
  }

  @Override
  public @Nullable Cache describeCachingCall(Node node) {
    if (!node.isCall()) {
      return null;
    }

    Node callTarget = node.getFirstChild();
    if (matchesCacheMethodName(callTarget)) {
      int paramCount = node.getChildCount() - 1;
      if (3 <= paramCount && paramCount <= 4) {
        Node cacheObj = callTarget.getNext();
        Node keyNode = cacheObj.getNext();
        Node valueFn = keyNode.getNext();
        Node keyFn = valueFn.getNext();

        return new Cache(cacheObj, keyNode, valueFn, keyFn);
      }
    }

    return super.describeCachingCall(node);
  }

  static final Node googCacheReflect = IR.getprop(IR.name("goog"), "reflect", "cache");

  private boolean matchesCacheMethodName(Node target) {
    if (target.isGetProp()) {
      return target.matchesQualifiedName(googCacheReflect);
    } else if (target.isName()) {
      return target.getString().equals("goog$reflect$cache");
    }
    return false;
  }

  private static @Nullable Node safeNext(Node n) {
    if (n != null) {
      return n.getNext();
    }
    return null;
  }
}
