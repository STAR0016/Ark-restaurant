import java.sql.*;
import java.util.Random;


//User Data Access Object, Handles database operations for users
public class UserData {

    //Register a new user.
    public static int registerUser(String username, String password, boolean isManager) {
        Random random = new Random();
        int userId = 10000 + random.nextInt(90000);

        while (isUserIdExists(userId, isManager)) {
            userId = 10000 + random.nextInt(90000);
        }

        String table = isManager ? "managers" : "users";
        String sql = "INSERT INTO " + table + " (id, username, password) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.setString(2, username);
            stmt.setString(3, password);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                return userId;
            }
            return -1;
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }

    }

    //check if userID already exists.
    public static boolean isUserIdExists(int userId, boolean isManager) {
        String table = isManager ? "managers" : "users";
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    //Check if username already exists.
    public static boolean isUsernameExists(String username, boolean isManager) {
        // Determine which table to use based on user type
        String table = isManager ? "managers" : "users";
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE username = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // Validate login credentials
    public static boolean checkUsernameExists(String table, String username) {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE username = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean checkPassword(String table, String username, String password) {
        String sql = "SELECT password FROM " + table + " WHERE username = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getString(1).equals(password);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static int getUserId(String table, String username) {
        String sql = "SELECT id FROM " + table + " WHERE username = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt(1) : -1;
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }
}
