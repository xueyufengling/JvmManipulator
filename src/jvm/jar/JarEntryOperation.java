package jvm.jar;

import java.io.ByteArrayOutputStream;
import java.util.jar.JarEntry;

@FunctionalInterface
public interface JarEntryOperation {
	/**
	 * 遍历处理JarEntry
	 * 
	 * @param entry
	 * @param bytes
	 * @return 在collect时是否收集该Entry
	 */
	public boolean operate(JarEntry entry, ByteArrayOutputStream bytes);

	@FunctionalInterface
	public static interface File {
		/**
		 * 单个文件处理
		 * 
		 * @param file_dir
		 * @param file_name
		 * @param entry
		 * @param bytes
		 */
		public boolean operate(String file_dir, String file_name, JarEntry entry, ByteArrayOutputStream bytes);
	}

	@FunctionalInterface
	public static interface Class {
		/**
		 * 单个文件处理
		 * 
		 * @param class_full_name
		 * @param entry
		 * @param bytes
		 */
		public boolean operate(String class_full_name, JarEntry entry, ByteArrayOutputStream bytes);
	}
}
