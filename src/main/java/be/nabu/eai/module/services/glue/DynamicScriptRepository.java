package be.nabu.eai.module.services.glue;

import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import be.nabu.glue.ScriptUtils;
import be.nabu.glue.api.ParserProvider;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.ScriptRepository;

public class DynamicScriptRepository implements ScriptRepository {

	private ParserProvider provider;
	private Map<String, Script> scripts;
	
	public DynamicScriptRepository(ParserProvider provider) {
		this.provider = provider;
	}
	
	@Override
	public Iterator<Script> iterator() {
		return getScripts().values().iterator();
	}

	@Override
	public Script getScript(String name) throws IOException, ParseException {
		return getScripts().get(name);
	}
	
	private Map<String, Script> getScripts() {
		if (scripts == null) {
			scripts = new HashMap<String, Script>();
		}
		return scripts;
	}

	@Override
	public ParserProvider getParserProvider() {
		return provider;
	}

	@Override
	public ScriptRepository getParent() {
		return null;
	}

	@Override
	public void refresh() throws IOException {
		scripts = null;
	}

	public void add(Script script) {
		getScripts().put(ScriptUtils.getFullName(script), script);
	}
	
	public void remove(Script script) {
		getScripts().remove(ScriptUtils.getFullName(script));
	}
}
