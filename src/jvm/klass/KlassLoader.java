package jvm.klass;

import java.lang.reflect.Method;
import java.security.ProtectionDomain;

import jvm.lang.JavaLang;

public class KlassLoader {
	private static Method ClassLoader_m_defineClass;

	static {
		ClassLoader_m_defineClass = Reflect.getMethod(ClassLoader.class, "defineClass", new Class<?>[] { String.class, byte[].class, int.class, int.class, ProtectionDomain.class });
	}

	/**
	 * 从指定的dClassLoader加载class
	 * 
	 * @param loader
	 * @param name
	 * @param b
	 * @param off
	 * @param len
	 * @param protectionDomain
	 * @return
	 * @throws ClassFormatError
	 */
	public static final Class<?> defineClass(ClassLoader loader, String name, byte[] b, int off, int len, ProtectionDomain protectionDomain) throws ClassFormatError {
		return (Class<?>) ObjectManipulator.invoke(loader, ClassLoader_m_defineClass, name, b, off, len, protectionDomain);
	}

	/**
	 * 从指定stackSkip对应的类的ClassLoader加载class
	 * 
	 * @param stackSkip
	 * @param name
	 * @param b
	 * @param off
	 * @param len
	 * @param protectionDomain
	 * @return
	 * @throws ClassFormatError
	 */
	public static final Class<?> defineClass(int stackSkip, String name, byte[] b, int off, int len, ProtectionDomain protectionDomain) throws ClassFormatError {
		return defineClass(JavaLang.trackStackClass(stackSkip).getClassLoader(), name, b, off, len, protectionDomain);
	}

	/**
	 * 从指定的dClassLoader加载class
	 * 
	 * @param loader
	 * @param name
	 * @param b
	 * @param off
	 * @param len
	 * @return
	 * @throws ClassFormatError
	 */
	public static final Class<?> defineClass(ClassLoader loader, String name, byte[] b, int off, int len) throws ClassFormatError {
		return (Class<?>) ObjectManipulator.invoke(loader, ClassLoader_m_defineClass, name, b, off, len, null);
	}

	public static final Class<?> defineClass(ClassLoader loader, String name, byte[] b) throws ClassFormatError {
		return defineClass(loader, name, b, 0, b.length);
	}

	/**
	 * 从指定stackSkip对应的类的ClassLoader加载class
	 * 
	 * @param stackSkip
	 * @param name
	 * @param b
	 * @param off
	 * @param len
	 * @return
	 * @throws ClassFormatError
	 */
	public static final Class<?> defineClass(int stackSkip, String name, byte[] b, int off, int len) throws ClassFormatError {
		return defineClass(JavaLang.trackStackClass(stackSkip).getClassLoader(), name, b, off, len);
	}

	public static final Class<?> defineClass(int stackSkip, String name, byte[] b) throws ClassFormatError {
		return defineClass(stackSkip, name, b, 0, b.length);
	}

	public static ClassLoader getCallerClassLoader() {
		return JavaLang.getCallerClass().getClassLoader();
	}

	public static ClassLoader getOuterCallerClassLoader() {
		return JavaLang.getOuterCallerClass().getClassLoader();
	}

	public static ClassLoader getStackClassClassLoader(int skip) {
		return JavaLang.trackStackClass(skip).getClassLoader();
	}
}
