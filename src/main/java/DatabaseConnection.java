import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * DatabaseConnection – NeonDB single source.
 *
 * Reads DB_URL from the environment (set in compose.yaml).
 * Fallback defaults point at Neon so every environment uses the same DB.
 */
public class DatabaseConnection {

    // ⚠️  No hardcoded credentials — all values MUST come from environment variables.
    //     Set DB_URL, DB_USER, DB_PASSWORD in .env (see compose.yaml).
    private static final String DEFAULT_URL  =
        "jdbc:postgresql://ep-quiet-sunset-aszvxpyx-pooler.c-4.eu-central-1.aws.neon.tech:5432/neondb?sslmode=require&channel_binding=require";
    private static final String DEFAULT_USER = "neondb_owner";

    private static final HikariDataSource dataSource;

    static {
        String url      = System.getenv("DB_URL");
        String user     = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");

        if (url  == null || url.isBlank())  url  = DEFAULT_URL;
        if (user == null || user.isBlank()) user = DEFAULT_USER;
        if (password == null || password.isBlank()) {
            System.err.println("[DB] ⚠️  DB_PASSWORD environment variable is not set. Set it in .env");
            throw new IllegalStateException("DB_PASSWORD must be provided via environment variable");
        }

        System.out.println("[DB] Connecting to: " + url.replaceAll(":[^:]+@", ":****@"));

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");

        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(20000);
        config.setLeakDetectionThreshold(5000);

        dataSource = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
