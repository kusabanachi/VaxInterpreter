package vax_interpreter;

import java.util.*;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.ClosedChannelException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.DirectoryStream;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Pipe;
import static vax_interpreter.Kernel.Constant.*;

class FileItem {
    private final SeekableByteChannel chan;
    private final Path path;
    private final byte f_flag;
    private byte f_count;

    public static final FileItem stdin = new FileItem(new ConsoleChannel(System.in), FREAD);
    public static final FileItem stdout = new FileItem(new ConsoleChannel(System.out), FWRITE);
    public static final FileItem stderr = new FileItem(new ConsoleChannel(System.err), FWRITE);

    private FileItem(SeekableByteChannel ch, Path path, int mode) {
        this.chan = ch;
        this.path = path;
        this.f_flag = (byte)(mode & (FREAD | FWRITE));
        this.f_count = 1;
    }

    private FileItem(SeekableByteChannel ch, int mode) {
        this(ch, null, mode);
    }

    public static FileItem open(String fname, int mode) throws FileItemException {
        Path fpath = Paths.get(fname);
        if (!Files.exists(fpath, NOFOLLOW_LINKS)) {
            throw new FileItemException(ENOENT);
        }
        if (Files.isDirectory(fpath, NOFOLLOW_LINKS)) {
            return openDir(fpath, mode);
        } else {
            return openFile(fpath, mode);
        }
    }

    private static FileItem openFile(Path fpath, int mode) throws FileItemException {
        if ((mode & FREAD) != 0) {
            if (!Files.isReadable(fpath)) {
                throw new FileItemException(EACCES);
            }
        }
        if ((mode & FWRITE) != 0) {
            if (!Files.isWritable(fpath)) {
                throw new FileItemException(EACCES);
            }
        }

        try {
            SeekableByteChannel ch = openChannel(fpath, mode);
            return new FileItem(ch, fpath, mode);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static SeekableByteChannel openChannel(Path fpath, int mode) throws IOException {
        List<OpenOption> options = new ArrayList<>();
        if ((mode & FREAD) != 0) {
            options.add(StandardOpenOption.READ);
        }
        if ((mode & FWRITE) != 0) {
            options.add(StandardOpenOption.WRITE);
        }
        return Files.newByteChannel(fpath, options.toArray(new OpenOption[0]));
    }

    private static FileItem openDir(Path dpath, int mode) throws FileItemException {
        if ((mode & FREAD) != 0) {
            if (!Files.isReadable(dpath)) {
                throw new FileItemException(EACCES);
            }
        }
        if ((mode & FWRITE) != 0) {
            throw new FileItemException(EISDIR);
        }

        return new FileItem(new DirChannel(dpath), dpath, mode);
    }

    public static FileItem create(String fname, int fmode) throws FileItemException {
        boolean created = false;

        Path fpath = Paths.get(fname);
        if (!Files.exists(fpath, NOFOLLOW_LINKS)) {
            try {
                Files.createFile(fpath);
                created = true;
            } catch (IOException e) {
                throw new FileItemException(ENOENT);
            }
        } else {
            if (!Files.isWritable(fpath)) {
                throw new FileItemException(EACCES);
            }
        }

        FileItem fItem;
        try {
            SeekableByteChannel ch = Files.newByteChannel(fpath, StandardOpenOption.WRITE);
            fItem = new FileItem(ch, fpath, FWRITE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // The file mode is set at last to make SeekableByteChannel with write option.
        // If file mode is read-only, it would be failed.
        if (created) {
            FileOperations.setFileMode(fpath, fmode);
        }
        return fItem;
    }

    public static FileItem[] openPipe() {
        PipeChannel pipech = new PipeChannel();
        return new FileItem[] {new FileItem(pipech, null, FREAD),
                               new FileItem(pipech, null, FWRITE)};
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
            int rcount = chan.read(buf);
            if (rcount > 0) {
                return Arrays.copyOf(buf.array(), rcount);
            } else {
                return new byte[0];
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int write(byte[] bytes) throws FileItemException {
        if ((f_flag & FWRITE) == 0) {
            throw new FileItemException(EBADF);
        }

        ByteBuffer buf = ByteBuffer.wrap(bytes);
        try {
            return chan.write(buf);
        } catch (IOException e) {
            Throwable cause = e.getCause();
            if (cause instanceof FileItemException) {
                throw (FileItemException)cause;
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    public int seek(int offset, int sbase) throws FileItemException {
        if (chan instanceof PipeChannel) {
            throw new FileItemException(ESPIPE);
        }

        try {
            if (sbase == 1) {
                offset += chan.position();
            } else if (sbase == 2) {
                offset += chan.size();
            }
            chan.position(offset);
            return offset;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isNormalFile() {
        return chan instanceof FileChannel;
    }

    public void addReference() {
        ++f_count;
    }

    public Path getPath() {
        return path;
    }
}

class DirChannel implements SeekableByteChannel {
    private final ByteBuffer buf;
    private boolean isOpen;

    public DirChannel(Path dpath) {
        List<Path> files =
            new ArrayList<>(Arrays.asList(dpath.resolve("."), dpath.resolve("..")));
        try (DirectoryStream<Path> dstream = Files.newDirectoryStream(dpath)) {
            for (Path entry : dstream) {
                files.add(entry);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final int DirEntrySize = 16;
        ByteBuffer buf = ByteBuffer.allocate(DirEntrySize * files.size())
                                   .order(ByteOrder.LITTLE_ENDIAN);
        for (Path entry : files) {
            buf.putShort(FileOperations.getFileId(entry));    // d_ino
            byte[] fname = entry.getFileName().toString()
                                .getBytes(StandardCharsets.US_ASCII);
            buf.put(Arrays.copyOf(fname, 14));                // d_name[14]
        }
        buf.flip();

        this.buf = buf;
        this.isOpen = true;
    }

    @Override public long position() throws IOException {
        if (!isOpen) {
            throw new ClosedChannelException();
        }
        return buf.position();
    }

    @Override public SeekableByteChannel position(long newPosition) throws IOException {
        if (!isOpen) {
            throw new ClosedChannelException();
        }
        buf.position((int)newPosition);
        return this;
    }

    @Override public int read(ByteBuffer dst) throws IOException {
        if (!isOpen) {
            throw new ClosedChannelException();
        }
        if (buf.hasRemaining()) {
            byte[] bytes = new byte[Math.min(buf.remaining(), dst.remaining())];
            buf.get(bytes);
            dst.put(bytes);
            return bytes.length;
        } else {
            return -1;
        }
    }

    @Override public long size() throws IOException {
        if (!isOpen) {
            throw new ClosedChannelException();
        }
        return buf.capacity();
    }

    @Override public SeekableByteChannel truncate(long size) {
        throw new NonWritableChannelException();
    }

    @Override public int write(ByteBuffer src) {
        throw new NonWritableChannelException();
    }

    @Override public void close() throws IOException {
        if (!isOpen) {
            throw new ClosedChannelException();
        }
        isOpen = false;
    }

    @Override public boolean isOpen() {
        return isOpen;
    }
}

class ConsoleChannel implements SeekableByteChannel {
    private final Channel chan;
    private long position;

    public ConsoleChannel(InputStream in) {
        this.chan = Channels.newChannel(in);
    }

    public ConsoleChannel(OutputStream out) {
        this.chan = Channels.newChannel(out);
    }

    @Override public long position() throws IOException {
        return position;
    }

    @Override public SeekableByteChannel position(long newPosition) throws IOException {
        position = newPosition;
        return this;
    }

    @Override public int read(ByteBuffer dst) throws IOException {
        return ((ReadableByteChannel)chan).read(dst);
    }

    @Override public long size() throws IOException {
        return 0;
    }

    @Override public SeekableByteChannel truncate(long size) throws IOException {
        throw new IOException();
    }

    @Override public int write(ByteBuffer src) throws IOException {
        return ((WritableByteChannel)chan).write(src);
    }

    @Override public void close() throws IOException {
        chan.close();
    }

    @Override public boolean isOpen() {
        return chan.isOpen();
    }
}

class PipeChannel implements SeekableByteChannel {
    private static final int PIPSIZ = 4096;

    private final Pipe.SinkChannel sinkChan;
    private final Pipe.SourceChannel srcChan;
    private byte i_count;
    private int i_size;
    private long position;

    public PipeChannel() {
        try {
            Pipe pipe = Pipe.open();
            this.sinkChan = pipe.sink();
            this.srcChan = pipe.source();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.i_count = 2;
    }

    @Override public long position() throws IOException {
        return position;
    }

    @Override public SeekableByteChannel position(long newPosition) throws IOException {
        position = newPosition;
        return this;
    }

    @Override public int read(ByteBuffer dst) throws IOException {
        while (i_size == 0) {
            // pipe is empty

            if (i_count < 2) {
                // no writing pipe
                return -1;
            }

            try {
                Context.class.notifyAll();
                Context.class.wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        int readCount = srcChan.read(dst);
        position += readCount;
        if (position == i_size) {
            position = 0;
            i_size = 0;
        }

        return readCount;
    }

    @Override public long size() throws IOException {
        return i_size;
    }

    @Override public SeekableByteChannel truncate(long size) throws IOException {
        throw new IOException();
    }

    @Override public int write(ByteBuffer src) throws IOException {
        int resid = src.remaining();
        int writeCount = resid;

        while (resid > 0) {
            if (i_count < 2) {
                // no reading pipe
                throw new IOException(new FileItemException(EPIPE));
            }

            if (i_size >= PIPSIZ) {
                // pipe is full
                try {
                    Context.class.notifyAll();
                    Context.class.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                continue;
            }

            writeCount = writep(src, Math.min(resid, PIPSIZ));
            resid -= writeCount;
            i_size += writeCount;
        }
        return writeCount;
    }

    private int writep(ByteBuffer src, int count) throws IOException {
        int preLimit = src.limit();
        src.limit(src.position() + count);
        int n = sinkChan.write(src);
        src.limit(preLimit);
        return n;
    }


    @Override public void close() throws IOException {
        if (--i_count > 0) {
            return;
        }
        srcChan.close();
        sinkChan.close();
    }

    @Override public boolean isOpen() {
        return true;
    }
}

class FileItemException extends Exception {
    public int error;
    public FileItemException(int error) {
        this.error = error;
    }
}

