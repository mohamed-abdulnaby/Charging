import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    // Default fallback credentials for local development
    private static final String DEFAULT_URL = "jdbc:postgresql://localhost:5432/charging";
    private static final String DEFAULT_USER = "postgres";
    private static final String DEFAULT_PASSWORD = "***REDACTED***";

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("PostgreSQL JDBC Driver not found.", e);
        }

        // Check environment variable overrides (provided in compose.yaml or shell env)
        String envUrl = System.getenv("DB_URL");
        String url = (envUrl != null) ? envUrl : DEFAULT_URL;

        String envUser = System.getenv("DB_USER");
        String user = (envUser != null) ? envUser : DEFAULT_USER;

        String envPass = System.getenv("DB_PASSWORD");
        String password = (envPass != null) ? envPass : DEFAULT_PASSWORD;

        // Log the connection target (safely hiding the password if included in URL)
        System.out.println("[DB] Connecting to database: " + url.replaceAll(":[^@]+@", ":****@"));
        return DriverManager.getConnection(url, user, password);
    }
}