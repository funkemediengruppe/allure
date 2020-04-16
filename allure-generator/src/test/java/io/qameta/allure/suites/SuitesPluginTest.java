/*
 *  Copyright 2019 Qameta Software OÜ
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.qameta.allure.suites;

import io.qameta.allure.ConfigurationBuilder;
import io.qameta.allure.Issue;
import io.qameta.allure.core.Configuration;
import io.qameta.allure.core.LaunchResults;
import io.qameta.allure.entity.Label;
import io.qameta.allure.entity.Status;
import io.qameta.allure.entity.TestResult;
import io.qameta.allure.entity.Time;
import io.qameta.allure.tree.Tree;
import io.qameta.allure.tree.TreeNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static io.qameta.allure.suites.SuitesPlugin.CSV_FILE_NAME;
import static io.qameta.allure.suites.SuitesPlugin.JSON_FILE_NAME;
import static io.qameta.allure.testdata.TestData.createSingleLaunchResults;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author charlie (Dmitry Baev).
 */
class SuitesPluginTest {

    private static final Logger LOG = LoggerFactory.getLogger(SuitesPluginTest.class);

    private Configuration configuration;
    private Path reportPath;

    @BeforeEach
    void setUp(@TempDir final Path temp) {
        reportPath = temp.resolve("report");
        LOG.info("setUp(): temp = {}", temp);
        configuration = new ConfigurationBuilder().useDefault().build();
        LOG.info("setUp(): temp = {}", temp);
    }

    @Test
    void shouldCreateTree() {

        final Tree<TestResult> tree = SuitesPlugin.getData(getSimpleLaunchResults());
        LOG.info("shouldCreateTree(): tree.getChildren().size() = {}", tree.getChildren().size());

        assertThat(tree.getChildren())
                .hasSize(2)
                .extracting(TreeNode::getName)
                .containsExactlyInAnyOrder("s1", "s2");
    }

    @Issue("587")
    @Issue("572")
    @Test
    void shouldSortByStartTimeAsc() {
        final TestResult first = new TestResult()
                .setName("first")
                .setTime(new Time().setStart(10L));
        final TestResult second = new TestResult()
                .setName("second")
                .setTime(new Time().setStart(12L));
        final TestResult timeless = new TestResult()
                .setName("timeless");

        final Tree<TestResult> tree = SuitesPlugin.getData(
                createSingleLaunchResults(second, first, timeless)
        );
        for (TreeNode child : tree.getChildren()) {
            LOG.info("shouldSortByStartTimeAsc(): child = {}", child);
        }
        assertThat(tree.getChildren())
                .extracting(TreeNode::getName)
                .containsExactly("timeless", "first", "second");
    }

    @Test
    void shouldCreateCsvFile() throws IOException {

        final SuitesPlugin plugin = new SuitesPlugin();

        plugin.aggregate(configuration, getSimpleLaunchResults(), reportPath);
        LOG.info("shouldCreateCsvFile(): reportPath = {}", reportPath);

        assertThat(reportPath.resolve("data").resolve(JSON_FILE_NAME))
                .exists();

        assertThat(reportPath.resolve("data").resolve(CSV_FILE_NAME))
                .exists();
    }

    private List<LaunchResults> getSimpleLaunchResults() {
        final TestResult first = new TestResult()
                .setName("first")
                .setLabels(singletonList(new Label().setName("suite").setValue("s1")));
        final TestResult second = new TestResult()
                .setName("second")
                .setLabels(singletonList(new Label().setName("suite").setValue("s1")));
        final TestResult third = new TestResult()
                .setName("third")
                .setLabels(singletonList(new Label().setName("suite").setValue("s2")));
        return createSingleLaunchResults(second, first, third);
    }

    @Test
    void shouldCreateSuitesJsonFile() throws IOException {

        final SuitesPlugin plugin = new SuitesPlugin();
        LOG.info("shouldCreateSuitesJsonFile(): reportPath = {}", reportPath);
        plugin.aggregate(configuration, getFunkeLaunchResults(), reportPath);
        LOG.info("shouldCreateSuitesJsonFile(): reportPath = {}", reportPath);

        assertThat(reportPath.resolve("data").resolve(JSON_FILE_NAME))
                .exists();

        assertThat(reportPath.resolve("data").resolve(CSV_FILE_NAME))
                .exists();
    }

    private static final String[] PUBS = {
            "HAO", "BMO", "BGZ", "FUZO", "NRW-WAZ", "NRW-IKZ", "BDF", "MULTI",
            "HAO1", "BMO1", "BGZ1", "FUZO1", "NRW-WAZ1", "NRW-IKZ1", "BDF1", "MULTI1",
            "HAO2", "BMO2", "BGZ2", "FUZO2", "NRW-WAZ2", "NRW-IKZ2", "BDF2", "MULTI2",
            "HAO3", "BMO3", "BGZ3", "FUZO3", "NRW-WAZ3", "NRW-IKZ3", "BDF3", "MULTI3",};

    private List<LaunchResults> getFunkeLaunchResults() {
        List<TestResult> testResults = new ArrayList<>();
        int suiteNr = 0;
        int testCounter = 0;
        for (int testIx = 0; testIx < 500; testIx++) {
            LOG.info("getFunkeLaunchResults(): testIx = {}, suiteNr = {}", testIx, suiteNr);
            TestResult testResult = new TestResult()
                    .setName("TestName" + testIx)
                    .setLabels(singletonList(
                            new Label().setName("suite")
                                    .setValue("SuiteName [PROD][" +
                                            PUBS[suiteNr] + "]")
                    ));
            testResult.setStatus(testCounter > 10 ? Status.PASSED : Status.FAILED);
            testResults.add(testResult);
            if (testCounter++ > 20) {
                testCounter = 0;
                suiteNr++;
            }
        }
        TestResult[] array = testResults.toArray(new TestResult[testResults.size()]);
        return createSingleLaunchResults(array);
    }
}
