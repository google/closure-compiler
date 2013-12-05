/*
 * Copyright 2013 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.fuzzing;

import com.google.common.base.Joiner;

import java.util.Random;

/**
 * Generates strings and numbers to be used in, for example, string literals,
 * variable names
 * UNDER DEVELOPMENT. DO NOT USE!
 * @author zplin@google.com (Zhongpeng Lin)
 */
public class StringNumberGenerator {
  private static final int LARGEST_NUMBER = 1000;
  private Random random;
  private int counter = 0;
  private static final String[] ADJECTIVES = {
      "Amazing",
      "Breezy",
      "Cheerful",
      "Dapper",
      "Edgy",
      "Feisty",
      "Gutsy",
      "Hardy",
      "Intrepid",
      "Jaunty",
      "Karmic",
      "Lucid",
      "Maverick",
      "Natty",
      "Oneiric",
      "Precise",
      "Quantal",
      "Raring",
      "Saucy",
      "Tranquil",
      "Ubiquitous",
      "Vigilant",
      "Wild",
      "Xenial",
      "Yelping",
      "Zealous"
      };
  private static final String[] ANIMALS = {
      "Ape",
      "Badger",
      "Chipmunk",
      "Drake",
      "Eft",
      "Fawn",
      "Gibbon",
      "Heron",
      "Ibex",
      "Jackalope",
      "Koala",
      "Lynx",
      "Meerkat",
      "Narwhal",
      "Ocelot",
      "Pangolin",
      "Quetzal",
      "Ringtail",
      "Salamander",
      "Turtle",
      "Unicorn",
      "Vixen",
      "Wolf",
      "Xerus",
      "Yeti",
      "Zebra"
      };

  public StringNumberGenerator(Random random) {
    this.random = random;
  }

  public String getString() {
    Joiner joiner = Joiner.on(' ');
    return joiner.join(getCombination());
  }

  public String getPropertyName() {
    Joiner joiner = Joiner.on("");
    String[] combination = getCombination();
    return joiner.join(combination[0].toLowerCase(), combination[1]);
  }

  public int getNextNumber() {
    return counter++;
  }

  public int getRandomNumber() {
    return random.nextInt(LARGEST_NUMBER);
  }

  private String[] getCombination() {
    String[] result = new String[2];
    result[0] = ADJECTIVES[random.nextInt(ADJECTIVES.length)];
    result[1] = ANIMALS[random.nextInt(ANIMALS.length)];
    return result;
  }
}
