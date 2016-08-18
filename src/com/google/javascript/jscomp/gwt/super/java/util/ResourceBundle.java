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

package java.util;

import com.google.javascript.jscomp.GwtProperties;
import com.google.javascript.jscomp.ResourceLoader;

/**
 * GWT compatible replacement for {@code ResourceBundle} that defers to {@link ResourceLoader} for
 * specific known properties files.
 */
public class ResourceBundle {
  private static final Map<String, ResourceBundle> CACHE = new HashMap<>();

  private final GwtProperties properties;

  private ResourceBundle(GwtProperties properties) {
    this.properties = properties;
  }

  public String getString(String key) throws MissingResourceException {
    String value = properties.getProperty(key);
    if (value == null) {
      throw new MissingResourceException("no key found", null, key);
    }
    return value;
  }

  public static ResourceBundle getBundle(String baseName) {
    String resourceName;
    if ("com.google.javascript.rhino.Messages".equals(baseName)) {
      resourceName = "rhino/Messages.properties";
    } else if ("com.google.javascript.jscomp.parsing.ParserConfig".equals(baseName)) {
      resourceName = "parsing/ParserConfig.properties";
    } else {
      throw new RuntimeException("ResourceBundle not available: " + baseName);
    }

    ResourceBundle bundle = CACHE.get(resourceName);
    if (bundle == null) {
      String resource = ResourceLoader.loadTextResource(null, resourceName);
      bundle = new ResourceBundle(GwtProperties.load(resource));
      CACHE.put(resourceName, bundle);
    }
    return bundle;
  }

  public static ResourceBundle getBundle(String baseName, Locale locale) {
    return getBundle(baseName);
  }

}
