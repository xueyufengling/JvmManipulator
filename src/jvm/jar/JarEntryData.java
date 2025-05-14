package jvm.jar;

import java.util.jar.JarEntry;

public class JarEntryData {
	public final String file_dir;
	public final String file_name;
	public final JarEntry entry;
	public final byte[] data;

	public JarEntryData(String file_dir, String file_name, JarEntry entry, byte[] data) {
		this.file_dir = file_dir;
		this.file_name = file_name;
		this.entry = entry;
		this.data = data;
	}

	public JarEntryData(JarEntry entry, byte[] data) {
		String path = entry.getName();
		int sep = path.lastIndexOf('/');
		this.file_dir = sep == -1 ? null : path.substring(0, sep);
		this.file_name = path.substring(sep + 1);
		this.entry = entry;
		this.data = data;
	}

	public static JarEntryData from(String file_dir, String file_name, JarEntry entry, byte[] data) {
		return new JarEntryData(file_dir, file_name, entry, data);
	}

	public static JarEntryData from(JarEntry entry, byte[] data) {
		return new JarEntryData(entry, data);
	}
}
