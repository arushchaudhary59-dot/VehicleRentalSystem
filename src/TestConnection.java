import java.sql.*;

public class TestConnection {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/"
                + "?useSSL=false&allowPublicKeyRetrieval=true";
        try (Connection con = DriverManager.getConnection(url, "root", "sqlarush")) {
            System.out.println("Connected to MySQL " + con.getMetaData().getDatabaseProductVersion());
        } catch (SQLException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }
}