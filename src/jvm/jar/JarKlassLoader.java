package jvm.jar;

import java.io.InputStream;

import jvm.klass.KlassLoader;
import jvm.lang.JavaLang;

public class JarKlassLoader {
	/**
	 * 加载指定jar中的所有类
	 * 
	 * @param loader
	 * @param jar
	 */
	public static ClassLoader newClassLoaderFor(ClassLoader loader, InputStream jar) {
		return KlassLoader.newClassLoaderFor(loader, JarFiles.collectClass(jar));
	}

	public static ClassLoader newClassLoaderFor(ClassLoader loader, byte[] jar_bytes) {
		return newClassLoaderFor(loader, JarFiles.getJarInputStream(jar_bytes));
	}

	/**
	 * 加载jar子包中的类
	 * 
	 * @param loader
	 * @param jar
	 * @param package_name
	 * @param include_subpackage
	 */
	public static ClassLoader newClassLoaderFor(ClassLoader loader, InputStream jar, String package_name, boolean include_subpackage) {
		return KlassLoader.newClassLoaderFor(loader, JarFiles.collectClass(jar, package_name, include_subpackage));
	}

	public static ClassLoader newClassLoaderFor(ClassLoader loader, byte[] jar_bytes, String package_name, boolean include_subpackage) {
		return newClassLoaderFor(loader, JarFiles.getJarInputStream(jar_bytes));
	}

	/**
	 * 从调用该方法的类所属的ClassLoader加载目标jar中的全部类
	 * 
	 * @param jar
	 */
	public static ClassLoader newClassLoaderFor(InputStream jar) {
		Class<?> caller = JavaLang.getOuterCallerClass();
		return newClassLoaderFor(caller.getClassLoader(), jar);
	}

	public static ClassLoader newClassLoaderFor(byte[] jar_bytes) {
		Class<?> caller = JavaLang.getOuterCallerClass();
		return newClassLoaderFor(caller.getClassLoader(), JarFiles.getJarInputStream(jar_bytes));
	}

	/**
	 * 从调用该方法的类所属的ClassLoader加载目标jar中指定路径下的类
	 * 
	 * @param jar
	 * @param package_name
	 * @param include_subpackage
	 */
	public static ClassLoader newClassLoaderFor(InputStream jar, String package_name, boolean include_subpackage) {
		Class<?> caller = JavaLang.getOuterCallerClass();
		return newClassLoaderFor(caller.getClassLoader(), jar, package_name, include_subpackage);
	}

	public static ClassLoader newClassLoaderFor(byte[] jar_bytes, String package_name, boolean include_subpackage) {
		Class<?> caller = JavaLang.getOuterCallerClass();
		return newClassLoaderFor(caller.getClassLoader(), JarFiles.getJarInputStream(jar_bytes), package_name, include_subpackage);
	}
}
