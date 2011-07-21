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

import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Class which represents JsonML element according to the specification at
 * "http://code.google.com/p/es-lab/wiki/JsonMLASTFormat"
 *
 * @author dhans@google.com (Daniel Hans)
 */
public class JsonML {
  private final TagType type;
  private Map<TagAttr, Object> attributes =
      new EnumMap<TagAttr, Object>(TagAttr.class);
  private List<JsonML> children = new ArrayList<JsonML>();

  /**
   * Creates a new element with a given type.
   * @param type
   */
  public JsonML(TagType type) {
    this.type = type;
  }

  /**
   * Creates a new element.
   * @param type type of the element
   * @param children children to append to the element
   */
  public JsonML(TagType type, JsonML... children) {
    this(type, Arrays.asList(children));
  }

  public JsonML(TagType type, List<? extends JsonML> children) {
    this(type, Collections.<TagAttr, Object>emptyMap(), children);
  }

  public JsonML(TagType type, Map<? extends TagAttr, ?> attributes) {
    this(type, attributes, Collections.<JsonML>emptyList());
  }

  public JsonML(TagType type, Map<? extends TagAttr, ?> attributes,
      List<? extends JsonML> children) {
    this.type = type;
    this.attributes.putAll(attributes);
    appendChildren(children);
  }

  /**
   * Inserts the given JsonML element at the given position in the
   * list of children.
   * @param index index at which the given element is to be inserted
   * @param element JsonML element to be inserted
   */
  public void addChild(int index, JsonML element) {
    children.add(index, element);
  }

  /**
   * Appends a given child element to the list of children.
   * @param element JsonML element to append
   */
  public void appendChild(JsonML element) {
    children.add(element);
  }

  /**
   * Appends a collection of children to the back of the list of children.
   * @param elements collection of JsonML elements to append
   */
  public void appendChildren(Collection<? extends JsonML> elements) {
    children.addAll(elements);
  }

  /**
   * Returns number of the children.
   */
  public int childrenSize() {
    return children.size();
  }

  /**
   * Removes all elements from the list of children.
   */
  public void clearChildren() {
    setChildren();
  }

  /**
   * Returns value associated with a given attribute.
   * @param name name of the attribute
   * @return associated value or null if the attribute is not present
   */
  public Object getAttribute(TagAttr name) {
    return attributes.get(name);
  }

  /**
   * Returns a map with attributes and respective values.
   */
  public Map<TagAttr, Object> getAttributes() {
    return attributes;
  }

  /**
   * Returns child at a given position.
   */
  public JsonML getChild(int index) {
    return children.get(index);
  }

  /**
   * Returns a list of all children.
   */
  public List<JsonML> getChildren() {
    return children;
  }

  /**
   * Returns the portion of children list between the specified
   * fromIndex, inclusive, and toIndex, exclusive.
   * @param fromIndex low endpoint (inclusive)
   * @param toIndex high endpoint (exclusive)
   */
  public List<JsonML> getChildren(int fromIndex, int toIndex) {
    return children.subList(fromIndex, toIndex);
  }

  /**
   * Returns type of the JsonML element.
   */
  public TagType getType() {
    return type;
  }

  /**
   * Returns true if the JsonML element has at least one child.
   */
  public boolean hasChildren() {
    return !children.isEmpty();
  }

  /**
   * Sets value for a given attribute.
   * @param name name of the attribute
   * @param value value to associate with the attribute
   */
  public void setAttribute(TagAttr name, Object value) {
    attributes.put(name, value);
  }

  /**
   * Sets attributes of the JsonML element.
   * @param attributes map with attributes and their values
   */
  public void setAttributes(Map<TagAttr, Object> attributes) {
    this.attributes = attributes;
  }

  /**
   * Replaces the element at the given position in the list of children wit
   * the given JsonML element.
   * @param index index of element to replace
   * @param element JsonML element to append
   */
  public void setChild(int index, JsonML element) {
    children.set(index, element);
  }

  /**
   * Replaces all elements in the list of children with the given
   * JsonML elements.
   * @param children a comma separated list of JsonML elements
   */
  public void setChildren(JsonML... children) {
    this.children.clear();
      this.children.addAll(Arrays.asList(children));
  }

  /**
   * Replaces all elements in the list of children with the given
   * list of JsonML elements..
   * @param children a list of JsonML elements.
   */
  public void setChildren(List<JsonML> children) {
    this.children = children;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    toString(sb, true, true);
    return sb.toString();
  }

  private void toString(StringBuilder sb, boolean printAttributes,
      boolean printChildren) {
    sb.append("[\"");
    escapeStringOnto(type.name(), sb);
    sb.append('"');

    if (printAttributes) {
      sb.append(", {");
      boolean first = true;
      for (Entry<TagAttr, Object> entry : attributes.entrySet()) {
        if (first) {
          first = false;
        } else {
          sb.append(", ");
        }
        sb.append('"');
        escapeStringOnto(entry.getKey().toString(), sb);
        sb.append("\": ");
        Object value = entry.getValue();
        if (value == null) {
          sb.append("null");
        } else if (value instanceof String) {
          sb.append('"');
          escapeStringOnto((String) value, sb);
          sb.append('"');
        } else {
          sb.append(value);
        }
      }
      sb.append("}");
    }

    if (printChildren) {
      for (JsonML child : children) {
        sb.append(", ");
        sb.append(child.toString());
      }
    }
    sb.append(']');
  }


  /**
   * Encodes the specified string and appends it to the given StringBuilder.
   */
  private static void escapeStringOnto(String s, StringBuilder sb) {
    int pos = 0, n = s.length();
    for (int i = 0; i < n; ++i) {
      char ch = s.charAt(i);
      switch (ch) {
        case '\r': case '\n': case '"': case '\\':
        // these two characters are the exceptions to the general rule
        // that JSON is a syntactic subset of JavaScript
        // From JSON's perspective they are considered to be whitespaces,
        // while ES5 specifies them as line terminators.
        case '\u2028': case '\u2029':
          String hex = Integer.toString(ch, 16);
          sb.append(s, pos, i)
              .append("\\u").append("0000", hex.length(), 4).append(hex);
          pos = i + 1;
          break;
      }
    }
    sb.append(s, pos, n);
  }

  /**
   * Prints a JsonML tree in a human readable format.
   */
  public String toStringTree() {
    try {
      StringBuilder s = new StringBuilder();
      toStringTreeHelper(this, 0, s);
      return s.toString();
    } catch (IOException e) {
      throw new RuntimeException("Should not happen\n" + e);
    }
  }

  private static void toStringTreeHelper(JsonML element, int level,
      StringBuilder sb) throws IOException {
    for (int i = 0; i < level; ++i) {
      sb.append("    ");
    }
    element.toString(sb, true, false);
    sb.append("\n");
    for (JsonML child : element.getChildren()) {
      toStringTreeHelper(child, level + 1, sb);
    }
  }
}

