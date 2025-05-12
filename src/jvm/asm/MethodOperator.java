package jvm.asm;

import org.objectweb.asm.MethodVisitor;

import jvm.asm.ByteCodeManipulator.MethodInfo;

public interface MethodOperator {
	public void modify(MethodInfo method_info, MethodVisitor method_visitor);
}
