package com.android.androidperf;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Side;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
import javafx.util.StringConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.vidstige.jadb.JadbDevice;
import se.vidstige.jadb.JadbException;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AppController implements Initializable {
    private static final Logger LOGGER = LogManager.getLogger(AppController.class);
    @FXML
    private ComboBox<String> deviceListBox;
    @FXML
    private ComboBox<String> packageListBox;
    @FXML
    private TableView<DeviceProp> propTable;
    @FXML
    private Button perfBtn;
    @FXML
    private LineChart<Number, Number> lineChartFPS;
    @FXML
    private LineChart<Number, Number> lineChartCPU;
    @FXML
    private LineChart<Number, Number> lineChartNetwork;
    private final HashMap<String, LineChart<Number, Number>> lineChartMap = new HashMap<>();
    @FXML
    private GridPane networkInterfaceContainer;

    public Device selectedDevice;
    private final HashMap<String, Device> deviceMap = new HashMap<>();
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // initialize the device list
        updateDeviceList();

        // initialize property table
        TableColumn<DeviceProp, String> nameCol = new TableColumn<>("Property");
        TableColumn<DeviceProp, String> valCol = new TableColumn<>("Value");
        nameCol.setCellValueFactory(cellData -> cellData.getValue().getPropName());
        valCol.setCellValueFactory(cellData -> cellData.getValue().getPropVal());
        valCol.setCellFactory(col -> {
            TableCell<DeviceProp, String> cell = new TableCell<>();
            Text text = new Text();
            cell.setGraphic(text);
            text.wrappingWidthProperty().bind(cell.widthProperty());
            text.textProperty().bind(cell.itemProperty());
            return cell;
        });
        propTable.getColumns().add(nameCol);
        propTable.getColumns().add(valCol);
        nameCol.prefWidthProperty().bind(propTable.widthProperty().multiply(0.38));
        valCol.prefWidthProperty().bind(propTable.widthProperty().multiply(0.62));

        // initialize line charts
        initAllLineCharts();

        // UI update
        updateUIOnStateChanges();
        packageListBox.setDisable(true);

//        // activate auto refresh task
//        executorService.scheduleAtFixedRate(this::refreshTask, 500, 500, TimeUnit.MILLISECONDS);
    }

    private void updateDeviceList() {
        try {
            List<JadbDevice> adbDevices = Device.connection.getDevices();
            ArrayList<String> obsoletes = new ArrayList<>(deviceListBox.getItems());
            if (selectedDevice != null && selectedDevice.isDeviceAlive()) {
                obsoletes.remove(selectedDevice.getDeviceADBID());
            }
            obsoletes.forEach(str -> {
                deviceListBox.getItems().remove(str);
                Device device = deviceMap.get(str);
                if (device != null) {
                    device.shutdown();
                    deviceMap.remove(str);
                }
            });
            for (JadbDevice adbDevice : adbDevices) {
                if (deviceMap.get(adbDevice.getSerial()) == null) {
                    Device device = new Device(adbDevice, this);
                    deviceListBox.getItems().add(device.getDeviceADBID());
                    deviceMap.put(device.getDeviceADBID(), device);
                }
            }
        } catch (IOException | JadbException e) {
            LOGGER.error("Cannot get device list");
        }
    }

    private void initAllLineCharts() {
        initLineChart(lineChartFPS, "FPS", new String[]{"FPS"}, 60, 10, "FPS");
        initLineChart(lineChartCPU, "CPU", new String[]{"App", "Total"}, 100, 20, "%");
        initLineChart(lineChartNetwork, "Network", new String[]{"Recv", "Send"}, 1000, 100, "KB/s");
    }

    private void initLineChart(LineChart<Number, Number> lineChart, String chartName, String[] series, int yBound, int yTick, String yLabel) {
        ObservableList<XYChart.Series<Number, Number>> seriesList = FXCollections.observableArrayList();
        for (String s : series) {
            XYChart.Series<Number, Number> data = new XYChart.Series<>();
            data.setName(s);
            seriesList.add(data);
        }
        lineChart.setData(seriesList);

        lineChart.setAnimated(true);
        lineChart.setTitle(chartName);

        NumberAxis xAxis = (NumberAxis) lineChart.getXAxis();
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

        NumberAxis yAxis = (NumberAxis) lineChart.getYAxis();
        yAxis.setLowerBound(0);
        yAxis.setUpperBound(yBound);
        yAxis.setTickUnit(yTick);
        yAxis.setLabel(yLabel);
        yAxis.setAutoRanging(false);
        yAxis.setMinorTickVisible(false);

        lineChart.setLegendSide(Side.RIGHT);

        lineChartMap.put(chartName, lineChart);
    }

    @SafeVarargs
    public final void addDataToChart(String chartName, XYChart.Data<Number, Number>... dataArrays) {
        LineChart<Number, Number> lineChart = lineChartMap.get(chartName);

        double yMax = Double.MIN_VALUE;
        for (int i = 0; i < dataArrays.length; i++) {
            var data = dataArrays[i];
            var series = lineChart.getData().get(i).getData();
            int xVal = data.getXValue().intValue();
            NumberAxis xAxis = (NumberAxis) lineChart.getXAxis();
            double xBound = xAxis.getUpperBound();
            if (xVal > xBound) {
                xBound = xVal + 15;
                xAxis.setUpperBound(xBound);
                xAxis.setTickUnit(xAxis.getTickUnit() + 1);
            }

            Optional<XYChart.Data<Number, Number>> max = series.stream().max(Comparator.comparingDouble(fc -> fc.getYValue().doubleValue()));
            if (max.isPresent()) {
                double y = max.get().getYValue().doubleValue();
                if (y > yMax)
                    yMax = y;
            }
            series.add(data);
        }

        NumberAxis yAxis = (NumberAxis) lineChart.getYAxis();
        int yBound = (int) yAxis.getUpperBound();
        if (yMax == Double.MIN_VALUE)
            yMax = yBound;
        int desiredBound = (int) (Math.ceil((yMax / 5.) + 1) * 5);
        if (yBound != desiredBound) {
            yAxis.setUpperBound(desiredBound);
            yAxis.setTickUnit(desiredBound / 5.);
        }
    }

    public void handleDeviceListBox() {
        if (selectedDevice != null)
            selectedDevice.endPerf();
        String deviceID = deviceListBox.getSelectionModel().getSelectedItem();
        selectedDevice = deviceMap.get(deviceID);
        if (selectedDevice == null)
            return;

        Dialog<String> dialog = new Dialog<>();
        //Setting the title
        dialog.setTitle("Connecting...");
        dialog.setContentText("Waiting for AndroidPerf server");
        Task<Boolean> task = new Task<>() {
            @Override public Boolean call() {
                return selectedDevice.startServer();
            }
        };

        task.setOnRunning((e) -> dialog.show());
        task.setOnSucceeded((e) -> Platform.runLater(() -> {
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL);
            dialog.close();
            propTable.getItems().clear();

            // initialize the package list
            packageListBox.setItems(selectedDevice.getPackageList());

            // initialize basic properties of the device
            ArrayList<DeviceProp> props = selectedDevice.getProps();
            ObservableList<DeviceProp> data = FXCollections.observableArrayList(props);
            propTable.getItems().addAll(data);

            // UI update
            updateUIOnStateChanges();
            packageListBox.setDisable(false);

            // activate auto refresh task
            executorService.scheduleAtFixedRate(this::refreshTask, 0, 500, TimeUnit.MILLISECONDS);
        }));
        task.setOnFailed((e) -> {
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL);
            dialog.close();
            MainApplication.alert("Cannot connect device, please retry!", Alert.AlertType.ERROR);
            Platform.runLater(()->deviceListBox.getSelectionModel().clearSelection());
        });
        new Thread(task).start();
    }

    public void appendNetworkInterfaceToGridPane(CheckBox checkBox) {
        int curSize = networkInterfaceContainer.getChildren().size();
        int numRow = networkInterfaceContainer.getRowCount();
        networkInterfaceContainer.add(checkBox, curSize / numRow, curSize % numRow);
    }

    private void refreshTask() {
        if (selectedDevice != null) {
            selectedDevice.checkCurrentPackage();
            selectedDevice.checkNetworkInterface();
        }
    }

    public void movePackageToFront(String packageName) {
        EventHandler<ActionEvent> handler = packageListBox.getOnAction();
        packageListBox.setOnAction(null);
        String selected = packageListBox.getSelectionModel().getSelectedItem();
        var packageList = selectedDevice.getPackageList();
        packageList.remove(packageName);
        packageList.add(0, packageName);
        if (selected != null) {
            packageListBox.getSelectionModel().select(selected);
            packageListBox.setValue(selected);
        }
        packageListBox.setOnAction(handler);
    }

    public void handlePackageListBox() {
        String packageName = packageListBox.getSelectionModel().getSelectedItem();
        if (packageName == null || packageName.length() == 0) {
            selectedDevice.endPerf();
            return;
        }
        selectedDevice.setTargetPackage(packageName);

        // UI update
        updateUIOnStateChanges();
    }

    public void handlePerfBtn() {
        if (selectedDevice.getPerfState()) {
            selectedDevice.endPerf();
        } else {
            initAllLineCharts();
            selectedDevice.startPerf();
        }
    }

    public void handleUpdateBtn() {
        updateDeviceList();
        if (selectedDevice != null) {
            selectedDevice.updatePackageList();
        } else {
            packageListBox.getItems().clear();
            propTable.getItems().clear();
            networkInterfaceContainer.getChildren().clear();
        }
    }

    public void updateUIOnStateChanges() {
        if (selectedDevice == null || selectedDevice.getTargetPackage() == null) {
            if (selectedDevice == null) {
                deviceListBox.setPromptText("Select connected devices");
            }
            packageListBox.setPromptText("Select target package");
            perfBtn.setDisable(true);
            perfBtn.setText("Start");
            return;
        }
        perfBtn.setDisable(false);
        if (selectedDevice.getPerfState()) {
            perfBtn.setText("End");
        } else {
            perfBtn.setText("Start");
        }
        if (!selectedDevice.isDeviceAlive()) {
            handleUpdateBtn();
            MainApplication.alert("Device is offline!", Alert.AlertType.ERROR);
        }
    }

    public void shutdown() {
        deviceMap.forEach((s, device) -> device.shutdown());
        executorService.shutdownNow();
    }
}
