package vax_interpreter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static vax_interpreter.Util.*;

abstract class Operand {
    private static final int PC = 0xf;
    protected String mnemonic;
    protected int len;

    protected final Context context;
    protected final DataType dataType;

    abstract public IntData getValue();
    abstract public void setValue(IntData val);

    protected Operand(Context context, DataType dataType) {
        this.context = context;
        this.dataType = dataType;
    }

    public static Operand fetch(Context context, DataType type) {
        if (type != DataType.BrB && type != DataType.BrW) {
            return fetchGeneralAddress(context, type);
        } else {
            return new BranchAddress(context, type);
        }
    }

    public static Operand fetchGeneralAddress(Context context, DataType type) {
        int head = context.lookAhead();
        if (head == -1) {
            return null;
        }

        Operand opr = null;
        switch (head >>> 4) {
            case 0x0: case 0x1: case 0x2: case 0x3:
                opr = new Literal(context, type);
                break;
            case 0x4:
                opr = new Index(context, type);
                break;
            case 0x5:
                opr = new Register(context, type);
                break;
            case 0x6:
                opr = new RegisterDeferred(context, type);
                break;
            case 0x7:
                opr = new AutoDecrement(context, type);
                break;
            case 0x8:
                opr = new AutoIncrement(context, type);
                break;
            case 0x9:
                opr = new AutoIncrementDeferred(context, type);
                break;
            case 0xA: case 0xC: case 0xE:
                opr = new Displacement(context, type);
                break;
            case 0xB: case 0xD: case 0xF:
                opr = new DisplacementDeferred(context, type);
                break;
        }
        return opr;
    }

    public String mnemonic() {
        return mnemonic;
    }

    public int len() {
        return len;
    }

    protected String regStr(int val) {
        final String [] regs = {"r0", "r1", "r2", "r3", "r4", "r5", "r6", "r7",
                                "r8", "r9", "r10", "r11", "ap", "fp", "sp", "pc"};
        return regs[val & 0xf];
    }

    protected boolean isPC(int val) {
        return (val & 0xf) == PC;
    }
}

abstract class Address extends Operand {
    protected int addr;

    protected Address(Context context, DataType dataType) {
        super(context, dataType);
    }

    @Override
    public IntData getValue() {
        return context.memory.load(addr, dataType);
    }

    @Override
    public void setValue(IntData val) {
        context.memory.store(addr, val);
    }

    public int getAddress() {
        return addr;
    }
}


class BranchAddress extends Address {
    protected BranchAddress(Context context, DataType dataType) {
        super(context, dataType);
        IntData offset = context.memory.load(context.register[PC], dataType);
        context.register[PC] += dataType.size;
        this.addr = context.pc() + offset.sint();
        mnemonic = String.format("0x%x", addr);
        len = dataType.size;
    }
}

class Literal extends Address {
    protected Literal(Context context, DataType dataType) {
        super(context, dataType);
        this.addr = context.pc();
        int val = context.readText();
        mnemonic = String.format("$0x%x", val & 0x3f) + dataType.annotation;
        len = 1;
    }

    @Override
    public IntData getValue() {
        ByteBuffer bbuf = ByteBuffer.allocate(dataType.size).order(ByteOrder.LITTLE_ENDIAN);
        int val = context.memory.load(addr, DataType.B).uint();
        switch (dataType) {
        default:
        case B: case W: case L: case Q: case O:
            return new IntData(val, dataType);
        case F:
            return new IntData(bbuf.putInt(val << 4 | 0x4000).array(),
                               dataType);
        case D:
            return new IntData(bbuf.putInt(val << 4 | 0x4000).putInt(0).array(),
                               dataType);
        case G:
            return new IntData(bbuf.putInt(val << 1 | 0x4000).putInt(0).array(),
                               dataType);
        case H:
            return new IntData(bbuf.putInt(val >> 3 | 0x4000 | val << 29)
                                   .putInt(0).putInt(0).putInt(0).array(),
                               dataType);
        }
    }
}

class Index extends Address {
    protected Index(Context context, DataType dataType) {
        super(context, dataType);
        int regNum = context.readText() & 0xf;
        Address base = (Address)fetchGeneralAddress(context, dataType);
        this.addr = context.register[regNum] * dataType.size + base.addr;
        mnemonic = base.mnemonic() + "[" + regStr(regNum) + "]";
        len = base.len() + 1;
    }
}

class Register extends Operand {
    public final int regNum;

    protected Register(Context context, DataType dataType) {
        super(context, dataType);
        this.regNum = context.readText() & 0xf;
        mnemonic = regStr(regNum);
        len = 1;
    }

    @Override
    public IntData getValue() {
        return context.getRegisterValue(regNum, dataType);
    }

    @Override
    public void setValue(IntData val) {
        context.setRegisterValue(regNum, val);
    }
}

class RegisterDeferred extends Address {
    protected RegisterDeferred(Context context, DataType dataType) {
        super(context, dataType);
        int regNum = context.readText() & 0xf;
        this.addr = context.register[regNum];
        mnemonic = "(" + regStr(regNum) + ")";
        len = 1;
    }
}

class AutoDecrement extends Address {
    protected AutoDecrement(Context context, DataType dataType) {
        super(context, dataType);
        int regNum = context.readText() & 0xf;
        context.register[regNum] -= dataType.size;
        this.addr = context.register[regNum];
        mnemonic = "-(" + regStr(regNum) + ")";
        len = 1;
    }
}

class AutoIncrement extends Address {
    protected AutoIncrement(Context context, DataType dataType) {
        super(context, dataType);
        int regNum = context.readText() & 0xf;
        this.addr = context.register[regNum];
        context.register[regNum] += dataType.size;
        if (isPC(regNum)) {
            IntData imm = context.memory.load(addr, dataType);
            mnemonic = "$" + imm.hexString() + dataType.annotation;
            len = 1 + dataType.size;
        } else {
            mnemonic = "(" + regStr(regNum) + ")+";
            len = 1;
        }
    }
}

class AutoIncrementDeferred extends Address {
    protected AutoIncrementDeferred(Context context, DataType dataType) {
        super(context, dataType);
        int regNum = context.readText() & 0xf;
        this.addr = context.memory.load(context.register[regNum], DataType.L).uint();
        context.register[regNum] += 4;
        if (isPC(regNum)) {
            mnemonic = String.format("*0x%x", addr);
            len = 5;
        } else {
            mnemonic = "@(" + regStr(regNum) + ")+";
            len = 1;
        }
    }
}

class Displacement extends Address {
    protected Displacement(Context context, DataType dataType) {
        super(context, dataType);
        int head = context.readText();
        int size = 1 << ((head >>> 5) - 5);  // 1, 2, 4 bytes
        DataType dispType = size == 1 ? DataType.B :
                            size == 2 ? DataType.W :
                            /*      4*/ DataType.L;
        int disp = context.memory.load(context.register[PC], dispType).sint();
        context.register[PC] += size;
        int regNum = head & 0xf;
        this.addr = disp + context.register[regNum];
        if (isPC(regNum)) {
            mnemonic = String.format("0x%x", addr);
        } else {
            mnemonic = String.format("0x%x(%s)", disp, regStr(regNum));
        }
        len = 1 + size;
    }
}

class DisplacementDeferred extends Address {
    protected DisplacementDeferred(Context context, DataType dataType) {
        super(context, dataType);
        Address displacement = new Displacement(context, dataType);
        this.addr = context.memory.load(displacement.addr, DataType.L).uint();
        mnemonic = "*" + displacement.mnemonic;
        len = displacement.len();
    }
}


