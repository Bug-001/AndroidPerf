package com.android.androidperf;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
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
    private BaseLineChart lineChartFPS;
    @FXML
    private BaseLineChart lineChartCPU;
    @FXML
    private CheckableLineChart lineChartNetwork;
    private final HashMap<String, BaseLineChart> lineChartMap = new HashMap<>();
    @FXML
    private GridPane checkerTable;

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
        lineChartFPS.initLineChart("FPS", new String[]{"FPS"}, "FPS");
        lineChartMap.put("FPS", lineChartFPS);
        lineChartCPU.initLineChart("CPU", new String[]{"App", "Total"}, "%");
        lineChartMap.put("CPU", lineChartCPU);
        lineChartNetwork.initLineChart("Network", new String[]{}, "KB/s");
        lineChartNetwork.setCheckerTable(checkerTable);
        lineChartMap.put("Network", lineChartNetwork);
    }

    public final BaseLineChart findChart(String chartName) {
        return lineChartMap.get(chartName);
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

    private void refreshTask() {
        if (selectedDevice != null) {
            selectedDevice.checkCurrentPackage();
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
            checkerTable.getChildren().clear();
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
