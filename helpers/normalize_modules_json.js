// Normalizes some values of a Module object

var fs = require("fs");
var _ = require("lodash");
var SharedGlobals = require("./shared_globals.js");
var NormalizeDepartment = require("./normalize_department.js");
var MODULES_ARRAY = require(__dirname + "/../api-nusmods-stuff/modules.json");

var STRING_KEYS = [
  "ModuleCode", "ModuleTitle", "Department", "ModuleDescription",
  "ModuleCredit", "Workload", "Preclusion", "ExamDate", "Prerequisite",
  "CrossModule", "Corequisite"
];

var ARRAY_OF_STRINGS_KEYS = [
  "Lecturers"
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
    // trim keys with array of strings value
    _.forEach(ARRAY_OF_STRINGS_KEYS, function(aosKey) {
      if (_.has(mod, aosKey)) {
        mod[aosKey] = _.map(mod[aosKey], function(s) { return s.trim(); });
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
    // normalize department string
    if (_.has(mod, "Department")) {
      mod.Department = NormalizeDepartment.normalize_department_string(
        mod.Department
      );
    }
    // convert `ModuleCredit` to integer
    if (_.has(mod, "ModuleCredit")) {
      mod.ModuleCredit = _.parseInt(mod.ModuleCredit);
    }
    // normalize WeekText in Lessons
    if (_.has(mod, "Timetable")) {
      mod.Timetable = _.map(mod.Timetable, function(lesson) {
        if (lesson.WeekText === "EVEN&nbsp;WEEK") {
          lesson.WeekText = "EVEN WEEK";
        } else if (lesson.WeekText === "EVERY&nbsp;WEEK") {
          lesson.WeekText = "EVERY WEEK";
        } else if (lesson.WeekText === "ODD&nbsp;WEEK") {
          lesson.WeekText = "ODD WEEK";
        }
        return lesson;
      });
    }
    return mod;
  });
  // write to `build-temp` directory
  fs.writeFileSync("build-temp/processed_modules.json",
    // 4 space indentation
    JSON.stringify(processedModulesArray, null, "    "),
    { flag: "w" }
  );
})();
