package com.android.androidperf;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Side;
import javafx.scene.chart.Axis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.util.StringConverter;

import java.util.Map;
import java.util.Objects;

public class BaseLineChart extends LineChart<Number, Number> {
    public BaseLineChart() {
        this(new NumberAxis(), new NumberAxis());
    }

    public BaseLineChart(Axis<Number> numberAxis, Axis<Number> numberAxis2) {
        super(numberAxis, numberAxis2);
    }

    public void initLineChart(String chartName, String[] series, String yLabel) {
        ObservableList<Series<Number, Number>> seriesList = FXCollections.observableArrayList();
        for (String s : series) {
            XYChart.Series<Number, Number> data = new XYChart.Series<>();
            data.setName(s);
            seriesList.add(data);
        }
        setData(seriesList);

        setAnimated(true);
        setCreateSymbols(false);
        setTitle(chartName);

        NumberAxis xAxis = (NumberAxis) getXAxis();
        xAxis.setLowerBound(0);
        xAxis.setUpperBound(60);
        xAxis.setTickUnit(4);
        xAxis.setAutoRanging(false);
        xAxis.setMinorTickVisible(false);
        xAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number number) {
                long time = number.longValue();
                return String.format("%d:%02d", time / 60, time % 60);
            }

            @Override
            public Number fromString(String s) {
                return null;
            }
        });

        NumberAxis yAxis = (NumberAxis) getYAxis();
        yAxis.setLowerBound(0);
        yAxis.setLabel(yLabel);
        yAxis.setMinorTickVisible(false);

        setLegendSide(Side.RIGHT);
    }

    public void addDataToChart(Map<String, Data<Number, Number>> points) {
        var chartData = getData();
        NumberAxis xAxis = (NumberAxis) getXAxis();
        double xBound = xAxis.getUpperBound();
        points.forEach((name, data) -> {
            // O(n^2) search is bad but works in this situation
            int i;
            XYChart.Series<Number, Number> series = null;
            for (i = 0; i < chartData.size(); ++i) {
                series = chartData.get(i);
                if (Objects.equals(series.getName(), name)) {
                    break;
                }
            }
            if (i >= chartData.size()) {
                // The needed series not found, add it to chart
                series = new XYChart.Series<>();
                series.setName(name);
                chartData.add(series);
                series.getNode().setVisible(false);
            }
            series.getData().add(data);
            double xVal = data.getXValue().doubleValue();
            if (xVal > xBound) {
                xAxis.setUpperBound(xVal + 15);
                xAxis.setLowerBound(Math.max(xVal + 15 - 120, 0));
            }
        });
    }

}
