# Downloads required files to the `api-nusmods-stuff` folder

import argparse
import os
import os.path
import requests

API_NUSMODS_STUFF_FOLDER      = "api-nusmods-stuff"
API_NUSMODS_URL_FORMAT_STRING = \
  "http://api.nusmods.com/{acad_year}/{sem}/{file_name}"
ACADEMIC_YEARS                = ["2014-2015"]
API_NUSMODS_FILES             = [
  "facultyDepartments.json", "lessonTypes.json", "modules.json"
]

CHUNK_SIZE = 8192

parser = argparse.ArgumentParser()
parser.add_argument("--force", action="store_true", default=False,
  dest="force",
  help="Re-download existing files from NUSMods API and overwrite them"
)

def download_file(url, destFileName):
  with open(destFileName, "wb") as f:
    r = requests.get(url, stream=True)
    print("Downloading {} to {}".format(url, destFileName))
    if r.status_code == 200:
      for chunk in r.iter_content(CHUNK_SIZE):
        f.write(chunk)
    else:
      print("\"{}\" does not exist".format(url))

args = parser.parse_args()

for acadYear in ACADEMIC_YEARS:
  for sem in [1, 2]:
    semFolder = os.path.join(API_NUSMODS_STUFF_FOLDER, acadYear,
      "sem{}".format(sem))
    if not os.path.exists(semFolder):
      os.makedirs(semFolder, 0755)
    for fileToDownload in API_NUSMODS_FILES:
      destFilePath = os.path.join(semFolder, fileToDownload)
      if not os.path.exists(destFilePath) or args.force:
        remoteFileName = API_NUSMODS_URL_FORMAT_STRING.format(
          acad_year=acadYear, sem=sem, file_name=fileToDownload
        )
        download_file(remoteFileName, destFilePath)
