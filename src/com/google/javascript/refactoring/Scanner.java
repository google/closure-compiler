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

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.Node;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * Class that developers should implement to perform a JsFlume refactoring.
 *
 * @author mknichel@google.com (Mark Knichel)
 */
public abstract class Scanner implements Serializable {

  /**
   * Returns true if the given node and node traversal should match for this
   * particular scanner. Typically this function uses the {@link Matcher} class
   * or predefined matchers from {@link Matchers} to match against the Node and
   * NodeMetadata.
   *
   * If this function returns true, a {@link Match} for this node will be passed
   * to {@link #processMatch(Match)} and all matches will be passed to
   * {@link #processAllMatches(Collection)} at the end of the traversal.
   */
  public abstract boolean matches(Node node, NodeMetadata t);

  /**
   * Processes one {@link Match} at a time. There is no order guaranteed for
   * when this function will be called with the Match.
   * @param match The {@link Match} from the node and traversal for any match
   *     that {@link #matches} returned true for.
   * @return List of {@link SuggestedFix} classes that will be applied to the
   *     source files at the end of the run to create the refactoring CL.
   */
  public List<SuggestedFix> processMatch(Match match) {
    return ImmutableList.of();
  }

  /**
   * Processes every given match at one time. This function can be used when
   * the refactoring needs the information from the entire run to perform the
   * refactoring, such as moving functions around.
   * @param matches All the {@link Match} matches that were collected when the
   *     {@link #matches} function returned true.
   * @return List of {@link SuggestedFix} classes that will be applied to the
   *     source files at the end of the run to create the refactoring CL.
   */
  public List<SuggestedFix> processAllMatches(Collection<Match> matches) {
    return ImmutableList.of();
  }
}
