/*******************************************************************************
 * Copyright (c) 2000, 2020 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.core.builder;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.batch.FileSystem;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.env.IModule;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.parser.Parser;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;
import org.eclipse.jdt.internal.compiler.problem.ProblemReporter;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.internal.core.ModuleDescriptionInfo;
import org.eclipse.jdt.internal.core.util.Util;

public class ClasspathMultiDirectory extends ClasspathDirectory {

IContainer sourceFolder;
char[][] inclusionPatterns; // used by builders when walking source folders
char[][] exclusionPatterns; // used by builders when walking source folders
boolean hasIndependentOutputFolder; // if output folder is not equal to any of the source folders
public boolean ignoreOptionalProblems;
int release; // if given this sets an explicit release level for this directory overriding project settings

ClasspathMultiDirectory(IContainer sourceFolder, IContainer binaryFolder, char[][] inclusionPatterns, char[][] exclusionPatterns,
		boolean ignoreOptionalProblems, IPath externalAnnotationPath, int release) {
	super(binaryFolder, true, null, externalAnnotationPath, false /* source never an automatic module*/);

	this.sourceFolder = sourceFolder;
	this.inclusionPatterns = inclusionPatterns;
	this.exclusionPatterns = exclusionPatterns;
	this.release = release>=JavaProject.FIRST_MULTI_RELEASE?release:JavaProject.NO_RELEASE;
	this.hasIndependentOutputFolder = false;
	this.ignoreOptionalProblems = ignoreOptionalProblems;

	// handle the case when a state rebuilds a source folder
	if (this.inclusionPatterns != null && this.inclusionPatterns.length == 0)
		this.inclusionPatterns = null;
	if (this.exclusionPatterns != null && this.exclusionPatterns.length == 0)
		this.exclusionPatterns = null;
}

@Override
public boolean equals(Object o) {
	if (this == o) return true;
	if (!(o instanceof ClasspathMultiDirectory)) return false;

	ClasspathMultiDirectory md = (ClasspathMultiDirectory) o;
	// TODO: revisit this - is this really required??
//	if (this.module != md.module)
//		if (this.module == null || !this.module.equals(md.module))
//			return false;
	return this.ignoreOptionalProblems == md.ignoreOptionalProblems
		&& this.sourceFolder.equals(md.sourceFolder) && this.binaryFolder.equals(md.binaryFolder)
		&& CharOperation.equals(this.inclusionPatterns, md.inclusionPatterns)
		&& CharOperation.equals(this.exclusionPatterns, md.exclusionPatterns);
}

@Override
protected boolean isExcluded(IResource resource) {
	if (this.exclusionPatterns != null || this.inclusionPatterns != null)
		if (this.sourceFolder.equals(this.binaryFolder))
			return Util.isExcluded(resource, this.inclusionPatterns, this.exclusionPatterns);
	return false;
}
@Override
String[] directoryList(String qualifiedPackageName) {
	String[] dirList = this.directoryCache.get(qualifiedPackageName);
	if (dirList == this.missingPackageHolder) return null; // package exists in another classpath directory or jar
	if (dirList != null) return dirList;

	try {
		IResource container = this.binaryFolder.findMember(qualifiedPackageName); // this is a case-sensitive check
		if (container instanceof IContainer binaryContainer) {
			IResource[] members = binaryContainer.members();
			dirList = new String[members.length];
			int index = 0;
			boolean foundClass = false;
			if (members.length > 0) {
				for (IResource m : members) {
					String name = m.getName();
					boolean isClass = m.getType() == IResource.FILE && org.eclipse.jdt.internal.compiler.util.Util.isClassFileName(name);
					if (m.getType() == IResource.FOLDER || isClass) {
						// add exclusion pattern check here if we want to hide .class files
						dirList[index++] = name;
						foundClass |= isClass;
					}
				}
			}
			if(!foundClass) {
				container = this.sourceFolder.findMember(qualifiedPackageName);
				if (container instanceof IContainer sourceContainer) {
					members = sourceContainer.members();
					if (members.length > 0) {
						dirList = new String[members.length];
						index = 0;
						for (IResource m : members) {
							String name = m.getName();
							if (m.getType() == IResource.FOLDER
									|| (m.getType() == IResource.FILE && org.eclipse.jdt.internal.compiler.util.Util.isJavaFileName(name))) {
								// FIXME: check if .java file has any declarations?
								dirList[index++] = name;
							}
						}
					}
				}
			}
			if (index < dirList.length)
				System.arraycopy(dirList, 0, dirList = new String[index], 0, index);
			this.directoryCache.put(qualifiedPackageName, dirList);
			return dirList;
		}
	} catch(CoreException ignored) {
		// ignore
	}
	this.directoryCache.put(qualifiedPackageName, this.missingPackageHolder);
	return null;
}

@Override
public String toString() {
	return "Source classpath directory " + this.sourceFolder.getFullPath().toString() + //$NON-NLS-1$
		" with " + super.toString(); //$NON-NLS-1$
}

/**
 * Initialize module from module-info.java in the source folder.
 * This is used for multi-release compilation where each source folder
 * with a different release may have its own module-info.java.
 */
IModule initializeModuleFromSource() {
	try {
		IResource moduleInfoResource = this.sourceFolder.findMember("module-info.java"); //$NON-NLS-1$
		if (moduleInfoResource instanceof IFile moduleInfoFile && moduleInfoFile.exists()) {
			// Create a simple ICompilationUnit to read the file
			ICompilationUnit sourceUnit = new ICompilationUnit() {
				@Override
				public char[] getContents() {
					try {
						return Util.getResourceContentsAsCharArray(moduleInfoFile);
					} catch (CoreException e) {
						return CharOperation.NO_CHAR;
					}
				}
				@Override
				public char[] getMainTypeName() {
					return "module-info".toCharArray(); //$NON-NLS-1$
				}
				@Override
				public char[][] getPackageName() {
					return CharOperation.NO_CHAR_CHAR;
				}
				@Override
				public char[] getFileName() {
					return moduleInfoFile.getFullPath().toString().toCharArray();
				}
				@Override
				public boolean ignoreOptionalProblems() {
					return false;
				}
			};
			
			// Parse the module-info.java file
			CompilerOptions compilerOptions = new CompilerOptions();
			if (this.release != JavaProject.NO_RELEASE) {
				compilerOptions.sourceLevel = CompilerOptions.versionToJdkLevel(Integer.toString(this.release));
				compilerOptions.complianceLevel = compilerOptions.sourceLevel;
				compilerOptions.targetJDK = compilerOptions.sourceLevel;
			}
			
			ProblemReporter problemReporter = new ProblemReporter(
				DefaultErrorHandlingPolicies.proceedWithAllProblems(),
				compilerOptions,
				new DefaultProblemFactory());
			
			Parser parser = new Parser(problemReporter, false);
			parser.javadocParser.checkDocComment = false;
			
			CompilationResult compilationResult = new CompilationResult(sourceUnit, 0, 1, compilerOptions.maxProblemsPerUnit);
			CompilationUnitDeclaration parsedUnit = parser.parse(sourceUnit, compilationResult);
			
			if (parsedUnit.moduleDeclaration != null) {
				// Convert AST ModuleDeclaration to IModule
				ModuleDescriptionInfo moduleInfo = ModuleDescriptionInfo.createModule(parsedUnit.moduleDeclaration);
				return moduleInfo;
			}
		}
	} catch (Exception e) {
		// Log and continue - no module found or parsing failed
		// This is not a critical error as the source folder may legitimately not have a module-info.java
		if (JavaModelManager.VERBOSE) {
			JavaModelManager.trace("Failed to parse module-info.java from " + this.sourceFolder.getFullPath(), e); //$NON-NLS-1$
		}
	}
	return null;
}

}
