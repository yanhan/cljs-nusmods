var _ = require("lodash");
var fs = require("fs");
var SharedGlobals = require("./shared_globals.js");
var NormalizeDepartment = require("./normalize_department.js");
var ORIGINAL_MODULES_ARRAY = require(__dirname + "/../processed_modules.json");
// An object that maps the value of the `Lesson.LessonType` attribute
// to either the string 'Lecture' or 'Tutorial'
var LESSON_TYPES_OBJECT = require(__dirname + "/../lessonTypes.json");
var FACULTY_DEPARTMENTS_OBJECT = require(
  __dirname + "/../facultyDepartments.json"
);
// String mapping of Department to Faculty
var DEPARTMENT_TO_FACULTY_HASH = _.reduce(FACULTY_DEPARTMENTS_OBJECT,
  function(deptToFacHash, facultyArray, department) {
    department = NormalizeDepartment.normalize_department_string(department);
    _.forEach(facultyArray, function(faculty) {
      faculty = NormalizeDepartment.normalize_department_string(faculty);
      deptToFacHash[faculty] = department;
    });
    return deptToFacHash;
  },
  {}
);

// Module Type information. This is written to `auxmodinfo.js`
var MODULE_TYPE = {
  Faculty: 1 << 0,
  UE: 1 << 1,
  GEM: 1 << 2,
  SS: 1 << 3,
};

// Lesson object keys that we should find a more compact representation for,
// based on a space efficiency comparison from
// `inspect_scripts/value_frequency.js`
var LESSON_KEYS_TO_HASH = [
  "LessonType", "WeekText", "Venue"
];

// Index of string representation of a lesson's day to its integer
// representation
var DAY_STRING_TO_INTEGER = {
  MONDAY: 0,
  TUESDAY: 1,
  WEDNESDAY: 2,
  THURSDAY: 3,
  FRIDAY: 4,
  SATURDAY: 5
};

// Converts a Lesson time to its integer representation
var lesson_time_to_integer_repr = function(time) {
  var timeInt = _.parseInt(time, 10);
  if (timeInt % 100 === 30) {
    // For timings which are "X-thirty" (eg. 0630 [six-thirty],
    // 0930 [nine-thirty]), add 20 so that we can divide them by 50 and simplify
    // computation
    timeInt += 20;
  }
  if (timeInt < 800) {
    // 0600 = -4, 0630 = -3, 0700 = -2, 0730 = -1
    return -(800 - timeInt / 50);
  } else {
    // Turn 2359 into 2400 for easier computation
    if (timeInt === 2359) {
      timeInt += 1;
    }
    // 0800 = 0, 0850 = 1 (represents 0830 in actuality), 0900 = 2,
    // 0950 = 3 (represents 0930 in actuality), until 2400 (2359 in reality,
    // converted to 2400 in `if` statement above to help simplify things)
    return (timeInt - 800) / 50;
  }
  return retVal;
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
//       |                      |            |                       |
//   4   | Timetable            | Array of   | See below for more    |
//       |                      | Arrays of  | information           |
//       |                      | Integers & |                       |
//       |                      | Strings    |                       |
//
//
// The Timetable array above stores arrays of integers and strings. Each of
// those arrays represent a Lesson. Their indices and meaning of values are as
// follows:
//
// Index | Key in Lesson Object | Value Type | Meaning
// ----------------------------------------------------------------------------
//   0   | ClassNo              | String     | Class Label
//       |                      |            |
//   1   | LessonType           | Integer    | Integer into array of LessonType
//       |                      |            | Strings
//       |                      |            |
//   2   | DayText              | Integer    | 0 = Monday, 1 = Tuesday, etc
//       |                      |            | until 5 = Saturday
//       |                      |            |
//   3   | StartTime            | Integer    | Starting time of the lesson.
//       |                      |            | 0 = 0800, 1 = 0830, 2 = 0900,
//       |                      |            | with increments of 1 per half
//       |                      |            | hour interval, until 2359
//       |                      |            | In addition, there are the
//       |                      |            | following special values:
//       |                      |            | -4 = 0600, -3 = 0630, -2 = 0700,
//       |                      |            | -1 = 0730 .
//       |                      |            | These are for timings that we do
//       |                      |            | not display in the timetable
//       |                      |            | builder
//       |                      |            |
//   4   | EndTime              | Integer    | Ending time of the lesson.
//       |                      |            | Same convention as EndTime.
//       |                      |            |
//   5   | Venue                | Integer    | Integer into array of Venue
//       |                      |            | Strings
//       |                      |            |
//   6   | WeekText             | Integer    | Integer into array of WeekText
//       |                      |            | Strings
//
// An array of Module objects, along with the following:
// - an array of ExamDate strings
// - an array of LessonType strings
// - an array of Venue strings
// - an array of WeekText strings
// form the essential module information. This essential module information is
// assigned to the global `MODULES` array, like so:
//
//     MODULES = {
//       modules: array of Module objects,
//       examDates: array of exam date strings,
//       lessonType: array of LessonType strings,
//       venues: array of Venue strings
//       weekText: array of WeekText strings
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
//       |                      |                   |                           |
//    4  | Prerequisites        | Integer           | Index to array of         |
//       |                      |                   | prerequisites strings     |
//       |                      |                   |                           |
//    5  | Preclusions          | Integer           | Index to array of         |
//       |                      |                   | preclusions strings       |
//       |                      |                   |                           |
//    6  | Workload             | Integer           | Index to array of         |
//       |                      |                   | workload strings          |
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
// An array of AuxModule objects, along with the following:
// - an array of `departments` strings
// - an array of `lecturers` strings
// - an array of `prereqs` strings
// - an array of `preclusions` strings
// - an array of `workload` strings
// - a hash of department index -> faculty index [this index is to the
//   `departments` array of strings]
//
// constitute the auxiliary module information. This auxiliary module
// information is assigned to the global `AUXMODULES` array, like so:
//
//     AUXMODULES = {
//       auxModules: array of AuxModule objects,
//       departments: array of department strings,
//       lecturers: array of lecturer strings,
//       prereqs: array of prerequisite strings,
//       preclusions: array of preclusions strings
//       workload: array of preclusions strings,
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
      // we factor the strings involved in `DEPARTMENTS_TO_FACULTY_HASH` into
      // the counts so as to achieve better compression
      ORIGINAL_MODULES_ARRAY.concat(
        _(DEPARTMENT_TO_FACULTY_HASH)
          .map(function(faculty, department) {
            return [{ Department: faculty }, { Department: department }]
          })
          .flatten()
          .value()
      ),
      "Department"
    );
  var lecturersStringValuesIndex =
    compute_StringValuesIndex_for_key_with_array_of_strings_value(
      ORIGINAL_MODULES_ARRAY, "Lecturers"
    );
  var prereqStringValuesIndex =
    compute_StringValuesIndex_for_key_with_string_value(
      ORIGINAL_MODULES_ARRAY, "Prerequisite"
    );
  var preclusionsStringValuesIndex =
    compute_StringValuesIndex_for_key_with_string_value(
      ORIGINAL_MODULES_ARRAY, "Preclusion"
    );
  var workloadStringValuesIndex =
    compute_StringValuesIndex_for_key_with_string_value(
      ORIGINAL_MODULES_ARRAY, "Workload"
    );
  var lessonsArray = _(ORIGINAL_MODULES_ARRAY)
    .filter(function(module) {
      return _.has(module, "Timetable");
    })
    .map(function(module) {
      return module.Timetable;
    })
    .flatten()
    .value();
  var lessonsKeysStringValuesIndex =
    _.reduce(LESSON_KEYS_TO_HASH, function(lessonsKeySVIndex, lessonKey) {
      lessonsKeySVIndex[lessonKey] =
        compute_StringValuesIndex_for_key_with_string_value(lessonsArray,
          lessonKey
        );
      return lessonsKeySVIndex;
    }, {});
  // contains absolutely critical module information
  var modulesArray = [];
  // contains auxiliary module information
  var auxModulesArray = [];
  _.forEach(ORIGINAL_MODULES_ARRAY, function(orgModule) {
    var mod = [];
    var lessonsArray;
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
    if (_.has(orgModule, "Timetable")) {
      mod.push(_.map(orgModule.Timetable, function(lesson) {
        // lesson representation
        var lessonRepr = [];
        lessonRepr.push(lesson.ClassNo);
        lessonRepr.push(lessonsKeysStringValuesIndex.LessonType.indexHash[
          lesson.LessonType
        ]);
        lessonRepr.push(DAY_STRING_TO_INTEGER[lesson.DayText]);
        lessonRepr.push(lesson_time_to_integer_repr(lesson.StartTime));
        lessonRepr.push(lesson_time_to_integer_repr(lesson.EndTime));
        lessonRepr.push(lessonsKeysStringValuesIndex.Venue.indexHash[
          lesson.Venue
        ]);
        lessonRepr.push(lessonsKeysStringValuesIndex.WeekText.indexHash[
          lesson.WeekText
        ]);
        return lessonRepr;
      }));
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
    if (_.has(orgModule, "Prerequisite")) {
      auxMod.push(prereqStringValuesIndex.indexHash[orgModule.Prerequisite]);
    } else {
      auxMod.push(-1);
    }
    if (_.has(orgModule, "Preclusion")) {
      auxMod.push(preclusionsStringValuesIndex.indexHash[orgModule.Preclusion]);
    } else {
      auxMod.push(-1);
    }
    if (_.has(orgModule, "Workload")) {
      auxMod.push(workloadStringValuesIndex.indexHash[orgModule.Workload]);
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
        examDates: examDateStringValuesIndex.stringsArray,
        lessonType: lessonsKeysStringValuesIndex.LessonType.stringsArray,
        venues: lessonsKeysStringValuesIndex.Venue.stringsArray,
        weekText: lessonsKeysStringValuesIndex.WeekText.stringsArray
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
        lecturers: lecturersStringValuesIndex.stringsArray,
        prereqs: prereqStringValuesIndex.stringsArray,
        preclusions: preclusionsStringValuesIndex.stringsArray,
        workload: workloadStringValuesIndex.stringsArray,
        departmentToFaculty:
          _.reduce(DEPARTMENT_TO_FACULTY_HASH,
            function(deptToFacHash, faculty, dept) {
              deptToFacHash[departmentStringValuesIndex.indexHash[dept]] =
                departmentStringValuesIndex.indexHash[faculty]
              return deptToFacHash;
            },
            {}
          ),
        lessonTypes: LESSON_TYPES_OBJECT
      }) +
      ";",
    { flag: "w" }
  );
})();
