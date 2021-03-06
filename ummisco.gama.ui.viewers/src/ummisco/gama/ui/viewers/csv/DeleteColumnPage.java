/*********************************************************************************************
 *
 * 'DeleteColumnPage.java, in plugin ummisco.gama.ui.viewers, is part of the source code of the
 * GAMA modeling and simulation platform.
 * (c) 2007-2016 UMI 209 UMMISCO IRD/UPMC & Partners
 *
 * Visit https://github.com/gama-platform/gama for license information and developers contact.
 * 
 *
 **********************************************************************************************/
package ummisco.gama.ui.viewers.csv;

import java.util.ArrayList;
import java.util.Arrays;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;

/**
 *
 * @author fhenri
 *
 */
public class DeleteColumnPage extends Dialog {

    private String[] columnTitle;
    private ArrayList<String> selectedColumn;

    /**
     * @param parentShell
     * @param columns
     */
    public DeleteColumnPage (
            Shell parentShell,
            String[] columns) {
        super(parentShell);
        this.columnTitle = columns;
        this.selectedColumn = new ArrayList<String>(columnTitle.length);
    }


    /**
     * @see org.eclipse.jface.dialogs.Dialog#createDialogArea(org.eclipse.swt.widgets.Composite)
     */
    protected Control createDialogArea (Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        final GridLayout gridLayout = new GridLayout();
        gridLayout.numColumns = 2;
        container.setLayout(gridLayout);
        final Label filterLabel = new Label(container, SWT.NONE);
        filterLabel.setLayoutData(new GridData(GridData.BEGINNING,
                GridData.BEGINNING, false, false, 2, 1));
        filterLabel.setText("Select the column that you want to delete:");

        final Label nameLabel = new Label(container, SWT.NONE);
        nameLabel.setLayoutData(new GridData(GridData.END,
                GridData.CENTER, false, false));
        nameLabel.setText("Column:");

        final List columnList = new List(container, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI);
        columnList.setBounds(40, 20, 220, 100);
        columnList.setItems(columnTitle);

        columnList.addSelectionListener(
                new SelectionAdapter() {
                    public void widgetSelected (SelectionEvent e) {
                        selectedColumn.clear();
                        selectedColumn.addAll(Arrays.asList(columnList.getSelection()));
                    }});

        return container;
    }

    /**
     * @return
     */
    public String[] getColumnSelected () {
        return selectedColumn.toArray(new String[selectedColumn.size()]);
    }

    /**
     * @see org.eclipse.jface.window.Window#configureShell(org.eclipse.swt.widgets.Shell)
     */
    protected void configureShell (Shell newShell) {
        super.configureShell(newShell);
        newShell.setText("Delete Column");
    }
}
