package factorization.coremod;

import java.io.IOException;
import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;

import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import cpw.mods.fml.relauncher.FMLRelaunchLog;

public class ASMTransformer implements IClassTransformer {
    static class Append {
        private final String obfClassName;
        private final String srgName, mcpName;
        boolean satisfied = false;
        Append(String obfClassName, String srgClassName, String srgName, String mcpName) {
            this.obfClassName = obfClassName;
            this.srgName = srgName;
            this.mcpName = mcpName;
        }
        
        boolean applies(MethodNode method) {
            if (LoadingPlugin.deobfuscatedEnvironment) {
                return method.name.equals(mcpName);
            } else {
                String method_as_srg = FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(obfClassName, method.name, method.desc);
                return srgName.equals(method_as_srg);
            }
        }
    }
    
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (transformedName.equals("net.minecraft.block.Block")) {
            return applyTransform(basicClass,
                    new Append(name, transformedName, "func_149723_a", "onBlockDestroyedByExplosion"),
                    new Append(name, transformedName, "func_149659_a", "canDropFromExplosion")
            );
        }
        if (transformedName.equals("net.minecraft.client.gui.inventory.GuiContainer")) {
            return applyTransform(basicClass,
                    new Append(name, transformedName, "func_73869_a", "keyTyped")
            );
        }
        return basicClass;
    }
    
    byte[] applyTransform(byte[] basicClass, Append... changes) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (MethodNode m : cn.methods) {
            for (Append change : changes) {
                if (change.applies(m)) {
                    MethodNode method = getMethod(change.srgName);
                    appendMethod(m, method);
                    change.satisfied = true;
                }
            }
        }
        
        for (Append change : changes) {
            if (!change.satisfied) {
                throw new RuntimeException("Unable to find method " + cn.name + "." + change.srgName + " (" + change.mcpName + ")");
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }
    
    List<MethodNode> apendeeMethods = null;
    
    MethodNode getMethod(String methodName) {
        if (apendeeMethods == null) {
            ClassReader cr = new ClassReader(getAppendeeBytecodeBytecode());
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);
            apendeeMethods = cn.methods;
        }
        for (MethodNode m : apendeeMethods) {
            if (m.name.equals(methodName)) {
                return m;
            }
        }
        throw new RuntimeException("Couldn't find method " + methodName);
    }
    
    boolean isReturn(int op) {
        return op == Opcodes.IRETURN
                || op == Opcodes.LRETURN
                || op == Opcodes.FRETURN
                || op == Opcodes.DRETURN
                || op == Opcodes.ARETURN
                || op == Opcodes.RETURN;
    }
    
    void appendMethod(MethodNode base, MethodNode toAppend) {
        AbstractInsnNode base_end = base.instructions.getLast();
        while (!isReturn(base_end.getOpcode())) {
            base_end = base_end.getPrevious();
        }
        AbstractInsnNode ins = toAppend.instructions.getFirst();
        while (ins != null) {
            AbstractInsnNode next = ins.getNext();
            if (!(ins instanceof LineNumberNode)) {
                base.instructions.insertBefore(base_end, ins);
            }
            ins = next;
        }
        base.instructions.remove(base_end);
    }

    
    byte[] getAppendeeBytecodeBytecode() {
        LaunchClassLoader rcl = (LaunchClassLoader) getClass().getClassLoader();
        try {
            return rcl.getClassBytes(MethodAppends.class.getCanonicalName());
        } catch (IOException e) {
            e.printStackTrace();
        }   
        return null;
    }   
    
    void printInstructions(AbstractInsnNode ins) {
        while (ins != null) {
            System.out.print(ins.getOpcode() + "  ");
            if ((ins instanceof JumpInsnNode)) {
                System.out.println("Jump to " + ((JumpInsnNode) ins).label.getLabel());
            } else if ((ins instanceof LabelNode)) {
                System.out.println("Label: " + ((LabelNode) ins).getLabel());
            } else {
                System.out.println(ins);
            }
            ins = ins.getNext();
        }
    }
    
    void log(Object msg) {
        FMLRelaunchLog.info(msg.toString());
    }
}
