import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import com.google.gson.JsonObject;

@WebServlet("/api/agi/*")
public class AsteriskAgiServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        // CORS — dynamically allow local development origins (localhost and 127.0.0.1)
        String origin = req.getHeader("Origin");
        if (origin != null && (origin.contains("localhost") || origin.contains("127.0.0.1"))) {
            resp.setHeader("Access-Control-Allow-Origin", origin);
        } else {
            resp.setHeader("Access-Control-Allow-Origin", "http://localhost:8080");
        }

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (pathInfo.equals("/authorize")) {
            JsonObject result = new JsonObject();
            String expectedToken = System.getenv("AGI_TOKEN");
            if (expectedToken != null && !expectedToken.trim().isEmpty()) {
                String token = req.getParameter("token");
                if (token == null || !token.equals(expectedToken)) {
                    result.addProperty("authorized", false);
                    result.addProperty("maxDuration", 0);
                    result.addProperty("balance", 0);
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    resp.getWriter().write(result.toString());
                    return;
                }
            }

            String callerId = req.getParameter("callerid");
            if (callerId == null || callerId.isEmpty()) {
                result.addProperty("authorized", false);
                result.addProperty("maxDuration", 0);
                result.addProperty("balance", 0);
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write(result.toString());
                return;
            }

            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT balance FROM Users WHERE msisdn = ?")) {

                ps.setString(1, callerId);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    double balance = rs.getDouble("balance");
                    if (balance > 0) {
                        long maxDuration = (long) (balance / 1.0 * 60000);
                        result.addProperty("authorized", true);
                        result.addProperty("maxDuration", maxDuration);
                        result.addProperty("balance", balance);

                        // Register active SIP call session in dashboard monitor map
                        CallSessionInfo info = new CallSessionInfo(callerId, java.time.LocalDateTime.now().toString(), 0, 5070, "SIP", balance);
                        MSC.activeSessions.put(callerId, info);
                    } else {
                        result.addProperty("authorized", false);
                        result.addProperty("maxDuration", 0);
                        result.addProperty("balance", 0);
                    }
                } else {
                    result.addProperty("authorized", false);
                    result.addProperty("maxDuration", 0);
                    result.addProperty("balance", 0);
                }

                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(result.toString());

            } catch (Exception e) {
                result.addProperty("authorized", false);
                result.addProperty("maxDuration", 0);
                result.addProperty("balance", 0);
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write(result.toString());
            }

        } else if (pathInfo.equals("/hangup")) {
            String callerId = req.getParameter("callerid");
            if (callerId != null && !callerId.isEmpty()) {
                CallSessionInfo session = MSC.activeSessions.remove(callerId);
                if (session != null) {
                    java.time.LocalDateTime endTime = java.time.LocalDateTime.now();
                    java.time.LocalDateTime startTime = java.time.LocalDateTime.parse(session.getStartTime());
                    long durationSeconds = java.time.Duration.between(startTime, endTime).getSeconds();
                    
                    // Round call duration up to nearest minute (e.g. 15s -> 1 min)
                    int durationMins = (int) Math.max(1, Math.ceil(durationSeconds / 60.0));
                    double cost = durationMins * 1.0;
                    
                    // one transaction: read final balance + write CDR atomically.
                    // if either step fails, both roll back — no charge without a CDR record.
                    String insertQuery = "INSERT INTO CDRs (msisdn, start_time, end_time, duration_mins, cost, result, final_balance) VALUES (?, ?, ?, ?, ?, ?, ?)";
                    try (Connection conn = DatabaseConnection.getConnection()) {
                        conn.setAutoCommit(false);
                        try {
                            double finalBalance = 0.0;
                            try (PreparedStatement ps = conn.prepareStatement("SELECT balance FROM Users WHERE msisdn = ?")) {
                                ps.setString(1, callerId);
                                try (ResultSet rs = ps.executeQuery()) {
                                    if (rs.next()) finalBalance = rs.getDouble("balance");
                                }
                            }
                            try (PreparedStatement ps = conn.prepareStatement(insertQuery)) {
                                ps.setString(1, callerId);
                                ps.setTimestamp(2, java.sql.Timestamp.valueOf(startTime));
                                ps.setTimestamp(3, java.sql.Timestamp.valueOf(endTime));
                                ps.setInt(4, durationMins);
                                ps.setDouble(5, cost);
                                ps.setString(6, "Normal call Clearing");
                                ps.setDouble(7, finalBalance);
                                ps.executeUpdate();
                            }
                            conn.commit();
                            System.out.println("[SIP CDR] Saved CDR in database for " + callerId + " (" + durationMins + " mins)");
                        } catch (Exception e) {
                            conn.rollback();
                            System.err.println("[SIP CDR] DB write failed, rolled back: " + e.getMessage());
                        }
                    } catch (Exception e) {
                        System.err.println("[SIP CDR] DB connection error: " + e.getMessage());
                    }
                }
            }
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write("{\"success\":true}");
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
