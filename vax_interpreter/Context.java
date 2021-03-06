package vax_interpreter;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.util.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import static vax_interpreter.Util.*;
import static vax_interpreter.Kernel.Constant.*;
import vax_interpreter.Kernel.Proc;

class Context {
    public int[] register = new int[16];
    public int psl;
    public final Memory memory;
    public final User u;

    public Flag flagC = new Flag(0);
    public Flag flagV = new Flag(1);
    public Flag flagZ = new Flag(2);
    public Flag flagN = new Flag(3);
    public Flag flagT = new Flag(4);
    public Flag flagIV = new Flag(5);
    public Flag flagFU = new Flag(6);
    public Flag flagDV = new Flag(7);


    public Context() {
        memory = new Memory();
        u = new User();
    }

    public Context(Context src) {
        System.arraycopy(src.register, 0, register, 0, register.length);
        psl = src.psl;
        memory = new Memory(src.memory);
        u = new User(src.u);
    }

    public int pc() {
        return register[PC];
    }

    public IntData getRegisterValue(int regNum, DataType type) {
        if (type.size >= 4) {
            ByteBuffer bbuf = ByteBuffer.allocate(type.size).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < type.size; i += 4) {
                if (regNum <= PC) {
                    bbuf.putInt(register[regNum++]);
                } else {
                    bbuf.putInt(0);
                }
            }
            return new IntData(bbuf.array(), type);
        } else {
            return new IntData(register[regNum], type);
        }
    }

    public void setRegisterValue(int regNum, NumData val) {
        if (val.size() >= 4) {
            ByteBuffer bbuf = ByteBuffer.wrap(val.bytes()).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < val.size(); i += 4) {
                if (regNum > PC) {
                    break;
                }
                register[regNum++] = bbuf.getInt();
            }
        } else {
            IntData intval = (IntData)val;
            int mask = (int)(0xffffffffL << (intval.size() << 3));
            register[regNum] &= mask;
            register[regNum] |= intval.uint() & ~mask;
        }
    }

    public void push(int val) {
        push(new IntData(val));
    }

    public void push(NumData val) {
        assert val.size() == 4 : "Invalid stack push";
        register[SP] -= 4;
        memory.store(register[SP], val);
    }

    public int pop() {
        IntData val = memory.loadInt(register[SP], DataType.L);
        register[SP] += 4;
        return val.sint();
    }

    public int readText() {
        if (pc() < memory.textSize) {
            return memory.loadInt(register[PC]++, DataType.B).uint();
        } else {
            return -1;
        }
    }

    public int lookAhead() {
        if (pc() < memory.textSize) {
            return memory.loadInt(pc(), DataType.B).uint();
        } else {
            return -1;
        }
    }

    class Memory {
        private final byte[] mem = new byte[MEM_SIZE];
        public int textSize;
        private static final int AoutHeaderSize = 32;
        private static final int SegUnitSize = 0x200;

        Memory() {}

        Memory(Memory srcMem) {
            System.arraycopy(srcMem.mem, 0, mem, 0, mem.length);
            textSize = srcMem.textSize;
        }

        public void setArgs(List<String> argStrs) {
            int nChars = 0;
            for (String argStr : argStrs) {
                nChars += argStr.length() + 1;
            }

            nChars = nChars + (NBPW - 1) & ~(NBPW - 1);
            int ucp = MEM_SIZE - nChars - NBPW;
            int ap = ucp - (argStrs.size() + 3) * NBPW;
            register[SP] = ap;
            store(ap, new IntData(argStrs.size()));
            ap += NBPW;
            for (String str: argStrs) {
                store(ap, new IntData(ucp));
                ap += NBPW;
                byte[] strb = str.getBytes(StandardCharsets.US_ASCII);
                byte[] arg = Arrays.copyOf(strb, strb.length + 1);
                storeBytes(ucp, arg, arg.length);
                ucp += arg.length;
            }
            store(ap, new IntData(0));
            ap += NBPW;
            store(ucp, new IntData(0));

            register[PC] = 2; /* skip over entry mask */
        }

        public boolean loadTextfile(String path) throws IOException {
            try (InputStream in = new FileInputStream(path)) {
                return loadAout(in);
            }
        }

        private boolean loadAout(InputStream in) throws IOException {
            byte[] header = new byte[AoutHeaderSize];
            if (!readUntil(in, header, 0, AoutHeaderSize)) {
                return false;
            }

            ByteBuffer bbuf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            int magic = bbuf.getInt();
            if (magic != 0410) {
                return false;
            }
            int tsize = bbuf.getInt();
            int dsize = bbuf.getInt();

            if (!readUntil(in, mem, 0, tsize)) {
                return false;
            }

            int tsegSize = ((tsize + SegUnitSize - 1) / SegUnitSize) * SegUnitSize;
            if (!readUntil(in, mem, tsegSize, dsize)) {
                return false;
            }

            // clear bss and stack
            Arrays.fill(mem, tsegSize + dsize, MEM_SIZE, (byte)0);

            textSize = tsize;
            return true;
        }

        private boolean readUntil(InputStream in, byte[] b, int off, int len) throws IOException {
            int readCount = 0;
            while (readCount < len) {
                int readLen = in.read(b, off + readCount, len - readCount);
                if (readLen < 0) {
                    return false;
                }
                readCount += readLen;
            }
            return true;
        }

        public NumData load(int rawAddr, DataType type) {
            int addr = getMemAddress(rawAddr);
            if (type.isFloatDataType()) {
                return new FloatData(Arrays.copyOfRange(mem, addr, addr + type.size),
                                     type);
            } else {
                return new IntData(Arrays.copyOfRange(mem, addr, addr + type.size),
                                   type);
            }
        }

        public IntData loadInt(int rawAddr, DataType type) {
            return (IntData)load(rawAddr, type);
        }

        public FloatData loadFloat(int rawAddr, DataType type) {
            return (FloatData)load(rawAddr, type);
        }

        public void store(int rawAddr, NumData val) {
            int addr = getMemAddress(rawAddr);
            System.arraycopy(val.bytes(), 0, mem, addr, val.size());
        }

        public byte[] loadBytes(int rawAddr, int size) {
            int addr = getMemAddress(rawAddr);
            return Arrays.copyOfRange(mem, addr, addr + size);
        }

        public void storeBytes(int rawAddr, byte[] val, int size) {
            int addr = getMemAddress(rawAddr);
            System.arraycopy(val, 0, mem, addr, size);
        }

        public byte[] loadStringBytes(int rawAddr) {
            int addr = getMemAddress(rawAddr);
            int i = addr;
            while (mem[i++] != 0) {}
            return Arrays.copyOfRange(mem, addr, i);
        }

        private int getMemAddress(int addr) {
            if (addr >= 0x7fffff00) {
                return MEM_SIZE - (int)(0x80000000L - addr);
            } else {
                return addr;
            }
        }
    }

    class Flag {
        private final int mask;

        Flag(int bit) {
            this.mask = 1 << bit;
        }

        public void set(boolean on) {
            if (on) {
                set();
            } else {
                clear();
            }
        }
        public void set() {
            psl |= mask;
        }
        public void clear() {
            psl &= ~mask;
        }
        public boolean get() {
            return (psl & mask) != 0;
        }
    }

    class User {
        public int u_error;
        public short u_uid;
        public short u_gid;
        public short u_ruid;
        public short u_rgid;
        public Path u_cdir;
        public FileItem u_ofile[] = new FileItem[NOFILE];
        public Proc u_procp;
        public u_r u_r = new u_r();
        public int[] u_signal = new int[NSIG];
        public short u_cmask = 02;

        User() {
            u_ofile[0] = FileItem.stdin;
            u_ofile[1] = FileItem.stdout;
            u_ofile[2] = FileItem.stderr;
            u_procp = Proc.newproc();
            Account acc = UserAccounts.getLoginAccount();
            if (acc != null) {
                u_uid = u_ruid = acc.uid;
                u_gid = u_rgid = acc.gid;
            }
            u_cdir = Paths.get(".");
        }

        User(User srcUser) {
            u_error = srcUser.u_error;
            u_uid = srcUser.u_uid;
            u_gid = srcUser.u_gid;
            u_ruid = srcUser.u_ruid;
            u_rgid = srcUser.u_rgid;
            u_cdir = srcUser.u_cdir;
            for (int i = 0; i < NOFILE; i++) {
                u_ofile[i] = srcUser.u_ofile[i];
                if (u_ofile[i] != null) {
                    u_ofile[i].addReference();
                }
            }
            u_procp = Proc.newproc(srcUser.u_procp);
            u_r = new u_r(srcUser.u_r);
            System.arraycopy(srcUser.u_signal, 0, u_signal, 0, u_signal.length);
            u_cmask = srcUser.u_cmask;
        }

        class u_r {
            public int r_val1;
            public int r_val2;

            u_r() {}
            u_r(u_r src) {
                r_val1 = src.r_val1;
                r_val2 = src.r_val2;
            }
        };

        public void exit() {
            for (int i = 0; i < NSIG; i++) {
                u_signal[i] = 1;
            }
            for (int i = 0; i < NOFILE; i++) {
                FileItem f = u.u_ofile[i];
                if(f != null) {
                    u_ofile[i] = null;
                    f.close();
                }
            }
        }

        public int fileOpen(String fname, int mode) {
            int fd = ufalloc();
            if (fd < 0) {
                u_error = EMFILE;
                return -1;
            }

            try {
                FileItem f = FileItem.open(fname, mode);
                u_ofile[fd] = f;
                return fd;
            } catch (FileItemException e) {
                u_error = e.error;
                return -1;
            }
        }

        public int fileCreate(String fname, int fmode) {
            int fd = ufalloc();
            if (fd < 0) {
                u_error = EMFILE;
                return -1;
            }

            try {
                FileItem f = FileItem.create(fname, fmode & ~u_cmask);
                u_ofile[fd] = f;
                return fd;

            } catch (FileItemException e) {
                u_error = e.error;
                return -1;
            }
        }

        public void fileClose(int fd) {
            FileItem f = getf(fd);
            if (f != null) {
                u_ofile[fd] = null;
                f.close();
            } else {
                u_error = EBADF;
            }
        }

        public int fileRead(int fd, int addr, int count) {
            FileItem f = getf(fd);
            if (f == null) {
                u_error = EBADF;
                return -1;
            }

            try {
                byte[] readBytes = f.read(count);
                memory.storeBytes(addr, readBytes, readBytes.length);
                return readBytes.length;
            } catch (FileItemException e) {
                u_error = e.error;
                return -1;
            }
        }

        public int fileWrite(int fd, int addr, int count) {
            FileItem f = getf(fd);
            if (f == null) {
                u_error = EBADF;
                return -1;
            }

            try {
                byte[] bytes = memory.loadBytes(addr, count);
                return f.write(bytes);
            } catch (FileItemException e) {
                u_error = e.error;
                return -1;
            }
        }

        public int fileSeek(int fd, int offset, int sbase) {
            FileItem f = getf(fd);
            if (f == null) {
                u_error = EBADF;
                return -1;
            }

            try {
                return f.seek(offset, sbase);
            } catch (FileItemException e) {
                u_error = e.error;
                return -1;
            }
        }

        public boolean isNormalFile(int fd) {
            FileItem f = getf(fd);
            if (f != null) {
                return f.isNormalFile();
            } else {
                u_error = EBADF;
                return false;
            }
        }

        public int fileDup(int fd1) {
            FileItem f1 = getf(fd1);
            if (f1 == null) {
                u_error = EBADF;
                return -1;
            }

            int fd2 = ufalloc();
            if (fd2 < 0) {
                u_error = EMFILE;
                return -1;
            }

            if (fd1 != fd2) {
                fileDuplicate(f1, fd2);
            }

            return fd2;
        }

        public void fileDup(int fd1, int fd2) {
            FileItem f1 = getf(fd1);
            if (f1 == null) {
                u_error = EBADF;
                return;
            }

            if (fd2 < 0 || fd2 >= NOFILE) {
                u_error = EBADF;
                return;
            }

            if (fd1 != fd2) {
                fileDuplicate(f1, fd2);
            }
        }

        private void fileDuplicate(FileItem f1, int fd2) {
            if (u_ofile[fd2] != null) {
                fileClose(fd2);
            }
            u_ofile[fd2] = f1;
            f1.addReference();
        }

        public int[] pipe() {
            FileItem[] pipes = FileItem.openPipe();

            int rfd = ufalloc();
            if (rfd < 0) {
                pipes[0].close();
                pipes[1].close();
                u_error = EMFILE;
                return null;
            }
            u_ofile[rfd] = pipes[0];

            int wfd = ufalloc();
            if (wfd < 0) {
                pipes[0].close();
                pipes[1].close();
                u_ofile[rfd] = null;
                u_error = EMFILE;
                return null;
            }
            u_ofile[wfd] = pipes[1];

            return new int[] {rfd, wfd};
        }

        public Path getFilePath(int fd) {
            FileItem f = getf(fd);
            if (f != null) {
                return f.getPath();
            }
            return null;
        }

        private int ufalloc() {
            for (int fd = 0; fd < NOFILE; fd++) {
                if (u_ofile[fd] == null) {
                    return fd;
                }
            }
            return -1;
        }

        private FileItem getf(int fd) {
            if (0 <= fd && fd < NOFILE) {
                return u_ofile[fd];
            }
            return null;
        }
    }
}
