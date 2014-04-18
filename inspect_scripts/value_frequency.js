var _ = require("lodash");
var modulesJSON = require("./modules.json");
var nrModules = modulesJSON.length;

var stringKeys = [
  "ModuleCode", "ModuleTitle", "Department", "ModuleDescription",
  "ModuleCredit", "Workload", "Preclusion", "ExamDate", "Prerequisite",
  "CrossModule", "Corequisite"
];
var nrStringKeys = stringKeys.length;

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
var compute_value_frequencies_for_string_keys = function() {
  var valuesHash = {};
  _(stringKeys).forEach(function(stringKey) {
    valuesHash[stringKey] = {};
  });
  _(modulesJSON).forEach(function(mod) {
    _(stringKeys).forEach(function(key) {
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
var compute_stats_for_string_keys_from_value_frequencies = function(
    valuesHash) {
  var retHash = {};
  _(stringKeys).forEach(function(key) {
    var countObj = valuesHash[key];
    var freqHash = {};
    var sumLengthsOfAllStrings;
    var sumLengthsOfUniqueStrings;
    var arr = [];
    var bytesForStoringIntegerIndices = 0;
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
      sumLengthsOfAllStrings = 0;
      sumLengthsOfUniqueStrings = 0;
      _.forOwn(freqHash, function(stringLengthArray, nrOccurrences) {
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
        sumLengthsOfAllStrings += sumLengths;
        sumLengthsOfUniqueStrings += sumLengths / nrOccurrences;
        bytesForStoringIntegerIndices += 8 * len;
        arr.push({
          nrOccurrences: nrOccurrences,
          count: freqHash[nrOccurrences].length,
          minLen: lengthArray[0],
          maxLen: lengthArray[len-1],
          medianLen: ((len % 2 === 1) ? lengthArray[Math.floor(len/2)] :
            (lengthArray[len/2 - 1] + lengthArray[len/2]) / 2),
          avgLen: sumLengths / len
        });
      });
      arr.sort(function(a, b) { return b.nrOccurrences - a.nrOccurrences });
      retHash[key] = {
        result: FREQ_MORE_ANALYSIS,
        stats: arr,
        sumLengthsOfAllStrings: sumLengthsOfAllStrings,
        sumLengthsOfUniqueStrings: sumLengthsOfUniqueStrings,
        bytesForStoringIntegerIndices: bytesForStoringIntegerIndices
      };
    }
  });

  return retHash;
};

// Displays the results from the
// `compute_stats_for_string_keys_from_value_frequencies` function
var show_results_for_string_keys = function(results) {
  console.log("Value Analysis for string keys");
  console.log("------------------------------");
  _.forEach(stringKeys, function(key, idx) {
    var keyResult = results[key];
    var statsArr;
    if (idx > 0) {
      console.log("\n");
    }
    console.log("Key \"" + key + "\"");
    if (keyResult.result === FREQ_ALL_UNIQUE) {
      console.log("    All values are unique");
    } else {
      _.forEach(keyResult.stats, function(statsObj) {
        console.log("    Values occurring " + statsObj.nrOccurrences +
          " times: " + statsObj.count + " unique strings [min length: " +
          statsObj.minLen + ", max length: " + statsObj.maxLen +
          ", avg length: " + statsObj.avgLen + ", median length: " +
          statsObj.medianLen + "]"
        );
      });
      console.log("Sum of length of all strings: " +
        keyResult.sumLengthsOfAllStrings
      );
      console.log("Sum of lengths of unique strings: " +
        keyResult.sumLengthsOfUniqueStrings
      );
      console.log("Bytes for storing integer indices (8 byte ints): " +
        keyResult.bytesForStoringIntegerIndices
      );
      console.log("Storing all strings vs. storing unique strings + " +
        "integer indices: " + keyResult.sumLengthsOfAllStrings + " vs. " +
        (keyResult.sumLengthsOfUniqueStrings +
         keyResult.bytesForStoringIntegerIndices)
      );
      if (keyResult.sumLengthsOfAllStrings <=
          keyResult.sumLengthsOfUniqueStrings +
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

show_results_for_string_keys(
  compute_stats_for_string_keys_from_value_frequencies(
    compute_value_frequencies_for_string_keys()
  )
);
