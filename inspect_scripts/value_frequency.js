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
  var i;
  var mod;
  var valuesHash = {};
  var k;
  var key;
  var val;
  for (k = 0; k < nrStringKeys; k += 1) {
    valuesHash[stringKeys[k]] = {};
  }

  for (i = 0; i < nrModules; i += 1) {
    mod = modulesJSON[i];
    for (k = 0; k < nrStringKeys; k += 1) {
      key = stringKeys[k];
      if (mod.hasOwnProperty(key)) {
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
    }
  }
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
  var i;
  var k;
  var m;
  var key;
  var countObj;
  var prop;
  var retHash = {};
  var ret = {};
  var valueLen;
  var nrOccurrences;
  var freqHash;
  var nrBuckets;
  var arr;
  var lengthArray;
  var len;
  var sumLengths;
  var sumLengthsOfAllStrings;
  var sumLengthsOfUniqueStrings;
  var bytesForStoringIntegerIndices;

  for (i = 0; i < nrStringKeys; i += 1) {
    key = stringKeys[i];
    countObj = valuesHash[key];
    freqHash = {};
    // for each possible value for that string key
    for (prop in countObj) {
      if (countObj.hasOwnProperty(prop)) {
        valueLen = prop.length;
        nrOccurrences = countObj[prop];
        if (!freqHash.hasOwnProperty(nrOccurrences)) {
          freqHash[nrOccurrences] = [];
        }
        // number of occurrences --> array of integers. Each integer is the
        //   lenghth of a unique string which is present that many times.
        freqHash[nrOccurrences].push(valueLen);
      }
    }
    // compute the number of different buckets. Bucket refers to the number of
    // occurrences.
    nrBuckets = 0;
    for (nrOccurrences in freqHash) {
      if (freqHash.hasOwnProperty(nrOccurrences)) {
        nrBuckets += 1;
      }
    }
    if (nrBuckets === 1) {
      retHash[key] = { result: FREQ_ALL_UNIQUE };
    } else {
      arr = [];
      sumLengthsOfAllStrings = 0;
      sumLengthsOfUniqueStrings = 0;
      bytesForStoringIntegerIndices = 0;
      for (nrOccurrences in freqHash) {
        if (freqHash.hasOwnProperty(nrOccurrences)) {
          lengthArray = [];
          sumLengths = 0;
          for (k = 0, len = freqHash[nrOccurrences].length; k < len; k += 1) {
            valueLen = freqHash[nrOccurrences][k];
            sumLengths += valueLen * nrOccurrences;
            sumLengthsOfUniqueStrings += valueLen;
            // assume one integer takes up 8 bytes
            bytesForStoringIntegerIndices += 8 * nrOccurrences;
            for (m = 0; m < nrOccurrences; m += 1) {
              lengthArray.push(valueLen);
            }
          }
          sumLengthsOfAllStrings += sumLengths;
          lengthArray.sort();
          len = lengthArray.length;
          arr.push({
            nrOccurrences: nrOccurrences,
            count: freqHash[nrOccurrences].length,
            minLen: lengthArray[0],
            maxLen: lengthArray[len-1],
            medianLen: ((len % 2 === 1) ? lengthArray[Math.floor(len/2)] :
              (lengthArray[len/2 - 1] + lengthArray[len/2]) / 2),
            avgLen: sumLengths / len
          });
        }
      }
      arr.sort(function(a, b) { return b.nrOccurrences - a.nrOccurrences });
      retHash[key] = {
        result: FREQ_MORE_ANALYSIS,
        stats: arr,
        sumLengthsOfAllStrings: sumLengthsOfAllStrings,
        sumLengthsOfUniqueStrings: sumLengthsOfUniqueStrings,
        bytesForStoringIntegerIndices: bytesForStoringIntegerIndices
      };
    }
  }
  return retHash;
};

var show_results_for_string_keys = function(results) {
  var i;
  var key;
  var keyResult;
  var statsObj;
  var freq;
  var k;
  var starsArr;
  var len;
  console.log("Value Analysis for string keys");
  console.log("------------------------------");
  for (i = 0; i < nrStringKeys; i += 1) {
    key = stringKeys[i];
    keyResult = results[key];
    if (i > 0) {
      console.log("\n");
    }
    console.log("Key \"" + key + "\"");
    if (keyResult.result === FREQ_ALL_UNIQUE) {
      console.log("    All values are unique");
    } else {
      statsArr = keyResult.stats;
      len = statsArr.length;
      for (k = 0; k < len; k += 1) {
        statsObj = statsArr[k];
        console.log("    Values occurring " + statsObj.nrOccurrences +
          " times: " + statsObj.count + " unique strings [min length: " +
          statsObj.minLen + ", max length: " + statsObj.maxLen +
          ", avg length: " + statsObj.avgLen + ", median length: " +
          statsObj.medianLen + "]"
       );
      }
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
  }
};

show_results_for_string_keys(
  compute_stats_for_string_keys_from_value_frequencies(
    compute_value_frequencies_for_string_keys()
  )
);
