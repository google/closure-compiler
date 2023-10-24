"""Check and generate the licenses for third party dependencies.

This script generates the THIRD_PARTY_NOTICES file, which is the licenses
for all our external dependencies. It reads the maven_artifacts.bzl
file, compares the pom.xml and gradle files with the jar file list,
and then generates the license file.
"""

import argparse
import sys
import xml.etree.ElementTree as ET
import requests

# Constants
GITHUB_PREFIX = 'https://github.com/'
RAW_FILE_PREFIX = 'https://raw.githubusercontent.com/'

POM_FILE_SUFFIX = '.xml'
GRADLE_FILE_SUFFIX = '.gradle'

SPACER = """

===============================================================================
===============================================================================
===============================================================================

"""


def get_file_from_github(github_url):
  raw_file_url_with_blob = github_url.replace(GITHUB_PREFIX, RAW_FILE_PREFIX)

  raw_file_url = ''
  branch_roots = ['master/', 'main/']

  for br in branch_roots:
    if br in raw_file_url_with_blob:
      split_url = raw_file_url_with_blob.split(br)
      raw_file_url = split_url[0][:-5] + br + split_url[1]
      break

  if not raw_file_url:
    return None

  response = requests.get(raw_file_url)

  if response.status_code != 200:
    return None

  return response.text


def get_pom_artifact_name(pom_xml_github_url):
  pom_xml_content = get_file_from_github(pom_xml_github_url)
  if pom_xml_content is None:
    print('Github Returned Error status when reading :', pom_xml_github_url)
    sys.exit(1)

  root = ET.fromstring(pom_xml_content)
  ns = {'mvn': 'http://maven.apache.org/POM/4.0.0'}

  parent = root.find('mvn:parent', ns)
  group_id = parent.find('mvn:groupId', ns).text
  artifact_id = root.find('mvn:artifactId', ns).text

  return '%s:%s' % (group_id, artifact_id)


def get_grad_rval(line):
  return (line.split()[-1]).strip("'")


def get_gradle_artifact_name(url):
  content = get_file_from_github(url)

  group_id = ''
  artifact_id = ''

  for line in content.splitlines():
    if 'groupId' in line:
      group_id = get_grad_rval(line)
    if 'artifactId' in line:
      artifact_id = get_grad_rval(line)

  return '%s:%s' % (group_id, artifact_id)


def get_github_root_url(url):
  branch_roots = ['master/', 'main/']
  github_root = ''

  for br in branch_roots:
    if br in url:
      split_url = url.split(br)
      github_root = split_url[0] + br

  if not github_root:
    print(
        'Cannot find the main branch root directory for the pom/gradle ',
        'file: ',
        url,
    )
    sys.exit(1)

  return github_root


def get_license_from_pom(url):
  github_branch_root = get_github_root_url(url)
  license_filenames = ['LICENSE', 'COPYING']

  license_content = None
  license_url = ''

  for filename in license_filenames:
    license_url = github_branch_root + filename
    license_content = get_file_from_github(license_url)
    if license_content is not None:
      break

  if license_content is None:
    print('Cannot get license information for pom/gradle file: ', url)
    sys.exit(1)

  return (license_url, license_content)


def main():
  parser = argparse.ArgumentParser(
      prog='ThirdPartyLicenseTest',
      description='Checks if the third party licenses are up to date',
      epilog='',
  )

  parser.add_argument(
      'maven_artifacts_file',
      help='path to the bzl file with the list of maven artifacts.',
  )
  parser.add_argument(
      'third_party_notices_file', help='path to the THIRD_PARTY_NOTICES file.'
  )
  parser.add_argument(
      '-u',
      '--update',
      action='store_true',
      help='Update THIRD_PARTY_NOTICES with the new content.',
  )
  args = parser.parse_args()

  # Read maven_artifacts.bzl
  bzl_file_contents = open(args.maven_artifacts_file).read()

  # Work around a python3 bug with exec and local variables
  ldict = {}
  exec(bzl_file_contents, globals(), ldict)  # pylint: disable=exec-used
  maven_artifacts = ldict['MAVEN_ARTIFACTS']
  pom_gradle_filelist = ldict['ORDERED_POM_OR_GRADLE_FILE_LIST']

  # Compare list lengths
  if len(maven_artifacts) != len(pom_gradle_filelist):
    print(
        'artifact list length and pom/gradle file list length is not equal. ',
        'Please check the file :',
        args.maven_artifacts_file,
    )
    sys.exit(1)

  package_name_to_pom = {}

  # Compare pom/gradle artifact names with maven jar files
  artifact_list_from_github = []
  for url in pom_gradle_filelist:
    if url.endswith(POM_FILE_SUFFIX):
      tmp = get_pom_artifact_name(url)
      artifact_list_from_github.append(tmp)
      package_name_to_pom[tmp] = url
    elif url.endswith(GRADLE_FILE_SUFFIX):
      tmp = get_gradle_artifact_name(url)
      artifact_list_from_github.append(tmp)
      package_name_to_pom[tmp] = url
    else:
      print('Neither a Pom, nor a Gradle a file found in the list. exiting.')
      sys.exit(1)

  artifact_list_from_maven = []
  for artifact_name in maven_artifacts:
    split_artifact_name = artifact_name.split(':')
    artifact_list_from_maven.append(
        '%s:%s' % (split_artifact_name[0], split_artifact_name[1])
    )

  if artifact_list_from_github != artifact_list_from_maven:
    print('Artifact names from github and maven are different.')
    print('Github artifact list: ', artifact_list_from_github)
    print('Maven artifact list: ', artifact_list_from_maven)
    sys.exit(1)

  license_url_to_package = {}
  license_url_to_content = {}
  # Create a dictionary of license names to maven jar files
  for package in artifact_list_from_github:
    (license_url, license_content) = get_license_from_pom(
        package_name_to_pom[package]
    )
    if license_url in license_url_to_package:
      license_url_to_package[license_url].append(package)
    else:
      license_url_to_package[license_url] = [package]
      license_url_to_content[license_url] = license_content

  # Create THIRD_PARTY_NOTICES
  third_party_notices_content = ''
  for license_url in license_url_to_package:
    third_party_notices_content += 'License for package(s): ' + str(
        license_url_to_package[license_url]
    )
    third_party_notices_content += '\n\n'
    third_party_notices_content += license_url_to_content[license_url]
    third_party_notices_content += SPACER

  # Compare or Write out THIRD_PARTY_NOTICES file
  if args.update:
    fh = open(args.third_party_notices_file, 'w')
    fh.write(third_party_notices_content)
    fh.close()
    sys.exit()

  else:
    old_third_party_notices_content = open(args.third_party_notices_file).read()
    if old_third_party_notices_content == third_party_notices_content:
      sys.exit()
    else:
      print('Changes detected in THIRD_PARTY_NOTICES file!')
      print('Please run the following command to update the license file: \n')
      print(' python3 \\')
      print('     third_party/third_party_license_test.py \\')
      print('     third_party/maven_artifacts.bzl \\')
      print('     third_party/THIRD_PARTY_NOTICES --update')
      sys.exit(1)


if __name__ == '__main__':
  main()
