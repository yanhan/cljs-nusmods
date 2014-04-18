var _ = require("lodash");
var MODULES_ARRAY = require("./modules.json");

(function() {
  var mcArray;
  console.log("All possible values for Module.ModuleCredit:");
  mcArray = _.uniq(_.reduce(MODULES_ARRAY, function(valueArray, mod) {
    if (_.has(mod, "ModuleCredit")) {
      valueArray.push(mod.ModuleCredit);
    }
    return valueArray;
  }, []).sort(), true);
  console.log(mcArray);
})();
