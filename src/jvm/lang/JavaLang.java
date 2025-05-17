package jvm.lang;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

	public static final StackWalker stackWalker;

	static {
		try {
			SharedSecrets = Class.forName("jdk.internal.access.SharedSecrets");
			JavaLangAccess = getAccess("JavaLangAccess");
			getConstantPool = ObjectManipulator.removeAccessCheck(JavaLangAccess.getClass().getDeclaredMethod("getConstantPool", Class.class));
			Class<?> Reflection = Class.forName("jdk.internal.reflect.Reflection");
			Reflection_fieldFilterMap = Handles.findStaticVarHandle(Reflection, "fieldFilterMap", Map.class);
			Reflection_methodFilterMap = Handles.findStaticVarHandle(Reflection, "methodFilterMap", Map.class);
		} catch (ClassNotFoundException | NoSuchMethodException | SecurityException ex) {
			ex.printStackTrace();
		}
		stackWalker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
	}

	/**
	 * 在没有反射字段过滤器的环境下操作
	 * 
	 * @param op
	 */
	public static final void noReflectionFieldFilter(Runnable op) {
		Map<Class<?>, Set<String>> filterMap = getReflectionFieldFilter();
		removeReflectionFieldFilter();
		op.run();
		setReflectionFieldFilter(filterMap);
	}

	/**
	 * 不经过反射过滤获取字段
	 * 
	 * @param cls
	 * @param field_name
	 * @return
	 */
	public static final Field fieldNoReflectionFilter(Class<?> cls, String field_name) {
		Field f = null;
		Map<Class<?>, Set<String>> filterMap = getReflectionFieldFilter();
		removeReflectionFieldFilter();
		f = Reflection.getField(cls, field_name);
		setReflectionFieldFilter(filterMap);
		return f;
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
	 * 栈追踪<br>
	 * 本方法对应的栈帧始终是0，<br>
	 * skip 1 会返回直接调用本方法trackStackFrame(int skip_frame_count)的栈帧，即getCallerClass()方法本身栈帧<br>
	 * skip 2 会返回调用getCallerClass()的方法栈帧
	 * 
	 * @param skip_frame_count
	 * @return
	 * @CallerSensitive
	 */
	public static StackWalker.StackFrame trackStackFrame(int skip_frame_count) {
		return stackWalker.walk(stack -> stack.skip(skip_frame_count).findFirst().get());
	}

	public static void trackStackFrame(int skip_frame_count, StackTrackOperation op) {
		op.operate(stackWalker.walk(stack -> stack.skip(skip_frame_count).findFirst().get()));
	}

	public static Class<?> trackStackFrameClass(int skip_frame_count) {
		return stackWalker.walk(stack -> stack.skip(skip_frame_count).findFirst().get().getDeclaringClass());
	}

	/**
	 * 追踪函数调用栈帧，并获取调用的类<br>
	 * StackTrackOption为SKIP_COUNT_BY_FRAME时，行为同trackStackFrameClass(int skip_frame_count)一致<br>
	 * StackTrackOption为SKIP_COUNT_BY_CLASS时，追踪函数调用栈帧，并且返回第skip_class_count个不同的类<br>
	 * skip_class_count只表明跳过几个不同的类，对于连续同一个类调用栈帧将直接全部跳过
	 * 
	 * @param skip_count
	 * @param option
	 * @return
	 * @since Java 9
	 * @CallerSensitive
	 */
	public static Class<?> trackStackFrameClass(int skip_count, StackTrackOption option) {
		switch (option) {
		case SKIP_COUNT_BY_FRAME:
			return trackStackFrameClass(skip_count);
		case SKIP_COUNT_BY_CLASS: {
			int skipped_class_count = 0;
			int skip_frame = 1;// 以JavaLang作为起点
			Class<?> caller_record = trackStackFrameClass(skip_frame);
			Class<?> stack_frame_class = null;
			if (skip_count > 0) {
				for (;;) {
					stack_frame_class = trackStackFrameClass(++skip_frame);
					if (caller_record == stack_frame_class)
						continue;
					else {
						caller_record = stack_frame_class;// 将下一个与当前caller_record不同的类记录作为追踪结果
						if (++skipped_class_count >= skip_count)
							break;
					}
				}
			}
			return caller_record;
		}
		}
		return null;
	}

	/**
	 * 获取直接调用该方法的类<br>
	 * 例如A()调用B()，B()调用getOuterCallerClass()，那么返回B()栈帧
	 * 
	 * @return
	 * @since Java 9
	 * @CallerSensitive
	 */
	public static Class<?> getCallerClass() {
		return trackStackFrameClass(2);
	}

	/**
	 * 获取一次间接调用该方法的类<br>
	 * 例如A()调用B()，B()调用getOuterCallerClass()，那么返回A()栈帧
	 * 
	 * @return
	 * @since Java 9
	 * @CallerSensitive
	 */
	public static Class<?> getOuterCallerClass() {
		return trackStackFrameClass(3);
	}

	public static Class<?> getOuterCallerClassAsParam() {
		return trackStackFrameClass(4);
	}
}
