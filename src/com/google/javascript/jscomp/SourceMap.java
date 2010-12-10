/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.common.base.Predicate;
import com.google.javascript.rhino.Node;

import java.io.IOException;

/**
 * Collects information mapping the generated (compiled) source back to
 * its original source for debugging purposes.
 *
 * @see CodeConsumer
 * @see CodeGenerator
 * @see CodePrinter
 *
 */
public interface SourceMap {

  enum Format {
     LEGACY {
       @Override SourceMap getInstance() {
         return new SourceMapLegacy();
       }
     },
     EXPERIMENTIAL {
       @Override SourceMap getInstance() {
         return new SourceMap2();
       }
     };
     abstract SourceMap getInstance();
  }

  /**
   * Source maps can be very large different levels of detail can be specified.
   */
  public enum DetailLevel implements Predicate<Node> {
    // ALL is best when the fullest details are needed for debugging or for
    // code-origin analysis.
    ALL {
      @Override public boolean apply(Node node) {
        return true;
      }
    },
    // SYMBOLS is intended to be used for stack trace deobfuscation when full
    // detail is not needed.
    SYMBOLS {
      @Override public boolean apply(Node node) {
        return NodeUtil.isCall(node)
            || NodeUtil.isNew(node)
            || NodeUtil.isFunction(node)
            || NodeUtil.isName(node)
            || NodeUtil.isGet(node)
            || NodeUtil.isObjectLitKey(node, node.getParent())
            || (NodeUtil.isString(node) && NodeUtil.isGet(node.getParent()));
      }
    };
  }

  /**
   * Appends the source map to the given buffer.
   *
   * @param out The stream to which the map will be appended.
   * @param name The name of the generated source file that this source map
   *   represents.
   */
  void appendTo(Appendable out, String name) throws IOException;

  /**
   * Resets the source map for reuse. A reset needs to be called between
   * each generated output file.
   */
  void reset();

  /**
   * Adds a mapping for the given node.  Mappings must be added in order.
   *
   * @param node The node that the new mapping represents.
   * @param startPosition The position on the starting line
   * @param endPosition The position on the ending line.
   */
  void addMapping(Node node, Position startPosition, Position endPosition);

  /**
   * Sets the prefix used for wrapping the generated source file before
   * it is written. This ensures that the source map is adjusted for the
   * change in character offsets.
   *
   * @param prefix The prefix that is added before the generated source code.
   */
  void setWrapperPrefix(String prefix);

  /**
   * Sets the source code that exists in the buffer for which the
   * generated code is being generated. This ensures that the source map
   * accurately reflects the fact that the source is being appended to
   * an existing buffer and as such, does not start at line 0, position 0
   * but rather some other line and position.
   *
   * @param offsetLine The index of the current line being printed.
   * @param offsetIndex The column index of the current character being printed.
   */
  void setStartingPosition(int offsetLine, int offsetIndex);

}
