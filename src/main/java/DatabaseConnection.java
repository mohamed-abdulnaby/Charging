import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseConnection {
    // Default fallback credentials for local development
    private static final String DEFAULT_URL = "jdbc:postgresql://localhost:5432/charging";
    private static final String DEFAULT_USER = "postgres";
    private static final String DEFAULT_PASSWORD = "***REDACTED***";

    private static final HikariDataSource dataSource;

    static {
        // Check environment variable overrides (provided in compose.yaml or shell env)
        String envUrl = System.getenv("DB_URL");
        String url = (envUrl != null) ? envUrl : DEFAULT_URL;

        String envUser = System.getenv("DB_USER");
        String user = (envUser != null) ? envUser : DEFAULT_USER;

        String envPass = System.getenv("DB_PASSWORD");
        String password = (envPass != null) ? envPass : DEFAULT_PASSWORD;

        // Log the connection target (safely hiding the password if included in URL)
        System.out.println("[DB] Initializing HikariCP connection pool...");
        System.out.println("[DB] Target URL: " + url.replaceAll(":[^@]+@", ":****@"));

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");

        // Connection pool tuning configurations
        config.setMaximumPoolSize(20); // Reuse up to 20 connections concurrently
        config.setMinimumIdle(5);
        config.setIdleTimeout(300000); // 5 minutes
        config.setConnectionTimeout(20000); // 20 seconds timeout to prevent hanging calls
        config.setLeakDetectionThreshold(5000); // 5 seconds leak detection logger

        dataSource = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}