package jvm.jar;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

import jvm.klass.KlassLoader;
import jvm.klass.UriPath;
import jvm.lang.JavaLang;
import jvm.lang.Reflection;

/**
 * jar文件的相关操作，任何操作都需要传入{@code any_class_in_jar}，即jar内的任意一个类。<br>
 * <p>
 * 如果使用没有{@code any_class_in_jar}参数的方法，那么将获取调用者所在类作为{@code any_class_in_jar}参数
 */
public class JarFiles {

	/**
	 * 获取已经加载的包列表
	 * 
	 * @return
	 */
	public static String[] getLoadedPackageNames() {
		Class<?> caller = JavaLang.getOuterCallerClass();
		Package[] packages = caller.getClassLoader().getDefinedPackages();// 获取调用该方法的类
		if (packages == null)
			return null;
		String[] package_names = new String[packages.length];
		for (int i = 0; i < packages.length; ++i) {
			package_names[i] = packages[i].getName();
		}
		return package_names;
	}

	// ------------------------------------------------------------ Internal Utils ----------------------------------------------------------------------------

	/**
	 * 从InputStream中获取指定path的JarEntry
	 * 
	 * @param jar  必须是新流，指针offset在0
	 * @param path
	 * @return
	 * @throws IOException
	 */
	@Deprecated
	@SuppressWarnings("unused")
	private static JarEntry getJarEntry(InputStream jar, String path) throws IOException {
		JarEntry entry = null;
		try (JarInputStream jar_stream = new JarInputStream(jar)) {
			while ((entry = jar_stream.getNextJarEntry()) != null) {
				if (entry.getName().equals(path))
					break;
			}
		}
		return entry;
	}

	/**
	 * 读取JarInputStream中的指定entry的内容
	 * 
	 * @param jar         必须是新流，指针offset在0
	 * @param buffer_size 读取缓冲区大小
	 * @return
	 * @throws IOException
	 */
	@Deprecated
	public static ByteArrayOutputStream getJarEntryBytes(JarInputStream jar_stream, int buffer_size) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buffer = new byte[buffer_size];
		int read = 0;
		while ((read = jar_stream.read(buffer)) != -1) {
			bos.write(buffer, 0, read);
		}
		return bos;
	}

	public static final int DEFAULT_BUFFER_SIZE = 1024;

	/**
	 * 读取jar InputStream中指定path的数据
	 * 
	 * @param jar  必须是新流，指针offset在0
	 * @param path
	 * @return
	 * @throws IOException
	 */
	@Deprecated
	public static JarEntryData readJarEntry(InputStream jar, String path) throws IOException {
		JarEntryData data = null;
		try (JarInputStream jar_stream = new JarInputStream(jar)) {
			JarEntry entry = null;
			while ((entry = jar_stream.getNextJarEntry()) != null) {
				if (entry.getName().equals(path))
					data = JarEntryData.from(entry, getJarEntryBytes(jar_stream, DEFAULT_BUFFER_SIZE).toByteArray());
			}
		}
		return data;
	}

	// -------------------------------------------------------- forEach Operations --------------------------------------------------------------------

	// foreach函数族
	/**
	 * 遍历每个JarEntry
	 * 
	 * @param jar
	 * @param op
	 */
	public static void foreachEntries(InputStream jar, JarEntryOperation op) {
		try (JarInputStream jar_stream = new JarInputStream(jar)) {
			JarEntry entry = null;
			while ((entry = jar_stream.getNextJarEntry()) != null) {
				op.operate(entry, getJarEntryBytes(jar_stream, DEFAULT_BUFFER_SIZE));
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * 遍历每个文件
	 * 
	 * @param jar
	 * @param op
	 */
	public static void foreachFiles(InputStream jar, JarEntryOperation.File op) {
		foreachEntries(jar, new JarEntryOperation() {
			@Override
			public boolean operate(JarEntry entry, ByteArrayOutputStream bytes) {
				if (!entry.isDirectory()) {
					String path = entry.getName();
					int sep = path.lastIndexOf('/');
					op.operate(sep == -1 ? null : path.substring(0, sep), path.substring(sep + 1), entry, bytes);
				}
				return true;
			}
		});
	}

	/**
	 * 从指定目录开始遍历每个文件
	 * 
	 * @param jar
	 * @param start_path         开始遍历的目录
	 * @param include_subpackage 是否遍历子目录
	 * @param op
	 */
	public static void foreachFiles(InputStream jar, String start_path, boolean include_subpackage, JarEntryOperation.File op) {
		foreachFiles(jar, new JarEntryOperation.File() {
			@Override
			public boolean operate(String file_dir, String file_name, JarEntry entry, ByteArrayOutputStream bytes) {
				if (isRootDir(start_path)) {
					if (isRootDir(file_dir))
						op.operate(file_dir, file_name, entry, bytes);
				} else {
					if (file_dir.startsWith(start_path))
						op.operate(file_dir, file_name, entry, bytes);
				}
				return true;
			}
		});
	}

	public static void filterFilesRegex(InputStream jar, String start_path, boolean include_subpackage, String regex, JarEntryOperation.File op) {
		foreachFiles(jar, start_path, include_subpackage, new JarEntryOperation.File() {
			@Override
			public boolean operate(String file_dir, String file_name, JarEntry entry, ByteArrayOutputStream bytes) {
				if (file_name.matches(regex))
					op.operate(file_dir, file_name, entry, bytes);
				return true;
			}
		});
	}

	// collect函数族

	/**
	 * 按照条件收集Entry
	 * 
	 * @param jar
	 * @param condition 条件，返回true则代表收集，false代表不收集
	 * @return
	 */
	public static List<JarEntryData> collectFiles(InputStream jar, JarEntryOperation.File condition) {
		List<JarEntryData> entries = new ArrayList<>();
		foreachFiles(jar, new JarEntryOperation.File() {
			@Override
			public boolean operate(String file_dir, String file_name, JarEntry entry, ByteArrayOutputStream bytes) {
				boolean reserved = condition.operate(file_dir, file_name, entry, bytes);
				if (reserved)
					entries.add(JarEntryData.from(file_dir, file_name, entry, bytes.toByteArray()));
				return reserved;
			}
		});
		return entries;
	}

	public static List<JarEntryData> collectFiles(InputStream jar, String start_path, boolean include_subpackage, JarEntryOperation.File condition) {
		List<JarEntryData> entries = new ArrayList<>();
		foreachFiles(jar, start_path, include_subpackage, new JarEntryOperation.File() {
			@Override
			public boolean operate(String file_dir, String file_name, JarEntry entry, ByteArrayOutputStream bytes) {
				boolean reserved = condition.operate(file_dir, file_name, entry, bytes);
				if (reserved)
					entries.add(JarEntryData.from(file_dir, file_name, entry, bytes.toByteArray()));
				return reserved;
			}
		});
		return entries;
	}

	// collect函数的具体实现
	public static List<JarEntryData> collectFilesByRegex(InputStream jar, String regex) {
		return collectFiles(jar, (String file_dir, String file_name, JarEntry entry, ByteArrayOutputStream bytes) -> {
			return file_name.matches(regex);
		});
	}

	public static List<JarEntryData> collectFilesByRegex(InputStream jar, String start_path, boolean include_subpackage, String regex) {
		return collectFiles(jar, (String file_dir, String file_name, JarEntry entry, ByteArrayOutputStream bytes) -> {
			return file_name.matches(regex);
		});
	}

	public static List<JarEntryData> collectFilesByType(InputStream jar, String type) {
		return collectFiles(jar, (String file_dir, String file_name, JarEntry entry, ByteArrayOutputStream bytes) -> {
			return file_name.endsWith(type);
		});
	}

	public static List<JarEntryData> collectFilesByType(InputStream jar, String start_path, boolean include_subpackage, String file_type) {
		return collectFiles(jar, (String file_dir, String file_name, JarEntry entry, ByteArrayOutputStream bytes) -> {
			return file_name.endsWith(file_type);
		});
	}

	// filter函数族
	/**
	 * 遍历指定条件的文件并执行操作
	 * 
	 * @param jar
	 * @param condition
	 * @param op
	 */
	public static void filterFiles(InputStream jar, JarEntryOperation.File condition, JarEntryOperation.File op) {
		foreachFiles(jar, new JarEntryOperation.File() {
			@Override
			public boolean operate(String file_dir, String file_name, JarEntry entry, ByteArrayOutputStream bytes) {
				if (condition.operate(file_dir, file_name, entry, bytes))
					op.operate(file_dir, file_name, entry, bytes);
				return true;
			}
		});
	}

	public static void filterFiles(InputStream jar, String start_path, boolean include_subpackage, JarEntryOperation.File condition, JarEntryOperation.File op) {
		foreachFiles(jar, start_path, include_subpackage, new JarEntryOperation.File() {
			@Override
			public boolean operate(String file_dir, String file_name, JarEntry entry, ByteArrayOutputStream bytes) {
				if (condition.operate(file_dir, file_name, entry, bytes))
					op.operate(file_dir, file_name, entry, bytes);
				return true;
			}
		});
	}

	public static void filterFilesByRegex(InputStream jar, String regex, JarEntryOperation.File op) {
		filterFiles(jar, (String file_dir, String file_name, JarEntry entry, ByteArrayOutputStream bytes) -> {
			return file_name.matches(regex);
		}, op);
	}

	public static void filterFilesByRegex(InputStream jar, String start_path, boolean include_subpackage, String regex, JarEntryOperation.File op) {
		filterFiles(jar, start_path, include_subpackage, (String file_dir, String file_name, JarEntry entry, ByteArrayOutputStream bytes) -> {
			return file_name.matches(regex);
		}, op);
	}

	public static void filterFilesByType(InputStream jar, String file_type, JarEntryOperation.File op) {
		filterFiles(jar, (String file_dir, String file_name, JarEntry entry, ByteArrayOutputStream bytes) -> {
			return file_name.endsWith(file_type);
		}, op);
	}

	public static void filterFilesByType(InputStream jar, String start_path, boolean include_subpackage, String file_type, JarEntryOperation.File op) {
		filterFiles(jar, start_path, include_subpackage, (String file_dir, String file_name, JarEntry entry, ByteArrayOutputStream bytes) -> {
			return file_name.endsWith(file_type);
		}, op);
	}

	public static void filterClass(InputStream jar, JarEntryOperation.Class op) {
		filterFilesByType(jar, KlassLoader.ClassExtensionName, (String file_dir, String file_name, JarEntry entry, ByteArrayOutputStream bytes) -> {
			String full_path = entry.getName();
			op.operate(full_path.substring(0, full_path.length() - KlassLoader.ClassExtensionName.length()).replace('/', '.'), entry, bytes);
			return true;
		});
	}

	public static void filterClass(InputStream jar, String start_path, boolean include_subpackage, JarEntryOperation.Class op) {
		filterFilesByType(jar, start_path, include_subpackage, KlassLoader.ClassExtensionName, (String file_dir, String file_name, JarEntry entry, ByteArrayOutputStream bytes) -> {
			String full_path = entry.getName();
			op.operate(full_path.substring(0, full_path.length() - KlassLoader.ClassExtensionName.length()).replace('/', '.'), entry, bytes);
			return true;
		});
	}

	public static HashMap<String, byte[]> collectClass(InputStream... jars) {
		HashMap<String, byte[]> classDefs = new HashMap<>();
		for (InputStream jar : jars)
			filterClass(jar, (String class_full_name, JarEntry entry, ByteArrayOutputStream bytes) -> {
				classDefs.put(class_full_name, bytes.toByteArray());
				return true;
			});
		return classDefs;
	}

	public static HashMap<String, byte[]> collectClass(String start_path, boolean include_subpackage, InputStream... jars) {
		HashMap<String, byte[]> classDefs = new HashMap<>();
		for (InputStream jar : jars)
			filterClass(jar, start_path, include_subpackage, (String class_full_name, JarEntry entry, ByteArrayOutputStream bytes) -> {
				classDefs.put(class_full_name, bytes.toByteArray());
				return true;
			});
		return classDefs;
	}

	// -------------------------------------------------------- Resources ----------------------------------------------------------------------

	public static byte[] getResourceAsBytes(Class<?> any_class_in_jar, String path) {
		byte[] bytes = null;
		try (InputStream res = any_class_in_jar.getResource(path).openStream()) {
			bytes = res.readAllBytes();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		return bytes;
	}

	public static byte[] getResourceAsBytes(String path) {
		Class<?> caller = JavaLang.getOuterCallerClass();
		return getResourceAsBytes(caller, path);// 获取调用该方法的类
	}

	/**
	 * 从JarFile中读取资源
	 * 
	 * @param jar
	 * @param path
	 * @return
	 */
	public static byte[] getResourceAsBytes(JarFile jar, String path) {
		byte[] bytes = null;
		try (jar) {
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
	 * 从文件系统中读取jar文件并获取资源字节
	 * 
	 * @param jar_path
	 * @param path
	 * @return
	 */
	public static byte[] getResourceAsBytes(String jar_path, String path) {
		try {
			return getResourceAsBytes(new JarFile(jar_path), path);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		return null;
	}

	public static byte[] getResourceAsBytes(InputStream jar_bytes, String path) {
		byte[] bytes = null;
		try {
			bytes = readJarEntry(jar_bytes, path).data;
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		return bytes;
	}

	public static byte[] getResourceAsBytes(byte[] jar_bytes, String path) {
		return getResourceAsBytes(new ByteArrayInputStream(jar_bytes), path);
	}

	// --------------------------------------------------------- File System --------------------------------------------------------------------

	/**
	 * 获取any_class_in_jar所在jar的路径
	 * 
	 * @param any_class_in_jar
	 * @param resolver         路径解析器，根据URI分割为合法的文件系统路径和Entry路径
	 * @return
	 */
	public static String getJarFilePath(Class<?> any_class_in_jar, UriPath.Resolver resolver) {
		return KlassLoader.localKlassLocation(any_class_in_jar, resolver);
	}

	public static String getJarFilePath(UriPath.Resolver resolver) {
		Class<?> caller = JavaLang.getOuterCallerClass();// 获取调用该方法的类
		return getJarFilePath(caller, resolver);
	}

	public static String getJarFilePath() {
		Class<?> caller = JavaLang.getOuterCallerClass();// 获取调用该方法的类
		return getJarFilePath(caller, UriPath.Resolver.DEFAULT);
	}

	public static boolean isRootDir(String dir) {
		return dir == null || dir.equals("") || dir.equals("/");
	}

	/**
	 * 获取any_class_in_jar所在jar文件字节流
	 * 
	 * @param any_class_in_jar
	 * @param resolver         路径解析器，根据URI分割为合法的文件系统路径和Entry路径
	 * @return
	 * @throws FileNotFoundException
	 */
	public static InputStream getJarInputStream(Class<?> any_class_in_jar, UriPath.Resolver resolver) throws FileNotFoundException {
		return new FileInputStream(getJarFilePath(any_class_in_jar, resolver));
	}

	public static InputStream getJarInputStream(Class<?> any_class_in_jar) throws FileNotFoundException {
		return getJarInputStream(any_class_in_jar, UriPath.Resolver.DEFAULT);
	}

	public static InputStream getJarInputStream(byte[] bytes) {
		return new ByteArrayInputStream(bytes);
	}

	/**
	 * 获取多个any_class_in_jars类所在jar文件字节流
	 * 
	 * @param any_class_in_jars
	 * @param resolver          路径解析器，根据URI分割为合法的文件系统路径和Entry路径
	 * @return
	 * @throws FileNotFoundException
	 */
	public static InputStream[] getJarsInputStreams(UriPath.Resolver resolver, Class<?>... any_class_in_jars) throws FileNotFoundException {
		InputStream[] streams = new InputStream[any_class_in_jars.length];
		for (int idx = 0; idx < any_class_in_jars.length; ++idx) {
			streams[idx] = new FileInputStream(getJarFilePath(any_class_in_jars[idx], resolver));
		}
		return streams;
	}

	public static InputStream[] getJarsInputStreams(Class<?>... any_class_in_jars) throws FileNotFoundException {
		InputStream[] streams = new InputStream[any_class_in_jars.length];
		for (int idx = 0; idx < any_class_in_jars.length; ++idx) {
			streams[idx] = getJarInputStream(any_class_in_jars[idx], UriPath.Resolver.DEFAULT);
		}
		return streams;
	}

	public static InputStream[] getJarsInputStreams(byte[]... multi_bytes) {
		InputStream[] streams = new InputStream[multi_bytes.length];
		for (int idx = 0; idx < multi_bytes.length; ++idx) {
			streams[idx] = new ByteArrayInputStream(multi_bytes[idx]);
		}
		return streams;
	}

	public static InputStream[] getJarsInputStreams(Class<?> any_class_in_jar, String... entry_paths) {
		InputStream[] streams = new InputStream[entry_paths.length];
		for (int idx = 0; idx < entry_paths.length; ++idx) {
			streams[idx] = getJarInputStream(getResourceAsBytes(any_class_in_jar, entry_paths[idx]));
		}
		return streams;
	}

	// ----------------------------------------------------------- Class ---------------------------------------------------------------------------
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
		try {
			filterClass(getJarInputStream(any_class_in_package), package_name, include_subpackage, (String class_full_name, JarEntry entry, ByteArrayOutputStream bytes) -> {
				class_names.add(class_full_name);
				return true;
			});
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
		}
		return class_names;
	}

	public static List<String> getClassNamesInJarPackage(String package_name, boolean include_subpackage) {
		Class<?> caller = JavaLang.getOuterCallerClass();
		return getClassNamesInJarPackage(caller, package_name, include_subpackage);// 获取调用该方法的类
	}

	public static List<String> getClassNamesInJarPackage(Class<?> any_class_in_package, String package_name) {
		return getClassNamesInJarPackage(any_class_in_package, package_name, false);
	}

	public static List<String> getClassNamesInJarPackage(String package_name) {
		Class<?> caller = JavaLang.getOuterCallerClass();
		return getClassNamesInJarPackage(caller, package_name);// 获取调用该方法的类
	}

	/**
	 * 获取一个已经加载的jar文件中指定Java包下的所有类
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
		Class<?> caller = JavaLang.getOuterCallerClass();
		return getClassInJarPackage(caller, package_name, include_subpackage);// 获取调用该方法的类
	}

	public static List<Class<?>> getClassInJarPackage(Class<?> any_class_in_package, String package_name) {
		return getClassInJarPackage(any_class_in_package, package_name, false);
	}

	public static List<Class<?>> getClassInJarPackage(String package_name) {
		Class<?> caller = JavaLang.getOuterCallerClass();
		return getClassInJarPackage(caller, package_name);// 获取调用该方法的类
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
			if (Reflection.hasSuperClass(clazz, super_class))
				specified_class_list.add(clazz);
		return specified_class_list;
	}

	public static List<Class<?>> getSubClassInJarPackage(String package_name, Class<?> super_class, boolean include_subpackage) {
		Class<?> caller = JavaLang.getOuterCallerClass();
		return getSubClassInJarPackage(caller, package_name, super_class, include_subpackage);// 获取调用该方法的类
	}

	public static List<Class<?>> getSubClassInJarPackage(Class<?> any_class_in_package, String package_name, Class<?> super_class) {
		return getSubClassInJarPackage(any_class_in_package, package_name, super_class, false);
	}

	public static List<Class<?>> getSubClassInJarPackage(String package_name, Class<?> super_class) {
		return getSubClassInJarPackage(JavaLang.getOuterCallerClass(), package_name, super_class);// 获取调用该方法的类
	}
}
