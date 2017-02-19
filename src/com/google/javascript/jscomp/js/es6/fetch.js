/*
 * Copyright 2016 The Closure Compiler Authors.
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

'require base';
//'require es6/headers';
'require es6/promise';
//'require es6/request';
//'require es6/response';
'require util/global';
'require util/polyfill';

/**
 * Should we unconditionally override a native implementation with our own?
 * @define {boolean}
 */
$jscomp.FORCE_POLYFILL_FETCH = false;

$jscomp.polyfill('fetch', function(nativeFetch) {
  if (nativeFetch &&!$jscomp.FORCE_POLYFILL_FETCH) {
    return nativeFetch;
  }

  /**
   * @param {!string} url
   * @return {!string}
   */
  var getHost = function(url) {
    if (URL) {
      return new URL(url).host;
    }

    // Fallback for IE and other old browsers
    var parser = document.createElement('a');
    parser.href = url;
    return parser.host;
  };

  /**
   * @param {!RequestInfo} input
   * @param {!RequestInit=} opt_init
   * @return {!Promise<!Response>}
   */
  var fetchPolyfill = function(input, opt_init) {
    return new Promise(function(resolve, reject) {
      var request = new Request(input, opt_init);
      var xhr = new XMLHttpRequest();

      xhr.onerror = function() {
        reject(new TypeError("Error when attempting to fetch resource."));
      };
      xhr.ontimeout = xhr.onerror;

      switch (request.credentials) {
      	// Check if it's the same domain
      	case 'same-origin':
      	  xhr.withCredentials = (location.host === getHost(request.url));
      	  break;

      	case 'include':
      	  xhr.withCredentials = true;
      	  break;

      	default:
      	  xhr.withCredentials = false;
      	  break;
      }

      var headerIterator = request.headers.entries();
      var head;
      while ((head = headerIterator.next()) && (!head.done)) {
        var key = head.value[0];
        var value = request.headers.getAll(key).join(',');

        xhr.setRequestHeader(key, value);
      };

      xhr.onload = function() {
        var body = /** @type {string} */ ('response' in xhr ? xhr.response : xhr.responseText);
        var headers = new Headers();

        // Parse xhr-response-headers and add them to response-headers
        var re = /\s*([^\s:]*):\s*([^\n]*)\r?\n/g;
        var responseHeaders = xhr.getAllResponseHeaders();
        var head = re.exec(responseHeaders);
        while (head) {
          for (var entry in head[2].split(",")) {
            headers.set(head[1], entry);
          }
          head = re.exec(responseHeaders);
        }

        var response = new Response(body, {
          status: xhr.status,
          statusText: xhr.statusText,
          headers: headers
        });

        // This won`t do anything on a native Response,
        // but since we`re polyfilling we can assume that we`re using
        // Closure Compiler`s polyfill.
        response.url = xhr.responseURL;

        resolve(response);
      };

      request.blob().then(function(body) {
        xhr.open(request.method, request.url, true);
        xhr.send(body);
      });
    });
  };

  return fetchPolyfill;
}, 'es6-impl', 'es3');
