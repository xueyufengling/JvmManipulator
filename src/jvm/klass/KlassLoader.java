package jvm.klass;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.HashMap;

import jvm.lang.JavaLang;
import jvm.lang.Reflect;

public class KlassLoader {
	public static final String ClassExtensionName = ".class";
	private static Method ClassLoader_m_defineClass;

	static {
		ClassLoader_m_defineClass = Reflect.getMethod(ClassLoader.class, "defineClass", new Class<?>[] { String.class, byte[].class, int.class, int.class, ProtectionDomain.class });
	}

	public static ClassLoader newClassLoaderFor(ClassLoader loader, HashMap<String, byte[]> undefinedKlass) {
		return new ClassLoader(loader) {
			private HashMap<String, byte[]> klassDefs = undefinedKlass;

			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] byte_code = klassDefs.get(name);
				if (byte_code == null)
					throw new ClassNotFoundException(name);
				return defineClass(name, byte_code, 0, byte_code.length);
			}
		};
	}

	public static ClassLoader newClassLoaderFor(HashMap<String, byte[]> undefinedKlass) {
		Class<?> caller = JavaLang.getOuterCallerClass();
		return newClassLoaderFor(caller.getClassLoader(), undefinedKlass);
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
		return defineClass(JavaLang.trackStackFrameClass(stackSkip).getClassLoader(), name, b, off, len, protectionDomain);
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
	 * @CallerSensitive
	 */
	public static final Class<?> defineClass(int stackSkip, String name, byte[] b, int off, int len) throws ClassFormatError {
		return defineClass(JavaLang.trackStackFrameClass(stackSkip).getClassLoader(), name, b, off, len);
	}

	public static final Class<?> defineClass(int stackSkip, String name, byte[] b) throws ClassFormatError {
		return defineClass(stackSkip, name, b, 0, b.length);
	}

	public static ClassLoader getCallerClassLoader() {
		return JavaLang.getOuterCallerClass().getClassLoader();
	}

	public static ClassLoader getStackClassClassLoader(int skip) {
		return JavaLang.trackStackFrameClass(skip + 1).getClassLoader();
	}

	/**
	 * 获取class的URL location
	 * 
	 * @param clazz
	 * @return
	 */
	@Deprecated
	public static String klassCodeSourceLocation(Class<?> clazz) {
		URL location = clazz.getProtectionDomain().getCodeSource().getLocation();
		if (location != null) {
			try {
				return URLDecoder.decode(location.getPath(), StandardCharsets.UTF_8.name());
			} catch (UnsupportedEncodingException ex) {
				throw new AssertionError("UTF-8 not supported", ex);
			}
		}
		return null;
	}

	/**
	 * 获取指定class的文件所在目录URI，可能是本地class文件，也可能是jar打包的class文件<br>
	 * 例如Minecraft模组BlueArchive: Rendezvous的模组jar中主类URI路径为/D:/JavaProjects/testClient/.minecraft/mods/ba-1.0.0.jar#191!/ba<br>
	 * 其中!为Java URL的分隔符，前面是jar包路径，后面是jar包内的路径。<br>
	 * #191为FML的ClassLoader自行添加的标识符，不同框架添加的标识符可能不一样，也可能不额外添加标识符<br>
	 * 
	 * @param any_class
	 * @return
	 */
	public static String localKlassLocationUri(Class<?> any_class) {
		String path = null;
		if (any_class.getResource(any_class.getSimpleName() + ClassExtensionName) == null)// 目标class的文件找不到
			return null;
		try {
			if (any_class.getClassLoader() == null)
				path = ClassLoader.getSystemResource("").toURI().getPath();
			else
				path = any_class.getResource("").toURI().getPath();
		} catch (URISyntaxException ex) {
			ex.printStackTrace();
		}
		return path;
	}

	/**
	 * 传入jar中的一个类，获取对应的jar绝对路径，包括BootstrapClassLoader加载的jar
	 * 
	 * @param any_class_in_jar jar内的任意一个类
	 * @return class的本地文件路径；当class在jar内时返回jar的绝对路径
	 */
	public static String localKlassLocation(Class<?> any_class, UriPath.Resolver resolver) {
		return UriPath.resolve(localKlassLocationUri(any_class), resolver).filesystem_path;
	}

	public static String localKlassLocation(Class<?> any_class) {
		return localKlassLocation(any_class, UriPath.Resolver.DEFAULT);
	}
}
