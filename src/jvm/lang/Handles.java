package jvm.lang;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

/**
 * 句柄操作相关，包括调用native方法
 */
public class Handles {
	private static final Lookup trusted_lookup;

	static {
		Lookup IMPL_LOOKUP = null;
		try {
			IMPL_LOOKUP = (Lookup) (InternalUnsafe.setAccessible(Lookup.class, "IMPL_LOOKUP", true).get(null));
		} catch (IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		} // 获取拥有所有权限的受信任的Lookup的唯一方法
		trusted_lookup = IMPL_LOOKUP;
	}

	/**
	 * 句柄工具
	 */

	/**
	 * 查找任意字节码行为的方法句柄(包括native)，从search_chain_start_subclazz开始查找，如果该类不存在方法则一直向上查找方法，直到在指定的超类search_chain_end_superclazz中也找不到方法时终止并抛出错误
	 * 句柄等价于Unsafe查找到的offset与base的组合，明确指定了一个内存中的方法地址
	 * 
	 * @param search_chain_start_subclazz 查找链起始类，也是要查找的对象，必须是search_chain_end_superclazz的子类
	 * @param search_chain_end_superclazz 查找链终止类
	 * @param type                        方法类型，包含返回值和参数类型
	 * @return 查找到的方法句柄
	 */
	public static MethodHandle findSpecialMethodHandle(Class<?> search_chain_start_subclazz, Class<?> search_chain_end_superclazz, String method_name, MethodType type) {
		MethodHandles.Lookup lookup;
		try {
			lookup = MethodHandles.privateLookupIn(search_chain_start_subclazz, trusted_lookup); // 获取该类所有的字节码行为的Lookup，即无视访问权限查找
			return lookup.findSpecial(search_chain_end_superclazz, method_name, type, search_chain_start_subclazz);
		} catch (IllegalAccessException | NoSuchMethodException ex) {
			ex.printStackTrace();
		}
		return null;
	}

	public static MethodHandle findSpecialMethodHandle(Class<?> search_clazz, String method_name, MethodType type) {
		return findSpecialMethodHandle(search_clazz, search_clazz, method_name, type);
	}

	public static MethodHandle findSpecialMethodHandle(Class<?> search_chain_start_subclazz, Class<?> search_chain_end_superclazz, String method_name, Class<?> return_type, Class<?>... arg_types) {
		return findSpecialMethodHandle(search_chain_start_subclazz, search_chain_end_superclazz, method_name, MethodType.methodType(return_type, arg_types));
	}

	public static MethodHandle findSpecialMethodHandle(Class<?> search_clazz, String method_name, Class<?> return_type, Class<?>... arg_types) {
		return findSpecialMethodHandle(search_clazz, search_clazz, method_name, return_type, arg_types);
	}

	public static MethodHandle findVirtualMethodHandle(Class<?> clazz, String method_name, MethodType type) {
		MethodHandles.Lookup lookup;
		try {
			lookup = MethodHandles.privateLookupIn(clazz, trusted_lookup); // 获取该类所有的字节码行为的Lookup，即无视访问权限查找
			return lookup.findVirtual(clazz, method_name, type);
		} catch (IllegalAccessException | NoSuchMethodException ex) {
			ex.printStackTrace();
		}
		return null;
	}

	public static MethodHandle findVirtualMethodHandle(Class<?> clazz, String method_name, Class<?> return_type, Class<?>... arg_types) {
		return findVirtualMethodHandle(clazz, method_name, MethodType.methodType(return_type, arg_types));
	}

	/**
	 * 查找静态函数的方法句柄
	 * 
	 * @param clazz
	 * @param method_name
	 * @param type
	 * @return
	 */
	public static MethodHandle findStaticMethodHandle(Class<?> clazz, String method_name, MethodType type) {
		MethodHandles.Lookup lookup;
		try {
			lookup = MethodHandles.privateLookupIn(clazz, trusted_lookup); // 获取该类所有的字节码行为的Lookup，即无视访问权限查找
			return lookup.findStatic(clazz, method_name, type);
		} catch (IllegalAccessException | NoSuchMethodException ex) {
			ex.printStackTrace();
		}
		return null;
	}

	public static MethodHandle findStaticMethodHandle(Class<?> clazz, String method_name, Class<?> return_type, Class<?>... arg_types) {
		return findStaticMethodHandle(clazz, method_name, MethodType.methodType(return_type, arg_types));
	}

	public static VarHandle findVarHandle(Class<?> clazz, String field_name, Class<?> type) {
		MethodHandles.Lookup lookup;
		try {
			lookup = MethodHandles.privateLookupIn(clazz, trusted_lookup); // 获取该类所有的字节码行为的Lookup，即无视访问权限查找
			return lookup.findVarHandle(clazz, field_name, type);
		} catch (IllegalAccessException | NoSuchFieldException ex) {
			ex.printStackTrace();
		}
		return null;
	}

	public static VarHandle findStaticVarHandle(Class<?> clazz, String field_name, Class<?> type) {
		MethodHandles.Lookup lookup;
		try {
			lookup = MethodHandles.privateLookupIn(clazz, trusted_lookup); // 获取该类所有的字节码行为的Lookup，即无视访问权限查找
			return lookup.findStaticVarHandle(clazz, field_name, type);
		} catch (IllegalAccessException | NoSuchFieldException ex) {
			ex.printStackTrace();
		}
		return null;
	}
}
