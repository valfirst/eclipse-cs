//============================================================================
//
// Copyright (C) 2002-2007  David Schneider, Lars K�dderitzsch
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//============================================================================

package com.atlassw.tools.eclipse.checkstyle.config.gui.widgets;

//=================================================
// Imports from java namespace
//=================================================

//=================================================
// Imports from javax namespace
//=================================================

//=================================================
// Imports from com namespace
//=================================================
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;

import com.atlassw.tools.eclipse.checkstyle.config.ConfigProperty;
import com.atlassw.tools.eclipse.checkstyle.util.CheckstylePluginException;
import com.atlassw.tools.eclipse.checkstyle.util.SWTUtil;

/**
 * A string property configuration widget.
 */
public class ConfigPropertyWidgetInteger extends ConfigPropertyWidgetAbstractBase
{
    // =================================================
    // Public static final variables.
    // =================================================

    // =================================================
    // Static class variables.
    // =================================================

    // =================================================
    // Instance member variables.
    // =================================================

    private Text mTextWidget;

    // =================================================
    // Constructors & finalizer.
    // =================================================

    /**
     * Creates the widget.
     * 
     * @param parent the parent composite
     * @param prop the property
     */
    public ConfigPropertyWidgetInteger(Composite parent, ConfigProperty prop)
    {
        super(parent, prop);
    }

    // =================================================
    // Methods.
    // =================================================

    /**
     * {@inheritDoc}
     */
    protected Control getValueWidget(Composite parent)
    {

        if (mTextWidget == null)
        {

            //
            // Create a text entry field.
            //
            mTextWidget = new Text(parent, SWT.SINGLE | SWT.BORDER);
            mTextWidget.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
            SWTUtil.addOnlyDigitInputSupport(mTextWidget);

            String initValue = getInitValue();
            if (initValue != null)
            {
                mTextWidget.setText(initValue);
            }
        }

        return mTextWidget;
    }

    /**
     * {@inheritDoc}
     */
    public String getValue()
    {
        String result = mTextWidget.getText();
        if (result == null)
        {
            result = new String();
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public void restorePropertyDefault()
    {
        String defaultValue = getConfigProperty().getMetaData().getDefaultValue();
        mTextWidget.setText(defaultValue != null ? defaultValue : new String());
    }

    /**
     * {@inheritDoc}
     */
    public void validate() throws CheckstylePluginException
    {
        try
        {
            //
            // Parse the value to see if an exception gets thrown.
            //
            Integer.parseInt(mTextWidget.getText());
        }
        catch (NumberFormatException e)
        {
            CheckstylePluginException.rethrow(e, e.getLocalizedMessage());
        }
    }
}