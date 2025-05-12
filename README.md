# JvmManipulator
提供一些Java的底层操作。包括修改JVM参数（启动参数无法修改）、修改final修饰变量、修改record成员、调用native方法、修改反射安全限制等。<br>
这些操作严重破坏了Java的安全性，请谨慎使用<br>
agent	Java Agent相关操作，包括运行时动态添加Agent<br>
asm	字节码操作<br>
jar	jar文件处理及内部资源读取<br>
klass	类操作，修改类成员、访问类方法<br>
lang	Java语言层面相关操作，修改安全限制<br>
vm	JVM相关操作<br>