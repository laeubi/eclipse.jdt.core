package org.eclipse.jdt.internal.debug.ui.actions;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.internal.debug.ui.launcher.RuntimeClasspathViewer;
import org.eclipse.jface.viewers.IStructuredSelection;

/**
 * Moves selected enries in a runtime classpath viewer down one position.
 */
public class MoveDownAction extends RuntimeClasspathAction {

	public MoveDownAction(RuntimeClasspathViewer viewer) {
		super(ActionMessages.getString("MoveDownAction.M&ove_Down_1"), viewer); //$NON-NLS-1$
	}
	/**
	 * @see IAction#run()
	 */
	public void run() {
		List targets = getOrderedSelection();
		if (targets.isEmpty()) {
			return;
		}
		List list = getEntiresAsList();
		int bottom = list.size() - 1;
		int index = 0;
		for (int i = targets.size() - 1; i >= 0; i--) {
			Object target = targets.get(i);
			index = list.indexOf(target);
			if (index < bottom) {
				bottom = index + 1;
				Object temp = list.get(bottom);
				list.set(bottom, target);
				list.set(index, temp);
			}
			bottom = index;
		} 
		setEntries(list);
	}

	/**
	 * @see SelectionListenerAction#updateSelection(IStructuredSelection)
	 */
	protected boolean updateSelection(IStructuredSelection selection) {
		return getViewer().isEnabled() && !selection.isEmpty() && !isIndexSelected(selection, getEntiresAsList().size() - 1);	
	}
}
