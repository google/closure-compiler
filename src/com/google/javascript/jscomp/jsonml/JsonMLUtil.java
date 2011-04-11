/*
 * Copyright 2010 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.jsonml;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * JsonMLUtil contains utilities for the JsonML object.
 *
 * @author dhans@google.com (Daniel Hans)
 */
public class JsonMLUtil {

  /**
   * Checks if the specified JsonML element represents an expression.
   */
  public static boolean isExpression(JsonML element) {
    switch (element.getType()) {
      case ArrayExpr:
      case AssignExpr:
      case BinaryExpr:
      case CallExpr:
      case ConditionalExpr:
      case CountExpr:
      case DeleteExpr:
      case EvalExpr:
      case FunctionExpr:
      case IdExpr:
      case InvokeExpr:
      case LiteralExpr:
      case LogicalAndExpr:
      case LogicalOrExpr:
      case MemberExpr:
      case NewExpr:
      case ObjectExpr:
      case RegExpExpr:
      case ThisExpr:
      case TypeofExpr:
      case UnaryExpr:
        return true;
      default:
        return false;
    }
  }

  /**
   * Parses JSON string which contains serialized JsonML content.
   * @param jsonml string representation of JsonML
   * @return root element of a JsonML tree
   */
  public static JsonML parseString(String jsonml) throws Exception {
    return parseElement(new JSONArray(jsonml));
  }

  private static JsonML parseElement(JSONArray element)
      throws Exception {
    JsonML jsonMLElement = new JsonML(TagType.valueOf(element.getString(0)));

    // set attributes for the JsonML element
    JSONObject attrs = element.getJSONObject(1);
    Iterator<?> it = attrs.keys();
    while (it.hasNext()) {
      String key = (String) it.next();
      Object value = attrs.get(key);
      TagAttr tag = TagAttr.get(key);

      // an unsupported attribute
      if (tag == null) {
        continue;
      }

      if (value instanceof Number) {
        value = ((Number) value).doubleValue();
      }

      switch (tag) {
        case NAME:
        case BODY:
        case FLAGS:
        case OP:
        case TYPE:
        case IS_PREFIX:
        case LABEL:
          jsonMLElement.setAttribute(tag, value);
          break;
        case VALUE:
          // we do not want to deal with JSONObject.NULL
          if (value != null && value.equals(null)) {
            value = null;
          }

          // we want all numbers to be stored as double values
          if (value instanceof Number) {
            jsonMLElement.setAttribute(tag, ((Number) value).doubleValue());
          } else {
            jsonMLElement.setAttribute(tag, value);
          }
          break;
        default:
      }
    }

    // recursively set children for the JsonML element
    for (int i = 2; i < element.length(); ++i) {
      jsonMLElement.appendChild(parseElement(element.getJSONArray(i)));
    }

    return jsonMLElement;
  }

  /**
   * Compares two specified JsonML trees.
   *
   * Two JsonML nodes are considered to be equal when the following conditions
   * are met:
   *
   * - have the same type
   * - have the same attributes from the list of attributes to compare
   * - have the same number of children
   * - nodes in each pair of corresponding children are equal
   *
   * Two JsonML trees are equal, if their roots are equal.
   *
   * When two nodes are compared, only the following attributes are taken
   * into account:
   * TagAttr.BODY, TagAttr.FLAGS, TagAttr.IS_PREFIX, TagAttr.LABEL,
   * TagAttr.NAME, TagAttr.OP, TagAttr.TYPE, TagAttr.VALUE
   * Generally, the comparator does not care about debugging attributes.
   *
   * @return
   * Returns string describing the inequality in the following format:
   *
   * The trees are not equal:
   *
   * Tree1:
   * -- string representation of Tree1
   *
   * Tree2:
   * -- string representation of Tree2
   *
   * Subtree1:
   * -- string representation of the subtree of the Tree1 which is not
   * -- equal to the corresponding subtree of the Tree2
   *
   * Subtree2:
   * -- see Subtree1
   *
   * If the trees are equal, null is returned.
   */
  public static String compare(JsonML tree1, JsonML tree2) {
    return (new JsonMLComparator(tree1, tree2)).compare();
  }

  /**
   * Returns true if the trees are equal, false otherwise.
   */
  static boolean compareSilent(JsonML tree1, JsonML tree2) {
    return (new JsonMLComparator(tree1, tree2)).compareSilent();
  }

  /**
   * Helper class which actually compares two given JsonML trees.
   *
   */
  private static class JsonMLComparator {
    private static final TagAttr[] ATTRS_TO_COMPARE = {
      TagAttr.BODY, TagAttr.FLAGS, TagAttr.IS_PREFIX, TagAttr.LABEL,
      TagAttr.NAME, TagAttr.OP, TagAttr.TYPE, TagAttr.VALUE
    };
    private JsonML treeA;
    private JsonML treeB;
    private JsonML mismatchA;
    private JsonML mismatchB;

    JsonMLComparator(JsonML treeA, JsonML treeB) {
      this.treeA = treeA;
      this.treeB = treeB;
      if (compareElements(treeA, treeB)) {
        mismatchA = null;
        mismatchB = null;
      }
    }

    private boolean setMismatch(JsonML a, JsonML b) {
      mismatchA = a;
      mismatchB = b;
      return false;
    }

    /**
     * Check if two elements are equal (including comparing their children).
     */
    private boolean compareElements(JsonML a, JsonML b) {
      // the elements are considered to be equal if they are both null
      if (a == null || b == null) {
        if (a == null && b == null) {
          return true;
        } else {
          return setMismatch(a, b);
        }
      }

      // the elements themselves have to be equivalent
      if (!areEquivalent(a, b)) {
        return setMismatch(a, b);
      }

      // they both have to have the same number of children
      if (a.childrenSize() != b.childrenSize()) {
        return setMismatch(a, b);
      }

      // all the children has to be the same
      Iterator<JsonML> itA = a.getChildren().listIterator();
      Iterator<JsonML> itB = b.getChildren().listIterator();
      while (itA.hasNext()) {
        if (!compareElements(itA.next(), itB.next())) {
          return false;
        }
      }

      return true;
    }

    /**
     * Checks if two elements are semantically the same.
     */
    private boolean areEquivalent(JsonML a, JsonML b) {
      // both elements must have the same type
      if (a.getType() != b.getType()) {
        return false;
      }

      for (TagAttr attr : ATTRS_TO_COMPARE) {
        if (!compareAttribute(attr, a, b)) {
          return false;
        }
      }
      return true;
    }

    private boolean compareAttribute(TagAttr attr, JsonML a, JsonML b) {
      Object valueA = a.getAttributes().get(attr);
      Object valueB = b.getAttributes().get(attr);

      // none of the elements have the attribute
      if (valueA == null && valueB == null) {
        return true;
      }

      // only one of the elements has the attribute
      if (valueA == null || valueB == null) {
        return false;
      }

      // check if corresponding values are equal
      if (!(valueA.equals(valueB))) {
        // there is still a chance that both attributes are numbers, but are
        // represented by different classes

        Double doubleA = null, doubleB = null;

        if (valueA instanceof Number) {
          doubleA = ((Number) valueA).doubleValue();
        } else if (valueA instanceof String) {
          doubleA = Double.valueOf((String) valueA);
        } else {
          return false;
        }

        if (valueB instanceof Number) {
          doubleB = ((Number) valueB).doubleValue();
        } else if (valueB instanceof String) {
          doubleB = Double.valueOf((String) valueB);
        } else {
          return false;
        }

        if (!doubleA.equals(doubleB)) {
          return false;
        }
      }

      return true;
    }

    private boolean compareSilent() {
      return mismatchA == null && mismatchB == null;
    }

    private String compare() {
      if (compareSilent()) {
        return null;
      }
      return "The trees are not equal: " +
          "\n\nTree1:\n " + treeA.toStringTree() +
          "\n\nTree2:\n " + treeB.toStringTree() +
          "\n\nSubtree1:\n " + mismatchA.toStringTree() +
          "\n\nSubtree2:\n " + mismatchB.toStringTree();

    }
  }
}
