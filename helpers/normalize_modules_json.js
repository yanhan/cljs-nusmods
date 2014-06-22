// Normalizes some values of a Module object

var fs = require("fs");
var _ = require("lodash");
var argv = require("minimist")(process.argv.slice(2));
// TODO: Centralize this information somewhere
var ACAD_YEAR = argv["acad-year"] || "2013-2014";
var SEM = argv.sem || 2;
var API_NUSMODS_FOLDER = __dirname +
  "/../api-nusmods-stuff/" + ACAD_YEAR + "/sem" + SEM;

var SharedGlobals = require("./shared_globals.js");
var NormalizeDepartment = require("./normalize_department.js");
var MODULES_ARRAY = require(API_NUSMODS_FOLDER + "/modules.json");

var STRING_KEYS = [
  "ModuleCode", "ModuleTitle", "Department", "ModuleDescription",
  "ModuleCredit", "Workload", "Preclusion", "ExamDate", "Prerequisite",
  "CrossModule", "Corequisite"
];

var ARRAY_OF_STRINGS_KEYS = [
  "Lecturers"
];

(function() {
  var examDateRegex = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})\+0800$/;
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
    if (_.has(mod, "ExamDate")) {
      examDate = mod.ExamDate;
      if (examDate === SharedGlobals.ORIGINAL_NO_EXAM_DATE_STRING) {
        // used processed no exam string for no exams
        mod.ExamDate = SharedGlobals.PROCESSED_NO_EXAM_DATE_STRING ;
      } else if (examDate.match(examDateRegex) === null) {
        throw {
          message: "Exam Date string \"" + examDate +
            "\" is not of the desired format",
          name: "ExamDateFormatException"
        };
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
        var weeksArr;
        var reduceResult;
        if (lesson.WeekText === "EVEN&nbsp;WEEK") {
          lesson.WeekText = "Even Weeks";
        } else if (lesson.WeekText === "EVERY&nbsp;WEEK" ||
            lesson.WeekText === "EVERY WEEK") {
          lesson.WeekText = "Every Week";
        } else if (lesson.WeekText === "ODD&nbsp;WEEK") {
          lesson.WeekText = "Odd Weeks";
        } else {
          weeksArr = _(lesson.WeekText.split(","))
            .map(function(x) {
              return parseInt(x, 10);
            })
            .value()
            .sort(function(a, b) { return a - b; });
          reduceResult = _.reduce(weeksArr.slice(1),
            function(result, weekNum) {
              if (result.rangeEnd + 1 === weekNum) {
                return _.assign(result, { rangeEnd: weekNum });
              } else {
                result.weekPairArr.push([result.rangeStart, result.rangeEnd]);
                return _.assign(result,
                  { rangeStart: weekNum, rangeEnd: weekNum });
              }
            },
            {
              rangeStart: weeksArr[0],
              rangeEnd: weeksArr[0],
              weekPairArr: []
            });
          reduceResult.weekPairArr.push([reduceResult.rangeStart,
            reduceResult.rangeEnd]);
          lesson.WeekText = "Weeks " +
            _.map(reduceResult.weekPairArr, function(weekPair) {
              var start = weekPair[0];
              var end = weekPair[1];
              if (start === end) {
                return start;
              } else {
                return start + "-" + end;
              }
            })
            .join(", ");
        }
        return lesson;
      });
    }
    return mod;
  });

  fs.writeFileSync(API_NUSMODS_FOLDER + "/processed_modules.json",
    // 4 space indentation
    JSON.stringify(processedModulesArray, null, "    "),
    { flag: "w" }
  );
})();
