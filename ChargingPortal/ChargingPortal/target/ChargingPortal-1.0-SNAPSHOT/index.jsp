<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List, com.mycompany.chargingportal.User, com.mycompany.chargingportal.UserDAO" %>
<%
    // Supports both flows: forwarded from UserServlet (attribute already set)
    // or index.jsp opened directly (fetches data itself as a fallback)
    List<User> users = (List<User>) request.getAttribute("users");
    if (users == null) {
        try {
            users = new UserDAO().getAllUsers();
        } catch (Exception e) {
            users = new java.util.ArrayList<>();
            request.setAttribute("dbError", e.getMessage());
        }
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Charging Portal · User Management</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg: #f5f7fb;
            --surface: #ffffff;
            --ink: #14213d;
            --muted: #64748b;
            --border: #e7eaf0;
            --primary: #4f46e5;
            --primary-dark: #4338ca;
            --success: #16a34a;
            --success-dark: #15803d;
            --danger: #ef4444;
            --danger-dark: #dc2626;
            --radius: 14px;
        }
        * { box-sizing: border-box; }
        body {
            font-family: 'Inter', 'Segoe UI', Tahoma, Arial, sans-serif;
            background: linear-gradient(180deg, #eef1fb 0%, var(--bg) 260px);
            margin: 0;
            padding: 40px 20px;
            color: var(--ink);
        }
        .container {
            max-width: 960px;
            margin: 0 auto;
        }
        .topbar {
            display: flex;
            align-items: center;
            gap: 14px;
            margin-bottom: 34px;
        }
        .logo {
            width: 46px;
            height: 46px;
            border-radius: 12px;
            background: linear-gradient(135deg, var(--primary), #7c3aed);
            display: flex;
            align-items: center;
            justify-content: center;
            color: #fff;
            font-weight: 800;
            font-size: 18px;
            box-shadow: 0 8px 20px rgba(79,70,229,0.28);
        }
        .topbar h1 {
            font-size: 22px;
            margin: 0;
            font-weight: 800;
            letter-spacing: -0.02em;
        }
        .topbar p {
            margin: 2px 0 0;
            font-size: 13px;
            color: var(--muted);
        }

        .stats {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 16px;
            margin-bottom: 24px;
        }
        .stat-card {
            background: var(--surface);
            border: 1px solid var(--border);
            border-radius: var(--radius);
            padding: 18px 20px;
        }
        .stat-card .label {
            font-size: 12px;
            text-transform: uppercase;
            letter-spacing: 0.05em;
            color: var(--muted);
            font-weight: 600;
        }
        .stat-card .value {
            font-size: 26px;
            font-weight: 800;
            margin-top: 6px;
        }

        .card {
            background: var(--surface);
            border: 1px solid var(--border);
            border-radius: var(--radius);
            padding: 26px;
            margin-bottom: 22px;
            box-shadow: 0 1px 2px rgba(16,24,40,0.04);
        }
        .card h2 {
            margin: 0 0 18px;
            font-size: 16px;
            font-weight: 700;
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .error-card {
            border: 1px solid #fecaca;
            background: #fef2f2;
        }
        .error-card strong { color: var(--danger-dark); }
        .error-card small { color: #7f1d1d; display: block; margin-top: 4px; }

        form.add-form {
            display: flex;
            gap: 14px;
            flex-wrap: wrap;
            align-items: flex-end;
        }
        .field {
            display: flex;
            flex-direction: column;
            gap: 6px;
            flex: 1;
            min-width: 180px;
        }
        .field label {
            font-size: 12.5px;
            font-weight: 600;
            color: var(--muted);
        }
        input[type="text"], input[type="number"] {
            padding: 11px 14px;
            border: 1.5px solid var(--border);
            border-radius: 10px;
            font-size: 14px;
            font-family: inherit;
            outline: none;
            transition: border-color .15s ease, box-shadow .15s ease;
            background: #fafbfc;
        }
        input:focus {
            border-color: var(--primary);
            box-shadow: 0 0 0 3px rgba(79,70,229,0.12);
            background: #fff;
        }
        button {
            cursor: pointer;
            border: none;
            border-radius: 10px;
            padding: 11px 22px;
            font-size: 14px;
            font-weight: 700;
            font-family: inherit;
            transition: background .15s ease, transform .05s ease;
        }
        button:active { transform: translateY(1px); }
        .btn-add {
            background: var(--primary);
            color: #fff;
            box-shadow: 0 4px 12px rgba(79,70,229,0.25);
        }
        .btn-add:hover { background: var(--primary-dark); }
        .btn-delete {
            background: transparent;
            color: var(--danger);
            border: 1.5px solid #fecaca;
            padding: 7px 14px;
            font-size: 13px;
        }
        .btn-delete:hover { background: var(--danger); color: #fff; border-color: var(--danger); }

        table {
            width: 100%;
            border-collapse: collapse;
        }
        thead th {
            text-align: left;
            color: var(--muted);
            font-size: 12px;
            text-transform: uppercase;
            letter-spacing: 0.05em;
            font-weight: 700;
            padding: 0 14px 12px;
            border-bottom: 1.5px solid var(--border);
        }
        thead th:last-child { text-align: right; }
        tbody td {
            padding: 15px 14px;
            border-bottom: 1px solid var(--border);
            font-size: 14px;
        }
        tbody td:last-child { text-align: right; }
        tbody tr:last-child td { border-bottom: none; }
        tbody tr:hover { background: #fafbff; }
        .msisdn { font-weight: 600; font-variant-numeric: tabular-nums; }
        .edit-form {
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .balance-input {
            width: 110px;
            padding: 7px 10px;
            border: 1.5px solid var(--border);
            border-radius: 8px;
            font-size: 13.5px;
            font-family: inherit;
            font-weight: 600;
            font-variant-numeric: tabular-nums;
            background: #ecfdf5;
            color: var(--success-dark);
            outline: none;
            transition: border-color .15s ease, background .15s ease;
        }
        .balance-input:focus {
            background: #fff;
            border-color: var(--primary);
            box-shadow: 0 0 0 3px rgba(79,70,229,0.12);
        }
        .btn-save {
            background: var(--primary);
            color: #fff;
            padding: 7px 14px;
            font-size: 13px;
        }
        .btn-save:hover:not(:disabled) { background: var(--primary-dark); }
        .btn-save:disabled {
            background: #e7eaf0;
            color: var(--muted);
            cursor: not-allowed;
        }
        .empty-row td {
            text-align: center;
            padding: 40px;
            color: var(--muted);
            font-size: 14px;
        }
        footer {
            text-align: center;
            font-size: 12px;
            color: var(--muted);
            margin-top: 30px;
        }
    </style>
</head>
<body>
<div class="container">

    <div class="topbar">
        <div class="logo">CP</div>
        <div>
            <h1>Charging Portal</h1>
            <p>User &amp; balance management</p>
        </div>
    </div>

    <% if (request.getAttribute("dbError") != null) { %>
    <div class="card error-card">
        <strong>Database connection failed</strong>
        <div><%= request.getAttribute("dbError") %></div>
        <small>Make sure PostgreSQL is running and the credentials in DatabaseConnection.java are correct.</small>
    </div>
    <% } %>

    <div class="stats">
        <div class="stat-card">
            <div class="label">Total Users</div>
            <div class="value"><%= users.size() %></div>
        </div>
        <div class="stat-card">
            <div class="label">Total Balance</div>
            <%
                double total = 0;
                for (User u : users) total += u.getBalance();
            %>
            <div class="value"><%= String.format("%.2f", total) %></div>
        </div>
    </div>

    <div class="card">
        <h2>➕ Add New User</h2>
        <form class="add-form" action="${pageContext.request.contextPath}/users" method="post">
            <input type="hidden" name="action" value="add">
            <div class="field">
                <label for="msisdn">MSISDN</label>
                <input type="text" id="msisdn" name="msisdn" placeholder="e.g. 01012345678" required>
            </div>
            <div class="field">
                <label for="balance">Balance</label>
                <input type="number" id="balance" name="balance" step="0.01" min="0" placeholder="0.00" required>
            </div>
            <button type="submit" class="btn-add">Add User</button>
        </form>
    </div>

    <div class="card">
        <h2>👥 Users</h2>
        <table>
            <thead>
                <tr>
                    <th>MSISDN</th>
                    <th>Balance</th>
                    <th>Action</th>
                </tr>
            </thead>
            <tbody>
                <% if (users.isEmpty()) { %>
                <tr class="empty-row">
                    <td colspan="3">No users yet — add your first one above.</td>
                </tr>
                <% } else {
                    for (User u : users) { %>
                <tr>
                    <td class="msisdn"><%= u.getMsisdn() %></td>
                    <td>
                        <form class="edit-form" action="${pageContext.request.contextPath}/users" method="post">
                            <input type="hidden" name="action" value="update">
                            <input type="hidden" name="msisdn" value="<%= u.getMsisdn() %>">
                            <input type="number" name="balance" class="balance-input" step="0.01" min="0"
                                   value="<%= String.format("%.2f", u.getBalance()) %>"
                                   data-original="<%= String.format("%.2f", u.getBalance()) %>"
                                   oninput="this.form.querySelector('.btn-save').disabled = (this.value === this.dataset.original);">
                            <button type="submit" class="btn-save" disabled>Save</button>
                        </form>
                    </td>
                    <td>
                        <form action="${pageContext.request.contextPath}/users" method="post"
                              onsubmit="return confirm('Delete this user?');" style="display:inline;">
                            <input type="hidden" name="action" value="delete">
                            <input type="hidden" name="msisdn" value="<%= u.getMsisdn() %>">
                            <button type="submit" class="btn-delete">Delete</button>
                        </form>
                    </td>
                </tr>
                <%  }
                } %>
            </tbody>
        </table>
    </div>

    <footer>Charging Portal · PostgreSQL backend</footer>
</div>
</body>
</html>
