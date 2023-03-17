package org.variantsync.vevos.extraction;

import org.tinylog.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class Serde {

    /**
     * Deserializes an object stored in the given file in an unchecked fashion. This means that a RuntimeException is
     * thrown, if any other exception occurs.
     *
     * @param file The file containing the serialized object
     * @param <V>  The type of the object
     * @return The loaded object
     */
    public static <V> V deserialize(File file) {
        try (ObjectInputStream is = new ObjectInputStream(new FileInputStream(file))) {
            Object obj = is.readObject();
            try {
                if (obj == null) {
                    Logger.error("Read a null from file {}", file);
                    throw new NullPointerException();
                }
                return (V) obj;
            } catch (ClassCastException e) {
                Logger.error("Was not able to cast loaded object: {}", obj);
                Logger.error(e);
                throw new RuntimeException(e);
            }
        } catch (IOException | ClassNotFoundException e) {
            Logger.error("Was not able to deserialize object under {}", file);
            Logger.error(e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Serializes the given serializable object in an unchecked fashion. This means that a RuntimeException is thrown, if
     * any other exception occurs.
     *
     * @param file The file to which the object should be written to
     * @param obj  The object to serialize
     */
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

    /**
     * Writes the given text to the file under path with the provided options.
     *
     * @param path Path to the written file
     * @param text Text to write into the file
     */
    public static void writeToFile(Path path, String text, OpenOption... options) {
        try {
            Files.writeString(path, text, options);
        } catch (IOException e) {
            Logger.error(e);
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Writes the given text to the file under path. If the file does not exist, it is created. If it does exist, it is
     * overwritten.
     *
     * @param path Path to the written file
     * @param text Text to write into the file
     */
    public static void writeToFile(Path path, String text) {
        writeToFile(path, text, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

    /**
     * Appends the given text to the file found under path. The text is appended as is, that is without adding
     * additional line breaks or other delimiters.
     *
     * @param path Path to the edited file
     * @param text Text that is to be appended at the end
     */
    public static void appendText(Path path, String text) {
        writeToFile(path, text, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }
}
