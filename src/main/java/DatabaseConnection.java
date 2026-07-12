import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    // ===================================================================
    // CONFIGURATION: Set USE_NEON=true for production (NeonDB),
    //                Set USE_NEON=false for local Docker PostgreSQL
    // ===================================================================
    private static final boolean USE_NEON = true; // ← Toggle this for local dev

    // NeonDB (Production)
    private static final String NEON_HOST = "ep-quiet- sunset- aszvxpyx- pooler. c-4.eu- central-1. aws. neon. tech";
    private static final String NEON_DB = "neondb";
    private static final String NEON_USER = "neondb_ owner";
    private static final String NEON_PASSWORD = "npg_ sIR1NZG0wEeT";

    // Local Docker PostgreSQL
    private static final String LOCAL_HOST = "localhost";
    private static final String LOCAL_DB = "charging";
    private static final String LOCAL_USER = "postgres";
    private static final String LOCAL_PASSWORD = "secretpassword";

    // Env override: Set DB_* env vars to completely override connection string
    private static final String ENV_DB_URL = System.getenv("DB_URL");
    private static final String ENV_DB_USER = System.getenv("DB_USER");
    private static final String ENV_DB_PASSWORD = System.getenv("DB_PASSWORD");

    private static String buildUrl() {
        if (ENV_DB_URL != null) return ENV_DB_URL;
        if (USE_NEON) {
            return String.format("jdbc:postgresql://%s/%s?sslmode=require&channel_binding=require",
                    NEON_HOST, NEON_DB);
        } else {
            return String.format("jdbc:postgresql://%s:5432/%s", LOCAL_HOST, LOCAL_DB);
        }
    }

    private static String buildUser() {
        if (ENV_DB_USER != null) return ENV_DB_USER;
        return USE_NEON ? NEON_USER : LOCAL_USER;
    }

    private static String buildPassword() {
        if (ENV_DB_PASSWORD != null) return ENV_DB_PASSWORD;
        return USE_NEON ? NEON_PASSWORD : LOCAL_PASSWORD;
    }

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("PostgreSQL JDBC Driver not found.", e);
        }
        String url = buildUrl();
        String user = buildUser();
        String password = buildPassword();
        System.out.println("[DB] Connecting to: " + url.replaceAll(":[^@]+@", ":****@"));
        System.out.println("[DB] Mode: " + (USE_NEON ? "NeonDB (Production)" : "Local PostgreSQL"));
        return DriverManager.getConnection(url, user, password);
    }
}