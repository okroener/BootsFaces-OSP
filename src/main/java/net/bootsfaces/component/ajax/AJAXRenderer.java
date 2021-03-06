package net.bootsfaces.component.ajax;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.el.ELContext;
import javax.el.ExpressionFactory;
import javax.el.MethodExpression;
import javax.el.PropertyNotFoundException;
import javax.faces.component.ActionSource;
import javax.faces.component.ActionSource2;
import javax.faces.component.UIComponent;
import javax.faces.component.UIComponentBase;
import javax.faces.component.behavior.AjaxBehavior;
import javax.faces.component.behavior.ClientBehavior;
import javax.faces.component.behavior.ClientBehaviorContext;
import javax.faces.component.behavior.ClientBehaviorHolder;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;
import javax.faces.event.ActionEvent;
import javax.faces.event.ActionListener;
import javax.faces.event.FacesEvent;
import javax.faces.event.PhaseId;

import net.bootsfaces.component.commandButton.CommandButton;
import net.bootsfaces.component.tabView.TabView;
import net.bootsfaces.expressions.ExpressionResolver;
import net.bootsfaces.render.CoreRenderer;

public class AJAXRenderer extends CoreRenderer {

	public void decode(FacesContext context, UIComponent component) {
		String id = component.getClientId(context);
		decode(context, component, id);
	}

	public void decode(FacesContext context, UIComponent component, String componentId) {
		if (componentIsDisabledOrReadonly(component)) {
			return;
		}
		String source = (String) context.getExternalContext().getRequestParameterMap().get("javax.faces.source");

		if (component instanceof TabView && source != null) {
			for (UIComponent tab : component.getChildren()) {
				String tabId = tab.getClientId().replace(":", "_") + "_tab";
				if (source.equals(tabId)) {
					component = tab;
					componentId = tabId;
					break;
				}
			}
		}
		if (source == null) {
			// check for non-ajax call
			if (context.getExternalContext().getRequestParameterMap().containsKey(componentId)) {
				source = componentId;
			}
		}

		if (source != null && source.equals(componentId)) {
			String event = context.getExternalContext().getRequestParameterMap().get("javax.faces.partial.event");
			String realEvent = (String) context.getExternalContext().getRequestParameterMap().get("params");
			if (null != realEvent && realEvent.startsWith("BsFEvent=")) {
				realEvent = realEvent.substring("BfFEvent=".length());
				if (!realEvent.equals(event)) {
					// System.out.println("Difference between event and
					// realEvent:" + event + " vs. " + realEvent
					// + " Component: " + component.getClass().getSimpleName());
					event = realEvent;
				}
			}
			String nameOfGetter = "getOn" + event;
			try {
				Method[] methods = component.getClass().getMethods();
				for (Method m : methods) {
					if (m.getParameterTypes().length == 0) {
						if (m.getReturnType() == String.class) {
							if (m.getName().equalsIgnoreCase(nameOfGetter)) {
								String jsCallback = (String) m.invoke(component);
								if (jsCallback != null && jsCallback.contains("ajax:")) {
									if (component instanceof CommandButton && "action".equals(event)) {
										component.queueEvent(new ActionEvent(component));
									} else {
										FacesEvent ajaxEvent = new BootsFacesAJAXEvent(
												new AJAXBroadcastComponent(component), event, jsCallback);
										ajaxEvent.setPhaseId(PhaseId.INVOKE_APPLICATION);
										component.queueEvent(ajaxEvent);
									}
								}
								break;
							}

						}
					}
				}
			} catch (ReflectiveOperationException ex) {
				System.err.println("Couldn't invoke method " + nameOfGetter);
			}

			if (null != event) {
				UIComponentBase bb = (UIComponentBase) component;
				Map<String, List<ClientBehavior>> clientBehaviors = bb.getClientBehaviors();
				for (Entry<String, List<ClientBehavior>> entry : clientBehaviors.entrySet()) {
					if (event.equals(entry.getKey())) {
						List<ClientBehavior> value = entry.getValue();
						for (ClientBehavior bh : value) {
							ClientBehaviorContext behaviorContext = ClientBehaviorContext.createClientBehaviorContext(
									context, (UIComponent) component, entry.getKey(), null, null);
							if (bh instanceof AjaxBehavior) {
								String delay = ((AjaxBehavior) bh).getDelay();
								bh.decode(context, component);
							}
						}
					}

				}
			} else {
				System.out.println("Event is null - probably that's a bug in AJAXRenderer, roughly line 100");
			}

			if (component instanceof ActionSource) {
				ActionSource b = (ActionSource) component;
				ActionListener[] actionListeners = b.getActionListeners();
				if (null != actionListeners && actionListeners.length > 0) {
					component.queueEvent(new ActionEvent(component));
				}
			}
			if (component instanceof ActionSource2) {
				MethodExpression actionExpression = ((ActionSource2) component).getActionExpression();
				if (null != actionExpression) {
					component.queueEvent(new ActionEvent(component));
				}
			}
		}
	}

	/**
	 * Public API for the command button.
	 * 
	 * @param context
	 * @param component
	 * @param rw
	 * @throws IOException
	 */
	public static void generateBootsFacesAJAXAndJavaScriptForCommandButtons(FacesContext context,
			CommandButton component, ResponseWriter rw) throws IOException {
		// Render Ajax Capabilities and on<Event>-Handlers

		generateBootsFacesAJAXAndJavaScript(context, component, rw);
	}

	/**
	 * Public API for every input component (effectively everything except the
	 * command button).
	 * 
	 * @param context
	 * @param component
	 * @param rw
	 * @throws IOException
	 */
	public static void generateBootsFacesAJAXAndJavaScript(FacesContext context, ClientBehaviorHolder component,
			ResponseWriter rw) throws IOException {
		generateBootsFacesAJAXAndJavaScript(context, component, rw, null, null, false);

	}

	public static void generateBootsFacesAJAXAndJavaScript(FacesContext context, ClientBehaviorHolder component,
			ResponseWriter rw, String specialEvent, String specialEventHandler, boolean isJQueryCallback)
					throws IOException {
		boolean generatedAJAXCall = false;
		Collection<String> eventNames = component.getEventNames();
		for (String keyClientBehavior : eventNames) {
			if (null != ((IAJAXComponent) component).getJQueryEvents())
				if (((IAJAXComponent) component).getJQueryEvents().containsKey(keyClientBehavior))
					continue;
			generatedAJAXCall |= generateAJAXCallForASingleEvent(context, component, rw, specialEvent,
					specialEventHandler, isJQueryCallback, keyClientBehavior, null);

		}
		if (!generatedAJAXCall) {
			// should we generate AJAX nonetheless?
			boolean ajax = ((IAJAXComponent) component).isAjax();
			ajax |= null != ((IAJAXComponent) component).getUpdate();

			if (ajax) {
				StringBuilder s = generateAJAXCallForClientBehavior(context, (IAJAXComponent) component,
						(ClientBehavior) null);
				String script = s.toString() + ";";
				String defaultEvent = ((IAJAXComponent) component).getDefaultEventName();
				if (component instanceof CommandButton)
					if (script.length() > 0 && "click".equals(defaultEvent))
						script += ";return false;";
				rw.writeAttribute("on" + defaultEvent, script, null);
			}
		}
	}

	private static boolean generateAJAXCallForASingleEvent(FacesContext context, ClientBehaviorHolder component,
			ResponseWriter rw, String specialEvent, String specialEventHandler, boolean isJQueryCallback,
			String keyClientBehavior, StringBuilder generatedJSCode) throws IOException {
		boolean generatedAJAXCall = false;
		String jsCallback = "";
		String nameOfGetter = "getOn" + keyClientBehavior;
		try {
			Method[] methods = component.getClass().getMethods();
			for (Method m : methods) {
				if (m.getParameterTypes().length == 0) {
					if (m.getReturnType() == String.class) {
						if (m.getName().equalsIgnoreCase(nameOfGetter)) {
							jsCallback = (String) m.invoke(component);
							if (specialEventHandler != null && keyClientBehavior.equals(specialEvent)) {
								if (null == jsCallback || jsCallback.length() == 0)
									jsCallback = specialEventHandler;
								else
									jsCallback = jsCallback + ";javascript:" + specialEventHandler;
							}
							jsCallback = convertAJAXToJavascript(context, jsCallback, component, keyClientBehavior);
							if ("dragstart".equals(keyClientBehavior)) {
								rw.writeAttribute("draggable", "true", "draggable");
							}
							break;
						}
					}
				}
			}
		} catch (ReflectiveOperationException ex) {
			System.err.println("Couldn't invoke method " + nameOfGetter);
		}

		// TODO behaviors don't render correctly?
		// SR 19.09.2015: looks a bit odd, indeed. The method generateAJAXCall()
		// generates an onclick handler -
		// regardless of which event we currently deal with
		String script = "";
		Map<String, List<ClientBehavior>> clientBehaviors = component.getClientBehaviors();
		List<ClientBehavior> behaviors = clientBehaviors.get(keyClientBehavior);
		if (null != behaviors) {
			for (ClientBehavior cb : behaviors) {
				if (cb.getClass().getSimpleName().equals("AjaxBehavior")) {
					StringBuilder s = generateAJAXCallForClientBehavior(context, (IAJAXComponent) component,
							(AjaxBehavior) cb);
					script += s.toString() + ";";
				}
			}
		}
		// TODO end
		if (jsCallback.contains("BsF.ajax.") || script.contains("BsF.ajax.")) {
			generatedAJAXCall = true;

		}
		if (!isJQueryCallback) {
			if (jsCallback.length() > 0 || script.length() > 0) {
				if (component instanceof CommandButton)
					if (jsCallback.length() > 0 && "click".equals(keyClientBehavior))
						script += ";return false;";
				rw.writeAttribute("on" + keyClientBehavior, jsCallback + script, null);
			}
		}
		if (null != generatedJSCode) {
			generatedJSCode.setLength(0);
			if (jsCallback.length() > 0)
				generatedJSCode.append(jsCallback);
			if (script.length() > 0)
				generatedJSCode.append(script);
		}

		return generatedAJAXCall;
	}

	private static String convertAJAXToJavascript(FacesContext context, String jsCallback,
			ClientBehaviorHolder component, String event) {
		if (jsCallback == null)
			jsCallback = "";
		else {
			if (jsCallback.contains("ajax:")) {
				int pos = jsCallback.indexOf("ajax:");
				String rest = "";
				int end = jsCallback.indexOf(";javascript:", pos);
				if (end >= 0) {
					rest = jsCallback.substring(end + ";javascript:".length());
					jsCallback = jsCallback.substring(0, end);
				}

				StringBuilder ajax = generateAJAXCall(context, (IAJAXComponent) component, event);

				jsCallback = jsCallback.substring(0, pos) + ";" + ajax + rest;
			}

			if (!jsCallback.endsWith(";"))
				jsCallback += ";";
		}
		return jsCallback;
	}

	private static StringBuilder generateAJAXCall(FacesContext context, IAJAXComponent component, String event) {
		String complete = component.getOncomplete();
		StringBuilder cJS = new StringBuilder(150);
		String update = component.getUpdate();
		if (null == update) {
			update = "@none";
		}
		update = ExpressionResolver.getComponentIDs(context, (UIComponent) component, update);
		String process = component.getProcess();
		if (null == process) {
			if (component.getClass().getName().contains("Command")) {
				// CommandButton and CommandLink default to @form - see
				// http://stackoverflow.com/questions/25339056/understanding-process-and-update-attributes-of-primefaces
				process = "@form";
			} else {
				process = "@this";
			}
		}

		process = ExpressionResolver.getComponentIDs(context, (UIComponent) component, process);
		// BsF.ajax.callAjax(o,e,r,"@all",f, null);
		cJS.append("BsF.ajax.callAjax(this, event").append(",'" + update + "'").append(",'").append(process)
				.append("'");
		if (complete != null) {
			cJS.append(",function(){" + complete + "}");
		} else
			cJS.append(", null");
		if (event != null) {
			cJS.append(", '" + event + "'");
			// cJS.append(", {'BsFEvent':'" + event+"'}'");
		}
		cJS.append(");");
		return cJS;
	}

	private static StringBuilder generateAJAXCallForClientBehavior(FacesContext context, IAJAXComponent component,
			ClientBehavior ajaxBehavior) {
		StringBuilder cJS = new StringBuilder(150);
		// find default values
		String update = component.getUpdate();
		String oncomplete = component.getOncomplete();
		String process = component.getProcess();
		String onevent = "";
		if (ajaxBehavior != null) {
			// the default values can be overridden by the AJAX behavior
			if (ajaxBehavior instanceof AjaxBehavior) {
				boolean disabled = ((AjaxBehavior) ajaxBehavior).isDisabled();
				if (!disabled) {
					String onerror = ((AjaxBehavior) ajaxBehavior).getOnerror(); // todo
					onevent = ((AjaxBehavior) ajaxBehavior).getOnevent();
					if (onevent == null)
						onevent = "";
					else if (onevent.length() > 0)
						onevent = onevent + ";";
					Collection<String> execute = ((AjaxBehavior) ajaxBehavior).getExecute();
					if (null != execute && (!execute.isEmpty())) {
						for (String u : execute) {
							if (null == process)
								process = u;
							else
								process += " " + u;
						}
					}

					Collection<String> render = ((AjaxBehavior) ajaxBehavior).getRender();
					if (null != render && (!render.isEmpty())) {
						update = "";
						for (String u : render) {
							update += u + " ";
						}
					}
					oncomplete = component.getOncomplete();
				}
			}
		}

		process = ExpressionResolver.getComponentIDs(context, (UIComponent) component, process);
		update = ExpressionResolver.getComponentIDs(context, (UIComponent) component, update);
		cJS.append(encodeClick(component)).append(onevent).append("BsF.ajax.callAjax(this, event")
				.append(update == null ? ",''" : (",'" + update + "'"))
				.append(process == null ? ",'@this'" : (",'" + process.trim() + "'"));
		if (oncomplete != null) {
			cJS.append(",function(){" + oncomplete + "}");
		}
		cJS.append(");");

		return cJS;
	}

	private static String encodeClick(IAJAXComponent component) {
		String js;
		String oc = (String) component.getOnclick();
		if (oc != null) {
			js = oc.endsWith(";") ? oc : oc + ";";
		} else {
			js = "";
		}

		return js;
	}

	// ToDo - copied from Mojarra, and has to be adapted to BootsFaces AJAX
	// Appends an name/value property pair to a JSON object. Assumes
	// object has already been opened by the caller.
	public static void appendProperty(StringBuilder builder, String name, Object value, boolean quoteValue) {

		if ((null == name) || (name.length() == 0))
			throw new IllegalArgumentException();

		char lastChar = builder.charAt(builder.length() - 1);
		if ((lastChar != ',') && (lastChar != '{'))
			builder.append(',');

		appendQuotedValue(builder, name);
		builder.append(":");

		if (value == null) {
			builder.append("''");
		} else if (quoteValue) {
			appendQuotedValue(builder, value.toString());
		} else {
			builder.append(value.toString());
		}
	}

	// ToDo - copied from Mojarra, and has to be adapted to BootsFaces AJAX
	// Append a script to the chain, escaping any single quotes, since
	// our script content is itself nested within single quotes.
	private static void appendQuotedValue(StringBuilder builder, String script) {

		builder.append("'");

		int length = script.length();

		for (int i = 0; i < length; i++) {
			char c = script.charAt(i);

			if (c == '\'' || c == '\\') {
				builder.append('\\');
			}

			builder.append(c);
		}

		builder.append("'");
	}

	/**
	 * Registers a callback with jQuery.
	 * 
	 * @param context
	 * @param component
	 * @param rw
	 * @param clientId
	 * @param additionalEventHandlers
	 * @throws IOException
	 */
	public void generateBootsFacesAJAXAndJavaScriptForJQuery(FacesContext context, UIComponent component,
			ResponseWriter rw, String clientId, Map<String, String> additionalEventHandlers) throws IOException {
		IAJAXComponent ajaxComponent = (IAJAXComponent) component;
		Set<String> events = ajaxComponent.getJQueryEvents().keySet();
		for (String event : events) {
			StringBuilder code = new StringBuilder();
			String additionalEventHandler = null;
			if (null != additionalEventHandlers)
				additionalEventHandler = additionalEventHandlers.get(event);

			generateAJAXCallForASingleEvent(context, (ClientBehaviorHolder) component, rw, event,
					additionalEventHandler, true, event, code);
			if (code.length() > 0) {
				rw.startElement("script", component);
				String js = "$('#" + clientId + "').on('" + ajaxComponent.getJQueryEvents().get(event)
						+ "', function(){" + code.toString() + "});";
				rw.writeText(js, null);
				rw.endElement("script");
			}
		}
	}
}
