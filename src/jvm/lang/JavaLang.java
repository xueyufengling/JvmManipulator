package jvm.lang;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.lang.StackWalker;

import jvm.klass.Handles;
import jvm.klass.ObjectManipulator;

/**
 * 修改反射安全限制等
 */
public class JavaLang {
	private static Class<?> SharedSecrets;// jdk.internal.access.SharedSecrets
	private static Object JavaLangAccess;// jdk.internal.access.JavaLangAccess;
	private static Method getConstantPool;// 获取指定类的类常量池The ConstantPool，其中包含静态成员、方法列表等

	/**
	 * 反射的过滤字段表，位于该map的字段无法被反射获取
	 */
	private static VarHandle Reflection_fieldFilterMap;

	/**
	 * 反射的过滤方法表，位于该map的方法无法被反射获取
	 */
	private static VarHandle Reflection_methodFilterMap;

	/**
	 * 获取调用该方法的类Class<?>
	 */
	public static MethodHandle Reflection_getCallerClass;

	private static StackWalker stackWalker;

	static {
		try {
			SharedSecrets = Class.forName("jdk.internal.access.SharedSecrets");
			JavaLangAccess = getAccess("JavaLangAccess");
			getConstantPool = ObjectManipulator.removeAccessCheck(JavaLangAccess.getClass().getDeclaredMethod("getConstantPool", Class.class));
			Class<?> Reflection = Class.forName("jdk.internal.reflect.Reflection");
			Reflection_fieldFilterMap = Handles.findStaticVarHandle(Reflection, "fieldFilterMap", Map.class);
			Reflection_methodFilterMap = Handles.findStaticVarHandle(Reflection, "methodFilterMap", Map.class);
			Reflection_getCallerClass = Handles.findSpecialMethodHandle(Reflection, "getCallerClass", Class.class);
			stackWalker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
		} catch (ClassNotFoundException | NoSuchMethodException | SecurityException ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * 获取反射过滤的字段
	 * 
	 * @return
	 */
	public static Map<Class<?>, Set<String>> getReflectionFieldFilter() {
		return (Map<Class<?>, Set<String>>) Reflection_fieldFilterMap.get();
	}

	/**
	 * 获取反射过滤的方法
	 * 
	 * @return
	 */
	public static Map<Class<?>, Set<String>> getReflectionMethodFilter() {
		return (Map<Class<?>, Set<String>>) Reflection_methodFilterMap.get();
	}

	/**
	 * 设置字段反射过滤，Java设置了一些非常核心的类无法通过反射获取即设置反射过滤，此操作将会替换原有的过滤限制。危险操作。
	 */
	public static void setReflectionFieldFilter(Map<Class<?>, Set<String>> filter_map) {
		Reflection_fieldFilterMap.set(filter_map);
	}

	/**
	 * 设置方法反射过滤，Java设置了一些非常核心的类无法通过反射获取即设置反射过滤，此操作将会替换原有的过滤限制。危险操作。
	 */
	public static void setReflectionMethodFilter(Map<Class<?>, Set<String>> filter_map) {
		Reflection_methodFilterMap.set(filter_map);
	}

	/**
	 * 移除反射过滤，使得全部字段均可通过反射获取，Java设置了一些非常核心的类无法通过反射获取即设置反射过滤，此操作将会移除该限制。危险操作。
	 */
	public static void removeReflectionFieldFilter() {
		setReflectionFieldFilter(new HashMap<Class<?>, Set<String>>());
	}

	/**
	 * 移除反射过滤，使得全部方法均可通过反射获取，Java设置了一些非常核心的类无法通过反射获取即设置反射过滤，此操作将会移除该限制。危险操作。
	 */
	public static void removeReflectionMethodFilter() {
		setReflectionMethodFilter(new HashMap<Class<?>, Set<String>>());
	}

	/**
	 * 获取jdk.internal.access.SharedSecrets中的访问对象
	 * 
	 * @param access_name 访问对象的类名，不包含包名
	 * @return 访问对象
	 */
	public static Object getAccess(String access_name) {
		return ObjectManipulator.invoke(SharedSecrets, "get" + access_name, null);
	}

	/**
	 * 获取指定类的常量池
	 * 
	 * @param clazz
	 * @return
	 */
	public static Object getConstantPool(Class<?> clazz) {
		try {
			return getConstantPool.invoke(JavaLangAccess, clazz);
		} catch (IllegalAccessException | InvocationTargetException ex) {
			ex.printStackTrace();
		}
		return null;
	}

	/**
	 * 追踪函数调用栈，并获取调用的类
	 * 
	 * @param skip_count
	 * @return
	 * @since Java 9
	 */
	public static Class<?> trackStackClass(int skip_count) {
		return stackWalker.walk(stack -> stack.skip(skip_count).findFirst().get().getDeclaringClass());
	}

	/**
	 * 获取直接调用该方法的类
	 * 
	 * @return
	 * @since Java 9
	 */
	public static Class<?> getCallerClass() {
		return trackStackClass(1);
	}

	/**
	 * 获取间接调用该方法的类，例如A()调用B()，B调用getOuterCallerClass()则返回A()所属类。
	 * 
	 * @return
	 * @since Java 9
	 */
	public static Class<?> getOuterCallerClass() {
		return trackStackClass(2);
	}
}
