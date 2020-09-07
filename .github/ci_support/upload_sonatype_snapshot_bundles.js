/**
 * @license
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @fileoverview Uploads Artifact Bundles for SNAPSHOT builds to Sonatype.
 *
 * Based on
 * https://support.sonatype.com/hc/en-us/articles/213465818-How-can-I-programmatically-upload-an-artifact-into-Nexus-2-
 *
 * It seems the only tool capable of uploading SNAPSHOTs in the way Sonatype
 * accepts is Maven. Flogger appears to have encountered the same limitation.
 * At least, Maven doesn't need to conduct the build of the SNAPSHOT files and
 * SNAPSHOTs don't require PGP signatures.
 *
 * Additionally, the section of that article on "Uploading Multiple Artifacts
 * at Once" is outdated; the steps do not work.
 *
 * While researching this, we discovered
 * https://github.com/graknlabs/bazel-distribution which may be a general
 * solution the the problem of deploying from Bazel.
 *
 * See Also:
 *   https://maven.apache.org/plugins/maven-deploy-plugin/deploy-file-mojo.html
 */

const {spawnSync} = require('child_process');
const fs = require('fs');

function main() {
  withTempDir((tmpPath) => {
    const settingsPath = `${tmpPath}/settings.xml`;
    fs.writeFileSync(
        settingsPath, lines([
          `<settings xmlns='http://maven.apache.org/SETTINGS/1.0.0'`,
          `          xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'`,
          `          xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd'>`,
          `  <servers>`,
          `    <server>`,
          `      <id>snapshot-repo-id</id>`,
          `      <username>${getEnvOrThrow('SONATYPE_USERNAME')}</username>`,
          `      <password>${getEnvOrThrow('SONATYPE_PASSWORD')}</password>`,
          `    </server>`,
          `  </servers>`,
          `</settings>`,
        ]),
        {flag: 'wx+'});

    mavenDeploySnapshotFromBazel({
      settingsPath,
      artifactId: `closure-compiler`,
      hasJar: true,
      hasSources: true,
      hasJavadoc: true,
    });
    mavenDeploySnapshotFromBazel({
      settingsPath,
      artifactId: `closure-compiler-unshaded`,
      hasJar: true,
      hasSources: true,
      hasJavadoc: true,
    });
    mavenDeploySnapshotFromBazel({
      settingsPath,
      artifactId: `closure-compiler-externs`,
      hasJar: true,
    });
    mavenDeploySnapshotFromBazel({
      settingsPath,
      artifactId: `closure-compiler-main`,
    });
    mavenDeploySnapshotFromBazel({
      settingsPath,
      artifactId: `closure-compiler-parent`,
    });
  });
}

/**
 * @param {{
 *     settingsPath: string,
 *     artifactId: string,
 *     hasJar: (boolean|undefined),
 *     hasSources: (boolean|undefined),
 *     hasJavadoc: (boolean|undefined),
 * }} fakeName
 */
function mavenDeploySnapshotFromBazel({
  settingsPath,
  artifactId,
  hasJar = undefined,
  hasSources = undefined,
  hasJavadoc = undefined
}) {
  withTempDir((tmpPath) => {
    spawnSync(
        `unzip`, [`bazel-bin/${artifactId}_bundle.jar`, `-d`, tmpPath],
        {stdio: 'inherit'})

    const jarList = [];
    if (hasJar) {
      jarList.push(`-Dfile=${tmpPath}/${artifactId}-1.0-SNAPSHOT.jar`);
    }
    if (hasSources) {
      jarList.push(
          `-Dsources=${tmpPath}/${artifactId}-1.0-SNAPSHOT-sources.jar`);
    }
    if (hasJavadoc) {
      jarList.push(
          `-Djavadoc=${tmpPath}/${artifactId}-1.0-SNAPSHOT-javadoc.jar`);
    }

    spawnSync(
        'mvn',
        [
          `deploy:deploy-file`,
          `--settings=${settingsPath}`,
          `-DgeneratePom=false`,
          `-DrepositoryId=snapshot-repo-id`,
          `-Durl=https://oss.sonatype.org/content/repositories/snapshots/`,
          `-DpomFile=${tmpPath}/pom.xml`,
          ...jarList,
        ],
        {stdio: 'inherit'});
  });
}

/**
 * @param {function(string)} callback
 */
function withTempDir(callback) {
  let {stdout: tmpPath} = spawnSync(`mktemp`, [`-d`]);
  tmpPath = String(tmpPath).trim();
  console.log(`Created temp directory: ${tmpPath}`);

  callback(tmpPath);

  spawnSync(`rm`, [`-R`, tmpPath]);
  console.log(`Deleted temp directory: ${tmpPath}`);
}

/**
 * @param {!Array<string>} args
 * @return {string}
 */
function lines(args) {
  return args.join('\n');
}

/**
 * @param {string} name
 * @return {string}
 */
function getEnvOrThrow(name) {
  const val = process.env[name];
  if (val === undefined) {
    throw new Error(`Environment variable not set: ${name}`);
  }
  return val;
}

main();
