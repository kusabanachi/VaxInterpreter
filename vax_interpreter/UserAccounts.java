package vax_interpreter;

import java.util.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

class UserAccounts {
    private static class Account {
        String name;
        short gid;
        Account(String name, short gid) {
            this.name = name;
            this.gid = gid;
        }
    }

    private final static HashMap<Short, Account> accounts = new HashMap<>();
    static {
        readPasswd();
    }

    private static void readPasswd() {
        Path passwd = Paths.get(Kernel.rootPath, "etc/passwd");
        try {
            for (String line : Files.readAllLines(passwd, StandardCharsets.US_ASCII)) {
                String[] pwEnt = line.split(":");
                if (pwEnt.length == 6) {
                    String name = pwEnt[0];
                    short uid = Short.parseShort(pwEnt[2]);
                    short gid = Short.parseShort(pwEnt[3]);
                    accounts.put(uid, new Account(name, gid));
                }
            }
        } catch (IOException e) {}
    }

    public static short getLoginAccount() {
        UserPrincipalLookupService lookupService = FileSystems.getDefault().getUserPrincipalLookupService();
        try {
            String userName = System.getProperty("user.name");
            UserPrincipal principal = lookupService.lookupPrincipalByName(userName);
            short uid = (short)principal.hashCode();

            Account acc = accounts.get(uid);
            if (acc == null) {
                registerLoginAccount();
            }
            return uid;
        } catch (IOException e) {
            throw new RuntimeException("Error looking up principal by login name: " + e);
        }
    }

    private static void registerLoginAccount() {
        try {
            Path tempFile = Files.createTempFile("vaxterp", ".tmp");
            PosixFileAttributes posixAttrs = Files.getFileAttributeView(tempFile, PosixFileAttributeView.class).readAttributes();
            if (posixAttrs != null) {
                String name = posixAttrs.owner().getName();
                short uid = (short)posixAttrs.owner().hashCode();
                short gid = (short)posixAttrs.group().hashCode();
                accounts.put(uid, new Account(name, gid));
            } else {
                String ownerName = Files.getOwner(tempFile).getName();
                String name = ownerName.substring(ownerName.lastIndexOf('\\') + 1);
                short uid = (short)Files.getOwner(tempFile).hashCode();
                accounts.put(uid, new Account(name, (short)-1));
            }
            Files.delete(tempFile);
        } catch (IOException e) {
            throw new RuntimeException("Error creating temporary file: " + e);
        }
    }
}
