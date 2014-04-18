var _ = require("lodash");
var MODULES_ARRAY = require("./modules.json");

var STRING_KEYS = [
  "ModuleCode", "ModuleTitle", "Department", "ModuleDescription",
  "ModuleCredit", "Workload", "Preclusion", "ExamDate", "Prerequisite",
  "CrossModule", "Corequisite"
];

// Returns an object that looks like this:
// {
//    ModuleCode: {
//      module1string: number of times module1string appears,
//      module2string: number of times module2string appears,
//      ...
//    },
//    ModuleTitle: {
//      title1: number of times title1 appears,
//      title2: number of times title2 appears,
//      ...
//    },
//    ...
//    Corequisite: {
//      coreq1: number of times coreq1 appears,
//      coreq2: number of times coreq2 appears
//      ...
//    }
// }
var compute_value_frequencies = function(keyList) {
  var valuesHash = {};
  _(keyList).forEach(function(key) {
    valuesHash[key] = {};
  });
  _(MODULES_ARRAY).forEach(function(mod) {
    _(keyList).forEach(function(key) {
      var val;
      if (_.has(mod, key)) {
        val = mod[key];
        if (val.trim() !== val) {
          console.log("value for key \"" + key + "\" needs trimming");
        }
        if (valuesHash[key][val]) {
          valuesHash[key][val] += 1;
        } else {
          valuesHash[key][val] = 1;
        }
      }
    });
  });
  return valuesHash;
};

// all values for the key are unique
var FREQ_ALL_UNIQUE = 1;
// not all values for the key are unique
var FREQ_MORE_ANALYSIS = 2;

// computes some numbers on the values of string keys from the return value of
// the `compute_value_frequencies_for_string_keys` function
var compute_stats_from_value_frequencies = function(keyList,
    valuesHash) {
  var retHash = {};
  _(keyList).forEach(function(key) {
    var countObj = valuesHash[key];
    var freqHash = {};
    var bytesForStoringAllStrings;
    var sumLengthOfUniqueStrings;
    var bucketStatsArray = [];
    var totalNrUniqueStrings = 0;
    // for each possible value for the current string key, compute an obj with:
    //   number of occurrences --> array of integers. Each integer in the array
    //   is the lengh of a unique string which is present that many times.
    _.forOwn(countObj, function(nrOccurrences, uniqueString) {
      if (!_.has(freqHash, nrOccurrences)) {
        freqHash[nrOccurrences] = [];
      }
      freqHash[nrOccurrences].push(uniqueString.length);
    });
    if (_.keys(freqHash).length === 1 && _.has(freqHash, 1)) {
      // all values are unique
      retHash[key] = { result: FREQ_ALL_UNIQUE };
    } else {
      // compute array of BucketStats object along some other numbers.
      // Each BucketStats object has the following properties:
      // - nrOccurrences
      // - stringLengthArray (optional)
      // - count
      // - minLen
      // - maxLen
      // - medianLen
      // - avgLen
      bytesForStoringAllStrings = 0;
      sumLengthOfUniqueStrings = 0;
      _.forOwn(freqHash, function(stringLengthArray, nrOccurrences) {
        var nrUniqueStrings = stringLengthArray.length;
        var lengthArray = _.reduce(stringLengthArray,
          function(arr, stringLength) {
            _.times(nrOccurrences, function() { arr.push(stringLength); });
            return arr;
          }, []).sort();
        var len = lengthArray.length;
        var sumLengths = _.reduce(stringLengthArray,
          function(sumLengths, currentLength) {
              return sumLengths + nrOccurrences * currentLength;
          }, 0);
        var bytesForStoringIntegerIndices;
        bytesForStoringAllStrings += sumLengths;
        sumLengthOfUniqueStrings += sumLengths / nrOccurrences;
        totalNrUniqueStrings += nrUniqueStrings;
        bucketStatsArray.push({
          nrOccurrences: nrOccurrences,
          stringLengthArray: stringLengthArray,
          count: nrUniqueStrings,
          minLen: lengthArray[0],
          maxLen: lengthArray[len-1],
          medianLen: ((len % 2 === 1) ? lengthArray[Math.floor(len/2)] :
            (lengthArray[len/2 - 1] + lengthArray[len/2]) / 2),
          avgLen: sumLengths / len
        });
      });
      bucketStatsArray.sort(function(a, b) {
        return b.nrOccurrences - a.nrOccurrences
      });
      bytesForStoringIntegerIndices =
        compute_storage_for_integer_indices(bucketStatsArray);
      retHash[key] = {
        result: FREQ_MORE_ANALYSIS,
        bucketStatsArray: _.map(bucketStatsArray, function(bucketStats) {
          return _.omit(bucketStats, 'stringLengthArray');
        }),
        bytesForStoringAllStrings: bytesForStoringAllStrings,
        // '[', ']', commas, quotes, strings themselves
        bytesForStoringUniqueStringsInNewArray: 2 + (totalNrUniqueStrings - 1) +
          totalNrUniqueStrings * 2 + sumLengthOfUniqueStrings,
        bytesForStoringIntegerIndices: bytesForStoringIntegerIndices
      };
    }
  });

  return retHash;
};

// Given an array of BucketStats objects with the `stringLengthArray` property,
// where the array is sorted by descending `nrOccurrences` property, compute
// the space required for storing integer indices in a JSON file.
// Indices will start from 0 for high `nrOccurrences`.
var compute_storage_for_integer_indices = function(bucketStatsArray) {
  var compute_storage_for_one_non_negative_integer = function(x) {
    var nrDigits = 0;
    if (x === 0) {
      return 1;
    }
    while (x > 0) {
      nrDigits += 1;
      x = Math.floor(x / 10);
    }
    return nrDigits;
  };

  return _.reduce(bucketStatsArray, function(accObj, bucketStats) {
    var currentIndex = accObj.currentIndex;
    var totalSpace = accObj.totalSpace;
    var nrOccurrences = bucketStats.nrOccurrences;
    _.forEach(bucketStats.stringLengthArray, function(stringLength) {
      totalSpace += nrOccurrences *
        compute_storage_for_one_non_negative_integer(currentIndex);
      currentIndex += 1;
    });
    return {
      currentIndex: currentIndex,
      totalSpace: totalSpace
    };
  }, { currentIndex: 0, totalSpace: 0 }).totalSpace;
};

// Displays the results from the
// `compute_stats_for_string_keys_from_value_frequencies` function
var show_results = function(keyList, results) {
  _.forEach(keyList, function(key, idx) {
    var keyResult = results[key];
    if (idx > 0) {
      console.log("\n");
    }
    console.log("Key \"" + key + "\"");
    if (keyResult.result === FREQ_ALL_UNIQUE) {
      console.log("    All values are unique");
    } else {
      _.forEach(keyResult.bucketStatsArray, function(bucketStats) {
        console.log("    Values occurring " + bucketStats.nrOccurrences +
          " times: " + bucketStats.count + " unique strings [min length: " +
          bucketStats.minLen + ", max length: " + bucketStats.maxLen +
          ", avg length: " + bucketStats.avgLen + ", median length: " +
          bucketStats.medianLen + "]"
        );
      });
      console.log("Bytes for storing all strings: " +
        keyResult.bytesForStoringAllStrings
      );
      console.log("Bytes for storing unique strings in separate array: " +
        keyResult.bytesForStoringUniqueStringsInNewArray
      );
      console.log("Bytes for storing integer indices: " +
        keyResult.bytesForStoringIntegerIndices
      );
      console.log("Storing all strings vs. storing unique strings + " +
        "integer indices: " + keyResult.bytesForStoringAllStrings + " vs. " +
        (keyResult.bytesForStoringUniqueStringsInNewArray+
         keyResult.bytesForStoringIntegerIndices)
      );
      if (keyResult.bytesForStoringAllStrings <=
          keyResult.bytesForStoringUniqueStringsInNewArray+
            keyResult.bytesForStoringIntegerIndices) {
        console.log("It is more efficient in space to store all the strings.");
      } else {
        console.log("It is more efficient in space to store unique strings " +
          "with integer indices."
        );
      }
    }
  });
};

show_results(
  STRING_KEYS,
  compute_stats_from_value_frequencies(
    STRING_KEYS,
    compute_value_frequencies(STRING_KEYS)
  )
);
