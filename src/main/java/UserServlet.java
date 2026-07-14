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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@WebServlet("/api/users")
public class UserServlet extends HttpServlet {

    // CORS — dynamically allow local development origins (localhost and 127.0.0.1)
    private void setCorsHeaders(HttpServletRequest req, HttpServletResponse resp) {
        String origin = req.getHeader("Origin");
        if (origin != null && (origin.contains("localhost") || origin.contains("127.0.0.1"))) {
            resp.setHeader("Access-Control-Allow-Origin", origin);
        } else {
            resp.setHeader("Access-Control-Allow-Origin", "http://localhost:8080");
        }
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(req, resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(req, resp);
        resp.setContentType("application/json");

        JsonArray usersArray = new JsonArray();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, msisdn, balance FROM Users")) {

            while (rs.next()) {
                JsonObject user = new JsonObject();
                user.addProperty("id", rs.getInt("id"));
                user.addProperty("msisdn", rs.getString("msisdn"));
                user.addProperty("balance", rs.getDouble("balance"));
                usersArray.add(user);
            }
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(usersArray.toString());

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            resp.getWriter().write(error.toString());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(req, resp);
        resp.setContentType("application/json");

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        try {
            JsonObject body = JsonParser.parseString(sb.toString()).getAsJsonObject();
            String msisdn = body.get("msisdn").getAsString();
            double balance = body.has("balance") ? body.get("balance").getAsDouble() : 100.0;

            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO Users (msisdn, balance) VALUES (?, ?)",
                         Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, msisdn);
                ps.setDouble(2, balance);
                ps.executeUpdate();

                ResultSet keys = ps.getGeneratedKeys();
                JsonObject result = new JsonObject();
                if (keys.next()) {
                    result.addProperty("id", keys.getInt(1));
                }
                result.addProperty("msisdn", msisdn);
                result.addProperty("balance", balance);

                resp.setStatus(HttpServletResponse.SC_CREATED);
                resp.getWriter().write(result.toString());
            }

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            resp.getWriter().write(error.toString());
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(req, resp);
        resp.setContentType("application/json");

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        try {
            JsonObject body = JsonParser.parseString(sb.toString()).getAsJsonObject();
            int id = body.get("id").getAsInt();
            double balance = body.get("balance").getAsDouble();

            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE Users SET balance = ? WHERE id = ?")) {

                ps.setDouble(1, balance);
                ps.setInt(2, id);
                int updated = ps.executeUpdate();

                JsonObject result = new JsonObject();
                if (updated > 0) {
                    result.addProperty("success", true);
                    result.addProperty("id", id);
                    result.addProperty("balance", balance);
                    resp.setStatus(HttpServletResponse.SC_OK);
                } else {
                    result.addProperty("success", false);
                    result.addProperty("error", "User not found");
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                }
                resp.getWriter().write(result.toString());
            }

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            resp.getWriter().write(error.toString());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(req, resp);
        resp.setContentType("application/json");

        String idParam = req.getParameter("id");
        if (idParam == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Missing 'id' parameter");
            resp.getWriter().write(error.toString());
            return;
        }

        try {
            int id = Integer.parseInt(idParam);
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM Users WHERE id = ?")) {

                ps.setInt(1, id);
                int deleted = ps.executeUpdate();

                JsonObject result = new JsonObject();
                if (deleted > 0) {
                    result.addProperty("success", true);
                    resp.setStatus(HttpServletResponse.SC_OK);
                } else {
                    result.addProperty("success", false);
                    result.addProperty("error", "User not found");
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                }
                resp.getWriter().write(result.toString());
            }

        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid 'id' parameter");
            resp.getWriter().write(error.toString());
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            resp.getWriter().write(error.toString());
        }
    }
}
