package jvm.vm;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.management.ManagementFactory;
import java.lang.management.PlatformManagedObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Properties;
import com.sun.management.HotSpotDiagnosticMXBean;
//import jdk.internal.loader.BootLoader;

import jvm.klass.ObjectManipulator;
import jvm.lang.Handles;
import jvm.lang.Reflection;

/**
 * 管理JVM的相关功能
 */
@SuppressWarnings({ "unchecked" })
public abstract class VmManipulator {
	public static final int NATIVE_JVM_BIT_VERSION;// 64或32
	public static final boolean NATIVE_JVM_HOTSPOT;// JVM是否是HotSpot，如果是才能获取JVM参数进而判断指针是否压缩
	public static final boolean NATIVE_JVM_COMPRESSED_OOPS;
	// JVM的管理类，实现是sun.management.VMManagementImpl，是sun.management.RuntimeImpl的成员jvm
	private static final Object RuntimeMXBean = null;
	private static final Object VMManagement = null;
	public static final Class<?> VMManagementClass = null;
	private static MethodHandle VMManagementImpl_getProcessId;
	// 系统属性System.props
	private static final Properties Properties = null;
	// HotSpotDiagnosticMXBean的实现类是 com.sun.management.internal.HotSpotDiagnostic
	private static final Class<ManagementFactory> ManagementFactoryClass = null;
	private static final HotSpotDiagnosticMXBean HotSpotDiagnosticMXBean = null;
	private static final Method HotSpotDiagnosticMXBean_getVMOption = null;
	private static final Method VMOption_getValue = null;
	// JVM参数 com.sun.management.internal.Flag
	public static final Class<?> Flag = null;
	private static MethodHandle Flag_getFlag;
	private static MethodHandle Flag_getValue;
	private static MethodHandle Flag_setLongValue;
	private static MethodHandle Flag_setDoubleValue;
	private static MethodHandle Flag_setBooleanValue;
	private static MethodHandle Flag_setStringValue;

	private static final Class<?> ClassLoaders = null;

	private static final boolean has_sa_jdi_jar = false;
	public static final Class<?> VMClass = null;// sun.jvm.hotspot.runtime.VM
	@SuppressWarnings("unused")
	private static final Object VM = null;

	static {
		String bit_version = System.getProperty("sun.arch.data.model");
		if (bit_version != null && bit_version.contains("64"))
			NATIVE_JVM_BIT_VERSION = 64;
		else {
			String arch = System.getProperty("os.arch");
			if (arch != null && arch.contains("64"))
				NATIVE_JVM_BIT_VERSION = 64;
			else
				NATIVE_JVM_BIT_VERSION = 32;
		}
		boolean hotspot = false;
		boolean compressed_oops = false;
		try {
			// 获取Management工厂类
			ObjectManipulator.setObject(VmManipulator.class, "ManagementFactoryClass", Class.forName("java.lang.management.ManagementFactory"));
			// 获取HotSpotDiagnosticMXBeanClass
			Class<HotSpotDiagnosticMXBean> HotSpotDiagnosticMXBeanClass = (Class<com.sun.management.HotSpotDiagnosticMXBean>) Class.forName("com.sun.management.HotSpotDiagnosticMXBean");
			ObjectManipulator.setObject(VmManipulator.class, "HotSpotDiagnosticMXBean", invokeManagementFactory("getPlatformMXBean", HotSpotDiagnosticMXBeanClass));
			if (HotSpotDiagnosticMXBean != null) {
				hotspot = true;// 获取HotSpotDiagnosticMXBean的getVMOption()方法
				ObjectManipulator.setObject(VmManipulator.class, "HotSpotDiagnosticMXBean_getVMOption", ObjectManipulator.removeAccessCheck(Reflection.getMethod(HotSpotDiagnosticMXBeanClass, "getVMOption", String.class)));
				if (NATIVE_JVM_BIT_VERSION == 64) {// 64位JVM需要检查是否启用了指针压缩
					Object oops_option = HotSpotDiagnosticMXBean_getVMOption.invoke(HotSpotDiagnosticMXBean, "UseCompressedOops");
					ObjectManipulator.setObject(VmManipulator.class, "VMOption_getValue", Reflection.getMethod(oops_option, "getValue"));
					compressed_oops = Boolean.parseBoolean(VMOption_getValue.invoke(oops_option).toString());
				} else
					compressed_oops = false;
			}
		} catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException | SecurityException | IllegalArgumentException ex) {
			hotspot = false;
		}
		NATIVE_JVM_HOTSPOT = hotspot;
		NATIVE_JVM_COMPRESSED_OOPS = compressed_oops;
		try {
			ObjectManipulator.setObject(VmManipulator.class, "RuntimeMXBean", invokeManagementFactory("getRuntimeMXBean"));
			ObjectManipulator.setObject(VmManipulator.class, "VMManagement", ObjectManipulator.access(RuntimeMXBean, "jvm"));// 获取JVM管理类
			ObjectManipulator.setObject(VmManipulator.class, "VMManagementClass", VMManagement.getClass());// 在HotSpot虚拟机中是sun.management.VMManagementImpl
			VMManagementImpl_getProcessId = Handles.findSpecialMethodHandle(VMManagementClass, VMManagementClass, "getProcessId", int.class);// 获取进程ID的native方法
			// 获取系统属性引用
			ObjectManipulator.setObject(VmManipulator.class, "Properties", ObjectManipulator.access(System.class, "props"));
			// 获取所有系统ClassLoaders
			ObjectManipulator.setObject(VmManipulator.class, "ClassLoaders", Class.forName("jdk.internal.loader.ClassLoaders"));
			// 虚拟机参数Flag类及其成员方法
			ObjectManipulator.setObject(VmManipulator.class, "Flag", Class.forName("com.sun.management.internal.Flag"));
			Flag_getFlag = Handles.findStaticMethodHandle(Flag, "getFlag", Flag, String.class);
			Flag_setLongValue = Handles.findStaticMethodHandle(Flag, "setLongValue", void.class, String.class, long.class);
			Flag_setDoubleValue = Handles.findStaticMethodHandle(Flag, "setDoubleValue", void.class, String.class, double.class);
			Flag_setBooleanValue = Handles.findStaticMethodHandle(Flag, "setBooleanValue", void.class, String.class, boolean.class);
			Flag_setStringValue = Handles.findStaticMethodHandle(Flag, "setStringValue", void.class, String.class, String.class);
			Flag_getValue = Handles.findSpecialMethodHandle(Flag, Flag, "getValue", Object.class);
			// 获取虚拟机实体VM
			if (has_sa_jdi_jar) {
				ObjectManipulator.setObject(VmManipulator.class, "VMClass", Class.forName("sun.jvm.hotspot.runtime.VM"));
				ObjectManipulator.setObject(VmManipulator.class, "VM", ObjectManipulator.invoke(VMClass, "getVM", null));
			}
		} catch (ClassNotFoundException ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * 使用反射调用ManagementFactory的工厂方法以获得相应的管理类单例
	 * 
	 * @param method_name
	 * @param arg_types
	 * @param args
	 * @return
	 */
	public static Object invokeManagementFactory(String method_name, Class<?>[] arg_types, Object args) {
		try {
			return ManagementFactoryClass.getDeclaredMethod(method_name, arg_types).invoke(null, args);
		} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException ex) {
			ex.printStackTrace();
		}
		return null;
	}

	public static Object invokeManagementFactory(String method_name) {
		try {
			return ManagementFactoryClass.getDeclaredMethod(method_name).invoke(null);
		} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException ex) {
			ex.printStackTrace();
		}
		return null;
	}

	public static <T extends PlatformManagedObject> T invokeManagementFactory(String method_name, Class<T> mxbean_interface) {
		try {
			return (T) ManagementFactoryClass.getDeclaredMethod(method_name, Class.class).invoke(null, mxbean_interface);
		} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException ex) {
			ex.printStackTrace();
		}
		return null;
	}

	/**
	 * 使用反射调用VMManagement的方法
	 * 
	 * @param method_name
	 * @param arg_types
	 * @param args
	 * @return
	 */
	public static Object invokeVmManagement(String method_name, Class<?>[] arg_types, Object args) {
		try {
			return ObjectManipulator.invoke(VMManagement, method_name, arg_types, args);
		} catch (SecurityException ex) {
			ex.printStackTrace();
		}
		return null;
	}

	/**
	 * 使用反射获取系统的内建ClassLoader
	 * 
	 * @param class_loader_name
	 * @return
	 */
	public static Object accessBuiltinClassLoaders(String class_loader_name) {
		try {
			return ObjectManipulator.access(ClassLoaders, class_loader_name);
		} catch (SecurityException | IllegalArgumentException ex) {
			System.err.println("Class loader name can only be BOOT_LOADER | PLATFORM_LOADER | APP_LOADER");
		}
		return null;
	}

	public static ArrayList<URL> getBuiltinClassLoaderClassPath(String class_loader_name) {
		try {
			Object class_loader = accessBuiltinClassLoaders(class_loader_name);
			Object url_classpath = ObjectManipulator.access(class_loader, "ucp");// jdk.internal.loader.URLClassPath
			if (url_classpath != null)// BOOT_LOADER的ucp为null
				return (ArrayList<URL>) ObjectManipulator.access(url_classpath, "path");
		} catch (SecurityException | IllegalArgumentException ex) {
			System.err.println("Cannot get class loader classpath");
		}
		return null;
	}

	/**
	 * 无视权限获取系统属性
	 * 
	 * @param key
	 * @return
	 */
	public static String getSystemProperty(String key) {
		return Properties.getProperty(key);
	}

	/**
	 * 无视权限设置系统属性
	 * 
	 * @param key
	 * @param value
	 */
	public static void setSystemProperty(String key, String value) {
		Properties.setProperty(key, value);
	}

	/**
	 * 获取指定的boolean类型的VM参数
	 * 
	 * @param option_name 参数名称，例如UseCompressedOops
	 * @return
	 */
	public static boolean getBooleanOption(String option_name) {
		try {
			return Boolean.parseBoolean(VMOption_getValue.invoke(HotSpotDiagnosticMXBean_getVMOption.invoke(HotSpotDiagnosticMXBean, option_name)).toString());
		} catch (IllegalAccessException | InvocationTargetException ex) {
			ex.printStackTrace();
		}
		return false;
	}

	/**
	 * 无视操作权限设置JVM的参数设置，必须自己确保value的类型与JVM的参数类型一致！调用的是native方法，但不是所有参数都支持运行时修改。大部分标志无法成功设置，因为检测可写标志在native方法内，无法干涉
	 * 
	 * @param name
	 * @param value
	 */
	public static void setVmOption(String name, Object value) {
		try {
			Object flag = Flag_getFlag.invoke(name);
			Object v = Flag_getValue.invoke(Flag.cast(flag));
			VarHandle writeable = Handles.findVarHandle(Flag, "writeable", boolean.class);
			writeable.set(flag, true);
			if (v instanceof Long lv)
				Flag_setLongValue.invokeExact(name, lv.longValue());
			if (v instanceof Double dv)
				Flag_setDoubleValue.invokeExact(name, dv.doubleValue());
			if (v instanceof Boolean bv)
				Flag_setBooleanValue.invokeExact(name, bv.booleanValue());
			if (v instanceof String sv)
				Flag_setStringValue.invokeExact(name, (String) sv);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * 获取当前JVM的进程ID（也称PID）
	 * 
	 * @return
	 */
	public static int getProcessId() {
		try {
			return (int) VMManagementImpl_getProcessId.invoke(VMManagementClass.cast(VMManagement));
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		return -1;
	}

	public static boolean isPrimitiveBoxingType(Object obj) {
		Class<?> cls = obj.getClass();
		return cls == Character.class || cls == Boolean.class || (obj instanceof Number);
	}
}
