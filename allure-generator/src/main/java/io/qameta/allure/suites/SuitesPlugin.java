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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.qameta.allure.CommonCsvExportAggregator;
import io.qameta.allure.CommonJsonAggregator;
import io.qameta.allure.CompositeAggregator;
import io.qameta.allure.Constants;
import io.qameta.allure.core.LaunchResults;
import io.qameta.allure.csv.CsvExportSuite;
import io.qameta.allure.entity.TestResult;
import io.qameta.allure.tree.TestResultTree;
import io.qameta.allure.tree.TestResultTreeGroup;
import io.qameta.allure.tree.Tree;
import io.qameta.allure.tree.TreeGroup;
import io.qameta.allure.tree.TreeNode;
import io.qameta.allure.tree.TreeWidgetData;
import io.qameta.allure.tree.TreeWidgetItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static io.qameta.allure.entity.LabelName.PARENT_SUITE;
import static io.qameta.allure.entity.LabelName.SUB_SUITE;
import static io.qameta.allure.entity.LabelName.SUITE;
import static io.qameta.allure.entity.Statistic.comparator;
import static io.qameta.allure.entity.TestResult.comparingByTimeAsc;
import static io.qameta.allure.tree.TreeUtils.calculateStatisticByLeafs;
import static io.qameta.allure.tree.TreeUtils.groupByLabels;

/**
 * Plugin that generates data for Suites tab.
 *
 * @since 2.0
 */
@SuppressWarnings("PMD.UseUtilityClass")
public class SuitesPlugin extends CompositeAggregator {

    private static final Logger LOG = LoggerFactory.getLogger(SuitesPlugin.class);

    private static final String SUITES = "suites";

    /**
     * Name of the json file.
     */
    protected static final String JSON_FILE_NAME = "suites.json";

    /**
     * Name of the csv file.
     */
    protected static final String CSV_FILE_NAME = "suites.csv";

    public SuitesPlugin() {
        super(Arrays.asList(
                new JsonAggregator(), new CsvExportAggregator(), new WidgetAggregator()
        ));
    }

    @SuppressWarnings("PMD.DefaultPackage")
    static /* default */ Tree<TestResult> getData(final List<LaunchResults> launchResults) {
        LOG.info("");
        LOG.info(">>> SuitesPlugin START");
        // TODO: irgendwo hier muß die Limitierung auf 15 passieren .. adding log output for debugging
        LOG.info("getData(): BEFORE launchResults.size() = {}", launchResults.size());
        for (LaunchResults launchResult : launchResults) {
            LOG.info("getData(): launchResult = {}", launchResult);
        }

        // @formatter:off
        final Tree<TestResult> xunit = new TestResultTree(
                SUITES,
            testResult -> groupByLabels(testResult, PARENT_SUITE, SUITE, SUB_SUITE)
        );
        // @formatter:on

        launchResults.stream()
                .map(LaunchResults::getResults)
                .flatMap(Collection::stream)
                .sorted(comparingByTimeAsc())
                .forEach(xunit::add);

        LOG.info("getData(): AFTER xunit.size() = {}", xunit.getChildren().size());
        printTree("xunit", xunit);

        LOG.info(">>> SuitesPlugin END");
        LOG.info("");
        return xunit;
    }

    private static void printTree(String treeName, Tree<?> tree) {
        LOG.info("printTree({}): name = {} class = {}", treeName, tree.getName(), tree.getClass());
        printChildren(tree, tree.getChildren(), 0, " -");
    }

    private static void printChildren(TreeNode parent, List<TreeNode> treeNodes, int level, String levelStr) {
        if (treeNodes != null && treeNodes.size() > 0) {
            LOG.info("treeNodes.getClass() = {}", treeNodes.getClass());
            List<TreeNode> toBeRemoved = new ArrayList<>(3);
            for (TreeNode treeNode : treeNodes) {
                toBeRemoved.addAll(filterChild(parent, treeNode, level));
                TreeGroup treeGroup = null;
                if (treeNode instanceof TreeGroup) {
                    treeGroup = (TreeGroup)treeNode;
                }
                int childCount = treeGroup == null ? 0 : treeGroup.getChildren().size();
                LOG.info("{} level: {} name = {} childCount = {} node = {} class = {}",
                        levelStr, level, treeNode.getName(), childCount, treeNode, treeNode.getClass());
                if (treeGroup != null) {
                    printChildren(treeGroup, treeGroup.getChildren(), level + 1, levelStr + " -");
                }
            }

            // now removing those unwanted nodes
            for (TreeNode treeNode : toBeRemoved) {
                LOG.warn("!!! Removing node: {} name: {}", treeNode, treeNode.getName());
                boolean removed = treeNodes.remove(treeNode);
                if (!removed) {
                    LOG.error("!!! FAILED removing node {} from list {}", treeNode, treeNodes);
                }
            }
        }
    }

    private static List<TreeNode> filterChild(TreeNode parent, TreeNode child, int level) {
        List<TreeNode> toBeRemoved = new ArrayList<>(3);
        if (parent != null && child != null) {
            String parentName = parent.getName();
            String childName = child.getName();
            if (parentName != null && parentName.equals(childName)) {
                toBeRemoved.add(child);
            }
        }
        return toBeRemoved;
    }

    /**
     * Generates tree data.
     */
    private static class JsonAggregator extends CommonJsonAggregator {

        JsonAggregator() {
            super(JSON_FILE_NAME);
        }

        @Override
        protected Tree<TestResult> getData(final List<LaunchResults> launches) {
            return SuitesPlugin.getData(launches);
        }
    }

    /**
     * Generates export data.
     */
    private static class CsvExportAggregator extends CommonCsvExportAggregator<CsvExportSuite> {

        CsvExportAggregator() {
            super(CSV_FILE_NAME, CsvExportSuite.class);
        }

        @Override
        protected List<CsvExportSuite> getData(final List<LaunchResults> launchesResults) {
            return launchesResults.stream()
                    .flatMap(launch -> launch.getResults().stream())
                    .map(CsvExportSuite::new).collect(Collectors.toList());
        }
    }

    /**
     * Generates widget data.
     */
    private static class WidgetAggregator extends CommonJsonAggregator {

        WidgetAggregator() {
            super(Constants.WIDGETS_DIR, JSON_FILE_NAME);
        }

        @Override
        protected Object getData(final List<LaunchResults> launches) {
            final Tree<TestResult> data = SuitesPlugin.getData(launches);
            final List<TreeWidgetItem> items = data.getChildren().stream()
                    .filter(TestResultTreeGroup.class::isInstance)
                    .map(TestResultTreeGroup.class::cast)
                    .map(WidgetAggregator::toWidgetItem)
                    .sorted(Comparator.comparing(TreeWidgetItem::getStatistic, comparator()).reversed())
                    .collect(Collectors.toList());
            return new TreeWidgetData().setItems(items).setTotal(data.getChildren().size());
        }

        private static TreeWidgetItem toWidgetItem(final TestResultTreeGroup group) {
            return new TreeWidgetItem()
                    .setUid(group.getUid())
                    .setName(group.getName())
                    .setStatistic(calculateStatisticByLeafs(group));
        }
    }
}
