package jvm.asm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ByteCodeManipulator {
	protected byte[] original_bytecode;
	protected ClassWriter class_writer;
	protected ClassReader class_reader;

	protected ArrayList<String> fileds_to_be_removed;
	protected ArrayList<FieldInfo> fileds_to_be_added;
	protected ArrayList<MethodInfo> methods_to_be_removed;
	protected HashMap<MethodInfo, MethodOperator> methods_to_be_modified;

	protected ByteCodeManipulator(byte[] bytecode) {
		original_bytecode = bytecode;
		class_reader = new ClassReader(bytecode);
		class_writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
	}

	public static ByteCodeManipulator load(byte[] bytecode) {
		return new ByteCodeManipulator(bytecode);
	}

	public ByteCodeManipulator removeFields(String... field_names) {
		fileds_to_be_removed.addAll(Arrays.asList(field_names));
		return this;
	}

	public ByteCodeManipulator addFields(String... field_declaration) {
		for (String decl : field_declaration)
			fileds_to_be_added.add(FieldInfo.from(decl));
		return this;
	}

	public ByteCodeManipulator removeMethods(String... method_declarations) {
		for (String decl : method_declarations)
			methods_to_be_removed.add(MethodInfo.from(decl));
		return this;
	}

	public ByteCodeManipulator modifyMethod(String method_declaration, MethodOperator modifier) {
		methods_to_be_modified.put(MethodInfo.from(method_declaration), modifier);
		return this;
	}

	public byte[] toByteCode() {
		class_reader.accept(new ClassVisitor(Opcodes.ASM5, class_writer) {
			@Override
			public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
				if (fileds_to_be_removed.contains(name))
					return null;
				return cv.visitField(access, name, desc, signature, value);
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
				MethodVisitor mv = null;
				for (Entry<MethodInfo, MethodOperator> entry : methods_to_be_modified.entrySet()) {
					MethodInfo info = entry.getKey();
					if (info.getMethodName() == name && info.getMethodDescriptor() == desc) {
						mv = cv.visitMethod(access, name, desc, signature, exceptions);
						mv.visitCode();
						entry.getValue().modify(info, mv);
						mv.visitMaxs(255, 255);
						mv.visitEnd();
					}
				}
				for (MethodInfo info : methods_to_be_removed)
					if (info.getMethodName() == name && info.getMethodDescriptor() == desc)
						return null;
				if (mv == null)
					mv = cv.visitMethod(access, name, desc, signature, exceptions);
				return mv;
			}

			@Override
			public void visitEnd() {
				for (int idx = 0; idx < fileds_to_be_added.size(); ++idx) {
					FieldInfo field_info = fileds_to_be_added.get(idx);
					FieldVisitor fv = cv.visitField(field_info.getAcc(), field_info.getFieldName(), field_info.getTypeDescriptor(), null, null);
					if (fv != null) {
						fv.visitEnd();
					}
				}
			}
		}, ClassReader.SKIP_DEBUG);
		return class_writer.toByteArray();
	}

	/**
	 * 内部名称转换工具
	 */

	/**
	 * 
	 * @param type_name
	 * @return
	 */
	public static String typeToTypeDescriptor(String type_name) {
		return FieldInfo.from(type_name).type_descriptor;
	}

	/**
	 * 非数组类型的名称转换为内部名称，将基本类型转换为内部名称，如果不是基本类型就返回本身
	 * 
	 * @param type_name 类型名称
	 * @return 内部名称
	 */
	protected static String nonarrTypeToTypeDescriptor(String type_name) {
		if (type_name == null || type_name.equals(""))
			return "";
		type_name = type_name.trim();
		switch (type_name) {
		case "boolean":
			return "Z";
		case "char":
			return "C";
		case "byte":
			return "B";
		case "short":
			return "S";
		case "int":
			return "I";
		case "float":
			return "F";
		case "long":
			return "J";
		case "double":
			return "D";
		case "void":
			return "V";
		default:
			return 'L' + type_name.replace('.', '/') + ';';
		}
	}

	public static String methodToMethodDescriptor(String method_signature) {
		return MethodInfo.from(method_signature).method_descriptor;
	}

	public static class FieldInfo {
		int acc;
		String type_descriptor;
		String field_name;

		private FieldInfo(int acc, String type_descriptor, String field_name) {
			this.acc = acc;
			this.type_descriptor = type_descriptor;
			this.field_name = field_name;
		}

		public int getAcc() {
			return acc;
		}

		public String getTypeDescriptor() {
			return type_descriptor;
		}

		public String getFieldName() {
			return field_name;
		}

		public static FieldInfo from(String declaration) {
			int acc = 0;
			String[] info = declaration.trim().split("\s+");
			int arr_dim = 0;
			StringBuilder type_name = new StringBuilder();
			boolean type_name_resolved = false;
			for (int info_idx = 0; info_idx < info.length; ++info_idx) {
				int this_acc = toAcc(info[info_idx]);
				acc += this_acc;
				if (this_acc == 0) {
					char[] chars = info[info_idx].toCharArray();
					for (char ch : chars) {
						if (ch == '[') {
							type_name_resolved = true;
							++arr_dim;
						}
						if (!type_name_resolved)
							type_name.append(ch);
					}
					type_name_resolved = true;// 第一次解析字符串结束时，由于是空格分开的，因此类型名称已经确定好
				}
			}
			StringBuilder type_descriptor = new StringBuilder();
			char[] arr_desp = new char[arr_dim];
			if (arr_desp.length > 0)
				Arrays.fill(arr_desp, '[');
			String type_name_str = type_name.toString();
			String field_name = info[info.length - 1].replaceAll("\\[+", "").replaceAll("\\]+", "");
			type_descriptor.append(arr_desp).append(nonarrTypeToTypeDescriptor(type_name_str));
			return new FieldInfo(acc, type_descriptor.toString(), type_name_str.equals(field_name) ? null : field_name);
		}

		@Override
		public String toString() {
			return "" + acc + ' ' + type_descriptor + ' ' + field_name;
		}
	}

	public static class MethodInfo {
		String clazz;
		int acc = 0;
		String method_descriptor;
		String method_name;

		private MethodInfo(int acc, String method_descriptor, String method_name) {
			this.acc = acc;
			this.method_descriptor = method_descriptor;
			this.method_name = method_name;
		}

		private MethodInfo(int acc, String method_descriptor, String method_name, String clazz) {
			this.acc = acc;
			this.method_descriptor = method_descriptor;
			this.method_name = method_name;
			this.clazz = clazz;
		}

		public int getAcc() {
			return acc;
		}

		public String getMethodDescriptor() {
			return method_descriptor;
		}

		public String getMethodName() {
			return method_name;
		}

		public static MethodInfo from(String declaration) {
			int paren_start_idx = declaration.indexOf('(');
			String info_no_args_str = declaration.substring(0, paren_start_idx).trim();
			FieldInfo info_no_args = FieldInfo.from(info_no_args_str);
			StringBuilder sb = new StringBuilder();
			String[] arg_list = declaration.substring(declaration.indexOf('(') + 1, declaration.lastIndexOf(')')).split("\s*,\s*");
			sb.append('(');
			for (String arg : arg_list)
				sb.append(typeToTypeDescriptor(arg));
			sb.append(')').append(info_no_args.type_descriptor);
			return new MethodInfo(info_no_args.acc, sb.toString(), info_no_args.field_name);
		}

		@Override
		public String toString() {
			return "" + acc + ' ' + method_name + ' ' + method_descriptor;
		}
	}

	public static int toAcc(String acc_str) {
		switch (acc_str) {
		case "public":
			return Opcodes.ACC_PUBLIC;
		case "protecetd":
			return Opcodes.ACC_PROTECTED;
		case "private":
			return Opcodes.ACC_PRIVATE;
		case "static":
			return Opcodes.ACC_STATIC;
		case "final":
			return Opcodes.ACC_FINAL;
		case "abstract":
			return Opcodes.ACC_ABSTRACT;
		case "volatile":
			return Opcodes.ACC_VOLATILE;
		default:
			return 0;
		}
	}
}
