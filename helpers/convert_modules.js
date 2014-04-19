var _ = require("lodash");
var fs = require("fs");
var SharedGlobals = require("./shared_globals.js");
var ORIGINAL_MODULES_ARRAY = require(__dirname + "/../processed_modules.json");

// This section details the compacted data representation for a Module object.
// A Module object does not contain all the information for an actual module,
// only the critical information which is required for the `Timetable Builder`
// page.
// Each Module object is represented as a fixed length array.
//
// Indices and meaning of the value in that index:
// 
// Index | Key in Module Object | Value Type | Meaning               | Example
// -------------------------------------------------------------------------------------------
//   0   | ModuleCode           | String     | Module Code String    | CS1010
//   1   | ModuleTitle          | String     | Module Title String   | Programming Methodology
//   2   | ModuleCredit         | Integer    | Modular Credits       | 4
//   3   | ExamDate             | Integer    | Index to array of     | 0
//       |                      |            | Date Strings of       |
//       |                      |            | ExamDate              |
//
// An array of Module objects, along with an array of ExamDate strings, form
// the essential module information. This essential module information is
// assigned to the global `MODULES` array, like so:
//
//     MODULES = {
//       modules: array of Module objects,
//       examDates: array of exam date strings
//     };
//
//
//
// For information that is not so critical and can be loaded in the
// `Module Finder` page, we represent them using AuxModule objects.
//
// Index | Key in Module Object | Value Type | Meaning            | Example
// -----------------------------------------------------------------------------------
//    0  | ModuleDescription    | String     | Module Description | An introductory
//       |                      |            |                    | programming module
//    1  | Department           | Integer    | Index to array of  |
//       |                      |            | Department strings | Computer Science
//
// An array of AuxModule objects, along with an array of `Department` strings,
// constitute the auxiliary module information. This auxiliary module
// information is assigned to the global `AUXMODULES` array, like so:
//
//     AUXMODULES = {
//       auxModules: array of AuxModule objects,
//       departments: array of department strings
//     };


// Type Definitions
// ----------------
//
// StringValuesIndex
// {
//   indexHash: an object of string value -> index in stringsArray
//   stringsArray: an array of unique string values, starting with the string
//                 which occurs by the most frequently
// }

var compute_StringValuesIndex_for_key_with_string_value = function(
    modulesArray, key) {
  var freqHash = _.reduce(modulesArray, function(fhash, module) {
    var value;
    if (_.has(module, key)) {
      value = module[key];
      if (!_.has(fhash, value)) {
        fhash[value] = 0;
      }
      fhash[value] += 1;
    }
    return fhash;
  }, {});
  var freqArray = _(freqHash)
    .map(function(nrOccurrences, value) {
      return {
        value: value,
        nrOccurrences: nrOccurrences
      };
    })
    .sort(function(a, b) { return b.nrOccurrences - a.nrOccurrences; })
    .reduce(function(objArrayAndIndex, freqObj) {
      var obj = _.clone(freqObj);
      obj.index = objArrayAndIndex.index;
      objArrayAndIndex.array.push(obj);
      objArrayAndIndex.index += 1;
      return objArrayAndIndex
    }, { array: [], index: 0 })
    .array;
  var indexHash = _.reduce(freqArray, function(idxHash, obj) {
    idxHash[obj.value] = obj.index;
    return idxHash;
  }, {});
  var stringsArray = _.map(freqArray, function(freqObj) {
    return freqObj.value;
  });
  return {
    indexHash: indexHash,
    stringsArray: stringsArray
  };
};

(function() {
  var examDateStringValuesIndex =
    compute_StringValuesIndex_for_key_with_string_value(
      ORIGINAL_MODULES_ARRAY, "ExamDate"
    );
  var departmentStringValuesIndex =
    compute_StringValuesIndex_for_key_with_string_value(
      ORIGINAL_MODULES_ARRAY, "Department"
    );
  // contains absolutely critical module information
  var modulesArray = [];
  // contains auxiliary module information
  var auxModulesArray = [];
  _.forEach(ORIGINAL_MODULES_ARRAY, function(orgModule) {
    var mod = [];
    var auxMod = [];
    mod.push(_.has(orgModule, "ModuleCode") ? orgModule.ModuleCode : -1);
    mod.push(_.has(orgModule, "ModuleTitle") ? orgModule.ModuleTitle : -1);
    mod.push(_.has(orgModule, "ModuleCredit") ? orgModule.ModuleCredit : -1);
    if (_.has(orgModule, "ExamDate")) {
      mod.push(examDateStringValuesIndex.indexHash[orgModule.ExamDate]);
    } else {
      mod.push(examDateStringValuesIndex.indexHash[
        SharedGlobals.PROCESSED_NO_EXAM_DATE_STRING
      ]);
    }
    modulesArray.push(mod);

    auxMod.push(_.has(orgModule, "ModuleDescription") ?
      orgModule.ModuleDescription : -1
    );
    if (_.has(orgModule, "Department")) {
      auxMod.push(departmentStringValuesIndex.indexHash[orgModule.Department]);
    } else {
      auxMod.push(-1);
    }
    auxModulesArray.push(auxMod);
  });

  // write to file
  fs.writeFileSync("modinfo.js",
    "var MODULES=" +
      JSON.stringify({
        modules: modulesArray,
        examDates: examDateStringValuesIndex.stringsArray
      }) +
      ";",
    { flag: "w" }
  );
  fs.writeFileSync("auxmodinfo.js",
    "var AUXMODULES=" +
      JSON.stringify({
        auxModules: auxModulesArray,
        departments: departmentStringValuesIndex.stringsArray
      }) +
      ";",
    { flag: "w" }
  );
})();
