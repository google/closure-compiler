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

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.TextResource;

/**
 * GWT compatible replacement for {@code ResourceLoader}
 *
 */
public final class ResourceLoader {
  static interface Libraries extends ClientBundle {
    Libraries INSTANCE = GWT.create(Libraries.class);

    @Source("externs/es3.js")
    TextResource es3();
    
    @Source("externs/es5.js")
    TextResource es5();
    
    @Source("externs/es6.js")
    TextResource es6();

    @Source("externs/es6_collections.js")
    TextResource es6Collections();

    @Source("externs/browser/intl.js")
    TextResource intl();
    
    @Source("externs/browser/w3c_event.js")
    TextResource w3cEvent();
    
    @Source("externs/browser/w3c_event3.js")
    TextResource w3cEvent3();
    
    @Source("externs/browser/gecko_event.js")
    TextResource geckoEvent();
    
    @Source("externs/browser/ie_event.js")
    TextResource ieEvent();
    
    @Source("externs/browser/webkit_event.js")
    TextResource webkitEvent();
    
    @Source("externs/browser/w3c_device_sensor_event.js")
    TextResource w3cDeviceSensorEvent();
    
    @Source("externs/browser/w3c_dom1.js")
    TextResource w3cDom1();
    
    @Source("externs/browser/w3c_dom2.js")
    TextResource w3cDom2();
    
    @Source("externs/browser/w3c_dom3.js")
    TextResource w3cDom3();

    @Source("externs/browser/gecko_dom.js")
    TextResource geckoDom();
    
    @Source("externs/browser/ie_dom.js")
    TextResource ieDom();
    
    @Source("externs/browser/webkit_dom.js")
    TextResource webkitDom();
    
    @Source("externs/browser/w3c_css.js")
    TextResource w3cCss();
    
    @Source("externs/browser/gecko_css.js")
    TextResource geckoCss();
    
    @Source("externs/browser/ie_css.js")
    TextResource ieCss();
    
    @Source("externs/browser/webkit_css.js")
    TextResource webkitCss();
    
    @Source("externs/browser/w3c_touch_event.js")
    TextResource w3cTouchEvent();
    
    @Source("externs/browser/fileapi.js")
    TextResource fileapi();
    
    @Source("externs/browser/html5.js")
    TextResource html5();
    
    @Source("externs/browser/page_visibility.js")
    TextResource pageVisibility();
    
    @Source("externs/browser/w3c_batterystatus.js")
    TextResource w3cBatterystatus();
    
    @Source("externs/browser/w3c_range.js")
    TextResource w3cRange();
    
    @Source("externs/browser/w3c_xml.js")
    TextResource w3cXml();
    
    @Source("js/base.js")
    TextResource base();

    @Source("js/es6_runtime.js")
    TextResource es6Runtime();

    @Source("js/runtime_type_check.js")
    TextResource runtimeTypeCheck();
  }

  public static String loadTextResource(Class<?> clazz, String path) {
    switch (path) {
      case "externs/es3.js":
        return Libraries.INSTANCE.es3().getText();
      case "externs/es5.js":
        return Libraries.INSTANCE.es5().getText();
      case "externs/es6.js":
        return Libraries.INSTANCE.es6().getText();
      case "externs/es6_collections.js":
        return Libraries.INSTANCE.es6Collections().getText();
      case "externs/browser/intl.js":
        return Libraries.INSTANCE.intl().getText();
      case "externs/browser/w3c_event.js":
        return Libraries.INSTANCE.w3cEvent().getText();
      case "externs/browser/w3c_event3.js":
        return Libraries.INSTANCE.w3cEvent3().getText();
      case "externs/browser/gecko_event.js":
        return Libraries.INSTANCE.geckoEvent().getText();
      case "externs/browser/ie_event.js":
        return Libraries.INSTANCE.ieEvent().getText();
      case "externs/browser/webkit_event.js":
        return Libraries.INSTANCE.webkitEvent().getText();
      case "externs/browser/w3c_device_sensor_event.js":
        return Libraries.INSTANCE.w3cDeviceSensorEvent().getText();
      case "externs/browser/w3c_dom1.js":
        return Libraries.INSTANCE.w3cDom1().getText();
      case "externs/browser/w3c_dom2.js":
        return Libraries.INSTANCE.w3cDom2().getText();
      case "externs/browser/w3c_dom3.js":
        return Libraries.INSTANCE.w3cDom3().getText();
      case "externs/browser/gecko_dom.js":
        return Libraries.INSTANCE.geckoDom().getText();
      case "externs/browser/ie_dom.js":
        return Libraries.INSTANCE.ieDom().getText();
      case "externs/browser/webkit_dom.js":
        return Libraries.INSTANCE.webkitDom().getText();
      case "externs/browser/w3c_css.js":
        return Libraries.INSTANCE.w3cCss().getText();
      case "externs/browser/gecko_css.js":
        return Libraries.INSTANCE.geckoCss().getText();
      case "externs/browser/ie_css.js":
        return Libraries.INSTANCE.ieCss().getText();
      case "externs/browser/webkit_css.js":
        return Libraries.INSTANCE.webkitCss().getText();
      case "externs/browser/w3c_touch_event.js":
        return Libraries.INSTANCE.w3cTouchEvent().getText();
      case "externs/browser/fileapi.js":
        return Libraries.INSTANCE.fileapi().getText();
      case "externs/browser/html5.js":
        return Libraries.INSTANCE.html5().getText();
      case "externs/browser/page_visibility.js":
        return Libraries.INSTANCE.pageVisibility().getText();
      case "externs/browser/w3c_batterystatus.js":
        return Libraries.INSTANCE.w3cBatterystatus().getText();
      case "externs/browser/w3c_range.js":
        return Libraries.INSTANCE.w3cRange().getText();
      case "externs/browser/w3c_xml.js":
        return Libraries.INSTANCE.w3cXml().getText();
      case "js/base.js":
        return Libraries.INSTANCE.base().getText();
      case "js/es6_runtime.js":
        return Libraries.INSTANCE.es6Runtime().getText();
      case "js/runtime_type_check.js":
        return Libraries.INSTANCE.runtimeTypeCheck().getText();
      default:
        throw new RuntimeException("Resource not found " + path);
    }
  }

  static boolean resourceExists(Class<?> clazz, String path) {
    return true; // GWT compilation would have failed otherwise
  }
}
