package org.variantsync.vevos.extraction;

import org.tinylog.Logger;

import java.io.*;

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
}
