package ru.kuranov.message;

import lombok.Data;

@Data
public class AuthMessage extends AbstractMessage {
    private static AuthMessage authMessage;
    private boolean isNewUser;
    private boolean isAuth;
    private String user;
    private String password;

    public AuthMessage(boolean isNewUser, boolean isAuth, String user, String password) {
        this.isNewUser = isNewUser;
        this.isAuth = isAuth;
        this.user = user;
        this.password = password;
    }
}
