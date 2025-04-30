import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.Stage;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class UserInterface {
    private Stage userStage;
    private Stage loginStage;
    private ObservableList<OrderItem> currentOrder;
    private Label totalLabel = new Label("Total: $0.00");
    private TableView<OrderItem> orderTable;
    private String currentUsername;
    private BorderPane root;
    private VBox orderPane;
    private ScrollPane menuPane;

    public static class Dish {
        private final int id;
        private final String name;
        private final double price;
        private final String imageName;

        public Dish(int id, String name, double price, String imageName) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.imageName = imageName;
        }

        // Getter methods
        public int getId() { return id; }
        public String getName() { return name; }
        public double getPrice() { return price; }
        public String getImageName() { return imageName; }
    }

    public static class OrderItem {
        private final int dishId;
        private final String dishName;
        private final IntegerProperty quantity;
        private final double unitPrice;
        private final DoubleProperty totalPrice;

        public OrderItem(int dishId, String dishName, int quantity, double unitPrice) {
            this.dishId = dishId;
            this.dishName = dishName;
            this.unitPrice = unitPrice;
            this.quantity = new SimpleIntegerProperty(quantity);
            this.totalPrice = new SimpleDoubleProperty(quantity * unitPrice);

            this.quantity.addListener((obs, oldVal, newVal) ->
                    totalPrice.set(newVal.intValue() * unitPrice));
        }

        // Getter/Setter
        public int getDishId() { return dishId; }
        public String getDishName() { return dishName; }
        public int getQuantity() { return quantity.get(); }
        public double getTotalPrice() { return totalPrice.get(); }
        public DoubleProperty totalPriceProperty() { return totalPrice; }

        public void increment() { quantity.set(quantity.get() + 1); }
        public void decrement() {
            if (quantity.get() > 0) quantity.set(quantity.get() - 1);
        }
    }

    public void display(String username, Stage loginStage, ObservableList<OrderItem> orderList) {
        this.loginStage = loginStage;
        this.currentOrder = orderList;
        this.currentUsername = username;

        root = new BorderPane();
        root.setPadding(new Insets(15));
        root.getStyleClass().add("root");

        orderPane = createOrderPanel();
        menuPane = createMenuScroll();

        root.setCenter(menuPane);

        if (!currentOrder.isEmpty()) {
            updateTotal();
        }

        setupStage(username, root);
    }

    private ScrollPane createMenuScroll() {
        VBox menuContainer = new VBox(15);
        menuContainer.setPadding(new Insets(10));
        menuContainer.getStyleClass().add("menu-container");

        Label titleLabel = new Label("Ark Restaurant‘s Menu");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 20));
        titleLabel.getStyleClass().add("title-label");

        GridPane menuGrid = new GridPane();
        menuGrid.setHgap(20);
        menuGrid.setVgap(20);
        menuGrid.setPadding(new Insets(10, 10, 20, 10));
        menuGrid.getStyleClass().add("menu-grid");

        List<Dish> menu = loadMenuItems();
        int col = 0, row = 0;
        for (Dish dish : menu) {
            menuGrid.add(createDishCard(dish), col++, row);
            if (col == 5) { col = 0; row++; }
        }

        HBox bottomBar = new HBox(15);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setPadding(new Insets(10, 5, 5, 5));
        bottomBar.getStyleClass().add("bottom-bar");

        totalLabel.setId("totalLabel");

        Button viewOrderBtn = new Button("View Order");
        viewOrderBtn.getStyleClass().add("login-button");
        viewOrderBtn.setOnAction(e -> showOrderPanel());

        Button logoutBtn = new Button("Logout");
        logoutBtn.getStyleClass().add("register-button");
        logoutBtn.setOnAction(e -> handleLogout());

        bottomBar.getChildren().addAll(totalLabel, viewOrderBtn, logoutBtn);

        menuContainer.getChildren().addAll(titleLabel, menuGrid, bottomBar);

        ScrollPane scrollPane = new ScrollPane(menuContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("menu-scroll");
        return scrollPane;
    }

    private VBox createDishCard(Dish dish) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("dish-card");

        ImageView iv = new ImageView();
        iv.setFitWidth(200);
        iv.setFitHeight(180);
        iv.setPreserveRatio(true);

        String imagePath = dish.getImageName(); // e.g. "resources/dish13.png"
        Image img;
        File imgFile = new File("src/" + imagePath);
        if (!imgFile.exists()) {
            imgFile = new File(imagePath);
        }
        if (imgFile.exists()) {
            img = new Image(imgFile.toURI().toString());
        } else {
            img = createPlaceholder(200, 180);
        }
        iv.setImage(img);

        Label name = new Label(dish.getName());
        name.getStyleClass().add("dish-name");

        Label price = new Label(formatPrice(dish.getPrice()));
        price.getStyleClass().add("dish-price");

        Button btn = new Button("Add to Order");
        btn.getStyleClass().add("login-button");
        btn.setMaxWidth(140);
        btn.setOnAction(e -> {
            handleAddDish(dish);
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Added");
            alert.setHeaderText(null);
            alert.setContentText(dish.getName() + " has been added to your order. Continue ordering?");

            ButtonType continueButton = new ButtonType("Continue");
            ButtonType backButton = new ButtonType("View Order");
            alert.getButtonTypes().setAll(continueButton, backButton);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == backButton) {
                showOrderPanel();
            }
        });

        card.getChildren().addAll(iv, name, price, btn);
        return card;
    }



    private String formatPrice(double price) {
        String currencySymbol = "$";
        return String.format("%.2f%s", price, currencySymbol);
    }

    private VBox createOrderPanel() {
        VBox panel = new VBox(50);
        panel.setPadding(new Insets(10));
        panel.setSpacing(20);
        panel.getStyleClass().add("order-panel");

        Label titleLabel = new Label("My Order");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 20));
        titleLabel.getStyleClass().add("title-label");

        orderTable = new TableView<>(currentOrder);
        orderTable.getStyleClass().add("order-table");
        orderTable.setFixedCellSize(40);
        setupTableColumns();
        VBox.setVgrow(orderTable, Priority.ALWAYS);

        Label emptyLabel = new Label("No items in your order");
        emptyLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #555;");
        orderTable.setPlaceholder(emptyLabel);

        HBox bottomBar = new HBox(30);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.getStyleClass().add("bottom-bar");

        Button backToMenuBtn = new Button("Back to Menu");
        backToMenuBtn.getStyleClass().add("login-button");
        backToMenuBtn.setOnAction(e -> showMenuPanel());


        Button checkoutBtn = new Button("Checkout");
        checkoutBtn.getStyleClass().add("register-button");
        checkoutBtn.setOnAction(e -> handleCheckout());

        Button logoutBtn = new Button("Logout");
        logoutBtn.getStyleClass().add("register-button");
        logoutBtn.setOnAction(e -> handleLogout());

        bottomBar.getChildren().addAll(backToMenuBtn, checkoutBtn, logoutBtn);

        panel.getChildren().addAll(titleLabel, orderTable, bottomBar);
        return panel;
    }

    private void showOrderPanel() {
        root.setCenter(orderPane);
        updateTotal();

        if (orderTable != null) {
            orderTable.refresh();
        }

        if (orderTable != null && !currentOrder.isEmpty()) {
            orderTable.scrollTo(0);
        }
    }

    private void showMenuPanel() {
        root.setCenter(menuPane);
    }

    private void setupTableColumns() {

        TableColumn<OrderItem, String> itemCol = new TableColumn<>("Item");
        itemCol.setCellValueFactory(new PropertyValueFactory<>("dishName"));
        itemCol.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        itemCol.setPrefWidth(200);

        TableColumn<OrderItem, Number> totalPriceCol = new TableColumn<>("Subtotal");
        totalPriceCol.setCellValueFactory(param -> param.getValue().totalPriceProperty());
        totalPriceCol.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;-fx-alignment: CENTER-RIGHT;");
        totalPriceCol.setCellFactory(col -> new TableCell<OrderItem, Number>() {
            @Override
            protected void updateItem(Number price, boolean empty) {
                super.updateItem(price, empty);
                setText(empty ? null : formatPrice(price.doubleValue()));
            }
        });
        totalPriceCol.setPrefWidth(120);

        TableColumn<OrderItem, Void> actionCol = new TableColumn<>("Quantity");
        Label quantityHeader = new Label("Quantity");
        quantityHeader.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        quantityHeader.setAlignment(Pos.CENTER);
        quantityHeader.setMaxWidth(Double.MAX_VALUE);
        actionCol.setText(null);
        actionCol.setGraphic(quantityHeader);

        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button addBtn = new Button("+");
            private final Button removeBtn = new Button("-");
            private final Label qtyLabel = new Label();
            private final HBox box = new HBox(15, removeBtn, qtyLabel, addBtn);

            {
                box.setAlignment(Pos.CENTER);
                addBtn.setStyle("-fx-font-size: 14px;");
                removeBtn.setStyle("-fx-font-size: 14px;");
                qtyLabel.setStyle("-fx-font-size: 14px; -fx-padding: 0 8px;");

                addBtn.setOnAction(e -> {
                    OrderItem item = getTableRow().getItem();
                    if (item != null) {
                        item.increment();
                        updateTotal();
                        orderTable.refresh();
                    }
                });
                removeBtn.setOnAction(e -> {
                    OrderItem item = getTableRow().getItem();
                    if (item != null) {
                        item.decrement();
                        if (item.getQuantity() == 0) {
                            currentOrder.remove(item);
                        }
                        updateTotal();
                        orderTable.refresh();
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    qtyLabel.setText(String.valueOf(getTableRow().getItem().getQuantity()));
                    setGraphic(box);
                }
            }
        });
        actionCol.setPrefWidth(180);
        actionCol.setStyle("-fx-alignment: CENTER;");

        orderTable.getColumns().clear();
        orderTable.getColumns().addAll(itemCol,  actionCol,  totalPriceCol );

        orderTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);


    }

    private void handleAddDish(Dish dish) {
        currentOrder.stream()
                .filter(item -> item.getDishId() == dish.getId())
                .findFirst()
                .ifPresentOrElse(
                        OrderItem::increment,
                        () -> currentOrder.add(new OrderItem(
                                dish.getId(),
                                dish.getName(),
                                1,
                                dish.getPrice()
                        ))
                );
        updateTotal();
        if (orderTable != null) {
            orderTable.refresh();
        }
    }

    private void updateTotal() {
        double total = currentOrder.stream()
                .mapToDouble(OrderItem::getTotalPrice)
                .sum();
        totalLabel.setText(String.format("Total: %s", formatPrice(total)));
    }

    private void handleCheckout() {
        if (currentOrder.isEmpty()) {
            showAlert("Empty Order", "Please add items first!");
            return;
        }

        double total = currentOrder.stream().mapToDouble(OrderItem::getTotalPrice).sum();

        Stage checkoutStage = new Stage();
        checkoutStage.setTitle("Confirm Checkout");

        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.CENTER);

        Label totalLabel = new Label("Total: " + formatPrice(total));
        totalLabel.setFont(Font.font("System", FontWeight.BOLD, 18));

        Label paymentLabel = new Label("Select Payment Method:");
        paymentLabel.setFont(Font.font("System", FontWeight.NORMAL, 16));

        ToggleGroup paymentGroup = new ToggleGroup();

        RadioButton creditCardRB = new RadioButton("Credit Card");
        creditCardRB.setFont(Font.font("System", 14));
        creditCardRB.setToggleGroup(paymentGroup);
        creditCardRB.setSelected(true);

        RadioButton cashRB = new RadioButton("Cash");
        cashRB.setFont(Font.font("System", 14));
        cashRB.setToggleGroup(paymentGroup);

        RadioButton applePayRB = new RadioButton("Touchngo ewallet");
        applePayRB.setFont(Font.font("System", 14));
        applePayRB.setToggleGroup(paymentGroup);

        VBox paymentOptions = new VBox(10, creditCardRB, cashRB, applePayRB);
        paymentOptions.setPadding(new Insets(10, 0, 20, 20));

        // Buttons
        HBox buttonBox = new HBox(20);
        buttonBox.setAlignment(Pos.CENTER);

        Button confirmButton = new Button("Confirm");
        confirmButton.getStyleClass().add("confirm-button");
        confirmButton.setPrefWidth(120);

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("confirm-button");
        cancelButton.setPrefWidth(120);

        buttonBox.getChildren().addAll(confirmButton, cancelButton);

        // Add all elements to layout
        layout.getChildren().addAll(totalLabel, paymentLabel, paymentOptions, buttonBox);

        // Set actions for buttons
        confirmButton.setOnAction(e -> {
            // Get selected payment method
            RadioButton selectedRB = (RadioButton) paymentGroup.getSelectedToggle();
            String paymentMethod = selectedRB.getText();

            // Process order with the selected payment method
            processOrder(paymentMethod);

            // Close checkout window
            checkoutStage.close();
        });

        cancelButton.setOnAction(e -> checkoutStage.close());

        // Create scene and show the stage
        Scene scene = new Scene(layout, 500, 300);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        // Try to apply the same styles as main window
        try {
            scene.getStylesheets().add(
                    Objects.requireNonNull(getClass().getResource("/styles.css")).toExternalForm()
            );
        } catch (Exception e) {
            System.out.println("CSS load error: " + e.getMessage());
        }

        checkoutStage.setScene(scene);
        checkoutStage.setResizable(false);
        checkoutStage.showAndWait();
    }

    private void saveOrderToDatabase(String username, String paymentMethod) {
        if (currentOrder.isEmpty()) {
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {

            conn.setAutoCommit(false);

            try {

                double totalOrderPrice = currentOrder.stream()
                        .mapToDouble(OrderItem::getTotalPrice)
                        .sum();

                String orderSql = "INSERT INTO orders " +
                        "(username, total_price, payment_method,order_time) VALUES (?, ?, ?,datetime('now','+8 hours'))";

                int orderId;
                try (java.sql.PreparedStatement prestring = conn.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS)) {
                    prestring.setString(1, username);
                    prestring.setDouble(2, totalOrderPrice);
                    prestring.setString(3, paymentMethod);

                    prestring.executeUpdate();

                    try (java.sql.ResultSet generatedKeys = prestring.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            orderId = generatedKeys.getInt(1);
                        } else {
                            throw new SQLException("Creating order failed, no ID obtained.");
                        }
                    }
                }

                String itemSql = "INSERT INTO order_items (order_id, dish_id, dish_name, quantity, unit_price, item_total) VALUES (?, ?, ?, ?, ?, ?)";

                try (java.sql.PreparedStatement pstmt = conn.prepareStatement(itemSql)) {
                    for (OrderItem item : currentOrder) {
                        if (item.getQuantity() > 0) {
                            double unitPrice = item.getTotalPrice() / item.getQuantity();

                            pstmt.setInt(1, orderId);
                            pstmt.setInt(2, item.getDishId());
                            pstmt.setString(3, item.getDishName());
                            pstmt.setInt(4, item.getQuantity());
                            pstmt.setDouble(5, unitPrice);
                            pstmt.setDouble(6, item.getTotalPrice());

                            pstmt.executeUpdate();
                        }
                    }
                }

                conn.commit();

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Database Error", "Failed to save order: " + e.getMessage());
        }
    }

    private void processOrder(String paymentMethod) {

        saveOrderToDatabase(currentUsername, paymentMethod);
        currentOrder.clear();
        updateTotal();

        showAlert("Order Successful",
                  "Payment completed via " + paymentMethod + "!\nYour order has been saved.");
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
                    userStage.close();
                    Platform.runLater(loginStage::show);
                }
            });
    }

    private void setupStage(String username, BorderPane root) {
        userStage = new Stage();
        userStage.setTitle(username + "'s Ordering System");

        Scene scene = new Scene(root, 1300, 800);
        try {
            scene.getStylesheets().add(
                    Objects.requireNonNull(getClass().getResource("/styles.css")).toExternalForm()
            );
        } catch (Exception e) {
            System.out.println("CSS load error: " + e.getMessage());
        }

        userStage.setScene(scene);
        userStage.setOnCloseRequest(e -> {
            currentOrder.clear();
            if (loginStage != null) Platform.runLater(loginStage::show);
        });
        userStage.show();
    }

    public Image createPlaceholder(double w, double h) {
        Canvas canvas = new Canvas(w, h);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.LIGHTGRAY);
        gc.fillRect(0, 0, w, h);
        gc.setFill(Color.DARKGRAY);
        gc.fillText("No Image", w/2-30, h/2);
        return canvas.snapshot(null, null);
    }

    public void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);

        alert.getDialogPane().setPrefSize(300, 150);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

        ButtonType confirmButton = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(confirmButton);

        alert.showAndWait();
    }

    private List<Dish> loadMenuItems() {

        List<Dish> dishes = DishData.loadDishesFromDatabase();
        if (dishes.isEmpty()) {
            System.err.println("Error: The list of dishes loaded from the database is empty!");
        }
        return dishes;
    }

}