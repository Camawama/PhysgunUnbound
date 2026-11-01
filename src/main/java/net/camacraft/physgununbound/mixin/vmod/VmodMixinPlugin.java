package net.camacraft.physgununbound.mixin.vmod;

import net.camacraft.physgununbound.vmod.VmodHookStatus;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * The plugin of {@code physgununbound.vmod.mixins.json}, the VMod hooks' own config (not
 * required: whatever goes wrong applying it is logged, never fatal). It applies the hooks only when
 * VMod and its physgun state class are there, and records whether they really made it into
 * {@code fromPkt} and into VMod's message handler ({@link VmodHookStatus}), so a VMod that changed
 * gets one log line at server start and an idle addon instead of a crash.
 */
public final class VmodMixinPlugin implements IMixinConfigPlugin {
    private static final String STATE_CLASS = "net/spaceeye/vmod/physgun/PlayerPhysgunState.class";
    private static final String HANDLER = "physgununbound$onPhysgunMessage";
    private static final String THREAD_HANDLER = "physgununbound$onTheServerThread";

    private boolean apply;

    @Override
    public void onLoad(String mixinPackage) {
        LoadingModList list = LoadingModList.get();
        boolean vmod = list != null && list.getModFileById("the_vmod") != null;
        apply = vmod && hasClass();
    }

    /** Looks for the class file without loading it (loading it now would apply mixins to it too early). */
    private static boolean hasClass() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) loader = VmodMixinPlugin.class.getClassLoader();
        return loader != null && loader.getResource(STATE_CLASS) != null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return apply;
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (calls(targetClass, "fromPkt", HANDLER)) VmodHookStatus.markApplied();
        if (calls(targetClass, "serverHandler", THREAD_HANDLER)) VmodHookStatus.markMessagesHooked();
    }

    /** Whether {@code method} of the class calls a handler whose name contains {@code handler}. */
    private static boolean calls(ClassNode targetClass, String method, String handler) {
        for (MethodNode node : targetClass.methods) {
            if (!method.equals(node.name)) continue;
            for (AbstractInsnNode insn : node.instructions) {
                // Mixin merges the handler under a decorated name that still contains ours
                if (insn instanceof MethodInsnNode call && call.name.contains(handler)) return true;
            }
        }
        return false;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
