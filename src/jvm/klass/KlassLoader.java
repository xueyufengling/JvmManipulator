package jvm.klass;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.HashMap;

import jvm.lang.JavaLang;
import jvm.lang.Reflection;

public class KlassLoader {
	public static final String ClassExtensionName = ".class";
	private static Method ClassLoader_m_defineClass;
	private static Method ClassLoader_m_findClass;
	private static Field ClassLoader_f_parent;
	private static Field Class_f_classLoader;

	public static final String default_parent_field_name = "parent";

	static {
		JavaLang.noReflectionFieldFilter(() -> {
			ClassLoader_m_defineClass = Reflection.getMethod(ClassLoader.class, "defineClass", new Class<?>[] { String.class, byte[].class, int.class, int.class, ProtectionDomain.class });
			ClassLoader_m_findClass = Reflection.getMethod(ClassLoader.class, "findClass", new Class<?>[] { String.class });
			ClassLoader_f_parent = Reflection.getField(ClassLoader.class, "parent");
			Class_f_classLoader = Reflection.getField(Class.class, "classLoader");
		});
	}

	public static class Proxy extends ClassLoader {
		private ClassLoader son;
		ByteCodeSource bytecodeSource;
		private boolean reverseLoading = false;// 记录是否已经开始向下查找，防止在整个加载链中都查找不到无限循环loadClass()

		public Proxy(ClassLoader dest, String dest_parent_field_name, ByteCodeSource source) {
			super(KlassLoader.getClassLoaderParent(dest, dest_parent_field_name));
			KlassLoader.setClassLoaderParent(dest, dest_parent_field_name, this);
			this.son = dest;
			this.bytecodeSource = source;
		}

		/**
		 * Proxy将插入双亲委托加载链的dest的上方
		 * 
		 * @param dest           目标ClassLoader
		 * @param undefinedKlass 要加载的字节码
		 */
		public Proxy(ClassLoader dest, String dest_parent_field_name, HashMap<String, byte[]> undefinedKlass) {
			this(dest, dest_parent_field_name, ByteCodeSource.Map.from(undefinedKlass));
		}

		public Proxy(ClassLoader dest, ByteCodeSource source) {
			this(dest, default_parent_field_name, source);
		}

		public Proxy(ClassLoader dest, HashMap<String, byte[]> undefinedKlass) {
			this(dest, default_parent_field_name, undefinedKlass);
		}

		@Override
		protected Class<?> findClass(String name) throws ClassNotFoundException {
			byte[] byte_code = bytecodeSource.genByteCode(name);
			// 代理及之上的类加载器找不到类定义时，则让子类加载器son去加载目标类，这样动态加载的类就可以引用son加载的类
			if (byte_code == null) {
				if (reverseLoading) {
					reverseLoading = false;
					return null;
				} else {
					reverseLoading = true;
					return son.loadClass(name);
				}
			}
			return defineClass(name, byte_code, 0, byte_code.length);
		}

		public final Class<?> load(String className) {
			try {
				return son.loadClass(className);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
			return null;
		}

		/**
		 * @param dest
		 * @param undefinedKlass
		 * @return
		 */
		public static final Proxy addFor(ClassLoader dest, HashMap<String, byte[]> undefinedKlass) {
			return new Proxy(dest, undefinedKlass);
		}

		public static final Proxy addFor(ClassLoader dest, String dest_parent_field_name, HashMap<String, byte[]> undefinedKlass) {
			return new Proxy(dest, dest_parent_field_name, undefinedKlass);
		}

		public static final Proxy addFor(ClassLoader dest, ByteCodeSource source) {
			return new Proxy(dest, source);
		}

		public static final Proxy addFor(ClassLoader dest, String dest_parent_field_name, ByteCodeSource source) {
			return new Proxy(dest, dest_parent_field_name, source);
		}
	}

	/**
	 * 将undefinedKlass委托给父类为loader的新自定义ClassLoader加载。<br>
	 * 注意，类加载的起点始终是手动调用的Class.forName(name,init,classLoader)、classLoader.loadClass(name)或直接使用该类型，例如直接在代码中使用{@code A a=new A();}的上下文的ClassLoader。<br>
	 * 类搜寻只会从起点开始，一直向上查找直到BootstrapClassLoader，而不会去查找起点ClassLoader的子代ClassLoader。<br>
	 * 因此，调用该方法返回的ClassLoader需要用户手动加载相关类，并且用反射使用加载的类，不能直接在代码中使用这些类型。<br>
	 * 
	 * @param loader
	 * @param undefinedKlass
	 * @return
	 */
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
	 * 解析父类加载器的字段
	 * 
	 * @param target
	 * @param dest_parent_field_name
	 * @return
	 */
	private static final Field resolveParentLoaderField(ClassLoader target, String dest_parent_field_name) {
		return dest_parent_field_name.equals(default_parent_field_name) ? ClassLoader_f_parent : JavaLang.fieldNoReflectionFilter(target.getClass(), dest_parent_field_name);
	}

	/**
	 * 为ClassLoader设置父类加载器
	 * 
	 * @param target                 目标ClassLoader
	 * @param dest_parent_field_name 目标ClassLoader使用的父类加载器的字段名，当没有使用ClassLoader.parent成员构建加载委托链时需要指定实际的字段名称
	 * @param parent
	 * @return
	 */
	public static ClassLoader setClassLoaderParent(ClassLoader target, String dest_parent_field_name, ClassLoader parent) {
		ObjectManipulator.setObject(target, resolveParentLoaderField(target, dest_parent_field_name), parent);
		return target;
	}

	public static ClassLoader setClassLoaderParent(ClassLoader target, ClassLoader parent) {
		return setClassLoaderParent(target, default_parent_field_name, parent);
	}

	/**
	 * 不经过安全检查直接获取parent
	 * 
	 * @param target
	 * @param parent
	 * @return
	 */
	public static ClassLoader getClassLoaderParent(ClassLoader target, String dest_parent_field_name) {
		return (ClassLoader) ObjectManipulator.access(target, resolveParentLoaderField(target, dest_parent_field_name));
	}

	public static ClassLoader getClassLoaderParent(ClassLoader target) {
		return getClassLoaderParent(target, default_parent_field_name);
	}

	/**
	 * 设置Class的classLoader变量
	 * 
	 * @param cls
	 * @param loader
	 * @return
	 */
	public static Class<?> setClassLoader(Class<?> cls, ClassLoader loader) {
		ObjectManipulator.setObject(cls, Class_f_classLoader, loader);
		return cls;
	}

	/**
	 * 不经过安全检查直接获取classLoader
	 * 
	 * @param target
	 * @param parent
	 * @return
	 */
	public static ClassLoader getClassLoader(Class<?> cls) {
		return (ClassLoader) ObjectManipulator.access(cls, Class_f_classLoader);
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

	/**
	 * 查找类
	 * 
	 * @param stackSkip
	 * @param name
	 * @param b
	 * @return
	 * @throws ClassFormatError
	 */
	public static final Class<?> findClass(ClassLoader loader, String name) throws ClassNotFoundException {
		return (Class<?>) ObjectManipulator.invoke(loader, ClassLoader_m_findClass, name);
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
