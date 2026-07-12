package com.mycompany.chargingportal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {

    /**
     * يجيب كل المستخدمين من جدول users مرتبين حسب MSISDN
     */
    public List<User> getAllUsers() throws Exception {
        List<User> users = new ArrayList<>();
        String query = "SELECT msisdn, balance FROM users ORDER BY msisdn";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                users.add(new User(rs.getString("msisdn"), rs.getDouble("balance")));
            }
        }
        return users;
    }

    /**
     * يضيف مستخدم جديد فقط. لو الـ MSISDN موجود بالفعل، العملية بتتجاهل
     * (استخدم updateBalance لو عايز تعدّل رصيد مستخدم موجود).
     */
    public void addUser(String msisdn, double balance) throws Exception {
        String query = "INSERT INTO users (msisdn, balance) VALUES (?, ?) "
                + "ON CONFLICT (msisdn) DO NOTHING";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setString(1, msisdn);
            ps.setDouble(2, balance);
            ps.executeUpdate();
        }
    }

    /**
     * يعدّل رصيد مستخدم موجود بالفعل.
     */
    public void updateBalance(String msisdn, double newBalance) throws Exception {
        String query = "UPDATE users SET balance = ? WHERE msisdn = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setDouble(1, newBalance);
            ps.setString(2, msisdn);
            ps.executeUpdate();
        }
    }

    /**
     * يمسح مستخدم عن طريق MSISDN
     */
    public void deleteUser(String msisdn) throws Exception {
        String query = "DELETE FROM users WHERE msisdn = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setString(1, msisdn);
            ps.executeUpdate();
        }
    }
}
