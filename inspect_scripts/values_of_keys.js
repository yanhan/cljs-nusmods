var _ = require("lodash");
var MODULES_ARRAY = require(__dirname + "/../modules.json");

var show_all_possible_values_for_key_with_string_value = function(moduleList,
    key) {
  console.log();
  console.log("All possible values for Module." + key + ":");
  console.log(_.uniq(_.reduce(moduleList, function(valueArray, mod) {
    if (_.has(mod, key)) {
      valueArray.push(mod[key]);
    }
    return valueArray;
  }, []).sort(), true));
};

show_all_possible_values_for_key_with_string_value(MODULES_ARRAY,
  "ModuleCredit"
);
show_all_possible_values_for_key_with_string_value(MODULES_ARRAY, "ExamDate");
