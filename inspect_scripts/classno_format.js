var _ = require("lodash");
var MODULES_JSON = require(__dirname + "/../api-nusmods-stuff/modules.json");
var classNoRegex = /^[A-Z0-9]+$/;

var classNoFailRegex = _(MODULES_JSON)
  .filter(function(module) {
    return _.has(module, "Timetable");
  })
  .map(function(module) {
    return module.Timetable;
  })
  .flatten()
  .map(function(lesson) {
    return lesson.ClassNo;
  })
  .sort()
  .uniq()
  .filter(function(classNo) {
    return classNoRegex.exec(classNo) === null;
  })
  .value();

if (classNoFailRegex.length === 0) {
  console.log("All ClassNo (lesson group labels) match the regex in the code.");
} else {
  console.log("The following ClassNo (lesson group labels) do not match the " +
    "regex in the code:"
  );
  console.log(classNoFailRegex);
}
