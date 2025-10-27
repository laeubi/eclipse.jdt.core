/*******************************************************************************
 * Copyright (c) 2025 Christoph Läubrich and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Christoph Läubrich - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.core.tests.builder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import junit.framework.Test;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.tests.util.Util;
import org.eclipse.jdt.core.util.IClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;

public class MultiReleaseTests extends BuilderTests {

	private static final String JAVA9_SRC_FOLDER = "src9";
	private static final String DEFAULT_SRC_FOLDER = "src";

	public MultiReleaseTests(String name) {
		super(name);
	}

	public static Test suite() {
		return buildTestSuite(MultiReleaseTests.class);
	}

	public void testMultiReleaseCompile() throws JavaModelException, IOException {
		IPath projectPath = whenSetupMRRpoject();
		fullBuild();
		expectingNoProblems();
		expectingMultiReleaseClasses(projectPath);
	}

	public void testMultiReleaseCompileWithHigherMain() throws JavaModelException, IOException {
		IPath projectPath = whenSetupMRRpoject(CompilerOptions.VERSION_11);
		fullBuild();
		IPath src9 = projectPath.append(JAVA9_SRC_FOLDER);
		expectingOnlySpecificProblemFor(src9, new Problem("", "Target release for src9 (9) is lower or equal to the project's default release (11).", src9, 0, 1, -1, IMarker.SEVERITY_ERROR, "JDT"));
	}

	public void testMultiReleaseCompileWithMultipleFolders() throws JavaModelException, IOException {
		IPath projectPath = whenSetupMRRpoject();
		IClasspathAttribute[] extraAttributes = new IClasspathAttribute[] {
				JavaCore.newClasspathAttribute(IClasspathAttribute.RELEASE, org.eclipse.jdt.core.JavaCore.VERSION_9) };
		IPath release9Src2 = env.addPackageFragmentRoot(projectPath, "src9_2", extraAttributes);
		env.addClass(release9Src2, "p", "Release9Type",
				"""
				package p;
				public class Release9Type {

				}
				"""
		);
		fullBuild();
		expectingNoProblems();
		expectingMultiReleaseClasses(projectPath);
		assertJavaVersion(projectPath.append("bin/META-INF/versions/9/p/Release9Type.class"), ClassFileConstants.MAJOR_VERSION_9);
	}

	public void testMultiReleaseWithMultipleReleaseFolders() throws JavaModelException, IOException {
		IPath projectPath = whenSetupMRRpoject();
		IPath defaultP = env.getPackageFragmentRootPath(projectPath, DEFAULT_SRC_FOLDER);
		IPath j9P = env.getPackageFragmentRootPath(projectPath, JAVA9_SRC_FOLDER);
		IPath j21P = env.addPackageFragmentRoot(projectPath, "src21", new IClasspathAttribute[] {
				JavaCore.newClasspathAttribute(IClasspathAttribute.RELEASE, org.eclipse.jdt.core.JavaCore.VERSION_21) });
		//Default release (1.8)
		env.addClass(defaultP, "p", "A",
				"""
				package p;
				public class A {

				}
				"""
		);
		env.addClass(defaultP, "p", "B",
				"""
				package p;
				public class B {

				}
				"""
		);
		//Java 9
		env.addClass(j9P, "p", "B",
				"""
				package p;
				public class B {
					A fieldA;
				}
				"""
		);
		env.addClass(j9P, "p", "C",
				"""
				package p;
				class C {

				}
				"""
		);
		//java 21
		env.addClass(j21P, "p", "B",
				"""
				package p;
				public class B {
					A fieldA;
					C fieldC;
					public void m() {
						// nop
					}
				}
				"""
		);
		IPath javaClass = env.addClass(j21P, "p", "D",
				"""
				package p;
					public class D {
						void m(B b) {
							b.m();
						}
					}
				"""
		);
		fullBuild();
		expectingNoProblems();
		assertJavaVersion(projectPath.append("bin/p/A.class"), ClassFileConstants.MAJOR_VERSION_1_8);
		assertJavaVersion(projectPath.append("bin/p/B.class"), ClassFileConstants.MAJOR_VERSION_1_8);
		assertJavaVersion(projectPath.append("bin/META-INF/versions/9/p/B.class"), ClassFileConstants.MAJOR_VERSION_9);
		assertJavaVersion(projectPath.append("bin/META-INF/versions/9/p/C.class"), ClassFileConstants.MAJOR_VERSION_9);
		assertJavaVersion(projectPath.append("bin/META-INF/versions/21/p/B.class"), ClassFileConstants.MAJOR_VERSION_21);
		assertJavaVersion(projectPath.append("bin/META-INF/versions/21/p/D.class"), ClassFileConstants.MAJOR_VERSION_21);
		env.editClass(javaClass,
				"""
				package p;
					public class D {
						void m(B b) {
							b.m();
							if (b.fieldC != null) {
								//hurray!
							}
						}
					}
				"""
		);
		incrementalBuild(projectPath);
		expectingNoProblems();
	}

	public void testMultiReleaseCompileWithDisabledTargetOption() throws JavaModelException, IOException {
		IPath projectPath = whenSetupMRRpoject();
		env.setProjectOption(projectPath, "org.eclipse.jdt.core.compiler.release", "disabled");
		IPath src9 = env.getPackageFragmentRootPath(projectPath, JAVA9_SRC_FOLDER);
		env.addClass(src9, "p", "NotInThisRelease",
				"""
				package p;

				import java.net.MalformedURLException;
				import java.net.URI;
				import java.net.URISyntaxException;
				import java.net.URL;

				public class NotInThisRelease {
					public URL create() throws MalformedURLException, URISyntaxException {
						// Only Java 20+ --> must fail!
						return URL.of(new URI("http://foo.org"), null);
					}
				}
				"""
		);
		IClasspathAttribute[] extraAttributes = new IClasspathAttribute[] {
				JavaCore.newClasspathAttribute(IClasspathAttribute.RELEASE, org.eclipse.jdt.core.JavaCore.VERSION_21) };
		IPath src21 = env.addPackageFragmentRoot(projectPath, "src11", extraAttributes);
		env.addClass(src21, "p", "ButInThisRelease",
				"""
				package p;

				import java.net.MalformedURLException;
				import java.net.URI;
				import java.net.URISyntaxException;
				import java.net.URL;

				public class ButInThisRelease {
					public URL create() throws MalformedURLException, URISyntaxException {
						// For Java 21 it will work!
						return URL.of(new URI("http://foo.org"), null);
					}
				}
				"""
		);
		fullBuild();
		Problem java9problem = new Problem("", "The method of(URI, null) is undefined for the type URL", src9.append("p/NotInThisRelease.java"), 281, 283, 50, IMarker.SEVERITY_ERROR, "JDT");
		expectingOnlySpecificProblemFor(src9, java9problem);
		expectingNoProblemsFor(src21);
		expectingOnlySpecificProblemsFor(projectPath, new Problem[] {java9problem, new Problem("", "Multi-Release Compilation requires the release option enabled in the project settings", projectPath, 0, 1, -1, IMarker.SEVERITY_ERROR, "JDT")});
	}

	public void testMultiReleaseCompileWithConflict() throws JavaModelException, IOException {
		IPath projectPath = whenSetupMRRpoject();
		IClasspathAttribute[] extraAttributes = new IClasspathAttribute[] {
				JavaCore.newClasspathAttribute(IClasspathAttribute.RELEASE, org.eclipse.jdt.core.JavaCore.VERSION_9) };
		IPath release9Src2 = env.addPackageFragmentRoot(projectPath, "src9_2", extraAttributes);
		env.addClass(release9Src2, "p", "MultiReleaseType",
				"""
				package p;
				public class MultiReleaseType {

				}
				"""
		);
		fullBuild();
		expectingOnlySpecificProblemFor(release9Src2, new Problem("", "The type MultiReleaseType is already defined", release9Src2.append("p/MultiReleaseType.java"), 24, 40, CategorizedProblem.CAT_TYPE, IMarker.SEVERITY_ERROR));
	}

	public void testMultiReleaseCompileInterDependency() throws JavaModelException, IOException {
		IPath projectMr = whenSetupMRRpoject();
		IPath projectPlain = env.addProject("P2", CompilerOptions.VERSION_1_8);
		env.removePackageFragmentRoot(projectPlain, "");
		IPath defaultSrc = env.addPackageFragmentRoot(projectPlain, DEFAULT_SRC_FOLDER);
		env.addRequiredProject(projectPlain, projectMr);
		env.addExternalJars(projectPlain, Util.getJavaClassLibs());
		env.addClass(defaultSrc, "mypackage", "UsingMultiReleaseType",
				"""
				package mypackage;
				import p.MultiReleaseType;
				public class UsingMultiReleaseType {
					public void testRef() {
						MultiReleaseType t = new MultiReleaseType();
						System.out.println(t.print());
					}
				}
				"""
		);
		fullBuild();
		expectingNoProblems();

	}

	public void testMultiReleaseWithModularProjectAndReleaseEnabled() throws JavaModelException, IOException {
		IPath projectPath = createMRProject(CompilerOptions.VERSION_9);
		IClasspathAttribute[] extraAttributes = new IClasspathAttribute[] {
				JavaCore.newClasspathAttribute(IClasspathAttribute.RELEASE, org.eclipse.jdt.core.JavaCore.VERSION_11) };
		IPath release11Src = env.addPackageFragmentRoot(projectPath, "src11", extraAttributes);
		env.addClass(release11Src, "p", "MultiReleaseType",
				"""
				package p;

				import java.net.http.HttpClient;

				public class MultiReleaseType {
					public String print() {
						HttpClient.newBuilder().build();
						return "Hello From Release 11";
					}
				}
				"""
		);
		fullBuild();
		expectingNoProblems();
	}

	private IPath whenSetupMRRpoject() throws JavaModelException {
		return whenSetupMRRpoject(CompilerOptions.VERSION_1_8);
	}

	private IPath whenSetupMRRpoject(String compliance) throws JavaModelException {
		IPath projectPath = createMRProject(compliance);
		IClasspathAttribute[] extraAttributes = new IClasspathAttribute[] {
				JavaCore.newClasspathAttribute(IClasspathAttribute.RELEASE, org.eclipse.jdt.core.JavaCore.VERSION_9) };
		IPath release9Src = env.addPackageFragmentRoot(projectPath, JAVA9_SRC_FOLDER, extraAttributes);
		env.addClass(release9Src, "p", "MultiReleaseType",
				"""
				package p;
				public class MultiReleaseType {
					public String print() {
						return "Hello From Release 9";
					}
				}
				"""
		);
		return projectPath;
	}

	protected IPath createMRProject(String compliance) throws JavaModelException {
		IPath projectPath = env.addProject("P", compliance);
		env.removePackageFragmentRoot(projectPath, "");
		env.setProjectOption(projectPath, "org.eclipse.jdt.core.compiler.release", "enabled");
		IPath defaultSrc = env.addPackageFragmentRoot(projectPath, DEFAULT_SRC_FOLDER);
		env.setOutputFolder(projectPath, "bin");
		env.addExternalJars(projectPath, Util.getJavaClassLibs());
		env.addClass(defaultSrc, "p", "MultiReleaseType",
				"""
				package p;
				public class MultiReleaseType {
					public String print() {
						return "Hello From Default Release";
					}
				}
				"""
				);
		return projectPath;
	}

	private void expectingMultiReleaseClasses(IPath projectPath) throws IOException, FileNotFoundException {
		IPath defaultReleaseClass = projectPath.append("bin/p/MultiReleaseType.class");
		IPath java9ReleaseClass = projectPath.append("bin/META-INF/versions/9/p/MultiReleaseType.class");
		assertJavaVersion(defaultReleaseClass, ClassFileConstants.MAJOR_VERSION_1_8);
		assertJavaVersion(java9ReleaseClass, ClassFileConstants.MAJOR_VERSION_9);
	}

	private void assertJavaVersion(IPath clazz, int javaVersion) throws IOException, FileNotFoundException {
		assertEquals("Wrong java version for "+clazz, javaVersion, getMajorVersionOfClass(clazz));
	}

	private int getMajorVersionOfClass(IPath clazz) throws IOException, FileNotFoundException {
		expectingPresenceOf(clazz);
		File classFile = env.getWorkspaceRootPath().append(clazz).toFile();
		assertNotNull(classFile);
		IClassFileReader reader;
		try (FileInputStream stream = new FileInputStream(classFile)) {
			reader = ToolFactory.createDefaultClassFileReader(stream, IClassFileReader.ALL);
		}
		return reader.getMajorVersion();
	}

	// Tests for Multi-Release API contract validation

	public void testMultiReleaseApiContractViolation_RemovedMethod() throws JavaModelException, IOException {
		IPath projectPath = createMRProject(CompilerOptions.VERSION_1_8);
		IPath defaultSrc = env.getPackageFragmentRootPath(projectPath, DEFAULT_SRC_FOLDER);
		IClasspathAttribute[] extraAttributes = new IClasspathAttribute[] {
				JavaCore.newClasspathAttribute(IClasspathAttribute.RELEASE, org.eclipse.jdt.core.JavaCore.VERSION_9) };
		IPath release9Src = env.addPackageFragmentRoot(projectPath, JAVA9_SRC_FOLDER, extraAttributes);
		
		// Base version has a public method sayHello()
		env.addClass(defaultSrc, "p", "MRType",
				"""
				package p;
				public class MRType {
					public String sayHello() {
						return "Hello";
					}
				}
				"""
		);
		
		// Java 9 version removes the sayHello() method - this violates the API contract
		env.addClass(release9Src, "p", "MRType",
				"""
				package p;
				public class MRType {
					// sayHello() method removed - API contract violation!
				}
				"""
		);
		
		fullBuild();
		// Should have an error about the missing method
		expectingSpecificProblemFor(release9Src, new Problem("", 
			"Multi-Release type 'p.MRType': public method 'sayHello' from base version is missing in version 9", 
			release9Src.append("p/MRType.java"), -1, -1, -1, IMarker.SEVERITY_ERROR));
	}

	public void testMultiReleaseApiContractViolation_ChangedMethodSignature() throws JavaModelException, IOException {
		IPath projectPath = createMRProject(CompilerOptions.VERSION_1_8);
		IPath defaultSrc = env.getPackageFragmentRootPath(projectPath, DEFAULT_SRC_FOLDER);
		IClasspathAttribute[] extraAttributes = new IClasspathAttribute[] {
				JavaCore.newClasspathAttribute(IClasspathAttribute.RELEASE, org.eclipse.jdt.core.JavaCore.VERSION_9) };
		IPath release9Src = env.addPackageFragmentRoot(projectPath, JAVA9_SRC_FOLDER, extraAttributes);
		
		// Base version has sayHello() with no parameters
		env.addClass(defaultSrc, "p", "MRType",
				"""
				package p;
				public class MRType {
					public String sayHello() {
						return "Hello";
					}
				}
				"""
		);
		
		// Java 9 version changes signature to have a parameter - this violates the API contract
		env.addClass(release9Src, "p", "MRType",
				"""
				package p;
				import java.util.Locale;
				public class MRType {
					public String sayHello(Locale locale) {
						return "Hello";
					}
				}
				"""
		);
		
		fullBuild();
		// Should have an error about the missing method (original signature)
		expectingSpecificProblemFor(release9Src, new Problem("", 
			"Multi-Release type 'p.MRType': public method 'sayHello' from base version is missing in version 9", 
			release9Src.append("p/MRType.java"), -1, -1, -1, IMarker.SEVERITY_ERROR));
	}

	public void testMultiReleaseApiContractValid_AddedMethod() throws JavaModelException, IOException {
		IPath projectPath = createMRProject(CompilerOptions.VERSION_1_8);
		IPath defaultSrc = env.getPackageFragmentRootPath(projectPath, DEFAULT_SRC_FOLDER);
		IClasspathAttribute[] extraAttributes = new IClasspathAttribute[] {
				JavaCore.newClasspathAttribute(IClasspathAttribute.RELEASE, org.eclipse.jdt.core.JavaCore.VERSION_9) };
		IPath release9Src = env.addPackageFragmentRoot(projectPath, JAVA9_SRC_FOLDER, extraAttributes);
		
		// Base version has sayHello()
		env.addClass(defaultSrc, "p", "MRType",
				"""
				package p;
				public class MRType {
					public String sayHello() {
						return "Hello";
					}
				}
				"""
		);
		
		// Java 9 version keeps sayHello() and adds a new method - this is valid
		env.addClass(release9Src, "p", "MRType",
				"""
				package p;
				import java.util.Locale;
				public class MRType {
					public String sayHello() {
						return "Hello";
					}
					public String sayHello(Locale locale) {
						return "Hello from locale";
					}
				}
				"""
		);
		
		fullBuild();
		expectingNoProblems();
	}

	public void testMultiReleaseApiContractViolation_RemovedField() throws JavaModelException, IOException {
		IPath projectPath = createMRProject(CompilerOptions.VERSION_1_8);
		IPath defaultSrc = env.getPackageFragmentRootPath(projectPath, DEFAULT_SRC_FOLDER);
		IClasspathAttribute[] extraAttributes = new IClasspathAttribute[] {
				JavaCore.newClasspathAttribute(IClasspathAttribute.RELEASE, org.eclipse.jdt.core.JavaCore.VERSION_9) };
		IPath release9Src = env.addPackageFragmentRoot(projectPath, JAVA9_SRC_FOLDER, extraAttributes);
		
		// Base version has a public field
		env.addClass(defaultSrc, "p", "MRType",
				"""
				package p;
				public class MRType {
					public String message = "Hello";
				}
				"""
		);
		
		// Java 9 version removes the field - this violates the API contract
		env.addClass(release9Src, "p", "MRType",
				"""
				package p;
				public class MRType {
					// message field removed - API contract violation!
				}
				"""
		);
		
		fullBuild();
		// Should have an error about the missing field
		expectingSpecificProblemFor(release9Src, new Problem("", 
			"Multi-Release type 'p.MRType': public field 'message' from base version is missing in version 9", 
			release9Src.append("p/MRType.java"), -1, -1, -1, IMarker.SEVERITY_ERROR));
	}

	public void testMultiReleaseApiContractValid_MultipleVersions() throws JavaModelException, IOException {
		IPath projectPath = createMRProject(CompilerOptions.VERSION_1_8);
		IPath defaultSrc = env.getPackageFragmentRootPath(projectPath, DEFAULT_SRC_FOLDER);
		IClasspathAttribute[] java9Attributes = new IClasspathAttribute[] {
				JavaCore.newClasspathAttribute(IClasspathAttribute.RELEASE, org.eclipse.jdt.core.JavaCore.VERSION_9) };
		IPath release9Src = env.addPackageFragmentRoot(projectPath, JAVA9_SRC_FOLDER, java9Attributes);
		IClasspathAttribute[] java11Attributes = new IClasspathAttribute[] {
				JavaCore.newClasspathAttribute(IClasspathAttribute.RELEASE, org.eclipse.jdt.core.JavaCore.VERSION_11) };
		IPath release11Src = env.addPackageFragmentRoot(projectPath, "src11", java11Attributes);
		
		// Base version
		env.addClass(defaultSrc, "p", "MRType",
				"""
				package p;
				public class MRType {
					public String sayHello() {
						return "Hello";
					}
				}
				"""
		);
		
		// Java 9 version maintains API
		env.addClass(release9Src, "p", "MRType",
				"""
				package p;
				public class MRType {
					public String sayHello() {
						return "Hello from Java 9";
					}
				}
				"""
		);
		
		// Java 11 version also maintains API
		env.addClass(release11Src, "p", "MRType",
				"""
				package p;
				public class MRType {
					public String sayHello() {
						return "Hello from Java 11";
					}
				}
				"""
		);
		
		fullBuild();
		expectingNoProblems();
	}

}
