/*
 * Copyright 2012 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.UnionType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

/**
 * Check to ensure there exists a path to dispose of each eventful object
 * created.
 *
 * An eventful class is any class that derives from goog.events.EventHandler
 * or (in aggressive mode) is disposable and disposes of an eventful class when
 * it is disposed (see http://research.google.com/pubs/pub40738.html).
 *
 * This pass is heuristic based and should not be used for any check
 * of pass/fail testing. The pass traverses the AST and marks as errors
 * cases where an eventful object is allocated but a dispose call is not found.
 * It only tracks eventful objects that has a easily identifiable static name,
 * i.e., objects assigned to arrays, returned from functions or captured in
 * closures are not considered. It simply tries to see if there exists a call to
 * a dispose method in the AST for every object seen as eventful.
 *
 * This compiler pass uses the inferred types and hence either type checking or
 * type inference needs to be enabled.
 *
 *
 */
 // TODO(user): Pass needs to be updated for listenable interfaces.
public class CheckEventfulObjectDisposal implements CompilerPass {

  static final DiagnosticType EVENTFUL_OBJECT_NOT_DISPOSED =
      DiagnosticType.error(
        "JSC_EVENTFUL_OBJECT_NOT_DISPOSED",
        "eventful object created should be\n" +
        "  * registered as disposable, or\n" +
        "  * explicitly disposed of");
  static final DiagnosticType EVENTFUL_OBJECT_PURELY_LOCAL =
      DiagnosticType.error(
        "JSC_EVENTFUL_OBJECT_PURELY_LOCAL",
        "a purely local eventful object cannot be disposed of later");
  static final DiagnosticType OVERWRITE_PRIVATE_EVENTFUL_OBJECT =
      DiagnosticType.error(
        "JSC_OVERWRITE_PRIVATE_EVENTFUL_OBJECT",
        "private eventful object overwritten in subclass cannot be properly "
        + "disposed of");
  static final DiagnosticType UNLISTEN_WITH_ANONBOUND =
      DiagnosticType.error(
        "JSC_UNLISTEN_WITH_ANONBOUND",
        "an unlisten call with an anonymous or bound function does not result "
        + "in the event being unlisted to");

  /**
   * Policies to determine the disposal checking level.
   */
  public enum DisposalCheckingPolicy {
    /**
     * Don't check any disposal.
     */
    OFF,

    /**
     * Default/conservative disposal checking.
     */
    ON,

    /**
     * Aggressive disposal checking.
     */
    AGGRESSIVE,
  }

  // Seed types
  private static final String DISPOSABLE_INTERFACE_TYPE_NAME =
      "goog.disposable.IDisposable";
  private static final String EVENT_HANDLER_TYPE_NAME =
      "goog.events.EventHandler";
  private JSType googDisposableInterfaceType;
  private JSType googEventsEventHandlerType;

  // Eventful types
  private Set<JSType> eventfulTypes;

  /*
   * Dispose methods is a map of types to maps from property/function name
   * to argument disposed/all arguments disposed. The key is used to filter
   * the dispose calls checked against. That is, the pass considers all dispose
   * calls of classes a class is derived from and not merely those in the map
   * of its given type.
   * Note: it is assumed that at most one string match will occur per
   * disposeMethod call.
   */
  private Map<JSType, Map<String, List<Integer>>> disposeCalls;

  /**
   * Constant used to signify all arguments of method/function
   * should be marked as disposed.
   */
  public static final int DISPOSE_ALL = -1;

  /**
   *  Constant used to signify that object on which this method is called,
   *  will itself get disposed of.
   */
  public static final int DISPOSE_SELF = -2;

  private final AbstractCompiler compiler;
  private final JSTypeRegistry typeRegistry;

  // At the moment only ALLOCATED and POSSIBLY_DISPOSED are used
  private enum SeenType {
    ALLOCATED, ALLOCATED_LOCALLY, POSSIBLY_DISPOSED, DISPOSED
  }

  // Combine the state and allocation site of eventful objects
  private static class EventfulObjectState {
    public SeenType seen;
    public Node allocationSite;
  }

  /*
   * The disposal checking policy used.
   */
  private final DisposalCheckingPolicy checkingPolicy;

  /*
   * Eventize DAG represented using adjacency lists.
   */
  private Map<String, Set<String>> eventizes;

  /*
   * Maps from eventful object name to state.
   */
  private static Map<String, EventfulObjectState> eventfulObjectMap;


  public CheckEventfulObjectDisposal(AbstractCompiler compiler,
      DisposalCheckingPolicy checkingPolicy) {
    this.compiler = compiler;
    this.checkingPolicy = checkingPolicy;
    this.initializeDisposeMethodsMap();
    this.typeRegistry = compiler.getTypeRegistry();
  }


  /**
   * Add a new call that is used to dispose an JS object.
   * @param functionOrMethodName The name or suffix of a function or method
   *  that disposes of/registers an object as disposable
   * @param argumentsThatAreDisposed An array of integers (ideally sorted) that
   *   specifies the arguments of the function being disposed
   */
  private void addDisposeCall(String functionOrMethodName,
      List<Integer> argumentsThatAreDisposed) {
    String potentiallyTypeName, propertyName;
    JSType objectType = null;

    int lastPeriod = functionOrMethodName.lastIndexOf('.');
    // If function call has a period it is potentially a method function.
    if (lastPeriod >= 0) {
      potentiallyTypeName = functionOrMethodName.substring(0, lastPeriod).
        replaceFirst(".prototype$", "");
      propertyName = functionOrMethodName.substring(lastPeriod);
      objectType = compiler.getTypeRegistry().getType(potentiallyTypeName);
    } else {
      propertyName = functionOrMethodName;
    }

    // Find or create property map for object type
    Map<String, List<Integer>> map = this.disposeCalls.get(objectType);
    if (map == null) {
      map = Maps.newHashMap();
      this.disposeCalls.put(objectType, map);
    }

    /*
     * If this is a static function call store the full function name,
     * else only the method of the object.
     */
    if (objectType == null) {
      map.put(functionOrMethodName, argumentsThatAreDisposed);
    } else {
      map.put(propertyName, argumentsThatAreDisposed);
    }
  }


  /*
   * Initialize disposeMethods map with calls to dispose calls.
   */
  private void initializeDisposeMethodsMap() {
    this.disposeCalls = Maps.newHashMap();

    /*
     * Initialize dispose calls map. Checks for:
     *    - Y.registerDisposable(X)
     *      (Y has to be of type goog.Disposable)
     *    - X.dispose()
     *    - goog.dispose(X)
     *    - goog.disposeAll(X...)
     *    - X.removeAll() (X is of type goog.events.EventHandler)
     *    - Y.add(X...) or Y.push(X)
     */
    this.addDisposeCall("goog.dispose", ImmutableList.of(0));
    this.addDisposeCall("goog.Disposable.registerDisposable", ImmutableList.of(0));
    this.addDisposeCall("goog.disposeAll", ImmutableList.of(DISPOSE_ALL));
    this.addDisposeCall("goog.events.EventHandler.removeAll", ImmutableList.of(DISPOSE_SELF));
    this.addDisposeCall(".dispose", ImmutableList.of(DISPOSE_SELF));
    this.addDisposeCall(".push", ImmutableList.of(0));
    this.addDisposeCall(".add", ImmutableList.of(DISPOSE_SELF));
  }


  private static Node getBase(Node n) {
    Node base = n;
    while (base.isGetProp()) {
      base = base.getFirstChild();
    }

    return base;
  }


  /*
   * Get the type of the this in the current scope of traversal
   */
  private static JSType getTypeOfThisForScope(NodeTraversal t) {
    JSType typeOfThis = t.getScopeRoot().getJSType();
    if (typeOfThis == null) {
      return null;
    }
    ObjectType objectType =
        ObjectType.cast(dereference(typeOfThis));
    return objectType.getTypeOfThis();
  }


  /**
   * Determines if thisType is possibly a subtype of thatType.
   *
   *  It differs from isSubtype only in that thisType gets expanded
   *  if it is a union.
   *
   *  Common case targeted is a function returning an eventful object
   *  that may also return a null.
   *
   *  @param thisType the JSType being tested
   *  @param thatType the JSType that is possibly a base of thisType
   *  @return whether thisType is possibly subtype of thatType
   */
  private static boolean isPossiblySubtype(JSType thisType, JSType thatType) {
    if (thisType == null) {
      return false;
    }

    JSType type = thisType;

    if (type.isUnionType()) {
      for (JSType alternate : type.toMaybeUnionType().getAlternates()) {
        if (alternate.isSubtype(thatType)) {
          return true;
        }
      }
    } else {
      if (type.isSubtype(thatType)) {
        return true;
      }
    }

    return false;
  }

  private static JSType dereference(JSType type) {
    return type == null ? null : type.dereference();
  }

  /*
   * Create a unique identification string for Node n, or null if function
   * called with invalid argument.
   *
   * This function is basically used to distinguish between:
   *   A.B = function() {
   *     this.eh = new ...
   *   }
   * and
   *   C.D = function() {
   *     this.eh = new ...
   *   }
   *
   * As well as
   *   A.B = function() {
   *     var eh = new ...
   *   }
   * and
   *   C.D = function() {
   *     var eh = new ...
   *   }
   *
   * Warning: Inheritance is not currently handled.
   */
  private static String generateKey(NodeTraversal t, Node n,
      boolean noLocalVariables) {
    if (n == null) {
      return null;
    }
    String key;

    Node scopeNode = t.getScopeRoot();

    if (n.isName()) {
      if (noLocalVariables) {
        return null;
      }
      key = n.getQualifiedName();

      if (scopeNode.isFunction()) {
        JSType parentScopeType = t.getScope().getParentScope().getTypeOfThis();
        /*
         * If the locally defined variable is defined within a function, use
         * the function name to create ID.
         */
        if (!parentScopeType.isGlobalThisType()) {
          key = parentScopeType + "~" + key;
        }
        key = NodeUtil.getFunctionName(scopeNode) + "=" + key;
      }
    } else {
      /*
       * Only handle cases such as a.b.c.X and not cases where the
       * eventful object is stored in an array or uses a function to
       * determine the index.
       *
       * Note: Inheritance changes the name that should be returned here
       */
      if (!n.isQualifiedName()) {
        return null;
      }
      key = n.getQualifiedName();

      /*
       * If it is not a simple variable and doesn't use this, then we assume
       * global variable.
       */
      Node base = getBase(n);
      if (base != null && base.isThis()) {
        if (base.getJSType().isUnknownType()) {
          // Handle anonymous function created in constructor:
          //
          // /**
          // * @extends {goog.SubDisposable}
          // * @constructor */
          // speel.Person = function() {
          //  this.run = function() {
          //    this.eh = new goog.events.EventHandler();
          //  }
          //};
          key = t.getScope().getParentScope().getTypeOfThis() + "~" + key;
        } else {
          if (n.getFirstChild() == null) {
            key = base.getJSType() + "=" + key;
          } else {
            ObjectType objectType =
                ObjectType.cast(dereference(n.getFirstChild().getJSType()));
            if (objectType == null) {
              return null;
            }

            ObjectType hObjT = objectType;
            String propertyName = n.getLastChild().getString();

            while (objectType != null) {
              hObjT = objectType;
              objectType = objectType.getImplicitPrototype();
              if (objectType == null) {
                break;
              }
              if (objectType.getDisplayName().endsWith("prototype")) {
                continue;
              }
              if (!objectType.getPropertyNames().contains(propertyName)) {
                break;
              }
            }
            key = hObjT + "=" + key;
          }
        }
      }
    }

    return key;
  }

  @Override
  public void process(Node externs, Node root) {
    // This pass should not have gotten added in this case
    Preconditions.checkArgument(checkingPolicy != DisposalCheckingPolicy.OFF);

    // Initialize types
    googDisposableInterfaceType =
        compiler.getTypeRegistry().getType(DISPOSABLE_INTERFACE_TYPE_NAME);
    googEventsEventHandlerType = compiler.getTypeRegistry()
        .getType(EVENT_HANDLER_TYPE_NAME);

    /*
     * Required types not found therefore the kind of pattern considered
     * will not be found.
     */
    if (googEventsEventHandlerType == null ||
        googDisposableInterfaceType == null) {
      return;
    }

    // Seed list of disposable stype
    eventfulTypes = new HashSet<>();
    eventfulTypes.add(googEventsEventHandlerType);

    // Construct eventizer graph
    if (checkingPolicy == DisposalCheckingPolicy.AGGRESSIVE) {
      NodeTraversal.traverse(compiler, root, new ComputeEventizeTraversal());
      computeEventful();
    }

    /*
     * eventfulObjectMap maps a eventful object's "name" to its corresponding
     * EventfulObjectState which tracks the state (allocated, disposed of)
     * as well as allocation site.
     */
    eventfulObjectMap = new HashMap<>();

    // Traverse tree
    NodeTraversal.traverse(compiler, root, new Traversal());

    /*
     * Scan eventfulObjectMap for allocated eventful objects that
     * had no dispose calls.
     */
    for (EventfulObjectState e : eventfulObjectMap.values()) {
      Node n = e.allocationSite;
      if (e.seen == SeenType.ALLOCATED) {
        compiler.report(JSError.make(n, EVENTFUL_OBJECT_NOT_DISPOSED));
      } else if (e.seen == SeenType.ALLOCATED_LOCALLY &&
          checkingPolicy == DisposalCheckingPolicy.AGGRESSIVE) {
        compiler.report(JSError.make(n, EVENTFUL_OBJECT_PURELY_LOCAL));
      }
    }
  }

  private void computeEventful() {
    /*
     * Topological order of Eventize DAG
     */
    String[] order = new String[eventizes.size()];

    /*
     * Perform topological sort
     */
    int white = 0, gray = 1, black = 2;
    int last = eventizes.size() - 1;
    Map<String, Integer> color = new HashMap<>();
    Stack<String> dfsStack = new Stack<>();

    /*
     * Initialize color.
     * Some types are only on one or the other side of the
     * inference.
     */
    for (Map.Entry<String, Set<String>> eventizesEntry : eventizes.entrySet()) {
      color.put(eventizesEntry.getKey(), white);
      for (String s : eventizesEntry.getValue()) {
        color.put(s, white);
      }
    }

    int indx = 0;
    for (String s : eventizes.keySet()) {
      dfsStack.push(s);
      while (!dfsStack.isEmpty()) {
        String top = dfsStack.pop();
        if (!color.containsKey(top)) {
          continue;
        }
        if (color.get(top) == white) {
          color.put(top, gray);
          dfsStack.push(top);
          // for v in Adj[s]
          if (eventizes.containsKey(top)) {
            for (String v : eventizes.get(top)) {
              if (color.get(v) == white) {
                dfsStack.push(v);
              }
            }
          }
        } else if (color.get(top) == gray && eventizes.containsKey(top)) {
          order[last - indx] = top;
          ++indx;
          color.put(top, black);
        }
      }
    }

    /*
     * Propagate eventfulness by iterating in topological order
     */
    for (String s : order) {
      if (eventfulTypes.contains(typeRegistry.getType(s))) {
        for (String v : eventizes.get(s)) {
          eventfulTypes.add(typeRegistry.getType(v));
        }
      }
    }
  }

  private JSType maybeReturnDisposedType(Node n, boolean checkDispose) {
    /*
     * Checks for:
     *    - Y.registerDisposable(X)
     *      (Y has to be of type goog.Disposable)
     *    - X.dispose()
     *    - goog.dispose(X)
     *    - X.removeAll() (X is of type goog.events.EventHandler)
     *    - <array>.property(X) or Y.push(X)
     */
    Node first = n.getFirstChild();

    if (first == null || !first.isQualifiedName()) {
      return null;
    }
    String property = first.getQualifiedName();

    if (property.endsWith(".registerDisposable"))  {
      /*
       *  Ensure object is of type disposable
       */
      Node base = first.getFirstChild();
      JSType baseType = base.getJSType();

      if (baseType == null ||
          !isPossiblySubtype(baseType, googDisposableInterfaceType)) {
        return null;
      }

      return n.getLastChild().getJSType();
    }

    if (checkDispose) {
      if (property.equals("goog.dispose")) {
        return n.getLastChild().getJSType();
      }
      if (property.endsWith(".dispose")) {
        /*
         * n -> call
         *   n.firstChild -> "dispose"
         *   n.firstChild.firstChild -> object
         */
        return n.getFirstChild().getFirstChild().getJSType();
      }
    }

    return null;
  }

  /*
   * Compute eventize relationship graph.
   */
  private class ComputeEventizeTraversal extends AbstractPostOrderCallback
      implements ScopedCallback {

    /*
     * Keep track of whether in the constructor or disposal scope.
     */
    Stack<Boolean> isConstructorStack;
    Stack<Boolean> isDisposalStack;


    public ComputeEventizeTraversal() {
      isConstructorStack = new Stack<>();
      isDisposalStack = new Stack<>();
      eventizes = new HashMap<>();
    }

    private Boolean inConstructorScope() {
      Preconditions.checkNotNull(isConstructorStack);
      if (!isDisposalStack.isEmpty()) {
        return isConstructorStack.peek();
      }
      return null;
    }

    private Boolean inDisposalScope() {
      Preconditions.checkNotNull(isDisposalStack);
      if (!isDisposalStack.isEmpty()) {
        return isDisposalStack.peek();
      }
      return null;
    }

    /*
     * Filter types not interested in for eventize graph
     */
    private boolean collectorFilterType(JSType type) {
      if (type == null) {
        return true;
      }

      return type.isEmptyType() || type.isUnknownType()
          || !isPossiblySubtype(type, googDisposableInterfaceType);
    }

    /*
     * Log that thisType eventizes thatType.
     */
    private void addEventize(JSType thisType, JSType thatType) {
      if (collectorFilterType(thisType) ||
          collectorFilterType(thatType) ||
          thisType.isEquivalentTo(thatType)) {
        return;
      }

      String className = thisType.getDisplayName();
      if (thatType.isUnionType()) {
        UnionType ut = thatType.toMaybeUnionType();
        for (JSType type : ut.getAlternates()) {
          if (type.isObject()) {
            addEventizeClass(className, type);
          }
        }
      } else {
        addEventizeClass(className, thatType);
      }
    }

    private void addEventizeClass(String className, JSType thatType) {
      String propertyJsTypeName = thatType.getDisplayName();

      Set<String> eventize = eventizes.get(propertyJsTypeName);
      if (eventize == null) {
        eventize = new HashSet<>();
        eventizes.put(propertyJsTypeName, eventize);
      }
      eventize.add(className);
    }

    @Override
    public void enterScope(NodeTraversal t) {
      Node n = t.getScopeRoot();
      boolean isConstructor = false;
      boolean isInDisposal = false;
      String functionName = null;

      /*
       * Scope entered is a function definition
       */
      if (n.isFunction()) {
        functionName = NodeUtil.getFunctionName(n);

        /*
         *  Skip anonymous functions
         */
        if (functionName != null) {

          JSDocInfo jsDocInfo = NodeUtil.getBestJSDocInfo(n);
          if (jsDocInfo != null) {
            /*
             *  Record constructor of a type
             */
            if (jsDocInfo.isConstructor()) {
              isConstructor = true;

              /*
               * Initialize eventizes relationship
               */
              if (t.getScope() != null &&
                  t.getScope().getTypeOfThis() != null) {
                ObjectType objectType = ObjectType.cast(t.getScope()
                    .getTypeOfThis().dereference());

                /*
                 * Eventize due to inheritance
                 */

                while (objectType != null) {
                  objectType = objectType.getImplicitPrototype();
                  if (objectType == null) {
                    break;
                  }

                  if (objectType.getDisplayName().endsWith("prototype")) {
                    continue;
                  }

                  addEventize(compiler.getTypeRegistry().getType(functionName),
                      objectType);

                  /*
                   * Don't add transitive eventize edges here, it will be
                   * taken care of in computeEventful
                   */
                  break;
                }
              }
            }
          }

          /*
           *  Indicate within a disposeInternal member
           */
          if (functionName.endsWith(".disposeInternal")) {
            isInDisposal = true;
          }
        }

        isConstructorStack.push(isConstructor);
        isDisposalStack.push(isInDisposal);
      } else {
        isConstructorStack.push(inConstructorScope());
        isDisposalStack.push(inDisposalScope());
      }
    }

    @Override
    public void exitScope(NodeTraversal t) {
      isConstructorStack.pop();
      isDisposalStack.pop();
    }

    /*
     * Is the current node a call to goog.events.unlisten
     */
    private void isGoogEventsUnlisten(Node n) {
      Preconditions.checkArgument(n.getChildCount() > 3);

      Node listener = n.getChildAtIndex(3);

      Node objectWithListener = n.getChildAtIndex(1);
      if (!objectWithListener.isQualifiedName()) {
        return;
      }

      if (listener.isFunction()) {
        /*
         * Anonymous function
         */
        compiler.report(JSError.make(n, UNLISTEN_WITH_ANONBOUND));
      } else if (listener.isCall()) {
        if (!listener.getFirstChild().isQualifiedName()) {
          /*
           * Anonymous function
           */
          compiler.report(JSError.make(n, UNLISTEN_WITH_ANONBOUND));
        } else if (listener.getFirstChild().matchesQualifiedName("goog.bind")) {
          /*
           * Using goog.bind to unlisten
           */
          compiler.report(JSError.make(n, UNLISTEN_WITH_ANONBOUND));
        }
      }
    }


    private void visitCall(NodeTraversal t, Node n) {
      Node functionCalled = n.getFirstChild();
      if (functionCalled == null ||
          !functionCalled.isQualifiedName()) {
          return;
      }
      JSType typeOfThis = getTypeOfThisForScope(t);
      if (typeOfThis == null) {
        return;
      }

      /*
       * Class considered eventful if there is an unlisten call in the
       * disposal.
       */
      if (functionCalled.matchesQualifiedName("goog.events.unlisten")) {

        if (inDisposalScope()) {
          eventfulTypes.add(typeOfThis);
        }
        isGoogEventsUnlisten(n);
      }
      if (inDisposalScope() &&
          functionCalled.matchesQualifiedName("goog.events.removeAll")) {
        eventfulTypes.add(typeOfThis);
      }

      /*
       * If member with qualified name gets disposed of when this class
       * gets disposed, consider the member type as an eventizer of this
       * class.
       */
      JSType disposedType = maybeReturnDisposedType(n, inDisposalScope());
      if (!collectorFilterType(disposedType)) {
        addEventize(getTypeOfThisForScope(t), disposedType);
      }
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.CALL:
          visitCall(t, n);
          break;
        default:
          break;
      }
    }
  }

  private class Traversal extends AbstractPostOrderCallback
      implements ScopedCallback {
    /*
     * Checks if the input node correspond to the creation of an eventful object
     */
    private boolean createsEventfulObject(Node n) {
      Node first = n.getFirstChild();
      JSType type = n.getJSType();
      if (first == null ||
          !first.isQualifiedName() ||
          type.isEmptyType() ||
          type.isUnknownType()) {
        return false;
      }

      boolean isOfTypeNeedingDisposal = false;
      for (JSType disposableType : eventfulTypes) {
        if (type.isSubtype(disposableType)) {
          isOfTypeNeedingDisposal = true;
          break;
        }
      }
      return isOfTypeNeedingDisposal;
    }

    /*
     * This function traverses the current scope to see if a locally
     * defined eventful object is assigned to a live-out variable.
     *
     * Note: This function could be called multiple times to traverse
     * the same scope if multiple local eventful objects are created in the
     * scope.
     */
    private Node localEventfulObjectAssign(
        NodeTraversal t, Node propertyNode) {
      Node parent;
      if (!t.getScope().isGlobal()) {
        /*
         * In function
         */
        parent = NodeUtil.getFunctionBody(t.getScopeRoot());
      } else {
        /*
         * In global scope
         */
        parent = t.getScopeRoot().getFirstChild();
      }

      /*
       * Check to see if locally created EventHandler is assigned to field
       */
      for (Node sibling : parent.children()) {
        if (sibling.isExprResult()) {
          Node assign = sibling.getFirstChild();
          if (assign.isAssign()) {
            // assign.getLastChild().isEquivalentTo(propertyNode) did not work
            if (propertyNode.matchesQualifiedName(assign.getLastChild())) {
              if (!assign.getFirstChild().isName()) {
                return assign.getFirstChild();
              }
            }
          }
        }
      }

      /*
       * Eventful object created and assigned to a local variable which is not
       * assigned to another variable in a way to allow disposal.
       */
      String key = generateKey(t, propertyNode, false);
      if (key == null) {
        return null;
      }

      EventfulObjectState e;
      if (eventfulObjectMap.containsKey(key)) {
        e = eventfulObjectMap.get(key);

        if (e.seen == SeenType.ALLOCATED) {
          e.seen = SeenType.ALLOCATED_LOCALLY;
        }
      } else {
        e = new EventfulObjectState();
        e.seen = SeenType.ALLOCATED_LOCALLY;

        eventfulObjectMap.put(key, e);
      }
      e.allocationSite = propertyNode;


      return null;
    }

    /*
     * Record the creation of a new eventful object.
     */
    private void visitNew(NodeTraversal t, Node n, Node parent) {
      if (!createsEventfulObject(n)) {
        return;
      }

      /*
       * Insert allocation site and construct into eventfulObjectMap
       */
      String key;
      Node propertyNode;

      /*
       * Handles (E is an eventful class):
       *  - object.something = new E();
       *  - local = new E();
       *  - var local = new E();
       */
      if (parent.isAssign()) {
        propertyNode = parent.getFirstChild();
      } else {
        propertyNode = parent;
      }

      key = generateKey(t, propertyNode, false);
      if (key == null) {
        return;
      }

      EventfulObjectState e;
      if (eventfulObjectMap.containsKey(key)) {
        e = eventfulObjectMap.get(key);
      } else {
        e = new EventfulObjectState();
        e.seen = SeenType.ALLOCATED;

        eventfulObjectMap.put(key, e);
      }
      e.allocationSite = propertyNode;

      /*
       * Check if locally defined eventful object is assigned to global variable
       * and create an entry mapping to the previous site.
       */
      if (propertyNode.isName()) {
        Node globalVarNode = localEventfulObjectAssign(t, propertyNode);
        if (globalVarNode != null) {
          key = generateKey(t, globalVarNode, false);
          if (key == null) {
            /*
             * Local variable is assigned to an array or in a manner requiring
             * a function call.
             */
            e.seen = SeenType.POSSIBLY_DISPOSED;
            return;
          }
          eventfulObjectMap.put(key, e);
        }
      }
    }

    private void addDisposeArgumentsMatched(Map<String, List<Integer>> map,
        Node n, String property, List<Node> foundDisposeCalls) {
      for (Map.Entry<String, List<Integer>> disposeCallsEntry : map.entrySet()) {
        if (property.endsWith(disposeCallsEntry.getKey())) {
          List<Integer> disposeArguments = disposeCallsEntry.getValue();

          // Dispose specific arguments only
          Node t = n.getNext();
          int tsArgument = 0;
          for (Integer disposeArgument : disposeArguments) {
            switch (disposeArgument) {
              // Dispose all arguments
              case DISPOSE_ALL:
                for (Node tt = n.getNext(); tt != null; tt = tt.getNext()) {
                  foundDisposeCalls.add(tt);
                }
                break;
              // Dispose objects called on
              case DISPOSE_SELF:
                Node calledOn = n.getFirstChild();

                foundDisposeCalls.add(calledOn);
                break;
              default:
                // The current item pointed to by t is beyond that requested in
                // current array element.
                if (tsArgument > disposeArgument) {
                  t = n.getNext();
                  tsArgument = 0;
                }
                for (; tsArgument < disposeArgument && t != null;
                        ++tsArgument) {
                  t = t.getNext();
                }
                if (tsArgument == disposeArgument && t != null) {
                  foundDisposeCalls.add(t);
                }
                break;
            }
          }
        }
      }
    }

    private List<Node> maybeGetValueNodesFromCall(Node n) {
      List<Node> ret = Lists.newArrayList();
      Node first = n.getFirstChild();

      if (first == null || !first.isQualifiedName()) {
        return ret;
      }
      String property = first.getQualifiedName();

      Node base = first.getFirstChild();
      JSType baseType = null;
      if (base != null) {
        baseType = base.getJSType();
      }

      for (Map.Entry<JSType, Map<String, List<Integer>>> disposeCallEntry :
          disposeCalls.entrySet()) {
        JSType key = disposeCallEntry.getKey();
        if (key == null ||
            (baseType != null && isPossiblySubtype(baseType, key))) {
          addDisposeArgumentsMatched(disposeCallEntry.getValue(), first, property, ret);
        }
      }

      return ret;
    }

    /*
     * Look for calls to an eventful object's disposal functions.
     * (dispose or removeAll will remove all event listeners from
     * an EventHandler).
     */
    private void visitCall(NodeTraversal t, Node n) {
      // Filter the calls to find a "dispose" call
      List<Node> variableNodes = maybeGetValueNodesFromCall(n);

      for (Node variableNode : variableNodes) {
        Preconditions.checkState(variableNode != null);

        // Only consider removals on eventful object
        boolean isTrackedRemoval = false;
        JSType vnType = variableNode.getJSType();
        for (JSType type : eventfulTypes) {
          if (isPossiblySubtype(vnType, type)) {
            isTrackedRemoval = true;
          }
        }
        if (!isTrackedRemoval) {
          continue;
        }

        String key = generateKey(t, variableNode, false);
        if (key == null) {
          continue;
        }

        eventfulObjectDisposed(t, variableNode);
      }
    }

    /**
     * Dereference a type, autoboxing it and filtering out null.
     * From {@link CheckAccessControls}
     */
    private JSType dereference(JSType type) {
      return type == null ? null : type.dereference();
    }

    /*
     * Check function definitions to add custom dispose methods.
     */
    public void visitFunction(NodeTraversal t, Node n) {
      Preconditions.checkArgument(n.isFunction());
      JSDocInfo jsDocInfo = NodeUtil.getFunctionJSDocInfo(n);

      // Function annotated to dispose of
      if (jsDocInfo != null && jsDocInfo.isDisposes()) {
        JSType type = n.getJSType();
        if (type == null || type.isUnknownType()) {
          return;
        }

        FunctionType funType = type.toMaybeFunctionType();
        Node paramNode = NodeUtil.getFunctionParameters(n).getFirstChild();
        List<Integer> positionalDisposedParameters = Lists.newArrayList();

        if (jsDocInfo.disposesOf("*")) {
          positionalDisposedParameters.add(DISPOSE_ALL);
        } else {
          // Parameter types
          int index = 0;
          for (Node p : funType.getParameters()) {
              // Bail out if the paramNode is not there.
              if (paramNode == null) {
                break;
              }
              if (jsDocInfo.disposesOf(paramNode.getString())) {
                positionalDisposedParameters.add(index);
              }
              paramNode = paramNode.getNext();
              index++;
          }
        }
        addDisposeCall(NodeUtil.getFunctionName(n),
            positionalDisposedParameters);
      }
    }

    /*
     * Track assignments to see if a private field is being
     * overwritten.
     *
     * Assigning to an array element is taken care of by the generateKey
     * returning null on array ("complex") variable names.
     */
    public void visitAssign(NodeTraversal t, Node n) {
      Node assignedTo = n.getFirstChild();
      JSType assignedToType = assignedTo.getJSType();
      if (assignedToType == null || assignedToType.isEmptyType()) {
        return;
      }

      if (n.getFirstChild().isGetProp()) {
        boolean isTrackedAssign = false;
        for (JSType disposalType : eventfulTypes) {
          if (assignedToType.isSubtype(disposalType)) {
            isTrackedAssign = true;
            break;
          }
        }
        if (!isTrackedAssign) {
          return;
        }

        JSDocInfo di = n.getJSDocInfo();
        ObjectType objectType =
            ObjectType.cast(dereference(n.getFirstChild().getFirstChild()
                .getJSType()));
        String propertyName = n.getFirstChild().getLastChild().getString();

        boolean fieldIsPrivate = (
            (di != null) &&
            (di.getVisibility() == Visibility.PRIVATE));

        /*
         * See if field is defined as private in superclass
         */
        while (objectType != null) {
          di = null;
          objectType = objectType.getImplicitPrototype();
          if (objectType == null) {
            break;
          }

          /*
           * Skip prototype definitions:
           *   Don't flag a field declared private in assignment as well
           *   as in prototype declaration
           * Assumption: The inheritance hierarchy is similar to
           *   class
           *   class.prototype
           *   superclass
           *   superclass.prototype
           */
          if (objectType.getDisplayName().endsWith("prototype")) {
            continue;
          }

          di = objectType.getOwnPropertyJSDocInfo(propertyName);
          if (di != null) {
            if (fieldIsPrivate || di.getVisibility() == Visibility.PRIVATE) {
              compiler.report(
                  t.makeError(n, OVERWRITE_PRIVATE_EVENTFUL_OBJECT));
              break;
            }
          }
        }
      }
    }

    /*
     * Filter out any eventful objects returned.
     */
    private void visitReturn(NodeTraversal t, Node n) {
      Node variableNode = n.getFirstChild();
      if (variableNode == null) {
        return;
      }

      if (!variableNode.isArrayLit()) {
        eventfulObjectDisposed(t, variableNode);
      } else {
        for (Node child : variableNode.children()) {
          eventfulObjectDisposed(t, child);
        }
      }
    }

    /*
     * Mark an eventful object as being disposed.
     */
    private void eventfulObjectDisposed(NodeTraversal t, Node variableNode) {
      String key = generateKey(t, variableNode, false);
      if (key == null) {
        return;
      }

      EventfulObjectState e = eventfulObjectMap.get(key);
      if (e == null) {
        e = new EventfulObjectState();
        eventfulObjectMap.put(key, e);
      }
      e.seen = SeenType.POSSIBLY_DISPOSED;
    }

    @Override
    public void enterScope(NodeTraversal t) {
      /*
       * Local variables captured in scope are filtered at present.
       * LiveVariableAnalysis used to filter such variables.
       */
      ControlFlowGraph<Node> cfg = t.getControlFlowGraph();
      LiveVariablesAnalysis liveness =
          new LiveVariablesAnalysis(cfg, t.getScope(), compiler);
      liveness.analyze();

      for (Var v : liveness.getEscapedLocals()) {
        eventfulObjectDisposed(t, v.getNode());
      }
    }

    @Override
    public void exitScope(NodeTraversal t) {
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.ASSIGN:
          visitAssign(t, n);
          break;
        case Token.CALL:
          visitCall(t, n);
          break;
        case Token.FUNCTION:
          visitFunction(t, n);
          break;
        case Token.NEW:
          visitNew(t, n, parent);
          break;
        case Token.RETURN:
          visitReturn(t, n);
          break;
        default:
          break;
      }
    }
  }
}
