package jvm.jar;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.jar.JarEntry;

import jvm.klass.KlassLoader;

public class JarKlassLoader {

	/**
	 * 加载指定jar中的所有类
	 * 
	 * @param loader
	 * @param jar
	 */
	public static void loadJarKlass(ClassLoader loader, InputStream jar) {
		JarFiles.filterClass(jar, (String class_full_name, JarEntry entry, ByteArrayOutputStream bytes) -> {
			KlassLoader.defineClass(loader, class_full_name, bytes.toByteArray());
			return true;
		});
	}

	/**
	 * 加载jar子包中的类
	 * 
	 * @param loader
	 * @param jar
	 * @param package_name
	 * @param include_subpackage
	 */
	public static void loadJarKlass(ClassLoader loader, InputStream jar, String package_name, boolean include_subpackage) {
		JarFiles.filterClass(jar, package_name, include_subpackage, (String class_full_name, JarEntry entry, ByteArrayOutputStream bytes) -> {
			KlassLoader.defineClass(loader, class_full_name, bytes.toByteArray());
			return true;
		});
	}

	/**
	 * 从调用该方法的类所属的ClassLoader加载目标jar中的全部类
	 * 
	 * @param jar
	 */
	public static void loadJarKlass(InputStream jar) {
		loadJarKlass(KlassLoader.getOuterCallerClassLoader(), jar);
	}

	/**
	 * 从调用该方法的类所属的ClassLoader加载目标jar中指定路径下的类
	 * 
	 * @param jar
	 * @param package_name
	 * @param include_subpackage
	 */
	public static void loadJarKlass(InputStream jar, String package_name, boolean include_subpackage) {
		loadJarKlass(KlassLoader.getOuterCallerClassLoader(), jar, package_name, include_subpackage);
	}
}
