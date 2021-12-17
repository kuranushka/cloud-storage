package ru.kuranov.message;

import lombok.Data;
import ru.kuranov.handler.Command;

import java.util.List;

@Data
public class Message extends AbstractMessage {
    private String fileName;
    private Command command;
    private byte[] buf;
    private String oldFileName;
    private List<String> listFiles;
    private String serverPath;

    public Message(List<String> listFiles, Command command, String serverPath) {
        this.listFiles = listFiles;
        this.command = command;
        this.serverPath = serverPath;
    }

    public Message(String fileName, Command command, byte[] buf) {
        this.fileName = fileName;
        this.command = command;
        this.buf = buf;
    }

    public Message(String fileName, Command command) {
        this.fileName = fileName;
        this.command = command;
    }

    public Message(String fileName, String oldFileName, Command command) {
        this.fileName = fileName;
        this.command = command;
        this.oldFileName = oldFileName;
    }
}
