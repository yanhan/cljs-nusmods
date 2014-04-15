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
          freqHash[nrOccurrences] = {
            count: 0,
            lengthArray: []
          };
        }
        // number of occurrences --> how many unique strings occur this many times
        freqHash[nrOccurrences].count += 1;
        // for the number of times this string appears, push it onto the
        // `lengthArray`
        for (k = 0; k < nrOccurrences; k += 1) {
          freqHash[nrOccurrences].lengthArray.push(valueLen);
        }
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
      for (nrOccurrences in freqHash) {
        if (freqHash.hasOwnProperty(nrOccurrences)) {
          lengthArray = freqHash[nrOccurrences].lengthArray;
          lengthArray.sort();
          len = lengthArray.length;
          sumLengths = 0;
          for (k = 0; k < len; k += 1) {
            sumLengths += lengthArray[k];
          }
          arr.push({
            nrOccurrences: nrOccurrences,
            count: freqHash[nrOccurrences].count,
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
        stats: arr
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
    }
  }
};

show_results_for_string_keys(
  compute_stats_for_string_keys_from_value_frequencies(
    compute_value_frequencies_for_string_keys()
  )
);
