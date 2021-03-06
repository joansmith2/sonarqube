/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.computation.step;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import org.apache.commons.lang.ObjectUtils;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.utils.KeyValueFormat;
import org.sonar.batch.protocol.output.BatchReport;
import org.sonar.server.computation.batch.BatchReportReader;
import org.sonar.server.computation.component.Component;
import org.sonar.server.computation.component.TreeRootHolder;
import org.sonar.server.computation.formula.CreateMeasureContext;
import org.sonar.server.computation.formula.FileAggregateContext;
import org.sonar.server.computation.formula.Formula;
import org.sonar.server.computation.formula.FormulaExecutorComponentVisitor;
import org.sonar.server.computation.formula.counter.IntVariationValue;
import org.sonar.server.computation.measure.Measure;
import org.sonar.server.computation.measure.MeasureRepository;
import org.sonar.server.computation.measure.MeasureVariations;
import org.sonar.server.computation.metric.Metric;
import org.sonar.server.computation.metric.MetricRepository;
import org.sonar.server.computation.period.Period;
import org.sonar.server.computation.period.PeriodsHolder;

import static org.sonar.server.computation.measure.Measure.newMeasureBuilder;

/**
 * Computes measures related to the New Coverage. These measures do not have values, only variations.
 */
public class NewCoverageMeasuresStep implements ComputationStep {
  private final TreeRootHolder treeRootHolder;
  private final PeriodsHolder periodsHolder;
  private final BatchReportReader batchReportReader;
  private final MetricRepository metricRepository;
  private final MeasureRepository measureRepository;

  public NewCoverageMeasuresStep(TreeRootHolder treeRootHolder, PeriodsHolder periodsHolder, BatchReportReader batchReportReader,
    MeasureRepository measureRepository, final MetricRepository metricRepository) {
    this.treeRootHolder = treeRootHolder;
    this.periodsHolder = periodsHolder;
    this.batchReportReader = batchReportReader;
    this.metricRepository = metricRepository;
    this.measureRepository = measureRepository;
  }

  @Override
  public void execute() {
    FormulaExecutorComponentVisitor.newBuilder(metricRepository, measureRepository)
      .withVariationSupport(periodsHolder)
      .buildFor(ImmutableList.<Formula>of(
        // File coverage
        new NewCoverageOnFileFormula(batchReportReader),
        // IT File coverage
        new NewCoverageOnITFileFormula(batchReportReader),
        // Overall coverage
        new NewOverallCoverageFormula(batchReportReader)
        ))
      .visit(treeRootHolder.getRoot());
  }

  @Override
  public String getDescription() {
    return "Computation of New Coverage measures";
  }

  private static class NewCoverageOnFileFormula extends NewCoverageFormula {
    public NewCoverageOnFileFormula(BatchReportReader batchReportReader) {
      super(batchReportReader,
        new NewCoverageInputMetricKeys(
          CoreMetrics.COVERAGE_LINE_HITS_DATA_KEY, CoreMetrics.CONDITIONS_BY_LINE_KEY, CoreMetrics.COVERED_CONDITIONS_BY_LINE_KEY
        ),
        new NewCoverageOutputMetricKeys(
          CoreMetrics.NEW_LINES_TO_COVER_KEY, CoreMetrics.NEW_UNCOVERED_LINES_KEY,
          CoreMetrics.NEW_CONDITIONS_TO_COVER_KEY, CoreMetrics.NEW_UNCOVERED_CONDITIONS_KEY
        ));
    }
  }

  private static class NewCoverageOnITFileFormula extends NewCoverageFormula {
    public NewCoverageOnITFileFormula(BatchReportReader batchReportReader) {
      super(batchReportReader,
        new NewCoverageInputMetricKeys(
          CoreMetrics.IT_COVERAGE_LINE_HITS_DATA_KEY, CoreMetrics.IT_CONDITIONS_BY_LINE_KEY, CoreMetrics.IT_COVERED_CONDITIONS_BY_LINE_KEY
        ),
        new NewCoverageOutputMetricKeys(
          CoreMetrics.NEW_IT_LINES_TO_COVER_KEY, CoreMetrics.NEW_IT_UNCOVERED_LINES_KEY,
          CoreMetrics.NEW_IT_CONDITIONS_TO_COVER_KEY, CoreMetrics.NEW_IT_UNCOVERED_CONDITIONS_KEY
        ));
    }
  }

  private static class NewOverallCoverageFormula extends NewCoverageFormula {
    public NewOverallCoverageFormula(BatchReportReader batchReportReader) {
      super(batchReportReader,
        new NewCoverageInputMetricKeys(
          CoreMetrics.OVERALL_COVERAGE_LINE_HITS_DATA_KEY, CoreMetrics.OVERALL_CONDITIONS_BY_LINE_KEY, CoreMetrics.OVERALL_COVERED_CONDITIONS_BY_LINE_KEY
        ),
        new NewCoverageOutputMetricKeys(
          CoreMetrics.NEW_OVERALL_LINES_TO_COVER_KEY, CoreMetrics.NEW_OVERALL_UNCOVERED_LINES_KEY,
          CoreMetrics.NEW_OVERALL_CONDITIONS_TO_COVER_KEY, CoreMetrics.NEW_OVERALL_UNCOVERED_CONDITIONS_KEY
        ));
    }
  }

  public static class NewCoverageFormula implements Formula<NewCoverageCounter> {
    private final BatchReportReader batchReportReader;
    private final NewCoverageInputMetricKeys inputMetricKeys;
    private final NewCoverageOutputMetricKeys outputMetricKeys;

    public NewCoverageFormula(BatchReportReader batchReportReader, NewCoverageInputMetricKeys inputMetricKeys, NewCoverageOutputMetricKeys outputMetricKeys) {
      this.batchReportReader = batchReportReader;
      this.inputMetricKeys = inputMetricKeys;
      this.outputMetricKeys = outputMetricKeys;
    }

    @Override
    public NewCoverageCounter createNewCounter() {
      return new NewCoverageCounter(batchReportReader, inputMetricKeys);
    }

    @Override
    public Optional<Measure> createMeasure(NewCoverageCounter counter, CreateMeasureContext context) {
      MeasureVariations.Builder builder = MeasureVariations.newMeasureVariationsBuilder();
      for (Period period : context.getPeriods()) {
        if (counter.hasNewCode(period)) {
          int value = computeValueForMetric(counter, period, context.getMetric());
          builder.setVariation(period, value);
        }
      }
      if (builder.isEmpty()) {
        return Optional.absent();
      }
      return Optional.of(newMeasureBuilder().setVariations(builder.build()).createNoValue());
    }

    private int computeValueForMetric(NewCoverageCounter counter, Period period, Metric metric) {
      if (metric.getKey().equals(outputMetricKeys.getNewLinesToCover())) {
        return counter.getNewLines(period);
      }
      if (metric.getKey().equals(outputMetricKeys.getNewUncoveredLines())) {
        return counter.getNewLines(period) - counter.getNewCoveredLines(period);
      }
      if (metric.getKey().equals(outputMetricKeys.getNewConditionsToCover())) {
        return counter.getNewConditions(period);
      }
      if (metric.getKey().equals(outputMetricKeys.getNewUncoveredConditions())) {
        return counter.getNewConditions(period) - counter.getNewCoveredConditions(period);
      }
      throw new IllegalArgumentException("Unsupported metric " + metric.getKey());
    }

    @Override
    public String[] getOutputMetricKeys() {
      return new String[] {
        outputMetricKeys.getNewLinesToCover(),
        outputMetricKeys.getNewUncoveredLines(),
        outputMetricKeys.getNewConditionsToCover(),
        outputMetricKeys.getNewUncoveredConditions()
      };
    }
  }

  public static final class NewCoverageCounter implements org.sonar.server.computation.formula.Counter<NewCoverageCounter> {
    private final IntVariationValue.Array newLines = IntVariationValue.newArray();
    private final IntVariationValue.Array newCoveredLines = IntVariationValue.newArray();
    private final IntVariationValue.Array newConditions = IntVariationValue.newArray();
    private final IntVariationValue.Array newCoveredConditions = IntVariationValue.newArray();
    private final BatchReportReader batchReportReader;
    private final NewCoverageInputMetricKeys metricKeys;

    public NewCoverageCounter(BatchReportReader batchReportReader, NewCoverageInputMetricKeys metricKeys) {
      this.batchReportReader = batchReportReader;
      this.metricKeys = metricKeys;
    }

    @Override
    public void aggregate(NewCoverageCounter counter) {
      newLines.incrementAll(counter.newLines);
      newCoveredLines.incrementAll(counter.newCoveredLines);
      newConditions.incrementAll(counter.newConditions);
      newCoveredConditions.incrementAll(counter.newCoveredConditions);
    }

    @Override
    public void aggregate(FileAggregateContext context) {
      Component fileComponent = context.getFile();
      BatchReport.Changesets componentScm = batchReportReader.readChangesets(fileComponent.getRef());
      if (componentScm == null) {
        return;
      }

      Optional<Measure> hitsByLineMeasure = context.getMeasure(metricKeys.getCoverageLineHitsData());
      if (!hitsByLineMeasure.isPresent() || hitsByLineMeasure.get().getValueType() == Measure.ValueType.NO_VALUE) {
        return;
      }

      Map<Integer, Integer> hitsByLine = parseCountByLine(hitsByLineMeasure);
      Map<Integer, Integer> conditionsByLine = parseCountByLine(context.getMeasure(metricKeys.getConditionsByLine()));
      Map<Integer, Integer> coveredConditionsByLine = parseCountByLine(context.getMeasure(metricKeys.getCoveredConditionsByLine()));

      for (Map.Entry<Integer, Integer> entry : hitsByLine.entrySet()) {
        int lineId = entry.getKey();
        int hits = entry.getValue();
        int conditions = (Integer) ObjectUtils.defaultIfNull(conditionsByLine.get(lineId), 0);
        int coveredConditions = (Integer) ObjectUtils.defaultIfNull(coveredConditionsByLine.get(lineId), 0);
        BatchReport.Changesets.Changeset changeset = componentScm.getChangeset(componentScm.getChangesetIndexByLine(lineId - 1));
        Date date = changeset.hasDate() ? new Date(changeset.getDate()) : null;

        analyze(context.getPeriods(), date, hits, conditions, coveredConditions);
      }
    }

    private static Map<Integer, Integer> parseCountByLine(Optional<Measure> measure) {
      if (measure.isPresent() && measure.get().getValueType() != Measure.ValueType.NO_VALUE) {
        return KeyValueFormat.parseIntInt(measure.get().getStringValue());
      }
      return Collections.emptyMap();
    }

    public void analyze(List<Period> periods, @Nullable Date lineDate, int hits, int conditions, int coveredConditions) {
      if (lineDate == null) {
        return;
      }
      for (Period period : periods) {
        if (isLineInPeriod(lineDate, period)) {
          incrementLines(period, hits);
          incrementConditions(period, conditions, coveredConditions);
        }
      }
    }

    /**
     * A line belongs to a Period if its date is older than the SNAPSHOT's date of the period.
     */
    private static boolean isLineInPeriod(Date lineDate, Period period) {
      return lineDate.getTime() > period.getSnapshotDate();
    }

    private void incrementLines(Period period, int hits) {
      newLines.increment(period, 1);
      if (hits > 0) {
        newCoveredLines.increment(period, 1);
      }
    }

    private void incrementConditions(Period period, int conditions, int coveredConditions) {
      newConditions.increment(period, conditions);
      if (conditions > 0) {
        newCoveredConditions.increment(period, coveredConditions);
      }
    }

    public boolean hasNewCode(Period period) {
      return newLines.get(period).isSet();
    }

    public int getNewLines(Period period) {
      return newLines.get(period).getValue();
    }

    public int getNewCoveredLines(Period period) {
      return newCoveredLines.get(period).getValue();
    }

    public int getNewConditions(Period period) {
      return newConditions.get(period).getValue();
    }

    public int getNewCoveredConditions(Period period) {
      return newCoveredConditions.get(period).getValue();
    }
  }

  @Immutable
  public static final class NewCoverageOutputMetricKeys {
    private final String newLinesToCover;
    private final String newUncoveredLines;
    private final String newConditionsToCover;
    private final String newUncoveredConditions;

    public NewCoverageOutputMetricKeys(String newLinesToCover, String newUncoveredLines, String newConditionsToCover, String newUncoveredConditions) {
      this.newLinesToCover = newLinesToCover;
      this.newUncoveredLines = newUncoveredLines;
      this.newConditionsToCover = newConditionsToCover;
      this.newUncoveredConditions = newUncoveredConditions;
    }

    public String getNewLinesToCover() {
      return newLinesToCover;
    }

    public String getNewUncoveredLines() {
      return newUncoveredLines;
    }

    public String getNewConditionsToCover() {
      return newConditionsToCover;
    }

    public String getNewUncoveredConditions() {
      return newUncoveredConditions;
    }
  }

  @Immutable
  public static class NewCoverageInputMetricKeys {
    private final String coverageLineHitsData;
    private final String conditionsByLine;
    private final String coveredConditionsByLine;

    public NewCoverageInputMetricKeys(String coverageLineHitsData, String conditionsByLine, String coveredConditionsByLine) {
      this.coverageLineHitsData = coverageLineHitsData;
      this.conditionsByLine = conditionsByLine;
      this.coveredConditionsByLine = coveredConditionsByLine;
    }

    public String getCoverageLineHitsData() {
      return coverageLineHitsData;
    }

    public String getConditionsByLine() {
      return conditionsByLine;
    }

    public String getCoveredConditionsByLine() {
      return coveredConditionsByLine;
    }
  }
}
