package vax_interpreter;

import java.util.Set;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.FileStore;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.io.IOException;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static vax_interpreter.Kernel.Constant.*;

class FileOperations {
    public static byte[] getFileStatus(Path fpath) {
        BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(fpath, BasicFileAttributes.class, NOFOLLOW_LINKS);
        } catch (IOException e) {
            return null;
        }

        final int StatSize = 32;
        ByteBuffer statBuf = ByteBuffer.allocate(StatSize).order(ByteOrder.LITTLE_ENDIAN);
        statBuf.putShort(getDeviceId(fpath));                       // st_dev
        statBuf.putShort(getFileId(fpath));                         // st_ino
        statBuf.putShort(getFileMode(fpath));                       // st_mode
        statBuf.putShort(getNumberOfHardLink(fpath));               // st_nlink
        statBuf.putShort(getFileUser(fpath));                       // st_uid
        statBuf.putShort(getFileGroup(fpath));                      // st_gid
        statBuf.putShort((short)0);                                 // st_rdev
        statBuf.putShort((short)0);                                 // padding
        statBuf.putInt((int)attrs.size());                          // st_size
        statBuf.putInt((int)attrs.lastAccessTime().to(SECONDS));    // st_atime
        int mtime = (int)attrs.lastModifiedTime().to(SECONDS);
        statBuf.putInt(mtime);                                      // st_mtime
        statBuf.putInt(mtime);                                     // st_ctime
        return statBuf.array();
    }

    public static boolean setFileMode(Path fpath, int fmode) {
        File file = fpath.toFile();
        boolean rSet = file.setReadable((fmode & IREAD) != 0, (fmode & IREAD >> 6) == 0);
        boolean wSet = file.setWritable((fmode & IWRITE) != 0, (fmode & IWRITE >> 6) == 0);
        boolean eSet = file.setExecutable((fmode & IEXEC) != 0, (fmode & IEXEC >> 6) == 0);
        return rSet && wSet && eSet;
    }

    public static boolean changeFileOwner(Path fpath, int uid, int gid) {
        String usrName = UserAccounts.getUserName((short)uid);
        String grpName = UserAccounts.getGroupName((short)gid);
        if (grpName != null) {
            PosixFileAttributeView attrView = Files.getFileAttributeView(fpath, PosixFileAttributeView.class);
            if (attrView != null) {
                try {
                    UserPrincipalLookupService lookupService = FileSystems.getDefault().getUserPrincipalLookupService();
                    GroupPrincipal gPrincipal = lookupService.lookupPrincipalByGroupName(grpName);
                    attrView.setGroup(gPrincipal);
                    UserPrincipal uPrincipal = lookupService.lookupPrincipalByName(usrName);
                    attrView.setOwner(uPrincipal);
                    return true;
                } catch (IOException e) {}
            }
        }

        try {
            UserPrincipalLookupService lookupService = FileSystems.getDefault().getUserPrincipalLookupService();
            UserPrincipal uPrincipal = lookupService.lookupPrincipalByName(usrName);
            Files.setOwner(fpath, uPrincipal);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static short getDeviceId(Path fpath) {
        try {
            FileStore fstore = Files.getFileStore(fpath);
            return (short)fstore.hashCode();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static short getFileId(Path fpath) {
        try {
            Object filekey = Files.readAttributes(fpath, BasicFileAttributes.class, NOFOLLOW_LINKS).fileKey();
            if (filekey != null) {
                return (short)filekey.hashCode();
            } else {
                return (short)fpath.toAbsolutePath().normalize().hashCode();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static short getFileMode(Path fpath) {
        short mode = 0;
        if (Files.isRegularFile(fpath, NOFOLLOW_LINKS)) {
            mode |= IFREG;
        } else if (Files.isDirectory(fpath)) {
            mode |= IFDIR;
        }

        try {
            Set<PosixFilePermission> pperm = Files.getPosixFilePermissions(fpath, NOFOLLOW_LINKS);
            if (pperm.contains(PosixFilePermission.OWNER_READ)) {
                mode |= IREAD;
            }
            if (pperm.contains(PosixFilePermission.OWNER_WRITE)) {
                mode |= IWRITE;
            }
            if (pperm.contains(PosixFilePermission.OWNER_EXECUTE)) {
                mode |= IEXEC;
            }
            if (pperm.contains(PosixFilePermission.GROUP_READ)) {
                mode |= IREAD >> 3;
            }
            if (pperm.contains(PosixFilePermission.GROUP_WRITE)) {
                mode |= IWRITE >> 3;
            }
            if (pperm.contains(PosixFilePermission.GROUP_EXECUTE)) {
                mode |= IEXEC >> 3;
            }
            if (pperm.contains(PosixFilePermission.OTHERS_READ)) {
                mode |= IREAD >> 6;
            }
            if (pperm.contains(PosixFilePermission.OTHERS_WRITE)) {
                mode |= IWRITE >> 6;
            }
            if (pperm.contains(PosixFilePermission.OTHERS_EXECUTE)) {
                mode |= IEXEC >> 6;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return mode;
    }

    private static short getNumberOfHardLink(Path fpath) {
        try {
            Integer nlink = (Integer)Files.getAttribute(fpath, "unix:nlink");
            if (nlink != null) {
                return nlink.shortValue();
            }
        } catch (IOException e) {}

        return 1;
    }


    private static short getFileUser(Path fpath) {
        try {
            PosixFileAttributeView attrView = Files.getFileAttributeView(fpath, PosixFileAttributeView.class);
            if (attrView != null) {
                PosixFileAttributes posixAttrs = attrView.readAttributes();
                return (short)posixAttrs.owner().hashCode();
            } else {
                return (short)Files.getOwner(fpath).hashCode();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static short getFileGroup(Path fpath) {
        try {
            PosixFileAttributeView attrView = Files.getFileAttributeView(fpath, PosixFileAttributeView.class);
            if (attrView != null) {
                PosixFileAttributes posixAttrs = attrView.readAttributes();
                return (short)posixAttrs.group().hashCode();
            } else {
                return UserAccounts.nogroup;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
