import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrderData {

    public static class Order {
        private final SimpleIntegerProperty orderId;
        private final SimpleStringProperty username;
        private final SimpleDoubleProperty totalPrice;
        private final SimpleStringProperty paymentMethod;
        private final SimpleStringProperty status;
        private final Timestamp orderTime;

        public Order(int orderId, String username, double totalPrice, String paymentMethod, String status, Timestamp orderTime) {
            this.orderId = new SimpleIntegerProperty(orderId);
            this.username = new SimpleStringProperty(username);
            this.totalPrice = new SimpleDoubleProperty(totalPrice);
            this.paymentMethod = new SimpleStringProperty(paymentMethod);
            this.status = new SimpleStringProperty(status);
            this.orderTime = orderTime;
        }

        // Add the OrderItem inner class to OrderData.java
        public static class OrderItem {
            private final SimpleIntegerProperty itemId;
            private final SimpleIntegerProperty dishId;
            private final SimpleStringProperty dishName;
            private final SimpleIntegerProperty quantity;
            private final SimpleDoubleProperty unitPrice;
            private final SimpleDoubleProperty itemTotal;

            public OrderItem(int itemId, int dishId, String dishName, int quantity, double unitPrice, double itemTotal) {
                this.itemId = new SimpleIntegerProperty(itemId);
                this.dishId = new SimpleIntegerProperty(dishId);
                this.dishName = new SimpleStringProperty(dishName);
                this.quantity = new SimpleIntegerProperty(quantity);
                this.unitPrice = new SimpleDoubleProperty(unitPrice);
                this.itemTotal = new SimpleDoubleProperty(itemTotal);
            }

            public String getDishName() { return dishName.get(); }
            public int getQuantity() { return quantity.get(); }
            public double getUnitPrice() { return unitPrice.get(); }
            public double getItemTotal() { return itemTotal.get(); }

        }

        public static List<OrderItem> getOrderItems(int orderId) {
            List<OrderItem> items = new ArrayList<>();
            String sql = "SELECT item_id, dish_id, dish_name, quantity, unit_price, item_total " +
                    "FROM order_items WHERE order_id = ?";

            try (Connection conn = DatabaseConnector.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, orderId);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int itemId = rs.getInt("item_id");
                        int dishId = rs.getInt("dish_id");
                        String dishName = rs.getString("dish_name");
                        int quantity = rs.getInt("quantity");
                        double unitPrice = rs.getDouble("unit_price");
                        double itemTotal = rs.getDouble("item_total");

                        items.add(new OrderItem(itemId, dishId, dishName, quantity, unitPrice, itemTotal));
                    }
                }
            } catch (SQLException e) {
                System.err.println("Error loading order items: " + e.getMessage());
                e.printStackTrace();
            }
            return items;
        }

        // --- Getters for properties ---
        public int getOrderId() { return orderId.get(); }
        public String getUsername() { return username.get(); }
        public double getTotalPrice() { return totalPrice.get(); }
        public String getPaymentMethod() { return paymentMethod.get(); }
        public String getStatus() { return status.get(); }
        public Timestamp getOrderTime() {return orderTime;}

        // --- Setter for status (used after DB update) ---
        public void setStatus(String status) {
            this.status.set(status);
        }

        @Override
        public String toString() {
            return "Order{" +
                    "orderId=" + orderId.get() +
                    ", username='" + username.get() + '\'' +
                    ", status='" + status.get() + '\'' +
                    '}';
        }
    }

    public static List<Order> loadOrdersFromDatabase() {
        List<Order> orders = new ArrayList<>();
        // Select necessary columns from the orders table
        String sql = "SELECT order_id, username, total_price, payment_method, status, order_time FROM orders ORDER BY order_time DESC";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                int orderId = rs.getInt("order_id");
                String username = rs.getString("username");
                double totalPrice = rs.getDouble("total_price");
                String paymentMethod = rs.getString("payment_method");
                String status = rs.getString("status");
                Timestamp orderTime = rs.getTimestamp("order_time");

                // Create OrderData.Order object
                orders.add(new Order(orderId, username, totalPrice, paymentMethod, status, orderTime));
            }
        } catch (SQLException e) {
            System.err.println("Error loading order data: " + e.getMessage());
            e.printStackTrace(); // Log the stack trace for debugging
        }
        return orders;
    }

    public static boolean updateOrderStatus(int orderId, String newStatus) {
        String sql = "UPDATE orders SET status = ? WHERE order_id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newStatus);
            pstmt.setInt(2, orderId);

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                System.out.println("Order ID " + orderId + " status updated to " + newStatus);
                return true;
            } else {
                // This could happen if the order_id doesn't exist
                System.err.println("Order ID " + orderId + " not found or status not updated.");
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Error updating status for order ID " + orderId + ": " + e.getMessage());
            e.printStackTrace(); // Log the stack trace
            return false;
        }
    }
}
