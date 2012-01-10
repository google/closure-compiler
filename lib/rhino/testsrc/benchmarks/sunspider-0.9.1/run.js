var tests = [
    "3d-cube", "3d-morph", "3d-raytrace",
    "access-binary-trees", "access-fannkuch", "access-nbody", "access-nsieve",
    "bitops-3bit-bits-in-byte", "bitops-bits-in-byte", "bitops-bitwise-and", "bitops-nsieve-bits",
    "controlflow-recursive", "crypto-aes", "crypto-md5", "crypto-sha1",
    "date-format-tofte", "date-format-xparb",
    "math-cordic", "math-partial-sums", "math-spectral-norm",
    "regexp-dna",
    "string-base64", "string-fasta", "string-tagcloud", "string-unpack-code", "string-validate-input"
];

var repeatCount = 20; // number of iterations
var count = 5; // number of last iterations to consider

var results = { times: [], categories: {}};
for (var i = 0; i < tests.length; i++) {
    var test = tests[i];
    var dash = test.indexOf('-');
    var category = test.substring(0, dash);
    var testName = test.substring(dash + 1);
    results.categories[category] = results.categories[category] || { name: category, times: [], tests: {}};
    results.categories[category].tests[testName] = {name: testName, times: []};
}

for (var currentRepeat = -1; currentRepeat < repeatCount; currentRepeat++) {
    var totalTime = 0;
    for (var p in results.categories) {
        if (!results.categories.hasOwnProperty(p)) continue;
        var category = results.categories[p];
        var categoryTime = 0;
        for (var q in category.tests) {
            if (!category.tests.hasOwnProperty(q)) continue;
            var test = category.tests[q];
            print('iteration ' + lpad('' + currentRepeat, 2) + ' : loading ' + category.name + '-' + test.name + '.js')
            var start = new Date();
            load(category.name + "-" + test.name + ".js");
            var time = new Date() - start;
            totalTime += time;
            categoryTime += time;
            test.times.push(time);
        }
        category.times.push(categoryTime);
    }
    results.times.push(totalTime);
}

function lpad(s, n) { while (s.length < n) s = ' ' + s; return s; }
function rpad(s, n) { while (s.length < n) s += ' '; return s; }

var tDistribution = [NaN, NaN, 12.71, 4.30, 3.18, 2.78, 2.57, 2.45, 2.36, 2.31, 2.26, 2.23, 2.20, 2.18, 2.16, 2.14, 2.13, 2.12, 2.11, 2.10, 2.09, 2.09, 2.08, 2.07, 2.07, 2.06, 2.06, 2.06, 2.05, 2.05, 2.05, 2.04, 2.04, 2.04, 2.03, 2.03, 2.03, 2.03, 2.03, 2.02, 2.02, 2.02, 2.02, 2.02, 2.02, 2.02, 2.01, 2.01, 2.01, 2.01, 2.01, 2.01, 2.01, 2.01, 2.01, 2.00, 2.00, 2.00, 2.00, 2.00, 2.00, 2.00, 2.00, 2.00, 2.00, 2.00, 2.00, 2.00, 2.00, 2.00, 1.99, 1.99, 1.99, 1.99, 1.99, 1.99, 1.99, 1.99, 1.99, 1.99, 1.99, 1.99, 1.99, 1.99, 1.99, 1.99, 1.99, 1.99, 1.99, 1.99, 1.99, 1.99, 1.99, 1.99, 1.99, 1.99, 1.99, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.98, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.97, 1.96];
function tDist(n) {
    return (n >= tDistribution.length) ? 1.96 : tDistribution[n];
}

function timeDisplay(times) {
    // only consider last <count> iterations
    times = times.slice(-count);
    var sum = 0;
    for (var i = 0; i < count; i++) {
        sum += times[i];
    }
    var mean = sum / count;
    var deltaSquared = 0;
    for (var i = 0; i < count; i++) {
        deltaSquared += Math.pow(times[i] - mean, 2)
    }
    var variance = deltaSquared / (count - 1);

    var stdDev = Math.sqrt(variance);
    var sqrtCount = Math.sqrt(count);
    var stdErr = stdDev / sqrtCount;
    var percent = ((tDist(count) * stdErr / mean) * 100).toFixed(1);
    return lpad(mean.toFixed(1), 8) + "ms +- " + lpad(percent, 4) + "%";
}

// calculate mean, variance and display 95% CI

print("============================================");
print("RESULTS (means and 95% confidence intervals)");
print("--------------------------------------------");
print(rpad("Total:", 22) + timeDisplay(results.times));
print("--------------------------------------------");

for (var p in results.categories) {
    if (!results.categories.hasOwnProperty(p)) continue;
    var category = results.categories[p];
    print(rpad("  " + category.name + ":", 22) + timeDisplay(category.times));
    for (var q in category.tests) {
        if (!category.tests.hasOwnProperty(q)) continue;
        var test = category.tests[q];
        print(rpad("    " + test.name + ":", 22) + timeDisplay(test.times));
    }
}

