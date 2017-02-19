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
'require es6/promise';
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
      	  // TODO: Make it work in serviceworkers
      	  var parser = document.createElement('a');
      	  parser.href = request.url;
      	  xhr.withCredentials = (location.host === parser.host);
      	  break;

      	case 'include':
      	  xhr.withCredentials = true;
      	  break;

      	default:
      	  xhr.withCredentials = false;
      	  break;
      }

      request.headers.forEach(function(value, key) {
        xhr.setRequestHeader(key, value);
      });

      xhr.onload = function() {
        var body = 'response' in xhr ? xhr.response : xhr.responseText;
        
        var response = new Response(body, {
          status: xhr.status,
          statusText: xhr.statusText,
//        headers: ''
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
