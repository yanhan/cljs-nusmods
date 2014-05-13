var _ = require("lodash");
var MODULES_ARRAY = require(__dirname + "/../api-nusmods-stuff/modules.json");
var moduleCodeRegex = /^[A-Z]+\d{4}[A-Z]*$/;

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
