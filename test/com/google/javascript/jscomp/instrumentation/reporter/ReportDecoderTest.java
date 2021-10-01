/*
 * Copyright 2021 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.instrumentation.reporter;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.instrumentation.reporter.proto.FileProfile;
import com.google.javascript.jscomp.instrumentation.reporter.proto.InstrumentationPoint;
import com.google.javascript.jscomp.instrumentation.reporter.proto.InstrumentationPointStats;
import com.google.javascript.jscomp.instrumentation.reporter.proto.InstrumentationPointStats.Presence;
import com.google.javascript.jscomp.instrumentation.reporter.proto.ReportProfile;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ReportDecoderTest {

  @Test
  public void testCreateProfileOfStaticallyUsedCode() {
    InstrumentationPoint point1 =
        InstrumentationPoint.newBuilder().setFileName("file1").setFunctionName("fun1").build();
    InstrumentationPoint point2 =
        InstrumentationPoint.newBuilder().setFileName("file1").setFunctionName("fun2").build();
    InstrumentationPoint point3 =
        InstrumentationPoint.newBuilder().setFileName("file2").setFunctionName("fun3").build();
    InstrumentationPoint point4 =
        InstrumentationPoint.newBuilder().setFileName("file2").setFunctionName("fun4").build();
    ImmutableMap<String, InstrumentationPoint> mapping =
        ImmutableMap.of("abc1", point1, "def2", point2, "ghi3", point3, "jkl4", point4);
    String fileContent =
        // point1 used
        "ist.push('abc1');"
            // unrelated push that looks like point2
            + "foo.push('def2');"
            // point3 used with different quotes
            + "ist.push(\"ghi3\");"
            // unknown point
            + "ist.push('bla');"
            // point4 but used outside of push
            + "someCall('jkl14');";

    ReportProfile profile =
        ReportDecoder.createProfileOfStaticallyUsedCode(mapping, fileContent, "ist");
    assertThat(profile)
        .isEqualTo(
            ReportProfile.newBuilder()
                .addFileProfile(
                    FileProfile.newBuilder()
                        .setFileName("file1")
                        .addInstrumentationPointsStats(statsWithPresence(point1, Presence.PRESENT))
                        .addInstrumentationPointsStats(
                            statsWithPresence(point2, Presence.STATICALLY_REMOVED)))
                .addFileProfile(
                    FileProfile.newBuilder()
                        .setFileName("file2")
                        .addInstrumentationPointsStats(statsWithPresence(point3, Presence.PRESENT))
                        .addInstrumentationPointsStats(
                            statsWithPresence(point4, Presence.STATICALLY_REMOVED)))
                .build());
  }

  @Test
  public void testMergeProfilesCheckTimesExecuted() {
    InstrumentationPoint point1 =
        InstrumentationPoint.newBuilder()
            .setFileName("file1")
            .setFunctionName("fun1")
            .setLineNumber(1)
            .build();
    InstrumentationPoint point2 =
        InstrumentationPoint.newBuilder()
            .setFileName("file1")
            .setFunctionName("fun2")
            .setLineNumber(2)
            .build();
    InstrumentationPoint point3 =
        InstrumentationPoint.newBuilder()
            .setFileName("file2")
            .setFunctionName("fun3")
            .setLineNumber(3)
            .build();
    InstrumentationPoint point4 =
        InstrumentationPoint.newBuilder()
            .setFileName("file2")
            .setFunctionName("fun4")
            .setLineNumber(4)
            .build();

    ReportProfile profile1 =
        ReportProfile.newBuilder()
            .addFileProfile(
                FileProfile.newBuilder()
                    .setFileName("file1")
                    .addInstrumentationPointsStats(statsWithTimeExecuted(point1, 5))
                    .addInstrumentationPointsStats(statsWithTimeExecuted(point2, 10)))
            .build();
    ReportProfile profile2 =
        ReportProfile.newBuilder()
            .addFileProfile(
                FileProfile.newBuilder()
                    .setFileName("file1")
                    .addInstrumentationPointsStats(statsWithTimeExecuted(point1, 15)))
            .addFileProfile(
                FileProfile.newBuilder()
                    .setFileName("file2")
                    .addInstrumentationPointsStats(statsWithTimeExecuted(point3, 20)))
            .build();

    ReportProfile profile3 =
        ReportProfile.newBuilder()
            .addFileProfile(
                FileProfile.newBuilder()
                    .setFileName("file2")
                    .addInstrumentationPointsStats(statsWithTimeExecuted(point3, 25))
                    .addInstrumentationPointsStats(statsWithTimeExecuted(point4, 30)))
            .build();

    ReportProfile mergedReport =
        ReportProfile.newBuilder()
            .addFileProfile(
                FileProfile.newBuilder()
                    .setFileName("file1")
                    .addInstrumentationPointsStats(statsWithTimeExecuted(point1, 20))
                    .addInstrumentationPointsStats(statsWithTimeExecuted(point2, 10)))
            .addFileProfile(
                FileProfile.newBuilder()
                    .setFileName("file2")
                    .addInstrumentationPointsStats(statsWithTimeExecuted(point3, 45))
                    .addInstrumentationPointsStats(statsWithTimeExecuted(point4, 30)))
            .build();
    assertThat(ReportDecoder.mergeProfiles(Arrays.asList(profile1, profile2, profile3)))
        .isEqualTo(mergedReport);
  }

  @Test
  public void testMergeReportProfilesCheckPresence() {
    InstrumentationPoint point1 =
        InstrumentationPoint.newBuilder()
            .setFileName("file1")
            .setFunctionName("fun1")
            .setLineNumber(1)
            .build();
    InstrumentationPoint point2 =
        InstrumentationPoint.newBuilder()
            .setFileName("file1")
            .setFunctionName("fun2")
            .setLineNumber(2)
            .build();
    InstrumentationPoint point3 =
        InstrumentationPoint.newBuilder()
            .setFileName("file1")
            .setFunctionName("fun3")
            .setLineNumber(3)
            .build();

    ReportProfile profile1 =
        ReportProfile.newBuilder()
            .addFileProfile(
                FileProfile.newBuilder()
                    .setFileName("file1")
                    .addInstrumentationPointsStats(
                        statsWithPresence(point1, Presence.PRESENCE_UNKNOWN))
                    .addInstrumentationPointsStats(
                        statsWithPresence(point2, Presence.PRESENCE_UNKNOWN))
                    .addInstrumentationPointsStats(
                        statsWithPresence(point3, Presence.PRESENCE_UNKNOWN)))
            .build();
    ReportProfile profile2 =
        ReportProfile.newBuilder()
            .addFileProfile(
                FileProfile.newBuilder()
                    .setFileName("file1")
                    .addInstrumentationPointsStats(
                        statsWithPresence(point1, Presence.STATICALLY_REMOVED))
                    .addInstrumentationPointsStats(
                        statsWithPresence(point2, Presence.STATICALLY_REMOVED)))
            .build();
    ReportProfile profile3 =
        ReportProfile.newBuilder()
            .addFileProfile(
                FileProfile.newBuilder()
                    .setFileName("file1")
                    .addInstrumentationPointsStats(statsWithPresence(point1, Presence.PRESENT)))
            .build();

    ReportProfile mergedReport =
        ReportProfile.newBuilder()
            .addFileProfile(
                FileProfile.newBuilder()
                    .setFileName("file1")
                    .addInstrumentationPointsStats(statsWithPresence(point1, Presence.PRESENT))
                    .addInstrumentationPointsStats(
                        statsWithPresence(point2, Presence.STATICALLY_REMOVED))
                    .addInstrumentationPointsStats(
                        statsWithPresence(point3, Presence.PRESENCE_UNKNOWN)))
            .build();
    assertThat(ReportDecoder.mergeProfiles(Arrays.asList(profile1, profile3, profile2)))
        .isEqualTo(mergedReport);
  }

  private InstrumentationPointStats statsWithTimeExecuted(InstrumentationPoint point, long value) {
    return InstrumentationPointStats.newBuilder()
        .setPoint(point)
        .setTimesExecuted(value)
        .setPointPresence(Presence.PRESENCE_UNKNOWN)
        .build();
  }

  private InstrumentationPointStats statsWithPresence(InstrumentationPoint point, Presence value) {
    return InstrumentationPointStats.newBuilder()
        .setPoint(point)
        .setPointPresence(value)
        .setTimesExecuted(0)
        .build();
  }
}
