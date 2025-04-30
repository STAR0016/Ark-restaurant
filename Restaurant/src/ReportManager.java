import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.cell.MapValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;
import javafx.scene.image.WritableImage;

public class ReportManager {
    private Stage managerStage;
    private BorderPane reportPane;
    private TableView<Map<String, Object>> reportTableView;
    private StackPane chartPane;

    public ReportManager(Stage managerStage) {
        this.managerStage = managerStage;
    }

    public BorderPane createReportsManagementPane() {
        reportPane = new BorderPane();
        reportPane.setPadding(new Insets(15));

        // Create report type selection combo box
        ComboBox<String> reportTypeComboBox = new ComboBox<>();
        reportTypeComboBox.getItems().addAll(
                "Sales Report",
                "Dish Category Analysis",
                "Inventory Status Report"
        );
        reportTypeComboBox.setValue("Sales Report"); // Default selection
        reportTypeComboBox.setPrefWidth(200);

        // Date range pickers
        DatePicker startDatePicker = new DatePicker(LocalDate.now().minusMonths(1));
        DatePicker endDatePicker = new DatePicker(LocalDate.now());

        // Price range filters (used in dish analysis)
        TextField minPriceField = new TextField();
        minPriceField.setPromptText("Min Price");
        minPriceField.setPrefWidth(100);

        TextField maxPriceField = new TextField();
        maxPriceField.setPromptText("Max Price");
        maxPriceField.setPrefWidth(100);

        HBox priceFilterBox = new HBox(10, new Label("Price Range:"), minPriceField, new Label("-"), maxPriceField);
        priceFilterBox.setAlignment(Pos.CENTER_LEFT);

        // Dish category selector
        ComboBox<String> categoryComboBox = new ComboBox<>();
        categoryComboBox.getItems().addAll(
                "All Categories",
                "Garden Harvest (¥5-¥10)",
                "Chef's Canvas (¥10-¥15)",
                "Epicurean Treasures (¥15+)"
        );
        categoryComboBox.setValue("All Categories");
        categoryComboBox.setPrefWidth(200);

        // Generate report button
        Button generateReportButton = new Button("Generate Report");
        generateReportButton.getStyleClass().add("update-button");

        // Export report button
        Button exportReportButton = new Button("Export Report");
        exportReportButton.setDisable(true); // Disabled initially

        // Layout
        VBox filterBox = new VBox(15);
        filterBox.setPadding(new Insets(0, 0, 15, 0));

        HBox reportSelectionBox = new HBox(15);
        reportSelectionBox.setAlignment(Pos.CENTER_LEFT);
        reportSelectionBox.getChildren().addAll(
                new Label("Report Type:"), reportTypeComboBox,
                new Label("Start Date:"), startDatePicker,
                new Label("End Date:"), endDatePicker
        );

        HBox categoryFilterBox = new HBox(15);
        categoryFilterBox.setAlignment(Pos.CENTER_LEFT);
        categoryFilterBox.getChildren().addAll(
                new Label("Category:"), categoryComboBox, priceFilterBox
        );

        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        buttonBox.getChildren().addAll(generateReportButton, exportReportButton);

        filterBox.getChildren().addAll(
                reportSelectionBox,
                categoryFilterBox,
                buttonBox,
                new Separator()
        );

        // Report display area
        reportTableView = new TableView();
        reportTableView.setPrefHeight(400);

        // Chart area
        chartPane = new StackPane();
        chartPane.setPrefHeight(300);
        chartPane.setStyle("-fx-background-color: #f4f4f4; -fx-border-color: #ddd;");

        VBox mainContent = new VBox(10);
        mainContent.getChildren().addAll(chartPane, reportTableView);
        VBox.setVgrow(reportTableView, Priority.ALWAYS);

        reportPane.setTop(filterBox);
        reportPane.setCenter(mainContent);

        // Toggle filter visibility based on report type
        reportTypeComboBox.setOnAction(e -> {
            String selectedReport = reportTypeComboBox.getValue();
            boolean isCategoryReport = "Dish Category Analysis".equals(selectedReport);
            priceFilterBox.setVisible(isCategoryReport);
            priceFilterBox.setManaged(isCategoryReport);
            categoryComboBox.setVisible(isCategoryReport);
            categoryFilterBox.setVisible(isCategoryReport);
            categoryFilterBox.setManaged(isCategoryReport);

            // Reset table and chart
            reportTableView.getColumns().clear();
            reportTableView.getItems().clear();
            chartPane.getChildren().clear();
        });

        // Initial state
        priceFilterBox.setVisible(false);
        priceFilterBox.setManaged(false);
        categoryFilterBox.setVisible(false);
        categoryFilterBox.setManaged(false);

        // Generate report handler
        generateReportButton.setOnAction(e -> {
            String reportType = reportTypeComboBox.getValue();
            LocalDate startDate = startDatePicker.getValue();
            LocalDate endDate = endDatePicker.getValue();

            switch (reportType) {
                case "Sales Report":
                    generateSalesReport(startDate, endDate);
                    break;
                case "Dish Category Analysis":
                    String category = categoryComboBox.getValue();
                    double minPrice = parsePriceField(minPriceField, 0);
                    double maxPrice = parsePriceField(maxPriceField, Double.MAX_VALUE);
                    generateCategoryReport(category, minPrice, maxPrice);
                    break;
                case "Inventory Status Report":
                    generateInventoryReport();
                    break;
            }

            exportReportButton.setDisable(false);
        });

        // Export report handler
        exportReportButton.setOnAction(e -> {
            exportReport(reportTypeComboBox.getValue());
        });

        return reportPane;
    }

    private double parsePriceField(TextField field, double defaultValue) {
        try {
            if (field.getText() == null || field.getText().trim().isEmpty()) {
                return defaultValue;
            }
            return Double.parseDouble(field.getText().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void generateSalesReport(LocalDate startDate, LocalDate endDate) {
        reportTableView.getColumns().clear();
        reportTableView.getItems().clear();
        chartPane.getChildren().clear();

        java.sql.Date sqlStartDate = java.sql.Date.valueOf(startDate);
        java.sql.Date sqlEndDate = java.sql.Date.valueOf(endDate);

        TableColumn<Map<String, Object>, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory((Callback) new MapValueFactory<>("date"));
        dateCol.setPrefWidth(150);

        TableColumn<Map<String, Object>, Number> orderCountCol = new TableColumn<>("Orders");
        orderCountCol.setCellValueFactory((Callback) new MapValueFactory<>("orderCount"));
        orderCountCol.setPrefWidth(100);

        TableColumn<Map<String, Object>, Number> totalSalesCol = new TableColumn<>("Total Sales");
        totalSalesCol.setCellValueFactory((Callback) new MapValueFactory<>("totalSales"));
        totalSalesCol.setPrefWidth(150);
        totalSalesCol.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Number price, boolean empty) {
                super.updateItem(price, empty);
                setText(empty || price == null ? null : String.format("¥%.2f", price.doubleValue()));
            }
        });

        reportTableView.getColumns().addAll(dateCol, orderCountCol, totalSalesCol);

        new Thread(() -> {
            List<Map<String, Object>> salesData = getDailySalesData(sqlStartDate, sqlEndDate);

            Platform.runLater(() -> {
                ObservableList<Map<String, Object>> tableData = FXCollections.observableArrayList(salesData);
                reportTableView.setItems(tableData);

                if (salesData.isEmpty()) {
                    showAlert(AlertType.INFORMATION, "No Data", "No sales data in selected date range.");
                    return;
                }

                CategoryAxis xAxis = new CategoryAxis();
                NumberAxis yAxis = new NumberAxis();
                xAxis.setLabel("Date");
                yAxis.setLabel("Sales (¥)");

                LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
                lineChart.setTitle("Daily Sales");
                lineChart.setAnimated(false);

                XYChart.Series<String, Number> series = new XYChart.Series<>();
                series.setName("Sales");

                double maxSales = 0;
                for (Map<String, Object> data : salesData) {
                    double sales = ((Number) data.get("totalSales")).doubleValue();
                    if (sales > maxSales) maxSales = sales;

                    series.getData().add(new XYChart.Data<>(
                            data.get("date").toString(),
                            (Number) data.get("totalSales")
                    ));
                }

                yAxis.setAutoRanging(false);
                yAxis.setUpperBound(maxSales * 1.2);
                yAxis.setTickUnit(Math.max(10, Math.ceil(maxSales / 10)));

                lineChart.getData().add(series);

                series.getNode().setStyle("-fx-stroke: #4682B4; -fx-stroke-width: 3px;");
                for (XYChart.Data<String, Number> item : series.getData()) {
                    item.getNode().setStyle("-fx-background-color: #4682B4, white; -fx-background-radius: 5px; -fx-padding: 5px;");
                }

                lineChart.setPrefSize(chartPane.getWidth(), chartPane.getHeight());
                lineChart.setMinHeight(300);

                if (salesData.size() > 10) {
                    xAxis.setTickLabelRotation(45);
                }

                chartPane.getChildren().add(lineChart);

            });
        }).start();
    }

    private List<Map<String, Object>> getDailySalesData(java.sql.Date startDate, java.sql.Date endDate) {
        List<Map<String, Object>> result = new ArrayList<>();

        String sql = "SELECT strftime('%Y-%m-%d', order_time) as order_date, " +
                "COUNT(*) as order_count, " +
                "SUM(total_price) as total_sales " +
                "FROM orders " +
                "WHERE order_time BETWEEN ? AND ? " +
                "GROUP BY order_date " +
                "ORDER BY order_date";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            String startStr = startDate.toString() + " 00:00:00";
            String endStr = endDate.toString() + " 23:59:59";

            stmt.setString(1, startStr);
            stmt.setString(2, endStr);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> dataPoint = new HashMap<>();
                    String dateStr = rs.getString("order_date");
                    int orderCount = rs.getInt("order_count");
                    double totalSales = rs.getDouble("total_sales");

                    dataPoint.put("date", dateStr);
                    dataPoint.put("orderCount", orderCount);
                    dataPoint.put("totalSales", totalSales);

                    result.add(dataPoint);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching sales data: " + e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    private void generateCategoryReport(String category, double minPrice, double maxPrice) {
        reportTableView.getColumns().clear();
        reportTableView.getItems().clear();
        chartPane.getChildren().clear();

        double actualMinPrice = minPrice;
        double actualMaxPrice = maxPrice;

        if (!"All Categories".equals(category)) {
            if (category.contains("Garden Harvest")) {
                actualMinPrice = 5;
                actualMaxPrice = 10;
            } else if (category.contains("Chef's Canvas")) {
                actualMinPrice = 10;
                actualMaxPrice = 15;
            } else if (category.contains("Epicurean Treasures")) {
                actualMinPrice = 15;
                actualMaxPrice = Double.MAX_VALUE;
            }
        }

        TableColumn<Map<String, Object>, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory((Callback)new MapValueFactory<>("id"));

        TableColumn<Map<String, Object>, String> nameCol = new TableColumn<>("Dish Name");
        nameCol.setCellValueFactory((Callback)new MapValueFactory<>("name"));

        TableColumn<Map<String, Object>, Double> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory((Callback)new MapValueFactory<>("price"));
        priceCol.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                setText(empty || price == null ? null : String.format("¥%.2f", price));
            }
        });

        TableColumn<Map<String, Object>, Integer> inventoryCol = new TableColumn<>("Inventory");
        inventoryCol.setCellValueFactory((Callback)new MapValueFactory<>("inventory"));

        TableColumn<Map<String, Object>, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory((Callback)new MapValueFactory<>("category"));

        reportTableView.getColumns().addAll(idCol, nameCol, priceCol, inventoryCol, categoryCol);

        final double finalMinPrice = actualMinPrice;
        final double finalMaxPrice = actualMaxPrice;

        new Thread(() -> {
            List<Map<String, Object>> categoryData = getCategoryData(finalMinPrice, finalMaxPrice);

            Platform.runLater(() -> {
                ObservableList<Map<String, Object>> tableData = FXCollections.observableArrayList(categoryData);
                reportTableView.setItems(tableData);

                PieChart pieChart = new PieChart();
                pieChart.setTitle("Dish Category Distribution");

                Map<String, Integer> categoryCounts = new HashMap<>();
                categoryCounts.put("Garden Harvest", 0);
                categoryCounts.put("Chef's Canvas", 0);
                categoryCounts.put("Epicurean Treasures", 0);

                for (Map<String, Object> dish : categoryData) {
                    String cat = (String) dish.get("category");
                    categoryCounts.put(cat, categoryCounts.getOrDefault(cat, 0) + 1);
                }

                for (Map.Entry<String, Integer> entry : categoryCounts.entrySet()) {
                    if (entry.getValue() > 0) {
                        PieChart.Data slice = new PieChart.Data(entry.getKey() + " (" + entry.getValue() + ")", entry.getValue());
                        pieChart.getData().add(slice);
                    }
                }

                pieChart.setPrefSize(chartPane.getWidth(), chartPane.getHeight());
                chartPane.getChildren().add(pieChart);
            });
        }).start();
    }

    private List<Map<String, Object>> getCategoryData(double minPrice, double maxPrice) {
        List<Map<String, Object>> result = new ArrayList<>();

        List<UserInterface.Dish> allDishes = DishData.loadDishesFromDatabase();

        for (UserInterface.Dish dish : allDishes) {
            double price = dish.getPrice();
            if (price >= minPrice && price <= maxPrice) {
                Map<String, Object> item = new HashMap<>();

                int inventory = DishData.getInventory(dish.getId());
                if (inventory < 0) inventory = 0;

                String category;
                if (price >= 5 && price < 10) {
                    category = "Garden Harvest";
                } else if (price >= 10 && price < 15) {
                    category = "Chef's Canvas";
                } else if (price >= 15) {
                    category = "Epicurean Treasures";
                } else {
                    category = "Other";
                }

                item.put("id", dish.getId());
                item.put("name", dish.getName());
                item.put("price", price);
                item.put("inventory", inventory);
                item.put("category", category);

                result.add(item);
            }
        }

        return result;
    }

    private void generateInventoryReport() {
        reportTableView.getColumns().clear();
        reportTableView.getItems().clear();
        chartPane.getChildren().clear();

        TableColumn<Map<String, Object>, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory((Callback) new MapValueFactory<>("id"));

        TableColumn<Map<String, Object>, String> nameCol = new TableColumn<>("Dish Name");
        nameCol.setCellValueFactory((Callback) new MapValueFactory<>("name"));

        TableColumn<Map<String, Object>, Double> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory((Callback) new MapValueFactory<>("price"));
        priceCol.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                setText(empty || price == null ? null : String.format("¥%.2f", price));
            }
        });

        TableColumn<Map<String, Object>, Integer> inventoryCol = new TableColumn<>("Inventory");
        inventoryCol.setCellValueFactory((Callback)new MapValueFactory<>("inventory"));

        TableColumn<Map<String, Object>, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory((Callback)new MapValueFactory<>("status"));
        statusCol.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    if (status.equals("Low Inventory")) {
                        setStyle("-fx-text-fill: red;");
                    } else if (status.equals("Sufficient Inventory")) {
                        setStyle("-fx-text-fill: green;");
                    } else {
                        setStyle("-fx-text-fill: orange;");
                    }
                }
            }
        });

        reportTableView.getColumns().addAll(idCol, nameCol, priceCol, inventoryCol, statusCol);

        new Thread(() -> {
            List<Map<String, Object>> inventoryData = getInventoryData();

            Platform.runLater(() -> {
                ObservableList<Map<String, Object>> tableData = FXCollections.observableArrayList(inventoryData);
                reportTableView.setItems(tableData);

                int lowInventoryCount = 0;
                int normalInventoryCount = 0;
                int highInventoryCount = 0;

                for (Map<String, Object> item : inventoryData) {
                    String status = (String) item.get("status");
                    if ("Low Inventory".equals(status)) {
                        lowInventoryCount++;
                    } else if ("Moderate Inventory".equals(status)) {
                        normalInventoryCount++;
                    } else if ("Sufficient Inventory".equals(status)) {
                        highInventoryCount++;
                    }
                }

                PieChart pieChart = new PieChart();
                pieChart.setTitle("Inventory Status Distribution");

                if (lowInventoryCount > 0) {
                    pieChart.getData().add(new PieChart.Data("Low Inventory (" + lowInventoryCount + ")", lowInventoryCount));
                }
                if (normalInventoryCount > 0) {
                    pieChart.getData().add(new PieChart.Data("Moderate Inventory (" + normalInventoryCount + ")", normalInventoryCount));
                }
                if (highInventoryCount > 0) {
                    pieChart.getData().add(new PieChart.Data("Sufficient Inventory (" + highInventoryCount + ")", highInventoryCount));
                }

                int colorIndex = 0;
                for (PieChart.Data data : pieChart.getData()) {
                    String color;
                    if (data.getName().contains("Low Inventory")) {
                        color = "#ff6666";
                    } else if (data.getName().contains("Moderate Inventory")) {
                        color = "#ff9933";
                    } else {
                        color = "#66cc66";
                    }

                    data.getNode().setStyle("-fx-pie-color: " + color + ";");
                    colorIndex++;
                }

                pieChart.setPrefSize(chartPane.getWidth(), chartPane.getHeight());
                chartPane.getChildren().add(pieChart);
            });
        }).start();
    }

    private List<Map<String, Object>> getInventoryData() {
        List<Map<String, Object>> result = new ArrayList<>();

        List<UserInterface.Dish> dishes = DishData.loadDishesFromDatabase();
        for (UserInterface.Dish dish : dishes) {
            Map<String, Object> item = new HashMap<>();
            int inventory = DishData.getInventory(dish.getId());
            if (inventory < 0) inventory = 0;

            String status;
            if (inventory < 5) {
                status = "Low Inventory";
            } else if (inventory < 20) {
                status = "Moderate Inventory";
            } else {
                status = "Sufficient Inventory";
            }

            item.put("id", dish.getId());
            item.put("name", dish.getName());
            item.put("price", dish.getPrice());
            item.put("inventory", inventory);
            item.put("status", status);

            result.add(item);
        }

        return result;
    }

    /**
     * Export report to CSV file
     */
    private void exportReport(String reportType) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Report");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
                new FileChooser.ExtensionFilter("PNG Images", "*.png"),
                new FileChooser.ExtensionFilter("JPEG Images", "*.jpg")
        );
        fileChooser.setInitialFileName(reportType);

        File file = fileChooser.showSaveDialog(managerStage);
        if (file == null) {
            return;
        }

        String fileName = file.getName().toLowerCase();

        try {
            if (fileName.endsWith(".csv")) {
                try (FileWriter writer = new FileWriter(file)) {
                    StringBuilder header = new StringBuilder();
                    for (TableColumn<?, ?> column : reportTableView.getColumns()) {
                        header.append(column.getText()).append(",");
                    }
                    if (header.length() > 0) {
                        header.setLength(header.length() - 1);
                    }
                    writer.write(header.toString() + "\n");

                    for (Object item : reportTableView.getItems()) {
                        StringBuilder row = new StringBuilder();
                        if (item instanceof Map) {
                            Map<String, Object> mapItem = (Map<String, Object>) item;
                            for (TableColumn<?, ?> column : reportTableView.getColumns()) {
                                String key = getMapValueFactoryKey(column);
                                if (key != null && mapItem.containsKey(key)) {
                                    Object value = mapItem.get(key);
                                    row.append(value).append(",");
                                } else {
                                    row.append(",");
                                }
                            }
                        }
                        if (row.length() > 0) {
                            row.setLength(row.length() - 1);
                        }
                        writer.write(row.toString() + "\n");
                    }
                }
                showAlert(Alert.AlertType.INFORMATION, "Export Successful", "Report exported to: " + file.getAbsolutePath());
            } else if (fileName.endsWith(".png") || fileName.endsWith(".jpg")) {
                WritableImage snapshot = reportPane.snapshot(new javafx.scene.SnapshotParameters(), null);
                String format = fileName.endsWith(".png") ? "png" : "jpg";
                ImageIO.write(SwingFXUtils.fromFXImage(snapshot, null), format, file);
                showAlert(Alert.AlertType.INFORMATION, "Export Successful", "Report exported as image: " + file.getAbsolutePath());
            } else {
                showAlert(Alert.AlertType.ERROR, "Export Failed", "Unsupported file format.");
            }
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Export Failed", "Error saving file: " + e.getMessage());
        }
    }


    private String getMapValueFactoryKey(TableColumn<?, ?> column) {
        String columnName = column.getText().toLowerCase().replace(" ", "");

        if (columnName.equals("dishname")) return "name";
        if (columnName.equals("orders")) return "orderCount";
        if (columnName.equals("totalsales")) return "totalSales";
        if (columnName.equals("rank")) return "rank";
        if (columnName.equals("date")) return "date";
        if (columnName.equals("inventory")) return "inventory";
        if (columnName.equals("status")) return "status";
        if (columnName.equals("price")) return "price";
        if (columnName.equals("category")) return "category";
        if (columnName.equals("id")) return "id";

        return columnName;
    }

    private void showAlert(AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

}