/*******************************************************************************
 * Copyright (c) 2016, 2025 IBM Corporation and others.
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
 *     Christoph Läubrich - extracted from ClasspathJrtWithReleaseOption
 *******************************************************************************/
package org.eclipse.jdt.internal.core.builder;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.eclipse.jdt.internal.compiler.env.IBinaryType;
import org.eclipse.jdt.internal.compiler.env.IModule;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.eclipse.jdt.internal.compiler.util.CtSym;
import org.eclipse.jdt.internal.compiler.util.JRTUtil;
import org.eclipse.jdt.internal.compiler.util.SuffixConstants;
import org.eclipse.jdt.internal.core.util.Util;

/**
 * Represents one specific release in the ctsym for the given java runtime
 */
class JrtReleaseClasses {

	private static final String MODULE_INFO = "module-info.sig"; //$NON-NLS-1$

	private final ClasspathJrt jrt;
	private final int release;
	private final String releaseCode;
	private CtSym ctSym;
	private FileSystem fs;
	private Path releasePath;
	private String modPathString;
	private IOException initError;

	JrtReleaseClasses(ClasspathJrt jrt, int release) {
		this.jrt = jrt;
		this.release = release;
		this.releaseCode = CtSym.getReleaseCode(release);
		try {
			//TODO this should happen lazy (as well as loading the modules!
			this.ctSym = JRTUtil.getCtSym(Path.of(this.jrt.zipFilename).getParent().getParent());
			this.fs = this.ctSym.getFs();
			this.releasePath = this.ctSym.getRoot();
			Path modPath = this.fs.getPath(this.releaseCode + (this.ctSym.isJRE12Plus() ? "" : "-modules")); //$NON-NLS-1$ //$NON-NLS-2$
			this.modPathString = !Files.exists(modPath) ? null : (this.jrt.zipFilename + "|" + modPath.toString()); //$NON-NLS-1$
			if (!Files.exists(this.releasePath.resolve(this.releaseCode))) {
				throw new IOException(String.format("release %d is not found in the system", this.release)); //$NON-NLS-1$
			}
			if (Files.exists(this.fs.getPath(this.releaseCode, "system-modules"))) { //$NON-NLS-1$
				this.fs = null; // Fallback to default version, all classes are on jrt fs, not here.
			}
		} catch (IOException e) {
			// can't load it!
			this.fs = null;
			this.initError = e;
		}
		loadModules();
	}

	NameEnvironmentAnswer loadType(String qualifiedBinaryFileName, String moduleName,
			Predicate<String> moduleNameFilter) throws IOException, ClassFormatException {
		if (this.fs == null) {
			if (this.initError != null) {
				throw this.initError;
			}
			return this.jrt.loadDefaultType(qualifiedBinaryFileName, moduleName, moduleNameFilter);
		}
		List<Path> releaseRoots = this.ctSym.releaseRoots(this.releaseCode);
		IBinaryType reader = null;
		byte[] content = null;
		String fileNameWithoutExtension = qualifiedBinaryFileName.substring(0,
				qualifiedBinaryFileName.length() - SuffixConstants.SUFFIX_CLASS.length);
		if (!releaseRoots.isEmpty()) {
			qualifiedBinaryFileName = qualifiedBinaryFileName.replace(".class", ".sig"); //$NON-NLS-1$ //$NON-NLS-2$
			Path fullPath = this.ctSym.getFullPath(this.releaseCode, qualifiedBinaryFileName, moduleName);
			// If file is known, read it from ct.sym
			if (fullPath != null) {
				content = this.ctSym.getFileBytes(fullPath);
				if (content != null) {
					reader = new ClassFileReader(content, qualifiedBinaryFileName.toCharArray());
					if (moduleName != null) {
						((ClassFileReader) reader).moduleName = moduleName.toCharArray();
					} else {
						if (this.ctSym.isJRE12Plus()) {
							moduleName = this.ctSym.getModuleInJre12plus(this.releaseCode, qualifiedBinaryFileName);
							if (moduleName != null) {
								((ClassFileReader) reader).moduleName = moduleName.toCharArray();
							}
						}
					}
				}
			}
		} else {
			// Read the file in a "classic" way from the JDK itself
			if (this.jrt.jrtFileSystem == null) {
				return null;
			}
			reader = JRTUtil.getClassfile(this.jrt.jrtFileSystem, qualifiedBinaryFileName, moduleName,
					moduleNameFilter);
		}
		if (reader == null) {
			return null;
		}
		return this.jrt.createAnswer(fileNameWithoutExtension, reader, reader.getModule());
	}

	private void loadModules() {
		if (this.fs == null || !this.ctSym.isJRE12Plus()) {
			ClasspathJrt.loadModules(this.jrt);
			return;
		}
		if (this.modPathString == null) {
			return;
		}
		ClasspathJrt.modulesCache.computeIfAbsent(this.modPathString, key -> {
			List<Path> releaseRoots = this.ctSym.releaseRoots(this.releaseCode);
			Map<String, IModule> newCache = new HashMap<>();
			for (Path root : releaseRoots) {
				try {
					Files.walkFileTree(root, Collections.emptySet(), 2, new SimpleFileVisitor<Path>() {
						@Override
						public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) throws IOException {
							if (attrs.isDirectory() || f.getNameCount() < 3) {
								return FileVisitResult.CONTINUE;
							}
							if (f.getFileName().toString().equals(MODULE_INFO)) {
								byte[] content = JrtReleaseClasses.this.ctSym.getFileBytes(f);
								if (content == null) {
									return FileVisitResult.CONTINUE;
								}
								ClasspathJrt.acceptModule(key, content, f.getParent().getFileName().toString(),
										newCache);
							}
							return FileVisitResult.SKIP_SIBLINGS;
						}
					});
				} catch (IOException e) {
					Util.log(e, "Failed to init modules cache for " + key); //$NON-NLS-1$
				}
			}
			return newCache.isEmpty() ? null : Map.copyOf(newCache);
		});
	}

	boolean hasModule() {
		return this.fs == null ? true : this.modPathString != null;
	}

	String getKey() {
		return this.fs == null ? this.jrt.zipFilename : this.modPathString;
	}

}
