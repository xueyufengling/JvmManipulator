package jvm.jar;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import jvm.klass.Reflect;
import jvm.lang.JavaLang;

/**
 * jar文件的相关操作，任何操作都需要传入{@code any_class_in_jar}，即jar内的任意一个类。<br>
 * <p>如果使用没有{@code any_class_in_jar}参数的方法，那么将获取调用者所在类作为{@code any_class_in_jar}参数
 */
public class JarFiles {

	/**
	 * 获取已经加载的包列表
	 * 
	 * @return
	 */
	public static String[] getLoadedPackageNames() {
		Package[] packages = JavaLang.getOuterCallerClass().getClassLoader().getDefinedPackages();// 获取调用该方法的类
		if (packages == null)
			return null;
		String[] package_names = new String[packages.length];
		for (int i = 0; i < packages.length; ++i) {
			package_names[i] = packages[i].getName();
		}
		return package_names;
	}

	public static byte[] getJarResourceAsBytes(Class<?> any_class_in_jar, String path) {
		return getJarResourceAsBytes(getJarFilePath(any_class_in_jar), path);
	}

	public static byte[] getJarResourceAsBytes(String path) {
		return getJarResourceAsBytes(getJarFilePath(JavaLang.getOuterCallerClass()), path);// 获取调用该方法的类
	}

	/**
	 * 从文件系统中读取jar文件并获取资源字节
	 * 
	 * @param jar_path
	 * @param path
	 * @return
	 */
	public static byte[] getJarResourceAsBytes(String jar_path, String path) {
		byte[] bytes = null;
		try (JarFile jar = new JarFile(jar_path);) {
			JarEntry entry = jar.getJarEntry(path);
			if (entry != null) {
				InputStream input_stream = jar.getInputStream(entry);
				bytes = input_stream.readAllBytes();
				input_stream.close();
			}
		} catch (IOException ex) {
			System.err.println("Cannt read the jar file in path " + path);
			ex.printStackTrace();
		}
		return bytes;
	}

	/**
	 * 传入jar中的一个类，获取对应的jar绝对路径，包括BootstrapClassLoader加载的jar
	 * 
	 * @param any_class_in_jar jar内的任意一个类
	 * @return jar的绝对路径
	 */
	public static String getJarFilePath(Class<?> any_class_in_jar) {
		String path = null;
		if (any_class_in_jar.getClassLoader() == null)// 如果是BootstrapClassLoader加载的则直接获取对应的系统加载jar路径
			path = ClassLoader.getSystemResource("").getPath();
		else
			path = any_class_in_jar.getResource("").getPath();
		return path.substring(5, path.lastIndexOf('!'));
	}

	public static String getJarFilePath() {
		return getJarFilePath(JavaLang.getOuterCallerClass());// 获取调用该方法的类
	}

	/**
	 * 获取jar文件中指定Java包下的所有类名（含包名）
	 * 
	 * @param any_class_in_package jar包内的任意一个类，这是为了获取加载jar包内加载class文件的ClassLoader
	 * @param package_name         要获取的包名
	 * @param include_subpackage   是否获取该包及其所有递归子包的类名称
	 * @return 类名数组
	 */
	public static List<String> getClassNamesInJarPackage(Class<?> any_class_in_package, String package_name, boolean include_subpackage) {
		List<String> class_names = new ArrayList<>();
		try (JarFile jar = new JarFile(getJarFilePath(any_class_in_package));) {
			for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
				JarEntry entry = entries.nextElement();
				if (entry.isDirectory())// 文件夹直接略过
					continue;
				String file_name = entry.getName();
				if (package_name == null || package_name == "") {// 如果要查找的包是顶部空间
					if (file_name.lastIndexOf('/') == -1 && file_name.endsWith(".class"))
						class_names.add(file_name.substring(0, file_name.length() - 6));
				} else {
					if (file_name.lastIndexOf('/') != -1) {
						if (include_subpackage) {
							String name = file_name.replace('/', '.');
							if (name.startsWith(package_name) && file_name.endsWith(".class"))
								class_names.add(name.substring(0, file_name.length() - 6));
						} else {
							if (file_name.substring(0, file_name.lastIndexOf('/')).equals(package_name.replace('.', '/')) && file_name.endsWith(".class"))
								class_names.add(package_name + '.' + file_name.substring(package_name.length() + 1, file_name.length() - 6));
						}
					}
				}
			}
		} catch (IOException ex) {
			System.err.println("Cannt read the jar file of package " + package_name);
			ex.printStackTrace();
		}
		return class_names;
	}

	public static List<String> getClassNamesInJarPackage(String package_name, boolean include_subpackage) {
		return getClassNamesInJarPackage(JavaLang.getOuterCallerClass(), package_name, include_subpackage);// 获取调用该方法的类
	}

	public static List<String> getClassNamesInJarPackage(Class<?> any_class_in_package, String package_name) {
		return getClassNamesInJarPackage(any_class_in_package, package_name, false);
	}

	public static List<String> getClassNamesInJarPackage(String package_name) {
		return getClassNamesInJarPackage(JavaLang.getOuterCallerClass(), package_name);// 获取调用该方法的类
	}

	/**
	 * 获取jar文件中指定Java包下的所有类
	 * 
	 * @param any_class_in_package jar包内的任意一个类，这是为了获取加载jar包内加载class文件的ClassLoader
	 * @param package_name         要获取的包名
	 * @param include_subpackage   是否获取该包及其所有递归子包的类名称
	 * @return 包名数组
	 */
	public static List<Class<?>> getClassInJarPackage(Class<?> any_class_in_package, String package_name, boolean include_subpackage) {
		List<Class<?>> class_list = new ArrayList<>();
		List<String> class_names = getClassNamesInJarPackage(any_class_in_package, package_name, include_subpackage);
		ClassLoader class_loader = any_class_in_package.getClassLoader();
		for (String class_name : class_names)
			try {
				class_list.add(class_loader.loadClass(class_name));
			} catch (ClassNotFoundException ex) {
				System.err.println("Cannt find class " + class_name + " the jar file of package " + package_name);
				ex.printStackTrace();
			}
		return class_list;
	}

	public static List<Class<?>> getClassInJarPackage(String package_name, boolean include_subpackage) {
		return getClassInJarPackage(JavaLang.getOuterCallerClass(), package_name, include_subpackage);// 获取调用该方法的类
	}

	public static List<Class<?>> getClassInJarPackage(Class<?> any_class_in_package, String package_name) {
		return getClassInJarPackage(any_class_in_package, package_name, false);
	}

	public static List<Class<?>> getClassInJarPackage(String package_name) {
		return getClassInJarPackage(JavaLang.getOuterCallerClass(), package_name);// 获取调用该方法的类
	}

	/**
	 * 获取jar文件中指定Java包下的所有具有指定超类的类
	 * 
	 * @param any_class_in_package jar包内的任意一个类，这是为了获取加载jar包内加载class文件的ClassLoader
	 * @param package_name         要获取的包名
	 * @param include_subpackage   是否获取该包及其所有递归子包的类名称
	 * @return 包名数组
	 */
	public static List<Class<?>> getSubClassInJarPackage(Class<?> any_class_in_package, String package_name, Class<?> super_class, boolean include_subpackage) {
		List<Class<?>> specified_class_list = new ArrayList<>();
		List<Class<?>> class_list = getClassInJarPackage(any_class_in_package, package_name, include_subpackage);
		for (Class<?> clazz : class_list)
			if (Reflect.hasSuperClass(clazz, super_class))
				specified_class_list.add(clazz);
		return specified_class_list;
	}

	public static List<Class<?>> getSubClassInJarPackage(String package_name, Class<?> super_class, boolean include_subpackage) {
		return getSubClassInJarPackage(JavaLang.getOuterCallerClass(), package_name, super_class, include_subpackage);// 获取调用该方法的类
	}

	public static List<Class<?>> getSubClassInJarPackage(Class<?> any_class_in_package, String package_name, Class<?> super_class) {
		return getSubClassInJarPackage(any_class_in_package, package_name, super_class, false);
	}

	public static List<Class<?>> getSubClassInJarPackage(String package_name, Class<?> super_class) {
		return getSubClassInJarPackage(JavaLang.getOuterCallerClass(), package_name, super_class);// 获取调用该方法的类
	}
}
