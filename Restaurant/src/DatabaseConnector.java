import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.File;
import java.sql.Statement;

//Database connection manager class
public class DatabaseConnector {
    private static final String DB_FILE = "restaurant_system.db";
    private static final String URL = "jdbc:sqlite:" + DB_FILE;

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");

            File dbFile = new File(DB_FILE);
            File parentDir = dbFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            Connection conn = DriverManager.getConnection(URL);

            Statement stmt = conn.createStatement();
            stmt.execute("PRAGMA foreign_keys = ON");
            stmt.close();

            initDatabase(conn);

            return conn;
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e);
        }
    }

    private static void initDatabase(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();

        stmt.execute("CREATE TABLE IF NOT EXISTS managers ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "username varchar(50) NOT NULL UNIQUE, "
                + "password varchar(100) NOT NULL, "
                + "Time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

        stmt.execute("CREATE TABLE IF NOT EXISTS users ("
                + "id int(11) NOT NULL, "
                + "username varchar(50) NOT NULL UNIQUE, "
                + "password varchar(100) NOT NULL, "
                + "Time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

        stmt.execute("CREATE TABLE IF NOT EXISTS orders ("
                + "order_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "username varchar(50) NOT NULL, "
                + "total_price REAL NOT NULL, "
                + "payment_method varchar(50) NOT NULL, "
                + "status varchar(20) DEFAULT 'Pending', "
                + "order_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "FOREIGN KEY (username) REFERENCES users(username))");

        stmt.execute("CREATE TABLE IF NOT EXISTS order_items ("
                + "item_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "order_id INTEGER NOT NULL, "
                + "dish_id INTEGER NOT NULL, "
                + "dish_name varchar(100) NOT NULL, "
                + "quantity INTEGER NOT NULL, "
                + "unit_price REAL NOT NULL, "
                + "item_total REAL NOT NULL, "
                + "FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE)");

        stmt.execute("CREATE TABLE IF NOT EXISTS dishes ("
                + "id INTEGER PRIMARY KEY, "
                + "name varchar(100) NOT NULL, "
                + "price REAL NOT NULL, "
                + "image_path varchar(200) NOT NULL)");

        stmt.execute("CREATE TABLE IF NOT EXISTS dish_inventory ("
                + "dish_id   INTEGER PRIMARY KEY, "
                + "inventory INTEGER NOT NULL CHECK (inventory >= 0)"
                + ")");

        stmt.close();
    }
}

