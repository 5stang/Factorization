package factorization.servo;

import java.io.IOException;
import java.util.Iterator;

import factorization.api.Coord;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.Share;
import factorization.notify.Notify;
import factorization.servo.instructions.IntegerValue;

public class Executioner {
    private transient final ServoMotor motor;
    
    public static final int STACKS = 16;
    public static final int STACK_ERRNO = 0xF, STACK_ENTER = 0xE, STACK_IO = 0x0, STACK_ARGUMENT = 0x4;
    public static final byte JMP_NONE = 0, JMP_NEXT_INSTRUCTION = 1, JMP_NEXT_TILE = 2;
    
    public ServoStack[] stacks = new ServoStack[STACKS];
    private ServoStack buffer;
    private boolean is_running = false;
    public boolean stacks_changed = false;
    public byte jmp = JMP_NONE;
    public EntryAction entry_action = EntryAction.ENTRY_EXECUTE;
    
    public int pc = 0;
    public byte seg = STACK_ENTER;
    public boolean cpu_blocked = false;
    
    public Executioner(ServoMotor motor) {
        this.motor = motor;
        for (int i = 0; i < stacks.length; i++) {
            stacks[i] = new ServoStack(this);
        }
        buffer = new ServoStack(this);
    }
    
    public ServoStack getServoStack(int stackId) {
        if (stackId < 0) stackId = 0;
        if (stackId >= STACKS) stackId = STACKS - 1;
        if (is_running && stackId == seg) return buffer;
        return stacks[stackId];
    }
    
    public void putError(Object error) {
        if (!motor.worldObj.isRemote) {
            Notify.send(motor.getCurrentPos(), "%s", error.toString());
        }
        ServoStack ss = getServoStack(STACK_ERRNO);
        if (ss.getFreeSpace() <= 0) {
            ss.popEnd();
        }
        ss.push(error);
    }
    
    public void tick() {
        if (cpu_blocked) return;
        if (pc == -1) return;
        if (seg == -1) return;
        ServoStack ss = getServoStack(seg);
        if (ss == null) return;
        if (pc >= ss.getSize()) {
            pc = -1;
            return;
        }
        int i = 0;
        byte orig_seg = seg;
        is_running = true;
        boolean found_blocking_instruction = false;
        Iterator<Object> it = ss.descendingIterator();
        TICK: while (it.hasNext()) {
            Object obj = it.next();
            if (i++ != pc) continue;
            if (obj instanceof Instruction) {
                Instruction insn = (Instruction) obj;
                switch (insn.getBlockingBehavior()) {
                case BLOCK_FOR_TICK:
                    if (found_blocking_instruction) {
                        break TICK;
                    }
                    found_blocking_instruction = true;
                    break;
                case BLOCK_UNTIL_NEXT_ENTRY:
                    if (found_blocking_instruction) {
                        break TICK;
                    }
                    cpu_blocked = true;
                    found_blocking_instruction = true;
                    break;
                default:
                case NO_BLOCKING:
                    break;
                }
                pc++;
                insn.motorHit(motor);
            } else {
                pc++;
                motor.getArgStack().push(obj);
            }
            if (orig_seg != seg) break TICK;
        }
        is_running = false;
        if (buffer.getSize() > 0) {
            //Should we stay silent? -- motor.putError("Lost instructions: " + buffer.getSize());
            buffer.clear();
        }
        if (pc >= ss.getSize()) {
            pc = -1;
            return;
        }
    }
    
    public void onEnterNewBlock(TileEntityServoRail rail) {
        cpu_blocked = false;
        if (pc == -1) {
            seg = STACK_ENTER;
            pc = 0;
        }
        switch (entry_action) {
        default:
        case ENTRY_EXECUTE:
            if (rail == null /* :| */ || rail.decoration == null) {
                if (jmp == JMP_NEXT_TILE) {
                    jmp = JMP_NONE;
                }
                return;
            }
            if (motor.getCurrentPos().isWeaklyPowered()) {
                if (jmp == JMP_NEXT_TILE) {
                    jmp = JMP_NONE;
                }
                return;
            }
            if (jmp != JMP_NONE) {
                jmp = JMP_NONE;
                return;
            }
            rail.decoration.motorHit(motor);
            break;
        case ENTRY_LOAD:
            if (rail.decoration != null) {
                getServoStack(STACK_IO).push(rail.decoration.copyComponent());
            }
            break;
        case ENTRY_WRITE:
            ServoStack ss = getServoStack(STACK_IO);
            if (ss.getSize() == 0) {
                motor.putError("IO stack is emtpy!");
                break;
            }
            Object o = ss.pop();
            if (o instanceof Instruction) {
                rail.decoration = (Instruction) ((Instruction) o).copyComponent();
                new Coord(rail).markBlockForUpdate();
            } else if (o instanceof Integer) {
                int val = (Integer) o;
                IntegerValue iv = new IntegerValue();
                iv.setVal(val);
                rail.decoration = iv;
                new Coord(rail).markBlockForUpdate();
            } else {
                motor.putError("Can't write " + o + ", sorry!");
            }
            if (ss.getSize() == 0) {
                entry_action = EntryAction.ENTRY_EXECUTE;
            }
            break;
        case ENTRY_IGNORE:
            break;
        }
    }
    
    void putData(DataHelper data) throws IOException {
        if (data.hasLegacy("skip")) {
            jmp = data.as(Share.VISIBLE, "skip").putBoolean(jmp == 0) == true ? JMP_NEXT_INSTRUCTION : JMP_NONE;
        } else {
            jmp = data.as(Share.VISIBLE, "jmp").putByte(jmp);
        }
        entry_action = data.as(Share.VISIBLE, "entryAction").putEnum(entry_action);
        for (int i = 0; i < STACKS; i++) {
            String name = "stack" + i;
            stacks[i] = data.as(Share.VISIBLE, name).put(stacks[i]);
        }
        
        pc = data.as(Share.VISIBLE, "pc").putInt(pc);
        seg = data.as(Share.VISIBLE, "seg").putByte(seg);
        cpu_blocked = data.as(Share.VISIBLE, "cpuBlock").putBoolean(cpu_blocked);
    }

}
