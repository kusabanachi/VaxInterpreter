package vax_interpreter;

import java.util.*;
import java.nio.channels.*;
import java.nio.ByteBuffer;
import java.io.File;
import java.io.RandomAccessFile;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import static vax_interpreter.Kernel.Constant.*;

class FileItem {
    private final Channel chan;
    private final byte f_flag;
    private byte f_count;

    public static final FileItem stdin = new FileItem(Channels.newChannel(System.in), FREAD);
    public static final FileItem stdout = new FileItem(Channels.newChannel(System.out), FWRITE);
    public static final FileItem stderr = new FileItem(Channels.newChannel(System.err), FWRITE);

    private FileItem(Channel ch, int mode) {
        chan = ch;
        f_count = 1;
        f_flag = (byte)(mode & (FREAD | FWRITE));
    }

    public static FileItem open(String fname, int mode) throws FileItemException {
        File file = new File(fname);
        if (!file.exists()) {
            throw new FileItemException(ENOENT);
        }

        if ((mode & FREAD) != 0) {
            if (!file.canRead()) {
                throw new FileItemException(EACCES);
            }
        }
        if ((mode & FWRITE) != 0) {
            if (!file.canWrite()) {
                throw new FileItemException(EACCES);
            }
        }

        try {
            Channel ch = open(file, mode);
            return new FileItem(ch, mode);
        } catch (FileNotFoundException e) {
            throw new FileItemException(ENOENT);
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    private static Channel open(File file, int mode) throws FileNotFoundException, IOException {
        FileChannel ch = null;
        if ((mode & FREAD) != 0 && (mode & FWRITE) != 0) {
            ch = new RandomAccessFile(file, "rw").getChannel();
        } else if ((mode & FREAD) != 0) {
            ch = new FileInputStream(file).getChannel();
        } else if ((mode & FWRITE) != 0) {
            ch = new FileOutputStream(file, true).getChannel();
            ch.position(0);
        }
        return ch;
    }

    public static FileItem create(String fname, int fmode) throws FileItemException {
        boolean isCreatedNewFile = false;

        File file = new File(fname);
        if (!file.exists()) {
            try {
                isCreatedNewFile = file.createNewFile();
            } catch (IOException e) {
                isCreatedNewFile = false;
            }
            if (!isCreatedNewFile) {
                throw new FileItemException(ENFILE);
            }
        } else {
            if (!file.canWrite()) {
                throw new FileItemException(EACCES);
            }
        }

        FileItem fItem;
        try {
            Channel ch = new FileOutputStream(file).getChannel();
            fItem = new FileItem(ch, FWRITE);
        } catch (FileNotFoundException e) {
            throw new FileItemException(ENOENT);
        }

        // The file mode is set at last to succeed FileOutputStream constructer.
        // If file mode is read-only, it will be failed.
        if (isCreatedNewFile) {
            if (!Kernel.Sysent.setFileMode(file, fmode)) {
                throw new RuntimeException();
            }
        }
        return fItem;
    }

    public void close() {
        if (--f_count > 0) {
            return;
        }
        try {
            chan.close();
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }

    public byte[] read(int count) throws FileItemException {
        if ((f_flag & FREAD) == 0) {
            throw new FileItemException(EBADF);
        }

        ByteBuffer buf = ByteBuffer.allocate(count);
        try {
            int rcount = ((ReadableByteChannel)chan).read(buf);
            if (rcount > 0) {
                return Arrays.copyOf(buf.array(), rcount);
            } else {
                return new byte[0];
            }
        } catch (NonReadableChannelException e) {
            throw new FileItemException(EBADF);
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    public int write(byte[] bytes) throws FileItemException {
        if ((f_flag & FWRITE) == 0) {
            throw new FileItemException(EBADF);
        }

        ByteBuffer buf = ByteBuffer.wrap(bytes);
        try {
            return ((WritableByteChannel)chan).write(buf);
        } catch (NonWritableChannelException e) {
            throw new FileItemException(EBADF);
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    public int seek(int offset, int sbase) throws FileItemException {
        FileChannel fch = (FileChannel)chan;
        try {
            if (sbase == 1) {
                offset += fch.position();
            } else if (sbase == 2) {
                offset += fch.size();
            }
            fch.position(offset);
            return offset;
        } catch (IOException e) {
            throw new FileItemException(ESPIPE);
        }
    }

    public boolean isNormalFile() {
        return chan instanceof FileChannel;
    }

    public void addReference() {
        ++f_count;
    }
}

class FileItemException extends Exception {
    public int error;
    public FileItemException(int error) {
        this.error = error;
    }
}

