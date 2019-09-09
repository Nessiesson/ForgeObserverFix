package nessiesson.forgeobserverfix;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Map;

// This has been fixed in >1.12.0 Forge already. Why is the technical community two years behind?
// Heavily inspired by LexMaxnos' RedHerring.
@IFMLLoadingPlugin.Name("ForgeObserverFix")
@IFMLLoadingPlugin.SortingIndex(1001)
public class ForgeObserverFix implements IFMLLoadingPlugin {
	private static final String DESC = "(Lnet/minecraftforge/registries/IForgeRegistryInternal;Lnet/minecraftforge/registries/RegistryManager;ILnet/minecraft/block/Block;Lnet/minecraft/block/Block;)V";

	@Override
	public String[] getASMTransformerClass() {
		return new String[]{this.getClass().getName() + "$Transformer"};
	}

	public static class Transformer implements IClassTransformer {
		@Override
		public byte[] transform(String name, String transformedName, byte[] clazz) {
			if (!transformedName.equals("net.minecraftforge.registries.GameData$BlockCallbacks")) {
				return clazz;
			}

			FMLLog.log.info("[FORGEOBSERVERFIX] Patching " + transformedName + "(" + name + ")");
			try {
				ClassNode node = getNode(clazz);

				for (MethodNode method : node.methods) {
					if (!method.name.equals("onAdd") || !method.desc.equals(DESC)) {
						continue;
					}

					for (int i = 0; i < method.instructions.size(); i++) {
						AbstractInsnNode ain = method.instructions.get(i);
						if (ain.getOpcode() != Opcodes.ALOAD || ((VarInsnNode) ain).var != 7) {
							continue;
						}

						AbstractInsnNode next = method.instructions.get(i + 1);
						if (next.getOpcode() != Opcodes.ILOAD || ((VarInsnNode) next).var != 8) {
							continue;
						}

						AbstractInsnNode nextnext = method.instructions.get(i + 2);
						if (nextnext.getOpcode() != Opcodes.BALOAD) {
							continue;
						}

						AbstractInsnNode nextnextnext = method.instructions.get(i + 3);
						if (nextnextnext.getOpcode() != Opcodes.IFEQ) {
							continue;
						}

						InsnList insert = new InsnList();
						insert.add(new VarInsnNode(Opcodes.ALOAD, 4));
						insert.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false));
						insert.add(new LdcInsnNode("Lnet/minecraft/block/BlockObserver;"));
						insert.add(new JumpInsnNode(Opcodes.IF_ACMPNE, ((JumpInsnNode) nextnextnext).label));

						method.instructions.insert(nextnextnext, insert);
						break;
					}
				}

				return getBytes(node);
			} catch (Exception e) {
				FMLLog.log.info("[FORGEOBSERVERFIX] Could not patch GameData$BlockCallbacks: " + e.getMessage());
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}

		private ClassNode getNode(byte[] data) {
			ClassNode clazz = new ClassNode();
			ClassReader classReader = new ClassReader(data);
			classReader.accept(clazz, 0);
			return clazz;
		}

		private byte[] getBytes(ClassNode node) {
			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			node.accept(writer);
			return writer.toByteArray();
		}

	}

	// @formatter:off
	@Override public String getAccessTransformerClass() { return null; }
	@Override public String getModContainerClass() { return null; }
	@Override public String getSetupClass() { return null; }
	@Override public void injectData(Map<String, Object> data) {}
	// @formatter:on
}