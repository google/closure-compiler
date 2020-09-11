#!/usr/bin/env node

/*
 * Copyright 2020 The Closure Compiler Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
 * solution the problem of deploying from Bazel.
 *
 * See Also:
 *   https://maven.apache.org/plugins/maven-deploy-plugin/deploy-file-mojo.html
 */

const {execSync} = require('child_process');
const fs = require('fs');

const /** !Array<string> */ ARITFACT_TO_UPLOAD = [
  `closure-compiler`,
  `closure-compiler-unshaded`,
  `closure-compiler-externs`,
  `closure-compiler-main`,
  `closure-compiler-parent`,
];

const /** string */ SNAPSHOT_REPO_ID = 'snapshot-repo-id';

/** Main */
function main() {
  withTempDir((tmpPath) => {
    const settingsPath = `${tmpPath}/settings.xml`;
    fs.writeFileSync(
        settingsPath,
        lines(
            `<settings xmlns='http://maven.apache.org/SETTINGS/1.0.0'`,
            `          xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'`,
            `          xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd'>`,
            `  <servers>`,
            `    <server>`,
            `      <id>${SNAPSHOT_REPO_ID}</id>`,
            `      <username>${getEnvOrThrow('SONATYPE_USERNAME')}</username>`,
            `      <password>${getEnvOrThrow('SONATYPE_PASSWORD')}</password>`,
            `    </server>`,
            `  </servers>`,
            `</settings>`,
            ),
        {flag: 'wx+'});

    for (const id of ARITFACT_TO_UPLOAD) {
      mavenDeploySnapshotFromBazel(settingsPath, id);
    }
  });
}

/**
 * @param {string} settingsPath
 * @param {string} artifactId
 */
function mavenDeploySnapshotFromBazel(settingsPath, artifactId) {
  withTempDir((tmpPath) => {
    execSync(
        `unzip bazel-bin/${artifactId}_bundle.jar -d ${tmpPath}`,
        {stdio: 'inherit'});
    execSync(
        spaces(
            'mvn',
            `  deploy:deploy-file`,
            `  --settings=${settingsPath}`,
            `  -DgeneratePom=false`,
            `  -DrepositoryId=${SNAPSHOT_REPO_ID}`,
            `  -Durl=https://oss.sonatype.org/content/repositories/snapshots/`,
            `  -DpomFile=${tmpPath}/pom.xml`,
            ...assembleJarArgs(artifactId, tmpPath),
            ),
        {stdio: 'inherit'});
  });
}

/**
 * Create a list of JAR arguments for `mvn deploy:deploy-file`.
 *
 * Example: -Dfile=/some/tmp/path/some-artifact-1.0-SNAPSHOT.jar
 *
 * @param {string} artifactId
 * @param {string} unpackPath
 * @return {!Array<string>}
 */
function assembleJarArgs(artifactId, unpackPath) {
  const baseJarPath = `${unpackPath}/${artifactId}-1.0-SNAPSHOT`;

  const mvnArgToJarName = new Map();
  for (const [arg, suffix] of MVN_ARG_TO_JAR_SUFFIX) {
    const fullJarPath = `${baseJarPath}${suffix}.jar`;
    if (fs.existsSync(fullJarPath)) {
      mvnArgToJarName.set(arg, fullJarPath);
    }
  }

  if (!mvnArgToJarName.get(D_FILE)) {
    // -Dfile is required but not all bundles have a JAR, so default to the POM.
    mvnArgToJarName.set(D_FILE, `${unpackPath}/pom.xml`);
  }

  return [...mvnArgToJarName].map(([arg, path]) => `${arg}=${path}`);
}

const /** string */ D_FILE = `-Dfile`;

const /** !Map<string, string> */ MVN_ARG_TO_JAR_SUFFIX = new Map([
  [D_FILE, ``],
  [`-Dsources`, `-sources`],
  [`-Djavadoc`, `-javadoc`],
]);

/**
 * @param {function(string)} callback
 */
function withTempDir(callback) {
  const tmpPath = String(execSync(`mktemp -d`)).trim();
  callback(tmpPath);
  execSync(`rm -R ${tmpPath}`);
}

/**
 * @param {...string} args
 * @return {string}
 */
function lines(...args) {
  return args.join('\n');
}

/**
 * @param {...string} args
 * @return {string}
 */
function spaces(...args) {
  return args.join(' ');
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
