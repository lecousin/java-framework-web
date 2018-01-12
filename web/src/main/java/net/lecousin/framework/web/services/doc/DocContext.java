package net.lecousin.framework.web.services.doc;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import net.lecousin.framework.util.UnprotectedStringBuffer;

public class DocContext {

	private Map<String, Object> variables = new HashMap<>();
	
	@SuppressWarnings("unchecked")
	public DocContext addListElement(String listName) {
		LinkedList<DocContext> list;
		Object o = variables.get(listName);
		if (!(o instanceof LinkedList)) {
			list = new LinkedList<>();
			variables.put(listName, list);
		} else
			list = (LinkedList<DocContext>)o;
		DocContext element = new DocContext();
		list.add(element);
		return element;
	}
	
	public void setVariable(String name, String value) {
		variables.put(name, value);
	}
	
	@SuppressWarnings("unchecked")
	public void generate(UnprotectedStringBuffer str) {
		int pos = 0;
		int i;
		// lists
		while ((i = str.indexOf("@{", pos)) != -1) {
			int j = str.indexOf('{', i + 2);
			if (j < 0) break;
			String varName = str.substring(i + 2, j).asString();
			int k = str.indexOf("}" + varName + "}@", j + 1);
			if (k < 0) break;
			UnprotectedStringBuffer content = str.substring(j + 1, k);
			Object o = variables.get(varName);
			if (o == null)
				content = new UnprotectedStringBuffer();
			else if (o instanceof LinkedList) {
				UnprotectedStringBuffer template = content;
				content = new UnprotectedStringBuffer();
				for (DocContext element : ((LinkedList<DocContext>)o)) {
					UnprotectedStringBuffer copy = new UnprotectedStringBuffer(template);
					element.generate(copy);
					content.append(copy);
				}
			} else
				content = new UnprotectedStringBuffer((CharSequence)o);
			str.replace(i, k + 2 + varName.length(), content);
			pos = i + content.length();
		}
		// variables
		pos = 0;
		while ((i = str.indexOf("@@", pos)) != -1) {
			int j = str.indexOf("@@", i + 2);
			if (j < 0) break;
			String varName = str.substring(i + 2, j).asString();
			Object o = variables.get(varName);
			if (o instanceof CharSequence) {
				UnprotectedStringBuffer content = new UnprotectedStringBuffer((CharSequence)o);
				str.replace(i, j + 1, content);
				pos = i + content.length();
			} else {
				pos = j + 2;
			}
		}
	}
	
}
