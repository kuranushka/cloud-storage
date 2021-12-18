package ru.kuranov.auth;

import lombok.extern.slf4j.Slf4j;
import ru.kuranov.message.AuthMessage;

import java.sql.*;

/**
 * Класс авторизации с помощью запроса к базе данных
 */
@Slf4j
public class AuthDB {
    private static AuthDB instance;

    private AuthDB() {
    }

    public static AuthDB getInstance() {
        if (instance == null) {
            return new AuthDB();
        } else {
            return instance;
        }
    }

    public boolean auth(AuthMessage msg) {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        try (Connection conn = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:5432/cloud", "postgres", "root")) {
            if (conn != null) {
                log.debug("Connection to DB is Up");

                // авторизуем пользователя
                if (msg.isNewUser() == false) {
                    String sql = "SELECT pass FROM data WHERE login=?;";
                    PreparedStatement preparedStatement = conn.prepareStatement(sql);
                    preparedStatement.setString(1, msg.getUser());
                    ResultSet resultSet = preparedStatement.executeQuery();
                    while (resultSet.next()) {
                        if (msg.getPassword().equals(resultSet.getString("PASS"))) {
                            msg.setAuth(true);
                            log.debug("User {} accepted", msg);
                            conn.close();
                            return true;
                        }
                    }
                    conn.close();
                    return false;
                }
                // создаем нового пользователя
                else {
                    String sql = "INSERT INTO public.data (login, pass) VALUES ('" + msg.getUser() + "'::character varying, '" + msg.getPassword() + "'::character varying);";
                    Statement statement = conn.createStatement();
                    statement.executeUpdate(sql);
                    msg.setAuth(true);
                    return true;
                }
            } else {
                log.debug("Connection to DB is not established");
            }
        } catch (SQLException e) {
            System.err.format("SQL state: %s\n%s", e.getSQLState(), e.getMessage());
        }
        return false;
    }
}
