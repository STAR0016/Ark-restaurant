import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.converter.IntegerStringConverter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;

public class ManagerInterface {

    private Stage managerStage;
    private Stage primaryStage;

    // --- Wrapper class for Dish with non-persistent inventory ---
    public static class ManagerDish {
        private final UserInterface.Dish originalDish;
        private final SimpleIntegerProperty inventory;

        public ManagerDish(UserInterface.Dish dish) {
            this.originalDish = dish;

            int dbInventory = DishData.getInventory(dish.getId());

            this.inventory = new SimpleIntegerProperty(dbInventory >= 0 ? dbInventory : 0);
        }

        // Delegate methods to originalDish
        public int getId() { return originalDish.getId(); }
        public String getName() { return originalDish.getName(); }
        public double getPrice() { return originalDish.getPrice(); }
        public String getImagePath() {
            return originalDish.getImageName();
        }

        public String getImageName() { return originalDish.getImageName();} // Get original filename

        // Inventory property methods
        public int getInventory() { return inventory.get(); }
        public SimpleIntegerProperty inventoryProperty() { return inventory; }
        public void setInventory(int inventory) {
            this.inventory.set(inventory);

            DishData.updateInventory(getId(), inventory);
            System.out.println("The inventory has been updated to the database. " + getName() + " set as: " + inventory);
        }

    }

    // Menu Management Tab Components
    private TableView<ManagerDish> dishTable;
    private ObservableList<ManagerDish> dishData; // Use the wrapper class
    private ImageView dishImageView;
    private TextField nameField;
    private TextField priceField;
    private TextField inventoryField; // Field for non-persistent inventory
    private Label imagePathLabel;
    private File selectedImageFile;

    // Order Management Tab Components
    private TableView<OrderData.Order> orderTable;
    private ObservableList<OrderData.Order> orderData; // Use OrderData.Order

    private ReportManager reportManager;
    private BorderPane createReportsManagementPane() {

        reportManager = new ReportManager(managerStage);

        return reportManager.createReportsManagementPane();
    }

    public void display(Stage ownerStage) {
        this.primaryStage = ownerStage;

        managerStage = new Stage();
        managerStage.setTitle("Manager Dashboard"); // Manager Dashboard

        BorderPane rootLayout = new BorderPane();

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab menuTab = new Tab("Menu Management", createMenuManagementPane());
        Tab ordersTab = new Tab("Order Management", createOrderManagementPane());
        Tab reportsTab = new Tab("Report Management", createReportsManagementPane());

        tabPane.getTabs().addAll(menuTab, ordersTab, reportsTab);
        rootLayout.setCenter(tabPane);

        HBox bottomBar = new HBox();
        bottomBar.setPadding(new Insets(10));
        bottomBar.setAlignment(Pos.CENTER_RIGHT);
        Button logoutButton = new Button("Logout");
        logoutButton.getStyleClass().add("logout-button");
        logoutButton.setOnAction(e -> handleLogout());
        bottomBar.getChildren().add(logoutButton);
        logoutButton.setPrefWidth(100);
        logoutButton.setPrefHeight(40);

        rootLayout.setBottom(bottomBar);

        Scene scene = new Scene(rootLayout, 1000, 688); // Adjusted size
        managerStage.setScene(scene);

        // Load initial data asynchronously after the stage is set up
        loadDishData();
        loadOrderData();

        managerStage.show();
        managerStage.setOnCloseRequest(e -> {
            if (primaryStage != null) {
                primaryStage.show();
            }
        });
    }

    private BorderPane createMenuManagementPane() {
        BorderPane menuPane = new BorderPane();
        menuPane.setPadding(new Insets(15));

        // --- Table View (Left/Center) ---
        dishTable = new TableView<>();
        setupDishTableColumns(); // Setup columns including inventory
        dishData = FXCollections.observableArrayList();
        dishTable.setItems(dishData);
        menuPane.setCenter(dishTable);

        // --- Dish Details Form (Right) ---
        VBox detailsForm = createDishDetailsForm();
        menuPane.setRight(detailsForm);
        BorderPane.setMargin(detailsForm, new Insets(0, 0, 0, 15));

        // --- Table Row Selection Listener ---
        dishTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> populateDishDetails(newValue)); // Pass ManagerDish

        return menuPane;
    }

    private void setupDishTableColumns() {
        // ID Column
        TableColumn<ManagerDish, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(50);
        idCol.setSortable(true);

        // Name Column
        TableColumn<ManagerDish, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(180);

        // Price Column
        TableColumn<ManagerDish, Double> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));
        priceCol.setPrefWidth(90);
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(); // Default locale currency
        priceCol.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                setText(empty || price == null ? null : String.format("%.2f$",price));
            }
        });

        // Inventory Column (Editable, In-Memory)
        TableColumn<ManagerDish, Integer> inventoryCol = new TableColumn<>("Inventory");
        inventoryCol.setCellValueFactory(cellData -> cellData.getValue().inventoryProperty().asObject());
        inventoryCol.setPrefWidth(100);
        // Make the cell editable using TextFieldTableCell
        inventoryCol.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        // Handle edit commit: Update the ManagerDish object's inventory property and database
        inventoryCol.setOnEditCommit(event -> {
            ManagerDish dish = event.getRowValue();
            Integer newValue = event.getNewValue();
            if (newValue != null && newValue >= 0) { // Basic validation

                dish.setInventory(newValue);
                System.out.println("Inventory updated as: " + dish.getName() + " set as: " + newValue);
            } else {
                // Revert or show error if input is invalid
                showAlert(AlertType.ERROR, "Input Error", "Inventory must be a non-negative integer.");
                // Refresh the cell to show the old value
                event.getTableView().getItems().set(event.getTablePosition().getRow(), dish);
            }
        });
        dishTable.setEditable(true);

        dishTable.getColumns().addAll(idCol, nameCol, priceCol, inventoryCol);
        dishTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private VBox createDishDetailsForm() {
        VBox form = new VBox(10);
        form.setPadding(new Insets(10));
        form.setAlignment(Pos.TOP_CENTER);
        form.setPrefWidth(320);
        form.getStyleClass().add("details-form");

        Label formTitle = new Label("Dish Details");
        formTitle.getStyleClass().add("form-title");

        dishImageView = new ImageView();
        dishImageView.setFitHeight(200);
        dishImageView.setFitWidth(200);
        dishImageView.setPreserveRatio(true);
        StackPane imagePane = new StackPane(dishImageView);
        imagePane.setStyle("-fx-border-color: lightgrey; -fx-border-width: 1; -fx-background-color: #f4f4f4;");
        imagePane.setPrefSize(210, 210);
        imagePane.setAlignment(Pos.CENTER);
        imagePane.getStyleClass().add("image-preview");

        imagePathLabel = new Label("No image selected.");
        imagePathLabel.setWrapText(true);

        nameField = new TextField();
        nameField.setPromptText("Dish Name");

        priceField = new TextField();
        priceField.setPromptText("Price (e.g., 9.99)");

        inventoryField = new TextField();
        inventoryField.setPromptText("Inventory");

        Button updateButton = new Button("Update Selected");
        updateButton.getStyleClass().add("update-button");
        updateButton.setOnAction(e -> handleUpdateDish());

        Button removeButton = new Button("Remove Selected");
        removeButton.getStyleClass().add("remove-button");
        removeButton.setOnAction(e -> handleRemoveDish());

        Button addNewButton = new Button("Add New Dish");
        addNewButton.getStyleClass().add("add-button");
        addNewButton.setOnAction(e -> showAddDishDialog());

        form.getChildren().addAll(
                formTitle,
                imagePane,
                imagePathLabel,
                new Separator(),
                new Label("Name:"), nameField,
                new Label("Price:"), priceField,
                new Label("Inventory:"), inventoryField,
                new Separator(),
                updateButton,
                removeButton,
                addNewButton
        );

        return form;
    }

    private void showAddDishDialog() {
        Stage addDishStage = new Stage();
        addDishStage.initModality(Modality.APPLICATION_MODAL);
        addDishStage.initOwner(managerStage);
        addDishStage.setTitle("Add New Dish");

        VBox form = new VBox(10);
        form.setPadding(new Insets(20));
        form.setAlignment(Pos.TOP_LEFT);
        form.setPrefWidth(400);

        Label titleLabel = new Label("Add New Dish");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        TextField newNameField = new TextField();
        newNameField.setPromptText("Enter dish name");

        TextField newPriceField = new TextField();
        newPriceField.setPromptText("Enter price, e.g., 9.99");

        TextField newInventoryField = new TextField();
        newInventoryField.setPromptText("Enter initial inventory");

        ImageView newImageView = new ImageView();
        newImageView.setFitHeight(150);
        newImageView.setFitWidth(150);
        newImageView.setPreserveRatio(true);

        StackPane newImagePane = new StackPane(newImageView);
        newImagePane.setStyle("-fx-border-color: lightgrey; -fx-border-width: 1; -fx-background-color: #f4f4f4;");
        newImagePane.setPrefSize(160, 160);
        newImagePane.setAlignment(Pos.CENTER);

        Label newImageLabel = new Label("No image selected.");
        newImageLabel.setWrapText(true);

        Button selectImageBtn = new Button("Select Image...");
        final File[] selectedFile = new File[1];

        selectImageBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Dish Image");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
            );

            File file = fileChooser.showOpenDialog(addDishStage);
            if (file != null) {
                selectedFile[0] = file;
                newImageLabel.setText(file.getName());
                try {
                    Image previewImage = new Image(new FileInputStream(file));
                    if (previewImage.isError()) {
                        System.err.println("Image preview load error for: " + file.getName());
                        newImageView.setImage(new UserInterface().createPlaceholder(150, 150));
                    } else {
                        newImageView.setImage(previewImage);
                    }
                } catch (FileNotFoundException ex) {
                    System.err.println("Image preview exception: " + ex.getMessage());
                    ex.printStackTrace();
                    newImageView.setImage(new UserInterface().createPlaceholder(150, 150));
                }
            }
        });

        Button submitButton = new Button("Add");
        submitButton.getStyleClass().add("add-button");
        Button cancelButton = new Button("Cancel");

        HBox buttonBox = new HBox(10, submitButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        form.getChildren().addAll(
                titleLabel,
                new Label("Dish Name:"), newNameField,
                new Label("Price:"), newPriceField,
                new Label("Inventory:"), newInventoryField,
                new Label("Image:"),
                newImagePane,
                selectImageBtn,
                newImageLabel,
                new Separator(),
                buttonBox
        );

        submitButton.setOnAction(e -> {

            String name = newNameField.getText().trim();
            String priceStr = newPriceField.getText().trim();
            String inventoryStr = newInventoryField.getText().trim();

            if (name.isEmpty() || priceStr.isEmpty()) {
                showAlert(AlertType.ERROR, "Input Error",
                        "Name and Price cannot be empty.");
                return;
            }

            double price;
            int inventory = 0;

            try {
                price = Double.parseDouble(priceStr);
                if (price < 0) {
                    showAlert(AlertType.ERROR, "Input Error",
                            "Price cannot be negative.");
                    return;
                }
            } catch (NumberFormatException ex) {
                showAlert(AlertType.ERROR, "Input Error",
                        "Invalid price format.");
                return;
            }

            if (!inventoryStr.isEmpty()) {
                try {
                    inventory = Integer.parseInt(inventoryStr);
                    if (inventory < 0) {
                        showAlert(AlertType.WARNING, "Input Warning",
                                "Inventory set to 0 due to invalid input.");
                        inventory = 0;
                    }
                } catch (NumberFormatException ex) {
                    showAlert(AlertType.WARNING, "Input Warning",
                            "Inventory set to 0 due to invalid input.");
                    inventory = 0;
                }
            }

            addNewDish(name, price, inventory, selectedFile[0]);
            addDishStage.close();
        });

        cancelButton.setOnAction(e -> addDishStage.close());


        Scene scene = new Scene(form);

        addDishStage.setScene(scene);
        addDishStage.setResizable(false);
        addDishStage.show();
    }

    private void addNewDish(String name, double price, int inventory, File imageFile) {

        if (imageFile == null) {
            Alert confirmation = new Alert(AlertType.CONFIRMATION);
            confirmation.setTitle("Missing Image");
            confirmation.setHeaderText("No image selected for the new dish.");
            confirmation.setContentText("Do you want to add the dish without an image (a default placeholder will be used)?");

            ButtonType yesButton = new ButtonType("Yes");
            ButtonType noButton = new ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE);

            confirmation.getButtonTypes().setAll(yesButton, noButton);

            Optional<ButtonType> result = confirmation.showAndWait();
            if (!(result.isPresent() && result.get() == yesButton)) {
                return;
            }
        }

        UserInterface.Dish newOriginalDish = DishData.addNewDish(name, price, imageFile);

        if (newOriginalDish != null) {

            ManagerDish newManagerDish = new ManagerDish(newOriginalDish);

            DishData.insertInventory(newOriginalDish.getId(), inventory);

            newManagerDish.setInventory(inventory);

            dishData.add(newManagerDish);
            dishTable.sort();
            dishTable.getSelectionModel().select(newManagerDish);

            dishTable.refresh();

            showAlert(AlertType.INFORMATION, "Success", "New dish added successfully!");
        } else {
            showAlert(AlertType.ERROR, "Database Error", "Failed to add the new dish. Check logs.");
        }
    }

    private void populateDishDetails(ManagerDish managerDish) {
        if (managerDish != null) {
            nameField.setText(managerDish.getName());
            priceField.setText(String.format("%.2f", managerDish.getPrice()));
            inventoryField.setText(String.valueOf(managerDish.getInventory())); // Display in-memory inventory
            imagePathLabel.setText(managerDish.getImageName()); // Show original file name
            selectedImageFile = null; // Reset selected file

            // Load and display the image using the full relative path
            try {

                String imagePath = managerDish.getImagePath();
                File imgFile = new File("src/" + imagePath);
                if (!imgFile.exists()) {
                    imgFile = new File(imagePath);
                }

                Image img;
                if (imgFile.exists()) {
                    img = new Image(imgFile.toURI().toString());
                } else {
                    img = new UserInterface().createPlaceholder(200, 180);
                }
                dishImageView.setImage(img);
            } catch (Exception e) {
                System.err.println("Image exception: " + e.getMessage());
                e.printStackTrace();
                dishImageView.setImage(new UserInterface().createPlaceholder(200, 180));
            }
        }
    }

    private void clearDishDetailsForm() {
        dishTable.getSelectionModel().clearSelection();
        nameField.clear();
        priceField.clear();
        inventoryField.clear();
        imagePathLabel.setText("No image selected.");
        dishImageView.setImage(null);
        selectedImageFile = null;
        nameField.requestFocus();
    }

    private void handleUpdateDish() {
        ManagerDish selectedManagerDish = dishTable.getSelectionModel().getSelectedItem();
        if (selectedManagerDish == null) {
            showAlert(AlertType.WARNING, "Selection Error", "Please select a dish to update.");
            return;
        }

        String name = nameField.getText().trim();
        String priceStr = priceField.getText().trim();
        String inventoryStr = inventoryField.getText().trim(); // Get inventory input

        if (name.isEmpty() || priceStr.isEmpty()) {
            showAlert(AlertType.ERROR, "Input Error", "Name and Price cannot be empty.");
            return;
        }

        double price;
        int inventory;

        try {
            price = Double.parseDouble(priceStr);
            if (price < 0) {
                showAlert(AlertType.ERROR, "Input Error", "Price cannot be negative.");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert(AlertType.ERROR, "Input Error", "Invalid price format.");
            return;
        }

        // Parse inventory, keep existing if invalid input
        try {
            inventory = Integer.parseInt(inventoryStr);
            if (inventory < 0) {
                showAlert(AlertType.WARNING, "Input Warning", "Inventory not changed due to invalid input.");
                inventory = selectedManagerDish.getInventory(); // Keep old value
            }
        } catch (NumberFormatException e) {
            showAlert(AlertType.WARNING, "Input Warning", "Inventory not changed due to invalid input.");
            inventory = selectedManagerDish.getInventory(); // Keep old value
        }

        // Create a temporary UserInterface.Dish object with updated DB-relevant info
        // Use the ID from the selected item and the new name/price from the form
        UserInterface.Dish dishForDbUpdate = new UserInterface.Dish(
                selectedManagerDish.getId(),
                name,
                price,
                selectedManagerDish.getImageName()
        );

        // Call DishData to update the dish in the database
        // Pass the temporary dish object and the potentially new image file
        boolean dbSuccess = DishData.updateDish(dishForDbUpdate, selectedImageFile);

        if (dbSuccess) {

            selectedManagerDish.setInventory(inventory);

            loadDishData();
            clearDishDetailsForm();

            showAlert(AlertType.INFORMATION, "Success", "Dish updated successfully!");
        } else {
            showAlert(AlertType.ERROR, "Database Error", "Failed to update dish. Check logs.");
        }
    }

    private void handleRemoveDish() {
        ManagerDish selectedManagerDish = dishTable.getSelectionModel().getSelectedItem();
        if (selectedManagerDish == null) {
            showAlert(AlertType.WARNING, "Selection Error", "Please select a dish to remove.");
            return;
        }

        Alert confirmation = new Alert(AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm Deletion");
        confirmation.setHeaderText("Remove Dish: " + selectedManagerDish.getName());
        confirmation.setContentText("Are you sure you want to permanently remove this dish and its image?");
        confirmation.getDialogPane().setPrefSize(450, 200);
        confirmation.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            DishData.deleteInventory(selectedManagerDish.getId());
            // Call DishData to delete the dish using its ID
            boolean success = DishData.deleteDish(selectedManagerDish.getId());

            if (success) {
                dishData.remove(selectedManagerDish); // Remove from the observable list
                clearDishDetailsForm();
                showAlert(AlertType.INFORMATION, "Success", "Dish removed successfully!");
            } else {
                showAlert(AlertType.ERROR, "Database Error", "Failed to remove dish. Check logs.");
            }
        }
    }

    private void loadDishData() {
        new Thread(() -> {
            List<UserInterface.Dish> dishesFromDb = DishData.loadDishesFromDatabase();
            // Convert to ManagerDish wrappers
            List<ManagerDish> managerDishes = dishesFromDb.stream()
                    .map(ManagerDish::new)
                    .collect(Collectors.toList());
            Platform.runLater(() -> {
                dishData.setAll(managerDishes); // Update table with wrappers
                if (!dishData.isEmpty()) {
                    dishTable.getSelectionModel().selectFirst();
                } else {
                    clearDishDetailsForm();
                }
                dishTable.sort(); // Apply initial sort if needed
                System.out.println("Dish data loaded/reloaded.");
            });
        }).start();
    }

    private BorderPane createOrderManagementPane() {
        BorderPane orderPane = new BorderPane();
        orderPane.setPadding(new Insets(15));

        orderTable = new TableView<>();
        orderTable.setPlaceholder(new Label("No orders to display"));
        setupOrderTableColumns();
        orderData = FXCollections.observableArrayList();
        orderTable.setItems(orderData);
        orderPane.setCenter(orderTable);

        HBox actionBox = new HBox(10);
        actionBox.setPadding(new Insets(10, 0, 0, 0));
        actionBox.setAlignment(Pos.CENTER_LEFT);

        Button markCompletedButton = new Button("Mark Selected as Completed");
        markCompletedButton.getStyleClass().add("update-button");
        markCompletedButton.setOnAction(e -> handleMarkOrderCompleted());
        markCompletedButton.setPrefWidth(200);
        markCompletedButton.setPrefHeight(40);

        Button refreshButton = new Button("Refresh Orders");
        refreshButton.setOnAction(e -> loadOrderData());
        refreshButton.setPrefWidth(150);
        refreshButton.setPrefHeight(35);

        actionBox.getChildren().addAll(markCompletedButton, refreshButton);
        orderPane.setBottom(actionBox);

        return orderPane;
    }

    private boolean isOrderDetailsDialogShowing = false;

    private void setupOrderTableColumns() {
        // ID Column
        TableColumn<OrderData.Order, Integer> orderIdCol = new TableColumn<>("Order ID");
        orderIdCol.setCellValueFactory(new PropertyValueFactory<>("orderId"));
        orderIdCol.setPrefWidth(80);

        TableColumn<OrderData.Order, String> usernameCol = new TableColumn<>("Username");
        usernameCol.setCellValueFactory(new PropertyValueFactory<>("username"));
        usernameCol.setPrefWidth(150);

        TableColumn<OrderData.Order, Double> totalPriceCol = new TableColumn<>("Total Price");
        totalPriceCol.setCellValueFactory(new PropertyValueFactory<>("totalPrice"));
        totalPriceCol.setPrefWidth(100);
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance();
        totalPriceCol.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                setText(empty || price == null ? null : String.format("%.2f$",price));
            }
        });

        TableColumn<OrderData.Order, String> paymentMethodCol = new TableColumn<>("Payment Method");
        paymentMethodCol.setCellValueFactory(new PropertyValueFactory<>("paymentMethod"));
        paymentMethodCol.setPrefWidth(130);

        TableColumn<OrderData.Order, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(100);

        TableColumn<OrderData.Order, java.sql.Timestamp> orderTimeCol = new TableColumn<>("Order Time");
        orderTimeCol.setCellValueFactory(new PropertyValueFactory<>("orderTime"));
        orderTimeCol.setPrefWidth(180);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        //use Malaysia time.
        dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        orderTimeCol.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(java.sql.Timestamp time, boolean empty) {
                super.updateItem(time, empty);
                setText(empty || time == null ? null : dateFormat.format(time));
            }
        });

        orderTable.getColumns().addAll(orderIdCol, usernameCol, totalPriceCol, paymentMethodCol, statusCol, orderTimeCol);
        orderTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        orderTable.getSortOrder().add(orderTimeCol); // Default sort by time descending
        orderTimeCol.setSortType(TableColumn.SortType.DESCENDING);

        orderTable.setRowFactory(tv -> {
            TableRow<OrderData.Order> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 1 && (!row.isEmpty())) {
                    showOrderDetails(row.getItem());
                }
            });
            return row;
        });
    }

    private void showOrderDetails(OrderData.Order order) {
        if (isOrderDetailsDialogShowing) {
            return;
        }

        isOrderDetailsDialogShowing = true;

        // Get order items
        List<OrderData.Order.OrderItem> items = OrderData.Order.getOrderItems(order.getOrderId());

        StringBuilder content = new StringBuilder();
        content.append("Order ID: ").append(order.getOrderId()).append("\n");
        content.append("Username: ").append(order.getUsername()).append("\n");
        content.append("Total Price: $").append(String.format("%.2f", order.getTotalPrice())).append("\n");
        content.append("Payment Method: ").append(order.getPaymentMethod()).append("\n");
        content.append("Order Status: ").append(order.getStatus()).append("\n\n");
        content.append("Order Details:\n");
        content.append("--------------------------------\n");

        if (items.isEmpty()) {
            content.append("No items found for this order");
        } else {
            content.append(String.format("%-25s %-8s %-10s %-10s\n", "Item Name", "Quantity", "Unit Price", "Subtotal"));
            for (OrderData.Order.OrderItem item : items) {
                content.append(String.format("%-25s %-8d $%-9.2f $%-9.2f\n",
                        item.getDishName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getItemTotal()));
            }
        }

        // Create and display the Alert
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Order Details");
        alert.setHeaderText("Order #" + order.getOrderId() + " Details");

        // Use TextArea to display content for better formatting
        TextArea textArea = new TextArea(content.toString());
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefHeight(300);
        textArea.setPrefWidth(400);

        alert.getDialogPane().setContent(textArea);
        alert.getDialogPane().setPrefWidth(450);

        Button confirmButton = (Button) alert.getDialogPane().lookupButton(ButtonType.OK);
        confirmButton.setText("Confirm");

        // Reset the flag when the Alert is closed
        alert.setOnCloseRequest(e -> isOrderDetailsDialogShowing = false);
        alert.setOnHidden(e -> isOrderDetailsDialogShowing = false);

        alert.showAndWait();
    }


    private void handleMarkOrderCompleted() {
        OrderData.Order selectedOrder = orderTable.getSelectionModel().getSelectedItem();
        if (selectedOrder == null) {
            showAlert(AlertType.WARNING, " Selection Error", "Please select an order.");
            return;
        }

        if (selectedOrder.getStatus().equalsIgnoreCase("Completed")) {
            showAlert(AlertType.INFORMATION, "Order Status", "This order is already marked as completed");
            return;
        }

        Alert confirmation = new Alert(AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm Status Change");
        confirmation.setHeaderText("Mark Order " + selectedOrder.getOrderId() + " as Completed?");
        confirmation.setContentText(" Are you sure to change the status to 'Completed'?");

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            boolean success = OrderData.updateOrderStatus(selectedOrder.getOrderId(), "Completed");

            if (success) {
                selectedOrder.setStatus("Completed");
                orderTable.refresh(); // Refresh table view row
                showAlert(AlertType.INFORMATION, "Success", "Order status updated to Completed!");
            } else {
                showAlert(AlertType.ERROR, "Database Error", "Failed to update order status. Check logs.");
            }
        }
    }

    private void loadOrderData() {
        new Thread(() -> {
            List<OrderData.Order> ordersFromDb = OrderData.loadOrdersFromDatabase();
            Platform.runLater(() -> {
                orderData.setAll(ordersFromDb);
                orderTable.sort(); // Apply sort after loading data
                System.out.println("Order data loaded/reloaded.");
            });
        }).start();
    }

    private void handleLogout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Logout");
        alert.setHeaderText(null);
        alert.setContentText("Do you want to log out?");

        ButtonType yesBtn = new ButtonType("Yes", ButtonBar.ButtonData.YES);
        ButtonType noBtn = new ButtonType("No", ButtonBar.ButtonData.NO);
        alert.getButtonTypes().setAll(yesBtn, noBtn);

        alert.showAndWait().ifPresent(response -> {
            if (response == yesBtn) {
                managerStage.close();
                if (primaryStage != null) {
                    primaryStage.show();
                }
            }
        });
    }
    private void showAlert(AlertType type, String title, String content) {

        new UserInterface().showAlert(title, content);
    }
}

