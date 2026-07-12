import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import com.google.gson.JsonObject;

@WebServlet("/api/agi/authorize")
public class AsteriskAgiServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setHeader("Access-Control-Allow-Origin", "*");

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
    }
}
