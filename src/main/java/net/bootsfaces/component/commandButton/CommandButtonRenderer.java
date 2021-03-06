/**
 *  Copyright 2014 Riccardo Massera (TheCoder4.Eu)
 *
 *  This file is part of BootsFaces.
 *
 *  BootsFaces is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  BootsFaces is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with BootsFaces. If not, see <http://www.gnu.org/licenses/>.
 */
package net.bootsfaces.component.commandButton;

import java.io.IOException;
import java.util.Map;

import javax.faces.FacesException;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;
import javax.faces.render.FacesRenderer;

import net.bootsfaces.component.ajax.AJAXRenderer;
import net.bootsfaces.component.icon.IconRenderer;
import net.bootsfaces.render.A;
import net.bootsfaces.render.CoreRenderer;
import net.bootsfaces.render.H;
import net.bootsfaces.render.R;
import net.bootsfaces.render.Tooltip;

/** This class generates the HTML code of &lt;b:commandButton /&gt;. */
@FacesRenderer(componentFamily = "net.bootsfaces.component", rendererType = CommandButton.DEFAULT_RENDERER)
public class CommandButtonRenderer extends CoreRenderer {

	@Override
	public void decode(FacesContext context, UIComponent component) {
		if (componentIsDisabledOrReadonly(component)) {
			return;
		}

		String param = component.getClientId(context);
		if (context.getExternalContext().getRequestParameterMap().containsKey(param)) {
			new AJAXRenderer().decode(context, component);
		}
	}

	@Override
	public void encodeBegin(FacesContext context, UIComponent component) throws IOException {
		if (!component.isRendered()) {
			return;
		}
		CommandButton commandButton = (CommandButton) component;
		ResponseWriter rw = context.getResponseWriter();
//		component.setId(component.getId());
		String CID = component.getClientId(context);

		// 2) get the type (submit, button, reset ; default submit)
		String type = commandButton.getType();
		if (null == type)
			type = "submit";
		// 3) is it Ajax? (default= false)
		String style = commandButton.getStyle();

		rw.startElement("button", component);
		rw.writeAttribute("type", type, null);
		rw.writeAttribute("id", CID, "id");
		rw.writeAttribute("name", CID, "name");

		Tooltip.generateTooltip(context, commandButton, rw);

		writeAttribute(rw, H.STYLE, style, H.STYLE);

		rw.writeAttribute("class", getStyleClasses(commandButton), "class");

		String title = commandButton.getTitle();

		if (title != null && title.length() > 0) {
			rw.writeAttribute(H.TITLE, title, H.TITLE);
		}

		if (commandButton.isDisabled()) {
			rw.writeAttribute("disabled", "disabled", "disabled");
		}

		if (!type.equals("reset") && !type.equals("button")) {
			// Check if it is in a Form
			String formId = R.findComponentFormId(context, component);
			if (formId == null) {
				throw new FacesException("CommandButton : '" + CID + "' must be inside a form element");
			}
		}

		AJAXRenderer.generateBootsFacesAJAXAndJavaScriptForCommandButtons(context, commandButton, rw);

		// TODO : write DHTML attrs - onclick
		// Encode attributes (HTML 4 pass-through + DHTML)
		R.encodeHTML4DHTMLAttrs(rw, commandButton.getAttributes(), A.ALLBUTTON_ATTRS);
	}

	@Override
	public boolean getRendersChildren() {
		return true;
	}

	@Override
	public void encodeEnd(FacesContext context, UIComponent component) throws IOException {
		ResponseWriter rw = context.getResponseWriter();
		Map<String, Object> attrs = component.getAttributes();

		CommandButton commandButton = (CommandButton) component;

		Object value = commandButton.getValue();
		String icon = commandButton.getIcon();
		String faicon = commandButton.getIconAwesome();

		boolean fa = false; // flag to indicate whether the selected icon set is
		// Font Awesome or not.
		if (faicon != null) {
			icon = faicon;
			fa = true;
		}

		if (icon != null) {

			Object ialign = commandButton.getIconAlign(); // Default Left

			if (ialign != null && ialign.equals(A.RIGHT)) {
				value = value != null ? value + " " : null;
				writeText(rw, value, null);
				IconRenderer.encodeIcon(rw, component, icon, fa);
			} else {
				IconRenderer.encodeIcon(rw, component, icon, fa);
				value = value != null ? " " + value : null;
				writeText(rw, value, null);
			}

		} else {
			if (component.getChildCount() > 0) {
				value = value != null ? " " + value : null;
				writeText(rw, value, null);
			} else {
				writeText(rw, value, null);
			}
		}

		rw.endElement("button");

		Tooltip.activateTooltips(context, attrs, component);
	}



	private String getStyleClasses(CommandButton component) {
		StringBuilder sb = new StringBuilder(40); // optimize int

		sb.append("btn");
		String size = component.getSize();
		if (size != null) {
			sb.append(" btn-").append(size);
		}
		// TBS3 Si usa look, non severity
		String look = component.getLook();
		if (look != null) {
			sb.append(" btn-").append(look);
		} else {
			sb.append(" btn-default");
		}

		if (component.isDisabled()) {
			sb.append(" " + A.DISABLED);
		}
		String sclass = component.getStyleClass();
		if (sclass != null) {
			sb.append(" ").append(sclass);
		}

		return sb.toString().trim();

	}


}
