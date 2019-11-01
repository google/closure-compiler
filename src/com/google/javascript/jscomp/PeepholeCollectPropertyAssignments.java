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

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/**
 * A pass that looks for assignments to properties of an object or array
 * immediately following its creation using the abbreviated syntax.
 * <p>
 * E.g. {@code var a = [];a[0] = 0} is optimized to {@code var a = [0]} and
 * similarly for the object constructor.
 */
final class PeepholeCollectPropertyAssignments extends AbstractPeepholeOptimization {

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
      if (!NodeUtil.isNameDeclaration(child) && !NodeUtil.isExprAssign(child)) {
        continue;
      }
      if (!isPropertyAssignmentToName(child.getNext())) {
        // Quick check to see if there's anything to collapse.
        continue;
      }

      checkState(child.hasOneChild());
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
      reportChangeToEnclosingScope(subtree);
    }
    return subtree;
  }

  private static Node getName(Node n) {
    if (NodeUtil.isNameDeclaration(n)) {
      return n.getFirstChild();
    } else if (NodeUtil.isExprAssign(n)) {
      return n.getFirstFirstChild();
    }
    throw new IllegalStateException();
  }

  private static Node getValue(Node n) {
    if (NodeUtil.isNameDeclaration(n)) {
      return n.getFirstFirstChild();
    } else if (NodeUtil.isExprAssign(n)) {
      return n.getFirstChild().getLastChild();
    }
    throw new IllegalStateException();
  }

  static boolean isInterestingValue(Node n) {
    return n.isObjectLit() || n.isArrayLit();
  }

  private static boolean isPropertyAssignmentToName(Node propertyCandidate) {
    if (propertyCandidate == null) {
      return false;
    }
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
    return obj.isName();
  }

  private boolean collectProperty(Node propertyCandidate, String name, Node value) {
    if (!isPropertyAssignmentToName(propertyCandidate)) {
      return false;
    }

    Node lhs = propertyCandidate.getFirstFirstChild();
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

    switch (value.getToken()) {
      case ARRAYLIT:
        if (!collectArrayProperty(value, propertyCandidate)) {
          return false;
        }
        break;
      case OBJECTLIT:
        if (!collectObjectProperty(value, propertyCandidate)) {
          return false;
        }
        break;
      default:
        throw new IllegalStateException();
    }
    return true;
  }


  private static boolean collectArrayProperty(Node arrayLiteral, Node propertyCandidate) {
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
      arrayLiteral.addChildToBack(rhs.detach());
    } else {
      // An out of order assignment.  Allow it if it's a hole.
      Node currentValue = arrayLiteral.getChildAtIndex(index);
      if (!currentValue.isEmpty()) {
        // We've already collected a value for this index.
        return false;
      }
      arrayLiteral.replaceChild(currentValue, rhs.detach());
    }

    propertyCandidate.detach();
    return true;
  }

  private boolean collectObjectProperty(Node objectLiteral, Node propertyCandidate) {
    Node assignment = propertyCandidate.getFirstChild();
    Node lhs = assignment.getFirstChild();
    Node rhs = lhs.getNext();
    Node obj = lhs.getFirstChild();
    Node property = obj.getNext();

    // The property must be statically known.
    if (lhs.isGetElem() && !property.isString() && !property.isNumber()) {
      return false;
    }

    String propertyName;
    if (property.isNumber()) {
      propertyName = getSideEffectFreeStringValue(property);
    } else {
      propertyName = property.getString();
    }

    // Check if the new property already exists in the object literal
    // Note: Duplicate keys are invalid in strict mode
    Node existingProperty = null;
    for (Node currentProperty : objectLiteral.children()) {
      if (currentProperty.isStringKey() || currentProperty.isMemberFunctionDef()) {
        // Get the name of the current property
        String currentPropertyName = currentProperty.getString();
        // Get the value of the property
        Node currentValue = currentProperty.getFirstChild();
        // Compare the current property name with the new property name
        if (currentPropertyName.equals(propertyName)) {
          existingProperty = currentProperty;
          // Check if the current value and the new value are side-effect
          boolean isCurrentValueSideEffect = NodeUtil.canBeSideEffected(currentValue);
          boolean isNewValueSideEffect = NodeUtil.canBeSideEffected(rhs);
          // If they are side-effect free then replace the current value with the new one
          if (isCurrentValueSideEffect || isNewValueSideEffect) {
            return false;
          }
          // Break the loop if the property exists
          break;
        }
      } else if (currentProperty.isGetterDef() || currentProperty.isSetterDef()) {
        String currentPropertyName = currentProperty.getString();
        if (currentPropertyName.equals(propertyName)) {
          return false;
        }
      }
    }

    Node newProperty = IR.stringKey(propertyName)
        .useSourceInfoIfMissingFrom(property);
    // Preserve the quotedness of a property reference
    if (lhs.isGetElem()) {
      newProperty.setQuotedString();
    }
    Node newValue = rhs.detach();
    newProperty.addChildToBack(newValue);

    if (existingProperty != null) {
      deleteNode(existingProperty);
    }
    // If the property does not already exist we can safely add it
    objectLiteral.addChildToBack(newProperty);
    propertyCandidate.detach();
    return true;
  }


  private static boolean mightContainForwardReference(Node node, String varName) {
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
