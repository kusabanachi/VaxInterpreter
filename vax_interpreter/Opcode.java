package vax_interpreter;

import java.util.*;
import static vax_interpreter.Util.*;
import static vax_interpreter.DataType.*;

import static vax_interpreter.NopExec.*;
import static vax_interpreter.MovExec.*;
import static vax_interpreter.MovzExec.*;
import static vax_interpreter.PushExec.*;
import static vax_interpreter.MovaExec.*;
import static vax_interpreter.PushaExec.*;
import static vax_interpreter.McomExec.*;
import static vax_interpreter.MnegExec.*;
import static vax_interpreter.FmnegExec.*;
import static vax_interpreter.AddExec.*;
import static vax_interpreter.SubExec.*;
import static vax_interpreter.MulExec.*;
import static vax_interpreter.DivExec.*;
import static vax_interpreter.BitExec.*;
import static vax_interpreter.BisExec.*;
import static vax_interpreter.BicExec.*;
import static vax_interpreter.XorExec.*;
import static vax_interpreter.ClrExec.*;
import static vax_interpreter.IncExec.*;
import static vax_interpreter.DecExec.*;
import static vax_interpreter.AshExec.*;
import static vax_interpreter.TstExec.*;
import static vax_interpreter.CmpExec.*;
import static vax_interpreter.ExtExec.*;
import static vax_interpreter.InsvExec.*;
import static vax_interpreter.JmpExec.*;
import static vax_interpreter.BrExec.*;
import static vax_interpreter.BbExec.*;
import static vax_interpreter.BlbExec.*;
import static vax_interpreter.CallExec.*;
import static vax_interpreter.RetExec.*;
import static vax_interpreter.ChmkExec.*;
import static vax_interpreter.CaseExec.*;
import static vax_interpreter.AobExec.*;
import static vax_interpreter.SobExec.*;
import static vax_interpreter.CvtExec.*;
import static vax_interpreter.CvtlpExec.*;
import static vax_interpreter.AcbExec.*;
import static vax_interpreter.MovcExec.*;
import static vax_interpreter.CmpcExec.*;
import static vax_interpreter.LoccExec.*;
import static vax_interpreter.MovpExec.*;
import static vax_interpreter.EditpcExec.*;


enum DataType {
    B(1), W(2), L(4), Q(8), O(16),
    F(4, " [f-float]"), D(8, " [d-float]"), G(8, " [g-float]"), H(16, " [h-float]"),
    BrB(1), BrW(2);

    public final int size;
    public final String annotation;

    private DataType(int sz) {
        this(sz, "");
    }
    private DataType(int sz, String ann) {
        this.size = sz;
        this.annotation = ann;
    }
    public boolean isFloatDataType() {
        int enumOrd = ordinal();
        return enumOrd == F.ordinal() ||
               enumOrd == D.ordinal() ||
               enumOrd == G.ordinal() ||
               enumOrd == H.ordinal();
    }
}

class Opcode {
    private final static HashMap<Integer, VaxInstruction> instructionMap;
    static {
        VaxInstruction[] instructions = VaxInstruction.values();
        instructionMap = new HashMap<>();
        for (VaxInstruction instruction : instructions) {
            instructionMap.put(instruction.bin, instruction);
        }
    }

    private final VaxInstruction instruction;
    protected Opcode(VaxInstruction instruction) {
        this.instruction = instruction;
    }

    public static Opcode fetch(Context context) {
        int bin = 0;
        for (int i = 0; i < 2; i++) {
            int val = context.readText();
            if (val == -1) {
                return null;
            }
            bin += val << i * 8;
            VaxInstruction instruction = instructionMap.get(bin);
            if (instruction != null) {
                return new Opcode(instruction);
            }
        }
        return new Nullcode(bin);
    }

    public DataType[] operands() {
        return instruction.operands;
    }

    public String mnemonic() {
        return instruction.mnemonic;
    }

    public int len() {
        return instruction.bin <= 0xff ? 1 : 2;
    }

    public void execute(List<Operand> oprs, Context c) {
        instruction.execute(oprs, c);
    }
}


enum VaxInstruction {
    NOP (0x1, NopExec),

    MOVB (0x90, MovExec, B,B),   MOVW (0xb0, MovExec, W,W),
    MOVL (0xd0, MovExec, L,L),   MOVQ (0x7d, MovExec, Q,Q),
    MOVO (0x7dfd, MovExec, O,O),
    MOVF (0x50, MovExec, F,F),   MOVD (0x70, MovExec, D,D),
    MOVG (0x50fd, MovExec, G,G), MOVH (0x70fd, MovExec, H,H),

    MOVZBW (0x9b, MovzExec, B,W), MOVZBL (0x9a, MovzExec, B,L),
    MOVZWL (0x3c, MovzExec, W,L),

    PUSHL (0xdd, PushExec, L),

    MOVAB (0x9e, MovaExec, B,L), MOVAW (0x3e, MovaExec, W,L),
    MOVAL (0xde, MovaExec, L,L), MOVAQ (0x7e, MovaExec, Q,L),
    MOVAO (0x7efd, MovaExec, O,L),

    PUSHAB (0x9f, PushaExec, B), PUSHAW (0x3f, PushaExec, W),
    PUSHAL (0xdf, PushaExec, L),
    PUSHAQ (0x7f, PushaExec, Q), PUSHAO (0x7ffd, PushaExec, O),

    MCOMB (0x92, McomExec, B,B), MCOMW (0xb2, McomExec, W,W),
    MCOML (0xd2, McomExec, L,L),

    MNEGB (0x8e, MnegExec, B,B), MNEGW (0xae, MnegExec, W,W),
    MNEGL (0xce, MnegExec, L,L),

    MNEGF (0x52, FmnegExec, F,F),   MNEGD (0x72, FmnegExec, D,D),
    MNEGG (0x52fd, FmnegExec, G,G), MNEGH (0x72fd, FmnegExec, H,H),

    ADDB2 (0x80, AddExec, B,B), ADDB3 (0x81, AddExec, B,B,B),
    ADDW2 (0xa0, AddExec, W,W), ADDW3 (0xa1, AddExec, W,W,W),
    ADDL2 (0xc0, AddExec, L,L), ADDL3 (0xc1, AddExec, L,L,L),

    /*
    ADDF2 (0x40, FaddExec, F,F),   ADDF3 (0x41, FaddExec, F,F,F),
    ADDD2 (0x60, FaddExec, D,D),   ADDD3 (0x61, FaddExec, D,D,D),
    ADDG2 (0x40fd, FaddExec, G,G), ADDG3 (0x41fd, FaddExec, G,G,G),
    ADDH2 (0x60fd, FaddExec, H,H), ADDH3 (0x61fd, FaddExec, H,H,H),
    */

    SUBB2 (0x82, SubExec, B,B), SUBB3 (0x83, SubExec, B,B,B),
    SUBW2 (0xa2, SubExec, W,W), SUBW3 (0xa3, SubExec, W,W,W),
    SUBL2 (0xc2, SubExec, L,L), SUBL3 (0xc3, SubExec, L,L,L),

    /*
    SUBF2 (0x42, F,F),   SUBF3 (0x43, F,F,F),
    SUBD2 (0x62, D,D),   SUBD3 (0x63, D,D,D),
    SUBG2 (0x42fd, G,G), SUBG3 (0x43fd, G,G,G),
    SUBH2 (0x62fd, H,H), SUBH3 (0x63fd, H,H,H),
    */

    MULB2 (0x84, MulExec, B,B), MULB3 (0x85, MulExec, B,B,B),
    MULW2 (0xa4, MulExec, W,W), MULW3 (0xa5, MulExec, W,W,W),
    MULL2 (0xc4, MulExec, L,L), MULL3 (0xc5, MulExec, L,L,L),

    DIVB2 (0x86, DivExec, B,B), DIVB3 (0x87, DivExec, B,B,B),
    DIVW2 (0xa6, DivExec, W,W), DIVW3 (0xa7, DivExec, W,W,W),
    DIVL2 (0xc6, DivExec, L,L), DIVL3 (0xc7, DivExec, L,L,L),

    BITB (0x93, BitExec, B,B), BITW (0xb3, BitExec, W,W),
    BITL (0xd3, BitExec, L,L),

    BISB2 (0x88, BisExec, B,B), BISB3 (0x89, BisExec, B,B,B),
    BISW2 (0xa8, BisExec, W,W), BISW3 (0xa9, BisExec, W,W,W),
    BISL2 (0xc8, BisExec, L,L), BISL3 (0xc9, BisExec, L,L,L),

    BICB2 (0x8a, BicExec, B,B), BICB3 (0x8b, BicExec, B,B,B),
    BICW2 (0xaa, BicExec, W,W), BICW3 (0xab, BicExec, W,W,W),
    BICL2 (0xca, BicExec, L,L), BICL3 (0xcb, BicExec, L,L,L),

    XORB2 (0x8c, XorExec, B,B), XORB3 (0x8d, XorExec, B,B,B),
    XORW2 (0xac, XorExec, W,W), XORW3 (0xad, XorExec, W,W,W),
    XORL2 (0xcc, XorExec, L,L), XORL3 (0xcd, XorExec, L,L,L),

    CLRB (0x94, ClrExec, B), CLRW (0xb4, ClrExec, W),
    CLRL (0xd4, ClrExec, L),
    CLRQ (0x7c, ClrExec, Q), CLRO (0x7cfd, ClrExec, O),

    INCB (0x96, IncExec, B), INCW (0xb6, IncExec, W),
    INCL (0xd6, IncExec, L),

    DECB (0x97, DecExec, B), DECW (0xb7, DecExec, W),
    DECL (0xd7, DecExec, L),

    ASHL (0x78, AshlExec, B,L,L), ASHQ (0x79, AshqExec, B,Q,Q),

    TSTB (0x95, TstExec, B),   TSTW (0xb5, TstExec, W),
    TSTL (0xd5, TstExec, L),
    TSTF (0x53, TstExec, F),   TSTD (0x73, TstExec, D),
    TSTG (0x53fd, TstExec, G), TSTH (0x73fd, TstExec, H),

    CMPB (0x91, CmpExec, B,B), CMPW (0xb1, CmpExec, W,W),
    CMPL (0xd1, CmpExec, L,L),

    /*
    CMPF (0x51, F,F),   CMPD (0x71, D,D),
    CMPG (0x51fd, G,G), CMPH (0x71fd, H,H),
    */

    EXTV (0xee, ExtvExec, L,B,B,L),
    EXTZV (0xef, ExtzvExec, L,B,B,L),

    INSV (0xf0, InsvExec, L,L,B,B),

    JMP (0x17, JmpExec, B),
    BRB (0x11, BrExec, BrB),      BRW (0x31, BrExec, BrW),
    BNEQ (0x12, BneqExec, BrB),   BEQL (0x13, BeqlExec, BrB),
    BGTR (0x14, BgtrExec, BrB),   BLEQ (0x15, BleqExec, BrB),
    BGEQ (0x18, BgeqExec, BrB),   BLSS (0x19, BlssExec, BrB),
    BGTRU (0x1a, BgtruExec, BrB), BLEQU (0x1b, BlequExec, BrB),
    BVC (0x1c, BvcExec, BrB),     BVS (0x1d, BvsExec, BrB),
    BCC (0x1e, BccExec, BrB),     BLSSU (0x1f, BlssuExec, BrB),

    BBS (0xe0, BbsExec, L,B,BrB),    BBC (0xe1, BbcExec, L,B,BrB),
    BBSS (0xe2, BbssExec, L,B,BrB),  BBCS (0xe3, BbcsExec, L,B,BrB),
    BBSC (0xe4, BbscExec, L,B,BrB),  BBCC  (0xe5, BbccExec, L,B,BrB),
    BBSSI (0xe6, BbssExec, L,B,BrB), BBCCI (0xe7, BbccExec, L,B,BrB),

    BLBS (0xe8, BlbsExec, L,BrB), BLBC (0xe9, BlbcExec, L,BrB),

    CALLG (0xfa, CallgExec, B,B), CALLS (0xfb, CallsExec, L,B),

    RET (0x4, RetExec),

    CHMK (0xbc, ChmkExec, W),

    CASEB (0x8f, CaseExec, B,B,B), CASEW (0xaf, CaseExec, W,W,W),
    CASEL (0xcf, CaseExec, L,L,L),

    AOBLSS(0xf2, AoblssExec, L,L,BrB),
    AOBLEQ(0xf3, AobleqExec, L,L,BrB),

    SOBGEQ (0xf4, SobgeqExec, L,BrB),
    SOBGTR(0xf5, SobgtrExec, L,BrB),

    CVTBW (0x99, CvtExec, B,W), CVTBL (0x98, CvtExec, B,L),
    CVTWB (0x33, CvtExec, W,B), CVTWL (0x32, CvtExec, W,L),
    CVTLB (0xf6, CvtExec, L,B), CVTLW (0xf7, CvtExec, L,W),

    /*
    CVTBF (0x4c, B,F),   CVTBD (0x6c, B,D),
    CVTBG (0x4cfd, B,G), CVTBH (0x6cfd, B,H),
    CVTWF (0x4d, W,F),   CVTWD (0x6d, W,D),
    CVTWG (0x4dfd, W,G), CVTWH (0x6dfd, W,H),
    CVTLF (0x4e, L,F),   CVTLD (0x6e, L,D),
    CVTLG (0x4efd, L,G), CVTLH (0x6efd, L,H),
    CVTFB (0x48, F,B),   CVTDB (0x68, D,B),
    CVTGB (0x48fd, G,B), CVTHB (0x68fd, H,B),
    CVTFW (0x49, F,W),   CVTDW (0x69, D,W),
    CVTGW (0x49fd, G,W), CVTHW (0x69fd, H,W),
    CVTFL (0x4a, F,L),   CVTRFL(0x4b, F,L),
    CVTDL (0x6a, D,L),   CVTRDL(0x6b, D,L),
    CVTGL (0x4afd, G,L), CVTRGL(0x4bfd, G,L),
    CVTHL (0x6afd, H,L), CVTRHL(0x6bfd, H,L),
    CVTFD (0x56, F,D),   CVTFG (0x99fd, F,G),
    CVTFH (0x98fd, F,H), CVTDF (0x76, D,F),
    CVTDH (0x32fd, D,H), CVTGF (0x33fd, G,F),
    CVTGH (0x56fd, G,H), CVTHF (0xf6fd, H,F),
    CVTHD (0xf7fd, H,D), CVTHG (0x76fd, H,G),
    */

    CVTLP (0xf9, CvtlpExec, L,W,B),

    ACBB (0x9d, AcbExec, B,B,B,BrW), ACBW (0x3d, AcbExec, W,W,W,BrW),
    ACBL (0xf1, AcbExec, L,L,L,BrW),

    MOVC3 (0x28, MovcExec, W,B,B), MOVC5 (0x2c, MovcExec, W,B,B,W,B),

    CMPC3 (0x29, CmpcExec, W,B,B), CMPC5 (0x2d, CmpcExec, W,B,B,W,B),

    LOCC (0x3a, LoccExec, B,W,B), SKPC (0x3b, SkpcExec, B,W,B),

    MOVP (0x34, MovpExec, W,B,B),

    EDITPC (0x38, EditpcExec, W,B,B,B);

    public final int bin;
    private final CodeExec strategy;
    public final DataType[] operands;
    public final String mnemonic;

    private VaxInstruction(int bin, CodeExec strategy, DataType... oprs) {
        this.bin = bin;
        this.strategy = strategy;
        this.operands = oprs;
        this.mnemonic = name().toLowerCase(Locale.ENGLISH);
    }

    public void execute(List<Operand> oprs, Context context) {
        strategy.execute(oprs, context);
    }
}


interface CodeExec {
    public void execute(List<Operand> oprs, Context context);
}

enum NopExec implements CodeExec {
    NopExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {}
}

enum MovExec implements CodeExec {
    MovExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData srcVal = oprs.get(0).getIntValue();
        Operand dest = oprs.get(1);
        dest.setValue(srcVal);
        context.flagN.set( srcVal.isNegValue() );
        context.flagZ.set( srcVal.isZeroValue() );
        context.flagV.clear();
    }
}

enum MovzExec implements CodeExec {
    MovzExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData srcVal = oprs.get(0).getIntValue();
        Operand dest = oprs.get(1);
        IntData setVal = new IntData(srcVal.uint(), dest.dataType);
        dest.setValue(setVal);
        context.flagN.clear();
        context.flagZ.set( setVal.isZeroValue() );
        context.flagV.clear();
        context.flagC.clear();
    }
}

enum PushExec implements CodeExec {
    PushExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData srcVal = oprs.get(0).getIntValue();
        context.push(srcVal);
        context.flagN.set( srcVal.isNegValue() );
        context.flagZ.set( srcVal.isZeroValue() );
        context.flagV.clear();
    }
}

enum MovaExec implements CodeExec {
    MovaExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData addr = new IntData( ((Address)oprs.get(0)).getAddress() );
        Operand dest = oprs.get(1);
        dest.setValue(addr);
        context.flagN.set( addr.isNegValue() );
        context.flagZ.set( addr.isZeroValue() );
        context.flagV.clear();
    }
}

enum PushaExec implements CodeExec {
    PushaExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData addr = new IntData( ((Address)oprs.get(0)).getAddress() );
        context.push(addr);
        context.flagN.set( addr.isNegValue() );
        context.flagZ.set( addr.isZeroValue() );
        context.flagV.clear();
    }
}

enum McomExec implements CodeExec {
    McomExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData srcVal = oprs.get(0).getIntValue();
        Operand dest = oprs.get(1);
        IntData com = IntData.bitInvert(srcVal);
        dest.setValue(com);
        context.flagN.set( com.isNegValue() );
        context.flagZ.set( com.isZeroValue() );
        context.flagV.clear();
        context.flagC.clear();
    }
}

enum MnegExec implements CodeExec {
    MnegExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData srcVal = oprs.get(0).getIntValue();
        Operand dest = oprs.get(1);
        IntData neg = Calculator.sub(new IntData(0, srcVal.dataType()), srcVal, context);
        dest.setValue(neg);
    }
}

enum FmnegExec implements CodeExec {
    FmnegExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        FloatData srcVal = oprs.get(0).getFloatValue();

        assert !srcVal.isMinusZeroFloatValue() : "Reserved operand fault";

        FloatData neg;
        if (srcVal.isZeroValue()) {
            neg = srcVal;
        } else {
            neg = FloatData.negativeFloat(srcVal);
        }
        Operand dest = oprs.get(1);
        dest.setValue(neg);
        context.flagN.set( neg.isNegValue() );
        context.flagZ.set( neg.isZeroValue() );
        context.flagV.clear();
        context.flagC.clear();
    }
}

enum AddExec implements CodeExec {
    AddExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData arg1 = oprs.get(1).getIntValue();
        IntData arg2 = oprs.get(0).getIntValue();
        Operand dest = oprs.size() == 3 ? oprs.get(2) : oprs.get(1);
        IntData sum = Calculator.add(arg1, arg2, context);
        dest.setValue(sum);
    }
}

enum SubExec implements CodeExec {
    SubExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData arg1 = oprs.get(1).getIntValue();
        IntData arg2 = oprs.get(0).getIntValue();
        Operand dest = oprs.size() == 3 ? oprs.get(2) : oprs.get(1);
        IntData diff = Calculator.sub(arg1, arg2, context);
        dest.setValue(diff);
    }
}

enum MulExec implements CodeExec {
    MulExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData arg1 = oprs.get(0).getIntValue();
        IntData arg2 = oprs.get(1).getIntValue();
        Operand dest = oprs.size() == 3 ? oprs.get(2) : oprs.get(1);
        IntData prod = Calculator.mul(arg1, arg2, context);
        dest.setValue(prod);
    }
}

enum DivExec implements CodeExec {
    DivExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData divisor = oprs.get(0).getIntValue();
        IntData dividend = oprs.get(1).getIntValue();
        Operand dest = oprs.size() == 3 ? oprs.get(2) : oprs.get(1);
        IntData quo = Calculator.div(dividend, divisor, context);

        if (quo != null) {
            dest.setValue(quo);
        } else {
            if (oprs.size() == 3) {
                dest.setValue(dividend);
            }
            context.flagN.set( dest.getIntValue().isNegValue() );
            context.flagZ.set( dest.getIntValue().isZeroValue() );
            context.flagC.clear();
        }
    }
}

enum BitExec implements CodeExec {
    BitExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData arg1 = oprs.get(1).getIntValue();
        IntData arg2 = oprs.get(0).getIntValue();
        IntData testVal = new IntData(arg1.uint() & arg2.uint(), arg1.dataType());
        context.flagN.set( testVal.isNegValue() );
        context.flagZ.set( testVal.isZeroValue() );
        context.flagV.clear();
    }
}

enum BisExec implements CodeExec {
    BisExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData arg1 = oprs.get(1).getIntValue();
        IntData arg2 = oprs.get(0).getIntValue();
        IntData bisVal = new IntData(arg1.uint() | arg2.uint(), arg1.dataType());
        Operand dest = oprs.size() == 3 ? oprs.get(2) : oprs.get(1);
        dest.setValue(bisVal);
        context.flagN.set( bisVal.isNegValue() );
        context.flagZ.set( bisVal.isZeroValue() );
        context.flagV.clear();
    }
}

enum BicExec implements CodeExec {
    BicExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData arg1 = oprs.get(1).getIntValue();
        IntData arg2 = oprs.get(0).getIntValue();
        IntData bicVal = new IntData(arg1.uint() & ~arg2.uint(), arg1.dataType());
        Operand dest = oprs.size() == 3 ? oprs.get(2) : oprs.get(1);
        dest.setValue(bicVal);
        context.flagN.set( bicVal.isNegValue() );
        context.flagZ.set( bicVal.isZeroValue() );
        context.flagV.clear();
    }
}

enum XorExec implements CodeExec {
    XorExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData arg1 = oprs.get(1).getIntValue();
        IntData arg2 = oprs.get(0).getIntValue();
        IntData xorVal = new IntData(arg1.uint() ^  arg2.uint(), arg1.dataType());
        Operand dest = oprs.size() == 3 ? oprs.get(2) : oprs.get(1);
        dest.setValue(xorVal);
        context.flagN.set( xorVal.isNegValue() );
        context.flagZ.set( xorVal.isZeroValue() );
        context.flagV.clear();
    }
}

enum ClrExec implements CodeExec {
    ClrExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        Operand dest = oprs.get(0);
        IntData zero = new IntData(0, dest.dataType);
        dest.setValue(zero);
        context.flagN.clear();
        context.flagZ.set();
        context.flagV.clear();
    }
}

enum IncExec implements CodeExec {
    IncExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData arg = oprs.get(0).getIntValue();
        Operand dest = oprs.get(0);
        IntData sum = Calculator.add(arg, new IntData(1, arg.dataType()), context);
        dest.setValue(sum);
    }
}

enum DecExec implements CodeExec {
    DecExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData arg = oprs.get(0).getIntValue();
        Operand dest = oprs.get(0);
        IntData diff = Calculator.sub(arg, new IntData(1, arg.dataType()), context);
        dest.setValue(diff);
    }
}

enum AshExec implements CodeExec {
    AshlExec {
        @Override
        protected int maxCount() {
                return 31;
        }
        @Override
        protected int minCount() {
                return -31;
        }
    },
    AshqExec {
        @Override
        protected int maxCount() {
                return 63;
        }
        @Override
        protected int minCount() {
                return -63;
        }
    };

    @Override
    public void execute(List<Operand> oprs, Context context) {
        int count = oprs.get(0).getIntValue().sint();
        IntData srcVal = oprs.get(1).getIntValue();
        long src = srcVal.slong();
        Operand dest = oprs.get(2);

        long val;
        if (count >= 0) {
            val = count > maxCount() ? 0 : src << count;
        } else {
            int rcount = count < minCount() ? -minCount() : -count;
            val = src >> rcount;
        }
        IntData shifted = new IntData(val, srcVal.dataType());

        dest.setValue(shifted);
        context.flagN.set( shifted.isNegValue() );
        context.flagZ.set( shifted.isZeroValue() );
        context.flagV.set( srcVal.isNegValue() != shifted.isNegValue() );
        context.flagC.clear();
    }

    protected abstract int maxCount();
    protected abstract int minCount();
}

enum TstExec implements CodeExec {
    TstExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData srcVal = oprs.get(0).getIntValue();
        context.flagN.set( srcVal.isNegValue() );
        context.flagZ.set( srcVal.isZeroValue() );
        context.flagV.clear();
        context.flagC.clear();
    }
}

enum CmpExec implements CodeExec {
    CmpExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData lhs = oprs.get(0).getIntValue();
        IntData rhs = oprs.get(1).getIntValue();
        context.flagN.set( lhs.sint() < rhs.sint() );
        context.flagZ.set( lhs.sint() == rhs.sint() );
        context.flagV.clear();
        context.flagC.set( lhs.uint() < rhs.uint() );
    }
}

enum ExtExec implements CodeExec {
    ExtvExec {
        @Override
        protected boolean isSignExt() {
            return true;
        }
    },
    ExtzvExec {
        @Override
        protected boolean isSignExt() {
            return false;
        }
    };

    @Override
    public void execute(List<Operand> oprs, Context context) {
        int pos = oprs.get(0).getIntValue().uint();
        int size = oprs.get(1).getIntValue().uint();
        Operand base = oprs.get(2);
        Operand dest = oprs.get(3);

        assert size <= 32 : "Reserved operand fault";

        IntData extVal;
        if (size == 0) {
            extVal = new IntData(0);
        }
        else {
            long srcVal;
            if (base instanceof Register) {
                assert (pos & 0xffffffffL) <= 31 : "Reserved operand fault";

                int regNum = ((Register)base).regNum;
                srcVal = ((long)context.register[regNum + 1] << 32) | context.register[regNum];
            } else {
                int addr = ((Address)base).getAddress() + (pos >>> 5);
                pos = pos & 31;
                srcVal =
                    ((long)context.memory.loadInt(addr + 4, DataType.L).uint() << 32) |
                    context.memory.loadInt(addr, DataType.L).uint();
            }

            int lSpace = 64 - (pos + size);
            int eVal = (int)(isSignExt() ?
                             srcVal << lSpace >> (lSpace + pos) :
                             srcVal << lSpace >>> (lSpace + pos));
            extVal = new IntData(eVal, DataType.L);
        }

        dest.setValue(extVal);
        context.flagN.set( extVal.isNegValue() );
        context.flagZ.set( extVal.isZeroValue() );
        context.flagV.clear();
    }

    protected abstract boolean isSignExt();
}

enum InsvExec implements CodeExec {
    InsvExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        int size = oprs.get(2).getIntValue().uint();

        assert size <= 32 : "Reserved operand fault";

        if (size != 0) {
            int pos = oprs.get(1).getIntValue().uint();
            long srcVal = (long)oprs.get(0).getIntValue().uint() << pos;
            Operand base = oprs.get(3);
            if (base instanceof Register) {
                assert (pos & 0xffffffffL) <= 31 : "Reserved operand fault";

                int regNum = ((Register)base).regNum;
                long orgVal =
                    ((long)context.register[regNum + 1] << 32) |
                    context.register[regNum];
                long mask = (~(0xffffffffffffffffL << size)) << pos;
                long insVal = (orgVal & ~mask) | (srcVal & mask);
                context.setRegisterValue(regNum, new IntData(insVal, DataType.Q));
            } else {
                int addr = ((Address)base).getAddress() + (pos >>> 5);
                pos = pos & 31;

                long orgVal =
                    ((long)context.memory.loadInt(addr + 4, DataType.L).uint() << 32) |
                    context.memory.loadInt(addr, DataType.L).uint();
                long mask = (~(0xffffffffffffffffL << size)) << pos;
                long insVal = (orgVal & ~mask) | (srcVal & mask);
                context.memory.store(addr, new IntData(insVal, DataType.Q));
            }
        }
    }
}

enum JmpExec implements CodeExec {
    JmpExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        context.register[PC] = ((Address)oprs.get(0)).getAddress();
    }
}

enum BrExec implements CodeExec {
    BrExec {
        @Override
        public boolean check(Context context) {
            return true;
        }
    },
    BneqExec {
        @Override
        public boolean check(Context context) {
            return !context.flagZ.get();
        }
    },
    BeqlExec {
        @Override
        public boolean check(Context context) {
            return context.flagZ.get();
        }
    },
    BgtrExec {
        @Override
        public boolean check(Context context) {
            return !context.flagN.get() && !context.flagZ.get();
        }
    },
    BleqExec {
        @Override
        public boolean check(Context context) {
            return context.flagN.get() || context.flagZ.get();
        }
    },
    BgeqExec {
        @Override
        public boolean check(Context context) {
            return !context.flagN.get();
        }
    },
    BlssExec {
        @Override
        public boolean check(Context context) {
            return context.flagN.get();
        }
    },
    BgtruExec {
        @Override
        public boolean check(Context context) {
            return !context.flagC.get() && !context.flagZ.get();
        }
    },
    BlequExec {
        @Override
        public boolean check(Context context) {
            return context.flagC.get() || context.flagZ.get();
        }
    },
    BvcExec {
        @Override
        public boolean check(Context context) {
            return !context.flagV.get();
        }
    },
    BvsExec {
        @Override
        public boolean check(Context context) {
            return context.flagV.get();
        }
    },
    BccExec {
        @Override
        public boolean check(Context context) {
            return !context.flagC.get();
        }
    },
    BlssuExec {
        @Override
        public boolean check(Context context) {
            return context.flagC.get();
        }
    };

    @Override
    public void execute(List<Operand> oprs, Context context) {
        if (check(context)) {
            context.register[PC] = ((Address)oprs.get(0)).getAddress();
        }
    }

    protected abstract boolean check(Context context);
}

enum BbExec implements CodeExec {
    BbsExec {
        @Override
        public boolean doesBranchOnSet() {
            return true;
        }
    },
    BbcExec {
    },
    BbssExec {
        @Override
        public boolean doesSetBit() {
            return true;
        }
        @Override
        public boolean doesBranchOnSet() {
            return true;
        }
    },
    BbcsExec {
        @Override
        public boolean doesSetBit() {
            return true;
        }
    },
    BbscExec {
        @Override
        public boolean doesClearBit() {
            return true;
        }
        @Override
        public boolean doesBranchOnSet() {
            return true;
        }
    },
    BbccExec{
        @Override
        public boolean doesClearBit() {
            return true;
        }
    };

    @Override public void execute(List<Operand> oprs, Context context) {
        int pos = oprs.get(0).getIntValue().uint();
        Operand base = oprs.get(1);
        Address dest = (Address)oprs.get(2);

        boolean isSet;
        if (base instanceof Register) {
            assert (pos & 0xffffffffL) <= 31 : "Reserved operand fault";

            int regNum = ((Register)base).regNum;
            int bit = 1 << (pos & 0x1f);
            isSet = (context.register[regNum] & bit) != 0;
            if (doesSetBit()) {
                context.register[regNum] |= bit;
            } else if (doesClearBit()) {
                context.register[regNum] &= ~bit;
            }
        } else {
            int addr = ((Address)base).getAddress() + (pos >> 3);
            int targetByte = context.memory.loadInt(addr, DataType.B).uint();
            int bit = 1 << (pos & 7);
            isSet = (targetByte & bit) != 0;
            if (doesSetBit()) {
                context.memory.store(addr, new IntData(targetByte | bit, DataType.B));
            } else if (doesClearBit()) {
                context.memory.store(addr, new IntData(targetByte & ~bit, DataType.B));
            }
        }

        if (isSet == doesBranchOnSet()) {
            context.register[PC] = dest.getAddress();
        }
    }

    protected boolean doesSetBit() {
        return false;
    }
    protected boolean doesClearBit() {
        return false;
    }
    protected boolean doesBranchOnSet() {
        return false;
    }
}

enum BlbExec implements CodeExec {
    BlbsExec {
        @Override
        protected boolean doesBranchOnSet() {
            return true;
        }
    },
    BlbcExec {
        @Override
        protected boolean doesBranchOnSet() {
            return false;
        }
    };

    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData srcVal = oprs.get(0).getIntValue();
        boolean isSet = (srcVal.uint() & 1) == 1;
        if (isSet == doesBranchOnSet()) {
            Address dest = (Address)oprs.get(1);
            context.register[PC] = dest.getAddress();
        }
    }

    protected abstract boolean doesBranchOnSet();
}

enum CallExec implements CodeExec {
    CallgExec {
        @Override
        protected char callType() {
            return 'G';
        }
    },
    CallsExec {
        @Override
        protected char callType() {
            return 'S';
        }
    };

    @Override
    public void execute(List<Operand> oprs, Context context) {
        if (callType() == 'S') {
            IntData nArgs = oprs.get(0).getIntValue();
            context.push(nArgs);
        }
        int preSp = context.register[SP];
        context.register[SP] &= ~0x3;

        int addr = ((Address)oprs.get(1)).getAddress();
        int entryMask = context.memory.loadInt(addr, DataType.W).uint();
        for (int i = 11; i >= 0; i--) {
            if ((entryMask & 1 << i) != 0) {
                context.push(context.register[i]);
            }
        }
        context.push(context.register[PC]);
        context.push(context.register[FP]);
        context.push(context.register[AP]);

        context.flagN.clear();
        context.flagZ.clear();
        context.flagV.clear();
        context.flagC.clear();

        int status = 0;
        status |= preSp << 30;               // low 2 bits of the SP
        if (callType() == 'S') {
            status |= 0b1 << 29;             // S flag
        }
        status |= (entryMask & 0xfff) << 16; // procedure entry mask[0..12]
        status |= context.psl & 0xffef;      // processor status register[0..15] with T cleard
        context.push(status);

        context.push(0);

        context.register[FP] = context.register[SP];
        if (callType() == 'G') {
            context.register[AP] = ((Address)oprs.get(0)).getAddress();
        } else {
            context.register[AP] = preSp;
        }

        context.flagIV.set( (entryMask & 0x4000) != 0 );
        context.flagDV.set( (entryMask & 0x8000) != 0 );
        context.flagFU.clear();

        context.register[PC] = addr + 2;
    }

    protected abstract char callType();
}

enum RetExec implements CodeExec {
    RetExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        context.register[SP] = context.register[FP] + 4;
        int tmp = context.pop();
        context.register[AP] = context.pop();
        context.register[FP] = context.pop();
        context.register[PC] = context.pop();

        int entryMask = (tmp >> 16) & 0xfff;
        for (int i = 0; i <= 11; i++) {
            if ((entryMask & 1 << i) != 0) {
                context.register[i] = context.pop();
            }
        }

        context.register[SP] |= tmp >>> 30;

        context.psl = tmp & 0xffff;

        boolean isCalledWithS = (tmp & 0b1 << 29) != 0;
        if (isCalledWithS) {
            int nArgs = context.pop() & 0xff;
            context.register[SP] += nArgs * 4;
        }
    }
}

enum ChmkExec implements CodeExec {
    ChmkExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        int codeNum = oprs.get(0).getIntValue().uint();
        Kernel.syscall(codeNum, context);
    }
}

enum CaseExec implements CodeExec {
    CaseExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData sel = oprs.get(0).getIntValue();
        IntData base = oprs.get(1).getIntValue();
        IntData limit = oprs.get(2).getIntValue();
        IntData offset = new IntData(sel.sint() - base.sint(), sel.dataType());

        Calculator.sub(offset, limit, context);
        context.flagV.clear();

        if (context.flagC.get() || context.flagZ.get()) {
            int dispAddr = context.register[PC] + offset.uint() * 2;
            IntData disp = context.memory.loadInt(dispAddr, DataType.W);
            context.register[PC] += disp.sint();
        } else {
            context.register[PC] += (limit.uint() + 1) * 2;
        }
    }
}

enum AobExec implements CodeExec {
    AoblssExec {
        @Override
        protected boolean check(IntData index, IntData limit) {
                return index.sint() < limit.sint();
            }
        },
    AobleqExec {
        @Override
        protected boolean check(IntData index, IntData limit) {
            return index.sint() <= limit.sint();
        }
    };

    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData limit = oprs.get(0).getIntValue();
        Operand indexOpr = oprs.get(1);
        IntData index = indexOpr.getIntValue();
        Address dest = (Address)oprs.get(2);
        boolean preFlagC = context.flagC.get();

        index = Calculator.add(index, new IntData(1), context);
        indexOpr.setValue(index);
        context.flagC.set(preFlagC);

        if (check(index, limit)) {
            context.register[PC] = dest.getAddress();
        }
    }

    protected abstract boolean check(IntData index, IntData limit);
}

enum SobExec implements CodeExec {
    SobgeqExec {
        @Override
        protected boolean check(Context context) {
            return !context.flagN.get();
        }
    },
    SobgtrExec {
        @Override
        protected boolean check(Context context) {
            return !context.flagN.get() && !context.flagZ.get();
        }
    };

    @Override
    public void execute(List<Operand> oprs, Context context) {
        Operand indexOpr = oprs.get(0);
        IntData index = indexOpr.getIntValue();
        Address dest = (Address)oprs.get(1);
        boolean preFlagC = context.flagC.get();

        index = Calculator.sub(index, new IntData(1), context);
        indexOpr.setValue(index);
        context.flagC.set(preFlagC);

        if (check(context)) {
            context.register[PC] = dest.getAddress();
        }
    }

    protected abstract boolean check(Context context);
}

enum CvtExec implements CodeExec {
    CvtExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData srcVal = oprs.get(0).getIntValue();
        Operand dest = oprs.get(1);
        IntData cvtVal = new IntData(srcVal.sint(), dest.dataType);
        dest.setValue(cvtVal);

        context.flagN.set( cvtVal.isNegValue() );
        context.flagZ.set( cvtVal.isZeroValue() );
        context.flagV.set( srcVal.isNegValue() != cvtVal.isNegValue() );
        context.flagC.clear();
    }
}

enum CvtlpExec implements CodeExec {
    CvtlpExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData srcVal = oprs.get(0).getIntValue();
        long src = (long)srcVal.sint();
        int len = oprs.get(1).getIntValue().uint();
        Address dest = (Address)oprs.get(2);

        byte tail = src >= 0 ? (byte)12 : (byte)13;
        src = Math.abs(src);
        if (len != 0) {
            tail += src % 10 << 4;
            src /= 10;
            --len;
        }

        Deque<Byte> bytes = new ArrayDeque<>();
        bytes.addFirst(tail);

        while (len-- > 0) {
            byte val = (byte)(src % 10);
            src /= 10;
            if (len-- > 0) {
                val |= (byte)(src % 10 << 4);
                src /= 10;
            }
            bytes.addFirst(val);
        }

        int destAddr = dest.getAddress();
        while (!bytes.isEmpty()) {
            byte val = bytes.removeFirst();
            context.memory.store(destAddr++, new IntData(val, DataType.B));
        }

        context.register[0] = 0;
        context.register[1] = 0;
        context.register[2] = 0;
        context.register[3] = dest.getAddress();

        context.flagN.set( srcVal.isNegValue() );
        context.flagZ.set( srcVal.isZeroValue() );
        context.flagC.clear();
    }
}

enum AcbExec implements CodeExec {
    AcbExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData limit = oprs.get(0).getIntValue();
        IntData addend = oprs.get(1).getIntValue();
        Operand indexOpr = oprs.get(2);
        IntData index = indexOpr.getIntValue();
        Address dest = (Address)oprs.get(3);
        boolean preFlagC = context.flagC.get();

        index = Calculator.add(index, addend, context);
        indexOpr.setValue(index);
        context.flagC.set(preFlagC);

        if (!addend.isNegValue()) {
            if (index.sint() <= limit.sint()) {
                context.register[PC] = dest.getAddress();
            }
        } else {
            if (index.sint() >= limit.sint()) {
                context.register[PC] = dest.getAddress();
            }
        }
    }
}

enum MovcExec implements CodeExec {
    MovcExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData srclen = oprs.get(0).getIntValue();
        int srcAddr = ((Address)oprs.get(1)).getAddress();
        IntData fillVal;
        IntData destlen;
        int destAddr;
        if (oprs.size() == 5) {
            fillVal = oprs.get(2).getIntValue();
            destlen = oprs.get(3).getIntValue();
            destAddr = ((Address)oprs.get(4)).getAddress();
        } else {
            fillVal = null;
            destlen = srclen;
            destAddr = ((Address)oprs.get(2)).getAddress();
        }

        int slen = srclen.uint();
        int dlen = destlen.uint();
        for (; slen > 0 && dlen >0; slen--, dlen--) {
            IntData byteVal = context.memory.loadInt(srcAddr++, DataType.B);
            context.memory.store(destAddr++, byteVal);
        }
        for (; dlen > 0; dlen--) {
            context.memory.store(destAddr++, fillVal);
        }

        context.register[0] = slen;
        context.register[1] = srcAddr;
        context.register[2] = 0;
        context.register[3] = destAddr;
        context.register[4] = 0;
        context.register[5] = 0;
        // Set flags
        Calculator.sub(srclen, destlen, context);
        context.flagV.clear();
    }
}

enum CmpcExec implements CodeExec {
    CmpcExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData str1len = oprs.get(0).getIntValue();
        int str1Addr = ((Address)oprs.get(1)).getAddress();
        IntData fillVal;
        IntData str2len;
        int str2Addr;
        if (oprs.size() == 5) {
            fillVal = oprs.get(2).getIntValue();
            str2len = oprs.get(3).getIntValue();
            str2Addr = ((Address)oprs.get(4)).getAddress();
        } else {
            fillVal = null;
            str2len = str1len;
            str2Addr = ((Address)oprs.get(2)).getAddress();
        }

        int s1len = str1len.uint();
        int s2len = str2len.uint();
        COMPC: {
            for (; s1len > 0 && s2len >0; s1len--, s2len--, str1Addr++, str2Addr++) {
                IntData str1Val = context.memory.loadInt(str1Addr, DataType.B);
                IntData str2Val = context.memory.loadInt(str2Addr, DataType.B);
                Calculator.sub(str1Val, str2Val, context);
                if (!context.flagZ.get()) {
                    break COMPC;
                }
            }
            for (; s1len > 0; s1len--, str1Addr++) {
                IntData str1Val = context.memory.loadInt(str1Addr, DataType.B);
                Calculator.sub(str1Val, fillVal, context);
                if (!context.flagZ.get()) {
                    break COMPC;
                }
            }
            for (; s2len > 0; s2len--, str2Addr++) {
                IntData str2Val = context.memory.loadInt(str2Addr, DataType.B);
                Calculator.sub(fillVal, str2Val, context);
                if (!context.flagZ.get()) {
                    break COMPC;
                }
            }
        }

        context.register[0] = s1len;
        context.register[1] = str1Addr;
        context.register[2] = s2len;
        context.register[3] = str2Addr;
        context.flagV.clear();
    }
}

enum LoccExec implements CodeExec {
    LoccExec {
        @Override
        protected boolean isDetected(IntData actual, IntData target) {
            return actual.uint() == target.uint();
        }
    },
    SkpcExec {
        @Override
        protected boolean isDetected(IntData actual, IntData target) {
            return actual.uint() != target.uint();
        }
    };

    @Override
    public void execute(List<Operand> oprs, Context context) {
        IntData target = oprs.get(0).getIntValue();
        int len = oprs.get(1).getIntValue().uint();
        int addr = ((Address)oprs.get(2)).getAddress();

        for (; len > 0; len--, addr++) {
            IntData byteVal = context.memory.loadInt(addr, DataType.B);
            if (isDetected(target, byteVal)) {
                break;
            }
        }

        context.register[0] = len;
        context.register[1] = addr;
        context.flagN.clear();
        context.flagZ.set( context.register[0] == 0 );
        context.flagV.clear();
        context.flagC.clear();
    }

    protected abstract boolean isDetected(IntData actual, IntData target);
}

enum MovpExec implements CodeExec {
    MovpExec;
    @Override
    public void execute(List<Operand> oprs, Context context) {
        int len = oprs.get(0).getIntValue().uint();
        int srcAddr = ((Address)oprs.get(1)).getAddress();
        int destAddr = ((Address)oprs.get(2)).getAddress();
        int mostSigSrcAddr = srcAddr;
        int mostSigDestAddr = destAddr;

        int byteslen = len / 2 + 1;
        for (; byteslen > 0; byteslen--) {
            IntData byteVal = context.memory.loadInt(srcAddr++, DataType.B);
            context.memory.store(destAddr++, byteVal);
        }

        context.register[0] = 0;
        context.register[1] = mostSigSrcAddr;
        context.register[2] = 0;
        context.register[3] = mostSigDestAddr;
        context.flagN.set( isNegativePacked(destAddr, len, context) );
        context.flagZ.set( isZeroPacked(destAddr, len, context) );
        context.flagV.clear();
    }

    private boolean isNegativePacked(int addr, int len, Context context) {
        int signAddr = addr + len / 2;
        byte sign = (byte)(context.memory.loadInt(signAddr, DataType.B).uint() & 0xf);
        switch (sign) {
        case 0xa: case 0xc: case 0xe: case 0xf:
            return false;
        case 0xb: case 0xd:
            return true;
        default:
            assert false : "Invalid Packed decimal string";
            return false;
        }
    }

    private boolean isZeroPacked(int addr, int len, Context context) {
        int numslen = len / 2;
        for (; numslen > 0; numslen--) {
            int val = context.memory.loadInt(addr++, DataType.B).uint();
            if (val != 0) {
                return false;
            }
        }
        return (addr & 0xf0) == 0;
    }
}

enum EditpcExec implements CodeExec {
    EditpcExec;

    // not thread safe
    private final Queue<Byte> srcDigits = new ArrayDeque<>();
    private final List<Byte> destChars = new ArrayList<>();
    private Context context;

    @Override
    public void execute(List<Operand> oprs, Context context) {
        this.context = context;
        int srcLen = oprs.get(0).getIntValue().uint();
        int srcAddr = ((Address)oprs.get(1)).getAddress();
        int ptnAddr = ((Address)oprs.get(2)).getAddress();

        assert srcLen <= 31 : "Reserved operand fault";

        context.flagN.set( isNegativePacked(srcAddr, srcLen) );
        setSignChar((byte)(context.flagN.get() ? '-' : ' '));
        setFillChar((byte)' ');
        initDigits(srcAddr, srcLen);
        context.flagV.clear();
        context.flagC.clear();

        int endOpAddr = execOps(ptnAddr);

        // after execution
        byte[] charBytes = new byte[destChars.size()];
        for (int i = 0; i < destChars.size(); ++i) {
            charBytes[i] = destChars.get(i);
        }

        int destAddr = ((Address)oprs.get(3)).getAddress();
        context.memory.storeBytes(destAddr, charBytes, charBytes.length);

        context.register[0] = srcLen;
        context.register[1] = srcAddr;
        context.register[2] = 0;
        context.register[3] = endOpAddr;
        context.register[4] = 0;
        context.register[5] = destAddr + charBytes.length;
    }

    private int execOps(int ptnAddr) {
        do {
            int code = context.memory.loadInt(ptnAddr, DataType.B).uint();
            if (code == 0x0) {
                doEnd();
                return ptnAddr;
            } else if (code == 0x1) {
                doEndFloat();
            } else if (0x91 <= code && code <= 0x9f) {
                doMove(code & 0xf);
            } else if (0xa1 <= code && code <= 0xaf) {
                doFloat(code & 0xf);
            } else {
                assert false : "Unimplemented editpc operand :" + code;
            }
            ++ptnAddr;
        } while (true);
    }

    private void doEnd() {
        assert srcDigits.size() == 0 : "Reserved operand abort";
        if (context.flagN.get()) {
            context.flagZ.clear();
        }
    }

    private void doEndFloat() {
        if (!hasSignificanceSet()) {
            destChars.add(getSignChar());
            setSignificance();
        }
    }

    private void doMove(int count) {
        assert count <= srcDigits.size() : "Reserved operand abort";

        for (; count > 0; count--) {
            byte digit = srcDigits.remove();
            if (digit != 0) {
                setSignificance();
                clearZero();
            }
            if (!hasSignificanceSet()) {
                destChars.add(getFillChar());
            } else {
                destChars.add(asciiNumCode(digit));
            }
        }
    }

    private void doFloat(int count) {
        assert count <= srcDigits.size() : "Reserved operand abort";

        for (; count > 0; count--) {
            byte digit = srcDigits.remove();
            if (digit != 0) {
                if (!hasSignificanceSet()) {
                    destChars.add(getSignChar());
                }
                setSignificance();
                clearZero();
            }
            if (!hasSignificanceSet()) {
                destChars.add(getFillChar());
            } else {
                destChars.add(asciiNumCode(digit));
            }
        }
    }

    private byte getFillChar() {
        return (byte)context.register[2];
    }

    private void setFillChar(byte c) {
        context.register[2] &= ~0xff;
        context.register[2] |= c;
    }

    private byte getSignChar() {
        return (byte)(context.register[2] >> 8);
    }

    private void setSignChar(byte c) {
        context.register[2] &= ~0xff00;
        context.register[2] |= c << 8;
    }

    private boolean hasSignificanceSet() {
        return context.flagC.get();
    }

    private void setSignificance() {
        context.flagC.set();
    }

    private void clearZero() {
        context.flagZ.clear();
    }

    private byte asciiNumCode(byte val) {
        return (byte)(val + '0');
    }

    private boolean isNegativePacked(int addr, int len) {
        int signAddr = addr + len / 2;
        byte sign = (byte)(context.memory.loadInt(signAddr, DataType.B).uint() & 0xf);
        switch (sign) {
        case 0xa: case 0xc: case 0xe: case 0xf:
            return false;
        case 0xb: case 0xd:
            return true;
        default:
            assert false : "Invalid Packed decimal string";
            return false;
        }
    }

    private void initDigits(int addr, int len) {
        srcDigits.clear();
        destChars.clear();

        if (len % 2 == 0) {
            byte firstDigit = (byte)(context.memory.loadInt(addr++, DataType.B).uint() & 0xf);
            srcDigits.add(firstDigit);
            --len;
        }

        while (len > 0) {
            int twoDigits = context.memory.loadInt(addr++, DataType.B).uint();
            srcDigits.add((byte)(twoDigits >>> 4));
            --len;
            if (len <= 0) {
                break;
            }
            srcDigits.add((byte)(twoDigits & 0xf));
            --len;
        }
    }
}

class Nullcode extends Opcode {
    private final short val;

    protected Nullcode(int val) {
        super(null);
        this.val = (short)val;
    }

    @Override
    public DataType[] operands() {
        return new DataType[0];
    }

    @Override
    public String mnemonic() {
        return String.format(".word 0x%x", val);
    }

    @Override
    public int len() {
        return 2;
    }

    @Override
    public void execute(List<Operand> oprs, Context c) {
        System.err.printf("Error: unknown code: 0x%x%n", val);
        throw new RuntimeException();
    }
}


class Calculator {
    public static IntData add(IntData arg1, IntData arg2, boolean addCarry, Context context) {
        byte[] sumBytes = new byte[arg1.size()];
        byte[] arg1Bytes = arg1.bytes();
        byte[] arg2Bytes = arg2.bytes();
        int carry = addCarry ? 1 : 0;
        for (int i = 0; i < arg1.size(); i++) {
            int tmp = (arg1Bytes[i] & 0xff) + (arg2Bytes[i] & 0xff) + carry;
            sumBytes[i] = (byte)tmp;
            carry = tmp >> 8;
        }
        IntData sum = new IntData(sumBytes, arg1.dataType());
        context.flagN.set( sum.isNegValue() );
        context.flagZ.set( sum.isZeroValue() );
        context.flagV.set( arg1.isNegValue() == arg2.isNegValue() &&
                           arg1.isNegValue() != sum.isNegValue() );
        context.flagC.set( carry == 1 );
        return sum;
    }

    public static IntData add(IntData arg1, IntData arg2, Context context) {
        return add(arg1, arg2, false, context);
    }

    public static IntData sub(IntData arg1, IntData arg2, Context context) {
        IntData diff = add(arg1, IntData.bitInvert( arg2 ), true, context);
        context.flagC.set( !context.flagC.get() );
        return diff;
    }

    public static IntData mul(IntData arg1, IntData arg2, Context context) {
        long product64b = (long)arg1.sint() * arg2.sint();
        IntData prod = new IntData((int)product64b, arg1.dataType());

        context.flagN.set( prod.isNegValue() );
        context.flagZ.set( prod.isZeroValue() );
        int highHalf = (int)(product64b >>> 32);
        context.flagV.set( (prod.isNegValue() && highHalf == 0xffffffff) ||
                           (!prod.isNegValue() && highHalf == 0) );
        context.flagC.clear();
        return prod;
    }

    public static IntData div(IntData dividend, IntData divisor, Context context) {
        if (divisor.uint() == 0 ||
            (dividend.isLargestNegativeInteger() && divisor.sint() == -1)) {
            context.flagV.set();
            return null;
        } else {
            IntData quo = new IntData(dividend.sint() / divisor.sint(), divisor.dataType());
            context.flagN.set( quo.isNegValue() );
            context.flagZ.set( quo.isZeroValue() );
            context.flagV.clear();
            context.flagC.clear();
            return quo;
        }
    }
}


/* Not Implemented
    HALT (0x0), REI   (0x2),
    BPT (0x3), RET   (0x4),
    RSB (0x5), LDPCTX (0x6),
    SVPCTX (0x7), CVTPS (0x8, W,B,W,B),
    CVTSP (0x9,W,B,W,B), INDEX (0xa, L,L,L,L,L,L),
    CRC (0xb, B,L,W,B), PROBER (0xc, B,W,B),
    PROBEW (0xd, B,W,B), INSQUE (0xe, B,B),
    REMQUE (0xf, B,W), BSBB (0x10, BrB),
    JSB (0x16, B),
    ADDP4 (0x20, W,B,W,B), ADDP6 (0x21, W,B,W,B,W,B),
    SUBP4 (0x22, W,B,W,B), SUBP6 (0x23, W,B,W,B,W,B),
    CVTPT (0x24, W,B,B,W,B), MULP (0x25, W,B,W,B,W,B),
    CVTTP (0x26, W,B,B,W,B), DIVP (0x27, W,B,W,B,W,B),
    SCANC (0x2a, W,B,B,B), SPANC (0x2b, W,B,B,B),
    MOVTC (0x2e, W,B,B,B,W,B), MOVTUC (0x2f, W,B,B,B,W,B),
    BSBW  (0x30, BrW), CMPP3 (0x35, W,B,B),
    CVTPL (0x36, W,B,L), CMPP4 (0x37, W,B,W,B),
    MATCHC(0x39, W,B,W,B),
    MULF2 (0x44, F,F), MULG2 (0x44fd, G,G),
    MULF3 (0x45, F,F,F), MULG3 (0x45fd, G,G,G),
    DIVF2 (0x46, F,F), DIVG2 (0x46fd, G,G),
    DIVF3 (0x47, F,F,F), DIVG3 (0x47fd, G,G,G),
    ACBF (0x4f, F,F,F,BrW), ACBG (0x4ffd, G,G,G,BrW),
    EMODF (0x54, F,B,F,L,F), EMODG (0x54fd, G,W,G,L,G),
    POLYF (0x55, F,W,B), POLYG (0x55fd, G,W,B),
    ADAWI (0x58, W,W),
    INSQHI (0x5c, B,Q), INSQTI (0x5d, B,Q),
    REMQHI (0x5e, Q,L), REMQTI (0x5f, Q,L),
    MULD2 (0x64, D,D), MULH2 (0x64fd, H,H),
    MULD3 (0x65, D,D,D), MULH3 (0x65fd, H,H,H),
    DIVD2 (0x66, D,D), DIVH2 (0x66fd, H,H),
    DIVD3 (0x67, D,D,D), DIVH3 (0x67fd, H,H,H),
    ACBD (0x6f, D,D,D,BrW), ACBH (0x6ffd, H,H,H,BrW),
    EMODD (0x74, D,B,D,L,D), EMODH (0x74fd, H,W,H,L,H),
    POLYD (0x75, D,W,B), POLYH (0x75fd, H,W,B),
    EMUL (0x7a, L,L,L,Q), EDIV (0x7b, L,Q,L,L),
    ROTL  (0x9c, B,L,L),
    BISPSW (0xb8, W), BICPSW (0xb9, W),
    POPR (0xba, W), PUSHR (0xbb, W),
    CHME (0xbd, W), CHMS (0xbe, W),
    CHMU (0xbf, W),
    ADWC (0xd8, L,L), SBWC (0xd9, L,L),
    MTPR (0xda, L,L), MFPR (0xdb, L,L),
    MOVPSL (0xdc, L),
    FFS (0xea, L,B,B,L), FFC (0xeb, L,B,B,L),
    CMPV (0xec, L,B,B,L), CMPZV (0xed, L,B,B,L),
    ASHP (0xf8, B,W,B,B,W,B), XFC (0xfc),
    BUGL (0xfdff, L), BUGW (0xfeff, W);
*/
