import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class DishData {

    public static final String IMAGE_DIRECTORY = "src/resources";
    private static final String PLACEHOLDER_IMAGE = "placeholder.png";
    public static final String PLACEHOLDER_IMAGE_PATH = IMAGE_DIRECTORY + "/" + PLACEHOLDER_IMAGE;

    public static void initializeDefaultDishes() {
        if (!hasDishData()) {
            try {

                ensureImageDirectoryExists();
                ensurePlaceholderImageExists();

                List<UserInterface.Dish> dishes = getDefaultDishes();
                insertAllDishes(dishes);
                System.out.println("Default dish data initialized successfully.");
            } catch (SQLException e) {
                System.err.println("Failed to initialize default dish data: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            // Ensure directory and placeholder exist even if data is already present
            ensureImageDirectoryExists();
            ensurePlaceholderImageExists();
        }
    }

    public static List<UserInterface.Dish> loadDishesFromDatabase() {
        List<UserInterface.Dish> dishes = new ArrayList<>();
        // Select only columns present in the 'dishes' table
        String sql = "SELECT id, name, price, image_path FROM dishes ORDER BY id";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                double price = rs.getDouble("price");
                String imagePath = rs.getString("image_path"); // Path stored in DB (e.g., resources/dish1.png)

                String imageName = new File(imagePath).getName(); // Extract filename if needed by Dish constructor

                dishes.add(new UserInterface.Dish(id, name, price, imagePath)); // Use this if Dish needs full relative path

            }
        } catch (SQLException e) {
            System.err.println("Error loading dish data: " + e.getMessage());
            e.printStackTrace();
        }
        return dishes;
    }

    private static boolean hasDishData() {
        String sql = "SELECT COUNT(*) FROM dishes";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error checking for dish data: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public static void insertAllDishes(List<UserInterface.Dish> dishes) throws SQLException {
        // SQL for inserting/replacing dishes (id, name, price, image_path)
        String sql = "INSERT OR REPLACE INTO dishes (id, name, price, image_path) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);

            for (UserInterface.Dish dish : dishes) {
                // Construct the relative path for storage based on the imageName
                // Assumes imageName in the Dish object is just the file name (e.g., "dish1.png")
                String relativeImagePath = IMAGE_DIRECTORY + "/" + dish.getImageName();

                pstmt.setInt(1, dish.getId());
                pstmt.setString(2, dish.getName());
                pstmt.setDouble(3, dish.getPrice());
                pstmt.setString(4, relativeImagePath);
                pstmt.addBatch();

            }
            pstmt.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            System.err.println("Error inserting multiple dishes: " + e.getMessage());
            throw e;
        }
    }

    public static UserInterface.Dish addNewDish(String name, double price, File imageFile) {
        // 1. Find the next available ID
        int nextId = getNextDishId();
        if (nextId == -1) {
            System.err.println("Failed to determine next dish ID.");
            return null;
        }

        String relativeImagePath; // Path to store in DB
        String imageNameForDishObject; // Just the filename for the Dish object
        if (imageFile != null && imageFile.exists()) {
            // Create a unique name to avoid conflicts, e.g., dish_13_originalName.png
            String targetFileName = "dish" + nextId + ".png";
            relativeImagePath = copyImageToResources(imageFile, targetFileName);
            if (relativeImagePath == null) {
                System.err.println("Failed to copy image file. Using placeholder.");
                relativeImagePath = PLACEHOLDER_IMAGE_PATH;
                imageNameForDishObject = PLACEHOLDER_IMAGE;
            } else {
                imageNameForDishObject = targetFileName; // Store the copied file name
            }
        } else {
            relativeImagePath = PLACEHOLDER_IMAGE_PATH;
            imageNameForDishObject = PLACEHOLDER_IMAGE;
        }

        // 3. Insert into database
        String sql = "INSERT INTO dishes (id, name, price, image_path) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, nextId);
            pstmt.setString(2, name);
            pstmt.setDouble(3, price);
            pstmt.setString(4, relativeImagePath); // Store the full relative path (e.g., resources/dish_13_...)

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                System.out.println("New dish added successfully with ID: " + nextId);
                // Return a new Dish object with the correct data, including the image name used
                // *** Adjust constructor call based on what UserInterface.Dish expects ***

                return new UserInterface.Dish(nextId, name, price, relativeImagePath);

            } else {
                System.err.println("Failed to add new dish to database, no rows affected.");
                deleteImageFile(relativeImagePath);
                return null;
            }
        } catch (SQLException e) {
            System.err.println("Failed to add new dish: " + e.getMessage());
            e.printStackTrace();
            deleteImageFile(relativeImagePath);
            return null;
        }
    }


    public static boolean updateDish(UserInterface.Dish dish, File newImageFile) {
        String currentImagePath = getCurrentImagePath(dish.getId());
        String relativeImagePathToUpdate = currentImagePath;
        String oldImageToDelete = null;

        if (newImageFile != null && newImageFile.exists()) {
            String targetFileName = "dish" + dish.getId() + ".png" ; // Unique name
            String copiedPath = copyImageToResources(newImageFile, targetFileName);

            if (copiedPath != null) {
                relativeImagePathToUpdate = copiedPath;

                if (currentImagePath != null && !currentImagePath.equals(copiedPath) && !currentImagePath.equalsIgnoreCase(PLACEHOLDER_IMAGE_PATH)) {
                    oldImageToDelete = currentImagePath;
                }
            } else {
                System.err.println("Failed to copy new image file for update. Keeping existing image.");
                // Keep relativeImagePathToUpdate as currentImagePath
            }
        }

        // SQL to update name, price, and potentially image_path
        String sql = "UPDATE dishes SET name = ?, price = ?, image_path = ? WHERE id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, dish.getName());
            pstmt.setDouble(2, dish.getPrice());
            pstmt.setString(3, relativeImagePathToUpdate);
            pstmt.setInt(4, dish.getId());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                System.out.println("Dish ID " + dish.getId() + " updated successfully.");

                if (oldImageToDelete != null) {
                    deleteImageFile(oldImageToDelete);
                }
                return true;
            } else {
                System.err.println("Dish ID " + dish.getId() + " not found or no changes made.");
                // If DB update failed, don't delete the old image file
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Error updating dish ID " + dish.getId() + ": " + e.getMessage());
            e.printStackTrace();
            // If DB update failed, don't delete the old image file
            return false;
        }
    }

    public static boolean deleteDish(int dishId) {
        // 1. Get the image path before deleting the DB record
        String imagePathToDelete = getCurrentImagePath(dishId);

        // 2. Delete the database record
        String sqlDelete = "DELETE FROM dishes WHERE id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmtDelete = conn.prepareStatement(sqlDelete)) {

            pstmtDelete.setInt(1, dishId);
            int affectedRows = pstmtDelete.executeUpdate();

            if (affectedRows > 0) {
                System.out.println("Dish ID " + dishId + " deleted successfully from database.");
                // 3. Attempt to delete the image file if it's not the placeholder
                if (imagePathToDelete != null && !imagePathToDelete.equalsIgnoreCase(PLACEHOLDER_IMAGE_PATH)) {
                    deleteImageFile(imagePathToDelete);
                }
                return true;
            } else {
                System.err.println("Dish ID " + dishId + " not found in database.");
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Error deleting dish ID " + dishId + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static int getNextDishId() {
        String sql = "SELECT MAX(id) FROM dishes";
        try (Connection conn = DatabaseConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                int maxId = rs.getInt(1);
                return maxId + 1;
            } else {
                return 1;
            }
        } catch (SQLException e) {
            System.err.println("Error getting next dish ID: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    private static String getCurrentImagePath(int dishId) {
        String sqlSelect = "SELECT image_path FROM dishes WHERE id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmtSelect = conn.prepareStatement(sqlSelect)) {
            pstmtSelect.setInt(1, dishId);
            ResultSet rs = pstmtSelect.executeQuery();
            if (rs.next()) {
                return rs.getString("image_path");
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving image path for dish ID " + dishId + ": " + e.getMessage());
        }
        return null;
    }

    private static String copyImageToResources(File sourceFile, String targetFileName) {

        File targetDir = new File("src/resources");
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        Path sourcePath = sourceFile.toPath();

        Path targetPath = Paths.get("src/resources", targetFileName);

        try {
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Image copied successfully to: " + targetPath.toAbsolutePath());

            return "resources/" + targetFileName;
        } catch (IOException e) {
            System.err.println("From " + sourcePath + " copy image file to  " + targetPath + " failed");
            e.printStackTrace();
            return null;
        }
    }

    private static void deleteImageFile(String relativeImagePath) {

        if (relativeImagePath == null || relativeImagePath.isEmpty() || relativeImagePath.equalsIgnoreCase(PLACEHOLDER_IMAGE_PATH)) {
            System.out.println("Skipping deletion for null, empty, or placeholder image path: " + relativeImagePath);
            return;
        }
        try {
            Path pathToDelete = Paths.get("src", relativeImagePath);

            if (Files.exists(pathToDelete)) {
                Files.delete(pathToDelete);
                System.out.println("Deleted image file: " + pathToDelete.toAbsolutePath());
            } else {
                System.out.println("Image file not found for deletion: " + pathToDelete.toAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("Error deleting image file " + relativeImagePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void ensureImageDirectoryExists() {
        try {
            Path dirPath = Paths.get("src/resources");
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                System.out.println("Created image directory: " + dirPath.toAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("Fatal Error: Failed to create image directory 'src/resources' failed: " + e.getMessage());
        }
    }

    private static void ensurePlaceholderImageExists() {
        Path placeholderPath = Paths.get(PLACEHOLDER_IMAGE_PATH);
    }

    public static void insertInventory(int dishId, int inventory) {
        String sql = "INSERT OR REPLACE INTO dish_inventory (dish_id, inventory) VALUES (?, ?)";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, dishId);
            ps.setInt(2, inventory);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("insertInventory: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static int getInventory(int dishId) {
        String sql = "SELECT inventory FROM dish_inventory WHERE dish_id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, dishId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("inventory");
                }
            }
        } catch (SQLException e) {
            System.err.println("getInventory: " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }


    public static void updateInventory(int dishId, int inventory) {
        String sql = "UPDATE dish_inventory SET inventory = ? WHERE dish_id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, inventory);
            ps.setInt(2, dishId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("updateInventory: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void deleteInventory(int dishId) {
        String sql = "DELETE FROM dish_inventory WHERE dish_id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, dishId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("deleteInventory: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<UserInterface.Dish> getDefaultDishes() {
        List<UserInterface.Dish> dishes = new ArrayList<>();
        // Image names here are just the file names, assumed to be in the IMAGE_DIRECTORY
        dishes.add(new UserInterface.Dish(1, "Sparkling orange juice", 8.50, "dish1.png"));
        dishes.add(new UserInterface.Dish(2, "Mexico wrap", 7.50, "dish2.png"));
        dishes.add(new UserInterface.Dish(3, "Japanese sushi box", 10.00, "dish3.png"));
        dishes.add(new UserInterface.Dish(4, "Multi-flavored fruit jelly", 16.60, "dish4.png"));
        dishes.add(new UserInterface.Dish(5, "Freshly fried French fries", 8.80, "dish5.png"));
        dishes.add(new UserInterface.Dish(6, "Double-baked thick soup", 11.50, "dish6.png"));
        dishes.add(new UserInterface.Dish(7, "Smoked grilled meat", 9.50, "dish7.png"));
        dishes.add(new UserInterface.Dish(8, "Crab shell fried rice", 13.00, "dish8.png"));
        dishes.add(new UserInterface.Dish(9, "Energy drink", 5.60, "dish9.png"));
        dishes.add(new UserInterface.Dish(10, "Toast whole pig", 18.80, "dish10.png"));
        dishes.add(new UserInterface.Dish(11, "Smoked Ham set", 15.50, "dish11.png"));
        dishes.add(new UserInterface.Dish(12, "Amber Flowing Gold", 23.30, "dish12.png"));
        return dishes;
    }
}