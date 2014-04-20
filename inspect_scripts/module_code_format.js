var _ = require("lodash");
var MODULES_ARRAY = require(__dirname + "/../modules.json");
var moduleCodeRegex = /^\D+\d{4}\D*$/;

_.forEach(MODULES_ARRAY, function(mod) {
  var matchArray;
  var moduleCode;
  if (_.has(mod, "ModuleCode")) {
    moduleCode = mod.ModuleCode.trim();
    matchArray = moduleCode.match(moduleCodeRegex);
    if (matchArray === null) {
      console.log("ModuleCode \"" + moduleCode + "\" does not match the regex");
    }
  }
});
