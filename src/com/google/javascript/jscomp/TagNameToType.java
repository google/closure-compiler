/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Contains a mapping from HTML Element tag name to the javascript type of that element at runtime.
 */
class TagNameToType {
  static Map<String, String> getMap() {
      return new ImmutableMap.Builder<String, String>()
          .put("a", "HTMLAnchorElement")
          .put("area", "HTMLAreaElement")
          .put("audio", "HTMLAudioElement")
          .put("base", "HTMLBaseElement")
          .put("body", "HTMLBodyElement")
          .put("br", "HTMLBRElement")
          .put("button", "HTMLButtonElement")
          .put("canvas", "HTMLCanvasElement")
          .put("caption", "HTMLTableCaptionElement")
          .put("col", "HTMLTableColElement")
          .put("content", "HTMLContentElement")
          .put("data", "HTMLDataElement")
          .put("datalist", "HTMLDataListElement")
          .put("del", "HTMLModElement")
          .put("dir", "HTMLDirectoryElement")
          .put("div", "HTMLDivElement")
          .put("dl", "HTMLDListElement")
          .put("embed", "HTMLEmbedElement")
          .put("fieldset", "HTMLFieldSetElement")
          .put("font", "HTMLFontElement")
          .put("form", "HTMLFormElement")
          .put("frame", "HTMLFrameElement")
          .put("frameset", "HTMLFrameSetElement")
          .put("h1", "HTMLHeadingElement")
          .put("head", "HTMLHeadElement")
          .put("hr", "HTMLHRElement")
          .put("html", "HTMLHtmlElement")
          .put("iframe", "HTMLIFrameElement")
          .put("img", "HTMLImageElement")
          .put("input", "HTMLInputElement")
          .put("keygen", "HTMLKeygenElement")
          .put("label", "HTMLLabelElement")
          .put("legend", "HTMLLegendElement")
          .put("li", "HTMLLIElement")
          .put("link", "HTMLLinkElement")
          .put("map", "HTMLMapElement")
          .put("marquee", "HTMLMarqueeElement")
          .put("menu", "HTMLMenuElement")
          .put("menuitem", "HTMLMenuItemElement")
          .put("meta", "HTMLMetaElement")
          .put("meter", "HTMLMeterElement")
          .put("object", "HTMLObjectElement")
          .put("ol", "HTMLOListElement")
          .put("optgroup", "HTMLOptGroupElement")
          .put("option", "HTMLOptionElement")
          .put("output", "HTMLOutputElement")
          .put("p", "HTMLParagraphElement")
          .put("param", "HTMLParamElement")
          .put("pre", "HTMLPreElement")
          .put("progress", "HTMLProgressElement")
          .put("q", "HTMLQuoteElement")
          .put("script", "HTMLScriptElement")
          .put("select", "HTMLSelectElement")
          .put("shadow", "HTMLShadowElement")
          .put("source", "HTMLSourceElement")
          .put("span", "HTMLSpanElement")
          .put("style", "HTMLStyleElement")
          .put("table", "HTMLTableElement")
          .put("tbody", "HTMLTableSectionElement")
          .put("template", "HTMLTemplateElement")
          .put("textarea", "HTMLTextAreaElement")
          .put("thead", "HTMLTableSectionElement")
          .put("time", "HTMLTimeElement")
          .put("title", "HTMLTitleElement")
          .put("tr", "HTMLTableRowElement")
          .put("track", "HTMLTrackElement")
          .put("ul", "HTMLUListElement")
          .put("video", "HTMLVideoElement")
          .build();
  }
}
