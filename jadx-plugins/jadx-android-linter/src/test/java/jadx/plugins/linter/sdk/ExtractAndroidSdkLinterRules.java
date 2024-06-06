package jadx.plugins.linter.sdk;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.plugins.linter.AndroidAnnotationReader;

public class ExtractAndroidSdkLinterRules {

	private static final Logger LOG = LoggerFactory.getLogger(ExtractAndroidSdkLinterRules.class);

	public static void main(String[] args) {

		if (args.length < 3) {
			usage();
			System.exit(1);
		}

		final String androidSdkLocation = args[0];
		final String apiLevel = args[1];
		final String destDir = args[2];

		final String zipFilePath =
				new File(androidSdkLocation, "platforms/android-" + apiLevel + "/data/annotations.zip").getAbsolutePath();
		final String xmlDest = destDir + File.separator + "android.xml";
		final String constDestFile = destDir + File.separator + "android.txt";
		final String androidJarLocation = new File(androidSdkLocation, "platforms/android-" + apiLevel + "/android.jar").getAbsolutePath();

		final AndroidAnnotationReader annotationReader = new AndroidAnnotationReader();
		annotationReader.processRulesWithConstants(zipFilePath, xmlDest, constDestFile, androidJarLocation);
	}

	private static void usage() {
		LOG.info("<android sdk location (base dir)> <api-level> <output location>");
	}

}
