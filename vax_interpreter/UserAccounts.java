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
    public static final short nogroup = (short)65534;

    private final static HashMap<Short, Account> accounts = new HashMap<>();
    private final static HashMap<Short, String> groups = new HashMap<>();

    static {
        readPasswd();
        readGroup();
    }

    private static void readPasswd() {
        Path passwd = Paths.get(Kernel.rootPath, "etc/passwd");
        try {
            for (String line : Files.readAllLines(passwd, StandardCharsets.US_ASCII)) {
                String[] pwEnt = line.split(":");
                if (pwEnt.length > 3) {
                    String name = pwEnt[0];
                    short uid = Short.parseShort(pwEnt[2]);
                    short gid = Short.parseShort(pwEnt[3]);
                    accounts.put(uid, new Account(name, uid, gid));
                }
            }
        } catch (IOException e) {}
    }

    private static void readGroup() {
        Path group = Paths.get(Kernel.rootPath, "etc/group");
        try {
            for (String line : Files.readAllLines(group, StandardCharsets.US_ASCII)) {
                String[] grEnt = line.split(":");
                if (grEnt.length > 2) {
                    String name = grEnt[0];
                    short gid = Short.parseShort(grEnt[2]);
                    groups.put(gid, name);
                }
            }
        } catch (IOException e) {}
    }

    public static Account getLoginAccount() {
        UserPrincipalLookupService lookupService = FileSystems.getDefault().getUserPrincipalLookupService();
        try {
            String usrName = System.getProperty("user.name");
            UserPrincipal principal = lookupService.lookupPrincipalByName(usrName);
            short uid = (short)principal.hashCode();

            Account acc = accounts.get(uid);
            if (acc == null) {
                return registerLoginAccount();
            }
            return acc;
        } catch (IOException e) {
            return null;
        }
    }

    private static Account registerLoginAccount() {
        try {
            short uid, gid;
            String usrName, grpName = "";
            Path tempFile = Files.createTempFile("vaxterp", ".tmp");
            PosixFileAttributes posixAttrs = Files.getFileAttributeView(tempFile, PosixFileAttributeView.class).readAttributes();
            if (posixAttrs != null) {
                usrName = posixAttrs.owner().getName();
                uid = (short)posixAttrs.owner().hashCode();
                grpName = posixAttrs.group().getName();
                gid = (short)posixAttrs.group().hashCode();
            } else {
                String ownerName = Files.getOwner(tempFile).getName();
                usrName = ownerName.substring(ownerName.lastIndexOf('\\') + 1);
                uid = (short)Files.getOwner(tempFile).hashCode();
                gid = nogroup;
            }
            Files.delete(tempFile);
            Account acc = new Account(usrName, uid, gid);
            accounts.put(uid, acc);
            if (gid != nogroup) {
                groups.put(gid, grpName);
            }
            return acc;
        } catch (IOException e) {
            return null;
        }
    }

    public static String getUserName(short uid) {
        Account acc = accounts.get(uid);
        return (acc != null) ? acc.name : "";
    }

    public static String getGroupName(short gid) {
        String grpName = groups.get(gid);
        return (grpName != null) ? grpName : "";
    }
}
