package com.android.androidperf;

import javafx.application.Platform;
import javafx.css.Styleable;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.Axis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;

import java.util.*;

public class CheckableLineChart extends BaseLineChart {

    private GridPane checkerTable = null;
    private final Map<String, CheckBox> checkBoxMap = new LinkedHashMap<>();
    private Map<String, HBox> legendItemMap = new LinkedHashMap<>();
    private VBox legendBox = new VBox();

    public CheckableLineChart() {
        super();
        legendBox.setAlignment(Pos.CENTER_LEFT);
        legendBox.setSpacing(10);
        legendBox.setPadding(new Insets(10));
    }

    public void initLineChart(String chartName, String[] series, String yLabel, GridPane checkBoxPane) {
        super.initLineChart(chartName, series, yLabel);

        checkBoxMap.clear();
        checkerTable = checkBoxPane;
        checkerTable.getChildren().clear();
        setLegend(legendBox);
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

        points.forEach((seriesName, data) -> {
            checkBoxMap.computeIfAbsent(seriesName, s -> {
                CheckBox cb = new CheckBox(s);
                var series = findChartSeries(seriesName);
                assert series != null;
                cb.setUserData(series);
                // Create a new legend item for the series
                HBox legendItem = new HBox();
                legendItem.setAlignment(Pos.CENTER_LEFT);
                legendItem.setSpacing(5);

                // Create colored region to represent series line
                Region lineSymbol = new Region();
                lineSymbol.setPrefSize(10, 2);
                Path region = (Path) series.getNode().lookup(".chart-series-line");
                BackgroundFill backgroundFill = new BackgroundFill(region.getFill(), null, null);
                Background background = new Background(backgroundFill);
                lineSymbol.setBackground(background);
                lineSymbol.getStyleClass().setAll("chart-legend-item-symbol");
                region.getFill();

                Label seriesNameLabel = new Label(seriesName);
                seriesNameLabel.getStyleClass().add("chart-legend-item");
                seriesNameLabel.setTextFill(Color.BLACK);
                seriesNameLabel.setGraphic(lineSymbol);

                legendItem.getChildren().addAll(lineSymbol, seriesNameLabel);
                legendItemMap.put(seriesName, legendItem);

                // Add event handler to control its visibility
                cb.addEventHandler(ActionEvent.ACTION, e -> {
                    var source = (CheckBox) e.getSource();
                    var text = source.getText();
                    @SuppressWarnings("unchecked")
                    var userData = (Series<Number, Number>) source.getUserData();
                    userData.getNode().setVisible(source.isSelected());
                    if (source.isSelected()) {
                        legendBox.getChildren().add(legendItemMap.get(text));
                    } else {
                        legendBox.getChildren().remove(legendItemMap.get(text));
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

    @Override
    protected void updateAxisRange() {
        final Axis<Number> xa = getXAxis();
        final Axis<Number> ya = getYAxis();
        List<Number> xData = null;
        List<Number> yData = null;
        if (xa.isAutoRanging()) xData = new ArrayList<>();
        if (ya.isAutoRanging()) yData = new ArrayList<>();
        if (xData != null || yData != null) {
            for (Series<Number, Number> series : getData()) {
                if (series.getNode().isVisible()) { // consider only visible series
                    for (Data<Number, Number> data : series.getData()) {
                        if (xData != null) xData.add(data.getXValue());
                        if (yData != null) yData.add(data.getYValue());
                    }
                }
            }
            // RT-32838 No need to invalidate range if there is one data item - whose value is zero.
            if (xData != null && !(xData.size() == 1 && getXAxis().toNumericValue(xData.get(0)) == 0)) {
                xa.invalidateRange(xData);
            }
            if (yData != null && !(yData.size() == 1 && getYAxis().toNumericValue(yData.get(0)) == 0)) {
                ya.invalidateRange(yData);
            }
        }
    }

    @Override
    protected void updateLegend()
    {
        setLegend(legendBox);
    }
}
