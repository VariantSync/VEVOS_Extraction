package org.variantsync.vevos.extraction;

import org.apache.commons.lang3.NotImplementedException;
import org.tinylog.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class Serde {

    public static <V> V deserialize(File file) {
        try (ObjectInputStream is = new ObjectInputStream(new FileInputStream(file))) {
            Object obj = is.readObject();
            try {
                if (obj == null) {
                    Logger.error("Read a null from file {}", file);
                    throw new NullPointerException();
                }
                return (V) obj;
            } catch(ClassCastException e) {
                Logger.error("Was not able to cast loaded object: {}", obj);
                Logger.error(e);
                throw new RuntimeException(e);
            }
        } catch (IOException | ClassNotFoundException e){
            Logger.error("Was not able to deserialize object under {}", file);
            Logger.error(e);
            throw new RuntimeException(e);
        }
    }

    public static void serialize(File file, Serializable obj) {
        if (obj == null) {
            Logger.error("Tried to serialize a null value to file {}", file);
            throw new NullPointerException();
        }
        try (ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(file))) {
            os.writeObject(obj);
        } catch (IOException e) {
            Logger.error("Was not able to serialize {} to file {}", obj, file);
            Logger.error(e);
            throw new UncheckedIOException(e);
        }
    }

    public static void writeToFile(Path path, String text, OpenOption... options) {
        try {
            Files.writeString(path, text, options);
        } catch (IOException e) {
            Logger.error(e);
            throw new UncheckedIOException(e);
        }
    }

    public static void writeToFile(Path path, String text) {
        writeToFile(path, text, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

    public static void appendText(Path path, String text) {
        writeToFile(path, text, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }
}
