package com.android.androidperf;

import javafx.application.Platform;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.GridPane;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class CheckableLineChart extends BaseLineChart {

    private GridPane checkerTable = null;
    private final Map<String, CheckBox> checkBoxMap = new LinkedHashMap<>();

    public CheckableLineChart() {

    }

    public void setCheckerTable(GridPane gridPane) {
        checkerTable = gridPane;
    }

    @Override
    public void initLineChart(String chartName, String[] series, String yLabel) {
        super.initLineChart(chartName, series, yLabel);
    }

    private XYChart.Series<Number, Number> findChartSeries(String seriesName) {
        for (var series: getData()) {
            if (Objects.equals(series.getName(), seriesName))
                return series;
        }
        return null;
    }

    @Override
    public void addDataToChart(Map<String, Data<Number, Number>> points) {
        super.addDataToChart(points);

        checkBoxMap.forEach((intf, checkBox) -> checkBox.setDisable(true));

        points.forEach((series, data) -> {
            String intf = series.split(" ")[0];
            checkBoxMap.computeIfAbsent(intf, s -> {
                CheckBox cb = new CheckBox(s);
                cb.setOnAction(e -> {
                    var series_recv = findChartSeries(String.format("%s recv", cb.getText()));
                    var series_send = findChartSeries(String.format("%s send", cb.getText()));
                    assert series_recv != null;
                    assert series_send != null;
                    if (!cb.isDisabled() && cb.isSelected()) {
                        series_recv.getNode().setVisible(true);
                        series_send.getNode().setVisible(true);
                    } else {
                        series_recv.getNode().setVisible(false);
                        series_send.getNode().setVisible(false);
                    }
                });
                Platform.runLater(() -> {
                    int curSize = checkerTable.getChildren().size();
                    int numRow = checkerTable.getRowCount();
                    checkerTable.add(cb, curSize / numRow, curSize % numRow);
                });
                return cb;
            }).setDisable(false);
        });
    }
}
