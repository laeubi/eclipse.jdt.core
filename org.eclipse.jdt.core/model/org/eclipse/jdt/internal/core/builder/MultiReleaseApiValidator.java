/*******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Eclipse Copilot - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.core.builder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.eclipse.jdt.internal.compiler.env.IBinaryField;
import org.eclipse.jdt.internal.compiler.env.IBinaryMethod;
import org.eclipse.jdt.internal.compiler.env.IBinaryType;

/**
 * Validates that Multi-Release types maintain API compatibility across versions.
 * The contract for a Multi-Release type is that its public API must be the same
 * across all versions - later versions cannot remove or incompatibly change
 * public methods, fields, or signatures.
 */
public class MultiReleaseApiValidator {

	private final AbstractImageBuilder imageBuilder;
	private final Map<String, ApiSignature> baselineSignatures = new HashMap<>();
	
	public MultiReleaseApiValidator(AbstractImageBuilder imageBuilder) {
		this.imageBuilder = imageBuilder;
	}
	
	/**
	 * Validates API compatibility for all Multi-Release types in the project.
	 * Should be called after all compilation is complete.
	 */
	public void validateApiCompatibility() throws CoreException {
		// Collect all multi-release types
		Map<String, List<TypeVersion>> typeVersions = collectMultiReleaseTypes();
		
		// Validate each type
		for (Map.Entry<String, List<TypeVersion>> entry : typeVersions.entrySet()) {
			String typeName = entry.getKey();
			List<TypeVersion> versions = entry.getValue();
			
			// Sort by version (default/base version first, then ascending)
			versions.sort((a, b) -> {
				if (a.version == -1) return -1;
				if (b.version == -1) return 1;
				return Integer.compare(a.version, b.version);
			});
			
			// Validate compatibility across versions
			if (versions.size() > 1) {
				validateTypeVersions(typeName, versions);
			}
		}
	}
	
	private Map<String, List<TypeVersion>> collectMultiReleaseTypes() throws CoreException {
		Map<String, List<TypeVersion>> typeVersions = new HashMap<>();
		
		// Iterate through all source locations
		for (ClasspathMultiDirectory sourceLocation : this.imageBuilder.sourceLocations) {
			IContainer outputFolder = sourceLocation.binaryFolder;
			
			// Check for default version (base) classes
			collectTypesFromFolder(outputFolder, "", -1, typeVersions);
			
			// Check for versioned classes in META-INF/versions/X/
			IFolder metaInfFolder = outputFolder.getFolder(new Path("META-INF/versions"));
			if (metaInfFolder.exists()) {
				IResource[] versionFolders = metaInfFolder.members();
				for (IResource versionResource : versionFolders) {
					if (versionResource instanceof IFolder versionFolder) {
						try {
							int version = Integer.parseInt(versionFolder.getName());
							collectTypesFromFolder(versionFolder, "", version, typeVersions);
						} catch (NumberFormatException e) {
							// Skip non-numeric folders
						}
					}
				}
			}
		}
		
		return typeVersions;
	}
	
	private void collectTypesFromFolder(IContainer folder, String packagePrefix, int version,
			Map<String, List<TypeVersion>> typeVersions) throws CoreException {
		IResource[] members = folder.members();
		for (IResource member : members) {
			if (member instanceof IFile file) {
				String name = file.getName();
				if (name.endsWith(".class")) {
					String typeName = packagePrefix + name.substring(0, name.length() - 6);
					typeVersions.computeIfAbsent(typeName, k -> new ArrayList<>())
						.add(new TypeVersion(file, version));
				}
			} else if (member instanceof IFolder subFolder) {
				String newPrefix = packagePrefix.isEmpty() ? subFolder.getName() + "/" 
						: packagePrefix + subFolder.getName() + "/";
				collectTypesFromFolder(subFolder, newPrefix, version, typeVersions);
			}
		}
	}
	
	private void validateTypeVersions(String typeName, List<TypeVersion> versions) {
		TypeVersion baseVersion = versions.get(0);
		ApiSignature baseSignature = readApiSignature(baseVersion);
		
		if (baseSignature == null) {
			return; // Failed to read base version
		}
		
		// Compare each later version against the base
		for (int i = 1; i < versions.size(); i++) {
			TypeVersion laterVersion = versions.get(i);
			ApiSignature laterSignature = readApiSignature(laterVersion);
			
			if (laterSignature == null) {
				continue; // Failed to read this version
			}
			
			validateCompatibility(typeName, baseVersion, baseSignature, 
					laterVersion, laterSignature);
		}
	}
	
	private void validateCompatibility(String typeName, TypeVersion baseVersion, 
			ApiSignature baseSignature, TypeVersion laterVersion, ApiSignature laterSignature) {
		
		// Find the source file for the later version
		IFile sourceFile = findSourceFile(typeName, laterVersion.version);
		if (sourceFile == null) {
			// Can't find source file, report on class file
			sourceFile = laterVersion.classFile;
		}
		
		// Check for removed public methods
		for (MethodSignature baseMethod : baseSignature.publicMethods) {
			if (!laterSignature.publicMethods.contains(baseMethod)) {
				String methodName = new String(baseMethod.selector);
				String message = String.format(
					"Multi-Release type '%s': public method '%s' from base version is missing in version %d",
					typeName.replace('/', '.'), methodName, laterVersion.version);
				reportProblem(sourceFile, message);
			}
		}
		
		// Check for removed public fields
		for (FieldSignature baseField : baseSignature.publicFields) {
			if (!laterSignature.publicFields.contains(baseField)) {
				String fieldName = new String(baseField.name);
				String message = String.format(
					"Multi-Release type '%s': public field '%s' from base version is missing in version %d",
					typeName.replace('/', '.'), fieldName, laterVersion.version);
				reportProblem(sourceFile, message);
			}
		}
	}
	
	private IFile findSourceFile(String typeName, int version) {
		// Try to find the source file that produced this class
		for (ClasspathMultiDirectory sourceLocation : this.imageBuilder.sourceLocations) {
			if (version == -1 || sourceLocation.release == version) {
				// Construct the expected source file path
				String javaFileName = typeName + ".java";
				IFile sourceFile = sourceLocation.sourceFolder.getFile(new Path(javaFileName));
				if (sourceFile != null && sourceFile.exists()) {
					return sourceFile;
				}
			}
		}
		return null;
	}
	
	private ApiSignature readApiSignature(TypeVersion version) {
		try {
			File classFile = version.classFile.getLocation().toFile();
			try (FileInputStream stream = new FileInputStream(classFile)) {
				ClassFileReader reader = ClassFileReader.read(stream, classFile.getAbsolutePath());
				return extractApiSignature(reader);
			}
		} catch (ClassFormatException | IOException e) {
			// Log error but continue
			return null;
		}
	}
	
	private ApiSignature extractApiSignature(IBinaryType type) {
		ApiSignature signature = new ApiSignature();
		
		// Extract public methods
		IBinaryMethod[] methods = type.getMethods();
		if (methods != null) {
			for (IBinaryMethod method : methods) {
				if (isPublic(method.getModifiers())) {
					signature.publicMethods.add(new MethodSignature(
						method.getSelector(),
						method.getMethodDescriptor()
					));
				}
			}
		}
		
		// Extract public fields
		IBinaryField[] fields = type.getFields();
		if (fields != null) {
			for (IBinaryField field : fields) {
				if (isPublic(field.getModifiers())) {
					signature.publicFields.add(new FieldSignature(
						field.getName(),
						field.getTypeName()
					));
				}
			}
		}
		
		return signature;
	}
	
	private boolean isPublic(int modifiers) {
		return (modifiers & 0x0001) != 0; // ACC_PUBLIC
	}
	
	private void reportProblem(IFile resource, String message) {
		try {
			this.imageBuilder.createProblemFor(resource, null, message, org.eclipse.jdt.core.JavaCore.ERROR);
		} catch (CoreException e) {
			// Log but continue
		}
	}
	
	private static class TypeVersion {
		final IFile classFile;
		final int version; // -1 for base/default version
		
		TypeVersion(IFile classFile, int version) {
			this.classFile = classFile;
			this.version = version;
		}
	}
	
	private static class ApiSignature {
		final Set<MethodSignature> publicMethods = new HashSet<>();
		final Set<FieldSignature> publicFields = new HashSet<>();
	}
	
	private static class MethodSignature {
		final char[] selector;
		final char[] descriptor;
		
		MethodSignature(char[] selector, char[] descriptor) {
			this.selector = selector;
			this.descriptor = descriptor;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof MethodSignature other)) return false;
			return CharOperation.equals(this.selector, other.selector) &&
				   CharOperation.equals(this.descriptor, other.descriptor);
		}
		
		@Override
		public int hashCode() {
			return CharOperation.hashCode(this.selector) * 31 + 
				   CharOperation.hashCode(this.descriptor);
		}
	}
	
	private static class FieldSignature {
		final char[] name;
		final char[] typeName;
		
		FieldSignature(char[] name, char[] typeName) {
			this.name = name;
			this.typeName = typeName;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof FieldSignature other)) return false;
			return CharOperation.equals(this.name, other.name) &&
				   CharOperation.equals(this.typeName, other.typeName);
		}
		
		@Override
		public int hashCode() {
			return CharOperation.hashCode(this.name) * 31 + 
				   CharOperation.hashCode(this.typeName);
		}
	}
}
