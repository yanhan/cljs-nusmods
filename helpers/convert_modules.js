var _ = require("lodash");
var fs = require("fs");
var SharedGlobals = require("./shared_globals.js");
var ORIGINAL_MODULES_ARRAY = require(__dirname + "/../processed_modules.json");

// Module Type information. This is written to `auxmodinfo.js`
var MODULE_TYPE = {
  Faculty: 1 << 0,
  UE: 1 << 1,
  GEM: 1 << 2,
  SS: 1 << 3,
};

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
// Index | Key in Module Object | Value Type        | Meaning                   | Example
// -------------------------------------------------------------------------------------------------
//    0  | ModuleDescription    | String            | Module Description        | An introductory
//       |                      |                   |                           | programming module
//    1  | Department           | Integer           | Index to array of         |
//       |                      |                   | Department strings        | Computer Science
//       |                      |                   |                           |
//    2  | Type                 | Integer           | Bitmask used to represent |
//       |                      |                   | module information.       |
//       |                      |                   | See below for explanation |
//       |                      |                   |                           |
//    3  | Lecturers            | Array of Integers | Each integer in the array |
//       |                      |                   | is an index to its string |
//       |                      |                   | value in the `Lecturers`  |
//       |                      |                   | array.                    |
//
// The AuxModule.Type field is a bitmask used to represent the Type of the
// module. This is required for filtering modules in the Module Finder page
// by Breadth/UE, Faculty, GEM, Not in CORS, Singapore Studies.
// It is constructed based on the string values in the `Module.Types` array in
// the `processed_modules.json` file.
// This bitmask uses 3 Least Significant bits:
//
//   Bit 0 (Least Significant Bit)
//     - Is the module a faculty module? An alternative way of asking is,
//       is there an object in the `processed_modules.json` file with
//       "Module" in its `.Type` array and `.ModuleCode` equivalent to the
//       module's code?
//   Bit 1
//     - Is the module a Breadth/UEM module? An alternative way of asking is,
//       is there an object in the `processed_modules.json` file with
//       "UEM" in its `.Type` array and `.ModuleCode` equivalent to the module's
//       code?
//   Bit 2
//     - Is the module a GEM module? An alternative way of asking is, is there
//       an object in the `processed_modules.json` file with "GEM" in its
//       `.Type` array and `.ModuleCode` equivalent to the module's code?
//   Bit 3
//     - Is the module a Singapore Studies module? An alternative way of asking
//       is, is there an object in the `processed_modules.json` file with "SSM"
//       in its `.Type` array and `.ModuleCode` equivalent to the module's code?
//
// An array of AuxModule objects, along with an array of `Department` strings,
// constitute the auxiliary module information. This auxiliary module
// information is assigned to the global `AUXMODULES` array, like so:
//
//     AUXMODULES = {
//       auxModules: array of AuxModule objects,
//       departments: array of department strings,
//       lecturers: array of lecturer strings
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

var compute_StringValuesIndex_for_key_with_array_of_strings_value = function(
    modulesArray, key) {
  var modulesWithKey = _.filter(modulesArray, function(mod) {
    return _.has(mod, key);
  });
  var freqHash = _.reduce(modulesWithKey, function(fhash, mod) {
    _.forEach(mod[key], function(value) {
      if (!_.has(fhash, value)) {
        fhash[value] = 0;
      }
      fhash[value] += 1;
    });
    return fhash;
  }, {});
  var freqArray = _(freqHash).map(function(count, lecturer) {
    return {
      lecturer: lecturer,
      count: count
    };
  })
  .sort(function(a, b) { return b.count - a.count; })
  .value();
  freqHash = _.reduce(freqArray, function(obj, freqObj) {
    obj.indexHash[freqObj.lecturer] = obj.index;
    obj.index += 1;
    obj.stringsArray.push(freqObj.lecturer);
    return obj;
  }, { indexHash: {}, stringsArray: [], index: 0 });
  return {
    indexHash: freqHash.indexHash,
    stringsArray: freqHash.stringsArray
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
  var lecturersStringValuesIndex =
    compute_StringValuesIndex_for_key_with_array_of_strings_value(
      ORIGINAL_MODULES_ARRAY, "Lecturers"
    );
  // contains absolutely critical module information
  var modulesArray = [];
  // contains auxiliary module information
  var auxModulesArray = [];
  _.forEach(ORIGINAL_MODULES_ARRAY, function(orgModule) {
    var mod = [];
    var auxMod = [];
    var moduleTypeBitmask = 0;
    var lecturersArray = [];
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
    if (_.has(orgModule, "Types")) {
      _.forEach(orgModule.Types, function(moduleType) {
        if (moduleType === "Module") {
          moduleTypeBitmask |= MODULE_TYPE.Faculty;
        } else if (moduleType === "UEM" || moduleType === "CFM") {
          moduleTypeBitmask |= MODULE_TYPE.UE;
        } else if (moduleType === "GEM") {
          moduleTypeBitmask |= MODULE_TYPE.GEM;
        } else if (moduleType === "SSM") {
          moduleTypeBitmask |= MODULE_TYPE.SS;
        }
      });
    }
    auxMod.push(moduleTypeBitmask);
    if (_.has(orgModule, "Lecturers")) {
      _.forEach(orgModule.Lecturers, function(lecturer) {
        var lecturerIdx = lecturersStringValuesIndex.indexHash[lecturer];
        if (lecturerIdx !== null) {
          lecturersArray.push(lecturerIdx);
        } else {
          console.log("Module \"" + orgModule.ModuleCode + "\": Lecturer \"" +
            lecturer + "\" not found in lecturersStringValuesIndex.indexHash"
          );
        }
      });
    }
    auxMod.push(lecturersArray);
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
    // module type information
    "var MODULE_TYPE=" + JSON.stringify(MODULE_TYPE) + ";" +
    "var AUXMODULES=" +
      JSON.stringify({
        auxModules: auxModulesArray,
        departments: departmentStringValuesIndex.stringsArray,
        lecturers: lecturersStringValuesIndex.stringsArray
      }) +
      ";",
    { flag: "w" }
  );
})();
