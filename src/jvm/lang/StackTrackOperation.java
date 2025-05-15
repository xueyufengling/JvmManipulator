package jvm.lang;

@FunctionalInterface
public interface StackTrackOperation {
	public void operate(StackWalker.StackFrame stackFrame);
}
