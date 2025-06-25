/*******************************************************************************
 * Copyright (c) 2016, 2022 IBM Corporation and others.
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

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.eclipse.jdt.internal.compiler.env.AccessRuleSet;
import org.eclipse.jdt.internal.compiler.env.IReleaseAwareNameEnvironment;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.eclipse.jdt.internal.core.util.Util;

public class ClasspathJrtWithReleaseOption extends ClasspathJrt {

	final String release;
	private int releaseNumber;
	private JrtReleaseClasses jrtRelease;

	public ClasspathJrtWithReleaseOption(String zipFilename, AccessRuleSet accessRuleSet, IPath externalAnnotationPath,
			String release) throws CoreException {
		super(zipFilename);
		if (release == null || release.equals("")) { //$NON-NLS-1$
			throw new IllegalArgumentException("--release argument can not be null or empty"); //$NON-NLS-1$
		}
		this.accessRuleSet = accessRuleSet;
		if (externalAnnotationPath != null) {
			this.externalAnnotationPath = externalAnnotationPath.toString();
		}
		this.release = getReleaseOptionFromCompliance(release);
		this.releaseNumber = Integer.parseInt(this.release);
		this.jrtRelease = getJrtRelease(this.releaseNumber);
	}

	/*
	 * JDK 11 doesn't contain release 5. Hence if the compliance is below 6, we simply return the lowest supported
	 * release, which is 6.
	 */
	private static String getReleaseOptionFromCompliance(String comp) throws CoreException {
		if (JavaCore.compareJavaVersions(comp, JavaCore.VERSION_1_5) <= 0) {
			return "6"; //$NON-NLS-1$
		}
		int index = comp.indexOf("1."); //$NON-NLS-1$
		if (index != -1) {
			return comp.substring(index + 2, comp.length());
		} else {
			if (comp.indexOf('.') == -1) {
				return comp;
			}
			throw new CoreException(Status.error("Invalid value for --release argument:" + comp)); //$NON-NLS-1$
		}
	}

	@Override
	protected String getReleaseVersion() {
		return this.release;
	}

	@Override
	public NameEnvironmentAnswer findClass(String binaryFileName, String qualifiedPackageName, String moduleName,
			String qualifiedBinaryFileName, boolean asBinaryOnly, Predicate<String> moduleNameFilter, int r) {
		if (r > IReleaseAwareNameEnvironment.NO_RELEASE) {
			return super.findClass(binaryFileName, qualifiedPackageName, moduleName, qualifiedBinaryFileName,
					asBinaryOnly, moduleNameFilter, r);
		}
		try {
			return this.jrtRelease.loadType(qualifiedBinaryFileName, moduleName, moduleNameFilter);
		} catch (IOException | ClassFormatException e) { // handle like class not found
			return null;
		}
	}

	@Override
	public Collection<String> getModuleNames(Collection<String> limitModules) {
		Set<String> cache = ClasspathJrt.getModuleNames(this);
		if (cache != null)
			return selectModules(cache, limitModules);
		return Collections.emptyList();
	}

	@Override
	public void cleanup() {
		try {
			super.cleanup();
		} finally {
			// The same file system is also used in JRTUtil, so don't close it here.
			this.jrtRelease = null;
		}
	}

	@Override
	public boolean hasModule() {
		return this.jrtRelease.hasModule();
	}

	@Override
	protected String getKey() {
		return this.jrtRelease.getKey();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof ClasspathJrtWithReleaseOption))
			return false;
		ClasspathJrtWithReleaseOption jar = (ClasspathJrtWithReleaseOption) o;
		if (!Util.equalOrNull(this.release, jar.release)) {
			return false;
		}
		return super.equals(o);
	}

	@Override
	public int hashCode() {
		int hash = this.zipFilename == null ? super.hashCode() : this.zipFilename.hashCode();
		return Util.combineHashCodes(hash, this.release.hashCode());
	}

	@Override
	public String toString() {
		return "Classpath jrt file " + this.zipFilename + " with --release option " + this.release; //$NON-NLS-1$ //$NON-NLS-2$
	}

}
