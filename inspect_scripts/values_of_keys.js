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

var show_all_possible_values_for_key_with_array_of_strings_value = function(
    moduleList, key) {
  console.log();
  console.log("All possible values for Module." + key + ":");
  console.log(_(moduleList)
    .filter(function(mod) { return _.has(mod, key); })
    .map(function(mod) {
      return _.map(mod[key], function(s) { return s.trim(); });
    })
    .flatten()
    .uniq()
    .value()
  );
};

show_all_possible_values_for_key_with_string_value(MODULES_ARRAY,
  "ModuleCredit"
);
show_all_possible_values_for_key_with_string_value(MODULES_ARRAY, "ExamDate");
show_all_possible_values_for_key_with_string_value(MODULES_ARRAY, "Department");
show_all_possible_values_for_key_with_string_value(MODULES_ARRAY, "ModuleCredit");
show_all_possible_values_for_key_with_array_of_strings_value(MODULES_ARRAY,
  "Types"
);
