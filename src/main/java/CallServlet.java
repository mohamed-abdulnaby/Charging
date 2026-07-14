import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.BufferedReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@WebServlet("/api/calls/*")
public class CallServlet extends HttpServlet {

    private static final ConcurrentHashMap<String, WebCallSimulator> activeSimulators = new ConcurrentHashMap<>();

    public static void removeSimulator(String msisdn) {
        activeSimulators.remove(msisdn);
    }

    // CORS — restricted to same host, dashboard is served from the same origin
    private void setCorsHeaders(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "http://localhost:8080");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp);
        resp.setContentType("application/json");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (pathInfo.equals("/active")) {
            JsonArray activeArray = new JsonArray();
            for (CallSessionInfo info : MSC.activeSessions.values()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("msisdn", info.getMsisdn());
                obj.addProperty("startTime", info.getStartTime());
                obj.addProperty("elapsedMinutes", info.getElapsedMinutes());
                obj.addProperty("startTimeEpoch", info.getStartTimeEpoch());
                obj.addProperty("udpPort", info.getUdpPort());
                obj.addProperty("callType", info.getCallType());
                obj.addProperty("currentBalance", info.getCurrentBalance());
                activeArray.add(obj);
            }
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(activeArray.toString());

        } else if (pathInfo.equals("/cdr")) {
            JsonArray cdrArray = new JsonArray();
            String query = "SELECT msisdn, start_time, end_time, duration_mins, cost, result, final_balance FROM CDRs ORDER BY id DESC LIMIT 15";
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                while (rs.next()) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("msisdn", rs.getString("msisdn"));
                    obj.addProperty("startTime", rs.getTimestamp("start_time").toString());
                    obj.addProperty("endTime", rs.getTimestamp("end_time").toString());
                    obj.addProperty("duration", rs.getInt("duration_mins"));
                    obj.addProperty("cost", rs.getDouble("cost"));
                    obj.addProperty("result", rs.getString("result"));
                    obj.addProperty("finalBalance", rs.getDouble("final_balance"));
                    cdrArray.add(obj);
                }
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(cdrArray.toString());

            } catch (Exception e) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
            }

        } else if (pathInfo.equals("/revenue")) {
            String query = "SELECT COALESCE(SUM(cost), 0.0) AS total FROM CDRs";
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                double totalRevenue = 0.0;
                if (rs.next()) {
                    totalRevenue = rs.getDouble("total");
                }
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write("{\"totalRevenue\":" + totalRevenue + "}");

            } catch (Exception e) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
            }
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp);
        resp.setContentType("application/json");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (pathInfo.equals("/simulate")) {
            // Read MSISDN
            StringBuilder sb = new StringBuilder();
            String line;
            try (BufferedReader reader = req.getReader()) {
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            
            String msisdn = "";
            try {
                JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
                msisdn = json.get("msisdn").getAsString();
            } catch (Exception e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Invalid JSON body\"}");
                return;
            }

            // Check if user has balance
            double balance = 0.0;
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT balance FROM Users WHERE msisdn = ?")) {
                ps.setString(1, msisdn);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        balance = rs.getDouble("balance");
                    } else {
                        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        resp.getWriter().write("{\"error\":\"User not found\"}");
                        return;
                    }
                }
            } catch (Exception e) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
                return;
            }

            if (balance <= 0.0) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Subscriber has depleted balance\"}");
                return;
            }

            if (MSC.activeSessions.containsKey(msisdn)) {
                resp.setStatus(HttpServletResponse.SC_CONFLICT);
                resp.getWriter().write("{\"error\":\"Subscriber is already in an active call\"}");
                return;
            }

            // Start simulated call session
            WebCallSimulator sim = new WebCallSimulator(msisdn, balance);
            activeSimulators.put(msisdn, sim);
            new Thread(sim).start();

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write("{\"success\":true}");

        } else if (pathInfo.equals("/hangup")) {
            StringBuilder sb = new StringBuilder();
            String line;
            try (BufferedReader reader = req.getReader()) {
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            String msisdn = "";
            try {
                JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
                msisdn = json.get("msisdn").getAsString();
            } catch (Exception e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Invalid JSON body\"}");
                return;
            }

            WebCallSimulator sim = activeSimulators.remove(msisdn);
            if (sim != null) {
                sim.hangup();
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write("{\"success\":true}");
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"error\":\"No active simulator session found for this MSISDN\"}");
            }
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}

class WebCallSimulator implements Runnable {
    private String msisdn;
    private double currentBalance;
    private LocalDateTime startTime;
    private int elapsedMinutes = 0;
    private volatile boolean active = true;
    private String callResultOverride = null;

    public WebCallSimulator(String msisdn, double balance) {
        this.msisdn = msisdn;
        this.currentBalance = balance;
        this.startTime = LocalDateTime.now();
    }

    public void hangup() {
        this.active = false;
        this.callResultOverride = "User Hang Up";
    }

    @Override
    public void run() {
        CallSessionInfo info = new CallSessionInfo(msisdn, startTime.toString(), elapsedMinutes, 0, "WebSim", currentBalance);
        MSC.activeSessions.put(msisdn, info);
        
        try {
            while (active) {
                // 1 L.E. per real minute — same as the SIP/RTP path in CallSession
                elapsedMinutes++;
                deductBalance(msisdn, 1.0);
                currentBalance -= 1.0;
                
                info.setElapsedMinutes(elapsedMinutes);
                info.setCurrentBalance(currentBalance);

                if (currentBalance <= 0) {
                    System.out.println("[WebSim " + msisdn + "] Balance exhausted. Ending call.");
                    active = false;
                    callResultOverride = "Depleted";
                    break;
                }
                
                Thread.sleep(60000); // 1 real minute
            }
        } catch (InterruptedException e) {
            callResultOverride = "Call Aborted";
        } finally {
            MSC.activeSessions.remove(msisdn);
            CallServlet.removeSimulator(msisdn);
            generateCDR();
        }
    }

    private void deductBalance(String targetMsisdn, double amount) {
        String query = "UPDATE Users SET Balance = Balance - ? WHERE MSISDN = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setDouble(1, amount);
            ps.setString(2, targetMsisdn);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("[WebSim " + msisdn + "] DB Charging Error: " + e.getMessage());
        }
    }

    private double getFinalBalance(String targetMsisdn) {
        String query = "SELECT Balance FROM Users WHERE MSISDN = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, targetMsisdn);
            ResultSet rs = ps.executeQuery();
            if (rs.next())
                return rs.getDouble("Balance");
        } catch (Exception ignored) {}
        return 0.0;
    }

    private void generateCDR() {
        LocalDateTime endTime = LocalDateTime.now();
        double cost = elapsedMinutes * 1.0;
        String callResult;
        if (callResultOverride != null) {
            callResult = callResultOverride;
        } else if (elapsedMinutes == 0) {
            callResult = "Cancelled";
        } else {
            callResult = "Normal call Clearing";
        }

        // one transaction: read final balance + write CDR atomically.
        // if either step fails, both roll back — no charge without a CDR record.
        String selectQuery = "SELECT Balance FROM Users WHERE MSISDN = ?";
        String insertQuery = "INSERT INTO CDRs (msisdn, start_time, end_time, duration_mins, cost, result, final_balance) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                double finalBalance = 0.0;
                try (PreparedStatement ps = conn.prepareStatement(selectQuery)) {
                    ps.setString(1, msisdn);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) finalBalance = rs.getDouble("Balance");
                }
                try (PreparedStatement ps = conn.prepareStatement(insertQuery)) {
                    ps.setString(1, msisdn);
                    ps.setTimestamp(2, java.sql.Timestamp.valueOf(startTime));
                    ps.setTimestamp(3, java.sql.Timestamp.valueOf(endTime));
                    ps.setInt(4, elapsedMinutes);
                    ps.setDouble(5, cost);
                    ps.setString(6, callResult);
                    ps.setDouble(7, finalBalance);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            System.err.println("[WebSim " + msisdn + "] CDR write failed: " + e.getMessage());
        }
    }
}
