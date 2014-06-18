var _ = require("lodash");
var MODULES_ARRAY = require(__dirname + "/../api-nusmods-stuff/modules.json");
var NO_EXAM_DATE_STRING = "0000-01-01T00:00+0800";

(function() {
  var modulesWithoutLessons =
    _(MODULES_ARRAY)
      .filter(function(module) {
        return !_.has(module, "Timetable") || module.Timetable.length === 0;
      })
      .value();

  var modulesWithoutLessonsAndExam =
    _(modulesWithoutLessons)
      .filter(function(module) {
        return !_.has(module, "ExamDate") ||
          module.ExamDate === NO_EXAM_DATE_STRING;
      })
      .map(function(module) {
        return module.ModuleCode;
      })
      .value();

  var modulesWithoutLessonsButHasExam =
    _(modulesWithoutLessons)
      .filter(function(module) {
        return _.has(module, "ExamDate") &&
          module.ExamDate !== NO_EXAM_DATE_STRING;
      })
      .map(function(module) {
        return module.ModuleCode;
      })
      .value();

  console.log("Modules without Lessons and without Exam:");
  _.forEach(modulesWithoutLessonsAndExam, function(moduleCode) {
    console.log("  " + moduleCode);
  });

  console.log("\n\nModules without Lessons but has Exam:");
  _.forEach(modulesWithoutLessonsButHasExam, function(moduleCode) {
    console.log("  " + moduleCode);
  })
    
})();
