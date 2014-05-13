#!/bin/bash

# Downloads required files to the `api-nusmods-stuff` folder

YEAR='2013-2014'
SEMESTER=2
API_NUSMODS_FILES=("facultyDepartments.json" "lessonTypes.json" "modules.json")

for f in "${API_NUSMODS_FILES[@]}"
do
  wget "http://api.nusmods.com/$YEAR/$SEMESTER/$f" -O api-nusmods-stuff/$f
done
