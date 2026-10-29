package vrs.db;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * MySQL connection settings, stored in "db.properties" in the project folder.
 * The file is created the first time you save settings from the app.
 */
public final class DbConfig {
    public static final Path FILE = Paths.get("db.properties");

    public String host = "localhost";
    public int port = 3306;
    public String database = "vehicle_rental_db";
    public String user = "root";
    public String password = "";

    public String url() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
    }

    public static DbConfig load() {
        DbConfig c = new DbConfig();
        if (Files.exists(FILE)) {
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(FILE)) {
                p.load(r);
                c.host = p.getProperty("db.host", c.host).trim();
                c.port = Integer.parseInt(p.getProperty("db.port", String.valueOf(c.port)).trim());
                c.database = p.getProperty("db.name", c.database).trim();
                c.user = p.getProperty("db.user", c.user).trim();
                c.password = p.getProperty("db.password", c.password);
            } catch (IOException | NumberFormatException e) {
                System.err.println("Could not read db.properties: " + e.getMessage());
            }
        }
        return c;
    }

    public void save() throws IOException {
        Properties p = new Properties();
        p.setProperty("db.host", host);
        p.setProperty("db.port", String.valueOf(port));
        p.setProperty("db.name", database);
        p.setProperty("db.user", user);
        p.setProperty("db.password", password);
        try (Writer w = Files.newBufferedWriter(FILE)) {
            p.store(w, "Vehicle Rental System - MySQL connection");
        }
    }

    public DbConfig copy() {
        DbConfig c = new DbConfig();
        c.host = host; c.port = port; c.database = database; c.user = user; c.password = password;
        return c;
    }
}
