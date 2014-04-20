// Normalizes some values of a Module object

var fs = require("fs");
var _ = require("lodash");
var SharedGlobals = require("./shared_globals.js");
var MODULES_ARRAY = require(__dirname + "/../modules.json");

var STRING_KEYS = [
  "ModuleCode", "ModuleTitle", "Department", "ModuleDescription",
  "ModuleCredit", "Workload", "Preclusion", "ExamDate", "Prerequisite",
  "CrossModule", "Corequisite"
];

(function() {
  var examDateRegex = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/;
  var monthArray = [
    null, "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct",
    "Nov", "Dec"
  ];
  var processedModulesArray = _.map(MODULES_ARRAY, function(mod) {
    var examDate;
    var examDateMatchArray;
    var department;
    var hour;
    // trim keys with string values
    _.forEach(STRING_KEYS, function(stringKey) {
      if (_.has(mod, stringKey)) {
        mod[stringKey] = mod[stringKey].trim();
      }
    });
    // convert `ExamDate` to
    // `<day> <3 character month> <4 digit year> <hour>:<minutes> <AM/PM>`
    // representation
    if (_.has(mod, "ExamDate")) {
      examDate = mod.ExamDate;
      if (examDate === SharedGlobals.ORIGINAL_NO_EXAM_DATE_STRING) {
        mod.ExamDate = SharedGlobals.PROCESSED_NO_EXAM_DATE_STRING ;
      } else {
        examDateMatchArray = examDate.match(examDateRegex);
        if (examDateMatchArray !== null) {
          mod.ExamDate = _.parseInt(examDateMatchArray[3], 10) + " " +
            monthArray[_.parseInt(examDateMatchArray[2])] + " " +
            examDateMatchArray[1] + " ";
          hour = _.parseInt(examDateMatchArray[4], 10);
          if (hour > 12) {
            mod.ExamDate += (hour - 12);
          } else {
            mod.ExamDate += hour;
          }
          mod.ExamDate += ":" + examDateMatchArray[5] +
            (hour >= 12 ? "PM" : "AM");
        }
      }
    }
    // Department - we convert the strings to title case.
    // For instance, "COMPUTER SCIENCE" is converted to "Computer Science".
    //
    // Special Cases:
    //   "CTR FOR ENGLISH LANGUAGE COMMUNICATION"
    //     --> Center for English Language Communication
    //   "DUKE-NUS GRADUATE MEDICAL SCHOOL S'PORE"
    //     --> Duke NUS Graduate Medical School Singapore
    //   "NURSING/ALICE LEE CTR FOR NURSING STUD"
    //     --> Nursing / Alice Lee Center for Nursing Studies
    //   "NUS GRAD SCH FOR INTEGRATIVE SCI & ENGG"
    //     --> NUS Graduate School for Integrative Sciences and Engineering
    //   "SINGAPORE-MIT ALLIANCE"
    //     --> Singapore-MIT Alliance
    //   "YALE-NUS COLLEGE"
    //     --> Yale-NUS College
    if (_.has(mod, "Department")) {
      department = mod.Department;
      if (department === "CTR FOR ENGLISH LANGUAGE COMMUNICATION") {
        department = "Center for English Language Communication";
      } else if (department === "DUKE-NUS GRADUATE MEDICAL SCHOOL S'PORE") {
        department = "Duke-NUS Graduate Medical School Singapore";
      } else if (department === "NURSING/ALICE LEE CTR FOR NURSING STUD") {
        department = "Nursing / Alice Lee Center for Nursing Studies";
      } else if (department === "NUS GRAD SCH FOR INTEGRATIVE SCI & ENGG") {
        department = "NUS Graduate School for Integrative Sciences and Engineering";
      } else if (department === "SINGAPORE-MIT ALLIANCE") {
        department = "Singapore-MIT Alliance";
      } else if (department === "YALE-NUS COLLEGE") {
        department = "Yale-NUS College";
      } else {
        department = department.replace(/\w\S*/g, function(txt) {
          txt = txt.trim();
          return txt.charAt(0).toUpperCase() + txt.slice(1).toLowerCase();
        });
      }
      mod.Department = department;
    }

    // convert `ModuleCredit` to integer
    if (_.has(mod, "ModuleCredit")) {
      mod.ModuleCredit = _.parseInt(mod.ModuleCredit);
    }
    return mod;
  });
  fs.writeFileSync("processed_modules.json",
    // 4 space indentation
    JSON.stringify(processedModulesArray, null, "    "),
    { flag: "w" }
  );
})();
