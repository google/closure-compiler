/*
 * Copyright 2011 The Closure Compiler Authors.
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
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * A pass that looks for assignments to properties of an object or array
 * immediately following its creation using the abbreviated syntax.
 * <p>
 * E.g. {@code var a = [];a[0] = 0} is optimized to {@code var a = [0]} and
 * similarly for the object constructor.
 *
 */
public class PeepholeCollectPropertyAssignments
    extends AbstractPeepholeOptimization {

  @Override
  Node optimizeSubtree(Node subtree) {
    if (!subtree.isScript() && !subtree.isBlock()) {
      return subtree;
    }

    boolean codeChanged = false;

    // Look for variable declarations or simple assignments
    // and start processing there.
    for (Node child = subtree.getFirstChild();
         child != null; child = child.getNext()) {
      if (!child.isVar() && !NodeUtil.isExprAssign(child)) {
        continue;
      }
      if (!isPropertyAssignmentToName(child.getNext())) {
        // Quick check to see if there's anything to collapse.
        continue;
      }

      Preconditions.checkState(child.hasOneChild());
      Node name = getName(child);
      if (!name.isName()) {
        // The assignment target is not a simple name.
        continue;
      }
      Node value = getValue(child);
      if (value == null || !isInterestingValue(value)) {
        // No initializer or not an Object or Array literal.
        continue;
      }

      Node propertyCandidate;
      while ((propertyCandidate = child.getNext()) != null) {
        // This does not infinitely loop because collectProperty always
        // removes propertyCandidate from its parent when it returns true.
        if (!collectProperty(propertyCandidate, name.getString(), value)) {
          break;
        }
        codeChanged = true;
      }
    }

    if (codeChanged) {
      reportCodeChange();
    }
    return subtree;
  }

  private Node getName(Node n) {
    if (n.isVar()) {
      return n.getFirstChild();
    } else if (NodeUtil.isExprAssign(n)) {
      return n.getFirstChild().getFirstChild();
    }
    throw new IllegalStateException();
  }

  private Node getValue(Node n) {
    if (n.isVar()) {
      return n.getFirstChild().getFirstChild();
    } else if (NodeUtil.isExprAssign(n)) {
      return n.getFirstChild().getLastChild();
    }
    throw new IllegalStateException();
  }

  boolean isInterestingValue(Node n) {
    return n.isObjectLit() || n.isArrayLit();
  }

  private boolean isPropertyAssignmentToName(Node propertyCandidate) {
    if (propertyCandidate == null) { return false; }
    // Must be an assignment...
    if (!NodeUtil.isExprAssign(propertyCandidate)) {
      return false;
    }

    Node expr = propertyCandidate.getFirstChild();

    // to a property...
    Node lhs = expr.getFirstChild();
    if (!NodeUtil.isGet(lhs)) {
      return false;
    }

    // of a variable.
    Node obj = lhs.getFirstChild();
    if (!obj.isName()) {
      return false;
    }

    return true;
  }

  private boolean collectProperty(
      Node propertyCandidate, String name, Node value) {
    if (!isPropertyAssignmentToName(propertyCandidate)) {
      return false;
    }

    Node lhs = propertyCandidate.getFirstChild().getFirstChild();
    // Must be an assignment to the recent variable...
    if (!name.equals(lhs.getFirstChild().getString())) {
      return false;
    }

    Node rhs = lhs.getNext();
    // with a value that cannot change the values of the variables,
    if (mayHaveSideEffects(rhs)
        || NodeUtil.canBeSideEffected(rhs)) {
      return false;
    }
    // and does not have a reference to a variable initialized after it.
    if (!NodeUtil.isLiteralValue(rhs, true)
        && mightContainForwardReference(rhs, name)) {
      return false;
    }

    switch (value.getType()) {
      case Token.ARRAYLIT:
        if (!collectArrayProperty(value, propertyCandidate)) {
          return false;
        }
        break;
      case Token.OBJECTLIT:
        if (!collectObjectProperty(value, propertyCandidate)) {
          return false;
        }
        break;
      default:
        throw new IllegalStateException();
    }
    return true;
  }


  private boolean collectArrayProperty(
      Node arrayLiteral, Node propertyCandidate) {
    Node assignment = propertyCandidate.getFirstChild();
    final int sizeOfArrayAtStart = arrayLiteral.getChildCount();
    int maxIndexAssigned = sizeOfArrayAtStart - 1;

    Node lhs = assignment.getFirstChild();
    Node rhs = lhs.getNext();
    if (!lhs.isGetElem()) {
      return false;
    }
    Node obj = lhs.getFirstChild();
    Node property = obj.getNext();
    // The left hand side must have a numeric index
    if (!property.isNumber()) {
      return false;
    }
    // that is a valid array index
    double dindex = property.getDouble();
    if (!(dindex >= 0)  // Handles NaN and negatives.
        || Double.isInfinite(dindex) || dindex > 0x7fffffffL) {
      return false;
    }
    int index = (int) dindex;
    if (dindex != index) {
      return false;
    }
    // that would not make the array so sparse that they take more space
    // when rendered than x[9]=1.
    if (maxIndexAssigned + 4 < index) {
      return false;
    }
    if (index > maxIndexAssigned) {
      while (maxIndexAssigned < index - 1) {
        // Pad the array if it is sparse.
        // So if array is [0] and integer 3 is assigned at index is 2, then
        // we want to produce [0,,2].
        Node emptyNode = IR.empty().srcref(arrayLiteral);
        arrayLiteral.addChildToBack(emptyNode);
        ++maxIndexAssigned;
      }
      arrayLiteral.addChildToBack(rhs.detachFromParent());
    } else {
      // An out of order assignment.  Allow it if it's a hole.
      Node currentValue = arrayLiteral.getChildAtIndex(index);
      if (!currentValue.isEmpty()) {
        // We've already collected a value for this index.
        return false;
      }
      arrayLiteral.replaceChild(currentValue, rhs.detachFromParent());
    }

    propertyCandidate.detachFromParent();
    return true;
  }

  private boolean collectObjectProperty(
      Node objectLiteral, Node propertyCandidate) {
    Node assignment = propertyCandidate.getFirstChild();
    Node lhs = assignment.getFirstChild(), rhs = lhs.getNext();
    Node obj = lhs.getFirstChild();
    Node property = obj.getNext();

    // The property must be statically known.
    if (lhs.isGetElem()
        && (!property.isString()
            && !property.isNumber())) {
      return false;
    }

    String propertyName;
    if (property.isNumber()) {
      propertyName = NodeUtil.getStringValue(property);
    } else {
      propertyName = property.getString();
    }

    Node newProperty = IR.stringKey(propertyName)
        .copyInformationFrom(property);
    // Preserve the quotedness of a property reference
    if (lhs.isGetElem()) {
      newProperty.setQuotedString();
    }
    Node newValue = rhs.detachFromParent();
    newProperty.addChildToBack(newValue);
    objectLiteral.addChildToBack(newProperty);

    propertyCandidate.detachFromParent();
    return true;
  }


  private static boolean mightContainForwardReference(
      Node node, String varName) {
    if (node.isName()) {
      return varName.equals(node.getString());
    }
    for (Node child = node.getFirstChild(); child != null;
         child = child.getNext()) {
      if (mightContainForwardReference(child, varName)) {
        return true;
      }
    }
    return false;
  }

}
