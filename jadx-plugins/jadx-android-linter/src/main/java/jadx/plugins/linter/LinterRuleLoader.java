package jadx.plugins.linter;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.core.dex.nodes.IFieldInfoRef;
import jadx.core.dex.nodes.RootNode;

public class LinterRuleLoader {

	public LinterRuleLoader(RootNode root) {
		this.root = root;
	}

	private static final Logger LOG = LoggerFactory.getLogger(LinterRuleLoader.class);

	private final RootNode root;

	private final Map<String, List<LinterRule<?>>> linterRules = new HashMap<>();

	// enum maps are reused in rules, so cache them
	final Map<String, Map<Integer, IFieldInfoRef>> linterIntEnums = new HashMap<>();
	final Map<String, Map<String, IFieldInfoRef>> linterStringEnums = new HashMap<>();
	final Map<String, Map<Long, IFieldInfoRef>> linterLongEnums = new HashMap<>();

	final Map<String, String> constantMap = new HashMap<>();

	public void mapConstants() {
		for (final Map.Entry<String, List<LinterRule<?>>> ruleEntry : linterRules.entrySet()) {
			final String signature = ruleEntry.getKey();
			final List<LinterRule<?>> rules = ruleEntry.getValue();
			for (final LinterRule<?> rule : rules) {
				switch (rule.getType()) {
					case INT_DEF:
						if (linterIntEnums.containsKey(signature)) {
							((IntLinterRule) rule).setConstantMap(linterIntEnums.get(signature));
						} else {
							linterIntEnums.put(signature, ((IntLinterRule) rule).buildConstantMap(constantMap, root));
						}
						break;
					case LONG_DEF:
						if (linterLongEnums.containsKey(signature)) {
							((LongLinterRule) rule).setConstantMap(linterLongEnums.get(signature));
						} else {
							linterLongEnums.put(signature, ((LongLinterRule) rule).buildConstantMap(constantMap, root));
						}
						break;
					case STRING_DEF:
						if (linterStringEnums.containsKey(signature)) {
							((StringLinterRule) rule).setConstantMap(linterStringEnums.get(signature));
						} else {
							linterStringEnums.put(signature, ((StringLinterRule) rule).buildConstantMap(constantMap, root));
						}
						break;
				}
			}
		}
	}

	public Map<String, List<LinterRule<?>>> getLinterRules() {
		return linterRules;
	}

	public void loadAndroidLinterRules(final String xmlRuleFile, final String source) {
		final XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
		String methodSignature = null;
		int argumetOffset = -1;
		boolean isFlag = false;
		String valueString = null;
		String annotationName = null;
		try (final InputStream androidRulesXml = LinterRuleLoader.class.getClassLoader().getResourceAsStream(xmlRuleFile)) {
			final XMLEventReader reader = xmlInputFactory.createXMLEventReader(androidRulesXml);
			while (reader.hasNext()) {
				final XMLEvent nextEvent = reader.nextEvent();
				if (nextEvent.isStartElement()) {
					final StartElement startElement = nextEvent.asStartElement();
					if (startElement.getName().getLocalPart().equals("item")) {
						methodSignature = null;
						argumetOffset = -1;
						isFlag = false;
						valueString = null;
						annotationName = null;
						final String name = startElement.getAttributeByName(new QName("name")).getValue();
						final int indexOf = name.indexOf(')');
						if (indexOf > 0) {
							methodSignature = name.substring(0, indexOf + 1);

							final int indexOf2 = name.indexOf(' ', indexOf + 1);
							if (indexOf2 > -1) {
								argumetOffset = Integer.parseInt(name.substring(indexOf + 2, indexOf2 + 2));
							}
						}
					}
					if (startElement.getName().getLocalPart().equals("annotation")) {
						annotationName = startElement.getAttributeByName(new QName("name")).getValue();
					}
					if (startElement.getName().getLocalPart().equals("val")) {
						final String name = startElement.getAttributeByName(new QName("name")).getValue();
						final String val = startElement.getAttributeByName(new QName("val")).getValue();
						if ("value".equals(name)) {
							// truncate quotes
							valueString = val.substring(1, val.length() - 1);
						}
						if ("flag".equals(name)) {
							isFlag = Boolean.parseBoolean(val);
						}
					}
				}
				if (nextEvent.isEndElement()) {
					final EndElement endElement = nextEvent.asEndElement();
					if (endElement.getName().getLocalPart().equals("item") && annotationName != null) {
						LinterRule<?> linterRule = null;
						switch (annotationName) {
							case "androidx.annotation.IntDef":
								linterRule = new IntLinterRule(methodSignature, argumetOffset, isFlag, source,
										valueString);
								break;
							case "androidx.annotation.LongDef":
								linterRule = new LongLinterRule(methodSignature, argumetOffset, isFlag, source,
										valueString);
								break;
							case "androidx.annotation.StringDef":
								linterRule = new StringLinterRule(methodSignature, argumetOffset, isFlag, source, valueString);
								break;
						}

						if (!linterRules.containsKey(methodSignature)) {
							linterRules.put(methodSignature, new ArrayList<>());
						}
						linterRules.get(methodSignature).add(linterRule);
					}
				}
			}
		} catch (final IOException | XMLStreamException e) {
			LOG.error("Failed to load linter rule file", e);
		}
	}

	public void loadAndroidConstants(final String constantFile) {
		try (final InputStream is = LinterRuleLoader.class.getClassLoader().getResourceAsStream(constantFile);
				final Scanner sc = new Scanner(is)) {
			while (sc.hasNext()) {
				final String line = sc.nextLine();
				final int idx = line.indexOf('=');
				if (idx > -1) {
					final String constantName = line.substring(0, idx);
					final String constantValue = line.substring(idx + 1);
					constantMap.put(constantName, constantValue);
				}
			}
		} catch (final IOException e) {
			LOG.error("Failed to load linter constant file: ", e);
		}
	}

	public void loadLibraryRules(final String repo) {
		List<String> libs = new ArrayList<>();
		try (final InputStream is = LinterRuleLoader.class.getClassLoader().getResourceAsStream("linter/" + repo + ".txt");
				final Scanner sc = new Scanner(is)) {
			while (sc.hasNext()) {
				final String lib = sc.nextLine();
				libs.add(lib);
				loadAndroidConstants("linter/" + repo + "/" + lib + ".txt");
			}

			for (final String lib : libs) {
				loadAndroidLinterRules("linter/" + repo + "/" + lib + ".xml", lib.replace("_", ":"));
			}
		} catch (final IOException e) {
			LOG.error("Failed to load linter constant file: ", e);
		}
	}
}
