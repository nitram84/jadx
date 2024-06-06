package jadx.plugins.linter.google;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.plugins.linter.AndroidAnnotationReader;

public class GoogleMavenScraper {

	private static final Logger LOG = LoggerFactory.getLogger(GoogleMavenScraper.class);

	private static final String MAVEN_GOOGLE_BASEURL = "https://dl.google.com/android/maven2/";

	private String mirrorUrl = null;

	private final AndroidAnnotationReader androidAnnotationReader = new AndroidAnnotationReader();

	public static void main(String[] args) {
		if (args.length < 1) {
			usage();
			System.exit(1);
		}
		final GoogleMavenScraper scraper = new GoogleMavenScraper();

		final String destDir = args[0];
		if (args.length == 2) {
			scraper.setMirrorUrl(args[1]);
		}

		final Set<String> groups = scraper.fetchMasterIndex();
		for (String group : groups) {
			scraper.fetchIndex(group, destDir);
			final XMLInputFactory xmlInputFactory = buildXmlInputFactory();
			try {
				final URL groupIndexUrl = new URL(MAVEN_GOOGLE_BASEURL
						+ group.replace('.', '/') + "/group-index.xml");
				boolean skipFirstElement = true;
				try (final InputStream is = groupIndexUrl.openStream()) {
					final XMLEventReader reader = xmlInputFactory.createXMLEventReader(is);
					while (reader.hasNext()) {
						final XMLEvent nextEvent = reader.nextEvent();
						if (nextEvent.isStartElement()) {
							final StartElement startElement = nextEvent.asStartElement();
							final String artifact = startElement.getName().getLocalPart();
							if (skipFirstElement) {
								skipFirstElement = false;
							} else {
								final Attribute versionsAttrib = startElement.getAttributeByName(new QName("versions"));
								final String[] versions = versionsAttrib.getValue().split(",");
								scraper.fetchPom(group, artifact, versions[versions.length - 1], destDir);
							}
						}
					}
				} catch (final IOException | XMLStreamException e) {
					LOG.error("Failed to fetch group index: ", e);
				}
			} catch (final MalformedURLException e) {
				LOG.error("Invalid group index URL: ", e);
			}
		}
	}

	private static void usage() {
		LOG.info("<output location> <optional mirror/proxy location for maven.google.com>");
	}

	private static XMLInputFactory xmlInputFactory = null;

	private static XMLInputFactory buildXmlInputFactory() throws FactoryConfigurationError {
		if (xmlInputFactory == null) {
			xmlInputFactory = XMLInputFactory.newInstance();
			xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		}
		return xmlInputFactory;
	}

	private void fetchPom(final String group, final String artifact, final String version, final String destDir) {
		try {
			final String baseUrl = this.mirrorUrl != null ? mirrorUrl : MAVEN_GOOGLE_BASEURL;
			final URL url = new URL(baseUrl + group.replace('.', '/') + "/" + artifact + "/" + version
					+ "/" + artifact + "-" + version + ".pom");
			try (final InputStream is = url.openStream()) {
				final XMLEventReader reader = buildXmlInputFactory().createXMLEventReader(is);
				boolean isPackage = false;
				while (reader.hasNext()) {
					final XMLEvent nextEvent = reader.nextEvent();
					if (nextEvent.isStartElement()) {
						final StartElement startElement = nextEvent.asStartElement();
						final String el = startElement.getName().getLocalPart();
						if (el.equals("packaging")) {
							isPackage = true;
						}
					}
					if (nextEvent.isCharacters() && isPackage) {
						if (nextEvent.asCharacters().getData().equals("aar")) {
							this.fetchGoogleAarArtifact(group, artifact, version, destDir);
						}
						return;
					}
				}
			} catch (final IOException | XMLStreamException e) {
				LOG.error("Failed to process pom.xml: ", e);
			}
		} catch (final MalformedURLException e) {
			LOG.error("Invalid repository URL: ", e);
		}
	}

	private void fetchGoogleAarArtifact(final String group, final String artifact, final String version, final String destDir) {
		final String baseUrl = this.mirrorUrl != null ? mirrorUrl : MAVEN_GOOGLE_BASEURL;
		try {
			final URL url = new URL(baseUrl + group.replace('.', '/') + "/" + artifact + "/" + version
					+ "/" + artifact + "-" + version + ".aar");
			try (final InputStream is = url.openStream()) {
				androidAnnotationReader.extractAarLinterRules(group, artifact, is, destDir);
			} catch (final IOException e) {
				LOG.error("Failed to fetch aar: ", e);
			}
		} catch (final MalformedURLException e) {
			LOG.error("Invalid repository URL: ", e);
		}
	}

	private void setMirrorUrl(final String mirrorUrl) {
		this.mirrorUrl = mirrorUrl;
	}

	private Set<String> fetchMasterIndex() {
		final Set<String> groups = new HashSet<>();
		try {
			final URL url = new URL(MAVEN_GOOGLE_BASEURL + "master-index.xml");
			try (final InputStream is = url.openStream()) {
				final XMLEventReader reader = buildXmlInputFactory().createXMLEventReader(is);
				while (reader.hasNext()) {
					final XMLEvent nextEvent = reader.nextEvent();
					if (nextEvent.isStartElement()) {
						final StartElement startElement = nextEvent.asStartElement();
						String group = startElement.getName().getLocalPart();
						if (!group.equals("metadata")) {
							groups.add(group);
						}
					}
				}
			} catch (final IOException | XMLStreamException e) {
				LOG.error("Failed to fetch master index: ", e);
			}
		} catch (final MalformedURLException e) {
			LOG.error("Invalid master index URL: ", e);
		}
		return groups;
	}

	private void fetchIndex(final String group, final String destDir) {
		try {
			final URL groupIndexUrl = new URL(
					"https://dl.google.com/android/maven2/" + group.replace('.', '/') + "/group-index.xml");
			try (InputStream in = groupIndexUrl.openStream();
					FileOutputStream out = new FileOutputStream(new File(destDir, group + "-group-index.xml"))) {
				out.write(in.readAllBytes());
			} catch (IOException e) {
				LOG.error("Failed to fetch index: ", e);
			}
		} catch (final MalformedURLException e) {
			LOG.error("Invalid index URL: ", e);
		}
	}
}
