package com.mycompany.chargingportal;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@WebServlet(name = "UserServlet", urlPatterns = {"/users"})
public class UserServlet extends HttpServlet {

    private final UserDAO userDAO = new UserDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        try {
            List<User> users = userDAO.getAllUsers();
            request.setAttribute("users", users);

            RequestDispatcher dispatcher = request.getRequestDispatcher("/index.jsp");
            dispatcher.forward(request, response);

        } catch (Exception e) {
            throw new ServletException("Error fetching users from database", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String action = request.getParameter("action");

        try {
            if ("add".equals(action)) {
                String msisdn = request.getParameter("msisdn");
                String balanceParam = request.getParameter("balance");

                if (msisdn != null && !msisdn.isBlank() && balanceParam != null && !balanceParam.isBlank()) {
                    double balance = Double.parseDouble(balanceParam);
                    userDAO.addUser(msisdn.trim(), balance);
                }

            } else if ("update".equals(action)) {
                String msisdn = request.getParameter("msisdn");
                String balanceParam = request.getParameter("balance");

                if (msisdn != null && !msisdn.isBlank() && balanceParam != null && !balanceParam.isBlank()) {
                    double balance = Double.parseDouble(balanceParam);
                    userDAO.updateBalance(msisdn.trim(), balance);
                }

            } else if ("delete".equals(action)) {
                String msisdn = request.getParameter("msisdn");
                if (msisdn != null && !msisdn.isBlank()) {
                    userDAO.deleteUser(msisdn.trim());
                }
            }

        } catch (Exception e) {
            throw new ServletException("Error updating database", e);
        }

        // Redirect (Post/Redirect/Get) عشان نمنع إعادة إرسال الفورم عند الـ Refresh
        response.sendRedirect(request.getContextPath() + "/users");
    }
}
