package jvm.agent;

import java.io.IOException;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;

import jvm.klass.ObjectManipulator;
import jvm.vm.VmManipulator;

/**
 * Java Agent相关功能
 */
public class Agent {
	public static final Class<?> HotSpotVirtualMachineClass;// sun.tools.attach.HotSpotVirtualMachine
	static {
		VmManipulator.setSystemProperty("jdk.attach.allowAttachSelf", "true");// 并非实际允许调用，只是记录在系统中
		Class<?> cls = null;
		try {
			cls = Class.forName("sun.tools.attach.HotSpotVirtualMachine");
		} catch (ClassNotFoundException ex) {
			ex.printStackTrace();
		}
		HotSpotVirtualMachineClass = cls;
		ObjectManipulator.setBoolean(HotSpotVirtualMachineClass, "ALLOW_ATTACH_SELF", true);// 设置instrument可以从程序attach，不需要在启动JVM时传入参数jdk.attach.allowAttachSelf=true
	}

	/**
	 * 为指定的进程添加Agent
	 * 
	 * @param PID        要添加的JVM进程ID
	 * @param agent_path Agent的jar文件绝对路径
	 * @param args       传递给Agent的参数
	 */
	public static void attach(int PID, String agent_path, String args) {
		try {
			VirtualMachine jvm = VirtualMachine.attach(String.valueOf(PID));
			jvm.loadAgent(agent_path, args);
			jvm.detach();
		} catch (AttachNotSupportedException | IOException | AgentLoadException | AgentInitializationException ex) {
			ex.printStackTrace();
		}
	}

	public static void attach(int PID, String agent_path) {
		attach(PID, agent_path, null);
	}

	/**
	 * 为当前进程添加Agent
	 * 
	 * @param agent_path Agent的jar文件绝对路径
	 * @param args       传递给Agent的参数
	 */
	public static void attach(String agent_path, String args) {
		attach(VmManipulator.getProcessId(), agent_path, args);
	}

	public static void attach(String agent_path) {
		attach(agent_path, null);
	}
}
