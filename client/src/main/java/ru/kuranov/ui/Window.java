package ru.kuranov.ui;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import lombok.extern.slf4j.Slf4j;
import ru.kuranov.auth.Authentication;
import ru.kuranov.handler.ClientMessageHandler;
import ru.kuranov.handler.Command;
import ru.kuranov.handler.Converter;
import ru.kuranov.handler.OnMessageReceived;
import ru.kuranov.message.AuthMessage;
import ru.kuranov.message.Message;
import ru.kuranov.net.NettyClient;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

@Slf4j
public class Window implements Initializable {

    private final long DELAY = 300L;
    public ListView<String> clientFileList;
    public ListView<String> serverFileList;
    public Label myComputerLabel;
    public Label cloudStorageLabel;
    public Button clientLevelUpButton;
    public Button serverLevelButton;
    private String selectedHomeFile;
    private String selectedServerFile;
    private NettyClient netty;
    private OnMessageReceived callback;
    private Path root;
    private boolean isSelectClientFile;
    private ClientMessageHandler handler;
    private Converter converter;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        netty = NettyClient.getInstance(System.out::println);
        root = Paths.get(System.getProperty("user.home"));
        handler = ClientMessageHandler.getInstance(callback);
        converter = new Converter();

        auth();
        refreshClientFiles();
        refreshServerFiles();

        // двойной клик, навигация или открытие файла клиента
        clientFileList.setOnMouseClicked(event -> {
            if (event.getButton().equals(MouseButton.PRIMARY)) {
                if (event.getClickCount() == 2) {
                    if (isSelectClientFile) {
                        if (Files.isDirectory(Paths.get(root + "/" + converter.convertString(selectedHomeFile)))) {
                            root = Paths.get(root + "/" + converter.convertString(selectedHomeFile));
                            refreshClientFiles();
                        } else {
                            try {
                                Desktop.getDesktop().open(new File(root + "/" + converter.convertString(selectedHomeFile)));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        });

        // двойной клик, навигация или открытие файла сервера
        serverFileList.setOnMouseClicked(event -> {
            isSelectClientFile = false;
            if (event.getButton().equals(MouseButton.PRIMARY)) {
                if (event.getClickCount() == 2) {
                    if (selectedServerFile.contains("[Dir ]")) {
                        netty.sendMessage(new Message(selectedServerFile, Command.OPEN_IN));
                        refreshServerFiles();
                    } else if (selectedServerFile.contains("[file]")) {
                        Alert alert = new Alert((Alert.AlertType.CONFIRMATION));
                        alert.setTitle("open file from Cloud Storage");
                        alert.setHeaderText("to open " + converter.convertString(selectedServerFile) + "you must first download it. Download it to My Computer?");
                        Optional<ButtonType> result = alert.showAndWait();
                        if (result.isPresent()) {
                            if (result.get() == ButtonType.OK) {
                                netty.sendMessage(new Message(converter.convertString(selectedServerFile), Command.DOWNLOAD));
                                log.debug("Client file download");
                                refreshServerFiles();
                            }
                        } else if (result.get() == ButtonType.CANCEL) {
                            alert.close();
                        }
                    }
                }
            }
        });
    }

    // авторизация на сервере
    private void auth() {
        javafx.scene.control.Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Authentication");
        dialog.setHeaderText("Cloud Storage Authentication\nEnter user and password");
        dialog.setResizable(false);

        Label label1 = new Label("User: ");
        Label label2 = new Label("Password: ");
        Label label3 = new Label("New user?");
        javafx.scene.control.TextField user = new TextField();
        PasswordField pass = new PasswordField();
        RadioButton isNew = new RadioButton();

        GridPane grid = new GridPane();
        grid.add(label1, 1, 1);
        grid.add(user, 2, 1);
        grid.add(label2, 1, 2);
        grid.add(pass, 2, 2);
        grid.add(label3, 1, 3);
        grid.add(isNew, 2, 3);
        dialog.getDialogPane().setContent(grid);
        ButtonType ok = new ButtonType("Ok", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(ok);

        while (!Authentication.isAuth()) {
            Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) {
                log.debug("User {} Pass {} isNew {}", user.getText(), pass.getText(), isNew.isSelected());
                netty.sendAuth(new AuthMessage(isNew.isSelected(), false, user.getText(), pass.getText()));

                try {
                    Thread.sleep(1000);// задержка на отправку и возврат авторизации из базы
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }

                if (Authentication.isAuth()) {
                    log.debug("Auth {}", Authentication.isAuth());
                    log.debug("Logged");
                    return;
                } else {
                    dialog.setHeaderText("login or password uncorrected\ntry again ...");
                    user.clear();
                    pass.clear();
                }
            }
        }
    }

    // обновление таблицы файлов клиента
    private void refreshServerFiles() {
        try {
            Thread.sleep(DELAY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ObservableList<String> itemsServer = FXCollections.observableArrayList(handler.getServerFiles());
        serverFileList.setItems(itemsServer);
        serverFileList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        serverFileList.getSelectionModel().getSelectedItems().addListener(
                (ListChangeListener.Change<? extends String> change) ->
                {
                    ObservableList<String> oList = serverFileList.getSelectionModel().getSelectedItems();
                    //log.debug("ObservableList: {}", oList);

                    selectedServerFile = oList.get(0);
                    log.debug("Selected server files: {}", selectedServerFile);
                    isSelectClientFile = false;
                    log.debug("Select client file {}", isSelectClientFile);
                });
        updateServerPathLabel(handler.getServerPath());
    }

    // обновление таблицы файлов клиента
    private void refreshClientFiles() {
        File file = new File(root.toString());
        List<String> files = Arrays.stream(file.list())
                .map(m -> new File(root.toString() + "\\" + m))
                .map(n -> {
                    if (n.isDirectory()) {
                        return "[Dir ]" + n;
                    } else {
                        return "[file]" + n + "\t\t" + converter.convertTime(n.lastModified()) + " " + converter.convertFileSize(n);
                    }
                })
                .sorted()
                .map(o -> o.substring(0, 6) + o.substring(o.lastIndexOf("\\") + 1))
                .peek(System.out::println)
                .collect(Collectors.toList());
        log.debug("Items in rootDir {}", files);
        try {
            Thread.sleep(DELAY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ObservableList<String> itemsClient = FXCollections.observableArrayList(files);
        clientFileList.setItems(itemsClient);
        clientFileList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        clientFileList.getSelectionModel().getSelectedItems().addListener(
                (ListChangeListener.Change<? extends String> change) ->
                {
                    ObservableList<String> oList = clientFileList.getSelectionModel().getSelectedItems();
                    //log.debug("ObservableList: {}", oList);

                    selectedHomeFile = oList.get(0);
                    log.debug("Selected client files: {}", selectedHomeFile);
                    isSelectClientFile = true;
                    log.debug("Select client file {}", isSelectClientFile);
                });
        updateClientPathLabel(root);
    }

    // создание файла , папки
    public void create(ActionEvent event) {
        TextInputDialog dialog = new TextInputDialog("newFile");
        dialog.setTitle("create new file");
        dialog.setHeaderText("create ?");
        ComboBox comboBox = new ComboBox<String>();
        ObservableList<String> oList = FXCollections.observableArrayList();
        oList.addAll("File on Client", "Directory on Client", "File on Server", "Directory on Server");
        comboBox.setItems(oList);
        comboBox.getSelectionModel().selectFirst();
        dialog.setGraphic(comboBox);
        Optional<String> result = dialog.showAndWait();
        String entered = "";
        if (result.isPresent()) {
            entered = result.get();
        }
        switch ((String) comboBox.getValue()) {
            case "File on Client":
                try {
                    Files.createFile(Paths.get(root.toString() + "/" + entered));
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                break;
            case "Directory on Client":
                try {
                    Files.createDirectory(Paths.get(root.toString() + "/" + entered));
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                break;
            case "File on Server":
                netty.sendMessage(new Message(entered, Command.NEW_FILE));
                break;
            case "Directory on Server":
                netty.sendMessage(new Message(entered, Command.NEW_DIRECTORY));
                break;
        }
        refreshClientFiles();
        refreshServerFiles();
    }

    // отправка файла
    public void upload(ActionEvent event) {
        if (isSelectClientFile) {
            if (Files.isDirectory(Paths.get(root + "/" + converter.convertString(selectedHomeFile)))) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("upload file");
                alert.setHeaderText("you cannot upload directory, choose a file");
                alert.showAndWait();
            } else {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("upload file");
                alert.setHeaderText("upload " + converter.convertString(selectedHomeFile) + "?");
                Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent()) {
                    if (result.get() == ButtonType.OK) {
                        try {
                            log.debug("File to upload {}", root + "/" + converter.convertString(selectedHomeFile));
                            FileInputStream fis = new FileInputStream(root + "/" + converter.convertString(selectedHomeFile));
                            byte[] buf = new byte[fis.available()];
                            fis.read(buf);
                            netty.sendMessage(new Message(converter.convertString(selectedHomeFile), Command.UPLOAD, buf));
                            log.debug("Client file upload");
                            refreshServerFiles();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else if (result.get() == ButtonType.CANCEL) {
                        return;
                    }
                }
            }
        } else {
            return;
        }
    }

    // получение файла
    public void download(ActionEvent event) {
        if (!isSelectClientFile) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("download file");
            alert.setHeaderText("download " + converter.convertString(selectedServerFile) + "?");
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == ButtonType.OK) {
                    netty.sendMessage(new Message(converter.convertString(selectedServerFile), Command.DOWNLOAD));
                    handler.setClientPath(root);
                    log.debug("Client file download");
                    try {
                        Thread.sleep(DELAY);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    refreshClientFiles();
                } else if (result.get() == ButtonType.CANCEL) {
                    return;
                }
            }
        } else {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("download file");
            alert.setHeaderText("you have not selected a file to download");
            alert.showAndWait();
        }
    }

    // переименование файла папки
    public void rename(ActionEvent event) throws IOException {
        if (isSelectClientFile) {
            TextInputDialog dialog = new TextInputDialog(converter.convertString(selectedHomeFile));
            dialog.setTitle("rename file");
            dialog.setHeaderText("rename " + converter.convertString(selectedHomeFile) + "?");
            Optional<String> result = dialog.showAndWait();
            String entered = "";
            if (result.isPresent()) {
                entered = result.get();
            }
            log.debug(entered);
            Files.move(Paths.get(root.toString() + "/" + converter.convertString(selectedHomeFile)), Paths.get(root.toString() + "/" + entered));
        } else {
            TextInputDialog dialog = new TextInputDialog(converter.convertString(selectedServerFile));
            dialog.setTitle("rename file");
            dialog.setHeaderText("rename " + converter.convertString(selectedServerFile) + "?");
            Optional<String> result = dialog.showAndWait();
            String entered = "";
            if (result.isPresent()) {
                entered = result.get();
            }
            log.debug(entered);
            netty.sendMessage(new Message(entered, converter.convertString(selectedServerFile), Command.RENAME));
        }
        refreshClientFiles();
        refreshServerFiles();
    }

    // удаление файла, папки
    public void delete(ActionEvent event) {
        if (isSelectClientFile) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("delete file");
            alert.setHeaderText("delete " + converter.convertString(selectedHomeFile) + "?");
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == ButtonType.OK) {
                    try {
                        log.debug("Client file delete");
                        Files.deleteIfExists(Paths.get(root.toString() + "/" + converter.convertString(selectedHomeFile)));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else if (result.get() == ButtonType.CANCEL) {
                    return;
                }
            }
        } else {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("delete file");
            alert.setHeaderText("delete " + converter.convertString(selectedServerFile) + "?");
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == ButtonType.OK) {
                    log.debug("Server file delete");
                    netty.sendMessage(new Message(converter.convertString(selectedServerFile), Command.DELETE));
                } else if (result.get() == ButtonType.CANCEL) {
                    return;
                }
            }
        }
        refreshClientFiles();
        refreshServerFiles();
    }

    // навигация на уровень выше на клиенте
    public void clientLevelUp(ActionEvent actionEvent) {
        root = Paths.get(root.toString().substring(0, root.toString().lastIndexOf("\\")));
        updateClientPathLabel(root);
        refreshClientFiles();

    }

    // навигация на уровень выше на сервере
    public void serverLevelUp(ActionEvent actionEvent) {
        String serverPath = handler.getServerPath().substring(0, handler.getServerPath().lastIndexOf("\\"));
        netty.sendMessage(new Message(serverPath, Command.OPEN_OUT));
        refreshServerFiles();
    }

    // обновить указатель пути клиента
    private void updateClientPathLabel(Path root) {
        clientLevelUpButton.setText("↑↑  " + root.toString());
    }

    // обновить указатель пути сервера
    private void updateServerPathLabel(String path) {
        serverLevelButton.setText("↑↑  " + path);
    }
}
