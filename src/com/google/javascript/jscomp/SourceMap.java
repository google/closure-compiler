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
import com.google.javascript.jscomp.sourcemap.SourceMapGenerator;
import com.google.javascript.jscomp.sourcemap.SourceMapGeneratorV1;
import com.google.javascript.jscomp.sourcemap.SourceMapGeneratorV2;
import com.google.javascript.jscomp.sourcemap.Position;
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
public class SourceMap implements SourceMapGenerator {

  public static enum Format {
     LEGACY {
       @Override SourceMap getInstance() {
         return new SourceMap(new SourceMapGeneratorV1());
       }
     },
     EXPERIMENTIAL {
       @Override SourceMap getInstance() {
         return new SourceMap(new SourceMapGeneratorV2());
       }
     };
     abstract SourceMap getInstance();
  }

  /**
   * Source maps can be very large different levels of detail can be specified.
   */
  public static enum DetailLevel implements Predicate<Node> {
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

  final SourceMapGenerator generator;

  private SourceMap(SourceMapGenerator generator) {
    this.generator = generator;
  }

  @Override
  public void addMapping(
      Node node, Position startPosition, Position endPosition) {
    generator.addMapping(node, startPosition, endPosition);
  }

  @Override
  public void appendTo(Appendable out, String name) throws IOException {
    generator.appendTo(out, name);
  }

  @Override
  public void reset() {
    generator.reset();
  }

  @Override
  public void setStartingPosition(int offsetLine, int offsetIndex) {
    generator.setStartingPosition(offsetLine, offsetIndex);
  }

  @Override
  public void setWrapperPrefix(String prefix) {
    generator.setWrapperPrefix(prefix);
  }


  public void validate(boolean validate) {
    generator.validate(validate);
  }
}
