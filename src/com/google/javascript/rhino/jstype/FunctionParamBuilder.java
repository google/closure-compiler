/*
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Bob Jervis
 *   Google Inc.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

package com.google.javascript.rhino.jstype;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * A builder for the Rhino Node representing Function parameters.
 * @author nicksantos@google.com (Nick Santos)
 */
public class FunctionParamBuilder {

  private final JSTypeRegistry registry;
  private final Node root = new Node(Token.PARAM_LIST);

  public FunctionParamBuilder(JSTypeRegistry registry) {
    this.registry = registry;
  }

  /**
   * Add parameters of the given type to the end of the param list.
   * @return False if this is called after optional params are added.
   */
  public boolean addRequiredParams(JSType ...types) {
    if (hasOptionalOrVarArgs()) {
      return false;
    }

    for (JSType type : types) {
      newParameter(type);
    }
    return true;
  }

  /**
   * Add optional parameters of the given type to the end of the param list.
   * @param types Types for each optional parameter. The builder will make them
   *     undefine-able.
   * @return False if this is called after var args are added.
   */
  public boolean addOptionalParams(JSType ...types) {
    if (hasVarArgs()) {
      return false;
    }

    for (JSType type : types) {
      newParameter(registry.createOptionalType(type)).setOptionalArg(true);
    }
    return true;
  }

  /**
   * Add variable arguments to the end of the parameter list.
   * @return False if this is called after var args are added.
   */
  public boolean addVarArgs(JSType type) {
    if (hasVarArgs()) {
      return false;
    }

    // There are two types of variable argument functions:
    // 1) Programmer-defined var args
    // 2) Native bottom types that can accept any argument.
    // For the first one, "undefined" is a valid value for all arguments.
    // For the second, we do not want to cast it up to undefined.
    if (!type.isEmptyType()) {
      type = registry.createOptionalType(type);
    }
    newParameter(type).setVarArgs(true);
    return true;
  }

  /**
   * Copies the parameter specification from the given node.
   */
  public Node newParameterFromNode(Node n) {
    Node newParam = newParameter(n.getJSType());
    newParam.setVarArgs(n.isVarArgs());
    newParam.setOptionalArg(n.isOptionalArg());
    return newParam;
  }

  /**
   * Copies the parameter specification from the given node,
   * but makes sure it's optional.
   */
  public Node newOptionalParameterFromNode(Node n) {
    Node newParam = newParameterFromNode(n);
    if (!newParam.isVarArgs() && !newParam.isOptionalArg()) {
      newParam.setOptionalArg(true);
    }
    return newParam;
  }

  // Add a parameter to the list with the given type.
  private Node newParameter(JSType type) {
    Node paramNode = Node.newString(Token.NAME, "");
    paramNode.setJSType(type);
    root.addChildToBack(paramNode);
    return paramNode;
  }

  public Node build() {
    return root;
  }

  private boolean hasOptionalOrVarArgs() {
    Node lastChild = root.getLastChild();
    return lastChild != null &&
        (lastChild.isOptionalArg() || lastChild.isVarArgs());
  }

  public boolean hasVarArgs() {
    Node lastChild = root.getLastChild();
    return lastChild != null && lastChild.isVarArgs();
  }
}
