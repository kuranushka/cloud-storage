package ru.kuranov.handler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Converter {
    // изменяем время последней модификации файла в удобный формат
    public String convertTime(long t) {
        Date date = new Date(t);
        Format format = new SimpleDateFormat("HH:mm dd.MM.yy");
        return format.format(date);
    }

    // конвертируем размер файла в удобный формат
    public String convertFileSize(File file) {
        long size = 0;
        try {
            size = Files.size(file.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (size < 1024) {
            return String.format("%,d b", size);
        } else {
            return String.format("%,d kb", size / 1024);
        }
    }

    // очищаем строки из таблиц
    public String convertString(String unconverted) {
        if (unconverted.contains("\t")) {
            return unconverted.substring(6, unconverted.indexOf("\t"));
        } else {
            return unconverted.substring(6);
        }
    }
}
