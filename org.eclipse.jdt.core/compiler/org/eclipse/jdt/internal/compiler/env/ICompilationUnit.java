package org.eclipse.jdt.internal.compiler.env;
/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
/**
 * This interface denotes a compilation unit, providing its name and content.
 */
public interface ICompilationUnit extends IDependent {
/**
 * Answer the contents of the compilation unit.
 *
 * In normal use, the contents are requested twice.
 * Once during the initial lite parsing step, then again for the
 * more detailed parsing step.
 */

char[] getContents();
/**
 * Answer the name of the top level public type.
 * For example, {Hashtable}.
 */

char[] getMainTypeName();
}
