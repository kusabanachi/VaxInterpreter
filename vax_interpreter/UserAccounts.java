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

class Account {
    String name;
    short uid;
    short gid;
    Account(String name, short uid, short gid) {
        this.name = name;
        this.uid = uid;
        this.gid = gid;
    }
}

class UserAccounts {
    public static final short NO_GID = -1;

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
                    accounts.put(uid, new Account(name, uid, gid));
                }
            }
        } catch (IOException e) {}
    }

    public static Account getLoginAccount() {
        UserPrincipalLookupService lookupService = FileSystems.getDefault().getUserPrincipalLookupService();
        try {
            String userName = System.getProperty("user.name");
            UserPrincipal principal = lookupService.lookupPrincipalByName(userName);
            short uid = (short)principal.hashCode();

            Account acc = accounts.get(uid);
            if (acc == null) {
                return registerLoginAccount();
            }
            return acc;
        } catch (IOException e) {
            throw new RuntimeException("Error looking up principal by login name: " + e);
        }
    }

    private static Account registerLoginAccount() {
        try {
            short uid, gid;
            String name;
            Path tempFile = Files.createTempFile("vaxterp", ".tmp");
            PosixFileAttributes posixAttrs = Files.getFileAttributeView(tempFile, PosixFileAttributeView.class).readAttributes();
            if (posixAttrs != null) {
                name = posixAttrs.owner().getName();
                uid = (short)posixAttrs.owner().hashCode();
                gid = (short)posixAttrs.group().hashCode();
            } else {
                String ownerName = Files.getOwner(tempFile).getName();
                name = ownerName.substring(ownerName.lastIndexOf('\\') + 1);
                uid = (short)Files.getOwner(tempFile).hashCode();
                gid = NO_GID;
            }
            Files.delete(tempFile);
            Account acc = new Account(name, uid, gid);
            accounts.put(uid, acc);
            return acc;
        } catch (IOException e) {
            throw new RuntimeException("Error creating temporary file: " + e);
        }
    }
}
