/*
 *  Copyright 2017 TWO SIGMA OPEN SOURCE, LLC
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.twosigma.flint.timeseries.summarize.summarizer.subtractable

import com.twosigma.flint.timeseries._
import com.twosigma.flint.timeseries.row.Schema
import com.twosigma.flint.timeseries.summarize.SummarizerSuite
import com.twosigma.flint.timeseries.summarize.summarizer._
import org.apache.spark.sql.types.{ DoubleType, IntegerType }
import org.apache.spark.sql.Row

class ExponentialWeightedMovingAverageSummarizerSpec extends SummarizerSuite {

  override val defaultPartitionParallelism: Int = 10

  override val defaultResourceDir =
    "/timeseries/summarize/summarizer/exponentialmovingaveragesummarizer"

  "ExponentialWeightedMovingAverageSummarizer" should "smooth correctly" in {
    val timeSeriesRdd = fromCSV(
      "Price.csv",
      Schema(
        "id" -> IntegerType,
        "price" -> DoubleType,
        "expected_core" -> DoubleType,
        "expected_legacy" -> DoubleType
      )
    )

    val result1 = timeSeriesRdd.addSummaryColumns(
      Summarizers.ewma(
        xColumn = "price",
        durationPerPeriod = "constant",
        convention = "core"
      ),
      Seq("id")
    )

    result1.rdd
      .collect()
      .foreach { row =>
        val test = row.getAs[Double](
          ExponentialWeightedMovingAverageSummarizer.ewmaColumn
        )
        val expected = row.getAs[Double]("expected_core")
        assert(test === expected)
      }

    val result2 = timeSeriesRdd.addSummaryColumns(
      Summarizers.ewma(
        xColumn = "price",
        durationPerPeriod = "constant",
        convention = "legacy"
      ),
      Seq("id")
    )

    result2.rdd
      .collect()
      .foreach { row =>
        val test = row.getAs[Double](
          ExponentialWeightedMovingAverageSummarizer.ewmaColumn
        )
        val expected = row.getAs[Double]("expected_legacy")
        assert(test === expected)
      }
  }

  it should "smooth for large dataset correctly" in {
    // The following dataset is generated by
    // >>> import pandas as pd
    // >>> df = pd.read_csv('Volume.csv')
    // >>> df = df[['time', 'v']]
    // >>> df = df.assign(c = 1.0)
    // >>> df['expected_core'] = pd.ewma(df['v'], alpha = 0.05)
    // >>> df.to_csv('Volume.csv', index=False)
    val timeSeriesRdd = fromCSV(
      "Volume.csv",
      dateFormat = "yyyyMMdd HH:mm:ss.SSS"
    )

    val result1 = timeSeriesRdd
      .addSummaryColumns(
        Summarizers
          .ewma(
            xColumn = "v",
            durationPerPeriod = "constant",
            convention = "core"
          )
          .prefix("core")
      )
      .addSummaryColumns(
        Summarizers
          .ewma(
            xColumn = "v",
            durationPerPeriod = "constant",
            convention = "legacy"
          )
          .prefix("legacy_v")
      )
      .addSummaryColumns(
        Summarizers
          .ewma(
            xColumn = "c",
            durationPerPeriod = "constant",
            convention = "legacy"
          )
          .prefix("legacy_c")
      )

    result1.rdd
      .collect()
      .foreach { row =>
        val test = row.getAs[Double](
          s"core_${ExponentialWeightedMovingAverageSummarizer.ewmaColumn}"
        )
        val expected = row.getAs[Double]("expected_core")
        assert(test === expected)

        // The following is a property test to verify that "core" could be obtained from
        // two "legacy" computations. A "legacy" computation is on the original column and
        // the other "legacy" is on a constant column with all ones.
        val legacy1 = row.getAs[Double](
          s"legacy_v_${ExponentialWeightedMovingAverageSummarizer.ewmaColumn}"
        )
        val legacy2 = row.getAs[Double](
          s"legacy_c_${ExponentialWeightedMovingAverageSummarizer.ewmaColumn}"
        )
        assert(legacy1 / legacy2 === expected)
      }
  }

  it should "smooth constants correctly" in {
    def getConstants(id: Int, constant: Double = 1.0): TimeSeriesRDD = {
      var rdd = Clocks.uniform(
        sc,
        "1d",
        beginDateTime = "1960-01-01",
        endDateTime = "2030-01-01"
      )
      rdd = rdd.addColumns("value" -> DoubleType -> { (_: Row) =>
        constant
      })
      rdd = rdd.addColumns("id" -> IntegerType -> { (_: Row) =>
        id
      })
      rdd.addColumns("expected_core" -> DoubleType -> { (_: Row) =>
        constant
      })
    }

    val tsRdd = getConstants(1, 1.0)
      .merge(getConstants(2, 2.0))
      .merge(getConstants(3, 3.0))
      .addSummaryColumns(
        Summarizers.ewma(
          xColumn = "value",
          durationPerPeriod = "1d",
          convention = "core"
        ),
        Seq("id")
      )

    tsRdd.rdd
      .collect()
      .foreach { row =>
        val test = row.getAs[Double](
          ExponentialWeightedMovingAverageSummarizer.ewmaColumn
        )
        val expected = row.getAs[Double]("expected_core")
        assert(test === expected)
      }
  }

  it should "pass summarizer property test" in {
    summarizerPropertyTest(AllPropertiesAndSubtractable)(
      Summarizers.ewma(
        xColumn = "x3"
      )
    )
    summarizerPropertyTest(AllPropertiesAndSubtractable)(
      Summarizers.ewma(
        xColumn = "x3",
        durationPerPeriod = "constant"
      )
    )
  }
}
