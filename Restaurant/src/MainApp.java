import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import java.util.Locale;
import java.util.regex.Pattern;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.animation.FadeTransition;
import javafx.util.Duration;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;


public class MainApp extends Application {

    private VBox loginPane;
    private VBox registerPane;
    private Stage primaryStage;
    private ObservableList<UserInterface.OrderItem> currentOrder;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        DishData.initializeDefaultDishes();

        currentOrder = FXCollections.observableArrayList();

        loginPane = createLoginPane();
        registerPane = createRegisterPane();

        // Load background image using resource stream (more robust method)
        Image foodImage = null;
        try {
            // Ensure the image photo_1.jpg is in the src/resources directory
            foodImage = new Image(getClass().getResourceAsStream("resources/photo_1.jpg"));
        } catch (Exception e) {
            System.err.println("Unable to load background image: /photo_1.jpg " + e.getMessage());
        }

        ImageView imageView = new ImageView(foodImage);
        imageView.setFitWidth(930);
        imageView.setFitHeight(460);
        imageView.setPreserveRatio(false);

        // Apply blur effect to the image
        GaussianBlur blur = new GaussianBlur(10);
        imageView.setEffect(blur);

        StackPane backgroundPane = new StackPane(imageView);
        StackPane mainPane = new StackPane();
        // Ensure loginPane and registerPane are above the background
        mainPane.getChildren().addAll(backgroundPane, loginPane, registerPane);
        registerPane.setVisible(false); // Initially hide registerPane

        Scene scene = new Scene(mainPane, 930, 460);

        try {
            scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
            System.out.println("CSS loaded successfully."); // Debug information
        } catch (NullPointerException e) {
            System.err.println("Error: Cannot find CSS file: /styles.css. Make sure it's in the src/resources directory.");
        } catch (Exception e) {
            System.err.println("Error loading CSS: " + e.getMessage());
        }

        primaryStage.setTitle("Ark Restaurant Order and Management System");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private VBox createLoginPane() {
        Label title = new Label("Login");
        title.getStyleClass().add("title-label");

        ToggleGroup roleGroup = new ToggleGroup();
        RadioButton adminRadio = new RadioButton("Manager");
        adminRadio.setToggleGroup(roleGroup);

        RadioButton userRadio = new RadioButton("User");
        userRadio.setToggleGroup(roleGroup);
        userRadio.setSelected(true);

        HBox roleBox = new HBox(userRadio, adminRadio);
        roleBox.getStyleClass().add("role-box");

        TextField tfUser = new TextField();
        tfUser.setPromptText("Enter your name");
        tfUser.getStyleClass().add("text-field");

        PasswordField pfPwd = new PasswordField();
        pfPwd.setPromptText("Enter password");
        pfPwd.getStyleClass().add("password-field");

        Button btnLogin = new Button("Login");
        btnLogin.getStyleClass().add("login-button");

        btnLogin.setOnAction(e -> {
            RadioButton selectedRole = (RadioButton) roleGroup.getSelectedToggle();
            boolean isManagerSelected = selectedRole.getText().equals("Manager");
            String selectedTable = isManagerSelected ? "managers" : "users";

            String username = tfUser.getText().trim();
            String password = pfPwd.getText().trim();

            if (username.isEmpty() || password.isEmpty()) {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle("Login Error");
                alert.setHeaderText(null);
                alert.setContentText("Username and password cannot be empty!");
                alert.showAndWait();
                return;
            }

            if (!UserData.checkUsernameExists(selectedTable, username)) {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle("Login Error");
                alert.setHeaderText(null);
                alert.setContentText("Can't find this name in " +
                        (isManagerSelected ? "Manager" : "User") + " database");
                alert.showAndWait();
                return;
            }

            if (!UserData.checkPassword(selectedTable, username, password)) {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle("Login Error");
                alert.setHeaderText(null);
                alert.setContentText("Incorrect password!");
                alert.showAndWait();
                return;
            }

            int userId = UserData.getUserId(selectedTable, username);
            boolean isActualManager = (userId < 10000); // Because ID < 10000 is manager

            if (isManagerSelected != isActualManager) {
                String expectedRole = isManagerSelected ? "Manager" : "User";
                String actualRole = isActualManager ? "Manager" : "User";

                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle("Role Mismatch");
                alert.setHeaderText(null);
                alert.setContentText("Selected role (" + expectedRole +
                        ") doesn't match account type (" + actualRole + ")");
                alert.showAndWait();
                return;
            }

            if (isActualManager) {
                showManagerInterface();
                tfUser.clear();
                pfPwd.clear();
            } else {
                showUserInterface(username);
                tfUser.clear();
                pfPwd.clear();
            }
        });

        Hyperlink registerLink = new Hyperlink("Don't have an account? Register here.");
        registerLink.getStyleClass().addAll("hyperlink", "register-hyperlink");

        registerLink.setOnAction(e -> {
            FadeTransition ftOut = new FadeTransition(Duration.millis(300), loginPane);
            ftOut.setFromValue(1.0);
            ftOut.setToValue(0.0);
            ftOut.setOnFinished(event -> {
                loginPane.setVisible(false);
                registerPane.setOpacity(0.0);
                registerPane.setVisible(true);
                FadeTransition ftIn = new FadeTransition(Duration.millis(300), registerPane);
                ftIn.setFromValue(0.0);
                ftIn.setToValue(1.0);
                ftIn.play();
            });
            ftOut.play();
        });

        VBox box = new VBox(title, roleBox, tfUser, pfPwd, btnLogin, registerLink);
        box.getStyleClass().add("login-pane");
        return box;
    }

    private VBox createRegisterPane() {
        Label title = new Label("Register");
        title.getStyleClass().add("title-label");

        // Role selection (User only)
        ToggleGroup roleGroup = new ToggleGroup();
        RadioButton userRadio = new RadioButton("User");
        userRadio.setToggleGroup(roleGroup);
        userRadio.setSelected(true);
        HBox roleBox = new HBox(userRadio);
        roleBox.getStyleClass().add("role-box");

        TextField tfUser = new TextField();
        tfUser.setPromptText("Enter your name");
        tfUser.getStyleClass().add("text-field");

        PasswordField pfPwd = new PasswordField();
        pfPwd.setPromptText("Enter password");
        pfPwd.getStyleClass().add("password-field");

        Button btnRegister = new Button("Register");
        btnRegister.getStyleClass().add("register-button");

        btnRegister.setOnAction(e -> {
            String username = tfUser.getText().trim();
            String password = pfPwd.getText().trim();

            Pattern usernamePattern = Pattern.compile("^[a-zA-Z0-9_\\p{IsHan}]+$");
            Pattern passwordPattern = Pattern.compile("^[a-zA-Z0-9_]+$");

            if (password.length() < 3) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Registration Error");
                alert.setHeaderText(null);
                alert.setContentText("Too short password，must have least 3 characters");
                alert.showAndWait();
                return;
            }

            if (username.isEmpty() || password.isEmpty()) {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle("Registration Error");
                alert.setHeaderText(null);
                alert.setContentText("Name and password cannot be empty!");
                alert.showAndWait();
                return;
            }

            if (!usernamePattern.matcher(username).matches()) {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle("Registration Error");
                alert.setHeaderText(null);
                alert.setContentText
                        ("Please enter a valid Name. \nOnly letters, numbers, underscores, and Chinese characters are allowed.");
                alert.getDialogPane().setPrefSize(400, 130); // Can keep size adjustment for Alert Dialog
                alert.showAndWait();
                return;
            }

            if (!passwordPattern.matcher(password).matches()) {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle("Registration Error");
                alert.setHeaderText(null);
                alert.setContentText
                        ("Please enter a valid password. \nOnly letters, numbers, and underscores are allowed.");
                alert.getDialogPane().setPrefSize(400, 130);
                alert.showAndWait();
                return;
            }

            if (UserData.isUsernameExists(username, false)) { // Assume isUsernameExists checks user table
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle("Registration Error");
                alert.setHeaderText(null);
                alert.setContentText("Name already exists!");
                alert.showAndWait();
                return;
            }

            int userId = UserData.registerUser(username, password, false);
            if (userId > 0) {
                Alert alert = new Alert(AlertType.INFORMATION);
                alert.setTitle("Registration Successful");
                alert.setHeaderText(null);
                alert.setContentText("Registration successful! Your User ID: " + userId);
                alert.showAndWait();
                tfUser.clear();
                pfPwd.clear();

            } else {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle("Registration Error");
                alert.setHeaderText(null);
                alert.setContentText("Registration failed, please try again later!");
                alert.showAndWait();
            }
        });

        Hyperlink loginLink = new Hyperlink("Already have an account? Login here.");
        loginLink.getStyleClass().addAll("hyperlink", "login-hyperlink");
        // Click link to switch to into interface
        loginLink.setOnAction(e -> {
            FadeTransition ftOut = new FadeTransition(Duration.millis(300), registerPane);
            ftOut.setFromValue(1.0);
            ftOut.setToValue(0.0);
            ftOut.setOnFinished(event -> {
                registerPane.setVisible(false);
                loginPane.setOpacity(0.0);
                loginPane.setVisible(true);
                FadeTransition ftIn = new FadeTransition(Duration.millis(300), loginPane);
                ftIn.setFromValue(0.0);
                ftIn.setToValue(1.0);
                ftIn.play();
            });
            ftOut.play();
        });

        VBox box = new VBox(title, roleBox, tfUser, pfPwd, btnRegister, loginLink);
        box.getStyleClass().add("register-pane");

        return box;
    }

    private void showUserInterface(String username) {
        // Hide the primary stage (login window) when showing the user interface
        primaryStage.hide();
        // Create a new UserInterface instance and display it
        UserInterface userInterface = new UserInterface();
        userInterface.display(username, primaryStage, currentOrder);
    }
    private void showManagerInterface() {

        primaryStage.hide();
        ManagerInterface managerInterface = new ManagerInterface();
        managerInterface.display(primaryStage);

    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ENGLISH);
        launch(args);
    }
}




