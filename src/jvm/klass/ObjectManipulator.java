package jvm.klass;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import sun.misc.Unsafe;

/**
 * 核心的类成员修改、访问和方法调用类，支持修改final、record成员变量。<br>
 * 该类的方法破坏了Java的安全性，请谨慎使用。
 */
@SuppressWarnings("deprecation")
public abstract class ObjectManipulator {
	private static Class<?> internalUnsafeClazz;
	private static Unsafe unsafe;
	private static Object internalUnsafe;

	private static Method objectFieldOffset;// 没有检查的Unsafe.objectFieldOffset
	private static Method staticFieldBase;
	private static Method staticFieldOffset;
	/*
	 * 64位JVM的offset从12开始为数据段，此处为java.lang.reflect.AccessibleObject的boolean override成员，将该成员覆写为true可以无视权限调用Method、Field、Constructor
	 */
	private static long java_lang_reflect_AccessibleObject_override_OFFSET = 12;

	private static MethodHandle getDeclaredFields0;// Class.getDeclaredFields0无视反射访问权限获取字段
	private static MethodHandle getDeclaredMethods0;
	private static MethodHandle searchFields;
	private static MethodHandle searchMethods;

	static {
		Field theUnsafe;
		Field theInternalUnsafe;
		try {
			theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
			theUnsafe.setAccessible(true);
			unsafe = (Unsafe) theUnsafe.get(null);
			internalUnsafeClazz = Class.forName("jdk.internal.misc.Unsafe");
			theInternalUnsafe = Unsafe.class.getDeclaredField("theInternalUnsafe");
			theInternalUnsafe.setAccessible(true);
			internalUnsafe = theInternalUnsafe.get(null);
			// 最优先获取java.lang.reflect.AccessibleObject的override以获取访问权限
			java_lang_reflect_AccessibleObject_override_OFFSET = unsafe.objectFieldOffset(BlankMirror_java_lang_reflect_AccessibleObject.class.getDeclaredField("override"));
			getDeclaredFields0 = Handles.findSpecialMethodHandle(Class.class, Class.class, "getDeclaredFields0", Field[].class, boolean.class);
			getDeclaredMethods0 = Handles.findSpecialMethodHandle(Class.class, Class.class, "getDeclaredMethods0", Method[].class, boolean.class);
			searchFields = Handles.findStaticMethodHandle(Class.class, "searchFields", Field.class, Field[].class, String.class);
			searchMethods = Handles.findStaticMethodHandle(Class.class, "searchMethods", Method.class, Method[].class, String.class, Class[].class);
			objectFieldOffset = removeAccessCheck(internalUnsafeClazz.getDeclaredMethod("objectFieldOffset", Field.class));
			staticFieldBase = removeAccessCheck(internalUnsafeClazz.getDeclaredMethod("staticFieldBase", Field.class));
			staticFieldOffset = removeAccessCheck(internalUnsafeClazz.getDeclaredMethod("staticFieldOffset", Field.class));
		} catch (NoSuchFieldException | NoSuchMethodException | ClassNotFoundException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * 获取对象定义的字段原对象，无视反射过滤和访问权限，直接调用JVM内部的native方法获取全部字段。<br>
	 * 注意：本方法没有拷贝对象，因此对返回字段的任何修改都将反应在反射系统获取的所有的复制对象中
	 * 
	 * @param clazz 要获取的类
	 * @return 字段列表
	 */
	public static Field[] getDeclaredFields(Class<?> clazz) {
		try {
			return (Field[]) getDeclaredFields0.invokeExact(clazz, false);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		return null;
	}

	public static Field getDeclaredField(Class<?> clazz, String field_name) {
		try {
			return (Field) searchFields.invokeExact(getDeclaredFields(clazz), field_name);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		return null;
	}

	/**
	 * 获取对象定义的方法原对象，无视反射过滤和访问权限，直接调用JVM内部的native方法获取全部方法
	 * 
	 * @param clazz 要获取的类
	 * @return 字段列表
	 */
	public static Method[] getDeclaredMethods(Class<?> clazz) {
		try {
			return (Method[]) getDeclaredMethods0.invokeExact(clazz, false);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		return null;
	}

	public static Method getDeclaredMethod(Class<?> clazz, String method_name, Class<?> arg_types) {
		try {
			return (Method) searchMethods.invokeExact(getDeclaredMethods(clazz), method_name, arg_types);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		return null;
	}

	/**
	 * 反射工具
	 */

	/**
	 * 本类最核心的方法，移除AccessibleObject的访问安全检查限制，使得对象可以被访问。<br>
	 * 注意：如果access_obj为null，JVM将直接崩溃。
	 * 
	 * @param <AO>
	 * @param access_obj 要移除访问安全检查的对象
	 * @return
	 */
	public static <AO extends AccessibleObject> AO removeAccessCheck(AO access_obj) {
		unsafe.putBoolean(access_obj, java_lang_reflect_AccessibleObject_override_OFFSET, true);
		return access_obj;
	}

	/**
	 * 恢复AccessibleObject的访问安全检查限制，使得对象访问遵循Java规则
	 * 
	 * @param <AO>
	 * @param access_obj 要恢复访问安全检查的对象
	 * @return
	 */
	public static <AO extends AccessibleObject> AO recoveryAccessCheck(AO access_obj) {
		unsafe.putBoolean(access_obj, java_lang_reflect_AccessibleObject_override_OFFSET, false);
		return access_obj;
	}

	/**
	 * 使用反射无视权限访问成员，如果是静态成员则传入Class<?>，非静态成员则传入对象本身，jdk.internal.reflect.Reflection会对反射获取的字段进行过滤，因此这些字段不能访问。如需访问使用Handle的方法进行
	 * 
	 * @param obj        非静态成员所属对象本身或静态成员对应的Class<?>
	 * @param field_name 要访问的字段
	 * @return 成员的值
	 */
	public static Object access(Object obj, String field_name) {
		try {
			Field field = ObjectManipulator.removeAccessCheck(Reflect.getField(obj, field_name));
			return field.get(obj);
		} catch (IllegalArgumentException | IllegalAccessException | SecurityException ex) {
			System.err.println("access failed. obj=" + obj.toString() + ", field_name=" + field_name);
			ex.printStackTrace();
		}
		return null;
	}

	public static Object access(Object obj, Field field) {
		try {
			return ObjectManipulator.removeAccessCheck(field).get(obj);
		} catch (IllegalArgumentException | IllegalAccessException ex) {
			System.err.println("access failed. obj=" + obj.toString() + ", field_name=" + field.getName());
			ex.printStackTrace();
		}
		return null;
	}

	/**
	 * 使用反射无视权限调用方法，如果是静态方法则传入Class<?>，非静态方法则传入对象本身。jdk.internal.reflect.Reflection会对反射获取的方法进行过滤，因此这些方法不能访问。如需访问使用Handle的方法进行
	 * 
	 * @param obj
	 * @param method_name
	 * @param arg_types
	 * @param args
	 */
	public static Object invoke(Object obj, String method_name, Class<?>[] arg_types, Object... args) {
		try {
			Method method = ObjectManipulator.removeAccessCheck(Reflect.getMethod(obj, method_name, arg_types));
			return method.invoke(obj, args);
		} catch (IllegalArgumentException | IllegalAccessException | SecurityException ex) {
			System.err.println("invoke failed. obj=" + obj.toString() + ", method_name=" + method_name);
			ex.printStackTrace();
		} catch (InvocationTargetException ex) {
			System.err.println("invoke method throws exception. obj=" + obj.toString() + ", method_name=" + method_name);
			ex.getCause().printStackTrace();
		}
		return null;
	}

	public static Object invoke(Object obj, Method method, Object... args) {
		try {
			return ObjectManipulator.removeAccessCheck(method).invoke(obj, args);
		} catch (IllegalArgumentException | IllegalAccessException | SecurityException ex) {
			System.err.println("invoke failed. obj=" + obj.toString() + ", method_name=" + method.getName());
			ex.printStackTrace();
		} catch (InvocationTargetException ex) {
			System.err.println("invoke method throws exception. obj=" + obj.toString() + ", method_name=" + method.getName());
			ex.getCause().printStackTrace();
		}
		return null;
	}

	/**
	 * 调用internalUnsafe的方法
	 * 
	 * @param method_name 方法名称
	 * @param arg_types   参数类型
	 * @param args        实参
	 * @return
	 */
	public static Object invokeInternalUnsafeMethod(String method_name, Class<?>[] arg_types, Object... args) {
		try {
			return invoke(internalUnsafe, method_name, arg_types, args);
		} catch (SecurityException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * 没有任何安全检查的Unsafe.objectFieldOffset方法，可以修改record class的成员
	 * 
	 * @param field
	 * @return
	 */
	public static long objectFieldOffset(Field field) {
		long offset = -1;
		try {
			offset = (long) objectFieldOffset.invoke(internalUnsafe, field);
		} catch (IllegalAccessException | InvocationTargetException e) {
			e.printStackTrace();
		}
		return offset;
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
		long offset = -1;
		try {
			offset = (long) staticFieldOffset.invoke(internalUnsafe, field);
		} catch (IllegalAccessException | InvocationTargetException e) {
			e.printStackTrace();
		}
		return offset;
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
	public static boolean setObject(Object obj, Field field, Object value) {
		if (field == null)
			return false;
		if (Modifier.isStatic(field.getModifiers()))
			unsafe.putObject(staticFieldBase(field), staticFieldOffset(field), value);
		else
			unsafe.putObject(obj, objectFieldOffset(field), value);
		return true;
	}

	public static boolean setObject(Object obj, String field, Object value) {
		return setObject(obj, Reflect.getField(obj, field), value);
	}

	public static Object getObject(Object obj, Field field) {
		if (field == null)
			return false;
		if (Modifier.isStatic(field.getModifiers()))
			return unsafe.getObject(staticFieldBase(field), staticFieldOffset(field));
		else
			return unsafe.getObject(obj, objectFieldOffset(field));
	}

	public static Object getObject(Object obj, String field) {
		return getObject(obj, Reflect.getField(obj, field));
	}

	public static boolean setLong(Object obj, Field field, long value) {
		if (field == null)
			return false;
		if (Modifier.isStatic(field.getModifiers()))
			unsafe.putLong(staticFieldBase(field), staticFieldOffset(field), value);
		else
			unsafe.putLong(obj, objectFieldOffset(field), value);
		return true;
	}

	public static boolean setLong(Object obj, String field, long value) {
		return setLong(obj, Reflect.getField(obj, field), value);
	}

	public static boolean setBoolean(Object obj, Field field, boolean value) {
		if (field == null)
			return false;
		if (Modifier.isStatic(field.getModifiers()))
			unsafe.putBoolean(staticFieldBase(field), staticFieldOffset(field), value);
		else
			unsafe.putBoolean(obj, objectFieldOffset(field), value);
		return true;
	}

	public static boolean setBoolean(Object obj, String field, boolean value) {
		return setBoolean(obj, Reflect.getField(obj, field), value);
	}

	public static boolean setInt(Object obj, Field field, int value) {
		if (field == null)
			return false;
		if (Modifier.isStatic(field.getModifiers()))
			unsafe.putInt(staticFieldBase(field), staticFieldOffset(field), value);
		else
			unsafe.putInt(obj, objectFieldOffset(field), value);
		return true;
	}

	public static boolean setInt(Object obj, String field, int value) {
		return setInt(obj, Reflect.getField(obj, field), value);
	}

	public static boolean setDouble(Object obj, Field field, double value) {
		if (field == null)
			return false;
		if (Modifier.isStatic(field.getModifiers()))
			unsafe.putDouble(staticFieldBase(field), staticFieldOffset(field), value);
		else
			unsafe.putDouble(obj, objectFieldOffset(field), value);
		return true;
	}

	public static boolean setDouble(Object obj, String field, double value) {
		return setDouble(obj, Reflect.getField(obj, field), value);
	}

	public static boolean setFloat(Object obj, Field field, float value) {
		if (field == null)
			return false;
		if (Modifier.isStatic(field.getModifiers()))
			unsafe.putFloat(staticFieldBase(field), staticFieldOffset(field), value);
		else
			unsafe.putFloat(obj, objectFieldOffset(field), value);
		return true;
	}

	public static boolean setFloat(Object obj, String field, float value) {
		return setFloat(obj, Reflect.getField(obj, field), value);
	}
}
