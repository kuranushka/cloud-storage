package ru.kuranov.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import ru.kuranov.auth.AuthDB;
import ru.kuranov.message.AbstractMessage;
import ru.kuranov.message.AuthMessage;
import ru.kuranov.message.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Основной класс обработчик входящих сообщений на сервере
 */
@Slf4j
public class ServerMessageHandler extends SimpleChannelInboundHandler<AbstractMessage> {
    private AuthDB connection;
    private Path root;
    private boolean isAuth;
    private Converter converter;
    private String rootDirectory;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, AbstractMessage msg) {

        converter = new Converter();

        if (!isAuth) {

            // авторизация в базе данных
            if (msg.getClass() == AuthMessage.class) {
                connection = AuthDB.getInstance();
                isAuth = connection.auth((AuthMessage) msg);
                if (!isAuth) {
                    return;
                }
                root = Paths.get(System.getProperty("user.home") + "/" + ((AuthMessage) msg).getUser());
                rootDirectory = root.toString();

                // создание папки пользователя
                if (!Files.exists(root)) {
                    try {
                        root = Files.createDirectory(root);
                    } catch (IOException e) {
                        log.debug("Directory not create");
                    }
                    log.debug("Create user directory: {}", root);
                }

                // возвращаем авторизацию клиенту
                ctx.writeAndFlush(msg);
                sendListFiles(ctx);
                return;
            }
        }

        // навигация войти в папку
        if (((Message) msg).getCommand() == Command.OPEN_IN) {
            root = Paths.get(root + "/" + converter.convertString(((Message) msg).getFileName()));
            sendListFiles(ctx);
        }

        // навигация выйти в вверх
        if (((Message) msg).getCommand() == Command.OPEN_OUT) {
            if (((Message) msg).getFileName().contains(rootDirectory)) {
                root = Paths.get(((Message) msg).getFileName());
                sendListFiles(ctx);
            } else {
                sendListFiles(ctx);
            }

        }

        // создание файла
        if (((Message) msg).getCommand() == Command.NEW_FILE) {
            createFile(msg);
        }

        // создание папки
        if (((Message) msg).getCommand() == Command.NEW_DIRECTORY) {
            createDirectory(msg);
        }

        // приём файла
        if (((Message) msg).getCommand() == Command.UPLOAD) {
            receiveFile(ctx, msg);
        }

        // отправка файла клиенту
        if (((Message) msg).getCommand() == Command.DOWNLOAD) {
            sendFile(ctx, msg);
        }

        // переименование папки, файла
        if (((Message) msg).getCommand() == Command.RENAME) {
            renameFile(ctx, msg);
        }

        // удаление папки, файла
        if (((Message) msg).getCommand() == Command.DELETE) {
            deleteFile(ctx, msg);
        }

        sendListFiles(ctx);
    }

    // отправляем на клиенту список файлов сервера
    private void sendListFiles(ChannelHandlerContext ctx) {
        File file = new File(root.toString());
        List<String> files = Arrays.stream(file.list()).map(m -> new File(root.toString() + "\\" + m)).map(n -> {
            if (n.isDirectory()) {
                return "[Dir ]" + n;
            } else {
                return "[file]" + n + "\t\t" + converter.convertTime(n.lastModified()) + " " + converter.convertFileSize(n);
            }
        }).sorted().map(o -> o.substring(0, 6) + o.substring(o.lastIndexOf("\\") + 1)).peek(System.out::println).collect(Collectors.toList());
        log.debug("Items in rootDir {}", files);
        ctx.writeAndFlush(new Message(files, Command.REFRESH, root.toString()));
    }

    // создаем новый файл
    private void createFile(AbstractMessage msg) {
        try {
            Files.createFile(Paths.get(root.toString() + "/" + ((Message) msg).getFileName()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // создаем новую папку
    private void createDirectory(AbstractMessage msg) {
        try {
            Files.createDirectory(Paths.get(root.toString() + "/" + ((Message) msg).getFileName()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // принимаем файл от клиента
    private void receiveFile(ChannelHandlerContext ctx, AbstractMessage msg) {
        try {
            FileOutputStream fos = new FileOutputStream(root.toString() + "/" + ((Message) msg).getFileName());
            byte[] buf = ((Message) msg).getBuf();
            fos.write(buf);
            sendListFiles(ctx);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // отправляем файл клиенту
    private void sendFile(ChannelHandlerContext ctx, AbstractMessage msg) {
        String sendFile = (root + "/" + ((Message) msg).getFileName());
        try {
            FileInputStream fis = new FileInputStream(sendFile);
            byte[] buf = new byte[fis.available()];
            fis.read(buf);
            ctx.writeAndFlush(new Message(sendFile, Command.DOWNLOAD, buf));
            log.debug("File {} send to client", sendFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // переименовываем файл или папку
    private void renameFile(ChannelHandlerContext ctx, AbstractMessage msg) {
        String oldName = ((Message) msg).getOldFileName();
        String newName = ((Message) msg).getFileName();
        try {
            Files.move(Paths.get(root.toString() + "/" + oldName), Paths.get(root.toString() + "/" + newName));
        } catch (IOException e) {
            e.printStackTrace();
        }
        sendListFiles(ctx);
    }

    // удаляем файл или папку
    private void deleteFile(ChannelHandlerContext ctx, AbstractMessage msg) {
        try {
            Files.walk(Paths.get(root.toString() + "/" + ((Message) msg).getFileName())).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException e) {
            e.printStackTrace();
        }
        sendListFiles(ctx);
    }
}
