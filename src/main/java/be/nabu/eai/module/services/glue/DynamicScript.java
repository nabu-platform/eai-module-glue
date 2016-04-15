package be.nabu.eai.module.services.glue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;

import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.api.Parser;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.impl.executors.SequenceExecutor;

public class DynamicScript implements Script {

	private ScriptRepository repository;
	private String namespace;
	private String name;
	private String content;
	private Charset charset;
	private ExecutorGroup root;
	private Parser parser;
	
	public DynamicScript(String namespace, String name, ScriptRepository repository, Charset charset) {
		this.namespace = namespace;
		this.name = name;
		this.repository = repository;
		this.charset = charset;
		this.parser = repository.getParserProvider().newParser(repository, name + ".glue");
	}
	
	@Override
	public Iterator<String> iterator() {
		return new ArrayList<String>().iterator();
	}

	@Override
	public ScriptRepository getRepository() {
		return repository;
	}

	@Override
	public String getNamespace() {
		return namespace;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public ExecutorGroup getRoot() throws IOException, ParseException {
		if (root == null) {
			synchronized(this) {
				if (root == null) {
					root = new SequenceExecutor(null, null, null);
				}
			}
		}
		return root;
	}

	@Override
	public Charset getCharset() {
		return charset;
	}

	@Override
	public Parser getParser() {
		return parser;
	}

	@Override
	public InputStream getSource() {
		return content == null ? null : new ByteArrayInputStream(content.getBytes(charset));
	}

	@Override
	public InputStream getResource(String name) throws IOException {
		return null;
	}

	public void setContent(String content) throws IOException, ParseException {
		this.root = parser.parse(new StringReader(content));
		this.content = content;
	}
}
