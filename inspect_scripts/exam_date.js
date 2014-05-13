// ASSUMPTION I:
//   This script assumes that the .ExamDate value for Module objects are
//   strings in the following format:
//
//       "YYYY-MM-DDTHH:MM+ZZZZ"
//
//   One concrete example:
//
//       "2014-08-25T13:00+0800"
//
// ASSUMPTION II:
//
//   The timestamps are assumed to be in the same timezone, +0800
//
// ASSUMPTION III:
//
//   The string "0000-01-01T00:00+0800" represents the fact that the module
//   has no exam.


//
// Comparison of 3 strategies for storing Module.ExamDate
// Strategy 1: Store unique strings once in separate array. For a module's
//             .examDate field, store an index to the array of unique strings
// Strategy 2: Store as UNIX timestamp in a module's .ExamDate field
// Strategy 3: Store unique UNIX timestamp in separate array. For a module's
//             .examDate field, store an index to the array of unique UNIX
//             timestamps
var _ = require("lodash");
var moment = require("moment");
var MODULES_ARRAY = require(__dirname + "/../api-nusmods-stuff/modules.json");
var NO_EXAM_DATE_STRING = "0000-01-01T00:00+0800";

console.log("NOTE: Storing \"" + NO_EXAM_DATE_STRING + "\" as integer `-1`\n");

(function() {
  var freqHash;
  var freqArray;
  var nrUniqueDateStrings;
  var bytesRequired;

  var bytes_for_storing_one_integer_in_json = function(x) {
    var nrBytes = 0;
    if (x === 0) {
      return 1;
    } else {
      while (x > 0) {
        nrBytes += 1;
        x = Math.floor(x / 10);
      }
    }
    return nrBytes;
  };

  freqHash = _.reduce(MODULES_ARRAY, function(freqHash, mod) {
    if (_.has(mod, "ExamDate") && mod.ExamDate !== NO_EXAM_DATE_STRING) {
      if (!_.has(freqHash, mod.ExamDate)) {
        freqHash[mod.ExamDate] = 0;
      }
      freqHash[mod.ExamDate] += 1;
    }
    return freqHash;
  }, {});
  // use lodash chaining
  freqArray = _(freqHash)
    .map(function(nrOccurrences, dateString) {
      return {
        nrOccurrences: nrOccurrences,
        dateString: dateString
      };
    })
    .sort(function(a, b) { return b.nrOccurrences - a.nrOccurrences; })
    .reduce(function(objWithArrayAndIndex, obj) {
      obj.index = objWithArrayAndIndex.index;
      objWithArrayAndIndex.index += 1;
      objWithArrayAndIndex.array.push(obj);
      return objWithArrayAndIndex;
    }, { array: [], index: 0 })
    .array;
  freqHash = _.reduce(freqArray, function(fhash, obj) {
    fhash[obj.dateString] = {
      nrOccurrences: obj.nrOccurrences,
      index: obj.index
    };
    return fhash;
  }, {});
  nrUniqueDateStrings = _.keys(freqHash).length;

  console.log("Strategy 1");
  console.log("- Store unique strings in separate array");
  console.log("- Store an integer index to the array of unique strings");
  console.log("  in a module's .ExamDate field");
  console.log("Bytes required = " + (
    // '[', ']', commas, quotes, actual strings
    2 + (nrUniqueDateStrings - 1) + 2 * nrUniqueDateStrings +
      _.reduce(freqHash, function(sum, o, dateString) {
        return sum + dateString.length;
      }, 0) +
      // indices to the array of date strings
      _.reduce(freqHash, function(sum, o) {
        return sum +
          o.nrOccurrences * bytes_for_storing_one_integer_in_json(o.index);
      }, 0)
  ));

  console.log("\nStrategy 2");
  console.log("- Store as UNIX timestamps in module's .ExamDate field");
  console.log("Bytes required = " + (
    _.reduce(freqHash, function(sum, o, dateString) {
      return sum + o.nrOccurrences *
        bytes_for_storing_one_integer_in_json(moment(dateString).unix());
    }, 0)
  ));

  console.log("\nStrategy 3");
  console.log("- Store unique UNIX timestamps in separate array");
  console.log("- Store an integer index to the array of unique UNIX ");
  console.log("  timestamps in a module's .ExamDate field");
  console.log("Bytes required = " + (
    // '[', ']', commas, UNIX timestamps
    2 + (nrUniqueDateStrings - 1) +
      _.reduce(freqHash, function(sum, o, dateString) {
        return sum + bytes_for_storing_one_integer_in_json(
          moment(dateString).unix()
        );
      }, 0) +
      // indices to the array of UNIX timestamps
      _.reduce(freqHash, function(sum, o) {
        return sum +
          o.nrOccurrences * bytes_for_storing_one_integer_in_json(o.index);
      }, 0)
  ))
})();
