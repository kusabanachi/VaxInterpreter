package vax_interpreter;

import java.util.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.charset.StandardCharsets;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static vax_interpreter.Util.*;
import static vax_interpreter.Kernel.Constant.*;

class Kernel {

    static Path rootdir;
    static {
        String classPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
        rootdir = new File(classPath, "root").toPath();
    }

    public static void syscall(int code, Context context) {
        int params = context.register[AP];
        params += NBPW;
        int syscallNum = code & 0x3f;
        Sysent sysent = Sysent.getSysent(syscallNum);

        if (sysent == Sysent.indir) {
            /* indirect */
            syscallNum = context.memory.loadInt(params, DataType.L).uint() & 0x3f;
            params += NBPW;
            sysent = Sysent.getSysent(syscallNum);
        }

        ArrayList<Integer> args = new ArrayList<>(sysent.narg);
        for (int i = 0; i < sysent.narg; i++) {
            args.add(context.memory.loadInt(params, DataType.L).sint());
            params += NBPW;
        }

        context.u.u_error = 0;
        context.u.u_r.r_val1 = 0;
        context.u.u_r.r_val2 = context.register[1];
 //   System.err.printf("--: %s%n", sysent);
        sysent.call(args, context);

        if (context.u.u_error == 0) {
            context.register[0] = context.u.u_r.r_val1;
            context.register[1] = context.u.u_r.r_val2;
        } else {
            context.register[0] = context.u.u_error;
            context.flagC.set();
        }
    }

    static class Constant {
        public static final int NBPW = 4;       /* number of bytes in an integer */
        public static final int NOFILE = 20;    /* max open files per contextess */
        public static final int NSIG = 17;
        public static final int NCARGS = 5120;  /* # characters in exec arglist */

        // Error
        public static final int EPERM = 1;
        public static final int ENOENT = 2;
        public static final int E2BIG = 7;
        public static final int ENOEXEC = 8;
        public static final int EBADF = 9;
        public static final int ECHILD = 10;
        public static final int EACCES = 13;
        public static final int EFAULT = 14;
        public static final int EBUSY = 16;
        public static final int EEXIST = 17;
        public static final int ENOTDIR = 20;
        public static final int EISDIR = 21;
        public static final int EINVAL = 22;
        public static final int ENFILE = 23;
        public static final int EMFILE = 24;
        public static final int ENOTTY = 25;
        public static final int ESPIPE = 29;
        public static final int EPIPE = 32;

        /* read, write, execute permissions */
        public static final int IREAD = 0400;
        public static final int IWRITE = 0200;
        public static final int IEXEC = 0100;

        /* File Flags */
        public static final int FREAD = 1;
        public static final int FWRITE = 2;

        /* inode modes */
        public static final int IFMT = 0xf000;    /* type of file */
        public static final int IFDIR = 0x4000;    /* directory */
        public static final int IFREG = 0x8000;    /* regular */

        /* proc stat codes */
        public static final int SRUN = 3;    /* running */
        public static final int SZOMB = 5;   /* intermediate state in process termination */
    }


    private static final short startPid = 30;
    private static final Set<Integer> pidFreeSet =
        new LinkedHashSet<>(Arrays.asList(31,32,33,34,35,36,37,38,39,40,
                                    41,42,43,44,45,46,47,48,49,50,
                                    51,52,53,54,55,56,57,58,59,60));

    private static final Set<Proc> procSet = new HashSet<>();
    static class Proc {
        public byte p_stat;
        public short p_pid;
        public short p_ppid;
        public short xp_xstat;

        static Proc newproc() {
            Proc newp = new Proc();
            newp.p_stat = SRUN;
            newp.p_pid = allocPid();
            newp.p_ppid = startPid;
            procSet.add(newp);
            return newp;
        }

        static Proc newproc(Proc parent) {
            Proc newp = new Proc();
            newp.p_stat = SRUN;
            newp.p_pid = allocPid();
            newp.p_ppid = parent.p_pid;
            procSet.add(newp);
            return newp;
        }

        private static short allocPid() {
            assert !pidFreeSet.isEmpty() : "no procs";

            Iterator<Integer> iter = pidFreeSet.iterator();
            int pid = iter.next();
            iter.remove();
            return (short)pid;
        }

        private void freePid() {
            pidFreeSet.add((int)p_pid);
        }
    }

    enum Sysent {
        indir (0, 0),
        exit (1, 1) {
            @Override public void call(List<Integer> args, Context context) {
                context.u.exit();

                Proc p = context.u.u_procp;
                p.p_stat = SZOMB;
                p.xp_xstat = (short)((args.get(0) & 0xff) << 8);

                /*for (Proc q : procSet) {
                    if (q.p_ppid == p.p_pid) {
                        q.p_ppid = 1;
                    }
                }*/

                for (Proc q : procSet) {
                    if (p.p_ppid == q.p_pid) {
                        Context.class.notifyAll();
                    }
                }
            }
        },
        fork (2, 0) {
            @Override public void call(List<Integer> args, Context context) {
                Process newProc = new Process(context);
                newProc.context.register[0] = context.u.u_procp.p_pid;
                newProc.context.register[1] = 1;
                new Thread(newProc).start();

                context.u.u_r.r_val1 = newProc.context.u.u_procp.p_pid;
                context.u.u_r.r_val2 = 0;
            }
        },
        read (3, 3) {
            @Override public void call(List<Integer> args, Context context) {
                int fd = args.get(0);
                int addr = args.get(1);
                int count = args.get(2);
                int readCount = context.u.fileRead(fd, addr, count);
                if (context.u.u_error == 0) {
                    context.u.u_r.r_val1 = readCount;
                }
            }
        },
        write (4, 3) {
            @Override public void call(List<Integer> args, Context context) {
                int fd = args.get(0);
                int addr = args.get(1);
                int count = args.get(2);
                int wroteCount = context.u.fileWrite(fd, addr, count);
                if (context.u.u_error == 0) {
                    context.u.u_r.r_val1 = wroteCount;
                }
            }
        },
        open (5, 2) {
            @Override public void call(List<Integer> args, Context context) {
                String fname = getFileName(args.get(0), context);
                int mode = args.get(1) + 1;
                int fd = context.u.fileOpen(fname, mode);
                if (context.u.u_error == 0) {
                    context.u.u_r.r_val1 = fd;
                }
            }
        },
        close (6, 1) {
            @Override public void call(List<Integer> args, Context context) {
                int fd = args.get(0);
                context.u.fileClose(fd);
            }
        },
        wait (7, 0) {
            @Override public void call(List<Integer> args, Context context) {
                Proc p = context.u.u_procp;
                boolean isFound = false;
                do {
                    Iterator iter = procSet.iterator();
                    while (iter.hasNext()) {
                        Proc p2 = (Proc)iter.next();
                        if (p2.p_ppid == p.p_pid) {
                            isFound = true;
                            if (p2.p_stat == SZOMB) {
                                context.u.u_r.r_val1 = p2.p_pid;
                                context.u.u_r.r_val2 = p2.xp_xstat;
                                p2.freePid();
                                procSet.remove(p2);
                                return;
                            }
                        }
                    }
                    if (isFound) {
                        try {
                            Context.class.wait();
                        } catch (InterruptedException e) {}
                    }
                } while (isFound);

                context.u.u_error = ECHILD;
            }
        },
        creat (8, 2) {
            @Override public void call(List<Integer> args, Context context) {
                String fname = getFileName(args.get(0), context, FileNameOption.NOCHANGE_BLANK);
                if (fname.isEmpty()) {
                    context.u.u_error = ENOENT;
                    return;
                }

                int fmode = args.get(1);
                int fd = context.u.fileCreate(fname, fmode);
                if (context.u.u_error == 0) {
                    context.u.u_r.r_val1 = fd;
                }
            }
        },
        link (9, 2) {
            @Override public void call(List<Integer> args, Context context) {
                Path target = Paths.get(getFileName(args.get(0), context));

                String linkname = getFileName(args.get(1), context, FileNameOption.NOCHANGE_BLANK);
                if (linkname.isEmpty()) {
                    context.u.u_error = ENOENT;
                    return;
                }
                Path link = Paths.get(linkname);

                try {
                    Files.createLink(link, target);
                } catch (NoSuchFileException x) {
                    context.u.u_error = ENOENT;
                } catch (FileAlreadyExistsException x) {
                    if ((link.endsWith(".") || link.endsWith("..")) &&
                            link.normalize().equals(target.normalize())) {
                        // link to current or parent directory is not error.
                        return;
                    }
                    context.u.u_error = EEXIST;
                } catch (IOException e) {
                    e.printStackTrace(System.err);
                }
            }
        },
        unlink (10, 1) {
            @Override public void call(List<Integer> args, Context context) {
                String fname = getFileName(args.get(0), context, FileNameOption.NOCHANGE_BLANK);
                File file = new File(fname);
                if (!file.exists() || fname.isEmpty()) {
                    context.u.u_error = ENOENT;
                    return;
                }
                if (!file.delete()) {
                    context.u.u_error = EBUSY;
                }
            }
        },
        exec (11, 2),
        chdir (12, 1) {
            @Override public void call(List<Integer> args, Context context) {
                Path dir = Paths.get(getFileName(args.get(0), context));
                if (!Files.exists(dir, NOFOLLOW_LINKS)) {
                    context.u.u_error = ENOENT;
                    return;
                }
                if (!Files.isDirectory(dir, NOFOLLOW_LINKS)) {
                    context.u.u_error = ENOTDIR;
                    return;
                }
                if (!Files.isExecutable(dir)) {
                    context.u.u_error = EACCES;
                    return;
                }

                context.u.u_cdir = dir;
            }
        },
        time (13, 0),
        mknod (14, 3) {
            @Override public void call(List<Integer> args, Context context) {
                String fname = getFileName(args.get(0), context, FileNameOption.NOCHANGE_BLANK);
                if (fname.isEmpty()) {
                    context.u.u_error = ENOENT;
                    return;
                }
                Path fpath = Paths.get(fname);
                if (Files.exists(fpath)) {
                    context.u.u_error = EEXIST;
                    return;
                }

                int fmode = args.get(1);
                if ((fmode & IFMT) == 0) {
                    try {
                        Files.createFile(fpath);
                    } catch (IOException e) {
                        context.u.u_error = ENOENT;
                        return;
                    }
                } else if ((fmode & IFMT) == IFDIR) {
                    try {
                        Files.createDirectory(fpath);
                    } catch (IOException e) {
                        context.u.u_error = ENOENT;
                        return;
                    }
                } else {
                    throw new RuntimeException("Device node is not supported.");
                }

                FileOperations.setFileMode(fpath, fmode & ~context.u.u_cmask);
            }
        },
        chmod (15, 2) {
            @Override public void call(List<Integer> args, Context context) {
                Path fpath = Paths.get(getFileName(args.get(0), context));
                if (!Files.exists(fpath)) {
                    context.u.u_error = ENOENT;
                    return;
                }

                int fmode = args.get(1);
                if (!FileOperations.setFileMode(fpath, fmode)) {
                    context.u.u_error = EPERM;
                }
            }
        },
        chown (16, 3) {
            @Override public void call(List<Integer> args, Context context) {
                if (context.u.u_uid != 0) {
                    context.u.u_error = EPERM;
                    return;
                }

                Path filePath = Paths.get(getFileName(args.get(0), context));
                if (!Files.exists(filePath)) {
                    context.u.u_error = ENOENT;
                    return;
                }

                int uid = args.get(1);
                int gid = args.get(2);
                if (!FileOperations.changeFileOwner(filePath, uid, gid)) {
                    context.u.u_error = EPERM;
                }
            }
        },
        sbreak (17, 1),
        stat (18, 2) {
            @Override public void call(List<Integer> args, Context context) {
                Path fpath = Paths.get(getFileName(args.get(0), context));
                if (!Files.exists(fpath, NOFOLLOW_LINKS)) {
                    context.u.u_error = ENOENT;
                    return;
                }

                byte[] fstatus = FileOperations.getFileStatus(fpath);
                if (fstatus == null) {
                    context.u.u_error = EFAULT;
                    return;
                }

                int addr = args.get(1);
                context.memory.storeBytes(addr, fstatus, fstatus.length);
            }
        },
        seek (19, 3) {
            @Override public void call(List<Integer> args, Context context) {
                int fd = args.get(0);
                int offset = args.get(1);
                int sbase = args.get(2);
                int newOff = context.u.fileSeek(fd, offset, sbase);
                if (context.u.u_error == 0) {
                    context.u.u_r.r_val1 = newOff;
                }
            }
        },
        getpid (20, 0) {
            @Override public void call(List<Integer> args, Context context) {
                context.u.u_r.r_val1 = context.u.u_procp.p_pid;
                context.u.u_r.r_val2 = context.u.u_procp.p_ppid;
            }
        },
        mount (21, 3),
        umount (22, 1),
        setuid (23, 1),
        getuid (24, 0) {
            @Override public void call(List<Integer> args, Context context) {
                context.u.u_r.r_val1 = context.u.u_ruid;
                context.u.u_r.r_val2 = context.u.u_uid;
            }
        },
        stime (25, 1),
        ptrace (26, 4),
        alarm (27, 1),
        fstat (28, 2) {
            @Override public void call(List<Integer> args, Context context) {
                int fd = args.get(0);
                Path fpath = context.u.getFilePath(fd);
                if (fpath == null) {
                    context.u.u_error = EBADF;
                    return;
                }

                byte[] fstatus = FileOperations.getFileStatus(fpath);
                if (fstatus == null) {
                    context.u.u_error = EFAULT;
                    return;
                }

                int addr = args.get(1);
                context.memory.storeBytes(addr, fstatus, fstatus.length);
            }
        },
        pause (29, 0),
        utime (30, 2),
        stty (31, 2),
        gtty (32, 2),
        access (33, 2) {
            @Override public void call(List<Integer> args, Context context) {
                File file = new File(getFileName(args.get(0), context));
                if (!file.exists()) {
                    context.u.u_error = ENOENT;
                    return;
                }

                int fmode = args.get(1);
                if ((fmode & IREAD >> 6) != 0) {
                    if (!file.canRead()) {
                        context.u.u_error = EACCES;
                    }
                }
                if ((fmode & IWRITE >> 6) != 0) {
                    if (!file.canWrite()) {
                        context.u.u_error = EACCES;
                    }
                }
                if ((fmode & IEXEC >> 6) != 0) {
                    if (!file.canExecute()) {
                        context.u.u_error = EACCES;
                    }
                }
            }
        },
        nice (34, 1),
        ftime (35, 1),
        sync (36, 0),
        kill (37, 2),
        dup (41, 2) {
            @Override public void call(List<Integer> args, Context context) {
                int fd1 = args.get(0);
                int fd2;
                int m = fd1 & ~0x3f;
                fd1 &= 0x3f;

                if ((m & 0x40) == 0) {
                    fd2 = context.u.fileDup(fd1);
                } else {
                    fd2 = args.get(1);
                    context.u.fileDup(fd1, fd2);
                }
                if (context.u.u_error == 0) {
                    context.u.u_r.r_val1 = fd2;
                }
            }
        },
        pipe (42, 0) {
            @Override public void call(List<Integer> args, Context context) {
                int[] fds = context.u.pipe();
                if (context.u.u_error == 0) {
                    context.u.u_r.r_val1 = fds[0];
                    context.u.u_r.r_val2 = fds[1];
                }
            }
        },
        times (43, 1),
        prof (44, 4),
        setgid (46, 1),
        getgid (47, 0) {
            @Override public void call(List<Integer> args, Context context) {
                context.u.u_r.r_val1 = context.u.u_rgid;
                context.u.u_r.r_val2 = context.u.u_gid;
            }
        },
        sig (48, 2) {
            @Override public void call(List<Integer> args, Context context) {
                int signo = args.get(0);
                if (signo <= 0 || signo >= NSIG || signo == 9) { // 9: SIGKIL
                    context.u.u_error = EINVAL;
                    return;
                }
                context.u.u_r.r_val1 = context.u.u_signal[signo];
                context.u.u_signal[signo] = args.get(1);
            }
        },
        sysacct (51, 1),
        sysphys (52, 3),
        syslock (53, 1),
        ioctl (54, 3) {
            @Override public void call(List<Integer> args, Context context) {
                int fd = args.get(0);
                boolean isFile = context.u.isNormalFile(fd);
                if (context.u.u_error == 0) {
                    if (isFile) {
                        context.u.u_error = ENOTTY;
                    }
                }
            }
        },
        mpxchan (56, 4),
        exece (59, 3) {
            @Override public void call(List<Integer> args, Context context) {
                String fname = getFileName(args.get(0), context);
                File file = new File(fname);
                if (!file.exists()) {
                    context.u.u_error = ENOENT;
                    return;
                }
                if (!file.canExecute() || !file.isFile()) {
                    context.u.u_error = EACCES;
                    return;
                }

                List<byte[]> argBuf = new ArrayList<>();
                List<byte[]> envBuf = new ArrayList<>();
                int nChars = 0;

                int argp = args.get(1);
                if (argp != 0) {
                    do {
                        int ap = context.memory.loadInt(argp, DataType.L).uint();
                        argp += NBPW;
                        if (ap == 0) {
                            break;
                        }

                        byte[] arg = context.memory.loadStringBytes(ap);
                        if (nChars + arg.length >= NCARGS - 1) {
                            context.u.u_error = E2BIG;
                            return;
                        }
                        argBuf.add(arg);
                        nChars += arg.length;
                    } while (true);

                    int envp = args.get(2);
                    do {
                        int ap = context.memory.loadInt(envp, DataType.L).uint();
                        envp += NBPW;
                        if (ap == 0) {
                            break;
                        }

                        byte[] env = context.memory.loadStringBytes(ap);
                        if (nChars + env.length >= NCARGS - 1) {
                            context.u.u_error = E2BIG;
                            return;
                        }
                        envBuf.add(env);
                        nChars += env.length;
                    } while (true);
                }

                boolean loadTextFileSucceeded;
                try {
                    loadTextFileSucceeded = context.memory.loadTextfile(fname);
                } catch (IOException e) {
                    loadTextFileSucceeded = false;
                }
                if (!loadTextFileSucceeded) {
                    context.u.u_error = ENOEXEC;
                    return;
                }


                nChars = nChars + (NBPW - 1) & ~(NBPW - 1);
                int ucp = MEM_SIZE - nChars - NBPW;
                int ap = ucp - (argBuf.size() + envBuf.size() + 3) * NBPW;
                context.register[SP] = ap;
                context.memory.store(ap, new IntData(argBuf.size()));
                ap += NBPW;
                for (byte[] arg: argBuf) {
                    context.memory.store(ap, new IntData(ucp));
                    ap += NBPW;
                    context.memory.storeBytes(ucp, arg, arg.length);
                    ucp += arg.length;
                }
                context.memory.store(ap, new IntData(0));
                ap += NBPW;
                for (byte[] env: envBuf) {
                    context.memory.store(ap, new IntData(ucp));
                    ap += NBPW;
                    context.memory.storeBytes(ucp, env, env.length);
                    ucp += env.length;
                }
                context.memory.store(ap, new IntData(0));
                context.memory.store(ucp, new IntData(0));

                // setregs
                for (int i = 0; i < NSIG; i++) {
                    if ((context.u.u_signal[i] & 1) == 0) {
                        context.u.u_signal[i] = 0;
                    }
                }
                context.register[PC] = 2; /* skip over entry mask */
            }
        },
        umask (60, 1),
        chroot (61, 1);

        public final int number;
        public final int narg;

        private Sysent(int number, int narg) {
            this.number = number;
            this.narg = narg;
        }

        private final static HashMap<Integer, Sysent> sysentMap;
        static {
            sysentMap = new HashMap<>(Sysent.values().length);
            for (Sysent entry : Sysent.values()) {
                sysentMap.put(entry.number, entry);
            }
        }

        public static Sysent getSysent(int num) {
            Sysent entry = sysentMap.get(num);
            if (entry != null) {
                return entry;
            } else {
                return indir;
            }
        }

        public void call(List<Integer> args, Context context) {}

        private enum FileNameOption {
            NOCHANGE_BLANK;
        }
        private static String getFileName(int addr, Context context, FileNameOption... option) {
            byte[] strBytes = context.memory.loadStringBytes(addr);
            String fname = new String(strBytes, 0, strBytes.length - 1, StandardCharsets.US_ASCII);
            if (fname.startsWith("/")) {
                fname = rootdir.resolve(fname.substring(1)).toString();
            } else if (!fname.isEmpty() || !Arrays.asList(option).contains(FileNameOption.NOCHANGE_BLANK)) {
                fname = context.u.u_cdir.resolve(fname).toString();
            }

            return fname;
        }
    }
}
