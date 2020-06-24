/**
 * Taken from goog.string.regExpEscape
 * @param {string} s
 * @return {string}
 */
Pattern.quote = function(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
};
