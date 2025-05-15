package jvm.klass;

public class UriPath {
	public final String filesystem_path;
	/**
	 * JarEntry内的路径，有可能是嵌套的，嵌套依然以!分割
	 */
	public final String entry_path;

	public static final String JAR_FILE_URL_HEADER = "jar:";
	public static final String FILE_URL_HEADER = "file:";

	UriPath(String filesystem_path, String entry_path) {
		this.filesystem_path = filesystem_path;
		this.entry_path = entry_path;
	}

	UriPath(String filesystem_path) {
		this(filesystem_path, null);
	}

	public static UriPath from(String filesystem_path, String entry_path) {
		return new UriPath(filesystem_path, entry_path);
	}

	public static UriPath from(String filesystem_path) {
		return new UriPath(filesystem_path);
	}

	public static UriPath resolve(String uri, Resolver resolver) {
		return resolver.resolve(uri);
	}

	@FunctionalInterface
	public static interface Resolver {
		public UriPath resolve(String uri);

		public static final Resolver STD = (String uri) -> {
			String filesystem_path = uri;
			String entry_path = null;
			if (uri.startsWith(JAR_FILE_URL_HEADER)) {// 该class在jar内
				int path_sep_idx = uri.indexOf('!');
				filesystem_path = uri.substring(JAR_FILE_URL_HEADER.length(), path_sep_idx);
				entry_path = uri.substring(path_sep_idx + 1);
			}
			if (filesystem_path.startsWith(FILE_URL_HEADER))
				filesystem_path = uri.substring(FILE_URL_HEADER.length());
			return UriPath.from(filesystem_path, entry_path);
		};

		public static final Resolver FML = (String uri) -> {
			UriPath std_path = STD.resolve(uri);
			String filesystem_path = std_path.filesystem_path;
			String entry_path = std_path.entry_path;
			int ext_idx = filesystem_path.lastIndexOf('#');
			if (ext_idx != -1)// 如果添加了额外标识符
				filesystem_path = filesystem_path.substring(0, ext_idx);
			return UriPath.from(filesystem_path, entry_path);
		};

		public static Resolver DEFAULT = FML;
	}
}
