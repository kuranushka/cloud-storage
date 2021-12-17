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

@Slf4j
public class ClientMessageHandler extends SimpleChannelInboundHandler<AbstractMessage> {
    private static ClientMessageHandler instance;
    private final OnMessageReceived callback;
    private final NettyClient netty;
    private List<String> serverFiles;
    private String serverPath;
    private Path clientPath;

    private ClientMessageHandler(OnMessageReceived callback) {
        this.callback = callback;
        netty = NettyClient.getInstance(System.out::println);
        this.serverFiles = new ArrayList<>();
    }

    public static ClientMessageHandler getInstance(OnMessageReceived callback) {
        if (instance == null) {
            instance = new ClientMessageHandler(callback);
            return instance;
        } else {
            return instance;
        }
    }

    public void setClientPath(Path clientPath) {
        this.clientPath = clientPath;
    }

    public String getServerPath() {
        return serverPath;
    }

    public List<String> getServerFiles() {
        return serverFiles;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, AbstractMessage msg) {
        if (msg.getClass() == AuthMessage.class) {
            auth(msg);
        }

        if (msg.getClass() == Message.class && ((Message) msg).getCommand() == Command.REFRESH) {
            serverFiles = ((Message) msg).getListFiles();
            serverPath = ((Message) msg).getServerPath();
            log.debug("Handler Server Root {}  Server File List {}", serverPath, serverFiles);
        }

        if (msg.getClass() == Message.class && ((Message) msg).getCommand() == Command.DOWNLOAD) {
            receiveFile(msg);
        }
    }

    private void receiveFile(AbstractMessage msg) {
        String newFile = ((Message) msg).getFileName();
        try {
            File file = new File(clientPath + newFile.substring(newFile.lastIndexOf("/")));
            log.debug("New file is {}", file);
            FileOutputStream fos = new FileOutputStream(file);
            byte[] buf = ((Message) msg).getBuf();
            fos.write(buf);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void auth(AbstractMessage msg) {
        if (msg.getClass() == AuthMessage.class && ((AuthMessage) msg).isAuth()) {
            Authentication.setAuth(true);
            System.out.println("clientHandler__" + Authentication.isAuth());
            //this.callback.onReceive(msg);
        }
    }
}
