package org.eclipse.jdt.internal.compiler.codegen;
/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
public interface QualifiedNamesConstants {
	char[] JavaLangObjectConstantPoolName = "java/lang/Object"/*nonNLS*/.toCharArray();
	char[] JavaLangStringConstantPoolName = "java/lang/String"/*nonNLS*/.toCharArray();
	char[] JavaLangStringBufferConstantPoolName = "java/lang/StringBuffer"/*nonNLS*/.toCharArray();
	char[] JavaLangClassConstantPoolName = "java/lang/Class"/*nonNLS*/.toCharArray();
	char[] JavaLangThrowableConstantPoolName = "java/lang/Throwable"/*nonNLS*/.toCharArray();
	char[] JavaLangClassNotFoundExceptionConstantPoolName = "java/lang/ClassNotFoundException"/*nonNLS*/.toCharArray();
	char[] JavaLangNoClassDefFoundErrorConstantPoolName = "java/lang/NoClassDefFoundError"/*nonNLS*/.toCharArray();
	char[] JavaLangIntegerConstantPoolName = "java/lang/Integer"/*nonNLS*/.toCharArray();
	char[] JavaLangFloatConstantPoolName = "java/lang/Float"/*nonNLS*/.toCharArray();
	char[] JavaLangDoubleConstantPoolName = "java/lang/Double"/*nonNLS*/.toCharArray();
	char[] JavaLangLongConstantPoolName = "java/lang/Long"/*nonNLS*/.toCharArray();
	char[] JavaLangShortConstantPoolName = "java/lang/Short"/*nonNLS*/.toCharArray();
	char[] JavaLangByteConstantPoolName = "java/lang/Byte"/*nonNLS*/.toCharArray();
	char[] JavaLangCharacterConstantPoolName = "java/lang/Character"/*nonNLS*/.toCharArray();
	char[] JavaLangVoidConstantPoolName = "java/lang/Void"/*nonNLS*/.toCharArray();
	char[] JavaLangBooleanConstantPoolName = "java/lang/Boolean"/*nonNLS*/.toCharArray();
	char[] JavaLangSystemConstantPoolName = "java/lang/System"/*nonNLS*/.toCharArray();
	char[] JavaLangErrorConstantPoolName = "java/lang/Error"/*nonNLS*/.toCharArray();
	char[] JavaLangExceptionConstantPoolName = "java/lang/Exception"/*nonNLS*/.toCharArray();
	char[] JavaLangReflectConstructor = "java/lang/reflect/Constructor"/*nonNLS*/.toCharArray();  
	char[] Append = new char[] {'a', 'p', 'p', 'e', 'n', 'd'};
	char[] ToString = new char[] {'t', 'o', 'S', 't', 'r', 'i', 'n', 'g'};
	char[] Init = new char[] {'<', 'i', 'n', 'i', 't', '>'};
	char[] Clinit = new char[] {'<', 'c', 'l', 'i', 'n', 'i', 't', '>'};
	char[] ValueOf = new char[] {'v', 'a', 'l', 'u', 'e', 'O', 'f'};
	char[] ForName = new char[] {'f', 'o', 'r', 'N', 'a', 'm', 'e'};
	char[] GetMessage = new char[] {'g', 'e', 't', 'M', 'e', 's', 's', 'a', 'g', 'e'};
	char[] NewInstance = "newInstance"/*nonNLS*/.toCharArray();
	char[] GetConstructor = "getConstructor"/*nonNLS*/.toCharArray();
	char[] Exit = new char[] {'e', 'x', 'i', 't'};
	char[] Intern = "intern"/*nonNLS*/.toCharArray();
	char[] Out = new char[] {'o', 'u', 't'};
	char[] TYPE = new char[] {'T', 'Y', 'P', 'E'};
	char[] This = new char[] {'t', 'h', 'i', 's'};
	char[] JavaLangClassSignature = new char[] {'L', 'j', 'a', 'v', 'a', '/', 'l', 'a', 'n', 'g', '/', 'C', 'l', 'a', 's', 's', ';'};
	char[] ForNameSignature = "(Ljava/lang/String;)Ljava/lang/Class;"/*nonNLS*/.toCharArray();
	char[] GetMessageSignature = "()Ljava/lang/String;"/*nonNLS*/.toCharArray();
	char[] GetConstructorSignature = "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;"/*nonNLS*/.toCharArray();
	char[] StringConstructorSignature = "(Ljava/lang/String;)V"/*nonNLS*/.toCharArray();
	char[] NewInstanceSignature = "([Ljava/lang/Object;)Ljava/lang/Object;"/*nonNLS*/.toCharArray();
	char[] DefaultConstructorSignature = {'(', ')', 'V'};
	char[] ClinitSignature = DefaultConstructorSignature;
	char[] ToStringSignature = GetMessageSignature;
	char[] InternSignature = GetMessageSignature;
	char[] AppendIntSignature = "(I)Ljava/lang/StringBuffer;"/*nonNLS*/.toCharArray();
	char[] AppendLongSignature = "(J)Ljava/lang/StringBuffer;"/*nonNLS*/.toCharArray();
	char[] AppendFloatSignature = "(F)Ljava/lang/StringBuffer;"/*nonNLS*/.toCharArray();
	char[] AppendDoubleSignature = "(D)Ljava/lang/StringBuffer;"/*nonNLS*/.toCharArray();
	char[] AppendCharSignature = "(C)Ljava/lang/StringBuffer;"/*nonNLS*/.toCharArray();
	char[] AppendBooleanSignature = "(Z)Ljava/lang/StringBuffer;"/*nonNLS*/.toCharArray();
	char[] AppendObjectSignature = "(Ljava/lang/Object;)Ljava/lang/StringBuffer;"/*nonNLS*/.toCharArray();
	char[] AppendStringSignature = "(Ljava/lang/String;)Ljava/lang/StringBuffer;"/*nonNLS*/.toCharArray();
	char[] ValueOfObjectSignature = "(Ljava/lang/Object;)Ljava/lang/String;"/*nonNLS*/.toCharArray();
	char[] ValueOfIntSignature = "(I)Ljava/lang/String;"/*nonNLS*/.toCharArray();
	char[] ValueOfLongSignature = "(J)Ljava/lang/String;"/*nonNLS*/.toCharArray();
	char[] ValueOfCharSignature = "(C)Ljava/lang/String;"/*nonNLS*/.toCharArray();
	char[] ValueOfBooleanSignature = "(Z)Ljava/lang/String;"/*nonNLS*/.toCharArray();
	char[] ValueOfDoubleSignature = "(D)Ljava/lang/String;"/*nonNLS*/.toCharArray();
	char[] ValueOfFloatSignature = "(F)Ljava/lang/String;"/*nonNLS*/.toCharArray();
	char[] JavaIoPrintStreamSignature = "Ljava/io/PrintStream;"/*nonNLS*/.toCharArray();
	char[] ExitIntSignature = new char[] {'(', 'I', ')', 'V'};
	char[] ArrayJavaLangObjectConstantPoolName = "[Ljava/lang/Object;"/*nonNLS*/.toCharArray();
	char[] ArrayJavaLangClassConstantPoolName = "[Ljava/lang/Class;"/*nonNLS*/.toCharArray();

}
