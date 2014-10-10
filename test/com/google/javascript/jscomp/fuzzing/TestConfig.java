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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
class TestConfig {
  static JsonObject getConfig() {
    try {
      String json =
          "{\n" +
          "  \"script\": {\n" +
          "    \"maxLength\": 0.05\n" +
          "  },\n" +
          "  \"sourceElement\": {\n" +
          "    \"weights\": {\n" +
          "      \"statement\": 7,\n" +
          "      \"function\": 3\n" +
          "    }\n" +
          "  },\n" +
          "  \"function\": {\n" +
          "    \"maxParams\": 5,\n" +
          "    \"maxLength\": 0.1\n" +
          "  },\n" +
          "\n" +
          "  \"statement\": {\n" +
          "    \"weights\": {\n" +
          "      \"block\": 1,\n" +
          "      \"var\": 1,\n" +
          "      \"empty\": 1,\n" +
          "      \"exprStmt\": 1,\n" +
          "      \"if\": 1,\n" +
          "      \"while\": 1,\n" +
          "      \"doWhile\": 1,\n" +
          "      \"for\": 1,\n" +
          "      \"forIn\": 1,\n" +
          "      \"continue\": 1,\n" +
          "      \"break\": 1,\n" +
          "      \"return\": 1,\n" +
          "      \"switch\": 1,\n" +
          "      \"label\": 1,\n" +
          "      \"throw\": 1,\n" +
          "      \"try\": 1\n" +
          "    }\n" +
          "  },\n" +
          "  \"block\": {\n" +
          "    \"maxLength\": 0.1\n" +
          "  },\n" +
          "  \"if\": {\n" +
          "    \"hasElse\": 0.5\n" +
          "  },\n" +
          "  \"for\": {\n" +
          "    \"headBudget\": 0.3\n" +
          "  },\n" +
          "  \"forInitializer\": {\n" +
          "    \"weights\": {\n" +
          "      \"var\": 1,\n" +
          "      \"expression\": 1\n" +
          "    }\n" +
          "  },\n" +
          "  \"forInItem\": {\n" +
          "    \"weights\": {\n" +
          "      \"assignableExpr\": 1,\n" +
          "      \"var\": 1\n" +
          "    }\n" +
          "  },\n" +
          "  \"continue\": {\n" +
          "    \"toLabel\": 0.5\n" +
          "  },\n" +
          "  \"break\": {\n" +
          "    \"toLabel\": 0.5\n" +
          "  },\n" +
          "  \"return\": {\n" +
          "    \"hasValue\": 0.5\n" +
          "  },\n" +
          "  \"case\": {\n" +
          "    \"valueBudget\": 0.1\n" +
          "  },\n" +
          "  \"expression\": {\n" +
          "    \"weights\": {\n" +
          "        \"this\": 1,\n" +
          "        \"existingIdentifier\": 1,\n" +
          "        \"literal\": 1,\n" +
          "        \"functionCall\": 1,\n" +
          "        \"unaryExpr\": 1,\n" +
          "        \"binaryExpr\": 1,\n" +
          "        \"function\": 1,\n" +
          "        \"ternaryExpr\": 1\n" +
          "    }\n" +
          "  },\n" +
          "  \"literal\": {\n" +
          "    \"weights\": {\n" +
          "        \"null\": 1,\n" +
          "        \"undefined\": 1,\n" +
          "        \"Infinity\": 1,\n" +
          "        \"NaN\": 1,\n" +
          "        \"boolean\": 1,\n" +
          "        \"numeric\": 1,\n" +
          "        \"string\": 1,\n" +
          "        \"array\": 1,\n" +
          "        \"object\": 1,\n" +
          "        \"regularExpr\": 1\n" +
          "    }\n" +
          "  },\n" +
          "  \"numeric\": {\n" +
          "    \"max\": 1000\n" +
          "  },\n" +
          "  \"identifier\": {\n" +
          "    \"shadow\": 0.1\n" +
          "  },\n" +
          "  \"object\": {\n" +
          "    \"maxLength\": 0.5\n" +
          "  },\n" +
          "  \"array\": {\n" +
          "    \"maxLength\": 1\n" +
          "  },\n" +
          "  \"assignableExpr\": {\n" +
          "    \"weights\": {\n" +
          "        \"getProp\": 1,\n" +
          "        \"getElem\": 1,\n" +
          "        \"existingIdentifier\": 1\n" +
          "    }\n" +
          "  },\n" +
          "  \"functionCall\": {\n" +
          "    \"weights\": {\n" +
          "        \"constructorCall\": 1,\n" +
          "        \"normalCall\": 1\n" +
          "    }\n" +
          "  },\n" +
          "  \"constructorCall\": {\n" +
          "    \"argLength\": 1\n" +
          "  },\n" +
          "  \"normalCall\": {\n" +
          "    \"argLength\": 1\n" +
          "  },\n" +
          "  \"callableExpr\": {\n" +
          "    \"weights\": {\n" +
          "        \"getProp\": 1,\n" +
          "        \"getElem\": 1,\n" +
          "        \"existingIdentifier\": 1,\n" +
          "        \"function\": 1\n" +
          "    }\n" +
          "  },\n" +
          "  \"unaryExpr\": {\n" +
          "    \"weights\": {\n" +
          "        \"void\": 1,\n" +
          "        \"typeof\": 1,\n" +
          "        \"pos\": 1,\n" +
          "        \"neg\": 1,\n" +
          "        \"bitNot\": 1,\n" +
          "        \"not\": 1,\n" +
          "        \"inc\": 1,\n" +
          "        \"dec\": 1,\n" +
          "        \"delProp\": 1,\n" +
          "        \"postInc\": 1,\n" +
          "        \"postDec\": 1\n" +
          "    }\n" +
          "  },\n" +
          "  \"binaryExpr\": {\n" +
          "    \"weights\": {\n" +
          "        \"mul\": 1,\n" +
          "        \"div\": 1,\n" +
          "        \"mod\": 1,\n" +
          "        \"add\": 1,\n" +
          "        \"sub\": 1,\n" +
          "        \"lsh\": 1,\n" +
          "        \"rsh\": 1,\n" +
          "        \"ursh\": 1,\n" +
          "        \"lt\": 1,\n" +
          "        \"gt\": 1,\n" +
          "        \"le\": 1,\n" +
          "        \"ge\": 1,\n" +
          "        \"instanceof\": 1,\n" +
          "        \"in\": 1,\n" +
          "        \"eq\": 1,\n" +
          "        \"ne\": 1,\n" +
          "        \"sheq\": 1,\n" +
          "        \"shne\": 1,\n" +
          "        \"bitAnd\": 1,\n" +
          "        \"bitXor\": 1,\n" +
          "        \"bitOr\": 1,\n" +
          "        \"and\": 1,\n" +
          "        \"or\": 1,\n" +
          "        \"assign\": 1,\n" +
          "        \"assignMul\": 1,\n" +
          "        \"assignDiv\": 1,\n" +
          "        \"assignMod\": 1,\n" +
          "        \"assignAdd\": 1,\n" +
          "        \"assignSub\": 1,\n" +
          "        \"assignLsh\": 1,\n" +
          "        \"assignRsh\": 1,\n" +
          "        \"assignUrsh\": 1,\n" +
          "        \"assignBitAnd\": 1,\n" +
          "        \"assignBitXor\": 1,\n" +
          "        \"assignBitOr\": 1\n" +
          "    }\n" +
          "  },\n" +
          "  \"program\": {\n" +
          "    \"maxLength\": 0.1\n" +
          "  }\n" +
          "}";
      return new Gson().fromJson(json, JsonObject.class);
    } catch (JsonParseException e) {
      e.printStackTrace();
      return null;
    }

  }
}
