package jvm.jar;

import java.io.InputStream;

import jvm.klass.KlassLoader;
import jvm.lang.JavaLang;

public class JarKlassLoader {
	public static String parentClassLoaderField = KlassLoader.default_parent_field_name;

	/**
	 * 加载指定jar中的所有类
	 * 
	 * @param loader
	 * @param jar
	 */
	public static ClassLoader loadKlass(ClassLoader loader, InputStream jar) {
		return KlassLoader.Proxy.addFor(loader, parentClassLoaderField, JarFiles.collectClass(jar));
	}

	public static ClassLoader loadKlass(ClassLoader loader, byte[] jar_bytes) {
		return loadKlass(loader, JarFiles.getJarInputStream(jar_bytes));
	}

	public static final void resetParentClassLoaderField() {
		parentClassLoaderField = KlassLoader.default_parent_field_name;
	}

	/**
	 * 加载jar子包中的类
	 * 
	 * @param loader
	 * @param jar
	 * @param package_name
	 * @param include_subpackage
	 */
	public static ClassLoader loadKlass(ClassLoader loader, InputStream jar, String package_name, boolean include_subpackage) {
		return KlassLoader.Proxy.addFor(loader, parentClassLoaderField, JarFiles.collectClass(jar, package_name, include_subpackage));
	}

	public static ClassLoader loadKlass(ClassLoader loader, byte[] jar_bytes, String package_name, boolean include_subpackage) {
		return loadKlass(loader, JarFiles.getJarInputStream(jar_bytes));
	}

	/**
	 * 从调用该方法的类所属的ClassLoader加载目标jar中的全部类
	 * 
	 * @param jar
	 */
	public static ClassLoader loadKlass(InputStream jar) {
		Class<?> caller = JavaLang.getOuterCallerClass();
		return loadKlass(caller.getClassLoader(), jar);
	}

	public static ClassLoader loadKlass(byte[] jar_bytes) {
		Class<?> caller = JavaLang.getOuterCallerClass();
		return loadKlass(caller.getClassLoader(), JarFiles.getJarInputStream(jar_bytes));
	}

	public static ClassLoader loadKlass(String jar_path) {
		Class<?> caller = JavaLang.getOuterCallerClass();
		byte[] jar_bytes = JarFiles.getResourceAsBytes(caller, jar_path);
		return loadKlass(caller.getClassLoader(), JarFiles.getJarInputStream(jar_bytes));
	}

	/**
	 * 从调用该方法的类所属的ClassLoader加载目标jar中指定路径下的类
	 * 
	 * @param jar
	 * @param package_name
	 * @param include_subpackage
	 */
	public static ClassLoader loadKlass(InputStream jar, String package_name, boolean include_subpackage) {
		Class<?> caller = JavaLang.getOuterCallerClass();
		return loadKlass(caller.getClassLoader(), jar, package_name, include_subpackage);
	}

	public static ClassLoader loadKlass(byte[] jar_bytes, String package_name, boolean include_subpackage) {
		Class<?> caller = JavaLang.getOuterCallerClass();
		return loadKlass(caller.getClassLoader(), JarFiles.getJarInputStream(jar_bytes), package_name, include_subpackage);
	}

	public static ClassLoader loadKlass(String jar_path, String package_name, boolean include_subpackage) {
		Class<?> caller = JavaLang.getOuterCallerClass();
		byte[] jar_bytes = JarFiles.getResourceAsBytes(caller, jar_path);
		return loadKlass(caller.getClassLoader(), JarFiles.getJarInputStream(jar_bytes), package_name, include_subpackage);
	}
}
