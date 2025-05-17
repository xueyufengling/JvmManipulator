package jvm.lang;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;

import jvm.klass.ObjectManipulator;
import sun.misc.Unsafe;

public class InternalUnsafe {
	public static Unsafe unsafe;
	static Class<?> internalUnsafeClass;
	static Object internalUnsafe;

	private static Method objectFieldOffset$Field;// 没有检查的jdk.internal.misc.Unsafe.objectFieldOffset()
	private static Method objectFieldOffset$Class$String;
	private static Method staticFieldBase;
	private static Method staticFieldOffset;
	private static Method defineClass;

	/*
	 * 64位JVM的offset从12开始为数据段，此处为java.lang.reflect.AccessibleObject的boolean override成员，将该成员覆写为true可以无视权限调用Method、Field、Constructor
	 */
	private static final long java_lang_reflect_AccessibleObject_override_offset;

	public static final long INVALID_FIELD_OFFSET = -1;

	static {
		Field theUnsafe;
		Field theInternalUnsafe;
		try {
			theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
			theUnsafe.setAccessible(true);
			unsafe = (Unsafe) theUnsafe.get(null);
			internalUnsafeClass = Class.forName("jdk.internal.misc.Unsafe");
			theInternalUnsafe = Unsafe.class.getDeclaredField("theInternalUnsafe");
			theInternalUnsafe.setAccessible(true);
			internalUnsafe = theInternalUnsafe.get(null);
		} catch (NoSuchFieldException | ClassNotFoundException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
			ex.printStackTrace();
		}
		// 最优先获取java.lang.reflect.AccessibleObject的override以获取访问权限
		java_lang_reflect_AccessibleObject_override_offset = java_lang_reflect_AccessibleObject_override_offset();
		try {
			objectFieldOffset$Field = setAccessible(internalUnsafeClass.getDeclaredMethod("objectFieldOffset", Field.class), true);
			objectFieldOffset$Class$String = setAccessible(internalUnsafeClass.getDeclaredMethod("objectFieldOffset", Class.class, String.class), true);
			staticFieldBase = setAccessible(internalUnsafeClass.getDeclaredMethod("staticFieldBase", Field.class), true);
			staticFieldOffset = setAccessible(internalUnsafeClass.getDeclaredMethod("staticFieldOffset", Field.class), true);
			defineClass = setAccessible(internalUnsafeClass.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class, ClassLoader.class, ProtectionDomain.class), true);
		} catch (NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
		}

	}

	/**
	 * 获取{@code java.lang.reflect.AccessibleObject.override}成员内存位移<br>
	 * {@code sun.misc.Unsafe.objectFieldOffset()}已经标注{@code @Deprecated}，如果该方法还存在则调用该方法获取{@code override}位移。<br>
	 * 若不存在则直接返回根据内存模型决定的位移（magic number）<br>
	 * 
	 * @return
	 */
	private static final long java_lang_reflect_AccessibleObject_override_offset() {
		Method unsafe_objectFieldOffset = null;
		try {
			unsafe_objectFieldOffset = Unsafe.class.getDeclaredMethod("objectFieldOffset", Field.class);
			if (unsafe_objectFieldOffset != null)
				return (long) unsafe_objectFieldOffset.invoke(unsafe, BlankMirror_java_lang_reflect_AccessibleObject.class.getDeclaredField("override"));
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException | NoSuchFieldException e) {
		}
		return 12;
	}

	public static final class Invoker {
		/**
		 * 调用internalUnsafe的方法
		 * 
		 * @param method_name 方法名称
		 * @param arg_types   参数类型
		 * @param args        实参
		 * @return
		 */
		public static final Object invoke(String method_name, Class<?>[] arg_types, Object... args) {
			try {
				return ObjectManipulator.invoke(InternalUnsafe.internalUnsafe, method_name, arg_types, args);
			} catch (SecurityException e) {
				e.printStackTrace();
			}
			return null;
		}
	}

	/**
	 * 无视权限设置是否可访问
	 * 
	 * @param <AO>
	 * @param accessibleObj
	 * @param accessible
	 * @return
	 */
	public static <AO extends AccessibleObject> AO setAccessible(AO accessibleObj, boolean accessible) {
		unsafe.putBoolean(accessibleObj, java_lang_reflect_AccessibleObject_override_offset, accessible);
		return accessibleObj;
	}

	public static Field setAccessible(Class<?> cls, String field_name, boolean accessible) {
		Field f = Reflection.getField(cls, field_name);
		setAccessible(f, accessible);
		return f;
	}

	/**
	 * 没有任何安全检查的Unsafe.objectFieldOffset方法，可以修改record class的成员
	 * 
	 * @param field
	 * @return
	 */
	public static long objectFieldOffset(Field field) {
		try {
			return (long) objectFieldOffset$Field.invoke(internalUnsafe, field);
		} catch (IllegalAccessException | InvocationTargetException e) {
			e.printStackTrace();
		}
		return INVALID_FIELD_OFFSET;
	}

	public static long objectFieldOffset(Class<?> cls, String field_name) {
		try {
			return (long) objectFieldOffset$Class$String.invoke(internalUnsafe, cls, field_name);
		} catch (IllegalAccessException | InvocationTargetException e) {
			e.printStackTrace();
		}
		return INVALID_FIELD_OFFSET;
	}

	public static Object staticFieldBase(Field field) {
		try {
			return staticFieldBase.invoke(internalUnsafe, field);
		} catch (IllegalAccessException | InvocationTargetException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static long staticFieldOffset(Field field) {
		try {
			return (long) staticFieldOffset.invoke(internalUnsafe, field);
		} catch (IllegalAccessException | InvocationTargetException e) {
			e.printStackTrace();
		}
		return INVALID_FIELD_OFFSET;
	}

	/**
	 * 不调用构造函数创建一个对象
	 * 
	 * @param cls 对象类
	 * @return 分配的对象
	 */
	@SuppressWarnings("unchecked")
	public static <T> T allocateInstance(Class<T> cls) {
		try {
			return (T) unsafe.allocateInstance(cls);
		} catch (InstantiationException ex) {
			ex.printStackTrace();
		}
		return null;
	}

	/**
	 * 无视访问权限和修饰符修改Object值，如果是静态成员忽略obj参数.此方法对于HiddenClass和record同样有效
	 * 
	 * @param obj   要修改值的对象
	 * @param field 要修改的Field
	 * @param value 要修改的值
	 * @return
	 */
	public static void putObject(Object obj, Field field, Object value) {
		if (Modifier.isStatic(field.getModifiers()))
			unsafe.putObject(staticFieldBase(field), staticFieldOffset(field), value);
		else
			unsafe.putObject(obj, objectFieldOffset(field), value);
	}

	public static void putObject(Object obj, String field, Object value) {
		Field f = Reflection.getField(obj, field);
		if (Modifier.isStatic(f.getModifiers()))
			unsafe.putObject(staticFieldBase(f), staticFieldOffset(f), value);
		else
			unsafe.putObject(obj, objectFieldOffset(obj.getClass(), field), value);
	}

	public static Object getObject(Object obj, Field field) {
		if (Modifier.isStatic(field.getModifiers()))
			return unsafe.getObject(staticFieldBase(field), staticFieldOffset(field));
		else
			return unsafe.getObject(obj, objectFieldOffset(field));
	}

	public static Object getObject(Object obj, String field) {
		Field f = Reflection.getField(obj, field);
		if (Modifier.isStatic(f.getModifiers()))
			return unsafe.getObject(staticFieldBase(f), staticFieldOffset(f));
		else
			return unsafe.getObject(obj, objectFieldOffset(obj.getClass(), field));
	}

	public static void putLong(Object obj, Field field, long value) {
		if (Modifier.isStatic(field.getModifiers()))
			unsafe.putLong(staticFieldBase(field), staticFieldOffset(field), value);
		else
			unsafe.putLong(obj, objectFieldOffset(field), value);
	}

	public static void putLong(Object obj, String field, long value) {
		putLong(obj, Reflection.getField(obj, field), value);
	}

	public static void puttBoolean(Object obj, Field field, boolean value) {
		if (Modifier.isStatic(field.getModifiers()))
			unsafe.putBoolean(staticFieldBase(field), staticFieldOffset(field), value);
		else
			unsafe.putBoolean(obj, objectFieldOffset(field), value);
	}

	public static void puttBoolean(Object obj, String field, boolean value) {
		puttBoolean(obj, Reflection.getField(obj, field), value);
	}

	public static void putInt(Object obj, Field field, int value) {
		if (Modifier.isStatic(field.getModifiers()))
			unsafe.putInt(staticFieldBase(field), staticFieldOffset(field), value);
		else
			unsafe.putInt(obj, objectFieldOffset(field), value);
	}

	public static void putInt(Object obj, String field, int value) {
		putInt(obj, Reflection.getField(obj, field), value);
	}

	public static void putDouble(Object obj, Field field, double value) {
		if (Modifier.isStatic(field.getModifiers()))
			unsafe.putDouble(staticFieldBase(field), staticFieldOffset(field), value);
		else
			unsafe.putDouble(obj, objectFieldOffset(field), value);
	}

	public static void putDouble(Object obj, String field, double value) {
		putDouble(obj, Reflection.getField(obj, field), value);
	}

	public static void putFloat(Object obj, Field field, float value) {
		if (Modifier.isStatic(field.getModifiers()))
			unsafe.putFloat(staticFieldBase(field), staticFieldOffset(field), value);
		else
			unsafe.putFloat(obj, objectFieldOffset(field), value);
	}

	/**
	 * 直接令loader加载指定class<br>
	 * 绕过类加载器： 直接向JVM注册类，不经过ClassLoader体系.<br>
	 * 无依赖解析：不自动加载依赖类，如果依赖类不存在则直接抛出java.lang.NoClassDefFoundError<br>
	 * 无安全检查： 跳过字节码验证、包可见性检查等<br>
	 * 内存驻留： 定义的类不会被 GC 回收<br>
	 * 
	 * @param name
	 * @param b
	 * @param off
	 * @param len
	 * @param loader
	 * @param protectionDomain
	 * @return
	 */
	public static Class<?> defineClass(String name, byte[] b, int off, int len, ClassLoader loader, ProtectionDomain protectionDomain) {
		try {
			return (Class<?>) defineClass.invoke(internalUnsafe, name, b, off, len, loader, protectionDomain);
		} catch (IllegalAccessException | InvocationTargetException e) {
			e.printStackTrace();
		}
		return null;
	}
}
