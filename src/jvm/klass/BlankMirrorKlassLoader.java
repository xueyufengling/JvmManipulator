package jvm.klass;

import jvm.jar.JarFiles;

public class BlankMirrorKlassLoader {

	public static byte[] loadByteCode(Object target) {
		Class<?> target_clazz = target.getClass();
		byte[] bytecode = JarFiles.getJarResourceAsBytes(target_clazz, target_clazz.getName().replace('.', '/') + ".class");
		return bytecode;
	}
}
