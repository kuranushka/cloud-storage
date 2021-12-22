package ru.kuranov.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import ru.kuranov.auth.Authentication;
import ru.kuranov.message.AbstractMessage;
import ru.kuranov.message.AuthMessage;
import ru.kuranov.message.Message;
import ru.kuranov.net.NettyClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Обработчик входящих сообений на клиентско стороне.
 */
@Slf4j
public class ClientMessageHandler extends SimpleChannelInboundHandler<AbstractMessage> {
    private static ClientMessageHandler instance;
    //private final OnMessageReceived callback;
    private final NettyClient netty;
    private List<String> serverFiles;
    private String serverPath;
    private Path clientPath;

    private ClientMessageHandler() {
        //this.callback = callback;
        netty = NettyClient.getInstance();
        this.serverFiles = new ArrayList<>();
    }

    public static ClientMessageHandler getInstance() {
        if (instance == null) {
            instance = new ClientMessageHandler();
            return instance;
        } else {
            return instance;
        }
    }

    // сохраняем путь к текущей папке клиента
    public void setClientPath(Path clientPath) {
        this.clientPath = clientPath;
    }

    // путь текущей папки на сервере
    public String getServerPath() {
        return serverPath;
    }

    // список файлов на сервере
    public List<String> getServerFiles() {
        return serverFiles;
    }

    // читаем входящие сообщения от сервера
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, AbstractMessage msg) {
        // сообщение об авторизации
        if (msg.getClass() == AuthMessage.class) {
            auth(msg);
        }

        // REFRESH отображенных списков файлов после операции на сервере
        if (msg.getClass() == Message.class && ((Message) msg).getCommand() == Command.REFRESH) {
            serverFiles = ((Message) msg).getListFiles();
            serverPath = ((Message) msg).getServerPath();
            log.debug("Handler Server Root {}  Server File List {}", serverPath, serverFiles);
        }

        // получение файла с сервера
        if (msg.getClass() == Message.class && ((Message) msg).getCommand() == Command.DOWNLOAD) {
            receiveFile(msg);
        }
    }

    // получение и сохранение файла с сервера
    private void receiveFile(AbstractMessage msg) {
        String newFile = ((Message) msg).getFileName();
        try {
            File file = new File(clientPath + newFile.substring(newFile.lastIndexOf("/")));
            log.debug("New file is {}", file);
            FileOutputStream fos = new FileOutputStream(file);
            byte[] buf = ((Message) msg).getBuf();
            fos.write(buf);
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // обработка сообщения об авторизации и сохранение его состояния
    private void auth(AbstractMessage msg) {
        if (msg.getClass() == AuthMessage.class && ((AuthMessage) msg).isAuth()) {
            Authentication.setAuth(true);
        }
    }
}
