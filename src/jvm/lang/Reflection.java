package jvm.lang;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * 反射工具，大部分功能可以直接使用Manipulator调用
 */
public abstract class Reflection {

	public static Class<?> getClassForName(String name, boolean printClassNotFoundException) {
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException ex) {
			if (printClassNotFoundException)
				ex.printStackTrace();
		}
		return null;
	}

	public static Class<?> getClassForName(String name) {
		return getClassForName(name, true);
	}

	public static String getClassNameWithoutPackage(String full_name) {
		return full_name.substring(full_name.lastIndexOf('.') + 1);
	}

	public static String getClassNameWithoutPackage(Object obj) {
		return getClassNameWithoutPackage(obj.getClass().getName());
	}

	public static String getPackageName(String full_name) {
		return full_name.substring(0, full_name.lastIndexOf('.'));
	}

	/**
	 * 查询类成员，如果该类没有则递归查找父类
	 */
	public static Field getField(Object obj, String name) {
		Class<?> cls;
		if (obj instanceof Class<?> c)
			cls = c;
		else
			cls = obj.getClass();
		try {
			return cls.getDeclaredField(name);
		} catch (NoSuchFieldException ex) {
			Class<?> supercls = cls.getSuperclass();
			if (supercls == null) {
				System.err.println("Cannot find field " + name);
				ex.printStackTrace();
				return null;
			} else
				return getField(supercls, name);
		}
	}

	public static Object getValue(Object obj, Field field) {
		if (obj == null || field == null)
			return null;
		try {
			field.setAccessible(true);
			return field.get(obj);
		} catch (IllegalAccessException ex) {
			System.err.println("Reflection Utils reached IllegalAccessException reading field " + field);
			ex.printStackTrace();
		}
		return null;
	}

	public static Object getValue(Object obj, String field) {
		return getValue(obj, getField(obj, field));
	}

	public static boolean setValue(Object obj, Field field, Object value) {
		if (obj == null || field == null)
			return false;
		try {
			field.setAccessible(true);
			field.set(obj, value);
		} catch (IllegalAccessException ex) {
			System.err.println("Reflection Utils reached IllegalAccessException writing field " + field + " with value " + value + " in object " + obj.toString());
			ex.printStackTrace();
			;
			return false;
		}
		return true;
	}

	public static boolean setValue(Object obj, String field, Object value) {
		return setValue(obj, getField(obj, field), value);
	}

	public static String methodDescription(String name, Class<?>... arg_types) {
		String method_description = name + '(';
		if (arg_types != null)
			for (int a = 0; a < arg_types.length; ++a) {
				method_description += arg_types[a].getName();
				if (a != arg_types.length - 1)
					method_description += ", ";
			}
		method_description += ')';
		return method_description;
	}

	// 只搜寻该类自己的方法
	public static Method getMethodSelf(Class<?> clazz, String name, Class<?>... arg_types) {
		try {
			return clazz.getDeclaredMethod(name, arg_types == null ? (new Class<?>[] {}) : arg_types);
		} catch (NoSuchMethodException ex) {
			return null;
		}
	}

	// 只搜寻该类及其父类、实现接口的方法
	public static Method getMethodDirectInherited(Class<?> clazz, String name, Class<?>... arg_types) {
		try {
			return clazz.getDeclaredMethod(name, arg_types == null ? (new Class<?>[] {}) : arg_types);
		} catch (NoSuchMethodException ex) {
			Class<?> supercls = clazz.getSuperclass();
			Class<?>[] interfaces = clazz.getInterfaces();
			if (supercls == null && interfaces.length == 0) {
				System.err.println("Cannot find method " + name + " in neither super class nor implemented interfaces");
				ex.printStackTrace();
				return null;
			} else {
				Method method = getMethodSelf(supercls, name, arg_types);
				if (method != null)// 如果父类有方法则优先返回父类的方法
					return method;
				else {// 从接口中搜寻方法
					for (Class<?> i : interfaces)
						method = getMethodSelf(i, name, arg_types);
				}
				return method;
			}
		}
	}

	public static Method getMethod(Object obj, String name, Class<?>... arg_types) {
		Class<?> cls;
		if (obj instanceof Class<?> c)
			cls = c;
		else
			cls = obj.getClass();
		Method method = null;
		ArrayList<ArrayList<Class<?>>> chain = resolveInheritImplamentChain(cls);
		FOUND: for (int depth = 0; depth < chain.size(); ++depth) {
			ArrayList<Class<?>> equal_depth_classes = chain.get(depth);
			for (int i = 0; i < equal_depth_classes.size(); ++i)
				if ((method = getMethodSelf(equal_depth_classes.get(i), name, arg_types)) != null)
					break FOUND;
		}
		if (method == null) {
			System.err.println("Method " + methodDescription(name, arg_types) + " not found in class " + cls.getName() + " or its parents");
		}
		return method;
	}

	public static void resolveInheritChain(Class<?> clazz, ArrayList<Class<?>> chain) {
		chain.add(clazz);
		Class<?> supercls = clazz.getSuperclass();
		if (supercls != null)
			resolveInheritChain(supercls, chain);
	}

	public static Class<?>[] resolveInheritChain(Class<?> clazz) {
		ArrayList<Class<?>> chain = new ArrayList<>();
		resolveInheritChain(clazz, chain);
		return chain.toArray(new Class<?>[chain.size()]);
	}

	public static ArrayList<ArrayList<Class<?>>> resolveInheritImplamentChain(Class<?> self, int current_depth, ArrayList<ArrayList<Class<?>>> chain) {
		ArrayList<Class<?>> current_depth_classes = null;
		while (current_depth_classes == null)
			try {
				current_depth_classes = chain.get(current_depth);
			} catch (IndexOutOfBoundsException ex) {
				chain.add(new ArrayList<>());
			}
		current_depth_classes.add(self);
		Class<?> supercls = self.getSuperclass();
		if (supercls != null)
			resolveInheritImplamentChain(supercls, current_depth + 1, chain);
		Class<?>[] interfaces = self.getInterfaces();
		for (Class<?> i : interfaces)
			resolveInheritImplamentChain(i, current_depth + 1, chain);
		return chain;
	}

	public static ArrayList<ArrayList<Class<?>>> resolveInheritImplamentChain(Class<?> clazz) {
		ArrayList<ArrayList<Class<?>>> chain = new ArrayList<>();
		return resolveInheritImplamentChain(clazz, 0, chain);
	}

	/**
	 * 推断每个参数的类型，每个参数的类型均是一个数组，为该类型的继承链
	 * 
	 * @param args 要推断的参数列表
	 * @return
	 */
	public static Class<?>[][] resolveArgTypesChain(Object... args) {
		Class<?>[][] arg_types = new Class<?>[args.length][];
		for (int idx = 0; idx < args.length; ++idx)
			arg_types[idx] = resolveInheritChain(args[idx].getClass());
		return arg_types;
	}

	/**
	 * 推断每个参数的类型，每个参数的类型均是传入参数本类型，不包括其父类继承链
	 * 
	 * @param args 要推断的参数列表
	 * @return
	 */
	public static Class<?>[] resolveArgTypes(Object... args) {
		Class<?>[] arg_types = new Class<?>[args.length];
		for (int idx = 0; idx < args.length; ++idx)
			arg_types[idx] = args[idx].getClass();
		return arg_types;
	}

	public static Object invoke(Object obj, String method_name, Class<?>[] arg_types, Object... args) {
		Method method = getMethod(obj, method_name, arg_types);
		try {
			method.setAccessible(true);
			return method.invoke(obj, args);
		} catch (IllegalAccessException | InvocationTargetException ex) {
			System.err.println("Reflection Utils reached exception invoking method " + method_name + " with arguments " + args + " in object " + obj.toString());
			ex.printStackTrace();
			return null;
		}
	}

	public static Constructor<?> getConstructor(Object obj, Class<?>... arg_types) {
		Class<?> cls;
		if (obj instanceof Class<?> c)
			cls = c;
		else
			cls = obj.getClass();
		try {
			return cls.getDeclaredConstructor(arg_types == null ? (new Class<?>[] {}) : arg_types);
		} catch (NoSuchMethodException ex) {
			Class<?> supercls = cls.getSuperclass();
			return supercls == null ? null : getConstructor(supercls, arg_types);
		}
	}

	/**
	 * 利用反射调用构造函数
	 * 
	 * @param obj  目标类型的对象实例或Class<T>
	 * @param args
	 * @return
	 */
	public static Object construct(Object obj, Class<?>[] arg_types, Object... args) {
		Constructor<?> constructor = getConstructor(obj, arg_types);
		try {
			constructor.setAccessible(true);
			return constructor.newInstance(args);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
			System.err.println("Reflection Utils reached exception contructing " + obj.toString() + " with arguments " + args);
			ex.printStackTrace();
			return null;
		}
	}

	/**
	 * 判断某个类是否具有指定超类，支持向上递归查找超类
	 * 
	 * @param clazz       要判断是否有超类的类
	 * @param super_class 超类
	 * @return clazz具有超类super_class则返回true，否则返回false
	 */
	public static boolean hasSuperClass(Class<?> clazz, Class<?> super_class) {
		Class<?> supercls = clazz.getSuperclass();
		if (supercls == super_class)
			return true;
		return supercls == null ? false : hasSuperClass(supercls, super_class);
	}
}
